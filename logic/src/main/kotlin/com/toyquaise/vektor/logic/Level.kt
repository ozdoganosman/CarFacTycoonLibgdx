package com.toyquaise.vektor.logic

import kotlin.math.sqrt

/**
 * The parts of the game, each tied to learning outcomes of the 9th grade physics unit "Kuvvet ve
 * Hareket" (Türkiye Yüzyılı Maarif Modeli, FİZ.9.2).
 */
enum class Chapter(val outcomes: String) {
    /** Fuel is a scalar, a move is a vector; magnitude by Pythagoras; vectors add tip to tail. */
    LAUNCH("FİZ.9.2.2 · FİZ.9.2.4"),

    /** Asteroids in the way: the road taken is longer than the displacement. */
    BELT("FİZ.9.2.4 · FİZ.9.2.6"),

    /** A solar wind added to every move for free: the resultant of two vectors. */
    WIND("FİZ.9.2.4"),
}

/**
 * One stretch of space. Positions are grid points from (0, 0) to ([width], [height]). Asteroids
 * stand on grid points; crystals too, and they are picked up by flying right over them.
 */
class Level(
    val number: Int,
    val chapter: Chapter,
    val width: Int,
    val height: Int,
    val start: Vec,
    val portal: Vec,
    val asteroids: Set<Vec>,
    val crystals: List<Vec>,
    /** Added to every move without burning fuel. */
    val wind: Vec = Vec.ZERO,
    /** The scalar the player spends: each move burns its own magnitude. */
    val fuel: Double = FUEL,
) {
    init {
        val points = listOf(start, portal) + crystals
        require(points.all { Space.inside(this, it) }) { "level $number: a point off the map" }
        require(points.none { it in asteroids }) { "level $number: a point on an asteroid" }
        require(start != portal) { "level $number: starts in the portal" }
        require(crystals.distinct().size == crystals.size) { "level $number: two crystals in one place" }
    }

    companion object {
        /** Every level gives the same five units of fuel. */
        const val FUEL = 5.0

        /**
         * Reads a map drawn as text, top row first, one character per grid point: '.' empty,
         * '#' asteroid, 'S' the ship's start, 'P' the portal, '*' a crystal.
         */
        fun parse(number: Int, chapter: Chapter, map: String, wind: Vec = Vec.ZERO): Level {
            val rows = map.trimIndent().lines().map { it.trim() }.filter { it.isNotEmpty() }.reversed()
            val width = rows.first().length - 1
            require(rows.all { it.length == width + 1 }) { "level $number: ragged map" }
            var start: Vec? = null
            var portal: Vec? = null
            val asteroids = HashSet<Vec>()
            val crystals = ArrayList<Vec>()
            for ((y, row) in rows.withIndex()) for ((x, c) in row.withIndex()) {
                when (c) {
                    '.' -> {}
                    '#' -> asteroids += Vec(x, y)
                    'S' -> start = Vec(x, y)
                    'P' -> portal = Vec(x, y)
                    '*' -> crystals += Vec(x, y)
                    else -> error("level $number: unknown map character '$c'")
                }
            }
            return Level(
                number, chapter, width, rows.size - 1,
                requireNotNull(start) { "level $number: no S" }, requireNotNull(portal) { "level $number: no P" },
                asteroids, crystals, wind,
            )
        }
    }
}

/** Geometry of space: its edges, asteroids in the way and crystals on the way. */
object Space {
    /**
     * A move passing closer than this (in squares) to an asteroid hits it. Half a square: no move
     * slips between two asteroids side by side, but one can pass between two corner to corner.
     */
    const val ASTEROID_REACH = 0.5

    /** A move picks up a crystal only by passing right over its grid point. */
    const val CRYSTAL_REACH = 0.15

    fun inside(level: Level, p: Vec): Boolean = p.x in 0..level.width && p.y in 0..level.height

    /** The first asteroid (nearest [from]) on the straight way from [from] to [to], or null. */
    fun asteroidOnWay(level: Level, from: Vec, to: Vec): Vec? =
        level.asteroids.filter { distanceToSegment(it, from, to) < ASTEROID_REACH }.minByOrNull { (it - from).lengthSquared }

    /** The crystals the straight way from [from] to [to] passes over, in the order it meets them. */
    fun crystalsOnWay(level: Level, from: Vec, to: Vec): List<Vec> =
        level.crystals.filter { distanceToSegment(it, from, to) < CRYSTAL_REACH }.sortedBy { (it - from).lengthSquared }

    /** Distance from point [p] to the segment [a]–[b]. */
    fun distanceToSegment(p: Vec, a: Vec, b: Vec): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = a.x + dx * t - p.x
        val cy = a.y + dy * t - p.y
        return sqrt(cx * cx + cy * cy)
    }
}
