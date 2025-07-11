import lib.Graph
import lib.Link
import lib.Matrix
import lib.Node
import lib.adjacencyMatrix
import lib.degreeMatrix
import lib.normalize
import lib.plus
import lib.times
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.isolated
import org.openrndr.extra.noise.scatter
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import kotlin.math.sqrt

fun main() {
    application {
        configure {
            width = 720
            height = 720
        }

        program {

            class MyNode(override val id: Int, var position: Vector2, var velocity: Vector2 = Vector2.ZERO) : Node

           // extend(ScreenRecorder())

            val g = Graph<MyNode>()


            fun makeTree(prevIdx: Int, currentDepth: Int = 0) {
                if (currentDepth >= 5) return

                for (i in 0 until 5) {
                    val newPos = Polar((i - 3) / 5.0 * 360.0 - (50.0 * currentDepth).coerceAtLeast(240.0), 100.0).cartesian + g.nodes[prevIdx].position
                    val nodeId = g.nodes.size
                    g.nodes.add(MyNode(nodeId, newPos))
                    g.links.add(Link(prevIdx, nodeId))

                    makeTree(nodeId, currentDepth + 1)
                }
            }

            g.nodes.add(MyNode(0, drawer.bounds.center))
            makeTree(0)



           /* val map = pts.mapIndexed { index, vector2 -> Pair(vector2, index) }.toMap()
            val kd = pts.kdTree()

            for (p in pts) {
                val nearest = kd.findKNearest(p, 4)
                for (n in nearest) {
                    if (p == n) continue

                    val dir = (p - n).normalized
                    val centerDir = (p - drawer.bounds.center).normalized

                    val w = dir.perpendicular().dot(centerDir) * 0.5 + 0.5

                    val source = map[p] ?: error("no source found")
                    val target = map[n] ?: error("no target found")

                    g.links.add(Link(source, target, w + Double.uniform(-0.3, 0.3))) // +
                }
            }*/


            val lm = g.adjacencyMatrix().normalize(g.degreeMatrix())

            val signal = Matrix.zeros(g.nodes.size, 1)

            var diffused = lm * signal

            fun filter(coeffs: DoubleArray, shift: Matrix, signal: Matrix): Matrix {
                val shifted = mutableListOf<Matrix>()

                var current = signal
                shifted.add(current)

                for (i in 0 until coeffs.size - 1) {
                    current = shift * current
                    shifted.add(current)
                }

                var result = Matrix.zeros(signal.rows, signal.cols)

                for (i in 0 until coeffs.size) {
                    result += (shifted[i] * coeffs[i])
                }

                return result
            }

            var diffusedHist = MutableList(20) { Matrix.zeros(g.nodes.size, 1) }

            var l0 = 0.0

            /*mouse.buttonUp.listen {
                val nearest = kd.findNearest(it.position)
                val nearestId = map[nearest]

                if (nearestId != null) {
                    l0 = 1.0
                    if (it.button == MouseButton.LEFT) {
                        diffused.data[nearestId][0] = 1.0
                    } else {
                        diffused.data[nearestId][0] = -1.0
                    }
                }
            }*/

            val allCoeffs = listOf(
                doubleArrayOf(1.0, 0.1),
                doubleArrayOf(1.0, -0.5, 0.2),
                doubleArrayOf(0.5, 0.8, 0.3, -0.1),
                doubleArrayOf(0.8, 0.3, 0.1, 0.05, 0.02),
                doubleArrayOf(1.0, -0.8, 0.6, -0.4, 0.2)
            )

            var sign = -1.0

            keyboard.character.listen {
                sign = -sign
            }



            fun updateGraph(dt: Double) {
                g.nodes.forEach { it.velocity *= 0.0 }

                // Apply repulsion forces between all nodes
                for (i in g.nodes.indices) {
                    for (j in i + 1 until g.nodes.size) {
                        val n1 = g.nodes[i]
                        val n2 = g.nodes[j]

                        val dx = n1.position.x - n2.position.x
                        val dy = n1.position.y - n2.position.y
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > 0) {
                            val force = 1000.0 / (dist * dist)
                            val fx = (dx / dist) * force
                            val fy = (dy / dist) * force

                            n1.velocity += Vector2(fx, fy)
                            n2.velocity -= Vector2(fx, fy)
                        }
                    }
                }

                g.links.forEach { link ->
                    val dx = g.nodes[link.target].position.x - g.nodes[link.source].position.x
                    val dy = g.nodes[link.target].position.y - g.nodes[link.source].position.y
                    val dist = sqrt(dx * dx + dy * dy)

                    if (dist > 0) {
                        val displacement = dist - 100.0
                        val force = 0.4 * displacement
                        val fx = (dx / dist) * force
                        val fy = (dy / dist) * force

                        g.nodes[link.source].velocity += Vector2(fx, fy)
                        g.nodes[link.target].velocity -= Vector2(fx, fy)
                    }
                }

                // Update positions
                g.nodes.forEach { node ->
                    node.velocity *= 0.95
                    node.position += node.velocity * dt
                }
            }


            extend {
                drawer.clear(ColorRGBa.GRAY.shade(1.0))

                l0 -= 0.0001

                var newDiffused = filter(allCoeffs[0], lm, diffused) + (diffusedHist.last() * 0.05).times(sign)

                for (y in 0 until newDiffused.rows) {
                    newDiffused[y, 0] = newDiffused[y, 0].coerceIn(0.0, 1.0)
                }

                diffusedHist.add(0, diffused)
                diffusedHist.removeLast()
                diffused = newDiffused


             //   updateGraph(1.0 / 60.0)

                drawer.isolated {
                    // Draw links
                    stroke = ColorRGBa.BLACK
                    strokeWeight = 1.0
                    g.links.forEach { link ->
                        lineSegment(g.nodes[link.source].position, g.nodes[link.target].position)
                    }

                    // Draw nodes
                    fill = ColorRGBa.WHITE
                    stroke = ColorRGBa.WHITE
                    g.nodes.forEach { node ->
                        circle(node.position, 9.0)
                    }
                }

               /* drawer.circles {
                    for (i in g.nodes.indices) {
                        fill = ColorRGBa.WHITE.shade(diffused[i, 0].coerceIn(0.0, 1.0))
                        circle(g.nodes[i].position, 9.0)
                    }
                }

                val nearest = kd.findNearest(mouse.position)

                var drawn = false

                g.links.map {
                    drawer.stroke = ColorRGBa.BLACK
                    val l = LineSegment(g.nodes[it.source].position, g.nodes[it.target].position)
                    drawer.lineSegment(l)

                    if (it.source == map[nearest] && !drawn) {
                        drawer.stroke = null
                        drawer.fill = ColorRGBa.WHITE
                        drawer.text(it.weight.toString().take(4), g.nodes[it.source].position.mix(g.nodes[it.target].position, 0.0))
                        drawn = true
                    }

                }*/


                /*drawer.stroke = null
                drawer.fill = ColorRGBa.BLACK
                drawer.circle(mouse.position, 1.0)

                drawer.fill = ColorRGBa.BLUE
                drawer.stroke = null

                if (nearest != null) {
                    drawer.circle(nearest, 9.0)
                }*/
            }
        }
    }
}