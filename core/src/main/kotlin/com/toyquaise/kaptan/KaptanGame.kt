package com.toyquaise.kaptan

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.toyquaise.kaptan.logic.AdPacing
import com.toyquaise.kaptan.logic.Levels
import com.toyquaise.kaptan.render.ClayRenderer
import com.toyquaise.kaptan.render.Models
import com.toyquaise.kaptan.ui.UiKit

/**
 * Start-up options, used by the desktop launcher for development and screenshots: open a level
 * straight away, play part of it with the hints, show a planned move, then save a screenshot.
 */
class Options(
    /** Level to open (1-based), or null for the level list. */
    val level: Int? = null,
    /** Moves to make with the hints before showing the level. */
    val steps: Int = 0,
    /** Select the hint's next move, so its arrows show. */
    val preview: Boolean = false,
    /** Select this card of the hand (0-based), so its arrows show. */
    val pick: Int? = null,
    /** Finish the level with the hints (shows the end card). */
    val finish: Boolean = false,
    val screenshot: String? = null,
    /** Draw only the 3D sea (for screenshots of the models). */
    val hideHud: Boolean = false,
    /** Keep progress in memory only (screenshots, tests), not in the device's preferences. */
    val volatile: Boolean = false,
    /** Play the level by itself with the hints, at a watchable pace (for videos). */
    val autoplay: Boolean = false,
    /** Save every frame into this folder for [seconds] seconds (for videos), then quit. */
    val record: String? = null,
    val seconds: Float = 12f,
)

class KaptanGame(val options: Options = Options(), val ads: Ads = NoAds) : Game() {
    lateinit var renderer: ClayRenderer
        private set
    lateinit var models: Models
        private set
    lateinit var kit: UiKit
        private set
    lateinit var batch: SpriteBatch
        private set
    lateinit var progress: Progress
        private set
    val layout = GlyphLayout()
    val pacing = AdPacing()

    private var frames = 0

    override fun create() {
        renderer = ClayRenderer()
        models = Models()
        batch = SpriteBatch()
        val scale = minOf(Gdx.graphics.width / UI_WIDTH, Gdx.graphics.height / UI_HEIGHT)
        kit = UiKit(scale.coerceAtLeast(0.5f))
        progress = Progress(if (options.volatile) null else Gdx.app.getPreferences("hamurkaptan"))
        val start = options.level
        if (start != null) play(start) else menu()
    }

    fun menu() = show(MenuScreen(this))

    fun play(level: Int) = show(PlayScreen(this, level.coerceIn(1, Levels.all.size)))

    /** After a finished level: maybe an ad (see [AdPacing]), then the next level. */
    fun next(level: Int) {
        val go = { if (level < Levels.all.size) play(level + 1) else menu() }
        if (pacing.due()) ads.interstitial(go) else go()
    }

    private fun show(next: com.badlogic.gdx.Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun render() {
        // While recording, time runs at the video's frame rate however slowly frames are drawn.
        screen?.render(if (options.record != null) 1f / RECORD_FPS else Gdx.graphics.deltaTime)
        frames++
        options.record?.let { dir ->
            capture("$dir/frame%04d.png".format(frames))
            if (frames >= options.seconds * RECORD_FPS) Gdx.app.exit()
        }
        val path = options.screenshot ?: return
        // Some frames for the view to settle, then save what is on screen.
        if (frames == SCREENSHOT_FRAME) {
            capture(path)
            Gdx.app.log("HamurKaptan", "screenshot saved to $path")
            Gdx.app.exit()
        }
    }

    /** Saves what is on screen as a PNG. */
    private fun capture(path: String) {
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
    }

    override fun dispose() {
        screen?.dispose()
        renderer.dispose()
        models.dispose()
        kit.dispose()
        batch.dispose()
    }

    companion object {
        const val UI_WIDTH = 540f
        const val UI_HEIGHT = 960f
        const val SCREENSHOT_FRAME = 24
        const val RECORD_FPS = 30
    }
}
