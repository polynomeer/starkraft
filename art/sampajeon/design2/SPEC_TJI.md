# 삼파전 — 천·지·인 reorganisation (addendum to SPEC*.md)

The user approved on 2026-11-04: the three factions become 천(天), 지(地), 인(人), so each differs in body, building method and how it dies. Use StarCraft only as a benchmark for how deep the differences go. Copy no looks, units or mechanics, and nothing psionic or warp-like.

All drawing rules from SPEC_HQ, SPEC_CHAR and SPEC_FACE still apply: `K.tube` continuous limbs, deep shading, individual faces.

| | 인(人) 조선 공방 JOSEON WORKS | 지(地) 요물 무리 YOMUL HORDE | 천(天) 오방 신당 OBANG SHRINE |
|---|---|---|---|
| Files | RaceR1, HeroR1 | RaceR2, HeroR2 (DONE, untouched) | RaceR3, HeroR3 |
| Identity | People and wood/iron machines; 관상감 + 보부상 merged | Creatures woken from earth and water | Shamanic generals and spirits |
| Expanding | Workers build; carts and kites haul supplies | Wagers draw creatures in | 금줄 extends the 성역 |
| Strong when | Dug in, massed firepower at range | Attrition: grows as it fights | Strong inside 성역, weak outside |
| On death | Wreckage remains, salvageable | Scatters into smoke or iron | Spirit lingers briefly |
| Colour/shape | Red + brass; squares and wheels | Goblin-fire blue; asymmetric curves | 오방색 + white hanji, gold accent (#E2B13C); vertical posts and flags |

Keep the existing UI accent per slot: slot 1 red, slot 2 blue, slot 3 gold. Faction header labels show 人 / 地 / 天.

## 인 — 조선 공방 (RaceR1.dc.html, HeroR1.dc.html; id prefixes unchanged)
Lore: 관상감의 기술자와 전국을 잇는 보부상이 손잡은 사람의 세력. Original text only.

Units (7 cards):
- U-G1 공방 일꾼 (keep)
- U-G2 화승총수 (keep)
- U-G3 방패연 궁수 (move from the old R3)
- U-G4 **태엽 목우 Clockwork Puppet** (NEW)
- U-G5 화차 (keep)
- U-G6 거북 전차 (keep)
- U-G7 비거 (keep)

태엽 목우: a wooden soldier, 2 heads taller than a man, with brass clockwork visible through the open chest, a drum or gong motif, peg joints and a spear. Its idea comes from the time-announcing wooden figures of the 자격루. Machine joints MAY be visible here, but the shading stays deep. Mechanic: 부서지면 잔해가 남아 일꾼이 되살림. Caption: "자격루의 시보 인형에서 착안한 창작 유닛".

Dropped: 짐꾼, 호위무사, 황포 돛배, 정찰 연.

Buildings (7): 관상감 본청, 산기 증류소, 훈련원 (trains gunners and kite archers), 자격루 연구소, 측우 망루, **장터 객주** (replaces 곳간, supply; reuse the 장터 본영 awning art), 화차 공창.

Combat frames: keep the hwacha volley. Add wreckage to the death frame.

Heroes: 장영실, 이순신 (keep, art unchanged), **김만덕** (moved from the old R3 page). Remove 최무선. Header reads "영웅 · 조선 공방". Keep 김만덕's own face, art and text, with id prefixes renamed so nothing collides.

Old drawings are preserved in `gen2/race_r3_kite.py` and `gen2/hero_r3_kite.py`. Import or copy from those, never from race_r3.py / hero_r3.py, which the 천 fork is rewriting. Importing them may write output files, so redirect or guard that. They must not overwrite design2/project.

## 천 — 오방 신당 (RaceR3.dc.html, HeroR3.dc.html; id prefixes unchanged)
Lore: 귀화석이 깨어나 땅이 어지러워지자, 마을을 지키던 신령들이 금줄을 치고 일어선다. Original text only.

**Respect rule:** 무속 is a living religion. Draw it with dignity. Never make it comic, villainous, grotesque or sexualised. Avoid Japanese shrine looks: no torii silhouette, no shimenawa-thick ropes (금줄 is a thin left-twisted straw rope with paper, pine or charcoal). Do not imitate real shrines or real people.

Every unit has its own silhouette and they share a vertical, posts-and-flags feel. No giwa halls in this faction.

| ID | Name | English | Role | Size | Look | Mechanic |
|---|---|---|---|---|---|---|
| U-T1 | 금줄지기 | Rope Keeper | worker | 1×1 | Small white-clad figure in hanji robes, carrying a coil of 금줄 and stakes | 금줄을 쳐 성역을 넓힘 |
| U-T2 | 저승사자 | Death Envoy | melee | 1×1 | Tall pale envoy, black 도포 and 갓, a scroll (명부) and rope | 쓰러뜨린 적의 혼을 거둠 |
| U-T3 | 무당 | Mudang | ranged/support | 1×1 | Dignified woman in layered 무복, 오방색 sashes, 방울 and 부채 in hand, mid-dance | 방울 소리로 아군 치유·적 약화 |
| U-T4 | 장승 | Jangseung | heavy | 2×2 | A pair: 천하대장군 and 지하여장군, carved wood giants, bulging eyes, tusks, carved inscription, walking on root-like feet | 성역 안에서 진지가 됨 |
| U-T5 | 솟대 오리 | Sotdae Duck | air/scout | 1×1 | A carved wooden duck in flight, pole trailing, ribbons | 하늘로 기원을 나르는 정찰 |
| U-T6 | 오방신장 | Obang Generals | late | 3×2 | Five armoured generals, one each in 청·적·황·백·흑, flags raised, centre one larger | 다섯 방위 오라 |

Buildings (7):
- 서낭당: main 3×3. Sacred tree with cloth strips, stone cairn, small shrine.
- 약수터: gas 2×2.
- 굿당: production 2×2. A tented ritual ground with flags.
- 저승문: 2×2, produces 저승사자. Dark gate with vertical arrow slats, not a torii.
- 오방기단: tech 2×2. Five tall flags on a stone platform.
- 해태상: defense 1×1. Stone guardian.
- 누석단: supply 1×1. Stone cairn.

Show 금줄 boundaries around buildings as the territory marker.

Combat frames: 무당's bell attack in 4 frames, death (spirit lingers), destruction, 채집, 반납.

Heroes (HeroR3):
1. **바리공주**, 巫祖神. Robed young woman in shaman dress carrying a medicine-water flask and flowers, guiding faint souls. Dignified, compassionate, unsettlingly calm. Skills: 약수 (대규모 치유), 망자 인도 (쓰러진 아군의 혼을 돌려보냄), 저승길 (이동).
2. **강림도령**. A bold young envoy in a red robe with a rope and red summons scroll, mid-stride. Skills: 적패지 (지목한 적을 표식), 오랏줄 (적 속박), 염라 체포 (강력 단일 처치).
3. **산군** 山君. A huge mountain tiger with a white-bearded mountain elder riding or beside it, in the style of a 산신 scene (no copying a specific painting). Skills: 포효, 산신의 가호 (성역 범위 확대), 호환 (돌진). Lore may note the 창귀 of the 요물 무리 as its old enemy.

## Source captions: only what was verified. Use these or weaker.
- 오방신장: "무속 신격 · 오방을 지키는 장군신"
- 장승: "마을 수호 · 천하대장군 · 지하여장군"
- 저승사자: "무속 · 저승차사"
- 솟대: "마을 신앙 · 장대 위의 새"
- 금줄지기: "금줄 · 부정을 막는 왼새끼"
- 해태상: "궁궐 앞 상상의 동물 · 불을 누른다는 속설". The fire story is disputed, so keep the word 속설.
- 바리공주: "서사무가 〈바리공주〉 · 무조신"
- 강림도령: "제주 무가 〈차사본풀이〉"
- 산군: "산신 신앙 · 호랑이를 이르던 말"

Do not cite books beyond these.

## Renames elsewhere (screens fork)
- "관상감 공방" → "조선 공방"; "GWANSANG WORKS" → "JOSEON WORKS".
- "보부상 연단" → "오방 신당"; "KITE GUILD" → "OBANG SHRINE".
- Keep "관상감 본청" as a building name. Lobby quip "요물이냐 보부상이냐" must be rewritten to fit the new factions.
- Lobby and Results race busts/medallions: slot 3 becomes a dignified 무당 bust, slot 1 stays. Lobby mentions 보부상 preview text; rewrite it for 오방 신당.
- Tokens: faction rows, map marks. Keep colours; add 天地人 labels.
- Main faction banners, showing 人 / 地 / 天.

## Process
- Back up every generator you change as `*_v5.py` first.
- Do not edit figure_kit.py, yomul_figs.py, race_r2.py, hero_r2.py or canvas.json. Do not publish.
- Check: no duplicate ids, every `url(#)`/`href` resolves, tags balanced, no stale names.
- Take 1× screenshots plus 2× crops, VIEW them, iterate. Save to shots/v5/<Name>.png.
