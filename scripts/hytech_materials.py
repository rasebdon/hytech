"""The Hytech material and component table.

One table, read by every generator that needs it: `generate-material-assets.py` turns it into item
definitions, recipes and language lines, and `generate-icons.py` draws it. Balance therefore lives
in exactly one readable place rather than spread across forty JSON files.

The chain, top to bottom:

    vanilla ore --(crusher)--> 2 dust --(smelter)--> 1 vanilla bar --(bench)--> 1 plate
    plate --> wire, coils, circuits, casings, frames --> machines and pipes

Crushing before smelting is what doubles an ore, since vanilla's own furnace smelts ore 1:1 and
that recipe is left alone. Alloys are smelted from two dusts, which is why the electric smelter has
two ingredient slots and Hytech needs no separate mixer.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# Mekanism-style tier names, used for circuits, frames, machines and (in a later phase) pipes.
TIERS = ["Basic", "Advanced", "Elite", "Ultimate", "Quantum"]

# Accent per tier, so a tier reads the same on a circuit icon as on a frame icon.
TIER_COLOURS = {
    "Basic": ((0x6E, 0x76, 0x82), (0x9C, 0xA6, 0xB4)),
    "Advanced": ((0x3E, 0x7A, 0x4A), (0x6D, 0xB0, 0x7C)),
    "Elite": ((0x2F, 0x5C, 0xA8), (0x5A, 0x8E, 0xD8)),
    "Ultimate": ((0xB0, 0x5C, 0xC6), (0xE0, 0x96, 0xF0)),
    "Quantum": ((0xC2, 0x4A, 0x12), (0xFF, 0xA5, 0x2B)),
}


@dataclass(frozen=True)
class Metal:
    """One metal in the chain.

    `ore` is None for an alloy, which has no ore to crush -- its dust comes from crushing the bar
    back down, and its bar from the alloy recipe. `bar` names a *vanilla* item wherever one exists,
    so Hytech feeds the game's own economy instead of shadowing it; steel is the one metal vanilla
    has no bar for, so Hytech ships that one item.
    """

    name: str
    ore: str | None
    bar: str
    body: tuple[int, int, int]
    highlight: tuple[int, int, int]
    smelt_seconds: float

    @property
    def dust(self) -> str:
        return f"Hytech_Dust_{self.name}"

    @property
    def plate(self) -> str:
        return f"Hytech_Plate_{self.name}"

    @property
    def owns_bar(self) -> bool:
        """Whether the bar is ours to define rather than vanilla's."""
        return self.bar.startswith("Hytech_")


# Smelt times follow vanilla's own furnace recipes where there is one, so a Hytech smelter is
# recognisably the same material taking the same effort.
METALS = [
    Metal("Copper", "Ore_Copper", "Ingredient_Bar_Copper", (0xB5, 0x6B, 0x38), (0xE2, 0x93, 0x5A), 6),
    Metal("Iron", "Ore_Iron", "Ingredient_Bar_Iron", (0x8E, 0x8E, 0x96), (0xBD, 0xBD, 0xC6), 8),
    Metal("Silver", "Ore_Silver", "Ingredient_Bar_Silver", (0xC2, 0xC8, 0xD2), (0xEC, 0xF0, 0xF6), 4),
    Metal("Gold", "Ore_Gold", "Ingredient_Bar_Gold", (0xD9, 0xA4, 0x2B), (0xF7, 0xD9, 0x6A), 6),
    Metal("Cobalt", "Ore_Cobalt", "Ingredient_Bar_Cobalt", (0x2F, 0x5C, 0xA8), (0x5A, 0x8E, 0xD8), 10),
    Metal("Thorium", "Ore_Thorium", "Ingredient_Bar_Thorium", (0x3E, 0x7A, 0x4A), (0x6D, 0xB0, 0x7C), 10),
    Metal("Mithril", "Ore_Mithril", "Ingredient_Bar_Mithril", (0x7A, 0xB8, 0xC6), (0xB4, 0xE4, 0xEE), 16),
    Metal("Adamantite", "Ore_Adamantite", "Ingredient_Bar_Adamantite", (0x8C, 0x2E, 0x3A), (0xC7, 0x5A, 0x66), 12),
    Metal("Onyxium", "Ore_Onyxium", "Ingredient_Bar_Onyxium", (0x3A, 0x33, 0x44), (0x6B, 0x5F, 0x7A), 4),
    Metal("Prisma", "Ore_Prisma", "Ingredient_Bar_Prisma", (0xB0, 0x5C, 0xC6), (0xE0, 0x96, 0xF0), 4),
    # Alloys. Vanilla ships a bronze bar with no recipe at all, so Hytech gives it one rather than
    # inventing a second bronze; steel it has to introduce outright.
    Metal("Bronze", None, "Ingredient_Bar_Bronze", (0xA2, 0x7B, 0x3C), (0xD0, 0xA5, 0x5E), 8),
    Metal("Steel", None, "Hytech_Bar_Steel", (0x6E, 0x76, 0x82), (0x9C, 0xA6, 0xB4), 12),
]

BY_NAME = {metal.name: metal for metal in METALS}


@dataclass(frozen=True)
class Alloy:
    """A bar smelted from two or more dusts, in the electric smelter's two ingredient slots."""

    metal: str
    inputs: list[tuple[str, int]]
    output_quantity: int
    seconds: float


ALLOYS = [
    # Iron plus carbon, the real recipe, using the charcoal a burner generator already produces.
    Alloy("Steel", [("Hytech_Dust_Iron", 1), ("Ingredient_Charcoal", 1)], 1, 12),
    # Copper hardened with silver. Hytale has no tin, and bronze needs *some* way to exist.
    Alloy("Bronze", [("Hytech_Dust_Copper", 3), ("Hytech_Dust_Silver", 1)], 4, 10),
]


@dataclass(frozen=True)
class Component:
    """A crafted part: wire, a coil, a circuit, a casing, a frame.

    `kind` picks the icon; `tier` is None for the parts that are not tiered. Recipes are player
    crafting rather than machine processing -- they hang off the item's own `Recipe` block, at the
    vanilla workbench, which is also where the machines themselves are built.
    """

    id: str
    name: str
    kind: str
    inputs: list[tuple[str, int]]
    output_quantity: int = 1
    seconds: float = 2
    tier: str | None = None
    metal: str | None = None
    item_level: int = 20


def _circuit(tier: str, inputs: list[tuple[str, int]], level: int) -> Component:
    return Component(
        id=f"Hytech_Circuit_{tier}",
        name=f"{tier} Circuit",
        kind="circuit",
        inputs=inputs,
        seconds=3,
        tier=tier,
        item_level=level,
    )


def _frame(tier: str, inputs: list[tuple[str, int]], level: int) -> Component:
    return Component(
        id=f"Hytech_Frame_{tier}",
        name=f"{tier} Machine Frame",
        kind="frame",
        inputs=inputs,
        seconds=4,
        tier=tier,
        item_level=level,
    )


COMPONENTS = [
    Component(
        id="Hytech_Wire_Copper",
        name="Copper Wire",
        kind="wire",
        inputs=[("Hytech_Plate_Copper", 1)],
        output_quantity=3,
        seconds=1,
        metal="Copper",
        item_level=12,
    ),
    Component(
        id="Hytech_Coil",
        name="Induction Coil",
        kind="coil",
        inputs=[("Hytech_Wire_Copper", 3), ("Hytech_Plate_Iron", 1)],
        seconds=2,
        metal="Iron",
        item_level=18,
    ),
    Component(
        id="Hytech_Casing",
        name="Machine Casing",
        kind="casing",
        inputs=[("Hytech_Plate_Iron", 4), ("Hytech_Plate_Steel", 2)],
        seconds=3,
        metal="Steel",
        item_level=24,
    ),
    # Circuits climb by adding the previous tier to a better plate, so a Quantum circuit carries
    # the whole ladder inside it and no tier can be skipped.
    _circuit("Basic", [("Hytech_Plate_Copper", 1), ("Hytech_Wire_Copper", 2)], 20),
    _circuit("Advanced", [("Hytech_Circuit_Basic", 1), ("Hytech_Plate_Silver", 1), ("Hytech_Coil", 1)], 30),
    _circuit("Elite", [("Hytech_Circuit_Advanced", 1), ("Hytech_Plate_Gold", 1), ("Hytech_Coil", 2)], 40),
    _circuit("Ultimate", [("Hytech_Circuit_Elite", 1), ("Hytech_Plate_Mithril", 1), ("Hytech_Plate_Prisma", 1)], 50),
    _circuit("Quantum", [("Hytech_Circuit_Ultimate", 1), ("Hytech_Plate_Adamantite", 1), ("Hytech_Plate_Onyxium", 1)], 60),
    # Frames do the same with the structural metals, so the two ladders never compete for a plate.
    _frame("Basic", [("Hytech_Casing", 1), ("Hytech_Plate_Steel", 4)], 24),
    _frame("Advanced", [("Hytech_Frame_Basic", 1), ("Hytech_Plate_Bronze", 4)], 34),
    _frame("Elite", [("Hytech_Frame_Advanced", 1), ("Hytech_Plate_Cobalt", 4)], 44),
    _frame("Ultimate", [("Hytech_Frame_Elite", 1), ("Hytech_Plate_Thorium", 4)], 54),
    _frame("Quantum", [("Hytech_Frame_Ultimate", 1), ("Hytech_Plate_Adamantite", 4)], 64),
]

# Every Hytech recipe is crafted at Hytech's own bench, so a player looks in one place rather than
# hunting four vanilla category tabs for parts that have nothing to do with them. The bench itself
# is the exception -- it is built at the vanilla workbench out of vanilla bars, which is what keeps
# the whole tree reachable from a fresh world.
BENCH_ID = "Hytech_Workbench"

CATEGORY_MATERIALS = "Hytech_Materials"
CATEGORY_COMPONENTS = "Hytech_Components"
CATEGORY_LOGISTICS = "Hytech_Logistics"
CATEGORY_MACHINES = "Hytech_Machines"

BENCH_CATEGORIES = [
    (CATEGORY_MATERIALS, "Materials", "Icons/CraftingCategories/Workbench/Processing.png"),
    (CATEGORY_COMPONENTS, "Components", "Icons/CraftingCategories/Workbench/Tools.png"),
    (CATEGORY_LOGISTICS, "Logistics", "Icons/CraftingCategories/Workbench/Deco_Target.png"),
    (CATEGORY_MACHINES, "Machines", "Icons/CraftingCategories/Workbench/WeaponsCrude.png"),
]

VANILLA_WORKBENCH = [{"Type": "Crafting", "Id": "Workbench", "Categories": ["Workbench_Crafting"]}]


def bench(category: str) -> list[dict]:
    """The bench requirement for a Hytech recipe, in one of the bench's own tabs."""
    return [{"Type": "Crafting", "Id": BENCH_ID, "Categories": [category]}]


@dataclass(frozen=True)
class BlockRecipe:
    """A recipe for a block that already has a hand-authored item definition.

    The generator owns only the `Recipe` key of these files, leaving their models, components and
    block states alone. Keeping the costs here rather than scattered across fifteen JSON files is
    the same argument as for the materials: the ladder should be readable in one place.
    """

    path: str
    category: str
    inputs: list[tuple[str, int]]
    output_quantity: int = 1
    seconds: float = 4


BLOCK_RECIPES = [
    # ---- tools: the first two things a player needs, so they cost plates and nothing rarer ----
    BlockRecipe("Wrench.json", CATEGORY_COMPONENTS, [("Hytech_Plate_Iron", 2)], seconds=2),
    BlockRecipe("Multimeter.json", CATEGORY_COMPONENTS,
                [("Hytech_Plate_Copper", 1), ("Hytech_Circuit_Basic", 1)], seconds=2),

    # ---- logistics: eight pipes a craft, since a run eats them by the dozen ----
    BlockRecipe("Pipes/Energy/Pipe_Energy.json", CATEGORY_LOGISTICS,
                [("Hytech_Plate_Copper", 6), ("Hytech_Wire_Copper", 2)], output_quantity=8),
    BlockRecipe("Pipes/Items/Pipe_Items.json", CATEGORY_LOGISTICS,
                [("Hytech_Plate_Iron", 6)], output_quantity=8),
    BlockRecipe("Pipes/Fluid/Pipe_Fluid.json", CATEGORY_LOGISTICS,
                [("Hytech_Plate_Bronze", 6)], output_quantity=8),
    BlockRecipe("Pipes/Gas/Pipe_Gas.json", CATEGORY_LOGISTICS,
                [("Hytech_Plate_Silver", 6)], output_quantity=8),
    BlockRecipe("Pipes/Heat/Pipe_Heat.json", CATEGORY_LOGISTICS,
                [("Hytech_Plate_Steel", 6)], output_quantity=8),

    BlockRecipe("Storage.Items/Item_Buffer.json", CATEGORY_LOGISTICS,
                [("Hytech_Casing", 1), ("Hytech_Plate_Iron", 4)]),
    BlockRecipe("Storage.Tanks/Fluid_Tank.json", CATEGORY_LOGISTICS,
                [("Hytech_Casing", 1), ("Hytech_Plate_Bronze", 4)]),
    BlockRecipe("Storage.Tanks/Gas_Tank.json", CATEGORY_LOGISTICS,
                [("Hytech_Casing", 1), ("Hytech_Plate_Silver", 4)]),
    BlockRecipe("Storage.Tanks/Heat_Tank.json", CATEGORY_LOGISTICS,
                [("Hytech_Casing", 1), ("Hytech_Plate_Steel", 4)]),
    BlockRecipe("Storage.Batteries/Battery_Tier_1.json", CATEGORY_LOGISTICS,
                [("Hytech_Casing", 1), ("Hytech_Wire_Copper", 4), ("Hytech_Circuit_Basic", 2)]),

    # ---- machines ----
    BlockRecipe("Generators/Burner_Generator.json", CATEGORY_MACHINES,
                [("Hytech_Casing", 1), ("Hytech_Plate_Copper", 2)]),
    BlockRecipe("Generators/Solar_Panel_Tier_1.json", CATEGORY_MACHINES,
                [("Hytech_Casing", 1), ("Hytech_Circuit_Basic", 1), ("Hytech_Plate_Silver", 2)]),
    BlockRecipe("Machines/Crusher_Basic.json", CATEGORY_MACHINES,
                [("Hytech_Frame_Basic", 1), ("Hytech_Circuit_Basic", 2), ("Hytech_Plate_Steel", 4)],
                seconds=6),
    BlockRecipe("Machines/Electric_Smelter_Basic.json", CATEGORY_MACHINES,
                [("Hytech_Frame_Basic", 1), ("Hytech_Circuit_Basic", 2), ("Hytech_Coil", 2)],
                seconds=6),

    # ---- the bench itself: vanilla bars only, so it is reachable before any of the above ----
    BlockRecipe("Hytech_Workbench.json", "Workbench_Crafting",
                [("Ingredient_Bar_Iron", 4), ("Ingredient_Bar_Copper", 2)], seconds=5),
]

# Creative-only test blocks keep whatever recipe they shipped with; they are a debugging aid, not
# part of the progression. See TESTING.md.

# The two machine recipe groups, matching the RecipeGroup on each machine's processor component.
CRUSHER_GROUP = "Hytech_Crusher"
SMELTER_GROUP = "Hytech_Smelter"


def display_name(item_id: str) -> str:
    """Player-facing name for a generated item id."""
    for metal in METALS:
        if item_id == metal.dust:
            return f"{metal.name} Dust"
        if item_id == metal.plate:
            return f"{metal.name} Plate"
        if item_id == metal.bar and metal.owns_bar:
            return f"{metal.name} Bar"

    for component in COMPONENTS:
        if item_id == component.id:
            return component.name

    raise KeyError(item_id)
