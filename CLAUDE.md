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

Each face of a logistic block has a `BlockFaceConfig` (INPUT/OUTPUT/BOTH/DISABLED). This controls neighbor detection and
flow direction. The Wrench interaction cycles face configs and fires `CHANGED` events.

On a pipe the wrench targets an individual arm: it reads `InteractionSyncData.raycastHit`,
converts it to block-local coordinates and asks `PipeConnectionMask.faceAt` which arm box
contains the point, falling back to `blockFace` when the client sends no hit point.

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
| `energy/EnergyModule.java`                              | Richest module: generation, UIs, block states    |
| `core/components/ContainerHolder.java`                  | Neighbor tracking base                          |
| `core/networks/LogisticNetwork.java`                    | Network graph structure                         |
| `core/networks/LogisticNetworkSystem.java`              | Graph algorithms (connected-component DFS)      |
| `core/networks/ScalarNetwork.java`                      | Aggregate buffer shared by all scalar types      |
| `core/systems/LogisticComponentRegistrationSystem.java` | Component lifecycle with Hytale stores          |

## Current Development

Branch `feat/item-system`. Five resource modules are live — `energy`, `items`, `fluid`, `gas`,
`heat` — plus a Burner Generator that turns any vanilla `Fuel` item into energy.

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
