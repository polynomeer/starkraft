# tools module

Offline utility CLIs for replay/map/data workflows.

## Run

- `./gradlew :tools:run --args="replay meta <path> [--json]"`
- `./gradlew :tools:run --args="replay stats <path> [--json]"`
- `./gradlew :tools:run --args="replay verify-ndjson <path> [--json]"`
- `./gradlew :tools:run --args="replay verify <path> [--strictHash] [--json]"`
- `./gradlew :tools:run --args="replay fast-forward <path> [--ticks N] [--json]"`
- `./gradlew :tools:run --args="map validate <map.json> [--json]"`
- `./gradlew :tools:run --args="map generate <map.json> --width 64 --height 64 --seed 1337 [--json]"`
- `./gradlew :tools:run --args="data validate --dir sim/src/main/resources/data [--json]"`

Paths are resolved relative to the repository root when passed as relative paths.

Full tools smoke run (JSON contracts + assertions):
- `./scripts/tools_health.sh`

## Commands

- `replay meta`: print replay metadata (`schema`, hash, seed, map/build tags, size)
  - `--json` prints machine-readable single-line JSON
- `replay stats`: print replay record counts
  - supports sim replay JSON and server replay NDJSON
  - for server NDJSON, also reports keyframe hash mismatches (non-zero on mismatch)
  - `--json` prints machine-readable single-line JSON
- `replay verify-ndjson`: verify server NDJSON keyframe `worldHash` values
  - `--json` prints machine-readable single-line JSON
- `replay verify`: compare stored replay hash to computed replay hash
  - returns non-zero for hash mismatch or strict-hash validation errors
  - also runs replay through sim systems and prints final world hash
  - `--json` prints machine-readable single-line JSON
- `replay fast-forward`: run replay commands through sim systems and print final world hash
  - `--json` prints machine-readable single-line JSON
- `map validate`: validate map JSON/YAML shape and tile/resource/spawn bounds
  - `--json` prints machine-readable single-line JSON
  - invalid results include `firstError`
- `map generate`: generate a deterministic starter map JSON
  - `--json` prints machine-readable single-line JSON
- `data validate`: validate units/buildings/techs/weapons JSON/YAML IDs and cross references
  - `--json` prints machine-readable single-line JSON
  - invalid results include `firstError`

## Schemas

- `tools/schema/map-v1.schema.json`: map payload structure for generated/validated map files
- `tools/schema/data-defs-v1.schema.json`: top-level data defs container shape
