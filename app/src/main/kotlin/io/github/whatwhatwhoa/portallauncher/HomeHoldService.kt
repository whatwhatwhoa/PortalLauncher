package io.github.whatwhatwhoa.portallauncher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
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
        // AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS, hidden from apps; an app may read its own.
        const val RESTRICTED_OP = "android:access_restricted_settings"
        // Settings' extras for the row to show (its preference key is the component).
        const val ARG_KEY = ":settings:fragment_args_key"
        const val SHOW_ARGS = ":settings:show_fragment_args"
    }
}
