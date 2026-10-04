package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.MilestoneItem
import ly.mahd.app.data.MilestoneAnswer
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.content.MilestoneDomain
import ly.mahd.app.content.MilestoneGroup
import ly.mahd.app.ui.components.ProgressRing
import ly.mahd.app.ui.profileName
import ly.mahd.app.ui.theme.domainAccent
import ly.mahd.app.util.ShareCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.util.Format

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
    val answers by remember(profile.id) { app.repository.milestoneAnswers(profile.id) }.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()

    val months = ChildAge.referenceMonths(birth, profile.weeksAtBirth, today)
    val currentGroup = content.groupForAge(months)
    var groupId by rememberSaveable(profile.id) { mutableStateOf(currentGroup.id) }
    val group = content.groups.firstOrNull { it.id == groupId } ?: currentGroup
    val reached = months + 0.01 >= group.months
    val notYet = group.items.filter { answers[it.id] == MilestoneAnswer.NOT_YET }

    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.share_card_tagline)
    val shareTitle = stringResource(R.string.share_card_title, profileName(profile))
    val chooserTitle = stringResource(R.string.share_chooser)
    val footer = stringResource(R.string.share_card_footer)
    val yesTotal = group.items.count { answers[it.id] == MilestoneAnswer.YES }
    val countLine = stringResource(R.string.milestones_progress, yesTotal.toString(), group.items.size.toString())

    val chipsState = rememberLazyListState()
    LaunchedEffect(profile.id) {
        chipsState.scrollToItem(content.groups.indexOfFirst { it.id == currentGroup.id }.coerceAtLeast(0))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LazyRow(state = chipsState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(content.groups, key = { it.id }) { g ->
                    FilterChip(
                        selected = g.id == group.id,
                        onClick = { groupId = g.id },
                        label = { Text(g.title + if (g.id == currentGroup.id) " ⭐" else "") },
                    )
                }
            }
        }
        if (!content.reviewed) item { DraftNotice() }
        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(stringResource(R.string.milestones_by_age, group.title), style = MaterialTheme.typography.titleLarge)
                if (ChildAge.usesCorrectedAge(birth, profile.weeksAtBirth, today)) {
                    Text(stringResource(R.string.milestones_corrected_note), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (reached) stringResource(R.string.milestones_intro)
                    else stringResource(R.string.milestones_future),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (yesTotal > 0) {
                    OutlinedButton(onClick = {
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
                    }) { Text("📤  " + stringResource(R.string.share_achievements)) }
                }
            }
        }
        item { DomainRings(content.domains, group, answers) }
        content.domains.forEach { domain ->
            val domainItems = group.items.filter { it.domain == domain.id }
            if (domainItems.isNotEmpty()) {
                item(key = "domain-${group.id}-${domain.id}") {
                    Text(
                        "${domain.emoji}  ${domain.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = domainAccent(domain.id),
                    )
                }
                items(domainItems, key = { it.id }) { item ->
                    MilestoneRow(item, answers[item.id], domainAccent(domain.id)) { answer ->
                        scope.launch { app.repository.setMilestoneAnswer(profile.id, item.id, answer) }
                    }
                }
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
                AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("💡  " + stringResource(R.string.milestones_tips_title), style = MaterialTheme.typography.titleMedium)
                    BulletList(group.tips)
                }
            }
        }
        item {
            AppCard {
                Text("🚩  " + stringResource(R.string.milestones_warnings_title), style = MaterialTheme.typography.titleMedium)
                BulletList(content.warnings)
            }
        }
        item {
            Text(
                stringResource(R.string.milestones_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MilestoneRow(item: MilestoneItem, answer: MilestoneAnswer?, accent: Color, onAnswer: (MilestoneAnswer?) -> Unit) {
    AppCard(accent = accent) {
        Text(item.text, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnswerChip(R.string.answer_yes, answer == MilestoneAnswer.YES) {
                onAnswer(if (answer == MilestoneAnswer.YES) null else MilestoneAnswer.YES)
            }
            AnswerChip(R.string.answer_not_yet, answer == MilestoneAnswer.NOT_YET) {
                onAnswer(if (answer == MilestoneAnswer.NOT_YET) null else MilestoneAnswer.NOT_YET)
            }
            AnswerChip(R.string.answer_not_sure, answer == MilestoneAnswer.NOT_SURE) {
                onAnswer(if (answer == MilestoneAnswer.NOT_SURE) null else MilestoneAnswer.NOT_SURE)
            }
        }
    }
}

@Composable
private fun AnswerChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

/** One small ring per area of development (social, language, thinking, movement) for this age. */
@Composable
private fun DomainRings(domains: List<MilestoneDomain>, group: MilestoneGroup, answers: Map<String, MilestoneAnswer>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        domains.forEach { d ->
            val items = group.items.filter { it.domain == d.id }
            if (items.isNotEmpty()) {
                val yes = items.count { answers[it.id] == MilestoneAnswer.YES }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProgressRing(progress = yes / items.size.toFloat(), size = 60.dp, stroke = 6.dp, color = domainAccent(d.id)) {
                        Text(d.emoji, fontSize = 22.sp)
                    }
                    Text("$yes/${items.size}", style = MaterialTheme.typography.labelLarge)
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
