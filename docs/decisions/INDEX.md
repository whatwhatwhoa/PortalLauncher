# Decisions — index

One line per entry, newest at the bottom. The entry holds the why; `architecture.md` holds the
what. IDs: D<n>.k is milestone n up to M8, card F<n> from F9 on (`TASKS.md`, the feature board,
since 2026-10-25); a big card's file is `F<n>-<slug>.md`, a release's small ones go in
`<version>.md`. Owner batches are B<n>, B10 next. Every entry carries a `Status:` line; settled
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
  release signing outside the repo; the release check (a side-by-side `.check` build, what ran);
  what the owner does to go public.
- `0.1.0.md` (2026-10-25) — the first release's small cards: the hero reflection (D9.1: mirror
  line inside the band's fade, streaky blur, slow ripple, a switch); starting categories (D10.1:
  the table, unknown and new apps in All apps only); the dock's settings icon (B5.1); all accepted.
- `0.2.0.md` (2026-10-25) — 0.2.0's small cards: the reflection as its own block under a gap
  (D20.1, the owner's sketch; not yet seen); hold Home for standby (D19.1: lock became standby,
  an accessibility service because Android keeps the Home key, four presses of one button to
  wake, triggers not counting); renaming and adding categories (D12.1: ids and names, edit mode
  in the layer, an app in several categories); updates from GitHub (D18.1: a Settings row asked
  when the panel opens, checks before installing, self-update without a confirm; the owner's
  debug build can't take releases).
- `F22-sounds.md` (2026-10-25) — UI sounds: synthesized by a script rather than AI-generated
  (consistency, ours to ship), 12 sounds in one key, three families (Soft the default), on at
  Low, silent in Do Not Disturb (D22.1); all families loaded, media stream, sounds read from
  state before and after each action (D22.2).
- `0.3.0.md` (2026-10-25) — 0.3.0's small cards: art chosen from the Art… page's preview
  (D23.1: Up selects it, the menu rows gone, gallery as a grid tile, the focused thumbnail shown
  in place, the panel wider, Hero/Scene tabs between bumper glyphs; not yet tried); hero or
  scene per app (D24.1: the Scenes setting gone, the glow no longer behind a scene; not yet tried;
  the hero by default, B6.1); the quick panel's player row (D21.1: L1/R1 skip, a seek bar on notification access; the seek
  bar not yet tried); B7: AYN settings as an app and a dock default, the light shadow's
  square corners (a faded layer clips); B8: named Controller settings, a Portal icon of our own;
  what's playing beside the clock (D27.1: title · artist · album, playing only, Android's callbacks);
  the release: versionCode 3, what releasing means.
- `F25-settings.md` (2026-10-25) — Launcher Settings in categories (D25.1): 760 dp from the right
  rather than full screen (Home judged live beside it), the Switch's two columns, focus on the
  categories first, B back out, L1/R1 turn; Look split into Look and Effects so no page scrolls;
  credits on System's page; owner OK. D25.2: Look and Effects one category again (B7.1).
- `F26-setup.md` (2026-10-25) — keeping the setup (D26.1): updates already keep it, an uninstall
  or new signing key doesn't; a setup file in Download saved by hand (no key, gallery art and
  wallpapers inside, downloaded art fetched again), restored through the picker; Android's
  backup limited to the setup prefs (the art pushed it toward the 25 MB limit).
- `0.4.0.md` (2026-10-25) — 0.4.0's small cards: the wake line on standby after a press (D30.1),
  the Fitted big name (D31.1: as written, lighter, sized to fit, one measure), the menus on
  dark gray, a panel color of their own (D32.1), Move in dock from any sheet and a grayed
  *Add to dock (full)* (D33.1), bumpers turn the category, *Home screen play controls* (D34.1),
  the README brought up to date (D29.1), the owner's batch B9 (no art lookups for Android's own
  apps, the smallest-width tip), the stress test (D28.1: the script and how it stays off the rest
  of the device; back at home finished the launcher, every old activity leaked through
  LauncherApps; both fixed; a clean run on the R8 build), the release (versionCode 4, the
  history purged of `concepts/`, the repo public, GitHub release v0.4.0).
- `0.4.1.md` (2026-10-25) — standby's switch (D35.1: why it went off, a force stop; the wanted
  flag and its watch; the row onto Android's list, then App info when restricted; the optional
  adb grant that puts it back by itself); the README names the menu buttons by shape (B10).
