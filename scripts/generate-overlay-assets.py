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

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"

MODEL_PATH = RESOURCES / "Common/VFX/Overlay/Face_Overlay.blockymodel"
TEXTURE_DIR = RESOURCES / "Common/VFX/Overlay"
MODEL_JSON_DIR = RESOURCES / "Server/Models/Overlay"

# ModelAsset validates that its Model and Texture live under one of these Common roots,
# so the quad cannot sit beside the block assets under Blocks/ or BlockTextures/.
COMMON_ASSET_ROOTS = ("Characters/", "NPC/", "Items/", "VFX/")

# Keep in step with BlockFaceConfigType on the Java side.
COLOURS = {
    "None": (0x80, 0x80, 0x80),
    "Both": (0xA0, 0x40, 0xC0),
    "Input": (0xD0, 0x30, 0x30),
    "Output": (0x30, 0x60, 0xD0),
}

# Must be at least as large as the biggest face of the quad (QUAD_SIZE x QUAD_SIZE in
# model units), because every face's UV offset is (0,0). A smaller texture makes the faces
# sample past their own region -- which reads neighbouring atlas entries and shows up as
# mixed colours and missing patches.
TEXTURE_SIZE = 64
# Binary alpha only. This render path does cutout, not blending: a uniform 50% alpha comes
# out fully opaque, because entity models ignore texture alpha (vanilla fades a model with
# ModelVFX.PostColorOpacity instead). So the design works with 0/255 alpha rather than
# against it -- a solid frame around the face, and a sparse wash inside it.

# Width of the fully opaque border, in texture pixels.
BORDER_PX = 4

# Interior wash: one opaque pixel per FILL_PERIOD x FILL_PERIOD cell, so 2 gives 25%
# coverage. Raise it for a lighter tint, lower it for a denser one.
FILL_PERIOD = 2

# A block spans 32 model units. The quad covers a whole side and is one unit thick.
QUAD_SIZE = 32


def overlay_png(rgb: tuple[int, int, int], size: int) -> bytes:
    """Framed RGBA PNG using only 0/255 alpha, so no image library is needed.

    A solid border marks the face unambiguously; the sparse interior wash tints it while
    still letting the block's own texture read through. Both rely on cutout, not blending.
    """
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
    """A flat plane, mirroring how vanilla builds VFX quads (Common/VFX/Fire).

    A "box" with "flat" shading is the wrong shape for this: it has six faces to texture
    and renders as a solid slab. "quad" is a single-faced plane with a 2D size, which is
    what an overlay wants. Note that neither shape blends texture alpha -- this render path
    does cutout only -- so the texture is designed around 0/255 alpha.

    The quad lies in the XY plane facing +Z, so the overlay rotates it onto each block side
    rather than translating a slab.
    """
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    stale: list[Path] = []

    write(MODEL_PATH, (json.dumps(quad_model(), indent=2) + "\n").encode("utf-8"), args.check, stale)

    for name, rgb in COLOURS.items():
        write(TEXTURE_DIR / f"Face_Overlay_{name}.png",
              overlay_png(rgb, TEXTURE_SIZE), args.check, stale)

        # Without an explicit HitBox the asset has no bounding box, which leaves the
        # spawned model with nothing for the client to size or cull against.
        model_json = {
            "Model": "VFX/Overlay/Face_Overlay.blockymodel",
            "Texture": f"VFX/Overlay/Face_Overlay_{name}.png",
            "HitBox": {
                "Min": {"X": -0.5, "Y": -0.03, "Z": -0.5},
                "Max": {"X": 0.5, "Y": 0.03, "Z": 0.5},
            },
        }
        write(MODEL_JSON_DIR / f"Face_Overlay_{name}.json",
              (json.dumps(model_json, indent=2) + "\n").encode("utf-8"), args.check, stale)

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
