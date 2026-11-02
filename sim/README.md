# sim module

Deterministic fixed-tick RTS simulation core (Kotlin/JVM), plus the libGDX
graphical client, console client, and snapshot/input bridges.

Builds with Kotlin `jvmToolchain(21)`; Gradle needs JDK 17+ to run and the
foojay resolver downloads JDK 21 if it is not installed (CI uses JDK 17).

## Run

```bash
./gradlew :sim:run --args="--ticks 200 --noSleep"
```

Interactive sim + graphical client launcher:

```bash
./gradlew :sim:play
```

Positional `:sim:play` args are `[playDir] [ticks] [scenario]`
(see `scripts/play_sim_graphical.sh`).

Optional play startup tuning:
- `STARKRAFT_PLAY_WAIT_SNAPSHOT_MS` controls how long `:sim:play` waits for initial snapshots (default `5000`, min `250`).
- `STARKRAFT_PLAY_SNAPSHOT_EVERY` controls snapshot write frequency for the play bridge (default `2`, min `1`).

## Test

```bash
./gradlew :sim:build   # build + test (what CI runs)
./gradlew :sim:test
```

Play smoke:

```bash
./scripts/play_sim_smoke.sh
```

Play loop with debug mode on (`STARKRAFT_SIM_DEBUG=1`):

```bash
./scripts/play_sim_profile.sh
```

The current play path keeps the fast baseline by:
- filtering unsupported stream record types before full JSON decoding
- capping file-stream drain work per poll
- using a tighter default snapshot cadence for `:sim:play`

## Visual assets

The graphical client now supports optional sprite-based art assets through
[`sim/src/main/resources/data/visuals.json`](src/main/resources/data/visuals.json).
Place PNGs under
`sim/src/main/resources/assets/` (see [assets/README.md](src/main/resources/assets/README.md))
and map them by unit, building, resource kind, or terrain tile.

Current state: sprites are loaded (`GdxSpriteAssets`) but not drawn. The sprite
draw path was removed in `5149344` (perf: restore fast play baseline), so
`GdxWorldRenderer` renders procedural ShapeRenderer silhouettes only.

## Determinism check

```bash
./gradlew :sim:test --tests starkraft.sim.DeterminismTest
./gradlew :sim:test --tests starkraft.sim.ReplayRunnerTest
./gradlew :sim:test --tests starkraft.sim.ReplayHashTest
./gradlew :sim:test --tests starkraft.sim.ReplayIOTest
```

## Other client tasks

```bash
./gradlew :sim:graphicalClient --args="--headless --headlessTicks 20 <snapshots.ndjson> <input.ndjson>"
./gradlew :sim:consoleClient --args="<input.ndjson|tcp://host:port> [snapshot.ndjson|tcp://host:port|-]"
./gradlew :sim:consumeSnapshotStream --args="<snapshots.ndjson>"
./gradlew :sim:tcpClientBridge --args="<snapshots.ndjson> <input.ndjson> <snapshotPort> <inputPort>"
./gradlew :sim:webSocketClientBridge --args="<snapshots.ndjson> <input.ndjson> <snapshotPort> <inputPort>"
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
