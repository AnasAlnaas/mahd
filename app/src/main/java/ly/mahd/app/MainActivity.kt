package ly.mahd.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import ly.mahd.app.data.ThemeMode
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.reminders.ReminderReceiver
import ly.mahd.app.reminders.ReminderScheduler
import ly.mahd.app.ui.MahdRoot
import ly.mahd.app.ui.OpenRequest
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.LocalResumeCount
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.theme.MahdTheme
import ly.mahd.app.util.AppLocale
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Refreshed in onResume, so "today" is right even if the app stayed open overnight. */
    private val today = mutableStateOf(LocalDate.now())

    /** Counts returns to the screen (see LocalResumeCount). */
    private val resumeCount = mutableIntStateOf(0)

    /** Set when the app is opened from a reminder; MahdRoot shows that profile and tab, then clears it. */
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after a screen rotation the same intent must not move the mother again.
        if (savedInstanceState == null) openFromReminder(intent)
        // The window shows for a moment before Compose draws; paint it in the chosen mode, not the phone's.
        when (app.prefs.themeMode.value) {
            ThemeMode.LIGHT -> window.setBackgroundDrawableResource(R.color.window_background_light)
            ThemeMode.DARK -> window.setBackgroundDrawableResource(R.color.window_background_dark)
            ThemeMode.SYSTEM -> Unit
        }
        setContent {
            val mode by app.prefs.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The status and navigation bar icons follow the app's choice, not the phone's, so they stay readable.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                )
                onDispose {}
            }
            MahdTheme(dark = dark) {
                CompositionLocalProvider(LocalToday provides today.value, LocalResumeCount provides resumeCount.intValue) {
                    MahdRoot(app, openRequest.value, onOpenHandled = { openRequest.value = null })
                }
            }
        }
    }

    /** A reminder was tapped while the app was already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromReminder(intent)
    }

    override fun onResume() {
        super.onResume()
        today.value = LocalDate.now()
        resumeCount.intValue++
        ReminderScheduler.ensureScheduled(this)
        // If today's reminder check has not run yet (late or missing alarm), run it now.
        ReminderReceiver.runIfDue(this)
    }

    private companion object {
        // The navigation bar scrims enableEdgeToEdge() uses by default (needed for 3-button navigation).
        val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }

    private fun openFromReminder(intent: Intent?) {
        // Reopening the app from the recent-apps list repeats the old intent; that is not a new tap.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val profileId = intent.getLongExtra(Notifications.EXTRA_PROFILE_ID, -1L)
        val route = intent.getStringExtra(Notifications.EXTRA_ROUTE)
        if (profileId >= 0 && route != null && route in Routes.TABS) openRequest.value = OpenRequest(profileId, route)
    }
}
