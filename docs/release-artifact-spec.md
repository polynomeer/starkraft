# Release Artifact Spec

## 목적

릴리스 시 어떤 산출물을 만들고, 각 산출물에 어떤 메타데이터와 검증 정보가 포함되어야 하는지 정의한다.

## 산출물 범위

### 서버 산출물

- 실행 바이너리 또는 실행 패키지
- 기본 설정 예시
- 버전 정보

### 클라이언트 산출물

- 헤드리스 CLI/봇 바이너리 또는 실행 패키지
- 그래픽 클라이언트 실행 패키지
- 기본 실행 가이드

### 시뮬레이션/도구 산출물

- `sim` 실행 패키지
- `tools` CLI 패키지
- replay/map/data 검증 도구

### 공통 산출물

- `CHANGELOG.md`
- `RELEASE_CHECKLIST.md`
- manifest
- checksum 목록

## 필수 메타데이터

각 릴리스는 최소한 다음을 기록해야 한다.

- release version
- protocol version
- sim version
- build date
- git commit 또는 build hash
- artifact size
- sha256 checksum

## manifest 요구사항

manifest는 다음을 포함해야 한다.

- artifact 파일명
- artifact 타입
- 크기
- checksum
- 생성 시각

프로젝트에는 이미 패키징 스크립트와 manifest 생성 흐름이 존재하므로, 이 문서는 그 출력 계약을 고정하는 역할을 한다.

## 릴리스 검증

릴리스 전에 다음을 확인한다.

1. 핵심 테스트 통과
2. smoke scripts 통과
3. replay verify 통과
4. server/client 실행 확인
5. manifest/checksum 생성 확인

## 배포 단위

### 개발 배포

- 내부 테스트용
- 빠른 확인이 목적
- 서명/압축 정책은 단순할 수 있음

### 검증 배포

- playtest/QA용
- replay와 로그 보존이 중요

### 제품 배포

- 버전 태그 고정
- changelog 포함
- checksum과 manifest 고정

## 관련 문서

- `/Users/hammac/Projects/starkraft/RELEASE_CHECKLIST.md`
- `/Users/hammac/Projects/starkraft/CHANGELOG.md`
- `/Users/hammac/Projects/starkraft/OPERATIONS.md`
- `/Users/hammac/Projects/starkraft/scripts/release_package.sh`
