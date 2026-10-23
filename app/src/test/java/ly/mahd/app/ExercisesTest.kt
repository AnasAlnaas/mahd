package ly.mahd.app

import ly.mahd.app.content.ContentRepository
import ly.mahd.app.logic.ExerciseRoutine
import ly.mahd.app.logic.PregnancyMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

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

    @Test
    fun theDailyProgramUsesOnlyExistingExercisesInTheThirdTrimester() {
        val steps = parts.flatMap { p -> p.stages.flatMap { it.routine } }
        steps.forEach { s ->
            assertNotNull("routine step ${s.exerciseId} names no exercise (the owner adds no new ones)", exercises.exercise(s.exerciseId))
            assertTrue(s.exerciseId, s.dose.isNotBlank() && "كِ" !in s.dose && "تِ" !in s.dose)
        }
        val ids = steps.map { it.exerciseId }
        assertEquals("an exercise twice in one program", ids.size, ids.toSet().size)
        // The owner's program (her PDF): weeks 28 to 42, in her order, without the two exercises the app does not have.
        assertEquals(
            listOf("preg_breathing", "post_pelvic_floor", "preg_cat_cow", "preg_butterfly", "preg_side_leg_lift", "preg_wall_pushup", "preg_squat"),
            exercises.routineFor(34).map { it.first.id },
        )
        // A program's own pictures follow the same names; Kegel in pregnancy must not show the after-birth cot picture.
        parts.flatMap { p -> p.stages.flatMap { it.routine } }.forEach { s ->
            s.images?.forEach { name ->
                assertTrue("$name does not belong to ${s.exerciseId}", name.startsWith("${s.exerciseId}_") || name == "pelvic_floor_diagram")
            }
        }
        val kegel = exercises.routineFor(34).first { it.first.id == "post_pelvic_floor" }.first
        assertTrue("post_pelvic_floor" !in kegel.images && kegel.images.isNotEmpty())
        assertTrue("the full list keeps its own picture", "post_pelvic_floor" in exercises.exercise("post_pelvic_floor")!!.images)
        assertEquals(7, exercises.routineFor(28).size)
        assertEquals(7, exercises.routineFor(42).size)
        assertTrue("no program before the third trimester", exercises.routineFor(27).isEmpty())
        assertTrue(exercises.postpartum.stages.all { it.routine.isEmpty() })
    }

    @Test
    fun theProgramsWeekIsThePregnancyWeekAndOnlyDaysThatCameCanBeTicked() {
        val due = LocalDate.of(2026, 12, 6)
        val today = LocalDate.of(2026, 10, 23) // week 33 + 5 days; the owner's PDF starts week 34 on 25/10 too
        val days = ExerciseRoutine.weekDays(due, today)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 18), days.first())
        assertEquals(LocalDate.of(2026, 10, 25), days.last().plusDays(1))
        assertTrue(today in days)
        days.forEach { assertEquals(33, PregnancyMath.gestationalAge(due, it).weeks) }
        assertTrue(ExerciseRoutine.canTick(days.first(), today))
        assertFalse(ExerciseRoutine.canTick(today.plusDays(1), today))

        val ids = listOf("a", "b", "c", "d")
        assertEquals(0f, ExerciseRoutine.progress(null, ids))
        assertEquals(2, ExerciseRoutine.doneCount(setOf("a", "c", "other"), ids))
        assertEquals(0.5f, ExerciseRoutine.progress(setOf("a", "c", "other"), ids))
        assertEquals(0f, ExerciseRoutine.progress(setOf("a"), emptyList()))

        // In order: the exercise to do now is the first one not ticked; all done = past the end.
        assertEquals(0, ExerciseRoutine.current(null, ids))
        assertEquals(2, ExerciseRoutine.current(setOf("a", "b"), ids))
        assertEquals(1, ExerciseRoutine.current(setOf("a", "c"), ids)) // a gap left by an older version
        assertEquals(4, ExerciseRoutine.current(ids.toSet(), ids))
    }
}
