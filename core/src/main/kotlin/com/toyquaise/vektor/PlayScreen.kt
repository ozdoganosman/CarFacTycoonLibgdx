package com.toyquaise.vektor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.toyquaise.vektor.gfx.Palette
import com.toyquaise.vektor.gfx.Particles
import com.toyquaise.vektor.gfx.Sprites
import com.toyquaise.vektor.logic.Aim
import com.toyquaise.vektor.logic.Flight
import com.toyquaise.vektor.logic.Levels
import com.toyquaise.vektor.logic.Piece
import com.toyquaise.vektor.logic.Solver
import com.toyquaise.vektor.logic.Space
import com.toyquaise.vektor.logic.Vec
import com.toyquaise.vektor.ui.Strings
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * One level: the map of space with the ship, asteroids, crystals and the portal; drag from the
 * ship to aim a piece of the five units of fuel, let go to fly it.
 */
class PlayScreen(private val app: VektorGame, val levelNumber: Int) : ScreenAdapter() {
    private val level = Levels.all[levelNumber - 1]
    val flight = Flight(level)
    private val g = app.gfx
    private val ui = app.ui

    private val world = OrthographicCamera()
    private val stage = Stage(ExtendViewport(VektorGame.UI_WIDTH, VektorGame.UI_HEIGHT), g.batch)
    private val particles = Particles()
    private var time = 0f

    /** The move being aimed, while a finger is down. */
    private var aim: Aim? = null

    // The ship as drawn: where it is, which way it points, and the move it is flying.
    private val shipAt = Vector2(level.start.x.toFloat(), level.start.y.toFloat())
    private var shipAngle = angleOf(level.portal - level.start)
    private var sailing: Piece? = null
    private var sailT = 0f
    private var sailTime = 1f
    private var shownPieces = 0
    private val shownCrystals = HashSet<Vec>()
    private var arrivedFor = -1f
    private var shake = 0f
    private val rnd = Random(1)

    private var finished = false
    private var strandedShown = false
    private var fuelShown = level.fuel.toFloat()

    // ------------------------------------------------------------------ HUD

    private val titleLabel = ui.label("${Strings.LEVEL} ${level.number}", ui.heading, Palette.white)
    private val chapterLabel = ui.label(Strings.chapter(level.chapter).uppercase(), ui.tiny, Palette.cyan)
    private val crystalLabel = ui.label("0/2", ui.button, Palette.gold)
    private val toast = ui.label("", ui.body).apply { setWrap(true); setAlignment(Align.center) }
    private val toastBox = Container(toast).apply {
        background = ui.panel(Color(0.06f, 0.08f, 0.2f, 0.92f), Palette.cyan, 16f, 8f, 1.5f)
        pad(12f, 18f, 12f, 18f)
        fill()
        isVisible = false
        touchable = Touchable.disabled
    }
    private var toastTime = 0f
    private val undoButton = ui.iconButton(ui.undo, Palette.cyan)
    private val restartButton = ui.iconButton(ui.restart, Palette.cyan)
    private val hintButton = ui.iconButton(ui.bulb, Palette.gold)
    private val endPanel = Table().apply { isVisible = false }
    private val root = Table()
    private val adBadge = Image(ui.play).apply { touchable = Touchable.disabled }

    /** Heights of the HUD at the top and bottom, in UI units, for framing the map between them. */
    private val topHud = 200f
    private val bottomHud = 120f

    private val keys = object : InputAdapter() {
        override fun keyUp(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { app.menu(); return true }
            return false
        }
    }

    private val touch = object : InputAdapter() {
        private var pointer = -1

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (this.pointer != -1 || busy) return false
            this.pointer = pointer
            aimAt(screenX, screenY)
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (pointer != this.pointer) return false
            aimAt(screenX, screenY)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer != this.pointer) return false
            this.pointer = -1
            aimAt(screenX, screenY)
            release()
            return true
        }
    }

    init {
        buildHud()
        playDemo()
        // A flight already under way (a demo) is shown where it is.
        shownPieces = flight.pieces.size
        shownCrystals += flight.collected
        shipAt.set(flight.position.x.toFloat(), flight.position.y.toFloat())
        flight.pieces.lastOrNull()?.let { shipAngle = angleOf(it.thrust) }
        if (flight.arrived) arrivedFor = 10f
        fuelShown = flight.fuelLeft.toFloat()
        Gdx.input.inputProcessor = InputMultiplexer(stage, keys, touch)
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        val opts = app.options
        if (opts.level == levelNumber) {
            opts.aim?.let { aim = flight.check(it) }
            if (opts.aimHint) Solver.hint(flight)?.let { aim = flight.check(it) }
        }
        val tip = Strings.tip(level.number)
        if (tip.isNotEmpty() && flight.pieces.isEmpty() && aim == null) say(tip, 9f)
    }

    private fun playDemo() {
        if (app.options.level != levelNumber) return
        var steps = if (app.options.finish) Int.MAX_VALUE else app.options.steps
        while (steps-- > 0 && !flight.arrived) flight.fly(Solver.hint(flight) ?: break)
    }

    private val busy: Boolean get() = sailing != null || finished || endPanel.isVisible

    // ------------------------------------------------------------------ aiming and flying

    private val tmp3 = Vector3()

    /** Aims at the grid point under the finger. */
    private fun aimAt(screenX: Int, screenY: Int) {
        if (busy) return
        world.unproject(tmp3.set(screenX.toFloat(), screenY.toFloat(), 0f))
        val target = Vec(tmp3.x.roundToInt(), tmp3.y.roundToInt())
        val thrust = target - flight.position - level.wind
        val next = flight.check(thrust)
        if (aim?.thrust != next.thrust) aim = next
    }

    private fun release() {
        val a = aim ?: return
        aim = null
        if (a.thrust.isZero) return
        if (!a.ok) {
            shake = 0.35f
            say(Strings.problem(a.problem!!), 2.5f)
            return
        }
        fly(a.thrust)
    }

    private fun fly(thrust: Vec) {
        if (flight.fly(thrust) == null) hideToast()
    }

    private fun undo() {
        if (busy) return
        flight.undo()
        resync()
    }

    private fun restart() {
        if (finished) return
        flight.reset()
        resync()
    }

    /** Puts the ship back where the flight says, after undo or a restart. */
    private fun resync() {
        sailing = null
        aim = null
        shownPieces = flight.pieces.size
        shownCrystals.clear()
        shownCrystals += flight.collected
        shipAt.set(flight.position.x.toFloat(), flight.position.y.toFloat())
        shipAngle = flight.pieces.lastOrNull()?.let { angleOf(it.thrust) } ?: angleOf(level.portal - level.start)
        strandedShown = false
        endPanel.isVisible = false
        particles.burst(shipAt.x, shipAt.y, 10, listOf(Palette.cyan, Palette.white), 2f, 0.06f, 0.5f)
    }

    private fun hint() {
        if (busy) return
        val h = Solver.hint(flight)
        if (h == null) {
            say(Strings.DEAD_END, 3f)
            return
        }
        app.ads.rewarded(
            rewarded = { showHint(h) },
            unavailable = {
                showHint(h)
                say(Strings.HINT_FREE, 3f)
            },
        )
    }

    private fun showHint(h: Vec) {
        aim = flight.check(h)
        hintShowing = 3.5f
        say(Strings.hint(h), 3.5f)
    }

    /** Seconds left to show a hint's arrow without a finger on the screen. */
    private var hintShowing = 0f

    private fun angleOf(v: Vec): Float = atan2(v.y.toFloat(), v.x.toFloat())

    // ------------------------------------------------------------------ frame

    override fun render(delta: Float) {
        val dt = delta
        time += dt
        autoplay(dt)
        update(dt)

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        g.begin(pixels.setToOrtho2D(0f, 0f, w, h))
        app.stars.draw(g, w, h)
        g.end()

        val sx = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake * 0.5f else 0f
        val sy = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake * 0.5f else 0f
        world.position.add(sx, sy, 0f)
        world.update()
        g.begin(world.combined)
        drawMap()
        g.end()
        world.position.sub(sx, sy, 0f)
        world.update()

        if (app.options.hideHud) return
        stage.viewport.apply()
        g.begin(stage.camera.combined)
        drawLabels()
        drawFuel()
        g.end()
        stage.draw()
    }

    private val pixels = Matrix4()

    private fun update(dt: Float) {
        particles.update(dt)
        shake = maxOf(0f, shake - dt)
        if (hintShowing > 0f) {
            hintShowing -= dt
            if (hintShowing <= 0f && sailing == null) aim = null
        }
        // Start sailing the next move the flight has made.
        if (sailing == null && flight.pieces.size > shownPieces) {
            val p = flight.pieces[shownPieces++]
            sailing = p
            sailT = 0f
            sailTime = 0.35f + p.length.toFloat() * 0.13f
            aim = null
            hintShowing = 0f
        }
        val p = sailing
        if (p != null) {
            sailT += dt
            val k = Interpolation.pow2.apply((sailT / sailTime).coerceIn(0f, 1f))
            shipAt.set(p.from.x + (p.to.x - p.from.x) * k, p.from.y + (p.to.y - p.from.y) * k)
            turnTo(angleOf(p.thrust), dt)
            val back = Vector2(cos(shipAngle), sin(shipAngle)).scl(-1.6f)
            particles.exhaust(shipAt.x - cos(shipAngle) * 0.25f, shipAt.y - sin(shipAngle) * 0.25f, back.x, back.y, Palette.orange, 0.07f)
            for (c in p.crystals) {
                if (c in shownCrystals) continue
                val along = (c - p.from).length / p.length.coerceAtLeast(1e-6)
                if (k >= along - 1e-3) {
                    shownCrystals += c
                    particles.burst(c.x.toFloat(), c.y.toFloat(), 22, listOf(Palette.gold, Palette.white, Palette.orange), 3.2f, 0.07f)
                }
            }
            if (sailT >= sailTime) {
                sailing = null
                shipAt.set(p.to.x.toFloat(), p.to.y.toFloat())
                if (shownPieces == flight.pieces.size && flight.arrived) {
                    arrivedFor = 0f
                    particles.burst(shipAt.x, shipAt.y, 50, listOf(Palette.magenta, Palette.cyan, Palette.white, Palette.gold), 4.5f, 0.08f, 1.1f)
                }
            }
        }
        if (arrivedFor >= 0f) arrivedFor += dt
        if (flight.arrived && !finished && arrivedFor > 0.9f) finish()
        if (sailing == null && flight.stranded && !strandedShown) {
            strandedShown = true
            showStranded()
        }
        val target = flight.fuelLeft.toFloat()
        fuelShown += (target - fuelShown) * minOf(1f, dt * 6f)
        undoButton.isDisabled = busy || !flight.canUndo
        restartButton.isDisabled = finished || !flight.canUndo
        hintButton.isDisabled = busy
        crystalLabel.setText("${shownCrystals.size}/${level.crystals.size}")
        if (toastTime > 0f) {
            toastTime -= dt
            if (toastTime <= 0f) toastBox.addAction(Actions.sequence(Actions.fadeOut(0.3f), Actions.visible(false)))
        }
        stage.act(dt)
    }

    private fun turnTo(angle: Float, dt: Float) {
        var d = angle - shipAngle
        while (d > MathUtils.PI) d -= MathUtils.PI2
        while (d < -MathUtils.PI) d += MathUtils.PI2
        shipAngle += MathUtils.clamp(d, -12f * dt, 12f * dt)
    }

    // ------------------------------------------------------------------ the map

    private fun drawMap() {
        val w = level.width.toFloat()
        val h = level.height.toFloat()
        // The holographic grid: faint lines and brighter points.
        for (x in 0..level.width) g.line(x.toFloat(), -0.35f, x.toFloat(), h + 0.35f, 0.05f, Palette.cyan, 0.13f)
        for (y in 0..level.height) g.line(-0.35f, y.toFloat(), w + 0.35f, y.toFloat(), 0.05f, Palette.cyan, 0.13f)
        for (x in 0..level.width) for (y in 0..level.height) g.dot(x.toFloat(), y.toFloat(), 0.035f, Palette.cyan, 0.5f, add = true)
        corners(w, h)

        // How far the fuel left can take the ship: a dashed circle around it.
        val reach = fuelShown
        if (reach > 0.05f && !flight.arrived && sailing == null) reachRing(shipAt.x, shipAt.y, reach, Palette.cyan, if (aim != null) 0.9f else 0.55f)

        // The road taken so far, and the displacement from the start.
        val drawn = if (sailing != null) shownPieces - 1 else shownPieces
        for (i in 0 until drawn.coerceAtMost(flight.pieces.size)) {
            val pc = flight.pieces[i]
            g.arrow(pc.from.x.toFloat(), pc.from.y.toFloat(), pc.to.x.toFloat(), pc.to.y.toFloat(), 0.16f, Palette.cyan, 0.75f, 0.32f)
        }
        sailing?.let { pc -> g.line(pc.from.x.toFloat(), pc.from.y.toFloat(), shipAt.x, shipAt.y, 0.16f, Palette.cyan, 0.75f) }
        if (drawn >= 2 || (drawn >= 1 && flight.arrived)) {
            val to = flight.pieces[drawn - 1].to
            g.dashed(level.start.x.toFloat(), level.start.y.toFloat(), to.x.toFloat(), to.y.toFloat(), 0.12f, Palette.magenta, 0.18f, 0.12f, 0.8f)
        }
        // The start pad.
        g.ring(level.start.x.toFloat(), level.start.y.toFloat(), 0.22f, Palette.cyan, 0.5f, time * 20f)

        val danger = aim?.asteroid
        for ((i, a) in level.asteroids.sortedWith(compareBy({ it.y }, { it.x })).withIndex()) {
            val d = if (a == danger) 0.6f + 0.4f * sin(time * 14f) else 0f
            Sprites.asteroid(g, a.x.toFloat(), a.y.toFloat(), i + level.number * 13, time, d)
        }
        val aimed = aim?.takeIf { it.ok }?.crystals ?: emptyList()
        for ((i, c) in level.crystals.withIndex()) {
            if (c in shownCrystals) continue
            if (c in aimed) g.glow(c.x.toFloat(), c.y.toFloat(), 0.9f, Palette.gold, 0.5f + 0.3f * sin(time * 10f))
            Sprites.crystal(g, c.x.toFloat(), c.y.toFloat(), time, i * 1.7f)
        }
        Sprites.portal(g, level.portal.x.toFloat(), level.portal.y.toFloat(), time)

        aim?.let { drawAim(it) }

        // The ship, shrinking into the portal once it has arrived.
        val gone = if (arrivedFor >= 0f) (1f - arrivedFor / 0.6f).coerceIn(0f, 1f) else 1f
        if (gone > 0f) {
            val spin = if (arrivedFor >= 0f) arrivedFor * 12f else 0f
            val thrust = if (sailing != null) 1f else if (aim != null) 0.45f else 0.25f + 0.1f * sin(time * 6f)
            val bob = if (sailing == null) sin(time * 2.4f) * 0.02f else 0f
            Sprites.ship(g, shipAt.x, shipAt.y + bob, shipAngle + spin, thrust, time, gone)
        }
        particles.draw(g)
    }

    /** Sci-fi corner brackets round the map. */
    private fun corners(w: Float, h: Float) {
        val m = 0.62f
        val l = 0.6f
        val c = Palette.cyan
        for ((x, y, dx, dy) in listOf(
            floatArrayOf(-m, -m, 1f, 1f), floatArrayOf(w + m, -m, -1f, 1f),
            floatArrayOf(-m, h + m, 1f, -1f), floatArrayOf(w + m, h + m, -1f, -1f),
        ).map { listOf(it[0], it[1], it[2], it[3]) }) {
            g.line(x, y, x + dx * l, y, 0.09f, c, 0.8f)
            g.line(x, y, x, y + dy * l, 0.09f, c, 0.8f)
        }
    }

    /** The reach: a ring of dots at [r] round the ship, kept inside the map. */
    private fun reachRing(x: Float, y: Float, r: Float, color: Color, alpha: Float) {
        val n = (r * MathUtils.PI2 / 0.22f).roundToInt().coerceIn(16, 160)
        val turn = time * 0.1f
        val m = 0.45f
        for (i in 0 until n) {
            val a = turn + i * MathUtils.PI2 / n
            val px = x + cos(a) * r
            val py = y + sin(a) * r
            if (px < -m || py < -m || px > level.width + m || py > level.height + m) continue
            g.dot(px, py, 0.035f, color, alpha, add = true)
        }
    }

    /**
     * The aimed move: the piece as a glowing arrow from the ship, its two components as the legs of
     * a right triangle, the wind from its tip, and where the ship would end up.
     */
    private fun drawAim(a: Aim) {
        if (a.thrust.isZero) return
        val ox = a.from.x.toFloat()
        val oy = a.from.y.toFloat()
        val tx = ox + a.thrust.x
        val ty = oy + a.thrust.y
        val color = if (a.ok) Palette.gold else Palette.red
        // Components: x along, then y up, dashed, with the right-angle mark.
        if (a.thrust.x != 0 && a.thrust.y != 0) {
            g.dashed(ox, oy, tx, oy, 0.07f, Palette.white, 0.14f, 0.1f, 0.55f, time * 0.6f)
            g.dashed(tx, oy, tx, ty, 0.07f, Palette.white, 0.14f, 0.1f, 0.55f, time * 0.6f)
            val sx = if (a.thrust.x > 0) -0.16f else 0.16f
            val sy = if (a.thrust.y > 0) 0.16f else -0.16f
            g.line(tx + sx, oy, tx + sx, oy + sy, 0.05f, Palette.white, 0.5f)
            g.line(tx + sx, oy + sy, tx, oy + sy, 0.05f, Palette.white, 0.5f)
        }
        g.arrow(ox, oy, tx, ty, 0.2f, color, 1f, 0.38f)
        val to = a.to
        if (!level.wind.isZero) {
            g.arrow(tx, ty, to.x.toFloat(), to.y.toFloat(), 0.16f, Palette.purple, 0.95f, 0.3f)
            // The resultant: where the ship really goes.
            g.arrow(ox, oy, to.x.toFloat(), to.y.toFloat(), 0.2f, if (a.ok) Palette.white else Palette.red, 0.95f, 0.36f)
        }
        g.ring(to.x.toFloat(), to.y.toFloat(), 0.28f, color, 0.9f, time * 120f)
        if (a.ok) Sprites.ship(g, to.x.toFloat(), to.y.toFloat(), angleOf(a.thrust), 0f, time, 0.85f, 0.35f)
    }

    // ------------------------------------------------------------------ text over the map

    private val screenPos = Vector3()
    private val stagePos = Vector2()

    /** World point to UI units; false if off screen. */
    private fun toStage(x: Float, y: Float): Boolean {
        world.project(screenPos.set(x, y, 0f))
        stagePos.set(screenPos.x, Gdx.graphics.height - screenPos.y)
        stage.screenToStageCoordinates(stagePos)
        return true
    }

    private fun text(font: BitmapFont, s: String, x: Float, y: Float, color: Color, alpha: Float = 1f, plate: Boolean = false) {
        app.layout.setText(font, s)
        val w = app.layout.width
        val h = app.layout.height
        if (plate) {
            val b = g.sprites(add = false)
            b.color = Color.WHITE
            ui.panel(Color(0.04f, 0.05f, 0.14f, 0.85f), color, 8f, 0f, 1.2f).draw(b, x - w / 2 - 8f, y - h / 2 - 6f, w + 16f, h + 12f)
        }
        val b = g.sprites(add = false)
        font.setColor(color.r, color.g, color.b, alpha)
        font.draw(b, s, x - w / 2, y + h / 2)
    }

    private fun drawLabels() {
        // Axis numbers.
        for (x in 0..level.width) if (toStage(x.toFloat(), -0.78f)) text(ui.tiny, "$x", stagePos.x, stagePos.y, Palette.dim, 0.8f)
        for (y in 0..level.height) if (toStage(-0.78f, y.toFloat())) text(ui.tiny, "$y", stagePos.x, stagePos.y, Palette.dim, 0.8f)
        val a = aim ?: return
        if (a.thrust.isZero || sailing != null) return
        val ox = a.from.x.toFloat()
        val oy = a.from.y.toFloat()
        val t = a.thrust
        val color = if (a.ok) Palette.gold else Palette.red
        // The components beside their legs, the magnitude beside the arrow.
        if (t.x != 0 && t.y != 0) {
            toStage(ox + t.x / 2f, oy - (if (t.y > 0) 0.38f else -0.38f))
            text(ui.small, "x: ${Strings.num(t.x)}", stagePos.x, stagePos.y, Palette.white, 0.9f)
            toStage(ox + t.x + (if (t.x > 0) 0.5f else -0.5f), oy + t.y / 2f)
            text(ui.small, "y: ${Strings.num(t.y)}", stagePos.x, stagePos.y, Palette.white, 0.9f)
        }
        val len = t.length.toFloat()
        val nx = -t.y / len * (if (t.x >= 0) 1f else -1f)
        val ny = t.x / len * (if (t.x >= 0) 1f else -1f)
        toStage(ox + t.x / 2f + nx * 0.55f, oy + t.y / 2f + ny * 0.55f)
        text(ui.small, "${Strings.vec(t)}  |${Strings.magnitude(t)}|", stagePos.x, stagePos.y, color, 1f, plate = true)
        if (!level.wind.isZero) {
            val tip = a.from + t
            toStage(tip.x + level.wind.x / 2f + 0.1f, tip.y + level.wind.y / 2f + 0.42f)
            text(ui.tiny, "${Strings.WIND} ${Strings.vec(level.wind)}", stagePos.x, stagePos.y, Palette.purple, 1f, plate = true)
            val d = a.delta
            val dl = d.length.toFloat().coerceAtLeast(1e-3f)
            toStage(ox + d.x / 2f - d.y / dl * 0.5f * (if (d.x >= 0) -1f else 1f), oy + d.y / 2f + d.x / dl * 0.5f * (if (d.x >= 0) -1f else 1f))
            text(ui.tiny, "${Strings.RESULTANT} ${Strings.vec(d)}", stagePos.x, stagePos.y, if (a.ok) Palette.white else Palette.red, 1f, plate = true)
        }
    }

    /** The fuel gauge under the top bar: five units, ticks at each, and what the aimed move would burn. */
    private fun drawFuel() {
        val sw = stage.viewport.worldWidth
        val sh = stage.viewport.worldHeight
        val x0 = 40f
        val x1 = sw - 40f
        val y = sh - 128f
        val unit = (x1 - x0) / level.fuel.toFloat()
        text(ui.small, Strings.FUEL, x0 + 24f, y + 30f, Palette.cyan, 0.9f)
        val value = Strings.dec(fuelShown.toDouble())
        app.layout.setText(ui.big, value)
        val b = g.sprites(add = false)
        ui.big.color = Palette.gold
        ui.big.draw(b, value, x1 - app.layout.width - 34f, y + 44f)
        text(ui.small, "/ 5", x1 - 14f, y + 30f, Palette.dim)
        aim?.takeIf { !it.thrust.isZero && sailing == null }?.let {
            text(ui.button, "−${Strings.dec(it.cost)}", x0 + 140f, y + 30f, if (it.ok) Palette.white else Palette.red)
        }
        // The tank.
        val s = g.shapes()
        s.setColor(0.05f, 0.06f, 0.16f, 0.9f)
        s.rect(x0, y - 10f, x1 - x0, 20f)
        val full = x0 + unit * fuelShown
        g.gradient(x0, y - 8f, full - x0, 16f, Palette.orange, Palette.gold)
        val a = aim
        if (a != null && !a.thrust.isZero && sailing == null) {
            val cost = a.cost.toFloat()
            val from = (full - unit * cost).coerceAtLeast(x0)
            val pulse = 0.55f + 0.45f * sin(time * 9f)
            val c = if (a.ok) Palette.white else Palette.red
            s.setColor(c.r, c.g, c.b, 0.75f * pulse)
            s.rect(from, y - 8f, full - from, 16f)
            if (!a.ok && cost > fuelShown) {
                s.setColor(Palette.red.r, Palette.red.g, Palette.red.b, 0.5f * pulse)
                s.rect(x0, y - 8f, x1 - x0, 16f)
            }
        }
        g.glow(full, y, 26f, Palette.gold, 0.5f)
        // A tick and a number at every unit: fuel is a scalar, measured on a line.
        for (i in 0..level.fuel.toInt()) {
            val tx = x0 + unit * i
            val sh2 = g.shapes()
            sh2.setColor(Palette.white.r, Palette.white.g, Palette.white.b, 0.6f)
            sh2.rect(tx - 1f, y - 14f, 2f, 28f)
            text(ui.tiny, "$i", tx, y - 26f, Palette.dim, 0.8f)
        }
    }

    // ------------------------------------------------------------------ HUD

    private fun changed(f: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = f()
    }

    private fun buildHud() {
        root.setFillParent(true)
        root.top()
        stage.addActor(root)
        val top = Table()
        val levels = ui.iconButton(ui.grid, Palette.cyan)
        levels.addListener(changed { app.menu() })
        top.add(levels).size(58f).left()
        val title = Table()
        title.add(titleLabel).row()
        title.add(chapterLabel)
        top.add(title).expandX()
        val gems = Table()
        gems.add(Image(ui.crystal)).size(26f).padRight(6f)
        gems.add(crystalLabel)
        top.add(Container(gems).apply { background = ui.panel(Color(0.12f, 0.08f, 0.02f, 0.85f), Palette.gold, 18f, 8f, 1.5f); pad(6f, 14f, 6f, 14f) }).height(52f).right()
        root.add(top).growX().pad(16f, 16f, 0f, 16f).row()
        root.add().height(110f).row()
        root.add(toastBox).width(480f).padTop(4f).row()
        root.add().expand().row()

        val bottom = Table()
        undoButton.addListener(changed { undo() })
        restartButton.addListener(changed { restart() })
        hintButton.addListener(changed { hint() })
        val hintWithBadge = Table()
        hintWithBadge.add(hintButton).size(66f)
        bottom.add(undoButton).size(66f).padRight(26f)
        bottom.add(restartButton).size(66f).padRight(26f)
        bottom.add(hintWithBadge)
        root.add(bottom).padBottom(26f)
        // The video badge on the hint button.
        stage.addActor(adBadge)
        stage.addActor(endPanel)
    }

    private fun say(text: String, seconds: Float) {
        toast.setText(text)
        toastBox.isVisible = true
        toastBox.clearActions()
        toastBox.color.a = 1f
        toastTime = seconds
    }

    private fun hideToast() {
        toastTime = 0f
        toastBox.isVisible = false
    }

    private fun finish() {
        finished = true
        aim = null
        val stars = flight.stars
        app.progress.record(levelNumber, stars)
        app.pacing.levelFinished()
        val card = endCard(Strings.ARRIVED, Palette.cyan)
        val row = Table()
        for (i in 1..3) {
            val on = i <= stars
            val img = Image(ui.star)
            img.color = if (on) Palette.gold else Color(Palette.faint).also { it.a = 0.6f }
            row.add(img).size(if (i == 2) 76f else 62f).pad(0f, 6f, 0f, 6f)
        }
        card.add(row).padTop(10f).row()
        card.add(ui.label("◆ ${flight.collected.size}/${level.crystals.size}", ui.button, Palette.gold)).padTop(6f).row()
        for (l in listOf(Strings.fuelLine(flight.fuelUsed), Strings.roadLine(flight.travelled), Strings.displacementLine(flight.displacement))) {
            card.add(ui.label(l, ui.body, Palette.white)).padTop(6f).row()
        }
        val note = if (flight.travelled > flight.displacement.length + 1e-6)
            "Yer değiştirme, alınan yoldan kısadır: çıkıştan varışa dümdüz giden vektördür."
        else "Dümdüz gittin: alınan yol, yer değiştirmenin büyüklüğüne eşit."
        card.add(ui.label(note, ui.tiny, Palette.dim).apply { setWrap(true); setAlignment(Align.center) }).width(400f).padTop(10f).row()
        val hasNext = levelNumber < Levels.all.size
        if (!hasNext) card.add(ui.label(Strings.ALL_DONE, ui.body, Palette.cyan).apply { setWrap(true); setAlignment(Align.center) }).width(400f).padTop(10f).row()
        val next = TextButton(if (hasNext) "${Strings.NEXT}  ▶" else Strings.LEVELS, ui.buttonStyle(Palette.cyan, ui.heading))
        next.addListener(changed { if (hasNext) app.next(levelNumber) else app.menu() })
        card.add(next).width(330f).height(68f).padTop(18f).row()
        val more = Table()
        val again = TextButton(Strings.AGAIN, ui.buttonStyle(Palette.magenta))
        again.addListener(changed { app.play(levelNumber) })
        val levels = TextButton(Strings.LEVELS, ui.buttonStyle(Palette.purple))
        levels.addListener(changed { app.menu() })
        more.add(again).width(160f).height(54f).padRight(10f)
        more.add(levels).width(160f).height(54f)
        card.add(more).padTop(12f)
        showEnd(card)
    }

    private fun showStranded() {
        val card = endCard(Strings.STRANDED, Palette.orange)
        card.add(ui.label(Strings.STRANDED_HINT, ui.body, Palette.white).apply { setWrap(true); setAlignment(Align.center) }).width(400f).padTop(12f).row()
        val row = Table()
        val back = TextButton("GERİ AL", ui.buttonStyle(Palette.cyan))
        back.addListener(changed { endPanel.isVisible = false; undo() })
        val again = TextButton("BAŞTAN", ui.buttonStyle(Palette.magenta))
        again.addListener(changed { endPanel.isVisible = false; restart() })
        row.add(back).width(160f).height(56f).padRight(10f)
        row.add(again).width(160f).height(56f)
        card.add(row).padTop(18f)
        showEnd(card)
    }

    private fun endCard(title: String, color: Color): Table {
        val card = Table().apply {
            background = ui.panel(Color(0.05f, 0.06f, 0.18f, 0.95f), color, 26f, 16f, 2f)
            pad(26f, 26f, 28f, 26f)
        }
        card.add(ui.label(title, ui.heading, color)).row()
        return card
    }

    private fun showEnd(card: Table) {
        hideToast()
        endPanel.clear()
        endPanel.isVisible = true
        endPanel.setFillParent(true)
        endPanel.background = ui.solid(Color(0.01f, 0.01f, 0.05f, 0.55f))
        endPanel.add(card)
        endPanel.color.a = 0f
        endPanel.addAction(Actions.fadeIn(0.35f))
        card.isTransform = true
        card.setOrigin(Align.center)
        card.setScale(0.85f)
        card.addAction(Actions.scaleTo(1f, 1f, 0.35f, Interpolation.swingOut))
    }

    // ------------------------------------------------------------------ autoplay (videos)

    private var autoTimer = 0f

    private fun autoplay(dt: Float) {
        if (!app.options.autoplay || busy) return
        autoTimer += dt
        if (aim == null && autoTimer > 1.2f) {
            autoTimer = 0f
            Solver.hint(flight)?.let { aim = flight.check(it); hintShowing = 10f }
        } else if (aim != null && autoTimer > 1.6f) {
            autoTimer = 0f
            val t = aim!!.thrust
            aim = null
            hintShowing = 0f
            fly(t)
        }
    }

    // ------------------------------------------------------------------ layout

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        val uiScale = stage.viewport.screenHeight / stage.viewport.worldHeight
        // Fit the map (and its axis numbers) between the HUD at the top and the buttons below.
        val m = 1.0f
        val mapW = level.width + m * 2
        val mapH = level.height + m * 2
        val top = height - topHud * uiScale
        val bottom = bottomHud * uiScale
        val ppu = minOf(width * 0.94f / mapW, (top - bottom) / mapH)
        world.viewportWidth = width / ppu
        world.viewportHeight = height / ppu
        val centreY = (top + bottom) / 2f
        world.position.set(level.width / 2f, level.height / 2f + (height / 2f - centreY) / ppu, 0f)
        world.update()
        root.validate()
        // Put the video badge on the hint button's corner.
        val p = hintButton.localToStageCoordinates(Vector2(hintButton.width - 12f, hintButton.height - 12f))
        adBadge.setBounds(p.x - 2f, p.y - 2f, 22f, 22f)
    }

    override fun dispose() {
        stage.dispose()
    }
}
