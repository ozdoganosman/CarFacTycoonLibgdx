package com.toyquaise.toothfort.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.toothfort.Palette
import com.toyquaise.toothfort.logic.Cell
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Role
import com.toyquaise.toothfort.logic.game.Enemy
import com.toyquaise.toothfort.logic.game.EnemyKind
import com.toyquaise.toothfort.logic.game.Game
import com.toyquaise.toothfort.logic.game.GameEvent
import com.toyquaise.toothfort.logic.game.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws a game in clay: the board, the tooth, parts and their wires, candies, paste and beams,
 * and little bursts of dough when things happen. Holds only looks (turning heads, flashes,
 * particles); the game itself lives in [Game].
 */
class WorldView(private val models: Models, private val game: Game) : Disposable {
    private val boardMesh: Mesh = BoardMeshes.board(game.level).toMesh()
    private var wireMesh: Mesh? = null
    private var wireRevision = -1
    var time = 0f
        private set

    private class PartLook(var headYaw: Float, var recoil: Float = 0f, var scrub: Float = 0f)
    private val looks = HashMap<Part, PartLook>()
    private val enemyFlash = HashMap<Int, Float>()
    private var toothFlash = 0f
    private var toothSquash = 0f

    private class Particle(
        val pos: Vector3, val vel: Vector3, var life: Float, val maxLife: Float,
        val color: Color, val size: Float, val gravity: Float, val mesh: Mesh,
    )
    private val particles = ArrayList<Particle>()
    private val rnd = Random(11)

    /** Floating notes ("+3") the overlay draws: world position, text, colour, age. */
    class Floater(val pos: Vector3, val text: String, val color: Color, var age: Float = 0f)
    val floaters = ArrayList<Floater>()

    /** The cell the player is pointing at and whether something could go there. */
    var hover: Cell? = null
    var hoverOk = true

    /** The selected part's cell. */
    var selected: Cell? = null

    private val m = Matrix4()
    private val m2 = Matrix4()
    private val v = Vector3()
    private val v2 = Vector3()
    private val tint = Color()

    // ------------------------------------------------------------------ events

    fun onEvent(e: GameEvent) {
        when (e) {
            is GameEvent.Hit -> {
                enemyFlash[e.enemy.id] = 0.12f
                looks[e.by]?.let { if (e.by.kind == PartKind.BRUSH) it.scrub = 0.25f }
                if (e.by.kind == PartKind.BRUSH) burst(enemyPos(e.enemy, v), 2, Palette.enamel, 0.035f, 0.9f)
            }
            is GameEvent.Splat -> burst(world(e.at, 0.2f, v), 10, Palette.enamel, 0.05f, 1.6f, Palette.coral)
            is GameEvent.Killed -> {
                burst(world(e.at, 0.25f, v), 14, candyColor(e.enemy), 0.06f, 2.2f)
                floaters += Floater(world(e.at, 0.6f, Vector3()), "+${e.enemy.kind.reward}", Palette.yellow)
                enemyFlash.remove(e.enemy.id)
            }
            is GameEvent.Bit -> {
                toothFlash = 0.35f
                toothSquash = 1f
                floaters += Floater(BoardSpace.center(game.level.tooth, 1.6f), "−${e.damage}", Palette.coral)
            }
            is GameEvent.Wiring -> when (val b = e.event) {
                is BoardEvent.Burnt -> {
                    BoardSpace.center(b.part.cell, 0.35f, v)
                    burst(v, 14, Palette.yellow, 0.04f, 2.5f)
                    smoke(v, 8)
                }
                is BoardEvent.Melted -> {
                    BoardSpace.center(b.wire.a, 0.1f, v).lerp(BoardSpace.center(b.wire.b, 0.1f, v2), 0.5f)
                    burst(v, 12, Palette.yellow, 0.035f, 2.2f, Palette.coral)
                    smoke(v, 5)
                }
                is BoardEvent.Blown -> {
                    BoardSpace.center(b.part.cell, 0.25f, v)
                    burst(v, 8, Palette.yellow, 0.03f, 1.8f)
                }
                is BoardEvent.Depleted -> {}
            }
            is GameEvent.Shot -> looks[e.by]?.recoil = 1f
            is GameEvent.WaveStarted, is GameEvent.WaveCleared, GameEvent.Won, GameEvent.Lost -> {}
        }
    }

    private fun burst(at: Vector3, n: Int, color: Color, size: Float, speed: Float, second: Color? = null) {
        repeat(n) { i ->
            val dir = Vector3(rnd.nextFloat() - 0.5f, rnd.nextFloat() * 0.9f + 0.4f, rnd.nextFloat() - 0.5f).nor()
            val s = speed * (0.5f + rnd.nextFloat() * 0.6f)
            val c = if (second != null && i % 3 == 0) second else color
            val life = 0.45f + rnd.nextFloat() * 0.35f
            particles += Particle(at.cpy(), dir.scl(s), life, life, c, size * (0.7f + rnd.nextFloat() * 0.6f), 7f, models.dot)
        }
    }

    private fun smoke(at: Vector3, n: Int) {
        repeat(n) {
            val life = 1.0f + rnd.nextFloat() * 0.8f
            val vel = Vector3((rnd.nextFloat() - 0.5f) * 0.3f, 0.6f + rnd.nextFloat() * 0.4f, (rnd.nextFloat() - 0.5f) * 0.3f)
            particles += Particle(at.cpy(), vel, life, life, Palette.smoke, 0.07f + rnd.nextFloat() * 0.05f, -0.2f, models.dot)
        }
    }

    // ------------------------------------------------------------------ frame

    fun update(dt: Float) {
        time += dt
        toothFlash = maxOf(0f, toothFlash - dt)
        toothSquash = maxOf(0f, toothSquash - dt * 3f)
        enemyFlash.entries.removeAll { it.setValue(it.value - dt); it.value <= 0f }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vel.y -= p.gravity * dt
            p.pos.mulAdd(p.vel, dt)
            if (p.pos.y < 0.03f && p.gravity > 0) { p.pos.y = 0.03f; p.vel.scl(0.4f, -0.3f, 0.4f) }
        }
        floaters.removeAll { f -> f.age += dt; f.pos.y += dt * 0.7f; f.age > 1.1f }

        looks.keys.retainAll(game.board.allParts.toSet())
        for (p in game.board.allParts) {
            val look = looks.getOrPut(p) { PartLook(BoardSpace.yaw(p.facing)) }
            look.recoil = maxOf(0f, look.recoil - dt * 4f)
            look.scrub = maxOf(0f, look.scrub - dt)
            val target = p.kind.machine?.let { aimOf(p) }
            if (target != null && !p.broken && p.performance > 0) {
                val want = target
                var diff = want - look.headYaw
                while (diff > 180f) diff -= 360f
                while (diff < -180f) diff += 360f
                look.headYaw += MathUtils.clamp(diff, -480f * dt, 480f * dt)
            }
        }
    }

    /** Yaw toward the candy this machine would aim at (the one furthest along within reach). */
    private fun aimOf(p: Part): Float? {
        val spec = p.kind.machine ?: return null
        val c = Vec2.center(p.cell)
        val e = game.beams[p] ?: game.enemies.filter { it.alive && game.path.at(it.distance).distanceTo(c) <= spec.range }.maxByOrNull { it.distance }
        ?: return null
        val at = game.path.at(e.distance)
        return MathUtils.radiansToDegrees * atan2(-(at.y - c.y), at.x - c.x).toFloat()
    }

    fun draw(r: ClayRenderer) {
        val wobble = 0.022f
        r.draw(boardMesh, m.idt(), Color.WHITE, wobble = 0.03f).apply { grain = 0.5f }

        // The candy jar at the road's entry.
        val cells = game.path.cells
        val entry = Cell(cells[0].x - (cells[1].x - cells[0].x), cells[0].y - (cells[1].y - cells[0].y))
        BoardSpace.center(entry, 0.06f, v)
        r.draw(models.jarCandies, m.setToTranslation(v), Color.WHITE, wobble)
        r.draw(models.jarGlass, m.setToTranslation(v), tint.set(1f, 1f, 1f, 0.42f), 0.01f).apply { castShadow = false; grain = 0.1f }

        drawTooth(r)

        if (wireRevision != game.board.revision || wireMesh == null) {
            wireMesh?.dispose()
            wireMesh = BoardMeshes.wires(game.board, 3).toMesh()
            wireRevision = game.board.revision
        }
        wireMesh?.let { r.draw(it, m.idt(), Color.WHITE, wobble = 0.012f).grain = 0.35f }
        drawCurrent(r)

        for (p in game.board.allParts) drawPart(r, p)
        for (e in game.enemies) drawEnemy(r, e)
        for (b in game.blobs) {
            world(b.position, 0.38f, v)
            r.draw(models.pasteBall, m.setToTranslation(v).rotate(Vector3.Y, time * 400f).scale(0.09f, 0.09f, 0.09f), Color.WHITE, 0.08f)
        }
        drawBeams(r)
        for (p in particles) {
            val s = p.size * minOf(1f, p.life / p.maxLife * 2f)
            r.draw(p.mesh, m.setToTranslation(p.pos).scale(s, s, s), p.color, 0.1f, seed = p.maxLife * 10f).castShadow = p.gravity > 0
        }
        drawHover(r)
    }

    private fun drawTooth(r: ClayRenderer) {
        BoardSpace.center(game.level.tooth, 0.02f, v)
        val bob = sin(time * 2.2f) * 0.015f
        val squash = 1f - 0.12f * toothSquash * sin(toothSquash * 12f)
        val s = 1.25f
        val wide = s * (1f + (1f - squash) * 0.5f)
        m.setToTranslation(v.x, v.y + bob, v.z).scale(wide, s * squash, wide)
        val item = r.draw(models.tooth, m, Color.WHITE, 0.02f)
        if (toothFlash > 0) item.flash.set(toothFlash * 0.9f, 0f, 0f, 0f)
        // How healthy: the tooth yellows as it is bitten.
        val health = game.toothHealth.toFloat() / game.level.toothHealth
        item.color.set(1f, 0.86f + 0.14f * health, 0.7f + 0.3f * health, 1f)
    }

    /** Current shown as little bright beads running along the wires, + to −. */
    private fun drawCurrent(r: ClayRenderer) {
        val board = game.board
        for (w in board.allWires) {
            val amps = w.amps
            if (w.melted || abs(amps) < 0.02) continue
            val d = w.a.dirTo(w.b) ?: continue
            BoardSpace.wireEnd(board, w.a, d, v)
            BoardSpace.wireEnd(board, w.b, d.opposite, v2)
            val speed = MathUtils.clamp(abs(amps).toFloat() * 1.6f, 0.25f, 4f)
            val spacing = 0.34f
            val shift = (time * speed) % spacing
            val len = v.dst(v2)
            val radius = BoardSpace.radius(w.gauge)
            var s = shift
            while (s < len) {
                val t = if (amps > 0) s / len else 1f - s / len
                m.setToTranslation(
                    MathUtils.lerp(v.x, v2.x, t),
                    MathUtils.lerp(v.y, v2.y, t) + radius * 0.85f,
                    MathUtils.lerp(v.z, v2.z, t),
                ).scale(0.03f, 0.03f, 0.03f)
                r.draw(models.dot, m, Palette.surface, 0f).apply { emissive = 0.8f; castShadow = false }
                s += spacing
            }
        }
    }

    private fun drawPart(r: ClayRenderer, p: Part) {
        val look = looks[p] ?: return
        BoardSpace.center(p.cell, 0f, v)
        val baseYaw = BoardSpace.yaw(p.facing)
        val seed = (p.cell.x * 7 + p.cell.y * 13).toFloat()
        val dead = p.broken && p.kind.role == Role.MACHINE
        val paint = if (dead) tint.set(0.45f, 0.45f, 0.45f, 1f) else Color.WHITE
        m.setToTranslation(v).rotate(Vector3.Y, baseYaw)
        when (p.kind) {
            PartKind.BATTERY -> {
                val empty = p.charge <= 0.0
                r.draw(models.battery, m, if (empty) tint.set(0.6f, 0.6f, 0.6f, 1f) else Color.WHITE, 0.02f, seed)
                // A charge window on top.
                m2.set(m).translate(0f, 0.5f, 0.12f).scale(0.42f, 0.035f, 0.09f)
                r.draw(models.bar, m2, Palette.cream, 0f).castShadow = false
                if (!empty) {
                    val c = p.charge.toFloat()
                    m2.set(m).translate(-0.2f + 0.2f * c, 0.515f, 0.12f).scale(0.4f * c, 0.035f, 0.065f)
                    r.draw(models.bar, m2, if (c > 0.3f) Palette.yellow else Palette.coral, 0f).apply { emissive = 0.3f; castShadow = false }
                }
            }
            PartKind.BRUSH -> {
                r.draw(models.machineBase, m, paint, 0.02f, seed)
                r.draw(models.brushMotor, m, paint, 0.02f, seed)
                val scrub = if (look.scrub > 0 && !dead) sin(time * 55f) * 0.035f else 0f
                m2.setToTranslation(v).rotate(Vector3.Y, look.headYaw).translate(scrub, 0f, 0f)
                if (dead) m2.rotate(Vector3.X, 25f)
                r.draw(models.brush, m2, paint, 0.015f, seed)
            }
            PartKind.PASTE_CANNON -> {
                r.draw(models.machineBase, m, paint, 0.02f, seed)
                r.draw(models.cradle, m, paint, 0.02f, seed)
                m2.setToTranslation(v.x, 0.48f, v.z).rotate(Vector3.Y, look.headYaw).rotate(Vector3.Z, 14f).translate(-look.recoil * 0.06f, 0f, 0f)
                val squash = 1f + look.recoil * 0.12f
                m2.scale(1f / squash, squash, squash)
                r.draw(models.pasteTube, m2, paint, 0.015f, seed)
            }
            PartKind.LASER -> {
                r.draw(models.machineBase, m, paint, 0.02f, seed)
                r.draw(models.laserStand, m, paint, 0.02f, seed)
                m2.setToTranslation(v.x, 0.6f, v.z).rotate(Vector3.Y, look.headYaw).rotate(Vector3.Z, -8f)
                r.draw(models.laserHead, m2, paint, 0.015f, seed)
            }
            PartKind.SWITCH -> {
                r.draw(models.machineBase, m, Color.WHITE, 0.02f, seed)
                r.draw(models.switchPosts, m, Color.WHITE, 0.01f, seed)
                m2.set(m).translate(-0.2f, 0.25f, 0f).rotate(Vector3.Z, if (p.closed) 0f else 50f)
                r.draw(models.switchLever, m2, Color.WHITE, 0.01f, seed)
            }
            PartKind.FUSE -> {
                r.draw(models.machineBase, m, Color.WHITE, 0.02f, seed)
                r.draw(models.fuseWire, m, if (p.broken) Palette.charcoal else Palette.steel, 0.005f, seed)
                r.draw(models.fuse, m, tint.set(1f, 1f, 1f, 0.55f), 0.005f, seed).castShadow = false
            }
            PartKind.RESISTOR -> {
                r.draw(models.machineBase, m, Color.WHITE, 0.02f, seed)
                r.draw(models.resistor, m, Color.WHITE, 0.015f, seed)
            }
        }
    }

    private fun drawEnemy(r: ClayRenderer, e: Enemy) {
        val at = game.path.at(e.distance)
        val heading = game.path.heading(e.distance)
        val yaw = MathUtils.radiansToDegrees * atan2(-heading.y, heading.x).toFloat()
        // Stop-motion: the dough "boils" a few times a second, as in claymation.
        val boil = floor(time * 8f) + e.id * 3f
        val flash = enemyFlash[e.id] ?: 0f
        val x = at.x.toFloat()
        val z = at.y.toFloat()
        val ground = BoardSpace.ROAD_TOP
        val phase = time * e.kind.speed.toFloat() * 5.5f + e.id
        val item = when (e.kind) {
            EnemyKind.SUGAR_CUBE -> {
                val hop = abs(sin(phase)) * 0.12f
                val squash = 1f - 0.18f * (1f - abs(sin(phase))).let { it * it * it }
                m.setToTranslation(x, ground + 0.21f * squash + hop, z).rotate(Vector3.Y, yaw).scale(1f / squash.coerceAtLeast(0.85f), squash, 1f / squash.coerceAtLeast(0.85f))
                r.draw(models.sugarCube, m, Color.WHITE, 0.03f, boil).apply { grain = 1.1f; grainScale = 6f }
            }
            EnemyKind.GUMMY_BEAR -> {
                val waddle = sin(phase) * 9f
                m.setToTranslation(x, ground + abs(sin(phase)) * 0.03f, z).rotate(Vector3.Y, yaw).rotate(Vector3.X, waddle).scale(1.3f, 1.3f, 1.3f)
                val body = r.draw(models.gummyBear, m, gummyColor(e.id), 0.025f, boil)
                r.draw(models.gummyFace, m, Color.WHITE, 0.01f, boil)
                body
            }
            EnemyKind.LOLLIPOP -> {
                val hop = abs(sin(phase * 0.6f)) * 0.2f
                m.setToTranslation(x, ground + hop, z).rotate(Vector3.Y, yaw).rotate(Vector3.X, sin(phase * 0.6f) * 6f).scale(1.35f, 1.35f, 1.35f)
                r.draw(models.lollipop, m, Color.WHITE, 0.03f, boil)
            }
        }
        if (flash > 0) item.flash.set(flash * 2f, flash * 2f, flash * 2f, 0f)
    }

    private fun drawBeams(r: ClayRenderer) {
        for ((p, e) in game.beams) {
            val at = game.path.at(e.distance)
            BoardSpace.center(p.cell, 0.6f, v)
            v2.set(at.x.toFloat(), BoardSpace.ROAD_TOP + 0.2f, at.y.toFloat())
            val dir = v2.cpy().sub(v)
            val len = dir.len()
            dir.nor()
            // Start the beam at the lens.
            v.mulAdd(dir, 0.3f)
            val width = 0.045f + 0.012f * sin(time * 40f)
            m.setToTranslation(v)
            // Turn the rod's +y onto the beam direction.
            val axis = Vector3.Y.cpy().crs(dir)
            val angle = MathUtils.radiansToDegrees * kotlin.math.acos(MathUtils.clamp(Vector3.Y.dot(dir), -1f, 1f))
            if (axis.len2() > 1e-6f) m.rotate(axis.nor(), angle)
            m.scale(width, len - 0.3f, width)
            r.draw(models.rod, m, tint.set(1f, 0.97f, 0.88f, 0.85f), 0f).apply { emissive = 1f; castShadow = false }
            m.scale(2.2f, 1f, 2.2f)
            r.draw(models.rod, m, tint.set(0.55f, 0.9f, 0.86f, 0.3f), 0f).apply { emissive = 1f; castShadow = false }
        }
    }

    private fun drawHover(r: ClayRenderer) {
        selected?.let { c ->
            BoardSpace.center(c, 0.01f, v)
            m.setToTranslation(v).scale(0.98f, 0.018f, 0.98f)
            r.draw(models.bar, m, tint.set(Palette.turquoise).also { it.a = 0.55f }, 0f).castShadow = false
        }
        val c = hover ?: return
        if (!game.board.inside(c)) return
        BoardSpace.center(c, 0.012f, v)
        m.setToTranslation(v).scale(0.92f, 0.02f, 0.92f)
        r.draw(models.bar, m, if (hoverOk) tint.set(Palette.yellow).also { it.a = 0.45f } else tint.set(Palette.coral).also { it.a = 0.45f }, 0f).castShadow = false
    }

    // ------------------------------------------------------------------ helpers

    private fun world(p: Vec2, y: Float, out: Vector3) = out.set(p.x.toFloat(), y, p.y.toFloat())

    private fun enemyPos(e: Enemy, out: Vector3): Vector3 = world(game.path.at(e.distance), 0.3f, out)

    private fun gummyColor(id: Int): Color = when (id % 4) {
        0 -> Palette.coral
        1 -> Palette.yellow
        2 -> Palette.grape
        else -> Palette.turquoise
    }

    private fun candyColor(e: Enemy): Color = when (e.kind) {
        EnemyKind.SUGAR_CUBE -> Palette.sugar
        EnemyKind.GUMMY_BEAR -> gummyColor(e.id)
        EnemyKind.LOLLIPOP -> Palette.pink
    }

    override fun dispose() {
        boardMesh.dispose()
        wireMesh?.dispose()
    }
}
