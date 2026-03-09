#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${STARKRAFT_PLAY_PORT:-18080}"
ADDR="127.0.0.1:${PORT}"
ROOM="${STARKRAFT_PLAY_ROOM:-local-play}"
SIM_VERSION="${STARKRAFT_PLAY_SIM_VERSION:-dev}"
TMP_DIR="${STARKRAFT_PLAY_TMP_DIR:-$(mktemp -d /tmp/starkraft-play-XXXXXX)}"
REPLAY_FILE="${STARKRAFT_PLAY_REPLAY:-$TMP_DIR/replay.jsonl}"
SERVER_LOG="$TMP_DIR/server.log"
BOT_LOG="$TMP_DIR/bot.log"
RUN_BOT="${STARKRAFT_PLAY_BOT:-1}"

mkdir -p "$TMP_DIR"
touch "$SERVER_LOG" "$BOT_LOG"

http_ready() {
  local url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -fsS "$url" >/dev/null 2>&1
    return $?
  fi
  python3 - "$url" <<'PY' >/dev/null 2>&1
import sys
import urllib.request

url = sys.argv[1]
with urllib.request.urlopen(url, timeout=1.0) as resp:
    if 200 <= resp.status < 300:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

cleanup() {
  set +e
  [[ -n "${CLI_PID:-}" ]] && kill "$CLI_PID" 2>/dev/null
  [[ -n "${BOT_PID:-}" ]] && kill "$BOT_PID" 2>/dev/null
  [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null
}
trap cleanup EXIT

echo "[play] logs=$TMP_DIR replay=$REPLAY_FILE room=$ROOM addr=$ADDR simVersion=$SIM_VERSION"

(
  cd "$ROOT_DIR/server"
  STARKRAFT_SERVER_ADDR="$ADDR" STARKRAFT_REPLAY_PATH="$REPLAY_FILE" go run ./cmd/server >"$SERVER_LOG" 2>&1
) &
SERVER_PID=$!

for _ in $(seq 1 40); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "[play] server exited during startup"
    cat "$SERVER_LOG"
    exit 1
  fi
  if http_ready "http://${ADDR}/healthz"; then
    break
  fi
  sleep 0.25
done

if ! http_ready "http://${ADDR}/healthz"; then
  echo "[play] server health check failed"
  cat "$SERVER_LOG"
  exit 1
fi

if [[ "$RUN_BOT" == "1" ]]; then
  (
    cd "$ROOT_DIR/client"
    go run ./cmd/bot --url "ws://${ADDR}/ws" --name bot-a --room "$ROOM" --simVersion "$SIM_VERSION" >"$BOT_LOG" 2>&1
  ) &
  BOT_PID=$!
  echo "[play] started bot-a (log: $BOT_LOG)"
fi

echo "[play] starting interactive CLI. Type 'quit' to stop."
(
  cd "$ROOT_DIR/client"
  go run ./cmd/client --url "ws://${ADDR}/ws" --name player-cli --room "$ROOM" --simVersion "$SIM_VERSION"
) &
CLI_PID=$!
wait "$CLI_PID"

echo "[play] done. server log: $SERVER_LOG"
if [[ -s "$REPLAY_FILE" ]]; then
  echo "[play] replay saved: $REPLAY_FILE"
fi
