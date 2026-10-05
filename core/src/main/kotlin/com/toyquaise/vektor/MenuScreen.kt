package com.toyquaise.vektor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.toyquaise.vektor.gfx.Palette
import com.toyquaise.vektor.gfx.Sprites
import com.toyquaise.vektor.logic.Chapter
import com.toyquaise.vektor.logic.Levels
import com.toyquaise.vektor.ui.Strings
import kotlin.math.cos
import kotlin.math.sin

/** The title, a ship looping round a portal, and the levels by chapter with the stars earned. */
class MenuScreen(private val app: VektorGame) : ScreenAdapter() {
    private val g = app.gfx
    private val ui = app.ui
    private val stage = Stage(ExtendViewport(VektorGame.UI_WIDTH, VektorGame.UI_HEIGHT), g.batch)
    private var time = 0f
    private val pixels = Matrix4()

    private val keys = object : InputAdapter() {
        override fun keyUp(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { Gdx.app.exit(); return true }
            return false
        }
    }

    init {
        val root = Table().apply { setFillParent(true); top() }
        stage.addActor(root)
        root.add().height(250f).row()

        val list = Table()
        for (chapter in Chapter.entries) {
            val levels = Levels.all.filter { it.chapter == chapter }
            val head = Table()
            head.add(ui.label(Strings.chapter(chapter).uppercase(), ui.button, Palette.cyan)).left().row()
            head.add(ui.label(Strings.chapterTopic(chapter), ui.tiny, Palette.dim)).left()
            list.add(head).left().padTop(14f).padBottom(8f).row()
            val row = Table()
            for ((i, l) in levels.withIndex()) {
                row.add(levelButton(l.number)).size(86f, 86f).pad(4f)
                if (i % 5 == 4) row.row()
            }
            list.add(row).left().row()
        }
        val scroll = ScrollPane(list).apply { setScrollingDisabled(true, false); fadeScrollBars = true }
        root.add(scroll).grow().pad(0f, 18f, 0f, 18f).row()

        val bottom = Table()
        val play = TextButton("${Strings.PLAY}  ▶", ui.buttonStyle(Palette.magenta, ui.heading))
        play.addListener(changed { app.play(app.progress.next) })
        bottom.add(play).width(320f).height(70f).padTop(10f).row()
        bottom.add(ui.label(Strings.CURRICULUM, ui.tiny, Palette.dim)).padTop(10f).row()
        if (app.ads.privacyOptionsRequired) {
            val privacy = TextButton(Strings.PRIVACY, ui.buttonStyle(Palette.purple, ui.small))
            privacy.addListener(changed { app.ads.showPrivacyOptions() })
            bottom.add(privacy).height(44f).padTop(8f).row()
        }
        root.add(bottom).padBottom(22f)

        Gdx.input.inputProcessor = InputMultiplexer(stage, keys)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
    }

    private fun levelButton(n: Int): Button {
        val open = app.progress.unlocked(n)
        val stars = app.progress.stars(n)
        val edge = when {
            !open -> Color(Palette.faint).also { it.a = 0.6f }
            stars == 3 -> Palette.gold
            stars > 0 -> Palette.cyan
            else -> Palette.magenta
        }
        val b = Button(Button.ButtonStyle().apply {
            up = ui.panel(Color(0.06f, 0.07f, 0.2f, 0.9f), edge, 16f, if (open) 9f else 0f)
            down = ui.panel(Color(0.12f, 0.14f, 0.32f, 0.95f), Palette.white, 16f, 12f)
            disabled = ui.panel(Color(0.04f, 0.05f, 0.12f, 0.8f), edge, 16f, 0f)
        })
        if (open) {
            b.add(ui.label("$n", ui.big, Palette.white)).row()
            val row = Table()
            for (i in 1..3) {
                val img = Image(ui.star)
                img.color = if (i <= stars) Palette.gold else Color(Palette.faint).also { it.a = 0.5f }
                row.add(img).size(18f).pad(0f, 1f, 0f, 1f)
            }
            b.add(row)
        } else {
            b.add(Image(ui.lock)).size(30f)
        }
        b.isDisabled = !open
        b.addListener(changed { if (open) app.play(n) })
        return b
    }

    private fun changed(f: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = f()
    }

    override fun render(delta: Float) {
        time += delta
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        g.begin(pixels.setToOrtho2D(0f, 0f, w, h))
        app.stars.draw(g, w, h)
        g.end()

        stage.viewport.apply()
        val cam = stage.camera
        g.begin(cam.combined)
        val sw = stage.viewport.worldWidth
        val sh = stage.viewport.worldHeight
        // The title, with a glow behind it.
        val cx = sw / 2
        val ty = sh - 70f
        g.glow(cx, ty, 260f, Palette.magenta, 0.35f)
        g.glow(cx, ty, 150f, Palette.cyan, 0.25f)
        val b = g.sprites(add = false)
        app.layout.setText(ui.title, Strings.TITLE)
        ui.title.color = Palette.white
        ui.title.draw(b, Strings.TITLE, cx - app.layout.width / 2, ty + app.layout.height / 2)
        app.layout.setText(ui.body, Strings.TAGLINE)
        ui.body.color = Palette.cyan
        ui.body.draw(b, Strings.TAGLINE, cx - app.layout.width / 2, ty - 44f)
        // A ship looping round a portal, in a scene of its own scale.
        val scale = 62f
        val px = cx
        val py = sh - 205f
        g.end()
        g.begin(Matrix4(cam.combined).translate(px, py, 0f).scale(scale, scale, 1f))
        Sprites.portal(g, 0f, 0f, time)
        val a = time * 1.1f
        val sx = cos(a) * 2.6f
        val sy = sin(a) * 0.75f
        Sprites.ship(g, sx, sy, a + MathUtils.PI / 2 + 0.25f * cos(a), 1f, time, 1.1f)
        Sprites.crystal(g, -3.4f, 0.4f, time, 0f)
        Sprites.asteroid(g, 3.5f, -0.5f, 3, time)
        g.end()

        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
    }
}
