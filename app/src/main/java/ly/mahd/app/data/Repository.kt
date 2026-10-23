package ly.mahd.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * All reads and writes of the mother's data go through here.
 *
 * Screens observe data as [Flow]s. Every write bumps [version], which makes every observed
 * query run again, so the screens always show fresh data without any extra library.
 */
class Repository(private val database: MahdDatabase) {

    private val version = MutableStateFlow(0L)

    private fun <T> observe(query: (SQLiteDatabase) -> T): Flow<T> =
        version.map { query(database.readableDatabase) }.flowOn(Dispatchers.IO)

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
            version.update { it + 1 }
        }
    }

    // ---------- Profiles ----------

    val profiles: Flow<List<Profile>> = observe { queryProfiles(it) }

    /** Blocking read, for background work such as reminders. */
    fun profilesNow(): List<Profile> = queryProfiles(database.readableDatabase)

    suspend fun addProfile(profile: Profile): Long = write { it.insertOrThrow("profiles", null, profile.toValues()) }

    /** Adds a child together with the skills already counted as done ([doneSkills] answered "yes"), in one write. */
    suspend fun addChild(profile: Profile, doneSkills: Collection<String>): Long = write { db ->
        val id = db.insertOrThrow("profiles", null, profile.toValues())
        doneSkills.forEach { itemId ->
            db.insertWithOnConflict("milestone_answers", null, ContentValues().apply {
                put("profile_id", id)
                put("item_id", itemId)
                put("answer", MilestoneAnswer.YES.code)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
        id
    }

    suspend fun updateProfile(profile: Profile) = write {
        it.update("profiles", profile.toValues(), "id = ?", arrayOf(profile.id.toString()))
    }

    suspend fun deleteProfile(id: Long) = write { it.delete("profiles", "id = ?", arrayOf(id.toString())) }

    private fun queryProfiles(db: SQLiteDatabase): List<Profile> =
        db.rawQuery("SELECT * FROM profiles ORDER BY id", null).use { c -> c.mapRows { it.toProfile() } }

    // ---------- Growth ----------

    fun growth(profileId: Long): Flow<List<GrowthEntry>> = observe { queryGrowth(it, profileId) }

    suspend fun addGrowth(entry: GrowthEntry): Long = write { it.insertOrThrow("growth", null, entry.toValues()) }

    suspend fun deleteGrowth(id: Long) = write { it.delete("growth", "id = ?", arrayOf(id.toString())) }

    private fun queryGrowth(db: SQLiteDatabase, profileId: Long): List<GrowthEntry> =
        db.rawQuery("SELECT * FROM growth WHERE profile_id = ? ORDER BY date, id", arrayOf(profileId.toString()))
            .use { c -> c.mapRows { it.toGrowth() } }

    // ---------- Everything for one child ----------

    /** All of one child's records in a single read, so a screen never mixes old and new data. */
    fun childRecords(profileId: Long): Flow<ChildRecords> = observe { db ->
        ChildRecords(
            doses = queryDoses(db, profileId),
            skipped = querySkips(db, profileId),
            answers = queryAnswers(db, profileId),
            growth = queryGrowth(db, profileId),
        )
    }

    // ---------- Vaccines ----------

    fun dosesNow(profileId: Long): Map<String, LocalDate> = queryDoses(database.readableDatabase, profileId)

    suspend fun setDosesGiven(profileId: Long, doseIds: Collection<String>, date: LocalDate) =
        setDosesGivenOn(profileId, doseIds.associateWith { date })

    /** Records each dose with its own date. A dose that was marked "skipped" stops being skipped. */
    suspend fun setDosesGivenOn(profileId: Long, doses: Map<String, LocalDate>) = write { db ->
        doses.forEach { (doseId, date) ->
            val values = ContentValues().apply {
                put("profile_id", profileId)
                put("dose_id", doseId)
                put("given_date", date.toString())
            }
            db.insertWithOnConflict("vaccine_doses", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("vaccine_skips", "profile_id = ? AND dose_id = ?", arrayOf(profileId.toString(), doseId))
        }
    }

    fun skippedDosesNow(profileId: Long): Set<String> = querySkips(database.readableDatabase, profileId)

    /** Marks doses "not applicable / skipped". Skipped doses are not counted as late and get no reminders. */
    suspend fun setDosesSkipped(profileId: Long, doseIds: Collection<String>) = write { db ->
        doseIds.forEach { doseId ->
            val values = ContentValues().apply {
                put("profile_id", profileId)
                put("dose_id", doseId)
            }
            db.insertWithOnConflict("vaccine_skips", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    suspend fun clearSkip(profileId: Long, doseId: String) = write {
        it.delete("vaccine_skips", "profile_id = ? AND dose_id = ?", arrayOf(profileId.toString(), doseId))
    }

    private fun querySkips(db: SQLiteDatabase, profileId: Long): Set<String> =
        db.rawQuery("SELECT dose_id FROM vaccine_skips WHERE profile_id = ?", arrayOf(profileId.toString()))
            .use { c -> c.mapRows { it.getString(0) }.toSet() }

    suspend fun clearDose(profileId: Long, doseId: String) = write {
        it.delete("vaccine_doses", "profile_id = ? AND dose_id = ?", arrayOf(profileId.toString(), doseId))
    }

    private fun queryDoses(db: SQLiteDatabase, profileId: Long): Map<String, LocalDate> =
        db.rawQuery("SELECT dose_id, given_date FROM vaccine_doses WHERE profile_id = ?", arrayOf(profileId.toString()))
            .use { c -> c.mapRows { it.getString(0) to LocalDate.parse(it.getString(1)) }.toMap() }

    // ---------- Milestones ----------

    fun milestoneAnswers(profileId: Long): Flow<Map<String, MilestoneAnswer>> = observe { queryAnswers(it, profileId) }

    private fun queryAnswers(db: SQLiteDatabase, profileId: Long): Map<String, MilestoneAnswer> =
        db.rawQuery("SELECT item_id, answer FROM milestone_answers WHERE profile_id = ?", arrayOf(profileId.toString()))
            .use { c ->
                c.mapRows { row -> MilestoneAnswer.fromCode(row.getInt(1))?.let { row.getString(0) to it } }
                    .filterNotNull()
                    .toMap()
            }

    suspend fun setMilestoneAnswer(profileId: Long, itemId: String, answer: MilestoneAnswer?) = write { db ->
        if (answer == null) {
            db.delete("milestone_answers", "profile_id = ? AND item_id = ?", arrayOf(profileId.toString(), itemId))
        } else {
            val values = ContentValues().apply {
                put("profile_id", profileId)
                put("item_id", itemId)
                put("answer", answer.code)
            }
            db.insertWithOnConflict("milestone_answers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ---------- The daily exercise program ----------

    /** Which exercises were ticked on which day, for one pregnancy. */
    fun exerciseLog(profileId: Long): Flow<Map<LocalDate, Set<String>>> = observe { db ->
        db.rawQuery("SELECT date, exercise_id FROM exercise_log WHERE profile_id = ?", arrayOf(profileId.toString()))
            .use { c -> c.mapRows { LocalDate.parse(it.getString(0)) to it.getString(1) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
    }

    suspend fun setExerciseDone(profileId: Long, date: LocalDate, exerciseId: String, done: Boolean) = write { db ->
        if (done) {
            db.insertWithOnConflict("exercise_log", null, ContentValues().apply {
                put("profile_id", profileId)
                put("date", date.toString())
                put("exercise_id", exerciseId)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            db.delete(
                "exercise_log",
                "profile_id = ? AND date = ? AND exercise_id = ?",
                arrayOf(profileId.toString(), date.toString(), exerciseId),
            )
        }
    }

    // ---------- Photos ----------

    /** One child's photos, oldest first. */
    fun photos(profileId: Long): Flow<List<Photo>> = observe { queryPhotos(it, profileId) }

    fun photo(id: Long): Flow<Photo?> = observe { db ->
        db.rawQuery("SELECT * FROM photos WHERE id = ?", arrayOf(id.toString())).use { c -> c.mapRows { it.toPhoto() }.firstOrNull() }
    }

    suspend fun addPhotos(photos: List<Photo>) = write { db ->
        photos.forEach { db.insertOrThrow("photos", null, it.toValues()) }
    }

    suspend fun setPhotoDate(id: Long, date: LocalDate) = write {
        it.update("photos", ContentValues().apply { put("date", date.toString()) }, "id = ?", arrayOf(id.toString()))
    }

    /** Deletes the photo's row and returns its file name, so the caller can delete the file. */
    suspend fun deletePhoto(id: Long): String? = write { db ->
        val file = db.rawQuery("SELECT file FROM photos WHERE id = ?", arrayOf(id.toString()))
            .use { c -> c.mapRows { it.getString(0) }.firstOrNull() }
        db.delete("photos", "id = ?", arrayOf(id.toString()))
        file
    }

    /** Every photo file the database still points at; any other file in the photo folder can be deleted. */
    suspend fun photoFiles(): Set<String> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery("SELECT file FROM photos", null).use { c -> c.mapRows { it.getString(0) }.toSet() }
    }

    private fun queryPhotos(db: SQLiteDatabase, profileId: Long): List<Photo> =
        db.rawQuery("SELECT * FROM photos WHERE profile_id = ? ORDER BY date, id", arrayOf(profileId.toString()))
            .use { c -> c.mapRows { it.toPhoto() } }

    // ---------- Backup ----------

    /** Everything the mother has entered, as a plain snapshot (used for backup files). */
    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        Snapshot(
            profiles = queryProfiles(db),
            growth = db.rawQuery("SELECT * FROM growth ORDER BY id", null).use { c -> c.mapRows { it.toGrowth() } },
            doses = db.rawQuery("SELECT * FROM vaccine_doses", null).use { c ->
                c.mapRows {
                    DoseRecord(
                        profileId = it.long("profile_id"),
                        doseId = it.string("dose_id")!!,
                        givenDate = LocalDate.parse(it.string("given_date")),
                    )
                }
            },
            milestones = db.rawQuery("SELECT * FROM milestone_answers", null).use { c ->
                c.mapRows { Triple(it.long("profile_id"), it.string("item_id")!!, it.int("answer")) }
            },
            skipped = db.rawQuery("SELECT * FROM vaccine_skips", null).use { c ->
                c.mapRows { it.long("profile_id") to it.string("dose_id")!! }
            },
            exerciseLog = db.rawQuery("SELECT * FROM exercise_log ORDER BY date", null).use { c ->
                c.mapRows { ExerciseTick(it.long("profile_id"), it.date("date")!!, it.string("exercise_id")!!) }
            },
        )
    }

    /**
     * Replaces ALL data with the snapshot (used when restoring a backup). Backup files hold no photos: a child the
     * backup describes the same way ([Profile.isSameChildAs]) keeps its photos, the others' photo rows are dropped
     * (the caller then deletes the files nobody points at any more).
     */
    suspend fun replaceAll(snapshot: Snapshot) = write { db ->
        val current = queryProfiles(db).associateBy { it.id }
        val sameChildren = snapshot.profiles.filter { p -> current[p.id]?.isSameChildAs(p) == true }.map { it.id }.toSet()
        val keptPhotos = db.rawQuery("SELECT * FROM photos", null)
            .use { c -> c.mapRows { it.toPhoto() } }
            .filter { it.profileId in sameChildren }
        db.delete("photos", null, null)
        db.delete("exercise_log", null, null)
        db.delete("milestone_answers", null, null)
        db.delete("vaccine_skips", null, null)
        db.delete("vaccine_doses", null, null)
        db.delete("growth", null, null)
        db.delete("profiles", null, null)
        snapshot.profiles.forEach { p ->
            db.insertOrThrow("profiles", null, p.toValues().apply { put("id", p.id) })
        }
        snapshot.growth.forEach { g ->
            db.insertOrThrow("growth", null, g.toValues().apply { put("id", g.id) })
        }
        snapshot.doses.forEach { d ->
            db.insertOrThrow("vaccine_doses", null, ContentValues().apply {
                put("profile_id", d.profileId)
                put("dose_id", d.doseId)
                put("given_date", d.givenDate.toString())
            })
        }
        snapshot.milestones.forEach { (profileId, itemId, answer) ->
            db.insertOrThrow("milestone_answers", null, ContentValues().apply {
                put("profile_id", profileId)
                put("item_id", itemId)
                put("answer", answer)
            })
        }
        snapshot.skipped.forEach { (profileId, doseId) ->
            db.insertOrThrow("vaccine_skips", null, ContentValues().apply {
                put("profile_id", profileId)
                put("dose_id", doseId)
            })
        }
        snapshot.exerciseLog.forEach { t ->
            db.insertOrThrow("exercise_log", null, ContentValues().apply {
                put("profile_id", t.profileId)
                put("date", t.date.toString())
                put("exercise_id", t.exerciseId)
            })
        }
        keptPhotos.forEach { p -> db.insertOrThrow("photos", null, p.toValues().apply { put("id", p.id) }) }
    }

    data class Snapshot(
        val profiles: List<Profile>,
        val growth: List<GrowthEntry>,
        val doses: List<DoseRecord>,
        /** (profileId, itemId, answer code) */
        val milestones: List<Triple<Long, String, Int>>,
        /** (profileId, doseId) of doses marked "skipped / not applicable" */
        val skipped: List<Pair<Long, String>> = emptyList(),
        /** The daily exercise program's ticks. */
        val exerciseLog: List<ExerciseTick> = emptyList(),
    )
}

// ---------- Row mapping helpers ----------

private fun <T> Cursor.mapRows(map: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count)
    while (moveToNext()) out.add(map(this))
    return out
}

private fun Cursor.string(column: String): String? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

private fun Cursor.intOrNull(column: String): Int? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }

private fun Cursor.doubleOrNull(column: String): Double? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

private fun Cursor.date(column: String): LocalDate? = string(column)?.let(LocalDate::parse)

private fun Cursor.toProfile() = Profile(
    id = long("id"),
    kind = ProfileKind.fromCode(string("kind") ?: ""),
    name = string("name") ?: "",
    sex = Sex.fromCode(string("sex")),
    birthDate = date("birth_date"),
    dueDate = date("due_date"),
    weeksAtBirth = intOrNull("weeks_at_birth"),
    birthType = BirthType.fromCode(string("birth_type")),
)

private fun Cursor.toGrowth() = GrowthEntry(
    id = long("id"),
    profileId = long("profile_id"),
    date = date("date")!!,
    weightKg = doubleOrNull("weight_kg"),
    lengthCm = doubleOrNull("length_cm"),
    headCm = doubleOrNull("head_cm"),
)

private fun Cursor.toPhoto() = Photo(
    id = long("id"),
    profileId = long("profile_id"),
    date = date("date")!!,
    file = string("file")!!,
)

private fun Photo.toValues() = ContentValues().apply {
    put("profile_id", profileId)
    put("date", date.toString())
    put("file", file)
}

private fun Profile.toValues() = ContentValues().apply {
    put("kind", kind.code)
    put("name", name)
    put("sex", sex?.code)
    put("birth_date", birthDate?.toString())
    put("due_date", dueDate?.toString())
    if (weeksAtBirth != null) put("weeks_at_birth", weeksAtBirth) else putNull("weeks_at_birth")
    put("birth_type", birthType?.code)
}

private fun GrowthEntry.toValues() = ContentValues().apply {
    put("profile_id", profileId)
    put("date", date.toString())
    if (weightKg != null) put("weight_kg", weightKg) else putNull("weight_kg")
    if (lengthCm != null) put("length_cm", lengthCm) else putNull("length_cm")
    if (headCm != null) put("head_cm", headCm) else putNull("head_cm")
}
