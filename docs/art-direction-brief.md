# Art Direction Brief

## 목적

`starkraft`의 시각 표현이 placeholder를 넘어 제품 수준으로 갈 때 지켜야 할 방향을 정의한다.

## 현재 상태

- 시뮬레이션과 UX 구조가 우선인 단계
- 시각 자산은 가독성과 기능 검증이 최우선
- 완성형 아트 스타일은 아직 고정되지 않음

## 시각 목표

### 읽기 쉬운 전장

- 유닛, 건물, 자원 노드가 한눈에 구분되어야 한다.
- faction 구분이 즉시 가능해야 한다.
- 선택/공격/이동 상태가 오버레이 없이도 최대한 읽혀야 한다.

### RTS 친화적 계층

- 지형
- 건물
- 유닛
- 투사체/효과
- UI 오버레이

이 계층이 시각적으로 충돌하지 않아야 한다.

### 디버그와 제품의 양립

- 개발용 정보는 토글 가능해야 한다.
- 제품 UI는 디버그 문자열에 의존하지 않아야 한다.

## 스타일 원칙

- 과도하게 사실적인 스타일보다 읽기 쉬운 스타일 우선
- 색상 수를 통제해 faction/경고 색이 묻히지 않게 유지
- 배경 대비 전투 오브젝트를 충분히 분리

## 우선 자산

1. 기본 타일셋
2. 유닛 실루엣 세트
3. 건물 footprint와 완공 상태 구분
4. 자원 노드 시각 구분
5. selection / rally / target marker

## 상태 표현

### 유닛

- hp 상태
- 선택 상태
- 작업 상태
- 공격 상태

### 건물

- construction progress
- active production/research
- damaged state

### 자원

- mineral vs gas 구분
- depletion 상태 표시 가능성

## 카메라와 화면 구성 고려

- 줌 레벨이 바뀌어도 유닛 유형 식별이 가능해야 한다.
- 미니맵 표현은 전장 표현과 일관된 faction 색을 사용해야 한다.

## 비주얼 구현 우선순위

1. 가독성 높은 placeholder 세트
2. faction color 시스템 정리
3. fog/vision 마스크 품질 개선
4. 전투 hit feedback
5. 생산/연구/건설 상태 애니메이션

## 관련 문서

- `/Users/hammac/Projects/starkraft/docs/ui-ux-spec.md`
- `/Users/hammac/Projects/starkraft/docs/game-design-document.md`
- `/Users/hammac/Projects/starkraft/docs/feature-backlog.md`
