package visualsystem

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import data.Stop
import data.StopTime
import data.Trip
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import lib.Pixelate
import lib.rbf.rbfGaussian
import lib.rbf.umapRbf
import org.openrndr.WindowConfiguration
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BlendMode
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadImage
import org.openrndr.draw.renderTarget
import org.openrndr.drawImage
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.color.presets.CORNFLOWER_BLUE
import org.openrndr.extra.color.presets.GOLD
import org.openrndr.extra.color.presets.ORANGE
import org.openrndr.extra.color.presets.ORANGE_RED
import org.openrndr.extra.color.presets.SKY_BLUE
import org.openrndr.extra.fx.Post
import org.openrndr.extra.fx.blur.FrameBlur
import org.openrndr.extra.fx.color.ColorCorrection
import org.openrndr.extra.fx.color.Duotone
import org.openrndr.extra.fx.color.Invert
import org.openrndr.extra.fx.edges.CannyEdgeDetector
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUI.ParameterValue
import org.openrndr.extra.gui.GUIAppearance
import org.openrndr.extra.gui.WindowedGUI
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.extra.noise.simplex
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.IntParameter
import org.openrndr.extra.shapes.rectify.rectified
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import org.openrndr.window
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.set
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1080
        height = 1080
    }

    program {

        extend(Screenshots())

        val background = drawImage(width, height) {
            val img = loadImage("data/images/latent-space.png")
            ColorCorrection().apply {
                contrast = 1.2
                brightness = 0.2
            }.apply(img, img)
            Duotone().apply {
                backgroundColor = ColorRGBa.TRANSPARENT
                foregroundColor = ColorRGBa.WHITE.opacify(0.22)
            }.apply(img, img)
            drawer.imageFit(img, drawer.bounds.offsetEdges(-20.0))
        }

        val mapImage = drawImage(width, height) {
            val img = loadImage("data/images/map.png")
            Invert().apply(img, img)
            ColorCorrection().apply {
                contrast = 1.3
                saturation = -1.0
                brightness = -0.5
            }.apply(img, img)
            drawer.translate(-120.0, 40.0)
            Duotone().apply {
                backgroundColor = ColorRGBa.TRANSPARENT
                foregroundColor = ColorRGBa.ORANGE_RED.mix(ColorRGBa.GOLD, 0.5).opacify(0.05)
            }.apply(img, img)
            drawer.imageFit(img, drawer.bounds.scaledBy(1.4))
        }

        val busContours = run {
            val tripsById = Json.decodeFromString<Map<String, Trip>>(File("data/hilversum-static-data/trips.json").readText())
            val stops = Json.decodeFromString<List<Stop>>(File("data/hilversum-static-data/stops.json").readText())
                .filter { !it.stopId.startsWith("stoparea:") && !it.stopName.startsWith("Amsterdam") }
                .associateBy { it.stopId }
            val stopTimesByStopId = Json.decodeFromString<Map<String, List<StopTime>>>(File("data/hilversum-static-data/stop-times.json").readText())

            data class StopInfo(val stopId: String, val departureTime: LocalTime, val position: Vector2, val stopName: String)

            val stopsByRouteId = mutableMapOf<String, MutableList<StopInfo>>()
            val stopsByTripId = mutableMapOf<String, MutableList<StopInfo>>()

            val stopBounds = stops.values.map { Vector2(it.coords.first, it.coords.second) }.bounds

            for ((stopId, stopTimes) in stopTimesByStopId) {

                for (stopTime in stopTimes) {
                    val tripId = stopTime.tripId
                    val list = stopsByTripId.getOrPut(tripId) { mutableListOf() }

                    val stop = stops[stopId]

                    if (stop == null) continue

                    val stopPos = Vector2(stop.coords.first, stop.coords.second).map(stopBounds, drawer.bounds.offsetEdges(-20.0))

                    if (list.none { it.stopId == stopId }) {
                        val stopInfo = StopInfo(stopId, stopTime.departureTime!!, stopPos, stop.stopName)
                        list.add(stopInfo)
                        list.sortBy { it.departureTime }
                    }
                }
            }

            for ((tripId, stopInfos) in stopsByTripId) {
                val trip = tripsById[tripId]

                if (trip == null) continue
                val routeId = trip.routeId

                if (stopsByRouteId[routeId] == null) {
                    stopsByRouteId[routeId] = stopInfos
                }
            }

            stopsByRouteId.values.map {
                ShapeContour.fromPoints(it.map { it.position }, true)
            }
        }


        var currentPosition = drawer.bounds.center
        var currentRoute = 0

        val parameterValues = mutableSetOf<Pair<String, ParameterValue>>()

        val rbf = run {
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

            data.umapRbf(0.0, drawer.bounds, 15, 100, 0, rbfGaussian(0.0001))
        }

        val parameterValueList = parameterValues.toList()

        window(WindowConfiguration(width = 1080, height = 1080, position = this@program.window.position.toInt() + IntVector2(1080, 0))) {

            val rt = renderTarget(400, 400) {
                colorBuffer()
                depthBuffer()
            }

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

            val gui = GUI(appearance = GUIAppearance(barWidth = 0))
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

            val fb = FrameBlur()
            val px = Pixelate()
            px.resolution = 0.03

            extend(gui) {
                visible = false
                compartmentsCollapsedByDefault = true
                showToolbar = false
            }
            extend {

                val values = rbf.interpolate(currentPosition)

                gui.fromObject(mapOf("No name" to values.toParams()))

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

                drawer.image(rt.colorBuffer(0), drawer.bounds)
            }
        }

        keyboard.character.listen {
            currentRoute = (currentRoute + 1).mod(busContours.size)
        }

        extend {

            drawer.image(background)
            drawer.drawStyle.blendMode = BlendMode.ADD
           // drawer.image(mapImage)
            drawer.drawStyle.blendMode = BlendMode.OVER

            drawer.strokeWeight = 3.0
            for ((i, contour) in busContours.withIndex()) {
                if (i == currentRoute) {
                    currentPosition = contour.position((seconds * 0.01).mod(1.0))

                    drawer.stroke = ColorRGBa.CORNFLOWER_BLUE
                    drawer.fill = null
                    drawer.contour(contour)

                    drawer.fill = ColorRGBa.ORANGE.mix(ColorRGBa.ORANGE_RED, 0.5)
                    drawer.stroke = null

                    drawer.circle(currentPosition, 10.0)
                } else {
                    drawer.stroke = ColorRGBa.CORNFLOWER_BLUE.opacify(0.1)
                    drawer.fill = null
                    drawer.contour(contour)
                }

            }

            drawer.strokeWeight = 1.0
            drawer.stroke = ColorRGBa.CORNFLOWER_BLUE
            drawer.fill = ColorRGBa.BLACK
            drawer.rectangle(20.0, 20.0, 140.0, 60.0)

            drawer.stroke = null
            drawer.fill = ColorRGBa.CORNFLOWER_BLUE
            drawer.text("Route nr: $currentRoute", 47.0, 55.0)


        }
    }

}