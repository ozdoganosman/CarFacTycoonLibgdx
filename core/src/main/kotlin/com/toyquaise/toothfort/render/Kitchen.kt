package com.toyquaise.toothfort.render

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.toyquaise.toothfort.Palette
import com.toyquaise.toothfort.logic.Vec2
import com.toyquaise.toothfort.logic.board.Board
import com.toyquaise.toothfort.logic.board.Layout
import com.toyquaise.toothfort.logic.board.Wire
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.Level
import com.toyquaise.toothfort.logic.game.PropKind

/** The counter in 3D: a point (x, y) on the counter is (x, height, y) in the world. */
object Kitchen {
    /** Top of the syrup trail, where candies walk. */
    const val TRAIL_TOP = 0.05f

    fun world(p: Vec2, y: Float = 0f, out: Vector3 = Vector3()): Vector3 = out.set(p.x.toFloat(), y, p.y.toFloat())

    /** Rotation about the vertical that turns a model's +x toward [degrees] on the counter. */
    fun yaw(degrees: Double): Float = degrees.toFloat()

    fun radius(g: WireGauge) = if (g == WireGauge.THICK) 0.07f else 0.045f

    fun propTop(kind: PropKind): Float = when (kind) {
        PropKind.CUTTING_BOARD -> 0.15f
        PropKind.PLATE -> 0.13f
        PropKind.MUG -> 0.57f
        PropKind.FRUIT_BOWL -> 0.5f
        PropKind.ROLLING_PIN -> 0.34f
        PropKind.SALT_SHAKER -> 0.45f
    }

    /** How high a wire lies at [p]: on the counter, on the trail, or draped over a prop. */
    fun wireHeight(level: Level, p: Vec2, radius: Float): Float {
        var h = 0.03f
        if (level.path.distanceTo(p) < Layout.PATH_HALF_WIDTH) h = TRAIL_TOP + 0.01f
        for (prop in level.props) for ((c, r) in prop.circles) {
            if (p.distanceTo(c) < r) h = maxOf(h, propTop(prop.kind) + 0.01f)
        }
        return h + radius
    }

    /** The syrup trail: a fat snake of pink, pressed flat, with sprinkles. Built once per level. */
    fun trail(level: Level): MeshData {
        val m = MeshData()
        val pts = level.path.points.filterIndexed { i, _ -> i % 2 == 0 || i == level.path.points.lastIndex }
            .map { Vector3(it.x.toFloat(), 0f, it.y.toFloat()) }
        m.append(MeshData().color(Palette.syrup).tube(pts, 0.47f), Matrix4().translate(0f, -0.01f, 0f).scale(1f, 0.08f, 1f))
        m.append(MeshData().color(Palette.syrupLight).tube(pts, 0.36f), Matrix4().scale(1f, 0.14f, 1f))
        val colours = listOf(Palette.yellow, Palette.surface, Palette.grape, Palette.turquoise, Palette.coral)
        var d = 0.2
        var i = 0
        while (d < level.path.length - 0.6) {
            val p = level.path.at(d)
            val h = level.path.heading(d)
            val side = Vec2(-h.y, h.x) * (((i * 7919) % 11) / 11.0 - 0.5) * 0.5
            val c = p + side
            val a = i * 2.399
            val dx = (kotlin.math.cos(a) * 0.05).toFloat()
            val dz = (kotlin.math.sin(a) * 0.05).toFloat()
            m.color(colours[i % colours.size])
            m.tube(listOf(Vector3(c.x.toFloat() - dx, TRAIL_TOP, c.y.toFloat() - dz), Vector3(c.x.toFloat() + dx, TRAIL_TOP, c.y.toFloat() + dz)), 0.019f)
            d += 0.23
            i++
        }
        return m
    }

    /** Every wire as a rolled snake of dough along its course, draped over whatever is under it. */
    fun wires(board: Board, level: Level): MeshData {
        val m = MeshData()
        for (w in board.wires) addWire(m, board, level, w)
        return m
    }

    fun addWire(m: MeshData, board: Board, level: Level, w: Wire) {
        val r = radius(w.gauge)
        val course = board.course(w)
        val pts = ArrayList<Vector3>()
        for ((i, p) in course.withIndex()) {
            val end = i == 0 || i == course.lastIndex
            val y = if (end) 0.1f else wireHeight(level, p, r)
            pts += Vector3(p.x.toFloat(), y, p.y.toFloat())
        }
        val smooth = if (pts.size > 2) MeshData.smooth(pts, 3) else pts
        if (w.melted) {
            m.color(Palette.charcoal)
            val cut = smooth.size / 2
            if (cut >= 2) m.tube(smooth.subList(0, cut - 1), r * 0.85f)
            if (smooth.size - cut >= 2) m.tube(smooth.subList(cut + 1, smooth.size).ifEmpty { smooth.takeLast(2) }, r * 0.85f)
            return
        }
        m.color(if (w.gauge == WireGauge.THICK) Palette.coral else Palette.yellow)
        m.tube(smooth, r)
    }
}
