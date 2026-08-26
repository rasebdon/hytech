#!/usr/bin/env python3
"""
Generates the electric machines' block textures.

Every machine is a cube block with a working face, and every one needs that face in two states:
dark while idle, lit while the `Processing` block state is set. The casing is shared, so the
machines read as one family and a new machine costs one front pair.

Drawn programmatically for the same reason the burner's textures are: no image dependency, the
palette lives in one place, and hand-authored art can replace the PNGs later without touching
any code or JSON.

Usage:
    python scripts/generate-machine-assets.py           # write assets
    python scripts/generate-machine-assets.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import pnglib  # noqa: E402  (deliberate: needs the sys.path line above)

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"
TEXTURE_DIR = RESOURCES / "Common/BlockTextures/Machines"

SIZE = 16

# A colder, lighter steel than the burner's iron: an electric machine should not look like a
# firebox with a different front.
CASING = (0x55, 0x57, 0x60)
CASING_DARK = (0x3B, 0x3D, 0x45)
RIVET = (0x77, 0x7A, 0x85)

RECESS = (0x1E, 0x1F, 0x24)
STEEL = (0x8A, 0x8E, 0x99)

# Crusher: idle jaws are bare steel, working jaws glow with the charge running through them.
SPARK_DIM = (0x2E, 0x7A, 0x8F)
SPARK_HOT = (0x5C, 0xD6, 0xF2)

# Smelter: an induction coil, orange when it is actually heating.
COIL_COLD = (0x6B, 0x4A, 0x2E)
COIL_WARM = (0xC2, 0x4A, 0x12)
COIL_HOT = (0xFF, 0xA5, 0x2B)

Rows = list[list[tuple[int, int, int]]]


def casing() -> Rows:
    """Plain riveted plate, used for every face that is not the front."""
    out: Rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            edge = x == 0 or y == 0 or x == SIZE - 1 or y == SIZE - 1
            rivet = x in (2, SIZE - 3) and y in (2, SIZE - 3)

            if rivet:
                row.append(RIVET)
            elif edge:
                row.append(CASING_DARK)
            else:
                row.append(CASING)
        out.append(row)
    return out


def window(out: Rows, top: int, bottom: int, left: int, right: int) -> None:
    """Sinks a dark recess into the casing, which every working face is drawn inside."""
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            out[y][x] = RECESS


def crusher_front(active: bool) -> Rows:
    """Two opposing rows of jaw teeth, charged when the machine is running."""
    out = casing()

    top, bottom = 4, SIZE - 4
    left, right = 3, SIZE - 4
    window(out, top, bottom, left, right)

    middle = (top + bottom) // 2

    for x in range(left + 1, right):
        # Teeth alternate so the two jaws interlock rather than meeting flat.
        upper = middle - (1 if x % 2 == 0 else 2)
        lower = middle + (2 if x % 2 == 0 else 1)

        for y in range(top + 1, upper + 1):
            out[y][x] = STEEL
        for y in range(lower, bottom):
            out[y][x] = STEEL

        if active:
            out[middle][x] = SPARK_HOT if x % 2 == 0 else SPARK_DIM

    return out


def workbench_front() -> Rows:
    """A tool board: the bench has no working state, so it needs only the one face."""
    out = casing()

    top, bottom = 4, SIZE - 4
    left, right = 3, SIZE - 4
    window(out, top, bottom, left, right)

    # A wrench and a plate hung on the board, in the machines' own steel.
    for y in range(top + 2, bottom - 1):
        out[y][left + 3] = STEEL
    out[top + 2][left + 2] = STEEL
    out[top + 2][left + 4] = STEEL

    for y in range(top + 3, top + 7):
        for x in range(right - 5, right - 1):
            out[y][x] = STEEL

    return out


def smelter_front(active: bool) -> Rows:
    """An induction coil of three bars, glowing hotter toward the base when running."""
    out = casing()

    top, bottom = 4, SIZE - 4
    left, right = 3, SIZE - 4
    window(out, top, bottom, left, right)

    bars = [top + 2, top + 4, top + 6]

    for index, y in enumerate(bars):
        if y >= bottom:
            continue

        if active:
            colour = COIL_HOT if index == len(bars) - 1 else COIL_WARM
        else:
            colour = COIL_COLD

        for x in range(left + 1, right):
            out[y][x] = colour

        # Coil returns, so the bars read as one winding rather than three stripes.
        edge = left + 1 if index % 2 == 0 else right - 1
        if y + 1 < bottom:
            out[y + 1][edge] = colour

    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    stale: list[Path] = []

    textures = {
        "Machine_Casing.png": casing(),
        "Crusher_Front.png": crusher_front(False),
        "Crusher_Front_Active.png": crusher_front(True),
        "Smelter_Front.png": smelter_front(False),
        "Smelter_Front_Active.png": smelter_front(True),
        "Workbench_Front.png": workbench_front(),
    }

    for name, rows in textures.items():
        pnglib.write_if_changed(TEXTURE_DIR / name,
                                pnglib.encode_rgb(SIZE, SIZE, rows), args.check, stale)

    if args.check:
        if stale:
            print("Stale generated machine assets:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-machine-assets.py", file=sys.stderr)
            return 1
        print("Generated machine assets are up to date.")
        return 0

    print(f"Wrote {len(textures)} machine block textures")
    return 0


if __name__ == "__main__":
    sys.exit(main())
