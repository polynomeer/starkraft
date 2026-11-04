"""Generate HeroR1.dc.html — 영웅 · 조선 공방 (삼재 SAMJAE hero codex R1).

Heroes: 장영실 (engineer), 이순신 (commander), 김만덕 (support, art from the preserved 보부상 page).
Figures are built on figure_kit via r1_figs' Kit (ids renamed r1k- -> h1k-, r1- -> h1- at the end so this
artboard never shares ids with RaceR1). Feet at (0, 0); heroes stand ~175 units tall (about 7 heads).
Usage: python3 hero_r1.py [out_path]
"""
import math
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from figure_kit import Mat, MATS, mix, shade, f, P
from r1_lib import RED, RED_L, GREEN, BRASS, BRASS_L, ORE, ORE_L, INK, MUTED, defs, u, smoke_puff
import r1_lib
from r1_figs import KK, stud, _soft, _path, _defs_once, coat_skirt, brigandine_torso, human_head, shin_cover, HEMP, JADE, SKIN

from r3_bridge import H, R as KITE, to_prefix  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "design2", "project", "HeroR1.dc.html")
RNG = random.Random(4242)
r1_lib.RNG.seed(4242)
M = MATS

# ------------------------------------------------------------------ materials
SILK_BLUE = Mat("#2A6684", "#6AAECE", "#06161E", "#3C5A74", spec="#E8F6FF", spec_op=0.32, gloss=0.45)   # 청색 단령
LACQ = Mat("#1C1A18", "#5C5852", "#030303", "#34404E", spec="#F4F0E8", spec_op=0.6, gloss=0.85)        # 사모 / 목화
BOOT = Mat("#1E1A17", "#544A42", "#040302", "#383E4A", spec="#F0E8DC", spec_op=0.5, gloss=0.7)
CORD = Mat("#B8301C", "#F07048", "#3A0804", "#7A3A40", spec_op=0.25, gloss=0.3)
MADDER = Mat("#6E2C1A", "#B0603E", "#1C0604", "#4E3638", spec_op=0.18, gloss=0.22)                     # 철릭
IRON = M["iron"]
IRON_D = Mat("#3E4248", "#8E98A4", "#0A0C0E", "#3E5066", spec="#FFFFFF", spec_op=0.75, gloss=1.0)
BRONZE = Mat("#8A5E2A", "#D8A860", "#24140A", "#5E5048", spec="#FFF0D0", spec_op=0.8, gloss=0.95)
LEATHER = M["leather"]
LEATHER_D = M["leather_dark"]
NAVY = Mat("#1E2C54", "#5268A0", "#040818", "#3A4C6E", spec="#E0E8FF", spec_op=0.32, gloss=0.4)
CAPE = Mat("#A02A1A", "#E0633E", "#2A0604", "#6A3036", spec_op=0.22, gloss=0.3)
# Hero faces: designed from recorded facts (no portrait tracing). See SPEC_FACE.md.
LOOK_JANG = dict(skin="skin", shape="oval", eye="almond", lid="double", brow="arched", nose="straight", mouth="calm",
                 age=0.4, beard="goatee", cheek=0.5, gaze=(-0.6, -0.7), eye_size=1.08)          # curious 40s artisan-genius
LOOK_YI = dict(skin="skin_tan", shape="long", eye="sharp", eye_size=0.92, brow="straight", nose="straight", nose_len=1.12,
               mouth="calm", lip=0.3, age=0.5, beard="thin_long", cheek=0.75, jaw=0.95, gaze=(-0.3, -0.2))  # 『징비록』: 단아, 근신하는 선비
LOOK_CHOI = dict(shape="square", jaw=1.12, eye="narrow", brow="thick", brow_col="#8C8780", nose="broad", mouth="shout",
                 age=0.85, beard="full", beard_col="#9A948A", scar="cheek", cheek=0.75, gaze=(-1, -0.2))   # 50s–60s at 진포 (1380)
GREY_HAIR = Mat("#8C8780", "#E4DED4", "#2A2622", "#6A7078", spec="#FFFFFF", spec_op=0.4, gloss=0.5)
SKIN_W = Mat("#B47A52", "#E8B488", "#3A1C0C", "#7A5244")  # weathered skin


def soft(x, y, rx, ry, col, op):
    return _soft(x, y, rx, ry, col, op)


def pth(d, fill="none", **a):
    return _path(d, fill, **a)


def grad_fill(d, center, normal, w, m, outline=True, sw=0.9):
    gid = KK._cyl_grad(center, normal, w, m)
    o = f' stroke="#120D08" stroke-width="{sw}" stroke-opacity="0.75" stroke-linejoin="round"' if outline else ""
    return f'<path d="{d}" fill="url(#{gid})"{o}></path>'


def ring(cx, cy, rx, ry, rot, w, m, front=True):
    """A shaded brass ring (ellipse band). front=False draws only the back half (darker)."""
    gid = KK._cyl_grad((cx, cy), (-0.6, -0.8), max(rx, ry), m)
    half = (f"M{f(cx - rx)} {f(cy)} A{f(rx)} {f(ry)} 0 0 0 {f(cx + rx)} {f(cy)}" if front
            else f"M{f(cx - rx)} {f(cy)} A{f(rx)} {f(ry)} 0 0 1 {f(cx + rx)} {f(cy)}")
    tr = f' transform="rotate({f(rot)} {f(cx)} {f(cy)})"'
    op = "1" if front else "0.75"
    return (f'<g{tr} opacity="{op}"><path d="{half}" fill="none" stroke="#120D08" stroke-width="{f(w + 1.2)}" stroke-opacity="0.8"></path>'
            f'<path d="{half}" fill="none" stroke="url(#{gid})" stroke-width="{f(w)}"></path>'
            f'<path d="{half}" fill="none" stroke="{m.spec}" stroke-width="{f(w * 0.25)}" stroke-opacity="{0.7 if front else 0.25}" '
            f'transform="translate({f(-w * 0.2)} {f(-w * 0.25)})"></path></g>')


# ------------------------------------------------------------------ dioramas (art space)
def _dia(cx, cy, rx, ry, a, b):
    """Point on the iso diamond: a along top->right, b along top->left (both 0..1)."""
    tx, ty = cx, cy - ry
    return (tx + a * rx - b * rx, ty + a * ry + b * ry)


def diorama(cx, cy, rx, ry, kind):
    out = []
    T = 16
    top, right, bot, left = (cx, cy - ry), (cx + rx, cy), (cx, cy + ry), (cx - rx, cy)
    out.append(f'<ellipse cx="{f(cx + 14)}" cy="{f(cy + ry + 10)}" rx="{f(rx * 1.05)}" ry="{f(ry * 0.5)}" fill="#000" opacity="0.6" filter="url(#r1-shadow-l)"></ellipse>')
    # slab faces: SW lit, SE shade
    lf = [left, bot, (bot[0], bot[1] + T), (left[0], left[1] + T)]
    rf = [bot, right, (right[0], right[1] + T), (bot[0], bot[1] + T)]
    face_l = {"stone": "#8A7E66", "scorch": "#6A5236", "deck": "#7A5232"}[kind]
    face_r = {"stone": "#4A4234", "scorch": "#3A2A1C", "deck": "#3E2818"}[kind]
    out.append(f'<polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in lf)}" fill="{face_l}" filter="url(#r1-stone)" stroke="#120D08" stroke-width="1"></polygon>')
    out.append(f'<polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in rf)}" fill="{face_r}" filter="url(#r1-stone)" stroke="#120D08" stroke-width="1"></polygon>')
    if kind in ("stone", "scorch"):
        # stone courses on the faces
        for k in range(1, 3):
            yy = T * k / 3
            out.append(pth(f"M{P((left[0], left[1] + yy))} L{P((bot[0], bot[1] + yy))} L{P((right[0], right[1] + yy))}", stroke="#1A140C", stroke_width=0.8, opacity="0.6"))
        for i in range(1, 12):
            t = i / 12
            p = (left[0] + (bot[0] - left[0]) * t, left[1] + (bot[1] - left[1]) * t)
            q = (bot[0] + (right[0] - bot[0]) * t, bot[1] + (right[1] - bot[1]) * t)
            off = (i % 2) * T / 3
            out.append(pth(f"M{P((p[0], p[1] + off))} l0 {f(T / 3)}", stroke="#1A140C", stroke_width=0.7, opacity="0.5"))
            out.append(pth(f"M{P((q[0], q[1] + off))} l0 {f(T / 3)}", stroke="#0A0806", stroke_width=0.7, opacity="0.5"))
    else:
        for k in range(1, 4):
            yy = T * k / 4
            out.append(pth(f"M{P((left[0], left[1] + yy))} L{P((bot[0], bot[1] + yy))} L{P((right[0], right[1] + yy))}", stroke="#1A0E06", stroke_width=0.8, opacity="0.7"))
    # top surface
    topc = {"stone": "url(#r1-yard)", "scorch": "url(#r1-ground)", "deck": "#8A6040"}[kind]
    out.append(f'<polygon points="{f(top[0])},{f(top[1])} {f(right[0])},{f(right[1])} {f(bot[0])},{f(bot[1])} {f(left[0])},{f(left[1])}" '
               f'fill="{topc}" filter="url(#r1-gtex)" stroke="#2A2214" stroke-width="1"></polygon>')
    if kind == "stone":
        n = 6
        for i in range(n):
            for j in range(n):
                jit = [(RNG.uniform(-0.012, 0.012), RNG.uniform(-0.012, 0.012)) for _ in range(4)]
                a0, a1, b0, b1 = i / n + 0.008, (i + 1) / n - 0.008, j / n + 0.008, (j + 1) / n - 0.008
                pts = [_dia(cx, cy, rx, ry, a0 + jit[0][0], b0 + jit[0][1]), _dia(cx, cy, rx, ry, a1 + jit[1][0], b0 + jit[1][1]),
                       _dia(cx, cy, rx, ry, a1 + jit[2][0], b1 + jit[2][1]), _dia(cx, cy, rx, ry, a0 + jit[3][0], b1 + jit[3][1])]
                tone = RNG.choice(["#A89C80", "#9A8E72", "#B4A88C", "#8E846A", "#A09478"])
                out.append(f'<polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in pts)}" fill="{tone}" stroke="#3A3226" stroke-width="1.2" '
                           f'stroke-linejoin="round" filter="url(#r1-stone)"></polygon>')
                out.append(pth(f"M{P(pts[3])} L{P(pts[0])} L{P(pts[1])}", stroke="#F2EAD4", stroke_width=0.8, opacity="0.45"))
        # moss in the joints
        for _ in range(30):
            a, b = RNG.random(), RNG.random()
            x, y = _dia(cx, cy, rx, ry, a, b)
            out.append(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(RNG.uniform(2, 5))}" ry="{f(RNG.uniform(1, 2))}" fill="#5E6A30" opacity="0.6"></ellipse>')
    elif kind == "deck":
        n = 11
        for i in range(n):
            a0, a1 = i / n, (i + 1) / n
            pts = [_dia(cx, cy, rx, ry, a0, 0), _dia(cx, cy, rx, ry, a1, 0), _dia(cx, cy, rx, ry, a1, 1), _dia(cx, cy, rx, ry, a0, 1)]
            tone = RNG.choice(["#8A6040", "#7E5636", "#946A46", "#86603E"])
            out.append(f'<polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in pts)}" fill="{tone}" stroke="#2A1608" stroke-width="1.1" filter="url(#r1-wood)"></polygon>')
            out.append(pth(f"M{P(pts[0])} L{P(pts[3])}", stroke="#D8A878", stroke_width=0.7, opacity="0.5"))
            for b in (0.12, 0.5, 0.88):
                x, y = _dia(cx, cy, rx, ry, (a0 + a1) / 2, b)
                out.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="1.1" fill="#1A1008"></circle><circle cx="{f(x - 0.3)}" cy="{f(y - 0.3)}" r="0.4" fill="#D8C8A8"></circle>')
            # butt joints
            bj = RNG.uniform(0.25, 0.75)
            p1, p2 = _dia(cx, cy, rx, ry, a0, bj), _dia(cx, cy, rx, ry, a1, bj)
            out.append(pth(f"M{P(p1)} L{P(p2)}", stroke="#2A1608", stroke_width=1))
    else:  # scorch
        out.append(f'<ellipse cx="{f(cx - 30)}" cy="{f(cy - 4)}" rx="{f(rx * 0.5)}" ry="{f(ry * 0.5)}" fill="url(#r1-scorch)"></ellipse>')
        out += r1_lib.tufts(cx, cy, rx * 0.75, ry * 0.7, 26)
        out += r1_lib.pebbles(cx, cy, rx * 0.7, ry * 0.7, 14)
        out += r1_lib.leaves(cx, cy, rx * 0.7, ry * 0.7, 10)
        for _ in range(18):
            x, y = _dia(cx, cy, rx, ry, RNG.uniform(0.15, 0.7), RNG.uniform(0.2, 0.8))
            out.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(RNG.uniform(0.6, 1.4))}" fill="#FFB040" filter="url(#r1-glow)" opacity="0.85"></circle>')
    return "".join(out)


def aura(cx, cy, col, rx=150, ry=46):
    """Hero aura: ground ring with rune ticks + a soft column of light behind the figure."""
    out = [f'<ellipse cx="{f(cx)}" cy="{f(cy - 170)}" rx="120" ry="200" fill="{col}" opacity="0.13" filter="url(#r1-glow-l)"></ellipse>',
           f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx * 0.62)}" ry="{f(ry * 0.62)}" fill="none" stroke="{col}" stroke-width="3" opacity="0.35" filter="url(#r1-glow)"></ellipse>',
           f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx * 0.62)}" ry="{f(ry * 0.62)}" fill="none" stroke="{mix(col, "#FFFFFF", 0.4)}" stroke-width="1" opacity="0.8"></ellipse>',
           f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx * 0.7)}" ry="{f(ry * 0.7)}" fill="none" stroke="{col}" stroke-width="1" opacity="0.55" stroke-dasharray="2 6"></ellipse>']
    for k in range(16):
        a = math.tau * k / 16
        x0, y0 = cx + math.cos(a) * rx * 0.64, cy + math.sin(a) * ry * 0.64
        x1, y1 = cx + math.cos(a) * rx * 0.69, cy + math.sin(a) * ry * 0.69
        out.append(pth(f"M{f(x0)} {f(y0)} L{f(x1)} {f(y1)}", stroke=mix(col, "#FFFFFF", 0.3), stroke_width=1.4, opacity="0.75"))
    return "".join(out)


# ------------------------------------------------------------------ shared hero parts
def boot_mokhwa(hip, knee, ankle, w, back=False, dirx=-1):
    """목화: tall black lacquered-felt boot from below the knee, with a white sole edge (follows the leg curve)."""
    m = BOOT.back() if back else BOOT
    out = [shin_cover(hip, knee, ankle, 0.6, w * 0.95, w * 0.72, m)]
    S = KK._catmull([hip, knee, ankle], 8)
    top = next((x, y) for (x, y, t) in S if t >= 0.6)
    out.append(KK.form((top[0] - w * 0.92, top[1] + 0.4), (top[0] + w * 0.92, top[1] - 0.4), 1.4, 1.4, m, ao_start=False, spec=False))
    out.append(KK.foot(ankle, w * 0.85, m, dirx, False))
    x, y = ankle
    s = w * 0.85
    out.append(pth(f"M{f(x - s * 0.6)} {f(y + s * 1.02)} Q{f(x + dirx * s * 0.6)} {f(y + s * 1.32)} {f(x + dirx * s * 1.85)} {f(y + s * 0.98)}",
                   stroke="#E8E0CC", stroke_width=1.1, opacity="0.85"))
    return "".join(out)


def armillary(cx, cy, R):
    """혼천의: brass meridian/horizon/equator/ecliptic rings with gear teeth around a glowing goblin-fire core."""
    B = M["brass"]
    out = [f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(R * 2.3)}" fill="url(#r1-ore-glow)" filter="url(#r1-glow-l)"></circle>']
    # back halves first
    for rx, ry, rot in ((R, R * 0.34, 0), (R, R * 0.3, -24), (R * 0.96, R * 0.3, 32)):
        out.append(ring(cx, cy, rx, ry, rot, 2.2, B.back(0.3), front=False))
    # core + rays
    for k in range(12):
        a = math.tau * k / 12 + 0.2
        ln = R * (0.9 + 0.5 * (k % 3) / 2)
        out.append(pth(f"M{f(cx + math.cos(a) * R * 0.3)} {f(cy + math.sin(a) * R * 0.3)} L{f(cx + math.cos(a) * ln)} {f(cy + math.sin(a) * ln)}",
                       stroke=ORE_L, stroke_width=1.1, opacity="0.55", filter="url(#r1-glow)"))
    out.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(R * 0.42)}" fill="url(#r1-ore-glow)" filter="url(#r1-glow)"></circle>')
    out.append(KK.ball(cx, cy, R * 0.24, Mat("#7ED8C0", "#E8FFF8", "#1E5A50", "#4AA0B0", spec="#FFFFFF", spec_op=0.9, gloss=1.0)))
    # meridian (vertical full ring) with gear teeth
    out.append(ring(cx, cy, R * 1.12, R * 1.12, 0, 3.2, B, front=False))
    for k in range(36):
        a = math.tau * k / 36
        x0, y0 = cx + math.cos(a) * R * 1.2, cy + math.sin(a) * R * 1.2
        x1, y1 = cx + math.cos(a) * R * 1.32, cy + math.sin(a) * R * 1.32
        lit = math.cos(a) * -0.6 + math.sin(a) * -0.8
        col = mix(B.core, B.light, 0.5 + 0.5 * lit)
        out.append(pth(f"M{f(x0)} {f(y0)} L{f(x1)} {f(y1)}", stroke=col, stroke_width=2.4))
    out.append(ring(cx, cy, R * 1.12, R * 1.12, 0, 3.2, B, front=True))
    for rx, ry, rot in ((R, R * 0.34, 0), (R, R * 0.3, -24), (R * 0.96, R * 0.3, 32)):
        out.append(ring(cx, cy, rx, ry, rot, 2.2, B, front=True))
    # graduations on the horizon ring
    for k in range(18):
        a = math.pi * k / 17
        x, y = cx - math.cos(a) * R, cy + math.sin(a) * R * 0.34
        out.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="0.55" fill="#2A1A08"></circle>')
    out.append(KK.ball(cx, cy - R * 1.32, 2.4, B))
    return "".join(out)


def star_chart(x0, y0, w, h):
    """Unrolled hanji star chart hanging from a roller (top edge at y0)."""
    d = (f"M{f(x0)} {f(y0)} L{f(x0 + w)} {f(y0)} C{f(x0 + w + 2)} {f(y0 + h * 0.4)} {f(x0 + w - 3)} {f(y0 + h * 0.75)} {f(x0 + w + 1)} {f(y0 + h)} "
         f"L{f(x0 + 2)} {f(y0 + h + 3)} C{f(x0 - 2)} {f(y0 + h * 0.7)} {f(x0 + 2)} {f(y0 + h * 0.35)} {f(x0)} {f(y0)} Z")
    paper = Mat("#E8D8B0", "#FFF6DC", "#6A5434", "#A89A88", spec_op=0.1, gloss=0.05)
    out = [soft(x0 + w * 0.6, y0 + h * 0.6, w * 0.5, h * 0.45, "#000", 0.45),
           grad_fill(d, (x0 + w / 2, y0 + h / 2), (-1, -0.2), w * 0.6, paper)]
    inner = []
    ccx, ccy = x0 + w / 2, y0 + h * 0.46
    for rr in (w * 0.38, w * 0.26, w * 0.12):
        inner.append(f'<circle cx="{f(ccx)}" cy="{f(ccy)}" r="{f(rr)}" fill="none" stroke="#7A2A18" stroke-width="0.5" opacity="0.8"></circle>')
    for k in range(12):
        a = math.tau * k / 12
        inner.append(pth(f"M{f(ccx)} {f(ccy)} L{f(ccx + math.cos(a) * w * 0.38)} {f(ccy + math.sin(a) * w * 0.38)}", stroke="#7A2A18", stroke_width=0.3, opacity="0.6"))
    for _ in range(40):
        a, r = RNG.random() * math.tau, math.sqrt(RNG.random()) * w * 0.37
        inner.append(f'<circle cx="{f(ccx + math.cos(a) * r)}" cy="{f(ccy + math.sin(a) * r)}" r="{f(RNG.uniform(0.35, 0.8))}" fill="#1A2A44"></circle>')
    for k in range(5):
        a1, a2 = RNG.random() * math.tau, RNG.random() * math.tau
        r1_, r2_ = RNG.uniform(3, w * 0.35), RNG.uniform(3, w * 0.35)
        inner.append(pth(f"M{f(ccx + math.cos(a1) * r1_)} {f(ccy + math.sin(a1) * r1_)} L{f(ccx + math.cos(a2) * r2_)} {f(ccy + math.sin(a2) * r2_)}", stroke="#1A2A44", stroke_width=0.35))
    for k in range(4):
        inner.append(pth(f"M{f(x0 + 3)} {f(y0 + h * 0.86 + k * 2.2)} l{f(w - 6)} 0", stroke="#3A2A1A", stroke_width=0.6, opacity="0.55", stroke_dasharray="2 1"))
    # paper fold shading
    for t in (0.33, 0.66):
        inner.append(pth(f"M{f(x0)} {f(y0 + h * t)} l{f(w + 2)} 1", stroke="#6A5434", stroke_width=1.6, opacity="0.35", filter="url(#r1k-soft)"))
    out.append(KK.clip_overlay(d, "".join(inner)))
    # top roller with knobs + bottom curl
    out.append(KK.form((x0 - 3, y0), (x0 + w + 3, y0), 1.8, 1.8, M["lacquer"], ao_start=False))
    out.append(KK.ball(x0 - 3.5, y0, 2.2, M["brass"]))
    out.append(KK.ball(x0 + w + 3.5, y0, 2.2, M["brass"]))
    out.append(KK.form((x0 + 1, y0 + h + 1.5), (x0 + w + 1, y0 + h), 1.6, 1.6, paper, ao_start=False))
    return "".join(out)


def danryeong_skirt(waist_y, hem_y, ww, hw, m, slit_x=-6):
    """Long 단령 robe skirt flaring in a stride, with a front slit that shows the trousers, hem lining and folds."""
    d = (f"M{f(-ww)} {f(waist_y)} C{f(-ww - 6)} {f(waist_y + 20)} {f(-hw - 6)} {f(hem_y - 18)} {f(-hw - 2)} {f(hem_y)} "
         f"Q{f(-hw * 0.6)} {f(hem_y + 5)} {f(slit_x - 4)} {f(hem_y + 2)} L{f(slit_x)} {f(waist_y + 30)} L{f(slit_x + 3)} {f(hem_y + 3)} "
         f"Q{f(hw * 0.4)} {f(hem_y + 7)} {f(hw + 2)} {f(hem_y + 1)} C{f(hw + 1)} {f(hem_y - 18)} {f(ww + 4)} {f(waist_y + 18)} {f(ww)} {f(waist_y)} Z")
    out = [grad_fill(d, (0, (waist_y + hem_y) / 2), (-1, -0.15), hw * 1.15, m)]
    inner = []
    for x, op in ((-18, 0.45), (-10, 0.3), (8, 0.4), (16, 0.5), (22, 0.35)):
        inner.append(pth(f"M{f(x * 0.55)} {f(waist_y + 4)} Q{f(x * 1.05)} {f((waist_y + hem_y) / 2)} {f(x * 1.2)} {f(hem_y + 4)}", stroke=m.core,
                         stroke_width=3.2, opacity=f"{op}", filter="url(#r1k-soft)"))
        inner.append(pth(f"M{f(x * 0.55 - 2)} {f(waist_y + 4)} Q{f(x * 1.05 - 2.2)} {f((waist_y + hem_y) / 2)} {f(x * 1.2 - 2.4)} {f(hem_y + 4)}",
                         stroke=m.light, stroke_width=1.1, opacity=f"{op * 0.9}"))
    # embroidered hem band (gold cloud scroll)
    inner.append(pth(f"M{f(-hw - 8)} {f(hem_y - 4)} Q{f(-hw * 0.5)} {f(hem_y + 1)} 0 {f(hem_y)} Q{f(hw * 0.5)} {f(hem_y + 2)} {f(hw + 8)} {f(hem_y - 3)}",
                     stroke="#C9A35A", stroke_width=1.3, opacity="0.7"))
    for k in range(9):
        x = -hw + k * hw * 0.25
        inner.append(pth(f"M{f(x)} {f(hem_y - 6 + abs(x) * 0.05)} q2 -3 4 0 q-2 1 -2 -1", stroke="#E9C77A", stroke_width=0.6, opacity="0.7"))
    inner.append(soft(0, waist_y + 4, ww + 6, 5, "#0A0604", 0.6))
    inner.append(soft(slit_x + 1, waist_y + 40, 4, 22, "#0A0604", 0.55))
    out.append(KK.clip_overlay(d, "".join(inner)))
    return "".join(out), d


def poomdae(y, w):
    """품대: stiff hoop belt hanging low in front with ivory plaques."""
    out = []
    d = f"M{f(-w - 2)} {f(y - 3)} Q0 {f(y + 9)} {f(w + 2)} {f(y - 3)}"
    out.append(pth(d, stroke="#120D08", stroke_width=6.4, stroke_linecap="round"))
    out.append(pth(d, stroke="#3A1A10", stroke_width=5))
    out.append(pth(d, stroke="#8A4A30", stroke_width=1.2, opacity="0.7", transform="translate(0 -1.6)"))
    ivory = Mat("#D8C8A0", "#FFF8E0", "#5A4A2A", "#9A9488", spec_op=0.7, gloss=0.8)
    for k in range(-3, 4):
        t = (k + 3.5) / 7
        x = -w - 2 + t * (2 * w + 4)
        yy = y - 3 + 4 * (1 - (2 * t - 1) ** 2) * 2.4
        out.append(KK.ball(x, yy, 2.2 if k else 2.8, ivory, ry=1.9 if k else 2.4))
    return "".join(out)


def samo(cx, cy, s=1.0):
    """사모: two-tier lacquered official hat with side wings; goggles pushed up on the front band. cy = band centre."""
    out = [soft(cx + 3, cy + 3, 13 * s, 3 * s, "#000", 0.5)]
    # wings (behind the crown)
    for sx, back in ((-1, False), (1, True)):
        m = LACQ.back() if back else LACQ
        wx = cx + sx * 17 * s
        wy = cy - 9 * s
        out.append(f'<ellipse cx="{f(wx)}" cy="{f(wy)}" rx="{f(8 * s)}" ry="{f(3.4 * s)}" transform="rotate({f(sx * -12)} {f(wx)} {f(wy)})" '
                   f'fill="{m.base}" stroke="#050403" stroke-width="0.9" opacity="0.95"></ellipse>')
        out.append(f'<ellipse cx="{f(wx)}" cy="{f(wy)}" rx="{f(6.2 * s)}" ry="{f(2.2 * s)}" transform="rotate({f(sx * -12)} {f(wx)} {f(wy)})" '
                   f'fill="none" stroke="{m.light}" stroke-width="0.5" opacity="0.6" stroke-dasharray="0.8 0.8"></ellipse>')
        out.append(KK.form((cx + sx * 7 * s, cy - 8 * s), (wx - sx * 6 * s, wy), 0.9 * s, 0.9 * s, m, ao_start=False, spec=False))
    # rear tall crown + front low band
    out.append(KK.ball(cx + 1 * s, cy - 11 * s, 8.6 * s, LACQ, ry=8 * s))
    out.append(KK.form((cx - 11.4 * s, cy - 1 * s), (cx + 11 * s, cy - 1.4 * s), 4.6 * s, 4.6 * s, LACQ, ao_start=False))
    out.append(soft(cx - 4 * s, cy - 15 * s, 3 * s, 2 * s, "#F4F0E8", 0.4))
    # goggles on the band
    for gx in (-4.8, 4.2):
        x, y = cx + gx * s, cy - 1.6 * s
        out.append(KK.ball(x, y, 3.2 * s, M["brass"]))
        out.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(2.1 * s)}" fill="url(#r1k-lens)"></circle>')
        out.append(f'<circle cx="{f(x - 0.7 * s)}" cy="{f(y - 0.8 * s)}" r="{f(0.6 * s)}" fill="#FFFFFF" opacity="0.9"></circle>')
    out.append(KK.form((cx - 1.6 * s, cy - 1.6 * s), (cx + 1.0 * s, cy - 1.6 * s), 0.7 * s, 0.7 * s, M["brass"], ao_start=False, spec=False))
    return "".join(out)


def beard_full(cx, cy, r, col, light):
    """Long layered beard + mustache (strands)."""
    out = []
    d = (f"M{f(cx - r * 0.7)} {f(cy + r * 0.45)} C{f(cx - r * 0.75)} {f(cy + r * 1.2)} {f(cx - r * 0.35)} {f(cy + r * 1.9)} {f(cx + r * 0.05)} {f(cy + r * 2.15)} "
         f"C{f(cx + r * 0.45)} {f(cy + r * 1.85)} {f(cx + r * 0.72)} {f(cy + r * 1.2)} {f(cx + r * 0.66)} {f(cy + r * 0.45)} "
         f"C{f(cx + r * 0.4)} {f(cy + r * 0.85)} {f(cx - r * 0.4)} {f(cy + r * 0.85)} {f(cx - r * 0.7)} {f(cy + r * 0.45)} Z")
    gid = KK._sphere_grad(cx - r * 0.1, cy + r * 1.1, r * 1.1, Mat(col, light, shade(col, -0.6), mix(col, "#6A7080", 0.4), spec_op=0.3, gloss=0.5))
    out.append(f'<path d="{d}" fill="url(#{gid})" stroke="#0A0806" stroke-width="0.6" stroke-opacity="0.6"></path>')
    for k in range(11):
        x = cx - r * 0.6 + k * r * 0.12
        out.append(pth(f"M{f(x)} {f(cy + r * 0.8)} Q{f(x + r * 0.05)} {f(cy + r * 1.4)} {f(cx + (x - cx) * 0.3)} {f(cy + r * 2.0)}",
                       stroke=light if k % 3 == 0 else shade(col, -0.4), stroke_width=0.45, opacity="0.7"))
    # mustache
    out.append(pth(f"M{f(cx)} {f(cy + r * 0.46)} q{f(-r * 0.36)} {f(-r * 0.06)} {f(-r * 0.72)} {f(r * 0.34)} q{f(r * 0.34)} {f(-r * 0.1)} {f(r * 0.72)} {f(-r * 0.1)} "
                   f"q{f(r * 0.36)} 0 {f(r * 0.7)} {f(r * 0.12)} q{f(-r * 0.3)} {f(-r * 0.34)} {f(-r * 0.7)} {f(-r * 0.34)} Z", col, stroke="#0A0806",
                   stroke_width=0.4))
    return "".join(out)


# ------------------------------------------------------------------ hero 1: 장영실
def hero_jang():
    out = []
    # star chart + far arm (behind the body)
    out.append(KK.arm((16, -128), (27, -108), (33, -96), SILK_BLUE, SKIN, w=6.4, hand=None, back=True))
    out.append(star_chart(30, -97, 26, 44))
    out.append(KK.hand((33, -96), (1, 0.15), 6.4 * 0.72, SKIN, kind="grip"))
    out.append(KK.folds((16, -128), (27, -108), 6.4, 5.0, SILK_BLUE.back(), n=2))
    # legs: long stride, white trousers, 목화 boots
    out.append(KK.cast_shadow(2, 0, 34, 8))
    out.append(KK.leg((7, -88), (12, -50), (17, -10), HEMP, w=7.4, trouser=HEMP, boot=BOOT, foot_dir=1, back=True))
    out.append(boot_mokhwa((7, -88), (12, -50), (17, -10), 7.4, back=True, dirx=1))
    out.append(KK.leg((-7, -88), (-17, -52), (-25, -11), HEMP, w=7.4, trouser=HEMP, boot=BOOT, foot_dir=-1))
    out.append(boot_mokhwa((-7, -88), (-17, -52), (-25, -11), 7.4, dirx=-1))
    # 측우기 slung at the back hip
    out.append(KK.form((19, -84), (21, -64), 5.2, 5.2, BRONZE.back(0.1), ao_start=False))
    for t in (0.15, 0.5, 0.85):
        y = -84 + 20 * t
        out.append(KK.form((14, y), (25, y + 0.4), 1.0, 1.0, M["brass"], ao_start=False, spec=False))
    # robe skirt
    sk, _ = danryeong_skirt(-94, -30, 15, 25, SILK_BLUE, slit_x=-8)
    out.append(sk)
    # torso
    t, d = KK.torso((0, -134), (0, -93), 17.5, 15, SILK_BLUE, chest=0.4)
    out.append(t)
    inner = [soft(15, -116, 4, 12, "#0A0604", 0.5)]
    # 흉배 rank badge (armillary + clouds, our own motif)
    inner.append(f'<rect x="-9" y="-126" width="17" height="17" fill="#1E2A44" stroke="#C9A35A" stroke-width="1.1"></rect>')
    inner.append(f'<rect x="-7.6" y="-124.6" width="14.2" height="14.2" fill="none" stroke="#E9C77A" stroke-width="0.4" stroke-dasharray="1 0.8"></rect>')
    for rx, ry, rot in ((5, 5, 0), (5, 1.8, -20), (5, 1.8, 25)):
        inner.append(f'<ellipse cx="-0.5" cy="-117.5" rx="{rx}" ry="{ry}" transform="rotate({rot} -0.5 -117.5)" fill="none" stroke="#E9C77A" stroke-width="0.7"></ellipse>')
    inner.append('<circle cx="-0.5" cy="-117.5" r="1.4" fill="#CFF5EA"></circle>')
    for x, y in ((-6, -111), (4, -111.5)):
        inner.append(pth(f"M{x} {y} q1.4 -2 2.8 0 q1.4 -2 2.8 0", stroke="#E9C77A", stroke_width=0.6))
    inner.append(soft(-1, -112, 9, 2, "#000", 0.3))
    out.append(KK.clip_overlay(d, "".join(inner)))
    out.append(poomdae(-95, 16))
    # measuring rule hanging from the belt
    out.append(KK.form((-12, -92), (-15, -66), 1.6, 1.6, M["wood"], ao_start=False))
    for k in range(8):
        y = -90 + k * 3
        out.append(pth(f"M{f(-13.5 - k * 0.11)} {f(y)} l1.6 0.1", stroke="#E9C77A", stroke_width=0.45))
    # round collar (단령) + white 동정
    out.append(f'<ellipse cx="0" cy="-133.5" rx="9" ry="4" fill="none" stroke="#120D08" stroke-width="4.4"></ellipse>')
    out.append(f'<ellipse cx="0" cy="-133.5" rx="9" ry="4" fill="none" stroke="{SILK_BLUE.base}" stroke-width="3.2"></ellipse>')
    out.append(f'<path d="M-9 -133.5 A9 4 0 0 1 9 -133.5" fill="none" stroke="{SILK_BLUE.light}" stroke-width="1" opacity="0.8"></path>')
    out.append(KK.neck((0, -133), (-0.5, -141), 4.6, SKIN))
    out.append(f'<path d="M-6 -132 Q0 -128 6 -132" fill="none" stroke="#F4EEDC" stroke-width="1.4"></path>')
    out.append(human_head(-1, -152, 11.4, turn=-0.3, look=LOOK_JANG))
    out.append(KK.ao(-1, -158, 12, 3, 0.5))
    out.append(samo(-1, -160.5))
    # cyan bounce from the armillary on the near cheek/shoulder
    out.append(soft(-12, -150, 6, 10, ORE_L, 0.18))
    # near arm raised with the armillary staff
    staff_b, staff_t = (-37, -104), (-31, -188)
    out.append(KK.form(staff_b, staff_t, 1.9, 2.2, M["lacquer"], ao_start=False))
    for t in (0.08, 0.5, 0.92):
        c = (staff_b[0] + (staff_t[0] - staff_b[0]) * t, staff_b[1] + (staff_t[1] - staff_b[1]) * t)
        out.append(KK.form((c[0], c[1] + 1.4), (c[0], c[1] - 1.4), 2.8, 2.8, M["brass"], ao_start=False, spec=False))
    out.append(KK.ball(staff_b[0], staff_b[1], 2.6, M["brass"]))
    out.append(armillary(-30.5, -203, 14))
    out.append(KK.arm((-16, -128), (-31, -140), (-34, -162), SILK_BLUE, SKIN, w=6.6, hand="grip", hand_dir=(0.06, -1)))
    # rolled sleeve: bunched silk + red tie cord at the elbow
    out.append(KK.folds((-16, -128), (-31, -140), 6.6, 5.4, SILK_BLUE, n=2))
    out.append(KK.ball(-29, -139, 5.8, SILK_BLUE, ry=5.0))
    out.append(KK.form((-34, -136), (-24, -143), 1.3, 1.3, CORD, ao_start=False))
    out.append(KK.form((-25, -142), (-21, -133), 0.9, 0.7, CORD, ao_start=False, spec=False))
    # forearm sinew + light from the armillary on the hand
    out.append(soft(-35, -166, 6, 5, ORE_L, 0.25))
    return "".join(out)


# ------------------------------------------------------------------ hero 2: 최무선
def lamellar(d, x0, y0, x1, y1, m=IRON_D, pw=3.4, ph=5.0):
    """찰갑: overlapping rows of small iron plates with per-plate specular and leather lacing, clipped to d."""
    inner = []
    row = 0
    y = y0
    while y < y1:
        off = (row % 2) * pw / 2
        x = x0 - off
        while x < x1:
            lit = 0.55 - (x - x0) / (x1 - x0) * 0.75
            base = mix(m.core, m.light, max(0.05, min(0.95, 0.45 + lit * 0.6)))
            inner.append(f'<rect x="{f(x)}" y="{f(y)}" width="{f(pw - 0.4)}" height="{f(ph)}" rx="0.9" fill="{base}" stroke="#08090A" stroke-width="0.45"></rect>')
            inner.append(pth(f"M{f(x + 0.5)} {f(y + ph - 0.6)} L{f(x + 0.5)} {f(y + 0.8)} Q{f(x + 0.5)} {f(y + 0.3)} {f(x + 1.2)} {f(y + 0.3)}",
                             stroke=m.spec, stroke_width=0.45, opacity=f"{max(0.15, 0.4 + lit * 0.6):.2f}"))
            inner.append(f'<rect x="{f(x + 0.2)}" y="{f(y + ph * 0.7)}" width="{f(pw - 0.8)}" height="{f(ph * 0.3)}" fill="#000" opacity="0.3"></rect>')
            x += pw
        inner.append(pth(f"M{f(x0 - 4)} {f(y + 1.2)} L{f(x1 + 4)} {f(y + 1.2)}", stroke="#5A2E18", stroke_width=0.6, stroke_dasharray="0.9 1.3", opacity="0.9"))
        y += ph * 0.72
        row += 1
    return KK.clip_overlay(d, "".join(inner))


def pleated_skirt(waist_y, hem_y, ww, hw, m):
    """철릭 lower half: dense knife pleats fanning from the waist."""
    d = (f"M{f(-ww)} {f(waist_y)} C{f(-ww - 6)} {f(waist_y + 16)} {f(-hw - 4)} {f(hem_y - 14)} {f(-hw)} {f(hem_y)} "
         f"Q0 {f(hem_y + 7)} {f(hw)} {f(hem_y + 1)} C{f(hw + 3)} {f(hem_y - 14)} {f(ww + 5)} {f(waist_y + 16)} {f(ww)} {f(waist_y)} Z")
    out = [grad_fill(d, (0, (waist_y + hem_y) / 2), (-1, -0.1), hw * 1.1, m)]
    inner = []
    n = 22
    for k in range(n + 1):
        t = k / n
        xt = -ww + t * 2 * ww
        xb = -hw - 2 + t * (2 * hw + 4)
        inner.append(pth(f"M{f(xt)} {f(waist_y)} L{f(xb)} {f(hem_y + 6)}", stroke=m.core, stroke_width=1.0, opacity="0.75"))
        inner.append(pth(f"M{f(xt - 0.8)} {f(waist_y)} L{f(xb - 1.2)} {f(hem_y + 6)}", stroke=m.light, stroke_width=0.6, opacity="0.45"))
    # soot smudges + hem scorch
    for _ in range(6):
        inner.append(soft(RNG.uniform(-hw, hw), RNG.uniform(waist_y + 10, hem_y), RNG.uniform(3, 6), RNG.uniform(2, 4), "#0A0604", 0.35))
    inner.append(pth(f"M{f(-hw - 4)} {f(hem_y - 1)} Q0 {f(hem_y + 6)} {f(hw + 4)} {f(hem_y)}", stroke="#140604", stroke_width=3.4, opacity="0.75"))
    inner.append(soft(0, waist_y + 3, ww + 4, 4, "#0A0604", 0.6))
    out.append(KK.clip_overlay(d, "".join(inner)))
    return "".join(out)


def iron_cap(cx, cy, s=1.0):
    """Iron skullcap with riveted bands, short brim and a studded leather neck curtain (drawn around a head centre)."""
    out = []
    # neck curtain behind (sides/back)
    cur = (f"M{f(cx - 12.5 * s)} {f(cy - 3 * s)} C{f(cx - 15 * s)} {f(cy + 8 * s)} {f(cx - 14 * s)} {f(cy + 14 * s)} {f(cx - 9 * s)} {f(cy + 17 * s)} "
           f"L{f(cx - 7 * s)} {f(cy + 6 * s)} L{f(cx + 9 * s)} {f(cy + 6 * s)} L{f(cx + 11 * s)} {f(cy + 17 * s)} C{f(cx + 15 * s)} {f(cy + 14 * s)} "
           f"{f(cx + 15 * s)} {f(cy + 6 * s)} {f(cx + 12.5 * s)} {f(cy - 3 * s)} Z")
    out.append(grad_fill(cur, (cx, cy + 6 * s), (-1, -0.2), 14 * s, LEATHER_D))
    for k in range(2):
        for x in (-12, -10, 10, 12):
            out.append(stud(cx + x * s, cy + (4 + k * 6) * s, 0.8 * s, iron=True))
    return "".join(out)


def iron_cap_front(cx, cy, s=1.0):
    out = []
    dome = (f"M{f(cx - 12.6 * s)} {f(cy - 3 * s)} C{f(cx - 13 * s)} {f(cy - 17 * s)} {f(cx + 13 * s)} {f(cy - 18 * s)} {f(cx + 12.6 * s)} {f(cy - 3 * s)} Z")
    gid = KK._sphere_grad(cx, cy - 7 * s, 13 * s, IRON_D)
    out.append(f'<path d="{dome}" fill="url(#{gid})" stroke="#08090A" stroke-width="0.9"></path>')
    for x in (-6, 0, 6):
        out.append(pth(f"M{f(cx + x * s)} {f(cy - 3 * s)} Q{f(cx + x * 1.1 * s)} {f(cy - 12 * s)} {f(cx + x * 0.2 * s)} {f(cy - 16 * s)}", stroke="#2A2C30", stroke_width=1.6 * s))
        out.append(pth(f"M{f(cx + x * s - 0.6)} {f(cy - 3 * s)} Q{f(cx + x * 1.1 * s - 0.6)} {f(cy - 12 * s)} {f(cx + x * 0.2 * s - 0.6)} {f(cy - 16 * s)}", stroke="#C8D0D8",
                       stroke_width=0.5 * s, opacity="0.6"))
    out.append(KK.form((cx - 14 * s, cy - 2.6 * s), (cx + 14 * s, cy - 2.6 * s), 2.0 * s, 2.0 * s, IRON_D, ao_start=False))
    for k in range(-5, 6):
        out.append(stud(cx + k * 2.5 * s, cy - 2.8 * s, 0.6 * s, iron=True))
    out.append(KK.ball(cx, cy - 17.5 * s, 2.2 * s, M["brass"]))
    return "".join(out)


def hand_cannon(tail, muzzle, firing=True):
    """화통: bronze barrel with reinforcing rings, touch-hole, wooden tiller; muzzle blast."""
    a, b = tail, muzzle
    L = math.hypot(b[0] - a[0], b[1] - a[1])
    ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
    out = []
    # tiller (wooden pole) back from the tail
    tl = (a[0] - ux * 22, a[1] - uy * 22)
    out.append(KK.form(tl, a, 2.2, 2.4, M["wood"], ao_start=False))
    out.append(KK.form(a, (a[0] + ux * L * 0.22, a[1] + uy * L * 0.22), 3.4, 3.6, BRONZE, ao_start=False))
    out.append(KK.form((a[0] + ux * L * 0.2, a[1] + uy * L * 0.2), b, 3.8, 4.4, BRONZE, [(0.92, 0.8, 0)], ao_start=False))
    for t in (0.04, 0.2, 0.42, 0.64, 0.86, 0.99):
        c = (a[0] + ux * L * t, a[1] + uy * L * t)
        w = 4.2 + t * 1.0
        out.append(KK.form((c[0] - ux * 1.2, c[1] - uy * 1.2), (c[0] + ux * 1.2, c[1] + uy * 1.2), w, w, BRONZE, ao_start=False, spec=False))
    # muzzle mouth
    out.append(f'<ellipse cx="{f(b[0] + ux * 1.4)}" cy="{f(b[1] + uy * 1.4)}" rx="3.2" ry="4.6" transform="rotate({f(math.degrees(math.atan2(uy, ux)))} {f(b[0] + ux * 1.4)} {f(b[1] + uy * 1.4)})" fill="#0A0604"></ellipse>')
    # touch hole sparks
    th = (a[0] + ux * L * 0.12 - uy * 3.4, a[1] + uy * L * 0.12 + ux * 3.4)
    out.append(f'<circle cx="{f(th[0])}" cy="{f(th[1])}" r="5" fill="url(#r1k-ember)" filter="url(#r1k-glow)"></circle>')
    for k in range(9):
        ang = -math.pi / 2 + RNG.uniform(-1.2, 1.2)
        ln = RNG.uniform(4, 11)
        out.append(pth(f"M{f(th[0])} {f(th[1])} l{f(math.cos(ang) * ln)} {f(math.sin(ang) * ln)}", stroke="#FFD27A", stroke_width=0.6, opacity="0.9"))
    if firing:
        mx, my = b[0] + ux * 6, b[1] + uy * 6
        out.append(f'<circle cx="{f(mx + ux * 10)}" cy="{f(my + uy * 10)}" r="34" fill="#FF8A3A" opacity="0.35" filter="url(#r1-glow-l)"></circle>')
        out += smoke_puff(mx + ux * 34 + 4, my + uy * 34 - 16, 18, dark=False, op=0.7)
        out += smoke_puff(mx + ux * 8 - 4, my + uy * 8 - 22, 12, dark=False, op=0.55)
        out += smoke_puff(mx + ux * 16 + 14, my + uy * 16 + 4, 10, dark=True, op=0.6)
        # star-shaped blast
        pts = []
        for k in range(14):
            ang = math.atan2(uy, ux) + (k - 7) * 0.2
            r = (40 if k % 2 == 0 else 15) * (1.25 - abs(k - 7) / 9)
            pts.append((mx + math.cos(ang) * r, my + math.sin(ang) * r))
        pts.append((mx - ux * 2, my - uy * 2))
        out.append(f'<polygon points="{" ".join(f"{f(x)},{f(y)}" for x, y in pts)}" fill="url(#r1-fireball)" filter="url(#r1-fire)"></polygon>')
        out.append(f'<circle cx="{f(mx + ux * 8)}" cy="{f(my + uy * 8)}" r="12" fill="url(#r1-fireball)" filter="url(#r1-fire)"></circle>')
        out.append(f'<circle cx="{f(mx + ux * 5)}" cy="{f(my + uy * 5)}" r="5.5" fill="#FFF8DC" filter="url(#r1-glow)"></circle>')
        # flaming bolt leaving the frame
        bx, by = mx + ux * 52, my + uy * 52
        out.append(pth(f"M{f(mx + ux * 12)} {f(my + uy * 12)} L{f(bx)} {f(by)}", stroke="#FFB040", stroke_width=2.4, opacity="0.7", filter="url(#r1-glow)"))
        out.append(KK.form((bx - ux * 10, by - uy * 10), (bx, by), 1.0, 0.6, IRON, ao_start=False))
        out.append(f'<circle cx="{f(bx - ux * 10)}" cy="{f(by - uy * 10)}" r="3.6" fill="url(#r1k-ember)" filter="url(#r1k-glow)"></circle>')
    return "".join(out)


def hero_choi():
    out = []
    tail, muzzle = (26, -106), (-58, -138)
    # far arm holding the tiller (behind)
    out.append(KK.arm((19, -124), (31, -100), (36, -103), MADDER, LEATHER, w=7.2, muscular=0.4, sleeve=MADDER, hand="grip",
                      hand_dir=(1, 0.35), hand_m=LEATHER, back=True))
    # legs: wide braced stance
    out.append(KK.cast_shadow(0, 0, 40, 9))
    for hip, knee, ank, back, dirx in (((9, -84), (21, -47), (27, -10), True, 1), ((-9, -84), (-24, -49), (-33, -10), False, -1)):
        out.append(KK.leg(hip, knee, ank, MADDER, w=8.2, trouser=MADDER.back(0.1), boot=LEATHER_D, foot_dir=dirx, back=back))
        wm = LEATHER.back() if back else LEATHER
        out.append(shin_cover(hip, knee, ank, 0.6, 7.6, 6.2, wm))
        t0 = (knee[0] + (ank[0] - knee[0]) * 0.25, knee[1] + (ank[1] - knee[1]) * 0.25)
        out.append(KK.bands(t0, ank, 7.4, 6.2, wm, n=4))
        out.append(KK.foot(ank, 7.0, LEATHER_D, dirx, back))
    # powder kegs behind the far leg are in the diorama; skirt
    out.append(pleated_skirt(-90, -36, 17, 28, MADDER))
    # torso: lamellar over madder
    t, d = KK.torso((2, -131), (0, -90), 20.5, 17, IRON_D, chest=0.5)
    out.append(t)
    out.append(lamellar(d, -22, -134, 23, -88))
    out.append(KK.clip_overlay(d, soft(18, -112, 5, 14, "#000", 0.55) + soft(-16, -122, 6, 8, "#FFB070", 0.12)))
    # waist cord + belt
    out.append(KK.form((-18, -91), (18, -90), 3.4, 3.4, LEATHER, ao_start=False))
    out.append(KK.ball(0, -90.5, 3.2, M["brass"], ry=2.6))
    # bandolier: strap + powder flasks + horn
    out.append(KK.form((-16, -128), (17, -92), 2.4, 2.4, LEATHER, ao_start=False))
    for t in (0.22, 0.42, 0.62):
        c = (-16 + 33 * t, -128 + 36 * t)
        out.append(KK.form((c[0], c[1] + 1), (c[0] + 0.5, c[1] + 9), 2.4, 2.2, M["wood"], ao_start=True))
        out.append(KK.ball(c[0] + 0.5, c[1] + 0.6, 1.6, M["brass"]))
    horn = M["horn"]
    out.append(KK.form((15, -92), (27, -76), 4.6, 1.4, horn, [(0.4, 0.6, 1)], ao_start=False))
    out.append(KK.form((14, -94), (16, -90), 5.2, 5.2, M["brass"], ao_start=False, spec=False))
    # pauldrons (iron lames)
    for sx, back in ((-19, False), (21, True)):
        mm = IRON_D.back() if back else IRON_D
        for k in range(3):
            out.append(KK.ball(sx + (1 if back else -1) * k * 0.6, -127 + k * 4.4, 8.6 - k * 0.8, mm, ry=4.4))
            out.append(stud(sx - 3, -126 + k * 4.4, 0.7, iron=True))
            out.append(stud(sx + 3, -126 + k * 4.4, 0.7, iron=True))
    out.append(KK.neck((2, -131), (1, -139), 5.6, SKIN_W))
    # head with iron cap, grey beard, scar
    out.append(iron_cap(0, -154, 1.0))
    lk = dict(LOOK_CHOI, turn=-0.35)
    out.append(KK.ao(1, -136, 11, 4, 0.6))
    h, hd = KK.face(0, -149, 12, SKIN_W, lk)
    out.append(h)
    out.append(KK.clip_overlay(hd, soft(-6, -140, 3, 2, "#2A1A10", 0.35) + soft(6, -146, 4, 3, "#1A1008", 0.3)))  # soot
    out.append(iron_cap_front(0, -154, 1.0))
    out.append(KK.ao(0, -155, 12, 2.4, 0.5))
    # the hand cannon (in front of torso) + near arm supporting it
    out.append(hand_cannon(tail, muzzle))
    out.append(KK.arm((-18, -124), (-32, -104), (-22, -116), MADDER, LEATHER, w=7.4, muscular=0.5, sleeve=MADDER, hand="grip",
                      hand_dir=(-1, -0.37), hand_m=LEATHER))
    # fuse cord wound around the forearm, trailing to the touch hole
    out.append(KK.bands((-32, -104), (-22, -116), 5.4, 4.6, Mat("#C8A060", "#F0D8A0", "#3A2A10", "#7A7060"), n=4))
    out.append(pth("M-25 -112 C-16 -104 -4 -100 8 -106 S18 -112 20 -110", stroke="#2A1A08", stroke_width=1.6))
    out.append(pth("M-25 -112 C-16 -104 -4 -100 8 -106 S18 -112 20 -110", stroke="#C8A060", stroke_width=0.9))
    # gauntlet straps on the far forearm area + warm fire bounce on the near side
    out.append(soft(-34, -132, 20, 18, "#FF9A4A", 0.2))
    return "".join(out)


# ------------------------------------------------------------------ hero 3: 이순신
def cape_back():
    d = ("M-17 -132 C-26 -112 -20 -80 -14 -50 C-6 -30 18 -18 44 -22 C60 -24 74 -34 80 -46 "
         "C70 -44 66 -52 70 -60 C60 -58 56 -66 60 -76 C50 -74 44 -84 48 -96 C40 -100 30 -116 22 -132 Z")
    out = [soft(30, -60, 40, 40, "#000", 0.35), grad_fill(d, (24, -80), (-0.7, -0.7), 56, CAPE)]
    inner = []
    for k, (x0, x1, op) in enumerate(((-6, 30, 0.5), (4, 44, 0.45), (14, 58, 0.5), (22, 70, 0.4))):
        inner.append(pth(f"M{x0} -126 C{x0 + 6} -100 {x1 - 18} -60 {x1} -40", stroke=CAPE.core, stroke_width=5, opacity=f"{op}", filter="url(#r1k-soft)"))
        inner.append(pth(f"M{x0 - 3} -126 C{x0 + 3} -100 {x1 - 21} -60 {x1 - 3} -40", stroke=CAPE.light, stroke_width=1.6, opacity=f"{op * 0.8}"))
    inner.append(pth("M-14 -50 C-6 -30 18 -18 44 -22 C60 -24 74 -34 80 -46", stroke="#E9C77A", stroke_width=2.2, opacity="0.75"))
    out.append(KK.clip_overlay(d, "".join(inner)))
    # dark lining visible at the flying edge
    out.append(pth("M80 -46 C70 -44 66 -52 70 -60 C60 -58 56 -66 60 -76 C50 -74 44 -84 48 -96", fill="none", stroke="#3A0804", stroke_width=3))
    return "".join(out)


def command_flag(pole_b, pole_t):
    out = [KK.form(pole_b, pole_t, 1.7, 1.5, M["lacquer"], ao_start=False), KK.ball(pole_t[0], pole_t[1] - 2, 2.6, M["brass"])]
    # spear-tip finial
    out.append(f'<path d="M{f(pole_t[0] - 1.6)} {f(pole_t[1] - 3)} L{f(pole_t[0] + 0.4)} {f(pole_t[1] - 13)} L{f(pole_t[0] + 2.2)} {f(pole_t[1] - 3)} Z" '
               f'fill="{IRON.light}" stroke="#08090A" stroke-width="0.6"></path>')
    x, y = pole_t
    d = (f"M{f(x + 1)} {f(y + 1)} C{f(x + 14)} {f(y - 4)} {f(x + 26)} {f(y + 6)} {f(x + 40)} {f(y + 2)} "
         f"C{f(x + 36)} {f(y + 12)} {f(x + 40)} {f(y + 20)} {f(x + 34)} {f(y + 30)} "
         f"C{f(x + 22)} {f(y + 34)} {f(x + 12)} {f(y + 28)} {f(x + 1.5)} {f(y + 34)} Z")
    flagm = Mat("#24408A", "#5E7EC8", "#081030", "#3A4A78", spec_op=0.25, gloss=0.35)
    out.append(grad_fill(d, (x + 20, y + 16), (-0.4, -0.9), 22, flagm))
    inner = [pth(f"M{f(x + 1)} {f(y + 1)} C{f(x + 14)} {f(y - 4)} {f(x + 26)} {f(y + 6)} {f(x + 40)} {f(y + 2)} C{f(x + 36)} {f(y + 12)} {f(x + 40)} {f(y + 20)} {f(x + 34)} {f(y + 30)} "
                 f"C{f(x + 22)} {f(y + 34)} {f(x + 12)} {f(y + 28)} {f(x + 1.5)} {f(y + 34)}", stroke="#C8432B", stroke_width=6),
             pth(f"M{f(x + 12)} {f(y)} C{f(x + 16)} {f(y + 12)} {f(x + 12)} {f(y + 22)} {f(x + 16)} {f(y + 32)}", stroke=flagm.core, stroke_width=4, opacity="0.5", filter="url(#r1k-soft)"),
             pth(f"M{f(x + 26)} {f(y + 4)} C{f(x + 30)} {f(y + 14)} {f(x + 26)} {f(y + 22)} {f(x + 28)} {f(y + 32)}", stroke=flagm.light, stroke_width=2, opacity="0.4"),
             f'<text x="{f(x + 19)}" y="{f(y + 23)}" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="15" fill="#F4EEDC" '
             f'transform="rotate(-6 {f(x + 19)} {f(y + 18)})">令</text>']
    out.append(KK.clip_overlay(d, "".join(inner)))
    out.append(pth(d.replace(" Z", ""), stroke="#120D08", stroke_width=0.8, opacity="0.7"))
    # tassel
    out.append(pth(f"M{f(x)} {f(y + 2)} q-5 6 -3 14", stroke="#C8432B", stroke_width=1.6))
    return "".join(out)


def long_sword(grip_c, tip, guard_t=0.0):
    """환도-like long sword (our design): wrapped grip, round brass guard, straight blade with fuller and hard specular."""
    gx, gy = grip_c
    L = math.hypot(tip[0] - gx, tip[1] - gy)
    ux, uy = (tip[0] - gx) / L, (tip[1] - gy) / L
    nx, ny = -uy, ux
    out = []
    pommel = (gx - ux * 9, gy - uy * 9)
    guard = (gx + ux * 6, gy + uy * 6)
    out.append(KK.form(pommel, guard, 2.0, 2.0, M["leather_dark"], ao_start=False))
    out.append(KK.bands(pommel, guard, 2.0, 2.0, Mat("#C8432B", "#F07A50", "#3A0804", "#6A3036"), n=5))
    out.append(KK.ball(pommel[0], pommel[1], 2.4, M["brass"]))
    out.append(KK.ball(guard[0], guard[1], 5.0, M["brass"], ry=3.4))
    b0 = (guard[0] + ux * 2, guard[1] + uy * 2)
    d = (f"M{P((b0[0] + nx * 2.4, b0[1] + ny * 2.4))} L{P((tip[0] + nx * 0.6 - ux * 6, tip[1] + ny * 0.6 - uy * 6))} L{P(tip)} "
         f"L{P((tip[0] - nx * 1.6 - ux * 7, tip[1] - ny * 1.6 - uy * 7))} L{P((b0[0] - nx * 2.4, b0[1] - ny * 2.4))} Z")
    out.append(grad_fill(d, ((b0[0] + tip[0]) / 2, (b0[1] + tip[1]) / 2), (nx if nx * -0.6 + ny * -0.8 > 0 else -nx, ny if nx * -0.6 + ny * -0.8 > 0 else -ny), 2.6, IRON, sw=0.7))
    out.append(pth(f"M{P((b0[0] + nx * 0.6, b0[1] + ny * 0.6))} L{P((tip[0] - ux * 12, tip[1] - uy * 12))}", stroke="#2A3038", stroke_width=0.7, opacity="0.8"))
    out.append(pth(f"M{P((b0[0] + nx * 1.4 + ux * 6, b0[1] + ny * 1.4 + uy * 6))} L{P((tip[0] - ux * 18 + nx * 0.5, tip[1] - uy * 18 + ny * 0.5))}", stroke="#FFFFFF",
                   stroke_width=0.9, opacity="0.85"))
    # engraved cloud at the forte
    out.append(pth(f"M{P((b0[0] + ux * 6, b0[1] + uy * 6))} q{f(nx * 1.2 + ux * 2)} {f(ny * 1.2 + uy * 2)} {f(ux * 4)} {f(uy * 4)}", stroke="#E9C77A", stroke_width=0.5))
    out.append(f'<circle cx="{f(tip[0] - ux * 3)}" cy="{f(tip[1] - uy * 3)}" r="3" fill="#FFFFFF" opacity="0.7" filter="url(#r1k-glow)"></circle>')
    return "".join(out)


def helmet_yi(cx, cy):
    """투구: bowl helmet with brass ribs, visor, spike + red plume; studded 드림 flaps over ears and neck. cy = head centre."""
    back, front = [], []
    # 드림 flaps (behind / around head)
    fl = (f"M{f(cx - 13)} {f(cy - 4)} C{f(cx - 17)} {f(cy + 6)} {f(cx - 17)} {f(cy + 16)} {f(cx - 13)} {f(cy + 22)} L{f(cx - 7)} {f(cy + 21)} "
          f"L{f(cx - 7)} {f(cy + 4)} L{f(cx + 8)} {f(cy + 4)} L{f(cx + 8)} {f(cy + 21)} L{f(cx + 14)} {f(cy + 22)} C{f(cx + 18)} {f(cy + 16)} "
          f"{f(cx + 18)} {f(cy + 6)} {f(cx + 13)} {f(cy - 4)} Z")
    back.append(grad_fill(fl, (cx, cy + 8), (-1, -0.2), 16, NAVY))
    for k in range(3):
        for x in (-14, -10.5, 11, 14.5):
            back.append(stud(cx + x, cy + 5 + k * 5.6, 0.85))
    back.append(pth(f"M{f(cx - 13)} {f(cy + 22)} L{f(cx - 7)} {f(cy + 21)} M{f(cx + 8)} {f(cy + 21)} L{f(cx + 14)} {f(cy + 22)}", stroke="#C9A35A", stroke_width=1.4))
    # plume (behind the spike, flowing right with the wind)
    for k in range(13):
        ang = math.radians(-70 + k * 9)
        ln = 16 + (k % 4) * 3
        ex, ey = cx + 2 + math.cos(ang) * ln * 0.4 + ln * 0.9, cy - 29 + math.sin(ang) * ln * 0.3 + 4
        col = mix("#E8432B", "#7A1408", (k % 3) / 3)
        front.append(pth(f"M{f(cx + 0.5)} {f(cy - 27)} Q{f(cx + 8)} {f(cy - 34 + k * 0.3)} {f(ex)} {f(ey)}", stroke=col, stroke_width=1.8, stroke_linecap="round"))
    front.append(pth(f"M{f(cx + 1)} {f(cy - 28)} Q{f(cx + 8)} {f(cy - 35)} {f(cx + 18)} {f(cy - 30)}", stroke="#FF9A7A", stroke_width=0.7, opacity="0.8"))
    # bowl
    dome = f"M{f(cx - 13)} {f(cy - 4)} C{f(cx - 13.5)} {f(cy - 22)} {f(cx + 13.5)} {f(cy - 23)} {f(cx + 13)} {f(cy - 4)} Z"
    gid = KK._sphere_grad(cx, cy - 10, 14, IRON_D)
    front.append(f'<path d="{dome}" fill="url(#{gid})" stroke="#08090A" stroke-width="0.9"></path>')
    for x in (-8, -3, 2.5, 8):
        front.append(pth(f"M{f(cx + x * 1.45)} {f(cy - 4)} Q{f(cx + x * 1.3)} {f(cy - 16)} {f(cx + x * 0.25)} {f(cy - 21)}", stroke=M["brass"].core, stroke_width=2.2))
        front.append(pth(f"M{f(cx + x * 1.45 - 0.6)} {f(cy - 4)} Q{f(cx + x * 1.3 - 0.6)} {f(cy - 16)} {f(cx + x * 0.25 - 0.4)} {f(cy - 21)}", stroke=M["brass"].light, stroke_width=0.8))
    # brim band + visor
    front.append(KK.form((cx - 14, cy - 3.6), (cx + 14, cy - 3.6), 2.4, 2.4, M["brass"], ao_start=False))
    front.append(f'<path d="M{f(cx - 11)} {f(cy - 2.4)} Q{f(cx - 2)} {f(cy + 3.6)} {f(cx + 9)} {f(cy - 2.4)} Q{f(cx - 1)} {f(cy + 0.6)} {f(cx - 11)} {f(cy - 2.4)} Z" '
                 f'fill="{IRON_D.light}" stroke="#08090A" stroke-width="0.7"></path>')
    # spike + plume holder cup
    front.append(KK.ball(cx + 0.5, cy - 23, 3.2, M["brass"], ry=2.4))
    front.append(f'<path d="M{f(cx - 0.6)} {f(cy - 25)} L{f(cx + 0.6)} {f(cy - 38)} L{f(cx + 1.8)} {f(cy - 25)} Z" fill="{M["brass"].light}" stroke="#3A2408" stroke-width="0.5"></path>')
    return "".join(back), "".join(front)


def pauldron(cx, cy, s, back=False):
    m = IRON_D.back() if back else IRON_D
    out = []
    for k in range(4):
        out.append(KK.ball(cx, cy + k * 4.2 * s, (9.5 - k * 0.9) * s, m, ry=4.6 * s))
        out.append(pth(f"M{f(cx - (9 - k) * s)} {f(cy + k * 4.2 * s + 2.6 * s)} Q{f(cx)} {f(cy + k * 4.2 * s + 5.6 * s)} {f(cx + (9 - k) * s)} {f(cy + k * 4.2 * s + 2.6 * s)}",
                       stroke=M["brass"].base if not back else M["brass"].core, stroke_width=1.0))
    return "".join(out)


def hero_yi():
    out = [cape_back()]
    # far arm: sword held low (behind the body)
    out.append(KK.arm((18, -131), (28, -108), (33, -88), NAVY, IRON_D, w=6.8, sleeve=NAVY, hand=None, back=True))
    out.append(long_sword((34, -84), (68, -18)))
    out.append(KK.hand((33, -88), (0.45, 1), 6.8 * 0.72, LEATHER_D.back(0.1), kind="grip"))
    # legs
    out.append(KK.cast_shadow(4, 0, 34, 8))
    out.append(KK.leg((8, -92), (13, -52), (17, -10), HEMP, w=7.6, trouser=HEMP, boot=BOOT, foot_dir=1, back=True))
    out.append(boot_mokhwa((8, -92), (13, -52), (17, -10), 7.6, back=True, dirx=1))
    out.append(KK.leg((-8, -92), (-17, -53), (-23, -11), HEMP, w=7.6, trouser=HEMP, boot=BOOT, foot_dir=-1))
    out.append(boot_mokhwa((-8, -92), (-17, -53), (-23, -11), 7.6, dirx=-1))
    # empty scabbard on the far hip
    out.append(KK.form((14, -90), (42, -50), 2.6, 2.2, M["lacquer"].back(0.2), ao_start=False))
    for t in (0.1, 0.45, 0.9):
        c = (14 + 28 * t, -90 + 40 * t)
        out.append(KK.form((c[0] - 1, c[1] - 1.4), (c[0] + 1, c[1] + 1.4), 3.0, 3.0, M["brass"].back(0.2), ao_start=False, spec=False))
    # armour coat skirt + torso
    out.append(coat_skirt(NAVY, waist_y=-96, hem_y=-46, ww=15.5, hw=26))
    out.append(brigandine_torso(neck=(0, -138), waist=(0, -95), sw=18.5, ww=15.5, m=NAVY))
    # turtle-shell emblem on the chest
    emb = Mat("#B88A3A", "#F8DC98", "#3A2408", "#7A6A50", spec="#FFFBE8", spec_op=0.85, gloss=1.0)
    out.append(KK.ball(-1, -118, 8.4, emb))
    for k in range(6):
        a = math.tau * k / 6 + math.pi / 6
        out.append(f'<polygon points="{" ".join(f"{f(-1 + math.cos(a + math.tau * j / 6) * 2.4 + math.cos(a) * 4.4)},{f(-118 + math.sin(a + math.tau * j / 6) * 2.4 + math.sin(a) * 4.4)}" for j in range(6))}" '
                   f'fill="none" stroke="#5A3A10" stroke-width="0.6"></polygon>')
    out.append(f'<polygon points="{" ".join(f"{f(-1 + math.cos(math.tau * j / 6) * 2.6)},{f(-118 + math.sin(math.tau * j / 6) * 2.6)}" for j in range(6))}" fill="{ORE}" stroke="#2F6F62" stroke-width="0.5"></polygon>')
    # red sash
    out.append(KK.form((-17, -96), (17, -95), 3.4, 3.4, CAPE, ao_start=False))
    out.append(KK.ball(-6, -95, 3.6, CAPE))
    out.append(KK.form((-6, -93), (-11, -70), 2.2, 2.8, CAPE, ao_start=True))
    out.append(KK.folds((-6, -93), (-11, -70), 2.2, 2.8, CAPE, n=2))
    # pauldrons
    out.append(pauldron(19, -136, 1.0, back=True))
    out.append(pauldron(-19, -136, 1.0))
    # head + helmet
    hb, hf = helmet_yi(-0.5, -162)
    out.append(hb)
    out.append(KK.neck((0, -137), (-0.5, -145), 4.6, SKIN))
    out.append(human_head(-0.5, -157, 11.2, turn=-0.32, look=LOOK_YI))
    out.append(KK.ao(-0.5, -161, 12, 3, 0.55))
    out.append(hf)
    # near arm thrust forward-up with the command flag
    out.append(command_flag((-40, -116), (-58, -208)))
    out.append(KK.arm((-18, -131), (-36, -140), (-46, -158), NAVY, IRON_D, w=7.0, sleeve=NAVY, hand="grip", hand_dir=(-0.2, -1), hand_m=LEATHER_D))
    out.append(pauldron(-19, -136, 1.0))
    # vambrace straps
    out.append(KK.bands((-36, -140), (-46, -158), 5.4, 4.2, M["brass"], n=3))
    return "".join(out)


# ------------------------------------------------------------------ skill icons (44x44)
def icon(body, accent):
    return (f'<svg width="44" height="44" viewBox="0 0 44 44" aria-hidden="true" style="display: block">'
            f'<rect x="1" y="1" width="42" height="42" rx="3" fill="url(#r1-medal)" stroke="#0A0604" stroke-width="1"></rect>'
            f'<rect x="3" y="3" width="38" height="38" rx="2" fill="none" stroke="{BRASS}" stroke-width="1.2"></rect>'
            f'<circle cx="22" cy="22" r="17" fill="{accent}" opacity="0.18" filter="url(#r1-glow-l)"></circle>{body}'
            f'<path d="M4 4 l6 0 M4 4 l0 6 M40 40 l-6 0 M40 40 l0 -6" stroke="{BRASS_L}" stroke-width="1.4"></path></svg>')


def ic_clock():
    b = [KK.ball(22, 23, 12, M["brass"]), '<circle cx="22" cy="23" r="9" fill="#F4EEDC" stroke="#3A2408" stroke-width="0.8"></circle>']
    for k in range(12):
        a = math.tau * k / 12
        b.append(pth(f"M{f(22 + math.cos(a) * 7.4)} {f(23 + math.sin(a) * 7.4)} L{f(22 + math.cos(a) * 8.6)} {f(23 + math.sin(a) * 8.6)}", stroke="#3A2408", stroke_width=0.8))
    b += [pth("M22 23 L22 16.5", stroke="#7A2A18", stroke_width=1.4), pth("M22 23 L27 25", stroke="#1A1008", stroke_width=1.2),
          '<path d="M33 8 q3 5 0 7 q-3 -2 0 -7 Z" fill="#7FD8F0" stroke="#1A4A60" stroke-width="0.5"></path>',
          '<path d="M10 33 q-6 3 -2 6" fill="none" stroke="#9FE3D0" stroke-width="1.4" opacity="0.8"></path>']
    return icon("".join(b), ORE)


def ic_eye():
    b = ['<path d="M5 23 Q22 6 39 23 Q22 40 5 23 Z" fill="#F4EEDC" stroke="#3A2408" stroke-width="1"></path>',
         KK.ball(22, 23, 7, Mat("#3E8F7A", "#9FE3D0", "#0A2420", "#2F6F62", spec="#FFFFFF", spec_op=0.9, gloss=1.0)),
         '<circle cx="22" cy="23" r="3" fill="#0A0806"></circle><circle cx="20" cy="21" r="1.2" fill="#FFF"></circle>']
    for k in range(9):
        a = math.pi + math.pi * k / 8
        b.append(pth(f"M{f(22 + math.cos(a) * 15)} {f(23 + math.sin(a) * 15)} L{f(22 + math.cos(a) * 19)} {f(23 + math.sin(a) * 19)}", stroke="#E9C77A", stroke_width=1.2))
    return icon("".join(b), "#E9C77A")


def ic_armillary():
    return icon(armillary(22, 24, 9), ORE)


def ic_rockets():
    b = []
    for k, (x, y) in enumerate(((10, 34), (18, 37), (26, 34))):
        tx, ty = x + 12, y - 24
        b.append(pth(f"M{x} {y} L{tx} {ty}", stroke="#FFB040", stroke_width=2.2, opacity="0.6", filter="url(#r1-glow)"))
        b.append(KK.form((tx - 5, ty + 10), (tx, ty), 1.4, 1.2, M["wood"], ao_start=False))
        b.append(f'<circle cx="{f(tx - 5)}" cy="{f(ty + 10)}" r="3.4" fill="url(#r1k-ember)" filter="url(#r1k-glow)"></circle>')
        b.append(f'<path d="M{f(tx - 1.4)} {f(ty + 1.6)} L{f(tx + 2)} {f(ty - 3)} L{f(tx + 1.2)} {f(ty + 2.6)} Z" fill="#C8D0D8"></path>')
    return icon("".join(b), "#FF8A3A")


def ic_forge():
    b = [KK.form((8, 30), (36, 30), 4, 4, IRON_D, ao_start=False),
         KK.form((12, 24), (32, 18), 3.6, 4.4, BRONZE, ao_start=False),
         f'<ellipse cx="33" cy="17.5" rx="2" ry="3.6" transform="rotate(-17 33 17.5)" fill="#0A0604"></ellipse>',
         KK.form((26, 12), (16, 6), 1.4, 1.4, M["wood"], ao_start=False), KK.ball(28, 13, 3.6, IRON_D, ry=2.6)]
    for k in range(6):
        a = RNG.uniform(-2.6, -0.4)
        b.append(pth(f"M22 24 l{f(math.cos(a) * 8)} {f(math.sin(a) * 8)}", stroke="#FFD27A", stroke_width=0.7))
    return icon("".join(b), "#FF8A3A")


def ic_fire():
    b = ['<ellipse cx="22" cy="34" rx="16" ry="5" fill="#1A0804" opacity="0.8"></ellipse>']
    b += r1_lib.flames(22, 35, 1.25, 4)
    return icon("".join(b), "#FF8A3A")


def ic_crane():
    b = []
    pts = [(22, 12)] + [(22 - k * 4.4, 12 + k * 3.6 - k * k * 0.15) for k in range(1, 5)] + [(22 + k * 4.4, 12 + k * 3.6 - k * k * 0.15) for k in range(1, 5)]
    b.append(pth("M4 30 Q22 2 40 30", stroke="#E9C77A", stroke_width=1, opacity="0.6", stroke_dasharray="2 2"))
    for x, y in pts:
        b.append(KK.ball(x, y + 8, 2.4, Mat("#C8432B", "#F09A6E", "#3A0804", "#6A3036")))
    b.append(KK.ball(22, 34, 3.6, M["brass"]))
    return icon("".join(b), RED_L)


def ic_shield():
    d = "M22 6 L36 11 C36 24 31 33 22 38 C13 33 8 24 8 11 Z"
    b = [grad_fill(d, (22, 22), (-0.7, -0.7), 14, CAPE),
         pth("M22 9 L33 13 C33 23 29 31 22 35 C15 31 11 23 11 13 Z", stroke="#E9C77A", stroke_width=1),
         '<text x="22" y="28" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="13" fill="#F4EEDC">死</text>']
    return icon("".join(b), RED_L)


def ic_turtle():
    b = [KK.ball(22, 24, 13, Mat("#2E6A5A", "#7CC4AE", "#081A16", "#3A5A68", spec_op=0.6, gloss=0.7), ry=10)]
    for k in range(6):
        a = math.tau * k / 6
        cx, cy = 22 + math.cos(a) * 7, 24 + math.sin(a) * 5.4
        b.append(f'<polygon points="{" ".join(f"{f(cx + math.cos(math.tau * j / 6) * 3)},{f(cy + math.sin(math.tau * j / 6) * 2.4)}" for j in range(6))}" fill="none" stroke="#0A2420" stroke-width="0.7"></polygon>')
        b.append(f'<circle cx="{f(cx)}" cy="{f(cy - 1.2)}" r="0.9" fill="#E8EEF2"></circle>')
    b.append(f'<polygon points="{" ".join(f"{f(22 + math.cos(math.tau * j / 6) * 3.4)},{f(24 + math.sin(math.tau * j / 6) * 2.6)}" for j in range(6))}" fill="{ORE}"></polygon>')
    b.append(KK.ball(7, 21, 3.2, Mat("#C8432B", "#F09A6E", "#3A0804", "#6A3036")))
    b.append(pth("M5 18 q-2 -4 2 -6 q-1 3 3 2", stroke="#B4AEA0", stroke_width=1.2, opacity="0.7"))
    return icon("".join(b), ORE)


# ------------------------------------------------------------------ page chrome (mirrors race_r1.py)
MONO = "font-family: 'IBM Plex Mono', monospace"
SERIF = "font-family: 'Nanum Myeongjo', serif"
GOLD = "#E9C77A"
GOLD_TXT = f"color: {GOLD}; text-shadow: 0 1px 0 #000, 0 0 2px #000, 0 2px 4px rgba(0,0,0,0.6)"
LACQUER = ("background: linear-gradient(180deg, #2E1E14 0%, #22160E 55%, #1A110A 100%); border: 1px solid #0A0604; "
           "box-shadow: inset 0 0 0 1px #6B4A2A, inset 0 0 0 3px #2A1A10, inset 0 0 0 4px rgba(201,163,90,0.45), inset 0 8px 18px rgba(255,220,160,0.05), 0 6px 14px rgba(0,0,0,0.65)")
ART_BG = ("background: radial-gradient(ellipse at 45% 38%, #3A2A1A 0%, #1C140C 62%, #0C0805 100%); "
          "box-shadow: inset 0 0 0 1px #0A0604, inset 0 0 0 2px rgba(201,163,90,0.35), inset 0 0 30px rgba(0,0,0,0.9)")
PARCH = ("background: linear-gradient(180deg, #F1E4C4 0%, #E6D4AE 100%); border: 1px solid #6E5222; "
         "box-shadow: inset 0 0 0 1px rgba(255,248,224,0.6), inset 0 0 14px rgba(110,82,34,0.35)")


def corner_svg(rot):
    body = [r1_lib.path("M1 1 L17 1 L17 5 L5 5 L5 17 L1 17 Z", u("brass"), stroke="#3A2810", stroke_width=0.6),
            r1_lib.path("M5 5 q5 0 6 4 q-3 -1 -4 1 q-1 -2 -2 -1 Z", "#8A6A30"),
            r1_lib.circ(3, 3, 1.1, BRASS_L, stroke="#3A2810", stroke_width=0.4), r1_lib.circ(14, 3, 0.8, BRASS_L), r1_lib.circ(3, 14, 0.8, BRASS_L)]
    pos = {0: "top: -3px; left: -3px", 90: "top: -3px; right: -3px", 180: "bottom: -3px; right: -3px", 270: "bottom: -3px; left: -3px"}[rot]
    return (f'<svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" style="position: absolute; {pos}; transform: rotate({rot}deg)">'
            + "".join(body) + "</svg>")


CORNERS = "".join(corner_svg(r) for r in (0, 90, 180, 270))


def crest():
    body = [r1_lib.circ(50, 50, 46, u("brass"), stroke="#3A2810", stroke_width=1), r1_lib.circ(50, 50, 41, u("medal"), stroke="#6E5222", stroke_width=1)]
    for k in range(24):
        a = math.tau * k / 24
        body.append(r1_lib.circ(50 + math.cos(a) * 43.6, 50 + math.sin(a) * 43.6, 0.9, "#3A2810"))
    body += [r1_lib.rect(30, 30, 40, 40, RED, stroke="#3A1008", stroke_width=1.2, filter=u("tex")), r1_lib.rect(34, 34, 32, 32, "none", stroke=BRASS, stroke_width=0.8)]
    body.append(r1_lib.circ(50, 50, 16, u("ore-glow"), filter=u("glow-l")))
    for rx, ry, rot in ((24, 24, 0), (24, 8, -24), (24, 8, 24), (8, 24, 0)):
        body.append(r1_lib.ell(50, 50, rx, ry, "none", stroke=u("brass"), stroke_width=2, transform=f"rotate({rot} 50 50)"))
    body += [r1_lib.circ(50, 50, 5, u("ore"), filter=u("glow")), r1_lib.circ(48.6, 48.4, 1.6, "#FFFFFF", opacity=0.9)]
    return f'<svg width="78" height="78" viewBox="0 0 100 100" role="img" aria-label="조선 공방 문장">{"".join(body)}</svg>'


def header_banner():
    ps = "".join(f'<path d="M{k} 6 q12 4 24 0" fill="none" stroke="#3A2E24" stroke-width="3"></path>' for k in range(0, 1441, 24))
    db = "".join(f'<rect x="{k}" y="10" width="12" height="4" fill="{RED if (k // 16) % 3 == 0 else (GREEN if (k // 16) % 3 == 1 else "#E2B13C")}"></rect>'
                 for k in range(0, 1440, 16))
    return (f'<svg width="1440" height="16" viewBox="0 0 1440 16" aria-hidden="true" style="position: absolute; left: 0; top: 0">'
            f'<rect width="1440" height="16" fill="#120C08"></rect>{ps}{db}</svg>')


def stat(label, val, frac, col):
    return (f'<div style="display: grid; grid-template-columns: 34px 1fr 40px; align-items: center; gap: 6px">'
            f'<span style="font-size: 11.5px; color: #4A3820; font-weight: 600">{label}</span>'
            f'<span style="height: 7px; background: #3A2E20; box-shadow: inset 0 1px 2px rgba(0,0,0,0.6); position: relative">'
            f'<span style="position: absolute; left: 0; top: 0; bottom: 0; width: {frac * 100:.0f}%; background: linear-gradient(180deg, {mix(col, "#FFFFFF", 0.35)}, {col} 60%, {shade(col, -0.4)})"></span></span>'
            f'<span style="{MONO}; font-size: 11px; color: #3A2A14; text-align: right">{val}</span></div>')


def skill(key, icon_svg, name, desc):
    return (f'<div style="display: grid; grid-template-columns: 44px 1fr; gap: 10px; align-items: center">'
            f'<div style="position: relative">{icon_svg}<span style="position: absolute; right: -4px; bottom: -4px; {MONO}; font-size: 10px; font-weight: 600; '
            f'color: #1A1008; background: {GOLD}; border: 1px solid #3A2408; padding: 0 3px; line-height: 13px">{key}</span></div>'
            f'<div style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><b style="{SERIF}; font-size: 14.5px; font-weight: 800; color: #F2E6CA">{name}</b>'
            f'<span style="font-size: 11.5px; line-height: 1.35; color: #CFC3A8">{desc}</span></div></div>')


def medallion(hid, vb, ring_col):
    cid = f"r1-med-{hid}"
    return (f'<svg width="76" height="76" viewBox="{vb}" role="img" aria-label="초상" style="display: block; flex: none">'
            f'<defs><clipPath id="{cid}"><circle cx="{vb_c(vb)[0]}" cy="{vb_c(vb)[1]}" r="{vb_c(vb)[2] * 0.86}"></circle></clipPath></defs>'
            f'<circle cx="{vb_c(vb)[0]}" cy="{vb_c(vb)[1]}" r="{vb_c(vb)[2] * 0.98}" fill="url(#r1-brass)" stroke="#3A2810" stroke-width="0.6"></circle>'
            f'<circle cx="{vb_c(vb)[0]}" cy="{vb_c(vb)[1]}" r="{vb_c(vb)[2] * 0.86}" fill="{ring_col}"></circle>'
            f'<g clip-path="url(#{cid})"><circle cx="{vb_c(vb)[0]}" cy="{vb_c(vb)[1]}" r="{vb_c(vb)[2]}" fill="url(#r1-medal)" opacity="0.55"></circle>'
            f'<use href="#r1-hero-{hid}"></use></g>'
            f'<circle cx="{vb_c(vb)[0]}" cy="{vb_c(vb)[1]}" r="{vb_c(vb)[2] * 0.86}" fill="none" stroke="#120D08" stroke-width="0.8"></circle></svg>')


def vb_c(vb):
    x, y, w, h = (float(v) for v in vb.split())
    return (f(x + w / 2), f(y + h / 2), w / 2)


HEROES = [
    dict(hid="jang", fn=hero_jang, dio="stone", aura="#9FE3D0", scale=1.92, fy=424, fx=226, vb="-24 -178 44 44",
         code="H-G1", name="장영실", hanja="蔣英實", role="공학 영웅", en="Master Engineer",
         src="조선 세종대 과학자 · 자격루·측우기·혼천의 제작",
         stats=[("체력", "620", 0.55, "#8CC79A"), ("공격", "18", 0.3, RED), ("사거리", "6", 0.6, BRASS), ("이동", "2.6", 0.55, "#7FB4E0")],
         skills=[("Q", ic_clock(), "자격루의 시각", "범위 안 적의 행동을 4초간 40% 늦춥니다."),
                 ("W", ic_eye(), "측우 관측", "넓은 지역의 시야를 10초간 밝히고 매복을 드러냅니다."),
                 ("E", ic_armillary(), "혼천의 공명", "주변 일꾼의 건설·수리 속도 +50% (지속 오라).")],
         lore="관노의 아들에서 궁궐 공방의 장인이 된 사람. 물에 시각을 담던 손이 이제 귀화석으로 별의 궤도를 돌립니다."),
    dict(hid="yi", fn=hero_yi, dio="deck", aura="#E8603A", scale=1.88, fy=426, fx=212, vb="-25 -186 46 46",
         code="H-G2", name="이순신", hanja="李舜臣", role="지휘 영웅", en="Admiral-Commander",
         src="조선 수군 삼도수군통제사 · 거북선 운용",
         stats=[("체력", "900", 0.82, "#8CC79A"), ("공격", "34", 0.68, RED), ("사거리", "2", 0.2, BRASS), ("이동", "2.8", 0.6, "#7FB4E0")],
         skills=[("Q", ic_crane(), "학익진", "학의 날개 진형 — 주변 아군 공격력 +25% 오라."),
                 ("W", ic_shield(), "필사즉생", "체력이 낮은 아군에게 8초간 방어 +60%."),
                 ("E", ic_turtle(), "거북 지휘", "주변 거북 전차의 수리 및 공격 속도 +30%.")],
         lore="물러설 곳 없는 물길에서 진을 펼친 장수. 그의 깃발이 오르면 거북 전차들이 일제히 머리를 듭니다."),
    dict(hid="mandeok", art=H.art_mandeok, vb="-19 -139 38 38",
         code="H-G3", name="김만덕", hanja="金萬德", role="지원 · 경제 영웅", en="Merchant of Jeju",
         src="조선 정조대 제주 거상 · 구휼(1795)",
         stats=[("체력", "480", 0.44, "#8CC79A"), ("공격", "8", 0.14, RED), ("사거리", "6", 0.6, BRASS), ("이동", "2.8", 0.6, "#7FB4E0")],
         skills=[("Q", icon(H.ic_rice(), "#E2B13C"), "구휼미", "범위 아군 체력 회복 · 식량 +4"),
                 ("W", icon(H.ic_ledger(), "#7FB4E0"), "객주 장부", "30초 동안 모든 수입 +20%"),
                 ("E", icon(H.ic_sea(), "#3E8F7A"), "바닷바람 교역", "어느 장터 객주에서든 원격 교역")],
         lore="흉년에 모은 재산을 털어 쌀을 사 섬사람들을 먹였다. 그녀의 장부에는 셈보다 사람이 먼저 적혀 있다."),
]


def hero_panel(h):
    W, H = 436, 432
    if "art" in h:
        # the figure lives in defs (h3-fig2) so the art and its medallion share one drawing
        art_svg = (f'<svg width="{W}" height="{H}" viewBox="0 0 440 420" preserveAspectRatio="xMidYMid slice" role="img" aria-label="{h["name"]} 전신" style="display: block">'
                   + h["art"]() + "</svg>")
    else:
        fig = h["fn"]()
        art = [diorama(W / 2, h["fy"] - 6, 176, 52, h["dio"]), aura(h["fx"], h["fy"], h["aura"]),
               f'<g transform="translate({h["fx"]} {h["fy"]}) scale({h["scale"]})"><g id="r1-hero-{h["hid"]}">{fig}</g></g>']
        # rim light sweep over the whole figure on the shadow side (race red)
        art.append(f'<ellipse cx="{h["fx"] + 70}" cy="{h["fy"] - 180}" rx="60" ry="190" fill="{RED}" opacity="0.07" filter="url(#r1-glow-l)"></ellipse>')
        art_svg = (f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="{h["name"]} 전신" style="display: block">'
                   + "".join(art) + "</svg>")
    stats = "".join(stat(*s) for s in h["stats"])
    skills = "".join(skill(*s) for s in h["skills"])
    return f"""
  <section style="position: relative; display: flex; flex-direction: column; gap: 9px; padding: 9px; {LACQUER}; min-width: 0">{CORNERS}
    <div style="position: relative; height: {H}px; overflow: hidden; {ART_BG}">{art_svg}
      <span style="position: absolute; left: 10px; top: 10px; {MONO}; font-size: 11px; color: #FFF4E0; background: {RED}; padding: 2px 7px; border: 1px solid {RED_L}">{h["code"]} · 영웅</span>
      <span style="position: absolute; right: 10px; top: 10px; {MONO}; font-size: 10.5px; color: {GOLD}; background: rgba(10,6,4,0.7); padding: 2px 7px; border: 1px solid rgba(201,163,90,0.5)">1×1 · 진영당 1명</span>
    </div>
    <div style="display: flex; gap: 12px; align-items: center">
      {medallion(h["hid"], h["vb"], "#3A1A10")}
      <div style="display: flex; flex-direction: column; gap: 2px; min-width: 0">
        <div style="display: flex; align-items: baseline; gap: 8px; white-space: nowrap"><b style="{SERIF}; font-size: 30px; font-weight: 800; line-height: 1.05; {GOLD_TXT}">{h["name"]}</b>
          <span style="{SERIF}; font-size: 15px; color: #CFC3A8">{h["hanja"]}</span><span style="font-size: 12px; color: {RED_L}; font-weight: 600">{h["role"]}</span></div>
        <span style="{MONO}; font-size: 11px; letter-spacing: 0.08em; color: {MUTED}">{h["en"].upper()}</span>
        <span style="font-size: 11.5px; color: #D8CDB4">출전 · {h["src"]}</span>
      </div>
    </div>
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 5px 16px; padding: 7px 10px; {PARCH}">{stats}</div>
    <div style="display: flex; flex-direction: column; gap: 7px; padding: 2px 2px 0">{skills}</div>
    <p style="margin: 0; padding: 7px 10px 0; border-top: 1px solid rgba(201,163,90,0.35); {SERIF}; font-size: 12.5px; line-height: 1.5; color: #E8DCC0">“{h["lore"]}”</p>
  </section>"""


panels = "".join(hero_panel(h) for h in HEROES)
_defs_once()

PAGE_BG = ("background-color: #16140F; background-image: radial-gradient(ellipse at 22% 0%, rgba(217,83,46,0.14), transparent 50%), "
           "radial-gradient(ellipse at 80% 100%, rgba(159,227,208,0.07), transparent 50%), "
           "repeating-linear-gradient(0deg, rgba(255,255,255,0.012) 0 2px, transparent 2px 4px)")

MANDEOK_FIG = H.hero_mandeok()
KIT_DEFS = ('<svg width="0" height="0" style="position: absolute; width: 0; height: 0; overflow: hidden" aria-hidden="true" focusable="false"><defs>'
            + KK.defs_svg() + KITE.DEFS + H.extra_defs() + KITE.K.defs_svg()
            + f'<g id="h3-fig2">{MANDEOK_FIG}</g><g id="r1-hero-mandeok"><use href="#h3-fig2"></use></g>' + "</defs></svg>")

body = f"""<div style="width: 1440px; height: 960px; box-sizing: border-box; {PAGE_BG}; color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; padding: 24px 28px 16px; display: flex; flex-direction: column; gap: 12px; overflow: hidden; position: relative">
{defs()}
{KIT_DEFS}
{header_banner()}
  <header style="position: relative; display: flex; align-items: center; gap: 16px; padding: 6px 16px 6px 8px; {LACQUER}">{CORNERS}
    {crest()}
    <div style="display: flex; flex-direction: column; gap: 3px; min-width: 0">
      <span style="{MONO}; font-size: 11.5px; letter-spacing: 0.14em; color: {MUTED}">HERO CODEX R1 · 삼재 SAMJAE · 진영당 영웅 1명 출전</span>
      <div style="display: flex; align-items: baseline; gap: 14px"><h1 style="margin: 0; {SERIF}; font-weight: 800; font-size: 40px; line-height: 1; {GOLD_TXT}">영웅 · 조선 공방</h1><span style="{MONO}; font-weight: 600; font-size: 16px; letter-spacing: 0.18em; color: {RED_L}">HEROES · JOSEON WORKS</span></div>
      <span style="font-size: 13px; color: #D8CDB4">역사 속 장인과 장수, 거상을 삼재의 조선으로 불러온 영웅들 — 각자 하나의 오라와 두 개의 기술을 지닙니다.</span>
    </div>
    <div style="margin-left: auto; display: flex; flex-direction: column; gap: 4px; align-items: flex-end; font-size: 12px; color: {MUTED}">
      <span>Q · W · E 기술 · 레벨 없이 고정 성능</span>
      <span>전신 일러스트 = 게임 내 고해상 스프라이트 원화</span>
    </div>
  </header>
  <div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; flex: 1; min-height: 0">{panels}
  </div>
</div>"""

HEAD = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>조선 공방 영웅</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
body{margin:0;background:#16140F}
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

html = HEAD + body + TAIL
# re-prefix every id/reference so this artboard never collides with RaceR1 (r1- -> h1-, r1k- -> h1k-)
html = re.sub(r'(id="|url\(#|href="#)r1(k?)-', r"\1h1\2-", to_prefix(html, "h1m"))
with open(OUT, "w") as fh:
    fh.write(html)
print("wrote", OUT, len(html))
