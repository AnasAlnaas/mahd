package ly.mahd.app.content

import android.content.res.AssetManager
import ly.mahd.app.data.Parent
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
    /** For the father, instead of the mother's "your body" and tip: what his wife may be going through, and how to help. */
    val fatherStages: List<FatherStage> = emptyList(),
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
    /** The same, written for the father: short things a working father can do. */
    val tipsFather: List<String> = emptyList(),
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

    /** Every checklist the child has reached; skills of older ages stay hidden until the child gets there. */
    fun reachedGroups(months: Double): List<MilestoneGroup> = groups.filter { it.months <= months + 0.01 }

    /** The next checklist the child has not reached yet, or null after the last one. */
    fun nextGroup(months: Double): MilestoneGroup? = groups.firstOrNull { it.months > months + 0.01 }
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
    val postpartum: PostpartumContent by lazy { parsePostpartum(readJson("content/postpartum.json")) }
    val hospitalBag: HospitalBag by lazy { parseHospitalBag(readJson("content/hospital_bag.json")) }
    val exercises: ExercisesContent by lazy { parseExercises(readJson("content/exercises.json")) }
    /** Today's word on Home: the verses and hadith, then the science facts (each file has its own review flag). */
    val advice: AdviceContent by lazy {
        val islamic = parseAdvice(readJson("content/islamic_advice.json"))
        val facts = parseAdvice(readJson("content/science_facts.json"))
        AdviceContent(islamic.reviewed, islamic.items + facts.items, factsReviewed = facts.reviewed)
    }
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
        fatherStages = o.optJSONArray("fatherStages")?.objects().orEmpty().map {
            FatherStage(it.getInt("fromWeek"), it.getInt("toWeek"), it.getString("wife"), it.getString("support"))
        },
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
                tipsFather = g.optJSONArray("tipsFather")?.strings().orEmpty(),
            )
        }.sortedBy { it.months },
        warnings = o.getJSONArray("warnings").strings(),
    )

    private fun parsePostpartum(o: JSONObject) = PostpartumContent(
        reviewed = o.optBoolean("reviewed", false),
        stages = o.getJSONArray("stages").objects().map { s ->
            PostpartumStage(
                fromDay = s.getInt("fromDay"),
                toDay = s.getInt("toDay"),
                title = s.getString("title"),
                normal = s.getJSONArray("normal").strings(),
                natural = s.getJSONArray("natural").strings(),
                caesarean = s.getJSONArray("caesarean").strings(),
                tips = s.getJSONArray("tips").strings(),
                tipsFather = s.getJSONArray("tipsFather").strings(),
            )
        }.sortedBy { it.fromDay },
        emergencySigns = o.getJSONArray("emergencySigns").strings(),
        emergencySignsCaesarean = o.getJSONArray("emergencySignsCaesarean").strings(),
        soonSigns = o.getJSONArray("soonSigns").strings(),
        checkups = o.getJSONArray("checkups").objects().map { c ->
            PostnatalCheckup(c.getString("id"), c.getInt("fromDay"), c.getInt("toDay"), c.getString("title"), c.getString("text"))
        }.sortedBy { it.fromDay },
        moodMother = o.getJSONArray("moodMother").strings(),
        moodFather = o.getJSONArray("moodFather").strings(),
        customs = o.getJSONArray("customs").objects().map { c ->
            PostpartumCustom(c.getString("id"), c.getString("title"), c.getString("verdict"), c.getString("text"), c.getString("source"))
        },
    )

    private fun parseHospitalBag(o: JSONObject) = HospitalBag(
        groups = o.getJSONArray("groups").objects().map { g ->
            BagGroup(
                id = g.getString("id"),
                title = g.getString("title"),
                items = g.getJSONArray("items").objects().map { BagItem(it.getString("id"), it.getString("text")) },
            )
        },
    )

    private fun parseExercises(o: JSONObject): ExercisesContent {
        fun part(p: JSONObject) = ExercisePart(
            intro = p.getJSONArray("intro").strings(),
            natural = p.getJSONArray("natural").strings(),
            caesarean = p.getJSONArray("caesarean").strings(),
            stages = p.getJSONArray("stages").objects().map { s ->
                ExerciseStage(
                    id = s.getString("id"),
                    title = s.getString("title"),
                    fromWeek = s.optIntOrNull("fromWeek"),
                    toWeek = s.optIntOrNull("toWeek"),
                    about = s.getString("about"),
                    exercises = s.getJSONArray("exercises").objects().map { e ->
                        Exercise(
                            id = e.getString("id"),
                            title = e.getString("title"),
                            steps = e.getJSONArray("steps").strings(),
                            dose = e.optStringOrNull("dose"),
                            warning = e.optStringOrNull("warning"),
                            caesarean = e.optStringOrNull("caesarean"),
                            images = e.optJSONArray("images")?.strings().orEmpty(),
                        )
                    },
                )
            },
            avoid = p.getJSONArray("avoid").strings(),
            stopSigns = p.getJSONArray("stopSigns").strings(),
            father = p.getJSONArray("father").strings(),
            source = p.getString("source"),
        )
        return ExercisesContent(
            reviewed = o.optBoolean("reviewed", false),
            pregnancy = part(o.getJSONObject("pregnancy")),
            postpartum = part(o.getJSONObject("postpartum")),
            pictureCredit = o.optStringOrNull("pictureCredit")?.takeIf { it.isNotBlank() },
        )
    }

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
                parent = a.optString("parent", "mother"),
                adviceFather = if (a.has("adviceFather")) a.getString("adviceFather") else null,
                seasons = a.optJSONArray("seasons")?.strings()?.toSet() ?: emptySet(),
                occasions = a.optJSONArray("occasions")?.strings()?.toSet() ?: emptySet(),
                situations = a.optJSONArray("situations")?.strings()?.toSet() ?: emptySet(),
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

// ---------- الأربعين: the mother's recovery after birth ----------

/** Days [fromDay]..[toDay] after birth (the day of birth is day 1). */
data class PostpartumStage(
    val fromDay: Int,
    val toDay: Int,
    val title: String,
    /** Expected after any birth. */
    val normal: List<String>,
    val natural: List<String>,
    val caesarean: List<String>,
    /** Written to the mother. */
    val tips: List<String>,
    val tipsFather: List<String>,
)

data class PostnatalCheckup(val id: String, val fromDay: Int, val toDay: Int, val title: String, val text: String)

/** A الأربعين custom weighed against the evidence: [verdict] in a few words, then why. */
data class PostpartumCustom(val id: String, val title: String, val verdict: String, val text: String, val source: String)

data class PostpartumContent(
    val reviewed: Boolean,
    val stages: List<PostpartumStage>,
    val emergencySigns: List<String>,
    val emergencySignsCaesarean: List<String>,
    val soonSigns: List<String>,
    val checkups: List<PostnatalCheckup>,
    /** Written to the mother: when low mood needs help (information only; the app asks her no questions). */
    val moodMother: List<String>,
    /** For the father: how to tell whether his wife's low mood needs help. */
    val moodFather: List<String>,
    val customs: List<PostpartumCustom>,
) {
    /** The stage for day [day] of الأربعين (the last one after the end). */
    fun stage(day: Int): PostpartumStage = stages.firstOrNull { day in it.fromDay..it.toDay } ?: stages.last()

    /** The last day the الأربعين section is shown (just after the six-week check-up). */
    val lastDay: Int get() = stages.maxOf { it.toDay }
}

// ---------- The mother's exercises, in pregnancy and after birth ----------

/**
 * One exercise: how to do it, step by step, how often ([dose]) and what to watch for ([warning]); [caesarean] is shown
 * unless the birth was natural. [images] are picture names (assets/exercises/<name>.webp), one or two poses.
 */
data class Exercise(
    val id: String,
    val title: String,
    val steps: List<String>,
    val dose: String?,
    val warning: String?,
    val caesarean: String?,
    val images: List<String>,
)

/** A group of exercises: a trimester (weeks [fromWeek]..[toWeek]) or the time after birth (no weeks). */
data class ExerciseStage(
    val id: String,
    val title: String,
    val fromWeek: Int?,
    val toWeek: Int?,
    val about: String,
    val exercises: List<Exercise>,
) {
    fun covers(week: Int): Boolean = fromWeek != null && toWeek != null && week in fromWeek..toWeek
}

/** Exercises for one time (pregnancy or after birth). All text speaks to neither parent except [father]. */
data class ExercisePart(
    val intro: List<String>,
    /** After a natural birth / a caesarean only (empty in pregnancy). */
    val natural: List<String>,
    val caesarean: List<String>,
    val stages: List<ExerciseStage>,
    val avoid: List<String>,
    /** Stop and see the doctor. */
    val stopSigns: List<String>,
    /** For the father: how to help. */
    val father: List<String>,
    val source: String,
) {
    val exercises: List<Exercise> get() = stages.flatMap { it.exercises }

    /** The stages in the order shown: the one for pregnancy week [week] first, then the others in order. */
    fun stagesFor(week: Int?): List<ExerciseStage> {
        val now = stages.firstOrNull { week != null && it.covers(week) } ?: return stages
        return listOf(now) + (stages - now)
    }
}

/** [pictureCredit]: who made the pictures, when their licence asks for a credit (shown under the sources). */
data class ExercisesContent(
    val reviewed: Boolean,
    val pregnancy: ExercisePart,
    val postpartum: ExercisePart,
    val pictureCredit: String? = null,
) {
    /** Every picture name, in order. */
    val pictureNames: List<String> get() = (pregnancy.exercises + postpartum.exercises).flatMap { it.images }
}

// ---------- Hospital bag ----------

data class BagItem(val id: String, val text: String)

data class BagGroup(val id: String, val title: String, val items: List<BagItem>)

data class HospitalBag(val groups: List<BagGroup>) {
    val allIds: List<String> get() = groups.flatMap { g -> g.items.map { it.id } }
}

// ---------- Daily advice (Qur'an, authentic hadith, science facts) ----------

enum class AdviceKind { QURAN, HADITH, FACT }

/** Who an advice item is for (see "for" in islamic_advice.json). */
enum class AdviceAudience { PREGNANCY, CHILD, ALL }

data class AdviceItem(
    val id: String,
    val kind: AdviceKind,
    val audience: AdviceAudience,
    /** The exact text: a verse (shown between ﴿ ﴾), a hadith, or a science fact. */
    val text: String,
    /** Where it comes from: "سورة لقمان: 14", "متفق عليه: البخاري (5971) ومسلم (2548)", "منظمة الصحة العالمية". */
    val source: String,
    /** A short reflection for the mother, in simple Arabic. */
    val advice: String,
    val minWeeks: Int? = null,
    val maxWeeks: Int? = null,
    val minMonths: Int? = null,
    val maxMonths: Int? = null,
    /** "boy" or "girl" when the item is about sons or daughters only. */
    val sex: String? = null,
    /** "mother" (the default), "father" or "both": who sees this item. */
    val parent: String = "mother",
    /** For a "both" item: the word for the father (when [advice] is written to the mother). */
    val adviceFather: String? = null,
    /** Shown only in these seasons ("summer", …; see logic.Season). Empty = all year. */
    val seasons: Set<String> = emptySet(),
    /** Shown only during one of these occasions ("ramadan", "eid_fitr", …; see logic.Occasion). */
    val occasions: Set<String> = emptySet(),
    /** Shown only to a family in one of these situations ("siblings", "twins", …; see logic.Situation). */
    val situations: Set<String> = emptySet(),
) {
    /** True for an item tied to the season, an occasion or the family, rather than shown all year to everyone. */
    val isContextual: Boolean get() = seasons.isNotEmpty() || occasions.isNotEmpty() || situations.isNotEmpty()

    /** The text as shown: a verse between the Qur'anic brackets ﴿ ﴾, a hadith as written. */
    val displayText: String get() = if (kind == AdviceKind.QURAN) "﴿$text﴾" else text
}

/** [reviewed] is for the verses and hadith, [factsReviewed] for the science facts (medical content). */
data class AdviceContent(val reviewed: Boolean, val items: List<AdviceItem>, val factsReviewed: Boolean = false)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

/** One stage of the pregnancy for the father, weeks [fromWeek]..[toWeek] (inclusive). */
data class FatherStage(val fromWeek: Int, val toWeek: Int, val wife: String, val support: String)

/** The father's stage for a pregnancy week (weeks outside the table use the nearest stage). */
fun PregnancyContent.fatherStage(week: Int): FatherStage? =
    fatherStages.firstOrNull { week in it.fromWeek..it.toWeek }
        ?: fatherStages.minByOrNull { minOf(kotlin.math.abs(week - it.fromWeek), kotlin.math.abs(week - it.toWeek)) }

/** The tips for whoever uses the app (the mother's tips if a group has no father tips). */
fun MilestoneGroup.tipsFor(parent: Parent): List<String> =
    if (parent == Parent.FATHER && tipsFather.isNotEmpty()) tipsFather else tips

/** Whether [parent] sees this advice item. */
fun AdviceItem.isFor(parent: Parent): Boolean = when (this.parent) {
    "both" -> true
    "father" -> parent == Parent.FATHER
    else -> parent == Parent.MOTHER
}

/** The short word under the text, written to whoever reads it. */
fun AdviceItem.adviceFor(parent: Parent): String = if (parent == Parent.FATHER) adviceFather ?: advice else advice
