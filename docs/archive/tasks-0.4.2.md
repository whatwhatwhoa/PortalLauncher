# Tasks 0.4.2 — Brightness and the dock (archived 2026-10-25 when it moved to Released)

The full lines. The one-line summary is in `TASKS.md` *Released*; the why in
`decisions/0.4.2.md`. Its open VERIFY round moved to *Planned* → *Candidates*.

### 0.4.2 — Brightness and the dock
The owner's asks of 2026-10-25 (night); released as "the next bug patch, 0.4.2" (owner).
- [x] **B11** (2026-10-25: on the Portal, the owner: "correct now. 10% no tint";
  `decisions/0.4.2.md` B11): B11.1 the brightness slider straight, as the Portal's slider counts
  (0% at the bottom, not 8%; the tint near 9% on both); a curve first went the wrong way; B11.2
  brightness steps on the 5% marks (volume: 15 levels, one a step).
- [x] **F36 Defocus home when using dock** (2026-10-25: on the Portal, the owner: "This looks
  good"; `decisions/0.4.2.md` D36.1): while the dock has focus, Home dims as under Triangle's
  sheet, all but the dock and its hints, and on the row the dock dims (after a row-only fade).
- [x] **B12** (2026-10-25: run on the Portal in both orientations; `decisions/0.4.2.md` B12):
  B12.1 portrait letterboxed (owner: "We need a fall back if the device is in portrait mode");
  B12.2 Home no longer crashes on resume while standby's service is off (from 0.4.1; found by
  the release's R8 run).
