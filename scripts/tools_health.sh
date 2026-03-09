#!/usr/bin/env bash
set -euo pipefail

# Env:
# - STARKRAFT_TOOLS_HEALTH_TMP_DIR (default mktemp under /tmp)
# Writes JSON artifacts plus summary.json under TMP_DIR.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="${STARKRAFT_TOOLS_HEALTH_TMP_DIR:-$(mktemp -d /tmp/starkraft-tools-health-XXXXXX)}"
REPLAY_JSON="$TMP_DIR/replay.json"
NDJSON_REPLAY="$TMP_DIR/replay.ndjson"
MAP_JSON="$TMP_DIR/map.json"
META_JSON="$TMP_DIR/replay-meta.json"
STATS_JSON="$TMP_DIR/replay-stats.json"
VERIFY_JSON="$TMP_DIR/replay-verify.json"
FAST_FORWARD_JSON="$TMP_DIR/replay-fast-forward.json"
MAP_GEN_JSON="$TMP_DIR/map-generate.json"
MAP_VALIDATE_JSON="$TMP_DIR/map-validate.json"
DATA_VALIDATE_JSON="$TMP_DIR/data-validate.json"
NDJSON_VERIFY_JSON="$TMP_DIR/ndjson-verify.json"
NDJSON_STATS_JSON="$TMP_DIR/ndjson-stats.json"
MAP_INVALID_JSON="$TMP_DIR/map-invalid.json"
DATA_INVALID_JSON="$TMP_DIR/data-invalid.json"
EMPTY_DATA_DIR="$TMP_DIR/empty-data"
SUMMARY_JSON="$TMP_DIR/summary.json"

mkdir -p "$TMP_DIR"
export TMP_DIR

echo "[tools-health] tmp=$TMP_DIR"

cat > "$NDJSON_REPLAY" <<'EOF'
{"recordType":"header","protocolVersion":1}
{"recordType":"keyframe","tick":1,"worldHash":1469598103934665634,"units":[]}
EOF
mkdir -p "$EMPTY_DATA_DIR"

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
  ./gradlew --quiet :tools:run --args="replay verify-ndjson $NDJSON_REPLAY --json" > "$NDJSON_VERIFY_JSON"
  ./gradlew --quiet :tools:run --args="replay stats $NDJSON_REPLAY --json" > "$NDJSON_STATS_JSON"

  ./gradlew --quiet :tools:run --args="map validate $TMP_DIR/missing-map.json --json" > "$MAP_INVALID_JSON" 2>/dev/null || true
  ./gradlew --quiet :tools:run --args="data validate --dir $EMPTY_DATA_DIR --json" > "$DATA_INVALID_JSON" 2>/dev/null || true
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
ndjson_verify = json.loads((tmp / "ndjson-verify.json").read_text())
ndjson_stats = json.loads((tmp / "ndjson-stats.json").read_text())
map_invalid = json.loads((tmp / "map-invalid.json").read_text())
data_invalid = json.loads((tmp / "data-invalid.json").read_text())

assert meta.get("schema") == 1, meta
assert meta.get("outputVersion") == 1, meta
assert stats.get("result") == "ok", stats
assert stats.get("outputVersion") == 1, stats
assert verify.get("result") == "ok", verify
assert verify.get("outputVersion") == 1, verify
assert ff.get("result") == "ok", ff
assert ff.get("outputVersion") == 1, ff
assert map_gen.get("result") == "generated", map_gen
assert map_gen.get("outputVersion") == 1, map_gen
assert map_val.get("result") == "ok", map_val
assert map_val.get("outputVersion") == 1, map_val
assert data_val.get("result") == "ok", data_val
assert data_val.get("outputVersion") == 1, data_val
assert ndjson_verify.get("result") == "ok", ndjson_verify
assert ndjson_verify.get("outputVersion") == 1, ndjson_verify
assert ndjson_stats.get("result") == "ok", ndjson_stats
assert ndjson_stats.get("outputVersion") == 1, ndjson_stats
assert ndjson_stats.get("keyframeHashMismatches") == 0, ndjson_stats
assert map_invalid.get("result") == "invalid", map_invalid
assert map_invalid.get("outputVersion") == 1, map_invalid
assert "firstError" in map_invalid, map_invalid
assert "errorsList" in map_invalid and len(map_invalid["errorsList"]) > 0, map_invalid
assert data_invalid.get("result") == "invalid", data_invalid
assert data_invalid.get("outputVersion") == 1, data_invalid
assert "firstError" in data_invalid, data_invalid
assert "errorsList" in data_invalid and len(data_invalid["errorsList"]) > 0, data_invalid
summary = {
    "result": "ok",
    "outputVersion": 1,
    "files": {
        "replay": str(tmp / "replay.json"),
        "replayMeta": str(tmp / "replay-meta.json"),
        "replayStats": str(tmp / "replay-stats.json"),
        "replayVerify": str(tmp / "replay-verify.json"),
        "replayFastForward": str(tmp / "replay-fast-forward.json"),
        "ndjsonVerify": str(tmp / "ndjson-verify.json"),
        "ndjsonStats": str(tmp / "ndjson-stats.json"),
        "mapGenerate": str(tmp / "map-generate.json"),
        "mapValidate": str(tmp / "map-validate.json"),
        "mapInvalid": str(tmp / "map-invalid.json"),
        "dataValidate": str(tmp / "data-validate.json"),
        "dataInvalid": str(tmp / "data-invalid.json")
    }
}
(tmp / "summary.json").write_text(json.dumps(summary, separators=(",", ":")))
print("ok")
PY

echo "[tools-health] ok summary=$SUMMARY_JSON"
