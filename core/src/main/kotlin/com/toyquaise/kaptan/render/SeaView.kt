package com.toyquaise.kaptan.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.kaptan.Palette
import com.toyquaise.kaptan.logic.Check
import com.toyquaise.kaptan.logic.Leg
import com.toyquaise.kaptan.logic.Vec
import com.toyquaise.kaptan.logic.Voyage
import com.toyquaise.kaptan.ui.Strings
import kotlin.math.abs
import kotlin.math.atan2
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

    fun update(dt: Float) {
        var left = dt
        while (left > 0f) {
            val h = minOf(left, 1f / 120f)
            val a = -170f * x - 9f * v
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
 * Draws a voyage in clay: the squared sea, rocks, the harbor, the boat sailing its moves, the
 * route behind it as arrows, and the arrows of the move being planned. Holds only looks (where
 * the boat is drawn, springs, particles); the rules live in [Voyage].
 */
class SeaView(private val models: Models, val voyage: Voyage) : Disposable {
    private val level = voyage.level
    private val seaMesh: Mesh = models.sea(level.width, level.height)
    private val linesX: Mesh = models.gridLines(level.width, level.height, vertical = true)
    private val linesY: Mesh = models.gridLines(level.width, level.height, vertical = false)
    var time = 0f
        private set

    /** The move being planned, drawn as arrows from the boat; null for none. */
    var preview: Check? = null

    /** Labels the overlay writes over the 3D scene this frame: vectors beside their arrows. */
    class Tag(val pos: Vector3, val text: String, val color: Color)
    val tags = ArrayList<Tag>()

    // ------------------------------------------------------------------ the boat

    private val boatAt = Vector2(level.start.x.toFloat(), level.start.y.toFloat())
    private var boatYaw = yawOf(level.harbor - level.start)
    private val boatSquish = Squish()

    private class Sail(val leg: Leg, val duration: Float) {
        var t = 0f
    }
    private var sail: Sail? = null
    private var shownLegs = 0
    private var shownRevision = voyage.revision

    /** True while the boat is still sailing a move. */
    val busy: Boolean get() = sail != null

    /** Seconds since the boat reached the harbor, or −1 before. */
    var arrivedFor = -1f
        private set

    // ------------------------------------------------------------------ particles

    private class Particle(
        val pos: Vector3, val vel: Vector3, var life: Float, val maxLife: Float,
        val color: Color, val size: Float, val gravity: Float,
    )
    private val particles = ArrayList<Particle>()
    private val rnd = Random(11)
    private var wakeTimer = 0f

    private val m = Matrix4()
    private val v = Vector3()
    private val tint = Color()

    init {
        // A voyage already under way (a demo, a screenshot) is shown where it is, without sailing.
        shownLegs = voyage.legs.size
        boatAt.set(voyage.position.x.toFloat(), voyage.position.y.toFloat())
        voyage.legs.lastOrNull()?.let { boatYaw = headingOf(it) }
        if (voyage.arrived) arrivedFor = 10f
    }

    // ------------------------------------------------------------------ frame

    fun update(dt: Float) {
        time += dt
        follow()
        boatSquish.update(dt)
        val s = sail
        if (s != null) {
            s.t += dt
            val k = Interpolation.smooth.apply((s.t / s.duration).coerceIn(0f, 1f))
            val leg = s.leg
            boatAt.set(
                leg.from.x + (leg.to.x - leg.from.x) * k,
                leg.from.y + (leg.to.y - leg.from.y) * k,
            )
            // The bow points where the engines push (the card), even when the current drifts the boat.
            turnTo(headingOf(leg), dt)
            wakeTimer -= dt
            if (wakeTimer <= 0f && s.t < s.duration) {
                wakeTimer = 0.045f
                wake()
            }
            if (s.t >= s.duration) {
                sail = null
                boatAt.set(leg.to.x.toFloat(), leg.to.y.toFloat())
                boatSquish.v -= 2.5f
                if (shownLegs == voyage.legs.size && voyage.arrived) arrive()
            }
        }
        if (arrivedFor >= 0f) arrivedFor += dt
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vel.y -= p.gravity * dt
            p.pos.mulAdd(p.vel, dt)
            if (p.pos.y < 0.02f && p.gravity > 0f) { p.life = minOf(p.life, 0.12f); p.vel.setZero() }
        }
    }

    /** Catches up with the voyage: starts sailing a new move, or jumps back after undo and reset. */
    private fun follow() {
        if (voyage.revision == shownRevision && sail == null && shownLegs == voyage.legs.size) return
        shownRevision = voyage.revision
        val legs = voyage.legs
        if (legs.size < shownLegs || (legs.size == shownLegs && sail == null)) {
            // Undone or reset: no sailing back, the boat is simply there again.
            sail = null
            shownLegs = legs.size
            boatAt.set(voyage.position.x.toFloat(), voyage.position.y.toFloat())
            boatYaw = legs.lastOrNull()?.let(::headingOf) ?: yawOf(level.harbor - level.start)
            boatSquish.v -= 2f
            arrivedFor = if (voyage.arrived) arrivedFor else -1f
            return
        }
        if (sail == null && legs.size > shownLegs) {
            val leg = legs[shownLegs++]
            sail = Sail(leg, 0.45f + leg.length.toFloat() * 0.13f)
        }
    }

    private fun arrive() {
        arrivedFor = 0f
        boatSquish.v = 4f
        val colors = listOf(Palette.yellow, Palette.coral, Palette.enamel, Palette.turquoise, Palette.pink)
        world(level.harbor.x.toFloat(), level.harbor.y.toFloat(), 0.3f, v)
        repeat(36) { i ->
            val dir = Vector3(rnd.nextFloat() - 0.5f, rnd.nextFloat() * 0.8f + 0.7f, rnd.nextFloat() - 0.5f).nor()
            val life = 0.8f + rnd.nextFloat() * 0.6f
            particles += Particle(v.cpy(), dir.scl(2.2f + rnd.nextFloat() * 1.4f), life, life, colors[i % colors.size], 0.035f + rnd.nextFloat() * 0.02f, 6f)
        }
    }

    private fun wake() {
        val back = Vector2(MathUtils.cosDeg(boatYaw), MathUtils.sinDeg(boatYaw)).scl(-0.3f)
        repeat(2) {
            val side = (rnd.nextFloat() - 0.5f) * 0.2f
            world(boatAt.x + back.x - back.y * side, boatAt.y + back.y + back.x * side, 0.03f, v)
            val life = 0.6f + rnd.nextFloat() * 0.3f
            val vel = Vector3((rnd.nextFloat() - 0.5f) * 0.2f, 0.35f, (rnd.nextFloat() - 0.5f) * 0.2f)
            particles += Particle(v.cpy(), vel, life, life, Palette.enamel, 0.03f + rnd.nextFloat() * 0.015f, 1.2f)
        }
    }

    private fun turnTo(yaw: Float, dt: Float) {
        var diff = yaw - boatYaw
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        boatYaw += MathUtils.clamp(diff, -540f * dt, 540f * dt)
    }

    /** Which way the bow points on a move: along the card (or the pair's diagonal), not the drift. */
    private fun headingOf(leg: Leg): Float {
        val push = leg.vectors.fold(Vec.ZERO) { a, b -> a + b }
        return yawOf(if (push.isZero) leg.delta else push)
    }

    private fun yawOf(d: Vec): Float = MathUtils.radiansToDegrees * atan2(d.y.toFloat(), d.x.toFloat())

    // ------------------------------------------------------------------ drawing

    fun draw(r: ClayRenderer) {
        tags.clear()
        r.draw(seaMesh, m.idt(), Color.WHITE, 0f).apply { grain = 0.35f; grainScale = 2.2f }
        r.draw(linesX, m.idt(), Color.WHITE, 0.006f).apply { grain = 0.3f; castShadow = false }
        r.draw(linesY, m.idt(), Color.WHITE, 0.006f).apply { grain = 0.3f; castShadow = false }
        drawCurrent(r)

        for ((i, rock) in level.rocks.sortedWith(compareBy({ it.y }, { it.x })).withIndex()) {
            world(rock.x.toFloat(), rock.y.toFloat(), 0f, v)
            shadow(r, v.x, v.z, 0.36f, 0.22f)
            val item = r.draw(models.rocks[i % models.rocks.size], m.setToTranslation(v).rotate(Vector3.Y, i * 67f), Color.WHITE, 0.012f, seed = i * 2.3f)
            if (preview?.rock == rock) {
                val pulse = 0.5f + 0.5f * sin(time * 10f)
                item.flash.set(0.55f * pulse, 0.05f * pulse, 0f, 0f)
            }
        }

        world(level.start.x.toFloat(), level.start.y.toFloat(), 0f, v)
        r.draw(models.startRing, m.setToTranslation(v), Color.WHITE, 0.01f).castShadow = false
        world(level.harbor.x.toFloat(), level.harbor.y.toFloat(), 0f, v)
        val bob = sin(time * 1.8f) * 0.01f
        r.draw(models.harbor, m.setToTranslation(v.x, bob, v.z), Color.WHITE, 0.012f)

        drawRoute(r)
        if (sail == null) preview?.let { drawPreview(r, it) }
        drawBoat(r)

        for (p in particles) {
            val s = p.size * minOf(1f, p.life / p.maxLife * 2f)
            r.draw(models.dot, m.setToTranslation(p.pos).scale(s, s, s), p.color, 0.1f, seed = p.maxLife * 10f).castShadow = false
        }
    }

    private fun drawBoat(r: ClayRenderer) {
        world(boatAt.x, boatAt.y, 0f, v)
        shadow(r, v.x, v.z, 0.42f, 0.28f, 0.012f)
        val bob = sin(time * 2.4f) * 0.014f
        val roll = sin(time * 1.7f) * 3f
        val sq = boatSquish
        m.setToTranslation(v.x, bob - 0.02f, v.z).rotate(Vector3.Y, boatYaw).rotate(Vector3.X, roll)
            .scale(BOAT_SCALE * sq.scaleXZ, BOAT_SCALE * sq.scaleY, BOAT_SCALE * sq.scaleXZ)
        r.draw(models.boat, m, Color.WHITE, 0.01f)
        val s = sail
        if (s != null && s.leg.together) {
            // Two small tugboats pull ahead, each along its own card.
            for (vec in s.leg.vectors) {
                val len = vec.length.toFloat()
                val ux = vec.x / len
                val uy = vec.y / len
                world(boatAt.x + ux * 0.62f, boatAt.y + uy * 0.62f, 0f, v)
                m.setToTranslation(v.x, bob, v.z).rotate(Vector3.Y, yawOf(vec)).scale(0.55f, 0.55f, 0.55f)
                r.draw(models.boat, m, Color.WHITE, 0.01f)
                for (k in 1..3) {
                    val f = k / 4f * 0.62f
                    world(boatAt.x + ux * f, boatAt.y + uy * f, 0.12f, v)
                    r.draw(models.dot, m.setToTranslation(v).scale(0.022f, 0.022f, 0.022f), Palette.wood, 0f)
                }
            }
        }
    }

    /** The moves made so far as thin dark arrows (the road), and the resultant from the start as coral dots. */
    private fun drawRoute(r: ClayRenderer) {
        val legs = voyage.legs
        val done = if (sail != null) shownLegs - 1 else shownLegs
        for (i in 0 until done.coerceAtMost(legs.size)) {
            val leg = legs[i]
            arrow(r, leg.from.x.toFloat(), leg.from.y.toFloat(), leg.to.x.toFloat(), leg.to.y.toFloat(), 0.03f, Palette.inkSoft)
        }
        if (done >= 2 || (done >= 1 && voyage.arrived)) {
            val to = legs[done - 1].to
            arrow(r, level.start.x.toFloat(), level.start.y.toFloat(), to.x.toFloat(), to.y.toFloat(), 0.03f, Palette.coral, dotted = true, lift = 0.09f)
            if (preview == null || voyage.arrived) {
                val d = to - level.start
                tag(level.start.x.toFloat(), level.start.y.toFloat(), to.x.toFloat(), to.y.toFloat(), "${Strings.DISPLACEMENT_SHORT} ${Strings.vec(d)}", Palette.brick)
            }
        }
    }

    /**
     * The planned move: the card vectors from the boat (two of them as a parallelogram when
     * pulled together), the current from their tip, and the resulting step.
     */
    private fun drawPreview(r: ClayRenderer, c: Check) {
        val ox = c.from.x.toFloat()
        val oy = c.from.y.toFloat()
        val ok = c.ok
        val card = if (ok) Palette.yellow else Palette.yellow.cpy().lerp(Palette.line, 0.4f)
        var tip = c.from
        if (c.vectors.size == 2) {
            val (a, b) = c.vectors
            arrow(r, ox, oy, ox + a.x, oy + a.y, 0.045f, card)
            arrow(r, ox, oy, ox + b.x, oy + b.y, 0.045f, card)
            // The other two sides of the parallelogram, as dotted copies.
            arrow(r, ox + b.x, oy + b.y, ox + a.x + b.x, oy + a.y + b.y, 0.03f, card, dotted = true)
            arrow(r, ox + a.x, oy + a.y, ox + a.x + b.x, oy + a.y + b.y, 0.03f, card, dotted = true)
            tag(ox, oy, ox + a.x, oy + a.y, Strings.vec(a), Palette.woodDark)
            tag(ox, oy, ox + b.x, oy + b.y, Strings.vec(b), Palette.woodDark)
            tip = c.from + a + b
        } else if (c.vectors.size == 1) {
            val a = c.vectors[0]
            arrow(r, ox, oy, ox + a.x, oy + a.y, 0.045f, card)
            tag(ox, oy, ox + a.x, oy + a.y, Strings.vec(a), Palette.woodDark)
            tip = c.from + a
        }
        if (!c.current.isZero) {
            arrow(r, tip.x.toFloat(), tip.y.toFloat(), (tip.x + c.current.x).toFloat(), (tip.y + c.current.y).toFloat(), 0.045f, Palette.grape, lift = 0.07f)
            tag(tip.x.toFloat(), tip.y.toFloat(), (tip.x + c.current.x).toFloat(), (tip.y + c.current.y).toFloat(), "${Strings.CURRENT_SHORT} ${Strings.vec(c.current)}", Palette.grape)
        }
        val resultant = c.vectors.size == 2 || !c.current.isZero
        val to = c.to
        if (resultant && !c.delta.isZero) {
            arrow(r, ox, oy, to.x.toFloat(), to.y.toFloat(), 0.055f, if (ok) Palette.coral else Palette.smoke, lift = 0.08f)
            tag(ox, oy, to.x.toFloat(), to.y.toFloat(), Strings.vec(c.delta), if (ok) Palette.brick else Palette.smoke, side = -1f)
        }
        // A ghost of the boat where the move ends.
        if (!c.delta.isZero) {
            world(to.x.toFloat(), to.y.toFloat(), 0f, v)
            m.setToTranslation(v.x, -0.02f, v.z).rotate(Vector3.Y, yawOf(c.delta)).scale(BOAT_SCALE, BOAT_SCALE, BOAT_SCALE)
            val ghost = if (ok) tint.set(1f, 1f, 1f, 0.35f) else tint.set(1f, 0.55f, 0.5f, 0.35f)
            r.draw(models.boat, m, ghost, 0.01f).castShadow = false
        }
    }

    /** Foam chevrons drifting with the current, so it can be seen before it is felt. */
    private fun drawCurrent(r: ClayRenderer) {
        val c = level.current
        if (c.isZero) return
        val len = c.length.toFloat()
        val ux = c.x / len
        val uy = c.y / len
        val yaw = yawOf(c)
        val w = level.width + 1f
        val h = level.height + 1f
        for (i in 0 until CHEVRONS) {
            // Fixed spots between the grid lines, drifting along the current and wrapping round.
            val bx = ((i * 0.618f) % 1f) * w
            val by = ((i * 0.382f + i * i * 0.137f) % 1f) * h
            val travel = time * 0.45f
            val x = wrap(bx + ux * travel, w) - 0.5f
            val y = wrap(by + uy * travel, h) - 0.5f
            val edge = minOf(x + 0.5f, w - 0.5f - x, y + 0.5f, h - 0.5f - y).coerceIn(0f, 0.4f) / 0.4f
            world(x, y, 0.015f, v)
            r.draw(models.chevron, m.setToTranslation(v).rotate(Vector3.Y, yaw).scale(1.3f, 1f, 1.3f), tint.set(1f, 1f, 1f, 0.75f * edge), 0.01f).castShadow = false
        }
    }

    private fun wrap(x: Float, n: Float): Float = ((x % n) + n) % n

    private val tmpA = Vector3()
    private val tmpB = Vector3()

    /** An arrow of dough lying on the water from grid point (ax, ay) to (bx, by). */
    private fun arrow(
        r: ClayRenderer, ax: Float, ay: Float, bx: Float, by: Float, radius: Float, color: Color,
        dotted: Boolean = false, lift: Float = ARROW_Y,
    ) {
        val dx = bx - ax
        val dy = by - ay
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-3f) return
        val ux = dx / len
        val uy = dy / len
        val yaw = MathUtils.radiansToDegrees * atan2(dy, dx)
        val headLen = minOf(radius * 4.4f, len * 0.45f)
        val headR = radius * 2.3f
        val shaftLen = len - headLen * 0.8f
        if (dotted) {
            val step = 0.17f
            var d = 0f
            while (d < shaftLen) {
                world(ax + ux * d, ay + uy * d, lift, tmpA)
                r.draw(models.dot, m.setToTranslation(tmpA).scale(radius * 1.15f, radius * 1.15f, radius * 1.15f), color, 0f).castShadow = false
                d += step
            }
        } else {
            world(ax, ay, lift, tmpA)
            r.draw(models.shaft, m.setToTranslation(tmpA).rotate(Vector3.Y, yaw).scale(shaftLen, radius, radius), color, 0.004f)
            r.draw(models.dot, m.setToTranslation(tmpA).scale(radius, radius, radius), color, 0f)
        }
        world(bx - ux * headLen, by - uy * headLen, lift, tmpB)
        r.draw(models.head, m.setToTranslation(tmpB).rotate(Vector3.Y, yaw).scale(headLen, headR, headR), color, 0.004f)
    }

    /** A label beside the middle of an arrow, on its left ([side] 1) or right (−1). */
    private fun tag(ax: Float, ay: Float, bx: Float, by: Float, text: String, color: Color, side: Float = 1f) {
        val dx = bx - ax
        val dy = by - ay
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        // Left of the arrow; flat arrows get their label above, steep ones beside.
        var nx = -dy / len * side
        var ny = dx / len * side
        if (abs(dy) < 1e-3f && ny < 0) { nx = -nx; ny = -ny }
        val off = 0.32f
        tags += Tag(world((ax + bx) / 2 + nx * off, (ay + by) / 2 + ny * off, 0.1f, Vector3()), text, color)
    }

    /** A soft dark patch on the water under something, so it sits instead of floating. */
    private fun shadow(r: ClayRenderer, x: Float, z: Float, radius: Float, strength: Float, y: Float = 0.01f) {
        val mm = Matrix4().setToTranslation(x, y, z).scale(radius, 1f, radius)
        r.draw(models.contactShadow, mm, tint.set(Palette.ink.r, Palette.ink.g, Palette.ink.b, strength), 0f).apply {
            castShadow = false
            grain = 0f
        }
    }

    /** The numbers along the bottom (x) and left (y) edges of the squared sea, for the overlay. */
    fun axisTags(): List<Tag> = buildList {
        val below = Models.MARGIN + 0.22f
        for (x in 0..level.width) add(Tag(world(x.toFloat(), -below, 0f, Vector3()), "$x", Palette.inkSoft))
        for (y in 0..level.height) add(Tag(world(-below, y.toFloat(), 0f, Vector3()), "$y", Palette.inkSoft))
    }

    override fun dispose() {
        seaMesh.dispose()
        linesX.dispose()
        linesY.dispose()
    }

    companion object {
        const val ARROW_Y = 0.06f
        const val BOAT_SCALE = 1.45f
        const val CHEVRONS = 12

        /** Grid point (x, y) of the sea in the world: x east, y north (away from the camera). */
        fun world(x: Float, y: Float, h: Float, out: Vector3): Vector3 = out.set(x, h, -y)
    }
}
