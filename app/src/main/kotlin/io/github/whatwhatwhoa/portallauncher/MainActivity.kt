package io.github.whatwhatwhoa.portallauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var catalog: AppCatalog
    private lateinit var state: LauncherState
    private lateinit var sounds: Sounds
    private lateinit var holdSwitch: HomeHoldSwitch
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleAnswered() }
    // Settings → System → Home app asked (not the first start): still not Home after, its page opens.
    private var homeAskedFromSettings = false
    // Started by Android's Home intent: the one launcher. Any other start is a copy (onCreate).
    private var isHome = false
    private val stick = Stick(::stickStep)
    // A touch that began while the launcher wasn't in front is swallowed to its end.
    private var touchIgnored = false
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
        isHome = isHomeIntent(intent)
        // One launcher: Android makes Home only from its Home intent, so any other start (Portal's
        // icon in another launcher, the settings gear) would build a second one beside it, its
        // setup drifting from Home's. While Portal is Home, such a start hands over to Home and
        // goes, before anything is built.
        if (!isHome && holdsHome()) {
            forwardHome(if (intent?.action == Intent.ACTION_APPLICATION_PREFERENCES) OPEN_SETTINGS else OPEN_HOME)
            finish()
            return
        }
        // A copy opened before Portal became Home (in Android's Default apps, say) can still be
        // running, stopped: Home, once built, ends it, so two launchers never share the setup.
        if (isHome) copy?.get()?.finish() else copy = WeakReference(this)
        enableEdgeToEdge()
        hideSystemBars()
        prefs = Prefs(this)
        // Until Compose draws, the window shows the backdrop's own color, not the theme's.
        window.setBackgroundDrawable(storedBackground().toArgb().toDrawable())
        catalog = AppCatalog(this, lifecycleScope)
        // The owner's key from Launcher Settings; a debug build may carry one from local.properties.
        val art = Art(this, lifecycleScope, prefs.artKey ?: BuildConfig.STEAMGRIDDB_KEY)
        sounds = Sounds.shared(this)
        holdSwitch = HomeHoldSwitch(this)
        state = LauncherState(
            catalog, prefs, art, lifecycleScope, ::launch, ::pickImage, File(filesDir, "backdrops"),
            openSystem = ::openSystem,
            holdsHome = ::holdsHome,
            otherHome = ::otherHome,
            requestHome = ::requestHome,
            say = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
            openShade = { HomeHoldService.openShade(this) },
            holdSwitch = holdSwitch,
            controls = DeviceControls(this),
            readClipboard = ::clipboardText,
            updates = Updates(this),
            sounds = sounds,
            setup = Setup(this),
            pickSetup = ::pickSetup,
            connectivity = Connectivity(this),
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
        setContent { LauncherRoot(state, motion) }
        holdFirstFrame()
        // Once per start: a rebuilt activity (Android's own restart after a change) mustn't open
        // the settings or ask again.
        if (savedInstanceState == null) {
            val open = intent?.getStringExtra(EXTRA_OPEN)
            when {
                open == OPEN_SETTINGS || intent?.action == Intent.ACTION_APPLICATION_PREFERENCES -> state.openSettings()
                open == null -> askForHomeOnce()
            }
        }
    }

    /**
     * Home's first frame waits for the app list, so it doesn't draw empty, then with a dock of two,
     * then fill in; never longer than [FIRST_FRAME_WAIT].
     */
    private fun holdFirstFrame() {
        val content = findViewById<View>(android.R.id.content)
        val since = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!catalog.loaded && SystemClock.uptimeMillis() - since < FIRST_FRAME_WAIT) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    /** The background Home will draw, from the stored Look, as the root works it out. */
    private fun storedBackground(): Color {
        val look = prefs.look
        val dark = when (look.theme) {
            Theme.System -> resources.configuration.isNightModeActive
            Theme.Light -> false
            Theme.Dark -> true
        }
        return palette(dark, look.accent, look.backdrop == Backdrop.Black.id).background
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
        } catch (e: RuntimeException) {
            // Missing here (ActivityNotFoundException), or kept to itself by another build
            // (SecurityException: not exported), or an intent it refuses: never a crash of Home.
            Toast.makeText(this, "Android has no screen for that here", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // A copy coming back after Portal became Home (Back from Default apps) hands over now.
        if (!isHome && !isFinishing && holdsHome()) {
            forwardHome(if (state.settingsOpen) OPEN_SETTINGS else OPEN_HOME)
            return finish()
        }
        state.refreshSettings()
        state.controls.watchMedia(true)
    }

    override fun onStop() {
        stick.cancel()
        state.controls.watchMedia(false)
        super.onStop()
    }

    override fun onDestroy() {
        // A copy that handed over to Home (onCreate) built nothing to take down.
        if (!::state.isInitialized) return super.onDestroy()
        if (copy?.get() === this) copy = null
        contentResolver.unregisterContentObserver(motionWatcher)
        holdSwitch.stop()
        catalog.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android resets its own stick conversion as the focus goes; the stick starts afresh too.
        if (hasFocus) hideSystemBars() else stick.reset()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A copy started another way handed over to Home (onCreate): what it was opened for, not
        // a Home press.
        when (intent.getStringExtra(EXTRA_OPEN)) {
            OPEN_SETTINGS -> return state.openSettings()
            OPEN_HOME -> return
        }
        // A copy from before Portal was Home, opened again now that it is, hands over too.
        if (!isHome && holdsHome()) {
            forwardHome(if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) OPEN_SETTINGS else OPEN_HOME)
            return finish()
        }
        // Coming back from an app the intent brings Home to the front; pressed while home, it
        // doesn't and the window already has focus. Both are checked, as Launcher3 does: an app's
        // splash screen can leave the focus with us.
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        if (intent.hasCategory(Intent.CATEGORY_HOME)) state.onHomePressed(alreadyHome = hasWindowFocus() && !broughtToFront)
        // The gear beside Portal in Android's Default apps → Home app, while Portal isn't Home.
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) state.openSettings()
    }

    // A text field takes keys only while it is on screen, so a stale flag can't take the controller.
    private val renaming get() = state.categoriesOpen && state.categoryEdit == CategoryEdit.Renaming
    private val searching get() = state.sheetOpen && state.picker?.typing == true

    // Keys are turned into actions here, once, before any view sees them; a mapped key's up event
    // is swallowed too, so the system never adds its own fallback (B as Back, A as Enter).
    // RestrictedApi: lint misreads androidx's override of this public Activity method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Between a launch and the app's window, and under a dialog or a picker, keys can still
        // come here: the launcher is paused then, so they do nothing rather than act on a Home no
        // one sees (a second A opened the row's app over the one just started).
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return if (actionFor(event) != null) true else super.dispatchKeyEvent(event)
        }
        // While a category's name is typed, keys are typing, except B, which keeps what is typed.
        if (renaming) {
            if (event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) state.finishRename()
            return true
        }
        // While the art search has the keyboard, keys are typing, except B, which ends it.
        state.picker?.takeIf { searching }?.let { picker ->
            if (event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_DOWN) picker.stopTyping()
            return true
        }
        val action = actionFor(event) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action.repeats)) {
            state.onAction(action, repeat = event.repeatCount > 0)
        }
        return true
    }

    // A touch puts the window in touch mode, and Android spends the next D-pad press leaving it
    // (gotchas.md). Focus here is always drawn, so that press only felt lost: the window leaves
    // touch mode as each touch ends instead (owner, 2026-10-25). Not while the keyboard is up.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // As keys: a tap during an app's splash (its window takes no touches) lands nowhere.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) touchIgnored = !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (touchIgnored) return true
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && state.picker?.typing != true) {
            window.decorView.post { (currentFocus ?: window.decorView).requestFocusFromTouch() }
        }
        return handled
    }

    // The left stick is read here ([Stick]); the hat and the rest go on to Android. While a field
    // has the keyboard, the stick goes on too, as the D-pad keys it types with.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.actionMasked == MotionEvent.ACTION_MOVE
        if (joystick && stick.takes(event, typing = renaming || searching)) return true
        val handled = super.dispatchGenericMotionEvent(event)
        if (joystick && !handled) stick.passed(event)
        return handled
    }

    // A step of the stick, as the D-pad key it stands for, through every gate a key passes.
    private fun stickStep(keyCode: Int, repeat: Int) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD)
        dispatchKeyEvent(down)
        dispatchKeyEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP))
    }

    /**
     * The app's window is revealed out of its tile: a clip that grows from the tile's rect to the
     * full screen, so the tile seems to open. Edge to edge, window pixels are screen pixels.
     * False when it couldn't start, so Home neither sounds nor keeps it.
     */
    private fun launch(entry: AppEntry, from: Rect?): Boolean {
        val options = from?.takeIf { motion }?.let {
            ActivityOptions.makeClipRevealAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        val launched = catalog.launch(entry, from, options)
        if (!launched) Toast.makeText(this, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
        return launched
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

    /** Settings → System → Home app while Portal isn't Home: the same dialog, one A away. */
    private fun requestHome() {
        val roles = getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return openSystem(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        homeAskedFromSettings = true
        roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
    }

    /**
     * The dialog's answer. Home now, a copy opened as an app hands over to Home. Still not Home
     * after the Settings row asked, Android's Default apps page opens: its "Don't ask again"
     * answers at once, and the page still lets Portal be picked.
     */
    private fun roleAnswered() {
        val fromSettings = homeAskedFromSettings
        homeAskedFromSettings = false
        if (holdsHome()) {
            if (!isHome) {
                forwardHome(if (fromSettings) OPEN_SETTINGS else OPEN_HOME)
                finish()
            }
        } else if (fromSettings) {
            openSystem(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun holdsHome() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    // Another app is Home; with none, Android's chooser ("android") stands in.
    private fun otherHome(): Boolean {
        val home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        return home != null && home != packageName && home != "android"
    }

    /** Android's Home intent, aimed at Portal, carrying what to open there ([EXTRA_OPEN]). */
    private fun forwardHome(open: String) {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
                .putExtra(EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // Android makes an activity Home only for its Home intent: MAIN and HOME, as the system sends it.
    private fun isHomeIntent(intent: Intent?) = intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)

    private companion object {
        /** On a Home intent from a copy handing over: [OPEN_SETTINGS] or [OPEN_HOME] (nothing more). */
        const val EXTRA_OPEN = "io.github.whatwhatwhoa.portallauncher.OPEN"
        const val OPEN_SETTINGS = "settings"
        const val OPEN_HOME = "home"
        /** The most Home's first frame waits for the app list. */
        const val FIRST_FRAME_WAIT = 1_000L
        /** A copy started as an app (not Home), while it lives; Home finishes it when built. */
        var copy: WeakReference<MainActivity>? = null
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
 * drew ~1.25× too big until a restart. Standby's window scales the same way (StandbyActivity).
 */
fun canvasDensity(system: Density, widthPx: Int, heightPx: Int): Density {
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
    // Once All apps has risen and covers Home, Home is drawn at alpha 0, which the renderer skips
    // (kept composed, so nothing reloads); it is back the moment All apps starts to sink.
    val drawerShown = remember { MutableTransitionState(false) }
    drawerShown.targetState = drawer
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = recede
                scaleY = recede
                alpha = if (drawer && drawerShown.isIdle && drawerShown.currentState) 0f else 1f
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
            visibleState = drawerShown,
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
    }
}

/** How far Home steps back behind All apps. */
private const val RECEDE = 0.96f
