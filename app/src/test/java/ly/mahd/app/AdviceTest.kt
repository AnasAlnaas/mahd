package ly.mahd.app

import ly.mahd.app.content.AdviceAudience
import ly.mahd.app.content.AdviceKind
import ly.mahd.app.content.ContentRepository
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.AdviceRules
import ly.mahd.app.logic.Occasion
import ly.mahd.app.logic.Season
import ly.mahd.app.logic.Situation
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
    fun notificationsTwiceAWeekAndOnTheFirstDayOfRamadanAndTheEids() {
        val week = (0L until 7L).map { today.plusDays(it) }
        assertEquals(2, week.count(AdviceRules::isNotificationDay))
        assertTrue(AdviceRules.isNotificationDay(LocalDate.of(2026, 2, 18))) // 1 Ramadan, a Wednesday
        assertTrue(AdviceRules.isNotificationDay(LocalDate.of(2026, 5, 27))) // Eid al-Adha, a Wednesday
    }

    private fun childOn(day: LocalDate, months: Long, id: Long = 2) =
        Profile(id, ProfileKind.CHILD, "", Sex.BOY, day.minusMonths(months).minusDays(1), null, null)

    @Test
    fun contextCodesAreKnown() {
        val seasons = Season.entries.map { it.code }.toSet()
        val occasions = Occasion.entries.map { it.code }.toSet()
        val situations = Situation.entries.map { it.code }.toSet()
        items.forEach { a ->
            assertTrue("${a.id}: season", seasons.containsAll(a.seasons))
            assertTrue("${a.id}: occasion", occasions.containsAll(a.occasions))
            assertTrue("${a.id}: situation", situations.containsAll(a.situations))
        }
    }

    @Test
    fun seasonalItemsFollowTheWeather() {
        val summer = LocalDate.of(2026, 7, 15)
        val winter = LocalDate.of(2027, 1, 15)
        assertTrue(AdviceRules.eligible(items, childOn(summer, 12), summer).any { it.id == "s_drowning" })
        assertTrue(AdviceRules.eligible(items, childOn(winter, 12), winter).none { it.id == "s_drowning" })
        assertTrue(AdviceRules.eligible(items, childOn(winter, 12), winter).any { it.id == "s_carbon_monoxide" })
        // A seasonal item does come up within a fortnight, and still no text twice.
        val p = childOn(summer, 12)
        val fortnight = (0L until 14L).map { AdviceRules.forDay(items, p, summer.plusDays(it))!! }
        assertTrue(fortnight.any { "summer" in it.seasons })
        assertEquals(14, fortnight.toSet().size)
        assertEquals("summer", AdviceRules.reason(items.first { it.id == "s_drowning" }, p, summer))
    }

    @Test
    fun ramadanAndTheEids() {
        // The first three days of Ramadan show Ramadan's own items, a different one each day.
        val ramadan = LocalDate.of(2026, 2, 18)
        val preg = Profile(1, ProfileKind.PREGNANCY, "", null, null, ramadan.plusWeeks(20), null)
        val first = (0L until 3L).map { AdviceRules.forDay(items, preg, ramadan.plusDays(it))!! }
        assertTrue(first.all { "ramadan" in it.occasions })
        assertEquals(3, first.toSet().size)

        // Each day of Eid al-Fitr shows an Eid item; none of them, nor Ramadan's, after it.
        val eid = LocalDate.of(2026, 3, 20)
        val child = childOn(eid, 12)
        val eidDays = (0L until 3L).map { AdviceRules.forDay(items, child, eid.plusDays(it))!! }
        assertTrue(eidDays.all { "eid_fitr" in it.occasions || "celebration" in it.occasions })
        assertEquals(3, eidDays.toSet().size)
        assertTrue(AdviceRules.eligible(items, child, eid.plusDays(10)).none { it.occasions.isNotEmpty() })
        // Fireworks on an Eid are labelled with the Eid.
        assertEquals("eid_fitr", AdviceRules.reason(items.first { it.id == "s_fireworks" }, child, eid))

        val arafah = LocalDate.of(2026, 5, 26)
        assertEquals("h_arafah", AdviceRules.forDay(items, childOn(arafah, 12), arafah)!!.id)
    }

    @Test
    fun familyItemsFollowTheProfilesOnThePhone() {
        val one = child(12)
        val older = child(40, id = 3)
        val twin = child(12, id = 4)
        assertTrue(AdviceRules.eligible(items, one, today).none { it.situations.isNotEmpty() })
        val withSibling = AdviceRules.eligible(items, one, today, family = listOf(one, older))
        assertTrue(withSibling.any { it.id == "h_adl" })
        assertTrue(withSibling.none { "twins" in it.situations })
        assertTrue(AdviceRules.eligible(items, one, today, family = listOf(one, twin)).any { it.id == "s_twins_pace" })

        val preg = pregnancy(20)
        assertTrue(AdviceRules.eligible(items, preg, today, family = listOf(preg, one)).any { it.id == "s_new_sibling" })
        assertTrue(AdviceRules.eligible(items, preg, today).none { it.id == "s_new_sibling" })

        val preterm = Profile(5, ProfileKind.CHILD, "", Sex.GIRL, today.minusMonths(1).minusDays(1), null, 32)
        assertTrue(AdviceRules.eligible(items, preterm, today).any { it.id == "s_preterm_kangaroo" })
    }
}
