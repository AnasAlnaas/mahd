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
                        .putOpt("weeksAtBirth", p.weeksAtBirth))
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
        return root.toString(2)
    }

    /**
     * Throws [IllegalArgumentException] when the file is not a valid, consistent backup of this app
     * (bad JSON, bad dates, rows pointing at a profile that is not in the file, duplicate rows).
     * Checking everything here means restoring can never fail half-way through.
     */
    fun decode(text: String): Repository.Snapshot = try {
        parse(text)
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
            Profile(
                id = o.getLong("id"),
                kind = ProfileKind.fromCode(o.getString("kind")),
                name = o.optString("name"),
                sex = Sex.fromCode(o.optStringOrNull("sex")),
                birthDate = o.optStringOrNull("birthDate")?.let(LocalDate::parse),
                dueDate = o.optStringOrNull("dueDate")?.let(LocalDate::parse),
                weeksAtBirth = if (o.has("weeksAtBirth")) o.getInt("weeksAtBirth") else null,
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

        return Repository.Snapshot(profiles, growth, doses, milestones, skipped)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
