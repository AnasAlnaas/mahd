package ly.mahd.app.content

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

enum class GrowthIndicator(val code: String) {
    WEIGHT("weight"),
    LENGTH("length"),
    HEAD("head"),
}

/**
 * The WHO "LMS" numbers for one age: L (skewness), M (median), S (spread).
 * With them we can turn a measurement into a z-score and back. See WHO Child Growth Standards.
 */
data class Lms(val l: Double, val m: Double, val s: Double) {

    fun zScore(value: Double): Double =
        if (abs(l) < 1e-9) ln(value / m) / s else ((value / m).pow(l) - 1.0) / (l * s)

    fun valueAt(z: Double): Double =
        if (abs(l) < 1e-9) m * exp(s * z) else m * (1.0 + l * s * z).pow(1.0 / l)
}

/**
 * WHO Child Growth Standards (birth to 5 years), one row per month.
 * Source: WHO (https://www.who.int/tools/child-growth-standards), monthly values taken from the
 * official daily tables. Between whole months we interpolate linearly, which is accurate enough
 * for a home growth chart.
 */
class GrowthStandards(private val table: Map<Pair<GrowthIndicator, Int>, List<Lms>>) {

    val maxMonth: Int = 60

    /** [sex] uses the WHO code: 1 = boy, 2 = girl. Returns null outside 0–60 months. */
    fun lms(indicator: GrowthIndicator, sex: Int, ageMonths: Double): Lms? {
        val rows = table[indicator to sex] ?: return null
        if (ageMonths < 0 || ageMonths > rows.lastIndex) return null
        val lower = ageMonths.toInt().coerceAtMost(rows.lastIndex)
        val upper = (lower + 1).coerceAtMost(rows.lastIndex)
        val t = ageMonths - lower
        val a = rows[lower]
        val b = rows[upper]
        return Lms(
            l = a.l + (b.l - a.l) * t,
            m = a.m + (b.m - a.m) * t,
            s = a.s + (b.s - a.s) * t,
        )
    }

    companion object {
        /** CSV columns: indicator,sex,month,l,m,s */
        fun parseCsv(text: String): GrowthStandards {
            val rows = HashMap<Pair<GrowthIndicator, Int>, MutableList<Pair<Int, Lms>>>()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("indicator") && !it.startsWith("#") }
                .forEach { line ->
                    val p = line.split(',')
                    val indicator = GrowthIndicator.entries.first { it.code == p[0] }
                    val key = indicator to p[1].toInt()
                    rows.getOrPut(key) { mutableListOf() }
                        .add(p[2].toInt() to Lms(p[3].toDouble(), p[4].toDouble(), p[5].toDouble()))
                }
            return GrowthStandards(rows.mapValues { (_, list) -> list.sortedBy { it.first }.map { it.second } })
        }
    }
}
