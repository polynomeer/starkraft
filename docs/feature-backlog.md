# Feature Backlog

## 목적

남은 작업을 제품 우선순위 기준으로 정리한다.  
이 문서는 구현 상세보다 무엇을 먼저 끝낼지 판단하는 기준이다.

## 상태 규칙

- `done`
- `in-progress`
- `next`
- `later`

항목 제목의 상태는 전체 항목 기준이다. 하위 bullet 중 코드/커밋으로 확인된 것은 `(done: ...)`으로 표시한다.

## P0: 제품 플레이 가능성

### `in-progress` 그래픽 클라이언트 제품화

- renderer와 UI 상태 분리
- selection card / command card 정리
- minimap, camera, selection UX 강화
- 명령 실패 사유를 HUD에서 확인 가능하게 개선 (부분: command button 비활성 사유 표시, `ba3e837`)

### `next` 네트워크 플레이 경로 정리

- 서버 연결 클라이언트와 로컬 sim 클라이언트 경험 정렬
- replay 저장/재생 경로를 사용자 수준 명령으로 단순화
- room lifecycle과 reconnect 시나리오 검증 (done: `resumeToken` 재접속 창, idle 빈 room eviction, 만료/잘못된 토큰 테스트, room command validation matrix 테스트)

### `next` 경기 종료/결과 표시 UX

- 승리/패배/무승부 표시 (done: 그래픽 클라이언트가 match end 시 Victory/Defeat/Draw와 reason을 표시, sim snapshot에 `matchEndReason` 전파)
- 결과 요약 HUD
- replay 저장 위치와 검증 결과 표시

## P1: 게임 깊이

### `next` 밸런스 데이터 외부화 정리

- unit/building/tech 수치 검토
- 밸런스 변경 로그 체계
- 데이터 문서와 실제 리소스 동기화 (부분: [`balance-spec.md`](balance-spec.md)에 현재 데이터 수치 표 수록)
- supply, 유닛 시야 데이터 필드, 연구 수치 효과 (미구현)

### `next` AI/봇 강화

- 경제 우선순위 개선
- 전투 집결 타이밍 개선
- 연구/생산 선택 다양화

현재 봇 동작은 [`ai-behavior-spec.md`](ai-behavior-spec.md)의 "현재 구현" 참고.

### `later` 추가 승리 조건

- score/time mode
- objective control
- surrender flow UX 완성 (규칙/프로토콜은 done: sim `surrenderFaction`, server surrender 명령, protocol golden. 그래픽 클라이언트의 항복 UI는 미구현)

## P2: 운영/품질

### `next` soak/perf 기준 문서화와 자동화

- 30~120분 soak baseline (부분: nightly CI가 `:sim:soak`를 1분 실행)
- 메모리/GC 추세 기록 (done: soak harness perf/GC telemetry, `3606a45`)
- benchmark 결과 보관 규칙

### `next` 프로토콜/리플레이 장기 호환성 강화

- schema migration 정책
- replay version matrix (부분: sim replay compatibility matrix 테스트, `103c11d`)
- keyframe 도입 여부 판단 (done: server replay JSONL에 `keyframe` 레코드 도입, `replaycheck`/`tools replay verify-ndjson`이 keyframe world hash 검증)

### `later` 릴리스 산출물 표준화

- 서버 배포 산출물 (부분: `scripts/release_package.sh`가 server 바이너리 빌드)
- 클라이언트 배포 산출물 (부분: 같은 스크립트가 sim distZip과 Go client/bot 바이너리 빌드)
- 버전별 manifest / checksum 자동화 (부분: manifest 텍스트 생성, checksum은 미구현)

## P3: 콘텐츠/표현

### `later` 아트 방향 정리

- placeholder sprite/tileset 표준화 (부분: `data/visuals.json` sprite 매핑과 절차적 fallback 렌더링)
- faction readability 개선
- fog/combat feedback 개선

### `later` 오디오 방향 정리

- UI feedback sound (부분: command input audio 분리, `ee0f9b3`)
- combat feedback sound (부분: combat/death/collapse audio 분리, `4da6443`, `1450f43`, `ce6f5b4`)
- alert priority 체계 (부분: under-attack/completion alert, `4636c75`, `1f90924`)

## 완료된 기반 작업

`done` 항목:

- deterministic fixed-tick sim
- pathfinding, occupancy, replanning
- combat, fog, economy, production, research
- replay tooling과 golden hash
- authoritative server 기초 (주의: server는 Kotlin sim이 아닌 자체 Go 유닛 모델로 상태를 진행)
- headless client / bot / smoke scripts
- faction surrender 명령 (sim/server/protocol)
- server reconnect (`resumeToken`)와 idle room eviction
- server replay keyframe과 hash 검증

## 운영 규칙

- backlog 항목은 가능하면 하나의 검증 가능한 산출물 단위로 쪼갠다.
- `next` 항목은 테스트 또는 smoke 경로가 정의되어야 한다.
- `later` 항목은 즉시 구현하지 않더라도 문서상 의도는 유지한다.
