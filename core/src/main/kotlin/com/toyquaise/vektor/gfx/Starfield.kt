package com.toyquaise.vektor.gfx

import com.badlogic.gdx.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deep space behind everything, in screen pixels: a dark gradient, slow coloured nebulae and
 * three layers of twinkling stars drifting down, so the ship always seems to be on its way.
 */
class Starfield(seed: Int = 3) {
    private class Star(val x: Float, var y: Float, val size: Float, val layer: Int, val phase: Float, val tint: Color)
    private class Cloud(val x: Float, val y: Float, val radius: Float, val color: Color, val phase: Float)

    private val rnd = Random(seed)
    private val stars = List(160) {
        val layer = rnd.nextInt(3)
        val tint = when (rnd.nextInt(6)) {
            0 -> Palette.cyan
            1 -> Palette.magenta
            2 -> Palette.gold
            else -> Palette.white
        }
        Star(rnd.nextFloat(), rnd.nextFloat(), 0.6f + layer * 0.55f + rnd.nextFloat() * 0.5f, layer, rnd.nextFloat() * 6.28f, tint)
    }
    private val clouds = listOf(
        Cloud(0.15f, 0.78f, 0.55f, Palette.magenta, 0f),
        Cloud(0.9f, 0.55f, 0.6f, Palette.blue, 1.7f),
        Cloud(0.3f, 0.2f, 0.65f, Palette.purple, 3.1f),
        Cloud(0.8f, 0.1f, 0.4f, Palette.cyan, 4.2f),
    )
    private var time = 0f

    fun update(dt: Float) {
        time += dt
        for (s in stars) {
            s.y -= dt * (0.004f + s.layer * 0.006f)
            if (s.y < 0f) s.y += 1f
        }
    }

    /** Draws over the whole [w] × [h] pixel screen; [g] must be set up in pixels. */
    fun draw(g: Gfx, w: Float, h: Float) {
        g.gradient(0f, h * 0.45f, w, h * 0.55f, Palette.spaceMid, Palette.spaceTop)
        g.gradient(0f, 0f, w, h * 0.45f + 1f, Palette.spaceBottom, Palette.spaceMid)
        val unit = maxOf(w, h)
        for (c in clouds) {
            val pulse = 0.85f + 0.15f * sin(time * 0.3f + c.phase)
            val x = c.x * w + sin(time * 0.05f + c.phase) * unit * 0.02f
            g.glow(x, c.y * h, c.radius * unit * pulse, c.color, 0.16f)
        }
        val px = unit / 900f
        for (s in stars) {
            val tw = 0.55f + 0.45f * sin(time * (1.2f + s.layer) + s.phase)
            val r = s.size * px
            g.dot(s.x * w, s.y * h, r, s.tint, 0.5f + 0.5f * tw, add = true)
            if (s.layer == 2) g.glow(s.x * w, s.y * h, r * 6f, s.tint, 0.25f * tw)
        }
    }
}
