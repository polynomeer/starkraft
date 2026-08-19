# Replay Format Spec

## 목적

replay는 경기 재현, 결정론 검증, 회귀 분석의 기준 산출물이다.

## 요구사항

- 재실행 가능해야 한다.
- hash 검증 가능해야 한다.
- 사람이 읽을 수 있는 디버그 경로를 제공해야 한다.
- 장기적으로 버전 구분이 가능해야 한다.

## 현재 형식

프로젝트는 JSON/NDJSON 기반 replay 흐름을 사용한다.

대표 요소:

- header
- command events
- optional metadata
- hash/checksum

## 최소 포함 정보

### Header

- replay schema version
- protocol version
- sim version
- seed
- map id
- start tick

### Command Stream

각 command record는 최소한 다음을 포함해야 한다.

- tick
- request id
- command type
- issuing player/faction context
- command payload

### Validation Data

- stored replay hash
- optional world hash
- optional final tick summary

## 검증 방식

### Replay Hash

- 저장 시 command stream 기준 hash를 기록한다.
- 로드 시 hash를 다시 계산해 비교할 수 있어야 한다.

### Sim Rerun

- replay를 `sim`에 재주입하여 최종 world hash를 비교한다.
- 이 경로는 desync 탐지의 핵심이다.

## 레거시 처리

- 구버전 schema 또는 단순 array 형식은 backward-compat 로더로 처리한다.
- strict 모드에서는 hash 누락 replay를 거부할 수 있어야 한다.

## 도구 지원

`tools` 모듈은 다음 기능을 제공해야 한다.

- replay metadata 출력
- stats 계산
- verify
- verify-ndjson
- fast-forward / inspection

## 운영 원칙

- 서버 authoritative 매치는 서버가 replay를 기록한다.
- 수동 테스트 replay도 seed와 version을 기록한다.
- golden replay는 CI에서 재검증 가능한 위치에 유지한다.
