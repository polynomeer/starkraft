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

13. **Unit Behaviors**
    - Add higher-level orders like `AttackMove`, `Hold`, `Patrol` by composing
      move + attack decisions at the order layer.

14. **Networking / Replay**
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
- `move <x> <y>` move selection
- `attack <targetId>` attack target
- `spawn <faction> <typeId> <x> <y> [vision]` spawn a unit

Other flags:
- `--seed <n>` jitter initial demo spawns deterministically
- `--script <path>` run a command script
- `--spawnScript <path>` run a spawn-only script before other commands
- `--replayOut <path>` save recorded commands after a run
- `--replayValidateOnly` load/validate replay and exit
- `--dumpWorldHash` print world hash after a normal run
- `--strictReplayHash` fail if replay is missing a hash
- `--printEntities` dump alive units at the end
