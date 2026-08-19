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

### Handshake 단계

- protocol version 호환성
- non-empty sim version
- client name 유효성
- room id 유효성
- resume token 유효성

### Command ingress 단계

- 지원되는 command type인지 확인
- payload 구조 검증
- batch 크기 제한
- rate limit 확인

### Room/sim 적용 단계

- unit ownership 확인
- building ownership 확인
- 좌표/타겟 bounds 확인
- tech/build/train prerequisite 확인

## Ack / 오류 보고

거부된 명령은 최소한 다음을 제공해야 한다.

- request id
- accepted=false
- reason
- tick

핵심 이유 예시:

- `invalidPayload`
- `unsupportedCommand`
- `notOwner`
- `outOfBounds`
- `invalidSimVersion`

## 로그와 관측

운영 중 추적할 항목:

- handshake reject reason 분포
- command ack reject reason 분포
- rate limit hit 빈도
- invalid replay/hash 사례

## 운영 원칙

- 검증 규칙은 서버와 문서가 함께 진화해야 한다.
- 클라이언트 편의 로직은 보안 경계가 아니다.
- replay는 사후 분석 수단이며, 사전 방어 수단을 대체하지 않는다.

## 관련 코드

- `/Users/hammac/Projects/starkraft/server/pkg/authoritative`
- `/Users/hammac/Projects/starkraft/client/pkg/headless`
- `/Users/hammac/Projects/starkraft/docs/protocol-spec.md`
- `/Users/hammac/Projects/starkraft/docs/server-ops-runbook.md`
