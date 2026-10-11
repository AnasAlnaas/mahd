package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import ly.mahd.app.logic.PostpartumRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PostpartumTest {
    private val content = ContentRepository { path -> File("src/main/assets/$path").inputStream() }
    private val postpartum = content.postpartum
    private val bag = content.hospitalBag

    @Test
    fun stagesCoverEveryDayOfTheFortyAndAfter() {
        assertFalse("medical content stays a draft until a doctor reviews it", postpartum.reviewed)
        assertEquals(1, postpartum.stages.first().fromDay)
        postpartum.stages.zipWithNext().forEach { (a, b) -> assertEquals("gap after day ${a.toDay}", a.toDay + 1, b.fromDay) }
        assertTrue(postpartum.lastDay > PostpartumRules.DAYS)
        postpartum.stages.forEach { s ->
            assertTrue("${s.title}: empty", s.normal.isNotEmpty() && s.tips.isNotEmpty() && s.tipsFather.isNotEmpty())
            // Until the end of the forty, each kind of birth has its own word.
            if (s.toDay <= PostpartumRules.DAYS) assertTrue("${s.title}: birth type", s.natural.isNotEmpty() && s.caesarean.isNotEmpty())
        }
        assertEquals("الأيام الأولى", postpartum.stage(2).title)
        assertEquals(postpartum.stages.last(), postpartum.stage(99))
    }

    @Test
    fun sharedTextSpeaksToNeitherParentAndTheFathersToHim() {
        // "كِ" / "تِ" would read wrong to the father.
        val shared = postpartum.stages.flatMap { it.normal + it.natural + it.caesarean } +
            postpartum.emergencySigns + postpartum.emergencySignsCaesarean + postpartum.soonSigns +
            postpartum.checkups.flatMap { listOf(it.title, it.text) } + postpartum.customs.flatMap { listOf(it.title, it.verdict, it.text) } +
            postpartum.moodFather + postpartum.stages.flatMap { it.tipsFather } +
            bag.groups.flatMap { g -> g.items.map { it.text } + g.title }
        shared.forEach { assertTrue("'$it' speaks to the mother", "كِ" !in it && "تِ" !in it) }
    }

    @Test
    fun checkupsFollowTheWho() {
        val checkups = postpartum.checkups
        assertEquals(listOf("day3", "week2", "week6"), checkups.map { it.id })
        assertTrue(checkups.all { it.fromDay <= it.toDay && it.toDay <= postpartum.lastDay })
        assertEquals("day3", PostpartumRules.checkupDue(checkups, 3)?.id) // 48–72 hours after birth
        assertNull(PostpartumRules.checkupDue(checkups, 6))
        assertEquals("week2", PostpartumRules.checkupDue(checkups, 8)?.id) // day 7 after birth
        assertEquals("week2", PostpartumRules.checkupDue(checkups, 12)?.id) // the daily check ran late
        assertEquals("week6", PostpartumRules.checkupDue(checkups, 36)?.id)
        assertNull(PostpartumRules.checkupDue(checkups, postpartum.lastDay + 1))

        val birth = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 8) to LocalDate.of(2026, 10, 15), PostpartumRules.window(checkups[1], birth))
    }

    @Test
    fun theFortyDaysAreCountedFromTheDayOfBirth() {
        val birth = LocalDate.of(2026, 10, 1)
        assertEquals(1, PostpartumRules.day(birth, birth))
        assertEquals(40, PostpartumRules.day(birth, birth.plusDays(39)))
        assertTrue(PostpartumRules.isActive(birth, birth, postpartum.lastDay))
        assertTrue(PostpartumRules.isActive(birth, birth.plusDays(postpartum.lastDay - 1L), postpartum.lastDay))
        assertFalse(PostpartumRules.isActive(birth, birth.plusDays(postpartum.lastDay.toLong()), postpartum.lastDay))
        assertFalse(PostpartumRules.isActive(birth, birth.minusDays(1), postpartum.lastDay))
    }

    @Test
    fun moodIsInformationOnlyForBothParents() {
        // The app asks the mother no questions (owner's decision); she reads when to ask for help.
        assertTrue(postpartum.moodMother.isNotEmpty() && postpartum.moodFather.isNotEmpty())
        assertTrue(postpartum.moodMother.none { it.trimEnd().endsWith("؟") })
    }

    @Test
    fun hospitalBag() {
        val ids = bag.allIds
        assertTrue(ids.size >= 25)
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        // Breastfeeding only: no bottles or formula in the bag.
        bag.groups.flatMap { it.items }.forEach { item ->
            assertTrue(item.text, listOf("رضّاعة", "رضاعة صناعية", "زجاجة", "صناعي", "ببرونة").none { it in item.text })
        }
    }
}
