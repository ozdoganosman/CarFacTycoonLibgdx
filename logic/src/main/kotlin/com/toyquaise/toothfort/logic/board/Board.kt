package com.toyquaise.toothfort.logic.board

import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.Dir
import com.toyquaise.toothfort.logic.circuit.Network
import kotlin.math.abs

/** What lies under a cell. Parts go on ground; wires may also cross the candy path. */
enum class Terrain { GROUND, PATH, TOOTH }

/**
 * A part on the board. It has two terminals on opposite sides: + on [facing], − on the side
 * opposite. Wires and neighbouring parts connect only through those two sides.
 */
class Part(val kind: PartKind, val cell: Cell, facing: Dir) {
    var facing: Dir = facing
        internal set
    val plusSide: Dir get() = facing
    val minusSide: Dir get() = facing.opposite

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

    fun terminalOn(side: Dir) = side == plusSide || side == minusSide

    /** Voltage as a share of the machine's rating (1 = exactly right). */
    val voltageRatio: Double get() = kind.machine?.let { abs(volts) / it.volts } ?: 0.0
}

/** A wire between two neighbouring cells. [a] is always the upper-left one. */
class Wire(val a: Cell, val b: Cell, val gauge: WireGauge) {
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

class Board(val width: Int, val height: Int, private val terrainAt: (Cell) -> Terrain) {
    private val parts = HashMap<Cell, Part>()
    private val wires = HashMap<Pair<Cell, Cell>, Wire>()

    /** Things that happened during the last [update]; the game drains it. */
    val events = ArrayList<BoardEvent>()

    val allParts: Collection<Part> get() = parts.values
    val allWires: Collection<Wire> get() = wires.values

    fun inside(c: Cell) = c.x in 0 until width && c.y in 0 until height
    fun terrain(c: Cell): Terrain = terrainAt(c)
    fun partAt(c: Cell): Part? = parts[c]
    fun wireBetween(a: Cell, b: Cell): Wire? = wires[key(a, b)]
    fun wiresAt(c: Cell): List<Wire> = Dir.entries.mapNotNull { wires[key(c, c.step(it))] }

    // ------------------------------------------------------------------ editing

    fun canPlace(c: Cell): Boolean =
        inside(c) && terrain(c) == Terrain.GROUND && parts[c] == null && wiresAt(c).isEmpty()

    fun place(kind: PartKind, c: Cell, facing: Dir): Part? {
        if (!canPlace(c)) return null
        return Part(kind, c, facing).also { parts[c] = it; changed() }
    }

    /** Turns a part a quarter turn. Wires on its old terminal sides stay but no longer connect. */
    fun rotate(c: Cell): Boolean {
        val p = parts[c] ?: return false
        p.facing = p.facing.clockwise
        changed()
        return true
    }

    fun remove(c: Cell): Part? = parts.remove(c)?.also { changed() }

    fun toggleSwitch(c: Cell): Boolean {
        val p = parts[c] ?: return false
        if (p.kind.role != Role.SWITCH) return false
        p.closed = !p.closed
        changed()
        return true
    }

    /** Whether a wire may run from [from] into its neighbour [to]. */
    fun canWire(from: Cell, to: Cell): Boolean {
        val d = from.dirTo(to) ?: return false
        if (!inside(from) || !inside(to)) return false
        if (terrain(from) == Terrain.TOOTH || terrain(to) == Terrain.TOOTH) return false
        if (wires.containsKey(key(from, to))) return false
        // A wire can only meet a part at one of its terminals.
        parts[from]?.let { if (!it.terminalOn(d)) return false }
        parts[to]?.let { if (!it.terminalOn(d.opposite)) return false }
        return true
    }

    fun addWire(from: Cell, to: Cell, gauge: WireGauge): Wire? {
        if (!canWire(from, to)) return null
        val (a, b) = key(from, to)
        return Wire(a, b, gauge).also { wires[a to b] = it; changed() }
    }

    fun removeWire(from: Cell, to: Cell): Wire? = wires.remove(key(from, to))?.also { changed() }

    // ------------------------------------------------------------------ electricity

    private var dirty = true

    /** Grows by one whenever a part or wire is added, removed, turned, switched or broken. */
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
     * Builds the circuit from the board and solves it. Every empty cell with wires is one node
     * (wires that meet in a cell are joined); every part has a node for each terminal.
     */
    fun solve() {
        dirty = false
        var nodeCount = 0
        val cellNode = HashMap<Cell, Int>()
        val plusNode = HashMap<Part, Int>()
        val minusNode = HashMap<Part, Int>()
        for (p in parts.values) {
            plusNode[p] = nodeCount++
            minusNode[p] = nodeCount++
        }
        fun endpoint(c: Cell, side: Dir): Int? {
            val p = parts[c]
            return when {
                p == null -> cellNode.getOrPut(c) { nodeCount++ }
                side == p.plusSide -> plusNode[p]
                side == p.minusSide -> minusNode[p]
                else -> null
            }
        }

        // First pass to number the nodes, then build the network.
        data class Link(val wire: Wire, val na: Int, val nb: Int)
        val links = ArrayList<Link>()
        for (w in wires.values) {
            w.amps = 0.0
            if (w.melted) continue
            val d = w.a.dirTo(w.b)!!
            val na = endpoint(w.a, d) ?: continue
            val nb = endpoint(w.b, d.opposite) ?: continue
            links += Link(w, na, nb)
        }
        val contacts = ArrayList<Pair<Int, Int>>()
        for (p in parts.values) {
            for (side in listOf(Dir.E, Dir.S)) {
                if (!p.terminalOn(side)) continue
                val q = parts[p.cell.step(side)] ?: continue
                if (!q.terminalOn(side.opposite)) continue
                val np = if (side == p.plusSide) plusNode[p]!! else minusNode[p]!!
                val nq = if (side.opposite == q.plusSide) plusNode[q]!! else minusNode[q]!!
                contacts += np to nq
            }
        }

        val net = Network(nodeCount)
        val wireIndex = links.map { net.resistor(it.na, it.nb, it.wire.gauge.ohmsPerSegment) }
        contacts.forEach { (a, b) -> net.resistor(a, b, Electric.CONTACT_OHMS) }
        val partIndex = HashMap<Part, Int>()
        val batteryIndex = HashMap<Part, Int>()
        for (p in parts.values) {
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
        links.forEachIndexed { i, l -> l.wire.amps = s.resistorCurrent[wireIndex[i]] }
        for (p in parts.values) {
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
        var changedTopology = false
        for (p in parts.values) {
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
                        changedTopology = true
                        events += BoardEvent.Burnt(p)
                    }
                }
                Role.FUSE -> if (!p.broken && abs(p.amps) > Electric.FUSE_AMPS) {
                    p.broken = true
                    changedTopology = true
                    events += BoardEvent.Blown(p)
                }
                else -> {}
            }
        }
        for (w in wires.values) {
            if (w.melted) continue
            val load = abs(w.amps) / w.gauge.maxAmps
            w.heat = if (load > 1.0) w.heat + (load * load - 1.0) * Electric.WIRE_HEATING * dt
            else maxOf(0.0, w.heat - Electric.WIRE_COOLING * dt)
            if (w.heat >= 1.0) {
                w.melted = true
                w.amps = 0.0
                changedTopology = true
                events += BoardEvent.Melted(w)
            }
        }
        if (changedTopology) changed()
    }

    // ------------------------------------------------------------------ test and repair helpers

    /** Replaces a spent battery's charge (the game charges for it). */
    fun refill(c: Cell): Boolean {
        val p = parts[c] ?: return false
        if (p.kind.role != Role.BATTERY) return false
        p.charge = 1.0
        changed()
        return true
    }

    private fun key(a: Cell, b: Cell): Pair<Cell, Cell> =
        if (a.y < b.y || (a.y == b.y && a.x < b.x)) a to b else b to a
}
