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

CLI tips:
- `select <ids...>`, `move x y`, `attack <targetId>`, `build x y [type]`, `queue [type]`
- `status` prints the latest command `requestId -> pending/accepted/rejected` table

## Run bot client

```bash
cd client
go run ./cmd/bot --url ws://127.0.0.1:8080/ws --name bot-a --room default
```
