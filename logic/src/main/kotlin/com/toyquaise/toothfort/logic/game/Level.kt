package com.toyquaise.toothfort.logic.game

import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Terrain
import com.toyquaise.toothfort.logic.board.WireGauge
import kotlin.math.hypot
import kotlin.math.sign

/** A point on the board in cell units; the centre of cell (x, y) is (x + 0.5, y + 0.5). */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    fun length() = hypot(x, y)
    fun distanceTo(o: Vec2) = hypot(x - o.x, y - o.y)

    companion object {
        fun center(c: Cell) = Vec2(c.x + 0.5, c.y + 0.5)
    }
}

/**
 * The road the candies walk, through straight runs between [waypoints] (each run along a row or
 * a column). They enter one cell before the first waypoint and stop on the last one, the tooth.
 */
class CandyPath(val waypoints: List<Cell>) {
    /** Every cell the road covers, in walking order, the tooth's cell last. */
    val cells: List<Cell>
    private val points: List<Vec2>
    private val cumulative: DoubleArray
    val length: Double

    init {
        require(waypoints.size >= 2)
        val list = ArrayList<Cell>()
        list += waypoints[0]
        for (i in 1 until waypoints.size) {
            val a = waypoints[i - 1]
            val b = waypoints[i]
            require(a.x == b.x || a.y == b.y) { "path runs must be straight: $a -> $b" }
            val dx = (b.x - a.x).sign
            val dy = (b.y - a.y).sign
            var c = a
            while (c != b) {
                c = Cell(c.x + dx, c.y + dy)
                list += c
            }
        }
        cells = list
        val first = waypoints[0]
        val second = cells[1]
        val entry = Cell(first.x - (second.x - first.x), first.y - (second.y - first.y))
        points = listOf(Vec2.center(entry)) + waypoints.map(Vec2::center)
        cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + points[i].distanceTo(points[i - 1])
        length = cumulative.last()
    }

    /** The point [distance] cells along the road. */
    fun at(distance: Double): Vec2 {
        val d = distance.coerceIn(0.0, length)
        var i = 1
        while (i < points.size - 1 && cumulative[i] < d) i++
        val t = (d - cumulative[i - 1]) / (cumulative[i] - cumulative[i - 1])
        return points[i - 1] + (points[i] - points[i - 1]) * t
    }

    /** The walking direction at [distance] (a unit vector along the current run). */
    fun heading(distance: Double): Vec2 {
        val a = at(distance)
        val b = at(minOf(length, distance + 0.05))
        val d = b - a
        val len = d.length()
        return if (len < 1e-9) Vec2(0.0, 1.0) else d * (1.0 / len)
    }
}

enum class EnemyKind(val health: Double, val speed: Double, val reward: Int, val bite: Int) {
    SUGAR_CUBE(health = 18.0, speed = 0.9, reward = 3, bite = 1),
    GUMMY_BEAR(health = 60.0, speed = 0.6, reward = 7, bite = 2),
    LOLLIPOP(health = 450.0, speed = 0.35, reward = 40, bite = 8),
}

/** [count] candies of one kind, one every [interval] seconds, starting [delay] seconds into the wave. */
class SpawnGroup(val kind: EnemyKind, val count: Int, val interval: Double, val delay: Double = 0.0)

class Wave(val groups: List<SpawnGroup>) {
    /** Spawn times and kinds, in order. */
    val schedule: List<Pair<Double, EnemyKind>> =
        groups.flatMap { g -> (0 until g.count).map { g.delay + it * g.interval to g.kind } }.sortedBy { it.first }
}

class Level(
    val number: Int,
    val path: CandyPath,
    val startMoney: Int,
    val toothHealth: Int,
    val parts: Set<PartKind>,
    val gauges: Set<WireGauge>,
    val waves: List<Wave>,
    val width: Int = 6,
    val height: Int = 9,
) {
    val tooth: Cell get() = path.cells.last()
    private val pathCells = path.cells.toHashSet()

    fun terrain(c: Cell): Terrain = when (c) {
        tooth -> Terrain.TOOTH
        in pathCells -> Terrain.PATH
        else -> Terrain.GROUND
    }
}

object Levels {
    private fun wave(vararg groups: SpawnGroup) = Wave(groups.toList())
    private fun cubes(n: Int, every: Double, delay: Double = 0.0) = SpawnGroup(EnemyKind.SUGAR_CUBE, n, every, delay)
    private fun bears(n: Int, every: Double, delay: Double = 0.0) = SpawnGroup(EnemyKind.GUMMY_BEAR, n, every, delay)
    private fun lollipop(delay: Double) = SpawnGroup(EnemyKind.LOLLIPOP, 1, 1.0, delay)

    /** Level 1: a battery, a wire and the first brush. A simple circuit. */
    val first = Level(
        number = 1,
        path = CandyPath(listOf(Cell(1, 0), Cell(1, 2), Cell(4, 2), Cell(4, 5), Cell(1, 5), Cell(1, 7), Cell(3, 7), Cell(3, 8))),
        startMoney = 70,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH),
        gauges = setOf(WireGauge.THIN),
        waves = listOf(
            wave(cubes(6, 1.6)),
            wave(cubes(10, 1.2)),
            wave(cubes(8, 1.0), bears(2, 3.0, delay = 6.0)),
            wave(cubes(16, 0.7)),
            wave(cubes(8, 0.9), bears(5, 2.2, delay = 4.0)),
        ),
    )

    /** Level 2: the 6 V paste ball. Batteries in series for voltage, in parallel to last longer. */
    val second = Level(
        number = 2,
        path = CandyPath(listOf(Cell(4, 0), Cell(4, 1), Cell(1, 1), Cell(1, 4), Cell(4, 4), Cell(4, 6), Cell(2, 6), Cell(2, 8))),
        startMoney = 120,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH, PartKind.PASTE_CANNON),
        gauges = setOf(WireGauge.THIN),
        waves = listOf(
            wave(cubes(12, 1.0)),
            wave(bears(6, 2.0)),
            wave(cubes(20, 0.6)),
            wave(bears(8, 1.6), cubes(10, 0.8, delay = 3.0)),
            wave(cubes(16, 0.7), bears(6, 2.0, delay = 5.0)),
            wave(cubes(10, 0.9), lollipop(delay = 8.0)),
        ),
    )

    /** Level 3: thick wire for the far side, switches to save batteries, and the 12 V laser. */
    val third = Level(
        number = 3,
        path = CandyPath(listOf(Cell(0, 1), Cell(4, 1), Cell(4, 3), Cell(1, 3), Cell(1, 6), Cell(4, 6), Cell(4, 8))),
        startMoney = 200,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH, PartKind.PASTE_CANNON, PartKind.LASER, PartKind.SWITCH),
        gauges = setOf(WireGauge.THIN, WireGauge.THICK),
        waves = listOf(
            wave(cubes(14, 0.8)),
            wave(bears(10, 1.4)),
            wave(cubes(24, 0.5), bears(4, 2.0, delay = 6.0)),
            wave(bears(12, 1.2), lollipop(delay = 10.0)),
            wave(cubes(20, 0.6), bears(10, 1.2, delay = 2.0), lollipop(delay = 14.0)),
        ),
    )

    val all = listOf(first, second, third)
}
