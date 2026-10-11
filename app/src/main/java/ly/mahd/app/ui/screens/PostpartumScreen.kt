package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.PostpartumContent
import ly.mahd.app.data.BirthType
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.MoodCheck
import ly.mahd.app.logic.MoodResult
import ly.mahd.app.logic.PostpartumRules
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.AppFilterChip
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.GroupItem
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.SectionTitle
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.WarningCard
import ly.mahd.app.ui.theme.LocalSurfaces
import ly.mahd.app.util.Format
import java.time.LocalDate

/** The two choices, in the order shown. */
private val BIRTH_TYPES = listOf(BirthType.NATURAL, BirthType.CAESAREAN)

// ---------------------------------------------------------------------------------------------
// Home: the الأربعين card, from the day of birth to just after the six-week check-up.
// ---------------------------------------------------------------------------------------------

@Composable
fun PostpartumCard(app: MahdApp, child: Profile, nav: Navigator) {
    val birth = child.birthDate ?: return
    val today = LocalToday.current
    val content = app.content.postpartum
    if (!PostpartumRules.isActive(birth, today, content.lastDay)) return
    val day = PostpartumRules.day(birth, today)
    ListGroup {
        GroupItem(
            overline = stringResource(R.string.postpartum_title),
            headline = if (day <= PostpartumRules.DAYS) stringResource(R.string.postpartum_day, day.toString())
            else stringResource(R.string.postpartum_done),
            supporting = stringResource(R.string.postpartum_card_hint),
            leading = { MoonPhase(day / PostpartumRules.DAYS.toFloat(), size = 48.dp) },
            onClick = { nav.push(Routes.POSTPARTUM) },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The الأربعين screen
// ---------------------------------------------------------------------------------------------

@Composable
fun PostpartumScreen(app: MahdApp, child: Profile, onBack: () -> Unit) {
    val content = app.content.postpartum
    val today = LocalToday.current
    val father = LocalParent.current == Parent.FATHER
    val scope = rememberCoroutineScope()
    val birth = child.birthDate ?: return
    val day = PostpartumRules.day(birth, today).coerceIn(1, content.lastDay)
    val stage = content.stage(day)
    val type = child.birthType

    SubScreen(title = stringResource(R.string.postpartum_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The day, as a moon filling over the forty days.
            item {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoonPhase(day / PostpartumRules.DAYS.toFloat(), size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (day <= PostpartumRules.DAYS) stringResource(R.string.postpartum_day, day.toString())
                            else stringResource(R.string.postpartum_done),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(stage.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // How the baby was born decides part of what follows.
            item {
                ListGroup {
                    GroupRow(spacing = 12.dp) {
                        Text(stringResource(R.string.postpartum_birth_type), style = MaterialTheme.typography.titleMedium)
                        ConnectedChoice(
                            options = listOf(stringResource(R.string.birth_natural), stringResource(R.string.birth_caesarean)),
                            selected = BIRTH_TYPES.indexOf(type).takeIf { it >= 0 },
                            onSelect = { i -> scope.launch { app.repository.updateProfile(child.copy(birthType = BIRTH_TYPES[i])) } },
                            unselectedColor = LocalSurfaces.current.inset,
                        )
                        if (type == null) {
                            Text(
                                stringResource(R.string.postpartum_birth_type_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.postpartum_normal_title)) }
            item {
                val normal = stage.normal + when (type) {
                    BirthType.NATURAL -> stage.natural
                    BirthType.CAESAREAN -> stage.caesarean
                    null -> stage.natural + stage.caesarean
                }
                ListGroup { GroupRow { BulletList(normal) } }
            }

            item { SectionTitle(stringResource(R.string.postpartum_tips_title)) }
            item { ListGroup { GroupRow { BulletList(if (father) stage.tipsFather else stage.tips) } } }

            item {
                WarningCard(title = stringResource(R.string.postpartum_emergency_title)) {
                    val signs = content.emergencySigns + if (type != BirthType.NATURAL) content.emergencySignsCaesarean else emptyList()
                    BulletList(signs, bullet = MaterialTheme.colorScheme.error)
                }
            }
            item { SectionTitle(stringResource(R.string.postpartum_soon_title)) }
            item { ListGroup { GroupRow { BulletList(content.soonSigns, bullet = MaterialTheme.colorScheme.error) } } }

            item { SectionTitle(stringResource(R.string.postpartum_checkups_title)) }
            item { CheckupsGroup(content, birth, day) }

            item { SectionTitle(stringResource(R.string.postpartum_mood_title)) }
            item {
                when {
                    father -> ListGroup { GroupRow { BulletList(content.moodFather) } }
                    day < MoodCheck.FROM_DAY -> ListGroup {
                        GroupRow { Text(stringResource(R.string.mood_early), style = MaterialTheme.typography.bodyLarge) }
                    }
                    else -> MoodCheckCard(day)
                }
            }

            item { SectionTitle(stringResource(R.string.postpartum_customs_title)) }
            item {
                ListGroup {
                    content.customs.forEach { c ->
                        GroupRow(spacing = 4.dp) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Text(c.verdict, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium)
                            Text(c.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!content.reviewed) item { DraftNotice() }
        }
    }
}

/** The WHO's postnatal check-ups, with their dates and whether each one is now, ahead or past. */
@Composable
private fun CheckupsGroup(content: PostpartumContent, birth: LocalDate, day: Int) {
    ListGroup {
        content.checkups.forEach { c ->
            val (from, to) = PostpartumRules.window(c, birth)
            val status = when {
                day < c.fromDay -> R.string.checkup_upcoming
                day <= c.toDay -> R.string.checkup_now
                else -> R.string.checkup_past
            }
            GroupItem(
                overline = stringResource(status),
                headline = c.title,
                supporting = "${stringResource(R.string.checkup_dates, Format.date(from), Format.date(to))}\n${c.text}",
            )
        }
    }
}

/** Three questions, answered on the spot; nothing is saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodCheckCard(day: Int) {
    var interest by rememberSaveable { mutableStateOf<Int?>(null) }
    var low by rememberSaveable { mutableStateOf<Int?>(null) }
    var harm by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val answers = listOf(R.string.mood_a_0, R.string.mood_a_1, R.string.mood_a_2, R.string.mood_a_3).map { stringResource(it) }

    Column {
        ListGroup {
            GroupRow(spacing = 12.dp) {
                Text(stringResource(R.string.mood_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.mood_q_interest), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    answers.forEachIndexed { i, a -> AppFilterChip(selected = interest == i, onClick = { interest = i }, label = a) }
                }
                Text(stringResource(R.string.mood_q_low), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    answers.forEachIndexed { i, a -> AppFilterChip(selected = low == i, onClick = { low = i }, label = a) }
                }
                Text(stringResource(R.string.mood_q_harm), style = MaterialTheme.typography.titleMedium)
                ConnectedChoice(
                    options = listOf(stringResource(R.string.mood_yes), stringResource(R.string.mood_no)),
                    selected = when (harm) { true -> 0; false -> 1; null -> null },
                    onSelect = { harm = it == 0 },
                    unselectedColor = LocalSurfaces.current.inset,
                )
                Text(stringResource(R.string.mood_source), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val i = interest
        val l = low
        val h = harm
        // Thoughts of harm are answered at once, whatever the other answers.
        val result = when {
            h == true -> MoodResult.URGENT
            i != null && l != null && h != null -> MoodCheck.result(i, l, h, day)
            else -> null
        }
        when (result) {
            MoodResult.URGENT -> WarningCard(title = stringResource(R.string.mood_result_urgent_title), modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.mood_result_urgent), style = MaterialTheme.typography.bodyLarge)
            }
            null -> {}
            else -> AppCard(modifier = Modifier.padding(top = 12.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    stringResource(
                        when (result) {
                            MoodResult.OK -> R.string.mood_result_ok
                            MoodResult.BABY_BLUES -> R.string.mood_result_blues
                            else -> R.string.mood_result_doctor
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hospital bag (pregnancy, from week 28)
// ---------------------------------------------------------------------------------------------

/** From this week of pregnancy, Home shows the hospital bag. */
const val HOSPITAL_BAG_FROM_WEEK = 28

/** Home's row: how much of the bag is packed, as a moon. */
@Composable
fun HospitalBagRow(app: MahdApp, pregnancy: Profile, nav: Navigator) {
    val packed by app.prefs.hospitalBag.collectAsState()
    val all = app.content.hospitalBag.allIds
    val done = all.count { it in packed[pregnancy.id].orEmpty() }
    GroupItem(
        headline = stringResource(R.string.bag_title),
        supporting = stringResource(R.string.bag_progress, done.toString(), all.size.toString()),
        leading = { MoonPhase(done / all.size.toFloat(), size = 40.dp) },
        onClick = { nav.push(Routes.HOSPITAL_BAG) },
    )
}

@Composable
fun HospitalBagScreen(app: MahdApp, pregnancy: Profile, onBack: () -> Unit) {
    val packedByProfile by app.prefs.hospitalBag.collectAsState()
    val packed = packedByProfile[pregnancy.id].orEmpty()
    val bag = app.content.hospitalBag
    val res = LocalContext.current.resources
    SubScreen(title = stringResource(R.string.bag_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val done = bag.allIds.count { it in packed }
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoonPhase(done / bag.allIds.size.toFloat(), size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            res.getString(R.string.bag_progress, done.toString(), bag.allIds.size.toString()),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(stringResource(R.string.bag_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            bag.groups.forEach { group ->
                item(key = "title_${group.id}") { SectionTitle(group.title) }
                item(key = group.id) {
                    ListGroup {
                        group.items.forEach { bagItem ->
                            val isPacked = bagItem.id in packed
                            GroupRow(
                                onClick = { app.prefs.setBagItem(pregnancy.id, bagItem.id, !isPacked) },
                                contentPadding = PaddingValues(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isPacked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                    Text(
                                        bagItem.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isPacked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
