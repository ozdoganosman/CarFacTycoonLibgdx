package com.toyquaise.vektor.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlightTest {
    private fun space(map: String, wind: Vec = Vec.ZERO) = Flight(Level.parse(1, Chapter.LAUNCH, map, wind))

    private val open = """
        ......
        ......
        ......
        ....P.
        ..*...
        ......
        .S....
        ......
    """

    @Test
    fun `the map is read top row first, with y going up`() {
        val l = Level.parse(1, Chapter.LAUNCH, open)
        assertEquals(5, l.width)
        assertEquals(7, l.height)
        assertEquals(Vec(1, 1), l.start)
        assertEquals(Vec(4, 4), l.portal)
        assertEquals(listOf(Vec(2, 3)), l.crystals)
        assertEquals(5.0, l.fuel)
    }

    @Test
    fun `each piece burns its magnitude, and the pieces add up tip to tail`() {
        val f = space(open)
        assertNull(f.fly(Vec(1, 2)))
        assertEquals(Math.sqrt(5.0), f.fuelUsed, 1e-9)
        assertEquals(Vec(2, 3), f.position)
        assertEquals(listOf(Vec(2, 3)), f.pieces.single().crystals)
        assertNull(f.fly(Vec(2, 1)))
        assertTrue(f.arrived)
        assertEquals(Vec(3, 3), f.displacement)
        assertEquals(2 * Math.sqrt(5.0), f.travelled, 1e-9)
        assertTrue(f.travelled > f.displacement.length)
        assertEquals(2, f.stars)
        assertEquals(Problem.ARRIVED, f.check(Vec(1, 0)).problem)
    }

    @Test
    fun `a piece longer than the fuel left cannot be flown`() {
        val f = space(open)
        assertEquals(Problem.NO_FUEL, f.check(Vec(4, 4)).problem)
        assertNull(f.check(Vec(3, 4)).problem)
        f.fly(Vec(0, 4))
        assertEquals(1.0, f.fuelLeft, 1e-9)
        assertEquals(Problem.NO_FUEL, f.check(Vec(1, 1)).problem)
        assertNull(f.check(Vec(1, 0)).problem)
        f.fly(Vec(1, 0))
        assertTrue(f.stranded)
    }

    @Test
    fun `no zero piece, and no leaving the map`() {
        val f = space(open)
        assertEquals(Problem.ZERO, f.check(Vec.ZERO).problem)
        assertEquals(Problem.OFF_MAP, f.check(Vec(-2, 0)).problem)
    }

    @Test
    fun `asteroids block a way that passes closer than half a square`() {
        val map = """
            ......
            ......
            ....P.
            ......
            .##...
            ......
            .S....
            ......
        """
        val f = space(map)
        val aim = f.check(Vec(0, 4))
        assertEquals(Problem.ASTEROID, aim.problem)
        assertEquals(Vec(1, 3), aim.asteroid)
        // Between two asteroids side by side: blocked, even on the slant.
        assertEquals(Problem.ASTEROID, f.check(Vec(1, 4)).problem)
        // Corner to corner is open.
        assertTrue(Space.distanceToSegment(Vec(1, 0), Vec(0, 0), Vec(1, 1)) > Space.ASTEROID_REACH)
    }

    @Test
    fun `crystals are picked up only by flying right over them`() {
        val f = space(open)
        // (1, 3) passes 0.32 squares from the crystal: not over it.
        assertEquals(emptyList<Vec>(), f.check(Vec(1, 3)).crystals)
        assertEquals(listOf(Vec(2, 3)), f.check(Vec(1, 2)).crystals)
        // (2, 4) is twice (1, 2): it flies right over the crystal on its way.
        assertEquals(listOf(Vec(2, 3)), f.check(Vec(2, 4)).crystals)
    }

    @Test
    fun `the wind is added to every piece for free`() {
        val f = space(open, wind = Vec(1, 0))
        val aim = f.check(Vec(0, 2))
        assertEquals(Vec(1, 2), aim.delta)
        assertEquals(2.0, aim.cost)
        f.fly(Vec(0, 2))
        assertEquals(Vec(2, 3), f.position)
        assertEquals(2.0, f.fuelUsed)
        assertEquals(Math.sqrt(5.0), f.travelled, 1e-9)
    }

    @Test
    fun `undo and reset give the fuel back`() {
        val f = space(open)
        f.fly(Vec(1, 2))
        f.fly(Vec(0, 1))
        assertTrue(f.undo())
        assertEquals(Vec(2, 3), f.position)
        assertEquals(listOf(Vec(2, 3)), f.collected.toList())
        assertTrue(f.undo())
        assertEquals(Vec(1, 1), f.position)
        assertEquals(5.0, f.fuelLeft)
        assertTrue(f.collected.isEmpty())
        assertFalse(f.undo())
        f.fly(Vec(1, 0))
        f.reset()
        assertEquals(Vec(1, 1), f.position)
        assertFalse(f.canUndo)
    }

    @Test
    fun `whole vectors up to five, shortest first`() {
        val v = Vec.upTo(5.0)
        assertEquals(80, v.size)
        assertEquals(1.0, v.first().length)
        assertEquals(5.0, v.last().length)
        assertTrue(Vec(3, 4) in v && Vec(4, 4) !in v)
    }
}
