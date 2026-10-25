# M4 — Art and the hero (2026-10-25)

The why behind Home's art and layout. How it works now: `architecture.md` *Home anatomy*,
*Tiles*, *Art*, *Own images*, *Backdrop*, *Structure* (*Art*).

## T4.1 Art source (2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-art*); the key's home changed since (`M8-release.md`).
- **What is looked up without asking:** the owner's favorites and the Games category (the
  owner's moves included). Not every app: a game database matched against "Files" or
  "Calculator" only gives junk, and those apps' adaptive icons are fine. On the Portal,
  GameNative, Stardew, NTE and DroidDeck declare the game category in their manifests.
- **Taken only on a close match:** the label and the game's name are the same words once case,
  symbols and punctuation are dropped, or the label is the name before a subtitle (`NTE` for
  `NTE: Neverness to Everness`, `:` or ` - `). Otherwise the app is recorded as *no match* and
  keeps its icon, so the launcher never guesses wrong art onto Home; the owner can still pick.
- **Per package, not per activity or profile:** the art belongs to the app, so a work-profile
  copy shares it. Records in their own `SharedPreferences` file (`art`), images in
  `files/art/<package>.cover` and `.hero`, replaced by a write-then-rename so a failed download
  keeps the old art.
- **Sizes, never upscaled:** covers are asked for at 512² or 1024² (512 preferred; a focused
  1.2× tile is 346 px), heroes at 1920×620 or 3840×1240 (1920 preferred: it is the screen's
  width). Still images only, no NSFW or humor sets. Decoding only scales down, to the size
  drawn.
- **The key** first came from `local.properties` through `BuildConfig`, fine for a private
  sideload; a published APK can't carry it, so users now give their own (`M8-release.md`).
- **Picking by hand:** choosing a match downloads it and the page stays, so another can be tried
  while comparing. *Use the app's icon* is never looked up again.
- **Retry:** a failed lookup (offline) is tried again at the next launcher start; the picker
  always searches live.
- **Network:** LineageOS's *Restricted networking mode* blocked every lookup at first
  (`UnknownHostException`); the owner had it allowed for the launcher alone, over adb
  (2026-10-25, `gotchas.md` *Restricted networking*).

## T4.2 Covers in the tiles (2026-10-25)
Status: accepted 2026-10-25 (B3.5).
- **Home carousel only.** The dock's buttons are round and 56 dp: a cover cropped to a circle
  loses its title and reads worse than the icon. All apps tiles are 96 dp with the name under
  them, where a cover adds little. Owner (2026-10-25): Home only, "the dock is more for
  utility apps, like mail, messenger".
- **Decoded at the lifted size** (tile × 1.14, the move-mode lift), so neither focus nor lift
  scales a bitmap up; the unfocused tile is a slight downscale.
- **Cropped, not letterboxed:** SteamGridDB's square covers are square, so the crop only trims
  an off-square upload. The sheet header and the tiles share one `CoverOrIcon`.

## T4.3 Home layout v2 (2026-10-25)
Status: as built 2026-10-25 over five rounds of the owner's looks, owner to confirm (*V-hero*).
- **The budget, 600 dp tall:** band 272 (490 px of the hero's 620), the bottom row's 56 dp
  dock 24 dp off the bottom, the 192 dp row between. The dock's focus label shares the space
  the grown tile uses; nothing had to shrink.
- **Hero cropped evenly,** not top-aligned: SteamGridDB heroes keep the subject in the middle
  (their logo area is a convention, not a rule). Decoded at the screen's width, so 1:1 on the
  Portal.
- **The vignette fades alpha,** not paint in the background color: the art ends in the page
  instead of in a frame, the same code works in light mode, and on a picture backdrop there is
  no hard edge. Strengths (bottom 100%, left 85%, top and right 50%) are first guesses for the
  owner to tune; the left one is what keeps the name legible.
- **The band follows the row, not the zone:** with the dock focused the hero and name stay
  (the name muted), so going down to the dock doesn't blank the top half.
- **Grow by layout, lift by scale:** the focused tile is laid out at 192 dp (346 px, under the
  512 px cover); Move adds a 1.14/1.08 scale on top. The row is fixed at 192 dp tall so focus
  leaving it doesn't move it.
- **The owner's numbers:** carousel center line at 52% of the full height (tried 65%, then dead
  center; it overlaps the hero's lower part, which the owner is fine with, and has no hairline,
  which would run through the gaps between tiles); unfocused tiles 144 dp at 85%; the name
  Metro-like, 3× then ×1.75 (210/252 sp), Bold (Selawik is static: SemiBold to Bold is the only
  step), all caps, 12 dp past the top edge, at 40%. Cap height is a fixed 0.72 of the size, near
  enough for all trial fonts, instead of reading each font's metrics. At that size a long name
  runs off the right edge (later: it scrolls, B2.3), and Wi-Fi and battery sit over it.
- **Reflections dropped** (owner, 2026-10-25): each tile drawn a second time flipped, in an
  offscreen layer with a DstIn gradient mask, so it mirrored the actual cover or icon, at a
  second decode per cover (small at 366 px); the ring and the Move lift weren't mirrored. The
  owner suspected them of the "keeps stopping"; the crash was really the spring overshoot
  (`gotchas.md`). Never committed; on the board as a small card.
- **The slide is one fractional slot per tile on a no-bounce spring,** with x and size derived
  from it, so the gaps can't drift. The first build scrolled a LazyRow to the item by a distance
  measured before the tiles resized and corrected at the end, while each tile's own spring
  (with bounce) ran on its own: it "jumps and doesn't render smoothly" (owner). A LazyRow isn't
  needed for a few favorites; touch drag moves focus instead of scrolling.
- **The last tile can scroll to the first slot too,** so focus always sits in the same place.

## Backdrop and the dead space under the hero (2026-10-25)
Status: accepted 2026-10-25: Black (the trial's end), the others are Settings choices.
- **The question:** the owner asked whether plain black is the answer for the space under the
  hero; a backdrop trial was built to judge it: Dark, Black, Hero glow (the hero blurred behind
  all of Home, the PS5 / Xbox way of filling the space with the game's color, soft by intent so
  the upscale is no crispness problem) and the five concept wallpapers.
- **Wallpapers are third-party images,** so they stay out of the APK and the repo (which may go
  public): they are read from a folder on the Portal they are pushed to. The abstract one is
  679×452 and looks soft full screen (2.8×, hard rule 4); the other four are 1920×1080 or larger.
  Android's own wallpaper (the theme's `windowShowWallpaper`) was the other way, not taken.
- **16:9 hero art (owner, "that'll solve the dead space"):** SteamGridDB has none (heroes are
  1920×620 / 3840×1240, grids at most 920×430), and stretching a hero is 1.7× soft. Steam's store
  screenshots are exactly 1920×1080 with no key (`store.steampowered.com/api/storesearch` by
  name → appid, `/api/appdetails?appids=` → `screenshots[].path_full`); the store page
  background is 1438×810 and pre-blurred, no use. IGDB artworks are real key art but need a
  Twitch app. Trial: ArtKind *Scene*, full screen, heavy vignette; picked by hand from contact
  sheets: NTE's screenshot 7 (night, blue flower field, comet), Stardew's 8 (the night dock with
  string lights), dark ones so text reads.

## Scenes kept (owner, 2026-10-25)
Status: accepted 2026-10-25; owner to confirm in use (*V-scene*).
- **Ask:** "16:9 screenshots/art is the way to go": find open art on the internet and load it as
  each app's scene. Dock to the center, the trial toggles gone, the accent white by default
  ("we'll give the users choices later"), smaller button hints. The owner also wants each app's
  art chosen from SteamGridDB, screenshots, or the gallery (*Art choices* below).
- **Source:** Steam's store API, no key: `storesearch` by the SteamGridDB name (canonical, so
  "NTE" finds "NTE: Neverness to Everness"), same words first, else the close-match rule;
  `appdetails?filters=screenshots`. Store screenshots are publicity images, freely reachable
  but not openly licensed: downloaded on the device for display like SteamGridDB's, never
  bundled. The SteamGridDB key is sent to SteamGridDB only.
- **Picking the shot:** the owner's hand picks were neither the darkest (NTE 43, Stardew 18 mean
  luma) nor the brightest, but mid-dark and very saturated (NTE #7: luma 82, saturation 174;
  Stardew #8: 69, 184). Score = mean saturation − 2·|mean luma − target|. With target 75 on
  PIL's samples both picks won, but on the device (ImageDecoder's 64×36) Stardew's #12 beat #8
  by a hair; 73 gives both a margin of 11–12 points, and the device then fetched exactly the
  owner's two files. Thumbnails cost about 40 kB each, 12–16 per game, once.
- **Crispness:** only shots whose file name says 16:9 and ≥ 1920 wide; Steam names every one
  `ss_….1920x1080.jpg`, so no download is needed to know.
- **Not on Steam:** GameNative and Crunchyroll found nothing; they keep the hero band.
- **Trials ended:** the owner had left the backdrop on Black and the accent on Violet. Black
  stays as the default backdrop; the accent is a new *White* (the text color, so near-black in
  light mode, where white would vanish). X and − do nothing on Home.
- **Dock centered:** the hints went from 16/24 to 14/20 and their disc from 24 to 20 dp. Even so
  the owner's 7-button dock (536 dp) and three hints in a row (303 dp) couldn't share the line:
  the first build moved the dock left to clear them, and since + Options comes and goes with
  focus (not on the All apps button) the dock jumped (owner). Owner's fix: A and B are known,
  hint only the other buttons, stacked and right-aligned. The stack is under 100 dp wide, so the
  dock sits exactly centered and still. All apps keeps X and +; the options sheet has no hints.
- **X hint held still:** in All apps' right-aligned bar the X glyph slid as Favorite became
  Unfavorite (owner: "the icon should sit still and the text move"). A hint reserves the
  width of its longest label (`Hint.room`), the text left-aligned in it.

## Owner's additions (2026-10-25)
Status: built (*T4.3 Home layout v2*, *Backdrop and the dead space*).
- **Hero vignette:** "a bit of vignetting feel" on the hero art: a soft darkening toward the
  band's edges and into the background, not a hard frame.
- **Backdrop trial:** cycle background colors and wallpapers on the device to judge the look,
  like the font and accent trials. The owner's test wallpapers (abstract, black and white,
  cyberpunk, a game, nature) were in `concepts/`, deleted 2026-10-25; copies stay on the
  Portal in the app's `files/backdrops`.

## Art choices (owner, 2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-art-preview*; reached from the preview since D23.1, `0.3.0.md`).
The owner wants every piece of art choosable, not only the game match.
- **SteamGridDB grids (T4.6):** covers and heroes as a grid, not a strip: three square covers
  fit the 296 dp panel at about 93 dp, and a grid moves on all four directions like the drawer.
  Heroes go one across, as a 1920×620 strip is unreadable at a third of the panel. Thumbnails are
  SteamGridDB's `thumb` copies, decoded to the cell's width and held in a 60-entry memory cache;
  nothing is written to disk until A. A pick is stored by its full url rather than its id, so
  Steam shots and gallery images share the store. With no pick the dot sits on the image `use`
  would take (first at 512 or 1920 wide), so the grid always shows what is in use. Checked on
  the Portal over adb: NTE has 3 covers and 11 heroes. A hero pick shows only where there is no
  scene.
- **Scene grid (T4.7):** fed by the same Steam store call the score uses, so it lists exactly
  the shots the score chose from. The Steam app id is kept even when no shot qualifies, so the
  grid can say "No Steam screenshots". Scenes fetched before the score's pick was remembered
  carry no dot until the owner picks.
- **The gallery (T4.7):** Android's photo picker (no permission, a system sheet the controller
  can drive) and an **automatic centered crop** rather than a crop editor, because a controller
  crop UI is a project of its own and most wallpapers are centered; it can come later if the
  owner misses it. The minimum is the size shown, not the size stored: a cover needs 346 px (the
  focused tile), a scene a full 1920 wide; smaller is refused with its size ("Too small:
  200×200, needs 346×346"). Apps with no game get their own record, `Own`, so the gallery works
  for GameNative-like apps too. A hero from the gallery was left out: a scene replaces the band
  wherever there is one. Checked over adb with a 7000×4667 photo (→ 512×512 cover, 1920×1080
  scene) and a 200 px square (refused).

## Re-picking after the icon (B2.5, 2026-10-25)
Status: fixed 2026-10-25.
Owner's report: after *Use the app's icon* on NTE, picking the game again "didn't seem to take".
The cover did take, but *Covers on tiles* was Off on the Portal, so the Home tile showed the
icon by design (the sheet header always shows the cover). The real bug was the scene: *Use the
app's icon* forgets the scene, and the scene lookup ran once per package per launcher start, so
re-picking the game in the same run brought back the cover and hero but no scene. A new (or the
same) game clears that once-per-start mark before looking.
