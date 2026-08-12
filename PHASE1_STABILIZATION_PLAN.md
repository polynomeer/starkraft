# Starkraft Phase 1 Stabilization Plan

## Purpose

This document turns the commercialization roadmap into an executable Phase 1 plan.

Phase 1 is not a polish pass. It is the pass that removes the most disruptive
control, visibility, runtime, and reliability problems so the client becomes a
stable base for later polish.

Related document:
- [COMMERCIALIZATION_ROADMAP.md](/Users/hammac/Projects/starkraft/COMMERCIALIZATION_ROADMAP.md)

## Phase 1 Goal

Make the current libGDX client reliably playable for repeated internal sessions.

That means:
- no known crashers in normal play
- no major HUD/world visibility blockers
- no inconsistent core command behavior
- no client I/O corruption under normal interaction

## Exit Criteria

Phase 1 is complete only when all of the following are true:

1. A 30-minute internal play session completes without a client crash.
2. Core commands behave consistently across mouse and keyboard paths.
3. No supported desktop resolution shows critical HUD overlap or hidden minimap/world state.
4. Snapshot/input file handling is stable under repeated play.
5. A formal playtest checklist passes with no blocker issues.

## Workstreams

## Workstream A: Command Contract

### Objective

Define exactly how every core command should behave, then make the client obey
that contract in all input paths.

### Scope

- `select`
- `box select`
- `move`
- `attack`
- `attack-move`
- `patrol`
- `build`
- `cancel build`
- `cancel train`
- `cancel research`
- `view switch`
- `pause`
- `help`

### Known Risk

The project already surfaced inconsistency between:
- right click vs left click armed command paths
- click selection vs command mode click behavior
- build/attack/move confirmation semantics

### Deliverables

- one command behavior matrix
- one regression test group for runtime command transitions
- no ambiguous command mode transitions

### Acceptance Criteria

- every armed command has a defined `enter`, `confirm`, `cancel`, and `feedback` rule
- the same command does not behave differently depending on whether it came from mouse or hotkey
- clicking an entity, terrain tile, minimap, or HUD control produces predictable results

### Immediate Tasks

1. Document current command behavior matrix in code comments or a dedicated md file.
2. Add regression tests for all armed command transitions.
3. Sweep command cancel paths and remove special-case drift.
4. Validate mixed-input flows:
   - hotkey then left click
   - hotkey then right click
   - hotkey then escape
   - selection change while armed

## Workstream B: HUD and Visibility Stability

### Objective

Ensure the player can always see the battlefield, minimap, selection state, and
commands clearly.

### Scope

- top bar
- bottom HUD
- minimap
- world labels
- health bars
- selection overlays
- warning overlays
- screen edge behavior

### Known Risk

This project already had regressions around:
- minimap layering
- HUD overlap
- world clipping
- world scissor mistakes
- excess fog-like masking

### Deliverables

- one supported resolution matrix
- one HUD overlap checklist
- no known minimap obstruction bug

### Acceptance Criteria

- the minimap is never partially hidden by HUD layering
- the battlefield never renders into a broken clipped shape
- HUD panels do not cover essential play-space at standard desktop sizes
- warning overlays do not hide important interaction targets

### Immediate Tasks

1. Define supported test resolutions.
2. Validate HUD layout at each resolution.
3. Verify battlefield bounds at all screen corners.
4. Verify minimap click, drag, and viewport box readability.
5. Verify selected-unit readability during dense combat.

## Workstream C: Snapshot and Input Pipeline Hardening

### Objective

Remove the remaining data-path fragility between sim output and client input.

### Scope

- NDJSON snapshot reading
- NDJSON input writing
- partial line handling
- concurrent append behavior
- session restart/reopen behavior

### Known Risk

Real bugs already appeared here:
- partial snapshot line parse failures
- input append corruption
- JSON decoding exceptions

### Deliverables

- regression tests for partial lines and concurrent append cases
- explicit handling rules for incomplete tail records
- stable session restart behavior

### Acceptance Criteria

- no parse crash from partially written snapshot lines
- no command write corruption under repeated input
- restarting a play session does not re-use a broken file state

### Immediate Tasks

1. Audit all file append and poll loops.
2. Add regression coverage for partial-tail and interrupted-write cases.
3. Verify restart and scenario-switch behavior with fresh files.
4. Add defensive logging for malformed line rejection.

## Workstream D: Long-Session Bug Sweep

### Objective

Find issues that only show up after repeated interaction, not just startup.

### Scope

- repeated selection
- repeated movement retargeting
- build spam
- frequent view switching
- long combat sequences
- pause/resume loops
- restart loops

### Deliverables

- one stabilized issue list
- issue labels:
  - blocker
  - major
  - minor
  - polish

### Acceptance Criteria

- no unresolved blocker issue remains open at Phase 1 exit
- major issues are either fixed or explicitly accepted into Phase 2 with rationale

### Immediate Tasks

1. Run repeated 15-30 minute manual sessions.
2. Record every issue with repro steps.
3. Fix blocker issues before any further broad polish pass.

## Workstream E: Playtest Checklist

### Objective

Replace ad-hoc validation with a repeatable checklist.

### Deliverables

- one manual playtest checklist
- one per-build validation flow

### Checklist Sections

1. Startup
- launch game
- open menu
- start scenario

2. Core Control
- single select
- drag select
- move command
- attack command
- attack-move
- patrol
- cancel armed mode

3. Camera and Minimap
- edge pan
- middle drag
- minimap click
- minimap drag
- centering

4. Production
- build placement valid
- build placement invalid
- construct
- train
- research
- cancel queue actions

5. Combat
- ranged combat readability
- melee combat readability
- death notices
- structure loss warning

6. Session State
- pause
- help
- restart
- scenario switch
- match end

### Acceptance Criteria

- checklist passes without blocker issues
- failures are written down with reproducible steps

## Resolution Matrix

Phase 1 should validate at least:

- `1280x720`
- `1440x900`
- `1600x900`
- `1920x1080`

If a layout works only at one resolution, it is not stable enough.

## Bug Triage Rules

Use these categories:

### Blocker

Prevents normal play.

Examples:
- crash
- minimap hidden
- command cannot be issued reliably
- battlefield mostly obscured

### Major

Playable, but undermines trust or session quality.

Examples:
- wrong command mode behavior
- severe visual misread in combat
- repeated warning spam
- restart/session flow inconsistency

### Minor

Visible defect that does not break the session.

Examples:
- slight layout drift
- weak timing mismatch
- cosmetic overlap in rare states

### Polish

Improvement that should wait until blockers and majors are closed.

Examples:
- fine-grain pulse timing
- effect intensity tuning
- typography refinement

## Recommended Execution Order

1. Command contract
2. Snapshot/input hardening
3. HUD/visibility stability
4. Long-session bug sweep
5. Formal checklist pass

Reason:
- command inconsistency and data corruption invalidate play faster than cosmetic issues
- layout fixes are easier once command and session flow are reliable

## Definition of Done

Phase 1 is done when:

- command behavior is explicit and tested
- file-backed client session I/O is stable
- standard desktop layouts are reliable
- no blocker bugs remain in repeated internal play
- the client is ready for a true Phase 2 feel/polish pass

## Immediate Next Actions

1. Create the command behavior matrix.
2. Create the manual playtest checklist.
3. Run the first long-session stabilization sweep.
4. Fix blocker issues before any new broad feature work.
