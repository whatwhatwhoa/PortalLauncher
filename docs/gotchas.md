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

### Restricted networking blocks new apps
LineageOS's *Restricted networking mode* is on on the Portal (`settings get global
restricted_networking_mode` is 1): a newly installed app, or one that newly asks for INTERNET,
gets `REJECT_ALL` and every lookup fails as `UnknownHostException` ("No address associated with
hostname") while `adb shell ping` works. Check with `adb shell dumpsys netpolicy | grep <uid>`.
Android's log drops the stack of an `UnknownHostException`, so log the exception's text too.
It is a device setting: change it only when the owner asks. Per app, App info → network
access, or over adb (no `cmd netpolicy` verb exists): `service call netpolicy 3 i32 <uid> i32
262144` removes REJECT_ALL (`removeUidPolicy`), `... 2 ...` adds it back (`addUidPolicy`), and
`service call netpolicy 4 i32 <uid>` reads it (0x00040000 = blocked). The uid is in
`cmd package list packages -U <package>`. Transaction codes follow `INetworkPolicyManager.aidl`;
check them with the read (code 4) before writing. Survives a reinstall (`install -r`).

### Git Bash rewrites device paths
In the Bash tool, `adb push x /sdcard/...` or `adb shell ls /data/...` gets the path turned into
`C:/Program Files/Git/sdcard/...` and fails ("secure_mkdirs() failed"). Prefix the command with
`export MSYS_NO_PATHCONV=1;`. Files for the app's private storage: push to `/data/local/tmp`,
then `adb shell "run-as <package> cp /data/local/tmp/f files/..."` (debug builds only).

### After a touch, the first D-pad press is swallowed
Any touch (an `adb shell input tap`, a finger on the screen, the photo picker) puts the window in
touch mode, and Android eats the next D-pad press to leave it: four Downs land one row short.
A scripted run that then presses A can choose the wrong option (it hid Browser once, 2026-10-25;
put back by editing `launcher.xml`). It is standard Android (`ViewRootImpl` spends the press
before the activity sees it), so `dispatchKeyEvent` can't catch it. Since 2026-10-25 the launcher
leaves touch mode as each touch ends (`requestFocusFromTouch`, `MainActivity.dispatchTouchEvent`),
so the press counts; other apps (the photo picker, Android's settings) still swallow one.

### Pull app files with `adb exec-out`, not `adb shell`
`adb shell run-as … cat` goes through a terminal that turns every LF into CRLF, so a preferences
file pulled that way no longer matches; `adb exec-out run-as … cat` gives the bytes. To put one
back: push it to `/data/local/tmp`, then in one `adb shell` line `am force-stop`, `run-as … cp`,
`am start`, so the launcher can't write its old copy in between.

### Reinstalling Home can crash its first start
`adb install -r` while the launcher is Home: Android restarts Home at once, sometimes before the
new APK is in place, and the process dies in `handleBindApplication` (`Resources.getConfiguration()`
on null) with none of our code on the stack. It comes back by itself (or `am start`). Check the
crash time in `adb logcat -b crash -d` against the install before hunting for a bug.

## Look

### Raster icons look soft on this panel
Console Launcher's dock and theme packs are WebP bitmaps scaled to the layout; at 288 dpi on a
7" 1080p OLED that reads as blur, which is the complaint that started this project. Glyphs are
vector (Material Symbols), app icons are adaptive icons drawn at 1:1, and covers are fetched at
or above the tile's pixel size (160 dp × 1.8 = 288 px, so fetch ≥ 600 px) and never upscaled.

### A spring overshoots: clamp anything computed from it
A spring with damping under 1 passes its target. Home's tile size did (192 dp → a bit more),
and `padding(top = (192.dp - size) / 2)` went negative for a frame: Compose throws "Padding
must be non-negative" and the launcher stops. Clamp values derived from a spring
(`coerceAtLeast(0.dp)`). Find it with `adb logcat -b crash -d`.

### Variable fonts render at their default weight unless the axis is set
`Font(R.font.x, FontWeight(600))` only labels the file; Compose draws the variable font's
default instance (Nunito Sans and Manrope: 200, Inter: 400) for every weight. Pass
`variationSettings = FontVariation.Settings(FontVariation.weight(w))` with each `Font` (`Look.kt`).
Check a new font's `fvar` defaults before blaming the font.
