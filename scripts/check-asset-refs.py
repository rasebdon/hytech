#!/usr/bin/env python3
"""
Verifies every asset path our JSON references actually resolves.

A missing `Icon` or texture is a *fatal* validation error for that item at load time, and the
server reports it as a wall of SEVERE lines rather than failing the build -- so it is easy to
ship and only find out when launching. This catches the same thing in a second.

References are resolved against our own `Common/` tree first and then the game's `Assets.zip`,
because plenty of our assets legitimately point at vanilla textures and models.

Usage:
    python scripts/check-asset-refs.py
"""

from __future__ import annotations

import json
import os
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"
COMMON = RESOURCES / "Common"

# Keys whose values name a file under Common/. Kept explicit rather than "any string that looks
# like a path", so a stray description never trips this.
ASSET_KEYS = {
    "Icon", "CustomModel", "Texture", "Model", "TransitionTexture",
    "All", "Sides", "UpDown", "Up", "Down", "North", "South", "East", "West",
}


def game_assets() -> set[str]:
    """Names inside the game's Assets.zip, or an empty set if it is not installed."""
    appdata = os.environ.get("APPDATA")
    if not appdata:
        home = os.environ.get("HOME", "")
        appdata = f"{home}/.var/app/com.hypixel.HytaleLauncher/data"

    archive = Path(appdata) / "Hytale/install/release/package/game/latest/Assets.zip"
    if not archive.exists():
        print(f"note: {archive} not found; vanilla references cannot be verified",
              file=sys.stderr)
        return set()

    with zipfile.ZipFile(archive) as zf:
        return set(zf.namelist())


def looks_like_a_path(value: object) -> bool:
    if not isinstance(value, str) or "/" not in value:
        return False

    return "." in value.rsplit("/", 1)[-1]


def collect(node: object, out: list[str]) -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ASSET_KEYS and looks_like_a_path(value):
                out.append(value)
            collect(value, out)
    elif isinstance(node, list):
        for value in node:
            collect(value, out)


def main() -> int:
    vanilla = game_assets()

    missing: list[tuple[Path, str]] = []
    checked = 0

    for path in sorted(RESOURCES.rglob("*.json")):
        if path.name == "manifest.json":
            continue

        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            missing.append((path, f"invalid JSON: {error}"))
            continue

        references: list[str] = []
        collect(payload, references)

        for reference in references:
            checked += 1

            if (COMMON / reference).exists():
                continue
            if f"Common/{reference}" in vanilla:
                continue

            missing.append((path, reference))

    print(f"Checked {checked} asset references across the resource tree.")

    if missing:
        print(f"\n{len(missing)} unresolved:", file=sys.stderr)
        for path, reference in missing:
            print(f"  {path.relative_to(RESOURCES)}  ->  {reference}", file=sys.stderr)
        print("\nEach of these is a fatal asset-validation error at server start.",
              file=sys.stderr)
        return 1

    print("All referenced assets resolve, in our tree or in the game's Assets.zip.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
