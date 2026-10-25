# F25 — Launcher Settings in categories (2026-10-25)

How it works now: `architecture.md` *Settings*. The panel before this, and why it is undimmed:
`M6-settings.md` *The panel*. Hand check: `VERIFY.md` *V-b7*.

### D25.1 · 2026-10-25 · Two columns at about 70% from the right
Status: built 2026-10-25; the owner: "Launcher settings look good" (same day). The
Look/Effects split undone by D25.2.

**Ask** (owner, card F25): "Bigger window (maybe even full screen or 70%), with categories and
submenus, similar to Switch. For example sounds, look and feel, etc." Answer 2026-10-25 (asked
with two sketches): **about 70%, at the right**, over the undimmed Home, rather than full screen.

**What**: a 760 dp panel (71% of the 1067 dp canvas; the art panel's 600 dp was the reference)
from the right edge, the same slide-in as before. The Switch's layout inside it: the categories
down the left (200 dp), a hairline, then the chosen category's rows. Moving through the
categories shows each one's page at once; A or Right goes into its rows, B comes back out, B
again closes. L1/R1 turn the category from either column; + closes from anywhere. The rows keep
everything from before: value rows (chevrons, left/right), action rows, the confirming second A.
The *submenu* is the accent picker: A on Accent opens it in the page's place, the categories
staying in view, and B returns to the rows.

**Why these choices**
- **70%, not full screen**: every change applies live, and the strip of Home left of the panel
  (the focused tile, the scene, the clock) is where a theme, accent or effect change is judged.
  Full screen would mean closing the panel to see what a change did.
- **Focus starts on the categories**, as the Switch's does, so the first thing in view is the map
  of what's there. Right is a second way in, because left and right do nothing else there.
- **B, not Left, comes back out**: in the rows Left already changes a value.
- **Look split into Look and Effects** (undone, D25.2): Look had nine rows, more than a page holds at 600 dp
  (eight 52 dp rows), so it would have been the only page that scrolls. Look keeps Theme,
  Accent, Backdrop and Covers on tiles; Effects has Background gradient, Focus glow, Tile depth,
  Hero reflection and Focus pulse. Every page now fits without scrolling (the scroll remains for a
  long one later).
- **The chosen category** shows lit and semibold with an accent bar at its left, as the Switch
  marks it; the ring is only ever on the one place focus is.
- **The credits** (version, font, icons, art sources) close the System page, where the Switch
  keeps its About, instead of closing one long list.
- **No drawn L1/R1 glyphs** next to the categories: the column already shows what L1/R1 move
  through, and Home stays calmer. Easy to add if the owner wants them, as on the Art… tabs.
- The accent picker's slider labels widened from 80 to 96 dp: "Intensity" was cut to "Intensit"
  (already so in the old panel, more visible in the wider one).

### D25.2 · 2026-10-25 · Look and Effects one category again (B7.1)
Status: built 2026-10-25, owner to try (*V-b7*).

**Ask** (owner, after trying F25): "Combine looks and effects. Also, some of the effects aren't
choosable." **Why none were**: a setting changes by its category, and the Effects category was
never given a branch, so its rows fell through to nothing. Folding them back into Look fixes it;
the category switch in `LauncherState.change` now names every category with no fallback, so a
new one can't swallow its rows again. Look has nine rows again and its page scrolls (the focused
row kept in view, as built for a long page).
