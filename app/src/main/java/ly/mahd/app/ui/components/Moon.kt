package ly.mahd.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ly.mahd.app.R
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.ui.theme.LocalSurfaces
import kotlin.math.abs

/**
 * The app's signature (أطوار = stages, like the launcher icon): a moon whose lit part grows with [fraction]
 * (0..1) from a thin crescent to a full disc. It is lit from the start edge, which in Arabic is the right,
 * the side a waxing moon is lit on. It fills with a spring when it first appears and whenever the value changes.
 */
@Composable
fun MoonPhase(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    lit: Color = MaterialTheme.colorScheme.primary,
    shadow: Color = LocalSurfaces.current.moonShadow,
    animate: Boolean = true,
) {
    val target = fraction.coerceIn(0f, 1f)
    val fill = remember { Animatable(if (animate) 0f else target) }
    val spec = AppMotion.slowSpatial<Float>()
    LaunchedEffect(target) { fill.animateTo(target, spec) }
    Canvas(modifier.size(size)) {
        drawMoon(fill.value.coerceIn(0f, 1f), lit, shadow) // read while drawing: only the moon redraws
    }
}

private fun DrawScope.drawMoon(f: Float, lit: Color, shadow: Color) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(shadow, r, c)
    // A faint rim in the lit colour, so even a new moon (nothing yet) says which area it belongs to.
    val rim = (r * 0.05f).coerceIn(1.dp.toPx(), 2.dp.toPx())
    drawCircle(lit.copy(alpha = 0.35f), r - rim / 2, c, style = Stroke(rim))
    if (f <= 0.005f) return
    if (f >= 0.995f) {
        drawCircle(lit, r, c)
        return
    }
    // Lit area = the half disc on the lit side, minus (before half) or plus (after half) a half ellipse whose
    // width is r·|1−2f|. Its area is then exactly f of the disc.
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        arcTo(Rect(c, r), startAngleDegrees = -90f, sweepAngleDegrees = 180f, forceMoveTo = false)
        val rx = r * abs(1 - 2 * f)
        if (rx < 0.5f) {
            lineTo(c.x, c.y - r)
        } else {
            val terminator = Rect(Offset(c.x - rx, c.y - r), Offset(c.x + rx, c.y + r))
            arcTo(terminator, startAngleDegrees = 90f, sweepAngleDegrees = if (f < 0.5f) -180f else 180f, forceMoveTo = false)
        }
        close()
    }
    // Drawn lit-on-the-right; mirrored for a left-to-right layout so it always grows from the start edge.
    if (layoutDirection == LayoutDirection.Rtl) {
        drawPath(path, lit)
    } else {
        scale(-1f, 1f, pivot = c) { drawPath(path, lit) }
    }
}

private val TRIMESTER_WEEKS = listOf(0f to 14f, 14f to 28f, 28f to 40f) // first, second, third (to week 40)

/**
 * The pregnancy as three moons, one per trimester (the launcher icon's three moons). Each fills during its own
 * trimester, so week 20 shows a full moon, a half-lit moon and a dark one.
 */
@Composable
fun TrimesterMoons(weeks: Float, modifier: Modifier = Modifier, moonSize: Dp = 36.dp) {
    val labels = listOf(R.string.trimester_short_1, R.string.trimester_short_2, R.string.trimester_short_3)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TRIMESTER_WEEKS.forEachIndexed { i, (start, end) ->
            val f = ((weeks - start) / (end - start)).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MoonPhase(f, size = moonSize)
                Text(
                    stringResource(labels[i]),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (f > 0f && f < 1f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
