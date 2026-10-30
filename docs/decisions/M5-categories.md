# M5 — Categories (2026-10-25)

The owner's idea, the choices and why. How it works now: `behavior.md` *Categories*,
*Controller map*.

Status: as built 2026-10-25, owner to confirm the ghost opacity and type size (*V-categories*).

## The owner's idea (2026-10-25)
Carousel categories such as GAMES and ENTERTAINMENT. Double-pressing (or mashing) B takes you to
the categories, like the old PlayStation ribbon (XMB) or Metro: the category names in big type,
with the carousel floating behind them at low opacity ("abandon if it's a pain"). The owner was
not sure yet what it takes and wanted it in the plan early.

## Answers (owner, 2026-10-25)
- **Key:** a single B opens the layer (B on Home did nothing, so there is no clash; no
  double-press window to tune).
- **List:** Favorites, Games, Entertainment (video and audio), Social, Utilities (the rest; the
  owner's name, replacing the draft's "Apps"). Renaming or adding categories wasn't asked for;
  it is on the board's feature list.

## Choices (T5.1, T5.2, built 2026-10-25)
- **Seeding from Android's own app category** (`appCategory`, or the older `isGame`), so apps
  sort with no work from the owner. Read from the Portal's APKs: GameNative, Stardew, NTE and
  DroidDeck declare game; YouTube Music and Twelve audio; Glimpse image; Chrome social (so it
  sits alone in Social); YouTube and Crunchyroll declare nothing, so they start in Utilities.
  No package table of our own at first: the owner moves the few that land wrong.
- **Moves are overrides only** (`categories` in the launcher preferences, key → category), so new
  installs and updates sort themselves. Favorites isn't a target: it is the owner's list, so an
  app can be in Favorites and in Games at once. Move (reorder) is offered only in Favorites.
- **No fallback row:** with no favorites, Favorites is empty and hidden, and Home opens on the
  first category that has apps.
- **Art follows the Games category**, the owner's moves included, not only Android's flag, so an
  app moved into Games gets its art looked up.
- **The label sits under the name, above the row**, not above the name as proposed: the name runs
  off the top edge, so there is nothing above it.
- **The layer is Home zoomed out,** not a new screen: the same row drawn with a layer alpha
  (18%) and a 0.94 scale, previewing the focused category; no blur needed. A sixth type size,
  *heading* (48/56 bold ×1.15), fits five names with room.
- **Why after M4:** the layer ghosts the M4 carousel and shares its heading, so building it first
  would have meant redoing it.
- **Home key, "while home":** the window having focus and the intent not bringing Home to the
  front, as Launcher3 checks. Crunchyroll's splash screen left the focus with us, so focus alone
  was not enough.
