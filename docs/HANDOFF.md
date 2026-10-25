# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). M3–M5 built, M6 built but for the motion pass (T6.3), M7's quick panel built.
From the owner's answers (B3): effects settled as built; a **gear at the dock's end** opens
Launcher Settings; the **quick panel is icons** (Material Symbols, the focused one named
below); the **first D-pad press after a touch now counts**; covers stay Home only. All
committed and pushed. Latest build installed; the owner's look untouched (White, System,
Black, effects on, Covers on tiles Off). No VERIFY walked.

## Next
1. The owner tries *V-b3* (dock gear, icon panel, D-pad after a touch), *V-quick* (grant
   *Modify system settings* once), *V-settings-more* and *V-b2*; tune on their asks.
2. Agent-doable: T6.3 motion pass. The other open tasks are VERIFY rounds with the owner (T2.3,
   T5.3, T7.3). After them the list moves to the feature board (`TASKS.md` header).

## Waiting on the owner
- Accent favorites (six built; the rest go).
- Quick panel: a swipe down from the top by touch too?
- The VERIFY checks.
- Q-reboot: after a Portal restart, does it still stay landscape and does the rotation app work?
  The owner isn't sure yet; if not, AYN settings may re-pin rotation (`gotchas.md` *Rotation*).
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`
  (agents can't); until then the build command sets it inline.

## Uncommitted
- Nothing.

## Watch out (this week only)
- Don't script key presses while the owner is holding the Portal: input mixes (it happened once).
  `adb shell dumpsys input` *RecentQueue* shows recent input; one of its queues is stale (its
  ages don't move between two reads), so compare two reads. After any tap, check before A.
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
