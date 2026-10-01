package com.toyquaise.toothfort.logic

import com.toyquaise.toothfort.logic.board.Layout
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Port
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.ActionResult
import com.toyquaise.toothfort.logic.game.CandyPath
import com.toyquaise.toothfort.logic.game.EnemyKind
import com.toyquaise.toothfort.logic.game.Game
import com.toyquaise.toothfort.logic.game.Level
import com.toyquaise.toothfort.logic.game.Levels
import com.toyquaise.toothfort.logic.game.Phase
import com.toyquaise.toothfort.logic.game.SpawnGroup
import com.toyquaise.toothfort.logic.game.Wave
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameTest {
    private fun v(x: Double, y: Double) = Vec2(x, y)
    private val dt = 1.0 / 60
    private val Part.plus get() = Port.Terminal(this, true)
    private val Part.minus get() = Port.Terminal(this, false)

    /** A straight trail along y = 2.5 to a tooth at (8, 2.5); six sugar cubes. */
    private fun straight(money: Int = 200) = Level(
        number = 0,
        path = CandyPath(listOf(v(0.0, 2.5), v(8.0, 2.5))),
        startMoney = money,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH),
        gauges = setOf(WireGauge.THIN),
        waves = listOf(Wave(listOf(SpawnGroup(EnemyKind.SUGAR_CUBE, 6, 1.5))), Wave(listOf(SpawnGroup(EnemyKind.SUGAR_CUBE, 2, 1.0)))),
        width = 9.0,
        height = 5.0,
    )

    private fun Game.part(kind: PartKind, x: Double, y: Double): Part {
        assertEquals(ActionResult.OK, place(kind, v(x, y), 0.0), "$kind at ($x, $y)")
        return board.parts.last()
    }

    /** A battery and a brush above the trail at x, wired in a loop. */
    private fun Game.brushLoop(x: Double) {
        val battery = part(PartKind.BATTERY, x, 0.5)
        val brush = part(PartKind.BRUSH, x, 1.5)
        assertEquals(ActionResult.OK, wire(battery.plus, brush.plus, listOf(v(x + 0.8, 0.5), v(x + 0.8, 1.5)), WireGauge.THIN))
        assertEquals(ActionResult.OK, wire(brush.minus, battery.minus, listOf(v(x - 0.8, 1.5), v(x - 0.8, 0.5)), WireGauge.THIN))
    }

    private fun Game.playWave(maxSeconds: Double = 120.0) {
        assertTrue(startWave())
        var t = 0.0
        while (phase == Phase.WAVE && t < maxSeconds) { step(dt); t += dt }
    }

    @Test
    fun `undefended candies bite the tooth`() {
        val g = Game(straight())
        g.playWave()
        assertEquals(Phase.BUILD, g.phase)
        assertEquals(20 - 6, g.toothHealth)
        assertEquals(1, g.waveIndex)
    }

    @Test
    fun `powered brushes clean the trail and earn money`() {
        val g = Game(straight())
        g.brushLoop(2.5)
        g.brushLoop(5.5)
        val before = g.money
        g.playWave()
        assertEquals(20, g.toothHealth)
        assertEquals(before + 6 * EnemyKind.SUGAR_CUBE.reward + 10, g.money)
    }

    @Test
    fun `a brush without a closed circuit does nothing`() {
        val g = Game(straight())
        val battery = g.part(PartKind.BATTERY, 3.0, 0.5)
        val brush = g.part(PartKind.BRUSH, 3.0, 1.5)
        g.wire(battery.plus, brush.plus, listOf(v(3.8, 0.5), v(3.8, 1.5)), WireGauge.THIN) // no return wire
        g.playWave()
        assertEquals(20 - 6, g.toothHealth)
    }

    @Test
    fun `wire is sold by length and building is free to undo`() {
        val g = Game(straight())
        val money = g.money
        val battery = g.part(PartKind.BATTERY, 3.0, 0.5)
        val brush = g.part(PartKind.BRUSH, 6.0, 0.5)
        val price = g.wirePrice(battery.plus, brush.minus, emptyList(), WireGauge.THIN)
        assertEquals(3, price) // 2.16 units of thin wire
        assertEquals(ActionResult.OK, g.wire(battery.plus, brush.minus, emptyList(), WireGauge.THIN))
        assertEquals(money - 10 - 15 - 3, g.money)
        // Taking the battery away takes its wire too, and everything comes back while no wave runs.
        g.erase(battery)
        g.erase(brush)
        assertEquals(money, g.money)
        assertTrue(g.board.wires.isEmpty())
    }

    @Test
    fun `parts stay off the trail and out of locked levels`() {
        val g = Game(Levels.first)
        val onTrail = g.path.at(3.0)
        assertEquals(ActionResult.BLOCKED, g.place(PartKind.BRUSH, onTrail))
        assertEquals(ActionResult.LOCKED, g.place(PartKind.PASTE_CANNON, v(3.0, 4.0)))
    }

    @Test
    fun `batteries only drain while a wave runs`() {
        val g = Game(straight())
        g.brushLoop(2.5)
        val battery = g.board.parts.first()
        repeat(600) { g.step(dt) }
        assertEquals(1.0, battery.charge)
        g.playWave()
        assertTrue(battery.charge < 1.0)
    }

    @Test
    fun `clearing the last wave wins the level`() {
        val g = Game(straight())
        g.brushLoop(2.5)
        g.brushLoop(5.5)
        g.playWave()
        g.playWave()
        assertEquals(Phase.WON, g.phase)
    }

    @Test
    fun `every kitchen keeps its props off the trail and leaves room to build`() {
        for (level in Levels.all) {
            val g = Game(level)
            assertTrue(level.path.points.all { g.board.inside(it) }, "level ${level.number}: trail on the counter")
            for (prop in level.props) for ((c, r) in prop.circles) {
                assertTrue(level.path.distanceTo(c) >= Layout.PATH_HALF_WIDTH + r - 0.05, "level ${level.number}: ${prop.kind} on the trail")
                assertTrue(g.board.inside(c), "level ${level.number}: ${prop.kind} off the counter")
            }
            var free = 0
            var all = 0
            var y = 0.25
            while (y < level.height) {
                var x = 0.25
                while (x < level.width) {
                    all++
                    if (g.board.canStand(v(x, y))) free++
                    x += 0.25
                }
                y += 0.25
            }
            assertTrue(free > all / 5, "level ${level.number}: only $free of $all spots free")
        }
    }
}
