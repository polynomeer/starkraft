# server module

Authoritative multiplayer host.

## Status

Implemented and wired for local play/smoke:
- websocket transport + handshake/resume
- room lifecycle + fixed tick orchestration
- authoritative command validation + ack reasons
- replay persistence (JSONL with optional keyframes)

## Current implementation

- websocket endpoint: `GET /ws`
- health endpoint: `GET /healthz`
- admin stats endpoint: `GET /admin/stats`
- required first message: protocol envelope with `message.type=handshake`
- handshake rejects now close socket with explicit reasons (e.g. `protocol mismatch: upgrade client`, `invalid room id`)
- room create/join by `requestedRoom` (default room if omitted)
- deterministic room snapshot broadcast support
- command queue with deterministic validation acks (`commandAck`)
  - ownership checks
  - bounds checks
  - per-tick rate limiting
  - accepted tick window checks (reject stale/far-future command batches)
- transport/input hardening
  - websocket read-size cap (`MaxReadBytes`, default 64 KiB)
  - command batch cap (`MaxBatchCommands`, default 64)
  - command schema checks for required fields (e.g. move needs `unitIds+x+y`)
  - per-command `unitIds` cap and request-id length cap
  - per-client pending command-batch queue cap
  - bounded outbound websocket queue per client (prevents blocking tick loop)
  - handshake token sanitization for `clientName` and `requestedRoom`
- tick profiling logs
  - periodic server logs include `tick-metrics` with p50/p95/p99 step latency and max pending queue depth

## Run

Optional first-time dependency bootstrap from repo root:

```bash
./scripts/bootstrap_dev.sh
```

```bash
cd server
go run ./cmd/server
```

Replay output (optional):

```bash
STARKRAFT_REPLAY_PATH=/tmp/starkraft-room.replay.jsonl go run ./cmd/server
```

Runtime tuning env vars:
- `STARKRAFT_EMPTY_ROOM_TTL` (duration, default `30s`)
- `STARKRAFT_RESUME_WINDOW` (duration, default `15s`)
- `STARKRAFT_KEYFRAME_EVERY` (int ticks, default `50`)

Operational endpoints:
- `GET /healthz`:
  - lightweight readiness/status payload (`status`, protocol, sim version, room/session counts)
- `GET /admin/stats`:
  - room-level runtime snapshot (`tick`, world hash, unit/client/pending counts, match state)
  - resumable session snapshot (token, room/client linkage, remaining expiry)

Replay verifier:

```bash
cd server
go run ./cmd/replaycheck --replay /tmp/starkraft-room.replay.jsonl
```

`replaycheck` validates:
- record ordering (`header` first, keyframe/command tick monotonicity)
- structural counts (single header, max one matchEnd)
- keyframe `worldHash` consistency against recorded unit snapshots

## Run tests

```bash
cd server
go test ./...
```
