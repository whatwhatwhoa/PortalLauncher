# M6 — Settings and effects (planned 2026-10-25; T6.1, T6.2, T6.4 built 2026-10-25)

Owner's input, slotted early. `TASKS.md` M6 holds the tasks (T6.1 effects, T6.2 *Look* and T6.4 the other groups are
built, see the *Built* sections); this entry holds the proposal, what was built and why, and
what is still open.

## The owner's asks (2026-10-25)
- A few palettes of favorite accent colors to pick from.
- The focus "breathing" slower (half speed now: B1.1) and calmer.
- "Whatever settings should be common for this type of interface."

## Proposal
One full-screen page from the + sheet (and Android's launcher-settings intent), grouped, every
row a controller row: Left/Right changes a value, A toggles, B leaves. Changes apply live, so the
owner judges against Home behind. Defaults are today's look, so an untouched install is unchanged.

- **Look** (T6.2): theme (System / Light / Dark) · accent palette · backdrop · scenes on/off ·
  covers on tiles on/off · effects on/off (T6.1) · focus pulse (Off / Slow / Normal, Slow
  default after B1.1).
- **Home** (T6.4): clock 12 / 24 h (follows Android by default) · battery % on/off · button hints
  on/off · carousel wraps at the ends · start on Favorites or the last category (after M5).
- **Art** (T6.4): fetch art automatically on/off (the only network use) · refresh all art ·
  clear art storage, with its size.
- **Apps** (T6.4): hidden apps (unhide from a list) · reset the dock.
- **System** (T6.4): make this the Home app (opens Android's Default apps) · Android settings ·
  About (version, font and SteamGridDB credits, the licenses in `licenses/`).

**Accent palettes.** Each palette is one accent plus its dimmed and on-accent shades, checked for
contrast on Black and on a scene. A first set to judge on the device: White (default), Ice blue,
Mint, Amber, Coral, Lavender. The owner names their favorites and the rest go.

**Left out** (clean beats complete; to *Later* if asked): UI sounds, an idle screensaver, icon
packs, grid size, backup and restore of settings.

## Open
Nothing; the accents were answered below.

## Owner's answers (2026-10-25)
- **Effects settled:** "all effects are good so far": the three stay as built, on by default.
- **A dock button too:** a gear after the dock's apps opens Launcher Settings. It is last so the
  apps keep their places and All apps stays first; it has no options sheet and doesn't move.

## Built (T6.1, T6.2, 2026-10-25)
Built by jim-98 while jim-e6 did T4.6, T4.7 and B1.1; driven over adb with keys only (no taps),
then put back to the owner's look (White, System, Black, everything on). Not yet seen by the owner.

**Effects (T6.1).** Three, each its own switch, so each can be judged against its absence:
- *Background gradient*: a radial spot of the palette's `lift` color where the scenes are
  brightest (62% across, 45% down, radius 75% of the width), fading to the background. In the
  dark the spot is the accent at 12% over the background, so a palette tints it (this answers
  the open "does a palette tint the gradient?" with yes); in the light it is near white, tinted
  6% only by a hued accent (White's accent is near-black there and would gray it). Drawn on the
  native canvas with dithering, as a precaution: a black-to-#1F1F1F ramp across 1900 px is the
  kind that bands (not compared without it). A scene, Hero glow or a wallpaper covers it.
- *Focus glow*: the accent, blurred 16 dp, drawn only *outside* the ring (`BlurMaskFilter`
  OUTER), so the 3 dp gap between tile and ring stays clean and the ring stays crisp; 45% of the
  accent, breathing with the ring. Everything with a ring gets it, sheet rows too.
- *Tile depth*: a shadow can't show on Black, so in the dark the tile catches light instead: a
  1 dp hairline along its top edge, white at 22% fading out by the middle. In the light it casts
  a soft shadow (black 22%, blurred 8 dp, dropped 4 dp). Carousel tiles, dock buttons and the
  All apps tiles; not sheet rows.
- **On by default**, unlike the proposal's "defaults are today's look": T6.1 asks for the
  effects to be judged by switching them *off*, so the first look has them. One trip to
  Settings turns any of them off.

**The panel (T6.2), not a full-screen page.** The proposal said a full-screen page with Home
judged behind, which can't both be true. It is the options sheet's 360 dp column at the right,
over an **undimmed** screen, so the focused tile, the name, the gradient and most of the dock
stay in view while a value changes. Up/down move, left/right change (wrapping), A steps on,
B or + closes, a tap outside closes; the focused value sits between two drawn chevrons (the font
isn't trusted to have ‹ ›). The rows scroll: nine rows don't fit under the sheet's top margin,
and T6.4's groups will follow under their own headings. *Look* is the only group so far.

**Where it opens.** *Settings…* is the last row of every + sheet (the launcher's own, after
everything about the app), and Android's gear beside Portal in Default apps → Home app
(`APPLICATION_PREFERENCES` on the activity). A dock button waits on the owner (*Open*).

**Accents.** The proposal's six: White (default), Ice blue, Mint, Amber, Coral, Lavender, each a
hue and a chroma made into OKLCH. Light-mode lightness went from 0.60 to 0.55: at 0.60 Mint was
3.1:1 on `#EBEBEB`, at 0.55 the set is 3.8–4.4:1 (above 3:1 for a ring); dark stays at 0.80,
about 11:1 on Black. The "dimmed and on-accent shades" aren't built: nothing draws them yet.
The trial's Cyan, Lime and Violet are gone.

**The rest of *Look*.** Theme (System / Light / Dark; ours only, Android's own setting is
untouched). Backdrop: Black, Dark, Hero glow, then any image in `files/backdrops` (read again
each time the panel opens; the owner's trial wallpapers are still there). Scenes off falls back
to the hero band; covers off puts icons back on the Home tiles (the sheet header keeps the
cover). Pulse: Off / Slow (1.8 s, default) / Normal (0.9 s). Stored in the `launcher`
preferences as `look.*`; a key not stored yet reads as the default.

## Built (T6.4, 2026-10-25)
Built by jim-d6 alongside the owner's B2 tweaks; seen on the device by screenshot (the groups,
values and headings), not yet clicked by the owner. The panel is now *Launcher Settings* (owner,
B2.4), so it isn't mistaken for Android's.

**Two kinds of row.** The proposal's Art, Apps and System rows mostly *do* something rather than
hold a value, so a row is a value row (chevrons, left/right) or an action row (no chevrons, A).
The two that can't be undone, *Clear all art* and *Reset the dock*, take a second A; the value
reads "A again to confirm" meanwhile and any other key disarms it. A dialog would be a new
surface for one question.

**Changed from the proposal, and why.**
- *Refresh all art* is left out: *Clear all art* with *Fetch art* on is the same thing (it all
  comes back on the spot), and refreshing only the automatic picks while keeping the owner's
  would need a second kind of record. Clearing takes the owner's picks too; the confirm says so
  by being a confirm, the VERIFY step says it outright.
- *Hidden apps* doesn't get its own list: A opens All apps with the hidden ones shown, where
  + → Unhide already works. One place to unhide.
- *About* is not a row: the version and credits are a muted line under the list, reached by
  scrolling to the end. The license texts stay in `licenses/` (not in the APK; the repo is
  private, revisit if it goes public).
- *Start on Favorites or the last category* became *Home key goes to*: Favorites (today's
  behavior, as the Switch does) or Where you left, which also restores the last category after a
  restart. "Start" only means something at those two moments.
- *Carousel wraps* is off by default (the row stopped at its ends before).
- *Fetch art* off stops only the automatic lookups; + → Art… still searches, as it is asked.
- *Home app* shows whether Portal holds the role (read again when the launcher comes back to the
  front) and opens Android's Default apps; the role itself is the owner's to change.

Stored as `home.*` (clock, battery percent, hints, wraps, start), `home.category` and
`art.fetch` in the `launcher` preferences; a key not stored reads as the default.

## Accents: eight and a picker (B4.1, owner, 2026-10-25)
"Give the users a minimal 8 palette to choose from with color picker."
- **The eight:** the six built, plus Lime (125°) and Rose (350°), which fill the two widest gaps
  in the hue circle (75–165° and 295–28°). Chromas stay muted (0.08–0.15), so "minimal" holds:
  none shouts. In hue order after White, so the swatches read as a quiet rainbow.
- **The picker is hue and intensity only.** Lightness stays fixed per theme (0.80 / 0.55), as
  the presets' is, so no pick can make the ring unreadable on either background; a full
  color wheel or HSV square would let it, and needs a pointer the controller doesn't have.
  Intensity tops out at 0.18 (sRGB clips most hues before that at 0.80).
- **A page of the settings column, not a dialog:** same width, same rows, B back, so it feels
  like one more level of the list. Left and right on the Accent row still step the presets
  without opening it.
- **Live, like every setting:** moving over a swatch wears it, so the owner sees it on the
  ring and glow around the picker itself. Nothing to confirm.
- **Stored by name** for presets (the old values load unchanged), `custom:<hue>:<chroma>` else.

## Motion pass (T6.3, 2026-10-25)
- **One timing for everything that comes and goes** (`Transit` in `Look.kt`): 240 ms in with a
  decelerating curve, 180 ms out accelerating. Before, every layer used Compose's default
  springs and fades, which differ slightly per kind, so the sheet, the panels and All apps each
  felt a little different. Exits are shorter: leaving should never feel like waiting.
- **All apps** rises 1/8 from below (where the swipe up starts) and sinks back only 1/16; Home
  behind it scales to 96%, so opening reads as a layer coming forward, not a page swap.
- **The pulse eases** (sine in and out) instead of running linear: a linear fade bounces off
  full and faint, which reads as a blink at Normal speed; the sine lingers at both ends.
- **Kept:** the focus and lift springs (0.7 damping, a hint of overshoot, follows the stick
  well), the row's no-bounce slide, the 250 ms hero and backdrop crossfades. All of it still
  snaps with animations off.
