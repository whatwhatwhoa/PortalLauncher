package io.github.whatwhatwhoa.portallauncher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** What a player says it is playing, read with notification access (F21). */
data class NowPlaying(
    val title: String?,
    val artist: String?,
    val album: String?,
    val positionMs: Long,
    val durationMs: Long,
    val seekable: Boolean,
) {
    val fraction get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

/**
 * The device's everyday controls, read from and written to Android (`decisions/M7-quick-settings.md`).
 * Brightness, Do not disturb and the seek bar need a one-time grant each, asked for from the panel
 * when first used; volume and the media keys need none.
 */
class DeviceControls(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val resolver = context.contentResolver
    // From the application, as LauncherApps in AppCatalog: its listener's binder outlives removal.
    private val sessions = context.applicationContext.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)

    /**
     * The brightness slider, 0..1: the 1..255 setting straight, as the display's own 0..1 level,
     * which is the percent the Portal's slider shows (owner's B11: its tint starts at 9%, value 25).
     */
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
    /** Notification access, which the seek bar needs (MediaListener). */
    var canSeeMedia by mutableStateOf(false)
        private set
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    /**
     * The panel's shortcuts this device has: a page no activity open to others answers (AYN's on
     * another handheld, or not exported) is left out rather than shown as a dead button.
     */
    val links: List<QuickLink> = QuickLink.entries.filter { link ->
        val intent = link.intent ?: return@filter true
        context.packageManager.resolveActivity(intent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.exported == true
    }

    // While the launcher is shown, Android tells it when the track or the playing changes, so
    // the status band's artist (F27) follows without polling; the panel still polls the position.
    private val main = Handler(Looper.getMainLooper())
    private var watching = false
    private var watched: MediaController? = null
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { readMedia() }
    private val trackChanged = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = readMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = readMedia()
        override fun onSessionDestroyed() = readMedia()
    }

    /** Everything again: the values change outside the launcher (the pull-down, the volume keys). */
    fun read() {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_RAW / 2)
        brightness = ((raw - 1).toFloat() / (MAX_RAW - 1)).coerceIn(0f, 1f)
        autoBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        quiet = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        playing = audio.isMusicActive
        canWrite = Settings.System.canWrite(context)
        canQuiet = notifications.isNotificationPolicyAccessGranted
        readMedia()
    }

    /**
     * Follows the player while the launcher is shown ([on]) and stops when it isn't. Again on
     * every return, since notification access may have been turned on in between.
     */
    fun watchMedia(on: Boolean) {
        if (on && !watching && notifications.isNotificationListenerAccessGranted(listener)) {
            watching = try {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
                true
            } catch (e: SecurityException) {
                false
            }
        } else if (!on && watching) {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
            watching = false
        }
        readMedia()
    }

    // The followed player's last report, so the panel's tick counts on from it without a call.
    private var lastState: PlaybackState? = null
    private var lastDuration = 0L

    /** The track and how far in: on opening, on returning, and whenever the player says it changed. */
    fun readMedia() {
        canSeeMedia = notifications.isNotificationListenerAccessGranted(listener)
        val player = player()
        follow(if (watching) player else null)
        val state = player?.playbackState
        // Once: each read is a call to the player that copies all of it, album art included.
        val metadata = player?.metadata
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        lastState = state
        lastDuration = duration
        nowPlaying = state?.let {
            NowPlaying(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.let { m ->
                    m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                },
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                positionMs = positionOf(it, duration),
                durationMs = duration,
                seekable = duration > 0 && it.actions and PlaybackState.ACTION_SEEK_TO != 0L,
            )
        }
        if (state != null) playing = state.state == PlaybackState.STATE_PLAYING
    }

    /**
     * The seek bar's tick while something plays: the position counted on from the followed
     * player's last report, which its callback keeps current, so the tick asks Android nothing.
     * Without a followed player it reads again, as it always did.
     */
    fun tickMedia() {
        if (watched == null) return readMedia()
        val state = lastState ?: return
        nowPlaying = nowPlaying?.copy(positionMs = positionOf(state, lastDuration))
    }

    // A player reports where it was at a moment, not where it is now.
    private fun positionOf(state: PlaybackState, duration: Long): Long {
        val running = if (state.state == PlaybackState.STATE_PLAYING) {
            ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        } else {
            0L
        }
        return (state.position + running).coerceIn(0L, duration.coerceAtLeast(0L))
    }

    /** One step of the seek bar, [by] milliseconds; shown at once, the player catches up. */
    fun seekBy(by: Long) {
        val now = nowPlaying ?: return
        seekTo((now.positionMs + by).toFloat() / now.durationMs.coerceAtLeast(1))
    }

    fun seekTo(fraction: Float) {
        val now = nowPlaying?.takeIf { it.seekable } ?: return
        val to = (now.durationMs * fraction.coerceIn(0f, 1f)).toLong()
        val player = player() ?: return
        player.transportControls.seekTo(to)
        nowPlaying = now.copy(positionMs = to)
    }

    private fun follow(player: MediaController?) {
        if (player?.sessionToken == watched?.sessionToken) return
        watched?.unregisterCallback(trackChanged)
        player?.registerCallback(trackChanged, main)
        watched = player
    }

    // The most recently active session that says where it is; none without notification access.
    private fun player(): MediaController? {
        if (!canSeeMedia) return null
        return try {
            sessions.getActiveSessions(listener).firstOrNull { it.playbackState != null }
        } catch (e: SecurityException) {
            null
        }
    }

    /** One step of the slider; moving it by hand turns auto-brightness off, as the pull-down does. */
    fun stepBrightness(by: Int) {
        // On the 5% marks (owner): from a level set elsewhere (22%), the first step lands on 20 or
        // 25. SNAP absorbs the 0..255 rounding, so a level on a mark counts as on it.
        val at = brightness / BRIGHTNESS_STEP
        val mark = if (by > 0) floor(at + SNAP) + by else ceil(at - SNAP) + by
        moveBrightnessTo(mark * BRIGHTNESS_STEP)
    }

    fun moveBrightnessTo(position: Float) {
        if (!canWrite) return
        val raw = 1 + (position.coerceIn(0f, 1f) * (MAX_RAW - 1)).roundToInt()
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

    /**
     * A media [key] to whatever app last played, as a headset's buttons do; whether it plays now
     * shows a moment later.
     */
    fun press(key: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
    }

    private companion object {
        const val MAX_RAW = 255
        const val BRIGHTNESS_STEP = 0.05f
        const val SNAP = 0.1f
    }
}

/**
 * The panel's rows, top to bottom: the player on one line (its buttons, then the seek bar as the
 * row's last stop), a hairline, then the settings. It opens on Brightness.
 */
enum class QuickRow { Media, Brightness, Volume, Toggles, Links }

/** The player row's column for the seek bar, after its buttons. */
val SEEK_COLUMN = QuickMedia.entries.size

/** The player's buttons (owner, F21); L1 and R1 skip from anywhere in the panel. */
enum class QuickMedia(val label: String, @DrawableRes val icon: Int, val key: Int) {
    Previous("Previous track", R.drawable.ic_sym_skip_previous, KeyEvent.KEYCODE_MEDIA_PREVIOUS),
    PlayPause("Play / pause", R.drawable.ic_sym_play, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    Next("Next track", R.drawable.ic_sym_skip_next, KeyEvent.KEYCODE_MEDIA_NEXT),
}

enum class QuickToggle(val label: String, @DrawableRes val icon: Int) {
    AutoBrightness("Auto brightness", R.drawable.ic_sym_auto_brightness),
    Quiet("Do not disturb", R.drawable.ic_sym_dnd),
}

/**
 * Shortcuts: the notification shade, then the system's own pages for what a launcher may not
 * change itself. Only the ones the device has show ([DeviceControls.links]).
 */
enum class QuickLink(val label: String, @DrawableRes val icon: Int, val intent: (() -> Intent)?) {
    // No page: the shade itself, pulled down by HomeHoldService (F13).
    Notifications("Notifications", R.drawable.ic_sym_notifications, null),
    Wifi("Wi-Fi", R.drawable.ic_sym_wifi, { Intent(Settings.ACTION_WIFI_SETTINGS) }),
    Bluetooth("Bluetooth", R.drawable.ic_sym_bluetooth, { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }),
    Battery("Battery", R.drawable.ic_sym_battery, { Intent(Intent.ACTION_POWER_USAGE_SUMMARY) }),
    // AYN's performance, fan and controller settings; it has no launcher entry of its own.
    Ayn("Controller settings", R.drawable.ic_sym_portal, { Intent().setComponent(AppCatalog.AYN_SETTINGS) }),
    Android("Android settings", R.drawable.ic_sym_settings, { Intent(Settings.ACTION_SETTINGS) }),
}

/** The page where the owner lets the launcher change brightness. */
fun writeSettingsGrant() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${BuildConfig.APPLICATION_ID}".toUri())

fun quietGrant() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

/** The launcher's own switch in Android's notification access, for the seek bar. */
fun mediaGrant() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
    ComponentName(BuildConfig.APPLICATION_ID, MediaListener::class.java.name).flattenToString(),
)

/**
 * The quick panel, from the top like the pull-down, over Home dimmed as the options sheet dims
 * it: the player on one line at its top edge (buttons, then the seek bar), a hairline, then
 * brightness and volume sliders, toggles and shortcuts, each an icon as in Android's own panel
 * (owner, 2026-10-25); the focused one is named on the line below. Up and down move between rows,
 * left and right change a slider or move along a row (on the seek bar they seek), A toggles or
 * opens, L1 and R1 skip tracks, B or Square closes.
 */
@Composable
fun AnimatedVisibilityScope.QuickPanel(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val motion = LocalMotion.current
    val controls = state.controls
    // Read on opening (LauncherState.openQuick); after that the time only moves while it plays.
    LaunchedEffect(controls.playing) {
        while (controls.playing) {
            delay(MEDIA_TICK)
            controls.tickMedia()
        }
    }
    Box(
        Modifier.fillMaxSize()
            .background(p.background.copy(alpha = SCRIM))
            .clickable(interactionSource = null, indication = null) { state.closeQuick() },
    ) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .animateEnterExit(
                    enter = if (motion) slideInVertically(Transit.enter()) { -it / 4 } else EnterTransition.None,
                    exit = if (motion) slideOutVertically(Transit.exit()) { -it / 4 } else ExitTransition.None,
                )
                .width(QUICK_WIDTH)
                .background(p.panel, RoundedCornerShape(bottomStart = Space.xl, bottomEnd = Space.xl))
                .clickable(interactionSource = null, indication = null) {}
                // The player sits at the top edge, between the status corners, so no sheetTop here.
                .padding(start = Space.xxl, end = Space.xxl, top = Space.l, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            val focusedRow = state.quickFocus
            // The bumpers drawn either side, as the Art… tabs have them: L1 and R1 skip too.
            val hints = LocalHome.current.hints
            // All of the player on one line (owner, F21): the seek bar takes what the buttons leave.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                if (hints) BumperGlyph(left = true)
                QuickMedia.entries.forEachIndexed { i, media ->
                    val on = media == QuickMedia.PlayPause && controls.playing
                    // Play shows pause while something plays, as a player's own button does.
                    val icon = if (on) R.drawable.ic_sym_pause else media.icon
                    IconChip(icon, on, focused = focusedRow == QuickRow.Media && i == state.quickColumn) {
                        state.tapQuick(QuickRow.Media, i)
                    }
                }
                if (hints) BumperGlyph(left = false)
                SeekBar(
                    controls.nowPlaying,
                    Modifier.weight(1f),
                    missing = !controls.canSeeMedia,
                    focused = focusedRow == QuickRow.Media && state.quickColumn == SEEK_COLUMN,
                    onTap = { state.tapQuickSlider(QuickRow.Media, it) },
                )
            }
            // A faint line between the player and the settings (owner, F21).
            Canvas(Modifier.fillMaxWidth().height(Space.xs)) {
                drawLine(p.rule, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
            }
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
                    }
                    IconChip(toggle.icon, on, focused = focusedRow == QuickRow.Toggles && i == state.quickColumn) {
                        state.tapQuick(QuickRow.Toggles, i)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                controls.links.forEachIndexed { i, link ->
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
        Track(value, Modifier.weight(1f), onTap)
        BasicText(shown, Modifier.width(SLIDER_VALUE), style = t.body.copy(color = if (focused) p.text else p.muted), maxLines = 1)
    }
}

/**
 * The seek bar, as a player draws it: the title and artist above (owner, F21), then the time in,
 * the track, the length. Without notification access only a small *Enable seek* at the line's
 * right end (the line below says what it needs); with nothing playing, or a player that can't
 * seek, the track is bare.
 */
@Composable
private fun SeekBar(now: NowPlaying?, modifier: Modifier, missing: Boolean, focused: Boolean, onTap: (Float) -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    if (missing) {
        Box(modifier, contentAlignment = Alignment.CenterEnd) {
            BasicText(
                "Enable seek",
                Modifier.focusRing(focused, Space.l)
                    .background(p.rule, RoundedCornerShape(Space.l))
                    .clickable(interactionSource = null, indication = null) { onTap(0f) }
                    .padding(horizontal = Space.m, vertical = Space.xs),
                style = t.caption.copy(color = if (focused) p.text else p.muted),
                maxLines = 1,
            )
        }
        return
    }
    val time = t.caption.copy(color = if (focused) p.text else p.muted)
    Column(
        modifier
            .height(Dimens.dockButton)
            .focusRing(focused, Space.m)
            .background(if (focused) p.rule else Color.Transparent, RoundedCornerShape(Space.m))
            .padding(horizontal = Space.m),
        verticalArrangement = Arrangement.Center,
    ) {
        // Title, then the artist muted, one line that ellipsizes, as a player's notification has it.
        val info = buildAnnotatedString {
            val title = now?.title
            if (title == null) {
                withStyle(SpanStyle(color = p.muted)) { append("Nothing playing") }
            } else {
                append(title)
                now.artist?.let { withStyle(SpanStyle(color = p.muted, fontWeight = FontWeight.Normal)) { append(" · $it") } }
            }
        }
        BasicText(info, style = t.caption.copy(color = p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            val shown = now?.takeIf { it.seekable }
            BasicText(shown?.let { clock(it.positionMs) }.orEmpty(), Modifier.width(SEEK_TIME), style = time, maxLines = 1)
            Track(shown?.fraction ?: 0f, Modifier.weight(1f), onTap)
            BasicText(
                shown?.let { clock(it.durationMs) }.orEmpty(),
                Modifier.width(SEEK_TIME),
                style = time.copy(textAlign = TextAlign.End),
                maxLines = 1,
            )
        }
    }
}

/** A drawn track filled to [value] with the accent; a tap reports where along it, 0..1. */
@Composable
private fun Track(value: Float, modifier: Modifier, onTap: (Float) -> Unit) {
    val p = LocalPalette.current
    Canvas(
        modifier.height(Space.xl).pointerInput(Unit) {
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
}

/** 3:07, or 1:02:07 past an hour. */
private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
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
private val SEEK_TIME = 40.dp
private const val MEDIA_TICK = 500L
private val TRACK = 6.dp
private const val SCRIM = 0.6f
