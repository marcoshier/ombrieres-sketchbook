package demo

import kotlinx.serialization.json.Json
import data.StaticDataModel
import data.StopTime
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import java.io.File

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

    val sdm = StaticDataModel()

    val stops = sdm.stops.filter { it.stopName.contains("Hilversum") }

    File("data/hilversum-static-data/stops.json").writeText(
        Json.encodeToString(stops)
    )

    val stopTimesByStopId = mutableMapOf<String, List<StopTime>>()

    for ((i, stop) in stops.withIndex()) {
        println("$i / ${stops.size} - ${stop.stopName}")
        stopTimesByStopId[stop.stopId] = sdm.stopTimesFromStopId(stop.stopId)
    }

    File("data/hilversum-static-data/stop-times.json").writeText(
        Json.encodeToString(stopTimesByStopId)
    )

}