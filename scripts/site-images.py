#!/usr/bin/env python3
"""Turns the screenshots from ScreenshotsClientTest (PNG) into the website's JPEGs:
name.jpg at full size and name-960.jpg for smaller screens.

    XVFB_SCREEN=1920x1080x24 GOLDBUCKS_SHOTS=$PWD/site/images scripts/client-test.sh
    scripts/site-images.py
"""
from pathlib import Path

from PIL import Image

folder = Path(__file__).resolve().parent.parent / "site/images"
for png in sorted(folder.glob("*.png")):
    image = Image.open(png).convert("RGB")
    full = png.with_suffix(".jpg")
    image.save(full, quality=84, optimize=True, progressive=True)
    small = png.with_name(png.stem + "-960.jpg")
    image.resize((960, round(image.height * 960 / image.width)), Image.LANCZOS).save(small, quality=82, optimize=True, progressive=True)
    print(f"{full.name}: {full.stat().st_size // 1024} KB, {small.name}: {small.stat().st_size // 1024} KB")
