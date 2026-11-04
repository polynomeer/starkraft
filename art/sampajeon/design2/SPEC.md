# 삼파전 SAMPAJEON — shared design spec (all artboards follow this)

Concept: Joseon-punk folk-tale RTS. An alternate Joseon that chose engineering over opening its ports. A survey crew mapping the whole country (in the spirit of 대동여지도) finds veins of 귀화석 (鬼火石, goblin-fire stone). Three powers fight over it in Korean mountains, rivers, rice paddies, stone walls, pine groves and tiled-roof villages. It is a 1v1 RTS on a deterministic 20 ms tick server, so the screens keep the same functional content as the old design in ../design/project (menu, server connect + resume, rooms, HUD, results + replay verification).

Original work only: nothing that resembles StarCraft (no Terran/Zerg/Protoss look-alikes, no psionic/warp, no creep). The goblins are mischievous folk goblins, not an invading swarm.

## File format (identical to ../design/project/*.dc.html)
- doctype; `<script src="./support.js">`; `<x-dc>`; `<helmet>` with Google Fonts link + style; one root div with fixed width/height and overflow hidden; at the end, `<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":W,"height":H}}'>` with `class Component extends DCLogic { renderVals() { return {}; } }`.
- Screens are 1440×900; asset sheets are 1440×960.
- Write each file through a Python generator in ../gen2/<name>.py so it can be regenerated.
- Inline SVG ids share one document namespace: prefix every id with the file's prefix (given in your brief), unique per SVG, with no duplicates. Plain-browser SVG only (gradients, filters incl. feTurbulence/feDisplacementMap, clipPath, pattern). No external images.

## Tokens
- Fonts (Google Fonts):
  - display: "Nanum Myeongjo" 800 (titles, wordmark)
  - body: "IBM Plex Sans KR" 400/500/600/700
  - data: "IBM Plex Mono" 400/500/600
  - Link: https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&family=IBM+Plex+Mono:wght@400;500;600&family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap
- UI surface (dark ink, 먹):
  - ground #16140F
  - panel #201D17
  - raised #2A2620
  - line #4A4236
  - ink (hanji) #EFE6D2
  - muted #A89E8A
- 오방색 accents:
  - 적 #C8432B
  - 황 #E2B13C
  - 청 #2F6E8E
  - 백 #EFE8D8
  - 흑 #1A1814
- States:
  - alert #E5604D
  - ok #8CC79A
  - focus ring #E2B13C
- Resources:
  - 귀화석 goblin-fire stone (ore): pale jade-cyan #9FE3D0, glow #CFF5EA
  - 산기 mountain breath (gas): mist violet #B79AE0
  - 식량 food (supply): rice #E8DDBF
- UI motifs:
  - hanji paper grain (subtle feTurbulence)
  - a red seal stamp (낙관) for the logo and confirmations
  - 문살 lattice patterns as dividers and backgrounds
  - thin 단청 bands (red / green / yellow stripes) as panel headers
  - corner brackets like wooden joinery
  - Keep it legible and game-like, not a museum page.
- Wordmark: "삼파전" in Nanum Myeongjo 800, with "SAMPAJEON" in small mono tracking and a square red seal holding "三" or "參".

## Races (faction colour + shape language)
Lighting rule for all art: one light from the top-left, blurred cast shadows, gradient faces, rim light, ambient occlusion, emissive glows. Buildings in 3/4 top-down view. Units at 128px masters, game-concept quality with dense detail.

### R1 관상감 공방 Gwansang Works (prefix r1-)
- Look: Joseon court engineers; science and gunpowder.
- Colours:
  - red #D9532E (light #F09A6E)
  - dancheong green #3E8F7A
  - brass #C9A35A
  - lacquered wood #4A2E1E / #2E1E14
- Shapes: round heaven, square earth (squares + circles).
- Units:
  - 공방 일꾼 Artisan (worker; carries tools on a back frame, uses a pick)
  - 화승총수 Matchlock (infantry)
  - 화차 Hwacha (rocket cart, ranged)
  - 거북 전차 Turtle Crawler (heavy 2×2, armoured shell with spikes, belches smoke)
  - 비거 Bigeo (flying cart, air scout)
- Buildings:
  - 관상감 본청 Observatory Hall (main 3×3)
  - 산기 증류소 Breath Still (gas 2×2)
  - 훈련원 Drill Yard (2×2)
  - 자격루 연구소 Water-clock Lab (tech 2×2)
  - 측우 망루 Rain-gauge Watchtower (defense 1×1)
  - 곳간 Granary (supply 2×2)
  - 화차 공창 Hwacha Works (vehicle 3×2)

### R2 도깨비 무리 Dokkaebi Horde (prefix r2-)
- Look: folk goblins born from goblin-fire stone; pranksters who love wagers and wrestling.
- Colours:
  - goblin-fire blue #4FB3E8 (light #A8DDF5, dark #12222E / #1B3242)
  - horn ochre #C9A35A
  - hide indigo #2B2F55
- Shapes: horns, teardrop flames, clubs. Flickering lights and smoke.
- Units:
  - 도깨비불 Wisp (worker)
  - 방망이 장사 Club Brute (melee)
  - 불씨 투척꾼 Ember Thrower (ranged)
  - 외다리 거인 One-legged Giant (heavy 2×2)
  - 허깨비 Phantom (air)
- Buildings:
  - 도깨비 소굴 Hollow Lair (main 3×3; a hollow ancient zelkova tree with blue fires)
  - 귀화 우물 Fire Well (gas 2×2)
  - 방망이 대장간 Club Forge (2×2)
  - 감투 창고 Hat Vault (tech 2×2)
  - 불씨 탑 Ember Tower (defense 1×1)
  - 장독대 Jar Terrace (supply 2×2)
  - 씨름판 Wrestling Ring (heavy production 3×2)

### R3 보부상 연단 Kite Guild (prefix r3-)
- Look: a travelling merchant guild crossing the mountains on wind, kites and sails.
- Colours:
  - yellow-sail ochre #E2B13C (light #F2D58A, dark #3A2E14)
  - indigo #3B4E8C
  - sail cloth #E8E1C8
  - rope #8A7A5A
- Shapes: triangles, shield kites (방패연: rectangle with a central round hole), sails.
- Units:
  - 짐꾼 Porter (worker with a back frame, 지게)
  - 호위무사 Escort (melee, bamboo hat)
  - 방패연 궁수 Kite Archer (ranged, tethered shield kite overhead)
  - 황포 돛배 Sail Barge (heavy 2×2 hover river boat)
  - 정찰 연 Scout Kite (air)
- Buildings:
  - 장터 본영 Market Camp (main 3×3)
  - 산기 풍로 Bellows (gas 2×2)
  - 연 공방 Kite Loft (2×2)
  - 객주 Guild Inn (tech 2×2)
  - 솟대 Sotdae (supply 1×1)
  - 봉수대 Beacon Tower (defense 1×1)
  - 나루터 Ferry Dock (heavy production 3×2)

## Canvas layout (for reference)
- Screens:
  - S1 메인 메뉴
  - S2 접속·로비
  - S3 인게임 HUD
  - S4 결과·리플레이 검증
  - S5 디자인 토큰
- Assets:
  - R1–R3 race codex
  - T 지형·배경
  - E 효과·아이콘·유닛 상태
