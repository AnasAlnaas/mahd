package ly.mahd.app.logic

import ly.mahd.app.content.MilestoneContent
import ly.mahd.app.content.MilestoneGroup
import ly.mahd.app.content.MilestoneItem
import ly.mahd.app.data.MilestoneAnswer
import kotlin.math.ceil

/**
 * What the skills tab shows, kept apart from Android code so it can be unit tested.
 * Only skills of the ages the child has reached are shown (an older age's skills stay hidden until then).
 * Skills she still has to watch come first; the ones the child does ("yes") sink to the end.
 */
object MilestoneFocus {
    data class Entry(val item: MilestoneItem, val group: MilestoneGroup)

    data class Lists(
        /** Not yet seen, "not yet" or "making progress": newest age first, then by area as in the file. */
        val focus: List<Entry>,
        /** Answered "yes", same order. */
        val done: List<Entry>,
    )

    /** Every skill of every checklist the child has reached ([months] = corrected age for a premature baby). */
    fun revealed(content: MilestoneContent, months: Double): List<Entry> {
        val domainOrder = content.domains.map { it.id }
        return content.reachedGroups(months)
            .sortedByDescending { it.months }
            .flatMap { g -> g.items.sortedBy { domainOrder.indexOf(it.domain) }.map { Entry(it, g) } }
    }

    /**
     * Splits [entries] into "watch now" and "done". A skill in [held] stays in "watch now" for a moment after
     * "yes" was tapped, so its little celebration plays where the mother tapped before it glides down.
     */
    fun split(entries: List<Entry>, answers: Map<String, MilestoneAnswer>, held: Set<String> = emptySet()): Lists {
        val (done, focus) = entries.partition { answers[it.item.id] == MilestoneAnswer.YES && it.item.id !in held }
        return Lists(focus, done)
    }

    /**
     * For a child added at an older age: every skill of the ages before the current one, counted as done ("yes"),
     * so the mother only fills in the newest list. She can still change any of them.
     */
    fun presumedDone(content: MilestoneContent, months: Double): List<String> =
        content.reachedGroups(months).dropLast(1).flatMap { g -> g.items.map { it.id } }

    /** Whole days until the child reaches [group] (0 if already there). */
    fun daysUntil(group: MilestoneGroup, months: Double): Int =
        ceil((group.months - months) * ChildAge.DAYS_PER_MONTH).toInt().coerceAtLeast(0)
}
