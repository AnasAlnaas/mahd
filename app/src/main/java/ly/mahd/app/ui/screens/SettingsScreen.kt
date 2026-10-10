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

import androidx.compose.material3.Switch
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import ly.mahd.app.data.Parent
import ly.mahd.app.data.ThemeMode
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.GroupItem
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SectionTitle
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.rememberCanPostNotifications
import ly.mahd.app.ui.ProfileAvatar
import ly.mahd.app.ui.profileName
import ly.mahd.app.ui.theme.LocalSurfaces
import ly.mahd.app.util.Format
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen(app: MahdApp, nav: Navigator, profiles: List<Profile>) {
    val context = LocalContext.current
    val today = LocalToday.current
    val scope = rememberCoroutineScope()
    val reminders by app.prefs.remindersEnabled.collectAsState()
    val advice by app.prefs.adviceNotifications.collectAsState()
    val themeMode by app.prefs.themeMode.collectAsState()
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ----- Profiles -----
            item { SectionTitle(stringResource(R.string.settings_profiles)) }
            item {
                ListGroup {
                    profiles.forEach { p ->
                        GroupItem(
                            headline = profileName(p),
                            supporting = when {
                                p.isPregnancy && p.dueDate != null -> stringResource(R.string.due_on, Format.date(p.dueDate))
                                p.birthDate != null -> stringResource(R.string.born_on, Format.date(p.birthDate))
                                else -> null
                            },
                            leading = { ProfileAvatar(p, size = 44.dp) },
                            trailing = { AppIcon(R.drawable.ic_edit, stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { nav.push(Routes.edit(p.id)) },
                        )
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(onClick = { nav.push(Routes.ADD_PREGNANCY) }, tonal = true, icon = R.drawable.ic_add, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.add_pregnancy), maxLines = 1)
                    }
                    ExpressiveButton(onClick = { nav.push(Routes.ADD_CHILD) }, tonal = true, icon = R.drawable.ic_add, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.add_child), maxLines = 1)
                    }
                }
            }

            // ----- Reminders -----
            item { SectionTitle(stringResource(R.string.settings_reminders), Modifier.padding(top = 12.dp)) }
            item {
                ListGroup {
                    GroupRow(onClick = { app.prefs.setRemindersEnabled(!reminders) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.reminders_switch), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = reminders, onCheckedChange = null) // the whole row is the button
                        }
                        Text(
                            stringResource(R.string.reminders_battery_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Rides on the reminders' daily check, so it is off (and greyed out) while they are off.
                    GroupRow(onClick = if (reminders) ({ app.prefs.setAdviceNotifications(!advice) }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.advice_switch),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (reminders) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(checked = reminders && advice, onCheckedChange = null, enabled = reminders)
                        }
                        Text(
                            stringResource(R.string.advice_switch_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reminders && !canPost) {
                        GroupItem(
                            headline = stringResource(R.string.open_notification_settings),
                            supporting = stringResource(R.string.notifications_blocked),
                            supportingColor = MaterialTheme.colorScheme.error,
                            onClick = { Notifications.openSettings(context) },
                        )
                    }
                }
            }

            // ----- Appearance -----
            item { SectionTitle(stringResource(R.string.settings_appearance), Modifier.padding(top = 12.dp)) }
            item {
                val modes = ThemeMode.entries
                ListGroup {
                    GroupRow(spacing = 12.dp) {
                        ConnectedChoice(
                            options = listOf(
                                stringResource(R.string.theme_system),
                                stringResource(R.string.theme_light),
                                stringResource(R.string.theme_dark),
                            ),
                            selected = modes.indexOf(themeMode),
                            onSelect = { app.prefs.setThemeMode(modes[it]) },
                            unselectedColor = LocalSurfaces.current.inset,
                        )
                        Text(
                            stringResource(R.string.theme_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ----- Who uses the app -----
            item { SectionTitle(stringResource(R.string.settings_parent), Modifier.padding(top = 12.dp)) }
            item {
                val parents = listOf(Parent.MOTHER, Parent.FATHER)
                ListGroup {
                    GroupRow(spacing = 12.dp) {
                        ConnectedChoice(
                            options = listOf(stringResource(R.string.role_mother_short), stringResource(R.string.role_father_short)),
                            selected = parents.indexOf(LocalParent.current),
                            // The screen restarts in the new voice and colours (MainActivity).
                            onSelect = { app.prefs.setParent(parents[it]) },
                            unselectedColor = LocalSurfaces.current.inset,
                        )
                        Text(
                            stringResource(R.string.settings_parent_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ----- Backup -----
            item { SectionTitle(stringResource(R.string.settings_backup), Modifier.padding(top = 12.dp)) }
            item {
                ListGroup {
                    GroupRow(spacing = 6.dp) {
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
                    }
                    GroupItem(headline = stringResource(R.string.backup_save), onClick = { saveLauncher.launch(backupFileName(today)) })
                    GroupItem(
                        headline = stringResource(R.string.backup_share),
                        onClick = { scope.launch { runOrToast(context, R.string.share_failed) { shareBackup(context, app, today) } } },
                    )
                    GroupItem(headline = stringResource(R.string.backup_restore), onClick = pickBackup)
                }
            }

            // ----- About -----
            item { SectionTitle(stringResource(R.string.settings_about), Modifier.padding(top = 12.dp)) }
            item {
                ListGroup {
                    GroupItem(
                        headline = stringResource(R.string.about_title),
                        supporting = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                        onClick = { nav.push(Routes.ABOUT) },
                    )
                }
            }
            item { Disclaimer(Modifier.padding(top = 8.dp)) }
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

/** "This app does not replace the doctor": a quiet block at the end of Settings and About. */
@Composable
private fun Disclaimer(modifier: Modifier = Modifier) {
    AppCard(modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AboutScreen(app: MahdApp, onBack: () -> Unit) {
    val content = app.content
    SubScreen(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The launcher icon's three moons: the stages the name means.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(0.25f, 0.5f, 1f).forEach { MoonPhase(it, size = 28.dp) }
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.about_mission), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.about_name_meaning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val reviewed = content.pregnancy.reviewed && content.milestones.reviewed && content.vaccines.reviewed
                ListGroup {
                    AboutSection(stringResource(R.string.privacy_title), stringResource(R.string.privacy_text))
                    AboutSection(stringResource(R.string.sources_title), stringResource(R.string.sources_text))
                    AboutSection(
                        stringResource(R.string.review_title),
                        stringResource(if (reviewed) R.string.review_done else R.string.review_pending),
                    )
                }
            }
            item { Disclaimer() }
        }
    }
}

@Composable
private fun AboutSection(title: String, text: String) {
    GroupRow(spacing = 4.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
