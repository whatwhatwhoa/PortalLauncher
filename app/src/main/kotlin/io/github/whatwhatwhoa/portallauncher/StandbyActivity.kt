package io.github.whatwhatwhoa.portallauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

/**
 * Standby (F19) in a window of its own, over whatever was showing: HomeHoldService starts it on
 * a hold of Home, in its own task, so the game or Home under it stays exactly as it was and
 * Android returns there when it finishes. It is opaque, so what is under it stops drawing; its
 * window keeps the screen on at the owner's brightness, and never shows over the lock screen.
 * It finishes on the fourth press of one button ([Standby]), or once it is out of view (the
 * screen off, an app over it).
 */
class StandbyActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var standby: Standby
    private val handler = Handler(Looper.getMainLooper())
    // The key that woke it while it is still held, and when it woke.
    private var wakeKey: Int? = null
    private var wokeAt = 0L
    private var hatPushed: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        enableEdgeToEdge()
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Standby comes and goes without Android's window animation: its own fades are the motion.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        prefs = Prefs(this)
        standby = Standby(lifecycleScope, ::woke)
        // Loaded with Home's, once for the process; the wake chime is ready when it is needed.
        Sounds.shared(this)
        val home = prefs.home
        val motion = ValueAnimator.areAnimatorsEnabled()
        setContent { StandbyRoot(standby, home, motion) }
        // The back gesture wakes nothing, as a touch doesn't.
        onBackPressedDispatcher.addCallback(this) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Out of view (the screen turned off, an app came over it): standby has done its job.
    override fun onStop() {
        super.onStop()
        if (current === this) current = null
        leave()
    }

    override fun onDestroy() {
        if (current === this) current = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * A key is a press toward waking, held or not; volume still sets the volume. The triggers
     * count no more than the sticks (owner): the Portal sends each as a button as well as an axis,
     * so their buttons are swallowed here and their axes below.
     * RestrictedApi: lint misreads androidx's override of this public Activity method.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(event)
        val trigger = key == KeyEvent.KEYCODE_BUTTON_L2 || key == KeyEvent.KEYCODE_BUTTON_R2
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!trigger && event.repeatCount == 0) standby.press(key)
            KeyEvent.ACTION_UP -> released(key)
        }
        return true
    }

    // Standby counts buttons only (owner): touches are swallowed, not counted.
    override fun dispatchTouchEvent(event: MotionEvent) = true

    /**
     * The sticks wake nothing (owner: buttons only), so their motion is swallowed before Android
     * turns it into D-pad keys. The D-pad is a hat on this controller and arrives here too; each
     * push from rest counts once, as the D-pad key of its direction, so four pushes the same way
     * wake and a roll around the pad doesn't.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val direction = when {
            x < 0f -> KeyEvent.KEYCODE_DPAD_LEFT
            x > 0f -> KeyEvent.KEYCODE_DPAD_RIGHT
            y < 0f -> KeyEvent.KEYCODE_DPAD_UP
            y > 0f -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> null
        }
        if (direction != null && hatPushed == null) standby.press(direction)
        if (direction == null) hatPushed?.let(::released)
        hatPushed = direction
        return true
    }

    /** A Home press, handed over by HomeHoldService while standby shows: one more toward waking. */
    fun home(down: Boolean) = if (down) standby.press(KeyEvent.KEYCODE_HOME) else released(KeyEvent.KEYCODE_HOME)

    /**
     * The fourth press: the chime, and the block fades out. Standby still swallows every key
     * until the waking key is let go and [WAKE_GUARD] has passed (never past [WAKE_GUARD_CAP]),
     * so a fifth quick press reaches neither the game nor Home.
     */
    private fun woke(key: Int) {
        Sounds.shared(this).apply { settings = prefs.sound }.play(Sound.Wake)
        wakeKey = key
        wokeAt = SystemClock.uptimeMillis()
        handler.postDelayed({ if (wakeKey == null) leave() }, WAKE_GUARD)
        handler.postDelayed(::leave, WAKE_GUARD_CAP)
    }

    private fun released(key: Int) {
        if (key != wakeKey) return
        wakeKey = null
        if (SystemClock.uptimeMillis() - wokeAt >= WAKE_GUARD) leave()
    }

    private fun leave() {
        if (isFinishing) return
        handler.removeCallbacksAndMessages(null)
        finish()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overridePendingTransition(0, 0)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        /**
         * The standby on screen, for HomeHoldService to hand Home presses to; null otherwise.
         * Set while it is created and shown, cleared as it stops, all on the main thread.
         */
        @SuppressLint("StaticFieldLeak")
        var current: StandbyActivity? = null
            private set
    }
}

/**
 * Standby's window: Home's font, type and canvas scaling, the owner's Home settings (hints, clock
 * format) as they were when it opened. The block fades in on opening and out on waking, on
 * Transit's timing; with animations off it snaps.
 */
@Composable
private fun StandbyRoot(standby: Standby, home: HomeSettings, motion: Boolean) {
    val type = remember { Type(selawik, FONT_SCALE) }
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = !standby.waking
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = canvasDensity(LocalDensity.current, constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalType provides type,
            LocalMotion provides motion,
            LocalHome provides home,
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = if (motion) fadeIn(Transit.enter()) else EnterTransition.None,
                exit = if (motion) fadeOut(Transit.exit()) else ExitTransition.None,
            ) {
                StandbyScreen(standby)
            }
        }
    }
}

// How long standby keeps swallowing keys after waking, and the most it waits for the waking key's
// release: past the fade, so an eager fifth press lands nowhere.
private const val WAKE_GUARD = 400L
private const val WAKE_GUARD_CAP = 1_000L
