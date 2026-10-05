package com.toyquaise.kaptan.logic

/** A vector card in the player's hand. [id] stays the same while the card is held. */
data class Card(val id: Int, val vec: Vec)

/** A card chosen for a move, with the number it is multiplied by. */
data class Pick(val card: Int, val factor: Factor = Factor.ONE)

/**
 * One move of the boat: a single card, played tip to tail after the previous move, or two cards
 * pulled together by two tugboats at once (the parallelogram), which sail the diagonal.
 */
data class Play(val picks: List<Pick>) {
    constructor(vararg picks: Pick) : this(picks.toList())

    val together: Boolean get() = picks.size == 2
}

/** A finished move: where it went, which multiplied vectors made it, and the current that was added. */
class Leg(
    val from: Vec,
    val to: Vec,
    val cards: List<Card>,
    val factors: List<Factor>,
    /** The cards' vectors after multiplying, in the order they were picked. */
    val vectors: List<Vec>,
    val current: Vec,
) {
    val delta: Vec get() = to - from
    val length: Double get() = delta.length
    val together: Boolean get() = vectors.size == 2
}

/** Why a move or a split cannot be made. */
enum class Problem {
    ARRIVED, NO_CARD, SAME_CARD, TOO_MANY, NO_DOUBLE, NO_HALF, NO_FLIP, NO_PAIR, ODD_HALF,
    NO_MOVE, OFF_SEA, ROCK, NO_SPLIT, CANT_SPLIT,
}

/**
 * What a move would do, worked out before it is made, for the preview arrows: the multiplied
 * card vectors, the current, the resulting step and where it ends. [problem] is null if it can be
 * made; [rock] is the rock it would hit.
 */
class Check(
    val problem: Problem?,
    val vectors: List<Vec> = emptyList(),
    val current: Vec = Vec.ZERO,
    val from: Vec = Vec.ZERO,
    val rock: Vec? = null,
) {
    val delta: Vec get() = vectors.fold(current) { a, v -> a + v }
    val to: Vec get() = from + delta
    val ok: Boolean get() = problem == null
}

/**
 * A level being played: the boat's position, the cards and tokens left, the moves made so far
 * (each one can be taken back), and whether the boat has reached the harbor.
 */
class Voyage(val level: Level) {
    var position: Vec = level.start
        private set
    private val cards = ArrayList<Card>()
    val hand: List<Card> get() = cards
    var tokens: Tokens = level.tokens
        private set
    private val moves = ArrayList<Leg>()
    val legs: List<Leg> get() = moves
    private var nextId = 0

    private class Snapshot(val position: Vec, val cards: List<Card>, val tokens: Tokens, val legs: Int)
    private val history = ArrayList<Snapshot>()

    /** Bumped on every change, so views know when to look again. */
    var revision = 0
        private set

    init {
        reset()
    }

    val arrived: Boolean get() = position == level.harbor

    /** Number of moves made (splitting a card is not a move). */
    val plays: Int get() = moves.size

    /** Distance sailed, the sum of the moves' lengths ("alınan yol"). */
    val travelled: Double get() = moves.sumOf { it.length }

    /** From the start to where the boat is now ("yer değiştirme"): the resultant of every move. */
    val displacement: Vec get() = position - level.start

    /** What is still left to the harbor. */
    val remaining: Vec get() = level.harbor - position

    val canUndo: Boolean get() = history.isNotEmpty()

    fun card(id: Int): Card? = cards.firstOrNull { it.id == id }

    fun reset() {
        position = level.start
        cards.clear()
        level.cards.forEach { cards += Card(nextId++, it) }
        tokens = level.tokens
        moves.clear()
        history.clear()
        revision++
    }

    fun check(play: Play): Check {
        if (arrived) return Check(Problem.ARRIVED)
        if (play.picks.isEmpty()) return Check(Problem.NO_CARD)
        if (play.picks.size > 2) return Check(Problem.TOO_MANY)
        if (play.together && play.picks[0].card == play.picks[1].card) return Check(Problem.SAME_CARD)
        val vectors = ArrayList<Vec>()
        for (p in play.picks) {
            val c = card(p.card) ?: return Check(Problem.NO_CARD)
            vectors += p.factor.applyTo(c.vec) ?: return Check(Problem.ODD_HALF)
        }
        val partial = Check(null, vectors, level.current, position)
        if (tokens.spend(play.picks.map { it.factor }, play.together) == null) {
            val f = play.picks.map { it.factor }
            val problem = when {
                play.together && tokens.pairs < 1 -> Problem.NO_PAIR
                f.count { it.flip } > tokens.flips -> Problem.NO_FLIP
                f.count { it.stretch == Stretch.DOUBLE } > tokens.doubles -> Problem.NO_DOUBLE
                else -> Problem.NO_HALF
            }
            return Check(problem, vectors, level.current, position)
        }
        if (partial.delta.isZero) return Check(Problem.NO_MOVE, vectors, level.current, position)
        if (!Sea.inside(level, partial.to)) return Check(Problem.OFF_SEA, vectors, level.current, position)
        val rock = Sea.rockOnWay(level, position, partial.to)
        if (rock != null) return Check(Problem.ROCK, vectors, level.current, position, rock)
        return partial
    }

    /** Makes the move, or says why it cannot be made. */
    fun play(play: Play): Problem? {
        val check = check(play)
        if (!check.ok) return check.problem
        save()
        val played = play.picks.map { card(it.card)!! }
        cards.removeAll { c -> played.any { it.id == c.id } }
        tokens = tokens.spend(play.picks.map { it.factor }, play.together)!!
        moves += Leg(position, check.to, played, play.picks.map { it.factor }, check.vectors, level.current)
        position = check.to
        revision++
        return null
    }

    /** Replaces a card by its two components, in place (FİZ.9.2.4). */
    fun split(id: Int): Problem? {
        if (arrived) return Problem.ARRIVED
        val i = cards.indexOfFirst { it.id == id }
        if (i < 0) return Problem.NO_CARD
        if (tokens.splits < 1) return Problem.NO_SPLIT
        if (cards[i].vec.isAxial) return Problem.CANT_SPLIT
        save()
        val (cx, cy) = cards[i].vec.components
        cards.removeAt(i)
        cards.add(i, Card(nextId++, cy))
        cards.add(i, Card(nextId++, cx))
        tokens = tokens.copy(splits = tokens.splits - 1)
        revision++
        return null
    }

    /** Takes back the last move or split. */
    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        position = s.position
        cards.clear()
        cards += s.cards
        tokens = s.tokens
        while (moves.size > s.legs) moves.removeAt(moves.lastIndex)
        revision++
        return true
    }

    private fun save() {
        history += Snapshot(position, ArrayList(cards), tokens, moves.size)
    }

    /** Stars for a finished voyage: 1 for arriving, 2 within one move of the best, 3 for the best. */
    fun stars(best: Solver.Solution?): Int {
        if (!arrived) return 0
        if (best == null) return 3
        return when {
            plays <= best.plays && travelled <= best.length + 1e-6 -> 3
            plays <= best.plays + 1 -> 2
            else -> 1
        }
    }
}
