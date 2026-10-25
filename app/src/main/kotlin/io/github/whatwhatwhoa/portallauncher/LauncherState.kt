package io.github.whatwhatwhoa.portallauncher

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { Home, Drawer }

/** Where focus is on the home screen. */
enum class Zone { Row, Dock }

/**
 * All focus and navigation. Focus is our own index state, not Compose focus: a lazy row can't
 * focus tiles that aren't composed, and a console row pivots instead of moving to the nearest.
 */
class LauncherState(
    private val catalog: AppCatalog,
    private val prefs: Prefs,
    private val launch: (AppEntry) -> Unit,
) {
    var screen by mutableStateOf(Screen.Home)
        private set
    var zone by mutableStateOf(Zone.Row)
        private set
    private var rowIndex by mutableIntStateOf(0)
    private var dockIndex by mutableIntStateOf(0)
    private var drawerIndex by mutableIntStateOf(0)

    private var favourites by mutableStateOf(prefs.favourites)
    var accent by mutableStateOf(Accent.entries.getOrElse(prefs.accent) { Accent.Cyan })
        private set
    var font by mutableStateOf(FontChoice.entries.getOrElse(prefs.font) { FontChoice.Inter })
        private set

    val drawer: List<AppEntry> get() = catalog.apps
    val dock: List<AppEntry> by derivedStateOf { catalog.dockApps(catalog.apps) }

    /** Favourites in the owner's order; until there is one, every app that isn't in the dock. */
    val row: List<AppEntry> by derivedStateOf {
        val byKey = catalog.apps.associateBy { it.key }
        val favs = favourites.mapNotNull { byKey[it] }
        favs.ifEmpty {
            val inDock = dock.mapTo(HashSet()) { it.key }
            catalog.apps.filter { it.key !in inDock }
        }
    }

    // Lists change under us (an uninstall, a new favourite), so indices are clamped on read.
    val rowFocus get() = rowIndex.coerceIn(0, maxOf(0, row.lastIndex))
    /** 0 is the All apps button; dock apps follow from 1. */
    val dockFocus get() = dockIndex.coerceIn(0, dock.size)
    val drawerFocus get() = drawerIndex.coerceIn(0, maxOf(0, drawer.lastIndex))

    fun isFavourite(entry: AppEntry) = entry.key in favourites

    fun onAction(action: Action) = when (screen) {
        Screen.Home -> onHome(action)
        Screen.Drawer -> onDrawer(action)
    }

    private fun onHome(action: Action) {
        when (action) {
            Action.Left -> if (zone == Zone.Row) rowIndex = rowFocus - 1 else dockIndex = dockFocus - 1
            Action.Right -> if (zone == Zone.Row) rowIndex = rowFocus + 1 else dockIndex = dockFocus + 1
            Action.Down -> zone = Zone.Dock
            Action.Up -> zone = Zone.Row
            Action.PagePrev -> if (zone == Zone.Row) rowIndex = rowFocus - PAGE
            Action.PageNext -> if (zone == Zone.Row) rowIndex = rowFocus + PAGE
            Action.First -> if (zone == Zone.Row) rowIndex = 0
            Action.Last -> if (zone == Zone.Row) rowIndex = row.lastIndex
            Action.Open -> openFocused()
            Action.Y -> openDrawer()
            Action.X -> cycleFont()
            Action.Select -> cycleAccent()
            Action.Back, Action.Options -> Unit
        }
        rowIndex = rowFocus
        dockIndex = dockFocus
    }

    private fun onDrawer(action: Action) {
        val cols = Dimens.DRAWER_COLUMNS
        val i = drawerFocus
        when (action) {
            Action.Left -> drawerIndex = i - 1
            Action.Right -> drawerIndex = i + 1
            Action.Up -> if (i >= cols) drawerIndex = i - cols
            Action.Down -> if (i / cols < drawer.lastIndex / cols) drawerIndex = minOf(i + cols, drawer.lastIndex)
            Action.PagePrev -> drawerIndex = i - cols * DRAWER_PAGE_ROWS
            Action.PageNext -> drawerIndex = i + cols * DRAWER_PAGE_ROWS
            Action.First -> drawerIndex = 0
            Action.Last -> drawerIndex = drawer.lastIndex
            Action.Open -> drawer.getOrNull(i)?.let(::launchAndClose)
            Action.X -> drawer.getOrNull(i)?.let(::toggleFavourite)
            Action.Back, Action.Y -> closeDrawer()
            Action.Options, Action.Select -> Unit
        }
        drawerIndex = drawerFocus
    }

    private fun openFocused() {
        if (zone == Zone.Row) {
            row.getOrNull(rowFocus)?.let(launch)
        } else if (dockFocus == 0) {
            openDrawer()
        } else {
            dock.getOrNull(dockFocus - 1)?.let(launch)
        }
    }

    fun tapRow(index: Int) {
        zone = Zone.Row
        rowIndex = index
        openFocused()
    }

    fun tapDock(index: Int) {
        zone = Zone.Dock
        dockIndex = index
        openFocused()
    }

    fun tapDrawer(index: Int) {
        drawerIndex = index
        drawer.getOrNull(index)?.let(::launchAndClose)
    }

    fun openDrawer() {
        screen = Screen.Drawer
    }

    fun closeDrawer() {
        screen = Screen.Home
    }

    /** The Home key while already home: back to the start of the row, as the Switch does. */
    fun onHomePressed() {
        screen = Screen.Home
        zone = Zone.Row
        rowIndex = 0
    }

    private fun launchAndClose(entry: AppEntry) {
        launch(entry)
        closeDrawer()
    }

    private fun toggleFavourite(entry: AppEntry) {
        favourites = if (entry.key in favourites) favourites - entry.key else favourites + entry.key
        prefs.favourites = favourites
    }

    private fun cycleAccent() {
        accent = Accent.entries[(accent.ordinal + 1) % Accent.entries.size]
        prefs.accent = accent.ordinal
    }

    private fun cycleFont() {
        font = FontChoice.entries[(font.ordinal + 1) % FontChoice.entries.size]
        prefs.font = font.ordinal
    }

    private companion object {
        const val PAGE = 5
        const val DRAWER_PAGE_ROWS = 3
    }
}
