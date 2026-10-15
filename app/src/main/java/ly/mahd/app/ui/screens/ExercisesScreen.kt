package ly.mahd.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.Exercise
import ly.mahd.app.content.ExerciseStage
import ly.mahd.app.data.BirthType
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.ConnectedChoice
import ly.mahd.app.ui.components.DraftNotice
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
 * Pictures sit on a light panel in both themes: drawn for a light page, they may keep a white background,
 * which would show as a white box on the dark page.
 */
private val PICTURE_PANEL = Color(0xFFF6F1EE)

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
    val afterBirth = partIndex == 1
    val part = if (afterBirth) content.postpartum else content.pregnancy
    val type = if (profile.isChild) profile.birthType else null
    val week = profile.dueDate?.takeIf { profile.isPregnancy }?.let { PregnancyMath.gestationalAge(it, today).weeks }

    SubScreen(title = stringResource(R.string.exercises_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ConnectedChoice(
                    options = listOf(stringResource(R.string.exercises_part_pregnancy), stringResource(R.string.exercises_part_postpartum)),
                    selected = partIndex,
                    onSelect = { partIndex = it },
                )
            }
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

/** One exercise: its name, its pictures (start and end pose side by side), the steps numbered, how often, and a warning. */
@Composable
private fun ExerciseRow(exercise: Exercise, pictures: Set<String>, showCaesarean: Boolean) {
    GroupRow(spacing = 10.dp) {
        Text(exercise.title, style = MaterialTheme.typography.titleMedium)
        val shown = exercise.images.filter { it in pictures }
        if (shown.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { ExercisePicture(it, exercise.title, Modifier.weight(1f)) }
            }
        }
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

/** One pose, read from the app's files off the main thread; the panel keeps its place while it loads. */
@Composable
private fun ExercisePicture(name: String, description: String, modifier: Modifier) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching { assets.open("$PICTURE_DIR/$name.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(PICTURE_PANEL)) {
        bitmap?.let { Image(it, contentDescription = description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}
