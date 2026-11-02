# Starkraft Command Contract

## Purpose

This document defines the intended behavior for the core in-game command paths.

It exists to remove ambiguity from the libGDX client input layer and to provide
clear acceptance criteria for regression tests and manual QA.

Related documents:
- [COMMERCIALIZATION_ROADMAP.md](COMMERCIALIZATION_ROADMAP.md)
- [PHASE1_STABILIZATION_PLAN.md](PHASE1_STABILIZATION_PLAN.md)
- [docs/input-mapping-spec.md](docs/input-mapping-spec.md) (full key map)

Implementation: `sim/src/main/kotlin/client/GameScreen.kt` (`GameInputController`, `resolveEscapeAction`)
and `sim/src/main/kotlin/client/GdxClientRuntime.kt` (`executeAction`, `issueLeftClick`, `issueRightClick`,
`issueMinimapLeftClick`, `issueMinimapRightClick`, `placeBuildingAt`).

## Principles

1. The sim remains authoritative.
2. The client must present one consistent rule per command.
3. Mouse and keyboard should not produce different gameplay semantics for the same command.
4. Every armed command must define:
- enter behavior
- confirm behavior
- cancel behavior
- feedback behavior

## Global Rules

### Selection Rules

- `LMB` on a friendly unit selects it.
- `LMB` drag performs box selection.
- `Esc` resolves in this order: close pause overlay, close help overlay, cancel armed command mode, clear selection, and only if none of those apply, open the pause overlay.
- Selecting a new set of units while armed should only be allowed if explicitly intended by the command mode. Current client: while armed (and a selection exists), `LMB` is always consumed as a confirm and drag-box selection is disabled.

### Armed Command Rules

An armed command is any mode entered before a confirm click.

Armed command examples:
- `move`
- `attack`
- `patrol`
- `rally`
- `build`

Shared rules:
- entering an armed command updates the HUD mode state
- armed mode must show a matching world or HUD hint
- `Esc` always cancels the armed mode
- successful confirm exits the armed mode unless the command explicitly supports repeated placement
- invalid confirm should not silently change selection state
- holding `Shift` on confirm issues the command as queued (`queued = true`) for move/attack/patrol; rally and build ignore it
- arming move/attack/patrol/build requires a selection; without one the client shows `select units first`, plays the invalid cue, and does not arm

### Confirm Surfaces

Allowed confirm surfaces:
- world terrain tile
- entity in world
- minimap, for `move`, `attack`, `patrol` and `rally` (`LMB` or `RMB` on the minimap issues the command at the mapped world point and shows a subtle minimap confirm mark)

Forbidden confirm surfaces:
- command confirm through unrelated HUD cards (clicks on the top bar, bottom HUD, warning banner or overlays never reach world input)
- `build` confirm on the minimap: rejected with `place build in main view` and the invalid cue; build mode stays armed
- accidental entity selection when an armed gameplay command should consume the click

Unarmed minimap input: `LMB` click/drag moves the camera; `RMB` issues the same context command as a world right-click (`Ctrl` = attack-move, `Shift` = queue).

## Command Matrix

## 1. Select

### Enter

- default mode

### Confirm

- `LMB` on a unit or structure updates selection
- `LMB` on empty terrain clears selection unless modifier rules later exist
- `Shift + LMB` toggles the clicked entity in the current selection

Current client gap: click selection picks the nearest entity of the viewed faction (any faction in observer view) with no distance cutoff, so a terrain click normally reselects the nearest owned entity instead of clearing. Use `Esc` to clear.

### Cancel

- none

### Feedback

- world selection confirm flash
- HUD selection pulse
- selection slot pulse when applicable

## 2. Box Select

### Enter

- `LMB` drag from world terrain

### Confirm

- on mouse release, all selectable owned units inside the box become selected
- current client: every entity of the viewed faction inside the box (units and structures; all factions in observer view); `Shift` adds to the selection

### Cancel

- releasing without meaningful drag falls back to click selection (box is drawn after 8px of drag and issued on release when the drag is at least 6px)

### Feedback

- drag box outline
- selection-center confirm flash on release
- HUD selection pulse

## 3. Move

### Enter

- hotkey `M`
- command button `Move`

### Confirm

- `LMB` or `RMB` on world terrain issues move
- clicking another terrain tile while units are already moving retargets the move destination
- the forced move ignores enemies and resource nodes under the cursor (no auto attack/harvest)
- a successful confirm exits move mode

### Cancel

- `Esc`

### Feedback

- command button pulse
- `move armed` notice and move cue when armed
- move ground ping
- command audio for move
- HUD mode hint while armed

### Invalid Cases

- clicking HUD should not issue move
- minimap interaction should not become a move confirm

## 4. Attack

### Enter

- hotkey `A`
- command button `Attack` (action id `attackMove`)

### Confirm

- `LMB` or `RMB` on enemy entity issues attack
- `LMB` or `RMB` on terrain issues attack-move (resource nodes are treated as terrain)
- outside armed mode, `Ctrl + RMB` on terrain issues attack-move directly

### Cancel

- `Esc`

### Feedback

- attack ground ping or attack target response
- attack input audio
- command button pulse
- HUD mode hint while armed

### Invalid Cases

- clicking a friendly entity should not quietly downgrade into selection if attack mode is armed
- invalid attack surface should preserve command state or cancel explicitly, not fail ambiguously
- current client: a friendly entity within 0.8 tiles of the click is rejected with `invalid attack target`, an invalid ground ping and the invalid cue; attack mode stays armed

## 5. Patrol

### Enter

- hotkey `P`
- command button `Patrol`

### Confirm

- `LMB` or `RMB` on terrain issues patrol anchor/route behavior supported by current sim client flow
- current client: a single click writes one `patrol` command with the clicked point; enemies and resource nodes under the cursor are ignored

### Cancel

- `Esc`

### Feedback

- patrol ground ping
- command button pulse
- armed mode hint

### Open Decision

- if patrol later becomes multi-click, the client must explicitly define `first point`, `second point`, and `cancel after first point` behavior

## 6. Rally

### Enter

- hotkey `S`
- command button `Rally` (shown only when the selection contains an entity with `supportsRally`)
- without a rally-capable selection the client shows `select rally building` and does not arm

### Confirm

- `LMB` or `RMB` on world terrain or the minimap writes a `rally` command for the first rally-capable selected building

### Cancel

- `Esc`

### Feedback

- build-style ground ping and build cue
- `rally set` notice
- rally cursor preview while armed

## 7. Build

### Enter

- build command button for a buildable structure
- hotkeys `B` (`Depot`), `R` (`ResourceDepot`), `G` (`GasDepot`)

### Confirm

- `LMB` or `RMB` on valid terrain places build order
- a successful placement exits build mode

### Cancel

- `Esc`

### Feedback

- build placement preview
- valid vs invalid placement colors
- build input audio or invalid input audio
- build ping on valid confirm

### Invalid Cases

- invalid placement must never place the order
- invalid placement must visibly reject the action
- invalid placement must not change selection
- current client: invalid placement shows `invalid build placement`, an invalid ground ping and the invalid cue, and keeps build mode armed

## 8. Cancel Build / Train / Research

### Enter

- direct command button
- hotkeys `X` (cancel build), `T` (cancel train), `Y` (cancel research)

Train and research queueing follow the same immediate pattern: `Train <type>` / `Research <tech>` buttons, or hotkeys `U`/`I`/`O` (first three train options) and `L` (first research option).

### Confirm

- one click only

### Cancel

- none, because this is an immediate command

### Feedback

- command button pulse
- queue/status card updates immediately

## 9. View Switch

### Enter

- `1`, `2`, `3` style faction/observer hotkeys
- command buttons for faction/observer view

### Confirm

- immediate

### Cancel

- none

### Feedback

- top bar mode and status update
- minimap title and view state update
- camera recenter or view recovery should follow a deterministic rule
- current client: switching view does not recenter the camera (`End` centers on the viewed faction); if the viewed faction has no visible tiles the client auto-switches to the faction with the most visible tiles (and centers on it) or to observer, with a `view auto-switched to ...` notice

## 10. Pause

There are two separate pause concepts in the current client.

### Enter

- sim pause: `Space` or the `Pause` command button toggles the sim play-control pause
- pause overlay: `Esc` when no overlay, armed mode or selection is active

### Confirm

- immediate

### Cancel

- same input toggles or closes depending on current state
- sim pause: `Space` / `Pause` again
- pause overlay: `Esc` or the `Resume` button

### Feedback

- top bar status update (`SIM HOLD` for sim pause, `PAUSED` for the overlay)
- pause overlay (`Resume`, `Sim Pause`, `Restart`, quick/alt preset save/load, `Menu`, `Quit`)
- pause card pulse
- while the pause or help overlay is open, world pointer input, command buttons and all hotkeys except `Esc` (and `F1` for help) are blocked

## 11. Help

### Enter

- `F1`
- help command button

### Confirm

- immediate

### Cancel

- `Esc`
- help input again

### Feedback

- help overlay
- overlay header pulse

## Mixed Input Rules

The following combinations must behave identically every time:

1. Hotkey -> `LMB` confirm
2. Hotkey -> `RMB` confirm
3. Hotkey -> `Esc`
4. Button click -> `LMB` confirm
5. Button click -> `RMB` confirm
6. Armed mode -> selection click attempt
7. Armed mode -> HUD click
8. Armed mode -> minimap interaction

## Required Regression Tests

Minimum runtime regression coverage:

1. `move` armed then `LMB`
2. `move` armed then `RMB`
3. `attack` armed then enemy click
4. `attack` armed then terrain click
5. `build` armed then valid placement
6. `build` armed then invalid placement
7. `Esc` cancels armed mode
8. selection change after armed-mode cancel
9. hotkey path and button-click path produce the same runtime state

Current coverage lives in `sim/src/test/kotlin/starkraft/sim/GdxClientRuntimeTest.kt` (arming, confirm, friendly-target rejection, build placement, rally, minimap commands, queued commands),
`GameScreenInputRulesTest.kt` (drag vs armed, overlay blocking, `Esc` priority) and `ClientControllerContractTest.kt` (forced move/patrol/attack-move intents).

## Manual QA Checklist

For each command above:

1. Enter command with hotkey.
2. Enter command with button.
3. Confirm with `LMB`.
4. Confirm with `RMB`, if supported.
5. Cancel with `Esc`.
6. Try clicking:
- empty terrain
- friendly unit
- enemy unit
- HUD
- minimap

If any path produces a different outcome than this contract, it is a Phase 1 bug.
