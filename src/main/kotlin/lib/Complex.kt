package lib

import org.openrndr.math.Matrix44
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.asDegrees
import org.openrndr.math.asRadians
import org.openrndr.math.transforms.buildTransform
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class Complex(val real: Double, val imaginary: Double) {
    operator fun plus(other: Complex): Complex {
        return Complex(real + other.real, imaginary + other.imaginary)
    }

    operator fun times(other: Complex): Complex {
        return Complex(real * other.real - imaginary * other.imaginary, real * other.imaginary + imaginary * other.real)
    }

    operator fun unaryMinus(): Complex {
        return Complex(-real, -imaginary)
    }

    operator fun div(other: Complex): Complex {
        val c = other.real * other.real + other.imaginary * other.imaginary
        return Complex((real * other.real + imaginary * other.imaginary) / c, (imaginary * other.real - real * other.imaginary) / c)
    }

    operator fun div(other: Double): Complex {
        return Complex(real / other, imaginary / other)
    }

    operator fun minus(other: Complex): Complex {
        return Complex(real - other.real, imaginary - other.imaginary)
    }

    operator fun times(other: Double): Complex {
        return Complex(real * other, imaginary * other)
    }

    fun magnitude(): Double {
        return Math.sqrt(real * real + imaginary * imaginary)
    }

    fun absPow2(): Double {
        return real * real + imaginary * imaginary
    }

    fun conjugate(): Complex {
        return Complex(real, -imaginary)
    }

    fun normalize(): Complex {
        val m = magnitude()
        return Complex(real / m, imaginary / m)
    }

    fun sqrt(): Complex {
        val r = Math.sqrt(Math.sqrt(real * real + imaginary * imaginary))
        val t = Math.atan2(imaginary, real) / 2.0
        return Complex(r * cos(t), r * sin(t))
    }

    fun pow(exponent: Double): Complex {
        val m = magnitude().pow(exponent)
        val phi = argument() * exponent

        return Complex(m * cos(phi), m * sin(phi))
//        return magnitude().pow(exponent).let {
//            Complex(it * cos(argument() * exponent), it * sin(argument() * exponent))
//        }
    }

    /**
     * Calculates the argument (or phase) of the complex number, which is the angle
     * that the complex number makes with the positive real axis in the complex plane.
     * The value is returned in radians and ranges from `-PI` to `PI`.
     *
     * @return the argument of the complex number in radians.
     */
    fun argument(): Double {
        return atan2(imaginary, real)
    }

    /**
     * Converts the current `Complex` instance to a `Vector2` object.
     * The real part of the complex number is mapped to the `x` component,
     * and the imaginary part is mapped to the `y` component of the vector.
     *
     * @return a `Vector2` representing the complex number as a two-dimensional vector.
     */
    fun toVector2(): Vector2 = Vector2(real, imaginary)

    fun pose(): Matrix44 {
        return buildTransform {
            scale(magnitude())
            rotate(Vector3.UNIT_Z, argument().asDegrees)
        }
    }

    companion object {

        fun fromRadians(radians: Double): Complex {
            return Complex(cos(radians), sin(radians))
        }

        fun fromPolar(magnitude: Double, argument: Double): Complex {
            return Complex(magnitude * cos(argument), magnitude * sin(argument))
        }

        fun fromPolar(polar: Polar) = Complex.fromPolar(polar.radius, polar.theta.asRadians)

        /**
         * Creates a `Complex` number using the components of a `Vector2` object.
         *
         * @param v the `Vector2` instance containing `x` and `y` values to be used as the real and imaginary parts of the complex number.
         * @return a `Complex` instance where the real part is set to `v.x` and the imaginary part is set to `v.y`.
         */
        fun fomVector2(v: Vector2): Complex = Complex(v.x, v.y)
    }
}