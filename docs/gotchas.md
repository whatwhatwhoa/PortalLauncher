# Gotchas

Traps that already cost time. One entry per trap; newest at the bottom of its section.

## Toolchain

### `sdkmanager` is gone; use the `android` CLI
`sdkmanager.bat` (cmdline-tools 23) prints a deprecation warning, installs only some of the
packages asked for, and hands over to the new `android` binary. Use
`android.exe --no-metrics --sdk=<sdk> sdk install "<pkg>"` and `sdk list` instead. Android
Studio's SDK Manager also failed to load before an SDK existed.

### No ARM64 Windows SDK builds
Google's repository (`repository2-3.xml`) has only `os=windows arch=any` archives (x64 code);
they run under Windows' emulation. Don't hunt for an ARM64 download. (The JDK is the
exception: Temurin ships a native Windows ARM64 build, and that is the one we use.)

### Android Studio's JDK is gone
Android Studio was uninstalled, taking its JBR (`C:\Program Files\Android\Android Studio\jbr`)
with it; old notes and guides point there. The JDK is now
`C:\Users\Jim\AppData\Local\Android\jdk-21`, and `JAVA_HOME` is not set for the user, so set
it in the command (`CLAUDE.md` *Commands*). Agents can't set user environment variables here.

### Lint flags `dispatchKeyEvent` as RestrictedApi
Overriding `ComponentActivity.dispatchKeyEvent` and calling `super` is reported as an error
(androidx annotates its override `@RestrictTo`). It is a public `Activity` method; the override
carries `@SuppressLint("RestrictedApi")`.

### Some automated commands get stuck on a safety check
In Claude Code, adb commands sometimes fail with "classifier gave no verdict"; it is transient.
Retry once, or a shorter command.

## Device

### Rotation
- The accelerometer's axes are 90° off on this LineageOS build: held portrait, it reads as level
  landscape. Auto-rotate therefore turns the screen the wrong way; keep it locked.
- Something on the device had set `wm fixed-to-user-rotation enabled`, likely to hide the sensor
  bug. With it on, apps' orientation requests (and rotation apps) are ignored. It may come back
  after a reboot via AYN's settings: check with `adb shell wm fixed-to-user-rotation`.
- Launcher3 locks the home screen (`SCREEN_ORIENTATION_NOSENSOR`) below 600 dp smallest width;
  our launcher should declare its own orientation and not rely on the sensor.
- From Android 16, apps targeting API 36+ have `screenOrientation` ignored on displays at least
  600 dp in their smallest width, and the Portal at density 288 is exactly 600 dp. Our
  `landscape` therefore does nothing here; landscape holds because it is the panel's natural
  rotation and rotation is locked. Lint warns about it (`DiscouragedApi`); the attribute stays
  for smaller screens.

## Look

### Raster icons look soft on this panel
Console Launcher's dock and theme packs are WebP bitmaps scaled to the layout; at 288 dpi on a
7" 1080p OLED that reads as blur, which is the complaint that started this project. Glyphs are
vector (Material Symbols), app icons are adaptive icons drawn at 1:1, and covers are fetched at
or above the tile's pixel size (160 dp × 1.8 = 288 px, so fetch ≥ 600 px) and never upscaled.
