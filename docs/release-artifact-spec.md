# Release Artifact Spec

## 목적

릴리스 시 어떤 산출물을 만들고, 각 산출물에 어떤 메타데이터와 검증 정보가 포함되어야 하는지 정의한다.

## 현재 구현 (`scripts/release_package.sh`)

```bash
./scripts/release_package.sh [OUT_DIR] [VERSION]   # 기본값: dist/release, dev
```

생성물 (`OUT_DIR` 아래):

- `starkraft-sim-<VERSION>.zip` (`./gradlew :sim:distZip`의 `sim-1.0-SNAPSHOT.zip` 복사)
- `starkraft-server-<VERSION>` (`server/cmd/server`)
- `starkraft-client-cli-<VERSION>` (`client/cmd/client`)
- `starkraft-client-bot-<VERSION>` (`client/cmd/bot`)
- `manifest-<VERSION>.txt`: `version`, `protocolVersion=1`, `simVersion=1.0-SNAPSHOT`, `buildDateUtc`

참고:

- `go`가 없으면 Go 바이너리 3개는 건너뛰고 경고 없이 계속 진행한다.
- Go 바이너리는 현재 호스트 OS/arch로만 빌드된다.
- manifest의 `protocolVersion`/`simVersion`은 스크립트에 하드코딩되어 있다.
- 마지막에 `ls -lh`로 파일 크기를 출력할 뿐 manifest에는 크기/checksum이 기록되지 않는다.

## 산출물 범위

아래는 목표 범위다. 현재 스크립트가 만들지 않는 항목은 (미구현)으로 표시한다.

### 서버 산출물

- 실행 바이너리 또는 실행 패키지
- 기본 설정 예시 (미구현)
- 버전 정보

### 클라이언트 산출물

- 헤드리스 CLI/봇 바이너리 또는 실행 패키지
- 그래픽 클라이언트 실행 패키지 (미구현: libGDX 클라이언트 클래스는 `sim` zip classpath에 들어 있지만 zip의 실행 스크립트는 `starkraft.sim.AppKt`만 띄운다)
- 기본 실행 가이드 (미구현)

### 시뮬레이션/도구 산출물

- `sim` 실행 패키지
- `tools` CLI 패키지 (미구현: 스크립트가 tools를 패키징하지 않음)
- replay/map/data 검증 도구 (`tools`에 포함, 동일하게 미패키징)

### 공통 산출물

- `CHANGELOG.md` (미구현: 산출물 디렉터리에 복사하지 않음)
- `RELEASE_CHECKLIST.md` (미구현: 동일)
- manifest
- checksum 목록 (미구현)

## 필수 메타데이터

각 릴리스는 최소한 다음을 기록해야 한다.

- release version (현재 manifest `version`)
- protocol version (현재 manifest `protocolVersion`)
- sim version (현재 manifest `simVersion`)
- build date (현재 manifest `buildDateUtc`)
- git commit 또는 build hash (미구현)
- artifact size (미구현)
- sha256 checksum (미구현)

## manifest 요구사항

manifest는 다음을 포함해야 한다 (목표). 현재 manifest는 `key=value` 텍스트이며 release 단위 정보 4개만 담고 artifact별 항목은 없다.

- artifact 파일명 (미구현)
- artifact 타입 (미구현)
- 크기 (미구현)
- checksum (미구현)
- 생성 시각 (release 단위 `buildDateUtc`만 존재)

패키징 스크립트와 manifest 생성 흐름은 존재하지만 위 목표 계약을 아직 다 충족하지 않는다. 계약을 확장할 때 스크립트와 이 문서를 함께 갱신한다.

## 릴리스 검증

릴리스 전에 다음을 확인한다.

1. 핵심 테스트 통과
2. smoke scripts 통과
3. replay verify 통과
4. server/client 실행 확인
5. manifest 생성 확인 (checksum은 현재 수동 생성 필요)

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

- `RELEASE_CHECKLIST.md`
- `CHANGELOG.md`
- `OPERATIONS.md`
- `scripts/release_package.sh`
