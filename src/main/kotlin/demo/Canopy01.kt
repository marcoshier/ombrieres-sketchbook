package demo

import offset.offset
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.camera.Camera2D
import org.openrndr.extra.noise.scatter
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.triangulation.delaunayTriangulation
import org.openrndr.extra.triangulation.smoothScatter
import org.openrndr.math.transforms.buildTransform
import org.openrndr.shape.Circle
import org.openrndr.shape.Triangle
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1080
        height = 1080
    }

    oliveProgram {

        extend(Screenshots())

        val area = Circle(drawer.bounds.center, 680.0)
        val points = area.smoothScatter(100.0)

        fun Triangle.base() = contour.segments.maxBy { it.length }

        fun Triangle.height(): Double {
            val base = contour.segments.maxBy { it.length }
            return (this.area * 2) / base.length
        }

        val del = points.delaunayTriangulation()
        val triangles = del.triangles().filter { it.height() / it.base().length > 0.2 }
        val contours = triangles.map { it.contour }

        val trianglePoints = contours.map { it.smoothScatter(9.5, 15.0, smoothing = 1.0).filter { Double.uniform(0.0, 1.0) > 0.5 } }
        val del2 = trianglePoints.map { it.delaunayTriangulation() }
        val triangles2 = del2.map { it.triangles().filter { it.height() / it.base().length > 0.2 } }
        val contours2 = triangles2.map { it.map {
            it.contour.transform(buildTransform {
                translate(it.centroid)
                scale(0.4)
                translate(-it.centroid)
        }) }.filter { it.bounds.area > 1.0 } }

        println(contours2.sumOf { it.size })

        extend(Camera2D())

        extend {

            drawer.stroke = ColorRGBa.WHITE
            drawer.fill = null
            drawer.contours(contours)

            drawer.stroke = null
            for ((i, cs) in contours2.withIndex()) {
                val r = Double.uniform(0.0, 1.0, Random(i))
                for ((j, c) in cs.withIndex()) {
                    val value = (Double.uniform(0.0, 1.0, Random(j)) + r) / 2.0
                    drawer.fill = ColorRGBa.WHITE.shade(value)
                    drawer.contour(c)
                }
            }

        }
    }
}