package ly.mahd.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.Log
import ly.mahd.app.R
import ly.mahd.app.app
import ly.mahd.app.content.AdviceKind
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.AdviceRules
import ly.mahd.app.logic.Holiday
import ly.mahd.app.logic.LibyaCalendar
import ly.mahd.app.logic.Occasion
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.logic.ReminderRules
import ly.mahd.app.logic.VaccinePlanner
import ly.mahd.app.ui.Routes
import ly.mahd.app.util.AppLocale
import ly.mahd.app.util.Format
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.concurrent.thread

/** Runs once a day: vaccine reminders, "a new pregnancy week has started", and twice a week a verse or hadith. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        thread(name = "daily-reminders") {
            try {
                runChecksSafely(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "Mahd"
        private val lock = Any()

        /**
         * Called when the app is opened: runs the daily check in the background if it has not run today.
         * The alarm can come hours late, or not at all when the phone's battery saver stops it.
         */
        fun runIfDue(context: Context) {
            val prefs = context.app.prefs
            if (!prefs.remindersEnabled.value || !ReminderRules.isCheckDue(prefs.lastCheckDate, LocalDateTime.now())) return
            thread(name = "reminders-catch-up") { runChecksSafely(context) }
        }

        /** A failing check must not crash the app in the background; it simply runs again next time. */
        private fun runChecksSafely(context: Context) {
            try {
                runChecks(context)
            } catch (e: Exception) {
                Log.e(TAG, "Daily reminder check failed", e)
            }
        }

        fun runChecks(context: Context, today: LocalDate = LocalDate.now()) {
            // The alarm and the app may both start a check; one at a time, so no reminder is sent twice.
            synchronized(lock) {
                val app = context.app
                if (!app.prefs.remindersEnabled.value) return
                val res = AppLocale.wrap(context).resources
                for (profile in app.repository.profilesNow()) {
                    // One profile with bad data must not stop the reminders of the others.
                    try {
                        checkProfile(context, res, profile, today)
                    } catch (e: Exception) {
                        Log.e(TAG, "Reminder check failed for profile ${profile.id}", e)
                    }
                }
                try {
                    sendAdvice(context, res, today)
                } catch (e: Exception) {
                    Log.e(TAG, "Advice notification failed", e)
                }
                app.prefs.lastCheckDate = today
            }
        }

        /**
         * Twice a week, and on the first day of Ramadan and of each Eid (with a greeting as the title), a verse or
         * hadith for the profile she is following, from the same rotation as Home (season, occasion, family).
         * Missed days are not caught up: unlike a vaccine reminder, nothing is lost if one is skipped.
         */
        private fun sendAdvice(context: Context, res: Resources, today: LocalDate) {
            val app = context.app
            if (!app.prefs.adviceNotifications.value || !AdviceRules.isNotificationDay(today)) return
            val key = "advice:$today"
            if (app.prefs.wasNotified(key)) return
            val profiles = app.repository.profilesNow()
            val profile = profiles.firstOrNull { it.id == app.prefs.selectedProfileId.value } ?: profiles.firstOrNull() ?: return
            // The switch promises a verse or hadith, so the science facts stay on Home.
            val islamic = app.content.advice.items.filter { it.kind != AdviceKind.FACT }
            val item = AdviceRules.forDay(islamic, profile, today, app.prefs.parent.value ?: Parent.MOTHER, profiles) ?: return
            val title = when (LibyaCalendar.greeting(today)) {
                Occasion.RAMADAN -> res.getString(R.string.advice_reason_ramadan)
                Occasion.EID_FITR -> res.getString(R.string.advice_reason_eid_fitr)
                Occasion.EID_ADHA -> res.getString(R.string.advice_reason_eid_adha)
                else -> res.getString(if (item.kind == AdviceKind.QURAN) R.string.advice_title_quran else R.string.advice_title_hadith)
            }
            val text = "${item.displayText}\n${item.source}"
            if (Notifications.post(context, key, title, text, profile.id, Routes.HOME)) app.prefs.markNotified(key)
        }

        /**
         * A word on the day she would go: the due date if it is still ahead, otherwise today. A public holiday
         * (the centre may be closed) or Ramadan (opening hours often change). Empty on an ordinary day.
         */
        private fun vaccineDayNote(res: Resources, today: LocalDate, daysUntilDue: Int): String {
            val day = if (daysUntilDue > 0) today.plusDays(daysUntilDue.toLong()) else today
            val holiday = LibyaCalendar.holiday(day)
            return when {
                holiday != null -> " " + res.getString(
                    if (daysUntilDue > 0) R.string.notify_vaccine_holiday_due else R.string.notify_vaccine_holiday_today,
                    res.getString(holidayName(holiday)),
                )
                LibyaCalendar.isRamadan(day) -> " " + res.getString(R.string.notify_vaccine_ramadan)
                else -> ""
            }
        }

        private fun holidayName(holiday: Holiday): Int = when (holiday) {
            Holiday.EID_FITR -> R.string.holiday_eid_fitr
            Holiday.ARAFAH -> R.string.holiday_arafah
            Holiday.EID_ADHA -> R.string.holiday_eid_adha
            Holiday.MAWLID -> R.string.holiday_mawlid
            Holiday.HIJRI_NEW_YEAR -> R.string.holiday_hijri_new_year
            Holiday.REVOLUTION -> R.string.holiday_revolution
            Holiday.LABOUR -> R.string.holiday_labour
            Holiday.MARTYRS -> R.string.holiday_martyrs
            Holiday.LIBERATION -> R.string.holiday_liberation
            Holiday.INDEPENDENCE -> R.string.holiday_independence
        }

        private fun checkProfile(context: Context, res: Resources, profile: Profile, today: LocalDate) {
            val app = context.app
            val prefs = app.prefs

            if (profile.isPregnancy && profile.dueDate != null) {
                val week = PregnancyMath.gestationalAge(profile.dueDate, today).weeks
                val last = prefs.lastNotifiedWeek(profile.id)
                if (last in 0 until week && week in 4..42) {
                    val title = res.getString(R.string.notify_new_week_title, week.toString())
                    val text = res.getString(R.string.notify_new_week_text)
                    Notifications.post(context, "week:${profile.id}:$week", title, text, profile.id, Routes.PREGNANCY)
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
                    } + vaccineDayNote(res, today, d)
                    // Only mark as sent if it was really shown, so a reminder is not lost
                    // when notifications are blocked and later allowed.
                    if (Notifications.post(context, key, title, text, profile.id, Routes.VACCINES)) prefs.markNotified(key)
                }
            }
        }
    }
}

/** Sets the daily alarm again after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.schedule(context)
    }
}
