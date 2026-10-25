package io.github.whatwhatwhoa.portallauncher

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

enum class Screen { Home, Drawer }

/** Where focus is on the home screen. */
enum class Zone { Row, Dock }

/** Where an options sheet was opened from; it decides which options make sense. */
enum class Origin { Row, Dock, Drawer }

enum class Option(val label: String) {
    Move("Move"),
    Favorite("Favorite"),
    Unfavorite("Unfavorite"),
    AddToDock("Add to dock"),
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
 * All focus and navigation. Focus is our own index state, not Compose focus: a lazy row can't
 * focus tiles that aren't composed, and a console row pivots instead of moving to the nearest.
 */
class LauncherState(
    private val catalog: AppCatalog,
    private val prefs: Prefs,
    /** Null when there is no SteamGridDB key: the Art option is then left out. */
    val art: Art?,
    private val scope: CoroutineScope,
    /** Opens an app; the rect is where its tile is on screen, for the launch animation. */
    private val launch: (AppEntry, Rect?) -> Unit,
    /** Opens Android's photo picker; the reply is the chosen image, or null. */
    private val pickImage: ((Uri?) -> Unit) -> Unit,
    /** Where the owner's wallpaper files are (`files/backdrops`); each is a backdrop choice. */
    private val wallpaperDir: File,
    /** Opens one of Android's own screens (settings pages, the grant pages, AYN's settings). */
    private val openSystem: (Intent) -> Unit,
    /** Whether the launcher holds the Home role now. */
    private val holdsHome: () -> Boolean,
    /** Brightness, volume and the rest, for the quick panel. */
    val controls: DeviceControls,
) {
    /** The quick panel (− on Home and All apps), over everything but the settings. */
    var quickOpen by mutableStateOf(false)
        private set
    private var quickRowIndex by mutableIntStateOf(0)
    private var quickColumnIndex by mutableIntStateOf(0)
    val quickFocus get() = QuickRow.entries[quickRowIndex.coerceIn(0, QuickRow.entries.lastIndex)]
    val quickColumn get() = quickColumnIndex.coerceIn(0, maxOf(0, quickColumns(quickFocus) - 1))

    private fun quickColumns(row: QuickRow) = when (row) {
        QuickRow.Toggles -> QuickToggle.entries.size
        QuickRow.Links -> QuickLink.entries.size
        else -> 1
    }

    /** The line under the panel: what the focused control needs, or else the focused button's name. */
    val quickNote: String
        get() = when {
            quickFocus == QuickRow.Brightness && !controls.canWrite ->
                "Brightness needs Android's “Modify system settings” for the launcher, once"
            quickFocus == QuickRow.Toggles && quickColumn == QuickToggle.AutoBrightness.ordinal && !controls.canWrite ->
                "Auto brightness needs Android's “Modify system settings” for the launcher, once"
            quickFocus == QuickRow.Toggles && quickColumn == QuickToggle.Quiet.ordinal && !controls.canQuiet ->
                "Do not disturb needs Android's “Do Not Disturb access” for the launcher, once"
            quickFocus == QuickRow.Toggles -> QuickToggle.entries[quickColumn].label
            quickFocus == QuickRow.Links -> QuickLink.entries[quickColumn].label
            else -> ""
        }
    var screen by mutableStateOf(Screen.Home)
        private set
    var zone by mutableStateOf(Zone.Row)
        private set
    private var rowIndex by mutableIntStateOf(0)
    private var dockIndex by mutableIntStateOf(0)
    private var drawerIndex by mutableIntStateOf(0)

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

    /** The categories layer (B on Home), and which name in it has focus. */
    var categoriesOpen by mutableStateOf(false)
        private set
    private var categoryIndex by mutableIntStateOf(0)
    private var currentCategory by mutableStateOf(
        prefs.lastCategory.takeIf { prefs.home.start == StartOn.Left } ?: Category.Favorites,
    )

    private var favorites by mutableStateOf(prefs.favorites)
    private var hidden by mutableStateOf(prefs.hidden)
    private var dockKeys by mutableStateOf(prefs.dock)
    private var categoryMoves by mutableStateOf(prefs.categories)
    var showHidden by mutableStateOf(false)
        private set

    /** Settings → Look, applied live as it changes. */
    var look by mutableStateOf(prefs.look)
        private set
    /** The settings panel, over everything; it takes input first. */
    var settingsOpen by mutableStateOf(false)
        private set
    private var settingsIndex by mutableIntStateOf(0)
    val settingsFocus get() = settingsIndex.coerceIn(0, settings.lastIndex)

    /** The panel's rows; the Art group only when there is a SteamGridDB key. */
    val settings = Setting.entries.filter { art != null || it.group != SettingGroup.Art }

    /** Settings → Home, applied live as it changes. */
    var home by mutableStateOf(prefs.home)
        private set
    var fetchArt by mutableStateOf(prefs.fetchArt)
        private set
    /** Bumped when the art is cleared, so it is looked up again. */
    var artCleared by mutableIntStateOf(0)
        private set
    // Read each time the settings open (and on coming back to them), as their values show.
    var artBytes by mutableStateOf<Long?>(null)
        private set
    var isHomeApp by mutableStateOf(false)
        private set
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

    private fun launchFrom(origin: Origin, entry: AppEntry) = launch(entry, placed["$origin/${entry.key}"])

    private val byKey by derivedStateOf { catalog.apps.associateBy { it.key } }

    val drawer: List<AppEntry> by derivedStateOf {
        if (showHidden) catalog.apps else catalog.apps.filter { it.key !in hidden }
    }
    val hiddenCount by derivedStateOf { catalog.apps.count { it.key in hidden } }

    /** Apps whose art is looked up without asking: the owner's favorites and the Games category. */
    val artWanted: List<AppEntry> by derivedStateOf {
        favorites.mapNotNull { byKey[it] } + appsIn(Category.Games)
    }

    /** The owner's dock once they have changed it; until then the default utilities. */
    val dock: List<AppEntry> by derivedStateOf {
        dockKeys?.mapNotNull { byKey[it] } ?: catalog.dockApps(catalog.apps)
    }

    /** The owner's move wins over the app's own category. */
    fun categoryOf(app: AppEntry) = categoryMoves[app.key] ?: app.seed

    /** Favorites in the owner's order; any other category is its visible apps, alphabetical. */
    private fun appsIn(category: Category): List<AppEntry> =
        if (category == Category.Favorites) {
            favorites.mapNotNull { byKey[it] }
        } else {
            catalog.apps.filter { it.key !in hidden && categoryOf(it) == category }
        }

    /** The categories with something in them, in the owner's order. */
    val categories: List<Category> by derivedStateOf { Category.entries.filter { appsIn(it).isNotEmpty() } }

    /** Home's category; if it has emptied, the first one that hasn't. */
    val category get() = currentCategory.takeIf { it in categories } ?: categories.firstOrNull() ?: Category.Favorites

    /** While the layer is open, the row previews the focused category instead. */
    private val shownCategory get() = if (categoriesOpen) categories.getOrNull(categoryFocus) ?: category else category

    val row: List<AppEntry> by derivedStateOf { appsIn(shownCategory) }

    // Lists change under us (an uninstall, a new favorite), so indices are clamped on read.
    // A previewed category shows from its start.
    val rowFocus get() = if (shownCategory != category) 0 else rowIndex.coerceIn(0, maxOf(0, row.lastIndex))
    /** 0 is the All apps button; dock apps follow from 1, then the Launcher Settings button. */
    val dockFocus get() = dockIndex.coerceIn(0, dockSettings)
    val dockSettings get() = dock.size + 1
    val drawerFocus get() = drawerIndex.coerceIn(0, maxOf(0, drawer.lastIndex))
    val sheetFocus get() = sheetIndex.coerceIn(0, maxOf(0, sheetRows - 1))
    val categoryFocus get() = categoryIndex.coerceIn(0, maxOf(0, categories.lastIndex))
    private val sheetRows get() = if (choosingCategory) Category.movable.size else sheet?.options?.size ?: 0

    fun isFavorite(entry: AppEntry) = entry.key in favorites
    fun isHidden(entry: AppEntry) = entry.key in hidden

    /** Whether + does anything where focus is now (All apps and Launcher Settings have none). */
    val focusHasOptions get() = screen == Screen.Drawer || zone == Zone.Row || dockFocus in 1..dock.size

    fun onAction(action: Action) = when {
        settingsOpen -> onSettings(action)
        quickOpen -> onQuick(action)
        sheetOpen -> onSheet(action)
        moving != null -> onMove(action)
        categoriesOpen -> onCategories(action)
        screen == Screen.Home -> onHome(action)
        else -> onDrawer(action)
    }

    private fun onHome(action: Action) {
        when (action) {
            Action.Left -> if (zone == Zone.Row) rowIndex = rowStep(-1) else dockIndex = dockFocus - 1
            Action.Right -> if (zone == Zone.Row) rowIndex = rowStep(1) else dockIndex = dockFocus + 1
            Action.Down -> zone = Zone.Dock
            Action.Up -> zone = Zone.Row
            Action.PagePrev -> if (zone == Zone.Row) rowIndex = rowFocus - PAGE
            Action.PageNext -> if (zone == Zone.Row) rowIndex = rowFocus + PAGE
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
        when (action) {
            Action.Up -> categoryIndex = categoryFocus - 1
            Action.Down -> categoryIndex = categoryFocus + 1
            Action.First -> categoryIndex = 0
            Action.Last -> categoryIndex = categories.lastIndex
            Action.Open -> enterCategory(categoryFocus)
            Action.Back -> closeCategories()
            else -> Unit
        }
        categoryIndex = categoryFocus
    }

    private fun onDrawer(action: Action) {
        val cols = Dimens.DRAWER_COLUMNS
        val i = drawerFocus
        when (action) {
            Action.Left -> drawerIndex = i - 1
            Action.Right -> drawerIndex = i + 1
            Action.Up -> if (i >= cols) drawerIndex = i - cols
            Action.Down -> if (i / cols < drawer.lastIndex / cols) drawerIndex = minOf(i + cols, drawer.lastIndex)
            Action.PagePrev -> drawerIndex = previousLetter(i)
            Action.PageNext -> drawerIndex = nextLetter(i)
            Action.First -> drawerIndex = 0
            Action.Last -> drawerIndex = drawer.lastIndex
            Action.Open -> drawer.getOrNull(i)?.let(::launchAndClose)
            Action.X -> drawer.getOrNull(i)?.let(::toggleFavorite)
            Action.Options -> optionsForFocused()
            Action.Back, Action.Y -> closeDrawer()
            Action.Select -> openQuick()
        }
        drawerIndex = drawerFocus
    }

    private fun onSheet(action: Action) {
        picker?.let {
            if (!it.onAction(action)) picker = null
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

    private fun onSettings(action: Action) {
        val setting = settings[settingsFocus]
        if (action != Action.Open) armed = null
        when (action) {
            Action.Up -> settingsIndex = settingsFocus - 1
            Action.Down -> settingsIndex = settingsFocus + 1
            Action.First -> settingsIndex = 0
            Action.Last -> settingsIndex = settings.lastIndex
            Action.Left -> if (!setting.acts) change(setting, -1)
            Action.Right -> if (!setting.acts) change(setting, 1)
            Action.Open -> if (setting.acts) act(setting) else change(setting, 1)
            Action.Back, Action.Options -> closeSettings()
            else -> Unit
        }
        settingsIndex = settingsFocus
    }

    fun tapSetting(index: Int) {
        if (index != settingsFocus) armed = null
        settingsIndex = index
        val setting = settings[settingsFocus]
        if (setting.acts) act(setting) else change(setting, 1)
    }

    private fun change(setting: Setting, by: Int) {
        when (setting.group) {
            SettingGroup.Look -> {
                look = look.step(setting, by, backdrops)
                prefs.look = look
            }
            SettingGroup.Home -> {
                home = home.step(setting, by)
                prefs.home = home
            }
            else -> if (setting == Setting.FetchArt) {
                fetchArt = !fetchArt
                prefs.fetchArt = fetchArt
            }
        }
    }

    private fun act(setting: Setting) {
        if (setting.confirms && armed != setting) {
            armed = setting
            return
        }
        armed = null
        when (setting) {
            Setting.ClearArt -> scope.launch {
                art?.clear()
                artBytes = art?.bytes()
                artCleared++
            }
            Setting.HiddenApps -> if (hiddenCount > 0) {
                closeSettings()
                showHidden = true
                drawerIndex = 0
                screen = Screen.Drawer
            }
            Setting.ResetDock -> {
                dockKeys = null
                prefs.dock = null
                dockIndex = 0
            }
            Setting.HomeApp -> openSystem(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            Setting.AndroidSettings -> openSystem(Intent(android.provider.Settings.ACTION_SETTINGS))
            else -> Unit
        }
    }

    /** From the + sheet or Android's launcher-settings gear (MainActivity). */
    fun openSettings() {
        wallpapers = findWallpapers()
        closeSheet()
        settingsIndex = 0
        armed = null
        settingsOpen = true
        refreshSettings()
    }

    /** The values that change outside the panel; again on coming back from Android's settings. */
    fun refreshSettings() {
        if (quickOpen) controls.read()
        if (!settingsOpen) return
        isHomeApp = holdsHome()
        scope.launch { artBytes = art?.bytes() }
    }

    fun closeSettings() {
        settingsOpen = false
        armed = null
    }

    private fun openQuick() {
        controls.read()
        quickRowIndex = 0
        quickColumnIndex = 0
        quickOpen = true
    }

    fun closeQuick() {
        quickOpen = false
    }

    private fun onQuick(action: Action) {
        val row = quickFocus
        when (action) {
            Action.Up -> quickRowIndex = (row.ordinal - 1).coerceAtLeast(0)
            Action.Down -> quickRowIndex = (row.ordinal + 1).coerceAtMost(QuickRow.entries.lastIndex)
            Action.Left -> quickStep(row, -1)
            Action.Right -> quickStep(row, 1)
            Action.Open -> quickOpenFocused(row, quickColumn)
            Action.Back, Action.Select -> closeQuick()
            else -> Unit
        }
        quickColumnIndex = quickColumn
    }

    private fun quickStep(row: QuickRow, by: Int) {
        when (row) {
            QuickRow.Brightness -> controls.stepBrightness(by)
            QuickRow.Volume -> controls.stepVolume(by)
            else -> quickColumnIndex = quickColumn + by
        }
    }

    /** A on a control: a toggle flips, a link opens; one missing its grant opens the grant page. */
    private fun quickOpenFocused(row: QuickRow, column: Int) {
        when (row) {
            QuickRow.Brightness -> if (!controls.canWrite) openSystem(writeSettingsGrant())
            QuickRow.Volume -> Unit
            QuickRow.Toggles -> when (QuickToggle.entries[column]) {
                QuickToggle.AutoBrightness -> if (controls.canWrite) controls.toggleAutoBrightness() else openSystem(writeSettingsGrant())
                QuickToggle.Quiet -> if (controls.canQuiet) controls.toggleQuiet() else openSystem(quietGrant())
                QuickToggle.Media -> {
                    controls.playPause()
                    scope.launch {
                        delay(MEDIA_SETTLE)
                        controls.read()
                    }
                }
            }
            QuickRow.Links -> openSystem(QuickLink.entries[column].intent())
        }
    }

    fun tapQuick(row: QuickRow, column: Int) {
        quickRowIndex = row.ordinal
        quickColumnIndex = column
        quickOpenFocused(row, column)
    }

    /** A tap on a slider's track sets it there. */
    fun tapQuickSlider(row: QuickRow, fraction: Float) {
        quickRowIndex = row.ordinal
        when (row) {
            QuickRow.Brightness -> controls.moveBrightnessTo(fraction)
            QuickRow.Volume -> controls.moveVolumeTo((fraction * controls.volumeMax).roundToInt())
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
        drawerIndex = index
        optionsForFocused()
    }

    fun tapSheet(index: Int) = if (choosingCategory) chooseCategory(index) else choose(index)

    fun tapCategory(index: Int) = enterCategory(index)

    fun openDrawer() {
        if (!categoriesOpen) screen = Screen.Drawer
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
        categoriesOpen = false
    }

    /** Into a category from the layer: its row from the start, unless it is the one already shown. */
    private fun enterCategory(index: Int) {
        val chosen = categories.getOrNull(index) ?: return
        if (chosen != category) {
            currentCategory = chosen
            prefs.lastCategory = chosen
            rowIndex = 0
        }
        zone = Zone.Row
        categoriesOpen = false
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
        categoriesOpen -> closeCategories()
        else -> closeDrawer()
    }

    val backHandled get() = settingsOpen || quickOpen || sheetOpen || moving != null || categoriesOpen || screen == Screen.Drawer

    /**
     * The Home key: closes everything. While already home (not on the way back from an app), it
     * also goes back to the first category's start, as the Switch does, unless Home key goes to
     * is Where you left.
     */
    fun onHomePressed(alreadyHome: Boolean) {
        closeSettings()
        closeQuick()
        closeSheet()
        moving = null
        categoriesOpen = false
        screen = Screen.Home
        if (!alreadyHome || home.start == StartOn.Left) return
        currentCategory = categories.firstOrNull() ?: Category.Favorites
        prefs.lastCategory = currentCategory
        zone = Zone.Row
        rowIndex = 0
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
            Origin.Row -> if (favorite && category == Category.Favorites) add(Option.Move)
            Origin.Dock -> add(Option.Move)
            Origin.Drawer -> Unit
        }
        if (origin != Origin.Dock) add(if (favorite) Option.Unfavorite else Option.Favorite)
        if (docked) add(Option.RemoveFromDock) else if (dock.size < DOCK_MAX) add(Option.AddToDock)
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
        if (option == Option.Art) {
            art?.let { picker = ArtPicker(app, it, scope, pickImage) }
            return
        }
        if (option == Option.ChangeCategory) {
            choosingCategory = true
            sheetIndex = Category.movable.indexOf(categoryOf(app)).coerceAtLeast(0)
            return
        }
        closeSheet()
        when (option) {
            Option.Move -> moving = sheet.origin
            Option.Favorite, Option.Unfavorite -> toggleFavorite(app)
            Option.AddToDock -> saveDock(dock.map { it.key } + app.key)
            Option.RemoveFromDock -> saveDock(dock.map { it.key } - app.key)
            Option.Hide -> hide(app)
            Option.Unhide -> saveHidden(hidden - app.key)
            Option.Art, Option.ChangeCategory -> Unit
            Option.Info -> catalog.showInfo(app)
            Option.Uninstall -> catalog.uninstall(app)
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
     * Moves the sheet's app to a category and closes the sheet. Only moves are stored, so moving
     * an app back to its own category drops the entry. If it leaves the row on show, the row's
     * focus is clamped onto its neighbor.
     */
    private fun chooseCategory(index: Int) {
        val app = sheet?.app ?: return
        val chosen = Category.movable.getOrNull(index) ?: return
        val moves = if (chosen == app.seed) categoryMoves - app.key else categoryMoves + (app.key to chosen)
        categoryMoves = moves
        prefs.categories = moves
        closeSheet()
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
        const val PAGE = 5
        const val DOCK_MAX = 6
        // How long a player takes to start or stop after the media key, before reading it again.
        const val MEDIA_SETTLE = 400L
        val WALLPAPER_TYPES = setOf("jpg", "jpeg", "png", "webp")
    }
}
