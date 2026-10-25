# M2 — Skeleton: owner's answers, toolchain, icons (2026-10-25)

The owner's answers to the M1 questions, the build versions picked, and the choices made while
building the first skeleton (carousel, All apps, dock). How it works now: `architecture.md`
*Look*, *Structure*; `device.md` *Toolchain*.

Status: accepted 2026-10-25; the skeleton's hand check (*V-skeleton*) is still open.

## The owner's answers
- **Q-tile:** square, "because Android icons come in round/square".
- **Q-abxy:** A is the bottom button (Xbox layout). Android reports it as `BUTTON_A`, so the
  key table maps it straight; a swap stays a one-table change.
- **Q-accent:** "we can generate an accent palette procedurally, right? Make 3–4 to go through."
  Done as a hue plus a per-theme OKLCH lightness; the presets since: `M6-settings.md` *Accents*.
- **Q-font:** asked back, not answered; the candidates went to an on-device trial.
  Answered 2026-10-25: Selawik (`M3-home.md`); Inter, Nunito Sans and Manrope dropped.
- **Q-sgdb:** not now; the scope is replacing the Android launcher. Covers move to *Later*
  (since done: `M4-art.md`).
- **Q-cocoon:** "the other launchers are ass"; not tried, not wanted.
- **Q-commit:** commit and push to the owner's GitHub (`whatwhatwhoa`), private for now.
- New asks: SVG (vector) for everything, raster only when unavoidable; spacing consistent on a
  modern scale; overlay effects and gradients later; a favorites carousel, an All apps drawer
  that can be opened, and a bottom row of utility apps.

## Toolchain
Android Studio, and with it the JDK 25 the notes assumed, is no longer on the laptop. Instead:
Temurin JDK 21 for Windows **ARM64** (native, not emulated), unpacked to
`C:\Users\Jim\AppData\Local\Android\jdk-21`. JDK 21 is LTS and inside every tool's support
range, which JDK 25 was not certain to be. Versions, all the newest stable on 2026-10-25:
Gradle 9.8.2, AGP 9.4.1 (supports up to API 37; needs Gradle ≥ 9.6, JDK ≥ 17), Kotlin 2.4.21,
Compose BOM 2026.09.00, activity-compose 1.13.0. AGP 9 compiles Kotlin itself; the Kotlin
plugin is declared `apply false` only to pin its version to the Compose compiler plugin's.
The first `assembleDebug` built in 2 min 21 s from empty caches, then about 10 s.

## Icons: vector where the app ships it, never upscaled
Android has no SVG; its vector format is `VectorDrawable`, and each app ships its own icon, so
we can't regenerate them. What we control is how they are drawn:
- An adaptive icon is two 108 dp layers, each a vector or a bitmap. System and Google apps are
  mostly vector; many third-party apps ship PNG layers, at most 432 px (xxxhdpi).
- We ask `LauncherActivityInfo.getIcon(DENSITY_XXXHIGH)` whatever the screen density, so bitmap
  layers arrive at their largest. A 160 dp tile at density 288 is 288 px, and the layer's 72 dp
  visible window then spans exactly 432 px: bitmaps draw 1:1, vectors render at full size.
- The layers are drawn straight onto the Compose canvas, inside our own mask (rounded square
  for tiles, circle for the dock), not the system's. That is the "render them slightly
  different / give them squares" the owner asked for, and it works for every adaptive icon.
- Bitmap layers get mipmaps, so the 56 dp dock size (2.9× smaller) doesn't alias.
- Legacy (non-adaptive) icons sit inset 16% on the surface color.
- Each place an icon is drawn gets its own drawable copy: the render thread reads a drawable's
  bounds after recording, so one drawable shared by two sizes would draw both at the last size.

## Other choices
- **Accent from OKLCH.** An accent is a hue at a fixed lightness per theme, chroma lowered
  until it fits sRGB: the same hue reads equally on both backgrounds, and the light theme's
  title stays legible (a fixed `#19C3E6` is too pale on `#EBEBEB`). The trial's hues (Cyan 215,
  Coral 28, Lime 135, Violet 300) and light lightness 0.60 were replaced (`M6-settings.md`).
- **Spacing scale** 4/8/12/16/24/32/48 dp; every gap in the code is one of these.
- **Dock = All apps + utility apps** (Settings, Files, Play Store, default browser, whichever
  exist), drawn as round icons. The earlier plan of system-function buttons (Notifications,
  Power) gives way to the owner's "bottom row of utilities/general apps".
- **Favorites:** X in All apps toggles one. The skeleton's fallback (every app not in the dock
  until the first favorite exists) went with categories (`M5-categories.md` *Built*).
- **SharedPreferences, not DataStore**: small values read once at start; no flows needed.
  Revisit if settings grow.
- **No motion handling for the stick.** Android turns unhandled joystick and hat motion into
  D-pad key events with repeat, so the key table covers both.

## First device run (2026-10-25)
Ran first time on the Portal over adb, no crash; the owner chose Portal in the Home-role dialog.
Driven with `adb shell input keyevent` (keyboard-source keys, not the real controller) and 1:1
screenshots. What it found:
- Every text style rendered at the font file's default weight (`gotchas.md` *Variable fonts*).
- **Crispness:** unfocused tiles and dock buttons have one-pixel edges at 1:1. The focused
  tile and dock button are a little softer, because the 1.08 scale resamples them; it shows
  most on the small dock buttons (a follow-up on the board: draw the focused icon at its scaled
  size instead of scaling the layer).
- **Tuning seen, not changed:** at 1.08 plus the ring, the focused tile leaves only about 8 px
  to the next tile; in All apps the ring touches the label below; the previous tile peeks into
  the 48 dp left margin.
