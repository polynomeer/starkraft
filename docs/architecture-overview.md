# Architecture Overview

## 목적

`starkraft`는 결정론적 RTS 시뮬레이션을 중심으로, 권위 서버, 클라이언트, 리플레이/맵 도구를 분리한 멀티모듈 프로젝트다.

핵심 원칙:

- `sim`은 단일 진실 공급원이다.
- 서버는 권위적(authoritative) 오케스트레이션을 담당한다.
- 클라이언트는 입력과 시각화 계층이다.
- 도구는 오프라인 검증과 제작 지원 계층이다.

## 모듈 책임

### `sim/`

- 고정 tick RTS 규칙 실행
- 유닛/건물/자원/전투/시야/생산/연구 처리
- 결정론 해시, 리플레이 재실행, 헤드리스 실행 지원
- snapshot 및 입력 파일 기반 로컬 플레이 지원

### `server/`

- 웹소켓 접속과 handshake 처리
- room lifecycle과 고정 tick 매치 실행
- 명령 검증, 소유권 검사, 기본 rate limiting
- snapshot broadcast와 replay 기록

### `client/`

- 서버 접속용 헤드리스/CLI/봇 클라이언트
- snapshot 수신, 명령 전송, handshake/resume 처리
- 로컬 연동용 플레이어/봇 워크플로 지원

### `shared-protocol/`

- 프로토콜 JSON schema
- golden 파일
- 버전 호환 규칙 기준점

### `tools/`

- replay 분석/검증 CLI
- map generate/validate CLI
- data validate CLI

### `scripts/`

- smoke test
- 로컬 플레이 런처
- 패키징/부트스트랩 보조

## 상위 데이터 흐름

### 온라인 매치

1. 클라이언트가 서버에 handshake를 보낸다.
2. 서버가 room에 플레이어를 연결한다.
3. 클라이언트 명령은 서버에서 검증된 뒤 tick 큐에 적재된다.
4. 서버는 `sim` 상태를 tick 단위로 전진시킨다.
5. 서버는 snapshot/ack/replay 기록을 생성한다.
6. 클라이언트는 snapshot을 소비해 UI를 갱신한다.

### 로컬 sim 플레이

1. `sim:run`이 snapshot NDJSON을 기록한다.
2. 그래픽 클라이언트가 snapshot 스트림을 구독한다.
3. 입력은 별도 NDJSON으로 기록된다.
4. `sim`은 입력 파일을 읽어 다음 tick 명령으로 반영한다.

## 아키텍처 경계

### `sim` 안에 있어야 하는 것

- 이동
- 전투
- 경제
- 생산
- 연구
- 승리 판정
- 결정론 해시

### `sim` 밖에 있어야 하는 것

- 소켓 IO
- 파일 보관 정책
- 계정/방 관리
- 렌더링
- UI 상태

## 현재 상태

- `sim`은 플레이 가능한 RTS 샌드박스 수준으로 성숙해 있다.
- `server`와 `client`는 기본적인 권위 서버 스택을 제공한다.
- 그래픽 클라이언트는 디버그/프로토타입 수준이며, 제품 수준 UI/렌더링 구조화가 다음 개선 축이다.

## 다음 아키텍처 우선순위

1. 그래픽 클라이언트의 view model / renderer 분리
2. 네트워크 클라이언트와 그래픽 클라이언트 통합 경로 정리
3. 리플레이/스냅샷 계약을 장기 호환 포맷으로 고정
4. 장시간 세션 기준 성능/메모리 하드닝
