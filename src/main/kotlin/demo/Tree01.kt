package demo

import lib.Branch
import lib.TreeIir
import lib.generateTree
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BlendMode
import org.openrndr.extra.camera.Camera2D
import org.openrndr.extra.color.spaces.ColorOKHSLa
import org.openrndr.extra.color.tools.shiftHue
import org.openrndr.extra.fx.Post
import org.openrndr.extra.fx.blur.GaussianBloom
import org.openrndr.extra.fx.blur.GaussianBlur
import org.openrndr.extra.fx.color.ColorCorrection
import org.openrndr.extra.noise.uniform
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.extra.shapes.path3d.toSegment3D
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.shape.Segment2D
import kotlin.random.Random

fun main() = application {
    configure {
        width = 1080
        height = 1080
    }

    program {

        extend(ScreenRecorder())

        val n = 5
        val iirs = List(n) { TreeIir() }

        val ids = MutableList(n) { 0 }
        fun populateTree(
            idx: Int,
            maxDepth: Int,
            maxChildren: Int,
            currentDepth: Int = 0
        ): Branch {
            ids[idx]++

            if (currentDepth >= maxDepth) {
                return Branch(ids[idx], emptyList())
            }

            return Branch(ids[idx],
                (0 until Int.uniform(2, maxChildren + 1, Random(idx + currentDepth))).map {
                    populateTree(idx, maxDepth, maxChildren, currentDepth + 1)
                }
            )
        }

        val trees = List(n) {
            populateTree(it, 5, 5)
        }

        extend(Post()) {
            val cc = ColorCorrection()
            val gb = GaussianBlur()

            gb.spread = 2.0
            gb.gain = 2.0
            gb.window = 20
          //  gb.sigma = 10.0
            cc.brightness = 0.8
            cc.saturation = -1.0
            cc.contrast = 2.0

            post { input, output ->
                val i0 = intermediate[0]
                cc.apply(input, i0)
                gb.apply(i0, output)
            }
        }

        extend(Camera2D())
        extend {

            for(i in 0 until n) {
                val p = Polar(360.0 / n * i, 120.0).cartesian + drawer.bounds.center

                val trunk = Segment2D(drawer.bounds.center, p)
                val (segments, parents) = generateTree(trees[i], trunk, {it.id}, { it.children }, seconds * Double.uniform(0.1, 2.0, Random((seconds * 0.1).toInt())), Random(0))
                drawer.stroke = ColorRGBa.WHITE
                drawer.strokeWeight = 20.0
                iirs[i].update(segments, parents)

                drawer.drawStyle.blendMode = BlendMode.EXCLUSION
                drawer.segments(iirs[i].dyn.map { it.toSegment3D() }, List(iirs[i].dyn.size) { 100.0 } , iirs[i].dyn.mapIndexed { j, it -> ColorRGBa.RED })
            }



          //  drawer.segments(segments)

        }
    }

}