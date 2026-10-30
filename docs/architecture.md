# Architecture

How PortalLauncher works now and where it is going. Present tense, no task IDs; the why behind
each choice lives in `decisions/`. The build and the Portal itself: `device.md`.

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
(`decisions/M2-skeleton.md`). In code the numbers live in `Look.kt`
(`Space`, `Dimens`), so this section and that file change together.
What the owner can change is in *Settings* (`behavior.md`); this section describes the defaults.
What each layer and control does (categories, All apps, the sheets, settings, sounds, the quick
panel, the controller map, standby): `behavior.md`.

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
   side): the clock in body type top-left (on Android's minute tick, read again on a time,
   zone or 12/24-hour change and as the screen comes on); Wi-Fi and battery top-right, 12 dp apart. Drawn once
   by the root over both screens, so it stays put while All apps slides in. Wi-Fi is the modern filled wedge (a
   quarter-circle sector, apex down, 18 dp tall, every corner rounded 2 dp): the whole wedge in
   the text color at 30%, the signal filled from the apex in four steps; no Wi-Fi network
   leaves it dim. The battery is a 40×20 dp rounded body with a small cap, the charge filling
   from the left (accent while charging), the percentage inside in bold caption type, knocked
   out where it crosses the fill. While music plays (notification access, as the seek bar), a
   24 dp bar and title · artist · album at 70% follow the clock, up to the screen's middle;
   a long one scrolls out under the bar (36 dp/s, 2.5 s pause), its last 48 dp faded. Not while
   the quick panel or Launcher Settings is open.
2. *Hero band*: the top 272 dp of the screen, with no edge line. In it, the row's focused app's
   hero at full width, cropped evenly top and bottom (490 of 620 px), decoded at the screen's
   width; it crossfades in 250 ms as focus moves (the old one stays while the next decodes) and
   stays on the row's app while the dock has focus. A vignette fades its alpha, so it melts into
   whatever backdrop is behind it: fully at the bottom, 85% at the left edge (under the name),
   50% along the top and the right. Under it lies the hero's **reflection** (an effect): the same
   crop flipped and set 32 dp under the band, so hero and reflection read as two blocks, its
   top faded in over its first 8%; blurred 3 dp across and 10 dp down, darkened to about 40% and cooled toward blue (light themes:
   only desaturated), at 55% fading out by 400 dp, behind the carousel. It sways sideways in a
   slow 9 s ripple that grows away from the line (an Android 13 runtime shader; still with
   animations off). No hero: the plain background. An app set to its **scene** (a
   1920×1080 picture, *Art*; each app shows its hero or its scene, never both) shows that over
   the whole of Home instead, with no reflection, 1:1, under a heavy alpha
   vignette: brightest right of center above the carousel, near gone at the left under the name
   and along the bottom under the dock and hints; it crossfades like the hero.
   The name sits top-left, 48 dp in, Metro-style, in capitals lifted so they run 12 dp past the
   top edge (cap height taken as 0.72 of the size), with the status drawn over it; display type
   (bold) at 40% opacity in the accent; one line. A name
   too long for the screen scrolls while the row has focus: out past the left edge at 90 dp/s
   and round again, a 2.5 s pause before each pass; muted, or with animations off, it runs off
   the right edge instead. *Big name* Fitted draws it as written instead, semibold at 60%,
   48 dp from the top between the margins, at 36% of the display size shrunk with the name's
   width to 20% at least; longer still, it scrolls the same way or ends in an ellipsis.
3. *Carousel*: the current category's apps in one row (`behavior.md` *Categories*), named above
   the row on the content margin in muted title type, 12 dp clear of the grown tile, between the
   drawn L1 and R1 bumpers at the name's 60% (with *Button hints* on and play controls off; none
   before the first category or after the last); its center line at 52%
   of the screen's height (placed by that line, not by its neighbors) and over the band's lower
   part, 48 dp side margins.
   The focused tile grows from 144 to 192 dp by layout on a spring, so its neighbors move aside;
   the others are drawn at 85% opacity. The row is 192 dp tall either way, so it doesn't shift
   when focus goes to the dock; there Home dims as under a sheet, all but the bottom row; on the row the
   dock dims as much. The focused tile is always in the first slot, the last one too:
   each tile slides on its own no-bounce spring along a fractional slot number, and its x and
   size come from that number in the layout phase, so the gaps stay even mid-slide. Dragging
   the row by touch moves focus a slot per tile width. Each category's row has tiles of its own:
   on a turn the new row glides in from one slot aside, from the right for a later category
   (R1, down in the layer) and the left for an earlier one, and the name crossfades.
4. *Bottom row*, 24 dp from the bottom, everything bottom-aligned. In the middle of the screen
   the dock: round 56 dp buttons, 24 dp apart: All apps first, then up to six apps the owner
   picks (Triangle → Add to dock), in their order, then Launcher Settings: the launcher's own icon with
   a small gear on a background-colored disc over its lower right, so it isn't one more plain
   gear beside Android's Settings. Until the
   first change it shows the utility apps (Settings, Controller settings, Files, Play Store, default
   browser, whichever exist); the dock is for utilities (mail, messages), covers stay on Home. The focused button's name
   shows 12 dp above it in caption type. On the right the live button hints, stacked 4 dp
   apart and right-aligned, each a 20 dp drawn disc with the button's mark knocked out and an
   8 dp gap to its label in hint type. The mark is the letter, or for the two menu buttons the
   shape the Portal prints on them, half the disc or more and rounded: Square (Select, left) and
   Triangle (Start, right), pointing right. A and B are never hinted anywhere (the owner
   takes open and back as known), so Home shows Triangle Options (where it applies) and Y All apps;
   while a tile is moved, only a muted note. *Button hints* off hides them all. Hints are drawn at
   80% and each sits on the side of the Portal its button is on: Square Quick settings stacks
   bottom-left on the content margin, the same way, and the dock keeps clear of both stacks. The stack is narrow, so the dock never moves as hints come and
   go; only a dock too wide to clear them would move left.

**Tiles.** Square, 144 dp (192 focused), 16 dp gap, 16 dp corners. An app's adaptive icon fills the tile
edge to edge inside our own mask; a legacy icon sits inset 16% on the surface color. Icons are
fetched at xxxhdpi and drawn at the shown size, so they are drawn down a little, never up, on
this panel (`decisions/M2-skeleton.md` *Icons*). On Home, an app with art shows its square
cover instead, cropped to the tile's mask and decoded at the lifted size (1.14×), so focus never
upscales it. The dock and All apps keep the icons.

**Art.** Square covers and 1920×620 heroes from SteamGridDB, downloaded once per app and never
upscaled (`decisions/M4-art.md`). The owner's favorites, Recent's apps and the Games category
are looked up by label when they appear (Android's own apps, by package, only in Games) and take
art only on a close match; any other app keeps its
icon until the owner picks. Only a lookup that finds no close match, or a match with no images,
records "no art"; a download that fails (offline, a timeout, an HTTP error) records nothing and
runs again when the network comes back (3 s after it settles) or at the next start (the apps
still owed an image are kept in `art.pending`, outside the setup); each image comes on its own,
and a picked one SteamGridDB no longer has (404) gives way to the automatic one. Art… says
which: "No art for <game>" or "Couldn't reach SteamGridDB". *Art…* in the options sheet turns the panel into a picker: a
preview of the art in use at the top (the 120 dp cover, beside it as tall the strip: the hero or
the scene, whichever fills Home behind the app), then the matches for the label, *Search* (the keyboard; B ends
typing) and *Use the app's icon*; the one in use has an accent dot. A match is downloaded when
chosen and the page stays open; B goes back to the options. Up from the first row selects the
preview itself, so art is changed where it is seen: left and right between the cover and the
strip, down back to the rows. A opens that slot as a thumbnail grid (covers four across, scenes
three, heroes two, "2 of 3" above), the cover and scene grids led by a *Gallery…* tile (*Own
images*); the focused thumbnail stands in the preview, whose ring holds still while the grid's
pulses. A downloads it into the slot and the grid stays open; B goes back to the preview. The
strip's grid has two tabs, *Hero* and *Scene*, drawn between the L1 and R1 bumper glyphs (hidden
with *Button hints* off): the one browsed underlined in the accent, a dot on the one Home shows.
It opens on that one; L1/R1 or a tap turn the tab. Taking an image in a tab makes that kind the
app's (`<package>.wide` in `picks`; without one, the hero, or the scene when there is no hero);
A on the other tab's dotted image only switches (or fetches it again if its file is gone); the icon or another game drops the choice. A
tap on the preview opens its grid. The dot marks the owner's pick, else the automatic one;
picks are kept per slot by url (`picks`) until another game or the icon is chosen. With art,
the sheet's header and the Home tiles show the cover instead of the icon, and the hero fills
Home's band. A matched game also gets a **scene** (`<package>.scene` beside the cover and hero,
shown only once chosen):
one of its Steam store screenshots, found by the SteamGridDB name in Steam's store search (no
key), only 16:9 shots at least 1920 wide (the size is in the file name). The shot is picked from
the 600 px copies sampled at 64×36: mean saturation less twice the distance of mean luma from 73
(0–255), so rich and mid-dark, and text reads on it. Looked for once; "not on Steam" is
remembered (`scenes` preferences), a network failure retries as a download does. *Use the app's icon*
deletes it; a match with another game replaces it; the same game keeps it. The scene grid
lists the game's qualifying Steam shots and a pick replaces the scored one; the scored shot's
url is kept (`<package>.url` in `scenes`) so the grid can dot it.

**Own images.** The *Gallery…* tile at the head of the cover and scene grids opens Android's
photo picker (no storage permission; only the chosen image is shared) for any app, matched or
not; an app with no game gets a grid with that tile alone.
The image is cropped to its largest centered square or 16:9, scaled down to 512 px or 1920×1080
and saved as the app's cover or scene (JPEG 92). A crop smaller than the slot is shown (346 px,
the focused tile; 1920 wide for a scene) is refused with its size, never upscaled. An app with
no game keeps these as its own (`o` in the `art` preferences): no lookup, shown like art.

**Focus.** The focused tile or button scales to 1.08 on a spring (Home's carousel tile grows
to 192 dp by layout instead), with a 3 dp accent ring 3 dp outside its edge whose alpha breathes
between 100 and 55% over 1.8 s each way (the Switch's tell; *Focus pulse* makes it 0.9 s or
still). A carousel tile picked up to move lifts a further 1.14/1.08 by
scale, its ring steady.

**Type.** One bundled font, never the system default: **Selawik**, the owner's pick, standing
in for Segoe UI, which can't ship. Its files are static and Medium uses Regular; it reads small,
so a factor of 1.15 multiplies every size and line height. Six sizes only: 210/252 display
(bold), 48/56 heading (bold, the categories layer), 24/32 title, 18/24 body, 14/20 hint (the button hints, kept apart from caption so they
can change alone), 14/20 caption (size/line height, sp).
The system font scale is honored within 0.9–1.15, so large text can't break the layout; layout
that depends on a line height reads it through the font scale (`asDp`).

**Color.** Two themes following the system dark/light setting (or fixed by *Theme*): light
background `#EBEBEB` with `#2D2D2D` text, dark `#141414` with white text.

**Backdrop**: *Black* (`#000000`, the whole palette's background, the OLED's off; the owner's
pick). The others: *Dark* (`#141414`), *Hero glow* (the focused app's hero over the whole of
Home, none under an app set to its scene, decoded at 480 px, blurred 40 dp and dimmed 50% with the background, crossfading with
focus), and each image in the app's `files/backdrops` (read again each time the settings
open), cropped full screen and dimmed 35%. All
apps keeps the plain background. Surface is the background mixed 8% toward the text; the menus (options, Launcher Settings,
quick panel) sit on *panel*, `#242424` in the dark, the surface in the light; muted text
is the text at 60%; the rule at 16%. The **accent** is one of eight presets or a custom color. *White* is the text color (so
`#2D2D2D` in light mode; the owner's pick and the default). The other seven are a hue and chroma
made into OKLCH color at lightness 0.80 (dark) or 0.55 (light), fitted to sRGB, in hue order:
Coral 28° 0.15, Amber 75° 0.14, Lime 125° 0.13, Mint 165° 0.11, Ice blue 235° 0.08, Lavender
295° 0.12, Rose 350° 0.12. A custom color is a hue (whole degrees) and a chroma (0.02–0.18, in
hundredths) made the same way, so it keeps the presets' contrast on either background; one
stepped back onto a preset's numbers is that preset. Stored in `look.accent` as the preset's
name or `custom:<hue>:<chroma>`.

**Overlay effects.** Three, each switchable in *Settings*, all on by default (the owner kept
all three as they are):
- *Background gradient*: a radial spot centered 62% across and 45% down (where scenes are
  brightest), radius 75% of the width, fading to the background. Its color (`lift`) is the
  accent at 12% over the background in the dark, near white (tinted 6% by a hued accent) in the
  light. Dithered. A scene, Hero glow or a wallpaper covers it.
- *Focus glow*: the accent at 45%, blurred 16 dp outside the ring only, breathing with it.
- *Tile depth*: in the dark a 1 dp highlight along the top edge (white 22%, gone by the middle);
  in the light a soft shadow (black 22%, blur 8 dp, 4 dp down), outside the tile and its fade
  layer. Carousel tiles, dock buttons,
  All apps tiles.

**Motion.** Short and springy, never decorative: focus scale on a spring, the row and grid
scroll animated to the focused item (a held direction glides a grid at an even speed instead of
stepping it). Everything that comes and goes (All apps, the categories
layer, the options sheet, the quick panel, the settings) uses one timing, `Transit`: in over
240 ms decelerating, out over 180 ms accelerating (then stretched by Android's duration scale;
the Portal's is 0.5). All apps rises 1/8 of the screen and fades in, sinks 1/16 and fades out,
and Home behind it steps back to 96% and returns. The focus ring's pulse eases in and out (a
sine), lingering at full and at faint. Opening an
app reveals its window out of the tile (a clip that grows from the tile's rect to the screen).
With the system's animations off (animator duration scale 0, from Developer options or
Accessibility → Remove animations) everything snaps: no spring, no slide or fade, no scroll
animation, no ring pulse, no launch reveal. The setting is watched, so it applies without a
restart.

**Scaling (bonus, not a goal).** The canvas is 600 dp tall and at least 1032 dp wide (the All
apps grid). The root scales the density to fit it: the smaller of height / 600 and width / 1032,
rounded to hundredths, clamped to 0.5–1.5, literals included. On the Portal the factor is exactly
1, icons 1:1, at any *Display size*; a change applies live (pixels over the current density).
Portrait (auto-rotate, a rotation app) is letterboxed: Home in a box of the screen's shape turned
sideways, full width, centered, scaled as on a landscape screen. Phones are unsupported.

## Structure

Two activities (Home, standby) and Compose screens, in `app/src/main/kotlin/io/github/whatwhatwhoa/portallauncher/`.

- **Launcher contract.** `MainActivity` has the HOME + DEFAULT intent filter (and LAUNCHER, so
  it can be tried from Launcher3), `singleTask`, `excludeFromRecents`, `stateNotNeeded`. On its
  first start it asks once for the Home role through `RoleManager`; the owner answers in the
  system dialog and can pick Launcher3 back the same way. There is one launcher: Android makes an
  activity Home only from its Home intent with no component, so any other start while Portal is
  Home (its icon in another launcher, `am start -n`, the settings gear) hands over to Home through
  that intent (`EXTRA_OPEN`: settings or home) and finishes before anything is built; a copy
  opened before Portal became Home ends once Home is built, or hands over when it comes back. A cold start
  shows the backdrop's own color, then the first frame once the app list is in (at most 1 s).
  Font size, bold text and locale changes don't rebuild it (`configChanges`).
  `StandbyActivity` is standby: its own task (`taskAffinity ""`, `singleInstance`), out of
  Recents and history, never over the lock screen; `HomeHoldService` starts it and routes Home
  presses to it while it shows (`StandbyActivity.current`).
- **App catalog** (`AppCatalog.kt`). `LauncherApps` lists the launchable activities of every
  profile on a background thread and pushes add/remove/change callbacks, so the screens update
  without a restart. An `AppEntry` is keyed by component + user serial. `SCREENS` adds settings pages with no launcher icon (AYN's, as *Controller settings*),
  opened by Settings' action, in All apps only, each with a name and icon of ours (AYN's: the
  Portal glyph on its teal, `ic_app_controller_settings`), and *Notifications* (`SHADE`: a
  launch pulls the shade down, under System UI's package). It also picks the
  dock's utility apps and launches through `startMainActivity`, saying whether the start
  succeeded. A package event waits 300 ms for the rest of its burst (Play's Update all) before
  one query; a newer event stops the running one; an app, profile or page that throws while
  read is left out and logged, never fatal to Home.
- **Input layer** (`Input.kt`). `dispatchKeyEvent` turns gamepad and keyboard keys into
  `Action`s and swallows both edges of a mapped key; only movement repeats when held, and the
  state is told which press is a repeat (a held bumper or settings value acts once). Not while
  the activity isn't resumed: then a mapped key or a touch begun there does nothing. The hat
  arrives as D-pad keys (Android synthesizes them from unhandled joystick motion); the left
  stick is read by `Stick.kt` only while the hat rests and Android holds no stick push of its
  own, and turned into D-pad steps that pass every gate a key does.
- **State** (`LauncherState.kt`). Screen (Home, All apps), zone (row, dock) and three focus
  indices, all our own state rather than Compose focus: a lazy row can't focus tiles that
  aren't composed, and a console row pivots instead of moving to the nearest. Indices are
  clamped on read, so lists can change under them. The settings panel, the quick panel, the
  options sheet and move mode are layers on top that take input first, in that order, then the
  categories layer. Favorites, hidden apps, the dock, category moves, the last category and the
  settings persist in `SharedPreferences` (`Prefs`), each read through a getter that falls
  back to the default on a value of the wrong type; `Prefs.fits` holds every key's type for
  Restore setup. Android's own screens open through one
  hook from `MainActivity`, which shows a toast if one is missing or refused. Home's place at a
  launch (`HomePlace`, with the boot's instant) reopens Home in a launcher restarted in the same
  boot. The row is derived from the category
  on show, which is the layer's focused one while it is open.
- **Screens.** `HomeScreen.kt` (carousel, dock, hint bar), `AllAppsScreen.kt` (grid),
  `OptionsSheet.kt` (the options panel and its Category… page), `Categories.kt` (the categories, their
  seeding from the manifest, the layer), `ArtSheet.kt` (its Art… page and the picker's state),
  `Settings.kt` (the panel, its rows and how each steps), `QuickSettings.kt` (the quick panel
  and `DeviceControls`, which reads and writes brightness, volume, Do not disturb and the media key),
  `Status.kt` (clock, Wi-Fi, battery: the sticky battery broadcast and a Wi-Fi network callback),
  `StandbyScreen.kt` (standby's screen and wake count, in `StandbyActivity`);
  shared drawing in `Chrome.kt` (icon renderer, focus ring and glow, depth, hint bar, clock, All
  apps glyph); tokens, fonts, palette and the settings' types in `Look.kt`. The root
  (`MainActivity`) provides the scaled density, the palette (from *Theme*, *Accent* and the
  backdrop), the type, `LocalMotion` (animations on or off, from a watch on the animator
  duration scale), `LocalEffects`, `LocalPulse` and `LocalHome` (the *Home* settings) to
  everything below it.
- **Updates** (`Updates.kt`). The project's GitHub releases, asked only when Launcher Settings
  opens (again after 10 minutes, or at once after a failed check), never in the background: the
  latest release's tag (without its "v") against `versionName`, its first `.apk` asset. "None published"
  is GitHub's 404 (no release, or a private repo). A on Install: the first time it opens
  Android's *Install unknown apps* page for Portal ("Allow, then A again"); then it downloads
  to `cache/update/` ("Downloading n%"), refuses an APK that isn't this package, isn't a higher
  `versionCode` or isn't signed with the same key ("Not this app", "Not newer", "Signed
  differently"), and commits it in a `PackageInstaller` session. No Android confirm
  (`UPDATE_PACKAGES_WITHOUT_USER_ACTION`); Play Protect may still ask.
  `UpdateReceiver` gets the installer's answer: a confirm it shows, or "Install failed" or
  "Cancelled" for the row. The releases URL is `BuildConfig.UPDATE_URL`; a debug build takes
  `update.url` from `local.properties` instead, for a stand-in (`gotchas.md` *Trying an update*).
- **Art** (`Art.kt`). Lookups, downloads and records (one per package, in their own
  `SharedPreferences`; images in `files/art/`), plain `HttpURLConnection` and `org.json` on the
  IO dispatcher; `rememberArt` decodes a file scaled down to the size it is drawn, covers two
  at a time and heroes and scenes one at a time (a step already passed drops out), and keeps
  what it decoded in two 24 MB memory caches (covers; heroes and scenes), so a turned-to row or
  a hero seen before draws on its first frame; trimmed when Android asks. Composition never
  reads the disk: the art files are listed once and kept current, and a change redraws only
  that app's tiles. A default-network callback reruns failed lookups when the network
  returns. The
  SteamGridDB key is the owner's, from *Settings → Art* (`art.key` in the `launcher`
  preferences); a debug build falls back to `steamgriddb.apiKey` from `local.properties` through
  `BuildConfig`, a release build carries none. Without a key nothing is looked up and Art…
  offers only the gallery and the icon.
- **Setup file** (`Setup.kt`). *Save setup* (Settings → System) writes `Download/Portal
  setup.json` through MediaStore, over this install's earlier file: the `launcher`, `art`,
  `picks` and `scenes` preferences as typed JSON (without `art.key`), and the files nothing could
  fetch again, gallery picks (`files/art/`, a pick stored as a `content:` URI) and wallpapers
  (`files/backdrops/`), in base64. *Restore setup* (a second A) opens Android's file picker,
  reads the whole file into `cache/setup/` first and refuses a broken, foreign or newer one
  ("Not a Portal setup", "Saved by a newer Portal"); then it copies the files in first (on a
  full disk the old setup stands), replaces the four preferences (keeping the current key; a
  value not of its key's type is dropped and logged, the rest kept), deletes the other files in
  `files/art/` and restarts the process as Home; a failure part way puts the preferences and
  the replaced files back and says "Couldn't restore".
  `Art.lookUp` fetches the images again for a matched app with neither cover nor hero on disk,
  or one in `art.pending`: each image on its own, a picked URL as is (one that answers 404/410
  gives way to the automatic one), otherwise the scored or first one (needs the key); a scored
  scene Steam took down is looked for again. Android's backup (`res/xml/data_extraction_rules.xml`) carries the same four
  preferences to the cloud and, in a device-to-device transfer, the art and wallpapers too.
- **Launches.** Every tile reports where it was laid out (`launchesFrom`), keyed by where it sits
  (row, dock, All apps) and the app; opening an app passes that rect to `startMainActivity` as
  the source bounds and as a clip-reveal `ActivityOptions`.
- **Network notes** (`Network.kt`). After a failed request (the key check, Updates),
  `Connectivity` looks once at every network: none validated is "Offline", a validated one with
  an unknown host is "Blocked" (Android's per-app network block), anything else "No answer".
- **Build.** Gradle wrapper, one module, Compose foundation and ui only (no Material: every
  surface is ours), no Hilt, no navigation library. A release build is minified and signed with
  the release key, which lives outside the repo; `local.properties` names it (the `release.*`
  lines). Without those lines the
  release APK is unsigned.

**The public page.** `README.md` is written for users, not developers, with
screenshots (`docs/screenshots/`, `adb exec-out screencap`, a photo in
it as JPEG 90). License MIT (`LICENSE`).
