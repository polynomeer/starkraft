#!/usr/bin/env python3
"""Generate RaceR3.dc.html — 삼파전 race codex R3 (보부상 연단 / Kite Guild).

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


def dancheong(iso, x0, x1, y, z, h=0.12, rng=None):
    """Thin painted band under the eaves on the lit face."""
    out = poly(iso.face_l(x0, x1, y, z - h, z), GREEN)
    n = max(4, int((x1 - x0) * 6))
    cols = [RED, OCHRE, "#EFE8D8", BLUE]
    for i in range(n):
        a = x0 + (x1 - x0) * (i + 0.2) / n
        b = x0 + (x1 - x0) * (i + 0.8) / n
        out += poly(iso.face_l(a, b, y, z - h * 0.75, z - h * 0.25), cols[i % 4])
    return out


def giwa_roof(iso, x0, y0, x1, y1, ze, zr, ov=0.35, lift=0.22, hip=None, tiles=True, ends=True):
    """Hipped-gable (팔작) roof with ridge along x, tile rows and upturned eaves."""
    ym = (y0 + y1) / 2
    hip = (y1 - y0) / 2 + ov if hip is None else hip
    E00, E10 = iso.P(x0 - ov, y0 - ov, ze + lift), iso.P(x1 + ov, y0 - ov, ze + lift)
    E11, E01 = iso.P(x1 + ov, y1 + ov, ze + lift), iso.P(x0 - ov, y1 + ov, ze + lift)
    R0, R1 = iso.P(x0 - ov + hip, ym, zr), iso.P(x1 + ov - hip, ym, zr)
    sag = iso.s * lift * 1.6

    def mid(a, b):
        m = lerp(a, b, 0.5)
        return (m[0], m[1] + sag)

    out = ""
    # eave shadow on walls
    out += poly([iso.P(x0, y1, ze), iso.P(x1, y1, ze), iso.P(x1, y1, ze - 0.45), iso.P(x0, y1, ze - 0.45)], "#000", ' opacity="0.28"')
    out += poly([iso.P(x1, y1, ze), iso.P(x1, y0, ze), iso.P(x1, y0, ze - 0.5), iso.P(x1, y1, ze - 0.5)], "#000", ' opacity="0.3"')
    back = [E00, E10, R1, R0]
    out += poly(back, "url(#r3-roof-b)", ' filter="url(#r3-tx-stone)"')
    # right hip (shade)
    m = mid(E10, E11)
    out += (f'<path d="M{f(E10[0])} {f(E10[1])} Q{f(m[0])} {f(m[1])} {f(E11[0])} {f(E11[1])} L{f(R1[0])} {f(R1[1])} Z" fill="url(#r3-roof-r)"></path>')
    if tiles:
        rows = ""
        for k in range(1, 9):
            t = k / 9
            a = lerp(E10, E11, t)
            a = (a[0], a[1] + sag * 4 * t * (1 - t))
            rows += line(a, R1, "#0A0C0F", 0.7, 0.75) + line((a[0] - 0.9, a[1]), (R1[0] - 0.4, R1[1]), "#5A626E", 0.5, 0.5)
        out += clipped([E10, m, E11, R1], rows)
    # front slope (lit)
    m = mid(E01, E11)
    front = f'M{f(E01[0])} {f(E01[1])} Q{f(m[0])} {f(m[1])} {f(E11[0])} {f(E11[1])} L{f(R1[0])} {f(R1[1])} L{f(R0[0])} {f(R0[1])} Z'
    out += f'<path d="{front}" fill="url(#r3-roof-f)" filter="url(#r3-tx-stone)"></path>'
    if tiles:
        L = math.dist(E01, E11)
        n = max(8, int(L / 3.2))
        rows = ""
        ext0 = iso.P(x0 - ov, ym, zr)
        ext1 = iso.P(x1 + ov, ym, zr)
        for k in range(1, n):
            t = k / n
            a = lerp(E01, E11, t)
            a = (a[0], a[1] + sag * 4 * t * (1 - t))
            b = lerp(ext0, ext1, t)
            rows += line(a, b, "#14181E", 0.85, 0.85) + line((a[0] + 1.1, a[1]), (b[0] + 0.8, b[1]), "#B4BCC6", 0.55, 0.55)
        # course lines (horizontal tile overlaps)
        for k in range(1, 5):
            t = k / 5
            a = lerp(E01, R0, t * 0.92)
            b = lerp(E11, R1, t * 0.92)
            mm = lerp(a, b, 0.5)
            rows += (f'<path d="M{f(a[0])} {f(a[1])} Q{f(mm[0])} {f(mm[1] + sag * (1 - t) * 1.6)} {f(b[0])} {f(b[1])}" '
                     f'stroke="#20252C" stroke-width="0.4" fill="none" opacity="0.5"></path>')
        out += clipped([E01, m, E11, R1, R0], rows)
    # eave edge: end tiles (막새) + white fascia
    out += (f'<path d="M{f(E01[0])} {f(E01[1])} Q{f(m[0])} {f(m[1])} {f(E11[0])} {f(E11[1])}" stroke="#E8E0CC" stroke-width="0.9" fill="none" opacity="0.65"></path>')
    if ends:
        L = math.dist(E01, E11)
        n = max(8, int(L / 3.2))
        for k in range(n + 1):
            t = k / n
            a = lerp(E01, E11, t)
            a = (a[0], a[1] + sag * 4 * t * (1 - t))
            out += f'<circle cx="{f(a[0])}" cy="{f(a[1] + 0.6)}" r="0.95" fill="#3A414A" stroke="#14181E" stroke-width="0.3"></circle>'
    # hip ridges + main ridge (용마루) with white mortar line
    for a, b in ((R0, E01), (R1, E11), (R1, E10)):
        out += line(a, b, "#1A1E24", 2.2) + line((a[0], a[1] - 0.5), (b[0], b[1] - 0.5), "#8E96A2", 0.6, 0.7)
    out += line(R0, R1, "#14181E", 3.4) + line((R0[0], R0[1] + 1.4), (R1[0], R1[1] + 1.4), "#E8E2D4", 0.9, 0.85)
    out += line((R0[0], R0[1] - 0.9), (R1[0], R1[1] - 0.9), "#7A828E", 0.7, 0.8)
    # ridge-end ornaments (취두)
    for R, d in ((R0, -1), (R1, 1)):
        out += (f'<path d="M{f(R[0])} {f(R[1] + 1)} q{f(d * 2.4)} -1 {f(d * 3.2)} -5 q{f(-d * 0.6)} 1.8 {f(-d * 2.2)} 2" fill="#22272E" stroke="#0A0C0F" stroke-width="0.4"></path>')
    # upturned corner tips
    for E in (E01, E11, E10):
        out += f'<path d="M{f(E[0] - 1.5)} {f(E[1] + 0.6)} q1.5 -0.2 2.6 -2.6" stroke="#1A1E24" stroke-width="1.2" fill="none" stroke-linecap="round"></path>'
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

def onggi(cx, cy, r=5, lid=True, glaze="onggi"):
    out = shadow(cx + r * 0.5, cy + r * 0.5, r * 1.15, r * 0.45, 0.5)
    body = (f'<path d="M{f(cx - r * 0.55)} {f(cy - r * 1.5)} Q{f(cx - r * 1.15)} {f(cy - r * 1.0)} {f(cx - r * 0.85)} {f(cy - r * 0.15)} '
            f'Q{f(cx - r * 0.6)} {f(cy + r * 0.25)} {f(cx)} {f(cy + r * 0.25)} Q{f(cx + r * 0.6)} {f(cy + r * 0.25)} {f(cx + r * 0.85)} {f(cy - r * 0.15)} '
            f'Q{f(cx + r * 1.15)} {f(cy - r * 1.0)} {f(cx + r * 0.55)} {f(cy - r * 1.5)} Z" fill="url(#r3-{glaze})"></path>')
    body += f'<path d="M{f(cx - r * 0.95)} {f(cy - r * 0.8)} Q{f(cx)} {f(cy - r * 0.45)} {f(cx + r * 0.95)} {f(cy - r * 0.8)}" stroke="#D8A878" stroke-width="0.45" fill="none" opacity="0.55"></path>'
    body += f'<path d="M{f(cx - r * 0.9)} {f(cy - r * 0.55)} q{f(r * 0.3)} -0.8 {f(r * 0.6)} 0 t{f(r * 0.6)} 0 t{f(r * 0.6)} 0" stroke="#2A1608" stroke-width="0.35" fill="none" opacity="0.5"></path>'
    body += f'<ellipse cx="{f(cx)}" cy="{f(cy - r * 1.5)}" rx="{f(r * 0.58)}" ry="{f(r * 0.2)}" fill="#3A2210"></ellipse>'
    if lid:
        body += (f'<ellipse cx="{f(cx)}" cy="{f(cy - r * 1.62)}" rx="{f(r * 0.66)}" ry="{f(r * 0.24)}" fill="url(#r3-onggi)" stroke="#1A0E06" stroke-width="0.3"></ellipse>'
                 f'<circle cx="{f(cx)}" cy="{f(cy - r * 1.72)}" r="{f(r * 0.12)}" fill="#A8784E"></circle>')
    body += spec(cx - r * 0.45, cy - r * 1.0, r * 0.14, 0.65)
    return out + body


def cloth_bolt(cx, cy, l=9, r=2.2, col="indigo"):
    """A rolled bolt of cloth lying along the iso x axis (screen down-right)."""
    dx, dy = l * 0.89, l * 0.45
    out = shadow(cx + dx / 2 + 1, cy + dy / 2 + 1.5, l * 0.6, r * 0.8, 0.45)
    out += poly([(cx, cy - r), (cx + dx, cy + dy - r), (cx + dx, cy + dy + r), (cx, cy + r)], f"url(#r3-{col})", ' filter="url(#r3-tx-cloth)"')
    out += line((cx, cy - r * 0.5), (cx + dx, cy + dy - r * 0.5), "#FFF", 0.5, 0.35)
    out += f'<ellipse cx="{f(cx + dx)}" cy="{f(cy + dy)}" rx="{f(r * 0.6)}" ry="{f(r)}" fill="url(#r3-{col}-sh)"></ellipse>'
    out += f'<path d="M{f(cx + dx)} {f(cy + dy)} m-0.6 0 a0.6 1 0 1 1 1.2 0" stroke="#FFF" stroke-width="0.25" fill="none" opacity="0.5"></path>'
    out += line((cx + dx * 0.3, cy + dy * 0.3 - r), (cx + dx * 0.3, cy + dy * 0.3 + r), ROPE, 0.6)
    return out


def sack(cx, cy, w=7, h=6, col="hemp", glow_ore=False):
    out = shadow(cx + 1.5, cy + 1, w * 0.7, h * 0.3, 0.45)
    out += (f'<path d="M{f(cx - w / 2)} {f(cy)} Q{f(cx - w / 2 - 1)} {f(cy - h * 0.7)} {f(cx - w * 0.2)} {f(cy - h)} L{f(cx + w * 0.2)} {f(cy - h)} '
            f'Q{f(cx + w / 2 + 1)} {f(cy - h * 0.7)} {f(cx + w / 2)} {f(cy)} Q{f(cx)} {f(cy + 1.2)} {f(cx - w / 2)} {f(cy)} Z" fill="url(#r3-{col})" filter="url(#r3-tx-cloth)"></path>')
    out += f'<path d="M{f(cx - w * 0.25)} {f(cy - h)} q{f(w * 0.25)} -2 {f(w * 0.5)} 0" stroke="{ROPE}" stroke-width="0.8" fill="none"></path>'
    out += f'<path d="M{f(cx - w * 0.15)} {f(cy - h * 0.6)} q{f(-w * 0.1)} {f(h * 0.3)} 0 {f(h * 0.5)}" stroke="#000" stroke-width="0.35" fill="none" opacity="0.3"></path>'
    if glow_ore:
        out += glow(cx, cy - h - 1, 5, "oreglow", 0.9)
        out += ore_cluster(cx, cy - h + 0.5, 0.28, halo=False)
    return out


def dried_fish_rack(cx, cy, w=16, rng=None):
    """Wooden rack with strings of dried fish (굴비 엮음)."""
    out = line((cx - w / 2, cy), (cx - w / 2, cy - 14), "#5A3E24", 1.2) + line((cx + w / 2, cy + 3), (cx + w / 2, cy - 11), "#3A2818", 1.2)
    out += line((cx - w / 2 - 1, cy - 13.5), (cx + w / 2 + 1, cy - 10.5), "#7A5634", 1.1)
    for k in range(5):
        t = (k + 0.5) / 5
        x = cx - w / 2 + w * t
        y = cy - 13.3 + 3 * t
        out += line((x, y), (x, y + 9), ROPE, 0.4)
        for j in range(3):
            fy = y + 2 + j * 2.6
            out += (f'<path d="M{f(x - 0.9)} {f(fy)} q0.9 -0.8 1.8 0 l-0.3 1.8 l0.6 0.8 h-1.8 l0.6 -0.8 z" fill="#C8A060" stroke="#5A3E1C" stroke-width="0.25"></path>'
                    f'<circle cx="{f(x - 0.3)}" cy="{f(fy + 0.1)}" r="0.22" fill="#1A1008"></circle>')
    return out


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


# ---------------------------------------------------------------- figures (local coords: feet at 0,0; up is -y)

def paeraengi_hat(y=-74, cotton=True):
    """Bamboo 패랭이 with the peddlers' cotton tufts (side view, 3/4)."""
    weave = "".join(line((x, y - 0.4), (x * 0.45 + 0.6, y - 8.5), "#5A4620", 0.35, 0.55) for x in range(-12, 15, 2))
    out = (f'<ellipse cx="1" cy="{y + 0.6}" rx="14.5" ry="4.2" fill="#000" opacity="0.25" filter="url(#r3-blur1)"></ellipse>'
           f'<ellipse cx="1" cy="{y}" rx="14" ry="4" fill="url(#r3-hat)" stroke="#4E3C1A" stroke-width="0.5"></ellipse>'
           f'<path d="M-6 {y - 0.5} Q-5.5 {y - 9} 1 {y - 9.5} Q7.5 {y - 9} 8 {y - 0.5} Z" fill="url(#r3-hat)" stroke="#4E3C1A" stroke-width="0.5"></path>'
           + weave
           + f'<path d="M-6 {y - 2} Q1 {y - 0.4} 8 {y - 2}" stroke="#1E1A14" stroke-width="1.3" fill="none"></path>'
           + f'<path d="M-12 {y - 1} Q-4 {y - 3.2} 6 {y - 3.4}" stroke="#FFF6D8" stroke-width="0.6" fill="none" opacity="0.6"></path>')
    if cotton:
        for cx, cy in ((-6.5, y - 4), (8.5, y - 4)):
            out += "".join(f'<circle cx="{f(cx + dx)}" cy="{f(cy + dy)}" r="{r}" fill="url(#r3-cotton)"></circle>'
                           for dx, dy, r in ((0, 0, 2.2), (-1.2, -1.3, 1.5), (1.3, -1.1, 1.5), (0.2, -2.2, 1.3)))
    out += f'<path d="M-4 {y + 1} Q-3 {y + 9} 0 {y + 12} M7 {y + 1} Q6 {y + 8} 3 {y + 12}" stroke="#2A1E10" stroke-width="0.45" fill="none"></path>'
    return out


def jeonrip_hat(y=-74):
    out = (f'<ellipse cx="1" cy="{y + 0.6}" rx="15" ry="4.4" fill="#000" opacity="0.3" filter="url(#r3-blur1)"></ellipse>'
           f'<ellipse cx="1" cy="{y}" rx="14.5" ry="4.2" fill="url(#r3-felt)" stroke="#000" stroke-width="0.5"></ellipse>'
           f'<path d="M-6 {y - 0.4} Q-6 {y - 8.5} 1 {y - 9} Q8 {y - 8.5} 8 {y - 0.4} Z" fill="url(#r3-felt)" stroke="#000" stroke-width="0.5"></path>'
           f'<path d="M-6 {y - 2.2} Q1 {y - 0.6} 8 {y - 2.2}" stroke="{OCHRE}" stroke-width="0.9" fill="none"></path>'
           f'<path d="M-12 {y - 1} Q-4 {y - 3} 6 {y - 3.2}" stroke="#8A847A" stroke-width="0.6" fill="none" opacity="0.6"></path>'
           f'<circle cx="1" cy="{y - 9.4}" r="1.3" fill="url(#r3-brass)"></circle>')
    # red horsehair tassel (상모)
    for k in range(9):
        a = -2.6 + k * 0.28
        out += f'<path d="M1 {y - 9.5} q{f(math.cos(a) * 3)} -2 {f(math.cos(a) * 7)} {f(4 + abs(math.sin(a)) * 3)}" stroke="#E0503A" stroke-width="0.8" fill="none"></path>'
    out += "".join(f'<circle cx="{f(-4 + i * 0.4)}" cy="{f(y + 2 + i * 1.6)}" r="0.55" fill="{OCHRE_L if i % 2 else RED}"></circle>' for i in range(6))
    return out


def head(y=-66, beard=False, grin=False):
    out = (f'<rect x="-2.4" y="{y + 5}" width="5.2" height="5" fill="#B88058"></rect>'
           f'<ellipse cx="1" cy="{y}" rx="6.6" ry="7.6" fill="url(#r3-skin)"></ellipse>'
           f'<ellipse cx="-4.9" cy="{y + 0.6}" rx="1.4" ry="2" fill="#C88A60"></ellipse>'
           f'<path d="M-0.5 {y - 1.6} q1.6 -0.8 3 0 M3.8 {y - 1.6} q1.3 -0.6 2.6 0" stroke="#2A1A10" stroke-width="0.6" fill="none"></path>'
           f'<ellipse cx="1.2" cy="{y - 0.2}" rx="0.7" ry="0.5" fill="#1A1008"></ellipse><ellipse cx="5" cy="{y - 0.2}" rx="0.6" ry="0.45" fill="#1A1008"></ellipse>'
           f'<path d="M3.6 {y} q0.9 2 -0.2 2.8" stroke="#9A6440" stroke-width="0.5" fill="none"></path>'
           f'<ellipse cx="-0.6" cy="{y + 2.2}" rx="1.5" ry="0.9" fill="#E08A70" opacity="0.4"></ellipse>')
    if grin:
        out += f'<path d="M1 {y + 4} q2 1.4 4 0" stroke="#5A2A18" stroke-width="0.6" fill="#FFF8EC"></path>'
    else:
        out += f'<path d="M1.6 {y + 4.2} q1.6 0.6 3.2 0" stroke="#6A3A24" stroke-width="0.5" fill="none"></path>'
    if beard:
        out += (f'<path d="M-2 {y + 3.4} Q1 {y + 12} 5 {y + 3.6} Q4 {y + 7} 1.6 {y + 6} Q0 {y + 7} -2 {y + 3.4} Z" fill="#1E1610"></path>'
                f'<path d="M0.6 {y + 3.2} q1.4 -0.6 3.2 0 q-1.6 1 -3.2 0" fill="#1E1610"></path>')
    return out


def legs(trouser="hemp", wrap="cloth", stance=0):
    lx, rx = -6 - stance, 5 + stance
    out = (f'<path d="M-11 -33 Q-13 -18 {lx - 3} -5 L{lx + 3} -5 Q-2 -18 -0.5 -31 Z" fill="url(#r3-{trouser})" filter="url(#r3-tx-cloth)"></path>'
           f'<path d="M0.5 -31 Q3 -18 {rx - 2.5} -5 L{rx + 3.5} -4 Q11 -18 9 -33 Z" fill="url(#r3-{trouser}-sh)" filter="url(#r3-tx-cloth)"></path>')
    for x0 in (lx, rx):
        out += f'<path d="M{x0 - 3} -12 L{x0 + 3} -12 L{x0 + 3} -4 L{x0 - 3} -4 Z" fill="url(#r3-{wrap})"></path>'
        out += "".join(line((x0 - 3, -11 + k * 2.4), (x0 + 3, -10 + k * 2.4), "#6E654C", 0.35, 0.8) for k in range(3))
        # straw sandals (짚신)
        out += (f'<ellipse cx="{x0 + 0.8}" cy="-1.6" rx="4.6" ry="2" fill="#B49A5A" stroke="#5A4620" stroke-width="0.4"></ellipse>'
                f'<path d="M{x0 - 2.4} -2 l1.6 -1.6 M{x0} -2.2 l1.6 -1.6 M{x0 + 2.4} -2 l1.4 -1.4" stroke="#6A5428" stroke-width="0.45"></path>')
    return out


def torso(coat="hemp", sash=OCHRE, long_skirt=False, collar="#F6F0E0"):
    out = ""
    if long_skirt:
        out += (f'<path d="M-13 -36 L-17 -11 Q0 -7 17 -11 L13 -36 Z" fill="url(#r3-{coat})" filter="url(#r3-tx-cloth)"></path>'
                + "".join(line((x, -33), (x * 1.25, -10), "#000", 0.4, 0.25) for x in (-8, -4, 0, 4, 8)))
    out += (f'<path d="M-12 -59 Q-16 -46 -13.5 -31 L13.5 -31 Q16 -46 12 -59 Q0 -63 -12 -59 Z" fill="url(#r3-{coat})" filter="url(#r3-tx-cloth)"></path>'
            f'<path d="M3 -59 Q14 -55 13.5 -31 L7 -31 Q9 -46 3 -59 Z" fill="#000" opacity="0.18"></path>'
            f'<path d="M-4 -60 L7 -43 L9.5 -45 L1 -60.5 Z" fill="{collar}"></path>'
            f'<path d="M-4 -60 L7 -43" stroke="#6E654C" stroke-width="0.35"></path>'
            f'<path d="M-13 -36 Q0 -32.4 13 -36 L13.6 -32 Q0 -28.4 -13.6 -32 Z" fill="{sash}"></path>'
            f'<path d="M5 -33 q2 6 -1 10 M6.5 -33 q3 5 1 9" stroke="{sash}" stroke-width="1.4" fill="none"></path>'
            f'<path d="M-10 -57 Q-6 -60 0 -60.5" stroke="#FFF" stroke-width="0.8" fill="none" opacity="0.45"></path>')
    return out


def sleeve(side, coat="hemp", hx=None, hy=None):
    """side -1 = viewer-left arm, +1 = right arm; hand at (hx,hy)."""
    if side < 0:
        hx = -17 if hx is None else hx
        hy = -35 if hy is None else hy
        sh = (-11, -57)
    else:
        hx = 16 if hx is None else hx
        hy = -35 if hy is None else hy
        sh = (11, -57)
    el = ((sh[0] + hx) / 2 + side * 3.5, (sh[1] + hy) / 2)
    g = coat if side < 0 else f"{coat}-sh"
    out = (f'<path d="M{f(sh[0] - 3.5)} {f(sh[1])} Q{f(el[0] - 4)} {f(el[1])} {f(hx - 3)} {f(hy - 1)} L{f(hx + 3)} {f(hy - 1)} '
           f'Q{f(el[0] + 4)} {f(el[1])} {f(sh[0] + 3.5)} {f(sh[1] - 1)} Z" fill="url(#r3-{g})" filter="url(#r3-tx-cloth)"></path>'
           f'<path d="M{f(hx - 3)} {f(hy - 1)} L{f(hx + 3)} {f(hy - 1)}" stroke="#F6F0E0" stroke-width="1" opacity="0.8"></path>'
           f'<ellipse cx="{f(hx)}" cy="{f(hy + 1.4)}" rx="2.6" ry="2.4" fill="url(#r3-skin)"></ellipse>')
    return out


def jige_frame(load=True):
    """지게 behind the shoulders, with bundles, an ore sack and an onggi jar."""
    out = (line((-9, -64), (-12, -6), "url(#r3-pole)", 2.6) + line((9, -66), (12, -8), "url(#r3-pole)", 2.6)
           + line((-9, -64), (-9.5, -70), "#5A3E22", 2.2) + line((9, -66), (9.6, -72), "#5A3E22", 2.2))
    for y in (-58, -46, -34, -22):
        out += line((-9.5 - (y + 64) * 0.04, y), (9.5 + (y + 64) * 0.04, y - 1.5), "#4A3218", 1.4)
    if load:
        out += f'<g transform="translate(0 -6)">'
        out += cloth_bolt(-13, -73, 14, 3.2, "indigo")
        out += sack(-6, -60, 14, 12, "hemp")
        out += sack(7, -62, 11, 10, "ochre", glow_ore=True)
        out += onggi(-1, -77, 4.2)
        out += cloth_bolt(-10, -86, 10, 2.4, "red")
        out += (f'<path d="M-15 -80 Q0 -70 15 -82 M-15 -64 Q0 -55 15 -66" stroke="{ROPE}" stroke-width="0.9" fill="none"></path>'
                f'<path d="M-15 -80 Q0 -70 15 -82" stroke="#D8CBA0" stroke-width="0.35" stroke-dasharray="1 1" fill="none"></path>')
        out += "</g>"
    return out


def bell(cx, cy, r=1.8):
    return (f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="url(#r3-brass)" stroke="#4E3C1A" stroke-width="0.3"></circle>'
            f'<path d="M{f(cx - r * 0.6)} {f(cy + r * 0.25)} H{f(cx + r * 0.6)}" stroke="#4E3C1A" stroke-width="0.35"></path>'
            + spec(cx - r * 0.35, cy - r * 0.4, r * 0.25))


def u_porter_fig():
    body = (f'<g transform="translate(-9 2) rotate(-8)">{jige_frame()}</g>'
            + legs("hemp", "cloth", 1)
            + torso("hemp", INDIGO)
            + sleeve(-1, "hemp", -15, -40)
            # 지게 작대기 (forked prop stick)
            + line((19, -50), (25, 0), "url(#r3-pole)", 2.2) + line((19, -50), (16, -55), "#5A3E22", 1.6) + line((19, -50), (22, -55.5), "#5A3E22", 1.6)
            + sleeve(1, "hemp", 19, -40)
            + f'<path d="M-9 -58 Q-11 -48 -12 -36 M9 -58 Q11 -50 12 -38" stroke="{ROPE}" stroke-width="1.6" fill="none"></path>'
            + bell(-12, -30)
            + head(-66, beard=False, grin=True)
            + paeraengi_hat(-74))
    return body


def u_escort_fig():
    flag = (line((-10, -100), (-8, -40), "url(#r3-bamboo)", 1.6)
            + f'<path d="M-10 -100 L8 -94 L-9.6 -86 Z" fill="url(#r3-ochre)" filter="url(#r3-tx-cloth)"></path>'
            + f'<path d="M-10 -100 L8 -94" stroke="{RED}" stroke-width="1.2"></path>'
            + f'<circle cx="-4" cy="-93" r="2.2" fill="{INDIGO}"></circle><circle cx="-4" cy="-93" r="0.9" fill="{CLOTH}"></circle>')
    sword = (f'<path d="M20 -44 L35 -78 L37.4 -77 L23 -42.5 Z" fill="url(#r3-steel)" stroke="#2E2E2C" stroke-width="0.35"></path>'
             + line((22, -45), (36, -77), "#FFF", 0.45, 0.85)
             + f'<ellipse cx="21" cy="-42.5" rx="3.6" ry="1.6" fill="url(#r3-brass)" transform="rotate(-25 21 -42.5)"></ellipse>'
             + line((20.5, -41.5), (17.5, -34), "#2A1810", 2.4)
             + f'<path d="M17.5 -34 q-1 4 1.5 6" stroke="{RED}" stroke-width="0.9" fill="none"></path>')
    scab = (line((-12, -34), (-24, -6), "#1A1210", 3.2)
            + line((-12, -34), (-24, -6), RED, 0.9, 0.8)
            + f'<circle cx="-16" cy="-25" r="1.2" fill="url(#r3-brass)"></circle><circle cx="-20" cy="-16" r="1.2" fill="url(#r3-brass)"></circle>')
    vest = (f'<path d="M-12 -58 L-13 -34 L13 -34 L12 -58 Q0 -61 -12 -58 Z" fill="url(#r3-red)" opacity="0.92" filter="url(#r3-tx-cloth)"></path>'
            + "".join(f'<circle cx="{f(x)}" cy="{f(y)}" r="0.6" fill="url(#r3-brass)"></circle>' for x in range(-10, 12, 4) for y in (-54, -47, -40)))
    return (flag + scab + legs("indigo", "cloth", 2) + torso("indigo", RED, long_skirt=True) + vest
            + sleeve(-1, "indigo", -16, -38) + sword + sleeve(1, "indigo", 19, -42)
            + head(-66, beard=True) + jeonrip_hat(-74))


def shield_kite(cx, cy, w, h, rot=0, tails=False, op=1):
    """방패연: bamboo spars, centre hole, 꼭지 taegeuk patch, coloured skirt corners."""
    x, y = cx - w / 2, cy - h / 2
    r = w * 0.2
    hy = cy + h * 0.04
    k = w * 0.13
    sp = max(0.35, w * 0.022)
    t = ""
    if tails:
        t = (f'<path d="M{f(x + 1)} {f(y + h)} q-4 14 4 26 t2 24" stroke="url(#r3-ochre)" stroke-width="{f(w * 0.09)}" fill="none" stroke-linecap="round"></path>'
             f'<path d="M{f(x + w - 1)} {f(y + h)} q6 12 -2 24 t4 24" stroke="url(#r3-indigo)" stroke-width="{f(w * 0.09)}" fill="none" stroke-linecap="round"></path>'
             f'<path d="M{f(cx)} {f(y + h)} q-2 10 1 18" stroke="{RED}" stroke-width="{f(w * 0.05)}" fill="none" stroke-linecap="round"></path>')
    body = (t
            + f'<rect x="{f(x + 1.4)}" y="{f(y + 2)}" width="{f(w)}" height="{f(h)}" fill="#000" opacity="0.25" filter="url(#r3-blur1)"></rect>'
            + f'<rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="{f(h)}" fill="url(#r3-cloth)" filter="url(#r3-tx-paper)"></rect>'
            + f'<polygon points="{f(x)},{f(y + h)} {f(x)},{f(y + h * 0.7)} {f(x + w * 0.34)},{f(y + h)}" fill="url(#r3-indigo)"></polygon>'
            + f'<polygon points="{f(x + w)},{f(y + h)} {f(x + w)},{f(y + h * 0.7)} {f(x + w * 0.66)},{f(y + h)}" fill="url(#r3-indigo)"></polygon>'
            + f'<rect x="{f(x)}" y="{f(y + h * 0.86)}" width="{f(w)}" height="{f(h * 0.14)}" fill="url(#r3-ochre)" opacity="0.9"></rect>'
            + f'<path d="M{f(cx - k)} {f(y + h * 0.13)} A{f(k)} {f(k)} 0 0 1 {f(cx + k)} {f(y + h * 0.13)} Z" fill="{RED}"></path>'
            + f'<path d="M{f(cx - k)} {f(y + h * 0.13)} A{f(k)} {f(k)} 0 0 0 {f(cx + k)} {f(y + h * 0.13)} Z" fill="{BLUE}"></path>'
            + f'<path d="M{f(cx - k)} {f(y + h * 0.13)} a{f(k / 2)} {f(k / 2)} 0 0 1 {f(k)} 0 a{f(k / 2)} {f(k / 2)} 0 0 0 {f(k)} 0" fill="{RED}"></path>'
            + f'<circle cx="{f(cx)}" cy="{f(hy)}" r="{f(r)}" fill="url(#r3-hole)" opacity="0.94"></circle>'
            + f'<circle cx="{f(cx)}" cy="{f(hy)}" r="{f(r)}" fill="none" stroke="#7A6440" stroke-width="{f(sp)}"></circle>'
            + f'<path d="M{f(cx)} {f(y)} V{f(hy - r)} M{f(cx)} {f(hy + r)} V{f(y + h)} M{f(x)} {f(y + sp)} H{f(x + w)} M{f(x)} {f(hy)} H{f(cx - r)} M{f(cx + r)} {f(hy)} H{f(x + w)} '
              f'M{f(x)} {f(y)} L{f(cx - r * 0.7)} {f(hy - r * 0.7)} M{f(x + w)} {f(y)} L{f(cx + r * 0.7)} {f(hy - r * 0.7)} '
              f'M{f(cx - r * 0.7)} {f(hy + r * 0.7)} L{f(x)} {f(y + h)} M{f(cx + r * 0.7)} {f(hy + r * 0.7)} L{f(x + w)} {f(y + h)}" '
              f'stroke="#8A6A38" stroke-width="{f(sp)}" fill="none"></path>'
            + f'<path d="M{f(x + 0.5)} {f(y + h - 1)} V{f(y + 0.5)} H{f(x + w - 1)}" stroke="#FFF" stroke-width="0.6" fill="none" opacity="0.6"></path>'
            + f'<path d="M{f(x + w - 0.3)} {f(y + 1)} V{f(y + h - 0.3)} H{f(x + 1)}" stroke="#2A2010" stroke-width="0.7" fill="none" opacity="0.5"></path>')
    # bridle (목줄) converging below
    bx, by = cx + w * 0.05, cy + h * 0.55
    bridle = "".join(line(p, (bx, by), "#E8DDB8", 0.35, 0.8) for p in ((x + w * 0.2, y + h * 0.15), (x + w * 0.8, y + h * 0.15), (cx, hy + r)))
    return f'<g transform="rotate({rot} {f(cx)} {f(cy)})" opacity="{op}">{body}{bridle}</g>', (bx, by)


def bow_and_arrow(lit=True):
    bow = (f'<path d="M14 -70 Q22 -62 20 -50 Q26 -44 21 -34 Q22 -26 14 -18" stroke="#3A2414" stroke-width="2.2" fill="none" stroke-linecap="round"></path>'
           f'<path d="M14 -70 Q22 -62 20 -50 Q26 -44 21 -34 Q22 -26 14 -18" stroke="{RED}" stroke-width="0.7" fill="none" stroke-dasharray="3 5"></path>'
           f'<path d="M14 -70 L6 -45 L14 -18" stroke="#E8DDB8" stroke-width="0.45" fill="none"></path>')
    arrow = line((6, -45), (34, -52), "#B08C5C", 0.9) + f'<path d="M34 -52 l3 -1.6 l-2.2 2.6 z" fill="#C8C8C0"></path>'
    arrow += f'<path d="M6 -45 l-2 -1.6 m2 1.6 l-2 1.4" stroke="#F6F0E0" stroke-width="0.8"></path>'
    if lit:
        arrow += glow(34, -53, 6, "fireglow", 1) + f'<ellipse cx="33.6" cy="-53.4" rx="2.6" ry="1.6" fill="url(#r3-fire)" filter="url(#r3-flame)"></ellipse>'
    return bow + arrow


def u_archer_fig():
    quiver = (f'<path d="M-14 -62 L-6 -64 L-2 -36 L-9 -34 Z" fill="url(#r3-wood)" filter="url(#r3-tx-wood-v)"></path>'
              + "".join(line((-12 + i * 1.6, -62 - i * 0.3), (-13 + i * 1.6, -72 - i), "#B08C5C", 0.6) for i in range(5))
              + "".join(f'<path d="M{f(-13 + i * 1.6)} {f(-72 - i)} l-1 -2.6 l2 0 z" fill="{RED if i % 2 else CLOTH}"></path>' for i in range(5)))
    tether = f'<path d="M-6 -33 Q-30 -60 -26 -96" stroke="#E8DDB8" stroke-width="0.5" fill="none" opacity="0.9"></path>'
    reel = (f'<ellipse cx="-7" cy="-31" rx="3.4" ry="2.2" fill="url(#r3-wood)"></ellipse>'
            f'<ellipse cx="-7" cy="-31" rx="1.4" ry="0.9" fill="#2A1A10"></ellipse>')
    return (quiver + tether + legs("hemp", "cloth", 2) + torso("cloth", INDIGO) + reel
            + sleeve(-1, "cloth", -10, -46) + bow_and_arrow() + sleeve(1, "cloth", 14, -46)
            + f'<rect x="10" y="-50" width="5" height="6" rx="1" fill="url(#r3-indigo)"></rect>'
            + head(-66) + paeraengi_hat(-74))


def fig(x, y, s, body, sh=(16, 5)):
    return (shadow(x + 6 * s, y + 1.5 * s, sh[0] * s, sh[1] * s, 0.55)
            + f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">{sprite(body)}</g>')


# ---------------------------------------------------------------- kit figures (pre-rendered sprite look; ids r3k-)
# Local coords: feet at (0,0), up is -y, ~145 tall with hat (6.5 heads). Facing 3/4 toward screen-right.
# Far-side limbs (screen-right) are drawn behind the torso with Mat.back().

from figure_kit import Kit, Mat, MATS, mix as kmix  # noqa: E402

K = Kit("r3k")
KM = dict(MATS)
KM["hemp"] = Mat("#CFC4A6", "#F6EFDC", "#4E4532", "#8E96A0", spec_op=0.2, gloss=0.15)
KM["sailcloth"] = Mat("#D8CFB0", "#FBF4DE", "#544A34", "#8A92A0", spec_op=0.2, gloss=0.15)
KM["ind"] = Mat("#34477E", "#6F84C4", "#0B1026", "#4A5C88", spec_op=0.22, gloss=0.22)
KM["red"] = Mat("#B23C26", "#E87A52", "#360B05", "#7A3A44", spec_op=0.2, gloss=0.22)
KM["ochre"] = Mat("#C8962E", "#F4CF72", "#3A2608", "#8A7048", spec_op=0.22, gloss=0.25)
KM["bamboo"] = Mat("#B89A50", "#EED89A", "#3A2A0C", "#7A7458", spec_op=0.45, gloss=0.5)
KM["steel"] = Mat("#7A808A", "#D8DEE8", "#14161A", "#5A7088", spec="#FFFFFF", spec_op=0.95, gloss=1.0)


def _pathc(d, gid, outline=True, extra=""):
    o = f' stroke="{OUTLINE}" stroke-width="0.8" stroke-opacity="0.7" stroke-linejoin="round"' if outline else ""
    return f'<path d="{d}" fill="url(#{gid})"{o}{extra}></path>'


def kpanel(pts_, m, folds=4, n=(-1.0, -0.12), fold_from=0.0):
    """A shaded cloth panel (coat skirt, flap) with vertical folds: crease + ridge per fold."""
    d = K._smooth(pts_)
    xs = [p[0] for p in pts_]
    ys = [p[1] for p in pts_]
    c = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    gid = K._cyl_grad(c, n, (max(xs) - min(xs)) / 2 + 1, m)
    out = [_pathc(d, gid)]
    inner = ""
    top, bot = min(ys), max(ys)
    for i in range(folds):
        t = (i + 0.7) / (folds + 0.4)
        x0 = min(xs) + (max(xs) - min(xs)) * t
        y0 = top + (bot - top) * fold_from
        inner += (f'<path d="M{f(x0)} {f(y0)} Q{f(x0 + 1.5)} {f((y0 + bot) / 2)} {f(x0 + 2.6)} {f(bot + 2)}" fill="none" stroke="{m.core}" '
                  f'stroke-width="1.6" stroke-opacity="0.55" filter="url(#r3k-soft)"></path>'
                  f'<path d="M{f(x0 - 1.6)} {f(y0 + 3)} Q{f(x0 - 0.4)} {f((y0 + bot) / 2)} {f(x0 + 0.6)} {f(bot)}" fill="none" stroke="{m.light}" '
                  f'stroke-width="0.8" stroke-opacity="0.5"></path>')
    # waist AO band
    inner += f'<rect x="{f(min(xs) - 2)}" y="{f(top - 2)}" width="{f(max(xs) - min(xs) + 4)}" height="5" fill="#0A0604" opacity="0.35" filter="url(#r3k-soft)"></rect>'
    out.append(K.clip_overlay(d, inner))
    return "".join(out), d


# Per-unit-type face designs (SPEC_FACE): each type has its own base look; repeated instances vary by seed.
LOOKS = {
    "porter": ("skin_tan", dict(shape="round", eye="narrow", brow="thick", nose="flat", mouth="grin", age=0.45, beard="stubble",
                                cheek=0.6, beard_col="#2A1E16")),
    "escort": ("skin_weathered", dict(shape="square", jaw=1.08, eye="sharp", brow="angry", nose="straight", mouth="stern", age=0.4,
                                      beard="full", beard_col="#14100C", cheek=0.75, scar="brow")),
    "archer": ("skin_fair", dict(shape="heart", eye="almond", brow="straight", nose="small", mouth="calm", age=0.1, cheek=0.35,
                                 gaze=(0.8, 0.0))),
    "helm": ("skin_ruddy", dict(shape="long", eye="droopy", brow="worried", nose="hooked", mouth="neutral", age=0.7,
                                beard="thin_long", beard_col="#6A6258", cheek=0.8)),
    "lookout": ("skin_tan", dict(shape="diamond", eye="round", brow="arched", nose="broad", mouth="smile", age=0.15, cheek=0.5, lid="double")),
    "scout": ("skin_fair", dict(shape="oval", eye="round", brow="thin", nose="small", mouth="grin", age=0.0, cheek=0.3, lid="double")),
}
_FACE_RNG = random.Random(3303)


def look_for(kind, vary=True):
    mat, base = LOOKS[kind]
    lk = dict(base)
    if vary:  # deterministic per-instance variation so squads are not clones
        r = _FACE_RNG
        lk["jaw"] = lk.get("jaw", 1.0) * (0.94 + r.random() * 0.12)
        lk["age"] = max(0.0, min(1.0, lk.get("age", 0.25) + (r.random() - 0.5) * 0.2))
        lk["nose_len"] = 0.92 + r.random() * 0.16
        lk["eye_size"] = 0.92 + r.random() * 0.14
    return KM[mat] if mat in KM else MATS[mat], lk


def khead(cx, cy, r=10.0, turn=0.25, beard=False, grin=False, m=None, kind="porter", vary=True):
    """Shaded head with an individually designed face (K.face) + hair cap clipped to the skull."""
    m0, lk = look_for(kind, vary)
    m = m or m0
    lk["turn"] = turn
    h, d = K.face(cx, cy, r, m, lk)
    hair = KM["hair"]
    gid = K._sphere_grad(cx - r * 0.3, cy - r * 0.4, r, hair)
    # hair cap on the back/top of the skull (clipped to the head)
    cap = (f'<path d="M{f(cx - r * 1.2)} {f(cy - r * 1.2)} L{f(cx + r * 1.2)} {f(cy - r * 1.2)} L{f(cx + r * 0.9)} {f(cy - r * 0.48)} '
           f'Q{f(cx + r * 0.1)} {f(cy - r * 0.7)} {f(cx - r * 0.5)} {f(cy - r * 0.4)} L{f(cx - r * 0.66)} {f(cy + r * 0.4)} L{f(cx - r * 1.2)} {f(cy + r * 0.5)} Z" fill="url(#{gid})"></path>')
    return h + K.clip_overlay(d, cap)


def brim_ao(cx, y, rx=11.0):
    """Shadow the hat brim throws on the face (eyes, cheekbones)."""
    return K.ao(cx + 1.5, y + 4.2, rx, 3.2, 0.62)


def k_paeraengi(cx, y, cotton=True):
    """패랭이: bamboo-strip hat with the peddlers' cotton tufts. y = brim level."""
    st = KM["bamboo"]
    out = K.ao(cx + 2, y + 2.2, 19, 4.6, 0.5)
    out += K.ball(cx, y, 18.5, st, ry=5.6)
    # brim underside rim + strip lines on the brim
    out += f'<path d="M{f(cx - 18)} {f(y + 0.8)} Q{f(cx)} {f(y + 7.2)} {f(cx + 18)} {f(y + 0.8)}" fill="none" stroke="#2A1E0A" stroke-width="1.3" opacity="0.8"></path>'
    out += "".join(f'<path d="M{f(cx + math.cos(a) * 9)} {f(y + math.sin(a) * 2.8)} L{f(cx + math.cos(a) * 18)} {f(y + math.sin(a) * 5.5)}" '
                   f'stroke="#5A4420" stroke-width="0.4" opacity="0.55"></path>' for a in [i * math.tau / 28 for i in range(28)])
    # crown
    out += K.form((cx + 0.5, y - 1), (cx + 0.5, y - 11.5), 9.0, 7.0, st, ao_start=False)
    out += "".join(f'<path d="M{f(cx + dx)} {f(y - 1.4)} L{f(cx + dx * 0.8 + 0.5)} {f(y - 13)}" stroke="#4A3618" stroke-width="0.45" opacity="0.5"></path>'
                   for dx in (-7, -4.5, -2, 0.5, 3, 5.5, 8))
    # black hat band
    out += f'<path d="M{f(cx - 8.6)} {f(y - 3.4)} Q{f(cx + 0.5)} {f(y - 0.6)} {f(cx + 9.6)} {f(y - 3.4)}" stroke="#14100C" stroke-width="2.2" fill="none"></path>'
    out += f'<path d="M{f(cx - 8.4)} {f(y - 4.5)} Q{f(cx - 3)} {f(y - 3.2)} {f(cx + 1)} {f(y - 3)}" stroke="#7A6A5A" stroke-width="0.5" fill="none"></path>'
    if cotton:
        cm = KM["white_hemp"]
        for bx, by in ((cx - 9.4, y - 6), (cx + 10.2, y - 6.4)):
            for dx, dy, r in ((0, 0, 2.8), (-1.6, -1.8, 2.1), (1.7, -1.6, 2.0), (0.1, -3.2, 1.8)):
                out += K.ball(bx + dx, by + dy, r, cm, outline=True)
    # chin cord
    out += f'<path d="M{f(cx - 6)} {f(y + 2)} Q{f(cx - 5)} {f(y + 14)} {f(cx + 1)} {f(y + 18)} M{f(cx + 8)} {f(y + 2)} Q{f(cx + 7)} {f(y + 12)} {f(cx + 3)} {f(y + 18)}" stroke="#2A1E10" stroke-width="0.6" fill="none"></path>'
    return out


def k_satgat(cx, y):
    """삿갓: wide conical woven-bamboo hat. y = rim level."""
    st = KM["bamboo"]
    out = K.ao(cx + 2, y + 3, 22, 5, 0.55)
    rim = (f"M{f(cx - 24)} {f(y)} Q{f(cx)} {f(y + 9)} {f(cx + 24)} {f(y)} L{f(cx + 1)} {f(y - 19)} Z")
    gid = K._cyl_grad((cx, y - 8), (-1, -0.25), 24, st)
    out += _pathc(rim, gid)
    # weave: radial ribs + concentric rings, clipped
    inner = "".join(f'<path d="M{f(cx + 1)} {f(y - 19)} L{f(cx - 24 + i * 3)} {f(y + 4.5 * math.sin(math.pi * i / 16))}" stroke="#4A3618" stroke-width="0.45" opacity="0.6"></path>'
                    for i in range(17))
    for k in range(1, 5):
        t = k / 5
        inner += (f'<path d="M{f(cx + 1 - 25 * t)} {f(y - 19 + 19 * t)} Q{f(cx + 1)} {f(y - 19 + 19 * t + 9 * t)} {f(cx + 1 + 23 * t)} {f(y - 19 + 19 * t)}" '
                  f'stroke="#3A2A10" stroke-width="0.7" fill="none" opacity="0.55"></path>'
                  f'<path d="M{f(cx + 1 - 25 * t)} {f(y - 19.8 + 19 * t)} Q{f(cx + 1)} {f(y - 19.8 + 19 * t + 9 * t)} {f(cx + 1 + 23 * t)} {f(y - 19.8 + 19 * t)}" '
                  f'stroke="#FFF0C0" stroke-width="0.4" fill="none" opacity="0.45"></path>')
    out += K.clip_overlay(rim, inner)
    out += f'<path d="M{f(cx - 24)} {f(y)} Q{f(cx)} {f(y + 9)} {f(cx + 24)} {f(y)}" stroke="#24180A" stroke-width="1.6" fill="none"></path>'
    out += K.ball(cx + 1, y - 19, 1.6, KM["brass"])
    out += f'<path d="M{f(cx - 7)} {f(y + 4)} Q{f(cx - 5)} {f(y + 15)} {f(cx + 1)} {f(y + 18)} M{f(cx + 9)} {f(y + 4)} Q{f(cx + 8)} {f(y + 13)} {f(cx + 3)} {f(y + 18)}" stroke="#E8DDB8" stroke-width="0.7" fill="none"></path>'
    return out


def k_jipsin(ankle, s, d=1, back=False):
    """짚신 straw sandal straps over the kit foot."""
    x, y = ankle
    col = "#6A5428" if not back else "#4A3A1C"
    out = ""
    for k in range(3):
        t = 0.2 + k * 0.32
        out += (f'<path d="M{f(x - s * 0.5 + d * s * t * 1.6)} {f(y + s * 0.05)} q{f(d * s * 0.3)} {f(s * 0.45)} {f(d * s * 0.15)} {f(s * 0.95)}" '
                f'stroke="{col}" stroke-width="{f(s * 0.16)}" fill="none" stroke-linecap="round"></path>')
    out += f'<path d="M{f(x - s * 0.7)} {f(y + s * 1.0)} L{f(x + d * s * 1.8)} {f(y + s * 0.95)}" stroke="#C8AA6A" stroke-width="{f(s * 0.3)}" stroke-linecap="round" opacity="0.9"></path>'
    out += f'<path d="M{f(x - s * 0.7)} {f(y - s * 0.1)} q{f(s * 0.7)} {f(s * 0.3)} {f(s * 1.4)} 0" stroke="{col}" stroke-width="{f(s * 0.18)}" fill="none"></path>'
    return out


def k_leg(hip, knee, ankle, trouser, d=1, back=False, w=7.0):
    out = K.leg(hip, knee, ankle, trouser, w=w, trouser=trouser, wrap=KM["white_hemp"], boot=KM["straw"], foot_dir=d, back=back)
    return out + k_jipsin(ankle, w * 0.8, d, back)


def k_arm(sh, el, wr, coat, hand="fist", hand_dir=None, back=False, w=6.4, cuff=None):
    return K.arm(sh, el, wr, coat, w=w, sleeve=coat, cuff=cuff, hand=hand, hand_dir=hand_dir, hand_m=KM["skin"], back=back)


def k_torso(coat, sash, neck=(0, -113), waist=(0, -69), sw=14.5, ww=12.0, collar=True):
    t, d = K.torso(neck, waist, sw, ww, coat)
    out = t
    inner = ""
    if collar:
        # 깃 + 동정 (crossed white collar) and 고름 ribbon
        inner += (f'<path d="M-6 -116 L9 -88 L12.5 -90 L-1.5 -117 Z" fill="{kmix(coat.core, coat.base, 0.5)}"></path>'
                  f'<path d="M-6.5 -116 L8 -90" stroke="#F4EEDC" stroke-width="2.6"></path>'
                  f'<path d="M-6.5 -116 L8 -90" stroke="#9A9078" stroke-width="0.5" transform="translate(1.1 0.2)"></path>'
                  f'<path d="M6 -116 L-2 -102" stroke="#F4EEDC" stroke-width="2.2" opacity="0.9"></path>')
        # wrinkles radiating from the armpits and sash
        for s_ in (-1, 1):
            inner += (f'<path d="M{f(s_ * 13)} {f(-104)} q{f(-s_ * 5)} 6 {f(-s_ * 7)} 14" stroke="{coat.core}" stroke-width="1.4" fill="none" opacity="0.5" filter="url(#r3k-soft)"></path>'
                      f'<path d="M{f(s_ * 11)} {f(-80)} q{f(-s_ * 3)} -4 {f(-s_ * 8)} -5" stroke="{coat.core}" stroke-width="1.2" fill="none" opacity="0.45" filter="url(#r3k-soft)"></path>')
        inner += f'<path d="M-11 -100 q3 6 2 14" stroke="{coat.light}" stroke-width="1" fill="none" opacity="0.55"></path>'
    out += K.clip_overlay(d, inner)
    if collar:
        out += K.ball(9.5, -90, 1.8, KM["red"])
        out += K.form((9.5, -89.5), (11.5, -79), 1.3, 1.0, KM["red"], ao_start=False, spec=False)
        out += K.form((9.8, -89.5), (14, -81), 1.2, 0.9, KM["red"], ao_start=False, spec=False)
    # sash (띠) with knot and hanging ends
    out += K.ao(0, -66, 14, 2.6, 0.5)
    out += K.form((-13, -70.5), (13, -69), 3.0, 3.0, sash, ao_start=False)
    out += K.ball(-8, -70, 3.0, sash)
    out += K.form((-8, -68), (-11, -56), 1.8, 1.4, sash, ao_start=True, spec=False)
    out += K.form((-7.5, -68), (-6, -57), 1.6, 1.3, sash, ao_start=True, spec=False)
    return out, d


def k_neck(m=None):
    m = m or KM["skin"]
    return K.neck((0.8, -112), (0.8, -120), 4.2, m) + K.ao(2, -114, 6, 2.4, 0.45)


def k_shadow(s=1.0, rx=24, ry=6.5):
    return K.cast_shadow(0, 0, rx * s, ry * s, 0.6)


def k_place(x, y, s, body, sh=(24, 6.5)):
    """Place a kit figure: ground shadow (unscaled outside) + scaled body."""
    return (K.cast_shadow(x, y, sh[0] * s, sh[1] * s, 0.6)
            + f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">{body}</g>')


# --- props drawn with kit lighting -------------------------------------------

def k_jige(load=True):
    """지게 (A-frame back carrier) with load. Drawn behind the body."""
    w = KM["wood"]
    out = ""
    out += K.form((-12, -132), (-16, -10), 2.6, 2.4, w.back(0.15), ao_start=False)
    out += K.form((12, -136), (18, -14), 2.6, 2.4, w.back(0.3), ao_start=False)
    for y0 in (-118, -98, -76, -54):
        out += K.form((-12.5 - (y0 + 130) * 0.03, y0), (12.5 + (y0 + 130) * 0.05, y0 - 2), 1.6, 1.6, w.back(0.25), ao_start=False, spec=False)
    # prongs (가지) sticking back
    out += K.form((-14, -40), (-24, -44), 1.8, 1.4, w.back(0.2), ao_start=False)
    if load:
        out += f'<g transform="translate(-20 -56) scale(1.12)">'
        out += cloth_bolt(-9, -73, 14, 3.2, "indigo")
        out += sack(-3, -60, 14, 12, "hemp")
        out += sack(9, -62, 11, 10, "ochre", glow_ore=True)
        out += onggi(2, -77, 4.2)
        out += cloth_bolt(-6, -86, 10, 2.4, "red")
        out += (f'<path d="M-11 -80 Q4 -70 19 -82 M-11 -64 Q4 -55 19 -66" stroke="{ROPE}" stroke-width="0.9" fill="none"></path>'
                f'<path d="M-11 -80 Q4 -70 19 -82" stroke="#D8CBA0" stroke-width="0.35" stroke-dasharray="1 1" fill="none"></path>')
        out += "</g>"
    return out


def k_straps():
    """멜빵: straw-rope shoulder straps over the chest, with twist bands."""
    st = KM["straw"]
    out = ""
    for a, b in (((-9, -114), (-12, -82)), ((10, -114), (13, -84))):
        out += K.form(a, b, 1.8, 1.7, st, ao_start=True)
        out += K.bands(a, b, 1.8, 1.7, st, n=6)
    return out


def k_bell(cx, cy, r=2.4):
    return K.ball(cx, cy, r, KM["brass"]) + f'<path d="M{f(cx - r * 0.6)} {f(cy + r * 0.3)} H{f(cx + r * 0.6)}" stroke="#3A2408" stroke-width="0.5"></path>'


# --- units ------------------------------------------------------------------

def porter_body(pose="walk"):
    """짐꾼: walking under a loaded 지게; near hand grips the strap, far hand the 작대기 prop stick.
    pose: walk | swing (pick raised at a rock) | unload (bending to set the frame down)."""
    hemp, ind = KM["hemp"], KM["ind"]
    out = ""
    if pose == "swing":
        out += f'<g transform="rotate(-6 0 -70)">{k_jige(True)}</g>'
    else:
        out += f'<g transform="rotate(-5 0 -70)">{k_jige(True)}</g>'
    # far leg (stepping back) + far arm behind torso
    out += k_leg((4, -66), (0, -38), (-8, -8), hemp, d=1, back=True)
    if pose == "walk":
        stick = K.form((30, -100), (36, -2), 1.9, 1.7, KM["wood"], ao_start=False)
        stick += K.form((30, -100), (26, -110), 1.4, 1.2, KM["wood"], ao_start=False) + K.form((30, -100), (33, -111), 1.4, 1.2, KM["wood"], ao_start=False)
        out += stick
        out += k_arm((13, -108), (23, -94), (30, -84), hemp, hand="fist", hand_dir=(0, 1), back=True)
    elif pose == "unload":
        out += k_arm((13, -108), (20, -88), (24, -72), hemp, hand="fist", hand_dir=(0.3, 1), back=True)
    # near leg (forward)
    out += k_leg((-4, -66), (6, -38), (10, -7), hemp, d=1)
    t, d = k_torso(hemp, ind)
    out += t
    out += k_straps()
    out += k_bell(-12, -60)
    out += k_neck()
    out += khead(0.8, -127, 10, turn=0.3, kind="porter")
    out += brim_ao(0.8, -136)
    out += k_paeraengi(0.8, -136)
    if pose == "walk":
        out += k_arm((-13, -108), (-17, -88), (-11, -80), hemp, hand="fist", hand_dir=(0.3, -1))
    elif pose == "swing":
        # both hands on the pick haft raised overhead to the right
        out += K.form((-4, -96), (40, -132), 1.9, 1.8, KM["wood"], ao_start=False)
        out += (f'<path d="M30 -146 Q44 -142 52 -122 L48.5 -121 Q42 -136 29 -141 Z" fill="url(#{K._cyl_grad((40, -134), (-0.7, -0.7), 6, KM["steel"])})" '
                f'stroke="{OUTLINE}" stroke-width="0.8"></path>')
        out += k_arm((13, -108), (14, -122), (16, -112), hemp, hand="fist", hand_dir=(1, -0.8), back=True)
        out += k_arm((-13, -108), (-12, -94), (2, -98), hemp, hand="fist", hand_dir=(1, -0.8))
    else:
        out += k_arm((-13, -108), (-10, -86), (6, -76), hemp, hand="fist", hand_dir=(1, 0.4))
    return out


def escort_body():
    """호위무사: 삿갓, indigo 두루마기 with a studded red 전복 vest, two-handed sword in middle guard."""
    ind, red = KM["ind"], KM["red"]
    out = ""
    # pennant on a bamboo pole slung across the back
    out += K.form((-18, -150), (-8, -60), 1.4, 1.4, KM["bamboo"].back(0.2), ao_start=False)
    pen = "M-18 -150 L4 -142 L-16 -132 Z"
    out += _pathc(pen, K._cyl_grad((-8, -142), (-0.3, -1), 9, KM["ochre"]))
    out += K.ball(-11, -142, 2.4, KM["ind"], outline=False)
    # scabbard on the near hip, behind the legs
    out += K.form((-12, -74), (-30, -26), 2.6, 2.4, KM["lacquer"], ao_start=False)
    out += K.ball(-18, -58, 1.5, KM["brass"]) + K.ball(-25, -40, 1.5, KM["brass"])
    # back coat flap
    flap, _ = kpanel([(-15, -70), (14, -70), (24, -34), (0, -30), (-25, -34)], ind.back(0.3), folds=5)
    out += flap
    # far leg + far arm (on the hilt) behind torso
    out += k_leg((6, -66), (15, -38), (19, -7), ind, d=1, back=True)
    out += k_arm((13, -108), (25, -96), (22, -86), ind, hand="fist", hand_dir=(0.5, -1), back=True)
    out += k_leg((-5, -66), (-13, -38), (-17, -7), ind, d=-1)
    # front coat flaps over the thighs
    fl, _ = kpanel([(-14, -71), (-1, -71), (-6, -42), (-24, -38)], ind, folds=3)
    out += fl
    t, d = k_torso(ind, KM["red"])
    out += t
    # 전복 vest: red, black border, brass studs (each stud catches its own light)
    vest = "M-14 -110 L-4 -112 L4 -86 L-1 -71 L-14 -71 Q-17 -90 -14 -110 Z"
    vg = K._cyl_grad((-8, -92), (-1, -0.1), 9, red)
    studs = "".join(K.stud(x, y, 1.1) for y in range(-104, -74, 6) for x in (-12, -8, -4) if not (x == -4 and y < -98))
    out += _pathc(vest, vg) + K.clip_overlay(vest, studs)
    out += f'<path d="{vest}" fill="none" stroke="#1A0A06" stroke-width="1.4" opacity="0.8"></path>'
    vest2 = "M8 -112 L14 -110 Q17 -90 14 -71 L6 -71 L9 -88 Z"
    out += _pathc(vest2, K._cyl_grad((11, -92), (-1, -0.1), 5, red.back(0.25)))
    out += K.clip_overlay(vest2, "".join(K.stud(x, y, 1.0, KM["brass"].back(0.2)) for y in range(-104, -74, 6) for x in (10, 13)))
    out += k_neck()
    out += khead(0.8, -127, 10, turn=0.3, kind="escort")
    out += brim_ao(0.8, -135, 13)
    out += k_satgat(0.8, -134)
    # sword: hilt with cord wrap, guard (코등이), blade with fuller + specular edge
    hilt_a, hilt_b = (12, -80), (26, -92)
    out += K.form(hilt_a, hilt_b, 1.9, 1.9, KM["leather_dark"], ao_start=False)
    out += K.bands(hilt_a, hilt_b, 1.9, 1.9, KM["leather_dark"], n=5)
    out += K.ball(27.5, -93.3, 3.2, KM["brass"], ry=2.0)
    blade = "M27 -95.5 L56 -134 L57.4 -132.2 L30 -92.5 Z"
    out += _pathc(blade, K._cyl_grad((42, -113), (-0.8, -0.6), 2.4, KM["steel"]))
    out += f'<path d="M28.6 -94.4 L56.4 -133" stroke="#FFFFFF" stroke-width="0.5" opacity="0.9"></path>'
    out += f'<path d="M29.5 -93.3 L55 -128" stroke="#2A3038" stroke-width="0.35" opacity="0.7"></path>'
    out += K.form((10, -79), (6, -70), 1.0, 0.8, KM["red"], ao_start=False, spec=False)
    # near arm across the body onto the hilt
    out += k_arm((-13, -108), (-9, -86), (13, -82), ind, hand="fist", hand_dir=(1, -0.85))
    return out


def korean_bow(top, grip, bot, draw):
    """각궁: recurved horn bow (tips curl forward) + string to the draw hand."""
    bm = KM["lacquer"]
    out = ""
    for a, c in ((top, (grip[0] + 6, (top[1] + grip[1]) / 2)), (bot, (grip[0] + 6, (bot[1] + grip[1]) / 2))):
        d = f"M{f(grip[0])} {f(grip[1])} Q{f(c[0])} {f(c[1])} {f(a[0])} {f(a[1])}"
        out += f'<path d="{d}" stroke="{OUTLINE}" stroke-width="3.6" fill="none" stroke-linecap="round" opacity="0.8"></path>'
        out += f'<path d="{d}" stroke="{bm.base}" stroke-width="2.6" fill="none" stroke-linecap="round"></path>'
        out += f'<path d="{d}" stroke="{bm.light}" stroke-width="0.9" fill="none" stroke-linecap="round" transform="translate(-0.6 -0.3)"></path>'
        out += f'<path d="{d}" stroke="{OCHRE}" stroke-width="2.7" fill="none" stroke-dasharray="1.2 9" opacity="0.9"></path>'
    for a in (top, bot):
        out += K.ball(a[0], a[1], 1.4, KM["horn"])
    out += f'<path d="M{f(top[0])} {f(top[1])} L{f(draw[0])} {f(draw[1])} L{f(bot[0])} {f(bot[1])}" stroke="#F0E6C8" stroke-width="0.55" fill="none"></path>'
    return out


def archer_body(draw=True, lit=True):
    """방패연 궁수: side-on, bow arm extended to the right, string drawn to the cheek; quiver on the back,
    kite reel on the belt."""
    cl, ind = KM["sailcloth"], KM["ind"]
    out = ""
    # quiver with fletchings
    out += K.form((-14, -84), (-6, -126), 4.4, 5.0, KM["wood"].back(0.15), ao_start=False)
    for i in range(5):
        a = (-9 + i * 1.6, -124 - i * 0.4)
        b = (-12 + i * 2.2, -140 - i * 0.6)
        out += K.form(a, b, 0.6, 0.6, KM["bamboo"], ao_start=False, spec=False)
        out += f'<path d="M{f(b[0])} {f(b[1])} l-1.4 -4 l2.6 0.4 z" fill="{RED if i % 2 else CLOTH}" stroke="{OUTLINE}" stroke-width="0.3"></path>'
    out += K.form((-15, -88), (-5, -86), 5.2, 5.2, KM["leather"], ao_start=False, spec=False)
    out += k_leg((5, -66), (13, -38), (19, -7), cl, d=1, back=True)
    # bow arm (far) extended to the right, fist on the grip
    grip = (44, -112)
    out += k_arm((12, -109), (28, -110), (38, -111), cl, hand="fist", hand_dir=(1, 0), back=True)
    out += k_leg((-4, -66), (-12, -38), (-15, -7), cl, d=-1)
    t, d = k_torso(cl, ind)
    out += t
    out += K.ball(-6, -66, 4.0, KM["wood"]) + K.ball(-6, -66, 1.6, KM["leather_dark"], outline=False)
    out += k_neck()
    out += khead(1.2, -127, 10, turn=0.55, kind="archer")
    out += brim_ao(1.2, -136)
    out += k_paeraengi(1.2, -136)
    draw_hand = (3, -116) if draw else (14, -108)
    out += korean_bow((40, -148), grip, (40, -76), draw_hand if draw else (38, -112))
    # bow-grip hand on top of the bow
    out += K.hand((38, -111), (1, 0), 6.4 * 0.72, KM["skin"].back(0.12), kind="fist")
    # arrow + (optional) goblin-fire wad at the tip
    tip = (66, -116.5) if draw else (60, -110)
    out += K.form(draw_hand, tip, 0.7, 0.7, KM["bamboo"], ao_start=False, spec=False)
    out += f'<path d="M{f(tip[0])} {f(tip[1] - 2)} l5 2 l-5 2 z" fill="#C8C8C0" stroke="{OUTLINE}" stroke-width="0.3"></path>'
    out += f'<path d="M{f(draw_hand[0] + 1)} {f(draw_hand[1])} l-3 -3 m3 3 l-3 3" stroke="#F6F0E0" stroke-width="1.2"></path>'
    if lit:
        out += glow(tip[0] + 2, tip[1], 9, "fireglow", 1)
        out += f'<ellipse cx="{f(tip[0] + 1)}" cy="{f(tip[1] - 0.4)}" rx="4" ry="2.4" fill="url(#r3-fire)" filter="url(#r3-flame)"></ellipse>'
    # drawing arm (near): elbow raised behind, hand at the cheek pinching the string
    if draw:
        out += k_arm((-12, -108), (-26, -112), draw_hand, cl, hand="fist", hand_dir=(1, -0.1))
    else:
        out += k_arm((-12, -108), (-16, -88), (-8, -78), cl, hand="fist", hand_dir=(0.3, 1))
    # bracer (깍지 / 팔찌) on the bow forearm
    out += K.form((30, -110.6), (35, -110.8), 4.0, 3.8, KM["leather_dark"].back(0.15), ao_start=False)
    return out


def crew_body(pose="tiller", hat=True, kind=None):
    """Waist-up crew member for the barge / scout basket. Feet line = gunwale (0,0) at -66 → shifted up."""
    hemp, ind = KM["hemp"], KM["ind"]
    out = ""
    if pose == "tiller":
        out += k_arm((13, -108), (24, -98), (34, -94), hemp, hand="fist", hand_dir=(1, 0.2), back=True)
    elif pose == "glass":
        out += k_arm((13, -108), (22, -112), (24, -124), hemp, hand="fist", hand_dir=(1, -0.5), back=True)
    t, d = k_torso(hemp, ind)
    out += t + k_neck()
    out += khead(0.8, -127, 10, turn=0.4, kind=kind or ("lookout" if pose == "glass" else "helm"))
    if hat:
        out += brim_ao(0.8, -136) + k_paeraengi(0.8, -136, cotton=True)
    if pose == "tiller":
        out += k_arm((-13, -108), (-10, -90), (22, -92), hemp, hand="fist", hand_dir=(1, 0))
    elif pose == "glass":
        out += K.form((16, -126), (40, -134), 2.6, 3.4, KM["brass"], ao_start=False)
        out += K.ball(40.5, -134.2, 3.2, KM["brass"], ry=3.6)
        out += k_arm((-13, -108), (-6, -112), (18, -124), hemp, hand="fist", hand_dir=(1, -0.3))
    else:
        out += k_arm((-13, -108), (-16, -88), (-8, -80), hemp, hand="fist")
    return out


# ---------------------------------------------------------------- unit art (viewBox 0 0 200 160)

def plinth(rng, cx=100, cy=118, s=30, n=55, yard=None, wet=False):
    iso = Iso(cx, cy - s * 1.5, s)
    return ground_patch(iso, 0, 0, 3, 3, rng, yard=yard, n_tuft=n, wet=wet)


def u_porter():
    rng = random.Random(301)
    out = plinth(rng, yard=(0.6, 0.6, 2.4, 2.4))
    out += rock_with_ore(150, 128, 0.7)
    out += k_place(98, 152, 0.9, porter_body('walk'))
    return out


def u_escort():
    rng = random.Random(302)
    out = plinth(rng)
    out += k_place(88, 152, 0.98, escort_body())
    return out


def u_archer():
    rng = random.Random(303)
    out = plinth(rng)
    k, (bx, by) = shield_kite(34, 30, 26, 34, -14)
    out += shadow(52, 152, 10, 2.6, 0.25)  # kite shadow (offset)
    out += sprite(k)
    out += f'<path d="M{f(bx)} {f(by)} Q48 80 76 88" stroke="#E8DDB8" stroke-width="0.6" fill="none" opacity="0.9"></path>'
    out += k_place(80, 152, 0.98, archer_body())
    return out


def u_barge():
    rng = random.Random(304)
    iso = Iso(100, 74, 21)
    out = ground_patch(iso, -0.3, -0.3, 3.3, 3.3, rng, n_tuft=20, leaves=False, wet=True)
    # water pool under the hovering hull
    w = iso.face_t(0.2, 0.6, 3.0, 2.6, 0)
    out += poly(w, "url(#r3-water)", ' opacity="0.85" filter="url(#r3-tx-stone)"')
    for k in range(6):
        c = iso.P(0.6 + k * 0.45, 1.6 + (k % 2) * 0.5)
        out += f'<path d="M{f(c[0] - 5)} {f(c[1])} q5 -2 10 0" stroke="#CFE8F0" stroke-width="0.6" fill="none" opacity="0.6"></path>'
    # hover spray ring + shadow
    c = iso.P(1.6, 1.6)
    out += f'<ellipse cx="{f(c[0])}" cy="{f(c[1] + 4)}" rx="50" ry="18" fill="none" stroke="#E8F4F8" stroke-width="2.4" opacity="0.35" filter="url(#r3-blur1)" stroke-dasharray="3 5"></ellipse>'
    out += shadow(c[0] + 6, c[1] + 6, 46, 15, 0.5, "soft6")
    out += f'<g transform="translate(100 78) scale(0.95) translate(-100 -40)">{sprite(barge_body(Iso(100, 46, 26), rng))}</g>'
    return out


def barge_body(iso, rng, sail=True, z=0.55):
    """Plank-built river boat along the iso x axis, hovering at height z."""
    out = ""
    # hull: bottom outline + gunwale outline; upturned bow (x high) and stern
    xs = [0.0, 0.3, 1.0, 2.0, 2.8, 3.25]
    lift = [0.55, 0.2, 0.0, 0.0, 0.25, 0.75]
    half = [0.35, 0.62, 0.78, 0.78, 0.55, 0.12]
    yc = 1.6
    gun = [iso.P(x, yc + hw, z + 0.62 + lf) for x, hw, lf in zip(xs, half, lift)]
    bot = [iso.P(x, yc + hw * 0.65, z + lf * 0.8) for x, hw, lf in zip(xs, half, lift)]
    gun_b = [iso.P(x, yc - hw, z + 0.62 + lf) for x, hw, lf in zip(xs, half, lift)]
    # deck
    deck = gun + gun_b[::-1]
    out += poly(deck, "url(#r3-deck)", ' filter="url(#r3-tx-wood)"')
    for k in range(1, 6):
        t = k / 6
        a = [lerp(g, b, t) for g, b in zip(gun, gun_b)]
        out += f'<polyline points="{pts(a)}" fill="none" stroke="#6A4A2C" stroke-width="0.4" opacity="0.7"></polyline>'
    # inner bulwark shading
    out += f'<polyline points="{pts(gun_b)}" fill="none" stroke="#3A2616" stroke-width="2.4" opacity="0.6"></polyline>'
    # cargo on deck
    c = iso.P(1.0, 1.55, z + 0.62)
    out += onggi(c[0] - 6, c[1] + 3, 4.2) + onggi(c[0] + 2, c[1] + 5, 3.6) + sack(c[0] - 14, c[1] + 6, 8, 6, "hemp")
    c2 = iso.P(2.1, 1.6, z + 0.62)
    out += cloth_bolt(c2[0] - 6, c2[1] - 2, 10, 2.4, "indigo") + cloth_bolt(c2[0] - 4, c2[1] + 2, 9, 2.2, "red")
    # crew: helmsman at the stern tiller, lookout at the bow (waist-up, hull hides the hips)
    for (cx_, cy_), pose in ((iso.P(0.5, 1.55, z + 0.62), "tiller"), (iso.P(2.5, 1.5, z + 0.62), "glass")):
        out += f'<g transform="translate({f(cx_)} {f(cy_ + 69 * 0.3)}) scale(0.3)">{crew_body(pose)}</g>'
    # mast + sail (yellow 황포 with battens)
    m0 = iso.P(1.65, 1.6, z + 0.62)
    m1 = (m0[0], m0[1] - 84)
    if sail:
        sl = [(m0[0] - 34, m0[1] - 78), (m0[0] + 4, m0[1] - 80), (m0[0] + 8, m0[1] - 18), (m0[0] - 38, m0[1] - 12)]
        out += (f'<path d="M{f(sl[0][0])} {f(sl[0][1])} Q{f(m0[0] - 14)} {f(m0[1] - 86)} {f(sl[1][0])} {f(sl[1][1])} Q{f(m0[0] + 14)} {f(m0[1] - 48)} {f(sl[2][0])} {f(sl[2][1])} '
                f'Q{f(m0[0] - 14)} {f(m0[1] - 8)} {f(sl[3][0])} {f(sl[3][1])} Q{f(m0[0] - 30)} {f(m0[1] - 46)} {f(sl[0][0])} {f(sl[0][1])} Z" fill="url(#r3-sail)" filter="url(#r3-tx-cloth)"></path>')
        for k in range(1, 7):
            t = k / 7
            a = lerp(sl[0], sl[3], t)
            b = lerp(sl[1], sl[2], t)
            mm = lerp(a, b, 0.5)
            out += (f'<path d="M{f(a[0])} {f(a[1])} Q{f(mm[0] + 6)} {f(mm[1] + 2)} {f(b[0])} {f(b[1])}" stroke="#6A4A18" stroke-width="1" fill="none"></path>'
                    f'<path d="M{f(a[0])} {f(a[1] + 1.2)} Q{f(mm[0] + 6)} {f(mm[1] + 3.2)} {f(b[0])} {f(b[1] + 1.2)}" stroke="#FFE8A8" stroke-width="0.4" fill="none" opacity="0.6"></path>')
        out += f'<path d="M{f(sl[1][0])} {f(sl[1][1])} Q{f(m0[0] + 14)} {f(m0[1] - 48)} {f(sl[2][0])} {f(sl[2][1])}" stroke="#5A3E10" stroke-width="1.2" fill="none" opacity="0.6"></path>'
        # sheet lines from batten ends to stern
        st = iso.P(0.3, 1.6, z + 0.9)
        for k in range(1, 7):
            a = lerp(sl[0], sl[3], k / 7)
            out += line(a, st, "#D8CBA0", 0.3, 0.6)
    out += line(m0, m1, "url(#r3-pole)", 2.4) + line((m1[0] - 1, m1[1] + 2), (m1[0] + 1, m1[1]), "#3A2616", 1)
    # rigging stays
    out += line(m1, iso.P(3.2, 1.6, z + 1.35), "#C8B88A", 0.45) + line(m1, iso.P(0.0, 1.6, z + 1.1), "#C8B88A", 0.45)
    out += f'<path d="M{f(m1[0])} {f(m1[1])} l6 -2 l-6 -3 z" fill="{RED}"></path>'
    # hull side (lit, +y side) with strakes
    side = gun + bot[::-1]
    out += poly(side, "url(#r3-hull)", ' filter="url(#r3-tx-wood)"')
    for k in range(1, 4):
        t = k / 4
        a = [lerp(g, b, t) for g, b in zip(gun, bot)]
        out += f'<polyline points="{pts(a)}" fill="none" stroke="#24160A" stroke-width="0.6"></polyline>'
        out += f'<polyline points="{pts([(p[0], p[1] - 0.8) for p in a])}" fill="none" stroke="#B08C5C" stroke-width="0.35" opacity="0.6"></polyline>'
    out += f'<polyline points="{pts(gun)}" fill="none" stroke="#C8A070" stroke-width="1.2"></polyline>'
    out += f'<polyline points="{pts(gun)}" fill="none" stroke="#2A1A0C" stroke-width="0.4" transform="translate(0 1.2)"></polyline>'
    for g in gun[1:-1]:
        out += f'<circle cx="{f(g[0])}" cy="{f(g[1] + 2.2)}" r="0.7" fill="url(#r3-brass)"></circle>'
    # bow gun (총통) on the prow
    p = iso.P(2.95, 1.6, z + 1.1)
    out += (f'<path d="M{f(p[0] - 6)} {f(p[1] + 1)} L{f(p[0] + 9)} {f(p[1] + 6)} L{f(p[0] + 9)} {f(p[1] + 8.4)} L{f(p[0] - 6)} {f(p[1] + 4.2)} Z" fill="url(#r3-brass)"></path>'
            f'<ellipse cx="{f(p[0] + 9)}" cy="{f(p[1] + 7.2)}" rx="1" ry="1.4" fill="#1A1008"></ellipse>'
            + "".join(f'<path d="M{f(p[0] - 3 + i * 4)} {f(p[1] + 1.8 + i * 1.3)} v2.6" stroke="#4E3C1A" stroke-width="0.6"></path>' for i in range(3)))
    # bow eye ornament + lantern on stern
    e = lerp(gun[-2], bot[-2], 0.4)
    out += f'<circle cx="{f(e[0])}" cy="{f(e[1])}" r="2.2" fill="#EFE8D8"></circle><circle cx="{f(e[0] + 0.4)}" cy="{f(e[1])}" r="1.1" fill="#1A1008"></circle>'
    s0 = iso.P(0.2, 1.6, z + 1.2)
    out += lantern(s0[0], s0[1] - 6, 2.0)
    return out


def u_scout():
    rng = random.Random(305)
    out = plinth(rng, n=40)
    # far ground shadow -> it flies high
    out += f'<path d="M118 128 l22 -6 l6 14 l-22 6 z" fill="#000" opacity="0.32" filter="url(#r3-soft)"></path>'
    out += f'<path d="M142 136 q10 6 2 14 q-8 6 4 12" stroke="#000" stroke-width="3" fill="none" opacity="0.18" filter="url(#r3-soft)"></path>'
    k, (bx, by) = shield_kite(88, 50, 52, 70, -10, tails=True)
    out += sprite(k)
    # lookout gondola: a small basket with a spyglass watcher hanging from the bridle
    out += f'<path d="M{f(bx)} {f(by)} L96 104 M{f(bx)} {f(by)} L108 104" stroke="#E8DDB8" stroke-width="0.45"></path>'
    out += sprite(
        f'<path d="M92 104 L112 104 L109 114 L95 114 Z" fill="url(#r3-wood)" filter="url(#r3-tx-wood)"></path>'
        + "".join(line((93 + i * 3, 104), (95 + i * 2.4, 114), "#3A2414", 0.5) for i in range(7))
        + bell(98, 116, 1.4) + bell(106, 116, 1.4))
    out += f'<g transform="translate(101 140) scale(0.3)">{crew_body("glass", kind="scout")}</g>'
    out += sprite(f'<path d="M92 104 L112 104 L109 114 L95 114 Z" fill="url(#r3-wood)" filter="url(#r3-tx-wood)"></path>'
                  + "".join(line((93 + i * 3, 104), (95 + i * 2.4, 114), "#3A2414", 0.5) for i in range(7)))
    # wind lines
    for i in range(4):
        y = 30 + i * 22
        out += f'<path d="M{150 + i * 4} {y} q12 -3 28 0" stroke="#EFE6D2" stroke-width="0.6" fill="none" opacity="0.35"></path>'
    return out


# ---------------------------------------------------------------- building art (viewBox 0 0 200 160)

def b_frame(rng, s, foot, yard=True, n=60, ox=100, oy=None, wet=False):
    """Ground plinth around a footprint `foot` (tiles) centred in the view."""
    pad = 0.55
    oy = 104 - (foot[0] + foot[1]) * s / 4 if oy is None else oy
    iso = Iso(ox - (foot[0] - foot[1]) * s / 2, oy, s)
    y = (0, 0, foot[0], foot[1]) if yard else None
    g = ground_patch(iso, -pad, -pad, foot[0] + pad, foot[1] + pad, rng, yard=y, n_tuft=n, wet=wet)
    return iso, g


def stall(iso, x0, y0, x1, y1, pat_cols, rng, goods="jars"):
    """Market stall: table, goods, striped awning on poles."""
    out = ""
    # back poles
    for p in (iso.P(x0, y0, 0), iso.P(x1, y0, 0)):
        out += line(p, (p[0], p[1] - iso.s * 1.25), "url(#r3-pole)", 1.2)
    # table
    out += box(iso, x0 + 0.05, y0 + 0.15, x1 - 0.05, y1 - 0.05, 0, 0.32, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    t = iso.P((x0 + x1) / 2, (y0 + y1) / 2, 0.32)
    if goods == "jars":
        out += onggi(t[0] - 5, t[1] + 1, 2.8) + onggi(t[0] + 1, t[1] + 2.4, 3.2) + onggi(t[0] + 6, t[1] - 0.6, 2.4, lid=False)
    elif goods == "cloth":
        out += cloth_bolt(t[0] - 8, t[1] - 2, 9, 1.8, "indigo") + cloth_bolt(t[0] - 6, t[1] + 1.4, 9, 1.8, "red") + cloth_bolt(t[0] - 4, t[1] + 4.6, 9, 1.8, "ochre")
    elif goods == "ore":
        out += sack(t[0] - 4, t[1] + 2, 6, 5, "hemp", glow_ore=True) + sack(t[0] + 4, t[1] + 3, 5, 4, "hemp")
    # front poles
    for p in (iso.P(x0, y1, 0), iso.P(x1, y1, 0)):
        out += line(p, (p[0], p[1] - iso.s * 0.95), "url(#r3-pole)", 1.2)
    # awning (sloping cloth, stripes along x)
    z0, z1 = 0.95, 1.25
    n = 8
    for i in range(n):
        a, b = x0 - 0.1 + (x1 - x0 + 0.2) * i / n, x0 - 0.1 + (x1 - x0 + 0.2) * (i + 1) / n
        quad = [iso.P(a, y0 - 0.1, z1), iso.P(b, y0 - 0.1, z1), iso.P(b, y1 + 0.2, z0), iso.P(a, y1 + 0.2, z0)]
        out += poly(quad, pat_cols[i % 2], ' filter="url(#r3-tx-cloth)"')
    # scalloped front edge
    e0, e1 = iso.P(x0 - 0.1, y1 + 0.2, z0), iso.P(x1 + 0.1, y1 + 0.2, z0)
    sc = ""
    for i in range(n):
        a, b = lerp(e0, e1, i / n), lerp(e0, e1, (i + 1) / n)
        sc += f'<path d="M{f(a[0])} {f(a[1])} Q{f((a[0] + b[0]) / 2)} {f((a[1] + b[1]) / 2 + 3)} {f(b[0])} {f(b[1])}" fill="{pat_cols[i % 2]}" stroke="#2A1A10" stroke-width="0.3"></path>'
    out += sc
    out += line(e0, e1, "#FFF", 0.5, 0.4)
    return out


def b_market():
    rng = random.Random(311)
    s = 27
    iso, out = b_frame(rng, s, (3, 3), n=70)
    out += stone_base(iso, 0.55, 0.3, 2.45, 1.55, 0, 0.22, rng, 1)
    out += timber_walls(iso, 0.7, 0.42, 2.3, 1.42, 0.22, 1.0, posts_x=3, posts_y=2, door=True)
    out += dancheong(iso, 0.7, 2.3, 1.42, 1.0)
    out += giwa_roof(iso, 0.7, 0.42, 2.3, 1.42, 1.02, 1.85, ov=0.32)
    # signboard (현판)
    sp = iso.P(1.5, 1.42, 0.9)
    out += f'<path d="M{f(sp[0] - 7)} {f(sp[1] - 4)} L{f(sp[0] + 7)} {f(sp[1] + 3)} L{f(sp[0] + 7)} {f(sp[1] + 7)} L{f(sp[0] - 7)} {f(sp[1])} Z" fill="url(#r3-lacquer)" stroke="{OCHRE}" stroke-width="0.4"></path>'
    out += f'<text x="{f(sp[0])}" y="{f(sp[1] + 2.4)}" font-size="3.2" fill="{OCHRE_L}" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" transform="rotate(26.5 {f(sp[0])} {f(sp[1] + 2)})">장 터</text>'
    # stalls in front
    out += stall(iso, 0.2, 1.85, 1.15, 2.55, ["#E8E1C8", OCHRE], rng, "jars")
    out += stall(iso, 1.4, 1.95, 2.35, 2.75, ["#E8E1C8", INDIGO], rng, "cloth")
    out += dried_fish_rack(*iso.P(2.75, 1.25, 0), w=14, rng=rng)
    # guild flag + lanterns
    fp = iso.P(2.8, 2.7, 0)
    out += line(fp, (fp[0], fp[1] - 56), "url(#r3-bamboo)", 1.4)
    out += f'<path d="M{f(fp[0])} {f(fp[1] - 56)} L{f(fp[0] + 20)} {f(fp[1] - 50)} L{f(fp[0])} {f(fp[1] - 42)} Z" fill="url(#r3-ochre)" filter="url(#r3-tx-cloth)"></path>'
    out += f'<circle cx="{f(fp[0] + 6)}" cy="{f(fp[1] - 49.5)}" r="2.6" fill="{INDIGO}"></circle><circle cx="{f(fp[0] + 6)}" cy="{f(fp[1] - 49.5)}" r="1.1" fill="{CLOTH}"></circle>'
    for x in (0.9, 2.1):
        p = iso.P(x, 1.42, 1.0)
        out += lantern(p[0], p[1] + 5, 1.8)
    p = iso.P(0.3, 2.9, 0)
    out += sack(p[0], p[1], 7, 6, "hemp") + sack(p[0] + 7, p[1] + 2, 6, 5, "ochre", glow_ore=True)
    return sprite(out)


def b_bellows():
    rng = random.Random(312)
    s = 38
    iso, out = b_frame(rng, s, (2, 2), n=55)
    # violet mist cleft behind
    c = iso.P(1.55, 0.35, 0)
    out += f'<ellipse cx="{f(c[0])}" cy="{f(c[1])}" rx="18" ry="6" fill="#1A1222"></ellipse>'
    out += f'<path d="M{f(c[0] - 14)} {f(c[1])} l6 -1 l4 2 l6 -2 l5 1 l7 0" stroke="#B79AE0" stroke-width="1" fill="none" filter="url(#r3-glow)"></path>'
    for i in range(4):
        out += f'<ellipse cx="{f(c[0] + rng.uniform(-6, 6))}" cy="{f(c[1] - 10 - i * 14)}" rx="{14 + i * 4}" ry="{8 + i * 2}" fill="url(#r3-mist)" filter="url(#r3-smoke)" opacity="{0.85 - i * 0.15}"></ellipse>'
    # clay furnace with flue (풍로)
    out += box(iso, 0.95, 0.15, 1.55, 0.75, 0, 0.75, "#8A5A3A", "url(#r3-stone-lit)", "url(#r3-stone-sh)", "tx-earth")
    f0 = iso.P(1.25, 0.45, 0.75)
    out += (f'<rect x="{f(f0[0] - 4)}" y="{f(f0[1] - 26)}" width="8" height="26" fill="url(#r3-stone-lit)" filter="url(#r3-tx-earth)"></rect>'
            f'<rect x="{f(f0[0])}" y="{f(f0[1] - 26)}" width="4" height="26" fill="#000" opacity="0.3"></rect>'
            f'<ellipse cx="{f(f0[0])}" cy="{f(f0[1] - 26)}" rx="4" ry="1.6" fill="#1A1222"></ellipse>')
    mouth = iso.P(1.25, 0.75, 0.25)
    out += f'<ellipse cx="{f(mouth[0])}" cy="{f(mouth[1])}" rx="4" ry="3" fill="#FFB050" filter="url(#r3-glow)"></ellipse>' + glow(mouth[0], mouth[1], 12, "fireglow", 0.9)
    # thatched open shed
    for x, y in ((0.1, 0.9), (1.9, 0.9), (0.1, 1.9), (1.9, 1.9)):
        p = iso.P(x, y, 0)
        out += line(p, (p[0], p[1] - s * 1.0), "url(#r3-pole)", 1.8)
    # 골풀무: seesaw foot bellows (board + two box chambers)
    out += box(iso, 0.35, 1.15, 1.65, 1.7, 0, 0.32, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    b0, b1 = iso.P(0.4, 1.42, 0.55), iso.P(1.6, 1.42, 0.25)
    out += line(b0, b1, "#6A4A2C", 4) + line((b0[0], b0[1] - 1.2), (b1[0], b1[1] - 1.2), "#B08C5C", 1.2, 0.7)
    pv = iso.P(1.0, 1.42, 0.4)
    out += f'<path d="M{f(pv[0] - 3)} {f(pv[1] + 4)} L{f(pv[0])} {f(pv[1] - 1)} L{f(pv[0] + 3)} {f(pv[1] + 4)} Z" fill="url(#r3-wood-sh)"></path>'
    # pipe from bellows to furnace
    out += f'<path d="M{f(b1[0])} {f(b1[1] + 2)} Q{f(mouth[0] + 6)} {f(mouth[1] + 10)} {f(mouth[0] + 3)} {f(mouth[1] + 2)}" stroke="#4A3220" stroke-width="2.4" fill="none"></path>'
    # a worker figure pumping (small)
    out += k_place(*iso.P(0.55, 1.6, 0.35), 0.27, porter_body('unload'))
    out += thatch_roof(iso, 0.1, 0.9, 1.9, 1.9, 1.0, 1.5, rng)
    # collection jars of captured mist
    for i, (x, y) in enumerate(((2.05, 0.45), (2.25, 0.9), (2.05, 1.35))):
        p = iso.P(x, y, 0)
        out += onggi(p[0], p[1], 3.6) + glow(p[0], p[1] - 7, 6, "mist", 0.9)
    return sprite(out)


def b_loft():
    rng = random.Random(313)
    s = 36
    iso, out = b_frame(rng, s, (2, 2), n=55)
    # two-storey loft: stone base, ground floor, upper gallery, giwa roof
    out += stone_base(iso, 0.15, 0.1, 1.55, 1.3, 0, 0.18, rng, 1)
    out += timber_walls(iso, 0.25, 0.2, 1.45, 1.2, 0.18, 0.95, posts_x=2, posts_y=2, door=True)
    out += box(iso, 0.15, 0.1, 1.55, 1.3, 0.95, 1.02, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)")
    out += timber_walls(iso, 0.3, 0.25, 1.4, 1.15, 1.02, 1.6, posts_x=2, posts_y=2)
    # gallery railing
    for i in range(9):
        x = 0.15 + 1.4 * i / 8
        out += line(iso.P(x, 1.3, 1.02), iso.P(x, 1.3, 1.2), "#5A3E24", 0.7)
    out += line(iso.P(0.15, 1.3, 1.2), iso.P(1.55, 1.3, 1.2), "#7A5634", 1.1)
    out += dancheong(iso, 0.3, 1.4, 1.15, 1.6)
    out += giwa_roof(iso, 0.3, 0.25, 1.4, 1.15, 1.62, 2.25, ov=0.32)
    # drying rack with kites hanging (right side)
    r0, r1 = iso.P(1.75, 0.2, 0), iso.P(1.75, 1.8, 0)
    out += line(r0, (r0[0], r0[1] - 40), "url(#r3-bamboo)", 1.3) + line(r1, (r1[0], r1[1] - 40), "url(#r3-bamboo)", 1.3)
    out += line((r0[0], r0[1] - 38), (r1[0], r1[1] - 38), "url(#r3-bamboo)", 1.2)
    for i, rot in enumerate((-6, 4, -3)):
        t = (i + 0.6) / 3.4
        p = lerp((r0[0], r0[1] - 38), (r1[0], r1[1] - 38), t)
        k, _ = shield_kite(p[0], p[1] + 12, 12, 16, rot)
        out += line(p, (p[0], p[1] + 4), "#E8DDB8", 0.4) + k
    # bamboo bundles + paper stack
    p = iso.P(0.3, 1.75, 0)
    for i in range(6):
        out += line((p[0] - 10 + i * 1.2, p[1] - 2 + i * 0.3), (p[0] + 14 + i * 1.2, p[1] + 10 + i * 0.3), "url(#r3-bamboo)", 1.2)
    out += line((p[0] - 2, p[1] + 1), (p[0] - 2, p[1] + 6), ROPE, 0.9)
    q = iso.P(1.45, 1.75, 0)
    out += box(Iso(q[0], q[1] - 6, 6), 0, 0, 1.2, 0.9, 0, 0.6, "#F4ECDA", "#E2D8BE", "#B8AE92", "tx-paper")
    return sprite(out)


def b_inn():
    rng = random.Random(314)
    s = 38
    iso, out = b_frame(rng, s, (2, 2), n=50)
    # ㄱ-shaped hanok: back wing along x, side wing along y
    out += stone_base(iso, 0.05, 0.05, 1.95, 0.85, 0, 0.2, rng, 1)
    out += timber_walls(iso, 0.15, 0.12, 1.85, 0.78, 0.2, 0.9, posts_x=4, posts_y=1, door=True)
    out += dancheong(iso, 0.15, 1.85, 0.78, 0.9)
    out += giwa_roof(iso, 0.15, 0.12, 1.85, 0.78, 0.92, 1.45, ov=0.26)
    out += stone_base(iso, 1.25, 0.95, 1.95, 1.95, 0, 0.2, rng, 1)
    out += timber_walls(iso, 1.32, 1.02, 1.88, 1.88, 0.2, 0.85, posts_x=1, posts_y=3, windows=True)
    # side wing roof (ridge along y): draw by swapping axes through a mirrored iso basis
    out += giwa_roof_y(iso, 1.32, 1.02, 1.88, 1.88, 0.87, 1.36, ov=0.24)
    # courtyard: low table, jars, lanterns, signboard
    t = iso.P(0.7, 1.4, 0)
    out += box(Iso(t[0], t[1] - 4, 6), 0, 0, 2, 1.4, 0, 0.5, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    out += onggi(t[0] - 2, t[1] + 1, 1.6) + onggi(t[0] + 3, t[1] + 2.2, 1.4)
    for x in (0.5, 1.5):
        p = iso.P(x, 0.78, 0.9)
        out += lantern(p[0], p[1] + 5, 1.7)
    for x, y in ((0.25, 1.85), (0.55, 1.95)):
        p = iso.P(x, y, 0)
        out += onggi(p[0], p[1], 4.4)
    sp = iso.P(1.0, 0.78, 0.82)
    out += f'<path d="M{f(sp[0] - 7)} {f(sp[1] - 4)} L{f(sp[0] + 7)} {f(sp[1] + 3)} L{f(sp[0] + 7)} {f(sp[1] + 7)} L{f(sp[0] - 7)} {f(sp[1])} Z" fill="url(#r3-lacquer)" stroke="{OCHRE}" stroke-width="0.4"></path>'
    out += f'<text x="{f(sp[0])}" y="{f(sp[1] + 2.4)}" font-size="3.2" fill="{OCHRE_L}" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" transform="rotate(26.5 {f(sp[0])} {f(sp[1] + 2)})">客 主</text>'
    # abacus on the table
    out += f'<rect x="{f(t[0] + 4)}" y="{f(t[1] - 4)}" width="6" height="3" fill="#3A2414" transform="rotate(26 {f(t[0] + 7)} {f(t[1] - 2.5)})"></rect>'
    return sprite(out)


def giwa_roof_y(iso, x0, y0, x1, y1, ze, zr, ov=0.3):
    """Roof whose ridge runs along y (the side wing): lit face is +x? no: +y gable end shows."""
    xm = (x0 + x1) / 2
    hip = (x1 - x0) / 2 + ov
    E00, E10 = iso.P(x0 - ov, y0 - ov, ze + 0.15), iso.P(x1 + ov, y0 - ov, ze + 0.15)
    E11, E01 = iso.P(x1 + ov, y1 + ov, ze + 0.15), iso.P(x0 - ov, y1 + ov, ze + 0.15)
    R0, R1 = iso.P(xm, y0 - ov + hip * 0.6, zr), iso.P(xm, y1 + ov - hip, zr)
    out = poly([E00, E01, R1, R0], "url(#r3-roof-b)", ' filter="url(#r3-tx-stone)"')
    out += poly([E10, E11, R1, R0], "url(#r3-roof-r)", ' filter="url(#r3-tx-stone)"')
    rows = ""
    for k in range(1, 10):
        t = k / 10
        a = lerp(E10, E11, t)
        b = lerp(iso.P(xm, y0 - ov, zr), iso.P(xm, y1 + ov, zr), t)
        rows += line(a, b, "#0A0C0F", 0.7, 0.7) + line((a[0] - 0.8, a[1]), (b[0] - 0.6, b[1]), "#5A626E", 0.4, 0.45)
    out += clipped([E10, E11, R1, R0], rows)
    out += poly([E01, E11, R1], "url(#r3-roof-f)", ' filter="url(#r3-tx-stone)"')
    rows = ""
    for k in range(1, 7):
        t = k / 7
        a = lerp(E01, E11, t)
        rows += line(a, R1, "#14181E", 0.7, 0.8) + line((a[0] + 0.9, a[1]), (R1[0] + 0.5, R1[1]), "#B4BCC6", 0.45, 0.5)
    out += clipped([E01, E11, R1], rows)
    for a, b in ((R1, E01), (R1, E11)):
        out += line(a, b, "#1A1E24", 2) + line((a[0], a[1] - 0.5), (b[0], b[1] - 0.5), "#8E96A2", 0.5, 0.7)
    out += line(R0, R1, "#14181E", 3.2) + line((R0[0] - 1.2, R0[1] + 0.6), (R1[0] - 1.2, R1[1] + 0.6), "#E8E2D4", 0.8, 0.8)
    for E in (E01, E11):
        out += f'<path d="M{f(E[0] - 1.5)} {f(E[1] + 0.6)} q1.5 -0.2 2.6 -2.6" stroke="#1A1E24" stroke-width="1.1" fill="none" stroke-linecap="round"></path>'
    return out


def duck(cx, cy, s=1.0, d=1):
    """Carved wooden bird for a 솟대 pole top."""
    return (f'<path d="M{f(cx - 5 * s * d)} {f(cy)} Q{f(cx - 2 * s * d)} {f(cy - 3 * s)} {f(cx + 3 * s * d)} {f(cy - 1.4 * s)} Q{f(cx + 4 * s * d)} {f(cy - 5 * s)} {f(cx + 6.2 * s * d)} {f(cy - 4.4 * s)} '
            f'L{f(cx + 7.6 * s * d)} {f(cy - 3.6 * s)} L{f(cx + 5.8 * s * d)} {f(cy - 3.2 * s)} Q{f(cx + 5 * s * d)} {f(cy + 0.4 * s)} {f(cx + 2 * s * d)} {f(cy + 1.2 * s)} Q{f(cx - 2 * s * d)} {f(cy + 1.6 * s)} {f(cx - 5 * s * d)} {f(cy)} Z" '
            f'fill="url(#r3-wood)" filter="url(#r3-tx-wood)"></path>'
            f'<circle cx="{f(cx + 5.6 * s * d)}" cy="{f(cy - 4 * s)}" r="{f(0.45 * s)}" fill="#1A1008"></circle>'
            f'<path d="M{f(cx - 3 * s * d)} {f(cy - 0.8 * s)} Q{f(cx)} {f(cy - 2 * s)} {f(cx + 2.6 * s * d)} {f(cy - 1.2 * s)}" stroke="#E8C890" stroke-width="{f(0.35 * s)}" fill="none" opacity="0.7"></path>')


def b_sotdae():
    rng = random.Random(315)
    s = 52
    iso, out = b_frame(rng, s, (1, 1), n=40, yard=False)
    c = iso.P(0.5, 0.5, 0)
    # stone cairn base (돌무더기)
    for i in range(16):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(0, 12)
        x, y = c[0] + math.cos(a) * r, c[1] + math.sin(a) * r * 0.5 - (12 - r) * 0.5
        rr = rng.uniform(2.4, 4.2)
        out += (f'<ellipse cx="{f(x + 0.8)}" cy="{f(y + 0.8)}" rx="{f(rr)}" ry="{f(rr * 0.7)}" fill="#000" opacity="0.35"></ellipse>'
                f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rr)}" ry="{f(rr * 0.72)}" fill="url(#r3-stone-lit)" filter="url(#r3-tx-stone)"></ellipse>'
                f'<ellipse cx="{f(x - rr * 0.3)}" cy="{f(y - rr * 0.3)}" rx="{f(rr * 0.4)}" ry="{f(rr * 0.22)}" fill="#E8E0CC" opacity="0.5"></ellipse>')
    poles = [(-8, 66, 1), (3, 80, -1), (13, 58, 1)]
    for dx, h, d in poles:
        b = (c[0] + dx, c[1] - 4)
        tp = (b[0] + d * 1.5, b[1] - h)
        out += line(b, tp, "url(#r3-pole)", 2.2)
        out += line((b[0] - 0.7, b[1]), (tp[0] - 0.7, tp[1]), "#D8B88A", 0.4, 0.6)
        out += duck(tp[0], tp[1] - 1, 2.7, d)
    # straw rope with five-colour cloth strips between the poles
    a, b = (c[0] - 8, c[1] - 40), (c[0] + 13, c[1] - 36)
    out += f'<path d="M{f(a[0])} {f(a[1])} Q{f((a[0] + b[0]) / 2)} {f(a[1] + 8)} {f(b[0])} {f(b[1])}" stroke="#C8AA6A" stroke-width="1.2" fill="none"></path>'
    for i, col in enumerate([RED, OCHRE, "#EFE8D8", BLUE, GREEN]):
        p = lerp(a, b, (i + 0.6) / 5.4)
        p = (p[0], p[1] + 4 * math.sin(math.pi * (i + 0.6) / 5.4) + 1)
        out += f'<path d="M{f(p[0])} {f(p[1])} q-1 5 0.6 10 l2 -0.4 q-1.6 -5 -0.6 -9.6 z" fill="{col}" filter="url(#r3-tx-cloth)"></path>'
    # offering jars + food sacks (supply)
    out += onggi(c[0] - 18, c[1] + 6, 4.2) + onggi(c[0] + 18, c[1] + 4, 3.6) + sack(c[0] + 6, c[1] + 12, 8, 6, "hemp")
    return sprite(out)


def flame(cx, cy, s=1.0):
    return (glow(cx, cy - 6 * s, 26 * s, "fireglow", 0.9)
            + f'<path d="M{f(cx - 8 * s)} {f(cy)} Q{f(cx - 10 * s)} {f(cy - 12 * s)} {f(cx - 2 * s)} {f(cy - 22 * s)} Q{f(cx - 2 * s)} {f(cy - 12 * s)} {f(cx + 2 * s)} {f(cy - 14 * s)} '
              f'Q{f(cx + 1 * s)} {f(cy - 22 * s)} {f(cx + 6 * s)} {f(cy - 26 * s)} Q{f(cx + 12 * s)} {f(cy - 12 * s)} {f(cx + 8 * s)} {f(cy)} Z" fill="url(#r3-fire)" filter="url(#r3-flame)"></path>'
            + f'<path d="M{f(cx - 3 * s)} {f(cy)} Q{f(cx - 4 * s)} {f(cy - 8 * s)} {f(cx + 1 * s)} {f(cy - 13 * s)} Q{f(cx + 5 * s)} {f(cy - 6 * s)} {f(cx + 3 * s)} {f(cy)} Z" fill="#FFF6D0" opacity="0.9"></path>')


def b_beacon():
    rng = random.Random(316)
    s = 50
    iso, out = b_frame(rng, s, (1, 1), n=40)
    # tapered stone platform in two tiers
    out += stone_base(iso, 0.0, 0.0, 1.0, 1.0, 0, 0.5, rng, 3)
    out += stone_base(iso, 0.15, 0.15, 0.85, 0.85, 0.5, 0.82, rng, 2)
    # stone steps on the lit side
    for i in range(3):
        out += box(iso, 0.35, 1.0 + 0.0, 0.65, 1.0 + 0.18 * (3 - i), 0.16 * i, 0.16 * (i + 1), "url(#r3-stone-top)", "url(#r3-stone-lit)", "url(#r3-stone-sh)", "tx-stone")
    # brazier chimney (연조) with fire + smoke
    out += box(iso, 0.33, 0.33, 0.67, 0.67, 0.82, 1.15, "#2A1A10", "url(#r3-stone-lit)", "url(#r3-stone-sh)", "tx-stone")
    c = iso.P(0.5, 0.5, 1.15)
    for i in range(5):
        out += f'<ellipse cx="{f(c[0] + i * 4 + rng.uniform(-2, 2))}" cy="{f(c[1] - 34 - i * 16)}" rx="{9 + i * 3}" ry="{7 + i * 2}" fill="url(#r3-smokeg)" filter="url(#r3-smoke)" opacity="{0.9 - i * 0.12}"></ellipse>'
    out += flame(c[0], c[1] + 2, 0.95)
    for i in range(10):
        out += f'<circle cx="{f(c[0] + rng.uniform(-10, 14))}" cy="{f(c[1] - rng.uniform(14, 44))}" r="{f(rng.uniform(0.4, 0.9))}" fill="#FFD27A" filter="url(#r3-glow)"></circle>'
    # guard banner + spear rack
    fp = iso.P(0.95, 0.1, 0.5)
    out += line(fp, (fp[0], fp[1] - 36), "url(#r3-bamboo)", 1.2)
    out += f'<path d="M{f(fp[0])} {f(fp[1] - 36)} L{f(fp[0] + 14)} {f(fp[1] - 31)} L{f(fp[0])} {f(fp[1] - 25)} Z" fill="url(#r3-red)"></path>'
    return sprite(out)


def b_dock():
    rng = random.Random(317)
    s = 27
    iso, out = b_frame(rng, s, (3, 2), n=50, wet=True, oy=46)
    # river along the +x side (water fills far part of plinth)
    wv = iso.face_t(-0.9, 1.25, 3.9, 2.9, 0)
    out += poly(wv, "url(#r3-water)", ' filter="url(#r3-tx-stone)"')
    for k in range(14):
        c = iso.P(rng.uniform(-0.5, 3.6), rng.uniform(1.4, 2.8))
        out += f'<path d="M{f(c[0] - 4)} {f(c[1])} q4 -1.6 8 0" stroke="#CFE8F0" stroke-width="0.5" fill="none" opacity="0.55"></path>'
    out += f'<polyline points="{pts([iso.P(x / 4, 1.25 + 0.06 * math.sin(x), 0) for x in range(-3, 16)])}" fill="none" stroke="#E8F4F8" stroke-width="1.4" opacity="0.45" filter="url(#r3-blur1)"></polyline>'
    # pier on piles
    for x in (0.9, 1.6, 2.3):
        for y in (1.35, 2.25):
            p = iso.P(x, y, 0)
            out += line((p[0], p[1] + 3), (p[0], p[1] - 0.25 * s), "#3A2616", 2)
    out += box(iso, 0.75, 1.2, 2.45, 2.4, 0.22, 0.3, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    for k in range(1, 9):
        x = 0.75 + 1.7 * k / 9
        out += line(iso.P(x, 1.2, 0.3), iso.P(x, 2.4, 0.3), "#6A4A2C", 0.45, 0.8)
    # moored boat with sail furled
    out += f'<g transform="translate(50 52) scale(0.62)">{barge_body(Iso(100, 46, 26), rng, sail=False, z=0.05)}</g>'
    # gate shed with giwa roof on shore
    out += stone_base(iso, 0.1, 0.1, 1.1, 0.9, 0, 0.15, rng, 1)
    out += timber_walls(iso, 0.2, 0.2, 1.0, 0.8, 0.15, 0.8, posts_x=2, posts_y=1, door=True)
    out += giwa_roof(iso, 0.2, 0.2, 1.0, 0.8, 0.82, 1.3, ov=0.22)
    # crates, rope coil, bell post
    p = iso.P(1.8, 0.5, 0)
    out += box(Iso(p[0], p[1] - 6, 7), 0, 0, 1, 1, 0, 1, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    out += box(Iso(p[0] + 9, p[1] - 2, 6), 0, 0, 1, 1, 0, 1, "url(#r3-deck)", "url(#r3-wood-lit)", "url(#r3-wood-sh)", "tx-wood")
    q = iso.P(2.6, 0.9, 0)
    out += "".join(f'<ellipse cx="{f(q[0])}" cy="{f(q[1] - i * 0.9)}" rx="{f(5 - i * 0.5)}" ry="{f(2.4 - i * 0.2)}" fill="none" stroke="{ROPE}" stroke-width="1"></ellipse>' for i in range(4))
    b = iso.P(2.6, 1.3, 0.3)
    out += line(b, (b[0], b[1] - 30), "url(#r3-pole)", 1.6) + line((b[0] - 4, b[1] - 28), (b[0] + 6, b[1] - 26), "#5A3E24", 1.2) + bell(b[0] + 3, b[1] - 23, 2.4)
    out += sack(*iso.P(0.6, 1.5, 0.3), 7, 6, "hemp") + onggi(*iso.P(1.2, 1.4, 0.3), 3.6)
    return sprite(out)


# ---------------------------------------------------------------- FX strip (viewBox 0 0 160 110)

def strip_ground(seed, wet=False):
    rng = random.Random(seed)
    return ground_patch(Iso(80, 26, 26), 0, 0, 3, 3, rng, n_tuft=26, wet=wet)


def dummy(cx, cy, burning=False, hit=False):
    out = shadow(cx + 4, cy + 1, 9, 3, 0.5)
    body = (line((cx, cy), (cx, cy - 26), "url(#r3-pole)", 1.8) + line((cx - 8, cy - 20), (cx + 8, cy - 18), "url(#r3-pole)", 1.4)
            + f'<path d="M{f(cx - 6)} {f(cy - 22)} Q{f(cx - 8)} {f(cy - 12)} {f(cx - 5)} {f(cy - 6)} L{f(cx + 5)} {f(cy - 5)} Q{f(cx + 8)} {f(cy - 12)} {f(cx + 6)} {f(cy - 21)} Z" fill="url(#r3-thatch-f)" filter="url(#r3-tx-straw)"></path>'
            + f'<circle cx="{f(cx)}" cy="{f(cy - 27)}" r="4" fill="url(#r3-thatch-f)" filter="url(#r3-tx-straw)"></circle>'
            + f'<path d="M{f(cx - 5)} {f(cy - 14)} Q{f(cx)} {f(cy - 12)} {f(cx + 5)} {f(cy - 13)}" stroke="{ROPE}" stroke-width="0.8" fill="none"></path>')
    out += sprite(body)
    if hit:
        out += line((cx - 14, cy - 18), (cx + 1, cy - 15), "#B08C5C", 0.9) + f'<path d="M-14 0 l-2 -1.4 m2 1.4 l-2 1.2" transform="translate({f(cx)} {f(cy - 18)})" stroke="#F6F0E0" stroke-width="0.8"></path>'
    if burning:
        out += flame(cx, cy - 8, 0.75)
        out += "".join(f'<circle cx="{f(cx + dx)}" cy="{f(cy - dy)}" r="0.7" fill="#FFD27A" filter="url(#r3-glow)"></circle>' for dx, dy in ((-6, 34), (4, 40), (9, 30), (-2, 46)))
    return out


def fire_trail(pts_, w=1.6):
    d = "M" + " L".join(f"{f(x)} {f(y)}" for x, y in pts_)
    return (f'<path d="{d}" stroke="#EE6A2E" stroke-width="{f(w * 2.4)}" fill="none" stroke-linecap="round" opacity="0.35" filter="url(#r3-glow4)"></path>'
            f'<path d="{d}" stroke="#FFC860" stroke-width="{f(w)}" fill="none" stroke-linecap="round" opacity="0.85"></path>'
            f'<path d="{d}" stroke="#FFF6D0" stroke-width="{f(w * 0.35)}" fill="none" stroke-linecap="round"></path>')


def archer_mini(x, y, s=0.62, lit=True):
    return fig(x, y, s, u_archer_fig() if lit else u_archer_fig())


def fx(n):
    out = strip_ground(400 + n)
    k, (bx, by) = shield_kite(36, 14, 14, 18, -14 + n * 2)
    out += sprite(k) + f'<path d="M{f(bx)} {f(by)} Q38 40 46 52" stroke="#E8DDB8" stroke-width="0.4" fill="none"></path>'
    out += k_place(46, 92, 0.42, archer_body(lit=(n == 1)))
    tx, ty = 128, 82
    if n == 1:
        out += glow(73, 54, 10, "fireglow", 1) + dummy(tx, ty)
    elif n == 2:
        out += fire_trail([(74, 54), (90, 46), (106, 48), (114, 56)]) + dummy(tx, ty)
        out += f'<path d="M114 56 l4 2.2 l-4.6 0.6 z" fill="#C8C8C0"></path>'
    elif n == 3:
        for dy, l in ((0, 1), (-6, 0.82), (6, 0.66)):
            out += fire_trail([(74, 54 + dy * 0.3), (90 + 8 * l, 44 + dy), (110 + 6 * l, 46 + dy), (118, 60 + dy * 0.6)], 1.2)
        out += dummy(tx, ty)
    else:
        out += dummy(tx, ty, burning=True, hit=True)
        out += f'<ellipse cx="{tx}" cy="{ty + 2}" rx="12" ry="4" fill="#1A0E06" opacity="0.6"></ellipse>'
        for i in range(3):
            out += f'<ellipse cx="{tx + i * 3}" cy="{ty - 46 - i * 12}" rx="{8 + i * 3}" ry="{6 + i * 2}" fill="url(#r3-smokeg)" filter="url(#r3-smoke)" opacity="0.7"></ellipse>'
    return out


def fx_death():
    out = strip_ground(410)
    # cut string, kite tumbling away up-right
    k, _ = shield_kite(120, 22, 16, 21, 38)
    out += sprite(k, "dead") if False else f'<g opacity="0.85">{sprite(k)}</g>'
    out += f'<path d="M110 32 q-6 10 -2 18 q4 8 -4 14" stroke="#E8DDB8" stroke-width="0.4" fill="none" stroke-dasharray="2 1.4"></path>'
    out += f'<path d="M104 64 l3 -2 M104 64 l-1 -3.4" stroke="#FFF" stroke-width="0.7"></path>'
    out += f'<path d="M96 70 q-14 4 -28 12" stroke="#E8DDB8" stroke-width="0.4" fill="none"></path>'
    # fallen archer (greyed), dropped bow
    out += f'<g filter="url(#r3-dead)"><g transform="translate(66 94) rotate(-80) scale(0.38)">{archer_body(draw=False, lit=False)}</g></g>'
    out += shadow(60, 94, 22, 4, 0.5)
    out += f'<path d="M86 92 Q96 86 104 92" stroke="#3A2414" stroke-width="1.6" fill="none"></path>'
    out += "".join(f'<path d="M{80 + i * 5} {84 - i} q2 -3 0 -6" stroke="#EFE6D2" stroke-width="0.6" fill="none" opacity="0.5"></path>' for i in range(3))
    return out


def fx_wreck():
    rng = random.Random(420)
    out = strip_ground(421)
    iso = Iso(78, 34, 16)
    # charred stall frame + collapsed awning
    out += f'<g filter="url(#r3-dead)">'
    for p in (iso.P(0.6, 0.6), iso.P(2.2, 0.8)):
        out += line(p, (p[0] + 3, p[1] - 22), "#2A1A10", 1.8)
    out += line(iso.P(1.0, 2.0), (iso.P(1.0, 2.0)[0] - 5, iso.P(1.0, 2.0)[1] - 12), "#2A1A10", 1.8)
    out += "</g>"
    aw = [iso.P(0.4, 0.8, 0.8), iso.P(2.6, 1.0, 1.2), iso.P(2.8, 2.6, 0.05), iso.P(0.5, 2.4, 0.05)]
    out += poly(aw, "#8A6A3A", ' filter="url(#r3-tx-cloth)" opacity="0.92"')
    for i in range(6):
        a = lerp(aw[0], aw[3], i / 6)
        b = lerp(aw[1], aw[2], i / 6)
        out += poly([a, b, lerp(aw[1], aw[2], (i + 0.5) / 6), lerp(aw[0], aw[3], (i + 0.5) / 6)], INDIGO, ' opacity="0.6"')
    out += f'<path d="M{f(aw[1][0])} {f(aw[1][1])} l-6 8 l4 2 l-6 10" stroke="#120A04" stroke-width="2" fill="none"></path>'
    # broken jars
    for x, y in ((50, 88), (62, 92), (104, 92)):
        out += (f'<path d="M{x - 4} {y} q-1 -5 2 -7 l2 3 l2 -3 q3 3 2 7 z" fill="url(#r3-onggi)"></path>'
                f'<path d="M{x + 6} {y + 2} l3 -2 l1 3 z M{x - 7} {y + 1} l2 -3 l2 2 z" fill="#5A3A20"></path>')
    out += flame(92, 66, 0.7) + flame(70, 74, 0.5)
    for i in range(4):
        out += f'<ellipse cx="{f(84 + i * 4)}" cy="{f(36 - i * 9)}" rx="{14 + i * 3}" ry="{8 + i * 2}" fill="url(#r3-smokeg)" filter="url(#r3-smoke)" opacity="{0.85 - i * 0.15}"></ellipse>'
    for i in range(12):
        out += f'<circle cx="{f(rng.uniform(56, 120))}" cy="{f(rng.uniform(20, 70))}" r="{f(rng.uniform(0.4, 0.9))}" fill="#FFB050" filter="url(#r3-glow)"></circle>'
    return out


def fx_harvest():
    out = strip_ground(430)
    out += rock_with_ore(104, 80, 0.85)
    porter = porter_body('swing')
    out += k_place(56, 96, 0.4, porter)
    # chips + sparkles flying to the 지게
    for i, (x, y) in enumerate(((86, 60), (80, 52), (92, 54), (74, 46))):
        out += f'<polygon points="{x},{y} {x + 2},{y - 2.4} {x + 3.4},{y + 0.6}" fill="url(#r3-ore)" filter="url(#r3-glow)"></polygon>'
    out += f'<path d="M92 62 Q80 30 60 38" stroke="{ORE_G}" stroke-width="0.6" fill="none" stroke-dasharray="1.4 2" opacity="0.8"></path>'
    return out


def fx_dropoff():
    rng = random.Random(440)
    out = strip_ground(441)
    iso = Iso(76, 30, 15)
    out += stall(iso, 0.6, 0.4, 2.6, 1.6, ["#E8E1C8", OCHRE], rng, "ore")
    out += k_place(120, 96, 0.4, porter_body('unload'))
    out += sack(92, 92, 8, 6, "hemp", glow_ore=True)
    out += (f'<text x="136" y="30" font-size="13" fill="{ORE_G}" font-family="IBM Plex Mono, monospace" font-weight="600" filter="url(#r3-glow)">+8</text>'
            f'<path d="M128 34 v12 m-3.4 -3.4 l3.4 3.4 l3.4 -3.4" stroke="{ORE_G}" stroke-width="1.4" fill="none"></path>')
    return out


# ---------------------------------------------------------------- crest + chrome

def emblem():
    ring = "".join(f'<circle cx="{f(50 + math.cos(a) * 43)}" cy="{f(50 + math.sin(a) * 43)}" r="1.3" fill="url(#r3-brass)"></circle>'
                   for a in [k * math.pi / 16 for k in range(32)])
    clouds = "".join(f'<path d="M{f(50 + math.cos(a) * 37)} {f(50 + math.sin(a) * 37)} m-3 0 a3 3 0 1 1 6 0 a2 2 0 1 1 -4 0" stroke="{OCHRE}" stroke-width="0.8" fill="none" opacity="0.7"></path>'
                     for a in [k * math.pi / 4 + math.pi / 8 for k in range(8)])
    k, (bx, by) = shield_kite(50, 44, 30, 40, 0)
    return (f'<circle cx="50" cy="50" r="48" fill="url(#r3-lacquer)" stroke="{OCHRE}" stroke-width="1.8"></circle>'
            f'<circle cx="50" cy="50" r="40" fill="#1E140C" stroke="#7A5A28" stroke-width="2.4"></circle>'
            + ring + clouds
            + f'<polygon points="18,74 50,16 82,74" fill="none" stroke="{OCHRE}" stroke-width="1.2" opacity="0.6"></polygon>'
            + sprite(k)
            + line((bx, by), (50, 78), "#E8DDB8", 0.6) + bell(50, 80, 3.4))


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


def unit_card(code, name, role, desc, fp, fn):
    return f"""
    <article style="{FRAME}">{corner_svgs()}
      <div style="height: 196px; {ART}">{svg('0 0 200 160', fn(), 250, 200, name)}</div>
      <div style="padding: 7px 11px 9px; display: flex; flex-direction: column; gap: 2px; {HANJI}">
        <div style="display: flex; justify-content: space-between; align-items: baseline">
          <h3 style="margin: 0; {SERIF}; font-size: 20px; font-weight: 800; color: #1E140A">{name}</h3>
          <span style="{MONO}; font-size: 11px; font-weight: 600; color: #8A2A18">{code} · {fp}</span>
        </div>
        <span style="font-size: 12px; font-weight: 600; color: #5A4428">{role}</span>
        <p style="margin: 1px 0 0; font-size: 12px; line-height: 1.45; color: #3A2C1C">{desc}</p>
      </div>
    </article>"""


def bld_card(code, name, role, fp, fn):
    return f"""
    <article style="{FRAME}">{corner_svgs()}
      <div style="height: 150px; {ART}">{svg('0 0 200 160', fn(), 188, 150, name)}</div>
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
    ("U-C1", "짐꾼", "Porter · 일꾼", "지게에 귀화석과 보따리를 지고 장터로 나릅니다. 패랭이 목화송이가 상단 표식.", "1×1", u_porter),
    ("U-C2", "호위무사", "Escort · 근접", "삿갓에 환도, 놋징 박은 붉은 전복, 등에 꽂은 삼각 상단기. 행렬을 지키는 근접 호위.", "1×1", u_escort),
    ("U-C3", "방패연 궁수", "Kite Archer · 원거리", "허리 얼레에 맨 방패연이 시야를 넓히고, 그 아래서 불화살을 쏩니다.", "1×1", u_archer),
    ("U-C4", "황포 돛배", "Sail Barge · 중장", "누런 돛 부양 강배. 이물 총통, 갑판엔 옹기와 피륙.", "2×2", u_barge),
    ("U-C5", "정찰 연", "Scout Kite · 공중", "꼬리 단 큰 방패연과 망꾼 바구니. 그림자가 멀리 떨어집니다.", "1×1 공중", u_scout),
]

BUILDINGS = [
    ("B-C1", "장터 본영", "본진", "3×3", b_market),
    ("B-C2", "산기 풍로", "산기 채취", "2×2", b_bellows),
    ("B-C3", "연 공방", "생산", "2×2", b_loft),
    ("B-C4", "객주", "연구", "2×2", b_inn),
    ("B-C5", "솟대", "보급 +8", "1×1", b_sotdae),
    ("B-C6", "봉수대", "방어", "1×1", b_beacon),
    ("B-C7", "나루터", "중장 생산", "3×2", b_dock),
]


def page():
    units = "".join(unit_card(*u) for u in UNITS)
    blds = "".join(bld_card(*b) for b in BUILDINGS)
    strip = (strip_cell("공격 1 · 점화", "연 아래 불화살", fx(1))
             + strip_cell("공격 2 · 발사", "불꼬리 비행", fx(2))
             + strip_cell("공격 3 · 연사", "3연발", fx(3))
             + strip_cell("공격 4 · 착탄", "불붙음 + 연기", fx(4))
             + strip_cell("유닛 사망", "연줄 끊김", fx_death())
             + strip_cell("건물 파괴", "차일 붕괴", fx_wreck())
             + strip_cell("채집", "곡괭이로 귀화석", fx_harvest())
             + strip_cell("자원 반납", "장터 하역", fx_dropoff()))
    swatches = "".join(swatch(c, l) for c, l in [(OCHRE, "E2B13C"), (OCHRE_L, "F2D58A"), (OCHRE_D, "3A2E14"), (INDIGO, "3B4E8C"), (CLOTH, "E8E1C8"), (ROPE, "8A7A5A"), (RED, "C8432B")])
    title_style = (f"margin: 0; {SERIF}; font-weight: 800; font-size: 46px; line-height: 1.05; "
                   "background: linear-gradient(180deg, #FFF4CC 0%, #F2D58A 35%, #C9902E 70%, #8A5A16 100%); "
                   "-webkit-background-clip: text; background-clip: text; color: transparent; "
                   "filter: drop-shadow(0 2px 0 #1A0E06) drop-shadow(0 0 1px #000)")
    return f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>보부상 연단 종족 시트</title>
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
<div style="position: relative; width: 1440px; height: 960px; box-sizing: border-box; background: radial-gradient(ellipse at 50% 0%, #2A2216 0%, {GROUND} 60%, #0E0C08 100%); color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; padding: 20px 40px; display: flex; flex-direction: column; gap: 10px; overflow: hidden">

  <svg width="0" height="0" style="position: absolute; width: 0; height: 0" aria-hidden="true" focusable="false"><defs>{DEFS}{K.defs_svg()}</defs></svg>

  <header style="display: flex; align-items: center; gap: 18px">
    {svg('0 0 100 100', emblem(), 88, 88, '보부상 연단 문장')}
    <div style="display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0">
      <span style="{MONO}; font-size: 12px; letter-spacing: 0.14em; color: {MUTED}">RACE CODEX R3 · 삼파전 SAMPAJEON · 2.5D ISOMETRIC</span>
      <div style="display: flex; align-items: baseline; gap: 14px"><h1 style="{title_style}">보부상 연단</h1><span style="{MONO}; font-size: 19px; font-weight: 600; letter-spacing: 0.2em; color: {OCHRE}">KITE GUILD</span></div>
      <span style="font-size: 13px; color: #D8CDB4">바람과 연, 황포 돛으로 산을 넘는 행상 조합. 지게와 패랭이, 방울 소리 — 장이 서는 곳이 곧 진지.</span>
    </div>
    <div style="display: flex; flex-direction: column; gap: 8px; align-items: flex-end">
      <div style="display: flex; gap: 8px">{swatches}</div>
      <span style="display: flex; align-items: center; gap: 8px; font-size: 12px; color: {MUTED}"><svg width="26" height="14" viewBox="0 0 26 14" aria-hidden="true"><polygon points="6,0 12,14 0,14" fill="{OCHRE}"></polygon><rect x="15" y="1" width="10" height="13" fill="{CLOTH}"></rect><circle cx="20" cy="8" r="2.4" fill="{GROUND}"></circle></svg>형태 언어: 삼각 상단기 · 방패연(가운데 구멍) · 황포 돛</span>
    </div>
  </header>
  <div style="display: flex; height: 5px; box-shadow: 0 1px 0 #000"><span style="flex: 3; background: {RED}"></span><span style="flex: 2; background: {GREEN}"></span><span style="flex: 2; background: {OCHRE}"></span><span style="flex: 1; background: {BLUE}"></span><span style="flex: 6; background: linear-gradient(90deg, #4A2A16, transparent)"></span></div>

  <section style="display: flex; flex-direction: column; gap: 7px">
    {section_title("유닛", "5종 · 45° 내려다본 2.5D · 몸 방향 = 이동, 손의 무기 = 공격")}
    <div style="display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px">{units}
    </div>
  </section>

  <section style="display: flex; flex-direction: column; gap: 7px">
    {section_title("건물", "7종 · 2:1 아이소메트릭 · 서남 벽 밝게, 동남 벽 그늘, 기와·초가 지붕")}
    <div style="display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 10px">{blds}
    </div>
  </section>

  <section style="display: flex; flex-direction: column; gap: 7px">
    {section_title("연출", "방패연 궁수 불화살 4프레임 · 사망 · 파괴 · 채집 · 반납")}
    <div style="display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 9px">{strip}
    </div>
  </section>
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


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(page())
    print("wrote", os.path.abspath(OUT), os.path.getsize(OUT))
