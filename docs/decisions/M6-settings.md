# M6 — Settings and effects (2026-10-25)

The owner's asks, the choices and why. How it works now: `architecture.md` *Color*, *Overlay
effects*, *Motion*; `behavior.md` *Settings*, *Accent picker*.

## The owner's asks (2026-10-25)
- A few palettes of favorite accent colors to pick from.
- The focus "breathing" slower (half speed: B1.1) and calmer.
- "Whatever settings should be common for this type of interface."

## The settings, proposed and kept (2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-settings*, *V-settings-more*).
Five groups, every row a controller row, changes live, defaults = the look before the panel
existed: *Look*, *Home*, *Art*, *Apps*, *System*.
**Left out** (clean beats complete; to the feature list if asked): UI sounds, an idle
screensaver, icon packs, grid size, backup and restore of settings.

## Owner's answers (2026-10-25)
Status: accepted 2026-10-25.
- **Effects settled:** "all effects are good so far": the three stay as built, on by default.
- **A dock button too:** a gear after the dock's apps opens Launcher Settings. It is last so the
  apps keep their places and All apps stays first; it has no options sheet and doesn't move.

## Effects (T6.1, 2026-10-25)
Status: accepted 2026-10-25 (B3.1).
- *Background gradient*: in the dark the spot is the accent at 12% over the background, so a
  palette tints it; in the light it is near white, tinted 6% only by a hued accent (White's
  accent is near-black there and would gray it). Drawn with dithering, as a precaution: a
  black-to-#1F1F1F ramp across 1900 px is the kind that bands (not compared without it).
- *Focus glow*: drawn only *outside* the ring (`BlurMaskFilter` OUTER), so the 3 dp gap between
  tile and ring stays clean and the ring stays crisp. Everything with a ring gets it, sheet rows
  too.
- *Tile depth*: a shadow can't show on Black, so in the dark the tile catches light instead (the
  top hairline); in the light it casts a soft shadow. Not on sheet rows.
- **On by default**, unlike "defaults are the old look": the effects were to be judged by
  switching them *off*, so the first look has them.

## The panel (T6.2, 2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-settings*). The 360 dp column became a 760 dp
panel in categories with F25 (`F25-settings.md`); undimmed still, for the reason below.
- **A column over an undimmed Home, not a full-screen page.** The proposal said a full-screen
  page with Home judged behind, which can't both be true. The options sheet's 360 dp column
  keeps the focused tile, the name, the gradient and most of the dock in view while a value
  changes. The value sits between two drawn chevrons: the font isn't trusted to have ‹ ›.
- **Where it opens:** the last row of every options sheet (the launcher's own, after everything about
  the app), the dock's gear, and Android's gear beside Portal in Default apps → Home app
  (`APPLICATION_PREFERENCES` on the activity). Named *Launcher Settings* (owner, B2.4), so it
  isn't mistaken for Android's.
- **Light accents at lightness 0.55,** not 0.60: at 0.60 Mint was 3.1:1 on `#EBEBEB`, at 0.55 the
  set is 3.8–4.4:1 (above 3:1 for a ring); dark stays at 0.80, about 11:1 on Black. The
  proposal's "dimmed and on-accent shades" aren't built: nothing draws them.
- **Theme is ours only;** Android's own dark setting is untouched.

## The other groups (T6.4, 2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-settings-more*).
- **Two kinds of row.** Art, Apps and System rows mostly *do* something rather than hold a
  value, so a row is a value row (chevrons, left/right) or an action row (no chevrons, A). The
  two that can't be undone, *Clear all art* and *Reset the dock*, take a second A: a dialog
  would be a new surface for one question.
- *Refresh all art* is left out: *Clear all art* with *Fetch art* on is the same thing (it all
  comes back on the spot), and refreshing only the automatic picks while keeping the owner's
  would need a second kind of record. Clearing takes the owner's picks too.
- *Hidden apps* doesn't get its own list: A opens All apps with the hidden ones shown, where
  Triangle → Unhide already works. One place to unhide.
- *About* is not a row: the version and credits are a muted line under the list. The license
  texts stay in `licenses/` (not in the APK; revisit when the repo goes public).
- *Start on Favorites or the last category* became *Home key goes to*: Favorites (as the Switch
  does) or Where you left, which also restores the last category after a restart. "Start" only
  means something at those two moments.
- *Carousel wraps* is off by default (the row stopped at its ends before).
- *Fetch art* off stops only the automatic lookups; Triangle → Art… still searches, as it is asked.
- *Home app* shows whether Portal holds the role and opens Android's Default apps; the role
  itself is the owner's to change.

## Accents: eight and a picker (B4.1, owner, 2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-accent*).
"Give the users a minimal 8 palette to choose from with color picker."
- **The eight:** the proposal's six (White, Ice blue, Mint, Amber, Coral, Lavender), plus Lime
  (125°) and Rose (350°), which fill the two widest gaps in the hue circle (75–165° and
  295–28°). Chromas stay muted (0.08–0.15), so "minimal" holds: none shouts. In hue order after
  White, so the swatches read as a quiet rainbow. The trial's Cyan and Violet are gone.
- **The picker is hue and intensity only.** Lightness stays fixed per theme (0.80 / 0.55), as
  the presets' is, so no pick can make the ring unreadable on either background; a full
  color wheel or HSV square would let it, and needs a pointer the controller doesn't have.
  Intensity tops out at 0.18 (sRGB clips most hues before that at 0.80).
- **A page of the settings column, not a dialog:** same width, same rows, B back, so it feels
  like one more level of the list.
- **Live, like every setting:** moving over a swatch wears it, so the owner sees it on the
  ring and glow around the picker itself. Nothing to confirm.

## Motion pass (T6.3, 2026-10-25)
Status: as built 2026-10-25, owner to confirm (*V-transit*).
- **One timing for everything that comes and goes** (`Transit`): before, every layer used
  Compose's default springs and fades, which differ slightly per kind, so the sheet, the panels
  and All apps each felt a little different. Exits are shorter: leaving should never feel like
  waiting.
- **All apps** rises from below (where the swipe up starts) and sinks back only half as far;
  Home behind it steps back, so opening reads as a layer coming forward, not a page swap.
- **The pulse eases** (sine in and out) instead of running linear: a linear fade bounces off
  full and faint, which reads as a blink at Normal speed.
- **Kept:** the focus and lift springs (0.7 damping, a hint of overshoot, follows the stick
  well), the row's no-bounce slide, the 250 ms hero and backdrop crossfades.
