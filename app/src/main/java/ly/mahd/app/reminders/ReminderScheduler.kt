package ly.mahd.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules one gentle daily check (around 10 in the morning) using Android's built-in
 * AlarmManager. "Inexact" alarms need no special permission and are kind to the battery.
 */
object ReminderScheduler {
    private val CHECK_TIME: LocalTime = LocalTime.of(10, 0)

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), CHECK_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Setting the same PendingIntent again replaces the old alarm, so this is safe to call often.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
