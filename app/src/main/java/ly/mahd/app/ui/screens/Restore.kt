package ly.mahd.app.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.BackupFile
import ly.mahd.app.data.Repository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

/** A real backup is a few kilobytes; anything much bigger (a video picked by mistake) is not read at all. */
private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

/**
 * Shared by Settings and the first-launch screen: lets the mother pick a backup file, checks it, and
 * hands the checked snapshot to [onPicked]. Shows "not a valid backup" if the file is wrong.
 * Returns a function that opens the file picker.
 */
@Composable
fun rememberBackupPicker(onPicked: (Repository.Snapshot) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            // Reading and checking happen off the main thread, so even a wrong, large file cannot freeze the screen.
            val snapshot = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_BACKUP_BYTES) }
                    BackupFile.decode(requireNotNull(bytes) { "unreadable or too large" }.toString(Charsets.UTF_8), LocalDate.now())
                }.getOrNull()
            }
            if (snapshot == null) showToast(context, R.string.restore_invalid) else onPicked(snapshot)
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
}

/** The whole stream, or null if it is longer than [limit] bytes. */
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val n = read(buffer)
        if (n < 0) return out.toByteArray()
        out.write(buffer, 0, n)
        if (out.size() > limit) return null
    }
}

/**
 * Replaces all data with [snapshot]. The write is one transaction, so on failure the old data is untouched.
 * On success the first profile is selected and old "reminder already sent" marks are cleared
 * (profile ids may now belong to different children). Returns true on success.
 */
suspend fun applyRestore(app: MahdApp, snapshot: Repository.Snapshot): Boolean {
    val ok = runCatching { app.repository.replaceAll(snapshot) }.isSuccess
    if (ok) {
        app.prefs.selectProfile(snapshot.profiles.firstOrNull()?.id)
        app.prefs.resetReminderBookkeeping()
    }
    return ok
}

fun showToast(context: Context, message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

/**
 * Runs [block]; if it fails (full storage, no app to share with…) shows [failedMessage] instead of
 * crashing the app. Leaving the screen (cancellation) is not a failure and shows nothing.
 */
suspend fun runOrToast(context: Context, failedMessage: Int, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Mahd", "Action failed", e)
        showToast(context, failedMessage)
    }
}
