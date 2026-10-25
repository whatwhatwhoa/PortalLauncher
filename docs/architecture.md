# Architecture

How PortalLauncher works now and where it is going. Present tense, no task IDs; the why behind
each choice lives in `decisions/`.

## Goals

The owner wants the Portal's home screen to feel like a welcoming handheld, not a phone and not
a TV menu, while staying a real Android launcher: every installed app, the system settings and
notifications are a button away. The launchers tried so far fall short in two ways: Console
Launcher and the Odin launcher look off (soft text, flat shadows, little detail), and the
emulator front ends (Daijishō, ES-DE) are built around ROMs, not Android. So the bar is
**clean and crisp first, features second**: fewer things on screen, each drawn well.

Not goals: Nova-style customization (grids, icon packs, gestures), widgets, emulator ROM
scanning, phone layouts in portrait.

## Look

Direction from the owner's concepts (`decisions/M1-design.md`) and answers
(`decisions/M2-skeleton.md`). Numbers are the skeleton's values, tuned on the device; in code
they live in `Look.kt` (`Space`, `Dimens`), so this section and that file change together.
What the owner can change is in *Settings*; this section describes the defaults.

**Vector first.** Glyphs and our own icon are vectors (drawn in code or `VectorDrawable`), type
is one bundled font, app icons are drawn 1:1 (*Tiles*). A raster asset needs a reason.

**Spacing.** One scale: 4, 8, 12, 16, 24, 32, 48 dp (`Space`). Every gap and margin is a step
on it; a new size means a new token, not a literal.

**Canvas.** 1920×1080 at density 288 is **1067×600 dp** (1 dp = 1.8 px; sizes in dp so the
layout holds at another density). Landscape, edge to edge, system bars
hidden (a swipe from the edge shows them); the launcher draws its own status. Touch is a full
second input, never the only one.

**Home anatomy** (top to bottom):
```
    ┌ 12:41 NAME OF THE FOCUSED APP ───────────────────  ◥  [85]▮ ┐ status 10.5 top, 14.5 side
    │░░ hero art, full width, cropped evenly, vignetted ░░░░░░░░░░░│ NAME: display 210 sp, bold,
    │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│ 40%, caps past the top edge
    │  ┏━━━━━━━┓ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌───         │ band 272 (490 px) behind;
    │  ┃  1.2  ┃ │      │ │      │ │      │ │      │ │            │ tiles 144, focused 192,   
    │  ┗━━━━━━━┛ └──────┘ └──────┘ └──────┘ └──────┘ └───         │ center line at 52%
    │                                                              │
    │                                                   ⊕ Options  │
    │           (⋮⋮⋮) (⚙) (▣) (▶) (◎)               Ⓨ All apps  │ bottom 24
    └─────────────────────────────────────────────────────────────┘
```
1. *Status*, tighter into the corners than the content (10.5 dp from the top, 14.5 from the
   side): the clock in body type top-left; Wi-Fi and battery top-right, 12 dp apart. Drawn once
   by the root over both screens, so it stays put while All apps slides in. Wi-Fi is the modern filled wedge (a
   quarter-circle sector, apex down, 18 dp tall, every corner rounded 2 dp): the whole wedge in
   the text color at 30%, the signal filled from the apex in four steps; no Wi-Fi network
   leaves it dim. The battery is a 40×20 dp rounded body with a small cap, the charge filling
   from the left (accent while charging), the percentage inside in bold caption type, knocked
   out where it crosses the fill.
2. *Hero band*: an app with a **scene** (a 1920×1080 picture, *Art*) shows it over the whole
   of Home instead, 1:1, under a heavy alpha vignette: brightest right of center above the
   carousel, near gone at the left under the name and along the bottom under the dock and
   hints; it crossfades like the hero. Otherwise the top 272 dp of the screen, with no edge line. In it, the row's current favorite's hero at full width, cropped evenly top and
   bottom (490 of 620 px), decoded at the screen's width; it crossfades in 250 ms as focus
   moves (the old one stays while the next decodes) and stays on the row's app while the dock
   has focus. A vignette fades its alpha, so it melts into whatever backdrop is behind it:
   fully at the bottom, 85% at the left edge (under the name), 50% along the top and the
   right. No hero: the plain background. The name sits top-left, 48 dp in, Metro-style, in capitals:
   lifted so they run 12 dp past the top edge (cap height taken as 0.72 of the size), with the
   status drawn over it; display type (bold) at 40% opacity, accent while the row has focus and muted otherwise;
   one line. A name too long for the screen scrolls while the row has focus: out past the left
   edge at 90 dp/s and round again, a 2.5 s pause before each pass; muted, or with animations
   off, it runs off the right edge instead.
3. *Carousel*: the current category's apps in one row (*Categories*), named above the row on
   the content margin in muted title type, 12 dp clear of the grown tile; its center line at 52% of the screen's height
   (placed by that line, not by its neighbors) and over the band's lower part, 48 dp side margins.
   The focused tile grows from 144 to 192 dp by layout on a spring, so its neighbors move aside;
   the others are drawn at 85% opacity. The row is 192 dp tall either way, so it doesn't shift
   when focus goes to the dock. The focused tile is always in the first slot, the last one too:
   each tile slides on its own no-bounce spring along a fractional slot number, and its x and
   size come from that number in the layout phase, so the gaps stay even mid-slide (a scrolling
   LazyRow fought the resizing and jumped). Dragging the row by touch moves focus a slot per
   tile width.
4. *Bottom row*, 24 dp from the bottom, everything bottom-aligned. In the middle of the screen
   the dock: round 56 dp buttons, 24 dp apart: All apps first, then up to six apps the owner
   picks (+ → Add to dock), in their order, then a gear that opens Launcher Settings. Until the
   first change it shows the utility apps (Settings, Files, Play Store, default browser,
   whichever exist); the dock is for utilities (mail, messages), covers stay on Home. The focused button's name
   shows 12 dp above it in caption type. On the right the live button hints, stacked 4 dp
   apart and right-aligned, each a 20 dp drawn disc with the button's mark knocked out and an
   8 dp gap to its label in hint type. The mark is the letter, or for the two menu buttons the
   shape the Portal prints on them, half the disc or more and rounded: a square on − (Select, left) and a
   triangle pointing right on + (Start, right). A and B are never hinted anywhere (the owner
   takes open and back as known), so Home shows + Options (where it applies) and Y All apps;
   while a tile is moved, only a muted note. *Button hints* off hides them all. Hints are drawn at
   80% and each sits on the side of the Portal its button is on: − Quick settings stacks
   bottom-left on the content margin, the same way, and the dock keeps clear of both stacks. The stack is narrow, so the dock never moves as hints come and
   go; only a dock too wide to clear them would move left.

**Categories.** Home shows one category at a time: Favorites (the owner's list, in their
order), Games, Entertainment, Social, Utilities (the rest), each but Favorites alphabetical;
an empty one doesn't show. An app starts where its own manifest puts it (game → Games, audio and
video → Entertainment, social → Social, anything else or nothing → Utilities); + → *Category…*
moves it, stored as a move only, so moving it back forgets it. An app can be a favorite and in
a category at once. **B on Home opens the categories layer**: the names stacked on the content
margin, vertically centered, in heading type, the focused one in the text color and the rest
muted. Behind it Home zooms out: its art and row at 18% opacity, drawn at 0.94, showing the
focused category's row from its start, so it previews where A goes; the name, the dock and the
hints fade out. The layer slides in 1/16 of the screen from the left and fades. A goes in, B goes
back; a tap on a name goes in, a tap elsewhere goes back.

**All apps.** A full-screen layer that slides up over Home: "All apps" and the count top-left,
below the status band (as on Home), then an 8-column grid of 96 dp tiles (12 dp corners, 24 dp gaps), each
with its name 8 dp below in caption type; a favorite has an accent dot on its corner. The
focused row is kept second from the top. At the bottom, a 56 dp hint bar: a hairline, then the
hints: − Quick settings on the left, X (Favorite / Unfavorite) and + right-aligned (Home's bottom row has no hairline). A
label that changes in place keeps the room of its longest form, so its glyph holds still. Opens with Y, the dock's first button, or a swipe up.
Hidden apps are left out (the count says how many); *Show hidden apps* in the options sheet
brings them back faded to 40% until it is turned off or the launcher restarts.

**Options sheet.** + (or a long press) on a carousel tile, a dock app or an All apps tile opens a
360 dp panel from the right over the screen dimmed to 60%: the app's icon and name, then its
options as 48 dp rows (the focused row filled with the rule color and ringed); no hints (A and B
are known). Options depend on where it was opened: Move (favorites on the Favorites row, and
the dock), Favorite or Unfavorite (not from the dock), Add to or Remove from dock, Category…
(a page of the four categories, the app's own dotted; A moves it and closes, B goes back), Hide
or Unhide (not from the dock), Art…,
App info, Uninstall (not for system apps; the system asks to confirm), Show or Hide hidden apps (All
apps only), and last, always, *Launcher settings…* (*Settings*). Hiding an app also
takes it off the row and the dock. *Move* picks the tile up: it
grows to 1.14 with a steady ring, left and right shift it, A or B puts it down.

**Tiles.** Square, 144 dp (192 focused), 16 dp gap, 16 dp corners. An app's adaptive icon fills the tile
edge to edge inside our own mask; a legacy icon sits inset 16% on the surface color. Icons are
fetched at xxxhdpi and drawn at the shown size, so they are drawn down a little, never up, on
this panel (`decisions/M2-skeleton.md` *Icons*). On Home, an app with art shows its square
cover instead, cropped to the tile's mask and decoded at the lifted size (1.14×), so focus never
upscales it. The dock and All apps keep the icons.

**Art.** Square covers and 1920×620 heroes from SteamGridDB, downloaded once per app and never
upscaled (`decisions/M4-art.md`). The owner's favorites and the Games category are
looked up by label when they appear and take art only on a close match; any other app keeps its
icon until the owner picks. *Art…* in the options sheet turns the panel into a picker: the cover
and the hero strip in use at the top (96 dp cover), then the matches for the label, *Search*
(the keyboard; B ends typing) and *Use the app's icon*; the one in use has an accent dot. A
match is downloaded when chosen and the page stays open; B goes back to the options. Once a
game is matched, *Choose the cover…* and *Choose the hero…* head the list and open its still
images as a thumbnail grid (covers three across, heroes one, "2 of 3" above); A downloads the
focused one into the slot and the grid stays open, B goes back to the rows. The dot marks the
owner's pick, else the image the automatic choice took; picks are kept per slot by url (`picks`
preferences) until another game or the icon is chosen. With art,
the sheet's header and the Home tiles show the cover instead of the icon, and the hero fills
Home's band. A matched game also gets a **scene** (`<package>.scene` beside the cover and hero):
one of its Steam store screenshots, found by the SteamGridDB name in Steam's store search (no
key), only 16:9 shots at least 1920 wide (the size is in the file name). The shot is picked from
the 600 px copies sampled at 64×36: mean saturation less twice the distance of mean luma from 73
(0–255), so rich and mid-dark, and text reads on it. Looked for once; "not on Steam" is
remembered (`scenes` preferences), a network failure retries next start. *Use the app's icon*
deletes it; a match with another game replaces it; the same game keeps it. *Choose the scene…*
lists the game's qualifying Steam shots (two across) and a pick replaces the scored one; the
scored shot's url is kept (`<package>.url` in `scenes`) so the grid can dot it.

**Own images.** *Cover from the gallery…* and *Scene from the gallery…* open Android's photo
picker (no storage permission; only the chosen image is shared) for any app, matched or not.
The image is cropped to its largest centered square or 16:9, scaled down to 512 px or 1920×1080
and saved as the app's cover or scene (JPEG 92). A crop smaller than the slot is shown (346 px,
the focused tile; 1920 wide for a scene) is refused with its size, never upscaled. An app with
no game keeps these as its own (`o` in the `art` preferences): no lookup, shown like art.

**Focus.** The focused tile or button scales to 1.08 on a spring (Home's carousel tile grows
to 192 dp by layout instead), with a 3 dp accent ring 3 dp outside its edge whose alpha breathes
between 100 and 55% over 1.8 s each way (the Switch's tell, at half its first speed; *Focus
pulse* makes it 0.9 s or still). A carousel tile picked up to move lifts a further 1.14/1.08 by
scale, its ring steady.

**Type.** One bundled font, never the system default: **Selawik**, the owner's pick, standing
in for Segoe UI, which can't ship. Its files are static and Medium uses Regular; it reads small,
so a factor of 1.15 multiplies every size and line height. Six sizes only: 210/252 display
(bold), 48/56 heading (bold, the categories layer), 24/32 title, 18/24 body, 14/20 hint (the button hints, kept apart from caption so they
can change alone), 14/20 caption (size/line height, sp).
The system font scale is honored within 0.9–1.15, so large text can't break the layout; layout
that depends on a line height reads it through the font scale (`asDp`).

**Color.** Two themes following the system dark/light setting (or fixed by *Theme*): light
background `#EBEBEB` with `#2D2D2D` text, dark `#141414` with white text (the Switch's two, dark
made much darker).

**Backdrop**: *Black* (`#000000`, the whole palette's background, the OLED's off; the owner's
pick). The others: *Dark* (`#141414`), *Hero glow* (the focused app's hero over the whole of
Home, decoded at 480 px, blurred 40 dp and dimmed 50% with the background, crossfading with
focus), and each image in the app's `files/backdrops`, cropped full screen and dimmed 35%. All
apps keeps the plain background. Surface is the background mixed 8% toward the text; muted text
is the text at 60%; the rule at 16%. The **accent** is *White*, the text color (so `#2D2D2D`
in light mode; the owner's pick). The others are a hue and chroma made into OKLCH color at
lightness 0.80 (dark) or 0.55 (light), fitted to sRGB: Ice blue 235° 0.08, Mint 165° 0.11,
Amber 75° 0.14, Coral 28° 0.15, Lavender 295° 0.12.

**Overlay effects.** Three, each switchable in *Settings*, all on by default (the owner kept
all three as they are):
- *Background gradient*: a radial spot centered 62% across and 45% down (where scenes are
  brightest), radius 75% of the width, fading to the background. Its color (`lift`) is the
  accent at 12% over the background in the dark, near white (tinted 6% by a hued accent) in the
  light. Dithered. A scene, Hero glow or a wallpaper covers it.
- *Focus glow*: the accent at 45%, blurred 16 dp outside the ring only, breathing with it.
- *Tile depth*: in the dark a 1 dp highlight along the top edge (white 22%, gone by the middle);
  in the light a soft shadow (black 22%, blur 8 dp, 4 dp down). Carousel tiles, dock buttons,
  All apps tiles.

**Settings.** One panel, *Launcher Settings*, from + → *Launcher settings…*, the dock's last
button (a gear), or the gear beside Portal in Android's Default apps → Home app: the options sheet's 360 dp column at the right,
over the screen *undimmed* so a change is judged live against Home. The title, then each group
under a muted caption heading, its rows 48 dp: the name left, the value right (the accent with
a swatch); the focused row is filled and ringed. Two kinds of row: a *value* row shows its
value between two drawn chevrons and left and right change it (wrapping), A steps it on; an
*action* row has no chevrons and A does its one thing, a second A first for one that can't be
undone (the value reads "A again to confirm"; any other key cancels). Up and down move (the
list scrolls to keep the focused row, its ring and its heading in view, and the credits under
the last row), B or + closes, a tap outside closes, a tap on a row acts as A. Every change
applies at once and is kept (`look.*`, `home.*`, `art.fetch` in the `launcher` preferences).
- *Look*: Theme (System, Light, Dark), Accent, Backdrop, Scenes (off: the hero band instead),
  Covers on tiles (off: icons on Home, whatever art is picked), Background gradient, Focus
  glow, Tile depth, Focus pulse (Off, Slow, Normal).
- *Home*: Clock (System, 12-hour, 24-hour), Battery percent (off: only the fill), Button hints,
  Carousel wraps (off by default: the row stops at its ends), Home key goes to (Favorites, the
  first category's start; or Where you left, which also reopens the last category after a restart).
- *Art* (only with a SteamGridDB key): Fetch art (off: no lookups without being asked; Art…
  still searches), Clear all art (its size shown; confirmed; every file, match, scene and
  pick goes, and with Fetch art on it is all looked up afresh).
- *Apps*: Hidden apps (the count; A opens All apps with them shown), Reset the dock (confirmed;
  back to the utility apps; "Default" when it already is).
- *System*: Home app (Portal or Not set; A opens Android's Default apps), Android settings.
- Under the list, muted: the version, the font and the icons and their licenses, where the art
  comes from.

**Quick panel.** − (the square, Select) on Home or All apps, the pull-down's everyday controls
from the controller (`decisions/M7-quick-settings.md`). A 760 dp panel drops from the top
(a quarter of its height, fading) over the screen dimmed to 60%, its bottom corners rounded:
- *Brightness* and *Volume* sliders, 48 dp rows like the settings rows: a sun or speaker icon, a 6 dp
  track filled with the accent, the value in percent. Left and right step them (5% of the
  slider for brightness, one step of media volume), held repeats; a tap on the track sets it
  there. Brightness goes through a 2.2 curve over Android's 0–255 value, so each step looks
  alike; moving it turns auto-brightness off, as the pull-down does.
- Toggles, round 56 dp icon buttons (the dock's size, 16 dp apart) filled in the text color
  when on: Auto brightness, Do not disturb (priority only while on), Play / pause (the media
  key to the last player; on while music plays, read again 0.4 s after; shows pause then).
- Shortcuts to the system's pages, the same buttons, for what a launcher may not change: Wi-Fi,
  Bluetooth, Battery, AYN settings (a gamepad; its settings activity, which has no launcher
  entry), Android settings (a gear).
- A muted line at the bottom says what the focused control still needs, or else names the
  focused button.
The icons are Material Symbols (Rounded, filled; Google, Apache 2.0) as vector drawables
(`res/drawable/ic_sym_*`), the family Android's own panel uses, tinted from the palette.
Up and down move between the four rows, left and right along a row, A toggles or opens, B or −
closes, a tap outside closes. **Grants:** brightness and auto-brightness need Android's *Modify
system settings* for the launcher, Do not disturb its *Do Not Disturb access*; until granted the
slider row reads "A: let the launcher change it" and A (on the row or the toggle) opens that
page. Nothing is asked for on install. Values are read when it opens, after every change and
when the launcher comes back to the front. Home and All apps only, not over a game.

**Motion.** Short and springy, never decorative: focus scale on a spring, the row and grid
scroll animated to the focused item, All apps slides 1/8 of the screen and fades. Opening an
app reveals its window out of the tile (a clip that grows from the tile's rect to the screen).
With the system's animations off (animator duration scale 0, from Developer options or
Accessibility → Remove animations) everything snaps: no spring, no slide or fade, no scroll
animation, no ring pulse, no launch reveal. The setting is watched, so it applies without a
restart.

**Controller map.** Keys become actions once, at the activity (`Input.kt`), so an ABXY swap is
one table. A is the bottom button (Xbox layout, `BUTTON_A`). Home: D-pad or left stick moves
along the row, down to the dock and back up; A opens; Y opens All apps; L1/R1 jump five tiles;
L2/R2 jump to the ends; + opens the options sheet; B opens the categories layer (up and down,
L2/R2 to the ends, A in, B back); − opens the quick panel; X does nothing. Carousel wraps on,
left and right go round the row's ends. All apps: D-pad
moves in the grid; A opens; X toggles favorite; + opens options; − the quick panel; B or Y
closes; L1/R1 jump to the previous or next letter (anything not starting with a letter is #).
The Home key closes every layer; pressed while already home it also returns to the first
category's start (unless *Home key goes to* is Where you left), while on
the way back from an app it keeps the category and focus (the window has focus and the intent
doesn't bring Home to the front, Launcher3's test). Touch: tap opens, long press opens options, drag
scrolls, swipe up opens All apps, back closes the top layer.

**Scaling (bonus, not a goal).** The layout is drawn on a 600 dp tall canvas at least 1032 dp
wide (the All apps grid). On another screen the root scales the density so the canvas fits:
the smaller of height / 600 and width / 1032, rounded to hundredths, clamped to 0.75–1.5. Every
size scales with it, literals included. The Portal's window is the canvas, so its factor is
exactly 1 and icons stay 1:1. Portrait and phones are unsupported.

## Structure

One activity and two Compose screens, in `app/src/main/kotlin/io/github/whatwhatwhoa/portallauncher/`.

- **Launcher contract.** `MainActivity` has the HOME + DEFAULT intent filter (and LAUNCHER, so
  it can be tried from Launcher3), `singleTask`, `excludeFromRecents`, `stateNotNeeded`. On its
  first start it asks once for the Home role through `RoleManager`; the owner answers in the
  system dialog and can pick Launcher3 back the same way.
- **App catalog** (`AppCatalog.kt`). `LauncherApps` lists the launchable activities of every
  profile on a background thread and pushes add/remove/change callbacks, so the screens update
  without a restart. An `AppEntry` is key (component + user serial), label, component, user and
  icon. It also picks the dock's utility apps and launches through `startMainActivity`.
- **Input layer** (`Input.kt`). `dispatchKeyEvent` turns gamepad and keyboard keys into
  `Action`s and swallows both edges of a mapped key; only movement repeats when held. The stick
  and hat arrive as D-pad keys (Android synthesizes them from unhandled joystick motion).
- **State** (`LauncherState.kt`). Screen (Home, All apps), zone (row, dock) and three focus
  indices, all our own state rather than Compose focus: a lazy row can't focus tiles that
  aren't composed, and a console row pivots instead of moving to the nearest. Indices are
  clamped on read, so lists can change under them. The settings panel, the quick panel, the
  options sheet and move mode are layers on top that take input first, in that order, then the
  categories layer. Favorites, hidden apps, the dock, category moves, the last category and the
  settings persist in `SharedPreferences` (`Prefs`). Android's own screens open through one
  hook from `MainActivity`, which shows a toast if one is missing. The row is derived from the category
  on show, which is the layer's focused one while it is open.
- **Screens.** `HomeScreen.kt` (carousel, dock, hint bar), `AllAppsScreen.kt` (grid),
  `OptionsSheet.kt` (the + panel and its Category… page), `Categories.kt` (the categories, their
  seeding from the manifest, the layer), `ArtSheet.kt` (its Art… page and the picker's state),
  `Settings.kt` (the panel, its rows and how each steps), `QuickSettings.kt` (the quick panel
  and `DeviceControls`, which reads and writes brightness, volume, Do not disturb and the media key),
  `Status.kt` (clock, Wi-Fi, battery: the sticky battery broadcast and a Wi-Fi network callback);
  shared drawing in `Chrome.kt` (icon renderer, focus ring and glow, depth, hint bar, clock, All
  apps glyph); tokens, fonts, palette and the settings' types in `Look.kt`. The root
  (`MainActivity`) provides the scaled density, the palette (from *Theme*, *Accent* and the
  backdrop), the type, `LocalMotion` (animations on or off, from a watch on the animator
  duration scale), `LocalEffects`, `LocalPulse` and `LocalHome` (the *Home* settings) to
  everything below it.
- **Art** (`Art.kt`). Lookups, downloads and records (one per package, in their own
  `SharedPreferences`; images in `files/art/`), plain `HttpURLConnection` and `org.json` on the
  IO dispatcher; `rememberArt` decodes a file scaled down to the size it is drawn. The
  SteamGridDB key comes from `local.properties` through `BuildConfig`; without it there is no
  Art option.
- **Launches.** Every tile reports where it was laid out (`launchesFrom`), keyed by where it sits
  (row, dock, All apps) and the app; opening an app passes that rect to `startMainActivity` as
  the source bounds and as a clip-reveal `ActivityOptions`.
- **Build.** Gradle wrapper, one module, Compose foundation and ui only (no Material: every
  surface is ours), no Hilt, no navigation library.

Planned: the motion pass (`TASKS.md` T6.3).

## Toolchain

- **Language and UI:** Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00), activity-compose 1.13.0.
- **Build:** Gradle 9.8.2 (wrapper), Android Gradle Plugin 9.4.1, which compiles Kotlin itself;
  `compileSdk`/`targetSdk` 37, `minSdk` 31. Application id
  `io.github.whatwhatwhoa.portallauncher`.
- **SDK:** `C:\Users\Jim\AppData\Local\Android\Sdk` (`ANDROID_HOME` set for the user):
  platform `android-37.0` (Android 17), build-tools 37.0.0, platform-tools 37.0.1,
  cmdline-tools 23.0. Packages are managed with `cmdline-tools\latest\bin\android.exe sdk`.
- **Java:** Temurin JDK 21 for Windows ARM64 (native), unpacked at
  `C:\Users\Jim\AppData\Local\Android\jdk-21`; `JAVA_HOME` must point at it for `gradlew`.
- **Host:** Windows 11 on ARM (Surface Laptop 7). Google ships only x64 Windows builds of the
  SDK tools; they run under Windows' x64 emulation. There is no Android emulator on this host:
  the Portal is the test device, over USB adb. Android Studio is not installed and not needed.
- **Font:** Selawik's static Regular, Semibold and Bold from Microsoft's GitHub release 1.01, in
  `res/font/`; its OFL license in `licenses/`.

## The device

AYN Odin 2 Portal (`odin2portal`), Snapdragon 8 Gen 2, 7" OLED 1920×1080, running LineageOS on
Android 17 (build `CP2A.260605.016`) with AYN's settings app (`org.lineageos.settings.ayn`);
stock Launcher3 is the current Home and the fallback.

- **Natural orientation is landscape** (rotation 0 = 1920×1080); the panel itself is mounted
  portrait (`ro.surface_flinger.primary_display_orientation=ORIENTATION_90`).
- **Density 288** (physical 360; LineageOS default 314), so the smallest width is 600 dp and
  apps and the system use their tablet layouts. Undo: `adb shell wm density 314`.
- **Rotation is locked to landscape** (`accelerometer_rotation=0`, `user_rotation=0`), rotation
  suggestions are off, and fixed-to-user-rotation is back to `default` (it was `enabled`). The
  accelerometer reports gravity 90° off, so the sensor can't drive rotation; portrait comes from
  apps that ask for it or from a rotation app's tile. See `gotchas.md` *Rotation*.
