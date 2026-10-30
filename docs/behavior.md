# Behavior

What each layer and control does, now. Present tense, no task IDs; the why behind each choice
lives in `decisions/`. The look (canvas, Home's anatomy, tiles, art, type, color, motion) and the
code are `architecture.md` *Look* and *Structure*. Split from `architecture.md` (2026-10-30).

## Home's layers

**Categories.** Home shows one category at a time: Favorites (the owner's list, in their
order), then Recent, Games, Entertainment, Social, Utilities until the owner renames, reorders,
deletes or adds to them; each but Favorites and Recent alphabetical; an empty one doesn't show.
Each has an id that never changes (the built-in ones' are their first names) and a name; the
list is stored in the preferences, Favorites always first. **Recent** holds the last 12 apps the
launcher opened (a tile on Home, the dock or All apps, once the start succeeded; 24 kept in
`recent`), latest first, never the dock's utilities (Settings, Files, the browser, Play Store)
or Android's settings pages. An install from before Recent gets it once, after Favorites
(`categories.recentAdded`), so a Recent the owner deleted stays deleted. A launch from Recent
puts that app first with focus on it, the tiles placed at once under the launch's reveal.
An app starts where a built-in table of well-known packages puts it (emulators,
frontends, stores and remote play → Games; video, music and books → Entertainment; chat →
Social; browsers, system apps and tools → Utilities), else where its own manifest does (game →
Games, audio and video → Entertainment, social → Social); anything else starts in **All apps
only**, off Home, so Home holds only what can be placed with confidence. That holds for the
apps there when the launcher first sorted them (their keys, `apps.sorted`, so a reinstall, a
restored setup or a new Portal keeps them placed); an app first installed later always starts
in All apps only, so a new install never changes Home by itself. Triangle → *Category…* puts it
in or takes it out of any number of categories, or All apps only takes it out of all; only a
change is stored, so putting it back where it started forgets it. An app can be a favorite and
in several categories at once. Favorites, Recent, the dock, hidden apps and category choices
are kept by app key, so an app uninstalled for now keeps its places and has them back when reinstalled;
a key whose activity is gone follows its package's one launchable activity (an icon switched
by alias), never a guess between two. Art is looked up for favorites, Recent's apps and the
Games category whatever its name. **B on Home opens the categories layer**: the names stacked on the content
margin, vertically centered, in heading type, the focused one in the text color and the rest
muted (a 150 ms fade as focus moves). Behind it Home zooms out: its art and row at 18% opacity, drawn at 0.94, showing the
focused category's row from its start, so it previews where A goes; the name, the dock and the
hints fade out. The layer slides in 1/16 of the screen from the left and fades. A goes in, B goes
back; a tap on a name goes in, a tap elsewhere goes back; a swipe up does nothing while it is
open. The names scroll once there are more than fit. Bottom right, the hint Triangle Edit.
**Edit mode** (Triangle in the layer, kept apart so the layer stays clean): every category, an empty one
at half the muted color, and *New category* in title type after them; the hint reads Triangle Done, and
Triangle or B leaves. A on a name puts Rename, Move and Delete beside it as title-type pills, the focused
one filled and ringed (Favorites: Rename only); left and right choose, A does it, B closes them.
Rename turns the name into a field with Android's keyboard (Done or B keeps the name; left empty,
the old one stays); Move colors the name with the accent, up and down move it (never above
Favorites), A or B puts it down; Delete asks for a second A, and its apps go to All apps only (an
app in another category too stays there). *New category* starts a name to type; left empty, it
goes away. A focused empty category says how to fill it. The window doesn't resize for the
keyboard, so the list gets 320 dp of room below while typing and scrolls the name above it.
The Home key mid-rename finishes it as B does, then closes the layer; keys go to the field only
while it is on screen.

**All apps.** A full-screen layer that slides up over Home: "All apps" and the count top-left,
below the status band (as on Home), then the focused app's letter (# for anything not starting
with a letter) between the drawn L1 and R1 bumpers (gone with *Button hints* off), muted; after
an L1/R1 jump to a new letter it shows in the text color for 1 s. Then an 8-column grid of 96 dp
tiles (12 dp corners, 24 dp gaps), each with its name 8 dp below in caption type; a favorite has
an accent dot on its corner. The focused row is kept second from the top: All apps opens already
scrolled there, a held direction glides the grid at an even speed with the focus, and a touch
drag or fling that leaves the focus out of view moves it (silently) to the second row in view.
At the bottom, a 56 dp hint bar: a hairline, then the hints: Square Quick settings on the left,
X (Favorite / Unfavorite), Y Search and Triangle right-aligned (Home's bottom row has no
hairline). A label that changes in place keeps the room of its longest form, so its glyph holds
still. Opens with Y, the dock's first button, or a swipe up. *Notifications* is listed as an app
(a bell): it pulls Android's shade down, and can be docked or favorited like any app.
**Search**: Y (or a fresh Up from the first row) moves focus to a letter strip in the title's
place: A–Z and # in title type, muted, the focused one ringed. Left and right choose (wrapping),
L2/R2 jump to A and #, A adds the letter, X takes back the last one; Down, Y or B go to the
results with the search kept (the hints read X Delete, Y Done). The grid keeps alphabetical order
and shows the apps whose label or one of its words starts with the search, whose words' initials
hold it ("gta"), or, from two letters on, that hold it anywhere; accents, case, spaces and
punctuation don't count. Focus starts on the best match. B in the results drops the search, B
again closes; All apps always opens without one.
Hidden apps are left out (the count says how many); *Show hidden apps* in the options sheet
brings them back faded to 40% until it is turned off or the launcher restarts.

**Options sheet.** Triangle (or a long press) on a carousel tile, a dock app or an All apps tile opens a
360 dp panel from the right over the screen dimmed to 60% (600 dp on the Art… page, sliding wider): the app's icon and name, then its
options as 48 dp rows (the focused row filled with the rule color and ringed); no hints (A and B
are known). Options depend on where it was opened: Move (favorites on the Favorites row, and
the dock), Favorite or Unfavorite (not from the dock), Add to dock (grayed "(full)" at six; A
only bumps), or for a docked app Move in dock (not from the dock: Home, the dock button
picked up) and Remove from dock, Category…
(a page of every category but Favorites and All apps only, scrolling when long, each the app is
in dotted; A puts it in or takes it out and stays, B goes back), Hide
or Unhide (not from the dock), Art…,
App info, Uninstall (not for system apps; the system asks to confirm), Show or Hide hidden apps (All
apps only), and last, always, *Launcher settings…* (*Settings*). Hiding an app also
takes it off the row and the dock. *Move* picks the tile up: it
grows to 1.14 with a steady ring, left and right shift it; A, B, Triangle, a tap or a swipe up
puts it down. An App info or
Uninstall that Android refuses says so in a toast.

## Settings and sounds

**Settings.** One panel, *Launcher Settings*, from Triangle → *Launcher settings…*, the dock's last
button, or the gear beside Portal in Android's Default apps → Home app: 760 dp (about 70%) at the
right, over the screen *undimmed* so a change is judged live against Home beside it. As the
Switch's: the categories down the left, a hairline, the chosen one's rows. It opens on
the categories at Look; up and down show each page at once, A or right goes into the rows, B
comes out, B again closes; L1/R1 turn the category from either column, Triangle closes from anywhere,
so does a tap outside. A row is 48 dp: the name left, the value right (the accent with a
swatch). A *value* row shows its value between two drawn chevrons and left and right change it
(wrapping; held, once), A steps it on; an *action* row has no chevrons and A does its one thing, a second A
first for one that can't be undone ("A again to confirm"; any other key cancels). A tap acts as
A. Every change applies at once and is kept (`look.*`, `home.*`, `art.fetch` in the `launcher`
preferences).
- *Look*: Theme (System, Light, Dark), Accent (left and right step through the presets, then
  the last custom color, *Custom*, kept in `look.customAccent`; A opens the accent picker in the
  page's place), Backdrop, Covers on tiles (off: icons on Home,
  whatever art is picked), Big name (Metro, Fitted), then the effects: Background gradient, Focus glow, Tile depth, Hero
  reflection, Focus pulse (Off, Slow, Normal); the page scrolls.
- *Home*: Clock (System, 12-hour, 24-hour), Battery percent (off: only the fill), Button hints,
  Carousel wraps (off by default: the row stops at its ends), Home key goes to (the first
  category Home shows, by its name: its first tile; or Where you left, which also reopens the last
  category after a restart),
  Home screen play controls (off by default; see *Controller map*), Hold Home for standby (see *Standby*).
- *Sound*: Sounds (on by default), Family (Soft, Glass, Crisp; Soft by default), Volume (Quiet,
  Low, Medium, Loud; Low by default). A new family or volume plays Select in itself (`sound.*`).
- *Art*: SteamGridDB key (an action row: A takes a 32-hex-digit key found anywhere on the
  clipboard, a longer hex run not, checks it with one search and keeps it only if SteamGridDB
  accepts; with none on the clipboard A opens SteamGridDB's API page in the browser and the row
  says "Copy your key, then A". It shows Set, Not set, "Checking…", "Not accepted", or why a
  check failed: "Offline, try again" with no validated network, "Blocked: App info" when one is
  up but Android keeps it from the launcher (`gotchas.md` *Restricted networking*), "No answer,
  try again" otherwise), Fetch art (off: no lookups
  without being asked; Art… still searches), Clear all art (its size shown; confirmed; every file, match, scene and
  pick goes, and with Fetch art on it is all looked up afresh). Without a key, Art… has a
  *SteamGridDB key…* row that opens this one.
- *Apps*: Hidden apps (the count; A opens All apps with them shown, focus on the first), Reset
  the dock (confirmed; back to the utility apps; "Default" when it already is).
- *System*: Home app (Portal, Another app or Not set; while Portal isn't Home, A asks with
  Android's own "use as Home?" dialog and opens Default apps if it still isn't; while it is, A
  opens Default apps, the way back), Android settings, Updates (an action row; see *Updates*
  under `architecture.md` *Structure*; a failure reads as the key row's).
- Under System's rows, muted: the version, the font and the icons and their licenses, where
  the art comes from.

**Accent picker.** A on *Accent* turns the settings column into the picker, titled *Accent*: the
eight presets as 40 dp swatches in two rows of four (the one in use carries a dot in the
background color), then *Custom* ("Custom (in use)" while it is) with two slider rows, *Hue*
and *Intensity*, each a track painted with the colors it leads to at the theme's lightness and
a ring in the accent where it sits. Moving over a swatch wears it (coming up from the sliders
only moves there); left and right on Hue (5°)
or Intensity (0.01) make a custom color from the one shown (from White: chroma 0.12). Every
change applies at once, so the ring, the glow and the Accent dot show it live. A or B goes back
to the list, Triangle closes the settings. Taps: a swatch picks it, a track sets the slider there.

**Sounds.** Twelve short UI sounds in three families, synthesized by
`tools/sounds/make_sounds.py` into `assets/sounds/<family>/` (one key, E major pentatonic;
rising for forward and open, falling for back and close; nothing under 300 Hz). All 36 load once
per process into one `SoundPool` (`Sounds.kt`), shared by Home and standby, on the media stream,
so the volume buttons set them with everything else; none play while Sounds is off or Do Not
Disturb is on (as Android last announced it). The screens make no
sound calls: every controller action and a Home press at home run through
`LauncherState.sounding`, which compares where the launcher was before and after (`Spot`: the
open layers, focus, switches, sliders) and picks one (`cue`): a layer more opens (the categories
layer and All apps swish, the rest swell), a layer less is back (A choosing is select), a moved
focus ticks, a move that moved nothing bumps, a switch toggles up or down, a slider ticks at a
pitch that rises with its value. Launching an app (once it started; one that couldn't bumps
beside a toast) and waking from standby play their own; held
moves and bumps are spaced (40 ms, 250 ms), and a held key that acts once bumps while held.
Touch is silent except a launch; coming back from an app is silent.

## Quick panel

**Quick panel.** Square (Select) on Home or All apps, the pull-down's everyday controls
from the controller (`decisions/M7-quick-settings.md`). A 760 dp panel drops from the top
(a quarter of its height, fading) over the screen dimmed to 60%, its bottom corners rounded:
- The player, one line 16 dp under the top edge: Previous, Play / pause, Next as round 56 dp
  buttons (the dock's size, 16 dp apart; media keys to the last player) between drawn L1 and R1
  bumpers (gone with *Button hints* off); Play / pause fills and shows pause while music plays.
  L1 and R1 skip a track per press from any row. Then the seek bar, the row's last stop: title ·
  artist (muted) over the time in, a track and the length (the times grow to fit), from the most
  recent media session, read on opening and when the player reports a change, the time counted
  on every 0.5 s while it plays; left and right seek 10 s, up or down leave, and so does left
  when it can't seek; a tap jumps. Without notification access
  (*Grants*) it is a small *Enable seek* pill at the right end.
- A faint 1 px line in the rule color, then the settings; the panel opens on Brightness.
- *Brightness* and *Volume* sliders, 48 dp rows like the settings': a sun or speaker icon, a 6 dp
  accent track, the percent. Left and right step them (brightness to the next 5% mark; volume one of the Portal's 15
  levels), held repeats; a tap on the track sets it. Brightness is the 1–255 value straight, as
  the Portal's slider shows it; moving it turns auto-brightness off.
- Toggles, the same buttons filled in the text color when on: Auto brightness, Do not disturb
  (priority only while on).
- Shortcuts, the same buttons: Notifications first (a bell; pulls Android's shade down, through
  standby's accessibility service while it is on, else Android's own call), then system pages
  for what a launcher may not change: Wi-Fi, Bluetooth, Battery, Controller settings (AYN's page,
  by its activity, only where it exists; the Portal glyph, drawn for this launcher), Android
  settings (a gear).
- A muted line at the bottom: what the focused control still needs, else its name.
Icons: Material Symbols (Rounded, filled; Apache 2.0) as `res/drawable/ic_sym_*`, as in
Android's panel, tinted from the palette (`ic_sym_portal` is ours).
Up and down move between the five rows, each row keeping its column while the panel is open,
left and right along a row, A toggles or opens, B or
Square closes, a tap outside closes. **Grants:** brightness and auto-brightness need Android's *Modify
system settings* for the launcher, Do not disturb its *Do Not Disturb access*; until granted the
slider row reads "A: let the launcher change it" and A (on the row or the toggle) opens that
page. The seek bar needs *Notification access* (Android shares media sessions only with a
notification listener; `MediaListener` reads nothing); A on *Enable seek* opens its switch,
and where Android restricts the app (an APK from a file) the note then says how to allow it
and the next A opens App info, alternating.
Nothing is asked for on install. Values are read on opening, after every change and
when the launcher comes back to the front. Home and All apps only, not over a game.

## Controls

**Controller map.** Keys become actions once, at the activity (`Input.kt`), so an ABXY swap is
one table. A is the bottom button (Xbox layout, `BUTTON_A`). The left stick is read by the
launcher (`Stick.kt`), the hat stays Android's D-pad keys: only the stick's dominant axis moves,
a push past half steps once, held it repeats, faster the further it is pushed. Home: D-pad or
left stick moves along the row, down to the dock and back up; A opens; Y opens All apps; L1/R1
turn to the category before or after, one per press, its row from the start, stopping at the
ends (with *Home screen play controls* on they skip a track per press instead and R3 plays or
pauses, media keys to the last player);
L2/R2 jump to the ends; Triangle opens the options sheet; B opens the categories layer (up and down,
L2/R2 to the ends, A in, B back, Triangle edit mode); Square opens the quick panel; X does nothing. Carousel wraps on,
left and right go round the row's ends. All apps: D-pad
moves in the grid; A opens; X toggles favorite; Y searches; Triangle opens options; Square the
quick panel; B drops a search, else closes; L1/R1 jump to the previous or next letter (held,
they keep jumping). Art…: Triangle closes the sheet; Up from an image grid's top row goes back
to the preview. The Home key's first press closes whatever is open (the settings, the quick
panel, a sheet, a tile being moved, the categories layer, All apps) and keeps the place on the
row; pressed with nothing open while already home, it returns to the first category's start
(unless *Home key goes to* is Where you left). On the way back from an app it keeps the
category and focus (the window has focus and the intent doesn't bring Home to the front,
Launcher3's test), also when Android ended the launcher meanwhile: each launch stores Home's
place (category, focused app, row or dock, `home.place`) with the boot it happened in, a
launcher started again in that boot reopens it, and back in front the place is dropped. Keys
and touches that come while Home isn't in front (between a launch and the app's window, under
Android's dialogs and the pickers) do nothing. Touch: tap opens, long press opens options, drag
scrolls, swipe up opens All apps (with a tile picked up, it puts it down), back closes the top
layer (if any).

## Standby

**Standby.** Holding Home opens standby, from anywhere, a game included: a rest that doesn't
lock, like an always-on display, in a window of its own over whatever was showing
(`StandbyActivity`, its own task, kept out of Recents). The screen goes black at once and the
block fades in. Black; the clock large (the display
type at 30%) in white at 72%, the date and the battery under it at 52%, and four rings. The
window keeps the screen on at the owner's brightness. The block moves to a random spot within
160 × 64 dp of the center each minute, for the OLED. Four presses of the same button within
2 s of the first leave it (a D-pad push counts as its direction, a short Home press as Home);
each fills a ring, and another
button starts the count again at one; while a count runs, "Press the same button 4 times to
wake" shows under the rings (faded, none with *Button hints* off). On the fourth the chime plays
and the block fades; the window stays on as a sink for keys until the waking key is let go (at
least 0.4 s, at most 1 s), so a fifth quick press lands nowhere, then goes without an animation
and leaves what was under it as it was: the game, or Home with its category, focus and open
layers. Home under standby is stopped and draws nothing. Touches, the back gesture,
the sticks and the triggers (axis and L2/R2 button both) are swallowed without counting; volume
keys still set the volume; a Home hold does nothing. It also ends when it goes out of view (the
screen turned off, an app over it). It never shows over the lock screen: locked, a hold is a
short press. Holding Home needs the launcher's accessibility service (`HomeHoldService`, listed
as *Portal: Home button and notifications*;
why: `decisions/0.2.0.md` D19.1): it takes every Home press, opens standby on a hold (Android's
long-press time), hands a shorter one back as Android's Home, and reads no screen content; the
quick panel's Notifications uses it too. While
it is on, each Home press shows "Hold Home for standby" in a pill for 0.6 s. Its switch
(`HomeHoldSwitch`, why: `decisions/0.4.1.md` D35.1): the row's A opens Android's
accessibility list at its row; still off and restricted by Android (an APK from a file), the
row says "Restricted: A, then ⋮ → Allow" and A opens App info; back from there it says
"Allowed? A: switch it on" and A opens the list again, never App info twice in a row. Seen on, it is wanted (`home.holdWanted`); seen going
off while the launcher runs, not. A force stop turns it off: found off while wanted, it goes back
on by itself where WRITE_SECURE_SETTINGS was granted over adb (the row then flips it in place),
else the first Home press shows "Standby is off: …" for 4 s, once per start.
