package ly.mahd.app

import android.app.Application
import android.content.Context
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

        thread(name = "content-preload") { content.preload() }

        Notifications.createChannel(this)
        ReminderScheduler.schedule(this)
    }
}

val Context.app: MahdApp get() = applicationContext as MahdApp
