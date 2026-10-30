# Tasks 0.5.0 — Steady and quick (archived 2026-10-30 when it moved to Released)

The full lines. The one-line summary is in `TASKS.md` *Released*; the why in
`decisions/0.5.0.md`. Its open VERIFY round moved to *Planned* → *Candidates*.

### 0.5.0 — Steady and quick
The owner's ask of 2026-10-30 (night): "Ease of use, a slick controller interface, and launcher
reliability is first … You are the boss tonight"; the session picked the cards from an audit
(`decisions/0.5.0.md`). All built, linted and run on the Portal (debug); the owner's hand checks:
`VERIFY.md` *V-0.5.0*. Released 2026-10-30 (versionCode 7) on the owner's word.
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
