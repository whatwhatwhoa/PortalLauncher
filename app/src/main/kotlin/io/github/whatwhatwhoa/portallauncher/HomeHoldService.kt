package io.github.whatwhatwhoa.portallauncher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.window.SplashScreen

/**
 * Holding Home opens standby (F19). Android keeps the Home key from every app, the Home app
 * included, and spends a hold on its own (Circle to Search on the Portal); an accessibility
 * service is the one place an app sees the key first, on stock Android as on LineageOS. So it
 * takes every Home press: a short one is handed back as Android's own Home, a hold opens standby.
 * It also pulls the notification shade down for Notifications (F13), a global action that needs
 * no more access. Off until the owner turns it on in Android's accessibility settings; it reads
 * no screen content.
 */
class HomeHoldService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var down = false
    // A Home press that went down while standby showed, counted when it comes up short.
    private var tap = false
    private val hold = Runnable {
        // Behind the lock screen standby would wait there for the unlock: locked, a hold is a
        // short press, so the release still goes Home.
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@Runnable
        down = false
        // A plain black start, not the app's icon on a splash; its own fade is the motion.
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)
        startActivity(
            Intent(this, StandbyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            options.toBundle(),
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        // While standby shows, a short Home press is one more toward waking, never Android's
        // Home, and a hold does nothing: so it counts on the release, once its length is known.
        // The hold that opened standby comes up here too, and counts no more than any hold.
        StandbyActivity.current?.let { standby ->
            handler.removeCallbacks(hold)
            down = false
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) tap = true
                KeyEvent.ACTION_UP -> if (tap) {
                    tap = false
                    if (event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) standby.home()
                }
            }
            return true
        }
        tap = false
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

    override fun onUnbind(intent: Intent?): Boolean {
        connected = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = null
        handler.removeCallbacks(hold)
        super.onDestroy()
    }

    companion object {
        // The service while Android has it on; it runs in the launcher's process, on its main
        // thread, and is let go when Android unbinds it.
        @SuppressLint("StaticFieldLeak")
        private var connected: HomeHoldService? = null

        /**
         * Pulls Android's notification shade down (F13): through the service while it is on,
         * else through StatusBarManager's own call, hidden from apps but allowed with the normal
         * EXPAND_STATUS_BAR permission. False when neither could.
         */
        @SuppressLint("DiscouragedPrivateApi")
        fun openShade(context: Context): Boolean {
            if (connected?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true) return true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            return try {
                val bar = context.applicationContext.getSystemService(StatusBarManager::class.java) ?: return false
                StatusBarManager::class.java.getMethod("expandNotificationsPanel").invoke(bar)
                true
            } catch (e: ReflectiveOperationException) {
                false
            } catch (e: RuntimeException) {
                false
            }
        }
    }
}

/**
 * The service's switch in Android's settings. Android turns it off whenever the launcher is
 * force-stopped, so the launcher keeps its own record of whether the owner wants it
 * ([Prefs.homeHoldWanted]) and puts it back (`LauncherState.checkHomeHold`): by itself where it
 * may write secure settings, granted once over adb, else with a note on Home.
 */
class HomeHoldSwitch(private val context: Context) {
    private val component = ComponentName(context, HomeHoldService::class.java)
    private val resolver = context.contentResolver
    private var watcher: ContentObserver? = null

    /** Read from the setting itself, not AccessibilityManager, so a write here reads back at once. */
    val on: Boolean get() = enabled().contains(component)

    /** WRITE_SECURE_SETTINGS, which only `adb shell pm grant` gives; with it the launcher flips the switch itself. */
    val canSwitch: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Turns the service on or off, leaving any other service as it is; needs [canSwitch]. */
    fun switch(on: Boolean) {
        try {
            val others = enabled().filter { it != component }
            val list = if (on) others + component else others
            Settings.Secure.putString(resolver, ENABLED, list.joinToString(":") { it.flattenToString() })
            if (on) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (e: SecurityException) {
            // The grant was taken back: the row falls back to Android's page.
        }
    }

    /**
     * Whether Android grays the switch out ("Restricted setting") until the owner allows it in App
     * info: Android 13 on, for an APK installed from a downloaded or local file. Read as Android's
     * own check does: the app op where the owner (or the install) set it, else the install source.
     */
    val restricted: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val mode = try {
                context.getSystemService(AppOpsManager::class.java)
                    .unsafeCheckOpNoThrow(RESTRICTED_OP, Process.myUid(), context.packageName)
            } catch (e: IllegalArgumentException) {
                return false
            } catch (e: SecurityException) {
                // The Portal's Android refuses even an app's own op without MANAGE_APPOPS (a
                // fresh install crashed Home on resume): let the install source decide.
                AppOpsManager.MODE_DEFAULT
            }
            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> false
                AppOpsManager.MODE_DEFAULT -> try {
                    context.packageManager.getInstallSourceInfo(context.packageName).packageSource in
                        setOf(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE, PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
                else -> true
            }
        }

    /**
     * Android's accessibility list, scrolled to the service and its row highlighted, as Settings'
     * own search does: the service's own page is open to system apps only.
     */
    fun page(): Intent {
        val key = component.flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(ARG_KEY, key)
            .putExtra(SHOW_ARGS, Bundle().apply { putString(ARG_KEY, key) })
    }

    /** App info, whose ⋮ menu has *Allow restricted settings* once the grayed switch was tried. */
    fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Calls [changed] whenever the list of services on changes, by anyone, while the launcher runs. */
    fun watch(changed: () -> Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = changed()
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(ENABLED), false, observer)
        watcher = observer
    }

    fun stop() {
        watcher?.let(resolver::unregisterContentObserver)
        watcher = null
    }

    private fun enabled(): List<ComponentName> =
        Settings.Secure.getString(resolver, ENABLED).orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString)

    private companion object {
        const val ENABLED = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        // AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS, hidden from apps; reading even its own may be refused.
        const val RESTRICTED_OP = "android:access_restricted_settings"
        // Settings' extras for the row to show (its preference key is the component).
        const val ARG_KEY = ":settings:fragment_args_key"
        const val SHOW_ARGS = ":settings:show_fragment_args"
    }
}
