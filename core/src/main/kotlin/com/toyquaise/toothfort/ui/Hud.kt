package com.toyquaise.toothfort.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.toyquaise.toothfort.Palette
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Role
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.Game
import com.toyquaise.toothfort.logic.game.Phase

/** What a touch on the board does. */
sealed interface Tool {
    data object Select : Tool
    data class Place(val kind: PartKind) : Tool
    data class Wiring(val gauge: WireGauge) : Tool
    data object Erase : Tool
}

interface HudListener {
    fun startWave()
    fun rotate(part: Part)
    fun sell(part: Part)
    fun refill(part: Part)
    fun toggle(part: Part)
    fun nextLevel()
    fun retry()
}

/**
 * The screen around the board: tooth, wave and money on top, the toolbox and the start button
 * at the bottom, a card for the selected part, and short notes.
 */
class Hud(private val kit: UiKit, val stage: Stage, private val listener: HudListener) {
    lateinit var game: Game
        private set

    var tool: Tool = Tool.Select
        private set
    var meters = true
        private set
    var speed = 1
        private set
    var selected: Part? = null
        private set

    private val toothLabel = kit.label("", kit.heading)
    private val waveLabel = kit.label("", kit.heading)
    private val moneyLabel = kit.label("", kit.heading)
    private val toast = kit.label("", kit.body).apply { setWrap(true); setAlignment(Align.center) }
    private val toastBox = Container(toast).apply {
        background = kit.slab(Palette.surface, 16, 4)
        pad(10f, 16f, 14f, 16f)
        fill()
        isVisible = false
        touchable = Touchable.disabled
    }
    private var toastTime = 0f

    private val infoTitle = kit.label("", kit.button)
    private val infoText = kit.label("", kit.body).apply { setWrap(true) }
    private val infoButtons = Table()
    private val info = Table().apply {
        background = kit.slab(Palette.surface, 18, 5)
        pad(8f, 14f, 12f, 14f)
        isVisible = false
    }

    private val tools = Table()
    private val toolGroup = ButtonGroup<Button>().apply { setMinCheckCount(0); setMaxCheckCount(1) }
    private val toolButtons = HashMap<Tool, Button>()
    private val startButton = TextButton(Strings.START_WAVE, kit.buttonStyle(Palette.coral, Palette.surface))
    private val speedButton = TextButton("1×", kit.buttonStyle(Palette.cream))
    private val metersButton = TextButton(Strings.METERS, kit.buttonStyle(Palette.cream)).apply { isChecked = true }

    private val endCard = Table().apply { isVisible = false }

    init {
        val root = Table().apply { setFillParent(true); top() }
        stage.addActor(root)

        val top = Table()
        top.add(pill(toothLabel, Palette.enamel)).expandX().left()
        top.add(pill(waveLabel, Palette.surface)).expandX()
        top.add(pill(moneyLabel, Palette.yellow)).expandX().right()
        root.add(top).growX().pad(12f, 12f, 4f, 12f).row()
        root.add(toastBox).width(500f).padTop(4f).row()
        root.add().expand().row()
        root.add(info).growX().pad(0f, 10f, 6f, 10f).row()

        val bottom = Table().apply { background = kit.slab(Palette.tray, 22, 0) }
        val scroll = ScrollPane(tools).apply { setScrollingDisabled(false, true); setOverscroll(true, false); fadeScrollBars = false }
        bottom.add(scroll).growX().height(86f).pad(10f, 8f, 4f, 8f).row()
        val actions = Table()
        actions.add(metersButton).height(58f).padRight(8f)
        actions.add(speedButton).width(70f).height(58f).padRight(8f)
        actions.add(startButton).growX().height(58f)
        bottom.add(actions).growX().pad(4f, 10f, 14f, 10f)
        root.add(bottom).growX()

        stage.addActor(endCard)

        startButton.addListener(changed { listener.startWave() })
        speedButton.addListener(changed {
            speed = if (speed >= 3) 1 else speed + 1
            speedButton.setText("$speed×")
        })
        metersButton.addListener(changed { meters = metersButton.isChecked })
    }

    private fun pill(label: Label, color: Color) = Container(label).apply {
        background = kit.slab(color, 20, 4)
        pad(6f, 16f, 10f, 16f)
    }

    private fun changed(f: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = f()
    }

    /** Rebuilds the toolbox for a new level. */
    fun bind(game: Game) {
        this.game = game
        tool = Tool.Select
        selected = null
        info.isVisible = false
        endCard.isVisible = false
        tools.clear()
        toolGroup.clear()
        toolButtons.clear()
        for (kind in PartKind.entries) if (game.unlocked(kind)) addTool(Tool.Place(kind), Strings.part(kind), "${kind.cost}")
        for (g in WireGauge.entries) if (game.unlocked(g)) addTool(Tool.Wiring(g), Strings.wire(g), "${g.cost}/kare")
        addTool(Tool.Erase, Strings.ERASE, "")
        val hint = Strings.hint(game.level.number)
        if (hint.isNotEmpty()) showToast(hint, 9f)
    }

    private fun addTool(t: Tool, name: String, cost: String) {
        val style = Button.ButtonStyle().apply {
            up = kit.slab(Palette.surface, 16, 5)
            down = kit.slab(Palette.cream, 16, 5)
            checked = kit.slab(Palette.yellow, 16, 5)
        }
        val b = Button(style)
        b.add(kit.label(name, kit.tool)).row()
        if (cost.isNotEmpty()) b.add(kit.label(cost, kit.small, Palette.inkSoft))
        b.pad(4f, 9f, 8f, 9f)
        b.addListener(changed { tool = if (b.isChecked) t else Tool.Select })
        toolGroup.add(b)
        toolButtons[t] = b
        tools.add(b).height(70f).minWidth(70f).padRight(5f)
    }

    fun showToast(text: String, seconds: Float = 2.5f) {
        toast.setText(text)
        toastBox.isVisible = true
        toastBox.clearActions()
        toastBox.color.a = 1f
        toastTime = seconds
    }

    fun select(part: Part?) {
        selected = part
        refreshInfo()
    }

    private var infoKey = ""

    private fun refreshInfo() {
        val p = selected
        if (p == null || game.board.partAt(p.cell) !== p) {
            selected = null
            info.isVisible = false
            infoKey = ""
            return
        }
        info.isVisible = true
        val lines = ArrayList<String>()
        when (p.kind.role) {
            Role.BATTERY -> {
                lines += "Dolu: ${Strings.percent(p.charge)} · Uçlarında ${Strings.volts(p.volts)}"
                lines += if (p.amps >= 0) "Verdiği akım: ${Strings.amps(p.amps)}" else "Dolan akım: ${Strings.amps(-p.amps)}"
            }
            Role.MACHINE -> {
                val spec = p.kind.machine!!
                if (p.broken) lines += "Yandı: gerilim fazla geldi. Satıp yenisini koy."
                else {
                    lines += "Gerilim: ${Strings.volts(kotlin.math.abs(p.volts))} (${Strings.percent(p.voltageRatio)} / ${Strings.volts(spec.volts)})"
                    lines += "Akım: ${Strings.amps(kotlin.math.abs(p.amps))} · Güç: ${Strings.percent(p.performance)}"
                    when {
                        p.voltageRatio > 1.1 -> lines += "Fazla gerilim! Isınıyor: ${Strings.percent(p.heat)}"
                        p.performance == 0.0 -> lines += "Çalışmıyor: devre kapalı mı, gerilim yetiyor mu?"
                        p.voltageRatio < 0.85 -> lines += "Gerilim az: daha çok pil ya da daha kısa/kalın kablo."
                    }
                }
            }
            Role.SWITCH -> lines += if (p.closed) "Kapalı: akım geçiyor." else "Açık: devre kesik."
            Role.FUSE -> lines += if (p.broken) "Attı: devrede kısa devre ya da aşırı akım vardı." else "Akım: ${Strings.amps(kotlin.math.abs(p.amps))}"
            Role.RESISTOR -> lines += "Üstünde ${Strings.volts(kotlin.math.abs(p.volts))} · ${Strings.amps(kotlin.math.abs(p.amps))}"
        }
        infoTitle.setText(Strings.partLong(p.kind))
        infoText.setText(lines.joinToString("\n"))
        val refill = if (p.kind.role == Role.BATTERY && p.charge < 1.0) game.refillCost(p) else 0
        val key = "${System.identityHashCode(p)}:${p.closed}:${p.broken}:$refill:${game.refundFor(p)}:${game.phase}"
        if (key == infoKey) return
        infoKey = key
        info.clearChildren()
        info.add(infoTitle).left().row()
        info.add(infoText).growX().left().padTop(2f).row()
        infoButtons.clear()
        fun button(text: String, color: Color, f: () -> Unit) {
            val b = TextButton(text, kit.buttonStyle(color, font = kit.tool))
            b.addListener(changed(f))
            infoButtons.add(b).height(42f).padRight(8f)
        }
        button(Strings.ROTATE, Palette.cream) { listener.rotate(p) }
        if (p.kind.role == Role.SWITCH) button(if (p.closed) Strings.TOGGLE_ON else Strings.TOGGLE_OFF, Palette.turquoise) { listener.toggle(p) }
        if (refill > 0) button("${Strings.REFILL} $refill", Palette.turquoise) { listener.refill(p) }
        button("${Strings.SELL} +${game.refundFor(p)}", Palette.coral) { listener.sell(p) }
        info.add(infoButtons).left().padTop(6f)
    }

    fun showEnd(won: Boolean, hasNext: Boolean) {
        endCard.clear()
        endCard.isVisible = true
        endCard.setFillParent(true)
        val card = Table().apply { background = kit.slab(Palette.surface, 26, 6); pad(26f, 26f, 30f, 26f) }
        card.add(kit.label(if (won) Strings.WON else Strings.LOST, kit.title, if (won) Palette.teal else Palette.brick)).row()
        card.add(kit.label("${Strings.LEVEL} ${game.level.number}", kit.bodyStrong, Palette.inkSoft)).padTop(6f).row()
        if (won && !hasNext) card.add(kit.label(Strings.ALL_DONE, kit.body).apply { setWrap(true); setAlignment(Align.center) }).width(380f).padTop(10f).row()
        val b = if (won && hasNext) TextButton(Strings.NEXT_LEVEL, kit.buttonStyle(Palette.turquoise, Palette.surface))
        else TextButton(Strings.RETRY, kit.buttonStyle(Palette.coral, Palette.surface))
        b.addListener(changed { if (won && hasNext) listener.nextLevel() else listener.retry() })
        card.add(b).width(300f).height(64f).padTop(18f)
        endCard.add(card)
        endCard.color.a = 0f
        endCard.addAction(Actions.fadeIn(0.4f))
    }

    fun update(dt: Float) {
        toothLabel.setText("${Strings.TOOTH} ${game.toothHealth}")
        waveLabel.setText("${Strings.WAVE} ${minOf(game.waveIndex + 1, game.waveCount)}/${game.waveCount}")
        moneyLabel.setText("${game.money}")
        startButton.isDisabled = game.phase != Phase.BUILD
        startButton.setText(if (game.phase == Phase.WAVE) Strings.WAVE_RUNNING else Strings.START_WAVE)
        if (toastTime > 0) {
            toastTime -= dt
            if (toastTime <= 0) toastBox.addAction(Actions.sequence(Actions.fadeOut(0.3f), Actions.visible(false)))
        }
        refreshInfo()
        stage.act(dt)
    }
}
