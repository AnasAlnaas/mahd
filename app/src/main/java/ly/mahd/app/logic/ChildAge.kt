package ly.mahd.app.logic

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** Child age helpers, including "corrected age" for babies born early. */
object ChildAge {
    /** Average month length used by WHO growth tables. */
    const val DAYS_PER_MONTH = 30.4375

    /** Corrected age is used until this chronological age (in months), as is common practice. */
    const val CORRECT_UNTIL_MONTHS = 24

    fun period(birth: LocalDate, on: LocalDate): Period =
        if (on.isBefore(birth)) Period.ZERO else Period.between(birth, on)

    fun days(birth: LocalDate, on: LocalDate): Long = ChronoUnit.DAYS.between(birth, on).coerceAtLeast(0)

    fun months(birth: LocalDate, on: LocalDate): Double = days(birth, on) / DAYS_PER_MONTH

    /**
     * For a baby born before 37 weeks, the birthday "as if born on the due date":
     * birth date + (40 − weeks at birth) weeks.
     */
    fun correctedBirthDate(birth: LocalDate, weeksAtBirth: Int?): LocalDate =
        if (weeksAtBirth != null && weeksAtBirth < 37) birth.plusWeeks((40 - weeksAtBirth).toLong()) else birth

    /** True when corrected age should be used today. */
    fun usesCorrectedAge(birth: LocalDate, weeksAtBirth: Int?, on: LocalDate): Boolean =
        weeksAtBirth != null && weeksAtBirth < 37 && months(birth, on) < CORRECT_UNTIL_MONTHS

    /**
     * Like [referenceMonths], but negative while a premature baby has not yet reached the age of
     * the original due date (corrected age < 0). WHO growth standards (term babies) do not apply then.
     */
    fun referenceMonthsSigned(birth: LocalDate, weeksAtBirth: Int?, on: LocalDate): Double =
        if (usesCorrectedAge(birth, weeksAtBirth, on)) {
            ChronoUnit.DAYS.between(correctedBirthDate(birth, weeksAtBirth), on) / DAYS_PER_MONTH
        } else {
            months(birth, on)
        }

    /** True for a premature baby measured before the original due date. */
    fun isBeforeCorrectedTerm(birth: LocalDate, weeksAtBirth: Int?, on: LocalDate): Boolean =
        referenceMonthsSigned(birth, weeksAtBirth, on) < 0

    /** The age (in months) to use for milestones and growth charts. */
    fun referenceMonths(birth: LocalDate, weeksAtBirth: Int?, on: LocalDate): Double =
        if (usesCorrectedAge(birth, weeksAtBirth, on)) months(correctedBirthDate(birth, weeksAtBirth), on)
        else months(birth, on)
}
