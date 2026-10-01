package com.toyquaise.toothfort.logic

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A point on the kitchen counter. x grows to the right, y grows toward the player (down the
 * screen). One unit is about the width of a machine.
 */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    operator fun unaryMinus() = Vec2(-x, -y)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun length() = hypot(x, y)
    fun distanceTo(o: Vec2) = hypot(x - o.x, y - o.y)
    fun normalized(): Vec2 = length().let { if (it < 1e-12) Vec2(1.0, 0.0) else Vec2(x / it, y / it) }

    override fun toString() = "(%.2f, %.2f)".format(x, y)

    companion object {
        val ZERO = Vec2(0.0, 0.0)

        /** The unit vector [degrees] counter-clockwise from +x, as seen from above (y grows down). */
        fun ofAngle(degrees: Double): Vec2 {
            val r = Math.toRadians(degrees)
            return Vec2(cos(r), -sin(r))
        }
    }
}

object Geometry {
    /** Distance from [p] to the segment [a]-[b]. */
    fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val ab = b - a
        val len2 = ab.dot(ab)
        if (len2 < 1e-12) return p.distanceTo(a)
        val t = ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
        return p.distanceTo(a + ab * t)
    }

    /** Distance from [p] to a polyline. */
    fun distanceToPolyline(p: Vec2, points: List<Vec2>): Double {
        if (points.size == 1) return p.distanceTo(points[0])
        var best = Double.MAX_VALUE
        for (i in 1 until points.size) best = minOf(best, distanceToSegment(p, points[i - 1], points[i]))
        return best
    }

    fun length(points: List<Vec2>): Double {
        var s = 0.0
        for (i in 1 until points.size) s += points[i].distanceTo(points[i - 1])
        return s
    }

    /** A smooth curve through [control] (Catmull-Rom), sampled about every [step] units. */
    fun smooth(control: List<Vec2>, step: Double = 0.1): List<Vec2> {
        if (control.size < 3) return control
        val out = ArrayList<Vec2>()
        for (i in 0 until control.size - 1) {
            val p0 = control[maxOf(0, i - 1)]
            val p1 = control[i]
            val p2 = control[i + 1]
            val p3 = control[minOf(control.size - 1, i + 2)]
            val n = maxOf(2, (p1.distanceTo(p2) / step).toInt())
            for (s in 0 until n) {
                val t = s.toDouble() / n
                val t2 = t * t
                val t3 = t2 * t
                fun f(a: Double, b: Double, c: Double, d: Double) =
                    0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
                out += Vec2(f(p0.x, p1.x, p2.x, p3.x), f(p0.y, p1.y, p2.y, p3.y))
            }
        }
        out += control.last()
        return out
    }
}
