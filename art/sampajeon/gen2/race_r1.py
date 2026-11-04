"""Generate RaceR1.dc.html — race codex R1 · 조선 공방 Joseon Works (삼파전 SAMPAJEON), high-fidelity pass.

2.5D isometric painterly SVG art with ornate lacquered-wood UI chrome.
Usage: python3 race_r1.py [out_path]
Drawing code lives in r1_lib.py (iso math, textures, hanok), r1_units.py and r1_scenes.py. All ids are prefixed r1-.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from r1_lib import (ground_patch, RED, RED_L, GREEN, BRASS, BRASS_L, WOOD, ORE, ORE_L, INK, MUTED, defs, svg, rect, circ, ell, path, poly,
                    line, g, u, f, P, hanok)
import r1_scenes as S
from r1_units import card_unit
from r1_figs import KK
from r3_bridge import R as KITE, to_prefix

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "design2", "project", "RaceR1.dc.html")

MONO = "font-family: 'IBM Plex Mono', monospace"
SERIF = "font-family: 'Nanum Myeongjo', serif"
GOLD = "#E9C77A"
GOLD_TXT = f"color: {GOLD}; text-shadow: 0 1px 0 #000, 0 0 2px #000, 0 2px 4px rgba(0,0,0,0.6)"
LACQUER = ("background: linear-gradient(180deg, #2E1E14 0%, #22160E 55%, #1A110A 100%); border: 1px solid #0A0604; "
           "box-shadow: inset 0 0 0 1px #6B4A2A, inset 0 0 0 3px #2A1A10, inset 0 0 0 4px rgba(201,163,90,0.45), inset 0 8px 18px rgba(255,220,160,0.05), 0 6px 14px rgba(0,0,0,0.65)")
ART_BG = ("background: radial-gradient(ellipse at 40% 35%, #2C2416 0%, #17120C 70%, #0E0A06 100%); "
          "box-shadow: inset 0 0 0 1px #0A0604, inset 0 0 0 2px rgba(201,163,90,0.35), inset 0 0 24px rgba(0,0,0,0.85)")


ART_W = 166


def archer_art():
    """방패연 궁수 on this sheet's own ground, at the same figure scale as the 화승총수."""
    k, (bx, by) = KITE.shield_kite(44, 36, 30, 38, -14)
    return (ground_patch(130, 168, 120, 30, "field", 0.9) + [KITE.sprite(k),
            f'<path d="M{f(bx)} {f(by)} Q72 112 104 110" stroke="#E8DDB8" stroke-width="0.7" fill="none" opacity="0.9"></path>',
            KITE.k_place(112, 190, 1.1, KITE.archer_body())])


def corner_svg(rot):
    """Brass L-shaped corner fitting with rivets and a cloud scroll."""
    body = [path("M1 1 L17 1 L17 5 L5 5 L5 17 L1 17 Z", u("brass"), stroke="#3A2810", stroke_width=0.6),
            path("M5 5 q5 0 6 4 q-3 -1 -4 1 q-1 -2 -2 -1 Z", "#8A6A30"),
            circ(3, 3, 1.1, BRASS_L, stroke="#3A2810", stroke_width=0.4), circ(14, 3, 0.8, BRASS_L), circ(3, 14, 0.8, BRASS_L)]
    pos = {0: "top: -3px; left: -3px", 90: "top: -3px; right: -3px", 180: "bottom: -3px; right: -3px", 270: "bottom: -3px; left: -3px"}[rot]
    return (f'<svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" style="position: absolute; {pos}; transform: rotate({rot}deg)">'
            + "".join(body) + "</svg>")


CORNERS = "".join(corner_svg(r) for r in (0, 90, 180, 270))


def cloud_scroll(flip=False):
    """구름문 ornament for section titles."""
    tr = ' style="transform: scaleX(-1)"' if flip else ""
    d = ("M2 10 q0 -7 7 -7 q6 0 6 6 q0 4 -4 4 q-3 0 -3 -3 q0 -2 2 -2 M15 9 q4 -6 10 -3 q4 3 1 7 q-2 2 -4 0 "
         "M26 11 l28 0")
    return (f'<svg width="56" height="16" viewBox="0 0 56 16" aria-hidden="true"{tr}><path d="{d}" fill="none" stroke="{BRASS}" stroke-width="1.4" '
            f'stroke-linecap="round"></path><circle cx="54" cy="11" r="1.6" fill="{BRASS_L}"></circle></svg>')


def section_title(text, note):
    return (f'<div style="display: flex; align-items: center; gap: 10px; height: 22px">{cloud_scroll()}'
            f'<h2 style="margin: 0; {SERIF}; font-size: 18px; font-weight: 800; {GOLD_TXT}">{text}</h2>'
            f'<span style="font-size: 12px; color: {MUTED}">{note}</span>'
            f'<span style="flex: 1; height: 1px; background: linear-gradient(90deg, rgba(201,163,90,0.6), rgba(201,163,90,0))"></span></div>')


def tag(text, bg=RED):
    return f'<span style="{MONO}; font-size: 10px; color: #FFF4E0; background: {bg}; padding: 0 5px; line-height: 13px; border: 1px solid #F09A6E">{text}</span>'


def unit_card(code, foot, name, en, role, desc, kind, new=False, note=None, vb="0 0 260 200", art_body=None):
    art = svg(ART_W, 176, name, art_body() if art_body else card_unit(kind), vb=vb).replace(f'width="{ART_W}"', 'width="100%"', 1)
    noted = f'<span style="font-size: 10.5px; line-height: 1.3; color: {MUTED}">{note}</span>' if note else ""
    return f"""
    <article style="position: relative; display: flex; flex-direction: column; gap: 3px; padding: 7px; {LACQUER}; min-width: 0">{CORNERS}
      <div style="height: 176px; display: flex; align-items: center; justify-content: center; overflow: hidden; {ART_BG}">{art}</div>
      <div style="display: flex; align-items: center; gap: 6px; white-space: nowrap"><span style="{MONO}; font-size: 10.5px; color: {RED_L}">{code} · {foot}</span>{tag("NEW") if new else ""}</div>
      <b style="{SERIF}; font-size: 18px; font-weight: 800; line-height: 1.1; white-space: nowrap; {GOLD_TXT}">{name}</b>
      <span style="font-size: 11px; color: {MUTED}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{en} · {role}</span>
      <span style="font-size: 11.5px; line-height: 1.38; color: #D8CDB4">{desc}</span>{noted}
    </article>"""


def bld_card(code, foot, name, en, role, art_fn, vb="0 0 190 150"):
    art = svg(176, 136, name, art_fn(), vb=vb)
    return f"""
    <figure style="position: relative; margin: 0; display: flex; flex-direction: column; gap: 5px; padding: 7px; {LACQUER}; min-width: 0">{CORNERS}
      <div style="height: 136px; display: flex; align-items: center; justify-content: center; overflow: hidden; {ART_BG}">{art}</div>
      <figcaption style="display: flex; flex-direction: column; gap: 1px; padding: 0 2px">
        <span style="{MONO}; font-size: 11px; color: {RED_L}">{code} · {foot}</span>
        <span style="display: flex; align-items: baseline; gap: 6px; white-space: nowrap"><b style="{SERIF}; font-size: 15px; font-weight: 800; {GOLD_TXT}">{name}</b><span style="font-size: 11px; color: {MUTED}; overflow: hidden; text-overflow: ellipsis">{en}</span></span>
        <span style="font-size: 11.5px; color: #CFC5AE; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{role}</span>
      </figcaption>
    </figure>"""


def strip_cell(title, sub, art_fn, accent=RED_L):
    art = svg(160, 124, title, art_fn(), vb="2 6 160 124")
    return f"""
    <figure style="position: relative; margin: 0; display: flex; flex-direction: column; gap: 4px; padding: 6px; {LACQUER}; min-width: 0">{CORNERS}
      <div style="height: 124px; display: flex; align-items: center; justify-content: center; overflow: hidden; {ART_BG}">{art}</div>
      <figcaption style="font-size: 12.5px; text-align: center; line-height: 1.3"><b style="color: {accent}">{title}</b><br><span style="font-size: 11px; color: {MUTED}">{sub}</span></figcaption>
    </figure>"""


def swatch(color, name):
    return (f'<span style="display: flex; flex-direction: column; gap: 3px; width: 58px"><span style="height: 22px; background: {color}; '
            f'box-shadow: inset 0 0 0 1px rgba(0,0,0,0.5), 0 0 0 1px {BRASS}"></span><span style="font-size: 11px; color: {INK}">{name}</span>'
            f'<span style="{MONO}; font-size: 9.5px; color: {MUTED}">{color}</span></span>')


def crest():
    """Medallion: brass ring, cloud border, armillary over a square earth plate."""
    c = (50, 50)
    body = [circ(52, 54, 46, "#000", opacity=0.6, filter=u("shadow-l")),
            circ(50, 50, 46, u("brass"), stroke="#3A2810", stroke_width=1),
            circ(50, 50, 41, u("medal"), stroke="#6E5222", stroke_width=1)]
    for k in range(24):
        a = math.tau * k / 24
        body.append(circ(50 + math.cos(a) * 43.6, 50 + math.sin(a) * 43.6, 0.9, "#3A2810"))
    body += [rect(30, 30, 40, 40, RED, transform="rotate(0 50 50)", stroke="#3A1008", stroke_width=1.2, filter=u("tex")),
             rect(34, 34, 32, 32, "none", stroke=BRASS, stroke_width=0.8)]
    body.append(circ(50, 50, 16, u("ore-glow"), filter=u("glow-l")))
    for rx, ry, rot in ((24, 24, 0), (24, 8, -24), (24, 8, 24), (8, 24, 0)):
        body.append(ell(50, 50, rx, ry, "none", stroke=u("brass"), stroke_width=2, transform=f"rotate({rot} 50 50)"))
    body += [circ(50, 50, 5, u("ore"), filter=u("glow")), circ(48.6, 48.4, 1.6, "#FFFFFF", opacity=0.9)]
    return f'<svg width="92" height="92" viewBox="0 0 100 100" role="img" aria-label="조선 공방 문장">{"".join(body)}</svg>'


def header_banner():
    """Carved roof-eave silhouette band behind the header."""
    ps = []
    for k in range(0, 1441, 24):
        ps.append(f"M{k} 6 q12 4 24 0")
    tiles = "".join(f'<path d="{d}" fill="none" stroke="#3A2E24" stroke-width="3"></path>' for d in ps)
    db = "".join(f'<rect x="{k}" y="10" width="12" height="4" fill="{RED if (k // 16) % 3 == 0 else (GREEN if (k // 16) % 3 == 1 else "#E2B13C")}"></rect>'
                 for k in range(0, 1440, 16))
    return (f'<svg width="1440" height="16" viewBox="0 0 1440 16" aria-hidden="true" style="position: absolute; left: 0; top: 0">'
            f'<rect width="1440" height="16" fill="#120C08"></rect>{tiles}{db}</svg>')


PERSON_VB = "30 -8 204 214"
MACHINE_VB = "8 36 244 164"
units = "".join([
    unit_card("U-G1", "1×1", "공방 일꾼", "Artisan", "일꾼", "귀화석을 캐고 건물을 짓습니다. 부서진 기계의 잔해도 되살립니다.", "artisan", vb=PERSON_VB),
    unit_card("U-G2", "1×1", "화승총수", "Matchlock", "보병", "진지를 굳히고 멀리서 일제 사격을 퍼붓는 주력.", "gunner", vb=PERSON_VB),
    unit_card("U-G3", "1×1", "방패연 궁수", "Kite Archer", "원거리", "방패연으로 시야를 넓히고 불화살을 쏩니다.", "archer", vb=PERSON_VB, art_body=archer_art),
    unit_card("U-G4", "1×1", "태엽 목우", "Clockwork Puppet", "기계 보병", "부서지면 잔해가 남아 일꾼이 되살립니다.", "puppet", new=True,
              note="자격루 시보 인형에서 착안한 창작", vb="46 -8 172 204"),
    unit_card("U-G5", "1×2", "화차", "Hwacha", "원거리 공성", "신기전 60발을 한 번에 쏘는 수레. 사수 둘이 밀고, 장전이 느림.", "hwacha", vb=MACHINE_VB),
    unit_card("U-G6", "2×2", "거북 전차", "Turtle Crawler", "중장갑", "철침 박힌 육각 귀갑과 연기 뿜는 용머리. 옆구리 총안으로 근접 제압.", "turtle", vb=MACHINE_VB),
    unit_card("U-G7", "1×1 공중", "비거", "Bigeo", "공중 정찰", "대나무 살에 한지 날개를 단 비행 수레.", "bigeo", vb=MACHINE_VB),
])

blds = "".join([
    bld_card("B-G1", "3×3", "관상감 본청", "Observatory Hall", "본진 · 귀화석 반입 · 일꾼 생산", S.b_hall),
    bld_card("B-G2", "2×2", "산기 증류소", "Breath Still", "산기 분출구 위 · 가스 채취", S.b_still),
    bld_card("B-G3", "2×2", "훈련원", "Drill Yard", "화승총수 · 방패연 궁수 생산", S.b_drill),
    bld_card("B-G4", "2×2", "자격루 연구소", "Water-clock Lab", "기술 연구 · 물시계", S.b_clock),
    bld_card("B-G5", "1×1", "측우 망루", "Rain-gauge Tower", "방어 · 총통 · 넓은 시야", S.b_tower),
    bld_card("B-G6", "2×2", "장터 객주", "Market Inn", "식량 +8 · 수레와 연이 물자 운반", KITE.b_market, vb="0 0 200 160"),
    bld_card("B-G7", "3×2", "화차 공창", "Hwacha Works", "화차 · 태엽 목우 · 거북 전차 · 비거", S.b_works),
])

strip = "".join([
    strip_cell("1 · 점화", "포가 섬광 + 초연", S.fx_ignite),
    strip_cell("2 · 비행", "불꼬리 신기전 5발", S.fx_flight),
    strip_cell("3 · 작렬", "연쇄 폭발 + 파편", S.fx_burst),
    strip_cell("4 · 잔연", "그을음 + 불씨", S.fx_smoke),
    strip_cell("유닛 사망", "잔해가 남아 일꾼이 회수", S.fx_death, "#E5604D"),
    strip_cell("건물 파괴", "그을린 기와 · 불길", S.fx_destroyed, "#E5604D"),
    strip_cell("채집", "곡괭이 + 결정 파편", S.fx_harvest, ORE),
    strip_cell("반입", "객주 문 앞 → +8", S.fx_dropoff, ORE),
])

PAGE_BG = ("background-color: #16140F; background-image: radial-gradient(ellipse at 22% 0%, rgba(217,83,46,0.12), transparent 50%), "
           "radial-gradient(ellipse at 90% 100%, rgba(159,227,208,0.06), transparent 50%), "
           "repeating-linear-gradient(0deg, rgba(255,255,255,0.012) 0 2px, transparent 2px 4px)")

KIT_DEFS = ('<svg width="0" height="0" style="position: absolute; width: 0; height: 0; overflow: hidden" aria-hidden="true" focusable="false"><defs>'
            + KK.defs_svg() + KITE.DEFS + KITE.K.defs_svg() + "</defs></svg>")

body = f"""<div style="width: 1440px; height: 960px; box-sizing: border-box; {PAGE_BG}; color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; padding: 26px 28px 18px; display: flex; flex-direction: column; gap: 8px; overflow: hidden; position: relative">
{defs()}
{KIT_DEFS}
{header_banner()}
  <header style="position: relative; display: flex; align-items: center; gap: 18px; padding: 8px 14px 8px 8px; {LACQUER}">{CORNERS}
    {crest()}
    <div style="display: flex; flex-direction: column; gap: 4px; min-width: 0">
      <span style="{MONO}; font-size: 11.5px; letter-spacing: 0.14em; color: {MUTED}">RACE CODEX R1 · 삼파전 SAMPAJEON · 人 사람과 기계</span>
      <div style="display: flex; align-items: baseline; gap: 14px"><span style="display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; align-self: center; background: {RED}; border: 1px solid {RED_L}; {SERIF}; font-size: 28px; font-weight: 800; color: #FFF4E0">人</span><h1 style="margin: 0; {SERIF}; font-weight: 800; font-size: 44px; line-height: 1; {GOLD_TXT}">조선 공방</h1><span style="{MONO}; font-weight: 600; font-size: 17px; letter-spacing: 0.18em; color: {RED_L}">JOSEON WORKS</span></div>
      <span style="font-size: 13.5px; color: #D8CDB4">관상감의 기술자와 팔도를 잇는 보부상이 손잡은 사람의 세력. 진지를 굳히고, 수레와 연으로 물자를 나르며, 화력으로 밀어붙입니다.</span>
    </div>
    <div style="margin-left: auto; display: flex; flex-direction: column; gap: 8px; align-items: flex-end">
      <div style="display: flex; gap: 8px">{swatch(RED, "주홍 옻칠")}{swatch(RED_L, "등불")}{swatch(GREEN, "단청 녹")}{swatch(BRASS, "놋쇠")}{swatch(WOOD, "옻칠 목재")}{swatch(ORE, "귀화석")}</div>
      <span style="font-size: 12px; color: {MUTED}">형태 언어 <b style="color: {GOLD}">붉은 옻칠과 놋쇠</b> · 사각형과 바퀴 · 쓰러지면 잔해가 남음</span>
    </div>
  </header>

  {section_title("유닛 · 7", "사람과 나무·쇠 기계 · 몸체 방향 = 이동, 총구·포가 방향 = 공격 · 붉은 갑옷과 놋쇠가 진영 식별")}
  <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr 1.14fr 1.14fr 1.14fr; gap: 9px">{units}
  </div>

  {section_title("건물 · 7", "2:1 아이소메트릭 · 남서 벽 밝게, 남동 벽 그늘 · 낱장 기와 · 단청 처마 · 문살 창")}
  <div style="display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 10px">{blds}
  </div>

  {section_title("전투 · 경제 연출", "대표 공격 4프레임(화차 일제 사격) · 사망/파괴 · 일꾼 채집과 반입")}
  <div style="display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 9px">{strip}
  </div>
</div>"""

HEAD = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>조선 공방 종족 에셋</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
body{margin:0;background:#16140F}
a{color:#F09A6E}a:hover{color:#FFC0A0}
</style>
</helmet>
"""

TAIL = """
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1440,"height":960}}'>
class Component extends DCLogic {
  renderVals() {
    return {};
  }
}
</script>
</body>
</html>
"""

html = to_prefix(HEAD + body + TAIL, "r1b")
with open(OUT, "w") as fh:
    fh.write(html)
print("wrote", OUT, len(html))
