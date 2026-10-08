package ly.mahd.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.Profile
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.logic.PregnancyMath
import ly.mahd.app.ui.screens.AboutScreen
import ly.mahd.app.ui.screens.BabyBornScreen
import ly.mahd.app.ui.screens.ChildDangerSignsScreen
import ly.mahd.app.ui.screens.ChildFormScreen
import ly.mahd.app.ui.screens.CompareScreen
import ly.mahd.app.ui.screens.DangerSignsScreen
import ly.mahd.app.ui.screens.GrowthScreen
import ly.mahd.app.ui.screens.HomeScreen
import ly.mahd.app.ui.screens.MilestonesScreen
import ly.mahd.app.ui.screens.OnboardingFlow
import ly.mahd.app.ui.screens.PhotoScreen
import ly.mahd.app.ui.screens.PhotosScreen
import ly.mahd.app.ui.screens.PregnancyFormScreen
import ly.mahd.app.ui.screens.PregnancyScreen
import ly.mahd.app.ui.screens.SettingsScreen
import ly.mahd.app.ui.screens.VaccinesScreen

/**
 * Decides what to show: first-run welcome, or the main app for the selected profile.
 * [openRequest] (from a tapped reminder) switches to that profile and tab once, then [onOpenHandled] clears it.
 */
@Composable
fun MahdRoot(app: MahdApp, openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val profiles by remember { app.repository.profiles }.collectAsState(initial = null)
    val accepted by app.prefs.disclaimerAccepted.collectAsState()
    val selectedId by app.prefs.selectedProfileId.collectAsState()
    val nav = rememberNavigator()

    val list = profiles
    when {
        list == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        !accepted || list.isEmpty() -> OnboardingFlow(app)
        else -> {
            val selected = list.firstOrNull { it.id == selectedId } ?: list.first()
            if (openRequest != null) {
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

private fun tabsFor(profile: Profile): List<String> =
    if (profile.isPregnancy) listOf(Routes.HOME, Routes.PREGNANCY)
    else listOf(Routes.HOME, Routes.VACCINES, Routes.GROWTH, Routes.MILESTONES, Routes.PHOTOS)

@Composable
private fun MainNav(app: MahdApp, nav: Navigator, profiles: List<Profile>, selected: Profile) {
    BackHandler(enabled = nav.canGoBack) { nav.back() }
    BackHandler(enabled = !nav.canGoBack && nav.current != Routes.HOME) { nav.selectTab(Routes.HOME) }

    val route = nav.current
    val arg = route.routeArg
    when (route.routeName) {
        in Routes.TABS -> TabsScaffold(app, nav, profiles, selected, route)
        Routes.ADD_PREGNANCY -> PregnancyFormScreen(app, existing = null, onClose = nav::back)
        Routes.ADD_CHILD -> ChildFormScreen(app, existing = null, onClose = nav::back)
        "edit" -> {
            val profile = profiles.firstOrNull { it.id == arg?.toLongOrNull() }
            when {
                profile == null -> LaunchedEffect(route) { nav.back() }
                profile.isPregnancy -> PregnancyFormScreen(app, existing = profile, onClose = nav::back)
                else -> ChildFormScreen(app, existing = profile, onClose = nav::back)
            }
        }
        "born" -> {
            val profile = profiles.firstOrNull { it.id == arg?.toLongOrNull() }
            if (profile == null) LaunchedEffect(route) { nav.back() }
            else BabyBornScreen(app, profile, onDone = { nav.selectTab(Routes.HOME) }, onClose = nav::back)
        }
        "week" -> PregnancyScreen(app, selected, nav, initialWeek = arg?.toIntOrNull(), standalone = true)
        Routes.DANGER_SIGNS -> DangerSignsScreen(app, onBack = nav::back)
        Routes.CHILD_DANGER_SIGNS -> ChildDangerSignsScreen(app, selected, onBack = nav::back)
        "photo" -> {
            val id = arg?.toLongOrNull()
            if (id == null) LaunchedEffect(route) { nav.back() } else PhotoScreen(app, selected, id, nav)
        }
        "compare" -> CompareScreen(app, selected, firstId = arg?.toLongOrNull()?.takeIf { it > 0 }, onBack = nav::back)
        Routes.SETTINGS -> SettingsScreen(app, nav, profiles)
        Routes.ABOUT -> AboutScreen(app, onBack = nav::back)
        else -> LaunchedEffect(route) { nav.selectTab(Routes.HOME) }
    }
}

@Composable
private fun TabsScaffold(app: MahdApp, nav: Navigator, profiles: List<Profile>, selected: Profile, route: String) {
    val tabs = tabsFor(selected)
    val tab = if (route in tabs) route else Routes.HOME
    if (tab != route) LaunchedEffect(route, selected.id) { nav.selectTab(Routes.HOME) }

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
        // A soft fade when switching tabs.
        Crossfade(targetState = tab, animationSpec = AppMotion.defaultEffects(), label = "tab") { current ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                // A fresh screen for each profile, so nothing of the previous child shows while the new data loads.
                key(selected.id) {
                    when (current) {
                        Routes.HOME -> HomeScreen(app, selected, nav)
                        Routes.PREGNANCY -> PregnancyScreen(app, selected, nav, initialWeek = null, standalone = false)
                        Routes.VACCINES -> VaccinesScreen(app, selected)
                        Routes.GROWTH -> GrowthScreen(app, selected)
                        Routes.MILESTONES -> MilestonesScreen(app, selected)
                        Routes.PHOTOS -> PhotosScreen(app, selected, nav)
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: String): Int = when (tab) {
    Routes.PREGNANCY -> R.drawable.ic_pregnancy
    Routes.VACCINES -> R.drawable.ic_vaccine
    Routes.GROWTH -> R.drawable.ic_growth
    Routes.MILESTONES -> R.drawable.ic_milestones
    Routes.PHOTOS -> R.drawable.ic_photos
    else -> R.drawable.ic_home
}

private fun tabLabel(tab: String): Int = when (tab) {
    Routes.PREGNANCY -> R.string.tab_pregnancy
    Routes.VACCINES -> R.string.tab_vaccines
    Routes.GROWTH -> R.string.tab_growth
    Routes.MILESTONES -> R.string.tab_milestones
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
            ProfileAvatar(selected)
            Spacer(Modifier.width(10.dp))
            Text(profileName(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
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
