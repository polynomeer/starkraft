# Performance Debugging Workflow

## 목적

입력 지연, 창 활성화 지연, 종료 지연, 프레임 드랍 같은 문제를 `수정 -> 실행 -> 계측 확인 -> 다시 수정` 루프로 반복적으로 잡기 위한 작업 절차다.

## 빠른 시작

1. 디버그 오버레이 포함 실행
   - `./scripts/play_sim_profile.sh`
2. 기본 플레이 스모크
   - `./scripts/play_sim_smoke.sh`
3. 일반 실행
   - `./gradlew :sim:play`

`play_sim_profile.sh`는 `STARKRAFT_SIM_DEBUG=1`로 실행하므로 인게임 성능 오버레이가 바로 켜진다.

## 인게임 성능 오버레이

디버그 오버레이는 다음 값을 표시한다.

- `fps`
- `frame ms`
- `tick ms`
- `hud ms`
- `world ms`
- `stage ms`
- 현재 화면 해상도
- world viewport height
- entity count
- selection count

의미는 다음과 같다.

- `tick ms`: snapshot poll, 상태 업데이트, 카메라 glide 등 런타임 갱신 비용
- `hud ms`: Scene2D HUD 동기화와 텍스트/버튼 재계산 비용
- `world ms`: 월드 렌더러가 지형, 유닛, 이펙트, 미니맵을 그리는 비용
- `stage ms`: `stage.act()` + `stage.draw()` 비용
- `frame ms`: 전체 프레임 비용

## 고정 재현 시나리오

매번 같은 순서로 확인한다.

1. 게임 시작
2. 마우스를 화면 상단, 좌측, 우측 끝으로 이동
3. 유닛 박스 선택
4. 우클릭 이동 명령
5. `A` 등 타겟팅 명령 후 좌클릭
6. 미니맵 클릭과 드래그
7. 창 비활성화 후 다시 활성화
8. 우상단 닫기 버튼 또는 `Quit` 버튼으로 종료

## 판단 기준

다음 식으로 병목 위치를 빠르게 좁힌다.

- `hud ms`가 튀면:
  - HUD 재빌드 빈도
  - Scene2D layout invalidation
  - 버튼/라벨 텍스트 재생성
- `world ms`가 튀면:
  - visible tile range
  - minimap sampling
  - fog/vision membership lookup
  - per-frame Color/Drawable allocation
- `stage ms`가 튀면:
  - overlay actor 수
  - draw order
  - alpha/background churn
- `tick ms`가 튀면:
  - snapshot backlog drain
  - NDJSON polling
  - selection recomputation
  - runtime event queue

## 작업 원칙

- 한 번에 하나만 바꾼다.
- 수정 후 반드시 바로 실행한다.
- 좋아진 수치와 나빠진 수치를 둘 다 기록한다.
- 체감만 보지 말고 오버레이 수치도 같이 본다.
- 악화되면 즉시 되돌린다.

## 권장 기록 방식

플레이 세션마다 [playtest-report-template.md](/Users/hammac/Projects/starkraft/docs/playtest-report-template.md)에 아래 항목을 추가로 기록한다.

- 재현 시나리오
- 평균 체감 FPS
- 오버레이에서 본 최고 `frame ms`
- 튀는 항목이 `tick/hud/world/stage` 중 무엇이었는지
- 창 활성화/종료 지연 유무

## 이번 코드베이스에서 우선 의심할 구간

- `GameScreen.refreshHud()`
- `GdxWorldRenderer.render()`
- `ClientSession` vision lookup
- snapshot stream polling backlog
- Scene2D actor background churn

최근 실제 병목으로 확인된 항목:

- snapshot NDJSON를 한 프레임에 과도하게 비우는 backlog drain
- HUD 레이아웃 확장 이후 Scene2D 재측정 비용 증가
- 월드 렌더러에서 화면 밖 정보까지 함께 갱신하는 경로

따라서 성능 이상이 재발하면 아래를 먼저 확인한다.

1. `FileClientStreamSubscription`의 poll 처리 상한이 유지되는지
2. `:sim:play` 기본 snapshot cadence가 불필요하게 커지지 않았는지
3. HUD actor 수와 tooltip/label churn이 다시 늘어나지 않았는지

## 다음 반복 루프

1. `play_sim_profile.sh` 실행
2. 재현 시나리오 수행
3. 가장 큰 ms 항목 확인
4. 해당 경로만 수정
5. 테스트 실행
6. 다시 실행 후 비교
