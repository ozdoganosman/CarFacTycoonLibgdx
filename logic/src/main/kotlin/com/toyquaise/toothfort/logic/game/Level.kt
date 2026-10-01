package com.toyquaise.toothfort.logic.game

import com.toyquaise.toothfort.logic.Geometry
import com.toyquaise.toothfort.logic.Vec2
import com.toyquaise.toothfort.logic.board.Layout
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.WireGauge

/**
 * The trail of spilt syrup the candies follow across the counter: a smooth curve through
 * [control] points, from the tipped-over candy jar to the tooth.
 */
class CandyPath(val control: List<Vec2>) {
    val points: List<Vec2> = Geometry.smooth(control, 0.08)
    private val cumulative = DoubleArray(points.size)
    val length: Double

    init {
        require(control.size >= 2)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + points[i].distanceTo(points[i - 1])
        length = cumulative.last()
    }

    val start: Vec2 get() = points.first()
    val end: Vec2 get() = points.last()

    /** The point [distance] units along the trail. */
    fun at(distance: Double): Vec2 {
        val d = distance.coerceIn(0.0, length)
        var lo = 1
        var hi = points.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] < d) lo = mid + 1 else hi = mid
        }
        val i = lo
        val span = cumulative[i] - cumulative[i - 1]
        val t = if (span < 1e-12) 0.0 else (d - cumulative[i - 1]) / span
        return points[i - 1] + (points[i] - points[i - 1]) * t
    }

    /** The walking direction at [distance] (a unit vector). */
    fun heading(distance: Double): Vec2 {
        val a = at(maxOf(0.0, distance - 0.05))
        val b = at(minOf(length, distance + 0.05))
        return (b - a).normalized()
    }

    fun distanceTo(p: Vec2): Double = Geometry.distanceToPolyline(p, points)
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

/**
 * Kitchen things on the counter. Nothing can be built on them; wires may run over them.
 * [footprint] is a set of circles (offset along and across the prop's axis, radius).
 */
enum class PropKind(val footprint: List<Triple<Double, Double, Double>>) {
    CUTTING_BOARD(listOf(Triple(-0.36, 0.0, 0.52), Triple(0.36, 0.0, 0.52))),
    PLATE(listOf(Triple(0.0, 0.0, 0.58))),
    MUG(listOf(Triple(0.0, 0.0, 0.36))),
    FRUIT_BOWL(listOf(Triple(0.0, 0.0, 0.55))),
    ROLLING_PIN(listOf(Triple(-0.6, 0.0, 0.2), Triple(-0.2, 0.0, 0.2), Triple(0.2, 0.0, 0.2), Triple(0.6, 0.0, 0.2))),
    SALT_SHAKER(listOf(Triple(0.0, 0.0, 0.2))),
}

class Prop(val kind: PropKind, val pos: Vec2, val angle: Double = 0.0) {
    /** The footprint circles on the counter: centre and radius. */
    val circles: List<Pair<Vec2, Double>> by lazy {
        val along = Vec2.ofAngle(angle)
        val across = Vec2(-along.y, along.x)
        kind.footprint.map { (a, c, r) -> pos + along * a + across * c to r }
    }
}

/** A kitchen scene: the counter, its props, the syrup trail, what the player may build, and the waves. */
class Level(
    val number: Int,
    val path: CandyPath,
    val startMoney: Int,
    val toothHealth: Int,
    val parts: Set<PartKind>,
    val gauges: Set<WireGauge>,
    val waves: List<Wave>,
    val props: List<Prop> = emptyList(),
    val width: Double = 6.0,
    val height: Double = 9.0,
) {
    val tooth: Vec2 get() = path.end
    val jar: Vec2 get() = path.start

    /** Whether a round footprint of radius [r] at [p] would hit the trail, a prop, the jar or the tooth. */
    fun blocked(p: Vec2, r: Double): Boolean {
        if (path.distanceTo(p) < Layout.PATH_HALF_WIDTH + r * 0.8) return true
        if (p.distanceTo(tooth) < TOOTH_RADIUS + r) return true
        if (p.distanceTo(jar) < JAR_RADIUS + r) return true
        return props.any { prop -> prop.circles.any { (c, cr) -> p.distanceTo(c) < cr + r } }
    }

    companion object {
        const val TOOTH_RADIUS = 0.72
        const val JAR_RADIUS = 0.55
    }
}

object Levels {
    private fun v(x: Double, y: Double) = Vec2(x, y)
    private fun wave(vararg groups: SpawnGroup) = Wave(groups.toList())
    private fun cubes(n: Int, every: Double, delay: Double = 0.0) = SpawnGroup(EnemyKind.SUGAR_CUBE, n, every, delay)
    private fun bears(n: Int, every: Double, delay: Double = 0.0) = SpawnGroup(EnemyKind.GUMMY_BEAR, n, every, delay)
    private fun lollipop(delay: Double) = SpawnGroup(EnemyKind.LOLLIPOP, 1, 1.0, delay)

    /** Level 1, the counter by the cutting board: a battery, a wire and the first brush. */
    val first = Level(
        number = 1,
        path = CandyPath(listOf(v(1.0, 0.5), v(1.2, 1.8), v(2.6, 2.6), v(4.3, 2.5), v(4.9, 3.9), v(3.9, 5.0), v(2.0, 5.2), v(1.3, 6.4), v(2.2, 7.6), v(3.8, 8.0))),
        startMoney = 70,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH),
        gauges = setOf(WireGauge.THIN),
        props = listOf(
            Prop(PropKind.CUTTING_BOARD, v(4.3, 0.9), 8.0),
            Prop(PropKind.MUG, v(0.6, 3.7)),
            Prop(PropKind.PLATE, v(5.0, 6.7)),
        ),
        waves = listOf(
            wave(cubes(6, 1.6)),
            wave(cubes(10, 1.2)),
            wave(cubes(8, 1.0), bears(2, 3.0, delay = 6.0)),
            wave(cubes(16, 0.7)),
            wave(cubes(8, 0.9), bears(5, 2.2, delay = 4.0)),
        ),
    )

    /** Level 2, breakfast: the 6 V paste ball. Batteries in series for voltage, in parallel to last. */
    val second = Level(
        number = 2,
        path = CandyPath(listOf(v(5.0, 0.5), v(4.6, 1.8), v(2.8, 1.6), v(1.0, 2.4), v(1.2, 4.0), v(3.0, 4.4), v(4.8, 5.0), v(4.6, 6.6), v(2.6, 6.9), v(1.6, 8.0))),
        startMoney = 120,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH, PartKind.PASTE_CANNON),
        gauges = setOf(WireGauge.THIN),
        props = listOf(
            Prop(PropKind.ROLLING_PIN, v(1.9, 0.6), -8.0),
            Prop(PropKind.FRUIT_BOWL, v(5.3, 3.2)),
            Prop(PropKind.MUG, v(0.6, 6.0)),
            Prop(PropKind.SALT_SHAKER, v(3.9, 8.4)),
        ),
        waves = listOf(
            wave(cubes(12, 1.0)),
            wave(bears(6, 2.0)),
            wave(cubes(20, 0.6)),
            wave(bears(8, 1.6), cubes(10, 0.8, delay = 3.0)),
            wave(cubes(16, 0.7), bears(6, 2.0, delay = 5.0)),
            wave(cubes(10, 0.9), lollipop(delay = 8.0)),
        ),
    )

    /** Level 3, by the window: thick wire for the far side, switches, and the 12 V laser. */
    val third = Level(
        number = 3,
        path = CandyPath(listOf(v(0.5, 1.0), v(2.0, 1.5), v(3.8, 1.0), v(5.0, 2.1), v(4.2, 3.6), v(2.2, 3.5), v(1.0, 4.9), v(2.0, 6.4), v(4.0, 6.2), v(4.6, 7.9))),
        startMoney = 200,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH, PartKind.PASTE_CANNON, PartKind.LASER, PartKind.SWITCH),
        gauges = setOf(WireGauge.THIN, WireGauge.THICK),
        props = listOf(
            Prop(PropKind.PLATE, v(1.3, 8.0)),
            Prop(PropKind.MUG, v(5.4, 5.0)),
            Prop(PropKind.SALT_SHAKER, v(0.4, 2.8)),
            Prop(PropKind.ROLLING_PIN, v(3.0, 5.0), 4.0),
        ),
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
