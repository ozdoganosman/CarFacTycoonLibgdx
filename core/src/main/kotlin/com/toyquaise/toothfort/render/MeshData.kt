package com.toyquaise.toothfort.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.math.Matrix3
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.FloatArray
import com.badlogic.gdx.utils.ShortArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry under construction: positions, normals and a colour per vertex, triangles wound
 * counter-clockwise seen from outside. The shapes are the vocabulary of the Toyquaise clay art,
 * in 3D: balls, rounded boxes, turned (lathe) shapes and rolled snakes (tubes).
 */
class MeshData {
    private val v = FloatArray(1024)
    private val idx = ShortArray(2048)
    private var color = Color.WHITE.toFloatBits()

    val vertexCount: Int get() = v.size / STRIDE

    fun color(c: Color): MeshData = apply { color = c.toFloatBits() }

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

    // ------------------------------------------------------------------ shapes

    /** An ellipsoid with radii (rx, ry, rz) around (cx, cy, cz). */
    fun ball(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float = rx, rz: Float = rx, slices: Int = 20, stacks: Int = 14): MeshData {
        val base = vertexCount
        for (j in 0..stacks) {
            val phi = PI * j / stacks
            val sy = cos(phi).toFloat()
            val r = sin(phi).toFloat()
            for (i in 0..slices) {
                val theta = 2 * PI * i / slices
                val dx = (r * cos(theta)).toFloat()
                val dz = (r * sin(theta)).toFloat()
                // Normal of an ellipsoid: the direction divided by the radii.
                val n = Vector3(dx / rx, sy / ry, dz / rz).nor()
                vertex(cx + dx * rx, cy + sy * ry, cz + dz * rz, n.x, n.y, n.z)
            }
        }
        sphereIndices(base, slices, stacks)
        return this
    }

    /**
     * A box with half-sizes (hx, hy, hz) and rounded edges of radius [r]: a ball of radius r
     * swept over the inner box (each sphere vertex pushed out to its octant's corner).
     */
    fun roundedBox(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, r: Float, slices: Int = 16, stacks: Int = 10): MeshData {
        val ix = hx - r
        val iy = hy - r
        val iz = hz - r
        val base = vertexCount
        for (j in 0..stacks) {
            // Offset by half a stack so no ring sits exactly on the equator.
            val phi = when (j) {
                0 -> 0.0
                stacks -> PI
                else -> PI * (j - 0.5 + 0.5 * j.toDouble() / stacks) / stacks
            }
            val ny = cos(phi).toFloat()
            val ring = sin(phi).toFloat()
            for (i in 0..slices) {
                val theta = 2 * PI * (i + 0.5) / slices
                val nx = (ring * cos(theta)).toFloat()
                val nz = (ring * sin(theta)).toFloat()
                val sx = if (nx >= 0) ix else -ix
                val sy = if (ny >= 0) iy else -iy
                val sz = if (nz >= 0) iz else -iz
                vertex(cx + sx + nx * r, cy + sy + ny * r, cz + sz + nz * r, nx, ny, nz)
            }
        }
        sphereIndices(base, slices, stacks)
        return this
    }

    private fun sphereIndices(base: Int, slices: Int, stacks: Int) {
        val row = slices + 1
        for (j in 0 until stacks) {
            for (i in 0 until slices) {
                val a = base + j * row + i
                val b = a + row
                // Seen from outside, going round in +theta and down in phi is clockwise; flip it.
                quad(a, a + 1, b + 1, b)
            }
        }
    }

    /**
     * A turned shape: [profile] is (radius, height) from bottom to top, revolved around the
     * vertical axis through (cx, cy, cz). Start and end at radius 0 to close it.
     */
    fun lathe(cx: Float, cy: Float, cz: Float, profile: List<Vector2>, slices: Int = 20): MeshData {
        val base = vertexCount
        val count = profile.size
        for (k in 0 until count) {
            val prev = profile[maxOf(0, k - 1)]
            val next = profile[minOf(count - 1, k + 1)]
            var tx = next.x - prev.x
            var ty = next.y - prev.y
            val len = sqrt(tx * tx + ty * ty).coerceAtLeast(1e-6f)
            tx /= len; ty /= len
            // Outward normal of a profile running upward: (ty, -tx).
            var nr = ty
            var ny = -tx
            if (profile[k].x < 1e-5f) { nr = 0f; ny = if (k == 0) -1f else 1f }
            for (i in 0..slices) {
                val theta = 2 * PI * i / slices
                val c = cos(theta).toFloat()
                val s = sin(theta).toFloat()
                val r = profile[k].x
                vertex(cx + r * c, cy + profile[k].y, cz + r * s, nr * c, ny, nr * s)
            }
        }
        val row = slices + 1
        for (k in 0 until count - 1) {
            for (i in 0 until slices) {
                val a = base + k * row + i
                val b = a + row
                quad(a, b, b + 1, a + 1)
            }
        }
        return this
    }

    /**
     * A rolled snake of dough along [points] with rounded ends. [radius] may taper from start
     * to end with [endRadius].
     */
    fun tube(points: List<Vector3>, radius: Float, endRadius: Float = radius, sides: Int = 10, caps: Boolean = true): MeshData {
        if (points.size < 2) return this
        val base = vertexCount
        val count = points.size
        // Rotation-minimising frames, carried along the curve.
        val tangent = Vector3()
        val normal = Vector3()
        val binormal = Vector3()
        tangentAt(points, 0, tangent)
        normal.set(if (abs(tangent.y) < 0.9f) Vector3.Y else Vector3.X).crs(tangent).nor()
        binormal.set(tangent).crs(normal).nor()
        val prevT = Vector3(tangent)
        val dir = Vector3()
        for (k in 0 until count) {
            tangentAt(points, k, tangent)
            if (k > 0) {
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
            val t = k.toFloat() / (count - 1)
            val r = radius + (endRadius - radius) * t
            for (i in 0..sides) {
                val a = 2 * PI * i / sides
                dir.set(normal).scl(cos(a).toFloat()).mulAdd(binormal, sin(a).toFloat())
                val p = points[k]
                vertex(p.x + dir.x * r, p.y + dir.y * r, p.z + dir.z * r, dir.x, dir.y, dir.z)
            }
        }
        val row = sides + 1
        for (k in 0 until count - 1) {
            for (i in 0 until sides) {
                val a = base + k * row + i
                val b = a + row
                // Around the tube +angle runs to the right of the tangent: counter-clockwise is a, a+1, b+1, b.
                quad(a, a + 1, b + 1, b)
            }
        }
        if (caps) {
            ball(points.first().x, points.first().y, points.first().z, radius, slices = sides, stacks = sides / 2 + 2)
            ball(points.last().x, points.last().y, points.last().z, endRadius, slices = sides, stacks = sides / 2 + 2)
        }
        return this
    }

    private fun tangentAt(points: List<Vector3>, k: Int, out: Vector3) {
        val a = points[maxOf(0, k - 1)]
        val b = points[minOf(points.size - 1, k + 1)]
        out.set(b).sub(a).nor()
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
        fun capsuleProfile(r: Float, h: Float, steps: Int = 6): List<Vector2> {
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
        fun roundedCylinderProfile(r: Float, h: Float, bevel: Float, steps: Int = 4): List<Vector2> {
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
    }
}
