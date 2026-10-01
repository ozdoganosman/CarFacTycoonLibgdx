package com.toyquaise.toothfort.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.random.Random

/**
 * Draws clay meshes: a shadow map from the light first, then the colour pass. Collect a frame's
 * draws between [begin] and [end]; opaque ones are drawn first, see-through ones after, back to front.
 */
class ClayRenderer : Disposable {
    private val shader: ShaderProgram
    private val depthShader: ShaderProgram
    private val shadowFbo: FrameBuffer
    private val grain: Texture

    /** Direction toward the light: from the upper left and behind, like the site's drop shadow. */
    val lightDir: Vector3 = Vector3(-0.62f, 1.1f, -0.78f).nor()
    private val lightCam = OrthographicCamera()
    private lateinit var camera: Camera

    class Item {
        lateinit var mesh: Mesh
        val world = Matrix4()
        val color = Color()
        val flash = Color()
        var wobble = 0f
        var seed = 0f
        var grain = 0.6f
        var grainScale = 3f
        var emissive = 0f
        var castShadow = true
        var depth = 0f
    }

    private val pool = ArrayList<Item>()
    private var used = 0
    private val opaque = ArrayList<Item>()
    private val blended = ArrayList<Item>()
    private val normalMatrix = Matrix3()
    private val tmp = Vector3()

    init {
        ShaderProgram.pedantic = false
        shader = compile("shaders/clay.vert", "shaders/clay.frag")
        depthShader = compile("shaders/depth.vert", "shaders/depth.frag")
        shadowFbo = FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_SIZE, SHADOW_SIZE, true)
        shadowFbo.colorBufferTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        grain = grainTexture()
    }

    private fun compile(vert: String, frag: String): ShaderProgram {
        val p = ShaderProgram(Gdx.files.internal(vert), Gdx.files.internal(frag))
        check(p.isCompiled) { "$vert / $frag: ${p.log}" }
        return p
    }

    /**
     * Starts a frame seen through [camera]. The shadow map covers a circle of [radius] around
     * [focus] on the table.
     */
    fun begin(camera: Camera, focus: Vector3, radius: Float) {
        this.camera = camera
        used = 0
        opaque.clear()
        blended.clear()
        lightCam.viewportWidth = radius * 2
        lightCam.viewportHeight = radius * 2
        lightCam.near = 1f
        lightCam.far = 80f
        lightCam.position.set(focus).mulAdd(lightDir, 40f)
        lightCam.direction.set(lightDir).scl(-1f)
        lightCam.up.set(Vector3.Y)
        lightCam.normalizeUp()
        lightCam.update()
    }

    /** Queues [mesh] at [world]. The returned item can be adjusted until [end]. */
    fun draw(mesh: Mesh, world: Matrix4, color: Color = Color.WHITE, wobble: Float = 0.025f, seed: Float = 0f): Item {
        if (used == pool.size) pool += Item()
        val it = pool[used++]
        it.mesh = mesh
        it.world.set(world)
        it.color.set(color)
        it.flash.set(0f, 0f, 0f, 0f)
        it.wobble = wobble
        it.seed = seed
        it.grain = 0.6f
        it.grainScale = 3f
        it.emissive = 0f
        it.castShadow = true
        if (color.a < 0.999f) blended += it else opaque += it
        return it
    }

    fun end() {
        renderShadows()
        renderColour()
    }

    private fun renderShadows() {
        shadowFbo.begin()
        Gdx.gl.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE)
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_FRONT)
        depthShader.bind()
        depthShader.setUniformMatrix("u_lightProjView", lightCam.combined)
        for (it in opaque) {
            if (!it.castShadow) continue
            depthShader.setUniformMatrix("u_world", it.world)
            depthShader.setUniformf("u_wobble", it.wobble)
            depthShader.setUniformf("u_seed", it.seed)
            it.mesh.render(depthShader, GL20.GL_TRIANGLES)
        }
        Gdx.gl.glCullFace(GL20.GL_BACK)
        shadowFbo.end()
    }

    private fun renderColour() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)
        shader.bind()
        shadowFbo.colorBufferTexture.bind(1)
        grain.bind(0)
        shader.setUniformi("u_grainTex", 0)
        shader.setUniformi("u_shadowMap", 1)
        shader.setUniformf("u_shadowTexel", 1f / SHADOW_SIZE)
        shader.setUniformf("u_shadows", 1f)
        shader.setUniformMatrix("u_projView", camera.combined)
        shader.setUniformMatrix("u_lightProjView", lightCam.combined)
        shader.setUniformf("u_lightDir", lightDir)
        shader.setUniformf("u_eye", camera.position)

        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        opaque.forEach(::renderItem)

        if (blended.isNotEmpty()) {
            for (it in blended) it.depth = it.world.getTranslation(tmp).dst2(camera.position)
            blended.sortByDescending { it.depth }
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glDepthMask(false)
            blended.forEach(::renderItem)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
    }

    private fun renderItem(it: Item) {
        shader.setUniformMatrix("u_world", it.world)
        shader.setUniformMatrix("u_normalMatrix", normalMatrix.set(it.world).inv().transpose())
        shader.setUniformf("u_color", it.color)
        shader.setUniformf("u_flash", it.flash.r, it.flash.g, it.flash.b)
        shader.setUniformf("u_wobble", it.wobble)
        shader.setUniformf("u_seed", it.seed)
        shader.setUniformf("u_grain", it.grain)
        shader.setUniformf("u_grainScale", it.grainScale)
        shader.setUniformf("u_emissive", it.emissive)
        it.mesh.render(shader, GL20.GL_TRIANGLES)
    }

    /** A small tileable noise: RGB a fine grain to bend normals with, A a coarse blotch for colour. */
    private fun grainTexture(): Texture {
        val size = 64
        val rnd = Random(7)
        fun lattice(n: Int) = Array(n) { FloatArray(n) { rnd.nextFloat() } }
        fun sample(l: Array<FloatArray>, x: Float, y: Float): Float {
            val n = l.size
            val fx = x * n / size
            val fy = y * n / size
            val x0 = fx.toInt()
            val y0 = fy.toInt()
            val tx = MathUtils.clamp(fx - x0, 0f, 1f).let { it * it * (3 - 2 * it) }
            val ty = MathUtils.clamp(fy - y0, 0f, 1f).let { it * it * (3 - 2 * it) }
            val a = l[y0 % n][x0 % n]
            val b = l[y0 % n][(x0 + 1) % n]
            val c = l[(y0 + 1) % n][x0 % n]
            val d = l[(y0 + 1) % n][(x0 + 1) % n]
            return MathUtils.lerp(MathUtils.lerp(a, b, tx), MathUtils.lerp(c, d, tx), ty)
        }
        val fine = Array(3) { lattice(32) }
        val mid = Array(3) { lattice(16) }
        val coarse = lattice(4)
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        for (y in 0 until size) for (x in 0 until size) {
            val fx = x.toFloat()
            val fy = y.toFloat()
            val r = 0.6f * sample(fine[0], fx, fy) + 0.4f * sample(mid[0], fx, fy)
            val g = 0.6f * sample(fine[1], fx, fy) + 0.4f * sample(mid[1], fx, fy)
            val b = 0.6f * sample(fine[2], fx, fy) + 0.4f * sample(mid[2], fx, fy)
            val a = sample(coarse, fx, fy)
            pm.drawPixel(x, y, Color.rgba8888(r, g, b, a))
        }
        val t = Texture(pm, true)
        pm.dispose()
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        return t
    }

    override fun dispose() {
        shader.dispose()
        depthShader.dispose()
        shadowFbo.dispose()
        grain.dispose()
    }

    companion object {
        const val SHADOW_SIZE = 2048
    }
}
