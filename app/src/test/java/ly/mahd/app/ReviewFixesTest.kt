package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.content.VaccineDose
import ly.mahd.app.content.VaccineSchedule
import ly.mahd.app.content.VaccineVisit
import ly.mahd.app.data.BackupFile
import ly.mahd.app.data.BirthType
import ly.mahd.app.data.DoseRecord
import ly.mahd.app.data.ExerciseTick
import ly.mahd.app.data.GrowthEntry
import ly.mahd.app.data.Profile
import ly.mahd.app.data.ProfileKind
import ly.mahd.app.data.Repository
import ly.mahd.app.data.Sex
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.logic.GrowthMath
import ly.mahd.app.logic.ReminderRules
import ly.mahd.app.logic.VaccinePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test
    fun childDangerSignsAreFilledIn() {
        val sections = content.childDangerSigns.sections
        assertTrue(sections.isNotEmpty())
        sections.forEach { s ->
            assertTrue("a section has no title", !s.title.isNullOrBlank())
            assertTrue("section '${s.title}' has an empty sign", s.signs.isNotEmpty() && s.signs.all { it.isNotBlank() })
            assertTrue("section '${s.title}' has a bad age limit", s.maxAgeMonths?.let { it > 0 } != false)
        }
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

    @Test
    fun appRunsTheDailyCheckItselfWhenTheAlarmHasNotRunToday() {
        val yesterday = LocalDate.of(2026, 10, 3)
        val noon = LocalDateTime.of(2026, 10, 4, 12, 0)
        assertTrue(ReminderRules.isCheckDue(yesterday, noon)) // alarm late or stopped
        assertTrue(ReminderRules.isCheckDue(null, noon)) // never ran (new install)
        assertTrue(ReminderRules.isCheckDue(yesterday, LocalDateTime.of(2026, 10, 4, 10, 0)))
    }

    @Test
    fun appLeavesTheCheckToTheAlarmBeforeItsTimeOrAfterItRan() {
        assertFalse(ReminderRules.isCheckDue(LocalDate.of(2026, 10, 3), LocalDateTime.of(2026, 10, 4, 9, 59)))
        assertFalse(ReminderRules.isCheckDue(LocalDate.of(2026, 10, 4), LocalDateTime.of(2026, 10, 4, 18, 0)))
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
        exerciseLog = listOf(ExerciseTick(1, LocalDate.of(2026, 1, 10), "preg_breathing")),
    )

    private fun decode(text: String) = BackupFile.decode(text, today)

    @Test
    fun exerciseTicksAreKeptAndOldBackupsWithoutThemStillLoad() {
        val old = encode(snapshot.copy(exerciseLog = emptyList()))
            .replace(Regex("\"exerciseLog\": \\[\\s*]"), "\"unknown\": []")
        assertTrue(decode(old).exerciseLog.isEmpty())
        val orphan = snapshot.copy(exerciseLog = listOf(ExerciseTick(99, today, "preg_breathing")))
        assertThrows(IllegalArgumentException::class.java) { decode(encode(orphan)) }
        val twice = snapshot.copy(exerciseLog = snapshot.exerciseLog + snapshot.exerciseLog)
        assertThrows(IllegalArgumentException::class.java) { decode(encode(twice)) }
        val farFuture = snapshot.copy(exerciseLog = listOf(ExerciseTick(1, LocalDate.of(3000, 1, 1), "preg_breathing")))
        assertThrows(IllegalArgumentException::class.java) { decode(encode(farFuture)) }
    }

    private fun encode(s: Repository.Snapshot) = BackupFile.encode(s, today)

    @Test
    fun roundTrip() {
        assertEquals(snapshot, decode(encode(snapshot)))
    }

    @Test
    fun theBirthTypeIsKeptAndOldBackupsWithoutItStillLoad() {
        val withType = snapshot.copy(profiles = listOf(profile.copy(birthType = BirthType.CAESAREAN)))
        assertEquals(withType, decode(encode(withType)))
        assertEquals(null, decode(encode(snapshot)).profiles.single().birthType)
        val unknown = encode(withType).replace("\"caesarean\"", "\"other\"")
        assertThrows(IllegalArgumentException::class.java) { decode(unknown) }
    }

    @Test
    fun oldBackupsWithoutSkippedDosesStillLoad() {
        val old = encode(snapshot.copy(skipped = emptyList()))
            .replace(Regex("\"skippedDoses\": \\[\\s*]"), "\"unknown\": []")
        assertTrue(decode(old).skipped.isEmpty())
    }

    @Test
    fun rowsWithoutAProfileAreRejectedInsteadOfCrashingTheRestore() {
        val orphanGrowth = snapshot.copy(growth = listOf(GrowthEntry(5, 99, LocalDate.of(2026, 3, 1), 4.5, null, null)))
        assertThrows(IllegalArgumentException::class.java) { decode(encode(orphanGrowth)) }
        val orphanDose = snapshot.copy(doses = listOf(DoseRecord(99, "bcg", today)))
        assertThrows(IllegalArgumentException::class.java) { decode(encode(orphanDose)) }
    }

    @Test
    fun duplicateRowsAreRejected() {
        val twice = snapshot.copy(doses = snapshot.doses + snapshot.doses)
        assertThrows(IllegalArgumentException::class.java) { decode(encode(twice)) }
    }

    @Test
    fun garbageIsRejectedWithTheDocumentedException() {
        assertThrows(IllegalArgumentException::class.java) { decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { decode("{\"app\":\"mahd\",\"format\":1}") }
        val badDate = encode(snapshot).replace("2026-03-01", "2026-13-45")
        assertThrows(IllegalArgumentException::class.java) { decode(badDate) }
    }

    @Test
    fun anEmptyBackupIsRejectedBecauseRestoringItWouldWipeEverything() {
        assertThrows(IllegalArgumentException::class.java) {
            decode(encode(Repository.Snapshot(emptyList(), emptyList(), emptyList(), emptyList())))
        }
    }

    @Test
    fun valuesThatWouldBreakTheAppAreRejected() {
        val pregnancyWithoutDueDate = Profile(2, ProfileKind.PREGNANCY, "", null, null, null, null)
        val broken = listOf(
            snapshot.copy(profiles = listOf(profile.copy(birthDate = null))), // child without a birth date
            snapshot.copy(profiles = listOf(profile, pregnancyWithoutDueDate)),
            snapshot.copy(profiles = listOf(profile.copy(birthDate = LocalDate.MAX))), // crashed the app on every launch
            snapshot.copy(profiles = listOf(profile.copy(weeksAtBirth = -5))),
            snapshot.copy(growth = listOf(GrowthEntry(5, 1, LocalDate.of(3026, 3, 1), 4.5, null, null))), // far future
            snapshot.copy(growth = listOf(GrowthEntry(5, 1, LocalDate.of(2026, 3, 1), -4.5, null, null))), // negative
            snapshot.copy(growth = listOf(GrowthEntry(5, 1, LocalDate.of(2026, 3, 1), null, null, null))), // no value
            snapshot.copy(milestones = listOf(Triple(1L, "m2_s1", 7))), // unknown answer
        ).map(::encode)
        val text = encode(snapshot)
        val edited = listOf(
            text.replace("\"kind\": \"child\"", "\"kind\": \"cat\""),
            text.replace("\"sex\": \"girl\"", "\"sex\": \"x\""),
            text.replace("\"weightKg\": 4.5", "\"weightKg\": \"NaN\""),
        )
        edited.forEach { assertNotEquals("the edit did not apply", text, it) }
        (broken + edited).forEach { file -> assertThrows(IllegalArgumentException::class.java) { decode(file) } }
    }

    @Test
    fun aBackupFromAPhoneWhoseClockWasSlightlyWrongStillLoads() {
        val ahead = encode(snapshot).replace("2026-03-01", "2026-10-20") // a measurement 16 days "in the future"
        assertEquals(LocalDate.of(2026, 10, 20), decode(ahead).growth.single().date)
    }
}

class VaccineSummaryTest {
    private val birth = LocalDate.of(2026, 1, 15)
    private val schedule = VaccineSchedule(
        reviewed = false,
        source = "test",
        visits = listOf(
            VaccineVisit("birth", 0, "birth", listOf(VaccineDose("bcg", "BCG", "", null))),
            VaccineVisit(
                "m2", 2, "2m",
                listOf(VaccineDose("rota", "Rota", "", null, maxAgeMonths = 8), VaccineDose("a1", "A", "", null)),
            ),
            VaccineVisit("m4", 4, "4m", listOf(VaccineDose("a2", "A", "", null), VaccineDose("b2", "B", "", null))),
            VaccineVisit("m12", 12, "12m", listOf(VaccineDose("mmr", "MMR", "", null))),
        ),
    )

    @Test
    fun countsOnlyDosesThatApplyAndSplitsOutTheLateOnes() {
        val today = LocalDate.of(2026, 11, 1) // 9.5 months old: m2 and m4 are due, rotavirus has expired
        val plans = VaccinePlanner.plan(schedule, birth, mapOf("bcg" to birth), today, skippedDoses = setOf("b2"))
        val s = VaccinePlanner.summary(plans)
        // applicable: bcg(given), a1, a2, mmr; rota expired and b2 skipped are left out
        assertEquals(1, s.given)
        assertEquals(4, s.total)
        assertEquals(2, s.lateDoses) // a1 and a2 are late, mmr is not due yet
        assertEquals(0.25f, s.progress, 1e-6f)
    }

    @Test
    fun nothingToDoMeansFullRing() {
        val s = VaccinePlanner.Summary(given = 0, total = 0, lateDoses = 0)
        assertEquals(1f, s.progress, 1e-6f)
    }
}

class GrowthTrendTest {
    @Test
    fun smallChangesAreSteady() {
        assertEquals(GrowthMath.Trend.STEADY, GrowthMath.trend(0.2, 0.7))
        assertEquals(GrowthMath.Trend.STEADY, GrowthMath.trend(-1.0, -1.6))
    }

    @Test
    fun movingToAnotherCurveIsUpOrDown() {
        assertEquals(GrowthMath.Trend.UP, GrowthMath.trend(0.0, 0.8))
        assertEquals(GrowthMath.Trend.DOWN, GrowthMath.trend(0.5, -0.4))
    }
}
