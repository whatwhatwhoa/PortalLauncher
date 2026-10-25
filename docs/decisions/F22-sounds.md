# F22 — UI sounds (2026-10-25)

### D22.1 · 2026-10-25 · Synthesized, not generated; three families to audition
Status: accepted 2026-10-25. The owner auditioned the three (`tools/sounds/make_sounds.py` →
`build/sounds/audition.html`): "all of these are great, make Soft the default, and users can
customize sound family".

**Ask** (owner, card F22): "use Nintendo Switch as the basis. While the Switch has playful sounds,
ours will be a bit more techy/modern." Answers 2026-10-25: try the agent's Python synthesis first
(an AI sound-effects trial only if it falls short); **on by default at a low level**, with a
switch and volume in Launcher Settings; **silent when the system is in Do Not Disturb**.

**Why synthesize**: UI sounds live or die by consistency (one key, one loudness, the same attack),
which text-to-sound tools are weakest at for 30–100 ms tonal ticks; a script is also tweakable
by a parameter and the files are ours to ship under the repo's license. AI generation stays the
fallback for the few textured sounds (launch, the layer's swish, wake).

**The set** (12; levels are the loudest 30 ms, set in `EVENTS`):

| Sound | When |
|---|---|
| move, edge | focus steps; pushing past the end of a row |
| select, back | A; B, close, cancel (the same gesture, rising vs falling) |
| layer open, layer close | B on Home: the categories layer, and back |
| sheet open | the options sheet, the quick panel |
| toggle on, toggle off | switches in the quick panel and settings |
| slider tick | volume, brightness: one file, its pitch raised with the value |
| launch | starting an app: the signature sound |
| wake | leaving standby: the only one with a tail |

**Rules the script keeps**: E major pentatonic; up for forward and open, down for back and
close; no reverb; most under 100 ms; a 300 Hz high-pass on everything (the Portal's speakers);
48 kHz mono 16-bit WAV, every file starting and ending at zero. Families: *Glass* (FM tines),
*Soft* (chorused sines), *Crisp* (clicks, filtered square and saw plucks).

### D22.2 · 2026-10-25 · How they're wired
Status: built 2026-10-25, installed, not yet heard on the Portal (`VERIFY.md` *V-sounds*).
How it works: `architecture.md` *Sounds*. The choices, against the plan above:
- **All three families ship and load at start** (36 WAVs, 1.3 MB in `assets/sounds/`, stored
  uncompressed so `openFd` works): the owner wanted the family a setting, and a new family has
  to play the moment it's picked, which a load on change (async) can't.
- **Media stream, not sonification** (`USAGE_GAME`): sonification rides the system or ring
  volume, which the Portal's volume buttons don't set from a launcher; one volume for
  everything is also the Switch's way. They don't take audio focus, so music keeps playing.
- **Do Not Disturb only** silences them (owner); the ringer mode is left alone. The quick
  panel's Quiet toggle is DND, so turning it on is silent and turning it off is heard.
- **Sounds from state, not calls**: `LauncherState.sounding` compares a `Spot` before and after
  each action, so the screens (and the next feature) need no sound calls; only launch and wake
  call `play`, and an action that asked for a sound gets no second one.
- **Levels 35% up** after the owner's first listen (2026-10-25: "not quite media player but not
  too soft"): Quiet 0.16, Low 0.34, Medium 0.68 of the file level; Loud stays 1, the ceiling.
  Then "still way too soft": the files themselves up, the UI sounds 50% (3.5 dB) and launch and
  wake 15% (1.2 dB), so every step and Loud rise too (`EVENTS` in the script). Then "launch
  is about perfect, but the moving through the UI is tiny soft, so another 30%": move and
  edge 2.3 dB more (-24.2), the rest unchanged. "It just doesn't feel louder, try doubling
  it": twice that again, 6 dB (-18.2), as loud as select.
- **Left out for now**: touch taps (only a launch sounds), typing, coming back from an app.
  Asked of the owner.
