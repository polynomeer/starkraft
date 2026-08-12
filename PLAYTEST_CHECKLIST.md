# Starkraft Playtest Checklist

## Purpose

This is the manual validation checklist for the current desktop client.

It is intended for:
- Phase 1 stabilization
- pre-release smoke validation
- regression checks after input, HUD, minimap, rendering, and session-flow changes

Related documents:
- [COMMERCIALIZATION_ROADMAP.md](/Users/hammac/Projects/starkraft/COMMERCIALIZATION_ROADMAP.md)
- [PHASE1_STABILIZATION_PLAN.md](/Users/hammac/Projects/starkraft/PHASE1_STABILIZATION_PLAN.md)
- [COMMAND_CONTRACT.md](/Users/hammac/Projects/starkraft/COMMAND_CONTRACT.md)

## Test Environment

Validate at minimum:
- `1280x720`
- `1440x900`
- `1600x900`
- `1920x1080`

Run path:

```bash
cd /Users/hammac/Projects/starkraft
./gradlew :sim:play
```

## Result Codes

- `PASS` expected behavior observed
- `FAIL` behavior incorrect or missing
- `BLOCKER` prevents normal play
- `NOTE` non-blocking observation worth tracking

## 1. Startup and Menu

### 1.1 Launch

- app starts without crash
- main menu renders correctly
- no clipped menu sections
- no broken button layout
- keyboard navigation works if supported

### 1.2 Start Match

- default scenario launches
- transition from menu to game screen works
- no blank screen
- no missing world render

### 1.3 Restart / Scenario Change

- restart returns to live playable session
- scenario switch starts the intended scenario
- no stale session state leaks into the next run

## 2. Core Selection

### 2.1 Single Select

- `LMB` on owned unit selects it
- selection highlight appears in world
- selection appears in HUD
- top bar selection summary updates

### 2.2 Empty Click

- `LMB` on empty terrain clears selection when not in armed mode
- no wrong command fires

### 2.3 Drag Select

- box outline appears correctly
- release selects units in the dragged area
- confirm flash appears
- drag box does not fill the battlefield with opaque color

### 2.4 Dense Combat Selection

- selected units remain identifiable in combat
- health bars and selection markers remain readable
- selected unit status is still visible in HUD

## 3. Core Commands

### 3.1 Move

- `M` then `LMB` moves selected units
- `M` then `RMB` moves selected units
- move button click then `LMB` moves selected units
- repeated move retarget changes destination in flight
- move ping and audio confirm fire correctly

### 3.2 Attack

- `A` then enemy click issues direct attack
- `A` then terrain click issues attack-move
- attack button click then `LMB` works
- attack feedback is visible and audible

### 3.3 Patrol

- patrol command enters armed mode correctly
- patrol confirm path behaves consistently
- patrol feedback appears correctly

### 3.4 Build

- build preview appears when armed
- valid placement is clearly readable
- invalid placement is clearly rejected
- `LMB` place works
- `RMB` place works if supported by current contract
- invalid placement does not place structure

### 3.5 Cancel Paths

- `Esc` cancels armed `move`
- `Esc` cancels armed `attack`
- `Esc` cancels armed `build`
- canceling armed mode does not break selection state

## 4. Camera and Minimap

### 4.1 Edge Pan

- edge pan starts smoothly
- edge pan does not jump
- edge pan speed feels predictable

### 4.2 Middle Drag

- middle drag pans the camera
- small jitter does not shake the screen
- drag does not overshoot badly

### 4.3 Minimap Click

- minimap click recenters the camera
- minimap confirm flash appears
- minimap is never visually hidden by HUD

### 4.4 Minimap Drag

- minimap drag updates the view continuously
- drag confirm is visible but not noisy
- viewport box remains readable

### 4.5 Camera Bounds

- moving to all four map corners works
- no broken clipping
- no `L`-shaped world masking
- no world render hidden behind incorrect viewport math

## 5. HUD and Visibility

### 5.1 Top Bar

- economy card readable
- selection card readable
- mode card readable
- status card readable
- no overlap at supported resolutions

### 5.2 Bottom HUD

- minimap readable
- selection panel readable
- command deck readable
- no major battlefield obstruction

### 5.3 Overlays

- `UNDER ATTACK` warning appears when expected
- structure-loss warning appears when expected
- pause overlay opens correctly
- help overlay opens correctly
- overlays do not permanently block gameplay after close

### 5.4 World Feedback

- selection markers readable
- health bars readable
- command pings readable
- hit flashes readable
- death remains visible but not too noisy

## 6. Economy and Production

### 6.1 Harvest

- worker can be selected
- worker can gather minerals
- worker can gather gas if available
- return/dropoff behavior is visible

### 6.2 Build

- build enters correctly
- construction progresses visibly
- completion feedback appears

### 6.3 Train and Research

- train action enters queue
- research action enters queue
- queue state updates in HUD
- completion feedback appears in world and HUD

### 6.4 Cancel Queue

- cancel build works
- cancel train works
- cancel research works
- HUD queue updates immediately

## 7. Combat Readability

### 7.1 Ranged Combat

- muzzle flash visible
- projectile/tracer readable
- impact point readable
- ranged audio triggers correctly

### 7.2 Melee Combat

- windup visible
- slash or melee hit readable
- melee audio triggers correctly

### 7.3 Death and Collapse

- unit death feedback visible
- unit death sound plays
- structure collapse feedback visible
- structure collapse sound plays
- remains fade naturally

### 7.4 Notices

- kill notice appears
- loss notice appears
- trade notice appears
- structure-loss warning appears for owned structure loss

## 8. Session Flow

### 8.1 Pause

- pause toggles correctly
- pause status appears in HUD
- unpause returns control cleanly

### 8.2 Help

- help opens
- help closes with expected input
- gameplay resumes normally

### 8.3 Match End

- end-of-match state is visible
- client remains stable after match end
- restart from match-end flow works

## 9. Stability Sweep

Run one uninterrupted session of at least 30 minutes and verify:

- no crash
- no JSON decode exception
- no frozen input state
- no broken minimap render
- no severe command desync
- no uncloseable overlay
- no permanent HUD corruption

## 10. Bug Log Template

Record each failure as:

- `Area:`
- `Severity:`
- `Repro steps:`
- `Expected:`
- `Actual:`
- `Resolution:`

Severity:
- `BLOCKER`
- `MAJOR`
- `MINOR`
- `POLISH`

## Signoff Rule

The build passes manual playtest only if:

1. no `BLOCKER` remains
2. no unresolved crash remains
3. no core command in [COMMAND_CONTRACT.md](/Users/hammac/Projects/starkraft/COMMAND_CONTRACT.md) fails
4. no minimap or battlefield visibility issue remains at supported resolutions
