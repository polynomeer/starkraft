# Document Maintenance Policy

## 목적

문서가 코드보다 뒤처지지 않도록 유지 규칙을 정한다.

## 기본 원칙

- 기능이 바뀌면 관련 문서도 함께 바뀌어야 한다.
- 문서가 구현을 추측해서는 안 된다.
- 설계 의도와 현재 구현 상태를 구분해 적는다.

## 변경 책임

### 시뮬레이션 변경

다음 문서 중 하나 이상을 검토한다.

- `simulation-architecture.md`
- `game-design-document.md`
- `balance-spec.md`
- `testing-strategy.md`

### 서버/프로토콜 변경

다음 문서 중 하나 이상을 검토한다.

- `protocol-spec.md`
- `security-validation-spec.md`
- `server-ops-runbook.md`
- `replay-format-spec.md`

### 클라이언트/UX 변경

다음 문서 중 하나 이상을 검토한다.

- `ui-ux-spec.md`
- `input-mapping-spec.md`
- `playtest-report-template.md`
- `art-direction-brief.md`

### 도구/데이터 변경

다음 문서 중 하나 이상을 검토한다.

- `data-schema-spec.md`
- `map-format-spec.md`
- `replay-validation-checklist.md`

## 문서 상태 기준

각 문서는 가능하면 다음 중 하나를 드러내야 한다.

- 현재 구현 기준
- 운영 기준
- 템플릿/체크리스트
- 장기 방향 초안

## PR/커밋 기준

- 기능 PR은 관련 문서 링크를 본문에 포함하는 것이 바람직하다.
- 큰 변경은 문서 전용 후속 커밋보다 같은 흐름에서 갱신하는 편이 좋다.

## 주기 점검

다음 시점에 문서 세트를 재검토한다.

- 릴리스 후보 생성 전
- 대형 리팩터 전후
- 프로토콜 버전 변경 시
- 그래픽 클라이언트 UX 단계 전환 시

## 관련 문서

- `/Users/hammac/Projects/starkraft/docs/README.md`
- `/Users/hammac/Projects/starkraft/docs/contribution-guide.md`
- `/Users/hammac/Projects/starkraft/README.md`
