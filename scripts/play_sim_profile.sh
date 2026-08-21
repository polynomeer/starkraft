#!/usr/bin/env bash
set -euo pipefail

# Env:
# - STARKRAFT_SIM_PLAY_DIR (default /tmp/starkraft-play-profile)
# - STARKRAFT_SIM_PLAY_TICKS (default 10000)
# - STARKRAFT_SIM_PLAY_SCENARIO (default skirmish)
# - STARKRAFT_SIM_DEBUG (forced to 1 unless already set)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAY_DIR="${STARKRAFT_SIM_PLAY_DIR:-/tmp/starkraft-play-profile}"
TICKS="${STARKRAFT_SIM_PLAY_TICKS:-10000}"
SCENARIO="${STARKRAFT_SIM_PLAY_SCENARIO:-skirmish}"
DEBUG_FLAG="${STARKRAFT_SIM_DEBUG:-1}"

cd "$ROOT_DIR"

echo "[sim-profile] root=$PLAY_DIR ticks=$TICKS scenario=$SCENARIO debug=$DEBUG_FLAG"
echo "[sim-profile] repro: start match -> move mouse to screen edges -> select -> command -> alt-tab -> close window"
STARKRAFT_SIM_DEBUG="$DEBUG_FLAG" ./gradlew :sim:play --args="$PLAY_DIR $TICKS $SCENARIO"
