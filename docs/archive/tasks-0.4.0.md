# Tasks 0.4.0 — Stress Test and Prepare for release candidate (archived 2026-10-25 when it moved to Released)

The full lines as they stood under *Working on*. The one-line summary is in `TASKS.md`
*Released*; the why in `decisions/0.4.0.md`. Its open VERIFY rounds moved to *Planned* →
*Candidates*.

### 0.4.0 — Stress Test and Prepare for release candidate
The beta (owner, 2026-10-25): already polished; this release hunts bugs and readies the release candidate.
- [x] **F30 Text on standby screen to unlock** (2026-10-25: "Press the same button 4 times to wake"
  fades in under the rings once a press counts, out when the count lapses; none with *Button hints*
  off; `decisions/0.4.0.md` D30.1):
  Only appearings after registering a press, Press same button 4 times to unlock. Disabling helper text also disables this.
- [x] **F31 Big Font Look** (2026-10-25: Look → *Big name*, Metro or Fitted; Fitted is as written,
  semibold, 60%, on screen under the status, shrinking to fit; `decisions/0.4.0.md` D31.1):
  A setting that makes the big font normal and fits most game titles and less of a metro UI. Doesn't get cutoff, is a bit more opague and less weight.
- [x] **F32 Change Menu background color** (2026-10-25: the options sheet, Launcher Settings and the
  quick panel on #242424 in the dark; `decisions/0.4.0.md` D32.1):
  Currently menu background is black, make it dark gray. Enough to stand out, but subdued to look crisp
- [x] **F33 Add reorganize dock icon options** (2026-10-25: *Move in dock* on a docked app's options
  from the row or All apps; *Add to dock (full)* grayed at six; `decisions/0.4.0.md` D33.1): Also update the options to when dock is full, the "add to dock" is greyed out with (full)
- [x] **F34 LB/RB/R3 play controls in home screen** (2026-10-25: L1/R1 turn the category on Home;
  Home → *Home screen play controls* makes them skip tracks and R3 play/pause; `decisions/0.4.0.md` D34.1): Allow a setting that enables LB and RB to change songs in home screen.
  R3 to stop playing. Home screen play controls is the setting. Default L1/R1 behavior (bumpers) is to change the home categories.
- [x] **F29 Updated GitHub home page** (2026-10-25: README for 0.4.0's features, standby, sounds,
  any display size fits, live (the scale fixed); four screenshots retaken; `decisions/0.4.0.md` D29.1): Updated instructions on must current features. Do this last.
- [x] **F28 Stress Test Script** (2026-10-25: `tools/stress/stress.py`, two 10-minute runs; back at
  home finished the launcher and every old activity leaked, both fixed; `decisions/0.4.0.md` D28.1):
  a bug hunt the agent scripts and runs (owner, 2026-10-25): load the systems, press every button,
  try to break it; what breaks becomes a fix or a card. Do this towards end/last
- [x] **B9** (2026-10-25, owner's batch during F28; `decisions/0.4.0.md` B9): B9.1 no SteamGridDB
  lookups for Android's own apps (Chrome had taken a game); B9.2 smallest width 600 back in the
  README as a tip.
