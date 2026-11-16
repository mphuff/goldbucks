#!/usr/bin/env python3
"""Turns the screenshots from ScreenshotsClientTest (PNG) into the website's smaller JPEGs.

    GOLDBUCKS_SHOTS=$PWD/site/images scripts/client-test.sh
    scripts/site-images.py
"""
from pathlib import Path

from PIL import Image

folder = Path(__file__).resolve().parent.parent / "site/images"
for png in sorted(folder.glob("*.png")):
    jpg = png.with_suffix(".jpg")
    Image.open(png).convert("RGB").save(jpg, quality=85, optimize=True, progressive=True)
    print(f"{jpg.name}: {jpg.stat().st_size // 1024} KB")
