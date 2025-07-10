package visualsystem

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import lib.rbf.rbfGaussian
import lib.rbf.umapRbf
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.color.spaces.OKHSV
import org.openrndr.extra.color.tools.shiftHue
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUI.ParameterValue
import org.openrndr.extra.noise.scatter
import org.openrndr.math.Vector2
import java.io.File
import kotlin.text.get
import kotlin.times

fun main() {
    application {
        configure {
            width = 1080
            height = 1080
        }

        program {

            extend(Screenshots())

            val labels = mutableSetOf<String>()

            val data = File("data/vs-parameters").listFiles()!!.filter { it.isFile }.map {
                val typeToken = object : TypeToken<Map<String, Map<String, ParameterValue>>>() {}
                val labeledValues: Map<String, Map<String, ParameterValue>> = Gson().fromJson(it.readText(), typeToken)
                labeledValues["No name"]!!.entries.toList().map { (key, value) ->
                    labels.add(key)
                    if (value.intValue != null) {
                        value.intValue!!.toDouble()
                    } else {
                        value.doubleValue!!
                    }
                }.toDoubleArray()
            }.toTypedArray()

            val rbf = data.umapRbf(0.0, drawer.bounds, 15, 100, 0, rbfGaussian(0.001))

            var position = drawer.bounds.center

            mouse.dragged.listen {
                position = it.position
            }

            val thumbnailPoints = drawer.bounds.offsetEdges(-40.0).scatter(20.0)

            extend {


                drawer.rectangles {
                    for (y in 0 until height step 15) {
                        for (x in 0 until width step 15) {

                            val p = Vector2(x.toDouble(), y.toDouble())
                            val values = rbf.interpolate(p)

                            for (i in values.indices) {
                                val v = values[i]
                                fill = ColorRGBa.RED.shiftHue<OKHSV>(i * 60.0)
                                stroke = null
                                rectangle(p + Vector2(i * 2.0, 0.0), 1.0, -v)

                            }
                        }
                    }
                }


                val values = rbf.interpolate(position)
                for (i in values.indices) {
                    val v = values[i]
                    drawer.stroke = ColorRGBa.RED
                    drawer.lineSegment(
                        position + Vector2(i * 5.0, 0.0),
                        position + Vector2(i * 5.0, -v * 10.0)
                    )
                }

            }
        }
    }
}