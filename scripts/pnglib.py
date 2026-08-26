"""Minimal PNG read/write, shared by the asset generators.

Hand-rolled so the build never needs an image dependency for a handful of generated textures.
Only what those generators use is supported: 8-bit RGBA, non-interlaced.
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

Pixels = bytearray

# PNG magic, and the "no filter" byte every scanline here is prefixed with.
SIGNATURE = bytes((0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
FILTER_NONE = bytes(1)


def paeth(left: int, up: int, up_left: int) -> int:
    estimate = left + up - up_left
    da, db, dc = abs(estimate - left), abs(estimate - up), abs(estimate - up_left)

    if da <= db and da <= dc:
        return left
    return up if db <= dc else up_left


def decode(path: Path) -> tuple[int, int, Pixels]:
    """Reads an 8-bit RGBA PNG, undoing the per-scanline filters."""
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


def encode(width: int, height: int, pixels: Pixels) -> bytes:
    """Writes filter-0 scanlines and lets zlib do the compressing."""
    stride = width * 4
    rows = bytearray()
    for y in range(height):
        rows += FILTER_NONE + pixels[y * stride:(y + 1) * stride]

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (SIGNATURE
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(rows), 9))
            + chunk(b"IEND", b""))


def encode_rgb(width: int, height: int, rows: list[list[tuple[int, int, int]]]) -> bytes:
    """Opaque RGB PNG from rows of colours.

    Block textures are not alpha blended, and an alpha channel on them is one more thing that
    can render wrong, so the generators that draw blocks use this rather than [encode].
    """
    raw = bytearray()
    for row in rows:
        raw += FILTER_NONE
        for red, green, blue in row:
            raw += bytes((red, green, blue))

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (SIGNATURE
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def chunk(tag: bytes, data: bytes) -> bytes:
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))


def blank(width: int, height: int) -> Pixels:
    """Fully transparent canvas."""
    return bytearray(width * height * 4)


def put(pixels: Pixels, width: int, x: int, y: int, rgb: tuple[int, int, int], alpha: int = 255) -> None:
    i = (y * width + x) * 4
    pixels[i] = rgb[0]
    pixels[i + 1] = rgb[1]
    pixels[i + 2] = rgb[2]
    pixels[i + 3] = alpha


def rect(pixels: Pixels, width: int, x0: int, y0: int, x1: int, y1: int,
         rgb: tuple[int, int, int], alpha: int = 255) -> None:
    """Filled rectangle, inclusive of both corners and clipped to the canvas."""
    height = len(pixels) // (width * 4)

    for y in range(max(0, y0), min(height - 1, y1) + 1):
        for x in range(max(0, x0), min(width - 1, x1) + 1):
            put(pixels, width, x, y, rgb, alpha)


def write_if_changed(path: Path, payload: bytes, check: bool, stale: list[Path]) -> None:
    if check:
        if not path.exists() or path.read_bytes() != payload:
            stale.append(path)
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)
