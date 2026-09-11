#!/usr/bin/env python3
"""
Generates the face-configuration overlay assets.

Holding a wrench and looking at a logistic block highlights the targeted side with a flat
coloured quad, one colour per face configuration. The quad is a single generated model
reused for every colour; only the texture differs.

Usage:
    python scripts/generate-overlay-assets.py           # write assets
    python scripts/generate-overlay-assets.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import json
import struct
import sys
import zlib
from pathlib import Path

from paths import REPO_ROOT, resources, CORE

RESOURCES = resources(CORE)

MODEL_PATH = RESOURCES / "Common/VFX/Overlay/Face_Overlay.blockymodel"
TEXTURE_DIR = RESOURCES / "Common/VFX/Overlay"
MODEL_JSON_DIR = RESOURCES / "Server/Models/Overlay"

# ModelAsset requires Model/Texture under one of these Common roots.
COMMON_ASSET_ROOTS = ("Characters/", "NPC/", "Items/", "VFX/")

# Keep in step with BlockFaceConfigType on the Java side.
COLOURS = {
    "None": (0x80, 0x80, 0x80),
    "Both": (0xA0, 0x40, 0xC0),
    "Input": (0xD0, 0x30, 0x30),
    "Output": (0x30, 0x60, 0xD0),
}

# Must equal the quad's size exactly: smaller samples into neighbouring atlas entries, larger
# gets cropped to its top-left corner and silently loses its right/bottom edges.
TEXTURE_SIZE = 32

# This render path does cutout, not blending -- entity models ignore texture alpha -- so the
# texture uses only 0/255 alpha: a solid border plus a sparse interior wash.
BORDER_PX = 2
FILL_PERIOD = 2  # one opaque pixel per period x period cell; 2 = 25% coverage

QUAD_SIZE = 32  # a block spans 32 model units; the quad covers one whole side


def overlay_png(rgb: tuple[int, int, int], size: int) -> bytes:
    red, green, blue = rgb

    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            on_border = (x < BORDER_PX or y < BORDER_PX
                         or x >= size - BORDER_PX or y >= size - BORDER_PX)
            in_wash = x % FILL_PERIOD == 0 and y % FILL_PERIOD == 0
            opaque = on_border or in_wash
            row += bytes([red, green, blue, 255 if opaque else 0])
        rows.append(b"\x00" + bytes(row))
    raw = b"".join(rows)

    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))


def quad_model() -> dict:
    """A "quad" shape, not a "box": a box has six faces and renders as a solid slab, where an
    overlay wants one single-faced plane. It lies in the XY plane facing +Z, so the overlay
    rotates it onto each block side rather than translating a slab."""
    return {
        "nodes": [
            {
                "id": "1",
                "name": "Overlay",
                "position": {"x": 0, "y": 0, "z": 0},
                "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
                "shape": {
                    "type": "quad",
                    "offset": {"x": 0, "y": 0, "z": 0},
                    "stretch": {"x": 1, "y": 1, "z": 1},
                    "settings": {"size": {"x": QUAD_SIZE, "y": QUAD_SIZE}},
                    "textureLayout": {
                        "front": {
                            "offset": {"x": 0, "y": 0},
                            "mirror": {"x": False, "y": False},
                            "angle": 0,
                        }
                    },
                    "unwrapMode": "custom",
                    "visible": True,
                    "doubleSided": True,
                    "shadingMode": "fullbright",
                },
            }
        ],
        "lod": "auto",
    }


def write(path: Path, payload: bytes, check: bool, stale: list[Path]) -> None:
    if check:
        if not path.exists() or path.read_bytes() != payload:
            stale.append(path)
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def write_json(path: Path, payload: dict, check: bool, stale: list[Path]) -> None:
    """Text, not bytes: a byte compare falsely calls every file stale on a CRLF checkout, since
    git hands us CRLF on Windows while json.dumps emits LF."""
    text = json.dumps(payload, indent=2) + "\n"
    if check:
        if not path.exists() or path.read_text(encoding="utf-8") != text:
            stale.append(path)
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    stale: list[Path] = []

    write_json(MODEL_PATH, quad_model(), args.check, stale)

    for name, rgb in COLOURS.items():
        write(TEXTURE_DIR / f"Face_Overlay_{name}.png",
              overlay_png(rgb, TEXTURE_SIZE), args.check, stale)

        # Without an explicit HitBox the client has nothing to size or cull the model against.
        model_json = {
            "Model": "VFX/Overlay/Face_Overlay.blockymodel",
            "Texture": f"VFX/Overlay/Face_Overlay_{name}.png",
            "HitBox": {
                "Min": {"X": -0.5, "Y": -0.03, "Z": -0.5},
                "Max": {"X": 0.5, "Y": 0.03, "Z": 0.5},
            },
        }
        write_json(MODEL_JSON_DIR / f"Face_Overlay_{name}.json", model_json,
                   args.check, stale)

    if args.check:
        if stale:
            print("Stale generated overlay assets:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            return 1
        print("Generated overlay assets are up to date.")
        return 0

    print(f"Wrote overlay quad + {len(COLOURS)} colour variants")
    return 0


if __name__ == "__main__":
    sys.exit(main())
