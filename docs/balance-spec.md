# Balance Spec

## 목적

이 문서는 `starkraft`의 세부 수치 밸런스를 어떻게 관리할지 정의한다.  
핵심 규칙 문서는 `/Users/hammac/Projects/starkraft/docs/game-design-document.md`이고, 이 문서는 그 위에 올라가는 수치 운영 문서다.

## 범위

밸런스 대상:

- 유닛 체력, 방어력, 이동속도, 시야
- 무기 피해량, 사거리, 쿨다운
- 건물 비용, 건설 시간, footprint 가치
- 생산 시간, 연구 시간
- 자원 채집량, 반환 주기, supply 효율

## 설계 원칙

### 읽기 쉬운 전장

- 같은 계열 유닛은 역할이 즉시 구분되어야 한다.
- 유닛 강점은 사거리, 내구, 기동성, 비용 중 최소 하나에서 드러나야 한다.

### 명확한 카운터

- 모든 주력 유닛은 효율적으로 상대 가능한 대응 축이 있어야 한다.
- 무적 조합보다 운영 선택을 강요하는 구성이 바람직하다.

### 경제와 전투의 연결

- 경제 이득이 곧바로 전투 snowball이 되더라도, 방어/견제 선택지가 남아야 한다.

### 결정론 친화성

- 밸런스 변경은 되도록 데이터 변경으로 끝나야 한다.
- 수치 조정이 시스템 로직 변경을 강제하면 별도 설계 검토가 필요하다.

## 밸런스 축

### 경제 축

- 초기 worker 수
- 기본 자원량
- 채집 속도
- 생산 인프라 투자 비용

### 전투 축

- TTK(time to kill)
- 사거리 우위
- 기동성
- 집중 화력 효율

### 테크 축

- 선행 건물 비용
- 연구 투자 회수 시간
- 업그레이드 체감 강도

## 관리 단위

### 유닛 시트

각 유닛은 최소한 다음을 관리한다.

- `typeId`
- role/archetype
- hp
- armor
- speed
- vision
- weapon profile
- mineral/gas cost
- build time
- supply cost

### 건물 시트

- `typeId`
- 역할
- footprint
- placement clearance
- mineral/gas cost
- build time
- unlock effect

### 연구 시트

- `techId`
- prerequisite
- cost
- duration
- effect

## 변경 절차

1. 문제를 증상 기준으로 기록한다.
   - 예: 특정 rush가 방어 불가능
2. 관련 replay를 수집한다.
3. 원인이 경제/전투/테크 중 어디인지 분류한다.
4. 수치 변경안을 데이터 기준으로 작성한다.
5. 고정 시드 playtest와 benchmark를 재실행한다.
6. 변경 결과를 changelog 또는 밸런스 로그에 남긴다.

## 추천 산출물

향후 별도 파일 또는 스프레드시트로 유지할 항목:

- matchup 메모
- 빌드오더별 timing 표
- TTK 비교표
- resource curve 표

## 관련 코드

- `/Users/hammac/Projects/starkraft/sim/src/main/resources/data`
- `/Users/hammac/Projects/starkraft/sim/src/main/kotlin/ecs`
- `/Users/hammac/Projects/starkraft/tools`

## 현재 상태

2026-08-19 기준:

- 핵심 규칙 구조는 성숙했지만, 수치 밸런스 운영 체계는 아직 제품 수준으로 고정되지 않았다.
- 따라서 이 문서는 구현 완료 문서가 아니라 운영 기준 초안이다.
