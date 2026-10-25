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
  milestone M8 (F9 first; next free F30), so its tasks are T<n>.1, T<n>.2 … and its decision
  entries D<n>.1 …, clashing with no milestone's. A card that came out of a milestone keeps its
  task IDs. Owner bug and tweak batches stay **B<n>** (B7 next). A follow-up gets an F number
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
- **Format**: feature-board format 1 (the notes-architecture skill's
  `references/board-format.md`); the owner's board app moves cards in this file, and the notes
  audit checks it.

## Working on

### 0.3.0 — Sounds and effects
- [x] **F22 Add sounds** (2026-10-25: 12 synthesized sounds, three families, Soft by default, on at
  Low, silent in Do Not Disturb; `decisions/F22-sounds.md` D22.1–D22.2):
  Add sounds, use Nintendo switch as the basis. While the switch has playful sounds, ours will a bit more techy/modern. Let me know how to prompt this
- [x] **F23 Tile setting** (2026-10-25: Up from the first row of Art… selects the preview; its grids
  show the focused image in place, gallery tile first; `decisions/0.3.0.md` D23.1):
  For tile setting, instead of going through the menu, you scroll UP, and it'll select the preview,
  so you can change it based off there. Closer to WYSIWYG.  The larger hero/scene can be set within that submenu instead.
- [x] **F21 Add back and forward button tiles for quick settings** (2026-10-25: one line at the panel's
  top, ⏮ ⏯ ⏭ between drawn L1/R1, then title · artist over a seek bar, a faint line, then brightness, where it opens; L1/R1
  skip from any row; `decisions/0.3.0.md` D21.1):
  This is for music player. Put it above the brightness, but the default is always brightness. So the normal seek options.
  When quick setting is engaged, LB and RB controls skips. make sure glyphs are added
- [x] **F24 Choosing Hero or Scene is either or option** (2026-10-25: per app in Art…, Hero and Scene
  tabs on L1/R1; the Scenes setting gone; one image and its effects on Home; `decisions/0.3.0.md` D24.1):
  Right now it is allowing both, with no way to disable one.
  Instead of calling it Hero, we'll call it Banner or Scene (unless steamdb calls it HERO, then we'll follow steamgrid).
  You can only set to either one. There is also seems to be some conflict between what gets rendered, make sure we only render one of the options, including effects ontop of that option.
- [x] **B6** (owner, 2026-10-25): F24's follow-up; `decisions/0.3.0.md` D24.1.
  - [x] B6.1 (2026-10-25) Without a choice, an app shows SteamGridDB's first hero, a scene only when chosen or with no hero.
- **F25 Launcher Setting Menu**: Bigger window (maybe even full screen or 70%, with categories and submenus, simliar to switch. for example sounds, look and feel, etc
- **VERIFY rounds** (owner, 2026-10-25; carried from 0.1.0 and 0.2.0): the milestones' hand checks on the Portal (`VERIFY.md`).
  - [ ] T2.3 Device session: install, run `docs/VERIFY.md` *V-skeleton*, fix what breaks; the
    owner sets it as Home and goes back to Launcher3 once, to prove the way back.
  - [ ] T5.3 VERIFY round: ghost opacity, type size (`VERIFY.md` *V-categories*).
  - [ ] T7.3 VERIFY round on the device (`VERIFY.md` *V-quick*).
  - [ ] T6.5 VERIFY round on the device (`VERIFY.md` *V-transit*, *V-accent*).
- **F26 Perserve players setup between updates**: Make sure updates don't the players set up as much as possible
- **F27 Artist information**: if music is playing, add the arist information inline with the clock. Have a vertical bar to separate it,
  and scroll it if its long, hidding behind the vertical bar. Like 2:30 PM | ARIST XYZYA SUPER LONG TITLE WOWWww
  and fade the right tail if its super long. Only allow it to occup left half the screen. Font 70% opacity

## Planned

### 0.4.0 — Stress Test and Prepare for release candidate
- **F28 Stress Test Script**
- **F29 Updated GitHub home page**: Updated instructions on must current features

### Candidates
Nothing planned yet: the owner picks a version, a theme and the cards from the feature list.

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
- **0.2.0** (2026-10-25, Functional and refinement): F20, F19, F12, F18; its VERIFY rounds
  carried into 0.3.0; no version number or release APK yet. `archive/tasks-0.2.0.md`, `decisions/0.2.0.md`.
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
  - [x] B2.2 (2026-10-25) Menu-button hints drawn as their printed shapes (square −, right triangle +) knocked out of the disc, big and rounded.
  - [x] B2.3 (2026-10-25) A Home name too long for the screen scrolls while the row has focus.
  - [x] B2.4 (2026-10-25) The panel is *Launcher Settings* (+ → Launcher settings…).
  - [x] B2.5 (2026-10-25) Re-picking a game after *Use the app's icon* fetches its scene again (`decisions/M4-art.md`).
  - [x] B2.6 (2026-10-25) Hints at 80%, each on the side its button is on (− bottom-left).
- [x] **B3** (2026-10-25): owner's answers.
  - [x] B3.1 (2026-10-25) Effects settled as built, all three on (`decisions/M6-settings.md` *Owner's answers*).
  - [x] B3.2 (2026-10-25) A gear at the dock's end opens Launcher Settings (`decisions/M6-settings.md`).
  - [x] B3.3 (2026-10-25) Quick panel buttons and sliders as Material Symbols icons (`decisions/M7-quick-settings.md` *Icons*).
  - [x] B3.4 (2026-10-25) The first D-pad press after a touch counts: the window leaves touch mode as a touch ends (`gotchas.md`).
  - [x] B3.5 (2026-10-25) Covers stay on Home only; the dock is for utilities (`decisions/M4-art.md` T4.2).
- [x] **B4** (2026-10-25): owner tweaks.
  - [x] B4.1 (2026-10-25) Eight accents (Lime, Rose added) and a hue/intensity picker on A (`decisions/M6-settings.md` *Accents*).
