# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HytechPlugin is a Hytale server plugin (not Minecraft/Forge/Fabric) implementing logistics networks for energy and
items. It uses the native Hytale Plugin framework with Gradle (Kotlin DSL).

- **Group/Version:** `at.rasebdon` / `0.1.0`
- **Java Version:** 25
- **Server Version:** `0.6.0`
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

The side-configuration panel carries an **arrow-up toggle** that flips `isExtracting` on the block
component of whichever resource its tabs have selected. Only the block-push phase of
`AbstractTransferSystem` reads that flag — a pipe wrenched to pull from a face draws whether or not
the block is extracting — so the toggle is what saves a player from wrenching every pipe that
collects a machine's output.

It lives beside the faces because it is the same question they answer: *which way does this resource
leave the block.* It used to be a stack of "Push Energy: On" buttons in the status column, one per
resource, which put a per-resource decision a long way from the other per-resource decision and made
the status column mostly buttons. One toggle that follows the selected tab replaces all five rows.

The resource is re-resolved from `LogisticResourceType.presentAt` on click rather than remembered: a
page can outlive a change to the block it describes, and a stale index would toggle the wrong
container.

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

### Hytale API Notes (0.6.0)

- Vectors are **JOML** (`org.joml.Vector3i/f/d`); Hytale's own vector classes are gone. The
  read-only `Vector3ic` views have no `clone()` — copy via `new Vector3i(other)`. Constants such as
  `BLOCK_SIDES` live on `com.hypixel.hytale.math.vector.Vector3iUtil`.
- Entity rotations use `com.hypixel.hytale.math.vector.Rotation3f` (radians), not `Vector3f`.
- The `world.meta.BlockState` / `BlockStateModule` API was removed. Block data is plain ECS
  components on the `ChunkStore`, fetched with `HytechUtil.getBlockComponent(world, pos, type)`.
- Chat goes through the `PlayerRef` **component** (`store.getComponent(ref, PlayerRef.getComponentType())`);
  `Player.sendMessage` is gone and `Player.getPlayerRef()` is deprecated for removal.
- **A block entity lives on a 32-cube section, not on the column.** `BlockModule.BlockStateInfo`
  carries `getSectionRef()` (0.5.9's `getChunkRef()` is gone) pointing at a `ChunkSection`, and its
  `getIndex()` decodes with `ChunkUtil.xFromIndex`/`yFromIndex`/`zFromIndex` -- the old
  `*FromBlockInColumn` trio no longer exists. The y that comes out is **section-local (0-31)** and
  has to be lifted by the section's own y before `WorldChunk` will take it; the old call returned a
  column y and needed no lifting, so this is a silent semantic change, not just a rename.
  `HytechUtil.locate` does the whole walk -- section, then `getChunkColumnReference()` to the
  column, then the coordinates -- and returns the `BlockLocation` every caller wants. Nothing
  should decode a block index by hand.
  `BlockStateInfo.fillWorldPos` is the engine's own version, but it yields a **world** position,
  while `WorldChunk`'s block accessors take chunk-local x/z with an absolute y. Different spaces.
- `BlockAccessor.getRotation` was **removed**. Only `getRotationIndex` survives, itself deprecated
  for removal, so `HytechUtil.getBlockTransform` turns the index back into a tuple with
  `RotationTuple.get` -- an unguarded array access, hence the bounds check before it.

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

| Piece                       | Role                                                                                       |
|-----------------------------|--------------------------------------------------------------------------------------------|
| `core/ui/HytechCustomPage`  | base: appends a `.ui` document, renders values, binds actions                              |
| `core/ui/MachinePage`       | the page every machine opens; owns the panels, the actions and the routing                 |
| `core/ui/MachineView`       | the **only** writer on the page: bars, cells, detail rows, and the signature               |
| `core/ui/SideConfigPanel`   | the plus-shaped side configurator, as a panel inside `MachinePage`                         |
| `core/ui/SlotTransfer`      | two-click item moves, since a page cannot be dragged on                                    |
| `core/ui/HytechPages`       | opens a page, with a container window when it has one                                      |
| `core/ui/PageRefreshSystem` | pushes fresh values once a second (HyUI did this internally)                               |
| `Hytech/Hytech.ui`          | the design tokens: palette, metrics, and the `@Section`/`@SlotCell`/`@FaceCell` constructs |
| `Hytech/MachinePage.ui`     | the three-column shell every block opens                                                   |
| `Hytech/InventoryPanel.ui`  | the player's 45 cells, as a named expression a page instantiates                           |

`MachinePage.ui` is one wide `@DecoratedContainer` (980x860) holding three columns and a strip:

```
Status (flex)    |  Contents (flex)      |  Side Configuration (268, hidden until asked)
  primary bar    |    in cells > out     |    [^] [E][I][F][G][H]
  secondary bar  |    progress + arrow   |         [ ][Up][ ]
  6 detail rows  |    contents summary   |     [W][North][E]
                 |    Open Inventory     |        [ ][S][Down]
-------------------------------------------------------------------
Inventory: the player's 36 storage + 9 hotbar cells, centred
[ Configure Sides ]  [ Cancel Move ]
```

Side configuration is a **panel, not a page**. It used to be its own `SideConfigPage`, which meant
configuring a crusher hid the crusher, and Back had to rebuild the machine page from scratch. Living
inside `MachinePage` retires that whole dance, and you can watch a face change take effect.

**The contents column and the inventory come and go together**, keyed off whether the machine filled
`MachineView.slots`. A battery has nothing you could move an item into, so both disappear and the
remaining panels flex to take the row back — which is why `#ReadoutPanel` and `#ProcessPanel` carry
`FlexWeight` rather than fixed widths, and why the vanilla progress bar (a hard-coded 284 wide) is
centred inside its column rather than stretched to fill it.

**Face colours are the wrench's colours**, sampled from `Common/VFX/Overlay/Face_Overlay_*.png`:
purple both, red in, blue out, grey off. The panel therefore needs no legend — the player learned
those colours pointing a wrench at a block, and a panel that invented its own would have to teach
them twice. `SideConfigPanel` owns the hex values; if the overlay art changes, they change with it.

**Resource tabs are coloured squares with tooltips**, not text buttons: five labels wrapped onto
three rows in a 268px column and read as a mess. Each carries the resource's initial over its pipe
accent, dimmed when not selected.

Things worth knowing:

- **Windows and custom pages are different systems, not layers.** `setPageWithWindows` switches the
  client to `Page.Bench` -- the screen that carries the player's inventory -- and a custom page
  *replaces* that screen. So a custom page cannot host real item slots, and
  `openCustomPageWithWindows` does not give you both -- it exists and does send the `OpenWindow`
  packets, but the custom page is the visible screen. A machine therefore draws its slots as
  clickable cells (see two-click transfer below) and keeps a `ContainerWindow` button for real
  dragging. The docs are explicit: "Only use `ContainerWindow` when the player needs to move actual
  items."
- **`EventData` keys: no `@` for a literal, `@` for a selector.** An unprefixed key carries a static
  value; a leading `@` marks it *dynamic*, meaning the client reads the value as a selector at event
  time. `@Action` with a literal makes the client try to resolve it as a selector and fail with
  "Failed to gather CustomUI event binding".
- **One decoded event arrives per page**, not per element, so each binding carries its action name as
  a static literal in its `EventData` and `onAction` switches on it.
- **A `UIPath` is relative to the document it is written in, and a miss is silent.** Copying
  vanilla's `"Common/ContainerPanelPatch.png"` into `Hytech/Hytech.ui` looks for
  `Hytech/Common/ContainerPanelPatch.png`, finds nothing, and the client draws a **white
  missing-texture cross** with nothing in the log — which is what made every panel on the first
  draft of this page look broken. Vanilla art needs `../`; our own art sits next to the document and
  needs no prefix at all. `check-asset-refs.py` now resolves every texture and `$document` reference
  in every `.ui` file, including the `@2x`-only naming most vanilla UI art uses, so this fails the
  check instead of the eye.
- **`ItemSlotButton`, not `ItemSlot`, and not `ItemGrid`.** `ItemSlot` renders but is display-only.
  `ItemGrid` is not the answer either, despite appearances: `ItemGridSlot` *is* registered in
  `UICommandBuilder.CODEC_MAP`, so `set("#Grid.Slots", List<ItemGridSlot>)` encodes fine -- but the
  element's only documented callbacks are `SlotDoubleClicking` and the mouse-enter/exit pair, no
  shipped server code binds any of them, and `EventData` carries static literals plus client-side
  `@` selectors with nothing that yields a slot index. `ItemSlotButton` reports `Activating`,
  `RightClicking` and `DoubleClicking`, and shows the item tooltip of any `ItemSlot` child. So every
  cell in the mod is an `ItemSlotButton` wrapping an `ItemSlot #Icon` and a `Label #Count`.
- **`.Quantity` on a page-hosted slot is rejected** with a "CustomUI Set command error", which is
  why `@SlotCell` draws the number in a label of its own -- as vanilla's `DroppedItemSlot` does.
- **Two clicks move an item.** Nothing on a custom page can be dragged, so `SlotTransfer` turns
  click-source then click-destination into `moveItemStackFromSlotToSlot` with filtering **on**. That
  is what keeps a machine's result slots refusing insertions -- the `SlotFilter.DENY` on ADD is the
  same filter a pipe hits -- without the UI knowing anything about machines. Right-click moves one.
  The `ContainerWindow` button is still there for anyone who would rather drag.
- **Two filters, two jobs.** A `SlotFilter` on the container is the right tool for a rule that holds
  against everyone — a machine's result slots refuse insertions from pipes and players alike.
  `SlotTransfer.Filter` is the rule that only applies to *a person clicking*: a crusher will happily
  hold cobblestone in its ingredient slot, it just has no recipe for it. The predicate comes from
  `MachineRecipes.acceptsIngredient`, which is deliberately **non-positional** — `MachineSlots`
  matches across the whole input range, so a positional check (vanilla's own
  `CraftingManager.matchesAnyRecipe`) would refuse loads the machine would have processed.
  It is the same predicate that greys the contents summary, so the page never learns what a crusher
  is.
- **Style states are settable from Java, by hex string.** `set("#FaceUp.Style.Default.Background",
  "#3f8f57")` works: selectors chain by descendant and nest into style objects, exactly as vanilla's
  `BarterPage` recolours a trade row. That is how the side configurator's faces carry their mode as
  a colour, and it means hover is a real style state rather than a round-trip event. `ButtonStyle`
  itself is *not* in `CODEC_MAP`, so a whole style can only be swapped by `Value.ref`.
- **Repaint the two cells that changed, not all fifty.** The page draws 45 inventory cells plus the
  machine's own; writing four style commands per cell every second to say "still not selected" would
  triple the update. `MachineView` is handed the previously-held cell and touches only it and the
  new one.
- **Declare the cells, set the values.** `MachinePage.ui` carries fixed item cells, six detail rows
  and five auto-push rows, hidden when unused, rather than `clear` + `append` per refresh. A
  structural update every second is a page that keeps dropping its own clicks (see the
  acknowledgment rule above).
- **A hidden element is skipped by layout**, which is why the slot grids use `LeftCenterWrap` rather
  than fixed rows: a crusher with one ingredient slot draws one cell, not one cell and three holes.
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
- **Everything writes through `MachineView`.** Not tidiness: `write` is what appends to the change
  signature, and a value sent around it is one that can go stale on screen without `refresh` ever
  noticing. `SideConfigPanel` takes the view, not the command builder, for exactly that reason.
- **`@ProgressBar` spreads the caller's `@Anchor` before its own `Width: 284`,** so a narrower bar
  cannot be had through `@Anchor` -- the 284 always wins. Replace the `Anchor` property outright.
  This is why `@ReadoutWidth` is 312: 284 plus the section's padding.
- A machine adds no UI document of its own: `MachinePage.ui` declares every section and
  [MachineView] hides the ones the machine did not fill. A new resource type needs no UI code.
- **`@DecoratedContainer`'s close button is artwork only.** `@CloseButton = true` draws the X; the
  page still has to bind `#CloseButton` itself, as vanilla's own containers do.

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

### Materials and the Progression

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

The bench is the one exception to its own rule: it is built at the *vanilla* workbench out of
vanilla bars, which is what keeps the whole tree reachable from a fresh world. Everything else —
plates, components, pipes, tanks, generators, machines — moved onto it, including the blocks that
had no recipe at all before (wrench, multimeter, burner, solar panel, battery).

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
| `scripts/hytech_materials.py`                           | The material and component table, and the balance |
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
2. **Materials and progression** — done: dust and plate for twelve metals, a steel chain, a bronze
   recipe, wire, coils, five circuit tiers, a casing and five machine frames, all from one table,
   plus a Tech Bench that every Hytech recipe now lives on.
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
