# Starkraft Commercialization Roadmap

## Goal

Keep the current deterministic simulation and libGDX client, then raise the game to a commercially shippable quality level through focused improvements in input feel, visual presentation, stability, and product readiness.

This roadmap assumes:
- `sim/` remains the source of truth for gameplay rules
- the current libGDX client remains the presentation layer
- large engine migration is out of scope unless the client architecture later proves to be the bottleneck

## Current State

What already exists:
- deterministic RTS simulation core
- live libGDX desktop client
- selectable units, issuing commands, minimap, HUD, construction, production, combat, fog, and event feedback
- replay, tools, and validation infrastructure

What is still below commercial quality:
- input feel is functional but not yet polished enough for extended play
- visual identity is still closer to a polished prototype than a shippable art direction
- UX consistency is uneven across menu, HUD, world feedback, warnings, and command flows
- bug surface is still too high for a reliable retail build
- content depth and onboarding are not yet strong enough for player retention

## Primary Problem Areas

### 1. Control Feel

Current issues:
- unit response, retargeting, and command confirmation still need tighter perceived immediacy
- different command modes are not yet fully uniform in feedback and cancellation rules
- camera control is improved but still needs sustained playtesting for fatigue and precision
- selection clarity is much better than before, but dense combat readability still needs work

Commercial target:
- command latency should feel immediate
- every command path should have consistent feedback, cancellation, and confirmation behavior
- camera movement should feel predictable, low-fatigue, and precise
- players should be able to parse selection, movement, attack, damage, and death states at a glance

### 2. Visual Design

Current issues:
- the game has stronger tactical readability now, but the presentation is still not a fully unified art direction
- terrain, units, buildings, and HUD materials do not yet feel like a finalized product set
- animation language exists, but still needs more type-specific identity
- effects and sounds are stronger, but still need better mix, pacing, and coherence

Commercial target:
- one clear visual language across menu, HUD, world, minimap, effects, and warnings
- stronger faction, unit-role, and structure-type readability
- cleaner terrain materials with less prototype repetition
- more deliberate motion design and audio identity

### 3. Bugs and Stability

Current issues:
- the project has already surfaced real client bugs in snapshot reading, NDJSON partial reads, HUD overlap, minimap layering, world clipping, and command mode behavior
- that means there are likely more edge-case bugs in input, rendering, event timing, and state transitions
- current validation is still too compile-oriented for a commercial client

Commercial target:
- no known crashers in normal play
- no data corruption in live client I/O
- no HUD/layout regressions across supported window sizes
- deterministic simulation and client synchronization remain stable under long sessions

### 4. Product Readiness

Current issues:
- onboarding is minimal
- scenario flow, progression, difficulty structure, and content loops are still limited
- accessibility, settings, save/load UX, and packaging quality are incomplete

Commercial target:
- a new player can launch, understand controls, and finish a first match without external guidance
- quality settings, controls, audio, and accessibility options are present
- packaging, release process, and smoke validation are reliable

## Secondary Problem Areas

These are not the first blockers, but they matter for ship quality:
- tutorial and onboarding flow
- AI quality and encounter design
- difficulty scaling and scenario structure
- options menu depth
- audio mixing and repetition control
- session recovery and error handling
- release packaging and QA process

## Productization Principles

1. Keep the sim authoritative
- do not move gameplay rules into UI code
- continue treating the sim snapshot/event stream as the source of truth

2. Fix feel before adding large new feature scope
- a bigger content surface with weak control feel will not convert into a better product

3. Reduce bug surface before visual overproduction
- unstable systems will erase the value of higher-end art and polish

4. Ship quality through iteration, not one big rewrite
- favor measured passes with validation, regressions, and acceptance criteria

## Roadmap

## Phase 1: Playability Stabilization

Objective:
- remove the most disruptive control, visibility, and runtime issues

Scope:
- command issue consistency for `move`, `attack`, `patrol`, `build`, `cancel`, and view switching
- selection stability in dense combat
- camera and minimap precision under repeated play
- HUD overlap, layout, and scaling validation across common resolutions
- snapshot/input pipeline hardening
- crash and rendering regression sweep

Deliverables:
- command behavior matrix with explicit expected outcomes
- regression tests for client runtime state transitions
- long-play bug list with fixes
- stable default play scenario with no known control blockers

Exit criteria:
- no known crashers in a 30-minute play session
- no known UI overlap/blocking issues at supported window sizes
- all core commands produce correct and consistent feedback

## Phase 2: Control and Feedback Polish

Objective:
- make the game feel deliberate and responsive

Scope:
- tighten selection, confirm flashes, command pulses, and ground ping timing
- improve attack windup, hit reaction, recoil recovery, and death transition timing
- unify command confirmation rules across mouse and keyboard paths
- improve audio timing, layering, and repetition control
- refine camera glide, edge pan, and minimap drag behavior through playtests

Deliverables:
- command feedback spec
- control feel pass with tuned timings and thresholds
- audio response tuning for move, attack, build, invalid, damage, kill, and collapse events

Exit criteria:
- players can issue commands repeatedly without confusion or visual noise
- combat feedback is readable without overwhelming the battlefield
- internal playtest notes identify feel polish, not systemic control failures

## Phase 3: Visual Cohesion Pass

Objective:
- move from polished prototype to unified product presentation

Scope:
- establish a final visual direction for terrain, buildings, units, effects, and HUD
- reduce remaining prototype textures, shapes, and placeholder material language
- improve faction readability and unit-role silhouette clarity
- unify top bar, bottom HUD, menu, overlays, minimap, and world labels under one visual system
- refine typography hierarchy and spacing

Deliverables:
- visual style guide for UI and world
- finalized color system for command, selection, warning, damage, build, and queue states
- silhouette pass for all primary unit and building types

Exit criteria:
- no major visual subsystem looks like a placeholder next to another polished subsystem
- players can identify role, threat, and state from silhouette and effects alone

## Phase 4: UX and Onboarding

Objective:
- make first-session understanding reliable

Scope:
- basic tutorial or guided first match
- clear hotkey reference and command explanation
- options menu for controls, audio, display, and gameplay assists
- better pause/help flow
- better post-match and restart flow

Deliverables:
- first-session onboarding flow
- options/settings screen
- updated help/controls documentation inside the client

Exit criteria:
- a new player can reach and understand the game loop without external explanation
- no critical control is discoverable only through code or README reading

## Phase 5: Content and Scenario Depth

Objective:
- make the game loop worth replaying

Scope:
- better default map composition
- clearer scenario identities
- improved AI pressure patterns
- difficulty bands
- stronger mission or skirmish loops

Deliverables:
- tuned default scenarios
- difficulty presets
- stronger faction openings and encounter pacing

Exit criteria:
- repeated sessions feel meaningfully different
- the default experience demonstrates the strengths of the game rather than just the engine

## Phase 6: Release Engineering

Objective:
- make the project shippable, supportable, and maintainable

Scope:
- packaging, installer/distribution workflow, and versioning
- broader smoke coverage for play flow
- long-run soak validation
- crash logging and runtime diagnostics
- release checklist expansion for client presentation quality

Deliverables:
- release QA checklist for game client
- reproducible packaging flow
- pre-release regression suite

Exit criteria:
- a release candidate can be built, tested, and validated repeatedly with low operator friction

## Priority Order

Recommended execution order:

1. Playability stabilization
2. Control and feedback polish
3. Bug hardening and regression coverage
4. Visual cohesion pass
5. UX and onboarding
6. Content depth
7. Release engineering

Reason:
- a stable and responsive game is worth polishing
- a beautiful but unstable game is not shippable
- a feature-rich but unreadable game is not commercial quality

## Concrete Backlog

### Critical

- define and test a full command-mode behavior contract
- remove remaining HUD/world overlap edge cases
- run systematic long-session bug sweeps
- add stronger regression coverage for runtime input/snapshot/state handling
- validate every major interaction path with keyboard-only, mouse-only, and mixed input

### High

- tighten combat readability in multi-unit fights
- improve movement and targeting responsiveness under repeated retargeting
- refine unit-specific attack and movement identity
- reduce remaining visual noise in labels, pulses, and effect overlap
- add settings/options and onboarding

### Medium

- deepen scenario and AI quality
- add stronger post-match UX
- improve sound mix and per-unit/faction differentiation
- improve packaging and build ergonomics

## Known Quality Risks

- presentation polish may hide unresolved simulation or input edge cases
- repeated UI polish without broader playtesting may optimize for screenshots instead of gameplay
- adding content before control feel is finalized may multiply bug surface
- lacking a formal acceptance checklist will let regressions re-enter the client

## Acceptance Metrics

Before calling the client commercially viable, the project should meet metrics like:

- no known blocker bugs in a standard play session
- no client crash during repeated 30-minute internal sessions
- deterministic sim checks remain green
- visual readability remains acceptable at common desktop resolutions
- first-session onboarding succeeds without external explanation
- playtest feedback shifts from “controls/UI are fighting me” to “balance/content/design feedback”

## Recommended Immediate Next Steps

1. Create a bug/feel triage list from the current client
- control bugs
- visibility/HUD bugs
- event/audio/animation bugs

2. Add a formal client playtest checklist
- startup
- menu flow
- command paths
- camera/minimap
- combat readability
- build/production flow
- match end and restart

3. Run a dedicated Phase 1 stabilization pass
- stop adding broad feature scope until the top control and bug issues are closed

4. After stabilization, run one controlled visual cohesion pass
- avoid mixing bug-fixing and major art-direction changes in the same sprint

## Summary

The main gap to commercialization is not the deterministic simulation core. The main gap is the client layer: input feel, readability, consistency, and stability.

The correct strategy is:
- keep the sim
- stabilize the client
- polish control feel
- unify visual language
- improve onboarding and content
- harden release quality

That path is materially safer and cheaper than replacing the engine now.
