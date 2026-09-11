#!/usr/bin/env python3
"""
Generates the material and component assets (item definitions, recipes, language lines) from the
table in `hytech_materials.py`, so a balance change is one edit instead of forty.

What it writes:

    Server/Item/Items/Materials/*.json     dusts, plates and the steel bar
    Server/Item/Items/Components/*.json    wire, coils, circuits, casings, frames
    Server/Item/Recipes/Hytech/Crusher/    ore -> dust, bar -> dust
    Server/Item/Recipes/Hytech/Smelter/    dust -> bar, ore -> bar, and the alloys
    Server/Languages/en-US/materials.lang  every generated item's name

Also rewrites the `Recipe` key of the hand-authored blocks named in `table.BLOCK_RECIPES`,
leaving every other key alone.

A translation key is `<file name>.<key in file>` (`I18nModule.getPrefix`), so names here are
`materials.items.X.name`, not `server.items.X.name`.

Icons are `generate-icons.py`'s job; a missing `Icon` is a fatal validation error for that item.

Usage:
    python scripts/generate-material-assets.py           # write assets
    python scripts/generate-material-assets.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import hytech_materials as table  # noqa: E402  (deliberate: needs the sys.path line above)

from paths import REPO_ROOT, resources, CONTENT

RESOURCES = resources(CONTENT)

ITEMS_DIR = RESOURCES / "Server/Item/Items"
MATERIALS_DIR = RESOURCES / "Server/Item/Items/Materials"
COMPONENTS_DIR = RESOURCES / "Server/Item/Items/Components"
CRUSHER_DIR = RESOURCES / "Server/Item/Recipes/Hytech/Crusher"
SMELTER_DIR = RESOURCES / "Server/Item/Recipes/Hytech/Smelter"
LANG_FILE = RESOURCES / "Server/Languages/en-US/materials.lang"

# Vanilla's pouch and ingot models stand in for every generated item: the icons tell them apart in
# an inventory, and bespoke models can replace these later without touching a recipe.
POUCH_MODEL = "Resources/Ingredients/Spore_Bag.blockymodel"
POUCH_TEXTURE = "Resources/Ingredients/Spore_Bag_Boomshroom_Texture.png"
INGOT_MODEL = "Resources/Materials/Ingot.blockymodel"
INGOT_TEXTURE = "Resources/Materials/Ingot_Textures/Copper.png"


def item(item_id: str, category: str, level: int, model: str, texture: str,
         recipe: dict | None, scale: float, translation: list[float]) -> dict:
    definition = {
        "TranslationProperties": {"Name": f"materials.items.{item_id}.name"},
        "Categories": [category],
        "ItemLevel": level,
        "MaxStack": 100,
        "Model": model,
        "Texture": texture,
        "Icon": f"Icons/ItemsGenerated/{item_id}.png",
        "PlayerAnimationsId": "Item",
        "IconProperties": {
            "Scale": scale,
            "Translation": translation,
            "Rotation": [22.5, 45, 22.5],
        },
        "Tags": {"Type": ["Ingredient"]},
        "DropOnDeath": True,
    }

    if recipe is not None:
        definition["Recipe"] = recipe

    return definition


def bench_recipe(inputs: list[tuple[str, int]], quantity: int, seconds: float,
                 category: str) -> dict:
    requirement = (table.VANILLA_WORKBENCH if category == "Workbench_Crafting"
                   else table.bench(category))

    return {
        "Input": [{"ItemId": ingredient, "Quantity": count} for ingredient, count in inputs],
        "BenchRequirement": requirement,
        "OutputQuantity": quantity,
        "TimeSeconds": seconds,
    }


def machine_recipe(inputs: list[tuple[str, int]], output_id: str, quantity: int,
                   group: str, seconds: float) -> dict:
    return {
        "Input": [{"ItemId": ingredient, "Quantity": count} for ingredient, count in inputs],
        "PrimaryOutput": {"ItemId": output_id, "Quantity": quantity},
        "Output": [{"ItemId": output_id, "Quantity": quantity}],
        "BenchRequirement": [{"Type": "Processing", "Id": group}],
        "TimeSeconds": seconds,
    }


def build() -> tuple[dict[Path, str], dict[str, str]]:
    files: dict[Path, str] = {}
    names: dict[str, str] = {}

    def write(path: Path, payload: dict) -> None:
        files[path] = json.dumps(payload, indent=2) + "\n"

    for metal in table.METALS:
        write(MATERIALS_DIR / f"{metal.dust}.json",
              item(metal.dust, "Technic.Materials", 10, POUCH_MODEL, POUCH_TEXTURE,
                   None, 0.7, [0.6, -9.6]))
        names[metal.dust] = f"{metal.name} Dust"

        write(MATERIALS_DIR / f"{metal.plate}.json",
              item(metal.plate, "Technic.Materials", 14, INGOT_MODEL, INGOT_TEXTURE,
                   bench_recipe([(metal.bar, 1)], 1, 2, table.CATEGORY_MATERIALS),
                   1, [0, -3]))
        names[metal.plate] = f"{metal.name} Plate"

        if metal.owns_bar:
            write(MATERIALS_DIR / f"{metal.bar}.json",
                  item(metal.bar, "Technic.Materials", 16, INGOT_MODEL, INGOT_TEXTURE,
                       None, 1, [0, -3]))
            names[metal.bar] = f"{metal.name} Bar"

        if metal.ore is not None:
            write(CRUSHER_DIR / f"Hytech_Crush_Ore_{metal.name}.json",
                  machine_recipe([(metal.ore, 1)], metal.dust, 2, table.CRUSHER_GROUP, 4))

            # Smelting ore directly still works, at vanilla's 1:1 -- crushing is a yield choice,
            # not the only path.
            write(SMELTER_DIR / f"Hytech_Smelt_Ore_{metal.name}.json",
                  machine_recipe([(metal.ore, 1)], metal.bar, 1, table.SMELTER_GROUP,
                                 metal.smelt_seconds + 4))

        # How an alloy gets a dust at all: crushing its bar back down.
        write(CRUSHER_DIR / f"Hytech_Crush_Bar_{metal.name}.json",
              machine_recipe([(metal.bar, 1)], metal.dust, 1, table.CRUSHER_GROUP, 4))

        write(SMELTER_DIR / f"Hytech_Smelt_Dust_{metal.name}.json",
              machine_recipe([(metal.dust, 1)], metal.bar, 1, table.SMELTER_GROUP,
                             metal.smelt_seconds))

    for alloy in table.ALLOYS:
        metal = table.BY_NAME[alloy.metal]
        write(SMELTER_DIR / f"Hytech_Alloy_{alloy.metal}.json",
              machine_recipe(alloy.inputs, metal.bar, alloy.output_quantity,
                             table.SMELTER_GROUP, alloy.seconds))

    for component in table.COMPONENTS:
        write(COMPONENTS_DIR / f"{component.id}.json",
              item(component.id, "Technic.Components", component.item_level,
                   INGOT_MODEL, INGOT_TEXTURE,
                   bench_recipe(component.inputs, component.output_quantity,
                                component.seconds, table.CATEGORY_COMPONENTS),
                   1, [0, -3]))
        names[component.id] = component.name

    return files, names


def block_recipes() -> dict[Path, str]:
    """Read-modify-write: these files carry models and block states no table should own."""
    files: dict[Path, str] = {}

    for entry in table.BLOCK_RECIPES:
        path = ITEMS_DIR / entry.path
        if not path.exists():
            raise SystemExit(f"BLOCK_RECIPES names a file that does not exist: {entry.path}")

        definition = json.loads(path.read_text(encoding="utf-8"))
        definition["Recipe"] = bench_recipe(entry.inputs, entry.output_quantity,
                                            entry.seconds, entry.category)

        files[path] = json.dumps(definition, indent=2) + "\n"

    return files


def lang(names: dict[str, str]) -> str:
    header = "# Generated by scripts/generate-material-assets.py -- edit the table, not this file.\n"
    body = "".join(f"items.{item_id}.name={name}\n" for item_id, name in sorted(names.items()))

    return header + body


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    files, names = build()
    files[LANG_FILE] = lang(names)
    files.update(block_recipes())

    owned = {MATERIALS_DIR, COMPONENTS_DIR, CRUSHER_DIR, SMELTER_DIR}

    if args.check:
        stale = [path for path, payload in files.items()
                 if not path.exists() or path.read_text(encoding="utf-8") != payload]

        # A renamed or dropped material otherwise leaves an orphan item with no recipe or icon.
        for folder in owned:
            if not folder.exists():
                continue
            stale += [path for path in folder.glob("*.json") if path not in files]

        if stale:
            print("Stale generated material assets:", file=sys.stderr)
            for path in sorted(set(stale)):
                print(f"  {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            print("Run: python scripts/generate-material-assets.py", file=sys.stderr)
            return 1

        print(f"Generated material assets are up to date ({len(files)} files, "
              f"{len(table.BLOCK_RECIPES)} of them block recipes).")
        return 0

    # Only the fully generated folders are cleared; the block definitions are edited in place.
    for folder in owned:
        if folder.exists():
            shutil.rmtree(folder)

    for path, payload in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(payload, encoding="utf-8")

    print(f"Wrote {len(files)} files: {len(names)} items, "
          f"{len(files) - len(names) - 1 - len(table.BLOCK_RECIPES)} machine recipes, "
          f"{len(table.BLOCK_RECIPES)} block recipes, 1 language file")
    return 0


if __name__ == "__main__":
    sys.exit(main())
