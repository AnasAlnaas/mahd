package ly.mahd.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    var notificationPermissionAsked: Boolean
        get() = sp.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = sp.edit { putBoolean(KEY_PERMISSION_ASKED, value) }

    // ----- Bookkeeping so each reminder is sent only once -----

    fun wasNotified(key: String): Boolean = sp.getBoolean("$NOTIFIED_PREFIX$key", false)

    fun markNotified(key: String) = sp.edit { putBoolean("$NOTIFIED_PREFIX$key", true) }

    fun lastNotifiedWeek(profileId: Long): Int = sp.getInt("$WEEK_PREFIX$profileId", -1)

    fun setLastNotifiedWeek(profileId: Long, week: Int) = sp.edit { putInt("$WEEK_PREFIX$profileId", week) }

    /** After a restore, profile ids may point at different children, so old "already sent" marks are void. */
    fun resetReminderBookkeeping() = sp.edit {
        sp.all.keys
            .filter { it.startsWith(NOTIFIED_PREFIX) || it.startsWith(WEEK_PREFIX) }
            .forEach { remove(it) }
    }

    private companion object {
        const val KEY_SELECTED = "selected_profile"
        const val KEY_DISCLAIMER = "disclaimer_accepted"
        const val KEY_REMINDERS = "reminders_enabled"
        const val KEY_PERMISSION_ASKED = "notification_permission_asked"
        const val NOTIFIED_PREFIX = "notified:"
        const val WEEK_PREFIX = "pregnancy_week:"
    }
}
