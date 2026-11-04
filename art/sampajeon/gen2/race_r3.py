#!/usr/bin/env python3
"""Generate RaceR3.dc.html — 삼재 race codex, slot 3: 天 오방 신당 (Obang Shrine).

High-fidelity pass: 2.5D isometric (2:1), painterly layered shading, material
textures, sprite outlines and ornate lacquer/brass/hanji chrome.
Deterministic: every scatter uses a seeded RNG.
"""
import math
import os
import random

OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "RaceR3.dc.html")

OCHRE, OCHRE_L, OCHRE_D = "#E2B13C", "#F2D58A", "#3A2E14"
INDIGO, CLOTH, ROPE = "#3B4E8C", "#E8E1C8", "#8A7A5A"
RED, BLUE, GREEN = "#C8432B", "#2F6E8E", "#3E8F7A"
ORE, ORE_G, MIST = "#9FE3D0", "#CFF5EA", "#B79AE0"
GROUND, PANEL, LINE, INK, MUTED = "#16140F", "#201D17", "#4A4236", "#EFE6D2", "#A89E8A"
OUTLINE = "#120d08"
N = None


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pts(ps):
    return " ".join(f"{f(x)},{f(y)}" for x, y in ps)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


# ---------------------------------------------------------------- defs

def lg(i, stops, x1=0, y1=0, x2=1, y2=1):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"{"" if a is None else f" stop-opacity={chr(34)}{a}{chr(34)}"}></stop>' for o, c, a in stops)
    return f'<linearGradient id="r3-{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def rg(i, stops, cx=0.35, cy=0.3, r=0.85):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"{"" if a is None else f" stop-opacity={chr(34)}{a}{chr(34)}"}></stop>' for o, c, a in stops)
    return f'<radialGradient id="r3-{i}" cx="{cx}" cy="{cy}" r="{r}">{s}</radialGradient>'


def tex(i, freq, octv, seed, amt, dark=0.0):
    """Multiply a noise texture onto the source graphic (keeps alpha)."""
    return (f'<filter id="r3-{i}" x="-5%" y="-5%" width="110%" height="110%" color-interpolation-filters="sRGB">'
            f'<feTurbulence type="fractalNoise" baseFrequency="{freq}" numOctaves="{octv}" seed="{seed}" result="n"></feTurbulence>'
            f'<feColorMatrix in="n" type="matrix" values="0 0 0 0 {1 - amt} 0 0 0 0 {1 - amt} 0 0 0 0 {1 - amt} 0 0 0 {amt * 2.2} {dark}" result="m"></feColorMatrix>'
            f'<feComposite in="m" in2="SourceGraphic" operator="in" result="mi"></feComposite>'
            f'<feBlend in="mi" in2="SourceGraphic" mode="multiply"></feBlend></filter>')


DEFS = "".join([
    '<filter id="r3-soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2.4"></feGaussianBlur></filter>',
    '<filter id="r3-soft6" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="5"></feGaussianBlur></filter>',
    '<filter id="r3-blur1" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="0.9"></feGaussianBlur></filter>',
    '<filter id="r3-glow" x="-150%" y="-150%" width="400%" height="400%"><feGaussianBlur stdDeviation="1.8" result="b"></feGaussianBlur><feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>',
    '<filter id="r3-glow4" x="-150%" y="-150%" width="400%" height="400%"><feGaussianBlur stdDeviation="4" result="b"></feGaussianBlur><feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>',
    # sprite outline: dilate alpha, tint dark, place under the art
    f'<filter id="r3-ol" x="-10%" y="-10%" width="120%" height="120%"><feMorphology in="SourceAlpha" operator="dilate" radius="0.7" result="d"></feMorphology>'
    f'<feFlood flood-color="{OUTLINE}" flood-opacity="0.75"></feFlood><feComposite in2="d" operator="in" result="o"></feComposite>'
    '<feMerge><feMergeNode in="o"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>',
    '<filter id="r3-smoke" x="-60%" y="-60%" width="220%" height="220%"><feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="3" seed="11" result="t"></feTurbulence>'
    '<feDisplacementMap in="SourceGraphic" in2="t" scale="10" xChannelSelector="R" yChannelSelector="G" result="d"></feDisplacementMap><feGaussianBlur in="d" stdDeviation="1.6"></feGaussianBlur></filter>',
    '<filter id="r3-flame" x="-60%" y="-60%" width="220%" height="220%"><feTurbulence type="fractalNoise" baseFrequency="0.12 0.05" numOctaves="2" seed="5" result="t"></feTurbulence>'
    '<feDisplacementMap in="SourceGraphic" in2="t" scale="5" xChannelSelector="R" yChannelSelector="G" result="d"></feDisplacementMap><feGaussianBlur in="d" stdDeviation="0.5" result="b"></feGaussianBlur>'
    '<feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="b"></feMergeNode></feMerge></filter>',
    '<filter id="r3-dead" color-interpolation-filters="sRGB"><feColorMatrix type="saturate" values="0.2"></feColorMatrix></filter>',
    tex("tx-cloth", "0.9 0.35", 2, 3, 0.22),
    tex("tx-wood", "0.03 0.55", 3, 8, 0.32),
    tex("tx-wood-v", "0.55 0.03", 3, 9, 0.32),
    tex("tx-stone", "0.18", 4, 12, 0.38),
    tex("tx-grass", "0.35", 3, 21, 0.4),
    tex("tx-earth", "0.12", 4, 31, 0.35),
    tex("tx-straw", "0.08 0.9", 2, 41, 0.42),
    tex("tx-paper", "0.7", 3, 51, 0.12),
    # plinth fade mask
    '<radialGradient id="r3-fadeg" cx="0.5" cy="0.5" r="0.5"><stop offset="0.55" stop-color="#fff"></stop><stop offset="1" stop-color="#000"></stop></radialGradient>',
    '<mask id="r3-fade" maskContentUnits="objectBoundingBox"><rect width="1" height="1" fill="url(#r3-fadeg)"></rect></mask>',
    # materials
    lg("ochre", [(0, "#FBE8B0", N), (0.45, OCHRE, N), (1, "#7E5A16", N)]),
    lg("sail", [(0, "#F6DE8E", N), (0.5, "#D9A63A", N), (1, "#8E6420", N)], 0, 0, 1, 0.3),
    lg("cloth", [(0, "#FFFBEE", N), (0.5, CLOTH, N), (1, "#A89E80", N)]),
    lg("cloth-sh", [(0, "#C8BEA0", N), (1, "#6E654C", N)]),
    lg("hemp", [(0, "#EFE4C4", N), (0.55, "#C2B088", N), (1, "#7A6A48", N)]),
    lg("hemp-sh", [(0, "#A8966C", N), (1, "#4E4430", N)]),
    lg("indigo", [(0, "#7488C8", N), (0.45, INDIGO, N), (1, "#1A2248", N)]),
    lg("indigo-sh", [(0, "#2C3A6C", N), (1, "#11162E", N)]),
    lg("red-sh", [(0, "#8A2A18", N), (1, "#3A1008", N)]),
    lg("ochre-sh", [(0, "#9A7020", N), (1, "#4A3410", N)]),
    lg("red", [(0, "#F08A6A", N), (0.5, RED, N), (1, "#6A1E12", N)]),
    lg("wood", [(0, "#B48E5E", N), (0.5, "#7A5634", N), (1, "#3A2818", N)]),
    lg("wood-lit", [(0, "#9A7448", N), (1, "#5A3E24", N)], 0, 0, 0, 1),
    lg("wood-sh", [(0, "#5A3E24", N), (1, "#24170C", N)], 0, 0, 0, 1),
    lg("pole", [(0, "#3A2818", N), (0.4, "#B08C5C", N), (1, "#2E2014", N)], 0, 0, 1, 0),
    lg("bamboo", [(0, "#6A6A2E", N), (0.4, "#D8CC86", N), (1, "#5A5424", N)], 0, 0, 1, 0),
    lg("steel", [(0, "#F6F4EE", N), (0.5, "#A8A9A6", N), (1, "#3E3F3C", N)]),
    lg("plaster", [(0, "#F4ECDA", N), (1, "#C4B898", N)], 0, 0, 0, 1),
    lg("plaster-sh", [(0, "#A89C80", N), (1, "#6A604C", N)], 0, 0, 0, 1),
    lg("stone-lit", [(0, "#B4AC98", N), (1, "#7A7262", N)], 0, 0, 0, 1),
    lg("stone-sh", [(0, "#6A6456", N), (1, "#2E2B24", N)], 0, 0, 0, 1),
    lg("stone-top", [(0, "#C8C0AA", N), (1, "#8E8674", N)]),
    lg("roof-f", [(0, "#4E5662", N), (1, "#7C8592", N)], 0, 0, 0, 1),
    lg("roof-b", [(0, "#2E343C", N), (1, "#4A525C", N)], 0, 0, 0, 1),
    lg("roof-r", [(0, "#30353D", N), (1, "#1A1D22", N)], 0, 0, 0, 1),
    lg("thatch-f", [(0, "#8A7038", N), (1, "#D2B46A", N)], 0, 0, 0, 1),
    lg("thatch-b", [(0, "#6A5428", N), (1, "#9A803E", N)], 0, 0, 0, 1),
    lg("thatch-r", [(0, "#5A4620", N), (1, "#3A2C12", N)], 0, 0, 0, 1),
    lg("water", [(0, "#4A9AB8", N), (0.5, "#2F6E8E", N), (1, "#123848", N)]),
    lg("hull", [(0, "#8A6440", N), (1, "#3A2616", N)], 0, 0, 0, 1),
    lg("deck", [(0, "#C8A070", N), (1, "#8A6640", N)]),
    lg("earth", [(0, "#8A7450", N), (1, "#5A4A30", N)]),
    lg("grass", [(0, "#8E8A44", N), (0.55, "#6A6A30", N), (1, "#3E4420", N)]),
    lg("gold-t", [(0, "#FFF2C0", N), (0.45, "#E8C064", N), (1, "#9A6A1E", N)], 0, 0, 0, 1),
    lg("lacquer", [(0, "#4A2A18", N), (0.5, "#2E1A0E", N), (1, "#1A0E06", N)], 0, 0, 0, 1),
    rg("skin", [(0, "#F6D8B4", N), (0.6, "#D49C70", N), (1, "#8A5A3A", N)]),
    rg("hat", [(0, "#F6E8BE", N), (0.55, "#C8AA6A", N), (1, "#6A5428", N)]),
    rg("felt", [(0, "#5A5650", N), (0.6, "#26231F", N), (1, "#0E0D0B", N)]),
    rg("brass", [(0, "#FFF0C8", N), (0.5, "#C9A35A", N), (1, "#4E3C1A", N)]),
    rg("onggi", [(0, "#C08E60", N), (0.45, "#6E4628", N), (1, "#24140A", N)], 0.32, 0.28, 0.8),
    rg("cotton", [(0, "#FFFFFF", N), (0.6, "#EDE7D6", N), (1, "#9A927C", N)]),
    rg("ore", [(0, "#F6FFFC", N), (0.45, ORE, N), (1, "#236A5C", N)], 0.3, 0.25, 0.9),
    rg("oreglow", [(0, ORE_G, "0.85"), (0.4, ORE, "0.4"), (1, ORE, "0")], 0.5, 0.5, 0.5),
    rg("fire", [(0, "#FFFBE0", N), (0.3, "#FFD066", N), (0.65, "#EE6A2E", "0.9"), (1, RED, "0")], 0.5, 0.6, 0.55),
    rg("fireglow", [(0, "#FFC860", "0.75"), (0.5, "#EE6A2E", "0.25"), (1, "#EE6A2E", "0")], 0.5, 0.5, 0.5),
    rg("mist", [(0, "#E6D8FA", "0.9"), (0.45, MIST, "0.5"), (1, MIST, "0")], 0.5, 0.5, 0.5),
    rg("smokeg", [(0, "#8A8478", "0.85"), (0.6, "#4A463E", "0.5"), (1, "#3A3630", "0")], 0.5, 0.5, 0.5),
    rg("lantern", [(0, "#FFF6D8", N), (0.5, "#EE7040", N), (1, "#7A2414", N)]),
    rg("hole", [(0, "#151209", N), (0.85, "#2A251A", N), (1, "#5A503C", N)], 0.55, 0.6, 0.6),
    rg("vign", [(0, "#000", "0"), (0.7, "#000", "0"), (1, "#000", "0.55")], 0.5, 0.5, 0.75),
])


# ---------------------------------------------------------------- primitives

def shadow(cx, cy, rx, ry, op=0.5, filt="soft"):
    return f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx)}" ry="{f(ry)}" fill="#0a0704" opacity="{op}" filter="url(#r3-{filt})"></ellipse>'


def poly(ps, fill, extra=""):
    return f'<polygon points="{pts(ps)}" fill="{fill}"{extra}></polygon>'


def line(a, b, col, w, op=1, cap="round"):
    if col.startswith("url(") and " " not in col:
        col = col + " #6E5030"  # fallback paint when the bbox is zero-width
    return (f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="{col}" stroke-width="{f(w)}" '
            f'stroke-linecap="{cap}" opacity="{op}" fill="none"></path>')


def spec(cx, cy, r=0.9, op=0.85):
    return f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="#FFF" opacity="{op}"></circle>'


def glow(cx, cy, r, grad="oreglow", op=1):
    return f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="url(#r3-{grad})" opacity="{op}"></circle>'


def sprite(body, filt="ol"):
    return f'<g filter="url(#r3-{filt})">{body}</g>'


def svg(vb, body, w, h, label):
    return f'<svg width="{w}" height="{h}" viewBox="{vb}" role="img" aria-label="{label}">{body}</svg>'


_clip = [0]


def clipped(ps, body):
    _clip[0] += 1
    cid = f"r3-c{_clip[0]}"
    return f'<clipPath id="{cid}"><polygon points="{pts(ps)}"></polygon></clipPath><g clip-path="url(#{cid})">{body}</g>'


# ---------------------------------------------------------------- isometric

class Iso:
    """2:1 dimetric projection. s = half tile width (px); z in tile units."""

    def __init__(self, ox, oy, s):
        self.ox, self.oy, self.s = ox, oy, s

    def P(self, x, y, z=0):
        return (self.ox + (x - y) * self.s, self.oy + (x + y) * self.s / 2 - z * self.s)

    def face_l(self, x0, x1, y, z0, z1):  # +y face, lit
        return [self.P(x0, y, z0), self.P(x1, y, z0), self.P(x1, y, z1), self.P(x0, y, z1)]

    def face_r(self, x, y0, y1, z0, z1):  # +x face, shade
        return [self.P(x, y1, z0), self.P(x, y0, z0), self.P(x, y0, z1), self.P(x, y1, z1)]

    def face_t(self, x0, y0, x1, y1, z):
        return [self.P(x0, y0, z), self.P(x1, y0, z), self.P(x1, y1, z), self.P(x0, y1, z)]


def box(iso, x0, y0, x1, y1, z0, z1, top, left, right, tx=""):
    t = f' filter="url(#r3-{tx})"' if tx else ""
    return (poly(iso.face_l(x0, x1, y1, z0, z1), left, t)
            + poly(iso.face_r(x1, y0, y1, z0, z1), right, t)
            + poly(iso.face_t(x0, y0, x1, y1, z1), top, t)
            + line(iso.P(x0, y1, z1), iso.P(x1, y1, z1), "#FFF6E0", 0.6, 0.45)
            + line(iso.P(x1, y1, z0), iso.P(x1, y1, z1), "#000", 0.6, 0.35))


def stone_base(iso, x0, y0, x1, y1, z0, z1, rng, courses=2):
    out = box(iso, x0, y0, x1, y1, z0, z1, "url(#r3-stone-top)", "url(#r3-stone-lit)", "url(#r3-stone-sh)", "tx-stone")
    h = (z1 - z0) / courses
    for c in range(courses):
        za, zb = z0 + c * h, z0 + (c + 1) * h
        out += line(iso.P(x0, y1, zb), iso.P(x1, y1, zb), "#2A2620", 0.5, 0.7)
        out += line(iso.P(x1, y1, zb), iso.P(x1, y0, zb), "#14120E", 0.5, 0.7)
        x = x0 + (0.18 if c % 2 else 0.05)
        while x < x1 - 0.05:
            out += line(iso.P(x, y1, za), iso.P(x, y1, zb), "#2A2620", 0.45, 0.6)
            x += 0.25 + rng.random() * 0.2
        y = y0 + (0.1 if c % 2 else 0.22)
        while y < y1 - 0.05:
            out += line(iso.P(x1, y, za), iso.P(x1, y, zb), "#14120E", 0.45, 0.6)
            y += 0.25 + rng.random() * 0.2
    # top edge highlight + AO line at ground
    out += line(iso.P(x0, y1, z0), iso.P(x1, y1, z0), "#000", 1.2, 0.35)
    out += line(iso.P(x1, y1, z0), iso.P(x1, y0, z0), "#000", 1.2, 0.35)
    return out


def timber_walls(iso, x0, y0, x1, y1, z0, z1, posts_x=3, posts_y=3, windows=True, door=False, sh_wall="plaster-sh"):
    """Plastered walls with timber posts, a lintel and lattice windows."""
    out = (poly(iso.face_l(x0, x1, y1, z0, z1), "url(#r3-plaster)", ' filter="url(#r3-tx-paper)"')
           + poly(iso.face_r(x1, y0, y1, z0, z1), f"url(#r3-{sh_wall})", ' filter="url(#r3-tx-paper)"'))
    # windows / doors on the lit face
    span = (x1 - x0) / posts_x
    for i in range(posts_x):
        a, b = x0 + i * span + span * 0.18, x0 + (i + 1) * span - span * 0.18
        if door and i == posts_x // 2:
            za, zb = z0 + 0.02, z0 + (z1 - z0) * 0.78
            out += poly(iso.face_l(a, b, y1, za, zb), "#3A2414")
            for k in range(1, 4):
                xx = a + (b - a) * k / 4
                out += line(iso.P(xx, y1, za), iso.P(xx, y1, zb), "#7A5634", 0.6)
            out += line(iso.P((a + b) / 2, y1, za), iso.P((a + b) / 2, y1, zb), "#1A0E06", 0.9)
            continue
        if not windows:
            continue
        za, zb = z0 + (z1 - z0) * 0.32, z0 + (z1 - z0) * 0.8
        out += poly(iso.face_l(a, b, y1, za, zb), "#EADFC2")
        n = 4
        for k in range(1, n):
            xx = a + (b - a) * k / n
            out += line(iso.P(xx, y1, za), iso.P(xx, y1, zb), "#6A4A2C", 0.45)
        for k in range(1, 4):
            zz = za + (zb - za) * k / 4
            out += line(iso.P(a, y1, zz), iso.P(b, y1, zz), "#6A4A2C", 0.45)
        out += poly(iso.face_l(a, b, y1, za, zb), "none", ' stroke="#3A2414" stroke-width="0.8"')
    # posts
    for i in range(posts_x + 1):
        x = x0 + i * span
        w = 0.05
        out += poly(iso.face_l(max(x0, x - w), min(x1, x + w), y1, z0, z1), "url(#r3-wood-lit)", ' filter="url(#r3-tx-wood-v)"')
    yspan = (y1 - y0) / posts_y
    for i in range(posts_y + 1):
        y = y0 + i * yspan
        w = 0.05
        out += poly(iso.face_r(x1, max(y0, y - w), min(y1, y + w), z0, z1), "url(#r3-wood-sh)")
    # lintel beam
    out += poly(iso.face_l(x0, x1, y1, z1 - 0.1, z1), "url(#r3-wood-lit)")
    out += poly(iso.face_r(x1, y0, y1, z1 - 0.1, z1), "url(#r3-wood-sh)")
    out += poly(iso.face_l(x0, x1, y1, z0, z0 + 0.06), "#4A3220")
    return out



def thatch_roof(iso, x0, y0, x1, y1, ze, zr, rng, ov=0.25):
    ym = (y0 + y1) / 2
    hip = (y1 - y0) / 2 + ov
    E00, E10 = iso.P(x0 - ov, y0 - ov, ze), iso.P(x1 + ov, y0 - ov, ze)
    E11, E01 = iso.P(x1 + ov, y1 + ov, ze), iso.P(x0 - ov, y1 + ov, ze)
    R0, R1 = iso.P(x0 - ov + hip * 0.8, ym, zr), iso.P(x1 + ov - hip * 0.8, ym, zr)
    out = poly([iso.P(x0, y1, ze), iso.P(x1, y1, ze), iso.P(x1, y1, ze - 0.35), iso.P(x0, y1, ze - 0.35)], "#000", ' opacity="0.3"')
    out += poly([E00, E10, R1, R0], "url(#r3-thatch-b)", ' filter="url(#r3-tx-straw)"')
    out += poly([E10, E11, R1], "url(#r3-thatch-r)", ' filter="url(#r3-tx-straw)"')
    # rounded front: bulge the ridge-to-eave with a curve
    c0 = lerp(E01, R0, 0.5)
    c1 = lerp(E11, R1, 0.5)
    front = (f'M{f(E01[0])} {f(E01[1])} L{f(E11[0])} {f(E11[1])} Q{f(c1[0] + 3)} {f(c1[1] - 2)} {f(R1[0])} {f(R1[1])} '
             f'L{f(R0[0])} {f(R0[1])} Q{f(c0[0] - 2)} {f(c0[1] - 3)} {f(E01[0])} {f(E01[1])} Z')
    out += f'<path d="{front}" fill="url(#r3-thatch-f)" filter="url(#r3-tx-straw)"></path>'
    straws = ""
    for _ in range(160):
        t = rng.random()
        u = rng.random()
        a = lerp(lerp(E01, E11, t), lerp(R0, R1, t), u)
        straws += line(a, (a[0] + rng.uniform(-0.6, 0.6), a[1] + 2.2 + rng.random() * 2), "#F2DC98" if rng.random() < 0.5 else "#5A4620", 0.4, 0.55)
    # rope net (새끼줄)
    for k in range(1, 6):
        t = k / 6
        straws += line(lerp(E01, E11, t), lerp(R0, R1, t), "#4A3818", 0.6, 0.65)
    for k in range(1, 4):
        t = k / 4
        straws += line(lerp(E01, R0, t), lerp(E11, R1, t), "#4A3818", 0.6, 0.6)
    out += clipped([E01, E11, R1, R0], straws)
    # shaggy eave fringe
    fr = ""
    for k in range(40):
        t = k / 39
        a = lerp(E01, E11, t)
        fr += line(a, (a[0] + rng.uniform(-0.5, 0.5), a[1] + 2 + rng.random() * 1.6), "#6A5428", 0.7, 0.9)
    for k in range(20):
        t = k / 19
        a = lerp(E11, E10, t)
        fr += line(a, (a[0], a[1] + 1.6 + rng.random()), "#3A2C12", 0.7, 0.9)
    out += fr + line(R0, R1, "#5A4620", 2.6) + line((R0[0], R0[1] - 0.6), (R1[0], R1[1] - 0.6), "#E0C67E", 0.7, 0.8)
    return out


# ---------------------------------------------------------------- ground


def ground_patch(iso, x0, y0, x1, y1, rng, yard=None, n_tuft=60, leaves=True, wet=False):
    """Textured grass diamond, faded at the edge, with tufts, pebbles and leaves."""
    ps = iso.face_t(x0, y0, x1, y1, 0)
    xs = [p[0] for p in ps]
    ys = [p[1] for p in ps]
    out = f'<g mask="url(#r3-fade)">'
    out += f'<rect x="{f(min(xs))}" y="{f(min(ys))}" width="{f(max(xs) - min(xs))}" height="{f(max(ys) - min(ys))}" fill="none"></rect>'
    out += poly(ps, "url(#r3-grass)", ' filter="url(#r3-tx-grass)"')
    if yard:
        yx0, yy0, yx1, yy1 = yard
        yp = iso.face_t(yx0, yy0, yx1, yy1, 0)
        out += poly(yp, "url(#r3-earth)", ' filter="url(#r3-tx-earth)"')
        out += poly(yp, "none", ' stroke="#3A2E1C" stroke-width="2" opacity="0.35" filter="url(#r3-blur1)"')
    if wet:
        for _ in range(3):
            x, y = rng.uniform(x0 + 0.5, x1 - 0.5), rng.uniform(y0 + 0.5, y1 - 0.5)
            c = iso.P(x, y)
            out += f'<ellipse cx="{f(c[0])}" cy="{f(c[1])}" rx="{f(iso.s * 0.5)}" ry="{f(iso.s * 0.2)}" fill="#5A7A84" opacity="0.5"></ellipse>'
    for _ in range(n_tuft):
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        if yard and yard[0] < x < yard[2] and yard[1] < y < yard[3]:
            if rng.random() < 0.85:
                continue
        c = iso.P(x, y)
        col = rng.choice(["#A8A050", "#C8B464", "#6E7034", "#B49A4A"])
        for k in range(4):
            a = -math.pi / 2 + rng.uniform(-0.7, 0.7)
            l = rng.uniform(2.2, 4.2)
            out += line(c, (c[0] + math.cos(a) * l, c[1] + math.sin(a) * l), col, 0.55, 0.9)
    for _ in range(int(n_tuft * 0.35)):
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        c = iso.P(x, y)
        r = rng.uniform(0.6, 1.4)
        out += (f'<ellipse cx="{f(c[0] + 0.4)}" cy="{f(c[1] + 0.4)}" rx="{f(r)}" ry="{f(r * 0.6)}" fill="#000" opacity="0.35"></ellipse>'
                f'<ellipse cx="{f(c[0])}" cy="{f(c[1])}" rx="{f(r)}" ry="{f(r * 0.65)}" fill="#9A9282"></ellipse>'
                f'<ellipse cx="{f(c[0] - r * 0.3)}" cy="{f(c[1] - r * 0.25)}" rx="{f(r * 0.4)}" ry="{f(r * 0.25)}" fill="#D8D0BC" opacity="0.8"></ellipse>')
    if leaves:
        for _ in range(int(n_tuft * 0.25)):
            x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
            c = iso.P(x, y)
            col = rng.choice(["#C8432B", "#D86A2E", "#E2B13C", "#9A3A20"])
            out += f'<ellipse cx="{f(c[0])}" cy="{f(c[1])}" rx="1.3" ry="0.7" fill="{col}" transform="rotate({rng.randint(0, 180)} {f(c[0])} {f(c[1])})" opacity="0.9"></ellipse>'
    for _ in range(int(n_tuft * 0.12)):
        x, y = rng.uniform(x0, x1), rng.uniform(y0, y1)
        c = iso.P(x, y)
        col = rng.choice(["#F2E6C8", "#E8D06A", "#C8A0E0"])
        out += f'<circle cx="{f(c[0])}" cy="{f(c[1] - 1.5)}" r="0.75" fill="{col}"></circle>'
    out += "</g>"
    return out


# ---------------------------------------------------------------- props


def lantern(cx, cy, r=2.2, lit=True):
    out = line((cx, cy - r * 2.2), (cx, cy - r * 1.1), "#2A1A10", 0.5)
    if lit:
        out += glow(cx, cy, r * 4, "fireglow", 0.8)
    out += f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(r)}" ry="{f(r * 1.25)}" fill="url(#r3-lantern)"></ellipse>'
    out += f'<path d="M{f(cx - r)} {f(cy)} H{f(cx + r)} M{f(cx)} {f(cy - r * 1.25)} V{f(cy + r * 1.25)}" stroke="#7A2414" stroke-width="0.3" opacity="0.6"></path>'
    out += f'<rect x="{f(cx - r * 0.6)}" y="{f(cy - r * 1.4)}" width="{f(r * 1.2)}" height="{f(r * 0.3)}" fill="#2A1A10"></rect>'
    out += f'<rect x="{f(cx - r * 0.6)}" y="{f(cy + r * 1.15)}" width="{f(r * 1.2)}" height="{f(r * 0.3)}" fill="#2A1A10"></rect>'
    out += line((cx, cy + r * 1.45), (cx, cy + r * 2.4), RED, 0.5)
    return out



def ore_cluster(cx, cy, s=1.0, halo=True):
    def p(dx, dy):
        return f"{f(cx + dx * s)},{f(cy + dy * s)}"
    out = glow(cx, cy - 4 * s, 18 * s, "oreglow", 0.6) if halo else ""
    out += (f'<polygon points="{p(-11,3)} {p(-6,-14)} {p(-1,-4)} {p(-3,6)}" fill="url(#r3-ore)" stroke="#1E5A4E" stroke-width="0.5"></polygon>'
            f'<polygon points="{p(-6,-14)} {p(-1,-4)} {p(-3,-15)}" fill="#F0FFFA" opacity="0.85"></polygon>'
            f'<polygon points="{p(-2,7)} {p(3,-19)} {p(10,-5)} {p(8,7)}" fill="url(#r3-ore)" stroke="#1E5A4E" stroke-width="0.5"></polygon>'
            f'<polygon points="{p(3,-19)} {p(10,-5)} {p(7,-4)}" fill="#2E7A6A" opacity="0.75"></polygon>'
            f'<polygon points="{p(3,-19)} {p(1,-3)} {p(-1,7)} {p(2,7)}" fill="#E8FFF8" opacity="0.5"></polygon>'
            f'<polygon points="{p(7,7)} {p(13,-2)} {p(16,7)}" fill="url(#r3-ore)" stroke="#1E5A4E" stroke-width="0.5"></polygon>'
            f'<polygon points="{p(-14,7)} {p(-11,-2)} {p(-7,7)}" fill="url(#r3-ore)" stroke="#1E5A4E" stroke-width="0.5"></polygon>'
            + spec(cx + 1.5 * s, cy - 13 * s, 1.0 * s))
    return out



def rock_with_ore(cx, cy, s=1.0):
    out = shadow(cx + 3 * s, cy + 3 * s, 20 * s, 7 * s, 0.55)
    out += (f'<path d="M{f(cx - 20 * s)} {f(cy + 2 * s)} L{f(cx - 14 * s)} {f(cy - 9 * s)} L{f(cx - 2 * s)} {f(cy - 13 * s)} L{f(cx + 12 * s)} {f(cy - 8 * s)} '
            f'L{f(cx + 20 * s)} {f(cy + 2 * s)} L{f(cx + 6 * s)} {f(cy + 7 * s)} L{f(cx - 10 * s)} {f(cy + 6 * s)} Z" fill="url(#r3-stone-lit)" filter="url(#r3-tx-stone)"></path>')
    out += f'<path d="M{f(cx + 12 * s)} {f(cy - 8 * s)} L{f(cx + 20 * s)} {f(cy + 2 * s)} L{f(cx + 6 * s)} {f(cy + 7 * s)} L{f(cx + 4 * s)} {f(cy - 2 * s)} Z" fill="url(#r3-stone-sh)"></path>'
    out += f'<path d="M{f(cx - 14 * s)} {f(cy - 9 * s)} L{f(cx - 2 * s)} {f(cy - 13 * s)} L{f(cx + 12 * s)} {f(cy - 8 * s)}" stroke="#E8E0CC" stroke-width="0.6" fill="none" opacity="0.6"></path>'
    out += ore_cluster(cx - 2 * s, cy - 6 * s, 0.75 * s)
    return out



# ---------------------------------------------------------------- 오방 신당 figures (see obang_figs.py)

import sys  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from figure_kit import Kit, Mat, MATS  # noqa: E402
import obang_figs as O  # noqa: E402

K = Kit("r3k")
KM = dict(MATS)
OBANG = O.OBANG


def place(x, y, s, body):
    return f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">{body}</g>'


def plinth(rng, cx=100, cy=160, s=38, n=60, yard=None, wet=False):
    iso = Iso(cx, cy - s * 1.5, s)
    return ground_patch(iso, 0, 0, 3, 3, rng, yard=yard, n_tuft=n, wet=wet)


def u_keeper():
    rng = random.Random(301)
    return plinth(rng, yard=(0.6, 0.6, 2.4, 2.4)) + place(112, 178, 1.3, O.rope_keeper(K))


def u_envoy():
    rng = random.Random(302)
    return plinth(rng, yard=(0.7, 0.7, 2.3, 2.3)) + place(100, 184, 0.99, O.death_envoy(K))


def u_mudang():
    rng = random.Random(303)
    return plinth(rng, yard=(0.6, 0.6, 2.4, 2.4)) + place(90, 182, 1.02, O.mudang(K))


def u_jangseung():
    rng = random.Random(304)
    return plinth(rng, cx=125, cy=158, s=46, n=70, yard=(0.5, 0.5, 2.5, 2.5)) + place(122, 184, 0.88, O.jangseung_pair(K))


def u_duck():
    rng = random.Random(305)
    return plinth(rng, n=50) + glow(100, 60, 34, "mist", 0.2) + place(100, 176, 1.02, O.sotdae_duck(K, alt=96))


def u_generals():
    rng = random.Random(306)
    return plinth(rng, cx=155, cy=154, s=54, n=90, yard=(0.4, 0.4, 2.6, 2.6)) + place(150, 178, 0.92, O.obang_generals(K))


# ---------------------------------------------------------------- 건물: posts, flags and rope; no giwa halls

def b_frame(rng, s, foot, yard=True, n=60, ox=100, oy=None, wet=False):
    """Ground plinth around a footprint `foot` (tiles) centred in the view."""
    pad = 0.55
    oy = 112 - (foot[0] + foot[1]) * s / 4 if oy is None else oy
    iso = Iso(ox - (foot[0] - foot[1]) * s / 2, oy, s)
    y = (0, 0, foot[0], foot[1]) if yard else None
    g = ground_patch(iso, -pad, -pad, foot[0] + pad, foot[1] + pad, rng, yard=y, n_tuft=n, wet=wet)
    return iso, g


def stake(iso, x, y, z=0.5):
    a, b = iso.P(x, y, 0), iso.P(x, y, z)
    return K.form(a, b, 1.2, 0.9, O.WOOD, ao_start=False, spec=False)


def rope_ring(iso, x0, y0, x1, y1, seed=1, z=0.46):
    """금줄 territory marker around a footprint: (back half, front half) so a building can stand between them."""
    corners_b = [(x0, y1), (x0, y0), (x1, y0)]
    corners_f = [(x0, y1), (x1, y1), (x1, y0)]
    back = "".join(stake(iso, x, y, z + 0.04) for x, y in corners_b)
    back += O.geumjul(K, [iso.P(x, y, z) for x, y in corners_b], sag=3.0, seed=seed)
    front = O.geumjul(K, [iso.P(x, y, z) for x, y in corners_f], sag=3.0, seed=seed + 1)
    front += stake(iso, x1, y1, z + 0.04)
    return back, front


def stones(cx, cy, w, rows, rng, r0=4.2):
    """A hand-stacked cairn: rows narrow toward the top, each stone its own shaded ball."""
    out = K.ao(cx, cy + 1, w * 0.62, 3.6, 0.6)
    y = cy
    for row in range(rows):
        t = row / max(1, rows - 1)
        rw = w * (1 - 0.82 * t)
        count = max(1, int(rw / (r0 * 1.4)))
        for i in range(count):
            x = cx - rw / 2 + rw * (i + 0.5) / count + rng.uniform(-0.8, 0.8)
            r = r0 * rng.uniform(0.75, 1.1) * (1 - 0.25 * t)
            out += K.ball(x, y + rng.uniform(-0.7, 0.7), r, O.STONE, ry=r * 0.74)
        y -= r0 * 1.15
    return out


def cloth_strips(a, b, n, rng, ln=11.0):
    """오방색 cloth strips tied along a rope."""
    cols = [OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#2E2A28"]
    out = O.stroke(f"M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}", "#3A2A10", 1.3) + O.stroke(f"M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}", "#C8A860", 0.7)
    for i in range(n):
        t = (i + 0.5) / n
        x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        c = cols[i % 5]
        l = ln * rng.uniform(0.8, 1.2)
        out += (f'<path d="M{f(x - 1)} {f(y)} L{f(x + 1)} {f(y)} L{f(x + 1.4 + rng.uniform(-0.8, 0.8))} {f(y + l)} L{f(x - 0.8)} {f(y + l - 1.2)} Z" fill="{c}" stroke="#120D08" stroke-width="0.3" stroke-opacity="0.7"></path>')
    return out


def b_seonang():
    rng = random.Random(401)
    iso, g = b_frame(rng, 23, (3, 3), oy=52)
    back, front = rope_ring(iso, 0.05, 0.05, 2.95, 2.95, seed=11)
    out = g + back
    base = iso.P(1.25, 1.2, 0)
    bx, by = base
    out += K.cast_shadow(bx + 8, by + 2, 34, 7, 0.55)
    # gnarled sacred tree: roots, trunk and limbs as continuous tubes
    for dx, dy in ((-20, 4), (17, 5), (-10, 8), (9, 9)):
        out += K.tube([(bx, by - 8), (bx + dx * 0.5, by - 2), (bx + dx, by + dy)], [5, 3.2, 1], O.WOOD_D, n_per=4, chunks_per_seg=2)
    out += K.tube([(bx, by), (bx - 3, by - 28), (bx + 2, by - 56)], [11, 8.4, 7], O.WOOD_D, n_per=6)
    limbs = [((bx + 1, by - 50), (bx - 24, by - 66), (bx - 42, by - 70)), ((bx + 2, by - 54), (bx + 22, by - 72), (bx + 42, by - 72)),
             ((bx, by - 56), (bx - 6, by - 78), (bx - 12, by - 92)), ((bx + 2, by - 54), (bx + 12, by - 80), (bx + 18, by - 94))]
    for a, c, b in limbs:
        out += K.tube([a, c, b], [5, 3, 1.4], O.WOOD_D, n_per=4, chunks_per_seg=2)
    leaf = [Mat("#2E5A34", "#6E9E5A", "#0A1C0E", "#3A5A58", spec_op=0.1, gloss=0.1), Mat("#3C6A3A", "#86B266", "#0E2410", "#44625C", spec_op=0.1, gloss=0.1)]
    for _ in range(58):
        a = rng.uniform(0, math.tau)
        rr = rng.uniform(0, 1) ** 0.5
        x = bx + math.cos(a) * rr * 58
        y = by - 88 + math.sin(a) * rr * 24
        out += K.ball(x, y, rng.uniform(5.6, 10), rng.choice(leaf), outline=False, ry=rng.uniform(4.4, 7))
    # strips hang on a rope wrapped round the trunk and from the low limbs
    out += cloth_strips((bx - 40, by - 66), (bx - 6, by - 46), 7, rng, 15)
    out += cloth_strips((bx + 6, by - 48), (bx + 40, by - 66), 7, rng, 15)
    out += cloth_strips((bx - 10, by - 24), (bx + 8, by - 21), 5, rng, 10)
    # small plank shrine, front left
    sx0, sy0, sx1, sy1 = 0.25, 2.0, 1.0, 2.75
    out += box(iso, sx0, sy0, sx1, sy1, 0, 0.5, "url(#r3-wood-lit)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    dl = iso.face_l(sx0 + 0.25, sx1 - 0.25, sy1, 0, 0.36)
    out += poly(dl, "#120C07")
    out += glow((dl[0][0] + dl[1][0]) / 2, dl[0][1] - 8, 9, "holy", 0.8)
    out += thatch_roof(iso, sx0, sy0, sx1, sy1, 0.5, 0.9, rng, ov=0.14)
    # cairn, front right
    p = iso.P(2.35, 1.95, 0)
    out += stones(p[0], p[1], 34, 5, rng)
    return out + front


def b_spring():
    rng = random.Random(402)
    iso, g = b_frame(rng, 30, (2, 2), oy=64)
    back, front = rope_ring(iso, 0, 0, 2, 2, seed=21)
    out = g + back
    # back-standing rock and wooden flume
    p = iso.P(0.5, 0.5, 0)
    out += stones(p[0], p[1], 40, 5, rng, r0=5.0)
    out += stone_base(iso, 0.45, 0.45, 1.75, 1.75, 0, 0.22, rng, courses=1)
    pool = iso.face_t(0.58, 0.58, 1.62, 1.62, 0.22)
    out += poly(pool, "url(#r3-water)")
    px, py = iso.P(1.1, 1.1, 0.22)
    out += f'<ellipse cx="{f(px)}" cy="{f(py)}" rx="16" ry="6" fill="none" stroke="#CFE8F0" stroke-width="0.7" opacity="0.7"></ellipse>'
    out += f'<ellipse cx="{f(px)}" cy="{f(py)}" rx="8" ry="3" fill="none" stroke="#CFE8F0" stroke-width="0.6" opacity="0.65"></ellipse>'
    a, b = iso.P(0.55, 0.6, 0.86), iso.P(1.0, 1.0, 0.56)
    out += K.form(a, b, 2.1, 1.9, O.WOOD, ao_start=False)
    out += O.stroke(f"M{f(b[0])} {f(b[1])} q1 8 0 {f(py - b[1])}", "#CFEFF4", 1.3, 0.9)
    out += glow(px, py - 14, 36, "mist", 0.75)
    for k in range(3):
        out += f'<path d="M{f(px - 12 + k * 12)} {f(py - 8)} q-7 -12 2 -22 q8 -10 -1 -22" fill="none" stroke="{MIST}" stroke-width="2.4" stroke-linecap="round" opacity="0.45" filter="url(#r3-blur1)"></path>'
    # gourd dipper on the rim, one prayer flag
    gp = iso.P(1.75, 1.2, 0.26)
    out += K.ball(gp[0], gp[1], 4.2, Mat("#BC8A3A", "#F2CC7A", "#3A2408", "#7E7050", spec_op=0.5, gloss=0.6), ry=3)
    fp = iso.P(1.85, 0.25, 0)
    out += O.flag(K, fp, (fp[0], fp[1] - 56), OBANG["청"], side=1, w=18, h=13)
    return out + front


def b_gutdang():
    rng = random.Random(403)
    iso, g = b_frame(rng, 30, (2, 2), oy=68)
    back, front = rope_ring(iso, -0.15, -0.15, 2.15, 2.15, seed=31)
    out = g + back
    z = 1.42
    cn = [(0.15, 0.15), (1.85, 0.15), (1.85, 1.85), (0.15, 1.85)]
    # the rear posts stand behind the cloth, the front posts in front
    for i in (0, 1, 3):
        x, y = cn[i]
        out += K.form(iso.P(x, y, 0), iso.P(x, y, z + 0.08), 1.8, 1.5, O.WOOD)
    # offering table and jars under the canopy
    out += box(iso, 0.55, 0.55, 1.3, 1.1, 0.26, 0.34, "url(#r3-wood-lit)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    for x, y in ((0.65, 1.0), (1.2, 1.0), (0.65, 0.65), (1.2, 0.65)):
        out += K.form(iso.P(x, y, 0), iso.P(x, y, 0.28), 0.9, 0.9, O.WOOD_D, ao_start=False, spec=False)
    t = iso.P(0.92, 0.82, 0.34)
    out += f'<ellipse cx="{f(t[0])}" cy="{f(t[1])}" rx="7" ry="3" fill="#F2EAD2" stroke="#6A6250" stroke-width="0.4"></ellipse>'
    out += O.flower(K, t[0] - 2, t[1] - 3, OBANG["적"], 0.7) + O.flower(K, t[0] + 3, t[1] - 3, OBANG["황"], 0.7)
    # standing drum
    d = iso.P(1.45, 1.45, 0.28)
    out += K.ball(d[0], d[1], 9, MATS["lacquer"], ry=9.2) + f'<ellipse cx="{f(d[0] - 3)}" cy="{f(d[1])}" rx="4.8" ry="7.2" fill="#E8DCB8" stroke="#3A2410" stroke-width="0.7"></ellipse>'
    # white ritual canopy, sagging, with a scalloped front valance and a shaded underside
    p = [iso.P(x, y, z) for x, y in cn]
    c_ = iso.P(1.0, 1.0, z - 0.2)
    out += f'<path d="M{f(p[0][0])} {f(p[0][1])} Q{f(c_[0] - 2)} {f(c_[1] - 3)} {f(p[1][0])} {f(p[1][1])} Q{f(c_[0] + 4)} {f(c_[1] + 3)} {f(p[2][0])} {f(p[2][1])} Q{f(c_[0])} {f(c_[1] + 2)} {f(p[3][0])} {f(p[3][1])} Q{f(c_[0] - 4)} {f(c_[1] + 2)} {f(p[0][0])} {f(p[0][1])} Z" fill="url(#r3-cloth)" stroke="#120D08" stroke-width="0.7" stroke-opacity="0.75"></path>'
    for k in range(1, 5):
        a = (p[3][0] + (p[2][0] - p[3][0]) * k / 5, p[3][1] + (p[2][1] - p[3][1]) * k / 5)
        b = (p[0][0] + (p[1][0] - p[0][0]) * k / 5, p[0][1] + (p[1][1] - p[0][1]) * k / 5)
        out += O.stroke(f"M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}", "#6A6250", 0.7, 0.4)
    for a, b in ((p[3], p[2]), (p[2], p[1])):
        n = 7
        scallops = f"M{f(a[0])} {f(a[1])}"
        for i in range(n):
            x0 = a[0] + (b[0] - a[0]) * (i + 1) / n
            y0 = a[1] + (b[1] - a[1]) * (i + 1) / n
            xm = a[0] + (b[0] - a[0]) * (i + 0.5) / n
            ym = a[1] + (b[1] - a[1]) * (i + 0.5) / n
            scallops += f" Q{f(xm)} {f(ym + 7)} {f(x0)} {f(y0)}"
        col = "#F6F0DC" if a is p[3] else "#B8B19C"
        out += f'<path d="{scallops} Z" fill="{col}" stroke="#120D08" stroke-width="0.5" stroke-opacity="0.7"></path>'
    x, y = cn[2]
    out += K.form(iso.P(x, y, 0), iso.P(x, y, z + 0.08), 1.9, 1.6, O.WOOD)
    # five tall flags rising over the canopy
    for (x, y, c, h_) in ((0.15, 0.15, OBANG["청"], 2.2), (1.85, 0.15, OBANG["적"], 2.05), (0.15, 1.85, OBANG["황"], 1.95), (1.85, 1.85, OBANG["백"], 1.9)):
        a = iso.P(x, y, z)
        t_ = iso.P(x, y, h_ + 0.62)
        out += O.flag(K, a, t_, c, side=1 if x > 1 else -1, w=20, h=13, pole_w=1.0)
    return out + front


def b_gate():
    rng = random.Random(404)
    iso, g = b_frame(rng, 30, (2, 2), oy=68, yard=True)
    back, front = rope_ring(iso, -0.15, -0.15, 2.15, 2.15, seed=41)
    out = g + back
    wood = Mat("#3A322C", "#8A7E70", "#070504", "#44607E", spec_op=0.22, gloss=0.3)
    Y = 1.1
    x0, x1 = 0.3, 1.7
    zb, zt = 1.5, 2.25
    # dark, misted opening behind the gate
    a = iso.P(x0, Y, 0)
    b_ = iso.P(x1, Y, 0)
    c_ = iso.P(x1, Y, zb)
    d_ = iso.P(x0, Y, zb)
    out += poly([a, b_, c_, d_], "url(#r3-hole)")
    out += poly([a, b_, c_, d_], "#0A0E18", ' opacity="0.72"')
    mx = (a[0] + b_[0]) / 2
    my = (a[1] + b_[1]) / 2
    out += f'<ellipse cx="{f(mx)}" cy="{f(my - 2)}" rx="26" ry="12" fill="#BCDCF2" opacity="0.5" filter="url(#r3-soft)"></ellipse>'
    out += O.soul(K, mx - 7, my - 22, 0.72, 0.62)
    out += O.soul(K, mx + 9, my - 18, 0.55, 0.42)
    # low fences of vertical slats on either side
    for (xa, xb) in ((-0.05, x0), (x1, 2.05)):
        for i in range(6):
            x = xa + (xb - xa) * (i + 0.5) / 6
            out += K.form(iso.P(x, Y, 0), iso.P(x, Y, 0.72), 1.0, 0.8, wood, ao_start=False, spec=False)
        out += K.form(iso.P(xa, Y, 0.56), iso.P(xb, Y, 0.56), 1.0, 1.0, wood, ao_start=False, spec=False)
    # tall posts, a single flush beam, arrow slats rising above it (no curved or overhanging lintel)
    slats = 9
    for i in range(slats):
        x = x0 + (x1 - x0) * (i + 0.5) / slats
        p0, p1 = iso.P(x, Y, zb), iso.P(x, Y, zt)
        out += K.form(p0, p1, 1.05, 0.85, wood, ao_start=False, spec=False)
        out += f'<path d="M{f(p1[0] - 2)} {f(p1[1] + 1)} L{f(p1[0])} {f(p1[1] - 5)} L{f(p1[0] + 2)} {f(p1[1] + 1)} Z" fill="#A8B0BC" stroke="#14161A" stroke-width="0.4"></path>'
    out += K.form(iso.P(x0, Y, zb - 0.04), iso.P(x1, Y, zb - 0.04), 2.5, 2.5, wood, ao_start=False)
    out += K.form(iso.P(x0, Y, zt - 0.2), iso.P(x1, Y, zt - 0.2), 1.1, 1.1, wood, ao_start=False, spec=False)
    for x in (x0, x1):
        out += K.form(iso.P(x, Y, 0), iso.P(x, Y, zt + 0.06), 2.9, 2.5, wood)
    return out + O.geumjul(K, [iso.P(x0, Y, zb - 0.22), iso.P(x1, Y, zb - 0.22)], sag=3.6, seed=43) + front


def b_flagstand():
    rng = random.Random(405)
    iso, g = b_frame(rng, 30, (2, 2), oy=70)
    back, front = rope_ring(iso, -0.15, -0.15, 2.15, 2.15, seed=51)
    out = g + back
    out += stone_base(iso, 0.2, 0.2, 1.8, 1.8, 0, 0.2, rng, courses=1)
    out += stone_base(iso, 0.5, 0.5, 1.5, 1.5, 0.2, 0.38, rng, courses=1)
    # 흑 and 청 at the back, 황 centre and tallest, 백 and 적 at the front
    spots = [(0.42, 0.42, 0.2, "#2E2A28", 2.35, -1), (1.58, 0.42, 0.2, OBANG["청"], 2.3, 1), (1.0, 1.0, 0.38, OBANG["황"], 2.95, 1), (0.42, 1.58, 0.2, OBANG["백"], 2.1, -1), (1.58, 1.58, 0.2, OBANG["적"], 2.05, 1)]
    for x, y, z0, col, h_, side in spots:
        a = iso.P(x, y, z0)
        t_ = iso.P(x, y, h_)
        out += K.ball(a[0], a[1], 2.8, O.STONE, ry=1.8)
        out += O.flag(K, a, t_, col, side=side, w=28, h=19, wave=2, pole_w=1.3)
    return out + front


def haetae(s=1.0):
    """Seated stone guardian (해태) facing left: a maned, scaled beast with one small horn; composed, not a lion dog."""
    m = O.STONE
    o = [K.tube([(16, -22), (30, -28), (36, -46)], [5, 4, 3], m, n_per=4, chunks_per_seg=2)]
    o.append(K.ball(36, -48, 4.4, m))
    o.append(K.tube([(12, -6), (6, -30), (-3, -46)], [17, 16, 13], m, n_per=5))
    # chest scale rows
    for r_ in range(4):
        for c in range(4):
            x = -7 + c * 6 + (3 if r_ % 2 else 0)
            y = -40 + r_ * 7
            o.append(O.stroke(f"M{x} {y} q3 4 6 0", "#2A2A26", 0.9, 0.7))
    o.append(K.tube([(-12, -36), (-14, -20), (-14, -4)], [6.4, 5, 4.6], m, n_per=4, chunks_per_seg=2))
    o.append(K.ball(-16, -2, 6.4, m, ry=4))
    o.append(K.tube([(14, -22), (22, -12), (12, -3)], [10, 6.6, 4.6], m, n_per=4, chunks_per_seg=2))
    o.append(K.ball(8, -2, 6.6, m, ry=4))
    # mane of curls
    for a in range(8):
        t = a * math.pi / 7
        o.append(K.ball(-6 + math.cos(t) * 15, -58 - math.sin(t) * 13 + 4, 4.6, m))
    o.append(K.ball(-7, -57, 13, m, ry=12))
    o.append(K.form((-8, -68), (-9, -78), 2.3, 0.6, m, ao_start=False, spec=False))
    # face
    for x in (-13, -2.5):
        o.append(ell_(x, -60, 3, 2.7, "#141412") + ell_(x - 0.5, -60.6, 1.2, 1.1, "#D8D4C8"))
    o.append(O.stroke("M-16.5 -64 q3 -2 7 0 M-6 -64 q3.4 -2 6.6 0", "#1C1C1A", 1.4))
    o.append(K.ball(-8, -52.5, 5.4, m, ry=4))
    o.append(ell_(-8, -54, 1.8, 1.1, "#141412"))
    o.append(O.stroke("M-17 -49 Q-8 -44 2 -49", "#141412", 1.5))
    o.append(O.stroke("M-14 -48 l1 3 M-11 -47 l.6 3 M-5 -47 l-.6 3 M-2 -48 l-1 3", "#E8E4D8", 1.1))
    return f'<g transform="scale({f(s)})">{"".join(o)}</g>'


def ell_(cx, cy, rx, ry, col):
    return O.ell(cx, cy, rx, ry, col)


def b_haetae():
    rng = random.Random(406)
    iso, g = b_frame(rng, 38, (1, 1), oy=78)
    back, front = rope_ring(iso, -0.12, -0.12, 1.12, 1.12, seed=61)
    out = g + back
    out += stone_base(iso, 0.14, 0.14, 0.86, 0.86, 0, 0.34, rng, courses=2)
    p = iso.P(0.5, 0.5, 0.34)
    return out + K.cast_shadow(p[0], p[1] + 2, 20, 5, 0.5) + place(p[0], p[1] + 4, 1.12, haetae()) + front


def b_cairn():
    rng = random.Random(407)
    iso, g = b_frame(rng, 38, (1, 1), oy=80)
    back, front = rope_ring(iso, -0.12, -0.12, 1.12, 1.12, seed=71)
    out = g + back
    p = iso.P(0.5, 0.5, 0)
    out += K.form((p[0], p[1] - 44), (p[0], p[1] - 92), 1.4, 0.9, O.WOOD, ao_start=False, spec=False)
    out += cloth_strips((p[0] - 10, p[1] - 80), (p[0] + 10, p[1] - 76), 5, rng, 13)
    return out + stones(p[0], p[1] + 2, 56, 8, rng, r0=4.8) + front


# ---------------------------------------------------------------- 연출: 방울 공격 + 사망 · 파괴 · 채집 · 반납

FIG_DEFS = []


def fig_use(key, build):
    """Draw a figure once into <defs>, reuse it with <use>. Returns the id."""
    gid = f"r3-fig-{key}"
    if gid not in [i for i, _ in FIG_DEFS]:
        FIG_DEFS.append((gid, build()))
    return gid


def use(key, build, x, y, s, flip=False):
    gid = fig_use(key, build)
    fl = " scale(-1 1)" if flip else ""
    return f'<use href="#{gid}" transform="translate({f(x)} {f(y)}){fl} scale({f(s)})"></use>'


def strip_ground(seed, wet=False):
    rng = random.Random(seed)
    return ground_patch(Iso(80, 26, 26), 0, 0, 3, 3, rng, n_tuft=26, wet=wet)


def straw_target(cx, cy, hit=False):
    """A plain straw target stands in for 'an enemy' so the frames read without borrowing another faction's art."""
    o = K.cast_shadow(cx, cy, 10, 3, 0.55)
    o += K.form((cx, cy), (cx, cy - 34), 1.4, 1.2, O.WOOD, ao_start=False, spec=False)
    o += K.form((cx - 12, cy - 24), (cx + 12, cy - 24), 1.1, 1.1, O.WOOD, ao_start=False, spec=False)
    o += K.tube([(cx, cy - 28), (cx, cy - 14)], [6.6, 7.6], O.STRAW, n_per=3, chunks_per_seg=1)
    o += K.ball(cx, cy - 36, 5.2, O.STRAW)
    if hit:
        o = f'<g opacity="0.82">{o}</g>'
    return o


def bell_rings(cx, cy, n, r0=7.0, step=8.0, a0=115, a1=245, col=None):
    """Sound of the bells: expanding arcs toward the target on the left."""
    col = col or OCHRE_L
    out = ""
    for i in range(n):
        r = r0 + i * step
        s = (cx + math.cos(math.radians(a0)) * r, cy + math.sin(math.radians(a0)) * r)
        e = (cx + math.cos(math.radians(a1)) * r, cy + math.sin(math.radians(a1)) * r)
        out += f'<path d="M{f(s[0])} {f(s[1])} A{f(r)} {f(r)} 0 0 1 {f(e[0])} {f(e[1])}" fill="none" stroke="{col}" stroke-width="{f(1.5 - i * 0.12)}" stroke-linecap="round" opacity="{f(0.9 - i * 0.14)}"></path>'
    return out


MU_S = 0.46
MU_X, MU_Y = 114, 88
BELL = (MU_X - 28 * MU_S, MU_Y - 151 * MU_S)


def _mudang():
    return use("mudang", lambda: O.mudang(K), MU_X, MU_Y, MU_S)


def fx(n):
    out = strip_ground(600 + n)
    if n == 1:
        out += straw_target(38, 80) + _mudang() + glow(BELL[0], BELL[1], 15, "holy", 0.95)
    elif n == 2:
        out += straw_target(38, 80) + _mudang() + bell_rings(BELL[0], BELL[1], 5)
    elif n == 3:
        out += straw_target(38, 80, hit=True) + _mudang() + bell_rings(BELL[0], BELL[1], 7)
        out += f'<circle cx="38" cy="60" r="17" fill="{MIST}" opacity="0.28" filter="url(#r3-soft)"></circle>'
        out += O.stroke("M26 42 l3 7 M38 37 l0 8 M50 42 l-3 7", MIST, 1.5, 0.95)
        out += O.stroke("M38 66 v10 m-3 -3 l3 3 l3 -3", "#EFE8D8", 1.4)
    else:
        out += use("keeper", lambda: O.rope_keeper(K), 46, 88, 0.42) + _mudang()
        out += glow(46, 62, 26, "holy", 0.7)
        rng = random.Random(77)
        for _ in range(14):
            x, y = rng.uniform(28, 62), rng.uniform(30, 80)
            out += f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(rng.uniform(0.7, 1.5))}" fill="{OCHRE_L}" opacity="{f(rng.uniform(0.5, 0.95))}"></circle>'
        out += O.stroke("M46 22 v10 M41 27 h10", OCHRE_L, 2.2)
    return out


def fx_death():
    out = strip_ground(611)
    # a dignified parting: the robe and hat lie still while the spirit lingers above them, not a corpse
    gid = K._cyl_grad((80, 80), (-0.3, -0.95), 14, O.DOPO)
    out += K.cast_shadow(80, 86, 26, 5, 0.5)
    out += f'<path d="M54 86 Q64 72 82 74 Q100 73 110 86 Q84 92 54 86 Z" fill="url(#{gid})" stroke="#120D08" stroke-width="0.6"></path>'
    out += O.stroke("M64 83 Q80 78 100 84 M70 87 Q84 82 96 87", "#55504E", 0.8, 0.8)
    out += place(62, 70, 0.62, O.gat(K, 0, 0, 10))
    out += O.soul(K, 86, 42, 1.3, 0.85)
    out += O.stroke("M86 62 q-3 6 0 12", "#CFE8F0", 1, 0.5)
    rng = random.Random(14)
    for _ in range(8):
        x, y = 86 + rng.uniform(-14, 14), rng.uniform(22, 60)
        out += f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(rng.uniform(0.5, 1.1))}" fill="#E8F6FF" opacity="{f(rng.uniform(0.4, 0.9))}"></circle>'
    return out


def fx_wreck():
    out = strip_ground(612)
    rng = random.Random(615)
    # 굿당 down: fallen posts, a torn canopy on the ground, one flag still leaning
    out += K.form((48, 78), (96, 56), 1.9, 1.6, O.WOOD_D)
    out += K.form((112, 80), (72, 66), 1.9, 1.5, O.WOOD)
    pts_ = [(46, 74), (66, 62), (92, 60), (116, 72), (104, 84), (86, 78), (72, 88), (58, 80)]
    gid = K._cyl_grad((80, 72), (-0.4, -0.9), 26, O.HANJI)
    out += f'<path d="M' + " L".join(f"{f(x)} {f(y)}" for x, y in pts_) + f' Z" fill="url(#{gid})" stroke="#120D08" stroke-width="0.7"></path>'
    out += O.stroke("M56 72 L100 66 M64 80 L94 70 M72 66 L84 78", "#6A6250", 0.8, 0.7)
    out += O.flag(K, (112, 76), (92, 26), OBANG["적"], side=1, w=20, h=13, wave=3, pole_w=1.0)
    for x, y, r in ((62, 44, 13), (76, 32, 17), (88, 18, 12)):
        out += f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#r3-smokeg)" filter="url(#r3-smoke)" opacity="0.8"></circle>'
    for _ in range(6):
        out += f'<circle cx="{f(rng.uniform(50, 108))}" cy="{f(rng.uniform(40, 80))}" r="{f(rng.uniform(0.5, 1.1))}" fill="#FFB060" opacity="0.8"></circle>'
    return out


def fx_harvest():
    out = strip_ground(613)
    out += rock_with_ore(52, 76, 0.86)
    return out + use("keeper-strike", lambda: O.rope_keeper(K, strike=True), 96, 88, 0.48)


def fx_dropoff():
    out = strip_ground(614)
    rng = random.Random(9)
    out += stones(52, 82, 36, 5, rng, r0=3.6)
    out += use("keeper", lambda: O.rope_keeper(K), 104, 88, 0.46)
    out += ore_cluster(76, 64, 0.34, True)
    out += O.stroke("M92 50 Q78 36 56 48", ORE_G, 1.1, 0.75)
    return out + f'<text x="56" y="32" font-family="IBM Plex Mono, monospace" font-size="12" font-weight="600" fill="{ORE_G}" text-anchor="middle">+8</text>'


def emblem():
    """Seal: 天 in a ring of five flag-coloured marks; the rest of the ring is gilt."""
    ring = "".join(f'<circle cx="{f(50 + math.cos(a) * 43)}" cy="{f(50 + math.sin(a) * 43)}" r="1.3" fill="url(#r3-brass)"></circle>'
                   for a in [k * math.pi / 16 for k in range(32)])
    marks = ""
    for i, c in enumerate((OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#121110")):
        a = -math.pi / 2 + i * math.tau / 5
        x, y = 50 + math.cos(a) * 31, 50 + math.sin(a) * 31
        marks += f'<rect x="{f(x - 4)}" y="{f(y - 5)}" width="8" height="10" rx="1" fill="{c}" stroke="{OCHRE}" stroke-width="0.8" transform="rotate({f(math.degrees(a) + 90)} {f(x)} {f(y)})"></rect>'
    return (f'<circle cx="50" cy="50" r="48" fill="url(#r3-lacquer)" stroke="{OCHRE}" stroke-width="1.8"></circle>'
            f'<circle cx="50" cy="50" r="40" fill="#1E140C" stroke="#7A5A28" stroke-width="2.4"></circle>'
            + ring + marks
            + f'<text x="50" y="61" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="31" fill="url(#r3-gold-t)">天</text>')




def corner_svgs():
    """Brass corner fittings (장석) for framed panels."""
    def one(style):
        return (f'<svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" style="position: absolute; {style}">'
                f'<path d="M1 1 H16 Q12 4 10 8 Q7 7 6 10 Q4 12 1 16 Z" fill="url(#r3-brass)" stroke="#3A2A10" stroke-width="0.6"></path>'
                f'<path d="M2.4 2.4 H12" stroke="#FFF2C8" stroke-width="0.6" opacity="0.8"></path>'
                f'<circle cx="4.6" cy="4.6" r="1.4" fill="#5A4218"></circle><circle cx="4.2" cy="4.2" r="0.5" fill="#FFF2C8"></circle></svg>')
    return (one("left: -3px; top: -3px")
            + one("right: -3px; top: -3px; transform: scaleX(-1)")
            + one("left: -3px; bottom: -3px; transform: scaleY(-1)")
            + one("right: -3px; bottom: -3px; transform: scale(-1, -1)"))


SERIF = "font-family: 'Nanum Myeongjo', serif"
MONO = "font-family: 'IBM Plex Mono', monospace"
FRAME = ("position: relative; display: flex; flex-direction: column; padding: 4px; "
         "background: linear-gradient(160deg, #5A3620 0%, #2E1A0E 45%, #1A0E06 100%); "
         "box-shadow: inset 0 0 0 1px #8A6A3A, inset 0 0 0 3px #1A0E06, 0 3px 8px rgba(0,0,0,0.6)")
ART = ("display: flex; align-items: center; justify-content: center; overflow: hidden; "
       "background: radial-gradient(ellipse at 50% 40%, #2E2A1E 0%, #1A170F 70%, #100E09 100%); "
       "box-shadow: inset 0 0 0 1px #000, inset 0 0 18px rgba(0,0,0,0.85)")
HANJI = ("background-color: #E9DFC6; "
         "background-image: repeating-linear-gradient(97deg, rgba(120,100,60,0.06) 0 1px, transparent 1px 7px), "
         "repeating-linear-gradient(8deg, rgba(120,100,60,0.05) 0 1px, transparent 1px 11px), "
         "radial-gradient(ellipse at 30% 20%, #F4ECD6, #DCCFAE); "
         "color: #2A1E12; box-shadow: inset 0 1px 0 #FFF8E4, inset 0 -1px 0 #A8966C")


def unit_card(code, name, role, desc, src, fp, fn, vb_w=200):
    return f"""
    <article style="{FRAME}; min-width: 0">{corner_svgs()}
      <div style="height: 186px; {ART}">{svg(f'0 0 {vb_w} 200', fn(), '100%', 186, name)}</div>
      <div style="padding: 6px 10px 7px; display: flex; flex-direction: column; gap: 1px; flex: 1; {HANJI}">
        <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 4px">
          <h3 style="margin: 0; {SERIF}; font-size: 18px; font-weight: 800; color: #1E140A; white-space: nowrap">{name}</h3>
          <span style="{MONO}; font-size: 10.5px; font-weight: 600; color: #8A2A18; white-space: nowrap">{code} · {fp}</span>
        </div>
        <span style="font-size: 11.5px; font-weight: 600; color: #5A4428">{role}</span>
        <p style="margin: 1px 0 0; font-size: 12px; line-height: 1.4; color: #3A2C1C">{desc}</p>
        <span style="margin-top: auto; padding-top: 3px; font-size: 10.5px; line-height: 1.3; color: #6A5230">{src}</span>
      </div>
    </article>"""


def bld_card(code, name, role, fp, fn):
    return f"""
    <article style="{FRAME}">{corner_svgs()}
      <div style="height: 168px; {ART}">{svg('0 -24 200 184', fn(), 183, 168, name)}</div>
      <div style="padding: 5px 9px 7px; display: flex; flex-direction: column; gap: 1px; {HANJI}">
        <div style="display: flex; justify-content: space-between; align-items: baseline">
          <h3 style="margin: 0; {SERIF}; font-size: 16px; font-weight: 800; color: #1E140A">{name}</h3>
          <span style="{MONO}; font-size: 10.5px; font-weight: 600; color: #8A2A18">{fp}</span>
        </div>
        <span style="font-size: 11.5px; font-weight: 600; color: #5A4428">{code} · {role}</span>
      </div>
    </article>"""


def strip_cell(title, sub, body):
    return f"""
      <figure style="margin: 0; {FRAME}">{corner_svgs()}
        <div style="height: 104px; {ART}">{svg('0 0 160 110', body, 151, 104, title)}</div>
        <figcaption style="padding: 4px 8px 5px; font-size: 12.5px; font-weight: 700; {HANJI}">{title}<br><span style="font-size: 11px; font-weight: 500; color: #5A4428">{sub}</span></figcaption>
      </figure>"""


def swatch(c, label):
    return (f'<span style="display: flex; flex-direction: column; gap: 3px; align-items: center; {MONO}; font-size: 10px; color: {MUTED}">'
            f'<span style="width: 30px; height: 20px; background: {c}; box-shadow: inset 0 0 0 1px rgba(0,0,0,0.5), 0 0 0 1px #8A6A3A"></span>{label}</span>')


def cloud_orn(flip=False):
    t = ' style="transform: scaleX(-1)"' if flip else ""
    return (f'<svg width="34" height="14" viewBox="0 0 34 14" aria-hidden="true"{t}>'
            f'<path d="M2 10 H14 a4 4 0 1 0 -4 -4 a2.4 2.4 0 1 0 2.4 2.4 M14 10 H32" stroke="url(#r3-gold-t)" stroke-width="1.4" fill="none" stroke-linecap="round"></path>'
            f'<circle cx="32" cy="10" r="1.4" fill="{OCHRE}"></circle></svg>')


def section_title(t, sub):
    return (f'<h2 style="margin: 0; display: flex; align-items: center; gap: 10px; font-size: 13px; font-weight: 600; color: {INK}">'
            f'<span style="display: inline-flex; align-items: center; gap: 6px; padding: 3px 12px; {SERIF}; font-size: 16px; font-weight: 800; letter-spacing: 0.08em; color: #F6DE9A; '
            f'background: linear-gradient(180deg, #4A2A16, #24140A); box-shadow: inset 0 0 0 1px #8A6A3A, 0 2px 4px rgba(0,0,0,0.6); text-shadow: 0 1px 0 #000">'
            f'{cloud_orn(True)}{t}{cloud_orn()}</span>'
            f'<span style="font-weight: 400; color: {MUTED}">{sub}</span>'
            f'<span style="flex: 1; height: 1px; background: linear-gradient(90deg, #8A6A3A, transparent)"></span></h2>')



UNITS = [
    ("U-T1", "금줄지기", "Rope Keeper · 일꾼", "금줄을 쳐 성역을 넓힙니다.", "금줄 · 부정을 막는 왼새끼", "1×1", u_keeper, 200),
    ("U-T2", "저승사자", "Death Envoy · 근접", "쓰러뜨린 적의 혼을 거둡니다.", "무속 · 저승차사", "1×1", u_envoy, 200),
    ("U-T3", "무당", "Mudang · 원거리·지원", "방울 소리로 아군을 치유하고 적을 약화합니다.", "무속 · 방울과 부채", "1×1", u_mudang, 200),
    ("U-T4", "장승", "Jangseung · 중장", "성역 안에서 뿌리를 내려 진지가 됩니다.", "마을 수호 · 천하대장군 · 지하여장군", "2×2", u_jangseung, 250),
    ("U-T5", "솟대 오리", "Sotdae Duck · 공중 정찰", "하늘로 기원을 나르며 먼 곳을 살핍니다.", "마을 신앙 · 장대 위의 새", "1×1 공중", u_duck, 200),
    ("U-T6", "오방신장", "Obang Generals · 최종", "다섯 방위의 오라로 주변 아군을 지킵니다.", "무속 신격 · 오방을 지키는 장군신", "3×2", u_generals, 300),
]

BUILDINGS = [
    ("B-T1", "서낭당", "본진 · 반납", "3×3", b_seonang),
    ("B-T2", "약수터", "산기 채취", "2×2", b_spring),
    ("B-T3", "굿당", "기본 생산", "2×2", b_gutdang),
    ("B-T4", "저승문", "저승사자 생산", "2×2", b_gate),
    ("B-T5", "오방기단", "연구 · 신장 해금", "2×2", b_flagstand),
    ("B-T6", "해태상", "방어 · 불을 누른다는 속설", "1×1", b_haetae),
    ("B-T7", "누석단", "보급 +8", "1×1", b_cairn),
]


def page():
    units = "".join(unit_card(*u) for u in UNITS)
    blds = "".join(bld_card(*b) for b in BUILDINGS)
    strip = (strip_cell("공격 1 · 방울", "방울을 높이 듦", fx(1))
             + strip_cell("공격 2 · 울림", "소리가 퍼져 나감", fx(2))
             + strip_cell("공격 3 · 약화", "적의 힘이 빠짐", fx(3))
             + strip_cell("공격 4 · 치유", "아군에게 금빛 기운", fx(4))
             + strip_cell("유닛 사망", "혼이 잠시 머묾", fx_death())
             + strip_cell("건물 파괴", "굿당 차일 무너짐", fx_wreck())
             + strip_cell("채집", "나무망치로 귀화석", fx_harvest())
             + strip_cell("자원 반납", "누석단에 쌓음", fx_dropoff()))
    swatches = "".join(swatch(c, l) for c, l in [(OBANG["청"], "2F6E8E"), (OBANG["적"], "C8432B"), (OBANG["황"], "E2B13C"), (OBANG["백"], "EFE8D8"), (OBANG["흑"], "1F1D1A"), ("#C8A860", "C8A860"), ("#FBF6E6", "FBF6E6")])
    title_style = (f"margin: 0; {SERIF}; font-weight: 800; font-size: 46px; line-height: 1.05; "
                   "background: linear-gradient(180deg, #FFF4CC 0%, #F2D58A 35%, #C9902E 70%, #8A5A16 100%); "
                   "-webkit-background-clip: text; background-clip: text; color: transparent; "
                   "filter: drop-shadow(0 2px 0 #1A0E06) drop-shadow(0 0 1px #000)")
    defs_figs = ""
    # figures must be built before K.defs_svg() is read
    body = f"""
  <header style="display: flex; align-items: center; gap: 18px">
    {svg('0 0 100 100', emblem(), 84, 84, '오방 신당 문장')}
    <div style="display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0">
      <span style="{MONO}; font-size: 12px; letter-spacing: 0.14em; color: {MUTED}">RACE CODEX 天 · 삼재 SAMJAE · 6 UNITS · 7 STRUCTURES</span>
      <div style="display: flex; align-items: baseline; gap: 14px"><h1 style="{title_style}">오방 신당</h1><span style="{MONO}; font-size: 19px; font-weight: 600; letter-spacing: 0.2em; color: {OCHRE}">OBANG SHRINE</span></div>
      <span style="font-size: 13px; color: #D8CDB4">귀화석이 깨어나 땅이 어지러워지자, 마을을 지키던 신령들이 금줄을 치고 일어섭니다. 성역 안에서 강하고, 쓰러져도 혼이 잠시 머뭅니다.</span>
    </div>
    <div style="display: flex; flex-direction: column; gap: 8px; align-items: flex-end">
      <div style="display: flex; gap: 8px">{swatches}</div>
      <span style="font-size: 12px; color: {MUTED}">형태 언어 <b style="color: {OCHRE_L}">세로 기둥과 깃발 · 오방색 · 흰 한지 · 금줄</b></span>
    </div>
  </header>
  <div style="display: flex; height: 5px; box-shadow: 0 1px 0 #000">{"".join(f'<span style="flex: 1; background: {c}"></span>' for c in (OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#121110"))}</div>

  <section style="display: flex; flex-direction: column; gap: 6px">
    {section_title("유닛", "6종 · 45° 내려다본 2.5D · 쓰러져도 혼이 잠시 머묾 · 공통의 세로 실루엣")}
    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1.25fr 1fr 1.5fr; gap: 10px">{units}
    </div>
  </section>

  <section style="display: flex; flex-direction: column; gap: 6px">
    {section_title("건물", "7종 · 2:1 아이소메트릭 · 기와집 없음 · 금줄로 성역 경계 표시")}
    <div style="display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 10px">{blds}
    </div>
  </section>

  <section style="display: flex; flex-direction: column; gap: 6px">
    {section_title("연출", "무당 방울 공격 4프레임 · 사망 · 파괴 · 채집 · 반납")}
    <div style="display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 9px">{strip}
    </div>
  </section>
"""
    figs = "".join(f'<g id="{gid}">{m}</g>' for gid, m in FIG_DEFS)
    return f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>오방 신당 종족 시트</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
body{{margin:0;background:{GROUND}}}
a{{color:{OCHRE}}}a:hover{{color:{OCHRE_L}}}
</style>
</helmet>
<div style="position: relative; width: 1440px; height: 960px; box-sizing: border-box; background: radial-gradient(ellipse at 50% 0%, #2A2216 0%, {GROUND} 60%, #0E0C08 100%); color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; overflow: hidden; padding: 16px 40px 14px; display: flex; flex-direction: column; gap: 9px">

  <svg width="0" height="0" style="position: absolute; width: 0; height: 0" aria-hidden="true" focusable="false"><defs>{DEFS}<radialGradient id="r3-holy"><stop offset="0" stop-color="#FFF4C8" stop-opacity="0.95"></stop><stop offset="0.45" stop-color="#F2CF72" stop-opacity="0.4"></stop><stop offset="1" stop-color="#F2CF72" stop-opacity="0"></stop></radialGradient>{K.defs_svg()}{figs}</defs></svg>
{body}</div>
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


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(page())
    print("wrote", os.path.abspath(OUT), os.path.getsize(OUT))
