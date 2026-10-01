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
                brushLoop(2, 1)
                brushLoop(3, 5)
            }
            2 -> {
                // Two batteries in series (6 V) for the paste ball.
                game.place(PartKind.BATTERY, c(3, 3), Dir.E)
                game.place(PartKind.BATTERY, c(4, 3), Dir.E)
                game.place(PartKind.PASTE_CANNON, c(4, 4), Dir.E)
                route(c(4, 3), c(5, 3), c(5, 4), c(4, 4))
                route(c(4, 4), c(3, 4), c(2, 4), c(2, 3), c(3, 3))
                brushLoop(2, 6)
            }
            else -> {
                brushLoop(1, 2)
                game.place(PartKind.BATTERY, c(3, 5), Dir.E)
                game.place(PartKind.BATTERY, c(4, 5), Dir.E)
                game.place(PartKind.PASTE_CANNON, c(4, 6), Dir.E)
                route(c(4, 5), c(5, 5), c(5, 6), c(4, 6), gauge = WireGauge.THICK)
                // Back to the first battery's − side (west); the wire may cross the road.
                route(c(4, 6), c(3, 6), c(2, 6), c(2, 5), c(3, 5), gauge = WireGauge.THICK)
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
            1 -> c(3, 2)
            2 -> c(4, 4)
            else -> c(4, 6)
        }
    }
}
