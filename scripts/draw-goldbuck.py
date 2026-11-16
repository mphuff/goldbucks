#!/usr/bin/env python3
"""Draws the Goldbuck coin as pixel art: the in-game icon (64x64), the mod icon (256x256) and a large copy (1024x1024).

Usage: python3 scripts/draw-goldbuck.py   (needs Pillow)
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
N = 32  # the coin is drawn on a 32x32 grid, then scaled up with hard pixel edges

RIM = (150, 98, 18, 255)
EDGE = (196, 136, 28, 255)
GOLD = (245, 190, 50, 255)
SHINE = (255, 232, 140, 255)
DARK = (122, 78, 10, 255)

# The "$" sign, 12 wide and 18 tall, centred on the coin.
DOLLAR = [
    "....XX......",
    "..XXXXXXX...",
    ".XXXXXXXXX..",
    "XXX.XX..XXX.",
    "XXX.XX......",
    "XXXXXX......",
    ".XXXXXXX....",
    "..XXXXXXXX..",
    "....XXXXXXX.",
    "....XX..XXXX",
    "....XX...XXX",
    "XXX.XX...XXX",
    "XXXXXX..XXX.",
    ".XXXXXXXXX..",
    "..XXXXXXX...",
    "....XX......",
    "....XX......",
    "............",
]


def coin():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    c = (N - 1) / 2

    for y in range(N):
        for x in range(N):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5

            if d <= 15.2:
                px[x, y] = RIM
            if d <= 14.0:
                px[x, y] = EDGE
            if d <= 12.6:
                px[x, y] = GOLD
            # A soft shine on the upper left.
            if d <= 12.6 and (x - 11) ** 2 + (y - 10) ** 2 <= 14:
                px[x, y] = SHINE

    top = 7
    left = 10

    for row, line in enumerate(DOLLAR):
        for col, ch in enumerate(line):
            if ch == "X":
                px[left + col, top + row] = DARK

    return img


def main():
    small = coin()
    targets = {
        ROOT / "src/main/resources/assets/goldbucks/textures/gui/goldbuck.png": 64,
        ROOT / "src/main/resources/assets/goldbucks/icon.png": 256,
        ROOT / "art/goldbuck.png": 1024,
    }

    for path, size in targets.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        small.resize((size, size), Image.NEAREST).save(path)
        print("wrote", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
