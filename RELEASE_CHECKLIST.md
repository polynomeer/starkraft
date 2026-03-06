# Starkraft Release Checklist (v1 Gate)

Use this checklist before tagging a release.

## Build and Test

- [ ] `./gradlew :sim:build`
- [ ] `cd /Users/hammac/Projects/starkraft/server && go test ./...`
- [ ] `cd /Users/hammac/Projects/starkraft/client && go test ./...`
- [ ] `cd /Users/hammac/Projects/starkraft && ./scripts/e2e_server_bots_smoke.sh`

## Determinism and Replay

- [ ] `./gradlew :sim:test --tests starkraft.sim.DeterminismTest`
- [ ] `./gradlew :sim:test --tests starkraft.sim.ReplayHashTest`
- [ ] `./gradlew :sim:test --tests starkraft.sim.ReplayIOTest`
- [ ] replay verifier pass:
  - `cd /Users/hammac/Projects/starkraft/server`
  - `go run ./cmd/replaycheck --replay <replay.jsonl>`

## Soak and Performance

- [ ] `./gradlew :sim:soak --args="--minutes 30 --units 240 --seed 1234 --reportEverySec 30"`
- [ ] p95/p99 tick times remain within expected budget
- [ ] no sustained memory growth/regression versus previous release

## Packaging

- [ ] `./scripts/release_package.sh /tmp/starkraft-release vX.Y.Z`
- [ ] verify generated manifest and checksums
- [ ] confirm artifacts include sim/server/client/tools distributions

## Documentation

- [ ] update `/Users/hammac/Projects/starkraft/CHANGELOG.md`
- [ ] verify `/Users/hammac/Projects/starkraft/OPERATIONS.md` is current
- [ ] verify module READMEs match current run/test commands

## Protocol and Compatibility

- [ ] protocol changes reviewed for backward compatibility
- [ ] shared protocol schema/golden files updated when required
- [ ] replay/schema compatibility notes captured in changelog
