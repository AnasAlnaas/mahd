package ly.mahd.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A warm, calm palette: rose (care), teal (health), sand (warmth).
private val Light = lightColorScheme(
    primary = Color(0xFFA23B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF3F0019),
    secondary = Color(0xFF2F6F6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7EFE8),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF524346),
    outline = Color(0xFF847376),
    outlineVariant = Color(0xFFD6C2C5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFBEAEC),
    surfaceContainerHigh = Color(0xFFF5E4E6),
    surfaceContainerHighest = Color(0xFFEFDEE0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C2),
    onPrimary = Color(0xFF65002D),
    primaryContainer = Color(0xFF832443),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFF9BD3CC),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF14504C),
    onSecondaryContainer = Color(0xFFB7EFE8),
    tertiary = Color(0xFFF2BF48),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1113),
    onBackground = Color(0xFFF0DEE0),
    surface = Color(0xFF1A1113),
    onSurface = Color(0xFFF0DEE0),
    surfaceVariant = Color(0xFF524346),
    onSurfaceVariant = Color(0xFFD6C2C5),
    outline = Color(0xFF9F8C8F),
    outlineVariant = Color(0xFF524346),
    surfaceContainerLowest = Color(0xFF140C0E),
    surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF271D1F),
    surfaceContainerHigh = Color(0xFF322829),
    surfaceContainerHighest = Color(0xFF3D3234),
)

/** Extra colours for health states (normal / attention / warning). */
@Immutable
data class StatusColors(
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val chartBand: Color,
    val chartMedian: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF2E7D32),
    onGood = Color(0xFFFFFFFF),
    goodContainer = Color(0xFFD7F0D9),
    attention = Color(0xFFB45309),
    attentionContainer = Color(0xFFFFE8CC),
    chartBand = Color(0x332E7D32),
    chartMedian = Color(0xFF2E7D32),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF8BD18F),
    onGood = Color(0xFF00390A),
    goodContainer = Color(0xFF1F4D23),
    attention = Color(0xFFFFB870),
    attentionContainer = Color(0xFF5A3200),
    chartBand = Color(0x408BD18F),
    chartMedian = Color(0xFF8BD18F),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

// Slightly larger text than the Material defaults: easier to read for everyone, including grandmothers.
private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 28.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MahdTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors: ColorScheme = if (dark) Dark else Light
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** The four areas of the app. Each has its own accent colour so a card is recognised at a glance. */
enum class Area { VACCINES, GROWTH, MILESTONES, PREGNANCY }

@Composable
fun Area.accent(): Color = when (this) {
    Area.VACCINES, Area.PREGNANCY -> MaterialTheme.colorScheme.primary
    Area.GROWTH -> MaterialTheme.colorScheme.secondary
    Area.MILESTONES -> MaterialTheme.colorScheme.tertiary
}

/** Accent for a milestone domain (social, language, cognitive, movement). */
@Composable
fun domainAccent(domainId: String): Color = when (domainId) {
    "social" -> MaterialTheme.colorScheme.primary
    "language" -> MaterialTheme.colorScheme.secondary
    "cognitive" -> MaterialTheme.colorScheme.tertiary
    else -> LocalStatusColors.current.good
}
