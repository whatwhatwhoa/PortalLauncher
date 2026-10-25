# M4 — Art and the hero (2026-10-25)

`TASKS.md` M4 holds the tasks; this entry holds the why.

## T4.1 Art source (2026-10-25)
- **What is looked up without asking:** the owner's favorites and apps whose own category is
  game (`ApplicationInfo.CATEGORY_GAME`). Not every app: a game database matched against
  "Files" or "Calculator" only gives junk, and those apps' adaptive icons are fine. (This said
  none of the Portal's apps sets the game category; wrong: GameNative, Stardew, NTE and DroidDeck
  do, read from their manifests in M5. Since M5 it is the Games category, owner's moves included.)
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
- **The key** is read from `local.properties` into `BuildConfig` at build time, so it is in no
  tracked file; it is inside the APK, which is fine for a private sideload. If the app is ever
  published, the key has to come from the owner at run time instead. No key: no Art option.
- **Picking by hand:** + → *Art…* turns the sheet into a picker: the cover and hero in use at
  the top, SteamGridDB's matches for the label, *Search* (the keyboard; while it is up, keys go
  to the field and B ends it), and *Use the app's icon* (never looked up again). Choosing a
  match downloads it and the page stays, so another can be tried while comparing. Until T4.2 the
  sheet header is the only place a cover shows.
- **Retry:** a failed lookup (offline) is tried again at the next launcher start; the picker
  always searches live.
- **First device run:** every lookup failed with `UnknownHostException`. The Portal has
  LineageOS's *Restricted networking mode* on, which blocks new apps (`gotchas.md`). The owner
  asked for it to be allowed; done over adb for the launcher alone (2026-10-25), and the
  favorites' covers and heroes downloaded at the next start (GameNative, Stardew, NTE).

## T4.2 Covers in the tiles (2026-10-25)
- **Home carousel only.** The dock's buttons are round and 56 dp: a cover cropped to a circle
  loses its title and reads worse than the icon. All apps tiles are 96 dp with the name under
  them, where a cover adds little; both can follow if the owner wants.
- **Decoded at the lifted size** (tile × 1.14, the move-mode lift), so neither focus nor lift
  scales a bitmap up; the unfocused tile is a slight downscale.
- **Cropped, not letterboxed:** SteamGridDB's square covers are square, so the crop only trims
  an off-square upload. The sheet header and the tiles share one `CoverOrIcon`.

## T4.3 Home layout v2 (2026-10-25)
- **The budget, 600 dp tall:** band 272 (490 px of the hero's 620), the bottom row's 56 dp
  dock 24 dp off the bottom, the carousel's 192 dp row centered in the 248 dp between. The dock's
  focus label needs 32 dp above the dock but only shows when the row's tiles are all 160, so it
  shares the space the grown tile uses; nothing had to shrink.
- **Hero cropped evenly,** not top-aligned: SteamGridDB heroes keep the subject in the middle
  (their logo area is a convention, not a rule). Decoded at the screen's width, so 1:1 on the
  Portal.
- **Vignette into the background color,** not black: the same code works in light mode, and
  the art ends in the page instead of in a frame. Strengths (bottom 100%, left 85%, top and right
  50%) are first guesses for the owner to tune; the left one is what keeps the name legible.
- **The band follows the row, not the zone:** with the dock focused the hero and name stay
  (the name muted), so going down to the dock doesn't blank the top half.
- **Grow by layout, lift by scale:** the focused tile is laid out at 192 dp (346 px, under the
  512 px cover); Move adds a 1.14/1.08 scale on top, so covers decode at 192 × 1.056 dp. The row
  is fixed at 192 dp tall so focus leaving it doesn't move it.
- **The trial note** didn't fit beside the dock (536 dp) and the hints; it sits above the hints
  until the trials end.

- **Owner's first look:** carousel placed by its center line at 65% of the screen height
  (measured on the full height, the owner's answer; it was ~66% as centered, so it barely moved
  up); the name 3× (120/144 sp) at 70% opacity so the hero shows through it; the dark
  background much darker, #141414, until T4.4 decides the backdrop.
- **Second look:** the carousel back to dead center (50%), overlapping the hero's lower part,
  which the owner is fine with; the hairline went, since at 50% it would have run through the
  gaps between tiles. The name made Metro-like: lifted until its capitals just pass the top
  edge (4 dp). Cap height is a fixed 0.72 of the size, near enough for all four trial fonts,
  instead of reading each font's metrics. The clock now sits over the name's first letters.
- **Third look:** carousel center 52%; the name ×1.75 (210/252 sp), Bold (Selawik is static:
  SemiBold to Bold is the only step), all caps, 12 dp past the top edge. At that size a name
  runs off the right edge instead of an ellipsis, and the Wi-Fi and battery sit over it too.
- **Reflection, real not faked:** each tile is drawn a second time flipped, in an offscreen
  layer with a DstIn gradient mask, so it mirrors the actual cover or icon. It costs a second
  decode per cover, small at 366 px. The ring and the Move lift aren't mirrored.
- **Fourth look:** reflections dropped for now (the owner suspected them of the "keeps
  stopping"; the crash was really the tile's spring overshooting 192 dp, which made the
  centring padding negative, see `gotchas.md`). They may come back. Unfocused tiles 160 → 144 dp
  at 85% opacity; the last tile can scroll to the first slot too; the name at 40%.
- **Fifth look:** the slide to a new tile "jumps and doesn't render smoothly": the LazyRow
  scrolled to the item by a distance measured before the tiles resized, then corrected at the
  end, while each tile's own spring (with bounce) ran on its own. Replaced by one fractional
  slot per tile on a no-bounce spring, with x and size derived from it, so the gaps can't drift.
  A LazyRow isn't needed for a few favorites; touch drag now moves focus instead of scrolling.
- **Dead space under the hero:** the owner asked whether plain black is the answer; built the
  backdrop trial to judge it (X): Dark, Black, Hero glow (the hero blurred behind all of Home,
  the PS5 / Xbox way of filling the space with the game's color, soft by intent so the
  upscale is no crispness problem) and the five concept wallpapers. For that the vignette
  became an alpha mask instead of paint in the background color: the same look on a plain
  backdrop, no hard edge on a picture. The abstract wallpaper (679×452) will look soft.
- **16:9 hero art (owner, "that'll solve the dead space"):** SteamGridDB has none (heroes are
  1920×620 / 3840×1240, grids at most 920×430), and stretching a hero is 1.7× soft. Steam's store
  screenshots are exactly 1920×1080 with no key (`store.steampowered.com/api/storesearch` by
  name → appid, `/api/appdetails?appids=` → `screenshots[].path_full`); the store page
  background is 1438×810 and pre-blurred, no use. IGDB artworks are real key art but need a
  Twitch app. Trial: ArtKind *Scene*, full screen, heavy vignette; picked by hand from contact
  sheets: NTE's screenshot 7 (night, blue flower field, comet), Stardew's 8 (the night dock with
  string lights), dark ones so text reads. If the owner keeps it: fetch from Steam on a match
  and pick the shot in the Art sheet; non-Steam apps keep the band.

## Scenes kept (owner, 2026-10-25)
- **Ask:** "16:9 screenshots/art is the way to go": find open art on the internet and load it as
  each app's scene. Dock to the center, the trial toggles gone, the accent white by default
  ("we'll give the users choices later"), smaller button hints. Later the owner wants each app's
  art chosen from SteamGridDB, screenshots, or the gallery (*Later* in `TASKS.md`).
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
  stays as the fixed backdrop; the accent is a new *White* (the text color, so near-black in
  light mode, where white would vanish). X and − do nothing on Home; the stored picks are no
  longer read. Dark, Hero glow and the wallpaper code stay for the settings screen (T6.2).
- **Dock centered:** the hints went from 16/24 to 14/20 and their disc from 24 to 20 dp. Even so
  the owner's 7-button dock (536 dp) and three hints in a row (303 dp) couldn't share the line:
  the first build moved the dock left to clear them, and since + Options comes and goes with
  focus (not on the All apps button) the dock jumped (owner). Owner's fix: A and B are known,
  hint only the other buttons, stacked and right-aligned. The stack is under 100 dp wide, so the
  dock sits exactly centered and still. All apps keeps X and +; the options sheet has no hints.
- **X hint held still:** in All apps' right-aligned bar the X glyph slid as Favorite became
  Unfavorite (owner: "the icon should sit still and the text move"). A hint now reserves the
  width of its longest label (`Hint.room`), the text left-aligned in it.

## Owner's additions (2026-10-25)
- **Hero vignette:** "a bit of vignetting feel" on the hero art: goes into T4.3, where the band
  is built (a soft darkening toward the band's edges and into the background, not a hard frame).
- **Backdrop trial:** cycle background colors and wallpapers on the device to judge the look,
  like the font and accent trials (T4.4). The owner's test wallpapers are in `concepts/`
  (abstract, black and white, cyberpunk, a game, nature). Notes for when it is built:
  - They are third-party images, so they stay out of the APK and the repo (which may go
    public): the trial reads them from a folder on the Portal they are pushed to.
  - The abstract one is 679×452; drawn full screen it would be upscaled 2.8× and look soft
    (hard rule 4). The other four are 1920×1080 or larger.
  - Android's own wallpaper (the theme's `windowShowWallpaper`) is the other way to do this:
    the owner sets it in system settings and every launcher shows it. Worth deciding with the
    owner at T4.4.

## Art choices (owner, 2026-10-25)
The owner wants every piece of art choosable, not only the game match.
- **T4.6 SteamGridDB (built 2026-10-25):** two rows above the matches open the game's covers or
  heroes as a grid, not a strip: three square covers fit the 296 dp panel at about 93 dp, and a
  grid moves on all four directions like the drawer. Heroes go one across, as a 1920×620 strip
  is unreadable at a third of the panel. Thumbnails are SteamGridDB's `thumb` copies, decoded to
  the cell's width and held in a 60-entry memory cache; nothing is written to disk until A.
  A pick is stored by its full url rather than its id, so T4.7's Steam shots and gallery images
  can share the store. With no pick the dot sits on the image `use` would take (first at 512
  or 1920 wide), so the grid always shows what is in use. Choosing another game, or the icon,
  clears the picks. Checked on the Portal over adb: NTE has 3 covers and 11 heroes; a pick
  shows on the tile and survives a restart. A hero pick shows only where there is no scene.
- **T4.7 Other sources (built 2026-10-25):** *Choose the scene…* is a third grid beside cover
  and hero, fed by the same Steam store call the score uses, so it lists exactly the shots the
  score chose from; the score's own pick is now remembered so it carries the dot (scenes
  fetched before this have none until the owner picks). The Steam app id is now kept even when
  no shot qualifies, so the grid can still say "No Steam screenshots". *From the gallery* is
  two rows, cover and scene, for every app: Android's photo picker (no permission, a system
  sheet the controller can drive) and an **automatic centered crop** rather than a crop editor,
  because a controller crop UI is a project of its own and most wallpapers are centered; it can
  come later if the owner misses it. The minimum is the size shown, not the size stored: a
  cover needs 346 px (the focused tile), a scene a full 1920 wide; smaller is refused with its
  size ("Too small: 200×200, needs 346×346"). Apps with no game get a new record, `Own`, so
  the gallery works for GameNative-like apps too (Crunchyroll turned out to be matched). A hero
  from the gallery was left out: a scene replaces the band wherever there is one. Checked on the
  Portal over adb with a pushed 7000×4667 photo and a 200 px square (both removed): the cover
  became 512×512, the scene 1920×1080, the small one was refused; Browser was put back.

## Re-picking after the icon (B2.5, 2026-10-25)
Owner's report: after *Use the app's icon* on NTE, picking the game again "didn't seem to take".
Two things: the cover did take, but *Covers on tiles* was Off on the Portal, so the Home tile
showed the icon by design (the sheet header always shows the cover). The real bug was the scene:
*Use the app's icon* forgets the scene, and the scene lookup runs once per package per launcher
start, so re-picking the game in the same run brought back the cover and hero but no scene.
A new (or the same) game now clears that once-per-start mark before looking.
