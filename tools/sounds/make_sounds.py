"""Synthesizes the launcher's UI sounds (F22) and an audition page to pick them from.

Standard library only, so it runs with the laptop's plain Python:
    py -I tools/sounds/make_sounds.py
writes app/src/main/assets/sounds/<family>/<event>.wav (what the app plays), the same files
under build/sounds/, and build/sounds/audition.html to compare them.

Every sound is built from the same few parts in one key (E major pentatonic): rising for
forward and open, falling for back and close, short, no reverb tails. Nothing below 300 Hz
survives the master filter: the Portal's speakers can't play it and it only muddies the rest.
Noise is seeded, so a run always gives the same files.
"""
import base64
import json
import math
import random
import struct
import wave
from pathlib import Path

SR = 48000
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "sounds"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "sounds"  # what the app ships (Sounds.kt)

PITCHES = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8,
           "A": 9, "A#": 10, "B": 11}


def hz(name):
    midi = 12 * (int(name[-1]) + 1) + PITCHES[name[:-1]]
    return 440.0 * 2 ** ((midi - 69) / 12)


E5, GS5, B5, CS6, E6, GS6, B6, E7 = (hz(n) for n in
                                     ("E5", "G#5", "B5", "C#6", "E6", "G#6", "B6", "E7"))

# --- building blocks -------------------------------------------------------------------


def env(n, attack, tau, release=0.004):
    """Raised-cosine attack, exponential decay, and a short fade so every sound ends at zero."""
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    out = []
    for i in range(n):
        e = 0.5 - 0.5 * math.cos(math.pi * i / a) if i < a else math.exp(-(i - a) / SR / tau)
        if i >= n - r:
            e *= (n - i) / r
        out.append(e)
    return out


def glide(f0, f1, time):
    if f1 is None or time <= 0:
        return lambda t: f0
    return lambda t: f0 * (f1 / f0) ** min(1.0, t / time)


def tone(dur, f0, f1=None, glide_time=0.0, attack=0.002, tau=0.05, harmonics=((1, 1.0),),
         detune_cents=0.0):
    """Additive tone (band-limited by construction: every partial stays well under Nyquist)."""
    n = int(dur * SR)
    e = env(n, attack, tau)
    freq = glide(f0, f1, glide_time)
    ratio = 2 ** (detune_cents / 1200)
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        phase += 2 * math.pi * freq(i / SR) * ratio / SR
        out[i] = e[i] * sum(a * math.sin(k * phase) for k, a in harmonics)
    return out


def fm(dur, f0, ratio, index, index_tau, attack=0.001, tau=0.08, f1=None, glide_time=0.0):
    """Two-operator FM: the bright index dies away fast, leaving a near sine. Reads as glass."""
    n = int(dur * SR)
    e = env(n, attack, tau)
    freq = glide(f0, f1, glide_time)
    out = [0.0] * n
    pc = pm = 0.0
    for i in range(n):
        t = i / SR
        f = freq(t)
        pc += 2 * math.pi * f / SR
        pm += 2 * math.pi * f * ratio / SR
        out[i] = e[i] * math.sin(pc + index * math.exp(-t / index_tau) * math.sin(pm))
    return out


def svf(x, fc, q=0.707, mode="low"):
    """Zavalishin's state-variable filter; fc is Hz or a function of position 0..1."""
    k = 1.0 / q
    ic1 = ic2 = 0.0
    n = len(x)
    out = [0.0] * n
    fixed = not callable(fc)
    g = math.tan(math.pi * min(fc, SR * 0.45) / SR) if fixed else 0.0
    for i, v in enumerate(x):
        if not fixed:
            g = math.tan(math.pi * min(fc(i / n), SR * 0.45) / SR)
        a1 = 1 / (1 + g * (g + k))
        a2 = g * a1
        a3 = g * a2
        v3 = v - ic2
        v1 = a1 * ic1 + a2 * v3
        v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        out[i] = v2 if mode == "low" else k * v1 if mode == "band" else v - k * v1 - v2
    return out


def sweep(f0, f1):
    return lambda u: f0 * (f1 / f0) ** u


def whoosh(dur, f0, f1, q, attack, tau, seed=1):
    """Band-passed noise whose center sweeps from f0 to f1."""
    n = int(dur * SR)
    rng = random.Random(seed)
    noise = [rng.uniform(-1, 1) for _ in range(n)]
    e = env(n, attack, tau)
    return [a * b for a, b in zip(svf(noise, sweep(f0, f1), q, "band"), e)]


def click(f, q=1.5, dur=0.006, seed=7):
    return whoosh(dur, f, f, q, 0.0003, 0.0012, seed)


def pluck(dur, f0, tau, f1=None, glide_time=0.0, cutoff=(6000, 1500), odd=True):
    """Square-ish (odd partials) or saw-ish tone through a closing low-pass: a digital pluck."""
    parts = ((1, 1.0), (3, 1 / 3), (5, 1 / 5), (7, 1 / 7)) if odd else \
        tuple((k, 1 / k) for k in range(1, 9))
    raw = tone(dur, f0, f1, glide_time, attack=0.001, tau=tau, harmonics=parts)
    return svf(raw, cutoff if not isinstance(cutoff, tuple) else sweep(*cutoff), 0.8)


def mix(*parts):
    """parts: (offset seconds, samples, gain)."""
    n = max(int(o * SR) + len(s) for o, s, _ in parts)
    out = [0.0] * n
    for o, s, g in parts:
        k = int(o * SR)
        for i, v in enumerate(s):
            out[k + i] += g * v
    return out


def chorus(make, cents=5):
    """The same voice twice, a few cents apart: wider and softer than one sine."""
    return mix((0, make(-cents / 2), 0.5), (0, make(cents / 2), 0.5))


# --- the three families ----------------------------------------------------------------


def glass():
    def bell(f, dur, tau, index=1.6, ratio=3.0, index_tau=0.015):
        return fm(dur, f, ratio if f < 1500 else 2.0, index, index_tau, tau=tau)

    return {
        "move": bell(E6, 0.05, 0.012, index=1.2, index_tau=0.006),
        "edge": fm(0.08, E5, 1.0, 1.5, 0.01, tau=0.02, f1=E5 * 0.94, glide_time=0.04),
        "select": mix((0, bell(B5, 0.25, 0.06), 1), (0.045, bell(E6, 0.3, 0.08), 1)),
        "back": mix((0, bell(E6, 0.25, 0.05), 1), (0.045, bell(B5, 0.3, 0.07), 1)),
        "layer_open": mix((0, whoosh(0.24, 700, 3500, 1.2, 0.12, 0.05), 1),
                          (0.11, bell(E6, 0.35, 0.08, index=1.0), 0.5)),
        "layer_close": mix((0, whoosh(0.24, 3500, 700, 1.2, 0.12, 0.05, seed=2), 1),
                           (0.11, bell(B5, 0.35, 0.08, index=1.0), 0.5)),
        "sheet_open": mix((0, fm(0.2, E5, 3.0, 1.0, 0.05, attack=0.02, tau=0.07, f1=E6,
                                 glide_time=0.07), 1),
                          (0.06, bell(B6, 0.2, 0.05, index=0.8), 0.3)),
        "toggle_on": mix((0, bell(CS6, 0.15, 0.035, index=1.0), 1),
                         (0.03, bell(E6, 0.2, 0.045, index=1.0), 1)),
        "toggle_off": mix((0, bell(E6, 0.15, 0.035, index=1.0), 1),
                          (0.03, bell(CS6, 0.2, 0.045, index=1.0), 1)),
        "slider": bell(E6, 0.04, 0.01, index=0.8, index_tau=0.005),
        "launch": mix((0, bell(E5, 0.2, 0.09), 0.8), (0.035, bell(GS5, 0.2, 0.09), 0.8),
                      (0.07, bell(B5, 0.2, 0.09), 0.8), (0.105, bell(E6, 0.6, 0.22), 1),
                      (0, whoosh(0.4, 1500, 7000, 0.9, 0.15, 0.1, seed=3), 0.35),
                      (0.105, bell(E7, 0.45, 0.12, index=0.6), 0.25)),
        "wake": mix((0, bell(E5, 1.2, 0.4, index=1.0), 0.4), (0, bell(E6, 1.0, 0.35), 1),
                    (0.14, bell(B6, 1.2, 0.45), 0.8)),
    }


def soft():
    def s(f, dur, tau, attack=0.004, f1=None, glide_time=0.0):
        return chorus(lambda c: tone(dur, f, f1, glide_time, attack, tau,
                                     ((1, 1.0), (2, 0.12)), detune_cents=c))

    def lp(x, fc=4000):
        return svf(x, fc)

    return {
        "move": lp(s(B5, 0.06, 0.014, attack=0.003)),
        "edge": lp(tone(0.08, E5, E5 * 0.94, 0.03, 0.003, 0.025, ((1, 1.0), (2, 0.1))), 2000),
        "select": lp(mix((0, s(B5, 0.25, 0.06), 1), (0.05, s(E6, 0.3, 0.08), 1))),
        "back": lp(mix((0, s(E6, 0.25, 0.05), 1), (0.05, s(B5, 0.3, 0.07), 1))),
        "layer_open": mix((0, whoosh(0.26, 500, 2500, 0.7, 0.14, 0.06), 1),
                          (0.12, lp(s(GS5, 0.3, 0.1, attack=0.01)), 0.35)),
        "layer_close": mix((0, whoosh(0.26, 2500, 500, 0.7, 0.14, 0.06, seed=2), 1),
                           (0.12, lp(s(E5, 0.3, 0.1, attack=0.01)), 0.35)),
        "sheet_open": lp(s(GS5, 0.22, 0.08, attack=0.03, f1=E6, glide_time=0.09)),
        "toggle_on": lp(mix((0, s(CS6, 0.15, 0.035), 1), (0.03, s(E6, 0.2, 0.045), 1))),
        "toggle_off": lp(mix((0, s(E6, 0.15, 0.035), 1), (0.03, s(CS6, 0.2, 0.045), 1))),
        "slider": lp(s(E6, 0.045, 0.012, attack=0.002)),
        "launch": mix((0, lp(s(E5, 0.2, 0.09)), 0.8), (0.04, lp(s(GS5, 0.2, 0.09)), 0.8),
                      (0.08, lp(s(B5, 0.2, 0.09)), 0.8),
                      (0.12, lp(mix((0, s(E6, 0.7, 0.25), 1), (0, s(B6, 0.7, 0.2), 0.4))), 1),
                      (0, whoosh(0.45, 1200, 5000, 0.6, 0.18, 0.12, seed=3), 0.3)),
        "wake": lp(mix((0, s(E5, 1.3, 0.45, attack=0.02), 0.7),
                       (0, s(B5, 1.3, 0.45, attack=0.02), 0.6),
                       (0.16, s(E6, 1.3, 0.5, attack=0.02), 1))),
    }


def crisp():
    return {
        "move": mix((0, click(4200), 1), (0, pluck(0.03, E6, 0.008), 0.6)),
        "edge": mix((0, click(1800, seed=8), 1),
                    (0, pluck(0.06, E5, 0.015, E5 * 0.92, 0.03, cutoff=(3000, 800)), 0.7),
                    (0.028, click(1500, seed=9), 0.5)),
        "select": mix((0, click(4500), 0.8), (0, pluck(0.2, B5, 0.07, E6, 0.03), 1)),
        "back": mix((0, click(3500), 0.8), (0, pluck(0.2, E6, 0.06, B5, 0.03), 1)),
        "layer_open": mix((0, whoosh(0.2, 600, 5000, 4.0, 0.1, 0.04), 1),
                          (0.12, click(5000), 0.5)),
        "layer_close": mix((0, whoosh(0.2, 5000, 600, 4.0, 0.1, 0.04, seed=2), 1),
                           (0.12, click(2000), 0.5)),
        "sheet_open": mix((0, pluck(0.15, E5, 0.06, E6, 0.02), 1), (0.02, click(5000), 0.5)),
        "toggle_on": mix((0, click(4000), 0.7), (0, pluck(0.1, CS6, 0.025), 1),
                         (0.025, pluck(0.15, E6, 0.035), 1)),
        "toggle_off": mix((0, click(3000), 0.7), (0, pluck(0.1, E6, 0.025), 1),
                          (0.025, pluck(0.15, CS6, 0.035), 1)),
        "slider": mix((0, click(3500, dur=0.004), 1), (0, pluck(0.025, E6, 0.005), 0.5)),
        # A power-up: a saw-ish fifth whose filter opens, then the ending note and a click.
        "launch": mix((0, pluck(0.35, E5, 0.2, B5, 0.12, odd=False,
                                cutoff=lambda u: 500 * 14 ** min(1, u * 2.5)), 0.6),
                      (0, pluck(0.35, B5, 0.2, odd=False,
                                cutoff=lambda u: 500 * 14 ** min(1, u * 2.5)), 0.4),
                      (0, whoosh(0.18, 800, 6000, 3.0, 0.12, 0.03, seed=3), 0.5),
                      (0.14, pluck(0.4, E6, 0.15), 0.8), (0.14, click(5000), 0.6)),
        "wake": mix((0, pluck(0.8, E5, 0.3), 0.6), (0.06, pluck(0.8, B5, 0.3), 0.6),
                    (0.12, pluck(0.8, E6, 0.3), 0.5), (0.18, pluck(0.9, B6, 0.35), 0.4),
                    (0, whoosh(0.4, 600, 3000, 0.8, 0.2, 0.12, seed=4), 0.2)),
    }


FAMILIES = {"glass": ("Glass", "FM tines: bright attack melting into a sine", glass),
            "soft": ("Soft", "Rounded, chorused sines; the quietest character", soft),
            "crisp": ("Crisp", "Clicks and filtered square/saw plucks; the most digital", crisp)}

# Loudness: dB over the loudest 30 ms. After the owner's listen on the Portal (2026-10-25, "still
# way too soft") the UI sounds are 3.5 dB (50%) up and the two long ones, launch and wake, 1.2 dB
# (15%), so the signature sounds don't take over. Then "moving through the UI is tiny soft, so
# another 30%": move and edge 2.3 dB more, launch left as it is ("about perfect"); "doesn't feel
# louder, try doubling it": 6 dB more again.
EVENTS = [  # id, label, when, loudness
    ("move", "Move", "focus steps along the row, dock or a list", -18.2),
    ("edge", "Edge", "pushing past the end of a row", -18.2),
    ("select", "Select", "A", -18.5),
    ("back", "Back", "B, close, cancel", -19.5),
    ("layer_open", "Layer open", "B on Home: the categories layer zooms out", -22.5),
    ("layer_close", "Layer close", "back to Home from the layer", -23.5),
    ("sheet_open", "Sheet open", "+/− options sheet, the quick panel", -20.5),
    ("toggle_on", "Toggle on", "a switch in the quick panel or settings", -21.5),
    ("toggle_off", "Toggle off", "the same switch, off", -22.5),
    ("slider", "Slider tick", "volume, brightness: the app raises the pitch with the value", -28.5),
    ("launch", "Launch", "starting an app", -16.8),
    ("wake", "Wake", "leaving standby", -19.8),
]

# --- mastering -------------------------------------------------------------------------


def db(x):
    return 20 * math.log10(max(x, 1e-12))


def loudest_rms(x, window=0.03):
    w = min(len(x), int(window * SR))
    acc = sum(v * v for v in x[:w])
    best = acc
    for i in range(w, len(x)):
        acc += x[i] * x[i] - x[i - w] * x[i - w]
        best = max(best, acc)
    return math.sqrt(max(best, 0.0) / w)


def master(x, target_db, peak_cap_db=-1.0):
    x = svf(svf(x, 300, 0.707, "high"), 300, 0.707, "high")  # 4th order: nothing low
    gain = 10 ** ((target_db - db(loudest_rms(x))) / 20)
    peak = max(abs(v) for v in x) * gain
    gain *= min(1.0, 10 ** (peak_cap_db / 20) / peak)
    x = [v * gain for v in x]
    floor = 10 ** (-66 / 20)
    end = max(i for i, v in enumerate(x) if abs(v) > floor) + 1
    x = x[:end]
    fade = min(len(x), int(0.003 * SR))
    for i in range(fade):
        x[len(x) - fade + i] *= 1 - (i + 1) / fade
    return x


def wav_bytes(x):
    import io
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", max(-32767, min(32767, round(v * 32767))))
                               for v in x))
    return buf.getvalue()


def main():
    data = {}
    for fid, (_, _, build) in FAMILIES.items():
        raw = build()
        (OUT / fid).mkdir(parents=True, exist_ok=True)
        data[fid] = {}
        for eid, _, _, target in EVENTS:
            x = master(raw[eid], target)
            blob = wav_bytes(x)
            (OUT / fid / f"{eid}.wav").write_bytes(blob)
            (ASSETS / fid).mkdir(parents=True, exist_ok=True)
            (ASSETS / fid / f"{eid}.wav").write_bytes(blob)
            data[fid][eid] = base64.b64encode(blob).decode("ascii")
            print(f"{fid:6} {eid:12} {len(x) / SR * 1000:6.0f} ms  peak {db(max(abs(v) for v in x)):6.1f} dB"
                  f"  rms30 {db(loudest_rms(x)):6.1f} dB  {len(blob) // 1024:4} kB")
    template = (Path(__file__).parent / "audition.template.html").read_text(encoding="utf-8")
    meta = {"families": [{"id": k, "name": v[0], "about": v[1]} for k, v in FAMILIES.items()],
            "events": [{"id": e, "name": n, "when": w} for e, n, w, _ in EVENTS]}
    page = template.replace("/*META*/null", json.dumps(meta)).replace("/*DATA*/null",
                                                                      json.dumps(data))
    (OUT / "audition.html").write_text(page, encoding="utf-8")
    print("wrote", OUT / "audition.html")


if __name__ == "__main__":
    main()
