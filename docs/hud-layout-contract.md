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
│ Top Bar                                                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                      World Viewport                          │
│                                                              │
├───────────────┬───────────────────────┬──────────────────────┤
│ Minimap Block │ Selection Block       │ Command Block        │
└───────────────┴───────────────────────┴──────────────────────┘
```

## Top Bar Contract

구성:

- 좌측: resource/economy
- 중앙: selection summary
- 우측: mode/status

규칙:

- 세 영역 모두 계약 폭을 갖는다.
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

## World Viewport Contract

월드 뷰포트는 다음에 의해 결정된다.

- top bar bottom
- bottom HUD top
- minimap upper bound

규칙:

- 월드 뷰포트 높이는 최소값 이하로 내려가지 않는다
- 카메라와 미니맵 viewport 계산은 실제 world viewport를 기준으로 한다
- UI overlay가 world scissor 경계를 넘어 침범하지 않는다

## Text Behavior Contract

- hover hint: 최대 글자 수 고정 또는 사전 축약
- queue/status text: 한 줄 요약 우선, 세부값은 내부 영역에서만 표시
- resource counters: `1,240` 또는 `1.2K` 포맷 사용
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
- selection block width: command block보다 좁거나 동일
- command button grid: 3열 또는 4열 고정

정확한 픽셀은 구현체에서 해상도별로 계산하되, 다음은 유지한다.

- small window: 정보 밀도를 줄여도 패널 위치는 유지
- large window: 패널이 비례 확장되더라도 world viewport 우선

## Responsive Rules

- 작은 창에서는 보조 라벨을 축약하고 버튼 수는 페이지로 넘긴다
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
- connection / stream failure

## Validation Checklist

- hover over command button 시 command block outer size가 변하지 않는다
- minimap이 다른 좌측 HUD 배경에 가려지지 않는다
- 선택 수가 늘어나도 selection block height가 변하지 않는다
- 작은 창에서도 world viewport가 플레이 가능 높이를 유지한다
- world viewport rectangle이 minimap에 정확히 표시된다

