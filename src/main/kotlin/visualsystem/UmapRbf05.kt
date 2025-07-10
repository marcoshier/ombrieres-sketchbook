package visualsystem

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import lib.Pixelate
import lib.rbf.rbfGaussian
import lib.rbf.umapRbf
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BlendMode
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.color.spaces.OKHSV
import org.openrndr.extra.color.tools.shiftHue
import org.openrndr.extra.fx.blur.FrameBlur
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUI.ParameterValue
import org.openrndr.extra.noise.scatter
import org.openrndr.extra.noise.simplex
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.IntParameter
import org.openrndr.extra.shapes.rectify.rectified
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.Rectangle
import org.openrndr.shape.ShapeContour
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.text.get
import kotlin.times

fun main() {
    application {
        configure {
            width = 1080
            height = 1080
        }

        program {


            val parameterValues = mutableSetOf<Pair<String, ParameterValue>>()

            val data = File("data/vs-parameters").listFiles()!!.filter { it.isFile }.map {
                val typeToken = object : TypeToken<Map<String, Map<String, ParameterValue>>>() {}
                val labeledValues: Map<String, Map<String, ParameterValue>> = Gson().fromJson(it.readText(), typeToken)
                labeledValues["No name"]!!.entries.toList().map { (key, value) ->
                    parameterValues.add(key to value)
                    if (value.intValue != null) {
                        value.intValue!!.toDouble()
                    } else {
                        value.doubleValue!!
                    }
                }.toDoubleArray()
            }.toTypedArray()

            val rbf = data.umapRbf(0.0, drawer.bounds, 15, 100, 0, rbfGaussian(0.0001))

            val thumbnailPoints = drawer.bounds.offsetEdges(-40.0).scatter(20.0)
            val parameterValueList = parameterValues.toList()

            fun DoubleArray.toParams(): Map<String, ParameterValue> {
                return this.mapIndexed { i, it ->
                    val (key, refpv) = parameterValueList[i]

                    if (refpv.intValue != null) {
                        key to ParameterValue(
                            intValue = it.toInt(),
                            minValue = refpv.minValue,
                            maxValue = refpv.maxValue,
                        )
                    } else {
                        key to ParameterValue(
                            doubleValue = it,
                            minValue = refpv.minValue,
                            maxValue = refpv.maxValue,
                        )
                    }
                }.toMap()
            }


            val gui = GUI().apply { visible = false }
            val params = object {
                @IntParameter("n", 1, 30)
                var n = 1

                @IntParameter("n2", 1, 30)
                var n2 = 1

                @DoubleParameter("n2 distance", 0.0, 0.5, precision = 3)
                var n2distance = 0.0001

                @DoubleParameter("speed", 0.0, 1.0, precision = 3)
                var speed = 0.0001

                @DoubleParameter("offset", 0.0, 1.0)
                var offset = 0.05

                @DoubleParameter("weight", 0.0, 100.0)
                var weight = 20.0

                @DoubleParameter("blend", 0.0, 1.0)
                var blend = 0.0

                @IntParameter("branches", 1, 100)
                var branches = 10

                @DoubleParameter("gravity pull", 0.0, 1.0)
                var gravityPull = 0.0

                @DoubleParameter("noise amt", 0.0, 1.0)
                var noiseAmt = 0.0

                @DoubleParameter("noise scale x", 0.0, 1.0, precision = 4)
                var noiseScaleX = 0.0001

                @DoubleParameter("noise scale y", 0.0, 1.0, precision = 4)
                var noiseScaleY = 0.0001

                @DoubleParameter("noise scale z", 0.0, 1.0, precision = 4)
                var noiseScaleZ = 0.0001


            }.also { gui.add(it) }

            extend(gui) {
                visible = false
            }

            extend(Screenshots())
            val px = Pixelate()
            px.resolution = 0.03

            val rts = thumbnailPoints.map {
                FrameBlur() to renderTarget(width, height) {
                    colorBuffer()
                    depthBuffer()
                }
            }

            extend {


                for ((i, point) in thumbnailPoints.withIndex()) {
                    val values = rbf.interpolate(point)
                    gui.fromObject(mapOf("No name" to values.toParams()))

                    gui.visible = false

                    val fb = rts[i].first

                    val rt = rts[i].second
                    fb.blend = params.blend * 0.1

                    drawer.isolatedWithTarget(rt) {
                        drawer.clear(ColorRGBa.BLACK)
                        drawer.stroke = ColorRGBa.WHITE
                        drawer.strokeWeight = params.weight
                        drawer.fill = null

                        for (i in 0 until params.n) {
                            val contour = Circle(540.0, 540.0, (400.0 / params.n) * (i + 1)).contour
                            val contourPositions = contour.equidistantPositions(params.branches * 20).mapIndexed { pi, it ->
                                val n = simplex(123, it.x * params.noiseScaleX, it.y * params.noiseScaleY, seconds * params.noiseScaleZ) * params.noiseAmt
                                if (params.gravityPull > 0.0) {
                                    val f = sin(2 * PI * pi * params.gravityPull) * 0.5 + 0.5
                                    it.mix(Vector2(540.0, 540.0), f * n)
                                } else it.mix(Vector2(540.0, 540.0), n)

                            }
                            val rectified = ShapeContour.fromPoints(contourPositions, true).rectified()

                            val pos = 0.1 * seconds * params.speed + Double.uniform(0.0, params.offset, Random(i))

                            for (j in 0 until params.n2) {
                                drawer.circle(rectified.position(pos - (params.n2distance * j)), params.weight)
                            }
                        }

                    }

                    rt.colorBuffer(0).generateMipmaps()
                    rt.colorBuffer(0).filter(MinifyingFilter.LINEAR_MIPMAP_LINEAR, MagnifyingFilter.NEAREST)
                    px.apply(rt.colorBuffer(0), rt.colorBuffer(0))
                    fb.apply(rt.colorBuffer(0), rt.colorBuffer(0))

                    drawer.drawStyle.blendMode = BlendMode.ADD
                    drawer.image(rt.colorBuffer(0), Rectangle.fromCenter(point, 60.0))

                }

            }
        }
    }
}