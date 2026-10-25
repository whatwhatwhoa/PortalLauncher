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

Not goals: Nova-style customisation (grids, icon packs, gestures), widgets, emulator ROM
scanning, phone layouts in portrait.

## Look

Direction from the owner's concepts (`decisions/M1-design.md`) and answers
(`decisions/M2-skeleton.md`). Numbers are the skeleton's values, tuned on the device; in code
they live in `Look.kt` (`Space`, `Dimens`), so this section and that file change together.
Still on trial: the font and the accent (**trial** below), picked by the owner on the device.

**Vector first.** Glyphs and our own icon are vectors (drawn in code or `VectorDrawable`), type
is a bundled variable font, app icons are drawn 1:1 (*Tiles*). A raster asset needs a reason.

**Spacing.** One scale: 4, 8, 12, 16, 24, 32, 48 dp (`Space`). Every gap and margin is a step
on it; a new size means a new token, not a literal.

**Canvas.** 1920×1080 at density 288 is **1067×600 dp**. Landscape, edge to edge, system bars
hidden (a swipe from the edge shows them); the launcher draws its own status. Touch is a full
second input, never the only one.

**Home anatomy** (top to bottom):
```
 48 ┌──────────────────────────────────────────────── 12:41 ┐ 32 top
    │                                                          │
    │  Title of the focused app        (accent, 24 sp)          │
    │  ↕ 24                                                     │
    │  ┏━━━━━━┓ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌───      │ tiles 160, gap 16
    │  ┃ 1.08 ┃ │      │ │      │ │      │ │      │ │         │ (row centred in
    │  ┗━━━━━━┛ └──────┘ └──────┘ └──────┘ └──────┘ └───      │  the free space)
    │                                                          │
    │              (⋮⋮⋮)  (⚙)  (▣)  (▶)  (◎)                   │ dock 56, gap 24
    │                       ↕ 32                               │
    ├──────────────────────────────────────────────────────────┤ hairline
    │  − Accent: Cyan   X Font: Inter          Ⓐ Open  Ⓨ All apps│ hint bar 56
    └──────────────────────────────────────────────────────────┘
```
1. *Status*, top-right: the clock, body type, 32 dp from the top and 48 from the side.
   Battery and Wi-Fi come next.
2. *Carousel*: the favourites in one row, 48 dp side margins. The focused tile's title sits
   24 dp above it in the accent colour, one line, up to 480 dp, cut with an ellipsis. The row
   scrolls so the focused tile stays in the first slot.
3. *Dock*: round 56 dp buttons, 24 dp apart, centred, 32 dp above the hint bar: All apps first,
   then the utility apps (Settings, Files, Play Store, default browser, whichever exist). The
   focused button's name shows 12 dp above it in caption type.
4. *Hint bar*: a hairline in the rule colour, then live button hints right-aligned, 24 dp
   apart, each a drawn disc with the button letter knocked out and an 8 dp gap to its label.
   The left side holds a muted note (for now, the trial choices).

**All apps.** A full-screen layer that slides up over Home: "All apps" and the count top-left,
the clock top-right, then an 8-column grid of 96 dp tiles (12 dp corners, 24 dp gaps), each
with its name 8 dp below in caption type; a favourite has an accent dot on its corner. The
focused row is kept second from the top. Opens with Y, the dock's first button, or a swipe up.

**Tiles.** Square, 160 dp, 16 dp gap, 16 dp corners. An app's adaptive icon fills the tile
edge to edge inside our own mask; a legacy icon sits inset 16% on the surface colour. Icons are
fetched at xxxhdpi and drawn at the shown size, so a 160 dp tile is the icon's layer 1:1 on
this panel (`decisions/M2-skeleton.md` *Icons*). Covers (full-resolution art) are *Later*.

**Focus.** The focused tile or button scales to 1.08 on a spring, with a 3 dp accent ring 3 dp
outside its edge whose alpha pulses slowly (the Switch's tell).

**Type.** One bundled variable font, never the system default. **Trial:** Inter, Nunito Sans,
Manrope (X on Home cycles). Four sizes only: 24/32 title, 18/24 body, 16/24 hint, 14/20
caption (size/line height, sp). Clamping the system font scale (0.9–1.15) is still to come.

**Colour.** Two themes following the system dark/light setting: light background `#EBEBEB`
with `#2D2D2D` text, dark `#2D2D2D` with white text (the Switch's two). Surface is the
background mixed 8% toward the text; muted text is the text at 60%; the rule at 16%. The accent
is a hue made into OKLCH colour at lightness 0.80 (dark) or 0.60 (light), chroma 0.15 fitted
to sRGB. **Trial:** Cyan 215°, Coral 28°, Lime 135°, Violet 300° (− on Home cycles).

**Overlay effects (to come).** Gradients, a focus glow and depth are wanted once the skeleton
is settled; until then the only effects are the ring and the scale.

**Motion.** Short and springy, never decorative: focus scale on a spring, the row and grid
scroll animated to the focused item, All apps slides 1/8 of the screen and fades. The launch
transition and honouring animation-off are still to come.

**Controller map.** Keys become actions once, at the activity (`Input.kt`), so an ABXY swap is
one table. A is the bottom button (Xbox layout, `BUTTON_A`). Home: D-pad or left stick moves
along the row, down to the dock and back up; A opens; Y opens All apps; L1/R1 jump five tiles;
L2/R2 jump to the ends; X and − are the trial keys. All apps: D-pad moves in the grid; A
opens; X toggles favourite; B or Y closes; L1/R1 jump three rows. Home while home returns to
the start of the row. Touch: tap opens, drag scrolls, swipe up opens All apps, back closes it.

**Scaling (bonus, not a goal).** Planned: every size times `windowHeightDp / 600`, clamped to
0.75–1.5, from `Dimens`; not built yet. Portrait and phones are unsupported.

## Structure

One activity and two Compose screens, in `app/src/main/kotlin/io/github/whatwhatwhoa/portallauncher/`.

- **Launcher contract.** `MainActivity` has the HOME + DEFAULT intent filter (and LAUNCHER, so
  it can be tried from Launcher3), `singleTask`, `excludeFromRecents`, `stateNotNeeded`. On its
  first start it asks once for the Home role through `RoleManager`; the owner answers in the
  system dialog and can pick Launcher3 back the same way.
- **App catalogue** (`AppCatalog.kt`). `LauncherApps` lists the launchable activities of every
  profile on a background thread and pushes add/remove/change callbacks, so the screens update
  without a restart. An `AppEntry` is key (component + user serial), label, component, user and
  icon. It also picks the dock's utility apps and launches through `startMainActivity`.
- **Input layer** (`Input.kt`). `dispatchKeyEvent` turns gamepad and keyboard keys into
  `Action`s and swallows both edges of a mapped key; only movement repeats when held. The stick
  and hat arrive as D-pad keys (Android synthesises them from unhandled joystick motion).
- **State** (`LauncherState.kt`). Screen (Home, All apps), zone (row, dock) and three focus
  indices, all our own state rather than Compose focus: a lazy row can't focus tiles that
  aren't composed, and a console row pivots instead of moving to the nearest. Indices are
  clamped on read, so lists can change under them. Favourites, accent and font persist in
  `SharedPreferences` (`Prefs`).
- **Screens.** `HomeScreen.kt` (status, carousel, dock, hint bar), `AllAppsScreen.kt` (grid);
  shared drawing in `Chrome.kt` (icon renderer, focus ring, hint bar, clock, All apps glyph);
  tokens, fonts and palette in `Look.kt`.
- **Build.** Gradle wrapper, one module, Compose foundation and ui only (no Material: every
  surface is ours), no Hilt, no navigation library.

Planned: an options sheet on + (pin, hide, app info, uninstall), our own battery and Wi-Fi
status, a settings screen, and covers with the hero backdrop (*Later* in `TASKS.md`).

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
- **Fonts:** Inter, Nunito Sans, Manrope variable TTFs from Google Fonts, in `res/font/`; their
  OFL licences in `licenses/`.

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
