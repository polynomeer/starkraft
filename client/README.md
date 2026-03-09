# client module

Headless websocket client tools.

## Current scope

- websocket handshake + protocol envelope messaging
- protocol compatibility enforcement (rejects mismatched envelope/handshake versions)
- interactive CLI client for select/move/attack/build/queue commands
- snapshot buffer with basic interpolation for text display
- autonomous bot client with simple build/queue + attack routine
- runtime failures (connect/protocol/stream timeout) exit with explicit stderr errors and non-zero code

## Run CLI client

Optional first-time dependency bootstrap from repo root:

```bash
./scripts/bootstrap_dev.sh
```

```bash
cd client
go run ./cmd/client --url ws://127.0.0.1:8080/ws --name cli --room default --simVersion dev
```

`--simVersion` must be non-empty (server rejects empty protocol sim version tags).
On connect, the CLI prints `resumeToken=<token>` when the server provides one.
Whitespace-only `--room` and `--resumeToken` values are trimmed away before handshake; whitespace-only `--name` is rejected locally.

Resume a prior session identity (if token is still valid):

```bash
cd client
go run ./cmd/client --url ws://127.0.0.1:8080/ws --name cli --room default --simVersion dev --resumeToken <token>
```

Scripted batch mode:

```bash
cd client
go run ./cmd/client --url ws://127.0.0.1:8080/ws --name cli --room default --script /tmp/starkraft-client-script.json
```

`--script` format (JSON array):

```json
[
  {
    "tick": 1,
    "commands": [
      { "commandType": "move", "unitIds": [1], "x": 10, "y": 10 }
    ]
  }
]
```

CLI tips:
- `select <ids...>`, `move x y`, `attack <targetId>`, `build x y [type]`, `queue [type]`, `surrender`
- control groups:
  - `groupSave <0-9>` stores current selection
  - `groupRecall <0-9>` replaces current selection from slot
  - `groupAdd <0-9>` merges slot into current selection
  - `groups` prints non-empty control groups
- `status` prints the latest command `requestId -> pending/accepted/rejected` table
- `hud` prints an authoritative snapshot summary (unit counts by owner/type, match end state, request-state counters)

## Run bot client

```bash
cd client
go run ./cmd/bot --url ws://127.0.0.1:8080/ws --name bot-a --room default --simVersion dev
```
On connect, the bot prints `bot resumeToken=<token>` when available.

Resume bot identity with a saved token:

```bash
cd client
go run ./cmd/bot --url ws://127.0.0.1:8080/ws --name bot-a --room default --simVersion dev --resumeToken <token>
```

## Verify module integrity

```bash
cd client
go test ./...
go mod verify
```
