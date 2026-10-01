package com.toyquaise.toothfort.ui

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
import com.toyquaise.toothfort.Palette
import kotlin.math.roundToInt

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
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛΩ₺→−×·–—’“”…"
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

    override fun dispose() {
        fonts.forEach { it.dispose() }
        textures.forEach { it.dispose() }
    }
}
