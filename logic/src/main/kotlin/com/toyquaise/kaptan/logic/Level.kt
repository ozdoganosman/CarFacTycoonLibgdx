package com.toyquaise.kaptan.logic

/**
 * The parts of the game, each tied to learning outcomes of the 9th grade physics unit "Kuvvet ve
 * Hareket" (Türkiye Yüzyılı Maarif Modeli, FİZ.9.2).
 */
enum class Chapter(val outcomes: String) {
    /** Vectors on one line: adding, opposite vectors, multiplying by a real number. */
    CANAL("FİZ.9.2.3"),

    /** Adding vectors tip to tail on the squared plane. */
    HARBOR("FİZ.9.2.4"),

    /** A current added to every move: the triangle of tip-to-tail addition. */
    CURRENT("FİZ.9.2.4"),

    /** Splitting into components and the parallelogram method. */
    TUGBOATS("FİZ.9.2.4"),
}

/**
 * One sea. Positions are grid points from (0, 0) to ([width], [height]). Rocks stand on grid
 * points too; a move is stopped by any rock it passes closer than [Sea.ROCK_REACH] to.
 */
class Level(
    val number: Int,
    val chapter: Chapter,
    val width: Int,
    val height: Int,
    val start: Vec,
    val harbor: Vec,
    val rocks: Set<Vec>,
    val cards: List<Vec>,
    /** Added to every move: the sea carries the boat while it sails. */
    val current: Vec = Vec.ZERO,
    val tokens: Tokens = Tokens(),
) {
    init {
        require(Sea.inside(this, start) && Sea.inside(this, harbor)) { "level $number: start or harbor off the sea" }
        require(start !in rocks && harbor !in rocks) { "level $number: start or harbor on a rock" }
        require(start != harbor) { "level $number: already in the harbor" }
        require(cards.none { it.isZero }) { "level $number: a zero card" }
    }

    companion object {
        /**
         * Reads a sea drawn as text, top row first, one character per grid point: '.' water,
         * '#' rock, 'S' the boat's start, 'L' the harbor (liman).
         */
        fun parse(
            number: Int,
            chapter: Chapter,
            map: String,
            cards: List<Vec>,
            current: Vec = Vec.ZERO,
            tokens: Tokens = Tokens(),
        ): Level {
            val rows = map.trimIndent().lines().map { it.trim() }.filter { it.isNotEmpty() }.reversed()
            val width = rows.first().length - 1
            require(rows.all { it.length == width + 1 }) { "level $number: ragged map" }
            var start: Vec? = null
            var harbor: Vec? = null
            val rocks = HashSet<Vec>()
            for ((y, row) in rows.withIndex()) for ((x, c) in row.withIndex()) {
                when (c) {
                    '.' -> {}
                    '#' -> rocks += Vec(x, y)
                    'S' -> start = Vec(x, y)
                    'L' -> harbor = Vec(x, y)
                    else -> error("level $number: unknown map character '$c'")
                }
            }
            return Level(
                number, chapter, width, rows.size - 1,
                requireNotNull(start) { "level $number: no S" }, requireNotNull(harbor) { "level $number: no L" },
                rocks, cards, current, tokens,
            )
        }
    }
}

/** Geometry of the sea: its edges and the rocks in the way. */
object Sea {
    /** A move that passes a rock closer than this (in squares) runs aground. */
    const val ROCK_REACH = 0.35

    fun inside(level: Level, p: Vec): Boolean = p.x in 0..level.width && p.y in 0..level.height

    /** The first rock (nearest [from]) on the straight way from [from] to [to], or null if the way is clear. */
    fun rockOnWay(level: Level, from: Vec, to: Vec): Vec? =
        level.rocks.filter { distanceToSegment(it, from, to) < ROCK_REACH }
            .minByOrNull { (it - from).length }

    /** Distance from point [p] to the segment [a]–[b]. */
    fun distanceToSegment(p: Vec, a: Vec, b: Vec): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = a.x + dx * t - p.x
        val cy = a.y + dy * t - p.y
        return kotlin.math.sqrt(cx * cx + cy * cy)
    }
}
