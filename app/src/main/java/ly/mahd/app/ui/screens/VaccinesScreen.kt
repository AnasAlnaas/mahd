package ly.mahd.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val plans = VaccinePlanner.plan(schedule, birth, doses, today)
    val scope = rememberCoroutineScope()

    // Which doses the date picker is for (one dose, or all remaining doses of a visit).
    var pickFor by remember { mutableStateOf<Pair<List<String>, LocalDate>?>(null) }
    var editDose by remember { mutableStateOf<Pair<VaccineDose, LocalDate>?>(null) }

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
        items(plans, key = { it.visit.id }) { plan ->
            VisitCard(
                plan = plan,
                onMarkAll = {
                    val remaining = plan.visit.doses.filter { it.id !in plan.given }.map { it.id }
                    pickFor = remaining to defaultGivenDate(plan.dueDate, today)
                },
                onDoseClick = { dose ->
                    val given = plan.given[dose.id]
                    if (given == null) pickFor = listOf(dose.id) to defaultGivenDate(plan.dueDate, today)
                    else editDose = dose to given
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
}

/** A sensible default for "when was it given?": the due date if it has passed, otherwise today. */
private fun defaultGivenDate(due: LocalDate, today: LocalDate): LocalDate = if (due.isAfter(today)) today else due

@Composable
private fun VisitCard(plan: VaccinePlanner.VisitPlan, onMarkAll: () -> Unit, onDoseClick: (VaccineDose) -> Unit) {
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
            Row(
                Modifier.fillMaxWidth().clickable { onDoseClick(dose) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = given != null, onCheckedChange = { onDoseClick(dose) })
                Column(Modifier.weight(1f)) {
                    Text(dose.name, style = MaterialTheme.typography.bodyLarge)
                    Text(dose.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    dose.note?.let { Text("⚠️ $it", style = MaterialTheme.typography.bodyMedium, color = colors.tertiary) }
                    if (given != null) {
                        Text(
                            stringResource(R.string.dose_given_on, Format.date(given)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = status.good,
                        )
                    }
                }
            }
        }
        if (!plan.isComplete && plan.status != Status.UPCOMING) {
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onMarkAll) { Text(stringResource(R.string.mark_all_given)) }
        }
    }
}
