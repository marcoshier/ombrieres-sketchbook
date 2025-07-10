package demo

import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import data.Stop
import data.StopTime
import data.Trip
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadFont
import org.openrndr.extra.camera.Camera2D
import org.openrndr.extra.color.spaces.ColorOKHSLa
import org.openrndr.extra.color.tools.shiftHue
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.svg.loadSVG
import org.openrndr.extra.triangulation.voronoiDiagram
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import java.io.File
import kotlin.collections.mutableMapOf

fun main() {
    application {
        configure {
            width = 1280
            height = 1080
        }

        program {

            val stationContour = loadSVG("data/svg/ombrieres-top.svg").findShape("main")!!.shape.contours.first()

            val tripsById = Json.decodeFromString<Map<String, Trip>>(File("data/hilversum-static-data/trips.json").readText())

            val stops = Json.decodeFromString<List<Stop>>(File("data/hilversum-static-data/stops.json").readText())
                .filter { !it.stopId.startsWith("stoparea:") && !it.stopName.startsWith("Amsterdam") }
                .associateBy { it.stopId }

            val stopTimesByStopId = Json.decodeFromString<Map<String, List<StopTime>>>(File("data/hilversum-static-data/stop-times.json").readText())


            data class StopInfo(val stopId: String, val departureTime: LocalTime, val position: Vector2, val stopName: String)

            val stopsByRouteId = mutableMapOf<String, MutableList<StopInfo>>()
            val stopsByTripId = mutableMapOf<String, MutableList<StopInfo>>()

            val stopBounds = stops.values.map { Vector2(it.coords.first, it.coords.second) }.bounds

            for ((stopId, stopTimes) in stopTimesByStopId) {

                for (stopTime in stopTimes) {
                    val tripId = stopTime.tripId
                    val list = stopsByTripId.getOrPut(tripId) { mutableListOf() }

                    val stop = stops[stopId]

                    if (stop == null) continue

                    val stopPos = Vector2(stop.coords.first, stop.coords.second).map(stopBounds, drawer.bounds.offsetEdges(-300.0))

                    if (list.none { it.stopId == stopId }) {
                        val stopInfo = StopInfo(stopId, stopTime.departureTime!!, stopPos, stop.stopName)
                        list.add(stopInfo)
                        list.sortBy { it.departureTime }
                    }
                }
            }

            for ((tripId, stopInfos) in stopsByTripId) {
                val trip = tripsById[tripId]

                if (trip == null) continue
                val routeId = trip.routeId

                if (stopsByRouteId[routeId] == null) {
                    stopsByRouteId[routeId] = stopInfos
                }
            }


            val contours = stopsByRouteId.values.map {
                ShapeContour.fromPoints(it.map { it.position }, true)
            }





            extend(ScreenRecorder()) {
                contentScale = 1.0
                maximumDuration = 60.0
                frameRate = 60
            }


            val points = contours.flatMap { it.equidistantPositions(20) }.toMutableList()

            extend(Camera2D())

            extend {
                drawer.translate(100.0, 100.0)
                drawer.fill = null
                drawer.strokeWeight = 2.0

                val vd = (points + stationContour.equidistantPositions(120))
                    .voronoiDiagram()


                drawer.stroke = ColorRGBa.WHITE
                //drawer.contours(vd.cellPolygons())


                drawer.fill = ColorRGBa.WHITE
                drawer.circles(points, 4.0)
                drawer.fill = null

                for (i in points.indices) {
                    val centroid = vd.cellCentroid(i)

                    if (centroid.x.isNaN() || centroid.y.isNaN())
                        continue

                    points[i] = points[i].mix(vd.cellCentroid(i), 0.5)

                }

                val newContours = points.windowed(20, 20).map {
                    ShapeContour.fromPoints(it, closed = true)
                }

                for ((i, contour) in newContours.withIndex()) {
                    val color = ColorRGBa.RED.shiftHue<ColorOKHSLa>(i * 30.0)
                    drawer.stroke = color
                    drawer.contour(contour)
                }

            }
        }
    }
}