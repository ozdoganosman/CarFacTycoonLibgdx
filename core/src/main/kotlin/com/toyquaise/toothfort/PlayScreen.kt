package com.toyquaise.toothfort

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.toyquaise.toothfort.logic.Geometry
import com.toyquaise.toothfort.logic.Vec2
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Port
import com.toyquaise.toothfort.logic.board.Role
import com.toyquaise.toothfort.logic.game.ActionResult
import com.toyquaise.toothfort.logic.game.Game
import com.toyquaise.toothfort.logic.game.GameEvent
import com.toyquaise.toothfort.logic.game.Levels
import com.toyquaise.toothfort.logic.game.Phase
import com.toyquaise.toothfort.render.Kitchen
import com.toyquaise.toothfort.render.WorldView
import com.toyquaise.toothfort.ui.Hud
import com.toyquaise.toothfort.ui.HudListener
import com.toyquaise.toothfort.ui.Strings
import com.toyquaise.toothfort.ui.Tool
import kotlin.math.abs
import kotlin.math.tan

/** One level: the kitchen counter in 3D, the HUD around it, and building with the fingers. */
class PlayScreen(private val app: ToothFortGame, val levelIndex: Int) : ScreenAdapter(), HudListener {
    val game = Game(Levels.all[levelIndex])
    private val view = WorldView(app.models, game)
    private val camera = PerspectiveCamera(30f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    private val stage = Stage(ExtendViewport(UI_WIDTH, UI_HEIGHT), app.batch)
    private val hud = Hud(app.kit, stage, this)
    private val shapes = ShapeRenderer()
    private val focus = Vector3()
    private var accumulator = 0.0
    private var ended = false

    /** The angle each kind of part was last placed at, so a row of them comes out alike. */
    private val lastAngle = HashMap<PartKind, Double>()

    private val boardInput = object : InputAdapter() {
        private var pointer = -1
        private var start: Vec2? = null
        private var moved = false
        private var wireFrom: Port? = null
        private val wirePath = ArrayList<Vec2>()

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (this.pointer != -1) return false
            val p = pick(screenX, screenY) ?: return false
            this.pointer = pointer
            start = p
            moved = false
            val part = game.board.partAt(p)
            when (val t = hud.tool) {
                is Tool.Place -> if (part != null) press(part) else {
                    val pl = game.board.placementFor(p, lastAngle[t.kind] ?: 0.0)
                    view.ghost = WorldView.Ghost(t.kind, pl.pos, pl.angle, pl.ok)
                }
                is Tool.Wiring -> {
                    val port = game.board.portAt(p, PORT_REACH)
                    if (port != null) {
                        wireFrom = port
                        wirePath.clear()
                        wirePath += game.board.position(port)
                        view.wirePreview = ArrayList(wirePath)
                        view.wirePreviewGauge = t.gauge
                    } else if (part != null) press(part)
                }
                else -> if (part != null) press(part)
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (pointer != this.pointer) return false
            val p = pick(screenX, screenY) ?: return true
            if (start?.let { it.distanceTo(p) > 0.12 } == true) moved = true
            view.ghost?.let { g ->
                val pl = game.board.placementFor(p, lastAngle[g.kind] ?: 0.0)
                g.pos = pl.pos
                g.angle = pl.angle
                g.ok = pl.ok
            }
            if (wireFrom != null && wirePath.last().distanceTo(p) > 0.12) {
                wirePath += p
                view.wirePreview = ArrayList(wirePath)
            }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer != this.pointer) return false
            this.pointer = -1
            val p = pick(screenX, screenY) ?: start ?: return true
            view.pressed = null
            val g = view.ghost
            if (g != null) {
                view.ghost = null
                place(g.kind, p, lastAngle[g.kind] ?: 0.0)
                return true
            }
            val from = wireFrom
            if (from != null) {
                wireFrom = null
                view.wirePreview = null
                finishWire(from, ArrayList(wirePath), p)
                return true
            }
            if (!moved) tap(p)
            return true
        }

        override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            this.pointer = -1
            view.pressed = null
            view.ghost = null
            view.wirePreview = null
            wireFrom = null
            return true
        }
    }

    init {
        Gdx.input.inputProcessor = InputMultiplexer(stage, boardInput)
        hud.bind(game)
    }

    // ------------------------------------------------------------------ touches

    private val ray = Vector3()

    /** The point on the counter under a screen point, or null off the counter. */
    fun pick(screenX: Int, screenY: Int): Vec2? {
        val r = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        if (abs(r.direction.y) < 1e-4f) return null
        val t = (0.05f - r.origin.y) / r.direction.y
        if (t < 0) return null
        ray.set(r.origin).mulAdd(r.direction, t)
        val p = Vec2(ray.x.toDouble(), ray.z.toDouble())
        return if (game.board.inside(p, -0.3)) p else null
    }

    private fun press(part: Part) {
        view.pressed = part
        select(part)
    }

    private fun place(kind: PartKind, at: Vec2, angle: Double) {
        when (game.place(kind, at, angle)) {
            ActionResult.OK -> {
                val part = game.board.parts.last()
                lastAngle[kind] = part.angle
                select(part)
                if (!rotateHintShown) { hud.showToast(Strings.TAP_TO_ROTATE); rotateHintShown = true }
            }
            ActionResult.NO_MONEY -> hud.showToast(Strings.NO_MONEY)
            ActionResult.BLOCKED -> hud.showToast(Strings.BLOCKED_PART)
            ActionResult.LOCKED -> hud.showToast(Strings.LOCKED)
            ActionResult.NOTHING -> {}
        }
    }

    /**
     * The finger drew a wire from [from] through [path] and let go at [end]: connect it to the
     * terminal or clip there, or leave a new clip where it stopped.
     */
    private fun finishWire(from: Port, path: List<Vec2>, end: Vec2) {
        val gauge = (hud.tool as? Tool.Wiring)?.gauge ?: return
        val start = game.board.position(from)
        val total = Geometry.length(path + listOf(end))
        val target = game.board.portAt(end, PORT_REACH)?.takeIf { it != from }
        if (target == null && total < 0.35) return
        val to = target ?: game.addJunction(end)?.let { Port.Clip(it) } ?: return
        val stop = game.board.position(to)
        // The bends: what the finger drew, minus the bits right at the two ends.
        val via = path.drop(1).filter { it.distanceTo(start) > 0.12 && it.distanceTo(stop) > 0.12 }
        when (game.wire(from, to, via, gauge)) {
            ActionResult.OK -> {}
            ActionResult.NO_MONEY -> {
                hud.showToast(Strings.NO_MONEY)
                if (to is Port.Clip && game.board.wiresAt(to).isEmpty()) game.eraseJunction(to.junction)
            }
            else -> hud.showToast(Strings.START_WIRE)
        }
    }

    private fun tap(p: Vec2) {
        val part = game.board.partAt(p)
        when (hud.tool) {
            Tool.Erase -> {
                if (part != null) {
                    if (hud.selected === part) select(null)
                    game.erase(part)
                    return
                }
                val clip = game.board.junctions.minByOrNull { it.pos.distanceTo(p) }?.takeIf { it.pos.distanceTo(p) < PORT_REACH }
                if (clip != null) { game.eraseJunction(clip); return }
                game.board.wireAt(p, 0.2)?.let { game.unwire(it) }
            }
            is Tool.Wiring -> if (part == null) hud.showToast(Strings.START_WIRE)
            else -> if (part == null) select(null)
        }
    }

    private var rotateHintShown = false

    /** Selects [p] as a tap would (also used by the demo). */
    fun select(p: Part?) {
        hud.select(p)
        view.selected = p
    }

    // ------------------------------------------------------------------ HUD actions

    override fun startWave() {
        game.startWave()
    }

    override fun rotate(part: Part) {
        game.rotate(part)
        lastAngle[part.kind] = part.angle
        view.poke(part)
    }

    override fun sell(part: Part) {
        select(null)
        game.erase(part)
    }

    override fun refill(part: Part) {
        if (game.refill(part) == ActionResult.NO_MONEY) hud.showToast(Strings.NO_MONEY) else view.poke(part)
    }

    override fun toggle(part: Part) {
        game.toggle(part)
        view.poke(part)
    }

    override fun nextLevel() = app.play(levelIndex + 1)

    override fun retry() = app.play(levelIndex)

    // ------------------------------------------------------------------ frame

    private val script = ArrayDeque(app.options.script?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList())
    private var scriptWait = 0

    /** Plays one step of the scripted touches (see [Options.script]). */
    private fun runScript() {
        if (scriptWait > 0) { scriptWait--; return }
        val step = script.removeFirstOrNull() ?: return
        val words = step.split(" ").filter { it.isNotEmpty() }
        fun at(): Pair<Int, Int> {
            screen.set(words[1].toFloat(), 0.05f, words[2].toFloat())
            camera.project(screen)
            return screen.x.toInt() to (Gdx.graphics.height - screen.y).toInt()
        }
        when (words[0]) {
            "tool" -> hud.pickTool(
                when (words[1]) {
                    "battery" -> Tool.Place(PartKind.BATTERY)
                    "brush" -> Tool.Place(PartKind.BRUSH)
                    "cannon" -> Tool.Place(PartKind.PASTE_CANNON)
                    "laser" -> Tool.Place(PartKind.LASER)
                    "switch" -> Tool.Place(PartKind.SWITCH)
                    "wire" -> Tool.Wiring(com.toyquaise.toothfort.logic.board.WireGauge.THIN)
                    "thick" -> Tool.Wiring(com.toyquaise.toothfort.logic.board.WireGauge.THICK)
                    else -> Tool.Erase
                },
            )
            "down" -> at().let { (x, y) -> boardInput.touchDown(x, y, 0, 0) }
            "move" -> at().let { (x, y) -> boardInput.touchDragged(x, y, 0) }
            "up" -> at().let { (x, y) -> boardInput.touchUp(x, y, 0, 0) }
            "wait" -> scriptWait = words[1].toInt()
            "start" -> game.startWave()
            "shot" -> app.shotNow = true
        }
    }

    override fun render(delta: Float) {
        runScript()
        val dt = minOf(delta, 0.1f)
        accumulator += dt * hud.speed
        while (accumulator >= STEP) {
            game.step(STEP)
            accumulator -= STEP
        }
        if (game.phase == Phase.BUILD) accumulator = 0.0
        for (e in game.events) {
            view.onEvent(e)
            react(e)
        }
        game.events.clear()
        view.update(dt)
        if (hud.selected != null && hud.selected !in game.board.parts) select(null)

        Gdx.gl.glClearColor(Palette.table.r, Palette.table.g, Palette.table.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        app.renderer.begin(camera, focus, 8f)
        view.draw(app.renderer)
        app.renderer.end()

        hud.update(dt)
        if (app.options.hideHud) return
        drawOverlay()
        stage.draw()
    }

    private fun react(e: GameEvent) {
        when (e) {
            is GameEvent.WaveCleared -> hud.showToast("${Strings.WAVE} ${e.number} bitti! +${e.bonus}")
            GameEvent.Won -> if (!ended) { ended = true; hud.showEnd(true, levelIndex + 1 < Levels.all.size) }
            GameEvent.Lost -> if (!ended) { ended = true; hud.showEnd(false, false) }
            is GameEvent.Wiring -> when (val b = e.event) {
                is BoardEvent.Burnt -> hud.showToast("${Strings.part(b.part.kind).replace('\n', ' ')} yandı: gerilim fazla geldi!")
                is BoardEvent.Melted -> hud.showToast("Kablo eridi: içinden çok fazla akım geçti!")
                is BoardEvent.Blown -> hud.showToast("Sigorta attı: devreyi korudu.")
                is BoardEvent.Depleted -> hud.showToast("Bir pil bitti. Seçip yenile ya da paralel pil ekle.")
            }
            else -> {}
        }
    }

    private val screen = Vector3()
    private val stagePos = Vector2()

    /** Readings above machines, health bars over candies, and floating numbers, in HUD space. */
    private fun drawOverlay() {
        val uiCam = stage.camera
        uiCam.update()
        shapes.projectionMatrix = uiCam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        for (e in game.enemies) {
            if (e.health >= e.kind.health) continue
            val at = game.path.at(e.distance)
            if (!toStage(at.x.toFloat(), 0.95f, at.y.toFloat())) continue
            val w = 28f
            val f = (e.health / e.kind.health).toFloat().coerceIn(0f, 1f)
            shapes.color = Palette.ink
            shapes.rect(stagePos.x - w / 2 - 1.5f, stagePos.y - 1.5f, w + 3f, 6f)
            shapes.color = if (f > 0.5f) Palette.meterOk else Palette.coral
            shapes.rect(stagePos.x - w / 2, stagePos.y, w * f, 3f)
        }
        shapes.end()

        val batch = app.batch
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        if (hud.meters) {
            // A small dark tag over every machine with the voltage it really gets.
            for (p in game.board.parts) {
                if (p.kind.role != Role.MACHINE) continue
                val text = if (p.broken) "yandı" else Strings.volts(abs(p.volts))
                val color = when {
                    p.broken -> Palette.coral
                    p.performance == 0.0 -> Palette.steel
                    p.voltageRatio > 1.1 -> Palette.coral
                    p.voltageRatio < 0.85 -> Palette.yellow
                    else -> Palette.meterOk
                }
                Kitchen.world(p.pos, 1.15f, screen)
                if (!toStage(screen.x, screen.y, screen.z)) continue
                val font = app.kit.small
                app.layout.setText(font, text)
                val w = app.layout.width + 12f
                val h = app.layout.height + 9f
                app.kit.slab(Palette.ink, 9, 0).draw(batch, stagePos.x - w / 2, stagePos.y, w, h)
                font.color = color
                font.draw(batch, text, stagePos.x - app.layout.width / 2, stagePos.y + h - 4.5f)
            }
        }
        for (f in view.floaters) {
            if (!toStage(f.pos.x, f.pos.y, f.pos.z)) continue
            val font = app.kit.heading
            font.color = f.color.cpy().also { it.a = (1.1f - f.age).coerceIn(0f, 1f) }
            app.layout.setText(font, f.text)
            font.draw(batch, f.text, stagePos.x - app.layout.width / 2, stagePos.y)
        }
        app.kit.small.color = Color.WHITE
        app.kit.heading.color = Color.WHITE
        batch.end()
    }

    /** Projects a world point into HUD coordinates ([stagePos]); false if it is behind the camera. */
    private fun toStage(x: Float, y: Float, z: Float): Boolean {
        screen.set(x, y, z)
        camera.project(screen)
        if (screen.z !in 0f..1f) return false
        stagePos.set(screen.x, Gdx.graphics.height - screen.y)
        stage.screenToStageCoordinates(stagePos)
        return true
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        fitCamera(width, height)
    }

    /**
     * Frames the counter between the HUD's top bar and toolbox: a fixed tilt, then the distance at
     * which the counter and the tooth just fit, resting on the toolbox. The wall shows above.
     */
    private fun fitCamera(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.near = 0.5f
        camera.far = 120f
        val uiScale = stage.viewport.screenHeight / stage.viewport.worldHeight
        val top = height - TOP_BAR * uiScale
        val bottom = BOTTOM_BAR * uiScale
        val w = game.level.width.toFloat()
        val h = game.level.height.toFloat()
        val tooth = game.level.tooth
        val points = listOf(
            Vector3(-0.15f, 0f, -0.15f), Vector3(w + 0.15f, 0f, -0.15f),
            Vector3(-0.15f, 0f, h + 0.15f), Vector3(w + 0.15f, 0f, h + 0.15f),
            Vector3(tooth.x.toFloat(), 1.9f, tooth.y.toFloat()),
        )
        val pitch = 50f * MathUtils.degreesToRadians
        val dir = Vector3(0f, -MathUtils.sin(pitch), -MathUtils.cos(pitch))
        focus.set(w / 2, 0f, h / 2)
        val target = Vector3(focus)
        val p = Vector3()
        fun place(d: Float) {
            camera.position.set(target).mulAdd(dir, -d)
            camera.direction.set(dir)
            camera.up.set(Vector3.Y)
            camera.normalizeUp()
            camera.update()
        }
        fun bounds(): FloatArray {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (q in points) {
                camera.project(p.set(q))
                minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x); minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
            }
            return floatArrayOf(minX, maxX, minY, maxY)
        }
        val margin = width * 0.012f
        var d = 20f
        repeat(4) {
            var lo = 4f
            var hi = 80f
            repeat(28) {
                val mid = (lo + hi) / 2
                place(mid)
                val b = bounds()
                if (b[0] >= margin && b[1] <= width - margin && b[2] >= bottom && b[3] <= top) hi = mid else lo = mid
            }
            d = hi
            place(d)
            // Slide the camera so the counter sits just above the toolbox; the room left at the top
            // shows the wall, and is where the part card and the notes appear.
            val b = bounds()
            val shift = (bottom + height * 0.01f) - b[2]
            val worldPerPixel = 2f * d * tan(camera.fieldOfView / 2 * MathUtils.degreesToRadians) / height
            target.mulAdd(Vector3(camera.up), -shift * worldPerPixel)
        }
        place(d)
        app.options.closeUp?.let { (x, y) ->
            camera.position.set(x + 0.0f, 2.1f, y + 2.1f)
            camera.lookAt(x.toFloat(), 0.3f, y.toFloat())
            camera.up.set(Vector3.Y)
            camera.normalizeUp()
            camera.update()
        }
    }

    override fun dispose() {
        stage.dispose()
        shapes.dispose()
        view.dispose()
    }

    companion object {
        const val STEP = 1.0 / 60.0
        const val UI_WIDTH = 540f
        const val UI_HEIGHT = 960f
        const val TOP_BAR = 84f
        const val BOTTOM_BAR = 162f

        /** How close a finger must come to a terminal or clip to grab it. */
        const val PORT_REACH = 0.3
    }
}
