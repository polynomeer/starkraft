# Security / Validation Spec

## 목적

권위 서버와 입력 검증 계층이 어떤 위협을 막아야 하는지, 그리고 최소한 어떤 검증을 해야 하는지 정의한다.

## 보안 목표

- 클라이언트는 상태를 제안할 수 없고 명령만 제안할 수 있어야 한다.
- 서버는 모든 게임 상태 변경을 직접 판정해야 한다.
- 비정상 입력이 서버 안정성을 해치지 않아야 한다.

## 위협 모델

### 명령 위조

- 다른 플레이어 유닛에 대한 명령 발행
- 존재하지 않는 target/typeId 참조
- 허용되지 않은 명령 타입 전송

### 범위 초과 입력

- 맵 바깥 좌표
- 음수 또는 비정상 tick
- 과도한 batch 크기

### 재연결 오용

- invalid resume token
- 만료된 resume token
- 다른 세션 토큰 재사용

### 서비스 품질 저하

- 명령 스팸
- 비정상 handshake 반복
- 큰 payload 전송

## 필수 검증

아래는 현재 서버(`server/pkg/authoritative`)에 구현된 검증과 기본 한도다. 한도는 `authoritative.Config` 기본값이며 현재 env로 조정할 수 없다.

### Transport 단계

- websocket read 크기 상한: 64 KiB (`MaxReadBytes`). 초과 시 연결이 끊긴다.
- 송신은 클라이언트별 write lock + 1.5초 write deadline으로 tick 루프 블로킹을 막는다. 이미 송신 중이면 해당 메시지는 버려진다 (`connection busy` 로그).
- `MaxOutboundQueue`(기본 128) 설정 필드는 있지만 현재 어디에서도 사용되지 않는다 (미구현).
- websocket Origin 검사는 하지 않는다 (`CheckOrigin`이 항상 true).
- `/admin/stats`: `STARKRAFT_ADMIN_TOKEN` 설정 시 `Authorization: Bearer <token>`을 constant-time 비교로 검사(불일치 401), 미설정 시 loopback 요청만 허용(그 외 403). 응답에는 resume token을 넣지 않는다.

### Handshake 단계

모든 handshake 거부는 websocket close frame으로 reason을 전달한다.

| 검사 | close code | reason |
|---|---|---|
| protocol version 불일치 | 1008 | `protocol mismatch: upgrade client` / `protocol mismatch: upgrade server` |
| `simVersion`이 비었거나 공백뿐 | 1008 | `invalid sim version` |
| 첫 메시지가 `handshake`가 아니거나 파싱 실패 | 1003 | `invalid handshake` |
| `clientName`이 빈 문자열 | 1008 | `empty client name` |
| `clientName` 32자 초과 또는 `^[A-Za-z0-9._:-]+$` 불일치 | 1008 | `invalid client name` |
| `resumeToken`이 없거나 만료됨 | 1008 | `invalid resume token` |
| room id 32자 초과 또는 같은 패턴 불일치 | 1008 | `invalid room id` |

- resume token은 1회용이다. 사용 시 기존 세션이 삭제되고 같은 `clientId`/이름/room으로 새 토큰이 발급된다.
- 토큰은 `crypto/rand` 128bit 값(`resume-<32 hex>`)이라 추측할 수 없다.
- 연결 중인 세션은 만료되지 않는다. 유효 기간 `STARKRAFT_RESUME_WINDOW`(기본 15초)는 연결 종료 시점부터 계산된다.
- handshake 이후 수신 envelope도 매번 protocol version과 `simVersion`을 검사하고, 위반 시 1008로 연결을 닫는다.

### Command ingress 단계 (수신 즉시, `server.go`)

batch 단위로 검사하며, 실패 시 `commandType="commandBatch"`, `requestId` 없는 ack 하나로 batch 전체를 거부한다. 이 ack들은 replay에 기록되지 않는다.

- batch 크기: 명령 64개 초과 → `batchTooLarge`
- 각 명령:
  - `commandType`이 비었거나 safe token 패턴 불일치 → `invalidPayload`
  - `requestId` 64자 초과 또는 패턴 불일치 → `invalidPayload`
  - `unitIds` 64개 초과 → `invalidPayload`
  - `x`/`y`가 NaN/Inf → `invalidPayload`
  - 명령별 필수 필드 누락 (`docs/protocol-spec.md` 표 참고) → `invalidPayload`
  - 지원하지 않는 command type → `unsupportedCommand`
- 클라이언트별 대기 batch 수: 8개(`MaxPendingBatchesPerClient`) 이상이면 → `queueFull`

### Room 적용 단계 (다음 tick, `room.go`)

명령 단위로 검사하며 ack는 room의 현재 tick으로 발행되고 replay `command` record로 기록된다.

- batch tick 허용 범위: room tick 기준 과거 2 ~ 미래 2 tick. 범위 밖이면 batch 내 모든 명령 → `invalidTick`
- 매치 종료 후 명령 → `matchEnded`
- rate limit: 클라이언트당 tick당 명령 16개 초과 → `rateLimit`
- 좌표 bounds: 32x32 맵 밖 (`move`, `build`) → `outOfBounds`
- unit 존재/소유: `missingUnits`(unitIds 비어 있음), `missingUnit`, `notOwner`
- `attack`: 자기 unit 대상 → `invalidTarget`, 거리 6 이내 공격자 없음 → `outOfRange`
- `build`: 클라이언트당 unit 64개 도달 → `unitCapReached`
- `queue`: 소유 unit 없음 → `missingBuilder`

미구현: building ownership, tech/build/train prerequisite 검증. 서버 room 모델에는 건물/테크 개념이 없고 `build`/`queue`는 unit을 즉시 생성한다. `unitType` 값은 safe token 등 내용 검증 없이 그대로 `typeId`가 된다. 비정상 handshake 반복에 대한 연결 수준 rate limit도 없다.

## Ack / 오류 보고

거부된 명령은 다음을 제공한다.

- `requestId` (room 단계 거부에서만; ingress batch 거부는 없음)
- `accepted=false`
- `reason`
- `tick`

현재 구현된 reason 전체:

- ingress: `batchTooLarge`, `invalidPayload`, `unsupportedCommand`, `queueFull`
- room: `invalidTick`, `matchEnded`, `rateLimit`, `outOfBounds`, `missingUnits`, `missingUnit`, `notOwner`, `invalidTarget`, `outOfRange`, `unitCapReached`, `missingBuilder`, `invalidPayload`, `unsupportedCommand`

`invalidSimVersion` ack reason은 없다. sim version 위반은 ack가 아니라 close reason `invalid sim version`으로 보고된다.

## 로그와 관측

운영 중 추적할 항목 (현재 이 분포를 집계하는 metrics endpoint는 없다. `commandAck`/close reason, replay `command` record, 서버 로그에서 수동 집계한다):

- handshake reject reason 분포
- command ack reject reason 분포
- rate limit hit 빈도
- invalid replay/hash 사례

## 운영 원칙

- 검증 규칙은 서버와 문서가 함께 진화해야 한다.
- 클라이언트 편의 로직은 보안 경계가 아니다.
- replay는 사후 분석 수단이며, 사전 방어 수단을 대체하지 않는다.

## 관련 코드

- `server/pkg/authoritative`
- `client/pkg/headless`
- `docs/protocol-spec.md`
- `docs/server-ops-runbook.md`
