package com.toyquaise.toothfort.logic

import com.toyquaise.toothfort.logic.board.Board
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Electric
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Terrain
import com.toyquaise.toothfort.logic.board.WireGauge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardTest {
    private fun board() = Board(10, 6) { Terrain.GROUND }
    private fun c(x: Int, y: Int) = Cell(x, y)

    /** Lays a wire through [cells], one segment per neighbouring pair. */
    private fun Board.route(vararg cells: Cell, gauge: WireGauge = WireGauge.THIN) {
        for (i in 1 until cells.size) assertNotNull(addWire(cells[i - 1], cells[i], gauge), "wire ${cells[i - 1]} -> ${cells[i]}")
    }

    /**
     * A battery at (1,1) (+ to the east) feeding a machine at (4,1) and back round underneath:
     * 12 wire segments in all.
     */
    private fun loop(machine: PartKind, gauge: WireGauge = WireGauge.THIN): Board = board().apply {
        place(PartKind.BATTERY, c(1, 1), Dir.E)
        place(machine, c(4, 1), Dir.E)
        route(c(1, 1), c(2, 1), c(3, 1), c(4, 1), gauge = gauge)
        route(c(4, 1), c(5, 1), c(5, 2), c(4, 2), c(3, 2), c(2, 2), c(1, 2), c(0, 2), c(0, 1), c(1, 1), gauge = gauge)
    }

    private fun expectedVolts(machineOhms: Double, emf: Double, wireOhms: Double) =
        emf * machineOhms / (machineOhms + wireOhms + Electric.BATTERY_OHMS)

    @Test
    fun `a closed loop runs the brush and the thin wire costs some voltage`() {
        val b = loop(PartKind.BRUSH)
        b.solve()
        val brush = b.partAt(c(4, 1))!!
        val expected = expectedVolts(6.0, 3.0, 12 * WireGauge.THIN.ohmsPerSegment)
        assertEquals(expected, kotlin.math.abs(brush.volts), 1e-6)
        assertTrue(brush.performance in 0.5..0.75, "performance ${brush.performance}")
    }

    @Test
    fun `thick wire loses less voltage`() {
        val thin = loop(PartKind.BRUSH, WireGauge.THIN).also { it.solve() }.partAt(c(4, 1))!!
        val thick = loop(PartKind.BRUSH, WireGauge.THICK).also { it.solve() }.partAt(c(4, 1))!!
        assertTrue(kotlin.math.abs(thick.volts) > kotlin.math.abs(thin.volts) + 0.25)
        assertTrue(thick.performance > thin.performance)
    }

    @Test
    fun `an open loop carries nothing`() {
        val b = loop(PartKind.BRUSH)
        b.removeWire(c(0, 1), c(1, 1))
        b.solve()
        val brush = b.partAt(c(4, 1))!!
        assertEquals(0.0, brush.volts, 1e-9)
        assertEquals(0.0, brush.performance)
    }

    @Test
    fun `wires only meet a part at its terminals`() {
        val b = board()
        b.place(PartKind.BATTERY, c(1, 1), Dir.E)
        assertFalse(b.canWire(c(1, 0), c(1, 1)), "north side is not a terminal")
        assertTrue(b.canWire(c(2, 1), c(1, 1)), "east side is +")
        b.rotate(c(1, 1)) // now + faces south
        assertTrue(b.canWire(c(1, 2), c(1, 1)))
        assertFalse(b.canWire(c(2, 1), c(1, 1)))
    }

    @Test
    fun `two batteries in series run the 6 V paste ball, one battery barely moves it`() {
        val one = loop(PartKind.PASTE_CANNON).also { it.solve() }.partAt(c(4, 1))!!

        // A second battery pressed against the first one's + side, the same way round: 6 V.
        val b = board().apply {
            place(PartKind.BATTERY, c(1, 1), Dir.E)
            place(PartKind.BATTERY, c(2, 1), Dir.E)
            place(PartKind.PASTE_CANNON, c(4, 1), Dir.E)
            route(c(2, 1), c(3, 1), c(4, 1))
            route(c(4, 1), c(5, 1), c(5, 2), c(4, 2), c(3, 2), c(2, 2), c(1, 2), c(0, 2), c(0, 1), c(1, 1))
        }
        b.solve()
        val two = b.partAt(c(4, 1))!!
        assertTrue(one.voltageRatio < 0.5, "one battery: ${one.voltageRatio}")
        assertTrue(two.voltageRatio > 0.7, "two batteries: ${two.voltageRatio}")
        assertTrue(two.performance > 3 * one.performance)
    }

    @Test
    fun `a battery the wrong way round cancels the other`() {
        val b = board().apply {
            place(PartKind.BATTERY, c(1, 1), Dir.E)
            place(PartKind.BATTERY, c(2, 1), Dir.W) // − faces east: back to back
            place(PartKind.BRUSH, c(4, 1), Dir.E)
            route(c(2, 1), c(3, 1), c(4, 1))
            route(c(4, 1), c(5, 1), c(5, 2), c(4, 2), c(3, 2), c(2, 2), c(1, 2), c(0, 2), c(0, 1), c(1, 1))
        }
        b.solve()
        assertEquals(0.0, b.partAt(c(4, 1))!!.volts, 1e-6)
    }

    @Test
    fun `too much voltage burns a machine out`() {
        val b = board().apply {
            place(PartKind.BATTERY, c(1, 1), Dir.E)
            place(PartKind.BATTERY, c(2, 1), Dir.E)
            place(PartKind.BRUSH, c(4, 1), Dir.E) // a 3 V brush on 6 V
            route(c(2, 1), c(3, 1), c(4, 1))
            route(c(4, 1), c(5, 1), c(5, 2), c(4, 2), c(3, 2), c(2, 2), c(1, 2), c(0, 2), c(0, 1), c(1, 1))
        }
        var t = 0.0
        while (t < 10.0 && b.events.none { it is BoardEvent.Burnt }) { b.update(0.05); t += 0.05 }
        val brush = b.partAt(c(4, 1))!!
        assertTrue(brush.broken, "burnt out")
        assertTrue(t in 1.0..6.0, "it takes a few seconds, took $t")
        b.solve()
        assertEquals(0.0, brush.amps, 1e-9, "a burnt machine is an open circuit")
    }

    @Test
    fun `a short circuit melts a thin wire`() {
        val b = board().apply {
            place(PartKind.BATTERY, c(1, 1), Dir.E)
            route(c(1, 1), c(2, 1), c(2, 0), c(1, 0), c(0, 0), c(0, 1), c(1, 1))
        }
        var t = 0.0
        while (t < 10.0 && b.events.none { it is BoardEvent.Melted }) { b.update(0.05); t += 0.05 }
        assertTrue(b.events.any { it is BoardEvent.Melted }, "melted after $t s")
        assertTrue(t < 3.0)
    }

    @Test
    fun `a fuse blows before the wire melts`() {
        val b = board().apply {
            place(PartKind.BATTERY, c(1, 1), Dir.E)
            place(PartKind.FUSE, c(1, 0), Dir.E)
            route(c(1, 1), c(2, 1), c(2, 0), c(1, 0))
            route(c(1, 0), c(0, 0), c(0, 1), c(1, 1))
        }
        b.update(0.05)
        assertTrue(b.events.any { it is BoardEvent.Blown })
        assertTrue(b.allWires.none { it.melted })
        b.update(0.05)
        assertEquals(0.0, b.partAt(c(1, 1))!!.amps, 1e-9)
    }

    @Test
    fun `batteries in parallel last longer`() {
        fun build(two: Boolean) = board().apply {
            place(PartKind.BATTERY, c(1, 1), Dir.E)
            place(PartKind.BRUSH, c(5, 2), Dir.E)
            route(c(1, 1), c(2, 1), c(2, 2), c(3, 2), c(4, 2), c(5, 2)) // + bus
            route(c(5, 2), c(6, 2), c(6, 3), c(6, 4), c(5, 4), c(4, 4), c(3, 4), c(2, 4), c(1, 4), c(0, 4), c(0, 3), c(0, 2), c(0, 1), c(1, 1)) // − bus
            if (two) {
                place(PartKind.BATTERY, c(1, 3), Dir.E)
                route(c(1, 3), c(2, 3), c(2, 2))
                route(c(0, 3), c(1, 3))
            }
        }
        val single = build(false)
        val pair = build(true)
        repeat(1200) { single.update(0.05); pair.update(0.05) } // one minute
        val used1 = 1.0 - single.partAt(c(1, 1))!!.charge
        val used2a = 1.0 - pair.partAt(c(1, 1))!!.charge
        val used2b = 1.0 - pair.partAt(c(1, 3))!!.charge
        // The pair shares the load (the battery with the shorter wire a little more), so each
        // one lasts well over the single battery's time.
        assertTrue(maxOf(used2a, used2b) < used1 * 0.75, "single $used1, pair $used2a + $used2b")
        assertEquals(used1, used2a + used2b, used1 * 0.15)
        // ...and the voltage stays the same: parallel does not add volts.
        assertEquals(single.partAt(c(5, 2))!!.volts, pair.partAt(c(5, 2))!!.volts, 0.2)
    }

    @Test
    fun `an empty battery stops driving`() {
        val b = loop(PartKind.BRUSH)
        repeat(20 * 600) { b.update(0.05) } // ~0.4 A drains 120 C in about five minutes
        val battery = b.partAt(c(1, 1))!!
        assertEquals(0.0, battery.charge, 1e-9)
        assertTrue(b.events.any { it is BoardEvent.Depleted })
        b.solve()
        assertEquals(0.0, b.partAt(c(4, 1))!!.performance)
    }
}
