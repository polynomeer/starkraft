# AI Behavior Spec

## 목적

봇/AI가 어떤 우선순위와 상태 전이를 통해 행동하는지 정의한다.

## 범위

이 문서는 현재의 단순 봇부터 향후 강화된 행동 계층까지 포함하는 설계 기준이다.

## 계층 구조

### 전략 계층

- 경제 우선
- 러시
- 테크 확장
- 수비 후 역공

### 전술 계층

- 부대 집결
- 교전 시작 조건
- retreat 판단
- target priority

### 작업 계층

- worker harvest assignment
- build order execution
- production queue scheduling
- research queue scheduling

## 기본 상태

### 경제 상태

- worker 부족 확인
- 자원 수급 균형 확인
- drop-off / 생산 건물 투자 판단

### 생산 상태

- idle producer 탐지
- 공급 한계 탐지
- 현재 조합에 필요한 유닛 우선순위 결정

### 전투 상태

- 일정 병력 이상일 때 집결
- 적 발견 시 attack-move 또는 방어 반응
- 무리한 chase를 피함

## 설계 원칙

- AI도 사람이 사용하는 명령 계약을 통해 동작해야 한다.
- 서버 authoritative 경로를 우회하지 않는다.
- 결정론을 해치지 않는 방식으로 상태를 갱신한다.

## 우선순위 예시

### 초반

1. worker 생산 유지
2. 채집 최적화
3. 첫 생산 건물/연구 건물 확보

### 중반

1. 병력 조합 유지
2. 공급/생산 병목 해소
3. 맵 장악 또는 견제

### 후반

1. 교전 효율 극대화
2. 자원 고갈 시 재배치
3. 승리 조건 직결 행동 우선

## 관측 가능성

AI 행동은 다음 산출물로 추적 가능해야 한다.

- replay command stream
- command ack
- snapshot state 변화
- optional AI decision log

## 개선 우선순위

1. 경제 루프 안정화
2. 전투 집결/attack timing 개선
3. 연구/생산 다양화
4. 상대 조합 대응 로직

## 관련 코드

- `/Users/hammac/Projects/starkraft/client/cmd/bot`
- `/Users/hammac/Projects/starkraft/sim/src/main/kotlin`
- `/Users/hammac/Projects/starkraft/docs/feature-backlog.md`
