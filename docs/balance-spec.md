# Balance Spec

## 목적

이 문서는 `starkraft`의 세부 수치 밸런스를 어떻게 관리할지 정의한다.  
핵심 규칙 문서는 [`game-design-document.md`](game-design-document.md)이고, 이 문서는 그 위에 올라가는 수치 운영 문서다.

## 범위

밸런스 대상:

- 유닛 체력, 방어력, 이동속도, 시야
- 무기 피해량, 사거리, 쿨다운
- 건물 비용, 건설 시간, footprint 가치
- 생산 시간, 연구 시간
- 자원 채집량, 반환 주기, supply 효율

주의: 현재 데이터/코드에는 유닛 `vision` 필드와 `supply` 개념이 없다(아래 "현재 데이터 수치" 참고). 시야와 supply는 계획 항목이다.

## 설계 원칙

### 읽기 쉬운 전장

- 같은 계열 유닛은 역할이 즉시 구분되어야 한다.
- 유닛 강점은 사거리, 내구, 기동성, 비용 중 최소 하나에서 드러나야 한다.

### 명확한 카운터

- 모든 주력 유닛은 효율적으로 상대 가능한 대응 축이 있어야 한다.
- 무적 조합보다 운영 선택을 강요하는 구성이 바람직하다.

### 경제와 전투의 연결

- 경제 이득이 곧바로 전투 snowball이 되더라도, 방어/견제 선택지가 남아야 한다.

### 결정론 친화성

- 밸런스 변경은 되도록 데이터 변경으로 끝나야 한다.
- 수치 조정이 시스템 로직 변경을 강제하면 별도 설계 검토가 필요하다.

## 밸런스 축

### 경제 축

- 초기 worker 수
- 기본 자원량
- 채집 속도
- 생산 인프라 투자 비용

### 전투 축

- TTK(time to kill)
- 사거리 우위
- 기동성
- 집중 화력 효율

### 테크 축

- 선행 건물 비용
- 연구 투자 회수 시간
- 업그레이드 체감 강도

## 관리 단위

### 유닛 시트

각 유닛은 최소한 다음을 관리한다. (괄호는 `units.json` 실제 필드명)

- `typeId` (`id`)
- role/archetype (`archetype`)
- hp (`hp`)
- armor (`armor`)
- speed (`speed`, tile/tick)
- vision (미구현: 데이터 필드 없음)
- weapon profile (`weaponId` → `weapons.json`)
- mineral/gas cost (`mineralCost`, `gasCost`)
- build time (`buildTicks`)
- supply cost (미구현)

### 건물 시트

- `typeId` (`id`)
- 역할 (`archetype`, `supportsTraining`/`supportsResearch`/`supportsRally`/`supportsDropoff`)
- footprint (`footprintWidth`, `footprintHeight`)
- placement clearance (`placementClearance`)
- mineral/gas cost (`mineralCost`, `gasCost`)
- build time (`buildTicks`)
- unlock effect (다른 정의의 `requiredBuildingTypes`로 표현)

### 연구 시트

- `techId` (`id`)
- prerequisite (`requiredBuildingTypes`, `requiredResearchIds`)
- cost (`mineralCost`, `gasCost`)
- duration (`buildTicks`)
- effect (현재는 faction unlock 집합에 추가되는 것뿐이며, 수치 효과 필드는 없다)

## 현재 데이터 수치

`sim/src/main/resources/data/*.json` 기준. 1 tick = 20ms.

### 유닛 (`units.json`)

| id | archetype | hp | armor | speed | weaponId | minerals | gas | buildTicks | producerTypes |
|---|---|---|---|---|---|---|---|---|---|
| Worker | worker | 40 | 0 | 0.095 | - | 50 | 0 | 50 | Depot |
| Marine | infantry | 45 | 0 | 0.09 | Gauss | 50 | 0 | 75 | Depot |
| Zergling | lightMelee | 35 | 0 | 0.115 | Claw | 25 | 0 | 55 | Depot |

### 무기 (`weapons.json`)

| id | damage | range | cooldownTicks |
|---|---|---|---|
| Gauss | 6 | 4.5 | 12 |
| Claw | 5 | 0.6 | 8 |

- 실제 피해는 `max(0, damage - 대상 armor)`.
- 공격 후 쿨다운이 `cooldownTicks`로 설정되고 매 tick 1씩 감소해 0인 tick에 다시 공격하므로, 실제 공격 간격은 `cooldownTicks + 1` tick이다.

### 건물 (`buildings.json`)

| id | archetype | hp | armor | footprint | clearance | 기능 | dropoff | queueLimit | rallyOffset | minerals | gas |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Depot | producer | 400 | 1 | 2x2 | 1 | train, research, rally, dropoff | minerals | 3 | (4.0, 0.0) | 100 | 0 |
| ResourceDepot | econDepot | 350 | 1 | 2x2 | 1 | dropoff | minerals | 0 | - | 75 | 0 |
| GasDepot | gasDepot | 325 | 1 | 2x2 | 1 | dropoff | gas | 0 | - | 90 | 0 |

- 세 건물 모두 `buildTicks`를 지정하지 않아 기본값 0이 적용된다. 따라서 현재 데이터에서는 배치 즉시 완성되며 construction site 단계가 생기지 않는다.

### 연구 (`techs.json`)

| id | buildTicks | minerals | gas | producerTypes | 선행 조건 |
|---|---|---|---|---|---|
| AdvancedTraining | 60 | 75 | 0 | Depot | 없음 |

- 현재 어떤 유닛/건물/연구도 `AdvancedTraining`을 `requiredResearchIds`로 요구하지 않으므로, 연구 완료의 게임플레이 효과는 아직 없다.

### 코드 상수 (데이터 외부)

- 시야: 데이터 필드가 없다. 생산 완료 유닛은 `Vision(6)` 고정, `spawn` 명령은 선택 `vision` 값을 사용한다.
- 채집(`Harvester` 기본값): `harvestPerTick` 1, 채집 사거리 1.25, 반환 사거리 1.5, cargo 용량 제한 없음. 한 번 채집하면 즉시 반환 이동하므로 1회 운반량은 `min(harvestPerTick, 노드 잔량, 노드 yieldPerTick(0이면 제한 없음))`이다. 데모 시작 worker는 `harvestPerTick` 2.
- 건설 범위: footprint 경계에서 1.5 이내의 worker가 tick당 1씩 진행시킨다(worker 수만큼 가속).
- attack-move leash: 3.0.
- 데모 시나리오 시작 자원: faction 1/2 모두 minerals 500, gas 0.

## 변경 절차

1. 문제를 증상 기준으로 기록한다.
   - 예: 특정 rush가 방어 불가능
2. 관련 replay를 수집한다.
3. 원인이 경제/전투/테크 중 어디인지 분류한다.
4. 수치 변경안을 데이터 기준으로 작성한다.
5. 고정 시드 playtest와 benchmark를 재실행한다.
6. 변경 결과를 changelog 또는 밸런스 로그에 남긴다.

## 추천 산출물

향후 별도 파일 또는 스프레드시트로 유지할 항목:

- matchup 메모
- 빌드오더별 timing 표
- TTK 비교표
- resource curve 표

## 관련 코드

- [`sim/src/main/resources/data`](../sim/src/main/resources/data)
- [`sim/src/main/kotlin/data/Schema.kt`](../sim/src/main/kotlin/data/Schema.kt) (필드 기본값)
- [`sim/src/main/kotlin/ecs`](../sim/src/main/kotlin/ecs)
- [`tools`](../tools)

## 현재 상태

2026-08-19 기준:

- 핵심 규칙 구조는 성숙했지만, 수치 밸런스 운영 체계는 아직 제품 수준으로 고정되지 않았다.
- 데이터는 유닛 3종, 무기 2종, 건물 3종, 연구 1종의 최소 세트이며 supply/시야 데이터와 연구 효과는 미구현이다.
- 따라서 이 문서는 구현 완료 문서가 아니라 운영 기준 초안이다.
