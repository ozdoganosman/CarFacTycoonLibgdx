package com.toyquaise.vektor.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The ship, asteroids, crystals and the portal, drawn from shapes in world units (one square = 1). */
object Sprites {
    private val tmp = Color()

    private fun rotated(points: FloatArray, x: Float, y: Float, angle: Float, scale: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        val out = FloatArray(points.size)
        var i = 0
        while (i < points.size) {
            val px = points[i] * scale
            val py = points[i + 1] * scale
            out[i] = x + px * c - py * s
            out[i + 1] = y + px * s + py * c
            i += 2
        }
        return out
    }

    // The ship, pointing along +x, about one third of a square long.
    private val hull = floatArrayOf(0.36f, 0f, 0.1f, 0.13f, -0.2f, 0.16f, -0.13f, 0f, -0.2f, -0.16f, 0.1f, -0.13f)
    private val wingL = floatArrayOf(0.02f, 0.11f, -0.24f, 0.3f, -0.22f, 0.12f)
    private val wingR = floatArrayOf(0.02f, -0.11f, -0.22f, -0.12f, -0.24f, -0.3f)
    private val canopy = floatArrayOf(0.22f, 0f, 0.08f, 0.055f, 0.0f, 0f, 0.08f, -0.055f)

    /**
     * The ship at (x, y) heading [angle] (radians); [thrust] 0..1 lights the engine. [scale] 1 is
     * the size on the map.
     */
    fun ship(g: Gfx, x: Float, y: Float, angle: Float, thrust: Float, time: Float, size: Float = 1f, alpha: Float = 1f) {
        val scale = size * 1.25f
        val bx = x - cos(angle) * 0.2f * scale
        val by = y - sin(angle) * 0.2f * scale
        g.glow(x, y, 0.75f * scale, Palette.cyan, 0.28f * alpha)
        if (thrust > 0f) {
            val flick = 0.85f + 0.15f * sin(time * 47f)
            val fx = bx - cos(angle) * 0.12f * scale * flick * thrust
            val fy = by - sin(angle) * 0.12f * scale * flick * thrust
            g.glow(fx, fy, 0.38f * scale * thrust * flick, Palette.orange, 0.95f * alpha)
            g.glow(fx, fy, 0.18f * scale * thrust * flick, Palette.gold, alpha)
        }
        g.polygon(rotated(wingL, x, y, angle, scale), tmp.set(Palette.magenta).mul(1f, 1f, 1f, alpha))
        g.polygon(rotated(wingR, x, y, angle, scale), tmp.set(Palette.magenta).mul(0.75f, 0.75f, 0.75f, alpha))
        val pts = rotated(hull, x, y, angle, scale)
        val light = Color(Palette.white).mul(1f, 1f, 1f, alpha)
        val mid = Color(0.72f, 0.82f, 1f, alpha)
        val dark = Color(0.42f, 0.5f, 0.78f, alpha)
        // Lit from the upper left: vertices facing up are lighter.
        g.polygon(pts, light) { i -> if (i == 0 || i == 1 || i == 2) light else if (i == 3) mid else dark }
        g.polygon(rotated(canopy, x, y, angle, scale), tmp.set(Palette.cyan).mul(1f, 1f, 1f, alpha))
        g.glow(x + cos(angle) * 0.12f * scale, y + sin(angle) * 0.12f * scale, 0.12f * scale, Palette.cyan, 0.8f * alpha)
    }

    /** A lumpy asteroid of radius about 0.36 at a grid point; [seed] gives each its own shape. */
    fun asteroid(g: Gfx, x: Float, y: Float, seed: Int, time: Float, danger: Float = 0f) {
        val rnd = Random(seed * 7919 + 17)
        val n = 11
        val spin = time * (0.15f + rnd.nextFloat() * 0.2f) * (if (rnd.nextBoolean()) 1 else -1)
        val radii = FloatArray(n) { 0.3f + rnd.nextFloat() * 0.1f }
        fun outline(scale: Float, dx: Float, dy: Float): FloatArray = FloatArray(n * 2) { i ->
            val k = i / 2
            val a = spin + k * MathUtils.PI2 / n
            if (i % 2 == 0) x + dx + cos(a) * radii[k] * scale else y + dy + sin(a) * radii[k] * scale
        }
        if (danger > 0f) g.glow(x, y, 0.9f, Palette.red, 0.7f * danger)
        g.glow(x, y, 0.62f, Palette.purple, 0.18f)
        g.polygon(outline(1.06f, 0f, 0f), Palette.rockLight)
        g.polygon(outline(1f, 0.02f, -0.025f), Palette.rock) { i ->
            // Darker towards the lower right, away from the light.
            val a = spin + i * MathUtils.PI2 / n
            val k = (cos(a) * 0.6f - sin(a) * 0.8f + 1f) / 2f
            tmp.set(Palette.rock).lerp(Palette.rockDark, k)
            Color(tmp)
        }
        // Craters.
        repeat(3) {
            val a = rnd.nextFloat() * MathUtils.PI2 + spin
            val d = rnd.nextFloat() * 0.16f
            val r = 0.04f + rnd.nextFloat() * 0.05f
            g.dot(x + cos(a) * d, y + sin(a) * d, r, Palette.rockDark, 0.9f)
            g.dot(x + cos(a) * d - r * 0.25f, y + sin(a) * d + r * 0.25f, r * 0.55f, Palette.rockLight, 0.35f)
        }
        if (danger > 0f) {
            g.polygon(outline(1.08f, 0f, 0f).let { it }, tmp.set(Palette.red).mul(1f, 1f, 1f, 0.35f * danger))
        }
    }

    /** A spinning, bobbing crystal at a grid point. */
    fun crystal(g: Gfx, x: Float, y: Float, time: Float, phase: Float, alpha: Float = 1f) {
        val bob = sin(time * 2.2f + phase) * 0.04f
        val cy = y + bob
        val w = 0.17f * (0.75f + 0.25f * cos(time * 1.6f + phase))
        val h = 0.24f
        g.glow(x, cy, 0.7f, Palette.gold, 0.45f * alpha)
        val left = floatArrayOf(x, cy + h, x - w, cy, x, cy - h)
        val right = floatArrayOf(x, cy + h, x, cy - h, x + w, cy)
        g.polygon(left, tmp.set(1f, 0.93f, 0.55f, alpha))
        g.polygon(right, tmp.set(0.98f, 0.68f, 0.12f, alpha))
        g.polygon(floatArrayOf(x, cy + h, x - w * 0.5f, cy + h * 0.25f, x, cy + h * 0.05f), tmp.set(1f, 1f, 1f, 0.75f * alpha))
        val sparkle = (sin(time * 3.1f + phase * 2f) + 1f) / 2f
        g.glow(x - w * 0.4f, cy + h * 0.45f, 0.14f * sparkle, Palette.white, alpha)
    }

    /** The portal: two rings turning against each other, light swirling into the middle. */
    fun portal(g: Gfx, x: Float, y: Float, time: Float, open: Float = 1f) {
        val pulse = 1f + 0.06f * sin(time * 3f)
        g.glow(x, y, 1.1f * pulse, Palette.magenta, 0.35f * open)
        g.glow(x, y, 0.5f, Palette.purple, 0.55f * open)
        g.ring(x, y, 0.42f * pulse, Palette.magenta, 0.95f, time * 60f)
        g.ring(x, y, 0.3f / pulse, Palette.cyan, 0.85f, -time * 90f)
        for (k in 0 until 6) {
            val a = time * 2.2f + k * MathUtils.PI2 / 6
            val r = 0.38f * ((time * 0.7f + k / 6f) % 1f)
            g.dot(x + cos(a) * r, y + sin(a) * r, 0.03f, Palette.white, 0.9f, add = true)
        }
        g.glow(x, y, 0.22f, Palette.white, 0.6f)
    }
}
