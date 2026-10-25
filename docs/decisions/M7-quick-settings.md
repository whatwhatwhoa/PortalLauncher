# M7 — Quick settings (planned 2026-10-25; T7.2 built 2026-10-25)

Owner's input, slotted after M5; built as *Built* says. `TASKS.md` M7 holds the tasks; this entry holds
the idea, what Android lets a launcher do, the proposal and what is still open. The settings
*screen* (the launcher's own choices) is M6, `decisions/M6-settings.md`; this is the device's
everyday controls, the ones you reach for in the pull-down.

## The owner's ask (2026-10-25)
"Basic functions if possible like changing the screen brightness, volume, etc. Some of the highly
common stuff you'd do with the pull-down control center, so users can do it with the controller."

## What a launcher may change (Android 17, target SDK 37, checked against this Portal)
| Control | Can we? | How |
|---|---|---|
| Media volume | Yes, no permission | `AudioManager.setStreamVolume(STREAM_MUSIC)` |
| Brightness, auto-brightness | Yes, after a one-time grant | `Settings.System` `screen_brightness` (now 13 of 255, manual), *Modify system settings* access (`ACTION_MANAGE_WRITE_SETTINGS`) |
| Play / pause / next | Yes, no permission | `AudioManager.dispatchMediaKeyEvent`; showing *what* is playing needs notification access, left out |
| Do not disturb | Yes, after a one-time grant | `NotificationManager.setInterruptionFilter`, *Do Not Disturb access* |
| Wi-Fi, Bluetooth on/off | No (Android 10 / 13 took it from apps) | open the system's Wi-Fi or Bluetooth panel (`Settings.Panel`), state shown on the tile |
| Battery saver, airplane mode | No (system or secure settings) | open the setting page; battery % and charging are already ours |
| Rotation | Not usefully | the accelerometer reads 90° off (`gotchas.md` *Rotation*); a tile opens the owner's rotation app |
| Performance and fan modes | No public setting | AYN keeps them in its own app (`org.lineageos.settings.ayn`), no keys in Android's settings; a tile opens it |
| Screen off, the notification shade | Only with an accessibility service | left out (the shade is already on *Later*) |

**It lives in the launcher, so it works on Home and All apps, not over a game.** Showing it over
another app would need an overlay plus an accessibility service to catch the button; that is a
different, heavier feature (on *Later* if the owner wants it).

## Proposal
- **A quick panel from the top**, like the pull-down, over the screen dimmed as the options sheet
  dims it. Row 1: brightness and volume sliders (left/right change them in steps, held repeats).
  Row 2: round toggles: Auto-brightness, Do not disturb, Play/pause. Row 3: shortcuts that open
  the system's page: Wi-Fi, Bluetooth, Battery, AYN settings, Android settings. Up and down move
  between rows, A toggles or opens, B closes.
- **Key:** − (Select), which does nothing on Home or All apps today, so there is no clash; also a
  swipe down from the top edge by touch. Hinted on Home (− Quick settings).
- **Brightness feels even:** the slider maps through a curve (as Android's own does), so each step
  looks like the same change; the raw value is linear and the low end matters on an OLED.
- **One-time grants are asked when first used,** not on install: the first brightness change
  opens Android's *Modify system settings* page for the launcher, with a line saying why; until
  granted, the slider row says so. The same for Do not disturb.

## Open (owner, answered 2026-10-25: see *Owner's answers*)
- − for the panel, or another button? A dock button too?
- Which controls are in: the list above, fewer, or others (screen timeout, vibration)?
- OK to ask for *Modify system settings* and *Do Not Disturb access* when first used?
- Over games too (the overlay plus accessibility service), or Home only for now?

## Owner's answers (2026-10-25)
"Make square a quick button to system adjustments (brightness, volume etc.)": − (the square,
Select) opens it, the proposed controls stand. The grants are asked on first use, as proposed;
Home and All apps only (no overlay over games).

## Built (T7.2, 2026-10-25)
Built by jim-d6; opened on the device once over adb (screenshot) and closed, no grant given, no
value changed. Not clicked by the owner.
- **As proposed, with text buttons instead of icons.** The toggles and shortcuts are round-ended
  48 dp buttons with their names: drawing nine clear icons is a task of its own, and names read
  at once. Icons can come later if the owner wants the row shorter.
- **One row per slider**, then a row of toggles, a row of shortcuts, and a muted line that says
  what the focused control still needs (the grant) — the line is empty otherwise.
- **Brightness** through a 2.2 curve over the legacy 0–255 `screen_brightness`, 5% of the slider a
  step; moving it switches auto-brightness off, as the pull-down does. Volume is media volume,
  one system step a press; both read as percentages.
- **Play / pause** dispatches the media key; it shows as on while `isMusicActive`, read again
  0.4 s after a press (a player takes a moment). Showing *what* plays still needs notification
  access, left out.
- **AYN** opens `org.lineageos.settings.ayn/.AynSettingsActivity` by component: it has no
  launcher entry, only Settings' injection filter, which makes it exported.
- **The swipe down from the top** isn't built: the panel is the controller's; touch has the
  real pull-down a swipe away. A dock button wasn't asked for.
- The manifest declares `WRITE_SETTINGS` and `ACCESS_NOTIFICATION_POLICY`; neither does
  anything until the owner flips it on in Android's page, which A on the control opens.

## Icons (B3.3, owner, 2026-10-25)
The owner asked for icons "similar to Android 17 icons" instead of the text buttons.
- **Material Symbols, Rounded, filled, weight 400**: Google's icon set, the one Android's own
  quick settings is drawn from; Apache 2.0 (`licenses/MaterialSymbols-Apache-2.0.txt`, credits
  line). Fetched as SVG from fonts.gstatic.com and turned into vector drawables
  (`res/drawable/ic_sym_*`, viewport 960 shifted down 960), so they stay crisp at any size.
  Not pulled from the Portal's SystemUI: its drawables are compiled and LineageOS's may differ.
- **Which:** brightness `light_mode` (a sun), volume `volume_up`, `brightness_auto`,
  `do_not_disturb_on`, `play_arrow` / `pause` (pause while music plays), `wifi`, `bluetooth`,
  `battery_android_full`, AYN `sports_esports` (a gamepad: its page is the handheld's
  performance and controller settings), Android settings `settings` (a gear, also the dock's
  Launcher Settings button).
- **Icon only, names on the note line:** round 56 dp buttons like the dock's; with no names the
  rows are short, and the focused button is named on the muted line when nothing is missing.
  The slider rows lose their names to the icon too, as Android's slider shows only its sun.
