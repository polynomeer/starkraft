# Art Direction Brief

## 목적

`starkraft`의 시각 표현이 placeholder를 넘어 제품 수준으로 갈 때 지켜야 할 방향을 정의한다.

## 현재 상태

- 시뮬레이션과 UX 구조가 우선인 단계
- 시각 자산은 가독성과 기능 검증이 최우선
- 완성형 아트 스타일은 아직 고정되지 않음

## 현재 구현 상태

- 시각 방향은 삼재 시안으로 정해졌다. 적용 순서는 [sampajeon-application-plan.md](sampajeon-application-plan.md)를 본다
- 월드 지형은 `GdxWorldRenderer`의 ShapeRenderer 도형으로 그린다. 유닛, 건물, 자원은 `data/visuals.json`에 등록된 타입이면 스프라이트 패스(y 정렬)로 그리고, 없으면 기존 silhouette(`drawUnitSilhouette`, `drawStructureSilhouette`)으로 그린다
- 그림은 같은 타입이면 양쪽 진영이 같으므로, 진영은 발밑 색 타원과 건물 바닥 색으로 구분한다
- faction 색: viewed faction은 friendly(파랑), 그 외는 enemy(적색), faction 0 이하는 neutral(sand). observer 시점에서는 faction 1이 friendly 색이다. 미니맵 마커도 같은 faction 색에 역할 색을 섞는다
- 구현된 상태 표현: HP 바, lime 선택 bracket/ring과 confirm pulse, 피격 flash/recoil, muzzle flash, death burst와 remains, 건설/생산/연구 완료 flash, activity/queue marker, 명령 ground ping, armed 명령 cursor preview, build placement preview
- 미구현/보류: 월드 fog 마스크(`drawFog`가 비어 있고, 대신 시야 밖 엔티티를 숨김), 자원 depletion 표시, 줌(현재 zoom lock)

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

1. 가독성 높은 placeholder 세트 (procedural silhouette로 진행 중)
2. faction color 시스템 정리 (theme 토큰으로 정리됨)
3. fog/vision 마스크 품질 개선 (미착수: 월드 fog pass 비활성)
4. 전투 hit feedback (기본 구현)
5. 생산/연구/건설 상태 애니메이션 (완료 flash와 activity marker 수준)

## 관련 문서

- [ui-ux-spec.md](ui-ux-spec.md)
- [battlefield-visual-contract.md](battlefield-visual-contract.md)
- [game-design-document.md](game-design-document.md)
- [feature-backlog.md](feature-backlog.md)
