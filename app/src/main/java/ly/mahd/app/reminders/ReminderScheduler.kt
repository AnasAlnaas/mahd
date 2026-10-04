package ly.mahd.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ly.mahd.app.logic.ReminderRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules one gentle daily check (around 10 in the morning) using Android's built-in
 * AlarmManager. "Inexact" alarms need no special permission and are kind to the battery,
 * but Android may deliver them hours after their time.
 */
object ReminderScheduler {

    /**
     * Sets the daily alarm if it is missing and leaves an existing one alone. Replacing it every time
     * the app opens would move a late-running check to the next day, again and again.
     */
    fun ensureScheduled(context: Context) {
        if (existingPendingIntent(context) == null) schedule(context)
    }

    /** Sets (or replaces) the daily alarm. Needed after the phone restarts or the app is updated. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), ReminderRules.CHECK_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Setting the same PendingIntent again replaces the old alarm.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    private fun intent(context: Context) = Intent(context, ReminderReceiver::class.java)

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The alarm's PendingIntent if the alarm is set, otherwise null. */
    private fun existingPendingIntent(context: Context): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    )
}
