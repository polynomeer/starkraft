#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$ROOT/dist/release}"
VERSION="${2:-dev}"

mkdir -p "$OUT_DIR"
echo "Packaging Starkraft release version=$VERSION out=$OUT_DIR"

echo "[1/4] Build sim distribution"
"$ROOT/gradlew" :sim:distZip >/dev/null
cp "$ROOT/sim/build/distributions/sim-1.0-SNAPSHOT.zip" "$OUT_DIR/starkraft-sim-${VERSION}.zip"

if command -v go >/dev/null 2>&1; then
  echo "[2/4] Build server binary"
  (cd "$ROOT/server" && go build -o "$OUT_DIR/starkraft-server-${VERSION}" ./cmd/server)

  echo "[3/4] Build client binaries"
  (cd "$ROOT/client" && go build -o "$OUT_DIR/starkraft-client-cli-${VERSION}" ./cmd/client)
  (cd "$ROOT/client" && go build -o "$OUT_DIR/starkraft-client-bot-${VERSION}" ./cmd/bot)
else
  echo "[2/4] Skip Go binaries (go toolchain not found)"
fi

echo "[4/4] Emit release manifest"
cat > "$OUT_DIR/manifest-${VERSION}.txt" <<EOF
version=$VERSION
protocolVersion=1
simVersion=1.0-SNAPSHOT
buildDateUtc=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

ls -lh "$OUT_DIR"
echo "Release packaging complete"
