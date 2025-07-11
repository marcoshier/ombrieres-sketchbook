package lib.rbf

import lib.Matrix
import lib.columnMean
import lib.minus
import org.openrndr.math.Vector2
import kotlin.math.exp

/**
 * Creates a Radial Basis Function (RBF) kernel based on the Gaussian function.
 *
 * @param scale A scaling factor applied to the squared distance in the Gaussian function. It controls how
 *              quickly the RBF value decreases with increasing distance.
 * @return A function that takes two `Vector2` points as input and returns a `Double` representing the RBF
 *         value computed using the Gaussian kernel.
 */
fun rbfGaussian(scale: Double): (Vector2, Vector2) -> Double {
    return { p1, p2 ->
        val d = p1.squaredDistanceTo(p2)
        exp(-d * scale)
    }
}

/**
 * A two-dimensional Radial Basis Function (RBF) interpolator.
 *
 * This class provides functionality to interpolate values in a 2D space
 * using Radial Basis Functions (RBFs). It computes interpolated values for
 * input points based on given data points, their corresponding values, and
 * an RBF kernel that defines the basis function.
 *
 * @constructor
 * @param points A list of 2D points representing the locations of the input data.
 * @param weights A 2D array of weights corresponding to each point for each output dimension.
 * @param values A 2D array of known function values at the given points.
 * @param rbf A function representing the Radial Basis Function (kernel) used for interpolation.
 * The function takes two `Vector2` arguments and returns a `Double`.
 */
class Rbf2DInterpolator(
    val points: List<Vector2>,
    val weights: Array<DoubleArray>,
    val values: Array<DoubleArray>,
    val rbf: (Vector2, Vector2) -> Double,
    val mean: DoubleArray
) {
    fun interpolate(x: Vector2): DoubleArray {
        val c = DoubleArray(values[0].size)
        for (j in points.indices) {
            val r = rbf(points[j], x)
            for (i in 0 until c.size) {
                c[i] += weights[j][i] * r
            }
        }
        for (i in 0 until c.size) {
            c[i] += mean[i]
        }
        return c
    }
}


/**
 * Constructs a two-dimensional Radial Basis Function (RBF) interpolator using provided input points,
 * their corresponding values, a smoothing factor, and a radial basis function (RBF) kernel.
 *
 * The interpolator computes a weight matrix derived from the RBF kernel and the supplied data.
 * The resulting interpolator can be used to estimate the values at new locations in a 2D space.
 *
 * @param points A list of 2D points representing the input data locations.
 * @param values A 2D array of known function values corresponding to the input points.
 *               Each row corresponds to a point, and each column corresponds to a value in a specific dimension.
 * @param smoothing A non-negative smoothing factor to reduce interpolation sensitivity. Default is 0.0.
 *                  Larger values result in smoother interpolations.
 * @param rbf A function defining the Radial Basis Function (RBF) kernel used for interpolation.
 *            The function accepts two `Vector2` points and returns a `Double` representing the basis function value.
 * @return An instance of `Rbf2DInterpolator` configured with the computed weight matrix and input data.
 */
fun rbfInterpolator(
    points: List<Vector2>,
    values: Array<DoubleArray>,
    smoothing: Double = 0.0,
    rbf: (Vector2, Vector2) -> Double
): Rbf2DInterpolator {

    val rmat = Matrix(points.size, points.size)
    for (j in points.indices) {
        for (i in points.indices) {
            rmat[i, j] = rbf(points[i], points[j])
        }
    }

    val imat = invertMatrixCholesky(rmat)

    val vmat = Matrix(points.size, values[0].size)
    for (j in points.indices) {
        for (i in values[0].indices) {
            vmat[j, i] = values[j][i] + if (j == i) smoothing else 0.0
        }
    }
    val mean = vmat.columnMean()
    val vwmat = vmat - mean

    val wmat = imat * vwmat
    return Rbf2DInterpolator(points, wmat.data, values, rbf, mean.data[0])
}