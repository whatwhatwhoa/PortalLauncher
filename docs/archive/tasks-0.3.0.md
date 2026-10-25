# Tasks 0.3.0 — Sounds and effects (archived 2026-10-25 when it moved to Released)

The full lines as they stood under *Working on*. The one-line summary is in `TASKS.md`
*Released*; the why in `decisions/0.3.0.md`, `F22-sounds.md`, `F25-settings.md`, `F26-setup.md`.
Its open VERIFY rounds moved to 0.4.0.

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
- [x] **F25 Launcher Setting Menu** (2026-10-25: a 760 dp panel at the right, categories down its left
  and the chosen one's rows beside them, Look split into Look and Effects, the accent picker in the page's
  place; `decisions/F25-settings.md` D25.1):
  Bigger window (maybe even full screen or 70%, with categories and submenus, simliar to switch. for example sounds, look and feel, etc
- [x] **B7** (owner, 2026-10-25): after F25's first look ("Launcher settings look good").
  - [x] B7.1 (2026-10-25) Look and Effects one category again, nine rows that scroll; the effects did nothing on their own page (`decisions/F25-settings.md` D25.2).
  - [x] B7.2 (2026-10-25) AYN settings (Settings' *Gamepad* page) is an app in All apps and the default dock, after Settings (`decisions/0.3.0.md` B7).
  - [x] B7.3 (2026-10-25) Home's tiles in the light theme: the shadow no longer cut square at the corners (`decisions/0.3.0.md` B7).
- [x] **B8** (owner, 2026-10-25): AYN settings, after B7.
  - [x] B8.1 (2026-10-25) Named *Controller settings*, with a Portal icon of our own (from the owner's picture, in a
    Switch glyph's style) in the dock, All apps and the quick panel (`decisions/0.3.0.md` B8).
- [x] **F26 Perserve players setup between updates** (2026-10-25: Save setup / Restore setup in System,
  one file in Download without the key, art fetched again; Android's backup carries the setup, not the art;
  `decisions/F26-setup.md` D26.1):
  Make sure updates don't the players set up as much as possible
- [x] **F27 Artist information** (2026-10-25: a tall bar and title · artist · album at 70% after the clock while
  music plays, to the screen's middle, a long one scrolling under the bar; `decisions/0.3.0.md` D27.1):
  if music is playing, add the arist information inline with the clock. Have a vertical bar to separate it,
  and scroll it if its long, hidding behind the vertical bar. Like 2:30 PM | ARIST XYZYA SUPER LONG TITLE WOWWww
  and fade the right tail if its super long. Only allow it to occup left half the screen. Font 70% opacity
