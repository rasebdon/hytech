# Testing Hytech in-world

There is no test framework in this repo, so every behavioural claim is verified by running the
dev server. This file is the checklist, plus the debug blocks that make the non-energy resource
types observable at all.

## Before launching

```bash
export JAVA_HOME=~/.jdks/openjdk-25.0.2      # Gradle needs a Java 25+ JVM

python scripts/check-asset-refs.py           # catches the fatal asset errors below
python scripts/generate-pipe-assets.py --check
python scripts/generate-pipe-tints.py --check
python scripts/generate-material-assets.py --check

./gradlew build && ./gradlew server
```

`server` runs `:HytechPlugin:runServer`, which loads both plugins. Each generator writes into the
project it belongs to -- `scripts/paths.py` says which -- so all of the above are still run from the
repo root, unchanged.

**Two plugins, so check the log before anything else:**

- **Both** `Enabled plugin Technic:...` lines are there, `HytechCore` first. The order comes from
  the content manifest's `Dependencies`, and content's setup reads `EnergyModule.get()`, so the
  wrong order is an immediate `IllegalStateException: Not initialized`. Only *one* line means the
  library booted alone -- `:HytechCore:runServer`, the IDE configuration named
  `HytechCore only (no content)` -- which presents as Hytech being broken: the creative menu holds
  the wrench, the multimeter and the five debug pipes and nothing else.
- `Loading assets from ...HytechCore...` before `...HytechPlugin...`, and **no** `Duplicate asset
  pack` line -- that one shuts the server down outright.
- No `SEVERE` from our packs. Vanilla's own `BlockSetModule` warnings and the `Flags.IsUsable`
  unused-key warnings are pre-existing noise.

To check the library still stands on its own, which the dev run does not tell you:

```bash
python scripts/check-asset-refs.py --resources HytechCore/src/main/resources
```

**Stopping the server: kill the JVM, not Gradle.** `runServer` forks the server as a child
process, so killing the Gradle invocation leaves it running -- holding the world lock and its log
file handles, which then makes the next run come up on a stale world and refuse to delete its own
logs. Stop it from the server console, or:

```powershell
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
  Where-Object { $_.CommandLine -like '*com.hypixel.hytale.Main*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

Also note `hytale.runDir` is resolved against the *project* directory, so it is pinned to the repo
root in the shared build config. Without that pin each subproject grows its own `run/` and the
server boots a fresh world while the real universe sits untouched at the root.

And once per release, the case `runServer` cannot reach: copy both jars into `run/mods/`, take them
off the classpath and start the server. Under `runServer` every class sits on one app classloader,
so cross-plugin linkage always appears to work; only real jars exercise `PluginClassLoader` and the
dependency bridge. **This has not been done yet.**

`check-asset-refs.py` exists because a missing `Icon` or texture is a **fatal validation error
for that item**, and the server reports it as a wall of `SEVERE` lines and then carries on
without the item rather than failing the build. That is easy to ship and only notice on launch.
The script resolves every asset path our JSON references against *every* project's `Common/` tree
and the game's `Assets.zip`, since plenty of our assets legitimately point at vanilla art -- and
since `Common/` is one merged namespace at runtime, so a content pipe pointing at the library's
geometry is correct rather than missing. It also checks that every pipe item declares all 64
connection states with models that resolve, which is the one pipe failure that is silent in-world.

Icons in particular are a trap: `Icons/ItemsGenerated/` is normally written by the game's own
icon renderer and copied back by the `syncAssets` task — which happens *after* validation. So
every new item needs a placeholder committed up front. `generate-material-assets.py` draws one for
every item it generates; anything hand-authored needs one committed by hand.

## Debug blocks

Energy has a solar panel to produce it and a battery to store it, so it was always testable.
Fluid, gas and heat had no generating machinery at all, which meant no way to observe transfer.
These three pairs fill that gap — search the creative library for "Source" or "Void":

| Block | Behaviour |
|---|---|
| `Fluid_Source` / `Gas_Source` / `Heat_Source` | Pinned **full**, extracting, every face `OUTPUT`, priority 0 (wins contests) |
| `Fluid_Void` / `Gas_Void` / `Heat_Void` | Pinned **empty**, every face `INPUT`, priority 100 (loses contests) |

Both are the same `hytech:core:creative_source` component, distinguished by a `Voiding` flag.
One component and one system cover every resource type, because they talk only to the container
interfaces — so a sixth resource type becomes testable the moment its module is registered.
Fluid and gas sources declare a `ResourceType` (`Water` and `Steam` by default); heat is
untyped. Change the resource by editing the block JSON.

A source will not convert one resource into another: if its tank somehow already holds
something else, it refuses rather than overwriting. A void releases its type claim each time it
drains, so it swallows anything you throw at it rather than locking onto the first resource.

**Read any block or pipe with the Multimeter** (`Hytech_ReadLogisticContainer`). It prints every
Hytech container on the block, so a fluid pipe reports its network's resource and fill, and the
burner reports both its energy and its item container.

`HytechCore` also ships **`Pipe_Debug_{Energy,Items,Fluid,Gas,Heat}`** -- untinted grey pipes at a
throughput high enough that they never become the bottleneck you are trying to watch, with no recipe
so they are creative-only. Two uses: run a resource across a distance without building a real pipe
network, and check a content pipe against them, since they are the reference implementation the
pipe-authoring rules in `HytechCore/CLAUDE.md` describe.

## Checklist

### Energy — regression only, this all worked before

- Solar panel charges a battery through a pipe run.
- A block with **three** output neighbours emits at most its own `MaxTransfer` per pass *in
  total*. It used to emit 3×, so existing setups feel slower; that is the fix, not a bug.
- Fill a network, idle 60 s, watch the total: it must not drift. The save system used to lose up
  to `pipeCount - 1` units every 5 s.
- Place a battery beside a live pipe run — network capacity must change with no restart.
- Wrench still cycles a single pipe arm, and face configs survive a topology change.

### Items

- Chest → item pipe → chest, respecting `INPUT`/`OUTPUT`.
- Two destination chests split fairly.

### Fluid, gas, heat

For each of the three, and note that **none of this has ever been observed**:

1. `Source` → pipe run → `Tank`. The tank fills; the Multimeter shows the resource id and rising
   amount. Tanks default to `BOTH` on every face, so no wrenching is needed to start.
2. `Tank` → pipe run → `Void`. The tank drains.
3. **Single-type rule** (fluid and gas only): fill a tank from a `Water` source, then pipe a
   `Steam` source into the same tank. It must **reject** the steam until the tank is empty.
   Change one source's `ResourceType` to test this.
4. Break a pipe mid-run and confirm the two halves become separate networks with their contents
   split rather than duplicated or lost.
5. Fill a run, travel far enough to unload the chunk, return. Contents *and* the resource id
   must both survive — the resource id is the part most likely to be missing, since a network
   that comes back holding an untyped quantity discards it.

### Burner generator

- Drop charcoal (or any item with the vanilla `Fuel` resource type) into the fuel grid. The
  firebox should light, energy should rise, the burn bar should deplete, and it should stop at
  zero fuel.
- **Confirm the drag actually moved the item server-side**: break the block afterwards; the
  remaining fuel must drop. HyUI's item grid is a rendered view plus events rather than a
  binding onto a server container, so the move is performed by our own code and is the most
  likely thing to be wrong.
- Click a slot to withdraw fuel back to your inventory.
- Feed the burner by **item pipe** as well as by hand — the fuel lives in a
  `hytech:items:container` precisely so pipes can fill it.

### Machines — the crusher and the electric smelter

Both are in the creative library under Technic → Machines; copper and iron dust are under
Technic → Materials.

- [ ] Place a **Basic Crusher**, wire a solar panel or burner into any face. The page shows the
      energy filling and `Idle`.
- [ ] Put `Ore_Copper` in the first slot (the Slots button opens the window). It processes: the
      front texture lights, the progress bar climbs, **two** `Copper Dust` land in the result slots
      and the buffer drains 20 RF/t.
- [ ] Feed the dust to a **Basic Electric Smelter** — one bar out per dust, so ore routed through
      the crusher first yields twice what the vanilla furnace gives.
- [ ] Cut the power mid-operation. Progress holds where it is, nothing is consumed, and it resumes
      when power comes back rather than restarting.
- [ ] Fill the result slots. Processing stops with `Blocked` and picks up again once they are
      emptied — nothing is destroyed.
- [ ] Pipe test: chest → item pipe → an INPUT face, and an OUTPUT face → item pipe → chest. The
      pipe must feed only the ingredient slots and collect only the results; it must never carry
      the unprocessed ore back out.
- [ ] Wrench each face, then read the machine with the Multimeter: it reports both its energy and
      its item container.
- [ ] Break the machine mid-operation and reload the world. The saved recipe and progress come
      back rather than restarting from zero.
- [ ] The page shows the slot contents as **item icons with quantities**, and a sixteen-slot buffer
      shows the first six plus `(+N more)`.
- [ ] **Push Items: On/Off** toggles on the page. With it on and a chest against an OUTPUT face, the
      results leave on their own; with it off they stay put. A machine carrying both items and
      energy shows one row per resource.
- [ ] Auto-push into a pipe run with nothing on the far end moves nothing — the results stay in the
      machine rather than loading the pipes and hitting the floor three seconds later.
- [ ] Wrench a machine face that has an **item pipe** against it: it cycles In / Out / Off rather
      than sticking on Off after one click. Same check on the burner and a battery with a cable.

### Materials, molten metals and the crafting ladder

Dusts and buckets are creative-library reachable under Technic → Materials, one of each per metal.
Everything below is generated from `scripts/hytech_materials.py`.

- [ ] **Names, not identifiers.** Every generated item shows a real name ("Dirty Iron Dust",
      "Bucket of Molten Copper"). A raw `materials.items.X.name` means a language key lost its
      file prefix.
- [ ] Crush 3 `Ingredient_Bar_Iron` in the crusher — 2 Dirty Iron Dust, not 3.
- [ ] Smelt one dust back — 1 bar. Crushing then smelting the same metal must lose material, or
      the balance in the table is not the balance in the world.
- [ ] Repeat on one late metal (mithril, adamantite) to confirm the recipes really are per-metal
      and not just iron's.
- [ ] Every dust is visually distinct in the inventory: iron and silver read grey, copper orange,
      thorium green, onyxium purple. Two metals that look identical mean the tint sampling found
      no colour in the vanilla ingot texture.
- [ ] **Buckets pour.** Hold a Bucket of Molten Iron, right-click a **Fluid Tank**: the tank shows
      `Molten_Iron` and 1,000 more units, the bucket becomes an ordinary `Container_Bucket`, and
      the tank's page does *not* open — the pour consumed the click.
- [ ] Right-click the same tank with a bucket of a **different** metal: nothing happens, the bucket
      stays full, and the page opens instead. A tank holds one fluid until it is empty.
- [ ] Pour into a tank with less than 1,000 units of room: nothing happens. A bucket is all or
      nothing.
- [ ] Right-click a bucket on a **crusher** or a battery: the machine page opens as usual, since
      neither has a typed container to pour into.
- [ ] Pipe the poured fluid out of the tank to a `Fluid_Void` — a molten metal is an ordinary fluid
      to the network, with no special casing anywhere.

Nothing produces a molten metal yet: buckets come from the creative library, and the smelter has
no fluid output. Filling an empty bucket *from* a tank is not implemented either.

### UIs

- Solar, wind and battery pages all open and show live values. The wind page is new: wind
  generators produced power with no UI at all before.
- A non-fuel item dropped in the burner grid shows as incompatible.

## Known gaps

- **The split has not been smoke-tested from real jars.** Both plugins load, both packs register in
  dependency order and cross-plugin component registration works -- but only under `runServer`,
  which puts everything on one classloader. See *Before launching*.
- **`FUEL_LIQUID` generators return 0.** Wiring them to the fluid module is not done.
- **Breaking a pipe fails when aimed at a marker-drawn arm** — the marker entity absorbs the
  break ray. Left as is by decision; the alternatives each trade one bug for another.
- **Heat is a stored scalar, not a temperature.** A full heat block stops accepting rather than
  reaching equilibrium with its neighbours.
