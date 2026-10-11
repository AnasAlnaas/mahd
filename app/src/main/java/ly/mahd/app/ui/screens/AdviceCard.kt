package ly.mahd.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/** Today's item and why it was chosen (an occasion, season or family code; null for a year-round item). */
data class TodaysAdvice(val item: AdviceItem, val reason: String?)

/**
 * Today's verse, hadith or fact for this profile (a different one each day), chosen with the season, the occasion
 * and the whole [family] on the phone in mind; null when none fits.
 */
@Composable
fun rememberTodaysAdvice(app: MahdApp, profile: Profile, family: List<Profile>): TodaysAdvice? {
    val today = LocalToday.current
    val parent = LocalParent.current
    val hijriOffset by app.prefs.hijriOffset.collectAsState() // Ramadan and the Eids move with it
    return remember(profile, family, today, parent, hijriOffset) {
        AdviceRules.forDay(app.content.advice.items, profile, today, parent, family)?.let {
            TodaysAdvice(it, AdviceRules.reason(it, profile, today, family))
        }
    }
}

/** The label of an occasion, season or family situation code (see [AdviceRules.reason]). */
fun adviceReasonLabel(code: String?): Int? = when (code) {
    "ramadan" -> R.string.advice_reason_ramadan
    "ramadan_last_ten" -> R.string.advice_reason_ramadan_last_ten
    "eid_fitr" -> R.string.advice_reason_eid_fitr
    "dhul_hijjah_ten" -> R.string.advice_reason_dhul_hijjah_ten
    "arafah" -> R.string.advice_reason_arafah
    "eid_adha" -> R.string.advice_reason_eid_adha
    "celebration" -> R.string.advice_reason_celebration
    "winter" -> R.string.advice_reason_winter
    "spring" -> R.string.advice_reason_spring
    "summer" -> R.string.advice_reason_summer
    "autumn" -> R.string.advice_reason_autumn
    "siblings" -> R.string.advice_reason_siblings
    "expecting" -> R.string.advice_reason_expecting
    "twins" -> R.string.advice_reason_twins
    "preterm" -> R.string.advice_reason_preterm
    else -> null
}

/**
 * A verse, hadith or science fact with its exact source, then a short word for the mother. A verse is set larger,
 * between the Qur'anic brackets; a hadith or fact (usually longer) at body size. A fact is medical content, so it
 * carries the draft line until a doctor has reviewed the facts file. The heading says why a seasonal, occasion or
 * family item is shown ("في الصيف", "رمضان كريم").
 */
@Composable
fun AdviceCard(advice: TodaysAdvice, factsReviewed: Boolean, modifier: Modifier = Modifier) {
    val item = advice.item
    val quran = item.kind == AdviceKind.QURAN
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonPhase(0.3f, size = 16.dp, animate = false)
            Spacer(Modifier.width(8.dp))
            val kind = stringResource(
                when (item.kind) {
                    AdviceKind.QURAN -> R.string.advice_title_quran
                    AdviceKind.HADITH -> R.string.advice_title_hadith
                    AdviceKind.FACT -> R.string.advice_title_fact
                },
            )
            val reason = adviceReasonLabel(advice.reason)?.let { stringResource(it) }
            Text(
                if (reason != null) "$kind · $reason" else kind,
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
        if (item.kind == AdviceKind.FACT && !factsReviewed) {
            Text(stringResource(R.string.draft_notice), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
