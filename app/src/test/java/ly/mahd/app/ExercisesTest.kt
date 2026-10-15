package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExercisesTest {
    private val exercises = ContentRepository { path -> File("src/main/assets/$path").inputStream() }.exercises
    private val parts = listOf(exercises.pregnancy, exercises.postpartum)
    private val all = parts.flatMap { it.exercises }

    @Test
    fun everyPartIsFilledIn() {
        assertFalse("medical content stays a draft until a doctor reviews it", exercises.reviewed)
        assertEquals("the owner's 17 exercises", 17, all.size)
        parts.forEach { p ->
            assertTrue(p.intro.isNotEmpty() && p.stages.isNotEmpty() && p.avoid.isNotEmpty())
            assertTrue(p.stopSigns.isNotEmpty() && p.father.isNotEmpty() && p.source.isNotBlank())
            p.stages.forEach { s -> assertTrue(s.id, s.about.isNotBlank() && s.exercises.isNotEmpty()) }
        }
        all.forEach { e -> assertTrue(e.id, e.title.isNotBlank() && e.steps.isNotEmpty() && e.dose != null && e.images.isNotEmpty()) }
        // After the birth, each kind of birth has its own word; in pregnancy there is none.
        assertTrue(exercises.postpartum.natural.isNotEmpty() && exercises.postpartum.caesarean.isNotEmpty())
        assertTrue(exercises.pregnancy.natural.isEmpty() && exercises.pregnancy.caesarean.isEmpty())
        val ids = all.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
    }

    @Test
    fun trimestersCoverEveryWeekAndTheCurrentOneComesFirst() {
        val stages = exercises.pregnancy.stages
        assertEquals(1, stages.first().fromWeek)
        assertEquals(42, stages.last().toWeek)
        stages.zipWithNext().forEach { (a, b) -> assertEquals("gap after week ${a.toWeek}", a.toWeek!! + 1, b.fromWeek) }
        assertEquals(listOf("t2", "t1", "t3"), exercises.pregnancy.stagesFor(20).map { it.id })
        assertEquals(listOf("t1", "t2", "t3"), exercises.pregnancy.stagesFor(null).map { it.id })
        // After the birth there are no weeks: one stage, always shown.
        assertTrue(exercises.postpartum.stages.all { it.fromWeek == null && it.toWeek == null })
    }

    @Test
    fun sharedTextSpeaksToNeitherParent() {
        // "كِ" / "تِ" would read wrong to the father.
        val shared = parts.flatMap { p ->
            p.intro + p.natural + p.caesarean + p.avoid + p.stopSigns + p.stages.flatMap { listOf(it.title, it.about) } +
                p.exercises.flatMap { e -> e.steps + listOfNotNull(e.title, e.dose, e.warning, e.caesarean) }
        }
        shared.forEach { assertTrue("'$it' speaks to the mother", "كِ" !in it && "تِ" !in it) }
    }

    @Test
    fun breastfeedingOnly() {
        val text = parts.flatMap { p ->
            p.intro + p.natural + p.caesarean + p.avoid + p.stopSigns + p.father +
                p.exercises.flatMap { e -> e.steps + listOfNotNull(e.warning, e.caesarean) }
        }
        text.forEach { t -> assertTrue(t, listOf("رضّاعة", "رضاعة صناعية", "زجاجة", "صناعي", "ببرونة", "حليب صناعي").none { it in t }) }
    }

    @Test
    fun pictureNamesFollowTheirExercise() {
        all.forEach { e ->
            e.images.forEach { name ->
                assertTrue("$name: lower case, digits and _ only", name.matches(Regex("[a-z0-9_]+")))
                assertTrue("$name does not belong to ${e.id}", name == e.id || name.matches(Regex("${e.id}_[12]")) || name == "pelvic_floor_diagram")
            }
        }
    }

    @Test
    fun everyPictureFileBelongsToAnExercise() {
        // tools/prepare_exercise_pictures.ps1 writes them; a file no exercise names is a typo or a leftover.
        val known = exercises.pictureNames.toSet()
        val files = File("src/main/assets/exercises").listFiles().orEmpty()
        files.forEach { f ->
            assertTrue("${f.name}: pictures are WebP", f.name.endsWith(".webp"))
            assertTrue("${f.name}: no exercise uses this picture", f.name.removeSuffix(".webp") in known)
        }
    }
}
