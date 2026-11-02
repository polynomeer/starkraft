# Starkraft Operations Playbook

This runbook covers local host/play/replay verification and fast triage.

## Prerequisites

- JDK 17+ to launch Gradle (CI uses Temurin 17). `sim` and `tools` compile with `jvmToolchain(21)`; if no local JDK 21 is found, Gradle auto-provisions one via the foojay resolver (needs network on first build).
- Go 1.22+ (`go.mod` and CI use 1.22)
- Unix shell with `bash`
- `curl` or `python3` (script readiness checks against `/healthz`)
- `rg` (ripgrep) for `scripts/e2e_server_bots_smoke.sh` replay/log assertions

Optional one-shot dependency bootstrap:

```bash
# from repo root
./scripts/bootstrap_dev.sh
```

## Quick Start (Authoritative Server + 2 Bots)

Bots and the CLI client send `simVersion=dev` by default; pass `--simVersion <tag>` to change it (`./scripts/play_stack_local.sh` forwards `STARKRAFT_PLAY_SIM_VERSION`). The server only rejects empty/whitespace values; it does not require client and server tags to match.

Server env vars (`server/cmd/server`, whitespace-only values fall back to defaults):

- `STARKRAFT_SERVER_ADDR` (default `:8080`)
- `STARKRAFT_SIM_VERSION` (default `dev`; server envelope + replay header tag)
- `STARKRAFT_BUILD_HASH` (optional; adds `buildHash` to envelopes and replay header)
- `STARKRAFT_REPLAY_PATH` (optional; JSONL replay, truncated on each server start)
- `STARKRAFT_KEYFRAME_EVERY` (default `50` ticks; `0` disables keyframes)
- `STARKRAFT_EMPTY_ROOM_TTL` (default `30s`)
- `STARKRAFT_RESUME_WINDOW` (default `15s`; counted from disconnect, a connected session never expires)
- `STARKRAFT_ADMIN_TOKEN` (optional; when set, `/admin/stats` requires `Authorization: Bearer <token>`; when empty, it only answers loopback clients)

Client/bot flags: `--url` (default `ws://127.0.0.1:8080/ws`), `--name`, `--room` (default `default`), `--simVersion` (default `dev`), `--resumeToken`; the CLI client also takes `--script <json>`.

1. Start server:

```bash
cd server
STARKRAFT_SERVER_ADDR=127.0.0.1:18080 \
STARKRAFT_REPLAY_PATH=/tmp/starkraft-room.replay.jsonl \
go run ./cmd/server
```

2. Start bot A:

```bash
cd client
go run ./cmd/bot --url ws://127.0.0.1:18080/ws --name bot-a --room smoke
```

3. Start bot B:

```bash
cd client
go run ./cmd/bot --url ws://127.0.0.1:18080/ws --name bot-b --room smoke
```

4. Verify replay:

```bash
cd server
go run ./cmd/replaycheck --replay /tmp/starkraft-room.replay.jsonl
```

## One-Command Smoke

```bash
# from repo root
./scripts/e2e_server_bots_smoke.sh
```

Starts the server with a replay file, runs bots `bot-a`/`bot-b` in room `smoke` until match end, asserts the replay contains `header`/`command`/`matchEnd` records, then runs `replaycheck`.

Optional:
- `STARKRAFT_E2E_SIM_VERSION=<tag>` to set bot handshake `simVersion` in this smoke.
- `STARKRAFT_E2E_PORT` (default `18080`), `STARKRAFT_E2E_TMP_DIR` (default `mktemp -d /tmp/starkraft-e2e-XXXXXX`).
- empty/whitespace sim version env values are rejected before launch.

Sim play pipeline smoke:

```bash
# from repo root
./scripts/play_sim_smoke.sh
```

If your machine is slow to produce the first snapshots, increase play startup wait (`STARKRAFT_PLAY_WAIT_SNAPSHOT_MS`, default `5000`, minimum `250`):

```bash
# from repo root
STARKRAFT_PLAY_WAIT_SNAPSHOT_MS=15000 ./scripts/play_sim_graphical.sh
```

If it fails, inspect script output and referenced log files.

## Sim Local Play (Headless + Minimal Graphical Client)

One-command launcher (runs `:sim:play`; env `STARKRAFT_SIM_PLAY_DIR` default `/tmp/starkraft-play`, `STARKRAFT_SIM_PLAY_TICKS` default `5000`, `STARKRAFT_SIM_PLAY_SCENARIO` default `skirmish`):

```bash
# from repo root
./scripts/play_sim_graphical.sh
```

1. Start sim snapshot producer:

```bash
# from repo root
./gradlew :sim:run --args="--snapshotEvery 1 --snapshotOut /tmp/starkraft/live/snapshots.ndjson --inputTail /tmp/starkraft/live/client-input.ndjson --noSleep --ticks 2000"
```

2. Start client:

```bash
# from repo root
./gradlew :sim:graphicalClient --args="/tmp/starkraft/live/snapshots.ndjson /tmp/starkraft/live/client-input.ndjson"
```

## Health and Admin Visibility

With server running:

```bash
curl -s http://127.0.0.1:18080/healthz | jq .
curl -s http://127.0.0.1:18080/admin/stats | jq .   # add -H "Authorization: Bearer $STARKRAFT_ADMIN_TOKEN" when set
```

- `/healthz` gives `status`, `protocolVersion`, `simVersion`, `rooms`, `resumeSessions` (counts).
- `/admin/stats` gives `protocolVersion`, `simVersion`, `buildHash`, `tickIntervalMs`, per-room runtime state (`tick`, `worldHash`, `units`, `connectedClients`, `pendingBatches`, `matchEnded`, `winnerId`, `emptyForMs`) and resumable sessions (`clientId`, `clientName`, `roomId`, `connected`, `expiresInMs` once disconnected). Tokens are never included. Without `STARKRAFT_ADMIN_TOKEN` it returns 403 to non-loopback clients; with it, a missing or wrong bearer token returns 401.
- Both are GET-only (other methods return 405). There is no separate metrics endpoint; the server logs a `tick-metrics` line (p50/p95/p99 step latency, max pending batches) every 200 ticks.
- Note: the server room runs its own minimal Go unit model (`server/pkg/authoritative/room.go`), not the Kotlin sim, so its `worldHash` is not comparable with sim hashes.

## Determinism and Replay Checks

```bash
# from repo root
./gradlew :sim:test --tests starkraft.sim.DeterminismTest
./gradlew :sim:test --tests starkraft.sim.ReplayHashTest
./gradlew :sim:test --tests starkraft.sim.ReplayIOTest
```

## Soak / Perf

```bash
# from repo root
./gradlew :sim:soak --args="--minutes 30 --units 240 --seed 1234 --reportEverySec 30"
```

Track:
- tick p50/p95/p99
- path node usage and queue depth
- memory and GC deltas

## Common Failures

- `ClassNotFoundException: AppKt`:
  - ensure run target is module-qualified (`:sim:run`) and main class is `starkraft.sim.AppKt`.
- `missing go.sum entry for module ... gorilla/websocket`:
  - run `./scripts/bootstrap_dev.sh` once, or:
    - `cd server && go mod download`
    - `cd client && go mod download`
- `script file not found`:
  - pass project-root-relative path (`sim/scripts/sample.script`) or absolute path.
- graphical client stuck on `waiting for snapshots...`:
  - run `./scripts/play_sim_graphical.sh` from repo root.
  - if sim startup fails, launcher exits with `sim startup failed: no snapshots produced within <N>ms (exitCode=...)` (N = `STARKRAFT_PLAY_WAIT_SNAPSHOT_MS`, default 5000); run:
    - `./gradlew :sim:run --args="--ticks 60 --snapshotEvery 1 --snapshotOut /tmp/starkraft-check.ndjson"`
- server bind/connect permission errors:
  - verify environment allows local sockets/ports.
- replay hash mismatch:
  - server JSONL replay: run `replaycheck` (and `./gradlew :tools:run --args="replay verify-ndjson <file> --json"`) and inspect header version and command source.
  - sim JSON replay: run `./gradlew :tools:run --args="replay verify <file> --strictHash"`.
  - do not compare server keyframe hashes with sim world hashes; they come from different models.
