package ly.mahd.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.data.Parent
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.AppIcon
import ly.mahd.app.ui.components.ExpressiveButton
import ly.mahd.app.ui.components.GroupItem
import ly.mahd.app.ui.components.GroupRow
import ly.mahd.app.ui.components.ListGroup
import ly.mahd.app.ui.components.MoonPhase
import ly.mahd.app.ui.components.sharedAxisX

/**
 * First run: welcome + disclaimer, then "I'm pregnant" or "I have a child", then the form.
 * Also shown if the mother deletes every profile.
 */
@Composable
fun OnboardingFlow(app: MahdApp) {
    val accepted by app.prefs.disclaimerAccepted.collectAsState()
    var step by rememberSaveable { mutableStateOf(if (accepted) STEP_CHOOSE else STEP_WELCOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A mother on a new phone can bring her data back right away. On success the profiles appear
    // and MahdRoot switches to the main screens by itself.
    val pickBackup = rememberBackupPicker { snapshot ->
        scope.launch {
            val ok = applyRestore(app, snapshot)
            showToast(context, if (ok) R.string.restore_done else R.string.restore_failed)
        }
    }

    // The steps slide like pages, forward and back.
    AnimatedContent(
        targetState = step,
        transitionSpec = { sharedAxisX(forward = stepOrder(targetState) > stepOrder(initialState)) },
        label = "onboarding",
    ) { shown ->
        when (shown) {
            STEP_WELCOME -> WelcomeScreen(onContinue = {
                app.prefs.acceptDisclaimer()
                step = STEP_CHOOSE
            })
            STEP_CHOOSE -> ChooseStartScreen(
                onPregnant = { step = STEP_PREGNANCY },
                onChild = { step = STEP_CHILD },
                onRestore = pickBackup,
            )
            // Once saved, the main screens take over by themselves (MahdRoot), so the step stays as it is.
            STEP_PREGNANCY -> {
                BackHandler(enabled = shown == step) { step = STEP_CHOOSE }
                PregnancyFormScreen(app, existing = null, onClose = { step = STEP_CHOOSE }, onSaved = {})
            }
            STEP_CHILD -> {
                BackHandler(enabled = shown == step) { step = STEP_CHOOSE }
                ChildFormScreen(app, existing = null, onClose = { step = STEP_CHOOSE }, onSaved = {})
            }
        }
    }
}

private const val STEP_WELCOME = "welcome"
private const val STEP_CHOOSE = "choose"
private const val STEP_PREGNANCY = "pregnancy"
private const val STEP_CHILD = "child"

/** Where a step lies in the first run, so moving to a later one slides forward. */
private fun stepOrder(step: String) = when (step) {
    STEP_WELCOME -> 0
    STEP_CHOOSE -> 1
    else -> 2
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The launcher icon's three moons, filling one after the other.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.25f, 0.5f, 1f).forEach { MoonPhase(it, size = 36.dp) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.welcome_subtitle), style = MaterialTheme.typography.titleMedium)
            }
            ListGroup {
                Feature(R.drawable.ic_pregnancy, stringResource(R.string.welcome_feature_pregnancy))
                Feature(R.drawable.ic_vaccine, stringResource(R.string.welcome_feature_vaccines))
                Feature(R.drawable.ic_growth, stringResource(R.string.welcome_feature_growth))
                Feature(R.drawable.ic_milestones, stringResource(R.string.welcome_feature_milestones))
                Feature(R.drawable.ic_lock, stringResource(R.string.welcome_feature_privacy))
            }
            AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyMedium)
            }
            ExpressiveButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.welcome_accept))
            }
        }
    }
}

@Composable
private fun Feature(icon: Int, text: String) {
    GroupRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChooseStartScreen(onPregnant: () -> Unit, onChild: () -> Unit, onRestore: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Centred when everything fits; scrolls on a small phone or with a large font instead of cutting off the end.
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(R.string.choose_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                ListGroup {
                    // A half moon for a pregnancy (on the way), a full one for a child who has arrived.
                    BigChoice(0.5f, stringResource(R.string.choose_pregnant), stringResource(R.string.choose_pregnant_hint), onPregnant)
                    BigChoice(1f, stringResource(R.string.choose_child), stringResource(R.string.choose_child_hint), onChild)
                }
                ListGroup {
                    GroupItem(
                        headline = stringResource(R.string.choose_restore),
                        supporting = stringResource(R.string.choose_restore_hint),
                        onClick = onRestore,
                    )
                }
                Text(
                    stringResource(R.string.choose_more_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BigChoice(moon: Float, title: String, hint: String, onClick: () -> Unit) {
    GroupRow(onClick = onClick, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonPhase(moon, size = 52.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(R.drawable.ic_chevron_forward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Asked once, before anything else: is the mother or the father using the app on this phone?
 * The answer changes the voice of every text, the advice, and the colours (rose or blue). Changeable in Settings.
 */
@Composable
fun ParentChoiceScreen(app: MahdApp) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(0.25f, 0.5f, 1f).forEach { MoonPhase(it, size = 28.dp) }
                    }
                    Text(stringResource(R.string.role_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.role_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ListGroup {
                    // Each choice shows its own colour: rose for the mother, blue for the father.
                    RoleChoice(MOTHER_ROSE, stringResource(R.string.role_mother), stringResource(R.string.role_mother_hint)) {
                        app.prefs.setParent(Parent.MOTHER)
                    }
                    RoleChoice(FATHER_BLUE, stringResource(R.string.role_father), stringResource(R.string.role_father_hint)) {
                        app.prefs.setParent(Parent.FATHER)
                    }
                }
            }
        }
    }
}

private val MOTHER_ROSE = Color(0xFFA23B5B)
private val FATHER_BLUE = Color(0xFF2C5A9E)

@Composable
private fun RoleChoice(color: Color, title: String, hint: String, onClick: () -> Unit) {
    GroupRow(onClick = onClick, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonPhase(1f, size = 44.dp, lit = color)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(R.drawable.ic_chevron_forward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
