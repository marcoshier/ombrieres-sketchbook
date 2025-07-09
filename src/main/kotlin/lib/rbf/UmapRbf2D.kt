package lib.rbf

import org.openrndr.extra.triangulation.voronoiDiagram
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import org.openrndr.shape.bounds
import org.openrndr.shape.map
import tagbio.umap.Umap

/**
 * Performs UMAP dimensionality reduction on a 2D array of data points and constructs a
 * two-dimensional Radial Basis Function (RBF) interpolator for further use.
 *
 * This method applies UMAP to reduce the data to two dimensions, adjusts the points
 * to fit within the specified bounds, optionally performs Voronoi shifting, and
 * constructs an RBF interpolator using the transformed data points.
 *
 * @param smoothing A non-negative smoothing factor to reduce sensitivity in interpolation.
 *                  Default is 0.0.
 * @param bounds The rectangular bounds within which the transformed points should be mapped.
 * @param nearestNeighbours Number of nearest neighbors to consider during the UMAP reduction.
 *                          Affects the local structure preservation. Default is 15.
 * @param voronoiShiftIterations Number of iterations for performing Voronoi-based centroid
 *                                adjustments on the points. Default is 0.
 * @param seed The seed for randomization in UMAP. Default is 0L.
 * @param rbf A function defining the Radial Basis Function (RBF) kernel for interpolation.
 *            The function accepts two `Vector2` points and returns a `Double` representing
 *            the kernel value between them.
 * @return An instance of `Rbf2DInterpolator`, configured using the transformed data points
 *         and the specified RBF kernel.
 */
fun Array<DoubleArray>.umapRbf(smoothing: Double = 0.0,
                               bounds: Rectangle,
                               nearestNeighbours: Int = 15,
                               voronoiShiftIterations: Int = 0,
                               seed: Long = 0L,
                               rbf: (Vector2, Vector2) -> Double): Rbf2DInterpolator {
    val umap = Umap()
    umap.setNumberComponents(2)
    umap.setNumberNearestNeighbours(nearestNeighbours)
    umap.setSeed(seed)
    val fit = umap.fitTransform(this)
    var points = fit.map { Vector2(it[0], it[1]) }.toMutableList()
    val ogbounds = points.bounds
    val tbounds = bounds
    points = points.map(ogbounds, tbounds).toMutableList()

    for (i in 0 until voronoiShiftIterations) {
        val vd = points.voronoiDiagram(bounds)
        for (p in points.indices) {
            val centroid = vd.cellCentroid(p)
            if (centroid.x == centroid.x && centroid.y == centroid.y) {
                points[p] = points[p].mix(centroid, 0.5)
            }
        }
    }

    val rbf = rbfInterpolator(points, this, smoothing, rbf)
    return rbf
}