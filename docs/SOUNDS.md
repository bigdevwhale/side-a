# Recorded sounds

Side A can lay a real recording of vinyl crackle or tape hiss under the music (Settings → Listening room → Real recording). Both recordings are public-domain dedications (CC0 1.0), so they ship with the app under its MIT licence with no conditions. They are credited here anyway.

| File | What | Author | Source | Licence |
|---|---|---|---|---|
| `app/src/main/res/raw/vinyl_crackle.wav` | “vinyl record crackle nice stereo” | kyles | [freesound.org/s/637931](https://freesound.org/people/kyles/sounds/637931/) | CC0 1.0 |
| `app/src/main/res/raw/tape_hiss.wav` | “Nice Tape Hiss” | marineiguana | [freesound.org/s/213918](https://freesound.org/people/marineiguana/sounds/213918/) | CC0 1.0 |

Both were taken from the [Nullframe/daw-content](https://github.com/Nullframe/daw-content/releases/tag/content-blessed-v1) “Blessed library v1” release on GitHub (`atmos-v1.tar`, `texture/`), which records the same provenance. The originals are in `tools/sfx/`. `python tools/sfx.py` resamples them to 32 kHz, matches their loudness and crossfades the seam so they loop cleanly.

Everything else (needle clicks on scratches, tape wow and flutter, creases, the synthesised crackle) is generated in code in `play/Surface.kt`.
