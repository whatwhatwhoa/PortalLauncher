"""Stress test for the launcher on the Portal (F28): random controller input over adb while
watching for crashes, ANRs, restarts, memory growth and jank.

Standard library only:
    py -I tools/stress/stress.py --minutes 10
    py -I tools/stress/stress.py --pkg io.github.whatwhatwhoa.portallauncher.check --minutes 5
    py -I tools/stress/stress.py --standby      # the standby checks only
writes build/stress/<time>/ (actions.log, the launcher's warnings, screenshots of anything
odd) and prints a summary.

It presses whatever the weights pick, so it reaches every layer and every option, and it
changes the launcher's data (hides apps, deletes categories, clears art, moves the dock):
back up the app first and put it back after (`docs/decisions/0.4.0.md` D28.1 has the
commands). What keeps it off everything else on the owner's device:
- a key goes only to the launcher: before each one, the window with focus must be ours;
- after A, a tap or a long press it watches the focus for 1.5 s, and anything else that came
  up (an app, an uninstall dialog, a settings page, a picker) gets Home at once, never A;
- volume keys are sent only to put the volume back; the quick panel can move brightness and volume and toggle
  Do not disturb, so they are read at the start and put back at the end, with the density
  and the animator scale it changes on purpose.
Check the owner isn't holding the Portal before a run (`gotchas.md` *Scripted keys*).
"""
import argparse
import random
import re
import subprocess
import sys
import time
from collections import Counter
from pathlib import Path

ADB = r"C:\Users\Jim\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEBUG_PKG = "io.github.whatwhatwhoa.portallauncher"
ACTIVITY = "io.github.whatwhatwhoa.portallauncher.MainActivity"
STANDBY = "io.github.whatwhatwhoa.portallauncher.STANDBY"
ROOT = Path(__file__).resolve().parents[2]

K = dict(UP=19, DOWN=20, LEFT=21, RIGHT=22, A=96, B=97, X=99, Y=100, L1=102, R1=103,
         L2=104, R2=105, R3=107, START=108, SELECT=109, HOME=3, BACK=4)
# How often each key is picked: movement most, as a hand does; Home rarely, it ends everything.
WEIGHTS = dict(UP=10, DOWN=10, LEFT=12, RIGHT=12, A=9, B=9, X=3, Y=4, L1=4, R1=4, L2=2,
               R2=2, R3=2, START=6, SELECT=4, HOME=1, BACK=2)
# Keys that can open something outside the launcher are sent alone and watched after.
LAUNCHING = {"A"}
DENSITIES = [360, 314, 408, 471]
SETTINGS = ["system screen_brightness", "system screen_brightness_mode", "global zen_mode",
            "global animator_duration_scale"]


class Shell:
    """One adb shell kept open: a command costs a round trip, not a process start."""

    def __init__(self):
        self.p = subprocess.Popen([ADB, "shell"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                  errors="replace", bufsize=1)

    def run(self, cmd):
        self.p.stdin.write(f"{cmd}; echo __END__\n")
        self.p.stdin.flush()
        out = []
        for line in self.p.stdout:
            if line.strip() == "__END__":
                break
            out.append(line.rstrip("\r\n"))
        return "\n".join(out)


class Run:
    def __init__(self, args):
        self.args = args
        self.pkg = args.pkg
        self.sh = Shell()
        self.rng = random.Random(args.seed)
        self.out = ROOT / "build" / "stress" / time.strftime("%Y%m%d-%H%M%S")
        self.out.mkdir(parents=True, exist_ok=True)
        self.log = (self.out / "actions.log").open("w", encoding="utf-8")
        self.steps = 0
        self.foreign = Counter()
        self.findings = []
        self.pss = []
        self.start_time = self.sh.run("date +'%m-%d %H:%M:%S.000'").strip()
        self.pid = self.pidof()
        self.saved = {k: self.sh.run(f"settings get {k}").strip() for k in SETTINGS}
        self.volume = self.media_volume()
        self.note(f"start pkg={self.pkg} pid={self.pid} seed={args.seed} settings={self.saved} volume={self.volume}")

    # --- what the device says -------------------------------------------------------------

    def note(self, text):
        line = f"{time.strftime('%H:%M:%S')} #{self.steps} {text}"
        self.log.write(line + "\n")
        self.log.flush()
        if self.args.verbose:
            print(line)

    def finding(self, text):
        self.findings.append(f"#{self.steps} {text}")
        self.note("FINDING " + text)
        print("FINDING", text)
        self.screenshot(f"finding-{len(self.findings)}")

    def pidof(self):
        return self.sh.run(f"pidof {self.pkg}").strip()

    def media_volume(self):
        """The music volume of the output in use (`cmd media_session --set` sets another device's)."""
        m = re.search(r"streamVolume:(\d+)", self.sh.run("dumpsys audio | grep -A6 '^- STREAM_MUSIC'"))
        return int(m.group(1)) if m else None

    def focus(self):
        m = re.search(r"mCurrentFocus=(.*)", self.sh.run("dumpsys window | grep mCurrentFocus"))
        return m.group(1).strip() if m else "?"

    def ours(self, f=None):
        f = f or self.focus()
        return f"{self.pkg}/" in f

    def standby_on(self):
        """Standby keeps the screen on, and nothing else in the launcher does."""
        win = self.sh.run(f"dumpsys window windows | grep -A12 'Window{{.*{self.pkg}/' | grep -c KEEP_SCREEN_ON")
        return win.strip() not in ("", "0")

    def screenshot(self, name):
        with (self.out / f"{name}.png").open("wb") as f:
            subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)

    # --- input ------------------------------------------------------------------------------

    def keys(self, names, longpress=False):
        codes = " ".join(str(K[n]) for n in names)
        self.sh.run(f"input keyevent {'--longpress ' if longpress else ''}{codes}")
        self.note(("long " if longpress else "") + " ".join(names))

    def watch(self, seconds=1.5):
        """After something that can launch: anything not ours gets Home before another key."""
        end = time.time() + seconds
        while time.time() < end:
            f = self.focus()
            if f != "?" and "null" not in f and not self.ours(f):
                self.recover(f)
                return
            time.sleep(0.2)

    def recover(self, f):
        name = re.sub(r"^Window\{\S+ \S+ ", "", f).rstrip("}")
        self.foreign[name.split("/")[0]] += 1
        self.note(f"foreign {name}: Home")
        for attempt in range(4):
            self.sh.run(f"input keyevent {K['HOME']}")
            if self.pkg != DEBUG_PKG or attempt:
                self.sh.run(f"am start -n {self.pkg}/{ACTIVITY} >/dev/null 2>&1")
            time.sleep(1.0)
            if self.ours():
                return
        self.finding(f"couldn't get back from {name}")

    def ensure_ours(self):
        f = self.focus()
        if self.ours(f):
            return True
        if f == "?" or "null" in f:
            time.sleep(0.5)
            f = self.focus()
            if self.ours(f):
                return True
        self.recover(f)
        return self.ours()

    # --- what a step does ---------------------------------------------------------------------

    def pick(self, exclude=()):
        names = [n for n in WEIGHTS if n not in exclude]
        return self.rng.choices(names, [WEIGHTS[n] for n in names])[0]

    def step(self):
        r = self.rng.random()
        if r < 0.70:
            key = self.pick(() if self.pkg == DEBUG_PKG else ("HOME",))
            self.keys([key])
            if key in LAUNCHING:
                self.watch()
        elif r < 0.82:
            # Mashing: a burst as fast as input sends it, nothing that can launch in it.
            self.keys([self.pick(("A", "HOME")) for _ in range(self.rng.randint(6, 25))])
        elif r < 0.86:
            self.keys([self.pick(("HOME",))], longpress=True)
            self.watch()
        elif r < 0.91:
            x, y = self.rng.randint(0, 1919), self.rng.randint(0, 1079)
            self.sh.run(f"input tap {x} {y}")
            self.note(f"tap {x} {y}")
            self.watch()
        elif r < 0.94:
            x1, y1, x2, y2 = (self.rng.randint(100, 1820), self.rng.randint(150, 930),
                              self.rng.randint(100, 1820), self.rng.randint(150, 930))
            self.sh.run(f"input swipe {x1} {y1} {x2} {y2} {self.rng.randint(80, 400)}")
            self.note(f"swipe {x1},{y1} -> {x2},{y2}")
            self.watch()
        elif r < 0.965:
            self.standby_episode()
        elif r < 0.975 and not self.args.no_density:
            self.density_episode()
        elif r < 0.98:
            self.animation_episode()
        else:
            self.sh.run("sleep 0.6")  # a pause, so springs and fades finish once in a while

    def standby_episode(self):
        self.sh.run(f"am start -n {self.pkg}/{ACTIVITY} -a {STANDBY} >/dev/null 2>&1")
        time.sleep(0.6)
        self.note("standby open")
        presses = [self.pick(("HOME", "A")) for _ in range(self.rng.randint(1, 6))]
        if self.rng.random() < 0.5:
            presses += [presses[-1]] * 4  # and then wake it properly
        for p in presses:
            self.keys([p])
        time.sleep(0.3)
        if self.standby_on() and self.rng.random() < 0.5:
            self.keys(["HOME"] * 4 if self.pkg == DEBUG_PKG else ["B"] * 4)

    def density_episode(self):
        d = self.rng.choice(DENSITIES)
        self.sh.run(f"wm density {d}")
        self.note(f"density {d}")
        for _ in range(self.rng.randint(3, 12)):
            self.keys([self.pick(("A", "HOME"))])
        self.sh.run("wm density 288")
        self.note("density 288")
        time.sleep(1.0)

    def animation_episode(self):
        self.sh.run("settings put global animator_duration_scale 0")
        self.note("animations off")
        for _ in range(self.rng.randint(5, 20)):
            self.keys([self.pick(("A", "HOME"))])
        self.sh.run(f"settings put global animator_duration_scale {self.saved['global animator_duration_scale']}")
        self.note("animations back")

    # --- checks ---------------------------------------------------------------------------------

    def check(self):
        pid = self.pidof()
        if pid != self.pid:
            self.finding(f"process changed {self.pid} -> {pid or 'none'}")
            self.pid = pid
        crash = self.sh.run(f"logcat -b crash -d -T '{self.start_time}' | grep -c {self.pkg}").strip()
        if crash not in ("", "0") and crash != getattr(self, "crashes", "0"):
            self.crashes = crash
            self.finding(f"crash buffer: {crash} lines")
        anr = self.sh.run(f"logcat -b events -d -T '{self.start_time}' | grep -E 'am_anr|am_crash' | grep -c {self.pkg}").strip()
        if anr not in ("", "0") and anr != getattr(self, "anrs", "0"):
            self.anrs = anr
            self.finding(f"am_anr/am_crash events: {anr}")
        # A Home activity that finishes is rebuilt in a new task; each old one leaked (D28.1).
        finished = self.sh.run(f"logcat -b events -d -T '{self.start_time}' | grep wm_finish_activity | grep -c {self.pkg}/").strip()
        if finished not in ("", "0") and finished != getattr(self, "finishes", "0"):
            self.finishes = finished
            self.finding(f"the launcher finished itself: {finished} times")
        mem = self.sh.run(f"dumpsys meminfo {self.pkg} | grep -E 'TOTAL PSS|Activities:'")
        m = re.search(r"TOTAL PSS:\s+(\d+)", mem)
        a = re.search(r"Activities:\s+(\d+)", mem)
        if m:
            self.pss.append((self.steps, int(m.group(1))))
            self.note(f"pss {m.group(1)} kB, activities {a.group(1) if a else '?'}")
        if a:
            self.activities = max(getattr(self, "activities", 0), int(a.group(1)))

    def standby_checks(self):
        """D30.1: standby once ended by itself ~3 s after one press. Deterministic, timed."""
        results = []
        for trial in range(self.args.standby_trials):
            for key in ("B", "X", "R1", "UP"):
                self.ensure_ours()
                self.sh.run(f"am start -n {self.pkg}/{ACTIVITY} -a {STANDBY} >/dev/null 2>&1")
                time.sleep(0.8)
                if not self.standby_on():
                    results.append(f"{key}: standby didn't open")
                    continue
                self.keys([key])
                time.sleep(4.0)  # past the 2 s window and the 3 s of D30.1
                if not self.standby_on():
                    self.finding(f"standby ended by itself after one {key}")
                    results.append(f"{key}: ENDED after one press")
                    continue
                self.keys([key] * 3)
                time.sleep(0.5)
                still = self.standby_on()
                self.keys([key] * 4)
                time.sleep(0.5)
                if self.standby_on():
                    results.append(f"{key}: four presses didn't wake")
                    self.finding(f"four {key} didn't wake")
                else:
                    results.append(f"{key}: ok (three after the lapse {'kept it' if still else 'WOKE it'})")
                    if not still:
                        self.finding(f"three {key} after a lapsed count woke standby")
        return results

    def restore(self):
        self.sh.run("wm density 288")
        for k, v in self.saved.items():
            if v and v != "null":
                self.sh.run(f"settings put {k} {v}")
        # The volume keys, over the launcher with nothing playing, step the music volume.
        self.ensure_ours()
        for _ in range(16):
            now = self.media_volume()
            if self.volume is None or now is None or now == self.volume:
                break
            self.sh.run(f"input keyevent {24 if now < self.volume else 25}; sleep 0.3")
        if self.saved["global zen_mode"] == "0":
            self.sh.run("cmd notification set_dnd off")
        self.sh.run(f"dumpsys gfxinfo {self.pkg} > /data/local/tmp/stress-gfx.txt")
        gfx = self.sh.run("grep -E 'Total frames|Janky frames|percentile' /data/local/tmp/stress-gfx.txt; rm /data/local/tmp/stress-gfx.txt")
        warn = self.sh.run(f"logcat -d -T '{self.start_time}' --pid={self.pid} '*:W'") if self.pid else ""
        (self.out / "launcher-warnings.log").write_text(warn, encoding="utf-8")
        return gfx

    def main(self):
        self.ensure_ours()
        self.sh.run(f"dumpsys gfxinfo {self.pkg} reset >/dev/null")
        results = []
        try:
            if self.args.standby:
                results = self.standby_checks()
            else:
                end = time.time() + self.args.minutes * 60
                while time.time() < end:
                    if not self.ensure_ours():
                        break
                    self.step()
                    self.steps += 1
                    if self.steps % 40 == 0:
                        self.check()
                self.check()
        finally:
            gfx = self.restore()
            self.ensure_ours()
        print(f"\n{self.steps} steps in {self.out}")
        for r in results:
            print("  ", r)
        print("foreign windows (sent Home):", dict(self.foreign))
        if self.pss:
            print(f"PSS kB: first {self.pss[0][1]}, max {max(p for _, p in self.pss)}, last {self.pss[-1][1]}")
            print("most activities alive at once (before GC):", getattr(self, "activities", "?"))
        print(gfx)
        print(f"findings: {len(self.findings)}")
        for f in self.findings:
            print("  ", f)


if __name__ == "__main__":
    a = argparse.ArgumentParser()
    a.add_argument("--pkg", default=DEBUG_PKG)
    a.add_argument("--minutes", type=float, default=5)
    a.add_argument("--seed", type=int, default=int(time.time()))
    a.add_argument("--standby", action="store_true", help="the standby checks only")
    a.add_argument("--standby-trials", type=int, default=2)
    a.add_argument("--no-density", action="store_true")
    a.add_argument("-v", "--verbose", action="store_true")
    sys.exit(Run(a.parse_args()).main())
