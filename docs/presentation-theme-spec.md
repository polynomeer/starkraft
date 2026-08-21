# Presentation Theme Spec

## 목적

그래픽 클라이언트 전반에서 사용하는 색상, 타이포그래피, 간격, 패널 재질, 모션 토큰의 단일 기준 문서다.

이 문서의 목적은 "예쁜 색 추천"이 아니라 다음을 막는 것이다.

- 파일마다 다른 색 하드코딩
- HUD와 월드 오버레이의 시각 언어 불일치
- 작은 UI 수정이 전체 화면 통일감을 깨는 현상

## 적용 범위

다음 레이어는 이 문서의 토큰을 우선 사용한다.

- [`sim/src/main/kotlin/client/GdxUiAssets.kt`](/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client/GdxUiAssets.kt)
- [`sim/src/main/kotlin/client/GameScreen.kt`](/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client/GameScreen.kt)
- [`sim/src/main/kotlin/client/GdxWorldRenderer.kt`](/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client/GdxWorldRenderer.kt)
- 이후 추가될 HUD presenter, effect pass, shader program

## 디자인 방향

- tone: sci-fi tactical FUI
- mood: disciplined, readable, low-noise
- contrast model: dark battlefield + readable bright overlays
- accent strategy: selection green, command teal, alert amber, danger red

## Color Tokens

## Base Surfaces

| Token | Value | Usage |
| --- | --- | --- |
| `bg.app` | `#0B131B` | 앱 전체 배경 |
| `bg.worldFrame` | `#0F172A` | 전장 외곽 프레임 |
| `bg.panel` | `#192134` | 기본 HUD 카드 |
| `bg.panelAlt` | `#0F1F2B` | 보조 카드, muted 영역 |
| `bg.panelStrong` | `#102430` | 강조 패널 |
| `bg.scrim` | `rgba(2, 8, 14, 0.72)` | pause/help/modal 오버레이 |

## Text

| Token | Value | Usage |
| --- | --- | --- |
| `text.primary` | `#FFFFFF` | 기본 텍스트 |
| `text.secondary` | `#D5DEE8` | 보조 본문 |
| `text.muted` | `#94A3B8` | 힌트, 부가 정보 |
| `text.disabled` | `#64748B` | 비활성 텍스트 |
| `text.inverse` | `#0F172A` | 밝은 배경 위 텍스트 |

## Interaction and State

| Token | Value | Usage |
| --- | --- | --- |
| `state.selection` | `#22C55E` | 선택 외곽, selection confirm |
| `state.selectionSoft` | `#86EFAC` | 선택 pulse, soft glow |
| `state.command` | `#38BDF8` | 명령 패널 강조, move confirmation |
| `state.commandSoft` | `#7DD3FC` | hover, secondary command glow |
| `state.warning` | `#F59E0B` | under attack, caution |
| `state.warningSoft` | `#FCD34D` | soft warning pulse |
| `state.danger` | `#DC2626` | 치명 경고, invalid, hostile critical |
| `state.dangerSoft` | `#F87171` | damage flash soft layer |
| `state.research` | `#22D3EE` | 연구 관련 하이라이트 |
| `state.production` | `#FBBF24` | 생산 관련 하이라이트 |
| `state.build` | `#34D399` | 건설 관련 하이라이트 |

## Border and Material

| Token | Value | Usage |
| --- | --- | --- |
| `border.subtle` | `rgba(255,255,255,0.08)` | 카드 외곽 |
| `border.default` | `rgba(125,211,252,0.28)` | 기본 라인 |
| `border.active` | `rgba(34,197,94,0.56)` | 활성 선택/포커스 |
| `border.warning` | `rgba(245,158,11,0.42)` | 경고 프레임 |
| `glow.selection` | `rgba(34,197,94,0.20)` | selection glow |
| `glow.command` | `rgba(56,189,248,0.16)` | command glow |
| `glow.alert` | `rgba(245,158,11,0.18)` | warning glow |

## World Rendering Tokens

| Token | Value | Usage |
| --- | --- | --- |
| `world.terrainA` | `#1A2A20` | 기본 타일 |
| `world.terrainB` | `#1E3225` | 보조 타일 |
| `world.terrainRidge` | `#2D3827` | ridge, edge |
| `world.terrainDust` | `#393021` | dust, dry area |
| `world.terrainMetal` | `#24303A` | blocked/structure plate |
| `world.fog` | `rgba(10,18,24,0.18)` | visible fog |
| `world.shroud` | `rgba(6,12,16,0.32)` | unexplored shroud |

## Faction Readability Tokens

전장 판독성을 위해 팀 컬러는 과도하게 넓게 쓰지 않는다.

| Role | Token Strategy |
| --- | --- |
| Friendly | cool cyan-blue core |
| Enemy | warm red-orange core |
| Neutral | muted sand-gold |
| Selection | always lime-green, faction color보다 우선 |

규칙:

- 팀 컬러는 외곽/trim/accent 중심으로 제한한다.
- 유닛 body 전체를 팀 컬러로 칠하지 않는다.
- 적대 여부 판단은 색상 + 형태 + HUD 컨텍스트로 함께 전달한다.

## Typography Tokens

## Font Roles

| Token | Role | Guideline |
| --- | --- | --- |
| `font.display` | 섹션 헤더, 주요 배지 | condensed, technical, high contrast |
| `font.body` | 일반 라벨, 본문 | readable sans, neutral spacing |
| `font.mono` | tick, debug, resource counters | fixed-width or pseudo-mono accent |

현재 기본 폰트 체계를 유지하되, 역할만 먼저 분리한다.

## Type Scale

| Token | Size Intent |
| --- | --- |
| `type.xs` | 12-13px equivalent |
| `type.sm` | 14px equivalent |
| `type.md` | 16px equivalent |
| `type.lg` | 18px equivalent |
| `type.xl` | 22px equivalent |

규칙:

- body text는 `type.md` 또는 가독성을 보장하는 `type.sm` 이상
- hint/muted line도 최소 `type.xs`
- HUD 헤더는 `type.lg`

## Spacing Tokens

| Token | Value |
| --- | --- |
| `space.1` | 4 |
| `space.2` | 8 |
| `space.3` | 12 |
| `space.4` | 16 |
| `space.5` | 20 |
| `space.6` | 24 |
| `space.8` | 32 |

규칙:

- HUD 내부 패딩은 `8 / 12 / 16`만 사용
- 상단 바, 선택 카드, 커맨드 카드 간 거리는 `8` 또는 `12`
- 임의 수치 추가 금지, 필요한 경우 토큰 승격 후 사용

## Radius and Shape

| Token | Value | Usage |
| --- | --- | --- |
| `radius.none` | 0 | 월드 오버레이, 전술 라인 |
| `radius.sm` | 4 | 버튼/배지 |
| `radius.md` | 8 | 카드, hover plate |
| `radius.lg` | 12 | modal, pause overlay |

규칙:

- HUD 전체는 둥근 카드보다 기술적 패널 감각을 유지
- 전장 위 selection/command effect는 radius 최소화

## Motion Tokens

| Token | Duration | Usage |
| --- | --- | --- |
| `motion.fast` | 120ms | hover, pressed |
| `motion.ui` | 180ms | panel emphasis, status flash |
| `motion.confirm` | 240ms | selection confirm, order confirm |
| `motion.alert` | 320ms | warning pulse |
| `motion.effect` | 420ms | death burst, completion burst |

Easing rules:

- UI: ease-out
- command confirm: ease-out with short overshoot 금지
- battlefield effect: front-loaded impact + short decay

## Audio Gain Tokens

| Token | Value |
| --- | --- |
| `gain.ui` | 0.42 |
| `gain.command` | 0.48 |
| `gain.combat` | 0.46 |
| `gain.alert` | 0.60 |
| `gain.deathStructure` | 0.60 |

규칙:

- alert가 command보다 항상 우선 청취돼야 한다.
- 반복 빈도 높은 효과는 gain보다 variation/cooldown을 먼저 조절한다.

## Component Mapping

| Component | Primary Tokens |
| --- | --- |
| top bar | `bg.panel`, `text.primary`, `state.command` |
| selection card | `bg.panel`, `state.selection`, `text.secondary` |
| command deck | `bg.panelStrong`, `state.command`, `border.default` |
| warning panel | `bg.panelStrong`, `state.warning`, `state.danger` |
| minimap frame | `bg.panelAlt`, `border.default`, `state.selection` |
| world selection | `state.selection`, `state.selectionSoft` |
| invalid order | `state.danger`, `state.dangerSoft` |
| build complete | `state.build`, `state.production` |

## Accessibility and Stability Rules

- primary text contrast는 dark panel 위에서 4.5:1 이상 유지
- muted text도 정보 전달용이면 4.5:1에 최대한 가깝게 유지
- hover 텍스트 삽입으로 panel width가 바뀌면 실패
- selection, danger, warning은 색상만이 아니라 shape/pulse/position으로도 구분

## Implementation Notes

권장 후속 작업:

1. `GdxUiAssets`에 `PresentationTheme` 데이터 클래스를 도입
2. 현재 하드코딩된 `Color(...)`를 단계적으로 token reference로 교체
3. `GameScreen`과 `GdxWorldRenderer`의 상수색을 theme에서 주입

