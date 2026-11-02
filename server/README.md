# server module

Authoritative multiplayer host.

## Related docs

- [protocol-spec.md](../docs/protocol-spec.md)
- [server-ops-runbook.md](../docs/server-ops-runbook.md)
- [security-validation-spec.md](../docs/security-validation-spec.md)
- [replay-format-spec.md](../docs/replay-format-spec.md)
- [replay-validation-checklist.md](../docs/replay-validation-checklist.md)
- [release-artifact-spec.md](../docs/release-artifact-spec.md)

## Status

Implemented and wired for local play/smoke:
- websocket transport + handshake/resume
- room lifecycle + fixed tick orchestration
- authoritative command validation + ack reasons
- replay persistence (JSONL with optional keyframes)

Note: the room loop (`pkg/authoritative/room.go`) runs its own minimal Go unit
model (`move`/`attack`/`build`/`queue`/`surrender`) and its own world hash. It
does not call the Kotlin `sim`, so server replay hashes are not comparable with
`sim` world/replay hashes.

## Current implementation

- websocket endpoint: `GET /ws`
- health endpoint: `GET /healthz`
- admin stats endpoint: `GET /admin/stats`
- required first message: protocol envelope with `message.type=handshake`
- protocol envelope requires non-empty `simVersion` (empty value is rejected with close reason `invalid sim version`)
- handshake rejects now close socket with explicit reasons (e.g. `protocol mismatch: upgrade client`, `invalid room id`)
- post-handshake protocol mismatches also close with explicit websocket reasons (`upgrade client` / `upgrade server`)
- handshake ack includes `resumeToken`; reconnecting clients can send it via handshake `resumeToken` to restore identity within `STARKRAFT_RESUME_WINDOW`
- room create/join by `requestedRoom` (default room if omitted)
- deterministic room snapshot broadcast support
- command queue with deterministic validation acks (`commandAck`)
  - ownership checks
  - bounds checks
  - per-tick rate limiting
  - accepted tick window checks (reject stale/far-future command batches)
  - unsupported command rejection (`unsupportedCommand`) at ingress and room validation
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

Runtime env vars:
- `STARKRAFT_SERVER_ADDR` (listen address, default `:8080`)
- `STARKRAFT_SIM_VERSION` (sim version tag reported by the server, default `dev`)
- `STARKRAFT_BUILD_HASH` (build identity, default empty)
- `STARKRAFT_REPLAY_PATH` (replay JSONL output, disabled when empty)
- `STARKRAFT_EMPTY_ROOM_TTL` (duration, default `30s`)
- `STARKRAFT_RESUME_WINDOW` (duration, default `15s`; counted from disconnect)
- `STARKRAFT_ADMIN_TOKEN` (optional bearer token for `/admin/stats`; when empty, only loopback clients are allowed)
- `STARKRAFT_KEYFRAME_EVERY` (int ticks, default `50`)
- whitespace-only launcher env values fall back to defaults after trimming

Operational endpoints:
- `GET /healthz`:
  - lightweight readiness/status payload (`status`, protocol, sim version, room/session counts)
- `GET /admin/stats`:
  - room-level runtime snapshot (`tick`, world hash, unit/client/pending counts, match state)
  - resumable session snapshot (client/room linkage, connected flag, remaining expiry after disconnect; tokens are never exposed)
  - requires `Authorization: Bearer $STARKRAFT_ADMIN_TOKEN` when set, otherwise loopback-only (403)

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
go build ./...
go test ./...
go vet ./...
go mod verify
```

Single test: `go test ./pkg/authoritative -run <TestName>`.
CI also runs `go mod tidy` and fails if `go.mod`/`go.sum` change.
