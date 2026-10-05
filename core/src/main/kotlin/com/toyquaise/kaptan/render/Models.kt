package com.toyquaise.kaptan.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.kaptan.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every clay model in the game, rolled from balls, boxes, turned shapes and snakes, each one
 * kneaded (see [MeshData]). One unit is one square of the sea; the water's surface is y = 0; a
 * boat faces +x. The sea itself depends on the level and is built by [sea] and [gridLines].
 */
class Models : Disposable {
    private val meshes = ArrayList<Mesh>()
    private var seeds = 1
    private fun MeshData.build(): Mesh = toMesh().also { meshes += it }
    private fun md() = MeshData().seed(seeds++ * 31)

    /** y up → x forward: turned shapes are built standing, then laid along +x. */
    private val layDown = Matrix4().rotate(Vector3.Z, -90f)

    val dot: Mesh = md().plain().ball(0f, 0f, 0f, 1f, slices = 12, stacks = 8).build()

    // ------------------------------------------------------------------ arrows

    /** An arrow's shaft: a smooth snake from x = 0 to x = 1 of radius 1, open at both ends. */
    val shaft: Mesh = md().plain().tube(listOf(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f)), 1f, sides = 14, caps = false).build()

    /** An arrow's head: a rounded cone from x = 0 (radius 1) to its tip at x = 1. */
    val head: Mesh = md().apply {
        plain()
        append(
            MeshData().plain().lathe(0f, 0f, 0f, listOf(
                Vector2(0f, 0f), Vector2(1f, 0f), Vector2(0.97f, 0.1f), Vector2(0.2f, 0.9f), Vector2(0.08f, 0.98f), Vector2(0f, 1f),
            ), slices = 18),
            layDown,
        )
    }.build()

    // ------------------------------------------------------------------ the boat

    /** A little tugboat with its captain, facing +x, floating with its waterline at y = 0. */
    val boat: Mesh = md().apply {
        // Hull, with a round bow and black tyre bumpers along its sides.
        color(Palette.coral).roundedBox(-0.02f, 0.06f, 0f, 0.26f, 0.09f, 0.15f, 0.08f)
        color(Palette.coral).ball(0.2f, 0.07f, 0f, 0.14f, 0.085f, 0.145f)
        color(Palette.cream).roundedBox(0f, 0.155f, 0f, 0.25f, 0.025f, 0.135f, 0.022f)
        color(Palette.cream).ball(0.21f, 0.155f, 0f, 0.11f, 0.026f, 0.12f)
        for (x in listOf(-0.16f, 0.02f, 0.18f)) for (z in listOf(-0.155f, 0.155f)) {
            color(Palette.charcoal).ball(x, 0.09f, z, 0.042f, 0.042f, 0.028f)
        }
        // Wheelhouse with a yellow roof and two round windows looking forward.
        color(Palette.cream).roundedBox(-0.08f, 0.27f, 0f, 0.11f, 0.1f, 0.1f, 0.04f)
        color(Palette.yellow).roundedBox(-0.08f, 0.38f, 0f, 0.135f, 0.022f, 0.125f, 0.02f)
        color(Palette.glass).ball(0.03f, 0.29f, -0.045f, 0.012f, 0.035f, 0.03f)
        color(Palette.glass).ball(0.03f, 0.29f, 0.045f, 0.012f, 0.035f, 0.03f)
        // Funnel, turquoise with a cream band, behind the wheelhouse.
        color(Palette.turquoise).lathe(-0.23f, 0.16f, 0f, MeshData.roundedCylinderProfile(0.05f, 0.26f, 0.02f))
        color(Palette.cream).lathe(-0.23f, 0.34f, 0f, MeshData.roundedCylinderProfile(0.054f, 0.045f, 0.015f))
        // The captain on the foredeck: blue coat, round face, cap.
        color(Palette.sky).ball(0.13f, 0.22f, 0f, 0.05f, 0.06f, 0.05f)
        color(Palette.enamel).ball(0.13f, 0.31f, 0f, 0.05f)
        color(Palette.charcoal).ball(0.175f, 0.318f, -0.018f, 0.008f)
        color(Palette.charcoal).ball(0.175f, 0.318f, 0.018f, 0.008f)
        color(Palette.pink).ball(0.17f, 0.298f, -0.032f, 0.01f, 0.007f, 0.01f)
        color(Palette.pink).ball(0.17f, 0.298f, 0.032f, 0.01f, 0.007f, 0.01f)
        color(Palette.teal).lathe(0.13f, 0.34f, 0f, MeshData.roundedCylinderProfile(0.048f, 0.04f, 0.015f))
        color(Palette.teal).ball(0.165f, 0.345f, 0f, 0.035f, 0.008f, 0.04f)
        color(Palette.enamel).ball(0.13f, 0.385f, 0f, 0.018f, 0.01f, 0.018f)
    }.build()

    // ------------------------------------------------------------------ rocks, start, harbor

    /** A few rocks of slightly different shapes, each a lump with a pebble and a little moss. */
    val rocks: List<Mesh> = (0 until 3).map { i ->
        md().apply {
            val s = 1f + i * 0.08f
            color(if (i == 1) Palette.rockDark else Palette.rock).ball(0f, 0.08f, 0f, 0.26f * s, 0.2f * s, 0.23f * s)
            color(Palette.rock).ball(0.15f - i * 0.1f, 0.05f, 0.12f, 0.12f, 0.09f, 0.11f)
            color(Palette.rockDark).ball(-0.17f, 0.03f, -0.08f + i * 0.08f, 0.09f, 0.06f, 0.08f)
            color(Palette.moss).ball(-0.04f, 0.25f * s, -0.03f, 0.1f, 0.035f, 0.09f)
        }.build()
    }

    /** Where the voyage began: a small cream ring on the water. */
    val startRing: Mesh = md().color(Palette.cream).tube(circle(0.22f, 0.02f, 36), 0.03f, caps = false).build()

    /** The harbor: a striped life ring floating on the point, and a lighthouse on an islet beside it. */
    val harbor: Mesh = md().apply {
        val ring = circle(0.3f, 0.03f, 48)
        for (k in 0 until 8) {
            color(if (k % 2 == 0) Palette.coral else Palette.enamel)
            tube(ring.subList(k * 6, k * 6 + 7), 0.055f)
        }
        // The islet sits up and to the right (north-east) of the point.
        val ix = 0.42f
        val iz = -0.42f
        color(Palette.sand).ball(ix, 0f, iz, 0.2f, 0.07f, 0.18f)
        val stripes = listOf(Palette.coral, Palette.enamel, Palette.coral, Palette.enamel)
        for ((k, c) in stripes.withIndex()) {
            val r = 0.075f - k * 0.006f
            color(c).lathe(ix, 0.04f + k * 0.085f, iz, MeshData.roundedCylinderProfile(r, 0.09f, 0.01f))
        }
        color(Palette.yellow).ball(ix, 0.42f, iz, 0.05f)
        color(Palette.coral).lathe(ix, 0.45f, iz, listOf(Vector2(0f, 0f), Vector2(0.07f, 0f), Vector2(0.06f, 0.02f), Vector2(0.01f, 0.09f), Vector2(0f, 0.1f)))
    }.build()

    /** A chevron of foam pointing +x, drifting with the current. */
    val chevron: Mesh = md().apply {
        color(Palette.enamel)
        tube(listOf(Vector3(-0.09f, 0f, -0.1f), Vector3(0.03f, 0f, 0f)), 0.022f)
        tube(listOf(Vector3(0.03f, 0f, 0f), Vector3(-0.09f, 0f, 0.1f)), 0.022f)
    }.build()

    /** A soft round shadow: dark in the middle, fading to nothing at radius 1. Drawn see-through. */
    val contactShadow: Mesh = md().apply {
        plain()
        val centre = vertex(0f, 0f, 0f, 0f, 1f, 0f)
        val rings = listOf(0.45f to 0.75f, 1f to 0f)
        val slices = 28
        val first = vertexCount
        for ((r, a) in rings) {
            color(Color(1f, 1f, 1f, a))
            for (i in 0 until slices) {
                val t = 2 * PI * i / slices
                vertex((r * cos(t)).toFloat(), 0f, (r * sin(t)).toFloat(), 0f, 1f, 0f)
            }
        }
        for (i in 0 until slices) {
            val a = first + i
            val b = first + (i + 1) % slices
            triangle(centre, b, a)
            triangle(a, b, b + slices)
            triangle(a, b + slices, a + slices)
        }
    }.build()

    // ------------------------------------------------------------------ the sea of a level

    /**
     * The sea for a level of [w] × [h] squares: a slab of water on a sandy shore, with its top at
     * y = 0; grid point (x, y) is at world (x, 0, −y). Owned by the caller.
     */
    fun sea(w: Int, h: Int): Mesh = MeshData().seed(5).apply {
        lumpiness = 0.006f
        thumbs = 0
        val cx = w / 2f
        val cz = -h / 2f
        color(Palette.sand).roundedBox(cx, -0.2f, cz, w / 2f + MARGIN + 0.32f, 0.17f, h / 2f + MARGIN + 0.32f, 0.16f)
        color(Palette.sea).roundedBox(cx, -0.1f, cz, w / 2f + MARGIN, 0.1f, h / 2f + MARGIN, 0.09f)
    }.toMesh()

    /** The squares: snakes of pale dough pressed into the water along every whole x and y. Owned by the caller. */
    fun gridLines(w: Int, h: Int, vertical: Boolean): Mesh = MeshData().seed(if (vertical) 7 else 9).apply {
        color(Palette.gridLine)
        val ext = 0.38f
        if (vertical) for (x in 0..w) tube(listOf(Vector3(x.toFloat(), 0f, ext), Vector3(x.toFloat(), 0f, -h - ext)), LINE, sides = 10)
        else for (y in 0..h) tube(listOf(Vector3(-ext, 0f, -y.toFloat()), Vector3(w + ext, 0f, -y.toFloat())), LINE, sides = 10)
    }.toMesh()

    private fun circle(r: Float, y: Float, n: Int): List<Vector3> = (0..n).map { i ->
        val a = 2 * PI * i / n
        Vector3((r * cos(a)).toFloat(), y, (r * sin(a)).toFloat())
    }

    override fun dispose() = meshes.forEach { it.dispose() }

    companion object {
        /** Water around the outermost grid points. */
        const val MARGIN = 0.6f

        /** Radius of a grid line. */
        const val LINE = 0.026f
    }
}
