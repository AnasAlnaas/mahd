package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.content.VaccineDose
import ly.mahd.app.content.VaccineSchedule
import ly.mahd.app.content.VaccineVisit
import ly.mahd.app.data.BackupFile
import ly.mahd.app.data.DoseRecord
import ly.mahd.app.data.GrowthEntry
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Repository
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.ReminderRules
import ly.mahd.app.logic.VaccinePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Parses the real content files, so a typo made while editing them is caught before release. */
class ContentFilesTest {
    private val content = ContentRepository { path -> File("src/main/assets/$path").inputStream() }

    @Test
    fun pregnancyHasEveryWeekAndDangerSigns() {
        val p = content.pregnancy
        assertEquals((1..42).toList(), p.weeks.map { it.week })
        assertTrue(p.dangerSigns.isNotEmpty())
        p.weeks.forEach { w ->
            assertTrue("week ${w.week} has empty text", w.baby.isNotBlank() && w.mother.isNotBlank() && w.tip.isNotBlank())
        }
    }

    @Test
    fun milestonesAreConsistent() {
        val m = content.milestones
        val domainIds = m.domains.map { it.id }.toSet()
        val itemIds = m.groups.flatMap { g -> g.items.map { it.id } }
        assertEquals("duplicate milestone ids", itemIds.size, itemIds.toSet().size)
        assertTrue(m.groups.flatMap { it.items }.all { it.domain in domainIds })
        assertTrue(m.groups.all { it.items.isNotEmpty() })
        assertEquals(m.groups.map { it.months }.distinct().size, m.groups.size)
        assertTrue(m.warnings.isNotEmpty())
    }

    @Test
    fun vaccineScheduleIsConsistent() {
        val v = content.vaccines
        val doseIds = v.visits.flatMap { visit -> visit.doses.map { it.id } }
        assertEquals("duplicate dose ids", doseIds.size, doseIds.toSet().size)
        assertEquals(v.visits.map { it.id }.distinct().size, v.visits.size)
        assertTrue(v.visits.all { it.doses.isNotEmpty() })
        assertTrue(v.visits.flatMap { it.doses }.all { dose -> dose.maxAgeMonths?.let { it > 0 } != false })
        // Rotavirus must not be given after 8 months (this is what stops it showing as "late" forever).
        assertTrue(v.visits.flatMap { it.doses }.filter { it.id.startsWith("rota_") }.all { it.maxAgeMonths == 8 })
    }

    @Test
    fun growthTablesLoad() {
        assertNotNull(content.growth.lms(GrowthIndicator.WEIGHT, 1, 0.0))
    }
}

class VaccineSkipTest {
    private val birth = LocalDate.of(2026, 1, 15)
    private val schedule = VaccineSchedule(
        reviewed = false,
        source = "test",
        visits = listOf(
            VaccineVisit(
                "m2", 2, "2m",
                listOf(VaccineDose("rota", "Rota", "", null, maxAgeMonths = 8), VaccineDose("a1", "A", "", null)),
            ),
            VaccineVisit("m4", 4, "4m", listOf(VaccineDose("a2", "A", "", null))),
        ),
    )

    @Test
    fun doseTooOldIsNotLateForever() {
        val today = LocalDate.of(2026, 11, 1) // 9.5 months old: past the rotavirus limit
        val plans = VaccinePlanner.plan(schedule, birth, mapOf("a1" to LocalDate.of(2026, 3, 16)), today)
        assertEquals(setOf("rota"), plans[0].expired)
        assertEquals(VaccinePlanner.Status.DONE, plans[0].status)
        assertEquals("m4", VaccinePlanner.next(plans)?.visit?.id)
    }

    @Test
    fun doseWithinAgeLimitStillCountsAsLate() {
        val today = LocalDate.of(2026, 4, 1) // 2.5 months old
        val plans = VaccinePlanner.plan(schedule, birth, emptyMap(), today)
        assertTrue(plans[0].expired.isEmpty())
        assertEquals(VaccinePlanner.Status.OVERDUE, plans[0].status)
    }

    @Test
    fun skippedDosesAreResolved() {
        val today = LocalDate.of(2027, 1, 1)
        val plans = VaccinePlanner.plan(schedule, birth, emptyMap(), today, setOf("a1", "a2"))
        assertEquals(VaccinePlanner.Status.SKIPPED, plans[0].status) // rota expired + a1 skipped, nothing given
        assertEquals(VaccinePlanner.Status.SKIPPED, plans[1].status)
        assertNull(VaccinePlanner.next(plans))
    }

    @Test
    fun givenBeatsSkipped() {
        val today = LocalDate.of(2026, 4, 1)
        val plans = VaccinePlanner.plan(schedule, birth, mapOf("a1" to LocalDate.of(2026, 3, 16)), today, setOf("a1"))
        assertTrue(plans[0].skipped.isEmpty())
        assertEquals(1, plans[0].givenCount)
    }
}

class PretermGrowthTest {
    private val birth = LocalDate.of(2026, 1, 1) // born at 32 weeks: original due date 2026-02-26

    @Test
    fun beforeTheOriginalDueDateWhoStandardsDoNotApply() {
        assertTrue(ChildAge.isBeforeCorrectedTerm(birth, 32, LocalDate.of(2026, 2, 1)))
        assertTrue(ChildAge.referenceMonthsSigned(birth, 32, LocalDate.of(2026, 2, 1)) < 0)
        assertFalse(ChildAge.isBeforeCorrectedTerm(birth, 32, LocalDate.of(2026, 3, 26)))
    }

    @Test
    fun termBabiesAreNeverBeforeTerm() {
        assertFalse(ChildAge.isBeforeCorrectedTerm(birth, 39, birth))
        assertFalse(ChildAge.isBeforeCorrectedTerm(birth, null, birth))
    }
}

class ReminderRulesTest {
    @Test
    fun remindsOnTheDayAndCatchesUpIfTheCheckWasMissed() {
        assertEquals(3, ReminderRules.vaccineThreshold(3))
        assertEquals(3, ReminderRules.vaccineThreshold(1)) // missed 2 days
        assertEquals(0, ReminderRules.vaccineThreshold(0))
        assertEquals(0, ReminderRules.vaccineThreshold(-2))
        assertEquals(-3, ReminderRules.vaccineThreshold(-3))
        assertEquals(-7, ReminderRules.vaccineThreshold(-8))
        assertEquals(-14, ReminderRules.vaccineThreshold(-14))
    }

    @Test
    fun staysQuietOnOtherDays() {
        assertNull(ReminderRules.vaccineThreshold(4))
        assertNull(ReminderRules.vaccineThreshold(-6))
        assertNull(ReminderRules.vaccineThreshold(-17))
        assertNull(ReminderRules.vaccineThreshold(-200))
    }
}

class BackupFileTest {
    private val today = LocalDate.of(2026, 10, 4)
    private val profile = Profile(1, ProfileKind.CHILD, "Test", Sex.GIRL, LocalDate.of(2026, 1, 15), null, 34)
    private val snapshot = Repository.Snapshot(
        profiles = listOf(profile),
        growth = listOf(GrowthEntry(5, 1, LocalDate.of(2026, 3, 1), 4.5, 55.0, null)),
        doses = listOf(DoseRecord(1, "bcg", LocalDate.of(2026, 1, 15))),
        milestones = listOf(Triple(1L, "m2_s1", 1)),
        skipped = listOf(1L to "rota_1"),
    )

    @Test
    fun roundTrip() {
        assertEquals(snapshot, BackupFile.decode(BackupFile.encode(snapshot, today)))
    }

    @Test
    fun oldBackupsWithoutSkippedDosesStillLoad() {
        val old = BackupFile.encode(snapshot.copy(skipped = emptyList()), today)
            .replace(Regex("\"skippedDoses\": \\[\\s*]"), "\"unknown\": []")
        assertTrue(BackupFile.decode(old).skipped.isEmpty())
    }

    @Test
    fun rowsWithoutAProfileAreRejectedInsteadOfCrashingTheRestore() {
        val orphanGrowth = snapshot.copy(growth = listOf(GrowthEntry(5, 99, LocalDate.of(2026, 3, 1), 4.5, null, null)))
        assertThrows(IllegalArgumentException::class.java) { BackupFile.decode(BackupFile.encode(orphanGrowth, today)) }
        val orphanDose = snapshot.copy(doses = listOf(DoseRecord(99, "bcg", today)))
        assertThrows(IllegalArgumentException::class.java) { BackupFile.decode(BackupFile.encode(orphanDose, today)) }
    }

    @Test
    fun duplicateRowsAreRejected() {
        val twice = snapshot.copy(doses = snapshot.doses + snapshot.doses)
        assertThrows(IllegalArgumentException::class.java) { BackupFile.decode(BackupFile.encode(twice, today)) }
    }

    @Test
    fun garbageIsRejectedWithTheDocumentedException() {
        assertThrows(IllegalArgumentException::class.java) { BackupFile.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupFile.decode("{\"app\":\"mahd\",\"format\":1}") }
        val badDate = BackupFile.encode(snapshot, today).replace("2026-03-01", "2026-13-45")
        assertThrows(IllegalArgumentException::class.java) { BackupFile.decode(badDate) }
    }
}
