package ly.mahd.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.ui.components.AppCard

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

    when (step) {
        STEP_WELCOME -> WelcomeScreen(onContinue = {
            app.prefs.acceptDisclaimer()
            step = STEP_CHOOSE
        })
        STEP_CHOOSE -> ChooseStartScreen(
            onPregnant = { step = STEP_PREGNANCY },
            onChild = { step = STEP_CHILD },
            onRestore = pickBackup,
        )
        STEP_PREGNANCY -> {
            BackHandler { step = STEP_CHOOSE }
            PregnancyFormScreen(app, existing = null, onClose = { step = STEP_CHOOSE })
        }
        STEP_CHILD -> {
            BackHandler { step = STEP_CHOOSE }
            ChildFormScreen(app, existing = null, onClose = { step = STEP_CHOOSE })
        }
    }
}

private const val STEP_WELCOME = "welcome"
private const val STEP_CHOOSE = "choose"
private const val STEP_PREGNANCY = "pregnancy"
private const val STEP_CHILD = "child"

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("🌙", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            AppCard {
                Feature("🤰", stringResource(R.string.welcome_feature_pregnancy))
                Feature("💉", stringResource(R.string.welcome_feature_vaccines))
                Feature("📈", stringResource(R.string.welcome_feature_growth))
                Feature("⭐", stringResource(R.string.welcome_feature_milestones))
                Feature("🔒", stringResource(R.string.welcome_feature_privacy))
            }
            AppCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(stringResource(R.string.disclaimer_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.bodyLarge)
            }
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.welcome_accept))
            }
        }
    }
}

@Composable
private fun Feature(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(R.string.choose_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                BigChoice("🤰", stringResource(R.string.choose_pregnant), stringResource(R.string.choose_pregnant_hint), onPregnant)
                BigChoice("👶", stringResource(R.string.choose_child), stringResource(R.string.choose_child_hint), onChild)
                AppCard(onClick = onRestore) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📂", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.choose_restore), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.choose_restore_hint), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    stringResource(R.string.choose_more_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BigChoice(emoji: String, title: String, hint: String, onClick: () -> Unit) {
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
