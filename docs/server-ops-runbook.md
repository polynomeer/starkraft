# Server Ops Runbook

## 목적

이 문서는 `server` 모듈 운영 관점의 최소 절차를 정리한다.  
실행 명령과 상세 트러블슈팅은 루트 `OPERATIONS.md`를 함께 본다.

## 운영 목표

- 서버가 room을 안정적으로 생성/종료할 것
- handshake 실패 원인이 명확히 보일 것
- replay가 누락 없이 저장될 것
- 기본 smoke 경로가 재현 가능할 것

## 기본 점검 항목

### 시작 전

- JDK/Gradle/Go 환경 준비
- `./scripts/bootstrap_dev.sh` 실행 여부 확인
- protocol/sim version 정책 확인

### 실행

- 개발 실행:
  - `cd server && go run ./cmd/server`
- 로컬 통합 실행:
  - `/Users/hammac/Projects/starkraft/scripts/play_stack_local.sh`
- 봇 smoke:
  - `/Users/hammac/Projects/starkraft/scripts/e2e_server_bots_smoke.sh`

### 헬스체크

- `/healthz` 응답 확인
- smoke script가 서버 readiness를 통과하는지 확인

## 장애 분류

### Handshake 실패

주요 원인:

- protocol mismatch
- invalid room id
- invalid client name
- invalid resume token
- invalid sim version

대응:

1. close reason 확인
2. 클라이언트 `protocolVersion` / `simVersion` 확인
3. room/resume token 값 확인

### 명령 거부 증가

주요 원인:

- ownership mismatch
- bounds violation
- unsupported command
- invalid payload

대응:

1. `commandAck.reason` 집계
2. 최근 클라이언트 변경 확인
3. replay에 동일 요청이 기록되는지 확인

### replay 누락 또는 검증 실패

대응:

1. replay 파일 생성 여부 확인
2. `tools replay stats` 실행
3. `tools replay verify` 또는 `verify-ndjson` 실행
4. protocol/sim version mismatch 여부 확인

## 운영 체크리스트

매치 기능 변경 후:

1. `go test ./...` in `server/`
2. `go test ./...` in `client/`
3. `./scripts/e2e_server_bots_smoke.sh`
4. replay verify 실행

릴리스 전:

1. CI green
2. smoke scripts green
3. replay artifact 확인
4. known issues 정리

## 관측 포인트

- handshake rejection reason 분포
- command ack rejection reason 분포
- replay file count / verify success rate
- 평균 매치 tick 수
- match end reason 분포

## 관련 문서

- `/Users/hammac/Projects/starkraft/OPERATIONS.md`
- `/Users/hammac/Projects/starkraft/server/README.md`
- `/Users/hammac/Projects/starkraft/docs/protocol-spec.md`
- `/Users/hammac/Projects/starkraft/docs/replay-format-spec.md`

## 관련 코드

- `server/cmd/server`
  - 서버 엔트리포인트와 환경 변수 파싱
- `server/pkg/authoritative`
  - handshake, room, replay, validation
- `client/cmd/client`
  - 수동 클라이언트 실행 경로
- `client/cmd/bot`
  - 봇 클라이언트 실행 경로
- `scripts/play_stack_local.sh`
  - 로컬 통합 플레이
- `scripts/e2e_server_bots_smoke.sh`
  - 통합 smoke
