# server module

Authoritative multiplayer host (in progress).

## Status

Protocol package scaffold is implemented.

## Planned responsibilities

- websocket transport and handshake
- room lifecycle and fixed tick orchestration
- authoritative command validation
- replay persistence

## Current implementation

- websocket endpoint: `GET /ws`
- required first message: protocol envelope with `message.type=handshake`
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

```bash
cd server
go run ./cmd/server
```

Replay output (optional):

```bash
STARKRAFT_REPLAY_PATH=/tmp/starkraft-room.replay.jsonl go run ./cmd/server
```

Replay verifier:

```bash
cd server
go run ./cmd/replaycheck --replay /tmp/starkraft-room.replay.jsonl
```

## Run tests

```bash
cd server
go test ./...
```
