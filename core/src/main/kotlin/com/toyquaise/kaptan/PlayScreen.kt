package com.toyquaise.kaptan

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.toyquaise.kaptan.logic.Levels
import com.toyquaise.kaptan.logic.Play
import com.toyquaise.kaptan.logic.Problem
import com.toyquaise.kaptan.logic.Solver
import com.toyquaise.kaptan.logic.Voyage
import com.toyquaise.kaptan.render.Framing
import com.toyquaise.kaptan.render.Models
import com.toyquaise.kaptan.render.SeaView
import com.toyquaise.kaptan.ui.Hud
import com.toyquaise.kaptan.ui.HudListener
import com.toyquaise.kaptan.ui.Strings

/** One level: the squared sea in 3D, the panel below it, and sailing card by card. */
class PlayScreen(private val app: KaptanGame, val levelNumber: Int) : ScreenAdapter(), HudListener {
    private val level = Levels.all[levelNumber - 1]
    val voyage = Voyage(level)

    /** The best way, for the stars. */
    private val best = Solver.solve(level)
    private val view: SeaView
    private val camera = PerspectiveCamera(30f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    private val stage = Stage(ExtendViewport(KaptanGame.UI_WIDTH, KaptanGame.UI_HEIGHT), app.batch)
    private val hud = Hud(app.kit, stage, this)
    private val focus = Vector3()
    private var finished = false
    private var outOfCardsShown = false

    private val keys = object : InputAdapter() {
        override fun keyUp(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { levels(); return true }
            return false
        }
    }

    init {
        playDemo()
        view = SeaView(app.models, voyage)
        Gdx.input.inputProcessor = InputMultiplexer(stage, keys)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        hud.bind(voyage)
        if (app.options.preview) Solver.hint(voyage)?.let { h ->
            when (h) {
                is Solver.Hint.Move -> hud.choose(h.play)
                is Solver.Hint.Split -> hud.chooseCard(h.card.id)
            }
        }
        app.options.pick?.let { i -> voyage.hand.getOrNull(i)?.let { hud.chooseCard(it.id) } }
        if (app.options.steps > 0 || app.options.preview || app.options.pick != null) hud.hideToast()
    }

    /** For screenshots and trying things quickly: some moves made with the hints. */
    private fun playDemo() {
        if (app.options.level != levelNumber) return
        var steps = if (app.options.finish) Int.MAX_VALUE else app.options.steps
        while (steps-- > 0 && !voyage.arrived) {
            when (val h = Solver.hint(voyage) ?: break) {
                is Solver.Hint.Split -> voyage.split(h.card.id)
                is Solver.Hint.Move -> voyage.play(h.play)
            }
        }
    }

    // ------------------------------------------------------------------ HUD actions

    override fun planChanged(play: Play?) {
        val check = play?.let(voyage::check)
        view.preview = check
        // Say straight away why a planned move cannot be made, before "Yola çık" is pressed.
        val problem = check?.problem
        if (problem in AT_ONCE) hud.showToast(Strings.problem(problem!!))
    }

    override fun go(play: Play) {
        val problem = voyage.play(play)
        if (problem != null) {
            hud.showToast(Strings.problem(problem))
            return
        }
        hud.clearPlan()
        hud.hideToast()
    }

    override fun split(card: Int) {
        val vec = voyage.card(card)?.vec
        val problem = voyage.split(card)
        if (problem != null) hud.showToast(Strings.problem(problem))
        else if (vec != null) hud.showToast(Strings.split(vec), 5f)
    }

    override fun undo() {
        voyage.undo()
        outOfCardsShown = false
    }

    override fun reset() {
        voyage.reset()
        outOfCardsShown = false
    }

    override fun hint() {
        if (view.busy || voyage.arrived) return
        // A dead end needs no ad: say so straight away.
        val h = Solver.hint(voyage)
        if (h == null) {
            hud.showToast(if (voyage.hand.isEmpty()) Strings.OUT_OF_CARDS else Strings.DEAD_END)
            return
        }
        app.ads.rewarded(
            rewarded = { showHint(h) },
            unavailable = {
                showHint(h)
                hud.showToast(Strings.HINT_FREE)
            },
        )
    }

    private fun showHint(h: Solver.Hint) {
        when (h) {
            is Solver.Hint.Split -> {
                hud.chooseCard(h.card.id)
                hud.showToast(Strings.hintSplit(h.card.vec), 5f)
            }
            is Solver.Hint.Move -> {
                hud.choose(h.play)
                hud.showToast(Strings.hintMove(h.play.picks.map { voyage.card(it.card)!!.vec to it.factor }), 5f)
            }
        }
    }

    override fun levels() = app.menu()

    override fun next() = app.next(levelNumber)

    override fun again() = app.play(levelNumber)

    // ------------------------------------------------------------------ frame

    private var autoTimer = 0f
    private var autoSplit: Int? = null

    /** Plays the level with the hints: show the next move, wait, sail, and again (for videos). */
    private fun autoplay(dt: Float) {
        if (!app.options.autoplay || view.busy || voyage.arrived) return
        autoTimer += dt
        val split = autoSplit
        when {
            split != null && autoTimer > 1.6f -> {
                autoSplit = null
                autoTimer = 0f
                hud.clearPlan()
                split(split)
            }
            split == null && hud.plan() == null && autoTimer > 1.3f -> {
                autoTimer = 0f
                val h = Solver.hint(voyage) ?: return
                showHint(h)
                if (h is Solver.Hint.Split) autoSplit = h.card.id
            }
            split == null && hud.plan() != null && autoTimer > 2.2f -> {
                autoTimer = 0f
                hud.plan()?.let(::go)
            }
        }
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, 0.1f)
        autoplay(dt)
        view.update(dt)
        hud.locked = view.busy || finished
        if (!view.busy && !voyage.arrived && voyage.hand.isEmpty() && !outOfCardsShown) {
            outOfCardsShown = true
            hud.showToast(Strings.OUT_OF_CARDS, 5f)
        }
        if (voyage.arrived && !finished && view.arrivedFor > 0.9f) finish()

        Gdx.gl.glClearColor(Palette.table.r, Palette.table.g, Palette.table.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        app.renderer.begin(camera, focus, maxOf(level.width, level.height) / 2f + 2f)
        view.draw(app.renderer)
        app.renderer.end()

        hud.update(dt)
        if (app.options.hideHud) return
        drawOverlay()
        stage.draw()
    }

    private fun finish() {
        finished = true
        val stars = voyage.stars(best)
        app.progress.record(levelNumber, stars)
        app.pacing.levelFinished()
        val lines = listOf(
            Strings.movesLine(voyage.plays, best?.plays ?: voyage.plays),
            Strings.roadLine(voyage.travelled),
            Strings.displacementLine(voyage.displacement),
        )
        val note = if (voyage.travelled > voyage.displacement.length + 1e-6)
            "Yer değiştirme, alınan yoldan kısadır: o, çıkıştan varışa dümdüz giden bileşke vektördür."
        else "Dümdüz gittin: alınan yol, yer değiştirmenin büyüklüğüne eşit."
        hud.showEnd(stars, lines, note, levelNumber < Levels.all.size)
    }

    private val screen = Vector3()
    private val stagePos = Vector2()

    /** Vectors beside their arrows and the numbers along the axes, written over the 3D sea. */
    private fun drawOverlay() {
        val uiCam = stage.camera
        uiCam.update()
        val batch = app.batch
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        val axisFont = app.kit.small
        for (t in view.axisTags()) {
            if (!toStage(t.pos)) continue
            app.layout.setText(axisFont, t.text)
            axisFont.color = t.color
            axisFont.draw(batch, t.text, stagePos.x - app.layout.width / 2, stagePos.y + app.layout.height / 2)
        }
        if (!hud.ended) for (t in view.tags) {
            if (!toStage(t.pos)) continue
            val font = app.kit.small
            app.layout.setText(font, t.text)
            val w = app.layout.width + 14f
            val h = app.layout.height + 10f
            app.kit.slab(Palette.surface, 9, 2).draw(batch, stagePos.x - w / 2, stagePos.y - h / 2, w, h)
            font.color = t.color
            font.draw(batch, t.text, stagePos.x - app.layout.width / 2, stagePos.y + app.layout.height / 2 + 1f)
        }
        axisFont.color = Color.WHITE
        batch.end()
    }

    /** Projects a world point into HUD coordinates ([stagePos]); false if it is behind the camera. */
    private fun toStage(p: Vector3): Boolean {
        screen.set(p)
        camera.project(screen)
        if (screen.z !in 0f..1f) return false
        stagePos.set(screen.x, Gdx.graphics.height - screen.y)
        stage.screenToStageCoordinates(stagePos)
        return true
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        stage.act(0f)
        val uiScale = stage.viewport.screenHeight / stage.viewport.worldHeight
        val w = level.width.toFloat()
        val h = level.height.toFloat()
        val m = Models.MARGIN + 0.35f
        val points = listOf(
            Vector3(-m - 0.4f, 0f, m + 0.45f), Vector3(w + m, 0f, m + 0.45f),
            Vector3(-m - 0.4f, 0f, -h - m), Vector3(w + m, 0f, -h - m),
            Vector3(level.harbor.x + 0.42f, 0.6f, -level.harbor.y - 0.42f),
        )
        Framing.fit(camera, points, width, height, (hud.bottomHeight + 6f) * uiScale, height - TOP_BAR * uiScale, PITCH)
        focus.set(w / 2, 0f, -h / 2)
    }

    override fun dispose() {
        stage.dispose()
        view.dispose()
    }

    companion object {
        const val TOP_BAR = 80f
        const val PITCH = 70f

        /** Problems worth saying as soon as the move is planned. */
        private val AT_ONCE = setOf(Problem.ROCK, Problem.OFF_SEA, Problem.NO_MOVE, Problem.ODD_HALF)
    }
}
