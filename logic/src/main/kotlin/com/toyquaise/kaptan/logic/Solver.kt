package com.toyquaise.kaptan.logic

/**
 * Finds the best way to the harbor: the fewest moves, then the shortest distance sailed. Used for
 * the stars (the best a level allows), for hints, and by the tests to prove every level can be
 * finished. Hands are small, so it simply tries everything, remembering the states it has seen.
 */
object Solver {
    sealed interface Action {
        /** Split the card with this vector into its components. */
        data class Split(val card: Vec) : Action

        /** Play these card vectors with these factors (two of them: pulled together). */
        data class Move(val picks: List<Pair<Vec, Factor>>) : Action
    }

    class Solution(val actions: List<Action>, val plays: Int, val length: Double) {
        val moves: List<Action.Move> get() = actions.filterIsInstance<Action.Move>()
    }

    fun solve(level: Level): Solution? = solve(level, level.start, level.cards, level.tokens)

    /** The best way on from where [voyage] is now; an empty solution if it has arrived. */
    fun solve(voyage: Voyage): Solution? =
        solve(voyage.level, voyage.position, voyage.hand.map { it.vec }, voyage.tokens)

    fun solve(level: Level, from: Vec, hand: List<Vec>, tokens: Tokens): Solution? =
        Search(level).best(from, hand.sortedWith(order), tokens)

    /** The first step of the best way on, turned into the cards of [voyage]'s hand; null if the harbor cannot be reached from here. */
    fun hint(voyage: Voyage): Hint? {
        val first = solve(voyage)?.actions?.firstOrNull() ?: return null
        return when (first) {
            is Action.Split -> voyage.hand.firstOrNull { it.vec == first.card }?.let { Hint.Split(it) }
            is Action.Move -> {
                val used = HashSet<Int>()
                val picks = first.picks.map { (vec, factor) ->
                    val card = voyage.hand.first { it.vec == vec && it.id !in used }
                    used += card.id
                    Pick(card.id, factor)
                }
                Hint.Move(Play(picks))
            }
        }
    }

    sealed interface Hint {
        data class Split(val card: Card) : Hint
        data class Move(val play: Play) : Hint
    }

    private val order = compareBy<Vec>({ it.x }, { it.y })

    private class Search(val level: Level) {
        private val memo = HashMap<String, Solution?>()

        private fun better(a: Solution?, b: Solution?): Solution? = when {
            a == null -> b
            b == null -> a
            b.plays < a.plays -> b
            b.plays == a.plays && b.length < a.length - 1e-9 -> b
            else -> a
        }

        private fun prepend(action: Action, rest: Solution?, plays: Int, length: Double): Solution? =
            rest?.let { Solution(listOf(action) + it.actions, it.plays + plays, it.length + length) }

        fun best(pos: Vec, hand: List<Vec>, tokens: Tokens): Solution? {
            if (pos == level.harbor) return Solution(emptyList(), 0, 0.0)
            if (hand.isEmpty()) return null
            val key = "$pos|$hand|$tokens"
            if (memo.containsKey(key)) return memo[key]
            var best: Solution? = null

            val distinct = hand.distinct()
            if (tokens.splits > 0) for (c in distinct) {
                if (c.isAxial) continue
                val (cx, cy) = c.components
                val next = (hand - c + cx + cy).sortedWith(order)
                best = better(best, prepend(Action.Split(c), best(pos, next, tokens.copy(splits = tokens.splits - 1)), 0, 0.0))
            }

            val factors = tokens.factors()
            for (c in distinct) for (f in factors) {
                val v = f.applyTo(c) ?: continue
                val left = tokens.spend(listOf(f), pair = false) ?: continue
                val to = step(pos, v) ?: continue
                val rest = best(to, hand - c, left)
                best = better(best, prepend(Action.Move(listOf(c to f)), rest, 1, (to - pos).length))
            }

            if (tokens.pairs > 0) for (i in hand.indices) for (j in i + 1 until hand.size) {
                val a = hand[i]
                val b = hand[j]
                // Equal neighbours in a sorted hand give the same pairs; try each pair of values once.
                if (i > 0 && hand[i - 1] == a) continue
                if (j > i + 1 && hand[j - 1] == b) continue
                for (fa in factors) for (fb in factors) {
                    val va = fa.applyTo(a) ?: continue
                    val vb = fb.applyTo(b) ?: continue
                    val left = tokens.spend(listOf(fa, fb), pair = true) ?: continue
                    val to = step(pos, va + vb) ?: continue
                    val rest = best(to, hand - a - b, left)
                    best = better(best, prepend(Action.Move(listOf(a to fa, b to fb)), rest, 1, (to - pos).length))
                }
            }

            memo[key] = best
            return best
        }

        /** Where a move of [v] (plus the current) from [pos] ends, or null if it cannot be made. */
        private fun step(pos: Vec, v: Vec): Vec? {
            val delta = v + level.current
            if (delta.isZero) return null
            val to = pos + delta
            if (!Sea.inside(level, to)) return null
            if (Sea.rockOnWay(level, pos, to) != null) return null
            return to
        }
    }
}
