package lib

class DoubleIir(val alpha: Double = 0.02, val beta: Double = 0.02) {

    var last = 0.0
    var velocity = 0.0
    var dyn = 0.0

    fun update(value: Double) {
        velocity += (value - last) * alpha
        velocity += (value - dyn) * beta
        velocity *= 0.90

        last = value
        dyn += velocity
    }
}