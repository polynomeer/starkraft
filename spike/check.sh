#!/usr/bin/env bash
# Rule checks for the P0 core: no floating point in rule code, then the dependency-free test runner.
set -euo pipefail
cd "$(dirname "$0")"
if grep -nE '\b(float|double|decimal|Math\.Sqrt|MathF)\b' Samjae.Core/*.cs; then
  echo "floating point is banned in Samjae.Core" >&2
  exit 1
fi
dotnet run -c Release --project Samjae.Check
