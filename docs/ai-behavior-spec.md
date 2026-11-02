# AI Behavior Spec

## 목적

봇/AI가 어떤 우선순위와 상태 전이를 통해 행동하는지 정의한다.

## 범위

이 문서는 현재의 단순 봇부터 향후 강화된 행동 계층까지 포함하는 설계 기준이다.  
아래 "현재 구현" 절만 실제 동작이고, 계층 구조/기본 상태/우선순위 예시는 목표 설계(미구현)다.

## 현재 구현

유일한 봇은 Go headless 봇 [`client/cmd/bot/main.go`](../client/cmd/bot/main.go)이며, `sim` 쪽 AI는 없다.

연결:

- 플래그: `--url`(기본 `ws://127.0.0.1:8080/ws`), `--name`(`bot`), `--room`(`default`), `--simVersion`(`dev`, 공백만 있는 값은 거부), `--resumeToken`(선택).
- authoritative server에 websocket으로 접속하고 일반 클라이언트와 같은 command batch 경로로 명령을 보낸다.

snapshot마다 수행하는 규칙(상태 머신 없음, 무상태 반응형):

1. `OwnerID`가 자기 client id인 유닛과 나머지(적)를 나눈다. 자기 유닛이 없으면 아무것도 하지 않는다.
2. `tick % 20 == 0`이면 첫 번째 자기 유닛 위치 (+1, +1)에 `unitType: "Worker"`로 `build`를 보낸다.
3. `tick % 15 == 0`이면 `unitType: "Soldier"`로 `queue`를 보낸다.
4. 적이 있으면 첫 번째 자기 유닛에서 가장 가까운 적을 고른다. 거리 제곱이 36 이하(거리 6 이내)면 자기 유닛 전체로 `attack`, 아니면 그 적 위치로 `move`를 보낸다.

기타:

- 거부된 ack는 `cmd`/`reason`을 로그로 남긴다.
- match end snapshot을 받으면 승자를 출력하고 종료한다. 15초 동안 snapshot이 없으면 오류 종료한다.
- `Worker`/`Soldier`는 서버(`server/pkg/authoritative/room.go`)의 자체 Go 유닛 모델 타입이며 `sim` 데이터(`units.json`)와 연결되어 있지 않다.
- 경제(채집), 연구, 공급, 후퇴, 조합 판단은 없다.

## 계층 구조 (목표 설계, 미구현)

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

## 기본 상태 (목표 설계, 미구현)

### 경제 상태

- worker 부족 확인
- 자원 수급 균형 확인
- drop-off / 생산 건물 투자 판단

### 생산 상태

- idle producer 탐지
- 공급 한계 탐지 (sim에 supply 개념 자체가 아직 없음)
- 현재 조합에 필요한 유닛 우선순위 결정

### 전투 상태

- 일정 병력 이상일 때 집결
- 적 발견 시 attack-move 또는 방어 반응
- 무리한 chase를 피함

## 설계 원칙

- AI도 사람이 사용하는 명령 계약을 통해 동작해야 한다.
- 서버 authoritative 경로를 우회하지 않는다.
- 결정론을 해치지 않는 방식으로 상태를 갱신한다.

## 우선순위 예시 (목표 설계)

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
- command ack (현재 봇은 거부 ack만 로그)
- snapshot state 변화
- optional AI decision log (미구현)

## 개선 우선순위

1. 경제 루프 안정화
2. 전투 집결/attack timing 개선
3. 연구/생산 다양화
4. 상대 조합 대응 로직

## 관련 코드

- [`client/cmd/bot`](../client/cmd/bot)
- [`server/pkg/authoritative/room.go`](../server/pkg/authoritative/room.go)
- [`sim/src/main/kotlin`](../sim/src/main/kotlin)
- [`feature-backlog.md`](feature-backlog.md)
