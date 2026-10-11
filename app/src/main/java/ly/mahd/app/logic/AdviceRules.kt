package ly.mahd.app.logic

import ly.mahd.app.content.AdviceAudience
import ly.mahd.app.content.AdviceItem
import ly.mahd.app.content.AdviceKind
import ly.mahd.app.content.isFor
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What the family on this phone looks like, seen from one profile (see "situations" in the content files). */
enum class Situation(val code: String) {
    /** This child has a brother or sister followed in the app. */
    SIBLINGS("siblings"),
    /** A baby is on the way and there is already a child. */
    EXPECTING("expecting"),
    /** Another child was born the same day (or the day before or after). */
    TWINS("twins"),
    /** This child was born before 37 weeks. */
    PRETERM("preterm");

    companion object {
        fun of(profile: Profile, family: List<Profile>): Set<Situation> {
            val children = family.filter { it.isChild }
            val birth = profile.birthDate
            return buildSet {
                if (profile.isChild && children.size >= 2) add(SIBLINGS)
                if (family.any { it.isPregnancy } && children.isNotEmpty()) add(EXPECTING)
                if (profile.isChild && birth != null && children.any { other ->
                        val otherBirth = other.birthDate
                        other.id != profile.id && otherBirth != null && abs(ChronoUnit.DAYS.between(birth, otherBirth)) <= 1
                    }
                ) add(TWINS)
                if (profile.isChild && profile.isPremature) add(PRETERM)
            }
        }
    }
}

/**
 * Which piece of advice (a verse, a hadith or a science fact) a parent sees, kept apart from Android code so it can
 * be unit tested. Besides the pregnancy week or the child's age, it follows the season in Libya, Ramadan and the
 * Eids, and the family: [family] is every profile on the phone (the selected one included).
 */
object AdviceRules {
    /** The occasional notification goes out on these days (twice a week), and on the first day of Ramadan and of each Eid. */
    val NOTIFICATION_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)

    /** A seasonal or family item comes back at most once in this many days. */
    private const val CONTEXT_REPEAT_DAYS = 14

    /** The items that fit this profile today: the pregnancy week or the child's age and sex, the season, the occasion, the family. */
    fun eligible(
        items: List<AdviceItem>,
        profile: Profile,
        today: LocalDate,
        parent: Parent = Parent.MOTHER,
        family: List<Profile> = listOf(profile),
    ): List<AdviceItem> {
        val season = Season.of(today).code
        val occasions = LibyaCalendar.occasions(today).map { it.occasion.code }.toSet()
        val situations = Situation.of(profile, family).map { it.code }.toSet()
        val mine = items.filter { a ->
            a.isFor(parent) &&
                (a.seasons.isEmpty() || season in a.seasons) &&
                (a.occasions.isEmpty() || a.occasions.any { it in occasions }) &&
                (a.situations.isEmpty() || a.situations.any { it in situations })
        }
        val due = profile.dueDate
        val birth = profile.birthDate
        return when {
            profile.isPregnancy && due != null -> {
                val weeks = PregnancyMath.gestationalAge(due, today).weeks
                mine.filter { a ->
                    a.audience != AdviceAudience.CHILD &&
                        (a.minWeeks == null || weeks >= a.minWeeks) &&
                        (a.maxWeeks == null || weeks <= a.maxWeeks)
                }
            }
            profile.isChild && birth != null -> {
                val months = ChildAge.months(birth, today)
                val sex = profile.sex?.name?.lowercase()
                mine.filter { a ->
                    a.audience != AdviceAudience.PREGNANCY &&
                        (a.minMonths == null || months >= a.minMonths) &&
                        (a.maxMonths == null || months < a.maxMonths) &&
                        (a.sex == null || a.sex == sex)
                }
            }
            else -> mine.filter { it.audience == AdviceAudience.ALL }
        }
    }

    /**
     * Today's item, the same all day long:
     * 1. In the first days of Ramadan, of its last ten nights and of Dhul Hijjah, and on the Eids, Arafah and
     *    celebration days: that occasion's own items, a different one each day, verses and hadith first.
     * 2. Otherwise, on some days, an item for the season, the occasion or the family; spaced so that none comes
     *    back within a fortnight even when there are only a few.
     * 3. On the other days, the year-round items in turn. Each profile starts at a different place in the list,
     *    so two children don't show the same text.
     */
    fun forDay(
        items: List<AdviceItem>,
        profile: Profile,
        today: LocalDate,
        parent: Parent = Parent.MOTHER,
        family: List<Profile> = listOf(profile),
    ): AdviceItem? {
        val list = eligible(items, profile, today, parent, family).sortedBy { it.id }
        if (list.isEmpty()) return null

        val featured = LibyaCalendar.occasions(today).filter { it.isFeatured(today) }
        if (featured.isNotEmpty()) {
            val codes = featured.map { it.occasion.code }.toSet()
            val own = list.filter { a -> a.occasions.any { it in codes } }
                .sortedWith(compareBy({ it.kind == AdviceKind.FACT }, { it.id }))
            val dayOfOccasion = ChronoUnit.DAYS.between(featured.minOf { it.start }, today).toInt()
            if (dayOfOccasion < own.size) return own[dayOfOccasion]
        }

        val day = today.toEpochDay() + profile.id * 7
        val (special, general) = list.partition { it.isContextual }
        if (special.isNotEmpty()) {
            val every = maxOf(2, (CONTEXT_REPEAT_DAYS + special.size - 1) / special.size)
            if (Math.floorMod(day, every.toLong()) == 0L || general.isEmpty()) {
                return special[Math.floorMod(Math.floorDiv(day, every.toLong()), special.size.toLong()).toInt()]
            }
        }
        return general[Math.floorMod(day, general.size.toLong()).toInt()]
    }

    /**
     * Why this item is shown today, for the small label on the card: an occasion, a season or a family situation
     * code, or null for a year-round item. A celebration item on an Eid is labelled with the Eid.
     */
    fun reason(item: AdviceItem, profile: Profile, today: LocalDate, family: List<Profile> = listOf(profile)): String? {
        val active = LibyaCalendar.occasions(today).map { it.occasion }
        val eid = active.firstOrNull { it == Occasion.EID_FITR || it == Occasion.EID_ADHA }
        val tagged = Occasion.entries.filter { it in active && it.code in item.occasions }
        if (tagged.isNotEmpty()) return (if (tagged == listOf(Occasion.CELEBRATION) && eid != null) eid else tagged.first()).code
        if (item.seasons.isNotEmpty()) return Season.of(today).code
        val situations = Situation.of(profile, family)
        return Situation.entries.firstOrNull { it in situations && it.code in item.situations }?.code
    }

    fun isNotificationDay(date: LocalDate): Boolean =
        date.dayOfWeek in NOTIFICATION_DAYS || LibyaCalendar.greeting(date) != null
}
