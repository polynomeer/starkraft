# Testing Strategy

## 목적

`starkraft`는 시뮬레이션 결정론과 권위 서버 동작이 핵심이므로, 테스트 전략은 기능 정확성보다 먼저 재현성과 안정성을 보장해야 한다.

## 테스트 계층

### 1. Unit Test

대상:

- pathfinding
- occupancy
- combat rule helpers
- protocol models
- replay parsing
- script parsing

원칙:

- deterministic
- single-responsibility
- 작은 fixture 사용

### 2. Integration Test

대상:

- server handshake
- command validation
- snapshot broadcast
- replay write/load
- client handshake/stream 처리

### 3. Determinism Test

대상:

- 같은 seed + 같은 command stream → 같은 final hash
- replay reload → 같은 replay/world hash

이 테스트는 시뮬레이션 회귀 방지의 핵심이다.

### 4. Regression Test

버그 수정 시 추가해야 한다.

예시:

- mid-path blocker 등장 시 replan
- diagonal corner-cut blocking
- protocol mismatch close reason
- whitespace/empty field validation

### 5. Performance / Benchmark

대상:

- tick p50/p95/p99
- pathfinding node budget usage
- replan count
- queue sizes
- allocation pressure 추세

### 6. Soak Test

대상:

- 장시간 headless 실행
- replay 생성 지속성
- 메모리 증가 여부
- 안정적인 match end 도달

## 모듈별 우선 테스트

### `sim`

- movement/pathfinding
- economy/production/research
- world hash determinism
- replay rerun

### `server`

- handshake validation
- room lifecycle
- ownership/bounds/rate limit validation
- replay persistence

### `client`

- protocol validation
- snapshot stream handling
- resume token handling

### `tools`

- replay verify/stats
- map validate/generate
- data validate

## CI 기준

최소 CI 게이트:

1. Gradle build/test
2. Go build/test
3. protocol golden test
4. smoke scripts
5. replay/tooling contract checks

## 테스트 작성 규칙

- flaky test 금지
- wall clock 의존 최소화
- random 사용 시 seed 고정
- 실패 시 원인 문자열이 명확해야 한다

## 릴리스 전 검증

- full test suite
- benchmark spot check
- replay verify
- local stack smoke
- sim graphical play smoke
