# Toolchain and device

How the launcher is built and the device it runs on. Split from `architecture.md` (2026-10-25);
traps: `gotchas.md`.

## Toolchain

- **Language and UI:** Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00), activity-compose 1.13.0.
- **Build:** Gradle 9.8.2 (wrapper), Android Gradle Plugin 9.4.1, which compiles Kotlin itself;
  `compileSdk`/`targetSdk` 37, `minSdk` 31. Application id
  `io.github.whatwhatwhoa.portallauncher`.
- **Build types:** `debug` (the owner's daily build, art key from `local.properties`),
  `release` (R8-minified, resources shrunk, release-signed, no key: what users get) and
  `releaseCheck` (`assembleReleaseCheck`: the release build under the package `….check`, so the
  R8 output runs beside the debug install; it carries the owner's key and is never published).
  The check build is a second Home app (back out of its Home-role ask); uninstall it after.
  It can take the Home role on the way, and its uninstall leaves none (Home shows Android's
  chooser): `adb shell cmd role add-role-holder android.app.role.HOME io.github.whatwhatwhoa.portallauncher`.
- **SDK:** `C:\Users\Jim\AppData\Local\Android\Sdk` (`ANDROID_HOME` set for the user):
  platform `android-37.0` (Android 17), build-tools 37.0.0, platform-tools 37.0.1,
  cmdline-tools 23.0. Packages are managed with `cmdline-tools\latest\bin\android.exe sdk`.
- **Java:** Temurin JDK 21 for Windows ARM64 (native), at
  `C:\Users\Jim\AppData\Local\Android\jdk-21`; `JAVA_HOME` must point at it.
- **Host:** Windows 11 on ARM (Surface Laptop 7). Google ships only x64 Windows builds of the
  SDK tools; they run under x64 emulation. No Android emulator here: the Portal is the test
  device, over USB adb. No Android Studio.
- **Font:** Selawik's static Regular, Semibold and Bold from Microsoft's GitHub release 1.01, in
  `res/font/`; its OFL license in `licenses/`.

## The device

AYN Odin 2 Portal (`odin2portal`), Snapdragon 8 Gen 2, 7" OLED 1920×1080, running LineageOS on
Android 17 (build `CP2A.260605.016`) with AYN's settings page (*Controller settings* here, see `architecture.md` *App catalog*).
Portal is its Home app; stock Launcher3 stays as the fallback.

- **Natural orientation is landscape** (rotation 0 = 1920×1080); the panel itself is mounted
  portrait (`ro.surface_flinger.primary_display_orientation=ORIENTATION_90`).
- **Density 288** (physical 360; LineageOS default 314): smallest width 600 dp, tablet layouts. Undo: `adb shell wm density 314`.
- **Rotation is locked to landscape** (`accelerometer_rotation=0`, `user_rotation=0`), rotation
  suggestions are off, fixed-to-user-rotation is `default`. The
  accelerometer reports gravity 90° off, so the sensor can't drive rotation; portrait comes from
  apps that ask for it or from a rotation app's tile. See `gotchas.md` *Rotation*.
