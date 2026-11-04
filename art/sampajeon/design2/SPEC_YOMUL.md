# 삼파전 — R2 faction expansion: 요물 무리 (addendum to SPEC*.md)

The user decided (2026-11-04):
- Faction name: **요물 무리** (replaces 도깨비 무리). English: **YOMUL HORDE**.
- Dokkaebi keep the worker and melee units. Everything else becomes other Korean creatures.
- Hero 길달 is replaced by 구미호. 비형랑 and 두두리 stay.

The drawing rules from SPEC_HQ, SPEC_CHAR and SPEC_FACE still apply: continuous limbs (`K.tube`), deep volumetric shading, individual faces.

## Lore
귀화석이 깨어나자 산과 물에 잠들어 있던 요물들도 함께 깨어났다. 도깨비들이 내기와 씨름판을 벌여 이들을 끌어모은다. The mischievous tone stays: dokkaebi run the show. Original text only.

## Unit roster (6 units, was 5)
Every creature needs its OWN silhouette. No more blue muscular humanoids except the 방망이 장사. The goblin-fire blue (#4FB3E8) stays as the common thread (an inner glow or rim light), and each creature adds its own accent colour.

| ID | Name | English | Role | Size | Look | Mechanic (one-line card text) |
|---|---|---|---|---|---|---|
| U-D1 | 도깨비불 | Wisp | worker | 1×1 | unchanged | 귀화석을 나름 |
| U-D2 | 방망이 장사 | Club Brute | melee | 1×1 | unchanged | unchanged |
| U-D3 | 창귀 | Tiger Thrall | ranged | 1×1 | Gaunt human ghost in tattered 베옷, tiger-claw wounds, tiger-stripe shadow cast behind it, a ghostly tiger head looming over its shoulder, hollow eyes, one long arm reaching. Ranged soul-bolt. Accent: tiger amber + ash grey | 쓰러진 적을 잠시 아군으로 끌어들임 |
| U-D4 | 불가사리 | Bulgasari | heavy | 2×2 | Stocky iron-eating beast on four legs: bear/tapir-like bulk, hide of needle-sharp iron bristles, rusty plates, chewing a cannon barrel or wheel, scattered metal scraps. Accent: rust + iron | 쇠를 먹어 커짐. 불에 약함 |
| U-D5 | 어둑시니 | Eodukshini | air/scout | 1×1 | A towering shapeless mass of darkness with faint eyes, ink-wash edges dissolving upward, smaller at the base and swelling above. No limbs. Must read clearly against the dark card (use a blue rim light and a lighter card background). Accent: ink + faint violet | 쳐다볼수록 커짐. 주변 시야를 가림 |
| U-D6 | 이무기 | Imugi | late-game | 3×2 | Huge coiled serpent with heavy shaded scales, no legs, small budding horns and a pearl (여의주) it has not yet won, half risen from dark water, trying to ascend. Accent: deep teal + pearl | 승천을 노리는 최종 유닛 |

Removed: 불씨 투척꾼, 외다리 거인, 허깨비.

## Heroes
Replace 길달 with **구미호 九尾狐**. Role: 둔갑 영웅, trickster.
- **Look:** a dignified, unsettling woman in layered hanbok with nine real fox tails tipped in blue fire, faint fox ears and sharp amber eyes, holding a glowing 여우구슬. Adult and elegant. Not sexualised, no exposed-skin emphasis.
- **Pose:** mid-transformation, with a faint fox shadow behind her.
- **Skills:**
  - 여우 둔갑: 은신.
  - 여우구슬: 적 하나의 기운을 빨아들여 체력 회복.
  - 홀림: 적 하나를 잠시 조종.
- 길달 lore text, the 吉達門 gate and related captions are removed.

Rewrite "도깨비 행렬 / 주변 도깨비 아군" as "요물 행렬 / 주변 요물 아군" in 비형랑's skills. 비형랑's and 두두리's art otherwise stays.

## Source captions: state only what was verified
Web checks on 2026-11-04 give these limits. Use these captions exactly, or something more modest. Do not claim anything stronger.
- 불가사리: "민간 설화 · 쇠를 먹는 불가살이(不可殺伊)". Verified in 한국민족문화대백과사전: an unkillable creature that eats iron. Secondary sources say it is finally burned.
- 어둑시니: "민담 요괴 · 표준어 어둑서니". Original tale not verified, so do NOT cite a book.
- 창귀: "호랑이 전승 · 범에게 죽은 이의 혼령". Do not call it purely Korean or cite a Korean book; a cited early text is the Ming-dynasty 『청우기담』.
- 이무기: "민간 전승 · 용이 되지 못한 큰 뱀".
- 구미호: "민담 · 고소설 · 여우구슬 전승". Do NOT cite 『삼국유사』.
- 비형랑, 두두리: keep existing captions.

## Buildings
Keep the 7 drawn buildings, including 도깨비 소굴. Update only their small role text where it names removed units. 이무기 unlocks after 감투 창고. Heavy production stays at 씨름판.

## Renames elsewhere
"도깨비 무리" → "요물 무리", "DOKKAEBI HORDE" → "YOMUL HORDE", and "DOKKAEBI HEROES" → "YOMUL HEROES". Keep "도깨비 소굴", "도깨비불" and "도깨비" when they mean the actual creature or building.

## Process
- Back up every generator you change as `*_v4.py` first.
- Do not edit figure_kit.py, canvas.json or files outside your scope. Do not publish.
- Check: no duplicate ids, every `url(#)`/`href` resolves, tags balanced.
- Take 1× screenshots plus 2× crops, VIEW them and iterate. Save final shots to shots/v4/<Name>.png.
