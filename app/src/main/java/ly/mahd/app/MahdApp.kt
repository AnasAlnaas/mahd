package ly.mahd.app

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ly.mahd.app.content.ContentRepository
import ly.mahd.app.data.MahdDatabase
import ly.mahd.app.data.PhotoStore
import ly.mahd.app.data.Prefs
import ly.mahd.app.data.Repository
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.reminders.ReminderScheduler
import java.io.File
import kotlin.concurrent.thread

/**
 * Creates the few shared objects the app needs. This is all the "dependency injection"
 * a small app needs — no framework required.
 */
class MahdApp : Application() {

    lateinit var repository: Repository
        private set
    lateinit var content: ContentRepository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var photos: PhotoStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(MahdDatabase(this))
        content = ContentRepository(assets)
        prefs = Prefs(this)
        photos = PhotoStore(File(filesDir, "photos"))

        // A broken content file must not kill the app from a background thread; the unit tests
        // (ContentFilesTest) catch broken files before release, and screens report the error when used.
        thread(name = "content-preload") {
            try {
                content.preload()
            } catch (e: Exception) {
                Log.e("Mahd", "Content preload failed", e)
            }
            // Photo files left behind (e.g. the app was closed in the middle of adding a photo).
            try {
                photos.deleteAllExcept(runBlocking { repository.photoFiles() }, minAgeMillis = 10 * 60 * 1000L)
            } catch (e: Exception) {
                Log.e("Mahd", "Photo clean-up failed", e)
            }
        }

        Notifications.createChannel(this)
        ReminderScheduler.ensureScheduled(this)
    }

    /** Deletes a profile with everything recorded for it, photo files included. */
    suspend fun deleteProfile(id: Long) {
        repository.deleteProfile(id)
        withContext(Dispatchers.IO) { photos.deleteAllExcept(repository.photoFiles()) }
    }
}

val Context.app: MahdApp get() = applicationContext as MahdApp
