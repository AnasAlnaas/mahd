package ly.mahd.app.ui.screens

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.Exercise
import ly.mahd.app.content.ExerciseStage
import ly.mahd.app.content.ExercisesContent
import ly.mahd.app.data.BirthType
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ExerciseRoutine
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.FadeInImage
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.RollingText
import ly.mahd.app.ui.components.SparkBurst
import ly.mahd.app.ui.components.fadeAndResize
import ly.mahd.app.ui.components.sharedAxisX
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.util.Format
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import ly.mahd.app.ui.components.GroupItem
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SectionTitle
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.WarningCard

/** Home shows the exercises row to the mother of a child younger than this (days). */
const val EXERCISES_UNTIL_DAY = 183

/** Exercise pictures: assets/exercises/<name>.webp (see tools/prepare_exercise_pictures.ps1). */
private const val PICTURE_DIR = "exercises"

/**
 * Pictures sit on a white panel in both themes: they are drawn on white (the owner's AI pictures keep their white
 * background), so on the light page they blend into the row, and on the dark page they read as one clean card.
 */
private val PICTURE_PANEL = Color.White

private val PICTURE_SHAPE = RoundedCornerShape(16.dp)

/** How long each pose holds before the next one (ms), and how long the fade between them takes. */
private const val POSE_HOLD_MS = 1100L
private const val POSE_FADE_MS = 450

/**
 * Home's row: the mother's exercises, for the pregnancy or for after the birth. With a daily program, [program] is
 * today's (done, of how many): the row then shows it as a moon.
 */
@Composable
fun ExercisesRow(pregnancy: Boolean, nav: Navigator, program: Pair<Int, Int>? = null) {
    GroupItem(
        headline = stringResource(R.string.exercises_title),
        supporting = if (program != null) {
            stringResource(R.string.routine_home, program.first.toString(), program.second.toString())
        } else {
            stringResource(if (pregnancy) R.string.exercises_row_pregnancy else R.string.exercises_row_postpartum)
        },
        leading = program?.let { (done, total) -> { MoonPhase(done / total.toFloat(), size = 40.dp) } },
        onClick = { nav.push(Routes.EXERCISES) },
    )
}

/**
 * Exercises for the mother: by trimester in pregnancy (her current one first), and for healing after the birth
 * (natural or caesarean). Opens on the part that fits the selected profile; the other part is one tap away.
 */
@Composable
fun ExercisesScreen(app: MahdApp, profile: Profile, onBack: () -> Unit) {
    val content = app.content.exercises
    val today = LocalToday.current
    val father = LocalParent.current == Parent.FATHER
    val assets = LocalContext.current.assets
    // The pictures added so far; the others are simply not shown yet.
    val pictures = remember {
        assets.list(PICTURE_DIR).orEmpty().filter { it.endsWith(".webp") }.map { it.removeSuffix(".webp") }.toSet()
    }
    var partIndex by rememberSaveable { mutableIntStateOf(if (profile.isPregnancy) 0 else 1) }
    val type = if (profile.isChild) profile.birthType else null
    val week = profile.dueDate?.takeIf { profile.isPregnancy }?.let { PregnancyMath.gestationalAge(it, today).weeks }
    // The daily program of the current stage (the third trimester), for the mother to tick (she does the exercises).
    val routine = remember(week) { week?.let(content::routineFor).orEmpty() }
    val log by remember(profile.id) { app.repository.exerciseLog(profile.id) }.collectAsState(initial = null)
    val program: (@Composable () -> Unit)? = log?.takeIf { routine.isNotEmpty() && !father }?.let { ticks ->
        { DailyProgram(app, profile, routine, ticks, pictures) }
    }

    SubScreen(title = stringResource(R.string.exercises_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ConnectedChoice(
                options = listOf(stringResource(R.string.exercises_part_pregnancy), stringResource(R.string.exercises_part_postpartum)),
                selected = partIndex,
                onSelect = { partIndex = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            // The two parts slide like neighbours: after the birth lies after the pregnancy.
            AnimatedContent(
                targetState = partIndex,
                modifier = Modifier.weight(1f),
                transitionSpec = { sharedAxisX(forward = targetState > initialState) },
                label = "exercises-part",
            ) { shownIndex ->
                ExercisesPart(content, afterBirth = shownIndex == 1, type, week, father, pictures, program.takeIf { shownIndex == 0 })
            }
        }
    }
}

/** One part of the exercises: in pregnancy (by trimester, the current one first) or after the birth. */
@Composable
private fun ExercisesPart(
    content: ExercisesContent,
    afterBirth: Boolean,
    type: BirthType?,
    week: Int?,
    father: Boolean,
    pictures: Set<String>,
    /** The daily program, shown first (null when there is none). */
    program: (@Composable () -> Unit)?,
) {
    val part = if (afterBirth) content.postpartum else content.pregnancy
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!content.reviewed) item { DraftNotice() }
        program?.let { item(key = "program") { it() } }

        item { SectionTitle(stringResource(R.string.exercises_intro_title)) }
        item { ListGroup { GroupRow { BulletList(part.intro) } } }

        if (afterBirth) {
            item { SectionTitle(stringResource(R.string.exercises_birth_title)) }
            item {
                ListGroup {
                    // Both kinds of birth when the mother has not said which.
                    if (type != BirthType.CAESAREAN) {
                        GroupRow(spacing = 8.dp) {
                            Text(stringResource(R.string.birth_natural_long), style = MaterialTheme.typography.titleMedium)
                            BulletList(part.natural)
                        }
                    }
                    if (type != BirthType.NATURAL) {
                        GroupRow(spacing = 8.dp) {
                            Text(stringResource(R.string.birth_caesarean_long), style = MaterialTheme.typography.titleMedium)
                            BulletList(part.caesarean)
                        }
                    }
                }
            }
        }

        part.stagesFor(if (afterBirth) null else week).forEach { stage ->
            val now = !afterBirth && week != null && stage.covers(week)
            item(key = "title_${stage.id}") { StageHeader(stage, now) }
            item(key = stage.id) {
                ListGroup {
                    stage.exercises.forEach { ExerciseRow(it, pictures, showCaesarean = afterBirth && type != BirthType.NATURAL) }
                }
            }
        }

        if (father && part.father.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.exercises_father_title)) }
            item { ListGroup { GroupRow { BulletList(part.father) } } }
        }

        item { SectionTitle(stringResource(R.string.exercises_avoid_title)) }
        item { ListGroup { GroupRow { BulletList(part.avoid) } } }

        item {
            WarningCard(title = stringResource(R.string.exercises_stop_title)) {
                BulletList(part.stopSigns, bullet = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.exercises_source, part.source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content.pictureCredit?.let {
                    Text(
                        stringResource(R.string.exercises_picture_credit, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The daily program: a moon that fills as the day's exercises are ticked (with the small firework when all are done),
 * the pregnancy week as seven small moons (an earlier day of the week can be opened to tick a forgotten exercise),
 * and the exercises in order, each a row to tick.
 */
@Composable
private fun DailyProgram(
    app: MahdApp,
    profile: Profile,
    routine: List<Pair<Exercise, String>>,
    log: Map<LocalDate, Set<String>>,
    pictures: Set<String>,
) {
    val today = LocalToday.current
    val due = profile.dueDate ?: return
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val days = remember(due, today) { ExerciseRoutine.weekDays(due, today) }
    // The day being ticked: today, unless the mother opened an earlier day of the week.
    var chosen by rememberSaveable(today) { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(chosen).takeIf { it in days && ExerciseRoutine.canTick(it, today) } ?: today
    val ids = routine.map { it.first.id }
    val done = log[day].orEmpty()
    val count = ExerciseRoutine.doneCount(done, ids)
    // The exercise to do now: the first one not yet done (the program is done in order).
    val current = ExerciseRoutine.current(done, ids)
    var bursts by remember { mutableIntStateOf(0) }
    // Goes up with each tick, so the next exercise's pictures are scrolled into view (not when the page just opens).
    var advances by remember { mutableIntStateOf(0) }
    val moonSize = 56.dp
    val moonCenter = with(LocalDensity.current) { moonSize.toPx() / 2 }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(moonSize)) {
                MoonPhase(ExerciseRoutine.progress(done, ids), size = moonSize)
                SparkBurst(bursts, origin = { Offset(moonCenter, moonCenter) }, Modifier.matchParentSize())
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (day == today) stringResource(R.string.routine_title)
                    else stringResource(R.string.routine_day_title, Format.dayMonth(day)),
                    style = MaterialTheme.typography.titleLarge,
                )
                RollingText(
                    stringResource(R.string.count_of_total, count.toString(), ids.size.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        WeekStrip(days, today, day, log, ids) { chosen = it.toString() }
        ListGroup {
            routine.forEachIndexed { i, (exercise, dose) ->
                val isDone = exercise.id in done
                ProgramStep(
                    number = i + 1,
                    exercise = exercise,
                    dose = dose,
                    done = isDone,
                    current = i == current,
                    locked = i > current,
                    pictures = pictures,
                    advances = advances,
                    // In order: the current exercise is ticked, the one just before it can be opened again.
                    onClick = when (i) {
                        current -> ({
                            scope.launch { app.repository.setExerciseDone(profile.id, day, exercise.id, true) }
                            advances++
                            // The last exercise of the day: a small celebration.
                            if (count + 1 == ids.size) {
                                bursts++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        })
                        current - 1 -> ({ scope.launch { app.repository.setExerciseDone(profile.id, day, exercise.id, false) } })
                        else -> null
                    },
                )
            }
        }
        AnimatedContent(targetState = count == ids.size, transitionSpec = { fadeAndResize() }, label = "program-note") { all ->
            Text(
                stringResource(if (all) R.string.routine_all_done else R.string.routine_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (all) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Text(
            stringResource(R.string.routine_missed),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/**
 * One exercise of the daily program. The [current] one opens to show its pictures (two poses take turns, like in the
 * full list) and steps; ticked, they shrink away and the next exercise's pictures rise into place. A [locked] one
 * (later in the order) is quiet and cannot be ticked yet. [onClick] is null when the row does nothing.
 */
@Composable
private fun ProgramStep(
    number: Int,
    exercise: Exercise,
    dose: String,
    done: Boolean,
    current: Boolean,
    locked: Boolean,
    pictures: Set<String>,
    advances: Int,
    onClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val titleColor by animateColorAsState(
        when {
            current -> colors.onSurface
            done -> colors.onSurfaceVariant
            else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
        },
        AppMotion.slowEffects(),
        label = "step",
    )
    GroupRow(onClick = onClick, contentPadding = PaddingValues(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The whole row is the button; the checkbox only shows the state (one target for TalkBack).
            Checkbox(checked = done, onCheckedChange = null, enabled = !locked, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    // The number in its own text, so it stays at the start of the line in Arabic.
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (locked) titleColor else colors.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(exercise.title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                }
                Text(dose, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        AnimatedVisibility(
            visible = current,
            enter = expandVertically(AppMotion.slowSpatial(), expandFrom = Alignment.Top) +
                fadeIn(tween(420, delayMillis = 120)) +
                scaleIn(AppMotion.slowSpatial(), initialScale = 0.85f) +
                slideInVertically(AppMotion.slowSpatial()) { it / 4 },
            exit = shrinkVertically(AppMotion.defaultSpatial(), shrinkTowards = Alignment.Top) +
                fadeOut(AppMotion.defaultEffects()) +
                scaleOut(AppMotion.defaultSpatial(), targetScale = 0.6f),
        ) {
            val requester = remember { BringIntoViewRequester() }
            // After a tick, scroll so the new exercise's pictures are on screen once they have opened.
            LaunchedEffect(Unit) {
                if (advances > 0) {
                    delay(STEP_OPEN_MS)
                    requester.bringIntoView()
                }
            }
            Column(
                Modifier.bringIntoViewRequester(requester).padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExercisePictures(exercise, pictures)
                exercise.steps.forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** About how long the next exercise's pictures take to open, before the page scrolls to show them. */
private const val STEP_OPEN_MS = 450L

/**
 * The pregnancy week as seven small moons, each lit by that day's share of the program. A day that has come can be
 * opened (to tick a forgotten exercise); days still ahead are quiet.
 */
@Composable
private fun WeekStrip(
    days: List<LocalDate>,
    today: LocalDate,
    selected: LocalDate,
    log: Map<LocalDate, Set<String>>,
    ids: List<String>,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val arabic = remember { Locale.forLanguageTag("ar") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { d ->
            val open = ExerciseRoutine.canTick(d, today)
            val background by animateColorAsState(
                if (d == selected) colors.primaryContainer else Color.Transparent,
                AppMotion.defaultEffects(),
                label = "day",
            )
            val label = if (open) colors.onSurface else colors.onSurfaceVariant
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .clickable(enabled = open, onClickLabel = Format.dayMonth(d)) { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(d.dayOfWeek.getDisplayName(DateTextStyle.NARROW, arabic), style = MaterialTheme.typography.labelMedium, color = label)
                MoonPhase(
                    ExerciseRoutine.progress(log[d], ids),
                    size = 26.dp,
                    lit = if (open) colors.primary else colors.outline,
                )
                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = label)
            }
        }
    }
}

/** A stage's name ("· now" for the mother's current trimester) and what it is about. */
@Composable
private fun StageHeader(stage: ExerciseStage, now: Boolean) {
    Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (now) "${stage.title} · ${stringResource(R.string.exercises_stage_now)}" else stage.title,
            style = MaterialTheme.typography.titleLarge,
            color = if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(stage.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One exercise: its name, its pictures (the poses taking turns), the steps numbered, how often, and a warning. */
@Composable
private fun ExerciseRow(exercise: Exercise, pictures: Set<String>, showCaesarean: Boolean) {
    GroupRow(spacing = 10.dp) {
        Text(exercise.title, style = MaterialTheme.typography.titleMedium)
        ExercisePictures(exercise, pictures)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            exercise.steps.forEachIndexed { i, step ->
                Row {
                    // The number in its own text, so it stays at the start of the line in Arabic.
                    Text(
                        (i + 1).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        exercise.dose?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        exercise.warning?.let {
            Text(
                "${stringResource(R.string.exercises_warning_label)}: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCaesarean) {
            exercise.caesarean?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * The exercise's own poses (`<id>`, `<id>_1`, `<id>_2`) play in one frame, taking turns so they read as one movement;
 * any other picture (the shared pelvic floor diagram) sits beside them.
 */
@Composable
private fun ExercisePictures(exercise: Exercise, pictures: Set<String>) {
    val shown = exercise.images.filter { it in pictures }
    if (shown.isEmpty()) return
    val (poses, others) = shown.partition { it == exercise.id || it.startsWith("${exercise.id}_") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (poses.isNotEmpty()) PoseFrame(poses, exercise.title, Modifier.weight(1f))
        others.forEach { PicturePanel(rememberPicture(it), exercise.title, Modifier.weight(1f)) }
    }
}

/**
 * Two poses in one frame: each one holds for a moment, then fades into the other, like a tiny flip-book (the owner's
 * request, an exception to "nothing moves on its own"). With the phone's animations turned off they stay still, and a
 * tap shows the next pose; a tap also starts the count again.
 */
@Composable
private fun PoseFrame(poses: List<String>, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmaps = poses.map { rememberPicture(it) }
    var index by remember(poses) { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    val moving = poses.size > 1
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    if (moving && animationsOn) {
        LaunchedEffect(poses, taps) {
            while (true) {
                delay(POSE_HOLD_MS)
                index = (index + 1) % poses.size
            }
        }
    }
    val nextPose = stringResource(R.string.exercises_next_pose)
    val tap = if (moving) {
        Modifier.clickable(onClickLabel = nextPose) {
            index = (index + 1) % poses.size
            taps++
        }
    } else {
        Modifier
    }
    Crossfade(
        targetState = index,
        animationSpec = tween(POSE_FADE_MS),
        modifier = modifier.clip(PICTURE_SHAPE).then(tap),
        label = "pose",
    ) { i -> PicturePanel(bitmaps[i], description, Modifier.fillMaxWidth()) }
}

/** A picture read from the app's files off the main thread (null while it loads). */
@Composable
private fun rememberPicture(name: String): ImageBitmap? {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching { assets.open("$PICTURE_DIR/$name.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

/** One picture on its panel; the panel keeps its place while the picture loads. */
@Composable
private fun PicturePanel(bitmap: ImageBitmap?, description: String, modifier: Modifier) {
    Box(modifier.aspectRatio(4f / 3f).clip(PICTURE_SHAPE).background(PICTURE_PANEL)) {
        FadeInImage(bitmap, contentDescription = description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}
