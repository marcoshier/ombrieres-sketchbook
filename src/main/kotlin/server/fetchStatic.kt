package server/*
package server

import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import java.io.File
import java.io.FileOutputStream

fun fetchStaticData() {
    val baseUrl = "https://gtfs.ovapi.nl/nl"

    val client = OkHttpClient()

    val staticFileName = "gtfs-nl.zip"

    val request = Request.Builder()
        .url("$baseUrl/$staticFileName")
        .get()
        .build()

    val response = client.newCall(request).execute()

    println(response.code())
    if (response.code() != 200)
        return


    var progress = 0

    val inputStream = response.body().byteStream()

    val byteArray = byteArrayOf()

    val contentLength = response.body().contentLength()
    val target = File("data/$staticFileName")

    val outputStream = FileOutputStream(target)

    while (true) {
        val read = inputStream.read(byteArray)
        if (read == -1) {
            return
        }

        outputStream.write(byteArray, 0, read)
        progress += read
        // println(progress)
    }

    */
/*outputStream.flush();
    outputStream.close();
    inputStream.close()*//*



}*/
