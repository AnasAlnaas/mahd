package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.data.MilestoneAnswer
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.GrowthMath
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.logic.VaccinePlanner
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.EmojiBadge
import ly.mahd.app.ui.components.TrimesterBar
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.ProgressRing
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.ui.theme.Area
import ly.mahd.app.ui.theme.accent
import ly.mahd.app.ui.profileName
import ly.mahd.app.util.Format

@Composable
fun HomeScreen(app: MahdApp, profile: Profile, nav: Navigator) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (profile.isPregnancy) {
            item { PregnancyTodayCard(app, profile, nav) }
            item {
                WarningCard(
                    title = stringResource(R.string.danger_signs_title),
                    onClick = { nav.push(Routes.DANGER_SIGNS) },
                ) { Text(stringResource(R.string.danger_signs_teaser), style = MaterialTheme.typography.bodyLarge) }
            }
            item { BabyBornCard(profile, nav) }
        } else if (profile.birthDate != null) {
            item { ChildAgeCard(profile) }
            item { TodayStrip(app, profile, nav) }
            item { NextVaccineCard(app, profile, nav) }
            item { MilestonesSummaryCard(app, profile, nav) }
            item { GrowthSummaryCard(app, profile, nav) }
            item { TipCard(app, profile) }
        }
    }
}

// ----- Pregnancy -----

@Composable
private fun PregnancyTodayCard(app: MahdApp, profile: Profile, nav: Navigator) {
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val due = profile.dueDate ?: return
    val ga = PregnancyMath.gestationalAge(due, today)
    val week = app.content.pregnancy.week(ga.weeks.coerceAtLeast(1))
    val daysLeft = PregnancyMath.daysUntilDue(due, today)

    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer, onClick = { nav.selectTab(Routes.PREGNANCY) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.pregnancy_week_number, ga.weeks.toString()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.pregnancy_exact_age, Format.gestationalAge(res, ga.weeks, ga.days)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            EmojiBadge(week.emoji, 76.dp)
        }
        TrimesterBar(
            weeks = ga.weeks + ga.days / 7f,
            track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        )
        Text(
            when {
                daysLeft > 0 -> stringResource(R.string.days_until_due, Format.days(res, daysLeft), Format.date(due))
                daysLeft == 0 -> stringResource(R.string.due_today)
                else -> stringResource(R.string.past_due, Format.days(res, -daysLeft))
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.baby_size_now, week.size), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { nav.selectTab(Routes.PREGNANCY) }) { Text(stringResource(R.string.read_this_week)) }
    }
}

fun trimesterName(t: Int): Int = when (t) {
    1 -> R.string.trimester_1
    2 -> R.string.trimester_2
    else -> R.string.trimester_3
}

@Composable
private fun BabyBornCard(profile: Profile, nav: Navigator) {
    val today = LocalToday.current
    // A week or more after the due date, make it obvious that the profile can be turned into a child.
    val overdue = profile.dueDate?.let { PregnancyMath.daysUntilDue(it, today) < -7 } == true
    AppCard(containerColor = if (overdue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(stringResource(R.string.baby_born_question), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (overdue) R.string.baby_born_overdue_hint else R.string.baby_born_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { nav.push(Routes.born(profile.id)) }) { Text(stringResource(R.string.baby_born_button)) }
    }
}

// ----- Child -----

@Composable
private fun ChildAgeCard(profile: Profile) {
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val birth = profile.birthDate ?: return
    val morning = java.time.LocalTime.now().hour < 12
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (morning) R.string.greeting_morning else R.string.greeting_evening),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    stringResource(R.string.child_age_title, profileName(profile)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    Format.age(res, ChildAge.period(birth, today)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(stringResource(R.string.born_on, Format.date(birth)), style = MaterialTheme.typography.bodyMedium)
            }
            EmojiBadge(
                when (profile.sex) {
                    ly.mahd.app.data.Sex.BOY -> "👦"
                    ly.mahd.app.data.Sex.GIRL -> "👧"
                    null -> "👶"
                },
                size = 84.dp,
            )
        }
        if (ChildAge.usesCorrectedAge(birth, profile.weeksAtBirth, today)) {
            val correctedBirth = ChildAge.correctedBirthDate(birth, profile.weeksAtBirth)
            Text(
                if (today.isBefore(correctedBirth)) {
                    // Not yet at the original due date: "corrected age" would be negative.
                    stringResource(R.string.corrected_age_before_term, Format.age(res, ChildAge.period(today, correctedBirth)))
                } else {
                    stringResource(R.string.corrected_age_line, Format.age(res, ChildAge.period(correctedBirth, today)))
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Three small tiles with today's most useful numbers: next vaccine, skills seen, last weight. */
@Composable
private fun TodayStrip(app: MahdApp, profile: Profile, nav: Navigator) {
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val birth = profile.birthDate ?: return
    val doses by remember(profile.id) { app.repository.doses(profile.id) }.collectAsState(initial = emptyMap())
    val skipped by remember(profile.id) { app.repository.skippedDoses(profile.id) }.collectAsState(initial = emptySet())
    val answers by remember(profile.id) { app.repository.milestoneAnswers(profile.id) }.collectAsState(initial = emptyMap())
    val entries by remember(profile.id) { app.repository.growth(profile.id) }.collectAsState(initial = emptyList())

    val next = VaccinePlanner.next(VaccinePlanner.plan(app.content.vaccines, birth, doses, today, skipped))
    val group = app.content.milestones.groupForAge(ChildAge.referenceMonths(birth, profile.weeksAtBirth, today))
    val yes = group.items.count { answers[it.id] == MilestoneAnswer.YES }
    val lastWeight = entries.lastOrNull { it.weightKg != null }?.weightKg

    val vaccineValue: String
    val vaccineLabel: Int
    when {
        next == null -> { vaccineValue = stringResource(R.string.tile_vaccine_done_value); vaccineLabel = R.string.tile_vaccine_done_label }
        next.daysUntilDue > 0 -> { vaccineValue = Format.days(res, next.daysUntilDue); vaccineLabel = R.string.tile_vaccine_in }
        next.daysUntilDue == 0 -> { vaccineValue = stringResource(R.string.tile_vaccine_today_value); vaccineLabel = R.string.tile_vaccine_today_label }
        else -> { vaccineValue = Format.days(res, -next.daysUntilDue); vaccineLabel = R.string.tile_vaccine_late }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            emoji = "💉",
            value = vaccineValue,
            label = stringResource(vaccineLabel),
            accent = Area.VACCINES.accent(),
            valueColor = if (next != null && next.daysUntilDue < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            onClick = { nav.selectTab(Routes.VACCINES) },
        )
        StatTile(
            emoji = "⭐",
            value = "$yes/${group.items.size}",
            label = stringResource(R.string.tile_milestones_label),
            accent = Area.MILESTONES.accent(),
            modifier = Modifier.weight(1f),
            onClick = { nav.selectTab(Routes.MILESTONES) },
        )
        StatTile(
            emoji = "⚖️",
            value = lastWeight?.let { stringResource(R.string.tile_weight_value, Format.decimal(it, 2)) } ?: "—",
            label = stringResource(if (lastWeight != null) R.string.tile_weight_label else R.string.tile_weight_none),
            accent = Area.GROWTH.accent(),
            modifier = Modifier.weight(1f),
            onClick = { nav.selectTab(Routes.GROWTH) },
        )
    }
}

@Composable
private fun StatTile(
    emoji: String,
    value: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EmojiBadge(emoji, size = 40.dp, container = accent.copy(alpha = 0.18f))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NextVaccineCard(app: MahdApp, profile: Profile, nav: Navigator) {
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val birth = profile.birthDate ?: return
    val doses by remember(profile.id) { app.repository.doses(profile.id) }.collectAsState(initial = emptyMap())
    val skipped by remember(profile.id) { app.repository.skippedDoses(profile.id) }.collectAsState(initial = emptySet())
    val plans = VaccinePlanner.plan(app.content.vaccines, birth, doses, today, skipped)
    val next = VaccinePlanner.next(plans)

    val summary = VaccinePlanner.summary(plans)
    val status = ly.mahd.app.ui.theme.LocalStatusColors.current

    AppCard(onClick = { nav.selectTab(Routes.VACCINES) }, accent = Area.VACCINES.accent()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💉  " + stringResource(R.string.next_vaccine_title), style = MaterialTheme.typography.titleMedium)
                if (next == null) {
                    Text(stringResource(R.string.all_vaccines_done), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(next.visit.title, style = MaterialTheme.typography.titleLarge)
                    Text(Format.date(next.dueDate), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        vaccineWhenText(res, next.daysUntilDue),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (next.daysUntilDue < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(next.remainingDoses.joinToString("، ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            ProgressRing(
                progress = summary.progress,
                size = 68.dp,
                stroke = 8.dp,
                color = if (summary.lateDoses > 0) status.attention else status.good,
            ) {
                Text(summary.given.toString(), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.vaccine_ring_of_total, summary.total.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun vaccineWhenText(res: android.content.res.Resources, daysUntilDue: Int): String = when {
    daysUntilDue > 0 -> res.getString(R.string.vaccine_in_days, Format.days(res, daysUntilDue))
    daysUntilDue == 0 -> res.getString(R.string.vaccine_today)
    else -> res.getString(R.string.vaccine_late_days, Format.days(res, -daysUntilDue))
}

@Composable
private fun MilestonesSummaryCard(app: MahdApp, profile: Profile, nav: Navigator) {
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val answers by remember(profile.id) { app.repository.milestoneAnswers(profile.id) }.collectAsState(initial = emptyMap())
    val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, today)
    val group = app.content.milestones.groupForAge(months)
    val yes = group.items.count { answers[it.id] == MilestoneAnswer.YES }
    val notYet = group.items.count { answers[it.id] == MilestoneAnswer.NOT_YET }

    AppCard(onClick = { nav.selectTab(Routes.MILESTONES) }, accent = Area.MILESTONES.accent()) {
        Text("⭐  " + stringResource(R.string.milestones_card_title, group.title), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { if (group.items.isEmpty()) 0f else yes / group.items.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            stringResource(R.string.milestones_progress, yes.toString(), group.items.size.toString()),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (notYet > 0) {
            Text(
                stringResource(R.string.milestones_not_yet_count, notYet.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun GrowthSummaryCard(app: MahdApp, profile: Profile, nav: Navigator) {
    val birth = profile.birthDate ?: return
    val entries by remember(profile.id) { app.repository.growth(profile.id) }.collectAsState(initial = emptyList())
    val lastWeight = entries.lastOrNull { it.weightKg != null }

    AppCard(onClick = { nav.selectTab(Routes.GROWTH) }, accent = Area.GROWTH.accent()) {
        Text("📈  " + stringResource(R.string.growth_card_title), style = MaterialTheme.typography.titleMedium)
        if (lastWeight == null) {
            Text(stringResource(R.string.growth_card_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            val sex = profile.sex
            Text(
                stringResource(R.string.last_weight, Format.decimal(lastWeight.weightKg!!, 2), Format.date(lastWeight.date)),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (sex != null) {
                val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, lastWeight.date)
                // WHO standards are for term babies: no band before a premature baby's original due date.
                val beforeTerm = ChildAge.isBeforeCorrectedTerm(birth, profile.weeksAtBirth, lastWeight.date)
                val lms = if (beforeTerm) null else app.content.growth.lms(GrowthIndicator.WEIGHT, sex.whoCode, months)
                if (beforeTerm) {
                    Text(stringResource(R.string.growth_preterm_note_short), style = MaterialTheme.typography.bodyMedium)
                } else if (lms != null) {
                    val band = GrowthMath.band(lms.zScore(lastWeight.weightKg))
                    GrowthBandText(band)
                }
            }
        }
    }
}

@Composable
private fun TipCard(app: MahdApp, profile: Profile) {
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val group = app.content.milestones.groupForAge(ChildAge.referenceMonths(birth, profile.weeksAtBirth, today))
    val tip = group.tips.getOrNull(today.dayOfYear % group.tips.size.coerceAtLeast(1)) ?: return
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text("💡  " + stringResource(R.string.tip_of_the_day), style = MaterialTheme.typography.titleMedium)
        Text(tip, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun GrowthBandText(band: GrowthMath.Band) {
    val status = ly.mahd.app.ui.theme.LocalStatusColors.current
    val (text, color) = when (band) {
        GrowthMath.Band.NORMAL -> stringResource(R.string.growth_band_normal) to status.good
        GrowthMath.Band.LOW -> stringResource(R.string.growth_band_low) to status.attention
        GrowthMath.Band.HIGH -> stringResource(R.string.growth_band_high) to status.attention
        GrowthMath.Band.VERY_LOW -> stringResource(R.string.growth_band_very_low) to MaterialTheme.colorScheme.error
        GrowthMath.Band.VERY_HIGH -> stringResource(R.string.growth_band_very_high) to MaterialTheme.colorScheme.error
    }
    Column {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
