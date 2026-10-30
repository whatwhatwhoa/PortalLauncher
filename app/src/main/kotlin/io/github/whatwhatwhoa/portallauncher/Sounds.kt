package io.github.whatwhatwhoa.portallauncher

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.math.pow

/** The sound families, made by `tools/sounds/make_sounds.py`; Soft is the default (owner). */
enum class SoundFamily(val label: String) { Soft("Soft"), Glass("Glass"), Crisp("Crisp") }

/**
 * How loud the sounds play under the media volume; Low is the default (owner: on, but low; then
 * 35% up, "not quite media player but not too soft"). Loud is SoundPool's ceiling.
 */
enum class SoundLevel(val label: String, val gain: Float) {
    Quiet("Quiet", 0.16f), Low("Low", 0.34f), Medium("Medium", 0.68f), Loud("Loud", 1f),
}

/** Settings → Sound. */
data class SoundSettings(
    val on: Boolean = true,
    val family: SoundFamily = SoundFamily.Soft,
    val level: SoundLevel = SoundLevel.Low,
)

/** The Sound settings after one step of [setting], as the other groups step. */
fun SoundSettings.step(setting: Setting, by: Int): SoundSettings = when (setting) {
    Setting.SoundsOn -> copy(on = !on)
    Setting.SoundSet -> copy(family = SoundFamily.entries[Math.floorMod(family.ordinal + by, SoundFamily.entries.size)])
    Setting.SoundVolume -> copy(level = SoundLevel.entries[Math.floorMod(level.ordinal + by, SoundLevel.entries.size)])
    else -> this
}

/**
 * One sound of each family; [file] is its name in `assets/sounds/<family>/`. A repeat sooner
 * than [gap] ms is skipped, so a held D-pad or a held push against the row's end doesn't stutter.
 */
enum class Sound(val file: String, val gap: Long = 0) {
    Move("move", 40), Edge("edge", 250), Select("select"), Back("back"),
    LayerOpen("layer_open"), LayerClose("layer_close"), SheetOpen("sheet_open"),
    ToggleOn("toggle_on"), ToggleOff("toggle_off"), Slider("slider", 40),
    Launch("launch"), Wake("wake"),
}

/**
 * The launcher's UI sounds (F22, `decisions/F22-sounds.md`). All three families are loaded at
 * start (about 1 MB), so a new family in the settings plays at once. They go on the media
 * stream: the volume buttons set them with everything else, as on the Switch. Nothing plays
 * while they're off or Do Not Disturb is on (owner).
 */
class Sounds(context: Context) {
    private val app = context.applicationContext
    private val notifications = app.getSystemService(NotificationManager::class.java)
    // Do Not Disturb as Android last announced it, rather than asked on every key: that was a
    // call into the system ahead of each tick, which waited whenever the system was busy.
    private var quiet = isQuiet()
    private val quietChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            quiet = isQuiet()
        }
    }
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = SoundFamily.entries.map { family ->
        Sound.entries.map { sound ->
            context.assets.openFd("sounds/${family.name.lowercase()}/${sound.file}.wav").use { pool.load(it, 1) }
        }
    }
    private val lastAt = LongArray(Sound.entries.size)

    init {
        ContextCompat.registerReceiver(
            app,
            quietChanged,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    var settings = SoundSettings()

    /** Counts the sounds asked for, so a caller can tell whether an action already made its own. */
    var asked = 0
        private set

    /** Plays [sound]; [pitch] is a playback rate, 0.5 to 2. */
    fun play(sound: Sound, pitch: Float = 1f) {
        asked++
        if (!settings.on) return
        // A toggle may be the quick panel's Do not disturb, cued before Android announces it, so
        // a flip asks once.
        if (sound == Sound.ToggleOn || sound == Sound.ToggleOff) quiet = isQuiet()
        if (quiet) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAt[sound.ordinal] < sound.gap) return
        lastAt[sound.ordinal] = now
        val gain = settings.level.gain
        pool.play(ids[settings.family.ordinal][sound.ordinal], gain, gain, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    private fun isQuiet() = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    fun release() {
        app.unregisterReceiver(quietChanged)
        pool.release()
    }
}

/**
 * Where the launcher is, as far as sounds care: how many layers are open, where focus is, and
 * the values a key can change. An action's sound is read from two of these ([cue]), so the
 * screens need no sound calls of their own.
 */
data class Spot(
    val depth: Int,
    val layer: Boolean,
    val drawer: Boolean,
    val focus: List<Any?>,
    val switches: List<Boolean>,
    val levels: List<Float>,
    val values: List<Any?>,
    /** False where focus lives inside a page we can't see into (the art picker): any move is a move. */
    val focusKnown: Boolean,
)

fun LauncherState.spot(): Spot {
    val drawer = screen == Screen.Drawer
    val artPicker = picker
    return Spot(
        depth = listOf(
            drawer, categoriesOpen, editingCategories, categoryEdit != null, moving != null, sheetOpen,
            artPicker != null, artPicker?.typing == true, artPicker?.browsing != null, choosingCategory,
            quickOpen, settingsOpen, accentOpen,
        ).count { it },
        layer = categoriesOpen,
        drawer = drawer,
        focus = listOf(
            zone, rowFocus, dockFocus, drawerFocus, sheetFocus, categoryFocus, categoryActionFocus,
            settingsGroupFocus, settingsInRows, settingsFocus, accentRow, accentColumn, quickFocus, quickColumn,
            category.id,
        ),
        switches = listOf(
            look.covers, look.effects.gradient, look.effects.glow, look.effects.depth,
            look.effects.reflection, home.batteryPercent, home.hints, home.wraps, home.playControls, fetchArt, sound.on,
            controls.autoBrightness, controls.quiet,
        ),
        levels = listOf(
            controls.brightness,
            controls.volume.toFloat() / controls.volumeMax.coerceAtLeast(1),
            controls.nowPlaying?.fraction ?: 0f,
        ),
        values = listOf(look, home, sound, armed, deleteArmed),
        focusKnown = artPicker == null,
    )
}

private val moves = setOf(
    Action.Left, Action.Right, Action.Up, Action.Down, Action.PagePrev, Action.PageNext, Action.First, Action.Last,
)

/**
 * The sound for what [action] did, going from [before] to [after]: a layer more is an open (the
 * categories and All apps swish, the rest swell), a layer less is back, unless A closed it by
 * choosing; a switch flipped is a toggle, a slider moved ticks at a pitch that rises with it, a
 * move that moved nothing bumps. Null is no sound.
 */
fun cue(before: Spot, after: Spot, action: Action?): Pair<Sound, Float>? {
    action ?: return null
    val swept = before.layer != after.layer || before.drawer != after.drawer
    if (after.depth > before.depth) return (if (swept) Sound.LayerOpen else Sound.SheetOpen) to 1f
    if (after.depth < before.depth) {
        return when {
            action == Action.Open -> Sound.Select
            swept -> Sound.LayerClose
            else -> Sound.Back
        } to 1f
    }
    before.switches.indices.firstOrNull { before.switches[it] != after.switches[it] }?.let {
        return (if (after.switches[it]) Sound.ToggleOn else Sound.ToggleOff) to 1f
    }
    before.levels.indices.firstOrNull { before.levels[it] != after.levels[it] }?.let {
        return Sound.Slider to 2f.pow(after.levels[it] - 0.5f)
    }
    val changed = before.focus != after.focus || before.values != after.values
    return when {
        action in moves -> if (changed || !after.focusKnown) Sound.Move else Sound.Edge
        action == Action.Back -> if (changed) Sound.Back else null
        action == Action.Open -> Sound.Select
        else -> if (changed) Sound.Select else null
    }?.let { it to 1f }
}
