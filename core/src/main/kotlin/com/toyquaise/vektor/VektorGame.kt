package com.toyquaise.vektor

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.toyquaise.vektor.gfx.Gfx
import com.toyquaise.vektor.gfx.Starfield
import com.toyquaise.vektor.logic.AdPacing
import com.toyquaise.vektor.logic.Levels
import com.toyquaise.vektor.logic.Vec
import com.toyquaise.vektor.ui.Ui

/**
 * Start-up options, used by the desktop launcher for development, screenshots and videos: open a
 * level straight away, make some moves with the hints, show an aimed move, then save a picture.
 */
class Options(
    /** Level to open (1-based), or null for the level list. */
    val level: Int? = null,
    /** Moves to make with the hints before showing the level. */
    val steps: Int = 0,
    /** Show this move being aimed (relative to the ship), as if a finger were dragging it. */
    val aim: Vec? = null,
    /** Aim the hint's next move. */
    val aimHint: Boolean = false,
    /** Finish the level with the hints (shows the end panel). */
    val finish: Boolean = false,
    val screenshot: String? = null,
    /** Draw no HUD (for pictures of the map). */
    val hideHud: Boolean = false,
    /** Keep progress in memory only (screenshots, videos), not in the device's preferences. */
    val volatile: Boolean = false,
    /** Play the level by itself with the hints, at a watchable pace (for videos). */
    val autoplay: Boolean = false,
    /** Save every frame into this folder for [seconds] seconds (for videos), then quit. */
    val record: String? = null,
    val seconds: Float = 12f,
)

class VektorGame(val options: Options = Options(), val ads: Ads = NoAds) : Game() {
    lateinit var gfx: Gfx
        private set
    lateinit var ui: Ui
        private set
    lateinit var progress: Progress
        private set
    val stars = Starfield()
    val layout = GlyphLayout()
    val pacing = AdPacing()

    private var frames = 0

    override fun create() {
        gfx = Gfx()
        val scale = minOf(Gdx.graphics.width / UI_WIDTH, Gdx.graphics.height / UI_HEIGHT)
        ui = Ui(scale.coerceAtLeast(0.5f))
        progress = Progress(if (options.volatile) null else Gdx.app.getPreferences("vektorpilotu"))
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

    private fun show(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun render() {
        // While recording, time runs at the video's frame rate however slowly frames are drawn.
        val dt = if (options.record != null) 1f / RECORD_FPS else minOf(Gdx.graphics.deltaTime, 0.1f)
        stars.update(dt)
        screen?.render(dt)
        frames++
        options.record?.let { dir ->
            capture("$dir/frame%04d.png".format(frames))
            if (frames >= options.seconds * RECORD_FPS) Gdx.app.exit()
        }
        val path = options.screenshot ?: return
        // Some frames for the view to settle, then save what is on screen.
        if (frames == SCREENSHOT_FRAME) {
            capture(path)
            Gdx.app.log("VektorPilotu", "screenshot saved to $path")
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
        gfx.dispose()
        ui.dispose()
    }

    companion object {
        const val UI_WIDTH = 540f
        const val UI_HEIGHT = 960f
        const val SCREENSHOT_FRAME = 40
        const val RECORD_FPS = 30
    }
}
