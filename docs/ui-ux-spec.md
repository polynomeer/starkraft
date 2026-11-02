# UI / UX Spec

## 목적

그래픽 클라이언트를 디버그 뷰어에서 플레이 가능한 RTS UI로 끌어올리기 위한 기준 문서다.

## UX 목표

- 플레이어가 로그를 보지 않고도 상태를 이해할 것
- 선택, 명령, 생산, 연구가 짧은 동선으로 가능할 것
- 실패한 명령의 이유를 화면에서 바로 알 수 있을 것

## 현재 구현 상태

libGDX 클라이언트(`sim/src/main/kotlin/client/GameScreen.kt`, `GdxWorldRenderer.kt`, `GdxClientRuntime.kt`) 기준으로 아래 화면 구성은 대부분 구현되어 있다. 항목별 차이는 각 절의 `현재:` 줄에 적는다. 레이아웃 세부 규칙은 [hud-layout-contract.md](hud-layout-contract.md), 키 매핑은 [input-mapping-spec.md](input-mapping-spec.md)를 따른다.

## 핵심 화면 구성

### 1. 메인 전장

- 유닛/건물/자원 노드 렌더링
- 시야/안개 표현
- 선택 윤곽과 체력 표시
- 이동/집결/명령 오버레이

### 2. 상단 상태 바

- minerals
- gas
- supply
- 현재 faction
- 게임 속도/일시정지 상태

현재: 좌측 economy 라인은 `T<tick> F<faction> M<minerals> G<gas> V<visible tiles>`(observer는 `T<tick> OBS E<entities> N<nodes>`)이며 supply는 표시하지 않는다. 그 오른쪽에 선택 요약, mode(`DEFAULT`/`MOVE`/`ATTACK-MOVE`/`PATROL`/`RALLY`/`BUILD:<type>` + 시점), 상태 배지(`SYNC`/`LIVE`/`SIM HOLD`/`PAUSED`/매치 결과)가 이어진다. sim 속도 값은 상단 바에 표시하지 않는다.

### 3. 선택 카드

- 현재 선택된 유닛/건물 타입 요약
- 다중 선택 수량
- hp/상태/작업/큐 상태

현재: headline, 대표 portrait 텍스트, HP 바, STATUS chip 줄, queue 줄, ROSTER grid(페이지당 8칸, 4열), 페이지 버튼, 컨트롤 그룹 버튼으로 구성된다. 단일 선택이 아니거나 chip이 상태를 이미 설명하면 상세 텍스트 줄은 숨긴다.

### 4. 명령 패널

- move
- attackMove
- patrol
- hold
- build
- train
- research
- cancel

현재: 위 명령에 rally와 clear/center가 추가되어 있다. 유닛 선택 시 `Orders`/`Production`/`Utility` 3열 그룹, train/research/rally 가능한 건물 선택 시 `Orders`/`Train`/`Research`/`Build`/`System` 2열 그룹으로 바뀐다. Utility/System 그룹은 시점 전환(F1/F2/Obs), Pause, Help를 담고 Debug 버튼은 debug 모드에서만 보인다.

### 5. 미니맵

- faction presence
- 시야 상태
- 현재 카메라 위치
- 클릭 이동

현재: 제목은 `Tac Map F1|F2|Obs`. 좌클릭/드래그는 카메라 이동이고, armed move/attack/patrol/rally 상태에서는 미니맵 클릭이 명령 확정이 된다. 우클릭은 미니맵 지점에 문맥형 명령을 내린다. 시야/탐사 상태는 미니맵 음영으로만 표현되고, 월드 fog overlay pass(`drawFog`)는 현재 비어 있다. 대신 viewed faction 시야 밖 엔티티는 월드에 그리지 않는다.

### 6. 알림/피드

- 건설 완료
- 생산 완료
- 연구 완료
- 공격받는 중
- 명령 실패

현재: `UNDER ATTACK`/`STRUCTURE LOST`는 화면 상단의 독립 banner로, 건설/생산/연구 완료(`<type> complete`, `<type> ready`, `<type> research complete`)와 명령 실패 notice는 command block의 action banner로 표시한다. 매치 종료 시 월드 상단 라벨과 상태 배지에 결과를 표시하며, 별도 결과 화면은 아직 없다.

## 상호작용 원칙

### 선택

- 좌클릭 단일 선택
- 드래그 박스 다중 선택
- shift 추가/제거
- 더블탭/그룹 리콜 포커스 (현재: 같은 컨트롤 그룹 키를 350ms 안에 다시 누르면 카메라 포커스)

### 명령

- 우클릭 기본 명령
- modifier로 attack-move 등 고급 명령 (현재: `ctrl + 우클릭` attack-move, `shift` queue)
- command panel 클릭 지원

### 피드백

- 선택 시 즉시 하이라이트
- 비활성 명령은 이유가 드러나야 함 (현재: hover 시 고정 hint 슬롯에 `Select units first` 등 이유 표시)
- build/train/research 가능 여부가 즉시 보여야 함

## 정보 우선순위

### 항상 보여야 하는 것

- 자원
- supply
- 선택 상태
- 주요 명령 가능 여부

### 상황에 따라 보여야 하는 것

- 건설 진행률
- 생산/연구 queue
- builder assignment
- replay/observer 상태

## 제품 수준 기준

다음을 만족해야 한다.

1. 15분 이상 플레이에서 HUD 탐색 비용이 크지 않다.
2. 선택과 명령이 일관적으로 동작한다.
3. 주요 정보가 문자열 디버그 로그에 의존하지 않는다.
4. 초보자도 현재 선택 상태와 가능한 행동을 이해할 수 있다.

## 구현 우선순위

1. selection card 정리
2. command panel enable/disable 이유 표준화
3. minimap 상호작용 보강
4. alert/feed 정리
5. 결과 화면 정리

## 관련 코드

- [sim/src/main/kotlin/client](../sim/src/main/kotlin/client)
- [input-mapping-spec.md](input-mapping-spec.md)
- [game-design-document.md](game-design-document.md)
