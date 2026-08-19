# Input Mapping Spec

## 목적

입력 체계를 고정해 클라이언트 UX와 명령 계약을 일관되게 유지한다.

## 입력 계층

### 카메라

- 화살표 키 또는 WASD: pan
- 마우스 휠: zoom
- 중클릭 드래그: pan
- `0`: 카메라 리셋
- `home`: selection 중심 이동
- `end`: viewed faction 중심 이동

### 선택

- 좌클릭: 단일 선택
- 좌클릭 드래그: 박스 선택
- `shift + 좌클릭`: 선택 추가/토글
- `shift + 드래그`: 박스 추가 선택

### 기본 명령

- 우클릭:
  - 적 위: attack
  - 자원 노드 위: harvest
  - 그 외: move
- `ctrl + 우클릭`: attack-move

### 패널 명령

- command panel 버튼 클릭으로 동일 명령 수행
- 비활성 버튼은 이유를 tooltip 또는 상태 라인으로 표시

## 단축키 기본안

### 시점/관전

- `1`: faction 1 보기
- `2`: faction 2 보기
- `3`: observer 보기

### 전투/이동

- `M`: move mode
- `A`: attack-move mode
- `P`: patrol mode
- `H`: hold

### 건설/생산/연구

- `B`: 기본 건설 모드
- `R`, `G`: 특정 건설 프리셋
- `N`: producer 선택
- `Z`: training building 선택
- `C`: research building 선택

### 선택 보조

- `F2`: 현재 viewed faction 전체 선택
- `F3`: 현재 선택 타입 전체 선택
- `F4`: 현재 선택 archetype 전체 선택
- `F11`: 전체 선택
- `F12`: idle worker 선택
- `F`: damaged 선택
- `V`: combat 선택

### 컨트롤 그룹

- `4..9`: recall
- `shift + 4..9`: set
- `alt + 4..9`: add
- `alt + 0`: clear groups

## 입력 설계 원칙

- 마우스 우클릭은 문맥형 기본 명령으로 유지한다.
- 자주 쓰는 선택 필터는 function key 또는 짧은 단축키에 둔다.
- 고급 명령은 panel과 hotkey 둘 다 지원한다.

## 명령 실패 표시

입력은 성공/실패 여부를 UI에서 확인할 수 있어야 한다.

예시 실패:

- selection 없음
- train/research/build capability 없음
- 자원 부족
- invalid placement

## 관련 문서

- `/Users/hammac/Projects/starkraft/docs/ui-ux-spec.md`
- `/Users/hammac/Projects/starkraft/COMMAND_CONTRACT.md`
- `/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client`
