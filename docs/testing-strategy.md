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

현재 구현 (`.github/workflows/ci.yml`, push/PR마다 실행):

- `sim-build-test`: `scripts/*.sh` 일부 `bash -n` 문법 검사, 공백-only `STARKRAFT_E2E_SIM_VERSION`/`STARKRAFT_PLAY_SIM_VERSION` 거부 확인, `:sim:build`, 짧은 `:sim:run`, `:sim:graphicalClient --headless`, `play_sim_smoke.sh`
- `tools-build-test`: `:tools:build`, `replay meta` smoke, `:tools:smoke` 후 `summary.json`의 `result == "ok"`, `outputVersion == 1`, 필수 키 검증
- `server-build-test` / `client-build-test`: `go build`, `go test`, `go vet`, `go mod verify`, `go mod tidy` 후 diff 없음 확인
- `e2e-smoke`: `e2e_server_bots_smoke.sh` 후 `tools replay verify-ndjson` / `replay stats --json` 결과 검증

야간 (`.github/workflows/nightly.yml`):

- `go-quality`: server/client `go vet`, `go mod verify`
- `sim-soak`: `:sim:soak --args="--minutes 1 --units 240 --seed 1234 --reportEverySec 30"`, `play_sim_smoke.sh`
- `replay-compat`: `DeterminismTest`, `ReplayHashTest`, `ReplayIOTest`
- `tools-health`: `:tools:smoke`와 summary payload 검증

CI의 JDK는 Temurin 17이며, Gradle이 Kotlin `jvmToolchain(21)`을 foojay resolver로 자동 프로비저닝한다. Go는 1.22다.

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

## 관련 코드와 명령

- `sim/src/test/kotlin/starkraft/sim`
  - 시뮬레이션/결정론/그래픽 클라이언트 테스트
  - 결정론/replay: `DeterminismTest`, `ReplayHashTest`, `ReplayIOTest`, `ReplayRunnerTest`(golden hash)
  - 경로탐색 회귀: `CornerCutTest`, `RepathTest`, `OccupancyStressTest`, `PathfindingSystemMetricsTest`
  - 규칙: `CombatBehaviorTest`, `BuildingPlacementTest`, `ProductionSystemTest`, `ResearchSystemTest`, `ResourceHarvestSystemTest`, `ResourceSystemTest`, `VictorySystemTest`, `SurrenderCommandTest`, `DataRepoTest`
  - 입력/스크립트: `InputJsonTest`, `InputTailReaderTest`, `ScriptRunnerTest`, `AppCliTest`, `AppTest`
  - 클라이언트/play 파이프라인: `GdxClientRuntimeTest`, `GameScreenInputRulesTest`, `ClientSessionTest`(partial NDJSON, concurrent append), `PlayLauncherTest`, `SnapshotStreamConsumerTest`, `ClientTcpBridgeTest`, `ClientWebSocketBridgeTest` 등
  - 프로토콜 golden: `protocol/ProtocolModelsTest`
  - 단일 클래스 실행: `./gradlew :sim:test --tests starkraft.sim.DeterminismTest`
- `tools/src/test/kotlin/starkraft/tools`
  - `ReplayToolTest`, `ReplayToolsTest`, `MapToolTest`, `MapToolsTest`, `DataToolTest`, `DataToolsTest`, `ToolsCliTest`, `ToolsOutputSchemaTest`
- `server/pkg/authoritative/*_test.go`
  - handshake, room lifecycle, validation matrix, admin/metrics, replay verify 테스트
- `server/pkg/protocol/models_test.go`, `client/pkg/protocol/golden_test.go`
  - `shared-protocol/golden/*.json`과의 byte 비교
- `client/pkg/headless/*_test.go`, `client/cmd/client/main_test.go`
  - 클라이언트 handshake/protocol/matchEnd/stream 테스트
- `./gradlew :sim:benchmark`, `:sim:pathfindingBenchmark`, `:sim:soak`
  - 성능/장시간 실행
- `scripts/tools_health.sh` (`./gradlew :tools:smoke`)
  - tools JSON 계약 smoke
- `scripts/smoke_run.sh`
  - 기본 smoke
- `scripts/e2e_server_bots_smoke.sh`
  - 서버 + 봇 + replay smoke
- `scripts/play_sim_smoke.sh`
  - sim play 파이프라인 smoke
