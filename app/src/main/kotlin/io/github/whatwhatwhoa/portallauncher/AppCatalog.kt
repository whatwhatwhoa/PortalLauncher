package io.github.whatwhatwhoa.portallauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.edit
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    private val icon: Drawable,
) {
    /**
     * A private copy per place it is drawn: the render thread reads a drawable's bounds after
     * the frame is recorded, so one drawable shared by a 160 dp tile and a 56 dp dock button
     * would draw both at whichever size was set last.
     */
    fun newIcon(): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon
}

/** Every launchable activity in every profile, kept current by LauncherApps callbacks. */
class AppCatalog(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private var job: Job? = null

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun stop() = launcherApps.unregisterCallback(callback)

    fun launch(entry: AppEntry): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, null, null)
        true
    } catch (e: Exception) {
        false
    }

    /** The dock's default utilities, in this order, skipping any the device doesn't have. */
    fun dockApps(apps: List<AppEntry>): List<AppEntry> {
        val pm = context.packageManager
        fun resolve(intent: Intent) =
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val packages = listOfNotNull(
            resolve(Intent(Settings.ACTION_SETTINGS)),
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.android.vending",
            // With no default browser this resolves to the system chooser, which isn't an app.
            resolve(Intent(Intent.ACTION_VIEW, "https://example.com".toUri())),
        )
        val me = Process.myUserHandle()
        return packages.mapNotNull { pkg -> apps.firstOrNull { it.component.packageName == pkg && it.user == me } }
            .distinctBy { it.key }
    }

    private fun reload() {
        job?.cancel()
        job = scope.launch { apps = withContext(Dispatchers.Default) { query() } }
    }

    private fun query(): List<AppEntry> = userManager.userProfiles.flatMap { user ->
        launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                AppEntry(key(info.componentName, user), info.label.toString(), info.componentName, user, loadIcon(info))
            }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    private fun key(component: ComponentName, user: UserHandle) =
        component.flattenToString() + "#" + userManager.getSerialNumberForUser(user)

    /**
     * Asks for the xxxhdpi icon whatever the screen's density: bitmap layers then arrive at
     * 432 px and are only ever scaled down. Mipmaps keep the small dock size from aliasing.
     */
    private fun loadIcon(info: LauncherActivityInfo): Drawable {
        val icon = info.getIcon(DisplayMetrics.DENSITY_XXXHIGH)
        val layers = if (icon is AdaptiveIconDrawable) listOf(icon.background, icon.foreground) else listOf(icon)
        layers.forEach { (it as? BitmapDrawable)?.bitmap?.setHasMipMap(true) }
        return icon
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Favourite keys in carousel order; a newline-joined string keeps the order a set loses. */
    var favourites: List<String>
        get() = sp.getString("favourites", "").orEmpty().split('\n').filter { it.isNotEmpty() }
        set(value) = sp.edit { putString("favourites", value.joinToString("\n")) }

    var accent: Int
        get() = sp.getInt("accent", 0)
        set(value) = sp.edit { putInt("accent", value) }

    var font: Int
        get() = sp.getInt("font", 0)
        set(value) = sp.edit { putInt("font", value) }

    var askedForHome: Boolean
        get() = sp.getBoolean("askedForHome", false)
        set(value) = sp.edit { putBoolean("askedForHome", value) }
}
