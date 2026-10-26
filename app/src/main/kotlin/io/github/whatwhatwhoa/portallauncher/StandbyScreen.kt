package io.github.whatwhatwhoa.portallauncher

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Standby (F19): a rest without locking, like an always-on display. Black, the clock large and
 * soft with the date and battery under it; MainActivity keeps the screen on meanwhile. The block
 * moves to a new spot each minute so the OLED never holds one picture. Four presses of the same
 * button leave it (touches, sticks and triggers don't count); the dots under it fill as they
 * come, so a press that seems lost isn't. Once a press counts, a line under the dots says how to
 * wake (F30); it stays out of sight otherwise, and with *Button hints* off.
 */
@Composable
fun StandbyScreen(state: LauncherState) {
    val t = LocalType.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(MINUTE - now % MINUTE)
        }
    }
    val minute = now / MINUTE
    val (dx, dy) = remember(minute) { Random.nextFloat() * 2 - 1 to Random.nextFloat() * 2 - 1 }
    val date = remember(minute) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault()).format(Date(now))
    }
    val battery = rememberBattery()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.align(Alignment.Center).offset(x = DRIFT_X * dx, y = DRIFT_Y * dy),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Clock(style = t.display.copy(fontSize = t.display.fontSize * CLOCK_SCALE, lineHeight = t.display.lineHeight * CLOCK_SCALE, color = BRIGHT))
            BasicText(date, style = t.title.copy(color = DIM))
            if (battery != null) {
                Spacer(Modifier.height(Space.xs))
                BasicText(
                    "${battery.percent}%" + if (battery.charging) " · Charging" else "",
                    style = t.body.copy(color = DIM),
                )
            }
            Spacer(Modifier.height(Space.xl))
            WakeDots(state.wakePresses, state.wakePressesNeeded)
            if (LocalHome.current.hints) {
                // Faded in place rather than added, so the block doesn't jump when it appears.
                val shown by animateFloatAsState(if (state.wakePresses > 0) 1f else 0f, tween(FADE_MS), label = "wakeHint")
                Spacer(Modifier.height(Space.m))
                BasicText(
                    "Press the same button ${state.wakePressesNeeded} times to wake",
                    Modifier.alpha(shown),
                    style = t.caption.copy(color = DIM),
                )
            }
        }
    }
}

/** One ring per press needed to wake; each press fills one. */
@Composable
private fun WakeDots(presses: Int, needed: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        repeat(needed) { i ->
            Canvas(Modifier.size(DOT)) {
                if (i < presses) drawCircle(BRIGHT) else drawCircle(DIM, style = Stroke(1.5.dp.toPx()), radius = size.minDimension / 2 - 1.dp.toPx())
            }
        }
    }
}

/** A small pill at the top after a Home press: "Hold Home for standby", or that standby went off. */
@Composable
fun StandbyHint(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BasicText(
        text,
        modifier.padding(top = Space.xl).background(p.surface, CircleShape).padding(horizontal = Space.l, vertical = Space.xs),
        style = LocalType.current.caption.copy(color = p.text),
    )
}

// White at two strengths, softer than Home's text: the first build's 70% and 45% at 5%
// brightness were too dark, 90% and 65% at the owner's brightness then a fifth too bright.
private val BRIGHT = Color.White.copy(alpha = 0.72f)
private val DIM = Color.White.copy(alpha = 0.52f)
// Half the first build's 60%, so fewer pixels are lit (owner, for power).
private const val CLOCK_SCALE = 0.3f
private const val MINUTE = 60_000L
// How far the block may wander from the center: well inside a 1032 × 600 dp canvas.
private val DRIFT_X = 160.dp
private val DRIFT_Y = 64.dp
private val DOT = 10.dp
private const val FADE_MS = 200
