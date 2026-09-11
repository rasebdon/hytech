"""The Hytech material table: one source of content and balance, read by every generator.

    vanilla ingot --(crusher)--> dirty dust --(smelter)--> vanilla ingot

Crushing an ingot loses metal (3 in, 2 out) and smelting a dust gives it back 1:1, so recycling is
a real recycling loss rather than a duplication bug. Ore doubling, if it comes, is one `Process`
entry against the `ore` form.

Three tables and nothing else:

- `MATERIALS` -- one line per metal. A metal whose vanilla item exists (`Ingredient_Bar_Iron`)
  reuses it; one that has no vanilla counterpart gets an item generated from the vanilla ingot
  model with its own tint, so **adding a custom metal is a single line here**. Its colour is
  sampled from the vanilla ingot texture unless `tint` says otherwise, which is also what a custom
  metal has to give (there is no vanilla texture to sample).
- `FORMS` -- what a metal can exist *as*. A form owns an id, a display name, a model, the greyscale
  texture that gets tinted per metal, and whatever extra item JSON it needs. A form that names a
  `vanilla` id resolves to the game's own item wherever the game has one and generates its own
  otherwise. Adding a form (plate, gear, clean dust) is one entry plus a `.blockymodel`.
- `PROCESSES` -- what a machine turns into what, in forms rather than item ids, so one line covers
  every metal. A process is skipped for a metal that cannot resolve one of its forms (no vanilla
  ore for bronze, say) rather than producing a recipe that can never match.

- `FLUIDS` -- the molten (and later gaseous) forms of a metal. A fluid is not an item: it is the
  bare string a Hytech tank stores, registered with the game as a `ResourceType` so it exists as a
  named thing rather than only as whatever a tank happens to hold. A `Form` naming one becomes a
  carried measure of it -- a bucket -- and pours into a tank on right-click.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# Where a tint source lives: our own authored art, or a file inside the game's Assets.zip.
GAME = "game:"


@dataclass(frozen=True)
class Material:
    """One metal. `tint` overrides the colour sampled from the vanilla ingot texture, and is
    required for a metal the game does not ship (there is nothing to sample)."""

    name: str
    tint: str | None = None
    item_level: int = 10

    @property
    def key(self) -> str:
        return self.name


@dataclass(frozen=True)
class Form:
    """One shape a metal takes. `vanilla` names the game's own item for this form, if any: where it
    exists the form resolves to it and nothing is generated, and where it does not, `item_id` is
    generated from `model` + a tint of `tint_source`. `item_id = None` makes the form resolve-only
    -- it can be named by a recipe but Hytech will never create it (ores, which are blocks)."""

    key: str
    display: str
    model: str
    tint_source: str
    item_id: str | None = None
    vanilla: str | None = None
    max_stack: int = 100
    icon_scale: float = 1.0
    icon_translation: tuple[float, float] = (0.6, -9.6)
    icon_rotation: tuple[float, float, float] = (22.5, 45.0, 22.5)
    extra: dict = field(default_factory=dict)

    # Tint only the pixels this texture leaves transparent. A bucket is mostly wood: the liquid is
    # exactly what the full texture adds to the empty one, so the empty one is the mask.
    tint_mask: str | None = None

    # Per-form overrides of the generator's tint constants, for a form that should read hotter or
    # duller than a lump of the same metal.
    light_floor: float | None = None
    light_range: float | None = None
    saturation_scale: float | None = None

    # A form that carries a fluid: which one, how much of it, and what is left in the hand.
    fluid: str | None = None
    fluid_amount: int = 0
    empty_item: str | None = None

    def resolve(self, material: Material, vanilla_items: set[str]) -> str | None:
        """The item id this form has for this metal, or None when it has none."""
        if self.vanilla:
            candidate = self.vanilla.format(material=material.name)
            if candidate in vanilla_items:
                return candidate
        return self.generated_id(material)

    def generated_id(self, material: Material) -> str | None:
        if self.item_id is None:
            return None
        return self.item_id.format(material=material.name)

    def name_for(self, material: Material) -> str:
        return self.display.format(material=material.name)


@dataclass(frozen=True)
class Fluid:
    """A resource id a Hytech tank can hold. Registered with the game as a `ResourceType` so the
    fluid is a named asset rather than a string that only exists once something holds it."""

    key: str
    id: str
    display: str

    def id_for(self, material: Material) -> str:
        return self.id.format(material=material.name)

    def name_for(self, material: Material) -> str:
        return self.display.format(material=material.name)


@dataclass(frozen=True)
class Process:
    """One machine recipe, written in forms. Quantities are per operation; a `"Ingredient_Charcoal"`
    style literal is any vanilla item id, used as-is for every metal."""

    machine: str
    inputs: list[tuple[str, int]]
    outputs: list[tuple[str, int]]
    seconds: float
    materials: list[str] | None = None

    @property
    def bench(self) -> str:
        return f"Hytech_{self.machine}"

    def applies_to(self, material: Material) -> bool:
        return self.materials is None or material.name in self.materials


# --- Metals ----------------------------------------------------------------------------------
#
# The eleven the game ships a bar for. A custom metal is the same line plus a `tint`:
#
# #
# which has no `Ingredient_Bar_Titanium`, so the ingot form generates `Hytech_Ingot_Titanium` from
# the vanilla ingot model and every process below covers it with no further edit.

MATERIALS = [
    Material("Copper"),
    Material("Iron"),
    Material("Silver"),
    Material("Gold"),
    Material("Bronze"),
    Material("Cobalt", item_level=20),
    Material("Thorium", item_level=20),
    Material("Mithril", item_level=30),
    Material("Adamantite", item_level=30),
    Material("Onyxium", item_level=30),
    Material("Prisma", item_level=30),
]

BY_NAME = {material.name: material for material in MATERIALS}


# --- Forms -----------------------------------------------------------------------------------
#
# `Ingot` uses the game's own ingot model and, for a custom metal, a tint of the game's iron ingot
# texture -- which is flat grey, so it takes a hue cleanly. `Dirty_Dust` uses our own model and the
# greyscale art next to it.

INGOT = Form(
    key="ingot",
    display="{material} Ingot",
    model="Resources/Materials/Ingot.blockymodel",
    tint_source=GAME + "Common/Resources/Materials/Ingot_Textures/Iron.png",
    item_id="Hytech_Ingot_{material}",
    vanilla="Ingredient_Bar_{material}",
    icon_scale=1.0,
    icon_translation=(0.0, -3.0),
    extra={
        "ResourceTypes": [{"Id": "Metal_Bars"}],
        "ItemSoundSetId": "ISS_Items_Ingots",
    },
)

DIRTY_DUST = Form(
    key="dirty_dust",
    display="Dirty {material} Dust",
    model="Items/Materials/Dirty_Dust.blockymodel",
    tint_source="Common/Items/Materials/Dirty_Dust.png",
    item_id="Hytech_Dirty_Dust_{material}",
)

# --- Fluids ----------------------------------------------------------------------------------

MOLTEN = Fluid(key="molten", id="Molten_{material}", display="Molten {material}")

FLUIDS = [MOLTEN]

BY_FLUID = {fluid.key: fluid for fluid in FLUIDS}

# A bucket is the fluid made carryable: the vanilla full-bucket model, the vanilla bucket texture
# with only its water recoloured, and the library's `Hytech_FluidBucket` interaction pouring it
# into whatever tank it is right-clicked on. Molten metal reads hotter than a dust of it, hence
# the brighter floor and the fuller saturation.
MOLTEN_BUCKET = Form(
    key="molten_bucket",
    display="Bucket of Molten {material}",
    model="Blocks/Miscellaneous/Bucket_Full.blockymodel",
    tint_source=GAME + "Common/Blocks/Miscellaneous/Bucket_Texture_Water.png",
    tint_mask=GAME + "Common/Blocks/Miscellaneous/Bucket_Texture.png",
    item_id="Hytech_Bucket_Molten_{material}",
    max_stack=1,
    icon_scale=0.58823,
    icon_translation=(-3.0, -18.0),
    icon_rotation=(0.0, 121.0, 25.0),  # vanilla's own bucket angle
    light_floor=0.45,
    light_range=0.35,
    saturation_scale=1.35,
    fluid="molten",
    fluid_amount=1000,
    empty_item="Container_Bucket",
    extra={"ItemSoundSetId": "ISS_Blocks_Wood"},
)

# Resolve-only: a recipe may name an ore, but an ore is a block and Hytech generates none.
ORE = Form(
    key="ore",
    display="{material} Ore",
    model="",
    tint_source="",
    item_id=None,
    vanilla="Ore_{material}",
)

FORMS = [INGOT, DIRTY_DUST, MOLTEN_BUCKET, ORE]

BY_KEY = {form.key: form for form in FORMS}


# --- Processing ------------------------------------------------------------------------------
#
# Ore doubling, once Hytech has ores of its own to go with the game's, is:
#
#     Process("Crusher", [("ore", 1)], [("dirty_dust", 2)], seconds=4),

PROCESSES = [
    Process("Crusher", [("ingot", 3)], [("dirty_dust", 2)], seconds=4),
    Process("Smelter", [("dirty_dust", 1)], [("ingot", 1)], seconds=8),
]


# --- Item JSON -------------------------------------------------------------------------------

CATEGORY = "Technic.Materials"

# Generated names live in materials.lang, so assets ask for them as `materials.items.<id>.name`.
LANGUAGE_FILE = "materials"


# Registered with the game so `ResourceTypes` on an item validates, and so a later recipe can name
# a fluid the way vanilla names `Metal_Bars`.
def resource_type_json(icon: str) -> dict:
    return {"Icon": icon}


def item_json(material: Material, form: Form, item_id: str, texture: str, icon: str) -> dict:
    payload = {
        "TranslationProperties": {"Name": f"{LANGUAGE_FILE}.items.{item_id}.name"},
        "Categories": [CATEGORY],
        "ItemLevel": material.item_level,
        "MaxStack": form.max_stack,
        "Model": form.model,
        "Texture": texture,
        "Icon": icon,
        "PlayerAnimationsId": "Item",
        "IconProperties": {
            "Scale": form.icon_scale,
            "Translation": list(form.icon_translation),
            "Rotation": list(form.icon_rotation),
        },
        "Tags": {"Type": ["Ingredient"]},
        "DropOnDeath": True,
    }
    if form.fluid:
        fluid = BY_FLUID[form.fluid].id_for(material)

        payload["ResourceTypes"] = [{"Id": fluid, "Quantity": form.fluid_amount}]
        payload["Interactions"] = {
            "Secondary": {
                "Interactions": [{
                    "Type": "Hytech_FluidBucket",
                    "Resource": fluid,
                    "Amount": form.fluid_amount,
                    "EmptyItem": form.empty_item,
                }]
            }
        }

    payload.update(form.extra)
    return payload
