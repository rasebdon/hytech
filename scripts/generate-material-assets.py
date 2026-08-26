#!/usr/bin/env python3
"""
Generates the material and component assets from the table in `hytech_materials.py`.

Forty-odd items, each with an item definition, a recipe and a language line, all following the same
few shapes. Written by a script rather than by hand so the progression is reviewable as a table and
a balance change is one edit instead of forty.

What it writes:

    Server/Item/Items/Materials/*.json     dusts, plates and the steel bar
    Server/Item/Items/Components/*.json    wire, coils, circuits, casings, frames
    Server/Item/Recipes/Hytech/Crusher/    ore -> dust, bar -> dust
    Server/Item/Recipes/Hytech/Smelter/    dust -> bar, ore -> bar, and the alloys
    Server/Languages/en-US/materials.lang  every generated item's name

Player crafting hangs off each item's own `Recipe` block; only the machine recipes are standalone
assets, because only they need a Hytech bench id. That includes the hand-authored blocks -- pipes,
tanks, generators, the machines themselves -- whose `Recipe` key this script owns and whose every
other key it leaves alone, so the whole crafting ladder is reviewable in one table.

Names live in `materials.lang`, and the key a translation is looked up by is
`<file name>.<key in file>` -- `I18nModule.getPrefix` builds it that way. So a name in
`materials.lang` is `materials.items.X.name`, not `server.items.X.name`, and the item definitions
this script writes ask for exactly that.

Icons are `generate-icons.py`'s job -- and they are not optional: a missing `Icon` is a fatal
validation error for that item.

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

REPO_ROOT = Path(__file__).resolve().parent.parent
RESOURCES = REPO_ROOT / "src" / "main" / "resources"

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
    """The shape every generated item shares."""
    definition = {
        # `materials.` rather than `server.`: a language key is prefixed with the file it came
        # from, and these names live in materials.lang.
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
    """A player crafting recipe on the item's own definition. Output is the item itself."""
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
    """A standalone recipe asset for one of the Hytech machines."""
    return {
        "Input": [{"ItemId": ingredient, "Quantity": count} for ingredient, count in inputs],
        "PrimaryOutput": {"ItemId": output_id, "Quantity": quantity},
        "Output": [{"ItemId": output_id, "Quantity": quantity}],
        "BenchRequirement": [{"Type": "Processing", "Id": group}],
        "TimeSeconds": seconds,
    }


def build() -> tuple[dict[Path, str], dict[str, str]]:
    """Every file to write, and every language line, keyed so `--check` can compare."""
    files: dict[Path, str] = {}
    names: dict[str, str] = {}

    def write(path: Path, payload: dict) -> None:
        files[path] = json.dumps(payload, indent=2) + "\n"

    for metal in table.METALS:
        # ---- dust: crushed ore, or a crushed bar for an alloy ----
        write(MATERIALS_DIR / f"{metal.dust}.json",
              item(metal.dust, "Technic.Materials", 10, POUCH_MODEL, POUCH_TEXTURE,
                   None, 0.7, [0.6, -9.6]))
        names[metal.dust] = f"{metal.name} Dust"

        # ---- plate: pressed from a bar at the bench, until a press machine exists ----
        write(MATERIALS_DIR / f"{metal.plate}.json",
              item(metal.plate, "Technic.Materials", 14, INGOT_MODEL, INGOT_TEXTURE,
                   bench_recipe([(metal.bar, 1)], 1, 2, table.CATEGORY_MATERIALS),
                   1, [0, -3]))
        names[metal.plate] = f"{metal.name} Plate"

        # ---- the one bar vanilla does not have ----
        if metal.owns_bar:
            write(MATERIALS_DIR / f"{metal.bar}.json",
                  item(metal.bar, "Technic.Materials", 16, INGOT_MODEL, INGOT_TEXTURE,
                       None, 1, [0, -3]))
            names[metal.bar] = f"{metal.name} Bar"

        # ---- crusher: ore doubles into dust ----
        if metal.ore is not None:
            write(CRUSHER_DIR / f"Hytech_Crush_Ore_{metal.name}.json",
                  machine_recipe([(metal.ore, 1)], metal.dust, 2, table.CRUSHER_GROUP, 4))

            # The slow path: a smelter alone still gets you a bar, at vanilla's 1:1, so the
            # crusher is a choice about yield rather than the only way through.
            write(SMELTER_DIR / f"Hytech_Smelt_Ore_{metal.name}.json",
                  machine_recipe([(metal.ore, 1)], metal.bar, 1, table.SMELTER_GROUP,
                                 metal.smelt_seconds + 4))

        # ---- crusher: a bar back down to dust, which is how an alloy gets a dust at all ----
        write(CRUSHER_DIR / f"Hytech_Crush_Bar_{metal.name}.json",
              machine_recipe([(metal.bar, 1)], metal.dust, 1, table.CRUSHER_GROUP, 4))

        # ---- smelter: dust to bar ----
        write(SMELTER_DIR / f"Hytech_Smelt_Dust_{metal.name}.json",
              machine_recipe([(metal.dust, 1)], metal.bar, 1, table.SMELTER_GROUP,
                             metal.smelt_seconds))

    # ---- smelter: alloys, in the two ingredient slots ----
    for alloy in table.ALLOYS:
        metal = table.BY_NAME[alloy.metal]
        write(SMELTER_DIR / f"Hytech_Alloy_{alloy.metal}.json",
              machine_recipe(alloy.inputs, metal.bar, alloy.output_quantity,
                             table.SMELTER_GROUP, alloy.seconds))

    # ---- components ----
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
    """The hand-authored block definitions, with only their `Recipe` key rewritten.

    Read-modify-write rather than generate: these files carry models, block states and component
    configuration that no table should own. Only the recipe comes from here.
    """
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

        # A renamed or dropped material leaves an orphan behind, and an orphan item is a live
        # item in the game with no recipe and no icon. The generated folders are ours entirely,
        # so anything in them we did not just write is stale.
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
