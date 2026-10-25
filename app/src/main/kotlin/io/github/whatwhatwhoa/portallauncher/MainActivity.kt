package io.github.whatwhatwhoa.portallauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private var imageReply: ((Uri?) -> Unit)? = null
    private val imageRequest = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        imageReply?.invoke(uri)
        imageReply = null
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
        val art = BuildConfig.STEAMGRIDDB_KEY.takeIf { it.isNotBlank() }?.let { Art(this, lifecycleScope, it) }
        state = LauncherState(
            catalog, prefs, art, lifecycleScope, ::launch, ::pickImage, File(filesDir, "backdrops"),
            openSystem = ::openSystem,
            holdsHome = { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) },
            controls = DeviceControls(this),
        )
        catalog.start()
        // New favorites and newly installed games get their art looked up as they appear, and
        // everything again after Clear all art; not at all while Fetch art is off.
        if (art != null) {
            lifecycleScope.launch {
                snapshotFlow { Triple(state.fetchArt, state.artCleared, state.artWanted) }
                    .collect { (fetch, _, wanted) -> if (fetch) art.lookUp(wanted) }
            }
        }
        motion = ValueAnimator.areAnimatorsEnabled()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            motionWatcher,
        )
        setContent { LauncherRoot(state, motion) }
        if (intent?.action == Intent.ACTION_APPLICATION_PREFERENCES) state.openSettings() else askForHomeOnce()
    }

    // Android's photo picker needs no storage permission and hands back only the one image.
    private fun pickImage(reply: (Uri?) -> Unit) {
        imageReply = reply
        imageRequest.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

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
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(motionWatcher)
        catalog.stop()
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
    }

    // Keys are turned into actions here, once, before any view sees them; a mapped key's up event
    // is swallowed too, so the system never adds its own fallback (B as Back, A as Enter).
    // RestrictedApi: lint misreads androidx's override of this public Activity method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && state.picker?.typing != true) {
            window.decorView.post { (currentFocus ?: window.decorView).requestFocusFromTouch() }
        }
        return handled
    }

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
        val density = canvasDensity(LocalDensity.current, maxWidth, maxHeight)
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
    BackHandler(enabled = state.backHandled) { state.back() }
}

/**
 * The layout is drawn on a 600 dp tall canvas. Another screen gets it scaled to fit (0.75–1.5,
 * narrowed if the screen is too narrow for the canvas), and the system font scale is held to
 * 0.9–1.15 so big text can't break the layout. The factor is rounded to hundredths so the
 * Portal, whose window is the canvas, lands on exactly 1 and icons stay 1:1.
 */
private fun canvasDensity(system: Density, width: Dp, height: Dp): Density {
    val fit = minOf(height / Dimens.canvasHeight, width / Dimens.canvasMinWidth)
    val factor = ((fit * 100).roundToInt() / 100f).coerceIn(0.75f, 1.5f)
    return Density(system.density * factor, system.fontScale.coerceIn(0.9f, 1.15f))
}

@Composable
private fun Layers(state: LauncherState, motion: Boolean) {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(state)
        AnimatedVisibility(
            visible = state.categoriesOpen,
            enter = if (motion) slideInHorizontally { -it / 16 } + fadeIn() else EnterTransition.None,
            exit = if (motion) slideOutHorizontally { -it / 16 } + fadeOut() else ExitTransition.None,
        ) {
            CategoriesLayer(state)
        }
        AnimatedVisibility(
            visible = state.screen == Screen.Drawer,
            enter = if (motion) slideInVertically { it / 8 } + fadeIn() else EnterTransition.None,
            exit = if (motion) slideOutVertically { it / 8 } + fadeOut() else ExitTransition.None,
        ) {
            AllAppsScreen(state)
        }
        AnimatedVisibility(
            visible = state.sheetOpen,
            enter = if (motion) fadeIn() else EnterTransition.None,
            exit = if (motion) fadeOut() else ExitTransition.None,
        ) {
            state.sheet?.let { OptionsSheet(state, it) }
        }
        AnimatedVisibility(
            visible = state.quickOpen,
            enter = if (motion) fadeIn() else EnterTransition.None,
            exit = if (motion) fadeOut() else ExitTransition.None,
        ) {
            QuickPanel(state)
        }
        AnimatedVisibility(
            visible = state.settingsOpen,
            enter = if (motion) fadeIn() else EnterTransition.None,
            exit = if (motion) fadeOut() else ExitTransition.None,
        ) {
            SettingsPanel(state)
        }
        StatusCluster(Modifier.align(Alignment.TopCenter))
    }
}
