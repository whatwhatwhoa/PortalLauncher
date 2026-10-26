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
  milestone M8 (F9 first; next free F37), so its tasks are T<n>.1, T<n>.2 … and its decision
  entries D<n>.1 …, clashing with no milestone's. A card that came out of a milestone keeps its
  task IDs. Owner bug and tweak batches stay **B<n>** (B12 next). A follow-up gets an F number
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

### 0.4.2 — Brightness and the dock
Proposed: the owner names the version and theme. The owner's asks of 2026-10-25 (night).
- [x] **B11** (2026-10-25: built, linted, installed on the Portal, not clicked;
  `decisions/0.4.2.md` B11): B11.1 the brightness slider straight, as the Portal's slider counts
  (0% at the bottom, not 8%; the tint near 9% on both); a curve first went the wrong way; B11.2 brightness steps on the 5% marks (volume: 15 levels, one a step).
- [x] **F36 Defocus home when using dock** (2026-10-25: built, linted, installed, not clicked;
  `decisions/0.4.2.md` D36.1): the row fades to about 40% while the dock has focus, as under
  Triangle's sheet.

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

## Feature list

### Big
- **F11 Recently played** (2026-10-25): recently played ordering (perhaps as a category).
- **F13 Notification shade button** (2026-10-25): a dock button that opens the notification shade directly (needs an accessibility service or a hidden API; the swipe works meanwhile).
- **F14 Monochrome icons** (2026-10-25): a monochrome icon style from Android 13's themed-icon layer (often vector).

### Small
- **F16 Search on Y** (2026-10-25): search on Y.
- **F17 Work-profile badges** (2026-10-25): work-profile badges on tiles.

### Follow-ups
Not scheduled; a line or two each; an F number when planned.
- **Focused icons at their scaled size**: the 1.08 focus scale resamples the tile and dock icon,
  a little soft on the dock (`decisions/M2-skeleton.md` *First device run*).

### Waiting for data
- **Odin 3 Portal**: confirm panel size and density when it exists; `Dimens` should cover it.

## Released

One line each, and the milestones before the board. The full task lines:
`archive/tasks-<version>.md` and `archive/tasks-M1-M7.md`; the why: `decisions/`.

### Releases
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
