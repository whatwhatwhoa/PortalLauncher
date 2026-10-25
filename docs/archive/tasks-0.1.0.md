# Tasks 0.1.0 — First public release (archived 2026-10-25 when it shipped)

The full lines as they stood under *Working on*. The one-line summary is in `TASKS.md`
*Released*; the why in `decisions/0.1.0.md` and `decisions/M8-release.md`.

### 0.1.0 — First public release (owner, 2026-10-25)
Also in it, done since the start: M1–M7, B1–B4 (`tasks-M1-M7.md`).
- **M8 Public page and release** (owner, 2026-10-25): a GitHub page users can install from: what it is, the APK, setup, screenshots.
  - [x] T8.1 (2026-10-25) User-facing README with screenshots, MIT license, SteamGridDB key entry
    in Launcher Settings (release APKs carry no key), release signing (`decisions/M8-release.md`).
  - [x] T8.2 (2026-10-25) Release APK run on the Portal through a side-by-side `releaseCheck`
    build: nothing broke, no keep rules needed; `PortalLauncher-0.1.0.apk` built. Making the
    repo public and the GitHub release stay with the owner (`decisions/M8-release.md`).
- [x] **F9 Hero reflection** (owner, 2026-10-25): below the hero band, the hero mirrored, blurred
  and darkened, a soft rippling "windy moonlight" reflection; the band's gradient stays.
  `decisions/0.1.0.md` D9.1.
  - [x] T9.1 Build it on Home's hero band — 2026-10-25: mirrored, blurred, darkened, rippling;
    a *Hero reflection* switch (D9.1).
- [x] **F10 Starting categories** (owner, 2026-10-25): a bundled table puts popular apps in their
  usual category on the launcher's first run; apps installed after that go to All apps only, not
  Home, until placed. `decisions/0.1.0.md` D10.1.
  - [x] T10.1 Table, first-run cutoff, an All-apps-only place on Category… — 2026-10-25: 117
    packages, cut by first install time (D10.1).
- [x] **B5** (owner, 2026-10-25): `decisions/0.1.0.md`.
  - [x] B5.1 The dock's Launcher Settings button: the launcher's own icon with a small gear badge, not a third plain gear — 2026-10-25: built as asked.

Carried to 0.2.0, not done in 0.1.0: the *VERIFY rounds* card (T2.3, T5.3, T7.3, T6.5).
