# Starkraft Release Checklist (v1 Gate)

Use this checklist before tagging a release.

## Build and Test

Run from repo root.

- [ ] `./gradlew :sim:build`
- [ ] `./gradlew :tools:build` and `./gradlew :tools:smoke`
- [ ] `cd server && go build ./... && go test ./... && go vet ./...`
- [ ] `cd client && go build ./... && go test ./... && go vet ./...`
- [ ] `go mod tidy` leaves `go.mod`/`go.sum` unchanged in `server/` and `client/` (CI enforces this)
- [ ] `./scripts/e2e_server_bots_smoke.sh`

## Determinism and Replay

- [ ] `./gradlew :sim:test --tests starkraft.sim.DeterminismTest`
- [ ] `./gradlew :sim:test --tests starkraft.sim.ReplayHashTest`
- [ ] `./gradlew :sim:test --tests starkraft.sim.ReplayIOTest`
- [ ] replay verifier pass:
  - `cd server && go run ./cmd/replaycheck --replay <replay.jsonl>` (server JSONL replay)
  - `./gradlew :tools:run --args="replay verify-ndjson <replay.jsonl> --json"` reports `result: ok`
  - sim JSON replays: `./gradlew :tools:run --args="replay verify <replay.json> --strictHash"`

## Soak and Performance

- [ ] `./gradlew :sim:soak --args="--minutes 30 --units 240 --seed 1234 --reportEverySec 30"`
- [ ] p95/p99 tick times remain within expected budget
- [ ] no sustained memory growth/regression versus previous release

## Packaging

- [ ] `./scripts/release_package.sh /tmp/starkraft-release vX.Y.Z` (requires `go` on PATH, otherwise Go binaries are silently skipped)
- [ ] verify generated `manifest-vX.Y.Z.txt` (`version`, `protocolVersion`, `simVersion`, `buildDateUtc`); `protocolVersion`/`simVersion` are hardcoded in the script, so update them if they changed
- [ ] generate checksums manually (e.g. `shasum -a 256 /tmp/starkraft-release/*`); the script does not emit them
- [ ] confirm artifacts include sim zip, server binary, client CLI and bot binaries (the script does not package `tools`)

## Documentation

- [ ] update `CHANGELOG.md`
- [ ] verify `OPERATIONS.md` is current
- [ ] verify module READMEs match current run/test commands

## Protocol and Compatibility

- [ ] protocol changes reviewed for backward compatibility
- [ ] shared protocol schema/golden files updated when required, together with all three model copies (`server/pkg/protocol`, `client/pkg/protocol`, `sim/src/main/kotlin/starkraft/sim/protocol/ProtocolModels.kt`)
- [ ] replay/schema compatibility notes captured in changelog
