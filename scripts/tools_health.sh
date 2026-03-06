#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${STARKRAFT_TOOLS_HEALTH_TMP_DIR:-$(mktemp -d /tmp/starkraft-tools-health-XXXXXX)}"
REPLAY_JSON="$TMP_DIR/replay.json"
MAP_JSON="$TMP_DIR/map.json"
META_JSON="$TMP_DIR/replay-meta.json"
STATS_JSON="$TMP_DIR/replay-stats.json"
VERIFY_JSON="$TMP_DIR/replay-verify.json"
FAST_FORWARD_JSON="$TMP_DIR/replay-fast-forward.json"
MAP_GEN_JSON="$TMP_DIR/map-generate.json"
MAP_VALIDATE_JSON="$TMP_DIR/map-validate.json"
DATA_VALIDATE_JSON="$TMP_DIR/data-validate.json"

mkdir -p "$TMP_DIR"
export TMP_DIR

echo "[tools-health] tmp=$TMP_DIR"

(
  cd "$ROOT_DIR"
  ./gradlew --quiet :sim:run --args="--script sim/scripts/sample.script --ticks 2 --noSleep --replayDump $REPLAY_JSON"
  ./gradlew --quiet :tools:run --args="replay meta $REPLAY_JSON --json" > "$META_JSON"
  ./gradlew --quiet :tools:run --args="replay stats $REPLAY_JSON --json" > "$STATS_JSON"
  ./gradlew --quiet :tools:run --args="replay verify $REPLAY_JSON --json" > "$VERIFY_JSON"
  ./gradlew --quiet :tools:run --args="replay fast-forward $REPLAY_JSON --json" > "$FAST_FORWARD_JSON"
  ./gradlew --quiet :tools:run --args="map generate $MAP_JSON --width 32 --height 32 --seed 1337 --json" > "$MAP_GEN_JSON"
  ./gradlew --quiet :tools:run --args="map validate $MAP_JSON --json" > "$MAP_VALIDATE_JSON"
  ./gradlew --quiet :tools:run --args="data validate --dir sim/src/main/resources/data --json" > "$DATA_VALIDATE_JSON"
)

python3 - <<'PY'
import json
import os
from pathlib import Path
tmp = Path(os.environ["TMP_DIR"])
meta = json.loads((tmp / "replay-meta.json").read_text())
stats = json.loads((tmp / "replay-stats.json").read_text())
verify = json.loads((tmp / "replay-verify.json").read_text())
ff = json.loads((tmp / "replay-fast-forward.json").read_text())
map_gen = json.loads((tmp / "map-generate.json").read_text())
map_val = json.loads((tmp / "map-validate.json").read_text())
data_val = json.loads((tmp / "data-validate.json").read_text())

assert meta.get("schema") == 1, meta
assert stats.get("result") == "ok", stats
assert verify.get("result") == "ok", verify
assert ff.get("result") == "ok", ff
assert map_gen.get("result") == "generated", map_gen
assert map_val.get("result") == "ok", map_val
assert data_val.get("result") == "ok", data_val
print("ok")
PY

echo "[tools-health] ok"
