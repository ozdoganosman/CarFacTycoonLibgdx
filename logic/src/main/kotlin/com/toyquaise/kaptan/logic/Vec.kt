package com.toyquaise.kaptan.logic

import kotlin.math.sqrt

/**
 * A vector on the squared sea, in whole squares: x to the east (right), y to the north (up), as
 * on the squared plane of the lesson. Positions are grid points written the same way.
 */
data class Vec(val x: Int, val y: Int) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Int) = Vec(x * k, y * k)
    operator fun unaryMinus() = Vec(-x, -y)

    /** Magnitude in squares (Pythagoras on the grid). */
    val length: Double get() = sqrt((x * x + y * y).toDouble())

    val isZero: Boolean get() = x == 0 && y == 0

    /** Along one axis: one component is zero, so it has nothing to be split into. */
    val isAxial: Boolean get() = x == 0 || y == 0

    /** The two perpendicular components, x first (FİZ.9.2.4: splitting into components). */
    val components: Pair<Vec, Vec> get() = Vec(x, 0) to Vec(0, y)

    override fun toString() = "($x, $y)"

    companion object {
        val ZERO = Vec(0, 0)
    }
}

/** How much a card is stretched before it is played. */
enum class Stretch { NONE, DOUBLE, HALF }

/**
 * The real number a card is multiplied by before it is played (FİZ.9.2.3): 1, 2 or ½, and turned
 * around (× −1) or not. Each part comes from a token the level hands out.
 */
data class Factor(val flip: Boolean = false, val stretch: Stretch = Stretch.NONE) {
    val isOne: Boolean get() = !flip && stretch == Stretch.NONE

    /** The multiplied vector, or null when halving would leave half squares (odd components). */
    fun applyTo(v: Vec): Vec? {
        val s = when (stretch) {
            Stretch.NONE -> v
            Stretch.DOUBLE -> v * 2
            Stretch.HALF -> if (v.x % 2 != 0 || v.y % 2 != 0) return null else Vec(v.x / 2, v.y / 2)
        }
        return if (flip) -s else s
    }

    /** The number itself: ±1, ±2 or ±0.5. */
    val value: Double
        get() = (if (flip) -1.0 else 1.0) * when (stretch) {
            Stretch.NONE -> 1.0
            Stretch.DOUBLE -> 2.0
            Stretch.HALF -> 0.5
        }

    companion object {
        val ONE = Factor()
    }
}

/**
 * What a level hands out besides the cards: multiplier tokens (× 2, × ½, × −1), splits into
 * components and pairs of tugboats that pull together (the parallelogram).
 */
data class Tokens(
    val doubles: Int = 0,
    val halves: Int = 0,
    val flips: Int = 0,
    val splits: Int = 0,
    val pairs: Int = 0,
) {
    /** What is left after playing cards with [factors] (two of them pulled together if [pair]), or null if it is not enough. */
    fun spend(factors: List<Factor>, pair: Boolean): Tokens? {
        val left = copy(
            doubles = doubles - factors.count { it.stretch == Stretch.DOUBLE },
            halves = halves - factors.count { it.stretch == Stretch.HALF },
            flips = flips - factors.count { it.flip },
            pairs = pairs - if (pair) 1 else 0,
        )
        return left.takeIf { it.doubles >= 0 && it.halves >= 0 && it.flips >= 0 && it.pairs >= 0 }
    }

    /** Every factor these tokens allow on a single card. */
    fun factors(): List<Factor> = buildList {
        for (flip in if (flips > 0) listOf(false, true) else listOf(false)) {
            add(Factor(flip, Stretch.NONE))
            if (doubles > 0) add(Factor(flip, Stretch.DOUBLE))
            if (halves > 0) add(Factor(flip, Stretch.HALF))
        }
    }

    val any: Boolean get() = doubles + halves + flips + splits + pairs > 0
}
