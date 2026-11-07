# content

유닛, 건물, 무기, 기술의 규칙 데이터입니다. `core`의 `ContentParser`가 읽고, 모르는 키, 빠진 필수 값, 없는 대상을 가리키는 참조가 있으면 거부합니다. 표시 정보(스프라이트 등)는 여기에 두지 않습니다.

## 단위

| 값 | 단위 | 비고 |
| --- | --- | --- |
| `buildMs`, `cooldownMs` | 밀리초 | 불러올 때 틱으로 올림 변환 |
| `speedTilesPerSecond` | 타일/초 | 불러올 때 타일/틱으로 변환 |
| `range`, `rallyOffsetX/Y` | 타일 | 소수는 고정소수점으로 정확히 읽음. 지수 표기 금지 |

## Kotlin 데이터와의 대응

기존 이름은 임시 이름이어서 새 데이터에 가져오지 않았습니다. 수치는 50틱/초 기준 값을 위 단위로 환산했습니다.

| Kotlin | 여기 |
| --- | --- |
| `Worker`, `Marine`, `Zergling` | `artisan`, `gunner`, `brute` |
| `Depot`, `ResourceDepot`, `GasDepot` | `hall`, `granary`, `still` |
| `Gauss`, `Claw` | `matchlock`, `club` |
| `AdvancedTraining` | `advanced_training` |
| `minerals` | `ore` |
