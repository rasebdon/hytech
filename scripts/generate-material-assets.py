#!/usr/bin/env python3
"""
Turns `hytech_materials.py` into every asset a metal needs: a tinted texture, an item definition,
a placeholder icon, a translation and the crusher/smelter recipes -- so adding a metal, a form or
a machine is an edit to that table rather than to forty files.

Each metal's colour is sampled from the game's own ingot texture, so a new vanilla metal needs
nothing but its name; a metal the game does not ship gives a `tint` instead and also gets an ingot
item of its own, built from the vanilla ingot model.

The three generated folders are owned outright: anything in them that this run did not write is
deleted, and `--check` fails on it, because a renamed metal would otherwise leave a live item
behind with no recipe. Icons share a folder with hand-made ones, so only icons whose name matches
a form's id pattern are pruned.

Usage:
    python scripts/generate-material-assets.py           # write assets
    python scripts/generate-material-assets.py --check   # fail if anything is stale
"""

from __future__ import annotations

import argparse
import colorsys
import json
import math
import os
import re
import statistics
import sys
import zipfile
from pathlib import Path
from typing import NamedTuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import hytech_materials as table  # noqa: E402  (deliberate: needs the sys.path line above)
import pnglib  # noqa: E402

from paths import CONTENT, REPO_ROOT, resources  # noqa: E402

RESOURCES = resources(CONTENT)

# Owned outright -- pruned to exactly what a run writes.
TEXTURE_DIR = RESOURCES / "Common/Items/Materials/Generated"
ITEM_DIR = RESOURCES / "Server/Item/Items/Materials/Generated"
RECIPE_DIR = RESOURCES / "Server/Item/Recipes/Hytech/Generated"
FLUID_DIR = RESOURCES / "Server/Item/ResourceTypes/Generated"
FLUID_ICON_DIR = RESOURCES / "Common/Icons/ResourceTypes"

# Shared with hand-made icons; pruned by id pattern only.
ICON_DIR = RESOURCES / "Common/Icons/ItemsGenerated"

LANGUAGE_FILE = RESOURCES / f"Server/Languages/en-US/{table.LANGUAGE_FILE}.lang"

ICON_SIZE = 64

# A tint keeps the source pixel's shading and takes the metal's hue. Lightness is re-centred on the
# metal's own -- onyxium dust has to come out dark and mithril bright -- but compressed towards the
# middle, since a texture at either extreme reads as a black or white smudge in an inventory.
LIGHT_FLOOR = 0.35
LIGHT_RANGE = 0.50
SATURATION_SCALE = 1.0
SATURATION_CEILING = 0.85


class Source(NamedTuple):
    """An authored texture, ready to tint: which pixels may be touched, and how light they are."""

    width: int
    height: int
    pixels: pnglib.Pixels
    offsets: list[int]
    lightness: float


def game_archive() -> zipfile.ZipFile:
    appdata = os.environ.get("APPDATA")
    if not appdata:
        home = os.environ.get("HOME", "")
        appdata = f"{home}/.var/app/com.hypixel.HytaleLauncher/data"

    archive = Path(appdata) / "Hytale/install/release/package/game/latest/Assets.zip"
    if not archive.exists():
        raise SystemExit(f"error: {archive} not found; the metal colours are sampled from it")

    return zipfile.ZipFile(archive)


def vanilla_item_ids(archive: zipfile.ZipFile) -> set[str]:
    """An item's id is its file name, the way `AssetBuilderCodec` keys the store."""
    return {
        name.split("/")[-1][:-5]
        for name in archive.namelist()
        if name.startswith("Server/Item/Items/") and name.endswith(".json")
    }


def read_texture(reference: str, archive: zipfile.ZipFile) -> tuple[int, int, pnglib.Pixels]:
    if reference.startswith(table.GAME):
        name = reference[len(table.GAME):]
        return pnglib.decode(archive.read(name), name)

    path = RESOURCES / reference
    if not path.exists():
        raise SystemExit(f"error: {reference} does not exist; a form's tint source must be authored")

    return pnglib.decode_file(path)


def pixels_hls(pixels: pnglib.Pixels) -> list[tuple[float, float, float]]:
    out = []
    for i in range(0, len(pixels), 4):
        r, g, b, a = pixels[i:i + 4]
        if a < 128:
            continue
        hue, light, sat = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        out.append((hue, light, sat))
    return out


def sample_palette(pixels: pnglib.Pixels) -> tuple[float, float, float]:
    """(hue, saturation, lightness) of a texture, as the tint to apply elsewhere.

    Hue is averaged on the circle and weighted by saturation, because a metal's identity sits in
    its few most colourful pixels while a median over all of them lands on whatever grey the
    shading uses. Iron and silver come out with a saturation near zero, which is the right answer:
    their dust stays grey.
    """
    samples = pixels_hls(pixels)
    if not samples:
        raise SystemExit("error: tint source has no opaque pixels")

    x = sum(sat * math.cos(hue * 2 * math.pi) for hue, _, sat in samples)
    y = sum(sat * math.sin(hue * 2 * math.pi) for hue, _, sat in samples)
    hue = (math.atan2(y, x) / (2 * math.pi)) % 1.0

    saturation = statistics.median(sat for _, _, sat in samples)
    lightness = statistics.median(light for _, light, _ in samples)
    return hue, saturation, lightness


def tintable(pixels: pnglib.Pixels, mask: pnglib.Pixels | None) -> list[int]:
    """Byte offsets of the pixels a tint may touch: opaque here, and absent from the mask. The
    mask is what keeps a bucket wooden -- its liquid is exactly what the empty texture lacks."""
    out = []
    for i in range(0, len(pixels), 4):
        if pixels[i + 3] == 0:
            continue
        if mask is not None and mask[i + 3] != 0:
            continue
        out.append(i)
    return out


def tint(pixels: pnglib.Pixels, palette: tuple[float, float, float], source_lightness: float,
         offsets: list[int], form: table.Form) -> pnglib.Pixels:
    hue, saturation, lightness = palette

    floor = form.light_floor if form.light_floor is not None else LIGHT_FLOOR
    span = form.light_range if form.light_range is not None else LIGHT_RANGE
    scale = form.saturation_scale if form.saturation_scale is not None else SATURATION_SCALE

    target = floor + span * lightness
    saturation = min(saturation * scale, SATURATION_CEILING)

    out = bytearray(pixels)
    for i in offsets:
        _, light, _ = colorsys.rgb_to_hls(out[i] / 255, out[i + 1] / 255, out[i + 2] / 255)
        shifted = min(0.95, max(0.05, target + (light - source_lightness)))
        r, g, b = colorsys.hls_to_rgb(hue, shifted, saturation)
        out[i:i + 3] = bytes((round(r * 255), round(g * 255), round(b * 255)))

    return out


# --- Placeholder icons -----------------------------------------------------------------------
#
# A missing `Icon` is a fatal validation error and the game's own renderer only fills
# `Icons/ItemsGenerated/` in after that check (then `syncAssets` copies the real ones back), so
# every generated item needs something committed up front. These only have to be told apart.

def icon_colours(palette: tuple[float, float, float]) -> tuple[tuple, tuple, tuple]:
    hue, saturation, lightness = palette
    saturation = min(saturation * SATURATION_SCALE, SATURATION_CEILING)
    body = LIGHT_FLOOR + LIGHT_RANGE * lightness

    def rgba(light: float) -> tuple[int, int, int, int]:
        r, g, b = colorsys.hls_to_rgb(hue, min(0.95, max(0.05, light)), saturation)
        return round(r * 255), round(g * 255), round(b * 255), 255

    return rgba(body), rgba(body + 0.18), rgba(body - 0.22)


def draw_fluid_icon(palette: tuple[float, float, float]) -> bytes:
    """A resource type's icon is not redrawn by the game the way an item's is, so this one is the
    real thing: a droplet in the metal's colour."""
    body, highlight, shadow = icon_colours(palette)
    pixels = pnglib.blank(ICON_SIZE, ICON_SIZE)

    rows = [(20, 30, 4), (24, 34, 7), (28, 38, 10), (32, 46, 13), (40, 52, 15), (48, 56, 13)]
    for y0, y1, half in rows:
        pnglib.fill(pixels, ICON_SIZE, (32 - half, y0, 32 + half, y1), body)

    pnglib.fill(pixels, ICON_SIZE, (26, 34, 30, 42), highlight)
    pnglib.fill(pixels, ICON_SIZE, (24, 50, 40, 56), shadow)
    return pnglib.encode(ICON_SIZE, ICON_SIZE, pixels)


def draw_icon(form: table.Form, palette: tuple[float, float, float]) -> bytes:
    body, highlight, shadow = icon_colours(palette)
    pixels = pnglib.blank(ICON_SIZE, ICON_SIZE)

    if form.fluid:
        # A pail of it: staves in wood, contents in the metal's colour.
        wood, wood_light, wood_dark = (99, 68, 44, 255), (128, 92, 62, 255), (68, 46, 30, 255)
        pnglib.fill(pixels, ICON_SIZE, (14, 20, 50, 26), wood_light)
        pnglib.fill(pixels, ICON_SIZE, (17, 26, 47, 30), body)
        pnglib.fill(pixels, ICON_SIZE, (22, 26, 30, 30), highlight)
        pnglib.fill(pixels, ICON_SIZE, (17, 30, 47, 50), wood)
        pnglib.fill(pixels, ICON_SIZE, (17, 36, 47, 40), wood_dark)
        pnglib.fill(pixels, ICON_SIZE, (20, 50, 44, 54), shadow)
    elif form.key == "ingot":
        rows = [(20, 26, 14, 50), (26, 32, 12, 52), (32, 44, 10, 54)]
        for y0, y1, x0, x1 in rows:
            pnglib.fill(pixels, ICON_SIZE, (x0, y0, x1, y1), body)
        pnglib.fill(pixels, ICON_SIZE, (14, 20, 50, 26), highlight)
        pnglib.fill(pixels, ICON_SIZE, (10, 40, 54, 44), shadow)
    else:
        # A heap: three overlapping mounds, lit from above.
        mounds = [(12, 36, 34, 52), (24, 44, 26, 52), (34, 52, 32, 52)]
        for x0, x1, y0, y1 in mounds:
            pnglib.fill(pixels, ICON_SIZE, (x0, y0, x1, y1), body)
        pnglib.fill(pixels, ICON_SIZE, (14, 34, 34, 38), highlight)
        pnglib.fill(pixels, ICON_SIZE, (26, 28, 30, 32), highlight)
        pnglib.fill(pixels, ICON_SIZE, (12, 48, 52, 52), shadow)

    return pnglib.encode(ICON_SIZE, ICON_SIZE, pixels)


# --- Recipes ---------------------------------------------------------------------------------

def recipe_entries(refs: list[tuple[str, int]], resolved: dict[str, str]) -> list[dict] | None:
    """None when a form this metal has no item for is named -- bronze has no ore, so the recipe
    that would crush one is not written rather than written and never matched."""
    out = []
    for key, quantity in refs:
        if key in table.BY_KEY:
            item = resolved.get(key)
            if item is None:
                return None
        else:
            item = key  # a literal vanilla item id, the same for every metal
        out.append({"ItemId": item, "Quantity": quantity})
    return out


def recipe_json(process: table.Process, inputs: list[dict], outputs: list[dict]) -> dict:
    return {
        "Input": inputs,
        "PrimaryOutput": outputs[0],
        "Output": outputs,
        "BenchRequirement": [{"Type": "Processing", "Id": process.bench}],
        "TimeSeconds": process.seconds,
    }


def recipe_id(process: table.Process, outputs: list[dict]) -> str:
    """Named for what it makes, so two machines making the same item still differ."""
    return f"Hytech_{process.machine}_{outputs[0]['ItemId'].removeprefix('Hytech_')}"


# --- Build -----------------------------------------------------------------------------------

def build() -> tuple[dict[Path, bytes], list[str]]:
    """Every file this table implies, as path -> bytes, plus a one-line summary per metal."""
    archive = game_archive()
    vanilla = vanilla_item_ids(archive)

    files: dict[Path, bytes] = {}
    notes: list[str] = []
    names: dict[str, str] = {}

    sources: dict[str, Source] = {}
    for form in table.FORMS:
        if not form.tint_source:
            continue

        width, height, pixels = read_texture(form.tint_source, archive)
        mask = read_texture(form.tint_mask, archive)[2] if form.tint_mask else None
        offsets = tintable(pixels, mask)

        if not offsets:
            raise SystemExit(f"error: {form.key}'s tint source has nothing left to tint")

        # Measured over the tintable pixels alone: a bucket's median is its wood otherwise, and
        # every molten metal would come out shifted by the same wrong amount.
        lightness = statistics.median(
            colorsys.rgb_to_hls(pixels[i] / 255, pixels[i + 1] / 255, pixels[i + 2] / 255)[1]
            for i in offsets)

        sources[form.key] = Source(width, height, pixels, offsets, lightness)

    ingot_textures = {
        name.split("/")[-1][:-4]
        for name in archive.namelist()
        if name.startswith("Common/Resources/Materials/Ingot_Textures/")
    }

    for material in table.MATERIALS:
        palette = material_palette(material, archive, ingot_textures)
        resolved: dict[str, str] = {}
        generated: list[str] = []
        fluids: list[str] = []

        for form in table.FORMS:
            item_id = form.resolve(material, vanilla)
            if item_id is None:
                continue
            resolved[form.key] = item_id

            if form.vanilla and item_id == form.vanilla.format(material=material.name):
                continue  # the game already ships this one

            generated.append(item_id)
            source = sources[form.key]
            texture = f"Items/Materials/Generated/{item_id}.png"
            icon = f"Icons/ItemsGenerated/{item_id}.png"

            files[TEXTURE_DIR / f"{item_id}.png"] = pnglib.encode(
                source.width, source.height,
                tint(source.pixels, palette, source.lightness, source.offsets, form))
            files[ICON_DIR / f"{item_id}.png"] = draw_icon(form, palette)
            files[ITEM_DIR / f"{item_id}.json"] = json_bytes(
                table.item_json(material, form, item_id, texture, icon))
            names[item_id] = form.name_for(material)

        for fluid in table.FLUIDS:
            fluid_id = fluid.id_for(material)
            icon = f"Icons/ResourceTypes/{fluid_id}.png"

            files[FLUID_DIR / f"{fluid_id}.json"] = json_bytes(table.resource_type_json(icon))
            files[FLUID_ICON_DIR / f"{fluid_id}.png"] = draw_fluid_icon(palette)
            fluids.append(fluid_id)

        recipes = 0
        for process in table.PROCESSES:
            if not process.applies_to(material):
                continue
            inputs = recipe_entries(process.inputs, resolved)
            outputs = recipe_entries(process.outputs, resolved)
            if inputs is None or outputs is None:
                continue

            name = recipe_id(process, outputs)
            files[RECIPE_DIR / process.machine / f"{name}.json"] = json_bytes(
                recipe_json(process, inputs, outputs))
            recipes += 1

        notes.append(f"{material.name:11s} items={len(generated):2d}  fluids={len(fluids):2d}  "
                     f"recipes={recipes}  {', '.join(generated) or '(vanilla only)'}")

    files[LANGUAGE_FILE] = text_bytes("".join(
        f"items.{item_id}.name={name}\n" for item_id, name in sorted(names.items())
    ))

    return files, notes


def material_palette(material: table.Material, archive: zipfile.ZipFile,
                     ingot_textures: set[str]) -> tuple[float, float, float]:
    if material.tint:
        return hex_palette(material.tint)

    if material.name not in ingot_textures:
        raise SystemExit(
            f"error: {material.name} has no vanilla ingot texture to sample; give it a `tint`")

    _, _, pixels = pnglib.decode(
        archive.read(f"Common/Resources/Materials/Ingot_Textures/{material.name}.png"),
        material.name)
    return sample_palette(pixels)


def hex_palette(value: str) -> tuple[float, float, float]:
    digits = value.lstrip("#")
    r, g, b = (int(digits[i:i + 2], 16) / 255 for i in (0, 2, 4))
    hue, light, sat = colorsys.rgb_to_hls(r, g, b)
    return hue, sat, light


def json_bytes(payload: dict) -> bytes:
    return text_bytes(json.dumps(payload, indent=2) + "\n")


def text_bytes(text: str) -> bytes:
    """Platform line endings, the way `Path.write_text` gives them: the repo is `text=auto`
    with `core.autocrlf`, so a checkout here is CRLF and spotless fails an LF file."""
    return text.replace("\n", os.linesep).encode("utf-8")


# --- Pruning ---------------------------------------------------------------------------------

def owned(files: dict[Path, bytes]) -> list[Path]:
    """Files on disk this table no longer accounts for."""
    stale = []

    for directory in (TEXTURE_DIR, ITEM_DIR, RECIPE_DIR, FLUID_DIR, FLUID_ICON_DIR):
        if directory.exists():
            stale.extend(path for path in directory.rglob("*") if path.is_file()
                         and path not in files)

    if ICON_DIR.exists():
        pattern = icon_pattern()
        stale.extend(path for path in ICON_DIR.glob("*.png")
                     if pattern.fullmatch(path.stem) and path not in files)

    return sorted(stale)


def icon_pattern() -> re.Pattern:
    """Which icon names belong to this generator: a form's id template with the metal wild."""
    alternatives = [
        re.escape(form.item_id).replace(re.escape("{material}"), r"\w+")
        for form in table.FORMS if form.item_id
    ]
    return re.compile("|".join(alternatives))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="verify the generated assets are up to date instead of writing them")
    args = parser.parse_args()

    files, notes = build()
    stale = owned(files)

    if args.check:
        outdated = [path for path, payload in sorted(files.items())
                    if not path.exists() or path.read_bytes() != payload]
        if outdated or stale:
            print("Stale generated material assets:", file=sys.stderr)
            for path in outdated:
                print(f"  {display(path)}", file=sys.stderr)
            for path in stale:
                print(f"  {display(path)} (orphan)", file=sys.stderr)
            print("Run: python scripts/generate-material-assets.py", file=sys.stderr)
            return 1

        print(f"Generated material assets are up to date ({len(files)} files).")
        return 0

    for path in stale:
        path.unlink()
        print(f"removed {display(path)}")

    written = 0
    for path, payload in sorted(files.items()):
        if path.exists() and path.read_bytes() == payload:
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        written += 1

    for note in notes:
        print(note)
    print(f"{len(files)} files ({written} written, {len(files) - written} already current)")
    return 0


def display(path: Path) -> str:
    try:
        return path.relative_to(REPO_ROOT).as_posix()
    except ValueError:
        return path.as_posix()


if __name__ == "__main__":
    sys.exit(main())
