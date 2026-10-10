package ly.mahd.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ly.mahd.app.R
import ly.mahd.app.data.Parent
import ly.mahd.app.reminders.Notifications
import ly.mahd.app.ui.theme.AppMotion
import ly.mahd.app.ui.theme.AppShape
import ly.mahd.app.ui.theme.LocalSurfaces
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

/** Who uses the app: the mother or the father. Chosen once; the screen restarts when it changes (MainActivity). */
val LocalParent = staticCompositionLocalOf { Parent.MOTHER }

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

/** A screen opened on top of the tabs: a large title that shrinks into the bar as the page scrolls. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(R.drawable.ic_back, stringResource(R.string.action_back))
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scroll,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------------------------

/**
 * A grouped list (Android 16 style): rows sit 2dp apart, the group has large outer corners and the rows small
 * inner ones. Put [GroupRow]s or [GroupItem]s inside.
 */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(AppShape.group),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** One row of a [ListGroup], free-form. */
@Composable
fun GroupRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = LocalSurfaces.current.groupRow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    spacing: Dp = 8.dp,
    shape: Shape = AppShape.groupInner,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = container, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = container, content = inner)
    }
}

/** A standard row of a [ListGroup]: optional leading picture, a headline, a supporting line, and a trailing part. */
@Composable
fun GroupItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    GroupRow(modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                overline?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(headline, style = MaterialTheme.typography.titleMedium)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = supportingColor) }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                AppIcon(R.drawable.ic_chevron_forward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A single free-standing block (a group of one). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalSurfaces.current.groupRow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = AppShape.group, color = containerColor, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = AppShape.group, color = containerColor, content = inner)
    }
}

/** The few surfaces that must stand out (danger signs, the reminders prompt): tinted, rounder, no border. */
@Composable
fun FeaturedCard(
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = AppShape.featured, color = container, contentColor = contentColor, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = AppShape.featured, color = container, contentColor = contentColor, content = inner)
    }
}

/** "Go to the hospital now" information: the error container, a warning mark, and an arrow when it opens a page. */
@Composable
fun WarningCard(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    FeaturedCard(colors.errorContainer, colors.onErrorContainer, modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_warning, null, Modifier.size(24.dp), tint = colors.error)
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (onClick != null) AppIcon(R.drawable.ic_chevron_forward, null)
        }
        content()
    }
}

/** Shown on medical content until a doctor has reviewed it. */
@Composable
fun DraftNotice(text: String = stringResource(R.string.draft_notice), modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            AppIcon(R.drawable.ic_info, null, Modifier.padding(top = 3.dp).size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The small heading above a group. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun BulletList(items: List<String>, modifier: Modifier = Modifier, bullet: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row {
                // Centred on the first line of text (bodyLarge is 28sp tall).
                Box(Modifier.padding(top = 11.dp).size(6.dp).background(bullet, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** A small rounded label, e.g. "تم" or "متأخر". */
@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = container, contentColor = content, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 1.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Centred message for empty lists: a new moon (nothing yet) above the text. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MoonPhase(0.12f, size = 56.dp, lit = MaterialTheme.colorScheme.outline)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons and choices
// ---------------------------------------------------------------------------------------------

/** A pill that squashes to rounder-square corners while pressed (Material 3 Expressive). */
@Composable
private fun rememberSquishShape(source: MutableInteractionSource): Shape {
    val pressed by source.collectIsPressedAsState()
    val percent by animateFloatAsState(
        if (pressed) 22f else 50f,
        AppMotion.fastSpatial(),
        label = "squish",
    )
    return RoundedCornerShape(CornerSize(percent))
}

/** The main action of a screen. [tonal] for a secondary action. */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    @DrawableRes icon: Int? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = rememberSquishShape(source)
    val padding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    val inner: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            AppIcon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
    val sized = modifier.heightIn(min = 52.dp)
    if (tonal) {
        // Tonal buttons are rose too: teal and gold belong to growth and milestones.
        val tonalColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        FilledTonalButton(onClick, sized, enabled, shape = shape, colors = tonalColors, contentPadding = padding, interactionSource = source, content = inner)
    } else {
        Button(onClick, sized, enabled, shape = shape, contentPadding = padding, interactionSource = source, content = inner)
    }
}

/**
 * A connected button group for picking one of a few options: pill ends, small corners between the segments,
 * and the selected segment rounds out into a full pill. [selected] may be null (nothing chosen yet).
 */
@Composable
fun ConnectedChoice(
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    unselectedColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedColors: @Composable (Int) -> Pair<Color, Color> = {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    },
    /** Extra modifier for segment i, e.g. to know where it is on screen. */
    segmentModifier: (Int) -> Modifier = { Modifier },
) {
    val spec = AppMotion.fastSpatial<Dp>()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            val outer = height / 2
            val inner = 8.dp
            val start by animateDpAsState(if (isSelected || i == 0) outer else inner, spec, label = "start")
            val end by animateDpAsState(if (isSelected || i == options.lastIndex) outer else inner, spec, label = "end")
            val (on, onContent) = selectedColors(i)
            val bg by animateColorAsState(if (isSelected) on else unselectedColor, label = "bg")
            val fg = if (isSelected) onContent else MaterialTheme.colorScheme.onSurface
            Surface(
                selected = isSelected,
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f).height(height).then(segmentModifier(i)),
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = bg,
                contentColor = fg,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
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

/** A tappable field that shows a date and opens the calendar. Looks like the text fields beside it. */
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
    OutlinedCard(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_calendar, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Keeps a date inside [min]..[max]. (Kotlin's coerceIn does not work directly on LocalDate.) */
fun LocalDate.clamp(min: LocalDate, max: LocalDate): LocalDate = when {
    isBefore(min) -> min
    isAfter(max) -> max
    else -> this
}

/** Filter chips are pills, and rose when selected (teal and gold belong to growth and milestones). */
@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
