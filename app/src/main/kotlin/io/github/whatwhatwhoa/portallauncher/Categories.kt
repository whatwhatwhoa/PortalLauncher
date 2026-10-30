package io.github.whatwhatwhoa.portallauncher

import android.content.pm.ApplicationInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One of Home's categories (`decisions/M5-categories.md`, F12): an id that never changes and the
 * owner's name for it, which they can change. The built-in ones have their first names as ids,
 * as stored before names could change; the owner's own get a made-up one. Favorites is always
 * first and can only be renamed. Recent (F11) fills itself with what the launcher opened, so no
 * app is put in it by hand. Home shows the ones with apps in them, in the owner's order.
 */
data class Category(val id: String, val label: String) {
    val isFavorites get() = id == FAVORITES
    val isRecent get() = id == RECENT

    companion object {
        const val FAVORITES = "Favorites"
        const val RECENT = "Recent"
        const val GAMES = "Games"
        const val ENTERTAINMENT = "Entertainment"
        const val SOCIAL = "Social"
        const val UTILITIES = "Utilities"

        /** The categories until the owner changes them. */
        val defaults = listOf(FAVORITES, RECENT, GAMES, ENTERTAINMENT, SOCIAL, UTILITIES).map { Category(it, it) }

        /** An id for a category the owner adds, never one used before, so no old move points at it. */
        fun newId() = "c" + System.currentTimeMillis().toString(36)
    }
}

/** What Triangle on a name in the categories layer offers, beside it; Favorites offers Rename only. */
enum class CategoryAction(val label: String) { Rename("Rename"), Move("Move"), Delete("Delete") }

/** Where the categories layer is while the owner changes a category. */
enum class CategoryEdit { Actions, Moving, Renaming }

/** A place's name on the Category… page; null keeps the app off Home, in All apps only. */
val Category?.placeLabel get() = this?.label ?: "All apps only"

/**
 * Where an app starts: the table of well-known apps first, since many manifests say nothing or
 * something odd (a browser declaring itself social); then Android's app category from the
 * manifest (`appCategory`, or the older `isGame`); anything else starts in All apps only (null),
 * so Home holds only what we can place with confidence (owner). Returns a built-in category's id;
 * one the owner has deleted places nothing.
 */
fun seedCategory(packageName: String, category: Int): String? = starting[packageName] ?: when (category) {
    ApplicationInfo.CATEGORY_GAME -> Category.GAMES
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> Category.ENTERTAINMENT
    ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
    else -> null
}

// Popular apps on a gaming handheld, by package name, so a user's Home sorts itself on the first
// run. Only names known for certain: a wrong one just never matches, but costs a reader trust.
private val starting: Map<String, String> = buildMap {
    fun add(category: String, vararg packages: String) = packages.forEach { put(it, category) }
    add(
        Category.GAMES,
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
        Category.ENTERTAINMENT,
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
        Category.SOCIAL,
        "com.discord", "com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.facebook.katana", "com.instagram.android", "com.instagram.barcelona",
        "com.twitter.android", "com.reddit.frontpage", "xyz.blueskyweb.app", "org.joinmastodon.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.Slack", "jp.naver.line.android",
        "com.tencent.mm", "com.kakao.talk", "com.viber.voip", "com.google.android.apps.messaging",
    )
    add(
        Category.UTILITIES,
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
 * B on Home: the names of the categories Home shows, stacked in big type on the content margin
 * (Metro), the focused one in the text color; they scroll once there are more than fit. Home stays
 * behind, ghosted and showing the focused category's row, so it previews where A goes
 * (HomeScreen does the ghosting). Up and down move, A goes in, B goes back; a tap on a name goes
 * in, a tap anywhere else goes back.
 *
 * Triangle is edit mode (F12), kept apart so the layer stays clean (owner): every category, an empty one
 * fainter, and New category after them. A on a name puts Rename, Move and Delete beside it
 * (Favorites: Rename only); A on New category starts one. Triangle or B leaves edit mode.
 */
@Composable
fun CategoriesLayer(state: LauncherState) {
    val t = LocalType.current
    val editing = state.editingCategories
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { state.closeCategories() }) {
        Column(
            Modifier.align(Alignment.CenterStart)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.edge, top = Space.xl, bottom = Space.xl),
        ) {
            state.layerCategories.forEachIndexed { i, category ->
                CategoryLine(state, i, category)
            }
            if (editing) {
                val focused = state.categoryFocus == state.categoryList.size
                InView(state.categoriesOpen && focused) {
                    BasicText(
                        "New category",
                        Modifier.padding(top = Space.m).clickable(interactionSource = null, indication = null) { state.tapCategory(state.categoryList.size) },
                        style = t.title.copy(color = focusColor(focused && state.categoryEdit == null)),
                        maxLines = 1,
                    )
                }
            }
            // Room to scroll the name being typed above the keyboard, which covers the lower half
            // and doesn't resize the window.
            if (state.categoryEdit == CategoryEdit.Renaming) Spacer(Modifier.height(KEYBOARD_ROOM))
        }
        Hints(
            listOfNotNull(
                when {
                    state.categoryEdit != null -> null
                    editing -> Hint(TRIANGLE, "Done")
                    else -> Hint(TRIANGLE, "Edit")
                },
            ),
            Modifier.align(Alignment.BottomEnd).padding(end = Dimens.edge, bottom = Dimens.bottom),
            stacked = true,
        )
    }
}

/** A name, and beside it in edit mode what A opened: its actions, how to move it, or how to fill it when empty. */
@Composable
private fun CategoryLine(state: LauncherState, i: Int, category: Category) {
    val p = LocalPalette.current
    val t = LocalType.current
    val focused = i == state.categoryFocus
    val edit = state.categoryEdit.takeIf { focused }
    InView(state.categoriesOpen && focused, below = if (edit == CategoryEdit.Renaming) KEYBOARD_ROOM else 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            if (edit == CategoryEdit.Renaming) {
                NameField(state)
            } else {
                val color = when {
                    edit == CategoryEdit.Moving -> p.accent
                    focused || state.hasApps(category) -> focusColor(focused)
                    else -> p.muted.copy(alpha = p.muted.alpha * EMPTY_ALPHA)
                }
                BasicText(
                    category.label,
                    Modifier.clickable(interactionSource = null, indication = null) { state.tapCategory(i) },
                    style = t.heading.copy(color = color),
                    maxLines = 1,
                )
            }
            when {
                edit == CategoryEdit.Actions -> Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    state.categoryActions.forEachIndexed { j, action ->
                        val on = j == state.categoryActionFocus
                        BasicText(
                            if (action == CategoryAction.Delete && state.deleteArmed) "Delete? A again" else action.label,
                            Modifier.focusRing(on, Space.m)
                                .background(if (on) p.rule else Color.Transparent, RoundedCornerShape(Space.m))
                                .clickable(interactionSource = null, indication = null) { state.tapCategoryAction(j) }
                                .padding(horizontal = Space.l, vertical = Space.xs),
                            style = t.title.copy(color = p.text),
                            maxLines = 1,
                        )
                    }
                }
                edit == CategoryEdit.Moving -> Note("Up and down move it, A puts it down")
                edit == CategoryEdit.Renaming -> Note("Type a name, then Done")
                focused && state.editingCategories && !state.hasApps(category) -> Note(
                    when {
                        category.isFavorites -> "Empty: Favorite on an app puts it here"
                        category.isRecent -> "Empty: what you open shows here"
                        else -> "Empty: Triangle → Category… on an app puts it here"
                    },
                )
            }
        }
    }
}

/** The focused name's field while renaming; the keyboard comes up with it, Done keeps the name. */
@Composable
private fun NameField(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    BasicTextField(
        value = state.categoryName,
        onValueChange = { state.categoryName = it },
        modifier = Modifier.widthIn(min = NAME_MIN).width(IntrinsicSize.Max).focusRequester(focusRequester),
        textStyle = t.heading.copy(color = p.text),
        singleLine = true,
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { state.finishRename() }),
        decorationBox = { field ->
            Box {
                if (state.categoryName.isEmpty()) BasicText("Name", style = t.heading.copy(color = p.muted.copy(alpha = p.muted.alpha * EMPTY_ALPHA)))
                field()
            }
        },
    )
}

@Composable
private fun Note(text: String) =
    BasicText(text, style = LocalType.current.hint.copy(color = LocalPalette.current.muted), maxLines = 1)

@Composable
private fun focusColor(focused: Boolean): Color {
    val p = LocalPalette.current
    val color by animateColorAsState(
        if (focused) p.text else p.muted,
        if (LocalMotion.current) tween(FOCUS_FADE) else snap(),
        label = "category",
    )
    return color
}

/**
 * Keeps the focused line scrolled into view, and [below] it clear too (the keyboard's room while
 * typing); the list's own padding keeps it off the edges.
 */
@Composable
private fun InView(focused: Boolean, below: Dp = 0.dp, content: @Composable () -> Unit) {
    val inView = remember { BringIntoViewRequester() }
    var height by remember { mutableIntStateOf(0) }
    val belowPx = with(LocalDensity.current) { below.toPx() }
    LaunchedEffect(focused, belowPx) { if (focused) inView.bringIntoView(Rect(0f, 0f, 1f, height + belowPx)) }
    Box(Modifier.bringIntoViewRequester(inView).onSizeChanged { height = it.height }) { content() }
}

private const val FOCUS_FADE = 150
// An empty category's name, and the field's placeholder: fainter than the other names.
private const val EMPTY_ALPHA = 0.5f
// Room for a short name and the cursor while the field is empty.
private val NAME_MIN = 240.dp
// The Portal's keyboard is about 306 dp of the 600 dp canvas.
private val KEYBOARD_ROOM = 320.dp
