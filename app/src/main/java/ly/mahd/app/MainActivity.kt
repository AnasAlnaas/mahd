package ly.mahd.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.reminders.ReminderReceiver
import ly.mahd.app.reminders.ReminderScheduler
import ly.mahd.app.ui.MahdRoot
import ly.mahd.app.ui.OpenRequest
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.theme.MahdTheme
import ly.mahd.app.util.AppLocale
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Refreshed in onResume, so "today" is right even if the app stayed open overnight. */
    private val today = mutableStateOf(LocalDate.now())

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
        setContent {
            MahdTheme {
                CompositionLocalProvider(LocalToday provides today.value) {
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
        ReminderScheduler.ensureScheduled(this)
        // If today's reminder check has not run yet (late or missing alarm), run it now.
        ReminderReceiver.runIfDue(this)
    }

    private fun openFromReminder(intent: Intent?) {
        // Reopening the app from the recent-apps list repeats the old intent; that is not a new tap.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val profileId = intent.getLongExtra(Notifications.EXTRA_PROFILE_ID, -1L)
        val route = intent.getStringExtra(Notifications.EXTRA_ROUTE)
        if (profileId >= 0 && route != null && route in Routes.TABS) openRequest.value = OpenRequest(profileId, route)
    }
}
