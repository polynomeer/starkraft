# server module

Authoritative multiplayer host (in progress).

## Status

Protocol package scaffold is implemented.

## Planned responsibilities

- websocket transport and handshake
- room lifecycle and fixed tick orchestration
- authoritative command validation
- replay persistence

## Run tests

```bash
cd server
go test ./...
```
