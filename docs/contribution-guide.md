# Contribution Guide

## 목적

저장소에 기능, 버그 수정, 문서 변경을 기여할 때 따라야 하는 기본 절차를 정리한다.

## 기본 원칙

- 작은 변경을 선호한다.
- 모듈 경계를 존중한다.
- 시뮬레이션 규칙은 `sim`에만 둔다.
- 새 로직에는 가능한 한 테스트를 추가한다.

## 모듈 책임

- `sim`
  - 결정론적 규칙과 상태 전이
- `server`
  - 권위 서버, 네트워크, room orchestration
- `client`
  - 입력, 표시, 연결
- `tools`
  - 오프라인 검증/제작 도구
- `shared-protocol`
  - schema와 golden 계약

## 작업 시작 전

1. 관련 문서를 읽는다.
2. 기존 테스트와 smoke 경로를 확인한다.
3. 변경이 어느 모듈 책임에 속하는지 먼저 판단한다.

## 변경 단위 규칙

- 기능 하나를 여러 단계로 나눌 수 있으면 나눈다.
- 큰 리팩터는 중간 passing state를 유지한다.
- 문서와 코드는 가능한 한 같은 변경 흐름에서 업데이트한다.

## 커밋 규칙

- Conventional Commits 사용
- subject는 짧고 명령형으로 쓴다
- 비사소한 변경은 body를 추가한다

예시:

- `feat(sim): add deterministic supply cap checks`
- `fix(server): reject invalid handshake payloads`
- `docs(docs): add replay validation checklist`

## 테스트 규칙

### 시뮬레이션 변경

- 관련 unit/regression test 추가
- 가능하면 determinism 경로 확인

### 서버/클라이언트 변경

- Go tests 추가
- handshake/validation 경로면 integration test 선호

### 도구 변경

- CLI 출력 계약 또는 golden 테스트 확인

## 문서 규칙

- 신규 기능은 최소한 관련 산출물 문서 한 곳을 갱신한다.
- 코드 경계가 변하면 `architecture-overview.md` 또는 관련 문서를 갱신한다.

## 추천 검증 명령

- `./gradlew :sim:test`
- `cd /Users/hammac/Projects/starkraft/server && go test ./...`
- `cd /Users/hammac/Projects/starkraft/client && go test ./...`
- `/Users/hammac/Projects/starkraft/scripts/smoke_run.sh`
- `/Users/hammac/Projects/starkraft/scripts/e2e_server_bots_smoke.sh`

## PR 작성 기준

- 무엇이 바뀌었는지
- 왜 필요한지
- 어떤 모듈에 영향이 있는지
- 어떻게 검증했는지

## 피해야 할 것

- `sim` 규칙을 `server`나 `client`에 복제
- 관련 없는 파일 수정
- 결정론 깨는 랜덤/시간 의존 로직 추가
- 테스트 없는 회귀 수정
