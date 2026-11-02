> **현재 구현 상태 (코드 기준 메모)**
>
> 이 문서는 외부 제안 원문을 보존한 것이다. 실제 실행 설계는 [presentation-quality-architecture.md](presentation-quality-architecture.md)를 따르며, 아래는 원문 제안 대비 현재 libGDX 클라이언트(`sim/src/main/kotlin/client/`)의 상태다.
>
> - 채택하지 않음/보류: 2.5D·3D 전환, Unity/Godot 클라이언트 교체, 영웅/스킬 기반 vertical slice. 현재는 2D procedural 렌더 경로를 유지한다.
> - sim/presentation 분리: 완료. sim은 snapshot NDJSON만 내보내고, 클라이언트(`GdxClientRuntime`)가 snapshot 차이에서 피격·사망·완료 이벤트를 추출해 연출한다. sim 쪽 presentation 이벤트 스트림(`UnitDamaged` 등)은 없다.
> - shader, FrameBuffer post-processing, particle, decal, lighting: 미착수.
> - 오디오: 런타임 합성 tone 기반 command/combat/death/alert/completion 효과음과 pitch·gain 순환, key별 cooldown만 있다. bus, priority, spatial, ambience, music은 미착수 ([audio-feedback-contract.md](audio-feedback-contract.md)).
>
> Quality Epic 상태:
>
> | 항목 | 상태 |
> | --- | --- |
> | VQ-01 Rendering architecture | 미착수 (단일 `GdxWorldRenderer`) |
> | VQ-02 Camera | 부분 (glide center, edge pan, map clamp, zoom은 잠금) |
> | VQ-03 Lighting / VQ-05 Material / VQ-10 Post processing | 미착수 |
> | VQ-04 Shadow | 부분 (구조물 단색 drop shadow) |
> | VQ-06 Animation | 부분 (bob, stride, recoil, critical shake 등 state-driven offset) |
> | VQ-07 VFX / VQ-09 Impact | 부분 (muzzle flash, impact spark, damage flash, death burst + remains, completion flash) |
> | VQ-08 Projectile | 미착수 |
> | VQ-11 Terrain | 부분 (패턴 tile tone, edge, prop) |
> | VQ-12 UI polish | 대부분 완료 (theme 토큰, 고정 HUD 슬롯, command deck/selection panel 재구성) |
> | AQ-04 Variations | 부분 (pitch/gain 순환만, sample rotation 없음) |
> | AQ-05 Combat SFX | 부분 (marine/zergling/melee/ranged별 tone) |
> | AQ-01~03, AQ-06~10 | 미착수 |

맞습니다. 지금 단계에서 Codex를 계속 돌려도 **“기능은 많아지는데 게임처럼 보이지 않는 현상”**이 생기기 쉽습니다. 이유는 상용 게임의 체감 품질에서 코드가 차지하는 비중보다 **아트 에셋 + 애니메이션 + VFX + 라이팅 + 사운드 + UI 연출**의 비중이 훨씬 크기 때문입니다.

특히 현재처럼 스타크래프트 계열 RTS에 영웅/장수와 액션 조작을 섞으려는 프로젝트라면, 이제부터는 `기능 개발`과 `게임 표현 개발`을 완전히 분리하는 것이 좋습니다. libGDX 자체는 Shader, FrameBuffer/Post-processing, 2D/3D Particle, SpriteBatch, TextureAtlas 등의 기반을 충분히 제공합니다. 즉 엔진이 표현을 못 하는 것은 아닙니다. 다만 Unity/Unreal처럼 그 표현을 쉽게 만들어주는 제작환경이 적기 때문에 직접 구축해야 할 부분이 많습니다. ([libGDX](https://libgdx.com/wiki/?utm_source=chatgpt.com))

제가 이 프로젝트라면 다음 방향으로 갑니다.

## 1. 우선 "그래픽 개선"을 멈추고 비주얼 타깃부터 정합니다

가장 먼저 하나의 전투 화면을 목표 이미지처럼 정의해야 합니다.

예를 들어 현재 게임을 이런 느낌으로 정하는 것입니다.

> **StarCraft 2 전투 가독성
>
> - Warcraft 3의 영웅성
> - League of Legends의 스킬 이펙트
> - 천년의 신화/거상의 장수 시스템
> - 현대적인 2.5D 그래픽**

여기서 중요한 것은 **상용제품 수준 = 고해상도 그래픽**이 아니라는 점입니다.

오히려 아래가 통일되어야 합니다.

- 카메라 각도
- 유닛 비율
- 건물 크기
- 지형 텍스처 스타일
- 그림자 방향
- 색상 팔레트
- 공격 이펙트 크기
- 피격 이펙트
- UI 톤
- 폰트
- 사운드 스타일

Codex에 이것이 없으면 매번 각각의 기능을 “적당히” 구현하게 됩니다.

그래서 결과물이 기술적으로는 맞는데 **하나의 게임처럼 느껴지지 않습니다.**

------

# 2. 현재 구조라면 2D보다 `2.5D`를 강하게 추천합니다

순수 2D 스프라이트 RTS도 가능하지만, 개인 개발자가 현대적인 상용 품질을 만들기에는 오히려 어려울 수 있습니다.

저라면:

```text
3D Environment
3D Units
3D Buildings

        ↓

Fixed Perspective Camera
RTS Camera

        ↓

2D UI
2D Skill Indicators
2D Floating Text
2D Selection UI
```

형태로 갑니다.

즉 실제 월드는 3D이고 카메라를 거의 고정하여 2D RTS처럼 보이게 만드는 것입니다.

libGDX에도 모델, 애니메이션, Material, ModelBatch, Blender 모델 import 및 3D particle 관련 기능이 존재합니다. ([libGDX](https://libgdx.com/wiki/graphics/3d/3d-particle-effects?utm_source=chatgpt.com))

이 방식의 장점이 큽니다.

```text
스프라이트 2D

idle 8방향
walk 8방향
attack 8방향
skill 8방향
death 8방향

→ 모든 방향 프레임 제작 필요
```

반면 3D는

```text
Model
 ├ Idle
 ├ Walk
 ├ Attack
 ├ Skill
 └ Death

Camera rotation / Model rotation
```

으로 해결됩니다.

장수가 스킬을 쓰고 대규모 전투까지 생각한다면 특히 3D 쪽이 좋습니다.

------

# 3. 그래픽 품질은 다음 7개 레이어로 나눠야 합니다

Codex에게 단순히

> Improve graphics.

라고 하면 안 됩니다.

렌더링을 명확하게 시스템으로 나누는 것이 좋습니다.

```text
Rendering
│
├── WorldRenderer
│
├── TerrainRenderer
│
├── UnitRenderer
│
├── BuildingRenderer
│
├── ShadowRenderer
│
├── VfxRenderer
│
├── DecalRenderer
│
├── LightingRenderer
│
├── PostProcessingRenderer
│
└── UiRenderer
```

그리고 프레임도:

```text
World
 ↓
Terrain
 ↓
Buildings
 ↓
Units
 ↓
Shadows
 ↓
Projectiles
 ↓
Particles
 ↓
Decals
 ↓
Lighting
 ↓
Post Processing
 ↓
HUD
```

처럼 명확하게 구성합니다.

libGDX의 FrameBuffer는 화면을 직접 출력하지 않고 texture에 렌더링할 수 있기 때문에, 이 구조 위에 post-processing을 넣을 수 있습니다. ([libGDX](https://libgdx.com/wiki/graphics/opengl-utils/frame-buffer-objects?utm_source=chatgpt.com))

------

# 4. 상용 게임처럼 보이는 데 가장 효과가 큰 것은 VFX입니다

의외로 모델 퀄리티보다 훨씬 중요합니다.

현재가 예를 들어:

```text
Marine attacks
→ bullet moves
→ enemy HP -= 5
```

라면 플레이어는 그냥 프로토타입으로 느낍니다.

상용 게임은 사실상:

```text
Marine fire

1. attack animation
2. muzzle flash
3. weapon sound
4. projectile tracer
5. projectile glow
6. impact spark
7. impact smoke
8. hit animation
9. slight knock/recoil
10. blood/dust/metal particle
11. floating damage
12. contextual sound
```

정도가 동시에 발생합니다.

### 예를 들어 화염구 스킬

현재:

```text
fireball.png
→ 이동
→ 충돌
```

개선:

```text
Cast
 ├ character animation
 ├ hand glow
 ├ casting circle
 ├ charge particle
 └ cast sound

Projectile
 ├ core
 ├ glow
 ├ trail
 ├ sparks
 └ looping sound

Impact
 ├ flash
 ├ explosion
 ├ shockwave
 ├ smoke
 ├ debris
 ├ ground scorch decal
 ├ camera shake
 └ impact sound
```

여기서 갑자기 게임 퀄리티가 한 단계 올라갑니다.

libGDX에는 현재 새로운 GDX Particle Editor가 있고 additive blending 등의 효과를 만들 수 있습니다. 공식 문서에서도 atlas와 pooling을 이용한 particle 최적화를 권장합니다. ([libGDX](https://libgdx.com/wiki/graphics/2d/2d-particleeffects?utm_source=chatgpt.com))

------

# 5. Shader를 반드시 도입하는 것이 좋습니다

Shader를 도입하면 Codex도 꽤 잘 구현할 수 있는 영역입니다.

libGDX 역시 GLSL shader를 직접 사용할 수 있습니다. ([libGDX](https://libgdx.com/wiki/graphics/opengl-utils/shaders?utm_source=chatgpt.com))

최소한 다음은 넣는 것이 좋습니다.

```text
Unit Shader
 ├ hit flash
 ├ selection outline
 ├ team color
 └ invisibility

Terrain Shader
 ├ texture blending
 ├ fog
 └ water animation

Skill Shader
 ├ dissolve
 ├ distortion
 ├ glow
 └ outline

Post Processing
 ├ bloom
 ├ color grading
 ├ vignette
 ├ screen flash
 └ optional FXAA
```

특히 **Bloom + Color Grading + Hit Flash** 정도만 넣어도 느낌이 크게 달라집니다.

------

# 6. 애니메이션이 그래픽보다 중요합니다

싸구려처럼 보이는 가장 큰 원인 중 하나가 이것입니다.

예를 들어 이동을:

```java
position += velocity * delta;
```

만 하면 아무리 모델이 좋아도 프로토타입처럼 보입니다.

대신 캐릭터가:

```text
Idle
Walk
Run
Attack
AttackRecover
Cast
Hit
Death
```

상태를 가져야 합니다.

그리고 공격도:

```text
Attack Start
    ↓
Wind-up
    ↓
Hit Frame
    ↓
Damage/Event
    ↓
Recovery
```

로 만들어야 합니다.

즉

```java
damage()
```

를 호출하는 것이 아니라

```text
Animation event
      ↓
Attack impact frame
      ↓
Combat event
      ↓
Damage
      ↓
VFX
      ↓
Sound
```

이어야 합니다.

이것이 **게임 Feel**의 핵심입니다.

------

# 7. 사운드는 완전히 별도의 시스템으로 다시 설계하는 게 좋습니다

사운드를

```java
sound.play();
```

정도로 구현하면 절대로 상용 게임 느낌이 나지 않습니다.

최소한 다음 구조가 필요합니다.

```text
AudioEngine
│
├── MusicBus
├── AmbienceBus
├── SfxBus
├── UiBus
├── VoiceBus
│
├── SpatialAudio
├── SoundPool
├── AudioPriority
├── AudioVariation
└── AudioDuck
```

예를 들어 검 공격도:

```text
sword_attack.wav
```

한 파일만 반복하면 매우 싸게 들립니다.

대신:

```text
Sword Swing
 ├ swing_01
 ├ swing_02
 ├ swing_03
 └ swing_04

Sword Hit Metal
 ├ hit_metal_01
 ├ hit_metal_02
 └ hit_metal_03

Sword Hit Flesh
 ├ flesh_01
 ├ flesh_02
 └ flesh_03
```

중에서 랜덤 재생해야 합니다.

Pitch도 ±3~5% 정도 변형합니다.

그러면 같은 공격을 30번 해도 반복감이 크게 줄어듭니다.

------

# 8. RTS라면 사운드 우선순위가 특히 중요합니다

100개 유닛이 전투하면:

```text
100 attacks
100 impacts
30 deaths
10 skills
```

같은 상황이 생깁니다.

전부 재생하면 그냥 소음입니다.

따라서:

```text
Priority 100
Hero skill

Priority 90
Hero voice

Priority 80
Major explosion

Priority 60
Selected unit

Priority 40
Nearby battle

Priority 20
Distant battle
```

같은 시스템이 필요합니다.

그리고 카메라 거리 기반으로:

```text
0~10m       full volume
10~30m      attenuated
30~60m      low
60m+        ignore
```

합니다.

이런 것이 실제 **Soundscape**를 만듭니다.

------

# 9. 배경 사운드를 넣으면 체감이 굉장히 달라집니다

전투가 없는 상황에서도:

```text
Map Ambience
│
├ wind
├ insects
├ birds
├ machinery
├ water
└ distant battle
```

같은 ambient layer가 계속 존재해야 합니다.

그리고 전투가 시작되면:

```text
Exploration music
      ↓
Enemy nearby
      ↓
Tension layer
      ↓
Battle music
      ↓
Hero battle
      ↓
High intensity layer
```

식으로 Dynamic Music을 적용할 수 있습니다.

------

# 10. 에셋은 Codex가 아니라 별도의 파이프라인으로 생산해야 합니다

여기가 핵심입니다.

Codex는:

```text
게임 시스템
Renderer
Animation State
VFX runtime
Audio engine
Asset loader
Shader
Tool
Pipeline
```

을 만드는 데 사용합니다.

반면

```text
Character Model
Building Model
Terrain Texture
Animation
Icon
Portrait
VFX Texture
Music
Voice
Sound Effect
```

은 별도로 공급해야 합니다.

즉:

```text
                  Codex
                    │
                    ↓
               Game Runtime
                    ↑
                    │
       ┌────────────┼────────────┐
       │            │            │
     Models       VFX          Audio
       │            │            │
    Blender      Particle      DAW/SFX
```

가 되어야 합니다.

이것을 안 하고 Codex가 placeholder image를 계속 만들어내게 하면 아무리 개발해도 프로토타입 느낌에서 벗어나기 어렵습니다.

------

# 11. 그리고 아주 중요한 선택지가 하나 있습니다

현재 프로젝트에서 **simulation과 presentation을 분리**하는 것을 권합니다.

기존에 만들어온 `sim`은 유지합니다.

```text
starcraft-project

sim
 ├ combat
 ├ movement
 ├ pathfinding
 ├ economy
 ├ abilities
 └ AI

client
 ├ rendering
 ├ audio
 ├ input
 ├ camera
 ├ ui
 └ vfx
```

이렇게 만드는 것입니다.

그리고

```text
Simulation

UnitMoved
UnitAttacked
ProjectileSpawned
UnitDamaged
UnitKilled
AbilityCast

        ↓ events

Presentation

Animation
Particles
Sound
Camera
UI
```

로 연결합니다.

이렇게 해야 나중에 그래픽을 완전히 갈아엎어도 게임 로직은 건드리지 않습니다.

------

# 12. 더 과감한 선택: 클라이언트 엔진만 변경

이것도 충분히 고려할 만합니다.

현재 목표가 정말

> **혼자 또는 소규모 인원으로 상용 게임 수준까지 만들기**

라면 저는 장기적으로

```text
Java Simulation
      +
Unity / Godot Presentation
```

도 검토하겠습니다.

libGDX가 성능이나 기능적으로 부족해서가 아닙니다.

문제는 **생산성**입니다.

libGDX에서는 직접 만들어야 하는 것들이:

```text
VFX workflow
Material editor
Shader workflow
Animation state machine
Scene editor
Lighting tools
Post processing
Audio tooling
Level editor
```

등 상당히 많습니다.

Unity/Unreal/Godot에서는 편집기에서 바로 보면서 조정할 수 있습니다.

게임의 최종 품질은 오히려 이런 **iteration 속도**에서 크게 차이 납니다.

------

# 제가 가장 추천하는 방식

지금 전체 프로젝트를 갈아엎지는 않겠습니다.

먼저 **Vertical Slice** 하나만 만듭니다.

```text
1 Map

2 factions

각 진영:
    3 normal units
    1 hero
    2 buildings

Hero:
    basic attack
    3 skills

게임:
    movement
    combat
    death
```

여기까지만 놓고 **상용 수준까지 polish**합니다.

그리고 그 화면에만:

```text
✓ 고품질 모델
✓ terrain
✓ animation
✓ shadow
✓ lighting
✓ shader
✓ particle
✓ decal
✓ camera shake
✓ sound
✓ ambient
✓ music
✓ UI
```

를 전부 넣습니다.

게임 규모를 늘리는 것은 그 다음입니다.

------

## 특히 Codex 개발 방식도 바꾸는 것이 좋습니다

기존:

```text
Phase 1
Phase 2
Phase 3
...
Phase 30

게임 완성
```

보다는 앞으로는:

```text
VISUAL QUALITY EPIC

VQ-01 Rendering architecture
VQ-02 Camera
VQ-03 Lighting
VQ-04 Shadow
VQ-05 Material
VQ-06 Animation
VQ-07 VFX
VQ-08 Projectile
VQ-09 Impact
VQ-10 Post processing
VQ-11 Terrain
VQ-12 UI polish

AUDIO QUALITY EPIC

AQ-01 Audio architecture
AQ-02 Mixer / buses
AQ-03 Spatial sound
AQ-04 Variations
AQ-05 Combat SFX
AQ-06 Ability SFX
AQ-07 Ambient
AQ-08 Dynamic music
AQ-09 Voice
AQ-10 Audio priority
```

처럼 **Quality Epic**을 별도로 두는 것이 좋습니다.

그리고 각 작업에는 반드시

```text
Before screenshot
After screenshot

or

Before video
After video
```

를 남기게 해야 합니다.

Codex가 코드만 보고 “완료”라고 판단하지 못하게 만드는 것입니다.

------

## 이 프로젝트에서 제가 권하는 최종 구조는 이렇습니다

```text
                    GAME
                     │
       ┌─────────────┴─────────────┐
       │                           │
 Simulation                  Presentation
       │                           │
 deterministic                 libGDX
       │                           │
 ┌─────┼─────┐         ┌──────────┼──────────┐
 │     │     │         │          │          │
AI   Combat Economy  Renderer    Audio       UI
 │                   │
Pathfinding        RenderGraph
                   │
         ┌─────────┼─────────┐
         │         │         │
       World      VFX      PostFX
         │
      Animation
```

그리고 **Codex는 오른쪽의 시스템을 만들게 하고, 에셋 자체는 전문 에셋/Blender/AI/사운드 툴을 통해 공급**하는 방향이 좋습니다.

제가 보기에 현재 프로젝트에서 가장 효과가 큰 다음 작업은 기능 추가가 아닙니다.

**“Visual & Audio Vertical Slice”를 하나 만드는 것**입니다.

`한 맵 + 보병 하나 + 장수 하나 + 건물 하나 + 스킬 하나`만 남겨놓고, 그것을 **실제 출시 게임처럼 보이고 들릴 때까지** 완성하는 것입니다. 그 수준이 나온 뒤 동일한 파이프라인으로 나머지 콘텐츠를 확장하면 됩니다.

원하시면 다음 단계로는 제가 **현재 스타그래프트 프로젝트를 Codex가 직접 분석한 뒤, 그래픽·애니메이션·VFX·사운드를 상용 수준으로 끌어올리는 전용 Codex 마스터 프롬프트**를 만들어드리는 것이 가장 좋습니다. 단순한 한 번짜리 프롬프트가 아니라 `Visual Quality → Audio → Animation → VFX → Polish` 순으로 스스로 프로젝트를 검사하고 반복 개선하도록 설계할 수 있습니다.