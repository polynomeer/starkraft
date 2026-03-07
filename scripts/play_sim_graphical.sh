#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAY_DIR="${STARKRAFT_SIM_PLAY_DIR:-/tmp/starkraft-play}"
TICKS="${STARKRAFT_SIM_PLAY_TICKS:-5000}"
SCENARIO="${STARKRAFT_SIM_PLAY_SCENARIO:-skirmish}"

cd "$ROOT_DIR"

echo "[sim-play] root=$PLAY_DIR ticks=$TICKS scenario=$SCENARIO"
./gradlew :sim:play --args="$PLAY_DIR $TICKS $SCENARIO"
