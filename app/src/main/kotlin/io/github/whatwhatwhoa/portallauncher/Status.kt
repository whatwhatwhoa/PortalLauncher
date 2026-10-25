package io.github.whatwhatwhoa.portallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The status band: the clock in the top-left corner, Wi-Fi and battery in the top-right (owner's
 * ask). Drawn once over both screens, so it stays put while All apps slides in. The glyphs are
 * paths drawn in code, crisp at any density.
 */
@Composable
fun StatusCluster(modifier: Modifier = Modifier) {
    val wifi = rememberWifiLevel()
    val battery = rememberBattery()
    Box(modifier.fillMaxWidth().padding(top = Dimens.statusTop, start = Dimens.statusEdge, end = Dimens.statusEdge)) {
        Clock(Modifier.align(Alignment.CenterStart))
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.statusGap),
        ) {
            WifiGlyph(wifi)
            if (battery != null) BatteryGlyph(battery)
        }
    }
}

/**
 * Wi-Fi as the modern filled wedge: a dim quarter-circle with the signal filled from the apex
 * out. [level] is 0..[WIFI_LEVELS], or null when there is no Wi-Fi network (dim wedge only).
 */
@Composable
private fun WifiGlyph(level: Int?) {
    val p = LocalPalette.current
    Canvas(Modifier.size(WIFI_RADIUS * 2 * HALF_SQRT2, WIFI_RADIUS)) {
        val apex = Offset(size.width / 2, size.height)
        val corner = WIFI_CORNER.toPx()
        drawPath(wedge(apex, size.height, corner), p.text.copy(alpha = TRACK_ALPHA))
        if (level != null && level > 0) {
            val reach = WIFI_REACH[level.coerceAtMost(WIFI_LEVELS) - 1]
            drawPath(wedge(apex, size.height * reach, corner), p.text)
        }
    }
}

/**
 * A quarter-circle sector pointing down at [apex], opening up, with every corner rounded by
 * [r]. Screen angles: clockwise from +x, so straight up is 270°, the edges are 225° and 315°.
 */
private fun wedge(apex: Offset, radius: Float, r: Float): Path {
    // The outer fillets sit inside the arc, so their centers turn in by t from each edge.
    val t = Math.toDegrees(asin(r / (radius - r)).toDouble()).toFloat()
    fun polar(deg: Float, dist: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return apex + Offset((cos(a) * dist).toFloat(), (sin(a) * dist).toFloat())
    }
    val right = polar(-(45f + t), radius - r)
    val left = polar(-(135f - t), radius - r)
    val tip = apex + Offset(0f, -r * SQRT2)
    return Path().apply {
        moveTo(apex.x + r * HALF_SQRT2, apex.y - r * HALF_SQRT2)
        arcTo(Rect(right, r), 45f, -(90f + t), false)
        arcTo(Rect(apex, radius), -(45f + t), -(90f - 2 * t), false)
        arcTo(Rect(left, r), -(135f - t), -(90f + t), false)
        arcTo(Rect(tip, r), 135f, -90f, false)
        close()
    }
}

/**
 * A horizontal battery with the percentage inside. The charge fills from the left (accent while
 * charging); the number is knocked out where it crosses the fill, so it reads at any level. With
 * Battery percent off, only the fill.
 */
@Composable
private fun BatteryGlyph(battery: Battery) {
    val p = LocalPalette.current
    val percent = LocalHome.current.batteryPercent
    val style = LocalType.current.caption.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.size(BATTERY_WIDTH + BATTERY_CAP_GAP + BATTERY_CAP_WIDTH, BATTERY_HEIGHT)) {
        // Whole pixels, so the body's straight edges land on the pixel grid.
        val w = BATTERY_WIDTH.toPx().roundToInt().toFloat()
        val h = size.height.roundToInt().toFloat()
        val radius = CornerRadius(BATTERY_RADIUS.toPx())
        val fill = if (battery.charging) p.accent else p.text
        val level = w * battery.percent / 100f
        drawRoundRect(p.text.copy(alpha = TRACK_ALPHA), size = Size(w, h), cornerRadius = radius)
        clipRect(right = level) { drawRoundRect(fill, size = Size(w, h), cornerRadius = radius) }
        val capH = BATTERY_CAP_HEIGHT.toPx().roundToInt().toFloat()
        drawRoundRect(
            p.text.copy(alpha = if (battery.percent == 100) 1f else TRACK_ALPHA),
            topLeft = Offset(w + BATTERY_CAP_GAP.toPx(), ((h - capH) / 2).roundToInt().toFloat()),
            size = Size(BATTERY_CAP_WIDTH.toPx(), capH),
            cornerRadius = CornerRadius(BATTERY_CAP_WIDTH.toPx() / 2),
        )
        if (!percent) return@Canvas
        val text = measurer.measure("${battery.percent}", style)
        val at = Offset(
            ((w - text.size.width) / 2).roundToInt().toFloat(),
            ((h - text.size.height) / 2).roundToInt().toFloat(),
        )
        clipRect(right = level) { drawText(text, p.background, at) }
        clipRect(left = level) { drawText(text, p.text, at) }
    }
}

data class Battery(val percent: Int, val charging: Boolean)

/** The battery from the sticky ACTION_BATTERY_CHANGED broadcast; null until it first arrives. */
@Composable
private fun rememberBattery(): Battery? {
    val context = LocalContext.current
    var battery by remember { mutableStateOf<Battery?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                battery = batteryFrom(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { battery = batteryFrom(it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return battery
}

private fun batteryFrom(intent: Intent): Battery? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Battery((level * 100f / scale).roundToInt(), charging)
}

/**
 * The Wi-Fi signal as 0..[WIFI_LEVELS], or null without a Wi-Fi network. The RSSI rides on the
 * network's capabilities, which the system re-sends when the signal changes noticeably.
 */
@Composable
private fun rememberWifiLevel(): Int? {
    val context = LocalContext.current
    var level by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifi = context.getSystemService(WifiManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val raw = wifi.calculateSignalLevel(caps.signalStrength)
                level = (raw * WIFI_LEVELS + wifi.maxSignalLevel / 2) / maxOf(1, wifi.maxSignalLevel)
            }

            override fun onLost(network: Network) {
                level = null
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }
    return level
}

private const val SQRT2 = 1.4142135f
private val HALF_SQRT2 = sqrt(2f) / 2
private const val TRACK_ALPHA = 0.3f

private const val WIFI_LEVELS = 4
/** How far the fill reaches, as a share of the wedge's radius, for levels 1..4. */
private val WIFI_REACH = floatArrayOf(0.42f, 0.62f, 0.81f, 1f)
private val WIFI_RADIUS = 18.dp
private val WIFI_CORNER = 2.dp

private val BATTERY_WIDTH = 40.dp
private val BATTERY_HEIGHT = 20.dp
private val BATTERY_RADIUS = 6.dp
private val BATTERY_CAP_GAP = 2.dp
private val BATTERY_CAP_WIDTH = 3.dp
private val BATTERY_CAP_HEIGHT = 8.dp
