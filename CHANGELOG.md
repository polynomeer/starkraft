# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Tools module with replay metadata/stats/verify/fast-forward CLI, map validator/generator, and data validation for JSON/YAML defs.
- Graphical client headless mode for non-GUI CI smoke runs.
- Soak harness (`:sim:soak`) with tick latency, pathfinding budget pressure, memory, and GC telemetry.
- Tools JSON output modes for replay/map/data commands with CI contract validation.

### Changed
- Benchmark output now includes tick `p99` and pathfinding node-budget usage percentiles.
- Live input tail parsing now enforces sanitization and anti-spam throttling counters.

## [0.1.0] - 2026-03-06

### Added
- Deterministic fixed-tick sim core with pathfinding/replanning, combat, fog, economy, buildings, production, research, and replay hashing.
- Versioned shared protocol v1 schema and golden model tests.
- Authoritative websocket server MVP with room loop, command validation, snapshot broadcast, and replay output.
- Headless client/bot MVP and end-to-end smoke workflow.

### Protocol
- `protocolVersion=1` is the current stable baseline for server/client handshake and message envelopes.
