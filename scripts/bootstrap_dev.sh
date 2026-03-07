#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[bootstrap] resolving Gradle wrapper and module deps"
(
  cd "$ROOT_DIR"
  ./gradlew --version >/dev/null
)

echo "[bootstrap] downloading server Go modules"
(
  cd "$ROOT_DIR/server"
  go mod download
)

echo "[bootstrap] downloading client Go modules"
(
  cd "$ROOT_DIR/client"
  go mod download
)

echo "[bootstrap] done"
