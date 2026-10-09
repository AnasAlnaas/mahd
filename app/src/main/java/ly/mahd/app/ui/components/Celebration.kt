package ly.mahd.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ly.mahd.app.ui.theme.LocalStatusColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SPARKS = 16
private const val DURATION_MS = 900

/**
 * A small firework: a soft ring and a burst of sparks in the app's colours, from [origin] (in this canvas's
 * coordinates). Plays once each time [trigger] goes up; draws nothing the rest of the time.
 * Not clipped, so the sparks may fly a little outside the row they belong to.
 */
@Composable
fun SparkBurst(trigger: Int, origin: () -> Offset?, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(DURATION_MS, easing = LinearOutSlowInEasing))
        }
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        LocalStatusColors.current.good,
        MaterialTheme.colorScheme.secondary,
    )
    // The same sparks for the whole of one burst; a new pattern for the next one.
    val sparks = remember(trigger) {
        val random = Random(trigger * 7919 + 17)
        List(SPARKS) { i ->
            Spark(
                angle = (i * 2 * PI / SPARKS + random.nextDouble(-0.25, 0.25)).toFloat(),
                speed = random.nextDouble(0.75, 1.15).toFloat(),
                color = colors[i % colors.size],
                streak = i % 2 == 0,
            )
        }
    }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val o = origin() ?: return@Canvas
        val reach = 58.dp.toPx()
        val gravity = 26.dp.toPx()
        val fade = 1f - t * t

        // The ring: grows quickly and is gone by mid-way.
        if (t < 0.5f) {
            val ringT = t / 0.5f
            drawCircle(
                colors[2].copy(alpha = 0.45f * (1f - ringT)),
                radius = 10.dp.toPx() + 22.dp.toPx() * ringT,
                center = o,
                style = Stroke(2.dp.toPx() * (1f - ringT) + 0.5f),
            )
        }
        sparks.forEach { s ->
            val d = reach * s.speed * t
            val p = Offset(o.x + cos(s.angle) * d, o.y + sin(s.angle) * d + gravity * t * t)
            val c = s.color.copy(alpha = fade)
            if (s.streak) {
                val tail = 7.dp.toPx() * (1f - t)
                val q = Offset(p.x - cos(s.angle) * tail, p.y - sin(s.angle) * tail)
                drawLine(c, q, p, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawCircle(c, radius = 3.dp.toPx() * (1f - 0.5f * t), center = p)
            }
        }
    }
}

private class Spark(val angle: Float, val speed: Float, val color: Color, val streak: Boolean)
