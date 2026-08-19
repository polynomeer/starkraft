# Protocol Spec

## 목적

서버, 클라이언트, 도구 간 데이터 교환 형식을 정의한다.

프로토콜 목표:

- 명시적 버전 관리
- JSON 기반 디버깅 용이성
- replay/snapshot과의 계약 일관성

## 버전 원칙

- 모든 envelope는 `protocolVersion`을 가진다.
- `simVersion`은 시뮬레이션 구현 식별자다.
- `buildHash` 또는 유사 식별자는 빌드 산출물 추적에 사용한다.
- breaking change는 명시적으로 버전 정책을 갱신해야 한다.

기준 schema:

- `shared-protocol/schema/rts-protocol-v1.schema.json`

## 주요 메시지

### Handshake

클라이언트가 연결 직후 전송한다.

주요 필드:

- client name
- requested room
- protocol version
- sim version
- optional resume token

### HandshakeAck

서버가 handshake 수락 시 응답한다.

주요 필드:

- accepted protocol version
- assigned player/faction context
- resume token

### CommandBatch

클라이언트가 특정 tick 이후 실행될 명령 묶음을 전송한다.

예시 명령:

- move
- attack
- attackMove
- harvest
- build
- train
- research
- cancel*
- surrender

### CommandAck

서버가 명령 수락/거부를 알린다.

주요 필드:

- request id
- accepted
- reason
- tick

### Snapshot

서버가 클라이언트에 보내는 게임 상태 요약이다.

포함 항목:

- tick
- map metadata
- faction summaries
- visible entities/resources/buildings
- match state

### MatchEnd

매치 종료 시점의 결과 메시지다.

예시 reason:

- `elimination`
- `timeout`
- `draw`
- `surrender`

## 호환성 규칙

### 서버 측

- handshake 단계에서 protocol mismatch를 거부한다.
- close reason은 사람이 이해 가능한 텍스트를 제공한다.
- empty/blank `simVersion`은 거부한다.

### 클라이언트 측

- handshake ack의 protocol version을 검증한다.
- handshake 이후 stream envelope도 protocol mismatch를 검증한다.
- empty/blank `simVersion`은 전송 전에 거부한다.

## 검증 자산

- JSON schema
- golden files
- Kotlin round-trip tests
- Go round-trip tests

## 변경 절차

프로토콜을 바꿀 때는 다음을 함께 수정한다.

1. schema
2. golden files
3. Kotlin models/tests
4. Go models/tests
5. 호환성 문서
