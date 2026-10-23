package ly.mahd.app.ui.screens

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.Exercise
import ly.mahd.app.content.ExerciseStage
import ly.mahd.app.content.ExercisesContent
import ly.mahd.app.data.BirthType
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.FadeInImage
import ly.mahd.app.ui.components.sharedAxisX
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

/** Home's row: the mother's exercises, for the pregnancy or for after the birth. */
@Composable
fun ExercisesRow(pregnancy: Boolean, nav: Navigator) {
    GroupItem(
        headline = stringResource(R.string.exercises_title),
        supporting = stringResource(if (pregnancy) R.string.exercises_row_pregnancy else R.string.exercises_row_postpartum),
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
                ExercisesPart(content, afterBirth = shownIndex == 1, type, week, father, pictures)
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
) {
    val part = if (afterBirth) content.postpartum else content.pregnancy
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!content.reviewed) item { DraftNotice() }

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
