# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The launcher **runs on the Portal and is its Home app** (Launcher3 still
installed). M3–M6 built, M7's quick panel built. New from jim-f6: **eight accents and a
hue/intensity picker** on A at Settings → Accent (B4.1), and the **motion pass** (T6.3). New
from jim-7a: the **GitHub page** (T8.1). `README.md` is for users, with 8 screenshots and an
MIT `LICENSE`. There is a **SteamGridDB key row** in Settings → Art (it reads the clipboard),
release APKs carry no key, and release signing uses a keystore outside the repo. **The repo stays
private** (owner). Latest debug build installed, with both; the owner's look: White, System,
Black, effects on, Covers on tiles On (seen 11:34). No VERIFY walked.

## Next
1. The owner tries *V-artkey*, *V-accent*, *V-transit*, *V-b3*, *V-quick*, *V-settings-more*,
   *V-b2*; tune on their asks.
2. T8.2 before going public: run the release APK once (it can't go over the debug install; see
   `decisions/M8-release.md` *Left*), then the owner flips the repo public and publishes v0.1.0.
3. The other open tasks are VERIFY rounds (T2.3, T5.3, T6.5, T7.3). After them the list moves
   to the feature board (`TASKS.md` header).

## Waiting on the owner
- Back up the release keystore `C:\Users\Jim\.android\portallauncher-release.jks` together with
  its password (in `local.properties`). If it is lost, users can't update.
- How to test the release APK: a second device, or move the Portal to release builds (that
  means uninstalling the debug one and losing favorites, dock and art once).
- When to make the repo public.
- Quick panel: a swipe down from the top by touch too?
- The VERIFY checks.
- Q-reboot: after a Portal restart, does it still stay landscape and does the rotation app work?
  The owner isn't sure yet; if not, AYN settings may re-pin rotation (`gotchas.md` *Rotation*).
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`
  (agents can't); until then the build command sets it inline.

## Uncommitted
- Everything since e37f8bf: jim-f6's accent picker + motion pass and jim-7a's GitHub page + key
  row. It builds and lints clean together; jim-f6 meant to commit and push it but ended first.
  Commit message notes: release APKs carry no SteamGridDB key and are signed from a keystore
  outside the repo; the release APK builds but has never run on a device.

## Watch out (this week only)
- Don't script key presses while the owner is holding the Portal: input mixes (it happened once).
  `adb shell dumpsys input` *RecentQueue* shows recent input; one of its queues is stale (its
  ages don't move between two reads), so compare two reads. After any tap, check before A.
- Clear all art and Reset the dock (Launcher Settings) wipe the owner's picks and dock: don't
  press A twice on them while testing.
- The 2026-10-25 screenshot session left Home on Favorites; the owner had it on Entertainment.
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
