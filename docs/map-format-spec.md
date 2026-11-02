# Map Format Spec

## 목적

맵 데이터가 어떤 구조를 가져야 하고, 시뮬레이션과 도구가 이를 어떻게 해석하는지 정의한다.

## 맵의 역할

맵은 다음 규칙의 입력이다.

- passability
- weighted terrain
- spawn location
- resource node placement
- building placement 가능 영역

## 최소 맵 구성

현재 형식은 `tools/schema/map-v1.schema.json`(Map v1, JSON 또는 YAML)이다.

- `schema` (필수, 정수 `1`)
- `id` (필수, 비어 있지 않은 문자열, 문서상 `mapId`)
- `width` (필수, 1 이상)
- `height` (필수, 1 이상)
- tile passability 정보: `blockedTiles` (선택, `[{x, y}]`)
- optional tile cost 정보: `weightedTiles` (선택, `[{x, y, cost}]`)
- initial resource node 목록: `resources` (선택, `[{kind, x, y, amount, yieldPerTick}]`)
- optional spawn metadata: `spawns` (선택, `[{faction, x, y}]`)

좌표는 모두 0 이상의 정수 tile 좌표다. 목록에 없는 타일은 통행 가능, cost 1.0이다.

예시:

```json
{
  "schema": 1,
  "id": "example",
  "width": 64,
  "height": 64,
  "blockedTiles": [{ "x": 10, "y": 10 }],
  "weightedTiles": [{ "x": 12, "y": 10, "cost": 3.0 }],
  "resources": [{ "kind": "MineralField", "x": 1, "y": 1, "amount": 1200, "yieldPerTick": 2 }],
  "spawns": [{ "faction": 1, "x": 0, "y": 0 }, { "faction": 2, "x": 63, "y": 63 }]
}
```

## 타일 규칙

### passability

- 이동 가능한지 여부를 나타낸다.
- pathfinding의 정적 기준이다.

### weighted terrain

- 이동 가능하지만 더 비싼 지형을 표현한다.
- A*는 이를 비용으로 반영한다.
  - 이동 비용 = step(직교 1, 대각 √2) x 도착 타일 cost (`ecs/path/Pathfinder.kt`).
  - `cost`는 1.0 이상이어야 한다.

## 자원 노드

각 노드는 최소한 다음을 가진다.

- node id (맵 파일에는 없음: sim이 엔티티 id를 부여한다)
- resource kind (`kind`, 예: `MineralField`, `GasGeyser`)
- position (`x`, `y`)
- amount (`amount`, 1 이상)
- optional yield cap (`yieldPerTick`: 맵 v1 스키마에서는 필수이며 1 이상. sim 내부 `ResourceNode`에서는 0이 "제한 없음"을 뜻한다)

## 스폰 정보

지원 또는 향후 확장 가능 항목:

- faction start position (지원: `spawns[].faction`은 1 이상)
- recommended camera start (미구현)
- scenario preset marker (미구현)

## 유효성 규칙

`tools map validate`(`tools/src/main/kotlin/starkraft/tools/MapTool.kt`)가 현재 검사하는 항목:

- `schema == 1`, `id` 비어 있지 않음
- 맵 크기는 1 이상
- blocked/weighted tile, resource, spawn이 맵 경계 안에 있어야 함
- weight(`cost`)는 1 이상
- resource `kind` 비어 있지 않음, `amount > 0`, `yieldPerTick > 0`
- spawn `faction >= 1`
- 알 수 없는 키는 오류로 처리된다(엄격 파싱)

미구현(계획):

- blocked tile과 resource/building spawn이 모순되지 않아야 함
- 중복 tile 검사 (`MapTools.kt`의 `validateToolMap`에는 있으나 CLI에 연결되어 있지 않고 테스트에서만 사용)

## 도구 연계

맵은 다음 흐름에서 사용된다.

- `map generate <path> [--width N] [--height N] [--seed N] [--json]`
  - 기본값 64x64, seed 1337.
  - 약 `width*height/25`개의 blocked tile, 약 `width*height/20`개의 weighted tile(cost 2~4)을 seed 기반으로 무작위 배치한다.
  - 자원: `MineralField` (1,1) amount 1200, `GasGeyser` (width-2, height-2) amount 800, 둘 다 `yieldPerTick` 2.
  - spawn: faction 1 (0,0), faction 2 (width-1, height-1).
- `map validate <path> [--json]` (`.yaml`/`.yml` 확장자면 YAML로 읽는다)
- sim 초기화 (미구현: 현재 sim은 맵 파일을 읽지 않는다)
- scenario/preset 실행

현재 sim 동작:

- `App.kt`가 96x96 데모 맵(차단 lane/choke, cost 3.0과 1.5 지형)과 시작 자원 노드를 코드로 구성한다.
- 추가 자원 노드는 script/input의 `spawnNode` 명령으로 생성한다.
- sim은 맵 상태(blocked/weighted tile, 정적 점유 tile, 자원 노드)를 snapshot 스트림의 map state 레코드로 내보내며, 클라이언트는 이를 사용한다.

## 운영 원칙

- 테스트용 맵과 플레이용 맵을 구분한다.
- benchmark용 맵은 고정 seed와 함께 관리한다.
- pathfinding regression은 작은 전용 맵으로 재현 가능해야 한다.

## 관련 코드

- [`tools/schema/map-v1.schema.json`](../tools/schema/map-v1.schema.json)
- [`tools/src/main/kotlin/starkraft/tools/MapTool.kt`](../tools/src/main/kotlin/starkraft/tools/MapTool.kt)
- [`sim/src/main/kotlin/ecs/MapGrid.kt`](../sim/src/main/kotlin/ecs/MapGrid.kt)
- [`sim/src/main/kotlin/ecs/path/Pathfinder.kt`](../sim/src/main/kotlin/ecs/path/Pathfinder.kt)
- [`sim/scripts`](../sim/scripts)
