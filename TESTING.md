# Testing Hytech in-world

There is no test framework in this repo, so every behavioural claim is verified by running the
dev server. This file is the checklist, plus the debug blocks that make the non-energy resource
types observable at all.

## Before launching

```bash
export JAVA_HOME=~/.jdks/openjdk-25.0.1      # Gradle needs a Java 25+ JVM

python scripts/check-asset-refs.py           # catches the fatal asset errors below
python scripts/generate-pipe-assets.py --check
python scripts/generate-pipe-tints.py --check
python scripts/generate-overlay-assets.py --check
python scripts/generate-burner-assets.py --check
python scripts/generate-machine-assets.py --check
python scripts/generate-icons.py --check

./gradlew build && ./gradlew server
```

`check-asset-refs.py` exists because a missing `Icon` or texture is a **fatal validation error
for that item**, and the server reports it as a wall of `SEVERE` lines and then carries on
without the item rather than failing the build. That is easy to ship and only notice on launch.
The script resolves every asset path our JSON references against both our `Common/` tree and the
game's `Assets.zip`, since plenty of our assets legitimately point at vanilla art.

Icons in particular are a trap: `Icons/ItemsGenerated/` is normally written by the game's own
icon renderer and copied back by the `syncAssets` task — which happens *after* validation. So
every new item needs a placeholder committed up front. `generate-icons.py` draws them.

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

**Read any block or pipe with the Multimeter** (`ReadLogisticContainer`). It prints every
Hytech container on the block, so a fluid pipe reports its network's resource and fill, and the
burner reports both its energy and its item container.

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

### UIs

- Solar, wind and battery pages all open and show live values. The wind page is new: wind
  generators produced power with no UI at all before.
- A non-fuel item dropped in the burner grid shows as incompatible.

## Known gaps

- **`FUEL_LIQUID` generators return 0.** Wiring them to the fluid module is not done.
- **Breaking a pipe fails when aimed at a marker-drawn arm** — the marker entity absorbs the
  break ray. Left as is by decision; the alternatives each trade one bug for another.
- **Heat is a stored scalar, not a temperature.** A full heat block stops accepting rather than
  reaching equilibrium with its neighbours.
