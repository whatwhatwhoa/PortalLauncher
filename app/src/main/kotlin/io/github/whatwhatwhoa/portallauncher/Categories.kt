package io.github.whatwhatwhoa.portallauncher

import android.content.pm.ApplicationInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Home's categories, in the owner's order (`decisions/M5-categories.md`); empty ones don't show. */
enum class Category(val label: String) {
    Favorites("Favorites"),
    Games("Games"),
    Entertainment("Entertainment"),
    Social("Social"),
    Utilities("Utilities");

    companion object {
        /** Where the owner can move an app; Favorites is a list of its own (Favorite in the sheet). */
        val movable = listOf(Games, Entertainment, Social, Utilities)

        /** The Category… page's rows: the movable categories, then All apps only (null). */
        val places: List<Category?> = movable + null
    }
}

/** A place's name on the Category… page; null keeps the app off Home, in All apps only. */
val Category?.placeLabel get() = this?.label ?: "All apps only"

/**
 * Where an app starts: the table of well-known apps first, since many manifests say nothing or
 * something odd (a browser declaring itself social); then Android's app category from the
 * manifest (`appCategory`, or the older `isGame`); anything else starts in All apps only (null),
 * so Home holds only what we can place with confidence (owner).
 */
fun seedCategory(packageName: String, category: Int): Category? = starting[packageName] ?: when (category) {
    ApplicationInfo.CATEGORY_GAME -> Category.Games
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> Category.Entertainment
    ApplicationInfo.CATEGORY_SOCIAL -> Category.Social
    else -> null
}

// Popular apps on a gaming handheld, by package name, so a user's Home sorts itself on the first
// run. Only names known for certain: a wrong one just never matches, but costs a reader trust.
private val starting: Map<String, Category> = buildMap {
    fun add(category: Category, vararg packages: String) = packages.forEach { put(it, category) }
    add(
        Category.Games,
        // Emulators and frontends.
        "com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32", "org.dolphinemu.dolphinemu",
        "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold", "xyz.aethersx2.android",
        "com.github.stenzek.duckstation", "org.citra.citra_emu", "io.github.lime3ds.android",
        "org.yuzu.yuzu_emu", "org.vita3k.emulator", "org.mupen64plusae.v3.fzurita",
        "io.recompiled.redream", "com.flycast.emulator", "me.magnum.melonds", "com.dsemu.drastic",
        "com.swordfish.lemuroid", "org.scummvm.scummvm", "com.winlator",
        "com.magneticchen.daijishou", "org.es_de.frontend", "org.pegasus_frontend.android",
        // Stores, streaming and remote play.
        "com.valvesoftware.android.steam.community", "com.valvesoftware.steamlink", "com.limelight",
        "com.nvidia.geforcenow", "com.playstation.remoteplay", "com.scee.psxandroid",
        "com.microsoft.xboxone.smartglass", "com.gamepass", "com.google.android.play.games",
    )
    add(
        Category.Entertainment,
        "com.google.android.youtube", "com.google.android.apps.youtube.music", "org.schabi.newpipe",
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus",
        "com.hulu.plus", "com.wbd.stream", "com.cbs.app", "com.peacocktv.peacockandroid",
        "com.crunchyroll.crunchyroid", "tv.twitch.android.app", "com.google.android.videos",
        "com.plexapp.android", "org.jellyfin.mobile", "org.xbmc.kodi", "org.videolan.vlc",
        "com.spotify.music", "com.aspiro.tidal", "deezer.android.app", "com.soundcloud.android",
        "com.pandora.android", "com.amazon.mp3", "au.com.shiftyjelly.pocketcasts",
        "com.audible.application", "com.amazon.kindle", "org.lineageos.eleven", "org.lineageos.twelve",
    )
    add(
        Category.Social,
        "com.discord", "com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.facebook.katana", "com.instagram.android", "com.instagram.barcelona",
        "com.twitter.android", "com.reddit.frontpage", "xyz.blueskyweb.app", "org.joinmastodon.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.Slack", "jp.naver.line.android",
        "com.tencent.mm", "com.kakao.talk", "com.viber.voip", "com.google.android.apps.messaging",
    )
    add(
        Category.Utilities,
        // Browsers.
        "com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
        "org.lineageos.jelly",
        // The system's own and Google's everyday apps.
        "com.android.settings", "com.android.vending", "com.google.android.googlequicksearchbox",
        "com.android.documentsui", "com.google.android.documentsui", "com.google.android.apps.nbu.files",
        "com.android.calculator2", "com.google.android.calculator", "com.android.deskclock",
        "com.google.android.deskclock", "com.google.android.calendar", "org.lineageos.etar",
        "com.android.contacts", "com.google.android.contacts", "com.android.camera2",
        "org.lineageos.aperture", "org.lineageos.glimpse", "org.lineageos.recorder",
        "com.google.android.apps.photos", "com.google.android.gm", "com.microsoft.office.outlook",
        "com.google.android.apps.maps", "com.google.android.apps.docs", "com.google.android.keep",
        // App stores and tools.
        "org.fdroid.fdroid", "com.aurora.store", "dev.imranr.obtainium", "com.termux",
        "pl.solidexplorer2", "com.mixplorer", "ru.zdevs.zarchiver",
    )
}

/**
 * B on Home: the categories' names stacked in big type on the content margin (Metro), the
 * focused one in the text color. Home stays behind, ghosted and showing the focused category's
 * row, so it previews where A goes (HomeScreen does the ghosting). Up and down move, A goes in,
 * B goes back; a tap on a name goes in, a tap anywhere else goes back.
 */
@Composable
fun CategoriesLayer(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val motion = LocalMotion.current
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { state.closeCategories() }) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = Dimens.edge)) {
            state.categories.forEachIndexed { i, category ->
                val color by animateColorAsState(
                    if (i == state.categoryFocus) p.text else p.muted,
                    if (motion) tween(FOCUS_FADE) else snap(),
                    label = "category",
                )
                BasicText(
                    category.label,
                    Modifier.clickable(interactionSource = null, indication = null) { state.tapCategory(i) },
                    style = t.heading.copy(color = color),
                    maxLines = 1,
                )
            }
        }
    }
}

private const val FOCUS_FADE = 150
