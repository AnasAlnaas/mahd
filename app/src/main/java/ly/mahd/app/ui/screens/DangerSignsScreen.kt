package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.DangerSignSection
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.ChildAge
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.BulletList
import ly.mahd.app.ui.components.DraftNotice
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.SubScreen
import ly.mahd.app.ui.components.WarningCard

/** "Go to the hospital now if…" during the pregnancy — always one tap away. */
@Composable
fun DangerSignsScreen(app: MahdApp, onBack: () -> Unit) {
    val content = app.content.pregnancy
    DangerSignsList(
        title = stringResource(R.string.danger_signs_title),
        intro = stringResource(R.string.danger_signs_intro),
        sections = listOf(DangerSignSection(title = null, signs = content.dangerSigns)),
        reviewed = content.reviewed,
        onBack = onBack,
    )
}

/** "Take your child to the emergency room now if…". The section for the child's age comes first. */
@Composable
fun ChildDangerSignsScreen(app: MahdApp, child: Profile, onBack: () -> Unit) {
    val today = LocalToday.current
    val content = app.content.childDangerSigns
    val months = child.birthDate?.let { ChildAge.months(it, today) }
    // A newborn's mother sees "under 3 months" first; for an older child the file's order is kept ("any age" first).
    val sections = content.sections.sortedBy { s ->
        if (months != null && s.maxAgeMonths != null && months < s.maxAgeMonths) 0 else 1
    }
    DangerSignsList(
        title = stringResource(R.string.child_danger_signs_title),
        intro = stringResource(R.string.child_danger_signs_intro),
        sections = sections,
        reviewed = content.reviewed,
        onBack = onBack,
    )
}

@Composable
private fun DangerSignsList(
    title: String,
    intro: String,
    sections: List<DangerSignSection>,
    reviewed: Boolean,
    onBack: () -> Unit,
) {
    SubScreen(title = title, onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WarningCard(title = stringResource(R.string.danger_signs_intro_title)) {
                    Text(intro, style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(sections) { section ->
                AppCard {
                    section.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    BulletList(section.signs)
                }
            }
            item {
                Text(
                    stringResource(R.string.danger_signs_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!reviewed) item { DraftNotice() }
        }
    }
}
