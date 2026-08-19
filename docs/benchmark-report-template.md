# Benchmark Report Template

## 목적

성능 측정 결과를 일관된 형식으로 남기기 위한 템플릿이다.

## 메타데이터

- 실행 날짜:
- 작성자:
- branch / commit:
- sim version:
- protocol version:
- 실행 환경:
  - CPU:
  - RAM:
  - OS:
  - JDK / Go version:

## 실행 조건

- tick 수:
- map/scenario:
- seed:
- snapshot/replay 출력 여부:
- bot 또는 scripted input 사용 여부:

## 측정 항목

### Tick 시간

- p50:
- p95:
- p99:
- max:

### 경로탐색

- average path length:
- node budget usage:
- replan count:
- queue depth:

### 메모리/할당

- 관찰된 GC 이벤트:
- 메모리 증가 추세:
- hot path allocation 의심 지점:

## 비교 기준

- 이전 기준 commit:
- 이전 대비 변화:
  - improved / regressed / neutral

## 해석

- 주요 병목:
- 회귀 의심 원인:
- 추가 조사 필요 항목:

## 결론

- 릴리스 차단 여부:
- 후속 작업:

## 관련 명령

- `./gradlew :sim:benchmark`
- `./scripts/smoke_run.sh`
- `/Users/hammac/Projects/starkraft/docs/testing-strategy.md`
