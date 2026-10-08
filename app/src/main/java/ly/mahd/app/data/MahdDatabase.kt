package ly.mahd.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The app's local database, using Android's built-in SQLite directly (no extra library).
 *
 * Dates are stored as ISO text ("2026-10-02"), which sorts correctly and is easy to read in backups.
 * When the schema changes, bump [VERSION] and add a step to [onUpgrade] — never delete user data.
 */
class MahdDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                name TEXT NOT NULL,
                sex TEXT,
                birth_date TEXT,
                due_date TEXT,
                weeks_at_birth INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE growth (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                date TEXT NOT NULL,
                weight_kg REAL,
                length_cm REAL,
                head_cm REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX growth_profile ON growth(profile_id, date)")
        db.execSQL(
            """
            CREATE TABLE vaccine_doses (
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                dose_id TEXT NOT NULL,
                given_date TEXT NOT NULL,
                PRIMARY KEY (profile_id, dose_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE milestone_answers (
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                item_id TEXT NOT NULL,
                answer INTEGER NOT NULL,
                PRIMARY KEY (profile_id, item_id)
            )
            """.trimIndent()
        )
        createVaccineSkips(db)
        createPhotos(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations go here, e.g.:
        // if (oldVersion < 4) db.execSQL("ALTER TABLE profiles ADD COLUMN note TEXT")
        if (oldVersion < 2) createVaccineSkips(db)
        if (oldVersion < 3) createPhotos(db)
    }

    /** The children's photos; the pictures themselves are files (see PhotoStore), this table says whose and when. */
    private fun createPhotos(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                date TEXT NOT NULL,
                file TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS photos_profile ON photos(profile_id, date)")
    }

    /** Doses the mother marked "not applicable / skipped" (so they stop counting as late). */
    private fun createVaccineSkips(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vaccine_skips (
                profile_id INTEGER NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
                dose_id TEXT NOT NULL,
                PRIMARY KEY (profile_id, dose_id)
            )
            """.trimIndent()
        )
    }

    companion object {
        const val NAME = "mahd.db"
        const val VERSION = 3
    }
}
