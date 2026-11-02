# Data Schema Spec

## 목적

유닛, 건물, 기술, 시각 자산 메타데이터 같은 데이터 파일의 구조와 검증 원칙을 정의한다.

## 데이터 설계 원칙

- 가능하면 규칙 로직이 아니라 데이터로 표현한다.
- `typeId`, `techId` 같은 식별자는 저장소 전체에서 안정적으로 유지한다.
- nullable 필드는 의미가 분명해야 한다.
- 기본값이 중요하면 로더와 문서에 동시에 명시한다.

## 데이터 분류

모든 정의 파일(`units`/`buildings`/`techs`/`weapons`)은 `{"list": [ ... ]}` 컨테이너 형식이다(`tools/schema/data-defs-v1.schema.json`). 런타임 로더는 `sim/src/main/kotlin/data/Schema.kt`의 data class와 `DataRepo.kt`이며, 알 수 없는 키는 무시된다(`ignoreUnknownKeys = true`). 아래 괄호 안 값은 생략 시 기본값이다.

### 유닛 데이터 (`units.json`)

필수 필드:

- `id` (문서상 `typeId`)
- `hp`

선택 필드:

- `archetype` (`"genericUnit"`)
- `armor` (0)
- `speed` (0.06, tile/tick)
- `weaponId` (null, `weapons.json` 참조)
- `mineralCost`, `gasCost` (0)
- `buildTicks` (0, 0이면 생산 불가: `trainSpec`이 null)
- `footprintWidth`, `footprintHeight` (0)
- `producerTypes` (빈 목록 = 생산 건물 제한 없음)
- `requiredBuildingTypes`, `requiredResearchIds` (빈 목록)

미구현(계획): `vision`, `supply`, harvest capability, special flags. 현재 시야는 코드 상수이고 채집 능력은 `harvest` 명령으로 부여된다.

### 건물 데이터 (`buildings.json`)

필수 필드:

- `id`
- `hp`
- `footprintWidth`
- `footprintHeight`

선택 필드:

- `archetype` (`"genericBuilding"`)
- `armor` (0)
- `buildTicks` (0, 0이면 배치 즉시 완성)
- `placementClearance` (0)
- `supportsTraining`, `supportsResearch`, `supportsRally`, `supportsDropoff` (false)
- `dropoffResourceKinds` (빈 목록, `minerals`/`gas`)
- `productionQueueLimit` (5, 연구 queue에도 같은 한도 적용)
- `rallyOffsetX`, `rallyOffsetY` (0.0)
- `mineralCost`, `gasCost` (0)
- `requiredBuildingTypes`, `requiredResearchIds` (빈 목록)

### 기술 데이터 (`techs.json`)

필수 필드:

- `id` (문서상 `techId`)
- `buildTicks` (연구 시간)

선택 필드:

- `mineralCost`, `gasCost` (0)
- `producerTypes` (빈 목록)
- `requiredBuildingTypes`, `requiredResearchIds` (빈 목록)

미구현(계획): `display name`, `effect summary`. 현재 연구 완료는 faction unlock 집합에 id를 추가할 뿐이다.

### 무기 데이터 (`weapons.json`)

필수 필드: `id`, `damage`, `range`, `cooldownTicks` (기본값 없음).

### 시각 데이터 (`visuals.json`)

현재 구조 (`sim/src/main/kotlin/client/VisualAssetCatalog.kt`):

- 최상위 키: `units`, `unitArchetypes`, `buildings`, `buildingArchetypes`, `resources`, `terrain`(`base`/`blocked`/`occupied`)
- 각 항목: `asset`(PNG 경로), `scale`(1.0), `tint`(null), `teamTintStrength`(0.0)
- 파일이나 PNG가 없으면 클라이언트는 절차적 렌더링으로 fallback한다.

향후 대상(미구현):

- atlas
- anchor / draw layer

## 식별자 규칙

- `typeId`, `techId`는 코드와 데이터에서 정확히 일치해야 한다.
- 이름 변경은 단순 문자열 치환이 아니라 migration으로 간주한다.
- replay/script/protocol에서 참조되는 id는 안정성이 더 중요하다.

## 검증 규칙

현재 `tools data validate --dir <path>`(`tools/src/main/kotlin/starkraft/tools/DataTool.kt`)가 다음을 검사한다. `units`/`buildings`/`techs`/`weapons` 파일을 `.json`/`.yaml`/`.yml`로 찾는다.

### 구조 검증

- 네 파일 존재 여부
- `list`가 객체 배열인지
- 각 항목의 비어 있지 않은 `id`
- 파일 내 중복 id 금지

### 참조 검증

- 유닛 `weaponId`가 `weapons`에 존재하는가
- 유닛/기술의 `producerTypes`가 `buildings`에 존재하는가
- `supportsDropoff=true` 건물은 `dropoffResourceKinds`가 비어 있지 않아야 한다
- `dropoffResourceKinds` 값은 `minerals` 또는 `gas`

### 의미 검증

`tools/src/main/kotlin/starkraft/tools/DataTools.kt`의 `validateGameData`는 다음을 추가로 검사하지만 현재 CLI에는 연결되어 있지 않고 테스트에서만 사용된다.

- `hp > 0`, 유닛 `speed >= 0`
- 건물 footprint 0 이하 금지
- `productionQueueLimit >= 0` (0은 생산 불가 건물에 유효: `ResourceDepot`, `GasDepot`)
- 기술 `buildTicks > 0`
- `requiredBuildingTypes`/`requiredResearchIds` 참조 존재 여부
- producer 건물이 각각 `supportsTraining`/`supportsResearch`인지

미구현(계획):

- 음수 비용 금지
- visual asset id가 실제 자산과 연결되는가

## 변경 절차

1. 데이터 변경 목적을 기록한다.
2. schema/validator가 필요한지 확인한다.
3. 관련 테스트를 추가한다.
4. replay/script 영향 여부를 확인한다.

## 관련 코드

- [`sim/src/main/resources/data`](../sim/src/main/resources/data)
- [`sim/src/main/kotlin/data`](../sim/src/main/kotlin/data)
- [`tools/schema/data-defs-v1.schema.json`](../tools/schema/data-defs-v1.schema.json)
- [`tools/src/main/kotlin`](../tools/src/main/kotlin)
- [`sim/src/test/kotlin/starkraft/sim`](../sim/src/test/kotlin/starkraft/sim)
