# Protocol Spec

## 목적

서버, 클라이언트, 도구 간 데이터 교환 형식을 정의한다.

프로토콜 목표:

- 명시적 버전 관리
- JSON 기반 디버깅 용이성
- replay/snapshot과의 계약 일관성

## 버전 원칙

- 모든 envelope는 `protocolVersion`을 가진다. 현재 값은 `1`이다 (`CurrentProtocolVersion`, schema에서 `const: 1`).
- `simVersion`은 시뮬레이션 구현 식별자다. schema상 `minLength: 1`이며 서버/클라이언트 모두 공백만 있는 값도 거부한다.
- `buildHash`는 선택 필드이며 빌드 산출물 추적에 사용한다. 서버는 `STARKRAFT_BUILD_HASH`가 설정된 경우에만 송신 envelope에 넣는다.
- breaking change는 명시적으로 버전 정책을 갱신해야 한다.

버전 호환성 판정 (`Compatibility(local, remote)`, Go/Kotlin 동일):

- `remote == local` → compatible
- `remote > local` → upgrade client
- `remote < local` → upgrade server

현재 구현은 정확히 같은 버전만 허용한다. 하위 호환 범위 협상은 없다.

기준 schema:

- `shared-protocol/schema/rts-protocol-v1.schema.json`

## Envelope

```json
{"protocolVersion":1,"simVersion":"1.0.0","buildHash":"abc123","message":{"type":"...", ...}}
```

- `message.type`으로 메시지 종류를 구분한다.
- 전송은 websocket(`GET /ws`) 위의 JSON 텍스트 프레임이다.

## 주요 메시지

### Handshake (`handshake`)

클라이언트가 연결 직후 첫 메시지로 전송한다.

필드:

- `clientName` (필수)
- `requestedRoom` (선택, 생략/빈 값이면 `default`)
- `resumeToken` (선택, 재접속 시)
- protocol version / sim version은 envelope 필드로 전달된다.

### HandshakeAck (`handshakeAck`)

서버가 handshake 수락 시 응답한다. 직후 서버는 현재 room 상태의 `snapshot`을 1회 즉시 보낸다.

필드:

- `roomId`
- `clientId` (`player-N` 형식, 서버 전역 카운터로 발급)
- `serverTickMs` (현재 20)
- `protocolVersion`
- `resumeToken` (`resume-<32 hex>`, 128bit 무작위 값)

faction/진영 정보는 별도로 전달하지 않는다. 소유권은 `clientId`와 snapshot unit의 `ownerId`로 판단한다.

재접속(resume): handshake에 이전 ack의 `resumeToken`을 넣으면 같은 `clientId`/이름/room으로 복귀한다 (`requestedRoom`은 무시). 토큰은 1회용이며 매 handshake마다 새 토큰이 발급된다. 유효 기간은 연결 종료 후 `STARKRAFT_RESUME_WINDOW`(기본 15초)이며, 연결 중에는 만료되지 않는다. 무효/만료 토큰은 close reason `invalid resume token`으로 거부된다.

### CommandBatch (`commandBatch`)

클라이언트가 특정 tick에 적용될 명령 묶음을 전송한다.

필드: `tick`, `commands[]`. 각 command(`WireCommand`)는 다음 필드를 가진다.

- `commandType` (필수)
- `requestId` (선택)
- `unitIds`, `x`, `y`, `targetUnitId`, `unitType` (명령별 선택)

현재 와이어 프로토콜이 지원하는 명령 (이외는 `unsupportedCommand`):

| commandType | 필수 필드 | 비고 |
|---|---|---|
| `move` | `unitIds`(1개 이상), `x`, `y` | |
| `attack` | `unitIds`(1개 이상), `targetUnitId` | |
| `build` | `x`, `y` | `unitType` 선택 (기본 `Worker`) |
| `queue` | 없음 | `unitType` 선택 (기본 `Soldier`) |
| `surrender` | 없음 | 다른 payload 필드가 있으면 `invalidPayload` |

참고: `attackMove`, `harvest`, `train`, `research`, `cancel*` 등은 `sim`의 내부 명령/replay 이벤트에는 있지만 와이어 프로토콜 v1에는 없다 (미구현).

### CommandAck (`commandAck`)

서버가 명령 수락/거부를 알린다.

필드:

- `tick`
- `requestId` (선택)
- `commandType` (batch 단위 거부 시 `commandBatch`)
- `accepted`
- `reason` (선택)

reason 값과 발생 위치는 `docs/security-validation-spec.md`를 본다.

### Snapshot (`snapshot`)

서버가 매 tick room의 모든 클라이언트에 보내는 상태 요약이다.

필드:

- `tick`
- `worldHash` (서버 room 모델 기준 hash)
- `units[]`: `id`, `ownerId`, `typeId`, `x`, `y`, `hp`
- `matchEnded` (선택)
- `winnerId` (선택)

현재 snapshot은 시야(fog) 필터 없이 room의 전체 unit을 담는다. map metadata, faction 요약, 자원/건물 정보는 포함되지 않는다 (미구현). schema는 snapshot에 대해서만 `additionalProperties: true`다.

주의: 서버 room은 Kotlin `sim`을 호출하지 않고 `server/pkg/authoritative/room.go`의 자체 최소 Go unit 모델(32x32 맵, move/attack/build/queue/surrender)과 자체 world hash를 사용한다. 따라서 snapshot `worldHash`는 `sim`의 world hash와 비교할 수 없다.

### MatchEnd (`matchEnd`)

매치 종료 시 room의 각 클라이언트에 1회 전송된다.

필드: `tick`, `winnerId` (선택). 종료 reason 필드는 없다.

서버 room의 종료 조건:

- 항복(`surrender`): 남은 소유자 중 사전순 첫 ID가 winner
- 소멸: 살아 있는 unit 소유자가 1명 이하
- 시간 초과: tick 600 도달 (winner 없음)

`elimination`/`timeout`/`draw`/`surrender` reason 값은 `sim`의 `MatchEndReason`(sim replay의 `matchEndReason`)에만 존재한다.

## 호환성 규칙

### 서버 측

- handshake 단계에서 protocol mismatch를 거부한다 (close code 1008, reason `protocol mismatch: upgrade client` / `protocol mismatch: upgrade server`).
- handshake 이후 수신 envelope도 매번 protocol version과 `simVersion`을 검사하고, 위반 시 같은 방식으로 연결을 닫는다.
- close reason은 사람이 이해 가능한 텍스트를 제공한다.
- empty/blank `simVersion`은 거부한다 (close reason `invalid sim version`).
- handshake 이후 `commandBatch` 이외의 메시지 type은 무시한다. JSON 파싱 실패 시 close reason 없이 연결을 닫는다.

### 클라이언트 측 (`client/pkg/headless`)

- handshake 응답 envelope와 handshake ack의 `protocolVersion`을 모두 검증한다.
- handshake 응답은 3초 안에 와야 하며 type이 `handshakeAck`가 아니면 실패한다.
- handshake 이후 stream envelope도 protocol mismatch를 검증한다.
- empty/blank `simVersion`과 `name`은 전송 전에 거부한다. `room`/`resumeToken`은 trim 후 빈 값이면 생략한다.
- 알 수 없는 message type을 받으면 오류로 처리한다.

## 검증 자산

- JSON schema
- golden files (`shared-protocol/golden/v1-*.json`)
- Kotlin golden tests (`sim/src/test/kotlin/starkraft/sim/protocol/ProtocolModelsTest.kt`)
- Go golden tests (`server/pkg/protocol/models_test.go`, `client/pkg/protocol/golden_test.go`): 마샬 결과를 golden과 byte 단위로 비교한다.

## 변경 절차

프로토콜 모델은 세 곳에 중복되어 있다: `server/pkg/protocol`, `client/pkg/protocol`, `sim/src/main/kotlin/starkraft/sim/protocol/ProtocolModels.kt`. 프로토콜을 바꿀 때는 다음을 함께 수정한다.

1. schema
2. golden files
3. Kotlin models/tests
4. Go server models/tests
5. Go client models/tests
6. 호환성 문서

참고: Kotlin 모델은 Go와 형태가 다르다. `handshakeAck` 타입이 없고, `WireCommand`/`Snapshot`의 상세 필드를 typed field 대신 `payload: JsonElement`로 둔다.

## 관련 코드

- `shared-protocol/schema/rts-protocol-v1.schema.json`
  - 프로토콜 schema 기준점
- `shared-protocol/golden`
  - golden envelope 예시
- `sim/src/main/kotlin/starkraft/sim/protocol`
  - Kotlin protocol models
- `server/pkg/protocol`
  - Go server protocol models/tests
- `client/pkg/protocol`
  - Go client protocol models/tests
- `server/pkg/authoritative/server.go`
  - handshake/validation enforcement
- `server/pkg/authoritative/room.go`
  - room 상태 모델, 명령 적용, 종료 판정
