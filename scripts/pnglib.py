"""Minimal PNG reader/writer: enough to tint an authored texture and draw a placeholder icon.

Reading covers what the game ships -- 8-bit greyscale, RGB, palette and RGBA, non-interlaced --
and always hands back straight RGBA. Writing is always RGBA. Pillow would do all of this, but the
generators run from a bare `python` on any machine that has checked the repo out, so this stays
dependency-free (as `generate-pipe-tints.py` already did in-line).
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

SIGNATURE = b"\x89PNG\r\n\x1a\n"

Pixels = bytearray


def decode(data: bytes, origin: str = "<bytes>") -> tuple[int, int, Pixels]:
    """8-bit, non-interlaced PNG in any of the four common colour types -> (width, height, RGBA)."""
    if data[:8] != SIGNATURE:
        raise ValueError(f"{origin} is not a PNG")

    pos = 8
    idat = bytearray()
    palette: bytes = b""
    alpha: bytes = b""
    width = height = colour = 0

    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]

        if tag == b"IHDR":
            width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", chunk[:13])
            if depth != 8 or interlace != 0:
                raise ValueError(f"{origin}: only 8-bit non-interlaced PNGs are supported")
        elif tag == b"PLTE":
            palette = chunk
        elif tag == b"tRNS":
            alpha = chunk
        elif tag == b"IDAT":
            idat += chunk

        pos += 12 + length

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(colour)
    if channels is None:
        raise ValueError(f"{origin}: unsupported colour type {colour}")

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    flat = _unfilter(raw, stride, height, channels)
    return width, height, _to_rgba(flat, colour, palette, alpha)


def decode_file(path: Path) -> tuple[int, int, Pixels]:
    return decode(path.read_bytes(), str(path))


def encode(width: int, height: int, pixels: Pixels) -> bytes:
    """RGBA -> PNG bytes. Every row is written with filter 0; these images are tiny."""
    stride = width * 4
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        raw += pixels[y * stride:(y + 1) * stride]

    out = bytearray(SIGNATURE)
    out += _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    out += _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    out += _chunk(b"IEND", b"")
    return bytes(out)


def blank(width: int, height: int) -> Pixels:
    return bytearray(width * height * 4)


def get(pixels: Pixels, width: int, x: int, y: int) -> tuple[int, int, int, int]:
    i = (y * width + x) * 4
    return pixels[i], pixels[i + 1], pixels[i + 2], pixels[i + 3]


def put(pixels: Pixels, width: int, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
    i = (y * width + x) * 4
    pixels[i:i + 4] = bytes(rgba)


def fill(pixels: Pixels, width: int, box: tuple[int, int, int, int],
         rgba: tuple[int, int, int, int]) -> None:
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(pixels, width, x, y, rgba)


def _chunk(tag: bytes, payload: bytes) -> bytes:
    return (struct.pack(">I", len(payload)) + tag + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))


def _unfilter(raw: bytes, stride: int, height: int, bpp: int) -> bytearray:
    out = bytearray(stride * height)
    for y in range(height):
        start = y * (stride + 1)
        filter_type = raw[start]
        line = raw[start + 1:start + 1 + stride]
        row = out[y * stride:(y + 1) * stride]
        prev = out[(y - 1) * stride:y * stride] if y else bytes(stride)

        for i, byte in enumerate(line):
            left = row[i - bpp] if i >= bpp else 0
            up = prev[i]
            up_left = prev[i - bpp] if i >= bpp else 0

            if filter_type == 0:
                value = byte
            elif filter_type == 1:
                value = byte + left
            elif filter_type == 2:
                value = byte + up
            elif filter_type == 3:
                value = byte + (left + up) // 2
            elif filter_type == 4:
                pa, pb, pc = abs(up - up_left), abs(left - up_left), abs(left + up - 2 * up_left)
                predictor = left if pa <= pb and pa <= pc else (up if pb <= pc else up_left)
                value = byte + predictor
            else:
                raise ValueError(f"unknown PNG filter {filter_type}")

            row[i] = value & 0xFF

        out[y * stride:(y + 1) * stride] = row
    return out


def _to_rgba(flat: bytearray, colour: int, palette: bytes, alpha: bytes) -> Pixels:
    if colour == 6:
        return flat

    count = len(flat) // {0: 1, 2: 3, 3: 1, 4: 2}[colour]
    out = bytearray(count * 4)

    for i in range(count):
        if colour == 0:
            grey = flat[i]
            out[i * 4:i * 4 + 4] = bytes((grey, grey, grey, 255))
        elif colour == 4:
            grey, a = flat[i * 2], flat[i * 2 + 1]
            out[i * 4:i * 4 + 4] = bytes((grey, grey, grey, a))
        elif colour == 2:
            out[i * 4:i * 4 + 3] = flat[i * 3:i * 3 + 3]
            out[i * 4 + 3] = 255
        else:
            index = flat[i]
            out[i * 4:i * 4 + 3] = palette[index * 3:index * 3 + 3]
            out[i * 4 + 3] = alpha[index] if index < len(alpha) else 255

    return out
