package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import ly.mahd.app.ui.components.AppFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.MilestoneDomain
import ly.mahd.app.content.MilestoneGroup
import ly.mahd.app.content.MilestoneItem
import ly.mahd.app.data.MilestoneAnswer
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.ui.profileName
import ly.mahd.app.ui.theme.domainAccent
import ly.mahd.app.ui.theme.LocalSurfaces
import ly.mahd.app.util.Format
import ly.mahd.app.util.ShareCard

/**
 * Milestone checklists by age (2 months to 4 years). For each skill the mother answers
 * "yes", "not yet" or "not sure". If a skill is "not yet" after its age, we gently suggest
 * talking to the doctor — the app never diagnoses.
 */
@Composable
fun MilestonesScreen(app: MahdApp, profile: Profile) {
    val today = LocalToday.current
    val res = LocalContext.current.resources
    val birth = profile.birthDate ?: return
    val content = app.content.milestones
    val loaded by remember(profile.id) { app.repository.milestoneAnswers(profile.id) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, today)
    val currentGroup = content.groupForAge(months)
    var groupId by rememberSaveable(profile.id) { mutableStateOf(currentGroup.id) }
    val group = content.groups.firstOrNull { it.id == groupId } ?: currentGroup
    val reached = months + 0.01 >= group.months

    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.share_card_tagline)
    val shareTitle = stringResource(R.string.share_card_title, profileName(profile))
    val chooserTitle = stringResource(R.string.share_chooser)
    val footer = stringResource(R.string.share_card_footer)

    // Null until the database answers, so the checklist does not show as empty for a moment.
    val answers = loaded ?: return
    val notYet = group.items.filter { answers[it.id] == MilestoneAnswer.NOT_YET }
    val yesTotal = group.items.count { answers[it.id] == MilestoneAnswer.YES }
    val countLine = stringResource(R.string.milestones_progress, yesTotal.toString(), group.items.size.toString())

    val chipsState = rememberLazyListState()
    LaunchedEffect(profile.id) {
        chipsState.scrollToItem(content.groups.indexOfFirst { it.id == currentGroup.id }.coerceAtLeast(0))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            val now = stringResource(R.string.milestones_now)
            LazyRow(state = chipsState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(content.groups, key = { it.id }) { g ->
                    AppFilterChip(
                        selected = g.id == group.id,
                        onClick = { groupId = g.id },
                        label = if (g.id == currentGroup.id) "${g.title} · $now" else g.title,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.milestones_by_age, group.title), style = MaterialTheme.typography.headlineMedium)
                if (ChildAge.usesCorrectedAge(birth, profile.weeksAtBirth, today)) {
                    Text(stringResource(R.string.milestones_corrected_note), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (reached) stringResource(R.string.milestones_intro)
                    else stringResource(R.string.milestones_future),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { DomainMoons(content.domains, group, answers) }
        if (!content.reviewed) item { DraftNotice() }
        content.domains.forEach { domain ->
            val domainItems = group.items.filter { it.domain == domain.id }
            if (domainItems.isNotEmpty()) {
                item(key = "domain-${group.id}-${domain.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            domain.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = domainAccent(domain.id),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        ListGroup {
                            domainItems.forEach { item ->
                                MilestoneRow(item, answers[item.id]) { answer ->
                                    scope.launch { app.repository.setMilestoneAnswer(profile.id, item.id, answer) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (yesTotal > 0) {
            item {
                ExpressiveButton(
                    tonal = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            runOrToast(context, R.string.share_failed) {
                                val card = ShareCard.Content(
                                    appName = appName,
                                    tagline = tagline,
                                    title = shareTitle,
                                    subtitle = "${Format.age(res, ChildAge.period(birth, today))} · ${group.title}",
                                    achievements = group.items.filter { answers[it.id] == MilestoneAnswer.YES }.map { it.text },
                                    countLine = countLine,
                                    footer = footer,
                                )
                                val bitmap = withContext(Dispatchers.Default) { ShareCard.render(card) }
                                ShareCard.share(context, bitmap, chooserTitle)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.share_achievements)) }
            }
        }
        if (reached && notYet.isNotEmpty()) {
            item {
                WarningCard(title = stringResource(R.string.milestones_talk_to_doctor_title)) {
                    Text(stringResource(R.string.milestones_talk_to_doctor), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (group.tips.isNotEmpty()) {
            item {
                AppCard {
                    Text(stringResource(R.string.milestones_tips_title), style = MaterialTheme.typography.titleLarge)
                    BulletList(group.tips, bullet = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item {
            AppCard {
                Text(stringResource(R.string.milestones_warnings_title), style = MaterialTheme.typography.titleLarge)
                BulletList(content.warnings, bullet = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(
                stringResource(R.string.milestones_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** One skill, and "yes / not yet / not sure" as a connected button group. Tapping the chosen answer clears it. */
@Composable
private fun MilestoneRow(item: MilestoneItem, answer: MilestoneAnswer?, onAnswer: (MilestoneAnswer?) -> Unit) {
    val order = listOf(MilestoneAnswer.YES, MilestoneAnswer.NOT_YET, MilestoneAnswer.NOT_SURE)
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    GroupRow(spacing = 12.dp) {
        Text(item.text, style = MaterialTheme.typography.bodyLarge)
        ConnectedChoice(
            options = listOf(
                stringResource(R.string.answer_yes),
                stringResource(R.string.answer_not_yet),
                stringResource(R.string.answer_not_sure),
            ),
            selected = answer?.let { order.indexOf(it) },
            onSelect = { i -> onAnswer(if (order[i] == answer) null else order[i]) },
            height = 44.dp,
            unselectedColor = LocalSurfaces.current.inset,
            selectedColors = { i ->
                when (order[i]) {
                    MilestoneAnswer.YES -> status.good to status.onGood
                    MilestoneAnswer.NOT_YET -> status.attentionContainer to status.onAttentionContainer
                    MilestoneAnswer.NOT_SURE -> colors.inverseSurface to colors.inverseOnSurface
                }
            },
        )
    }
}

/** One moon per area of development (social, language, thinking, movement) for this age. */
@Composable
private fun DomainMoons(domains: List<MilestoneDomain>, group: MilestoneGroup, answers: Map<String, MilestoneAnswer>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        domains.forEach { d ->
            val items = group.items.filter { it.domain == d.id }
            if (items.isNotEmpty()) {
                val yes = items.count { answers[it.id] == MilestoneAnswer.YES }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoonPhase(yes / items.size.toFloat(), size = 52.dp, lit = domainAccent(d.id))
                    Text(
                        stringResource(R.string.count_of_total, yes.toString(), items.size.toString()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        d.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
