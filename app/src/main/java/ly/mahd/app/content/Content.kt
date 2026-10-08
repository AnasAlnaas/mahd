package ly.mahd.app.content

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/*
 * Medical and educational content lives in plain files under app/src/main/assets/content/,
 * NOT in the code. Doctors can review the text, and a future web version can reuse the same files.
 * Every file carries a "reviewed" flag; the app shows a "draft" notice until a doctor signs it off.
 */

data class PregnancyWeek(
    val week: Int,
    val emoji: String,
    val size: String,
    val lengthCm: Double?,
    val weightG: Double?,
    val baby: String,
    val mother: String,
    val tip: String,
)

data class PregnancyContent(
    val reviewed: Boolean,
    val weeks: List<PregnancyWeek>,
    val dangerSigns: List<String>,
) {
    /** Content for a week; weeks outside the table use the nearest one. */
    fun week(n: Int): PregnancyWeek = weeks.firstOrNull { it.week == n }
        ?: if (n < weeks.first().week) weeks.first() else weeks.last()

    val lastWeek: Int get() = weeks.last().week
}

/** A list of "go to the hospital now" signs, with an optional heading (e.g. "under 3 months"). */
data class DangerSignSection(
    val title: String?,
    val signs: List<String>,
    /** The section is about children younger than this (in months); null = any age. */
    val maxAgeMonths: Int? = null,
)

data class ChildDangerSigns(
    val reviewed: Boolean,
    val sections: List<DangerSignSection>,
)

data class MilestoneDomain(val id: String, val title: String, val emoji: String)

data class MilestoneItem(val id: String, val domain: String, val text: String)

data class MilestoneGroup(
    val id: String,
    val months: Int,
    val title: String,
    val items: List<MilestoneItem>,
    val tips: List<String>,
)

data class MilestoneContent(
    val reviewed: Boolean,
    val domains: List<MilestoneDomain>,
    val groups: List<MilestoneGroup>,
    val warnings: List<String>,
) {
    fun domain(id: String) = domains.firstOrNull { it.id == id }

    /** The checklist that fits a child of this age: the latest one the child has reached. */
    fun groupForAge(months: Double): MilestoneGroup =
        groups.lastOrNull { it.months <= months + 0.01 } ?: groups.first()
}

data class VaccineDose(
    val id: String,
    val name: String,
    val description: String,
    val note: String?,
    /** Oldest age (in months) at which the dose may still be given, e.g. rotavirus 8. Null = no limit. */
    val maxAgeMonths: Int? = null,
)

data class VaccineVisit(
    val id: String,
    /** Age in months at which this visit is due (0 = at birth). */
    val months: Int,
    val title: String,
    val doses: List<VaccineDose>,
)

data class VaccineSchedule(
    val reviewed: Boolean,
    val source: String,
    val visits: List<VaccineVisit>,
)

/** [opener] returns the stream of a file under assets/ (a function, so unit tests can read the files directly). */
class ContentRepository(private val opener: (String) -> InputStream) {

    constructor(assets: AssetManager) : this({ path -> assets.open(path) })

    val pregnancy: PregnancyContent by lazy { parsePregnancy(readJson("content/pregnancy.json")) }
    val milestones: MilestoneContent by lazy { parseMilestones(readJson("content/milestones.json")) }
    val vaccines: VaccineSchedule by lazy { parseVaccines(readJson("content/vaccines_ly.json")) }
    val childDangerSigns: ChildDangerSigns by lazy { parseChildDangerSigns(readJson("content/child_danger_signs.json")) }
    val advice: AdviceContent by lazy { parseAdvice(readJson("content/islamic_advice.json")) }
    val growth: GrowthStandards by lazy {
        GrowthStandards.parseCsv(opener("content/growth_who.csv").bufferedReader().use { it.readText() })
    }

    /** Parse everything once in the background so screens open instantly. */
    fun preload() {
        pregnancy
        milestones
        vaccines
        childDangerSigns
        advice
        growth
    }

    private fun readJson(path: String): JSONObject =
        JSONObject(opener(path).bufferedReader(Charsets.UTF_8).use { it.readText() })

    private fun parsePregnancy(o: JSONObject) = PregnancyContent(
        reviewed = o.optBoolean("reviewed", false),
        weeks = o.getJSONArray("weeks").objects().map { w ->
            PregnancyWeek(
                week = w.getInt("week"),
                emoji = w.optString("emoji"),
                size = w.optString("size"),
                lengthCm = w.optNumber("lengthCm"),
                weightG = w.optNumber("weightG"),
                baby = w.optString("baby"),
                mother = w.optString("mother"),
                tip = w.optString("tip"),
            )
        }.sortedBy { it.week },
        dangerSigns = o.getJSONArray("dangerSigns").strings(),
    )

    private fun parseMilestones(o: JSONObject) = MilestoneContent(
        reviewed = o.optBoolean("reviewed", false),
        domains = o.getJSONArray("domains").objects().map {
            MilestoneDomain(it.getString("id"), it.getString("title"), it.optString("emoji"))
        },
        groups = o.getJSONArray("groups").objects().map { g ->
            MilestoneGroup(
                id = g.getString("id"),
                months = g.getInt("months"),
                title = g.getString("title"),
                items = g.getJSONArray("items").objects().map {
                    MilestoneItem(it.getString("id"), it.getString("domain"), it.getString("text"))
                },
                tips = g.optJSONArray("tips")?.strings().orEmpty(),
            )
        }.sortedBy { it.months },
        warnings = o.getJSONArray("warnings").strings(),
    )

    private fun parseChildDangerSigns(o: JSONObject) = ChildDangerSigns(
        reviewed = o.optBoolean("reviewed", false),
        sections = o.getJSONArray("sections").objects().map { s ->
            DangerSignSection(
                title = s.getString("title"),
                signs = s.getJSONArray("signs").strings(),
                maxAgeMonths = if (s.has("maxAgeMonths") && !s.isNull("maxAgeMonths")) s.getInt("maxAgeMonths") else null,
            )
        },
    )

    private fun parseAdvice(o: JSONObject) = AdviceContent(
        reviewed = o.optBoolean("reviewed", false),
        items = o.getJSONArray("items").objects().map { a ->
            AdviceItem(
                id = a.getString("id"),
                kind = AdviceKind.valueOf(a.getString("kind").uppercase()),
                audience = AdviceAudience.valueOf(a.getString("for").uppercase()),
                text = a.getString("text"),
                source = a.getString("source"),
                advice = a.getString("advice"),
                minWeeks = a.optIntOrNull("minWeeks"),
                maxWeeks = a.optIntOrNull("maxWeeks"),
                minMonths = a.optIntOrNull("minMonths"),
                maxMonths = a.optIntOrNull("maxMonths"),
                sex = if (a.has("sex") && !a.isNull("sex")) a.getString("sex") else null,
            )
        },
    )

    private fun parseVaccines(o: JSONObject) = VaccineSchedule(
        reviewed = o.optBoolean("reviewed", false),
        source = o.optString("source"),
        visits = o.getJSONArray("visits").objects().map { v ->
            VaccineVisit(
                id = v.getString("id"),
                months = v.getInt("months"),
                title = v.getString("title"),
                doses = v.getJSONArray("doses").objects().map { d ->
                    VaccineDose(
                        id = d.getString("id"),
                        name = d.getString("name"),
                        description = d.optString("description"),
                        note = if (d.has("note") && !d.isNull("note")) d.getString("note") else null,
                        maxAgeMonths = if (d.has("maxAgeMonths") && !d.isNull("maxAgeMonths")) d.getInt("maxAgeMonths") else null,
                    )
                },
            )
        }.sortedBy { it.months },
    )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

private fun JSONObject.optNumber(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null

// ---------- Islamic advice (Qur'an and authentic hadith) ----------

enum class AdviceKind { QURAN, HADITH }

/** Who an advice item is for (see "for" in islamic_advice.json). */
enum class AdviceAudience { PREGNANCY, CHILD, ALL }

data class AdviceItem(
    val id: String,
    val kind: AdviceKind,
    val audience: AdviceAudience,
    /** The exact text: a verse (shown between ﴿ ﴾) or a hadith. */
    val text: String,
    /** Where it comes from: "سورة لقمان: 14", "متفق عليه: البخاري (5971) ومسلم (2548)". */
    val source: String,
    /** A short reflection for the mother, in simple Arabic. */
    val advice: String,
    val minWeeks: Int? = null,
    val maxWeeks: Int? = null,
    val minMonths: Int? = null,
    val maxMonths: Int? = null,
    /** "boy" or "girl" when the item is about sons or daughters only. */
    val sex: String? = null,
) {
    /** The text as shown: a verse between the Qur'anic brackets ﴿ ﴾, a hadith as written. */
    val displayText: String get() = if (kind == AdviceKind.QURAN) "﴿$text﴾" else text
}

data class AdviceContent(val reviewed: Boolean, val items: List<AdviceItem>)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null
