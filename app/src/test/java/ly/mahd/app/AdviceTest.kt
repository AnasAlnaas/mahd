package ly.mahd.app

import ly.mahd.app.content.AdviceAudience
import ly.mahd.app.content.AdviceKind
import ly.mahd.app.content.ContentRepository
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.AdviceRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class AdviceTest {
    private val items = ContentRepository { path -> File("src/main/assets/$path").inputStream() }.advice.items
    private val today = LocalDate.of(2026, 10, 9)

    private fun pregnancy(week: Int, id: Long = 1) =
        Profile(id, ProfileKind.PREGNANCY, "", null, null, today.plusWeeks(40L - week), null)

    private fun child(months: Long, sex: Sex? = Sex.BOY, id: Long = 2) =
        Profile(id, ProfileKind.CHILD, "", sex, today.minusMonths(months).minusDays(1), null, null)

    @Test
    fun fileIsFilledIn() {
        assertTrue(items.size >= 30)
        assertEquals("duplicate ids", items.size, items.map { it.id }.toSet().size)
        items.forEach { a ->
            assertTrue("${a.id} has an empty field", a.text.isNotBlank() && a.source.isNotBlank() && a.advice.isNotBlank())
            // Every hadith names al-Bukhari or Muslim; every verse names its sura.
            if (a.kind == AdviceKind.HADITH) assertTrue("${a.id}: source", "البخاري" in a.source || "مسلم" in a.source)
            if (a.kind == AdviceKind.QURAN) assertTrue("${a.id}: source", a.source.startsWith("سورة"))
            // Science facts live in their own file and are never presented as Qur'an or hadith.
            if (a.kind == AdviceKind.FACT) assertTrue("${a.id}: fact id", a.id.startsWith("s_"))
            // The app adds the Qur'anic brackets itself.
            assertFalse("${a.id}: brackets in the text", '﴿' in a.text || '﴾' in a.text)
            assertTrue("${a.id}: bad sex", a.sex == null || a.sex == "boy" || a.sex == "girl")
            if (a.audience == AdviceAudience.PREGNANCY) assertTrue(a.minMonths == null && a.maxMonths == null)
            if (a.audience == AdviceAudience.CHILD) assertTrue(a.minWeeks == null && a.maxWeeks == null)
        }
    }

    @Test
    fun scienceFactsAreADraftMixedWithTheTexts() {
        val content = ContentRepository { path -> File("src/main/assets/$path").inputStream() }.advice
        val facts = items.filter { it.kind == AdviceKind.FACT }
        assertTrue(facts.size >= 30)
        assertFalse("medical content stays a draft until a doctor reviews it", content.factsReviewed)
        // Neither the verses and hadith nor the facts take over a stage.
        for (week in 4..42) {
            val shown = AdviceRules.eligible(items, pregnancy(week), today)
            assertTrue("pregnancy week $week", shown.any { it.kind == AdviceKind.FACT } && shown.any { it.kind != AdviceKind.FACT })
        }
        for (months in 0L..60L) {
            val shown = AdviceRules.eligible(items, child(months), today)
            assertTrue("child of $months months", shown.any { it.kind == AdviceKind.FACT } && shown.any { it.kind != AdviceKind.FACT })
        }
    }

    @Test
    fun everyStageHasSeveralItems() {
        for (week in 4..42) {
            assertTrue("pregnancy week $week", AdviceRules.eligible(items, pregnancy(week), today).size >= 10)
        }
        for (months in 0L..60L) {
            assertTrue("child of $months months", AdviceRules.eligible(items, child(months), today).size >= 10)
        }
    }

    @Test
    fun itemsFollowTheStage() {
        val preg = AdviceRules.eligible(items, pregnancy(12), today)
        assertTrue(preg.none { it.audience == AdviceAudience.CHILD })
        assertTrue(preg.none { it.id == "h_karb" }) // from week 34
        assertTrue(AdviceRules.eligible(items, pregnancy(36), today).any { it.id == "h_karb" })

        val baby = AdviceRules.eligible(items, child(2), today)
        assertTrue(baby.none { it.audience == AdviceAudience.PREGNANCY })
        assertTrue(baby.any { it.id == "q_baqarah_233_a" })
        assertTrue(AdviceRules.eligible(items, child(30), today).none { it.id == "q_baqarah_233_a" }) // under 2 years only
        assertTrue(AdviceRules.eligible(items, child(0), today).any { it.id == "q_al_imran_36" })
        assertTrue(AdviceRules.eligible(items, child(3), today).none { it.id == "q_al_imran_36" })
        // An "all" item can be limited in both: the colostrum fact from week 34 until the first month.
        assertTrue(AdviceRules.eligible(items, pregnancy(30), today).none { it.id == "s_colostrum" })
        assertTrue(AdviceRules.eligible(items, pregnancy(36), today).any { it.id == "s_colostrum" })
        assertTrue(AdviceRules.eligible(items, child(0), today).any { it.id == "s_colostrum" })
        assertTrue(AdviceRules.eligible(items, child(2), today).none { it.id == "s_colostrum" })

        assertTrue(AdviceRules.eligible(items, child(12, Sex.GIRL), today).any { it.sex == "girl" })
        assertTrue(AdviceRules.eligible(items, child(12, Sex.BOY), today).none { it.sex == "girl" })
    }

    @Test
    fun oneItemPerDayAndItChanges() {
        val p = child(10)
        val first = AdviceRules.forDay(items, p, today)
        assertEquals(first, AdviceRules.forDay(items, p, today))
        assertNotEquals(first, AdviceRules.forDay(items, p, today.plusDays(1)))
        // Over a fortnight she sees fourteen different texts.
        assertEquals(14, (0L until 14L).map { AdviceRules.forDay(items, p, today.plusDays(it))!!.id }.toSet().size)
    }

    @Test
    fun notificationsTwiceAWeek() {
        val week = (0L until 7L).map { today.plusDays(it) }
        assertEquals(2, week.count(AdviceRules::isNotificationDay))
    }
}
