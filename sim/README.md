# sim module

Deterministic fixed-tick RTS simulation core (Kotlin/JVM).

## Run

```bash
./gradlew :sim:run --args="--ticks 200 --noSleep"
```

## Test

```bash
./gradlew :sim:test
```

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
