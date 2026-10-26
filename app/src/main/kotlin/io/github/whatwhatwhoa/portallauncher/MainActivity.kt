package io.github.whatwhatwhoa.portallauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var catalog: AppCatalog
    private lateinit var state: LauncherState
    private lateinit var sounds: Sounds
    private lateinit var holdSwitch: HomeHoldSwitch
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private var imageReply: ((Uri?) -> Unit)? = null
    private val imageRequest = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        imageReply?.invoke(uri)
        imageReply = null
    }
    private var setupReply: ((Uri?) -> Unit)? = null
    private val setupRequest = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        setupReply?.invoke(uri)
        setupReply = null
    }

    // The system's animation switch (Developer options, or Accessibility → Remove animations) can
    // flip while Home stays open, so it is watched rather than read once.
    private var motion by mutableStateOf(true)
    private val motionWatcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            motion = ValueAnimator.areAnimatorsEnabled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        prefs = Prefs(this)
        catalog = AppCatalog(this, lifecycleScope)
        // The owner's key from Launcher Settings; a debug build may carry one from local.properties.
        val art = Art(this, lifecycleScope, prefs.artKey ?: BuildConfig.STEAMGRIDDB_KEY)
        sounds = Sounds(this)
        holdSwitch = HomeHoldSwitch(this)
        state = LauncherState(
            catalog, prefs, art, lifecycleScope, ::launch, ::pickImage, File(filesDir, "backdrops"),
            openSystem = ::openSystem,
            holdsHome = { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) },
            holdSwitch = holdSwitch,
            controls = DeviceControls(this),
            readClipboard = ::clipboardText,
            updates = Updates(this),
            sounds = sounds,
            setup = Setup(this),
            pickSetup = ::pickSetup,
        )
        catalog.start()
        holdSwitch.watch { state.checkHomeHold(watched = true) }
        // New favorites and newly installed games get their art looked up as they appear, and
        // everything again after Clear all art or a new key; not at all while Fetch art is off.
        lifecycleScope.launch {
            snapshotFlow { listOf(state.fetchArt, state.artCleared, art.key, state.artWanted) }
                .collect { if (state.fetchArt) art.lookUp(state.artWanted) }
        }
        motion = ValueAnimator.areAnimatorsEnabled()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            motionWatcher,
        )
        // Standby keeps the screen on, so the Portal rests without locking. Brightness stays the
        // owner's: dimmed to 5% it was too dark to read (owner, 2026-10-25).
        lifecycleScope.launch {
            snapshotFlow { state.standby }.collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        setContent { LauncherRoot(state, motion) }
        when (intent?.action) {
            Intent.ACTION_APPLICATION_PREFERENCES -> state.openSettings()
            ACTION_STANDBY -> state.openStandby()
            else -> askForHomeOnce()
        }
    }

    // Android's photo picker needs no storage permission and hands back only the one image.
    private fun pickImage(reply: (Uri?) -> Unit) {
        imageReply = reply
        imageRequest.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Android's file picker reads the setup file whoever owns it now, with no storage permission.
    private fun pickSetup(reply: (Uri?) -> Unit) {
        setupReply = reply
        setupRequest.launch(Setup.PICKER_TYPES)
    }

    // Android lets the app with focus read the clipboard (and says so in a toast).
    private fun clipboardText(): String? =
        getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(this)?.toString()

    // Settings → System and the quick panel open Android's screens; the panel stays open behind them.
    private fun openSystem(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Android has no screen for that here", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        state.refreshSettings()
        state.controls.watchMedia(true)
    }

    // The screen turned off or an app came over it: standby has done its job.
    override fun onStop() {
        state.endStandby()
        state.controls.watchMedia(false)
        super.onStop()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(motionWatcher)
        holdSwitch.stop()
        catalog.stop()
        sounds.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Coming back from an app the intent brings Home to the front; pressed while home, it
        // doesn't and the window already has focus. Both are checked, as Launcher3 does: an app's
        // splash screen can leave the focus with us.
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        if (intent.hasCategory(Intent.CATEGORY_HOME)) state.onHomePressed(alreadyHome = hasWindowFocus() && !broughtToFront)
        // The gear beside Portal in Android's Default apps → Home app.
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) state.openSettings()
        if (intent.action == ACTION_STANDBY) state.openStandby()
    }

    // Keys are turned into actions here, once, before any view sees them; a mapped key's up event
    // is swallowed too, so the system never adds its own fallback (B as Back, A as Enter).
    // RestrictedApi: lint misreads androidx's override of this public Activity method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // In standby a key is a press toward waking, held or not; volume still sets the volume.
        // The triggers count no more than the sticks (owner): the Portal sends each as a button
        // as well as an axis, so their buttons are swallowed here and their axes below.
        if (state.standby) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return super.dispatchKeyEvent(event)
            }
            val trigger = event.keyCode == KeyEvent.KEYCODE_BUTTON_L2 || event.keyCode == KeyEvent.KEYCODE_BUTTON_R2
            if (!trigger && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) state.standbyPress(event.keyCode)
            return true
        }
        // While a category's name is typed, keys are typing, except B, which keeps what is typed.
        if (state.categoryEdit == CategoryEdit.Renaming) {
            if (event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) state.finishRename()
            return true
        }
        // While the art search has the keyboard, keys are typing, except B, which ends it.
        state.picker?.takeIf { it.typing }?.let { picker ->
            if (event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) picker.stopTyping()
            return true
        }
        val action = actionFor(event) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action.repeats)) {
            state.onAction(action)
        }
        return true
    }

    // A touch puts the window in touch mode, and Android spends the next D-pad press leaving it
    // (gotchas.md). Focus here is always drawn, so that press only felt lost: the window leaves
    // touch mode as each touch ends instead (owner, 2026-10-25). Not while the keyboard is up.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Standby counts buttons only (owner): touches are swallowed, not counted.
        if (state.standby) return true
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && state.picker?.typing != true) {
            window.decorView.post { (currentFocus ?: window.decorView).requestFocusFromTouch() }
        }
        return handled
    }

    /**
     * In standby the sticks wake nothing (owner: buttons only), so their motion is swallowed
     * before Android turns it into D-pad keys. The D-pad is a hat on this controller and arrives
     * here too; each push from rest counts once, as the D-pad key of its direction, so four
     * pushes the same way wake and a roll around the pad doesn't.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!state.standby) return super.dispatchGenericMotionEvent(event)
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val direction = when {
            x < 0f -> KeyEvent.KEYCODE_DPAD_LEFT
            x > 0f -> KeyEvent.KEYCODE_DPAD_RIGHT
            y < 0f -> KeyEvent.KEYCODE_DPAD_UP
            y > 0f -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> null
        }
        if (direction != null && hatPushed == null) state.standbyPress(direction)
        hatPushed = direction
        return true
    }
    private var hatPushed: Int? = null

    /**
     * The app's window is revealed out of its tile: a clip that grows from the tile's rect to the
     * full screen, so the tile seems to open. Edge to edge, window pixels are screen pixels.
     */
    private fun launch(entry: AppEntry, from: Rect?) {
        val options = from?.takeIf { motion }?.let {
            ActivityOptions.makeClipRevealAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        if (!catalog.launch(entry, from, options)) Toast.makeText(this, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** The system's own "use as Home?" dialog, once; the owner can always switch back in Settings. */
    private fun askForHomeOnce() {
        val roles = getSystemService(RoleManager::class.java)
        if (prefs.askedForHome || !roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return
        prefs.askedForHome = true
        roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
    }

    companion object {
        /** Opens standby: from HomeHoldService when Home is held. */
        const val ACTION_STANDBY = "io.github.whatwhatwhoa.portallauncher.STANDBY"
    }
}

@Composable
private fun LauncherRoot(state: LauncherState, motion: Boolean) {
    val look = state.look
    val dark = when (look.theme) {
        Theme.System -> isSystemInDarkTheme()
        Theme.Light -> false
        Theme.Dark -> true
    }
    val black = state.backdrop == Backdrop.Black
    val palette = remember(dark, look.accent, black) { palette(dark, look.accent, black) }
    val type = remember { Type(selawik, FONT_SCALE) }
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        // Portrait is unsupported, but the Portal lands in it when auto-rotate is on (its sensor
        // reads 90° off) or a rotation app turns it: Home keeps the screen's landscape shape,
        // across the width and centered, bars above and below, rather than break.
        val portrait = constraints.maxHeight > constraints.maxWidth
        val canvas = if (!portrait) Modifier.fillMaxSize() else Modifier.align(Alignment.Center)
            .fillMaxWidth().aspectRatio(constraints.maxHeight.toFloat() / constraints.maxWidth).clipToBounds()
        BoxWithConstraints(canvas) {
            val density = canvasDensity(LocalDensity.current, constraints.maxWidth, constraints.maxHeight)
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalPalette provides palette,
                LocalType provides type,
                LocalMotion provides motion,
                LocalEffects provides look.effects,
                LocalPulse provides look.pulse,
                LocalHome provides state.home,
            ) {
                Layers(state, motion)
            }
        }
    }
    // Always on: with nothing to close, Android's own back finishes a Home activity (it isn't a
    // launcher task), and Home is rebuilt in a new task. Back at home does nothing, as Launcher3.
    BackHandler { if (state.backHandled) state.back() }
}

/**
 * The layout is drawn on a 600 dp tall canvas. Another screen gets it scaled to fit (0.5–1.5,
 * narrowed if the screen is too narrow for the canvas), and the system font scale is held to
 * 0.9–1.15 so big text can't break the layout. The factor is rounded to hundredths so the
 * Portal, whose window is the canvas, lands on exactly 1 and icons stay 1:1.
 * Measured in pixels against the current density: after a live density change (Display size,
 * Smallest width) BoxWithConstraints' maxWidth/maxHeight still used the old one, so the layout
 * drew ~1.25× too big until a restart.
 */
private fun canvasDensity(system: Density, widthPx: Int, heightPx: Int): Density {
    val width = widthPx / system.density
    val height = heightPx / system.density
    val fit = minOf(height / Dimens.canvasHeight.value, width / Dimens.canvasMinWidth.value)
    val factor = ((fit * 100).roundToInt() / 100f).coerceIn(0.5f, 1.5f)
    return Density(system.density * factor, system.fontScale.coerceIn(0.9f, 1.15f))
}

// Every layer comes and goes on the same timing (Transit); the ones that slide say how far.
@Composable
private fun Layers(state: LauncherState, motion: Boolean) {
    val drawer = state.screen == Screen.Drawer
    // Home steps back a little while All apps rises over it, so the two read as layers.
    val recede by animateFloatAsState(
        if (drawer) RECEDE else 1f,
        if (!motion) snap() else if (drawer) Transit.enter() else Transit.exit(),
        label = "recede",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = recede
                scaleY = recede
            },
        ) { HomeScreen(state) }
        AnimatedVisibility(
            visible = state.categoriesOpen,
            enter = if (motion) slideInHorizontally(Transit.enter()) { -it / 16 } + fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) slideOutHorizontally(Transit.exit()) { -it / 16 } + fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            CategoriesLayer(state)
        }
        // All apps rises from below (where the swipe that opens it starts) and sinks back a
        // shorter way, so closing feels quicker than opening.
        AnimatedVisibility(
            visible = drawer,
            enter = if (motion) slideInVertically(Transit.enter()) { it / 8 } + fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) slideOutVertically(Transit.exit()) { it / 16 } + fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            AllAppsScreen(state)
        }
        AnimatedVisibility(
            visible = state.sheetOpen,
            enter = if (motion) fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            state.sheet?.let { OptionsSheet(state, it) }
        }
        AnimatedVisibility(
            visible = state.quickOpen,
            enter = if (motion) fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            QuickPanel(state)
        }
        AnimatedVisibility(
            visible = state.settingsOpen,
            enter = if (motion) fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            SettingsPanel(state)
        }
        StatusCluster(state.controls, covered = state.quickOpen || state.settingsOpen, Modifier.align(Alignment.TopCenter))
        AnimatedVisibility(
            visible = state.standbyHint,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (motion) fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            StandbyHint(state.standbyHintText)
        }
        AnimatedVisibility(
            visible = state.standby,
            enter = if (motion) fadeIn(Transit.enter()) else EnterTransition.None,
            exit = if (motion) fadeOut(Transit.exit()) else ExitTransition.None,
        ) {
            StandbyScreen(state)
        }
    }
}

/** How far Home steps back behind All apps. */
private const val RECEDE = 0.96f
