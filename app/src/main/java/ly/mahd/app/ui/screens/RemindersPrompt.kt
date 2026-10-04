package ly.mahd.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.Profile
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.rememberCanPostNotifications

/** True while Home should ask the mother to turn reminders on (see [RemindersPrompt]). */
@Composable
fun shouldAskForReminders(app: MahdApp): Boolean {
    val reminders by app.prefs.remindersEnabled.collectAsState()
    val dismissed by app.prefs.reminderPromptDismissed.collectAsState()
    val canPost = rememberCanPostNotifications()
    return reminders && !dismissed && !canPost
}

/**
 * Explains the reminders first, then asks Android for permission only when the mother taps the button:
 * Android shows its question at most twice and then never again, so it must not come as a surprise.
 * If it was already asked (and refused), the button opens the phone's notification settings instead.
 * "Not now" hides the card; Settings still has the switch and a link to the phone's settings.
 */
@Composable
fun RemindersPrompt(app: MahdApp, profile: Profile) {
    val context = LocalContext.current
    // The card disappears by itself when the app comes back with notifications allowed (LocalResumeCount).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AppCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("🔔  " + stringResource(R.string.reminders_prompt_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (profile.isPregnancy) R.string.reminders_prompt_pregnancy else R.string.reminders_prompt_child),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !app.prefs.notificationPermissionAsked) {
                    app.prefs.notificationPermissionAsked = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Notifications.openSettings(context)
                }
            }) { Text(stringResource(R.string.reminders_prompt_allow)) }
            TextButton(onClick = { app.prefs.dismissReminderPrompt() }) {
                Text(stringResource(R.string.reminders_prompt_later))
            }
        }
    }
}
