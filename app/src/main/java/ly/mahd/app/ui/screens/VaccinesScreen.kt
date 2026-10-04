package ly.mahd.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.DatePickerPopup
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.Pill
import ly.mahd.app.ui.components.ProgressRing
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.util.Format
import java.time.LocalDate

/**
 * The national schedule for one child as a timeline: a progress ring on top, then one node per visit
 * (green = done, red = late, filled = due soon, hollow = later). Finished visits fold into one line.
 */
@Composable
fun VaccinesScreen(app: MahdApp, profile: Profile) {
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val schedule = app.content.vaccines
    // Null until the database answers, so the timeline never flashes "everything is late" first.
    val records by remember(profile.id) { app.repository.childRecords(profile.id) }.collectAsState(initial = null)
    val data = records ?: return
    val plans = VaccinePlanner.plan(schedule, birth, data.doses, today, data.skipped)
    val summary = VaccinePlanner.summary(plans)
    val firstUpcomingId = plans.firstOrNull { it.status == Status.UPCOMING }?.visit?.id
    val scope = rememberCoroutineScope()

    // Which folded visits the mother has opened to see their doses.
    var expanded by remember(profile.id) { mutableStateOf(setOf<String>()) }

    // Which doses the date picker is for (one dose, or all remaining doses of a visit).
    var pickFor by remember { mutableStateOf<Pair<List<String>, LocalDate>?>(null) }
    var editDose by remember { mutableStateOf<Pair<VaccineDose, LocalDate>?>(null) }
    var skippedDose by remember { mutableStateOf<VaccineDose?>(null) }
    var confirmSkip by remember { mutableStateOf<List<String>?>(null) }
    var confirmUpTo by remember { mutableStateOf<Pair<String, Map<String, LocalDate>>?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item { SummaryHeader(summary) }
        if (!schedule.reviewed) {
            item { DraftNotice(stringResource(R.string.vaccines_draft_notice), Modifier.padding(bottom = 16.dp)) }
        }
        itemsIndexed(plans, key = { _, plan -> plan.visit.id }) { index, plan ->
            val hasEarlierGap = plans.take(index).any { !it.isComplete }
            // Finished visits and far-away future visits fold into one line; only the next future visit stays open.
            val folds = plan.isComplete || (plan.status == Status.UPCOMING && plan.visit.id != firstUpcomingId)
            val collapsed = folds && plan.visit.id !in expanded
            TimelineRow(plan.status, isFirst = index == 0, isLast = index == plans.lastIndex) {
                VisitCard(
                    plan = plan,
                    collapsed = collapsed,
                    onToggle = {
                        expanded = if (plan.visit.id in expanded) expanded - plan.visit.id else expanded + plan.visit.id
                    },
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
        }
        item {
            Text(
                stringResource(R.string.vaccines_source, schedule.source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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

// ---------------------------------------------------------------------------------------------
// Header with the progress ring
// ---------------------------------------------------------------------------------------------

@Composable
private fun SummaryHeader(summary: VaccinePlanner.Summary) {
    val res = LocalContext.current.resources
    val status = LocalStatusColors.current
    val late = summary.lateDoses > 0
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.vaccines_title), style = MaterialTheme.typography.headlineSmall)
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(
                    progress = summary.progress,
                    color = if (late) status.attention else status.good,
                ) {
                    Text(summary.given.toString(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.vaccine_ring_of_total, summary.total.toString()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (late) stringResource(R.string.vaccine_summary_late, Format.doses(res, summary.lateDoses))
                        else stringResource(R.string.vaccine_summary_ok),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (late) MaterialTheme.colorScheme.error else status.good,
                    )
                    Text(
                        stringResource(R.string.vaccine_summary_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------------------------

private val NODE_SIZE = 28.dp
private val NODE_TOP = 8.dp

/** One step of the timeline: the vertical rail with its status node on the side, [content] beside it. */
@Composable
private fun TimelineRow(status: Status, isFirst: Boolean, isLast: Boolean, content: @Composable () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(40.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val nodeCenter = (NODE_TOP + NODE_SIZE / 2).toPx()
                    val top = if (isFirst) nodeCenter else 0f
                    val bottom = if (isLast) nodeCenter else size.height
                    drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 3.dp.toPx())
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            TimelineNode(status, Modifier.padding(top = NODE_TOP))
        }
        Column(Modifier.weight(1f).padding(bottom = 12.dp)) { content() }
    }
}

@Composable
private fun TimelineNode(status: Status, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val good = LocalStatusColors.current.good
    val base = modifier.size(NODE_SIZE)
    when (status) {
        Status.DONE -> NodeCircle(base.background(good, CircleShape), R.drawable.ic_check, Color.White)
        Status.OVERDUE, Status.PARTLY_DONE -> NodeCircle(base.background(colors.error, CircleShape), R.drawable.ic_warning, colors.onError)
        Status.DUE_TODAY -> NodeCircle(base.background(colors.primary, CircleShape), R.drawable.ic_vaccine, colors.onPrimary)
        Status.DUE_SOON -> NodeCircle(base.background(colors.tertiary, CircleShape), R.drawable.ic_vaccine, colors.onTertiary)
        Status.SKIPPED -> Box(base.background(colors.outline, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp, 2.dp).background(Color.White))
        }
        // Hollow: the background colour hides the rail behind the circle.
        Status.UPCOMING -> Box(base.background(colors.background, CircleShape).border(2.dp, colors.outline, CircleShape))
    }
}

@Composable
private fun NodeCircle(modifier: Modifier, icon: Int, tint: Color) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AppIcon(icon, null, Modifier.size(16.dp), tint = tint)
    }
}

// ---------------------------------------------------------------------------------------------
// Visit card
// ---------------------------------------------------------------------------------------------

@Composable
private fun StatusPill(status: Status) {
    val colors = MaterialTheme.colorScheme
    val good = LocalStatusColors.current
    when (status) {
        Status.DONE -> Pill(stringResource(R.string.status_done), good.goodContainer, good.good)
        Status.SKIPPED -> Pill(stringResource(R.string.status_skipped), colors.surfaceVariant, colors.onSurfaceVariant)
        Status.OVERDUE -> Pill(stringResource(R.string.status_overdue), colors.error, colors.onError)
        Status.PARTLY_DONE -> Pill(stringResource(R.string.status_partly), colors.error, colors.onError)
        Status.DUE_TODAY -> Pill(stringResource(R.string.status_today), colors.primary, colors.onPrimary)
        Status.DUE_SOON -> Pill(stringResource(R.string.status_soon), colors.tertiaryContainer, colors.onTertiaryContainer)
        Status.UPCOMING -> Pill(stringResource(R.string.status_upcoming), colors.surfaceVariant, colors.onSurfaceVariant)
    }
}

@Composable
private fun VisitCard(
    plan: VaccinePlanner.VisitPlan,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onMarkAll: () -> Unit,
    onSkipRemaining: () -> Unit,
    onMarkUpTo: (() -> Unit)?,
    onDoseClick: (VaccineDose) -> Unit,
) {
    val res = LocalContext.current.resources
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val highlight = plan.status == Status.OVERDUE || plan.status == Status.DUE_TODAY || plan.status == Status.PARTLY_DONE

    // A folded visit shows as a single tappable line.
    if (collapsed) {
        AppCard(onClick = onToggle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.visit.title, style = MaterialTheme.typography.titleMedium)
                    Text(Format.date(plan.dueDate), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                StatusPill(plan.status)
                AppIcon(R.drawable.ic_expand, stringResource(R.string.visit_expand), tint = colors.onSurfaceVariant)
            }
        }
        return
    }

    AppCard(containerColor = if (highlight) colors.errorContainer.copy(alpha = 0.45f) else colors.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = plan.isComplete || plan.status == Status.UPCOMING, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(plan.visit.title, style = MaterialTheme.typography.titleLarge)
                Text(Format.date(plan.dueDate), style = MaterialTheme.typography.bodyMedium)
            }
            StatusPill(plan.status)
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
