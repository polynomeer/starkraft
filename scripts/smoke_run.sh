#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[smoke] building sim"
./gradlew --no-daemon :sim:build >/tmp/starkraft-smoke-build.log

echo "[smoke] running deterministic short match"
OUT_FILE="$(mktemp /tmp/starkraft-smoke-XXXXXX.log)"
./gradlew --no-daemon :sim:run --args="--ticks 120 --noSleep --dumpWorldHash" >"$OUT_FILE"

if ! rg -q "world hash=" "$OUT_FILE"; then
  echo "[smoke] missing world hash output"
  cat "$OUT_FILE"
  exit 1
fi

HASH_LINE="$(rg "world hash=" "$OUT_FILE" | tail -n 1)"
echo "[smoke] ok: ${HASH_LINE}"
