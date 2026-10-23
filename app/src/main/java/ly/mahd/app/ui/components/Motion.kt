package ly.mahd.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ly.mahd.app.ui.theme.AppMotion

// Everything here answers something the mother did or opened: a screen coming or going, a number changing, a finger
// on a row. Nothing loops. With the phone's animations turned off, Compose finishes each of these at once.

/** Material's "emphasized decelerate": quick at first, then settling gently. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

// ---------------------------------------------------------------------------------------------
// Screens
// ---------------------------------------------------------------------------------------------

/** How long going back takes. Linear, so a back swipe moves the screen evenly under the finger. */
private const val CLOSE_SCREEN_MS = 400

/**
 * The progress of going back when it runs by itself (the back button, or after a swipe is let go): the linear
 * [closeScreen] animations are driven along this spring, which gives them their ease.
 */
val CloseScreenProgress: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 300f)

/**
 * Opening a screen on top: it comes in from the end side (the left in Arabic, where "next" lies) and fades in,
 * while the screen under it drifts a little the same way and fades.
 */
fun <S> AnimatedContentTransitionScope<S>.openScreen(): ContentTransform =
    (slideIntoContainer(SlideDirection.Start, AppMotion.defaultSpatial(), initialOffset = { it / 4 }) +
        fadeIn(AppMotion.slowEffects())) togetherWith
        (slideOutOfContainer(SlideDirection.Start, AppMotion.defaultSpatial(), targetOffset = { it / 12 }) +
            fadeOut(AppMotion.defaultEffects()))

/**
 * Going back: the screen on top shrinks a little and slides away [towards] the end (or the way a back swipe's finger
 * moves), above the screen it uncovers, which settles back into place. Driven by the back swipe while the finger is
 * down (see [CloseScreenProgress]).
 */
fun <S> AnimatedContentTransitionScope<S>.closeScreen(towards: SlideDirection = SlideDirection.End): ContentTransform {
    val slide = tween<IntOffset>(CLOSE_SCREEN_MS, easing = LinearEasing)
    return ContentTransform(
        targetContentEnter = slideIntoContainer(towards, slide, initialOffset = { it / 12 }) +
            fadeIn(tween(CLOSE_SCREEN_MS / 2, easing = LinearEasing)),
        initialContentExit = slideOutOfContainer(towards, slide, targetOffset = { it / 3 }) +
            scaleOut(tween(CLOSE_SCREEN_MS, easing = LinearEasing), targetScale = 0.9f) +
            fadeOut(tween(CLOSE_SCREEN_MS / 2, delayMillis = CLOSE_SCREEN_MS / 2, easing = LinearEasing)),
        // The screen going away stays on top of the one it uncovers.
        targetContentZIndex = -1f,
    )
}

/** Material's fade through, for the bottom tabs: the old page fades out, the new one fades in and grows into place. */
fun <S> AnimatedContentTransitionScope<S>.fadeThrough(): ContentTransform =
    (fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(320, delayMillis = 90, easing = EmphasizedDecelerate), initialScale = 0.95f)) togetherWith
        fadeOut(tween(90, easing = FastOutLinearInEasing))

/**
 * Material's shared axis, for moving between neighbours (pregnancy / after the birth, skills / growth, the steps of
 * the first run): the new content comes a short way from the side it lies on, the old one leaves the other way.
 * [forward] = towards the end side (the left in Arabic).
 */
fun <S> AnimatedContentTransitionScope<S>.sharedAxisX(forward: Boolean): ContentTransform {
    val towards = if (forward) SlideDirection.Start else SlideDirection.End
    return (slideIntoContainer(towards, AppMotion.defaultSpatial(), initialOffset = { it / 6 }) +
        fadeIn(tween(210, delayMillis = 60, easing = LinearOutSlowInEasing))) togetherWith
        (slideOutOfContainer(towards, AppMotion.defaultSpatial(), targetOffset = { it / 6 }) +
            fadeOut(tween(90, easing = FastOutLinearInEasing)))
}

/** Something appearing in a column: it unfolds downwards while fading in, and the things below make room. */
fun unfold(): EnterTransition =
    expandVertically(AppMotion.defaultSpatial(), expandFrom = Alignment.Top) + fadeIn(AppMotion.slowEffects())

/** The way back of [unfold]. */
fun fold(): ExitTransition =
    shrinkVertically(AppMotion.defaultSpatial(), shrinkTowards = Alignment.Top) + fadeOut(AppMotion.defaultEffects())

/** A soft cross-fade whose box grows or shrinks to the new content (folding and unfolding blocks). */
fun <S> AnimatedContentTransitionScope<S>.fadeAndResize(): ContentTransform =
    (fadeIn(tween(220, delayMillis = 60, easing = LinearOutSlowInEasing)) togetherWith
        fadeOut(tween(90, easing = FastOutLinearInEasing))) using
        SizeTransform { _, _ -> AppMotion.defaultSpatial() }

// ---------------------------------------------------------------------------------------------
// Numbers
// ---------------------------------------------------------------------------------------------

/**
 * A short text with a number in it ("13 من 19", "الأسبوع 20") that rolls to its new value like a counter: up when
 * the number grows, down when it shrinks.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val up = (firstNumber(targetState) ?: 0.0) >= (firstNumber(initialState) ?: 0.0)
            val from = if (up) 1 else -1
            (slideInVertically(AppMotion.fastSpatial()) { from * it / 2 } + fadeIn(AppMotion.defaultEffects())) togetherWith
                (slideOutVertically(AppMotion.fastSpatial()) { -from * it / 2 } + fadeOut(AppMotion.fastEffects())) using
                SizeTransform(clip = false)
        },
        label = "rolling",
    ) { Text(it, style = style, color = color) }
}

private val NUMBER = Regex("""\d+([.,٫]\d+)?""")

private fun firstNumber(text: String): Double? = NUMBER.find(text)?.value?.replace('٫', '.')?.replace(',', '.')?.toDoubleOrNull()

// ---------------------------------------------------------------------------------------------
// Touch
// ---------------------------------------------------------------------------------------------

/**
 * [base] with its corners rounding out towards [pressedCorner] while [source] is pressed, then springing back:
 * the rows' version of the buttons' squash (Material 3 Expressive). Corners already rounder stay as they are.
 */
@Composable
fun rememberPressMorph(base: Shape, source: InteractionSource, pressedCorner: Dp = 18.dp): Shape {
    val pressed by source.collectIsPressedAsState()
    val fraction by animateFloatAsState(if (pressed) 1f else 0f, AppMotion.fastSpatial(), label = "morph")
    return remember(base, fraction, pressedCorner) {
        if (fraction == 0f) base else MorphedShape(base, fraction, pressedCorner)
    }
}

private class MorphedShape(private val base: Shape, private val fraction: Float, private val to: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val target = with(density) { to.toPx() }
        val limit = size.minDimension / 2
        fun morph(r: CornerRadius) = CornerRadius(
            (r.x + (maxOf(r.x, target) - r.x) * fraction).coerceIn(0f, limit),
            (r.y + (maxOf(r.y, target) - r.y) * fraction).coerceIn(0f, limit),
        )
        return when (val outline = base.createOutline(size, layoutDirection, density)) {
            is Outline.Rounded -> outline.roundRect.let { r ->
                Outline.Rounded(
                    RoundRect(
                        r.left, r.top, r.right, r.bottom,
                        morph(r.topLeftCornerRadius), morph(r.topRightCornerRadius),
                        morph(r.bottomRightCornerRadius), morph(r.bottomLeftCornerRadius),
                    ),
                )
            }
            is Outline.Rectangle -> outline.rect.let { r ->
                val c = morph(CornerRadius.Zero)
                Outline.Rounded(RoundRect(r.left, r.top, r.right, r.bottom, c, c, c, c))
            }
            else -> outline
        }
    }
}

/** A card that gives a little under the finger: it shrinks a touch while pressed and springs back. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) pressedScale else 1f, AppMotion.fastSpatial(), label = "press")
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

// ---------------------------------------------------------------------------------------------
// Appearing
// ---------------------------------------------------------------------------------------------

private const val STAGGER_MS = 460
private const val STAGGER_STEP_MS = 55
private const val STAGGER_MAX_STEPS = 6
private const val STAGGER_WINDOW_MS = 400L
private val STAGGER_RISE = 18.dp

/** Pages whose first blocks have already risen into place since the app started (see [rememberEnterStagger]). */
private val staggeredPages = mutableSetOf<Any>()

/** Which blocks of a page rise into place (see [staggerIn]). */
@Stable
class EnterStagger internal constructor(internal var open: Boolean)

/**
 * Lets the first blocks of the page [key] rise softly into place one after another, the first time it appears
 * after the app starts: not each time the mother comes back to it, and not for blocks scrolled into view later.
 */
@Composable
fun rememberEnterStagger(key: Any): EnterStagger {
    val stagger = remember(key) { EnterStagger(open = staggeredPages.add(key)) }
    LaunchedEffect(stagger) {
        // What the page shows in its first moments takes part; anything after that simply appears.
        delay(STAGGER_WINDOW_MS)
        stagger.open = false
    }
    return stagger
}

/** The [index]th block of a page rising into place (see [rememberEnterStagger]). */
@Composable
fun Modifier.staggerIn(stagger: EnterStagger, index: Int): Modifier {
    val play = remember { stagger.open }
    if (!play) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delay = index.coerceAtMost(STAGGER_MAX_STEPS) * STAGGER_STEP_MS
        progress.animateTo(1f, tween(STAGGER_MS, delayMillis = delay, easing = EmphasizedDecelerate))
    }
    val rise = with(LocalDensity.current) { STAGGER_RISE.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise
    }
}

/**
 * A picture that fades in once it has loaded, instead of popping in. Draws nothing while [bitmap] is null; a picture
 * that is already there when this first shows simply shows.
 */
@Composable
fun FadeInImage(bitmap: ImageBitmap?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale) {
    val loading = remember { bitmap == null }
    if (bitmap == null) return
    val alpha = remember(bitmap) { Animatable(if (loading) 0f else 1f) }
    LaunchedEffect(bitmap) { alpha.animateTo(1f, AppMotion.slowEffects()) }
    Image(bitmap, contentDescription, modifier.graphicsLayer { this.alpha = alpha.value }, contentScale = contentScale)
}
