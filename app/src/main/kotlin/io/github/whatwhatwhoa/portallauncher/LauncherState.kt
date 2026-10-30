package io.github.whatwhatwhoa.portallauncher

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Screen { Home, Drawer }

/** Where focus is on the home screen. */
enum class Zone { Row, Dock }

/** Where an options sheet was opened from; it decides which options make sense. */
enum class Origin { Row, Dock, Drawer }

/** [enabled] false is shown grayed and does nothing, so the owner sees why it can't be done. */
enum class Option(val label: String, val enabled: Boolean = true) {
    Move("Move"),
    Favorite("Favorite"),
    Unfavorite("Unfavorite"),
    AddToDock("Add to dock"),
    DockFull("Add to dock (full)", enabled = false),
    MoveInDock("Move in dock"),
    RemoveFromDock("Remove from dock"),
    ChangeCategory("Category…"),
    Hide("Hide"),
    Unhide("Unhide"),
    Art("Art…"),
    Info("App info"),
    Uninstall("Uninstall"),
    ShowHidden("Show hidden apps"),
    HideHidden("Hide hidden apps"),
    Settings("Launcher settings…"),
}

class Sheet(val app: AppEntry, val origin: Origin, val options: List<Option>)

/**
 * Where Home was when an app last opened from it (F5): the category, the row's focused app, the
 * zone and the dock's focus, with the boot it happened in. Android may end the launcher while
 * the app runs, or rebuild it; started again in the same boot, Home goes back there.
 */
class HomePlace(val category: String, val app: String?, val zone: Zone, val dock: Int, val boot: Long) {
    // The boot's wall-clock instant moves only with a clock change, so a few seconds' slack.
    val sameBoot get() = abs(boot - bootInstant()) < SAME_BOOT

    val stored get() = listOf(category, app.orEmpty(), zone.name, dock, boot).joinToString("\t")

    companion object {
        private const val SAME_BOOT = 5_000L

        fun bootInstant() = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        fun parse(stored: String): HomePlace? {
            val (category, app, zone, dock, boot) = stored.split('\t').takeIf { it.size == 5 } ?: return null
            return HomePlace(
                category,
                app.ifEmpty { null },
                Zone.entries.firstOrNull { it.name == zone } ?: Zone.Row,
                dock.toIntOrNull() ?: 0,
                boot.toLongOrNull() ?: return null,
            )
        }
    }
}

/**
 * All focus and navigation. Focus is our own index state, not Compose focus: a lazy row can't
 * focus tiles that aren't composed, and a console row pivots instead of moving to the nearest.
 */
class LauncherState(
    private val catalog: AppCatalog,
    private val prefs: Prefs,
    /** The app's art; its SteamGridDB lookups wait for a key ([Art.hasKey]), gallery images don't. */
    val art: Art?,
    private val scope: CoroutineScope,
    /**
     * Opens an app; the rect is where its tile is on screen, for the launch animation. False when
     * it couldn't (MainActivity says so).
     */
    private val launch: (AppEntry, Rect?) -> Boolean,
    /** Opens Android's photo picker; the reply is the chosen image, or null. */
    private val pickImage: ((Uri?) -> Unit) -> Unit,
    /** Where the owner's wallpaper files are (`files/backdrops`); each is a backdrop choice. */
    private val wallpaperDir: File,
    /** Opens one of Android's own screens (settings pages, the grant pages, AYN's settings). */
    private val openSystem: (Intent) -> Unit,
    /** Whether the launcher holds the Home role now. */
    private val holdsHome: () -> Boolean,
    /** Whether another app holds it now; with none, Android asks at the next Home press. */
    private val otherHome: () -> Boolean = { false },
    /** Android's own "use as Home?" dialog; MainActivity takes its answer. */
    private val requestHome: () -> Unit = {},
    /** A short note in a toast, for something Android refused. */
    private val say: (String) -> Unit = {},
    /** Pulls Android's notification shade down; false when nothing could (HomeHoldService.openShade). */
    private val openShade: () -> Boolean = { false },
    /** The Home-hold service's switch (HomeHoldService); null leaves standby off. */
    private val holdSwitch: HomeHoldSwitch? = null,
    /** Brightness, volume and the rest, for the quick panel. */
    val controls: DeviceControls,
    /** The clipboard's text, for pasting a SteamGridDB key without typing it on the controller. */
    private val readClipboard: () -> String? = { null },
    /** The GitHub releases, for the Updates row; null leaves the row saying so. */
    private val updates: Updates? = null,
    /** The UI sounds; null plays none. */
    private val sounds: Sounds? = null,
    /** The setup file in Download (Save setup, Restore setup); null leaves the rows doing nothing. */
    private val setup: Setup? = null,
    /** Opens Android's file picker for a setup file; the reply is the chosen file, or null. */
    private val pickSetup: ((Uri?) -> Unit) -> Unit = {},
) {
    /** The quick panel (Square on Home and All apps), over everything but the settings. */
    var quickOpen by mutableStateOf(false)
        private set
    // Brightness from the very first opening (owner, F21); openQuick sets it again each time.
    private var quickRowIndex by mutableIntStateOf(QuickRow.Brightness.ordinal)
    private var quickColumnIndex by mutableIntStateOf(0)
    val quickFocus get() = QuickRow.entries[quickRowIndex.coerceIn(0, QuickRow.entries.lastIndex)]
    val quickColumn get() = quickColumnIndex.coerceIn(0, maxOf(0, quickColumns(quickFocus) - 1))

    private fun quickColumns(row: QuickRow) = when (row) {
        QuickRow.Media -> QuickMedia.entries.size + 1
        QuickRow.Toggles -> QuickToggle.entries.size
        QuickRow.Links -> controls.links.size
        else -> 1
    }

    /**
     * Notification access is a restricted setting too for an APK from a downloaded file: once its
     * page has been opened and Android still grays the switch, the next A opens App info, whose ⋮
     * menu allows it, and the one after the page again, as the Hold Home row does.
     */
    private var mediaRestricted by mutableStateOf(false)
    /** Nothing could pull the shade down (the Home button service off, Android's call refused). */
    private var shadeMissing by mutableStateOf(false)

    /** The line under the panel: what the focused control needs, or else the focused button's name. */
    val quickNote: String
        get() = when {
            quickFocus == QuickRow.Brightness && !controls.canWrite ->
                "Brightness needs Android's “Modify system settings” for the launcher, once"
            quickFocus == QuickRow.Toggles && quickColumn == QuickToggle.AutoBrightness.ordinal && !controls.canWrite ->
                "Auto brightness needs Android's “Modify system settings” for the launcher, once"
            quickFocus == QuickRow.Toggles && quickColumn == QuickToggle.Quiet.ordinal && !controls.canQuiet ->
                "Do not disturb needs Android's “Do Not Disturb access” for the launcher, once"
            quickFocus == QuickRow.Media && quickColumn == SEEK_COLUMN && !controls.canSeeMedia && mediaRestricted ->
                "Switch grayed out? A opens App info: ⋮ → Allow restricted settings"
            quickFocus == QuickRow.Media && quickColumn == SEEK_COLUMN && !controls.canSeeMedia ->
                "The seek bar needs Android's “Notification access” for the launcher, once"
            quickFocus == QuickRow.Media && quickColumn == SEEK_COLUMN -> "Seek"
            quickFocus == QuickRow.Media -> QuickMedia.entries[quickColumn].label
            quickFocus == QuickRow.Toggles -> QuickToggle.entries[quickColumn].label
            quickFocus == QuickRow.Links && controls.links[quickColumn] == QuickLink.Notifications && shadeMissing ->
                "Notifications: switch on “Portal: Home button and notifications”, once"
            quickFocus == QuickRow.Links -> controls.links[quickColumn].label
            else -> ""
        }
    var screen by mutableStateOf(Screen.Home)
        private set
    // Home's place at the last launch, when the launcher was ended or rebuilt in this boot while
    // that app ran (cleared on coming back, [refreshSettings]); the row's app is found once the
    // catalog lists it (the init after [row]).
    private val restored = prefs.place?.takeIf { it.sameBoot }
    var zone by mutableStateOf(restored?.zone ?: Zone.Row)
        private set
    private var rowIndex by mutableIntStateOf(0)
    private var dockIndex by mutableIntStateOf(restored?.dock ?: 0)
    private var drawerIndex by mutableIntStateOf(0)

    /** All apps' search, typed on the letter strip; empty shows every app. */
    var drawerQuery by mutableStateOf("")
        private set
    /** The letter strip in All apps' header has focus, not the grid. */
    var searchFocused by mutableStateOf(false)
        private set
    private var searchIndex by mutableIntStateOf(0)
    val searchLetter get() = searchIndex.coerceIn(0, SEARCH_LETTERS.lastIndex)
    /** Counts L1/R1 letter jumps, so the header can light the letter after each. */
    var letterJumps by mutableIntStateOf(0)
        private set

    // The last sheet stays set after it closes, so its exit animation still has something to draw.
    var sheet by mutableStateOf<Sheet?>(null)
        private set
    var sheetOpen by mutableStateOf(false)
        private set
    private var sheetIndex by mutableIntStateOf(0)

    /** The sheet's Art… page, while it is showing; it takes input before the options. */
    var picker by mutableStateOf<ArtPicker?>(null)
        private set

    /** The sheet's Category… page, while it is showing: the movable categories as its rows. */
    var choosingCategory by mutableStateOf(false)
        private set

    /** Set while a tile is being moved: left and right shift it, A or B puts it down. */
    var moving by mutableStateOf<Origin?>(null)
        private set

    /**
     * The categories layer (B on Home), and which line in it has focus: a category, or New
     * category after the last one ([categoryList]'s size).
     */
    var categoriesOpen by mutableStateOf(false)
        private set
    private var categoryIndex by mutableIntStateOf(0)
    /**
     * Home's category, by id, so a rename keeps it. A restart while an app opened from Home runs
     * goes back to where it opened from, whatever Home key goes to says; any other start (a
     * reboot, an update while Home shows) goes by Home key goes to, Where you left reopening the
     * last one.
     */
    private var currentCategory by mutableStateOf(
        restored?.category ?: prefs.lastCategory.takeIf { prefs.home.start == StartOn.Left } ?: Category.FAVORITES,
    )
    /** Every category, the empty ones too, in the owner's order: the layer's names. */
    var categoryList by mutableStateOf(withRecent(prefs.categoryList))
        private set

    /**
     * Recent goes in after Favorites once, on an install from before it (F11); from then on the
     * owner's list stands, so a deleted Recent stays deleted and a moved one where it was put.
     */
    private fun withRecent(list: List<Category>): List<Category> {
        if (prefs.recentAdded) return list
        prefs.recentAdded = true
        if (list.any { it.isRecent }) return list
        return (list.take(1) + Category(Category.RECENT, Category.RECENT) + list.drop(1)).also { prefs.categoryList = it }
    }

    /**
     * The layer's edit mode (Triangle in it): every category, the empty ones too, and New category after
     * them; out of it the layer is only the names Home shows (owner: clean, editing apart).
     */
    var editingCategories by mutableStateOf(false)
        private set
    /** What A on a name in edit mode opened, while it is open; null is moving between names. */
    var categoryEdit by mutableStateOf<CategoryEdit?>(null)
        private set
    private var actionIndex by mutableIntStateOf(0)
    /** Delete is waiting for its second A; any other key disarms it. */
    var deleteArmed by mutableStateOf(false)
        private set
    /** The name being typed while renaming; the field writes it. */
    var categoryName by mutableStateOf("")
    // The category being named is a new one, not saved until it has a name.
    private var addingCategory = false

    private var favorites by mutableStateOf(prefs.favorites)
    private var recent by mutableStateOf(prefs.recent)
    private var hidden by mutableStateOf(prefs.hidden)
    private var dockKeys by mutableStateOf(prefs.dock)
    private var categoryMoves by mutableStateOf(prefs.categories)
    private val sortedAt = prefs.sortedAt
    var showHidden by mutableStateOf(false)
        private set

    /** Settings → Look, applied live as it changes. */
    var look by mutableStateOf(prefs.look)
        private set
    /** The settings panel, over everything; it takes input first. */
    var settingsOpen by mutableStateOf(false)
        private set
    private var settingsIndex by mutableIntStateOf(0)
    val settingsFocus get() = settingsIndex.coerceIn(0, groupSettings.lastIndex)
    private var groupIndex by mutableIntStateOf(0)
    val settingsGroupFocus get() = groupIndex.coerceIn(0, settingGroups.lastIndex)
    /** Focus in the rows, or (false) in the categories at the left. */
    var settingsInRows by mutableStateOf(false)
        private set

    /** The panel's rows; the Art group only when there is art at all. */
    val settings = Setting.entries.filter { art != null || it.group != SettingGroup.Art }
    val settingGroups = settings.map { it.group }.distinct()
    val settingsGroup get() = settingGroups[settingsGroupFocus]
    /** The chosen category's rows, the page beside the categories. */
    val groupSettings get() = settings.filter { it.group == settingsGroup }

    /** Settings → Home, applied live as it changes. */
    var home by mutableStateOf(prefs.home)
        private set
    var fetchArt by mutableStateOf(prefs.fetchArt)
        private set
    /** Settings → Sound, applied live; the player keeps a copy, as it plays outside Compose. */
    var sound by mutableStateOf(prefs.sound)
        private set
    init {
        sounds?.settings = sound
    }
    /** What the SteamGridDB key row says while a key is checked or after it failed; null shows Set or Not set. */
    var keyNote by mutableStateOf<String?>(null)
        private set
    /** Bumped when the art is cleared, so it is looked up again. */
    var artCleared by mutableIntStateOf(0)
        private set
    // Read each time the settings open (and on coming back to them), as their values show.
    var artBytes by mutableStateOf<Long?>(null)
        private set
    var isHomeApp by mutableStateOf(false)
        private set
    /** Another app is Home (the Home app row says so rather than Not set). */
    var homeElsewhere by mutableStateOf(false)
        private set
    /** Whether holding Home opens standby (HomeHoldService on); kept current by [checkHomeHold]. */
    var homeHold by mutableStateOf(false)
        private set
    /** Android grays the service's switch out until it is allowed in App info ([HomeHoldSwitch.restricted]). */
    var homeHoldRestricted by mutableStateOf(false)
        private set
    /** The Hold Home row has opened Android's page since the launcher started, so a still-gray switch was seen. */
    var homeHoldTried by mutableStateOf(false)
        private set
    // The note that standby went off shows once per start of the launcher, not at every Home press.
    private var offNoteShown = false
    /** What the Updates row says: the check's answer, then the download and install. */
    var updateNote by mutableStateOf<String?>(null)
        private set
    var saveNote by mutableStateOf<String?>(null)
        private set
    var restoreNote by mutableStateOf<String?>(null)
        private set
    /** The newer release the last check found. */
    private var release: Release? = null
    private var updateCheckedAt = 0L
    private var updating: Job? = null
    /** A row that can't be undone, waiting for its second A; any other key disarms it. */
    var armed by mutableStateOf<Setting?>(null)
        private set
    val dockChanged get() = dockKeys != null

    // Read again each time the settings open, so a wallpaper pushed to the Portal shows up.
    private var wallpapers by mutableStateOf(findWallpapers())

    /** The backdrop choices: the plain two, Hero glow, then the owner's wallpaper files. */
    val backdrops get() = listOf(Backdrop.Black, Backdrop.Dark, Backdrop.Glow) + wallpapers
    val backdrop get() = backdrops.firstOrNull { it.id == look.backdrop } ?: Backdrop.Black

    private fun findWallpapers(): List<Backdrop> =
        wallpaperDir.listFiles { f -> f.isFile && f.extension.lowercase() in WALLPAPER_TYPES }
            ?.sortedBy { it.name.lowercase() }
            ?.map { Backdrop.Wallpaper(it) }
            .orEmpty()

    // Where each tile was last laid out, in window pixels. Plain, not state: only a launch reads it.
    private val placed = HashMap<String, Rect>()

    fun place(origin: Origin, entry: AppEntry, bounds: Rect) {
        placed["$origin/${entry.key}"] = bounds
    }

    // Only a start that worked sounds, goes in Recent and is kept as Home's place; a failed one
    // has its toast and a bump.
    private fun launchFrom(origin: Origin, entry: AppEntry) {
        if (!launch(entry, placed["$origin/${entry.key}"])) {
            sounds?.play(Sound.Edge)
            return
        }
        sounds?.play(Sound.Launch)
        val shown = category
        played(entry)
        // With Favorites empty Home shows the first category with apps; Recent filling up mustn't
        // take the row over.
        if (category.id != shown.id) currentCategory = shown.id
        // On Recent the app just opened is first now; focus goes with it, so it is there on coming back.
        if (category.isRecent && row.firstOrNull()?.key == entry.key) rowIndex = 0
        prefs.place = HomePlace(category.id, row.getOrNull(rowFocus)?.key, zone, dockFocus, HomePlace.bootInstant())
    }

    /**
     * An app opened from the launcher goes first in Recent, the list kept to [RECENT_KEPT]. The
     * dock's utilities (Settings, Files, the browser) and Android's settings pages never show
     * there, docked or not, so they aren't kept; it is written only when the first app changes.
     */
    private fun played(entry: AppEntry) {
        val pkg = entry.component.packageName
        if (entry.action != null || catalog.dockApps(catalog.apps).any { it.component.packageName == pkg }) return
        if (recent.firstOrNull() == entry.key) return
        recent = (listOf(entry.key) + (recent - entry.key)).take(RECENT_KEPT)
        prefs.recent = recent
    }

    private val byKey by derivedStateOf { catalog.apps.associateBy { it.key } }

    // A search keeps the alphabetical order, so the letter jumps still work and the grid doesn't
    // reshuffle as letters are added; how well each matches only decides where focus starts.
    val drawer: List<AppEntry> by derivedStateOf {
        val shown = if (showHidden) catalog.apps else catalog.apps.filter { it.key !in hidden }
        if (drawerQuery.isEmpty()) shown else shown.filter { searchRank(it.label, drawerQuery) != null }
    }
    val hiddenCount by derivedStateOf { catalog.apps.count { it.key in hidden } }

    /**
     * Apps whose art is looked up without asking: the owner's favorites, Recent's apps and the
     * Games category, whatever they are called now; a deleted one adds none. A favorite or recent
     * app that is one of Android's own waits for Art… unless it is in Games.
     */
    val artWanted: List<AppEntry> by derivedStateOf {
        (favorites.mapNotNull { byKey[it] } + byId(Category.RECENT)?.let(::appsIn).orEmpty())
            .filterNot { isPlatformApp(it.component.packageName) } +
            byId(Category.GAMES)?.let(::appsIn).orEmpty()
    }

    /** The owner's dock once they have changed it; until then the default utilities. */
    val dock: List<AppEntry> by derivedStateOf {
        dockKeys?.mapNotNull { byKey[it] } ?: catalog.dockApps(catalog.apps)
    }

    private fun byId(id: String?) = categoryList.firstOrNull { it.id == id }

    /**
     * The categories an app is in, any number (owner: an app can show in several): the owner's
     * choice wins over where the app starts; none is All apps only, not on Home, as is a
     * category that has been deleted.
     */
    fun categoriesOf(app: AppEntry): List<Category> {
        val ids = categoryMoves[app.key] ?: setOfNotNull(startOf(app))
        return categoryList.filter { it.id in ids }
    }

    /** Where the app is now, as ids: the owner's choice, else where it started. */
    private fun idsOf(app: AppEntry): Set<String> = categoriesOf(app).mapTo(HashSet()) { it.id }

    /** Whether the Category… page dots this place for the app; All apps only (null) when it is in none. */
    fun isIn(app: AppEntry, place: Category?) = if (place == null) categoriesOf(app).isEmpty() else place in categoriesOf(app)

    /** The app's seed if it was there when the launcher first sorted the apps, else All apps only. */
    private fun startOf(app: AppEntry): String? = app.seed?.takeIf { app.installed <= sortedAt }

    /**
     * Favorites in the owner's order; Recent what the launcher opened, latest first; any other
     * category is its visible apps, alphabetical.
     */
    private fun appsIn(category: Category): List<AppEntry> = when {
        category.isFavorites -> favorites.mapNotNull { byKey[it] }
        category.isRecent -> recentApps
        else -> catalog.apps.filter { it.key !in hidden && category.id in (categoryMoves[it.key] ?: setOfNotNull(startOf(it))) }
    }

    // Not what is hidden, nor what is on the dock: it is a button away there already.
    private val recentApps: List<AppEntry> by derivedStateOf {
        val docked = dock.mapTo(HashSet()) { it.key }
        recent.mapNotNull { byKey[it] }.filter { it.key !in hidden && it.key !in docked }.take(RECENT_SHOWN)
    }

    /** The categories with something in them, in the owner's order: the ones Home shows. */
    val categories: List<Category> by derivedStateOf { categoryList.filter { appsIn(it).isNotEmpty() } }

    fun hasApps(category: Category) = category in categories

    /**
     * Where the sheet's Category… page can move an app: every category but Favorites and Recent,
     * which fill themselves, then All apps only (null).
     */
    val places: List<Category?> get() = categoryList.filterNot { it.isFavorites || it.isRecent } + null

    /** Home's category; if it has emptied, the first one that hasn't. */
    val category get() = categories.firstOrNull { it.id == currentCategory } ?: categories.firstOrNull() ?: categoryList.first()

    /** While the layer is open, the row previews the focused category instead. */
    private val shownCategory get() = if (categoriesOpen) layerCategories.getOrNull(categoryFocus) ?: category else category

    /** The row's category, by id: Home keys its tiles by it, so two rows never share one. */
    val shownCategoryId get() = shownCategory.id

    /** The layer's names: the ones Home shows, or in edit mode every one. */
    val layerCategories get() = if (editingCategories) categoryList else categories

    val row: List<AppEntry> by derivedStateOf { appsIn(shownCategory) }

    init {
        // The restored place's app, once the catalog (loading as this is built) lists the apps.
        restored?.app?.let { key ->
            scope.launch {
                snapshotFlow { catalog.apps.isNotEmpty() }.first { it }
                rowIndex = row.indexOfFirst { it.key == key }.coerceAtLeast(0)
            }
        }
    }

    // Lists change under us (an uninstall, a new favorite), so indices are clamped on read.
    // A previewed category shows from its start.
    val rowFocus get() = if (shownCategory != category) 0 else rowIndex.coerceIn(0, maxOf(0, row.lastIndex))
    /** 0 is the All apps button; dock apps follow from 1, then the Launcher Settings button. */
    val dockFocus get() = dockIndex.coerceIn(0, dockSettings)
    val dockSettings get() = dock.size + 1
    val drawerFocus get() = drawerIndex.coerceIn(0, maxOf(0, drawer.lastIndex))
    /** The focused app's letter, for All apps' header; null with nothing to show. */
    val drawerLetter get() = if (drawer.isEmpty()) null else letter(drawerFocus)
    val sheetFocus get() = sheetIndex.coerceIn(0, maxOf(0, sheetRows - 1))
    /** In edit mode [categoryList]'s size is New category, after the last name. */
    val categoryFocus get() = categoryIndex.coerceIn(0, if (editingCategories) categoryList.size else maxOf(0, categories.lastIndex))
    private val sheetRows get() = if (choosingCategory) places.size else sheet?.options?.size ?: 0
    /** The focused name's actions, beside it, while + has opened them. */
    val categoryActions get() =
        if (categoryList.getOrNull(categoryFocus)?.isFavorites == true) listOf(CategoryAction.Rename) else CategoryAction.entries
    val categoryActionFocus get() = actionIndex.coerceIn(0, categoryActions.lastIndex)

    fun isFavorite(entry: AppEntry) = entry.key in favorites
    fun isHidden(entry: AppEntry) = entry.key in hidden

    /** Whether + does anything where focus is now (All apps and Launcher Settings have none). */
    val focusHasOptions get() = screen == Screen.Drawer || zone == Zone.Row || dockFocus in 1..dock.size

    /**
     * The action under way is a held key's repeat. Moves repeat, but a held key does harm where
     * one press is one deed (a track skipped, a category turned, a setting flipped), so those
     * act on the first press only.
     */
    private var held = false

    /** [repeat] is a held key's repeat (MainActivity), for [held]. */
    fun onAction(action: Action, repeat: Boolean = false) = sounding(action) {
        held = repeat
        when {
            settingsOpen -> onSettings(action)
            quickOpen -> onQuick(action)
            sheetOpen -> onSheet(action)
            moving != null -> onMove(action)
            categoriesOpen -> onCategories(action)
            screen == Screen.Home -> onHome(action)
            else -> onDrawer(action)
        }
    }

    /**
     * Runs [block], then plays the sound for what it did (Sounds.kt [cue]), unless it asked for one
     * itself (a launch, waking). [action] null makes no sound.
     */
    private fun sounding(action: Action?, block: () -> Unit) {
        val sounds = sounds ?: return block()
        val before = spot()
        val asked = sounds.asked
        block()
        if (sounds.asked == asked) cue(before, spot(), action)?.let { (sound, pitch) -> sounds.play(sound, pitch) }
    }

    private fun onHome(action: Action) {
        when (action) {
            Action.Left -> if (zone == Zone.Row) rowIndex = rowStep(-1) else dockIndex = dockFocus - 1
            Action.Right -> if (zone == Zone.Row) rowIndex = rowStep(1) else dockIndex = dockFocus + 1
            Action.Down -> zone = Zone.Dock
            Action.Up -> zone = Zone.Row
            // Bumpers: the categories, or with Home play controls the tracks (owner, F34); once a
            // press, as held they sent a burst of skips or turned through every category.
            Action.PagePrev -> if (held) Unit else if (home.playControls) skip(QuickMedia.Previous.key) else turnCategory(-1)
            Action.PageNext -> if (held) Unit else if (home.playControls) skip(QuickMedia.Next.key) else turnCategory(1)
            Action.R3 -> if (home.playControls) skip(QuickMedia.PlayPause.key)
            Action.First -> if (zone == Zone.Row) rowIndex = 0
            Action.Last -> if (zone == Zone.Row) rowIndex = row.lastIndex
            Action.Open -> openFocused()
            Action.Options -> optionsForFocused()
            Action.Y -> openDrawer()
            Action.Back -> openCategories()
            Action.X -> Unit
            Action.Select -> openQuick()
        }
        rowIndex = rowFocus
        dockIndex = dockFocus
    }

    /** One tile along the row; past an end it stops, or goes round when Carousel wraps is on. */
    private fun rowStep(by: Int) = if (home.wraps && row.size > 1) Math.floorMod(rowFocus + by, row.size) else rowFocus + by

    private fun onCategories(action: Action) {
        categoryEdit?.let { return onCategoryEdit(it, action) }
        when (action) {
            Action.Up -> categoryIndex = categoryFocus - 1
            Action.Down -> categoryIndex = categoryFocus + 1
            Action.First -> categoryIndex = 0
            Action.Last -> categoryIndex = if (editingCategories) categoryList.size else categories.lastIndex
            Action.Open -> if (editingCategories) editCategory() else enterCategory(categoryFocus)
            Action.Options -> if (editingCategories) stopEditingCategories() else startEditingCategories()
            Action.Back -> if (editingCategories) stopEditingCategories() else closeCategories()
            else -> Unit
        }
        categoryIndex = categoryFocus
    }

    // While renaming, keys go to the name's field (MainActivity), B ending it.
    private fun onCategoryEdit(edit: CategoryEdit, action: Action) {
        when (edit) {
            CategoryEdit.Actions -> {
                if (action != Action.Open) deleteArmed = false
                when (action) {
                    Action.Left -> actionIndex = categoryActionFocus - 1
                    Action.Right -> actionIndex = categoryActionFocus + 1
                    Action.Open -> categoryAction(categoryActions[categoryActionFocus])
                    Action.Back, Action.Options -> categoryEdit = null
                    else -> Unit
                }
                actionIndex = categoryActionFocus
            }
            CategoryEdit.Moving -> when (action) {
                Action.Up -> shiftCategory(-1)
                Action.Down -> shiftCategory(1)
                Action.Open, Action.Back, Action.Options -> categoryEdit = null
                else -> Unit
            }
            CategoryEdit.Renaming -> Unit
        }
    }

    private fun onDrawer(action: Action) {
        if (searchFocused) return onSearch(action)
        val cols = Dimens.DRAWER_COLUMNS
        val i = drawerFocus
        when (action) {
            Action.Left -> drawerIndex = i - 1
            Action.Right -> drawerIndex = i + 1
            // Up from the first row goes to the letters, on a fresh press: a held Up stops at the top.
            Action.Up -> if (i >= cols) drawerIndex = i - cols else if (!held) searchFocused = true
            Action.Down -> if (i / cols < drawer.lastIndex / cols) drawerIndex = minOf(i + cols, drawer.lastIndex)
            Action.PagePrev -> jumpTo(previousLetter(i))
            Action.PageNext -> jumpTo(nextLetter(i))
            Action.First -> drawerIndex = 0
            Action.Last -> drawerIndex = drawer.lastIndex
            Action.Open -> drawer.getOrNull(i)?.let(::launchAndClose)
            Action.X -> drawer.getOrNull(i)?.let(::toggleFavorite)
            Action.Options -> optionsForFocused()
            // With a search, B clears it first (keeping the app in focus), and B again closes.
            Action.Back -> if (drawerQuery.isNotEmpty()) clearSearch() else closeDrawer()
            Action.Y -> searchFocused = true
            Action.Select -> openQuick()
            Action.R3 -> Unit
        }
        drawerIndex = drawerFocus
    }

    /** An L1/R1 letter jump; the header lights the letter for a moment when it lands somewhere new. */
    private fun jumpTo(index: Int) {
        if (index != drawerFocus) letterJumps++
        drawerIndex = index
    }

    /**
     * The letter strip (Y in All apps), typing with the controller alone: left and right choose a
     * letter (wrapping), A adds it, X takes the last one back; Down, Y or B go to the results,
     * the search kept.
     */
    private fun onSearch(action: Action) {
        val letters = SEARCH_LETTERS.length
        when (action) {
            Action.Left -> searchIndex = Math.floorMod(searchLetter - 1, letters)
            Action.Right -> searchIndex = Math.floorMod(searchLetter + 1, letters)
            Action.First -> searchIndex = 0
            Action.Last -> searchIndex = letters - 1
            Action.Open -> addLetter(searchLetter)
            Action.X -> if (drawerQuery.isNotEmpty()) searchFor(drawerQuery.dropLast(1))
            Action.Down, Action.Y, Action.Back -> searchFocused = false
            // Triangle opens the options of the app focus goes back to, as it does in the results.
            Action.Options -> if (drawer.isNotEmpty()) {
                searchFocused = false
                optionsForFocused()
            }
            Action.Select -> openQuick()
            else -> Unit
        }
    }

    private fun addLetter(index: Int) = searchFor(drawerQuery + SEARCH_LETTERS[index].lowercaseChar())

    /** A new search: focus on its best match, the list itself staying alphabetical. */
    private fun searchFor(query: String) {
        drawerQuery = query
        drawerIndex = if (query.isEmpty()) 0 else drawer.indices.minByOrNull { searchRank(drawer[it].label, query) ?: Int.MAX_VALUE } ?: 0
    }

    /** Every app again, the focused one still in focus. */
    private fun clearSearch() {
        val focused = drawer.getOrNull(drawerFocus)
        searchFocused = false
        if (drawerQuery.isEmpty()) return
        drawerQuery = ""
        drawerIndex = drawer.indexOfFirst { it.key == focused?.key }.coerceAtLeast(0)
    }

    fun tapSearch() {
        if (screen == Screen.Drawer) searchFocused = true
    }

    fun tapSearchLetter(index: Int) {
        searchIndex = index
        addLetter(searchLetter)
    }

    private fun onSheet(action: Action) {
        picker?.let {
            // Triangle closes the sheet from the Art… page and its grids too, as from the options.
            if (action == Action.Options) return closeSheet()
            if (!it.onAction(action, held)) picker = null
            return
        }
        when (action) {
            Action.Up -> sheetIndex = sheetFocus - 1
            Action.Down -> sheetIndex = sheetFocus + 1
            Action.Open -> if (choosingCategory) chooseCategory(sheetFocus) else choose(sheetFocus)
            Action.Back -> if (choosingCategory) backToOptions() else closeSheet()
            Action.Options -> closeSheet()
            else -> Unit
        }
        sheetIndex = sheetFocus
    }

    /**
     * Two columns, as the Switch's settings: the categories, where moving shows each one's page at
     * once, and A or right goes into its rows; there left and right change a value and B comes
     * back out. L1 and R1 turn the category from either, Triangle closes from anywhere.
     */
    private fun onSettings(action: Action) {
        if (accentOpen) return onAccent(action)
        if (action != Action.Open) armed = null
        when (action) {
            Action.PagePrev -> turnGroup(settingsGroupFocus - 1)
            Action.PageNext -> turnGroup(settingsGroupFocus + 1)
            Action.Options -> closeSettings()
            else -> if (settingsInRows) onSettingRows(action) else onSettingGroups(action)
        }
        settingsIndex = settingsFocus
    }

    private fun onSettingGroups(action: Action) {
        when (action) {
            Action.Up -> turnGroup(settingsGroupFocus - 1)
            Action.Down -> turnGroup(settingsGroupFocus + 1)
            Action.First -> turnGroup(0)
            Action.Last -> turnGroup(settingGroups.lastIndex)
            Action.Right, Action.Open -> settingsInRows = true
            Action.Back -> closeSettings()
            else -> Unit
        }
    }

    private fun onSettingRows(action: Action) {
        val setting = groupSettings[settingsFocus]
        when (action) {
            Action.Up -> settingsIndex = settingsFocus - 1
            Action.Down -> settingsIndex = settingsFocus + 1
            Action.First -> settingsIndex = 0
            Action.Last -> settingsIndex = groupSettings.lastIndex
            // A step a press: these lists are short, and held they flipped 20 times a second
            // (Theme flashing light and dark). The accent's sliders, a page of their own, repeat.
            Action.Left -> if (!setting.acts && !held) change(setting, -1)
            Action.Right -> if (!setting.acts && !held) change(setting, 1)
            Action.Open -> if (setting == Setting.Accent) openAccent() else if (setting.acts) act(setting) else change(setting, 1)
            Action.Back -> settingsInRows = false
            else -> Unit
        }
    }

    /** Another category's page, its first row; focus stays in whichever column it was. */
    private fun turnGroup(index: Int) {
        val to = index.coerceIn(0, settingGroups.lastIndex)
        if (to == settingsGroupFocus) return
        groupIndex = to
        settingsIndex = 0
    }

    fun tapGroup(index: Int) {
        armed = null
        turnGroup(index)
        settingsInRows = false
    }

    fun tapSetting(index: Int) {
        if (index != settingsFocus) armed = null
        settingsIndex = index
        settingsInRows = true
        val setting = groupSettings[settingsFocus]
        if (setting == Setting.Accent) openAccent() else if (setting.acts) act(setting) else change(setting, 1)
    }

    /**
     * The accent picker, a page of the settings panel (A on Accent): two rows of four presets,
     * then a custom hue and intensity. Each change applies at once, as the settings do: moving
     * over a swatch wears it, left and right on a slider make a custom color from the one shown.
     */
    var accentOpen by mutableStateOf(false)
        private set
    private var accentRowIndex by mutableIntStateOf(0)
    private var accentColumnIndex by mutableIntStateOf(0)
    val accentRow get() = accentRowIndex.coerceIn(0, ACCENT_ROWS - 1)
    val accentColumn get() = accentColumnIndex.coerceIn(0, SWATCH_COLUMNS - 1)
    /**
     * The last custom accent, so a stray press onto a preset can be stepped back from: it is a
     * stop after the presets on the Accent row. Kept while the launcher runs.
     */
    private var customKept: Accent? = look.accent.takeIf { it.preset == null }

    private fun openAccent() {
        val preset = look.accent.preset
        accentRowIndex = preset?.let { it.ordinal / SWATCH_COLUMNS } ?: HUE_ROW
        accentColumnIndex = preset?.let { it.ordinal % SWATCH_COLUMNS } ?: 0
        accentOpen = true
    }

    private fun onAccent(action: Action) {
        val row = accentRow
        when (action) {
            Action.Up -> accentRowIndex = row - 1
            Action.Down -> accentRowIndex = row + 1
            Action.Left -> stepAccent(row, -1)
            Action.Right -> stepAccent(row, 1)
            Action.Open, Action.Back -> accentOpen = false
            Action.Options -> closeSettings()
            else -> Unit
        }
        accentRowIndex = accentRow
        // Landing on a swatch row wears the swatch under focus, as moving along it does; coming
        // up from the sliders only looks, so a custom color isn't lost to one press of Up.
        if (accentRow != row && accentRow < SWATCH_ROWS && row < SWATCH_ROWS) wearSwatch()
    }

    private fun stepAccent(row: Int, by: Int) {
        val accent = look.accent
        when (row) {
            HUE_ROW -> setAccent(customAccent(hue = (accent.hue ?: 0f) + by * HUE_STEP, chroma = accent.customChroma))
            INTENSITY_ROW -> setAccent(customAccent(hue = accent.hue ?: 0f, chroma = accent.customChroma + by * CHROMA_STEP))
            else -> {
                accentColumnIndex = accentColumn + by
                wearSwatch()
            }
        }
    }

    private fun wearSwatch() {
        AccentPreset.entries.getOrNull(accentRow * SWATCH_COLUMNS + accentColumn)?.let { setAccent(it.accent) }
    }

    fun tapSwatch(preset: AccentPreset) {
        accentRowIndex = preset.ordinal / SWATCH_COLUMNS
        accentColumnIndex = preset.ordinal % SWATCH_COLUMNS
        setAccent(preset.accent)
    }

    /** A tap on the hue or intensity track sets it there. */
    fun tapAccentSlider(row: Int, fraction: Float) {
        accentRowIndex = row
        val accent = look.accent
        val f = fraction.coerceIn(0f, 1f)
        setAccent(
            if (row == HUE_ROW) {
                customAccent(hue = f * 360f, chroma = accent.customChroma)
            } else {
                customAccent(hue = accent.hue ?: 0f, chroma = Accent.MIN_CHROMA + f * (Accent.MAX_CHROMA - Accent.MIN_CHROMA))
            },
        )
    }

    private fun setAccent(accent: Accent) {
        if (accent.preset == null) customKept = accent
        look = look.copy(accent = accent)
        prefs.look = look
    }

    /** The Accent row's next stop: the presets, then the last custom color, wrapping. */
    private fun steppedAccent(by: Int): Accent {
        val stops = AccentPreset.entries.map { it.accent } + listOfNotNull(customKept)
        val i = stops.indexOf(look.accent)
        return if (i < 0) look.accent.cycled(by) else stops[Math.floorMod(i + by, stops.size)]
    }

    private fun change(setting: Setting, by: Int) {
        when (setting.group) {
            SettingGroup.Look -> {
                look = if (setting == Setting.Accent) look.copy(accent = steppedAccent(by)) else look.step(setting, by, backdrops)
                prefs.look = look
            }
            SettingGroup.Home -> {
                home = home.step(setting, by)
                prefs.home = home
            }
            SettingGroup.Sound -> {
                sound = sound.step(setting, by)
                prefs.sound = sound
                sounds?.settings = sound
                // A new family or volume is heard at once, in itself.
                if (setting != Setting.SoundsOn) sounds?.play(Sound.Select)
            }
            SettingGroup.Art -> if (setting == Setting.FetchArt) {
                fetchArt = !fetchArt
                prefs.fetchArt = fetchArt
            }
            // Every category named, no else: a new one with value rows must say where they go,
            // or its rows quietly do nothing (B7.1).
            SettingGroup.Apps, SettingGroup.System -> Unit
        }
    }

    private fun act(setting: Setting) {
        if (setting.confirms && armed != setting) {
            armed = setting
            return
        }
        armed = null
        when (setting) {
            Setting.ArtKey -> takeKey()
            Setting.ClearArt -> scope.launch {
                art?.clear()
                artBytes = art?.bytes()
                artCleared++
            }
            Setting.HiddenApps -> if (hiddenCount > 0) {
                closeSettings()
                // The settings can be over All apps: no search may hide the hidden apps.
                drawerQuery = ""
                searchFocused = false
                showHidden = true
                // On the first hidden one, not at the top with the faded tiles somewhere below.
                drawerIndex = drawer.indexOfFirst { it.key in hidden }.coerceAtLeast(0)
                screen = Screen.Drawer
            }
            Setting.ResetDock -> {
                dockKeys = null
                prefs.dock = null
                dockIndex = 0
            }
            // Not Home: Android's own dialog, one A (MainActivity falls back to the page). Home: the
            // Default apps page, where another launcher can be picked back.
            Setting.HomeApp -> if (isHomeApp) openSystem(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) else requestHome()
            Setting.HomeHold -> holdSwitch?.let { sw ->
                when {
                    // Granted over adb: A flips it here, without Android's page.
                    sw.canSwitch -> {
                        sw.switch(!homeHold)
                        homeHold = sw.on
                        prefs.homeHoldWanted = homeHold
                    }
                    // App info's ⋮ menu offers Allow restricted settings only after the switch was tried.
                    !homeHold && homeHoldTried && homeHoldRestricted -> openSystem(sw.appInfo())
                    else -> {
                        homeHoldTried = true
                        openSystem(sw.page())
                    }
                }
            }
            Setting.AndroidSettings -> openSystem(Intent(android.provider.Settings.ACTION_SETTINGS))
            Setting.Updates -> update()
            Setting.SaveSetup -> saveSetup()
            Setting.RestoreSetup -> restoreSetup()
            else -> Unit
        }
    }

    private fun saveSetup() {
        val setup = setup ?: return
        saveNote = "Saving…"
        scope.launch {
            saveNote = try {
                withContext(Dispatchers.IO) { setup.save() }
                "In Download"
            } catch (e: Exception) {
                Log.w(Setup.TAG, "Saving the setup failed", e)
                "Couldn't save"
            }
        }
    }

    // The launcher starts afresh after a restore, so the note only ever shows a refusal.
    private fun restoreSetup() {
        val setup = setup ?: return
        restoreNote = null
        pickSetup { uri ->
            if (uri == null) return@pickSetup
            restoreNote = "Restoring…"
            scope.launch {
                val refused = withContext(Dispatchers.IO) { setup.restore(uri) }
                if (refused == null) setup.restart() else restoreNote = refused
            }
        }
    }

    /** From the options sheet (Triangle) or Android's launcher-settings gear (MainActivity). */
    fun openSettings() {
        // Android's gear can come mid-rename; a field left open under the panel kept the keys.
        if (categoryEdit == CategoryEdit.Renaming) finishRename()
        wallpapers = findWallpapers()
        closeSheet()
        groupIndex = 0
        settingsIndex = 0
        settingsInRows = false
        armed = null
        accentOpen = false
        saveNote = null
        restoreNote = null
        settingsOpen = true
        refreshSettings()
    }

    /** The values that change outside the panel; again on coming back from Android's settings. */
    fun refreshSettings() {
        // Home's place at a launch serves only the restart while that app runs; back in front
        // (this runs on every resume, after [restored] is read), a later restart starts as usual.
        prefs.place = null
        if (quickOpen) controls.read()
        checkHomeHold(watched = false)
        if (!settingsOpen) return
        isHomeApp = holdsHome()
        homeElsewhere = !isHomeApp && otherHome()
        scope.launch { artBytes = art?.bytes() }
        // Asked again after a while, or at once after a failed check; never while one runs.
        val stale = System.currentTimeMillis() - updateCheckedAt > UPDATE_CHECK_MS
        if (stale && updating?.isActive != true) checkUpdate()
    }

    /**
     * The service's switch as Android has it, on every resume and ([watched]) whenever it changes
     * while the launcher runs. Seen on, the owner wants it. Seen going off while the launcher runs,
     * the owner (or a policy) turned it off, so it isn't wanted any more. Found off at a start or
     * resume while still wanted, Android turned it off with a force stop (which ends the launcher
     * first): it goes back on where the launcher may switch it, else Home says so once.
     */
    fun checkHomeHold(watched: Boolean) {
        val sw = holdSwitch ?: return
        homeHold = sw.on
        when {
            homeHold -> prefs.homeHoldWanted = true
            watched -> prefs.homeHoldWanted = false
            prefs.homeHoldWanted && sw.canSwitch -> {
                sw.switch(true)
                homeHold = sw.on
            }
        }
        homeHoldRestricted = !homeHold && sw.restricted
    }

    private fun checkUpdate() {
        val updates = updates ?: return
        updateNote = "Checking…"
        updating = scope.launch {
            updateNote = try {
                val latest = updates.latest()
                release = latest?.takeIf { Updates.isNewer(it.version, BuildConfig.VERSION_NAME) }
                updateCheckedAt = System.currentTimeMillis()
                when {
                    latest == null -> "None published"
                    release == null -> "Up to date"
                    else -> "Install ${latest.version}"
                }
            } catch (e: Exception) {
                updateCheckedAt = 0L
                "Offline, try again"
            }
        }
    }

    /**
     * A on Updates: checks again, or fetches the newer release and installs it. The first time,
     * Android's "Install unknown apps" page opens for the one-time grant; A again after it.
     */
    private fun update() {
        val updates = updates ?: return
        if (updating?.isActive == true) return
        val release = release ?: return checkUpdate()
        if (!updates.mayInstall()) {
            updateNote = "Allow, then A again"
            openSystem(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${BuildConfig.APPLICATION_ID}".toUri()))
            return
        }
        Updates.failed = { updateNote = it }
        updating = scope.launch {
            updateNote = "Downloading"
            try {
                val apk = updates.download(release) { updateNote = "Downloading $it%" }
                val refusal = updates.refusal(apk)
                updateNote = refusal ?: "Installing…"
                // Android ends this process when the update is in; the note stays meanwhile.
                if (refusal == null) updates.install(apk)
            } catch (e: Exception) {
                updateNote = "Offline, try again"
            }
        }
    }

    /**
     * The key from the clipboard: copied on SteamGridDB's site (Preferences → API), A here. It is
     * kept only once SteamGridDB accepts it, and then the art is looked up.
     */
    private fun takeKey() {
        val art = art ?: return
        val candidate = readClipboard()?.trim().orEmpty()
        if (!KEY_SHAPE.matches(candidate)) {
            keyNote = "Copy your key first"
            return
        }
        keyNote = "Checking…"
        scope.launch {
            keyNote = try {
                if (art.accepts(candidate)) {
                    prefs.artKey = candidate
                    art.key = candidate
                    null
                } else {
                    "Not accepted"
                }
            } catch (e: Exception) {
                "Offline, try again"
            }
        }
    }

    fun closeSettings() {
        settingsOpen = false
        keyNote = null
        accentOpen = false
        armed = null
    }

    private fun openQuick() {
        controls.read()
        // Brightness first, always: the player's row above it is a press of Up away (owner, F21).
        quickRowIndex = QuickRow.Brightness.ordinal
        quickColumnIndex = 0
        quickColumnsLeft.fill(0)
        quickOpen = true
    }

    // Each row's column while the panel is open, so coming back to a row lands where it was left
    // (one shared column was clamped by the one-wide sliders on the way).
    private val quickColumnsLeft = IntArray(QuickRow.entries.size)

    private fun toQuickRow(row: Int) {
        quickColumnsLeft[quickFocus.ordinal] = quickColumn
        quickRowIndex = row
        quickColumnIndex = quickColumnsLeft[quickFocus.ordinal]
    }

    fun closeQuick() {
        quickOpen = false
    }

    private fun onQuick(action: Action) {
        val row = quickFocus
        when (action) {
            // The seek bar is the top row's last stop: Up leaves it for Next, as Down leaves it below.
            Action.Up -> if (row == QuickRow.Media && quickColumn == SEEK_COLUMN) {
                quickColumnIndex = SEEK_COLUMN - 1
            } else {
                toQuickRow((row.ordinal - 1).coerceAtLeast(0))
            }
            Action.Down -> toQuickRow((row.ordinal + 1).coerceAtMost(QuickRow.entries.lastIndex))
            Action.Left -> quickStep(row, -1)
            Action.Right -> quickStep(row, 1)
            Action.Open -> quickOpenFocused(row, quickColumn)
            // A skip a press, as on Home.
            Action.PagePrev -> if (!held) skip(QuickMedia.Previous.key)
            Action.PageNext -> if (!held) skip(QuickMedia.Next.key)
            Action.Back, Action.Select -> closeQuick()
            else -> Unit
        }
        quickColumnIndex = quickColumn
    }

    private fun quickStep(row: QuickRow, by: Int) {
        when (row) {
            QuickRow.Brightness -> controls.stepBrightness(by)
            QuickRow.Volume -> controls.stepVolume(by)
            // On the seek bar, left and right seek; Up or Down leave it, as on a TV player. With
            // nothing to seek (no access, nothing playing, a player that can't), Left leaves too.
            QuickRow.Media -> when {
                quickColumn != SEEK_COLUMN -> quickColumnIndex = quickColumn + by
                controls.canSeeMedia && controls.nowPlaying?.seekable == true -> controls.seekBy(by * SEEK_STEP)
                by < 0 -> quickColumnIndex = SEEK_COLUMN - 1
            }
            else -> quickColumnIndex = quickColumn + by
        }
    }

    /**
     * L1 or R1 (and R3 on Home): a media key, focus left where it is; it chimes, since nothing on
     * screen moves.
     */
    private fun skip(key: Int) {
        press(key)
        sounds?.play(Sound.Select)
    }

    private fun press(media: QuickMedia) = press(media.key)

    private fun press(key: Int) {
        controls.press(key)
        scope.launch {
            delay(MEDIA_SETTLE)
            controls.read()
        }
    }

    /** A on a control: a toggle flips, a link opens; one missing its grant opens the grant page. */
    private fun quickOpenFocused(row: QuickRow, column: Int) {
        when (row) {
            QuickRow.Media -> when {
                column < SEEK_COLUMN -> press(QuickMedia.entries[column])
                !controls.canSeeMedia -> openMediaGrant()
            }
            QuickRow.Brightness -> if (!controls.canWrite) openSystem(writeSettingsGrant())
            QuickRow.Volume -> Unit
            QuickRow.Toggles -> when (QuickToggle.entries[column]) {
                QuickToggle.AutoBrightness -> if (controls.canWrite) controls.toggleAutoBrightness() else openSystem(writeSettingsGrant())
                QuickToggle.Quiet -> if (controls.canQuiet) controls.toggleQuiet() else openSystem(quietGrant())
            }
            QuickRow.Links -> controls.links[column].intent?.let { openSystem(it()) } ?: openNotifications()
        }
    }

    // Alternates with App info while Android grays the switch out ([mediaRestricted]).
    private fun openMediaGrant() {
        val sw = holdSwitch
        if (mediaRestricted && sw != null) {
            mediaRestricted = false
            openSystem(sw.appInfo())
        } else {
            mediaRestricted = sw?.restricted == true
            openSystem(mediaGrant())
        }
    }

    /**
     * Notifications: the panel closes and Android's shade comes down over Home. With no way to
     * pull it, the panel stays and Android's page for the Home button service opens (App info
     * once the switch was seen grayed out), and the line under the panel says what it needs.
     */
    private fun openNotifications() {
        if (openShade()) {
            shadeMissing = false
            return closeQuick()
        }
        shadeMissing = true
        val sw = holdSwitch ?: return
        if (homeHoldTried && homeHoldRestricted) {
            openSystem(sw.appInfo())
        } else {
            homeHoldTried = true
            openSystem(sw.page())
        }
    }

    fun tapQuick(row: QuickRow, column: Int) {
        toQuickRow(row.ordinal)
        quickColumnIndex = column
        quickOpenFocused(row, column)
    }

    /** A tap on a slider's track sets it there. */
    fun tapQuickSlider(row: QuickRow, fraction: Float) {
        toQuickRow(row.ordinal)
        if (row == QuickRow.Media) quickColumnIndex = SEEK_COLUMN
        when (row) {
            QuickRow.Brightness -> controls.moveBrightnessTo(fraction)
            QuickRow.Volume -> controls.moveVolumeTo((fraction * controls.volumeMax).roundToInt())
            QuickRow.Media -> if (controls.canSeeMedia) controls.seekTo(fraction) else openMediaGrant()
            else -> Unit
        }
    }

    private fun onMove(action: Action) {
        when (action) {
            Action.Left -> shift(-1)
            Action.Right -> shift(1)
            Action.Open, Action.Back, Action.Options -> moving = null
            else -> Unit
        }
    }

    private fun openFocused() {
        if (zone == Zone.Row) {
            row.getOrNull(rowFocus)?.let { launchFrom(Origin.Row, it) }
        } else if (dockFocus == 0) {
            openDrawer()
        } else if (dockFocus == dockSettings) {
            openSettings()
        } else {
            dock.getOrNull(dockFocus - 1)?.let { launchFrom(Origin.Dock, it) }
        }
    }

    /** Touch dragging the carousel: focus moves along it, nothing opens. */
    fun focusRow(index: Int) {
        if (moving != null) return
        zone = Zone.Row
        rowIndex = index.coerceIn(0, maxOf(0, row.lastIndex))
    }

    fun tapRow(index: Int) {
        if (moving != null) {
            moving = null
            return
        }
        zone = Zone.Row
        rowIndex = index
        openFocused()
    }

    fun tapDock(index: Int) {
        if (moving != null) {
            moving = null
            return
        }
        zone = Zone.Dock
        dockIndex = index
        openFocused()
    }

    fun tapDrawer(index: Int) {
        drawerIndex = index
        drawer.getOrNull(index)?.let(::launchAndClose)
    }

    /** A long press: focus moves to the tile, then its options open. */
    fun holdRow(index: Int) {
        zone = Zone.Row
        rowIndex = index
        optionsForFocused()
    }

    fun holdDock(index: Int) {
        zone = Zone.Dock
        dockIndex = index
        optionsForFocused()
    }

    fun holdDrawer(index: Int) {
        searchFocused = false
        drawerIndex = index
        optionsForFocused()
    }

    /** A touch scroll left the focus off screen: it moves to a tile in view, and nothing opens. */
    fun focusDrawer(index: Int) {
        drawerIndex = index.coerceIn(0, maxOf(0, drawer.lastIndex))
    }

    fun tapSheet(index: Int) = if (choosingCategory) chooseCategory(index) else choose(index)

    fun tapCategory(index: Int) {
        if (categoryEdit != null) return
        categoryIndex = index
        if (editingCategories) editCategory() else enterCategory(categoryFocus)
    }

    fun tapCategoryAction(index: Int) {
        if (index != categoryActionFocus) deleteArmed = false
        actionIndex = index
        categoryAction(categoryActions[categoryActionFocus])
    }

    /**
     * All apps always opens on every app, on the one last in focus. The last search is dropped
     * here rather than on closing, so the grid sinks away as it was.
     */
    fun openDrawer() {
        if (categoriesOpen) return
        clearSearch()
        searchIndex = 0
        screen = Screen.Drawer
    }

    fun closeDrawer() {
        screen = Screen.Home
    }

    fun closeSheet() {
        sheetOpen = false
        picker = null
        choosingCategory = false
    }

    private fun openCategories() {
        categoryIndex = categories.indexOf(category).coerceAtLeast(0)
        categoriesOpen = true
    }

    fun closeCategories() {
        if (categoryEdit == CategoryEdit.Renaming) finishRename()
        categoryEdit = null
        editingCategories = false
        categoriesOpen = false
    }

    /** Triangle in the layer: edit mode, on the same name. */
    private fun startEditingCategories() {
        categoryIndex = categoryList.indexOf(categories.getOrNull(categoryFocus)).coerceAtLeast(0)
        editingCategories = true
    }

    /** Out of edit mode, on the same name if Home shows it, else on Home's category. */
    private fun stopEditingCategories() {
        val focused = categoryList.getOrNull(categoryFocus)
        editingCategories = false
        categoryIndex = categories.indexOf(focused).takeIf { it >= 0 } ?: categories.indexOf(category).coerceAtLeast(0)
    }

    /** L1 or R1 on Home: the category before or after, its row from the start; the ends stop. */
    private fun turnCategory(by: Int) {
        val next = categories.getOrNull(categories.indexOf(category) + by) ?: return
        currentCategory = next.id
        prefs.lastCategory = next.id
        rowIndex = 0
    }

    /** Into a category from the layer: its row from the start, unless it is the one already shown. */
    private fun enterCategory(index: Int) {
        val chosen = categories.getOrNull(index) ?: return
        if (chosen != category) {
            currentCategory = chosen.id
            prefs.lastCategory = chosen.id
            rowIndex = 0
        }
        zone = Zone.Row
        categoriesOpen = false
    }

    /** A in edit mode: a name's actions beside it, Rename first; on New category, a new one. */
    private fun editCategory() {
        if (categoryFocus == categoryList.size) return addCategory()
        actionIndex = 0
        deleteArmed = false
        categoryEdit = CategoryEdit.Actions
    }

    private fun categoryAction(action: CategoryAction) {
        when (action) {
            CategoryAction.Rename -> {
                categoryName = categoryList.getOrNull(categoryFocus)?.label ?: return
                categoryEdit = CategoryEdit.Renaming
            }
            CategoryAction.Move -> categoryEdit = CategoryEdit.Moving
            CategoryAction.Delete -> if (deleteArmed) deleteCategory() else deleteArmed = true
        }
    }

    /** A new category at the end, its name typed at once; it is kept only once it has one. */
    private fun addCategory() {
        categoryList = categoryList + Category(Category.newId(), "")
        categoryIndex = categoryList.lastIndex
        categoryName = ""
        addingCategory = true
        categoryEdit = CategoryEdit.Renaming
    }

    /**
     * The typed name, spaces tidied, becomes the category's (tabs and line breaks can't reach the
     * stored list). Left empty, a rename keeps the old name and a new category goes away.
     */
    fun finishRename() {
        val name = categoryName.trim().split(Regex("\\s+")).joinToString(" ")
        val target = categoryList.getOrNull(categoryFocus)
        when {
            target == null -> Unit
            name.isNotEmpty() -> saveCategoryList(categoryList.map { if (it.id == target.id) it.copy(label = name) else it })
            addingCategory -> categoryList = categoryList - target
        }
        addingCategory = false
        categoryEdit = null
        categoryIndex = categoryFocus
    }

    /** Moving a category up or down the list; Favorites stays first. */
    private fun shiftCategory(by: Int) {
        val i = categoryFocus
        if (i == 0 || i + by !in 1..categoryList.lastIndex) return
        saveCategoryList(categoryList.toMutableList().apply { add(i + by, removeAt(i)) })
        categoryIndex = i + by
    }

    /**
     * Deletes the focused category; its apps go to All apps only (owner), the ones that started
     * in it and the ones moved there alike.
     */
    private fun deleteCategory() {
        val target = categoryList.getOrNull(categoryFocus)?.takeUnless { it.isFavorites } ?: return
        saveCategoryList(categoryList - target)
        val moves = categoryMoves.mapValues { (_, ids) -> ids - target.id }
        categoryMoves = moves
        prefs.categories = moves
        deleteArmed = false
        categoryEdit = null
        categoryIndex = categoryFocus
    }

    private fun saveCategoryList(list: List<Category>) {
        categoryList = list
        prefs.categoryList = list
    }

    /** Back as a gesture: closes the innermost layer. */
    fun back() = when {
        settingsOpen -> closeSettings()
        quickOpen -> closeQuick()
        picker?.typing == true -> picker?.stopTyping()
        picker?.browsing != null -> picker?.stopBrowsing()
        picker != null -> picker = null
        choosingCategory -> backToOptions()
        sheetOpen -> closeSheet()
        moving != null -> moving = null
        categoriesOpen -> when {
            categoryEdit == CategoryEdit.Renaming -> finishRename()
            categoryEdit != null -> categoryEdit = null
            editingCategories -> stopEditingCategories()
            else -> closeCategories()
        }
        // In All apps as with B: out of the letters, then the search cleared, then closed.
        searchFocused -> searchFocused = false
        drawerQuery.isNotEmpty() && screen == Screen.Drawer -> clearSearch()
        else -> closeDrawer()
    }

    val backHandled get() = settingsOpen || quickOpen || sheetOpen || moving != null || categoriesOpen || screen == Screen.Drawer

    /**
     * The Home key: closes everything, keeping the place on the row. Pressed while already home
     * with nothing open (not on the way back from an app), it goes back to the first category's
     * start, as the Switch does, unless Home key goes to is Where you left. Coming back from an
     * app makes no sound; pressed at home it sounds as B.
     */
    fun onHomePressed(alreadyHome: Boolean) = sounding(Action.Back.takeIf { alreadyHome }) { goHome(alreadyHome) }

    /** Where Home key goes to Favorites takes Home: the first category it shows, whatever it is called. */
    val startCategory get() = categories.firstOrNull() ?: categoryList.first()

    private fun goHome(alreadyHome: Boolean) {
        if (homeHold) {
            flashStandbyHint(HOLD_HINT, HINT_SHOWN)
        } else if (prefs.homeHoldWanted && !offNoteShown) {
            offNoteShown = true
            flashStandbyHint(OFF_NOTE, OFF_NOTE_SHOWN)
        }
        // A press that closes something only closes it, as Launcher3's and the Switch's do; the
        // owner was somewhere on the row and wants to stay there.
        val layered = settingsOpen || quickOpen || sheetOpen || moving != null || categoriesOpen || screen == Screen.Drawer
        closeSettings()
        closeQuick()
        closeSheet()
        moving = null
        // Not only hidden: a rename left open kept its field taking the keys with nothing shown.
        closeCategories()
        screen = Screen.Home
        if (!alreadyHome || layered || home.start == StartOn.Left) return
        currentCategory = startCategory.id
        prefs.lastCategory = currentCategory
        zone = Zone.Row
        rowIndex = 0
    }

    /** "Hold Home for standby", shown briefly on each Home press while holding Home opens it. */
    var standbyHint by mutableStateOf(false)
        private set
    /** The pill's words; kept after it hides, so its fade still has them. */
    var standbyHintText by mutableStateOf(HOLD_HINT)
        private set
    private var hintTimer: Job? = null

    private fun flashStandbyHint(text: String, shown: Long) {
        hintTimer?.cancel()
        standbyHintText = text
        standbyHint = true
        hintTimer = scope.launch {
            delay(shown)
            standbyHint = false
        }
    }

    private fun optionsForFocused() {
        val (app, origin) = when {
            screen == Screen.Drawer -> drawer.getOrNull(drawerFocus) to Origin.Drawer
            zone == Zone.Row -> row.getOrNull(rowFocus) to Origin.Row
            else -> dock.getOrNull(dockFocus - 1) to Origin.Dock
        }
        if (app == null) return
        sheet = Sheet(app, origin, optionsFor(app, origin))
        sheetIndex = 0
        sheetOpen = true
    }

    private fun optionsFor(app: AppEntry, origin: Origin) = buildList {
        val favorite = app.key in favorites
        val docked = dock.any { it.key == app.key }
        when (origin) {
            // Only Favorites has an order of its own; the other categories are alphabetical.
            Origin.Row -> if (favorite && category.isFavorites) add(Option.Move)
            Origin.Dock -> add(Option.Move)
            Origin.Drawer -> Unit
        }
        if (origin != Origin.Dock) add(if (favorite) Option.Unfavorite else Option.Favorite)
        if (docked) {
            // From the dock itself, Move above does it.
            if (origin != Origin.Dock) add(Option.MoveInDock)
            add(Option.RemoveFromDock)
        } else {
            add(if (dock.size < DOCK_MAX) Option.AddToDock else Option.DockFull)
        }
        add(Option.ChangeCategory)
        if (origin != Origin.Dock) add(if (app.key in hidden) Option.Unhide else Option.Hide)
        if (art != null) add(Option.Art)
        add(Option.Info)
        if (!app.system) add(Option.Uninstall)
        if (origin == Origin.Drawer && (showHidden || hiddenCount > 0)) {
            add(if (showHidden) Option.HideHidden else Option.ShowHidden)
        }
        // The launcher's own, so it comes last, after everything about this app.
        add(Option.Settings)
    }

    private fun choose(index: Int) {
        val sheet = sheet ?: return
        val option = sheet.options.getOrNull(index) ?: return
        val app = sheet.app
        if (!option.enabled) {
            sounds?.play(Sound.Edge)
            return
        }
        if (option == Option.Art) {
            art?.let { picker = ArtPicker(app, it, scope, pickImage) }
            return
        }
        if (option == Option.ChangeCategory) {
            choosingCategory = true
            sheetIndex = places.indexOfFirst { isIn(app, it) }.coerceAtLeast(0)
            return
        }
        closeSheet()
        when (option) {
            Option.Move -> moving = sheet.origin
            Option.Favorite, Option.Unfavorite -> toggleFavorite(app)
            Option.AddToDock -> saveDock(dock.map { it.key } + app.key)
            Option.MoveInDock -> moveInDock(app)
            Option.RemoveFromDock -> saveDock(dock.map { it.key } - app.key)
            Option.DockFull -> Unit
            Option.Hide -> hide(app)
            Option.Unhide -> saveHidden(hidden - app.key)
            Option.Art, Option.ChangeCategory -> Unit
            Option.Info -> if (!catalog.showInfo(app)) say("Couldn't open App info")
            Option.Uninstall -> if (!catalog.uninstall(app)) say("Couldn't uninstall ${app.label}")
            Option.ShowHidden -> showHidden = true
            Option.HideHidden -> showHidden = false
            Option.Settings -> openSettings()
        }
    }

    private fun backToOptions() {
        choosingCategory = false
        sheetIndex = sheet?.options?.indexOf(Option.ChangeCategory)?.coerceAtLeast(0) ?: 0
    }

    /**
     * Puts the sheet's app in a category or takes it out (it can be in several), or All apps only
     * takes it out of all; the page stays, B goes back. Only changes are stored, so putting an
     * app back where it started drops the entry. If it leaves the row on show, the row's focus
     * is clamped onto its neighbor.
     */
    private fun chooseCategory(index: Int) {
        val app = sheet?.app ?: return
        if (index !in places.indices) return
        val now = idsOf(app)
        val chosen = places[index]?.id
        val next = when (chosen) {
            null -> emptySet()
            in now -> now - chosen
            else -> now + chosen
        }
        val moves = if (next == setOfNotNull(byId(startOf(app))?.id)) categoryMoves - app.key else categoryMoves + (app.key to next)
        categoryMoves = moves
        prefs.categories = moves
    }

    /** Swaps the moving tile with its neighbor; focus follows it. */
    private fun shift(by: Int) {
        when (moving) {
            Origin.Row -> {
                val keys = row.map { it.key }.toMutableList()
                val i = rowFocus
                if (i + by !in keys.indices) return
                keys.add(i + by, keys.removeAt(i))
                saveFavorites(keys)
                rowIndex = i + by
            }
            Origin.Dock -> {
                val keys = dock.map { it.key }.toMutableList()
                val i = dockFocus - 1
                if (i !in keys.indices || i + by !in keys.indices) return
                keys.add(i + by, keys.removeAt(i))
                saveDock(keys)
                dockIndex = i + by + 1
            }
            Origin.Drawer, null -> Unit
        }
    }

    /** From the row or All apps: Home, focus on the app's dock button, picked up as Move does. */
    private fun moveInDock(app: AppEntry) {
        val i = dock.indexOfFirst { it.key == app.key }
        if (i < 0) return
        closeDrawer()
        zone = Zone.Dock
        dockIndex = i + 1
        moving = Origin.Dock
    }

    // A hidden app leaves the row and the dock too; unhiding doesn't put it back.
    private fun hide(app: AppEntry) {
        saveHidden(hidden + app.key)
        if (app.key in favorites) saveFavorites(favorites - app.key)
        if (dock.any { it.key == app.key }) saveDock(dock.map { it.key } - app.key)
    }

    /** Labels are sorted, so a letter's apps are adjacent; anything not starting with a letter is #. */
    private fun letter(i: Int) = drawer[i].label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

    private fun letterStart(i: Int): Int {
        var j = i
        while (j > 0 && letter(j - 1) == letter(i)) j--
        return j
    }

    private fun nextLetter(i: Int): Int {
        if (drawer.isEmpty()) return 0
        var j = i
        while (j < drawer.lastIndex && letter(j) == letter(i)) j++
        return if (letter(j) != letter(i)) j else i
    }

    /** To the start of this letter; from its start, to the start of the one before. */
    private fun previousLetter(i: Int): Int {
        if (drawer.isEmpty()) return 0
        val start = letterStart(i)
        return if (start < i || start == 0) start else letterStart(start - 1)
    }

    private fun launchAndClose(entry: AppEntry) {
        launchFrom(Origin.Drawer, entry)
        closeDrawer()
    }

    private fun toggleFavorite(entry: AppEntry) =
        saveFavorites(if (entry.key in favorites) favorites - entry.key else favorites + entry.key)

    private fun saveFavorites(keys: List<String>) {
        favorites = keys
        prefs.favorites = keys
    }

    private fun saveDock(keys: List<String>) {
        dockKeys = keys
        prefs.dock = keys
    }

    private fun saveHidden(keys: Set<String>) {
        hidden = keys
        prefs.hidden = keys
    }

    private companion object {
        const val DOCK_MAX = 6
        // Recent keeps more than it shows, so apps left out (hidden, docked) don't empty the row.
        const val RECENT_KEPT = 24
        const val RECENT_SHOWN = 12
        // How long a player takes to start or stop after the media key, before reading it again.
        const val MEDIA_SETTLE = 400L
        // One Left or Right on the seek bar, as a player's own skip-ahead.
        const val SEEK_STEP = 10_000L
        // The owner's "0.5 or so seconds", plus the fades.
        const val HINT_SHOWN = 600L
        const val HOLD_HINT = "Hold Home for standby"
        // Shown when a Home press finds standby off though it was on: long enough to read twice.
        const val OFF_NOTE = "Standby is off: Launcher Settings → Home turns it back on"
        const val OFF_NOTE_SHOWN = 4000L
        val WALLPAPER_TYPES = setOf("jpg", "jpeg", "png", "webp")
        // SteamGridDB's API keys are 32 hex digits.
        val KEY_SHAPE = Regex("[0-9a-fA-F]{32}")
        /** How long a check holds before the panel asks GitHub again. */
        const val UPDATE_CHECK_MS = 10 * 60_000L
    }
}
