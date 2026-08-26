#!/usr/bin/env python3
"""
Generates inventory icons for the Hytech items that need one.

`Icon` is validated when the asset loads, and the failure is fatal for that item -- so the
PNG has to exist *before* the game's own icon renderer ever runs. Icons under
`Icons/ItemsGenerated/` are normally produced by the game and synced back by the `syncAssets`
Gradle task, which is too late. These are drawn from primitives instead: recognisable enough
to tell a fluid pipe from a gas tank in a hotbar, and cheap to replace with the real rendered
icons once the game has produced them.

Usage:
    python scripts/generate-icons.py           # write assets
    python scripts/generate-icons.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import pnglib  # noqa: E402  (deliberate: needs the sys.path line above)

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"
ICON_DIR = RESOURCES / "Common/Icons/ItemsGenerated"

SIZE = 64

# Body and highlight per resource type, matching the hues in generate-pipe-tints.py so an icon
# and its block read as the same thing.
PALETTE = {
    "Fluid": ((0x35, 0x6E, 0x8F), (0x5C, 0xA8, 0xD1)),
    "Gas": ((0x4A, 0x77, 0x31), (0x7C, 0xB8, 0x55)),
    "Heat": ((0x8F, 0x5A, 0x35), (0xD1, 0x8A, 0x5C)),
}

OUTLINE = (0x1A, 0x1A, 0x1E)

# Burner casing and ember, matching generate-burner-assets.py.
BURNER_CASING = (0x4A, 0x4A, 0x52)
BURNER_EMBER = (0xFF, 0xA5, 0x2B)
BURNER_GRATE = (0x2E, 0x2E, 0x34)

# Electric machine casing and working faces, matching generate-machine-assets.py.
MACHINE_CASING = (0x55, 0x57, 0x60)
MACHINE_RECESS = (0x1E, 0x1F, 0x24)
MACHINE_STEEL = (0x8A, 0x8E, 0x99)
MACHINE_SPARK = (0x5C, 0xD6, 0xF2)
MACHINE_COIL = (0xFF, 0xA5, 0x2B)

# Dust piles. Metal-ish rather than literal: an icon has to read at 16 pixels on a hotbar.
DUSTS = {
    "Copper": ((0xB5, 0x6B, 0x38), (0xE2, 0x93, 0x5A)),
    "Iron": ((0x8E, 0x8E, 0x96), (0xBD, 0xBD, 0xC6)),
}


def pipe_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A horizontal pipe run with a hub, drawn on the diagonal-ish axis the block icons use."""
    px = pnglib.blank(SIZE, SIZE)

    # Arms out to both edges.
    pnglib.rect(px, SIZE, 4, 26, 59, 37, OUTLINE)
    pnglib.rect(px, SIZE, 4, 28, 59, 35, body)
    pnglib.rect(px, SIZE, 4, 29, 59, 30, highlight)

    # Centre hub, slightly taller so the shape is not a plain bar.
    pnglib.rect(px, SIZE, 22, 20, 41, 43, OUTLINE)
    pnglib.rect(px, SIZE, 24, 22, 39, 41, body)
    pnglib.rect(px, SIZE, 24, 23, 39, 25, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def tank_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A tank with a fill window, so it reads as storage rather than a plain cube."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 12, 8, 51, 55, OUTLINE)
    pnglib.rect(px, SIZE, 14, 10, 49, 53, BURNER_CASING)

    # Fill window, part full, to signal a container with contents.
    pnglib.rect(px, SIZE, 20, 18, 43, 47, OUTLINE)
    pnglib.rect(px, SIZE, 22, 30, 41, 45, body)
    pnglib.rect(px, SIZE, 22, 30, 41, 31, highlight)

    # Collars top and bottom.
    pnglib.rect(px, SIZE, 16, 10, 47, 13, body)
    pnglib.rect(px, SIZE, 16, 50, 47, 53, body)

    return pnglib.encode(SIZE, SIZE, px)


def burner_icon() -> bytes:
    """A casing with a lit firebox, mirroring the block's own front texture."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 8, 8, 55, 55, OUTLINE)
    pnglib.rect(px, SIZE, 10, 10, 53, 53, BURNER_CASING)

    # Firebox in the lower two thirds, barred like the block texture.
    pnglib.rect(px, SIZE, 18, 24, 45, 49, OUTLINE)
    pnglib.rect(px, SIZE, 20, 26, 43, 47, BURNER_EMBER)
    for y in range(26, 48, 6):
        pnglib.rect(px, SIZE, 20, y, 43, y + 1, BURNER_GRATE)

    return pnglib.encode(SIZE, SIZE, px)


def machine_icon(working: tuple[int, int, int], coil: bool) -> bytes:
    """A cased machine seen face-on, its working face either toothed or wound.

    Deliberately the same silhouette as the burner's icon so the machines read as one family,
    with the face doing the distinguishing.
    """
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 8, 8, 55, 55, OUTLINE)
    pnglib.rect(px, SIZE, 10, 10, 53, 53, MACHINE_CASING)

    pnglib.rect(px, SIZE, 18, 20, 45, 47, OUTLINE)
    pnglib.rect(px, SIZE, 20, 22, 43, 45, MACHINE_RECESS)

    if coil:
        # Three windings, the lowest one hottest, as on the smelter's front texture.
        for offset, y in enumerate(range(26, 44, 6)):
            pnglib.rect(px, SIZE, 22, y, 41, y + 2, working if offset == 2 else MACHINE_STEEL)
    else:
        # Interlocking jaws either side of a charged gap.
        pnglib.rect(px, SIZE, 22, 24, 41, 31, MACHINE_STEEL)
        pnglib.rect(px, SIZE, 22, 36, 41, 43, MACHINE_STEEL)
        pnglib.rect(px, SIZE, 22, 32, 41, 35, working)

    return pnglib.encode(SIZE, SIZE, px)


def dust_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A heaped pile, so a dust never reads as an ingot or a nugget."""
    px = pnglib.blank(SIZE, SIZE)

    # Stepped heap: widest at the base, and none of it touching the icon's edge.
    for step, y in enumerate(range(44, 20, -4)):
        half = 20 - step * 3
        pnglib.rect(px, SIZE, 32 - half - 1, y - 4, 32 + half + 1, y, OUTLINE)
        pnglib.rect(px, SIZE, 32 - half, y - 3, 32 + half, y - 1, body)

    # A couple of glints so the pile has a top rather than a flat cap.
    pnglib.rect(px, SIZE, 28, 24, 33, 27, highlight)
    pnglib.rect(px, SIZE, 38, 32, 43, 35, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def source_icon(body: tuple[int, int, int], highlight: tuple[int, int, int], voiding: bool) -> bytes:
    """Creative source and void share a shape, distinguished by the arrow direction."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 10, 10, 53, 53, OUTLINE)
    pnglib.rect(px, SIZE, 12, 12, 51, 51, body)

    # A chunky arrow: pointing out of the block for a source, into it for a void.
    rows = range(20, 44, 2)
    for offset, y in enumerate(rows):
        half = offset if voiding else len(list(rows)) - 1 - offset
        pnglib.rect(px, SIZE, 32 - half, y, 32 + half, y + 1, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    stale: list[Path] = []

    for name, (body, highlight) in PALETTE.items():
        pnglib.write_if_changed(ICON_DIR / f"Pipe_{name}.png",
                                pipe_icon(body, highlight), args.check, stale)
        pnglib.write_if_changed(ICON_DIR / f"{name}_Tank.png",
                                tank_icon(body, highlight), args.check, stale)
        pnglib.write_if_changed(ICON_DIR / f"{name}_Source.png",
                                source_icon(body, highlight, False), args.check, stale)
        pnglib.write_if_changed(ICON_DIR / f"{name}_Void.png",
                                source_icon(body, highlight, True), args.check, stale)

    pnglib.write_if_changed(ICON_DIR / "Burner_Generator.png", burner_icon(), args.check, stale)

    pnglib.write_if_changed(ICON_DIR / "Crusher_Basic.png",
                            machine_icon(MACHINE_SPARK, False), args.check, stale)
    pnglib.write_if_changed(ICON_DIR / "Electric_Smelter_Basic.png",
                            machine_icon(MACHINE_COIL, True), args.check, stale)

    for metal, (body, highlight) in DUSTS.items():
        pnglib.write_if_changed(ICON_DIR / f"Hytech_Dust_{metal}.png",
                                dust_icon(body, highlight), args.check, stale)

    if args.check:
        if stale:
            print("Stale generated icons:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-icons.py", file=sys.stderr)
            return 1
        print("Generated icons are up to date.")
        return 0

    print(f"Wrote {len(PALETTE) * 4 + 3 + len(DUSTS)} icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
