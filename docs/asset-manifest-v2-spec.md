# Asset Manifest V2 Spec

## 목적

현재 [`sim/src/main/resources/data/visuals.json`](/Users/hammac/Projects/starkraft/sim/src/main/resources/data/visuals.json) 기반 자산 정의를 production-ready manifest로 확장하기 위한 규격이다.

이 문서는 다음 문제를 해결한다.

- 스프라이트마다 기준점이 달라 월드 위 정렬이 흔들리는 문제
- 타입별 variant, shadow, effect 연동 정보가 자산 외부에 흩어지는 문제
- atlas 전환 시 로더와 렌더러가 동시에 깨지는 문제

## 현재 구조

현재 참조 타입:

- [`sim/src/main/kotlin/client/VisualAssetCatalog.kt`](/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client/VisualAssetCatalog.kt)
- [`sim/src/main/kotlin/client/GdxSpriteAssets.kt`](/Users/hammac/Projects/starkraft/sim/src/main/kotlin/client/GdxSpriteAssets.kt)

현재 `SpriteAssetRef` 필드:

- `asset`
- `scale`
- `tint`
- `teamTintStrength`

이 구조는 시작점으로는 충분하지만, 표현 품질을 올리기에는 메타데이터가 부족하다.

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

- `asset` 경로가 없으면 경고 후 fallback
- `scale <= 0` 금지
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

