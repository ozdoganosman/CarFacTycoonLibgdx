package com.toyquaise.toothfort.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.toyquaise.toothfort.Palette
import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.Dir
import com.toyquaise.toothfort.logic.board.Board
import com.toyquaise.toothfort.logic.board.Terrain
import com.toyquaise.toothfort.logic.board.Wire
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.Level

/** World position of things on the board: cell (x, y) is centred on (x + 0.5, ·, y + 0.5). */
object BoardSpace {
    /** Where candies walk: the top of the gum road. */
    const val ROAD_TOP = 0.052f

    fun center(c: Cell, y: Float = 0f, out: Vector3 = Vector3()): Vector3 = out.set(c.x + 0.5f, y, c.y + 0.5f)

    /** Rotation about Y that turns a model's +x toward [d]. */
    fun yaw(d: Dir): Float = when (d) {
        Dir.E -> 0f
        Dir.N -> 90f
        Dir.W -> 180f
        Dir.S -> -90f
    }

    fun wireHeight(board: Board, c: Cell): Float = if (board.inside(c) && board.terrain(c) == Terrain.PATH) 0.1f else 0.075f

    /** Where a wire leaving [c] toward [side] starts: a part's terminal knob, or the cell's middle. */
    fun wireEnd(board: Board, c: Cell, side: Dir, out: Vector3): Vector3 {
        center(c, wireHeight(board, c), out)
        if (board.partAt(c) != null) {
            out.x += side.dx * 0.41f
            out.z += side.dy * 0.41f
            out.y = 0.09f
        }
        return out
    }

    fun radius(g: WireGauge) = if (g == WireGauge.THICK) 0.07f else 0.045f
}

object BoardMeshes {
    /**
     * The mint table, one slab of turquoise dough for the board with a pressed dot at every
     * cell corner, and the pink gum road. Built once per level.
     */
    fun board(level: Level): MeshData {
        val m = MeshData()
        val w = level.width.toFloat()
        val h = level.height.toFloat()
        // The table the board sits on (it catches the board's shadow).
        m.color(Palette.table).roundedBox(w / 2, -0.62f, h / 2, 40f, 0.2f, 40f, 0.1f, slices = 8, stacks = 4)
        // The board: a thick slab with soft edges, and a darker lip under it.
        m.color(Palette.boardLip).roundedBox(w / 2, -0.3f, h / 2, w / 2 + 0.26f, 0.13f, h / 2 + 0.26f, 0.13f, slices = 28, stacks = 12)
        m.color(Palette.board).roundedBox(w / 2, -0.12f, h / 2, w / 2 + 0.22f, 0.12f, h / 2 + 0.22f, 0.12f, slices = 28, stacks = 12)
        // A tongue of board under the road's entry, where the candy jar stands.
        val e = entryOf(level)
        m.color(Palette.boardLip).roundedBox(e.x + 0.5f, -0.3f, e.y + 0.5f, 0.6f, 0.13f, 0.6f, 0.13f, slices = 16, stacks = 8)
        m.color(Palette.board).roundedBox(e.x + 0.5f, -0.12f, e.y + 0.5f, 0.56f, 0.12f, 0.56f, 0.12f, slices = 16, stacks = 8)
        // Pressed dots at the cell corners: the grid, without tiles.
        m.color(Palette.boardDot)
        for (y in 0..level.height) for (x in 0..level.width) {
            m.ball(x.toFloat(), 0f, y.toFloat(), 0.045f, 0.012f, 0.045f, slices = 10, stacks = 5)
        }
        // The road: a fat snake of gum, pressed almost flat into the board.
        val cells = level.path.cells
        val points = (listOf(e) + cells).map { Vector3(it.x + 0.5f, 0f, it.y + 0.5f) }
        m.append(MeshData().color(Palette.gumDark).tube(MeshData.smooth(points, 3), 0.46f, sides = 18), Matrix4().translate(0f, -0.01f, 0f).scale(1f, 0.09f, 1f))
        m.append(MeshData().color(Palette.gum).tube(MeshData.smooth(points, 3), 0.4f, sides = 18), Matrix4().scale(1f, 0.13f, 1f))
        // Sprinkles on the road, like on a cake.
        val sprinkleColors = listOf(Palette.yellow, Palette.surface, Palette.grape, Palette.turquoise, Palette.coral)
        for ((i, c) in cells.dropLast(1).withIndex()) {
            for (k in 0 until 2) {
                val a = hash(c.x * 13 + c.y * 7 + k * 101) * 6.283f
                val ox = (hash(c.x * 5 + c.y * 11 + k * 37) - 0.5f) * 0.55f
                val oz = (hash(c.x * 17 + c.y * 3 + k * 53) - 0.5f) * 0.55f
                val cx = c.x + 0.5f + ox
                val cz = c.y + 0.5f + oz
                val dx = kotlin.math.cos(a) * 0.05f
                val dz = kotlin.math.sin(a) * 0.05f
                m.color(sprinkleColors[(i * 2 + k) % sprinkleColors.size])
                m.tube(listOf(Vector3(cx - dx, ROAD_TOP - 0.005f, cz - dz), Vector3(cx + dx, ROAD_TOP - 0.005f, cz + dz)), 0.018f, sides = 6)
            }
        }
        return m
    }

    private const val ROAD_TOP = BoardSpace.ROAD_TOP

    /** The cell just before the road's first cell, off the board, where candies come from. */
    fun entryOf(level: Level): Cell {
        val cells = level.path.cells
        return Cell(cells[0].x - (cells[1].x - cells[0].x), cells[0].y - (cells[1].y - cells[0].y))
    }

    /** Every wire as a rolled snake of dough, and a blob where wires meet in a cell. */
    fun wires(board: Board, seed: Int): MeshData {
        val m = MeshData()
        val a = Vector3()
        val b = Vector3()
        for (w in board.allWires) {
            val d = w.a.dirTo(w.b) ?: continue
            BoardSpace.wireEnd(board, w.a, d, a)
            BoardSpace.wireEnd(board, w.b, d.opposite, b)
            val r = BoardSpace.radius(w.gauge)
            if (w.melted) {
                // Two burnt stubs with a gap between them.
                m.color(Palette.charcoal)
                m.tube(listOf(a.cpy(), a.cpy().lerp(b, 0.38f).add(0f, -0.02f, 0f)), r * 0.85f)
                m.tube(listOf(b.cpy(), b.cpy().lerp(a, 0.38f).add(0f, -0.02f, 0f)), r * 0.85f)
                continue
            }
            m.color(wireColor(w))
            val mid = a.cpy().lerp(b, 0.5f)
            // A slight sideways bend, different for every wire, so they look hand-laid.
            val wobble = (hash(w.a.x * 31 + w.a.y * 17 + w.b.x * 7 + w.b.y * 3 + seed) - 0.5f) * 0.06f
            mid.x += -d.dy * wobble
            mid.z += d.dx * wobble
            mid.y += 0.01f
            m.tube(MeshData.smooth(listOf(a.cpy(), mid, b.cpy()), 4), r, sides = 8)
        }
        // Joints in empty cells.
        val joints = HashMap<Cell, WireGauge>()
        for (w in board.allWires) {
            if (w.melted) continue
            for (c in listOf(w.a, w.b)) {
                if (board.partAt(c) != null) continue
                if (joints[c] != WireGauge.THICK) joints[c] = w.gauge
            }
        }
        for ((c, g) in joints) {
            val r = BoardSpace.radius(g) * 1.45f
            m.color(if (g == WireGauge.THICK) Palette.coral else Palette.yellow)
            m.ball(c.x + 0.5f, BoardSpace.wireHeight(board, c) + 0.005f, c.y + 0.5f, r, r * 0.8f, r, slices = 12, stacks = 8)
        }
        return m
    }

    fun wireColor(w: Wire): Color = if (w.gauge == WireGauge.THICK) Palette.coral else Palette.yellow

    private fun hash(n: Int): Float {
        var x = n * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xffff) / 65535f
    }
}
