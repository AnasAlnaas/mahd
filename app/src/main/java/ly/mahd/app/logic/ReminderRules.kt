package ly.mahd.app.logic

/** When vaccine reminders are sent, kept apart from Android code so it can be unit tested. */
object ReminderRules {
    /** Days relative to the due date: 3 days before, the day itself, then 3, 7 and 14 days late. */
    val VACCINE_DAYS = listOf(3, 0, -3, -7, -14)

    /**
     * If the daily check could not run on the exact day (phone off, battery saver killed the alarm),
     * a reminder is still sent up to this many days late instead of being lost.
     */
    const val CATCH_UP_DAYS = 2

    /**
     * The reminder that applies today for a visit that is [daysUntilDue] days away (negative = late),
     * or null if today is not a reminder day. Each threshold is sent only once (see ReminderReceiver).
     */
    fun vaccineThreshold(daysUntilDue: Int): Int? =
        VACCINE_DAYS.firstOrNull { daysUntilDue <= it && daysUntilDue >= it - CATCH_UP_DAYS }
}
