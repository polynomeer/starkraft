# Architecture Overview

## 목적

`starkraft`는 결정론적 RTS 시뮬레이션을 중심으로, 권위 서버, 클라이언트, 리플레이/맵 도구를 분리한 멀티모듈 프로젝트다.

핵심 원칙:

- `sim`은 게임 규칙의 단일 진실 공급원이다(목표 원칙). 단, 현재 Go 서버는 `sim`을 호출하지 않는다(아래 "온라인 매치" 참고).
- 서버는 권위적(authoritative) 오케스트레이션을 담당한다.
- 클라이언트는 입력과 시각화 계층이다.
- 도구는 오프라인 검증과 제작 지원 계층이다.

## 모듈 책임

### `sim/`

Kotlin/JVM 모듈(JDK 21 toolchain). 규칙 엔진뿐 아니라 클라이언트 코드도 이 모듈에 함께 있다.

- 고정 tick RTS 규칙 실행 (`App.kt`가 CLI 파싱과 tick 루프를 소유)
- 유닛/건물/자원/전투/시야/생산/연구 처리 (`ecs/`)
- 결정론 해시, 리플레이 기록/재실행, 헤드리스 실행 지원 (`replay/`, `net/`)
- snapshot 및 입력 파일 기반 로컬 플레이 지원
- libGDX 그래픽 클라이언트, 콘솔 클라이언트, TCP/WebSocket 파일 브리지 (`client/`)
- benchmark/soak 하네스 (`bench/`)

### `server/`

독립 Go 모듈 (`cmd/server`, `cmd/replaycheck`, `pkg/authoritative`, `pkg/protocol`).

- 웹소켓 접속(`/ws`)과 handshake/resume 처리
- room lifecycle(생성, 빈 room eviction)과 20ms 고정 tick 매치 실행
- 명령 검증, 소유권 검사, 기본 rate limiting
- snapshot broadcast와 replay JSONL 기록
- `/healthz`, `/admin/stats` 운영 엔드포인트
- 현재 room은 Kotlin `sim`이 아니라 자체 최소 Go 유닛 모델(move/attack/build/queue/surrender)과 자체 world hash를 사용한다

### `client/`

- 서버 접속용 헤드리스/CLI/봇 클라이언트 (독립 Go 모듈: `cmd/client`, `cmd/bot`, `pkg/headless`)
- snapshot 수신, 명령 전송, handshake/resume 처리
- 로컬 연동용 플레이어/봇 워크플로 지원

### `shared-protocol/`

- 프로토콜 JSON schema (`schema/rts-protocol-v1.schema.json`)
- golden 파일 (`golden/*.json`; server/client Go 테스트와 sim Kotlin `ProtocolModelsTest`가 비교)
- 버전 호환 규칙 기준점

### `tools/`

Kotlin CLI (`starkraft.tools.ToolsCliKt`, `:sim`에 의존).

- replay 분석/검증 CLI (`replay meta|stats|verify|verify-ndjson|fast-forward`)
- map generate/validate CLI
- data validate CLI

### `scripts/`

- smoke test
- 로컬 플레이 런처
- 패키징/부트스트랩 보조

## 상위 데이터 흐름

### 온라인 매치

1. 클라이언트(Go CLI/봇)가 서버에 handshake를 보낸다.
2. 서버가 room에 플레이어를 연결한다(없으면 room 생성).
3. 클라이언트 명령은 서버에서 검증된 뒤 room의 pending batch 큐에 적재된다.
4. 서버는 tick마다 pending batch를 room의 Go 유닛 모델에 적용하고 world hash를 다시 계산한다.
5. 서버는 snapshot/ack/replay 기록을 생성한다.
6. 클라이언트는 snapshot을 소비해 UI를 갱신한다.

주의: 4단계는 Kotlin `sim`을 호출하지 않는다. `server/pkg/authoritative/room.go`는 별도의 최소 유닛 모델(32x32 맵, 즉시 이동, 사거리 내 고정 피해 등)과 별도 해시 공식을 쓰므로, 서버 world hash와 `sim` world hash는 비교 대상이 아니다. 서버가 `sim` 규칙으로 매치를 진행하는 구조는 아직 미구현(계획)이다.

### 로컬 sim 플레이

1. `:sim:play`(`client/PlayLauncher.kt`)가 sim 프로세스를 `--snapshotOut`/`--snapshotEvery`/`--inputTail`/`--playControlFile` 옵션으로 띄워 snapshot NDJSON을 기록하게 한다.
2. 그래픽 클라이언트가 snapshot 스트림을 구독한다(`SnapshotStreamConsumer` → `ClientSnapshot`).
3. 입력은 별도 NDJSON으로 기록된다.
4. `sim`은 `InputTailReader`로 입력 파일을 tail해 다음 tick 명령으로 반영한다.

이 경로는 서버를 거치지 않는 파일 기반 파이프라인이다. `ClientTcpBridge`/`ClientWebSocketBridge`는 같은 두 파일을 소켓으로 노출할 뿐, Go 서버 프로토콜과는 별개다.

## 아키텍처 경계

### `sim` 안에 있어야 하는 것

- 이동
- 전투
- 경제
- 생산
- 연구
- 승리 판정
- 결정론 해시

### `sim` 밖에 있어야 하는 것

- 소켓 IO
- 파일 보관 정책
- 계정/방 관리
- 렌더링
- UI 상태

## 현재 상태

- `sim`은 플레이 가능한 RTS 샌드박스 수준으로 성숙해 있다.
- `server`와 `client`는 기본적인 권위 서버 스택(handshake/resume, 검증, replay)을 제공하지만, 서버의 게임 모델은 `sim`과 분리된 최소 Go 구현이다.
- Kotlin 그래픽 클라이언트는 아직 Go 서버에 접속하지 않는다(로컬 파일 파이프라인 전용).
- 그래픽 클라이언트는 디버그/프로토타입 수준이며, 제품 수준 UI/렌더링 구조화가 다음 개선 축이다.

## 다음 아키텍처 우선순위

1. 그래픽 클라이언트의 view model / renderer 분리
2. (미구현) 서버가 `sim` 규칙 엔진으로 매치를 진행하도록 연결
3. 네트워크 클라이언트와 그래픽 클라이언트 통합 경로 정리
4. 리플레이/스냅샷 계약을 장기 호환 포맷으로 고정
5. 장시간 세션 기준 성능/메모리 하드닝

## 관련 코드

- `sim/src/main/kotlin`
  - 결정론 시뮬레이션과 로컬 플레이 파이프라인 (디렉터리 구조가 패키지와 일치하지 않음: `ecs/World.kt`는 `package starkraft.sim.ecs`)
- `server/pkg/authoritative`
  - 권위 서버(`server.go`), room/Go 유닛 모델(`room.go`), replay(`replay.go`, `replay_verify.go`)
- `client/pkg/headless`
  - Go 클라이언트 연결/스트림 처리
- `shared-protocol/schema`
  - JSON schema 기준점
- `tools/src/main/kotlin/starkraft/tools`
  - replay/map/data 유틸리티
