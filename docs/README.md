# Starkraft Docs

이 디렉터리는 `starkraft`의 제품 기획, 아키텍처, 규칙 설계, 운영 절차, 품질 전략을 정리한 문서 허브다.  
루트 `README.md`가 저장소의 입구라면, 이 디렉터리는 역할별로 필요한 문서를 찾는 네비게이션 레이어다.

## 먼저 읽을 문서

### 처음 방문자

1. `project-overview.md`
2. `product-vision.md`
3. `architecture-overview.md`
4. `technical-deep-dive.md`

### 채용담당자 / 기술면접관

1. `project-overview.md`
2. `technical-deep-dive.md`
3. `testing-strategy.md`
4. `server-ops-runbook.md`

### 엔지니어

1. `architecture-overview.md`
2. `simulation-architecture.md`
3. `protocol-spec.md`
4. `replay-format-spec.md`
5. `data-schema-spec.md`

### 기획 / UX / 게임 디자인

1. `product-vision.md`
2. `game-design-document.md`
3. `ui-ux-spec.md`
4. `feature-backlog.md`
5. `presentation-quality-architecture.md`

## 문서 지형도

| 범주 | 핵심 문서 | 설명 |
| --- | --- | --- |
| 프로젝트 소개 | `project-overview.md` | 첫 방문자와 리뷰어를 위한 종합 설명 |
| 제품 방향 | `product-vision.md` | 목표, 비목표, 핵심 가치, 성공 기준 |
| 게임 기획 | `game-design-document.md` | 핵심 플레이 루프와 규칙 구조 |
| 기능 우선순위 | `feature-backlog.md`, `milestone-plan.md` | 남은 범위와 단계별 목표 |
| 디자인 적용 | `sampajeon-application-plan.md` | 삼재 시안을 게임에 적용하는 단계별 계획 |
| 전체 개선안 | `architecture-renewal-proposal.md` | 구조, 기술 선택, 범위를 포함한 개편 제안 (P0 결과 포함) |
| P1 코어 이식 계획 | `p1-core-port-plan.md` | Kotlin 규칙 코어를 C#으로 옮기는 범위, 순서, 테스트 목록 |
| 상위 설계 | `architecture-overview.md` | 모듈 책임과 데이터 흐름 |
| 기술 심화 | `technical-deep-dive.md` | 결정론, 서버 경계, replay 중심 설계 해설 |
| 시뮬레이션 내부 | `simulation-architecture.md` | `sim` 내부 구조와 성능/결정론 원칙 |
| 네트워크 계약 | `protocol-spec.md` | 메시지 구조, 버전 정책, 호환성 원칙 |
| 리플레이/도구 | `replay-format-spec.md`, `replay-validation-checklist.md` | replay 구조와 검증 절차 |
| 데이터/맵 | `data-schema-spec.md`, `map-format-spec.md` | 콘텐츠 정의 구조와 검증 규칙 |
| 밸런스/AI | `balance-spec.md`, `ai-behavior-spec.md` | 수치 정책과 봇 행동 기준 |
| UX/입력 | `ui-ux-spec.md`, `input-mapping-spec.md`, `hud-layout-contract.md` | HUD, 조작, 피드백 기준 |
| 표현 품질 | `presentation-quality-architecture.md`, `presentation-quality-improvement-plan.md`, `presentation-theme-spec.md`, `battlefield-visual-contract.md`, `hud-layout-contract.md`, `asset-manifest-v2-spec.md`, `audio-feedback-contract.md` | 상용 수준 presentation 구조와 시각/오디오 계약 |
| 아트/오디오 | `art-direction-brief.md`, `audio-direction-brief.md` | 시각/청각 방향성 |
| 품질 전략 | `testing-strategy.md`, `desync-debugging-guide.md`, `performance-debugging-workflow.md` | 테스트 계층과 디버깅 절차 |
| 보안/검증 | `security-validation-spec.md` | 서버 입력 검증과 하드닝 기준 |
| 운영/릴리스 | `server-ops-runbook.md`, `release-artifact-spec.md` | 운영 절차와 배포 산출물 기준 |
| 템플릿 | `benchmark-report-template.md`, `playtest-report-template.md` | 성능/플레이테스트 기록 양식 |
| 협업 규칙 | `contribution-guide.md`, `document-maintenance-policy.md`, `glossary.md` | 협업, 용어, 문서 유지 기준 |

## 목적별 빠른 링크

### 이 프로젝트가 어떤 프로젝트인지 알고 싶을 때

- `project-overview.md`
- `product-vision.md`

### 어떤 기술적 난제를 풀고 있는지 보고 싶을 때

- `technical-deep-dive.md`
- `simulation-architecture.md`
- `testing-strategy.md`

### 게임 규칙과 기능을 확인하고 싶을 때

- `game-design-document.md`
- `feature-backlog.md`
- `balance-spec.md`

### 화면 품질과 표현 구조를 확인하고 싶을 때

- `presentation-quality-architecture.md`
- `presentation-theme-spec.md`
- `battlefield-visual-contract.md`
- `hud-layout-contract.md`

### 실행/운영 방법을 확인하고 싶을 때

- `server-ops-runbook.md`
- `replay-validation-checklist.md`
- `performance-debugging-workflow.md`
- 루트 `README.md`

### 데이터나 콘텐츠 정의를 수정하려 할 때

- `data-schema-spec.md`
- `map-format-spec.md`
- `balance-spec.md`

### 프로토콜이나 서버 검증 로직을 수정하려 할 때

- `protocol-spec.md`
- `security-validation-spec.md`
- `replay-format-spec.md`

## 문서 목록

- `project-overview.md`
  - 프로젝트 소개, 완성도, 사용법, 포트폴리오 포인트
- `technical-deep-dive.md`
  - 결정론, authoritative server, replay/tooling 중심 기술 해설
- `product-vision.md`
  - 제품 목표, 비목표, 품질 기준, 성공 지표
- `feature-backlog.md`
  - 기능 우선순위와 남은 작업
- `milestone-plan.md`
  - 단계별 목표와 검증 자산 매핑
- `game-design-document.md`
  - 핵심 규칙과 플레이 루프
- `balance-spec.md`
  - 수치 변경 원칙과 운영 방식
- `architecture-overview.md`
  - 모듈 구조와 상위 데이터 흐름
- `simulation-architecture.md`
  - `sim` 내부 구조와 결정론 원칙
- `protocol-spec.md`
  - 네트워크/도구 간 메시지 계약
- `replay-format-spec.md`
  - replay 구조와 검증 포인트
- `data-schema-spec.md`
  - 데이터 정의 구조와 validation 규칙
- `map-format-spec.md`
  - 맵 포맷과 무결성 규칙
- `ui-ux-spec.md`
  - 그래픽 클라이언트 UX 기준
- `input-mapping-spec.md`
  - 입력 체계와 조작 정책
- `ai-behavior-spec.md`
  - 봇 AI 규칙과 우선순위
- `testing-strategy.md`
  - 테스트, smoke, benchmark, soak 전략
- `desync-debugging-guide.md`
  - 결정론 불일치 추적 절차
- `server-ops-runbook.md`
  - 서버 실행, health, 장애 대응
- `security-validation-spec.md`
  - 서버 입력 검증, 크기/빈도 제한, 하드닝 기준
- `release-artifact-spec.md`
  - 배포 아티팩트와 manifest/checksum 기준
- `replay-validation-checklist.md`
  - replay 운영 검증 체크리스트
- `benchmark-report-template.md`
  - 성능 보고 템플릿
- `playtest-report-template.md`
  - 플레이테스트 기록 템플릿
- `performance-debugging-workflow.md`
  - 그래픽 플레이 지연/프레임 드랍 재현 및 계측 절차
- `presentation-quality-architecture.md`
  - 현재 클라이언트 구조 위에서 presentation system을 재구성하는 상위 설계
- `presentation-quality-improvement-plan.md`
  - 아트/애니메이션/VFX/사운드/UI 연출 파이프라인 개선 방향 초안
- `presentation-theme-spec.md`
  - 색상, 타이포, 간격, 모션 토큰 기준
- `battlefield-visual-contract.md`
  - 전장 가독성과 상태 표현 계약
- `hud-layout-contract.md`
  - HUD 배치, 고정 슬롯, 레이어링 계약
- `asset-manifest-v2-spec.md`
  - 자산 매니페스트 확장 규격과 fallback/validation 규칙
- `audio-feedback-contract.md`
  - 명령/전투/경고 사운드 우선순위와 variation 계약
- `art-direction-brief.md`
  - 전장 가독성과 시각 자산 방향
- `audio-direction-brief.md`
  - 피드백 중심 오디오 방향
- `glossary.md`
  - 공통 용어 사전
- `contribution-guide.md`
  - 기여 방식과 검증 기준
- `document-maintenance-policy.md`
  - 문서 유지보수 정책

## 루트 문서와의 관계

- 루트 `README.md`
  - 저장소 입구, 실행 경로, 핵심 가치, 빠른 시작
- `OPERATIONS.md`
  - 실제 운영/실행 중심의 보조 문서
- `COMMAND_CONTRACT.md`
  - 클라이언트 명령 입력(armed command enter/confirm/cancel) 상세 계약
- `PLAYTEST_CHECKLIST.md`
  - 데스크톱 클라이언트 수동 플레이테스트 체크리스트
- `PHASE1_STABILIZATION_PLAN.md`
  - 상용화 로드맵 Phase 1 안정화 실행 계획
- `CHANGELOG.md`
  - 변경 이력
- `RELEASE_CHECKLIST.md`
  - 릴리스 전 게이트
- `COMMERCIALIZATION_ROADMAP.md`
  - 확장/상용화 방향

## 유지 원칙

- 기능, 구조, 운영 규칙이 바뀌면 관련 문서를 함께 갱신한다.
- 새로운 검증 명령이나 운영 절차가 생기면 README와 관련 문서 허브를 같이 업데이트한다.
- 시뮬레이션 규칙의 최종 기준은 항상 `sim` 구현과 테스트다.
- 플레이 성능 회귀를 수정했다면 `performance-debugging-workflow.md` 기준으로 재현 절차와 계측 지점을 함께 갱신한다.
