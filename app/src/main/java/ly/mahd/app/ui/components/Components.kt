package ly.mahd.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ly.mahd.app.R
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Today's date. Provided by MainActivity and refreshed whenever the app comes back to the screen. */
val LocalToday = compositionLocalOf<LocalDate> { LocalDate.now() }

/**
 * Goes up each time the app comes back to the screen (provided by MainActivity), so anything the mother
 * may have changed in the phone's settings meanwhile, such as notifications, is checked again.
 */
val LocalResumeCount = compositionLocalOf { 0 }

/** Whether reminders can be shown right now; checked again whenever the app comes back to the screen. */
@Composable
fun rememberCanPostNotifications(): Boolean {
    val context = LocalContext.current
    val resumes = LocalResumeCount.current
    return remember(resumes) { Notifications.canPost(context) }
}

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    if (tint == Color.Unspecified) {
        Icon(painterResource(id), contentDescription, modifier)
    } else {
        Icon(painterResource(id), contentDescription, modifier, tint = tint)
    }
}

/** A screen opened on top of the tabs, with a back arrow. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(R.drawable.ic_back, stringResource(R.string.action_back))
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    /** A thin coloured bar on the start edge, to tell the areas of the app apart. */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val inner: @Composable ColumnScope.() -> Unit = {
        val accentBar = if (accent == null) Modifier else Modifier.drawBehind {
            val barWidth = 5.dp.toPx()
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - barWidth else 0f
            drawRect(accent, Offset(left, 0f), Size(barWidth, size.height))
        }
        Column(accentBar.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    }
}

/** Shown on medical content until a doctor has reviewed it. */
@Composable
fun DraftNotice(text: String = stringResource(R.string.draft_notice), modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_info, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A red-outlined card for "go to the hospital now" information. */
@Composable
fun WarningCard(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_warning, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            content()
        }
    }
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), border = border, content = inner)
    } else {
        OutlinedCard(modifier = modifier.fillMaxWidth(), border = border, content = inner)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun BulletList(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row {
                Text("•  ", style = MaterialTheme.typography.bodyLarge)
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** A small rounded label, e.g. "تم" or "متأخر". */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------- Dates ----------

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** A calendar popup limited to [min]..[max]. */
@Composable
fun DatePickerPopup(
    initial: LocalDate?,
    min: LocalDate,
    max: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // A birth date "in the future" (entered while the phone's clock was wrong) would leave no valid range,
    // and the calendar does not accept an empty one.
    val end = if (max.isBefore(min)) min else max
    val selectable = remember(min, end) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcTimeMillis.toLocalDateUtc()
                return !d.isBefore(min) && !d.isAfter(end)
            }

            override fun isSelectableYear(year: Int): Boolean = year in min.year..end.year
        }
    }
    val start = initial?.clamp(min, end)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = start?.toUtcMillis(),
        initialDisplayedMonthMillis = (start ?: LocalDate.now().clamp(min, end)).toUtcMillis(),
        yearRange = min.year..end.year,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.toLocalDateUtc()) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** A tappable field that shows a date and opens the calendar. */
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    min: LocalDate,
    max: LocalDate,
    onDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { open = true }, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_calendar, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    date?.let(Format::date) ?: stringResource(R.string.choose_date),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    if (open) {
        DatePickerPopup(initial = date, min = min, max = max, onDismiss = { open = false }, onPick = onDate)
    }
}

/** Centered message for empty lists. */
@Composable
fun EmptyState(emoji: String, text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, style = MaterialTheme.typography.displaySmall)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Keeps a date inside [min]..[max]. (Kotlin's coerceIn does not work directly on LocalDate.) */
fun LocalDate.clamp(min: LocalDate, max: LocalDate): LocalDate = when {
    isBefore(min) -> min
    isAfter(max) -> max
    else -> this
}
