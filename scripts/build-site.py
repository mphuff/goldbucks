#!/usr/bin/env python3
"""Builds the website into a folder, ready for GitHub Pages.

    scripts/build-site.py OUT_DIR SHA256 DATE

Fills in the version and Minecraft version (from gradle.properties), the checksum and build date
(from the arguments) and the newest changelog entries (from CHANGELOG.md).
"""
import html
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHANGELOG_ENTRIES = 5


def properties():
    values = {}
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def inline(text):
    """Escapes a changelog line and turns `code` into <code>."""
    parts = text.split("`")
    return "".join(f"<code>{html.escape(p)}</code>" if i % 2 else html.escape(p) for i, p in enumerate(parts))


def changes():
    out = []
    entries = 0
    in_list = False
    for line in (ROOT / "CHANGELOG.md").read_text().splitlines():
        heading = re.match(r"^## (.+)", line)
        if heading:
            if entries == CHANGELOG_ENTRIES:
                break
            if in_list:
                out.append("    </ul>")
                in_list = False
            out.append(f"    <h3>{inline(heading.group(1))}</h3>")
            entries += 1
        elif entries and line.startswith("- "):
            if not in_list:
                out.append("    <ul>")
                in_list = True
            out.append(f"      <li>{inline(line[2:])}</li>")
    if in_list:
        out.append("    </ul>")
    if not entries:
        sys.exit("CHANGELOG.md has no entries")
    return "\n".join(out)


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    out, sha256, date = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    if not re.fullmatch(r"[0-9a-f]{64}", sha256):
        sys.exit(f"Not a SHA-256 checksum: {sha256}")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
        sys.exit(f"Not a date (YYYY-MM-DD): {date}")

    props = properties()
    version = props["version"]
    if not changes().startswith(f"    <h3>{html.escape(version)}</h3>"):
        sys.exit(f"CHANGELOG.md's newest entry is not version {version}")

    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(ROOT / "site", out, ignore=shutil.ignore_patterns("*.png"))
    shutil.copy(ROOT / "src/main/resources/assets/goldbucks/icon.png", out / "images/icon.png")

    page = (out / "index.html").read_text()
    for key, value in {
        "VERSION": html.escape(version),
        "MC_VERSION": html.escape(props["minecraft_version"]),
        "SHA256": sha256,
        "DATE": date,
        "YEAR": date[:4],
        "CHANGES": changes(),
    }.items():
        page = page.replace("{{" + key + "}}", value)
    left = re.findall(r"\{\{\w+\}\}", page)
    if left:
        sys.exit(f"Unfilled placeholders: {left}")
    for image in re.findall(r'src="(images/[^"]+)"', page):
        if not (out / image).exists():
            sys.exit(f"Missing image: {image}")
    (out / "index.html").write_text(page)
    print(f"Built the site for Goldbucks {version} in {out}")


if __name__ == "__main__":
    main()
