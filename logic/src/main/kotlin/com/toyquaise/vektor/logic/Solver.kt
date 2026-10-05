package com.toyquaise.vektor.logic

import java.util.PriorityQueue

/**
 * Finds the best flight from any point: the most crystals brought to the portal, then the least
 * fuel, then the fewest moves. Used for hints and by the tests, which prove that every level can
 * be finished with all its crystals on five units of fuel. There are few states (a grid point and
 * the crystals picked up), so it simply runs Dijkstra over all of them.
 */
object Solver {
    class Plan(val thrusts: List<Vec>, val fuel: Double, val crystals: Int)

    /** The best plan for [level] from its start. */
    fun solve(level: Level): Plan? = solve(level, level.start, emptySet(), level.fuel)

    /** The best plan on from where [flight] is now (empty when it has arrived). */
    fun solve(flight: Flight): Plan? = solve(flight.level, flight.position, flight.collected, flight.fuelLeft)

    /** The next move of the best plan, or null if the portal cannot be reached from here. */
    fun hint(flight: Flight): Vec? = solve(flight)?.thrusts?.firstOrNull()

    /** The least fuel needed to reach the portal from the start, crystals or not. */
    fun leastFuel(level: Level): Double? = search(level, level.start, emptySet(), level.fuel)
        .filter { it.key.pos == level.portal }.values.minOfOrNull { it.fuel }

    fun solve(level: Level, from: Vec, collected: Set<Vec>, fuel: Double): Plan? {
        if (from == level.portal) return Plan(emptyList(), 0.0, collected.size)
        val reached = search(level, from, collected, fuel)
        val best = reached.filter { it.key.pos == level.portal }.entries
            .sortedWith(compareBy({ -Integer.bitCount(it.key.mask) }, { it.value.fuel }, { it.value.moves }))
            .firstOrNull() ?: return null
        val thrusts = ArrayList<Vec>()
        var state: State? = best.key
        while (state != null) {
            val node = reached[state]!!
            node.thrust?.let { thrusts += it }
            state = node.previous
        }
        thrusts.reverse()
        return Plan(thrusts, best.value.fuel, Integer.bitCount(best.key.mask))
    }

    private data class State(val pos: Vec, val mask: Int)

    private class Node(val fuel: Double, val moves: Int, val previous: State?, val thrust: Vec?)

    /** Dijkstra from [from]: the cheapest way to every reachable state, within [fuel]. */
    private fun search(level: Level, from: Vec, collected: Set<Vec>, fuel: Double): Map<State, Node> {
        fun maskOf(cs: Collection<Vec>) = cs.fold(0) { m, c -> m or (1 shl level.crystals.indexOf(c)) }
        val start = State(from, maskOf(collected.filter { it in level.crystals }))
        val best = HashMap<State, Node>()
        best[start] = Node(0.0, 0, null, null)
        val queue = PriorityQueue<Pair<State, Node>>(compareBy({ it.second.fuel }, { it.second.moves }))
        queue += start to best[start]!!
        val thrusts = Vec.upTo(fuel)
        while (queue.isNotEmpty()) {
            val (state, node) = queue.poll()
            if (best[state] !== node) continue
            // Reaching the portal ends the flight.
            if (state.pos == level.portal) continue
            for (t in thrusts) {
                val used = node.fuel + t.length
                if (used > fuel + Vec.EPS) break
                val to = state.pos + t + level.wind
                if (!Space.inside(level, to) || Space.asteroidOnWay(level, state.pos, to) != null) continue
                val mask = state.mask or maskOf(Space.crystalsOnWay(level, state.pos, to))
                val next = State(to, mask)
                val old = best[next]
                if (old == null || used < old.fuel - Vec.EPS || (used < old.fuel + Vec.EPS && node.moves + 1 < old.moves)) {
                    val n = Node(used, node.moves + 1, state, t)
                    best[next] = n
                    queue += next to n
                }
            }
        }
        return best
    }
}
