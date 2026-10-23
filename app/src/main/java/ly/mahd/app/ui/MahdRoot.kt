package ly.mahd.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.Profile
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.CloseScreenProgress
import ly.mahd.app.ui.components.closeScreen
import ly.mahd.app.ui.components.fadeThrough
import ly.mahd.app.ui.components.openScreen
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.screens.AboutScreen
import ly.mahd.app.ui.screens.BabyBornScreen
import ly.mahd.app.ui.screens.HospitalBagScreen
import ly.mahd.app.ui.screens.PostpartumScreen
import ly.mahd.app.ui.screens.ChildDangerSignsScreen
import ly.mahd.app.ui.screens.ChildFormScreen
import ly.mahd.app.ui.screens.CompareScreen
import ly.mahd.app.ui.screens.DangerSignsScreen
import ly.mahd.app.ui.screens.ExercisesScreen
import ly.mahd.app.ui.screens.HomeScreen
import ly.mahd.app.ui.screens.SkillsAndGrowth
import ly.mahd.app.ui.screens.OnboardingFlow
import ly.mahd.app.ui.screens.ParentChoiceScreen
import ly.mahd.app.ui.screens.PhotoScreen
import ly.mahd.app.ui.screens.PhotosScreen
import ly.mahd.app.ui.screens.PregnancyFormScreen
import ly.mahd.app.ui.screens.PregnancyScreen
import ly.mahd.app.ui.screens.SettingsScreen
import ly.mahd.app.ui.screens.VaccinesScreen

/** The big stages of the app: still loading, who uses it, the first run, and the app itself. */
private enum class Phase { LOADING, PARENT, ONBOARDING, MAIN }

/**
 * Decides what to show: first-run welcome, or the main app for the selected profile. The stages fade into each other.
 * [openRequest] (from a tapped reminder) switches to that profile and tab once, then [onOpenHandled] clears it.
 */
@Composable
fun MahdRoot(app: MahdApp, openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val profiles by remember { app.repository.profiles }.collectAsState(initial = null)
    val accepted by app.prefs.disclaimerAccepted.collectAsState()
    val parent by app.prefs.parent.collectAsState()
    val selectedId by app.prefs.selectedProfileId.collectAsState()
    val nav = rememberNavigator()

    val phase = when {
        profiles == null -> Phase.LOADING
        // First of all: who uses the app (also asked once of mothers who installed before the father version).
        parent == null -> Phase.PARENT
        !accepted || profiles.isNullOrEmpty() -> Phase.ONBOARDING
        else -> Phase.MAIN
    }
    Crossfade(targetState = phase, animationSpec = AppMotion.slowEffects(), label = "phase") { shown ->
        val list = profiles.orEmpty()
        when {
            shown == Phase.PARENT -> ParentChoiceScreen(app)
            shown == Phase.ONBOARDING -> OnboardingFlow(app)
            // Loading, or the main screens fading out after the last profile was deleted.
            shown == Phase.LOADING || list.isEmpty() -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            else -> {
                val selected = list.firstOrNull { it.id == selectedId } ?: list.first()
                if (openRequest != null && phase == Phase.MAIN) {
                    // Select the profile first; the tab is opened when this runs again with that profile selected
                    // (otherwise the tab could be checked against the previous profile and dropped).
                    LaunchedEffect(openRequest, selected.id) {
                        when {
                            list.none { it.id == openRequest.profileId } -> onOpenHandled() // deleted since the reminder
                            selected.id != openRequest.profileId -> app.prefs.selectProfile(openRequest.profileId)
                            else -> {
                                nav.selectTab(openRequest.route)
                                onOpenHandled()
                            }
                        }
                    }
                }
                MainNav(app, nav, list, selected)
            }
        }
    }
}

private fun tabsFor(profile: Profile): List<String> =
    if (profile.isPregnancy) listOf(Routes.HOME, Routes.PREGNANCY)
    else listOf(Routes.HOME, Routes.VACCINES, Routes.MILESTONES, Routes.PHOTOS)

/** All the tabs are one screen (they fade into each other inside it); every other route is a screen of its own. */
private const val TABS_KEY = "tabs"

private fun screenKey(route: String): String = if (route.routeName in Routes.TABS) TABS_KEY else route

/**
 * What fills the window, and how deep it is in the navigator: a deeper screen slides in, a shallower one is uncovered.
 * Two screens with the same [key] are the same screen (switching tabs is not a change of screen).
 */
private class Screen(val key: String, val depth: Int) {
    override fun equals(other: Any?) = other is Screen && other.key == key
    override fun hashCode() = key.hashCode()
}

private fun isBack(from: Screen, to: Screen) = to.depth < from.depth || (to.depth == from.depth && to.key == TABS_KEY)

/** How round a screen's corners get while it slides in or shrinks away (like Android's own back animation). */
private val MOVING_SCREEN_CORNER = 28.dp

/**
 * The screens: opening one slides it in, going back slides it away and uncovers the one below — following the finger
 * during Android's back swipe (predictive back). The tabs keep their place (scroll, chosen chart) while a screen is
 * open on top of them.
 */
@Composable
private fun MainNav(app: MahdApp, nav: Navigator, profiles: List<Profile>, selected: Profile) {
    val scope = rememberCoroutineScope()
    val current = Screen(screenKey(nav.current), nav.depth)
    val state = remember { SeekableTransitionState(current) }
    val transition = rememberTransition(state, label = "screens")

    // Follow the navigator. Going back runs along a spring (also after a back swipe is let go); opening uses its own.
    LaunchedEffect(current) {
        // The screen being left: the one on top, or the one still sliding in when the navigator changed again.
        // (After a back swipe the transition already points at the new screen, so it leaves the one it started from.)
        val from = if (state.targetState == current) state.currentState else state.targetState
        state.animateTo(current, if (isBack(from, current)) CloseScreenProgress else null)
    }
    // During a back swipe the screen moves the way the finger does (away from the edge it started from).
    var swipeTowards by remember { mutableStateOf<SlideDirection?>(null) }
    PredictiveBackHandler(enabled = nav.canGoBack) { swipe ->
        val previous = nav.previous?.let { Screen(screenKey(it), nav.depth - 1) }?.takeIf { it != current }
        try {
            swipe.collect { event ->
                if (swipeTowards == null) {
                    swipeTowards = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) SlideDirection.Left else SlideDirection.Right
                }
                if (previous != null) state.seekTo(event.progress, previous)
            }
            nav.back()
        } catch (e: CancellationException) {
            // The swipe was given up: the screen settles back where it was.
            scope.launch { state.animateTo(state.currentState) }
            throw e
        } finally {
            swipeTowards = null
        }
    }
    BackHandler(enabled = !nav.canGoBack && nav.current != Routes.HOME) { nav.selectTab(Routes.HOME) }

    // The tab under the open screens (kept while the bottom of the stack is a screen opened on its own).
    var lastTab by rememberSaveable { mutableStateOf(Routes.HOME) }
    val root = nav.root
    val tabRoute = if (root.routeName in Routes.TABS) root else lastTab
    if (tabRoute != lastTab) SideEffect { lastTab = tabRoute }

    // Each open screen keeps what it remembers (its scroll, for one) while another is on top of it; a closed screen
    // forgets it, so it opens fresh the next time.
    val holder = rememberSaveableStateHolder()
    val openKeys = nav.routes.map(::screenKey).toSet()
    val lastOpenKeys = remember { arrayOf(openKeys) }
    LaunchedEffect(openKeys) {
        (lastOpenKeys[0] - openKeys).forEach(holder::removeState)
        lastOpenKeys[0] = openKeys
    }

    transition.AnimatedContent(
        transitionSpec = { if (isBack(initialState, targetState)) closeScreen(swipeTowards ?: SlideDirection.End) else openScreen() },
        contentKey = { it.key },
    ) { screen ->
        val corner by this.transition.animateDp(label = "corner") { if (it == EnterExitState.Visible) 0.dp else MOVING_SCREEN_CORNER }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                shape = RoundedCornerShape(corner)
                clip = corner > 0.dp
            },
        ) {
            holder.SaveableStateProvider(screen.key) {
                // Only the screen being shown may move the navigator (not one sliding away or peeking during a swipe).
                val active = screen.key == screenKey(nav.current)
                ScreenContent(app, nav, profiles, selected, if (screen.key == TABS_KEY) tabRoute else screen.key, active)
            }
        }
    }
}

@Composable
private fun ScreenContent(app: MahdApp, nav: Navigator, profiles: List<Profile>, selected: Profile, route: String, active: Boolean) {
    /** For a route that cannot be shown (its profile was deleted, say): move on, if this screen is the one shown. */
    @Composable
    fun redirect(action: () -> Unit) {
        if (active) LaunchedEffect(route) { action() }
    }

    val arg = route.routeArg
    when (route.routeName) {
        in Routes.TABS -> TabsScaffold(app, nav, profiles, selected, route, active)
        Routes.ADD_PREGNANCY -> PregnancyFormScreen(app, existing = null, onClose = nav::back)
        Routes.ADD_CHILD -> ChildFormScreen(app, existing = null, onClose = nav::back)
        "edit" -> {
            val profile = profiles.firstOrNull { it.id == arg?.toLongOrNull() }
            when {
                profile == null -> redirect { nav.back() }
                profile.isPregnancy -> PregnancyFormScreen(app, existing = profile, onClose = nav::back)
                else -> ChildFormScreen(app, existing = profile, onClose = nav::back)
            }
        }
        "born" -> {
            val profile = profiles.firstOrNull { it.id == arg?.toLongOrNull() }
            if (profile == null) redirect { nav.back() }
            else BabyBornScreen(app, profile, onDone = { nav.selectTab(Routes.HOME) }, onClose = nav::back)
        }
        "week" -> PregnancyScreen(app, selected, nav, initialWeek = arg?.toIntOrNull(), standalone = true)
        Routes.DANGER_SIGNS -> DangerSignsScreen(app, onBack = nav::back)
        Routes.CHILD_DANGER_SIGNS -> ChildDangerSignsScreen(app, selected, onBack = nav::back)
        // Opened from Home or from a check-up reminder; back goes Home (see BackHandler above).
        Routes.POSTPARTUM ->
            if (selected.isChild) PostpartumScreen(app, selected, onBack = { if (nav.canGoBack) nav.back() else nav.selectTab(Routes.HOME) })
            else redirect { nav.selectTab(Routes.HOME) }
        Routes.HOSPITAL_BAG ->
            if (selected.isPregnancy) HospitalBagScreen(app, selected, onBack = nav::back)
            else redirect { nav.selectTab(Routes.HOME) }
        Routes.EXERCISES -> ExercisesScreen(app, selected, onBack = nav::back)
        "photo" -> {
            val id = arg?.toLongOrNull()
            if (id == null) redirect { nav.back() } else PhotoScreen(app, selected, id, nav)
        }
        "compare" -> CompareScreen(app, selected, firstId = arg?.toLongOrNull()?.takeIf { it > 0 }, onBack = nav::back)
        Routes.SETTINGS -> SettingsScreen(app, nav, profiles)
        Routes.ABOUT -> AboutScreen(app, onBack = nav::back)
        else -> redirect { nav.selectTab(Routes.HOME) }
    }
}

/** One page of the tabs: a tab of one profile. Another profile is another page, so nothing of the last child shows. */
private data class TabPage(val tab: String, val profileId: Long)

@Composable
private fun TabsScaffold(app: MahdApp, nav: Navigator, profiles: List<Profile>, selected: Profile, route: String, active: Boolean) {
    val tabs = tabsFor(selected)
    // Growth is the second part of the skills tab, not a tab of its own.
    val shown = if (route == Routes.GROWTH) Routes.MILESTONES else route
    val tab = if (shown in tabs) shown else Routes.HOME
    if (tab != shown && active) LaunchedEffect(route, selected.id) { nav.selectTab(Routes.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { ProfileSwitcher(app, nav, profiles, selected) },
                actions = {
                    IconButton(onClick = { nav.push(Routes.SETTINGS) }) {
                        AppIcon(R.drawable.ic_settings, stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            // Material 3 Expressive's short navigation bar: a slimmer bar with a pill behind the current tab.
            ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { t ->
                    ShortNavigationBarItem(
                        selected = t == tab,
                        onClick = { nav.selectTab(t) },
                        icon = { AppIcon(tabIcon(t), null) },
                        label = { Text(stringResource(tabLabel(t)), maxLines = 1) },
                        colors = ShortNavigationBarItemDefaults.colors(
                            selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // Material's fade through between tabs, and between profiles.
        AnimatedContent(
            targetState = TabPage(tab, selected.id),
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = { fadeThrough() },
            label = "tab",
        ) { page ->
            // The page going away keeps showing its own profile.
            val profile = profiles.firstOrNull { it.id == page.profileId }
            Box(Modifier.fillMaxSize()) {
                if (profile != null) {
                    when (page.tab) {
                        Routes.HOME -> HomeScreen(app, profile, profiles, nav)
                        Routes.PREGNANCY -> PregnancyScreen(app, profile, nav, initialWeek = null, standalone = false)
                        Routes.VACCINES -> VaccinesScreen(app, profile)
                        Routes.MILESTONES -> SkillsAndGrowth(app, profile, nav, growth = route == Routes.GROWTH)
                        Routes.PHOTOS -> PhotosScreen(app, profile, nav)
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: String): Int = when (tab) {
    Routes.PREGNANCY -> R.drawable.ic_pregnancy
    Routes.VACCINES -> R.drawable.ic_vaccine
    // The tab holds skills and growth, and is named النمو (owner's choice) with the growth chart icon.
    Routes.MILESTONES -> R.drawable.ic_growth
    Routes.PHOTOS -> R.drawable.ic_photos
    else -> R.drawable.ic_home
}

private fun tabLabel(tab: String): Int = when (tab) {
    Routes.PREGNANCY -> R.string.tab_pregnancy
    Routes.VACCINES -> R.string.tab_vaccines
    Routes.MILESTONES -> R.string.tab_growth
    Routes.PHOTOS -> R.string.tab_photos
    else -> R.string.tab_home
}

/**
 * A profile's picture: for a pregnancy, a moon lit as far as the pregnancy has come (week / 40);
 * for a child, the first letter of the name on a soft disc.
 */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp = 36.dp) {
    if (profile.isPregnancy) {
        val today = LocalToday.current
        val weeks = profile.dueDate?.let { PregnancyMath.gestationalAge(it, today).weeks } ?: 0
        Box(
            Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MoonPhase(weeks / 40f, size = size * 0.62f, animate = false)
        }
    } else {
        Box(
            Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profileName(profile).trim().take(1),
                style = if (size >= 44.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun profileName(profile: Profile): String =
    profile.name.ifBlank { stringResource(if (profile.isPregnancy) R.string.default_pregnancy_name else R.string.default_child_name) }

/** The name in the top bar; tapping it lists all profiles and lets the mother add another one. */
@Composable
private fun ProfileSwitcher(app: MahdApp, nav: Navigator, profiles: List<Profile>, selected: Profile) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The picture and name of the new profile fade in with its page.
            Crossfade(targetState = selected, animationSpec = AppMotion.slowEffects(), label = "profile") { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(p)
                    Spacer(Modifier.width(10.dp))
                    Text(profileName(p), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                }
            }
            AppIcon(R.drawable.ic_expand, stringResource(R.string.switch_profile))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = { Text(profileName(p)) },
                    leadingIcon = { ProfileAvatar(p, size = 28.dp) },
                    trailingIcon = if (p.id == selected.id) ({ AppIcon(R.drawable.ic_check, null) }) else null,
                    onClick = {
                        open = false
                        app.prefs.selectProfile(p.id)
                        nav.selectTab(Routes.HOME)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_pregnancy)) },
                leadingIcon = { AppIcon(R.drawable.ic_add, null) },
                onClick = { open = false; nav.push(Routes.ADD_PREGNANCY) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_child)) },
                leadingIcon = { AppIcon(R.drawable.ic_add, null) },
                onClick = { open = false; nav.push(Routes.ADD_CHILD) },
            )
        }
    }
}
