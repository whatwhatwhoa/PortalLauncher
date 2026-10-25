# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). `TASKS.md` is the owner's **feature board**; the owner moves cards in the
FeatureBoard app and a session works the first open card or task under *Working on*.
**0.1.0 — First public release** is in progress: F9 (hero reflection), F10 (starting
categories) and B5 are done and accepted; left are T8.2 (run the release APK) and the VERIFY
rounds. Every decision so far is settled (`decisions/`, *Status* lines). `concepts/` is gone and
gitignored. Everything is committed and pushed. The repo stays private (owner).

## Next
1. T8.2: a side-by-side `releaseCheck` build type (`initWith(release)`, package suffix `.check`)
   so the R8 build runs on the Portal without touching the debug install; walk Home, All apps,
   the layers, the key row, art search and a gallery pick; keep rules for what breaks. It is a
   second Home app, so its first start shows the Home-role dialog: back out without choosing,
   and uninstall the check build at the end. Then the owner makes the repo public (see below).
2. The owner tries *V-reflection* (the darker build) and *V-starting* first, then the older
   VERIFY sections; tune on their asks.

## Waiting on the owner
- Back up the release keystore `C:\Users\Jim\.android\portallauncher-release.jks` together with
  its password (in `local.properties`). If it is lost, users can't update.
- When to make the repo public. Before that: the deleted `concepts/` files (test wallpapers, mock-up
  images) are still in the git history; purge them with a history rewrite if they mustn't be public.
- Quick panel: a swipe down from the top by touch too?
- The VERIFY checks.
- Q-reboot: after a Portal restart, does it still stay landscape and does the rotation app work?
  The owner isn't sure yet; if not, AYN settings may re-pin rotation (`gotchas.md` *Rotation*).
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`
  (agents can't); until then the build command sets it inline.

## Uncommitted
- Nothing.

## Watch out (this week only)
- IDs since the board: new cards F20 on (tasks T<n>.k, decisions D<n>.k), owner batches B6 next;
  D<n>.k up to M8 means the milestone. Cards the owner adds in FeatureBoard come without an ID:
  give one the next F number when it is planned or settled.
- Don't script key presses while the owner is holding the Portal (`gotchas.md` *Scripted keys
  mix with the owner's input*). After any tap, check before A.
- Clear all art and Reset the dock (Launcher Settings) wipe the owner's picks and dock: don't
  press A twice on them while testing.
- Crunchyroll was opened by a stray scripted key (2026-10-25) and sits at its first-run consent
  screen; GameNative was left open after a test. Nothing was accepted in either.
- The Portal's animator duration scale is 0.5 (the owner's setting); put it back to that after
  any animation-off test.
- The owner's trial wallpapers are in the app's `files/backdrops`; they are Backdrop choices.
- Undo Home: Settings → Apps → Default apps → Home → Trebuchet, or
  `adb shell cmd role add-role-holder android.app.role.HOME com.android.launcher3`.
- Undo the launcher's network: `adb shell service call netpolicy 2 i32 10156 i32 262144`, or
  App info → network access.
- Undo a quick-panel grant: App info → Portal → *Modify system settings* off; Settings →
  Notifications → Do Not Disturb access → Portal off.
