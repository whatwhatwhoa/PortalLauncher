# PortalLauncher — agent rules (keep this file short; it loads every session)

A clean, welcoming Android home launcher for the owner's AYN Odin 2 Portal: controller first,
full Android underneath (every app, settings, notifications), far fewer features than Nova.
Kotlin + Jetpack Compose, built from the command line on a Windows ARM laptop. Public on GitHub (MIT).
Why and where it is going: `docs/architecture.md`, *Goals*. The work is the owner's feature
board: `docs/TASKS.md`.

## Read before working
| Task | Read |
|---|---|
| Any | `docs/HANDOFF.md` (state, next, waiting on the owner) |
| Pick or plan work | `docs/TASKS.md`: the first open card or task under *Working on*, or the owner's explicit ask |
| Reviewing the owner's design input | the files they point at; earlier reviews: `docs/decisions/M1-design.md` |
| The look, layout | `docs/architecture.md` *Goals*, *Look* |
| What a screen or button does, controller feel | `docs/behavior.md` |
| Build, SDK, adb, the device | `docs/device.md`; `docs/gotchas.md` |
| Why something is the way it is | `docs/decisions/INDEX.md`, then the one entry |
| Hand-testing with the owner | `docs/VERIFY.md` |

**Each fact has one home.** Rules here · state → `HANDOFF.md` · tasks → `TASKS.md` · how it
works → `architecture.md`, `behavior.md` (build and device: `device.md`) · why → `docs/decisions/` · traps → `gotchas.md` · built-not-clicked →
`docs/VERIFY.md` · reviews of the owner's design input → `docs/decisions/`. Link, don't copy.

## Hard rules
1. **The Portal is the owner's daily device.** Install, launch and read logs over adb freely;
   change a device setting (`settings put`, `wm`, setting the default Home) only when the owner
   asks, and write the undo command in the reply. Always keep Launcher3 installed as the fallback.
2. **The owner's design input stays out of the repo.** Mock-ups, wallpapers and third-party APKs
   are read where the owner put them, never committed (`concepts/` is gitignored, owner
   2026-10-25); review notes go in `docs/decisions/`.
3. **Clean beats complete.** A feature earns its place by making the home screen calmer or more
   welcoming; when in doubt, leave it out and add a line to the board's *Feature list*.
4. **Crisp at 1920×1080.** Vector or full-resolution assets only, real typography, no blurry
   scaled bitmaps: the reason this project exists is that other launchers look off.
5. **Commit wording**: "commit" = local only; "commit to github" = commit + push.
6. **Small diffs.** One task per session unless the owner bundles several.
7. **Secrets stay in `local.properties`** (gitignored), e.g. `steamgriddb.apiKey` and the
   `release.*` signing lines. Never copy a key or password into code, notes, a commit or a reply;
   the repo is public. The release keystore lives outside the repo (`decisions/M8-release.md`).

## Commands
- adb: `C:\Users\Jim\AppData\Local\Android\Sdk\platform-tools\adb.exe` (device `82ee7769`)
- SDK packages: `...\Sdk\cmdline-tools\latest\bin\android.exe --no-metrics sdk list|install`
  (replaces `sdkmanager`)
- Build (Bash): `JAVA_HOME="C:/Users/Jim/AppData/Local/Android/jdk-21" ./gradlew assembleDebug lintDebug`
  → `app/build/outputs/apk/debug/app-debug.apk`; `assembleRelease` → `.../release/app-release.apk`
  (no art key, signed with the release key; can't be installed over the debug build)
- Install + run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, then
  `adb shell am start -n io.github.whatwhatwhoa.portallauncher/.MainActivity`
- Log: `adb logcat --pid=$(adb shell pidof io.github.whatwhatwhoa.portallauncher)`;
  screenshot: `adb exec-out screencap -p > shot.png`
- GitHub: `whatwhatwhoa/PortalLauncher`, public; a release is tag `v<version>` + `PortalLauncher-<version>.apk`
- Notes audit: `py -I C:\Users\Jim\.claude\skills\notes-architecture\scripts\notes_audit.py .`

## End of session
1. Tick the card or task in `docs/TASKS.md` (date, one clause); its detail goes to a decision
   entry (a card's: `decisions/F<n>-<slug>.md`, or `decisions/<version>.md` for small ones).
2. New knowledge to its one home (table above); a finished feature is described in
   `architecture.md` or `behavior.md` in present tense, no task IDs.
3. Rewrite `docs/HANDOFF.md` from scratch: state ≤ 10 lines, next, waiting on the owner,
   uncommitted.
4. Build state goes in the commit message, not the notes.
5. Run the notes audit; fix what it flags.

## Style
Kotlin, Jetpack Compose, one screen per file; comments explain why, not what. American English
in the UI, code and notes (owner, 2026-10-25); stored keys keep their old spelling.
