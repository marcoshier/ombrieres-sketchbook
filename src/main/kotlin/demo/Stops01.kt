package demo

import data.StaticDataModel
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.camera.Camera2D
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle

fun main() {

    // Netherlands WGS84 bounds. From https://gist.github.com/graydon/11198540
    val minLat = 50.803721015
    val maxLat = 53.5104033474
    val minLon = 3.31497114423
    val maxLon = 7.09205325687

    fun Pair<Double, Double>.toVector2Equirectangular(bounds: Rectangle): Vector2 {
        val (lat, lon) = this
        val x = (lon - minLon) / (maxLon - minLon) * bounds.width
        val y = (maxLat - lat) / (maxLat - minLat) * bounds.height
        return Vector2(x, y)
    }

    val ar = (maxLon - minLon) / (maxLat - minLat)

    application {
        configure {
            width = 1280
            height = (width / ar).toInt()
        }

        program {

            extend(Camera2D())

            val sdm = StaticDataModel()

            val positions = mutableListOf<Vector2>()

            for (stop in sdm.stops) {
                val dlat = stop.stopLat.toDoubleOrNull()
                val dlon = stop.stopLon.toDoubleOrNull()

                if (dlat == null || dlon == null)
                    continue

                val coords = Pair(dlat, dlon)
                val vcoords = coords.toVector2Equirectangular(drawer.bounds.offsetEdges(-100.0))

                println(vcoords)
                positions.add(vcoords)
            }

            extend {

                drawer.stroke = null
                drawer.fill = ColorRGBa.WHITE.opacify(0.5)
                drawer.points(positions)

            }
        }
    }
}