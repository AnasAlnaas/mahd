package ly.mahd.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Converts all of the mother's data to and from a small JSON file.
 * She can save it to Drive or send it to herself on WhatsApp, and restore it on a new phone.
 */
object BackupFile {
    private const val APP = "mahd"
    private const val FORMAT = 1

    fun encode(snapshot: Repository.Snapshot, today: LocalDate): String {
        val root = JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("exportedAt", today.toString())
            .put("profiles", JSONArray().apply {
                snapshot.profiles.forEach { p ->
                    put(JSONObject()
                        .put("id", p.id)
                        .put("kind", p.kind.code)
                        .put("name", p.name)
                        .putOpt("sex", p.sex?.code)
                        .putOpt("birthDate", p.birthDate?.toString())
                        .putOpt("dueDate", p.dueDate?.toString())
                        .putOpt("weeksAtBirth", p.weeksAtBirth)
                        .putOpt("birthType", p.birthType?.code))
                }
            })
            .put("growth", JSONArray().apply {
                snapshot.growth.forEach { g ->
                    put(JSONObject()
                        .put("id", g.id)
                        .put("profileId", g.profileId)
                        .put("date", g.date.toString())
                        .putOpt("weightKg", g.weightKg)
                        .putOpt("lengthCm", g.lengthCm)
                        .putOpt("headCm", g.headCm))
                }
            })
            .put("doses", JSONArray().apply {
                snapshot.doses.forEach { d ->
                    put(JSONObject()
                        .put("profileId", d.profileId)
                        .put("doseId", d.doseId)
                        .put("givenDate", d.givenDate.toString()))
                }
            })
            .put("milestones", JSONArray().apply {
                snapshot.milestones.forEach { (profileId, itemId, answer) ->
                    put(JSONObject()
                        .put("profileId", profileId)
                        .put("itemId", itemId)
                        .put("answer", answer))
                }
            })
            .put("skippedDoses", JSONArray().apply {
                snapshot.skipped.forEach { (profileId, doseId) ->
                    put(JSONObject()
                        .put("profileId", profileId)
                        .put("doseId", doseId))
                }
            })
            .put("exerciseLog", JSONArray().apply {
                snapshot.exerciseLog.forEach { t ->
                    put(JSONObject()
                        .put("profileId", t.profileId)
                        .put("date", t.date.toString())
                        .put("exerciseId", t.exerciseId))
                }
            })
        return root.toString(2)
    }

    /** The oldest date accepted in a backup. */
    private val OLDEST = LocalDate.of(1900, 1, 1)

    /** Longest name or id accepted (the app itself allows 40 characters for a name). */
    private const val MAX_TEXT = 100

    /**
     * Throws [IllegalArgumentException] when the file is not a valid, consistent backup of this app:
     * bad JSON, bad dates, rows pointing at a profile that is not in the file, duplicate rows, or values
     * that make no sense (a child without a birth date, a date in the year 3000, a negative weight…).
     * Checking everything here means restoring can never fail half-way through, and a broken or
     * hand-edited file can never leave the app crashing every time it opens.
     */
    fun decode(text: String, today: LocalDate): Repository.Snapshot = try {
        parse(text).also { validate(it, today) }
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("invalid backup", e)
    }

    private fun parse(text: String): Repository.Snapshot {
        val root = JSONObject(text)
        require(root.optString("app") == APP) { "not a backup of this app" }
        require(root.optInt("format") in 1..FORMAT) { "backup made by a newer version" }

        val profiles = root.getJSONArray("profiles").objects().map { o ->
            val kind = o.getString("kind")
            Profile(
                id = o.getLong("id"),
                kind = requireNotNull(ProfileKind.entries.firstOrNull { it.code == kind }) { "unknown profile kind" },
                name = o.optString("name"),
                sex = o.optStringOrNull("sex")?.let { code -> requireNotNull(Sex.fromCode(code)) { "unknown sex" } },
                birthDate = o.optStringOrNull("birthDate")?.let(LocalDate::parse),
                dueDate = o.optStringOrNull("dueDate")?.let(LocalDate::parse),
                weeksAtBirth = if (o.has("weeksAtBirth") && !o.isNull("weeksAtBirth")) o.getInt("weeksAtBirth") else null,
                // Not in backups made before the app asked how the baby was born.
                birthType = o.optStringOrNull("birthType")?.let { code ->
                    requireNotNull(BirthType.fromCode(code)) { "unknown birth type" }
                },
            )
        }
        val growth = root.optJSONArray("growth").objects().map { o ->
            GrowthEntry(
                id = o.getLong("id"),
                profileId = o.getLong("profileId"),
                date = LocalDate.parse(o.getString("date")),
                weightKg = o.optDoubleOrNull("weightKg"),
                lengthCm = o.optDoubleOrNull("lengthCm"),
                headCm = o.optDoubleOrNull("headCm"),
            )
        }
        val doses = root.optJSONArray("doses").objects().map { o ->
            DoseRecord(
                profileId = o.getLong("profileId"),
                doseId = o.getString("doseId"),
                givenDate = LocalDate.parse(o.getString("givenDate")),
            )
        }
        val milestones = root.optJSONArray("milestones").objects().map { o ->
            Triple(o.getLong("profileId"), o.getString("itemId"), o.getInt("answer"))
        }
        val givenKeys = doses.map { it.profileId to it.doseId }.toSet()
        val skipped = root.optJSONArray("skippedDoses").objects()
            .map { o -> o.getLong("profileId") to o.getString("doseId") }
            .filter { it !in givenKeys }
        // Not in backups made before the daily exercise program.
        val exerciseLog = root.optJSONArray("exerciseLog").objects().map { o ->
            ExerciseTick(o.getLong("profileId"), LocalDate.parse(o.getString("date")), o.getString("exerciseId"))
        }

        val profileIds = profiles.map { it.id }.toSet()
        require(profileIds.size == profiles.size) { "duplicate profile ids" }
        require(growth.all { it.profileId in profileIds }) { "growth entry without a profile" }
        require(growth.map { it.id }.toSet().size == growth.size) { "duplicate growth ids" }
        require(doses.all { it.profileId in profileIds }) { "dose without a profile" }
        require(givenKeys.size == doses.size) { "duplicate dose records" }
        require(milestones.all { it.first in profileIds }) { "milestone without a profile" }
        require(milestones.map { it.first to it.second }.toSet().size == milestones.size) { "duplicate milestones" }
        require(skipped.all { it.first in profileIds }) { "skipped dose without a profile" }
        require(skipped.toSet().size == skipped.size) { "duplicate skipped doses" }
        require(exerciseLog.all { it.profileId in profileIds }) { "exercise tick without a profile" }
        require(exerciseLog.toSet().size == exerciseLog.size) { "duplicate exercise ticks" }

        return Repository.Snapshot(profiles, growth, doses, milestones, skipped, exerciseLog)
    }

    /** Checks that the values make sense; everything the app itself can save passes. */
    private fun validate(s: Repository.Snapshot, today: LocalDate) {
        // A year of slack for dates "in the future", in case the phone's clock was wrong when they were entered.
        val newest = today.plusYears(1)
        fun LocalDate?.inRange() = this == null || (!isBefore(OLDEST) && !isAfter(newest))
        fun String.isId() = isNotBlank() && length <= MAX_TEXT

        require(s.profiles.isNotEmpty()) { "no profiles" }
        s.profiles.forEach { p ->
            require(p.id > 0 && p.name.length <= MAX_TEXT) { "bad profile" }
            require(p.birthDate.inRange() && p.dueDate.inRange()) { "profile date out of range" }
            when (p.kind) {
                ProfileKind.CHILD -> require(p.birthDate != null) { "child without a birth date" }
                ProfileKind.PREGNANCY -> require(p.dueDate != null) { "pregnancy without a due date" }
            }
            // Any non-negative number of weeks can come from the app ("my baby is born" long after the due date).
            require(p.weeksAtBirth == null || p.weeksAtBirth >= 0) { "bad weeks at birth" }
        }
        s.growth.forEach { g ->
            require(g.id > 0 && g.date.inRange()) { "bad growth entry" }
            val values = listOfNotNull(g.weightKg, g.lengthCm, g.headCm)
            require(values.isNotEmpty() && values.all { it.isFinite() && it > 0 }) { "bad measurement" }
            require((g.weightKg ?: 0.0) <= 150 && (g.lengthCm ?: 0.0) <= 250 && (g.headCm ?: 0.0) <= 100) {
                "measurement out of range"
            }
        }
        s.doses.forEach { require(it.doseId.isId() && it.givenDate.inRange()) { "bad dose" } }
        s.milestones.forEach { (_, itemId, answer) ->
            require(itemId.isId() && MilestoneAnswer.fromCode(answer) != null) { "bad milestone answer" }
        }
        s.skipped.forEach { (_, doseId) -> require(doseId.isId()) { "bad skipped dose" } }
        s.exerciseLog.forEach { require(it.exerciseId.isId() && it.date.inRange()) { "bad exercise tick" } }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
