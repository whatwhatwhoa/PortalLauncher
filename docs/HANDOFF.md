# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). `TASKS.md` is the owner's **feature board**; the owner moves cards in the
FeatureBoard app and a session works the first open card or task under *Working on*.
**0.1.0 — First public release is closed** and sits under *Released*: the R8 release APK ran on
the Portal through a side-by-side `releaseCheck` build with nothing broken (T8.2,
`decisions/M8-release.md`), and `app/build/outputs/apk/release/PortalLauncher-0.1.0.apk` is
built. The repo is still private and has no GitHub release yet: both are the owner's step.
**0.2.0 — Functional and refinement** is planned (F20, F19, F12, F18, and the VERIFY rounds
carried from 0.1.0); *Working on* is empty until the owner starts it.

## Next
1. When the owner moves 0.2.0 to *Working on*: its first open card (F20 Reflection changes as
   the board stands: more space between the band and the reflection; the owner's concept).
2. The owner tries *V-reflection* and *V-starting* first, then the older VERIFY sections.

## Waiting on the owner
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
- The `releaseCheck` build type, T8.2's notes, the 0.1.0 fold into *Released* with
  `archive/tasks-0.1.0.md`, and the owner's 0.2.0 planning from FeatureBoard (F20 numbered).

## Watch out (this week only)
- IDs: next free card F21 (tasks T<n>.k, decisions D<n>.k), owner batches B6 next. Cards the
  owner adds in FeatureBoard come without an ID: give one the next F number when it is planned.
- Don't script key presses while the owner is holding the Portal (`gotchas.md` *Scripted keys
  mix with the owner's input*); screenshot before every A.
- Clear all art and Reset the dock (Launcher Settings) wipe the owner's picks and dock: don't
  press A twice on them while testing.
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
