# HUD Layout Contract

## 목적

HUD 레이아웃이 기능 추가 때문에 다시 흔들리거나 플레이 화면을 가리는 문제를 방지하기 위한 계약 문서다.

이 문서는 다음 문제를 직접 겨냥한다.

- 하단 HUD가 월드 화면을 과도하게 가리는 문제
- 미니맵과 HUD 레이어 충돌
- hover hint나 긴 라벨 때문에 패널 폭이 흔들리는 문제
- 선택 카드와 커맨드 카드가 정보량에 따라 요동치는 문제

## 레이아웃 원칙

1. 월드 뷰포트를 먼저 확보하고 HUD가 그 위에 덮어쓰지 않는다.
2. HUD 패널은 "내용 기반 폭"이 아니라 "계약된 폭"을 사용한다.
3. 미니맵, selection, command 영역은 각자 고정 앵커를 가진다.
4. 실시간 업데이트 텍스트는 고정 슬롯 안에서만 교체한다.

## 화면 구조

```text
┌──────────────────────────────────────────────────────────────┐
│ Top Bar  [economy ............][selection][mode][status]     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                      World Viewport                          │
│                                                              │
├───────────────┬───────────────────────┬ ─ ─ ┬────────────────┤
│ Minimap Block │ Selection Block       │ gap │ Command Block  │
└───────────────┴───────────────────────┴ ─ ─ ┴────────────────┘
```

현재 구현(`GameScreen.buildHud`/`refreshHud`)에서:

- 미니맵은 bottom HUD 행의 일부가 아니라 별도 stage actor(`leftHudColumn`)로 좌하단(left 16px, bottom 14px)에 고정된다. bottom HUD 행은 미니맵 폭만큼 빈 spacer slot을 둔다.
- Selection Block은 spacer 바로 오른쪽에 붙고, 그 뒤 가변 gap을 지나 Command Block이 우측에 붙는다. 화면 중앙 정렬이 아니다.
- `UNDER ATTACK` / `STRUCTURE LOST` banner는 상단 중앙의 독립 actor다.

## Top Bar Contract

구성:

- 좌측: resource/economy
- 중앙: selection summary
- 우측: mode/status

현재 구현:

- economy: `T<tick>  F<n>  M<minerals>  G<gas>  V<visible tiles>` (observer는 `T<tick>  OBS  E<entities>  N<nodes>`), 남는 폭을 채운다
- selection: 단일 `<type> <hp>/<maxHp>`, 다중 `<n>x <type>`, 없으면 `No sel`
- mode: `<overlay mode>  <F1|F2|OBSERVER>`
- status: `SYNC`(snapshot 없음) / `LIVE` / `SIM HOLD`(sim pause) / `PAUSED`(pause overlay) / 매치 결과
- selection/mode/status 폭: 일반 106/98/52px, 폭 1360px 미만에서 92/84/46px (`computeTopBarLayout`)

규칙:

- 세 영역 모두 계약 폭을 갖는다. (현재 economy 영역만 가변 폭)
- 숫자 증가 때문에 상단 바 높이가 변하면 안 된다.
- faction view, pause, debug 상태는 우측 상태 슬롯 안에만 표시한다.

## Bottom HUD Contract

### Left: Minimap Block

포함:

- minimap frame
- minimap title
- fixed utility strip

규칙:

- 플레이 화면 좌측을 가리는 여분 패널 금지
- minimap 위에 다른 불투명 HUD panel 금지
- minimap은 항상 최전면에서 읽혀야 한다

현재 구현:

- 크기: `min(176, screenWidth/7)` x `min(144, screenHeight/7)` (`gdxMiniMapBounds`)
- header: `Tac Map F1|F2|Obs` + hint, footer: `minimap drag camera` 또는 armed 상태에서 `armed: minimap/world click confirm + shift queue`
- frame chrome은 입력을 받지 않으며(`Touchable.disabled`), 클릭은 미니맵 bounds 기준으로 `GameInputController`가 직접 처리한다

### Center: Selection Block

포함:

- selection title
- representative portrait or silhouette
- HP / status / queue
- roster grid
- page controls

규칙:

- 다중 선택이 늘어나도 블록 전체 높이는 고정
- roster는 내부 paging으로 해결하고 바깥 크기는 유지
- 긴 이름은 줄바꿈보다 축약/ellipsize 우선

현재 구현 순서(위→아래): header(`Selected`/`Selection` + HP), headline, portrait(타입/역할 축약 텍스트) + meta 줄, HP 바, `STATUS` chip 줄(+선택적 상세 줄), queue 줄(+선택적 상세 줄), `ROSTER` 요약 줄(선택 1~4개일 때만), roster grid(페이지당 8칸, 4열), pager(`<` / `Pg n/m` / 컨트롤 그룹 버튼 / 그룹 요약 / `>`).

- 상세 줄은 단일 선택이고 chip이 4개 미만이며 내용이 `Ready`/`Idle`/`No ...`가 아닐 때만 보인다 (`shouldShowSelectionDetailLine`)
- 선택이 바뀌면 roster 페이지는 0으로 돌아간다

### Right: Command Block

포함:

- command deck header
- fixed hint slot
- button grid
- system / view / utility row

규칙:

- hover hint는 별도의 고정 라인에만 표시
- 버튼 텍스트 길이에 따라 버튼 크기가 달라지면 안 된다
- tooltip가 outer bounds를 밀어내면 안 된다

현재 구현:

- header: 유닛 선택 시 `Orders · <mode>`(production 버튼이 6개를 넘으면 `· n/m` 페이지 표시), 건물 deck이면 `Cmd <type>`. 우측 `<` / `>`는 production 페이지 번호만 바꾸며 버튼 목록을 잘라내지는 않는다
- 고정 hint 슬롯: hover 힌트, 비활성 이유, armed 안내를 축약해 표시하고 비어 있으면 `cmd standby`
- action banner: notice, ground ping 결과, armed ready 문구를 한 줄로 표시 (비어 있으면 숨김)
- button grid: 고정 높이 세로 스크롤 영역 안에 그룹 단위로 배치
  - 유닛 선택: `Orders`(move/attack/patrol/rally/hold/clear/center), `Production`(build/train/research/cancel), `Utility`(F1/F2/Obs/Pause/Help, debug 모드에서 Debug) — 3열
  - train/research/rally 가능한 건물 선택(`usesStructureCommandDeck`): `Orders`(rally/clear/center), `Train`, `Research`, `Build`, `System` — 2열, 버튼과 스크롤 높이를 약간 키움
- 별도의 system/view row는 없고 Utility/System 그룹이 그 역할을 한다

## World Viewport Contract

월드 뷰포트는 다음에 의해 결정된다.

- top bar bottom
- bottom HUD top
- minimap upper bound

규칙:

- 월드 뷰포트 높이는 최소값 이하로 내려가지 않는다
- 카메라와 미니맵 viewport 계산은 실제 world viewport를 기준으로 한다
- UI overlay가 world scissor 경계를 넘어 침범하지 않는다

현재 구현: world viewport 높이 = `min(bottom HUD top, minimap top)`, 단 화면 높이의 66% 이상 (`computeWorldViewportHeightForLayout`). 월드 렌더는 이 높이로 scissor 된다.

## Text Behavior Contract

- hover hint: 최대 글자 수 고정 또는 사전 축약
- queue/status text: 한 줄 요약 우선, 세부값은 내부 영역에서만 표시
- resource counters: `1,240` 또는 `1.2K` 포맷 사용 (현재 미적용, 원시 정수 표시)
- debug text: 기본적으로 world viewport 밖에만 표시

## Layering Contract

z-order 우선순위:

1. modal / pause / help
2. alerts and critical warning banner
3. minimap viewport and minimap symbols
4. bottom HUD cards
5. world overlays
6. world content

규칙:

- minimap 자체를 왼쪽 배경 패널 아래에 두지 않는다
- attack warning은 command deck 내부가 아니라 독립 banner로 표시
- world click feedback은 HUD 뒤로 숨어도 안 된다

## Sizing Contract

권장 기준:

- top bar height: compact fixed
- bottom HUD height: compact fixed band
- minimap width/height: aspect 유지, 고정 최대치
- selection block width: command block보다 약간 넓거나 비슷한 수준
- command button grid: 유닛 deck 3열, 건물 deck 2열 고정

정확한 픽셀은 구현체에서 해상도별로 계산하되, 다음은 유지한다.

- small window: 정보 밀도를 줄여도 패널 위치는 유지
- large window: 패널이 비례 확장되더라도 world viewport 우선

현재 구현 값 (`computeBottomHudLayout`, `computeBottomHudHeight`, 폭 1440px 미만이 compact):

| 항목 | compact | 일반 |
| --- | --- | --- |
| selection block 폭 | `w*0.172`, 226~286px | `w*0.182`, 248~332px |
| command block 폭 | `w*0.146`, 196~238px | `w*0.154`, 220~278px |
| bottom HUD 높이 | max(미니맵 높이, 78~98px, command shell) | max(미니맵 높이, 82~106px, command shell) |
| command 버튼 높이 | 13px | 15px |
| hint 슬롯 폭 | 156px | 172px |

## Responsive Rules

- 작은 창에서는 보조 라벨을 축약하고 버튼 수는 페이지로 넘긴다 (현재는 페이지 대신 command grid 세로 스크롤)
- 큰 창이라고 해서 패널을 무한 확대하지 않는다
- landscape desktop 기준이 기본이며, 세로형 비율은 별도 지원 전까지 최소한의 안전 동작만 보장

## Error States

다음은 HUD 안에서만 처리한다.

- no selection
- invalid command
- insufficient resources
- paused
- observer view

다음은 HUD와 독립된 overlay로 처리한다.

- under attack
- structure lost
- connection / stream failure (현재 독립 overlay 없음, snapshot이 없으면 상단 상태 배지가 `SYNC`)

## Validation Checklist

- hover over command button 시 command block outer size가 변하지 않는다
- minimap이 다른 좌측 HUD 배경에 가려지지 않는다
- 선택 수가 늘어나도 selection block height가 변하지 않는다
- 작은 창에서도 world viewport가 플레이 가능 높이를 유지한다
- world viewport rectangle이 minimap에 정확히 표시된다

