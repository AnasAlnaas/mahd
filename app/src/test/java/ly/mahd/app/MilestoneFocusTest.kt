package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import ly.mahd.app.data.MilestoneAnswer
import ly.mahd.app.logic.MilestoneFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MilestoneFocusTest {
    private val content = ContentRepository { path -> File("src/main/assets/$path").inputStream() }.milestones

    @Test
    fun firstListIsOneMonth() {
        assertEquals(1, content.groups.first().months)
        assertTrue(content.groups.first().items.size >= 5)
    }

    @Test
    fun nothingIsShownBeforeTheFirstList() {
        // Two weeks old: no list yet, the 1-month list comes next, in about two weeks.
        val twoWeeks = 14 / 30.4375
        assertTrue(MilestoneFocus.revealed(content, twoWeeks).isEmpty())
        val next = content.nextGroup(twoWeeks)!!
        assertEquals(1, next.months)
        assertTrue(MilestoneFocus.daysUntil(next, twoWeeks) in 16..17)
    }

    @Test
    fun olderAgesStayHidden() {
        val shown = MilestoneFocus.revealed(content, 2.5)
        assertEquals(setOf(1, 2), shown.map { it.group.months }.toSet())
        // A 6-month skill is not shown to a 2-week-old, nor to a 2-and-a-half-month-old.
        assertTrue(shown.none { it.group.months == 6 })
        assertTrue(MilestoneFocus.revealed(content, 6.0).any { it.group.months == 6 })
    }

    @Test
    fun anOlderChildStartsWithOnlyTheNewestListToFill() {
        // Added at 7 months: the 1-, 2- and 4-month skills count as done, the 6-month list is left to fill.
        val done = MilestoneFocus.presumedDone(content, 7.0).toSet()
        val entries = MilestoneFocus.revealed(content, 7.0)
        val lists = MilestoneFocus.split(entries, done.associateWith { MilestoneAnswer.YES })
        val newest = content.reachedGroups(7.0).last()
        assertEquals(newest.items.map { it.id }.toSet(), lists.focus.map { it.item.id }.toSet())
        assertTrue(lists.done.none { it.group.id == newest.id })
        // A baby on the first list (or before it) has nothing counted as done.
        assertTrue(MilestoneFocus.presumedDone(content, 1.5).isEmpty())
        assertTrue(MilestoneFocus.presumedDone(content, 0.2).isEmpty())
    }

    @Test
    fun newestAgeComesFirstThenEarlierOnes() {
        val shown = MilestoneFocus.revealed(content, 9.2)
        val ages = shown.map { it.group.months }
        assertEquals(9, ages.first())
        assertEquals(ages.sortedDescending(), ages)
    }

    @Test
    fun yesSinksToTheEndAfterItsCelebration() {
        val shown = MilestoneFocus.revealed(content, 4.0)
        val first = shown.first().item.id
        val answers = mapOf(
            first to MilestoneAnswer.YES,
            shown[1].item.id to MilestoneAnswer.NOT_YET,
            shown[2].item.id to MilestoneAnswer.PROGRESS,
        )
        // Held for its firework: still in "watch now", in the same place.
        val during = MilestoneFocus.split(shown, answers, held = setOf(first))
        assertEquals(first, during.focus.first().item.id)
        assertTrue(during.done.isEmpty())
        // Then it goes down; "not yet" and "making progress" stay up to be watched.
        val after = MilestoneFocus.split(shown, answers)
        assertEquals(listOf(first), after.done.map { it.item.id })
        assertEquals(shown.size - 1, after.focus.size)
        assertTrue(after.focus.any { it.item.id == shown[1].item.id } && after.focus.any { it.item.id == shown[2].item.id })
    }
}
