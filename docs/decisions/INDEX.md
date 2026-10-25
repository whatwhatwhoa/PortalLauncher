# Decisions — index

One line per entry, newest at the bottom. The entry holds the why; `architecture.md` holds the
what. IDs: D<n>.k is milestone n up to M8, card F<n> from F9 on (`TASKS.md`, the feature board,
since 2026-10-25); a big card's file is `F<n>-<slug>.md`, a release's small ones go in
`<version>.md`. Owner batches are B<n>, B6 next. Every entry carries a `Status:` line; settled
2026-10-25 (the stories cut back to choice and why, the what moved to `architecture.md`).

- `M1-design.md` (2026-10-25) — concept review: Switch 2 mock-up anatomy, flat Switch theme for
  v1, square vector-crisp tiles, hero backdrop later; why Console Launcher looks soft.
- `M2-skeleton.md` (2026-10-25) — owner's answers (square, A bottom, procedural accents, font
  trial, no SteamGridDB yet), JDK 21 ARM64 + AGP 9.4.1 toolchain, how icons are drawn 1:1; what
  the first device run found.
- `M3-home.md` (2026-10-25) — status corner (Wi-Fi wedge geometry, battery % inside, drawn
  once at the root), why not Segoe UI; options sheet; T3.3 scaling as one density override,
  animation-off, clip-reveal launch, Selawik 1.15×.
- `M5-categories.md` (2026-10-25) — owner's categories idea (XMB / Metro layer on B, ghosted
  carousel), single B, the five; seeding from manifests, overrides, the label's place, the layer
  as Home zoomed out, why after M4, the Home key's "while home" check.
- `M4-art.md` (2026-10-25) — SteamGridDB art: what is looked up, the close-match rule, sizes,
  the picker; restricted networking; covers Home only; Home v2's layout budget, hero crop and
  alpha vignette, the owner's numbers, reflections dropped, the no-bounce slide; the backdrop
  and 16:9 scenes; the shot score; trials ended; the dock centered; art choices (grids, the
  gallery, auto crop); the scene lost after re-picking a game (B2.5).
- `M6-settings.md` (2026-10-25) — the five groups and what is left out; owner's answers
  (effects settled, the dock's gear); the three effects and why each looks as it does, on by
  default; the panel undimmed instead of full screen, light accents at 0.55; T6.4: value and
  action rows, the confirming second A, what changed from the proposal; eight accents and the
  hue/intensity picker (B4.1); the motion pass (T6.3: one `Transit` timing, eased pulse).
- `M7-quick-settings.md` (2026-10-25) — controller quick panel (brightness, volume, …): what
  Android lets a launcher change, Home only; the owner's answers (the square); the brightness
  curve, AYN by component, no swipe; icons (Material Symbols, which ones, names on the note line).
- `M8-release.md` (2026-10-25) — the public page: README for users, screenshots, MIT; the
  SteamGridDB key moved to Launcher Settings (clipboard, checked) so release APKs carry none;
  release signing outside the repo; what is left before going public.
