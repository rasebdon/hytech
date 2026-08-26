# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HytechPlugin is a Hytale server plugin (not Minecraft/Forge/Fabric) implementing logistics networks for energy and
items. It uses the native Hytale Plugin framework with Gradle (Kotlin DSL).

- **Group/Version:** `at.rasebdon` / `0.1.0`
- **Java Version:** 25
- **Server Version:** `0.5.9`
- **Main Entrypoint:** `at.rasebdon.hytech.HytechPlugin`

## Build & Development Commands

```bash
./gradlew build          # Compile and package
./gradlew server         # Run dev server (also syncs assets)
./gradlew syncAssets     # Sync resources from game build folder back to src
```

Gradle itself must run on a **Java 25+ JVM** — the `hytale-mod` plugin requires it, so an older
default JDK fails during configuration with "Dependency requires at least JVM runtime version 25".
Set `JAVA_HOME` accordingly before invoking `./gradlew`.

There is no test framework configured; verification is in-world. See `TESTING.md` for the
checklist and for the creative source/void blocks that make fluid, gas and heat observable.

Run `python scripts/check-asset-refs.py` before launching. A missing `Icon` or texture is a
fatal validation error for that item, which the server reports as `SEVERE` lines and then
carries on without the item -- so it does not fail the build and is easy to miss. Note that
`Icons/ItemsGenerated/` is written by the game's icon renderer and copied back by `syncAssets`,
which happens *after* validation, so every new item needs a placeholder icon committed up front
(`scripts/generate-icons.py`).

### Decompiled server sources for the IDE

`./gradlew decompileServer` (from the `hytale-mod` plugin) writes
`HytaleServer-sources.jar` next to `HytaleServer.jar`, which is the `-sources` convention
IntelliJ picks up automatically. It is up-to-date-checked against the server jar, so it
only re-runs after a Hytale update.

`hytale.decompile_partial=true` in `gradle.properties` restricts it to `com.hypixel.*`.
The plugin defaults this to **false**, which decompiles the whole jar -- 37,150 classes
instead of 9,733, the bulk of it fastutil, bouncycastle, netty and guava. The task also
forks its own JVM, so `org.gradle.jvmargs` does not reach it and `maxHeapSize` is set on
the task in `build.gradle.kts`. With both, a full run is about a minute.

Note: toggling `hytale.decompile_partial` does not invalidate the task (the flag is passed
through an unannotated `CommandLineArgumentProvider`), so use `--rerun` after changing it.

### Reading the Hytale API

The API ships only as bytecode, so to read it as source, unpack the sources jar that
`decompileServer` produces:

```bash
unzip -qq -o "$APPDATA/Hytale/install/release/package/game/latest/Server/HytaleServer-sources.jar" -d hytale-sources
find hytale-sources -mindepth 1 -maxdepth 1 ! -name com -exec rm -rf {} +
```

`hytale-sources/` is gitignored. Grep that tree instead of running `javap` per class.
Re-unpack it after a Hytale update.

Two gotchas worth knowing: this `unzip` build does not let `*` cross `/`, so
`unzip 'com/*'` silently extracts nothing and you have to unpack everything and prune.
And `org.joml` is not in the jar (partial decompile covers `com.hypixel` only) -- get joml
sources from Maven if you need them.

## Architecture

### Plugin Initialization

`HytechPlugin.setup()` initializes the modules in a load-bearing order:

1. `HytechCoreModule` — shared components, pipe rendering, wrench, face overlay, read interaction
2. `ItemModule` — item network, transfer, vanilla container wrapping
3. `EnergyModule` — energy network, transfer, generation, UIs
4. `HeatModule`, `FluidModule`, `GasModule` — network, transfer, persistence
5. `MachineModule` — the processing engine behind the crusher and electric smelter

Machines come **last**: a machine owns no container of its own, it reads the
`hytech:items:container` and `hytech:energy:container` of the block it sits on, so both those
modules have to be registered first.

Items must come **before** energy: the burner generator reads its fuel from a
`hytech:items:container` so item pipes can feed it, which makes energy the module with the
dependency. Nothing on the item side needs energy.

### Generic Logistic Framework

Every resource type (energy, items, fluid, gas, heat) extends
`AbstractLogisticModule<TBlockComponent, TPipeComponent, TRegistrationSystem, TContainer>`, where
`TContainer extends LogisticContainer`. The four type parameters are:

- `TBlockComponent` — storage/processing blocks
- `TPipeComponent` — transport pipes
- `TRegistrationSystem` — handles component registration with Hytale's chunk/entity stores
- `TContainer` — the transferable resource interface

### Container Contract

`core/containers/` is what makes the framework generic. Before it existed `TContainer` was an
unbounded type variable, the framework could not call a single method on a container, and every
resource type therefore shipped its own copy of the transfer algorithm.

```
LogisticContainer                         getTransferSpeed, isEmpty, isFull,
                                          getAvailable, getAcceptable, moveTo
ScalarContainer         : Logistic…       getAmount, getTotalCapacity, add, reduce,
                                          getDelta, updateDelta  (+ derived helpers)
TypedScalarContainer<R> : Scalar…         getResourceType, setResourceType, canAccept
```

`LogisticContainer` deliberately has **no** F-bound. `LogisticContainer<T extends LogisticContainer<T>>`
would give `moveTo` a statically typed target, but the bound then has to be repeated on every
generic declaration in `core/` for no practical gain — a network only ever holds containers of one
family, so the `instanceof` in each `moveTo` is a guard against a bug, not a routine cast.

`getAcceptable` returns `Long.MAX_VALUE` for slot-based containers, so sum it with
`LogisticContainer.saturatingSum` rather than `+`.

Energy, fluid, gas and heat are scalars; **items are the odd one out** and implement only the bare
`LogisticContainer`, because slot capacity depends on what is already in the slots and there is no
meaningful "remaining capacity".

Fluid and gas are **single-type tanks** (Mekanism style): a tank adopts whatever first enters it,
rejects anything else until drained, and releases the claim once empty. The resource is a plain
string id, so a new fluid is declared entirely in assets. Two invariants hold in both tanks and
pipes: an amount with no type is unreachable and gets zeroed, and clearing a type zeroes the amount
— which is why `TypedScalarNetworkSaveSystem` writes the amount *before* the type.

Heat is a stored scalar rather than a temperature that equalises. A full heat block stops accepting
instead of reaching equilibrium with its neighbours; gradients would need a diffusion transfer
system instead of the shared pull/push one.

### Shared Component and Network Bases

| Base | Purpose |
|---|---|
| `AbstractScalarBlockComponent` | amount/capacity/speed + codec for a storage block |
| `AbstractScalarPipeComponent` | per-segment capacity/speed and saved contents |
| `AbstractTypedScalar{Block,Pipe}Component` | adds the resource id for fluid/gas |
| `ScalarNetwork` / `TypedScalarNetwork` | aggregate buffer: capacity summed, speed minimised |
| `ScalarNetworkSaveSystem` / typed variant | capacity-weighted persistence with remainder carry |
| `AbstractBlockStateSystem` | drives a block's visual state from one of its components |

**Validators are checked against a field's default at registration.** `BuilderCodec.validateDefaults`
runs every validator over the value a *fresh* component instance holds, before any asset is read. So
a bound the default cannot satisfy -- `greaterThan(0)` on a field defaulting to 0 -- fails component
registration and takes the whole plugin down with a `CodecValidationException`, not just that one
block. Either widen the bound or give the field a default that passes.

**Codec key naming.** Capacity and transfer speed are spelled identically everywhere and live on
the shared bases. The stored *amount* is per-subclass: energy keeps `Energy` and `SavedEnergy`
because shipped assets and existing worlds use those keys, while new types use `Amount`. Renaming
energy's would silently zero every battery and pipe in an existing world.

A pipe is a **holder, not a container** — it reports its network's container as its own and returns
null when unnetworked. It must not implement the container interface itself; the energy pipe used
to, forwarding 40 lines of delegation to the network and throwing on any read before a network
existed.

### Network System Pattern

Each resource type has three collaborating systems:

1. **Network** (`LogisticNetwork`) — graph of pipes + targets. Maintains `Set<Pipes>`, `List<PullTargets>`,
   `List<PushTargets>` derived from neighbor face configs.

2. **NetworkSystem** (`LogisticNetworkSystem`) — listens to component change events (`ADDED`/`REMOVED`/`CHANGED`), runs
   connected-component DFS to rebuild networks, fires network lifecycle events.

3. **TransferSystem** — `AbstractTransferSystem<TContainer>` holds the *entire* algorithm: pull
   into each network, then priority-ordered block push, then network push, all with fair-share
   distribution and rate limiting. A concrete transfer system is pure configuration (~25 lines):
   the two event classes, an optional pass interval, and an optional delta hook.

   Note `MaxTransfer` is denominated **per transfer pass**, not per second. Energy passes every
   tick; items pass once a second. Unifying that would rebalance every existing block, so the
   interval stays per module via `getTransferIntervalSeconds()`.

   A source is capped by its own transfer speed across *all* targets. Energy originally applied the
   cap per target, so a block with N outputs emitted up to `N × MaxTransfer` per tick.

### Event-Driven Design

All inter-system communication uses Hytale's `IEventRegistry`. Change types: `ADDED`, `REMOVED`, `CHANGED` (from
`LogisticChangeType`). Events are dispatched in: component change → network rebuild → transfer system re-indexes.

### Pipe Connection Rendering

Pipes do **not** spawn an entity per connection. Each of the 64 possible connection masks
is a block-state variant with its own model and multi-box hitbox, and
`PipeConnectionStateSystem` swaps between them with `setBlockInteractionState` when the
topology changes. That call writes with settings 198, whose bit 2 makes `WorldChunk.setBlock`
skip block-entity recreation, so the pipe's component and face configs survive the swap.
Do not switch to vanilla's `ConnectedBlocksUtil` for this -- it writes with settings 132 and
would wipe the block entity on every neighbour update.

`PipeConnectionMask` owns the bit layout (`1 << (face.getValue() - 1)`), the state naming
and the arm box extents; the renderer, the wrench and the asset generator all read it.

The only pipe entities left are the push/pull markers `PipeFaceMarkers` spawns for faces
explicitly wrenched to INPUT or OUTPUT, torn down by the companion `RefSystem` returned
from `PipeConnectionStateSystem.cleanupSystem()`.

### Block Face Configuration

Each face of a logistic block has a `BlockFaceConfig` (INPUT/OUTPUT/BOTH/DISABLED).

**The per-face asset keys are an allow-list, not a default.** `"Up": ["OUTPUT"]` means OUTPUT is the
*only* permitted state, so the wrench cannot cycle that side and the face overlay deliberately shows
nothing on it -- which is what made the wrench look broken on generators. Use `"Default": "OUTPUT"`
to set the starting state without restricting the face. Convention: give a functional side
`[<state>, "NONE"]` plus that `Default`, so it can always be switched off but never set to something
meaningless. This controls neighbor detection and
flow direction. The Wrench interaction cycles face configs and fires `CHANGED` events.

The pipe-to-pipe shortcut — direction is meaningless between two pipes, so such a face only
toggles between connected and off — requires **both** sides to be pipes. Testing only the neighbour
made every block face with a pipe against it toggle-only: a crusher side permitting INPUT, OUTPUT and
NONE has no BOTH to toggle to, so the side went to NONE and stuck there, and the same was true of the
burner and of a battery with a cable on it.

On a pipe the wrench targets an individual arm: it reads `InteractionSyncData.raycastHit`,
converts it to block-local coordinates and asks `PipeConnectionMask.faceAt` which arm box
contains the point, falling back to `blockFace` when the client sends no hit point.

### Auto-push

`MachinePage` carries a **Push ‹resource›: On/Off** row for every resource the block participates in,
which flips `isExtracting` on that resource's block component. Only the block-push phase of
`AbstractTransferSystem` reads that flag — a pipe wrenched to pull from a face draws whether or not
the block is extracting — so the toggle is what saves a player from wrenching every pipe that
collects a machine's output.

Rows index `SideConfigPage.presentResources`, the same registration-ordered list the side
configurator walks, and the index is re-resolved on click rather than remembered: a page can outlive
a change to the block it describes.

`ItemNetwork.isFull()` is what stops this being a footgun. An item run with no reachable sink reports
itself full, so a machine with auto-push on next to a dead-end pipe pushes nothing instead of loading
the run for `ItemPipeEjectSystem` to drop on the floor three seconds later. Pipes are a conduit, not
storage, and that is the one place the aggregate container has to say so out loud.

### Neighbor Management

Neighbors are explicitly registered (not discovered via chunk scan). `LogisticNeighborMap` maintains bidirectional
neighbor relationships. Face configs on both sides of a connection determine whether transfer is possible and in which
direction.

### External Container Wrapping

Vanilla Hytale item containers are integrated via `HytechItemContainerWrapper`, which adapts a
native `ItemContainer` to the `HytechItemContainer` interface. `ItemUtils.getLegacyItemContainer`
resolves the container for a world position from the block's ECS components
(`ItemContainerBlock`, or `ProcessingBenchBlock` exposed as a combined input+output container).
`ItemComponentRegistrationSystem` keys its wrappers by world position, because components — unlike
the block states they replaced — are not identity-stable across lookups.

### Hytale API Notes (0.5.9)

- Vectors are **JOML** (`org.joml.Vector3i/f/d`); Hytale's own vector classes are gone. The
  read-only `Vector3ic` views have no `clone()` — copy via `new Vector3i(other)`. Constants such as
  `BLOCK_SIDES` live on `com.hypixel.hytale.math.vector.Vector3iUtil`.
- Entity rotations use `com.hypixel.hytale.math.vector.Rotation3f` (radians), not `Vector3f`.
- The `world.meta.BlockState` / `BlockStateModule` API was removed. Block data is plain ECS
  components on the `ChunkStore`, fetched with `HytechUtil.getBlockComponent(world, pos, type)`.
- Chat goes through the `PlayerRef` **component** (`store.getComponent(ref, PlayerRef.getComponentType())`);
  `Player.sendMessage` is gone and `Player.getPlayerRef()` is deprecated for removal.
- `BlockAccessor.getRotation`/`getRotationIndex` are deprecated for removal with no replacement
  exposed yet, so `HytechUtil.getBlockTransform` still emits one deprecation warning.

### Machine UIs

Pages are built on Hytale's own custom-UI API. **HyUI was removed** -- it could not do two things
this mod needs:

- `PageBuilder.open` always calls `openCustomPage`, and HyUI exposes no way to build a page without
  opening it. So a page could never be opened *with* windows, and a machine could never show the
  player's inventory.
- It registers element ids when its HTML is parsed, so only statically declared elements can receive
  events. Its own dynamic example adds cards with no ids and no listeners.

The native API has neither limit, and `.ui` gives access to the game's real design language --
`$C.@DecoratedContainer`, the vanilla button art, the shared colour variables -- which HyUI's HTML
dialect only approximated.

How it fits together:

| Piece | Role |
|---|---|
| `core/ui/HytechCustomPage` | base: appends a `.ui` document, renders values, binds actions |
| `core/ui/MachinePage` | the page every machine opens; sections are filled or hidden |
| `core/ui/MachineView` | what a machine writes: primary bar, secondary bar, item slots, detail rows |
| `core/ui/HytechPages` | opens a page, with a container window when it has one |
| `core/ui/PageRefreshSystem` | pushes fresh values once a second (HyUI did this internally) |
| `Common/UI/Custom/Hytech/*.ui` | the documents, built on the game's `Common.ui` |

Things worth knowing:

- **Windows and custom pages are different systems, not layers.** `setPageWithWindows` switches the
  client to `Page.Bench` -- the screen that carries the player's inventory -- and a custom page
  *replaces* that screen. So a custom page cannot host real item slots, and
  `openCustomPageWithWindows` does not give you both. A machine that needs slots summarises its
  container on the page and offers a button that opens a `ContainerWindow`. The docs are explicit:
  "Only use `ContainerWindow` when the player needs to move actual items."
- **`EventData` keys: no `@` for a literal, `@` for a selector.** An unprefixed key carries a static
  value; a leading `@` marks it *dynamic*, meaning the client reads the value as a selector at event
  time. `@Action` with a literal makes the client try to resolve it as a selector and fail with
  "Failed to gather CustomUI event binding".
- **One decoded event arrives per page**, not per element, so each binding carries its action name as
  a static literal in its `EventData` and `onAction` switches on it.
- `ItemSlot` **renders** on a custom page and is how a machine shows its contents: set
  `#Selector.ItemId` and `#Selector.Quantity` on it, as vanilla's own `RespawnPage` does for the
  items a player dropped. What a custom page cannot do is let anyone *drag* one, so moving items
  still means a `ContainerWindow`. `ItemGrid` in a custom page renders nothing.
- **Declare the cells, set the values.** `MachinePage.ui` carries a fixed six item cells and five
  auto-push rows, hidden when unused, rather than `clear` + `append` per refresh. A structural
  update every second is a page that keeps dropping its own clicks (see the acknowledgment rule
  above).
- `render` runs on open *and* on every refresh, so it must read live state and be safe to repeat.
  It returns a **change signature**, and `refresh` skips the update when it matches the last one.
  That is not an optimisation: `updateCustomPage` increments an outstanding-acknowledgment counter,
  and `PageManager.handleEvent` **drops incoming Data events while that counter is non-zero**, so a
  page that refreshes unconditionally eats its own button clicks.
- **Bind with `locksInterface = false`.** A locking binding leaves the client on "Loading..." until
  the server answers; combined with the dropped-event rule above, one badly timed refresh froze a
  page permanently.
- **A block's own `Use` interaction runs instead of the held item's.** That is why the wrench works
  on pipes, which declare no `Use`, and did nothing on a generator or battery, which do. A machine
  therefore has to honour the wrench itself: `OpenPageBlockInteraction` checks
  `WrenchInteraction.isWrench(item)` and calls `configureTargetedFace` rather than opening its page.
  Any new machine with a page inherits that.
- **Use the vanilla widget styles rather than rebuilding them.** `$C.@ProgressBar` carries the right
  height, background and effect textures; wrapping a bare `ProgressBar` in a bordered `Group` and
  forcing a taller height stretched the 9-patch and looked broken. `@PanelWidth` is 284 to match it.
- A machine adds no UI document of its own: `MachinePage.ui` declares every section and
  [MachineView] hides the ones the machine did not fill. A new resource type needs no UI code.

### Electric Machines

A machine is three components on one block: `hytech:machine:processor` for the rules,
`hytech:items:container` for the slots, `hytech:energy:container` for the buffer. There is one
component and one system for every machine — a crusher and an electric smelter differ only in the
numbers their assets carry, which is what makes a later factory tier a JSON edit rather than a
class.

| Piece | Role |
|---|---|
| `machines/components/MachineProcessorComponent` | `RecipeGroup`, `EnergyPerTick`, `SpeedMultiplier`, `ParallelOperations`, plus the saved operation |
| `machines/systems/MachineProcessingSystem` | resolve a recipe, spend energy, consume ingredients, write results |
| `machines/MachineRecipes` | index of which recipes belong to which machine |
| `machines/MachineSlots` | the ingredient/result halves of one container |
| `machines/interaction/ui/OpenMachinePageInteraction` | the machine's page, on the shared `MachinePage` |

**Recipes are ordinary vanilla assets.** `AssetRegistryLoader` registers `CraftingRecipe` against
`Item/Recipes`, so a machine recipe is a standalone JSON under `Server/Item/Recipes/Hytech/` with a
`BenchRequirement` naming the machine — `{"Type": "Processing", "Id": "Hytech_Crusher"}`. That id
belongs to no bench block, which vanilla is content with: `CraftingPlugin.onRecipeLoad` creates a
registry for whatever id a recipe names. So Hytech gets the whole authoring format (item or resource
type inputs, several outputs, `TimeSeconds`) and the game's own validation, and `MachineRecipes`
only has to bucket them by id. The index is built lazily and rebuilt when the asset count changes,
because assets load on their own schedule and a machine may tick before the recipe pack is in.

Matching still goes through vanilla: `CraftingManager.getInputMaterials`, `getOutputItemStacks` and
`matches` are the same calls `BenchSystems.ProcessingBenchTick` makes, so a Hytech machine reads a
recipe exactly as a vanilla bench does.

**Energy is per tick**, matching generation in `EnergyGenerationSystem`. A machine that cannot
afford the current tick simply does not advance — progress is held rather than lost, and nothing is
consumed while it waits.

**One container, split into halves.** A block can hold only one `hytech:items:container`
(`ComponentRegistry` keys by class), so a machine declares `InputSlots` and `OutputSlots` on it: the
leading slots take ingredients, the trailing ones hold results. Both default to 0, which means
"undivided" — what the burner's fuel slot and the item buffer want. Two things enforce the split,
and both are needed:

- `SlotFilter.DENY` on `FilterActionType.ADD` for every output slot, applied by
  `ItemBlockComponent`. Pipes and players both insert through the container's filtered path, so one
  filter closes both; the machine writes its own results with filtering off. Filters are not part of
  the codec, so they are re-applied whenever the component decodes or resizes.
- `HytechItemContainer.canExtractFrom(slot)`, consulted by `moveTo`. Removal has to stay open for
  the player, so a filter cannot do this job: without the hook a pipe on an OUTPUT face would carry
  the unprocessed ore straight back out again.

`MachineSlots` owns the slot arithmetic that follows from the split — how many whole sets of
ingredients are present, how many sets of results will fit, and the consume/insert pair. Vanilla's
container helpers work on a whole container, and a crusher must not count the dust in its output as
an ingredient.

### Resource Assets

- `src/main/resources/Common/` — client-side (textures, block models, UI, icons)
- `src/main/resources/Server/` — server-side (item defs, interactions, languages, models)
- `Assets.zip` is unpacked during the build from Hytale game files

## Key Files for Orientation

| File                                                    | Purpose                                         |
|---------------------------------------------------------|-------------------------------------------------|
| `HytechPlugin.java`                                     | Entry point, module init order                  |
| `core/AbstractLogisticModule.java`                      | Generic framework all modules extend            |
| `core/containers/LogisticContainer.java`                | The contract that makes the framework generic   |
| `core/systems/AbstractTransferSystem.java`              | The whole transfer algorithm, once               |
| `heat/HeatModule.java`                                  | Smallest complete resource type; copy this       |
| `machines/MachineModule.java`                           | Machines: one engine for every processing block  |
| `machines/systems/MachineProcessingSystem.java`         | Recipe, energy and progress in one place        |
| `energy/EnergyModule.java`                              | Richest module: generation, UIs, block states    |
| `core/components/ContainerHolder.java`                  | Neighbor tracking base                          |
| `core/networks/LogisticNetwork.java`                    | Network graph structure                         |
| `core/networks/LogisticNetworkSystem.java`              | Graph algorithms (connected-component DFS)      |
| `core/networks/ScalarNetwork.java`                      | Aggregate buffer shared by all scalar types      |
| `core/systems/LogisticComponentRegistrationSystem.java` | Component lifecycle with Hytale stores          |

## Current Development

Branch `feat/item-system`. Five resource modules are live — `energy`, `items`, `fluid`, `gas`,
`heat` — plus a Burner Generator that turns any vanilla `Fuel` item into energy, and a `machines`
module with a Basic Crusher and a Basic Electric Smelter.

Machines, materials and tiers are being built in phases (the plan lives outside the repo):

1. **The processing engine** — done: the processor component, both machines at their basic tier, and
   a starter recipe set (copper and iron ore → dust → vanilla bars, so crushing first doubles an ore).
2. **Materials and progression** — dust and plate for all eleven vanilla metals, a Hytech steel
   chain, wire, coils, circuits and machine frames, with recipes anchored on vanilla ores and bars.
3. **Five tiers** — `Basic`, `Advanced`, `Elite`, `Ultimate`, `Quantum` across the pipes and the
   machines, from one balance table, with tier N crafted from tier N-1 plus that tier's circuit and
   frame.

Module init order is **core → items → energy → heat → fluid → gas**. Items must precede energy
because the burner reads its fuel from a `hytech:items:container`, so item pipes can feed it. The
dependency only runs one way.

Adding another scalar resource type costs ~11 small classes and no new pipe geometry. Most of those
classes are only separate because `ComponentRegistry` and `IEventRegistry` both key by class, so a
shared generic instance would collide.

Known gaps:

- **Items deliberately have no save system.** Item pipes own their buffer containers and those are
  part of `ItemPipeComponent`'s codec, so contents persist with the block.
- **Breaking a pipe fails when aimed at a marker-drawn arm.** The marker entity absorbs the break
  ray. Left as is by decision; the alternatives each trade one bug for another.
- **`FUEL_LIQUID` generators return 0.** Wiring them to the fluid module is not done.
- **Fluid, gas and heat have never been tested in-world.** They compile and the assets cross-check,
  but no transfer has been observed.
