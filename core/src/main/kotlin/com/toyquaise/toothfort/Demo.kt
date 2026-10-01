package com.toyquaise.toothfort

import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.Dir
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.Game

/** A sample defence for each level, for screenshots and for trying the look quickly. */
object Demo {
    /** Builds the sample defence and plays [seconds] of the first wave; returns a part worth selecting. */
    fun build(game: Game, seconds: Float): Cell? {
        fun c(x: Int, y: Int) = Cell(x, y)
        fun route(vararg cells: Cell, gauge: WireGauge = WireGauge.THIN) {
            for (i in 1 until cells.size) game.wire(cells[i - 1], cells[i], gauge)
        }
        /** A battery at (x+1, y) and a brush below it at (x+1, y+1), wired in a loop. */
        fun brushLoop(x: Int, y: Int) {
            game.place(PartKind.BATTERY, c(x + 1, y), Dir.E)
            game.place(PartKind.BRUSH, c(x + 1, y + 1), Dir.E)
            route(c(x + 1, y), c(x + 2, y), c(x + 2, y + 1), c(x + 1, y + 1))
            route(c(x + 1, y + 1), c(x, y + 1), c(x, y), c(x + 1, y))
        }
        when (game.level.number) {
            1 -> {
                brushLoop(2, 0)
                brushLoop(1, 3)
            }
            2 -> {
                // Two batteries in series (6 V) for the paste ball; the return wire crosses the road.
                game.place(PartKind.BATTERY, c(2, 2), Dir.E)
                game.place(PartKind.BATTERY, c(3, 2), Dir.E)
                game.place(PartKind.PASTE_CANNON, c(3, 3), Dir.E)
                route(c(3, 2), c(4, 2), c(4, 3), c(3, 3))
                route(c(3, 3), c(2, 3), c(1, 3), c(1, 2), c(2, 2))
                brushLoop(3, 7)
            }
            else -> {
                brushLoop(2, 4)
                game.place(PartKind.BATTERY, c(1, 7), Dir.E)
                game.place(PartKind.BATTERY, c(2, 7), Dir.E)
                game.place(PartKind.PASTE_CANNON, c(2, 8), Dir.E)
                route(c(2, 7), c(3, 7), c(3, 8), c(2, 8), gauge = WireGauge.THICK)
                route(c(2, 8), c(1, 8), c(0, 8), c(0, 7), c(1, 7), gauge = WireGauge.THICK)
            }
        }
        if (seconds > 0) {
            game.startWave()
            var t = 0.0
            while (t < seconds) {
                game.step(PlayScreen.STEP)
                t += PlayScreen.STEP
            }
        }
        return when (game.level.number) {
            1 -> c(3, 1)
            2 -> c(3, 3)
            else -> c(2, 8)
        }
    }
}
