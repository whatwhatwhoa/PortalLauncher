# Tasks

Rules: one task per session unless the owner bundles several; work on the first unchecked task
under *Open*, or the owner's explicit ask; tick with the date and one clause, then its detail
goes to `decisions/` and the line stays one line. Owner bug/tweak batches are B1, B2, …
After M4 the list moves to the feature board (`notes-architecture` skill, format 1).

## Open (in order)

### M1 — Design direction (owner, 2026-10-25)
Goal: from the owner's concepts, settle what "clean and welcoming" looks like on the Portal.
- [x] T1.1 (2026-10-25) Concepts reviewed: `decisions/M1-design.md`; direction drafted in
  `architecture.md` *Look*.
- [x] T1.2 (2026-10-25) Answers in: square tiles, A bottom, procedural accents; font and accent
  go to an on-device trial (`decisions/M2-skeleton.md`).

### M2 — Skeleton that runs (owner, 2026-10-25)
Goal: a Gradle project that builds on this laptop and installs on the Portal as a Home app.
- [x] T2.1 (2026-10-25) Gradle 9.8.2 + AGP 9.4.1 + Kotlin 2.4.21 on a native ARM64 JDK 21;
  `assembleDebug` and lint pass.
- [x] T2.2 (2026-10-25) Home app built with the owner's bundle: favourites carousel, All apps
  drawer, utility dock, key table, Home-role request; not yet run on the device.
- [ ] T2.3 Device session: install, run `docs/VERIFY.md` *V-skeleton*, fix what breaks; the
  owner sets it as Home and goes back to Launcher3 once, to prove the way back.

### M3 — Home screen v1 (owner, 2026-10-25)
Goal: the first version the owner would keep as the daily Home.
- [ ] T3.1 Status: battery and Wi-Fi beside the clock (vector glyphs); system bars stay hidden.
- [ ] T3.2 Options on + (pin/unpin, move left/right, hide, app info, uninstall) for carousel
  and drawer tiles; dock apps chosen the same way; L1/R1 letter jump in All apps.
- [ ] T3.3 Font scale clamp and `Dimens` scaling; animation-off honoured; launch transition.
- [ ] T3.4 Trial ends: the owner picks the font and the accent; drop the trial keys and the
  unused fonts; tune constants at 1:1 screenshots (`adb exec-out screencap -p`).

### M4 — Depth (owner, 2026-10-25)
Goal: the overlay effects and gradients the owner asked for, keeping everything vector-crisp.
- [ ] T4.1 Overlay pass: background gradient, focus glow, tile depth (a soft shadow or
  highlight), light and dark; each effect switchable off while judging.
- [ ] T4.2 Settings screen: theme override, accent, effects on/off.
- [ ] T4.3 Motion pass: focus pulse tuning, All apps transition, a VERIFY round.

### Later
- Covers and the hero backdrop: SteamGridDB (needs the owner's free API key) or a document-picker
  import, cached at ≥ 600 px, never upscaled; the backdrop only once covers are real.
- Categories or tabs (Games / Apps), recently played ordering, search on Y.
- Quick toggles: Wi-Fi, brightness, the rotation app.
- A dock button that opens the notification shade directly (needs an accessibility service or
  a hidden API; the swipe works meanwhile).
- A monochrome icon style from Android 13's themed-icon layer (often vector).
- Odin 3 Portal: confirm panel size and density when it exists; `Dimens` should cover it.
- Work-profile badges on tiles.

## Done (one line each)
- M0 (2026-10-25): notes in the iceberg convention, `concepts/` drop folder, `git init`.
