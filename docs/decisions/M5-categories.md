# M5 — Categories (planned 2026-10-25)

Owner's input, given early so it can be slotted in; nothing is built. `TASKS.md` M5 holds the
tasks; this entry holds the idea, the proposal and what is still open.

## The owner's idea (2026-10-25)
Carousel categories such as GAMES and ENTERTAINMENT. Double-pressing (or mashing) B takes you to
the categories, like the old PlayStation ribbon (XMB) or Metro: the category names in big type,
with the carousel floating behind them at low opacity ("abandon if it's a pain"). The owner is
not sure yet what it takes and wanted it in the plan early.

## Proposal
- **Home shows one category at a time.** The carousel is the current category's apps; Favorites
  is the first category and today's row. The category name sits small above the focused app's
  title, which M4 moves top-left in a display size, so the two read as one heading.
- **The categories layer** is Home zoomed out: the names stacked in a large display weight,
  left-aligned on the content margin (Metro), the focused one in the text color, the rest
  muted. Behind them the carousel of the focused category at about 15–20% opacity, changing as
  focus moves, so it previews where A will take you. A goes in (the layer fades, the carousel
  comes up to full opacity), B goes back.
- **The ghosted carousel is cheap:** the same composable drawn with a layer alpha; no blur
  needed (a `RenderEffect` blur exists from API 31 if wanted). It stays in.
- **Where categories come from:** Android's own app category (`ApplicationInfo.category`: game,
  audio, video, image, social, news, maps, productivity) sorts apps with no work from the owner;
  apps without one go to Utilities. The owner moves an app from the + sheet (*Category…*). Stored as
  overrides only, so new installs sort themselves. Empty categories don't show.
- **List (owner, 2026-10-25):** Favorites, Games, Entertainment (video and audio), Social,
  Utilities (the rest; the owner's name, replacing the draft's "Apps").
- **Why after M4:** the layer ghosts the M4 carousel and shares its heading, so building it first
  would mean redoing it. Its model (which category an app is in) is independent and could move
  earlier if wanted. Depth becomes M6.

## Answers (owner, 2026-10-25)
- **Key:** a single B opens the layer (B on Home did nothing, so there is no clash; no
  double-press window to tune).
- **List:** the five above, with Utilities for the rest. Renaming or adding categories wasn't
  asked for; it is on *Later*.

## Built (2026-10-25, T5.1 + T5.2)
- **Seeding:** the app's manifest category (`appCategory`, or the older `isGame`): game → Games,
  audio and video → Entertainment, social → Social, the rest and undeclared → Utilities. Read
  from the Portal's APKs: GameNative, Stardew, NTE and DroidDeck declare game; YouTube Music and
  Twelve audio; Glimpse image; Chrome social (so it sits alone in Social); YouTube and
  Crunchyroll declare nothing, so they start in Utilities until the owner moves them. No package
  table of our own: the owner moves the few that land wrong.
- **Moves are overrides only** (`categories` in the launcher preferences, key → category); moving
  an app back to its own category drops the entry, so new installs and updates sort themselves.
  Favorites isn't a target: it is the owner's list (Favorite in the sheet), so an app can be in
  Favorites and in Games at once. Move (reorder) is offered only in Favorites; the other
  categories are alphabetical.
- **The old fallback row is gone** (every app when there were no favorites): with no favorites,
  Favorites is empty and hidden, and Home opens on the first category that has apps.
- **Art follows the Games category**, the owner's moves included, not only Android's flag, so an
  app moved into Games gets its art looked up.
- **The label sits under the name, above the row**, not above the name as proposed: the name runs
  off the top edge (M4), so there is nothing above it. Title type, muted, on the content margin,
  12 dp above the grown tile.
- **The layer:** a sixth type size, *heading* (48/56 bold ×1.15), fits five names with room.
  Names on the content margin, vertically centered, focused in the text color, the rest muted
  (150 ms fade). Home behind keeps only its art and row, at 18% and scaled to 0.94 (zoomed out);
  the name, dock and hints fade out. The layer slides in 1/16 from the left and fades. A goes in
  (from the start of the row, unless it is the category already shown), B goes back; a tap on a
  name goes in, elsewhere goes back. Swipe up does nothing while it is open.
- **Home key:** pressed while home it returns to the first category's start; coming back from an
  app it keeps the category and the focus (until now it always jumped to the start). "While home"
  is the window having focus and the intent not bringing Home to the front, as Launcher3 checks:
  Crunchyroll's splash screen left the focus with us, so focus alone was not enough.
