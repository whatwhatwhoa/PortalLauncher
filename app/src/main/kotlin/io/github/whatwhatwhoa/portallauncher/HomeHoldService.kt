package io.github.whatwhatwhoa.portallauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

/**
 * Holding Home opens standby (F19). Android keeps the Home key from every app, the Home app
 * included, and spends a hold on its own (Circle to Search on the Portal); an accessibility
 * service is the one place an app sees the key first, on stock Android as on LineageOS. So it
 * takes every Home press: a short one is handed back as Android's own Home, a hold opens standby.
 * Off until the owner turns it on in Android's accessibility settings; it reads no screen content.
 */
class HomeHoldService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var down = false
    private val hold = Runnable {
        down = false
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_STANDBY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                down = true
                // Android's own long-press time, which the owner can lengthen in accessibility settings.
                handler.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            KeyEvent.ACTION_UP -> if (down) {
                down = false
                handler.removeCallbacks(hold)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(hold)
        super.onDestroy()
    }
}
