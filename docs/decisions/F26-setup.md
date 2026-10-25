# F26 — Keep the setup through updates

Card: `TASKS.md` F26, "Make sure updates don't [lose] the players set up as much as possible".

### D26.1 · 2026-10-25 · A setup file, and Android's backup carrying the setup without the art
Status: built 2026-10-25, owner to try (*V-setup*). Owner: "F26 confirmed, I'll go with your
recommendation".

**What wipes a setup**: a normal update (adb `install -r`, Updates, a newer APK) keeps the app's
data, so it already kept everything. Only an uninstall or a change of signing key wipes it, and
the key change is coming: the owner's daily build is debug-signed, and moving to release builds
(F18) is an uninstall.
**The file**: Launcher Settings → System → *Save setup* writes `Download/Portal setup.json`;
*Restore setup* (a second A) opens Android's file picker, reads the whole file before changing
anything, then puts the setup back and starts the launcher again. It holds favorites, dock,
categories, hidden apps, every setting and the art choices (the game each app matched, the
picks, the scene choices), plus the gallery images and wallpaper files, since nothing could
fetch those again. Downloaded art stays out: after a restore each matched app's images are
fetched again from the picked URL, or SteamGridDB's first image (that one needs the key).
**Why the key stays out**: it is the owner's own and the file is a thing that gets copied
around; a restore keeps whatever key is set. After a move to a release build, paste it again
(Settings → Art).
**Why saving is by hand**: the moments that wipe the data are ones the owner chooses
(uninstall, switching builds), so one press before is enough. An automatic save on every change
would leave files behind: after an uninstall, Android no longer counts the old file as the
app's, so the next save would make *Portal setup (1).json*. That is also why Restore goes
through the picker, which can open a file whoever owns it, with no storage permission.
**Android's backup**: it was on by default and backed up everything, ~10 MB of art included;
past 25 MB a cloud backup isn't made at all. Rules (`res/xml/data_extraction_rules.xml`) now
carry only the four setup prefs to the cloud, the SteamGridDB key with them (Google's backup is
encrypted with the screen lock); a device-to-device transfer also carries the art files.
Restored art comes back by the same refetch.
**Not done**: no automatic save, no restore of a file from another device's apps (keys hold the
component, so apps that aren't installed are just skipped).
