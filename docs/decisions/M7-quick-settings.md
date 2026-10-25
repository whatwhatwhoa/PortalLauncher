# M7 — Quick settings (2026-10-25)

The device's everyday controls from the controller, the ones you reach for in the pull-down
(the launcher's own choices are `M6-settings.md`). How it works now: `architecture.md` *Quick
panel*.

Status: as built 2026-10-25, owner to confirm (*V-quick*, *V-b3*).

## The owner's ask (2026-10-25)
"Basic functions if possible like changing the screen brightness, volume, etc. Some of the highly
common stuff you'd do with the pull-down control center, so users can do it with the controller."

## What a launcher may change (Android 17, target SDK 37, checked against this Portal)
| Control | Can we? | How |
|---|---|---|
| Media volume | Yes, no permission | `AudioManager.setStreamVolume(STREAM_MUSIC)` |
| Brightness, auto-brightness | Yes, after a one-time grant | `Settings.System` `screen_brightness` (13 of 255, manual, when checked), *Modify system settings* access (`ACTION_MANAGE_WRITE_SETTINGS`) |
| Play / pause / next | Yes, no permission | `AudioManager.dispatchMediaKeyEvent`; showing *what* is playing needs notification access, left out |
| Do not disturb | Yes, after a one-time grant | `NotificationManager.setInterruptionFilter`, *Do Not Disturb access* |
| Wi-Fi, Bluetooth on/off | No (Android 10 / 13 took it from apps) | open the system's Wi-Fi or Bluetooth panel (`Settings.Panel`) |
| Battery saver, airplane mode | No (system or secure settings) | open the setting page; battery % and charging are already ours |
| Rotation | Not usefully | the accelerometer reads 90° off (`gotchas.md` *Rotation*); a tile opens the owner's rotation app |
| Performance and fan modes | No public setting | AYN keeps them in its own app (`org.lineageos.settings.ayn`), no keys in Android's settings; a tile opens it |
| Screen off, the notification shade | Only with an accessibility service | left out (the shade is a card on the board, F13) |

**It lives in the launcher, so it works on Home and All apps, not over a game.** Showing it over
another app would need an overlay plus an accessibility service to catch the button; that is a
different, heavier feature (to the feature list if the owner wants it).

## Owner's answers (2026-10-25)
Status: accepted 2026-10-25.
"Make square a quick button to system adjustments (brightness, volume etc.)": − (the square,
Select) opens it; the proposed controls stand (sliders for brightness and volume; toggles for
auto-brightness, Do not disturb, play/pause; shortcuts to Wi-Fi, Bluetooth, Battery, AYN
settings, Android settings). The grants are asked on first use, not on install; Home and All
apps only (no overlay over games). Still open: a swipe down from the top by touch too.

## Choices (T7.2, 2026-10-25)
- **Brightness feels even:** the slider maps through a 2.2 curve over the legacy 0–255
  `screen_brightness` (as Android's own does), so each step looks like the same change; the raw
  value is linear and the low end matters on an OLED. Moving it switches auto-brightness off, as
  the pull-down does.
- **Play / pause** shows as on while `isMusicActive`, read again 0.4 s after a press (a player
  takes a moment).
- **AYN** opens `org.lineageos.settings.ayn/.AynSettingsActivity` by component: it has no
  launcher entry, only Settings' injection filter, which makes it exported.
- **No swipe down from the top:** the panel is the controller's; touch has the real pull-down a
  swipe away. A dock button wasn't asked for.
- The manifest declares `WRITE_SETTINGS` and `ACCESS_NOTIFICATION_POLICY`; neither does
  anything until the owner flips it on in Android's page, which A on the control opens.

## Icons (B3.3, owner, 2026-10-25)
The owner asked for icons "similar to Android 17 icons" instead of the first build's text
buttons.
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
- **Icon only, names on the note line:** with no names the rows are short, and the focused
  button is named on the muted line when nothing is missing. The slider rows lose their names to
  the icon too, as Android's slider shows only its sun.
