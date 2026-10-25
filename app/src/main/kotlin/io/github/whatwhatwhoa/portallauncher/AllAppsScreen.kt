package io.github.whatwhatwhoa.portallauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Every app, alphabetical, in a grid; X adds or removes a favourite. */
@Composable
fun AllAppsScreen(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val apps = state.drawer
    val focus = state.drawerFocus
    val cols = Dimens.DRAWER_COLUMNS
    val gridState = rememberLazyGridState()
    // Keep the focused row second from the top, so there is always a row of context above it.
    LaunchedEffect(focus) {
        if (apps.isNotEmpty()) gridState.animateScrollToItem(maxOf(0, focus / cols - 1) * cols)
    }
    Column(Modifier.fillMaxSize().background(p.background)) {
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.top, start = Dimens.edge, end = Dimens.edge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            BasicText("All apps", style = t.title.copy(color = p.text))
            BasicText("${apps.size}", style = t.body.copy(color = p.muted))
            Spacer(Modifier.weight(1f))
            Clock()
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
                DrawerTile(app, focused = i == focus, favourite = state.isFavourite(app)) { state.tapDrawer(i) }
            }
        }
        val favourite = apps.getOrNull(focus)?.let(state::isFavourite) == true
        HintBar(listOf("A" to "Open", "X" to if (favourite) "Unfavourite" else "Favourite", "B" to "Back"))
    }
}

@Composable
private fun DrawerTile(app: AppEntry, focused: Boolean, favourite: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Column(
        Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(Dimens.drawerTile).focusTile(focused, Dimens.drawerRadius)) {
            AppIcon(app, RoundedCornerShape(Dimens.drawerRadius), Modifier.size(Dimens.drawerTile))
            if (favourite) {
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
