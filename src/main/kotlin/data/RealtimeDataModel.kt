package data

import com.google.transit.realtime.GtfsRealtime
import java.io.File
typealias FeedMessage = GtfsRealtime.FeedMessage


class RealtimeDataModel {

    /**
     * A service alert is generated for each disruption in the network.
     * If a disruption leads to cancellations and/or delays,
     * these are communicated as Trip Updates and Vehicle Positions.
     */

    val alerts = parseGtfs("alerts.pb")


    /**
     * Updates on trips. For each active trip one, and no more than one, update is available.
     * If there is no update message for a specific trip,
     * the assumption is that the trip is not running.
     */

    val tripUpdates = parseGtfs("tripUpdates.pb")


    /** If available (depends on vehicle), the current location of a vehicle on a trip.
     * It provides information on the next stop and
     * current delay of the vehicle on this specific trip.
     * */

    val vehiclePositions = parseGtfs("vehiclePositions.pb")



    private fun parseGtfs(fileName: String): FeedMessage {
        return FeedMessage.parseFrom(File("data/gtfs-realtime/$fileName").inputStream())
    }
}

fun main() {
    val dm = RealtimeDataModel()
    dm.tripUpdates.entityList.forEach {
        println(it)
    }

   // println(dm.tripUpdates.entityList)
}