package io.github.whatwhatwhoa.portallauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.edit
import androidx.core.net.toUri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.annotation.DrawableRes
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
    /** Part of the system image: it can't be uninstalled, only its updates. */
    val system: Boolean,
    /** Where the app starts, a category id ([seedCategory]), null for All apps only; the owner's move wins (LauncherState). */
    val seed: String?,
    /** When the app was first installed: one installed after the launcher's first run starts off Home. */
    val installed: Long,
    private val icon: Drawable,
    /** Set for a settings screen with no launcher icon ([AppCatalog.SCREENS]): it opens by this action. */
    val action: String? = null,
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
    // From the application: Android keeps the callback's binder alive after unregistering, and
    // through it the context the service came from; an activity's held every old activity (F28).
    private val launcherApps = context.applicationContext.getSystemService(LauncherApps::class.java)
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

    fun launch(entry: AppEntry, bounds: Rect?, options: Bundle?): Boolean = try {
        if (entry.action != null) {
            val intent = Intent(entry.action).setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent.apply { sourceBounds = bounds }, options)
        } else {
            launcherApps.startMainActivity(entry.component, entry.user, bounds, options)
        }
        true
    } catch (e: Exception) {
        false
    }

    fun showInfo(entry: AppEntry): Boolean = try {
        launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null)
        true
    } catch (e: Exception) {
        false
    }

    /** The system's own uninstall dialog; the catalog callback drops the app once it is gone. */
    fun uninstall(entry: AppEntry): Boolean = try {
        val intent = Intent(Intent.ACTION_DELETE, "package:${entry.component.packageName}".toUri())
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
            AYN_SETTINGS.packageName,
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

    private fun query(): List<AppEntry> = (screens() + userManager.userProfiles.flatMap { user ->
        launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                AppEntry(
                    key(info.componentName, user),
                    info.label.toString(),
                    info.componentName,
                    user,
                    system = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    seed = seedCategory(info.componentName.packageName, info.applicationInfo.category),
                    installed = info.firstInstallTime,
                    icon = crisp(info.getIcon(DisplayMetrics.DENSITY_XXXHIGH)),
                )
            }
    }).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /**
     * The [SCREENS] this device has, as apps of the main profile. They start in All apps only
     * (no seed), so one never changes Home by itself.
     */
    private fun screens(): List<AppEntry> {
        val pm = context.packageManager
        val me = Process.myUserHandle()
        return SCREENS.mapNotNull { (component, action, label, icon) ->
            val info = pm.resolveActivity(Intent(action).setComponent(component), 0)?.activityInfo
                ?.takeIf { it.exported } ?: return@mapNotNull null
            AppEntry(
                key(component, me),
                label,
                component,
                me,
                system = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                seed = null,
                installed = pm.getPackageInfo(component.packageName, 0).firstInstallTime,
                icon = context.getDrawable(icon)!!,
                action = action,
            )
        }
    }

    private fun key(component: ComponentName, user: UserHandle) =
        component.flattenToString() + "#" + userManager.getSerialNumberForUser(user)

    /**
     * An icon asked for at xxxhdpi whatever the screen's density: bitmap layers then arrive at
     * 432 px and are only ever scaled down. Mipmaps keep the small dock size from aliasing.
     */
    private fun crisp(icon: Drawable): Drawable {
        val layers = if (icon is AdaptiveIconDrawable) listOf(icon.background, icon.foreground) else listOf(icon)
        layers.forEach { (it as? BitmapDrawable)?.bitmap?.setHasMipMap(true) }
        return icon
    }

    companion object {
        /** AYN's device settings, on the Portal only inside Android's Settings; a dock default (B7.2). */
        val AYN_SETTINGS = ComponentName("org.lineageos.settings.ayn", "org.lineageos.settings.ayn.AynSettingsActivity")

        /**
         * Settings screens with no launcher icon, listed as apps so the dock can hold them, each
         * with the action Android's Settings opens it by and a name and icon of our own: AYN's
         * calls itself "Gamepad", which beside the games reads like one, under a plain gear.
         */
        val SCREENS = listOf(
            SettingsPage(AYN_SETTINGS, "com.android.settings.action.IA_SETTINGS", "Controller settings", R.drawable.ic_app_controller_settings),
        )
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /**
     * Favorite keys in carousel order; a newline-joined string keeps the order a set loses.
     * The key keeps the British spelling it was first saved under, so existing favorites stay.
     */
    var favorites: List<String>
        get() = sp.getString("favourites", "").orEmpty().split('\n').filter { it.isNotEmpty() }
        set(value) = sp.edit { putString("favourites", value.joinToString("\n")) }

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()).orEmpty().toSet()
        set(value) = sp.edit { putStringSet("hidden", value) }

    /** Dock app keys in order; null until the owner first changes the dock, then the defaults apply. */
    var dock: List<String>?
        get() = sp.getString("dock", null)?.split('\n')?.filter { it.isNotEmpty() }
        set(value) = sp.edit { if (value == null) remove("dock") else putString("dock", value.joinToString("\n")) }

    /**
     * The owner's category choices only, one "key<TAB>id,id…" per line; no category (stored as
     * [ALL_APPS_ONLY]) keeps the app off Home. A built-in category's id is the name it was stored
     * under before categories could change, so older one-category lines still read.
     */
    var categories: Map<String, Set<String>>
        get() = sp.getString("categories", "").orEmpty().split('\n').mapNotNull { line ->
            val (key, ids) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
            key to if (ids == ALL_APPS_ONLY) emptySet() else ids.split(',').toSet()
        }.toMap()
        set(value) = sp.edit {
            putString("categories", value.entries.joinToString("\n") { "${it.key}\t${it.value.joinToString(",").ifEmpty { ALL_APPS_ONLY }}" })
        }

    /**
     * Home's categories in the owner's order, one "id<TAB>name" per line; the built-in ones until
     * they change them. Favorites always comes first, whatever was stored.
     */
    var categoryList: List<Category>
        get() {
            val stored = sp.getString("categories.list", null)?.split('\n')?.mapNotNull { line ->
                line.split('\t').takeIf { it.size == 2 }?.let { (id, label) -> Category(id, label) }
            } ?: return Category.defaults
            return listOf(stored.firstOrNull { it.isFavorites } ?: Category.defaults.first()) + stored.filterNot { it.isFavorites }
        }
        set(value) = sp.edit { putString("categories.list", value.joinToString("\n") { "${it.id}\t${it.label}" }) }

    /** Recent has been put in [categoryList] once, so a Recent the owner deleted stays deleted. */
    var recentAdded: Boolean
        get() = sp.getBoolean("categories.recentAdded", false)
        set(value) = sp.edit { putBoolean("categories.recentAdded", value) }

    /** Recent's app keys (F11), what the launcher opened, latest first; joined as [favorites] are. */
    var recent: List<String>
        get() = sp.getString("recent", "").orEmpty().split('\n').filter { it.isNotEmpty() }
        set(value) = sp.edit { putString("recent", value.joinToString("\n")) }

    /**
     * When the launcher first sorted the apps into categories. Apps installed before then start in
     * their category; ones installed after start in All apps only (owner, 2026-10-25), so a new
     * install never changes Home by itself. Set on the first read.
     */
    val sortedAt: Long
        get() = sp.getLong("apps.sortedAt", 0L).takeIf { it > 0 }
            ?: System.currentTimeMillis().also { now -> sp.edit { putLong("apps.sortedAt", now) } }

    var askedForHome: Boolean
        get() = sp.getBoolean("askedForHome", false)
        set(value) = sp.edit { putBoolean("askedForHome", value) }

    /** Settings → Look; anything not stored yet is the default, so an untouched install is unchanged. */
    var look: LookSettings
        get() {
            val d = LookSettings()
            return LookSettings(
                theme = named("look.theme", d.theme),
                accent = Accent.of(sp.getString("look.accent", null)) ?: d.accent,
                backdrop = sp.getString("look.backdrop", null) ?: d.backdrop,
                covers = sp.getBoolean("look.covers", d.covers),
                effects = Effects(
                    gradient = sp.getBoolean("look.gradient", d.effects.gradient),
                    glow = sp.getBoolean("look.glow", d.effects.glow),
                    depth = sp.getBoolean("look.depth", d.effects.depth),
                    reflection = sp.getBoolean("look.reflection", d.effects.reflection),
                ),
                pulse = named("look.pulse", d.pulse),
                name = named("look.name", d.name),
            )
        }
        set(value) = sp.edit {
            putString("look.theme", value.theme.name)
            putString("look.accent", value.accent.id)
            putString("look.backdrop", value.backdrop)
            putBoolean("look.covers", value.covers)
            putBoolean("look.gradient", value.effects.gradient)
            putBoolean("look.glow", value.effects.glow)
            putBoolean("look.depth", value.effects.depth)
            putBoolean("look.reflection", value.effects.reflection)
            putString("look.pulse", value.pulse.name)
            putString("look.name", value.name.name)
        }

    /** Settings → Home, stored the same way as [look]. */
    var home: HomeSettings
        get() {
            val d = HomeSettings()
            return HomeSettings(
                clock = named("home.clock", d.clock),
                batteryPercent = sp.getBoolean("home.batteryPercent", d.batteryPercent),
                hints = sp.getBoolean("home.hints", d.hints),
                wraps = sp.getBoolean("home.wraps", d.wraps),
                start = named("home.start", d.start),
                playControls = sp.getBoolean("home.playControls", d.playControls),
            )
        }
        set(value) = sp.edit {
            putString("home.clock", value.clock.name)
            putBoolean("home.batteryPercent", value.batteryPercent)
            putBoolean("home.hints", value.hints)
            putBoolean("home.wraps", value.wraps)
            putString("home.start", value.start.name)
            putBoolean("home.playControls", value.playControls)
        }

    /** The id of the category Home was last on, for Start on → Where you left after a restart. */
    var lastCategory: String?
        get() = sp.getString("home.category", null)
        set(value) = sp.edit { putString("home.category", value) }

    /** Where Home was when an app last opened from it, and in which boot ([HomePlace]). */
    var place: HomePlace?
        get() = sp.getString("home.place", null)?.let(HomePlace::parse)
        set(value) = sp.edit { if (value == null) remove("home.place") else putString("home.place", value.stored) }

    /** The owner turned standby's service on and not off since, so the launcher puts it back if Android turns it off. */
    var homeHoldWanted: Boolean
        get() = sp.getBoolean("home.holdWanted", false)
        set(value) = sp.edit { putBoolean("home.holdWanted", value) }

    /** Settings → Art: look art up without being asked; off, the launcher uses the network only from Art…. */
    var fetchArt: Boolean
        get() = sp.getBoolean("art.fetch", true)
        set(value) = sp.edit { putBoolean("art.fetch", value) }

    /** Settings → Sound, stored the same way as [look]. */
    var sound: SoundSettings
        get() {
            val d = SoundSettings()
            return SoundSettings(
                on = sp.getBoolean("sound.on", d.on),
                family = named("sound.family", d.family),
                level = named("sound.level", d.level),
            )
        }
        set(value) = sp.edit {
            putBoolean("sound.on", value.on)
            putString("sound.family", value.family.name)
            putString("sound.level", value.level.name)
        }

    /** Settings → Art: the owner's own SteamGridDB key, or null until one is given. */
    var artKey: String?
        get() = sp.getString("art.key", null)
        set(value) = sp.edit { putString("art.key", value) }

    private companion object {
        const val ALL_APPS_ONLY = "-"
    }

    private inline fun <reified E : Enum<E>> named(key: String, default: E): E =
        sp.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}

/** A settings page listed as an app ([AppCatalog.SCREENS]). */
data class SettingsPage(val component: ComponentName, val action: String, val label: String, @DrawableRes val icon: Int)
