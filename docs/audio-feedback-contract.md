# Audio Feedback Contract

## 목적

그래픽 클라이언트의 명령, 전투, 경고, 완료 사운드를 일관된 규칙으로 관리하기 위한 계약 문서다.

이 문서는 다음 문제를 방지한다.

- 같은 상황인데 파일마다 다른 음량/반응 속도
- 반복 재생으로 싸구려처럼 들리는 문제
- 전투가 많아질수록 중요한 소리가 묻히는 문제

## 현재 구현 상태

현재 사운드는 이벤트 기반으로 동작한다. [`GdxClientRuntime.kt`](../sim/src/main/kotlin/client/GdxClientRuntime.kt)가 `CommandSoundKind`/`CombatSoundKind`/`DeathSoundKind`와 attack alert/completion 플래그를 pending 상태로 남기고, [`GameScreen.kt`](../sim/src/main/kotlin/client/GameScreen.kt)의 `render()`가 매 프레임 이를 소비해 재생한다.

구현됨:

- 음원: 오디오 파일이 없다. [`GdxUiAssets.kt`](../sim/src/main/kotlin/client/GdxUiAssets.kt)가 시작 시 `renderToneWav`로 짧은 tone WAV를 합성해 임시 파일로 쓰고 `Gdx.audio.newSound`로 로드한다 (dispose 시 삭제)
- command: move / attack / build / invalid. 명령 확정뿐 아니라 armed 진입 시에도 해당 cue가 나고, 실패(선택 없음, invalid placement, invalid attack target, 미니맵 build 시도)는 invalid cue
- combat: marine ranged / zergling melee / 일반 melee / 일반 ranged (공격자 기준 선택)
- death: unit / marine / zergling / structure (structure는 main + tail 두 소리)
- alert: viewed faction 피해 시 under attack, 아군 구조물 파괴 시 structure lost
- completion: 건설/생산/연구 완료
- variation: 이벤트당 단일 샘플. 재생할 때마다 결정론적 phase로 pitch(예: move 0.98~1.05)와 gain(약 ±5%)을 순환시킨다. sample rotation은 없다
- cooldown: key별 cooldown이 command/death에만 있다 (아래 표 참고)

미구현:

- UI click / selection confirm 사운드
- bus, priority gate, ducking
- 카메라 거리 attenuation (모든 소리 non-spatial)
- ambience, music
- `PresentationTheme.audio` gain 토큰은 정의만 되어 있고 재생 코드는 하드코딩 볼륨을 쓴다 (alert 0.70, move 0.48, attack 0.55, build 0.52, invalid 0.48, combat 0.42~0.46, death 0.56~0.60, structure tail 0.38, completion 0.55)
- 카테고리별 pending 슬롯이 하나라서 한 프레임에 같은 카테고리 이벤트가 여러 개면 마지막 것만 재생된다

아래 Bus / Priority / Spatial 절은 목표 규격이다.

## Audio Categories

```text
ui
command
combat
alert
death
completion
ambience
music
```

## Bus Structure

```text
UiBus
CommandBus
CombatBus
AlertBus
AmbienceBus
MusicBus
```

규칙:

- 한 event는 정확히 한 primary bus를 가진다
- alert는 다른 bus보다 청취 우선순위가 높다
- ambience/music는 UI 반응성을 침범하지 않는다

## Event Mapping

| Event | Bus | Priority |
| --- | --- | --- |
| button click | UiBus | 20 |
| selection confirm | UiBus | 28 |
| move order | CommandBus | 40 |
| attack order | CommandBus | 48 |
| build order | CommandBus | 46 |
| invalid order | AlertBus | 65 |
| hit impact | CombatBus | 50 |
| unit death | CombatBus | 58 |
| structure death | AlertBus | 76 |
| under attack | AlertBus | 82 |
| completion notice | AlertBus | 60 |

## Variation Rules

반복감 감소를 위해 모든 주요 action은 최소 3개 variation을 권장한다.

| Action | Minimum Variations |
| --- | --- |
| move acknowledge | 3 |
| attack acknowledge | 3 |
| impact hit | 4 |
| death unit | 3 |
| structure collapse | 2 |
| warning beep | 2 |

variation 방식:

- sample rotation
- pitch ±3~5%
- gain ±4~8%

주의:

- alert류는 pitch 변화 폭을 과도하게 키우지 않는다
- comedic variation 금지

## Cooldown Rules

| Event Group | Cooldown |
| --- | --- |
| UI click | 40ms |
| move/attack command | 30ms |
| build/invalid | 45ms |
| unit death | 55ms |
| structure death | 80ms |
| attack alert | 300ms 이상 |

현재 구현값: move/attack 30ms, build/invalid 45ms, unit/marine death 55ms, zergling death 45ms, structure death 80ms (tail 120ms). UI click 사운드는 없고, combat/alert/completion에는 시간 cooldown이 없다 (attack alert는 damage tick당 한 번).

규칙:

- 같은 frame에 동일 그룹이 여러 번 발생해도 대표 샘플만 재생 가능
- cooldown은 spam 억제용이지 입력 반응 지연용이 아니다

## Priority Rules

동시 재생 경쟁 시 우선순위:

1. attack alert / structure loss
2. selected unit acknowledge
3. visible combat near camera
4. completion notification
5. distant combat
6. passive ambience

## Spatial Rules

향후 combat/death 사운드는 카메라 기준 거리 attenuation을 지원한다.

예:

- near: 1.0
- mid: 0.72
- far: 0.42
- beyond threshold: drop or heavily attenuate

규칙:

- UI/command는 non-spatial
- combat/death는 spatial candidate
- under attack alert는 non-spatial global cue

## Mix Rules

- command는 짧고 dry해야 한다
- combat는 존재감이 있어야 하지만 dialog/alert를 먹으면 안 된다
- warning은 battlefield noise 위로 떠야 한다
- structure death는 단일 unit death보다 더 낮고 무겁게 들려야 한다

## State-Specific Audio Language

| State | Audio Character |
| --- | --- |
| selection | crisp, light, immediate |
| move | affirmative, precise |
| attack | sharper, more aggressive |
| build | industrial / constructive |
| invalid | short, clear rejection |
| research complete | clean technical success |
| under attack | concise tactical warning |

## Acceptance Criteria

- 반복 이동/공격 명령이 거슬리지 않는다
- 구조물 파괴와 일반 유닛 죽음이 청각적으로 구분된다
- 공격 경고는 전투 소리 속에서도 즉시 인지된다
- 선택/명령 입력 반응이 느리게 들리지 않는다

## Implementation Plan

현재 Phase 1~3 항목은 모두 미착수다. cooldown 값은 `GameScreen.render()`에 하드코딩되어 있다.

Phase 1:

- event group별 variation bank 정의
- cooldown table 외부화
- gain token 테이블화

Phase 2:

- simple bus abstraction
- priority gate
- selected-unit acknowledge 분리

Phase 3:

- distance attenuation
- ambience layering
- ducking between alert and combat

