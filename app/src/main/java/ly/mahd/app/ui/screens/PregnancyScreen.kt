package ly.mahd.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.PregnancyWeek
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.TrimesterMoons
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.util.Format

/** Week-by-week pregnancy guide. Opens on the current week; arrows move between weeks. */
@Composable
fun PregnancyScreen(app: MahdApp, profile: Profile, nav: Navigator, initialWeek: Int?, standalone: Boolean) {
    val today = LocalToday.current
    val content = app.content.pregnancy
    val currentWeek = profile.dueDate?.let { PregnancyMath.gestationalAge(it, today).weeks }
        ?.coerceIn(1, content.lastWeek) ?: 1
    var week by rememberSaveable(profile.id) { mutableIntStateOf(initialWeek ?: currentWeek) }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WeekSwitcher(
                    week = week,
                    lastWeek = content.lastWeek,
                    isCurrent = week == currentWeek,
                    onWeek = { week = it },
                    onToday = { week = currentWeek },
                )
            }
            item { TrimesterMoons(weeks = week.toFloat()) }
            if (!content.reviewed) item { DraftNotice() }
            item { Crossfade(targetState = week, label = "week") { wk -> WeekDetail(content.week(wk)) } }
            item {
                WarningCard(
                    title = stringResource(R.string.danger_signs_title),
                    onClick = { nav.push(Routes.DANGER_SIGNS) },
                ) { Text(stringResource(R.string.danger_signs_teaser), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }

    if (standalone) {
        SubScreen(title = stringResource(R.string.tab_pregnancy), onBack = nav::back, content = body)
    } else {
        body(PaddingValues(0.dp))
    }
}

@Composable
private fun WeekSwitcher(week: Int, lastWeek: Int, isCurrent: Boolean, onWeek: (Int) -> Unit, onToday: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onWeek(week - 1) }, enabled = week > 1) {
                AppIcon(R.drawable.ic_chevron_back, stringResource(R.string.previous_week))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.pregnancy_week_number, week.toString()),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.trimester_label, stringResource(trimesterName(PregnancyMath.trimester(week)))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = { onWeek(week + 1) }, enabled = week < lastWeek) {
                AppIcon(R.drawable.ic_chevron_forward, stringResource(R.string.next_week))
            }
        }
        if (isCurrent) {
            Text(
                stringResource(R.string.this_is_your_week),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onToday) { Text(stringResource(R.string.back_to_current_week)) }
        }
    }
}

@Composable
private fun WeekDetail(w: PregnancyWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            // The fruit is content (what the size is compared to), so it stays, inline with the words.
            Text("${stringResource(R.string.baby_size_now, w.size)} ${w.emoji}", style = MaterialTheme.typography.headlineSmall)
            val parts = buildList {
                w.lengthCm?.let { add(stringResource(R.string.approx_length, Format.decimal(it))) }
                w.weightG?.let { g ->
                    add(
                        if (g >= 1000) stringResource(R.string.approx_weight_kg, Format.decimal(g / 1000.0, 1))
                        else stringResource(R.string.approx_weight_g, Format.decimal(g, 0)),
                    )
                }
            }
            if (parts.isNotEmpty()) {
                Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ListGroup {
            WeekSection(stringResource(R.string.section_baby), w.baby, MaterialTheme.colorScheme.primary)
            WeekSection(stringResource(R.string.section_mother), w.mother, MaterialTheme.colorScheme.primary)
            WeekSection(stringResource(R.string.section_tip), w.tip, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WeekSection(title: String, text: String, titleColor: Color) {
    GroupRow(spacing = 4.dp) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = titleColor)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
