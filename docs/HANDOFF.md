# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-30, morning):** **0.5.0 — Steady and quick** is built, under *Working on*, all
cards ticked (F37–F45 with F11 Recent, F13 Notifications, F16 Search on Y; the session's own
pick from an audit, `decisions/0.5.0.md`). Merged from four lanes, reviewed again as a whole
(13 low findings fixed), lint clean but the old warning. Not released: versionName is still
0.4.2. The Portal runs this build (installed 10:34, standby's service on, the grant kept),
landscape, auto-rotate off: it had been found on and stuck in portrait; `user_rotation` set back
to 0 at the owner's ask (`gotchas.md` *Rotation*). Everything is
committed locally on `main` and not pushed (`git log origin/main..main`). `architecture.md` was
split: what each screen does is now `docs/behavior.md`.

## Next
1. The owner tries **`VERIFY.md` *V-0.5.0*** on the Portal, the stick and standby over a game
   first (the agent could only drive keys over adb and a virtual Home key).
2. Then release 0.5.0 on the owner's word: versionCode 7, versionName 0.5.0, the release APK
   (`TASKS.md` rules), the R8 check build run fresh first (`decisions/0.4.2.md` lesson); "commit
   to github" and the GitHub release are the owner's to ask for.
3. Older VERIFY rounds T2.3, T5.3, T7.3, T6.5, T35.1, T36.1 (*Candidates*) still need hands.
4. The promo video (jim-13): when it is on YouTube, add its link under the README's hero shot.

## Waiting on the owner
- 0.5.0 Recent: second after Favorites and named *Recent*: right? Play Store stays out (a dock
  utility): right? (D11.1)
- 0.5.0 Home key: its first press now only closes what is open and keeps the row's place; a
  second goes to the start (D41.1 P5). Good?
- 0.5.0 the quick panel's bell: with standby's service off it uses a hidden Android call; keep it,
  or show it only while the service is on? (D13.1)
- 0.5.0 the stick: speed, the half-push threshold, the acceleration (D40.1).
- 0.5.0 L1/R1 glyphs beside the category name (only with Button hints on): keep? (D42.1 E9)
- P11 (not built): remember the place in each category instead of its row from the start?
- F36: the dock's hints stay bright while Home dims: right, or dim them too?
- Back up `C:\Users\Jim\.android\portallauncher-release.jks` with its password (in
  `local.properties`). If it is lost, users can't update.
- F18: the daily build is debug-signed, so Updates says "Signed differently" for every release:
  move the Portal to the release APK (*Save setup*, uninstall, install, *Restore setup*) or keep
  updating by adb? An uninstall turns standby's service off (`gotchas.md`).
- B9: clear the wrong art on Chrome and Calculator: Triangle → Art… → *Use the app's icon*.
- Older questions, unchanged: F29 README tone (D29.1); F33 *Move in dock* from any sheet (D33.1);
  F34 L1/R1's old five-tile jump gone; F21 Play/pause in the player line, 10 s seeks; F23/F24
  *Covers on tiles* in Art… too; F22 sound levels, sounds on taps; F19 the 0.4 s hold, L3/R3 to
  wake; a swipe down by touch for the quick panel; Q-reboot (landscape after a restart);
  Q-javahome (`JAVA_HOME` for the user); the agent's test category **Test** may still be there.

## Uncommitted
Nothing.

## Watch out (this week only)
- **The history was rewritten** for the purge (2026-10-25): other clones must be cloned again. The
  pre-purge bundle `C:\Users\Jim\.android\PortalLauncher-pre-purge.bundle` holds the concepts
  files: never push it.
- **Testing a Home hold** over adb needs a virtual key device (`gotchas.md` *Testing a Home
  hold*); `input keyevent` never reaches standby's service.
- **A force stop turns standby's service off** (`gotchas.md`); the Portal has the grant, so the
  launcher puts it back.
- LineageOS's Home hold action on the Portal is 0 (nothing); only `content insert` changes it.
- IDs: next free card F46 (tasks T<n>.k, decisions D<n>.k), owner batches B13 next. Cards the
  owner adds in FeatureBoard come without an ID: give one the next F number when it is planned.
- Don't script keys while the owner holds the Portal (`gotchas.md` *Scripted keys mix with the
  owner's input*): check `dumpsys input` *RecentQueue* first. The Portal's animator scale is 0.5
  and its font scale 1.15 (the owner's); put them back after a test that changes them.
- Clear all art, Reset the dock, Restore setup and Delete in edit mode wipe the owner's choices:
  never two A's on them while testing.
- Undo Home: Settings → Apps → Default apps → Home → Trebuchet, or
  `adb shell cmd role add-role-holder android.app.role.HOME com.android.launcher3`.
- Undo the launcher's network: `adb shell service call netpolicy 2 i32 10156 i32 262144`, or
  App info → network access.
- Undo a quick-panel grant: App info → Portal → *Modify system settings* off; Do Not Disturb
  access → Portal off; notification access → Portal off.
