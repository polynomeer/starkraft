# 삼재 — hero units (addendum to SPEC.md, SPEC_HQ.md, SPEC_CHAR.md)

The user asked for hero characters, an RTS staple: one-per-army named units with skills and an aura. They should come from Korean myth, folk tales or history. Each race gets 3 heroes.

Depict historical figures respectfully and heroically, in period-plausible dress adapted to our Joseon-punk alternate history. Use no real portraits or likenesses (none exist for most of them anyway), and do not copy other games' versions of these figures. Lore lines are our own short Korean text. Cite the source in a small caption, for example "『삼국유사』 비형랑 설화" or "조선 세종대 과학자".

## Heroes
### R1 관상감 공방 (prefix h1, kit prefix h1k): HeroR1.dc.html
1. **장영실** 蔣英實, 조선 세종대 과학자 · 발명가. Role: engineer hero.
   - Look: a master craftsman in a court official's robe (청색 단령) with sleeves tied back, a brass astrolabe/혼천의 gear-staff in hand, a 측우기 slung at his side, goggles pushed up on his 사모, and a measuring rule on his belt.
   - Skills:
     - 자격루의 시각: slows time in an area for enemies.
     - 측우 관측: reveals a large area.
     - 혼천의 공명: buffs repair and build speed.
2. **최무선** 崔茂宣, 고려 말 화약 개발자 · 화통도감. Role: artillery hero.
   - Look: a stocky veteran in a soot-stained 철릭 over lamellar plates, a powder-horn bandolier, a lit 화통 (hand cannon) on his shoulder, burn-scarred gauntlets, and a fuse cord wound round his arm.
   - Skills:
     - 주화 일제 발사: a rocket barrage.
     - 화통도감: speeds up hwacha production.
     - 진포의 불길: an ignite area.
3. **이순신** 李舜臣, 조선 수군 통제사. Role: commander hero, tied to the 거북 전차.
   - Look: general's armour (두정갑 with 견철 shoulder plates and a 투구 with plume), a long sword (쌍룡검-style, but our own design), a commander's baton/flag, and a calm, stern face.
   - Skills:
     - 학익진: a formation aura that boosts allied attack.
     - 필사즉생: hero and allies near death gain armour.
     - 거북 지휘: heals and empowers turtle crawlers.

### R2 도깨비 무리 (prefix h2, kit prefix h2k): HeroR2.dc.html. All three are from 『삼국유사』 and folk tradition.
1. **비형랑** 鼻荊郞, 신라 진지왕의 혼령과 도화녀의 아들. He commands goblins and ghosts, and they built 귀교 (the ghost bridge) in one night. Role: summoner hero.
   - Look: a young Silla noble, half human and half spirit, with flowing robes, a jade 관, goblin-fire whirling around his hands and faint ghostly echoes behind him.
   - Skills:
     - 귀교 하룻밤: builds an instant bridge or wall.
     - 귀신 부리기: summons wisps.
     - 도깨비 행렬: speeds up allied dokkaebi.
2. **두두리** 豆豆里, 신라 시대부터 섬겨진 도깨비 대장장이 신 (목랑). Role: brute hero.
   - Look: a huge horned smith-goblin with a leather apron, a giant magic club 방망이 with gold studs, a "금 나와라 뚝딱" spray of gold coins, glowing forge scars, and anvil and chain.
   - Skills:
     - 금 나와라 뚝딱: generates resources.
     - 은 나와라 뚝딱: shields an ally.
     - 대장간 신령: a forge aura.
3. **길달** 吉達, 비형랑이 부린 귀신. He became a fox and fled, and was caught. Role: trickster and assassin hero.
   - Look: a lanky shapeshifter mid-transformation, half goblin and half fox, with a nine-tail wisp of blue fire, a 길달문 gate-plaque motif, and a sly grin.
   - Skills:
     - 여우 둔갑: invisibility.
     - 문루 장난: teleports through a gate.
     - 홀림: confuses an enemy.

### R3 보부상 연단 (prefix h3, kit prefix h3k): HeroR3.dc.html
1. **김정호** 金正浩, 『대동여지도』를 만든 조선 후기 지리학자. He is the in-lore discoverer of the 귀화석 veins. Role: scout and map hero.
   - Look: a weathered traveller in a 갓 and 도포 with gaiters, a huge rolled map-scroll case on his back, a 목판 woodblock in hand, a compass (윤도) on a cord, and dusty straw sandals.
   - Skills:
     - 대동여지도: map reveal.
     - 십리 눈금: allied move speed.
     - 지세 판독: terrain bonus.
2. **김만덕** 金萬德, 조선 정조대 제주 거상 · 구휼. Role: support and economy hero.
   - Look: a dignified Jeju merchant woman in a layered 치마저고리 with a 쓰개치마 or 가리마, an abacus (주판) and ledger, rice sacks and a coin purse, and a 갈옷 accent.
   - Skills:
     - 구휼미: heals and resupplies allies.
     - 객주 장부: speeds up income.
     - 바닷바람 교역: a remote trade.
3. **김유신** 金庾信, 신라 장군. During 비담의 난 he flew a burning kite (연) by night to restore morale, a legend recorded in 『삼국사기』. Role: kite general.
   - Look: a Silla general in lamellar armour (찰갑) with a plumed helmet, a sword, a cape, and a huge blazing shield-kite (방패연) with burning tail flying behind him on a cord.
   - Skills:
     - 불타는 연: a morale aura that also makes enemies flee.
     - 연줄 비상: a jump or dash.
     - 화랑의 맹세: buffs allies.

## Artboard layout: 1440×960, same frame and chrome style as that race's RaceRx sheet
- **Header band:** "영웅 · HEROES" plus the race name, with the race palette and crest.
- **Three hero panels side by side**, each about 460×800. Each panel has:
  - A large painterly **full-body hero figure** about 380–440px tall, drawn with figure_kit (SPEC_CHAR) at the highest detail of the project. Include:
    - full limb anatomy;
    - layered costume with folds;
    - per-plate armour speculars;
    - a hero aura or rim light in the race colour;
    - an isometric diorama base;
    - a contact shadow plus cast shadow.

    The figure is in a dynamic signature pose, not standing still.
  - A **name plate:** 한글 name in Nanum Myeongjo 800, hanja and role, plus a source caption.
  - A **painted portrait medallion**: a head-and-shoulders bust.
  - **Stats:** 체력, 공격, 사거리 and 이동 as small bars.
  - **3 skill icons** (painted, framed, with hotkeys Q/W/E), each with its name and a one-line effect.
  - **A lore line:** 1–2 sentences, original text.
- Keep text contrast at 4.5:1 or better. Use no swastika motifs and nothing StarCraft-like.
- The density and detail must clearly exceed the regular units. These are the showpieces.

## Process
Same as SPEC_CHAR:
- Write a new generator `gen2/hero_rX.py` that may import figure_kit and your race's existing helper modules. Do not edit figure_kit.py or other artboards' generators; subclass or copy instead.
- Run the checks: no duplicate ids, tags balanced, every `url(#)` resolves.
- Take screenshots at 1× and as 2× crops, and iterate. Save them to shots/hero/HeroRX.png.
- Do not publish, and do not edit canvas.json.
