package lib

import org.openrndr.math.Vector2
import org.openrndr.shape.Segment2D
import kotlin.isNaN
import kotlin.ranges.until

/**
 * Represents a tree-based IIR (Infinite Impulse Response) filter used for smoothing and dynamic calculations
 * on a tree structure represented as a list of `Segment2D` objects. The filter operates on input segments and
 * calculates dynamic positions and velocities for each segment over time.
 *
 * @constructor Creates a new instance of the `TreeIir` class.
 * @param alpha The alpha blending factor used to determine the influence of position changes on velocities.
 * @param beta The beta blending factor used to determine the influence of dynamics on velocities.
 */
class TreeIir(val alpha: Double = 0.02, val beta: Double = 0.02) {


    var center = Vector2.ZERO
    var fallOff = 0.0

    val last = mutableListOf<Segment2D>()
    val velocities = mutableListOf<Segment2D>()
    val dyn = mutableListOf<Segment2D>()

    /**
     * Updates the internal state of the IIR filter based on the provided tree structure.
     *
     * This method processes a list of `Segment2D` objects, updating the `last`, `velocities`, and `dyn` lists
     * for smoothing and dynamic calculations. The method applies blending factors (`alpha` and `beta`) to influence
     * the dynamic behavior of the tree filter over time.
     *
     * @param tree A list of `Segment2D` objects representing the current state of the tree structure.
     *             Each segment will be used to update the internal state for smoothing and dynamics.
     */
    fun update(tree: List<Segment2D>, parents: List<Int> = emptyList()) {

        for (i in 0 until tree.size) {
            if (tree[i] == Segment2D(Vector2.ZERO, Vector2.ZERO)) {
                error("shouldnt happen")
            }

            if (parents.isEmpty() || i == 0 || parents[i] == -1) {
                if (i + 1 > last.size) {
                    last.add(tree[i])
                }
                if (i + 1 > velocities.size) {
                    velocities.add(Segment2D(Vector2.ZERO, Vector2.ZERO).cubic)
                }
                if (i + 1 > dyn.size) {
                    dyn.add(tree[i])
                }
            } else {
                val parent = parents[i]
                require(parent < i)

                if (i + 1 > last.size) {
                    val zeroLength = Segment2D(dyn[parent].end, dyn[parent].end).cubic
                    last.add(zeroLength)
                }
                if (i + 1 > velocities.size) {
                    velocities.add(
                        Segment2D(
                            velocities[parent].end,
                            velocities[parent].end,
                            velocities[parent].end,
                            velocities[parent].end
                        )
                    )
                }
                if (i + 1 > dyn.size) {
                    val zeroLength = Segment2D(dyn[parent].end, dyn[parent].end).cubic
                    dyn.add(zeroLength)
                }
            }
            velocities[i] += (tree[i] - last[i]) * alpha
            velocities[i] += (tree[i] - dyn[i]) * beta
            velocities[i] *= 0.90

            last[i] = tree[i]
            dyn[i] += velocities[i]
            require(!velocities[i].start.x.isNaN()) { }
            require(!velocities[i].start.y.isNaN())
            require(!velocities[i].end.x.isNaN())
            require(!velocities[i].end.y.isNaN())

            require(!dyn[i].start.x.isNaN())
            require(!dyn[i].start.y.isNaN())
            require(!dyn[i].end.x.isNaN())
            require(!dyn[i].end.y.isNaN())

        }
    }
}