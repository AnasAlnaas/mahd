package ly.mahd.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.MilestoneDomain
import ly.mahd.app.content.MilestoneGroup
import ly.mahd.app.content.tipsFor
import ly.mahd.app.data.MilestoneAnswer
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.MilestoneFocus
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.RollingText
import ly.mahd.app.ui.components.SparkBurst
import ly.mahd.app.ui.components.sharedAxisX
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.ui.components.groupRowShape
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.profileName
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.ui.theme.LocalSurfaces
import ly.mahd.app.ui.theme.domainAccent
import ly.mahd.app.util.Format
import ly.mahd.app.util.ShareCard

/** How long a skill answered "yes" stays where it was, for its little firework, before gliding down. */
private const val CELEBRATION_MS = 1000L

/**
 * The skills tab: the skills, and growth (weight, length, head) as its second part. Which part is shown lives in the
 * route ([Routes.GROWTH] opens the growth part), so Home's growth row and screen rotation keep it.
 */
@Composable
fun SkillsAndGrowth(app: MahdApp, profile: Profile, nav: Navigator, growth: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        ConnectedChoice(
            options = listOf(stringResource(R.string.tab_milestones), stringResource(R.string.tab_growth)),
            selected = if (growth) 1 else 0,
            onSelect = { nav.selectTab(if (it == 1) Routes.GROWTH else Routes.MILESTONES) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            height = 44.dp,
            // Each part in its own area colour: skills gold, growth teal.
            selectedColors = { i -> if (i == 0) colors.tertiary to colors.onTertiary else colors.secondary to colors.onSecondary },
        )
        // The two parts slide like neighbours: growth lies after the skills.
        AnimatedContent(
            targetState = growth,
            modifier = Modifier.weight(1f),
            transitionSpec = { sharedAxisX(forward = targetState) },
            label = "skills-growth",
        ) { showGrowth ->
            Box(Modifier.fillMaxSize()) {
                if (showGrowth) GrowthScreen(app, profile) else MilestonesScreen(app, profile)
            }
        }
    }
}

/**
 * Milestones (1 month to 4 years). Only the skills of the ages the child has reached are shown; the mother answers
 * "yes", "not yet" or "making progress". What the child does sinks to the end with a small firework, so the top
 * always shows what to watch now. If a skill is "not yet", we gently suggest telling the doctor — the app never
 * diagnoses.
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
    val currentGroup = content.reachedGroups(months).lastOrNull()
    val nextGroup = content.nextGroup(months)
    val corrected = ChildAge.usesCorrectedAge(birth, profile.weeksAtBirth, today)
    val parent = LocalParent.current

    // Skills just answered "yes": they stay in place until their firework has played.
    var held by remember(profile.id) { mutableStateOf(setOf<String>()) }

    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.share_card_tagline)
    val shareTitle = stringResource(R.string.share_card_title, profileName(profile))
    val chooserTitle = stringResource(R.string.share_chooser)
    val footer = stringResource(R.string.share_card_footer)

    // Null until the database answers, so the checklist does not show as empty for a moment.
    val answers = loaded ?: return

    if (currentGroup == null) {
        // Younger than the first list: a countdown (the moon fills as the day comes), and ways to help meanwhile.
        val first = nextGroup ?: return
        FirstListCountdown(first, months, corrected, content.reviewed)
        return
    }

    val entries = MilestoneFocus.revealed(content, months)
    val lists = MilestoneFocus.split(entries, answers, held)
    // A skill just answered "yes" shows as yes at once, before the database confirms it, so the firework plays on green.
    val shown = if (held.isEmpty()) answers else answers + held.associateWith { MilestoneAnswer.YES }
    val anyNotYet = entries.any { answers[it.item.id] == MilestoneAnswer.NOT_YET }
    val yesNow = currentGroup.items.filter { answers[it.id] == MilestoneAnswer.YES }

    fun answer(id: String, a: MilestoneAnswer?) {
        scope.launch { app.repository.setMilestoneAnswer(profile.id, id, a) }
        if (a == MilestoneAnswer.YES) {
            held = held + id
            scope.launch {
                delay(CELEBRATION_MS)
                held = held - id
            }
        }
    }

    // A list follows a moved item by its key, so when a skill moves between "watch now" and "mastered" the page
    // would jump after it. Instead the page stays where the mother is reading and only the skill glides away.
    val listState = rememberLazyListState()
    val lastPosition = remember { intArrayOf(0, 0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> lastPosition[0] = index; lastPosition[1] = offset }
    }
    val doneIds = lists.done.map { it.item.id }.toSet()
    val lastDoneIds = remember { arrayOfNulls<Set<String>>(1) }
    SideEffect {
        val before = lastDoneIds[0]
        if (before != null && before != doneIds) listState.requestScrollToItem(lastPosition[0], lastPosition[1])
        lastDoneIds[0] = doneIds
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.milestones_up_to, currentGroup.title), style = MaterialTheme.typography.headlineMedium)
                if (corrected) Text(stringResource(R.string.milestones_corrected_note), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.milestones_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                nextGroup?.let { next ->
                    Text(
                        stringResource(
                            R.string.milestones_next_list,
                            next.title,
                            Format.days(res, MilestoneFocus.daysUntil(next, months)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "moons") { DomainMoons(content.domains, entries, answers, Modifier.padding(bottom = 14.dp)) }
        if (!content.reviewed) item(key = "draft") { DraftNotice(modifier = Modifier.padding(bottom = 14.dp)) }

        sectionTitle("focus-title", res.getString(R.string.milestones_focus_title), lists.focus.size)
        if (lists.focus.isEmpty()) {
            item(key = "all-done") {
                AppCard(Modifier.animateItem(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(stringResource(R.string.milestones_all_done), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        skillRows(lists.focus, currentGroup, shown, ::answer)

        if (lists.done.isNotEmpty()) {
            sectionTitle("done-title", res.getString(R.string.milestones_done_title), lists.done.size)
            // Earlier ages may have been counted as done when the child was added: say they can be changed.
            if (lists.done.any { it.group.id != currentGroup.id }) {
                item(key = "done-hint") {
                    Text(
                        stringResource(R.string.milestones_done_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).animateItem(),
                    )
                }
            }
            skillRows(lists.done, currentGroup, shown, ::answer)
        }

        if (yesNow.isNotEmpty()) {
            item(key = "share") {
                ExpressiveButton(
                    tonal = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).animateItem(),
                    onClick = {
                        scope.launch {
                            runOrToast(context, R.string.share_failed) {
                                val card = ShareCard.Content(
                                    appName = appName,
                                    tagline = tagline,
                                    title = shareTitle,
                                    subtitle = "${Format.age(res, ChildAge.period(birth, today))} · ${currentGroup.title}",
                                    achievements = yesNow.map { it.text },
                                    countLine = res.getString(
                                        R.string.milestones_progress,
                                        yesNow.size.toString(),
                                        currentGroup.items.size.toString(),
                                    ),
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
        if (anyNotYet) {
            item(key = "doctor") {
                WarningCard(
                    title = stringResource(R.string.milestones_talk_to_doctor_title),
                    modifier = Modifier.padding(top = 16.dp).animateItem(),
                ) {
                    Text(stringResource(R.string.milestones_talk_to_doctor), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        val tips = currentGroup.tipsFor(parent)
        if (tips.isNotEmpty()) {
            item(key = "tips") { TipsCard(tips, Modifier.padding(top = 16.dp).animateItem()) }
        }
        item(key = "warnings") {
            AppCard(Modifier.padding(top = 16.dp).animateItem()) {
                Text(stringResource(R.string.milestones_warnings_title), style = MaterialTheme.typography.titleLarge)
                BulletList(content.warnings, bullet = MaterialTheme.colorScheme.error)
            }
        }
        item(key = "source") {
            Text(
                stringResource(R.string.milestones_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp).animateItem(),
            )
        }
    }
}

/** A section's name and how many skills it holds; the count rolls as skills move between the sections. */
private fun LazyListScope.sectionTitle(key: String, title: String, count: Int) {
    item(key = key) {
        val style = MaterialTheme.typography.titleSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            Modifier.animateItem().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = style, color = color)
            Text("·", style = style, color = color)
            RollingText(count.toString(), style = style, color = color)
        }
    }
}

/**
 * The skills of one section, as one grouped list whose rows glide to their new place when an answer moves them.
 * Each row is its own list item (so it can move), and takes the group's outer corners when it is first or last.
 */
private fun LazyListScope.skillRows(
    rows: List<MilestoneFocus.Entry>,
    currentGroup: MilestoneGroup,
    answers: Map<String, MilestoneAnswer>,
    onAnswer: (String, MilestoneAnswer?) -> Unit,
) {
    itemsIndexed(rows, key = { _, e -> e.item.id }) { i, e ->
        MilestoneRow(
            entry = e,
            showAge = e.group.id != currentGroup.id,
            answer = answers[e.item.id],
            shape = groupRowShape(i, rows.size),
            modifier = Modifier.animateItem(
                fadeInSpec = AppMotion.defaultEffects(),
                placementSpec = AppMotion.slowSpatial(),
                fadeOutSpec = AppMotion.defaultEffects(),
            ),
            onAnswer = { onAnswer(e.item.id, it) },
        )
    }
}

/**
 * One skill, and "yes / not yet / making progress" as a connected button group. Tapping the chosen answer clears it.
 * "Yes" sets off a small firework from the button itself.
 */
@Composable
private fun MilestoneRow(
    entry: MilestoneFocus.Entry,
    showAge: Boolean,
    answer: MilestoneAnswer?,
    shape: RoundedCornerShape,
    modifier: Modifier,
    onAnswer: (MilestoneAnswer?) -> Unit,
) {
    val order = listOf(MilestoneAnswer.YES, MilestoneAnswer.NOT_YET, MilestoneAnswer.PROGRESS)
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var bursts by remember { mutableIntStateOf(0) }
    // Where the row and its "yes" button are, so the sparks start from the button (plain holders, not state).
    val rowAt = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val yesAt = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(modifier.onGloballyPositioned { rowAt[0] = it }) {
        GroupRow(spacing = 12.dp, shape = shape) {
            Column {
                if (showAge) {
                    Text(
                        stringResource(R.string.milestones_from_age, entry.group.title),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(entry.item.text, style = MaterialTheme.typography.bodyLarge)
            }
            ConnectedChoice(
                options = listOf(
                    stringResource(R.string.answer_yes),
                    stringResource(R.string.answer_not_yet),
                    stringResource(R.string.answer_progress),
                ),
                selected = answer?.let { order.indexOf(it) },
                onSelect = { i ->
                    val picked = order[i]
                    if (picked == answer) {
                        onAnswer(null)
                    } else {
                        if (picked == MilestoneAnswer.YES) {
                            bursts++
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onAnswer(picked)
                    }
                },
                height = 44.dp,
                unselectedColor = LocalSurfaces.current.inset,
                selectedColors = { i ->
                    when (order[i]) {
                        MilestoneAnswer.YES -> status.good to status.onGood
                        MilestoneAnswer.NOT_YET -> colors.inverseSurface to colors.inverseOnSurface
                        MilestoneAnswer.PROGRESS -> colors.tertiaryContainer to colors.onTertiaryContainer
                    }
                },
                segmentModifier = { i -> if (i == 0) Modifier.onGloballyPositioned { yesAt[0] = it } else Modifier },
            )
        }
        SparkBurst(
            trigger = bursts,
            origin = {
                val row = rowAt[0]
                val yes = yesAt[0]
                if (row != null && yes != null && row.isAttached && yes.isAttached) {
                    row.localPositionOf(yes, Offset(yes.size.width / 2f, yes.size.height / 2f))
                } else {
                    null
                }
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** One moon per area of development (social, language, thinking, movement), over every skill shown. */
@Composable
private fun DomainMoons(
    domains: List<MilestoneDomain>,
    entries: List<MilestoneFocus.Entry>,
    answers: Map<String, MilestoneAnswer>,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        domains.forEach { d ->
            val items = entries.filter { it.item.domain == d.id }
            if (items.isNotEmpty()) {
                val yes = items.count { answers[it.item.id] == MilestoneAnswer.YES }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoonPhase(yes / items.size.toFloat(), size = 52.dp, lit = domainAccent(d.id))
                    RollingText(
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

@Composable
private fun TipsCard(tips: List<String>, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(stringResource(R.string.milestones_tips_title), style = MaterialTheme.typography.titleLarge)
        BulletList(tips, bullet = MaterialTheme.colorScheme.tertiary)
    }
}

/** Before the first list: the moon fills as its day comes near, with ways to help the baby until then. */
@Composable
private fun FirstListCountdown(first: MilestoneGroup, months: Double, corrected: Boolean, reviewed: Boolean) {
    val res = LocalContext.current.resources
    val parent = LocalParent.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoonPhase((months / first.months).toFloat(), size = 64.dp, lit = MaterialTheme.colorScheme.tertiary)
                Text(
                    stringResource(R.string.milestones_first_list_title, first.title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.milestones_first_list_in, Format.days(res, MilestoneFocus.daysUntil(first, months))),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (corrected) Text(stringResource(R.string.milestones_corrected_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val tips = first.tipsFor(parent)
        if (tips.isNotEmpty()) item { TipsCard(tips) }
        if (!reviewed) item { DraftNotice() }
    }
}
