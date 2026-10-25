# Handoff

Current state only. **Rewrite this file at the end of every session**: carry forward what is
open, delete what is resolved, never append history (git and `docs/decisions/` hold it).

**State (2026-10-25):** The skeleton builds and passes lint but has **never run on the
device** (the Portal wasn't on adb). It is a Home app with the owner's bundle: a favourites
carousel, an All apps drawer (Y, the dock's first button, or swipe up), a dock of utility apps,
the key table (A bottom), the clock, light/dark from the system, and a font and accent trial
(X and − on Home). Icons are drawn 1:1 from their xxxhdpi layers inside our own masks
(`decisions/M2-skeleton.md`). Toolchain: native ARM64 JDK 21, Gradle 9.8.2, AGP 9.4.1; Android
Studio is gone. M3/M4 were reshaped: covers parked in *Later*, an overlay/gradient pass added.

## Next
1. T2.3: connect the Portal, install, walk `VERIFY.md` *V-skeleton* with the owner, fix what
   breaks. Expect first-run tuning: the row's pivot offset, the title position, the dock label.

## Waiting on the owner
- Q-reboot: after a Portal restart, does it still stay landscape and does the rotation app work?
  If not, AYN settings may re-pin rotation (`gotchas.md` *Rotation*).
- Q-odinlite: `concepts/` has `Odinlite-OdinRomLauncher-α1-8-release.apk.1`, not reviewed.
  What should we look at in it? (Kept out of git as a third-party APK.)
- Q-javahome: set `JAVA_HOME` for the user to `C:\Users\Jim\AppData\Local\Android\jdk-21`
  (agents can't); until then the build command sets it inline.

## Uncommitted
- Nothing, once this session's commit is pushed to `whatwhatwhoa/PortalLauncher` (private).

## Watch out (this week only)
- The SDK tools moved to the new `android` CLI; old guides say `sdkmanager` (`gotchas.md`).
