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

현재 구현 규칙 (`ecs/Harvest.kt`):

- `harvest` 명령으로 worker에 대상 노드를 지정한다.
- 노드에서 1.25 이내면 1회 채집 후 즉시 반환 이동한다. 1회 운반량은 `min(harvestPerTick, 노드 잔량, 노드 yieldPerTick)`(yieldPerTick 0은 제한 없음).
- 반환 대상은 같은 faction 건물 중 가장 가까운 곳으로, 자원 종류가 호환되는 drop-off 건물(`dropoffResourceKinds`)을 우선하고, 없으면 아무 drop-off 건물, 그것도 없으면 아무 아군 건물을 고른다.
- 반환 대상 1.5 이내에서 stockpile에 더한 뒤 같은 노드로 다시 이동한다. 노드 잔량이 0이면 채집을 멈춘다.

## 유닛

유닛은 다음 성격으로 분류된다.

- worker
- combat
- support (계획: 현재 데이터 없음)
- producer/utility (계획: 현재 데이터 없음)

현재 데이터 유닛: `Worker`(worker), `Marine`(infantry), `Zergling`(lightMelee). 세부 수치는 [`balance-spec.md`](balance-spec.md) 참고.

일반 속성:

- hp / armor
- speed
- sight (데이터 필드 없음: 생산 유닛은 시야 6 고정)
- weapon
- archetype

## 건물

건물은 다음 역할을 가진다.

- drop-off
- unit production
- research
- supply / tech prerequisite (supply는 미구현, prerequisite는 `requiredBuildingTypes`로 구현)
- map control / blocking

현재 데이터 건물: `Depot`(생산/연구/rally/mineral drop-off), `ResourceDepot`(mineral drop-off), `GasDepot`(gas drop-off).

건물 규칙:

- footprint 기반 배치
- 점유 및 clearance 규칙 준수
  - footprint에 `placementClearance`만큼 확장한 영역의 모든 타일이 맵 안, 통행 가능, 비점유(건물 및 유닛)여야 한다.
  - 배치된 footprint 타일은 정적 점유로 등록되어 이동을 막는다.
- 비용은 배치 시점에 차감된다. 실패 사유: `invalidDefinition`, `invalidFootprint`, `missingTech`, `invalidPlacement`, `insufficientResources`.
- construction progress가 완료되어야 기능 활성화
  - `buildTicks > 0`이면 HP 1의 construction site로 시작하고, `construct` 명령을 받은 worker archetype 유닛이 footprint 경계 1.5 이내에 있을 때 tick당 worker 수만큼 진행된다. HP는 진행률에 비례해 오른다.
  - 건설 중인 건물은 생산/연구를 받을 수 없고 prerequisite로도 인정되지 않는다.
  - 현재 데이터의 건물은 모두 `buildTicks`가 없어(0) 배치 즉시 완성된다.
- cancel 시 환불 가능
  - `cancelBuild`는 건설 중인 건물에만 유효하며 비용을 전액 환불하고 건물을 제거한다.

## 생산

- 생산은 building queue 단위로 처리한다.
- queue limit은 데이터 기반이어야 한다. (`productionQueueLimit`, Depot 3)
- 생산 완료 시 주변 free tile에 spawn한다.
- rally point를 가질 수 있다.

현재 구현 규칙 (`ecs/Production.kt`):

- `train`은 건물이 `supportsTraining`이고 유닛의 `producerTypes`에 포함될 때만 허용되며, 비용은 enqueue 시점에 차감된다.
- queue 맨 앞 job만 tick당 1씩 진행한다.
- spawn 위치는 footprint 바로 바깥 테두리 타일 중 첫 번째 통행 가능/비점유 타일이며, 없으면 완료 상태로 대기한다.
- `cancelTrain`은 queue의 마지막 job을 취소하고 비용을 전액 환불한다.
- rally: `rally` 명령으로 지정한 지점이 우선이고(`supportsRally` 건물만), 없으면 데이터 `rallyOffsetX/Y`(0,0이 아닐 때)를 건물 중심에 더한 지점으로 이동 명령을 붙인다.

## 연구

- 연구는 faction state를 변경한다.
- prerequisite를 만족해야 enqueue 가능하다.
- 완료 시 해당 faction 전체에 적용된다.

현재 구현 규칙 (`ecs/Research.kt`, `ecs/Tech.kt`):

- 건물이 `supportsResearch`이고 연구의 `producerTypes`에 포함되어야 한다.
- prerequisite는 `requiredBuildingTypes`(같은 faction의 완성된 건물)와 `requiredResearchIds`(이미 완료된 연구)로 검사하며, 유닛/건물 정의에도 같은 필드가 적용된다.
- 이미 완료된 연구는 `alreadyUnlocked`로 거부된다. 연구 queue 한도는 건물의 `productionQueueLimit`을 공유한다.
- 완료 시 faction unlock 집합에 추가될 뿐 수치 효과는 아직 없다. `cancelResearch`는 마지막 job을 전액 환불한다.

## 전투

### 기본 명령

- move
- attack
- attackMove
- hold
- patrol

그 외 `harvest`, `construct`, `build`, `train`/`cancelTrain`, `research`/`cancelResearch`, `cancelBuild`, `rally`, `surrenderFaction` 명령이 있다(`net/Commands.kt`). 이동 계열 명령은 `queued` 플래그로 기존 order queue 뒤에 붙일 수 있다.

### 전투 원칙

- 시야 안에서만 정보/교전 판단 (미구현: 현재 sim 전투는 fog와 무관하게 사거리만 본다)
- deterministic target selection
- 불필요한 무한 추격 방지
- attack-move는 짧은 chase leash 유지

현재 구현 규칙 (`ecs/Systems.kt`):

- 무기를 가진 유닛은 order와 무관하게(move/hold 중에도) 사거리 안의 적을 자동 공격한다.
- 대상 선택 순서: (1) 사거리 안의 명시적 `attack` 대상, (2) 사거리 안에 남아 있는 직전 자동 대상, (3) 사거리 안의 적 중 같은 tick에 예약된 피해로 죽지 않을 대상을 우선하고, 그다음 가장 가까운 대상.
- 피해는 `max(0, damage - armor)`이며, HP 0 이하가 되면 즉시 제거된다.
- `attack`은 대상이 죽거나 사라질 때까지 경로를 갱신하며 추적한다. 자동 대상은 사거리를 벗어나면 해제되고 추격하지 않는다.
- `attackMove`: 이동 중 사거리 안에 적이 있으면 멈춰 교전한다. 이때 목적지에서 3.0보다 멀리 있으면 attack-move order 자체가 해제된다(leash).
- `hold`: queue에 남아 이동을 막는다. 자동 공격은 계속한다.
- `patrol`: 명령 시점 위치와 지정 지점 사이를 왕복한다.

## 시야 / 안개

- faction별 fog-of-war 유지
- 현재 보임과 마지막 탐색됨을 구분할 수 있어야 한다
- 클라이언트는 시야 정책을 깨지 않는 정보만 표시해야 한다

현재 구현:

- sim은 faction 1, 2에 대해 `FogGrid`를 매 tick 지우고 `Vision` 컴포넌트를 가진 살아 있는 유닛 기준 원형 범위로 "현재 보임"만 다시 계산한다.
- 건물과 자원 노드는 `Vision`이 없어 시야를 제공하지 않는다.
- "마지막 탐색됨"은 sim이 아니라 클라이언트 세션(`ClientSession`)이 누적한다.

## 승리 조건

현재 기본 규칙:

- 마지막까지 살아남은 faction이 승리
  - faction > 0이고 HP > 0인 엔티티(유닛과 건물 모두, `MineralField`/`GasGeyser` 제외)가 남은 faction이 하나면 승리(`elimination`), 하나도 없으면 무승부(`draw`).
- surrender (구현됨): `surrenderFaction` 명령 시 남은 faction이 하나면 그 faction이 승리하고 reason은 `surrender`.
- match end reason: `elimination`, `surrender`, `timeout`, `draw`. `timeout`은 현재 좌표가 NaN이 된 비정상 상태 보호용으로만 설정되며 시간 제한 규칙은 없다.

확장 후보 (미구현):

- objective control
- score/time variants

## 플레이어 UX 목표

- 선택과 명령 발행이 빠를 것
- 명령 실패 사유를 로그 없이 알 수 있을 것
- 생산/연구/건설 상태를 HUD에서 즉시 읽을 수 있을 것

## 밸런스 문서 분리

세부 수치 밸런스는 [`balance-spec.md`](balance-spec.md)로 분리되어 있으며, 현재 데이터 수치 표도 그 문서에 있다.  
이 문서는 규칙 구조를 우선 정의한다.

## 관련 코드

- `sim/src/main/resources/data`
  - 유닛/건물/기술 데이터
- `sim/src/main/kotlin/ecs`
  - 실제 게임 규칙 구현
- [`COMMAND_CONTRACT.md`](../COMMAND_CONTRACT.md)
  - 명령 계층 계약
