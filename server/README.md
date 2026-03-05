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
  - per-command `unitIds` cap and request-id length cap
  - per-client pending command-batch queue cap
  - handshake token sanitization for `clientName` and `requestedRoom`

## Run

```bash
cd server
go run ./cmd/server
```

Replay output (optional):

```bash
STARKRAFT_REPLAY_PATH=/tmp/starkraft-room.replay.jsonl go run ./cmd/server
```

## Run tests

```bash
cd server
go test ./...
```
