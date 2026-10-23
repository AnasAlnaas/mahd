package ly.mahd.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * A tiny screen navigator: a list of route strings, the last one is on screen.
 * Simple enough that we don't need a navigation library.
 */
class Navigator(initial: List<String>) {
    private val stack = mutableStateListOf<String>().apply { addAll(initial.ifEmpty { listOf(Routes.HOME) }) }

    val current: String get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** The screen [back] returns to (null when there is none). */
    val previous: String? get() = stack.getOrNull(stack.size - 2)

    /** The screen at the bottom: a tab, or a screen opened on its own (e.g. from a reminder). */
    val root: String get() = stack.first()

    /** How many screens are open (1 = only the root): tells opening a screen from going back. */
    val depth: Int get() = stack.size

    /** Every open screen, the root first. */
    val routes: List<String> get() = stack.toList()

    fun push(route: String) {
        if (current != route) stack.add(route)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Switches bottom tab and forgets the screens opened on top of the previous tab. */
    fun selectTab(route: String) {
        stack.clear()
        stack.add(route)
    }

    internal fun save(): ArrayList<String> = ArrayList(stack)
}

object Routes {
    // Bottom tabs
    const val HOME = "home"
    const val PREGNANCY = "pregnancy"
    const val VACCINES = "vaccines"
    /** Not a tab of its own: the skills tab, opened on its growth part. */
    const val GROWTH = "growth"
    const val MILESTONES = "milestones"
    const val PHOTOS = "photos"

    // Screens opened on top
    const val ADD_PREGNANCY = "add_pregnancy"
    const val ADD_CHILD = "add_child"
    const val DANGER_SIGNS = "danger_signs"
    const val CHILD_DANGER_SIGNS = "child_danger_signs"
    /** The mother's الأربعين, for the selected child. */
    const val POSTPARTUM = "postpartum"
    const val HOSPITAL_BAG = "hospital_bag"
    /** The mother's exercises, in pregnancy and after birth. */
    const val EXERCISES = "exercises"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun edit(profileId: Long) = "edit/$profileId"
    fun born(profileId: Long) = "born/$profileId"
    fun week(n: Int) = "week/$n"
    fun photo(id: Long) = "photo/$id"

    /** Side-by-side photos, starting with photo [firstId] (or the oldest one). */
    fun compare(firstId: Long?) = "compare/${firstId ?: 0}"

    val TABS = setOf(HOME, PREGNANCY, VACCINES, GROWTH, MILESTONES, PHOTOS)
}

/** Asks the app to show the tab [route] for profile [profileId], e.g. when the mother taps a reminder. */
data class OpenRequest(val profileId: Long, val route: String)

/** "edit/12" -> "edit" */
val String.routeName: String get() = substringBefore('/')

/** "edit/12" -> "12" */
val String.routeArg: String? get() = if (contains('/')) substringAfter('/') else null

@Composable
fun rememberNavigator(): Navigator = rememberSaveable(
    saver = Saver<Navigator, ArrayList<String>>(save = { it.save() }, restore = { Navigator(it) }),
) { Navigator(listOf(Routes.HOME)) }
