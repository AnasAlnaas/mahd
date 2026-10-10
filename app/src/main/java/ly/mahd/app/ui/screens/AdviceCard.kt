package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ly.mahd.app.MahdApp
import ly.mahd.app.R
import ly.mahd.app.content.AdviceItem
import ly.mahd.app.content.AdviceKind
import ly.mahd.app.content.adviceFor
import ly.mahd.app.data.Profile
import ly.mahd.app.logic.AdviceRules
import ly.mahd.app.ui.components.AppCard
import ly.mahd.app.ui.components.LocalParent
import ly.mahd.app.ui.components.LocalToday
import ly.mahd.app.ui.components.MoonPhase

/** Today's verse or hadith for this profile (a different one each day), or null when none fits. */
@Composable
fun rememberTodaysAdvice(app: MahdApp, profile: Profile): AdviceItem? {
    val today = LocalToday.current
    val parent = LocalParent.current
    return remember(profile, today, parent) { AdviceRules.forDay(app.content.advice.items, profile, today, parent) }
}

/**
 * A verse or hadith with its exact source, then a short word for the mother. A verse is set larger, between
 * the Qur'anic brackets; a hadith (usually longer) at body size.
 */
@Composable
fun AdviceCard(item: AdviceItem, modifier: Modifier = Modifier) {
    val quran = item.kind == AdviceKind.QURAN
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonPhase(0.3f, size = 16.dp, animate = false)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (quran) R.string.advice_title_quran else R.string.advice_title_hadith),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (quran) quranicBrackets(item.displayText) else AnnotatedString(item.displayText),
            style = if (quran) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)
            else MaterialTheme.typography.bodyLarge,
        )
        Text(item.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.adviceFor(LocalParent.current), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Readex Pro has no proper glyphs for the ornate Qur'anic brackets ﴿ ﴾ (it draws plain, wrong-facing parentheses)
 * nor for the mushaf's pause marks (ۖ ۚ ۗ …) and verse separator ۝, so those characters are drawn in the phone's
 * own Arabic font, which has them.
 */
private fun quranicBrackets(text: String): AnnotatedString = buildAnnotatedString {
    text.forEach { c ->
        val mushafSign = c == '﴿' || c == '﴾' || c in 'ۖ'..'ۭ'
        if (mushafSign) withStyle(SpanStyle(fontFamily = FontFamily.Default)) { append(c) } else append(c)
    }
}
