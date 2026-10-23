package ly.mahd.app.logic

import java.time.LocalDate

/**
 * The daily exercise program (a stage's "routine" in exercises.json): one tick per exercise per day. Kegel, done three
 * times a day, is one tick too, once the day's sets are done (the owner does not want the app opened many times a day).
 */
object ExerciseRoutine {

    /** The 7 days of the pregnancy week that [today] is in, the week's first day first. */
    fun weekDays(dueDate: LocalDate, today: LocalDate): List<LocalDate> {
        val start = today.minusDays(PregnancyMath.gestationalAge(dueDate, today).days.toLong())
        return (0L until 7L).map { start.plusDays(it) }
    }

    /** A day can be ticked when it has come: today, or a day earlier in this pregnancy week (a forgotten tick). */
    fun canTick(day: LocalDate, today: LocalDate): Boolean = !day.isAfter(today)

    /** How many of the program's [exerciseIds] were ticked on a day ([done]: that day's ticks). */
    fun doneCount(done: Set<String>?, exerciseIds: List<String>): Int = exerciseIds.count { done?.contains(it) == true }

    /**
     * The exercise to do now: the index of the first one not ticked ([exerciseIds].size when all are done). The program
     * is done in order, so only this one can be ticked, and only the one just before it un-ticked.
     */
    fun current(done: Set<String>?, exerciseIds: List<String>): Int =
        exerciseIds.indexOfFirst { done?.contains(it) != true }.let { if (it == -1) exerciseIds.size else it }

    /** The day's share of the program, 0..1, for its moon. */
    fun progress(done: Set<String>?, exerciseIds: List<String>): Float =
        if (exerciseIds.isEmpty()) 0f else doneCount(done, exerciseIds) / exerciseIds.size.toFloat()
}
