package ly.mahd.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ly.mahd.app.content.GrowthIndicator
import ly.mahd.app.content.GrowthStandards
import ly.mahd.app.ui.theme.LocalStatusColors
import ly.mahd.app.util.Format
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A WHO growth chart drawn directly on a Canvas (no chart library).
 * Green band = the normal range (between −2 and +2 standard deviations, about 95 of every 100 children).
 * The dashed line in the middle is the average; the curves are labelled at their right end (+3, +2, 0, −2, −3).
 * Dots are the child's own measurements, and the latest one carries a small tag with its value.
 * Shows ages [minMonth]..[maxMonth] only, so the chart can zoom in on the recent months; a new range is reached
 * with a glide, not a jump.
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
    minMonth: Int,
    maxMonth: Int,
    /** Text for the tag on the latest point, e.g. "9.4". */
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
    val curveLabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val tagStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary)

    // The WHO curves every half month, over all the ages the chart can show, so it can glide between ranges.
    val curves = remember(indicator, sexCode) {
        val ages = (0..MAX_MONTH * 2).map { it / 2.0 }
        fun curve(z: Double) = ages.mapNotNull { a -> standards.lms(indicator, sexCode, a)?.let { a to it.valueAt(z) } }
        mapOf(-3.0 to curve(-3.0), -2.0 to curve(-2.0), 0.0 to curve(0.0), 2.0 to curve(2.0), 3.0 to curve(3.0))
    }
    val range = remember(curves, points, minMonth, maxMonth) { valueRange(curves, points, minMonth, maxMonth) } ?: return

    // Zooming in on the recent months (or out again) glides: both axes move to their new range together.
    val fromMonth by animateFloatAsState(minMonth.toFloat(), ZOOM, label = "fromMonth")
    val toMonth by animateFloatAsState(maxMonth.toFloat(), ZOOM, label = "toMonth")
    val fromValue by animateFloatAsState(range.min.toFloat(), ZOOM, label = "fromValue")
    val toValue by animateFloatAsState(range.max.toFloat(), ZOOM, label = "toValue")
    val xStep = when (maxMonth - minMonth) {
        in 0..6 -> 1
        in 7..12 -> 2
        in 13..24 -> 3
        else -> 6
    }

    Canvas(modifier.fillMaxWidth().height(280.dp)) {
        val leftPad = 44.dp.toPx()
        val bottomPad = 28.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 30.dp.toPx() // room for the curve labels
        val w = size.width - leftPad - rightPad
        val h = size.height - topPad - bottomPad
        val lo = fromMonth.toDouble()
        val hi = toMonth.toDouble().coerceAtLeast(lo + 0.5)
        val yMin = fromValue.toDouble()
        val yMax = toValue.toDouble().coerceAtLeast(yMin + 1e-6)

        val low = curves.getValue(-3.0)
        val high = curves.getValue(3.0)
        if (low.isEmpty() || high.isEmpty()) return@Canvas
        val visible = points.filter { it.first >= lo - 1e-6 && it.first <= hi + 1e-6 }.sortedBy { it.first }

        fun x(age: Double) = leftPad + ((age - lo) / (hi - lo) * w).toFloat()
        fun y(v: Double) = topPad + ((yMax - v) / (yMax - yMin) * h).toFloat()

        // Grid + Y labels, on the round values inside the range shown
        var v = ceil(yMin / range.step - 1e-6) * range.step
        while (v <= yMax + 1e-6) {
            drawLine(colors.outlineVariant, Offset(leftPad, y(v)), Offset(leftPad + w, y(v)), strokeWidth = 1f)
            val label = measurer.measure(Format.decimal(v), labelStyle)
            drawText(label, topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), y(v) - label.size.height / 2))
            v += range.step
        }
        // X labels
        var m = (ceil(lo / xStep - 1e-6) * xStep).toInt()
        while (m <= hi + 1e-6) {
            drawLine(colors.outlineVariant, Offset(x(m.toDouble()), topPad), Offset(x(m.toDouble()), topPad + h), strokeWidth = 1f)
            val label = measurer.measure(m.toString(), labelStyle)
            drawText(label, topLeft = Offset(x(m.toDouble()) - label.size.width / 2, topPad + h + 4.dp.toPx()))
            m += xStep
        }

        fun line(pointsList: List<Pair<Double, Double>>) = Path().apply {
            pointsList.forEachIndexed { i, (a, value) -> if (i == 0) moveTo(x(a), y(value)) else lineTo(x(a), y(value)) }
        }
        // The curves and the child's line are drawn whole and cut to the plot, so they slide in and out while zooming.
        clipRect(leftPad, topPad, leftPad + w, topPad + h) {
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

            val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            drawPath(line(curves.getValue(0.0)), status.chartMedian, style = Stroke(width = 2.dp.toPx(), pathEffect = dashed))
            drawPath(line(low), status.attention, style = Stroke(width = 1.dp.toPx(), pathEffect = dashed))
            drawPath(line(high), status.attention, style = Stroke(width = 1.dp.toPx(), pathEffect = dashed))

            val all = points.sortedBy { it.first }
            if (all.size > 1) drawPath(line(all), colors.primary, style = Stroke(width = 2.5.dp.toPx()))
        }

        // Curve labels at the right end: +3, +2, 0, −2, −3
        // A left-to-right mark (‎) keeps the sign in front of the number even in a right-to-left screen.
        listOf(3.0 to "‎+3", 2.0 to "‎+2", 0.0 to "0", -2.0 to "‎−2", -3.0 to "‎−3").forEach { (z, text) ->
            val curve = curves.getValue(z)
            val end = curve.lastOrNull { it.first <= hi + 1e-6 } ?: curve.first()
            val color = if (abs(z) == 3.0) status.attention else status.chartMedian
            val label = measurer.measure(text, curveLabelStyle.copy(color = color))
            drawText(label, topLeft = Offset(leftPad + w + 4.dp.toPx(), y(end.second) - label.size.height / 2))
        }

        // The child's measurements
        visible.forEach { (a, value) ->
            drawCircle(colors.surface, radius = 6.dp.toPx(), center = Offset(x(a), y(value)))
            drawCircle(colors.primary, radius = 4.5.dp.toPx(), center = Offset(x(a), y(value)))
        }

        // A small tag with the value of the latest measurement
        visible.lastOrNull()?.let { (a, value) ->
            val text = measurer.measure(valueLabel(value), tagStyle)
            val padX = 8.dp.toPx()
            val padY = 3.dp.toPx()
            val tagW = text.size.width + padX * 2
            val tagH = text.size.height + padY * 2
            val cx = x(a)
            val cy = y(value)
            val left = (cx - tagW / 2).coerceIn(leftPad, leftPad + w + rightPad - tagW)
            val above = cy - 12.dp.toPx() - tagH
            val top = if (above >= 0f) above else cy + 12.dp.toPx()
            drawRoundRect(colors.primary, Offset(left, top), Size(tagW, tagH), CornerRadius(tagH / 2))
            drawText(text, topLeft = Offset(left + padX, top + padY))
        }
    }
}

/** The oldest age on any chart (5 years). */
private const val MAX_MONTH = 60

/** How the chart glides to a new range: no bounce, so the axes never swing past their values. */
private val ZOOM = spring<Float>(dampingRatio = 1f, stiffness = 260f)

private class ValueRange(val min: Double, val max: Double, val step: Double)

/**
 * The values shown for the ages [minMonth]..[maxMonth], from round number to round number. Only the curves and the
 * measurements in that age range count, so an early value (such as the birth weight) does not squash the recent
 * months when the chart is zoomed in. Null when there are no curves for that range.
 */
private fun valueRange(
    curves: Map<Double, List<Pair<Double, Double>>>,
    points: List<Pair<Double, Double>>,
    minMonth: Int,
    maxMonth: Int,
): ValueRange? {
    fun inRange(age: Double) = age >= minMonth && age <= maxMonth
    val low = curves.getValue(-3.0).filter { inRange(it.first) }
    val high = curves.getValue(3.0).filter { inRange(it.first) }
    if (low.isEmpty() || high.isEmpty()) return null
    var yMin = low.minOf { it.second }
    var yMax = high.maxOf { it.second }
    points.filter { inRange(it.first) }.forEach { (_, v) -> yMin = min(yMin, v); yMax = max(yMax, v) }
    val step = niceStep((yMax - yMin) / 6)
    return ValueRange(floor(yMin / step) * step, ceil(yMax / step) * step, step)
}

/** 1, 2 or 5 × a power of ten — gives round numbers on the axis. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val exp = floor(log10(raw))
    val base = 10.0.pow(exp)
    val f = raw / base
    return when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    } * base
}
