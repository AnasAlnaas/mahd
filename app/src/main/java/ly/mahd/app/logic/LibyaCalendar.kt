package ly.mahd.app.logic

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** The seasons in Libya: a long hot summer, a short mild winter with the rain, the dusty ghibli winds of spring. */
enum class Season(val code: String) {
    WINTER("winter"),
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn");

    companion object {
        fun of(date: LocalDate): Season = when (date.monthValue) {
            12, 1, 2 -> WINTER
            3, 4, 5 -> SPRING
            6, 7, 8, 9 -> SUMMER
            else -> AUTUMN
        }
    }
}

/**
 * Islamic seasons and celebration days that change what the app says. For the first [featuredDays] days of an
 * occasion (all of it when null, for the short ones such as the Eids) its own items come before everything else.
 */
enum class Occasion(val code: String, val featuredDays: Int?) {
    RAMADAN("ramadan", 3),
    RAMADAN_LAST_TEN("ramadan_last_ten", 1),
    EID_FITR("eid_fitr", null),
    DHUL_HIJJAH_TEN("dhul_hijjah_ten", 2),
    ARAFAH("arafah", null),
    EID_ADHA("eid_adha", null),
    /** Days when Libyans celebrate with fireworks: the two Eids, the Mawlid and 17 February. */
    CELEBRATION("celebration", null),
}

data class ActiveOccasion(val occasion: Occasion, val start: LocalDate) {
    fun isFeatured(today: LocalDate): Boolean {
        val days = occasion.featuredDays ?: return true
        return today.isBefore(start.plusDays(days.toLong()))
    }
}

/** Public holidays in Libya, when clinics and vaccination centres may be closed. */
enum class Holiday {
    EID_FITR, ARAFAH, EID_ADHA, MAWLID, HIJRI_NEW_YEAR, REVOLUTION, LABOUR, MARTYRS, LIBERATION, INDEPENDENCE
}

/**
 * Seasons, Islamic dates and Libyan public holidays, worked out on the phone (no internet). Hijri dates use the
 * Umm al-Qura calendar built into Java; Libya follows the sighting of the moon, so a date can be a day off, which
 * the mother corrects in Settings ([hijriOffsetDays]).
 */
object LibyaCalendar {
    data class Hijri(val year: Int, val month: Int, val day: Int)

    /**
     * Days added to the Umm al-Qura date: −1 when Libya starts the month a day later, +1 when a day earlier.
     * Set from Prefs when the app starts and whenever the mother changes it.
     */
    @Volatile
    var hijriOffsetDays: Int = 0

    /**
     * Null outside the calendar's range (years 1300–1600 AH) or if the phone cannot convert the date; the app then
     * simply shows no Islamic occasions rather than failing.
     */
    fun hijri(date: LocalDate): Hijri? = try {
        val h = HijrahDate.from(date.plusDays(hijriOffsetDays.toLong()))
        Hijri(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    } catch (e: DateTimeException) {
        null
    } catch (e: RuntimeException) {
        null // a phone whose Java calendar data is missing or broken
    }

    fun occasions(date: LocalDate): List<ActiveOccasion> = buildList {
        val h = hijri(date)
        if (h != null) {
            // The date on which the occasion began, from its day of the Hijri month.
            fun from(day: Int) = date.minusDays((h.day - day).toLong())
            when (h.month) {
                3 -> if (h.day in 11..12) add(ActiveOccasion(Occasion.CELEBRATION, from(11))) // the Mawlid
                9 -> {
                    add(ActiveOccasion(Occasion.RAMADAN, from(1)))
                    if (h.day >= 21) add(ActiveOccasion(Occasion.RAMADAN_LAST_TEN, from(21)))
                }
                10 -> if (h.day <= 3) {
                    add(ActiveOccasion(Occasion.EID_FITR, from(1)))
                    add(ActiveOccasion(Occasion.CELEBRATION, from(1)))
                }
                12 -> {
                    if (h.day <= 9) add(ActiveOccasion(Occasion.DHUL_HIJJAH_TEN, from(1)))
                    if (h.day == 9) add(ActiveOccasion(Occasion.ARAFAH, date))
                    if (h.day in 10..13) {
                        add(ActiveOccasion(Occasion.EID_ADHA, from(10)))
                        add(ActiveOccasion(Occasion.CELEBRATION, from(10)))
                    }
                }
            }
        }
        if (date.monthValue == 2 && date.dayOfMonth == 17) add(ActiveOccasion(Occasion.CELEBRATION, date))
    }

    fun isRamadan(date: LocalDate): Boolean = hijri(date)?.month == 9

    /** The first day of Ramadan or of an Eid, when the app sends its greeting with the day's verse or hadith. */
    fun greeting(date: LocalDate): Occasion? {
        val h = hijri(date) ?: return null
        return when {
            h.month == 9 && h.day == 1 -> Occasion.RAMADAN
            h.month == 10 && h.day == 1 -> Occasion.EID_FITR
            h.month == 12 && h.day == 10 -> Occasion.EID_ADHA
            else -> null
        }
    }

    fun holiday(date: LocalDate): Holiday? {
        val h = hijri(date)
        if (h != null) {
            when {
                h.month == 10 && h.day <= 3 -> return Holiday.EID_FITR
                h.month == 12 && h.day == 9 -> return Holiday.ARAFAH
                h.month == 12 && h.day in 10..13 -> return Holiday.EID_ADHA
                h.month == 3 && h.day == 12 -> return Holiday.MAWLID
                h.month == 1 && h.day == 1 -> return Holiday.HIJRI_NEW_YEAR
            }
        }
        return when (date.monthValue to date.dayOfMonth) {
            2 to 17 -> Holiday.REVOLUTION
            5 to 1 -> Holiday.LABOUR
            9 to 16 -> Holiday.MARTYRS
            10 to 23 -> Holiday.LIBERATION
            12 to 24 -> Holiday.INDEPENDENCE
            else -> null
        }
    }
}
