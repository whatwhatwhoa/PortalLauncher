package io.github.whatwhatwhoa.portallauncher

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.text.Normalizer

/**
 * Every app, alphabetical, in a grid; X adds or removes a favorite, Triangle opens its options.
 * Y (or Up from the first row) moves focus to the letter strip in the header, which searches.
 */
@Composable
fun AllAppsScreen(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val apps = state.drawer
    val focus = state.drawerFocus
    val cols = Dimens.DRAWER_COLUMNS
    // Opens where its focus is, so it doesn't scroll down to it while it slides in.
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = maxOf(0, focus / cols - 1) * cols)
    val motion = LocalMotion.current
    val follow = remember { GridFollow() }
    val query = state.drawerQuery
    // Keep the focused row second from the top, so there is always a row of context above it.
    // A new search's results are new contents, so it jumps to them.
    LaunchedEffect(focus, query) {
        if (apps.isEmpty()) return@LaunchedEffect
        follow.to(gridState, focus, cols, motion, contents = query)
    }
    // A touch scroll moves the grid but not the focus: once it settles with the focus out of
    // view, focus moves into view, so the next press goes on from what is shown instead of
    // scrolling back. Only a drag says so; the grid's own scrolls start none.
    LaunchedEffect(gridState) {
        gridState.interactionSource.interactions.collectLatest { interaction ->
            if (interaction !is DragInteraction.Stop && interaction !is DragInteraction.Cancel) return@collectLatest
            // The fling starts after the drag ends; wait for it to stop too.
            withFrameNanos {}
            snapshotFlow { gridState.isScrollInProgress }.first { !it }
            inView(gridState, state.drawerFocus, cols)?.let(state::focusDrawer)
        }
    }
    Column(Modifier.fillMaxSize().background(p.background)) {
        // Below the status band, which is drawn over this screen and has the clock top-left.
        Row(
            Modifier.fillMaxWidth().padding(
                top = Dimens.top + t.body.lineHeight.asDp(),
                start = Dimens.edge,
                end = Dimens.edge,
            ),
            verticalAlignment = Alignment.CenterVertically,
            // Room for the ring on the strip's first letter, clear of the count.
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            // The strip is measured first; what it leaves goes to the title or the search.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                val tapSearch = Modifier.clickable(interactionSource = null, indication = null) { state.tapSearch() }
                if (query.isEmpty()) {
                    // The strip needs the title's room while it has focus; the count stays.
                    if (!state.searchFocused) BasicText("All apps", tapSearch, style = t.title.copy(color = p.text), maxLines = 1)
                } else {
                    // The search stands where the title was; a long one shows its end, the letters just added.
                    BasicText(
                        query,
                        Modifier.weight(1f, fill = false).then(tapSearch),
                        style = t.title.copy(color = p.text),
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
                val hidden = state.hiddenCount
                val searching = query.isNotEmpty() || state.searchFocused
                val count = if (hidden > 0 && !state.showHidden && !searching) "${apps.size} · $hidden hidden" else "${apps.size}"
                BasicText(count, style = t.body.copy(color = p.muted), maxLines = 1)
                if (!state.searchFocused) LetterJump(state)
            }
            if (state.searchFocused) LetterStrip(state)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Dimens.edge, vertical = Space.xl),
            horizontalArrangement = Arrangement.spacedBy(Dimens.drawerGap),
            verticalArrangement = Arrangement.spacedBy(Dimens.drawerGap),
        ) {
            itemsIndexed(apps, key = { _, app -> app.key }) { i, app ->
                DrawerTile(
                    app,
                    focused = i == focus && !state.searchFocused,
                    favorite = state.isFavorite(app),
                    hidden = state.isHidden(app),
                    onClick = { state.tapDrawer(i) },
                    onHold = { state.holdDrawer(i) },
                    modifier = Modifier.launchesFrom(state, Origin.Drawer, app),
                )
            }
        }
        val hints = if (state.searchFocused) {
            // In the letters X takes one back and Y goes to the results; the bar keeps its shape,
            // so only the labels change.
            listOf(
                Hint(SQUARE, "Quick settings"),
                Hint("X", "Delete", room = "Unfavorite"),
                Hint("Y", "Done", room = "Search"),
                Hint(TRIANGLE, "Options"),
            )
        } else {
            val favorite = apps.getOrNull(focus)?.let(state::isFavorite) == true
            listOf(
                Hint(SQUARE, "Quick settings"),
                Hint("X", if (favorite) "Unfavorite" else "Favorite", room = "Unfavorite"),
                Hint("Y", "Search"),
                Hint(TRIANGLE, "Options"),
            )
        }
        HintBar(hints)
    }
}

/**
 * L1 and R1 jump by letter: the bumpers drawn either side of the focused app's letter (only with
 * Button hints on, as the Art… tabs have them), the letter lit for a moment after a jump so
 * where it landed reads at a glance, then muted.
 */
@Composable
private fun LetterJump(state: LauncherState) {
    val letter = state.drawerLetter ?: return
    val p = LocalPalette.current
    val t = LocalType.current
    val jumps = state.letterJumps
    // Opening (or leaving the strip) shows the letter muted; only a jump after that lights it.
    val shownAt = remember { jumps }
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(jumps) {
        if (jumps == shownAt) return@LaunchedEffect
        lit = true
        delay(LETTER_LIT)
        lit = false
    }
    val color by animateColorAsState(
        if (lit) p.text else p.muted,
        if (LocalMotion.current) tween(LETTER_FADE) else snap(),
        label = "letter",
    )
    val hints = LocalHome.current.hints
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        if (hints) BumperGlyph(left = true)
        // The room of the widest letter, so R1 holds still as the letter changes.
        Box(contentAlignment = Alignment.Center) {
            BasicText("W", style = t.body.copy(color = Color.Transparent), maxLines = 1)
            BasicText(letter.toString(), style = t.body.copy(color = color), maxLines = 1)
        }
        if (hints) BumperGlyph(left = false)
    }
}

/** The letters to search with, the focused one ringed; a tap adds one. */
@Composable
private fun LetterStrip(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LETTER_GAP)) {
        SEARCH_LETTERS.forEachIndexed { i, letter ->
            val focused = i == state.searchLetter
            BasicText(
                letter.toString(),
                // At least the cell, so a wide M or W grows it instead of clipping.
                Modifier.widthIn(min = LETTER_CELL)
                    .focusRing(focused, Space.s)
                    .clickable(interactionSource = null, indication = null) { state.tapSearchLetter(i) },
                style = t.title.copy(color = if (focused) p.text else p.muted, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/**
 * The tile to move focus to after a touch scroll, or null while the focused one is still wholly
 * in view: the same column on the second row in view, matching the focused row's place.
 */
private fun inView(grid: LazyGridState, focus: Int, cols: Int): Int? {
    val info = grid.layoutInfo
    val whole = info.visibleItemsInfo.filter {
        it.offset.y >= info.viewportStartOffset && it.offset.y + it.size.height <= info.viewportEndOffset
    }
    if (whole.isEmpty() || whole.any { it.index == focus }) return null
    val rows = whole.map { it.index / cols }.distinct().sorted()
    val row = rows.getOrElse(1) { rows.first() }
    val inRow = whole.map { it.index }.filter { it / cols == row }
    val same = row * cols + focus % cols
    return if (same in inRow) same else inRow.max()
}

/**
 * Keeps a grid's focused row second from the top as focus moves. A held key moves focus a row
 * every repeat (about 50 ms), and a fresh scroll each time started from rest, so the grid
 * trailed the focus in steps; a target that comes that soon after the last is glided to at an
 * even speed instead, over the time the last one took, so the grid keeps pace while the key is
 * held. New [contents] (the grid opening, a new search) jump straight there.
 */
class GridFollow {
    private var contents: Any? = this
    private var top = -1
    private var movedAt = 0L

    suspend fun to(grid: LazyGridState, focus: Int, columns: Int, motion: Boolean, contents: Any?) {
        val top = maxOf(0, focus / columns - 1) * columns
        val now = SystemClock.uptimeMillis()
        val since = now - movedAt
        val fresh = contents !== this.contents
        val moved = top != this.top
        this.contents = contents
        if (moved) {
            this.top = top
            movedAt = now
        }
        when {
            fresh || !motion -> grid.scrollToItem(top)
            moved && since < HELD_GAP -> glide(grid, top, columns, since)
            else -> grid.animateScrollToItem(top)
        }
    }

    private suspend fun glide(grid: LazyGridState, top: Int, columns: Int, millis: Long) {
        val info = grid.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull() ?: return grid.scrollToItem(top)
        val pitch = first.size.height + info.mainAxisItemSpacing
        val rows = top / columns - grid.firstVisibleItemIndex / columns
        val distance = rows * pitch - grid.firstVisibleItemScrollOffset
        // Android's animation scale (0.5 on the Portal) would shorten the glide and bring the
        // steps back; it is divided out so the glide lasts until the next repeat.
        val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor?.takeIf { it > 0f } ?: 1f
        val duration = (millis.coerceAtLeast(FRAME) / scale).toInt()
        grid.animateScrollBy(distance.toFloat(), tween(duration, easing = LinearEasing))
    }

    private companion object {
        // Sooner than this after the last target is a held key (Android repeats every 50 ms).
        const val HELD_GAP = 150L
        const val FRAME = 16L
    }
}

/** The search strip's letters in order; # stands for any digit. */
const val SEARCH_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

/**
 * How well [label] matches [query] (lowercase letters and #), best first: 0 the label starts
 * with it, 1 one of its words does, 2 its words' initials hold it ("gta"), 3 it is anywhere in
 * it; null is no match. Accents, case, spaces and punctuation don't count, so "pokemon" finds
 * "Pokémon" and "zeldabreath" finds "Zelda: Breath…".
 */
fun searchRank(label: String, query: String): Int? {
    val words = Normalizer.normalize(label, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
        .split(NOT_WORD).filter { it.isNotEmpty() }
    val whole = words.joinToString("")
    return when {
        whole.holds(query, 0) -> 0
        words.any { it.holds(query, 0) } -> 1
        words.joinToString("") { it.take(1) }.holdsAnywhere(query) -> 2
        whole.holdsAnywhere(query) -> 3
        else -> null
    }
}

private fun String.holds(query: String, at: Int) =
    at + query.length <= length && query.indices.all { query[it] == this[at + it] || (query[it] == '#' && this[at + it].isDigit()) }

private fun String.holdsAnywhere(query: String) = (0..length - query.length).any { holds(query, it) }

private val MARKS = Regex("\\p{Mn}+")
private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

@Composable
private fun DrawerTile(
    app: AppEntry,
    focused: Boolean,
    favorite: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val t = LocalType.current
    Column(
        Modifier.combinedClickable(interactionSource = null, indication = null, onLongClick = onHold, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(Dimens.drawerTile).then(modifier).focusTile(focused, Dimens.drawerRadius).depth(Dimens.drawerRadius)) {
            // Hidden apps only show while the owner asks to see them, faded so they read as hidden.
            AppIcon(
                app,
                RoundedCornerShape(Dimens.drawerRadius),
                Modifier.size(Dimens.drawerTile).alpha(if (hidden) HIDDEN_ALPHA else 1f),
            )
            if (favorite) {
                // A small accent dot on the corner, ringed in the background so it lifts off any icon.
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .offset(x = Space.xs, y = -Space.xs)
                        .size(Space.m)
                        .background(p.accent, CircleShape)
                        .border(2.dp, p.background, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        BasicText(
            app.label,
            modifier = Modifier.width(Dimens.drawerTile + Dimens.drawerGap),
            style = t.caption.copy(color = if (focused) p.text else p.muted, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val HIDDEN_ALPHA = 0.4f
// How long a letter jump lights the letter, and its fade (the categories' focus fade).
private const val LETTER_LIT = 1000L
private const val LETTER_FADE = 150
// A letter's room in the strip, and the gap between: the focus ring, drawn Dimens.ringGap +
// Dimens.ring outside the room, then clears the next letter even beside a W. The 27 fit the
// header beside the count and a short search, not the title, which gives way to them.
private val LETTER_CELL = Space.xl
private val LETTER_GAP = Space.s
