package io.github.whatwhatwhoa.portallauncher

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var catalog: AppCatalog
    private lateinit var state: LauncherState
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        prefs = Prefs(this)
        catalog = AppCatalog(this, lifecycleScope)
        state = LauncherState(catalog, prefs, ::launch)
        catalog.start()
        setContent { LauncherRoot(state) }
        askForHomeOnce()
    }

    override fun onDestroy() {
        catalog.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) state.onHomePressed()
    }

    // Keys are turned into actions here, once, before any view sees them; a mapped key's up event
    // is swallowed too, so the system never adds its own fallback (B as Back, A as Enter).
    // RestrictedApi: lint misreads androidx's override of this public Activity method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = actionFor(event) ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action.repeats)) {
            state.onAction(action)
        }
        return true
    }

    private fun launch(entry: AppEntry) {
        if (!catalog.launch(entry)) Toast.makeText(this, "Couldn't open ${entry.label}", Toast.LENGTH_SHORT).show()
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
private fun LauncherRoot(state: LauncherState) {
    val dark = isSystemInDarkTheme()
    val palette = remember(dark, state.accent) { palette(dark, state.accent) }
    val type = remember(state.font) { Type(state.font.family()) }
    CompositionLocalProvider(LocalPalette provides palette, LocalType provides type) {
        Box(Modifier.fillMaxSize().background(palette.background)) {
            HomeScreen(state)
            AnimatedVisibility(
                visible = state.screen == Screen.Drawer,
                enter = slideInVertically { it / 8 } + fadeIn(),
                exit = slideOutVertically { it / 8 } + fadeOut(),
            ) {
                AllAppsScreen(state)
            }
        }
        BackHandler(enabled = state.screen == Screen.Drawer) { state.closeDrawer() }
    }
}
