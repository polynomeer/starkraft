# Starkraft (Headless RTS Prototype)

## Status

The `sim` module is complete as a deterministic headless RTS sandbox.

Completed scope:
- fixed-tick deterministic simulation loop
- movement, pathfinding, replanning, and occupancy
- combat, fog-of-war, attack-move, hold, and patrol
- buildings, construction, production, research, and tech prerequisites
- economy with stockpiles, harvesting, drop-offs, and retargeting
- replay, benchmark, golden determinism checks, and snapshot/replay tooling
- client-facing snapshot, event stream, live input, and minimal graphical/console clients

Out of scope for this module:
- a polished game UI
- art, audio, menus, and campaign content
- full online multiplayer
- non-headless rendering engine integration

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
   - Auto-targeting now avoids obvious lethal overkill when another in-range target can absorb a full shot.
   - Auto-acquired targets now stay sticky while they remain valid and in range, which reduces target thrash.

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
   - Building defs can also declare `buildTicks`; those buildings occupy immediately, gain HP over time, and do not satisfy tech prerequisites until construction completes.
   - Construction now requires explicit worker assignment through `construct` orders instead of passively advancing from any nearby worker.

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
   - `AttackMove` is now available as a higher-level order.
   - `Hold` is now available as a position-locking order that keeps auto-fire active without chasing.
   - `Patrol` is now available as a repeating two-point movement order.

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
The sample script now includes an `attackMove` order in addition to direct `move`, `attack`, spawn, and harvest flows.

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
- `patrol <x> <y>` alternate movement between the current position and the target point
- `attackMove <x> <y>` move selection while pausing to attack enemies that enter weapon range
  Attack-move now uses a short leash; if combat drags a unit too far from its destination, the order drops instead of chasing indefinitely.
- `hold` keep selection in place while still auto-attacking in weapon range
- `attack <targetId|@label>` attack target
  Attack orders now chase their assigned target through pathfinding until the unit gets into weapon range.
- `harvest <targetId|@label>` assign selection to harvest from a resource node
  Harvested cargo is delivered to the nearest same-faction building footprint.
- `construct <targetId|@label>` assign selected workers to an unfinished building so its construction advances
- `spawnNode [@label] <kind> <x> <y> <amount> [yield]` spawn a resource node (`MineralField`, `GasGeyser`)
  If `yield` is omitted, the node is uncapped and workers harvest at their own rate.
- `spawn [@label] <faction> <typeId> <x> <y> [vision]` spawn a unit
- `build [@label] <faction> <typeId> <tileX> <tileY> [width] [height] [hp] [armor] [minerals] [gas]` place a building footprint
- `cancelBuild <buildingId|@label>` cancel an unfinished building and refund its stored build cost
- `train <buildingId|@label> <typeId> [buildTicks] [minerals] [gas]` enqueue unit production on a building
- `research <buildingId|@label> <techId> [buildTicks] [minerals] [gas]` enqueue faction tech research on a building
- `cancelTrain <buildingId|@label>` cancel the last queued production job and refund its cost
- `cancelResearch <buildingId|@label>` cancel the last queued research job and refund its cost
- `rally <buildingId|@label> <x> <y>` override a producer building rally point for future trained units
- If optional build/train values are omitted or `0`, the sim falls back to typed data defs when available
- Unit and building defs can declare `requiredBuildingTypes`, and both `build` and `train` now fail deterministically with `missingTech` until the faction has those prerequisite buildings
- Unit, building, and research defs can also declare `requiredResearchIds`, which unlock only after that faction finishes the corresponding research queue
- `train` also fails with `underConstruction` when the producer building has not finished building yet
- `cancelResearch` fails with `nothingToCancel` when the building has no queued tech job
- Buildings under construction only advance while a same-faction worker with an active `construct` order is in build range
- `cancelBuild` only works on unfinished structures; completed buildings fail with `notUnderConstruction`
- Building defs can opt into research with `supportsResearch`; tech defs live in `/Users/hammac/Projects/starkraft/sim/src/main/resources/data/techs.json`
- Building defs can also provide `placementClearance`, which reserves a buffer ring around footprints

Other flags:
- `--seed <n>` jitter initial demo spawns deterministically
  Recorded into replay metadata when saving replay files.
  Replay files also store `mapId` and `buildVersion`.
- `--script <path>` run a command script
- `--inputJson <path>` load machine-readable client input as JSON or NDJSON with `commands` and optional `selections`
- `--inputTail <path>` live-read appended NDJSON input records each tick
- Relative script/replay paths are resolved from the project root, so `sim/scripts/sample.script` works under Gradle
- `--scriptDryRun` parse, validate, and print script commands without running
- Script validation now reports specific build-definition errors such as unknown building ids or unresolved width/height/hp defaults

Example client-input file:

```json
{
  "commands": [
    {"tick":0,"commandType":"build","faction":1,"typeId":"Depot","tileX":6,"tileY":6,"label":"depot"},
    {"tick":1,"commandType":"train","buildingLabel":"depot","typeId":"Marine"},
    {"tick":2,"commandType":"rally","buildingLabel":"depot","x":14.0,"y":14.0}
  ]
}
```

You can run the sample with:

`./gradlew :sim:run --args="--inputJson sim/scripts/sample.input.json --noSleep --ticks 20"`

NDJSON is also accepted for append-friendly client pipelines, one `selection` or `command` object per line.
Use `--inputJson -` to read the same JSON or NDJSON payload from stdin.
`--inputTail` is for append-only files from an external client process; each completed line is picked up on the next sim tick.
Input `command` records may include an optional `requestId`; the sim copies it into both `command` and `commandAck` stream records so external clients can correlate submissions and results directly.

Minimal graphical client:

1. Start the sim with streamed snapshots and live input tailing:
   `./gradlew :sim:run --args="--snapshotEvery 1 --snapshotOut /tmp/starkraft/live/snapshots.ndjson --inputTail /tmp/starkraft/live/client-input.ndjson --noSleep --ticks 2000"`
2. Start the client in another terminal:
   `./gradlew :sim:graphicalClient --args="/tmp/starkraft/live/snapshots.ndjson /tmp/starkraft/live/client-input.ndjson"`
   The same client can also connect over TCP sockets:
   `./gradlew :sim:graphicalClient --args="tcp://127.0.0.1:9001 tcp://127.0.0.1:9002"`
   Or over WebSocket endpoints:
   `./gradlew :sim:graphicalClient --args="ws://127.0.0.1:9101 ws://127.0.0.1:9102"`

One-command local play:
- `./gradlew :sim:play`
- Optional custom workspace and tick limit:
  `./gradlew :sim:play --args="/tmp/starkraft/my-play 10000"`
- Optional scenario preset:
  `./gradlew :sim:play --args="/tmp/starkraft/my-play 10000 scripted"`
  Supported presets:
  - `skirmish`
  - `economy`
  - `gas`
  - `scripted`
  Each launch resets the play workspace NDJSON files so the next match starts cleanly.
  While the graphical client is open, press `F5` to restart the current play scenario in the same workspace.
  The play workspace also includes `play-control.txt`, which the client updates for pause and speed control.
  Press `F6` / `F7` in the client to switch scenario and immediately restart into the previous/next preset.

Alternative client transports/renderers:
- Text client over file stream:
  `./gradlew :sim:consoleClient --args="/tmp/starkraft/live/client-input.ndjson /tmp/starkraft/live/snapshots.ndjson"`
- Text client over stdin:
  `tail -f /tmp/starkraft/live/snapshots.ndjson | ./gradlew :sim:consoleClient --args="/tmp/starkraft/live/client-input.ndjson -"`
- Text client over TCP sockets:
  `./gradlew :sim:consoleClient --args="tcp://127.0.0.1:9002 tcp://127.0.0.1:9001"`
- TCP bridge from ndjson files to sockets:
  `./gradlew :sim:tcpClientBridge --args="/tmp/starkraft/live/snapshots.ndjson /tmp/starkraft/live/client-input.ndjson 9001 9002"`
- WebSocket bridge from ndjson files to websocket endpoints:
  `./gradlew :sim:webSocketClientBridge --args="/tmp/starkraft/live/snapshots.ndjson /tmp/starkraft/live/client-input.ndjson 9101 9102"`
The text client frame now includes selected research queue state and the latest streamed research event totals.
The snapshot-stream consumer also summarizes `researchState` records, including unlocked tech count, active research buildings, queued jobs, and active tech ids when present.

Client controls:
- left click: select nearest faction 1 unit
- left drag: box-select faction 1 units
- shift + left click: add/remove the nearest faction 1 unit from the current selection
- shift + left drag: add a box selection to the current selection
- command panel status now includes a `selection hud` line with selected type counts
- command panel status now includes current command mode (e.g. `mode: attack-move`)
- command panel buttons now highlight active mode/view/menu toggles (build mode, view target, help/menu open)
- unavailable command buttons now render dimmed and show tooltip `(unavailable)` context
- command panel status now includes in-panel scenario menu guidance while the menu is open
- hovering command panel buttons now mirrors their tooltip into a `hint:` status line
- command panel status lines are width-fitted with ellipsis to keep the panel readable
- middle drag or `WASD` / arrow keys: pan the camera
- mouse wheel or `+` / `-`: zoom
- `0`: reset camera
- `Space`: pause/resume `:sim:play`
- `[` / `]`: decrease/increase `:sim:play` speed
- `F6` / `F7`: switch `:sim:play` scenario and restart
- `F8`: save the current scenario and speed into the `quick` preset
- `F9`: load the `quick` preset and restart into it
- `Shift+F8`: save into the `alt` preset
- `Shift+F9`: load the `alt` preset and restart into it
- `F10`: open the preset menu, `Up`/`Down` to pick slot, `S` save, `L`/`Enter` load
- `F1`: toggle help overlay with quick control hints
- `F2`: select all units for the currently viewed faction (1/2)
- `F3`: select all units matching the first selected unit type (within viewed faction)
- `F4`: select all units matching the first selected unit role/archetype
- `F11`: select all units from the current snapshot
- `F12`: select idle workers (optionally scoped to viewed faction 1/2)
- `F`: select damaged units (optionally scoped to viewed faction 1/2)
- `V`: select combat-capable units (optionally scoped to viewed faction 1/2)
- `N`: select producer buildings (optionally scoped to viewed faction 1/2)
- `Z`: select buildings that can train units
- `C`: select buildings that can research tech
- `J`: select active construction sites
- `K`: select active harvesters (gather/return workers)
- `Q`: select only returning harvesters
- `E`: select workers currently carrying cargo
- `D`: select resource drop-off buildings
- `Home`: center camera on current selection
- `End`: center camera on viewed faction
- `Shift+4..9`: assign control groups
- `Alt+4..9`: add current selection to control groups
- `4..9`: recall control groups
- `4..9` twice quickly: recall + center camera on that group
- `Alt+0`: clear all control groups
- command panel `groups:` line marks the last recalled group with `*` briefly
- command panel status now shows populated control groups as `groups: 4=...`
- `Tab`: open the in-client scenario chooser, `Up` / `Down` to change, `Enter` to restart into it
- command panel includes `Select View` to do the same faction-wide selection
- command panel includes `Select Type` for type-based selection
- command panel includes `Select Role` for archetype-based selection
- command panel includes `Select All` for full snapshot selection
- command panel includes `Idle Workers` for quick economy control
- command panel includes `Damaged` for quick retreat/regroup control
- command panel includes `Combat` for quick army selection
- command panel includes `Producers` for quick production control
- command panel includes `Trainers` and `Researchers` for queue management
- command panel includes `Construction` to inspect unfinished buildings
- command panel includes `Harvesters` for economy-unit selection
- command panel includes `Returning` to focus returning workers
- command panel includes `Cargo` to focus loaded workers
- command panel includes `Clear Groups` (same effect as `Alt+0`)
- command panel includes `Center` to move camera to current selection
- command panel includes `Center View` to move camera to viewed faction
- command panel includes `View F1/F2/Obs` quick view mode switches
- command panel includes `Dropoffs` to focus deposit buildings
- `1`: view/control faction 1
- `2`: view/control faction 2
- `3`: observer view
- `M`: arm move mode for the next ground right-click
- `A`: arm attack-move mode for the next ground right-click
- `P`: arm patrol mode for the next ground right-click
- `B`: arm `Depot` placement preview
- `R`: arm `ResourceDepot` placement preview
- `G`: arm `GasDepot` placement preview
- `H`: issue hold to the current selection immediately
- `U`: queue `Train Worker` on the first selected training building
- `I`: queue `Train Marine` on the first selected training building
- `O`: queue `Train Zergling` on the first selected training building
- `L`: queue `Research AdvancedTraining` on the first selected research-capable building
- `F5`: restart the current `:sim:play` match
- `X`: cancel build on the first selected construction site
- `T`: cancel train on the first selected producer queue
- `Y`: cancel research on the first selected research queue
- `Esc`: clear selection and reset command mode
- right click enemy: issue `attack`
- right click resource node: issue `harvest`
- right click empty ground: issue `move`
- selected producers render rally markers, and entities now show simple health bars in the graphical client
- selected units with active paths render path-goal markers and a path summary in the HUD
- selected builders and harvesters now render simple assignment lines, and the HUD summarizes build/gather/return tasks
- the command panel also exposes `Prev Scenario` / `Next Scenario` for `:sim:play`
- the command panel also exposes `Pause`, `Slower`, and `Faster` for `:sim:play`
- the command panel header now shows the live play state and current scenario
- hovering command panel buttons shows short tooltip help for the action
- the command panel also exposes `Save/Load Quick` and `Save/Load Alt` preset actions
- an early-match startup overlay now highlights key controls plus current play state/scenario
- preset save/load now shows a short in-client `notice:` status line for success/missing preset feedback
- the panel status now also shows preset slot availability (`quick`/`alt`)
- build/train/research actions in the client are sourced from the sim data defs, rather than being hardcoded only in the UI
- selected buildings now render compact status labels for construction, training, and research progress
- the graphical client now shows a simple victory/defeat overlay when one side has no surviving units left
- the graphical client now darkens tiles outside the current faction 1 vision set and shows a fog visibility summary in the HUD when `vision` stream records are present
- the active view faction changes selection filtering, enemy highlighting, fog rendering, and victory/defeat overlays
- the client now shows build placement preview boxes for supported depot types and only submits build orders on valid tiles
- the build preview also shows structure name, footprint, clearance, and mineral/gas cost next to the ghost
- a simple right-side command panel now exposes clickable buttons for move, attack-move, patrol, hold, build preview modes, and clear
- the command panel also exposes cancel build/train/research actions for quick queue control
- when a selected building supports it, the command panel also exposes `Train Worker`, `Train Marine`, `Train Zergling`, and `Research Adv`
- the graphical client now renders a simple minimap with unit/resource dots and the current camera viewport
- left click on the minimap recenters the camera
- ctrl + right click empty ground: issue `attackMove`

The client consumes `snapshot` and `commandAck` NDJSON records and writes append-only NDJSON commands compatible with `--inputTail`.
Left-click selections are also written as `selectionType="units"` NDJSON records so the input trail includes both client selection changes and commands.
The HUD shows the latest command ack so input failures surface without reading sim logs.
The HUD also shows selected unit type counts, so mixed selections are visible without inspecting the stream.
The NDJSON tail reader and input sink now live in reusable bridge code so a future non-Swing renderer can reuse the same snapshot/input boundary instead of copying file protocol logic.
Client-side click interpretation is also extracted into reusable controller helpers, so a future renderer can share the same selection and right-click command rules without depending on Swing widgets.
Snapshot polling, selection retention, ack tracking, and command submission now also live in a renderer-agnostic client session layer, so Swing is reduced to a view adapter over shared client state.
That session now depends on a stable `ClientStreamSubscription` interface instead of a raw file tail implementation, so alternate transports can plug in without changing session or controller code.
Swing drawing now also sits behind a pluggable `ClientRenderer` adapter, so the current AWT renderer is replaceable without changing the client session or command/controller layers.
The UI timer now drives a renderer-agnostic client app loop, so future frontends can reuse the same poll/update lifecycle without depending on Swing timers directly.
A text-mode client is now available as a second renderer path, and stream subscriptions can come from either file tails or stdin.
Socket transport is now also available over line-delimited TCP for both snapshot subscriptions and command submission, without adding a websocket dependency.
A small TCP bridge is included so existing file-based sim output can be exposed to socket-based clients without changing the headless sim process.
WebSocket transport is now available on the same client bridge abstractions, with a lightweight websocket bridge that exposes the same snapshot/input file pair over `ws://` endpoints.
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
- Snapshots expose faction minerals/gas, unlocked tech ids, per-faction drop-off building counts, resource nodes with remaining amounts, a dedicated `dropoffEntityIds` list, plus entity archetypes, production state, research state, construction progress, active builder assignments, building extents, placement clearance, building capabilities/queue limits, drop-off resource compatibility, default rally offsets, current rally point, and harvester cargo/return state when present
  Periodic summaries now also carry research queue counts plus research failure breakdowns, so HUD-style clients do not need to reconstruct them from raw command failures.
- `--snapshotEvery <n>` stream client snapshots every `n` ticks during the run; respects `--compactJson`
- `--snapshotOut <path>` write typed snapshot NDJSON records (`recordType`, `tick`, `snapshot`) instead of stdout
  NDJSON files begin with a `sessionStart` record carrying `mapId`, `buildVersion`, and `seed`.
  They also emit a `mapState` bootstrap record with blocked tiles, weighted terrain, current static occupancy, and resource nodes.
  Issued commands are also emitted as `command` records.
  Each command also emits a `commandAck` record that references the command record sequence and reports `accepted` or a deterministic `reason`.
  Script-driven selection changes emit `selection` records before commands for that tick.
  Rally overrides emit `rally` records when producer rally points change.
  Invalid rally commands also emit `rallyFailure` records with deterministic reasons.
  Producer-related train failures also emit `producerFailure` records with building/type context.
  Invalid build commands also emit `buildFailure` records with faction/type/tile context.
  Other invalid train commands emit `trainFailure` records with producer/type context.
  Tech-prerequisite failures surface as `missingTech` in those failure records.
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
  Resource depletion retargeting emits `harvesterRetarget` records when workers are reassigned to a fallback node.
  Per-tick resource spends and refunds emit `resourceDelta` records with mineral/gas deltas.
  Per-tick resource deltas also emit `resourceDeltaSummary` records aggregated by faction.
  Snapshot cadence also emits `producerState` records with producer archetypes, capabilities, drop-off resource compatibility, and default rally offsets.
  Snapshot cadence also emits `constructionState` records with unfinished building HP and remaining build ticks, `builderState` records for currently assigned builders, plus `researchState` records with faction unlocked techs and active research queues.
  Snapshot cadence also emits `dropoffState` records with drop-off building ids, archetypes, factions, and positions.
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
  They also include harvest totals for minerals/gas, pickup/deposit cycle counts and amounts, drop-off building counts by faction, mineral/gas drop-off compatibility totals, plus resource-node change and depletion counts.
  `metrics` records also carry faction minerals/gas alongside alive counts and visibility.
  Each NDJSON record also carries a monotonic `sequence` field.
  They end with a `sessionEnd` record carrying final `tick`, `worldHash`, and optional `replayHash`.
- Example consumer:
  `./gradlew :sim:consumeSnapshotStream --args="/tmp/starkraft.ndjson"`
  This reads the NDJSON stream and prints record counts plus session/hash metadata, including resource-node change counts, per-faction harvest splits, active nodes, remaining resources, economy, producer/production, combat, pathing, vision, and archetype selector summaries when present.
  It also summarizes `commandAck` records as accepted/rejected totals plus deterministic rejection reasons when present.
  It also summarizes `dropoffState` records as faction-split drop-off building totals.
  It also summarizes `builderState` records as faction-split active builder counts and unique build targets.
  It also summarizes harvester gather/return phase counts and total carried cargo when `harvesterState` records are present.
  It also summarizes `harvesterRetarget` records as total reassignment events and unique workers retargeted after node depletion.
  When `harvestCycle` records are present, it also prints pickup/deposit event counts and transferred amounts.
  It also summarizes `research` records as `enqueue/progress/complete/cancel` totals.
  Producer summaries include drop-off-capable building counts plus mineral/gas compatibility totals when `dropoffResourceKinds` is present in `producerState`.
  Resource-node totals also drop depleted nodes once a `despawn` record with reason `resourceDepleted` is seen.
- Replay validation/stats warn when replay `mapId` or `buildVersion` differs from the current run
- `--strictReplayMeta` fail on replay `mapId`/`buildVersion` mismatches
- Normal script/replay runs print current runtime metadata (`mapId`, `buildVersion`, `seed`) with the final hashes
- Final CLI outcome summaries now include aggregate harvest totals, per-faction harvest splits, pickup/deposit cycle totals, harvester retarget totals, drop-off building counts by faction, mineral/gas compatibility totals, changed nodes, depletion counts, and current remaining node totals when harvesting occurred
- Depleted resource nodes are removed from the world with despawn reason `resourceDepleted`
- Workers targeting a depleted node are cleared from harvesting, and their pending move-to-node order is dropped
- If another compatible node exists, affected workers are retargeted toward the richest remaining fallback before distance tie-breaking, instead of going idle
- Harvester retargeting now follows resource kind, so depleted mineral nodes can fall through to alternate mineral node types without crossing over to gas
- Human CLI logs now include build/train outcome summaries, including failure reasons when they occur
- Periodic tick logs also include per-tick harvest pickup/deposit cycle totals, harvester retarget totals, current drop-off building counts, and mineral/gas compatibility totals when available
- `--dumpWorldHash` print world hash after a normal run
- `--strictReplayHash` fail if replay is missing a hash
- `--printEntities` dump alive units at the end
- `--printOrders` dump pending orders at the end
- `--replayDump <path>` save a replay after script runs
- `--labelDump` dump label→entity mappings
- Building defs can declare `dropoffResourceKinds`; harvest return targeting prefers resource-compatible depots before generic drop-offs
- Research queue activity now emits typed `research` NDJSON records with `enqueue`, `progress`, `complete`, and `cancel` events

Sample spawn script:
`./gradlew :sim:run --args="--spawnScript sim/scripts/spawn.script --script sim/scripts/sample.script --noSleep"`
This sample now places both a `ResourceDepot` and a `GasDepot`, plus matching mineral and gas nodes.
`--spawnScript` accepts `build` commands too, so drop-off setup can happen before the main script runs.
`sim/scripts/sample.script` now also spawns a faction 2 worker that harvests the spawn script's gas geyser, so the combined demo exercises both drop-off types.
To verify the snapshot stream consumer sees the compatibility data live:
`./gradlew :sim:run --args="--spawnScript sim/scripts/spawn.script --script sim/scripts/sample.script --ticks 260 --noSleep --snapshotEvery 50 --snapshotOut /tmp/starkraft-live-compat.ndjson"`
`./gradlew :sim:consumeSnapshotStream --args="/tmp/starkraft-live-compat.ndjson"`
Expected producer summary includes non-zero compatibility counts, e.g. `producers: total=2 training=1 rally=1 dropoff=2 minerals=2 gas=0 ...`
If a node depletes and a worker is reassigned, the consumer also prints a line like `harvesterRetarget: events=1 workers=1`.
If workers are assigned to construction, the consumer also prints a line like `builders: total=2 f1=2 f2=0 targets=1`.
If build placement and cancellation happened, the consumer also prints a line like `builds: placed=1 canceled=1 fail=1 reasons=invalidPlacement=1`.
If buildings are under construction, the consumer also prints a line like `construction: total=1 f1=1 f2=0 remaining=6`.
The graphical client HUD also shows selected builder assignment state, e.g. `builders: active=1 targets=1`.
The graphical client HUD also shows selected construction progress, e.g. `construction: sites=1 remaining=6 Depotx1`.
The graphical and console clients also show selected production queue state, e.g. `production: labs=1 queue=2 active=Marinex1`.
The graphical client HUD also shows selected research state, e.g. `research: labs=1 queue=2 active=AdvancedTrainingx1`.
The graphical and console clients also show viewed-faction economy state, e.g. `economy: f1 minerals=275 gas=40 dropoffs=1`.
The graphical and console clients also show selected faction mix, e.g. `selection factions: f1=5 f2=1`.
The graphical and console clients also show selected archetype counts, e.g. `selection roles: infantryx4 workerx2`.
The graphical and console clients also show coarse selected class mix, e.g. `selection classes: workers=2 combat=4 structures=1 other=0`.
The graphical and console clients also show selected position centroid/span, e.g. `selection pos: center=5.7,4.0 span=3.0x0.0`.
The graphical and console clients also show selected vision spread, e.g. `selection vision: avg=6.0 min=5.0 max=7.0`.
The graphical and console clients also show selected durability, e.g. `selection durability: avgArmor=0.5 damaged=1/2`.
The graphical and console clients also show selected carried resources, e.g. `selection cargo: loaded=2 minerals=5 gas=3`.
The graphical and console clients also show selected movement posture, e.g. `selection mobility: moving=2 pathing=1 stationary=1`.
The graphical and console clients also show selected weapon mix, e.g. `selection weapons: Riflex3 Cannonx1 unarmed=2`.
The graphical and console clients also show selected path convergence, e.g. `selection paths: active=2 avg=3.0 topGoal=9,9`.
The graphical and console clients also show selected order pressure, e.g. `orders: queued=3 active=movex2 attackMovex1`.
The graphical and console clients also show selected task phases, e.g. `selection phases: gather=1 return=1 build=1 train=1 research=1`.
The graphical and console clients also show selected target fan-out, e.g. `selection targets: build=1 harvestNodes=2 return=1`.
The graphical and console clients also show selected warning signals, e.g. `selection alerts: lowHp=2 idleWorkers=1`.
The graphical and console clients also show selected rally configuration fan-out, e.g. `selection rally: configured=2/3 top=14,10`.
The graphical and console clients also show selected structure footprint totals, e.g. `selection structures: total=2 constructing=1 area=10`.
The graphical and console clients also show selected production/research queue pressure, e.g. `selection queues: prod=2@1 research=3@1`.
The graphical client HUD also shows aggregate selected HP, e.g. `selection hp: 185/465 (39%)`.
The graphical and console clients also show selected combat readiness, e.g. `selection combat: armed=2 ready=1 cooling=1 unarmed=1 nextReady=4`.
The graphical and console clients also show selected capability counts, e.g. `capabilities: train=1 research=1 rally=0 dropoff=1`.
The graphical and console clients also show command affordance toggles, e.g. `commands: move=on train=off research=on viewSelect=on`.
The graphical and console clients also show unlocked faction tech, e.g. `tech: AdvancedTrainingx1`.
The graphical and console clients also show the latest tick-level build/train/research outcome pressure, including failure reasons, e.g. `activity: builds=1/x1 buildFails=2[invalidPlacement=1,insufficientResources=1] train=q2/c1/x1 trainFails=1[queueFull=1] research=q1/c0/x1 researchFails=1[invalidTech=1] @15`.
The graphical client HUD also shows current fog coverage, e.g. `fog: visible=42 hidden=982`.
The console client frame also includes current view scope and selected type counts, e.g. `view=f1 selection hud: Marinex1 Workerx1`.
The console client frame also shows selected builder and construction summaries, e.g. `builders: active=1 targets=1 construction: sites=1 remaining=6 Depotx1`.
The graphical and console clients also show the latest streamed construction totals, e.g. `construction state: total=2 f1=2 f2=0 remaining=10 @15`.
The graphical and console clients also show the latest streamed production event totals, e.g. `production events: e1/p2/c0/x1 @15`.
The graphical client HUD also shows the latest streamed research event totals, e.g. `research events: e1/p2/c0/x1 @15`.

Sample harvest script:
`./gradlew :sim:run --args="--script sim/scripts/harvest.script --ticks 50 --noSleep"`

Sample harvest script with a dedicated drop-off building:
`./gradlew :sim:run --args="--script sim/scripts/harvest-depot.script --ticks 50 --noSleep"`

Sample harvest retarget script:
`./gradlew :sim:run --args="--script sim/scripts/harvest-retarget.script --ticks 5 --noSleep --snapshotEvery 1 --snapshotOut /tmp/starkraft-retarget.ndjson"`
`./gradlew :sim:consumeSnapshotStream --args="/tmp/starkraft-retarget.ndjson"`
This depletes the first node immediately and emits `harvesterRetarget` when the worker is reassigned to `@ore2`.

Sample split economy setup with spawn script + main script:
`./gradlew :sim:run --args="--spawnScript sim/scripts/spawn-harvest.script --script sim/scripts/harvest-main.script --ticks 50 --noSleep"`

Sample split gas economy setup for faction 2:
`./gradlew :sim:run --args="--spawnScript sim/scripts/spawn-harvest-gas.script --script sim/scripts/harvest-gas-main.script --ticks 50 --noSleep"`

Sample gas harvest retarget script:
`./gradlew :sim:run --args="--script sim/scripts/harvest-gas-retarget.script --ticks 5 --noSleep --snapshotEvery 1 --snapshotOut /tmp/starkraft-gas-retarget.ndjson"`
`./gradlew :sim:consumeSnapshotStream --args="/tmp/starkraft-gas-retarget.ndjson"`
This depletes the first geyser immediately and emits `harvesterRetarget` with `resourceKind="gas"` when the worker is reassigned to `@geyser2`.

Sample gas harvest script:
`./gradlew :sim:run --args="--script sim/scripts/harvest-gas.script --ticks 50 --noSleep"`
`./gradlew :sim:run --args="--script sim/scripts/harvest-gas.script --ticks 50 --noSleep --snapshotEvery 1 --snapshotOut /tmp/starkraft-gas.ndjson"`

Sample gas harvest script with a dedicated gas depot:
`./gradlew :sim:run --args="--script sim/scripts/harvest-gas-depot.script --ticks 50 --noSleep"`
