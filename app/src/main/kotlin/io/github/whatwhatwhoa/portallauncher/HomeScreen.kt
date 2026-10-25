package io.github.whatwhatwhoa.portallauncher

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

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
            Hero(state.art, current, scenes = state.look.scenes)
            Carousel(state, Modifier.offset(y = rowTop))
        }
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - ghost }) {
            Box(Modifier.fillMaxWidth().height(Dimens.band)) {
                if (current != null) Name(current.label, inFocus = state.zone == Zone.Row)
            }
            Spacer(Modifier.weight(1f))
            BottomRow(state)
        }
        CategoryLabel(state.category.label, Modifier.graphicsLayer { alpha = 1f - ghost }.endingAt(rowTop - Space.m))
    }
}

/** The category on show, small, on the content margin just above the carousel (which B changes). */
@Composable
private fun CategoryLabel(label: String, modifier: Modifier) {
    BasicText(
        label,
        modifier = modifier.padding(start = Dimens.edge),
        style = LocalType.current.title.copy(color = LocalPalette.current.muted),
        maxLines = 1,
    )
}

/** Places the content so its bottom edge is [y] from the top. */
private fun Modifier.endingAt(y: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, y.roundToPx() - placeable.height) }
}

private val SWIPE_UP = 96.dp
private const val HERO_FADE = 250
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
            Backdrop.Glow -> app?.let { rememberArt(art, it.component.packageName, ArtKind.Hero, GLOW_PX) }
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
 * vignette if it has one (and [scenes] are on), else its hero in the top band under a soft one.
 * Stays on the row's app while the dock has focus; neither leaves the backdrop.
 */
@Composable
private fun Hero(art: Art?, app: AppEntry?, scenes: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // rememberArt keeps the last image while the next decodes, so the crossfade has no gap.
        val pkg = app?.component?.packageName
        val scene = pkg?.takeIf { scenes }?.let { rememberArt(art, it, ArtKind.Scene, constraints.maxWidth) }
        val hero = pkg?.let { rememberArt(art, it, ArtKind.Hero, constraints.maxWidth) }
        val shown = scene?.let { Shown(it, full = true) } ?: hero?.let { Shown(it, full = false) }
        Crossfade(shown, animationSpec = if (LocalMotion.current) tween(HERO_FADE) else snap(), label = "hero") {
            when {
                it == null -> Unit
                it.full -> Image(it.image, null, Modifier.fillMaxSize().sceneVignette(), contentScale = ContentScale.Crop)
                else -> Image(
                    it.image,
                    null,
                    Modifier.fillMaxWidth().height(Dimens.band).vignette(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

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
    val color = if (inFocus) p.accent else p.muted
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

private const val NAME_SCROLL_PAUSE = 2_500
private val NAME_SCROLL_SPEED = 90.dp

/**
 * The favorites row, the focused tile always in the first slot. Each tile slides on its own
 * spring along a fractional slot number (0 the focused slot, 1 the next, -1 the one before, off
 * screen), and its x and size are worked out from that number in the layout phase: nothing
 * recomposes while the row moves, the gaps stay even mid-slide, and a moved tile slides too. A
 * LazyRow scrolling to the item while the tiles resized made the motion jump (owner's report).
 * The row is laid out for the grown tile, so focus leaving it doesn't shift it.
 */
@Composable
private fun Carousel(state: LauncherState, modifier: Modifier) {
    val apps = state.row
    val focus = state.rowFocus
    val motion = LocalMotion.current
    // How grown the first slot is: 1 while the row has focus, 0 while the dock has it.
    val grow = animateFloatAsState(if (state.zone == Zone.Row) 1f else 0f, if (motion) SLIDE else snap(), label = "grow")
    // Picked up to move, the grown tile lifts a little more, by scale.
    val liftScale = Dimens.LIFT_SCALE / Dimens.FOCUS_SCALE
    Box(modifier.fillMaxWidth().height(Dimens.tileFocused).dragsAlong(state)) {
        apps.forEachIndexed { i, app ->
            key(app.key) {
                val target = (i - focus).toFloat()
                val slot = remember { Animatable(target) }
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
                // Art where it exists: a legacy icon like NTE's 192 px one can't fill a tile crisply.
                CoverOrIcon(
                    state.art.takeIf { state.look.covers },
                    app,
                    RoundedCornerShape(Dimens.tileRadius),
                    Dimens.tileFocused * liftScale,
                    Modifier.inSlot({ slot.value }, { grow.value })
                        .launchesFrom(state, Origin.Row, app)
                        .graphicsLayer {
                            scaleX = lift
                            scaleY = lift
                            this.alpha = alpha
                        }
                        .depth(Dimens.tileRadius)
                        .focusRing(focused, Dimens.tileRadius, steady = lifted)
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
        listOfNotNull(Hint("+", "Options").takeIf { state.focusHasOptions }, Hint("−", "Quick settings"), Hint("Y", "All apps"))
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
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.dockGap)) {
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
        ) {
            Symbol(R.drawable.ic_sym_settings, LocalPalette.current.text)
        }
    }
}

@Composable
private fun DockButton(
    label: String,
    focused: Boolean,
    lifted: Boolean,
    onClick: () -> Unit,
    onHold: (() -> Unit)?,
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
    }
}
