package com.toyquaise.toothfort.logic.board

import com.toyquaise.toothfort.logic.Geometry
import com.toyquaise.toothfort.logic.Vec2
import com.toyquaise.toothfort.logic.circuit.Network
import kotlin.math.abs

/**
 * A part on the counter, anywhere and at any angle. It has two terminals at the ends of its
 * axis: + toward [angle], − opposite. Wires and touching parts connect only there.
 */
class Part(val id: Int, val kind: PartKind, pos: Vec2, angle: Double) {
    var pos: Vec2 = pos
        internal set

    /** Degrees counter-clockwise from +x, seen from above. */
    var angle: Double = angle
        internal set

    val axis: Vec2 get() = Vec2.ofAngle(angle)
    val plusTerminal: Vec2 get() = pos + axis * Layout.TERMINAL_OFFSET
    val minusTerminal: Vec2 get() = pos - axis * Layout.TERMINAL_OFFSET

    /** Battery charge, 1 = full. */
    var charge = 1.0
        internal set

    /** Machine heat, 1 = burnt out. */
    var heat = 0.0
        internal set

    /** A burnt machine or a blown fuse: it no longer conducts. */
    var broken = false
        internal set

    /** Switches only. */
    var closed = true
        internal set

    /** From the last solve: V(+) − V(−) across the part. */
    var volts = 0.0
        internal set

    /** From the last solve: for a battery the current out of +, for anything else the current from + to −. */
    var amps = 0.0
        internal set

    /** Machines: how well it works right now (see [Electric.performance]). */
    var performance = 0.0
        internal set

    /** Voltage as a share of the machine's rating (1 = exactly right). */
    val voltageRatio: Double get() = kind.machine?.let { abs(volts) / it.volts } ?: 0.0
}

/** A clip where wires meet in the middle of nowhere. */
class Junction(val id: Int, val pos: Vec2)

/** Where a wire ends: one terminal of a part, or a junction. */
sealed interface Port {
    data class Terminal(val part: Part, val plus: Boolean) : Port
    data class Clip(val junction: Junction) : Port
}

/**
 * A wire from [a] to [b], laid through [via] (the bends the finger drew). Its ends follow the
 * ports, so turning a part drags its wires along. Resistance and price go by length.
 */
class Wire(val id: Int, val a: Port, val b: Port, val via: List<Vec2>, val gauge: WireGauge) {
    var heat = 0.0
        internal set
    var melted = false
        internal set

    /** From the last solve: current from [a] to [b]. */
    var amps = 0.0
        internal set
}

sealed interface BoardEvent {
    data class Burnt(val part: Part) : BoardEvent
    data class Blown(val part: Part) : BoardEvent
    data class Melted(val wire: Wire) : BoardEvent
    data class Depleted(val part: Part) : BoardEvent
}

/** Where a part would go, after snapping, and whether it may. */
data class Placement(val pos: Vec2, val angle: Double, val ok: Boolean, val snappedTo: Part?)

/**
 * The counter, [width] by [height] units. [blocked] says whether a round footprint of a radius
 * would hit the candies' trail, a kitchen prop or the tooth.
 */
class Board(val width: Double, val height: Double, private val blocked: (Vec2, Double) -> Boolean) {
    private val partList = ArrayList<Part>()
    private val junctionList = ArrayList<Junction>()
    private val wireList = ArrayList<Wire>()
    private var nextId = 1

    /** Things that happened during the last [update]; the game drains it. */
    val events = ArrayList<BoardEvent>()

    val parts: List<Part> get() = partList
    val junctions: List<Junction> get() = junctionList
    val wires: List<Wire> get() = wireList

    fun inside(p: Vec2, margin: Double = 0.0) = p.x >= margin && p.y >= margin && p.x <= width - margin && p.y <= height - margin

    // ------------------------------------------------------------------ geometry

    fun position(port: Port): Vec2 = when (port) {
        is Port.Terminal -> if (port.plus) port.part.plusTerminal else port.part.minusTerminal
        is Port.Clip -> port.junction.pos
    }

    /** The wire's whole course, port to port. */
    fun course(w: Wire): List<Vec2> = listOf(position(w.a)) + w.via + listOf(position(w.b))

    fun length(w: Wire): Double = Geometry.length(course(w))

    fun ohms(w: Wire): Double = maxOf(1e-3, length(w) * w.gauge.ohmsPerUnit)

    fun partAt(p: Vec2, reach: Double = Layout.PART_RADIUS): Part? =
        partList.filter { it.pos.distanceTo(p) <= reach }.minByOrNull { it.pos.distanceTo(p) }

    /** The terminal or junction nearest [p] within [reach]. */
    fun portAt(p: Vec2, reach: Double): Port? {
        var best: Port? = null
        var bestD = reach
        for (part in partList) {
            for (plus in listOf(true, false)) {
                val d = (if (plus) part.plusTerminal else part.minusTerminal).distanceTo(p)
                if (d <= bestD) { bestD = d; best = Port.Terminal(part, plus) }
            }
        }
        for (j in junctionList) {
            val d = j.pos.distanceTo(p)
            if (d <= bestD) { bestD = d; best = Port.Clip(j) }
        }
        return best
    }

    fun wireAt(p: Vec2, reach: Double): Wire? =
        wireList.map { it to Geometry.distanceToPolyline(p, course(it)) }.filter { it.second <= reach }.minByOrNull { it.second }?.first

    fun wiresAt(port: Port): List<Wire> = wireList.filter { it.a == port || it.b == port }

    // ------------------------------------------------------------------ editing

    /**
     * Where a part dropped at [pos] turned to [angle] would land. A terminal dropped near another
     * part's terminal snaps onto it, end to end along that part's axis, keeping which of its own
     * ends the player brought close: + against − makes a series pair.
     */
    fun placementFor(pos: Vec2, angle: Double, ignore: Part? = null): Placement {
        var p = pos
        var a = angle
        var snapped: Part? = null
        var best = Layout.SNAP_DISTANCE
        val axis = Vec2.ofAngle(angle)
        for (q in partList) {
            if (q === ignore) continue
            for (qPlus in listOf(true, false)) {
                val t = if (qPlus) q.plusTerminal else q.minusTerminal
                val outward = if (qPlus) q.axis else -q.axis
                for (ownPlus in listOf(true, false)) {
                    val own = if (ownPlus) pos + axis * Layout.TERMINAL_OFFSET else pos - axis * Layout.TERMINAL_OFFSET
                    val d = own.distanceTo(t)
                    if (d < best) {
                        best = d
                        snapped = q
                        p = t + outward * Layout.TERMINAL_OFFSET
                        // The touching end points back at q: our + then faces inward, our − outward.
                        val dir = if (ownPlus) -outward else outward
                        a = Math.toDegrees(kotlin.math.atan2(-dir.y, dir.x))
                    }
                }
            }
        }
        return Placement(p, normalize(a), canStand(p, ignore), snapped)
    }

    /** Whether a part's footprint fits at [p]: on the counter, off the trail and props, clear of other parts. */
    fun canStand(p: Vec2, ignore: Part? = null): Boolean {
        if (!inside(p, Layout.PART_RADIUS * 0.6)) return false
        if (blocked(p, Layout.PART_RADIUS)) return false
        return partList.none { it !== ignore && it.pos.distanceTo(p) < 2 * Layout.PART_RADIUS }
    }

    fun place(kind: PartKind, pos: Vec2, angle: Double): Part? {
        val pl = placementFor(pos, angle)
        if (!pl.ok) return null
        return Part(nextId++, kind, pl.pos, pl.angle).also { partList += it; changed() }
    }

    /** Turns a part about its centre. Its wires follow; a touching neighbour may let go. */
    fun rotate(part: Part, degrees: Double): Boolean {
        if (part !in partList) return false
        part.angle = normalize(part.angle + degrees)
        changed()
        return true
    }

    /** Takes a part off the counter with every wire that ends on it. */
    fun remove(part: Part): Boolean {
        if (!partList.remove(part)) return false
        wireList.removeAll { (it.a as? Port.Terminal)?.part === part || (it.b as? Port.Terminal)?.part === part }
        changed()
        return true
    }

    fun toggleSwitch(part: Part): Boolean {
        if (part.kind.role != Role.SWITCH || part !in partList) return false
        part.closed = !part.closed
        changed()
        return true
    }

    fun addJunction(pos: Vec2): Junction? {
        if (!inside(pos)) return null
        return Junction(nextId++, pos).also { junctionList += it; changed() }
    }

    /** Takes a junction away with its wires. */
    fun removeJunction(j: Junction): Boolean {
        if (!junctionList.remove(j)) return false
        val port = Port.Clip(j)
        wireList.removeAll { it.a == port || it.b == port }
        changed()
        return true
    }

    /** Any two different ports, even both ends of one battery: that is a short circuit, and it should be possible to make one. */
    fun canWire(a: Port, b: Port): Boolean {
        if (a == b) return false
        if (a is Port.Terminal && a.part !in partList) return false
        if (b is Port.Terminal && b.part !in partList) return false
        if (a is Port.Clip && a.junction !in junctionList) return false
        if (b is Port.Clip && b.junction !in junctionList) return false
        return true
    }

    fun addWire(a: Port, b: Port, via: List<Vec2>, gauge: WireGauge): Wire? {
        if (!canWire(a, b)) return null
        return Wire(nextId++, a, b, via.map { Vec2(it.x.coerceIn(0.0, width), it.y.coerceIn(0.0, height)) }, gauge)
            .also { wireList += it; changed() }
    }

    fun removeWire(w: Wire): Boolean = wireList.remove(w).also { if (it) changed() }

    /** Replaces a spent battery's charge (the game charges for it). */
    fun refill(part: Part): Boolean {
        if (part.kind.role != Role.BATTERY || part !in partList) return false
        part.charge = 1.0
        changed()
        return true
    }

    // ------------------------------------------------------------------ electricity

    private var dirty = true

    /** Grows by one whenever a part, junction or wire is added, removed, turned, switched or broken. */
    var revision = 0
        private set

    private fun changed() {
        dirty = true
        revision++
    }

    /** Solves the circuit again if anything changed since the last solve. */
    fun solveIfChanged() {
        if (dirty) solve()
    }

    /**
     * Builds the circuit and solves it: every terminal and junction is a node; wires are
     * resistors by length; terminals that touch are joined; parts sit between their terminals.
     */
    fun solve() {
        dirty = false
        var nodeCount = 0
        val plusNode = HashMap<Part, Int>()
        val minusNode = HashMap<Part, Int>()
        val clipNode = HashMap<Junction, Int>()
        for (p in partList) {
            plusNode[p] = nodeCount++
            minusNode[p] = nodeCount++
        }
        for (j in junctionList) clipNode[j] = nodeCount++
        fun node(port: Port): Int = when (port) {
            is Port.Terminal -> if (port.plus) plusNode[port.part]!! else minusNode[port.part]!!
            is Port.Clip -> clipNode[port.junction]!!
        }

        val net = Network(nodeCount)
        val wireIndex = HashMap<Wire, Int>()
        for (w in wireList) {
            w.amps = 0.0
            if (!w.melted) wireIndex[w] = net.resistor(node(w.a), node(w.b), ohms(w))
        }
        // Terminals pressed together.
        for (i in partList.indices) for (k in i + 1 until partList.size) {
            val p = partList[i]
            val q = partList[k]
            for (pp in listOf(true, false)) for (qp in listOf(true, false)) {
                val tp = if (pp) p.plusTerminal else p.minusTerminal
                val tq = if (qp) q.plusTerminal else q.minusTerminal
                if (tp.distanceTo(tq) <= Layout.CONTACT_DISTANCE) {
                    net.resistor(if (pp) plusNode[p]!! else minusNode[p]!!, if (qp) plusNode[q]!! else minusNode[q]!!, Electric.CONTACT_OHMS)
                }
            }
        }
        val partIndex = HashMap<Part, Int>()
        val batteryIndex = HashMap<Part, Int>()
        for (p in partList) {
            val plus = plusNode[p]!!
            val minus = minusNode[p]!!
            when (p.kind.role) {
                Role.BATTERY -> batteryIndex[p] = net.battery(minus, plus, Electric.batteryEmf(p.charge), Electric.BATTERY_OHMS)
                Role.MACHINE -> if (!p.broken) partIndex[p] = net.resistor(plus, minus, p.kind.machine!!.ohms)
                Role.SWITCH -> if (p.closed) partIndex[p] = net.resistor(plus, minus, Electric.SWITCH_OHMS)
                Role.FUSE -> if (!p.broken) partIndex[p] = net.resistor(plus, minus, Electric.FUSE_OHMS)
                Role.RESISTOR -> partIndex[p] = net.resistor(plus, minus, Electric.RESISTOR_OHMS)
            }
        }

        val s = net.solve()
        for ((w, i) in wireIndex) w.amps = s.resistorCurrent[i]
        for (p in partList) {
            p.volts = s.voltage[plusNode[p]!!] - s.voltage[minusNode[p]!!]
            p.amps = partIndex[p]?.let { s.resistorCurrent[it] } ?: batteryIndex[p]?.let { s.batteryCurrent[it] } ?: 0.0
            p.performance = if (p.kind.role == Role.MACHINE && !p.broken) Electric.performance(p.voltageRatio) else 0.0
        }
    }

    /**
     * Runs the circuit for [dt] seconds: solves it, drains (or charges) batteries, heats machines
     * driven above their rating and wires carrying more than they can, and blows fuses.
     */
    fun update(dt: Double) {
        solve()
        var broke = false
        for (p in partList) {
            when (p.kind.role) {
                Role.BATTERY -> {
                    val before = p.charge
                    p.charge = (p.charge - p.amps * dt / Electric.BATTERY_CAPACITY).coerceIn(0.0, 1.0)
                    // The last drop of charge drives almost nothing; call it empty.
                    if (p.charge < Electric.EMPTY_CHARGE && p.amps > 0) p.charge = 0.0
                    if (before > 0.0 && p.charge <= 0.0) events += BoardEvent.Depleted(p)
                }
                Role.MACHINE -> if (!p.broken) {
                    val power = p.voltageRatio * p.voltageRatio
                    val excess = power - Electric.SAFE_RATIO * Electric.SAFE_RATIO
                    p.heat = if (excess > 0) p.heat + excess * Electric.MACHINE_HEATING * dt
                    else maxOf(0.0, p.heat - Electric.MACHINE_COOLING * dt)
                    if (p.heat >= 1.0) {
                        p.broken = true
                        p.performance = 0.0
                        broke = true
                        events += BoardEvent.Burnt(p)
                    }
                }
                Role.FUSE -> if (!p.broken && abs(p.amps) > Electric.FUSE_AMPS) {
                    p.broken = true
                    broke = true
                    events += BoardEvent.Blown(p)
                }
                else -> {}
            }
        }
        for (w in wireList) {
            if (w.melted) continue
            val load = abs(w.amps) / w.gauge.maxAmps
            w.heat = if (load > 1.0) w.heat + (load * load - 1.0) * Electric.WIRE_HEATING * dt
            else maxOf(0.0, w.heat - Electric.WIRE_COOLING * dt)
            if (w.heat >= 1.0) {
                w.melted = true
                w.amps = 0.0
                broke = true
                events += BoardEvent.Melted(w)
            }
        }
        if (broke) changed()
    }

    private fun normalize(a: Double): Double {
        var x = a % 360.0
        if (x < 0) x += 360.0
        return x
    }
}
