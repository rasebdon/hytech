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

The material chain is one table: `scripts/hytech_materials.py`. `generate-material-assets.py`
turns it into tinted textures, item definitions, placeholder icons, recipes and a language file, so
a new metal is one line rather than forty files.

```
vanilla ingot --(crusher: 3 in)--> 2 dirty dust --(smelter)--> 1 vanilla ingot
```

Crushing loses metal and smelting gives it back 1:1, so the loop is recycling rather than
duplication. Ore doubling is one `Process` entry away and deliberately not taken yet.

Four tables, and adding content is an edit to one of them:

- **`MATERIALS`** -- one line per metal. The eleven the game ships a bar for reuse it; a metal
  vanilla has none for gives a `tint` instead and gets `Hytech_Ingot_<Metal>` generated from the
  game's own ingot model, with every process below following automatically. That is the seam for
  Hytech's own metals.
- **`FORMS`** -- what a metal can *be*: an ingot, a dirty dust, an ore. A form owns the id and name
  templates, the model, and the greyscale texture that gets tinted per metal, so a plate or a clean
  dust is one entry plus a `.blockymodel`. A form naming a `vanilla` id resolves to the game's own
  item wherever one exists -- which is how an ore is nameable in a recipe while Hytech ships none.
- **`PROCESSES`** -- what a machine makes from what, written in forms rather than item ids, so one
  line covers every metal. A process whose forms a metal cannot resolve (there is no bronze ore) is
  skipped rather than written out as a recipe that can never match.
- **`FLUIDS`** -- the molten form of each metal. A fluid is not an item: it is the bare string a
  Hytech tank stores (`Molten_Iron`), and the generator registers each one with the game as a
  `ResourceType` asset with an icon of its own, so the fluid exists as a named thing rather than
  only as whatever some tank happens to be holding, and a later recipe can name it the way vanilla
  names `Metal_Bars`.

**A bucket is a fluid made carryable.** `MOLTEN_BUCKET` is an ordinary `Form` with a `fluid`, an
amount and an `empty_item`: it draws the vanilla full-bucket model, tints *only* the water pixels of
the vanilla bucket texture (the empty texture is the mask, so the wood stays wood), and declares the
library's `Hytech_FluidBucket` interaction, which pours 1,000 units into whatever Hytech container
it is right-clicked on and leaves a `Container_Bucket` in the hand. See `HytechCore/CLAUDE.md` for
the interaction and for why a block with a page has to honour it itself.

Nothing *produces* a molten metal yet -- no recipe outputs a fluid, and the buckets come from the
creative library. The fluids exist so that the tanks, pipes and machines have something to move
before the machine that makes them does.

**Colours are sampled, not typed.** Each metal's hue, saturation and lightness come from the game's
own `Ingot_Textures/<Metal>.png`, and the authored greyscale art is re-shaded with them -- so iron
and silver stay grey because their ingots are grey, and a new vanilla metal needs nothing but its
name. Lightness is re-centred on the metal's own but compressed towards the middle, or onyxium dust
would be a black smudge in an inventory.

Two things about generated assets:

- **The generated folders are owned outright.** `--check` fails on an orphan as well as on a stale
  file: a renamed metal would otherwise leave a live item behind with no recipe. Icons share a
  folder with hand-made ones, so only names matching a form's id pattern are pruned there.
- **Most ingredients wins.** `MachineRecipes` sorts each group by input count, descending. That is
  what will let an alloy (two dusts) beat a plain smelt (one dust) when alloys return; without the
  sort that choice fell out of asset iteration order.

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

Everything else — pipes, tanks, generators, machines — is on the Tech Bench, including the blocks
that had no recipe at all before (burner, solar panel, battery). Those recipes still name the
plates, wire, coils, circuits, casings and frames that came out with the old material table, so
`check-asset-refs.py` reports 26 dangling `ItemId`s: the blocks are uncraftable until those forms
are back in `FORMS` or the recipes are rewritten against what exists.

Player crafting hangs off each item's own `Recipe` block, so only machine recipes need to be
standalone assets -- which is the whole reason the generator writes *nothing* into a hand-authored
block: a crusher recipe is a file of its own under `Server/Item/Recipes/Hytech/Generated/`, while a
block's bench recipe lives in the block's own JSON where it was written by hand.

**A translation key is prefixed with the file it came from.** `I18nModule.getPrefix` builds every
key as `<file name>.<key in file>`, folding in subdirectories — which is why `server.lang` holds
`items.X.name` and assets ask for `server.items.X.name`. Generated names live in `materials.lang`,
so those items ask for `materials.items.X.name`. Getting this wrong is silent: the client shows the
raw identifier and nothing is logged.

`check-asset-refs.py` has a second pass for this: every `ItemId` a recipe names must exist. That
failure is quiet in a way a missing texture is not — the recipe loads, validates, and then never
matches, so a machine just sits there.

