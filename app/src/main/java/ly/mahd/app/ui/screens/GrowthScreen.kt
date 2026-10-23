package ly.mahd.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog


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
import androidx.compose.ui.graphics.Shape
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
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.GroupItem
import ly.mahd.app.ui.components.RollingText
import ly.mahd.app.ui.components.SectionTitle
import ly.mahd.app.ui.components.fadeAndResize
import ly.mahd.app.ui.components.groupRowShape
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.DateField
import ly.mahd.app.ui.components.EmptyState
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.ui.theme.LocalSurfaces
import ly.mahd.app.util.Format
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Weight, length and head size on WHO charts, with a plain-language reading. */
@Composable
fun GrowthScreen(app: MahdApp, profile: Profile) {
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val sex = profile.sex
    val loaded by remember(profile.id) { app.repository.growth(profile.id) }.collectAsState(initial = null)
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
    // Null until the database answers, so "no measurements yet" does not flash before the real list.
    val entries = loaded ?: return
    val canZoom = allMax > 12
    val zoomed = recentOnly && canZoom
    val minMonth = if (zoomed) (ageNow - 5).toInt().coerceAtLeast(0) else 0
    val maxMonth = if (zoomed) maxOf(minMonth + 6, ceil(ageNow + 1).toInt()).coerceAtMost(60).coerceAtLeast(minMonth + 1) else allMax

    fun valueOf(e: GrowthEntry, of: GrowthIndicator = indicator): Double? = when (of) {
        GrowthIndicator.WEIGHT -> e.weightKg
        GrowthIndicator.LENGTH -> e.lengthCm
        GrowthIndicator.HEAD -> e.headCm
    }

    // WHO standards describe term babies, so measurements taken before a premature baby's original
    // due date (negative corrected age) are listed but not drawn or classified.
    fun beforeTerm(e: GrowthEntry) = ChildAge.isBeforeCorrectedTerm(birth, profile.weeksAtBirth, e.date)

    fun pointsOf(of: GrowthIndicator) = entries.mapNotNull { e ->
        if (beforeTerm(e)) null
        else valueOf(e, of)?.let { ChildAge.referenceMonths(birth, profile.weeksAtBirth, e.date) to it }
    }

    /** The latest value of [of], how it reads on the WHO chart, and how it moved since the measurement before. */
    @Composable
    fun LatestReading(of: GrowthIndicator) {
        val childSex = sex ?: return
        val latest = entries.lastOrNull { valueOf(it, of) != null } ?: return
        val value = valueOf(latest, of) ?: return
        val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, latest.date)
        val latestBeforeTerm = beforeTerm(latest)
        val lms = if (latestBeforeTerm) null else app.content.growth.lms(of, childSex.whoCode, months)
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.latest_measurement_on, Format.date(latest.date)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A new measurement rolls the number to its value.
            RollingText(
                "${Format.decimal(value, 2)} ${stringResource(indicatorUnit(of))}",
                style = MaterialTheme.typography.displaySmall,
            )
            if (latestBeforeTerm) {
                Text(stringResource(R.string.growth_preterm_note), style = MaterialTheme.typography.bodyMedium)
            } else if (lms != null) {
                val z = lms.zScore(value)
                GrowthBandText(GrowthMath.band(z))
                val pct = GrowthMath.percentile(z).roundToInt().coerceIn(1, 99)
                val notes = buildList {
                    add(stringResource(R.string.percentile_plain, pct.toString()))
                    // Compare with the measurement before this one (same indicator, both on the WHO charts).
                    val usable = entries.filter { valueOf(it, of) != null && !beforeTerm(it) }
                    val before = usable.getOrNull(usable.size - 2)
                    val beforeValue = before?.let { valueOf(it, of) }
                    if (before != null && beforeValue != null && usable.last() == latest) {
                        val beforeLms = app.content.growth.lms(of, childSex.whoCode, ChildAge.referenceMonths(birth, profile.weeksAtBirth, before.date))
                        if (beforeLms != null) {
                            add(
                                stringResource(
                                    when (GrowthMath.trend(beforeLms.zScore(beforeValue), z)) {
                                        GrowthMath.Trend.UP -> R.string.trend_up
                                        GrowthMath.Trend.STEADY -> R.string.trend_steady
                                        GrowthMath.Trend.DOWN -> R.string.trend_down
                                    },
                                ),
                            )
                        }
                    }
                    if (GrowthMath.band(z) != GrowthMath.Band.NORMAL) add(stringResource(R.string.growth_see_doctor))
                }
                notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(stringResource(R.string.growth_out_of_range), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // Blocks are 16dp apart; the measurements, each a list item of their own so they can fade in and out, 2dp.
    val block = Modifier.padding(bottom = 14.dp)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "indicator") {
            ConnectedChoice(
                options = GrowthIndicator.entries.map { stringResource(indicatorLabel(it)) },
                selected = GrowthIndicator.entries.indexOf(indicator),
                onSelect = { indicatorCode = GrowthIndicator.entries[it].code },
                modifier = block,
            )
        }
        if (sex != null && entries.any { valueOf(it) != null }) {
            item(key = "latest") {
                // Weight, length and head each have their own reading; a new one fades in when another is chosen.
                AnimatedContent(
                    targetState = indicator,
                    modifier = block.animateItem(),
                    transitionSpec = { fadeAndResize() },
                    label = "latest",
                ) { shownIndicator ->
                    LatestReading(shownIndicator)
                }
            }
        }
        item(key = "chart") {
            AppCard(block.animateItem()) {
                Crossfade(targetState = indicator, animationSpec = AppMotion.slowEffects(), label = "chart-title") {
                    Text(stringResource(indicatorTitle(it)), style = MaterialTheme.typography.titleLarge)
                }
                if (sex == null) {
                    Text(stringResource(R.string.growth_need_sex), style = MaterialTheme.typography.bodyLarge)
                } else {
                    if (canZoom) {
                        ConnectedChoice(
                            options = listOf(stringResource(R.string.chart_range_all), stringResource(R.string.chart_range_recent)),
                            selected = if (recentOnly) 1 else 0,
                            onSelect = { recentOnly = it == 1 },
                            height = 40.dp,
                            unselectedColor = LocalSurfaces.current.inset,
                            selectedColors = { MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer },
                        )
                    }
                    // Another measure cross-fades to its own chart; the range (all / recent months) glides inside it.
                    Crossfade(targetState = indicator, animationSpec = AppMotion.slowEffects(), label = "chart") { shownIndicator ->
                        GrowthChart(
                            standards = app.content.growth,
                            indicator = shownIndicator,
                            sexCode = sex.whoCode,
                            points = pointsOf(shownIndicator),
                            minMonth = minMonth,
                            maxMonth = maxMonth,
                            valueLabel = { Format.decimal(it, if (shownIndicator == GrowthIndicator.WEIGHT) 2 else 1) },
                        )
                    }
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
        item(key = "add") {
            ExpressiveButton(onClick = { adding = true }, modifier = block.animateItem().fillMaxWidth(), icon = R.drawable.ic_add) {
                Text(stringResource(R.string.add_measurement))
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.growth_empty), Modifier.animateItem()) }
        } else {
            item(key = "history") { SectionTitle(stringResource(R.string.measurements_history), Modifier.animateItem().padding(bottom = 6.dp)) }
            val newestFirst = entries.asReversed()
            // A new measurement fades in at the top and the others glide down; a deleted one fades away.
            itemsIndexed(newestFirst, key = { _, e -> e.id }) { i, e ->
                MeasurementRow(
                    e,
                    shape = groupRowShape(i, newestFirst.size),
                    modifier = Modifier.animateItem(AppMotion.slowEffects(), AppMotion.defaultSpatial(), AppMotion.defaultEffects()),
                    onDelete = { deleting = e },
                )
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
private fun MeasurementRow(e: GrowthEntry, shape: Shape, modifier: Modifier, onDelete: () -> Unit) {
    val parts = buildList {
        e.weightKg?.let { add(stringResource(R.string.value_weight, Format.decimal(it, 2))) }
        e.lengthCm?.let { add(stringResource(R.string.value_length, Format.decimal(it))) }
        e.headCm?.let { add(stringResource(R.string.value_head, Format.decimal(it))) }
    }
    GroupItem(
        headline = parts.joinToString("  ·  "),
        modifier = modifier,
        shape = shape,
        supporting = Format.date(e.date),
        trailing = {
            IconButton(onClick = onDelete) {
                AppIcon(R.drawable.ic_delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
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
    birth: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (GrowthEntry) -> Unit,
) {
    // Kept as text so it survives screen rotation.
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateText)
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
