package ly.mahd.app.logic

import ly.mahd.app.content.AdviceAudience
import ly.mahd.app.content.AdviceItem
import ly.mahd.app.content.isFor
import ly.mahd.app.data.Parent
import ly.mahd.app.data.Profile
import java.time.DayOfWeek
import java.time.LocalDate

/** Which piece of advice (Qur'an / hadith) a mother sees, kept apart from Android code so it can be unit tested. */
object AdviceRules {
    /** The occasional notification goes out on these days only (twice a week). */
    val NOTIFICATION_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)

    /** The items that fit this profile today: her pregnancy week, or her child's age (and sex, for a few items). */
    fun eligible(items: List<AdviceItem>, profile: Profile, today: LocalDate, parent: Parent = Parent.MOTHER): List<AdviceItem> {
        val mine = items.filter { it.isFor(parent) }
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
     * Today's item: the eligible ones in turn, one per day, so she sees a different one each day and the same one
     * all day long. Each profile starts at a different place in the list, so two children don't show the same text.
     */
    fun forDay(items: List<AdviceItem>, profile: Profile, today: LocalDate, parent: Parent = Parent.MOTHER): AdviceItem? {
        val list = eligible(items, profile, today, parent).sortedBy { it.id }
        if (list.isEmpty()) return null
        return list[Math.floorMod(today.toEpochDay() + profile.id * 7, list.size.toLong()).toInt()]
    }

    fun isNotificationDay(date: LocalDate): Boolean = date.dayOfWeek in NOTIFICATION_DAYS
}
