package ly.mahd.app.logic

import ly.mahd.app.content.PostnatalCheckup
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** الأربعين: the mother's six weeks after the birth, kept apart from Android code so it can be unit tested. */
object PostpartumRules {
    /** الأربعين lasts 40 days; the section stays a few days longer, for the six-week check-up. */
    const val DAYS = 40

    /** Day of الأربعين: the day of birth is day 1. */
    fun day(birth: LocalDate, today: LocalDate): Int = ChronoUnit.DAYS.between(birth, today).toInt() + 1

    /** Whether the الأربعين section is shown for a baby born on [birth] (from birth to [lastDay]). */
    fun isActive(birth: LocalDate, today: LocalDate, lastDay: Int): Boolean = day(birth, today) in 1..lastDay

    /**
     * The check-up whose reminder applies today: any day inside its window, so a reminder is still sent if the
     * daily check ran late. Each one is sent once (see ReminderReceiver).
     */
    fun checkupDue(checkups: List<PostnatalCheckup>, day: Int): PostnatalCheckup? =
        checkups.firstOrNull { day in it.fromDay..it.toDay }

    /** The first and last date of a check-up's window. */
    fun window(checkup: PostnatalCheckup, birth: LocalDate): Pair<LocalDate, LocalDate> =
        birth.plusDays(checkup.fromDay - 1L) to birth.plusDays(checkup.toDay - 1L)
}
