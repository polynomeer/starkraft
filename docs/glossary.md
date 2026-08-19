# Glossary

## 목적

`starkraft` 문서와 코드 전반에서 반복되는 핵심 용어를 일관되게 정의한다.

## 용어

### authoritative server

게임 상태를 최종 판정하는 서버 계층.  
클라이언트는 상태를 직접 확정하지 못하고 명령만 제안한다.

### tick

고정 시간 단위의 시뮬레이션 스텝.  
`starkraft`는 20ms 고정 tick 기반으로 동작한다.

### deterministic simulation

동일한 초기 상태와 동일한 입력이 주어졌을 때 항상 동일한 결과를 내는 시뮬레이션.

### world hash

특정 tick 또는 최종 상태를 요약하는 결정론 검증용 해시.

### replay hash

replay command stream 또는 replay payload 자체를 기준으로 계산한 검증용 해시.

### snapshot

클라이언트 표시 또는 디버깅을 위해 외부에 내보내는 게임 상태 요약.

### command ack

서버가 특정 명령 요청을 수락/거부했는지 알려주는 응답.

### occupancy

맵 타일 또는 공간이 현재 유닛/건물에 의해 점유된 상태.

### path replan

기존 경로가 무효화되거나 진전이 없을 때 새 경로를 다시 계산하는 것.

### fog of war

faction별 시야에 따라 보이는 정보와 숨겨지는 정보를 구분하는 시스템.

### producer

유닛이나 연구를 큐에 넣을 수 있는 건물 또는 엔티티.

### drop-off

worker가 채집 cargo를 반환해 faction stockpile로 전환하는 건물.

### archetype

엔티티의 대략적인 역할 분류.  
예: worker, producer, combat.

### golden test

예상 출력이나 해시를 고정해 회귀를 잡는 테스트.

### smoke test

전체 경로가 최소한으로 정상 동작하는지 빠르게 확인하는 테스트.

### soak test

장시간 실행을 통해 메모리 증가, 성능 저하, 안정성 문제를 확인하는 테스트.

## 관련 문서

- `/Users/hammac/Projects/starkraft/docs/architecture-overview.md`
- `/Users/hammac/Projects/starkraft/docs/simulation-architecture.md`
- `/Users/hammac/Projects/starkraft/docs/protocol-spec.md`
