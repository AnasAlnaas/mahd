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
    const val GROWTH = "growth"
    const val MILESTONES = "milestones"

    // Screens opened on top
    const val ADD_PREGNANCY = "add_pregnancy"
    const val ADD_CHILD = "add_child"
    const val DANGER_SIGNS = "danger_signs"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun edit(profileId: Long) = "edit/$profileId"
    fun born(profileId: Long) = "born/$profileId"
    fun week(n: Int) = "week/$n"

    val TABS = setOf(HOME, PREGNANCY, VACCINES, GROWTH, MILESTONES)
}

/** "edit/12" -> "edit" */
val String.routeName: String get() = substringBefore('/')

/** "edit/12" -> "12" */
val String.routeArg: String? get() = if (contains('/')) substringAfter('/') else null

@Composable
fun rememberNavigator(): Navigator = rememberSaveable(
    saver = Saver<Navigator, ArrayList<String>>(save = { it.save() }, restore = { Navigator(it) }),
) { Navigator(listOf(Routes.HOME)) }
