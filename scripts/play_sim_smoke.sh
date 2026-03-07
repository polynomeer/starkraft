#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${STARKRAFT_PLAY_SIM_SMOKE_TMP_DIR:-$(mktemp -d /tmp/starkraft-play-smoke-XXXXXX)}"
SNAPSHOT_FILE="$TMP_DIR/snapshots.ndjson"
INPUT_FILE="$TMP_DIR/input.ndjson"
RUN_LOG="$TMP_DIR/sim-run.log"
CLIENT_LOG="$TMP_DIR/client-headless.log"
KEEP_TMP="${STARKRAFT_PLAY_SIM_SMOKE_KEEP_TMP:-0}"

mkdir -p "$TMP_DIR"
: >"$INPUT_FILE"

cd "$ROOT_DIR"

echo "[play-sim-smoke] tmp=$TMP_DIR"
./gradlew :sim:run --args="--ticks 40 --noSleep --snapshotEvery 1 --snapshotOut $SNAPSHOT_FILE --inputTail $INPUT_FILE" >"$RUN_LOG"
./gradlew :sim:graphicalClient --args="--headless --headlessTicks 5 $SNAPSHOT_FILE $INPUT_FILE" >"$CLIENT_LOG"

if ! rg -q '"recordType":"snapshot"' "$SNAPSHOT_FILE"; then
  if ! grep -q '"recordType":"snapshot"' "$SNAPSHOT_FILE"; then
    echo "[play-sim-smoke] missing snapshot records"
    cat "$RUN_LOG"
    cat "$CLIENT_LOG"
    exit 1
  fi
fi

echo "[play-sim-smoke] ok snapshot=$SNAPSHOT_FILE"
if [[ "$KEEP_TMP" != "1" ]]; then
  rm -rf "$TMP_DIR"
else
  echo "[play-sim-smoke] kept tmp dir $TMP_DIR"
fi
