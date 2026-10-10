package ly.mahd.app.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.content.MilestoneContent
import ly.mahd.app.content.fatherStage
import ly.mahd.app.content.MilestoneGroup
import ly.mahd.app.content.tipsFor
import ly.mahd.app.data.ChildRecords
import ly.mahd.app.data.GrowthEntry
import ly.mahd.app.data.MilestoneAnswer
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.GrowthMath
import ly.mahd.app.logic.MilestoneFocus
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.logic.VaccinePlanner
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.GroupItem
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.TrimesterMoons
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.ui.profileName
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.util.Format
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(app: MahdApp, profile: Profile, nav: Navigator) {
    if (profile.isPregnancy) PregnancyHome(app, profile, nav) else ChildHome(app, profile, nav)
}

@Composable
private fun HomeList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

/** The top of Home: no card, just the one number that matters today, large. */
@Composable
private fun Hero(overline: String, title: String?, big: String, lines: List<String>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(overline, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        Text(big, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
        lines.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun greeting(): String =
    stringResource(if (LocalTime.now().hour < 12) R.string.greeting_morning else R.string.greeting_evening)

// ----- Pregnancy -----

@Composable
private fun PregnancyHome(app: MahdApp, profile: Profile, nav: Navigator) {
    val askForReminders = shouldAskForReminders(app)
    val advice = rememberTodaysAdvice(app, profile)
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val due = profile.dueDate ?: return
    val ga = PregnancyMath.gestationalAge(due, today)
    val week = app.content.pregnancy.week(ga.weeks.coerceAtLeast(1))
    val daysLeft = PregnancyMath.daysUntilDue(due, today)

    HomeList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Hero(
                    overline = greeting(),
                    title = null,
                    big = stringResource(R.string.pregnancy_week_number, ga.weeks.toString()),
                    lines = listOf(
                        stringResource(R.string.pregnancy_exact_age, Format.gestationalAge(res, ga.weeks, ga.days)),
                        when {
                            daysLeft > 0 -> stringResource(R.string.days_until_due, Format.days(res, daysLeft), Format.date(due))
                            daysLeft == 0 -> stringResource(R.string.due_today)
                            else -> stringResource(R.string.past_due, Format.days(res, -daysLeft))
                        },
                    ),
                )
                TrimesterMoons(weeks = ga.weeks + ga.days / 7f)
            }
        }
        if (askForReminders) item { RemindersPrompt(app, profile) }
        advice?.let { item { AdviceCard(it) } }
        item {
            ListGroup {
                GroupItem(
                    overline = stringResource(R.string.section_baby),
                    headline = stringResource(R.string.baby_size_now, week.size),
                    supporting = stringResource(R.string.read_this_week),
                    supportingColor = MaterialTheme.colorScheme.primary,
                    onClick = { nav.selectTab(Routes.PREGNANCY) },
                )
                // The father sees how he can help at this stage, right on Home.
                val stage = app.content.pregnancy.fatherStage(ga.weeks.coerceAtLeast(1))
                if (LocalParent.current == Parent.FATHER && stage != null) {
                    GroupRow(spacing = 4.dp) {
                        Text(
                            stringResource(R.string.section_support),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(stage.support, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item { BabyBornCard(profile, nav) }
        // Last on the page (the owner's choice); it stays one tap away on the pregnancy tab too.
        item {
            WarningCard(
                title = stringResource(R.string.danger_signs_title),
                onClick = { nav.push(Routes.DANGER_SIGNS) },
            ) { Text(stringResource(R.string.danger_signs_teaser), style = MaterialTheme.typography.bodyLarge) }
        }
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
    AppCard(containerColor = if (overdue) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Text(stringResource(R.string.baby_born_question), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(if (overdue) R.string.baby_born_overdue_hint else R.string.baby_born_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        ExpressiveButton(
            onClick = { nav.push(Routes.born(profile.id)) },
            tonal = !overdue,
            modifier = Modifier.padding(top = 4.dp),
        ) { Text(stringResource(R.string.baby_born_button)) }
    }
}

// ----- Child -----

@Composable
private fun ChildHome(app: MahdApp, profile: Profile, nav: Navigator) {
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val birth = profile.birthDate ?: return
    // Everything recorded for this child in one read. Null until the database answers, so the cards never
    // show a moment of "every vaccine is late" before the real data arrives.
    val records by remember(profile.id) { app.repository.childRecords(profile.id) }.collectAsState(initial = null)
    val data = records ?: return
    val plans = VaccinePlanner.plan(app.content.vaccines, birth, data.doses, today, data.skipped)
    val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, today)
    val group = app.content.milestones.groupForAge(months)
    val askForReminders = shouldAskForReminders(app)
    val advice = rememberTodaysAdvice(app, profile)

    val lines = buildList {
        add(stringResource(R.string.born_on, Format.date(birth)))
        if (ChildAge.usesCorrectedAge(birth, profile.weeksAtBirth, today)) {
            val correctedBirth = ChildAge.correctedBirthDate(birth, profile.weeksAtBirth)
            add(
                if (today.isBefore(correctedBirth)) {
                    // Not yet at the original due date: "corrected age" would be negative.
                    stringResource(R.string.corrected_age_before_term, Format.age(res, ChildAge.period(today, correctedBirth)))
                } else {
                    stringResource(R.string.corrected_age_line, Format.age(res, ChildAge.period(correctedBirth, today)))
                },
            )
        }
    }

    HomeList {
        item {
            Hero(
                overline = greeting(),
                title = stringResource(R.string.child_age_title, profileName(profile)),
                big = Format.age(res, ChildAge.period(birth, today)),
                lines = lines,
            )
        }
        if (askForReminders) item { RemindersPrompt(app, profile) }
        advice?.let { item { AdviceCard(it) } }
        item {
            ListGroup {
                NextVaccineRow(plans, nav)
                MilestonesRow(app.content.milestones, months, data, nav)
                GrowthRow(app, profile, birth, data.growth, nav)
            }
        }
        item { TipBlock(group) }
        // Last on the page (the owner's choice).
        item {
            WarningCard(
                title = stringResource(R.string.child_danger_signs_title),
                onClick = { nav.push(Routes.CHILD_DANGER_SIGNS) },
            ) { Text(stringResource(R.string.child_danger_signs_teaser), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** The first row of Home's group, larger than the others: the next vaccine, and a moon of doses given. */
@Composable
private fun NextVaccineRow(plans: List<VaccinePlanner.VisitPlan>, nav: Navigator) {
    val res = LocalContext.current.resources
    val next = VaccinePlanner.next(plans)
    val summary = VaccinePlanner.summary(plans)
    val colors = MaterialTheme.colorScheme

    GroupRow(onClick = { nav.selectTab(Routes.VACCINES) }, spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.next_vaccine_title), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                if (next == null) {
                    Text(stringResource(R.string.all_vaccines_done), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(next.visit.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${Format.date(next.dueDate)} · ${vaccineWhenText(res, next.daysUntilDue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (next.daysUntilDue < 0) colors.error else colors.onSurface,
                    )
                    Text(
                        next.remainingDoses.joinToString("، ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MoonPhase(summary.progress, size = 56.dp)
                Text(
                    stringResource(R.string.count_of_total, summary.given.toString(), summary.total.toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

fun vaccineWhenText(res: Resources, daysUntilDue: Int): String = when {
    daysUntilDue > 0 -> res.getString(R.string.vaccine_in_days, Format.days(res, daysUntilDue))
    daysUntilDue == 0 -> res.getString(R.string.vaccine_today)
    else -> res.getString(R.string.vaccine_late_days, Format.days(res, -daysUntilDue))
}

@Composable
private fun MilestonesRow(content: MilestoneContent, months: Double, data: ChildRecords, nav: Navigator) {
    val res = LocalContext.current.resources
    // Before the first list (1 month): the same countdown the skills tab shows.
    val group = content.reachedGroups(months).lastOrNull() ?: run {
        val first = content.groups.first()
        GroupItem(
            headline = stringResource(R.string.milestones_first_list_title, first.title),
            supporting = Format.days(res, MilestoneFocus.daysUntil(first, months)),
            leading = { MoonPhase((months / first.months).toFloat(), size = 40.dp, lit = MaterialTheme.colorScheme.tertiary) },
            onClick = { nav.selectTab(Routes.MILESTONES) },
        )
        return
    }
    val yes = group.items.count { data.answers[it.id] == MilestoneAnswer.YES }
    val notYet = group.items.count { data.answers[it.id] == MilestoneAnswer.NOT_YET }
    GroupItem(
        headline = stringResource(R.string.milestones_card_title, group.title),
        supporting = stringResource(R.string.milestones_progress, yes.toString(), group.items.size.toString()) +
            if (notYet > 0) " · " + stringResource(R.string.milestones_not_yet_count, notYet.toString()) else "",
        leading = {
            MoonPhase(
                if (group.items.isEmpty()) 0f else yes / group.items.size.toFloat(),
                size = 40.dp,
                lit = MaterialTheme.colorScheme.tertiary,
            )
        },
        onClick = { nav.selectTab(Routes.MILESTONES) },
    )
}

@Composable
private fun GrowthRow(app: MahdApp, profile: Profile, birth: LocalDate, entries: List<GrowthEntry>, nav: Navigator) {
    val weights = entries.filter { it.weightKg != null }
    val last = weights.lastOrNull()
    val weight = last?.weightKg

    var supporting: String? = null
    var supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sex = profile.sex
    if (last != null && weight != null && sex != null) {
        val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, last.date)
        // WHO standards are for term babies: no band before a premature baby's original due date.
        val beforeTerm = ChildAge.isBeforeCorrectedTerm(birth, profile.weeksAtBirth, last.date)
        val lms = if (beforeTerm) null else app.content.growth.lms(GrowthIndicator.WEIGHT, sex.whoCode, months)
        if (beforeTerm) {
            supporting = stringResource(R.string.growth_preterm_note_short)
        } else if (lms != null) {
            val (text, color) = bandTextAndColor(GrowthMath.band(lms.zScore(weight)))
            supporting = "$text · ${Format.date(last.date)}"
            supportingColor = color
        }
    } else if (last != null) {
        supporting = Format.date(last.date)
    }

    GroupItem(
        headline = if (weight != null) {
            stringResource(R.string.tile_weight_label) + ": " + stringResource(R.string.tile_weight_value, Format.decimal(weight, 2))
        } else {
            stringResource(R.string.growth_card_title)
        },
        supporting = supporting ?: stringResource(R.string.growth_card_empty),
        supportingColor = if (supporting == null) MaterialTheme.colorScheme.onSurfaceVariant else supportingColor,
        leading = {
            val values = weights.takeLast(8).mapNotNull { it.weightKg }
            if (values.size >= 2) {
                Sparkline(values, MaterialTheme.colorScheme.secondary)
            } else {
                AppIcon(R.drawable.ic_growth, null, Modifier.size(40.dp).padding(6.dp), tint = MaterialTheme.colorScheme.secondary)
            }
        },
        onClick = { nav.selectTab(Routes.GROWTH) },
    )
}

/** The last few weights as a small line, oldest on the left like the growth chart, the latest one marked. */
@Composable
private fun Sparkline(values: List<Double>, color: Color) {
    Canvas(Modifier.size(width = 40.dp, height = 40.dp)) {
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val pad = 4.dp.toPx()
        val points = values.mapIndexed { i, v ->
            val t = i / (values.size - 1f)
            val x = pad + (size.width - 2 * pad) * t
            val y = size.height - pad - ((v - min) / span).toFloat() * (size.height - 2 * pad)
            Offset(x, y)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 3.5.dp.toPx(), points.last())
    }
}

/** One idea for today, set as a quiet block of text rather than another card. */
@Composable
private fun TipBlock(group: MilestoneGroup) {
    val today = LocalToday.current
    val tips = group.tipsFor(LocalParent.current)
    val tip = tips.getOrNull(today.dayOfYear % tips.size.coerceAtLeast(1)) ?: return
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.tip_of_the_day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(tip, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun bandTextAndColor(band: GrowthMath.Band): Pair<String, Color> {
    val status = LocalStatusColors.current
    return when (band) {
        GrowthMath.Band.NORMAL -> stringResource(R.string.growth_band_normal) to status.good
        GrowthMath.Band.LOW -> stringResource(R.string.growth_band_low) to status.attention
        GrowthMath.Band.HIGH -> stringResource(R.string.growth_band_high) to status.attention
        GrowthMath.Band.VERY_LOW -> stringResource(R.string.growth_band_very_low) to MaterialTheme.colorScheme.error
        GrowthMath.Band.VERY_HIGH -> stringResource(R.string.growth_band_very_high) to MaterialTheme.colorScheme.error
    }
}

@Composable
fun GrowthBandText(band: GrowthMath.Band) {
    val (text, color) = bandTextAndColor(band)
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
}
