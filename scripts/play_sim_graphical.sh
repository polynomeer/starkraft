#!/usr/bin/env bash
set -euo pipefail

# Env:
# - STARKRAFT_SIM_PLAY_DIR (default /tmp/starkraft-play)
# - STARKRAFT_SIM_PLAY_TICKS (default 5000)
# - STARKRAFT_SIM_PLAY_SCENARIO (default skirmish)
# - STARKRAFT_PLAY_WAIT_SNAPSHOT_MS (default 5000, minimum 250)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAY_DIR="${STARKRAFT_SIM_PLAY_DIR:-/tmp/starkraft-play}"
TICKS="${STARKRAFT_SIM_PLAY_TICKS:-5000}"
SCENARIO="${STARKRAFT_SIM_PLAY_SCENARIO:-skirmish}"
WAIT_SNAPSHOT_MS="${STARKRAFT_PLAY_WAIT_SNAPSHOT_MS:-5000}"

cd "$ROOT_DIR"

echo "[sim-play] root=$PLAY_DIR ticks=$TICKS scenario=$SCENARIO waitSnapshotMs=$WAIT_SNAPSHOT_MS"
./gradlew :sim:play --args="$PLAY_DIR $TICKS $SCENARIO"
