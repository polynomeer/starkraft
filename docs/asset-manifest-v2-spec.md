# Asset Manifest V2 Spec

## 목적

현재 [`sim/src/main/resources/data/visuals.json`](../sim/src/main/resources/data/visuals.json) 기반 자산 정의를 production-ready manifest로 확장하기 위한 규격이다.

이 문서는 다음 문제를 해결한다.

- 스프라이트마다 기준점이 달라 월드 위 정렬이 흔들리는 문제
- 타입별 variant, shadow, effect 연동 정보가 자산 외부에 흩어지는 문제
- atlas 전환 시 로더와 렌더러가 동시에 깨지는 문제

## 현재 구조

현재 참조 타입:

- [`sim/src/main/kotlin/client/VisualAssetCatalog.kt`](../sim/src/main/kotlin/client/VisualAssetCatalog.kt)
- [`sim/src/main/kotlin/client/GdxSpriteAssets.kt`](../sim/src/main/kotlin/client/GdxSpriteAssets.kt)

현재 `SpriteAssetRef` 필드:

- `asset`
- `scale`
- `tint`
- `teamTintStrength`

이 구조는 시작점으로는 충분하지만, 표현 품질을 올리기에는 메타데이터가 부족하다.

## 현재 구현 상태 (v1)

이 문서의 V2 필드(`anchorX`, `anchorY`, `shadowAsset`, `selectionStyle`, `variantGroup`, `animationSet`, `layer`, `effectSet`, `minimapStyle`)와 atlas, validation CLI는 모두 미구현이다. 현재 v1 동작은 다음과 같다.

- 최상위 키: `units`, `unitArchetypes`, `buildings`, `buildingArchetypes`, `resources`, `terrain` (`base` / `blocked` / `occupied`). V2 제안의 `structures` 대신 현재 키는 `buildings`다
- 조회: footprint가 있는 엔티티는 `buildings[typeId]` → `buildingArchetypes[archetype]`, 그 외는 `units[typeId]` → `unitArchetypes[archetype]` 순
- 파싱: `ignoreUnknownKeys = true`라 V2 필드를 미리 넣어도 무시된다. `/data/visuals.json`이 없으면 빈 manifest
- 로더(`GdxSpriteAssets`): classpath 경로로 텍스처를 읽고, 없는 파일은 missing으로 캐시해 `null`을 돌려준다(경고 로그 없음). 알파 > 8인 픽셀 기준으로 opaque bounds를 잘라 region을 만든다. `scale`은 0.01 이상, `teamTintStrength`는 `0..1`로 clamp하고, `tint`는 `#` 유무와 무관하게 hex로 파싱한다
- 현재 `visuals.json`: `Worker`/`Marine`/`Zergling`, `Depot`/`ResourceDepot`/`GasDepot`(세 건물 모두 `depot.png` 공유), `minerals`/`gas`, terrain 3종
- 렌더링: `GdxWorldRenderer`는 현재 sprite를 그리지 않는다. 커밋 `5149344`(perf: restore fast play baseline)에서 sprite draw 경로가 빠진 뒤로 월드는 항상 procedural silhouette으로 그려지고, `GdxUiAssets.spriteAssets`는 생성만 된다
- `tools`의 data validate는 `visuals.json`을 검사하지 않는다

## 설계 원칙

1. manifest는 렌더 규칙을 설명하되 gameplay 규칙은 담지 않는다.
2. 타입별 기본 규칙과 개별 override를 둘 다 지원한다.
3. atlas 기반과 loose file 기반을 동시에 지원한다.
4. 자산이 누락되어도 procedural fallback이 가능해야 한다.

## 대상 리소스 범주

```text
units
structures
resources
terrain
effects
ui
shadows
```

## 제안 스키마

```json
{
  "units": {
    "Marine": {
      "asset": "assets/sprites/units/marine.png",
      "scale": 1.0,
      "anchorX": 0.5,
      "anchorY": 0.78,
      "tint": null,
      "teamTintStrength": 0.18,
      "shadowAsset": "assets/sprites/shadows/unit-soft.png",
      "selectionStyle": "infantry",
      "variantGroup": "marine",
      "animationSet": "marine-basic",
      "layer": "unit"
    }
  }
}
```

## 필드 정의

### Required

| Field | Meaning |
| --- | --- |
| `asset` | 기본 텍스처 또는 atlas region 식별자 |

### Optional

| Field | Meaning |
| --- | --- |
| `scale` | 월드 렌더 기준 배율 |
| `anchorX` | 0~1 기준 좌우 anchor |
| `anchorY` | 0~1 기준 상하 anchor |
| `tint` | 고정 tint override |
| `teamTintStrength` | 팀 컬러 혼합 강도 |
| `shadowAsset` | 별도 그림자 스프라이트 |
| `selectionStyle` | selection ring/bracket 스타일 키 |
| `variantGroup` | 랜덤/상태 variant 그룹 |
| `animationSet` | 상태별 프레임/클립 세트 키 |
| `layer` | 렌더 우선 레이어 |
| `effectSet` | hit/death/complete effect preset 키 |
| `minimapStyle` | minimap 아이콘 스타일 키 |

## Terrain 정의

terrain은 개별 entity와 다르게 타일 재질 정의를 가진다.

```json
{
  "terrain": {
    "base": {
      "asset": "assets/sprites/terrain/base-tile.png",
      "scale": 1.0
    },
    "blocked": {
      "asset": "assets/sprites/terrain/blocked-tile.png",
      "scale": 1.0
    },
    "occupied": {
      "asset": "assets/sprites/terrain/occupied-tile.png",
      "scale": 1.0
    }
  }
}
```

향후 확장:

- biome set
- edge overlay
- animated tile set

## Effect Set 연동

manifest는 effect 세트와 느슨하게 연결되어야 한다.

예:

- `effectSet = infantry-ballistic`
- `effectSet = melee-organic`
- `effectSet = structure-industrial`

의미:

- 피격 flash 색
- death burst 파편 성격
- complete burst 모양
- projectile trail tone

## Selection Style 연동

`selectionStyle`는 아래 타입을 권장한다.

- `infantry`
- `worker`
- `melee`
- `hero`
- `structure-small`
- `structure-large`

이 키는 후속 `battlefield-visual-contract` 구현에서 bracket/ring shape를 매핑할 때 쓴다.

## Atlas 전략

지원 방식:

1. loose PNG 경로
2. atlas region 식별자

권장 규칙:

- 개발 초기: loose PNG 허용
- 자산 묶음 안정화 후: atlas 우선
- loader는 둘 다 처리

## Validation Rules

- `asset` 경로가 없으면 경고 후 fallback (현재: 경고 없이 fallback)
- `scale <= 0` 금지 (현재: 로더가 0.01로 clamp)
- `anchorX`, `anchorY`는 `0..1`
- `teamTintStrength`는 `0..1`
- 중복 type key 금지
- 존재하지 않는 `selectionStyle`, `effectSet`, `animationSet`은 validation warning

## Fallback Rules

누락 시 동작:

- sprite 없음: procedural silhouette
- shadow 없음: generic soft shadow
- selectionStyle 없음: `infantry` 또는 `structure-small`
- effectSet 없음: generic type fallback
- minimapStyle 없음: default dot

## 파일 구조 제안

```text
sim/src/main/resources/
├── assets/
│   ├── sprites/
│   ├── shadows/
│   ├── effects/
│   └── ui/
└── data/
    ├── visuals.json
    ├── effects.json
    ├── selection-styles.json
    └── minimap-styles.json
```

## 단계별 도입 계획

Phase 1:

- `anchorX`, `anchorY`, `selectionStyle`, `effectSet` 추가

Phase 2:

- atlas region 지원
- `animationSet` 지원

Phase 3:

- validation CLI 추가
- asset packing script 추가

