package lib

import org.openrndr.extra.noise.uniform
import org.openrndr.math.Vector2
import org.openrndr.shape.Segment2D
import kotlin.collections.forEach
import kotlin.math.cos
import kotlin.random.Random
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.until

class Branch(val id: Int, val children: List<Branch>)

fun <T> generateTree(
    root: T,
    trunk: Segment2D,
    id: (T) -> Int,
    children: (T) -> List<T>,
    seconds: Double,
    random: Random,
    focusT: Double = 0.0,
): Pair<List<Segment2D>, List<Int>> {

    val wind = Vector2(cos(trunk.start.x * 0.00101 + seconds * 1.0) * 5.0 + 5.0, 0.0) * (1.0 - focusT * 0.8)

    val lengths = listOf(
        1.0 + Double.uniform(-0.1, 0.1, random),
        0.7 + Double.uniform(-0.1, 0.1, random),
        0.7 + Double.uniform(-0.1, 0.1, random),
        0.7,
        0.5,
        0.5,
        0.5,
    )


    fun countNodes(pd: Branch): Int {
        var count = 0
        fun recurse(pd: Branch) {
            count += pd.children.size
            pd.children.forEach { recurse(it) }
        }
        recurse(pd)
        return count
    }

    val count = countNodes(root as Branch)


    val branches = MutableList(count + 1) { trunk }
    val parents = MutableList(count + 1) { -1 }

    fun generate(node: T, parent: Segment2D, idepth: Int, spread: Double) {

        val childNodes = children(node)

        val splitFactor = childNodes.size

        val pId = id(node)
        for (i in 0 until splitFactor) {

            val id = id(childNodes[i])
            var rotate = (i.toDouble() / (splitFactor.toDouble() - 1.0).coerceAtLeast(1.0))
            rotate = rotate - 0.5

            val dirMpd =
                cos(0.1 * seconds * Double.uniform(0.9, 1.1, random) + Double.uniform(-Math.PI, Math.PI, random)) * 20.0
            val lengthMpd =
                lengths[idepth] + Double.uniform(-0.1, 0.1, random) * cos(seconds + i + idepth) * (1.0 - focusT * 0.35)

            val newSpread = spread
            val newLength = parent.length * lengthMpd

            val newDir = parent.direction(1.0).normalized.rotate(rotate * newSpread + dirMpd * (1.0 - focusT))
            val newEnd = parent.end + newDir * newLength + wind

            val curl = Double.uniform(0.2, 0.4, random)
            val newBranch = parent.continueTo(newEnd + Vector2(0.0, 1.0), curl)

            branches[id] = newBranch
            parents[id] = pId

            if (idepth < 3) {
                generate(childNodes[i], newBranch, idepth + 1, newSpread * 0.75)
            }
        }
    }

    branches[0] = trunk
    parents[0] = -1


    generate(
        node = root,
        parent = trunk,
        idepth = 0,
        spread = 135.0
    )


    return Pair(branches, parents)
}