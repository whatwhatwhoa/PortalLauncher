# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). M3–M5 built, M6 built but for the motion pass (T6.3), M7's quick panel built. New
this session: **Launcher Settings** has all five groups (*Look*, *Home*, *Art*, *Apps*,
*System*); **− (the square) opens the quick panel** (brightness, volume, auto-brightness, Do not
disturb, play/pause, five system shortcuts; brightness and DND ask their grant on first use);
American English everywhere; menu-button hints drawn as square and triangle in discs, hints at
80% and on their button's side; long Home names scroll; the scene now returns after
*Use the app's icon* → re-pick. Latest build installed; the owner's look untouched (White,
System, Black, effects on; **Covers on tiles is Off**, the owner's choice). No VERIFY walked.

## Next
1. The owner tries the quick panel (*V-quick*: grant *Modify system settings* once) and the new
   settings groups (*V-settings-more*), plus *V-b2*; tune on their asks.
2. Agent-doable: T6.3 motion pass. The other open tasks are VERIFY rounds with the owner (T2.3,
   T5.3, T7.3). After them the list moves to the feature board (`TASKS.md` header).

## Waiting on the owner
- Effects (M6): keep, tune or drop each one; they are on by default for this first look.
- Accent favorites (six built); Launcher Settings from + only, or a dock button too?
- Quick panel: text buttons fine, or icons? A swipe down from the top by touch too?
- The first D-pad press after touching the screen is swallowed (`gotchas.md`); fix it as a
  tweak, or leave it?
- Covers in the dock or All apps too? T4.2 put them on Home only (`decisions/M4-art.md`).
- The VERIFY checks; a commit: nothing since the skeleton is committed.
- Q-reboot: after a Portal restart, does it still stay landscape and does the rotation app work?
  If not, AYN settings may re-pin rotation (`gotchas.md` *Rotation*).
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`
  (agents can't); until then the build command sets it inline.

## Uncommitted
- Everything since the skeleton: the font-weight fix, T3.1–T3.4, Selawik (`res/font/selawik_*.ttf`,
  `licenses/`; the other three fonts are `git rm`'d), T4.1–T4.7, B1.1, B2.1–B2.6, M5 categories
  (`Categories.kt`), T6.1, T6.2, T6.4 (`Settings.kt`), T7.2 (`QuickSettings.kt`, the
  `WRITE_SETTINGS` and `ACCESS_NOTIFICATION_POLICY` permissions), and the notes. The owner
  hasn't asked for a commit.

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
