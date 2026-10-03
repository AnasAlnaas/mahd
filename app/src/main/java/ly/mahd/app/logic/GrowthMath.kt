package ly.mahd.app.logic

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Turns a WHO z-score into something a parent can understand. */
object GrowthMath {

    enum class Band {
        VERY_LOW,   // below −3
        LOW,        // −3 to −2
        NORMAL,     // −2 to +2
        HIGH,       // +2 to +3
        VERY_HIGH,  // above +3
    }

    fun band(z: Double): Band = when {
        z < -3 -> Band.VERY_LOW
        z < -2 -> Band.LOW
        z <= 2 -> Band.NORMAL
        z <= 3 -> Band.HIGH
        else -> Band.VERY_HIGH
    }

    /** Percentile (0–100): how many children out of 100 of the same age measure less. */
    fun percentile(z: Double): Double = 100.0 * normalCdf(z)

    /** Standard normal cumulative distribution. */
    fun normalCdf(z: Double): Double = 0.5 * (1.0 + erf(z / sqrt(2.0)))

    /** Abramowitz & Stegun 7.1.26 (error below 1.5e-7) — plenty for growth charts. */
    fun erf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * abs(x))
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t +
            0.254829592) * t * exp(-x * x)
        return if (x >= 0) y else -y
    }
}
