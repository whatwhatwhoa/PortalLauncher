package io.github.whatwhatwhoa.portallauncher

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * An app icon drawn straight onto the canvas at the size it is shown: vector layers render at
 * full resolution and bitmap layers scale down only. An adaptive icon fills the shape edge to
 * edge (our mask, not the system's); a legacy icon sits inset on the surface color.
 */
@Composable
fun AppIcon(entry: AppEntry, shape: Shape, modifier: Modifier = Modifier) {
    val icon = remember(entry) { entry.newIcon() }
    IconCanvas(icon, shape, modifier)
}

/** This launcher's own icon, drawn the same way as any app's. */
@Composable
fun OwnIcon(shape: Shape, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName) }
    IconCanvas(icon, shape, modifier)
}

@Composable
private fun IconCanvas(icon: Drawable, shape: Shape, modifier: Modifier) {
    val surface = LocalPalette.current.surface
    Canvas(modifier.clip(shape)) {
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (icon is AdaptiveIconDrawable) {
            // Layers are 108 dp with the visible 72 dp in the middle, so they overhang by a quarter.
            val dx = w / 4
            val dy = h / 4
            drawIntoCanvas { canvas ->
                listOfNotNull(icon.background, icon.foreground).forEach {
                    it.setBounds(-dx, -dy, w + dx, h + dy)
                    it.draw(canvas.nativeCanvas)
                }
            }
        } else {
            drawRect(surface)
            val inset = (w * LEGACY_INSET).roundToInt()
            drawIntoCanvas { canvas ->
                icon.setBounds(inset, inset, w - inset, h - inset)
                icon.draw(canvas.nativeCanvas)
            }
        }
    }
}

private const val LEGACY_INSET = 0.16f

/**
 * The app's square cover where it has art, else its icon. The cover is decoded at [drawn], the
 * largest size it is ever shown at, so nothing upscales it.
 */
@Composable
fun CoverOrIcon(art: Art?, app: AppEntry, shape: Shape, drawn: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { drawn.roundToPx() }
    val cover = rememberArt(art, app.component.packageName, ArtKind.Cover, px)
    if (cover != null) {
        Image(cover, null, modifier.clip(shape), contentScale = ContentScale.Crop)
    } else {
        AppIcon(app, shape, modifier)
    }
}

/** Reports where this tile sits, so opening its app can grow out of it (MainActivity.launch). */
fun Modifier.launchesFrom(state: LauncherState, origin: Origin, entry: AppEntry): Modifier =
    onGloballyPositioned { state.place(origin, entry, it.boundsInWindow().roundToIntRect().toAndroidRect()) }

/**
 * Focus: the tile grows a little and an accent ring, a hair away from its edge, pulses slowly.
 * [corner] is the tile's corner radius; null means the tile is a circle.
 */
@Composable
fun Modifier.focusTile(focused: Boolean, corner: Dp?, lifted: Boolean = false): Modifier {
    val scale by animateFloatAsState(
        targetValue = when {
            lifted -> Dimens.LIFT_SCALE
            focused -> Dimens.FOCUS_SCALE
            else -> 1f
        },
        animationSpec = if (LocalMotion.current) spring(dampingRatio = 0.7f, stiffness = 700f) else snap(),
        label = "focusScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.focusRing(focused, corner, steady = lifted)
}

/**
 * The accent ring alone, for things that shouldn't grow (rows in a sheet). A [steady] ring
 * doesn't pulse: a tile being moved holds still so it reads as picked up. With animations off,
 * or the pulse set to Off, no ring pulses. With the glow effect on, a soft accent glow lies
 * outside the ring and breathes with it.
 */
@Composable
fun Modifier.focusRing(focused: Boolean, corner: Dp?, steady: Boolean = false): Modifier {
    val accent = LocalPalette.current.accent
    val glow = LocalEffects.current.glow
    val period = LocalPulse.current.millis
    // Only the focused tile runs the pulse; the alpha is read at draw time so it never recomposes.
    val pulse: State<Float>? = if (focused && !steady && period != null && LocalMotion.current) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            // Slow (1.8 s each way) is half the first speed (owner's ask), a breath, not a blink;
            // eased like one too, lingering at full and at faint instead of bouncing off them.
            animationSpec = infiniteRepeatable(tween(period, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
    } else {
        null
    }
    val glowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    return drawBehind {
        if (!focused) return@drawBehind
        val breath = pulse?.value ?: 1f
        val stroke = Dimens.ring.toPx()
        val out = Dimens.ringGap.toPx() + stroke / 2
        if (glow) {
            // OUTER blurs only outside the shape, so the gap between tile and ring stays clean.
            glowPaint.maskFilter = glowPaint.maskFilter ?: BlurMaskFilter(Dimens.glow.toPx(), BlurMaskFilter.Blur.OUTER)
            glowPaint.color = accent.copy(alpha = Dimens.GLOW_ALPHA * breath).toArgb()
            drawShape(glowPaint, corner, grow = out + stroke / 2)
        }
        val color = accent.copy(alpha = breath)
        if (corner == null) {
            drawCircle(color, radius = size.minDimension / 2 + out, style = Stroke(stroke))
        } else {
            drawRoundRect(
                color,
                topLeft = Offset(-out, -out),
                size = Size(size.width + 2 * out, size.height + 2 * out),
                cornerRadius = CornerRadius(corner.toPx() + out),
                style = Stroke(stroke),
            )
        }
    }
}

/** The tile's own shape, grown by [grow] on every side and moved down by [drop], on the native canvas. */
private fun DrawScope.drawShape(paint: Paint, corner: Dp?, grow: Float, drop: Float = 0f) = drawIntoCanvas {
    val c = it.nativeCanvas
    if (corner == null) {
        c.drawCircle(size.width / 2, size.height / 2 + drop, size.minDimension / 2 + grow, paint)
    } else {
        val r = corner.toPx() + grow
        c.drawRoundRect(-grow, -grow + drop, size.width + grow, size.height + grow + drop, r, r, paint)
    }
}

/**
 * Tile depth (an effect). On a dark background a shadow can't show, so the tile catches light
 * instead: a hairline along its top edge fading out by the middle. On the light one it casts a
 * soft shadow. [corner] as for [focusTile]; put it after any scaling, so it scales with the tile.
 */
@Composable
fun Modifier.depth(corner: Dp?): Modifier {
    if (!LocalEffects.current.depth) return this
    if (LocalPalette.current.dark) {
        return drawWithContent {
            drawContent()
            val w = Dimens.rim.toPx()
            val light = Brush.verticalGradient(0f to Color.White.copy(alpha = Dimens.RIM_ALPHA), 0.5f to Color.Transparent)
            if (corner == null) {
                drawCircle(light, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
            } else {
                drawRoundRect(
                    light,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(corner.toPx() - w / 2),
                    style = Stroke(w),
                )
            }
        }
    }
    val shadow = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    return drawBehind {
        shadow.maskFilter = shadow.maskFilter ?: BlurMaskFilter(Dimens.shadowBlur.toPx(), BlurMaskFilter.Blur.NORMAL)
        shadow.color = Color.Black.copy(alpha = Dimens.SHADOW_ALPHA).toArgb()
        drawShape(shadow, corner, grow = 0f, drop = Dimens.shadowDrop.toPx())
    }
}

/**
 * A controller button: a filled disc with its mark knocked out. A letter button's mark is its
 * letter; the two menu buttons' are the shapes the Portal prints on them (owner's ask): − (Select,
 * left) a square and + (Start, right) a triangle pointing right, both slightly rounded.
 */
@Composable
fun ButtonGlyph(button: String) {
    val p = LocalPalette.current
    Box(
        Modifier.size(Dimens.glyph).background(p.text, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (button == "−" || button == "+") {
            MenuMark(triangle = button == "+", color = p.background)
        } else {
            BasicText(button, style = LocalType.current.caption.copy(color = p.background, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * A menu button's mark, drawn as a path so it stays crisp at any scale. The triangle's corners
 * are rounded by stroking it with round joins over a copy inset by the radius; it is taller than
 * the square and nudged right of center, so the two look the same size and centered.
 */
@Composable
private fun MenuMark(triangle: Boolean, color: Color) {
    Canvas(Modifier.size(Dimens.glyph)) {
        val r = size.minDimension * MENU_CORNER
        if (triangle) {
            val h = size.height * 0.62f
            val w = h * 0.9f
            val left = (size.width - w) / 2 + w * 0.1f
            val top = (size.height - h) / 2
            val path = Path().apply {
                moveTo(left + r, top + r)
                lineTo(left + w - r, top + h / 2)
                lineTo(left + r, top + h - r)
                close()
            }
            drawPath(path, color)
            drawPath(path, color, style = Stroke(width = 2 * r, join = StrokeJoin.Round))
        } else {
            val side = size.minDimension * 0.5f
            drawRoundRect(color, Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side), CornerRadius(r))
        }
    }
}

private const val MENU_CORNER = 0.09f

/** The bottom bar: a hairline, then a note and the left-hand hints, and the right-hand hints at the right. */
@Composable
fun HintBar(hints: List<Hint>, note: String = "") {
    val p = LocalPalette.current
    val t = LocalType.current
    Column(Modifier.fillMaxWidth().height(Dimens.hintBar).padding(horizontal = Dimens.edge)) {
        Canvas(Modifier.fillMaxWidth().height(Space.xs)) {
            drawLine(p.rule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1f)
        }
        // Each hint on the side of the Portal its button is on (owner's ask).
        val (left, right) = hints.partition { it.onLeft }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicText(note, style = t.hint.copy(color = p.muted), maxLines = 1)
            if (note.isNotEmpty() && left.isNotEmpty()) Spacer(Modifier.width(Space.xl))
            Hints(left)
            Spacer(Modifier.weight(1f))
            Hints(right)
        }
    }
}

/**
 * A button hint. A label that changes in place (Favorite / Unfavorite) keeps the room of its
 * longest form, [room], so the glyph holds still and only the text changes (owner's ask).
 */
class Hint(val button: String, val label: String, val room: String = label) {
    /** − (Select) is the one hinted button on the Portal's left; its hint goes on the left too. */
    val onLeft get() = button == "−"
}

/**
 * Button hints at 80% (owner's ask), each a glyph and its label: in a row 24 dp apart, or
 * [stacked] 4 dp apart, right-aligned or, for the left side, left-aligned. A and B are never
 * hinted: the owner takes them as known (open, back).
 */
@Composable
fun Hints(hints: List<Hint>, modifier: Modifier = Modifier, stacked: Boolean = false, alignEnd: Boolean = true) {
    if (!LocalHome.current.hints || hints.isEmpty()) return
    val p = LocalPalette.current
    val t = LocalType.current
    @Suppress("NAME_SHADOWING")
    val modifier = modifier.graphicsLayer { alpha = HINT_ALPHA }
    val each = @Composable {
        hints.forEach { hint ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ButtonGlyph(hint.button)
                Box {
                    BasicText(hint.room, style = t.hint.copy(color = Color.Transparent))
                    BasicText(hint.label, style = t.hint.copy(color = p.text))
                }
            }
        }
    }
    if (stacked) {
        Column(
            modifier,
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) { each() }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xl)) { each() }
    }
}

private const val HINT_ALPHA = 0.8f

/** The time, ticking on the minute: in the system's 12/24-hour format unless Settings → Clock fixes one. */
@Composable
fun Clock(modifier: Modifier = Modifier, style: TextStyle = LocalType.current.body.copy(color = LocalPalette.current.text)) {
    val context = LocalContext.current
    val clock = LocalHome.current.clock
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(MINUTE - now % MINUTE)
        }
    }
    val format = remember(context, clock) {
        clock.pattern?.let { SimpleDateFormat(it, Locale.getDefault()) } ?: DateFormat.getTimeFormat(context)
    }
    BasicText(
        format.format(Date(now)),
        modifier = modifier,
        style = style,
    )
}

private const val MINUTE = 60_000L

/** The All apps button's glyph: a 3×3 grid of dots, drawn rather than loaded. */
@Composable
fun AllAppsGlyph() {
    val color = LocalPalette.current.text
    Canvas(Modifier.size(Space.xl)) {
        val step = size.width / 3
        val r = step * 0.28f
        for (row in 0..2) for (col in 0..2) {
            drawCircle(color, r, Offset(step * (col + 0.5f), step * (row + 0.5f)))
        }
    }
}

/**
 * A Material Symbols glyph (Rounded, filled) from `res/drawable/ic_sym_*`: vector, so crisp at
 * any size, and tinted here so it follows the palette.
 */
@Composable
fun Symbol(@DrawableRes id: Int, color: Color, modifier: Modifier = Modifier) {
    Image(painterResource(id), contentDescription = null, modifier.size(Space.xl), colorFilter = ColorFilter.tint(color))
}
