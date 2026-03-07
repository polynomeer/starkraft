#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${STARKRAFT_PLAY_SIM_SMOKE_TMP_DIR:-$(mktemp -d /tmp/starkraft-play-smoke-XXXXXX)}"
SNAPSHOT_FILE="$TMP_DIR/snapshots.ndjson"
INPUT_FILE="$TMP_DIR/input.ndjson"

mkdir -p "$TMP_DIR"
: >"$INPUT_FILE"

cd "$ROOT_DIR"

echo "[play-sim-smoke] tmp=$TMP_DIR"
./gradlew :sim:run --args="--ticks 40 --noSleep --snapshotEvery 1 --snapshotOut $SNAPSHOT_FILE --inputTail $INPUT_FILE" >/tmp/starkraft-play-sim-smoke-run.log
./gradlew :sim:graphicalClient --args="--headless --headlessTicks 5 $SNAPSHOT_FILE $INPUT_FILE" >/tmp/starkraft-play-sim-smoke-client.log

if ! rg -q '"recordType":"snapshot"' "$SNAPSHOT_FILE"; then
  echo "[play-sim-smoke] missing snapshot records"
  cat /tmp/starkraft-play-sim-smoke-run.log
  exit 1
fi

echo "[play-sim-smoke] ok snapshot=$SNAPSHOT_FILE"
