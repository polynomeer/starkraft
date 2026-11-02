# Battlefield Visual Contract

## 목적

전장 위에 그려지는 모든 시각 요소가 "예뻐 보이기"보다 "즉시 읽히기"를 우선하도록 규정한다.

이 문서는 다음 질문에 답한다.

- 어떤 유닛이 선택되었는가
- 어떤 유닛이 적인가
- 지금 전투 중인가
- 방금 어떤 명령이 내려졌는가
- 어떤 구조물이 생산/연구/건설 중인가

## 현재 구현 상태

[`GdxWorldRenderer.kt`](../sim/src/main/kotlin/client/GdxWorldRenderer.kt) 기준. 모든 월드 표현은 ShapeRenderer procedural 렌더이며 sprite는 현재 그리지 않는다.

- 렌더 순서(월드 scissor 안): terrain → resources → (fog: no-op) → entities → activity markers → order markers → ground ping → selection click pulse → death remains → death bursts → build preview → armed command preview → drag box, 이어서 line pass(grid, terrain edge, world frame, selection bracket, selection overlay). 그 뒤 minimap, minimap viewport, 라벨 순
- 구현됨: lime selection bracket/overlay와 confirm pulse, 피격 flash(melee/ranged별 alpha·색 차등), damage recoil·critical shake, muzzle flash, impact spark, death burst + remains(구조물은 더 큰 burst), 건설/생산/연구 완료 flash, 생산/연구/건설 진행 activity marker, queued order marker와 rally/build chevron trail, armed 명령 cursor preview(move/attack/patrol/rally), build placement preview
- command ping 색은 아래 표와 다르다: move = `state.selection`(lime), attack = `state.warning`(amber), build = `state.build`에 `commandSoft` 혼합, invalid = `state.danger`
- 미구현: projectile tracer, impact smoke, damaged 구조물 smoke/crack, hero silhouette, effect 중첩 시 약화 규칙의 일반화
- fog: 월드 fog pass(`drawFog`)는 비어 있다. viewed faction 시야 밖 엔티티는 그리지 않고, visible/explored 음영은 미니맵에서만 표현한다
- minimap: 구조물/전투/worker/support별 마커 크기와 역할 색, 자원 노드, 선택 강조, 실제 world viewport 기준 viewport box가 구현되어 있다

## 기본 원칙

1. 가독성이 장식보다 우선이다.
2. 상태 표시는 내부 채색보다 외곽, 링, pulse로 전달한다.
3. 같은 상태는 같은 위치, 같은 색상군, 같은 모션군을 사용한다.
4. 전장 지형은 정보를 전달하지 않으면 뒤로 물러서야 한다.

## Visual Hierarchy

우선순위:

1. selection
2. imminent danger
3. active combat impact
4. command confirmation
5. production/research/build state
6. faction identity
7. terrain decoration

의미:

- 플레이어가 가장 자주 찾는 정보가 항상 가장 먼저 읽혀야 한다.
- 팀 컬러보다 selection이 우선이다.
- 아름다운 지형보다 공격받는 사실이 우선이다.

## Unit Contract

### Role Silhouette

| Role | Shape Language |
| --- | --- |
| worker | compact body, tool arm, cargo cue |
| ranged | balanced upper body, muzzle/front cue |
| melee | forward-lean, shorter reach, aggressive stance |
| hero/champion | larger crown silhouette, stronger trim, unique idle pulse |
| structure-builder | worker silhouette + build effect cue |

### State Layers

순서:

1. base body
2. team trim
3. role accent
4. state overlay
5. selection ring/bracket

### Selection Rules

- 내부 fill 금지
- primary ring는 lime-green
- corner bracket는 thin technical shape
- confirm 시 짧은 pulse 가능
- persistent selection은 steady, non-flashing

### Damage Rules

- 피해 직후 짧은 flash
- melee와 ranged impact는 다른 accent 사용
- critical state는 body 전체 flashing보다 HP and smoke cue 우선

## Structure Contract

### Silhouette

- footprint가 즉시 읽혀야 한다
- roof, vents, trim, intake, tower 등으로 type 구분
- building body는 저명암, roof/trim이 역할 구분 담당

### State Cues

| State | Cue |
| --- | --- |
| under construction | scaffold/build stripe + progress energy |
| producing | amber cadence strip |
| researching | cyan signal strip |
| damaged | dark smoke + crack highlight |
| selected | lime bracket + footprint emphasis |

## Resource Contract

- minerals: cool bright crystalline read
- gas: soft green glow pocket
- resource node는 전장에서 과하게 크면 안 된다
- minimap에서는 resource icon이 unit icon보다 미세하게 작아야 한다

## Terrain Contract

지형 목표:

- 반복되어도 눈에 거슬리지 않을 것
- 유닛과 이펙트를 묻지 않을 것
- blocked tile은 플레이 규칙을 분명히 전달할 것

규칙:

- terrain contrast는 mid-low 유지
- ridge/dust/metal 패턴은 sparse하게 사용
- blocked/occupied tile은 decoration이 아니라 navigation 정보로 취급

## Effect Contract

### Command Effects

| Command | Color | Motion |
| --- | --- | --- |
| move | teal-cyan (현재 lime) | short ripple |
| attack | orange-red (현재 amber) | sharp pulse |
| build | green-cyan | grid/placement cue |
| invalid | red | brief broken pulse |

### Combat Effects

| Event | Required Cue |
| --- | --- |
| muzzle fire | short flash near source |
| projectile travel | light trail or tracer |
| impact | spark + micro smoke |
| death | burst + remains |
| completion | radial or cross burst |

규칙:

- 효과는 한 프레임 늦지 않게 느껴져야 한다
- effect duration은 길게 끌지 않는다
- 같은 위치에 여러 effect가 쌓이면 가장 중요한 것만 남기고 나머지는 약화

## Fog and Vision Contract

- visible area: low-opacity fog only
- explored but unseen: darker shroud
- unseen: information 차단이 우선
- fog는 미니맵과 월드에서 같은 논리를 사용해야 한다

규칙:

- fog 때문에 전체 화면이 L자나 박스 형태로 잘못 가려져 보이면 실패
- vision state는 clipping bug처럼 보이지 않도록 자연스럽게 들어와야 한다

## Minimap Contract

- terrain는 축약 표현
- unit dot는 faction readability 우선
- selected unit or current viewport는 명확한 선으로 표시
- minimap symbol은 HUD 배경에 묻히면 안 된다

규칙:

- viewport box는 실제 world viewport 기준
- minimap entity mark는 너무 작아 "아무것도 없는 것처럼" 보이면 안 된다
- resource, structure, unit의 상대적 구분이 있어야 한다

## Animation Contract

- idle motion: subtle
- move motion: directional
- attack motion: wind-up → impact → recover
- death motion: burst → remains
- construction completion: distinct but short

규칙:

- 무게감은 속도가 아니라 타이밍과 impact frame으로 만든다
- walk와 attack은 프레임 없이도 state-driven offset/pulse로 차별화 가능해야 한다

## Acceptance Criteria

- 선택된 유닛은 전장 어디에서도 즉시 찾을 수 있다
- 전투 발생 지점이 HUD를 보지 않아도 읽힌다
- 건물의 상태가 외곽 cue만으로 구분된다
- 지형이 유닛보다 더 튀지 않는다
- 미니맵만 보고도 현재 위치와 병력 분포를 읽을 수 있다

