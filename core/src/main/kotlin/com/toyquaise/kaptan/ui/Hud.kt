package com.toyquaise.kaptan.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.toyquaise.kaptan.Palette
import com.toyquaise.kaptan.logic.Factor
import com.toyquaise.kaptan.logic.Pick
import com.toyquaise.kaptan.logic.Play
import com.toyquaise.kaptan.logic.Stretch
import com.toyquaise.kaptan.logic.Voyage

interface HudListener {
    /** The planned move changed (null: nothing chosen). */
    fun planChanged(play: Play?)
    fun go(play: Play)
    fun split(card: Int)
    fun undo()
    fun reset()
    fun hint()
    fun levels()
    fun next()
    fun again()
}

/**
 * The screen around the sea: the level and a help button on top, and below it what is left to
 * the harbor, the cards, the tokens, and the buttons to sail, undo, start over and get a hint.
 * Choosing cards and tokens plans a move; "Yola çık" makes it.
 */
class Hud(private val kit: UiKit, val stage: Stage, private val listener: HudListener) {
    lateinit var voyage: Voyage
        private set

    private val picks = ArrayList<Pick>()

    /** Two cards pulled together by two tugboats (the parallelogram) instead of one. */
    var together = false
        private set

    /** No input while the boat is sailing or the level is over. */
    var locked = false

    private val titleLabel = kit.label("", kit.button)
    private val movesLabel = kit.label("", kit.button)
    private val toast = kit.label("", kit.body).apply { setWrap(true); setAlignment(Align.center) }
    private val toastBox = Container(toast).apply {
        background = kit.slab(Palette.surface, 16, 4)
        pad(10f, 16f, 14f, 16f)
        fill()
        isVisible = false
        touchable = Touchable.disabled
    }
    private var toastTime = 0f

    private val remainingLabel = kit.label("", kit.bodyStrong)
    private val currentLabel = kit.label("", kit.bodyStrong, Palette.grape)
    private val cards = Table()
    private val tokens = Table()
    private val bottom = Table()
    private val goButton = TextButton(Strings.GO, kit.buttonStyle(Palette.coral, Palette.surface))
    private val undoButton = TextButton(Strings.UNDO, kit.buttonStyle(Palette.cream, font = kit.tool))
    private val resetButton = TextButton(Strings.RESET, kit.buttonStyle(Palette.cream, font = kit.tool))
    private val hintButton = Button(Button.ButtonStyle().apply {
        up = kit.slab(Palette.yellow)
        down = kit.slab(Palette.yellow.cpy().mul(0.9f, 0.9f, 0.9f, 1f))
        disabled = kit.slab(Palette.mintLine)
    })

    private val endCard = Table().apply { isVisible = false }

    /** Height of the panel at the bottom, in UI units, for framing the sea above it. */
    val bottomHeight: Float get() = bottom.prefHeight

    private var shownRevision = -1

    init {
        val root = Table().apply { setFillParent(true); top() }
        stage.addActor(root)

        val top = Table()
        val levelsButton = TextButton(Strings.LEVELS, kit.buttonStyle(Palette.surface, font = kit.tool))
        levelsButton.addListener(changed { listener.levels() })
        val helpButton = TextButton("?", kit.buttonStyle(Palette.surface))
        helpButton.addListener(changed { showToast(Strings.tip(voyage.level.number), 9f) })
        top.add(levelsButton).height(52f).left()
        top.add(pill(titleLabel, Palette.surface)).expandX()
        top.add(pill(movesLabel, Palette.surface)).padRight(8f)
        top.add(helpButton).width(52f).height(52f).right()
        root.add(top).growX().pad(12f, 12f, 4f, 12f).row()
        root.add(toastBox).width(500f).padTop(4f).row()
        root.add().expand().row()

        bottom.background = kit.slab(Palette.surface, 26, 0)
        root.add(bottom).growX()

        hintButton.add(kit.label(Strings.HINT, kit.tool)).padRight(6f)
        hintButton.add(Image(kit.adBadge)).size(20f)
        hintButton.pad(4f, 12f, 8f, 12f)

        goButton.addListener(changed { if (!locked) plan()?.let(listener::go) ?: showToast(if (picks.isEmpty()) Strings.PICK_FIRST else Strings.PICK_SECOND) })
        undoButton.addListener(changed { if (!locked) { clearPlan(); listener.undo() } })
        resetButton.addListener(changed { if (!locked) { clearPlan(); listener.reset() } })
        hintButton.addListener(changed { if (!locked) listener.hint() })

        stage.addActor(endCard)
    }

    private fun pill(label: Label, color: Color) = Container(label).apply {
        background = kit.slab(color, 20, 4)
        pad(6f, 14f, 10f, 14f)
    }

    private fun changed(f: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent, actor: Actor) = f()
    }

    /** Builds the panel for a voyage. */
    fun bind(voyage: Voyage) {
        this.voyage = voyage
        picks.clear()
        together = false
        endCard.isVisible = false
        val level = voyage.level
        titleLabel.setText("${Strings.LEVEL} ${level.number}")

        bottom.clear()
        val readout = Table()
        readout.add(remainingLabel).expandX().left()
        if (!level.current.isZero) readout.add(currentLabel).right()
        currentLabel.setText("${Strings.CURRENT} ${Strings.vec(level.current)}")
        bottom.add(readout).growX().pad(12f, 16f, 2f, 16f).row()
        bottom.add(cards).growX().height(82f).pad(2f, 8f, 2f, 8f).row()
        if (level.tokens.any) bottom.add(tokens).growX().height(50f).pad(2f, 8f, 2f, 8f).row()
        val actions = Table()
        actions.add(undoButton).height(54f).padRight(6f)
        actions.add(resetButton).height(54f).padRight(6f)
        actions.add(hintButton).height(54f).padRight(6f)
        actions.add(goButton).growX().height(54f)
        bottom.add(actions).growX().pad(4f, 10f, 14f, 10f)
        shownRevision = -1
        refresh()
        val tip = Strings.tip(level.number)
        if (tip.isNotEmpty()) showToast(tip, 10f)
    }

    // ------------------------------------------------------------------ planning a move

    /** The planned move, or null if it is not complete. */
    fun plan(): Play? = when {
        picks.isEmpty() -> null
        together && picks.size < 2 -> null
        else -> Play(picks.toList())
    }

    fun clearPlan() {
        picks.clear()
        changedPlan()
    }

    /** Chooses [play] as if its cards and tokens had been tapped (used for hints). */
    fun choose(play: Play) {
        picks.clear()
        picks += play.picks
        together = play.together
        changedPlan()
    }

    /** Chooses one card on its own (used for the split hint). */
    fun chooseCard(id: Int) {
        together = false
        picks.clear()
        picks += Pick(id)
        changedPlan()
    }

    private fun tapCard(id: Int) {
        if (locked) return
        val i = picks.indexOfFirst { it.card == id }
        when {
            i >= 0 -> picks.removeAt(i)
            together && picks.size < 2 -> picks += Pick(id)
            together -> picks[1] = Pick(id)
            else -> { picks.clear(); picks += Pick(id) }
        }
        changedPlan()
    }

    /** Changes the factor of the card chosen last. */
    private fun tapFactor(change: (Factor) -> Factor) {
        if (locked) return
        if (picks.isEmpty()) { showToast(Strings.PICK_FIRST); return }
        val last = picks.last()
        picks[picks.lastIndex] = last.copy(factor = change(last.factor))
        changedPlan()
    }

    private fun tapSplit() {
        if (locked) return
        val only = picks.singleOrNull()
        if (only == null) { showToast(Strings.PICK_FIRST); return }
        picks.clear()
        changedPlan()
        listener.split(only.card)
    }

    private fun tapTogether() {
        if (locked) return
        if (!together && voyage.tokens.pairs < 1) { showToast(Strings.problem(com.toyquaise.kaptan.logic.Problem.NO_PAIR)); return }
        together = !together
        if (!together) while (picks.size > 1) picks.removeAt(0)
        if (together) showToast(Strings.TOGETHER_ON, 4f)
        changedPlan()
    }

    private fun changedPlan() {
        // Cards that are gone (split, played) cannot stay chosen.
        picks.removeAll { voyage.card(it.card) == null }
        shownRevision = -1
        refresh()
        listener.planChanged(if (picks.isEmpty()) null else Play(picks.toList()))
    }

    // ------------------------------------------------------------------ panel

    private fun refresh() {
        if (shownRevision == voyage.revision) return
        shownRevision = voyage.revision
        picks.removeAll { voyage.card(it.card) == null }
        remainingLabel.setText("${Strings.REMAINING} ${Strings.vec(voyage.remaining)}")
        movesLabel.setText("${Strings.MOVES} ${voyage.plays}")
        undoButton.isDisabled = !voyage.canUndo
        resetButton.isDisabled = !voyage.canUndo

        cards.clear()
        for (card in voyage.hand) {
            val pick = picks.firstOrNull { it.card == card.id }
            val color = if (pick != null) Palette.yellow else Palette.cream
            val b = Button(Button.ButtonStyle().apply {
                up = kit.slab(color, 16, 5)
                down = kit.slab(color.cpy().mul(0.9f, 0.9f, 0.9f, 1f), 16, 5)
            })
            b.add(Image(kit.arrowIcon(card.vec)).apply { setScaling(Scaling.fit) }).size(34f).row()
            b.add(kit.label(Strings.vec(card.vec), kit.small)).row()
            if (pick != null && !pick.factor.isOne) b.add(kit.label(Strings.factor(pick.factor), kit.small, Palette.brick))
            b.pad(4f, 6f, 8f, 6f)
            b.addListener(changed { tapCard(card.id) })
            cards.add(b).minWidth(72f).height(78f).padRight(6f)
        }

        tokens.clear()
        val t = voyage.tokens
        val lt = voyage.level.tokens
        val last = picks.lastOrNull()?.factor
        if (lt.doubles > 0) token(Strings.DOUBLE, t.doubles, last?.stretch == Stretch.DOUBLE) { tapFactor { f -> f.copy(stretch = if (f.stretch == Stretch.DOUBLE) Stretch.NONE else Stretch.DOUBLE) } }
        if (lt.halves > 0) token(Strings.HALF, t.halves, last?.stretch == Stretch.HALF) { tapFactor { f -> f.copy(stretch = if (f.stretch == Stretch.HALF) Stretch.NONE else Stretch.HALF) } }
        if (lt.flips > 0) token(Strings.FLIP, t.flips, last?.flip == true) { tapFactor { f -> f.copy(flip = !f.flip) } }
        if (lt.splits > 0) token(Strings.SPLIT, t.splits, false) { tapSplit() }
        if (lt.pairs > 0) token(Strings.TOGETHER, t.pairs, together) { tapTogether() }
    }

    private fun token(text: String, left: Int, on: Boolean, f: () -> Unit) {
        val color = if (on) Palette.grape else Palette.cream
        val ink = if (on) Palette.surface else Palette.ink
        val b = TextButton("$text · $left", kit.buttonStyle(color, ink, kit.tool))
        b.isDisabled = left == 0 && !on
        b.addListener(changed { if (!b.isDisabled) f() })
        tokens.add(b).height(46f).padRight(6f)
    }

    fun showToast(text: String, seconds: Float = 3f) {
        toast.setText(text)
        toastBox.isVisible = true
        toastBox.clearActions()
        toastBox.color.a = 1f
        toastTime = seconds
    }

    fun hideToast() {
        toastTime = 0f
        toastBox.isVisible = false
    }

    // ------------------------------------------------------------------ the end

    fun showEnd(stars: Int, lines: List<String>, note: String, hasNext: Boolean) {
        hideToast()
        endCard.clear()
        endCard.isVisible = true
        endCard.setFillParent(true)
        endCard.background = kit.solid(Palette.ink.cpy().apply { a = 0.35f })
        val card = Table().apply { background = kit.slab(Palette.surface, 26, 6); pad(24f, 24f, 28f, 24f) }
        card.add(kit.label(Strings.ARRIVED, kit.title, Palette.teal)).row()
        val row = Table()
        for (i in 1..3) row.add(Image(kit.star(if (i <= stars) Palette.yellow else Palette.mintLine))).size(if (i == 2) 70f else 58f).pad(0f, 4f, 0f, 4f)
        card.add(row).padTop(8f).row()
        for (l in lines) card.add(kit.label(l, kit.bodyStrong).apply { setWrap(true); setAlignment(Align.center) }).width(420f).padTop(6f).row()
        card.add(kit.label(note, kit.small, Palette.inkSoft).apply { setWrap(true); setAlignment(Align.center) }).width(420f).padTop(8f).row()
        if (!hasNext) card.add(kit.label(Strings.ALL_DONE, kit.body).apply { setWrap(true); setAlignment(Align.center) }).width(420f).padTop(10f).row()
        val next = TextButton(if (hasNext) Strings.NEXT else Strings.LEVELS, kit.buttonStyle(Palette.turquoise, Palette.surface))
        next.addListener(changed { if (hasNext) listener.next() else listener.levels() })
        card.add(next).width(320f).height(64f).padTop(16f).row()
        val more = Table()
        val again = TextButton(Strings.AGAIN, kit.buttonStyle(Palette.cream, font = kit.tool))
        again.addListener(changed { listener.again() })
        more.add(again).height(50f).padRight(8f)
        if (hasNext) {
            val levels = TextButton(Strings.LEVELS, kit.buttonStyle(Palette.cream, font = kit.tool))
            levels.addListener(changed { listener.levels() })
            more.add(levels).height(50f)
        }
        card.add(more).padTop(10f)
        endCard.add(card)
        endCard.color.a = 0f
        endCard.addAction(Actions.fadeIn(0.4f))
    }

    val ended: Boolean get() = endCard.isVisible

    fun update(dt: Float) {
        refresh()
        goButton.isDisabled = locked || plan() == null
        hintButton.isDisabled = locked || voyage.arrived
        if (toastTime > 0) {
            toastTime -= dt
            if (toastTime <= 0) toastBox.addAction(Actions.sequence(Actions.fadeOut(0.3f), Actions.visible(false)))
        }
        stage.act(dt)
    }
}
