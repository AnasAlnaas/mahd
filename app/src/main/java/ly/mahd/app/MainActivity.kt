package ly.mahd.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import ly.mahd.app.data.Parent
import ly.mahd.app.data.ThemeMode
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.reminders.ReminderReceiver
import ly.mahd.app.reminders.ReminderScheduler
import ly.mahd.app.ui.MahdRoot
import ly.mahd.app.ui.OpenRequest
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.LocalParent
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
        // Who this screen speaks to; the text was already chosen for them when the activity started (AppLocale).
        val startedAs = app.prefs.parent.value
        // The window shows for a moment before Compose draws; paint it in the chosen mode and the parent's colours.
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val startDark = when (app.prefs.themeMode.value) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        window.setBackgroundDrawableResource(
            when {
                startedAs == Parent.FATHER -> if (startDark) R.color.window_background_father_dark else R.color.window_background_father_light
                else -> if (startDark) R.color.window_background_dark else R.color.window_background_light
            },
        )
        setContent {
            val mode by app.prefs.themeMode.collectAsState()
            val parent by app.prefs.parent.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Choosing mother or father changes every text: start the screen again so all of it is in the new voice.
            LaunchedEffect(parent) {
                if (parent != startedAs) recreate()
            }
            // The status and navigation bar icons follow the app's choice, not the phone's, so they stay readable.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                )
                onDispose {}
            }
            MahdTheme(dark = dark, father = startedAs == Parent.FATHER) {
                CompositionLocalProvider(
                    LocalToday provides today.value,
                    LocalResumeCount provides resumeCount.intValue,
                    LocalParent provides (startedAs ?: Parent.MOTHER),
                ) {
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
