package lib

import org.openrndr.math.Vector2
import org.openrndr.shape.Segment2D
import kotlin.collections.isNotEmpty
import kotlin.collections.last

/**
 * Extends the current segment to a given target point with an optional tangent scaling factor.
 * If the current segment has control points, the tangent is calculated based on the last
 * control point and scaled accordingly. Otherwise, a linear segment is created between
 * the current end point and the target.
 *
 * @param target the point to which the segment should be continued.
 * @param tangentScale an optional scaling factor for the tangent of the curve, defaulting to 1.0.
 * @return a new `Segment2D` that extends the current segment to the target point.
 */
fun Segment2D.continueTo(target: Vector2, tangentScale: Double = 1.0): Segment2D {
    if (control.isNotEmpty()) {
        val delta = control.last() - end
        return org.openrndr.shape.Segment2D(end, end - delta * tangentScale, target)
    } else {
        return Segment2D(end, target).quadratic
    }
}
