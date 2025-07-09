package lib

class DoubleArrayIir(val alpha: Double = 0.02, val beta: Double = 0.02, val size: Int) {

    var last = DoubleArray(size)
    var velocity = DoubleArray(size)
    var dyn = DoubleArray(size)

    fun update(values: DoubleArray) {
        for (i in values.indices) {
            velocity[i] += (values[i] - last[i]) * alpha
            velocity[i] += (values[i] - dyn[i]) * beta
            velocity[i] *= 0.90

            last[i] = values[i]
            dyn[i] += velocity[i]
        }
    }
}