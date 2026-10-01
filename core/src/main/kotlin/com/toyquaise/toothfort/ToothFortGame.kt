package com.toyquaise.toothfort

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.toyquaise.toothfort.logic.game.Levels
import com.toyquaise.toothfort.render.ClayRenderer
import com.toyquaise.toothfort.render.Models
import com.toyquaise.toothfort.ui.UiKit

/**
 * Start-up options, used by the desktop launcher for development: open a given level, build a
 * sample defence and play part of a wave, then save a screenshot and quit.
 */
class Options(
    val level: Int = 0,
    val demo: Boolean = false,
    val screenshot: String? = null,
    val seconds: Float = 6f,
    /** Look closely at this cell instead of framing the whole board (for checking models). */
    val closeUp: Pair<Int, Int>? = null,
    /** Draw only the 3D board (for screenshots of the models). */
    val hideHud: Boolean = false,
    /**
     * Scripted touches for checking the controls, one step per frame, separated by ";":
     * "tool battery|brush|cannon|laser|switch|wire|thick|erase", "down x y", "move x y", "up x y"
     * (counter units), "wait n" (frames), "shot" (take the screenshot now).
     */
    val script: String? = null,
)

class ToothFortGame(val options: Options = Options()) : Game() {
    lateinit var renderer: ClayRenderer
        private set
    lateinit var models: Models
        private set
    lateinit var kit: UiKit
        private set
    lateinit var batch: SpriteBatch
        private set
    val layout = GlyphLayout()

    private var frames = 0

    /** Set by the play screen when a script asks for the screenshot. */
    var shotNow = false

    override fun create() {
        renderer = ClayRenderer()
        models = Models()
        batch = SpriteBatch()
        val scale = minOf(Gdx.graphics.width / PlayScreen.UI_WIDTH, Gdx.graphics.height / PlayScreen.UI_HEIGHT)
        kit = UiKit(scale.coerceAtLeast(0.5f))
        play(options.level.coerceIn(0, Levels.all.lastIndex))
    }

    fun play(index: Int) {
        val old = screen
        val next = PlayScreen(this, index.coerceIn(0, Levels.all.lastIndex))
        setScreen(next)
        old?.dispose()
        if (options.demo) Demo.build(next.game, options.seconds)?.let(next::select)
    }

    override fun render() {
        super.render()
        val path = options.screenshot ?: return
        frames++
        // A few frames for the view to settle, then save what is on screen.
        val due = if (options.script != null) shotNow else frames == 20
        if (due) {
            shotNow = false
            val w = Gdx.graphics.backBufferWidth
            val h = Gdx.graphics.backBufferHeight
            val pm = Pixmap.createFromFrameBuffer(0, 0, w, h)
            val flipped = Pixmap(w, h, Pixmap.Format.RGBA8888)
            for (y in 0 until h) flipped.drawPixmap(pm, 0, y, w, 1, 0, h - 1 - y, w, 1)
            // The back buffer may carry alpha; a screenshot should not.
            flipped.blending = Pixmap.Blending.None
            for (y in 0 until h) for (x in 0 until w) flipped.drawPixel(x, y, flipped.getPixel(x, y) or 0xff)
            PixmapIO.writePNG(Gdx.files.absolute(path), flipped)
            pm.dispose()
            flipped.dispose()
            Gdx.app.log("ToothFort", "screenshot saved to $path")
            Gdx.app.exit()
        }
    }

    override fun dispose() {
        screen?.dispose()
        renderer.dispose()
        models.dispose()
        kit.dispose()
        batch.dispose()
    }
}
