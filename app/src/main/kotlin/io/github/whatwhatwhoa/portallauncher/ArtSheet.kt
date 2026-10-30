package io.github.whatwhatwhoa.portallauncher

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface ArtRow {
    class Result(val match: ArtMatch) : ArtRow
    data object Search : ArtRow
    /** Without a key: Launcher Settings on the SteamGridDB key row. */
    data object Key : ArtRow
    data object UseIcon : ArtRow
}

/**
 * The options sheet's Art… page: SteamGridDB's matches for the app's label, a search for other
 * words, and the app's own icon. Choosing a match fetches it and shows it in the preview at the
 * top, and the page stays open so another can be tried; B goes back to the options. Up from the
 * first row selects the preview itself: the cover, or right of it the wide strip, which is what
 * fills Home behind the app: its hero or its scene, never both (Art.wide). A there opens that
 * slot's images as a grid of thumbnails (the matched game's, a scene's from Steam), the focused
 * one standing in the preview so it is seen in place before A takes it; the strip's grid has two
 * tabs, Hero and Scene, that L1/R1 turn between, and taking an image from one makes that kind the
 * app's (A on the one already dotted only switches). B goes back to the preview. The cover and
 * scene grids start with a gallery tile, for any app, matched or not; [pickImage] opens the photo
 * picker and hands back the image, or null. All of an app's art is set here; Launcher Settings
 * keeps only what is global (the key, fetching, clearing).
 */
class ArtPicker(
    val app: AppEntry,
    private val art: Art,
    private val scope: CoroutineScope,
    private val pickImage: ((Uri?) -> Unit) -> Unit,
    /** Opens Launcher Settings on the SteamGridDB key, for the page's row without a key. */
    private val openKey: () -> Unit,
) {
    val pkg: String = app.component.packageName
    var query by mutableStateOf(app.label)
    // The words the results are for.
    private var searched = app.label
    var results by mutableStateOf<List<ArtMatch>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    /** The game being downloaded, shown on its row. */
    var fetching by mutableStateOf<Int?>(null)
        private set
    /** What went wrong on the rows (a search, a match's download), for their caption. */
    var failed by mutableStateOf<String?>(null)
        private set
    /** True while the search field has the keyboard; keys then go to the field, not to us. */
    var typing by mutableStateOf(false)
        private set
    private var index by mutableIntStateOf(0)
    // Apart, so opening a grid never cancels the label's search under the rows.
    private var searchJob: Job? = null
    private var imagesJob: Job? = null

    /** The slot whose images are showing instead of the rows, or null. */
    var browsing by mutableStateOf<ArtKind?>(null)
        private set
    var images by mutableStateOf<List<ArtImage>>(emptyList())
        private set
    var loadingImages by mutableStateOf(false)
        private set
    /** What went wrong in the grid (its images, a pick, the gallery), for its own caption. */
    var gridFailed by mutableStateOf<String?>(null)
        private set
    /** The image being downloaded or saved, shown in the caption. */
    var picking by mutableStateOf<String?>(null)
        private set
    private var imageIndex by mutableIntStateOf(0)
    /** The grid's focused tile; with a gallery tile that is tile 0 and the images follow it. */
    val imageFocus get() = imageIndex.coerceIn(0, (tiles - 1).coerceAtLeast(0))
    /** The cover and scene grids start with a tile that opens the gallery. */
    val galleryTile get() = browsing in GALLERY
    private val offset get() = if (galleryTile) 1 else 0
    private val tiles get() = images.size + offset
    fun imageAt(tile: Int): ArtImage? = images.getOrNull(tile - offset)
    /** The focused thumbnail, standing in the preview for the slot being browsed. */
    val candidate get() = browsing?.let { imageAt(imageFocus) }

    /** True while the preview has focus (Up from the first row); [previewWide] picks its strip. */
    var previewing by mutableStateOf(false)
        private set
    var previewWide by mutableStateOf(false)
        private set

    private val matched get() = (art.record(pkg) as? ArtRecord.Matched)?.match
    val hasMatch get() = matched != null
    /**
     * What the preview's strip shows and opens: the tab being browsed, else what fills Home behind
     * the app. Without a game only a scene from the gallery can go there.
     */
    val wideKind: ArtKind
        get() {
            browsing?.takeIf { it != ArtKind.Cover }?.let { return it }
            return if (matched == null) ArtKind.Scene else art.wide(pkg)
        }
    /** The kind Home shows behind the app, for the tab's dot. */
    val wideInUse get() = art.wide(pkg)
    /** Without a SteamGridDB key the page has the way to one and the icon; the preview still takes gallery images. */
    val hasKey get() = art.hasKey
    private val searchRows get() = if (hasKey) results.map { ArtRow.Result(it) } + ArtRow.Search else listOf(ArtRow.Key)
    val rows: List<ArtRow>
        get() = searchRows + ArtRow.UseIcon
    val focus get() = index.coerceIn(0, rows.lastIndex)

    init {
        if (hasKey) search()
    }

    /** False when B leaves the page. [held] is a held key's repeat. */
    fun onAction(action: Action, held: Boolean = false): Boolean {
        if (browsing != null) {
            onBrowse(action, held)
            return true
        }
        if (previewing) {
            when (action) {
                Action.Left -> previewWide = false
                Action.Right -> previewWide = true
                Action.Down -> previewing = false
                Action.Open -> browse(if (previewWide) wideKind else ArtKind.Cover)
                Action.Back -> return false
                else -> Unit
            }
            return true
        }
        when (action) {
            Action.Up -> if (focus == 0) previewing = true else index = focus - 1
            Action.Down -> index = focus + 1
            Action.First -> index = 0
            Action.Last -> index = rows.lastIndex
            Action.Open -> choose(focus)
            Action.Back -> return false
            else -> Unit
        }
        index = focus
        return true
    }

    fun tap(i: Int) {
        previewing = false
        index = i
        choose(i)
    }

    fun tapPreview(wide: Boolean) {
        previewing = true
        previewWide = wide
        browse(if (wide) wideKind else ArtKind.Cover)
    }

    fun tapTab(kind: ArtKind) {
        if (kind != browsing && matched != null) browse(kind)
    }

    fun tapImage(i: Int) {
        imageIndex = i
        takeTile(i)
    }

    /** Back to the preview, which keeps focus on the slot that was browsed. */
    fun stopBrowsing() {
        browsing = null
        images = emptyList()
        imagesJob?.cancel()
        loadingImages = false
    }

    private fun onBrowse(action: Action, held: Boolean) {
        val kind = browsing ?: return
        val columns = columns(kind)
        when (action) {
            Action.Left -> imageIndex = imageFocus - 1
            Action.Right -> imageIndex = imageFocus + 1
            // Up and down stay in their column: clamped, they slid sideways onto the gallery tile
            // or the last image. Up from the top row goes back to the preview, on a fresh press.
            Action.Up -> if (imageFocus >= columns) imageIndex = imageFocus - columns else if (!held) stopBrowsing()
            Action.Down -> if (imageFocus / columns < (tiles - 1) / columns) imageIndex = minOf(imageFocus + columns, tiles - 1)
            Action.First -> imageIndex = 0
            Action.Last -> imageIndex = tiles - 1
            Action.PagePrev -> if (kind == ArtKind.Scene && matched != null) browse(ArtKind.Hero)
            Action.PageNext -> if (kind == ArtKind.Hero) browse(ArtKind.Scene)
            Action.Open -> takeTile(imageFocus)
            Action.Back -> stopBrowsing()
            else -> Unit
        }
        imageIndex = imageFocus
    }

    private fun takeTile(tile: Int) {
        val kind = browsing ?: return
        if (galleryTile && tile == 0) {
            if (picking == null) fromGallery(kind)
        } else {
            imageAt(tile)?.let(::pick)
        }
    }

    /** Opens [kind]'s grid on the image in use, or on the gallery tile when none of them is. */
    private fun browse(kind: ArtKind) {
        browsing = kind
        images = emptyList()
        imageIndex = 0
        gridFailed = null
        imagesJob?.cancel()
        loadingImages = false
        // Without a game there are no images to look up: the grid is the gallery tile alone.
        if (matched == null) return
        imagesJob = scope.launch {
            loadingImages = true
            images = try {
                art.images(pkg, kind)
            } catch (e: CancellationException) {
                // The other tab, or the grid closed: whatever replaced this load writes its own.
                throw e
            } catch (e: Exception) {
                gridFailed = "Couldn't reach SteamGridDB"
                emptyList()
            }
            loadingImages = false
            val at = images.indexOfFirst { isPicked(kind, it) }
            imageIndex = if (at >= 0) at + offset else 0
        }
    }

    private fun pick(image: ArtImage) {
        val kind = browsing ?: return
        if (picking != null) return
        if (isPicked(kind, image)) {
            // Already downloaded: taking it again only makes its kind the one Home shows.
            if (kind != ArtKind.Cover && art.wide(pkg) != kind) art.useWide(pkg, kind)
            return
        }
        scope.launch {
            picking = image.url
            val ok = art.pick(pkg, kind, image)
            gridFailed = if (ok) null else "Couldn't fetch that image"
            if (ok && kind != ArtKind.Cover) art.useWide(pkg, kind)
            picking = null
        }
    }

    /** The image Home shows: picked, and for the strip, of the kind the app uses. */
    fun inUse(kind: ArtKind, image: ArtImage) = isPicked(kind, image) && (kind == ArtKind.Cover || art.wide(pkg) == kind)

    /**
     * The one in use: the owner's pick, else the image the automatic choice takes (the first at
     * the slot's preferred width, as Art.use does).
     */
    fun isPicked(kind: ArtKind, image: ArtImage): Boolean {
        art.picked(pkg, kind)?.let { return it == image.url }
        if (kind == ArtKind.Scene) return art.autoScene(pkg) == image.url
        val auto = images.firstOrNull { it.width == preferredWidth(kind) } ?: images.firstOrNull()
        return auto?.url == image.url
    }

    suspend fun thumbnail(url: String, maxPx: Int) = art.thumbnail(url, maxPx)

    /**
     * B while typing ends it and keeps the results: searching there would replace them (or empty
     * them offline). The field goes back to the words they are for, so it never shows words that
     * weren't searched; only the keyboard's search key searches.
     */
    fun stopTyping() {
        typing = false
        query = searched
    }

    fun search() {
        typing = false
        searched = query
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            failed = null
            results = try {
                art.search(query)
            } catch (e: CancellationException) {
                // A newer search took over; the results stay until it has its own.
                throw e
            } catch (e: Exception) {
                failed = "Couldn't reach SteamGridDB"
                emptyList()
            }
            searching = false
            // Start on the match in use, if it is among the results.
            index = results.indexOfFirst { it.gameId == matched?.gameId }.coerceAtLeast(0)
        }
    }

    private fun choose(i: Int) {
        when (val row = rows.getOrNull(i)) {
            is ArtRow.Result -> if (fetching == null && !isCurrent(row.match)) fetch(row.match)
            ArtRow.Search -> typing = true
            ArtRow.Key -> openKey()
            ArtRow.UseIcon -> art.useIcon(pkg)
            null -> Unit
        }
    }

    private fun fromGallery(kind: ArtKind) = pickImage { uri ->
        if (uri != null) {
            scope.launch {
                picking = uri.toString()
                gridFailed = art.useImage(pkg, kind, uri)
                if (gridFailed == null && kind == ArtKind.Scene) art.useWide(pkg, kind)
                picking = null
            }
        }
    }

    private fun fetch(match: ArtMatch) = scope.launch {
        fetching = match.gameId
        failed = when (art.use(pkg, match)) {
            ArtFetch.Done -> null
            ArtFetch.NoImages -> "No art for ${match.name}"
            ArtFetch.Failed -> "Couldn't reach SteamGridDB"
        }
        fetching = null
    }

    fun isCurrent(match: ArtMatch) = matched?.gameId == match.gameId
    val usingIcon get() = art.record(pkg) == ArtRecord.Icon

    companion object {
        val GALLERY = listOf(ArtKind.Cover, ArtKind.Scene)

        /** Thumbnails across the wide panel: square covers four to a row, 16:9 scenes three, heroes two. */
        fun columns(kind: ArtKind) = when (kind) {
            ArtKind.Cover -> 4
            ArtKind.Scene -> 3
            ArtKind.Hero -> 2
        }

        fun preferredWidth(kind: ArtKind) = if (kind == ArtKind.Cover) 512 else 1920
    }
}

/** The page's body inside the options panel; the panel draws the header above it. */
@Composable
fun ColumnScope.ArtPage(picker: ArtPicker) {
    val p = LocalPalette.current
    val t = LocalType.current
    val corner = RoundedCornerShape(Space.m)
    val listState = rememberLazyListState()
    val focus = picker.focus
    val motion = LocalMotion.current
    LaunchedEffect(focus) {
        // Keeps the focused row in view with one row of context above it.
        val target = (focus - 1).coerceAtLeast(0)
        if (motion) listState.animateScrollToItem(target) else listState.scrollToItem(target)
    }
    picker.browsing?.let {
        ImageGrid(picker, it)
        return
    }
    BasicText(
        when {
            picker.picking != null -> "Saving…"
            picker.searching -> "Searching…"
            picker.failed != null -> picker.failed!!
            !picker.hasKey -> "Add a SteamGridDB key in Launcher Settings to search"
            picker.results.isEmpty() -> "No matches for “${picker.query}”"
            else -> "SteamGridDB"
        },
        style = t.caption.copy(color = p.muted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(Space.s))
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth().ringRoom(),
        state = listState,
        contentPadding = PaddingValues(RING_ROOM),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        itemsIndexed(picker.rows) { i, row ->
            val focused = i == focus && !picker.typing && !picker.previewing
            Box(
                Modifier.fillMaxWidth()
                    .height(Dimens.sheetRow)
                    .focusRing(focused, Space.m)
                    .background(if (focused) p.rule else Color.Transparent, corner)
                    .clickable(interactionSource = null, indication = null) { picker.tap(i) }
                    .padding(horizontal = Space.l),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (row) {
                    is ArtRow.Result -> Choice(
                        when (row.match.gameId) {
                            picker.fetching -> "Fetching…"
                            else -> row.match.label
                        },
                        picked = picker.isCurrent(row.match),
                    )
                    ArtRow.Search -> SearchField(picker)
                    ArtRow.Key -> Choice("SteamGridDB key…", picked = false)
                    ArtRow.UseIcon -> Choice("Use the app’s icon", picked = picker.usingIcon)
                }
            }
        }
    }
}

/**
 * One slot's images for the matched game, after the gallery tile for a cover or a scene; the one
 * in use has the accent dot. The strip's grid sits under its two tabs ([WideTabs]).
 */
@Composable
private fun ColumnScope.ImageGrid(picker: ArtPicker, kind: ArtKind) {
    val p = LocalPalette.current
    val t = LocalType.current
    val corner = RoundedCornerShape(Space.s)
    val gridState = rememberLazyGridState()
    val columns = ArtPicker.columns(kind)
    val focus = picker.imageFocus
    val motion = LocalMotion.current
    val follow = remember { GridFollow() }
    LaunchedEffect(focus, picker.images) {
        if (picker.images.isEmpty()) return@LaunchedEffect
        // One row of context above the focused image. New images (the grid opening, a tab
        // turned) open on the image in use without scrolling to it; held moves glide.
        follow.to(gridState, focus, columns, motion, contents = picker.images)
    }
    val name = when (kind) {
        ArtKind.Cover -> "Covers"
        ArtKind.Hero -> "Heroes"
        ArtKind.Scene -> "Scenes"
    }
    val gallery = picker.galleryTile
    if (kind != ArtKind.Cover && picker.hasMatch) {
        WideTabs(picker, kind)
        Spacer(Modifier.height(Space.m))
    }
    BasicText(
        when {
            picker.loadingImages -> "Loading ${name.lowercase()}…"
            picker.picking != null -> if (gallery && focus == 0) "Saving…" else "Fetching…"
            picker.gridFailed != null -> picker.gridFailed!!
            !picker.hasMatch -> "No game matched: choose one below, or from the gallery"
            picker.images.isEmpty() -> "No ${if (kind == ArtKind.Scene) "Steam screenshots" else name.lowercase()} for this game"
            gallery && focus == 0 -> "$name · from the gallery"
            else -> "$name · ${focus + 1 - (if (gallery) 1 else 0)} of ${picker.images.size}"
        },
        style = t.caption.copy(color = p.muted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(Space.s))
    val aspect = when (kind) {
        ArtKind.Cover -> 1f
        ArtKind.Hero -> HERO_ASPECT
        ArtKind.Scene -> SCENE_ASPECT
    }
    LazyVerticalGrid(
        GridCells.Fixed(columns),
        Modifier.weight(1f).fillMaxWidth().ringRoom(),
        state = gridState,
        contentPadding = PaddingValues(RING_ROOM),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (gallery) {
            item(key = "gallery") {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(1f / aspect)
                        .focusRing(focus == 0, Space.s)
                        .clip(corner)
                        .background(p.rule)
                        .clickable(interactionSource = null, indication = null) { picker.tapImage(0) }
                        .padding(Space.s),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Gallery…", style = t.caption.copy(color = p.text), maxLines = 1)
                }
            }
        }
        gridItemsIndexed(picker.images, key = { _, image -> image.url }) { at, image ->
            val i = at + if (gallery) 1 else 0
            BoxWithConstraints(
                Modifier.fillMaxWidth()
                    .aspectRatio(1f / aspect)
                    .focusRing(i == focus, Space.s)
                    .clip(corner)
                    .background(p.rule)
                    .clickable(interactionSource = null, indication = null) { picker.tapImage(i) },
            ) {
                val px = constraints.maxWidth
                val thumb by produceState<ImageBitmap?>(null, image.thumb, px) { value = picker.thumbnail(image.thumb, px) }
                thumb?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                if (picker.inUse(kind, image)) {
                    Box(Modifier.align(Alignment.TopEnd).padding(Space.xs).size(Space.m).background(p.accent, CircleShape))
                }
            }
        }
    }
}

/**
 * The strip's two kinds as tabs, Hero on L1 and Scene on R1 (the bumpers drawn, as the hints
 * are): the one browsed lit and underlined, a dot on the one Home shows. A tap turns to one too.
 */
@Composable
private fun WideTabs(picker: ArtPicker, kind: ArtKind) {
    val hints = LocalHome.current.hints
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
        if (hints) BumperGlyph(left = true)
        listOf(ArtKind.Hero to "Hero", ArtKind.Scene to "Scene").forEach { (tab, label) ->
            Tab(label, on = tab == kind, used = picker.wideInUse == tab) { picker.tapTab(tab) }
        }
        if (hints) BumperGlyph(left = false)
    }
}

@Composable
private fun Tab(label: String, on: Boolean, used: Boolean, onTap: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(IntrinsicSize.Max).clickable(interactionSource = null, indication = null, onClick = onTap),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            BasicText(
                label,
                style = LocalType.current.body.copy(
                    color = if (on) p.text else p.muted,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
            if (used) Box(Modifier.size(Space.s).background(p.accent, CircleShape))
        }
        Box(Modifier.fillMaxWidth().height(Dimens.ring).background(if (on) p.accent else Color.Transparent, CircleShape))
    }
}

// A lazy list clips to its bounds, so it reaches out past the panel's padding by the focus
// ring's width and pads its items back in: items line up with the options and rings show whole.
private val RING_ROOM = Dimens.ring + Dimens.ringGap

private fun Modifier.ringRoom() = layout { measurable, constraints ->
    val extra = (RING_ROOM * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** A row's label, with an accent dot when it is the one in use. */
@Composable
fun Choice(label: String, picked: Boolean) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        BasicText(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = LocalType.current.body.copy(color = p.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (picked) Box(Modifier.size(Space.s).background(p.accent, CircleShape))
    }
}

/** The search words; A on the row brings up the keyboard, its search key runs the search. */
@Composable
private fun SearchField(picker: ArtPicker) {
    val p = LocalPalette.current
    val t = LocalType.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(picker.typing) {
        if (picker.typing) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    // Leaving the page while typing (Home, a tap outside) takes the field away with typing still
    // on, so it hides the keyboard itself, as the category name's field does.
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        BasicText("Search:", style = t.body.copy(color = p.muted), maxLines = 1)
        BasicTextField(
            value = picker.query,
            onValueChange = { picker.query = it },
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            textStyle = t.body.copy(color = p.text),
            singleLine = true,
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { picker.search() }),
        )
    }
}

/**
 * The art in use, for checking a match and for changing it in place: the square cover beside what
 * fills Home behind it, the scene at 16:9 when there is one, else the hero's wide strip. Falls
 * back to the icon when there is no cover; an empty strip when there is neither. While a slot is
 * browsed its focused thumbnail stands in for it and its ring holds still, the grid's pulsing.
 */
@Composable
fun ArtPreview(picker: ArtPicker, art: Art) {
    val app = picker.app
    val pkg = picker.pkg
    val p = LocalPalette.current
    val corner = RoundedCornerShape(Space.s)
    val browsing = picker.browsing
    val steady = browsing != null
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        val size = Dimens.artPreview
        val coverMod = Modifier.size(size)
            .focusRing(picker.previewing && !picker.previewWide, Space.s, steady)
            .clip(corner)
            .clickable(interactionSource = null, indication = null) { picker.tapPreview(wide = false) }
        val cover = candidate(picker, ArtKind.Cover, size.px()) ?: rememberArt(art, pkg, ArtKind.Cover, size.px())
        if (cover != null) {
            Image(cover, null, coverMod, contentScale = ContentScale.Crop)
        } else {
            AppIcon(app, corner, coverMod)
        }
        // As tall as the cover, as wide as its kind is (a hero is wider than a scene), within the
        // room left; sized from the panel's final width, so its opening slide decodes nothing twice.
        val kind = picker.wideKind
        val aspect = if (kind == ArtKind.Scene) SCENE_ASPECT else HERO_ASPECT
        val room = Dimens.artSheet - Space.xxl * 2 - size - Space.m
        val width = minOf(room, size / aspect)
        val wide = candidate(picker, kind, width.px()) ?: rememberArt(art, pkg, kind, width.px())
        Box(
            Modifier.size(width, width * aspect)
                .focusRing(picker.previewing && picker.previewWide, Space.s, steady)
                .clip(corner)
                .background(p.rule)
                .clickable(interactionSource = null, indication = null) { picker.tapPreview(wide = true) },
        ) {
            if (wide != null) Image(wide, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** The focused thumbnail while [kind] is browsed, null otherwise (or while it loads). */
@Composable
private fun candidate(picker: ArtPicker, kind: ArtKind, px: Int): ImageBitmap? {
    val url = picker.candidate?.thumb?.takeIf { picker.browsing == kind }
    val thumb by produceState<ImageBitmap?>(null, url, px) { value = url?.let { picker.thumbnail(it, px) } }
    return thumb.takeIf { url != null }
}

// SteamGridDB heroes are 1920×620; scenes 16:9.
private const val HERO_ASPECT = 620f / 1920f
private const val SCENE_ASPECT = 9f / 16f

@Composable
private fun Dp.px(): Int = with(LocalDensity.current) { toPx().roundToInt() }
