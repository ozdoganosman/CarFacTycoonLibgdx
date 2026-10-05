package com.toyquaise.kaptan.render

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.tan

/** Points a camera at a scene so it just fits between two heights of the screen. */
object Framing {
    /**
     * Looks down at [pitch] degrees and moves [camera] to the distance at which every one of
     * [points] fits on a [width] × [height] screen between [bottom] and [top] pixels (from the
     * bottom), centred between them.
     */
    fun fit(camera: PerspectiveCamera, points: List<Vector3>, width: Int, height: Int, bottom: Float, top: Float, pitch: Float) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.near = 0.5f
        camera.far = 120f
        val p = pitch * MathUtils.degreesToRadians
        val dir = Vector3(0f, -MathUtils.sin(p), -MathUtils.cos(p))
        val target = Vector3()
        for (q in points) target.add(q)
        target.scl(1f / points.size).y = 0f
        val tmp = Vector3()
        fun place(d: Float) {
            camera.position.set(target).mulAdd(dir, -d)
            camera.direction.set(dir)
            camera.up.set(Vector3.Y)
            camera.normalizeUp()
            camera.update()
        }
        fun bounds(): FloatArray {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (q in points) {
                camera.project(tmp.set(q))
                minX = minOf(minX, tmp.x); maxX = maxOf(maxX, tmp.x); minY = minOf(minY, tmp.y); maxY = maxOf(maxY, tmp.y)
            }
            return floatArrayOf(minX, maxX, minY, maxY)
        }
        val margin = width * 0.02f
        var d = 20f
        repeat(4) {
            var lo = 2f
            var hi = 90f
            repeat(28) {
                val mid = (lo + hi) / 2
                place(mid)
                val b = bounds()
                if (b[0] >= margin && b[1] <= width - margin && b[2] >= bottom && b[3] <= top) hi = mid else lo = mid
            }
            d = hi
            place(d)
            // Centre the scene in the space it has.
            val b = bounds()
            val worldPerPixel = 2f * d * tan(camera.fieldOfView / 2 * MathUtils.degreesToRadians) / height
            val sideways = ((b[0] + b[1]) / 2 - width / 2f) * worldPerPixel
            val down = (bottom + top) / 2 - (b[2] + b[3]) / 2
            target.mulAdd(Vector3(camera.direction).crs(camera.up).nor(), sideways)
            target.mulAdd(Vector3(camera.up), -down * worldPerPixel)
        }
        place(d)
    }
}
