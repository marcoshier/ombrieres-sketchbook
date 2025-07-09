package data

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Data model for the static GTFS data
 * Data descriptions from https://towardsdatascience.com/where-is-the-bus-gtfs-will-tell-us-f8adc18a2f8e/
 */

class StaticDataModel {

    private val csv = csvReader()


    /**
     * The list of agencies for which transit data is provided
     */

    val agencies = csv.readAllWithHeader(File("data/gtfs-static/agency.txt")).map {
        Agency(it["agency_id"] ?: "no id", it["agency_name"] ?: "no name")
    }



    /**
     * All transit routes. A route is a a group of trips and is seen as a service by the customer.
     * An examples is a bus line (line 5 in Amsterdam to Westergasfabriek)
     * or train service (serie 3300 is the soptrain between
     * Hoorn Kersenboogerd and Den Haag Central)
     */

    val routes = csv.readAllWithHeader(File("data/gtfs-static/routes.txt")).map {
        Route(
            it["route_id"] ?: "no route id",
            it["agency_id"] ?: "no agency id",
            it["route_short_name"] ?: "no route short name",
            it["route_long_name"] ?: "no route long name",
            it["route_desc"] ?: "no route desc",
            it["route_type"] ?: "no route type",
            it["route_color"] ?: "#00000000",
            it["route_text_color"] ?: "#00000000",
        )
    }

    val routesPerAgency = routes.groupBy { it.agencyId }.mapKeys { (agencyName, _) -> agencies.find { it.id == agencyName } }



    /**
     * All transit trips. A trip is one bus/train on a route, connecting two or more stop locations.
     * model.Stop locations can differ per trip (e.g. skipping specific stations).
     * A trip belongs to one route.
     */

    val trips = csv.readAllWithHeader(File("data/gtfs-static/trips.txt")).map {
        Trip(
            it["route_id"] ?: "no stop id",
            it["service_id"] ?: "no stop code",
            it["trip_id"] ?: "no stop name",
            it["realtime_trip_id"] ?: "no stop latitude",
            it["trip_headsign"] ?: "no stop longitude",
            it["trip_short_name"] ?: "no location type",
            it["trip_long_name"] ?: "no parent station",
            it["direction_id"]?.toIntOrNull() ?: -1,
            it["block_id"] ?: "no zone id",
            it["shape_id"] ?: "no zone id",
        )
    }

    val tripsPerRoute = trips.groupBy { it.routeId }.mapKeys { (routeId, _) -> routes.find { it.routeId == routeId } }



    /**
     * A table linking dates to services.
     * For each data an entry is present with the trip IDs of all trips running that day.
     * The GTFS standard uses this file as a exception file for the service patterns (e.g. weekly pattern)
     * specified in the optional file calendar.txt.
     * This GTFS provider only uses the calendar_dates to map services to dates.
     * A service is one or more trips and is defined by the service ID in the trips specification.
     */

    val calendarDates = csv.readAllWithHeader(File("data/gtfs-static/calendar_dates.txt")).map {
        val date = it["date"]!!
        CalendarDate(
            serviceId = it["service_id"] ?: "no service id",
            date = LocalDate(
                date.substring(0, 4).toInt(),
                date.substring(4, 6).toInt(),
                date.substring(6, 8).toInt()
            )
        )
    }


    /**
     * All stop locations. This can be a bus stop or train station.
     * Stops are defined on the level of platform and combined to location in the shape of stopareas.
     * A train station has one stop for each platform and one stoparea (the station).
     *
     */

    val stops = csv.readAllWithHeader(File("data/gtfs-static/stops.txt")).map {
        Stop(
            it["stop_id"] ?: "no stop id",
            it["stop_code"] ?: "no stop code",
            it["stop_name"] ?: "no stop name",
            it["stop_lat"] ?: "no stop latitude",
            it["stop_lon"] ?: "no stop longitude",
            it["location_type"] ?: "no location type",
            it["parent_station"] ?: "no parent station",
            it["platform_code"] ?: "no platform code",
            it["zone_id"] ?: "no zone id",
        )
    }


    /**
     * For each stop on each trip the arrival and departure time.
     * The largest file of the dataset (1 GB of data), therefore
     * a different way to retrieve this data may be needed at some point.
     */

    
    private val tripIdsToStopTimes = mutableMapOf<String, List<StopTime>>()
    private val stopTimesFile = "data/gtfs-static/stop_times.txt"
    
    fun stopTimesFromTripId(tripId: String): List<StopTime> {
        return tripIdsToStopTimes.getOrPut(tripId) {
            sequence {
                BufferedReader(FileReader(stopTimesFile)).use { reader ->
                    reader.lineSequence()
                        .drop(1)
                        .forEachIndexed { i, it ->
                            yield(it.split(","))
                        }
                }
            }.filter { it[0] == tripId }.toStopTimes()
        }
    }


    private val stopIdsToStopTimes = mutableMapOf<String, List<StopTime>>()

    fun stopTimesFromStopId(stopId: String): List<StopTime> {
        return stopIdsToStopTimes.getOrPut(stopId) {
            sequence {
                BufferedReader(FileReader(stopTimesFile)).use { reader ->
                    reader.lineSequence()
                        .drop(1)
                        .forEachIndexed { i, it ->
                            yield(it.split(","))
                        }
                }
            }.filter { it[2] == stopId }.toStopTimes()
        }
    }


    private val headsignToStopTimes = mutableMapOf<String, List<StopTime>>()

    fun stopTimesFromHeadsign(headsign: String): List<StopTime> {
        return headsignToStopTimes.getOrPut(headsign) {
            sequence {
                BufferedReader(FileReader(stopTimesFile)).use { reader ->
                    reader.lineSequence()
                        .drop(1)
                        .forEachIndexed { i, it ->
                            yield(it.split(","))
                        }
                }
            }.filter { it[3] == headsign }.toStopTimes()
        }
    }



    private fun Sequence<List<String>>.toStopTimes(): List<StopTime> {
        return map { row ->
            StopTime(
                row.getOrNull(0) ?: "no trip id",
                row.getOrNull(1) ?: "no stop sequence",
                row.getOrNull(2) ?: "no stop id",
                row.getOrNull(3) ?: "no stop headsign",
                row.getOrNull(4)?.let { LocalTime.parse(it.sanitizeTime()) },
                row.getOrNull(5)?.let { LocalTime.parse(it.sanitizeTime()) },
                row.getOrNull(6)?.toIntOrNull() ?: -1,
                row.getOrNull(7)?.toIntOrNull() ?: -1,
            )
        }.toList()
    }


    private fun String.sanitizeTime(): String {
        return replace("^(\\d{2}):".toRegex()) { matchResult ->
            val hour = matchResult.groupValues[1].toInt()
            val normalizedHour = hour % 24
            String.format("%02d:", normalizedHour)
        }
    }

}

fun main() {
    val dm = StaticDataModel()
    val startTime = System.currentTimeMillis()
    val de = dm.stopTimesFromTripId("260620530")
    println(de.size)
    println(System.currentTimeMillis() - startTime)
}