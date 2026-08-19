# UI / UX Spec

## 목적

그래픽 클라이언트를 디버그 뷰어에서 플레이 가능한 RTS UI로 끌어올리기 위한 기준 문서다.

## UX 목표

- 플레이어가 로그를 보지 않고도 상태를 이해할 것
- 선택, 명령, 생산, 연구가 짧은 동선으로 가능할 것
- 실패한 명령의 이유를 화면에서 바로 알 수 있을 것

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

### 3. 선택 카드

- 현재 선택된 유닛/건물 타입 요약
- 다중 선택 수량
- hp/상태/작업/큐 상태

### 4. 명령 패널

- move
- attackMove
- patrol
- hold
- build
- train
- research
- cancel

### 5. 미니맵

- faction presence
- 시야 상태
- 현재 카메라 위치
- 클릭 이동

### 6. 알림/피드

- 건설 완료
- 생산 완료
- 연구 완료
- 공격받는 중
- 명령 실패

## 상호작용 원칙

### 선택

- 좌클릭 단일 선택
- 드래그 박스 다중 선택
- shift 추가/제거
- 더블탭/그룹 리콜 포커스

### 명령

- 우클릭 기본 명령
- modifier로 attack-move 등 고급 명령
- command panel 클릭 지원

### 피드백

- 선택 시 즉시 하이라이트
- 비활성 명령은 이유가 드러나야 함
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

- `/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client`
- `/Users/hammac/Projects/starkraft/docs/input-mapping-spec.md`
- `/Users/hammac/Projects/starkraft/docs/game-design-document.md`
