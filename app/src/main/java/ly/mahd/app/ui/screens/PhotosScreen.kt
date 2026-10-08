package ly.mahd.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.Photo
import ly.mahd.app.data.PhotoStore
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.PhotoRules
import ly.mahd.app.ui.Navigator
import ly.mahd.app.ui.Routes
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.theme.LocalSurfaces
import ly.mahd.app.ui.components.ConfirmDialog
import ly.mahd.app.ui.components.DatePickerPopup
import ly.mahd.app.ui.components.EmptyState
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.profileName
import ly.mahd.app.util.Format
import ly.mahd.app.util.ShareCard
import java.io.File
import java.io.InputStream
import java.time.LocalDate

/** How many pictures can be picked from the gallery at once. */
private const val MAX_PICK = 20

/** The child's age on the day of a photo, short enough for a label ("4 أشهر", "سنة و2 شهر"). */
private fun photoAge(res: Resources, birth: LocalDate, date: LocalDate): String =
    if (!date.isAfter(birth)) res.getString(R.string.photo_birth_day) else Format.shortAge(res, ChildAge.period(birth, date))

/**
 * One child's photo album: take a photo with the phone's camera or pick some from the gallery (neither needs a
 * permission), see them newest first with the child's age on each, compare two side by side, save copies to the
 * phone's gallery.
 */
@Composable
fun PhotosScreen(app: MahdApp, profile: Profile, nav: Navigator) {
    val context = LocalContext.current
    val res = context.resources
    val today = LocalToday.current
    val birth = profile.birthDate ?: return
    val scope = rememberCoroutineScope()
    val loaded by remember(profile.id) { app.repository.photos(profile.id) }.collectAsState(initial = null)
    var busy by remember { mutableStateOf(false) }

    // Copies pictures into the album. Runs in the app's scope, so leaving the screen does not lose half of them.
    // Camera photos are dated today; gallery photos by the date inside the picture (or today), between birth and today.
    fun add(sources: List<() -> InputStream?>, fromCamera: Boolean, afterwards: () -> Unit = {}) {
        if (sources.isEmpty()) return
        busy = true
        app.scope.launch {
            val imported = withContext(Dispatchers.IO) {
                sources.mapNotNull { open -> runCatching { app.photos.import(open) }.getOrNull() }.also { afterwards() }
            }
            val photos = imported.map { picture ->
                val taken = if (fromCamera) today else picture.dateTaken ?: today
                Photo(0, profile.id, PhotoRules.clampDate(taken, birth, today), picture.file)
            }
            val saved = photos.isEmpty() || runCatching { app.repository.addPhotos(photos) }.isSuccess
            if (!saved || imported.size < sources.size) showToast(context, R.string.photos_add_failed)
            busy = false
        }
    }

    val cameraFile = remember { File(context.cacheDir, "camera/photo.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) add(listOf { cameraFile.takeIf { it.exists() }?.inputStream() }, fromCamera = true) { cameraFile.delete() }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        add(uris.map { uri -> { context.contentResolver.openInputStream(uri) } }, fromCamera = false)
    }

    val photos = loaded ?: return
    val export: () -> Unit = {
        scope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { app.photos.exportToGallery(context, photos) }.getOrNull() }
            val message = if (count == null) res.getString(R.string.photos_export_failed)
            else res.getString(R.string.photos_exported, Format.photos(res, count))
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    // Android 9 and older need the storage permission to put pictures in the gallery; newer phones don't.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export() else showToast(context, R.string.photos_export_failed)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            try {
                                cameraFile.parentFile?.mkdirs()
                                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile))
                            } catch (e: ActivityNotFoundException) {
                                showToast(context, R.string.photos_no_camera)
                            }
                        },
                    ) { Text(stringResource(R.string.photos_take)) }
                    ExpressiveButton(
                        tonal = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) { Text(stringResource(R.string.photos_pick)) }
                }
                if (busy) {
                    Text(stringResource(R.string.photos_adding), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (photos.size >= 2) {
                    ExpressiveButton(onClick = { nav.push(Routes.compare(null)) }, modifier = Modifier.fillMaxWidth(), tonal = true) {
                        Text(stringResource(R.string.photos_compare))
                    }
                }
            }
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(stringResource(R.string.photos_empty, profileName(profile)))
            }
        }
        items(photos.asReversed(), key = { it.id }) { photo ->
            PhotoTile(app.photos, photo, photoAge(res, birth, photo.date)) { nav.push(Routes.photo(photo.id)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            AppCard(modifier = Modifier.padding(top = 8.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Text(stringResource(R.string.photos_private_note), style = MaterialTheme.typography.bodyMedium)
                if (photos.isNotEmpty()) {
                    TextButton(onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else export()
                    }) { Text(stringResource(R.string.photos_export)) }
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(store: PhotoStore, photo: Photo, label: String, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        PhotoImage(store, photo.file, minSidePx = 320, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(
            label,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** A photo from the album, decoded off the main thread at about [minSidePx] (its shorter side). */
@Composable
fun PhotoImage(
    store: PhotoStore,
    file: String,
    minSidePx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(null, file, minSidePx) {
        value = withContext(Dispatchers.IO) { store.load(file, minSidePx)?.asImageBitmap() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale) }
    }
}

/** Wraps the photo so "still loading" (null state) and "deleted" (null photo) can be told apart. */
private class LoadedPhoto(val photo: Photo?)

/** One photo, large, with its date, the child's age and weight then; change the date, share, compare, delete. */
@Composable
fun PhotoScreen(app: MahdApp, profile: Profile, photoId: Long, nav: Navigator) {
    val context = LocalContext.current
    val res = context.resources
    val today = LocalToday.current
    val scope = rememberCoroutineScope()
    val birth = profile.birthDate ?: return
    val state by remember(photoId) { app.repository.photo(photoId).map { LoadedPhoto(it) } }.collectAsState(initial = null)
    val growth by remember(profile.id) { app.repository.growth(profile.id) }.collectAsState(initial = emptyList())
    var changingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val loaded = state ?: return
    val photo = loaded.photo
    if (photo == null) {
        LaunchedEffect(Unit) { nav.back() } // deleted
        return
    }

    SubScreen(title = photoAge(res, birth, photo.date), onBack = nav::back) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PhotoImage(
                    app.photos, photo.file, minSidePx = 1080,
                    modifier = Modifier.fillMaxWidth().height(460.dp).clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Fit,
                )
            }
            item {
                AppCard(containerColor = LocalSurfaces.current.groupRow) {
                    Text(stringResource(R.string.photo_date, Format.date(photo.date)), style = MaterialTheme.typography.titleMedium)
                    PhotoRules.weightNear(growth, photo.date)?.let { kg ->
                        Text(stringResource(R.string.photo_weight, Format.decimal(kg, 2)), style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = { changingDate = true }) { Text(stringResource(R.string.photo_change_date)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(onClick = { nav.push(Routes.compare(photo.id)) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.photo_compare_with))
                    }
                    ExpressiveButton(
                        tonal = true,
                        onClick = { scope.launch { runOrToast(context, R.string.share_failed) { sharePhoto(context, app.photos.file(photo.file)) } } },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.photo_share)) }
                }
            }
            item {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (changingDate) {
        DatePickerPopup(
            initial = photo.date,
            min = birth,
            max = today,
            onDismiss = { changingDate = false },
            onPick = { date -> scope.launch { app.repository.setPhotoDate(photo.id, date) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.photo_delete_title),
            text = stringResource(R.string.photo_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                // The app's scope: the screen closes as soon as the row is gone, the file is deleted after.
                app.scope.launch {
                    val file = runCatching { app.repository.deletePhoto(photo.id) }.getOrNull()
                    if (file != null) withContext(Dispatchers.IO) { app.photos.delete(file) }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun sharePhoto(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.photo_share_chooser)))
}

/**
 * Two photos side by side, the older one first (on the right in Arabic), each with the child's age, the date and
 * the weight measured around then. The mother picks each photo from a strip below; the pair can be shared as one
 * picture.
 */
@Composable
fun CompareScreen(app: MahdApp, profile: Profile, firstId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    val res = context.resources
    val birth = profile.birthDate ?: return
    val photos by remember(profile.id) { app.repository.photos(profile.id) }.collectAsState(initial = null)
    val growth by remember(profile.id) { app.repository.growth(profile.id) }.collectAsState(initial = null)
    var chosenFirst by rememberSaveable { mutableStateOf<Long?>(null) }
    var chosenSecond by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val name = profileName(profile)
    val footer = stringResource(R.string.share_card_footer)

    SubScreen(title = stringResource(R.string.compare_title), onBack = onBack) { padding ->
        val list = photos ?: return@SubScreen
        val entries = growth ?: return@SubScreen
        val defaults = PhotoRules.defaultPair(list.map { it.id }, firstId)
        if (defaults == null) {
            Box(Modifier.padding(padding)) { EmptyState(stringResource(R.string.compare_need_two)) }
            return@SubScreen
        }
        val first = list.firstOrNull { it.id == chosenFirst } ?: list.first { it.id == defaults.first }
        val second = list.firstOrNull { it.id == chosenSecond } ?: list.first { it.id == defaults.second }
        val (older, newer) = listOf(first, second).sortedBy { it.date }.let { it[0] to it[1] }
        fun label(photo: Photo): String = listOfNotNull(
            photoAge(res, birth, photo.date),
            Format.date(photo.date),
            PhotoRules.weightNear(entries, photo.date)?.let { res.getString(R.string.tile_weight_value, Format.decimal(it, 2)) },
        ).joinToString("\n")

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // In a right-to-left row the first panel is on the right: the older photo, then the newer one.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComparePanel(app.photos, older, label(older), Modifier.weight(1f))
                    ComparePanel(app.photos, newer, label(newer), Modifier.weight(1f))
                }
            }
            item {
                ExpressiveButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            runOrToast(context, R.string.share_failed) {
                                val card = withContext(Dispatchers.Default) {
                                    val olderBitmap = requireNotNull(app.photos.load(older.file, 900))
                                    val newerBitmap = requireNotNull(app.photos.load(newer.file, 900))
                                    ShareCard.renderComparison(
                                        ShareCard.Comparison(
                                            title = res.getString(
                                                R.string.compare_card_title, name,
                                                photoAge(res, birth, older.date), photoAge(res, birth, newer.date),
                                            ),
                                            older = olderBitmap, olderLabel = label(older),
                                            newer = newerBitmap, newerLabel = label(newer),
                                            footer = footer,
                                        ),
                                    )
                                }
                                ShareCard.share(context, card, res.getString(R.string.compare_share), "atwar-comparison.png")
                            }
                        }
                    },
                ) { Text(stringResource(R.string.compare_share)) }
            }
            item { PhotoStrip(app.photos, list, stringResource(R.string.compare_first), selected = first.id) { chosenFirst = it } }
            item { PhotoStrip(app.photos, list, stringResource(R.string.compare_second), selected = second.id) { chosenSecond = it } }
        }
    }
}

@Composable
private fun ComparePanel(store: PhotoStore, photo: Photo, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PhotoImage(
            store, photo.file, minSidePx = 600,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.extraLarge),
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** A row of small photos to choose one from; the chosen one has a coloured frame. */
@Composable
private fun PhotoStrip(store: PhotoStore, photos: List<Photo>, title: String, selected: Long, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(photos, key = { it.id }) { photo ->
                val frame = if (photo.id == selected) MaterialTheme.colorScheme.primary else Color.Transparent
                PhotoImage(
                    store, photo.file, minSidePx = 200,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(3.dp, frame, RoundedCornerShape(10.dp))
                        .clickable { onSelect(photo.id) },
                )
            }
        }
    }
}
