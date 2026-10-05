package com.toyquaise.kaptan.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class LevelsTest {
    private fun level(n: Int) = Levels.all[n - 1]

    private fun Level.without(tokens: Tokens) = Level(number, chapter, width, height, start, harbor, rocks, cards, current, tokens)

    private fun Voyage.id(v: Vec) = hand.first { it.vec == v }.id

    @Test
    fun `levels are numbered in order and fit a phone`() {
        Levels.all.forEachIndexed { i, l ->
            assertEquals(i + 1, l.number)
            assertTrue(l.width <= 5 && l.height <= 8, "level ${l.number} is too big")
            assertTrue(l.cards.size <= 6, "level ${l.number} has too many cards for one row")
        }
    }

    @TestFactory
    fun `every level can be finished by following the hints, for three stars`() = Levels.all.map { l ->
        DynamicTest.dynamicTest("level ${l.number}") {
            val best = requireNotNull(Solver.solve(l)) { "level ${l.number} cannot be finished" }
            val v = Voyage(l)
            var steps = 0
            while (!v.arrived) {
                when (val h = requireNotNull(Solver.hint(v)) { "level ${l.number}: no hint at ${v.position}" }) {
                    is Solver.Hint.Split -> assertNull(v.split(h.card.id))
                    is Solver.Hint.Move -> assertNull(v.play(h.play))
                }
                assertTrue(++steps < 20)
            }
            assertEquals(best.plays, v.plays)
            assertEquals(3, v.stars(best))
        }
    }

    @Test
    fun `chapters come in order`() {
        val chapters = Levels.all.map { it.chapter }
        assertEquals(chapters.sortedBy { it.ordinal }, chapters)
        assertEquals(Chapter.entries.toSet(), chapters.toSet())
    }

    @Test
    fun `level 3 - the backward card cannot go first`() {
        val v = Voyage(level(3))
        assertEquals(Problem.OFF_SEA, v.check(Play(Pick(v.id(Vec(0, -2))))).problem)
    }

    @Test
    fun `levels 4, 5, 6 and 13 need their multiplier`() {
        for (n in listOf(4, 5, 6, 13)) {
            val l = level(n)
            val best = Solver.solve(l)!!
            assertTrue(best.moves.any { m -> m.picks.any { !it.second.isOne } }, "level $n")
            val plain = Solver.solve(l.without(Tokens()))
            assertTrue(plain == null || plain.plays > best.plays, "level $n can be done as well without tokens")
        }
    }

    @Test
    fun `level 8 - the order of the cards matters because of a rock`() {
        val v = Voyage(level(8))
        assertEquals(Problem.ROCK, v.check(Play(Pick(v.id(Vec(3, 0))))).problem)
        assertNull(v.check(Play(Pick(v.id(Vec(0, 4))))).problem)
    }

    @Test
    fun `level 14 needs the split, level 15 the pair`() {
        assertNull(Solver.solve(level(14).without(Tokens())))
        val v = Voyage(level(14))
        assertEquals(Problem.ROCK, v.check(Play(Pick(v.id(Vec(3, 4))))).problem)
        assertNull(Solver.solve(level(15).without(Tokens())))
        assertTrue(Solver.solve(level(15))!!.moves.single().picks.size == 2)
    }

    @Test
    fun `a hint is null in a dead end`() {
        val v = Voyage(level(3))
        v.play(Play(Pick(v.id(Vec(0, 4)))))
        // From (1, 5) the harbor at (1, 4) needs -1: neither 5 nor -2 makes it.
        assertNull(Solver.hint(v))
        assertTrue(v.undo())
        assertNotNull(Solver.hint(v))
    }
}
