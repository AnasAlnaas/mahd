package ly.mahd.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ly.mahd.app.R

private val TRIMESTER_WEEKS = listOf(14f, 14f, 12f) // first, second, third (to week 40)

/**
 * The pregnancy as three segments (the three trimesters), filled up to [weeks]. It fills from the
 * start edge, so in Arabic it grows from right to left.
 */
@Composable
fun TrimesterBar(
    weeks: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animated by animateFloatAsState(weeks.coerceIn(0f, 40f), tween(900), label = "trimester-bar")
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val gap = 6.dp.toPx()
            val usable = size.width - gap * (TRIMESTER_WEEKS.size - 1)
            val total = TRIMESTER_WEEKS.sum()
            val corner = CornerRadius(size.height / 2)
            val flip = layoutDirection == LayoutDirection.Rtl
            withTransform({ if (flip) scale(-1f, 1f, pivot = center) }) {
                var x = 0f
                var start = 0f
                TRIMESTER_WEEKS.forEach { len ->
                    val width = usable * len / total
                    drawRoundRect(track, Offset(x, 0f), Size(width, size.height), corner)
                    val filled = ((animated - start) / len).coerceIn(0f, 1f)
                    if (filled > 0f) {
                        val w = (width * filled).coerceAtLeast(size.height)
                        drawRoundRect(color, Offset(x, 0f), Size(w, size.height), corner)
                    }
                    x += width + gap
                    start += len
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(R.string.trimester_short_1, R.string.trimester_short_2, R.string.trimester_short_3)
                .forEachIndexed { i, label ->
                    Text(
                        stringResource(label),
                        modifier = Modifier.weight(TRIMESTER_WEEKS[i]),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}

/** An emoji on a soft round background: used for "your baby is the size of…" and similar. */
@Composable
fun EmojiBadge(emoji: String, size: Dp, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
    Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.55f).sp)
    }
}
