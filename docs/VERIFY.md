# Verify

Built but not yet clicked on the device. Each check is one line: what to do, what should
happen. Ticked checks leave this file (their result goes in the commit or a decision entry).

## V-skeleton (built 2026-10-25)
Install: `CLAUDE.md` *Commands*. Launcher3 stays installed throughout.
- [ ] Launch from Launcher3's app list: a system dialog asks to make Portal the Home app.
      Answer either way; it asks only once.
- [ ] Home: the clock top-right, a row of square app tiles, a round dock, the hint bar.
- [ ] D-pad and left stick move along the row; the focused tile grows, gets a pulsing ring and
      its name above in the accent colour; the row keeps it in the first slot.
- [ ] L1/R1 jump five tiles, L2/R2 to the ends.
- [ ] Down goes to the dock (the focused button's name shows above it), Up comes back.
- [ ] A on a tile or a dock app opens it; Home comes back to Portal with focus at the start.
- [ ] Y, the dock's ⋮⋮⋮ button, or a swipe up opens All apps; B, Y or Back closes it.
- [ ] All apps: D-pad moves in the 8-column grid, the focused row stays second from the top.
- [ ] X in All apps marks a favourite (accent dot); back on Home the carousel shows only the
      favourites, in the order added. X again removes it.
- [ ] − cycles the accent (Cyan, Coral, Lime, Violet) and X on Home cycles the font (Inter,
      Nunito Sans, Manrope); the hint bar names the current pair; both survive a restart.
- [ ] Switch the system to light mode: background, text and accent all change, still legible.
- [ ] Icons look crisp at 1:1 (`adb exec-out screencap -p > shot.png`): no soft edges on tiles
      or dock buttons.
- [ ] Install or uninstall any app: the row and All apps update without a restart.
- [ ] Settings → Apps → Default apps → Home: pick Launcher3, press Home, Launcher3 is back.
