package ly.mahd.app

import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.content.GrowthStandards
import ly.mahd.app.content.VaccineDose
import ly.mahd.app.content.VaccineSchedule
import ly.mahd.app.content.VaccineVisit
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.GrowthMath
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.logic.VaccinePlanner
import ly.mahd.app.ui.screens.parseNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PregnancyMathTest {
    @Test
    fun dueDateIs280DaysAfterLastPeriod() {
        assertEquals(LocalDate.of(2026, 10, 8), PregnancyMath.dueDateFromLastPeriod(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun gestationalAgeInWeeksAndDays() {
        val due = LocalDate.of(2026, 10, 8) // last period 2026-01-01
        assertEquals(PregnancyMath.GestationalAge(12, 0), PregnancyMath.gestationalAge(due, LocalDate.of(2026, 3, 26)))
        assertEquals(PregnancyMath.GestationalAge(20, 3), PregnancyMath.gestationalAge(due, LocalDate.of(2026, 5, 24)))
        assertEquals(PregnancyMath.GestationalAge(0, 0), PregnancyMath.gestationalAge(due, LocalDate.of(2025, 12, 1)))
    }

    @Test
    fun trimesters() {
        assertEquals(1, PregnancyMath.trimester(13))
        assertEquals(2, PregnancyMath.trimester(14))
        assertEquals(2, PregnancyMath.trimester(27))
        assertEquals(3, PregnancyMath.trimester(28))
    }

    @Test
    fun plausibleDueDates() {
        val today = LocalDate.of(2026, 10, 2)
        assertTrue(PregnancyMath.isPlausibleDueDate(today.plusWeeks(20), today))
        assertTrue(PregnancyMath.isPlausibleDueDate(today.minusDays(3), today)) // a little late
        assertFalse(PregnancyMath.isPlausibleDueDate(today.plusWeeks(45), today)) // not pregnant yet
        assertFalse(PregnancyMath.isPlausibleDueDate(today.minusWeeks(6), today)) // far too late
    }
}

class ChildAgeTest {
    @Test
    fun correctedAgeForPrematureBaby() {
        val birth = LocalDate.of(2026, 1, 1)
        assertEquals(LocalDate.of(2026, 2, 26), ChildAge.correctedBirthDate(birth, 32))
        assertEquals(birth, ChildAge.correctedBirthDate(birth, 38))
        assertEquals(birth, ChildAge.correctedBirthDate(birth, null))
    }

    @Test
    fun correctedAgeStopsAtTwoYears() {
        val birth = LocalDate.of(2024, 1, 1)
        assertTrue(ChildAge.usesCorrectedAge(birth, 30, LocalDate.of(2025, 6, 1)))
        assertFalse(ChildAge.usesCorrectedAge(birth, 30, LocalDate.of(2026, 2, 1)))
        assertFalse(ChildAge.usesCorrectedAge(birth, 39, LocalDate.of(2025, 6, 1)))
    }
}

class GrowthTest {
    private val standards = GrowthStandards.parseCsv(File("src/main/assets/content/growth_who.csv").readText())

    @Test
    fun whoTablesLoadForAllIndicatorsAndBothSexes() {
        for (indicator in GrowthIndicator.entries) for (sex in 1..2) {
            assertNotNull(standards.lms(indicator, sex, 0.0))
            assertNotNull(standards.lms(indicator, sex, 60.0))
        }
        assertNull(standards.lms(GrowthIndicator.WEIGHT, 1, 61.0))
    }

    @Test
    fun medianBoyAtBirthHasZScoreZero() {
        val lms = standards.lms(GrowthIndicator.WEIGHT, 1, 0.0)!!
        assertEquals(3.3464, lms.m, 1e-6)
        assertEquals(0.0, lms.zScore(3.3464), 1e-9)
    }

    @Test
    fun plusTwoSdMatchesWhoTable() {
        // WHO weight-for-age, boys, birth: +2 SD = 4.4 kg
        val lms = standards.lms(GrowthIndicator.WEIGHT, 1, 0.0)!!
        assertEquals(4.42, lms.valueAt(2.0), 0.01)
        assertEquals(2.0, lms.zScore(lms.valueAt(2.0)), 1e-9)
    }

    @Test
    fun interpolatesBetweenMonths() {
        val a = standards.lms(GrowthIndicator.LENGTH, 2, 3.0)!!
        val b = standards.lms(GrowthIndicator.LENGTH, 2, 4.0)!!
        val mid = standards.lms(GrowthIndicator.LENGTH, 2, 3.5)!!
        assertEquals((a.m + b.m) / 2, mid.m, 1e-9)
    }

    @Test
    fun percentilesAndBands() {
        assertEquals(50.0, GrowthMath.percentile(0.0), 1e-6)
        assertEquals(97.0, GrowthMath.percentile(1.881), 0.05)
        assertEquals(GrowthMath.Band.NORMAL, GrowthMath.band(1.9))
        assertEquals(GrowthMath.Band.LOW, GrowthMath.band(-2.5))
        assertEquals(GrowthMath.Band.VERY_HIGH, GrowthMath.band(3.2))
    }
}

class VaccinePlannerTest {
    private val schedule = VaccineSchedule(
        reviewed = false,
        source = "test",
        visits = listOf(
            VaccineVisit("birth", 0, "birth", listOf(VaccineDose("bcg", "BCG", "", null))),
            VaccineVisit("m2", 2, "2m", listOf(VaccineDose("a1", "A", "", null), VaccineDose("b1", "B", "", null))),
            VaccineVisit("m4", 4, "4m", listOf(VaccineDose("a2", "A", "", null))),
        ),
    )
    private val birth = LocalDate.of(2026, 1, 15)

    @Test
    fun statusesFollowDueDatesAndRecords() {
        val today = LocalDate.of(2026, 3, 20) // 2-month visit was due 2026-03-15
        val plans = VaccinePlanner.plan(schedule, birth, mapOf("bcg" to birth, "a1" to LocalDate.of(2026, 3, 16)), today)
        assertEquals(VaccinePlanner.Status.DONE, plans[0].status)
        assertEquals(VaccinePlanner.Status.PARTLY_DONE, plans[1].status)
        assertEquals(-5, plans[1].daysUntilDue)
        assertEquals(VaccinePlanner.Status.UPCOMING, plans[2].status)
        assertEquals("m2", VaccinePlanner.next(plans)?.visit?.id)
    }

    @Test
    fun dueSoonAndToday() {
        val plans = VaccinePlanner.plan(schedule, birth, mapOf("bcg" to birth), LocalDate.of(2026, 3, 15))
        assertEquals(VaccinePlanner.Status.DUE_TODAY, plans[1].status)
        val soon = VaccinePlanner.plan(schedule, birth, mapOf("bcg" to birth), LocalDate.of(2026, 3, 5))
        assertEquals(VaccinePlanner.Status.DUE_SOON, soon[1].status)
    }
}

class NumberInputTest {
    @Test
    fun acceptsArabicDigitsAndCommas() {
        assertEquals(3.5, parseNumber("3.5")!!, 1e-9)
        assertEquals(3.5, parseNumber("3,5")!!, 1e-9)
        assertEquals(3.5, parseNumber("٣٫٥")!!, 1e-9)
        assertEquals(62.0, parseNumber(" ٦٢ ")!!, 1e-9)
        assertNull(parseNumber("abc"))
    }
}
