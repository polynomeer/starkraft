# Simulation Architecture

## 목적

`sim` 모듈은 `starkraft`의 권위적 게임 규칙 엔진이다.  
모든 게임 규칙은 고정 tick 기반으로 결정론적으로 실행되어야 한다.

## 핵심 원칙

- tick 기반 고정 시간 전진
- 동일 입력에 대해 동일 결과 보장
- hot path에서 불필요한 할당 최소화
- 시스템 책임 분리

## 시간 모델

- 고정 tick: `Time.TICK_MS = 20`
- 실시간 표시 여부와 무관하게, 규칙 계산은 tick 정수 기준으로 진행된다.

## 월드 모델

월드는 ECS-lite 구조를 사용한다.

- 엔티티는 정수 id를 가진다.
- 상태는 컴포넌트 맵에 저장된다.
- 예시 컴포넌트:
  - `Transform`
  - `Motion`
  - `Health`
  - `Vision`
  - `WeaponRef`
  - `OrderQueue`
  - `PathFollow`

이 구조의 장점:

- 직렬화/해시/리플레이에 유리
- 시스템별 책임이 분명함
- OOP 객체 그래프보다 결정론 추적이 쉬움

## 시스템 순서

권장 실행 순서:

1. `AliveSystem`
2. `OccupancySystem`
3. `PathfindingSystem`
4. `MovementSystem`
5. `CombatSystem`
6. `VisionSystem`
7. 생산/연구/경제/건설 관련 시스템
8. 승리 판정 및 이벤트 집계

이 순서는 다음을 보장한다.

- 이동 전에 최신 점유 상태 반영
- 전투 전에 최신 위치 반영
- 시야 계산이 최신 생존/위치 기준으로 수행

## 이동과 경로탐색

### 맵

- `MapGrid`가 정적 passability와 tile weight를 관리한다.

### 동적 점유

- `OccupancyGrid`가 유닛/건물 점유를 관리한다.
- pathfinding과 movement는 정적 passability와 동적 blocker를 모두 고려한다.

### 경로탐색

- cooperative A* 기반
- octile heuristic 사용
- 8방향 이동 지원
- corner-cut 규칙 준수
- tick당 node budget 사용
- tick당 path request quota 사용

### 재경로탐색

다음 경우 replan 가능:

- 다음 waypoint가 막힘
- unit이 일정 tick 동안 진전이 없음
- cooldown이 끝난 상태에서 move order가 여전히 유효함

## 전투

- spatial hash + faction cache 기반 타겟 탐색
- deterministic iteration을 유지해야 함
- 과도한 overkill을 피하는 보정이 포함됨
- attack-move, hold, patrol 등 상위 명령이 전투 시스템과 연동됨

## 시야와 안개

- faction별 `FogGrid` 유지
- 시야는 faction 단위 정보 은닉의 기준이다
- snapshot/클라이언트에 노출되는 정보는 시야 정책을 따라야 한다

## 경제/건설/생산/연구

- 자원 stockpile은 faction 상태로 유지
- 채집은 노드 → worker cargo → drop-off 순으로 처리
- 건설은 footprint, builder assignment, progress, cancel/refund를 처리
- 생산은 building queue 단위로 진행
- 연구는 faction unlock 상태를 변경한다

## 결정론 규칙

다음 항목은 반드시 안정적이어야 한다.

- iteration 순서
- 랜덤 시드 사용 위치
- float 처리 방식
- replay command 적용 순서
- hash 계산 입력 순서

## 출력물

`sim`은 다음 산출물을 생성하거나 검증한다.

- final world hash
- replay hash
- snapshot NDJSON
- replay 파일
- benchmark 결과

## 변경 시 체크리스트

새 규칙을 추가할 때는 다음을 확인한다.

1. 상태가 `sim` 내부에만 존재하는가
2. 동일 입력에서 결과가 동일한가
3. tick당 할당이 증가하지 않는가
4. replay 재실행으로 재현 가능한가
5. 테스트 또는 golden hash를 추가했는가
