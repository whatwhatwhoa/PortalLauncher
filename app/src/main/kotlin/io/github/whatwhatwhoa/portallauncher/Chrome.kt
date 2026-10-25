package io.github.whatwhatwhoa.portallauncher

import android.graphics.drawable.AdaptiveIconDrawable
import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/**
 * An app icon drawn straight onto the canvas at the size it is shown: vector layers render at
 * full resolution and bitmap layers scale down only. An adaptive icon fills the shape edge to
 * edge (our mask, not the system's); a legacy icon sits inset on the surface colour.
 */
@Composable
fun AppIcon(entry: AppEntry, shape: Shape, modifier: Modifier = Modifier) {
    val icon = remember(entry) { entry.newIcon() }
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
 * Focus: the tile grows a little and an accent ring, a hair away from its edge, pulses slowly.
 * [corner] is the tile's corner radius; null means the tile is a circle.
 */
@Composable
fun Modifier.focusTile(focused: Boolean, corner: Dp?): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (focused) Dimens.FOCUS_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "focusScale",
    )
    val accent = LocalPalette.current.accent
    // Only the focused tile runs the pulse; the alpha is read at draw time so it never recomposes.
    val pulse: State<Float>? = if (focused) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
    } else {
        null
    }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.drawBehind {
        if (pulse == null) return@drawBehind
        val color = accent.copy(alpha = pulse.value)
        val stroke = Dimens.ring.toPx()
        val out = Dimens.ringGap.toPx() + stroke / 2
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

/** A controller button drawn as a filled disc with its letter knocked out. */
@Composable
fun ButtonGlyph(button: String) {
    val p = LocalPalette.current
    Box(
        Modifier.size(Dimens.glyph).background(p.text, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(button, style = LocalType.current.caption.copy(color = p.background, fontWeight = FontWeight.Bold))
    }
}

/** The bottom bar: a hairline, then a note on the left and button hints on the right. */
@Composable
fun HintBar(hints: List<Pair<String, String>>, note: String = "") {
    val p = LocalPalette.current
    val t = LocalType.current
    Column(Modifier.fillMaxWidth().height(Dimens.hintBar).padding(horizontal = Dimens.edge)) {
        Canvas(Modifier.fillMaxWidth().height(Space.xs)) {
            drawLine(p.rule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1f)
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            BasicText(note, style = t.hint.copy(color = p.muted), maxLines = 1)
            Spacer(Modifier.weight(1f))
            hints.forEach { (button, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    ButtonGlyph(button)
                    BasicText(label, style = t.hint.copy(color = p.text))
                }
            }
        }
    }
}

/** The time in the system's 12/24 h format, ticking on the minute. */
@Composable
fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(MINUTE - now % MINUTE)
        }
    }
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    BasicText(
        format.format(Date(now)),
        modifier = modifier,
        style = LocalType.current.body.copy(color = LocalPalette.current.text),
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
