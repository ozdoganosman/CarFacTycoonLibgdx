package com.toyquaise.kaptan.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoyageTest {
    private fun sea(map: String, vararg cards: Vec, current: Vec = Vec.ZERO, tokens: Tokens = Tokens()) =
        Voyage(Level.parse(1, Chapter.HARBOR, map, cards.toList(), current, tokens))

    private val open = """
        ......
        ......
        ....L.
        ......
        ......
        ......
        .S....
        ......
    """

    private fun Voyage.id(v: Vec) = hand.first { it.vec == v }.id

    @Test
    fun `the map is read top row first, with y going up`() {
        val l = Level.parse(1, Chapter.HARBOR, open, listOf(Vec(1, 0)))
        assertEquals(5, l.width)
        assertEquals(7, l.height)
        assertEquals(Vec(1, 1), l.start)
        assertEquals(Vec(4, 5), l.harbor)
    }

    @Test
    fun `cards played tip to tail add up, and the road is longer than the displacement`() {
        val v = sea(open, Vec(3, 0), Vec(0, 4))
        assertNull(v.play(Play(Pick(v.id(Vec(3, 0))))))
        assertEquals(Vec(4, 1), v.position)
        assertFalse(v.arrived)
        assertNull(v.play(Play(Pick(v.id(Vec(0, 4))))))
        assertTrue(v.arrived)
        assertEquals(2, v.plays)
        assertEquals(7.0, v.travelled, 1e-9)
        assertEquals(Vec(3, 4), v.displacement)
        assertEquals(5.0, v.displacement.length, 1e-9)
        assertEquals(Problem.ARRIVED, v.check(Play(Pick(0))).problem)
    }

    @Test
    fun `a move may not leave the sea`() {
        val v = sea(open, Vec(-2, 0))
        val c = v.check(Play(Pick(v.id(Vec(-2, 0)))))
        assertEquals(Problem.OFF_SEA, c.problem)
        assertEquals(Vec(-1, 1), c.to)
    }

    @Test
    fun `rocks on the way, or close to it, stop the move`() {
        val map = """
            ......
            ......
            ....L.
            ......
            ......
            ......
            .S.#..
            ......
        """
        val v = sea(map, Vec(3, 0), Vec(0, 4))
        val c = v.check(Play(Pick(v.id(Vec(3, 0)))))
        assertEquals(Problem.ROCK, c.problem)
        assertEquals(Vec(3, 1), c.rock)
        assertNull(v.check(Play(Pick(v.id(Vec(0, 4))))).problem)
    }

    @Test
    fun `how close is too close to a rock`() {
        // A (1, 3) step passes the point beside it 0.32 squares away: aground.
        assertTrue(Sea.distanceToSegment(Vec(0, 1), Vec(0, 0), Vec(1, 3)) < Sea.ROCK_REACH)
        // A (1, 2) step passes 0.45 away: clear.
        assertTrue(Sea.distanceToSegment(Vec(0, 1), Vec(0, 0), Vec(1, 2)) > Sea.ROCK_REACH)
        // Squeezing diagonally between two rocks is fine: 0.71 from each.
        assertTrue(Sea.distanceToSegment(Vec(1, 0), Vec(0, 0), Vec(1, 1)) > Sea.ROCK_REACH)
    }

    @Test
    fun `the current is added to every move`() {
        val v = sea(open, Vec(0, 2), current = Vec(1, 0))
        val c = v.check(Play(Pick(v.id(Vec(0, 2)))))
        assertEquals(listOf(Vec(0, 2)), c.vectors)
        assertEquals(Vec(1, 2), c.delta)
        v.play(Play(Pick(v.id(Vec(0, 2)))))
        assertEquals(Vec(2, 3), v.position)
        assertEquals(Vec(1, 0), v.legs.single().current)
    }

    @Test
    fun `a move that the current cancels is no move`() {
        val v = sea(open, Vec(-1, 0), current = Vec(1, 0))
        assertEquals(Problem.NO_MOVE, v.check(Play(Pick(v.id(Vec(-1, 0))))).problem)
    }

    @Test
    fun `factors multiply a card and use up tokens`() {
        val v = sea(open, Vec(1, 2), Vec(0, 1), tokens = Tokens(doubles = 1, flips = 1))
        val double = Factor(stretch = Stretch.DOUBLE)
        assertEquals(Vec(2, 4), v.check(Play(Pick(v.id(Vec(1, 2)), double))).delta)
        assertNull(v.play(Play(Pick(v.id(Vec(1, 2)), double))))
        assertEquals(0, v.tokens.doubles)
        assertEquals(Problem.NO_DOUBLE, v.check(Play(Pick(v.id(Vec(0, 1)), double))).problem)
        assertEquals(Vec(0, -1), v.check(Play(Pick(v.id(Vec(0, 1)), Factor(flip = true)))).delta)
    }

    @Test
    fun `halving needs even components`() {
        val v = sea(open, Vec(2, 4), Vec(1, 2), tokens = Tokens(halves = 2))
        val half = Factor(stretch = Stretch.HALF)
        assertEquals(Vec(1, 2), v.check(Play(Pick(v.id(Vec(2, 4)), half))).delta)
        assertEquals(Problem.ODD_HALF, v.check(Play(Pick(v.id(Vec(1, 2)), half))).problem)
        assertEquals(-0.5, Factor(true, Stretch.HALF).value)
    }

    @Test
    fun `splitting puts the two components in the card's place`() {
        val v = sea(open, Vec(1, 0), Vec(3, 4), Vec(0, 1), tokens = Tokens(splits = 1))
        assertEquals(Problem.CANT_SPLIT, v.split(v.id(Vec(1, 0))))
        assertNull(v.split(v.id(Vec(3, 4))))
        assertEquals(listOf(Vec(1, 0), Vec(3, 0), Vec(0, 4), Vec(0, 1)), v.hand.map { it.vec })
        assertEquals(0, v.tokens.splits)
        assertEquals(0, v.plays)
        assertEquals(Problem.NO_SPLIT, v.split(v.id(Vec(0, 4))))
    }

    @Test
    fun `two tugboats pulling together sail the diagonal of the parallelogram`() {
        val map = """
            ......
            ......
            ......
            ....L.
            .#....
            ......
            .S#...
            ......
        """
        val v = sea(map, Vec(3, 0), Vec(0, 3), tokens = Tokens(pairs = 1))
        val a = v.id(Vec(3, 0))
        val b = v.id(Vec(0, 3))
        assertEquals(Problem.ROCK, v.check(Play(Pick(a))).problem)
        assertEquals(Problem.ROCK, v.check(Play(Pick(b))).problem)
        assertEquals(Problem.SAME_CARD, v.check(Play(Pick(a), Pick(a))).problem)
        assertNull(v.play(Play(Pick(a), Pick(b))))
        assertTrue(v.arrived)
        assertEquals(1, v.plays)
        assertTrue(v.legs.single().together)
        assertEquals(Math.sqrt(18.0), v.travelled, 1e-9)
        assertEquals(0, v.tokens.pairs)
    }

    @Test
    fun `pulling together needs a pair token`() {
        val v = sea(open, Vec(3, 0), Vec(0, 4))
        assertEquals(Problem.NO_PAIR, v.check(Play(Pick(v.id(Vec(3, 0))), Pick(v.id(Vec(0, 4))))).problem)
    }

    @Test
    fun `undo takes back moves and splits one at a time, reset starts over`() {
        val v = sea(open, Vec(3, 4), Vec(1, 0), tokens = Tokens(splits = 1, doubles = 1))
        v.play(Play(Pick(v.id(Vec(1, 0)), Factor(stretch = Stretch.DOUBLE))))
        v.split(v.id(Vec(3, 4)))
        v.play(Play(Pick(v.id(Vec(0, 4)))))
        assertEquals(Vec(3, 5), v.position)
        assertTrue(v.undo())
        assertEquals(Vec(3, 1), v.position)
        assertEquals(listOf(Vec(3, 0), Vec(0, 4)), v.hand.map { it.vec })
        assertTrue(v.undo())
        assertEquals(listOf(Vec(3, 4)), v.hand.map { it.vec })
        assertEquals(1, v.tokens.splits)
        assertTrue(v.undo())
        assertEquals(Vec(1, 1), v.position)
        assertEquals(1, v.tokens.doubles)
        assertFalse(v.undo())
        v.play(Play(Pick(v.id(Vec(1, 0)))))
        v.reset()
        assertEquals(Vec(1, 1), v.position)
        assertEquals(2, v.hand.size)
        assertEquals(0, v.plays)
        assertFalse(v.canUndo)
    }

    @Test
    fun `the best way earns three stars`() {
        val v = sea(open, Vec(3, 0), Vec(0, 4), Vec(0, 2), Vec(0, 2))
        val best = Solver.solve(v.level)!!
        assertEquals(2, best.plays)
        v.play(Play(Pick(v.id(Vec(0, 4)))))
        v.play(Play(Pick(v.id(Vec(3, 0)))))
        assertEquals(3, v.stars(best))
        v.reset()
        v.play(Play(Pick(v.id(Vec(3, 0)))))
        v.play(Play(Pick(v.hand.first { it.vec == Vec(0, 2) }.id)))
        v.play(Play(Pick(v.id(Vec(0, 2)))))
        assertTrue(v.arrived)
        assertEquals(2, v.stars(best))
    }
}
