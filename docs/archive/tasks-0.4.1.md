# Tasks 0.4.1 — Standby's switch (archived 2026-10-25 when it moved to Released)

The full lines. The one-line summary is in `TASKS.md` *Released*; the why in
`decisions/0.4.1.md`. Its open VERIFY round moved to *Planned* → *Candidates*.

### 0.4.1 — Standby's switch
A fix release (owner, 2026-10-25: "bump release to 0.4.1 for this fix").
- [x] **F35 Standby's switch** (2026-10-25: built and linted, not run on the Portal, which was
  not connected; `decisions/0.4.1.md` D35.1): Hold Home stopped working (the service had been
  turned off); make turning it on automated and easy. The row lands on Portal's row in Android's
  list, says when Android restricts it and then opens App info; Home notes once when Android
  turned it off; with an adb grant the launcher switches it itself and puts it back.
- [x] **B10** (2026-10-25): B10.1 the README names the menu buttons Triangle (Start) and Square
  (Select) (`decisions/0.4.1.md` B10).
