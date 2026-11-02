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

- 고정 tick: `Time.TICK_MS = 20` (`App.kt`의 `object Time`)
- 실시간 표시 여부와 무관하게, 규칙 계산은 tick 정수 기준으로 진행된다.
- tick 사이 대기는 `--noSleep`으로 끌 수 있고, `--playControlFile`의 pause/speed 상태가 대기 시간(`TICK_MS / speed`)과 일시정지를 제어한다. 대기 방식은 규칙 결과에 영향을 주지 않는다.

## 월드 모델

월드는 ECS-lite 구조를 사용한다.

- 엔티티는 정수 id를 가진다.
- 상태는 `World`(`sim/src/main/kotlin/ecs/World.kt`, `package starkraft.sim.ecs`)의 컴포넌트 맵(`mutableMapOf`, 즉 삽입 순서가 유지되는 `LinkedHashMap`)에 저장된다.
- faction별 자원(`stockpiles`), 해금 기술(`unlockedTechsByFaction`), 매치 종료 상태(`matchEnded`/`winnerFaction`/`matchEndReason`)도 `World`가 보유한다.
- 예시 컴포넌트 (`ecs/Components.kt`):
  - `Transform`
  - `Motion`
  - `Health`
  - `Vision`
  - `WeaponRef`
  - `OrderQueue`
  - `PathFollow`
  - `BuildingFootprint`, `ConstructionSite`, `ProductionQueue`, `ResearchQueue`, `ResourceNode`, `Harvester`
- 명령 큐의 `Order` 종류: `Move`, `AttackMove`, `Patrol`, `Hold`, `Construct`, `Attack`

이 구조의 장점:

- 직렬화/해시/리플레이에 유리
- 시스템별 책임이 분명함
- OOP 객체 그래프보다 결정론 추적이 쉬움

## 시스템 순서

실제 실행 순서는 `sim/src/main/kotlin/App.kt`의 tick 루프에 고정되어 있다. 각 tick:

1. 입력 수집: `InputTailReader.poll`(`--inputTail` 사용 시)
2. tick 이벤트 버퍼 초기화 후 `AliveSystem` (alive snapshot 갱신)
3. 입력 적용: script/replay/inputJson 명령 → live 입력(tail) 명령 순으로 `processTickInput`
4. `ResourceHarvestSystem` (채집/운반/drop-off), 고갈 노드 제거
5. `OccupancySystem` (동적 점유 재구성)
6. `ConstructionSystem`
7. `ResearchSystem`
8. `BuildingProductionSystem`
9. `PathfindingSystem`
10. `MovementSystem`
11. `CombatSystem` (사망 엔티티 despawn 기록 포함)
12. `VictorySystem`
13. `VisionSystem`
14. 집계 카운터 갱신, `--snapshotEvery` 주기에 맞춰 snapshot/vision/metrics 레코드 출력

각 시스템 뒤에는 해당 시스템의 typed NDJSON stream 레코드(harvest, resource delta, research, production, path, damage/combat, despawn 등)가 `--snapshotOut`으로 출력된다.

참고: 데모 맵에는 tick 200에 y=10(x=14..20) 정적 blocker를 추가하고 tick 500에 제거하는 하드코딩된 이벤트가 있다.

이 순서는 다음을 보장한다.

- 이동/경로탐색 전에 최신 점유 상태 반영
- 전투 전에 최신 위치 반영
- 승리 판정이 같은 tick의 전투 결과를 반영
- 시야 계산이 최신 생존/위치 기준으로 수행

시스템 순서와 각 시스템 내부 iteration 순서는 결정론의 일부다. 순서를 바꾸면 world hash와 replay 결과가 바뀐다.

## 이동과 경로탐색

### 맵

- `MapGrid`가 정적 passability와 tile weight를 관리한다.

### 동적 점유

- `OccupancyGrid`가 유닛/건물 점유를 관리한다.
- pathfinding과 movement는 정적 passability와 동적 blocker를 모두 고려한다.

### 경로탐색

- grid A* 기반 (`ecs/path/Pathfinder.kt`): 정적 passability와 `OccupancyGrid` blocker를 함께 검사한다. 다른 유닛의 미래 경로를 예약하는 cooperative(reservation table) 방식은 아니다.
- octile heuristic 사용
- 8방향 이동 지원
- corner-cut 금지 (`Pathfinder(map, occ, allowCornerCut = false)`)
- tick당 node budget 사용 (`PathfindingSystem(nodesBudgetPerTick = 2000)`), 예산이 소진되면 남은 요청은 다음 tick으로 이월
- path request queue 제한 (`PathRequestQueue(256, 50)`: 최대 256개, tick당 enqueue 50개)
- 경로 버퍼는 `PathPool`로 재사용해 hot path 할당을 줄인다

### 재경로탐색

다음 경우 replan 가능 (`MovementSystem`):

- 다음 waypoint가 막힘
- unit이 일정 tick 동안 진전이 없음 (stuck: 20 tick 동안 이동량이 임계값 미만)
- cooldown이 끝난 상태에서 move order가 여전히 유효함

replan 간에는 `RepathCooldown` 10 tick이 적용된다.

## 전투

- `FactionIndex` 기반 faction별 적 목록 cache를 tick마다 만들고, 사거리 내 적을 선형 탐색한다 (spatial hash는 현재 없음)
- 명시적 `Attack` 명령 대상 → 유지 중인 auto-attack 대상(sticky target) → 신규 탐색 순으로 타겟을 고른다
- 같은 tick에 이미 예약된 피해(reserved damage)를 고려해 과도한 overkill을 피한다 (한 번에 죽지 않는 대상 우선, 그다음 거리)
- 공격은 계획 단계 후 일괄 적용되며, 무기 cooldown은 `weapons.json`의 `cooldownTicks`를 쓴다
- deterministic iteration을 유지해야 함 (alive snapshot 순서로 순회)
- attack-move, hold, patrol 등 상위 명령이 전투 시스템과 연동됨 (attack-move는 사거리 내 적이 있으면 정지하고 leash를 넘으면 해당 order를 종료)

## 시야와 안개

- faction별 `FogGrid` 유지 (`ecs/services/Fog.kt`). 현재는 faction 1, 2용 그리드 두 개만 만든다.
- `VisionSystem`은 매 tick 그리드를 비우고 `Vision.range` 기준으로 원형 시야를 다시 칠한다 (탐사 기록/explored 상태는 없음)
- 시야는 faction 단위 정보 은닉의 기준이다
- (원칙, 미구현) snapshot/클라이언트에 노출되는 정보는 시야 정책을 따라야 한다. 현재 sim snapshot은 엔티티를 시야로 필터링하지 않고, faction별 `visibleTiles` 수와 vision 변경 레코드를 함께 내보낸다.

## 경제/건설/생산/연구

- 자원 stockpile은 faction 상태로 유지 (`ResourceSystem`, minerals/gas)
- 채집은 노드 → worker cargo → 가장 가까운 drop-off 순으로 처리 (`ResourceHarvestSystem`), 고갈 노드는 제거된다
- 건설은 footprint(`placementClearance` 포함), builder assignment, progress, cancel/refund를 처리 (`BuildingPlacementSystem`, `ConstructionSystem`)
- 생산은 building queue 단위로 진행 (`BuildingProductionSystem`, `productionQueueLimit`, rally offset)
- 연구는 faction unlock 상태를 변경한다 (`ResearchSystem`)
- 유닛/건물/무기/기술 정의는 `sim/src/main/resources/data/*.json`에서 `DataRepo`로 로드한다
- 승리 판정 (`VictorySystem`): 자원 노드를 제외한 생존 엔티티가 한 faction에만 남으면 `elimination`, 아무도 없으면 `draw`. 종료 사유에는 `surrender`/`timeout`도 있다

## 결정론 규칙

다음 항목은 반드시 안정적이어야 한다.

- 시스템 실행 순서와 iteration 순서
- 랜덤 시드 사용 위치 (현재 `--seed` RNG는 초기 spawn 위치 jitter에만 쓰인다)
- float 처리 방식
- replay command 적용 순서
- hash 계산 입력 순서

### 해시 구현

- world hash (`App.kt`의 `hashWorldForReplay`): 64-bit FNV 계열 mix. 엔티티 id 오름차순으로 id, `x*1000`/`y*1000`(정수 절삭), path index, order 수, hp, 무기 cooldown을 섞고, 마지막에 매치 종료 여부/승자/종료 사유를 섞는다. 자원, 시야, 건설/생산 진행도는 포함되지 않는다. `--dumpWorldHash`로 출력한다.
- replay hash (`replay/ReplayHash.kt`의 `ReplayHashRecorder`): 64-bit FNV-1a로 명령 스트림(명령 종류 코드, tick, 대상, 좌표 float bit)을 누적한다. replay 파일(schema > 0)에 저장되고 로드 시 검증되며, `--strictReplayHash`는 누락/불일치를 실패로 처리한다.
- Go 서버의 world hash는 별도 공식이며 위 해시와 비교할 수 없다.

## 출력물

`sim`은 다음 산출물을 생성하거나 검증한다.

- final world hash (`--dumpWorldHash`)
- replay hash
- snapshot NDJSON (`--snapshotOut`, `--snapshotEvery`)
- replay 파일 (`--record`, `--replayOut`, `--replayDump`)
- benchmark 결과 (`:sim:benchmark`, `:sim:pathfindingBenchmark`, `:sim:soak`)

## 변경 시 체크리스트

새 규칙을 추가할 때는 다음을 확인한다.

1. 상태가 `sim` 내부에만 존재하는가
2. 동일 입력에서 결과가 동일한가
3. tick당 할당이 증가하지 않는가
4. replay 재실행으로 재현 가능한가
5. 테스트 또는 golden hash를 추가했는가

## 관련 코드

- `sim/src/main/kotlin/App.kt`
  - CLI 파싱, tick 루프, world hash, stream 레코드 출력
- `sim/src/main/kotlin/ecs`
  - world, component maps, core systems (`Systems.kt`, `Harvest.kt`, `Buildings.kt`, `Production.kt`, `Research.kt`, `Victory.kt`)
- `sim/src/main/kotlin/ecs/path`
  - pathfinding
- `sim/src/main/kotlin/ecs/services`
  - fog (`FogGrid`)
- `sim/src/main/kotlin/net`, `sim/src/main/kotlin/replay`
  - 명령 모델/입력 파싱/script, replay 기록·IO·hash
- `sim/src/main/kotlin/client`
  - snapshot 소비, 로컬 플레이 클라이언트
- `sim/src/test/kotlin/starkraft/sim`
  - 결정론(`DeterminismTest`), replay(`ReplayHashTest`, `ReplayIOTest`, `ReplayRunnerTest`), 이동/전투(`RepathTest`, `CornerCutTest`, `CombatBehaviorTest`)/클라이언트 테스트
