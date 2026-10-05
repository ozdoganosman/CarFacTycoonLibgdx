package com.toyquaise.vektor.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class LevelsTest {
    private fun level(n: Int) = Levels.all[n - 1]

    @Test
    fun `levels are numbered in order, in chapter order, on one board size`() {
        Levels.all.forEachIndexed { i, l ->
            assertEquals(i + 1, l.number)
            assertEquals(5, l.width)
            assertEquals(7, l.height)
            assertEquals(2, l.crystals.size, "level ${l.number}")
            assertEquals(Level.FUEL, l.fuel)
        }
        val chapters = Levels.all.map { it.chapter }
        assertEquals(chapters.sortedBy { it.ordinal }, chapters)
        assertEquals(Chapter.entries.toSet(), chapters.toSet())
        assertTrue(Levels.all.filter { it.chapter == Chapter.WIND }.all { !it.wind.isZero })
        assertTrue(Levels.all.filter { it.chapter != Chapter.WIND }.all { it.wind.isZero })
        assertTrue(Levels.all.filter { it.chapter == Chapter.LAUNCH }.all { it.asteroids.isEmpty() })
    }

    @TestFactory
    fun `every level can be finished with both crystals by following the hints`() = Levels.all.map { l ->
        DynamicTest.dynamicTest("level ${l.number}") {
            val best = requireNotNull(Solver.solve(l)) { "level ${l.number} cannot be finished" }
            assertEquals(2, best.crystals, "level ${l.number}: not every crystal can be brought along")
            assertTrue(best.fuel <= Level.FUEL + 1e-9)
            val f = Flight(l)
            var steps = 0
            while (!f.arrived) {
                val t = requireNotNull(Solver.hint(f)) { "level ${l.number}: no hint at ${f.position}" }
                assertNull(f.fly(t))
                assertTrue(++steps < 10)
            }
            assertEquals(3, f.stars)
            assertTrue(f.fuelUsed <= Level.FUEL + 1e-9)
        }
    }

    @Test
    fun `the crystals always cost a detour or a trick after the first two levels`() {
        for (l in Levels.all.drop(2)) {
            val plain = requireNotNull(Solver.leastFuel(l))
            val best = Solver.solve(l)!!
            assertTrue(best.fuel > plain + 1e-9 || best.thrusts.size > 1, "level ${l.number} is a single straight pull")
        }
    }

    @Test
    fun `asteroids stand in the straight way in the belt`() {
        for (n in listOf(7, 9)) {
            val l = level(n)
            assertNotNull(Space.asteroidOnWay(l, l.start, l.portal), "level $n")
        }
    }

    @Test
    fun `the wind takes the ship further than five units of fuel could`() {
        for (n in listOf(11, 13, 14, 15)) {
            val l = level(n)
            assertTrue((l.portal - l.start).length > Level.FUEL, "level $n")
        }
    }

    @Test
    fun `level 3 goes past the portal and back`() {
        val best = Solver.solve(level(3))!!
        assertEquals(listOf(Vec(0, 4), Vec(0, -1)), best.thrusts)
        assertEquals(5.0, best.fuel, 1e-9)
    }

    @Test
    fun `a hint is null when the portal is out of reach`() {
        val f = Flight(level(1))
        f.fly(Vec(0, -1))
        f.fly(Vec(0, -1))
        // At (2, 0) with 3 units left, the portal at (2, 5) is 5 away.
        assertNull(Solver.hint(f))
    }
}
