package ly.mahd.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.VaccineDose
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.VaccinePlanner
import ly.mahd.app.logic.VaccinePlanner.Status
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.DatePickerPopup
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.Pill
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.util.Format
import java.time.LocalDate

/** The Libyan national schedule for one child: what is done, what is next, what is late. */
@Composable
fun VaccinesScreen(app: MahdApp, profile: Profile) {
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val schedule = app.content.vaccines
    val doses by remember(profile.id) { app.repository.doses(profile.id) }.collectAsState(initial = emptyMap())
    val skipped by remember(profile.id) { app.repository.skippedDoses(profile.id) }.collectAsState(initial = emptySet())
    val plans = VaccinePlanner.plan(schedule, birth, doses, today, skipped)
    val scope = rememberCoroutineScope()

    // Which doses the date picker is for (one dose, or all remaining doses of a visit).
    var pickFor by remember { mutableStateOf<Pair<List<String>, LocalDate>?>(null) }
    var editDose by remember { mutableStateOf<Pair<VaccineDose, LocalDate>?>(null) }
    var skippedDose by remember { mutableStateOf<VaccineDose?>(null) }
    var confirmSkip by remember { mutableStateOf<List<String>?>(null) }
    var confirmUpTo by remember { mutableStateOf<Pair<String, Map<String, LocalDate>>?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.vaccines_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.vaccines_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!schedule.reviewed) item { DraftNotice(stringResource(R.string.vaccines_draft_notice)) }
        itemsIndexed(plans, key = { _, plan -> plan.visit.id }) { index, plan ->
            val hasEarlierGap = plans.take(index).any { !it.isComplete }
            VisitCard(
                plan = plan,
                onMarkAll = {
                    pickFor = plan.remainingDoses.map { it.id } to defaultGivenDate(plan.dueDate, today)
                },
                onSkipRemaining = { confirmSkip = plan.remainingDoses.map { it.id } },
                onMarkUpTo = if (hasEarlierGap && plan.status != Status.UPCOMING && plan.status != Status.DUE_SOON) {
                    // Everything not yet recorded up to and including this visit, dated on each visit's due date.
                    ({
                        val upTo = plans.take(index + 1).flatMap { p ->
                            val date = defaultGivenDate(p.dueDate, today)
                            p.remainingDoses.map { it.id to date }
                        }.toMap()
                        confirmUpTo = plan.visit.title to upTo
                    })
                } else {
                    null
                },
                onDoseClick = { dose ->
                    val given = plan.given[dose.id]
                    when {
                        given != null -> editDose = dose to given
                        dose.id in plan.skipped -> skippedDose = dose
                        else -> pickFor = listOf(dose.id) to defaultGivenDate(plan.dueDate, today)
                    }
                },
            )
        }
        item {
            Text(
                stringResource(R.string.vaccines_source, schedule.source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pickFor?.let { (ids, initial) ->
        DatePickerPopup(
            initial = initial,
            min = birth,
            max = today,
            onDismiss = { pickFor = null },
            onPick = { date -> scope.launch { app.repository.setDosesGiven(profile.id, ids, date) } },
        )
    }

    editDose?.let { (dose, given) ->
        var changeDate by remember { mutableStateOf(false) }
        if (changeDate) {
            DatePickerPopup(
                initial = given,
                min = birth,
                max = today,
                onDismiss = { editDose = null },
                onPick = { date -> scope.launch { app.repository.setDosesGiven(profile.id, listOf(dose.id), date) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { editDose = null },
                title = { Text(dose.name) },
                text = { Text(stringResource(R.string.dose_given_on, Format.date(given))) },
                confirmButton = { TextButton(onClick = { changeDate = true }) { Text(stringResource(R.string.change_date)) } },
                dismissButton = {
                    TextButton(onClick = {
                        scope.launch { app.repository.clearDose(profile.id, dose.id) }
                        editDose = null
                    }) { Text(stringResource(R.string.mark_not_given), color = MaterialTheme.colorScheme.error) }
                },
            )
        }
    }

    skippedDose?.let { dose ->
        AlertDialog(
            onDismissRequest = { skippedDose = null },
            title = { Text(dose.name) },
            text = { Text(stringResource(R.string.dose_skipped_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pickFor = listOf(dose.id) to today
                    skippedDose = null
                }) { Text(stringResource(R.string.record_as_given)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { app.repository.clearSkip(profile.id, dose.id) }
                    skippedDose = null
                }) { Text(stringResource(R.string.undo_skip)) }
            },
        )
    }

    confirmSkip?.let { ids ->
        ConfirmDialog(
            title = stringResource(R.string.skip_confirm_title),
            text = stringResource(R.string.skip_confirm_text),
            confirmLabel = stringResource(R.string.skip_confirm_button),
            onConfirm = { scope.launch { app.repository.setDosesSkipped(profile.id, ids) } },
            onDismiss = { confirmSkip = null },
        )
    }

    confirmUpTo?.let { (visitTitle, upTo) ->
        ConfirmDialog(
            title = stringResource(R.string.upto_confirm_title),
            text = stringResource(R.string.upto_confirm_text, visitTitle),
            confirmLabel = stringResource(R.string.upto_confirm_button),
            onConfirm = { scope.launch { app.repository.setDosesGivenOn(profile.id, upTo) } },
            onDismiss = { confirmUpTo = null },
        )
    }
}

/** A sensible default for "when was it given?": the due date if it has passed, otherwise today. */
private fun defaultGivenDate(due: LocalDate, today: LocalDate): LocalDate = if (due.isAfter(today)) today else due

@Composable
private fun VisitCard(
    plan: VaccinePlanner.VisitPlan,
    onMarkAll: () -> Unit,
    onSkipRemaining: () -> Unit,
    onMarkUpTo: (() -> Unit)?,
    onDoseClick: (VaccineDose) -> Unit,
) {
    val res = LocalContext.current.resources
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val highlight = plan.status == Status.OVERDUE || plan.status == Status.DUE_TODAY || plan.status == Status.PARTLY_DONE

    AppCard(containerColor = if (highlight) colors.errorContainer.copy(alpha = 0.45f) else colors.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.visit.title, style = MaterialTheme.typography.titleLarge)
                Text(Format.date(plan.dueDate), style = MaterialTheme.typography.bodyMedium)
            }
            when (plan.status) {
                Status.DONE -> Pill(stringResource(R.string.status_done), status.goodContainer, status.good)
                Status.SKIPPED -> Pill(stringResource(R.string.status_skipped), colors.surfaceVariant, colors.onSurfaceVariant)
                Status.OVERDUE -> Pill(stringResource(R.string.status_overdue), colors.error, colors.onError)
                Status.PARTLY_DONE -> Pill(stringResource(R.string.status_partly), colors.error, colors.onError)
                Status.DUE_TODAY -> Pill(stringResource(R.string.status_today), colors.primary, colors.onPrimary)
                Status.DUE_SOON -> Pill(stringResource(R.string.status_soon), colors.tertiaryContainer, colors.onTertiaryContainer)
                Status.UPCOMING -> Pill(stringResource(R.string.status_upcoming), colors.surfaceVariant, colors.onSurfaceVariant)
            }
        }
        if (!plan.isComplete && plan.status != Status.UPCOMING) {
            Text(
                vaccineWhenText(res, plan.daysUntilDue),
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.daysUntilDue < 0) colors.error else colors.onSurface,
            )
        }
        plan.visit.doses.forEach { dose ->
            val given = plan.given[dose.id]
            val isSkipped = dose.id in plan.skipped
            val isExpired = dose.id in plan.expired
            Row(
                Modifier.fillMaxWidth().clickable { onDoseClick(dose) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = given != null, onCheckedChange = { onDoseClick(dose) })
                Column(Modifier.weight(1f)) {
                    val muted = isSkipped || isExpired
                    Text(
                        dose.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (muted) colors.onSurfaceVariant else colors.onSurface,
                    )
                    Text(dose.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    // The age-limit note is redundant once the "too late" line below is shown.
                    if (!isExpired) {
                        dose.note?.let { Text("⚠️ $it", style = MaterialTheme.typography.bodyMedium, color = colors.tertiary) }
                    }
                    if (given != null) {
                        Text(
                            stringResource(R.string.dose_given_on, Format.date(given)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = status.good,
                        )
                    } else if (isSkipped) {
                        Text(
                            stringResource(R.string.dose_skipped),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    } else if (isExpired) {
                        Text(
                            stringResource(R.string.dose_expired, Format.months(res, dose.maxAgeMonths ?: 0)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!plan.isComplete && plan.status != Status.UPCOMING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMarkAll) { Text(stringResource(R.string.mark_all_given)) }
                OutlinedButton(onClick = onSkipRemaining) { Text(stringResource(R.string.skip_remaining)) }
            }
            if (onMarkUpTo != null) {
                TextButton(onClick = onMarkUpTo) { Text(stringResource(R.string.mark_upto_given)) }
            }
        }
    }
}
