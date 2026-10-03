package ly.mahd.app.logic

import ly.mahd.app.content.VaccineSchedule
import ly.mahd.app.content.VaccineVisit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Works out, for one child, when each vaccine visit is due and what has been given. */
object VaccinePlanner {

    enum class Status { DONE, PARTLY_DONE, OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING }

    data class VisitPlan(
        val visit: VaccineVisit,
        val dueDate: LocalDate,
        val given: Map<String, LocalDate>,
        val status: Status,
        /** Positive = days until due, negative = days late. */
        val daysUntilDue: Int,
    ) {
        val givenCount: Int get() = visit.doses.count { it.id in given }
        val isComplete: Boolean get() = givenCount == visit.doses.size
    }

    /** "Soon" means within this many days. */
    const val SOON_DAYS = 14

    fun dueDate(birth: LocalDate, visit: VaccineVisit): LocalDate = birth.plusMonths(visit.months.toLong())

    fun plan(
        schedule: VaccineSchedule,
        birth: LocalDate,
        givenDoses: Map<String, LocalDate>,
        today: LocalDate,
    ): List<VisitPlan> = schedule.visits.map { visit ->
        val due = dueDate(birth, visit)
        val given = visit.doses.mapNotNull { dose -> givenDoses[dose.id]?.let { dose.id to it } }.toMap()
        val days = ChronoUnit.DAYS.between(today, due).toInt()
        val status = when {
            given.size == visit.doses.size -> Status.DONE
            days < 0 && given.isNotEmpty() -> Status.PARTLY_DONE
            days < 0 -> Status.OVERDUE
            days == 0 -> Status.DUE_TODAY
            days <= SOON_DAYS -> Status.DUE_SOON
            else -> Status.UPCOMING
        }
        VisitPlan(visit, due, given, status, days)
    }

    /** The first visit that still has doses to give. */
    fun next(plans: List<VisitPlan>): VisitPlan? = plans.firstOrNull { !it.isComplete }
}
