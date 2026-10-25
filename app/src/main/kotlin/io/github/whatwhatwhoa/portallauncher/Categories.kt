package io.github.whatwhatwhoa.portallauncher

import android.content.pm.ApplicationInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Home's categories, in the owner's order (`decisions/M5-categories.md`); empty ones don't show. */
enum class Category(val label: String) {
    Favorites("Favorites"),
    Games("Games"),
    Entertainment("Entertainment"),
    Social("Social"),
    Utilities("Utilities");

    companion object {
        /** Where the owner can move an app; Favorites is a list of its own (Favorite in the sheet). */
        val movable = listOf(Games, Entertainment, Social, Utilities)
    }
}

/**
 * Android's app category, which the app's manifest declares (`appCategory`, or the older
 * `isGame`); many apps declare none, and they start in Utilities until the owner moves them.
 */
fun seedCategory(category: Int) = when (category) {
    ApplicationInfo.CATEGORY_GAME -> Category.Games
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> Category.Entertainment
    ApplicationInfo.CATEGORY_SOCIAL -> Category.Social
    else -> Category.Utilities
}

/**
 * B on Home: the categories' names stacked in big type on the content margin (Metro), the
 * focused one in the text color. Home stays behind, ghosted and showing the focused category's
 * row, so it previews where A goes (HomeScreen does the ghosting). Up and down move, A goes in,
 * B goes back; a tap on a name goes in, a tap anywhere else goes back.
 */
@Composable
fun CategoriesLayer(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val motion = LocalMotion.current
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { state.closeCategories() }) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = Dimens.edge)) {
            state.categories.forEachIndexed { i, category ->
                val color by animateColorAsState(
                    if (i == state.categoryFocus) p.text else p.muted,
                    if (motion) tween(FOCUS_FADE) else snap(),
                    label = "category",
                )
                BasicText(
                    category.label,
                    Modifier.clickable(interactionSource = null, indication = null) { state.tapCategory(i) },
                    style = t.heading.copy(color = color),
                    maxLines = 1,
                )
            }
        }
    }
}

private const val FOCUS_FADE = 150
