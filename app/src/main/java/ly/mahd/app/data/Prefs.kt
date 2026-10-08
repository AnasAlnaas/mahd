package ly.mahd.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Small settings, kept in Android's built-in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mahd_prefs", Context.MODE_PRIVATE)

    private val _selectedProfileId = MutableStateFlow(sp.getLong(KEY_SELECTED, -1L).takeIf { it >= 0 })
    val selectedProfileId: StateFlow<Long?> = _selectedProfileId.asStateFlow()

    fun selectProfile(id: Long?) {
        sp.edit { if (id == null) remove(KEY_SELECTED) else putLong(KEY_SELECTED, id) }
        _selectedProfileId.value = id
    }

    private val _disclaimerAccepted = MutableStateFlow(sp.getBoolean(KEY_DISCLAIMER, false))
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted.asStateFlow()

    fun acceptDisclaimer() {
        sp.edit { putBoolean(KEY_DISCLAIMER, true) }
        _disclaimerAccepted.value = true
    }

    private val _remindersEnabled = MutableStateFlow(sp.getBoolean(KEY_REMINDERS, true))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    fun setRemindersEnabled(enabled: Boolean) {
        sp.edit { putBoolean(KEY_REMINDERS, enabled) }
        _remindersEnabled.value = enabled
    }

    /** The twice-a-week notification with a verse or hadith (part of the reminders; off when they are off). */
    private val _adviceNotifications = MutableStateFlow(sp.getBoolean(KEY_ADVICE_NOTIFICATIONS, true))
    val adviceNotifications: StateFlow<Boolean> = _adviceNotifications.asStateFlow()

    fun setAdviceNotifications(enabled: Boolean) {
        sp.edit { putBoolean(KEY_ADVICE_NOTIFICATIONS, enabled) }
        _adviceNotifications.value = enabled
    }

    /** Light or dark colours: follow the phone (the default), or always one of them. */
    private val _themeMode = MutableStateFlow(ThemeMode.fromCode(sp.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        sp.edit { putString(KEY_THEME, mode.code) }
        _themeMode.value = mode
    }

    var notificationPermissionAsked: Boolean
        get() = sp.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = sp.edit { putBoolean(KEY_PERMISSION_ASKED, value) }

    /** The mother tapped "not now" on the Home card that asks to turn reminders on. */
    private val _reminderPromptDismissed = MutableStateFlow(sp.getBoolean(KEY_REMINDER_PROMPT_DISMISSED, false))
    val reminderPromptDismissed: StateFlow<Boolean> = _reminderPromptDismissed.asStateFlow()

    fun dismissReminderPrompt() {
        sp.edit { putBoolean(KEY_REMINDER_PROMPT_DISMISSED, true) }
        _reminderPromptDismissed.value = true
    }

    // ----- Bookkeeping so each reminder is sent only once -----

    fun wasNotified(key: String): Boolean = sp.getBoolean("$NOTIFIED_PREFIX$key", false)

    fun markNotified(key: String) = sp.edit { putBoolean("$NOTIFIED_PREFIX$key", true) }

    fun lastNotifiedWeek(profileId: Long): Int = sp.getInt("$WEEK_PREFIX$profileId", -1)

    fun setLastNotifiedWeek(profileId: Long, week: Int) = sp.edit { putInt("$WEEK_PREFIX$profileId", week) }

    /** The day the daily reminder check last ran, so the app can run it itself if the alarm did not. */
    var lastCheckDate: LocalDate?
        get() = sp.getString(KEY_LAST_CHECK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = sp.edit { if (value == null) remove(KEY_LAST_CHECK) else putString(KEY_LAST_CHECK, value.toString()) }

    /**
     * After a restore, profile ids may point at different children, so old "already sent" marks are void,
     * and the daily check should run again for the restored profiles.
     */
    fun resetReminderBookkeeping() = sp.edit {
        sp.all.keys
            .filter { it.startsWith(NOTIFIED_PREFIX) || it.startsWith(WEEK_PREFIX) || it == KEY_LAST_CHECK }
            .forEach { remove(it) }
    }

    private companion object {
        const val KEY_SELECTED = "selected_profile"
        const val KEY_DISCLAIMER = "disclaimer_accepted"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_ADVICE_NOTIFICATIONS = "advice_notifications"
        const val KEY_THEME = "theme_mode"
        const val KEY_PERMISSION_ASKED = "notification_permission_asked"
        const val KEY_REMINDER_PROMPT_DISMISSED = "reminder_prompt_dismissed"
        const val KEY_LAST_CHECK = "last_reminder_check"
        const val NOTIFIED_PREFIX = "notified:"
        const val WEEK_PREFIX = "pregnancy_week:"
    }
}

enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}
