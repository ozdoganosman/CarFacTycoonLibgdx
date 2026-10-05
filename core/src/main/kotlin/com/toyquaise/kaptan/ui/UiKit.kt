package com.toyquaise.kaptan.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.kaptan.Palette
import com.toyquaise.kaptan.logic.Vec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fonts and "dough slab" backgrounds in the look of toyquaise.com: DynaPuff for headings and
 * buttons, Lexend for reading, rounded slabs with a darker lip underneath.
 * [scale] is screen pixels per UI unit; fonts are rasterised at that size so they stay sharp.
 */
class UiKit(private val scale: Float) : Disposable {
    private val textures = ArrayList<Texture>()
    private val fonts = ArrayList<BitmapFont>()

    val title = font("fonts/DynaPuff-SemiBold.ttf", 40)
    val heading = font("fonts/DynaPuff-SemiBold.ttf", 28)
    val button = font("fonts/DynaPuff-SemiBold.ttf", 22)
    val tool = font("fonts/DynaPuff-SemiBold.ttf", 18)
    val body = font("fonts/Lexend-Regular.ttf", 19)
    val bodyStrong = font("fonts/Lexend-SemiBold.ttf", 19)
    val small = font("fonts/Lexend-SemiBold.ttf", 15)

    val white: TextureRegion = TextureRegion(texture(Pixmap(2, 2, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }))

    private fun font(path: String, size: Int): BitmapFont {
        val gen = FreeTypeFontGenerator(Gdx.files.internal(path))
        val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = (size * scale).roundToInt().coerceAtLeast(8)
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ½→−×·–—’“”…"
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

    private fun texture(pm: Pixmap): Texture {
        val t = Texture(pm)
        pm.dispose()
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        textures += t
        return t
    }

    private val slabs = HashMap<Triple<Int, Int, Int>, NinePatchDrawable>()

    /** A rounded slab of [color] with a darker lip below, like the site's buttons. */
    fun slab(color: Color, radius: Int = 18, lip: Int = 5): NinePatchDrawable = slabs.getOrPut(Triple(Color.rgba8888(color), radius, lip)) {
        val r = (radius * scale).roundToInt().coerceAtLeast(4)
        val l = (lip * scale).roundToInt()
        val size = r * 2 + 4
        val pm = Pixmap(size, size + l, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver
        fun rounded(y0: Int, c: Color) {
            pm.setColor(c)
            pm.fillCircle(r, y0 + r, r)
            pm.fillCircle(size - r - 1, y0 + r, r)
            pm.fillCircle(r, y0 + size - r - 1, r)
            pm.fillCircle(size - r - 1, y0 + size - r - 1, r)
            pm.fillRectangle(r, y0, size - 2 * r, size)
            pm.fillRectangle(0, y0 + r, size, size - 2 * r)
        }
        rounded(l, color.cpy().mul(0.78f, 0.78f, 0.78f, 1f))
        rounded(0, color)
        // A soft highlight along the top.
        pm.setColor(1f, 1f, 1f, 0.22f)
        pm.fillRectangle(r, (r * 0.35f).roundToInt(), size - 2 * r, (r * 0.25f).roundToInt().coerceAtLeast(1))
        val patch = NinePatch(TextureRegion(texture(pm)), r + 1, r + 1, r + 1, r + 1 + l)
        patch.scale(1f / scale, 1f / scale)
        NinePatchDrawable(patch)
    }

    fun solid(color: Color): Drawable = TextureRegionDrawable(white).tint(color)

    fun label(text: String, font: BitmapFont = body, color: Color = Palette.ink) = Label(text, Label.LabelStyle(font, color))

    fun buttonStyle(color: Color, fontColor: Color = Palette.ink, font: BitmapFont = button): TextButton.TextButtonStyle =
        TextButton.TextButtonStyle().apply {
            up = slab(color)
            down = slab(color.cpy().mul(0.9f, 0.9f, 0.9f, 1f))
            checked = slab(Palette.yellow)
            disabled = slab(Palette.mintLine)
            this.font = font
            this.fontColor = fontColor
            disabledFontColor = Palette.inkSoft
        }

    private val arrows = HashMap<Vec, TextureRegion>()

    /**
     * A small picture of a card's vector: an arrow on a few faint squares, drawn to scale but
     * fitted into the icon (so (0, 4) and (0, 2) differ in length).
     */
    fun arrowIcon(v: Vec): TextureRegion = arrows.getOrPut(v) {
        val size = (56 * scale).roundToInt().coerceAtLeast(24)
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver
        val cell = size / 6f
        pm.setColor(Palette.mintLine)
        for (i in 1..5) {
            val p = (i * cell).roundToInt()
            pm.drawLine(p, 0, p, size)
            pm.drawLine(0, p, size, p)
        }
        // Up to 4 squares across shown as they are; longer vectors are shrunk to fit.
        val longest = maxOf(kotlin.math.abs(v.x), kotlin.math.abs(v.y)).toFloat()
        val k = cell * minOf(1f, 4f / longest)
        val c = size / 2f
        val ax = c - v.x * k / 2
        val ay = c + v.y * k / 2
        val bx = c + v.x * k / 2
        val by = c - v.y * k / 2
        val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay)).coerceAtLeast(1e-3f)
        val ux = (bx - ax) / len
        val uy = (by - ay) / len
        val w = size * 0.05f
        val head = minOf(size * 0.24f, len * 0.6f)
        val sx = bx - ux * head * 0.8f
        val sy = by - uy * head * 0.8f
        pm.setColor(Palette.ink)
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
            pm.fillTriangle(x1.roundToInt(), y1.roundToInt(), x2.roundToInt(), y2.roundToInt(), x3.roundToInt(), y3.roundToInt())
        tri(ax - uy * w, ay + ux * w, sx - uy * w, sy + ux * w, sx + uy * w, sy - ux * w)
        tri(ax - uy * w, ay + ux * w, sx + uy * w, sy - ux * w, ax + uy * w, ay - ux * w)
        pm.fillCircle(ax.roundToInt(), ay.roundToInt(), w.roundToInt())
        val hw = head * 0.55f
        val hx = bx - ux * head
        val hy = by - uy * head
        tri(bx, by, hx - uy * hw, hy + ux * hw, hx + uy * hw, hy - ux * hw)
        TextureRegion(texture(pm))
    }

    /** A five-pointed star, filled with [color]. */
    fun star(color: Color): TextureRegion = stars.getOrPut(Color.rgba8888(color)) {
        val size = (64 * scale).roundToInt().coerceAtLeast(16)
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val c = size / 2f
        val outer = size * 0.48f
        val inner = outer * 0.48f
        fun point(i: Int, r: Float): Pair<Int, Int> {
            val a = PI / 2 + i * PI / 5
            return (c + r * cos(a)).roundToInt() to (c - r * sin(a) + size * 0.03f).roundToInt()
        }
        fun drawStar(col: Color, grow: Float) {
            pm.setColor(col)
            for (i in 0 until 10) {
                val (x1, y1) = point(i, (if (i % 2 == 0) outer else inner) * grow)
                val (x2, y2) = point(i + 1, (if ((i + 1) % 2 == 0) outer else inner) * grow)
                pm.fillTriangle(c.roundToInt(), c.roundToInt(), x1, y1, x2, y2)
            }
        }
        drawStar(color.cpy().mul(0.8f, 0.8f, 0.8f, 1f), 1f)
        drawStar(color, 0.86f)
        TextureRegion(texture(pm))
    }
    private val stars = HashMap<Int, TextureRegion>()

    /** A small "play" triangle on a round badge: this button shows a video ad. */
    val adBadge: TextureRegion = run {
        val size = (32 * scale).roundToInt().coerceAtLeast(12)
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.setColor(Palette.ink)
        pm.fillCircle(size / 2, size / 2, size / 2 - 1)
        pm.setColor(Palette.surface)
        val s = size.toFloat()
        pm.fillTriangle((s * 0.38f).roundToInt(), (s * 0.27f).roundToInt(), (s * 0.38f).roundToInt(), (s * 0.73f).roundToInt(), (s * 0.76f).roundToInt(), (s * 0.5f).roundToInt())
        TextureRegion(texture(pm))
    }

    override fun dispose() {
        fonts.forEach { it.dispose() }
        textures.forEach { it.dispose() }
    }
}
