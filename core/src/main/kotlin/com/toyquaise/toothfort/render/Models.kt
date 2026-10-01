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
 * Every clay model in the game, rolled from balls, boxes, turned shapes and snakes, each one
 * kneaded (see [MeshData]). Units are those of the counter; the counter top is y = 0; a part's +
 * terminal faces +x. Models that are recoloured per instance (gummy bears) are built white.
 */
class Models : Disposable {
    private val meshes = ArrayList<Mesh>()
    private var seeds = 1
    private fun MeshData.build(): Mesh = toMesh().also { meshes += it }
    private fun md() = MeshData().seed(seeds++ * 31)

    /** y up → x forward: turned shapes are built standing, then laid along +x. */
    private val layDown = Matrix4().rotate(Vector3.Z, -90f)
    private fun lay(y: Float) = Matrix4().translate(0f, y, 0f).mul(layDown)

    val dot = md().plain().ball(0f, 0f, 0f, 1f, slices = 12, stacks = 8).build()

    /** A unit rounded box (half-size 0.5), scaled for bars and slabs. */
    val bar = md().plain().roundedBox(0f, 0f, 0f, 0.5f, 0.5f, 0.5f, 0.2f, steps = 6).build()

    /** A unit cylinder standing from y = 0 to 1, for beams. */
    val rod = md().plain().lathe(0f, 0f, 0f, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)), slices = 16).build()

    // ------------------------------------------------------------------ parts

    val battery: Mesh = md().apply {
        append(md().color(Palette.turquoise).lathe(0f, -0.32f, 0f, MeshData.roundedCylinderProfile(0.29f, 0.62f, 0.11f)), lay(0.31f))
        append(md().color(Palette.charcoal).lathe(0f, -0.335f, 0f, MeshData.roundedCylinderProfile(0.3f, 0.18f, 0.09f)), lay(0.31f))
        append(md().color(Palette.cream).lathe(0f, 0.0f, 0f, MeshData.roundedCylinderProfile(0.298f, 0.12f, 0.03f)), lay(0.31f))
        append(md().color(Palette.yellow).lathe(0f, 0.29f, 0f, MeshData.roundedCylinderProfile(0.1f, 0.1f, 0.04f)), lay(0.31f))
        // Terminals: + coral, − charcoal, where wires meet them.
        color(Palette.coral).ball(0.42f, 0.1f, 0f, 0.085f)
        color(Palette.charcoal).ball(-0.42f, 0.1f, 0f, 0.085f)
        // A + and a − rolled from thin snakes and pressed on top.
        color(Palette.coral).tube(listOf(Vector3(0.13f, 0.6f, 0f), Vector3(0.27f, 0.6f, 0f)), 0.022f)
        color(Palette.coral).tube(listOf(Vector3(0.2f, 0.6f, -0.07f), Vector3(0.2f, 0.6f, 0.07f)), 0.022f)
        color(Palette.cream).tube(listOf(Vector3(-0.27f, 0.6f, 0f), Vector3(-0.13f, 0.6f, 0f)), 0.022f)
    }.build()

    /** The flat base small parts stand on, with a terminal knob on each side. */
    val componentBase: Mesh = md().apply {
        color(Palette.teal).roundedBox(0f, 0.08f, 0f, 0.37f, 0.08f, 0.34f, 0.07f)
        color(Palette.yellow).ball(0.42f, 0.1f, 0f, 0.08f)
        color(Palette.yellow).ball(-0.42f, 0.1f, 0f, 0.08f)
    }.build()

    /** A machine's base: the plate with a cream pedestal, so machines stand tall. Top at [PEDESTAL]. */
    val machineBase: Mesh = md().apply {
        color(Palette.teal).roundedBox(0f, 0.08f, 0f, 0.38f, 0.08f, 0.36f, 0.075f)
        color(Palette.yellow).ball(0.42f, 0.1f, 0f, 0.085f)
        color(Palette.yellow).ball(-0.42f, 0.1f, 0f, 0.085f)
        color(Palette.cream).lathe(0f, 0.1f, 0f, listOf(
            Vector2(0f, 0f), Vector2(0.27f, 0f), Vector2(0.25f, 0.05f), Vector2(0.2f, 0.12f), Vector2(0.19f, 0.2f),
            Vector2(0.24f, 0.25f), Vector2(0.25f, 0.29f), Vector2(0.22f, 0.32f), Vector2(0f, 0.32f),
        ))
    }.build()

    /** The brush's motor, a dome on the pedestal. */
    val brushMotor: Mesh = md().color(Palette.yellow).ball(0f, 0.2f, 0f, 0.2f, 0.16f, 0.2f).build()

    /** The toothbrush itself, pivoting on the motor and pointing along +x. */
    val brush: Mesh = md().apply {
        color(Palette.turquoise).tube(listOf(Vector3(-0.14f, 0.4f, 0f), Vector3(0.18f, 0.4f, 0f), Vector3(0.38f, 0.43f, 0f)), 0.07f, 0.055f)
        color(Palette.enamel).roundedBox(0.5f, 0.44f, 0f, 0.14f, 0.045f, 0.085f, 0.04f)
        // Bristles: little rolls of dough side by side.
        for (i in 0 until 4) for (k in 0 until 2) {
            val x = 0.41f + i * 0.06f
            val z = -0.035f + k * 0.07f
            color(if ((i + k) % 2 == 0) Palette.enamel else Palette.turquoise)
            tube(listOf(Vector3(x, 0.4f, z), Vector3(x, 0.29f, z)), 0.027f)
        }
        color(Palette.coral).ball(-0.16f, 0.4f, 0f, 0.075f)
    }.build()

    /** The paste ball cannon: a toothpaste tube on a cradle, nozzle along +x. Pivot at its middle. */
    val pasteTube: Mesh = md().apply {
        val profile = listOf(
            Vector2(0f, -0.32f), Vector2(0.08f, -0.32f), Vector2(0.17f, -0.25f), Vector2(0.2f, -0.12f),
            Vector2(0.2f, 0.17f), Vector2(0.16f, 0.25f), Vector2(0.085f, 0.3f), Vector2(0.085f, 0.41f), Vector2(0f, 0.41f),
        )
        append(md().color(Palette.enamel).lathe(0f, 0f, 0f, profile), layDown)
        append(md().color(Palette.coral).lathe(0f, -0.06f, 0f, MeshData.roundedCylinderProfile(0.208f, 0.1f, 0.03f)), layDown)
        append(md().color(Palette.turquoise).lathe(0f, 0.09f, 0f, MeshData.roundedCylinderProfile(0.208f, 0.07f, 0.03f)), layDown)
        append(md().color(Palette.coral).lathe(0f, 0.31f, 0f, MeshData.roundedCylinderProfile(0.11f, 0.08f, 0.03f)), layDown)
        // The squeezed, crimped end.
        color(Palette.enamel).roundedBox(-0.36f, 0f, 0f, 0.05f, 0.2f, 0.045f, 0.035f)
        color(Palette.line).tube(listOf(Vector3(-0.395f, -0.16f, 0f), Vector3(-0.395f, 0.16f, 0f)), 0.012f)
    }.build()

    val cradle: Mesh = md().apply {
        color(Palette.charcoal).roundedBox(0f, 0.2f, 0f, 0.07f, 0.08f, 0.07f, 0.035f)
        color(Palette.yellow).ball(0f, 0.27f, 0f, 0.16f, 0.1f, 0.16f)
    }.build()

    /** The whitening laser: a lamp head along +x with a glass lens. Pivot at its middle. */
    val laserHead: Mesh = md().apply {
        append(md().color(Palette.grape).lathe(0f, -0.26f, 0f, MeshData.capsuleProfile(0.17f, 0.52f)), layDown)
        append(md().color(Palette.cream).lathe(0f, 0.15f, 0f, MeshData.roundedCylinderProfile(0.19f, 0.09f, 0.035f)), layDown)
        color(Palette.glass).ball(0.27f, 0f, 0f, 0.06f, 0.13f, 0.13f)
        color(Palette.yellow).ball(-0.24f, 0.13f, 0f, 0.05f)
    }.build()

    val laserStand: Mesh = md().apply {
        color(Palette.charcoal).lathe(0f, 0.12f, 0f, MeshData.capsuleProfile(0.065f, 0.38f))
        color(Palette.grape).ball(0f, 0.18f, 0f, 0.15f, 0.08f, 0.15f)
    }.build()

    /** Switch posts; the lever is drawn on its own so it can open. */
    val switchPosts: Mesh = md().apply {
        color(Palette.steel).roundedBox(-0.2f, 0.19f, 0f, 0.045f, 0.06f, 0.065f, 0.03f)
        color(Palette.steel).roundedBox(0.2f, 0.19f, 0f, 0.045f, 0.06f, 0.065f, 0.03f)
    }.build()

    /** The lever, hinged at the origin and lying along +x when closed. */
    val switchLever: Mesh = md().apply {
        color(Palette.steel).tube(listOf(Vector3(0f, 0f, 0f), Vector3(0.42f, 0f, 0f)), 0.028f)
        color(Palette.coral).ball(0.5f, 0.02f, 0f, 0.065f)
    }.build()

    /** The glass of a fuse; see-through, so smooth. */
    val fuse: Mesh = md().apply {
        append(MeshData().plain().color(Palette.glass).lathe(0f, -0.22f, 0f, MeshData.capsuleProfile(0.08f, 0.44f)), lay(0.22f))
    }.build()

    val fuseCaps: Mesh = md().apply {
        append(md().color(Palette.steel).lathe(0f, -0.25f, 0f, MeshData.roundedCylinderProfile(0.09f, 0.1f, 0.03f)), lay(0.22f))
        append(md().color(Palette.steel).lathe(0f, 0.15f, 0f, MeshData.roundedCylinderProfile(0.09f, 0.1f, 0.03f)), lay(0.22f))
    }.build()

    /** The thin wire inside a fuse, recoloured when it blows. */
    val fuseWire: Mesh = md().tube(listOf(Vector3(-0.16f, 0.22f, 0f), Vector3(0f, 0.25f, 0f), Vector3(0.16f, 0.22f, 0f)), 0.013f).build()

    val resistor: Mesh = md().apply {
        append(md().color(Palette.wood).lathe(0f, -0.2f, 0f, MeshData.capsuleProfile(0.085f, 0.4f)), lay(0.2f))
        for ((i, c) in listOf(Palette.brick, Palette.grape, Palette.yellow).withIndex()) {
            append(md().color(c).lathe(0f, -0.1f + i * 0.08f, 0f, MeshData.roundedCylinderProfile(0.09f, 0.035f, 0.012f)), lay(0.2f))
        }
        color(Palette.steel).tube(listOf(Vector3(-0.36f, 0.09f, 0f), Vector3(-0.27f, 0.2f, 0f), Vector3(-0.2f, 0.2f, 0f)), 0.02f)
        color(Palette.steel).tube(listOf(Vector3(0.2f, 0.2f, 0f), Vector3(0.27f, 0.2f, 0f), Vector3(0.36f, 0.09f, 0f)), 0.02f)
    }.build()

    /** A wire clip: a little blob where wires meet. */
    val clip: Mesh = md().apply {
        color(Palette.yellow).ball(0f, 0.07f, 0f, 0.1f, 0.07f, 0.1f)
        color(Palette.charcoal).ball(0f, 0.12f, 0f, 0.035f)
    }.build()

    // ------------------------------------------------------------------ candies

    /** A sugar cube with an angry face on its +x side. Centre at the origin, half-size 0.24. */
    val sugarCube: Mesh = md().apply {
        color(Palette.sugar).roundedBox(0f, 0f, 0f, 0.24f, 0.24f, 0.24f, 0.09f)
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
        color(Palette.pink).ball(0f, 0.68f, 0f, 0.075f, 0.3f, 0.3f)
        val spiral = ArrayList<Vector3>()
        val turns = 2.6
        for (i in 0..80) {
            val t = i / 80.0
            val a = t * turns * 2 * PI
            val r = 0.03 + 0.23 * t
            spiral += Vector3(0.068f - 0.03f * (t * t).toFloat(), (0.68 + r * sin(a)).toFloat(), (r * cos(a)).toFloat())
        }
        color(Palette.cream).tube(spiral, 0.03f, 0.028f)
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

    // ------------------------------------------------------------------ the tooth

    /** The tooth the player defends: a cartoon molar on two roots, smiling toward the player (+z). */
    val tooth: Mesh = md().apply {
        color(Palette.enamel)
        for (side in listOf(-1f, 1f)) {
            tube(MeshData.smooth(listOf(Vector3(0.21f * side, 0.62f, 0f), Vector3(0.27f * side, 0.36f, 0.01f), Vector3(0.22f * side, 0.1f, 0.03f))), 0.19f, 0.08f)
        }
        roundedBox(0f, 0.84f, 0f, 0.47f, 0.29f, 0.35f, 0.25f)
        ball(-0.2f, 1.03f, 0f, 0.25f, 0.12f, 0.3f)
        ball(0.2f, 1.03f, 0f, 0.25f, 0.12f, 0.3f)
        // The shine every cartoon tooth has.
        color(Color.WHITE).tube(MeshData.smooth(listOf(Vector3(-0.38f, 0.78f, 0.28f), Vector3(-0.36f, 0.95f, 0.28f), Vector3(-0.26f, 1.04f, 0.27f))), 0.035f, 0.025f)
        // Face.
        color(Palette.charcoal).ball(-0.15f, 0.86f, 0.335f, 0.065f, 0.09f, 0.04f)
        color(Palette.charcoal).ball(0.15f, 0.86f, 0.335f, 0.065f, 0.09f, 0.04f)
        color(Color.WHITE).ball(-0.13f, 0.9f, 0.37f, 0.024f)
        color(Color.WHITE).ball(0.17f, 0.9f, 0.37f, 0.024f)
        color(Palette.charcoal).tube(MeshData.smooth(listOf(Vector3(-0.13f, 0.71f, 0.35f), Vector3(0f, 0.64f, 0.365f), Vector3(0.13f, 0.71f, 0.35f))), 0.022f)
        color(Palette.pink).ball(-0.29f, 0.74f, 0.29f, 0.075f, 0.05f, 0.04f)
        color(Palette.pink).ball(0.29f, 0.74f, 0.29f, 0.075f, 0.05f, 0.04f)
    }.build()

    /** The gum the tooth stands in, on a folded napkin. */
    val gum: Mesh = md().apply {
        color(Palette.cream).roundedBox(0f, 0.025f, 0f, 0.75f, 0.025f, 0.65f, 0.02f)
        color(Palette.coral).tube(listOf(Vector3(-0.72f, 0.05f, 0.5f), Vector3(0.72f, 0.05f, 0.5f)), 0.02f)
        color(Palette.coral).tube(listOf(Vector3(-0.72f, 0.05f, -0.5f), Vector3(0.72f, 0.05f, -0.5f)), 0.02f)
        color(Palette.gum).ball(0f, 0.07f, 0f, 0.6f, 0.13f, 0.48f)
    }.build()

    /** A soft round shadow: dark in the middle, fading to nothing at radius 1. Drawn see-through on the ground. */
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

    /** A ball of striped toothpaste, radius 1. */
    val pasteBall: Mesh = md().apply {
        color(Palette.enamel).ball(0f, 0f, 0f, 1f, slices = 24, stacks = 16)
        for ((c, tilt) in listOf(Palette.coral to 0f, Palette.turquoise to 60f, Palette.coral to 120f)) {
            val ring = (0..32).map { i ->
                val a = 2 * PI * i / 32
                Vector3(0f, (1.02 * cos(a)).toFloat(), (1.02 * sin(a)).toFloat()).rotate(Vector3.Z, tilt)
            }
            color(c).tube(ring, 0.16f, caps = false)
        }
    }.build()

    // ------------------------------------------------------------------ the kitchen

    /** The candy jar, tipped over on its side with its mouth toward +x; the glass is [jarGlass]. */
    val jarCandies: Mesh = md().apply {
        color(Palette.coral).ball(0.05f, 0.14f, 0.08f, 0.12f)
        color(Palette.yellow).ball(-0.15f, 0.14f, -0.06f, 0.12f)
        color(Palette.grape).ball(-0.32f, 0.13f, 0.05f, 0.11f)
        color(Palette.sugar).roundedBox(0.3f, 0.1f, -0.08f, 0.09f, 0.09f, 0.09f, 0.03f)
        color(Palette.pink).ball(0.48f, 0.08f, 0.12f, 0.08f)
        color(Palette.yellow).ball(0.62f, 0.06f, -0.1f, 0.06f)
        // The lid rolled away.
        color(Palette.coral).lathe(0.85f, 0f, 0.45f, MeshData.roundedCylinderProfile(0.3f, 0.09f, 0.04f))
    }.build()

    val jarGlass: Mesh = md().apply {
        plain()
        val glass = MeshData().plain().color(Palette.glass).lathe(
            0f, 0f, 0f,
            listOf(Vector2(0f, 0f), Vector2(0.24f, 0f), Vector2(0.3f, 0.06f), Vector2(0.31f, 0.42f), Vector2(0.27f, 0.54f), Vector2(0.27f, 0.66f), Vector2(0.0f, 0.66f)),
        )
        append(glass, Matrix4().translate(-0.25f, 0.31f, 0f).rotate(Vector3.Z, -90f))
    }.build()

    /** The wooden counter: planks with pressed grooves, a softly lumpy top, a thick front edge. The caller disposes it. */
    fun counter(width: Float, height: Float): Mesh = md().apply {
        val x0 = -0.7f
        val x1 = width + 0.7f
        val z0 = -0.75f
        val z1 = height + 0.7f
        // The body under the top is plain: kneaded at this size its lumps would poke through.
        dough = false
        color(Palette.woodDark).roundedBox((x0 + x1) / 2, -0.37f, (z0 + z1) / 2, (x1 - x0) / 2, 0.3f, (z1 - z0) / 2, 0.12f, steps = 18)
        val plank = 0.78f
        sheet(x0 + 0.06f, z0 + 0.06f, x1 - 0.06f, z1 - 0.06f, 0.06f,
            height = { x, z ->
                val lump = (MeshData.fbm(x * 0.9f, 3.1f, z * 0.9f) - 0.5f) * 0.05f
                val grain = (MeshData.fbm(x * 0.6f, 7.3f, z * 5f) - 0.5f) * 0.012f
                val d = ((x - x0) % plank).let { minOf(it, plank - it) }
                lump + grain - 0.03f * kotlin.math.exp(-d * d / 0.0012f)
            },
            paint = { x, z ->
                val k = ((x - x0) / plank).toInt()
                val base = if (k % 2 == 0) Palette.wood else Palette.woodLight
                val g = MeshData.fbm(x * 0.7f, 1.7f, z * 6f)
                Color(base).lerp(Palette.woodDark, (g - 0.35f).coerceIn(0f, 0.6f) * 0.5f)
            })
    }.toMesh()

    /** The tiled wall behind the counter, with a socket. Its foot runs along z = 0. The caller disposes it. */
    fun wall(width: Float): Mesh = md().apply {
        val x0 = -0.7f
        val x1 = width + 0.7f
        dough = false
        color(Palette.cream).roundedBox((x0 + x1) / 2, 1.6f, -0.12f, (x1 - x0) / 2, 1.7f, 0.12f, 0.06f, steps = 16)
        dough = true
        lumpiness = 0.08f
        var row = 0
        var y = 0.18f
        while (y < 3.2f) {
            var x = x0 + 0.1f + if (row % 2 == 0) 0f else 0.3f
            while (x < x1 - 0.2f) {
                color(if ((row + (x * 2).toInt()) % 3 == 0) Palette.turquoise else Palette.tileWall)
                roundedBox(x + 0.28f, y + 0.27f, 0.02f, 0.27f, 0.26f, 0.05f, 0.06f, steps = 6)
                x += 0.6f
            }
            y += 0.58f
            row++
        }
        // A wall socket: a pressed square with two holes (the mains come in a later level).
        color(Palette.surface).roundedBox(width - 1.2f, 0.75f, 0.1f, 0.24f, 0.24f, 0.07f, 0.08f)
        color(Palette.charcoal).ball(width - 1.28f, 0.75f, 0.17f, 0.035f, 0.05f, 0.02f)
        color(Palette.charcoal).ball(width - 1.12f, 0.75f, 0.17f, 0.035f, 0.05f, 0.02f)
    }.toMesh()

    /** A cutting board with a carrot on it, long side along +x. */
    val cuttingBoard: Mesh = md().apply {
        color(Palette.woodLight).roundedBox(0f, 0.07f, 0f, 0.8f, 0.07f, 0.52f, 0.06f)
        color(Palette.woodDark).ball(0.64f, 0.14f, 0f, 0.08f, 0.012f, 0.05f)
        color(Palette.carrot).tube(MeshData.smooth(listOf(Vector3(-0.5f, 0.2f, 0.1f), Vector3(-0.15f, 0.19f, 0.05f), Vector3(0.15f, 0.17f, -0.05f))), 0.075f, 0.02f)
        color(Palette.leaf)
        for (a in listOf(-30f, 0f, 30f)) {
            val dir = Vector3(-0.2f, 0.1f, 0f).rotate(Vector3.Y, a)
            tube(listOf(Vector3(-0.55f, 0.2f, 0.11f), Vector3(-0.55f, 0.2f, 0.11f).add(dir)), 0.025f, 0.012f)
        }
    }.build()

    /** A plate with two cookies. */
    val plate: Mesh = md().apply {
        color(Palette.cream).lathe(0f, 0f, 0f, listOf(
            Vector2(0f, 0f), Vector2(0.38f, 0f), Vector2(0.52f, 0.05f), Vector2(0.58f, 0.1f), Vector2(0.55f, 0.12f),
            Vector2(0.42f, 0.06f), Vector2(0f, 0.055f),
        ))
        color(Palette.turquoise).tube((0..40).map { i ->
            val a = 2 * PI * i / 40
            Vector3((0.53 * cos(a)).toFloat(), 0.11f, (0.53 * sin(a)).toFloat())
        }, 0.02f, caps = false)
        for ((x, z) in listOf(-0.15f to 0.05f, 0.17f to -0.08f)) {
            color(Palette.wood).lathe(x, 0.05f, z, MeshData.roundedCylinderProfile(0.17f, 0.06f, 0.03f))
            color(Palette.woodDark)
            for (k in 0 until 4) ball(x + 0.09f * cos(k * 1.7f), 0.115f, z + 0.09f * sin(k * 1.7f), 0.025f)
        }
    }.build()

    /** A mug of cocoa. */
    val mug: Mesh = md().apply {
        color(Palette.coral).lathe(0f, 0f, 0f, listOf(
            Vector2(0f, 0f), Vector2(0.26f, 0f), Vector2(0.3f, 0.04f), Vector2(0.31f, 0.5f), Vector2(0.28f, 0.55f), Vector2(0.24f, 0.5f), Vector2(0f, 0.48f),
        ))
        color(Palette.woodDark).ball(0f, 0.49f, 0f, 0.25f, 0.02f, 0.25f)
        color(Palette.coral).tube(MeshData.smooth(listOf(Vector3(0.29f, 0.42f, 0f), Vector3(0.45f, 0.38f, 0f), Vector3(0.45f, 0.18f, 0f), Vector3(0.29f, 0.14f, 0f))), 0.05f)
    }.build()

    /** A bowl of fruit. */
    val fruitBowl: Mesh = md().apply {
        color(Palette.grape).lathe(0f, 0f, 0f, listOf(
            Vector2(0f, 0f), Vector2(0.25f, 0f), Vector2(0.45f, 0.14f), Vector2(0.55f, 0.34f), Vector2(0.5f, 0.36f), Vector2(0.4f, 0.2f), Vector2(0f, 0.13f),
        ))
        color(Palette.carrot).ball(-0.15f, 0.33f, 0.08f, 0.17f)
        color(Palette.brick).ball(0.16f, 0.32f, -0.05f, 0.16f, 0.15f, 0.16f)
        color(Palette.woodDark).tube(listOf(Vector3(0.16f, 0.45f, -0.05f), Vector3(0.18f, 0.53f, -0.06f)), 0.015f)
        color(Palette.leaf).ball(0.22f, 0.51f, -0.06f, 0.05f, 0.015f, 0.03f)
        color(Palette.yellow).tube(MeshData.smooth(listOf(Vector3(-0.3f, 0.4f, -0.2f), Vector3(0f, 0.36f, -0.28f), Vector3(0.3f, 0.42f, -0.18f))), 0.06f, 0.04f)
    }.build()

    /** A rolling pin along x. */
    val rollingPin: Mesh = md().apply {
        append(md().color(Palette.woodLight).lathe(0f, -0.48f, 0f, MeshData.roundedCylinderProfile(0.17f, 0.96f, 0.06f)), lay(0.17f))
        append(md().color(Palette.wood).lathe(0f, -0.82f, 0f, MeshData.capsuleProfile(0.065f, 0.36f)), lay(0.17f))
        append(md().color(Palette.wood).lathe(0f, 0.46f, 0f, MeshData.capsuleProfile(0.065f, 0.36f)), lay(0.17f))
    }.build()

    /** A salt shaker. */
    val saltShaker: Mesh = md().apply {
        color(Palette.surface).lathe(0f, 0f, 0f, listOf(Vector2(0f, 0f), Vector2(0.15f, 0f), Vector2(0.17f, 0.05f), Vector2(0.14f, 0.25f), Vector2(0.15f, 0.3f), Vector2(0f, 0.3f)))
        color(Palette.steel).lathe(0f, 0.29f, 0f, listOf(Vector2(0f, 0f), Vector2(0.15f, 0f), Vector2(0.14f, 0.07f), Vector2(0.1f, 0.13f), Vector2(0f, 0.15f)))
        color(Palette.charcoal)
        for (k in 0 until 5) ball(0.06f * cos(k * 1.26f), 0.435f, 0.06f * sin(k * 1.26f), 0.012f)
    }.build()

    override fun dispose() = meshes.forEach { it.dispose() }

    companion object {
        /** Height of the top of a machine's pedestal; machine heads are drawn this much higher. */
        const val PEDESTAL = 0.32f
    }
}
