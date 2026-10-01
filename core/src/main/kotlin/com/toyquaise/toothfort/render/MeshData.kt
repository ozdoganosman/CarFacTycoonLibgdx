package com.toyquaise.toothfort.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.FloatArray
import com.badlogic.gdx.utils.ShortArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Geometry under construction: positions, normals and a colour per vertex, triangles wound
 * counter-clockwise seen from outside. The shapes are the vocabulary of the Toyquaise clay art,
 * in 3D: balls, rounded boxes, turned (lathe) shapes and rolled snakes (tubes).
 *
 * Every shape comes out "kneaded" unless [dough] is switched off: finely tessellated, its
 * surface pushed in and out by a slow noise, as uneven as something rolled by hand, with a thumb
 * pressed into it here and there. The normals are bent to match, so the light finds every lump.
 */
class MeshData {
    private val v = FloatArray(4096)
    private val idx = ShortArray(8192)
    private var color = Color.WHITE.toFloatBits()

    /** How lumpy new shapes are, as a share of their size (0 = machine-made). */
    var lumpiness = 0.045f

    /** Thumb prints per shape, at most. */
    var thumbs = 2

    /** Whether new shapes are kneaded at all. */
    var dough = true

    private var seed = 1

    val vertexCount: Int get() = v.size / STRIDE

    fun color(c: Color): MeshData = apply { color = c.toFloatBits() }

    /** Shapes from here on are smooth and exact (shadow discs, glass). */
    fun plain(): MeshData = apply { dough = false }

    fun seed(s: Int): MeshData = apply { seed = s }

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        check(vertexCount < 65535) { "mesh too large" }
        v.add(x, y, z)
        v.add(nx, ny, nz)
        v.add(color)
        return vertexCount - 1
    }

    fun triangle(a: Int, b: Int, c: Int) {
        idx.add(a.toShort())
        idx.add(b.toShort())
        idx.add(c.toShort())
    }

    /** A quad a-b-c-d, counter-clockwise. */
    fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(a, c, d)
    }

    /** Copies [other] in, moved by [transform] and painted [paint] (multiplied with its own colours). */
    fun append(other: MeshData, transform: Matrix4, paint: Color = Color.WHITE): MeshData {
        val base = vertexCount
        check(base + other.vertexCount < 65535) { "mesh too large" }
        val normalMatrix = Matrix3().set(transform).inv().transpose()
        val p = Vector3()
        val n = Vector3()
        val tmp = Color()
        for (i in 0 until other.vertexCount) {
            val o = i * STRIDE
            p.set(other.v[o], other.v[o + 1], other.v[o + 2]).mul(transform)
            n.set(other.v[o + 3], other.v[o + 4], other.v[o + 5]).mul(normalMatrix).nor()
            Color.abgr8888ToColor(tmp, other.v[o + 6])
            tmp.mul(paint)
            v.add(p.x, p.y, p.z)
            v.add(n.x, n.y, n.z)
            v.add(tmp.toFloatBits())
        }
        for (i in 0 until other.idx.size) idx.add((other.idx[i] + base).toShort())
        return this
    }

    fun toMesh(): Mesh {
        val mesh = Mesh(
            true, vertexCount, idx.size,
            VertexAttribute(Usage.Position, 3, "a_position"),
            VertexAttribute(Usage.Normal, 3, "a_normal"),
            VertexAttribute(Usage.ColorPacked, 4, "a_color"),
        )
        mesh.setVertices(v.items, 0, v.size)
        mesh.setIndices(idx.items, 0, idx.size)
        return mesh
    }

    // ------------------------------------------------------------------ kneading

    /**
     * Pushes the vertices from [from] on in and out: a slow lumpy noise of [size] × [lumpiness],
     * and up to [thumbs] round thumb prints. The normals follow the displacement's slope.
     */
    private fun knead(from: Int, size: Float) {
        if (!dough || lumpiness <= 0f || from >= vertexCount) return
        val s = seed++
        val rnd = Random(s * 7919 + 13)
        val amount = size * lumpiness
        val freq = 2.4f / size
        class Thumb(val x: Float, val y: Float, val z: Float, val r: Float, val depth: Float)
        val prints = ArrayList<Thumb>()
        if (size > 0.1f) {
            repeat(rnd.nextInt(thumbs + 1)) {
                val i = from + rnd.nextInt(vertexCount - from)
                val r = size * (0.28f + rnd.nextFloat() * 0.18f)
                prints += Thumb(px(i), py(i), pz(i), r, r * (0.12f + rnd.nextFloat() * 0.08f))
            }
        }
        val ox = rnd.nextFloat() * 100f
        val oy = rnd.nextFloat() * 100f
        val oz = rnd.nextFloat() * 100f
        fun field(x: Float, y: Float, z: Float): Float {
            var d = amount * (fbm(x * freq + ox, y * freq + oy, z * freq + oz) - 0.5f) * 2f
            for (t in prints) {
                val dx = x - t.x
                val dy = y - t.y
                val dz = z - t.z
                d -= t.depth * exp(-(dx * dx + dy * dy + dz * dz) / (t.r * t.r))
            }
            return d
        }
        val e = size * 0.01f
        for (i in from until vertexCount) {
            val o = i * STRIDE
            val x = v[o]
            val y = v[o + 1]
            val z = v[o + 2]
            var nx = v[o + 3]
            var ny = v[o + 4]
            var nz = v[o + 5]
            val d = field(x, y, z)
            val gx = (field(x + e, y, z) - field(x - e, y, z)) / (2 * e)
            val gy = (field(x, y + e, z) - field(x, y - e, z)) / (2 * e)
            val gz = (field(x, y, z + e) - field(x, y, z - e)) / (2 * e)
            val gn = gx * nx + gy * ny + gz * nz
            // Tilt the normal against the slope of the bump (the tangential part of the gradient).
            val tx = nx - (gx - gn * nx)
            val ty = ny - (gy - gn * ny)
            val tz = nz - (gz - gn * nz)
            val len = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-6f)
            v[o] = x + nx * d
            v[o + 1] = y + ny * d
            v[o + 2] = z + nz * d
            nx = tx / len; ny = ty / len; nz = tz / len
            v[o + 3] = nx
            v[o + 4] = ny
            v[o + 5] = nz
        }
    }

    private fun px(i: Int) = v[i * STRIDE]
    private fun py(i: Int) = v[i * STRIDE + 1]
    private fun pz(i: Int) = v[i * STRIDE + 2]

    // ------------------------------------------------------------------ shapes

    /** An ellipsoid with radii (rx, ry, rz) around (cx, cy, cz). */
    fun ball(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float = rx, rz: Float = rx, slices: Int = 0, stacks: Int = 0): MeshData {
        val size = maxOf(rx, ry, rz)
        val sl = if (slices > 0) slices else (24 + size * 120).toInt().coerceIn(16, 56)
        val st = if (stacks > 0) stacks else (sl * 0.7f).toInt().coerceAtLeast(8)
        val base = vertexCount
        for (j in 0..st) {
            val phi = PI * j / st
            val sy = cos(phi).toFloat()
            val r = sin(phi).toFloat()
            for (i in 0..sl) {
                val theta = 2 * PI * i / sl
                val dx = (r * cos(theta)).toFloat()
                val dz = (r * sin(theta)).toFloat()
                // Normal of an ellipsoid: the direction divided by the radii.
                val n = Vector3(dx / rx, sy / ry, dz / rz).nor()
                vertex(cx + dx * rx, cy + sy * ry, cz + dz * rz, n.x, n.y, n.z)
            }
        }
        val row = sl + 1
        for (j in 0 until st) for (i in 0 until sl) {
            val a = base + j * row + i
            val b = a + row
            quad(a, a + 1, b + 1, b)
        }
        knead(base, size)
        return this
    }

    /**
     * A box with half-sizes (hx, hy, hz) and edges rounded by [r]: a finely divided cube whose
     * every point is pulled onto the rounded surface, so even the flat faces can be kneaded.
     */
    fun roundedBox(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, r: Float, steps: Int = 0): MeshData {
        val size = maxOf(hx, hy, hz)
        val base = vertexCount
        val ix = hx - r
        val iy = hy - r
        val iz = hz - r
        // One face at a time: origin corner, then two edge directions (u × v points outward).
        val faces = listOf(
            floatArrayOf(1f, -1f, 1f, 0f, 0f, -1f, 0f, 1f, 0f),
            floatArrayOf(-1f, -1f, -1f, 0f, 0f, 1f, 0f, 1f, 0f),
            floatArrayOf(-1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, -1f),
            floatArrayOf(-1f, -1f, -1f, 1f, 0f, 0f, 0f, 0f, 1f),
            floatArrayOf(-1f, -1f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(1f, -1f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
        )
        val hs = floatArrayOf(hx, hy, hz)
        for (f in faces) {
            // Divide each face edge by its length so cells stay about square.
            val lu = 2 * (abs(f[3]) * hx + abs(f[4]) * hy + abs(f[5]) * hz)
            val lv = 2 * (abs(f[6]) * hx + abs(f[7]) * hy + abs(f[8]) * hz)
            val cell = if (steps > 0) maxOf(lu, lv) / steps else (size * 0.09f).coerceIn(0.012f, 0.06f)
            val nu = ceil(lu / cell).toInt().coerceIn(2, 60)
            val nv = ceil(lv / cell).toInt().coerceIn(2, 60)
            val first = vertexCount
            for (j in 0..nv) for (i in 0..nu) {
                val u = i.toFloat() / nu * 2
                val w = j.toFloat() / nv * 2
                val qx = (f[0] + f[3] * u + f[6] * w) * hs[0]
                val qy = (f[1] + f[4] * u + f[7] * w) * hs[1]
                val qz = (f[2] + f[5] * u + f[8] * w) * hs[2]
                val kx = MathUtils.clamp(qx, -ix, ix)
                val ky = MathUtils.clamp(qy, -iy, iy)
                val kz = MathUtils.clamp(qz, -iz, iz)
                var dx = qx - kx
                var dy = qy - ky
                var dz = qz - kz
                val len = sqrt(dx * dx + dy * dy + dz * dz)
                if (len < 1e-6f) { dx = 0f; dy = 0f; dz = 0f } else { dx /= len; dy /= len; dz /= len }
                vertex(cx + kx + dx * r, cy + ky + dy * r, cz + kz + dz * r, dx, dy, dz)
            }
            val row = nu + 1
            for (j in 0 until nv) for (i in 0 until nu) {
                val a = first + j * row + i
                quad(a, a + 1, a + row + 1, a + row)
            }
        }
        knead(base, size)
        return this
    }

    /**
     * A turned shape: [profile] is (radius, height) from bottom to top, revolved around the
     * vertical axis through (cx, cy, cz). Start and end at radius 0 to close it.
     */
    fun lathe(cx: Float, cy: Float, cz: Float, profile: List<Vector2>, slices: Int = 0): MeshData {
        val maxR = profile.maxOf { it.x }
        val height = profile.maxOf { it.y } - profile.minOf { it.y }
        val size = maxOf(maxR, height / 2)
        val prof = subdivide(profile, (size * 0.07f).coerceIn(0.01f, 0.05f))
        val sl = if (slices > 0) maxOf(slices, 24) else (24 + maxR * 140).toInt().coerceIn(20, 64)
        val base = vertexCount
        val count = prof.size
        for (k in 0 until count) {
            val prev = prof[maxOf(0, k - 1)]
            val next = prof[minOf(count - 1, k + 1)]
            var tx = next.x - prev.x
            var ty = next.y - prev.y
            val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-6f)
            tx /= len; ty /= len
            // Outward normal of a profile running upward: (ty, -tx).
            var nr = ty
            var ny = -tx
            if (prof[k].x < 1e-5f) { nr = 0f; ny = if (k == 0) -1f else 1f }
            for (i in 0..sl) {
                val theta = 2 * PI * i / sl
                val c = cos(theta).toFloat()
                val s = sin(theta).toFloat()
                val r = prof[k].x
                vertex(cx + r * c, cy + prof[k].y, cz + r * s, nr * c, ny, nr * s)
            }
        }
        val row = sl + 1
        for (k in 0 until count - 1) for (i in 0 until sl) {
            val a = base + k * row + i
            val b = a + row
            quad(a, b, b + 1, a + 1)
        }
        knead(base, size)
        return this
    }

    private fun subdivide(profile: List<Vector2>, maxStep: Float): List<Vector2> {
        val out = ArrayList<Vector2>()
        for (i in 0 until profile.size - 1) {
            val a = profile[i]
            val b = profile[i + 1]
            val n = ceil(a.dst(b) / maxStep).toInt().coerceAtLeast(1)
            for (s in 0 until n) out += Vector2(a).lerp(b, s.toFloat() / n)
        }
        out += profile.last()
        return out
    }

    /**
     * A rolled snake of dough along [points] with rounded ends. [radius] may taper from start
     * to end with [endRadius].
     */
    fun tube(points: List<Vector3>, radius: Float, endRadius: Float = radius, sides: Int = 0, caps: Boolean = true): MeshData {
        if (points.size < 2) return this
        val base = vertexCount
        val path = resample(points, maxOf(radius, endRadius) * 0.7f)
        val count = path.size
        val sd = if (sides > 0) maxOf(sides, 10) else (12 + radius * 160).toInt().coerceIn(10, 32)
        val tangent = Vector3()
        val normal = Vector3()
        val binormal = Vector3()
        tangentAt(path, 0, tangent)
        normal.set(if (abs(tangent.y) < 0.9f) Vector3.Y else Vector3.X).crs(tangent).nor()
        binormal.set(tangent).crs(normal).nor()
        val prevT = Vector3(tangent)
        val dir = Vector3()
        val total = path.zipWithNext { a, b -> a.dst(b) }.sum().coerceAtLeast(1e-6f)
        var travelled = 0f
        for (k in 0 until count) {
            tangentAt(path, k, tangent)
            if (k > 0) {
                travelled += path[k].dst(path[k - 1])
                // Turn the frame by the rotation that takes the previous tangent to this one.
                val axis = Vector3(prevT).crs(tangent)
                val sinA = axis.len()
                if (sinA > 1e-6f) {
                    val angle = Math.toDegrees(kotlin.math.atan2(sinA.toDouble(), prevT.dot(tangent).toDouble())).toFloat()
                    axis.nor()
                    normal.rotate(axis, angle)
                    binormal.set(tangent).crs(normal).nor()
                    normal.set(binormal).crs(tangent).nor()
                }
                prevT.set(tangent)
            }
            val r = radius + (endRadius - radius) * (travelled / total)
            for (i in 0..sd) {
                val a = 2 * PI * i / sd
                dir.set(normal).scl(cos(a).toFloat()).mulAdd(binormal, sin(a).toFloat())
                val p = path[k]
                vertex(p.x + dir.x * r, p.y + dir.y * r, p.z + dir.z * r, dir.x, dir.y, dir.z)
            }
        }
        val row = sd + 1
        for (k in 0 until count - 1) for (i in 0 until sd) {
            val a = base + k * row + i
            val b = a + row
            // Around the tube +angle runs to the right of the tangent: counter-clockwise is a, a+1, b+1, b.
            quad(a, a + 1, b + 1, b)
        }
        val kneaded = dough
        dough = false
        if (caps) {
            ball(path.first().x, path.first().y, path.first().z, radius, slices = sd, stacks = sd / 2 + 2)
            ball(path.last().x, path.last().y, path.last().z, endRadius, slices = sd, stacks = sd / 2 + 2)
        }
        dough = kneaded
        // A snake is kneaded by its thickness, not its length: many small lumps along it.
        knead(base, maxOf(radius, endRadius) * 2.2f)
        return this
    }

    private fun resample(points: List<Vector3>, maxStep: Float): List<Vector3> {
        val out = ArrayList<Vector3>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val n = ceil(a.dst(b) / maxStep).toInt().coerceAtLeast(1)
            for (s in 0 until n) out += Vector3(a).lerp(b, s.toFloat() / n)
        }
        out += points.last().cpy()
        return out
    }

    private fun tangentAt(points: List<Vector3>, k: Int, out: Vector3) {
        val a = points[maxOf(0, k - 1)]
        val b = points[minOf(points.size - 1, k + 1)]
        out.set(b).sub(a).nor()
    }

    /**
     * A floor of dough from (x0, z0) to (x1, z1), [step] apart, raised by [height] and painted by
     * [paint]. Normals come from the slope of [height].
     */
    fun sheet(x0: Float, z0: Float, x1: Float, z1: Float, step: Float, height: (Float, Float) -> Float, paint: (Float, Float) -> Color): MeshData {
        val nx = ceil((x1 - x0) / step).toInt()
        val nz = ceil((z1 - z0) / step).toInt()
        val base = vertexCount
        val e = step * 0.5f
        val keep = color
        for (j in 0..nz) for (i in 0..nx) {
            val x = x0 + (x1 - x0) * i / nx
            val z = z0 + (z1 - z0) * j / nz
            val y = height(x, z)
            val dx = (height(x + e, z) - height(x - e, z)) / (2 * e)
            val dz = (height(x, z + e) - height(x, z - e)) / (2 * e)
            val n = Vector3(-dx, 1f, -dz).nor()
            color = paint(x, z).toFloatBits()
            vertex(x, y, z, n.x, n.y, n.z)
        }
        color = keep
        val row = nx + 1
        for (j in 0 until nz) for (i in 0 until nx) {
            val a = base + j * row + i
            quad(a, a + row, a + row + 1, a + 1)
        }
        return this
    }

    companion object {
        /** x, y, z, nx, ny, nz, packed colour. */
        const val STRIDE = 7

        /** A smooth curve through [points] (Catmull-Rom), [steps] samples per span. */
        fun smooth(points: List<Vector3>, steps: Int = 6): List<Vector3> {
            if (points.size < 3) return points
            val out = ArrayList<Vector3>()
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]
                for (s in 0 until steps) {
                    val t = s.toFloat() / steps
                    val t2 = t * t
                    val t3 = t2 * t
                    fun f(a: Float, b: Float, c: Float, d: Float) =
                        0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
                    out += Vector3(f(p0.x, p1.x, p2.x, p3.x), f(p0.y, p1.y, p2.y, p3.y), f(p0.z, p1.z, p2.z, p3.z))
                }
            }
            out += points.last().cpy()
            return out
        }

        /** Profile of a capsule standing on y = 0: radius [r], total height [h]. */
        fun capsuleProfile(r: Float, h: Float, steps: Int = 10): List<Vector2> {
            val out = ArrayList<Vector2>()
            for (i in 0..steps) {
                val a = -PI / 2 + PI / 2 * i / steps
                out += Vector2((r * cos(a)).toFloat(), (r + r * sin(a)).toFloat())
            }
            for (i in 0..steps) {
                val a = PI / 2 * i / steps
                out += Vector2((r * cos(a)).toFloat(), (h - r + r * sin(a)).toFloat())
            }
            return out
        }

        /** Profile of a cylinder standing on y = 0 with edges rounded by [bevel]. */
        fun roundedCylinderProfile(r: Float, h: Float, bevel: Float, steps: Int = 8): List<Vector2> {
            val out = ArrayList<Vector2>()
            out += Vector2(0f, 0f)
            for (i in 0..steps) {
                val a = -PI / 2 + PI / 2 * i / steps
                out += Vector2((r - bevel + bevel * cos(a)).toFloat(), (bevel + bevel * sin(a)).toFloat())
            }
            for (i in 0..steps) {
                val a = PI / 2 * i / steps
                out += Vector2((r - bevel + bevel * cos(a)).toFloat(), (h - bevel + bevel * sin(a)).toFloat())
            }
            out += Vector2(0f, h)
            return out
        }

        // A small value noise, the same idea as in the shaders.
        private fun hash(x: Int, y: Int, z: Int): Float {
            var h = x * 374761393 + y * 668265263 + z * 1274126177
            h = (h xor (h ushr 13)) * 1103515245
            h = h xor (h ushr 16)
            return (h and 0xffffff) / 16777215f
        }

        private fun noise(x: Float, y: Float, z: Float): Float {
            val ix = floor(x).toInt()
            val iy = floor(y).toInt()
            val iz = floor(z).toInt()
            var fx = x - ix
            var fy = y - iy
            var fz = z - iz
            fx = fx * fx * (3 - 2 * fx)
            fy = fy * fy * (3 - 2 * fy)
            fz = fz * fz * (3 - 2 * fz)
            fun l(a: Float, b: Float, t: Float) = a + (b - a) * t
            return l(
                l(l(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx), l(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy),
                l(l(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx), l(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy),
                fz,
            )
        }

        /** Two octaves: broad lumps with a little unevenness on them. */
        fun fbm(x: Float, y: Float, z: Float): Float = noise(x, y, z) * 0.72f + noise(x * 2.3f + 17f, y * 2.3f + 5f, z * 2.3f + 11f) * 0.28f
    }
}
