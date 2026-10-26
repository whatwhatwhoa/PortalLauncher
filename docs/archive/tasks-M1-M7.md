# Tasks M1–M7, the foundation (archived 2026-10-25 at the switch to the feature board)

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
- [x] T2.2 (2026-10-25) Home app built with the owner's bundle: favorites carousel, All apps
  drawer, utility dock, key table, Home-role request; not yet run on the device.
- T2.3 (open) rides in 0.1.0 on `TASKS.md`'s *VERIFY rounds* card.

### M3 — Home screen v1 (owner, 2026-10-25)
Goal: the first version the owner would keep as the daily Home.
- [x] T3.1 (2026-10-25) Status: clock, Wi-Fi wedge, battery with % inside, tighter to the
  corner (`decisions/M3-home.md`); not yet clicked by the owner.
- [x] T3.2 (2026-10-25) Options sheet on Triangle and long press, move mode, hidden apps, owner's
  dock, L1/R1 letter jump; Selawik joins the font trial (`decisions/M3-home.md`).
- [x] T3.3 (2026-10-25) Canvas scaling and font scale clamp at the root, animation-off watched
  live, clip-reveal launch from the tile; Selawik 1.3× (`decisions/M3-home.md`).
- [x] T3.4 (2026-10-25) Trial ends: Selawik at 1.15×, accent White (owner), trial keys gone
  (`decisions/M3-home.md`, `decisions/M4-art.md` *Scenes kept*).

### M4 — Art and the hero (owner, 2026-10-25)
Goal: real art where an app's icon is too small, and the Home layout built around it.
- [x] T4.1 (2026-10-25) SteamGridDB covers and heroes for favorites on a close label match,
  Triangle → Art… to pick or search; blocked on the device's network setting (`decisions/M4-art.md`).
- [x] T4.2 (2026-10-25) Square covers in the Home tiles where art exists; NTE's shows
  (`decisions/M4-art.md`).
- [x] T4.3 (2026-10-25) Home layout v2: hero band with vignette, Metro-style display name, reflections,
  focused tile 1.2× by layout, dock beside the hints (`decisions/M4-art.md`).
- [x] T4.4 (2026-10-25) Backdrop trial ended on Black, the key removed; choices wait for T6.2
  (`decisions/M4-art.md` *Scenes kept*).
- [x] T4.5 (2026-10-25) Scenes kept: fetched from Steam on a match, the shot picked by a score
  tuned to the owner's two picks; dock centered, smaller hints (`decisions/M4-art.md` *Scenes kept*).
- [x] T4.6 (2026-10-25) SteamGridDB choice: Art… opens the game's covers or heroes as a grid,
  A picks; kept per slot (`decisions/M4-art.md` *Art choices*).
- [x] T4.7 (2026-10-25) Art sources: a Steam screenshot grid for the scene; cover or scene from
  the gallery, auto-cropped, any app (`decisions/M4-art.md` *Art choices*).

### M5 — Categories (owner, 2026-10-25)
Goal: the carousel shows one category at a time, and a big-type categories layer (XMB / Metro)
moves between them. Proposal and open questions: `decisions/M5-categories.md`.
- [x] T5.1 (2026-10-25) Categories seeded from the app's manifest, moved with Triangle → Category…,
  label above the row (`decisions/M5-categories.md` *Built*).
- [x] T5.2 (2026-10-25) B opens the layer: names stacked, the row ghosted at 18% and zoomed out,
  previewing as focus moves (`decisions/M5-categories.md` *Built*).
- T5.3 (open) rides in 0.1.0 on `TASKS.md`'s *VERIFY rounds* card.

### M7 — Quick settings (owner, 2026-10-25)
Goal: the pull-down's everyday controls (brightness, volume and the like) from the controller,
on Home. Proposal, what Android allows and open questions: `decisions/M7-quick-settings.md`.
- [x] T7.1 (2026-10-25) Owner: Square opens it, "brightness, volume etc."; grants on first use as proposed.
- [x] T7.2 (2026-10-25) Quick panel: brightness and volume sliders, three toggles, five shortcuts
  (`decisions/M7-quick-settings.md` *Built*).
- T7.3 (open) rides in 0.1.0 on `TASKS.md`'s *VERIFY rounds* card.

### M6 — Depth (owner, 2026-10-25)
Goal: the overlay effects and gradients the owner asked for, keeping everything vector-crisp,
and the settings screen that holds the choices. Settings proposal: `decisions/M6-settings.md`.
- [x] T6.1 (2026-10-25) Effects: accent-tinted background gradient, focus glow breathing with
  the ring, tile depth (top highlight dark, shadow light); each a switch (`decisions/M6-settings.md` *Built*).
- [x] T6.2 (2026-10-25) Settings *Look* panel from Triangle → Settings… and Android's gear: theme,
  six accents, backdrop, scenes, covers, the three effects, pulse (`decisions/M6-settings.md` *Built*).
- [x] T6.3 (2026-10-25) Motion pass: one timing for every layer, All apps rises as Home steps back, eased pulse (`decisions/M6-settings.md` *Motion pass*).
- T6.5 (open) rides in 0.1.0 on `TASKS.md`'s *VERIFY rounds* card.
- [x] T6.4 (2026-10-25) *Home*, *Art*, *Apps*, *System* groups, action rows with a confirming
  second A, credits (`decisions/M6-settings.md` *Built (T6.4)*).
