# Data Schema Spec

## 목적

유닛, 건물, 기술, 시각 자산 메타데이터 같은 데이터 파일의 구조와 검증 원칙을 정의한다.

## 데이터 설계 원칙

- 가능하면 규칙 로직이 아니라 데이터로 표현한다.
- `typeId`, `techId` 같은 식별자는 저장소 전체에서 안정적으로 유지한다.
- nullable 필드는 의미가 분명해야 한다.
- 기본값이 중요하면 로더와 문서에 동시에 명시한다.

## 데이터 분류

### 유닛 데이터

최소 필드:

- `typeId`
- `archetype`
- `hp`
- `armor`
- `speed`
- `vision`
- `cost`
- `buildTicks`
- `supply`

선택 필드:

- `weaponId`
- `harvest capability`
- `special flags`

### 건물 데이터

최소 필드:

- `typeId`
- `archetype`
- `footprintWidth`
- `footprintHeight`
- `placementClearance`
- `minerals`
- `gas`
- `buildTicks`

선택 필드:

- `supportsTraining`
- `supportsResearch`
- `dropOffKinds`
- `queueLimit`
- `rallyOffset`

### 기술 데이터

최소 필드:

- `techId`
- `display name`
- `prerequisites`
- `minerals`
- `gas`
- `researchTicks`
- `effect summary`

### 시각 데이터

현재/향후 대상:

- sprite id
- atlas/asset 경로
- 팀 컬러 처리 방식
- scale / anchor / draw layer

## 식별자 규칙

- `typeId`, `techId`는 코드와 데이터에서 정확히 일치해야 한다.
- 이름 변경은 단순 문자열 치환이 아니라 migration으로 간주한다.
- replay/script/protocol에서 참조되는 id는 안정성이 더 중요하다.

## 검증 규칙

### 구조 검증

- 필수 필드 존재 여부
- 숫자 범위 유효성
- 중복 id 금지

### 참조 검증

- 생산 가능한 `typeId`가 실제 존재하는가
- prerequisite가 실제 데이터에 존재하는가
- visual asset id가 실제 자산과 연결되는가

### 의미 검증

- 음수 비용 금지
- footprint 0 이하 금지
- queue limit 1 미만 금지

## 변경 절차

1. 데이터 변경 목적을 기록한다.
2. schema/validator가 필요한지 확인한다.
3. 관련 테스트를 추가한다.
4. replay/script 영향 여부를 확인한다.

## 관련 코드

- `/Users/hammac/Projects/starkraft/sim/src/main/resources/data`
- `/Users/hammac/Projects/starkraft/tools/src/main/kotlin`
- `/Users/hammac/Projects/starkraft/sim/src/test/kotlin/starkraft/sim`
