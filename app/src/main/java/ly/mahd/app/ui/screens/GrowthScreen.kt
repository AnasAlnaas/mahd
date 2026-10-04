package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.data.GrowthEntry
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.GrowthMath
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.DateField
import ly.mahd.app.ui.components.EmptyState
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.theme.Area
import ly.mahd.app.ui.theme.accent
import ly.mahd.app.util.Format
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Weight, length and head size on WHO charts, with a plain-language reading. */
@Composable
fun GrowthScreen(app: MahdApp, profile: Profile) {
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val sex = profile.sex
    val entries by remember(profile.id) { app.repository.growth(profile.id) }.collectAsState(initial = emptyList())
    var indicatorCode by rememberSaveable { mutableStateOf(GrowthIndicator.WEIGHT.code) }
    val indicator = GrowthIndicator.entries.first { it.code == indicatorCode }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GrowthEntry?>(null) }
    val scope = rememberCoroutineScope()

    val ageNow = ChildAge.referenceMonths(birth, profile.weeksAtBirth, today)
    // Show at least the first year; then round up to the next half-year, max 5 years.
    val allMax = (ceil((ageNow + 2) / 6) * 6).toInt().coerceIn(12, 60)
    // "Recent months" zooms in on the last ~6 months so small changes are easier to see.
    var recentOnly by rememberSaveable { mutableStateOf(false) }
    val canZoom = allMax > 12
    val zoomed = recentOnly && canZoom
    val minMonth = if (zoomed) (ageNow - 5).toInt().coerceAtLeast(0) else 0
    val maxMonth = if (zoomed) maxOf(minMonth + 6, ceil(ageNow + 1).toInt()).coerceAtMost(60).coerceAtLeast(minMonth + 1) else allMax

    fun valueOf(e: GrowthEntry): Double? = when (indicator) {
        GrowthIndicator.WEIGHT -> e.weightKg
        GrowthIndicator.LENGTH -> e.lengthCm
        GrowthIndicator.HEAD -> e.headCm
    }

    // WHO standards describe term babies, so measurements taken before a premature baby's original
    // due date (negative corrected age) are listed but not drawn or classified.
    fun beforeTerm(e: GrowthEntry) = ChildAge.isBeforeCorrectedTerm(birth, profile.weeksAtBirth, e.date)

    val points = entries.mapNotNull { e ->
        if (beforeTerm(e)) null
        else valueOf(e)?.let { ChildAge.referenceMonths(birth, profile.weeksAtBirth, e.date) to it }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrowthIndicator.entries.forEach { ind ->
                    FilterChip(
                        selected = ind == indicator,
                        onClick = { indicatorCode = ind.code },
                        label = { Text(stringResource(indicatorLabel(ind))) },
                    )
                }
            }
        }
        if (canZoom) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !recentOnly, onClick = { recentOnly = false }, label = { Text(stringResource(R.string.chart_range_all)) })
                    FilterChip(selected = recentOnly, onClick = { recentOnly = true }, label = { Text(stringResource(R.string.chart_range_recent)) })
                }
            }
        }
        item {
            AppCard(accent = Area.GROWTH.accent()) {
                Text(stringResource(indicatorTitle(indicator)), style = MaterialTheme.typography.titleMedium)
                if (sex == null) {
                    Text(stringResource(R.string.growth_need_sex), style = MaterialTheme.typography.bodyLarge)
                } else {
                    GrowthChart(
                        standards = app.content.growth,
                        indicator = indicator,
                        sexCode = sex.whoCode,
                        points = points,
                        minMonth = minMonth,
                        maxMonth = maxMonth,
                        valueLabel = { Format.decimal(it, if (indicator == GrowthIndicator.WEIGHT) 2 else 1) },
                    )
                    Text(
                        stringResource(R.string.chart_axis_hint, stringResource(indicatorUnit(indicator))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.chart_legend),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ChildAge.usesCorrectedAge(birth, profile.weeksAtBirth, today)) {
                        Text(stringResource(R.string.chart_corrected_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        val latest = entries.lastOrNull { valueOf(it) != null }
        if (latest != null && sex != null) {
            item {
                val value = valueOf(latest)!!
                val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, latest.date)
                val latestBeforeTerm = beforeTerm(latest)
                val lms = if (latestBeforeTerm) null else app.content.growth.lms(indicator, sex.whoCode, months)
                AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        stringResource(
                            R.string.latest_measurement,
                            Format.decimal(value, 2),
                            stringResource(indicatorUnit(indicator)),
                            Format.date(latest.date),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (latestBeforeTerm) {
                        Text(stringResource(R.string.growth_preterm_note), style = MaterialTheme.typography.bodyMedium)
                    } else if (lms != null) {
                        val z = lms.zScore(value)
                        GrowthBandText(GrowthMath.band(z))
                        val pct = GrowthMath.percentile(z).roundToInt().coerceIn(1, 99)
                        Text(
                            stringResource(R.string.percentile_plain, pct.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (GrowthMath.band(z) != GrowthMath.Band.NORMAL) {
                            Text(stringResource(R.string.growth_see_doctor), style = MaterialTheme.typography.bodyMedium)
                        }
                        // Compare with the measurement before this one (same indicator, both on the WHO charts).
                        val usable = entries.filter { valueOf(it) != null && !beforeTerm(it) }
                        val before = usable.getOrNull(usable.size - 2)
                        if (before != null && usable.last() == latest) {
                            val beforeLms = app.content.growth.lms(indicator, sex.whoCode, ChildAge.referenceMonths(birth, profile.weeksAtBirth, before.date))
                            if (beforeLms != null) {
                                val trendText = when (GrowthMath.trend(beforeLms.zScore(valueOf(before)!!), z)) {
                                    GrowthMath.Trend.UP -> R.string.trend_up
                                    GrowthMath.Trend.STEADY -> R.string.trend_steady
                                    GrowthMath.Trend.DOWN -> R.string.trend_down
                                }
                                Text(stringResource(trendText), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        Text(stringResource(R.string.growth_out_of_range), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                AppIcon(R.drawable.ic_add, null)
                Text("  " + stringResource(R.string.add_measurement))
            }
        }
        if (entries.isEmpty()) {
            item { EmptyState("📏", stringResource(R.string.growth_empty)) }
        } else {
            item { Text(stringResource(R.string.measurements_history), style = MaterialTheme.typography.titleMedium) }
            items(entries.asReversed(), key = { it.id }) { e ->
                MeasurementRow(e, onDelete = { deleting = e })
            }
        }
    }

    if (adding) {
        AddMeasurementDialog(
            birth = birth,
            today = today,
            onDismiss = { adding = false },
            onSave = { entry ->
                scope.launch { app.repository.addGrowth(entry.copy(profileId = profile.id)) }
                adding = false
            },
        )
    }
    deleting?.let { e ->
        ConfirmDialog(
            title = stringResource(R.string.delete_measurement_title),
            text = Format.date(e.date),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { scope.launch { app.repository.deleteGrowth(e.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun MeasurementRow(e: GrowthEntry, onDelete: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Format.date(e.date), style = MaterialTheme.typography.titleMedium)
                val parts = buildList {
                    e.weightKg?.let { add(stringResource(R.string.value_weight, Format.decimal(it, 2))) }
                    e.lengthCm?.let { add(stringResource(R.string.value_length, Format.decimal(it))) }
                    e.headCm?.let { add(stringResource(R.string.value_head, Format.decimal(it))) }
                }
                Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = onDelete) {
                AppIcon(R.drawable.ic_delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Accepts "3.5", "3,5", Arabic digits "٣٫٥" — whatever the keyboard gives. */
fun parseNumber(text: String): Double? {
    val normalized = buildString {
        text.trim().forEach { c ->
            when (c) {
                in '٠'..'٩' -> append('0' + (c - '٠'))
                in '۰'..'۹' -> append('0' + (c - '۰'))
                ',', '٫', '،' -> append('.')
                else -> append(c)
            }
        }
    }
    return normalized.toDoubleOrNull()
}

@Composable
private fun AddMeasurementDialog(
    birth: java.time.LocalDate,
    today: java.time.LocalDate,
    onDismiss: () -> Unit,
    onSave: (GrowthEntry) -> Unit,
) {
    // Kept as text so it survives screen rotation.
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = java.time.LocalDate.parse(dateText)
    var weight by rememberSaveable { mutableStateOf("") }
    var length by rememberSaveable { mutableStateOf("") }
    var head by rememberSaveable { mutableStateOf("") }

    val w = parseNumber(weight)
    val l = parseNumber(length)
    val h = parseNumber(head)
    val weightOk = weight.isBlank() || (w != null && w in 0.5..40.0)
    val lengthOk = length.isBlank() || (l != null && l in 30.0..130.0)
    val headOk = head.isBlank() || (h != null && h in 25.0..60.0)
    val anyValue = w != null || l != null || h != null
    val valid = weightOk && lengthOk && headOk && anyValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_measurement)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(R.string.measurement_date), date, birth, today, { dateText = it.toString() })
                NumberField(weight, { weight = it }, stringResource(R.string.weight_label), weightOk)
                NumberField(length, { length = it }, stringResource(R.string.length_label), lengthOk)
                NumberField(head, { head = it }, stringResource(R.string.head_label), headOk)
                Text(
                    stringResource(R.string.measurement_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        GrowthEntry(
                            id = 0,
                            profileId = 0,
                            date = date,
                            weightKg = if (weight.isBlank()) null else w,
                            lengthCm = if (length.isBlank()) null else l,
                            headCm = if (head.isBlank()) null else h,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NumberField(value: String, onValue: (String) -> Unit, label: String, ok: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(6)) },
        label = { Text(label) },
        isError = !ok,
        supportingText = if (!ok) ({ Text(stringResource(R.string.value_out_of_range)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun indicatorLabel(i: GrowthIndicator) = when (i) {
    GrowthIndicator.WEIGHT -> R.string.indicator_weight
    GrowthIndicator.LENGTH -> R.string.indicator_length
    GrowthIndicator.HEAD -> R.string.indicator_head
}

private fun indicatorTitle(i: GrowthIndicator) = when (i) {
    GrowthIndicator.WEIGHT -> R.string.chart_title_weight
    GrowthIndicator.LENGTH -> R.string.chart_title_length
    GrowthIndicator.HEAD -> R.string.chart_title_head
}

private fun indicatorUnit(i: GrowthIndicator) = when (i) {
    GrowthIndicator.WEIGHT -> R.string.unit_kg
    else -> R.string.unit_cm
}
