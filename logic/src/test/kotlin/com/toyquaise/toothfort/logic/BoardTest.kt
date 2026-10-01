package com.toyquaise.toothfort.logic

import com.toyquaise.toothfort.logic.board.Board
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Electric
import com.toyquaise.toothfort.logic.board.Layout
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Port
import com.toyquaise.toothfort.logic.board.WireGauge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class BoardTest {
    private fun board() = Board(10.0, 6.0) { _, _ -> false }
    private fun v(x: Double, y: Double) = Vec2(x, y)
    private val Part.plus get() = Port.Terminal(this, true)
    private val Part.minus get() = Port.Terminal(this, false)

    /** Asserts that [x] is there and returns it. */
    private fun <T : Any> there(x: T?, what: String): T {
        assertNotNull(x, what)
        return x!!
    }

    private fun Board.connect(a: Port, b: Port, vararg via: Vec2, gauge: WireGauge = WireGauge.THIN) =
        there(addWire(a, b, via.toList(), gauge), "wire $a -> $b")

    private fun Board.put(kind: PartKind, x: Double, y: Double, angle: Double = 0.0) =
        there(place(kind, v(x, y), angle), "$kind at ($x, $y)")

    /**
     * A battery at (1.5, 1.5) feeding a machine at (4.5, 1.5) in a straight line, and back round
     * underneath: 10.32 units of wire.
     */
    private fun loop(machine: PartKind, gauge: WireGauge = WireGauge.THIN): Pair<Board, Part> {
        val b = board()
        val battery = b.put(PartKind.BATTERY, 1.5, 1.5)
        val m = b.put(machine, 4.5, 1.5)
        b.connect(battery.plus, m.minus, gauge = gauge)
        b.connect(m.plus, battery.minus, v(5.5, 1.5), v(5.5, 2.5), v(0.5, 2.5), v(0.5, 1.5), gauge = gauge)
        return b to m
    }

    @Test
    fun `a closed loop runs the brush and the wire's length costs voltage`() {
        val (b, brush) = loop(PartKind.BRUSH)
        b.solve()
        val wireOhms = 10.32 * WireGauge.THIN.ohmsPerUnit
        val expected = 3.0 * 6.0 / (6.0 + wireOhms + Electric.BATTERY_OHMS)
        assertEquals(expected, abs(brush.volts), 1e-6)
        assertTrue(brush.performance in 0.5..0.8, "performance ${brush.performance}")
    }

    @Test
    fun `thick wire loses less voltage`() {
        val thin = loop(PartKind.BRUSH, WireGauge.THIN).also { it.first.solve() }.second
        val thick = loop(PartKind.BRUSH, WireGauge.THICK).also { it.first.solve() }.second
        assertTrue(abs(thick.volts) > abs(thin.volts) + 0.25)
    }

    @Test
    fun `an open loop carries nothing`() {
        val (b, brush) = loop(PartKind.BRUSH)
        b.removeWire(b.wires.last())
        b.solve()
        assertEquals(0.0, brush.volts, 1e-9)
        assertEquals(0.0, brush.performance)
    }

    @Test
    fun `a battery dropped near another's plus end snaps on in series`() {
        val b = board()
        val b1 = b.put(PartKind.BATTERY, 1.5, 1.5)
        // Dropped roughly, slightly turned, its − end near b1's + end.
        val b2 = b.put(PartKind.BATTERY, 2.4, 1.6, 10.0)
        assertEquals(1.5 + 2 * Layout.TERMINAL_OFFSET, b2.pos.x, 1e-9)
        assertEquals(1.5, b2.pos.y, 1e-9)
        assertEquals(0.0, b2.angle, 1e-9)
        assertTrue(b2.minusTerminal.distanceTo(b1.plusTerminal) < 1e-9)

        val cannon = b.put(PartKind.PASTE_CANNON, 5.5, 1.5)
        b.connect(b2.plus, cannon.minus)
        b.connect(cannon.plus, b1.minus, v(6.5, 1.5), v(6.5, 2.5), v(0.5, 2.5), v(0.5, 1.5))
        b.solve()
        assertTrue(cannon.voltageRatio > 0.7, "two batteries: ${cannon.voltageRatio}")

        // Pull the second battery away: the cannon gets nothing.
        b.remove(b2)
        b.solve()
        assertEquals(0.0, cannon.volts, 1e-9)
    }

    @Test
    fun `one battery barely moves the 6 V paste ball`() {
        val (b, cannon) = loop(PartKind.PASTE_CANNON)
        b.solve()
        assertTrue(cannon.voltageRatio < 0.5, "${cannon.voltageRatio}")
    }

    @Test
    fun `a battery snapped on the wrong way round cancels the other`() {
        val b = board()
        val b1 = b.put(PartKind.BATTERY, 1.5, 1.5)
        val b2 = b.put(PartKind.BATTERY, 2.4, 1.5, 180.0) // its + end near b1's + end
        assertTrue(b2.plusTerminal.distanceTo(b1.plusTerminal) < 1e-9)
        val brush = b.put(PartKind.BRUSH, 5.5, 1.5)
        b.connect(b2.minus, brush.minus)
        b.connect(brush.plus, b1.minus, v(6.5, 1.5), v(6.5, 2.5), v(0.5, 2.5), v(0.5, 1.5))
        b.solve()
        assertEquals(0.0, brush.volts, 1e-6)
    }

    @Test
    fun `parts cannot overlap, and the blocked map keeps the trail clear`() {
        val trail = Board(10.0, 6.0) { p, r -> abs(p.y - 4.0) < 0.4 + r }
        assertNotNull(trail.place(PartKind.BATTERY, v(1.5, 1.5), 0.0))
        assertNull(trail.place(PartKind.BRUSH, v(1.5, 1.9), 90.0), "overlaps the battery")
        assertNull(trail.place(PartKind.BRUSH, v(5.0, 4.2), 0.0), "on the trail")
        assertNull(trail.place(PartKind.BRUSH, v(0.05, 1.0), 0.0), "off the counter")
        assertNotNull(trail.place(PartKind.BRUSH, v(5.0, 2.5), 0.0))
    }

    @Test
    fun `turning a part drags its wires and lets go of its neighbour`() {
        val b = board()
        val b1 = b.put(PartKind.BATTERY, 1.5, 1.5)
        val b2 = b.put(PartKind.BATTERY, 2.4, 1.5)
        val brush = b.put(PartKind.BRUSH, 5.5, 1.5)
        val w = b.connect(b2.plus, brush.minus)
        b.connect(brush.plus, b1.minus, v(6.5, 1.5), v(6.5, 2.5), v(0.5, 2.5), v(0.5, 1.5))
        val before = b.length(w)
        b.rotate(b2, 90.0)
        assertTrue(abs(b.length(w) - before) > 0.1, "the wire's end moved with the terminal")
        b.solve()
        assertEquals(0.0, brush.volts, 1e-9, "the batteries no longer touch")
    }

    @Test
    fun `a junction joins wires`() {
        val b = board()
        val battery = b.put(PartKind.BATTERY, 1.5, 1.5)
        val left = b.put(PartKind.BRUSH, 4.5, 0.8)
        val right = b.put(PartKind.BRUSH, 4.5, 2.2)
        val top = there(b.addJunction(v(3.0, 1.5)), "junction")
        val bottom = there(b.addJunction(v(6.0, 1.5)), "junction")
        b.connect(battery.plus, Port.Clip(top))
        b.connect(Port.Clip(top), left.minus)
        b.connect(Port.Clip(top), right.minus)
        b.connect(left.plus, Port.Clip(bottom))
        b.connect(right.plus, Port.Clip(bottom))
        b.connect(Port.Clip(bottom), battery.minus, v(6.0, 3.0), v(0.5, 3.0), v(0.5, 1.5))
        b.solve()
        assertTrue(left.performance > 0.3 && right.performance > 0.3)
        assertEquals(left.volts, right.volts, 0.05, "two brushes in parallel see about the same voltage")
        assertSame(battery, b.partAt(v(1.6, 1.4)))
        assertEquals(Port.Clip(top), b.portAt(v(3.05, 1.5), 0.2))
    }

    @Test
    fun `too much voltage burns a machine out`() {
        val b = board()
        val b1 = b.put(PartKind.BATTERY, 1.5, 1.5)
        val b2 = b.put(PartKind.BATTERY, 2.4, 1.5)
        val brush = b.put(PartKind.BRUSH, 4.5, 1.5) // a 3 V brush on 6 V
        b.connect(b2.plus, brush.minus)
        b.connect(brush.plus, b1.minus, v(5.5, 1.5), v(5.5, 2.5), v(0.5, 2.5), v(0.5, 1.5))
        var t = 0.0
        while (t < 10.0 && b.events.none { it is BoardEvent.Burnt }) { b.update(0.05); t += 0.05 }
        assertTrue(brush.broken, "burnt out")
        assertTrue(t in 1.0..6.0, "it takes a few seconds, took $t")
        b.solve()
        assertEquals(0.0, brush.amps, 1e-9, "a burnt machine is an open circuit")
    }

    @Test
    fun `a short circuit melts a thin wire`() {
        val b = board()
        val battery = b.put(PartKind.BATTERY, 1.5, 1.5)
        b.connect(battery.plus, battery.minus, v(2.5, 1.5), v(2.5, 0.5), v(0.5, 0.5), v(0.5, 1.5))
        var t = 0.0
        while (t < 10.0 && b.events.none { it is BoardEvent.Melted }) { b.update(0.05); t += 0.05 }
        assertTrue(b.events.any { it is BoardEvent.Melted }, "melted after $t s")
        assertTrue(t < 3.0)
    }

    @Test
    fun `a fuse blows before the wire melts`() {
        val b = board()
        val battery = b.put(PartKind.BATTERY, 1.5, 1.5)
        val fuse = b.put(PartKind.FUSE, 1.5, 0.5)
        b.connect(battery.plus, fuse.plus, v(2.3, 1.5), v(2.3, 0.5))
        b.connect(fuse.minus, battery.minus, v(0.7, 0.5), v(0.7, 1.5))
        b.update(0.05)
        assertTrue(b.events.any { it is BoardEvent.Blown })
        assertTrue(b.wires.none { it.melted })
        b.update(0.05)
        assertEquals(0.0, battery.amps, 1e-9)
    }

    @Test
    fun `batteries in parallel last longer`() {
        fun build(two: Boolean): Board = board().apply {
            val b1 = put(PartKind.BATTERY, 1.5, 1.5)
            val plus = Port.Clip(addJunction(v(2.5, 2.5))!!)
            val minus = Port.Clip(addJunction(v(0.5, 2.5))!!)
            connect(b1.plus, plus)
            connect(b1.minus, minus)
            if (two) {
                val b2 = put(PartKind.BATTERY, 1.5, 3.5)
                connect(b2.plus, plus)
                connect(b2.minus, minus)
            }
            val brush = put(PartKind.BRUSH, 5.5, 2.5)
            connect(plus, brush.minus)
            connect(brush.plus, minus, v(6.5, 2.5), v(6.5, 4.5), v(0.5, 4.5))
        }
        val single = build(false)
        val pair = build(true)
        repeat(1200) { single.update(0.05); pair.update(0.05) } // one minute
        val used1 = 1.0 - single.parts.first().charge
        val usedPair = pair.parts.filter { it.kind == PartKind.BATTERY }.map { 1.0 - it.charge }
        assertEquals(used1 / 2, usedPair[0], used1 * 0.1)
        assertEquals(usedPair[0], usedPair[1], 1e-6, "the same wiring on both sides: an even share")
        // ...and the voltage stays the same: parallel does not add volts.
        val b1 = single.parts.first { it.kind == PartKind.BRUSH }
        val b2 = pair.parts.first { it.kind == PartKind.BRUSH }
        assertEquals(b1.volts, b2.volts, 0.2)
    }

    @Test
    fun `an empty battery stops driving`() {
        val (b, brush) = loop(PartKind.BRUSH)
        repeat(20 * 600) { b.update(0.05) }
        val battery = b.parts.first { it.kind == PartKind.BATTERY }
        assertEquals(0.0, battery.charge, 1e-9)
        assertTrue(b.events.any { it is BoardEvent.Depleted })
        b.solve()
        assertEquals(0.0, brush.performance)
    }

    @Test
    fun `a wire needs two different ends`() {
        val b = board()
        val battery = b.put(PartKind.BATTERY, 1.5, 1.5)
        assertFalse(b.canWire(battery.plus, battery.plus))
        assertTrue(b.canWire(battery.plus, battery.minus), "a short circuit is allowed, and teaches something")
    }
}
