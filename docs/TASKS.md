# Tasks
<!-- feature-board 1 -->

The feature board (owner, 2026-10-25). Features move **Feature list → Planned → Working on →
Released**. Each release has a **theme** and holds the cards planned for it. The milestones
M0–M8 built the foundation; their history is under *Released* (full lines:
`archive/tasks-M1-M7.md`).

Rules:
- **Work** on the first open card or task under *Working on*, or the owner's explicit ask; one
  per session unless the owner bundles several.
- **IDs never change when a card moves.** A new card is **F<n>**, numbered on from the last
  milestone M8 (F9 first; next free F46), so its tasks are T<n>.1, T<n>.2 … and its decision
  entries D<n>.1 …, clashing with no milestone's. A card that came out of a milestone keeps its
  task IDs. Owner bug and tweak batches stay **B<n>** (B13 next). A follow-up gets an F number
  when it is planned.
- **A card is a line or two**: what, why, a pointer to its spec. A long spec is a decision entry
  marked "planned" (`decisions/F<n>-<slug>.md` for a big feature, `decisions/<version>.md` for
  a release's small ones), not a paragraph here.
- **Planning is the owner's**: a theme and cards from the feature list into *Planned*; when its
  work starts, the release moves to *Working on*. A big feature may land over several releases.
- **Done**: tick it with the date and one clause once it builds, lints and runs on the Portal;
  the detail goes to its decision entry. It stays ticked under *Working on* until the release
  ships, then folds into that release's line under *Released* (the full lines to
  `archive/tasks-<version>.md`).
- **Releasing** (owner, 2026-10-25) raises the app's version (`versionCode`, `versionName` in
  `app/build.gradle.kts`) and builds the release APK `PortalLauncher-<version>.apk`.
- **Format**: feature-board format 1 (the notes-architecture skill's
  `references/board-format.md`); the owner's board app moves cards in this file, and the notes
  audit checks it.

## Working on

### 0.5.0 — Steady and quick
The owner's ask of 2026-10-30 (night): "Ease of use, a slick controller interface, and launcher
reliability is first … You are the boss tonight"; the session picked the cards from an audit
(`decisions/0.5.0.md`). All built, linted and run on the Portal (debug); the owner's hand checks:
`VERIFY.md` *V-0.5.0*. Not released: versionName is still 0.4.2.
- [x] **F37 Standby in its own window** (2026-10-30; D37.1): waking returns to the game that was
  under it, extra presses land nowhere, Home stops drawing underneath, never over the lock screen.
- [x] **F38 One Home, cleaner starts** (2026-10-30; D38.1): any other start hands over to Home
  (no second launcher); the Home dialog from Settings; no gray flash on a cold start.
- [x] **F39 Input that can't misfire** (2026-10-30; D39.1): no keys while an app starts; Home
  mid-rename no longer kills the controller; held bumpers and settings values act once.
- [x] **F40 The left stick** (2026-10-30; D40.1): the dominant axis only, faster the further
  it is pushed.
- [x] **F11 Recently played** (2026-10-30; D11.1, D11.2): a Recent category, second; Home's place
  kept when Android ends the launcher mid-game.
- [x] **F13 Notification shade button** (2026-10-30; D13.1): a bell first in the quick panel, and
  *Notifications* in All apps for the dock.
- [x] **F16 Search on Y** (2026-10-30; D16.1): a letter strip in All apps, no keyboard; the
  focused letter in the header.
- [x] **F41 Controller polish** (2026-10-30; D41.1): the first Home press only closes; grids open
  at their focus and glide; the quick panel's rows keep their columns; Art…'s grids.
- [x] **F42 Smoother Home** (2026-10-30; D42.1): a category's row glides in from its side, L1/R1
  glyphs by the name, decoded art kept in memory, fewer redraws and system calls.
- [x] **F43 Art that recovers** (2026-10-30; D43.1): a failed download is retried when the
  network returns, never "no art" for good.
- [x] **F44 Data that holds** (2026-10-30; D44.1): a bad setup file can't crash Home; places kept
  for uninstalled apps; the first sort by key; the clock right after sleep.
- [x] **F45 Easier setup** (2026-10-30; D45.1): the art key from the controller; the Hold Home
  row no longer loops; Offline, Blocked or No answer.

## Planned

### Candidates
No release planned yet: the owner picks a version, a theme and the cards from the feature list.
- **VERIFY rounds** (owner, 2026-10-25; carried from 0.1.0–0.4.0, into the next release): the milestones' hand checks on the Portal (`VERIFY.md`).
  - [ ] T2.3 Device session: install, run `docs/VERIFY.md` *V-skeleton*, fix what breaks; the
    owner sets it as Home and goes back to Launcher3 once, to prove the way back.
  - [ ] T5.3 VERIFY round: ghost opacity, type size (`VERIFY.md` *V-categories*).
  - [ ] T7.3 VERIFY round on the device (`VERIFY.md` *V-quick*).
  - [ ] T6.5 VERIFY round on the device (`VERIFY.md` *V-transit*, *V-accent*).
  - [ ] T35.1 VERIFY round (`VERIFY.md` *V-standby-switch*), from 0.4.1.
  - [ ] T36.1 VERIFY round (`VERIFY.md` *V-0.4.2*: brightness steps, portrait), from 0.4.2.

## Feature list

### Big
- **F14 Monochrome icons** (2026-10-25): a monochrome icon style from Android 13's themed-icon layer (often vector).

### Small
- **F17 Work-profile badges** (2026-10-25): work-profile badges on tiles.
- **Bring back a deleted built-in category** (2026-10-30, audit E11): a deleted Games or Recent
  can't come back; *New category* can't make a built-in one.
- **New installs marked** (2026-10-30, audit F7): All apps opens on a new app and marks it until
  it is opened or placed.

### Follow-ups
Not scheduled; a line or two each; an F number when planned.
- **Focused icons at their scaled size**: the 1.08 focus scale resamples the tile and dock icon,
  a little soft on the dock (`decisions/M2-skeleton.md` *First device run*). Needs the focused
  tile to grow by layout (0.5.0's lane found the renderer alone can't).
- **Search from a keyboard** (2026-10-30): a hardware keyboard's letters type into All apps'
  search (D16.1).
- **Art…'s own network note** (2026-10-30): Art… still says "Couldn't reach SteamGridDB" where
  the settings row says Blocked; A on a Blocked note could open App info (D45.1).
- **Speed, if it is ever felt** (2026-10-30, audit S3, S8, S10, S6): every D-pad step recomposes
  every carousel tile; the reflection's shader per hero; the empty row until icons load after a
  restart; launch rects from layout coordinates.

### Waiting for data
- **Odin 3 Portal**: confirm panel size and density when it exists; `Dimens` should cover it.

## Released

One line each, and the milestones before the board. The full task lines:
`archive/tasks-<version>.md` and `archive/tasks-M1-M7.md`; the why: `decisions/`.

### Releases
- **0.4.2** (2026-10-25, Brightness and the dock): B11 (brightness as the Portal counts it, 5%
  steps), F36 (Home dims while the dock has focus), B12 (portrait letterboxed; a resume crash
  with standby off, from 0.4.1); its VERIFY round to *Candidates*; versionCode 6, GitHub release
  v0.4.2. `archive/tasks-0.4.2.md`, `decisions/0.4.2.md`.
- **0.4.1** (2026-10-25, Standby's switch): F35 (Hold Home's switch easy to find, kept on, an
  optional adb grant), B10 (Triangle and Square everywhere); its VERIFY round to *Candidates*;
  versionCode 5, GitHub release v0.4.1. `archive/tasks-0.4.1.md`, `decisions/0.4.1.md`.
- **0.4.0** (2026-10-25, Stress Test and Prepare for release candidate): F28–F34, B9; the first
  public release (repo public, GitHub release v0.4.0 with `PortalLauncher-0.4.0.apk`); its VERIFY
  rounds carried to *Candidates*; versionCode 4. `archive/tasks-0.4.0.md`, `decisions/0.4.0.md`.
- **0.3.0** (2026-10-25, Sounds and effects): F22, F23, F24, F21, F25, F26, F27, B6–B8; its VERIFY
  rounds carried into 0.4.0; versionCode 3, release APK `PortalLauncher-0.3.0.apk`. `archive/tasks-0.3.0.md`,
  `decisions/0.3.0.md`, `F22-sounds.md`, `F25-settings.md`, `F26-setup.md`.
- **0.2.0** (2026-10-25, Functional and refinement): F20, F19, F12, F18; its VERIFY rounds
  carried into 0.3.0; no version of its own (0.3.0 went from versionCode 1 to 3). `archive/tasks-0.2.0.md`, `decisions/0.2.0.md`.
- **0.1.0** (2026-10-25, First public release): M8, F9, F10, B5, on top of M1–M7 and B1–B4;
  release APK checked on the Portal (T8.2); going public and the GitHub release are the owner's.
  `archive/tasks-0.1.0.md`, `decisions/0.1.0.md`, `decisions/M8-release.md`.

### Milestones (the foundation, 2026-10-25 … 2026-10-25)
- **M0 Notes** (2026-10-25) — notes in the iceberg convention, `concepts/` drop folder, `git init`.
- **M1 Design direction** — T1.1–T1.2: concepts reviewed, the direction drafted. `decisions/M1-design.md`.
- **M2 Skeleton that runs** — T2.1–T2.2: the toolchain and the Home app (T2.3 rides in 0.1.0). `decisions/M2-skeleton.md`.
- **M3 Home screen v1** — T3.1–T3.4: status, options sheet, scaling, trials ended. `decisions/M3-home.md`.
- **M4 Art and the hero** — T4.1–T4.7: SteamGridDB art, Home v2, scenes, art choices. `decisions/M4-art.md`.
- **M5 Categories** — T5.1–T5.2: seeding, the layer on B (T5.3 rides in 0.1.0). `decisions/M5-categories.md`.
- **M6 Depth** — T6.1–T6.4: effects, Launcher Settings, motion pass (T6.5 rides in 0.1.0). `decisions/M6-settings.md`.
- **M7 Quick settings** — T7.1–T7.2: the quick panel (T7.3 rides in 0.1.0). `decisions/M7-quick-settings.md`.

### Owner batches
- [x] **B1** (2026-10-25): B1.1 (2026-10-25) Focus pulse at half speed, 1.8 s each way; a speed setting waits for T6.2.
- [x] **B2** (2026-10-25): owner tweaks.
  - [x] B2.1 (2026-10-25) American English in the UI, code and notes; the stored favorites key keeps its old spelling.
  - [x] B2.2 (2026-10-25) Menu-button hints drawn as their printed shapes (a square for Select, a right triangle for Start) knocked out of the disc, big and rounded.
  - [x] B2.3 (2026-10-25) A Home name too long for the screen scrolls while the row has focus.
  - [x] B2.4 (2026-10-25) The panel is *Launcher Settings* (Triangle → Launcher settings…).
  - [x] B2.5 (2026-10-25) Re-picking a game after *Use the app's icon* fetches its scene again (`decisions/M4-art.md`).
  - [x] B2.6 (2026-10-25) Hints at 80%, each on the side its button is on (Square bottom-left).
- [x] **B3** (2026-10-25): owner's answers.
  - [x] B3.1 (2026-10-25) Effects settled as built, all three on (`decisions/M6-settings.md` *Owner's answers*).
  - [x] B3.2 (2026-10-25) A gear at the dock's end opens Launcher Settings (`decisions/M6-settings.md`).
  - [x] B3.3 (2026-10-25) Quick panel buttons and sliders as Material Symbols icons (`decisions/M7-quick-settings.md` *Icons*).
  - [x] B3.4 (2026-10-25) The first D-pad press after a touch counts: the window leaves touch mode as a touch ends (`gotchas.md`).
  - [x] B3.5 (2026-10-25) Covers stay on Home only; the dock is for utilities (`decisions/M4-art.md` T4.2).
- [x] **B4** (2026-10-25): owner tweaks.
  - [x] B4.1 (2026-10-25) Eight accents (Lime, Rose added) and a hue/intensity picker on A (`decisions/M6-settings.md` *Accents*).
