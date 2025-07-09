package visualsystem

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import lib.DoubleArrayIir
import lib.Pixelate
import lib.rbf.rbfGaussian
import lib.rbf.umapRbf
import data.Stop
import data.StopTime
import data.Trip
import org.openrndr.WindowConfiguration
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.draw.persistent
import org.openrndr.draw.renderTarget
import org.openrndr.drawImage
import org.openrndr.extra.color.presets.GOLD
import org.openrndr.extra.color.presets.ORANGE_RED
import org.openrndr.extra.fx.blur.ApproximateGaussianBlur
import org.openrndr.extra.fx.blur.FrameBlur
import org.openrndr.extra.fx.color.ColorCorrection
import org.openrndr.extra.fx.color.Invert
import org.openrndr.extra.gui.GUI
import org.openrndr.extra.gui.GUI.ParameterValue
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.extra.kdtree.kdTree
import org.openrndr.extra.noise.simplex
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.Once
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.IntParameter
import org.openrndr.extra.shadestyles.fills.FillUnits
import org.openrndr.extra.shadestyles.fills.gradients.gradient
import org.openrndr.extra.shapes.rectify.rectified
import org.openrndr.math.Vector2
import org.openrndr.math.smoothstep
import org.openrndr.shape.Circle
import org.openrndr.shape.LineSegment
import org.openrndr.shape.ShapeContour
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import org.openrndr.window
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

fun main() {
    application {
        configure {
            width = 1080
            height = 1080
        }

        oliveProgram {

            val tripsById = Json.decodeFromString<Map<String, Trip>>(File("data/hilversum-static-data/trips.json").readText())
            val stops = Json.decodeFromString<List<Stop>>(
                File("data/hilversum-static-data/stops.json").readText()).filter { !it.stopId.startsWith("stoparea:") && !it.stopName.startsWith("Amsterdam")
            }.associateBy { it.stopId }
            val stopTimesByStopId = Json.decodeFromString<Map<String, List<StopTime>>>(File("data/hilversum-static-data/stop-times.json").readText())

            data class TripInfo(val timeRange: Pair<LocalTime, LocalTime>, val headsign: String, val direction: Int)
            data class StopInfo(val departureTime: LocalTime, val position: Vector2, val stopName: String)

            val tripsToStopInfos = mutableMapOf<Trip, MutableList<StopInfo>>()
            val tripInfoById = mutableMapOf<String, TripInfo>()

            val stopCoords = stops.values.map { Vector2(it.coords.first, it.coords.second) }
            val bounds = stopCoords.bounds

            for ((stopId, stopTimes) in stopTimesByStopId) {
                val stop = stops[stopId]

                if (stop == null || stopId.startsWith("stoparea:") || stop.stopName.startsWith("Amsterdam") )  // stops with stoparea id have some sort of relative coordinates?
                    continue

                val stopPos = Vector2(stop.coords.first, stop.coords.second).map(bounds, drawer.bounds.offsetEdges(-50.0))


                for (stopTime in stopTimes) {
                    val trip = tripsById[stopTime.tripId]
                    require(trip != null)

                    val list = tripsToStopInfos.getOrPut(trip) { mutableListOf() }
                    list.add(StopInfo(stopTime.departureTime!!, stopPos, stop.stopName))
                    list.sortBy { it.departureTime }
                }
            }


            var tripIdx = 3000

            val parameterValues = mutableSetOf<Pair<String, ParameterValue>>()

            fun loadParameters() = File("data/vs-parameters").listFiles()!!.filter { it.isFile }.map {
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

            val data = loadParameters()
            val parameterValueList = parameterValues.toList()

            val rbf = data.umapRbf(
                0.0,
                drawer.bounds,
                15,
                100,
                0,
                rbfGaussian(0.00005)
            )

            var position = drawer.bounds.center

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

            val fb = FrameBlur()
            val px = Pixelate()
            px.resolution = 0.03


            val mapImage = drawImage(width, height) {
                val img = loadImage("data/images/map.png")
                Invert().apply(img, img)
                ColorCorrection().apply {
                    contrast = 1.3
                    saturation = -1.0
                    gamma = 1.7
                    brightness = -1.2
                }.apply(img, img)
                ApproximateGaussianBlur().apply(img, img)
                drawer.translate(-120.0, 40.0)
                drawer.shadeStyle = gradient<ColorRGBa> {
                    this.stops[0.0] = ColorRGBa.WHITE
                    this.stops[0.3] = ColorRGBa.WHITE
                    this.stops[0.75] = ColorRGBa.TRANSPARENT
                    this.stops[1.0] = ColorRGBa.TRANSPARENT
                    this.fillUnits = FillUnits.WORLD
                    radial {
                        this.center = drawer.bounds.center
                        this.radius = width / 2.0
                    }
                }
                drawer.imageFit(img, drawer.bounds)
            }

            val mw = window

            val iir = DoubleArrayIir(0.03, 0.03, rbf.values[0].size)



            val window by Once {
                persistent {
                    window(WindowConfiguration(
                        position = Vector2((mw.position.x + mw.size.x), mw.position.y).toInt(),
                        width = 1080,
                        height = 1080
                    )) {

                        val rt = renderTarget(400, 400) {
                            colorBuffer()
                            depthBuffer()
                        }


                        extend {

                            val values = rbf.interpolate(position)
                            iir.update(values)

                            gui.fromObject(mapOf("No name" to iir.dyn.toParams()))

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
                }
            }

            window

            val kd = rbf.points.kdTree()

            extend {

                drawer.image(mapImage)
                val stationPos = drawer.bounds.position(0.5, 0.5)
                drawer.fill = ColorRGBa.ORANGE_RED
                drawer.stroke = null
                drawer.circle(stationPos, 10.0)


                for (pos in rbf.points) {
                    drawer.fill = ColorRGBa.GOLD
                    drawer.circle(pos, 4.0)
                }


                val mapOffset = Vector2(30.0, -165.0)

               // drawer.translate(mapOffset)
                drawer.fontMap = loadFont("data/fonts/default.otf", 6.0, contentScale = 8.0)

                val (trip, stopInfos) = tripsToStopInfos.entries.toList()[tripIdx]

                val color = ColorRGBa.WHITE
                val positions = stopInfos.map { it.position + mapOffset }

                drawer.stroke = color.opacify(0.75)
                drawer.strokeWeight = 1.0
                drawer.fill = null
                val contour = ShapeContour.fromPoints(positions, true)
                drawer.contour(contour)


                drawer.stroke = null
                drawer.fill = ColorRGBa.WHITE
                val mul = if (trip.directionId == 0) 1 else -1
                val busPos =  contour.rectified().position((0.5 + seconds * 0.01).mod(1.0) * mul)
                drawer.circle(busPos, 4.0)

                val stationRadius = 100.0

                drawer.stroke = ColorRGBa.ORANGE_RED
                drawer.fill = null
                val stationCircle = Circle(stationPos, stationRadius)
                drawer.circle(stationCircle)

                if (busPos in stationCircle) {

                    val t = 1.0 - ((seconds * 0.5).mod(1.0) * 2.0).coerceAtMost(1.0)

                    drawer.stroke = null
                    drawer.fill = ColorRGBa.ORANGE_RED
                    val ls = LineSegment(stationPos, busPos)
                    drawer.circle(ls.position(t.smoothstep(0.1, 0.8)), 6.0)

                    drawer.stroke = ColorRGBa.ORANGE_RED
                    drawer.strokeWeight = 0.1
                    drawer.lineSegment(ls)

                    if (seconds % 1.0 == 0.0) {
                        position = busPos
                    }

                } else {

                    val closest = kd.findNearest(busPos)

                    if (closest != null) {
                        val t = ((seconds * 0.5).mod(1.0) * 2.0).coerceAtMost(1.0)

                        drawer.stroke = null
                        drawer.fill = ColorRGBa.GOLD
                        val ls = LineSegment(closest, busPos)
                        drawer.circle(ls.position(t.smoothstep(0.1, 0.8)), 6.0 * (sin(t * 2 * PI) * 0.5 + 0.5))

                        drawer.stroke = ColorRGBa.GOLD
                        drawer.strokeWeight = 0.1
                        drawer.lineSegment(ls)
                    }

                }




            }
        }
    }
}