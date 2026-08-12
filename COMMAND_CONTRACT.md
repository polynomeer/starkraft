# Starkraft Command Contract

## Purpose

This document defines the intended behavior for the core in-game command paths.

It exists to remove ambiguity from the libGDX client input layer and to provide
clear acceptance criteria for regression tests and manual QA.

Related documents:
- [COMMERCIALIZATION_ROADMAP.md](/Users/hammac/Projects/starkraft/COMMERCIALIZATION_ROADMAP.md)
- [PHASE1_STABILIZATION_PLAN.md](/Users/hammac/Projects/starkraft/PHASE1_STABILIZATION_PLAN.md)

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
- `Esc` clears armed command mode first, then clears selection if no armed mode is active.
- Selecting a new set of units while armed should only be allowed if explicitly intended by the command mode.

### Armed Command Rules

An armed command is any mode entered before a confirm click.

Armed command examples:
- `move`
- `attack`
- `patrol`
- `build`

Shared rules:
- entering an armed command updates the HUD mode state
- armed mode must show a matching world or HUD hint
- `Esc` always cancels the armed mode
- successful confirm exits the armed mode unless the command explicitly supports repeated placement
- invalid confirm should not silently change selection state

### Confirm Surfaces

Allowed confirm surfaces:
- world terrain tile
- entity in world
- minimap, only for view movement, not gameplay commands

Forbidden confirm surfaces:
- command confirm through unrelated HUD cards
- accidental entity selection when an armed gameplay command should consume the click

## Command Matrix

## 1. Select

### Enter

- default mode

### Confirm

- `LMB` on a unit or structure updates selection
- `LMB` on empty terrain clears selection unless modifier rules later exist

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

### Cancel

- releasing without meaningful drag falls back to click selection

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

### Cancel

- `Esc`

### Feedback

- command button pulse
- move ground ping
- command audio for move
- HUD mode hint while armed

### Invalid Cases

- clicking HUD should not issue move
- minimap interaction should not become a move confirm

## 4. Attack

### Enter

- hotkey `A`
- command button `Attack`

### Confirm

- `LMB` or `RMB` on enemy entity issues attack
- `LMB` or `RMB` on terrain issues attack-move

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

## 5. Patrol

### Enter

- hotkey `P`
- command button `Patrol`

### Confirm

- `LMB` or `RMB` on terrain issues patrol anchor/route behavior supported by current sim client flow

### Cancel

- `Esc`

### Feedback

- patrol ground ping
- command button pulse
- armed mode hint

### Open Decision

- if patrol later becomes multi-click, the client must explicitly define `first point`, `second point`, and `cancel after first point` behavior

## 6. Build

### Enter

- build command button for a buildable structure

### Confirm

- `LMB` or `RMB` on valid terrain places build order

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

## 7. Cancel Build / Train / Research

### Enter

- direct command button

### Confirm

- one click only

### Cancel

- none, because this is an immediate command

### Feedback

- command button pulse
- queue/status card updates immediately

## 8. View Switch

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

## 9. Pause

### Enter

- `Space`
- pause command button

### Confirm

- immediate

### Cancel

- same input toggles or closes depending on current state

### Feedback

- top bar status update
- pause overlay
- pause card pulse

## 10. Help

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
