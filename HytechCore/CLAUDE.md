# HytechCore

The logistics library every tech mod builds on. Read the root `CLAUDE.md` first — it covers the
build, the framework internals and the platform. This file is the **contract a content mod builds
against**, and the rules that only hold here.

## The rule for what belongs in this project

Engines and containers, never a concrete block. A battery, a generator, a crusher and a cable are
all specialized implementations of a `LogisticContainer`, and mods will want different ones.

The two worked examples are worth holding in mind:

- The **machine engine** stays here. A machine names its own `RecipeGroup`, `MachineRecipes` indexes
  every `Processing` bench requirement it finds, and nothing has to be registered — so another mod
  adds a machine in assets alone.
- **Generation** does not. `GeneratorType{SOLAR, WIND, FUEL_SOLID, FUEL_LIQUID}` is a closed enum
  inside a codec, so a mod could not add a generator kind without editing this project. It lives in
  `HytechPlugin/content/generators` instead.

Anything shaped like the first is framework. Anything shaped like the second is content.

## What this project must never do

- **Reference a content asset.** The library has to stand alone, and there is a check for it:
  `python scripts/check-asset-refs.py --resources HytechCore/src/main/resources` resolves this tree
  against itself and vanilla only. It must pass.
- **Put a recipe on a content mod's bench.** The wrench and the multimeter craft at the vanilla
  workbench out of vanilla bars for exactly this reason. The debug pipes have no recipe at all.
- **Name a resource type.** Nothing in `core/` mentions energy, items, fluid, gas or heat any more.
  A resource brings its own id, label and accent colour via `AbstractLogisticModule`, so a
  mod-supplied type gets a wrench entry and a side-config tab for free. If you find yourself adding
  a `switch` on a resource id here, add a method to `LogisticResourceType` instead.
- **Import from `at.rasebdon.hytech.content`.** A javadoc link counts; one crept in and had to be
  reworded.

## Authoring a pipe

This is the one thing a content mod has to get right in assets, so it has its own validation.

A pipe block renders as a centre hub plus one arm per connected face, and every one of the 64
connection masks is a **block-state variant** with its own model and multi-box hitbox.
`PipeConnectionStateSystem` swaps between them with `setBlockInteractionState`. A mask the item does
not declare makes that call a **silent no-op** — the pipe simply draws as though nothing were
connected, with nothing in the log.

To ship a pipe of your own:

1. Declare `hytech:<resource>:pipe` on the block, with `PipeCapacity` and `PipeTransferSpeed`.
   For **items** `PipeCapacity` is *buffer slots*, not an amount — a large number allocates a large
   container per segment.
2. Point `CustomModel` at one of the shapes this project ships and give it your own
   `CustomModelTexture`. That is the reskin, and it is all most pipes need:
   - `Blocks/Pipes/Default/Pipe_Center.blockymodel` — the 8-unit hub, used by every scalar resource.
   - `Blocks/Pipes/Items/Pipe_Items_Center.blockymodel` — the 12-unit hub. `ItemPipeComponent`
     returns 12 from `getHubSize()`, which is what the wrench ray-tests an arm against, so an item
     pipe has to use this one.
3. Add your item JSON to `PIPE_TYPES[].item_jsons` in `scripts/generate-pipe-assets.py` and run it.
   That fills in all 64 `BlockType.State.Definitions` entries pointing at the shared geometry and
   hitboxes. Different geometry means authoring two source models and adding a `PIPE_TYPES` entry
   with your own `root`, which writes the 64 models and hitboxes into your tree.
4. Leave `NormalConnectionModelAsset`, `PullConnectionModelAsset` and `PushConnectionModelAsset` out
   unless you have your own marker models — the defaults resolve to the `Pipe_Normal`, `Pipe_Pull`
   and `Pipe_Push` assets this project ships.

`check-asset-refs.py` enforces all of it: all 64 states present, every `CustomModel` and
`HitboxType` resolving, and the three marker models resolving. The five `Pipe_Debug_*` items here
are the reference implementation to copy, and they deliberately omit step 4 so the defaults stay
exercised.

## Debug blocks

`Server/Item/Items/Debug/Pipe_Debug_{Energy,Items,Fluid,Gas,Heat}.json` — one per resource type, at
a throughput high enough that a debug pipe never becomes the bottleneck you are trying to observe.
Untinted grey art, so they are visibly not one of the real pipes, and no recipe, so they are
creative-only. They exist to make a resource type observable in-world before any content mod ships
a pipe for it.

Their icons are drawn into *this* project's `Common/Icons/ItemsGenerated/` by
`scripts/generate-icons.py` — a missing `Icon` is a fatal validation error for that item, and the
game's icon renderer only fills that folder in *after* validation, so a placeholder has to be
committed up front.

The creative-library category tree (`Technic.json`) is here too, with its labels in
`hytechcore.lang`, so that content items saying `"Categories": ["Technic.General"]` resolve against
a category the library owns and the library alone still has somewhere to put its own items.
