package data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class Agency(val id: String, val name: String)

@Serializable
data class Route(val routeId: String, val agencyId: String, val routeShortName: String, val routeLongName: String, val routeDesc: String, val routeType: String, val routeColor: String, val routeTextColor: String)

@Serializable
data class Trip(val routeId: String, val serviceId: String, val tripId: String, val realtimeTripId: String, val tripHeadsign: String, val tripShortName: String, val tripLongName: String, val directionId: Int, val blockId: String, val shapeId: String)

@Serializable
data class CalendarDate(val serviceId: String, val date: LocalDate)

@Serializable
data class Stop(val stopId: String, val stopCode: String, val stopName: String, val stopLat: String, val stopLon: String, val locationType: String, val parentStation: String, val platformCode: String, val zoneId: String) {

    val coords = Pair(stopLat.toDouble(), stopLon.toDouble())

}

@Serializable
data class StopTime(val tripId: String, val stopSequence: String, val stopId: String, val stopHeadsign: String, val arrivalTime: LocalTime? = null, val departureTime: LocalTime? = null, val pickupType: Int, val dropOffType: Int)
