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

## Notes

- `sim` is authoritative for game rules/state updates.
- Keep IO/networking outside this module.
- Tick loop target is `Time.TICK_MS = 20`.
