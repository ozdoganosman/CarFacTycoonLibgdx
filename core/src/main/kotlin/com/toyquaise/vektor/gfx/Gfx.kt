package com.toyquaise.vektor.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.EarClippingTriangulator
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Drawing in neon: soft glows, glowing lines and arrows, rings, filled polygons. Everything is
 * drawn in the units of the current projection; glows add light (additive blending), solid
 * shapes cover. Call [begin] and [end] around a frame; the helpers switch between the sprite
 * batch and the shape renderer as needed.
 */
class Gfx : Disposable {
    val batch = SpriteBatch()
    val shapes = ShapeRenderer()
    private val textures = ArrayList<Texture>()

    /** A round glow: bright in the middle, fading to nothing at the edge. */
    val glow: TextureRegion = region(128) { x, y ->
        val r = (sqrt(x * x + y * y)).coerceAtMost(1f)
        val a = exp(-r * r * 4.5f) * (1f - r)
        a
    }

    /** A crisp round dot, anti-aliased at its edge. */
    val dot: TextureRegion = region(64) { x, y ->
        val r = sqrt(x * x + y * y)
        ((1f - r) * 32f).coerceIn(0f, 1f)
    }

    /** A thin ring of radius 0.8 of the texture, soft at both edges. */
    val ring: TextureRegion = region(256) { x, y ->
        val r = sqrt(x * x + y * y)
        val d = kotlin.math.abs(r - 0.8f)
        exp(-d * d * 900f) + 0.35f * exp(-d * d * 60f)
    }

    /** Across a glowing line: a bright core and a soft halo (the texture's height is the line's width). */
    val line: TextureRegion = run {
        val pm = Pixmap(4, 64, Pixmap.Format.RGBA8888)
        for (y in 0 until 64) {
            val t = (y + 0.5f) / 64f * 2f - 1f
            val core = exp(-t * t * 22f)
            val halo = exp(-t * t * 4f) * 0.4f
            val a = (core + halo).coerceAtMost(1f)
            for (x in 0 until 4) pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a))
        }
        TextureRegion(texture(pm))
    }

    val white: TextureRegion = region(4) { _, _ -> 1f }

    private fun region(size: Int, alpha: (Float, Float) -> Float): TextureRegion {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) for (x in 0 until size) {
            val fx = (x + 0.5f) / size * 2f - 1f
            val fy = (y + 0.5f) / size * 2f - 1f
            pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha(fx, fy).coerceIn(0f, 1f)))
        }
        return TextureRegion(texture(pm))
    }

    private fun texture(pm: Pixmap): Texture {
        val t = Texture(pm, true)
        pm.dispose()
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        textures += t
        return t
    }

    // ------------------------------------------------------------------ frame

    private enum class Mode { NONE, SPRITES, SHAPES }
    private var mode = Mode.NONE
    private var additive = false
    private val projection = Matrix4()

    fun begin(projection: Matrix4) {
        this.projection.set(projection)
        batch.projectionMatrix = projection
        shapes.projectionMatrix = projection
        mode = Mode.NONE
    }

    fun end() {
        when (mode) {
            Mode.SPRITES -> batch.end()
            Mode.SHAPES -> shapes.end()
            Mode.NONE -> {}
        }
        mode = Mode.NONE
        // The batch is shared with the UI stage: leave it covering, in white.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.color = Color.WHITE
        additive = false
    }

    /** Switches to the sprite batch, adding light ([add]) or covering. */
    fun sprites(add: Boolean): SpriteBatch {
        if (mode == Mode.SHAPES) { shapes.end(); mode = Mode.NONE }
        if (mode == Mode.NONE) {
            batch.begin()
            mode = Mode.SPRITES
            additive = !add
        }
        if (additive != add) {
            if (add) batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            else batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            additive = add
        }
        return batch
    }

    fun shapes(): ShapeRenderer {
        if (mode == Mode.SPRITES) { batch.end(); mode = Mode.NONE }
        if (mode == Mode.NONE) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            mode = Mode.SHAPES
        }
        return shapes
    }

    // ------------------------------------------------------------------ light

    /** A soft glow of [radius] around (x, y). */
    fun glow(x: Float, y: Float, radius: Float, color: Color, alpha: Float = 1f) {
        val b = sprites(add = true)
        b.setColor(color.r, color.g, color.b, color.a * alpha)
        b.draw(glow, x - radius, y - radius, radius * 2, radius * 2)
    }

    fun dot(x: Float, y: Float, radius: Float, color: Color, alpha: Float = 1f, add: Boolean = false) {
        val b = sprites(add)
        b.setColor(color.r, color.g, color.b, color.a * alpha)
        b.draw(dot, x - radius, y - radius, radius * 2, radius * 2)
    }

    fun ring(x: Float, y: Float, radius: Float, color: Color, alpha: Float = 1f, rotation: Float = 0f) {
        val b = sprites(add = true)
        b.setColor(color.r, color.g, color.b, color.a * alpha)
        // The ring sits at 0.8 of the texture's half-size.
        val half = radius / 0.8f
        b.draw(ring, x - half, y - half, half, half, half * 2, half * 2, 1f, 1f, rotation)
    }

    /** A glowing line [width] wide (halo included) from (x1, y1) to (x2, y2). */
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color, alpha: Float = 1f) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) return
        val b = sprites(add = true)
        b.setColor(color.r, color.g, color.b, color.a * alpha)
        val angle = MathUtils.radiansToDegrees * atan2(dy, dx)
        // Overhang by half the width at both ends, so joined lines meet softly.
        val over = width * 0.25f
        b.draw(line, x1 - over, y1 - width / 2, over, width / 2, len + over * 2, width, 1f, 1f, angle)
    }

    /** A dashed glowing line. */
    fun dashed(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color, dash: Float, gap: Float, alpha: Float = 1f, phase: Float = 0f) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) return
        val ux = dx / len
        val uy = dy / len
        var d = -((phase % (dash + gap)) + dash + gap) % (dash + gap)
        while (d < len) {
            val a = d.coerceAtLeast(0f)
            val e = (d + dash).coerceAtMost(len)
            if (e > a) line(x1 + ux * a, y1 + uy * a, x1 + ux * e, y1 + uy * e, width, color, alpha)
            d += dash + gap
        }
    }

    /** A glowing arrow from (x1, y1) to its tip at (x2, y2). */
    fun arrow(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Color, alpha: Float = 1f, head: Float = width * 2.2f) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-4f) return
        val ux = dx / len
        val uy = dy / len
        val h = minOf(head, len * 0.6f)
        val bx = x2 - ux * h
        val by = y2 - uy * h
        line(x1, y1, bx + ux * h * 0.3f, by + uy * h * 0.3f, width, color, alpha)
        // The head: a filled triangle with a glow behind it.
        val nx = -uy * h * 0.55f
        val ny = ux * h * 0.55f
        glow(x2 - ux * h * 0.4f, y2 - uy * h * 0.4f, h * 1.1f, color, 0.6f * alpha)
        val s = shapes()
        s.setColor(color.r, color.g, color.b, color.a * alpha)
        s.triangle(x2, y2, bx + nx, by + ny, bx - nx, by - ny)
        s.setColor(1f, 1f, 1f, 0.55f * alpha)
        s.triangle(x2 - ux * h * 0.18f, y2 - uy * h * 0.18f, bx + nx * 0.45f + ux * h * 0.2f, by + ny * 0.45f + uy * h * 0.2f, bx - nx * 0.45f + ux * h * 0.2f, by - ny * 0.45f + uy * h * 0.2f)
    }

    // ------------------------------------------------------------------ solid shapes

    private val triangulator = EarClippingTriangulator()

    /** A filled polygon ([points] as x0, y0, x1, y1...), with an optional colour per vertex. */
    fun polygon(points: FloatArray, color: Color, shade: ((Int) -> Color)? = null) {
        val s = shapes()
        val tris = triangulator.computeTriangles(points)
        var i = 0
        while (i < tris.size) {
            val a = tris[i].toInt()
            val b = tris[i + 1].toInt()
            val c = tris[i + 2].toInt()
            if (shade == null) {
                s.setColor(color)
                s.triangle(points[a * 2], points[a * 2 + 1], points[b * 2], points[b * 2 + 1], points[c * 2], points[c * 2 + 1])
            } else {
                s.triangle(points[a * 2], points[a * 2 + 1], points[b * 2], points[b * 2 + 1], points[c * 2], points[c * 2 + 1], shade(a), shade(b), shade(c))
            }
            i += 3
        }
    }

    /** A filled rectangle with a vertical gradient, [bottom] to [top]. */
    fun gradient(x: Float, y: Float, w: Float, h: Float, bottom: Color, top: Color) {
        shapes().rect(x, y, w, h, bottom, bottom, top, top)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        textures.forEach { it.dispose() }
    }
}
