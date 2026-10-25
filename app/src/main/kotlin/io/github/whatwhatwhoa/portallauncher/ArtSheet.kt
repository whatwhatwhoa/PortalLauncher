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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface ArtRow {
    /** Opens the matched game's images for one slot. */
    class Slot(val kind: ArtKind) : ArtRow
    /** Opens Android's photo picker for one slot. */
    class Gallery(val kind: ArtKind) : ArtRow
    class Result(val match: ArtMatch) : ArtRow
    data object Search : ArtRow
    data object UseIcon : ArtRow
}

/**
 * The options sheet's Art… page: SteamGridDB's matches for the app's label, a search for other
 * words, and the app's own icon. Choosing a match fetches it and shows it at the top, and the
 * page stays open so another can be tried; B goes back to the options. Once a game is matched,
 * a cover, a hero and a scene row open its images (the scene's from Steam) as a grid of
 * thumbnails: A takes one, B goes back to the rows. Any app can take a cover or a scene from the
 * gallery; [pickImage] opens the photo picker and hands back the image, or null.
 */
class ArtPicker(
    val app: AppEntry,
    private val art: Art,
    private val scope: CoroutineScope,
    private val pickImage: ((Uri?) -> Unit) -> Unit,
) {
    val pkg: String = app.component.packageName
    var query by mutableStateOf(app.label)
    var results by mutableStateOf<List<ArtMatch>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    /** The game being downloaded, shown on its row. */
    var fetching by mutableStateOf<Int?>(null)
        private set
    var failed by mutableStateOf<String?>(null)
        private set
    /** True while the search field has the keyboard; keys then go to the field, not to us. */
    var typing by mutableStateOf(false)
        private set
    private var index by mutableIntStateOf(0)
    private var job: Job? = null

    /** The slot whose images are showing instead of the rows, or null. */
    var browsing by mutableStateOf<ArtKind?>(null)
        private set
    var images by mutableStateOf<List<ArtImage>>(emptyList())
        private set
    var loadingImages by mutableStateOf(false)
        private set
    /** The image being downloaded or saved, shown in the caption. */
    var picking by mutableStateOf<String?>(null)
        private set
    private var imageIndex by mutableIntStateOf(0)
    val imageFocus get() = imageIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))

    private val matched get() = (art.record(pkg) as? ArtRecord.Matched)?.match
    private val slots get() = if (matched != null) SLOTS.map { ArtRow.Slot(it) } else emptyList()
    /** Without a SteamGridDB key the page has only the gallery and the icon. */
    val hasKey get() = art.hasKey
    private val searchRows get() = if (hasKey) results.map { ArtRow.Result(it) } + ArtRow.Search else emptyList()
    val rows: List<ArtRow>
        get() = slots + searchRows + GALLERY.map { ArtRow.Gallery(it) } + ArtRow.UseIcon
    val focus get() = index.coerceIn(0, rows.lastIndex)

    init {
        if (hasKey) search()
    }

    /** False when B leaves the page. */
    fun onAction(action: Action): Boolean {
        if (browsing != null) {
            onBrowse(action)
            return true
        }
        when (action) {
            Action.Up -> index = focus - 1
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
        index = i
        choose(i)
    }

    fun tapImage(i: Int) {
        imageIndex = i
        images.getOrNull(i)?.let(::pick)
    }

    fun stopBrowsing() {
        browsing = null
        images = emptyList()
    }

    private fun onBrowse(action: Action) {
        val columns = columns(browsing ?: return)
        when (action) {
            Action.Left -> imageIndex = imageFocus - 1
            Action.Right -> imageIndex = imageFocus + 1
            Action.Up -> imageIndex = imageFocus - columns
            Action.Down -> imageIndex = imageFocus + columns
            Action.First -> imageIndex = 0
            Action.Last -> imageIndex = images.lastIndex
            Action.Open -> images.getOrNull(imageFocus)?.let(::pick)
            Action.Back -> stopBrowsing()
            else -> Unit
        }
        imageIndex = imageFocus
    }

    private fun browse(kind: ArtKind) {
        if (matched == null) return
        browsing = kind
        images = emptyList()
        failed = null
        job?.cancel()
        job = scope.launch {
            loadingImages = true
            images = try {
                art.images(pkg, kind)
            } catch (e: Exception) {
                failed = "Couldn't reach SteamGridDB"
                emptyList()
            }
            loadingImages = false
            imageIndex = images.indexOfFirst { isPicked(kind, it) }.coerceAtLeast(0)
        }
    }

    private fun pick(image: ArtImage) {
        val kind = browsing ?: return
        if (picking != null || isPicked(kind, image)) return
        scope.launch {
            picking = image.url
            failed = if (art.pick(pkg, kind, image)) null else "Couldn't fetch that image"
            picking = null
        }
    }

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

    fun stopTyping() {
        typing = false
    }

    fun search() {
        typing = false
        job?.cancel()
        job = scope.launch {
            searching = true
            failed = null
            results = try {
                art.search(query)
            } catch (e: Exception) {
                failed = "Couldn't reach SteamGridDB"
                emptyList()
            }
            searching = false
            // Start on the match in use, if it is among the results.
            val at = results.indexOfFirst { it.gameId == matched?.gameId }
            index = if (at >= 0) slots.size + at else 0
        }
    }

    private fun choose(i: Int) {
        when (val row = rows.getOrNull(i)) {
            is ArtRow.Slot -> browse(row.kind)
            is ArtRow.Gallery -> if (picking == null) fromGallery(row.kind)
            is ArtRow.Result -> if (fetching == null && !isCurrent(row.match)) fetch(row.match)
            ArtRow.Search -> typing = true
            ArtRow.UseIcon -> art.useIcon(pkg)
            null -> Unit
        }
    }

    private fun fromGallery(kind: ArtKind) = pickImage { uri ->
        if (uri != null) {
            scope.launch {
                picking = uri.toString()
                failed = art.useImage(pkg, kind, uri)
                picking = null
            }
        }
    }

    private fun fetch(match: ArtMatch) = scope.launch {
        fetching = match.gameId
        failed = if (art.use(pkg, match)) null else "No art for ${match.name}"
        fetching = null
    }

    fun isCurrent(match: ArtMatch) = matched?.gameId == match.gameId
    val usingIcon get() = art.record(pkg) == ArtRecord.Icon

    companion object {
        val SLOTS = listOf(ArtKind.Cover, ArtKind.Hero, ArtKind.Scene)
        val GALLERY = listOf(ArtKind.Cover, ArtKind.Scene)

        /** Thumbnails across the panel: square covers three to a row, 16:9 scenes two, heroes one. */
        fun columns(kind: ArtKind) = when (kind) {
            ArtKind.Cover -> 3
            ArtKind.Scene -> 2
            ArtKind.Hero -> 1
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
            val focused = i == focus && !picker.typing
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
                    is ArtRow.Slot -> Choice("Choose the ${row.kind.suffix}…", picked = false)
                    is ArtRow.Gallery -> Choice("${row.kind.suffix.replaceFirstChar { it.uppercase() }} from the gallery…", picked = false)
                    is ArtRow.Result -> Choice(
                        when (row.match.gameId) {
                            picker.fetching -> "Fetching…"
                            else -> row.match.label
                        },
                        picked = picker.isCurrent(row.match),
                    )
                    ArtRow.Search -> SearchField(picker)
                    ArtRow.UseIcon -> Choice("Use the app’s icon", picked = picker.usingIcon)
                }
            }
        }
    }
}

/** One slot's images for the matched game; the one in use has the accent dot. */
@Composable
private fun ColumnScope.ImageGrid(picker: ArtPicker, kind: ArtKind) {
    val p = LocalPalette.current
    val t = LocalType.current
    val corner = RoundedCornerShape(Space.s)
    val gridState = rememberLazyGridState()
    val columns = ArtPicker.columns(kind)
    val focus = picker.imageFocus
    val motion = LocalMotion.current
    LaunchedEffect(focus, picker.images) {
        if (picker.images.isEmpty()) return@LaunchedEffect
        // One row of context above the focused image.
        val target = ((focus / columns - 1) * columns).coerceAtLeast(0)
        if (motion) gridState.animateScrollToItem(target) else gridState.scrollToItem(target)
    }
    val name = when (kind) {
        ArtKind.Cover -> "Covers"
        ArtKind.Hero -> "Heroes"
        ArtKind.Scene -> "Steam screenshots"
    }
    BasicText(
        when {
            picker.loadingImages -> "Loading ${name.lowercase()}…"
            picker.picking != null -> "Fetching…"
            picker.failed != null -> picker.failed!!
            picker.images.isEmpty() -> "No ${if (kind == ArtKind.Scene) "Steam screenshots" else name.lowercase()} for this game"
            else -> "$name · ${focus + 1} of ${picker.images.size}"
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
        gridItemsIndexed(picker.images, key = { _, image -> image.url }) { i, image ->
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
                if (picker.isPicked(kind, image)) {
                    Box(Modifier.align(Alignment.TopEnd).padding(Space.xs).size(Space.m).background(p.accent, CircleShape))
                }
            }
        }
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
 * The art in use, for checking a match: the square cover beside what fills Home behind it, the
 * scene at 16:9 when there is one, else the hero's wide strip. Falls back to the icon when there
 * is no cover; an empty strip when there is neither.
 */
@Composable
fun ArtPreview(app: AppEntry, art: Art) {
    val pkg = app.component.packageName
    val p = LocalPalette.current
    val corner = RoundedCornerShape(Space.s)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        val size = Dimens.artPreview
        val cover = rememberArt(art, pkg, ArtKind.Cover, size.px())
        if (cover != null) {
            Image(cover, null, Modifier.size(size).clip(corner), contentScale = ContentScale.Crop)
        } else {
            AppIcon(app, corner, Modifier.size(size))
        }
        val heroWidth = Dimens.sheet - Space.xxl * 2 - size - Space.m
        val scene = rememberArt(art, pkg, ArtKind.Scene, heroWidth.px())
        val hero = rememberArt(art, pkg, ArtKind.Hero, heroWidth.px())
        val wide = scene ?: hero
        Box(Modifier.size(heroWidth, heroWidth * if (scene != null) SCENE_ASPECT else HERO_ASPECT).clip(corner).background(p.rule)) {
            if (wide != null) Image(wide, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

// SteamGridDB heroes are 1920×620; scenes 16:9.
private const val HERO_ASPECT = 620f / 1920f
private const val SCENE_ASPECT = 9f / 16f

@Composable
private fun Dp.px(): Int = with(LocalDensity.current) { toPx().roundToInt() }
