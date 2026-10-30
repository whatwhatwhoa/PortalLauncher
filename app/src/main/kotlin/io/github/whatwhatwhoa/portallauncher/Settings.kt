package io.github.whatwhatwhoa.portallauncher

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The panel's categories, in order, down its left side (`decisions/F25-settings.md`). */
enum class SettingGroup(val label: String) {
    Look("Look"), Home("Home"), Sound("Sound"), Art("Art"), Apps("Apps"), System("System"),
}

/**
 * One row of the settings panel. A value row changes with left and right, and A steps it on; an
 * action row ([acts]) does its one thing on A, and one that can't be undone ([confirms]) asks
 * for a second A first.
 */
enum class Setting(val group: SettingGroup, val label: String, val acts: Boolean = false, val confirms: Boolean = false) {
    Theme(SettingGroup.Look, "Theme"),
    Accent(SettingGroup.Look, "Accent"),
    Backdrop(SettingGroup.Look, "Backdrop"),
    Covers(SettingGroup.Look, "Covers on tiles"),
    Name(SettingGroup.Look, "Big name"),
    Gradient(SettingGroup.Look, "Background gradient"),
    Glow(SettingGroup.Look, "Focus glow"),
    Depth(SettingGroup.Look, "Tile depth"),
    Reflection(SettingGroup.Look, "Hero reflection"),
    Pulse(SettingGroup.Look, "Focus pulse"),
    Clock(SettingGroup.Home, "Clock"),
    BatteryPercent(SettingGroup.Home, "Battery percent"),
    Hints(SettingGroup.Home, "Button hints"),
    Wraps(SettingGroup.Home, "Carousel wraps"),
    Start(SettingGroup.Home, "Home key goes to"),
    PlayControls(SettingGroup.Home, "Home screen play controls"),
    HomeHold(SettingGroup.Home, "Hold Home for standby", acts = true),
    SoundsOn(SettingGroup.Sound, "Sounds"),
    SoundSet(SettingGroup.Sound, "Family"),
    SoundVolume(SettingGroup.Sound, "Volume"),
    ArtKey(SettingGroup.Art, "SteamGridDB key", acts = true),
    FetchArt(SettingGroup.Art, "Fetch art"),
    ClearArt(SettingGroup.Art, "Clear all art", acts = true, confirms = true),
    HiddenApps(SettingGroup.Apps, "Hidden apps", acts = true),
    ResetDock(SettingGroup.Apps, "Reset the dock", acts = true, confirms = true),
    HomeApp(SettingGroup.System, "Home app", acts = true),
    AndroidSettings(SettingGroup.System, "Android settings", acts = true),
    Updates(SettingGroup.System, "Updates", acts = true),
    SaveSetup(SettingGroup.System, "Save setup", acts = true),
    RestoreSetup(SettingGroup.System, "Restore setup", acts = true, confirms = true),
}

/** The Look settings after one step of [setting]: a choice moves along its list and wraps, a switch flips. */
fun LookSettings.step(setting: Setting, by: Int, backdrops: List<Backdrop>): LookSettings = when (setting) {
    Setting.Theme -> copy(theme = theme.cycled(by))
    Setting.Accent -> copy(accent = accent.cycled(by))
    Setting.Backdrop -> {
        val i = backdrops.indexOfFirst { it.id == backdrop }.coerceAtLeast(0)
        copy(backdrop = backdrops[Math.floorMod(i + by, backdrops.size)].id)
    }
    Setting.Covers -> copy(covers = !covers)
    Setting.Name -> copy(name = name.cycled(by))
    Setting.Gradient -> copy(effects = effects.copy(gradient = !effects.gradient))
    Setting.Glow -> copy(effects = effects.copy(glow = !effects.glow))
    Setting.Depth -> copy(effects = effects.copy(depth = !effects.depth))
    Setting.Reflection -> copy(effects = effects.copy(reflection = !effects.reflection))
    Setting.Pulse -> copy(pulse = pulse.cycled(by))
    else -> this
}

/** The Home settings after one step of [setting], the same way. */
fun HomeSettings.step(setting: Setting, by: Int): HomeSettings = when (setting) {
    Setting.Clock -> copy(clock = clock.cycled(by))
    Setting.BatteryPercent -> copy(batteryPercent = !batteryPercent)
    Setting.Hints -> copy(hints = !hints)
    Setting.Wraps -> copy(wraps = !wraps)
    Setting.Start -> copy(start = start.cycled(by))
    Setting.PlayControls -> copy(playControls = !playControls)
    else -> this
}

private inline fun <reified E : Enum<E>> E.cycled(by: Int): E {
    val all = enumValues<E>()
    return all[Math.floorMod(ordinal + by, all.size)]
}

/** What a row shows on its right; an armed row asks for the second A instead. */
private fun LauncherState.shown(setting: Setting): String {
    if (setting == armed) return "A again to confirm"
    return when (setting) {
        Setting.Theme -> look.theme.label
        Setting.Accent -> look.accent.label
        Setting.Backdrop -> backdrop.label
        Setting.Covers -> onOff(look.covers)
        Setting.Name -> look.name.label
        Setting.Gradient -> onOff(look.effects.gradient)
        Setting.Glow -> onOff(look.effects.glow)
        Setting.Depth -> onOff(look.effects.depth)
        Setting.Reflection -> onOff(look.effects.reflection)
        Setting.Pulse -> look.pulse.label
        Setting.Clock -> home.clock.label
        Setting.BatteryPercent -> onOff(home.batteryPercent)
        Setting.Hints -> onOff(home.hints)
        Setting.Wraps -> onOff(home.wraps)
        // The category itself: Favorites only while it has apps and keeps that name.
        Setting.Start -> if (home.start == StartOn.Favorites) startCategory.label else home.start.label
        Setting.PlayControls -> onOff(home.playControls)
        // Grayed out by Android: the next A opens App info, where ⋮ allows it (README *Standby*);
        // back from there it asks, and from then on A opens the switch and the row never says Restricted.
        Setting.HomeHold -> when {
            homeHold -> onOff(true)
            homeHoldInfoShown -> "Allowed? A: switch it on"
            homeHoldTried && homeHoldRestricted -> "Restricted: A, then ⋮ → Allow"
            else -> onOff(false)
        }
        Setting.SoundsOn -> onOff(sound.on)
        Setting.SoundSet -> sound.family.label
        Setting.SoundVolume -> sound.level.label
        Setting.ArtKey -> keyNote ?: if (art?.hasKey == true) "Set" else "Not set"
        Setting.FetchArt -> onOff(fetchArt)
        Setting.ClearArt -> artBytes?.let(::megabytes).orEmpty()
        Setting.HiddenApps -> if (hiddenCount == 0) "None" else "$hiddenCount"
        Setting.ResetDock -> if (dockChanged) "" else "Default"
        Setting.HomeApp -> if (isHomeApp) "Portal" else if (homeElsewhere) "Another app" else "Not set"
        Setting.AndroidSettings -> ""
        Setting.Updates -> updateNote.orEmpty()
        Setting.SaveSetup -> saveNote.orEmpty()
        Setting.RestoreSetup -> restoreNote.orEmpty()
    }
}

private fun onOff(on: Boolean) = if (on) "On" else "Off"

private fun megabytes(bytes: Long) = if (bytes < 1_000_000) "Under 1 MB" else "${(bytes + 500_000) / 1_000_000} MB"

/**
 * Launcher Settings, from the options sheet (Triangle) or Android's launcher-settings gear: about 70% of the screen
 * from the right edge, over an undimmed Home, so each change is judged live against the strip of
 * it left showing. Laid out as the Switch's settings: the categories down the left, the chosen
 * one's rows beside them (`LauncherState.onSettings` for the keys). A tap outside closes. A long
 * page scrolls, keeping the focused row in view; the credits close the System page.
 */
@Composable
fun AnimatedVisibilityScope.SettingsPanel(state: LauncherState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val motion = LocalMotion.current
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { state.closeSettings() }) {
        Column(
            Modifier.align(Alignment.CenterEnd)
                .animateEnterExit(
                    enter = if (motion) slideInHorizontally(Transit.enter()) { it / 8 } else EnterTransition.None,
                    exit = if (motion) slideOutHorizontally(Transit.exit()) { it / 8 } else ExitTransition.None,
                )
                .fillMaxHeight()
                .width(Dimens.settingsSheet)
                .background(p.panel)
                .clickable(interactionSource = null, indication = null) {}
                .padding(start = Space.xxl, end = Space.xxl, top = Dimens.sheetTop, bottom = Space.xl),
        ) {
            BasicText(if (state.accentOpen) "Accent" else "Launcher Settings", style = t.title.copy(color = p.text), maxLines = 1)
            Spacer(Modifier.height(Space.s))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                Column(
                    Modifier.width(Dimens.settingsNav).padding(top = Space.l),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    state.settingGroups.forEachIndexed { i, group ->
                        GroupRow(
                            group.label,
                            chosen = i == state.settingsGroupFocus,
                            focused = state.settingsOpen && !state.settingsInRows && !state.accentOpen && i == state.settingsGroupFocus,
                            onClick = { state.tapGroup(i) },
                        )
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.rule))
                // A on Accent opens its picker in place of the rows; B comes back to them.
                if (state.accentOpen) {
                    Column(Modifier.weight(1f)) { AccentPicker(state) }
                    return@Row
                }
                // A fresh scroll per category, so each page opens at its top.
                key(state.settingsGroup) {
                    GroupPage(state, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** The chosen category's rows; System's end with the credits. */
@Composable
private fun GroupPage(state: LauncherState, modifier: Modifier) {
    val p = LocalPalette.current
    val t = LocalType.current
    val rows = state.groupSettings
    val credits = state.settingsGroup == SettingGroup.System
    // The top inset inside the scroll leaves the first row's ring room, as the categories' has.
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(top = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        rows.forEachIndexed { i, setting ->
            SettingRow(
                setting,
                state.shown(setting),
                focused = state.settingsOpen && state.settingsInRows && i == state.settingsFocus,
                last = credits && i == rows.lastIndex,
                onClick = { state.tapSetting(i) },
            )
        }
        if (credits) {
            BasicText(
                "Portal ${BuildConfig.VERSION_NAME} · Selawik font, SIL Open Font License · " +
                    "Material Symbols icons, Apache License 2.0 · " +
                    "Art from SteamGridDB and Steam",
                Modifier.padding(start = Space.l, end = Space.l, top = Space.l),
                style = t.caption.copy(color = p.muted),
            )
        }
        // Room to scroll the last row's ring clear of the bottom edge.
        Spacer(Modifier.height(Space.l))
    }
}

/**
 * A category: the chosen one lit, with an accent bar at its left as the Switch marks it, while
 * focus is in its rows; ringed and filled like a row while focus is on the categories.
 */
@Composable
private fun GroupRow(label: String, chosen: Boolean, focused: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    Row(
        Modifier.fillMaxWidth()
            .height(Dimens.sheetRow)
            .focusRing(focused, Space.m)
            .background(if (focused) p.rule else Color.Transparent, RoundedCornerShape(Space.m))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(end = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = Space.xs, end = Space.s)
                .width(Dimens.ring)
                .height(Space.xl)
                .background(if (chosen && !focused) p.accent else Color.Transparent, CircleShape),
        )
        BasicText(
            label,
            style = t.body.copy(
                color = if (chosen) p.text else p.muted,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A row: the name on the left, the value on the right. The focused row is filled and ringed like
 * the sheet's; a value row's value sits between two drawn chevrons, the sign that left and right
 * change it, while an action row has none (A does it).
 */
@Composable
private fun SettingRow(row: Setting, value: String, focused: Boolean, last: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val corner = RoundedCornerShape(Space.m)
    val inView = remember { BringIntoViewRequester() }
    // The ring and its glow lie outside the row, so a margin past it is scrolled into view too.
    val above = Space.l
    // Below the last row, the credits.
    val below = if (last) Space.l * 2 + t.caption.lineHeight.asDp() * 2 else Space.l
    val (top, margin, height) = with(LocalDensity.current) { Triple(above.toPx(), below.toPx(), Dimens.sheetRow.toPx()) }
    LaunchedEffect(focused) { if (focused) inView.bringIntoView(Rect(0f, -top, 1f, height + margin)) }
    Row(
        Modifier.fillMaxWidth()
            .height(Dimens.sheetRow)
            .bringIntoViewRequester(inView)
            .focusRing(focused, Space.m)
            .background(if (focused) p.rule else Color.Transparent, corner)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        BasicText(
            row.label,
            Modifier.weight(1f),
            style = t.body.copy(color = p.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row == Setting.Accent) Box(Modifier.size(Space.m).background(p.accent, CircleShape))
        val chevrons = focused && !row.acts
        if (chevrons) Chevron(pointsRight = false)
        BasicText(
            value,
            Modifier.padding(horizontal = Space.xs),
            style = t.body.copy(color = if (focused) p.text else p.muted),
            maxLines = 1,
        )
        if (chevrons) Chevron(pointsRight = true)
    }
}

/** A small open chevron, drawn: the font isn't trusted to have one, and a vector stays crisp. */
@Composable
private fun Chevron(pointsRight: Boolean) {
    val color = LocalPalette.current.text
    Canvas(Modifier.size(width = Dimens.chevron / 2, height = Dimens.chevron)) {
        val tip = if (pointsRight) size.width else 0f
        val back = size.width - tip
        val stroke = Dimens.chevronStroke.toPx()
        drawLine(color, Offset(back, 0f), Offset(tip, size.height / 2), stroke, StrokeCap.Round)
        drawLine(color, Offset(tip, size.height / 2), Offset(back, size.height), stroke, StrokeCap.Round)
    }
}
