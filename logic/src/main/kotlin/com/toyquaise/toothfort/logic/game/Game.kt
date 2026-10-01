package com.toyquaise.toothfort.logic.game

import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.Dir
import com.toyquaise.toothfort.logic.board.Attack
import com.toyquaise.toothfort.logic.board.Board
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Role
import com.toyquaise.toothfort.logic.board.WireGauge
import kotlin.math.ceil

enum class Phase { BUILD, WAVE, WON, LOST }

enum class ActionResult { OK, NO_MONEY, BLOCKED, LOCKED, NOTHING }

class Enemy(val id: Int, val kind: EnemyKind) {
    var health = kind.health
        internal set
    var distance = 0.0
        internal set
    var alive = true
        internal set
}

/** A ball of paste in flight toward [target]; it lands where the target was if the target is gone. */
class Blob(val from: Part, var target: Enemy, var position: Vec2, val damage: Double, val splash: Double) {
    var aim: Vec2 = position
        internal set
}

sealed interface GameEvent {
    data class Hit(val enemy: Enemy, val by: Part) : GameEvent
    data class Shot(val by: Part) : GameEvent
    data class Splat(val at: Vec2, val radius: Double) : GameEvent
    data class Killed(val enemy: Enemy, val at: Vec2) : GameEvent
    data class Bit(val enemy: Enemy, val damage: Int) : GameEvent
    data class Wiring(val event: BoardEvent) : GameEvent
    data class WaveStarted(val number: Int) : GameEvent
    data class WaveCleared(val number: Int, val bonus: Int) : GameEvent
    data object Won : GameEvent
    data object Lost : GameEvent
}

/**
 * One level in play. Deterministic: the same actions and the same [step] sizes give the same game.
 * Time only runs during a wave; while building, the circuit is still solved so every voltage and
 * current can be read, but batteries do not drain and nothing heats.
 */
class Game(val level: Level) {
    val board = Board(level.width, level.height, level::terrain)
    val path: CandyPath get() = level.path

    var money = level.startMoney
        private set
    var toothHealth = level.toothHealth
        private set
    var phase = Phase.BUILD
        private set

    /** Index of the wave being played, or the next one while building. */
    var waveIndex = 0
        private set
    val waveCount: Int get() = level.waves.size

    val enemies = ArrayList<Enemy>()
    val blobs = ArrayList<Blob>()

    /** Lasers and the candy each is burning right now. */
    val beams = HashMap<Part, Enemy>()

    /** What happened since the UI last drained this list. */
    val events = ArrayList<GameEvent>()

    var time = 0.0
        private set
    private var waveTime = 0.0
    private var spawned = 0
    private var nextEnemyId = 1
    private val cooldown = HashMap<Part, Double>()

    // ------------------------------------------------------------------ building

    fun unlocked(kind: PartKind) = kind in level.parts
    fun unlocked(gauge: WireGauge) = gauge in level.gauges

    fun place(kind: PartKind, cell: Cell, facing: Dir = Dir.E): ActionResult {
        if (!unlocked(kind)) return ActionResult.LOCKED
        if (phase == Phase.WON || phase == Phase.LOST) return ActionResult.BLOCKED
        if (!board.canPlace(cell)) return ActionResult.BLOCKED
        if (money < kind.cost) return ActionResult.NO_MONEY
        board.place(kind, cell, facing) ?: return ActionResult.BLOCKED
        money -= kind.cost
        return ActionResult.OK
    }

    /** The money a part brings back: all of it while building, half during a wave, nothing once broken. */
    fun refundFor(part: Part): Int {
        if (part.broken) return 0
        val value = if (part.kind.role == Role.BATTERY) part.kind.cost * part.charge else part.kind.cost.toDouble()
        return (if (phase == Phase.BUILD) value else value / 2).toInt()
    }

    fun erase(cell: Cell): ActionResult {
        val part = board.partAt(cell) ?: return ActionResult.NOTHING
        money += refundFor(part)
        board.remove(cell)
        cooldown.remove(part)
        beams.remove(part)
        return ActionResult.OK
    }

    fun rotate(cell: Cell): ActionResult = if (board.rotate(cell)) ActionResult.OK else ActionResult.NOTHING

    fun toggle(cell: Cell): ActionResult = if (board.toggleSwitch(cell)) ActionResult.OK else ActionResult.NOTHING

    fun wire(from: Cell, to: Cell, gauge: WireGauge): ActionResult {
        if (!unlocked(gauge)) return ActionResult.LOCKED
        if (!board.canWire(from, to)) return ActionResult.BLOCKED
        if (money < gauge.cost) return ActionResult.NO_MONEY
        board.addWire(from, to, gauge) ?: return ActionResult.BLOCKED
        money -= gauge.cost
        return ActionResult.OK
    }

    fun unwire(from: Cell, to: Cell): ActionResult {
        val w = board.removeWire(from, to) ?: return ActionResult.NOTHING
        if (!w.melted) money += if (phase == Phase.BUILD) w.gauge.cost else w.gauge.cost / 2
        return ActionResult.OK
    }

    /** What a fresh battery costs in place of this one. */
    fun refillCost(part: Part): Int = maxOf(1, ceil(part.kind.cost * (1.0 - part.charge)).toInt())

    fun refill(cell: Cell): ActionResult {
        val part = board.partAt(cell) ?: return ActionResult.NOTHING
        if (part.kind.role != Role.BATTERY || part.charge >= 1.0) return ActionResult.NOTHING
        val cost = refillCost(part)
        if (money < cost) return ActionResult.NO_MONEY
        money -= cost
        board.refill(cell)
        return ActionResult.OK
    }

    // ------------------------------------------------------------------ waves

    fun startWave(): Boolean {
        if (phase != Phase.BUILD) return false
        phase = Phase.WAVE
        waveTime = 0.0
        spawned = 0
        events += GameEvent.WaveStarted(waveIndex + 1)
        return true
    }

    fun step(dt: Double) {
        when (phase) {
            Phase.BUILD -> board.solveIfChanged()
            Phase.WAVE -> runWave(dt)
            Phase.WON, Phase.LOST -> {}
        }
    }

    private fun runWave(dt: Double) {
        time += dt
        waveTime += dt
        val wave = level.waves[waveIndex]

        while (spawned < wave.schedule.size && wave.schedule[spawned].first <= waveTime) {
            enemies += Enemy(nextEnemyId++, wave.schedule[spawned].second)
            spawned++
        }

        board.update(dt)
        board.events.forEach { events += GameEvent.Wiring(it) }
        board.events.clear()

        runMachines(dt)
        moveBlobs(dt)

        for (e in enemies) {
            if (!e.alive) continue
            e.distance += e.kind.speed * dt
            if (e.distance >= path.length) {
                e.alive = false
                toothHealth = maxOf(0, toothHealth - e.kind.bite)
                events += GameEvent.Bit(e, e.kind.bite)
            }
        }
        enemies.removeAll { !it.alive }

        if (toothHealth <= 0) {
            phase = Phase.LOST
            events += GameEvent.Lost
            return
        }
        if (spawned == wave.schedule.size && enemies.isEmpty()) {
            val bonus = 10 + 5 * waveIndex
            money += bonus
            events += GameEvent.WaveCleared(waveIndex + 1, bonus)
            blobs.clear()
            beams.clear()
            if (waveIndex == level.waves.lastIndex) {
                phase = Phase.WON
                events += GameEvent.Won
            } else {
                waveIndex++
                phase = Phase.BUILD
            }
        }
    }

    /** The candy furthest along the road within [range] cells of [cell]. */
    private fun target(cell: Cell, range: Double): Enemy? {
        val c = Vec2.center(cell)
        return enemies.filter { it.alive && path.at(it.distance).distanceTo(c) <= range }.maxByOrNull { it.distance }
    }

    private fun runMachines(dt: Double) {
        beams.clear()
        for (p in board.allParts) {
            val spec = p.kind.machine ?: continue
            if (p.broken || p.performance <= 0.0) continue
            val left = (cooldown[p] ?: 0.0) - dt
            cooldown[p] = maxOf(left, 0.0)
            val t = target(p.cell, spec.range) ?: continue
            when (spec.attack) {
                Attack.SCRUB -> if (left <= 0.0) {
                    damage(t, spec.damage, p)
                    cooldown[p] = spec.interval / p.performance
                }
                Attack.BLOB -> if (left <= 0.0) {
                    blobs += Blob(p, t, Vec2.center(p.cell), spec.damage, spec.splash)
                    events += GameEvent.Shot(p)
                    cooldown[p] = spec.interval / p.performance
                }
                Attack.BEAM -> {
                    beams[p] = t
                    damage(t, spec.damage * p.performance * dt / spec.interval, p)
                }
            }
        }
    }

    private fun moveBlobs(dt: Double) {
        val speed = 7.0
        val landed = ArrayList<Blob>()
        for (b in blobs) {
            if (b.target.alive) b.aim = path.at(b.target.distance)
            val to = b.aim - b.position
            val dist = to.length()
            val stepLen = speed * dt
            if (dist <= stepLen) {
                b.position = b.aim
                landed += b
            } else {
                b.position = b.position + to * (stepLen / dist)
            }
        }
        for (b in landed) {
            if (b.target.alive) damage(b.target, b.damage, b.from)
            for (e in enemies) {
                if (e !== b.target && e.alive && path.at(e.distance).distanceTo(b.position) <= b.splash) damage(e, b.damage / 2, b.from)
            }
            events += GameEvent.Splat(b.position, b.splash)
        }
        blobs.removeAll(landed.toSet())
    }

    private fun damage(e: Enemy, amount: Double, by: Part) {
        if (!e.alive) return
        e.health -= amount
        events += GameEvent.Hit(e, by)
        if (e.health <= 0) {
            e.alive = false
            money += e.kind.reward
            events += GameEvent.Killed(e, path.at(e.distance))
        }
    }
}
