# Starkraft (Headless RTS Prototype)

## Architecture / Dev Roadmap

This project is a headless, fixed-tick RTS simulation. The roadmap below is ordered to keep the runtime deterministic and stable while layering systems.

### Core Loop
1. **Time + Tick Budget**
   - Keep all systems driven by a fixed tick (`Time.TICK_MS`).
   - Avoid allocations inside the tick loop unless pooled or reused.

2. **World + ECS-ish State**
   - Store components in `World` maps by `EntityId`.
   - Keep component creation and removal centralized in `World.spawn/remove`.
   - Add new components only when a system needs state that persists across ticks.

3. **Order Queue**
   - Commands become `Order`s and are consumed by systems in a consistent order.
   - Prefer small, composable orders over large polymorphic “do everything” orders.

### Movement and Pathing
4. **Map + Static Terrain**
   - `MapGrid` is the source of static passability and per-tile weights.
   - Any terrain changes should be explicit and infrequent.

5. **Dynamic Occupancy**
   - `OccupancyGrid` tracks moving blockers each tick.
   - Avoid per-entity allocation by clearing and refilling in a single sweep.

6. **Pathfinding**
   - `Pathfinder` (A* with octile heuristic) handles weighted tiles and corner-cut rules.
   - `PathfindingSystem` enforces per-tick node budgets and request quotas.
   - Keep the path pool preallocated to avoid allocations on replan.

7. **Movement Execution**
   - `MovementSystem` consumes `Order.Move`, follows `PathFollow`.
   - Replan on blocked next waypoint or “stuck” detection.
   - Apply arrival tolerance and waypoint smoothing to reduce jitter.

### Combat + Visibility
8. **Combat**
   - Resolve targeting and damage in `CombatSystem`.
   - Cache enemy lists per faction to reduce per-tick scan cost.
   - Ensure deterministic ordering when iterating entities.

9. **Vision / Fog**
   - `VisionSystem` updates per-faction fog grids each tick.
   - Fog is used as the basis for line-of-sight and information hiding.

### Determinism and Performance
10. **Deterministic Simulation Tests**
    - Run the same seed and command script and compare a final hash.
    - Use this test to validate refactors or performance changes.

11. **Benchmark Harness**
    - Log p50/p95 tick times over long runs (10k+ ticks).
    - Use this to validate that replans and path budgets are stable.

### Extending Features
12. **Buildings / Footprints**
   - Add building placement/removal that updates `OccupancyGrid` (static counts).
   - Keep building behavior in its own system and avoid branching in movement.
   - `BuildingPlacementSystem` now covers headless placement/removal for rectangular footprints.
   - Placement clearance is data-driven in building defs, so spacing rules stay deterministic.

13. **Resources**
   - Keep faction stockpiles in deterministic sim state.
   - Apply spending at placement / production time, not lazily.
   - `ResourceSystem` now provides basic minerals/gas stockpiles for headless costs.
   - `ResourceHarvestSystem` now supports deterministic nearby harvesting from fixed resource nodes.
   - Harvesters now carry cargo back to the nearest same-faction building footprint before stockpiles increase.
   - Building defs now carry explicit harvest drop-off capability, so return targets do not have to piggyback on producer training rules.
   - `DataRepo.buildSpec(...)` reads dedicated building defs, while `DataRepo.trainSpec(...)` reads trainable unit defs.
   - Unit and building defs now carry explicit `archetype` ids so coarse categories do not have to overload `typeId`.

14. **Unit Behaviors**
   - `BuildingProductionSystem` now supports deterministic per-building production queues.
   - Spawn completed units onto free tiles around building footprints.
   - Training compatibility is data-driven via allowed producer building type ids.
   - Producer queue limits are data-driven in building defs rather than hardcoded in the system.
   - Producer building defs can also supply default rally offsets for newly trained units.
   - Producer capabilities such as training and rally override are explicit in building defs.
   - Add higher-level orders like `AttackMove`, `Hold`, `Patrol` by composing
     move + attack decisions at the order layer.

15. **Networking / Replay**
   - Keep commands deterministic and serializable.
   - Use replay as the canonical regression tool for simulation changes.

### System Order (Recommended)
1. `AliveSystem`
2. `OccupancySystem`
3. `PathfindingSystem`
4. `MovementSystem`
5. `CombatSystem`
6. `VisionSystem`

This order prevents movement from using stale occupancy, ensures path requests
are ready before movement executes, and avoids combat targeting out of date
positions.

## Running
Use `./gradlew :sim:run` with JDK 17.

### Headless Script Input
You can run a simple command script with:
`./gradlew :sim:run --args="--script sim/scripts/sample.script --noSleep"`

Script syntax:
- `tick <n>` set absolute tick
- `wait <n>` advance ticks
- `select <id...>` select units by entity id
- `select @label...` select units by label (from spawn)
- `selectAll` select all units
- `selectFaction <id>` select units by faction at execution time
- `selectType <typeId>` select units by type at execution time
- `selectArchetype <id>` select units and buildings by archetype at execution time
- `move <x> <y>` move selection
- `attack <targetId|@label>` attack target
- `harvest <targetId|@label>` assign selection to harvest from a resource node
  Harvested cargo is delivered to the nearest same-faction building footprint.
- `spawnNode [@label] <kind> <x> <y> <amount>` spawn a resource node (`MineralField`, `GasGeyser`)
- `spawn [@label] <faction> <typeId> <x> <y> [vision]` spawn a unit
- `build [@label] <faction> <typeId> <tileX> <tileY> [width] [height] [hp] [armor] [minerals] [gas]` place a building footprint
- `train <buildingId|@label> <typeId> [buildTicks] [minerals] [gas]` enqueue unit production on a building
- `cancelTrain <buildingId|@label>` cancel the last queued production job and refund its cost
- `rally <buildingId|@label> <x> <y>` override a producer building rally point for future trained units
- If optional build/train values are omitted or `0`, the sim falls back to typed data defs when available
- Building defs can also provide `placementClearance`, which reserves a buffer ring around footprints

Other flags:
- `--seed <n>` jitter initial demo spawns deterministically
  Recorded into replay metadata when saving replay files.
  Replay files also store `mapId` and `buildVersion`.
- `--script <path>` run a command script
- Relative script/replay paths are resolved from the project root, so `sim/scripts/sample.script` works under Gradle
- `--scriptDryRun` parse, validate, and print script commands without running
- Script validation now reports specific build-definition errors such as unknown building ids or unresolved width/height/hp defaults
- Script validation also preflights `train` defaults and labeled producer compatibility when that information is available
- For labeled producer builds, script validation also catches obvious queue-limit overflow using an optimistic queue timeline
- `--spawnScript <path>` run a spawn-only script before other commands
- `--replayOut <path>` save recorded commands after a run
- `--replayValidateOnly` load/validate replay and exit
- `--replayStats` print per-tick command counts plus selector split (`direct`, `faction`, `type`) and action breakdowns like `move.direct`; large reports auto-compact the middle ticks
- `--replayStatsJson` print replay stats as JSON, including per-tick selector splits and action breakdowns
- `--compactJson` render `--replayStatsJson` and `--replayMetaJson` on one line for smaller machine-facing output
- Replay stats JSON shape is covered by a golden test in `sim/src/test/kotlin/starkraft/sim/AppTest.kt`
- `--replayMetaJson` print replay metadata plus current runtime map/build/seed context, resolved replay path, file size, event count, strict-mode flags, and compatibility warnings as JSON
- Replay metadata JSON shape is covered by a golden test in `sim/src/test/kotlin/starkraft/sim/AppTest.kt`
- `--snapshotJson` print a final read-only client snapshot JSON for renderer/frontend integration
- Snapshots expose faction minerals/gas, resource nodes with remaining amounts, plus entity archetypes, production state, building extents, placement clearance, building capabilities/queue limits, default rally offsets, current rally point, and harvester cargo/return state when present
- `--snapshotEvery <n>` stream client snapshots every `n` ticks during the run; respects `--compactJson`
- `--snapshotOut <path>` write typed snapshot NDJSON records (`recordType`, `tick`, `snapshot`) instead of stdout
  NDJSON files begin with a `sessionStart` record carrying `mapId`, `buildVersion`, and `seed`.
  They also emit a `mapState` bootstrap record with blocked tiles, weighted terrain, current static occupancy, and resource nodes.
  Issued commands are also emitted as `command` records.
  Script-driven selection changes emit `selection` records before commands for that tick.
  Rally overrides emit `rally` records when producer rally points change.
  Invalid rally commands also emit `rallyFailure` records with deterministic reasons.
  Producer-related train failures also emit `producerFailure` records with building/type context.
  Invalid build commands also emit `buildFailure` records with faction/type/tile context.
  Other invalid train commands emit `trainFailure` records with producer/type context.
  Spawn commands emit `spawn` records with resolved runtime entity ids.
  Applied move and attack orders emit `orderApplied` records after selector resolution.
  Those order applications also emit `orderQueue` records with the resulting queue sizes.
  Successful path solves emit `pathAssigned` records when units receive new waypoint lists.
  Waypoint consumption emits `pathProgress` records as units advance and finish paths.
  Static blocker changes emit `occupancy` records with tile-level blocked/unblocked deltas.
  Snapshot cadence emits `vision` records with fog visibility deltas per faction.
  Snapshot cadence emits `economy` records with faction minerals/gas for HUD updates.
  Harvest ticks emit `resourceNode` records with per-node harvested amounts, remaining resources, and depletion flags.
  Harvest ticks also emit `harvestCycle` records for worker pickup/deposit completions.
  Snapshot cadence also emits `harvesterState` records with worker gather/return phase, cargo, node target, and return building target.
  Per-tick resource spends and refunds emit `resourceDelta` records with mineral/gas deltas.
  Per-tick resource deltas also emit `resourceDeltaSummary` records aggregated by faction.
  Snapshot cadence also emits `producerState` records with producer archetypes, capabilities, and default rally offsets.
  Combat ticks also emit `damage` records for health-bar style UI updates.
  Production activity emits `production` records for enqueue/progress/complete events.
  Invalid build/train commands emit `commandFailure` records with deterministic reasons.
  Streams end with a `sessionStats` aggregate record before `sessionEnd`.
  Combat ticks emit `combat` records with attack and kill events.
  Entity removals emit `despawn` records with ids, types, factions, and reasons.
  Snapshot cadence also emits `metrics` records with alive counts, visibility, pathing, and replan telemetry.
  Snapshot cadence also emits compact `tickSummary` records for fast per-tick client consumption.
  `tickSummary` and `sessionStats` include build/train failure counters split by reason.
  `tickSummary` and `sessionStats` also include faction 1/2 minerals and gas.
  `tickSummary` and `sessionStats` also include mineral/gas spend and refund rollups, both total and split by faction 1/2.
  They also include harvest totals for minerals/gas, pickup/deposit cycle counts and amounts, plus resource-node change and depletion counts.
  `metrics` records also carry faction minerals/gas alongside alive counts and visibility.
  Each NDJSON record also carries a monotonic `sequence` field.
  They end with a `sessionEnd` record carrying final `tick`, `worldHash`, and optional `replayHash`.
- Example consumer:
  `./gradlew :sim:consumeSnapshotStream --args="/tmp/starkraft.ndjson"`
  This reads the NDJSON stream and prints record counts plus session/hash metadata, including resource-node change counts, per-faction harvest splits, active nodes, remaining resources, economy, producer/production, combat, pathing, vision, and archetype selector summaries when present.
  It also summarizes harvester gather/return phase counts and total carried cargo when `harvesterState` records are present.
  When `harvestCycle` records are present, it also prints pickup/deposit event counts and transferred amounts.
  Resource-node totals also drop depleted nodes once a `despawn` record with reason `resourceDepleted` is seen.
- Replay validation/stats warn when replay `mapId` or `buildVersion` differs from the current run
- `--strictReplayMeta` fail on replay `mapId`/`buildVersion` mismatches
- Normal script/replay runs print current runtime metadata (`mapId`, `buildVersion`, `seed`) with the final hashes
- Final CLI outcome summaries now include aggregate harvest totals, per-faction harvest splits, pickup/deposit cycle totals, changed nodes, depletion counts, and current remaining node totals when harvesting occurred
- Depleted resource nodes are removed from the world with despawn reason `resourceDepleted`
- Workers targeting a depleted node are cleared from harvesting, and their pending move-to-node order is dropped
- If another node of the same type exists, affected workers are retargeted to the nearest remaining node instead of going idle
- Human CLI logs now include build/train outcome summaries, including failure reasons when they occur
- `--dumpWorldHash` print world hash after a normal run
- `--strictReplayHash` fail if replay is missing a hash
- `--printEntities` dump alive units at the end
- `--printOrders` dump pending orders at the end
- `--replayDump <path>` save a replay after script runs
- `--labelDump` dump label→entity mappings

Sample spawn script:
`./gradlew :sim:run --args="--spawnScript sim/scripts/spawn.script --script sim/scripts/sample.script --noSleep"`

Sample harvest script:
`./gradlew :sim:run --args="--script sim/scripts/harvest.script --ticks 50 --noSleep"`

Sample gas harvest script:
`./gradlew :sim:run --args="--script sim/scripts/harvest-gas.script --ticks 50 --noSleep"`
`./gradlew :sim:run --args="--script sim/scripts/harvest-gas.script --ticks 50 --noSleep --snapshotEvery 1 --snapshotOut /tmp/starkraft-gas.ndjson"`
