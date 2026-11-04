#!/usr/bin/env python3
"""Generate HeroR3.dc.html — 삼파전 hero codex for R3 보부상 연단 (Kite Guild).

Heroes: 김정호 (scout / map), 김만덕 (support / economy), 김유신 (kite general).
Figures use figure_kit through race_r3's kit helpers; every id from race_r3 (r3- / r3k-)
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
from figure_kit import Mat, MATS, mix, shade  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "HeroR3.dc.html")
K = R.K
KM = R.KM
f = R.f
OCHRE, OCHRE_L, OCHRE_D = R.OCHRE, R.OCHRE_L, R.OCHRE_D
GROUND, INK, MUTED = R.GROUND, R.INK, R.MUTED
OUTLINE = R.OUTLINE

M = {
    "ok": Mat("#9DB5AA", "#DDEEE4", "#1C3630", "#6A8A9A", spec_op=0.22, gloss=0.22),      # 옥색 도포
    "gat": Mat("#1E1C1A", "#5E5A54", "#030303", "#3A4458", spec="#DDE6FF", spec_op=0.55, gloss=0.75),
    "galot": Mat("#A85C2A", "#E49E60", "#2E1006", "#7A4A40", spec_op=0.22, gloss=0.22),   # 갈옷 저고리
    "chima": Mat("#2C3A74", "#6A7CC0", "#070B22", "#4A5A8C", spec_op=0.28, gloss=0.35),   # 남색 치마
    "gold": Mat("#D2A43A", "#FFE9A0", "#4A3008", "#9A7A40", spec="#FFFDF0", spec_op=0.95, gloss=1.0),
    "cape": Mat("#9C2618", "#E25C3A", "#260504", "#6A2A3A", spec_op=0.22, gloss=0.28),
    "cape_in": Mat("#C8962E", "#F4CF72", "#3A2608", "#8A7048", spec_op=0.2, gloss=0.2),
    "jade": Mat("#4FA486", "#BDF2DC", "#0C3426", "#4A8A8A", spec="#FFFFFF", spec_op=0.85, gloss=0.9),
    "trous": Mat("#D8D0BC", "#FBF6E8", "#4E4636", "#8E96A0", spec_op=0.2, gloss=0.15),
    "iron": Mat("#5E646E", "#B8C0CC", "#101216", "#4E6278", spec="#FFFFFF", spec_op=0.9, gloss=1.0),
    "skin_f": Mat("#D8A27A", "#F8D6B6", "#5A2E1C", "#9A6A5A"),
}
SKIN = KM["skin"]

_uid = [0]


def uid(tag):
    _uid[0] += 1
    return f"h3-{tag}{_uid[0]}"


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def P(p):
    return f"{f(p[0])} {f(p[1])}"


def smooth(pts_, closed=True):
    return K._smooth(list(pts_), closed)


def fill_form(d, c, n, w, m, outline=True, extra=""):
    gid = K._cyl_grad(c, n, w, m)
    o = f' stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.75" stroke-linejoin="round"' if outline else ""
    return f'<path d="{d}" fill="url(#{gid})"{o}{extra}></path>'


def shade_overlay(c, n, w, lit=0.22, dark=0.55):
    """Transparent light→dark gradient (for clipped overlays that need one global form shade)."""
    gid = K.id("o")
    x1, y1 = c[0] + n[0] * w, c[1] + n[1] * w
    x2, y2 = c[0] - n[0] * w, c[1] - n[1] * w
    K._defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">'
                   f'<stop offset="0" stop-color="#FFF6DC" stop-opacity="{lit}"></stop><stop offset="0.35" stop-color="#FFF6DC" stop-opacity="0"></stop>'
                   f'<stop offset="0.7" stop-color="#050302" stop-opacity="{dark * 0.6}"></stop><stop offset="0.92" stop-color="#050302" stop-opacity="{dark}"></stop>'
                   f'<stop offset="1" stop-color="#5A6A8A" stop-opacity="0.25"></stop></linearGradient>')
    return gid


def ell(cx, cy, rx, ry, fill, op=1.0, extra=""):
    return f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx)}" ry="{f(ry)}" fill="{fill}" opacity="{f(op)}"{extra}></ellipse>'


def stroke(d, col, w, op=1.0, extra=""):
    return f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{f(w)}" stroke-opacity="{f(op)}" stroke-linecap="round" stroke-linejoin="round"{extra}></path>'


SOFT = ' filter="url(#r3k-soft)"'


# ================================================================ faces & hair

def hero_head(cx, cy, r, turn=0.3, female=False, age=0.0, beard=None, m=None, mouth="closed", hair=True, look=None):
    """Individually designed hero face (figure_kit K.face) + hair cap clipped to the skull."""
    lk = dict(look or {})
    lk.setdefault("turn", turn)
    lk.setdefault("age", age)
    lk.setdefault("fem", female)
    if beard and "beard" not in lk:
        lk["beard"] = beard
    if mouth == "open":
        lk.setdefault("mouth", "shout")
    m = m or (M["skin_f"] if female else SKIN)
    h, d = K.face(cx, cy, r, m, lk, ear=(turn >= 0 and not female))
    out = [h]
    if hair and not female:
        fx = cx + turn * r * 0.3
        hg = K._sphere_grad(cx - r * 0.3, cy - r * 0.5, r * 1.1, KM["hair"])
        cap = (f'<path d="M{f(cx - r * 1.3)} {f(cy - r * 1.3)} L{f(cx + r * 1.3)} {f(cy - r * 1.3)} L{f(cx + r * 1.0)} {f(cy - r * 0.55)} '
               f'Q{f(fx + r * 0.1)} {f(cy - r * 0.78)} {f(fx - r * 0.55)} {f(cy - r * 0.5)} Q{f(cx - r * 0.75)} {f(cy - r * 0.38)} {f(cx - r * 0.66)} {f(cy + r * 0.2)} '
               f'L{f(cx - r * 0.74)} {f(cy + r * 0.55)} L{f(cx - r * 1.3)} {f(cy + r * 0.65)} Z" fill="url(#{hg})"></path>')
        strands = "".join(stroke(f"M{f(cx + math.cos(-2.6 + j * 0.25) * r * 0.95)} {f(cy + math.sin(-2.6 + j * 0.25) * r * 0.95)} "
                                 f"Q{f(cx - r * 0.2)} {f(cy - r * 0.95)} {f(cx + r * 0.5)} {f(cy - r * 0.8)}", "#8A94B0", r * 0.025, 0.45)
                          for j in range(5))
        out.append(K.clip_overlay(d, cap + strands))
    return "".join(out), d


# Research-based designs (SPEC_FACE): ages at the depicted events; no portrait is traced.
LOOK_JEONGHO = dict(shape="diamond", cheek=0.95, eye="droopy", eye_size=0.95, brow="worried", brow_col="#2A221C", nose="straight",
                    nose_len=1.12, mouth="calm", age=0.62, beard="thin_long", beard_col="#4A433C", jaw=0.92)
LOOK_MANDEOK = dict(shape="oval", fem=True, cheek=0.45, eye="almond", lid="double", brow="thin", nose="small", mouth="smile",
                    lip=0.6, age=0.44, blush=True)
LOOK_YUSIN = dict(shape="square", jaw=1.12, cheek=0.7, eye="sharp", brow="angry", brow_col="#120A06", nose="straight", mouth="shout",
                  age=0.5, beard="goatee", beard_col="#18100A", scar="brow")


def neck(base, top, w, m=None):
    m = m or SKIN
    return K.neck(base, top, w, m) + K.ao(base[0] + 1.5, base[1] - 1, w * 1.4, 2.4, 0.5)


# ================================================================ hats & costume parts

def gat(cx, y):
    """갓: black horsehair hat — translucent brim, mesh crown, amber bead strings (갓끈)."""
    gm = M["gat"]
    out = ""
    brim = f"M{f(cx - 27)} {f(y)} Q{f(cx - 26)} {f(y - 6.5)} {f(cx)} {f(y - 7)} Q{f(cx + 26)} {f(y - 6.5)} {f(cx + 27)} {f(y)} Q{f(cx)} {f(y + 8.5)} {f(cx - 27)} {f(y)} Z"
    bg = K._sphere_grad(cx - 6, y - 4, 30, gm)
    out += f'<path d="{brim}" fill="url(#{bg})" opacity="0.82" stroke="#000" stroke-width="0.7" stroke-opacity="0.8"></path>'
    weave = "".join(stroke(f"M{f(cx + math.cos(a) * 8)} {f(y + math.sin(a) * 2.2)} L{f(cx + math.cos(a) * 27)} {f(y + math.sin(a) * 7.6)}", "#8A8A90", 0.25, 0.35)
                    for a in [i * math.tau / 60 for i in range(60)])
    weave += "".join(stroke(f"M{f(cx - 27 * t)} {f(y)} Q{f(cx)} {f(y + 8.5 * t)} {f(cx + 27 * t)} {f(y)} M{f(cx - 27 * t)} {f(y)} Q{f(cx)} {f(y - 7 * t)} {f(cx + 27 * t)} {f(y)}", "#6A6A70", 0.22, 0.4)
                     for t in (0.45, 0.6, 0.75, 0.9))
    out += K.clip_overlay(brim, weave)
    out += stroke(f"M{f(cx - 26)} {f(y - 1)} Q{f(cx - 20)} {f(y - 6)} {f(cx - 4)} {f(y - 7)}", "#C8D0E8", 0.7, 0.55)
    # crown (모자): slightly tapered cylinder, translucent mesh
    crown = f"M{f(cx - 9)} {f(y - 2)} L{f(cx - 8.2)} {f(y - 18)} Q{f(cx)} {f(y - 20.5)} {f(cx + 8.2)} {f(y - 18)} L{f(cx + 9)} {f(y - 2)} Q{f(cx)} {f(y + 1)} {f(cx - 9)} {f(y - 2)} Z"
    cg = K._cyl_grad((cx, y - 10), (-1, -0.1), 9.5, gm)
    out += f'<path d="{crown}" fill="url(#{cg})" opacity="0.92" stroke="#000" stroke-width="0.7"></path>'
    mesh = "".join(stroke(f"M{f(cx - 9 + i * 1.2)} {f(y - 1)} L{f(cx - 8.2 + i * 1.1)} {f(y - 19)}", "#8A8A96", 0.2, 0.35) for i in range(16))
    mesh += "".join(stroke(f"M{f(cx - 9)} {f(y - 2 - k * 2.2)} Q{f(cx)} {f(y + 0.8 - k * 2.2)} {f(cx + 9)} {f(y - 2 - k * 2.2)}", "#5A5A62", 0.2, 0.45) for k in range(8))
    out += K.clip_overlay(crown, mesh)
    out += ell(cx, y - 18.6, 8.2, 2.0, "#2A2826", 0.95, ' stroke="#000" stroke-width="0.5"')
    out += stroke(f"M{f(cx - 7)} {f(y - 15)} L{f(cx - 6.6)} {f(y - 5)}", "#E8EEFF", 0.9, 0.45)
    # band
    out += stroke(f"M{f(cx - 9)} {f(y - 3.2)} Q{f(cx)} {f(y - 0.2)} {f(cx + 9)} {f(y - 3.2)}", "#050404", 1.6)
    return out


def gat_beads(cx, y, end):
    """갓끈: two strings of amber beads from the brim, meeting under the chin and hanging to the chest."""
    out = ""
    amber = Mat("#C07A2A", "#FFD58A", "#3A1C06", "#8A5A40", spec="#FFFFFF", spec_op=0.9, gloss=0.9)
    for sx in (-12.5, 12.0):
        a = (cx + sx, y + 3)
        mid = (cx + sx * 0.95, end[1] - 6)
        n = 9
        for i in range(n + 1):
            t = i / n
            p = lerp(lerp(a, mid, t), lerp(mid, end, t), t)
            out += K.ball(p[0], p[1], 0.95, amber, outline=False)
    for i in range(6):
        p = (end[0] + 0.3 * i * 0.2, end[1] + i * 1.9)
        out += K.ball(p[0], p[1], 1.0 + (0.25 if i == 5 else 0), amber, outline=False)
    return out


def wide_sleeve(sh, el, wr, m, droop=12.0, back=False, w=7.2):
    """도포 wide sleeve covering the arm to the wrist, with the hanging 배래 belly and a dark sleeve mouth."""
    m2 = m.back() if back else m
    out = K.tube([sh, lerp(sh, el, 0.5), el], [w, w * 1.05, w * 1.1], m2, [(0.5, w * 0.12, 0)])
    # belly of the sleeve hanging under the forearm
    ax = (wr[0] - el[0], wr[1] - el[1])
    L = math.hypot(*ax) or 1
    n = (-ax[1] / L, ax[0] / L)
    if n[1] < 0:
        n = (-n[0], -n[1])
    p1 = (el[0] + n[0] * w * 0.9, el[1] + n[1] * w * 0.9)
    p2 = (wr[0] + n[0] * w * 1.2, wr[1] + n[1] * w * 1.2)
    mid = lerp(p1, p2, 0.55)
    c = (mid[0], mid[1] + droop * 1.6)
    belly = f"M{P((el[0] - n[0] * w, el[1] - n[1] * w))} L{P((wr[0] - n[0] * w * 1.25, wr[1] - n[1] * w * 1.25))} L{P(p2)} Q{P(c)} {P(p1)} Z"
    out += fill_form(belly, (mid[0], mid[1]), (-0.4, -1), w * 1.6, m2)
    folds = ""
    for t in (0.25, 0.5, 0.75):
        q = lerp(el, wr, t)
        folds += stroke(f"M{P(q)} Q{f(q[0] + 1.5)} {f(q[1] + droop * 0.8)} {f(q[0] + n[0] * 2 + 3)} {f(q[1] + w + droop * (1.2 - abs(t - 0.55)))}", m2.core, 1.4, 0.55, SOFT)
        folds += stroke(f"M{f(q[0] - 1.4)} {f(q[1] + 1)} Q{f(q[0])} {f(q[1] + droop * 0.6)} {f(q[0] + 1.2)} {f(q[1] + w + droop * 0.8)}", m2.light, 0.7, 0.5)
    folds += K.ao(el[0], el[1] - w * 0.4, w * 1.1, w * 0.5, 0.4)
    out += K.clip_overlay(belly, folds)
    out += K.folds(sh, el, w, w * 1.1, m2, n=3)
    # sleeve mouth (dark opening the hand comes out of)
    ang = math.degrees(math.atan2(n[1], n[0]))
    out += (f'<ellipse cx="{f(wr[0] + n[0] * 0.5)}" cy="{f(wr[1] + n[1] * 0.5)}" rx="{f(w * 1.2)}" ry="{f(w * 0.42)}" fill="#0A0806" '
            f'transform="rotate({f(ang)} {f(wr[0] + n[0] * 0.5)} {f(wr[1] + n[1] * 0.5)})" stroke="{m2.light}" stroke-width="0.6" stroke-opacity="0.6"></ellipse>')
    return out


def tassel(x, y, col="#B8322A", ln=7.0, w=2.2):
    m = Mat(col, shade(col, 0.35), shade(col, -0.6))
    out = K.ball(x, y, w * 0.6, m)
    out += K.form((x, y + w * 0.4), (x, y + w * 1.4), w * 0.55, w * 0.7, M["gold"], ao_start=False, spec=True)
    body = f"M{f(x - w * 0.55)} {f(y + w * 1.4)} L{f(x - w * 0.9)} {f(y + ln)} Q{f(x)} {f(y + ln + 1.2)} {f(x + w * 0.9)} {f(y + ln)} L{f(x + w * 0.55)} {f(y + w * 1.4)} Z"
    out += fill_form(body, (x, y + ln * 0.6), (-1, 0), w, m)
    out += "".join(stroke(f"M{f(x + (i - 2.5) * w * 0.3)} {f(y + w * 1.6)} L{f(x + (i - 2.5) * w * 0.34)} {f(y + ln)}", m.core, 0.35, 0.6) for i in range(6))
    return out


# ================================================================ HERO 1: 김정호 — unrolling the map

def map_sheet(tl, tr, bot_y, seed=7):
    """The hanging 대동여지도-style sheet: hanji, fold grid, mountain ranges, rivers, 10-li roads, towns, goblin-fire ore marks."""
    rng = random.Random(seed)
    bl = (tl[0] + 5, bot_y + 2)
    br = (tr[0] + 7, bot_y - 1)
    left = [tl, (tl[0] - 1.6, lerp(tl, bl, 0.3)[1]), (tl[0] + 3.5, lerp(tl, bl, 0.65)[1]), bl]
    right = [br, (tr[0] + 6.5, lerp(tr, br, 0.6)[1]), (tr[0] + 2, lerp(tr, br, 0.3)[1]), tr]
    pts_ = left + [(lerp(bl, br, 0.5)[0], bot_y + 2.5)] + right
    d = smooth(pts_)
    x0, x1 = tl[0] - 2, br[0] + 2
    y0, y1 = tl[1] - 4, bot_y + 4
    # ripple gradient across the sheet (vertical waves of the hanging paper)
    gid = K.id("p")
    st = ""
    for i in range(9):
        t = i / 8
        col = ["#FBF2D8", "#E2D2A8", "#F6EACB", "#CDBB8E", "#F0E2BE", "#D4C298", "#E8D8B0", "#B8A276", "#9A865C"][i]
        st += f'<stop offset="{f(t)}" stop-color="{col}"></stop>'
    K._defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x0)}" y1="{f(y0)}" x2="{f(x1)}" y2="{f(y0 + 8)}">{st}</linearGradient>')
    out = f'<path d="{d}" fill="#000" opacity="0.35" transform="translate(3 3)"{SOFT}></path>'
    out += f'<path d="{d}" fill="url(#{gid})" filter="url(#r3-tx-paper)" stroke="#5A4426" stroke-width="0.7"></path>'
    inner = ""
    # border frame of the printed sheet
    inner += f'<rect x="{f(x0 + 4)}" y="{f(y0 + 7)}" width="{f(x1 - x0 - 8)}" height="{f(y1 - y0 - 12)}" fill="none" stroke="#3A2A18" stroke-width="0.5" opacity="0.6"></rect>'
    # sea (bottom right) with wave hatching
    sea = f"M{f(x1)} {f(y1 - 34)} Q{f(x1 - 12)} {f(y1 - 26)} {f(x1 - 10)} {f(y1 - 14)} Q{f(x1 - 22)} {f(y1 - 8)} {f(x1 - 30)} {f(y1)} L{f(x1)} {f(y1)} Z"
    inner += f'<path d="{sea}" fill="#9CC0C4" opacity="0.55"></path>'
    for k in range(9):
        yy = y1 - 30 + k * 3.2
        inner += stroke(f"M{f(x1 - 14 + k * 0.6)} {f(yy)} q1.2 -0.8 2.4 0 t2.4 0 t2.4 0", "#3A6A7A", 0.3, 0.6)
    inner += stroke(f"M{f(x1)} {f(y1 - 34)} Q{f(x1 - 12)} {f(y1 - 26)} {f(x1 - 10)} {f(y1 - 14)} Q{f(x1 - 22)} {f(y1 - 8)} {f(x1 - 30)} {f(y1)}", "#2A4A5A", 0.6, 0.8)
    # rivers: double lines + pale fill, widening downstream
    for rv in range(3):
        sx = rng.uniform(x0 + 6, x1 - 14)
        p = (sx, y0 + 8)
        pts_r = [p]
        for _ in range(9):
            p = (p[0] + rng.uniform(-3, 5), p[1] + rng.uniform(6, 9))
            pts_r.append(p)
        dd = smooth(pts_r, closed=False)
        inner += stroke(dd, "#A8C8D0", 1.6 + rv * 0.3, 0.9)
        inner += stroke(dd, "#2E5A7A", 0.35, 0.9)
        inner += stroke(dd, "#2E5A7A", 0.35, 0.9, ' transform="translate(1.1 0.2)"')
    # mountain ranges: chains of hills (ink), the signature of the map
    for ch in range(7):
        p = (rng.uniform(x0 + 4, x1 - 6), rng.uniform(y0 + 10, y1 - 10))
        ang = rng.uniform(0, math.tau)
        for _ in range(rng.randint(8, 14)):
            ang += rng.uniform(-0.6, 0.6)
            q = (p[0] + math.cos(ang) * 2.6, p[1] + math.sin(ang) * 2.6)
            if not (x0 + 3 < q[0] < x1 - 3 and y0 + 8 < q[1] < y1 - 6):
                ang += math.pi
                continue
            hgt = rng.uniform(1.6, 2.8)
            inner += f'<path d="M{f(q[0] - 1.6)} {f(q[1])} Q{f(q[0] - 0.3)} {f(q[1] - hgt * 1.4)} {f(q[0] + 1.6)} {f(q[1])} Z" fill="#3E5234" opacity="0.92"></path>'
            inner += f'<path d="M{f(q[0] - 1.2)} {f(q[1] - 0.2)} Q{f(q[0] - 0.6)} {f(q[1] - hgt)} {f(q[0] - 0.1)} {f(q[1] - hgt * 0.7)}" stroke="#A8B888" stroke-width="0.3" fill="none" opacity="0.8"></path>'
            inner += stroke(f"M{P(p)} L{P(q)}", "#1E2A18", 0.8, 0.85)
            p = q
    # roads with 10-li ticks (십리 눈금)
    towns = [(rng.uniform(x0 + 8, x1 - 10), rng.uniform(y0 + 12, y1 - 12)) for _ in range(6)]
    for a, b in zip(towns, towns[1:]):
        inner += stroke(f"M{P(a)} L{P(b)}", "#8A2A18", 0.45, 0.85)
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        for k in range(1, int(L / 3.2)):
            q = lerp(a, b, k * 3.2 / L)
            nx_, ny_ = -(b[1] - a[1]) / L, (b[0] - a[0]) / L
            inner += stroke(f"M{f(q[0] - nx_ * 0.9)} {f(q[1] - ny_ * 0.9)} L{f(q[0] + nx_ * 0.9)} {f(q[1] + ny_ * 0.9)}", "#8A2A18", 0.3, 0.9)
    for i, t in enumerate(towns):
        if i % 2:
            inner += f'<rect x="{f(t[0] - 1.6)}" y="{f(t[1] - 1.2)}" width="3.2" height="2.4" fill="#F4E6C0" stroke="#7A1A10" stroke-width="0.4"></rect>'
        else:
            inner += f'<circle cx="{f(t[0])}" cy="{f(t[1])}" r="1.5" fill="#C8432B" stroke="#4A0E06" stroke-width="0.3"></circle>'
    # goblin-fire ore veins (the discovery)
    for _ in range(4):
        q = (rng.uniform(x0 + 8, x1 - 10), rng.uniform(y0 + 14, y1 - 14))
        inner += f'<circle cx="{f(q[0])}" cy="{f(q[1])}" r="4.5" fill="url(#r3-oreglow)"></circle>'
        inner += f'<path d="M{f(q[0])} {f(q[1] - 1.8)} l1.4 1.8 l-1.4 1.8 l-1.4 -1.8 z" fill="#9FE3D0" stroke="#1E5A4E" stroke-width="0.3"></path>'
    # fold grid of the folding book (분첩절첩식)
    for k in range(1, 4):
        xx = x0 + (x1 - x0) * k / 4
        inner += f'<path d="M{f(xx)} {f(y0)} V{f(y1)}" stroke="#6A5430" stroke-width="0.5" opacity="0.45"></path>'
        inner += f'<path d="M{f(xx + 0.6)} {f(y0)} V{f(y1)}" stroke="#FFF8E0" stroke-width="0.4" opacity="0.5"></path>'
    for k in range(1, 6):
        yy = y0 + (y1 - y0) * k / 6
        inner += f'<path d="M{f(x0)} {f(yy)} H{f(x1)}" stroke="#6A5430" stroke-width="0.45" opacity="0.4"></path>'
    # title cartouche + red seal
    inner += f'<rect x="{f(x0 + 6)}" y="{f(y0 + 9)}" width="12" height="4" fill="#F6ECD0" stroke="#3A2A18" stroke-width="0.35"></rect>'
    inner += "".join(f'<rect x="{f(x0 + 7 + i * 2.7)}" y="{f(y0 + 10)}" width="1.8" height="2" fill="#2A1A10" opacity="0.75"></rect>' for i in range(4))
    inner += f'<rect x="{f(x1 - 12)}" y="{f(y0 + 9)}" width="4" height="4" fill="#B8301E" opacity="0.85"></rect>'
    # paper shading: AO under the top roller, ripple shade
    inner += f'<rect x="{f(x0)}" y="{f(y0)}" width="{f(x1 - x0)}" height="7" fill="#1A1008" opacity="0.45"{SOFT}></rect>'
    sg = shade_overlay(((x0 + x1) / 2, (y0 + y1) / 2), (-1, -0.15), (x1 - x0) / 2 + 2, 0.18, 0.4)
    inner += f'<rect x="{f(x0)}" y="{f(y0)}" width="{f(x1 - x0)}" height="{f(y1 - y0)}" fill="url(#{sg})"></rect>'
    out += K.clip_overlay(d, inner)
    # bottom curl (paper rolling up at the hem)
    out += K.form((bl[0] - 1, bot_y), (br[0] + 1, bot_y - 2.5), 2.6, 2.6, Mat("#E8D8B0", "#FFF6DC", "#5A4426", "#8A8070", spec_op=0.3, gloss=0.3), ao_start=False)
    return out


def scroll_roller(a, b, r=2.4):
    wood = KM["lacquer"]
    out = K.form(a, b, r, r, wood, ao_start=False)
    for p in (a, b):
        out += K.ball(p[0], p[1], r * 1.25, M["gold"])
    return out


def compass_disc(cx, cy, s=1.0):
    """윤도 (Korean geomantic compass): lacquered wood disc, rings of tiny marks, red needle."""
    wd = Mat("#8A5A2A", "#D8A464", "#2A1408", "#6A5A50", spec_op=0.5, gloss=0.6)
    out = ell(cx + 1.5 * s, cy + 2.4 * s, 8.6 * s, 3.4 * s, "#000", 0.45, SOFT)
    out += f'<path d="M{f(cx - 8 * s)} {f(cy)} L{f(cx - 8 * s)} {f(cy + 1.6 * s)} Q{f(cx)} {f(cy + 5 * s)} {f(cx + 8 * s)} {f(cy + 1.6 * s)} L{f(cx + 8 * s)} {f(cy)} Z" fill="{wd.core}"></path>'
    out += K.ball(cx, cy, 8 * s, wd, ry=3.3 * s)
    for k, rr in enumerate((6.6, 5.2, 3.8)):
        out += ell(cx, cy, rr * s, rr * 0.41 * s, "none", 1, f' stroke="#2A1408" stroke-width="{f(0.3 * s)}" stroke-opacity="0.8"')
        n = 24 - k * 6
        for i in range(n):
            a = i * math.tau / n
            out += (f'<path d="M{f(cx + math.cos(a) * rr * s)} {f(cy + math.sin(a) * rr * 0.41 * s)} L{f(cx + math.cos(a) * (rr - 0.9) * s)} '
                    f'{f(cy + math.sin(a) * (rr - 0.9) * 0.41 * s)}" stroke="#1A0A04" stroke-width="{f(0.22 * s)}" opacity="0.8"></path>')
    out += ell(cx, cy, 2.6 * s, 1.1 * s, "#E8E0C8", 1, ' stroke="#2A1408" stroke-width="0.3"')
    out += f'<path d="M{f(cx - 2.4 * s)} {f(cy + 0.5 * s)} L{f(cx + 2.4 * s)} {f(cy - 0.5 * s)}" stroke="#C8301E" stroke-width="{f(0.6 * s)}"></path>'
    out += f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(0.4 * s)}" fill="#C9A35A"></circle>'
    out += ell(cx - 3.5 * s, cy - 1.2 * s, 2.2 * s, 0.6 * s, "#FFFFFF", 0.35)
    return out


def hero_jeongho():
    ok, hemp = M["ok"], KM["hemp"]
    out = ""
    # map-scroll case on the back (behind everything)
    a, b = (-25, -148), (10, -58)
    out += K.form(a, b, 5.6, 5.6, KM["lacquer"].back(0.05), ao_start=False)
    for t in (0.1, 0.48, 0.9):
        p = lerp(a, b, t)
        ax = ((b[0] - a[0]) / 97, (b[1] - a[1]) / 97)
        out += K.form((p[0] - ax[0] * 1.4, p[1] - ax[1] * 1.4), (p[0] + ax[0] * 1.4, p[1] + ax[1] * 1.4), 6.1, 6.1, M["gold"], ao_start=False)
    out += K.ball(a[0], a[1], 5.4, M["gold"], ry=3.6)
    out += ell(a[0] - 0.4, a[1] - 0.4, 3.0, 1.8, "#2A1A08", 0.9)
    # back flap of the 도포 (behind both legs)
    flap, _ = R.kpanel([(-15, -72), (15, -72), (28, -18), (3, -13), (-31, -18)], ok.back(0.32), folds=5)
    out += flap
    # far leg (stepping back)
    out += R.k_leg((6, -64), (14, -36), (21, -7), hemp, d=1, back=True)
    # far arm raised: bare forearm, sleeve slid to the elbow
    sh, el, wr = (12, -107), (25, -127), (33, -145)
    out += K.tube([lerp(sh, el, 0.45), el, wr], [5.0, 4.6, 3.6], SKIN.back(0.12), [(0.7, 1.0, 0)], crease_at=(1,))
    sl, _ = R.kpanel([(8, -113), (17, -128), (27, -134), (32, -127), (31, -114), (25, -102), (14, -99)], ok.back(0.18), folds=3)
    out += sl
    out += K.ao(28, -128, 4, 3, 0.5)
    # the map, hanging from a roller held overhead
    out += map_sheet((22, -146), (64, -141), -60)
    out += scroll_roller((19, -148), (68, -142), 2.3)
    out += K.hand(wr, (0.25, -1), 3.4, SKIN.back(0.1), kind="fist")
    # near leg (planted forward)
    out += R.k_leg((-5, -64), (-12, -36), (-16, -7), hemp, d=-1)
    # front skirts of the 도포 (split front, flaring with the stride)
    fr, _ = R.kpanel([(-15, -73), (1, -73), (-2, -26), (-12, -21), (-26, -24)], ok, folds=3)
    out += fr
    fr2, _ = R.kpanel([(0, -73), (14, -73), (21, -26), (8, -22), (-1, -26)], ok.back(0.1), folds=3)
    out += fr2
    out += stroke("M0.5 -72 Q-1 -50 -1.5 -26", ok.core, 1.2, 0.7)
    # torso
    t, d = K.torso((0, -113), (0, -71), 15, 13.5, ok)
    out += t
    inner = (f'<path d="M-6 -116 L9 -88 L13 -90 L-1.5 -117 Z" fill="{mix(ok.core, ok.base, 0.45)}"></path>'
             + stroke("M-6.5 -116 L8.5 -90", "#F6F0E0", 2.6) + stroke("M-6.5 -116 L8.5 -90", "#9A9078", 0.5, 1, ' transform="translate(1.1 0.2)"')
             + stroke("M6 -116 L-2 -102", "#F6F0E0", 2.2, 0.9))
    for s_ in (-1, 1):
        inner += stroke(f"M{s_ * 13} -104 q{-s_ * 5} 6 {-s_ * 7} 14", ok.core, 1.4, 0.5, SOFT)
    out += K.clip_overlay(d, inner)
    # strap of the map case across the chest
    out += K.form((-13, -111), (13, -74), 1.8, 1.8, KM["leather"], ao_start=False)
    out += K.ball(-1, -94, 1.7, M["gold"])
    # 세조대 (cord belt) with knot and long tasselled ends
    out += K.ao(0, -69, 15, 2.4, 0.5)
    cord = Mat("#7A2A4A", "#C86A8A", "#1A040C", "#5A3A50", spec_op=0.3, gloss=0.4)
    out += K.form((-14.5, -72), (14, -70.5), 1.3, 1.3, cord, ao_start=False)
    out += K.ball(-7, -71.6, 2.0, cord)
    for (x1_, y1_) in ((-10, -36), (-6, -32)):
        out += stroke(f"M-7 -70 Q{f(x1_ - 1)} -52 {f(x1_)} {f(y1_)}", cord.base, 1.1)
        out += stroke(f"M-7.3 -70 Q{f(x1_ - 1.3)} -52 {f(x1_ - 0.3)} {f(y1_)}", cord.light, 0.35, 0.7)
        out += tassel(x1_, y1_, "#7A2A4A", 7.5, 2.2)
    # compass cord + brush tucked in the belt
    out += K.form((8, -78), (11, -60), 0.9, 0.7, KM["bamboo"], ao_start=False)
    out += K.form((11, -60), (12, -55), 1.1, 0.4, M["gat"], ao_start=False, spec=False)
    # neck, head, hat
    out += neck((0.8, -112), (0.8, -120), 4.3)
    hd, _ = hero_head(1.2, -128, 10, turn=0.35, m=MATS["skin_weathered"], look=LOOK_JEONGHO)
    out += hd
    out += K.ao(2.5, -133, 13, 3.6, 0.6)
    out += gat(1.2, -136)
    out += gat_beads(1.2, -136, (4, -103))
    # near arm, elbow out, holding the compass in front of the chest
    out += wide_sleeve((-14, -108), (-27, -94), (-12, -88), ok, droop=10)
    out += compass_disc(-3, -90, 1.0)
    out += K.hand((-12, -88), (1, 0.15), 3.5, SKIN, kind="fist")
    return out


# ================================================================ HERO 2: 김만덕 — distributing rice

def rice_stream(a, b, rng, n=70):
    """Falling grains of rice from a to b (parabolic), with highlights and a dusty trail."""
    out = ""
    c = (a[0] - 4, (a[1] + b[1]) / 2 - 4)
    trail = f"M{P(a)} Q{P(c)} {P(b)}"
    out += stroke(trail, "#F4ECD4", 3.4, 0.22, SOFT)
    for i in range(n):
        t = rng.random() ** 0.9
        p = lerp(lerp(a, c, t), lerp(c, b, t), t)
        spread = 0.6 + t * 2.4
        x = p[0] + rng.uniform(-spread, spread)
        y = p[1] + rng.uniform(-1, 1)
        ang = rng.uniform(0, 180)
        out += (f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="0.85" ry="0.42" fill="#F6EEDA" stroke="#8A7A5A" stroke-width="0.12" '
                f'transform="rotate({ang} {f(x)} {f(y)})"></ellipse>')
    return out


def rice_pile(cx, cy, w=10, h=4, rng=None):
    out = f'<path d="M{f(cx - w)} {f(cy)} Q{f(cx)} {f(cy - h * 2)} {f(cx + w)} {f(cy)} Z" fill="#EFE6CC"></path>'
    out += f'<path d="M{f(cx - w * 0.2)} {f(cy - h * 0.95)} Q{f(cx + w * 0.4)} {f(cy - h * 0.7)} {f(cx + w)} {f(cy)} L{f(cx + w * 0.3)} {f(cy)} Z" fill="#B8AA88" opacity="0.6"></path>'
    for _ in range(40):
        x = cx + rng.uniform(-w, w) * 0.9
        y = cy - rng.uniform(0, h * (1 - abs(x - cx) / w)) * 1.6
        out += f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="0.7" ry="0.35" fill="#FFFBF0" opacity="0.85"></ellipse>'
    return out


def open_sack(cx, cy, s=1.0):
    """Hemp rice sack, mouth rolled open, full of rice."""
    hm = KM["hemp"]
    out = K.cast_shadow(cx + 3 * s, cy, 15 * s, 4 * s, 0.55)
    body = (f"M{f(cx - 12 * s)} {f(cy)} Q{f(cx - 15 * s)} {f(cy - 12 * s)} {f(cx - 11 * s)} {f(cy - 20 * s)} L{f(cx + 11 * s)} {f(cy - 20 * s)} "
            f"Q{f(cx + 15 * s)} {f(cy - 12 * s)} {f(cx + 12 * s)} {f(cy)} Q{f(cx)} {f(cy + 3 * s)} {f(cx - 12 * s)} {f(cy)} Z")
    out += fill_form(body, (cx, cy - 10 * s), (-1, -0.2), 14 * s, hm, extra=' filter="url(#r3-tx-cloth)"')
    out += K.clip_overlay(body, "".join(stroke(f"M{f(cx + (i - 3) * 3.6 * s)} {f(cy - 18 * s)} q{f(1 * s)} {f(9 * s)} {f(0.4 * s)} {f(18 * s)}", hm.core, 0.9 * s, 0.4, SOFT) for i in range(7))
                          + f'<rect x="{f(cx - 14 * s)}" y="{f(cy - 9 * s)}" width="{f(28 * s)}" height="{f(3.4 * s)}" fill="#8A2A18" opacity="0.65"></rect>')
    out += K.form((cx - 12 * s, cy - 20 * s), (cx + 12 * s, cy - 20 * s), 2.4 * s, 2.4 * s, hm, ao_start=False)
    out += ell(cx, cy - 20.5 * s, 10.5 * s, 3 * s, "#F2EAD2", 1, ' stroke="#8A7A5A" stroke-width="0.4"')
    out += ell(cx - 1, cy - 21.3 * s, 6 * s, 1.6 * s, "#FFFCF2", 0.9)
    return out


def abacus(tl, tr, br, bl):
    """주판: lacquered frame, divider bar, rods with 1 + 4 beads."""
    wd = KM["lacquer"]
    bead = Mat("#5A3418", "#B8885A", "#140804", "#4A3A38", spec="#FFF0D8", spec_op=0.7, gloss=0.8)
    out = f'<polygon points="{R.pts([tl, tr, br, bl])}" fill="#2A1A0E" opacity="0.6" transform="translate(1.5 2)"{SOFT}></polygon>'
    out += f'<polygon points="{R.pts([tl, tr, br, bl])}" fill="#3A2414" stroke="#0A0402" stroke-width="0.6"></polygon>'
    n = 9
    bar_t = 0.32
    for i in range(n):
        u = (i + 0.7) / (n + 0.4)
        top, bot = lerp(tl, tr, u), lerp(bl, br, u)
        out += stroke(f"M{P(top)} L{P(bot)}", "#C9A35A", 0.35)
        for j, v in enumerate((0.13, 0.42, 0.56, 0.7, 0.84)):
            v2 = v if j == 0 else v + (0.04 if (i * 3 + j) % 4 == 0 else 0)
            p = lerp(top, bot, v2)
            out += K.ball(p[0], p[1], 1.05, bead, outline=False, ry=0.75)
    for e, g in ((lerp(tl, bl, bar_t), lerp(tr, br, bar_t)),):
        out += K.form(e, g, 0.7, 0.7, wd, ao_start=False, spec=False)
    for e, g in ((tl, tr), (bl, br), (tl, bl), (tr, br)):
        out += K.form(e, g, 1.1, 1.1, wd, ao_start=False)
    for p in (tl, tr, br, bl):
        out += K.ball(p[0], p[1], 0.9, M["gold"], outline=False)
    return out


def doebak(c, ang, s=1.0):
    """됫박: square wooden rice measure, tilted, rice heaped inside."""
    wd = KM["wood"]
    w, h = 8 * s, 6 * s
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))

    def rot(x, y):
        return (c[0] + x * ca - y * sa, c[1] + x * sa + y * ca)
    front = [rot(-w / 2, -h / 2), rot(w / 2, -h / 2), rot(w / 2, h / 2), rot(-w / 2, h / 2)]
    top = [rot(-w / 2, -h / 2), rot(w / 2, -h / 2), rot(w / 2 + 2.4 * s, -h / 2 - 2.2 * s), rot(-w / 2 + 2.4 * s, -h / 2 - 2.2 * s)]
    side = [rot(w / 2, -h / 2), rot(w / 2 + 2.4 * s, -h / 2 - 2.2 * s), rot(w / 2 + 2.4 * s, h / 2 - 2.2 * s), rot(w / 2, h / 2)]
    out = f'<polygon points="{R.pts(side)}" fill="{wd.core}" stroke="#0A0402" stroke-width="0.5"></polygon>'
    out += fill_form(f"M{P(front[0])} L{P(front[1])} L{P(front[2])} L{P(front[3])} Z", c, (-0.7, -0.7), w * 0.7, wd)
    out += f'<polygon points="{R.pts(top)}" fill="#F2EAD2" stroke="#3A2414" stroke-width="0.5"></polygon>'
    out += stroke(f"M{P(lerp(front[0], front[3], 0.3))} L{P(lerp(front[1], front[2], 0.3))}", wd.core, 0.35, 0.6)
    out += stroke(f"M{P(lerp(front[0], front[3], 0.7))} L{P(lerp(front[1], front[2], 0.7))}", wd.core, 0.35, 0.6)
    return out, rot(-w / 2 - 0.5, -h / 2 - 0.5)


def kkotsin(x, y, d=-1, col="#B8322A"):
    """꽃신 toe peeking under the hem, white 버선 above."""
    m = Mat(col, shade(col, 0.35), shade(col, -0.6))
    sh = f"M{f(x - 4 * d)} {f(y)} Q{f(x)} {f(y + 2.2)} {f(x + 5 * d)} {f(y + 0.6)} Q{f(x + 7 * d)} {f(y - 0.6)} {f(x + 6.2 * d)} {f(y - 2.2)} Q{f(x + 3 * d)} {f(y - 2.4)} {f(x - 4 * d)} {f(y - 2.2)} Z"
    out = fill_form(sh, (x, y - 0.6), (-0.6, -1), 3, m)
    out += stroke(f"M{f(x - 3 * d)} {f(y - 2.2)} Q{f(x + 2 * d)} {f(y - 3)} {f(x + 5.8 * d)} {f(y - 2.1)}", "#F6F0E0", 1.0)
    out += K.ball(x + 2 * d, y - 1.0, 0.7, M["gold"], outline=False)
    out += stroke(f"M{f(x - 1 * d)} {f(y - 0.8)} q{f(1 * d)} -0.8 {f(2 * d)} 0", "#2E6A4A", 0.5)
    return out


def norigae(x, y):
    """노리개: knot, jade butterfly + coral bead, long red tassel."""
    out = stroke(f"M{f(x)} {f(y)} L{f(x + 0.5)} {f(y + 6)}", "#8A1A12", 0.6)
    out += K.ball(x + 0.5, y + 6.5, 1.5, M["gold"])
    # jade piece (butterfly-ish double lobe)
    jd = M["jade"]
    for s in (-1, 1):
        out += K.ball(x + 0.5 + s * 2.2, y + 10, 2.2, jd, ry=1.7)
    out += K.ball(x + 0.5, y + 10.4, 1.3, M["gold"])
    out += K.ball(x + 0.5, y + 14, 1.4, Mat("#C83A2A", "#FF9A80", "#3A0804", "#8A4A50", spec_op=0.8, gloss=0.9))
    out += tassel(x + 0.5, y + 15.6, "#B8221A", 11, 2.4)
    return out


def hero_mandeok():
    chima, jeo = M["chima"], M["galot"]
    rng = random.Random(812)
    out = ""
    # skirt (치마): bell from a high waist, swinging back with the lean
    sk = [(-12, -93), (12, -93), (18, -72), (25, -44), (32, -14), (33, -3), (18, 2), (-2, 4), (-20, 2), (-31, -2), (-30, -14), (-24, -42), (-18, -70)]
    d = smooth(sk)
    out += K.cast_shadow(2, 0, 34, 7.5, 0.6)
    out += fill_form(d, (1, -45), (-1, -0.12), 34, chima)
    inner = ""
    for i in range(11):
        u = (i + 0.5) / 11
        top = (-11 + 22 * u, -92)
        bot = (-30 + 63 * u, 1 + 3 * math.sin(u * math.pi))
        mid = lerp(top, bot, 0.55)
        inner += stroke(f"M{P(top)} Q{f(mid[0] + 1.5)} {f(mid[1])} {P(bot)}", chima.core, 2.2, 0.55, SOFT)
        inner += stroke(f"M{f(top[0] - 1.2)} {f(top[1] + 4)} Q{f(mid[0] - 0.5)} {f(mid[1])} {f(bot[0] - 1.8)} {f(bot[1])}", chima.light, 0.9, 0.45)
    inner += f'<rect x="-40" y="-95" width="80" height="6" fill="#050302" opacity="0.45"{SOFT}></rect>'
    inner += stroke("M-36 -8 Q2 -2 38 -10", "#C9A35A", 1.4, 0.5)                    # thin gilt band near the hem
    inner += stroke("M-36 -6.3 Q2 -0.3 38 -8.3", "#FFE9A0", 0.4, 0.5)
    inner += stroke("M-36 -1 Q2 7 38 -2", "#050302", 3, 0.5, SOFT)
    out += K.clip_overlay(d, inner)
    # wrap edge (치마 여밈) on the near side
    out += stroke("M-10 -92 Q-17 -50 -24 2", chima.light, 0.9, 0.6) + stroke("M-9.2 -92 Q-16.2 -50 -23.2 2", chima.core, 1.0, 0.5)
    # shoe tips
    out += kkotsin(-14, 3.5, -1, "#B8322A") + kkotsin(6, 4.5, 1, "#2E6A4A")
    # far arm (hanging, holding the abacus at the hip)
    out += K.arm((10, -104), (17, -90), (15, -78), jeo, w=4.8, sleeve=jeo, cuff=M["chima"], hand=None, back=True)
    out += abacus((9, -84), (33, -79), (31, -69), (7, -74))
    out += K.hand((15, -78), (0.3, 1), 3.4, M["skin_f"].back(0.12), kind="fist")
    # purse hanging from the far side
    out += stroke("M11 -91 Q22 -84 26 -66", "#8A1A12", 0.6)
    pm = Mat("#B8322A", "#F07A5A", "#3A0804", "#8A4A50", spec_op=0.3, gloss=0.4)
    purse = "M21 -66 Q20 -59 26 -56 Q32 -59 31 -66 Q26 -68 21 -66 Z"
    out += fill_form(purse, (26, -62), (-1, -0.3), 5, pm)
    out += K.clip_overlay(purse, stroke("M22 -63 q4 2 8 0", "#E2B13C", 0.6) + stroke("M23 -60 q3 1.5 6 0", "#2E6A4A", 0.5))
    out += K.ball(26, -66.5, 1.1, M["gold"])
    # jacket (저고리): short, 갈옷 brown
    t, dj = K.torso((0, -110), (0, -90), 12.5, 12.8, jeo)
    out += t
    inner = ""
    # chest volume (subtle, dignified)
    inner += ell(-5, -100, 5.5, 3.2, jeo.light, 0.35, SOFT) + ell(-3, -95.5, 7.5, 1.6, jeo.core, 0.4, SOFT)
    inner += ell(6, -99, 4.5, 3, jeo.core, 0.25, SOFT)
    # 깃 (indigo collar) + 동정
    inner += f'<path d="M-5 -112 L8 -94 L12.5 -96 L0 -113 Z" fill="{chima.base}"></path>'
    inner += stroke("M-5.3 -112 L8 -95.5", "#F6F0E0", 2.2) + stroke("M5 -112 L-1 -104", "#F6F0E0", 1.8, 0.9)
    inner += f'<rect x="-20" y="-92" width="40" height="3" fill="#050302" opacity="0.4"{SOFT}></rect>'
    # 도련 hem curve light
    inner += stroke("M-12 -90.5 Q0 -88.5 12 -91", jeo.light, 0.7, 0.5)
    out += K.clip_overlay(dj, inner)
    # 고름 (long red ribbons) + 노리개
    gm = Mat("#8E1C1A", "#E0584A", "#2A0404", "#7A3A44", spec_op=0.35, gloss=0.45)
    out += K.ball(8, -94.5, 2.0, gm)
    rib1 = "M7 -93.5 L10 -93.5 Q11 -80 9 -64 L5.8 -64.5 Q7.5 -80 7 -93.5 Z"
    out += fill_form(rib1, (8, -80), (-1, -0.05), 2.4, gm)
    rib2 = "M9 -93.5 L12 -93.5 Q14 -86 15 -78 L12.2 -77.6 Q11 -86 9 -93.5 Z"
    out += fill_form(rib2, (12, -86), (-1, -0.05), 2.2, gm.back(0.15))
    out += norigae(4, -93)
    # ledger tucked in the skirt band
    led = "M-13 -93 L-4 -95 L-3.5 -84 L-12.5 -82 Z"
    out += fill_form(led, (-8, -89), (-1, -0.3), 5, Mat("#2E4A7A", "#7A98C8", "#0A1428", "#4A5A80", spec_op=0.3, gloss=0.4))
    out += stroke("M-11.8 -92.4 L-11.4 -82.6", "#F4ECD4", 0.4) + "".join(K.ball(-11.6, -91 + i * 2.6, 0.35, M["gold"], outline=False) for i in range(4))
    out += f'<rect x="-9.5" y="-92" width="3.4" height="5" fill="#F4ECD4" transform="rotate(-10 -8 -90)"></rect>'
    # neck + bun (behind head, back-right because she faces left) + head
    out += neck((0.2, -109), (0.2, -116), 3.6, M["skin_f"])
    hairm = KM["hair"]
    out += K.ball(9.2, -118.5, 5.0, hairm, ry=4.0)
    out += K.form((4, -116), (14, -120), 1.0, 0.8, hairm, ao_start=False)
    out += stroke("M5.5 -120 q4 2.4 7.8 -0.4", "#9AA4C4", 0.5, 0.6)
    # 비녀 (silver hairpin with a dragon-head-ish end, our design: cloud knob)
    silver = Mat("#A8ACB0", "#F4F6F8", "#2A2C30", "#6A7A90", spec="#FFFFFF", spec_op=0.95, gloss=1.0)
    out += K.form((2, -117.4), (19.5, -120.2), 0.9, 0.7, silver, ao_start=False)
    out += K.ball(20, -120.4, 1.6, silver) + K.ball(21.2, -121.4, 0.9, M["jade"])
    hd, dh = hero_head(-0.5, -124, 9.4, turn=-0.3, female=True, hair=False, look=LOOK_MANDEOK)
    out += hd
    # hair: centre part, sleek bands sweeping back to the bun
    hg = K._sphere_grad(-4, -128, 11, hairm)
    hair = (f'<path d="M-14 -138 L14 -138 L12 -116 L6.5 -117 Q6 -124 1.5 -126.5 Q-1.5 -129.6 -3.4 -130 Q-6 -129.4 -8.4 -127 '
            f'Q-10 -124 -10.2 -121 L-14 -120 Z" fill="url(#{hg})"></path>')
    sheen = ell(-1.5, -131.2, 5.5, 1.6, "#9AA6CC", 0.45, SOFT)
    sheen += "".join(stroke(f"M{f(-3.4 + k * 0.5)} {f(-131 + k * 0.4)} Q{f(3 + k)} {f(-130.5 + k * 1.5)} {f(9 + k * 0.4)} {f(-122 + k * 1.2)}",
                            "#000" if k % 2 else "#6A7494", 0.25, 0.45) for k in range(6))
    sheen += stroke("M-3.4 -130 Q-1 -133.2 2 -133.6", "#000", 0.5, 0.8)
    sheen += stroke("M-3.6 -130 Q-7 -129 -9 -126", "#7A86A8", 0.4, 0.5)
    out += K.clip_overlay(dh, hair + sheen)
    # near arm: pouring rice from a measure toward the sack
    out += K.arm((-10, -104), (-21, -94), (-31, -86), jeo, w=4.8, sleeve=jeo, cuff=chima, hand=None)
    box, lip = doebak((-37, -86), -28, 1.1)
    out += box
    out += K.hand((-31, -86), (-1, 0.15), 3.4, M["skin_f"], kind="fist")
    out += rice_stream((lip[0] - 1, lip[1] + 1), (-50, -21), rng)
    return out


# ================================================================ HERO 3: 김유신 — sword raised under the burning kite

def lamellar_overlay(d, bbox, pw=3.4, ph=6.2, lace="#7A1A12", seed=1, rows_shift=0.0):
    """Rows of overlapping iron lamellae clipped to d; each plate catches its own light."""
    x0, y0, w, h = bbox
    inner = ""
    rows = int(h / (ph * 0.72)) + 2
    for r_ in range(rows):
        y = y0 + r_ * ph * 0.72 - 2
        off = (pw * 0.5) if r_ % 2 else 0
        cols = int(w / pw) + 3
        for c in range(cols):
            x = x0 - pw + c * pw + off + rows_shift * r_
            pl = (f"M{f(x + 0.3)} {f(y + 0.8)} Q{f(x + 0.3)} {f(y)} {f(x + pw * 0.5)} {f(y)} Q{f(x + pw - 0.3)} {f(y)} {f(x + pw - 0.3)} {f(y + 0.8)} "
                  f"L{f(x + pw - 0.3)} {f(y + ph - 1)} Q{f(x + pw * 0.5)} {f(y + ph + 0.4)} {f(x + 0.3)} {f(y + ph - 1)} Z")
            inner += f'<path d="{pl}" fill="url(#h3-plate)" stroke="#0A0C10" stroke-width="0.3"></path>'
            inner += stroke(f"M{f(x + 0.7)} {f(y + ph - 1.6)} L{f(x + 0.7)} {f(y + 0.9)} Q{f(x + 0.8)} {f(y + 0.35)} {f(x + pw * 0.55)} {f(y + 0.3)}", "#F4F8FF", 0.35, 0.75)
            inner += f'<circle cx="{f(x + pw * 0.5)}" cy="{f(y + 1.6)}" r="0.32" fill="#0A0604"></circle>'
        inner += stroke(f"M{f(x0 - pw)} {f(y + ph * 0.86)} H{f(x0 + w + pw)}", "#000", 1.0, 0.45, SOFT)
        inner += stroke(f"M{f(x0 - pw)} {f(y + 1.6)} H{f(x0 + w + pw)}", lace, 0.55, 0.85)
        inner += "".join(stroke(f"M{f(x0 + k * pw * 2 + off)} {f(y + 1.1)} l0.9 1", "#E8A08A", 0.3, 0.7) for k in range(int(w / (pw * 2)) + 1))
    cx, cy = x0 + w / 2, y0 + h / 2
    sg = shade_overlay((cx, cy), (-1, -0.15), w / 2 + 1, 0.2, 0.6)
    inner += f'<rect x="{f(x0 - 2)}" y="{f(y0 - 2)}" width="{f(w + 4)}" height="{f(h + 4)}" fill="url(#{sg})"></rect>'
    return K.clip_overlay(d, inner)


def helmet_neckguard(cx, y):
    """Lamellar neck guard hanging from the helmet rim — drawn BEHIND the head."""
    ir = M["iron"]
    ng = f"M{f(cx - 12)} {f(y + 1)} Q{f(cx - 15)} {f(y + 12)} {f(cx - 9)} {f(y + 18)} L{f(cx + 8)} {f(y + 16)} Q{f(cx + 14)} {f(y + 10)} {f(cx + 12)} {f(y + 1)} Z"
    out = fill_form(ng, (cx, y + 8), (-1, -0.2), 13, ir.back(0.25))
    out += lamellar_overlay(ng, (cx - 15, y, 29, 19), pw=2.6, ph=4.6, seed=3)
    return out


def silla_helmet(cx, y):
    """Bowl helmet of vertical riveted plates (our design), gilt brow band, red plume."""
    ir = M["iron"]
    out = ""
    # bowl
    bowl = f"M{f(cx - 11.5)} {f(y + 1)} Q{f(cx - 12)} {f(y - 14)} {f(cx)} {f(y - 15)} Q{f(cx + 12)} {f(y - 14)} {f(cx + 11.5)} {f(y + 1)} Q{f(cx)} {f(y + 3)} {f(cx - 11.5)} {f(y + 1)} Z"
    gid = K._sphere_grad(cx, y - 6, 13, ir)
    out += f'<path d="{bowl}" fill="url(#{gid})" stroke="{OUTLINE}" stroke-width="0.8"></path>'
    plates = ""
    for i in range(9):
        u = (i + 0.5) / 9
        x = cx - 11.5 + 23 * u
        plates += stroke(f"M{f(cx)} {f(y - 15)} Q{f(cx + (x - cx) * 1.15)} {f(y - 10)} {f(x)} {f(y + 1.5)}", "#0E1014", 0.45, 0.8)
        plates += stroke(f"M{f(cx + 0.3)} {f(y - 14.6)} Q{f(cx + (x - cx) * 1.15 + 0.5)} {f(y - 10)} {f(x + 0.5)} {f(y + 1.3)}", "#E8EEF8", 0.3, 0.5)
        for k in range(3):
            p = lerp((cx, y - 15), (x, y + 1.5), 0.35 + k * 0.25)
            plates += f'<circle cx="{f(p[0] + (x - cx) * 0.08)}" cy="{f(p[1])}" r="0.45" fill="url(#h3-rivet)"></circle>'
    out += K.clip_overlay(bowl, plates)
    # brow band (gilt) + visor peak
    out += K.form((cx - 12, y - 0.5), (cx + 12, y - 0.5), 1.8, 1.8, M["gold"], ao_start=False)
    for i in range(7):
        out += K.ball(cx - 10 + i * 3.4, y - 0.6, 0.55, ir, outline=False)
    out += f'<path d="M{f(cx - 7)} {f(y + 1.6)} Q{f(cx + 2)} {f(y + 4.6)} {f(cx + 12)} {f(y + 1.8)} L{f(cx + 11)} {f(y + 0.4)} Q{f(cx + 2)} {f(y + 2.6)} {f(cx - 7)} {f(y + 0.2)} Z" fill="#1A1C20"></path>'
    # finial + plume flying left (wind)
    out += K.form((cx, y - 15), (cx + 0.3, y - 21), 1.3, 0.9, M["gold"], ao_start=False)
    out += K.ball(cx + 0.3, y - 21.6, 1.8, M["gold"])
    pm = M["cape"]
    plume = (f"M{f(cx + 0.5)} {f(y - 22)} Q{f(cx - 10)} {f(y - 32)} {f(cx - 26)} {f(y - 27)} Q{f(cx - 34)} {f(y - 24)} {f(cx - 40)} {f(y - 15)} "
             f"Q{f(cx - 28)} {f(y - 19)} {f(cx - 20)} {f(y - 18)} Q{f(cx - 8)} {f(y - 18)} {f(cx + 0.5)} {f(y - 21)} Z")
    out += fill_form(plume, (cx - 18, y - 24), (-0.2, -1), 7, pm)
    out += K.clip_overlay(plume, "".join(stroke(f"M{f(cx)} {f(y - 21.5)} Q{f(cx - 14 - k * 2)} {f(y - 28 + k * 2.2)} {f(cx - 36 + k * 1.5)} {f(y - 18 + k * 0.6)}",
                                                pm.light if k % 2 else pm.core, 0.5, 0.6) for k in range(6)))
    return out


def ring_sword(hilt_a, dirv, blade_len):
    """환두대도 style straight sword: gilt ring pommel, cord-wrapped grip, guard, single-edged blade with glint."""
    L = math.hypot(*dirv)
    ux, uy = dirv[0] / L, dirv[1] / L
    nx, ny = -uy, ux
    grip_b = (hilt_a[0] + ux * 9, hilt_a[1] + uy * 9)
    out = ""
    ring_c = (hilt_a[0] - ux * 3.2, hilt_a[1] - uy * 3.2)
    out += f'<circle cx="{f(ring_c[0])}" cy="{f(ring_c[1])}" r="3.2" fill="none" stroke="url(#h3-goldring)" stroke-width="1.6"></circle>'
    out += f'<circle cx="{f(ring_c[0])}" cy="{f(ring_c[1])}" r="3.2" fill="none" stroke="#3A2408" stroke-width="0.3"></circle>'
    out += K.ball(ring_c[0] - 0.4, ring_c[1] - 1.2, 0.9, M["jade"], outline=False)
    out += K.form(hilt_a, grip_b, 1.7, 1.7, KM["leather_dark"], ao_start=False)
    out += K.bands(hilt_a, grip_b, 1.7, 1.7, KM["leather_dark"], n=6)
    gd = (grip_b[0] + ux * 0.8, grip_b[1] + uy * 0.8)
    out += K.form((gd[0] - nx * 3.4, gd[1] - ny * 3.4), (gd[0] + nx * 3.4, gd[1] + ny * 3.4), 1.3, 1.3, M["gold"], ao_start=False)
    b0 = (gd[0] + ux * 1.2, gd[1] + uy * 1.2)
    tip = (b0[0] + ux * blade_len, b0[1] + uy * blade_len)
    w = 2.0
    blade = (f"M{f(b0[0] + nx * w)} {f(b0[1] + ny * w)} L{f(tip[0] - ux * 5 + nx * w * 0.9)} {f(tip[1] - uy * 5 + ny * w * 0.9)} "
             f"L{P(tip)} L{f(b0[0] - nx * w * 0.9)} {f(b0[1] - ny * w * 0.9)} Z")
    out += fill_form(blade, lerp(b0, tip, 0.5), (nx if nx * -0.6 + ny * -0.8 > 0 else -nx, ny if nx * -0.6 + ny * -0.8 > 0 else -ny), w, KM["steel"])
    out += stroke(f"M{f(b0[0] + nx * 0.4)} {f(b0[1] + ny * 0.4)} L{f(tip[0] - ux * 6 + nx * 0.3)} {f(tip[1] - uy * 6 + ny * 0.3)}", "#3A4048", 0.4, 0.8)
    out += stroke(f"M{f(b0[0] + nx * w * 0.85)} {f(b0[1] + ny * w * 0.85)} L{f(tip[0] - ux * 4 + nx * w * 0.8)} {f(tip[1] - uy * 4 + ny * w * 0.8)}", "#FFFFFF", 0.45, 0.95)
    # glint star near the tip
    g = lerp(b0, tip, 0.82)
    out += (f'<circle cx="{f(g[0])}" cy="{f(g[1])}" r="5" fill="url(#h3-glint)"></circle>'
            + stroke(f"M{f(g[0] - 6)} {f(g[1])} H{f(g[0] + 6)} M{f(g[0])} {f(g[1] - 6)} V{f(g[1] + 6)}", "#FFFFFF", 0.4, 0.9))
    return out, tip


def eolle(c, s=1.0):
    """얼레: four-spoke kite reel with handle, cord wound on it."""
    wd = KM["wood"]
    out = ""
    out += K.form((c[0] - 6 * s, c[1] + 3 * s), (c[0] + 7 * s, c[1] - 3 * s), 1.0 * s, 1.0 * s, wd, ao_start=False)
    for dx, dy in ((-4.2, -5), (4.6, -6.8), (-4.6, 5.2), (4.2, 3.4)):
        out += K.form((c[0], c[1]), (c[0] + dx * s, c[1] + dy * s), 0.8 * s, 0.7 * s, wd, ao_start=False, spec=False)
    out += K.ball(c[0], c[1], 3.6 * s, Mat("#E8DDB8", "#FFFCEE", "#6A5A3A", "#8A8A90", spec_op=0.3, gloss=0.2), ry=2.8 * s)
    out += "".join(stroke(f"M{f(c[0] - 3.2 * s)} {f(c[1] + (k - 2) * 1.0 * s)} Q{f(c[0])} {f(c[1] + (k - 2) * 1.0 * s + 0.8)} {f(c[0] + 3.2 * s)} {f(c[1] + (k - 2) * 1.0 * s)}", "#9A8A60", 0.25, 0.7) for k in range(5))
    return out


def silla_belt(y0):
    """Gilt belt with openwork plaques and hanging pendants (곡옥, leaf, fish, chain)."""
    out = K.form((-17, y0), (17, y0 + 1), 2.4, 2.4, KM["leather_dark"], ao_start=False)
    for i in range(7):
        x = -14 + i * 4.6
        yy = y0 + 0.15 * i
        out += f'<rect x="{f(x - 1.7)}" y="{f(yy - 2)}" width="3.4" height="4" fill="url(#h3-gold)" stroke="#3A2408" stroke-width="0.35"></rect>'
        out += f'<path d="M{f(x - 0.8)} {f(yy - 1)} q0.8 1 0 2 M{f(x + 0.8)} {f(yy - 1)} q-0.8 1 0 2" stroke="#3A2408" stroke-width="0.35" fill="none"></path>'
        out += f'<circle cx="{f(x - 0.9)}" cy="{f(yy - 1.2)}" r="0.4" fill="#FFF6D0"></circle>'
    # pendants
    pend = [(-11, 14, "gogok"), (-6.5, 21, "leaf"), (-2, 26, "fish"), (3, 18, "gogok"), (8, 12, "leaf")]
    for x, ln, kind in pend:
        top = (x, y0 + 2)
        out += "".join(K.ball(x + 0.15 * k, y0 + 3 + k * 1.6, 0.55, M["gold"], outline=False) for k in range(int(ln / 1.6) - 1))
        e = (x + 0.15 * (int(ln / 1.6) - 1), y0 + 2 + ln)
        if kind == "gogok":
            gg = f"M{f(e[0])} {f(e[1])} a2.2 2.2 0 1 1 0.6 4.2 q-1.6 1.8 -3.2 1.2 q2 -0.6 2.6 -2.2 a2.2 2.2 0 0 1 0.6 -3.2 z"
            out += fill_form(gg, (e[0], e[1] + 2.5), (-1, -0.4), 2.6, M["jade"])
            out += K.ball(e[0] + 0.4, e[1] + 1.6, 0.5, M["gold"], outline=False)
        elif kind == "leaf":
            lf = f"M{f(e[0])} {f(e[1])} q2.4 2.4 0 6.4 q-2.4 -4 0 -6.4 z"
            out += fill_form(lf, (e[0], e[1] + 3), (-1, -0.2), 1.6, M["gold"])
        else:
            fs = f"M{f(e[0])} {f(e[1])} q1.6 3 0.4 6 l1.2 1.6 h-3 l1.2 -1.6 q-1.2 -3 0.2 -6 z"
            out += fill_form(fs, (e[0], e[1] + 3.5), (-1, -0.2), 1.8, M["gold"])
    return out


def hero_yusin():
    ir, cape, inn = M["iron"], M["cape"], M["cape_in"]
    trous = M["trous"]
    out = ""
    # cape: from the shoulders, blown back to the left by the wind
    cp = [(-15, -112), (14, -112), (21, -80), (18, -40), (8, -10), (-12, -4), (-38, -8), (-58, -22), (-62, -36), (-50, -60), (-36, -86), (-24, -106)]
    dcp = smooth(cp)
    out += fill_form(dcp, (-18, -60), (-0.6, -0.8), 40, cape)
    inner = ""
    for i in range(7):
        a = (-14 + i * 4.4, -110)
        b = (-56 + i * 9, -22 + i * 2.4)
        mid = lerp(a, b, 0.5)
        inner += stroke(f"M{P(a)} Q{f(mid[0] - 5)} {f(mid[1] + 3)} {P(b)}", cape.core, 3.0, 0.55, SOFT)
        inner += stroke(f"M{f(a[0] - 1.5)} {f(a[1] + 6)} Q{f(mid[0] - 7)} {f(mid[1] + 2)} {f(b[0] - 2.5)} {f(b[1] - 1)}", cape.light, 1.0, 0.5)
    inner += f'<path d="M-62 -36 Q-52 -30 -38 -8 L-60 -6 Z" fill="url(#h3-capein)"></path>'      # gold lining flipping out
    out += K.clip_overlay(dcp, inner)
    out += stroke("M-58 -22 Q-48 -10 -38 -8", inn.light, 1.2, 0.8)
    # far leg (lunging back)
    out += K.leg((7, -62), (19, -36), (27, -6), trous, m_shin=KM["leather_dark"], w=7.2, trouser=None, boot=KM["leather_dark"], foot_dir=1, back=True)
    # far arm raised, sword up and forward
    sh, el, wr = (15, -108), (28, -122), (33, -140)
    out += K.arm(sh, el, wr, ir, m_fore=KM["leather_dark"], w=6.2, hand=None, back=True)
    out += K.bands(el, wr, 5.2, 4.2, KM["leather_dark"].back(0.1), n=4)
    sword, tip = ring_sword((30, -141), (0.62, -0.78), 50)
    out += sword
    out += K.hand(wr, (0.62, -0.78), 3.8, KM["leather"].back(0.1), kind="fist")
    # near leg (forward, bent)
    out += K.leg((-6, -62), (-18, -40), (-21, -7), trous, m_shin=KM["leather_dark"], w=7.4, trouser=None, boot=KM["leather_dark"], foot_dir=-1)
    # boot cuffs (gilt) + puttee ties
    for kn, an in (((-18, -40), (-21, -7)), ):
        p = lerp(kn, an, 0.12)
        out += K.form((p[0] - 5.4, p[1]), (p[0] + 5.4, p[1] + 0.6), 1.4, 1.4, M["gold"], ao_start=False)
    # lamellar skirt over the thighs (split front)
    for pts_, m_ in (([(-17, -71), (2, -71), (-1, -38), (-27, -40)], ir), ([(1, -71), (17, -71), (24, -40), (2, -38)], ir.back(0.15))):
        dsk = smooth(pts_)
        xs = [p[0] for p in pts_]
        ys = [p[1] for p in pts_]
        out += fill_form(dsk, (sum(xs) / 4, sum(ys) / 4), (-1, -0.1), 12, m_)
        out += lamellar_overlay(dsk, (min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)), pw=3.4, ph=6.0, seed=5)
        out += stroke(f"M{P(pts_[3])} L{P(pts_[2])}", "#7A1A12", 1.2, 0.9)
    # torso cuirass
    t, d = K.torso((0, -113), (0, -70), 17, 14.5, ir, chest=0.6)
    out += t
    out += lamellar_overlay(d, (-19, -116, 38, 48), pw=3.2, ph=5.6, seed=7)
    # chest mirror plate (호심경) — gilt disc
    out += K.ball(-4, -98, 5.2, M["gold"])
    out += ell(-4, -98, 3.6, 3.6, "none", 1, ' stroke="#4A3008" stroke-width="0.4"')
    out += "".join(f'<path d="M{f(-4 + math.cos(a) * 1.4)} {f(-98 + math.sin(a) * 1.4)} L{f(-4 + math.cos(a) * 3.2)} {f(-98 + math.sin(a) * 3.2)}" stroke="#4A3008" stroke-width="0.35"></path>'
                   for a in [k * math.tau / 8 for k in range(8)])
    out += ell(-5.6, -100, 1.4, 0.8, "#FFFFFF", 0.7)
    # collar guard
    out += K.form((-9, -113), (9, -113), 2.6, 2.6, KM["leather_dark"], ao_start=False)
    # belt with pendants
    out += K.ao(0, -66, 16, 3, 0.55)
    out += silla_belt(-70)
    # pauldrons (layered lamellae)
    for cx_, cy_, m_ in ((-16, -108, ir), (16, -108, ir.back(0.15))):
        pd = f"M{f(cx_ - 8)} {f(cy_ - 3)} Q{f(cx_)} {f(cy_ - 9)} {f(cx_ + 8)} {f(cy_ - 3)} L{f(cx_ + 9)} {f(cy_ + 9)} Q{f(cx_)} {f(cy_ + 12)} {f(cx_ - 9)} {f(cy_ + 9)} Z"
        out += fill_form(pd, (cx_, cy_ + 2), (-1, -0.3), 9, m_)
        out += lamellar_overlay(pd, (cx_ - 10, cy_ - 9, 20, 22), pw=2.8, ph=4.8, seed=9)
    # neck + head + helmet
    out += helmet_neckguard(1.2, -134)
    out += neck((0.8, -112), (0.8, -120), 4.6)
    hd, _ = hero_head(1.2, -128, 10.2, turn=0.3, m=MATS["skin_ruddy"], look=LOOK_YUSIN)
    out += hd
    out += K.ao(2, -134, 12, 3.4, 0.6)
    out += silla_helmet(1.2, -134)
    # near arm: holding the kite reel at the waist
    out += K.arm((-16, -108), (-24, -90), (-15, -78), ir, m_fore=KM["leather_dark"], w=6.4, hand=None)
    out += K.bands((-24, -90), (-15, -78), 5.4, 4.4, KM["leather_dark"], n=4)
    out += eolle((-10, -76), 1.0)
    out += K.hand((-15, -78), (1, 0.6), 3.8, KM["leather"], kind="fist")
    return out, tip


# ================================================================ scenery & diorama

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


def diorama(cx, cy, s, seed, kind="field"):
    rng = random.Random(seed)
    iso = R.Iso(cx, cy - s * 1.5, s)
    out = ""
    # slab thickness (earth sides) under the patch
    top = iso.face_t(0, 0, 3, 3, 0)
    lf = [iso.P(0, 3, 0), iso.P(3, 3, 0), iso.P(3, 3, -0.35), iso.P(0, 3, -0.35)]
    rf = [iso.P(3, 0, 0), iso.P(3, 3, 0), iso.P(3, 3, -0.35), iso.P(3, 0, -0.35)]
    out += f'<polygon points="{R.pts(lf)}" fill="url(#r3-stone-lit)" filter="url(#r3-tx-stone)"></polygon>'
    out += f'<polygon points="{R.pts(rf)}" fill="url(#r3-stone-sh)" filter="url(#r3-tx-stone)"></polygon>'
    for k in range(1, 3):
        out += stroke(f"M{P(iso.P(0, 3, -0.12 * k))} L{P(iso.P(3, 3, -0.12 * k))} L{P(iso.P(3, 0, -0.12 * k))}", "#2A2418", 0.6, 0.5)
    out += f'<polygon points="{R.pts(top)}" fill="url(#r3-grass)" filter="url(#r3-tx-grass)"></polygon>'
    out += R.ground_patch(iso, 0, 0, 3, 3, rng, yard=(0.7, 0.7, 2.3, 2.3) if kind != "jeju" else None, n_tuft=140)
    if kind == "jeju":
        # basalt field wall (밭담) along the back edges — black porous stones
        for (a, b) in (((0.05, 0.1), (2.9, 0.1)), ((0.1, 0.05), (0.1, 2.9))):
            for i in range(14):
                t = (i + 0.5) / 14
                x = a[0] + (b[0] - a[0]) * t
                y = a[1] + (b[1] - a[1]) * t
                for z in (0, 0.22):
                    p = iso.P(x, y, z + 0.1)
                    rr = s * rng.uniform(0.09, 0.13)
                    out += R.K.ball(p[0], p[1], rr, Mat("#3A3634", "#7A7470", "#0A0908", "#4A5058", spec_op=0.25, gloss=0.3), ry=rr * 0.8)
                    out += f'<circle cx="{f(p[0] - rr * 0.3)}" cy="{f(p[1])}" r="{f(rr * 0.15)}" fill="#0A0908" opacity="0.7"></circle>'
    return out


# ================================================================ skill icons

def icon_frame(body, key, gid_bg):
    return (f'<svg width="46" height="46" viewBox="0 0 46 46" aria-hidden="true">'
            f'<rect x="1" y="1" width="44" height="44" rx="5" fill="url(#h3-iconrim)"></rect>'
            f'<rect x="4" y="4" width="38" height="38" rx="3" fill="url(#{gid_bg})"></rect>'
            f'<g clip-path="url(#h3-iconclip)">{body}</g>'
            f'<rect x="4" y="4" width="38" height="38" rx="3" fill="none" stroke="#000" stroke-width="1" opacity="0.6"></rect>'
            f'<path d="M5 22 V6 Q5 5 6 5 H40" stroke="#FFF6D8" stroke-width="0.8" fill="none" opacity="0.45"></path>'
            f'<rect x="31" y="31" width="13" height="13" rx="2" fill="#1A0E06" stroke="{OCHRE}" stroke-width="0.8"></rect>'
            f'<text x="37.5" y="41.2" font-family="IBM Plex Mono, monospace" font-size="9.5" font-weight="600" fill="#F6DE9A" text-anchor="middle">{key}</text></svg>')


def ic_map():
    return ('<path d="M9 13 L18 10 L28 13 L37 10 L37 32 L28 35 L18 32 L9 35 Z" fill="url(#h3-icpaper)" stroke="#3A2A18" stroke-width="0.8"></path>'
            '<path d="M18 10 V32 M28 13 V35" stroke="#6A5430" stroke-width="0.6"></path>'
            '<path d="M11 26 l3 -5 l3 4 l3 -6 l4 6" stroke="#2E4024" stroke-width="1.4" fill="none"></path>'
            '<path d="M12 31 Q20 24 30 28 T36 20" stroke="#2E5A7A" stroke-width="1.1" fill="none"></path>'
            '<circle cx="29" cy="20" r="6" fill="url(#r3-oreglow)"></circle><path d="M29 17 l2 3 l-2 3 l-2 -3 z" fill="#9FE3D0" stroke="#1E5A4E" stroke-width="0.4"></path>')


def ic_feet():
    return ('<path d="M8 36 L38 10" stroke="#E2B13C" stroke-width="1.6" stroke-dasharray="1 3.4"></path>'
            + "".join(f'<path d="M{8 + k * 6} {33 - k * 5} l2.4 -1.4" stroke="#F6DE9A" stroke-width="1.2"></path>' for k in range(5))
            + '<ellipse cx="17" cy="24" rx="3" ry="4.6" fill="#E8DCC0" transform="rotate(-35 17 24)"></ellipse>'
            '<ellipse cx="27" cy="16" rx="3" ry="4.6" fill="#E8DCC0" transform="rotate(-35 27 16)"></ellipse>'
            '<path d="M30 9 l6 -1 l-1 6" stroke="#FFF6D8" stroke-width="1.6" fill="none"></path>')


def ic_terrain():
    return ('<path d="M4 34 L16 16 L22 24 L30 12 L42 34 Z" fill="url(#h3-icmount)"></path>'
            '<path d="M16 16 L19 21 M30 12 L33 18" stroke="#F4F0E0" stroke-width="1.4"></path>'
            '<circle cx="31" cy="31" r="8" fill="#5A3418" stroke="#E2B13C" stroke-width="1.2"></circle>'
            '<circle cx="31" cy="31" r="5.6" fill="#EDE2C4"></circle><path d="M27.5 33.5 L34.5 28.5" stroke="#C8301E" stroke-width="1.4"></path>')


def ic_rice():
    out = ('<circle cx="23" cy="26" r="15" fill="url(#h3-icwarm)"></circle>'
           '<path d="M9 24 H37 Q36 36 23 37 Q10 36 9 24 Z" fill="#6A3A1C" stroke="#2A1408" stroke-width="0.8"></path>'
           '<path d="M10 25 H36" stroke="#C88A50" stroke-width="1"></path>'
           '<path d="M10 24 Q23 14 36 24 Z" fill="#F6EEDA"></path>')
    out += "".join(f'<ellipse cx="{12 + (k * 7) % 22}" cy="{22 - (k * 3) % 5}" rx="1.2" ry="0.6" fill="#FFFFFF"></ellipse>' for k in range(8))
    out += '<path d="M16 12 q-2 -3 0 -6 M23 11 q-2 -3 0 -6 M30 12 q-2 -3 0 -6" stroke="#FFF6D8" stroke-width="1.2" fill="none" opacity="0.7"></path>'
    return out


def ic_ledger():
    return ('<rect x="9" y="9" width="20" height="27" rx="1.5" fill="#2E4A7A" stroke="#0A1428" stroke-width="0.8"></rect>'
            '<rect x="12" y="12" width="7" height="12" fill="#F4ECD4"></rect>'
            + "".join(f'<circle cx="10.6" cy="{12 + k * 7}" r="0.9" fill="#E2B13C"></circle>' for k in range(4))
            + '<circle cx="31" cy="30" r="7" fill="url(#h3-gold)" stroke="#4A3008" stroke-width="0.8"></circle><rect x="29.2" y="28.2" width="3.6" height="3.6" fill="#4A3008"></rect>'
            '<circle cx="34" cy="21" r="5.4" fill="url(#h3-gold)" stroke="#4A3008" stroke-width="0.8"></circle><rect x="32.6" y="19.6" width="2.8" height="2.8" fill="#4A3008"></rect>'
            '<path d="M24 8 l4 4 m-4 0 l4 -4" stroke="#8CC79A" stroke-width="0"></path>')


def ic_sea():
    return ('<rect x="4" y="27" width="38" height="15" fill="#1E4A5E"></rect>'
            + "".join(f'<path d="M{4 + k * 7} {31 + (k % 2) * 4} q2 -2 3.5 0 t3.5 0" stroke="#8ABED0" stroke-width="0.8" fill="none"></path>' for k in range(6))
            + '<path d="M12 27 H33 L30 32 H15 Z" fill="#5A3A1C"></path><path d="M22 8 V27" stroke="#3A2414" stroke-width="1.2"></path>'
            '<path d="M22 9 Q33 14 31 25 L22 25 Z" fill="url(#h3-icsail)"></path>'
            '<path d="M5 12 q5 -3 10 0 M7 17 q5 -3 10 0" stroke="#F6EEDA" stroke-width="1" fill="none" opacity="0.8"></path>')


def ic_firekite():
    return ('<circle cx="23" cy="20" r="16" fill="url(#h3-icfire)"></circle>'
            '<rect x="14" y="8" width="17" height="22" fill="#E8E1C8" stroke="#3A2A18" stroke-width="0.8" transform="rotate(-8 22 19)"></rect>'
            '<circle cx="22.5" cy="20" r="3.4" fill="#2A1A0C" transform="rotate(-8 22 19)"></circle>'
            '<path d="M15 9 L30 9" stroke="#C8432B" stroke-width="2" transform="rotate(-8 22 19)"></path>'
            '<path d="M17 30 q-3 5 0 10 M27 29 q3 5 -1 10" stroke="#FFB040" stroke-width="2" fill="none"></path>'
            '<path d="M13 12 q-3 -6 3 -9 q-1 4 2 5 Z M31 10 q4 -5 -1 -9 q0 4 -2 5 Z" fill="#FFD066"></path>')


def ic_dash():
    return ('<path d="M6 40 Q16 30 22 20 T38 6" stroke="#E8DDB8" stroke-width="0.9" fill="none"></path>'
            '<path d="M8 28 L30 16 L26 24 L38 20 L14 36 L18 28 Z" fill="url(#h3-icgold)" stroke="#4A3008" stroke-width="0.7"></path>'
            '<path d="M4 22 H14 M6 27 H12 M8 32 H13" stroke="#F6DE9A" stroke-width="1.2" opacity="0.8"></path>')


def ic_oath():
    return ('<circle cx="23" cy="23" r="15" fill="url(#h3-icwarm)"></circle>'
            '<path d="M23 6 L25 30 L23 34 L21 30 Z" fill="url(#h3-icsteel)" stroke="#2A3038" stroke-width="0.6"></path>'
            '<circle cx="23" cy="37" r="3" fill="none" stroke="#E2B13C" stroke-width="1.6"></circle>'
            '<path d="M14 30 H32" stroke="#E2B13C" stroke-width="2"></path>'
            '<path d="M10 18 Q16 22 21 18 M36 18 Q30 22 25 18" stroke="#C8432B" stroke-width="2.2" fill="none"></path>')


# ================================================================ page

HEROES = [
    dict(code="H-C1", name="김정호", hanja="金正浩", role="정찰 · 지도 영웅", src="조선 후기 지리학자 · 『대동여지도』(1861)",
         lore="발로 재고 눈으로 새긴 팔도의 지도. 그 판목 끝에서 귀화석의 맥이 처음 드러났다.",
         stats=[("체력", 420, 900), ("공격", 12, 40), ("사거리", 5, 8), ("이동", 3.4, 4)],
         skills=[("Q", ic_map, "h3-icbg-teal", "대동여지도", "넓은 지역을 밝히고 귀화석 광맥을 표시", "45초"),
                 ("W", ic_feet, "h3-icbg-ochre", "십리 눈금", "주변 아군 이동 속도 +25% · 12초", "30초"),
                 ("E", ic_terrain, "h3-icbg-ink", "지세 판독", "지속 · 언덕과 숲 위 아군 사거리 +1", "지속")]),
    dict(code="H-C2", name="김만덕", hanja="金萬德", role="지원 · 경제 영웅", src="조선 정조대 제주 거상 · 구휼(1795)",
         lore="흉년에 모은 재산을 털어 쌀을 사 섬사람들을 먹였다. 그녀의 장부에는 셈보다 사람이 먼저 적혀 있다.",
         stats=[("체력", 480, 900), ("공격", 8, 40), ("사거리", 6, 8), ("이동", 2.8, 4)],
         skills=[("Q", ic_rice, "h3-icbg-ochre", "구휼미", "범위 아군 체력 회복 · 식량 +4", "40초"),
                 ("W", ic_ledger, "h3-icbg-ink", "객주 장부", "30초 동안 모든 수입 +20%", "90초"),
                 ("E", ic_sea, "h3-icbg-teal", "바닷바람 교역", "어느 나루터에서든 원격 교역", "60초")]),
    dict(code="H-C3", name="김유신", hanja="金庾信", role="연 장수 · 전투 영웅", src="신라 장군 · 『삼국사기』 비담의 난(647)",
         lore="떨어진 별을 다시 하늘로 올린 불연 한 점. 흔들리던 진영이 그 불빛을 보고 다시 일어섰다.",
         stats=[("체력", 760, 900), ("공격", 34, 40), ("사거리", 1, 8), ("이동", 3.0, 4)],
         skills=[("Q", ic_firekite, "h3-icbg-red", "불타는 연", "아군 사기 상승 · 주변 적 보병이 겁먹고 물러남", "50초"),
                 ("W", ic_dash, "h3-icbg-ochre", "연줄 비상", "연줄을 타고 지정 지점으로 도약 돌진", "18초"),
                 ("E", ic_oath, "h3-icbg-red", "화랑의 맹세", "지속 · 주변 아군 공격 +15% · 방어 +1", "지속")]),
]


def art_jeongho():
    rng = random.Random(41)
    W, H = 440, 420
    bg = (f'<rect width="{W}" height="{H}" fill="url(#h3-sky1)"></rect>'
          f'<circle cx="330" cy="120" r="70" fill="url(#h3-sun)"></circle>'
          + soft_ridge(210, 70, 3, "#4A5A6A", 0.55) + soft_ridge(240, 60, 5, "#34404A", 0.75)
          + f'<rect x="0" y="200" width="{W}" height="60" fill="url(#h3-haze)"></rect>'
          + ridge(285, 50, 9, "#222A26", 1.0, step=10)
          + "".join(pine(x, 290 - rng.uniform(0, 30), rng.uniform(0.6, 1.0), "#1A2420") for x in (30, 52, 380, 404, 420)))
    body = bg + diorama(220, 368, 46, 101)
    body += hero_ring(220, 370, 62, 18)
    body += '<ellipse cx="200" cy="230" rx="120" ry="170" fill="url(#h3-aura)"></ellipse>'
    body += K.cast_shadow(232, 372, 58, 13, 0.6)
    body += '<use href="#h3-fig1" transform="translate(220 374) scale(2.28)"></use>'
    return body


def art_mandeok():
    rng = random.Random(42)
    W, H = 440, 420
    bg = (f'<rect width="{W}" height="{H}" fill="url(#h3-sky2)"></rect>'
          f'<circle cx="110" cy="150" r="80" fill="url(#h3-sun2)"></circle>'
          f'<path d="M-10 236 Q120 150 220 150 Q330 150 450 236 Z" fill="#4A5262" opacity="0.7"></path>'   # 한라산 dome
          f'<path d="M180 158 q40 -10 80 0" stroke="#C8B8A0" stroke-width="1.2" opacity="0.4" fill="none"></path>'
          f'<rect x="0" y="232" width="{W}" height="80" fill="url(#h3-sea)"></rect>'
          + "".join(f'<path d="M{f(x)} {f(y)} q4 -1.6 8 0" stroke="#F6E6C0" stroke-width="0.8" opacity="{f(0.3 + (y - 236) / 160)}" fill="none"></path>'
                    for x, y in [(rng.uniform(0, 430), rng.uniform(240, 300)) for _ in range(50)])
          + "".join(f'<path d="M{x} {y} l0 -16 q8 6 6 16 Z" fill="#E2B13C" opacity="0.8"></path><path d="M{x - 6} {y} h12 l-2 3 h-8 Z" fill="#2A1A10"></path>'
                    for x, y in ((300, 262), (360, 254), (60, 258)))
          + soft_ridge(300, 18, 11, "#2A2E24", 1.0))
    body = bg + diorama(220, 368, 46, 102, kind="jeju")
    body += hero_ring(220, 370, 62, 18)
    body += '<ellipse cx="215" cy="250" rx="120" ry="150" fill="url(#h3-aura)"></ellipse>'
    # rice sacks behind her on the right + open sack she pours into on the left
    for i, (x, y, s_) in enumerate(((318, 356, 2.2), (296, 362, 2.0), (334, 340, 1.9))):
        body += R.sack(x, y, 8 * s_, 7 * s_, "hemp")
    body += K.cast_shadow(222, 372, 60, 13, 0.6)
    body += '<use href="#h3-fig2" transform="translate(222 372) scale(2.4)"></use>'
    body += f'<g transform="translate(222 372) scale(2.4)">{open_sack(-50, -1, 1.0)}{rice_pile(-50, -21.5, 9, 2.4, random.Random(5))}</g>'
    return body


def art_yusin():
    rng = random.Random(43)
    W, H = 440, 420
    bg = (f'<rect width="{W}" height="{H}" fill="url(#h3-sky3)"></rect>' + stars(rng, W, H, 90)
          + '<path d="M300 30 L250 70" stroke="url(#h3-meteor)" stroke-width="2.2" stroke-linecap="round"></path>'
          + '<circle cx="300" cy="30" r="3" fill="#FFF6D8"></circle>'
          + soft_ridge(268, 40, 21, "#1A1E2E", 1.0)
          + "".join(f'<circle cx="{x}" cy="{y}" r="7" fill="url(#r3-fireglow)"></circle><circle cx="{x}" cy="{y}" r="1.3" fill="#FFD066"></circle>'
                    for x, y in ((40, 262), (78, 268), (370, 258), (410, 264)))
          + "".join(f'<path d="M{x} {y} v-22" stroke="#0E0E14" stroke-width="1.2"></path><path d="M{x} {y - 22} l10 3 l-10 4 z" fill="#3A1A16"></path>'
                    for x, y in ((60, 268), (392, 262)))
          + soft_ridge(300, 14, 23, "#12141C", 1.0))
    # the burning shield-kite, high behind him to the left
    kite, (bx, by) = R.shield_kite(0, 0, 54, 70, -14, tails=True)
    kx, ky, ks = 92, 92, 1.0
    body = bg
    body += '<circle cx="92" cy="96" r="95" fill="url(#h3-kiteglow)"></circle>'
    body += f'<g transform="translate({kx} {ky}) scale({ks})">{kite}</g>'
    for (fx_, fy_, s_) in ((-30, -26, 1.0), (-6, -36, 1.3), (22, -34, 0.95), (32, -4, 0.8), (-32, 10, 0.75), (-26, 40, 0.9), (20, 42, 0.85), (2, 60, 0.7)):
        body += f'<g transform="translate({f(kx + fx_)} {f(ky + fy_)})">{R.flame(0, 0, s_)}</g>'
    body += "".join(f'<circle cx="{f(kx + rng.uniform(-50, 60))}" cy="{f(ky + rng.uniform(-70, 60))}" r="{f(rng.uniform(0.6, 1.5))}" fill="#FFC860" opacity="{f(rng.uniform(0.5, 1))}"></circle>' for _ in range(30))
    body += diorama(220, 368, 46, 103)
    body += hero_ring(222, 370, 64, 18)
    body += '<ellipse cx="225" cy="240" rx="130" ry="170" fill="url(#h3-aura)"></ellipse>'
    body += K.cast_shadow(222, 372, 64, 14, 0.6)
    # cord from the kite bridle down to the reel (reel local (-10,-76) at scale 2.05)
    rx_, ry_ = 222 + (-10) * 2.05, 372 + (-76) * 2.05
    body += f'<path d="M{f(kx + bx)} {f(ky + by)} Q{f(70)} {f(190)} {f(rx_)} {f(ry_)}" stroke="#F0E6C8" stroke-width="1.1" fill="none" opacity="0.95"></path>'
    body += '<use href="#h3-fig3" transform="translate(222 372) scale(2.05)"></use>'
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

    def rg(i, stops, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
        s = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"></stop>' for o, c, a in stops)
        fxy = f' fx="{fx}" fy="{fy}"' if fx is not None else ""
        return f'<radialGradient id="h3-{i}" cx="{cx}" cy="{cy}" r="{r}"{fxy}>{s}</radialGradient>'
    return "".join([
        lg("plate", [(0, "#DDE4EE", 1), (0.35, "#8A929E", 1), (0.8, "#3A3E46", 1), (1, "#1A1C20", 1)], 0, 0, 0.6, 1),
        rg("rivet", [(0, "#FFFFFF", 1), (0.5, "#A8B0BC", 1), (1, "#2A2E36", 1)], 0.35, 0.35, 0.6),
        lg("gold", [(0, "#FFF2C0", 1), (0.45, "#E2B13C", 1), (1, "#7A5214", 1)], 0, 0, 1, 1),
        lg("goldring", [(0, "#FFF2C0", 1), (0.5, "#D2A43A", 1), (1, "#6A4410", 1)], 0, 0, 1, 1),
        rg("glint", [(0, "#FFFFFF", 1), (0.3, "#FFF6D8", 0.6), (1, "#FFF6D8", 0)]),
        lg("capein", [(0, "#F4CF72", 1), (1, "#8A6420", 1)], 0, 0, 1, 1),
        lg("sky1", [(0, "#1A2032", 1), (0.45, "#4A4A52", 1), (0.75, "#B8885A", 1), (1, "#E2B06A", 1)]),
        rg("sun", [(0, "#FFF0C0", 0.95), (0.3, "#F2C878", 0.6), (1, "#E2A050", 0)]),
        lg("haze", [(0, "#D8C8A8", 0), (0.5, "#D8C8A8", 0.35), (1, "#D8C8A8", 0)]),
        lg("sky2", [(0, "#2A2A44", 1), (0.4, "#8A5A50", 1), (0.6, "#E2945A", 1), (0.75, "#F2C27A", 1)]),
        rg("sun2", [(0, "#FFF6D0", 1), (0.25, "#FFD488", 0.85), (1, "#F09A50", 0)]),
        lg("sea", [(0, "#C88A5A", 1), (0.2, "#4A6A7A", 1), (1, "#1A2E3A", 1)]),
        lg("sky3", [(0, "#070A18", 1), (0.6, "#141A34", 1), (1, "#2E2440", 1)]),
        lg("meteor", [(0, "#FFF6D8", 1), (1, "#FFF6D8", 0)], 0, 0, 1, 1),
        rg("kiteglow", [(0, "#FFB040", 0.55), (0.4, "#E2602E", 0.22), (1, "#E2602E", 0)]),
        rg("aura", [(0, OCHRE, 0.28), (0.55, OCHRE, 0.08), (1, OCHRE, 0)]),
        rg("ringglow", [(0.6, OCHRE, 0), (0.85, OCHRE, 0.35), (1, OCHRE, 0)]),
        lg("iconrim", [(0, "#F6DE9A", 1), (0.5, "#8A6A3A", 1), (1, "#3A2410", 1)], 0, 0, 1, 1),
        lg("icbg-teal", [(0, "#2E6A64", 1), (1, "#0E2422", 1)], 0, 0, 1, 1),
        lg("icbg-ochre", [(0, "#8A6420", 1), (1, "#2A1A08", 1)], 0, 0, 1, 1),
        lg("icbg-ink", [(0, "#3A3A4A", 1), (1, "#12121A", 1)], 0, 0, 1, 1),
        lg("icbg-red", [(0, "#8A2A1A", 1), (1, "#2A0A06", 1)], 0, 0, 1, 1),
        lg("icpaper", [(0, "#FBF2D8", 1), (1, "#C8B48A", 1)], 0, 0, 1, 1),
        lg("icmount", [(0, "#6A8A5A", 1), (1, "#22301E", 1)]),
        rg("icwarm", [(0, "#FFD488", 0.7), (1, "#FFD488", 0)]),
        lg("icsail", [(0, "#F6DE8E", 1), (1, "#B8862A", 1)], 0, 0, 1, 1),
        rg("icfire", [(0, "#FFD066", 0.85), (0.6, "#E2602E", 0.4), (1, "#E2602E", 0)]),
        lg("icgold", [(0, "#FFF2C0", 1), (1, "#C9902E", 1)], 0, 0, 1, 1),
        lg("icsteel", [(0, "#FFFFFF", 1), (1, "#7A808A", 1)], 0, 0, 1, 0),
        lg("medrim", [(0, "#FFF0C0", 1), (0.4, "#C9A35A", 1), (1, "#4A3010", 1)], 0, 0, 1, 1),
        rg("medbg1", [(0, "#E2B06A", 1), (1, "#3A3020", 1)], 0.5, 0.35, 0.7),
        rg("medbg2", [(0, "#F2C27A", 1), (1, "#4A2A20", 1)], 0.5, 0.35, 0.7),
        rg("medbg3", [(0, "#4A3A6A", 1), (1, "#0E1020", 1)], 0.5, 0.35, 0.7),
        rg("medvig", [(0.6, "#000", 0), (1, "#000", 0.55)]),
        '<clipPath id="h3-medclip"><circle cx="44" cy="44" r="37"></circle></clipPath>',
        '<clipPath id="h3-iconclip"><rect x="4" y="4" width="38" height="38" rx="3"></rect></clipPath>',
    ])


def page():
    fig1 = hero_jeongho()
    fig2 = hero_mandeok()
    fig3, _tip = hero_yusin()
    panels = (hero_panel(HEROES[0], art_jeongho, "h3-fig1", (1.2, -124), 2.5, "h3-medbg1", 0)
              + hero_panel(HEROES[1], art_mandeok, "h3-fig2", (-0.5, -120), 2.6, "h3-medbg2", 1)
              + hero_panel(HEROES[2], art_yusin, "h3-fig3", (1.2, -126), 2.3, "h3-medbg3", 2))
    title_style = (f"margin: 0; {R.SERIF}; font-weight: 800; font-size: 42px; line-height: 1.05; "
                   "background: linear-gradient(180deg, #FFF4CC 0%, #F2D58A 35%, #C9902E 70%, #8A5A16 100%); "
                   "-webkit-background-clip: text; background-clip: text; color: transparent; "
                   "filter: drop-shadow(0 2px 0 #1A0E06) drop-shadow(0 0 1px #000)")
    defs = (R.DEFS + extra_defs() + K.defs_svg()
            + f'<g id="h3-fig1">{fig1}</g><g id="h3-fig2">{fig2}</g><g id="h3-fig3">{fig3}</g>')
    html = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>보부상 연단 영웅</title>
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
<div style="position: relative; width: 1440px; height: 960px; box-sizing: border-box; background: radial-gradient(ellipse at 50% 0%, #2A2216 0%, {GROUND} 60%, #0E0C08 100%); color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; padding: 18px 40px 20px; display: flex; flex-direction: column; gap: 10px; overflow: hidden">

  <svg width="0" height="0" style="position: absolute; width: 0; height: 0" aria-hidden="true" focusable="false"><defs>{defs}</defs></svg>

  <header style="display: flex; align-items: center; gap: 16px">
    {R.svg('0 0 100 100', R.emblem(), 72, 72, '보부상 연단 문장')}
    <div style="display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0">
      <span style="{R.MONO}; font-size: 12px; letter-spacing: 0.14em; color: {MUTED}">HERO CODEX R3 · 삼파전 SAMPAJEON · 역사 · 설화 인물</span>
      <div style="display: flex; align-items: baseline; gap: 14px"><h1 style="{title_style}">영웅 · 보부상 연단</h1><span style="{R.MONO}; font-size: 18px; font-weight: 600; letter-spacing: 0.2em; color: {OCHRE}">HEROES</span></div>
    </div>
    <p style="margin: 0; max-width: 470px; font-size: 12.5px; line-height: 1.55; color: #D8CDB4">길 위의 사람들 — 지도를 그린 이, 쌀을 나눈 이, 불연을 띄운 이. 영웅은 한 진영에 하나씩 소환되며 발밑의 금빛 고리로 구분됩니다. 실존 인물은 존중을 담아 삼파전 세계관에 맞게 재해석했습니다.</p>
  </header>
  <div style="display: flex; height: 5px; box-shadow: 0 1px 0 #000"><span style="flex: 3; background: {R.RED}"></span><span style="flex: 2; background: {R.GREEN}"></span><span style="flex: 2; background: {OCHRE}"></span><span style="flex: 1; background: {R.BLUE}"></span><span style="flex: 6; background: linear-gradient(90deg, #4A2A16, transparent)"></span></div>

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
