# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

Changes since the 2026-03-06 changelog update, summarized by area from `git log`.

### Graphical client (libGDX, `sim/src/main/kotlin/client`)
- Added the libGDX graphical frontend and `:sim:play` flow: main menu, scenario presets, tactical HUD (top bar, selection cards, command deck, minimap), control groups, pause/help overlays, restart and scenario cycling.
- Added battlefield feedback: command pings and order trails, combat tracers, hit flashes, attack windup/recoil, death remains and staged structure collapse, completion/economy markers, under-attack and structure-loss alerts, and differentiated combat/command audio cues.
- Added left-click armed commands for all orders, build placement preview, queued command chaining, rally command flow, armed-command cursor previews, and disabled-command reasons.
- Optional sprite assets via `data/visuals.json`, with procedural rendering as fallback.
- Moved HUD and renderer colors onto presentation theme tokens.

### Fixed (client stability)
- Armed-mode rules: keep selection on cancel, require a selection before arming orders or build mode, block box select and minimap drag while armed, reject friendly targets in attack mode.
- HUD/overlay input: block world input on HUD surfaces, block gameplay hotkeys and command UI under overlays, overlay-first escape handling.
- Session I/O: clear session state on match entry and restart, write play state files atomically, wait for a complete initial snapshot.
- Layout: align the world viewport with HUD bounds and compact the bottom HUD, command deck, and center panel on small screens.

### Performance
- Cached HUD, command deck, and selection rebuilds; skipped redundant label/actor updates; culled world tile rendering.
- Restored a fast play baseline: the snapshot stream filters unsupported record types before full decode, caps per-poll drain work, and `:sim:play` defaults to a snapshot every 2 ticks (`STARKRAFT_PLAY_SNAPSHOT_EVERY`).
- Added `scripts/play_sim_profile.sh` (runs `:sim:play` with `STARKRAFT_SIM_DEBUG=1`).

### Sim
- `:sim:play` fails when snapshots never start; initial wait is configurable via `STARKRAFT_PLAY_WAIT_SNAPSHOT_MS`.
- Default map gained more, spread-out enemy packs; unit movement speed increased.

### Server and protocol
- Handshake rejections close with explicit reasons (protocol mismatch with upgrade direction, invalid room, invalid/expired resume token, non-object or non-handshake first message); post-handshake protocol mismatches also close with a reason.
- Empty or whitespace-only `simVersion` is rejected; unknown command types are rejected at ingress with `unsupportedCommand`.
- Launcher env values are trimmed before parsing.
- Added resume-token handshake and surrender command-batch golden coverage.

### Go client and bot
- Added `--resumeToken` and `--simVersion` flags; the resume token is printed on connect and `simVersion` is trimmed before handshake.

### Scripts and CI
- Added `scripts/bootstrap_dev.sh`, `scripts/play_sim_graphical.sh`, and `scripts/play_sim_smoke.sh`; `play_stack_local.sh` and `e2e_server_bots_smoke.sh` accept a sim version override and reject whitespace-only values; smoke scripts fall back to `grep`/`python3` when `rg`/`curl` are missing.
- CI: shell syntax checks, script env guard-rail checks, `go vet`, `go mod verify`, and the sim play smoke; nightly gained Go quality checks and the play smoke.

### Docs
- Added the `docs/` hub (product, design, architecture, protocol, replay, data, UX, presentation, ops, testing docs), `COMMAND_CONTRACT.md`, `PLAYTEST_CHECKLIST.md`, `COMMERCIALIZATION_ROADMAP.md`, `PHASE1_STABILIZATION_PLAN.md`, a PR template, and `CLAUDE.md`.

### Added
- Tools module with replay metadata/stats/verify/fast-forward CLI, map validator/generator, and data validation for JSON/YAML defs.
- Graphical client headless mode for non-GUI CI smoke runs.
- Soak harness (`:sim:soak`) with tick latency, pathfinding budget pressure, memory, and GC telemetry.
- Tools JSON output modes for replay/map/data commands with CI contract validation.
- Tools JSON outputs now carry `outputVersion=1` and richer invalid payload fields (`firstError`, `errorsList`).
- Added machine-contract schema metadata for tools JSON outputs (`tools-output-v1.schema.json`) with test coverage.

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
