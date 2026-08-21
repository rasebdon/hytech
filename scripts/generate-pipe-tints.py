#!/usr/bin/env python3
"""
Generates recoloured pipe textures for the resource types that share the Default geometry.

Energy, fluid, gas and heat pipes are the same hub-and-arm model; only the texture differs,
and the texture is named on each pipe's item JSON rather than baked into the generated block
model. So a new resource type needs no new geometry at all -- just a tint of the authored
energy texture, which is what this produces.

The authored energy texture is greyscale -- a two-tone metal casing -- so recolouring means
colourising it: each pixel keeps its brightness (which is what carries the shading) and gains
the type's hue at a fixed saturation. Replace these PNGs with hand-drawn art whenever you
like; nothing but this script depends on them being generated.

Usage:
    python scripts/generate-pipe-tints.py           # write assets
    python scripts/generate-pipe-tints.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import colorsys
import struct
import sys
import zlib
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"

# The authored texture every tint derives from.
SOURCE = RESOURCES / "Common/BlockTextures/Pipes/Energy/Pipe_Energy.png"

# Target hue per type, in degrees. Chosen to be distinguishable at a glance and from each
# other: water-blue fluid, sickly green gas, hot orange heat.
TINTS = {
    "Fluid": 205.0,
    "Gas": 95.0,
    "Heat": 25.0,
}

# How strongly to colourise. Enough to identify the pipe at a glance, low enough that it still
# reads as painted metal rather than a solid colour swatch.
SATURATION = 0.45

# Gas sits between fluid and heat on the wheel, so it gets a touch more to stay distinct.
SATURATION_OVERRIDE = {
    "Gas": 0.55,
}

# Both the block texture and the item texture are written, mirroring how the energy pipe
# ships the same image under two roots.
OUT_DIRS = [
    RESOURCES / "Common/BlockTextures/Pipes",
    RESOURCES / "Common/Items/Pipes",
]


def decode_png(path: Path) -> tuple[int, int, bytearray]:
    """Minimal RGBA PNG reader, including the per-scanline filters.

    Hand-rolled because the repo has no image dependency and the build must not gain one for
    three textures. Only what this file needs is supported: 8-bit RGBA, non-interlaced.
    """
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")

    pos = 8
    idat = bytearray()
    width = height = 0

    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]

        if tag == b"IHDR":
            width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", chunk[:13])
            if depth != 8 or colour != 6 or interlace != 0:
                raise ValueError(f"{path}: only 8-bit non-interlaced RGBA is supported")
        elif tag == b"IDAT":
            idat += chunk

        pos += 12 + length

    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    out = bytearray(width * height * 4)

    for y in range(height):
        filter_type = raw[y * (stride + 1)]
        line = raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)]
        row = out[y * stride:(y + 1) * stride]
        prev = out[(y - 1) * stride:y * stride] if y else bytes(stride)

        for x in range(stride):
            left = row[x - 4] if x >= 4 else 0
            up = prev[x]
            up_left = prev[x - 4] if x >= 4 else 0
            value = line[x]

            if filter_type == 0:
                row[x] = value
            elif filter_type == 1:
                row[x] = (value + left) & 0xFF
            elif filter_type == 2:
                row[x] = (value + up) & 0xFF
            elif filter_type == 3:
                row[x] = (value + ((left + up) >> 1)) & 0xFF
            elif filter_type == 4:
                row[x] = (value + paeth(left, up, up_left)) & 0xFF
            else:
                raise ValueError(f"{path}: unknown PNG filter {filter_type}")

        out[y * stride:(y + 1) * stride] = row

    return width, height, out


def paeth(left: int, up: int, up_left: int) -> int:
    estimate = left + up - up_left
    da, db, dc = abs(estimate - left), abs(estimate - up), abs(estimate - up_left)

    if da <= db and da <= dc:
        return left
    return up if db <= dc else up_left


def encode_png(width: int, height: int, pixels: bytearray) -> bytes:
    """Writes filter-0 scanlines; zlib does the compressing."""
    stride = width * 4
    rows = bytearray()
    for y in range(height):
        rows += b"\x00" + pixels[y * stride:(y + 1) * stride]

    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(rows), 9))
            + chunk(b"IEND", b""))


def retint(pixels: bytearray, hue_degrees: float, saturation: float) -> bytearray:
    """Colourises every visible pixel to the target hue, keeping its brightness.

    Brightness is what carries the authored shading, so preserving it keeps the bevels and
    highlights intact. Saturation is imposed rather than scaled because the source is
    greyscale -- scaling a saturation of zero would leave the texture untouched, which is
    exactly the trap this walked into first time round.
    """
    out = bytearray(pixels)
    hue = hue_degrees / 360.0

    for i in range(0, len(out), 4):
        if out[i + 3] == 0:
            continue

        red, green, blue = out[i] / 255.0, out[i + 1] / 255.0, out[i + 2] / 255.0
        _, _, value = colorsys.rgb_to_hsv(red, green, blue)

        red, green, blue = colorsys.hsv_to_rgb(hue, saturation, value)

        out[i] = round(red * 255)
        out[i + 1] = round(green * 255)
        out[i + 2] = round(blue * 255)

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

    if not SOURCE.exists():
        print(f"error: missing source texture {SOURCE}", file=sys.stderr)
        return 1

    width, height, pixels = decode_png(SOURCE)

    stale: list[Path] = []

    for name, hue in TINTS.items():
        tinted = retint(pixels, hue, SATURATION_OVERRIDE.get(name, SATURATION))
        payload = encode_png(width, height, tinted)

        for out_dir in OUT_DIRS:
            write(out_dir / name / f"Pipe_{name}.png", payload, args.check, stale)

    if args.check:
        if stale:
            print("Stale generated pipe tints:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-pipe-tints.py", file=sys.stderr)
            return 1
        print("Generated pipe tints are up to date.")
        return 0

    print(f"Wrote {len(TINTS)} tinted pipe textures ({width}x{height}) to "
          f"{len(OUT_DIRS)} roots each")
    return 0


if __name__ == "__main__":
    sys.exit(main())
