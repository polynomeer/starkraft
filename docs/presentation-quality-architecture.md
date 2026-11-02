# Presentation Quality Architecture

## 목적

[`docs/presentation-quality-improvement-plan.md`](presentation-quality-improvement-plan.md)의 문제의식을 현재 `starkraft` 코드베이스에 맞는 실행 설계로 변환한다.

관련 하위 계약 문서:

- [`docs/presentation-theme-spec.md`](presentation-theme-spec.md)
- [`docs/hud-layout-contract.md`](hud-layout-contract.md)
- [`docs/battlefield-visual-contract.md`](battlefield-visual-contract.md)

이 문서는 다음을 전제로 한다.

- 결정론 시뮬레이션과 libGDX 클라이언트는 유지한다.
- `sim`의 규칙/상태를 presentation 레이어가 덮어쓰지 않는다.
- "게임처럼 보이지 않는 문제"를 기능 부족이 아니라 표현 파이프라인 부재 문제로 본다.
- 대규모 엔진 교체나 3D 전환은 당장 주 경로가 아니다.

## 핵심 결론

원문 문서의 방향은 대체로 맞다. 특히 다음 진단은 정확하다.

- 기능 개발과 표현 개발이 분리되어야 한다.
- 비주얼 타깃 없이 개별 개선을 반복하면 결과가 통일되지 않는다.
- 체감 품질에서 VFX, 애니메이션, 오디오, UI 연출의 비중이 크다.

하지만 현재 저장소 기준으로는 다음 식으로 번역해야 한다.

1. `2D 절차 렌더 + 선택적 스프라이트` 구조를 정식 presentation pipeline으로 승격한다.
2. 화면 품질을 `렌더링 레이어`, `피드백 레이어`, `자산 파이프라인`, `UI 시스템`으로 분리한다.
3. 3D/2.5D 전환은 "나중에 고려할 옵션"으로 남기고, 지금은 2D 경로에서 상용 수준 가독성과 감각을 만든다.

## 현재 코드베이스 기준 진단

현재 presentation 관련 핵심 진입점은 다음이다.

- [`sim/src/main/kotlin/client/GameScreen.kt`](../sim/src/main/kotlin/client/GameScreen.kt)
- [`sim/src/main/kotlin/client/GdxWorldRenderer.kt`](../sim/src/main/kotlin/client/GdxWorldRenderer.kt)
- [`sim/src/main/kotlin/client/GdxClientRuntime.kt`](../sim/src/main/kotlin/client/GdxClientRuntime.kt)
- [`sim/src/main/kotlin/client/GdxUiAssets.kt`](../sim/src/main/kotlin/client/GdxUiAssets.kt)
- [`sim/src/main/kotlin/client/VisualAssetCatalog.kt`](../sim/src/main/kotlin/client/VisualAssetCatalog.kt)
- [`sim/src/main/kotlin/client/GdxSpriteAssets.kt`](../sim/src/main/kotlin/client/GdxSpriteAssets.kt)

현재 상태의 강점:

- 시뮬레이션과 렌더링 책임이 이미 분리되어 있다.
- 런타임이 선택, 공격 경고, 피격, 죽음, 완료 이벤트를 presentation-friendly state로 가공하고 있다.
- 스프라이트 매니페스트와 기본 자산 경로가 이미 존재한다.
- HUD와 월드 렌더가 별도 클래스라서 단계적 교체가 가능하다.

현재 상태의 한계:

- 월드가 아직 "렌더링 시스템"보다 "큰 단일 렌더러"에 가깝다.
- 유닛 표현이 상태 기반 silhouette 수준에 머물러 있어 type fantasy가 약하다.
- HUD는 기능은 많지만 정보 계층, 텍스트 리듬, 고정 슬롯 전략이 약하다.
- 효과/사운드가 이벤트 반응형 수준이지 연출 규칙 기반은 아니다.
- style token은 색상만 중앙화되어 있고 typography/spacing/motion/audio 토큰은 실제 코드에서 쓰이지 않는다.

## 현재 구현 상태

코드와 git 이력(`git log -- sim/src/main/kotlin/client`) 기준 요약이다.

| 영역 | 상태 | 근거 |
| --- | --- | --- |
| 색상 토큰 (`PresentationTheme`) | 완료 | `GdxUiAssets.kt`, 커밋 `d27a2f5` 이후 HUD/렌더러 색 토큰화 |
| HUD 고정 슬롯 / 계약 폭 | 완료 | `computeTopBarLayout`, `computeBottomHudLayout`, 고정 hint 슬롯(`formatFixedHudHintSlot`) |
| disabled reason 표준화 | 완료 | `resolveCommandButtonDisabledReason` |
| command deck 재구성 | 완료 | 유닛 3열 / 건물 2열 그룹 deck (`resolveCommandDeckGroups`) |
| selection card 계층 재배치 | 완료 | headline / status / queue / roster 분리, 중복 상세 줄 숨김 |
| silhouette / selection / effect / minimap 통일 | 대부분 완료 | procedural silhouette, lime bracket, ground ping, death burst, minimap 마커 |
| world renderer pass 분리 | 미착수 | `GdxWorldRenderer` 단일 클래스 안의 `draw*` 함수 순서로만 분리 |
| asset manifest 확장 / atlas | 미착수 | `SpriteAssetRef`는 v1 필드만. sprite draw 경로는 커밋 `5149344`에서 제거되어 현재 procedural만 렌더 |
| shader / post-processing | 미착수 | ShapeRenderer/SpriteBatch 기본 셰이더만 사용 |
| audio bus / priority / spatial | 미착수 | 합성 tone + pitch/gain 순환 + key별 cooldown만 존재 |

후속 산출물 5종(theme spec, battlefield contract, HUD layout contract, asset manifest v2 spec, audio feedback contract)은 모두 작성되어 있다.

## 비주얼 타깃

목표는 "스타크래프트를 흉내내는 UI"가 아니라 다음 네 가지를 동시에 만족하는 것이다.

1. 전장 가독성
2. RTS 조작 감각
3. 하나의 세계처럼 느껴지는 통일감
4. 저사양에서도 유지되는 안정적 프레임

### 추천 아트 방향

현재 코드와 생산성을 고려하면 다음 조합이 가장 안전하다.

- 월드: 픽셀 아트가 아니라 `stylized tactical 2D`
- UI: `sci-fi tactical FUI`
- 색상: dark navy base + muted teal structure + lime selection + amber alert + red danger
- 모션: 짧고 명확한 기능성 모션, 과장된 bounce 금지

이 방향은 `game-art` 기준으로도 합리적이다.

- 배경 디테일보다 silhouette readability 우선
- 같은 오브젝트는 같은 색상군 유지
- 장식보다 역할 전달 우선

또한 `ui-ux-pro-max` 기준으로도 다음 제약을 반드시 따른다.

- hover만으로 중요한 상호작용을 숨기지 않는다.
- 패널 크기나 텍스트 길이에 따라 HUD가 흔들리지 않도록 슬롯을 고정한다.
- body text 대비와 line-height를 안정적으로 유지한다.
- 긴 숫자는 단위 축약 또는 구분자를 사용한다.

## 목표 아키텍처

## 1. Presentation Layer 분리

현재 `GdxWorldRenderer`와 `GameScreen`이 담당하는 내용을 아래 구조로 쪼갠다.

```text
Presentation
├── RuntimeState
│   ├── GdxClientRuntime
│   ├── event state
│   └── audio cues
├── WorldRenderPipeline
│   ├── TerrainPass
│   ├── StructurePass
│   ├── UnitPass
│   ├── EffectPass
│   ├── OverlayPass
│   └── MinimapPass
├── HudPipeline
│   ├── TopBarPresenter
│   ├── SelectionPresenter
│   ├── CommandDeckPresenter
│   ├── AlertPresenter
│   └── Pause/HelpPresenter
├── PresentationAssets
│   ├── visual manifest
│   ├── sprite atlases
│   ├── ui skin tokens
│   ├── sfx banks
│   └── shader programs
└── PresentationTuning
    ├── timing constants
    ├── color tokens
    ├── animation curves
    └── audio variation rules
```

핵심은 "기능별 giant file"을 줄이고 "패스별 책임"을 세우는 것이다.

## 2. World Rendering Pipeline

`GdxWorldRenderer`는 최종적으로 다음 순서의 패스를 가진다.

```text
Clear
→ TerrainPass
→ TerrainDetailPass
→ ResourcePass
→ StructurePass
→ UnitPass
→ CombatEffectPass
→ CommandEffectPass
→ SelectionOverlayPass
→ FogPass
→ MinimapPass
→ ScreenSpaceLabelPass
```

### TerrainPass

역할:

- 타일 base tone
- blocked/occupied tile material
- biome variation

규칙:

- 지형 명암 대비는 낮게 유지한다.
- 전투 정보보다 지형이 튀면 실패다.
- 타일 detail은 zoom/visible range 기준으로 단계적으로 줄인다.

### StructurePass

역할:

- 건물 silhouette
- footprint readability
- team tint
- build/production state stripe

규칙:

- 건물은 `정면 외형`보다 `맵 위 footprint 판독성`이 우선이다.
- 건물 종류는 roof/edge/trim language로 구분한다.
- under construction, active production, active research는 동일한 규칙으로 표시한다.

### UnitPass

역할:

- 유닛 base body
- class-specific silhouette
- facing/motion cue
- carrying/attacking/damaged states

규칙:

- 선택 여부 때문에 내부를 채색하지 않는다.
- 선택 표시는 외곽 ring, corner bracket, pulse만 사용한다.
- worker, ranged, melee, hero, flying 같은 역할별 silhouette rule을 만든다.

### EffectPass

역할:

- muzzle flash
- projectile trail
- impact spark
- death burst
- build complete flash
- command confirm ping

규칙:

- 효과는 짧고 읽혀야 한다.
- 같은 프레임에 여러 효과가 겹쳐도 battlefield hierarchy를 무너뜨리지 않아야 한다.
- effect 색은 "무기 타입 / 행동 타입 / 경고 타입" 중심으로 통일한다.

## 3. HUD Architecture

현재 HUD의 가장 큰 문제는 "정보가 많다"가 아니라 "정보 계층이 섞인다"는 점이다.

재설계 원칙:

### 고정 슬롯

HUD는 내용 길이에 따라 레이아웃이 흔들리면 안 된다.

- top bar width 고정
- command deck button grid 고정
- hover hint line 고정
- selection roster grid 고정
- minimap frame 고정

### 정보 계층

상시 정보:

- 자원
- 공급량
- 현재 시점 faction / observer 상태
- 현재 선택 수

선택 정보:

- 대표 portrait 또는 silhouette
- 체력/실드/에너지
- 상태 태그
- 생산/연구/건설 진행

행동 정보:

- 가능한 명령
- 현재 armed command
- 명령 실패 이유
- 현재 queue 요약

알림 정보:

- 공격받는 중
- 생산 완료
- 연구 완료
- 자원 부족

### 시각 규칙

- body text는 최소 16px equivalent 기준으로 읽혀야 한다.
- muted text는 배경과 충분한 대비를 유지한다.
- line-height는 1.5 전후를 유지한다.
- 중요한 텍스트는 색상만이 아니라 위치/크기/배지 형태로도 구분한다.

## 4. Animation and Motion Model

원문 문서의 핵심 주장 중 "애니메이션이 그래픽보다 중요하다"는 부분은 그대로 채택한다.

단, 현재 구조에서는 skeletal animation보다 `state-driven motion language`부터 정리한다.

### 우선 적용할 모션

- idle hover/breath
- move lean / trailing shadow offset
- attack wind-up pulse
- impact recoil
- death collapse + remains fade
- build complete burst
- selection confirm pulse

### 타이밍 원칙

- selection confirm: 180ms ~ 260ms
- move confirm ping: 220ms ~ 360ms
- attack impact: 80ms ~ 140ms
- death burst: 220ms ~ 420ms
- HUD hover emphasis: 120ms ~ 180ms

주의:

- 동일 duration을 모든 애니메이션에 쓰지 않는다.
- 레이아웃 bounds를 바꾸는 애니메이션은 금지한다.
- reduced-motion 옵션을 나중에라도 고려할 수 있게 timing token으로 관리한다.

## 5. Audio Architecture

현재 `playSoundVariant` 수준의 변형은 시작으로는 충분하지만 구조가 더 필요하다.

목표 구조:

```text
Audio
├── UiBus
├── WorldBus
├── CombatBus
├── AlertBus
├── MusicBus
└── AmbienceBus
```

우선순위 규칙:

- hero/major alert
- selected unit response
- nearby combat
- distant combat
- ambient tail

필수 개선:

- 같은 action에 3개 이상 variation
- pitch/volume micro variation
- cooldown + priority gate
- camera-distance based attenuation

즉시 구현 가치가 큰 항목:

- move acknowledge variation bank
- attack acknowledge variation bank
- impact material split
- death unit vs structure split
- under-attack alert ducking

## 6. Asset Pipeline

지금 이미 아래 기반은 존재한다.

- [`sim/src/main/resources/data/visuals.json`](../sim/src/main/resources/data/visuals.json)
- [`sim/src/main/resources/assets/`](../sim/src/main/resources/assets/)
- [`sim/src/main/kotlin/client/VisualAssetCatalog.kt`](../sim/src/main/kotlin/client/VisualAssetCatalog.kt)

이 기반을 production-ready asset pipeline으로 바꾼다.

### 파일 체계

```text
assets/
├── sprites/
│   ├── units/
│   ├── structures/
│   ├── resources/
│   ├── terrain/
│   ├── effects/
│   └── ui/
├── atlases/
├── audio/
│   ├── ui/
│   ├── combat/
│   ├── ambience/
│   └── voice/
└── shaders/
```

### 자산 규칙

- 타입별 source resolution을 정의한다.
- alpha crop, anchor point, scale rule을 메타데이터로 가진다.
- 팀 컬러는 원본 스프라이트를 오염시키지 않고 tint channel로 제어한다.
- atlas packing과 fallback procedural rendering을 동시에 유지한다.

### 매니페스트 확장 제안

현재 `SpriteAssetRef`에 다음을 추가할 가치가 있다.

- `anchorX`, `anchorY`
- `shadowAsset`
- `outlineStyle`
- `animationSet`
- `variantGroup`
- `layer`

## 7. Shaders and Screen Effects

원문처럼 shader는 분명히 효과가 크다. 다만 순서를 잘라서 도입해야 한다.

### Phase 1 shader

- hit flash tint
- team tint blend
- selection glow
- fog darkening

### Phase 2 shader

- terrain blend
- build hologram preview
- additive projectile glow
- death dissolve

### Phase 3 post-processing

- light bloom on strong effects only
- subtle vignette
- color grading LUT or simple screen tint

주의:

- post-processing은 반드시 옵션화한다.
- baseline performance를 유지한 뒤에만 활성화한다.
- screen-wide effect는 전장 가독성을 해치지 않는 수준으로 제한한다.

## 8. Design Token System

지금 가장 부족한 것은 "공통 취향"이 아니라 "공통 토큰"이다.

반드시 중앙집중형 토큰을 둔다.

```text
PresentationTheme
├── colors
├── typography
├── spacing
├── radius
├── border alpha
├── glow strength
├── pulse timings
└── audio gain defaults
```

권장 색상군:

- background: `#0F172A`
- card: `#192134`
- muted: `#0F1F2B`
- selection primary: `#22C55E`
- selection soft: `#86EFAC`
- accent: `#D97706`
- warning: `#F59E0B`
- destructive: `#DC2626`
- text primary: `#FFFFFF`
- text muted: `#94A3B8`

이 팔레트는 dark tactical UI에 적합하고, 현재 lime selection 요구와도 맞다.

## 구현 로드맵

## Phase A. Presentation Foundation

목표:

- 대형 리팩터링 없이 기준선과 토큰을 만든다.

작업:

- HUD 색/폰트/간격 토큰 추출
- `GdxWorldRenderer` pass 분리 시작
- 자산 매니페스트 확장 설계
- command/selection/effect color contract 문서화

완료 기준:

- 새로운 시각 요소를 넣을 때 하드코딩 색이 아니라 토큰을 사용한다.

현재: 토큰 추출(색상)과 문서화는 완료, pass 분리는 미착수, 폰트/간격 토큰은 미구현.

## Phase B. Battlefield Readability

목표:

- 전장 정보 판독성 향상

작업:

- 유닛별 silhouette pass
- selection ring/bracket 통일
- damage/attack/build/research effect 통일
- minimap 심벌과 camera viewport 정리

완료 기준:

- 유닛 역할, 선택 상태, 전투 상태를 1초 이내 판독 가능

현재: 작업 항목 4개 모두 1차 구현 완료 (silhouette, selection bracket, effect palette, minimap 마커/viewport).

## Phase C. Command and HUD Cohesion

목표:

- 조작과 UI를 하나의 시스템처럼 느끼게 만든다.

작업:

- command deck 재구성
- hover hint 고정 슬롯화
- disabled reason UI 표준화
- selection card 정보 계층 재배치

완료 기준:

- HUD가 정보는 많아도 산만하지 않다.
- hover/tooltip로 패널 폭이 흔들리지 않는다.

현재: 작업 항목 4개 모두 구현 완료.

## Phase D. Asset and Motion Upgrade

목표:

- 프로토타입 느낌 제거

작업:

- 스프라이트 세트 교체
- atlas 기반 자산 로딩
- 이동/공격/죽음/생산 상태 모션 강화
- 효과 레이어 추가

완료 기준:

- 플레이 영상을 정지시켜도 placeholder 느낌이 크게 줄어든다.

현재: 상태 모션(bob, stride, recoil, death remains)만 일부 구현. 스프라이트 교체와 atlas 로딩은 미착수.

## Phase E. Audio and FX Finish

목표:

- 체감 품질 마감

작업:

- variation bank 도입
- audio bus 분리
- alert ducking
- hit/build/research FX 사운드 계층화

완료 기준:

- 반복 명령과 전투가 덜 싸구려처럼 들린다.

현재: 미착수.

## 구현 우선순위

가장 먼저 해야 할 것:

1. presentation token 문서화 (완료)
2. HUD 고정 슬롯/고정 크기 전략 (완료)
3. unit/structure/effect 색상 계약 (완료)
4. world renderer pass 분리 (미착수)
5. asset manifest 확장 (스펙 문서만 작성)

이후:

1. sprite atlas 도입
2. effect set 확장
3. shader 1단계
4. audio bus/priority

## 하지 말아야 할 것

- 당장 3D 전환부터 시작하는 것
- HUD를 계속 `Table` 단위 감으로만 증설하는 것
- 이벤트마다 새 색을 즉흥적으로 추가하는 것
- 스프라이트를 넣기 전에 silhouette 계약 없이 자산부터 늘리는 것
- post-processing으로 기본 가독성 문제를 덮으려는 것

## 산출물 제안

이 문서 다음에 바로 만들어야 할 후속 산출물:

1. [`presentation-theme-spec.md`](presentation-theme-spec.md) (작성됨)
2. [`battlefield-visual-contract.md`](battlefield-visual-contract.md) (작성됨)
3. [`hud-layout-contract.md`](hud-layout-contract.md) (작성됨)
4. [`asset-manifest-v2-spec.md`](asset-manifest-v2-spec.md) (작성됨)
5. [`audio-feedback-contract.md`](audio-feedback-contract.md) (작성됨)

## 결론

이 프로젝트가 지금 상용 수준으로 올라가지 못하는 이유는 엔진 한계보다 `presentation architecture`의 부재에 더 가깝다.

현재 구조는 이미 개선 가능한 상태다. 핵심은 다음이다.

- 기능 개발과 표현 개발을 분리한다.
- 월드/HUD/효과/오디오를 시스템으로 나눈다.
- style token과 visual contract를 먼저 세운다.
- 3D 전환보다 2D 경로의 통일감과 감각을 먼저 완성한다.

이 문서의 목표는 "예쁘게 만들자"가 아니라 "반복 가능한 presentation production system을 만들자"에 있다.
