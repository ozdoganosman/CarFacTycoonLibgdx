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
import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.Role
import com.toyquaise.toothfort.logic.game.ActionResult
import com.toyquaise.toothfort.logic.game.Game
import com.toyquaise.toothfort.logic.game.GameEvent
import com.toyquaise.toothfort.logic.game.Levels
import com.toyquaise.toothfort.logic.game.Phase
import com.toyquaise.toothfort.render.BoardSpace
import com.toyquaise.toothfort.render.WorldView
import com.toyquaise.toothfort.ui.Hud
import com.toyquaise.toothfort.ui.HudListener
import com.toyquaise.toothfort.ui.Strings
import com.toyquaise.toothfort.ui.Tool
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.tan

/** One level: the clay board in 3D, the HUD around it, and touch building. */
class PlayScreen(private val app: ToothFortGame, val levelIndex: Int) : ScreenAdapter(), HudListener {
    val game = Game(Levels.all[levelIndex])
    private val view = WorldView(app.models, game)
    private val camera = PerspectiveCamera(34f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    private val stage = Stage(ExtendViewport(UI_WIDTH, UI_HEIGHT), app.batch)
    private val hud = Hud(app.kit, stage, this)
    private val shapes = ShapeRenderer()
    private val focus = Vector3()
    private var accumulator = 0.0
    private var ended = false

    private val boardInput = object : InputAdapter() {
        private var pointer = -1
        private var last: Cell? = null
        private var dragged = false
        private var warned = false

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (this.pointer != -1) return false
            val cell = pick(screenX, screenY) ?: return false
            this.pointer = pointer
            last = cell
            dragged = false
            warned = false
            view.hover = cell
            view.hoverOk = true
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (pointer != this.pointer) return false
            val cell = pick(screenX, screenY) ?: return true
            var from = last ?: return true
            if (cell == from) return true
            dragged = true
            // Walk one neighbouring cell at a time, even if the finger skipped some.
            while (from != cell) {
                val next = if (from.x != cell.x) Cell(from.x + if (cell.x > from.x) 1 else -1, from.y)
                else Cell(from.x, from.y + if (cell.y > from.y) 1 else -1)
                stroke(from, next)
                from = next
            }
            last = cell
            view.hover = cell
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer != this.pointer) return false
            this.pointer = -1
            if (!dragged) last?.let(::tap)
            view.hover = null
            return true
        }

        override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
            view.hover = pick(screenX, screenY)
            view.hoverOk = view.hover?.let { canUse(it) } ?: true
            return false
        }

        private fun stroke(from: Cell, to: Cell) {
            when (val t = hud.tool) {
                is Tool.Wiring -> {
                    if (game.board.wireBetween(from, to) != null) return
                    when (game.wire(from, to, t.gauge)) {
                        ActionResult.OK -> {}
                        ActionResult.NO_MONEY -> warnOnce(Strings.NO_MONEY)
                        ActionResult.BLOCKED -> warnOnce(Strings.BLOCKED_WIRE)
                        ActionResult.LOCKED -> warnOnce(Strings.LOCKED)
                        ActionResult.NOTHING -> {}
                    }
                }
                Tool.Erase -> game.unwire(from, to)
                else -> {}
            }
        }

        private fun warnOnce(text: String) {
            if (!warned) hud.showToast(text)
            warned = true
        }
    }

    init {
        Gdx.input.inputProcessor = InputMultiplexer(stage, boardInput)
        hud.bind(game)
    }

    // ------------------------------------------------------------------ touches

    private val ray = Vector3()

    /** The board cell under a screen point, or null off the board. */
    fun pick(screenX: Int, screenY: Int): Cell? {
        val r = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        if (abs(r.direction.y) < 1e-4f) return null
        val t = (0.05f - r.origin.y) / r.direction.y
        if (t < 0) return null
        ray.set(r.origin).mulAdd(r.direction, t)
        val c = Cell(floor(ray.x).toInt(), floor(ray.z).toInt())
        return if (game.board.inside(c)) c else null
    }

    private fun canUse(c: Cell): Boolean = when (val t = hud.tool) {
        is Tool.Place -> game.board.canPlace(c) || game.board.partAt(c)?.kind == t.kind
        else -> true
    }

    private fun tap(cell: Cell) {
        val part = game.board.partAt(cell)
        when (val t = hud.tool) {
            is Tool.Place -> when {
                part != null && part.kind == t.kind && hud.selected === part -> rotate(part)
                part != null -> select(part)
                else -> when (game.place(t.kind, cell)) {
                    ActionResult.OK -> {
                        select(game.board.partAt(cell))
                        if (!rotateHintShown) { hud.showToast(Strings.TAP_TO_ROTATE); rotateHintShown = true }
                    }
                    ActionResult.NO_MONEY -> hud.showToast(Strings.NO_MONEY)
                    ActionResult.BLOCKED -> hud.showToast(Strings.BLOCKED_PART)
                    ActionResult.LOCKED -> hud.showToast(Strings.LOCKED)
                    ActionResult.NOTHING -> {}
                }
            }
            Tool.Erase -> if (part != null) {
                if (hud.selected === part) select(null)
                game.erase(cell)
            } else {
                for (w in game.board.wiresAt(cell).toList()) game.unwire(w.a, w.b)
            }
            else -> select(part)
        }
    }

    private var rotateHintShown = false

    /** Selects the part on [cell], as a tap would (also used by the demo). */
    fun selectAt(cell: Cell) = select(game.board.partAt(cell))

    private fun select(p: Part?) {
        hud.select(p)
        view.selected = p?.cell
    }

    // ------------------------------------------------------------------ HUD actions

    override fun startWave() {
        game.startWave()
    }

    override fun rotate(part: Part) {
        game.rotate(part.cell)
    }

    override fun sell(part: Part) {
        select(null)
        game.erase(part.cell)
    }

    override fun refill(part: Part) {
        if (game.refill(part.cell) == ActionResult.NO_MONEY) hud.showToast(Strings.NO_MONEY)
    }

    override fun toggle(part: Part) {
        game.toggle(part.cell)
    }

    override fun nextLevel() = app.play(levelIndex + 1)

    override fun retry() = app.play(levelIndex)

    // ------------------------------------------------------------------ frame

    override fun render(delta: Float) {
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
        if (hud.selected != null && game.board.partAt(hud.selected!!.cell) !== hud.selected) select(null)

        Gdx.gl.glClearColor(Palette.table.r, Palette.table.g, Palette.table.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        app.renderer.begin(camera, focus, 9.5f)
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

    /** Readings above parts, health bars over candies, and floating numbers, in HUD space. */
    private fun drawOverlay() {
        val uiCam = stage.camera
        uiCam.update()
        shapes.projectionMatrix = uiCam.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        for (e in game.enemies) {
            if (e.health >= e.kind.health) continue
            val at = game.path.at(e.distance)
            if (!toStage(at.x.toFloat(), 0.85f, at.y.toFloat())) continue
            val w = 34f
            val f = (e.health / e.kind.health).toFloat().coerceIn(0f, 1f)
            shapes.color = Palette.ink
            shapes.rect(stagePos.x - w / 2 - 2, stagePos.y - 2, w + 4, 8f)
            shapes.color = if (f > 0.5f) Palette.turquoise else Palette.coral
            shapes.rect(stagePos.x - w / 2, stagePos.y, w * f, 4f)
        }
        shapes.end()

        val batch = app.batch
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        if (hud.meters) {
            for (p in game.board.allParts) {
                val text: String
                val color: Color
                when (p.kind.role) {
                    Role.MACHINE -> {
                        if (p.broken) { text = "yandı"; color = Palette.brick }
                        else {
                            text = Strings.volts(abs(p.volts))
                            color = when {
                                p.performance == 0.0 -> Palette.inkSoft
                                p.voltageRatio > 1.1 -> Palette.brick
                                p.voltageRatio < 0.85 -> Palette.coral
                                else -> Palette.teal
                            }
                        }
                    }
                    Role.BATTERY -> { text = Strings.percent(p.charge); color = if (p.charge > 0.3) Palette.teal else Palette.brick }
                    else -> continue
                }
                BoardSpace.center(p.cell, 0.95f, screen)
                if (!toStage(screen.x, screen.y, screen.z)) continue
                val font = app.kit.small
                app.layout.setText(font, text)
                val w = app.layout.width + 14f
                val h = app.layout.height + 12f
                app.kit.slab(Palette.surface, 10, 2).draw(batch, stagePos.x - w / 2, stagePos.y - 2, w, h + 2)
                font.color = color
                font.draw(batch, text, stagePos.x - app.layout.width / 2, stagePos.y + h - 5)
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
     * Frames the board between the HUD's top bar and toolbox: a fixed tilt, then the distance
     * at which the board, the tooth and the candy jar just fit, then centred in that band.
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
            Vector3(-0.35f, 0f, -1.0f), Vector3(w + 0.35f, 0f, -1.0f),
            Vector3(-0.35f, 0f, h + 0.35f), Vector3(w + 0.35f, 0f, h + 0.35f),
            Vector3(tooth.x + 0.5f, 1.5f, tooth.y + 0.5f), Vector3(w / 2, 0.7f, -0.8f),
        )
        val pitch = 56f * MathUtils.degreesToRadians
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
        val margin = width * 0.025f
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
            // Slide the camera so the board sits in the middle of the free band.
            val b = bounds()
            val shift = (top + bottom) / 2 - (b[2] + b[3]) / 2
            val worldPerPixel = 2f * d * tan(camera.fieldOfView / 2 * MathUtils.degreesToRadians) / height
            val up = Vector3(camera.up)
            target.mulAdd(up, -shift * worldPerPixel)
        }
        place(d)
        app.options.closeUp?.let { (x, y) ->
            camera.position.set(x + 0.5f, 2.1f, y + 2.6f)
            camera.lookAt(x + 0.5f, 0.3f, y + 0.5f)
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
        const val BOTTOM_BAR = 186f
    }
}
