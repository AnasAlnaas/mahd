package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.AppFilterChip
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.DateField
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.util.Format
import java.time.LocalDate

private val dateSaver = Saver<LocalDate?, String>(
    save = { it?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else LocalDate.parse(it) },
)

/** [limit], or [saved] when that is earlier, so a calendar can still show a date saved long ago. */
private fun earliest(limit: LocalDate, saved: LocalDate?): LocalDate =
    if (saved != null && saved.isBefore(limit)) saved else limit

@Composable
private fun FormColumn(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(padding)
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Pregnancy
// ---------------------------------------------------------------------------------------------

@Composable
fun PregnancyFormScreen(app: MahdApp, existing: Profile?, onClose: () -> Unit) {
    val today = LocalToday.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var useLastPeriod by rememberSaveable { mutableStateOf(existing == null) }
    var lastPeriod by rememberSaveable(stateSaver = dateSaver) {
        mutableStateOf(existing?.dueDate?.let(PregnancyMath::lastPeriodFromDueDate))
    }
    var dueDate by rememberSaveable(stateSaver = dateSaver) { mutableStateOf(existing?.dueDate) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val computedDue: LocalDate? = if (useLastPeriod) lastPeriod?.let(PregnancyMath::dueDateFromLastPeriod) else dueDate
    val plausible = computedDue != null && PregnancyMath.isPlausibleDueDate(computedDue, today)
    // A saved pregnancy may keep its due date even when it is long past (to rename it, for example);
    // only a newly chosen date has to fit an ongoing pregnancy.
    val keepsPastDate = computedDue != null && computedDue == existing?.dueDate && computedDue.isBefore(today)
    val valid = plausible || keepsPastDate
    val res = LocalContext.current.resources

    SubScreen(
        title = stringResource(if (existing == null) R.string.add_pregnancy else R.string.edit_pregnancy),
        onBack = onClose,
    ) { padding ->
        FormColumn(padding) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.pregnancy_name_label)) },
                placeholder = { Text(stringResource(R.string.default_pregnancy_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.pregnancy_date_question), style = MaterialTheme.typography.titleMedium)
            ConnectedChoice(
                options = listOf(stringResource(R.string.pregnancy_by_last_period), stringResource(R.string.pregnancy_by_due_date)),
                selected = if (useLastPeriod) 0 else 1,
                onSelect = { useLastPeriod = it == 0 },
            )

            // The calendars also reach back to the saved date when it is older than the usual limit.
            val savedDue = existing?.dueDate
            if (useLastPeriod) {
                DateField(
                    label = stringResource(R.string.last_period_label),
                    date = lastPeriod,
                    min = earliest(today.minusWeeks(44), savedDue?.let(PregnancyMath::lastPeriodFromDueDate)),
                    max = today,
                    onDate = { lastPeriod = it },
                )
            } else {
                DateField(
                    label = stringResource(R.string.due_date_label),
                    date = dueDate,
                    min = earliest(today.minusWeeks(4), savedDue),
                    max = today.plusWeeks(42),
                    onDate = { dueDate = it },
                )
            }

            if (computedDue != null) {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    if (plausible) {
                        val ga = PregnancyMath.gestationalAge(computedDue, today)
                        Text(
                            stringResource(R.string.computed_due_date, Format.date(computedDue)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.computed_current_week, Format.gestationalAge(res, ga.weeks, ga.days)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else if (keepsPastDate) {
                        Text(
                            stringResource(R.string.computed_due_date, Format.date(computedDue)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.past_due, Format.days(res, -PregnancyMath.daysUntilDue(computedDue, today))),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Text(stringResource(R.string.due_date_not_plausible), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text(
                stringResource(R.string.due_date_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExpressiveButton(
                onClick = {
                    val due = computedDue ?: return@ExpressiveButton
                    scope.launch {
                        if (existing == null) {
                            val id = app.repository.addProfile(
                                Profile(0, ProfileKind.PREGNANCY, name.trim(), null, null, due, null),
                            )
                            app.prefs.selectProfile(id)
                        } else {
                            app.repository.updateProfile(existing.copy(name = name.trim(), dueDate = due))
                        }
                        onClose()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }

            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.delete_profile), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_profile_title),
            text = stringResource(R.string.delete_profile_text),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                scope.launch {
                    app.deleteProfile(existing.id)
                    app.prefs.selectProfile(null)
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Child
// ---------------------------------------------------------------------------------------------

/** Weeks offered for "born early": 24 to 36 completed weeks. */
private val PRETERM_WEEKS = (24..36).toList()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildFormScreen(app: MahdApp, existing: Profile?, onClose: () -> Unit) {
    val today = LocalToday.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var sex by rememberSaveable { mutableStateOf(existing?.sex?.code) }
    var birth by rememberSaveable(stateSaver = dateSaver) { mutableStateOf(existing?.birthDate) }
    var premature by rememberSaveable { mutableStateOf(existing?.isPremature == true) }
    var weeksAtBirth by rememberSaveable { mutableStateOf(existing?.weeksAtBirth?.takeIf { it < 37 } ?: 34) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val valid = name.isNotBlank() && sex != null && birth != null

    SubScreen(
        title = stringResource(if (existing == null) R.string.add_child else R.string.edit_child),
        onBack = onClose,
    ) { padding ->
        FormColumn(padding) {
            ChildBasicsFields(
                name = name, onName = { name = it },
                sex = sex, onSex = { sex = it },
            )
            DateField(
                label = stringResource(R.string.birth_date_label),
                date = birth,
                min = today.minusYears(6),
                max = today,
                onDate = { birth = it },
            )
            PrematureFields(
                premature = premature, onPremature = { premature = it },
                weeks = weeksAtBirth, onWeeks = { weeksAtBirth = it },
            )

            ExpressiveButton(
                onClick = {
                    val b = birth ?: return@ExpressiveButton
                    val s = Sex.fromCode(sex) ?: return@ExpressiveButton
                    val weeks = if (premature) weeksAtBirth else existing?.weeksAtBirth?.takeIf { it >= 37 }
                    scope.launch {
                        if (existing == null) {
                            val id = app.repository.addProfile(
                                Profile(0, ProfileKind.CHILD, name.trim(), s, b, null, weeks),
                            )
                            app.prefs.selectProfile(id)
                        } else {
                            app.repository.updateProfile(
                                existing.copy(kind = ProfileKind.CHILD, name = name.trim(), sex = s, birthDate = b, weeksAtBirth = weeks),
                            )
                        }
                        onClose()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }

            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.delete_profile), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_profile_title),
            text = stringResource(R.string.delete_profile_text),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                scope.launch {
                    app.deleteProfile(existing.id)
                    app.prefs.selectProfile(null)
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ChildBasicsFields(name: String, onName: (String) -> Unit, sex: String?, onSex: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(40)) },
        label = { Text(stringResource(R.string.child_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.sex_label), style = MaterialTheme.typography.titleMedium)
    val sexes = listOf(Sex.BOY.code, Sex.GIRL.code)
    ConnectedChoice(
        options = listOf(stringResource(R.string.sex_boy), stringResource(R.string.sex_girl)),
        selected = sexes.indexOf(sex).takeIf { it >= 0 },
        onSelect = { onSex(sexes[it]) },
    )
    Text(
        stringResource(R.string.sex_why),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrematureFields(premature: Boolean, onPremature: (Boolean) -> Unit, weeks: Int, onWeeks: (Int) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.premature_question), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.premature_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = premature, onCheckedChange = onPremature)
        }
        if (premature) {
            Text(stringResource(R.string.premature_weeks_label), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRETERM_WEEKS.forEach { w ->
                    AppFilterChip(selected = weeks == w, onClick = { onWeeks(w) }, label = w.toString())
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// "My baby is born" — turns a pregnancy into a child profile, keeping its id and due date.
// ---------------------------------------------------------------------------------------------

@Composable
fun BabyBornScreen(app: MahdApp, pregnancy: Profile, onDone: () -> Unit, onClose: () -> Unit) {
    val today = LocalToday.current
    val scope = rememberCoroutineScope()
    val due = pregnancy.dueDate ?: today
    var name by rememberSaveable { mutableStateOf(pregnancy.name) }
    var sex by rememberSaveable { mutableStateOf<String?>(null) }
    var birth by rememberSaveable(stateSaver = dateSaver) { mutableStateOf<LocalDate?>(today) }

    val weeksAtBirth = birth?.let { PregnancyMath.gestationalAge(due, it).weeks }
    val valid = name.isNotBlank() && sex != null && birth != null

    SubScreen(title = stringResource(R.string.baby_born_title), onBack = onClose) { padding ->
        FormColumn(padding) {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                MoonPhase(1f, size = 40.dp)

                Text(stringResource(R.string.baby_born_congrats), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.baby_born_text), style = MaterialTheme.typography.bodyLarge)
            }
            ChildBasicsFields(name = name, onName = { name = it }, sex = sex, onSex = { sex = it })
            DateField(
                label = stringResource(R.string.birth_date_label),
                date = birth,
                min = due.minusWeeks(18),
                max = today,
                onDate = { birth = it },
            )
            if (weeksAtBirth != null) {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(
                        stringResource(R.string.baby_born_weeks, weeksAtBirth.toString()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (weeksAtBirth < 37) {
                        Text(stringResource(R.string.baby_born_premature_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            ExpressiveButton(
                onClick = {
                    val b = birth ?: return@ExpressiveButton
                    val s = Sex.fromCode(sex) ?: return@ExpressiveButton
                    scope.launch {
                        app.repository.updateProfile(
                            pregnancy.copy(
                                kind = ProfileKind.CHILD,
                                name = name.trim(),
                                sex = s,
                                birthDate = b,
                                weeksAtBirth = weeksAtBirth,
                            ),
                        )
                        onDone()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.action_save)) }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
