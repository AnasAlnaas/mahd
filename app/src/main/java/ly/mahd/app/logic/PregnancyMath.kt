package ly.mahd.app.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pregnancy dating, using the standard 280-day (40-week) rule counted from the last period. */
object PregnancyMath {
    const val TERM_DAYS = 280
    const val TERM_WEEKS = 40

    fun dueDateFromLastPeriod(lastPeriod: LocalDate): LocalDate = lastPeriod.plusDays(TERM_DAYS.toLong())

    fun lastPeriodFromDueDate(dueDate: LocalDate): LocalDate = dueDate.minusDays(TERM_DAYS.toLong())

    /** Gestational age as completed weeks + days, e.g. 12 weeks and 3 days. */
    data class GestationalAge(val weeks: Int, val days: Int) {
        val totalDays: Int get() = weeks * 7 + days
    }

    fun gestationalAge(dueDate: LocalDate, on: LocalDate): GestationalAge {
        val days = ChronoUnit.DAYS.between(lastPeriodFromDueDate(dueDate), on).toInt().coerceAtLeast(0)
        return GestationalAge(days / 7, days % 7)
    }

    fun daysUntilDue(dueDate: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, dueDate).toInt()

    /** 1st trimester: weeks 0–13, 2nd: 14–27, 3rd: 28 and after. */
    fun trimester(weeks: Int): Int = when {
        weeks < 14 -> 1
        weeks < 28 -> 2
        else -> 3
    }

    /** Fraction of the 40 weeks done, for progress bars. */
    fun progress(age: GestationalAge): Float = (age.totalDays / TERM_DAYS.toFloat()).coerceIn(0f, 1f)

    /**
     * A due date makes sense if the pregnancy is between 0 and 44 weeks today
     * (it can be set a little in the past when the baby is late).
     */
    fun isPlausibleDueDate(dueDate: LocalDate, today: LocalDate): Boolean {
        val days = ChronoUnit.DAYS.between(lastPeriodFromDueDate(dueDate), today)
        return days in 0..(44 * 7)
    }
}
