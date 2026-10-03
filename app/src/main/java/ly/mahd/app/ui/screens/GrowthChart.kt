package ly.mahd.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.content.GrowthStandards
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.util.Format
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A WHO growth chart drawn directly on a Canvas (no chart library).
 * Green band = the normal range (between −2 and +2 standard deviations, about 95 of every 100 children).
 * The line in the middle is the average. Dots are the child's own measurements.
 *
 * Charts read left-to-right (age increasing to the right) even in Arabic, like the printed WHO charts.
 */
@Composable
fun GrowthChart(
    standards: GrowthStandards,
    indicator: GrowthIndicator,
    sexCode: Int,
    /** (age in months, measured value) */
    points: List<Pair<Double, Double>>,
    maxMonth: Int,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)

    // Sample the WHO curves every half month.
    val curves = remember(indicator, sexCode, maxMonth) {
        val ages = (0..maxMonth * 2).map { it / 2.0 }
        fun curve(z: Double) = ages.mapNotNull { a -> standards.lms(indicator, sexCode, a)?.let { a to it.valueAt(z) } }
        mapOf(-3.0 to curve(-3.0), -2.0 to curve(-2.0), 0.0 to curve(0.0), 2.0 to curve(2.0), 3.0 to curve(3.0))
    }

    Canvas(modifier.fillMaxWidth().height(280.dp)) {
        val leftPad = 44.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad

        val low = curves.getValue(-3.0)
        val high = curves.getValue(3.0)
        if (low.isEmpty() || high.isEmpty()) return@Canvas
        var yMin = low.minOf { it.second }
        var yMax = high.maxOf { it.second }
        points.forEach { (_, v) -> yMin = min(yMin, v); yMax = max(yMax, v) }
        val step = niceStep((yMax - yMin) / 6)
        yMin = floor(yMin / step) * step
        yMax = ceil(yMax / step) * step

        fun x(age: Double) = leftPad + (age / maxMonth * w).toFloat()
        fun y(v: Double) = topPad + ((yMax - v) / (yMax - yMin) * h).toFloat()

        // Grid + Y labels
        var v = yMin
        while (v <= yMax + 1e-9) {
            drawLine(colors.outlineVariant, Offset(leftPad, y(v)), Offset(leftPad + w, y(v)), strokeWidth = 1f)
            val label = measurer.measure(Format.decimal(v), labelStyle)
            drawText(label, topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), y(v) - label.size.height / 2))
            v += step
        }
        // X labels
        val xStep = when {
            maxMonth <= 6 -> 1
            maxMonth <= 12 -> 2
            maxMonth <= 24 -> 3
            else -> 6
        }
        for (m in 0..maxMonth step xStep) {
            drawLine(colors.outlineVariant, Offset(x(m.toDouble()), topPad), Offset(x(m.toDouble()), topPad + h), strokeWidth = 1f)
            val label = measurer.measure(m.toString(), labelStyle)
            drawText(label, topLeft = Offset(x(m.toDouble()) - label.size.width / 2, topPad + h + 4.dp.toPx()))
        }

        // Normal band (−2..+2)
        val lower = curves.getValue(-2.0)
        val upper = curves.getValue(2.0)
        val band = Path().apply {
            moveTo(x(upper.first().first), y(upper.first().second))
            upper.forEach { (a, value) -> lineTo(x(a), y(value)) }
            lower.asReversed().forEach { (a, value) -> lineTo(x(a), y(value)) }
            close()
        }
        drawPath(band, status.chartBand)

        fun line(pointsList: List<Pair<Double, Double>>) = Path().apply {
            pointsList.forEachIndexed { i, (a, value) -> if (i == 0) moveTo(x(a), y(value)) else lineTo(x(a), y(value)) }
        }
        val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        drawPath(line(curves.getValue(0.0)), status.chartMedian, style = Stroke(width = 2.dp.toPx(), pathEffect = dashed))
        drawPath(line(low), status.attention, style = Stroke(width = 1.dp.toPx(), pathEffect = dashed))
        drawPath(line(high), status.attention, style = Stroke(width = 1.dp.toPx(), pathEffect = dashed))

        // The child's measurements
        val visible = points.filter { it.first in 0.0..maxMonth.toDouble() }.sortedBy { it.first }
        if (visible.size > 1) drawPath(line(visible), colors.primary, style = Stroke(width = 2.5.dp.toPx()))
        visible.forEach { (a, value) ->
            drawCircle(colors.surface, radius = 6.dp.toPx(), center = Offset(x(a), y(value)))
            drawCircle(colors.primary, radius = 4.5.dp.toPx(), center = Offset(x(a), y(value)))
        }
    }
}

/** 1, 2 or 5 × a power of ten — gives round numbers on the axis. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val exp = floor(kotlin.math.log10(raw))
    val base = Math.pow(10.0, exp)
    val f = raw / base
    return when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    } * base
}
