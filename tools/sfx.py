"""Turns the two recorded textures into seamless loops for the app.

python tools/sfx.py

Sources (both CC0-1.0, from the Nullframe/daw-content "Blessed library v1" release on GitHub,
which took them from Freesound; see docs/SOUNDS.md):
  tools/sfx/vinyl-crackle.flac  "vinyl record crackle nice stereo" by kyles, freesound.org/s/637931
  tools/sfx/tape-hiss.flac      "Nice Tape Hiss" by marineiguana, freesound.org/s/213918

Each is resampled to 32 kHz stereo, levelled to the same loudness (RMS 0.1), and its end is
crossfaded into its start so it loops without a seam. Written as 16-bit WAV to res/raw.
"""
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
RATE = 32_000
FADE = 0.25  # seconds of crossfade at the seam


def loop(src: Path, out: Path):
    data, rate = sf.read(src, always_2d=True, dtype="float64")
    n = int(len(data) * RATE / rate)
    t = np.linspace(0, len(data) - 1, n)
    data = np.stack([np.interp(t, np.arange(len(data)), data[:, ch]) for ch in range(data.shape[1])], axis=1)
    data -= data.mean(axis=0)
    f = int(FADE * RATE)
    ramp = np.linspace(0, 1, f)[:, None]
    head = data[:f] * ramp + data[-f:] * (1 - ramp)
    data = np.concatenate([head, data[f:-f]])
    data *= 0.1 / np.sqrt((data ** 2).mean())
    data = np.clip(data, -0.99, 0.99)
    sf.write(out, data, RATE, subtype="PCM_16")
    print(out.name, f"{len(data) / RATE:.1f}s", f"{out.stat().st_size // 1024} KB")


def main():
    raw = ROOT / "app/src/main/res/raw"
    raw.mkdir(parents=True, exist_ok=True)
    loop(ROOT / "tools/sfx/vinyl-crackle.flac", raw / "vinyl_crackle.wav")
    loop(ROOT / "tools/sfx/tape-hiss.flac", raw / "tape_hiss.wav")


if __name__ == "__main__":
    main()
