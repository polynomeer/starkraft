# client module

Headless websocket client tools.

## Current scope

- websocket handshake + protocol envelope messaging
- interactive CLI client for select/move/attack/build/queue commands
- snapshot buffer with basic interpolation for text display
- autonomous bot client with simple build/queue + attack routine

## Run CLI client

```bash
cd client
go run ./cmd/client --url ws://127.0.0.1:8080/ws --name cli --room default
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
- `select <ids...>`, `move x y`, `attack <targetId>`, `build x y [type]`, `queue [type]`
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
go run ./cmd/bot --url ws://127.0.0.1:8080/ws --name bot-a --room default
```
