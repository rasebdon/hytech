#!/usr/bin/env python3
"""
Verifies every asset path our JSON references actually resolves, and every item a recipe names
actually exists.

A missing `Icon` or texture is a *fatal* validation error for that item at load time, and the
server reports it as a wall of SEVERE lines rather than failing the build -- so it is easy to
ship and only find out when launching. This catches the same thing in a second.

References are resolved against our own `Common/` tree first and then the game's `Assets.zip`,
because plenty of our assets legitimately point at vanilla textures and models.

Usage:
    python scripts/check-asset-refs.py
"""

from __future__ import annotations

import json
import os
import re
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"
COMMON = RESOURCES / "Common"

# Keys whose values name a file under Common/. Kept explicit rather than "any string that looks
# like a path", so a stray description never trips this.
ASSET_KEYS = {
    "Icon", "CustomModel", "Texture", "Model", "TransitionTexture",
    "All", "Sides", "UpDown", "Up", "Down", "North", "South", "East", "West",
}


def game_assets() -> set[str]:
    """Names inside the game's Assets.zip, or an empty set if it is not installed."""
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


def item_ids(vanilla: set[str]) -> set[str]:
    """Every item id the server will know: the game's, plus ours.

    An item's id is its file name, which is how `AssetBuilderCodec` keys the store.
    """
    known = {
        name.split("/")[-1][:-5]
        for name in vanilla
        if name.startswith("Server/Item/Items/") and name.endswith(".json")
    }

    known.update(path.stem for path in (RESOURCES / "Server/Item/Items").rglob("*.json"))

    return known


def collect_items(node: object, out: list[str]) -> None:
    """Every `ItemId` under a recipe: inputs, outputs, upgrade materials."""
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "ItemId" and isinstance(value, str):
                out.append(value)
            else:
                collect_items(value, out)
    elif isinstance(node, list):
        for value in node:
            collect_items(value, out)


def check_recipes(vanilla: set[str]) -> list[tuple[Path, str]]:
    """Recipes naming an item that does not exist.

    Worth its own pass because the failure is quiet in a different way from a missing texture: the
    recipe loads, validates, and then simply never matches anything, so a machine sits idle with no
    log line to explain why. Generated recipes make this cheap to get wrong at scale.
    """
    known = item_ids(vanilla)
    missing: list[tuple[Path, str]] = []

    for path in sorted(RESOURCES.rglob("*.json")):
        if path.name == "manifest.json":
            continue

        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue  # already reported by the asset pass

        references: list[str] = []
        for key in ("Recipe", "Input", "Output", "PrimaryOutput"):
            collect_items(payload.get(key), references)

        for reference in references:
            if reference not in known:
                missing.append((path, reference))

    return missing


UI_ROOT = COMMON / "UI" / "Custom"

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
    """Where a UIPath points, given the file it was written in.

    A UIPath is relative to the *declaring document*, not to any root -- so vanilla's own
    "Common/ContainerPanelPatch.png", copied into a file one directory deeper, quietly resolves
    somewhere that does not exist. The client draws a white cross and logs nothing, which is a
    miserable thing to debug by eye.
    """
    return (document.parent / reference).resolve()


def check_ui(vanilla: set[str]) -> tuple[list[tuple[Path, str]], int]:
    """Texture and document references inside .ui files."""
    missing: list[tuple[Path, str]] = []
    checked = 0

    for document in sorted(UI_ROOT.rglob("*.ui")):
        text = document.read_text(encoding="utf-8")
        text = re.sub(r"//[^\n]*", "", text)

        references = UI_PATH_PATTERN.findall(text) + UI_DOCUMENT_PATTERN.findall(text)

        for reference in references:
            checked += 1

            target = resolve_ui_path(document, reference)
            if target.exists():
                continue

            # The game ships most UI art only at @2x and references it without the suffix.
            retina = target.with_name(target.stem + "@2x" + target.suffix)
            if retina.exists():
                continue

            try:
                relative = target.relative_to(RESOURCES).as_posix()
            except ValueError:
                missing.append((document, reference))
                continue

            if relative in vanilla:
                continue

            stem, _, suffix = relative.rpartition(".")
            if f"{stem}@2x.{suffix}" in vanilla:
                continue

            missing.append((document, reference))

    return missing, checked


def main() -> int:
    vanilla = game_assets()

    missing: list[tuple[Path, str]] = []
    checked = 0

    for path in sorted(RESOURCES.rglob("*.json")):
        if path.name == "manifest.json":
            continue

        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            missing.append((path, f"invalid JSON: {error}"))
            continue

        references: list[str] = []
        collect(payload, references)

        for reference in references:
            checked += 1

            if (COMMON / reference).exists():
                continue
            if f"Common/{reference}" in vanilla:
                continue

            missing.append((path, reference))

    print(f"Checked {checked} asset references across the resource tree.")

    if missing:
        print(f"\n{len(missing)} unresolved:", file=sys.stderr)
        for path, reference in missing:
            print(f"  {path.relative_to(RESOURCES)}  ->  {reference}", file=sys.stderr)
        print("\nEach of these is a fatal asset-validation error at server start.",
              file=sys.stderr)
        return 1

    print("All referenced assets resolve, in our tree or in the game's Assets.zip.")

    unknown = check_recipes(vanilla)
    if unknown:
        print(f"\n{len(unknown)} recipe references name an item that does not exist:",
              file=sys.stderr)
        for path, reference in unknown:
            print(f"  {path.relative_to(RESOURCES)}  ->  {reference}", file=sys.stderr)
        print("\nSuch a recipe loads and then never matches, with nothing in the log.",
              file=sys.stderr)
        return 1

    print("All recipe item references exist.")

    unresolved, ui_checked = check_ui(vanilla)
    print(f"Checked {ui_checked} UI references across {UI_ROOT.name}/.")

    if unresolved:
        print(f"\n{len(unresolved)} UI references do not resolve:", file=sys.stderr)
        for document, reference in unresolved:
            print(f"  {document.relative_to(RESOURCES)}  ->  {reference}", file=sys.stderr)
        print("\nA UIPath is relative to the document it is written in. A miss is silent: the"
              "\nclient draws a white missing-texture cross and logs nothing.", file=sys.stderr)
        return 1

    print("All UI texture and document references resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
