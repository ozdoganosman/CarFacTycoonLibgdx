package com.toyquaise.toothfort.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.toothfort.Palette
import com.toyquaise.toothfort.logic.Vec2
import com.toyquaise.toothfort.logic.board.BoardEvent
import com.toyquaise.toothfort.logic.board.Part
import com.toyquaise.toothfort.logic.board.PartKind
import com.toyquaise.toothfort.logic.board.Role
import com.toyquaise.toothfort.logic.board.Wire
import com.toyquaise.toothfort.logic.board.WireGauge
import com.toyquaise.toothfort.logic.game.Enemy
import com.toyquaise.toothfort.logic.game.EnemyKind
import com.toyquaise.toothfort.logic.game.Game
import com.toyquaise.toothfort.logic.game.GameEvent
import com.toyquaise.toothfort.logic.game.PropKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Squash and stretch: a damped spring around 0. Positive stretches up, negative squashes down;
 * [scaleY] and [scaleXZ] keep the volume, as dough does.
 */
class Squish {
    var x = 0f
    var v = 0f
    var target = 0f

    fun update(dt: Float) {
        // A few small steps: stiff springs and long frames do not mix.
        var left = dt
        while (left > 0f) {
            val h = minOf(left, 1f / 120f)
            val a = -170f * (x - target) - 9f * v
            v += a * h
            x += v * h
            left -= h
        }
        x = x.coerceIn(-0.6f, 0.6f)
    }

    val scaleY: Float get() = 1f + x
    val scaleXZ: Float get() = 1f / sqrt(scaleY.coerceAtLeast(0.3f))
}

/**
 * Draws a game in clay: the kitchen counter and its things, the syrup trail, the tooth, parts and
 * their wires, candies, paste and beams, and little bursts of dough when things happen. Holds
 * only looks (turning heads, squishes, particles); the game lives in [Game].
 */
class WorldView(private val models: Models, private val game: Game) : Disposable {
    private val level = game.level
    private val counter: Mesh = models.counter(level.width.toFloat(), level.height.toFloat())
    private val wall: Mesh = models.wall(level.width.toFloat())
    private val trail: Mesh = Kitchen.trail(level).toMesh()
    private var wireMesh: Mesh? = null
    private var wireRevision = -1
    var time = 0f
        private set

    private class PartLook(var headYaw: Float, val squish: Squish = Squish(), var recoil: Float = 0f, var scrub: Float = 0f)
    private val looks = HashMap<Part, PartLook>()
    private val enemySquish = HashMap<Int, Squish>()
    private val enemyFlash = HashMap<Int, Float>()
    private val toothSquish = Squish()
    private var toothFlash = 0f

    private class Particle(
        val pos: Vector3, val vel: Vector3, var life: Float, val maxLife: Float,
        val color: Color, val size: Float, val gravity: Float,
    )
    private val particles = ArrayList<Particle>()
    private val rnd = Random(11)

    /** Floating notes ("+3") the overlay draws: world position, text, colour, age. */
    class Floater(val pos: Vector3, val text: String, val color: Color, var age: Float = 0f)
    val floaters = ArrayList<Floater>()

    /** The part being placed, before the finger lets go. */
    class Ghost(var kind: PartKind, var pos: Vec2, var angle: Double, var ok: Boolean)
    var ghost: Ghost? = null

    /** The wire being drawn, as the finger has drawn it. */
    var wirePreview: List<Vec2>? = null
    var wirePreviewGauge = WireGauge.THIN

    var selected: Part? = null

    /** The part under the player's finger: squashed until it is let go. */
    var pressed: Part? = null
        set(value) {
            field?.let { looks[it]?.squish?.target = 0f }
            field = value
            value?.let { looks[it]?.squish?.target = -0.22f }
        }

    private val m = Matrix4()
    private val m2 = Matrix4()
    private val root = Matrix4()
    private val v = Vector3()
    private val v2 = Vector3()
    private val tint = Color()

    // ------------------------------------------------------------------ events

    fun onEvent(e: GameEvent) {
        when (e) {
            is GameEvent.Hit -> {
                enemyFlash[e.enemy.id] = 0.12f
                enemySquish.getOrPut(e.enemy.id) { Squish() }.v -= 2.5f
                looks[e.by]?.let { if (e.by.kind == PartKind.BRUSH) it.scrub = 0.25f }
                if (e.by.kind == PartKind.BRUSH) burst(enemyPos(e.enemy, v), 2, Palette.enamel, 0.035f, 0.9f)
            }
            is GameEvent.Shot -> looks[e.by]?.let { it.recoil = 1f; it.squish.v -= 3.5f }
            is GameEvent.Splat -> burst(Kitchen.world(e.at, 0.2f, v), 10, Palette.enamel, 0.05f, 1.6f, Palette.coral)
            is GameEvent.Killed -> {
                burst(Kitchen.world(e.at, 0.25f, v), 14, candyColor(e.enemy), 0.06f, 2.2f)
                floaters += Floater(Kitchen.world(e.at, 0.6f), "+${e.enemy.kind.reward}", Palette.yellow)
                enemyFlash.remove(e.enemy.id)
                enemySquish.remove(e.enemy.id)
            }
            is GameEvent.Bit -> {
                toothFlash = 0.35f
                toothSquish.v -= 7f
                floaters += Floater(Kitchen.world(level.tooth, 1.9f), "−${e.damage}", Palette.coral)
            }
            is GameEvent.Wiring -> when (val b = e.event) {
                is BoardEvent.Burnt -> {
                    Kitchen.world(b.part.pos, 0.45f, v)
                    burst(v, 14, Palette.yellow, 0.04f, 2.5f)
                    smoke(v, 8)
                    looks[b.part]?.squish?.v = -5f
                }
                is BoardEvent.Melted -> {
                    val course = game.board.course(b.wire)
                    Kitchen.world(course[course.size / 2], 0.1f, v)
                    burst(v, 12, Palette.yellow, 0.035f, 2.2f, Palette.coral)
                    smoke(v, 5)
                }
                is BoardEvent.Blown -> {
                    Kitchen.world(b.part.pos, 0.25f, v)
                    burst(v, 8, Palette.yellow, 0.03f, 1.8f)
                }
                is BoardEvent.Depleted -> looks[b.part]?.squish?.v = -2f
            }
            is GameEvent.WaveStarted, is GameEvent.WaveCleared, GameEvent.Won, GameEvent.Lost -> {}
        }
    }

    /** A little poke: the part jiggles (used when it is tapped, turned or switched). */
    fun poke(part: Part) {
        looks[part]?.squish?.v = -3f
    }

    private fun burst(at: Vector3, n: Int, color: Color, size: Float, speed: Float, second: Color? = null) {
        repeat(n) { i ->
            val dir = Vector3(rnd.nextFloat() - 0.5f, rnd.nextFloat() * 0.9f + 0.4f, rnd.nextFloat() - 0.5f).nor()
            val s = speed * (0.5f + rnd.nextFloat() * 0.6f)
            val c = if (second != null && i % 3 == 0) second else color
            val life = 0.45f + rnd.nextFloat() * 0.35f
            particles += Particle(at.cpy(), dir.scl(s), life, life, c, size * (0.7f + rnd.nextFloat() * 0.6f), 7f)
        }
    }

    private fun smoke(at: Vector3, n: Int) {
        repeat(n) {
            val life = 1.0f + rnd.nextFloat() * 0.8f
            val vel = Vector3((rnd.nextFloat() - 0.5f) * 0.3f, 0.6f + rnd.nextFloat() * 0.4f, (rnd.nextFloat() - 0.5f) * 0.3f)
            particles += Particle(at.cpy(), vel, life, life, Palette.smoke, 0.07f + rnd.nextFloat() * 0.05f, -0.2f)
        }
    }

    // ------------------------------------------------------------------ frame

    fun update(dt: Float) {
        time += dt
        toothFlash = maxOf(0f, toothFlash - dt)
        toothSquish.update(dt)
        enemyFlash.entries.removeAll { it.setValue(it.value - dt); it.value <= 0f }
        enemySquish.values.forEach { it.update(dt) }
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

        looks.keys.retainAll(game.board.parts.toSet())
        for (p in game.board.parts) {
            // A new part lands like a dropped ball of dough: flat, then up, then settling.
            val look = looks.getOrPut(p) { PartLook(p.angle.toFloat(), Squish().also { s -> s.x = -0.5f; s.v = 2f }) }
            look.squish.update(dt)
            look.recoil = maxOf(0f, look.recoil - dt * 4f)
            look.scrub = maxOf(0f, look.scrub - dt)
            val want = aimOf(p)
            if (want != null && !p.broken && p.performance > 0) {
                var diff = want - look.headYaw
                while (diff > 180f) diff -= 360f
                while (diff < -180f) diff += 360f
                look.headYaw += MathUtils.clamp(diff, -480f * dt, 480f * dt)
            }
        }
    }

    /** Yaw toward the candy this machine is (or would be) aiming at. */
    private fun aimOf(p: Part): Float? {
        val spec = p.kind.machine ?: return null
        val e = game.beams[p] ?: game.target(p.pos, spec.range) ?: return null
        val at = game.path.at(e.distance)
        return MathUtils.radiansToDegrees * atan2(-(at.y - p.pos.y), at.x - p.pos.x).toFloat()
    }

    fun draw(r: ClayRenderer) {
        r.draw(counter, m.idt(), Color.WHITE, 0f).apply { grain = 0.45f; grainScale = 2.5f }
        // The wall stands behind the light's way in; it would only darken the counter.
        r.draw(wall, m.setToTranslation(0f, 0f, -0.75f), Color.WHITE, 0f).apply { grain = 0.4f; castShadow = false }
        r.draw(trail, m.idt(), Color.WHITE, 0.015f).apply { grain = 0.25f }
        drawProps(r)
        drawJar(r)
        drawTooth(r)

        if (wireRevision != game.board.revision || wireMesh == null) {
            wireMesh?.dispose()
            wireMesh = Kitchen.wires(game.board, level).toMesh()
            wireRevision = game.board.revision
            beads.clear()
        }
        wireMesh?.let { r.draw(it, m.idt(), Color.WHITE, 0.008f).grain = 0.35f }
        for (j in game.board.junctions) {
            r.draw(models.clip, m.setToTranslation(j.pos.x.toFloat(), 0f, j.pos.y.toFloat()), Color.WHITE, 0.01f)
        }
        drawCurrent(r)

        for (p in game.board.parts) drawPart(r, p, look = looks[p] ?: continue)
        for (e in game.enemies) drawEnemy(r, e)
        for (b in game.blobs) {
            Kitchen.world(b.position, 0.55f, v)
            r.draw(models.pasteBall, m.setToTranslation(v).rotate(Vector3.Y, time * 400f).scale(0.09f, 0.09f, 0.09f), Color.WHITE, 0.08f)
        }
        drawBeams(r)
        for (p in particles) {
            val s = p.size * minOf(1f, p.life / p.maxLife * 2f)
            r.draw(models.dot, m.setToTranslation(p.pos).scale(s, s, s), p.color, 0.1f, seed = p.maxLife * 10f).castShadow = p.gravity > 0
        }
        drawSelection(r)
        drawGhost(r)
        drawWirePreview(r)
    }

    private fun drawProps(r: ClayRenderer) {
        for ((i, prop) in level.props.withIndex()) {
            val mesh = when (prop.kind) {
                PropKind.CUTTING_BOARD -> models.cuttingBoard
                PropKind.PLATE -> models.plate
                PropKind.MUG -> models.mug
                PropKind.FRUIT_BOWL -> models.fruitBowl
                PropKind.ROLLING_PIN -> models.rollingPin
                PropKind.SALT_SHAKER -> models.saltShaker
            }
            Kitchen.world(prop.pos, 0f, v)
            val radius = prop.circles.maxOf { it.first.distanceTo(prop.pos) + it.second }.toFloat()
            shadow(r, v.x, v.z, radius * 1.05f, 0.28f)
            r.draw(mesh, m.setToTranslation(v).rotate(Vector3.Y, prop.angle.toFloat()), Color.WHITE, 0.015f, seed = i * 3f)
        }
    }

    private fun drawJar(r: ClayRenderer) {
        val h = level.path.heading(0.3)
        val yaw = MathUtils.radiansToDegrees * atan2(-h.y, h.x).toFloat()
        // The jar lies behind the trail's start, its mouth spilling candies onto it.
        Kitchen.world(level.jar - h * 0.45, 0f, v)
        shadow(r, v.x, v.z, 0.6f, 0.3f)
        m.setToTranslation(v).rotate(Vector3.Y, yaw)
        r.draw(models.jarCandies, m, Color.WHITE, 0.015f)
        r.draw(models.jarGlass, m, tint.set(1f, 1f, 1f, 0.42f), 0f).apply { castShadow = false; grain = 0.05f }
    }

    private fun drawTooth(r: ClayRenderer) {
        Kitchen.world(level.tooth, 0f, v)
        r.draw(models.gum, m.setToTranslation(v).scale(1.3f, 1.3f, 1.3f), Color.WHITE, 0.01f)
        shadow(r, v.x, v.z, 0.9f, 0.35f)
        val bob = sin(time * 2.2f) * 0.015f
        val s = 1.3f
        // Leaning back a little so its face looks up at the camera.
        m.setToTranslation(v.x, v.y + 0.12f + bob, v.z).scale(s * toothSquish.scaleXZ, s * toothSquish.scaleY, s * toothSquish.scaleXZ).rotate(Vector3.X, -20f)
        val item = r.draw(models.tooth, m, Color.WHITE, 0.01f)
        if (toothFlash > 0) item.flash.set(toothFlash * 0.9f, 0f, 0f, 0f)
        // The tooth yellows as it is bitten.
        val health = game.toothHealth.toFloat() / level.toothHealth
        item.color.set(1f, 0.86f + 0.14f * health, 0.7f + 0.3f * health, 1f)
    }

    /** A soft dark patch on the ground under something, so it sits instead of floating. */
    private fun shadow(r: ClayRenderer, x: Float, z: Float, radius: Float, strength: Float, y: Float = 0.03f) {
        m2.setToTranslation(x, y, z).scale(radius, 1f, radius)
        r.draw(models.contactShadow, m2, tint.set(Palette.ink.r, Palette.ink.g, Palette.ink.b, strength), 0f).apply {
            castShadow = false
            grain = 0f
        }
    }

    // Beads of current: points along each wire's course in 3D, rebuilt when the wiring changes.
    private val beads = HashMap<Int, List<Vector3>>()

    private fun courseOf(w: Wire): List<Vector3> = beads.getOrPut(w.id) {
        val r = Kitchen.radius(w.gauge)
        val course = game.board.course(w)
        course.mapIndexed { i, p ->
            val end = i == 0 || i == course.lastIndex
            Vector3(p.x.toFloat(), (if (end) 0.1f else Kitchen.wireHeight(level, p, r)) + r * 0.85f, p.y.toFloat())
        }
    }

    /** Current shown as little bright beads running along the wires, + to −. */
    private fun drawCurrent(r: ClayRenderer) {
        for (w in game.board.wires) {
            val amps = w.amps
            if (w.melted || abs(amps) < 0.02) continue
            val pts = courseOf(w)
            val lengths = FloatArray(pts.size)
            for (i in 1 until pts.size) lengths[i] = lengths[i - 1] + pts[i].dst(pts[i - 1])
            val total = lengths.last()
            if (total < 1e-3f) continue
            val speed = MathUtils.clamp(abs(amps).toFloat() * 1.6f, 0.25f, 4f)
            val spacing = 0.3f
            var s = (time * speed) % spacing
            while (s < total) {
                val d = if (amps > 0) s else total - s
                var i = 1
                while (i < pts.size - 1 && lengths[i] < d) i++
                val t = ((d - lengths[i - 1]) / (lengths[i] - lengths[i - 1]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
                v.set(pts[i - 1]).lerp(pts[i], t)
                r.draw(models.dot, m.setToTranslation(v).scale(0.03f, 0.03f, 0.03f), Palette.surface, 0f).apply { emissive = 0.8f; castShadow = false }
                s += spacing
            }
        }
    }

    private fun drawPart(r: ClayRenderer, p: Part, look: PartLook) {
        val x = p.pos.x.toFloat()
        val z = p.pos.y.toFloat()
        shadow(r, x, z, 0.55f, 0.32f)
        val seed = p.id * 7f
        val dead = p.broken && p.kind.role == Role.MACHINE
        val paint = if (dead) tint.set(0.45f, 0.45f, 0.45f, 1f) else Color.WHITE
        val sq = look.squish
        root.setToTranslation(x, 0f, z).scale(sq.scaleXZ, sq.scaleY, sq.scaleXZ)
        m.set(root).rotate(Vector3.Y, p.angle.toFloat())
        when (p.kind) {
            PartKind.BATTERY -> {
                val empty = p.charge <= 0.0
                r.draw(models.battery, m, if (empty) tint.set(0.6f, 0.6f, 0.6f, 1f) else Color.WHITE, 0f, seed)
                // A charge window on top.
                m2.set(m).translate(0f, 0.6f, 0.13f).scale(0.44f, 0.04f, 0.1f)
                r.draw(models.bar, m2, Palette.cream, 0f).castShadow = false
                if (!empty) {
                    val c = p.charge.toFloat()
                    m2.set(m).translate(-0.21f + 0.21f * c, 0.615f, 0.13f).scale(0.42f * c, 0.04f, 0.075f)
                    r.draw(models.bar, m2, if (c > 0.3f) Palette.yellow else Palette.coral, 0f).apply { emissive = 0.3f; castShadow = false }
                }
            }
            PartKind.BRUSH -> {
                r.draw(models.machineBase, m, paint, 0f, seed)
                m2.set(m).translate(0f, Models.PEDESTAL - 0.08f, 0f)
                r.draw(models.brushMotor, m2, paint, 0f, seed)
                val scrub = if (look.scrub > 0 && !dead) sin(time * 55f) * 0.035f else 0f
                m2.set(root).translate(0f, Models.PEDESTAL - 0.08f, 0f).rotate(Vector3.Y, look.headYaw).translate(scrub, 0f, 0f)
                if (dead) m2.rotate(Vector3.X, 25f)
                r.draw(models.brush, m2, paint, 0f, seed)
            }
            PartKind.PASTE_CANNON -> {
                r.draw(models.machineBase, m, paint, 0f, seed)
                m2.set(m).translate(0f, Models.PEDESTAL - 0.12f, 0f)
                r.draw(models.cradle, m2, paint, 0f, seed)
                m2.set(root).translate(0f, 0.48f + Models.PEDESTAL - 0.12f, 0f).rotate(Vector3.Y, look.headYaw).rotate(Vector3.Z, 14f)
                    .translate(-look.recoil * 0.06f, 0f, 0f)
                val squash = 1f + look.recoil * 0.15f
                m2.scale(1f / squash, squash, squash)
                r.draw(models.pasteTube, m2, paint, 0f, seed)
            }
            PartKind.LASER -> {
                r.draw(models.machineBase, m, paint, 0f, seed)
                m2.set(m).translate(0f, Models.PEDESTAL - 0.12f, 0f)
                r.draw(models.laserStand, m2, paint, 0f, seed)
                m2.set(root).translate(0f, 0.6f + Models.PEDESTAL - 0.12f, 0f).rotate(Vector3.Y, look.headYaw).rotate(Vector3.Z, -8f)
                r.draw(models.laserHead, m2, paint, 0f, seed)
            }
            PartKind.SWITCH -> {
                r.draw(models.componentBase, m, Color.WHITE, 0f, seed)
                r.draw(models.switchPosts, m, Color.WHITE, 0f, seed)
                m2.set(m).translate(-0.2f, 0.25f, 0f).rotate(Vector3.Z, if (p.closed) 0f else 50f)
                r.draw(models.switchLever, m2, Color.WHITE, 0f, seed)
            }
            PartKind.FUSE -> {
                r.draw(models.componentBase, m, Color.WHITE, 0f, seed)
                r.draw(models.fuseCaps, m, Color.WHITE, 0f, seed)
                r.draw(models.fuseWire, m, if (p.broken) Palette.charcoal else Palette.steel, 0f, seed)
                r.draw(models.fuse, m, tint.set(1f, 1f, 1f, 0.5f), 0f, seed).apply { castShadow = false; grain = 0.05f }
            }
            PartKind.RESISTOR -> {
                r.draw(models.componentBase, m, Color.WHITE, 0f, seed)
                r.draw(models.resistor, m, Color.WHITE, 0f, seed)
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
        val sq = enemySquish[e.id]
        val x = at.x.toFloat()
        val z = at.y.toFloat()
        val ground = Kitchen.TRAIL_TOP
        val phase = time * e.kind.speed.toFloat() * 5.5f + e.id
        shadow(r, x, z, if (e.kind == EnemyKind.SUGAR_CUBE) 0.32f else 0.38f, 0.32f, ground + 0.01f)
        val sy = sq?.scaleY ?: 1f
        val sxz = sq?.scaleXZ ?: 1f
        val item = when (e.kind) {
            EnemyKind.SUGAR_CUBE -> {
                val hop = abs(sin(phase)) * 0.12f
                // Lands squashed, takes off stretched.
                val land = 1f - 0.22f * (1f - abs(sin(phase))).let { it * it * it }
                m.setToTranslation(x, ground + hop, z).rotate(Vector3.Y, yaw)
                    .scale(sxz / land.coerceAtLeast(0.8f), sy * land, sxz / land.coerceAtLeast(0.8f)).translate(0f, 0.24f, 0f)
                r.draw(models.sugarCube, m, Color.WHITE, 0.02f, boil).apply { grain = 1.1f; grainScale = 6f }
            }
            EnemyKind.GUMMY_BEAR -> {
                val waddle = sin(phase) * 9f
                // Jelly: a quick wobble on top of the walk.
                val jelly = 1f + sin(time * 13f + e.id) * 0.04f
                m.setToTranslation(x, ground + abs(sin(phase)) * 0.03f, z).rotate(Vector3.Y, yaw).rotate(Vector3.X, waddle)
                    .scale(1.5f * sxz / jelly, 1.5f * sy * jelly, 1.5f * sxz / jelly)
                val body = r.draw(models.gummyBear, m, gummyColor(e.id), 0.02f, boil)
                r.draw(models.gummyFace, m, Color.WHITE, 0.005f, boil)
                body
            }
            EnemyKind.LOLLIPOP -> {
                val hop = abs(sin(phase * 0.6f)) * 0.2f
                m.setToTranslation(x, ground + hop, z).rotate(Vector3.Y, yaw).rotate(Vector3.X, sin(phase * 0.6f) * 6f)
                    .scale(1.5f * sxz, 1.5f * sy, 1.5f * sxz)
                r.draw(models.lollipop, m, Color.WHITE, 0.02f, boil)
            }
        }
        if (flash > 0) item.flash.set(flash * 2f, flash * 2f, flash * 2f, 0f)
    }

    private fun drawBeams(r: ClayRenderer) {
        for ((p, e) in game.beams) {
            val at = game.path.at(e.distance)
            Kitchen.world(p.pos, 0.6f + Models.PEDESTAL - 0.12f, v)
            v2.set(at.x.toFloat(), Kitchen.TRAIL_TOP + 0.25f, at.y.toFloat())
            val dir = v2.cpy().sub(v)
            val len = dir.len()
            dir.nor()
            v.mulAdd(dir, 0.3f)
            val width = 0.045f + 0.012f * sin(time * 40f)
            m.setToTranslation(v)
            val axis = Vector3.Y.cpy().crs(dir)
            val angle = MathUtils.radiansToDegrees * kotlin.math.acos(MathUtils.clamp(Vector3.Y.dot(dir), -1f, 1f))
            if (axis.len2() > 1e-6f) m.rotate(axis.nor(), angle)
            m.scale(width, len - 0.3f, width)
            r.draw(models.rod, m, tint.set(1f, 0.97f, 0.88f, 0.85f), 0f).apply { emissive = 1f; castShadow = false }
            m.scale(2.2f, 1f, 2.2f)
            r.draw(models.rod, m, tint.set(0.55f, 0.9f, 0.86f, 0.3f), 0f).apply { emissive = 1f; castShadow = false }
        }
    }

    private fun drawSelection(r: ClayRenderer) {
        val p = selected ?: return
        if (p !in game.board.parts) { selected = null; return }
        // A soft glowing ring on the counter around the selected part.
        m.setToTranslation(p.pos.x.toFloat(), 0.04f, p.pos.y.toFloat()).scale(0.62f, 1f, 0.62f)
        r.draw(models.contactShadow, m, tint.set(Palette.yellow).also { it.a = 0.75f }, 0f).apply { castShadow = false; grain = 0f; emissive = 1f }
    }

    private fun drawGhost(r: ClayRenderer) {
        val g = ghost ?: return
        val x = g.pos.x.toFloat()
        val z = g.pos.y.toFloat()
        m.setToTranslation(x, 0.04f, z).scale(0.6f, 1f, 0.6f)
        r.draw(models.contactShadow, m, tint.set(if (g.ok) Palette.leaf else Palette.coral).also { it.a = 0.8f }, 0f).apply { castShadow = false; grain = 0f; emissive = 1f }
        val mesh = when (g.kind) {
            PartKind.BATTERY -> models.battery
            PartKind.SWITCH, PartKind.FUSE, PartKind.RESISTOR -> models.componentBase
            else -> models.machineBase
        }
        // Hovering a little above the counter until it is dropped.
        m.setToTranslation(x, 0.12f + sin(time * 6f) * 0.02f, z).rotate(Vector3.Y, g.angle.toFloat())
        r.draw(mesh, m, if (g.ok) tint.set(1f, 1f, 1f, 0.7f) else tint.set(1f, 0.55f, 0.5f, 0.6f), 0f).apply { castShadow = false }
    }

    private fun drawWirePreview(r: ClayRenderer) {
        val pts = wirePreview ?: return
        if (pts.size < 2) return
        val c = if (wirePreviewGauge == WireGauge.THICK) Palette.coral else Palette.yellow
        val rad = Kitchen.radius(wirePreviewGauge)
        var carry = 0.0
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val len = a.distanceTo(b)
            var s = carry
            while (s < len) {
                val p = a + (b - a) * (s / len)
                Kitchen.world(p, Kitchen.wireHeight(level, p, rad), v)
                r.draw(models.dot, m.setToTranslation(v).scale(rad * 1.1f, rad * 1.1f, rad * 1.1f), c, 0f).apply { castShadow = false; emissive = 0.3f }
                s += 0.07
            }
            carry = s - len
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun enemyPos(e: Enemy, out: Vector3): Vector3 = Kitchen.world(game.path.at(e.distance), 0.35f, out)

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
        counter.dispose()
        wall.dispose()
        trail.dispose()
        wireMesh?.dispose()
    }
}
