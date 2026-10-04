package ly.mahd.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ly.mahd.app.R
import ly.mahd.app.app
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.logic.ReminderRules
import ly.mahd.app.logic.VaccinePlanner
import ly.mahd.app.util.AppLocale
import ly.mahd.app.util.Format
import java.time.LocalDate
import kotlin.concurrent.thread

/** Runs once a day: vaccine reminders and "a new pregnancy week has started". */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread(name = "daily-reminders") {
            try {
                runChecks(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun runChecks(context: Context, today: LocalDate = LocalDate.now()) {
            val app = context.app
            if (!app.prefs.remindersEnabled.value) return
            val res = AppLocale.wrap(context).resources
            val prefs = app.prefs

            for (profile in app.repository.profilesNow()) {
                if (profile.isPregnancy && profile.dueDate != null) {
                    val week = PregnancyMath.gestationalAge(profile.dueDate, today).weeks
                    val last = prefs.lastNotifiedWeek(profile.id)
                    if (last in 0 until week && week in 4..42) {
                        val title = res.getString(R.string.notify_new_week_title, week.toString())
                        val text = res.getString(R.string.notify_new_week_text)
                        Notifications.post(context, "week:${profile.id}:$week", title, text)
                    }
                    if (last != week) prefs.setLastNotifiedWeek(profile.id, week)
                }

                val birth = profile.birthDate
                if (profile.isChild && birth != null) {
                    val given = app.repository.dosesNow(profile.id)
                    val skipped = app.repository.skippedDosesNow(profile.id)
                    val plans = VaccinePlanner.plan(app.content.vaccines, birth, given, today, skipped)
                    for (plan in plans) {
                        if (plan.isComplete) continue
                        val d = plan.daysUntilDue
                        // One reminder per threshold, even if the check ran a day or two late.
                        val threshold = ReminderRules.vaccineThreshold(d) ?: continue
                        val key = "vaccine:${profile.id}:${plan.visit.id}:$threshold"
                        if (prefs.wasNotified(key)) continue
                        val title = res.getString(R.string.notify_vaccine_title, profile.name)
                        val text = when {
                            d > 0 -> res.getString(R.string.notify_vaccine_soon, plan.visit.title, Format.days(res, d))
                            d == 0 -> res.getString(R.string.notify_vaccine_today, plan.visit.title)
                            else -> res.getString(R.string.notify_vaccine_late, plan.visit.title, Format.days(res, -d))
                        }
                        // Only mark as sent if it was really shown, so a reminder is not lost
                        // when notifications are blocked and later allowed.
                        if (Notifications.post(context, key, title, text)) prefs.markNotified(key)
                    }
                }
            }
        }
    }
}

/** Puts the daily alarm back after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Application.onCreate() already reschedules; calling again is harmless and explicit.
        ReminderScheduler.schedule(context)
    }
}
