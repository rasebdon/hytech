#!/usr/bin/env python3
"""
Generates the Burner Generator's block textures.

The burner is a cube block with a distinct front face, so it needs three textures per burn
state: a plain metal casing for the sides and top, and a front carrying the firebox grate.
The lit variant is the same geometry with the grate glowing, which the "Burning" block state
swaps to while fuel is alight.

Written programmatically for the same reason the overlay textures are: no image library is
needed, the palette lives in one place, and hand-authored art can replace the PNGs later
without touching any code.

Usage:
    python scripts/generate-burner-assets.py           # write assets
    python scripts/generate-burner-assets.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"
TEXTURE_DIR = RESOURCES / "Common/BlockTextures/Generators/Burner"

SIZE = 16

# Riveted iron casing, a shade darker than the solar panel's frame so the two machines read
# as different at a glance.
CASING = (0x4A, 0x4A, 0x52)
CASING_DARK = (0x35, 0x35, 0x3C)
RIVET = (0x6B, 0x6B, 0x74)

GRATE_COLD = (0x1C, 0x1C, 0x20)
GRATE_BAR = (0x2E, 0x2E, 0x34)

# Fire seen through the grate. Warm enough to be obvious at distance in a dim base.
EMBER_DIM = (0xC2, 0x4A, 0x12)
EMBER_HOT = (0xFF, 0xA5, 0x2B)


def png(pixels: list[list[tuple[int, int, int]]]) -> bytes:
    """Opaque RGB PNG. Block textures are not alpha blended, so no alpha channel."""
    rows = []
    for row in pixels:
        raw = bytearray()
        for red, green, blue in row:
            raw += bytes((red, green, blue))
        rows.append(b"\x00" + bytes(raw))

    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(b"".join(rows)))
            + chunk(b"IEND", b""))


def casing() -> list[list[tuple[int, int, int]]]:
    """Plain riveted plate, used for every face that is not the front."""
    out = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            edge = x == 0 or y == 0 or x == SIZE - 1 or y == SIZE - 1
            # Rivets sit just inside each corner.
            rivet = x in (2, SIZE - 3) and y in (2, SIZE - 3)

            if rivet:
                row.append(RIVET)
            elif edge:
                row.append(CASING_DARK)
            else:
                row.append(CASING)
        out.append(row)
    return out


def front(lit: bool) -> list[list[tuple[int, int, int]]]:
    """Casing with a firebox opening in the lower two thirds.

    The grate bars stay visible when lit so the block still reads as a machine rather than a
    solid block of orange.
    """
    out = casing()

    top, bottom = 5, SIZE - 3
    left, right = 3, SIZE - 4

    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            on_bar = (y - top) % 3 == 0

            if on_bar:
                out[y][x] = GRATE_BAR
            elif lit:
                # Hotter toward the base of the firebox, where the fuel would sit.
                depth = (y - top) / max(1, bottom - top)
                out[y][x] = EMBER_HOT if depth > 0.45 else EMBER_DIM
            else:
                out[y][x] = GRATE_COLD

    return out


def write(path: Path, payload: bytes, check: bool, stale: list[Path]) -> None:
    if check:
        if not path.exists() or path.read_bytes() != payload:
            stale.append(path)
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    stale: list[Path] = []

    write(TEXTURE_DIR / "Burner_Casing.png", png(casing()), args.check, stale)
    write(TEXTURE_DIR / "Burner_Front.png", png(front(False)), args.check, stale)
    write(TEXTURE_DIR / "Burner_Front_Lit.png", png(front(True)), args.check, stale)

    if args.check:
        if stale:
            print("Stale generated burner assets:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-burner-assets.py", file=sys.stderr)
            return 1
        print("Generated burner assets are up to date.")
        return 0

    print("Wrote burner casing + cold/lit front textures")
    return 0


if __name__ == "__main__":
    sys.exit(main())
