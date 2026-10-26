# M8 — Public page and release (2026-10-25)

## The owner's ask
Set up the GitHub page for users, not developers: the quick things up front, where to download
the current APK, how to set it up, screenshots that show how it looks.

## Owner's answers (2026-10-25)
- **Public?** Not yet. Prepare everything, keep the repo private until the owner says so.
- **The art key:** build the key entry first (users bring their own key), rather than ship
  without art.
- **License:** MIT.
- **Screenshots:** the agent drives the Portal over adb while it is put down.

## Built (T8.1)
- **The page is `README.md`,** not a GitHub Pages site. The repo's front page is what people
  land on; a Pages site on a private repo needs a paid plan, and it would be a second copy of
  the same text. The order is what a user needs: the hero shot, one download link
  (`releases/latest`), install in four steps, the button table, art, quick settings, the
  gallery, then the fine print. Developer material is one line at the end.
- **Screenshots** were taken with `adb exec-out screencap` on the owner's real setup (their
  favorites, art and the White accent on Black). The input-quiet check: before each scripted
  key, the live input queue's youngest event must be older than our own last key. It caught
  the owner picking the Portal up again mid-session, and the agent stopped there (Home was left
  on Favorites, not on Entertainment where the owner had it). Photos are JPEG 90 through .NET's
  encoder (no Pillow here), the flat UI shots stay PNG. The art in them belongs to the games'
  makers; the README says so.
- **SteamGridDB key at run time.** `M4-art.md` said a published APK must not carry the
  owner's key. Now it is a Launcher Settings row. Typing 32 hex digits on a controller is
  miserable, so A **reads the clipboard**: the user copies the key on SteamGridDB's site in the
  Portal's browser and presses A. Android lets the focused app read the clipboard and shows its
  own toast. Only a 32-hex string is tried, and only one SteamGridDB accepts (one autocomplete
  request; 401/403 = not accepted, anything else = offline) is kept, so a typo can't silently
  turn art off. There is no "remove key" row: nobody asked, and Fetch art off already stops all
  lookups.
- **Art without a key.** `Art` always exists now: gallery covers and scenes, Clear all art and
  the Art… page work without a key; lookups and Search wait for one, and the page's caption
  says where to add it. A key arriving re-runs the automatic lookup at once (the key is part
  of the watched state in `MainActivity`).
- **Debug keeps the owner's key** from `local.properties` as a fallback, so their daily build
  didn't change. Checked: the release APK doesn't contain the key, the debug APK does.
- **Release signing:** a 4096-bit RSA key, PKCS12, valid 100 years, at
  `C:\Users\Jim\.android\portallauncher-release.jks`, alias `portallauncher`. Its password is only
  in `local.properties`. **If it is lost, users can't update over the old install.** The owner
  should back up the file and the password together.
- **Version 0.1.0** (versionCode 1). The release APK is about 2.3 MB against the debug build's
  24 MB.

## The release check (T8.2, 2026-10-25)
**Why a side-by-side build:** the release APK can't be installed over the owner's debug build
(a different signature) without uninstalling, which would wipe their favorites, dock and art.
A `releaseCheck` build type (`initWith(release)`, package suffix `.check`, owner-approved) is the
same R8 output under its own package, so it ran beside the debug install with its own data.
It carries the owner's key so art search could be checked; the shipped `release` still has none.
**Rejected:** a second device (there is none); moving the owner to release builds for good
(their data would have to be moved over first).

**What ran, all fine, no crash and no R8 error in the log:** first start (Home-role dialog
dismissed, nothing chosen), Home with the starting categories applied on first run, scene
lookups and downloads (GameNative, NTE), carousel moves, the categories layer on B, All apps on
Y, the quick panel on Square, the options sheet on Triangle, the Art… page (SteamGridDB search answered;
its one DroidDeck match has no grids, SteamGridDB's answer), a cover from the gallery through
Android's photo picker, Launcher Settings down to the version line ("0.1.0-check"), and the key
row's clipboard read ("Copy your key first" with no key on the clipboard). Not run: a key typed
in through the clipboard and accepted; the request is the same one art search makes. No keep
rules were needed. The check build was uninstalled after.

**Then the release APK** was rebuilt from the same tree and named `PortalLauncher-0.1.0.apk`
(2.3 MB, release-signed). 0.1.0 is closed on the board (owner, 2026-10-25).

## Left for the owner before going public
- Make the repo public (check the history once more for secrets; the deleted `concepts/` files
  are still in it), then put up the GitHub release v0.1.0 with `PortalLauncher-0.1.0.apk`, as
  the README's `releases/latest` link expects.
- The README's LineageOS network step says "App info → Mobile data & Wi-Fi → Network access".
  That wording comes from LineageOS in general and wasn't checked on the Portal.
