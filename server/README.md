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

## Run

```bash
cd server
go run ./cmd/server
```

## Run tests

```bash
cd server
go test ./...
```
