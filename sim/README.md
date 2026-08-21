# sim module

Deterministic fixed-tick RTS simulation core (Kotlin/JVM).

## Run

```bash
./gradlew :sim:run --args="--ticks 200 --noSleep"
```

Interactive sim + graphical client launcher:

```bash
./gradlew :sim:play
```

Optional play startup tuning:
- `STARKRAFT_PLAY_WAIT_SNAPSHOT_MS` controls how long `:sim:play` waits for initial snapshots (default `5000`, min `250`).
- `STARKRAFT_PLAY_SNAPSHOT_EVERY` controls snapshot write frequency for the play bridge (default `2`, min `1`).

## Test

```bash
./gradlew :sim:test
```

Play smoke:

```bash
./scripts/play_sim_smoke.sh
```

Profiled play loop with in-game debug overlay:

```bash
./scripts/play_sim_profile.sh
```

The current play path keeps the fast baseline by:
- filtering unsupported stream record types before full JSON decoding
- capping file-stream drain work per poll
- using a tighter default snapshot cadence for `:sim:play`

## Visual assets

The graphical client now supports optional sprite-based art assets through
[`/Users/hammac/Projects/starkraft/sim/src/main/resources/data/visuals.json`](/Users/hammac/Projects/starkraft/sim/src/main/resources/data/visuals.json).
Place PNGs under
[`/Users/hammac/Projects/starkraft/sim/src/main/resources/assets/README.md`](/Users/hammac/Projects/starkraft/sim/src/main/resources/assets/README.md)
and map them by unit, building, resource kind, or terrain tile. Missing files
automatically fall back to the existing procedural renderer.

## Determinism check

```bash
./gradlew :sim:test --tests starkraft.sim.DeterminismTest
./gradlew :sim:test --tests starkraft.sim.ReplayRunnerTest
```

## Benchmark / Soak

```bash
./gradlew :sim:benchmark
./gradlew :sim:pathfindingBenchmark
./gradlew :sim:soak --args="--minutes 30 --units 120 --seed 1234 --reportEverySec 10"
```

The soak harness prints:
- tick latency percentiles (`p50/p95/p99/max`)
- pathfinding budget pressure (`pathNodes`, queue/carry-over)
- memory and GC deltas
- JFR `jcmd` hint for profile captures

## Notes

- `sim` is authoritative for game rules/state updates.
- Keep IO/networking outside this module.
- Tick loop target is `Time.TICK_MS = 20`.
