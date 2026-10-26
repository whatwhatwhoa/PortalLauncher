# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25, late night):** **0.4.2 — Brightness and the dock** is released (GitHub
`v0.4.2`, versionCode 6): B11 brightness as the Portal counts it, F36 Home dims on the dock
(owner: "This looks good"), B12 portrait letterboxed and a Home crash on resume while standby's
service is off (in 0.4.1 too; found by the release's R8 run). Nothing under *Working on*; the
owner plans next. The Portal runs the 0.4.2 debug build, landscape, auto-rotate off.

## Next
1. The owner plans the next release from the feature list.
2. **VERIFY rounds** T2.3, T5.3, T7.3, T6.5, T35.1, T36.1 need the owner on the Portal.
3. The owner tries *V-0.4.0*, *V-artist*, *V-setup*, *V-b8*, *V-b7*, *V-quick-media*,
   *V-art-preview* (incl. B6.1), *V-sounds*, *V-categories-edit*, *V-standby*, *V-gap*,
   *V-updates* (needs a release build: see F18 below).
4. The promo video (jim-13, outside the repo): when it is on YouTube, add its link under the
   README's hero shot.
## Waiting on the owner
- F36: the dock's hints stay bright while Home dims: right, or dim them too?
- Back up `C:\Users\Jim\.android\portallauncher-release.jks` with its password (in
  `local.properties`). If it is lost, users can't update.
- F18: the daily build is debug-signed, so Updates says "Signed differently" for every release.
  Move the Portal to the release APK (*Save setup*, uninstall, install, *Restore setup*) or keep
  updating by adb? An uninstall turns standby's service off (`gotchas.md`).
- B9: clear the wrong art on Chrome and Calculator: Triangle → Art… → *Use the app's icon* (B9.1).
- F29: read the README; is the accessibility paragraph the right tone (D29.1)?
- F33: *Move in dock* from any sheet (D33.1): enough, or a dock page that lists all six?
- F34: L1/R1's old five-tile jump on Home is gone (categories replace it): fine? The ends stop
  rather than wrap.
- F21: Play / pause in the player line: keep it there? Seek steps 10 s: right? The track title
  on the line itself (it is on the note line now)?
- F23/F24: *Covers on tiles* is still in Launcher Settings (global): move it to Art… too?
- F22: is the move/edge level right now? Sounds on touch taps too?
- F19: is a hold of 0.4 s right? Should L3/R3 count toward waking? They do now.
- The agent's test category **Test** may still be on the Portal: delete it in edit mode.
- Quick panel: a swipe down from the top by touch too?
- Q-reboot: after a Portal restart, does it stay landscape and does the rotation app work?
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`.

## Uncommitted
Nothing.

## Watch out (this week only)
- **The history was rewritten** for the purge (2026-10-25). Any other clone or worktree of the
  repo must be cloned again, not pulled. The pre-purge history is in
  `C:\Users\Jim\.android\PortalLauncher-pre-purge.bundle` (local only, it holds the concepts
  files: never push it).
- **A force stop turns standby's service off** (`gotchas.md`), the restore procedure's
  `am force-stop` included. 0.4.1 puts it back (the Portal has the grant) or says so.
- LineageOS's Home hold action on the Portal is 0 (nothing) since F19's session; only `content
  insert` changes it (`gotchas.md` *The Home key never reaches an app*).
- IDs: next free card F37 (tasks T<n>.k, decisions D<n>.k), owner batches B13 next. Cards the
  owner adds in FeatureBoard come without an ID: give one the next F number when it is planned.
- Don't script key presses while the owner is holding the Portal (`gotchas.md` *Scripted keys
  mix with the owner's input*). The stress script changes the launcher's data: back up and
  restore as D28.1 says. jim-13 reorganized the owner's favorites, dock and categories at their
  request: don't restore an older launcher backup over it.
- Clear all art, Reset the dock and Restore setup wipe the owner's picks, dock or setup: don't
  press A twice on them while testing; the same for Delete in the categories' edit mode.
- The Portal's animator duration scale is 0.5 and its font scale 1.15 (the owner's settings);
  put them back after any test that changes them.
- Undo Home: Settings → Apps → Default apps → Home → Trebuchet, or
  `adb shell cmd role add-role-holder android.app.role.HOME com.android.launcher3`.
- Undo the launcher's network: `adb shell service call netpolicy 2 i32 10156 i32 262144`, or
  App info → network access.
- Undo a quick-panel grant: App info → Portal → *Modify system settings* off; Settings →
  Notifications → Do Not Disturb access → Portal off; the seek bar's: Settings → Notifications →
  notification access → Portal off.
