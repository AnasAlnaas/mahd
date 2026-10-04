package ly.mahd.app.logic

import ly.mahd.app.content.VaccineDose
import ly.mahd.app.content.VaccineSchedule
import ly.mahd.app.content.VaccineVisit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Works out, for one child, when each vaccine visit is due and what has been given. */
object VaccinePlanner {

    enum class Status { DONE, PARTLY_DONE, OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING, SKIPPED }

    data class VisitPlan(
        val visit: VaccineVisit,
        val dueDate: LocalDate,
        val given: Map<String, LocalDate>,
        /** Doses the mother marked "skipped / not applicable". */
        val skipped: Set<String>,
        /** Doses whose maximum age has passed (e.g. rotavirus after 8 months) and that were never given. */
        val expired: Set<String>,
        val status: Status,
        /** Positive = days until due, negative = days late. */
        val daysUntilDue: Int,
    ) {
        val givenCount: Int get() = visit.doses.count { it.id in given }

        /** Doses that still need to be given: not given, not skipped and not too old. */
        val remainingDoses: List<VaccineDose>
            get() = visit.doses.filter { it.id !in given && it.id !in skipped && it.id !in expired }

        /** True when nothing is left to do for this visit (everything given, skipped or expired). */
        val isComplete: Boolean get() = remainingDoses.isEmpty()
    }

    /** "Soon" means within this many days. */
    const val SOON_DAYS = 14

    fun dueDate(birth: LocalDate, visit: VaccineVisit): LocalDate = birth.plusMonths(visit.months.toLong())

    fun plan(
        schedule: VaccineSchedule,
        birth: LocalDate,
        givenDoses: Map<String, LocalDate>,
        today: LocalDate,
        skippedDoses: Set<String> = emptySet(),
    ): List<VisitPlan> = schedule.visits.map { visit ->
        val due = dueDate(birth, visit)
        val given = visit.doses.mapNotNull { dose -> givenDoses[dose.id]?.let { dose.id to it } }.toMap()
        val skipped = visit.doses.map { it.id }.filter { it in skippedDoses && it !in given }.toSet()
        val expired = visit.doses
            .filter { dose ->
                dose.id !in given && dose.maxAgeMonths != null &&
                    today.isAfter(birth.plusMonths(dose.maxAgeMonths.toLong()))
            }
            .map { it.id }
            .toSet()
        val remaining = visit.doses.count { it.id !in given && it.id !in skipped && it.id !in expired }
        val days = ChronoUnit.DAYS.between(today, due).toInt()
        val status = when {
            remaining == 0 -> if (given.isEmpty()) Status.SKIPPED else Status.DONE
            days < 0 && (given.isNotEmpty() || skipped.isNotEmpty()) -> Status.PARTLY_DONE
            days < 0 -> Status.OVERDUE
            days == 0 -> Status.DUE_TODAY
            days <= SOON_DAYS -> Status.DUE_SOON
            else -> Status.UPCOMING
        }
        VisitPlan(visit, due, given, skipped, expired, status, days)
    }

    /** The first visit that still has doses to give. */
    fun next(plans: List<VisitPlan>): VisitPlan? = plans.firstOrNull { !it.isComplete }
}
