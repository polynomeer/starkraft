# Game Design Document

## 게임 개요

`starkraft`는 고정 tick 결정론 기반의 실시간 전략 게임이다.  
플레이어는 자원을 수집하고, 건물을 배치하고, 생산과 연구를 통해 병력을 확장한 뒤 적을 제거해 승리한다.

## 디자인 목표

- 짧은 명령 지연과 읽기 쉬운 전장 상태
- 생산/경제/전투가 분리되지만 서로 강하게 연결된 RTS 루프
- replay와 관전이 쉬운 명확한 규칙

## 핵심 플레이 루프

1. worker를 자원 노드에 배치한다.
2. 자원을 drop-off 건물로 반환한다.
3. 건물을 건설한다.
4. 유닛을 생산한다.
5. 연구를 진행해 능력을 해금한다.
6. 병력을 이동/교전시킨다.
7. 적 faction을 제거한다.

## 자원

### 종류

- minerals
- gas

### 채집 흐름

- worker가 resource node를 채집한다.
- cargo를 들고 drop-off 건물로 복귀한다.
- faction stockpile이 증가한다.

## 유닛

유닛은 다음 성격으로 분류된다.

- worker
- combat
- support
- producer/utility

일반 속성:

- hp / armor
- speed
- sight
- weapon
- archetype

## 건물

건물은 다음 역할을 가진다.

- drop-off
- unit production
- research
- supply / tech prerequisite
- map control / blocking

건물 규칙:

- footprint 기반 배치
- 점유 및 clearance 규칙 준수
- construction progress가 완료되어야 기능 활성화
- cancel 시 환불 가능

## 생산

- 생산은 building queue 단위로 처리한다.
- queue limit은 데이터 기반이어야 한다.
- 생산 완료 시 주변 free tile에 spawn한다.
- rally point를 가질 수 있다.

## 연구

- 연구는 faction state를 변경한다.
- prerequisite를 만족해야 enqueue 가능하다.
- 완료 시 해당 faction 전체에 적용된다.

## 전투

### 기본 명령

- move
- attack
- attackMove
- hold
- patrol

### 전투 원칙

- 시야 안에서만 정보/교전 판단
- deterministic target selection
- 불필요한 무한 추격 방지
- attack-move는 짧은 chase leash 유지

## 시야 / 안개

- faction별 fog-of-war 유지
- 현재 보임과 마지막 탐색됨을 구분할 수 있어야 한다
- 클라이언트는 시야 정책을 깨지 않는 정보만 표시해야 한다

## 승리 조건

현재 기본 규칙:

- 마지막까지 살아남은 faction이 승리

확장 후보:

- surrender
- objective control
- score/time variants

## 플레이어 UX 목표

- 선택과 명령 발행이 빠를 것
- 명령 실패 사유를 로그 없이 알 수 있을 것
- 생산/연구/건설 상태를 HUD에서 즉시 읽을 수 있을 것

## 밸런스 문서 분리

세부 수치 밸런스는 향후 별도 데이터 시트 또는 `balance-spec` 문서로 분리하는 것이 바람직하다.  
이 문서는 규칙 구조를 우선 정의한다.
