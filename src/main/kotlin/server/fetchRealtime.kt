package server

import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.minutes

suspend fun fetchRealtimeData() {
    val baseUrl = "https://gtfs.ovapi.nl/nl"

    val files = listOf(
        "tripUpdates.pb",
        "vehiclePositions.pb",
        "alerts.pb",
    )

    val client = OkHttpClient()

    while (true) {
        for (file in files) {
            val request = Request.Builder()
                .url("$baseUrl/$file")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBytes = response.body().bytes()
                if (responseBytes != null) {
                    val saveFile = File("data/gtfs-realtime", file)
                    saveFile.writeBytes(responseBytes)
                }
            } else {
                println("HTTP Error: ${response.code()}")
            }
        }

        delay(1.minutes)
    }

}