# Audio Direction Brief

## 목적

`starkraft`의 오디오가 어떤 역할을 해야 하는지 정의한다.  
현재는 구현보다 방향성 정리가 목적이다.

## 오디오 목표

- 플레이어가 화면을 보지 않아도 중요한 이벤트를 감지할 수 있을 것
- 과도한 사운드 중첩 없이 전장 상태를 보조할 것
- UI/전투/경고가 우선순위에 따라 구분될 것

## 오디오 계층

### UI 피드백

- 선택
- 명령 수락
- 명령 실패
- 메뉴/패널 상호작용

### 전투 피드백

- 공격 발사
- 피격
- 유닛 사망
- 건물 파괴

### 경제/생산 피드백

- 건설 시작/완료
- 생산 완료
- 연구 완료
- 자원 부족

### 경고/전장 이벤트

- 공격받는 중
- worker idle 또는 경제 병목
- supply cap 도달
- 승리/패배

## 설계 원칙

- 경고음은 정보 우선순위가 높아야 한다.
- 반복 이벤트는 피로를 줄이기 위해 rate limit 또는 variation이 필요하다.
- 선택/명령 피드백은 짧고 즉각적이어야 한다.

## 우선순위

1. 명령 수락/실패
2. 공격받는 중
3. 생산/연구 완료
4. 선택/기본 UI

## 초기 구현 우선순위

1. command ack 기반 성공/실패 피드백
2. under attack alert
3. build/train/research complete alert
4. match end cue

## 운영 원칙

- 오디오는 gameplay 정보 전달을 돕는 방향으로 설계한다.
- 분위기 음악보다 정보성 효과음을 먼저 고정한다.
- headless/CI 경로는 오디오에 의존하지 않아야 한다.

## 관련 문서

- `/Users/hammac/Projects/starkraft/docs/ui-ux-spec.md`
- `/Users/hammac/Projects/starkraft/docs/server-ops-runbook.md`
- `/Users/hammac/Projects/starkraft/docs/playtest-report-template.md`
