package com.toyquaise.vektor.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.vektor.gfx.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fonts, glowing glass panels and buttons, and the little icons, all made at start-up for the
 * screen's density. [scale] is screen pixels per UI unit.
 */
class Ui(private val scale: Float) : Disposable {
    private val textures = ArrayList<Texture>()
    private val fonts = ArrayList<BitmapFont>()

    val title = font("fonts/RussoOne-Regular.ttf", 58)
    val big = font("fonts/RussoOne-Regular.ttf", 36)
    val heading = font("fonts/ChakraPetch-Bold.ttf", 28)
    val button = font("fonts/ChakraPetch-Bold.ttf", 23)
    val body = font("fonts/ChakraPetch-Medium.ttf", 20)
    val small = font("fonts/ChakraPetch-Bold.ttf", 16)
    val tiny = font("fonts/ChakraPetch-Medium.ttf", 14)

    private fun font(path: String, size: Int): BitmapFont {
        val gen = FreeTypeFontGenerator(Gdx.files.internal(path))
        val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = (size * scale).roundToInt().coerceAtLeast(8)
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ½√≈²−×·→←↑↓◆▶–—’“”…"
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
            kerning = true
        }
        val f = gen.generateFont(p)
        gen.dispose()
        f.data.setScale(1f / scale)
        f.setUseIntegerPositions(false)
        fonts += f
        return f
    }

    private fun texture(pm: Pixmap): TextureRegion {
        val t = Texture(pm)
        pm.dispose()
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        textures += t
        return TextureRegion(t)
    }

    private fun px(units: Float) = (units * scale).roundToInt().coerceAtLeast(1)

    // ------------------------------------------------------------------ panels and buttons

    private val patches = HashMap<String, NinePatchDrawable>()

    /**
     * A rounded panel: [fill] darkening slightly downwards, an [edge] line and a soft outer glow
     * of the edge colour. Sizes in UI units.
     */
    fun panel(fill: Color, edge: Color, radius: Float = 18f, glow: Float = 10f, edgeWidth: Float = 2f): NinePatchDrawable =
        patches.getOrPut("${Color.rgba8888(fill)}:${Color.rgba8888(edge)}:$radius:$glow:$edgeWidth") {
            val r = px(radius)
            val gl = px(glow)
            val ew = edgeWidth * scale
            val inner = r * 2 + 4
            val size = inner + gl * 2
            val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
            pm.blending = Pixmap.Blending.None
            val half = inner / 2f
            for (y in 0 until size) for (x in 0 until size) {
                // Signed distance to the rounded rectangle's edge (negative inside).
                val qx = abs(x + 0.5f - size / 2f) - (half - r)
                val qy = abs(y + 0.5f - size / 2f) - (half - r)
                val outside = sqrt(maxOf(qx, 0f).let { it * it } + maxOf(qy, 0f).let { it * it })
                val d = outside + minOf(maxOf(qx, qy), 0f) - r
                var c = Color(0f, 0f, 0f, 0f)
                if (d < 0.5f) {
                    // Fill, lighter at the top.
                    val t = (y.toFloat() / size)
                    c = Color(fill).lerp(Color(fill).mul(0.75f, 0.75f, 0.85f, 1f), t)
                    c.a = fill.a * (0.5f - d).coerceIn(0f, 1f)
                    // The edge line.
                    val e = (1f - abs(d + ew / 2f) / (ew / 2f + 0.5f)).coerceIn(0f, 1f)
                    c.lerp(Color(edge.r, edge.g, edge.b, 1f), e * edge.a)
                    c.a = maxOf(c.a, e * edge.a)
                } else if (gl > 0) {
                    val k = exp(-(d / gl) * (d / gl) * 3f) * 0.55f * edge.a
                    c = Color(edge.r, edge.g, edge.b, k)
                }
                pm.drawPixel(x, y, Color.rgba8888(c))
            }
            val patch = NinePatch(texture(pm), gl + r + 1, gl + r + 1, gl + r + 1, gl + r + 1)
            patch.scale(1f / scale, 1f / scale)
            // The glow lies outside the widget's bounds.
            patch.padLeft = 0f; patch.padRight = 0f; patch.padTop = 0f; patch.padBottom = 0f
            NinePatchDrawable(patch).apply {
                leftWidth = r.toFloat() / scale; rightWidth = r.toFloat() / scale
                topHeight = r.toFloat() / scale; bottomHeight = r.toFloat() / scale
                // Draw the patch bigger than the widget by the glow's width.
            }.let { GlowPatch(it, gl / scale) }
        }

    /** A nine-patch that draws its glow outside the widget it decorates. */
    private class GlowPatch(source: NinePatchDrawable, private val out: Float) : NinePatchDrawable(source) {
        override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch, x: Float, y: Float, width: Float, height: Float) {
            super.draw(batch, x - out, y - out, width + out * 2, height + out * 2)
        }
    }

    fun buttonStyle(color: Color, font: BitmapFont = button, fontColor: Color = Palette.white): TextButton.TextButtonStyle =
        TextButton.TextButtonStyle().apply {
            up = panel(Color(color).mul(0.35f, 0.35f, 0.45f, 0.92f), color, 16f, 9f)
            down = panel(Color(color).mul(0.55f, 0.55f, 0.65f, 0.95f), Palette.white, 16f, 12f)
            disabled = panel(Color(0.1f, 0.12f, 0.24f, 0.8f), Color(Palette.faint).also { it.a = 0.7f }, 16f, 0f)
            this.font = font
            this.fontColor = fontColor
            disabledFontColor = Palette.faint
        }

    fun iconButton(icon: TextureRegion, color: Color): Button {
        val style = Button.ButtonStyle().apply {
            up = panel(Color(color).mul(0.3f, 0.3f, 0.42f, 0.9f), color, 22f, 9f)
            down = panel(Color(color).mul(0.55f, 0.55f, 0.65f, 0.95f), Palette.white, 22f, 12f)
            disabled = panel(Color(0.1f, 0.12f, 0.24f, 0.7f), Color(Palette.faint).also { it.a = 0.6f }, 22f, 0f)
        }
        val b = Button(style)
        b.add(com.badlogic.gdx.scenes.scene2d.ui.Image(icon)).size(30f)
        return b
    }

    fun label(text: String, font: BitmapFont = body, color: Color = Palette.white) = Label(text, Label.LabelStyle(font, color))

    fun solid(color: Color): Drawable = TextureRegionDrawable(white).tint(color)

    val white: TextureRegion = texture(Pixmap(2, 2, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() })

    // ------------------------------------------------------------------ icons

    /** Draws an icon of [size] UI units with a function of (x, y) in -1..1 giving coverage 0..1. */
    private fun icon(size: Float = 32f, color: Color = Palette.white, cover: (Float, Float) -> Float): TextureRegion {
        val n = px(size)
        val pm = Pixmap(n, n, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        val ss = 3
        for (y in 0 until n) for (x in 0 until n) {
            var a = 0f
            for (sy in 0 until ss) for (sx in 0 until ss) {
                val fx = (x + (sx + 0.5f) / ss) / n * 2f - 1f
                val fy = 1f - (y + (sy + 0.5f) / ss) / n * 2f
                a += cover(fx, fy).coerceIn(0f, 1f)
            }
            pm.drawPixel(x, y, Color.rgba8888(color.r, color.g, color.b, a / (ss * ss)))
        }
        return texture(pm)
    }

    private fun inTriangle(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        fun s(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3)
        val d1 = s(px, py, ax, ay, bx, by)
        val d2 = s(px, py, bx, by, cx, cy)
        val d3 = s(px, py, cx, cy, ax, ay)
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    private fun arc(x: Float, y: Float, r: Float, w: Float, from: Double, to: Double): Boolean {
        val d = sqrt(x * x + y * y)
        if (abs(d - r) > w) return false
        var a = kotlin.math.atan2(y.toDouble(), x.toDouble())
        if (a < 0) a += 2 * PI
        return a in from..to
    }

    /** Take back: an arrow turning back to the left. */
    val undo = icon { x, y ->
        val ring = arc(x + 0.05f, y + 0.05f, 0.5f, 0.12f, 0.0, PI * 1.05)
        val head = inTriangle(x, y, -0.85f, 0.05f, -0.2f, 0.05f, -0.52f, -0.42f)
        if (ring || head) 1f else 0f
    }

    /** Start over: a circle with an arrow head. */
    val restart = icon { x, y ->
        val ring = arc(x, y, 0.55f, 0.12f, 0.5, PI * 2)
        val head = inTriangle(x, y, 0.38f, 0.52f, 0.95f, 0.52f, 0.62f, 0.02f)
        if (ring || head) 1f else 0f
    }

    /** A hint: a light bulb. */
    val bulb = icon(color = Palette.gold) { x, y ->
        val globe = x * x + (y - 0.18f) * (y - 0.18f) < 0.36f
        val neck = abs(x) < 0.26f && y in -0.62f..-0.2f
        val gap = y in -0.48f..-0.42f && abs(x) < 0.26f
        if ((globe || neck) && !gap) 1f else 0f
    }

    /** The level list: four squares. */
    val grid = icon { x, y ->
        val fx = abs(x)
        val fy = abs(y)
        if (fx in 0.12f..0.75f && fy in 0.12f..0.75f) 1f else 0f
    }

    /** A video ad: a play triangle in a circle. */
    val play = icon(18f, Palette.gold) { x, y ->
        val disc = x * x + y * y < 0.9f
        val tri = inTriangle(x, y, -0.3f, 0.45f, -0.3f, -0.45f, 0.5f, 0f)
        if (disc && !tri) 1f else 0f
    }

    val lock = icon(color = Palette.dim) { x, y ->
        val body = abs(x) < 0.5f && y in -0.7f..0.05f
        val shackle = arc(x, y - 0.05f, 0.32f, 0.09f, 0.0, PI)
        if (body || shackle) 1f else 0f
    }

    /** A crystal: a diamond, light on the left. */
    val crystal = icon(28f, Palette.gold) { x, y -> if (abs(x) / 0.6f + abs(y) / 0.9f < 1f) 1f else 0f }

    /** A five-pointed star. */
    val star = run {
        val pts = FloatArray(20) { i ->
            val k = i / 2
            val r = if (k % 2 == 0) 0.95f else 0.42f
            val a = PI / 2 + k * PI / 5
            (if (i % 2 == 0) r * cos(a) else r * sin(a) - 0.06).toFloat()
        }
        icon(64f) { x, y -> if (inPolygon(x, y, pts)) 1f else 0f }
    }

    private fun inPolygon(x: Float, y: Float, p: FloatArray): Boolean {
        var inside = false
        var j = p.size / 2 - 1
        for (i in 0 until p.size / 2) {
            val xi = p[i * 2]; val yi = p[i * 2 + 1]; val xj = p[j * 2]; val yj = p[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    override fun dispose() {
        fonts.forEach { it.dispose() }
        textures.forEach { it.dispose() }
    }
}
