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

print_logs() {
  echo "[e2e] server log:"
  [[ -f "$SERVER_LOG" ]] && cat "$SERVER_LOG" || true
  echo "[e2e] bot-a log:"
  [[ -f "$BOT1_LOG" ]] && cat "$BOT1_LOG" || true
  echo "[e2e] bot-b log:"
  [[ -f "$BOT2_LOG" ]] && cat "$BOT2_LOG" || true
}

(
  cd "$ROOT_DIR/server"
  STARKRAFT_SERVER_ADDR=127.0.0.1:18080 STARKRAFT_REPLAY_PATH="$REPLAY_FILE" go run ./cmd/server >"$SERVER_LOG" 2>&1
) &
SERVER_PID=$!

sleep 1

if ! kill -0 "$SERVER_PID" 2>/dev/null; then
  echo "[e2e] server exited during startup"
  print_logs
  exit 1
fi

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
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "[e2e] server exited before bots finished"
    print_logs
    exit 1
  fi
  if ! kill -0 "$BOT1_PID" 2>/dev/null && ! kill -0 "$BOT2_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if kill -0 "$BOT1_PID" 2>/dev/null || kill -0 "$BOT2_PID" 2>/dev/null; then
  echo "[e2e] bots did not finish in time"
  print_logs
  exit 1
fi

set +e
wait "$BOT1_PID"
BOT1_STATUS=$?
wait "$BOT2_PID"
BOT2_STATUS=$?
set -e
if [[ "$BOT1_STATUS" -ne 0 || "$BOT2_STATUS" -ne 0 ]]; then
  echo "[e2e] bot process exited with error status bot-a=$BOT1_STATUS bot-b=$BOT2_STATUS"
  print_logs
  exit 1
fi

if [[ ! -s "$REPLAY_FILE" ]]; then
  echo "[e2e] replay file missing"
  print_logs
  exit 1
fi

if ! rg -q '"recordType":"header"' "$REPLAY_FILE"; then
  echo "[e2e] replay missing header"
  print_logs
  exit 1
fi
if ! rg -q '"recordType":"command"' "$REPLAY_FILE"; then
  echo "[e2e] replay missing command"
  print_logs
  exit 1
fi
if ! rg -q '"recordType":"matchEnd"' "$REPLAY_FILE"; then
  echo "[e2e] replay missing matchEnd"
  print_logs
  exit 1
fi

if ! rg -q 'match ended winner=' "$BOT1_LOG" && ! rg -q 'match ended winner=' "$BOT2_LOG"; then
  echo "[e2e] bots did not observe match end"
  print_logs
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
