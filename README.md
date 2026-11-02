# Starkraft

결정론적 시뮬레이션, 권위 서버, 리플레이 검증 도구를 한 저장소에 담은 RTS 실험/개발 스택이다.  
게임 자체를 만드는 프로젝트이면서 동시에 "재현 가능한 실시간 시뮬레이션 시스템을 어떻게 설계하고 운영할 것인가"를 보여주는 엔지니어링 포트폴리오이기도 하다.

## 한눈에 보기

- 장르: 1v1 RTS sandbox
- 핵심 엔진: 고정 tick 결정론 시뮬레이션
- 구조: `sim` + `server` + `client` + `tools` + `shared-protocol`
- 주요 가치: 동일 입력 동일 결과, authoritative server, replay-first 검증, 모듈 분리
- 현재 상태: 로컬 플레이와 서버 기반 스모크 경로가 동작하는 플레이어블 샌드박스

## 이 프로젝트가 무엇을 보여주는가

### 제품 관점

`starkraft`는 자원 수집, 건설, 생산, 연구, 전투, 시야, 승패 판정을 갖춘 RTS 규칙 세트를 구현한다.  
단순한 데모가 아니라 "플레이 가능한 게임 규칙"과 "운영 가능한 멀티플레이 구조" 사이의 경계를 의식해서 설계되어 있다.

### 엔지니어링 관점

이 저장소는 다음 문제를 어떻게 푸는지 보여준다.

- 결정론적 시뮬레이션을 고정 tick으로 유지하는 방법
- authoritative server에서 클라이언트 입력을 검증하고 판정하는 방법
- replay, world hash, golden schema로 회귀를 잡는 방법
- Kotlin/JVM 시뮬레이션과 Go 네트워크 스택을 공유 프로토콜 계약(schema + golden)으로만 느슨하게 결합하는 방법
- 헤드리스 자동 검증 경로와 사람이 직접 플레이하는 경로를 동시에 유지하는 방법

채용담당자나 첫 방문자가 보기 좋은 포인트는 "기능이 많다"보다 아래 세 가지다.

1. 게임 규칙, 네트워크, 도구, 운영 문서가 모듈 경계에 맞춰 분리되어 있다.
2. 문서가 코드 구조와 검증 명령으로 이어진다.
3. 시뮬레이션의 재현성과 운영 관측성을 설계 중심에 둔다.

## 현재 구현 범위

### 구현된 핵심 기능

- 고정 tick 기반 시뮬레이션 루프
- 이동, 경로탐색, 재경로탐색, 점유 처리
- 전투, 자동 타겟팅, attack-move, hold, patrol
- 안개, 시야, faction 단위 정보 은닉
- 자원 채집, 운반, drop-off, stockpile
- 건물 배치, 건설 진행, 취소/환불 규칙
- 생산 큐, 랠리 포인트, 연구/기술 선행조건
- 승리 판정과 match-end reason 기록
- 서버 handshake/resume, room lifecycle, 명령 검증, replay 기록
- CLI 클라이언트, 봇 클라이언트, libGDX 기반 그래픽 클라이언트
- replay/map/data 검증용 오프라인 CLI 도구

### 의도적으로 아직 범위 밖인 것

- 상용 수준 아트/오디오 파이프라인 완성
- 대규모 온라인 서비스 기능
- 캠페인/콘텐츠 중심 게임 패키징
- 모바일/콘솔 배포

## 저장소 구조

| 경로 | 역할 |
| --- | --- |
| `sim/` | Kotlin 기반 결정론 RTS 시뮬레이션 코어, libGDX 그래픽/콘솔 클라이언트, 로컬 플레이 파이프라인 |
| `server/` | Go 기반 authoritative multiplayer host |
| `client/` | Go 기반 CLI/봇 headless client |
| `tools/` | replay/map/data 검증용 Kotlin CLI |
| `shared-protocol/` | JSON schema와 golden protocol payload |
| `docs/` | 제품 기획, 설계, 운영, 테스트, UX, 데이터 명세 문서 |
| `scripts/` | bootstrap, smoke, local play, packaging 자동화 스크립트 |

## 아키텍처 개요

### 상위 구조

1. 클라이언트는 명령만 보낸다.
2. 로컬 플레이 경로(`:sim:play`)에서는 서버 없이 `sim`이 규칙을 판정하고 snapshot NDJSON을 기록하며, libGDX 클라이언트는 그 스트림을 읽고 명령을 input NDJSON으로 돌려준다.
3. 네트워크 경로에서 Go 서버는 handshake, room, validation, tick orchestration을 담당한다.
4. 현재 Go 서버(`server/pkg/authoritative/room.go`)는 Kotlin `sim`을 호출하지 않는다. move/attack/build/queue/surrender만 다루는 자체 최소 유닛 모델과 자체 world hash로 판정하므로, 서버 replay의 hash와 `sim` hash는 서로 비교 대상이 아니다.
5. 서버는 snapshot, ack, replay를 기록/중계한다.
6. 도구는 replay와 데이터 계약을 오프라인으로 검증한다.

### 핵심 설계 원칙

- 게임 규칙의 단일 진실 공급원은 `sim`이다. (Go 서버의 room 모델은 네트워크 경로 검증용 최소 구현이며 `sim` 규칙을 대체하지 않는다.)
- 클라이언트는 입력과 렌더링 계층이다.
- 프로토콜은 schema와 golden 파일로 고정한다.
- replay는 디버깅 도구가 아니라 회귀 방지 자산이다.
- 성능 최적화보다 먼저 결정론을 지킨다.

## 기술적으로 중요한 차별점

### 1. 결정론 우선 구조

- `Time.TICK_MS = 20` 고정 tick 기반
- iteration 순서와 command 적용 순서를 안정적으로 유지
- world hash와 replay hash로 결과 재현성 검증
- determinism test, replay rerun test, smoke script를 별도 보유

### 2. 권위 서버와 재연결 설계

- `server`는 websocket ingress, room lifecycle, command validation, snapshot broadcast를 담당
- handshake 이후에도 protocol mismatch를 명시적으로 검증
- `resumeToken` 기반 세션 복구 흐름 지원
- 빈 값, 공백, 크기 제한, queue cap, rate limit 등 입력 하드닝 포함

### 3. 도구화된 검증 경로

- `tools` 모듈로 replay verify/stats/meta, map validate/generate, data validate 제공
- `scripts/tools_health.sh`로 JSON contract smoke 수행
- 운영용 replay 파일과 개발용 sim replay를 둘 다 검증하는 경로 보유

### 4. 플레이 경로가 하나가 아니다

- headless sim 단독 실행
- snapshot 기반 그래픽 클라이언트 플레이
- authoritative server + bot/CLI 조합의 네트워크 플레이
- 각 경로에 대해 독립된 smoke/test 자산 존재

## 빠른 시작

### 요구 사항

- JDK 17 이상 (Gradle 8.10 실행용, CI는 Temurin 17 사용)
  - `sim`/`tools`는 Kotlin `jvmToolchain(21)`로 빌드된다. 로컬에 JDK 21이 없으면 foojay toolchain resolver가 자동으로 내려받는다.
- Go 1.22 이상 (`server/go.mod`, `client/go.mod` 기준 `go 1.22`, CI도 1.22)
- macOS 또는 Linux 셸 환경 권장
- `bash`, `curl`, `mktemp` 같은 기본 유틸리티

### 최초 준비

```bash
./scripts/bootstrap_dev.sh
```

이 스크립트는 아래를 처리한다.

- Gradle wrapper runtime 확인
- `server/` Go module 다운로드
- `client/` Go module 다운로드

### 가장 빠른 검증

```bash
./scripts/smoke_run.sh
```

이 스크립트는 다음을 확인한다.

- `:sim:build` 성공
- 짧은 deterministic match 실행
- 최종 `world hash=` 출력 존재

## 실행 방법

### 1. 헤드리스 시뮬레이션 실행

```bash
./gradlew :sim:run --args="--ticks 200 --noSleep"
```

유용한 옵션 예시:

- `--dumpWorldHash`: 최종 world hash 출력
- `--snapshotEvery N`: snapshot NDJSON 주기 기록
- `--snapshotOut <path>`: snapshot 기록 파일 지정
- `--seed N`: 시드 지정
- `--script <file>`: `.script` 명령 파일 실행 (예: `sim/scripts/sample.script`)
- `--replayDump <path>` / `--replay <path>`: replay 기록 / 재생
- `--inputTail <path>`: live input NDJSON tail
- `--help`: 전체 옵션 출력

### 2. 로컬 그래픽 플레이

```bash
./scripts/play_sim_graphical.sh
```

`STARKRAFT_SIM_PLAY_DIR`(기본 `/tmp/starkraft-play`), `STARKRAFT_SIM_PLAY_TICKS`(기본 `5000`), `STARKRAFT_SIM_PLAY_SCENARIO`(기본 `skirmish`)로 실행 인자를 바꿀 수 있다.

또는:

```bash
./gradlew :sim:play
```

이 경로는 시뮬레이션과 libGDX 클라이언트를 함께 띄워 로컬 플레이/디버그를 가능하게 한다.

성능 관련 기본 가드레일:

- `:sim:play`는 기본적으로 snapshot 기록 주기를 `2 tick`으로 사용한다.
- 스트림 파서는 필요한 record type만 선별해서 디코드하고, poll 당 처리량도 제한한다.
- 초기 snapshot 대기 시간은 `STARKRAFT_PLAY_WAIT_SNAPSHOT_MS`로 조정할 수 있다.
- snapshot 기록 주기는 `STARKRAFT_PLAY_SNAPSHOT_EVERY`로 조정할 수 있다.

성능 재현과 계측은 아래 경로를 사용한다.

```bash
./scripts/play_sim_profile.sh
```

이 스크립트는 `STARKRAFT_SIM_DEBUG=1`로 `:sim:play`를 실행한다(기본 `10000` tick, 출력 디렉터리 `/tmp/starkraft-play-profile`).

관련 절차 문서:

- `docs/performance-debugging-workflow.md`
- `docs/project-overview.md`
- `docs/technical-deep-dive.md`

### 3. 서버 + 봇 + CLI 기반 로컬 스택 플레이

```bash
./scripts/play_stack_local.sh
```

이 스크립트는 다음을 순서대로 준비한다.

- Go 서버 실행
- 선택적으로 봇 실행
- 사용자용 CLI 클라이언트 실행
- replay 파일 기록

주요 env: `STARKRAFT_PLAY_PORT`(기본 `18080`), `STARKRAFT_PLAY_ROOM`(기본 `local-play`), `STARKRAFT_PLAY_BOT`(`1`/`0`, 기본 `1`), `STARKRAFT_PLAY_SIM_VERSION`(기본 `dev`, 공백만 있는 값은 거부), `STARKRAFT_PLAY_TMP_DIR`, `STARKRAFT_PLAY_REPLAY`.

### 4. 서버 기반 종단 스모크

```bash
./scripts/e2e_server_bots_smoke.sh
```

검증 내용:

- 서버 기동 및 `/healthz` 응답
- 2개 봇의 접속 및 match 종료
- replay 기록 존재
- replay에 `header`, `command`, `matchEnd` 포함
- `replaycheck` 검증 통과

주요 env: `STARKRAFT_E2E_PORT`(기본 `18080`), `STARKRAFT_E2E_TMP_DIR`, `STARKRAFT_E2E_SIM_VERSION`(기본 `dev`, 공백만 있는 값은 거부).

## 자주 쓰는 개발 명령

### Gradle / Kotlin

```bash
./gradlew :sim:build
./gradlew :sim:test
./gradlew :sim:test --tests starkraft.sim.DeterminismTest
./gradlew :sim:benchmark
./gradlew :sim:pathfindingBenchmark
./gradlew :sim:soak --args="--minutes 30 --units 120 --seed 1234 --reportEverySec 10"
./gradlew :tools:build
./gradlew :tools:test
./gradlew :tools:smoke
./gradlew :tools:run --args="replay stats /tmp/example.replay.jsonl --json"
```

보조 클라이언트/브리지 태스크 (`sim/build.gradle.kts`):

```bash
./gradlew :sim:graphicalClient --args="--headless --headlessTicks 20 <snapshots.ndjson> <input.ndjson>"
./gradlew :sim:consoleClient --args="<input.ndjson|tcp://host:port> [snapshot.ndjson|tcp://host:port|-]"
./gradlew :sim:consumeSnapshotStream --args="<snapshots.ndjson>"
./gradlew :sim:tcpClientBridge --args="<snapshots.ndjson> <input.ndjson> <snapshotPort> <inputPort>"
./gradlew :sim:webSocketClientBridge --args="<snapshots.ndjson> <input.ndjson> <snapshotPort> <inputPort>"
```

### Go

```bash
cd server && go build ./... && go test ./... && go mod verify
cd client && go build ./... && go test ./... && go mod verify
cd server && go vet ./...
cd client && go vet ./...
```

CI는 추가로 `go mod tidy` 후 `go.mod`/`go.sum`에 diff가 없는지 확인한다.

### 운영/도구 스모크

```bash
./scripts/play_sim_smoke.sh
./scripts/tools_health.sh
```

### 릴리스 패키징

```bash
./scripts/release_package.sh [outDir] [version]
```

`:sim:distZip` 결과물, (Go가 있으면) 서버/CLI/봇 바이너리, `manifest-<version>.txt`를 `outDir`(기본 `dist/release`)에 만든다.

### CI

- `.github/workflows/ci.yml` (push/PR): `sim-build-test`, `tools-build-test`, `server-build-test`, `client-build-test`, `e2e-smoke`
- `.github/workflows/nightly.yml` (매일, 수동 실행 가능): `go-quality`, `sim-soak`(1분 soak + play smoke), `replay-compat`(`DeterminismTest`/`ReplayHashTest`/`ReplayIOTest`), `tools-health`

## 품질 보증 방식

이 프로젝트의 신뢰성은 "기능 설명"이 아니라 "검증 자산"에서 나온다.

### 테스트 계층

- 단위 테스트: pathfinding, protocol model, replay parsing, data helpers
- 통합 테스트: server handshake, room lifecycle, snapshot broadcast, client stream 처리
- 결정론 테스트: 같은 seed + 같은 입력 = 같은 final hash
- 회귀 테스트: corner-cut, replan, protocol mismatch, 입력 검증
- 성능 테스트: benchmark, soak harness, pathfinding budget 측정

### 검증 자산

- `shared-protocol/schema/rts-protocol-v1.schema.json`
- `shared-protocol/golden/*`
- `scripts/smoke_run.sh`
- `scripts/e2e_server_bots_smoke.sh`
- `scripts/play_sim_smoke.sh`
- `scripts/tools_health.sh`

## 문서 읽기 가이드

### 처음 방문자

1. `docs/project-overview.md`
2. `docs/product-vision.md`
3. `docs/architecture-overview.md`
4. `docs/technical-deep-dive.md`

### 채용담당자 / 기술면접관

1. `docs/project-overview.md`
2. `docs/technical-deep-dive.md`
3. `docs/testing-strategy.md`
4. `docs/server-ops-runbook.md`

### 기능/기획 담당자

1. `docs/product-vision.md`
2. `docs/game-design-document.md`
3. `docs/feature-backlog.md`
4. `docs/ui-ux-spec.md`

### 엔지니어

1. `docs/architecture-overview.md`
2. `docs/simulation-architecture.md`
3. `docs/protocol-spec.md`
4. `docs/replay-format-spec.md`
5. `docs/data-schema-spec.md`

## 문서 인덱스

문서 허브는 `docs/README.md`에 정리되어 있다.  
운영 절차는 `OPERATIONS.md`, 릴리스 게이트는 `RELEASE_CHECKLIST.md`, 변경 이력은 `CHANGELOG.md`를 본다.

## 프로젝트 강점 요약

- 게임 규칙과 네트워크 계층의 책임 분리가 명확하다.
- replay와 hash를 중심으로 재현성 검증 경로가 설계되어 있다.
- 서버/클라이언트/도구 문서가 실행 명령과 이어진다.
- 포트폴리오 관점에서 "무엇을 만들었는가"와 "어떻게 검증하는가"가 동시에 드러난다.
