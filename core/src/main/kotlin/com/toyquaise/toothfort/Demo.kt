package com.toyquaise.toothfort

import com.toyquaise.toothfort.logic.Vec2
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Port
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.ActionResult
import com.toyquaise.toothfort.logic.game.Game

/** A sample defence for each kitchen, for screenshots and for trying the look quickly. */
object Demo {
    /** Builds the sample defence and plays [seconds] of the first wave; returns a part worth selecting. */
    fun build(game: Game, seconds: Float): Part? {
        fun v(x: Double, y: Double) = Vec2(x, y)
        fun put(kind: PartKind, x: Double, y: Double, angle: Double = 0.0): Part? =
            if (game.place(kind, v(x, y), angle) == ActionResult.OK) game.board.parts.last() else null
        fun wire(a: Part?, aPlus: Boolean, b: Part?, bPlus: Boolean, vararg via: Vec2, gauge: WireGauge = WireGauge.THIN) {
            if (a == null || b == null) return
            game.wire(Port.Terminal(a, aPlus), Port.Terminal(b, bPlus), via.toList(), gauge)
        }
        val show: Part? = when (game.level.number) {
            1 -> {
                // A battery standing on end with the brush pressed onto its + end, and a wire back.
                val b1 = put(PartKind.BATTERY, 2.5, 0.7, -90.0)
                val brush1 = put(PartKind.BRUSH, 2.5, 1.5, -90.0)
                wire(brush1, true, b1, false, v(3.1, 1.96), v(3.1, 0.28))
                val b2 = put(PartKind.BATTERY, 2.6, 3.8)
                val brush2 = put(PartKind.BRUSH, 3.4, 3.8)
                wire(brush2, true, b2, false, v(4.0, 4.3), v(2.1, 4.3))
                brush1
            }
            2 -> {
                // Two batteries pressed end to end (6 V) for the paste ball.
                val b1 = put(PartKind.BATTERY, 2.4, 3.2)
                val b2 = put(PartKind.BATTERY, 3.3, 3.2) // snaps onto b1's + end
                val cannon = put(PartKind.PASTE_CANNON, 3.0, 5.7)
                wire(b2, true, cannon, true, v(4.1, 3.2), v(4.0, 5.7))
                wire(cannon, false, b1, false, v(2.2, 5.7), v(1.6, 4.6), v(1.6, 3.2))
                val battery = put(PartKind.BATTERY, 4.9, 8.3)
                val brush = put(PartKind.BRUSH, 4.0, 7.5)
                wire(battery, true, brush, true, v(5.5, 8.3), v(5.4, 7.5))
                wire(brush, false, battery, false, v(3.5, 7.9), v(3.9, 8.3))
                cannon
            }
            else -> {
                val b = put(PartKind.BATTERY, 2.3, 2.4)
                val brush = put(PartKind.BRUSH, 3.2, 2.4)
                wire(brush, true, b, false, v(3.8, 2.9), v(1.8, 2.9))
                // Two batteries in series and a long thick wire to the paste ball.
                val b1 = put(PartKind.BATTERY, 2.4, 7.6)
                val b2 = put(PartKind.BATTERY, 3.3, 7.6)
                val cannon = put(PartKind.PASTE_CANNON, 1.2, 6.9)
                wire(b1, false, cannon, true, v(1.9, 7.2), gauge = WireGauge.THICK)
                wire(b2, true, cannon, false, v(3.9, 8.3), v(2.0, 8.75), v(0.5, 8.6), v(0.5, 7.3), gauge = WireGauge.THICK)
                cannon
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
        return show
    }
}
