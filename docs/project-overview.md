# Project Overview

## 문서 목적

이 문서는 처음 저장소에 들어온 사람, 채용담당자, 협업을 시작하려는 엔지니어가 `starkraft`를 빠르게 이해하도록 돕는 종합 안내서다.  
상세 규칙과 세부 명세는 다른 문서에 분리되어 있지만, 이 문서 하나만 읽어도 프로젝트의 목표, 현재 완성도, 구조, 사용법, 기술적 포인트를 파악할 수 있게 구성한다.

## 프로젝트 요약

`starkraft`는 결정론적 RTS 시뮬레이션을 중심으로 한 멀티모듈 프로젝트다.  
게임 규칙 엔진, 권위 서버, 헤드리스 클라이언트, 그래픽 클라이언트, replay/map/data 도구, 그리고 이를 뒷받침하는 설계 문서가 한 저장소 안에 정리되어 있다.

한 줄로 요약하면:

> "RTS 게임플레이를 구현하면서도, 재현성과 운영 가능성을 먼저 생각한 시뮬레이션 스택"

## 왜 이 프로젝트가 흥미로운가

많은 개인 게임 프로젝트는 플레이 화면이나 기능 데모에 집중한다.  
`starkraft`는 반대로 아래 질문에 더 집중한다.

- 같은 입력이면 정말 같은 결과가 나오는가?
- 네트워크 클라이언트가 틀린 입력을 보내면 어디서 막는가?
- 플레이 결과를 나중에 다시 검증할 수 있는가?
- 규칙 엔진, 서버, 클라이언트, 도구의 경계가 명확한가?
- 사람이 직접 플레이하는 경로와 자동 검증 경로를 둘 다 유지할 수 있는가?

이 점 때문에 이 저장소는 단순한 게임 프로토타입이 아니라, 시뮬레이션 시스템 설계 역량을 보여주는 기술 포트폴리오로 읽힌다.

## 제품 비전

### 무엇을 만들고 있는가

- 자원 수집
- 건물 배치와 건설
- 유닛 생산
- 연구/기술 해금
- 전투와 시야
- 승리 조건 판정
- replay 및 검증

즉, RTS의 핵심 루프를 최소한의 형태로가 아니라 구조적으로 확장 가능한 방식으로 구현하고 있다.

### 누구를 위한 프로젝트인가

1차 사용자는 다음과 같다.

- deterministic simulation 구조를 연구하는 엔지니어
- authoritative multiplayer 설계를 실험하려는 개발자
- replay 기반 밸런싱과 검증 체계를 갖춘 RTS를 만들고 싶은 팀

2차 사용자는 다음과 같다.

- 로컬 또는 제한적 네트워크 환경에서 1v1 RTS를 플레이하고 싶은 사용자
- 게임 메커닉과 서버 구조를 함께 보고 싶은 채용담당자 및 면접관

## 현재 완성도

### 이미 가능한 것

- 시뮬레이션 단독 실행
- world hash 출력과 deterministic smoke
- snapshot 기반 그래픽 플레이
- authoritative server 기반 room 운영
- CLI 클라이언트와 봇 클라이언트 플레이
- replay 파일 기록과 검증
- 데이터/맵/도구 계약 검증

### 아직 남아 있는 것

- 제품 수준 UI polish
- 상용 아트/오디오 파이프라인
- 장시간 온라인 운영 경험 축적
- 밸런스/콘텐츠 레이어 확장

즉, 현재 상태는 "핵심 시스템이 살아 있고 검증 자산이 갖춰진 플레이어블 샌드박스"에 가깝다.

## 핵심 기능

### 시뮬레이션

- 고정 tick 기반 시간 전진
- ECS-lite 월드 구조
- 유닛 이동과 경로탐색
- 점유 및 재경로탐색
- 전투, 자동 타겟팅, 상위 명령 처리
- 자원 채집 및 drop-off
- 건물 배치, 건설 진행, 기능 활성화
- 생산 큐와 연구 처리
- 승리 판정과 종료 상태 기록

### 서버

- websocket handshake
- protocol version / sim version 검증
- room 생성/참가 흐름
- 명령 소유권/범위/빈도 검증
- snapshot broadcast
- replay persistence
- reconnect/resume token 지원

### 클라이언트

- 인터랙티브 CLI 플레이
- 스크립트 기반 명령 실행
- 간단한 봇 자동 플레이
- snapshot 기반 텍스트 HUD
- libGDX 기반 그래픽 렌더링

### 오프라인 도구

- replay meta/stats/verify/fast-forward
- map validate/generate
- data validate
- JSON output contract 기반 자동화 친화성

## 저장소를 읽는 가장 좋은 순서

### 채용담당자/리뷰어용 추천 순서

1. 루트 `README.md`
2. `project-overview.md`
3. `technical-deep-dive.md`
4. `testing-strategy.md`
5. `server-ops-runbook.md`

이 순서가 좋은 이유:

- 제품 관점과 기술 관점을 빠르게 모두 본다.
- 설계가 말뿐이 아니라 검증 루트까지 연결되는지 확인할 수 있다.

### 엔지니어용 추천 순서

1. `architecture-overview.md`
2. `simulation-architecture.md`
3. `protocol-spec.md`
4. `replay-format-spec.md`
5. `data-schema-spec.md`

### 기획/디자인용 추천 순서

1. `product-vision.md`
2. `game-design-document.md`
3. `ui-ux-spec.md`
4. `feature-backlog.md`

## 빠른 실행 가이드

### 1. 개발 환경 준비

```bash
./scripts/bootstrap_dev.sh
```

### 2. 가장 기본적인 상태 확인

```bash
./scripts/smoke_run.sh
```

### 3. 그래픽 플레이 확인

```bash
./scripts/play_sim_graphical.sh
```

### 4. 네트워크 스택 확인

```bash
./scripts/e2e_server_bots_smoke.sh
```

### 5. 직접 CLI로 플레이

```bash
./scripts/play_stack_local.sh
```

## 설계적으로 눈여겨볼 부분

### 1. `sim`을 단일 진실 공급원으로 고정

게임 규칙은 `sim`에만 있고, 서버는 이를 호출해 진행을 오케스트레이션한다.  
이 구조 덕분에 규칙 로직이 네트워크 계층에 흩어지지 않고, replay/benchmark/test가 같은 규칙 엔진을 기준으로 돌아간다.

### 2. protocol을 문서와 golden 파일로 동시에 관리

스키마만 두고 끝내지 않고, golden payload와 Kotlin/Go 양쪽 테스트까지 연결한다.  
이 방식은 "문서상 계약"과 "실제 구현"이 어긋나는 문제를 줄인다.

### 3. replay를 운영 자산으로 취급

replay는 단순한 관전 파일이 아니라 다음 용도로 쓰인다.

- 버그 재현
- world hash 검증
- 서버 결과 검산
- 성능/품질 회귀 탐지

### 4. 자동화와 수동 플레이 경로를 모두 보유

헤드리스 스모크만 있는 프로젝트는 실제 사용성 감각이 부족해지고,  
반대로 수동 플레이만 있는 프로젝트는 회귀를 잡기 어렵다.  
`starkraft`는 이 둘을 함께 유지한다.

## 이 프로젝트를 포트폴리오로 볼 때의 강점

### 문제 정의가 명확하다

- "RTS를 만든다"가 아니라
- "결정론, authoritative server, replay 검증을 갖춘 RTS 스택을 만든다"로 문제를 정의한다.

### 기술 선택의 이유가 보인다

- Kotlin/JVM은 시뮬레이션과 도구에 사용
- Go는 서버/클라이언트 네트워크 경로에 사용
- 문서와 테스트가 선택 이유를 뒷받침한다

### 운영 관점까지 포함한다

- health endpoint
- admin stats
- replay verification
- release artifact/checklist
- server ops runbook

즉, 코드만 있는 프로젝트가 아니라 "실제로 다루고 유지할 시스템"으로 설명된다.

## 다음에 보면 좋은 문서

- 제품 방향: `product-vision.md`
- 게임 규칙: `game-design-document.md`
- 모듈 구조: `architecture-overview.md`
- 기술 심화: `technical-deep-dive.md`
- 테스트 전략: `testing-strategy.md`
- 운영 문서: `server-ops-runbook.md`
