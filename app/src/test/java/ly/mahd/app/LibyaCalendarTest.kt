package ly.mahd.app

import ly.mahd.app.logic.Holiday
import ly.mahd.app.logic.LibyaCalendar
import ly.mahd.app.logic.Occasion
import ly.mahd.app.logic.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LibyaCalendarTest {
    private fun occasionsOn(date: String) = LibyaCalendar.occasions(LocalDate.parse(date)).map { it.occasion }.toSet()

    @Test
    fun seasons() {
        assertEquals(Season.WINTER, Season.of(LocalDate.of(2027, 1, 10)))
        assertEquals(Season.SPRING, Season.of(LocalDate.of(2027, 4, 10)))
        assertEquals(Season.SUMMER, Season.of(LocalDate.of(2026, 9, 30)))
        assertEquals(Season.AUTUMN, Season.of(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun hijriDatesFollowUmmAlQura() {
        assertEquals(LibyaCalendar.Hijri(1447, 9, 1), LibyaCalendar.hijri(LocalDate.of(2026, 2, 18)))
        assertEquals(LibyaCalendar.Hijri(1447, 10, 1), LibyaCalendar.hijri(LocalDate.of(2026, 3, 20)))
        assertEquals(LibyaCalendar.Hijri(1447, 12, 10), LibyaCalendar.hijri(LocalDate.of(2026, 5, 27)))
    }

    @Test
    fun theSettingMovesTheHijriDate() {
        try {
            // Libya starts Ramadan a day after Umm al-Qura: everything moves one day later.
            LibyaCalendar.hijriOffsetDays = -1
            assertEquals(LibyaCalendar.Hijri(1447, 8, 29), LibyaCalendar.hijri(LocalDate.of(2026, 2, 18)))
            assertNull(LibyaCalendar.greeting(LocalDate.of(2026, 2, 18)))
            assertEquals(Occasion.RAMADAN, LibyaCalendar.greeting(LocalDate.of(2026, 2, 19)))
            assertEquals(Occasion.EID_FITR, LibyaCalendar.greeting(LocalDate.of(2026, 3, 21)))
            assertEquals(Holiday.EID_FITR, LibyaCalendar.holiday(LocalDate.of(2026, 3, 23)))
            // A day earlier instead.
            LibyaCalendar.hijriOffsetDays = 1
            assertEquals(Occasion.RAMADAN, LibyaCalendar.greeting(LocalDate.of(2026, 2, 17)))
        } finally {
            LibyaCalendar.hijriOffsetDays = 0
        }
    }

    @Test
    fun ramadanAndItsLastTenNights() {
        assertEquals(setOf(Occasion.RAMADAN), occasionsOn("2026-02-18"))
        val last = LibyaCalendar.occasions(LocalDate.of(2026, 3, 11))
        assertTrue(last.any { it.occasion == Occasion.RAMADAN_LAST_TEN && it.start == LocalDate.of(2026, 3, 10) })
        assertTrue(LibyaCalendar.isRamadan(LocalDate.of(2026, 3, 19)))
        assertFalse(LibyaCalendar.isRamadan(LocalDate.of(2026, 3, 20)))
        // Ramadan's own items come first for its first three days only.
        val ramadan = LibyaCalendar.occasions(LocalDate.of(2026, 2, 20)).first { it.occasion == Occasion.RAMADAN }
        assertTrue(ramadan.isFeatured(LocalDate.of(2026, 2, 20)))
        assertFalse(ramadan.isFeatured(LocalDate.of(2026, 2, 21)))
    }

    @Test
    fun eidsArafahAndCelebrations() {
        assertEquals(setOf(Occasion.EID_FITR, Occasion.CELEBRATION), occasionsOn("2026-03-22"))
        assertTrue(occasionsOn("2026-03-23").isEmpty())
        assertEquals(setOf(Occasion.DHUL_HIJJAH_TEN, Occasion.ARAFAH), occasionsOn("2026-05-26"))
        assertEquals(setOf(Occasion.EID_ADHA, Occasion.CELEBRATION), occasionsOn("2026-05-30"))
        // 17 February 2027 falls in Ramadan: both apply.
        assertEquals(setOf(Occasion.RAMADAN, Occasion.CELEBRATION), occasionsOn("2027-02-17"))
        assertTrue(occasionsOn("2026-10-09").isEmpty())
    }

    @Test
    fun greetingsOnTheFirstDay() {
        assertEquals(Occasion.RAMADAN, LibyaCalendar.greeting(LocalDate.of(2026, 2, 18)))
        assertEquals(Occasion.EID_FITR, LibyaCalendar.greeting(LocalDate.of(2026, 3, 20)))
        assertEquals(Occasion.EID_ADHA, LibyaCalendar.greeting(LocalDate.of(2026, 5, 27)))
        assertNull(LibyaCalendar.greeting(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun libyanPublicHolidays() {
        assertEquals(Holiday.EID_FITR, LibyaCalendar.holiday(LocalDate.of(2026, 3, 21)))
        assertEquals(Holiday.ARAFAH, LibyaCalendar.holiday(LocalDate.of(2026, 5, 26)))
        assertEquals(Holiday.EID_ADHA, LibyaCalendar.holiday(LocalDate.of(2026, 5, 29)))
        assertEquals(Holiday.MAWLID, LibyaCalendar.holiday(LocalDate.of(2026, 8, 25)))
        assertEquals(Holiday.REVOLUTION, LibyaCalendar.holiday(LocalDate.of(2027, 2, 17)))
        assertEquals(Holiday.INDEPENDENCE, LibyaCalendar.holiday(LocalDate.of(2026, 12, 24)))
        assertNull(LibyaCalendar.holiday(LocalDate.of(2026, 10, 9)))
    }
}
