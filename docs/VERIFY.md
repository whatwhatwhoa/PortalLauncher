# Verify

Built but not yet clicked on the device. Each check is one line: what to do, what should
happen. Ticked checks leave this file (their result goes in the commit or a decision entry).

## V-skeleton (built 2026-10-25; first run 2026-10-25)
Install: `CLAUDE.md` *Commands*. Launcher3 stays installed throughout. Portal is already the
Home app (the owner chose it in the first-run dialog). The agent drove the rest over adb with
injected keys (`decisions/M2-skeleton.md` *First device run*); these need the owner's hands.
- [ ] Real controller: left stick and D-pad move along the row and into the dock; A opens; Y
      opens All apps and B closes it; L1/R1 jump five; L2/R2 go to the ends; X and −
      do nothing on Home; Home returns to the start of the row. Holding a direction repeats.
- [ ] The focus feel: the 1.08 grow and the ring's slow pulse read as calm, not jumpy.
- [ ] Touch: tap a tile opens it; drag scrolls the row; swipe up opens All apps; Back closes it.
- [ ] Switch the system to light mode: background, text and accent all change, still legible.
- [ ] Install or uninstall any app: the row and All apps update without a restart.
- [ ] Settings → Apps → Default apps → Home: pick Launcher3, press Home, Launcher3 is back.
      Then pick Portal again if you want to keep it.

## V-options (built 2026-10-25)
The agent opened the sheet over adb; the rest is the owner's.
- [ ] + on a carousel tile, a dock app and an All apps tile opens the sheet; the options fit
      where it came from; B, + or a tap outside closes it; a long press opens it too.
- [ ] Move a favorite and a dock app: it lifts, left/right shift it, A puts it down; the
      order survives a restart.
- [ ] Hide an app: it leaves All apps (count says "· 1 hidden"), the row and the dock; Show
      hidden apps brings it back faded; Unhide works.
- [ ] Add to dock / Remove from dock; App info opens the system page; Uninstall asks first.
- [ ] All apps: L1/R1 jump by first letter.
- [ ] Selawik (now the default): weights look right (title semibold)?

## V-status (built 2026-10-25)
- [ ] The corners read well: the clock top-left, Wi-Fi and battery top-right; at half the old
      distance (third ask), close but not cramped. All apps' title sits clear below the clock.
- [ ] Walk away from the router: the Wi-Fi wedge fills less; Wi-Fi off leaves it dim.
- [ ] Unplug: the battery fill turns from the accent to the text color; the number tracks the
      system's.
- [ ] Light mode: the dim parts of both glyphs and the number inside the battery stay legible.

## V-motion (built 2026-10-25)
- [ ] Open an app from the row, the dock and All apps: its window grows out of the tile, not
      from the middle of the screen.
- [ ] Selawik at 1.15×: right beside the others now? The hint bar and titles don't clip.
- [ ] Optional, a device setting: `adb shell settings put global animator_duration_scale 0`
      (undo: `... animator_duration_scale 0.5`, the Portal's value). Home without restarting:
      no ring pulse, no slides or fades, launches cut straight in.

## V-art (built 2026-10-25)
Network is allowed for the launcher, and the favorites' art is downloaded.
- [ ] NTE's Home tile shows its cover, crisp, focused or not; its + sheet shows the cover too.
- [ ] + → Art… on NTE: the cover and hero strip at the top, matches below with a dot on the
      one in use; pick another, it downloads ("Fetching…") and the top changes; B goes back.
- [ ] Search: A on the Search row brings up the keyboard; type a name, its search key lists
      new matches; B ends typing without leaving the page.
- [ ] Use the app's icon: the dot moves there and the header and the Home tile show the icon
      again; picking a match brings its cover back to the tile without a restart.
- [ ] A favorite with no close match (Browser) keeps its icon and nothing odd shows.

## V-scene (built 2026-10-25; Steam fetch 2026-10-25)
- [ ] NTE and Stardew fill the whole of Home with their scene, crisp, the vignette heavy at the
      left and bottom; the name, tiles, dock and hints read over it; moving to an app without
      one crossfades back to the band or the backdrop with no flash.
- [ ] Favorite a Steam game you have on the Portal (or + → Art… on one): its scene appears on
      its own within a few seconds and reads well; a non-Steam app keeps the band.
- [ ] The dock sits in the middle and doesn't move as focus goes along it; the hints (+ Options,
      Y All apps) are stacked at the right, smaller, and still read; the ring and name are white.

## V-hero (built 2026-10-25)
- [ ] Move along the row: NTE's hero fills the band and crossfades in and out; Browser and
      apps without a hero leave the plain background; nothing flashes empty between two heroes.
- [ ] The name (210 sp bold caps, 40%): clipped by the top edge; the
      status over it still reads; a long name runs off the right edge cleanly.
- [ ] Every tile, the last one too, slides into the first slot when focused, smoothly, no
      jump at the end; holding a direction glides; the others are 144 and a touch see-through.
      Dragging the row by touch moves focus along it. Going quickly never stops the launcher.
- [ ] The vignette: soft, the name reads over the art, the clock and battery read along the top.
- [ ] The focused tile grows and its neighbors slide aside smoothly; L1/R1 and L2/R2 too.
- [ ] Down to the dock: the row's tiles all shrink to 144, the row doesn't jump, the name goes
      muted, the dock's label is clear of the tiles. Up again: back to accent and 192.
- [ ] + → Move on a carousel tile: it lifts a little more, moves, A puts it down.
- [ ] Light mode: the vignette fades to the light background, the name still reads.
- [ ] Animations off: the hero, tile growth and lift all snap.

## V-art-preview (F23, F24, built 2026-10-25; replaces the grid checks of 2026-10-25)
Installed, not driven over adb (the owner was using the Portal). The choice and gallery code
underneath is the one checked over adb before; what is new is how it is reached.
- [ ] + → Art… on NTE: the matches start focused; Up from the first one rings the cover in the
      preview; Right moves the ring to the wide strip, Left back, Down back to the matches.
- [ ] A on the cover: a grid, *Gallery…* first, "n of N" above, a dot on the one in use, focus
      on it. Moving shows each thumbnail in the preview's cover; its ring stays still.
- [ ] A on another cover: "Fetching…", then the preview and the Home tile keep it; B goes back
      to the preview (cover still ringed), B again to the options.
- [ ] The panel slides out to about half the screen on Art… and back on B; the preview and
      grids read big enough (covers four across, scenes three, heroes two).
- [ ] A on the strip: tabs *Hero* and *Scene* between drawn L1 and R1 bumpers, the one Home
      shows dotted and open; R1/L1 turn tabs and the preview's strip takes that shape.
- [ ] F24: A on a hero: Home behind NTE turns to the hero band with its reflection, no scene,
      and Hero glow (if the backdrop) shows; A on a scene: the scene, no reflection, no glow.
      A on the already-dotted image of the other tab switches without a download.
- [ ] Launcher Settings → Look has no *Scenes* row any more.
- [ ] B6.1: a game newly matched (Art… → another match, or *Use the app's icon* then the game
      again) shows its hero on Home, not its scene; the strip opens on the *Hero* tab.
- [ ] *Gallery…* on Browser's cover (no game): caption "No game matched…", the photo picker
      opens, the chosen photo becomes the tile, cropped square; the same on the strip for a scene.
- [ ] A small image (a screenshot crop, an icon) is refused with "Too small: …" and nothing
      changes; *Use the app's icon* clears both.
- [ ] Touch: a tap on the cover or the strip opens its grid; a tap on a thumbnail takes it.
- [ ] After a restart the pick is still dotted. Choosing another game (or the icon) and back
      starts from that game's own first images.

## V-categories (built 2026-10-25)
The agent drove these over adb once (layer, Category… move and back, Home key both ways); they
need the owner's eye and hands.
- [ ] Home names the category ("Favorites") small above the row; it reads, and isn't crowded
      under the big name.
- [ ] B on Home: the names in big type on the left, the row behind faint and a little smaller;
      up and down preview each category's row; A goes in, B goes back. Ghost at 18% and the
      heading size feel right? (T5.3: say lighter, darker, bigger or smaller.)
- [ ] The seeds: Games has GameNative, Stardew, NTE and DroidDeck; Entertainment YouTube Music
      and Twelve; Chrome alone in Social; YouTube and Crunchyroll in Utilities. + → Category…
      moves one (try YouTube to Entertainment); it survives a restart.
- [ ] Open a game from Games, press Home: back on Games with that game focused. Press Home
      again: Favorites, first tile.
- [ ] Animations off: the layer and the ghosting snap.

## V-effects (built 2026-10-25)
All three are on; + → Launcher settings… switches each off and on to compare.
- [ ] Focus glow: a soft accent haze outside the ring, breathing with it; the gap between tile
      and ring stays clean. Too strong, too weak, or right?
- [ ] Tile depth, dark: a faint highlight along the top edge of the tiles, dock buttons and All
      apps tiles. Light theme: a soft shadow under them instead. Keep it?
- [ ] Background gradient: on an app without a scene, a faint glow right of center behind the
      row; with Mint or Amber it takes the color. Any banding on the OLED?

## V-settings (built 2026-10-25)
- [ ] + on any tile: *Launcher settings…* is the last row; A opens *Launcher Settings* at the right, Home not
      dimmed. B or + closes it; Home closes it too.
- [ ] In a category's rows: up/down move, left/right change the value with chevrons either
      side; A steps it on.
- [ ] Each accent recolors ring, glow, name and battery fill at once; Theme Light/Dark
      overrides Android's; Backdrop cycles Black, Dark, Hero glow, then the trial wallpapers.
- [ ] Covers off: icons on the Home tiles.
- [ ] Focus pulse Off: the ring holds still; Normal: twice as fast.
- [ ] Android Settings → Apps → Default apps → Home app: the gear beside Portal opens the panel.
- [ ] Restart the launcher (or the Portal): every choice is kept.

## V-b7 (B7, built 2026-10-25)
- [ ] Launcher Settings → Look: nine rows, Theme down to Focus pulse; going past the eighth
      scrolls the page; each effect switches (gradient, glow, depth, reflection, pulse).
- [ ] Light theme, focus on the dock: Home's faded tiles have round shadows at all four
      corners, no grey square edge; the focused tile too.
- [ ] All apps lists *Controller settings* (B8) under C; A opens AYN's Gamepad page; Home comes back.
- [ ] It reaches your dock by + → Add to dock (yours is already your own); Reset the dock would
      put it after Settings, but resets the rest too.

## V-artist (F27, built 2026-10-25)
Seen on the Portal (2026-10-25): title · artist · album with YouTube Music playing, a long one
cut at the middle with its end faded, then scrolling; gone on pause.
- [ ] A long line waits 2.5 s, then scrolls left and slides under the bar, round again; its
      right end fades where it is cut. Is the speed (36 dp/s) right?
- [ ] Skip a track: the line crossfades. Pause: the bar and name fade out.
- [ ] − opens the quick panel, or + Launcher Settings: the artist is gone while it is open.
- [ ] Notification access off for Portal: no artist, even while playing (turn it back on).

## V-b8 (B8, built 2026-10-25)
- [ ] The dock's Controller settings shows the Portal in white on teal, crisp, its name above
      when focused; the same icon in All apps.
- [ ] Quick panel (−), the shortcuts row: the Portal glyph, named *Controller settings*.

## V-setup (F26, built 2026-10-25)
- [ ] Settings → System → *Save setup*: A shows "Saving…", then "In Download". Files (the dock)
      → Downloads has *Portal setup.json*; a second save replaces it, no "(1)".
- [ ] *Restore setup*: A asks "A again to confirm"; A opens the file picker; choose the file.
      The launcher starts again with the same favorites, dock, categories and settings, and the
      art comes back within a minute (picked heroes and scenes the same as before).
- [ ] Pick some other file (a photo, any .json): the row says "Not a Portal setup" and nothing
      changes. B in the picker: nothing changes.
- [ ] Before moving to a release build (F18): save, uninstall, install the release build,
      restore, paste the SteamGridDB key again (Settings → Art).

## V-settings-more (built 2026-10-25)
- [ ] The categories go on past *Look*: *Home*, *Sound*, *Art*, *Apps*, *System*;
      the credits line closes System's page.
- [ ] Clock 12-hour / 24-hour changes the corner clock at once; System follows Android again.
- [ ] Battery percent Off: only the fill. Button hints Off: no hints on Home or All apps.
- [ ] Carousel wraps On: right on the last tile goes to the first, left on the first to the last.
- [ ] Home key goes to → Where you left: open Games, press Home twice, still on Games; restart the
      launcher, still Games. Favorites: back to the first category, as before.
- [ ] Fetch art Off, then favorite a game you haven't: no art comes; + → Art… still searches.
- [ ] Clear all art: the size shows; A reads "A again to confirm", a move cancels it; A twice
      clears **every cover, scene and pick** (NTE's too), then with Fetch art on they come back
      by themselves (the picks don't). Only if you want that; otherwise skip this one.
- [ ] Hidden apps: the count; A opens All apps with them shown. Reset the dock: A twice, the dock
      is the utility apps again (skip if you like your dock).
- [ ] Home app reads Portal; A opens Default apps (B back). Android settings opens Settings.

## V-quick-media (F21, built 2026-10-25)
Seen on the Portal (2026-10-25): the one line with the grant on and music playing, "Endless
Night · Dreamcatcher" over 0:43 / 3:18, focus on Brightness. Keys not driven over adb.
- [ ] − opens the panel on Brightness; a faint line above it; the player on one line at the top:
      L1 ⏮ ⏯ ⏭ R1 and a small *Enable seek* at the right end. Up reaches ⏮; the line below
      names the focused one. The toggles row is two buttons now.
- [ ] Right to *Enable seek*, A: Android's notification access for Portal; switch it on, B back:
      with music playing, title · artist over the time in, the bar moving and the length. Left/Right on the bar seek 10 s (held repeats), Down leaves it, a
      tap on the bar jumps there.
- [ ] With music playing in an app (YouTube, a player): A on ⏭ skips, ⏮ goes back; A on ⏯
      pauses and the button empties, again plays and it fills with the pause icon.
- [ ] With focus on Brightness or Volume, R1 skips and L1 goes back; focus stays put; a soft
      select sound each time (sounds on).
- [ ] *Button hints* off in Launcher Settings: the bumpers are gone, L1/R1 still skip.

## V-quick (built 2026-10-25)
- [ ] − (the square) on Home and in All apps drops the panel from the top; B or − closes it, a
      tap outside too, the Home key too.
- [ ] Brightness the first time: the row says "A: let the launcher change it"; A opens Android's
      *Modify system settings* for Portal; switch it on, B back: the slider is there.
- [ ] Brightness left/right: even-looking steps, held repeats, all the way down stays readable;
      moving it turns Auto brightness off. Tap the track: it jumps there.
- [ ] Volume left/right: media volume steps, the percentage follows; the volume rocker's change
      shows next time the panel opens.
- [ ] Auto brightness toggles (filled when on). Do not disturb: the first A opens *Do Not
      Disturb access*; after granting, A toggles it (the status bar's DND icon agrees).
- [ ] Wi-Fi, Bluetooth, Battery, AYN and Android settings each open their page; B comes back to
      the panel still open.

## V-artkey (built 2026-10-25)
- [ ] Launcher Settings → Art: the first row is *SteamGridDB key* and reads Set (the debug build
      still carries the key from `local.properties`). Art keeps arriving as before.
- [ ] With something else copied (or nothing), A on it: "Copy your key first"; nothing changes.
- [ ] Copy the key (steamgriddb.com → Preferences → API), A: "Checking…", then Set. A wrong
      32-digit key: "Not accepted". Wi-Fi off: "Offline, try again".
- [ ] README: read it on github.com once; the steps and button table match what you see.

## V-accent (built 2026-10-25)
- [ ] Settings → Accent, left/right: White, Coral, Amber, Lime, Mint, Ice blue, Lavender, Rose,
      round again; the ring and dot follow at once.
- [ ] A on Accent: the page *Accent*, two rows of four swatches, the one in use dotted; moving
      over them wears each; B back to the list on the Accent row.
- [ ] Down to Hue: left/right walk the color round the circle; Intensity from near gray to
      strong; the tracks show where they lead and the ring sits where the accent is. The row
      reads "Custom" in the list; after a restart the custom color is still there.
- [ ] Light theme: every swatch and a strong custom color still read as a ring on the light
      background. Taps: a swatch picks it, a tap on a track sets it there.

## V-transit (built 2026-10-25)
- [ ] Y (or a swipe up): All apps rises and fades in, Home behind steps back a little; B: it
      sinks away quicker than it came. Neither feels slow or jumpy.
- [ ] The +, − and settings panels and the categories layer (B) come and go with the same feel.
- [ ] The ring's pulse at Slow and at Normal: a calm breath, no blink at the ends.
- [ ] Animations off: all of it snaps (then put the duration scale back to 0.5).

## V-b3 (built 2026-10-25)
- [ ] The dock ends with Launcher Settings (since B5.1 the launcher's icon with a gear badge);
      focused it reads "Launcher settings", A opens the panel, B closes it and focus is still on
      it. + on it does nothing (no hint either).
- [ ] − opens the quick panel: a sun and a speaker before the sliders, then two rows of round
      icon buttons, crisp; the line under them names the focused one ("Wi-Fi", …).
- [ ] Play / pause shows a pause icon while music plays; Auto brightness and Do not disturb fill
      when on, their icon dark.
- [ ] Touch the screen (a tap on the background), then press the D-pad once: focus moves at
      once, no lost press. Typing in Art… search still works with the keyboard.

## V-b2 (built 2026-10-25)
- [ ] Every word reads in American English (Favorites, color, centered); favorites survived.
- [ ] The hints: − Quick settings bottom-left, + Options and Y All apps bottom-right, all at 80%;
      the square and the right-pointing triangle sit big and rounded in their discs, crisp.
- [ ] A favorite with a long name (one that runs off the screen): it scrolls left after a pause,
      round again; the dock focused, it stops and is muted.
- [ ] NTE: + → Art… → Use the app's icon, then pick NTE's game again: cover, hero **and scene**
      come back (Covers on tiles must be On to see the cover on the Home tile).

## V-reflection (F9, built 2026-10-25; owner saw the first build)
- [ ] The darker reflection (about 40% instead of 62%): dark enough now, still visible?

## V-gap (F20, built 2026-10-25)
On Home, focus an app with a hero but no scene (set to Hero in Art…, or a game without a scene).
- [ ] A clear gap between the hero's foot and the reflection, like the sketch; the reflection's
      top is soft, not a hard line; it is gone before the dock.

## V-categories-edit (F12, built 2026-10-25)
- [ ] B on Home: the layer shows only the categories with apps, no New category; + Edit bottom right.
- [ ] + : edit mode, every category (an empty one faint), New category at the end, + Done; + or B leaves.
- [ ] A on Games: Rename, Move, Delete beside it; on Favorites only Rename.
- [ ] Rename: the keyboard comes up and the name stays above it; type, Done: Home's label and the
      layer show the new name, and it lasts a restart. B keeps the typed name too.
- [ ] Move: up and down move it, never above Favorites; Home's order follows.
- [ ] New category, type a name, Done: it shows faint with the empty note. + → Category… on an app:
      the new one is a row; A on it dots it and the page stays; the app shows there on Home.
- [ ] Category… on an app: dot two categories, it shows in both; All apps only clears them; the
      first and last rows' ring isn't clipped.
- [ ] Delete (A twice) on that new category: it goes, and an app only in it is in All apps only.
- [ ] Remove the agent's test category **Test** the same way if it is still there.

## V-standby (F19, built 2026-10-25)
First: + → Launcher settings… → Home → Hold Home for standby (Off) → A → turn on *Portal: hold
Home for standby* in Android's accessibility settings; back in Launcher Settings the row says On.
- [ ] A short Home press: "Hold Home for standby" shows at the top for about half a second;
      Home does what it always did (closes layers; while home, back to the start).
- [ ] Hold Home on Home, and in a game: standby opens (black, clock, date, battery, four
      rings), no Circle to Search; readable at your brightness; stays on past the screen timeout.
- [ ] One to three presses fill rings and leave you in standby; wait 2 s and they empty; four
      quick presses of one button (any: Home, A, D-pad up …) come back to where you were.
- [ ] Mixed buttons (A, B, A, B) don't wake it: each new button starts the rings again at one.
- [ ] Touches, swipes, both sticks and both triggers do nothing in standby: no rings fill.
- [ ] Volume keys in standby still change the volume; power off and on: Home, not standby.
- [ ] The clock moves to a new spot each minute.

## V-updates (F18, built 2026-10-25)
Once the repo is public and a release has its APK, on the release build (a debug build reads
"Signed differently", which is right).
- [ ] + → Launcher settings… → System → Updates: "Checking…", then "Up to date", or
      "Install <version>" when a newer release is up.
- [ ] Before any release (or while private): "None published". Wi-Fi off: "Offline, try
      again"; A checks again.
- [ ] A on Install the first time: Android's *Install unknown apps* page for Portal; allow, B
      back: the row says "Allow, then A again"; A: "Downloading n%", then "Installing…".
- [ ] The launcher restarts on the new version (the credits line shows it); favorites, dock,
      art and settings are all still there. If Play Protect asks, *Install without scanning*.

## V-sounds (F22, built 2026-10-25)
Media volume up a little first: the sounds follow it.
- [ ] Home: left and right tick softly; at the row's end (Carousel wraps off) a duller bump, and
      holding against the end doesn't machine-gun. Down to the dock and back up tick too.
- [ ] B: the layer opens with a rising swish, B again closes with a falling one; Y and B on All
      apps the same. + and − (sheet, quick panel) swell open; B out of them is Back.
- [ ] A on a tile: the launch sound, once (no select on top), and it isn't cut off as the app opens.
- [ ] Quick panel: Volume and Brightness tick higher as they rise; Auto brightness toggles up
      and down. Do not disturb on: everything is silent until it's off again.
- [ ] Launcher Settings → Sound: Family steps play Select in Soft, Glass, Crisp; Volume steps
      get louder; Sounds off: silent everywhere, on: the toggle sound.
- [ ] Standby: the fourth press wakes with the chime. Home pressed at home: Back.
- [ ] Too loud or too quiet at Low against a game's sound? Which sound grates after a while?

## V-starting (F10, built 2026-10-25)
- [ ] After this build, Home's categories hold the apps the table or their manifest places;
      the rest (system odds and ends) are in All apps only, and Chrome is in Utilities.
- [ ] Install any app (from the Play Store): it shows in All apps, not on Home.
- [ ] In All apps, + → Category… on it: five rows, *All apps only* dotted; pick Games: it is on
      Home under Games. Category… again → *All apps only*: off Home again.
- [ ] Update an app that is on Home: it stays where it was.
