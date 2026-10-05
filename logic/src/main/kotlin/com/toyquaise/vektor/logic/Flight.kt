package com.toyquaise.vektor.logic

/**
 * One move: the ship fired its engine along [thrust] (burning its magnitude in fuel) and the wind
 * added [wind]; it went from [from] to [to] and picked up [crystals] on the way.
 */
class Piece(val from: Vec, val to: Vec, val thrust: Vec, val wind: Vec, val crystals: List<Vec>) {
    val delta: Vec get() = to - from

    /** Fuel burnt: the magnitude of the thrust, a scalar. */
    val cost: Double get() = thrust.length

    /** Distance flown: the magnitude of the step, wind included. */
    val length: Double get() = delta.length
}

/** Why a move cannot be made. */
enum class Problem { ARRIVED, ZERO, NO_FUEL, OFF_MAP, ASTEROID }

/** What a move would do, worked out while the player aims, for the preview. */
class Aim(
    val problem: Problem?,
    val from: Vec,
    val thrust: Vec,
    val wind: Vec,
    val cost: Double,
    val asteroid: Vec? = null,
    val crystals: List<Vec> = emptyList(),
) {
    val delta: Vec get() = thrust + wind
    val to: Vec get() = from + delta
    val ok: Boolean get() = problem == null
}

/**
 * A level being played: where the ship is, the fuel left, the moves made (each can be taken back),
 * the crystals picked up, and whether it has reached the portal.
 */
class Flight(val level: Level) {
    var position: Vec = level.start
        private set
    private val moves = ArrayList<Piece>()
    val pieces: List<Piece> get() = moves

    /** Bumped on every change, so views know when to look again. */
    var revision = 0
        private set

    val arrived: Boolean get() = position == level.portal

    /** Fuel burnt so far: the sum of the thrusts' magnitudes. */
    val fuelUsed: Double get() = moves.sumOf { it.cost }

    val fuelLeft: Double get() = (level.fuel - fuelUsed).coerceAtLeast(0.0)

    /** Distance flown, the road taken ("alınan yol"): the sum of the steps' magnitudes. */
    val travelled: Double get() = moves.sumOf { it.length }

    /** From the start to the ship ("yer değiştirme"): the resultant of every move. */
    val displacement: Vec get() = position - level.start

    val collected: Set<Vec> get() = moves.flatMap { it.crystals }.toSet()

    /** No whole move is left: the shortest one costs 1 and less than that is in the tank. */
    val stranded: Boolean get() = !arrived && fuelLeft < 1.0 - Vec.EPS

    val canUndo: Boolean get() = moves.isNotEmpty()

    fun check(thrust: Vec): Aim {
        val cost = thrust.length
        val wind = level.wind
        if (arrived) return Aim(Problem.ARRIVED, position, thrust, wind, cost)
        if (thrust.isZero) return Aim(Problem.ZERO, position, thrust, wind, cost)
        if (cost > fuelLeft + Vec.EPS) return Aim(Problem.NO_FUEL, position, thrust, wind, cost)
        val to = position + thrust + wind
        if (!Space.inside(level, to)) return Aim(Problem.OFF_MAP, position, thrust, wind, cost)
        val rock = Space.asteroidOnWay(level, position, to)
        if (rock != null) return Aim(Problem.ASTEROID, position, thrust, wind, cost, rock)
        val gems = Space.crystalsOnWay(level, position, to).filter { it !in collected }
        return Aim(null, position, thrust, wind, cost, crystals = gems)
    }

    /** Makes the move, or says why it cannot be made. */
    fun fly(thrust: Vec): Problem? {
        val aim = check(thrust)
        if (!aim.ok) return aim.problem
        moves += Piece(position, aim.to, thrust, aim.wind, aim.crystals)
        position = aim.to
        revision++
        return null
    }

    /** Takes back the last move. */
    fun undo(): Boolean {
        val last = moves.removeLastOrNull() ?: return false
        position = last.from
        revision++
        return true
    }

    fun reset() {
        moves.clear()
        position = level.start
        revision++
    }

    /** Stars: one for reaching the portal and one for every crystal brought along. */
    val stars: Int get() = if (arrived) 1 + collected.size.coerceAtMost(2) else 0
}
