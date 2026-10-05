package com.toyquaise.vektor.logic

import kotlin.math.sqrt

/**
 * A vector on the squared plane, in whole squares: x to the east (right), y to the north (up).
 * Positions are grid points written the same way.
 */
data class Vec(val x: Int, val y: Int) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Int) = Vec(x * k, y * k)
    operator fun unaryMinus() = Vec(-x, -y)

    /** Magnitude in squares: Pythagoras on the grid, √(x² + y²). */
    val length: Double get() = sqrt((x * x + y * y).toDouble())

    /** The square of the magnitude, x² + y², a whole number. */
    val lengthSquared: Int get() = x * x + y * y

    val isZero: Boolean get() = x == 0 && y == 0

    override fun toString() = "($x, $y)"

    companion object {
        val ZERO = Vec(0, 0)

        /** Every nonzero whole vector no longer than [max], shortest first. */
        fun upTo(max: Double): List<Vec> {
            val r = max.toInt()
            return buildList {
                for (x in -r..r) for (y in -r..r) {
                    val v = Vec(x, y)
                    if (!v.isZero && v.length <= max + EPS) add(v)
                }
            }.sortedWith(compareBy({ it.lengthSquared }, { it.x }, { it.y }))
        }

        const val EPS = 1e-9
    }
}
