#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPLAY_FILE="$(mktemp /tmp/starkraft-server-replay-XXXXXX).jsonl"
touch "$REPLAY_FILE"
SERVER_LOG="$(mktemp /tmp/starkraft-server-log-XXXXXX).txt"
BOT1_LOG="$(mktemp /tmp/starkraft-bot1-log-XXXXXX).txt"
BOT2_LOG="$(mktemp /tmp/starkraft-bot2-log-XXXXXX).txt"

cleanup() {
  set +e
  [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null
  [[ -n "${BOT1_PID:-}" ]] && kill "$BOT1_PID" 2>/dev/null
  [[ -n "${BOT2_PID:-}" ]] && kill "$BOT2_PID" 2>/dev/null
}
trap cleanup EXIT

(
  cd "$ROOT_DIR/server"
  STARKRAFT_SERVER_ADDR=127.0.0.1:18080 STARKRAFT_REPLAY_PATH="$REPLAY_FILE" go run ./cmd/server >"$SERVER_LOG" 2>&1
) &
SERVER_PID=$!

sleep 1

(
  cd "$ROOT_DIR/client"
  go run ./cmd/bot --url ws://127.0.0.1:18080/ws --name bot-a --room smoke >"$BOT1_LOG" 2>&1
) &
BOT1_PID=$!

(
  cd "$ROOT_DIR/client"
  go run ./cmd/bot --url ws://127.0.0.1:18080/ws --name bot-b --room smoke >"$BOT2_LOG" 2>&1
) &
BOT2_PID=$!

for _ in $(seq 1 35); do
  if ! kill -0 "$BOT1_PID" 2>/dev/null && ! kill -0 "$BOT2_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if kill -0 "$BOT1_PID" 2>/dev/null || kill -0 "$BOT2_PID" 2>/dev/null; then
  echo "[e2e] bots did not finish in time"
  exit 1
fi

if [[ ! -s "$REPLAY_FILE" ]]; then
  echo "[e2e] replay file missing"
  exit 1
fi

if ! rg -q '"recordType":"header"' "$REPLAY_FILE"; then
  echo "[e2e] replay missing header"
  exit 1
fi
if ! rg -q '"recordType":"command"' "$REPLAY_FILE"; then
  echo "[e2e] replay missing command"
  exit 1
fi
if ! rg -q '"recordType":"matchEnd"' "$REPLAY_FILE"; then
  echo "[e2e] replay missing matchEnd"
  exit 1
fi

if ! rg -q 'match ended winner=' "$BOT1_LOG" && ! rg -q 'match ended winner=' "$BOT2_LOG"; then
  echo "[e2e] bots did not observe match end"
  exit 1
fi

(
  cd "$ROOT_DIR/server"
  go run ./cmd/replaycheck --replay "$REPLAY_FILE" >/tmp/starkraft-replaycheck.log 2>&1
) || {
  echo "[e2e] replay verification failed"
  cat /tmp/starkraft-replaycheck.log
  exit 1
}

echo "[e2e] ok replay=$REPLAY_FILE"
