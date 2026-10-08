package ly.mahd.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.mahd.app.BuildConfig
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.BackupFile
import ly.mahd.app.data.Profile
import ly.mahd.app.data.Repository
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SectionTitle
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.rememberCanPostNotifications
import ly.mahd.app.ui.profileEmoji
import ly.mahd.app.ui.profileName
import ly.mahd.app.util.Format
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen(app: MahdApp, nav: Navigator, profiles: List<Profile>) {
    val context = LocalContext.current
    val today = LocalToday.current
    val scope = rememberCoroutineScope()
    val reminders by app.prefs.remindersEnabled.collectAsState()
    val canPost = rememberCanPostNotifications()
    var pendingRestore by remember { mutableStateOf<Repository.Snapshot?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = BackupFile.encode(app.repository.snapshot(), today)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("no stream")
                }
            }.isSuccess
            toast(context, if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }

    val pickBackup = rememberBackupPicker { snapshot -> pendingRestore = snapshot }

    SubScreen(title = stringResource(R.string.settings_title), onBack = nav::back) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ----- Profiles -----
            item { SectionTitle(stringResource(R.string.settings_profiles)) }
            profiles.forEach { p ->
                item(key = "profile-${p.id}") {
                    AppCard(onClick = { nav.push(Routes.edit(p.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profileEmoji(p), style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(profileName(p), style = MaterialTheme.typography.titleMedium)
                                val sub = when {
                                    p.isPregnancy && p.dueDate != null -> stringResource(R.string.due_on, Format.date(p.dueDate))
                                    p.birthDate != null -> stringResource(R.string.born_on, Format.date(p.birthDate))
                                    else -> ""
                                }
                                Text(sub, style = MaterialTheme.typography.bodyMedium)
                            }
                            AppIcon(R.drawable.ic_edit, stringResource(R.string.action_edit))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.push(Routes.ADD_PREGNANCY) }) { Text(stringResource(R.string.add_pregnancy)) }
                    OutlinedButton(onClick = { nav.push(Routes.ADD_CHILD) }) { Text(stringResource(R.string.add_child)) }
                }
            }

            // ----- Reminders -----
            item { SectionTitle(stringResource(R.string.settings_reminders)) }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reminders_switch), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = reminders, onCheckedChange = { app.prefs.setRemindersEnabled(it) })
                    }
                    Text(
                        stringResource(R.string.reminders_battery_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reminders && !canPost) {
                        Text(
                            stringResource(R.string.notifications_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { Notifications.openSettings(context) }) {
                            Text(stringResource(R.string.open_notification_settings))
                        }
                    }
                }
            }

            // ----- Backup -----
            item { SectionTitle(stringResource(R.string.settings_backup)) }
            item {
                AppCard {
                    Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.backup_photos_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.backup_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { saveLauncher.launch(backupFileName(today)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_save)) }
                    OutlinedButton(
                        onClick = { scope.launch { runOrToast(context, R.string.share_failed) { shareBackup(context, app, today) } } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_share)) }
                    OutlinedButton(
                        onClick = pickBackup,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_restore)) }
                }
            }

            // ----- About -----
            item { SectionTitle(stringResource(R.string.settings_about)) }
            item {
                AppCard(onClick = { nav.push(Routes.ABOUT) }) {
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    pendingRestore?.let { snapshot ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(R.string.restore_confirm_text, snapshot.profiles.size.toString()),
            confirmLabel = stringResource(R.string.restore_confirm_button),
            destructive = true,
            onConfirm = {
                scope.launch {
                    val ok = applyRestore(app, snapshot)
                    if (ok) nav.selectTab(Routes.HOME)
                    toast(context, if (ok) R.string.restore_done else R.string.restore_failed)
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
}

private fun toast(context: Context, message: Int) = showToast(context, message)

/** The name the mother sees for her backup file. (Inside, the file still says "app": "mahd" — see BackupFile.) */
private fun backupFileName(today: LocalDate) = "atwar-backup-$today.json"

/** Writes the backup to the app's cache and opens Android's share sheet (WhatsApp, Drive, email…). */
private suspend fun shareBackup(context: Context, app: MahdApp, today: LocalDate) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, backupFileName(today)).apply {
            writeText(BackupFile.encode(app.repository.snapshot(), today), Charsets.UTF_8)
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share)))
}

@Composable
fun AboutScreen(app: MahdApp, onBack: () -> Unit) {
    val content = app.content
    SubScreen(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("🌙  " + stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.about_mission), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.about_name_meaning), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                AppCard {
                    Text("🔒  " + stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                AppCard {
                    Text("📚  " + stringResource(R.string.sources_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.sources_text), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                AppCard {
                    Text("🩺  " + stringResource(R.string.review_title), style = MaterialTheme.typography.titleMedium)
                    val reviewed = content.pregnancy.reviewed && content.milestones.reviewed && content.vaccines.reviewed
                    Text(
                        stringResource(if (reviewed) R.string.review_done else R.string.review_pending),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
