# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). `TASKS.md` is the owner's **feature board**; a session works the first open card or
task under *Working on*. **0.1.0** is released locally (repo still private, no GitHub release:
the owner's step). **0.2.0 — Functional and refinement**: F20, F19, F12 and **F18** are ticked;
the VERIFY rounds are left. F18 (Launcher Settings → System → Updates) builds, lints and ran
end to end on the Portal in a side-by-side test copy against a laptop stand-in; it is
installed on the owner's Portal (with F22). F19's fix and F12's checklist and ring are
installed, not yet tried by the owner. **F22 sounds built + installed 2026-10-25, not yet
heard on the Portal (VERIFY V-sounds)**; the Soft family, picked by the owner from
`tools/sounds/make_sounds.py` → `build/sounds/audition.html`.

## Next
1. The owner tries *V-categories-edit*, the F19 fix (*V-standby*) and looks at *V-gap*.
2. 0.2.0's VERIFY rounds (T2.3, T5.3, T7.3, T6.5), *V-reflection* and *V-starting* first; then
   the owner closes 0.2.0 (versionCode 2, the release APK, `decisions/M8-release.md`).
3. *V-updates* only once the repo is public and a release is up.

## Waiting on the owner
- F18: GitHub has nothing to offer until the repo is public and a release has its APK (now:
  "None published"). The daily build is debug-signed, so it will say "Signed differently" for
  every release: move to the release build (an uninstall, so favorites, dock and art need
  moving first; an agent can script that) or keep updating by adb. A quiet daily check with a
  dot on the gear, or is asking when Launcher Settings opens enough?
- F22: touch taps are silent except launch: want sounds on touch too?
- The agent's test category **Test** may still be on the Portal: delete it in edit mode (+ in the
  layer, A on Test, Delete, A twice).
- F19: is a hold of Android's long-press time (0.4 s here) right, or should it be longer?
  Does the service's accessibility warning need softer wording in the README when it ships?
  Should the stick buttons (L3/R3) count toward waking? They do now.
- Going public: make the repo public (first decide on purging the deleted `concepts/` files
  from the git history), then the GitHub release v0.1.0 with `PortalLauncher-0.1.0.apk`.
  An agent can tag and upload the release on request.
- Back up the release keystore `C:\Users\Jim\.android\portallauncher-release.jks` together with
  its password (in `local.properties`). If it is lost, users can't update.
- Quick panel: a swipe down from the top by touch too?
- Q-reboot: after a Portal restart, does it still stay landscape and does the rotation app work?
  If not, AYN settings may re-pin rotation (`gotchas.md` *Rotation*).
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`
  (agents can't); until then the build command sets it inline.

## Uncommitted
- F20, F19 (and its trigger and same-button fix), F12, F18 (code, `src/debug/` network config,
  `decisions/0.2.0.md`, architecture, gotchas, VERIFY, INDEX, README), 0.2.0 moved to *Working
  on*, F21 and F24 numbered. F22 (`tools/sounds/`, `assets/sounds/`, `Sounds.kt`, edits in
  LauncherState/Settings/AppCatalog/MainActivity, `decisions/F22-sounds.md`). Everything before
  is pushed (`be74a98`).

## Watch out (this week only)
- LineageOS's Home hold action on the Portal is 0 (nothing) since F19's session; it was unset
  (Circle to Search). Only `content insert` changes it (`gotchas.md` *The Home key never reaches an app*).
- IDs: next free card F25 (tasks T<n>.k, decisions D<n>.k), owner batches B6 next. Cards the
  owner adds in FeatureBoard come without an ID: give one the next F number when it is planned.
- Don't script key presses while the owner is holding the Portal (`gotchas.md` *Scripted keys
  mix with the owner's input*); screenshot before every A. Check `dumpsys power`
  *lastUserActivityTime* and `dumpsys input` *RecentQueue* first: it caught the owner again
  mid-test (2026-10-25).
- Clear all art and Reset the dock (Launcher Settings) wipe the owner's picks and dock: don't
  press A twice on them while testing; the same for Delete in the categories' edit mode.
- Crunchyroll sits at its first-run consent screen and GameNative was left open after a test
  (2026-10-25); nothing was accepted in either.
- The Portal's animator duration scale is 0.5 (the owner's setting); put it back to that after
  any animation-off test.
- The owner's trial wallpapers are in the app's `files/backdrops`; they are Backdrop choices.
- Undo Home: Settings → Apps → Default apps → Home → Trebuchet, or
  `adb shell cmd role add-role-holder android.app.role.HOME com.android.launcher3`.
- Undo the launcher's network: `adb shell service call netpolicy 2 i32 10156 i32 262144`, or
  App info → network access.
- Undo a quick-panel grant: App info → Portal → *Modify system settings* off; Settings →
  Notifications → Do Not Disturb access → Portal off.
