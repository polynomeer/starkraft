# Desync Debugging Guide

## 목적

동일 replay 또는 동일 입력에서 world hash가 달라지는 문제를 조사하는 절차를 정리한다.

## 증상

대표적인 desync 징후:

- 같은 replay를 재실행했는데 final hash가 다름
- 서버/클라이언트 관측 상태가 장기적으로 벌어짐
- golden determinism test가 깨짐 (`starkraft.sim.DeterminismTest`, `ReplayHashTest`, `ReplayIOTest`)

주의: hash 비교는 같은 계열끼리만 의미가 있다. Go 서버(`server/pkg/authoritative/room.go`)는 Kotlin `sim`을 호출하지 않고 자체 최소 unit 모델과 자체 world hash를 쓴다. 서버 snapshot/keyframe `worldHash`와 `sim`의 world hash는 원래 다른 값이므로 둘의 차이는 desync가 아니다.

## 1차 확인

1. seed가 동일한가
2. protocol version이 동일한가
3. sim version이 동일한가
4. replay hash가 먼저 일치하는가
5. 최근 변경이 `sim` 규칙인지, protocol인지, replay writer인지 분류했는가

## 우선 조사 순서

### 1. replay 자체 검증

- sim replay: `replay meta`, `replay verify <file> --strictHash`, `replay stats`
- server replay: `cd server && go run ./cmd/replaycheck --replay <file>`, `replay verify-ndjson <file> --json` (첫 불일치 keyframe tick을 `firstMismatchTick`으로 보고)

### 2. world hash 비교

- baseline run과 suspect run의 final world hash 비교 (`./gradlew :sim:run --args="... --dumpWorldHash"`)
- partial replay로 중간 tick까지 잘라 비교 (`replay fast-forward <file> --ticks N --json`)
- 주의: `tools replay verify`/`fast-forward`가 출력하는 world hash는 축소 재실행 하네스(20x20 맵, 이동 계열 시스템만) 기준이라 `:sim:run`의 hash와 같지 않다. 같은 도구끼리만 비교한다.

### 3. snapshot 비교

- 일정 간격 snapshot을 출력해 divergence 시작 tick을 좁힌다 (`:sim:run --args="... --snapshotEvery N --snapshotOut f.ndjson"`)

## 자주 발생하는 원인

### iteration order 불안정

- `HashMap`/`Set` 순회에 결과가 의존
- 정렬되지 않은 target selection

### hidden mutable state

- reset되지 않는 캐시
- session/state 누수

### float 계산 차이

- 경계 조건에서 rounding 차이
- 이동/거리 계산이 tick 순서에 민감

### replay 적용 순서 문제

- 같은 tick 안의 command order drift
- ack/command writer 불일치

## 조사 절차

1. 문제 replay를 복사한다.
2. strict verify를 실행한다.
3. 필요하면 partial replay로 divergence tick을 좁힌다.
4. 해당 tick 전후의 entity/order/queue 상태를 비교한다.
5. 최근 commit 범위를 좁혀 regression point를 찾는다.

## 권장 산출물

조사 후 남겨야 할 정보:

- 재현 명령
- 재현 seed
- 문제 replay 위치
- expected vs actual hash
- divergence 시작 tick
- 원인 commit

## 관련 문서

- `docs/replay-validation-checklist.md`
- `docs/testing-strategy.md`
- `docs/simulation-architecture.md`
