package ly.mahd.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ly.mahd.app.R

// See DIRECTION.md. Rose is the one loud colour; teal belongs to growth, gold to milestones.
// Neutrals are warm ("moonlit paper") by day and a violet-tinted night sky by night.
private val Light = lightColorScheme(
    primary = Color(0xFFA23B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E1),
    onPrimaryContainer = Color(0xFF6E1B3A),
    secondary = Color(0xFF2F6F6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4EDE7),
    onSecondaryContainer = Color(0xFF0F4F4A),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE3B0),
    onTertiaryContainer = Color(0xFF4A3400),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF7A0F0F),
    background = Color(0xFFF6F1EE),
    onBackground = Color(0xFF1F1A1C),
    surface = Color(0xFFF6F1EE),
    onSurface = Color(0xFF1F1A1C),
    surfaceVariant = Color(0xFFEDE3E3),
    onSurfaceVariant = Color(0xFF5A5053),
    outline = Color(0xFF8C7F82),
    outlineVariant = Color(0xFFDCD2D3),
    inverseSurface = Color(0xFF352F31),
    inverseOnSurface = Color(0xFFF9EEF0),
    inversePrimary = Color(0xFFFFB1C3),
    surfaceTint = Color(0xFFA23B5B),
    surfaceBright = Color(0xFFFFFDFB),
    surfaceDim = Color(0xFFE2DBD8),
    surfaceContainerLowest = Color(0xFFFFFDFB),
    surfaceContainerLow = Color(0xFFFBF6F3),
    surfaceContainer = Color(0xFFF1EBE8),
    surfaceContainerHigh = Color(0xFFEBE4E1),
    surfaceContainerHighest = Color(0xFFE4DDDA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB1C3),
    onPrimary = Color(0xFF5E1131),
    primaryContainer = Color(0xFF7E2846),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFF9BD3CC),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF1C4F4A),
    onSecondaryContainer = Color(0xFFC4EDE7),
    tertiary = Color(0xFFF2BF48),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFE3B0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF55191C), // muted: a full red block is too loud at night
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF161218),
    onBackground = Color(0xFFEDE2E6),
    surface = Color(0xFF161218),
    onSurface = Color(0xFFEDE2E6),
    surfaceVariant = Color(0xFF3B3439),
    onSurfaceVariant = Color(0xFFD1C4C9),
    outline = Color(0xFF9A8D93),
    outlineVariant = Color(0xFF4A4247),
    inverseSurface = Color(0xFFEDE2E6),
    inverseOnSurface = Color(0xFF332D31),
    inversePrimary = Color(0xFFA23B5B),
    surfaceTint = Color(0xFFFFB1C3),
    surfaceBright = Color(0xFF3D363C),
    surfaceDim = Color(0xFF161218),
    surfaceContainerLowest = Color(0xFF110E13),
    surfaceContainerLow = Color(0xFF1E191F),
    surfaceContainer = Color(0xFF231E24),
    surfaceContainerHigh = Color(0xFF2E282F),
    surfaceContainerHighest = Color(0xFF39323A),
)

// The father's colours: a deep, calm blue in place of rose, and cool greys in place of the warm ones.
// Teal (growth), gold (milestones) and the health colours keep their meaning.
private val FatherLight = Light.copy(
    primary = Color(0xFF2C5A9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF123266),
    background = Color(0xFFF1F3F7),
    onBackground = Color(0xFF1A1C21),
    surface = Color(0xFFF1F3F7),
    onSurface = Color(0xFF1A1C21),
    surfaceVariant = Color(0xFFE0E4EC),
    onSurfaceVariant = Color(0xFF4A505C),
    outline = Color(0xFF7A808C),
    outlineVariant = Color(0xFFCDD2DB),
    inverseSurface = Color(0xFF2E3138),
    inverseOnSurface = Color(0xFFEEF0F6),
    inversePrimary = Color(0xFFAFC6FF),
    surfaceTint = Color(0xFF2C5A9E),
    surfaceBright = Color(0xFFFCFDFF),
    surfaceDim = Color(0xFFD9DCE3),
    surfaceContainerLowest = Color(0xFFFCFDFF),
    surfaceContainerLow = Color(0xFFF6F7FA),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = Color(0xFFDFE2E9),
)

private val FatherDark = Dark.copy(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF0F2F61),
    primaryContainer = Color(0xFF29467A),
    onPrimaryContainer = Color(0xFFD9E4FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E4EB),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E4EB),
    surfaceVariant = Color(0xFF363A43),
    onSurfaceVariant = Color(0xFFC3C7D1),
    outline = Color(0xFF8D919B),
    outlineVariant = Color(0xFF434750),
    inverseSurface = Color(0xFFE2E4EB),
    inverseOnSurface = Color(0xFF2E3138),
    inversePrimary = Color(0xFF2C5A9E),
    surfaceTint = Color(0xFFAFC6FF),
    surfaceBright = Color(0xFF373A41),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF282A31),
    surfaceContainerHighest = Color(0xFF33353C),
)

/** Extra colours for health states (normal / attention / warning). */
@Immutable
data class StatusColors(
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val chartBand: Color,
    val chartMedian: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF2E7D32),
    onGood = Color(0xFFFFFFFF),
    goodContainer = Color(0xFFD7F0D9),
    onGoodContainer = Color(0xFF14501A),
    attention = Color(0xFFB45309),
    attentionContainer = Color(0xFFFFE8CC),
    onAttentionContainer = Color(0xFF6B3000),
    chartBand = Color(0x332E7D32),
    chartMedian = Color(0xFF2E7D32),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF8BD18F),
    onGood = Color(0xFF00390A),
    goodContainer = Color(0xFF1F4D23),
    onGoodContainer = Color(0xFFC8EFCA),
    attention = Color(0xFFFFB870),
    attentionContainer = Color(0xFF5A3200),
    onAttentionContainer = Color(0xFFFFDDB8),
    chartBand = Color(0x408BD18F),
    chartMedian = Color(0xFF8BD18F),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Surface roles the Material scheme has no name for. */
@Immutable
data class SurfaceRoles(
    /** Rows of a grouped list: lighter than the page by day, raised from the night sky by night. */
    val groupRow: Color,
    /** Controls placed on a group row (unselected answer buttons, the chart range). */
    val inset: Color,
    /** The unlit part of every moon. */
    val moonShadow: Color,
)

private val LightSurfaces = SurfaceRoles(groupRow = Color(0xFFFFFDFB), inset = Color(0xFFF1EBE8), moonShadow = Color(0xFFE6DCDC))
private val DarkSurfaces = SurfaceRoles(groupRow = Color(0xFF241F26), inset = Color(0xFF3A333B), moonShadow = Color(0xFF3E363E))
private val FatherLightSurfaces = SurfaceRoles(groupRow = Color(0xFFFCFDFF), inset = Color(0xFFEBEEF3), moonShadow = Color(0xFFDCE0E8))
private val FatherDarkSurfaces = SurfaceRoles(groupRow = Color(0xFF1F2229), inset = Color(0xFF353943), moonShadow = Color(0xFF383C45))

val LocalSurfaces = staticCompositionLocalOf { LightSurfaces }

// ---------- Type ----------

/** Readex Pro, one variable font file; each weight is an instance of its "wght" axis. */
private val Readex = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(
            R.font.readex_pro,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

/**
 * Arabic needs no letter-spacing (it breaks the joins) and about 1.6× line height. The extra height is kept above
 * the first line and below the last one too (no trimming), because Arabic descenders reach deep below the baseline.
 */
private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = Readex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = TextUnit.Unspecified,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

// Larger than the Material defaults: easier to read for everyone, including grandmothers.
private val AppTypography = Typography(
    displayLarge = style(52, 76, FontWeight.SemiBold),
    displayMedium = style(42, 62, FontWeight.SemiBold),
    displaySmall = style(34, 52, FontWeight.SemiBold),
    headlineLarge = style(30, 46, FontWeight.SemiBold),
    headlineMedium = style(26, 40, FontWeight.SemiBold),
    headlineSmall = style(22, 34, FontWeight.SemiBold),
    titleLarge = style(20, 32, FontWeight.SemiBold),
    titleMedium = style(17, 28, FontWeight.Medium),
    titleSmall = style(15, 24, FontWeight.Medium),
    bodyLarge = style(17, 28, FontWeight.Normal),
    bodyMedium = style(15, 24, FontWeight.Normal),
    bodySmall = style(13, 20, FontWeight.Normal),
    labelLarge = style(15, 22, FontWeight.Medium),
    labelMedium = style(13, 20, FontWeight.Medium),
    labelSmall = style(12, 18, FontWeight.Medium),
)

// ---------- Shape ----------

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Shapes the Material set does not name (see DIRECTION.md, "Space & form"). */
object AppShape {
    /** Outer corners of a grouped list. */
    val group = RoundedCornerShape(24.dp)
    /** Corners between rows inside a group. */
    val groupInner = RoundedCornerShape(6.dp)
    /** Danger signs, reminders prompt: the few surfaces that must stand out. */
    val featured = RoundedCornerShape(28.dp)
}

/**
 * Material 3 Expressive's motion: springs with a little bounce for things that move, no bounce for fades.
 * Same values as Material's own expressive scheme, which material3 1.4 keeps internal; when 1.5 is stable,
 * MahdTheme can switch to MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) and these can go.
 */
object AppMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

@Composable
fun MahdTheme(dark: Boolean = isSystemInDarkTheme(), father: Boolean = false, content: @Composable () -> Unit) {
    val colors: ColorScheme = when {
        father -> if (dark) FatherDark else FatherLight
        else -> if (dark) Dark else Light
    }
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalSurfaces provides when {
            father -> if (dark) FatherDarkSurfaces else FatherLightSurfaces
            else -> if (dark) DarkSurfaces else LightSurfaces
        },
    ) {
        MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography, content = content)
    }
}

/** The four areas of the app. Each has its own colour, used for its moon and little else. */
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
