package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import ly.mahd.app.content.adviceFor
import ly.mahd.app.content.fatherStage
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.AdviceRules
import ly.mahd.app.util.FATHER_STRINGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class FatherTest {
    private val content = ContentRepository { path -> File("src/main/assets/$path").inputStream() }
    private val today = LocalDate.of(2026, 10, 9)

    /** name -> text of every <string> in a resource file. */
    private fun strings(file: String): Map<String, String> =
        Regex("""<string name="([a-z0-9_]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File("src/main/res/values/$file").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun stringId(name: String): Int = R.string::class.java.getField(name).getInt(null)

    @Test
    fun everyFatherStringReplacesAMotherStringWithTheSamePlaceholders() {
        val mother = strings("strings.xml")
        val father = strings("strings_father.xml")
        assertTrue(father.isNotEmpty())
        val placeholders = Regex("%\\d\\\$s")
        father.forEach { (name, text) ->
            assertTrue("$name must end in _m", name.endsWith("_m"))
            val base = name.removeSuffix("_m")
            val motherText = mother[base]
            assertNotNull("$name has no mother string '$base'", motherText)
            assertEquals("$name: placeholders differ", placeholders.findAll(motherText!!).map { it.value }.toSet(), placeholders.findAll(text).map { it.value }.toSet())
            assertEquals("$name is not in FATHER_STRINGS", stringId(name), FATHER_STRINGS[stringId(base)])
        }
        assertEquals("FATHER_STRINGS has entries without a father string", father.size, FATHER_STRINGS.size)
    }

    @Test
    fun fatherStagesCoverTheWholePregnancy() {
        val stages = content.pregnancy.fatherStages
        assertEquals(1, stages.first().fromWeek)
        assertEquals(42, stages.last().toWeek)
        stages.zipWithNext().forEach { (a, b) -> assertEquals("gap or overlap after week ${a.toWeek}", a.toWeek + 1, b.fromWeek) }
        stages.forEach { assertTrue(it.wife.isNotBlank() && it.support.isNotBlank()) }
        for (w in 1..42) assertNotNull(content.pregnancy.fatherStage(w))
    }

    @Test
    fun everyMilestoneAgeHasFatherTips() {
        content.milestones.groups.forEach { g -> assertTrue("${g.id} has no father tips", g.tipsFather.size >= 2) }
    }

    @Test
    fun milestoneItemsSpeakToNeitherParentAsYouFeminine() {
        // e.g. "تكلّمينه", "تحملينه": a 2nd-person feminine verb would read wrong to the father.
        val feminineVerb = Regex("""(^|\s)ت\S*ين(ه|ها)?(\s|$|[،.])""")
        content.milestones.groups.flatMap { it.items }.forEach { item ->
            assertTrue("'${item.text}' speaks to the mother", !feminineVerb.containsMatchIn(item.text) && "تِ" !in item.text)
        }
    }

    @Test
    fun theFatherHasAdviceAtEveryStageAndItIsWrittenToHim() {
        val items = content.advice.items
        assertTrue(items.all { it.parent in setOf("mother", "father", "both") })
        assertTrue(items.count { it.parent == "father" } >= 10)
        for (week in 4..42) {
            val pregnancy = Profile(1, ProfileKind.PREGNANCY, "", null, null, today.plusWeeks(40L - week), null)
            val shown = AdviceRules.eligible(items, pregnancy, today, Parent.FATHER)
            assertTrue("father, pregnancy week $week", shown.size >= 10)
            assertTrue(shown.none { it.parent == "mother" })
        }
        for (months in 0L..60L) {
            val child = Profile(2, ProfileKind.CHILD, "", Sex.GIRL, today.minusMonths(months).minusDays(1), null, null)
            assertTrue("father, child of $months months", AdviceRules.eligible(items, child, today, Parent.FATHER).size >= 10)
        }
        // A father-only item never reaches the mother.
        val child = Profile(2, ProfileKind.CHILD, "", Sex.BOY, today.minusMonths(20), null, null)
        assertTrue(AdviceRules.eligible(items, child, today, Parent.MOTHER).none { it.parent == "father" })
        // What the father reads under a shared text is his own word whenever the mother's is written to her.
        items.filter { it.parent == "both" && "كِ" in it.advice }.forEach {
            assertTrue("${it.id} needs adviceFather", it.adviceFather != null && "كِ" !in it.adviceFor(Parent.FATHER))
        }
    }
}
