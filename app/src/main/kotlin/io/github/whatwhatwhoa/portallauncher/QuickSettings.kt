package io.github.whatwhatwhoa.portallauncher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The device's everyday controls, read from and written to Android (`decisions/M7-quick-settings.md`).
 * Brightness and Do not disturb need a one-time grant each, asked for from the panel when first
 * used; volume and the media key need none.
 */
class DeviceControls(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val resolver = context.contentResolver

    /** The brightness slider, 0..1: the raw 0..255 through [GAMMA], so each step looks alike. */
    var brightness by mutableFloatStateOf(0f)
        private set
    var autoBrightness by mutableStateOf(false)
        private set
    var volume by mutableIntStateOf(0)
        private set
    val volumeMax: Int = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    /** Do not disturb: anything quieter than letting everything through. */
    var quiet by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var canWrite by mutableStateOf(false)
        private set
    var canQuiet by mutableStateOf(false)
        private set

    /** Everything again: the values change outside the launcher (the pull-down, the volume keys). */
    fun read() {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_RAW / 2)
        brightness = (raw.toFloat() / MAX_RAW).pow(1 / GAMMA)
        autoBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        quiet = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        playing = audio.isMusicActive
        canWrite = Settings.System.canWrite(context)
        canQuiet = notifications.isNotificationPolicyAccessGranted
    }

    /** One step of the slider; moving it by hand turns auto-brightness off, as the pull-down does. */
    fun stepBrightness(by: Int) = moveBrightnessTo(brightness + by * BRIGHTNESS_STEP)

    fun moveBrightnessTo(position: Float) {
        if (!canWrite) return
        val p = position.coerceIn(0f, 1f)
        val raw = (MAX_RAW * p.pow(GAMMA)).roundToInt().coerceIn(1, MAX_RAW)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)
        read()
    }

    fun toggleAutoBrightness() {
        if (!canWrite) return
        val mode = if (autoBrightness) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        read()
    }

    fun stepVolume(by: Int) = moveVolumeTo(volume + by)

    fun moveVolumeTo(level: Int) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, volumeMax), 0)
        read()
    }

    /** Priority only while on, as the pull-down's tile does. */
    fun toggleQuiet() {
        if (!canQuiet) return
        notifications.setInterruptionFilter(
            if (quiet) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        read()
    }

    /** Play/pause to whatever app last played; whether it plays now shows a moment later. */
    fun playPause() {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
    }

    private companion object {
        const val MAX_RAW = 255
        const val GAMMA = 2.2f
        const val BRIGHTNESS_STEP = 0.05f
    }
}

/** The panel's rows, top to bottom; the last two are rows of buttons. */
enum class QuickRow { Brightness, Volume, Toggles, Links }

enum class QuickToggle(val label: String, @DrawableRes val icon: Int) {
    AutoBrightness("Auto brightness", R.drawable.ic_sym_auto_brightness),
    Quiet("Do not disturb", R.drawable.ic_sym_dnd),
    Media("Play / pause", R.drawable.ic_sym_play),
}

/** Shortcuts to the system's own pages, for what a launcher may not change itself. */
enum class QuickLink(val label: String, @DrawableRes val icon: Int, val intent: () -> Intent) {
    Wifi("Wi-Fi", R.drawable.ic_sym_wifi, { Intent(Settings.ACTION_WIFI_SETTINGS) }),
    Bluetooth("Bluetooth", R.drawable.ic_sym_bluetooth, { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }),
    Battery("Battery", R.drawable.ic_sym_battery, { Intent(Intent.ACTION_POWER_USAGE_SUMMARY) }),
    // AYN's performance, fan and controller settings; it has no launcher entry of its own.
    Ayn("AYN settings", R.drawable.ic_sym_gamepad, {
        Intent().setComponent(ComponentName("org.lineageos.settings.ayn", "org.lineageos.settings.ayn.AynSettingsActivity"))
    }),
    Android("Android settings", R.drawable.ic_sym_settings, { Intent(Settings.ACTION_SETTINGS) }),
}

/** The page where the owner lets the launcher change brightness. */
fun writeSettingsGrant() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${BuildConfig.APPLICATION_ID}".toUri())

fun quietGrant() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

/**
 * The quick panel, from the top like the pull-down, over Home dimmed as the options sheet dims
 * it: brightness and volume sliders, then toggles, then shortcuts, each an icon as in Android's
 * own panel (owner, 2026-10-25); the focused one is named on the line below. Up and down move
 * between rows, left and right change a slider or move along a row, A toggles or opens, B or −
 * closes.
 */
@Composable
fun AnimatedVisibilityScope.QuickPanel(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val motion = LocalMotion.current
    val controls = state.controls
    Box(
        Modifier.fillMaxSize()
            .background(p.background.copy(alpha = SCRIM))
            .clickable(interactionSource = null, indication = null) { state.closeQuick() },
    ) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .animateEnterExit(
                    enter = if (motion) slideInVertically { -it / 4 } else EnterTransition.None,
                    exit = if (motion) slideOutVertically { -it / 4 } else ExitTransition.None,
                )
                .width(QUICK_WIDTH)
                .background(p.surface, RoundedCornerShape(bottomStart = Space.xl, bottomEnd = Space.xl))
                .clickable(interactionSource = null, indication = null) {}
                .padding(start = Space.xxl, end = Space.xxl, top = Dimens.sheetTop, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            val focusedRow = state.quickFocus
            SliderRow(
                R.drawable.ic_sym_brightness,
                controls.brightness,
                "${(controls.brightness * 100).roundToInt()}%",
                missing = !controls.canWrite,
                focused = focusedRow == QuickRow.Brightness,
                onTap = { state.tapQuickSlider(QuickRow.Brightness, it) },
            )
            SliderRow(
                R.drawable.ic_sym_volume,
                controls.volume.toFloat() / controls.volumeMax.coerceAtLeast(1),
                "${(controls.volume * 100f / controls.volumeMax.coerceAtLeast(1)).roundToInt()}%",
                missing = false,
                focused = focusedRow == QuickRow.Volume,
                onTap = { state.tapQuickSlider(QuickRow.Volume, it) },
            )
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                QuickToggle.entries.forEachIndexed { i, toggle ->
                    val on = when (toggle) {
                        QuickToggle.AutoBrightness -> controls.autoBrightness
                        QuickToggle.Quiet -> controls.quiet
                        QuickToggle.Media -> controls.playing
                    }
                    // Play shows pause while something plays, as a player's own button does.
                    val icon = if (toggle == QuickToggle.Media && on) R.drawable.ic_sym_pause else toggle.icon
                    IconChip(icon, on, focused = focusedRow == QuickRow.Toggles && i == state.quickColumn) {
                        state.tapQuick(QuickRow.Toggles, i)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                QuickLink.entries.forEachIndexed { i, link ->
                    IconChip(link.icon, on = false, focused = focusedRow == QuickRow.Links && i == state.quickColumn) {
                        state.tapQuick(QuickRow.Links, i)
                    }
                }
            }
            BasicText(state.quickNote, Modifier.padding(top = Space.xs), style = t.caption.copy(color = p.muted), maxLines = 1)
        }
    }
}

/**
 * A slider row: its icon, a drawn track filled to [value], the value in words. Without the grant
 * it needs, the track gives way to how to get it.
 */
@Composable
private fun SliderRow(@DrawableRes icon: Int, value: Float, shown: String, missing: Boolean, focused: Boolean, onTap: (Float) -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(
        Modifier.fillMaxWidth()
            .height(Dimens.sheetRow)
            .focusRing(focused, Space.m)
            .background(if (focused) p.rule else Color.Transparent, RoundedCornerShape(Space.m))
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Symbol(icon, p.text)
        if (missing) {
            BasicText("A: let the launcher change it", Modifier.weight(1f), style = t.body.copy(color = p.muted), maxLines = 1)
            return@Row
        }
        Canvas(
            Modifier.weight(1f).height(Space.xl).pointerInput(Unit) {
                detectTapGestures { onTap(it.x / size.width) }
            },
        ) {
            val h = TRACK.toPx()
            val top = (size.height - h) / 2
            val radius = CornerRadius(h / 2)
            drawRoundRect(p.rule, topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = radius)
            val filled = size.width * value.coerceIn(0f, 1f)
            if (filled > 0f) {
                drawRoundRect(p.accent, topLeft = Offset(0f, top), size = Size(filled, h), cornerRadius = radius)
            }
        }
        BasicText(shown, Modifier.width(SLIDER_VALUE), style = t.body.copy(color = if (focused) p.text else p.muted), maxLines = 1)
    }
}

/** A round icon button, the dock's size; [on] fills it, as Android's tiles fill when on. */
@Composable
private fun IconChip(@DrawableRes icon: Int, on: Boolean, focused: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(Dimens.dockButton)
            .focusRing(focused, Dimens.dockButton / 2)
            .background(if (on) p.text else p.rule, CircleShape)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(icon, if (on) p.background else p.text)
    }
}

private val QUICK_WIDTH = 760.dp
private val SLIDER_VALUE = 56.dp
private val TRACK = 6.dp
private const val SCRIM = 0.6f
