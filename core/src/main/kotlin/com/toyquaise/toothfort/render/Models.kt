package com.toyquaise.toothfort.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.toyquaise.toothfort.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every clay model in the game, rolled from balls, boxes, turned shapes and snakes. Units are
 * cells; the table top is y = 0; a part's + terminal faces +x. Models that are recoloured per
 * instance (gummy bears, wires) are built white.
 */
class Models : Disposable {
    private val meshes = ArrayList<Mesh>()
    private fun MeshData.build(): Mesh = toMesh().also { meshes += it }
    private fun md() = MeshData()

    /** y up → x forward: turned shapes are built standing, then laid along +x. */
    private val layDown = Matrix4().rotate(Vector3.Z, -90f)

    val ball = md().ball(0f, 0f, 0f, 1f, slices = 22, stacks = 16).build()
    val dot = md().ball(0f, 0f, 0f, 1f, slices = 10, stacks = 7).build()

    /** A unit rounded box (half-size 0.5), scaled for bars and slabs. */
    val bar = md().roundedBox(0f, 0f, 0f, 0.5f, 0.5f, 0.5f, 0.2f, slices = 12, stacks = 8).build()

    /** A unit cylinder standing from y = 0 to 1, for beams. */
    val rod = md().lathe(0f, 0f, 0f, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)), slices = 14).build()

    // ------------------------------------------------------------------ parts

    val battery: Mesh = md().apply {
        val lift = Matrix4().translate(0f, 0.31f, 0f).mul(layDown)
        append(md().color(Palette.turquoise).lathe(0f, -0.32f, 0f, MeshData.roundedCylinderProfile(0.29f, 0.62f, 0.1f), slices = 26), lift)
        append(md().color(Palette.charcoal).lathe(0f, -0.33f, 0f, MeshData.roundedCylinderProfile(0.298f, 0.18f, 0.09f), slices = 26), lift)
        append(md().color(Palette.cream).lathe(0f, 0.0f, 0f, MeshData.roundedCylinderProfile(0.296f, 0.12f, 0.025f), slices = 26), lift)
        append(md().color(Palette.yellow).lathe(0f, 0.29f, 0f, MeshData.roundedCylinderProfile(0.1f, 0.1f, 0.035f)), lift)
        // Terminals: + coral, − charcoal, at the edges of the cell where wires meet them.
        color(Palette.coral).ball(0.42f, 0.1f, 0f, 0.085f)
        color(Palette.charcoal).ball(-0.42f, 0.1f, 0f, 0.085f)
        // A + and a − pressed into the dough on top.
        color(Palette.coral).roundedBox(0.2f, 0.59f, 0f, 0.07f, 0.02f, 0.02f, 0.015f)
        color(Palette.coral).roundedBox(0.2f, 0.59f, 0f, 0.02f, 0.02f, 0.07f, 0.015f)
        color(Palette.cream).roundedBox(-0.2f, 0.59f, 0f, 0.07f, 0.02f, 0.02f, 0.015f)
    }.build()

    /** The flat base small parts stand on, with a terminal knob on each side. */
    val componentBase: Mesh = md().apply {
        color(Palette.teal).roundedBox(0f, 0.08f, 0f, 0.37f, 0.08f, 0.36f, 0.06f)
        color(Palette.yellow).ball(0.42f, 0.1f, 0f, 0.08f)
        color(Palette.yellow).ball(-0.42f, 0.1f, 0f, 0.08f)
    }.build()

    /** A machine's base: the same plate with a cream pedestal, so machines stand tall. Top at [PEDESTAL]. */
    val machineBase: Mesh = md().apply {
        color(Palette.teal).roundedBox(0f, 0.08f, 0f, 0.38f, 0.08f, 0.37f, 0.07f)
        color(Palette.yellow).ball(0.42f, 0.1f, 0f, 0.085f)
        color(Palette.yellow).ball(-0.42f, 0.1f, 0f, 0.085f)
        color(Palette.cream).lathe(0f, 0.1f, 0f, listOf(
            Vector2(0f, 0f), Vector2(0.27f, 0f), Vector2(0.25f, 0.05f), Vector2(0.2f, 0.12f), Vector2(0.19f, 0.2f),
            Vector2(0.24f, 0.25f), Vector2(0.25f, 0.29f), Vector2(0.22f, 0.32f), Vector2(0f, 0.32f),
        ), slices = 22)
    }.build()

    /** The brush's motor, a dome on the base. */
    val brushMotor: Mesh = md().color(Palette.yellow).ball(0f, 0.2f, 0f, 0.2f, 0.16f, 0.2f).build()

    /** The toothbrush itself, pivoting on the motor and pointing along +x. */
    val brush: Mesh = md().apply {
        color(Palette.turquoise).tube(listOf(Vector3(-0.14f, 0.4f, 0f), Vector3(0.18f, 0.4f, 0f), Vector3(0.38f, 0.43f, 0f)), 0.07f, 0.055f, sides = 12)
        color(Palette.enamel).roundedBox(0.5f, 0.44f, 0f, 0.14f, 0.045f, 0.085f, 0.04f)
        color(Palette.enamel).roundedBox(0.5f, 0.36f, 0f, 0.13f, 0.055f, 0.075f, 0.03f)
        color(Palette.turquoise).roundedBox(0.5f, 0.29f, 0f, 0.125f, 0.03f, 0.07f, 0.025f)
        color(Palette.coral).ball(-0.16f, 0.4f, 0f, 0.075f)
    }.build()

    /** The paste ball cannon: a toothpaste tube on a cradle, nozzle along +x. Pivot at its middle. */
    val pasteTube: Mesh = md().apply {
        val profile = listOf(
            Vector2(0f, -0.32f), Vector2(0.08f, -0.32f), Vector2(0.17f, -0.25f), Vector2(0.2f, -0.12f),
            Vector2(0.2f, 0.17f), Vector2(0.16f, 0.25f), Vector2(0.085f, 0.3f), Vector2(0.085f, 0.41f), Vector2(0f, 0.41f),
        )
        append(md().color(Palette.enamel).lathe(0f, 0f, 0f, profile, slices = 24), layDown)
        append(md().color(Palette.coral).lathe(0f, -0.06f, 0f, MeshData.roundedCylinderProfile(0.205f, 0.1f, 0.025f), slices = 24), layDown)
        append(md().color(Palette.turquoise).lathe(0f, 0.09f, 0f, MeshData.roundedCylinderProfile(0.205f, 0.07f, 0.025f), slices = 24), layDown)
        append(md().color(Palette.coral).lathe(0f, 0.31f, 0f, MeshData.roundedCylinderProfile(0.105f, 0.08f, 0.025f)), layDown)
        // The crimped end.
        color(Palette.enamel).roundedBox(-0.36f, 0f, 0f, 0.05f, 0.2f, 0.045f, 0.03f)
        color(Palette.line).roundedBox(-0.39f, 0f, 0f, 0.02f, 0.17f, 0.048f, 0.012f)
    }.build()

    val cradle: Mesh = md().apply {
        color(Palette.charcoal).roundedBox(0f, 0.2f, 0f, 0.07f, 0.08f, 0.07f, 0.03f)
        color(Palette.yellow).ball(0f, 0.27f, 0f, 0.16f, 0.1f, 0.16f)
    }.build()

    /** The whitening laser: a lamp head along +x with a glass lens. Pivot at its middle. */
    val laserHead: Mesh = md().apply {
        append(md().color(Palette.grape).lathe(0f, -0.26f, 0f, MeshData.capsuleProfile(0.17f, 0.52f), slices = 22), layDown)
        append(md().color(Palette.cream).lathe(0f, 0.15f, 0f, MeshData.roundedCylinderProfile(0.19f, 0.09f, 0.03f), slices = 22), layDown)
        color(Palette.glass).ball(0.27f, 0f, 0f, 0.06f, 0.13f, 0.13f)
        color(Palette.yellow).ball(-0.24f, 0.13f, 0f, 0.05f)
    }.build()

    val laserStand: Mesh = md().apply {
        color(Palette.charcoal).lathe(0f, 0.12f, 0f, MeshData.capsuleProfile(0.065f, 0.38f))
        color(Palette.grape).ball(0f, 0.18f, 0f, 0.15f, 0.08f, 0.15f)
    }.build()

    /** Switch posts; the lever is drawn on its own so it can open. */
    val switchPosts: Mesh = md().apply {
        color(Palette.steel).roundedBox(-0.2f, 0.19f, 0f, 0.04f, 0.06f, 0.06f, 0.02f)
        color(Palette.steel).roundedBox(0.2f, 0.19f, 0f, 0.04f, 0.06f, 0.06f, 0.02f)
    }.build()

    /** The lever, hinged at the origin and lying along +x when closed. */
    val switchLever: Mesh = md().apply {
        color(Palette.steel).tube(listOf(Vector3(0f, 0f, 0f), Vector3(0.42f, 0f, 0f)), 0.025f)
        color(Palette.coral).ball(0.5f, 0.02f, 0f, 0.06f)
    }.build()

    val fuse: Mesh = md().apply {
        append(md().color(Palette.glass).lathe(0f, -0.22f, 0f, MeshData.capsuleProfile(0.08f, 0.44f)), Matrix4().translate(0f, 0.22f, 0f).mul(layDown))
        append(md().color(Palette.steel).lathe(0f, -0.25f, 0f, MeshData.roundedCylinderProfile(0.09f, 0.1f, 0.025f)), Matrix4().translate(0f, 0.22f, 0f).mul(layDown))
        append(md().color(Palette.steel).lathe(0f, 0.15f, 0f, MeshData.roundedCylinderProfile(0.09f, 0.1f, 0.025f)), Matrix4().translate(0f, 0.22f, 0f).mul(layDown))
    }.build()

    /** The thin wire inside a fuse, recoloured when it blows. */
    val fuseWire: Mesh = md().tube(listOf(Vector3(-0.16f, 0.22f, 0f), Vector3(0f, 0.25f, 0f), Vector3(0.16f, 0.22f, 0f)), 0.012f).build()

    val resistor: Mesh = md().apply {
        append(md().color(Palette.wood).lathe(0f, -0.2f, 0f, MeshData.capsuleProfile(0.085f, 0.4f)), Matrix4().translate(0f, 0.2f, 0f).mul(layDown))
        for ((i, c) in listOf(Palette.brick, Palette.grape, Palette.yellow).withIndex()) {
            append(md().color(c).lathe(0f, -0.1f + i * 0.08f, 0f, MeshData.roundedCylinderProfile(0.09f, 0.035f, 0.01f)), Matrix4().translate(0f, 0.2f, 0f).mul(layDown))
        }
        color(Palette.steel).tube(listOf(Vector3(-0.36f, 0.09f, 0f), Vector3(-0.27f, 0.2f, 0f), Vector3(-0.2f, 0.2f, 0f)), 0.018f)
        color(Palette.steel).tube(listOf(Vector3(0.2f, 0.2f, 0f), Vector3(0.27f, 0.2f, 0f), Vector3(0.36f, 0.09f, 0f)), 0.018f)
    }.build()

    // ------------------------------------------------------------------ candies

    /** A sugar cube with an angry face on its +x side. Centre at the origin, half-size 0.17. */
    val sugarCube: Mesh = md().apply {
        color(Palette.sugar).roundedBox(0f, 0f, 0f, 0.24f, 0.24f, 0.24f, 0.08f)
        eyes(0.225f, 0.05f, 0.095f, 0.06f)
    }.build()

    /** A gummy bear, built white and tinted per bear; its face is [gummyFace]. Feet at y = 0, facing +x. */
    val gummyBear: Mesh = md().apply {
        ball(0f, 0.21f, 0f, 0.13f, 0.16f, 0.12f)
        ball(0.02f, 0.42f, 0f, 0.12f, 0.11f, 0.12f)
        ball(0.01f, 0.52f, 0.085f, 0.05f)
        ball(0.01f, 0.52f, -0.085f, 0.05f)
        ball(0.12f, 0.4f, 0f, 0.055f, 0.045f, 0.06f)
        ball(0.06f, 0.27f, 0.13f, 0.06f, 0.05f, 0.05f)
        ball(0.06f, 0.27f, -0.13f, 0.06f, 0.05f, 0.05f)
        ball(0.04f, 0.06f, 0.075f, 0.075f, 0.06f, 0.06f)
        ball(0.04f, 0.06f, -0.075f, 0.075f, 0.06f, 0.06f)
    }.build()

    val gummyFace: Mesh = md().apply {
        eyes(0.105f, 0.46f, 0.05f, 0.034f, brows = false)
        color(Palette.charcoal).ball(0.172f, 0.405f, 0f, 0.022f)
    }.build()

    /** A lollipop on its stick: candy disc facing +x with a cream spiral. Stick foot at y = 0. */
    val lollipop: Mesh = md().apply {
        color(Palette.cream).lathe(0f, 0f, 0f, MeshData.capsuleProfile(0.035f, 0.45f))
        color(Palette.pink).ball(0f, 0.68f, 0f, 0.075f, 0.3f, 0.3f, slices = 26, stacks = 16)
        val spiral = ArrayList<Vector3>()
        val turns = 2.6
        for (i in 0..60) {
            val t = i / 60.0
            val a = t * turns * 2 * PI
            val r = 0.03 + 0.23 * t
            spiral += Vector3(0.068f - 0.03f * (t * t).toFloat(), (0.68 + r * sin(a)).toFloat(), (r * cos(a)).toFloat())
        }
        color(Palette.cream).tube(spiral, 0.03f, 0.028f, sides = 8)
    }.build()

    /** Cartoon eyes on a +x face, centred at height [y]: white eyeballs, dark pupils, cross brows. */
    private fun MeshData.eyes(x: Float, y: Float, spread: Float, eye: Float, brows: Boolean = true) {
        for (side in listOf(1f, -1f)) {
            val z = spread * side
            color(Palette.surface).ball(x, y, z, eye * 0.55f, eye * 1.15f, eye)
            color(Palette.charcoal).ball(x + eye * 0.45f, y - eye * 0.15f, z - side * eye * 0.12f, eye * 0.3f, eye * 0.55f, eye * 0.5f)
            color(Color.WHITE).ball(x + eye * 0.62f, y + eye * 0.15f, z - side * eye * 0.25f, eye * 0.12f)
            if (brows) {
                color(Palette.charcoal).tube(
                    listOf(Vector3(x + eye * 0.3f, y + eye * 1.75f, z + side * eye * 0.9f), Vector3(x + eye * 0.35f, y + eye * 1.15f, z - side * eye * 0.75f)),
                    eye * 0.22f,
                )
            }
        }
    }

    // ------------------------------------------------------------------ the tooth and its friends

    /** The tooth the player defends: a cartoon molar on two roots, smiling toward the player (+z). */
    val tooth: Mesh = md().apply {
        color(Palette.enamel)
        for (side in listOf(-1f, 1f)) {
            tube(MeshData.smooth(listOf(Vector3(0.21f * side, 0.62f, 0f), Vector3(0.27f * side, 0.36f, 0.01f), Vector3(0.22f * side, 0.1f, 0.03f))), 0.19f, 0.08f, sides = 16)
        }
        roundedBox(0f, 0.84f, 0f, 0.47f, 0.29f, 0.35f, 0.25f, slices = 26, stacks = 16)
        ball(-0.2f, 1.03f, 0f, 0.25f, 0.12f, 0.3f)
        ball(0.2f, 1.03f, 0f, 0.25f, 0.12f, 0.3f)
        // The shine every cartoon tooth has.
        color(Color.WHITE).tube(MeshData.smooth(listOf(Vector3(-0.38f, 0.78f, 0.27f), Vector3(-0.36f, 0.95f, 0.27f), Vector3(-0.26f, 1.04f, 0.26f))), 0.035f, 0.025f)
        // Face.
        color(Palette.charcoal).ball(-0.15f, 0.86f, 0.335f, 0.065f, 0.09f, 0.04f)
        color(Palette.charcoal).ball(0.15f, 0.86f, 0.335f, 0.065f, 0.09f, 0.04f)
        color(Color.WHITE).ball(-0.13f, 0.9f, 0.37f, 0.024f)
        color(Color.WHITE).ball(0.17f, 0.9f, 0.37f, 0.024f)
        color(Palette.charcoal).tube(MeshData.smooth(listOf(Vector3(-0.13f, 0.71f, 0.345f), Vector3(0f, 0.64f, 0.36f), Vector3(0.13f, 0.71f, 0.345f))), 0.022f)
        color(Palette.pink).ball(-0.29f, 0.74f, 0.29f, 0.075f, 0.05f, 0.04f)
        color(Palette.pink).ball(0.29f, 0.74f, 0.29f, 0.075f, 0.05f, 0.04f)
    }.build()

    /** The gum the tooth stands in. */
    val gum: Mesh = md().color(Palette.gum).ball(0f, 0.04f, 0f, 0.62f, 0.14f, 0.5f, slices = 26).build()

    /** A soft round shadow: dark in the middle, fading to nothing at radius 1. Drawn see-through on the ground. */
    val contactShadow: Mesh = md().apply {
        val centre = vertex(0f, 0f, 0f, 0f, 1f, 0f)
        val rings = listOf(0.45f to 0.75f, 1f to 0f)
        val slices = 24
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

    /** A ball of striped toothpaste, radius 1. */
    val pasteBall: Mesh = md().apply {
        color(Palette.enamel).ball(0f, 0f, 0f, 1f, slices = 16, stacks = 12)
        for ((c, tilt) in listOf(Palette.coral to 0f, Palette.turquoise to 60f, Palette.coral to 120f)) {
            val ring = (0..24).map { i ->
                val a = 2 * PI * i / 24
                Vector3(0f, (1.02 * cos(a)).toFloat(), (1.02 * sin(a)).toFloat()).rotate(Vector3.Z, tilt)
            }
            color(c).tube(ring, 0.16f, sides = 6, caps = false)
        }
    }.build()

    /** The candy jar candies come out of. Glass is drawn see-through on its own. */
    val jarCandies: Mesh = md().apply {
        color(Palette.coral).ball(-0.1f, 0.14f, 0.05f, 0.11f)
        color(Palette.yellow).ball(0.1f, 0.14f, -0.06f, 0.11f)
        color(Palette.grape).ball(0.02f, 0.3f, 0.02f, 0.1f)
        color(Palette.sugar).roundedBox(0.1f, 0.3f, 0.1f, 0.07f, 0.07f, 0.07f, 0.02f)
        color(Palette.pink).ball(-0.08f, 0.42f, -0.06f, 0.09f)
        color(Palette.coral).lathe(0f, 0.6f, 0f, MeshData.roundedCylinderProfile(0.3f, 0.09f, 0.035f))
    }.build()

    val jarGlass: Mesh = md().color(Palette.glass).lathe(
        0f, 0f, 0f,
        listOf(Vector2(0f, 0f), Vector2(0.24f, 0f), Vector2(0.3f, 0.06f), Vector2(0.31f, 0.42f), Vector2(0.27f, 0.54f), Vector2(0.27f, 0.62f), Vector2(0f, 0.62f)),
        slices = 24,
    ).build()

    // ------------------------------------------------------------------ table props (from toyquaise.com's art)

    /** The site's pencil: pink eraser, yellow body, wooden tip, along +x. */
    val pencil: Mesh = md().apply {
        val r = 0.16f
        append(md().color(Palette.pink).lathe(0f, -1.1f, 0f, MeshData.roundedCylinderProfile(r, 0.32f, 0.08f)), layDown)
        append(md().color(Palette.steel).lathe(0f, -0.82f, 0f, MeshData.roundedCylinderProfile(r + 0.01f, 0.14f, 0.03f)), layDown)
        append(md().color(Palette.yellow).lathe(0f, -0.7f, 0f, MeshData.roundedCylinderProfile(r, 1.4f, 0.04f), slices = 6), layDown)
        append(md().color(Palette.wood).lathe(0f, 0.68f, 0f, listOf(Vector2(0f, 0f), Vector2(r * 0.92f, 0f), Vector2(0.03f, 0.36f), Vector2(0f, 0.38f))), layDown)
        append(md().color(Palette.charcoal).lathe(0f, 0.94f, 0f, listOf(Vector2(0f, 0f), Vector2(0.06f, 0f), Vector2(0.012f, 0.11f), Vector2(0f, 0.12f))), layDown)
    }.build()

    /** A toy block, half-size 0.3. */
    val block: Mesh = md().roundedBox(0f, 0.3f, 0f, 0.3f, 0.3f, 0.3f, 0.09f).build()

    /** The site's gear, lying flat. */
    val gear: Mesh = md().apply {
        color(Palette.yellow)
        val profile = listOf(Vector2(0f, 0f), Vector2(0.5f, 0f), Vector2(0.55f, 0.05f), Vector2(0.55f, 0.17f), Vector2(0.5f, 0.22f), Vector2(0f, 0.22f))
        lathe(0f, 0f, 0f, profile, slices = 36)
        for (i in 0 until 9) {
            val a = 2 * PI * i / 9
            val t = Matrix4().rotate(Vector3.Y, Math.toDegrees(a).toFloat()).translate(0.6f, 0.11f, 0f)
            append(md().roundedBox(0f, 0f, 0f, 0.13f, 0.1f, 0.11f, 0.05f), t)
        }
        color(Palette.cream).lathe(0f, 0.2f, 0f, MeshData.roundedCylinderProfile(0.2f, 0.06f, 0.03f))
    }.build()

    override fun dispose() = meshes.forEach { it.dispose() }

    companion object {
        /** Height of the top of a machine's pedestal; machine heads are drawn this much higher. */
        const val PEDESTAL = 0.32f
    }
}
