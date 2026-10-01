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
    const val ROAD_TOP = 0.08f

    fun center(c: Cell, y: Float = 0f, out: Vector3 = Vector3()): Vector3 = out.set(c.x + 0.5f, y, c.y + 0.5f)

    /** Rotation about Y that turns a model's +x toward [d]. */
    fun yaw(d: Dir): Float = when (d) {
        Dir.E -> 0f
        Dir.N -> 90f
        Dir.W -> 180f
        Dir.S -> -90f
    }

    fun wireHeight(board: Board, c: Cell): Float = if (board.inside(c) && board.terrain(c) == Terrain.PATH) 0.13f else 0.075f

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
    /** The table tray, the clay tiles and the pink gum road. Built once per level. */
    fun board(level: Level): MeshData {
        val m = MeshData()
        val w = level.width.toFloat()
        val h = level.height.toFloat()
        m.color(Palette.tray).roundedBox(w / 2, -0.2f, h / 2, w / 2 + 0.3f, 0.16f, h / 2 + 0.3f, 0.16f, slices = 24, stacks = 12)
        for (y in 0 until level.height) for (x in 0 until level.width) {
            val c = Cell(x, y)
            if (level.terrain(c) != Terrain.GROUND) continue
            m.color(if ((x + y) % 2 == 0) Palette.tileA else Palette.tileB)
            m.roundedBox(x + 0.5f, -0.06f, y + 0.5f, 0.47f, 0.06f, 0.47f, 0.06f, slices = 12, stacks = 8)
        }
        // The road: a fat snake of gum, pressed flat.
        val cells = level.path.cells
        val first = cells[0]
        val entry = Cell(first.x - (cells[1].x - first.x), first.y - (cells[1].y - first.y))
        val points = (listOf(entry) + cells).map { Vector3(it.x + 0.5f, 0f, it.y + 0.5f) }
        val road = MeshData().color(Palette.gum).tube(MeshData.smooth(points, 3), 0.44f, sides = 18)
        m.append(road, Matrix4().scale(1f, 0.18f, 1f))
        // Cell marks along the road, so wires over it still read as a grid.
        for (c in cells.dropLast(1)) {
            m.color(Palette.gumDark).ball(c.x + 0.5f, ROAD_DOT_Y, c.y + 0.5f, 0.05f, 0.012f, 0.05f, slices = 10, stacks = 5)
        }
        return m
    }

    private const val ROAD_DOT_Y = 0.078f

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
