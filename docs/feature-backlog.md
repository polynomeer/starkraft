# Feature Backlog

## 목적

남은 작업을 제품 우선순위 기준으로 정리한다.  
이 문서는 구현 상세보다 무엇을 먼저 끝낼지 판단하는 기준이다.

## 상태 규칙

- `done`
- `in-progress`
- `next`
- `later`

## P0: 제품 플레이 가능성

### `in-progress` 그래픽 클라이언트 제품화

- renderer와 UI 상태 분리
- selection card / command card 정리
- minimap, camera, selection UX 강화
- 명령 실패 사유를 HUD에서 확인 가능하게 개선

### `next` 네트워크 플레이 경로 정리

- 서버 연결 클라이언트와 로컬 sim 클라이언트 경험 정렬
- replay 저장/재생 경로를 사용자 수준 명령으로 단순화
- room lifecycle과 reconnect 시나리오 검증

### `next` 경기 종료/결과 표시 UX

- 승리/패배/무승부 표시
- 결과 요약 HUD
- replay 저장 위치와 검증 결과 표시

## P1: 게임 깊이

### `next` 밸런스 데이터 외부화 정리

- unit/building/tech 수치 검토
- 밸런스 변경 로그 체계
- 데이터 문서와 실제 리소스 동기화

### `next` AI/봇 강화

- 경제 우선순위 개선
- 전투 집결 타이밍 개선
- 연구/생산 선택 다양화

### `later` 추가 승리 조건

- score/time mode
- objective control
- surrender flow UX 완성

## P2: 운영/품질

### `next` soak/perf 기준 문서화와 자동화

- 30~120분 soak baseline
- 메모리/GC 추세 기록
- benchmark 결과 보관 규칙

### `next` 프로토콜/리플레이 장기 호환성 강화

- schema migration 정책
- replay version matrix
- keyframe 도입 여부 판단

### `later` 릴리스 산출물 표준화

- 서버 배포 산출물
- 클라이언트 배포 산출물
- 버전별 manifest / checksum 자동화

## P3: 콘텐츠/표현

### `later` 아트 방향 정리

- placeholder sprite/tileset 표준화
- faction readability 개선
- fog/combat feedback 개선

### `later` 오디오 방향 정리

- UI feedback sound
- combat feedback sound
- alert priority 체계

## 완료된 기반 작업

`done` 항목:

- deterministic fixed-tick sim
- pathfinding, occupancy, replanning
- combat, fog, economy, production, research
- replay tooling과 golden hash
- authoritative server 기초
- headless client / bot / smoke scripts

## 운영 규칙

- backlog 항목은 가능하면 하나의 검증 가능한 산출물 단위로 쪼갠다.
- `next` 항목은 테스트 또는 smoke 경로가 정의되어야 한다.
- `later` 항목은 즉시 구현하지 않더라도 문서상 의도는 유지한다.
