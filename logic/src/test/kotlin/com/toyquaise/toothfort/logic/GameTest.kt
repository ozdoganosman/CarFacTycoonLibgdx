package com.toyquaise.toothfort.logic

import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Terrain
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
    private fun c(x: Int, y: Int) = Cell(x, y)
    private val dt = 1.0 / 60

    /** A straight road along row 2 to a tooth at (7,2); six sugar cubes. */
    private fun straight(money: Int = 200) = Level(
        number = 0,
        path = CandyPath(listOf(c(0, 2), c(7, 2))),
        startMoney = money,
        toothHealth = 20,
        parts = setOf(PartKind.BATTERY, PartKind.BRUSH),
        gauges = setOf(WireGauge.THIN),
        waves = listOf(Wave(listOf(SpawnGroup(EnemyKind.SUGAR_CUBE, 6, 1.5))), Wave(listOf(SpawnGroup(EnemyKind.SUGAR_CUBE, 2, 1.0)))),
        width = 8,
        height = 5,
    )

    private fun Game.route(vararg cells: Cell) {
        for (i in 1 until cells.size) assertEquals(ActionResult.OK, wire(cells[i - 1], cells[i], WireGauge.THIN), "${cells[i - 1]} -> ${cells[i]}")
    }

    /** A battery-and-brush loop in the 3×2 block whose top-left corner is (x,0), above the road. */
    private fun Game.brushLoop(x: Int) {
        assertEquals(ActionResult.OK, place(PartKind.BATTERY, c(x + 1, 0), Dir.E))
        assertEquals(ActionResult.OK, place(PartKind.BRUSH, c(x + 1, 1), Dir.E))
        route(c(x + 1, 0), c(x + 2, 0), c(x + 2, 1), c(x + 1, 1))
        route(c(x + 1, 1), c(x, 1), c(x, 0), c(x + 1, 0))
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
    fun `powered brushes clean the road and earn money`() {
        val g = Game(straight())
        g.brushLoop(2)
        g.brushLoop(5)
        val before = g.money
        g.playWave()
        assertEquals(20, g.toothHealth)
        assertEquals(before + 6 * EnemyKind.SUGAR_CUBE.reward + 10, g.money)
    }

    @Test
    fun `a brush without a closed circuit does nothing`() {
        val g = Game(straight())
        g.place(PartKind.BATTERY, c(3, 0), Dir.E)
        g.place(PartKind.BRUSH, c(3, 1), Dir.E)
        g.route(c(3, 0), c(4, 0), c(4, 1), c(3, 1)) // the return wire is missing
        g.playWave()
        assertEquals(20 - 6, g.toothHealth)
    }

    @Test
    fun `parts go on the ground only, cost money and are locked until their level`() {
        val g = Game(Levels.first)
        val road = g.path.cells[2]
        assertEquals(Terrain.PATH, g.board.terrain(road))
        assertEquals(ActionResult.BLOCKED, g.place(PartKind.BRUSH, road))
        assertEquals(ActionResult.LOCKED, g.place(PartKind.PASTE_CANNON, c(4, 0)))
        val money = g.money
        assertEquals(ActionResult.OK, g.place(PartKind.BRUSH, c(4, 0)))
        assertEquals(money - PartKind.BRUSH.cost, g.money)
        // Building is free to undo: everything comes back while no wave runs.
        assertEquals(ActionResult.OK, g.erase(c(4, 0)))
        assertEquals(money, g.money)
    }

    @Test
    fun `wires may cross the road but not the tooth`() {
        val g = Game(straight())
        assertEquals(ActionResult.OK, g.wire(c(3, 1), c(3, 2), WireGauge.THIN))
        assertEquals(ActionResult.BLOCKED, g.wire(c(6, 2), c(7, 2), WireGauge.THIN))
    }

    @Test
    fun `batteries only drain while a wave runs`() {
        val g = Game(straight())
        g.brushLoop(2)
        repeat(600) { g.step(dt) }
        assertEquals(1.0, g.board.partAt(c(3, 0))!!.charge)
        g.playWave()
        assertTrue(g.board.partAt(c(3, 0))!!.charge < 1.0)
    }

    @Test
    fun `clearing the last wave wins the level`() {
        val g = Game(straight())
        g.brushLoop(2)
        g.brushLoop(5)
        g.playWave()
        g.playWave()
        assertEquals(Phase.WON, g.phase)
    }

    @Test
    fun `every level's road reaches its tooth and leaves room to build`() {
        for (level in Levels.all) {
            val g = Game(level)
            assertEquals(Terrain.TOOTH, g.board.terrain(level.tooth))
            assertTrue(level.path.cells.all { it.x in 0 until level.width && it.y in 0 until level.height }, "level ${level.number}")
            val ground = (0 until level.width).sumOf { x -> (0 until level.height).count { y -> g.board.terrain(c(x, y)) == Terrain.GROUND } }
            assertTrue(ground > level.width * level.height / 2, "level ${level.number} has $ground free cells")
        }
    }
}
