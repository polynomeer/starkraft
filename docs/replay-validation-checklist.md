# Replay Validation Checklist

## 목적

replay 산출물이 신뢰 가능한지 빠르게 점검하기 위한 운영용 체크리스트다.

## 언제 사용하나

- 시뮬레이션 규칙 변경 직후
- 프로토콜 변경 직후
- 서버 replay writer 변경 직후
- 릴리스 후보 검증 시
- desync 의심 사례 조사 시

## 기본 체크

### 1. 파일 생성 여부

- replay 파일이 예상 위치에 생성되었는가
- 파일 크기가 0이 아닌가

### 2. 메타데이터 확인

- sim replay (JSON): `schema`가 1인가, `seed`/`mapId`/`buildVersion`이 기록되었는가 (`replay meta`)
- server replay (JSONL): 첫 줄이 `header` record이고 `protocolVersion`/`simVersion`/`tickMs`가 있는가 (server replay에는 seed 필드가 없다)

### 3. hash 확인

- sim replay: `replayHash`가 저장되었는가, `--strictHash`에서 재계산 hash와 일치하는가
- server replay: keyframe `worldHash`가 재계산 값과 일치하는가 (`replaycheck`, `verify-ndjson`)
- 두 형식의 hash는 서로 비교할 수 없다. 서버 hash는 `room.go`의 자체 Go 모델 기준이다.

## 권장 검증 순서

sim replay (JSON):

1. `replay meta`
2. `replay stats`
3. `replay verify --strictHash`
4. golden 또는 기대 world hash와 비교

server replay (JSONL):

1. `go run ./cmd/replaycheck --replay <file>` (in `server/`)
2. `replay stats` (server-ndjson로 자동 판별)
3. `replay verify-ndjson`

`replay meta` / `verify` / `fast-forward`는 sim replay 전용이며 server JSONL에는 사용할 수 없다.

## 실패 유형

### hash mismatch

가능 원인:

- 명령 순서 불일치
- protocol/schema drift
- 시뮬레이션 로직 회귀

### missing hash

가능 원인:

- legacy replay
- 저장 경로 미완성
- writer 회귀

### replay loads but world hash diverges

가능 원인:

- deterministic ordering 문제
- seed 적용 문제
- hidden mutable state

## 조사 절차

1. 관련 commit 범위를 좁힌다.
2. 동일 seed/명령으로 재실행한다.
3. replay stats 차이를 확인한다.
4. 필요 시 snapshot 출력과 비교한다.
5. golden hash 또는 최근 정상 replay와 비교한다.

## 릴리스 전 최소 기준

- 대표 replay 샘플에 대해 verify 통과
- authoritative stack smoke replay verify 통과 (`e2e_server_bots_smoke.sh` 내 `replaycheck`, CI `e2e-smoke`의 `verify-ndjson`)
- golden replay hash 회귀 없음

## 관련 명령

- `./gradlew :tools:run --args="replay meta <file>"`
- `./gradlew :tools:run --args="replay stats <file> --json"`
- `./gradlew :tools:run --args="replay verify <file> --strictHash"`
- `./gradlew :tools:run --args="replay verify-ndjson <file> --json"`
- `cd server && go run ./cmd/replaycheck --replay <file>`

결과 코드: `verify`의 `result`는 `ok` / `ok-legacy`(hash 없음, non-strict) / `missing-hash`(strict) / `mismatch` / `validation-error`, `verify-ndjson`/`stats`(server)는 `ok` / `mismatch` / `invalid`. 실패 시 exit code 2.

## 관련 문서

- `docs/replay-format-spec.md`
- `docs/testing-strategy.md`
- `tools/README.md`
