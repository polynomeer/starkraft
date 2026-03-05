# tools module

Offline utility CLIs for replay/map/data workflows.

## Run

- `./gradlew :tools:run --args="replay meta <path>"`
- `./gradlew :tools:run --args="replay fast-forward <path> [--ticks N]"`
- `./gradlew :tools:run --args="replay verify <path> [--ticks N] [--strictHash]"`
- `./gradlew :tools:run --args="map validate <map.json>"`
- `./gradlew :tools:run --args="map generate <map.json> --width 64 --height 64 --seed 1337"`
- `./gradlew :tools:run --args="data validate --dir sim/src/main/resources/data"`

Paths are resolved relative to the repository root when passed as relative paths.

## Commands

- `replay meta`: print replay metadata (`schema`, hash, seed, map/build tags, size)
- `replay fast-forward`: run replay commands through the sim stack and print final world hash
- `replay verify`: compare stored replay hash to computed replay hash, then re-run sim and print world hash
- `map validate`: validate map schema and bounds/cost/resource/spawn constraints
- `map generate`: generate a basic map JSON with blocked and weighted tiles
- `data validate`: validate units/buildings/tech/weapons JSON or YAML against schema + reference rules

## Schemas

- `tools/schema/map-v1.schema.json`: map payload structure for generated/validated map files
- `tools/schema/data-defs-v1.schema.json`: top-level data defs container shape
