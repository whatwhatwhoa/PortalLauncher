# M3 — Home screen v1 (2026-10-25)

Choices made while building M3. `architecture.md` holds the result.

## T3.1 Status (2026-10-25)
The owner's ask: clock, Wi-Fi and battery "slightly tighter towards the corner"; Wi-Fi "the more
modern version with slightly softer corners"; battery "a bit bigger/longer with %".
- **Tighter:** 24 dp top and 32 dp side instead of the content's 32 and 48. The status band on
  Home keeps its old height, so the carousel did not move.
- **Second ask (2026-10-25):** the clock to the top-left corner, Wi-Fi and battery staying
  top-right, and the whole status ~5 px tighter into the corners: 21 dp top, 29 dp side (3 dp is
  5.4 px at density 288). All apps' title moved down below the status band, as Home's content
  already sits, since the clock now shares its corner.
- **Third ask (2026-10-25):** halve the distance again: 10.5 dp top, 14.5 dp side (19 and 26 px).
  Off the 4 dp scale on purpose: the owner tunes this by eye on the device.
- **Drawn once at the root,** over Home and All apps, instead of a clock in each header: it no
  longer jumps when All apps slides in. All apps' title stays at the content margins, so it sits
  a little lower than the clock; fine at that distance.
- **Wi-Fi** is the filled wedge Android 12+ uses, not the old ring arcs, drawn as one path:
  each corner is a true fillet (the outer ones turned in by asin(r / (R − r)) so they meet the
  arc tangentially), not `CornerPathEffect`, which rounds only between straight segments.
  16 dp looked small beside the battery; 18 dp.
- **Level:** the RSSI from the Wi-Fi network's capabilities, through `WifiManager`'s own
  `calculateSignalLevel`, scaled to four steps. Needs only `ACCESS_NETWORK_STATE`; no location
  permission, because the SSID is not shown. First run: −70 dBm on 6 GHz showed two of four.
- **Battery:** % inside a longer body (the modern Android and iOS style), so the number needs
  no room of its own. The digits are drawn twice, clipped: background color over the fill, text
  color over the track, so they read at any level. Charging fills in the accent; a low-battery
  color is left out for now.
- **Fonts, asked the same day:** Segoe UI is Microsoft's, licensed with Windows only, so it
  can't ship in an APK (least of all an open-source one). Its open look-alike is Microsoft's
  Selawik (OFL, static weights); offered to the owner as a fourth trial font.

## Legacy icons (owner asked, 2026-10-25)
NTE's tile showed a small icon on gray. It is not adaptive: its biggest icon is
`drawable-xxxhdpi/icon.png`, 192×192 px with the corners already rounded. A 160 dp tile is
288 px, and the 16% legacy inset leaves 196 px, so it already draws about 1:1; filling the tile
would upscale it 1.5× (soft, against rule 4). The fix is art (M4), not the renderer.

## Owner answers (2026-10-25)
- **Odinlite APK:** an old launcher with a few tools; kept in `concepts/` only in case we need
  something from it later. No review.
- **Hero band height:** the dock moves down into the bottom row with the button hints, all
  bottom-aligned, rather than cropping the hero hard or shrinking the tiles (T4.3).

## T3.2 Options (2026-10-25)
- **A side panel, not a popup by the tile:** the same place every time, room for the app's name,
  and it leaves the row visible under the dim. Its own A/B hints, because the hint bar is dimmed.
- **Move is a mode, not "move left / move right" rows:** pick up, shift with the D-pad, put
  down, the console way; one row in the sheet instead of two. Only favorites move; the
  fallback row is alphabetical, so it offers Favorite instead.
- **The dock is the owner's list once changed:** stored as keys, null until the first edit, so a
  fresh install still gets the utility defaults. Capped at six apps (seven buttons) so it still
  fits beside the hints when T4.3 moves it into the bottom row.
- **Hidden apps come back through the sheet** (*Show hidden apps*, faded), not a separate screen
  or setting: no new place to learn. Not persisted, so they hide again on restart.
- **Uninstall** uses the system's dialog (`ACTION_DELETE`, `REQUEST_DELETE_PACKAGES`), offered
  only for apps outside the system image, whose updates can't be uninstalled from here.
- **Selawik** (the owner chose it over loading a Segoe UI file): static files, so the trial
  enum now builds each family itself; Medium maps to Regular as in Segoe UI.
- **First run:** the sheet opened over adb on the fallback row. The owner was using the Portal at
  the same time, so the scripted move test mixed with their input; they own the result.

## T3.3 Scaling, font scale, motion, launch (2026-10-25)
- **Scaling is one density override at the root,** not a factor multiplied into each `Dimens`
  token: every dp scales with it, including the literals in `Status.kt` and borders, so none
  can be missed. The factor fits the canvas by height and by the width the All apps grid needs
  (1032 dp), so a 4:3 screen narrows instead of overflowing. It is rounded to hundredths: the
  Portal's height in dp comes out a hair off 600 in float math, and a factor of 1.0000001
  would shift every icon off 1:1. Checked on the device: the focused tile is 311 px (288 × 1.08).
- **The font scale clamp lives in the same override** (0.9–1.15). Three places used
  `lineHeight.value.dp`, which ignores the font scale; they now use `asDp`.
- **Animation-off is read from the system, explicitly.** Compose already shortens some
  animations by the duration scale, but not the infinite pulse; a `LocalMotion` flag makes every
  case visible in code. `ValueAnimator.areAnimatorsEnabled()` plus a `ContentObserver` on
  `ANIMATOR_DURATION_SCALE`, because Home is never restarted. Not tested at 0: changing that
  setting is the owner's call (rule 1); the device runs at 0.5.
- **Launch: clip reveal, not scale-up.** `makeClipRevealAnimation` grows a window-shaped clip
  out of the tile, so the app's content never stretches; scale-up distorts it. Launcher3's own
  launch uses remote animations, which need a system permission. The tile rect comes from each
  tile's layout (`launchesFrom`), stored outside Compose state because only a launch reads it.
  Not yet seen: the owner was using the Portal, so no keys were sent.
- **Selawik 1.3× (owner, same day):** it read small beside the others. Each trial font now
  carries a size factor; sizes and line heights scale together, so nothing clips.
- **Font picked: Selawik at 1.15× (owner, same day):** 1.3 overshot; the owner put it at about
  1.1–1.15 and chose 1.15's end. It is the default now; the other fonts and the X key stay
  until the accent is picked too (T3.4), since a swap is easy ("we'll go with it for now").
