package ly.mahd.app.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** When vaccine reminders are sent, kept apart from Android code so it can be unit tested. */
object ReminderRules {
    /** Days relative to the due date: 3 days before, the day itself, then 3, 7 and 14 days late. */
    val VACCINE_DAYS = listOf(3, 0, -3, -7, -14)

    /**
     * If the daily check could not run on the exact day (phone off, battery saver killed the alarm),
     * a reminder is still sent up to this many days late instead of being lost.
     */
    const val CATCH_UP_DAYS = 2

    /** The daily check runs at this time of day (or, since the alarm is inexact, some time after it). */
    val CHECK_TIME: LocalTime = LocalTime.of(10, 0)

    /**
     * The reminder that applies today for a visit that is [daysUntilDue] days away (negative = late),
     * or null if today is not a reminder day. Each threshold is sent only once (see ReminderReceiver).
     */
    fun vaccineThreshold(daysUntilDue: Int): Int? =
        VACCINE_DAYS.firstOrNull { daysUntilDue <= it && daysUntilDue >= it - CATCH_UP_DAYS }

    /**
     * True when today's check has not run yet although its time has passed: the alarm can come hours
     * late, or not at all when the phone's battery saver stops it. The app then runs the check itself
     * when it is opened.
     */
    fun isCheckDue(lastCheck: LocalDate?, now: LocalDateTime): Boolean =
        (lastCheck == null || lastCheck.isBefore(now.toLocalDate())) && !now.toLocalTime().isBefore(CHECK_TIME)
}
