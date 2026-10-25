# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). `TASKS.md` is the owner's **feature board**; a session works the first open card or
task under *Working on*. **0.1.0**, **0.2.0** and **0.3.0 — Sounds and effects** are under
*Released* (0.2.0 and 0.3.0 without a version bump or release APK; repo still private). 0.3.0
closed with **F27**: title · artist · album after the clock while music plays (seen on the
Portal). **0.4.0 — Stress Test and Prepare for release candidate** is under *Working on*:
F28–F33 (F30–F33 numbered this session) and the VERIFY rounds carried along. Everything is
committed and pushed.

## Next
1. **F28 Stress Test Script**, the first open card of 0.4.0 (only a title so far: ask the owner
   what it should stress, or propose one), then F29–F33 in order.
2. The owner tries *V-artist*, *V-setup*, *V-b8*, *V-b7*, *V-quick-media*, *V-art-preview*
   (incl. B6.1), *V-sounds*, *V-categories-edit*, *V-standby*, *V-gap*.
3. The VERIFY rounds (T2.3, T5.3, T7.3, T6.5). *V-updates* only once the repo is public.
4. `docs/screenshots/settings.jpg` shows the old one-column panel: retake it with F29.

## Waiting on the owner
- F27: title · artist · album beside the clock, a 24 dp bar: right? The scroll's speed (36 dp/s)?
- F21: Play / pause moved from the toggles row into the player line: keep it there? *Enable
  seek* is in sentence case like the rest of the UI (the owner wrote "Enable Seek"). Seek steps
  10 s: right? The track title on the line itself (it is on the note line now)?
- 0.2.0 and 0.3.0 are Released on the board without a version number: want versionCode 3 /
  0.3.0 and a release APK (0.2.0 skipped)? The 0.3.0 state is the commit that released it.
- F23/F24: *Covers on tiles* is still in Launcher Settings (global): move it to Art… too?
- F22: is the move/edge level right now? If it still doesn't feel louder, reshape Soft's move
  rather than add dB. Sounds on touch taps too?
- F18: the daily build is debug-signed, so it says "Signed differently" for every release. Move
  to the release build (now: Save setup, uninstall, install, Restore setup, paste the key) or
  keep updating by adb? A quiet daily check with a dot on the gear, or is asking when Launcher
  Settings opens enough?
- F19: is a hold of 0.4 s right? Softer wording for the accessibility warning in the README?
  Should L3/R3 count toward waking? They do now.
- The agent's test category **Test** may still be on the Portal: delete it in edit mode.
- Going public: make the repo public (first decide on purging the deleted `concepts/` files
  from the history), then the GitHub release v0.1.0 with `PortalLauncher-0.1.0.apk`; an agent
  can tag and upload on request.
- Back up `C:\Users\Jim\.android\portallauncher-release.jks` with its password (in
  `local.properties`). If it is lost, users can't update.
- Quick panel: a swipe down from the top by touch too?
- Q-reboot: after a Portal restart, does it stay landscape and does the rotation app work?
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`.

## Uncommitted
Nothing.

## Watch out (this week only)
- LineageOS's Home hold action on the Portal is 0 (nothing) since F19's session; it was unset
  (Circle to Search). Only `content insert` changes it (`gotchas.md` *The Home key never reaches an app*).
- IDs: next free card F34 (tasks T<n>.k, decisions D<n>.k), owner batches B9 next. Cards the
  owner adds in FeatureBoard come without an ID: give one the next F number when it is planned.
- Don't script key presses while the owner is holding the Portal (`gotchas.md` *Scripted keys
  mix with the owner's input*); screenshot before every A. Check `dumpsys power`
  *lastUserActivityTime* and `dumpsys input` *RecentQueue* first. Launcher Settings opens
  without keys: `adb shell am start -a android.intent.action.APPLICATION_PREFERENCES -n
  io.github.whatwhatwhoa.portallauncher/.MainActivity`.
- Clear all art, Reset the dock and Restore setup wipe the owner's picks, dock or setup: don't
  press A twice on them while testing; the same for Delete in the categories' edit mode.
  Restore setup also empties `files/art/` (downloaded again afterwards).
- Crunchyroll sits at its first-run consent screen and GameNative was left open after a test;
  nothing was accepted in either.
- The Portal's animator duration scale is 0.5 (the owner's setting); put it back after any
  animation-off test.
- `files/backdrops` is empty on the Portal now (the owner's trial wallpapers are gone).
- Undo Home: Settings → Apps → Default apps → Home → Trebuchet, or
  `adb shell cmd role add-role-holder android.app.role.HOME com.android.launcher3`.
- Undo the launcher's network: `adb shell service call netpolicy 2 i32 10156 i32 262144`, or
  App info → network access.
- Undo a quick-panel grant: App info → Portal → *Modify system settings* off; Settings →
  Notifications → Do Not Disturb access → Portal off; the seek bar's: Settings → Notifications →
  notification access → Portal off.
