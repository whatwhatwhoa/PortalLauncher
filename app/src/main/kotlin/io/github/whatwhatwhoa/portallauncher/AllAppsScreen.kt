package io.github.whatwhatwhoa.portallauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Every app, alphabetical, in a grid; X adds or removes a favorite, + opens its options. */
@Composable
fun AllAppsScreen(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val apps = state.drawer
    val focus = state.drawerFocus
    val cols = Dimens.DRAWER_COLUMNS
    val gridState = rememberLazyGridState()
    val motion = LocalMotion.current
    // Keep the focused row second from the top, so there is always a row of context above it.
    LaunchedEffect(focus) {
        if (apps.isEmpty()) return@LaunchedEffect
        val top = maxOf(0, focus / cols - 1) * cols
        if (motion) gridState.animateScrollToItem(top) else gridState.scrollToItem(top)
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
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            BasicText("All apps", style = t.title.copy(color = p.text))
            val hidden = state.hiddenCount
            val count = if (hidden > 0 && !state.showHidden) "${apps.size} · $hidden hidden" else "${apps.size}"
            BasicText(count, style = t.body.copy(color = p.muted))
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
                    focused = i == focus,
                    favorite = state.isFavorite(app),
                    hidden = state.isHidden(app),
                    onClick = { state.tapDrawer(i) },
                    onHold = { state.holdDrawer(i) },
                    modifier = Modifier.launchesFrom(state, Origin.Drawer, app),
                )
            }
        }
        val favorite = apps.getOrNull(focus)?.let(state::isFavorite) == true
        HintBar(listOf(Hint("−", "Quick settings"), Hint("X", if (favorite) "Unfavorite" else "Favorite", room = "Unfavorite"), Hint("+", "Options")))
    }
}

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
