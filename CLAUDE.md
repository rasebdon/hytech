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

There is no test framework configured.

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

`HytechPlugin.setup()` initializes three modules in order:

1. `HytechCoreModule` — registers shared components (pipes, blocks, entity proxies, wrench interaction)
2. `EnergyModule` — energy network, transfer, generation, UI
3. `ItemModule` — item network, transfer, legacy container wrapping

### Generic Logistic Framework

Every resource type (energy, items) extends
`AbstractLogisticModule<TBlockComponent, TPipeComponent, TRegistrationSystem, TContainer>`. The four type parameters
are:

- `TBlockComponent` — storage/processing blocks
- `TPipeComponent` — transport pipes
- `TRegistrationSystem` — handles component registration with Hytale's chunk/entity stores
- `TContainer` — the transferable resource interface (`HytechEnergyContainer`, `HytechItemContainer`)

### Component Hierarchy

```
ContainerHolder<TContainer>           (neighbor tracking)
  └── LogisticComponent<TContainer>   (per-face block face config: INPUT/OUTPUT/BOTH/DISABLED)
        ├── LogisticBlockComponent     (storage: transfer priority, extraction flag)
        └── LogisticPipeComponent      (transport: network assignment, render state)
```

### Network System Pattern

Each resource type has three collaborating systems:

1. **Network** (`LogisticNetwork`) — graph of pipes + targets. Maintains `Set<Pipes>`, `List<PullTargets>`,
   `List<PushTargets>` derived from neighbor face configs.

2. **NetworkSystem** (`LogisticNetworkSystem`) — listens to component change events (`ADDED`/`REMOVED`/`CHANGED`), runs
   connected-component DFS to rebuild networks, fires network lifecycle events.

3. **TransferSystem** (e.g., `EnergyTransferSystem`) — ticking system that listens to component and network events,
   implements pull-from-sources → push-to-sinks logic with priority-based ordering and rate limiting.

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
| `core/AbstractLogisticModule.java`                      | Generic framework all systems extend            |
| `energy/EnergyModule.java`                              | Reference implementation of the generic pattern |
| `core/components/ContainerHolder.java`                  | Neighbor tracking base                          |
| `core/networks/LogisticNetwork.java`                    | Network graph structure                         |
| `core/networks/LogisticNetworkSystem.java`              | Graph algorithms (connected-component DFS)      |
| `energy/networks/EnergyNetwork.java`                    | Concrete network with transfer logic            |
| `core/systems/LogisticComponentRegistrationSystem.java` | Component lifecycle with Hytale stores          |

## Current Development

Branch `feat/item-system` is implementing the item transfer system by mirroring the energy system pattern. The
`ItemModule`, `ItemNetwork`, `ItemNetworkSystem`, `ItemTransferSystem`, and supporting components are all modeled after
their `energy/` counterparts.

The item side now mirrors energy: real container semantics on `HytechItemContainer`,
working pull/push transfer with face-config filtering, priority ordering, fair-share
distribution and rate limiting, plus an `Item_Buffer` block and a `ReadItemContainer`
inspection interaction.

One deliberate divergence: there is **no** `ItemNetworkSaveSystem`. Item pipes own their
buffer containers and those are part of `ItemPipeComponent`'s codec, so contents persist
with the block. `ItemNetwork` only aggregates them through a `CombinedItemContainer`. This
avoids the rounding loss `EnergyNetworkSaveSystem` still has (`perPipe = energy / pipeCount`).
