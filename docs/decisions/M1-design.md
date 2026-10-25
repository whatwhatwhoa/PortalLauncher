# M1 — Design direction: concept review (2026-10-25)

What the owner dropped in `concepts/`, what each gets right or wrong for a 7" 1920×1080 OLED,
and what we take. The resulting direction is in `architecture.md` *Look*; this entry is the why.

## The brief, in the owner's words
Minimum function like the Nintendo Switch home screen. Dark and light mode, modern fonts, even
spacing. Target locked to the Odin 2/3 Portal (7", 1080p OLED, landscape); anything else it runs
on is a bonus. Scalable if it costs nothing; never at the expense of the target.

## The concepts

### `62l9ribax0ae1.jpg` — Switch home redesign (fan mock-up by u/__Luner, 3648×2052)
- **Right:** one row of square covers; the focused tile is larger with a cyan ring; the title
  and the button hints (`A Start`, `+ Options`) sit beside it, not under it; the status cluster
  (clock, Wi-Fi, battery) is small and top-right; the hero art fills the screen behind a
  dark teal wash so white text stays readable everywhere.
- **Wrong for us:** the system icons are strung along the top with the profile avatar; the
  orange "On" pill and the page dots add clutter; the title is cut off by the hero.

### `gijhclroo4pe1.png` — Switch 2 style home (fan mock-up, 1920×1080)
- **Right:** this is the clearest anatomy of the three and the closest to the brief. Portrait
  covers (3:4) in one row; the focused cover lifts with a cyan glow and its title appears above
  it in the accent color; a dock of round, evenly spaced system buttons under the row; a thin
  rule and a hint bar (`+ Options`, `A Start`) at the very bottom; status top-right; two tabs
  (`Software` / `Media`) centered at the top.
- **Wrong for us:** the folder tile (four small covers) is busy; nine dock buttons is too many;
  the music line at the bottom is noise.

### `image.webp` — Steam Big Picture (900×506 screenshot)
- **Right:** the hero art behind a dim gradient; the row's first tile is wide and the rest are
  portrait, so the focused game dominates; the hint bar lists every button in play; the three
  section tabs are small type under the row.
- **Wrong for us:** too dense for 7"; mixed tile shapes make the row ragged; small gray labels
  (`Recent Games`, `What's new`) would be unreadable at arm's length.

## Launchers already tried

### Console Launcher (likeich/console-launcher)
The GitHub repo is not source: the app is closed, the repo holds its theme packs, dock icon
packs, emulator and platform registries, and a market index. Two lessons from it:
- Its dock icon packs are WebP bitmaps (`dock-themes/*/pack.json`, `"extension": "webp"`),
  scaled to whatever the layout asks. That is the soft look the owner dislikes. Ours are vector.
- Its feature list (three home layouts, theme packs, icon shapes, ROM scanning, RetroArch cores,
  Discord, RetroAchievements) is the Nova problem in console clothes. Nothing on it is a goal.

### The real Switch home (the baseline the owner named)
Flat light gray (`#EBEBEB`) or dark gray (`#2D2D2D`) background, no hero art; twelve square
tiles in one row then an *All Software* tile; a dock of round icons; a hint bar; a hard focus
ring in cyan with a slow pulse; one rounded sans at two sizes. The mock-ups add hero art to this;
the owner picked both mock-ups, so hero art is wanted.

## What we take
1. **The Switch 2 mock-up's anatomy** as the home screen: status top-right, one row of covers,
   title above the focused cover, round dock, hint bar.
2. **v1 ships the real Switch's flat theme background** (light and dark). The hero backdrop
   from the mock-ups comes once covers exist, as a theme option, because without full-res art
   it is a blur, and blur is what we are escaping.
3. **One tile shape.** Square (1:1) by default: Android app icons sit naturally in a square, and
   SteamGridDB serves 1:1 grids. The owner may prefer the 3:4 covers of the Switch 2 mock-up;
   it is a single constant (`Look` *Tiles*), asked as Q-tile.
4. **At most five dock buttons** (All apps, Settings, Files, Notifications, Power). Fewer if the
   owner agrees.
5. **No tabs in v1.** One row: pinned apps in the owner's order, then the rest by recency.
   Categories stay in *Later*.
6. **Every glyph is vector** (Material Symbols), every cover is fetched at or above its pixel
   size on the device, type is a bundled variable font. This is the whole point.

## Rejected, and why
- Profile avatar, page dots, music line, folder tiles, mixed tile shapes: clutter.
- Theme packs and icon packs: the look is the product; one look, two modes.
- Emulator and ROM awareness: the emulators are apps; they get a tile like any other.
