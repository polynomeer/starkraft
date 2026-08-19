# Milestone Plan

## 목적

`starkraft`를 플레이 가능한 RTS 제품 스택으로 마무리하기 위한 단계별 마일스톤을 정의한다.

## 마일스톤 정의

### M1. Deterministic Sandbox

목표:

- `sim`이 단독으로 게임 규칙을 안정적으로 실행
- replay/hash/benchmark가 신뢰 가능

완료 기준:

- headless 경기 실행 가능
- golden determinism test green
- replay verify 경로 동작

상태:

- `done`

### M2. Authoritative Stack

목표:

- `server` + `client` + `sim`으로 권위 서버 플레이 루프 구성

완료 기준:

- handshake, room join, command ack, snapshot broadcast 동작
- e2e smoke에서 replay 생성

상태:

- `mostly-done`

남은 핵심:

- reconnect/room UX 정리
- 운영 관점 observability 보강

### M3. Playable Client

목표:

- 그래픽 클라이언트가 디버그 뷰어가 아니라 실제 플레이 가능한 UX 제공

완료 기준:

- camera, selection, command issuing, HUD가 실사용 가능
- 생산/연구/건설 상태를 HUD에서 읽을 수 있음
- match end/result를 UI에서 확인 가능

상태:

- `in-progress`

### M4. Stable 1v1 Product

목표:

- 로컬/서버 기반 1v1 경기를 반복 플레이 가능한 수준까지 안정화

완료 기준:

- soak/perf 기준 통과
- replay 검증 자동화
- packaging/release 문서 정리

상태:

- `next`

### M5. Presentation Upgrade

목표:

- 시각/오디오/피드백을 제품 수준으로 끌어올림

완료 기준:

- placeholder를 넘어선 읽기 쉬운 전장 표현
- faction, fog, combat, alerts의 시각적 계층 정리
- 기본 오디오 피드백 도입

상태:

- `later`

## 추천 진행 순서

1. M3 완료
2. M4 안정화
3. M5 표현 강화

이 순서를 권장하는 이유:

- 현재 가장 큰 병목은 그래픽 클라이언트 UX다.
- sim/server의 기반은 이미 상당 부분 갖춰져 있다.
- 표현 강화는 입력/상태 구조가 정리된 후가 더 비용 효율적이다.

## 검증 자산 매핑

### M1

- `:sim:test`
- replay verify
- benchmark

### M2

- `server` / `client` Go tests
- e2e smoke scripts

### M3

- sim graphical smoke
- 수동 플레이 체크리스트
- HUD/command regression tests

### M4

- soak scripts
- packaging smoke
- operations checklist

### M5

- visual regression baseline
- asset loading tests
- performance spot checks
