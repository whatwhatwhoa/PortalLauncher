package io.github.whatwhatwhoa.portallauncher

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    /** The app's art; its SteamGridDB lookups wait for a key ([Art.hasKey]), gallery images don't. */
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
    /** Whether the Home-hold service (HomeHoldService) is on, so holding Home opens standby. */
    private val holdsHomeKey: () -> Boolean = { false },
    /** Brightness, volume and the rest, for the quick panel. */
    val controls: DeviceControls,
    /** The clipboard's text, for pasting a SteamGridDB key without typing it on the controller. */
    private val readClipboard: () -> String? = { null },
    /** The GitHub releases, for the Updates row; null leaves the row saying so. */
    private val updates: Updates? = null,
    /** The UI sounds; null plays none. */
    private val sounds: Sounds? = null,
) {
    /** The quick panel (− on Home and All apps), over everything but the settings. */
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
            quickFocus == QuickRow.Media && quickColumn == SEEK_COLUMN && !controls.canSeeMedia ->
                "The seek bar needs Android's “Notification access” for the launcher, once"
            quickFocus == QuickRow.Media && quickColumn == SEEK_COLUMN -> "Seek"
            quickFocus == QuickRow.Media -> QuickMedia.entries[quickColumn].label
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

    /**
     * The categories layer (B on Home), and which line in it has focus: a category, or New
     * category after the last one ([categoryList]'s size).
     */
    var categoriesOpen by mutableStateOf(false)
        private set
    private var categoryIndex by mutableIntStateOf(0)
    /** Home's category, by id, so a rename keeps it. */
    private var currentCategory by mutableStateOf(
        prefs.lastCategory.takeIf { prefs.home.start == StartOn.Left } ?: Category.FAVORITES,
    )
    /** Every category, the empty ones too, in the owner's order: the layer's names. */
    var categoryList by mutableStateOf(prefs.categoryList)
        private set

    /**
     * The layer's edit mode (+ in it): every category, the empty ones too, and New category after
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
    val settingsFocus get() = settingsIndex.coerceIn(0, settings.lastIndex)

    /** The panel's rows; the Art group only when there is art at all. */
    val settings = Setting.entries.filter { art != null || it.group != SettingGroup.Art }

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
    var homeHold by mutableStateOf(false)
        private set
    /** What the Updates row says: the check's answer, then the download and install. */
    var updateNote by mutableStateOf<String?>(null)
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

    private fun launchFrom(origin: Origin, entry: AppEntry) {
        sounds?.play(Sound.Launch)
        launch(entry, placed["$origin/${entry.key}"])
    }

    private val byKey by derivedStateOf { catalog.apps.associateBy { it.key } }

    val drawer: List<AppEntry> by derivedStateOf {
        if (showHidden) catalog.apps else catalog.apps.filter { it.key !in hidden }
    }
    val hiddenCount by derivedStateOf { catalog.apps.count { it.key in hidden } }

    /**
     * Apps whose art is looked up without asking: the owner's favorites and the Games category,
     * whatever it is called now; once deleted, favorites only.
     */
    val artWanted: List<AppEntry> by derivedStateOf {
        favorites.mapNotNull { byKey[it] } + byId(Category.GAMES)?.let(::appsIn).orEmpty()
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

    /** Favorites in the owner's order; any other category is its visible apps, alphabetical. */
    private fun appsIn(category: Category): List<AppEntry> =
        if (category.isFavorites) {
            favorites.mapNotNull { byKey[it] }
        } else {
            catalog.apps.filter { it.key !in hidden && category.id in (categoryMoves[it.key] ?: setOfNotNull(startOf(it))) }
        }

    /** The categories with something in them, in the owner's order: the ones Home shows. */
    val categories: List<Category> by derivedStateOf { categoryList.filter { appsIn(it).isNotEmpty() } }

    fun hasApps(category: Category) = category in categories

    /** Where the sheet's Category… page can move an app: every category but Favorites, then All apps only (null). */
    val places: List<Category?> get() = categoryList.filterNot { it.isFavorites } + null

    /** Home's category; if it has emptied, the first one that hasn't. */
    val category get() = categories.firstOrNull { it.id == currentCategory } ?: categories.firstOrNull() ?: categoryList.first()

    /** While the layer is open, the row previews the focused category instead. */
    private val shownCategory get() = if (categoriesOpen) layerCategories.getOrNull(categoryFocus) ?: category else category

    /** The layer's names: the ones Home shows, or in edit mode every one. */
    val layerCategories get() = if (editingCategories) categoryList else categories

    val row: List<AppEntry> by derivedStateOf { appsIn(shownCategory) }

    // Lists change under us (an uninstall, a new favorite), so indices are clamped on read.
    // A previewed category shows from its start.
    val rowFocus get() = if (shownCategory != category) 0 else rowIndex.coerceIn(0, maxOf(0, row.lastIndex))
    /** 0 is the All apps button; dock apps follow from 1, then the Launcher Settings button. */
    val dockFocus get() = dockIndex.coerceIn(0, dockSettings)
    val dockSettings get() = dock.size + 1
    val drawerFocus get() = drawerIndex.coerceIn(0, maxOf(0, drawer.lastIndex))
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

    fun onAction(action: Action) = sounding(action) {
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
        if (accentOpen) return onAccent(action)
        val setting = settings[settingsFocus]
        if (action != Action.Open) armed = null
        when (action) {
            Action.Up -> settingsIndex = settingsFocus - 1
            Action.Down -> settingsIndex = settingsFocus + 1
            Action.First -> settingsIndex = 0
            Action.Last -> settingsIndex = settings.lastIndex
            Action.Left -> if (!setting.acts) change(setting, -1)
            Action.Right -> if (!setting.acts) change(setting, 1)
            Action.Open -> if (setting == Setting.Accent) openAccent() else if (setting.acts) act(setting) else change(setting, 1)
            Action.Back, Action.Options -> closeSettings()
            else -> Unit
        }
        settingsIndex = settingsFocus
    }

    fun tapSetting(index: Int) {
        if (index != settingsFocus) armed = null
        settingsIndex = index
        val setting = settings[settingsFocus]
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
        // Landing on a swatch row wears the swatch under focus, as moving along it does.
        if (accentRow != row && accentRow < SWATCH_ROWS) wearSwatch()
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
        look = look.copy(accent = accent)
        prefs.look = look
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
            SettingGroup.Sound -> {
                sound = sound.step(setting, by)
                prefs.sound = sound
                sounds?.settings = sound
                // A new family or volume is heard at once, in itself.
                if (setting != Setting.SoundsOn) sounds?.play(Sound.Select)
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
            Setting.ArtKey -> takeKey()
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
            Setting.HomeHold -> openSystem(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Setting.AndroidSettings -> openSystem(Intent(android.provider.Settings.ACTION_SETTINGS))
            Setting.Updates -> update()
            else -> Unit
        }
    }

    /** From the + sheet or Android's launcher-settings gear (MainActivity). */
    fun openSettings() {
        wallpapers = findWallpapers()
        closeSheet()
        settingsIndex = 0
        armed = null
        accentOpen = false
        settingsOpen = true
        refreshSettings()
    }

    /** The values that change outside the panel; again on coming back from Android's settings. */
    fun refreshSettings() {
        if (quickOpen) controls.read()
        if (!settingsOpen) return
        isHomeApp = holdsHome()
        homeHold = holdsHomeKey()
        scope.launch { artBytes = art?.bytes() }
        // Asked again after a while, or at once after a failed check; never while one runs.
        val stale = System.currentTimeMillis() - updateCheckedAt > UPDATE_CHECK_MS
        if (stale && updating?.isActive != true) checkUpdate()
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
            Action.PagePrev -> skip(QuickMedia.Previous)
            Action.PageNext -> skip(QuickMedia.Next)
            Action.Back, Action.Select -> closeQuick()
            else -> Unit
        }
        quickColumnIndex = quickColumn
    }

    private fun quickStep(row: QuickRow, by: Int) {
        when (row) {
            QuickRow.Brightness -> controls.stepBrightness(by)
            QuickRow.Volume -> controls.stepVolume(by)
            // On the seek bar, left and right seek; Up or Down leave it, as on a TV player.
            QuickRow.Media -> if (quickColumn == SEEK_COLUMN) controls.seekBy(by * SEEK_STEP) else quickColumnIndex = quickColumn + by
            else -> quickColumnIndex = quickColumn + by
        }
    }

    /** L1 or R1: a skip from any row, focus left where it is; it chimes, since nothing on screen moves. */
    private fun skip(media: QuickMedia) {
        press(media)
        sounds?.play(Sound.Select)
    }

    private fun press(media: QuickMedia) {
        controls.press(media)
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
                !controls.canSeeMedia -> openSystem(mediaGrant())
            }
            QuickRow.Brightness -> if (!controls.canWrite) openSystem(writeSettingsGrant())
            QuickRow.Volume -> Unit
            QuickRow.Toggles -> when (QuickToggle.entries[column]) {
                QuickToggle.AutoBrightness -> if (controls.canWrite) controls.toggleAutoBrightness() else openSystem(writeSettingsGrant())
                QuickToggle.Quiet -> if (controls.canQuiet) controls.toggleQuiet() else openSystem(quietGrant())
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
        if (row == QuickRow.Media) quickColumnIndex = SEEK_COLUMN
        when (row) {
            QuickRow.Brightness -> controls.moveBrightnessTo(fraction)
            QuickRow.Volume -> controls.moveVolumeTo((fraction * controls.volumeMax).roundToInt())
            QuickRow.Media -> if (controls.canSeeMedia) controls.seekTo(fraction) else openSystem(mediaGrant())
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
        if (categoryEdit == CategoryEdit.Renaming) finishRename()
        categoryEdit = null
        editingCategories = false
        categoriesOpen = false
    }

    /** + in the layer: edit mode, on the same name. */
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
        standby -> Unit
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
        else -> closeDrawer()
    }

    val backHandled get() = standby || settingsOpen || quickOpen || sheetOpen || moving != null || categoriesOpen || screen == Screen.Drawer

    /**
     * The Home key: closes everything. While already home (not on the way back from an app), it
     * also goes back to the first category's start, as the Switch does, unless Home key goes to
     * is Where you left. Coming back from an app makes no sound; pressed at home it sounds as B.
     */
    fun onHomePressed(alreadyHome: Boolean) = sounding(Action.Back.takeIf { alreadyHome }) { goHome(alreadyHome) }

    private fun goHome(alreadyHome: Boolean) {
        if (standby) return standbyPress(KeyEvent.KEYCODE_HOME)
        if (holdsHomeKey()) flashStandbyHint()
        closeSettings()
        closeQuick()
        closeSheet()
        moving = null
        categoriesOpen = false
        screen = Screen.Home
        if (!alreadyHome || home.start == StartOn.Left) return
        currentCategory = (categories.firstOrNull() ?: categoryList.first()).id
        prefs.lastCategory = currentCategory
        zone = Zone.Row
        rowIndex = 0
    }

    /**
     * Standby (F19): a rest that doesn't lock, over whatever was showing, which is still there
     * after. Opened by holding Home (HomeHoldService).
     */
    var standby by mutableStateOf(false)
        private set
    /** Presses so far toward leaving standby, 0 until [WAKE_PRESSES]; the dots show them. */
    var wakePresses by mutableIntStateOf(0)
        private set
    val wakePressesNeeded = WAKE_PRESSES
    private var wakeWindow: Job? = null
    /** "Hold Home for standby", shown briefly on each Home press while holding Home opens it. */
    var standbyHint by mutableStateOf(false)
        private set
    private var hintTimer: Job? = null

    fun openStandby() {
        standbyHint = false
        endWakeWindow()
        standby = true
    }

    /** Leaving without the presses: the launcher went out of view (the screen off, an app over it). */
    fun endStandby() {
        endWakeWindow()
        standby = false
    }

    /**
     * A button press in standby, [button] its key code (not a touch, a stick or a trigger:
     * MainActivity swallows those). [WAKE_PRESSES] of the same button within [WAKE_WINDOW] of the
     * first leave it, so a bump in a bag, which presses whatever it presses, doesn't; another
     * button starts the count again from one (owner).
     */
    fun standbyPress(button: Int) {
        if (button != wakeButton) endWakeWindow()
        wakeButton = button
        if (++wakePresses >= WAKE_PRESSES) {
            sounds?.play(Sound.Wake)
            return endStandby()
        }
        if (wakePresses == 1) wakeWindow = scope.launch {
            delay(WAKE_WINDOW)
            wakePresses = 0
            wakeButton = null
        }
    }
    private var wakeButton: Int? = null

    private fun endWakeWindow() {
        wakeWindow?.cancel()
        wakePresses = 0
        wakeButton = null
    }

    private fun flashStandbyHint() {
        hintTimer?.cancel()
        standbyHint = true
        hintTimer = scope.launch {
            delay(HINT_SHOWN)
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
            sheetIndex = places.indexOfFirst { isIn(app, it) }.coerceAtLeast(0)
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
        // One Left or Right on the seek bar, as a player's own skip-ahead.
        const val SEEK_STEP = 10_000L
        const val WAKE_PRESSES = 4
        const val WAKE_WINDOW = 2_000L
        // The owner's "0.5 or so seconds", plus the fades.
        const val HINT_SHOWN = 600L
        val WALLPAPER_TYPES = setOf("jpg", "jpeg", "png", "webp")
        // SteamGridDB's API keys are 32 hex digits.
        val KEY_SHAPE = Regex("[0-9a-fA-F]{32}")
        /** How long a check holds before the panel asks GitHub again. */
        const val UPDATE_CHECK_MS = 10 * 60_000L
    }
}
