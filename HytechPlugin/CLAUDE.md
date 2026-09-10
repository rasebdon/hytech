# HytechPlugin

Hytech itself: the content built on `HytechCore`. Read the root `CLAUDE.md` first — it covers the
build, the logistics framework and the platform. This file is what only concerns this project.

## What lives here

- **Java (11 classes).** `HytechPlugin` plus `content.generators` (the generator and fuel-burner
  components, `GeneratorType`, the generation system, the burner's lit front, the generator page)
  and `content.storage` (the battery's fill states and its page). Everything else — the framework,
  the five resource types, the machine engine — comes from the library.
- **Assets.** Every real pipe, the batteries, the tanks, the generators, the crusher, the smelter,
  the twelve-metal material chain, all the recipes, the Tech Bench, and `server.lang` /
  `materials.lang`.

`content/HytechContentModule.java` is the piece worth reading: it registers this plugin's components
and systems through *this* plugin's `getChunkStoreRegistry()` while reading component types the
library registered through its own. That is the pattern any content mod follows.

## Adding a block

Most blocks need no Java at all — declare the library's components on the block and it works:

- a **battery or tank**: `hytech:<resource>:container`, plus `Hytech_OpenLogisticContainer` as the
  block's `Use` interaction for the generic page.
- a **machine**: `hytech:machine:processor` with a `RecipeGroup`, plus `hytech:items:container` with
  an `InputSlots`/`OutputSlots` split and `hytech:energy:container` for the buffer, plus recipe
  assets whose `BenchRequirement` is `{"Type": "Processing", "Id": "<that group>"}`.
- a **pipe**: see `HytechCore/CLAUDE.md`, which has the contract and the validation.

A block needs Java only when it does something the library has no engine for — which today means
generators, because `GeneratorType` is a closed enum.

## Materials and the Progression

The whole material chain is one table: `scripts/hytech_materials.py`. `generate-material-assets.py`
turns it into item definitions, recipes and a language file, and `generate-icons.py` draws it, so a
balance change is one edit rather than forty files.

```
vanilla ore --(crusher)--> 2 dust --(smelter)--> 1 vanilla bar --(bench)--> 1 plate
plate --> wire, coils, circuits, casings, frames --> machines
```

Anchored on vanilla throughout: Hytech crushes the game's own ores and smelts its dusts back into
the game's own `Ingredient_Bar_*`, so the two economies feed each other. Crushing first is what
doubles an ore — vanilla's furnace smelts ore 1:1 and that recipe is untouched. Twelve metals carry
a dust and a plate; **steel** is the one bar Hytech ships, because vanilla has none, and **bronze**
gets the recipe vanilla forgot to give it (its bar exists with no way to make it).

Alloys are smelted from two dusts, which is the whole reason the electric smelter has two ingredient
slots and Hytech needs no separate mixer.

Two things about generated assets:

- **The generated folders are owned outright.** `--check` fails on an orphan as well as on a stale
  file: a renamed material would otherwise leave a live item behind with no recipe and no icon.
- **Most ingredients wins.** `MachineRecipes` sorts each group by input count, descending. Iron dust
  alone smelts to a bar; iron dust *and* charcoal is steel, and a player who loaded both meant the
  alloy. Without the sort that choice fell out of asset iteration order.

**Everything Hytech is crafted at the Tech Bench** (`Hytech_Workbench`), an ordinary vanilla
`Bench` of type `Crafting` with four tabs — Materials, Components, Logistics, Machines. It needs no
code: a `Bench` block declaring `BenchBlock` in its `BlockEntity` is opened by the game's own bench
handling, and `Bench_WorkBench` itself declares no `Use` interaction either.

The bench is one of three exceptions to its own rule. It is built at the *vanilla* workbench out of
vanilla bars, which is what keeps the whole tree reachable from a fresh world. The **wrench** and
the **multimeter** are the other two, and for a different reason: they belong to `HytechCore`, and a
library cannot put its recipes on a bench a content mod owns — so they craft at the vanilla
workbench out of two vanilla bars each and are not in `BLOCK_RECIPES` at all. That makes them
reachable before the Tech Bench, which is a deliberate change from when they cost Hytech plates.

Everything else — plates, components, pipes, tanks, generators, machines — is on the Tech Bench,
including the blocks that had no recipe at all before (burner, solar panel, battery).

Player crafting hangs off each item's own `Recipe` block, so only machine recipes need to be
standalone assets. For hand-authored blocks the generator owns **only the `Recipe` key** and leaves
models, block states and components alone, which is what lets the crafting ladder live in the table
next to the materials. Plates are pressed at the bench for now; a dedicated press is a machine for
later.

**A translation key is prefixed with the file it came from.** `I18nModule.getPrefix` builds every
key as `<file name>.<key in file>`, folding in subdirectories — which is why `server.lang` holds
`items.X.name` and assets ask for `server.items.X.name`. Generated names live in `materials.lang`,
so those items ask for `materials.items.X.name`. Getting this wrong is silent: the client shows the
raw identifier and nothing is logged.

`check-asset-refs.py` has a second pass for this: every `ItemId` a recipe names must exist. That
failure is quiet in a way a missing texture is not — the recipe loads, validates, and then never
matches, so a machine just sits there.

