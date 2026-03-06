# tools module

Offline utility CLIs for replay/map/data workflows.

## Run

- `./gradlew :tools:run --args="replay meta <path>"`
- `./gradlew :tools:run --args="replay verify <path> [--strictHash]"`
- `./gradlew :tools:run --args="replay fast-forward <path> [--ticks N]"`
- `./gradlew :tools:run --args="map validate <map.json>"`
- `./gradlew :tools:run --args="map generate <map.json> --width 64 --height 64 --seed 1337"`
- `./gradlew :tools:run --args="data validate --dir sim/src/main/resources/data"`

Paths are resolved relative to the repository root when passed as relative paths.

## Commands

- `replay meta`: print replay metadata (`schema`, hash, seed, map/build tags, size)
- `replay verify`: compare stored replay hash to computed replay hash
  - returns non-zero for hash mismatch or strict-hash validation errors
  - also runs replay through sim systems and prints final world hash
- `replay fast-forward`: run replay commands through sim systems and print final world hash
- `map validate`: validate map JSON shape and tile/resource/spawn bounds
- `map generate`: generate a deterministic starter map JSON
- `data validate`: validate units/buildings/techs/weapons IDs and cross references

## Schemas

- `tools/schema/map-v1.schema.json`: map payload structure for generated/validated map files
- `tools/schema/data-defs-v1.schema.json`: top-level data defs container shape
