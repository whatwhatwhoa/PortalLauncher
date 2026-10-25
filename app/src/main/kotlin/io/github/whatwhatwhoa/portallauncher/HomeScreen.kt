package io.github.whatwhatwhoa.portallauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Status top-right, the favourites carousel in the middle, the dock under it, the hint bar at
 * the bottom. A swipe up anywhere opens All apps.
 */
@Composable
fun HomeScreen(state: LauncherState) {
    Column(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var travel = 0f
            detectVerticalDragGestures(
                onDragStart = { travel = 0f },
                onDragEnd = { if (travel < -SWIPE_UP.toPx()) state.openDrawer() },
                onVerticalDrag = { _, dy -> travel += dy },
            )
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Dimens.top, start = Dimens.edge, end = Dimens.edge)) {
            Spacer(Modifier.weight(1f))
            Clock()
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Carousel(state)
        }
        Dock(state, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Dimens.dockAboveHints))
        HintBar(
            hints = listOf("A" to "Open", "Y" to "All apps"),
            note = "− Accent: ${state.accent.label}    X Font: ${state.font.label}",
        )
    }
}

private val SWIPE_UP = 96.dp
private val TITLE_MAX = 480.dp

/** The favourites row. The focused tile is kept at the first slot; its title floats above it. */
@Composable
private fun Carousel(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val apps = state.row
    val focus = state.rowFocus
    val listState = rememberLazyListState()
    LaunchedEffect(focus, apps.size) {
        if (apps.isNotEmpty()) listState.animateScrollToItem(focus)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.edge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.tileGap),
    ) {
        itemsIndexed(apps, key = { _, app -> app.key }) { i, app ->
            val focused = state.zone == Zone.Row && i == focus
            Column(Modifier.width(Dimens.tile)) {
                Box(Modifier.fillMaxWidth().height(t.title.lineHeight.value.dp)) {
                    if (focused) {
                        BasicText(
                            app.label,
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).widthIn(max = TITLE_MAX),
                            style = t.title.copy(color = p.accent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.titleGap))
                AppIcon(
                    app,
                    RoundedCornerShape(Dimens.tileRadius),
                    Modifier.size(Dimens.tile)
                        .focusTile(focused, Dimens.tileRadius)
                        .clickable(interactionSource = null, indication = null) { state.tapRow(i) },
                )
            }
        }
    }
}

/** All apps first, then the utility apps, as round buttons; the focused one's name sits above. */
@Composable
private fun Dock(state: LauncherState, modifier: Modifier) {
    val inDock = state.zone == Zone.Dock
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.dockGap)) {
        DockButton("All apps", inDock && state.dockFocus == 0, onClick = { state.tapDock(0) }) {
            AllAppsGlyph()
        }
        state.dock.forEachIndexed { i, app ->
            DockButton(app.label, inDock && state.dockFocus == i + 1, onClick = { state.tapDock(i + 1) }) {
                AppIcon(app, CircleShape, Modifier.size(Dimens.dockButton))
            }
        }
    }
}

@Composable
private fun DockButton(label: String, focused: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Box(Modifier.size(Dimens.dockButton)) {
        if (focused) {
            BasicText(
                label,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = -(t.caption.lineHeight.value.dp + Dimens.dockLabelGap))
                    .wrapContentWidth(unbounded = true),
                style = t.caption.copy(color = p.text),
                maxLines = 1,
            )
        }
        Box(
            Modifier.size(Dimens.dockButton)
                .focusTile(focused, corner = null)
                .clip(CircleShape)
                .background(p.surface)
                .clickable(interactionSource = null, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
