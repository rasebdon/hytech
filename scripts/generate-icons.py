#!/usr/bin/env python3
"""
Generates inventory icons for the Hytech items that need one.

A missing `Icon` is a fatal validation error, and the game's own icon renderer only fills in
`Icons/ItemsGenerated/` (via `syncAssets`) after that check -- so these are drawn from
primitives up front, recognisable enough to tell items apart until the real icons exist.

Usage:
    python scripts/generate-icons.py           # write assets
    python scripts/generate-icons.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import hytech_materials as table  # noqa: E402  (deliberate: needs the sys.path line above)
import pnglib  # noqa: E402

from paths import CONTENT, CORE, REPO_ROOT, resources

RESOURCES = resources(CONTENT)
ICON_DIR = RESOURCES / "Common/Icons/ItemsGenerated"

# HytechCore's own debug pipes need placeholder icons too, for the same fatal-validation reason.
CORE_ICON_DIR = resources(CORE) / "Common/Icons/ItemsGenerated"

SIZE = 64

# Matches the hues in generate-pipe-tints.py so an icon and its block read as the same thing.
PALETTE = {
    "Fluid": ((0x35, 0x6E, 0x8F), (0x5C, 0xA8, 0xD1)),
    "Gas": ((0x4A, 0x77, 0x31), (0x7C, 0xB8, 0x55)),
    "Heat": ((0x8F, 0x5A, 0x35), (0xD1, 0x8A, 0x5C)),
}

OUTLINE = (0x1A, 0x1A, 0x1E)

# Hues match SideConfigPanel's tab accents, so the icon, tab and wrench agree on a resource's
# colour -- Energy and Items have no pipe tint of their own to borrow.
DEBUG_PIPE_PALETTE = {
    "Energy": ((0x8F, 0x68, 0x24), (0xE8, 0xA9, 0x3B)),
    "Items": ((0x4A, 0x60, 0x7A), (0x7A, 0x9C, 0xC6)),
    "Fluid": PALETTE["Fluid"],
    "Gas": PALETTE["Gas"],
    "Heat": PALETTE["Heat"],
}

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


def pipe_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 4, 26, 59, 37, OUTLINE)
    pnglib.rect(px, SIZE, 4, 28, 59, 35, body)
    pnglib.rect(px, SIZE, 4, 29, 59, 30, highlight)

    pnglib.rect(px, SIZE, 22, 20, 41, 43, OUTLINE)
    pnglib.rect(px, SIZE, 24, 22, 39, 41, body)
    pnglib.rect(px, SIZE, 24, 23, 39, 25, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def tank_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A fill window makes it read as storage rather than a plain cube."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 12, 8, 51, 55, OUTLINE)
    pnglib.rect(px, SIZE, 14, 10, 49, 53, BURNER_CASING)

    pnglib.rect(px, SIZE, 20, 18, 43, 47, OUTLINE)
    pnglib.rect(px, SIZE, 22, 30, 41, 45, body)
    pnglib.rect(px, SIZE, 22, 30, 41, 31, highlight)

    pnglib.rect(px, SIZE, 16, 10, 47, 13, body)
    pnglib.rect(px, SIZE, 16, 50, 47, 53, body)

    return pnglib.encode(SIZE, SIZE, px)


def burner_icon() -> bytes:
    """Mirrors the block's own front texture."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 8, 8, 55, 55, OUTLINE)
    pnglib.rect(px, SIZE, 10, 10, 53, 53, BURNER_CASING)

    pnglib.rect(px, SIZE, 18, 24, 45, 49, OUTLINE)
    pnglib.rect(px, SIZE, 20, 26, 43, 47, BURNER_EMBER)
    for y in range(26, 48, 6):
        pnglib.rect(px, SIZE, 20, y, 43, y + 1, BURNER_GRATE)

    return pnglib.encode(SIZE, SIZE, px)


def machine_icon(working: tuple[int, int, int], coil: bool) -> bytes:
    """Same silhouette as the burner's icon so the machines read as one family, distinguished
    only by the working face."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 8, 8, 55, 55, OUTLINE)
    pnglib.rect(px, SIZE, 10, 10, 53, 53, MACHINE_CASING)

    pnglib.rect(px, SIZE, 18, 20, 45, 47, OUTLINE)
    pnglib.rect(px, SIZE, 20, 22, 43, 45, MACHINE_RECESS)

    if coil:
        for offset, y in enumerate(range(26, 44, 6)):
            pnglib.rect(px, SIZE, 22, y, 41, y + 2, working if offset == 2 else MACHINE_STEEL)
    else:
        pnglib.rect(px, SIZE, 22, 24, 41, 31, MACHINE_STEEL)
        pnglib.rect(px, SIZE, 22, 36, 41, 43, MACHINE_STEEL)
        pnglib.rect(px, SIZE, 22, 32, 41, 35, working)

    return pnglib.encode(SIZE, SIZE, px)


def dust_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A heaped pile, so a dust never reads as an ingot or a nugget."""
    px = pnglib.blank(SIZE, SIZE)

    for step, y in enumerate(range(44, 20, -4)):
        half = 20 - step * 3
        pnglib.rect(px, SIZE, 32 - half - 1, y - 4, 32 + half + 1, y, OUTLINE)
        pnglib.rect(px, SIZE, 32 - half, y - 3, 32 + half, y - 1, body)

    pnglib.rect(px, SIZE, 28, 24, 33, 27, highlight)
    pnglib.rect(px, SIZE, 38, 32, 43, 35, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def plate_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A flat slab seen at a slight angle, so a plate never reads as a bar."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 10, 20, 53, 43, OUTLINE)
    pnglib.rect(px, SIZE, 12, 22, 51, 41, body)

    pnglib.rect(px, SIZE, 12, 22, 51, 25, highlight)
    pnglib.rect(px, SIZE, 12, 38, 51, 41, OUTLINE)

    return pnglib.encode(SIZE, SIZE, px)


def bar_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A cast ingot: narrower on top than at the base."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 14, 24, 49, 43, OUTLINE)
    pnglib.rect(px, SIZE, 16, 26, 47, 41, body)
    pnglib.rect(px, SIZE, 20, 22, 43, 27, OUTLINE)
    pnglib.rect(px, SIZE, 22, 24, 41, 27, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def wire_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """Three strands, stepped so they read as wire rather than as bars."""
    px = pnglib.blank(SIZE, SIZE)

    for index, top in enumerate((20, 30, 40)):
        left = 12 + index * 4
        pnglib.rect(px, SIZE, left, top, left + 36, top + 5, OUTLINE)
        pnglib.rect(px, SIZE, left, top + 1, left + 36, top + 3, body)
        pnglib.rect(px, SIZE, left, top + 1, left + 36, top + 1, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def coil_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """Windings around a core: concentric rings, brightest on the inside."""
    px = pnglib.blank(SIZE, SIZE)

    for step, inset in enumerate(range(10, 26, 5)):
        colour = highlight if step % 2 else body
        pnglib.rect(px, SIZE, inset, inset, 63 - inset, 63 - inset, OUTLINE)
        pnglib.rect(px, SIZE, inset + 2, inset + 2, 61 - inset, 61 - inset, colour)

    pnglib.rect(px, SIZE, 28, 28, 35, 35, MACHINE_RECESS)

    return pnglib.encode(SIZE, SIZE, px)


def circuit_icon(body: tuple[int, int, int], highlight: tuple[int, int, int]) -> bytes:
    """A board with traces and a chip, the board tinted by tier."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 10, 12, 53, 51, OUTLINE)
    pnglib.rect(px, SIZE, 12, 14, 51, 49, body)

    for x in (20, 42):
        pnglib.rect(px, SIZE, x, 18, x + 2, 45, highlight)
        pnglib.rect(px, SIZE, x - 2, 16, x + 4, 19, highlight)
        pnglib.rect(px, SIZE, x - 2, 44, x + 4, 47, highlight)

    pnglib.rect(px, SIZE, 26, 26, 37, 37, OUTLINE)
    pnglib.rect(px, SIZE, 28, 28, 35, 35, MACHINE_STEEL)

    return pnglib.encode(SIZE, SIZE, px)


def frame_icon(body: tuple[int, int, int], highlight: tuple[int, int, int], solid: bool) -> bytes:
    """A hollow box for a frame; the casing fills it in, since a casing is the closed version."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 8, 8, 55, 55, OUTLINE)
    pnglib.rect(px, SIZE, 10, 10, 53, 53, body)

    if solid:
        pnglib.rect(px, SIZE, 18, 18, 45, 45, MACHINE_CASING)
        pnglib.rect(px, SIZE, 18, 18, 45, 21, highlight)
    else:
        pnglib.rect(px, SIZE, 18, 18, 45, 45, MACHINE_RECESS)
        pnglib.rect(px, SIZE, 10, 10, 53, 13, highlight)

    for x in (12, 46):
        for y in (12, 46):
            pnglib.rect(px, SIZE, x, y, x + 5, y + 5, highlight)

    return pnglib.encode(SIZE, SIZE, px)


def source_icon(body: tuple[int, int, int], highlight: tuple[int, int, int], voiding: bool) -> bytes:
    """Creative source and void share a shape, distinguished by the arrow direction."""
    px = pnglib.blank(SIZE, SIZE)

    pnglib.rect(px, SIZE, 10, 10, 53, 53, OUTLINE)
    pnglib.rect(px, SIZE, 12, 12, 51, 51, body)

    # Arrow points out of the block for a source, into it for a void.
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

    for name, (body, highlight) in DEBUG_PIPE_PALETTE.items():
        pnglib.write_if_changed(CORE_ICON_DIR / f"Pipe_Debug_{name}.png",
                                pipe_icon(body, highlight), args.check, stale)

    pnglib.write_if_changed(ICON_DIR / "Burner_Generator.png", burner_icon(), args.check, stale)

    pnglib.write_if_changed(ICON_DIR / "Hytech_Workbench.png",
                            machine_icon(MACHINE_STEEL, False), args.check, stale)
    pnglib.write_if_changed(ICON_DIR / "Crusher_Basic.png",
                            machine_icon(MACHINE_SPARK, False), args.check, stale)
    pnglib.write_if_changed(ICON_DIR / "Electric_Smelter_Basic.png",
                            machine_icon(MACHINE_COIL, True), args.check, stale)

    icons = 0

    for metal in table.METALS:
        pnglib.write_if_changed(ICON_DIR / f"{metal.dust}.png",
                                dust_icon(metal.body, metal.highlight), args.check, stale)
        pnglib.write_if_changed(ICON_DIR / f"{metal.plate}.png",
                                plate_icon(metal.body, metal.highlight), args.check, stale)
        icons += 2

        if metal.owns_bar:
            pnglib.write_if_changed(ICON_DIR / f"{metal.bar}.png",
                                    bar_icon(metal.body, metal.highlight), args.check, stale)
            icons += 1

    for component in table.COMPONENTS:
        if component.tier is not None:
            body, highlight = table.TIER_COLOURS[component.tier]
        else:
            metal = table.BY_NAME[component.metal]
            body, highlight = metal.body, metal.highlight

        drawing = {
            "wire": lambda: wire_icon(body, highlight),
            "coil": lambda: coil_icon(body, highlight),
            "circuit": lambda: circuit_icon(body, highlight),
            "casing": lambda: frame_icon(body, highlight, True),
            "frame": lambda: frame_icon(body, highlight, False),
        }[component.kind]

        pnglib.write_if_changed(ICON_DIR / f"{component.id}.png", drawing(), args.check, stale)
        icons += 1

    if args.check:
        if stale:
            print("Stale generated icons:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-icons.py", file=sys.stderr)
            return 1
        print("Generated icons are up to date.")
        return 0

    print(f"Wrote {len(PALETTE) * 4 + 4 + icons} icons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
