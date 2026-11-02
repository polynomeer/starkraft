# Input Mapping Spec

## 목적

입력 체계를 고정해 클라이언트 UX와 명령 계약을 일관되게 유지한다.

이 문서의 키/마우스 매핑은 libGDX 클라이언트 구현(`sim/src/main/kotlin/client/GameScreen.kt`의 `GameInputController`, `GdxClientRuntime.kt`)과 일치해야 한다. 코드와 다르면 코드를 기준으로 이 문서를 고친다.

## 입력 계층

### 카메라

- 화살표 키: pan (한 번 누를 때마다 고정 step 이동, WASD는 pan에 쓰이지 않음)
- 화면 가장자리 edge pan: 마우스가 월드 뷰포트 가장자리 20px 안에 있으면 자동 pan (HUD 위, 버튼 누름/드래그 중, pause/help overlay 중에는 비활성)
- 중클릭 드래그: pan
- 미니맵 좌클릭/좌드래그: 카메라 중심 이동 (armed 명령이 없을 때)
- 마우스 휠, `=`/`+`, `-`: zoom 입력 경로는 있으나 현재 `zoomLocked = true`로 고정되어 있어 무시된다
- `0`: 카메라 리셋
- `Home`: selection 중심 이동
- `End`: viewed faction 중심 이동

### 선택

- 좌클릭: 단일 선택 (클릭 지점에서 가장 가까운 viewed faction 엔티티, observer 시점에서는 모든 엔티티 대상)
- 좌클릭 드래그(8px 이상): 박스 선택 (viewed faction의 유닛과 건물, observer 시점에서는 전체)
- `shift + 좌클릭`: 선택 추가/토글
- `shift + 드래그`: 박스 추가 선택
- armed 명령 상태에서는 좌클릭이 선택이 아니라 명령 확정으로 소비되고, 드래그 박스 선택도 발생하지 않는다

### 기본 명령

- 우클릭:
  - 적 위: attack
  - 자원 노드 위: harvest
  - 그 외: move
- `ctrl + 우클릭`: attack-move
- `shift + 우클릭`: 명령 queue (기존 명령 뒤에 추가)
- 미니맵 우클릭: 미니맵 지점에 동일한 문맥형 명령 (`ctrl`/`shift` 규칙 동일)

### armed 명령

- `M`/`A`/`P`/`S` 또는 command panel 버튼으로 move/attack/patrol/rally를 armed 상태로 만든다
- 확정: 월드 또는 미니맵에 좌클릭/우클릭 (`shift`를 누르면 queue)
- build armed 상태는 월드에서만 확정되며, 미니맵 클릭은 `place build in main view`로 거절된다
- 취소: `Esc`
- 상세 규칙은 `COMMAND_CONTRACT.md`를 따른다

### 패널 명령

- command panel 버튼 클릭으로 동일 명령 수행
- 비활성 버튼은 hover 시 command block의 고정 hint 슬롯에 비활성 이유를 표시

## 단축키 (인게임, 현재 구현)

### 시스템

- `Esc`: pause overlay 닫기 → help overlay 닫기 → armed 명령 취소 → 선택 해제 → (아무것도 없으면) pause overlay 열기 순으로 처리
- `Space`: sim 일시정지/재개 토글 (play control 파일에 기록, pause overlay와는 별개)
- `F1`: help overlay 토글
- `Tab`: debug 정보 토글
- `[` / `]`: sim 속도 감소/증가
- `F5`: 매치 재시작
- `F6` / `F7`: 이전/다음 시나리오로 바꾸고 재시작

pause 또는 help overlay가 열려 있으면 `Esc`(와 help가 열린 경우 `F1`)만 처리되고 나머지 단축키와 월드 마우스 입력은 차단된다.

### 시점/관전

- `1`: faction 1 보기
- `2`: faction 2 보기
- `3`: observer 보기

### 전투/이동

- `M`: move mode
- `A`: attack-move mode
- `P`: patrol mode
- `S`: rally mode (rally 가능한 건물 선택 시)
- `H`: hold

### 건설/생산/연구

- `B`: `Depot` 건설 모드
- `R`: `ResourceDepot` 건설 모드
- `G`: `GasDepot` 건설 모드
- `U` / `I` / `O`: catalog의 1/2/3번째 train 옵션 queue
- `L`: catalog의 첫 번째 research 옵션 queue
- `X`: cancel build
- `T`: cancel train
- `Y`: cancel research

### 선택 보조

- `F2`: 현재 viewed faction 전체 선택
- `F3`: 현재 선택 타입 전체 선택
- `F4`: 현재 선택 archetype 전체 선택
- `F11`: 전체 선택
- `F12`: idle worker 선택
- `F`: damaged 선택
- `V`: combat 선택
- `N`: producer 건물 선택
- `Z`: training building 선택
- `C`: research building 선택
- `J`: 건설 중인 구조물 선택
- `K`: 채집 중인 harvester 선택
- `Q`: 귀환 중인 harvester 선택
- `E`: 자원을 들고 있는 harvester 선택
- `D`: 자원 drop-off 건물 선택

### 컨트롤 그룹

- `4..9`: recall (350ms 안에 같은 그룹을 다시 누르면 카메라 포커스)
- `shift + 4..9`: set
- `alt + 4..9`: add
- `alt + 0`: clear groups

### 메인 메뉴

- `←` / `→`: 시나리오 변경
- `Enter` / `Space`: 매치 진입
- `S` / `L`: quick preset 저장/불러오기
- `A` / `K`: alt preset 저장/불러오기
- `F5`: 시나리오 적용 후 재시작
- `Esc`: 종료

## 입력 설계 원칙

- 마우스 우클릭은 문맥형 기본 명령으로 유지한다.
- 자주 쓰는 선택 필터는 function key 또는 짧은 단축키에 둔다.
- 고급 명령은 panel과 hotkey 둘 다 지원한다.

## 명령 실패 표시

입력은 성공/실패 여부를 UI에서 확인할 수 있어야 한다.

예시 실패:

- selection 없음 (`select units first`, `select rally building`)
- train/research/build capability 없음
- 자원 부족
- invalid placement (`invalid build placement`)
- attack armed 상태에서 아군 클릭 (`invalid attack target`, armed 상태 유지)

실패 시 command block의 action banner notice, invalid ground ping, invalid 효과음이 함께 나온다.

## 알려진 불일치

- help overlay 문구(`GraphicalClient.kt`의 `buildHelpOverlayLines`)에 `f8/f9 quick preset`이 적혀 있지만 인게임 `F8`/`F9` 키 바인딩은 없다. quick/alt preset은 pause overlay 버튼과 메인 메뉴 단축키로만 쓸 수 있다.

## 관련 문서

- [ui-ux-spec.md](ui-ux-spec.md)
- [COMMAND_CONTRACT.md](../COMMAND_CONTRACT.md)
- [sim/src/main/kotlin/client](../sim/src/main/kotlin/client)
