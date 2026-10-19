"""Draws the README logo, the launcher icon's record on walnut, as docs/logo.png.

python tools/icon.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
S = 1024
K = 4  # supersampling


def main():
    n = S * K
    im = Image.new("RGBA", (n, n), (0x5E, 0x3B, 0x23, 255))
    d = ImageDraw.Draw(im)
    u = n / 108

    def circle(cx, cy, r, **kw):
        d.ellipse([(cx - r) * u, (cy - r) * u, (cx + r) * u, (cy + r) * u], **kw)

    # Walnut grain.
    for y, w in [(20, 3), (47, 2), (78, 4), (96, 2), (8, 1), (62, 1)]:
        d.rectangle([0, y * u, n, (y + w) * u], fill=(0x4A, 0x2D, 0x1A))
    # The record, its grooves, the orange label.
    circle(55.5, 56.5, 31.5, fill=(0x3A, 0x24, 0x15))
    circle(54, 54, 31, fill=(0x14, 0x13, 0x12))
    for r in range(13, 30, 2):
        circle(54, 54, r, outline=(0x2E, 0x2C, 0x2A), width=int(0.5 * u))
    circle(54, 54, 11, fill=(0xFF, 0x9A, 0x3C))
    circle(54, 54, 10, outline=(0xE8, 0xD5, 0xB5), width=int(0.4 * u))
    circle(54, 54, 1.6, fill=(0x14, 0x13, 0x12))
    # Tonearm.
    arm = (0xD9, 0xD5, 0xCC)
    d.line([80 * u, 24 * u, 77 * u, 58 * u], fill=arm, width=int(2.4 * u))
    d.line([77 * u, 58 * u, 68 * u, 68 * u], fill=arm, width=int(2.4 * u))
    d.polygon([(64 * u, 66 * u), (69 * u, 71 * u), (71.5 * u, 68.5 * u), (66.5 * u, 63.5 * u)], fill=(0x26, 0x23, 0x1F))
    circle(80, 24, 5, fill=(0xBD, 0xB8, 0xAE))
    circle(80, 24, 2.4, fill=(0x6E, 0x6A, 0x63))

    # The light from the window, laid over the vinyl.
    glow = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.pieslice([23 * u, 23 * u, 85 * u, 85 * u], -50, -15, fill=(255, 255, 255, 22))
    g.ellipse([42 * u, 42 * u, 66 * u, 66 * u], fill=(0, 0, 0, 0))
    hole = Image.new("L", (n, n), 255)
    ImageDraw.Draw(hole).ellipse([43 * u, 43 * u, 65 * u, 65 * u], fill=0)
    glow.putalpha(Image.composite(glow.getchannel("A"), Image.new("L", (n, n), 0), hole))
    im = Image.alpha_composite(im, glow)

    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1], radius=int(n * 0.22), fill=255)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    out = out.resize((S, S), Image.LANCZOS)
    (ROOT / "docs").mkdir(exist_ok=True)
    out.save(ROOT / "docs/logo.png")


if __name__ == "__main__":
    main()
