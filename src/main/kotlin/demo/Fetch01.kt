package demo

import kotlinx.coroutines.runBlocking
import server.fetchRealtimeData

fun main() {
    val rt = runBlocking {
        fetchRealtimeData()
    }
}