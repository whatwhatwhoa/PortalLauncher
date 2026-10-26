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

### Trying an update before it is on GitHub
Updates reads the real releases on GitHub; to try a version that isn't published yet, or the whole
path: put `update.url=http://localhost:8765/latest.json` in `local.properties` (debug builds
only; plain http to localhost is allowed in debug only), serve a folder holding `latest.json`
(`{"tag_name":"v0.1.1","assets":[{"name":"x.apk","size":…,"browser_download_url":"http://localhost:8765/x.apk"}]}`)
and the APK with `py -I -m http.server 8765 --bind 127.0.0.1`, then `adb reverse tcp:8765 tcp:8765`.
To keep the owner's install untouched, build both APKs with a temporary
`applicationIdSuffix = ".check"` and versionCodes 1 and 2, and grant the copy only:
`adb shell appops set <pkg>.check REQUEST_INSTALL_PACKAGES allow` (gone with its uninstall).
Restricted networking didn't block the new copy's localhost requests. Play Protect asks to
scan an APK it hasn't seen: *More details → Install without scanning*, never *Scan app* (it
uploads the APK). Take `update.url` back out after.

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

### Scripted keys mix with the owner's input
The Portal is the owner's daily device and they often hold it while an agent works. Keys sent
with `adb shell input` then land between theirs (it happened in two sessions on 2026-10-25: a
move test, and a stray key that opened Crunchyroll). Before each scripted key, check that the
input is quiet: `adb shell dumpsys input` *RecentQueue* lists recent events, and one of its
queues is stale (its ages don't move between two reads), so compare two reads; the youngest
event must be older than our own last key. Stop when it isn't.

A key sent while a sheet is still sliding in can be lost: three downs on the options sheet
moved focus two rows and left it on *Uninstall* (2026-10-25). Screenshot before every A and
send it only when the focus is where it should be.

### The Home key never reaches an app
The Portal's Home button comes from its built-in controller ("Xbox Wireless Controller",
`KEY_HOME`) as Android's Home key, which the system handles itself: the Home app only gets a
Home intent on release, and a hold starts Circle to Search instead (seen 2026-10-25). Only an
accessibility service with key filtering sees it first (`HomeHoldService`). LineageOS has its
own hold action (`key_home_long_press_action` in `content://lineagesettings/system`, 7 = sleep,
0 = nothing), but its provider refuses deletes and updates from adb; only `content insert`
works (it replaces the row). On the owner's Portal it is 0 since 2026-10-25 (it was unset:
Circle to Search).

### A force stop turns the hold-Home service off
Android drops a package's accessibility services from `enabled_accessibility_services` when it
is force-stopped (AOSP's `AccessibilityManagerService`; not yet seen on the Portal), and an
uninstall clears them too. The restore below force-stops the launcher. The launcher switches it
back on by itself where `WRITE_SECURE_SETTINGS` is granted, else Home says so once
(`decisions/0.4.1.md` D35.1). Check with `adb shell settings get secure enabled_accessibility_services`.

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

### Back with nothing to handle finishes the Home activity
A disabled `BackHandler` lets Android's own back run, and for an activity that isn't the root of
a launcher task (ours is a HOME task) that is `finish()`: Home is rebuilt in a new task, the
process stays (`wm_finish_activity … app-request` in `adb logcat -b events`). With predictive
back, `KEYCODE_BACK` never reaches `dispatchKeyEvent`, so the key table can't catch it. Keep a
handler on at all times (`MainActivity`), doing nothing when there is nothing to close.

### A system service taken from an activity can keep it alive
`LauncherApps` (and `MediaSessionManager`) from an activity's context hold that context, and
their callback's binder stays a JNI global in the process after unregistering, so every
recreated activity stayed in memory with its art and SoundPool (`decisions/0.4.0.md` D28.1).
Take services that get a listener from `applicationContext`. Count live activities with
`dumpsys meminfo <pkg>` (*Activities*) after `am dumpheap` (it collects first).

### `cmd media_session volume --set` sets another output's volume
It writes the *default* device's index, not the speaker's, though `--get` reads the speaker.
Read the real one in `dumpsys audio` (*STREAM_MUSIC*, *streamVolume*); to set it, send
`input keyevent 24`/`25` over the launcher with nothing playing (they step the music volume).

## Look

### Raster icons look soft on this panel
Console Launcher's dock and theme packs are WebP bitmaps scaled to the layout; at 288 dpi on a
7" 1080p OLED that reads as blur, which is the complaint that started this project. Glyphs are
vector (Material Symbols), app icons are adaptive icons drawn at 1:1, and covers are fetched at
or above the tile's pixel size (160 dp × 1.8 = 288 px, so fetch ≥ 600 px) and never upscaled.

### A faded layer clips: keep drawing outside a node out of it
A `graphicsLayer` with alpha under 1 is drawn offscreen at the node's own size, so anything the
node draws outside its bounds (a shadow, a glow, the focus ring) is cut off at that box. Home's
light shadow showed as grey square corners on every faded tile. Put the fade in its own layer
after (inside) the modifiers that draw outside, or fade without one.

### BoxWithConstraints' `maxWidth` goes stale after a density change
The activity handles `density` itself (`configChanges`), so a new *Display size* or *Smallest
width* recomposes without a restart. `LocalDensity.current` was new, but `maxWidth`/`maxHeight`
were still converted with the old density, so the root's scale came out 1 and Home drew ~1.25×
too big until a restart. Size from `constraints` (pixels) / `LocalDensity.current` instead, and
test with `adb shell wm density <n>` while the launcher runs (back: `wm density 288`).

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

### A new preferences file is missed by Save setup and the backup
The setup file and Android's backup each list the preferences they carry: `Setup.PREFS` and
`res/xml/data_extraction_rules.xml` (`launcher`, `art`, `picks`, `scenes`). A setting kept in a
new `getSharedPreferences` name, or a new folder the owner fills, is silently left out of both
until it is added to the two lists.
