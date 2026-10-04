package ly.mahd.app

import android.app.Application
import android.content.Context
import android.util.Log
import ly.mahd.app.content.ContentRepository
import ly.mahd.app.data.MahdDatabase
import ly.mahd.app.data.Prefs
import ly.mahd.app.data.Repository
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.reminders.ReminderScheduler
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

    override fun onCreate() {
        super.onCreate()
        repository = Repository(MahdDatabase(this))
        content = ContentRepository(assets)
        prefs = Prefs(this)

        // A broken content file must not kill the app from a background thread; the unit tests
        // (ContentFilesTest) catch broken files before release, and screens report the error when used.
        thread(name = "content-preload") {
            try {
                content.preload()
            } catch (e: Exception) {
                Log.e("Mahd", "Content preload failed", e)
            }
        }

        Notifications.createChannel(this)
        ReminderScheduler.ensureScheduled(this)
    }
}

val Context.app: MahdApp get() = applicationContext as MahdApp
