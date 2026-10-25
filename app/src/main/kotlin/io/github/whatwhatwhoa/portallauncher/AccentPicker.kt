package io.github.whatwhatwhoa.portallauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// The picker's rows: two of swatches, then the custom hue and intensity sliders.
const val SWATCH_ROWS = 2
const val SWATCH_COLUMNS = 4
const val HUE_ROW = 2
const val INTENSITY_ROW = 3
const val ACCENT_ROWS = 4
const val HUE_STEP = 5f
const val CHROMA_STEP = 0.01f

/** Where a custom color starts its chroma: White has none, so a middling one. */
val Accent.customChroma get() = if (hue == null) 0.12f else chroma

/** A custom accent, kept to whole degrees and hundredths so stepping back lands on a preset exactly. */
fun customAccent(hue: Float, chroma: Float) = Accent(
    Math.floorMod(hue.roundToInt(), 360).toFloat(),
    (chroma.coerceIn(Accent.MIN_CHROMA, Accent.MAX_CHROMA) * 100).roundToInt() / 100f,
)

/**
 * The accent page of Launcher Settings: the eight presets as swatches, the one in use marked,
 * then *Custom*, a hue and an intensity slider whose tracks show the colors they lead to at this
 * theme's lightness. Everything around it (the focus ring, the Accent dot) changes live.
 */
@Composable
fun AccentPicker(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val accent = state.look.accent
    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
        for (r in 0 until SWATCH_ROWS) {
            Row(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                for (c in 0 until SWATCH_COLUMNS) {
                    val preset = AccentPreset.entries[r * SWATCH_COLUMNS + c]
                    Swatch(
                        preset.accent.on(p.dark),
                        picked = accent.preset == preset,
                        focused = state.accentRow == r && state.accentColumn == c,
                    ) { state.tapSwatch(preset) }
                }
            }
        }
        BasicText(
            if (accent.preset == null) "Custom (in use)" else "Custom",
            Modifier.padding(start = Space.l, top = Space.s),
            style = t.caption.copy(color = p.muted),
        )
        val hue = accent.hue ?: 0f
        val chroma = accent.customChroma
        ColorSlider(
            "Hue",
            focused = state.accentRow == HUE_ROW,
            position = hue / 360f,
            track = List(HUE_STOPS + 1) { Accent.color(p.dark, chroma.coerceAtLeast(TRACK_CHROMA), it * 360f / HUE_STOPS) },
            onTap = { state.tapAccentSlider(HUE_ROW, it) },
        )
        ColorSlider(
            "Intensity",
            focused = state.accentRow == INTENSITY_ROW,
            position = (chroma - Accent.MIN_CHROMA) / (Accent.MAX_CHROMA - Accent.MIN_CHROMA),
            track = List(HUE_STOPS + 1) {
                Accent.color(p.dark, Accent.MIN_CHROMA + it * (Accent.MAX_CHROMA - Accent.MIN_CHROMA) / HUE_STOPS, hue)
            },
            onTap = { state.tapAccentSlider(INTENSITY_ROW, it) },
        )
    }
}

/** A round swatch, ringed when focused; the one in use carries a dot in the background color. */
@Composable
private fun Swatch(color: Color, picked: Boolean, focused: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(SWATCH)
            .focusRing(focused, SWATCH / 2)
            .background(color, CircleShape)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) Box(Modifier.size(Space.m).background(p.background, CircleShape))
    }
}

/**
 * A slider row like the quick panel's: the name, then a track painted with the colors it leads
 * to and a ring where the accent sits. Left and right step it; a tap on the track sets it there.
 */
@Composable
private fun ColorSlider(name: String, focused: Boolean, position: Float, track: List<Color>, onTap: (Float) -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(
        Modifier.fillMaxWidth()
            .height(Dimens.sheetRow)
            .focusRing(focused, Space.m)
            .background(if (focused) p.rule else Color.Transparent, RoundedCornerShape(Space.m))
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        BasicText(name, Modifier.width(SLIDER_LABEL), style = t.body.copy(color = p.text), maxLines = 1)
        Canvas(
            Modifier.weight(1f).height(Space.xl).pointerInput(Unit) {
                detectTapGestures { onTap(it.x / size.width) }
            },
        ) {
            val h = TRACK.toPx()
            val radius = THUMB.toPx()
            // The track is inset by the thumb's radius, so the thumb never pokes out at the ends.
            val left = radius
            val width = size.width - radius * 2
            val top = (size.height - h) / 2
            drawRoundRect(
                Brush.horizontalGradient(track, startX = left, endX = left + width),
                topLeft = Offset(left, top),
                size = Size(width, h),
                cornerRadius = CornerRadius(h / 2),
            )
            val x = left + width * position.coerceIn(0f, 1f)
            drawCircle(p.accent, radius, Offset(x, size.height / 2))
            drawCircle(p.text, radius, Offset(x, size.height / 2), style = Stroke(RING.toPx()))
        }
    }
}

private val SWATCH = 40.dp
private val SLIDER_LABEL = 96.dp
private val TRACK = 8.dp
private val THUMB = 9.dp
private val RING = 2.dp
private const val HUE_STOPS = 24
// The hue track keeps some color even while the accent is White or near gray, so it reads as hues.
private const val TRACK_CHROMA = 0.10f
