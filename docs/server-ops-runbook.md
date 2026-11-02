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

- JDK/Gradle/Go 환경 준비 (Go 1.22, JDK는 `OPERATIONS.md` Prerequisites 참고)
- `./scripts/bootstrap_dev.sh` 실행 여부 확인
- protocol/sim version 정책 확인

### 실행

- 개발 실행:
  - `cd server && go run ./cmd/server`
- 로컬 통합 실행 (server + 선택적 bot + 대화형 CLI):
  - `./scripts/play_stack_local.sh`
- 봇 smoke (server + bot 2개 + replay 검증):
  - `./scripts/e2e_server_bots_smoke.sh`

서버 환경 변수 (`server/cmd/server/main.go`, 값은 trim 후 비어 있으면 기본값):

| 변수 | 기본값 | 설명 |
|---|---|---|
| `STARKRAFT_SERVER_ADDR` | `:8080` | listen 주소 |
| `STARKRAFT_SIM_VERSION` | `dev` | 서버 송신 envelope / header의 `simVersion` |
| `STARKRAFT_BUILD_HASH` | (없음) | 설정 시 envelope / replay header에 `buildHash` 포함 |
| `STARKRAFT_REPLAY_PATH` | (없음) | 설정 시 JSONL replay 기록 (서버 시작 시 덮어씀) |
| `STARKRAFT_KEYFRAME_EVERY` | `50` | keyframe 기록 간격(tick), 0 이하면 비활성 |
| `STARKRAFT_EMPTY_ROOM_TTL` | `30s` | 빈 room 제거까지 대기 (Go duration) |
| `STARKRAFT_RESUME_WINDOW` | `15s` | 연결 종료 후 resume token 유효 기간 (Go duration). 연결 중에는 만료되지 않는다 |
| `STARKRAFT_ADMIN_TOKEN` | (없음) | 설정 시 `/admin/stats`에 `Authorization: Bearer <token>` 필요. 미설정 시 loopback 요청만 허용 |

잘못된 int/duration 값은 경고 로그 후 기본값을 쓴다. tick 간격은 20ms 고정이다. 서버는 클라이언트 `simVersion`이 자기 값과 같은지는 검사하지 않는다 (비어 있지 않은지만 검사).

### 헬스체크

- `GET /healthz`: `status`, `protocolVersion`, `simVersion`, `rooms`, `resumeSessions`
- `GET /admin/stats`: `protocolVersion`, `simVersion`, `buildHash`, `tickIntervalMs`, room별 `id`/`tick`/`worldHash`/`units`/`connectedClients`/`pendingBatches`/`matchEnded`/`winnerId`/`emptyForMs`, `resumeSessions[]`(`clientId`/`clientName`/`roomId`/`connected`/연결 종료 후 `expiresInMs`; token은 포함하지 않음)
- 두 endpoint 모두 GET 이외는 405. `/admin/stats`는 `STARKRAFT_ADMIN_TOKEN` 미설정 시 loopback 외 요청에 403, 설정 시 bearer token 불일치에 401을 반환한다. `/healthz`는 인증 없이 열려 있다.
- smoke script가 서버 readiness(`/healthz`)를 통과하는지 확인

### 헬스체크

- `/healthz` 응답 확인
- smoke script가 서버 readiness를 통과하는지 확인

## 알려진 제약

- 서버 room은 Kotlin `sim`을 실행하지 않고 `room.go`의 자체 최소 Go 모델(32x32 맵, 600 tick 시간 제한)을 쓴다. 서버 `worldHash`는 sim hash와 비교할 수 없다.
- 새 room은 `player-1`/`player-2` 소유 Worker 2기로 시작하지만 `clientId`는 서버 전역 카운터로 발급된다. 서버 재시작 후 첫 두 클라이언트만 이 seed unit을 소유한다.
- 하나의 replay 파일에 모든 room이 기록되며 record에 room id가 없다.

## 장애 분류

### Handshake 실패

주요 원인:

- protocol mismatch (`protocol mismatch: upgrade client|server`)
- invalid room id
- empty / invalid client name
- invalid resume token (만료 또는 이미 사용됨)
- invalid sim version
- invalid handshake (첫 메시지가 handshake가 아님)

대응:

1. close reason 확인
2. 클라이언트 `protocolVersion` / `simVersion` 확인
3. room/resume token 값 확인

### 명령 거부 증가

주요 원인:

- ownership mismatch (`notOwner`)
- bounds violation (`outOfBounds`)
- unsupported command (`unsupportedCommand`)
- invalid payload (`invalidPayload`)
- 한도 초과 (`batchTooLarge`, `queueFull`, `rateLimit`)
- tick window 이탈 (`invalidTick`, ±2 tick)

전체 reason 목록은 `docs/security-validation-spec.md`를 본다.

대응:

1. `commandAck.reason` 집계
2. 최근 클라이언트 변경 확인
3. replay에 동일 요청이 기록되는지 확인 (ingress 단계 batch 거부는 replay에 기록되지 않음)

### replay 누락 또는 검증 실패

대응:

1. replay 파일 생성 여부 확인 (`STARKRAFT_REPLAY_PATH` 설정, 생성 실패 시 `replay disabled:` 로그)
2. `cd server && go run ./cmd/replaycheck --replay <file>`
3. `tools replay stats` / `verify-ndjson` 실행 (server JSONL에 `replay verify`는 쓰지 않는다)
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

현재 별도 metrics endpoint는 없다. 서버는 200 tick마다 `tick-metrics room=... tick=... p50us p95us p99us maxPending` 로그를 남긴다. 아래 항목은 로그 / ack / replay에서 수동 집계한다.

- handshake rejection reason 분포
- command ack rejection reason 분포
- replay file count / verify success rate
- 평균 매치 tick 수
- match end reason 분포

## 관련 문서

- `OPERATIONS.md`
- `server/README.md`
- `docs/protocol-spec.md`
- `docs/replay-format-spec.md`

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
