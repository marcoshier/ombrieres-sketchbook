package lib

import kotlin.math.sqrt


class Link(val source: Int, val target: Int, val weight: Double = 1.0)

interface Node {
    val id: Int
}

class Graph<N : Node> {
    val nodes = mutableListOf<N>()
    val links = mutableListOf<Link>()
}

fun Graph<*>.adjacencyMatrix(): Matrix {
    val n = nodes.size
    val matrix = Matrix.zeros(n, n)
    for (link in links) {
        matrix[link.source, link.target] = link.weight
        //matrix[link.target, link.source] = link.weight
    }
    return matrix
}

fun Graph<*>.degreeMatrix(): Matrix {
    val n = nodes.size
    val matrix = Matrix.zeros(n, n)
    for (link in links) {
        matrix[link.source, link.source] += link.weight
     matrix[link.target, link.target] += link.weight
    }
    return matrix
}

fun Graph<*>.laplacianMatrix(): Matrix {
    val n = nodes.size
    val dm = degreeMatrix()
    val am = adjacencyMatrix()
    return dm - am
}

fun Matrix.normalize(degreeMatrix: Matrix): Matrix {
    val result = Matrix(rows, cols)
    for (j in 0 until rows) {
        for (i in 0 until cols) {
            result[j, i] = this[j, i] / sqrt(degreeMatrix[j, j] * degreeMatrix[i, i])
        }
    }
    return result
}