package com.toyquaise.kaptan

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.toyquaise.kaptan.logic.Chapter
import com.toyquaise.kaptan.logic.Level
import com.toyquaise.kaptan.logic.Levels
import com.toyquaise.kaptan.logic.Vec
import com.toyquaise.kaptan.logic.Voyage
import com.toyquaise.kaptan.render.Framing
import com.toyquaise.kaptan.render.SeaView
import com.toyquaise.kaptan.ui.Strings

/** The title and the list of levels by chapter, with the stars earned; a little sea on top. */
class MenuScreen(private val app: KaptanGame) : ScreenAdapter() {
    private val stage = Stage(ExtendViewport(KaptanGame.UI_WIDTH, KaptanGame.UI_HEIGHT), app.batch)
    private val camera = PerspectiveCamera(30f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

    /** A small sea with the boat waiting by the harbor, as the title picture. */
    private val scenery = SeaView(app.models, Voyage(Level(0, Chapter.HARBOR, 4, 1, Vec(1, 0), Vec(3, 1), setOf(Vec(4, 0)), listOf(Vec(2, 1)))))
    private val picture = Actor()

    private val keys = object : InputAdapter() {
        override fun keyUp(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { Gdx.app.exit(); return true }
            return false
        }
    }

    init {
        val kit = app.kit
        val root = Table().apply { setFillParent(true); top() }
        stage.addActor(root)
        root.add(kit.label(Strings.TITLE, kit.title, Palette.teal)).padTop(26f).row()
        root.add(kit.label(Strings.SUBTITLE, kit.button, Palette.inkSoft)).padTop(2f).row()
        root.add(picture).height(210f).growX().row()

        val list = Table()
        for (chapter in Chapter.entries) {
            val levels = Levels.all.filter { it.chapter == chapter }
            list.add(kit.label("${Strings.chapter(chapter)} · ${Strings.chapterTopic(chapter)}", kit.bodyStrong, Palette.ink))
                .left().padTop(12f).padBottom(6f).row()
            val row = Table()
            for ((i, l) in levels.withIndex()) {
                row.add(levelButton(l.number)).size(112f, 100f).pad(4f)
                if (i % 4 == 3) row.row()
            }
            list.add(row).left().row()
        }
        val scroll = ScrollPane(list).apply { setScrollingDisabled(true, false); fadeScrollBars = true }
        root.add(scroll).grow().pad(0f, 14f, 0f, 14f).row()

        val bottom = Table().apply { background = kit.slab(Palette.surface, 26, 0) }
        val play = TextButton("${Strings.PLAY} · ${Strings.LEVEL} ${app.progress.next}", kit.buttonStyle(Palette.coral, Palette.surface))
        play.addListener(changed { app.play(app.progress.next) })
        bottom.add(kit.label(Strings.CURRICULUM, kit.small, Palette.inkSoft)).padTop(10f).row()
        bottom.add(play).width(320f).height(64f).pad(8f, 0f, 10f, 0f).row()
        if (app.ads.privacyOptionsRequired) {
            val privacy = TextButton(Strings.PRIVACY, kit.buttonStyle(Palette.cream, font = kit.tool))
            privacy.addListener(changed { app.ads.showPrivacyOptions() })
            bottom.add(privacy).height(46f).padBottom(12f)
        }
        root.add(bottom).growX()

        Gdx.input.inputProcessor = InputMultiplexer(stage, keys)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
    }

    private fun levelButton(n: Int): Button {
        val kit = app.kit
        val open = app.progress.unlocked(n)
        val stars = app.progress.stars(n)
        val color = when {
            !open -> Palette.mintLine
            stars > 0 -> Palette.turquoise
            else -> Palette.yellow
        }
        val b = Button(Button.ButtonStyle().apply {
            up = kit.slab(color, 18, 5)
            down = kit.slab(color.cpy().mul(0.9f, 0.9f, 0.9f, 1f), 18, 5)
            disabled = kit.slab(Palette.mintLine, 18, 5)
        })
        b.add(kit.label("$n", kit.heading, if (stars > 0) Palette.surface else Palette.ink).apply { setAlignment(Align.center) }).row()
        val row = Table()
        for (i in 1..3) row.add(Image(kit.star(if (i <= stars) Palette.yellow else Palette.surface.cpy().apply { a = if (open) 0.7f else 0.4f }))).size(22f).pad(0f, 1f, 0f, 1f)
        b.add(row)
        b.isDisabled = !open
        b.addListener(changed { if (open) app.play(n) })
        return b
    }

    private fun changed(f: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = f()
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, 0.1f)
        scenery.update(dt)
        Gdx.gl.glClearColor(Palette.table.r, Palette.table.g, Palette.table.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        app.renderer.begin(camera, Vector3(2f, 0f, -0.5f), 4f)
        scenery.draw(app.renderer)
        app.renderer.end()
        stage.act(dt)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        (stage.root.children.first() as Table).validate()
        // The title picture fills the empty band left for it in the layout.
        val lo = picture.localToScreenCoordinates(Vector2(0f, 0f))
        val hi = picture.localToScreenCoordinates(Vector2(0f, picture.height))
        val m = 0.9f
        val points = listOf(
            Vector3(-m, 0f, m), Vector3(4f + m, 0f, m), Vector3(-m, 0f, -1f - m), Vector3(4f + m, 0f, -1f - m),
            Vector3(3.42f, 0.6f, -1.42f),
        )
        Framing.fit(camera, points, width, height, height - lo.y, height - hi.y, 52f)
    }

    override fun dispose() {
        stage.dispose()
        scenery.dispose()
    }
}
