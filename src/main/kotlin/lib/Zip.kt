package lib

import java.io.File
import java.util.zip.ZipFile

fun unzipFile(file: File) {
    ZipFile(file.path).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                File("data/gtfs-static/${entry.name}")
                    .outputStream().use { output ->
                        input.copyTo(output)
                }
            }
        }
    }
}

fun main() {
    unzipFile(File("data/gtfs-nl.zip"))
}