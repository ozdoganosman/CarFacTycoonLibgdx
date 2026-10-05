package com.toyquaise.vektor.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import kotlin.random.Random

/** Sparks of light: bursts, exhaust and sparkles, in world units, drawn as additive glows. */
class Particles {
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val size: Float, val color: Color, val drag: Float)

    private val list = ArrayList<P>()
    private val rnd = Random(5)

    fun burst(x: Float, y: Float, n: Int, colors: List<Color>, speed: Float, size: Float, life: Float = 0.8f) {
        repeat(n) { i ->
            val a = rnd.nextFloat() * MathUtils.PI2
            val s = speed * (0.35f + rnd.nextFloat() * 0.65f)
            val l = life * (0.6f + rnd.nextFloat() * 0.6f)
            list += P(x, y, MathUtils.cos(a) * s, MathUtils.sin(a) * s, l, l, size * (0.6f + rnd.nextFloat() * 0.8f), colors[i % colors.size], 2.5f)
        }
    }

    /** Exhaust: a few sparks flying out backwards along (dx, dy). */
    fun exhaust(x: Float, y: Float, dx: Float, dy: Float, color: Color, size: Float) {
        repeat(2) {
            val spread = (rnd.nextFloat() - 0.5f) * 0.6f
            val vx = dx + -dy * spread
            val vy = dy + dx * spread
            val l = 0.35f + rnd.nextFloat() * 0.25f
            list += P(x, y, vx, vy, l, l, size * (0.7f + rnd.nextFloat() * 0.6f), color, 3f)
        }
    }

    fun update(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            val k = 1f / (1f + p.drag * dt)
            p.vx *= k
            p.vy *= k
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    fun draw(g: Gfx) {
        for (p in list) {
            val t = p.life / p.max
            g.glow(p.x, p.y, p.size * (0.6f + t * 0.8f) * 2.4f, p.color, t * 0.9f)
            g.dot(p.x, p.y, p.size * 0.35f * t, Palette.white, t, add = true)
        }
    }
}
