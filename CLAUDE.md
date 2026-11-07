# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Commit/PR/testing rules live in @AGENTS.md (Conventional Commits, subject ≤ 72 chars, imperative). Commit scopes in use: `sim`, `client`, `ui`, `server`, `tools`, `replay`, `ci`, `readme`. Note that `client`/`ui` usually mean the Kotlin libGDX client inside `sim/`, not the Go `client/` module.

## Project

The product name is 삼재 (SAMJAE); `starkraft` remains the repo and code name. It is a 1v1 RTS sandbox built around a deterministic fixed-tick simulation (`Time.TICK_MS = 20`). Most project docs (`README.md`, `docs/*.md`) are in Korean; `docs/README.md` is the doc index.

Modules:
- `sim/` — Kotlin/JVM (JDK 21 toolchain): simulation core, headless runner, benchmarks, **and** the libGDX graphical client + console client.
- `tools/` — Kotlin CLI for replay / map / data validation (`starkraft.tools.ToolsCliKt`).
- `server/` — Go authoritative websocket server (`cmd/server`) plus `cmd/replaycheck`.
- `client/` — Go headless CLI client (`cmd/client`) and bot (`cmd/bot`).
- `shared-protocol/` — JSON schema + golden envelopes for wire protocol v1.
- `core/`, `core.tests/`, `content/` — C# rewrite of the rule core in progress, see `docs/p1-core-port-plan.md`. `CoreBoundaryTests` bans floating point, IO and engine use in `core/`.
- `spike/` — P0 Godot baseline scene and its own older core copy. Kept for re-measuring until P2 starts; do not build on it.

Gradle includes only `sim` and `tools`; `server` and `client` are independent Go modules (run `go` commands inside each directory).

## Commands

```bash
./scripts/bootstrap_dev.sh                       # first-time setup (gradle wrapper + go mod download)
./scripts/smoke_run.sh                           # fastest end-to-end sanity check (sim build + deterministic run)

./gradlew :sim:build                             # build + test sim (what CI runs)
./gradlew :sim:test
./gradlew :sim:test --tests starkraft.sim.DeterminismTest            # single test class
./gradlew :sim:test --tests 'starkraft.sim.AppTest.renders replay*'  # single test (backtick names, use wildcards)
./gradlew :tools:build
./gradlew :tools:run --args="replay stats <file> --json"
./gradlew :tools:smoke                           # tools JSON-contract health check (scripts/tools_health.sh)

./gradlew :sim:run --args="--ticks 200 --noSleep [--dumpWorldHash] [--snapshotEvery N --snapshotOut f.ndjson] [--script sim/scripts/sample.script] [--replayDump f.json]"
./gradlew :sim:play                              # sim + libGDX window together (opens a GUI)
./gradlew :sim:graphicalClient --args="--headless --headlessTicks 20 <snapshots.ndjson> <input.ndjson>"
./gradlew :sim:benchmark | :sim:pathfindingBenchmark | :sim:soak

cd server && go build ./... && go test ./... && go vet ./...
cd server && go test ./pkg/authoritative -run TestName              # single Go test
cd client && go test ./... && go vet ./...
# CI also enforces: go mod tidy leaves go.mod/go.sum unchanged

dotnet test --project core.tests                 # C# rule core tests (.NET 10 SDK)
dotnet test --project core.tests --filter-class Samjae.Core.Tests.FixTests  # single class

./scripts/e2e_server_bots_smoke.sh               # server + 2 bots + replaycheck
./scripts/play_sim_smoke.sh                      # headless check of the :sim:play pipeline
./scripts/play_stack_local.sh                    # server + optional bot + interactive CLI
```

CI (`.github/workflows/ci.yml`) also runs `bash -n` on most scripts in `scripts/` (not `play_sim_profile.sh` / `release_package.sh`) and checks that scripts reject whitespace-only `STARKRAFT_*_SIM_VERSION` env values — keep those guard rails when editing scripts.

## Architecture

### Sim (`sim/src/main/kotlin`)

- Source directories do **not** mirror packages: `sim/src/main/kotlin/ecs/World.kt` is `package starkraft.sim.ecs`, `App.kt` is `starkraft.sim` (main class `starkraft.sim.AppKt`). Tests live under `sim/src/test/kotlin/starkraft/sim/`.
- `App.kt` (~6k lines) owns CLI parsing and the tick loop. Each tick: drain input (script/replay commands, then live input tailed from an NDJSON file) → `harvest` → `occupancy` → `construction` → `research` → `production` → `pathing` → `movement` → `combat` → `victory` → `vision`, emitting typed NDJSON stream records after each system. **System order and iteration order are part of determinism** — changing them changes world/replay hashes.
- `ecs/` holds components, `World`, and the systems; `ecs/path/` A* pathfinding with per-tick budget; `ecs/services/Fog.kt` vision.
- Game data (units, buildings, weapons, techs, visuals) is JSON in `sim/src/main/resources/data/`, loaded by `data/DataRepo.kt`; `tools data validate` and `tools/schema/data-defs-v1.schema.json` check it.
- `net/` = command model, input JSON parsing, input tail reader, `.script` runner (`sim/scripts/*.script`). `replay/` = recorder, replay IO, replay hash.
- Rule: game rules stay in `sim`; socket IO, room/account management, rendering, and UI state stay out of the sim core.

### Local play pipeline (file-based, no server)

`:sim:play` (`client/PlayLauncher.kt`) runs the sim writing a snapshot NDJSON stream; the graphical client (`client/GdxClientRuntime.kt`, `GameScreen.kt`, `GdxWorldRenderer.kt`) consumes it via `SnapshotStreamConsumer` → `ClientSnapshot` and writes player commands to an input NDJSON that the sim tails on the next tick. `ClientTcpBridge`/`ClientWebSocketBridge` expose the same two files over sockets. Tunables: `STARKRAFT_PLAY_WAIT_SNAPSHOT_MS`, `STARKRAFT_PLAY_SNAPSHOT_EVERY`. `data/visuals.json` sprites are loaded but not drawn (draw path removed in `5149344`); the world is rendered with procedural ShapeRenderer shapes only. Audio is synthesized tones generated at startup (`GdxUiAssets`), with no audio files.

Client input/HUD behavior is specified in `COMMAND_CONTRACT.md` (armed-command enter/confirm/cancel rules) and `docs/hud-layout-contract.md`, `docs/input-mapping-spec.md`.

### Network stack (Go)

- `server/pkg/authoritative`: websocket ingress, handshake/resume (`resumeToken`), room lifecycle and eviction, per-tick command batch validation (ownership, size caps, rate limits), snapshot broadcast, replay JSONL (`header`/`command`/`keyframe`/`matchEnd`). HTTP routes are only `/ws`, `/healthz`, `/admin/stats` (bearer `STARKRAFT_ADMIN_TOKEN`, or loopback-only when unset); metrics are `tick-metrics` log lines, not an endpoint.
- Caveat: docs describe the server as advancing `sim` state, but `room.go` currently keeps its **own minimal Go unit model and world hash** (move/attack/build/queue/surrender); it does not call the Kotlin sim. Don't assume server and sim hashes are comparable.
- Server env: `STARKRAFT_SERVER_ADDR`, `STARKRAFT_REPLAY_PATH` (see `OPERATIONS.md`).

### Protocol contract

Wire models are duplicated in three places: `server/pkg/protocol`, `client/pkg/protocol`, and `sim/src/main/kotlin/starkraft/sim/protocol/ProtocolModels.kt`. Go tests in both modules compare marshaled output byte-for-byte against `shared-protocol/golden/*.json`. A protocol change means updating the schema, goldens, and all three model copies together (see `docs/protocol-spec.md`, `docs/replay-format-spec.md`).

`tools` output is versioned against `tools/schema/tools-output-v1.schema.json`; CI asserts `result == "ok"` and specific keys in the JSON — keep the output shape stable.

## Docs maintenance

`docs/document-maintenance-policy.md` and the PR template expect related docs to be updated with behavior/contract changes (sim → `simulation-architecture.md`/`balance-spec.md`; server/protocol → `protocol-spec.md`/`security-validation-spec.md`/`replay-format-spec.md`; client/UX → `ui-ux-spec.md`/`input-mapping-spec.md`).
