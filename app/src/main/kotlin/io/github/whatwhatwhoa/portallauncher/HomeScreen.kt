package io.github.whatwhatwhoa.portallauncher

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The hero band across the top (the focused app's art, its name at the top edge), the current
 * category's carousel across the middle with the category named above it, and the dock centered
 * at the bottom. A swipe up anywhere opens All apps. Behind the categories layer Home zooms out:
 * the art and the row ghosted, the rest gone.
 */
@Composable
fun HomeScreen(state: LauncherState) {
    val current = state.row.getOrNull(state.rowFocus)
    val ghost by animateFloatAsState(
        if (state.categoriesOpen) 1f else 0f,
        if (LocalMotion.current) tween(HERO_FADE) else snap(),
        label = "ghost",
    )
    // On the dock, Home dims as under Triangle's sheet, all but the bottom row: only the dock is
    // in focus (owner). Its hints stay, since they are the dock's buttons.
    val dock by animateFloatAsState(
        if (state.zone == Zone.Dock) 1f else 0f,
        if (LocalMotion.current) tween(HERO_FADE) else snap(),
        label = "dockScrim",
    )
    val background = LocalPalette.current.background
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var travel = 0f
            detectVerticalDragGestures(
                onDragStart = { travel = 0f },
                onDragEnd = { if (travel < -SWIPE_UP.toPx()) state.openDrawer() },
                onVerticalDrag = { _, dy -> travel += dy },
            )
        },
    ) {
        // Placed by its center line, not by what is around it (owner's ask).
        val rowTop = maxHeight * Dimens.CAROUSEL_CENTER - Dimens.tileFocused / 2
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f + (Dimens.GHOST_ALPHA - 1f) * ghost
                scaleX = 1f + (Dimens.GHOST_SCALE - 1f) * ghost
                scaleY = scaleX
            },
        ) {
            BackdropLayer(state.backdrop, state.art, current)
            Hero(state.art, current)
            Carousel(state, Modifier.offset(y = rowTop))
        }
        Box(Modifier.fillMaxSize().drawBehind { drawRect(background, alpha = DOCK_SCRIM * dock) })
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - ghost }) {
            // Drawn over the scrim, so faded by as much as it.
            Box(Modifier.fillMaxWidth().height(Dimens.band).graphicsLayer { alpha = 1f - DOCK_SCRIM * dock }) {
                if (current != null) {
                    when (state.look.name) {
                        NameStyle.Metro -> Name(current.label, inFocus = state.zone == Zone.Row)
                        NameStyle.Fitted -> FittedName(current.label, inFocus = state.zone == Zone.Row)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            BottomRow(state)
        }
        CategoryLabel(
            state,
            Modifier.graphicsLayer { alpha = (1f - ghost) * (1f - DOCK_SCRIM * dock) }.endingAt(rowTop - Space.m),
        )
    }
}

/**
 * The category on show, small, on the content margin just above the carousel (which B changes);
 * a new one crossfades in as the hero does. While L1 and R1 turn it, their bumpers are drawn
 * either side as the quick panel and the Art… tabs draw them, muted like the name: not at an end,
 * where the turn stops, nor while they skip tracks.
 */
@Composable
private fun CategoryLabel(state: LauncherState, modifier: Modifier) {
    val p = LocalPalette.current
    val home = LocalHome.current
    val categories = state.categories
    val bumpers = home.hints && !home.playControls && categories.size > 1
    Crossfade(state.category, modifier, animationSpec = if (LocalMotion.current) tween(HERO_FADE) else snap(), label = "category") { category ->
        val at = categories.indexOf(category)
        Row(
            Modifier.padding(start = Dimens.edge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (bumpers && at > 0) Box(Modifier.graphicsLayer { alpha = p.muted.alpha }) { BumperGlyph(left = true) }
            BasicText(category.label, style = LocalType.current.title.copy(color = p.muted), maxLines = 1)
            if (bumpers && at in 0 until categories.lastIndex) Box(Modifier.graphicsLayer { alpha = p.muted.alpha }) { BumperGlyph(left = false) }
        }
    }
}

/** Places the content so its bottom edge is [y] from the top. */
private fun Modifier.endingAt(y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, y.roundToPx() - placeable.height) }
}

private val SWIPE_UP = 96.dp
private const val HERO_FADE = 250
// The options sheet's (OptionsSheet).
private const val DOCK_SCRIM = 0.6f
private const val NAME_ALPHA = 0.4f
private val NAME_BLEED = Space.m
private const val CAP_HEIGHT = 0.72f
private const val GLOW_PX = 480
private val GLOW_BLUR = 40.dp
private const val GLOW_DIM = 0.5f
private const val WALLPAPER_DIM = 0.35f

/**
 * What Home sits on. The plain ones are the root's own background color, with the background
 * gradient (an effect) over it: a faint spot of the palette's lift where the scenes are
 * brightest, fading to the background color. Hero glow is the focused app's hero over the whole
 * screen, blurred and dimmed, so the space around the carousel takes the game's colors; it is
 * decoded small, since the blur hides it. A wallpaper fills the screen, cropped, dimmed a little
 * so the tiles and hints read on it. Both cover the gradient.
 */
@Composable
private fun BackdropLayer(backdrop: Backdrop, art: Art?, app: AppEntry?) {
    val p = LocalPalette.current
    val background = p.background
    val gradient = LocalEffects.current.gradient
    // Dithered on the native canvas: a near-black ramp this wide bands without it.
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG) }
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            if (!gradient) return@drawBehind
            paint.shader = RadialGradient(
                size.width * Dimens.LIFT_X,
                size.height * Dimens.LIFT_Y,
                size.width * Dimens.LIFT_RADIUS,
                p.lift.toArgb(),
                background.toArgb(),
                Shader.TileMode.CLAMP,
            )
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        },
    ) {
        val image = when (backdrop) {
            // Under an app that shows its scene there is no hero to glow: one image per app.
            Backdrop.Glow -> app?.component?.packageName?.takeIf { art?.wide(it) == ArtKind.Hero }
                ?.let { rememberArt(art, it, ArtKind.Hero, GLOW_PX) }
            is Backdrop.Wallpaper -> rememberImage(backdrop.file, constraints.maxWidth)
            else -> null
        }
        val glow = backdrop == Backdrop.Glow
        Crossfade(image, animationSpec = if (LocalMotion.current) tween(HERO_FADE) else snap(), label = "backdrop") {
            if (it != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        it,
                        null,
                        Modifier.fillMaxSize().then(if (glow) Modifier.blur(GLOW_BLUR, BlurredEdgeTreatment.Rectangle) else Modifier),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(background.copy(alpha = if (glow) GLOW_DIM else WALLPAPER_DIM)))
                }
            }
        }
    }
}

/**
 * The row's current app's art behind Home: its scene over the whole screen under a heavy
 * vignette, or its hero in the top band under a soft one, whichever the app uses (Art.wide);
 * never both, so the hero's reflection never lies under a scene. Stays on the row's app while
 * the dock has focus; neither leaves the backdrop.
 */
@Composable
private fun Hero(art: Art?, app: AppEntry?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // rememberArt keeps the last image while the next decodes, so the crossfade has no gap.
        val pkg = app?.component?.packageName
        val kind = pkg?.let { art?.wide(it) }
        val image = kind?.let { rememberArt(art, pkg, it, constraints.maxWidth) }
        val shown = image?.let { Shown(it, full = kind == ArtKind.Scene) }
        Crossfade(shown, animationSpec = if (LocalMotion.current) tween(HERO_FADE) else snap(), label = "hero") {
            when {
                it == null -> Unit
                it.full -> Image(it.image, null, Modifier.fillMaxSize().sceneVignette(), contentScale = ContentScale.Crop)
                else -> Box(Modifier.fillMaxSize()) {
                    if (LocalEffects.current.reflection) Reflection(it.image)
                    Image(
                        it.image,
                        null,
                        Modifier.fillMaxWidth().height(Dimens.band).vignette(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

/**
 * The hero mirrored below the band like moonlight on water (owner's idea): flipped and set a
 * short gap under the band, so hero and reflection read as two blocks (the owner's sketch; the
 * first build overlapped them); darkened and a little cooled, blurred more down than across so
 * it streaks like a reflection, rippling slowly sideways, and gone before the dock. It sits behind the carousel
 * and fills the space around it with the game's colors. The ripple needs Android 13's runtime
 * shaders; before that, or with animations off, the reflection is still.
 */
@Composable
private fun Reflection(image: ImageBitmap) {
    val dark = LocalPalette.current.dark
    val ripple = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(RIPPLE_SHADER) else null }
    val phase = if (ripple != null && LocalMotion.current) {
        rememberInfiniteTransition(label = "ripple").animateFloat(
            0f,
            TWO_PI,
            infiniteRepeatable(tween(RIPPLE_PERIOD, easing = LinearEasing)),
            label = "phase",
        )
    } else {
        null
    }
    // Light pages get no darkening: a dark smudge on a light page reads as dirt, not water.
    val filter = remember(dark) {
        val tone = ColorMatrix().apply { setToSaturation(REFLECT_SATURATION) }
        // Darkened twice as far as the first build, which the owner found too bright.
        if (dark) tone.timesAssign(ColorMatrix().apply { setToScale(0.38f, 0.41f, 0.5f, 1f) })
        ColorFilter.colorMatrix(tone)
    }
    Image(
        image,
        null,
        Modifier.offset(y = Dimens.band + REFLECT_GAP).fillMaxWidth().height(Dimens.band)
            .reflectionFade()
            .graphicsLayer {
                scaleY = -1f
                val blur = android.graphics.RenderEffect.createBlurEffect(
                    REFLECT_BLUR_X.toPx(),
                    REFLECT_BLUR_Y.toPx(),
                    Shader.TileMode.CLAMP,
                )
                // Reading the phase here redraws only this layer each frame, not Home.
                val effect = if (ripple != null && Build.VERSION.SDK_INT >= 33) {
                    ripple.setFloatUniform("size", size.width, size.height)
                    ripple.setFloatUniform("phase", phase?.value ?: 0f)
                    ripple.setFloatUniform("amp", RIPPLE_AMP.toPx())
                    android.graphics.RenderEffect.createChainEffect(
                        blur,
                        android.graphics.RenderEffect.createRuntimeShaderEffect(ripple, "image"),
                    )
                } else {
                    blur
                }
                renderEffect = effect.asComposeRenderEffect()
            },
        contentScale = ContentScale.Crop,
        colorFilter = filter,
    )
}

/**
 * Faded in over its first few dp, so the top is soft rather than a hard line across the gap;
 * strongest just under it, gone by two thirds of the way down; the band's side fades too.
 */
private fun Modifier.reflectionFade() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val keep = Color.Black
    drawRect(
        Brush.verticalGradient(0f to keep.copy(alpha = 0f), REFLECT_SOFT_TOP to keep.copy(alpha = REFLECT_ALPHA), 0.3f to keep.copy(alpha = REFLECT_ALPHA * 0.5f), 0.68f to keep.copy(alpha = 0f)),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        Brush.horizontalGradient(0f to keep.copy(alpha = 0.15f), 0.5f to keep, 0.75f to keep, 1f to keep.copy(alpha = 0.5f)),
        blendMode = BlendMode.DstIn,
    )
}

// Two waves whose speeds are whole multiples of one period, so the loop has no seam; the sway
// grows away from the mirror line (the layer is flipped, so the line is its local bottom). The x is
// clamped so the sway never samples past the picture's sides.
private const val RIPPLE_SHADER = """
uniform shader image;
uniform float2 size;
uniform float phase;
uniform float amp;
half4 main(float2 p) {
    float depth = 1.0 - clamp(p.y / size.y, 0.0, 1.0);
    float wave = 0.65 * sin(p.y * 0.045 + phase) + 0.35 * sin(p.y * 0.13 - 2.0 * phase + p.x * 0.004);
    float x = clamp(p.x + wave * amp * (0.2 + depth), 0.0, size.x - 1.0);
    return image.eval(float2(x, p.y));
}
"""
private const val TWO_PI = 6.2831855f
private const val RIPPLE_PERIOD = 9000
private val RIPPLE_AMP = 6.dp
// The gap between the band's foot and the reflection's top (F20, the owner's sketch).
private val REFLECT_GAP = Space.xxl
private const val REFLECT_SOFT_TOP = 0.08f
private const val REFLECT_ALPHA = 0.55f
private const val REFLECT_SATURATION = 0.75f
private val REFLECT_BLUR_X = 3.dp
private val REFLECT_BLUR_Y = 10.dp

private data class Shown(val image: ImageBitmap, val full: Boolean)

/**
 * The scene's heavy vignette, by alpha like the band's: brightest a little right of center and
 * above the carousel, falling away to the corners; the left under the name and the bottom under
 * the dock and hints nearly gone, so text reads on any picture.
 */
private fun Modifier.sceneVignette() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val keep = Color.Black
    drawRect(
        Brush.radialGradient(
            0f to keep.copy(alpha = 0.9f),
            0.45f to keep.copy(alpha = 0.75f),
            1f to keep.copy(alpha = 0.12f),
            center = Offset(size.width * 0.62f, size.height * 0.38f),
            radius = size.width * 0.8f,
        ),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        Brush.verticalGradient(0f to keep.copy(alpha = 0.6f), 0.25f to keep, 0.55f to keep, 1f to keep.copy(alpha = 0.1f)),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        Brush.horizontalGradient(0f to keep.copy(alpha = 0.35f), 0.45f to keep),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * Fading out toward the edges, by alpha, so the hero melts into whatever is behind it, a color or
 * a wallpaper: gone at the bottom (the art ends in the page, not on an edge), mostly gone on the
 * left where the name sits, half along the top under the status and on the right. Each mask
 * multiplies the alpha, so where they overlap it fades further.
 */
private fun Modifier.vignette() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val keep = Color.Black
    drawRect(
        Brush.verticalGradient(0f to keep.copy(alpha = 0.5f), 0.3f to keep, 0.5f to keep, 1f to keep.copy(alpha = 0f)),
        blendMode = BlendMode.DstIn,
    )
    drawRect(
        Brush.horizontalGradient(0f to keep.copy(alpha = 0.15f), 0.5f to keep, 0.75f to keep, 1f to keep.copy(alpha = 0.5f)),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * The focused favorite's name, top-left under the clock, see-through so the hero shows in it;
 * muted while the dock has focus. A name too long for the screen scrolls while the row has
 * focus (owner's ask), out past the left edge and round again after a pause; with animations off,
 * or while muted, it runs off the right edge instead of ending in an ellipsis.
 */
@Composable
private fun Name(label: String, inFocus: Boolean) {
    val p = LocalPalette.current
    val t = LocalType.current
    // The accent on the dock too: the scrim dims it there, as under a sheet (owner).
    val color = p.accent
    val scrolls = inFocus && LocalMotion.current
    // A new name starts from its first letter, after the pause.
    key(label) {
        BasicText(
            label.uppercase(),
            modifier = Modifier.layout { measurable, constraints ->
                // Metro-style, the capitals run past the top edge: the line is lifted so the cap
                // height ends NAME_BLEED above the screen. Cap height is a share of the size,
                // close enough for every trial font.
                val width = if (scrolls) constraints.maxWidth else Constraints.Infinity
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = width))
                val capTop = placeable[FirstBaseline] - (t.display.fontSize.toPx() * CAP_HEIGHT).roundToInt()
                layout(constraints.maxWidth, placeable.height) { placeable.place(0, -capTop - NAME_BLEED.roundToPx()) }
            }.then(
                if (scrolls) {
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = NAME_SCROLL_PAUSE,
                        repeatDelayMillis = NAME_SCROLL_PAUSE,
                        velocity = NAME_SCROLL_SPEED,
                    )
                } else {
                    Modifier
                },
            ).padding(start = Dimens.edge),
            style = t.display.copy(color = color.copy(alpha = color.alpha * NAME_ALPHA)),
            softWrap = false,
            maxLines = 1,
        )
    }
}

/**
 * The big name in the Fitted style (F31): as written, semibold, wholly on screen under the
 * status. It starts at [FITTED_MAX] of the display size and shrinks with the name's width, down
 * to [FITTED_MIN], so most game titles fit on one line; a longer one scrolls like the Metro name
 * (or ends in an ellipsis while muted or with animations off).
 */
@Composable
private fun FittedName(label: String, inFocus: Boolean) {
    val p = LocalPalette.current
    val t = LocalType.current
    // The accent on the dock too: the scrim dims it there, as under a sheet (owner).
    val color = p.accent
    val scrolls = inFocus && LocalMotion.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = Dimens.edge, end = Dimens.edge, top = FITTED_TOP)) {
        val room = constraints.maxWidth
        val base = t.display.copy(fontWeight = FontWeight.SemiBold, fontSize = t.display.fontSize * FITTED_MAX)
        // Text width grows with its size, so one measure at the largest size gives the scale.
        val style = remember(label, room, base) {
            val wide = measurer.measure(label, base, softWrap = false, maxLines = 1).size.width
            val scale = if (wide <= room) 1f else (room.toFloat() / wide).coerceAtLeast(FITTED_MIN / FITTED_MAX)
            base.copy(fontSize = base.fontSize * scale, lineHeight = base.fontSize * scale * FITTED_LINE)
        }
        key(label) {
            BasicText(
                label,
                modifier = if (scrolls) {
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = NAME_SCROLL_PAUSE,
                        repeatDelayMillis = NAME_SCROLL_PAUSE,
                        velocity = NAME_SCROLL_SPEED,
                    )
                } else {
                    Modifier
                },
                style = style.copy(color = color.copy(alpha = color.alpha * FITTED_ALPHA)),
                softWrap = false,
                maxLines = 1,
                overflow = if (scrolls) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}

// Under the status, clear of it; the largest line (about 110 dp) ends above the category name.
private val FITTED_TOP = Space.xxxl
private const val FITTED_MAX = 0.36f
private const val FITTED_MIN = 0.2f
private const val FITTED_LINE = 1.2f
// A little more opaque than the Metro name's 0.4 (owner's ask).
private const val FITTED_ALPHA = 0.6f

private const val NAME_SCROLL_PAUSE = 2_500
private val NAME_SCROLL_SPEED = 90.dp

/**
 * The favorites row, the focused tile always in the first slot. Each tile slides on its own
 * spring along a fractional slot number (0 the focused slot, 1 the next, -1 the one before, off
 * screen), and its x and size are worked out from that number in the layout phase: nothing
 * recomposes while the row moves, the gaps stay even mid-slide, and a moved tile slides too. A
 * LazyRow scrolling to the item while the tiles resized made the motion jump (owner's report).
 * The row is laid out for the grown tile, so focus leaving it doesn't shift it. Each category's
 * row has tiles of its own: a new one glides in from the side it was turned to, from the right
 * for a later category (R1, down in the layer), from the left for an earlier one.
 */
@Composable
private fun Carousel(state: LauncherState, modifier: Modifier) {
    val apps = state.row
    val focus = state.rowFocus
    val motion = LocalMotion.current
    val shown = state.shownCategoryId
    // The category shown when the row last drew; only the frame that turns it starts tiles aside,
    // so a tile added to the same row (a new favorite) still starts in place.
    val last = remember { ShownRow(shown) }
    val enter = if (shown == last.id || !motion) {
        0f
    } else {
        val order = state.categoryList
        ENTER_SLOTS * (order.indexOfFirst { it.id == shown } - order.indexOfFirst { it.id == last.id }).sign
    }
    SideEffect { last.id = shown }
    // How grown the first slot is: 1 while the row has focus, 0 while the dock has it.
    val grow = animateFloatAsState(if (state.zone == Zone.Row) 1f else 0f, if (motion) SLIDE else snap(), label = "grow")
    // Picked up to move, the grown tile lifts a little more, by scale.
    val liftScale = Dimens.LIFT_SCALE / Dimens.FOCUS_SCALE
    Box(modifier.fillMaxWidth().height(Dimens.tileFocused).dragsAlong(state)) {
        apps.forEachIndexed { i, app ->
            key(shown, app.key) {
                val target = (i - focus).toFloat()
                val slot = remember { Animatable(target + enter) }
                // A new target mid-slide carries the current speed on, so holding a direction glides.
                LaunchedEffect(target, motion) { if (motion) slot.animateTo(target, SLIDE) else slot.snapTo(target) }
                val focused = state.zone == Zone.Row && i == focus
                val lifted = focused && state.moving == Origin.Row
                val lift by animateFloatAsState(
                    if (lifted) liftScale else 1f,
                    if (motion) spring(dampingRatio = 0.7f, stiffness = 700f) else snap(),
                    label = "lift",
                )
                val alpha by animateFloatAsState(
                    if (focused) 1f else UNFOCUSED_ALPHA,
                    if (motion) tween(HERO_FADE) else snap(),
                    label = "tileAlpha",
                )
                val art = state.art.takeIf { state.look.covers }
                // Art where it exists: a legacy icon like NTE's 192 px one can't fill a tile crisply.
                CoverOrIcon(
                    art,
                    app,
                    RoundedCornerShape(Dimens.tileRadius),
                    Dimens.tileFocused * liftScale,
                    Modifier.inSlot({ slot.value }, { grow.value })
                        .launchesFrom(state, Origin.Row, app)
                        .graphicsLayer {
                            scaleX = lift
                            scaleY = lift
                        }
                        .depth(Dimens.tileRadius)
                        .focusRing(focused, Dimens.tileRadius, steady = lifted)
                        // The fade in a layer of its own, inside the shadow and the ring: a faded
                        // layer is drawn offscreen at the tile's size, which cut the light theme's
                        // shadow off square at the corners (B7.3).
                        .graphicsLayer {
                            this.alpha = alpha
                            // A cover (or its placeholder) is one draw, so it fades without an
                            // offscreen pass of its own; an icon's two layers would show through
                            // each other, so it keeps one. Read here, so an app's art arriving
                            // redoes this layer only, not the whole row.
                            val oneDraw = art?.file(app.component.packageName, ArtKind.Cover) != null
                            compositingStrategy = if (oneDraw) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
                        }
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onLongClick = { state.holdRow(i) },
                            onClick = { state.tapRow(i) },
                        ),
                )
            }
        }
    }
}

private const val UNFOCUSED_ALPHA = 0.85f
// No bounce: an overshoot reads as a jump when every tile does it at once.
private val SLIDE = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
// How many slots aside a turned-to row starts, gliding into place on SLIDE.
private const val ENTER_SLOTS = 1f

private class ShownRow(var id: String)

/**
 * Lays a carousel tile out at its fractional [slot]: x is interpolated between the slots either
 * side, and the size from the first slot's (grown by [grow]) to a plain tile's within one slot
 * of it. Those two together keep the gap to each neighbor exactly [Dimens.tileGap] mid-slide.
 * Both are read here, in the layout phase, so a slide only re-lays out.
 */
private fun Modifier.inSlot(slot: () -> Float, grow: () -> Float) = layout { measurable, constraints ->
    val edge = Dimens.edge.toPx()
    val tile = Dimens.tile.toPx()
    val gap = Dimens.tileGap.toPx()
    val first = tile + (Dimens.tileFocused.toPx() - tile) * grow()
    fun x(n: Int) = when {
        n == 0 -> edge
        n > 0 -> edge + first + gap + (n - 1) * (tile + gap)
        else -> edge + n * (tile + gap)
    }
    val s = slot()
    val lo = floor(s).toInt()
    val left = x(lo) + (x(lo + 1) - x(lo)) * (s - lo)
    val size = (tile + (first - tile) * (1f - abs(s)).coerceAtLeast(0f)).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(size, size))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(left.roundToInt(), (constraints.maxHeight - size) / 2)
    }
}

/** Touch: dragging the row moves focus one slot per tile width dragged; nothing opens. */
private fun Modifier.dragsAlong(state: LauncherState) = pointerInput(state) {
    var travel = 0f
    detectHorizontalDragGestures(onDragStart = { travel = 0f }) { _, dx ->
        travel += dx
        val step = (Dimens.tile + Dimens.tileGap).toPx()
        val slots = (travel / step).toInt()
        if (slots != 0) {
            state.focusRow(state.rowFocus - slots)
            travel -= slots * step
        }
    }
}

/**
 * The dock centered on the screen (owner's ask), the button hints stacked on the right,
 * bottom-aligned. A and B go without saying (owner), so only the other buttons are hinted. The
 * stack is narrow enough that the dock stays put as hints come and go; only a dock too wide to
 * clear them would move left.
 */
@Composable
private fun BottomRow(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val moving = state.moving != null
    val hints = if (moving) {
        emptyList()
    } else {
        listOfNotNull(Hint(TRIANGLE, "Options").takeIf { state.focusHasOptions }, Hint(SQUARE, "Quick settings"), Hint("Y", "All apps"))
    }
    // Each hint on the side of the Portal its button is on (owner's ask).
    val (leftHints, rightHints) = hints.partition { it.onLeft }
    Layout(
        content = {
            Dock(state)
            Column(horizontalAlignment = Alignment.End) {
                if (moving) BasicText("Left and right move it", style = t.hint.copy(color = p.muted), maxLines = 1)
                Hints(rightHints, stacked = true)
            }
            // A box, so the side is laid out (empty) even with no hints on it.
            Box { Hints(leftHints, stacked = true, alignEnd = false) }
        },
        modifier = Modifier.fillMaxWidth().padding(start = Dimens.edge, end = Dimens.edge, bottom = Dimens.bottom),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (dock, right, left) = measurables.map { it.measure(loose) }
        val width = constraints.maxWidth
        val height = maxOf(dock.height, right.height, left.height)
        val rightX = width - right.width
        val gap = Dimens.dockGap.roundToPx()
        // The margins are even, so the middle of this row is the middle of the screen; a dock too
        // wide to clear the hints moves away from the right ones, but never over the left ones.
        val dockX = minOf((width - dock.width) / 2, rightX - gap - dock.width).coerceAtLeast(if (left.width > 0) left.width + gap else 0)
        layout(width, height) {
            dock.place(dockX, height - dock.height)
            right.place(rightX, height - right.height)
            left.place(0, height - left.height)
        }
    }
}

/**
 * All apps first, then the utility apps, then Launcher Settings (owner, 2026-10-25), as round
 * buttons; the focused one's name sits above.
 */
@Composable
private fun Dock(state: LauncherState) {
    val inDock = state.zone == Zone.Dock
    // Off the dock it steps back as Home does on it (owner). Faded per draw, not as a layer: a
    // layer the dock's size would clip the focus ring and the name above while it fades back in.
    val rest by animateFloatAsState(
        if (inDock) 0f else 1f,
        if (LocalMotion.current) tween(HERO_FADE) else snap(),
        label = "dockRest",
    )
    Row(
        Modifier.graphicsLayer {
            alpha = 1f - DOCK_SCRIM * rest
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        horizontalArrangement = Arrangement.spacedBy(Dimens.dockGap),
    ) {
        DockButton("All apps", inDock && state.dockFocus == 0, lifted = false, onClick = { state.tapDock(0) }, onHold = null) {
            AllAppsGlyph()
        }
        state.dock.forEachIndexed { i, app ->
            val focused = inDock && state.dockFocus == i + 1
            DockButton(
                app.label,
                focused,
                lifted = focused && state.moving == Origin.Dock,
                onClick = { state.tapDock(i + 1) },
                onHold = { state.holdDock(i + 1) },
            ) {
                AppIcon(app, CircleShape, Modifier.size(Dimens.dockButton).launchesFrom(state, Origin.Dock, app))
            }
        }
        DockButton(
            "Launcher settings",
            inDock && state.dockFocus == state.dockSettings,
            lifted = false,
            onClick = { state.tapDock(state.dockSettings) },
            onHold = null,
            badge = { SettingsBadge(Modifier.align(Alignment.BottomEnd)) },
        ) {
            // Our own icon, so it reads as this launcher's settings beside Android's gear (owner).
            OwnIcon(CircleShape, Modifier.size(Dimens.dockButton))
        }
    }
}

/** A small gear on a disc of the background color, overlapping the button's lower right. */
@Composable
private fun SettingsBadge(modifier: Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.offset(x = BADGE_OUT, y = BADGE_OUT).size(BADGE).clip(CircleShape).background(p.background),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(R.drawable.ic_sym_settings, p.text, Modifier.size(BADGE_GEAR))
    }
}

private val BADGE = 24.dp
private val BADGE_GEAR = 18.dp
private val BADGE_OUT = 4.dp

@Composable
private fun DockButton(
    label: String,
    focused: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    onHold: (() -> Unit)?,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val t = LocalType.current
    Box(Modifier.size(Dimens.dockButton)) {
        if (focused) {
            BasicText(
                label,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = -(t.caption.lineHeight.asDp() + Dimens.dockLabelGap))
                    .wrapContentWidth(unbounded = true),
                style = t.caption.copy(color = p.text),
                maxLines = 1,
            )
        }
        Box(
            Modifier.size(Dimens.dockButton)
                .focusTile(focused, corner = null, lifted = lifted)
                .depth(corner = null)
                .clip(CircleShape)
                .background(p.surface)
                .combinedClickable(interactionSource = null, indication = null, onLongClick = onHold, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
        badge?.invoke(this)
    }
}
