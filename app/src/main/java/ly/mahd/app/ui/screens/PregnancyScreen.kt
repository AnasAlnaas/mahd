package ly.mahd.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.FatherStage
import ly.mahd.app.content.PregnancyWeek
import ly.mahd.app.content.fatherStage
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.RollingText
import ly.mahd.app.ui.components.TrimesterMoons
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.ui.components.fadeAndResize
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.util.Format

/** The page gutter; the weeks slide edge to edge under it. */
private val GUTTER = 16.dp

/**
 * Week-by-week pregnancy guide. Opens on the current week. The weeks are pages that follow the finger (the next week
 * lies to the left in Arabic, like the next page of a book); the arrows move one week too.
 */
@Composable
fun PregnancyScreen(app: MahdApp, profile: Profile, nav: Navigator, initialWeek: Int?, standalone: Boolean) {
    val today = LocalToday.current
    val content = app.content.pregnancy
    val currentWeek = profile.dueDate?.let { PregnancyMath.gestationalAge(it, today).weeks }
        ?.coerceIn(1, content.lastWeek) ?: 1
    val pager = rememberPagerState(initialPage = (initialWeek ?: currentWeek).coerceIn(1, content.lastWeek) - 1) { content.lastWeek }
    // The week mostly on screen: the number and the moons follow it during a swipe.
    val week = pager.currentPage + 1
    val scope = rememberCoroutineScope()
    fun goTo(w: Int) {
        scope.launch { pager.animateScrollToPage(w.coerceIn(1, content.lastWeek) - 1, animationSpec = AppMotion.defaultSpatial()) }
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
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
                    onWeek = ::goTo,
                    onToday = { goTo(currentWeek) },
                    modifier = Modifier.padding(horizontal = GUTTER),
                )
            }
            item { TrimesterMoons(weeks = week.toFloat(), Modifier.padding(horizontal = GUTTER)) }
            if (!content.reviewed) item { DraftNotice(modifier = Modifier.padding(horizontal = GUTTER)) }
            item {
                HorizontalPager(
                    state = pager,
                    // Weeks are not all as long: the space eases to the new week's length.
                    modifier = Modifier.fillMaxWidth().animateContentSize(AppMotion.defaultSpatial()),
                    contentPadding = PaddingValues(horizontal = GUTTER),
                    pageSpacing = GUTTER,
                    verticalAlignment = Alignment.Top,
                    key = { it },
                ) { page -> WeekDetail(content.week(page + 1), content.fatherStage(page + 1)) }
            }
            item {
                WarningCard(
                    title = stringResource(R.string.danger_signs_title),
                    modifier = Modifier.padding(horizontal = GUTTER),
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
private fun WeekSwitcher(
    week: Int,
    lastWeek: Int,
    isCurrent: Boolean,
    onWeek: (Int) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { onWeek(week - 1) }, enabled = week > 1) {
                AppIcon(R.drawable.ic_chevron_back, stringResource(R.string.previous_week))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RollingText(
                    stringResource(R.string.pregnancy_week_number, week.toString()),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                RollingText(
                    stringResource(R.string.trimester_label, stringResource(trimesterName(PregnancyMath.trimester(week)))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = { onWeek(week + 1) }, enabled = week < lastWeek) {
                AppIcon(R.drawable.ic_chevron_forward, stringResource(R.string.next_week))
            }
        }
        AnimatedContent(
            targetState = isCurrent,
            transitionSpec = { fadeAndResize() },
            contentAlignment = Alignment.Center,
            label = "this-week",
        ) { current ->
            if (current) {
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
}

@Composable
private fun WeekDetail(w: PregnancyWeek, fatherStage: FatherStage?) {
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
            if (LocalParent.current == Parent.FATHER && fatherStage != null) {
                // The father: what his wife may be going through, and how he can help (instead of "your body" and the tip).
                WeekSection(stringResource(R.string.section_wife), fatherStage.wife, MaterialTheme.colorScheme.primary)
                WeekSection(stringResource(R.string.section_support), fatherStage.support, MaterialTheme.colorScheme.primary)
            } else {
                WeekSection(stringResource(R.string.section_mother), w.mother, MaterialTheme.colorScheme.primary)
                WeekSection(stringResource(R.string.section_tip), w.tip, MaterialTheme.colorScheme.primary)
            }
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
