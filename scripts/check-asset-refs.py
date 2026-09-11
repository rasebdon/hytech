#!/usr/bin/env python3
"""
Verifies every asset path our JSON references resolves, every item a recipe names exists, and
every pipe item declares the block states its renderer needs -- catching in a second what would
otherwise surface as a wall of SEVERE lines (a fatal but non-failing validation error) at launch.

References resolve against every mod's `Common/` tree plus the game's `Assets.zip`, since
`Common/` is one flat, last-pack-wins namespace at runtime and cross-mod references are valid.

Usage:
    python scripts/check-asset-refs.py
    python scripts/check-asset-refs.py --resources HytechCore/src/main/resources
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Explicit allow-list rather than "any string that looks like a path", so a stray description
# never trips this.
ASSET_KEYS = {
    "Icon", "CustomModel", "Texture", "Model", "TransitionTexture",
    "All", "Sides", "UpDown", "Up", "Down", "North", "South", "East", "West",
}


def resource_roots(explicit: list[str]) -> list[Path]:
    """Checked together, not one at a time: the runtime merges packs, so a per-project run would
    report every cross-mod reference as missing."""
    if explicit:
        roots = [Path(value).resolve() for value in explicit]
    else:
        roots = sorted(path.resolve() for path in REPO_ROOT.glob("*/src/main/resources")
                       if path.is_dir())

    if not roots:
        sys.exit("no resource roots found; expected */src/main/resources")

    for root in roots:
        if not root.is_dir():
            sys.exit(f"not a directory: {root}")

    return roots


def display(path: Path) -> str:
    try:
        return path.relative_to(REPO_ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def game_assets() -> set[str]:
    """Empty set if Assets.zip is not installed."""
    appdata = os.environ.get("APPDATA")
    if not appdata:
        home = os.environ.get("HOME", "")
        appdata = f"{home}/.var/app/com.hypixel.HytaleLauncher/data"

    archive = Path(appdata) / "Hytale/install/release/package/game/latest/Assets.zip"
    if not archive.exists():
        print(f"note: {archive} not found; vanilla references cannot be verified",
              file=sys.stderr)
        return set()

    with zipfile.ZipFile(archive) as zf:
        return set(zf.namelist())


def looks_like_a_path(value: object) -> bool:
    if not isinstance(value, str) or "/" not in value:
        return False

    return "." in value.rsplit("/", 1)[-1]


def collect(node: object, out: list[str]) -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ASSET_KEYS and looks_like_a_path(value):
                out.append(value)
            collect(value, out)
    elif isinstance(node, list):
        for value in node:
            collect(value, out)


def json_files(roots: list[Path]) -> list[Path]:
    return sorted(path for root in roots for path in root.rglob("*.json")
                  if path.name != "manifest.json")


def item_ids(roots: list[Path], vanilla: set[str]) -> set[str]:
    """An item's id is its file name (how `AssetBuilderCodec` keys the store)."""
    known = {
        name.split("/")[-1][:-5]
        for name in vanilla
        if name.startswith("Server/Item/Items/") and name.endswith(".json")
    }

    for root in roots:
        known.update(path.stem for path in (root / "Server/Item/Items").rglob("*.json"))

    return known


def collect_items(node: object, out: list[str]) -> None:
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "ItemId" and isinstance(value, str):
                out.append(value)
            else:
                collect_items(value, out)
    elif isinstance(node, list):
        for value in node:
            collect_items(value, out)


def check_recipes(roots: list[Path], vanilla: set[str]) -> list[tuple[Path, str]]:
    """A recipe naming a nonexistent item loads and validates fine, then simply never matches --
    no log line, just a machine that silently sits idle."""
    known = item_ids(roots, vanilla)
    missing: list[tuple[Path, str]] = []

    for path in json_files(roots):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

        references: list[str] = []
        for key in ("Recipe", "Input", "Output", "PrimaryOutput"):
            collect_items(payload.get(key), references)

        for reference in references:
            if reference not in known:
                missing.append((path, reference))

    return missing


# A missing pipe block state is not an error anywhere -- setBlockInteractionState just no-ops and
# the pipe renders as unconnected -- so this is the only check for that contract.
PIPE_COMPONENT = re.compile(r"^hytech:\w+:pipe$")

# Kept in step with PipeConnectionMask: six faces, so 64 masks, named `Conn_<mask>`.
PIPE_STATE_COUNT = 64

# Defaults from LogisticPipeComponent.DEFAULT_CONNECTION_MODEL_ASSETS.
PIPE_MODEL_KEYS = {
    "NormalConnectionModelAsset": "Pipe_Normal",
    "PullConnectionModelAsset": "Pipe_Pull",
    "PushConnectionModelAsset": "Pipe_Push",
}


def model_asset_ids(roots: list[Path], vanilla: set[str]) -> set[str]:
    known = {
        name.split("/")[-1][:-5]
        for name in vanilla
        if name.startswith("Server/Models/") and name.endswith(".json")
    }

    for root in roots:
        known.update(path.stem for path in (root / "Server/Models").rglob("*.json"))

    return known


def hitbox_ids(roots: list[Path], vanilla: set[str]) -> set[str]:
    known = {
        name.split("/")[-1][:-5]
        for name in vanilla
        if name.startswith("Server/Item/Block/Hitboxes/") and name.endswith(".json")
    }

    for root in roots:
        known.update(path.stem
                     for path in (root / "Server/Item/Block/Hitboxes").rglob("*.json"))

    return known


def pipe_components(payload: dict) -> list[str]:
    block_type = payload.get("BlockType")
    if not isinstance(block_type, dict):
        return []

    entity = block_type.get("BlockEntity")
    if not isinstance(entity, dict):
        return []

    components = entity.get("Components")
    if not isinstance(components, dict):
        return []

    return [name for name in components if PIPE_COMPONENT.match(name)]


def check_pipes(roots: list[Path], vanilla: set[str]) -> tuple[list[tuple[Path, str]], int]:
    problems: list[tuple[Path, str]] = []
    models = model_asset_ids(roots, vanilla)
    hitboxes = hitbox_ids(roots, vanilla)
    checked = 0

    for path in json_files(roots):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue

        if not isinstance(payload, dict) or not pipe_components(payload):
            continue

        checked += 1
        block_type = payload["BlockType"]
        components = block_type["BlockEntity"]["Components"]

        state = block_type.get("State")
        definitions = state.get("Definitions") if isinstance(state, dict) else None
        if not isinstance(definitions, dict):
            problems.append((path, "declares a pipe component but no BlockType.State.Definitions"))
            continue

        expected = {f"Conn_{mask}" for mask in range(PIPE_STATE_COUNT)}
        for name in sorted(expected - definitions.keys()):
            problems.append((path, f"missing block state {name}"))

        for name, definition in sorted(definitions.items()):
            if not isinstance(definition, dict):
                continue

            hitbox = definition.get("HitboxType")
            if isinstance(hitbox, str) and hitbox not in hitboxes:
                problems.append((path, f"{name} names unknown HitboxType {hitbox}"))

        for component in components.values():
            if not isinstance(component, dict):
                continue

            for key, fallback in PIPE_MODEL_KEYS.items():
                name = component.get(key, fallback)
                if isinstance(name, str) and name not in models:
                    problems.append((path, f"{key} names unknown ModelAsset {name}"))

    return problems, checked


# Properties in a .ui document whose value names a file. Everything else that happens to be a
# quoted string -- a label, a tooltip -- is left alone.
UI_PATH_KEYS = (
    "TexturePath", "Background", "MaskTexturePath", "BarTexturePath", "EffectTexturePath",
    "ContentMaskTexturePath", "Overlay", "Handle", "HoveredHandle", "DraggedHandle",
    "DefaultBackground", "HoveredBackground", "PressedBackground", "DisabledBackground",
    "SelectedBackground", "LabelMaskTexturePath", "DefaultArrowTexturePath",
    "HoveredArrowTexturePath", "PressedArrowTexturePath", "AssetPath",
)

UI_PATH_PATTERN = re.compile(
    r"\b(?:" + "|".join(UI_PATH_KEYS) + r")\s*:\s*\(?[^\"\n]*\"([^\"]+\.(?:png|ui))\""
)

UI_DOCUMENT_PATTERN = re.compile(r"^\s*\$\w+\s*=\s*\"([^\"]+\.ui)\"", re.MULTILINE)


def resolve_ui_path(document: Path, reference: str) -> Path:
    """A UIPath is relative to the declaring document, not to any root -- a miss draws a silent
    white missing-texture cross with nothing logged."""
    return (document.parent / reference).resolve()


def check_ui(roots: list[Path], vanilla: set[str]) -> tuple[list[tuple[Path, str]], int]:
    missing: list[tuple[Path, str]] = []
    checked = 0

    for root in roots:
        ui_root = root / "Common" / "UI" / "Custom"
        if not ui_root.is_dir():
            continue

        for document in sorted(ui_root.rglob("*.ui")):
            text = document.read_text(encoding="utf-8")
            text = re.sub(r"//[^\n]*", "", text)

            references = UI_PATH_PATTERN.findall(text) + UI_DOCUMENT_PATTERN.findall(text)

            for reference in references:
                checked += 1

                target = resolve_ui_path(document, reference)
                if ui_reference_resolves(target, root, roots, vanilla):
                    continue

                missing.append((document, reference))

    return missing, checked


def ui_reference_resolves(target: Path, root: Path, roots: list[Path],
                          vanilla: set[str]) -> bool:
    candidates = [target]

    # Also try the same relative location in another mod's tree: `Common/` is one namespace.
    try:
        relative = target.relative_to(root)
    except ValueError:
        relative = None
    else:
        candidates += [other / relative for other in roots if other != root]

    for candidate in candidates:
        if candidate.exists():
            return True

        # The game ships most UI art only at @2x and references it without the suffix.
        retina = candidate.with_name(candidate.stem + "@2x" + candidate.suffix)
        if retina.exists():
            return True

    if relative is None:
        return False

    name = relative.as_posix()
    if name in vanilla:
        return True

    stem, _, suffix = name.rpartition(".")
    return f"{stem}@2x.{suffix}" in vanilla


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", action="append", default=[], metavar="DIR",
                        help="a resource root to check; defaults to every */src/main/resources")
    args = parser.parse_args()

    roots = resource_roots(args.resources)
    vanilla = game_assets()

    print("Checking " + ", ".join(display(root) for root in roots))

    missing: list[tuple[Path, str]] = []
    checked = 0

    for path in json_files(roots):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            missing.append((path, f"invalid JSON: {error}"))
            continue

        references: list[str] = []
        collect(payload, references)

        for reference in references:
            checked += 1

            if any((root / "Common" / reference).exists() for root in roots):
                continue
            if f"Common/{reference}" in vanilla:
                continue

            missing.append((path, reference))

    trees = "tree" if len(roots) == 1 else "trees"
    print(f"Checked {checked} asset references across {len(roots)} resource {trees}.")

    if missing:
        print(f"\n{len(missing)} unresolved:", file=sys.stderr)
        for path, reference in missing:
            print(f"  {display(path)}  ->  {reference}", file=sys.stderr)
        print("\nEach of these is a fatal asset-validation error at server start.",
              file=sys.stderr)
        return 1

    print("All referenced assets resolve, in some pack or in the game's Assets.zip.")

    unknown = check_recipes(roots, vanilla)
    if unknown:
        print(f"\n{len(unknown)} recipe references name an item that does not exist:",
              file=sys.stderr)
        for path, reference in unknown:
            print(f"  {display(path)}  ->  {reference}", file=sys.stderr)
        print("\nSuch a recipe loads and then never matches, with nothing in the log.",
              file=sys.stderr)
        return 1

    print("All recipe item references exist.")

    pipe_problems, pipes_checked = check_pipes(roots, vanilla)
    print(f"Checked {pipes_checked} pipe items.")

    if pipe_problems:
        print(f"\n{len(pipe_problems)} pipe problems:", file=sys.stderr)
        for path, problem in pipe_problems:
            print(f"  {display(path)}  ->  {problem}", file=sys.stderr)
        print("\nA pipe renders from one block-state variant per connection mask. A state the"
              "\nitem does not declare makes setBlockInteractionState a silent no-op, and the"
              "\npipe draws as though nothing were connected.", file=sys.stderr)
        return 1

    print("Every pipe item declares all 64 connection states, with models that resolve.")

    unresolved, ui_checked = check_ui(roots, vanilla)
    print(f"Checked {ui_checked} UI references.")

    if unresolved:
        print(f"\n{len(unresolved)} UI references do not resolve:", file=sys.stderr)
        for document, reference in unresolved:
            print(f"  {display(document)}  ->  {reference}", file=sys.stderr)
        print("\nA UIPath is relative to the document it is written in. A miss is silent: the"
              "\nclient draws a white missing-texture cross and logs nothing.", file=sys.stderr)
        return 1

    print("All UI texture and document references resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
