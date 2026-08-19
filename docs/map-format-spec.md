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

- `mapId`
- `width`
- `height`
- tile passability 정보
- optional tile cost 정보
- initial resource node 목록
- optional spawn metadata

## 타일 규칙

### passability

- 이동 가능한지 여부를 나타낸다.
- pathfinding의 정적 기준이다.

### weighted terrain

- 이동 가능하지만 더 비싼 지형을 표현한다.
- A*는 이를 비용으로 반영한다.

## 자원 노드

각 노드는 최소한 다음을 가진다.

- node id
- resource kind
- position
- amount
- optional yield cap

## 스폰 정보

지원 또는 향후 확장 가능 항목:

- faction start position
- recommended camera start
- scenario preset marker

## 유효성 규칙

- 맵 크기는 1 이상
- blocked tile과 resource/building spawn이 모순되지 않아야 함
- weight는 음수가 아니어야 함
- resource node는 맵 경계 안에 있어야 함

## 도구 연계

맵은 다음 흐름에서 사용된다.

- `map generate`
- `map validate`
- sim 초기화
- scenario/preset 실행

## 운영 원칙

- 테스트용 맵과 플레이용 맵을 구분한다.
- benchmark용 맵은 고정 seed와 함께 관리한다.
- pathfinding regression은 작은 전용 맵으로 재현 가능해야 한다.

## 관련 코드

- `/Users/hammac/Projects/starkraft/sim/src/main/kotlin/ecs/MapGrid.kt`
- `/Users/hammac/Projects/starkraft/tools/src/main/kotlin`
- `/Users/hammac/Projects/starkraft/sim/scripts`
