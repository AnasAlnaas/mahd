package ly.mahd.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import ly.mahd.app.reminders.ReminderScheduler
import ly.mahd.app.ui.MahdRoot
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.theme.MahdTheme
import ly.mahd.app.util.AppLocale
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Refreshed in onResume, so "today" is right even if the app stayed open overnight. */
    private val today = mutableStateOf(LocalDate.now())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MahdTheme {
                CompositionLocalProvider(LocalToday provides today.value) {
                    MahdRoot(app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        today.value = LocalDate.now()
        ReminderScheduler.schedule(this)
    }
}
