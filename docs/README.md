# Starkraft Docs

이 디렉터리는 `starkraft`의 제품/기술 산출물을 정리하는 기본 문서 집합이다.

## 문서 목록

- `product-vision.md`
  - 제품 목표, 비목표, 핵심 가치, 성공 지표
- `feature-backlog.md`
  - 남은 작업의 우선순위와 상태
- `milestone-plan.md`
  - 단계별 완성 기준과 검증 자산 매핑
- `balance-spec.md`
  - 수치 밸런스 관리 원칙과 변경 절차
- `data-schema-spec.md`
  - 유닛/건물/기술/시각 데이터 구조와 검증 원칙
- `map-format-spec.md`
  - 맵 데이터 구조와 유효성 규칙
- `architecture-overview.md`
  - 저장소 모듈 책임과 상위 레벨 데이터 흐름
- `simulation-architecture.md`
  - `sim` 고정 tick, 시스템 순서, 결정론 규칙
- `protocol-spec.md`
  - 서버/클라이언트/도구 간 메시지 계약과 버전 정책
- `replay-format-spec.md`
  - replay 파일 구조, 검증 규칙, 활용 흐름
- `game-design-document.md`
  - 게임 규칙, 핵심 루프, 유닛/건물/자원/승리 조건
- `ui-ux-spec.md`
  - 그래픽 클라이언트의 HUD/패널/피드백 기준
- `input-mapping-spec.md`
  - 입력 체계와 단축키 원칙
- `ai-behavior-spec.md`
  - 봇/AI 계층과 행동 우선순위
- `security-validation-spec.md`
  - 권위 서버 입력 검증과 보안 경계
- `contribution-guide.md`
  - 저장소 기여 절차와 검증 기준
- `release-artifact-spec.md`
  - 릴리스 산출물과 manifest/checksum 기준
- `replay-validation-checklist.md`
  - replay 검증용 운영 체크리스트
- `benchmark-report-template.md`
  - 성능 측정 결과 기록 템플릿
- `desync-debugging-guide.md`
  - 결정론 불일치 조사 절차
- `playtest-report-template.md`
  - 플레이테스트 결과 기록 템플릿
- `testing-strategy.md`
  - 단위/통합/결정론/성능/소크 테스트 전략
- `server-ops-runbook.md`
  - 서버 실행, 헬스체크, 장애 대응, 운영 점검

## 추천 읽기 순서

### 제품 방향부터 볼 때

1. `product-vision.md`
2. `feature-backlog.md`
3. `milestone-plan.md`
4. `game-design-document.md`

### 구현 구조부터 볼 때

1. `architecture-overview.md`
2. `simulation-architecture.md`
3. `protocol-spec.md`
4. `replay-format-spec.md`
5. `testing-strategy.md`
6. `server-ops-runbook.md`

## 빠른 참조

- 게임 규칙을 바꿀 때:
  - `game-design-document.md`
  - `simulation-architecture.md`
  - `testing-strategy.md`
- 데이터/콘텐츠 구조를 바꿀 때:
  - `data-schema-spec.md`
  - `map-format-spec.md`
  - `balance-spec.md`
- 네트워크/프로토콜을 바꿀 때:
  - `protocol-spec.md`
  - `replay-format-spec.md`
  - `server-ops-runbook.md`
- 서버 검증/운영 규칙을 바꿀 때:
  - `security-validation-spec.md`
  - `server-ops-runbook.md`
  - `protocol-spec.md`
- 제품 우선순위를 조정할 때:
  - `product-vision.md`
  - `feature-backlog.md`
  - `milestone-plan.md`
- 릴리스/검증 절차를 정리할 때:
  - `release-artifact-spec.md`
  - `replay-validation-checklist.md`
  - `server-ops-runbook.md`
- 성능/결정론 이슈를 분석할 때:
  - `benchmark-report-template.md`
  - `desync-debugging-guide.md`
  - `testing-strategy.md`

## 기존 문서와의 관계

- 루트 `README.md`
  - 빠른 시작, 모듈 요약, 실행 명령
- `OPERATIONS.md`
  - 실제 실행/패키징/트러블슈팅 중심 운영 문서
- `COMMAND_CONTRACT.md`
  - 입력/명령 계약 상세
- `CHANGELOG.md`
  - 릴리스 변경 이력
- `RELEASE_CHECKLIST.md`
  - 릴리스 게이트 체크리스트
- `COMMERCIALIZATION_ROADMAP.md`
  - 상용화/확장 계획

## 사용 원칙

- 설계 변경 시 관련 문서를 함께 갱신한다.
- 코드보다 늦게 문서가 바뀌지 않도록, 기능 머지 전에 최소 초안은 최신 상태로 유지한다.
- 시뮬레이션 규칙은 반드시 `sim` 구현을 기준으로 기록한다.
