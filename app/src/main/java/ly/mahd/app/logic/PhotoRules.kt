package ly.mahd.app.logic

import ly.mahd.app.data.GrowthEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Small rules for the photo album, kept apart from Android code so they can be unit tested. */
object PhotoRules {
    /** A weight measured this close to a photo (in days) is shown with it, e.g. "6.4 كغ". */
    const val MEASUREMENT_WINDOW_DAYS = 14

    private val EXIF_DATE = Regex("""^(\d{4}):(\d{2}):(\d{2})""")

    /** The day in a photo's EXIF date ("2026:03:01 10:15:00"), or null when it is missing or not a real date. */
    fun dateFromExif(value: String?): LocalDate? {
        val match = EXIF_DATE.find(value?.trim() ?: return null) ?: return null
        val (year, month, day) = match.destructured
        return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }

    /** A photo's date kept between the child's birth and today (a camera with a wrong clock, a very old picture). */
    fun clampDate(date: LocalDate, birth: LocalDate, today: LocalDate): LocalDate = when {
        date.isBefore(birth) -> birth
        date.isAfter(today) -> today
        else -> date
    }

    /** The weight measured closest to [date], if one was measured within [MEASUREMENT_WINDOW_DAYS] of it. */
    fun weightNear(entries: List<GrowthEntry>, date: LocalDate): Double? =
        entries.mapNotNull { e -> e.weightKg?.let { it to abs(ChronoUnit.DAYS.between(e.date, date)) } }
            .filter { (_, days) -> days <= MEASUREMENT_WINDOW_DAYS }
            .minByOrNull { (_, days) -> days }
            ?.first

    /** The two photos to compare first: [preferred] (or the oldest) and the newest other one. */
    fun <T> defaultPair(photos: List<T>, preferred: T?): Pair<T, T>? {
        if (photos.size < 2) return null
        val first = preferred?.takeIf { it in photos } ?: photos.first()
        val second = photos.lastOrNull { it != first } ?: return null
        return first to second
    }
}
