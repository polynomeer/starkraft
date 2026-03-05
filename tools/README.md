# tools module

Offline utility CLIs for replay/map/data workflows.

## Run

- `./gradlew :tools:run --args="replay meta <path>"`
- `./gradlew :tools:run --args="replay fast-forward <path> [--ticks N]"`
- `./gradlew :tools:run --args="replay verify <path> [--ticks N] [--strictHash]"`

## Commands

- `replay meta`: print replay metadata (`schema`, hash, seed, map/build tags, size)
- `replay fast-forward`: run replay commands through the sim stack and print final world hash
- `replay verify`: compare stored replay hash to computed replay hash, then re-run sim and print world hash
