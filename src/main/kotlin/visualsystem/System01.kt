package visualsystem

import lib.DoubleIir
import lib.Pixelate
import org.openrndr.KEY_SPACEBAR
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.MagnifyingFilter
import org.openrndr.draw.MinifyingFilter
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.persistent
import org.openrndr.draw.renderTarget
import org.openrndr.extra.fx.blur.FrameBlur
import org.openrndr.extra.gui.WindowedGUI
import org.openrndr.extra.noise.simplex
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.Once
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.parameters.DoubleParameter
import org.openrndr.extra.parameters.IntParameter
import org.openrndr.extra.shapes.rectify.rectified
import org.openrndr.math.Vector2
import org.openrndr.shape.Circle
import org.openrndr.shape.ShapeContour
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

var fileIdx = 0

fun main() {
    application {
        configure {
            width = 1080
            height = 1080
        }

        program {
            val gui = WindowedGUI().apply { gui.compartmentsCollapsedByDefault = false }

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


            val rt = renderTarget(400, 400) {
                colorBuffer()
                depthBuffer()
            }

            keyboard.keyUp.listen {
                if (it.key == KEY_SPACEBAR) {
                    println("saving $fileIdx")
                    gui.gui.saveParameters(File("data/vs-parameters/$fileIdx.json"))
                    fileIdx++
                }
            }


            extend(gui)

            val fb = FrameBlur()
            val px = Pixelate()
            px.resolution = 0.03
            extend {

                fb.blend = params.blend

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

                        val pos = seconds * params.speed + Double.uniform(0.0, params.offset, Random(i))

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