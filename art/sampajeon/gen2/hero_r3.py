#!/usr/bin/env python3
"""Generate HeroR3.dc.html — 삼파전 hero codex, slot 3: 天 오방 신당.

Heroes: 바리공주 (support, 무조신), 강림도령 (duelist, 저승차사), 산군 (mountain tiger with the elder).
Figures come from obang_figs; race_r3 supplies the chrome. Every id from race_r3 (r3- / r3k-)
is renamed to h3- / h3k- at the end so the sheet has its own namespace.
Deterministic: all scatter uses seeded RNGs.
"""
import math
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import race_r3 as R  # noqa: E402
import obang_figs as O  # noqa: E402
from figure_kit import Mat  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "HeroR3.dc.html")
K = R.K
f = R.f
OCHRE, OCHRE_L, OCHRE_D = R.OCHRE, R.OCHRE_L, R.OCHRE_D
GROUND, INK, MUTED = R.GROUND, R.INK, R.MUTED
OBANG = O.OBANG
stroke = O.stroke


def P(p):
    return f"{f(p[0])} {f(p[1])}"


def smooth(pts_, closed=True):
    return K._smooth(list(pts_), closed)


def stars(rng, w, h, n=60):
    out = ""
    for _ in range(n):
        x, y = rng.uniform(0, w), rng.uniform(0, h * 0.55)
        r = rng.choice([0.5, 0.6, 0.8, 1.1])
        out += f'<circle cx="{f(x)}" cy="{f(y)}" r="{r}" fill="#F6F0E0" opacity="{f(rng.uniform(0.4, 0.95))}"></circle>'
    return out


def ridge(y, amp, seed, col, op=1.0, w=440, step=14):
    rng = random.Random(seed)
    pts_ = [(-10, y)]
    x = -10
    while x < w + 20:
        x += step + rng.uniform(-4, 6)
        pts_.append((x, y - rng.uniform(0, amp)))
    d = f"M-10 {f(y + 200)} L" + " L".join(P(p) for p in pts_) + f" L{w + 30} {f(y + 200)} Z"
    return f'<path d="{d}" fill="{col}" opacity="{op}"></path>'


def soft_ridge(y, amp, seed, col, op=1.0, w=440):
    rng = random.Random(seed)
    pts_ = [(-20, y + 10)]
    for i in range(9):
        pts_.append((i * w / 8, y - rng.uniform(0.2, 1) * amp))
    pts_.append((w + 20, y + 10))
    return f'<path d="{smooth(pts_, closed=False)} L{w + 20} 600 L-20 600 Z" fill="{col}" opacity="{op}"></path>'


def pine(x, y, s=1.0, col="#1E2A1A"):
    out = stroke(f"M{f(x)} {f(y)} q{f(-2 * s)} {f(-14 * s)} {f(1 * s)} {f(-30 * s)}", "#2A1A10", 1.6 * s)
    for k in range(4):
        yy = y - 12 * s - k * 6 * s
        out += f'<ellipse cx="{f(x + (k % 2 * 2 - 1) * 4 * s)}" cy="{f(yy)}" rx="{f((9 - k) * s)}" ry="{f(2.6 * s)}" fill="{col}"></ellipse>'
    return out


def hero_ring(cx, cy, rx, ry):
    """Hero selection ring on the ground: double gilt ellipse, cloud ticks, glow."""
    out = f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx + 6)}" ry="{f(ry + 2)}" fill="url(#h3-ringglow)"></ellipse>'
    out += f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx)}" ry="{f(ry)}" fill="none" stroke="{OCHRE}" stroke-width="2.2" opacity="0.9"></ellipse>'
    out += f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx - 6)}" ry="{f(ry - 2)}" fill="none" stroke="{OCHRE_L}" stroke-width="0.8" stroke-dasharray="3 5" opacity="0.8"></ellipse>'
    for k in range(8):
        a = k * math.tau / 8 + 0.2
        x, y = cx + math.cos(a) * rx, cy + math.sin(a) * ry
        out += f'<path d="M{f(x - 4)} {f(y)} L{f(x)} {f(y - 2)} L{f(x + 4)} {f(y)} L{f(x)} {f(y + 2)} Z" fill="{OCHRE_L}" stroke="#5A3A10" stroke-width="0.5"></path>'
    return out


def diorama(cx, cy, s, seed, top="grass"):
    """Thick isometric base slab under the hero; top = grass | stone | basalt."""
    rng = random.Random(seed)
    iso = R.Iso(cx, cy - s * 1.5, s)
    lf = [iso.P(0, 3, 0), iso.P(3, 3, 0), iso.P(3, 3, -0.35), iso.P(0, 3, -0.35)]
    rf = [iso.P(3, 0, 0), iso.P(3, 3, 0), iso.P(3, 3, -0.35), iso.P(3, 0, -0.35)]
    out = f'<polygon points="{R.pts(lf)}" fill="url(#r3-stone-lit)" filter="url(#r3-tx-stone)"></polygon>'
    out += f'<polygon points="{R.pts(rf)}" fill="url(#r3-stone-sh)" filter="url(#r3-tx-stone)"></polygon>'
    for k in range(1, 3):
        out += stroke(f"M{P(iso.P(0, 3, -0.12 * k))} L{P(iso.P(3, 3, -0.12 * k))} L{P(iso.P(3, 0, -0.12 * k))}", "#2A2418", 0.6, 0.5)
    pts_top = R.pts(iso.face_t(0, 0, 3, 3, 0))
    if top == "grass":
        out += f'<polygon points="{pts_top}" fill="url(#r3-grass)" filter="url(#r3-tx-grass)"></polygon>'
        out += R.ground_patch(iso, 0, 0, 3, 3, rng, yard=(0.7, 0.7, 2.3, 2.3), n_tuft=140)
    elif top == "water":
        out += f'<polygon points="{pts_top}" fill="#16242E"></polygon>'
        for k in range(26):
            p = iso.P(rng.uniform(0.2, 2.8), rng.uniform(0.2, 2.8))
            out += f'<ellipse cx="{f(p[0])}" cy="{f(p[1])}" rx="{f(rng.uniform(5, 18))}" ry="{f(rng.uniform(1, 2.6))}" fill="none" stroke="#9AC4DC" stroke-width="0.7" opacity="{f(rng.uniform(0.25, 0.6))}"></ellipse>'
    else:
        out += f'<polygon points="{pts_top}" fill="#24221F"></polygon>'
        for i in range(90):
            p = iso.P(rng.uniform(0.1, 2.9), rng.uniform(0.1, 2.9))
            rr = rng.uniform(2.6, 5.8)
            out += K.ball(p[0], p[1], rr, Mat("#3A3634", "#7A7470", "#0A0908", "#4A5058", spec_op=0.2, gloss=0.2), outline=False, ry=rr * 0.62)
    return out


def icon_frame(body, key, gid_bg):
    return (f'<svg width="46" height="46" viewBox="0 0 46 46" aria-hidden="true">'
            f'<rect x="1" y="1" width="44" height="44" rx="5" fill="url(#h3-iconrim)"></rect>'
            f'<rect x="4" y="4" width="38" height="38" rx="3" fill="url(#{gid_bg})"></rect>'
            f'<g clip-path="url(#h3-iconclip)">{body}</g>'
            f'<rect x="4" y="4" width="38" height="38" rx="3" fill="none" stroke="#000" stroke-width="1" opacity="0.6"></rect>'
            f'<path d="M5 22 V6 Q5 5 6 5 H40" stroke="#FFF6D8" stroke-width="0.8" fill="none" opacity="0.45"></path>'
            f'<rect x="31" y="31" width="13" height="13" rx="2" fill="#1A0E06" stroke="{OCHRE}" stroke-width="0.8"></rect>'
            f'<text x="37.5" y="41.2" font-family="IBM Plex Mono, monospace" font-size="9.5" font-weight="600" fill="#F6DE9A" text-anchor="middle">{key}</text></svg>')



# ================================================================ skill icons (46×46; inner field 4..42)

def ic_water():
    return (f'<path d="M23 9 C31 20 34 25 34 30 A11 11 0 0 1 12 30 C12 25 15 20 23 9 Z" fill="url(#h3-icwater)" stroke="#0E2430" stroke-width="1"></path>'
            f'<path d="M17 30 Q18 36 24 37" fill="none" stroke="#FFF" stroke-width="1.6" stroke-linecap="round" opacity="0.8"></path>'
            f'<circle cx="23" cy="23" r="16" fill="url(#h3-icwarm)" opacity="0.35"></circle>')


def ic_soul():
    return (O.stroke("M6 40 Q18 34 40 16", "#E2B13C", 1.4, 0.7) + '<path d="M26 8 C33 12 33 22 28 30 C27 26 23 28 21 33 C17 24 18 13 26 8 Z" fill="#E8F6FF" stroke="#4A7088" stroke-width="0.8"></path>'
            + '<circle cx="12" cy="31" r="2.4" fill="#E8F6FF" opacity="0.7"></circle><circle cx="18" cy="35" r="1.6" fill="#E8F6FF" opacity="0.5"></circle>')


def ic_road():
    return ('<path d="M18 42 L22 18 H24 L28 42 Z" fill="#CFC8B0" opacity="0.8"></path>'
            '<path d="M10 42 V14 M36 42 V14 M8 15 H38" stroke="#1A1612" stroke-width="3" fill="none"></path>'
            + "".join(f'<path d="M{x} 15 V7" stroke="#C8CCD4" stroke-width="1.2"></path>' for x in (12, 17, 23, 29, 34))
            + '<circle cx="23" cy="26" r="7" fill="url(#h3-icwarm)" opacity="0.8"></circle>')


def ic_summons():
    return ('<path d="M14 9 H33 L32 38 H13 Z" fill="#C82E20" stroke="#3A0A06" stroke-width="1"></path>'
            '<path d="M11 9 H36 M10 38 H35" stroke="#2A1A0C" stroke-width="2.6" stroke-linecap="round"></path>'
            + "".join(f'<path d="M17 {y} H29" stroke="#FFF2DC" stroke-width="1.5"></path>' for y in (15, 20, 25, 30)))


def ic_rope():
    d = "M8 34 q10 8 22 2 q10 -6 4 -15 q-7 -9 -17 -4 q-9 5 -3 12 q6 6 13 1 q5 -4 1 -8"
    return stroke(d, "#2A1A0C", 4) + stroke(d, "#D0503E", 2.6) + f'<path d="{d}" fill="none" stroke="#7A1E14" stroke-width="2.6" stroke-dasharray="1.2 2.6"></path>'


def ic_arrest():
    d = "M8 30 Q23 44 38 30"
    return ('<path d="M11 26 V12 L17 18 L23 9 L29 18 L35 12 V26 Z" fill="url(#h3-icgold)" stroke="#3A2408" stroke-width="1"></path>'
            '<rect x="11" y="25" width="24" height="4" fill="#C9902E" stroke="#3A2408" stroke-width="0.7"></rect>'
            + stroke(d, "#2A1A0C", 3.6) + stroke(d, "#D0503E", 2.2))


def ic_roar():
    arcs = "".join(f'<path d="M{20 + k * 5} {23 - 8 - k * 3} Q{27 + k * 6} 23 {20 + k * 5} {23 + 8 + k * 3}" fill="none" stroke="#F6DE9A" stroke-width="1.6" opacity="{0.95 - k * 0.22}"></path>' for k in range(4))
    return ('<circle cx="15" cy="23" r="9.6" fill="#CC8A2C" stroke="#2A1408" stroke-width="1"></circle>'
            '<path d="M8 17 l4 3 M10 13 l3.4 4 M22 17 l-4 3 M20 13 l-3.4 4" stroke="#160A04" stroke-width="1.4"></path>'
            '<circle cx="11.5" cy="21" r="1.2" fill="#160A04"></circle><circle cx="18.5" cy="21" r="1.2" fill="#160A04"></circle>'
            '<path d="M10.5 26 Q15 32 19.5 26 Z" fill="#3A0E08"></path>' + arcs)


def ic_blessing():
    return ('<circle cx="23" cy="20" r="14" fill="url(#h3-icwarm)"></circle>'
            '<path d="M4 42 L17 18 L24 29 L30 22 L42 42 Z" fill="url(#h3-icmount)" stroke="#121A10" stroke-width="0.8"></path>'
            '<path d="M17 18 L13 26 L17 24 L20 27 Z" fill="#EFE8D8" opacity="0.8"></path>'
            '<ellipse cx="23" cy="39" rx="17" ry="3" fill="none" stroke="#E8D090" stroke-width="1.2" stroke-dasharray="2 2"></ellipse>')


def ic_charge():
    return "".join(f'<path d="M{13 + k * 8} 8 Q{9 + k * 8} 24 {16 + k * 8} 39 Q{15 + k * 8} 22 {17 + k * 8} 8 Z" fill="url(#h3-icsteel)" stroke="#2A1408" stroke-width="0.6"></path>' for k in range(3))


HEROES = [
    dict(code="H-T1", name="바리공주", hanja="巫祖神", role="지원 · 인도 영웅", src="서사무가 〈바리공주〉 · 무조신",
         lore="버려진 공주가 저승길을 걸어 약수를 길어 왔다. 이제 그는 망자의 곁에서 마지막 길을 함께 걷는다.",
         stats=[("체력", 460, 900), ("공격", 10, 40), ("사거리", 6, 8), ("이동", 2.8, 4)],
         skills=[("Q", ic_water, "h3-icbg-teal", "약수", "넓은 범위의 아군을 크게 치유합니다.", "45초"),
                 ("W", ic_soul, "h3-icbg-ink", "망자 인도", "쓰러진 아군의 혼을 전장으로 돌려보냅니다.", "90초"),
                 ("E", ic_road, "h3-icbg-ink", "저승길", "지정한 지점으로 저승길을 열어 이동합니다.", "30초")]),
    dict(code="H-T2", name="강림도령", hanja="差使", role="차사 · 결투 영웅", src="제주 무가 〈차사본풀이〉",
         lore="염라대왕까지 붙들어 왔다는 이야기가 전하는 차사. 붉은 적패지에 이름이 오르면 길을 피할 수 없다.",
         stats=[("체력", 680, 900), ("공격", 36, 40), ("사거리", 3, 8), ("이동", 3.6, 4)],
         skills=[("Q", ic_summons, "h3-icbg-red", "적패지", "지목한 적에게 표식을 남겨 위치를 드러냅니다.", "12초"),
                 ("W", ic_rope, "h3-icbg-ochre", "오랏줄", "적 하나를 잠시 묶어 움직이지 못하게 합니다.", "18초"),
                 ("E", ic_arrest, "h3-icbg-red", "염라 체포", "표식이 남은 적 하나에게 강력한 일격을 가합니다.", "80초")]),
    dict(code="H-T3", name="산군", hanja="山君", role="산신 · 돌격 영웅", src="산신 신앙 · 호랑이를 이르던 말",
         lore="산의 주인으로 모셔진 호랑이와 그를 탄 산신. 호랑이의 이름을 팔고 다니는 창귀를 산군은 용서하지 않는다.",
         stats=[("체력", 880, 900), ("공격", 32, 40), ("사거리", 1, 8), ("이동", 3.2, 4)],
         skills=[("Q", ic_roar, "h3-icbg-ochre", "포효", "주변 적의 공격을 잠시 약화합니다.", "25초"),
                 ("W", ic_blessing, "h3-icbg-teal", "산신의 가호", "성역 범위를 넓히고 그 안의 아군을 지킵니다.", "60초"),
                 ("E", ic_charge, "h3-icbg-red", "호환", "지정 방향으로 돌진해 부딪힌 적을 쓰러뜨립니다.", "22초")]),
]


def art_bari():
    rng = random.Random(41)
    W, H = 440, 420
    bg = (f'<rect width="{W}" height="{H}" fill="url(#h3-sky1)"></rect>' + stars(rng, W, H, 44)
          + '<circle cx="340" cy="84" r="58" fill="url(#h3-moon)"></circle><circle cx="340" cy="84" r="23" fill="#EEF0E2"></circle>'
          + soft_ridge(190, 56, 3, "#2A3A52", 0.8) + soft_ridge(226, 52, 5, "#1C2A3E", 0.95)
          + f'<rect x="0" y="240" width="{W}" height="180" fill="url(#h3-river)"></rect>')
    for k in range(20):  # moonlight and ripples on the river of the other world
        y = 262 + k * 7
        x = 340 - k * 2 + rng.uniform(-20, 20)
        bg += f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rng.uniform(18, 50))}" ry="1.1" fill="#D8E8F0" opacity="{f(rng.uniform(0.2, 0.5))}"></ellipse>'
    bg += f'<rect x="0" y="215" width="{W}" height="60" fill="url(#h3-haze)"></rect>'
    # souls she leads: a quiet line receding across the water, none of them frightening
    souls = "".join(O.soul(K, x, y, s, op) for x, y, s, op in ((362, 300, 1.25, 0.9), (394, 268, 0.95, 0.72), (64, 306, 1.15, 0.82), (34, 262, 0.82, 0.62), (102, 256, 0.72, 0.55)))
    body = bg + souls + diorama(220, 372, 46, 101, top="water")
    for x, y in ((122, 372), (312, 382), (182, 400), (262, 402)):
        body += O.flower(K, x, y, "#F2E6DC", 1.5)
    body += hero_ring(220, 372, 62, 18)
    body += '<ellipse cx="220" cy="230" rx="118" ry="170" fill="url(#h3-aura)"></ellipse>'
    body += K.cast_shadow(226, 376, 56, 12, 0.6)
    body += '<use href="#h3-fig1" transform="translate(220 378) scale(2.32)"></use>'
    return body


def art_gangnim():
    rng = random.Random(42)
    W, H = 440, 420
    bg = (f'<rect width="{W}" height="{H}" fill="url(#h3-sky2)"></rect>'
          + '<circle cx="100" cy="150" r="80" fill="url(#h3-sun2)"></circle>'
          + f'<path d="M0 262 L60 248 L128 190 Q168 176 208 192 L290 250 L360 258 L440 240 V420 H0 Z" fill="#2C2320"></path>'
          + f'<path d="M128 190 Q168 176 208 192 L196 200 Q168 190 140 200 Z" fill="#14100E"></path>'
          + soft_ridge(300, 28, 9, "#191512", 1.0))
    # low basalt field wall
    for i in range(22):
        x = -8 + i * 21 + rng.uniform(-3, 3)
        for row in range(3):
            y = 306 - row * 11 + rng.uniform(-1.5, 1.5)
            r = rng.uniform(8, 11.5)
            bg += K.ball(x + row * 8, y, r, Mat("#26221F", "#4E4A46", "#060505", "#32383F", spec_op=0.06, gloss=0.05), outline=False, ry=r * 0.66)
    body = bg + diorama(212, 374, 48, 102, top="basalt")
    body += hero_ring(212, 372, 62, 18)
    body += '<ellipse cx="210" cy="220" rx="130" ry="170" fill="url(#h3-aura)"></ellipse>'
    body += K.cast_shadow(218, 378, 62, 12, 0.62)
    body += '<use href="#h3-fig2" transform="translate(208 380) scale(1.84)"></use>'
    return body


def art_sangun():
    rng = random.Random(43)
    W, H = 440, 420
    bg = (f'<rect width="{W}" height="{H}" fill="url(#h3-sky3)"></rect>' + stars(rng, W, H, 34)
          + '<circle cx="86" cy="86" r="60" fill="url(#h3-moon)"></circle><circle cx="86" cy="86" r="26" fill="#F4EFD8"></circle>'
          + f'<path d="M0 240 L60 196 L110 214 L196 118 L250 176 L300 140 L380 208 L440 188 V420 H0 Z" fill="#34434A"></path>'
          + f'<path d="M196 118 L176 142 L196 134 L210 148 L220 142 Z M300 140 L288 154 L302 150 L316 158 Z" fill="#D8DEDC" opacity="0.85"></path>'
          + soft_ridge(256, 54, 5, "#22302E", 1.0)
          + f'<rect x="0" y="222" width="{W}" height="70" fill="url(#h3-haze)"></rect>'
          + ridge(310, 34, 9, "#141C18", 1.0, step=12))
    for x, y, s in ((24, 312, 1.5), (58, 316, 1.2), (392, 316, 1.5), (424, 312, 1.25), (346, 318, 1.0)):
        bg += pine(x, y, s, "#121C16")
    body = bg + diorama(220, 382, 50, 103, top="grass")
    body += hero_ring(220, 384, 82, 22)
    body += '<ellipse cx="220" cy="240" rx="170" ry="150" fill="url(#h3-aura)"></ellipse>'
    body += '<use href="#h3-fig3" transform="translate(210 386) scale(1.42)"></use>'
    return body




def medallion(fig_id, head_xy, scale, bg_grad):
    hx, hy = head_xy
    tx, ty = 44 - hx * scale, 50 - hy * scale
    return (f'<svg width="88" height="88" viewBox="0 0 88 88" aria-hidden="true">'
            f'<circle cx="44" cy="44" r="43" fill="url(#h3-medrim)"></circle>'
            f'<circle cx="44" cy="44" r="37" fill="url(#{bg_grad})"></circle>'
            f'<g clip-path="url(#h3-medclip)"><use href="#{fig_id}" transform="translate({f(tx)} {f(ty)}) scale({f(scale)})"></use>'
            f'<circle cx="44" cy="44" r="37" fill="url(#h3-medvig)"></circle></g>'
            f'<circle cx="44" cy="44" r="37" fill="none" stroke="#1A0E06" stroke-width="1.6"></circle>'
            + "".join(f'<circle cx="{f(44 + math.cos(a) * 40)}" cy="{f(44 + math.sin(a) * 40)}" r="1.1" fill="#FFF0C0" opacity="0.8"></circle>' for a in [k * math.tau / 24 for k in range(24)])
            + '</svg>')


def stat_rows(stats):
    out = ""
    for label, v, mx in stats:
        pct = max(6, min(100, v / mx * 100))
        val = f"{v:.1f}" if isinstance(v, float) else str(v)
        out += (f'<div style="display: grid; grid-template-columns: 44px 1fr 34px; align-items: center; gap: 6px; font-size: 11.5px; font-weight: 600; color: #4A3820">'
                f'<span>{label}</span>'
                f'<span style="height: 7px; background: #2A1E12; box-shadow: inset 0 1px 2px #000, 0 1px 0 #FFF8E4"><span style="display: block; height: 100%; width: {f(pct)}%; '
                f'background: linear-gradient(180deg, #F6DE9A, #C9902E 60%, #7A5214); box-shadow: inset 0 1px 0 #FFF6D8"></span></span>'
                f'<span style="{R.MONO}; font-size: 11px; color: #2A1E12; text-align: right">{val}</span></div>')
    return out


def skill_rows(skills):
    out = ""
    for key, fn, bgid, name, desc, cd in skills:
        out += (f'<div style="display: grid; grid-template-columns: 46px 1fr; gap: 9px; align-items: center">{icon_frame(fn(), key, bgid)}'
                f'<div style="display: flex; flex-direction: column; gap: 1px; min-width: 0">'
                f'<div style="display: flex; justify-content: space-between; align-items: baseline; gap: 6px"><b style="{R.SERIF}; font-size: 15px; font-weight: 800; color: #1E140A">{name}</b>'
                f'<span style="{R.MONO}; font-size: 10.5px; font-weight: 600; color: #8A2A18">{cd}</span></div>'
                f'<span style="font-size: 12px; line-height: 1.35; color: #3A2C1C">{desc}</span></div></div>')
    return out


def hero_panel(h, art_fn, fig_id, head_xy, med_scale, med_bg, idx):
    title = (f"margin: 0; {R.SERIF}; font-weight: 800; font-size: 30px; line-height: 1; "
             "color: #2A1A0C; text-shadow: 0 1px 0 #FFF8E4")
    return f"""
    <article style="{R.FRAME}; min-width: 0">{R.corner_svgs()}
      <div style="position: relative; height: 420px; {R.ART}; display: block">
        <svg width="100%" height="420" viewBox="0 0 440 420" preserveAspectRatio="xMidYMid slice" role="img" aria-label="{h['name']} 전신">{art_fn()}</svg>
        <span style="position: absolute; left: 10px; top: 10px; {R.MONO}; font-size: 11px; font-weight: 600; letter-spacing: 0.12em; color: #F6DE9A; padding: 3px 8px; background: rgba(20,12,6,0.78); box-shadow: inset 0 0 0 1px #8A6A3A">{h['code']} · 영웅</span>
        <span style="position: absolute; right: 10px; top: 10px; font-size: 12px; font-weight: 700; color: #1E140A; padding: 3px 9px; background: linear-gradient(180deg, #F6DE9A, #C9902E); box-shadow: 0 1px 3px rgba(0,0,0,0.6)">{h['role']}</span>
      </div>
      <div style="padding: 10px 14px 12px; display: flex; flex-direction: column; gap: 9px; flex: 1; {R.HANJI}">
        <div style="display: grid; grid-template-columns: 88px 1fr; gap: 12px; align-items: center">
          {medallion(fig_id, head_xy, med_scale, med_bg)}
          <div style="display: flex; flex-direction: column; gap: 5px; min-width: 0">
            <div style="display: flex; align-items: baseline; gap: 8px"><h3 style="{title}">{h['name']}</h3><span style="{R.SERIF}; font-size: 16px; font-weight: 700; color: #6A4A28">{h['hanja']}</span></div>
            <span style="font-size: 11.5px; font-weight: 600; color: #8A2A18">{h['src']}</span>
            {stat_rows(h['stats'])}
          </div>
        </div>
        <div style="height: 1px; background: linear-gradient(90deg, transparent, #8A6A3A, transparent)"></div>
        <div style="display: flex; flex-direction: column; gap: 7px">{skill_rows(h['skills'])}</div>
        <p style="margin: auto 0 0; padding-top: 7px; border-top: 1px dashed #A8966C; {R.SERIF}; font-size: 13px; font-weight: 700; line-height: 1.5; color: #3A2A18">“{h['lore']}”</p>
      </div>
    </article>"""



def extra_defs():
    def lg(i, stops, x1=0, y1=0, x2=0, y2=1):
        s = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"></stop>' for o, c, a in stops)
        return f'<linearGradient id="h3-{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'

    def rg(i, stops, cx=0.5, cy=0.5, r=0.5):
        s = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"></stop>' for o, c, a in stops)
        return f'<radialGradient id="h3-{i}" cx="{cx}" cy="{cy}" r="{r}">{s}</radialGradient>'
    return "".join([
        lg("sky1", [(0, "#0C1426", 1), (0.5, "#1C2C48", 1), (1, "#3A4C66", 1)]),
        rg("moon", [(0, "#F4F2E0", 0.75), (0.45, "#C8D4E0", 0.22), (1, "#C8D4E0", 0)]),
        lg("river", [(0, "#2A3E56", 1), (0.5, "#16263A", 1), (1, "#0C1622", 1)]),
        lg("haze", [(0, "#C8D4DC", 0), (0.5, "#C8D4DC", 0.28), (1, "#C8D4DC", 0)]),
        lg("sky2", [(0, "#1E1420", 1), (0.42, "#6A2A28", 1), (0.66, "#D2663A", 1), (0.86, "#F0B066", 1)]),
        rg("sun2", [(0, "#FFF0C8", 0.95), (0.3, "#FFC878", 0.6), (1, "#F09A50", 0)]),
        lg("sky3", [(0, "#0A1420", 1), (0.55, "#1E3238", 1), (1, "#46605C", 1)]),
        rg("aura", [(0, OCHRE, 0.26), (0.55, OCHRE, 0.07), (1, OCHRE, 0)]),
        rg("ringglow", [(0.6, OCHRE, 0), (0.85, OCHRE, 0.35), (1, OCHRE, 0)]),
        lg("iconrim", [(0, "#F6DE9A", 1), (0.5, "#8A6A3A", 1), (1, "#3A2410", 1)], 0, 0, 1, 1),
        lg("icbg-teal", [(0, "#2E6A64", 1), (1, "#0E2422", 1)], 0, 0, 1, 1),
        lg("icbg-ochre", [(0, "#8A6420", 1), (1, "#2A1A08", 1)], 0, 0, 1, 1),
        lg("icbg-ink", [(0, "#3A3A4A", 1), (1, "#12121A", 1)], 0, 0, 1, 1),
        lg("icbg-red", [(0, "#8A2A1A", 1), (1, "#2A0A06", 1)], 0, 0, 1, 1),
        lg("icmount", [(0, "#6A8A5A", 1), (1, "#22301E", 1)]),
        rg("icwarm", [(0, "#FFD488", 0.7), (1, "#FFD488", 0)]),
        lg("icwater", [(0, "#D8F4FA", 1), (1, "#3E9AB8", 1)], 0, 0, 1, 1),
        lg("icgold", [(0, "#FFF2C0", 1), (1, "#C9902E", 1)], 0, 0, 1, 1),
        lg("icsteel", [(0, "#FFFFFF", 1), (1, "#7A808A", 1)], 0, 0, 1, 0),
        lg("medrim", [(0, "#FFF0C0", 1), (0.4, "#C9A35A", 1), (1, "#4A3010", 1)], 0, 0, 1, 1),
        rg("medbg1", [(0, "#5A7A92", 1), (1, "#141E2E", 1)], 0.5, 0.35, 0.7),
        rg("medbg2", [(0, "#E2864E", 1), (1, "#2E1418", 1)], 0.5, 0.35, 0.7),
        rg("medbg3", [(0, "#6E8A84", 1), (1, "#101C1E", 1)], 0.5, 0.35, 0.7),
        rg("medvig", [(0.6, "#000", 0), (1, "#000", 0.55)]),
        '<clipPath id="h3-medclip"><circle cx="44" cy="44" r="37"></circle></clipPath>',
        '<clipPath id="h3-iconclip"><rect x="4" y="4" width="38" height="38" rx="3"></rect></clipPath>',
    ])


def page():
    fig1 = O.bari(K)
    fig2 = O.gangnim(K) + O.lasso(K)
    fig3 = O.sangun(K)
    panels = (hero_panel(HEROES[0], art_bari, "h3-fig1", (0, -128), 2.7, "h3-medbg1", 0)
              + hero_panel(HEROES[1], art_gangnim, "h3-fig2", (-2, -122), 2.35, "h3-medbg2", 1)
              + hero_panel(HEROES[2], art_sangun, "h3-fig3", (-80, -96), 1.15, "h3-medbg3", 2))
    title_style = (f"margin: 0; {R.SERIF}; font-weight: 800; font-size: 42px; line-height: 1.05; "
                   "background: linear-gradient(180deg, #FFF4CC 0%, #F2D58A 35%, #C9902E 70%, #8A5A16 100%); "
                   "-webkit-background-clip: text; background-clip: text; color: transparent; "
                   "filter: drop-shadow(0 2px 0 #1A0E06) drop-shadow(0 0 1px #000)")
    emblem = R.svg('0 0 100 100', R.emblem(), 72, 72, '오방 신당 문장')
    stripe = "".join(f'<span style="flex: 1; background: {c}"></span>' for c in (OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#121110"))
    # K.defs_svg() must be read after every panel has drawn its gradients
    defs = (R.DEFS + extra_defs() + K.defs_svg()
            + f'<g id="h3-fig1">{fig1}</g><g id="h3-fig2">{fig2}</g><g id="h3-fig3">{fig3}</g>')
    html = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>오방 신당 영웅</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
body{{margin:0;background:{GROUND}}}
</style>
</helmet>
<div style="position: relative; width: 1440px; height: 960px; box-sizing: border-box; background: radial-gradient(ellipse at 50% 0%, #2A2216 0%, {GROUND} 60%, #0E0C08 100%); color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; padding: 18px 40px; display: flex; flex-direction: column; gap: 10px; overflow: hidden">

  <svg width="0" height="0" style="position: absolute; width: 0; height: 0" aria-hidden="true" focusable="false"><defs>{defs}</defs></svg>

  <header style="display: flex; align-items: center; gap: 16px">
    {emblem}
    <div style="display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0">
      <span style="{R.MONO}; font-size: 12px; letter-spacing: 0.14em; color: {MUTED}">HERO CODEX 天 · 삼파전 SAMPAJEON · 무가 · 민간 신앙</span>
      <div style="display: flex; align-items: baseline; gap: 14px"><h1 style="{title_style}">영웅 · 오방 신당</h1><span style="{R.MONO}; font-size: 18px; font-weight: 600; letter-spacing: 0.2em; color: {OCHRE}">HEROES</span></div>
    </div>
    <p style="margin: 0; max-width: 470px; font-size: 12.5px; line-height: 1.55; color: #D8CDB4">망자를 인도하는 이, 저승의 부름을 전하는 이, 산을 지키는 이. 영웅은 한 진영에 하나씩 소환되며 발밑의 금빛 고리로 구분됩니다. 살아 있는 신앙에서 온 인물이므로 존중을 담아 재해석했습니다.</p>
  </header>
  <div style="display: flex; height: 5px; box-shadow: 0 1px 0 #000">{stripe}</div>

  <div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; flex: 1; min-height: 0">{panels}
  </div>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":1440,"height":960}}}}'>
class Component extends DCLogic {{
  renderVals() {{
    return {{}};
  }}
}}
</script>
</body>
</html>
"""
    # own namespace: race_r3 helpers emit r3- / r3k- ids
    return re.sub(r'(id="|#)r3(k?)-', r'\1h3\2-', html)


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(page())
    print("wrote", os.path.abspath(OUT), os.path.getsize(OUT))
