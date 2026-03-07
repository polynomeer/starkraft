# Starkraft Operations Playbook

This runbook covers local host/play/replay verification and fast triage.

## Prerequisites

- JDK 17+
- Go 1.22+
- Unix shell with `bash`

Optional one-shot dependency bootstrap:

```bash
cd /Users/hammac/Projects/starkraft
./scripts/bootstrap_dev.sh
```

## Quick Start (Authoritative Server + 2 Bots)

1. Start server:

```bash
cd /Users/hammac/Projects/starkraft/server
STARKRAFT_SERVER_ADDR=127.0.0.1:18080 \
STARKRAFT_REPLAY_PATH=/tmp/starkraft-room.replay.jsonl \
go run ./cmd/server
```

2. Start bot A:

```bash
cd /Users/hammac/Projects/starkraft/client
go run ./cmd/bot --url ws://127.0.0.1:18080/ws --name bot-a --room smoke
```

3. Start bot B:

```bash
cd /Users/hammac/Projects/starkraft/client
go run ./cmd/bot --url ws://127.0.0.1:18080/ws --name bot-b --room smoke
```

4. Verify replay:

```bash
cd /Users/hammac/Projects/starkraft/server
go run ./cmd/replaycheck --replay /tmp/starkraft-room.replay.jsonl
```

## One-Command Smoke

```bash
cd /Users/hammac/Projects/starkraft
./scripts/e2e_server_bots_smoke.sh
```

If it fails, inspect script output and referenced log files.

## Sim Local Play (Headless + Minimal Graphical Client)

One-command launcher:

```bash
cd /Users/hammac/Projects/starkraft
./scripts/play_sim_graphical.sh
```

1. Start sim snapshot producer:

```bash
cd /Users/hammac/Projects/starkraft
./gradlew :sim:run --args="--snapshotEvery 1 --snapshotOut /tmp/starkraft/live/snapshots.ndjson --inputTail /tmp/starkraft/live/client-input.ndjson --noSleep --ticks 2000"
```

2. Start client:

```bash
cd /Users/hammac/Projects/starkraft
./gradlew :sim:graphicalClient --args="/tmp/starkraft/live/snapshots.ndjson /tmp/starkraft/live/client-input.ndjson"
```

## Health and Admin Visibility

With server running:

```bash
curl -s http://127.0.0.1:18080/healthz | jq .
curl -s http://127.0.0.1:18080/admin/stats | jq .
```

- `/healthz` gives status + room/session counts.
- `/admin/stats` gives room runtime state and resumable session state.

## Determinism and Replay Checks

```bash
cd /Users/hammac/Projects/starkraft
./gradlew :sim:test --tests starkraft.sim.DeterminismTest
./gradlew :sim:test --tests starkraft.sim.ReplayHashTest
./gradlew :sim:test --tests starkraft.sim.ReplayIOTest
```

## Soak / Perf

```bash
cd /Users/hammac/Projects/starkraft
./gradlew :sim:soak --args="--minutes 30 --units 240 --seed 1234 --reportEverySec 30"
```

Track:
- tick p50/p95/p99
- path node usage and queue depth
- memory and GC deltas

## Common Failures

- `ClassNotFoundException: AppKt`:
  - ensure run target is module-qualified (`:sim:run`) and main class is `starkraft.sim.AppKt`.
- `script file not found`:
  - pass project-root-relative path (`sim/scripts/sample.script`) or absolute path.
- server bind/connect permission errors:
  - verify environment allows local sockets/ports.
- replay hash mismatch:
  - run `replaycheck` and inspect replay schema/version and command source.
