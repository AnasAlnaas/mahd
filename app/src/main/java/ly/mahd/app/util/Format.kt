package ly.mahd.app.util

import android.content.res.Resources
import ly.mahd.app.R
import java.time.LocalDate
import java.time.Period
import java.util.Locale

/**
 * Text formatting for dates, numbers and ages.
 *
 * Numbers always use the digits 0–9 (as is usual in Libya) — we never let the phone's locale
 * switch them, so the same screen looks the same on every phone.
 */
object Format {

    private val MONTHS = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    /** e.g. "2 أكتوبر 2026" */
    fun date(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"

    /** e.g. "2 أكتوبر" (no year) */
    fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

    /** One decimal place, dropping ".0": 3.0 → "3", 3.25 → "3.3". */
    fun decimal(value: Double, places: Int = 1): String {
        val text = String.format(Locale.US, "%.${places}f", value)
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    fun years(res: Resources, n: Int): String = res.getQuantityString(R.plurals.years, n, n.toString())
    fun months(res: Resources, n: Int): String = res.getQuantityString(R.plurals.months, n, n.toString())
    fun weeks(res: Resources, n: Int): String = res.getQuantityString(R.plurals.weeks, n, n.toString())
    fun days(res: Resources, n: Int): String = res.getQuantityString(R.plurals.days, n, n.toString())
    fun doses(res: Resources, n: Int): String = res.getQuantityString(R.plurals.doses, n, n.toString())
    fun photos(res: Resources, n: Int): String = res.getQuantityString(R.plurals.photos, n, n.toString())

    /** A shorter age for small labels: days in the first month, then months, then years and months ("سنة و4 أشهر"). */
    fun shortAge(res: Resources, period: Period): String {
        val totalMonths = period.toTotalMonths().toInt()
        return when {
            totalMonths == 0 -> if (period.days == 0) res.getString(R.string.age_newborn) else days(res, period.days)
            period.years == 0 -> months(res, period.months)
            period.months == 0 -> years(res, period.years)
            else -> and(years(res, period.years), months(res, period.months))
        }
    }

    /** Joins two parts with the Arabic "and": "8 أشهر و5 أيام". */
    fun and(first: String, second: String): String = "$first و$second"

    /** A child's age in words: "12 يومًا", "8 أشهر و5 أيام", "سنتان و3 أشهر". */
    fun age(res: Resources, period: Period): String {
        val totalMonths = period.toTotalMonths().toInt()
        return when {
            totalMonths == 0 && period.days == 0 -> res.getString(R.string.age_newborn)
            totalMonths == 0 -> days(res, period.days)
            period.years == 0 -> if (period.days == 0) months(res, period.months)
            else and(months(res, period.months), days(res, period.days))
            else -> if (period.months == 0) years(res, period.years)
            else and(years(res, period.years), months(res, period.months))
        }
    }

    /** "20 أسبوعًا و3 أيام" */
    fun gestationalAge(res: Resources, weeks: Int, days: Int): String =
        if (days == 0) weeks(res, weeks) else and(weeks(res, weeks), days(res, days))
}
