package io.github.whatwhatwhoa.portallauncher

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow

/**
 * What + opens on a tile: a panel from the right edge over a dimmed screen, the app at the top
 * and its options below. Up and down move, A chooses, B or + closes; a tap outside closes too.
 */
@Composable
fun AnimatedVisibilityScope.OptionsSheet(state: LauncherState, sheet: Sheet) {
    val p = LocalPalette.current
    val t = LocalType.current
    val corner = RoundedCornerShape(Space.m)
    Box(
        Modifier.fillMaxSize()
            .background(p.background.copy(alpha = SCRIM))
            .clickable(interactionSource = null, indication = null) { state.closeSheet() },
    ) {
        Column(
            Modifier.align(Alignment.CenterEnd)
                .animateEnterExit(
                    enter = if (LocalMotion.current) slideInHorizontally(Transit.enter()) { it / 8 } else EnterTransition.None,
                    exit = if (LocalMotion.current) slideOutHorizontally(Transit.exit()) { it / 8 } else ExitTransition.None,
                )
                .fillMaxHeight()
                .width(Dimens.sheet)
                .background(p.surface)
                // Taps on the panel itself must not reach the scrim behind it.
                .clickable(interactionSource = null, indication = null) {}
                .padding(start = Space.xxl, end = Space.xxl, top = Dimens.sheetTop, bottom = Space.xl),
        ) {
            val picker = state.picker
            if (picker != null && state.art != null) {
                BasicText(sheet.app.label, style = t.title.copy(color = p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.m))
                ArtPreview(sheet.app, state.art)
                Spacer(Modifier.height(Space.xl))
                ArtPage(picker)
                Spacer(Modifier.height(Space.l))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    // The cover when it has art, so a match can be checked at a glance.
                    CoverOrIcon(state.art, sheet.app, corner, Dimens.sheetIcon, Modifier.size(Dimens.sheetIcon))
                    BasicText(
                        sheet.app.label,
                        style = t.title.copy(color = p.text),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Space.xl))
                if (state.choosingCategory) {
                    val current = state.categoryOf(sheet.app)
                    Rows(state, Category.places.size, corner) { i ->
                        Choice(Category.places[i].placeLabel, picked = Category.places[i] == current)
                    }
                } else {
                    Rows(state, sheet.options.size, corner) { i ->
                        BasicText(sheet.options[i].label, style = t.body.copy(color = p.text), maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The sheet's rows: its options, or the categories on the Category… page (the one in use dotted). */
@Composable
private fun Rows(state: LauncherState, count: Int, corner: Shape, row: @Composable (Int) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        repeat(count) { i ->
            val focused = state.sheetOpen && i == state.sheetFocus
            Box(
                Modifier.fillMaxWidth()
                    .height(Dimens.sheetRow)
                    .focusRing(focused, Space.m)
                    .background(if (focused) p.rule else Color.Transparent, corner)
                    .clickable(interactionSource = null, indication = null) { state.tapSheet(i) }
                    .padding(horizontal = Space.l),
                contentAlignment = Alignment.CenterStart,
            ) {
                row(i)
            }
        }
    }
}

private const val SCRIM = 0.6f
