#!/usr/bin/env python3
"""
Generates the per-connection-mask pipe assets.

A pipe block renders as a centre hub plus one arm per connected face. Rather than
spawning an entity per arm at runtime, every one of the 64 possible connection
masks gets its own block-state variant: a model, a matching multi-box hitbox, and
a "State" entry on the pipe's item JSON. The server then only has to call
setBlockInteractionState(pos, blockType, "Conn_<mask>") when the topology changes.

Everything here is derived from the two hand-authored source models, so the arm
geometry and texture layout stay in one place:

    Common/Blocks/Pipes/Default/Pipe_Center.blockymodel   -> the hub node
    Common/Blocks/Pipes/Default/Pipe_Full.blockymodel     -> the arm node (+Y)

Usage:
    python scripts/generate-pipe-assets.py           # write assets
    python scripts/generate-pipe-assets.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import copy
import json
import math
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"

# Each pipe type has its own hub and arm geometry with its own UV layout and size, so
# they cannot share a generated model set: the item hub is 12 units and the default hub is
# 8, and their textures are authored against those specific layouts.
PIPE_TYPES = [
    {
        "name": "Default",
        "center_model": RESOURCES / "Common/Blocks/Pipes/Default/Pipe_Center.blockymodel",
        "arm_model": RESOURCES / "Common/Blocks/Pipes/Default/Pipe_Full.blockymodel",
        "hub_units": 8,
        # Every scalar resource shares this geometry; only CustomModelTexture on each item
        # JSON differs, so a new type costs no generated models or hitboxes at all.
        "item_jsons": [
            RESOURCES / "Server/Item/Items/Pipes/Energy/Pipe_Energy.json",
            RESOURCES / "Server/Item/Items/Pipes/Fluid/Pipe_Fluid.json",
            RESOURCES / "Server/Item/Items/Pipes/Gas/Pipe_Gas.json",
            RESOURCES / "Server/Item/Items/Pipes/Heat/Pipe_Heat.json",
        ],
    },
    {
        "name": "Items",
        "center_model": RESOURCES / "Common/Blocks/Pipes/Items/Pipe_Items_Center.blockymodel",
        "arm_model": RESOURCES / "Common/Items/Pipes/Items/Pipe_Items_Normal.blockymodel",
        "hub_units": 12,
        "item_jsons": [RESOURCES / "Server/Item/Items/Pipes/Items/Pipe_Items.json"],
    },
]

# A block model spans 32 units, with the hub centred on it.
BLOCK_UNITS = 32

MODEL_ROOT = RESOURCES / "Common/Blocks/Pipes/Generated"
HITBOX_ROOT = RESOURCES / "Server/Item/Block/Hitboxes/Pipes/Generated"

# Bit layout mirrors com.hypixel.hytale.protocol.BlockFace, minus the None entry:
# Up(1) -> bit 0, Down(2) -> bit 1, ... West(6) -> bit 5. Keeping the same order as
# the enum means the Java side can do `1 << (face.getValue() - 1)` with no table.
FACES = [
    ("Up", (0, 1, 0)),
    ("Down", (0, -1, 0)),
    ("North", (0, 0, -1)),
    ("South", (0, 0, 1)),
    ("East", (1, 0, 0)),
    ("West", (-1, 0, 0)),
]

# The authored arm points +Y, so each face needs the quaternion that carries +Y onto
# its direction. Rotating about X sweeps +Y through +/-Z; rotating about Z sweeps it
# through -/+X.
HALF_SQRT2 = math.sqrt(2.0) / 2.0
ARM_ORIENTATIONS = {
    "Up": (0.0, 0.0, 0.0, 1.0),
    "Down": (1.0, 0.0, 0.0, 0.0),
    "North": (-HALF_SQRT2, 0.0, 0.0, HALF_SQRT2),
    "South": (HALF_SQRT2, 0.0, 0.0, HALF_SQRT2),
    "East": (0.0, 0.0, -HALF_SQRT2, HALF_SQRT2),
    "West": (0.0, 0.0, HALF_SQRT2, HALF_SQRT2),
}

def hub_extents(hub_units: int) -> tuple[float, float]:
    """Block-local extents of the hub, derived from its size in model units."""
    half = hub_units / 2.0 / BLOCK_UNITS
    return 0.5 - half, 0.5 + half



def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def find_node(nodes: list[dict], name: str) -> dict:
    for node in nodes:
        if node.get("name") == name:
            return node
        child = find_node(node.get("children", []), name)
        if child is not None:
            return child
    return None


def build_model(mask: int, center_node: dict, arm_node: dict) -> dict:
    """Centre hub plus one arm per set bit, each rotated onto its face."""
    nodes = [copy.deepcopy(center_node)]
    nodes[0]["id"] = "1"

    next_id = 2
    for bit, (face, _) in enumerate(FACES):
        if not mask & (1 << bit):
            continue

        arm = copy.deepcopy(arm_node)
        arm["name"] = f"Connection_{face}"
        x, y, z, w = ARM_ORIENTATIONS[face]
        arm["orientation"] = {"x": x, "y": y, "z": z, "w": w}

        # Re-id the arm and its children so ids stay unique within the model.
        arm["id"] = str(next_id)
        next_id += 1
        for child in arm.get("children", []):
            child["id"] = str(next_id)
            next_id += 1

        nodes.append(arm)

    return {"nodes": nodes, "format": "prop", "lod": "auto"}


def build_hitbox(mask: int, hub_units: int) -> dict:
    HUB_MIN, HUB_MAX = hub_extents(hub_units)

    boxes = [
        {
            "Min": {"X": HUB_MIN, "Y": HUB_MIN, "Z": HUB_MIN},
            "Max": {"X": HUB_MAX, "Y": HUB_MAX, "Z": HUB_MAX},
        }
    ]

    for bit, (_, direction) in enumerate(FACES):
        if not mask & (1 << bit):
            continue

        lo = {"X": HUB_MIN, "Y": HUB_MIN, "Z": HUB_MIN}
        hi = {"X": HUB_MAX, "Y": HUB_MAX, "Z": HUB_MAX}

        # Stretch the box from the hub out to the block edge along the arm's axis.
        for axis, component in zip("XYZ", direction):
            if component > 0:
                hi[axis] = 1.0
            elif component < 0:
                lo[axis] = 0.0

        boxes.append({"Min": lo, "Max": hi})

    return {"Boxes": boxes}


def keep_only_normal_cap(arm_node: dict) -> dict:
    """
    The authored arm carries all three end caps (Normal/Push/Pull) as siblings so
    the modelling tool can show them together. A rendered arm wants just the plain
    collar; push/pull are surfaced separately as marker entities.
    """
    arm = copy.deepcopy(arm_node)
    arm["children"] = [
        child for child in arm.get("children", []) if child.get("name", "").startswith("Normal")
    ]
    return arm


def state_definitions(type_name: str) -> dict:
    """The "Definitions" map spliced into each pipe's BlockType.State.

    State names only need to be unique within their parent block, but hitbox assets are
    keyed globally by file name, so those carry the pipe type.
    """
    definitions = {}
    for mask in range(64):
        name = f"Conn_{mask}"
        definitions[name] = {
            "CustomModel": f"Blocks/Pipes/Generated/{type_name}/{name}.blockymodel",
            # Both collision and targeting use the shaped arm boxes, so a pipe is only hit
            # where its geometry actually is. A full-cell interaction box made breaking
            # uniform but let a pipe swallow clicks aimed past it at anything behind.
            "HitboxType": f"Pipe_{type_name}_{name}",
            "InteractionHitboxType": f"Pipe_{type_name}_{name}",
        }
    return definitions


def write_json(path: Path, payload: dict, check: bool, stale: list[Path]) -> None:
    text = json.dumps(payload, indent=2) + "\n"
    if check:
        if not path.exists() or path.read_text(encoding="utf-8") != text:
            stale.append(path)
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the generated assets are up to date instead of writing them",
    )
    args = parser.parse_args()

    stale: list[Path] = []

    for pipe_type in PIPE_TYPES:
        name = pipe_type["name"]
        center_node = find_node(load_json(pipe_type["center_model"])["nodes"], "Center")
        arm_source = find_node(load_json(pipe_type["arm_model"])["nodes"], "Connection")
        if center_node is None or arm_source is None:
            print(f"error: missing Center/Connection node for pipe type {name}", file=sys.stderr)
            return 1

        arm_node = keep_only_normal_cap(arm_source)

        for mask in range(64):
            mask_name = f"Conn_{mask}"
            write_json(MODEL_ROOT / name / f"{mask_name}.blockymodel",
                       build_model(mask, center_node, arm_node), args.check, stale)
            write_json(HITBOX_ROOT / name / f"Pipe_{name}_{mask_name}.json",
                       build_hitbox(mask, pipe_type["hub_units"]), args.check, stale)

        definitions = state_definitions(name)
        for pipe_json in pipe_type["item_jsons"]:
            payload = load_json(pipe_json)
            # No marker key here: BlockType.State is decoded by a strict codec, so an
            # extra comment field would be rejected.
            payload.setdefault("BlockType", {})["State"] = {"Definitions": definitions}
            write_json(pipe_json, payload, args.check, stale)

    if args.check:
        if stale:
            print("Stale generated pipe assets:", file=sys.stderr)
            for path in stale:
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-pipe-assets.py", file=sys.stderr)
            return 1
        print("Generated pipe assets are up to date.")
        return 0

    for pipe_type in PIPE_TYPES:
        print(f"{pipe_type['name']}: 64 models + 64 hitboxes "
              f"(hub {pipe_type['hub_units']}u -> {hub_extents(pipe_type['hub_units'])})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
