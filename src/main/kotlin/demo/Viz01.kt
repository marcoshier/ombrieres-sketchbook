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
import org.openrndr.math.Vector2
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import java.io.File
import kotlin.collections.mutableMapOf

fun main() {
    application {
        configure {
            width = 1080
            height = 1080
        }


        oliveProgram {

           // extend(ScreenRecorder())

            val tripsById = Json.decodeFromString<Map<String, Trip>>(File("data/hilversum-static-data/trips.json").readText())
            val stops = Json.decodeFromString<List<Stop>>(
                File("data/hilversum-static-data/stops.json").readText()).filter { !it.stopId.startsWith("stoparea:") && !it.stopName.startsWith("Amsterdam")
                }.associateBy { it.stopId }
            val stopTimesByStopId = Json.decodeFromString<Map<String, List<StopTime>>>(File("data/hilversum-static-data/stop-times.json").readText())

            data class TripInfo(val timeRange: Pair<LocalTime, LocalTime>, val headsign: String, val direction: Int)
            data class StopInfo(val departureTime: LocalTime, val position: Vector2, val stopName: String)

            val tripsToStopInfos = mutableMapOf<Trip, MutableList<StopInfo>>()
            val tripInfoById = mutableMapOf<String, TripInfo>()

            val stopCoords = stops.values.map { Vector2(it.coords.first, it.coords.second) }
            val bounds = stopCoords.bounds

            for ((stopId, stopTimes) in stopTimesByStopId) {
                val stop = stops[stopId]

                if (stop == null || stopId.startsWith("stoparea:") || stop.stopName.startsWith("Amsterdam") )  // stops with stoparea id have some sort of relative coordinates?
                    continue

                val stopPos = Vector2(stop.coords.first, stop.coords.second).map(bounds, drawer.bounds.offsetEdges(-50.0))


                for (stopTime in stopTimes) {
                    val trip = tripsById[stopTime.tripId]
                    require(trip != null)

                    val list = tripsToStopInfos.getOrPut(trip) { mutableListOf() }
                    list.add(StopInfo(stopTime.departureTime!!, stopPos, stop.stopName))
                    list.sortBy { it.departureTime }
                }
            }


            extend(Camera2D())

            extend {

                drawer.fontMap = loadFont("data/fonts/default.otf", 4.0, contentScale = 8.0)

                val texts = mutableMapOf<String, Vector2>()

                tripsToStopInfos.entries.forEachIndexed { i, (trip, stopInfos) ->
                    val sorted = stopInfos.sortedBy { it.departureTime }
                    val tripStart = sorted.first()
                    val tripEnd = sorted.last()

                    if (tripStart.departureTime.hour in 7..9) {
                        val color = ColorRGBa.RED.shiftHue<ColorOKHSLa>(i * 30.0)
                        val positions = stopInfos.map { it.position }

                        drawer.stroke = color
                        drawer.strokeWeight = 0.01
                        drawer.fill = null
                        val contour = ShapeContour.fromPoints(positions, true)
                        drawer.contour(contour)

                        drawer.stroke = null
                        drawer.fill = color
                        drawer.circles(positions, 1.0)

                        for (info in stopInfos) {
                            if (texts[info.stopName] == null) {
                                texts[info.stopName] = info.position
                            }
                        }

                        val mul = if (trip.directionId == 0) 1 else -1
                        drawer.circle(contour.position((seconds * 0.1).mod(1.0) * mul), 3.0)

                    }
                }

                for ((text, position) in texts) {
                    drawer.fill = ColorRGBa.WHITE
                    drawer.stroke = null
                    drawer.text(text, position)
                }

            }
        }
    }
}