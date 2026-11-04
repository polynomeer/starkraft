#!/usr/bin/env python3
"""Generate design2/project/Match.dc.html — 삼파전 in-game HUD, high-fidelity 2.5D pass (id prefix s3-).

The battlefield is a 2:1 isometric painted scene. Everything repetitive (roof tiles,
stones, grass, rice) is generated with a seeded RNG so the output is deterministic.
"""
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "design2", "project", "Match.dc.html")
rnd = random.Random(1592)

# palette (SPEC.md)
GROUND, PANEL, RAISED, LINE, INK, MUTED = "#16140F", "#201D17", "#2A2620", "#4A4236", "#EFE6D2", "#A89E8A"
RED, YEL, BLUE_O, GRN = "#C8432B", "#E2B13C", "#2F6E8E", "#3E8F7A"
R1, R1L, BRASS = "#D9532E", "#F09A6E", "#C9A35A"
R2, R2L, R2D, HIDE = "#4FB3E8", "#A8DDF5", "#12222E", "#2B2F55"
ORE, ORE_G, GAS, FOOD = "#9FE3D0", "#CFF5EA", "#B79AE0", "#E8DDBF"
ALERT, OK = "#E5604D", "#8CC79A"
OUTL = 'stroke="#120d08" stroke-opacity="0.62"'

FW, FH = 1440, 700  # battlefield svg size
S = 32.0            # half tile width in px (tile = 64x32)


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def P(pts):
    return " ".join(f"{f(x)},{f(y)}" for x, y in pts)


def hx(c):
    c = c.lstrip("#")
    if len(c) == 3:
        c = "".join(ch * 2 for ch in c)
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    ra, rb = hx(a), hx(b)
    return "#" + "".join(f"{int(round(x + (y - x) * t)):02X}" for x, y in zip(ra, rb))


def dk(c, t):
    return mix(c, "#0A0806", t)


def lt(c, t):
    return mix(c, "#FFF6E4", t)


def iso(ax, ay, u, v, z=0.0):
    return (ax + (u - v) * S, ay + (u + v) * S / 2 - z)


def lerp(p, q, t):
    return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)


def hull(points):
    pts = sorted(set((round(x, 2), round(y, 2)) for x, y in points))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0:
            lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0:
            up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


def poly(pts, fill, extra="", outline=True):
    o = f' {OUTL} stroke-width="0.7" stroke-linejoin="round"' if outline else ""
    return f'<polygon points="{P(pts)}" fill="{fill}"{o} {extra}/>'


def in_poly(x, y, pts):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
            inside = not inside
        j = i
    return inside


# ---------------------------------------------------------------- defs
DEFS = f"""
<defs>
  <filter id="s3-sh" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2.4"/></filter>
  <filter id="s3-sh1" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.1"/></filter>
  <filter id="s3-sh2" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="5"/></filter>
  <filter id="s3-glow" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
  <filter id="s3-glow2" x="-150%" y="-150%" width="400%" height="400%"><feGaussianBlur stdDeviation="6" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
  <filter id="s3-blur2" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2"/></filter>
  <filter id="s3-blur4" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="4"/></filter>
  <filter id="s3-blur9" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="9"/></filter>
  <filter id="s3-mot1" filterUnits="userSpaceOnUse" x="0" y="0" width="{FW}" height="{FH}"><feTurbulence type="fractalNoise" baseFrequency="0.0065" numOctaves="4" seed="3"/><feColorMatrix values="0 0 0 0 0.14  0 0 0 0 0.13  0 0 0 0 0.06  2.6 0 0 0 -1.15"/></filter>
  <filter id="s3-mot2" filterUnits="userSpaceOnUse" x="0" y="0" width="{FW}" height="{FH}"><feTurbulence type="fractalNoise" baseFrequency="0.011" numOctaves="3" seed="8"/><feColorMatrix values="0 0 0 0 0.62  0 0 0 0 0.52  0 0 0 0 0.26  0 2.4 0 0 -1.12"/></filter>
  <filter id="s3-mot3" filterUnits="userSpaceOnUse" x="0" y="0" width="{FW}" height="{FH}"><feTurbulence type="fractalNoise" baseFrequency="0.55" numOctaves="2" seed="12"/><feColorMatrix values="0 0 0 0 0.05  0 0 0 0 0.04  0 0 0 0 0.02  0 0 1.2 0 -0.42"/></filter>
  <filter id="s3-dirt" x="-6%" y="-6%" width="112%" height="112%"><feGaussianBlur in="SourceGraphic" stdDeviation="2.2" result="sg"/><feTurbulence type="fractalNoise" baseFrequency="0.08" numOctaves="3" seed="7" result="n"/><feColorMatrix in="n" values="0 0 0 0 0.18  0 0 0 0 0.13  0 0 0 0 0.08  1.9 0 0 0 -0.82" result="d"/><feComposite in="d" in2="sg" operator="in" result="d2"/><feTurbulence type="fractalNoise" baseFrequency="0.03" numOctaves="2" seed="17" result="n2"/><feColorMatrix in="n2" values="0 0 0 0 0.86  0 0 0 0 0.74  0 0 0 0 0.52  0 1.6 0 0 -0.82" result="l"/><feComposite in="l" in2="sg" operator="in" result="l2"/><feMerge><feMergeNode in="sg"/><feMergeNode in="l2"/><feMergeNode in="d2"/></feMerge></filter>
  <filter id="s3-tex" x="-4%" y="-4%" width="108%" height="108%"><feTurbulence type="fractalNoise" baseFrequency="0.22" numOctaves="2" seed="4" result="n"/><feColorMatrix in="n" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  1.4 0 0 0 -0.62" result="d"/><feComposite in="d" in2="SourceGraphic" operator="in" result="d2"/><feMerge><feMergeNode in="SourceGraphic"/><feMergeNode in="d2"/></feMerge></filter>
  <filter id="s3-water" x="-4%" y="-4%" width="108%" height="108%"><feTurbulence type="fractalNoise" baseFrequency="0.012 0.09" numOctaves="2" seed="31" result="n"/><feColorMatrix in="n" values="0 0 0 0 0.86  0 0 0 0 0.94  0 0 0 0 0.96  0 0 2.4 0 -1.38" result="w"/><feComposite in="w" in2="SourceGraphic" operator="in" result="w2"/><feMerge><feMergeNode in="SourceGraphic"/><feMergeNode in="w2"/></feMerge></filter>
  <filter id="s3-ink" x="-30%" y="-30%" width="160%" height="160%"><feTurbulence type="fractalNoise" baseFrequency="0.012" numOctaves="4" seed="7" result="n"/><feDisplacementMap in="SourceGraphic" in2="n" scale="120" xChannelSelector="R" yChannelSelector="G" result="d"/><feGaussianBlur in="d" stdDeviation="7"/></filter>
  <filter id="s3-inkwash" filterUnits="userSpaceOnUse" x="0" y="0" width="{FW}" height="{FH}"><feTurbulence type="fractalNoise" baseFrequency="0.018 0.05" numOctaves="4" seed="21"/><feColorMatrix values="0 0 0 0 0.06  0 0 0 0 0.055  0 0 0 0 0.045  0 0 1.8 0 -0.6"/></filter>
  <filter id="s3-mist" x="-60%" y="-60%" width="220%" height="220%"><feTurbulence type="fractalNoise" baseFrequency="0.035" numOctaves="2" seed="5" result="n"/><feDisplacementMap in="SourceGraphic" in2="n" scale="24" xChannelSelector="R" yChannelSelector="G" result="d"/><feGaussianBlur in="d" stdDeviation="4"/></filter>
  <filter id="s3-smoke" x="-60%" y="-60%" width="220%" height="220%"><feTurbulence type="fractalNoise" baseFrequency="0.08" numOctaves="2" seed="9" result="n"/><feDisplacementMap in="SourceGraphic" in2="n" scale="10" xChannelSelector="R" yChannelSelector="G" result="d"/><feGaussianBlur in="d" stdDeviation="1.6"/></filter>
  <filter id="s3-woodf" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.004 0.16" numOctaves="3" seed="44" result="n"/><feColorMatrix in="n" values="0.42 0 0 0 0.03  0.25 0 0 0 0.02  0.13 0 0 0 0.01  0 0 0 0 1" result="w"/><feComposite in="w" in2="SourceGraphic" operator="in"/></filter>
  <filter id="s3-parch" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.035" numOctaves="4" seed="61" result="n"/><feColorMatrix in="n" values="0.32 0 0 0 0.62  0.32 0 0 0 0.54  0.3 0 0 0 0.38  0 0 0 0 1" result="p"/><feComposite in="p" in2="SourceGraphic" operator="in"/></filter>

  <linearGradient id="s3-eave" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity="0.62"/><stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient>
  <linearGradient id="s3-roofF" x1="0" y1="1" x2="0.2" y2="0"><stop offset="0" stop-color="#4C535D"/><stop offset="0.6" stop-color="#5E6670"/><stop offset="1" stop-color="#727B86"/></linearGradient>
  <linearGradient id="s3-roofR" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#23272D"/><stop offset="1" stop-color="#363C44"/></linearGradient>
  <linearGradient id="s3-roofB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6C7480"/><stop offset="1" stop-color="#454C55"/></linearGradient>
  <linearGradient id="s3-hanji" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4E7C2"/><stop offset="1" stop-color="#D8C595"/></linearGradient>
  <linearGradient id="s3-hanjiG" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE4A0"/><stop offset="1" stop-color="#E8A955"/></linearGradient>
  <linearGradient id="s3-pillar" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#A8462C"/><stop offset="0.5" stop-color="#7A2E1E"/><stop offset="1" stop-color="#4A1A10"/></linearGradient>
  <radialGradient id="s3-coat" cx="0.32" cy="0.28" r="0.8"><stop offset="0" stop-color="#F08A62"/><stop offset="0.5" stop-color="#C2402A"/><stop offset="1" stop-color="#5E1A10"/></radialGradient>
  <radialGradient id="s3-skin" cx="0.35" cy="0.3" r="0.8"><stop offset="0" stop-color="#F6D6AE"/><stop offset="0.6" stop-color="#D6A574"/><stop offset="1" stop-color="#8A5E3A"/></radialGradient>
  <radialGradient id="s3-brim" cx="0.38" cy="0.3" r="0.8"><stop offset="0" stop-color="#57514A"/><stop offset="0.55" stop-color="#211E1A"/><stop offset="1" stop-color="#0A0908"/></radialGradient>
  <linearGradient id="s3-pants" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#F2EBD8"/><stop offset="1" stop-color="#A89E86"/></linearGradient>
  <linearGradient id="s3-gun" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9A7048"/><stop offset="1" stop-color="#3E2614"/></linearGradient>
  <linearGradient id="s3-iron" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7C7E84"/><stop offset="0.5" stop-color="#3A3B40"/><stop offset="1" stop-color="#18181B"/></linearGradient>
  <linearGradient id="s3-brass" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FBE7A6"/><stop offset="0.45" stop-color="{BRASS}"/><stop offset="1" stop-color="#5E4216"/></linearGradient>
  <linearGradient id="s3-brassV" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FBE7A6"/><stop offset="0.5" stop-color="{BRASS}"/><stop offset="1" stop-color="#4E3610"/></linearGradient>
  <linearGradient id="s3-woodL" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#A57A4C"/><stop offset="1" stop-color="#5E3E22"/></linearGradient>
  <linearGradient id="s3-woodD" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6A4628"/><stop offset="1" stop-color="#2E1E12"/></linearGradient>
  <radialGradient id="s3-dskin" cx="0.32" cy="0.28" r="0.85"><stop offset="0" stop-color="#8C9AE8"/><stop offset="0.45" stop-color="#4C59A8"/><stop offset="1" stop-color="#1C2050"/></radialGradient>
  <radialGradient id="s3-dskin2" cx="0.32" cy="0.28" r="0.85"><stop offset="0" stop-color="#7FB0E0"/><stop offset="0.5" stop-color="#3D6A9E"/><stop offset="1" stop-color="#16294A"/></radialGradient>
  <linearGradient id="s3-tiger" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F2A84A"/><stop offset="1" stop-color="#A85A1A"/></linearGradient>
  <linearGradient id="s3-horn" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#8A6A2E"/><stop offset="0.6" stop-color="#D9BC78"/><stop offset="1" stop-color="#FFF0C8"/></linearGradient>
  <linearGradient id="s3-club" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#B58A56"/><stop offset="0.5" stop-color="#7A5232"/><stop offset="1" stop-color="#3E2614"/></linearGradient>
  <radialGradient id="s3-bfire" cx="0.5" cy="0.62" r="0.6"><stop offset="0" stop-color="#FFFFFF"/><stop offset="0.25" stop-color="{R2L}"/><stop offset="0.65" stop-color="{R2}"/><stop offset="1" stop-color="#1F6FB8" stop-opacity="0"/></radialGradient>
  <radialGradient id="s3-ball" cx="0.42" cy="0.4" r="0.6"><stop offset="0" stop-color="#FFFFFF"/><stop offset="0.3" stop-color="{R2L}"/><stop offset="0.7" stop-color="{R2}" stop-opacity="0.85"/><stop offset="1" stop-color="{R2}" stop-opacity="0"/></radialGradient>
  <radialGradient id="s3-fire" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFFBE8"/><stop offset="0.22" stop-color="#FFD98A"/><stop offset="0.5" stop-color="#F28A36"/><stop offset="0.78" stop-color="#B8321C" stop-opacity="0.65"/><stop offset="1" stop-color="#6A1A0A" stop-opacity="0"/></radialGradient>
  <radialGradient id="s3-smk" cx="0.4" cy="0.35" r="0.65"><stop offset="0" stop-color="#E8E2D6"/><stop offset="0.6" stop-color="#9C958A"/><stop offset="1" stop-color="#5A554E" stop-opacity="0"/></radialGradient>
  <radialGradient id="s3-smkD" cx="0.4" cy="0.35" r="0.65"><stop offset="0" stop-color="#6E6860"/><stop offset="0.7" stop-color="#36322C"/><stop offset="1" stop-color="#1A1814" stop-opacity="0"/></radialGradient>
  <linearGradient id="s3-trail" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#F28A36" stop-opacity="0"/><stop offset="0.7" stop-color="#FFC266" stop-opacity="0.8"/><stop offset="1" stop-color="#FFF6D8"/></linearGradient>
  <linearGradient id="s3-btrail" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{R2}" stop-opacity="0"/><stop offset="0.7" stop-color="{R2L}" stop-opacity="0.75"/><stop offset="1" stop-color="#FFFFFF"/></linearGradient>
  <radialGradient id="s3-oreglow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="{ORE_G}" stop-opacity="0.7"/><stop offset="0.45" stop-color="{ORE}" stop-opacity="0.25"/><stop offset="1" stop-color="{ORE}" stop-opacity="0"/></radialGradient>
  <linearGradient id="s3-crysL" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F2FFFB"/><stop offset="0.5" stop-color="{ORE}"/><stop offset="1" stop-color="#4FA593"/></linearGradient>
  <linearGradient id="s3-crysD" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6CC2AE"/><stop offset="1" stop-color="#1E5A4E"/></linearGradient>
  <radialGradient id="s3-gasglow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#EDE2FC" stop-opacity="0.85"/><stop offset="0.45" stop-color="{GAS}" stop-opacity="0.4"/><stop offset="1" stop-color="{GAS}" stop-opacity="0"/></radialGradient>
  <radialGradient id="s3-lairglow" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="{R2L}" stop-opacity="0.5"/><stop offset="1" stop-color="{R2}" stop-opacity="0"/></radialGradient>
  <linearGradient id="s3-waterG" x1="0" y1="0" x2="1" y2="0.3"><stop offset="0" stop-color="#3F7F8C"/><stop offset="0.5" stop-color="#2A5A68"/><stop offset="1" stop-color="#1D4250"/></linearGradient>
  <linearGradient id="s3-paddyW" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#9FB9B4"/><stop offset="0.5" stop-color="#6E8F8E"/><stop offset="1" stop-color="#4A6A6C"/></linearGradient>
  <linearGradient id="s3-rice" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E2C25E"/><stop offset="1" stop-color="#A9822E"/></linearGradient>
  <linearGradient id="s3-fogfade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#0C0B08" stop-opacity="0"/><stop offset="1" stop-color="#0C0B08" stop-opacity="0.9"/></linearGradient>
  <linearGradient id="s3-gly" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF1C2"/><stop offset="0.5" stop-color="#E9C46A"/><stop offset="1" stop-color="#A97A26"/></linearGradient>
  <linearGradient id="s3-glyR" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFC4B0"/><stop offset="1" stop-color="{ALERT}"/></linearGradient>
</defs>"""

# ---------------------------------------------------------------- drawables (depth sorted by ground y)
DRAW = []


def add(y, svg):
    DRAW.append((y, len(DRAW), svg))


# ---------------------------------------------------------------- generic iso pieces
def box(ax, ay, a, b, h, top, left, right, z0=0.0, extra_top=""):
    n, e, s, w = (iso(ax, ay, 0, 0, z0 + h), iso(ax, ay, a, 0, z0 + h),
                  iso(ax, ay, a, b, z0 + h), iso(ax, ay, 0, b, z0 + h))
    s0, w0, e0 = iso(ax, ay, a, b, z0), iso(ax, ay, 0, b, z0), iso(ax, ay, a, 0, z0)
    return (poly([w0, s0, s, w], left) + poly([s0, e0, e, s], right) + poly([n, e, s, w], top, extra_top))


def face_pt(ax, ay, a, b, side, t, z):
    return iso(ax, ay, t * a, b, z) if side == "L" else iso(ax, ay, a, t * b, z)


def face_quad(ax, ay, a, b, side, t0, t1, z0, z1):
    return [face_pt(ax, ay, a, b, side, t0, z0), face_pt(ax, ay, a, b, side, t1, z0),
            face_pt(ax, ay, a, b, side, t1, z1), face_pt(ax, ay, a, b, side, t0, z1)]


def stones_face(ax, ay, a, b, side, z0, z1, base, rows=2):
    out = []
    length = a if side == "L" else b
    rh = (z1 - z0) / rows
    for r in range(rows):
        t = rnd.uniform(-0.05, 0.0)
        while t < 1:
            step = rnd.uniform(0.35, 0.8) / length
            t1 = min(1, t + step)
            col = mix(base, "#FFF4DC" if rnd.random() < .5 else "#1A140E", rnd.uniform(0.02, 0.2))
            q = face_quad(ax, ay, a, b, side, max(0, t) + 0.006, t1 - 0.006, z0 + r * rh + 0.8, z0 + (r + 1) * rh - 0.6)
            out.append(f'<polygon points="{P(q)}" fill="{col}" stroke="#1a140e" stroke-opacity="0.55" stroke-width="0.7"/>')
            p0, p1 = q[3], q[2]
            out.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1] + 0.8)}" x2="{f(p1[0])}" y2="{f(p1[1] + 0.8)}" stroke="#FFF6E0" stroke-opacity="0.22" stroke-width="0.7"/>')
            t = t1
    return "".join(out)


def cast_shadow(ax, ay, a, b, height, grow=0.5, op=0.45):
    base = [iso(ax, ay, -grow, -grow), iso(ax, ay, a + grow, -grow), iso(ax, ay, a + grow, b + grow), iso(ax, ay, -grow, b + grow)]
    dx, dy = height * 0.78, height * 0.30
    pts = hull(base + [(x + dx, y + dy) for x, y in base])
    return f'<polygon points="{P(pts)}" fill="#0b0805" opacity="{op}" filter="url(#s3-sh2)"/>'


# ---------------------------------------------------------------- hanok (tiled-roof building)
def roof(ax, ay, a, b, zr, rh, o=0.55, up=6.0, lit="url(#s3-roofF)", shade="url(#s3-roofR)", tile_lit="#8E97A2"):
    r0 = b / 2.0
    ridge0, ridge1 = (r0, b / 2.0), (a - r0, b / 2.0)

    def upf(t):
        return up * (abs(2 * t - 1) ** 5)

    def edge(u0, v0, u1, v1, n=36):
        return [iso(ax, ay, u0 + (u1 - u0) * i / n, v0 + (v1 - v0) * i / n, zr + upf(i / n)) for i in range(n + 1)]
    R0 = iso(ax, ay, ridge0[0], ridge0[1], zr + rh)
    R1 = iso(ax, ay, ridge1[0], ridge1[1], zr + rh)
    back = edge(a + o, -o, -o, -o)
    left = edge(-o, -o, -o, b + o)
    front = edge(-o, b + o, a + o, b + o)
    right = edge(a + o, b + o, a + o, -o)
    g = []
    g.append(poly(back + [R0, R1], "url(#s3-roofB)"))
    g.append(poly(left + [R0], "#5C646E"))
    # front slope
    g.append(poly(front + [R1, R0], lit))
    # tile rows on the front slope
    n = int((a + 2 * o) * 6.2)
    for k in range(n):
        t = (k + 0.5) / n
        ue = -o + t * (a + 2 * o)
        E = iso(ax, ay, ue, b + o, zr + upf(t))
        if ue < r0:
            s = (ue + o) / (r0 + o)
        elif ue > a - r0:
            s = (a + o - ue) / (r0 + o)
        else:
            s = 1.0
        U = iso(ax, ay, ue, b + o - s * (b / 2 + o), zr + s * rh)
        g.append(f'<line x1="{f(E[0] + 1.4)}" y1="{f(E[1] + 0.4)}" x2="{f(U[0] + 1.4)}" y2="{f(U[1] + 0.4)}" stroke="#1E2228" stroke-width="1.5" stroke-opacity="0.75"/>')
        g.append(f'<line x1="{f(E[0])}" y1="{f(E[1])}" x2="{f(U[0])}" y2="{f(U[1])}" stroke="{tile_lit}" stroke-width="1.7" stroke-linecap="round" stroke-opacity="0.85"/>')
        g.append(f'<ellipse cx="{f(E[0])}" cy="{f(E[1] + 0.6)}" rx="1.5" ry="1.25" fill="#A9B1BA" stroke="#16191d" stroke-width="0.5"/>')
    # horizontal courses (tile overlap shadows) on the front slope
    for j in range(1, 6):
        s = j / 6
        pts = []
        for i in range(21):
            t = i / 20
            ue = -o + t * (a + 2 * o)
            lim = 1.0 if r0 <= ue <= a - r0 else ((ue + o) / (r0 + o) if ue < r0 else (a + o - ue) / (r0 + o))
            if s <= lim:
                pts.append(iso(ax, ay, ue, b + o - s * (b / 2 + o), zr + s * rh + upf(t) * (1 - s)))
        if len(pts) > 1:
            g.append(f'<polyline points="{P(pts)}" fill="none" stroke="#14171b" stroke-opacity="0.35" stroke-width="0.8"/>')
    # right hip (shade)
    g.append(poly(right + [R1], shade))
    n2 = int((b + 2 * o) * 6.2)
    for k in range(n2):
        t = (k + 0.5) / n2
        ve = b + o - t * (b + 2 * o)
        E = iso(ax, ay, a + o, ve, zr + upf(t))
        s = (b + o - ve) / (b / 2 + o) if ve > b / 2 else (ve + o) / (b / 2 + o)
        U = iso(ax, ay, a + o - s * (r0 + o), ve, zr + s * rh)
        g.append(f'<line x1="{f(E[0])}" y1="{f(E[1])}" x2="{f(U[0])}" y2="{f(U[1])}" stroke="#4A525C" stroke-width="1.4" stroke-opacity="0.8"/>')
        g.append(f'<ellipse cx="{f(E[0])}" cy="{f(E[1] + 0.6)}" rx="1.3" ry="1.1" fill="#6A727C" stroke="#111" stroke-width="0.4"/>')
    # hip ridges + main ridge
    for c, r in ((front[0], R0), (front[-1], R1), (right[-1], R1), (back[-1], R0)):
        g.append(f'<line x1="{f(c[0])}" y1="{f(c[1])}" x2="{f(r[0])}" y2="{f(r[1])}" stroke="#16191d" stroke-width="3.6" stroke-linecap="round"/>')
        g.append(f'<line x1="{f(c[0])}" y1="{f(c[1] - 1)}" x2="{f(r[0])}" y2="{f(r[1] - 1)}" stroke="#C9CED4" stroke-width="1.3" stroke-linecap="round" stroke-opacity="0.85"/>')
    g.append(f'<line x1="{f(R0[0])}" y1="{f(R0[1])}" x2="{f(R1[0])}" y2="{f(R1[1])}" stroke="#14171b" stroke-width="6" stroke-linecap="round"/>')
    g.append(f'<line x1="{f(R0[0])}" y1="{f(R0[1] - 2)}" x2="{f(R1[0])}" y2="{f(R1[1] - 2)}" stroke="#DDE1E5" stroke-width="1.8" stroke-linecap="round"/>')
    for R in (R0, R1):  # 망와 end ornaments
        g.append(f'<path d="M{f(R[0] - 3)} {f(R[1] + 1)} q-1 -8 4 -9 q2 3 0 6 z" fill="#2A2F35" stroke="#0e0f11" stroke-width="0.6"/>')
    # eave edge highlight
    g.append(f'<polyline points="{P(front)}" fill="none" stroke="#D5DAE0" stroke-width="1.1" stroke-opacity="0.7"/>')
    g.append(f'<polyline points="{P(right)}" fill="none" stroke="#7E8690" stroke-width="0.9" stroke-opacity="0.6"/>')
    return "".join(g), front, right


def wall_face(ax, ay, a, b, side, z0, z1, bays, doors, shade, glow=False):
    """Timber-frame wall: plaster, pillars, beams, lattice doors, dancheong band."""
    g = []
    length = a if side == "L" else b
    plaster = mix("#E8DEC6", "#2a2018", shade)
    g.append(poly(face_quad(ax, ay, a, b, side, 0, 1, z0, z1), plaster))
    # lower sill + middle rail
    g.append(poly(face_quad(ax, ay, a, b, side, 0, 1, z0, z0 + 5), mix("#6A4628", "#000", shade * 0.8), outline=False))
    for i in range(bays):
        t0, t1 = i / bays, (i + 1) / bays
        if i in doors:
            m = 0.1 / length
            q = face_quad(ax, ay, a, b, side, t0 + m * 1.3, t1 - m * 1.3, z0 + 6, z1 - 9)
            fill = "url(#s3-hanjiG)" if glow else ("url(#s3-hanji)" if shade < .3 else mix("#C8B486", "#1a140e", shade * .7))
            g.append(f'<polygon points="{P(q)}" fill="{fill}" stroke="#3A2414" stroke-width="1.1"/>')
            for k in range(1, 5):
                tt = t0 + m * 1.3 + (t1 - t0 - 2.6 * m) * k / 5
                p0, p1 = face_pt(ax, ay, a, b, side, tt, z0 + 6), face_pt(ax, ay, a, b, side, tt, z1 - 9)
                g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="#5A3A20" stroke-width="0.55"/>')
            for k in range(1, 7):
                zz = z0 + 6 + (z1 - 15 - z0) * k / 7
                p0, p1 = face_pt(ax, ay, a, b, side, t0 + m * 1.3, zz), face_pt(ax, ay, a, b, side, t1 - m * 1.3, zz)
                g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="#5A3A20" stroke-width="0.55"/>')
        else:
            zz = (z0 + z1) / 2
            p0, p1 = face_pt(ax, ay, a, b, side, t0, zz), face_pt(ax, ay, a, b, side, t1, zz)
            g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="{mix("#6A4628", "#000", shade)}" stroke-width="2.2"/>')
    # pillars
    for i in range(bays + 1):
        t = i / bays
        w = 0.07 / length
        q = face_quad(ax, ay, a, b, side, max(0, t - w), min(1, t + w), z0, z1)
        g.append(f'<polygon points="{P(q)}" fill="{mix("#8A3422", "#1a0a06", shade)}" {OUTL} stroke-width="0.6"/>')
        p0, p1 = face_pt(ax, ay, a, b, side, max(0, t - w) + 0.002, z0), face_pt(ax, ay, a, b, side, max(0, t - w) + 0.002, z1)
        g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="#E07A56" stroke-opacity="{0.55 - shade * 0.4:.2f}" stroke-width="0.7"/>')
    # dancheong beam band
    band = face_quad(ax, ay, a, b, side, 0, 1, z1 - 7, z1)
    g.append(poly(band, mix(GRN, "#06120f", shade * 0.8)))
    nmot = max(2, int(length * 2.2))
    for k in range(nmot):
        t = (k + 0.5) / nmot
        c = face_pt(ax, ay, a, b, side, t, z1 - 3.5)
        col = [RED, YEL, BLUE_O][k % 3]
        g.append(f'<ellipse cx="{f(c[0])}" cy="{f(c[1])}" rx="2.2" ry="1.6" fill="{mix(col, "#000", shade * .6)}" stroke="#F2E6C8" stroke-width="0.45" stroke-opacity="{0.8 - shade * .5:.2f}"/>')
    p0, p1 = face_pt(ax, ay, a, b, side, 0, z1 - 7), face_pt(ax, ay, a, b, side, 1, z1 - 7)
    g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="{RED}" stroke-width="0.9"/>')
    # under-eave shadow
    q = face_quad(ax, ay, a, b, side, 0, 1, z1 - 16, z1)
    g.append(f'<polygon points="{P(q)}" fill="url(#s3-eave)"/>')
    return "".join(g)


def hanok(ax, ay, a, b, wall_h=30, roof_h=30, base_h=9, base_e=0.35, bays=(4, 2), doors=((1, 2), ()),
          stairs=True, glow=False, o=0.55, open_walls=False):
    g = [cast_shadow(ax, ay, a, b, base_h + wall_h + roof_h, grow=o + base_e)]
    # stone platform 기단
    bx, by = iso(ax, ay, -base_e, -base_e)
    A, B = a + 2 * base_e, b + 2 * base_e
    g.append(box(bx, by, A, B, base_h, "#A79F88", "#8C836E", "#625A4A"))
    g.append(stones_face(bx, by, A, B, "L", 0, base_h, "#9A917A", rows=2))
    g.append(stones_face(bx, by, A, B, "R", 0, base_h, "#6A6252", rows=2))
    # flagstone seams on platform top
    for k in range(1, int(A * 2)):
        p0, p1 = iso(bx, by, k / 2, 0, base_h), iso(bx, by, k / 2, B, base_h)
        g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="#5E5646" stroke-width="0.5" stroke-opacity="0.6"/>')
    if stairs:
        mid = a / 2 + base_e
        for st in range(2):
            sx, sy = iso(bx, by, mid - 0.7, B + 0.55 - st * 0.3)
            g.append(box(sx, sy, 1.4, 0.3, base_h * (st + 1) / 2.4, "#B3AB94", "#958C76", "#6E6654"))
    zt = base_h + wall_h
    if open_walls:
        for (u, v) in ((0, 0), (a, 0), (0, b), (a, b), (a / 2, b), (a / 2, 0)):
            px, py = iso(ax, ay, u, v, base_h)
            g.append(f'<rect x="{f(px - 2.2)}" y="{f(py - wall_h)}" width="4.4" height="{f(wall_h)}" fill="url(#s3-pillar)" {OUTL} stroke-width="0.6"/>')
        g.append(poly(face_quad(ax, ay, a, b, "L", 0, 1, zt - 7, zt), GRN))
        g.append(poly(face_quad(ax, ay, a, b, "R", 0, 1, zt - 7, zt), mix(GRN, "#000", .45)))
    else:
        g.append(wall_face(ax, ay, a, b, "L", base_h, zt, bays[0], set(doors[0]), 0.0, glow))
        g.append(wall_face(ax, ay, a, b, "R", base_h, zt, bays[1], set(doors[1]), 0.42, False))
    # rafter ends (dancheong dots) just under eaves
    for k in range(int((a + 2 * o) * 4)):
        t = (k + 0.5) / int((a + 2 * o) * 4)
        p = iso(ax, ay, -o + t * (a + 2 * o), b + o - 0.12, zt + 2)
        g.append(f'<circle cx="{f(p[0])}" cy="{f(p[1])}" r="1.15" fill="{GRN}" stroke="#0e1a14" stroke-width="0.4"/><circle cx="{f(p[0])}" cy="{f(p[1])}" r="0.45" fill="{YEL}"/>')
    r, front, right = roof(ax, ay, a, b, zt + 3, roof_h, o=o)
    g.append(r)
    return "".join(g)


# ---------------------------------------------------------------- props
def boulder(x, y, r, col="#8A8474", seed_shift=0.0):
    n = 9
    pts = []
    for i in range(n):
        ang = math.pi * 2 * i / n + seed_shift
        rr = r * rnd.uniform(0.72, 1.0)
        pts.append((x + math.cos(ang) * rr, y + math.sin(ang) * rr * 0.62 - r * 0.25))
    top = [(px, py - r * 0.18) for px, py in pts]
    g = [f'<ellipse cx="{f(x + r * 0.5)}" cy="{f(y + r * 0.15)}" rx="{f(r * 1.15)}" ry="{f(r * 0.45)}" fill="#000" opacity="0.38" filter="url(#s3-sh)"/>']
    g.append(poly(pts, dk(col, .35)))
    g.append(poly([p for p in top if p[1] < y], col, outline=False) if len([p for p in top if p[1] < y]) > 2 else "")
    g.append(f'<ellipse cx="{f(x - r * 0.25)}" cy="{f(y - r * 0.55)}" rx="{f(r * 0.45)}" ry="{f(r * 0.22)}" fill="{lt(col, .35)}" opacity="0.75"/>')
    g.append(f'<path d="M{f(x - r * .3)} {f(y - r * .1)} l{f(r * .3)} {f(r * .22)} l{f(r * .35)} {f(-r * .1)}" stroke="#2a2418" stroke-width="0.7" fill="none" opacity="0.7"/>')
    return "".join(g)


def pine(x, y, h=70, lean=-0.18):
    g = [f'<ellipse cx="{f(x + h * 0.45)}" cy="{f(y + 4)}" rx="{f(h * 0.5)}" ry="{f(h * 0.16)}" fill="#0b0805" opacity="0.45" filter="url(#s3-sh2)"/>']
    tx = x + lean * h
    g.append(f'<path d="M{f(x - 3.2)} {f(y)} C{f(x - 2)} {f(y - h * .4)} {f(tx - 3)} {f(y - h * .7)} {f(tx - 1)} {f(y - h * .92)} L{f(tx + 1.4)} {f(y - h * .92)} C{f(tx + 1)} {f(y - h * .6)} {f(x + 3)} {f(y - h * .35)} {f(x + 3.4)} {f(y)} Z" fill="url(#s3-pillar)" {OUTL} stroke-width="0.6"/>')
    for k in range(5):
        by = y - h * (0.2 + 0.08 * k) - rnd.uniform(0, 4)
        g.append(f'<path d="M{f(x - 2 + lean * (y - by))} {f(by)} l{f(rnd.uniform(-2, 2))} 1.6" stroke="#3a1a0e" stroke-width="0.7"/>')
    clumps = [(-0.32, 0.55, 0.36), (0.3, 0.62, 0.32), (-0.05, 0.78, 0.4), (0.22, 0.9, 0.3), (-0.25, 0.97, 0.26), (0.02, 1.06, 0.24)]
    for dxr, hy, rr in clumps:
        cx, cy, r = tx + dxr * h * 0.9, y - h * hy, h * rr * 0.62
        g.append(f'<ellipse cx="{f(cx + 2)}" cy="{f(cy + 3)}" rx="{f(r)}" ry="{f(r * 0.48)}" fill="#0F1A10"/>')
        g.append(f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(r)}" ry="{f(r * 0.46)}" fill="#24402A" {OUTL} stroke-width="0.6"/>')
        g.append(f'<ellipse cx="{f(cx - r * .18)}" cy="{f(cy - r * .14)}" rx="{f(r * .72)}" ry="{f(r * .3)}" fill="#3A5E36"/>')
        g.append(f'<ellipse cx="{f(cx - r * .32)}" cy="{f(cy - r * .22)}" rx="{f(r * .38)}" ry="{f(r * .15)}" fill="#6C8A4A" opacity="0.85"/>')
        for _ in range(7):
            px, py = cx + rnd.uniform(-r * .8, r * .8), cy + rnd.uniform(-r * .3, r * .25)
            g.append(f'<path d="M{f(px)} {f(py)} l{f(rnd.uniform(-3, 3))} {f(-rnd.uniform(1, 2.5))}" stroke="#89A660" stroke-width="0.6" opacity="0.7"/>')
    return "".join(g)


def maple(x, y, r=26, hue=("#C2412A", "#E0722E", "#F0A548")):
    g = [f'<ellipse cx="{f(x + r * .8)}" cy="{f(y + 3)}" rx="{f(r * 1.1)}" ry="{f(r * .38)}" fill="#0b0805" opacity="0.42" filter="url(#s3-sh2)"/>']
    g.append(f'<path d="M{f(x - 2.4)} {f(y)} L{f(x - 1.2)} {f(y - r * 1.1)} L{f(x + 1.6)} {f(y - r * 1.1)} L{f(x + 2.6)} {f(y)} Z" fill="#4A2A1A" {OUTL} stroke-width="0.5"/>')
    g.append(f'<path d="M{f(x)} {f(y - r * .8)} l-7 -8 M{f(x)} {f(y - r * .9)} l8 -7" stroke="#4A2A1A" stroke-width="1.4"/>')
    cy = y - r * 1.45
    blobs = []
    for i in range(16):
        ang = rnd.uniform(0, math.pi * 2)
        d = rnd.uniform(0, r * 0.75)
        blobs.append((x + math.cos(ang) * d, cy + math.sin(ang) * d * 0.7, rnd.uniform(r * .32, r * .5)))
    blobs.sort(key=lambda b: b[1])
    g.append(f'<ellipse cx="{f(x + 2)}" cy="{f(cy + 4)}" rx="{f(r * 1.05)}" ry="{f(r * .8)}" fill="#3A140C"/>')
    for bx, by, br in blobs:
        lit = (bx - x) * -0.5 + (by - cy) * -0.6
        c = hue[0] if lit < -4 else (hue[1] if lit < 4 else hue[2])
        g.append(f'<circle cx="{f(bx)}" cy="{f(by)}" r="{f(br)}" fill="{dk(c, .18)}" {OUTL} stroke-width="0.5"/>')
        g.append(f'<circle cx="{f(bx - br * .2)}" cy="{f(by - br * .25)}" r="{f(br * .66)}" fill="{c}"/>')
    for _ in range(26):
        px, py = x + rnd.uniform(-r, r) * .9, cy + rnd.uniform(-r * .7, r * .5)
        g.append(f'<circle cx="{f(px)}" cy="{f(py)}" r="{f(rnd.uniform(.8, 1.6))}" fill="{rnd.choice(["#FFD27A", "#F7B455", "#9E2A18"])}" opacity="0.85"/>')
    return "".join(g)


def grass_tuft(x, y, s=1.0, col=None):
    col = col or rnd.choice(["#8D8A48", "#A39A52", "#6E7238", "#B5A35C", "#7D7E3E"])
    d = []
    for k in range(rnd.randint(3, 5)):
        dx = rnd.uniform(-3.2, 3.2) * s
        hh = rnd.uniform(3.5, 7.5) * s
        d.append(f"M{f(x + dx * .3)} {f(y)} q{f(dx * .4)} {f(-hh * .6)} {f(dx)} {f(-hh)}")
    return f'<path d="{" ".join(d)}" stroke="{col}" stroke-width="{f(0.75 * s)}" fill="none" stroke-linecap="round"/>'


def straw_stack(x, y):
    g = [f'<ellipse cx="{f(x + 7)}" cy="{f(y + 2)}" rx="11" ry="4" fill="#000" opacity="0.4" filter="url(#s3-sh)"/>']
    g.append(f'<path d="M{f(x - 9)} {f(y)} Q{f(x - 9)} {f(y - 14)} {f(x)} {f(y - 20)} Q{f(x + 9)} {f(y - 14)} {f(x + 9)} {f(y)} Z" fill="url(#s3-rice)" {OUTL} stroke-width="0.6"/>')
    for k in range(6):
        g.append(f'<path d="M{f(x - 7 + k * 2.6)} {f(y - 1)} q1 -8 {f(1 + (k - 3) * .2)} -15" stroke="#7A5A1E" stroke-width="0.5" fill="none" opacity="0.7"/>')
    g.append(f'<path d="M{f(x - 4)} {f(y - 18)} Q{f(x)} {f(y - 25)} {f(x + 4)} {f(y - 18)}" fill="#C9A44E" {OUTL} stroke-width="0.5"/>')
    g.append(f'<path d="M{f(x - 8)} {f(y - 7)} Q{f(x)} {f(y - 4)} {f(x + 8)} {f(y - 7)}" stroke="#6A4A1A" stroke-width="1" fill="none"/>')
    return "".join(g)


def flag(x, y, h=40, col=RED, wave=1):
    return (f'<line x1="{f(x)}" y1="{f(y)}" x2="{f(x)}" y2="{f(y - h)}" stroke="#3A2414" stroke-width="1.6"/>'
            f'<circle cx="{f(x)}" cy="{f(y - h - 1)}" r="1.6" fill="url(#s3-brass)"/>'
            f'<path d="M{f(x)} {f(y - h + 1)} q{f(9 * wave)} -3 {f(18 * wave)} 2 q-4 {f(5)} {f(-2 * wave)} 10 q{f(-8 * wave)} -4 {f(-16 * wave)} 0 Z" fill="{col}" stroke="{YEL}" stroke-width="0.9"/>'
            f'<path d="M{f(x + 3 * wave)} {f(y - h + 4)} q{f(5 * wave)} -1 {f(10 * wave)} 1" stroke="#FFD9A0" stroke-width="0.6" fill="none" opacity="0.7"/>')


# ---------------------------------------------------------------- units
def ring(x, y, rx=11, col=YEL, enemy=False):
    c = ALERT if enemy else col
    return (f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rx)}" ry="{f(rx * .48)}" fill="{c}" fill-opacity="0.12" stroke="{c}" stroke-width="1.6" filter="url(#s3-glow)"/>'
            f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rx + 3)}" ry="{f(rx * .48 + 1.6)}" fill="none" stroke="{c}" stroke-width="0.7" stroke-dasharray="3 3" opacity="0.7"/>')


def hp_bar(x, y, frac, w=22, segs=5, enemy=False):
    col = OK if frac > .6 else ("#E8C547" if frac > .3 else ALERT)
    if enemy:
        col = "#E86A50" if frac > .3 else ALERT
    g = [f'<rect x="{f(x - w / 2 - 1)}" y="{f(y - 1)}" width="{f(w + 2)}" height="5" rx="1" fill="#0B0A08" stroke="#000" stroke-width="0.5"/>']
    sw = w / segs
    for i in range(segs):
        fill_frac = max(0.0, min(1.0, frac * segs - i))
        if fill_frac > 0:
            g.append(f'<rect x="{f(x - w / 2 + i * sw + .4)}" y="{f(y)}" width="{f((sw - .8) * fill_frac)}" height="3" fill="{col}"/>')
            g.append(f'<rect x="{f(x - w / 2 + i * sw + .4)}" y="{f(y)}" width="{f((sw - .8) * fill_frac)}" height="1" fill="#fff" opacity="0.35"/>')
    return "".join(g)


def studs(pts):
    return "".join(f'<circle cx="{f(x)}" cy="{f(y)}" r="0.55" fill="#F2D27E"/>' for x, y in pts)


def gunner(x, y, s=1.0, face=1, aim=-14, fire=False, sel=False, hp=None, burn=False):
    g = []
    if sel:
        g.append(ring(x, y))
    g.append(f'<g transform="translate({f(x)},{f(y)}) scale({f(face * s)},{f(s)})">')
    g.append('<ellipse cx="3" cy="0.6" rx="9" ry="3.4" fill="#000" opacity="0.45" filter="url(#s3-sh1)"/>')
    # legs (back then front)
    g.append(f'<path d="M-4.6 -14 L-0.6 -14 L-2 -6 L-2.6 -1.4 L-6 -1.4 L-5.4 -6 Z" fill="#B9AF96" {OUTL} stroke-width="0.6"/>')
    g.append('<path d="M-5.4 -6 L-2 -6 L-2.4 -2 L-5.8 -2 Z" fill="#5E5644"/>')
    g.append('<ellipse cx="-4.6" cy="-1" rx="2.6" ry="1.2" fill="#15120E"/>')
    g.append(f'<path d="M-0.4 -14 L3.8 -14 L5 -6 L6.4 -1.2 L2.6 -1.2 L1.4 -6 Z" fill="url(#s3-pants)" {OUTL} stroke-width="0.6"/>')
    g.append('<path d="M1.4 -6 L5 -6 L5.8 -2 L2.2 -2 Z" fill="#6E6450"/>')
    g.append('<path d="M2.4 -5.2 L5.2 -4.6 M2.2 -3.6 L5.5 -3" stroke="#3A3428" stroke-width="0.4"/>')
    g.append('<ellipse cx="4.8" cy="-0.9" rx="2.8" ry="1.2" fill="#15120E"/>')
    # brigandine skirt + torso
    g.append(f'<path d="M-6.4 -12.6 L7.2 -12.6 L5.6 -22 L-4.8 -22 Z" fill="url(#s3-coat)" {OUTL} stroke-width="0.7"/>')
    g.append('<path d="M0.4 -12.6 L0.8 -22" stroke="#5A1408" stroke-width="0.6"/>')
    g.append(studs([(-4.6 + c * 2.2 + r * .3, -14 - r * 2.6) for r in range(3) for c in range(5)]))
    g.append(f'<path d="M-4.8 -22 L5.6 -22 L5.2 -30 Q0.6 -32.6 -4.2 -30 Z" fill="url(#s3-coat)" {OUTL} stroke-width="0.7"/>')
    g.append(studs([(-3 + c * 2.2, -24.5 - r * 2.4) for r in range(2) for c in range(4)]))
    g.append(f'<rect x="-5.2" y="-23.2" width="11.2" height="2.3" fill="{BLUE_O}" {OUTL} stroke-width="0.5"/>')
    g.append('<rect x="-0.6" y="-23.4" width="2.4" height="2.6" fill="url(#s3-brass)"/>')
    g.append('<path d="M-3.8 -29.4 Q-4.6 -24 -4.2 -16" stroke="#FFB79A" stroke-width="0.6" fill="none" opacity="0.6"/>')
    # powder flask on hip
    g.append(f'<ellipse cx="-5.6" cy="-17" rx="1.8" ry="2.6" fill="url(#s3-woodL)" {OUTL} stroke-width="0.4"/>')
    # gun
    g.append(f'<g transform="translate(1,-26) rotate({aim})">')
    g.append(f'<path d="M-8 -1.2 L4 -1.6 L4 1.8 L-7 2.6 Q-9 1.4 -8 -1.2Z" fill="url(#s3-gun)" {OUTL} stroke-width="0.5"/>')
    g.append(f'<rect x="3" y="-1.1" width="18" height="2" rx="0.6" fill="url(#s3-iron)" {OUTL} stroke-width="0.4"/>')
    g.append('<rect x="8" y="-1.4" width="1" height="2.6" fill="url(#s3-brass)"/><rect x="15" y="-1.4" width="1" height="2.6" fill="url(#s3-brass)"/>')
    g.append('<circle cx="1.6" cy="-2.6" r="0.9" fill="#FFB040" filter="url(#s3-glow)"/>')
    if fire:
        g.append('<g filter="url(#s3-glow2)"><path d="M21 0 L27 -3 L25 -0.4 L31 0 L25 0.6 L27 3.2 Z" fill="#FFE7A8"/><circle cx="22.5" cy="0" r="2.4" fill="#FFF8E0"/></g>')
        g.append('<g filter="url(#s3-smoke)" opacity="0.85"><circle cx="28" cy="-4" r="4" fill="url(#s3-smk)"/><circle cx="33" cy="-7" r="3.2" fill="url(#s3-smk)"/><circle cx="25" cy="-8" r="3" fill="url(#s3-smk)"/></g>')
    g.append('</g>')
    # arms
    g.append('<path d="M3.6 -29 Q8 -27.4 8.6 -25.6" stroke="#9E2E1C" stroke-width="3.2" fill="none" stroke-linecap="round"/>')
    g.append('<circle cx="8.8" cy="-25.6" r="1.4" fill="url(#s3-skin)"/>')
    g.append('<path d="M-3.6 -29 Q-3 -26 -1.4 -25" stroke="#7A2214" stroke-width="3" fill="none" stroke-linecap="round"/>')
    # head
    g.append('<rect x="0.2" y="-32.4" width="2.6" height="2" fill="#A87A50"/>')
    g.append(f'<ellipse cx="1.6" cy="-34.4" rx="3.1" ry="3.4" fill="url(#s3-skin)" {OUTL} stroke-width="0.5"/>')
    g.append('<path d="M0.6 -32.4 q1.6 1.4 3.6 0" stroke="#2A1A10" stroke-width="0.8" fill="none"/>')
    g.append('<circle cx="3.4" cy="-34.8" r="0.45" fill="#1a0f08"/>')
    # 전립 hat
    g.append(f'<ellipse cx="1.6" cy="-36.8" rx="7.6" ry="2.5" fill="url(#s3-brim)" {OUTL} stroke-width="0.6"/>')
    g.append('<path d="M-5 -37.4 Q1.6 -39.8 8 -37.4" stroke="#7A746A" stroke-width="0.5" fill="none"/>')
    g.append(f'<path d="M-1.6 -37 Q-1.8 -41.4 1.6 -41.6 Q5 -41.4 4.8 -37 Z" fill="#1E1B18" {OUTL} stroke-width="0.5"/>')
    g.append('<path d="M-0.8 -38 Q-0.6 -40.6 1.4 -40.9" stroke="#6A6458" stroke-width="0.5" fill="none"/>')
    g.append('<g filter="url(#s3-glow)"><path d="M1.6 -41.6 q-3 -3 -6 -2.4 M1.6 -41.6 q-2 -4 -5 -5 M1.6 -41.6 q1 -4 -1 -6" stroke="#E04A2E" stroke-width="1.3" fill="none" stroke-linecap="round"/></g>')
    g.append('<circle cx="1.6" cy="-41.6" r="1" fill="url(#s3-brass)"/>')
    g.append('</g>')
    if burn:
        g.append(blue_flame(x - 2, y - 2, 0.7) + blue_flame(x + 5, y - 10, 0.5))
    if hp is not None:
        g.append(hp_bar(x + 1, y - 50 * s, hp))
    return "".join(g)


def worker(x, y, face=1, mining=False, carry=False, hammer=False, s=1.0):
    g = [f'<g transform="translate({f(x)},{f(y)}) scale({f(face * s)},{f(s)})">',
         '<ellipse cx="3" cy="0.6" rx="8" ry="3" fill="#000" opacity="0.45" filter="url(#s3-sh1)"/>']
    # legs
    g.append(f'<path d="M-4 -12 L-0.6 -12 L-1.6 -1.2 L-5 -1.2 Z" fill="#B5AA8E" {OUTL} stroke-width="0.6"/>')
    g.append(f'<path d="M0 -12 L3.6 -12 L5 -1.2 L1.6 -1.2 Z" fill="url(#s3-pants)" {OUTL} stroke-width="0.6"/>')
    g.append('<path d="M-5 -4 L-1.6 -4 M1.4 -4 L4.8 -4" stroke="#6E6450" stroke-width="1.6"/>')
    g.append('<ellipse cx="-3.4" cy="-0.9" rx="2.4" ry="1" fill="#4A3A26"/><ellipse cx="3.6" cy="-0.9" rx="2.4" ry="1" fill="#4A3A26"/>')
    # jige back frame (behind torso)
    g.append('<path d="M-6.4 -6 L-5.2 -34 M-2.4 -6 L-1.2 -34" stroke="#6A4A2A" stroke-width="1.3"/>')
    g.append('<path d="M-6 -16 L-1.8 -16 M-5.8 -22 L-1.6 -22 M-5.6 -28 L-1.4 -28" stroke="#8A6A40" stroke-width="1"/>')
    if carry:
        g.append(f'<g filter="url(#s3-glow)"><polygon points="-9,-26 -6,-34 -2,-31 -1,-24 -5,-21" fill="url(#s3-crysL)" {OUTL} stroke-width="0.5"/><polygon points="-6,-34 -4,-38 -1,-33 -2,-31" fill="{ORE_G}"/></g>')
    # torso hemp jacket
    g.append(f'<path d="M-4.6 -12 L5 -12 L4.6 -26 Q0.6 -28.4 -4 -26 Z" fill="#D8CFB8" {OUTL} stroke-width="0.7"/>')
    g.append('<path d="M-4.6 -12 L5 -12 L4.8 -14 L-4.6 -14 Z" fill="#8A7A5A"/>')
    g.append('<path d="M0.4 -26.4 L1.4 -14" stroke="#9E927A" stroke-width="0.6"/>')
    g.append(f'<rect x="-4.8" y="-17" width="10" height="1.8" fill="{R1}"/>')
    g.append('<path d="M3.4 -26 Q4.6 -20 4.4 -14" stroke="#7E745E" stroke-width="0.8" fill="none"/>')
    # head + headband + goggles
    g.append(f'<ellipse cx="1" cy="-30.4" rx="3" ry="3.3" fill="url(#s3-skin)" {OUTL} stroke-width="0.5"/>')
    g.append('<path d="M-2 -32 Q1 -34.6 4 -32 L4 -33.2 Q1 -36 -2 -33.2 Z" fill="#1E1A16"/>')
    g.append(f'<rect x="-2.2" y="-32.6" width="6.4" height="1.4" fill="{R1}"/>')
    g.append('<circle cx="2.6" cy="-30.6" r="1.1" fill="#9FE3D0" stroke="url(#s3-brass)" stroke-width="0.6"/>')
    if mining:
        g.append('<g transform="translate(4,-22) rotate(-62)"><rect x="-1" y="-1" width="17" height="2" fill="url(#s3-woodL)" stroke="#2a1a0e" stroke-width="0.4"/>'
                 '<path d="M15 -5 Q17.5 0 15 5 L16.8 0 Z" fill="url(#s3-iron)" stroke="#111" stroke-width="0.4"/></g>')
        g.append('<path d="M4 -24 Q7 -26 8.6 -29" stroke="#BDB29A" stroke-width="2.6" stroke-linecap="round" fill="none"/>')
    if hammer:
        g.append('<g transform="translate(4,-22) rotate(-40)"><rect x="0" y="-0.8" width="11" height="1.6" fill="url(#s3-woodL)"/><rect x="9" y="-2.6" width="3.2" height="5.2" fill="url(#s3-iron)"/></g>')
        g.append('<path d="M4 -24 Q7 -26 8 -28" stroke="#BDB29A" stroke-width="2.6" stroke-linecap="round" fill="none"/>')
    if not mining and not hammer:
        g.append('<path d="M4 -25 Q6.4 -20 5.6 -16" stroke="#BDB29A" stroke-width="2.4" stroke-linecap="round" fill="none"/>')
    g.append('</g>')
    return "".join(g)


def hwacha(x, y, s=1.0, firing=True, sel=False, hp=None):
    """Rocket cart pointing up-right; local projection with D (along cart) and U (across)."""
    D, U = (0.894, -0.447), (0.894, 0.447)

    def p(l, w, z):
        return (x + (l * D[0] + w * U[0]) * s, y + (l * D[1] + w * U[1] - z) * s)

    g = []
    if sel:
        g.append(ring(x, y + 2, rx=26))
    g.append(f'<ellipse cx="{f(x + 10 * s)}" cy="{f(y + 4 * s)}" rx="{f(34 * s)}" ry="{f(12 * s)}" fill="#000" opacity="0.45" filter="url(#s3-sh)"/>')

    def wheel(l, w, zc, r):
        rim = [p(l + r * math.cos(t), w, zc + r * math.sin(t)) for t in [i * math.pi * 2 / 28 for i in range(28)]]
        inner = [p(l + r * .78 * math.cos(t), w, zc + r * .78 * math.sin(t)) for t in [i * math.pi * 2 / 28 for i in range(28)]]
        c = p(l, w, zc)
        out = [poly(rim, "#4A2E18"), poly(inner, "#2a1a0e", outline=False)]
        for k in range(10):
            t = k * math.pi * 2 / 10
            q = p(l + r * .78 * math.cos(t), w, zc + r * .78 * math.sin(t))
            out.append(f'<line x1="{f(c[0])}" y1="{f(c[1])}" x2="{f(q[0])}" y2="{f(q[1])}" stroke="#8A6236" stroke-width="{f(1.2 * s)}"/>')
        out.append(f'<polyline points="{P(rim[20:28] + rim[0:3])}" fill="none" stroke="#B88A56" stroke-width="{f(0.8 * s)}"/>')
        out.append(f'<circle cx="{f(c[0])}" cy="{f(c[1])}" r="{f(2.2 * s)}" fill="url(#s3-brass)" stroke="#2a1a0e" stroke-width="0.5"/>')
        return "".join(out)

    g.append(wheel(0, -11, 11, 11))
    # shafts
    for w in (-6, 6):
        a0, a1 = p(-14, w, 10), p(-40, w * 1.2, 3)
        g.append(f'<line x1="{f(a0[0])}" y1="{f(a0[1])}" x2="{f(a1[0])}" y2="{f(a1[1])}" stroke="#5A3A20" stroke-width="{f(2.2 * s)}" stroke-linecap="round"/>')
    # bed (box)
    def quad(*pts):
        return pts
    top = [p(-16, -9, 14), p(16, -9, 14), p(16, 9, 14), p(-16, 9, 14)]
    near = [p(-16, 9, 9), p(16, 9, 9), p(16, 9, 14), p(-16, 9, 14)]
    rear = [p(-16, -9, 9), p(-16, 9, 9), p(-16, 9, 14), p(-16, -9, 14)]
    g.append(poly(rear, "#9A6E42") + poly(near, "#5A3A22") + poly(top, "#8A6038"))
    for l in (-8, 0, 8):
        a0, a1 = p(l, -9, 14), p(l, 9, 14)
        g.append(f'<line x1="{f(a0[0])}" y1="{f(a0[1])}" x2="{f(a1[0])}" y2="{f(a1[1])}" stroke="#3A2414" stroke-width="0.6"/>')
    # rocket rack: tilted box rising toward the front
    def zb(l):
        return 15 + (l + 15) * 0.62
    H = 17
    rtop = [p(-15, -10, zb(-15) + H), p(15, -10, zb(15) + H), p(15, 10, zb(15) + H), p(-15, 10, zb(-15) + H)]
    rnear = [p(-15, 10, zb(-15)), p(15, 10, zb(15)), p(15, 10, zb(15) + H), p(-15, 10, zb(-15) + H)]
    rrear = [p(-15, -10, zb(-15)), p(-15, 10, zb(-15)), p(-15, 10, zb(-15) + H), p(-15, -10, zb(-15) + H)]
    g.append(poly(rnear, "#4E3220") + poly(rtop, "#7A5230"))
    for l in range(-12, 15, 6):
        a0, a1 = p(l, 10, zb(l)), p(l, 10, zb(l) + H)
        g.append(f'<line x1="{f(a0[0])}" y1="{f(a0[1])}" x2="{f(a1[0])}" y2="{f(a1[1])}" stroke="#2a1a0e" stroke-width="0.6"/>')
    g.append(poly(rrear, "#A87A4A"))
    # rocket tails (fletching) on the rear face: 7 x 5 grid
    for i in range(7):
        for j in range(5):
            w = -8 + i * 16 / 6
            z = zb(-15) + 2.4 + j * (H - 4.8) / 4
            c = p(-15.3, w, z)
            g.append(f'<circle cx="{f(c[0])}" cy="{f(c[1])}" r="{f(1.05 * s)}" fill="#1a0f08"/>')
            if (i + j) % 3 != 0 or not firing:
                g.append(f'<path d="M{f(c[0] - .9 * s)} {f(c[1] - .4 * s)} l{f(.9 * s)} {f(.9 * s)} l{f(.9 * s)} {f(-.9 * s)}" stroke="{"#F2E6C8" if (i + j) % 2 else RED}" stroke-width="{f(.6 * s)}" fill="none"/>')
    # brass bands on rack
    for l in (-10, 10):
        a0, a1, a2 = p(l, -10, zb(l) + H), p(l, 10, zb(l) + H), p(l, 10, zb(l))
        g.append(f'<polyline points="{P([a0, a1, a2])}" fill="none" stroke="url(#s3-brass)" stroke-width="{f(1.6 * s)}"/>')
    g.append(wheel(0, 11, 11, 11))
    # operator with torch
    g.append(gunner(*p(-30, 16, 0), s=0.95 * s, face=1, aim=30))
    t = p(-24, 17, 30)
    g.append(f'<g filter="url(#s3-glow2)"><path d="M{f(t[0])} {f(t[1])} q-2 -4 1 -8 q1 3 3 2 q0 4 -4 6z" fill="#FFB040"/></g>')
    if firing:
        for k in range(4):
            m = p(16 + k * 1.5, -6 + k * 4, zb(15) + 6 + k * 2.4)
            g.append(f'<circle cx="{f(m[0])}" cy="{f(m[1])}" r="{f(3.2 * s)}" fill="#FFE9B0" filter="url(#s3-glow2)" opacity="0.9"/>')
        g.append(f'<g filter="url(#s3-smoke)" opacity="0.8">' + "".join(
            f'<circle cx="{f(x + (-14 + k * 7) * s)}" cy="{f(y + (-26 - k * 3) * s)}" r="{f((7 + k) * s)}" fill="url(#s3-smk)"/>' for k in range(4)) + '</g>')
    if hp is not None:
        g.append(hp_bar(x + 6 * s, y - 56 * s, hp, w=30, segs=6))
    return "".join(g)


def blue_flame(x, y, s=1.0):
    return (f'<g filter="url(#s3-glow)"><path d="M{f(x - 4 * s)} {f(y)} Q{f(x - 6 * s)} {f(y - 8 * s)} {f(x - 1 * s)} {f(y - 15 * s)} Q{f(x)} {f(y - 9 * s)} {f(x + 2 * s)} {f(y - 11 * s)} '
            f'Q{f(x + 6 * s)} {f(y - 6 * s)} {f(x + 4 * s)} {f(y)} Z" fill="url(#s3-bfire)"/>'
            f'<path d="M{f(x - 1.6 * s)} {f(y - 1)} Q{f(x - 2.4 * s)} {f(y - 5 * s)} {f(x)} {f(y - 8 * s)} Q{f(x + 2 * s)} {f(y - 4 * s)} {f(x + 1.6 * s)} {f(y - 1)} Z" fill="#F4FCFF"/></g>')


def wisps(x, y, n=4, r=22):
    out = []
    for i in range(n):
        a = i * math.pi * 2 / n + 0.6
        out.append(f'<circle cx="{f(x + math.cos(a) * r)}" cy="{f(y + math.sin(a) * r * .5)}" r="{f(rnd.uniform(1.2, 2.2))}" fill="{R2L}" filter="url(#s3-glow)" opacity="0.9"/>')
    return "".join(out)


def brute(x, y, face=-1, s=1.0, hp=None, swing=False):
    g = [f'<g transform="translate({f(x)},{f(y)}) scale({f(face * s)},{f(s)})">',
         '<ellipse cx="3" cy="1" rx="15" ry="5.4" fill="#000" opacity="0.48" filter="url(#s3-sh1)"/>']
    # legs
    g.append(f'<path d="M-9 -20 L-2 -20 L-3 -6 L-1.4 -1 L-9.6 -1 L-8.4 -6 Z" fill="url(#s3-dskin)" {OUTL} stroke-width="0.7"/>')
    g.append(f'<path d="M1 -20 L8.6 -20 L9 -6 L11 -1 L3 -1 L2.4 -6 Z" fill="url(#s3-dskin)" {OUTL} stroke-width="0.7"/>')
    g.append('<path d="M-9.6 -1 l1 -1.8 l1 1.8 l1 -1.8 l1 1.8 M3 -1 l1 -1.8 l1 1.8 l1 -1.8 l1 1.8" stroke="#E8E0C8" stroke-width="0.5" fill="none"/>')
    # tiger-skin loincloth
    g.append(f'<path d="M-10.6 -27 L10.6 -27 L11.6 -16 L5 -12.4 L1 -16 L-4 -12 L-11 -15 Z" fill="url(#s3-tiger)" {OUTL} stroke-width="0.7"/>')
    for k in range(6):
        g.append(f'<path d="M{f(-9 + k * 3.6)} -26 q1.6 3 0 6" stroke="#1A120A" stroke-width="1.1" fill="none"/>')
    g.append(f'<rect x="-11" y="-28.4" width="22" height="2.6" fill="#5A3A1E" {OUTL} stroke-width="0.4"/>')
    # torso
    g.append(f'<path d="M-12 -27 Q-14 -40 -9 -47 Q0 -51 9 -47 Q14 -40 12 -27 Z" fill="url(#s3-dskin)" {OUTL} stroke-width="0.8"/>')
    g.append('<path d="M-6 -42 Q0 -39.6 6 -42 M-4 -35 Q0 -33 4 -35 M0 -45 L0 -31" stroke="#1E2458" stroke-width="0.7" fill="none" opacity="0.8"/>')
    g.append('<path d="M-9 -44 Q-11 -38 -10.4 -30" stroke="#B0BCF4" stroke-width="0.8" fill="none" opacity="0.6"/>')
    g.append("".join(f'<circle cx="{f(-6 + k * 2)}" cy="{f(-45 + abs(k - 3) * .8)}" r="1" fill="#7A4A26" stroke="#2a1a0e" stroke-width="0.3"/>' for k in range(7)))
    # back arm
    g.append(f'<path d="M-10 -44 Q-16 -36 -15 -28" stroke="url(#s3-dskin)" stroke-width="5" stroke-linecap="round" fill="none"/>')
    g.append('<circle cx="-15" cy="-27.4" r="2.6" fill="#3D4A94"/>')
    # club raised over shoulder
    ang = -70 if swing else -38
    g.append(f'<g transform="translate(10,-44) rotate({ang})">')
    g.append(f'<path d="M-2 -1.6 L26 -4.4 Q30 0 26 4.4 L-2 1.6 Z" fill="url(#s3-club)" {OUTL} stroke-width="0.7"/>')
    g.append("".join(f'<circle cx="{f(10 + k * 4.2)}" cy="{f((k % 2) * 3 - 1.5)}" r="1.1" fill="url(#s3-iron)" stroke="#000" stroke-width="0.3"/>' for k in range(4)))
    g.append('<path d="M0 -1.6 L0 1.6 M2 -1.8 L2 1.8" stroke="#C9A35A" stroke-width="0.8"/>')
    g.append('</g>')
    g.append(f'<path d="M9 -45 Q14 -44 13 -48" stroke="url(#s3-dskin)" stroke-width="5" stroke-linecap="round" fill="none"/>')
    # head
    g.append(f'<ellipse cx="0.6" cy="-53" rx="8.4" ry="8" fill="url(#s3-dskin)" {OUTL} stroke-width="0.8"/>')
    g.append('<path d="M-7 -60 l-2 -4 l3 1.6 l0 -4 l3 3 l1 -4.6 l2.4 4 l2 -4.6 l1.4 4.6 l3 -3.4 l0 4.4 l3 -1 l-2 4" fill="#14162C" stroke="#000" stroke-width="0.4"/>')
    g.append('<path d="M1 -61.4 Q0 -68 3.6 -72 Q3.4 -66 4.2 -61.4 Z" fill="url(#s3-horn)" stroke="#3A2A10" stroke-width="0.5"/>')
    g.append('<path d="M-7.6 -54 l-3 -2.4 l3.4 -0.4 Z M8.8 -54 l3 -2.4 l-3.4 -0.4 Z" fill="#3D4A94"/>')
    g.append('<path d="M-4.6 -56 L-0.6 -54.6 M5.8 -56 L1.8 -54.6" stroke="#0E1030" stroke-width="1.2"/>')
    g.append('<g filter="url(#s3-glow)"><circle cx="-2.4" cy="-53.6" r="1.2" fill="#FFE27A"/><circle cx="3.8" cy="-53.6" r="1.2" fill="#FFE27A"/></g>')
    g.append('<path d="M-4.6 -49.6 Q0.6 -45 5.8 -49.6 Q0.6 -47.6 -4.6 -49.6Z" fill="#2a0e14"/>')
    g.append('<path d="M-3.6 -49.2 l0.8 1.6 l0.8 -1.4 l0.8 1.6 l0.8 -1.4 l0.8 1.6 l0.8 -1.4 l0.8 1.6 l0.8 -1.6" fill="#F4EEDC" stroke="none"/>')
    g.append('<path d="M-3.8 -49.4 l0.6 -2.6 l0.8 2.4 M4.6 -49.4 l0.4 -2.6 l0.8 2.4" fill="#F4EEDC"/>')
    g.append('<ellipse cx="-2.6" cy="-57" rx="3" ry="1.4" fill="#C2CCFA" opacity="0.4"/>')
    g.append('</g>')
    g.append(wisps(x, y - 30 * s, 3, 20 * s))
    if hp is not None:
        g.append(hp_bar(x, y - 78 * s, hp, w=28, segs=6, enemy=True))
    return "".join(g)


def thrower(x, y, face=-1, s=1.0, hp=None, ball=True):
    g = [f'<g transform="translate({f(x)},{f(y)}) scale({f(face * s)},{f(s)})">',
         '<ellipse cx="3" cy="0.6" rx="10" ry="3.8" fill="#000" opacity="0.45" filter="url(#s3-sh1)"/>']
    g.append(f'<path d="M-5.4 -16 L-1 -16 L-2 -1 L-6.6 -1 Z" fill="url(#s3-dskin2)" {OUTL} stroke-width="0.6"/>')
    g.append(f'<path d="M0.6 -16 L5 -16 L6.6 -1 L2 -1 Z" fill="url(#s3-dskin2)" {OUTL} stroke-width="0.6"/>')
    g.append(f'<path d="M-7 -22 L7.4 -22 L8 -13 L3 -11 L0 -14 L-3.6 -11 L-7.6 -13 Z" fill="url(#s3-tiger)" {OUTL} stroke-width="0.6"/>')
    g.append("".join(f'<path d="M{f(-5.6 + k * 3.2)} -21 q1.2 2.4 0 5" stroke="#1A120A" stroke-width="0.9" fill="none"/>' for k in range(4)))
    g.append(f'<path d="M-7 -22 Q-8.6 -31 -5 -36 Q0.6 -38.4 6 -36 Q9 -31 7.4 -22 Z" fill="url(#s3-dskin2)" {OUTL} stroke-width="0.7"/>')
    g.append(f'<path d="M-6.4 -34 L6 -24" stroke="{R1}" stroke-width="2"/>')
    # gourd on hip
    g.append(f'<g transform="translate(-8,-18)"><circle cx="0" cy="2" r="2.8" fill="#C9A35A" {OUTL} stroke-width="0.4"/><circle cx="0" cy="-2" r="1.8" fill="#D9B86A" {OUTL} stroke-width="0.4"/></g>')
    # arm raised with fireball
    g.append('<path d="M5 -34 Q9 -40 8 -47" stroke="url(#s3-dskin2)" stroke-width="3.6" stroke-linecap="round" fill="none"/>')
    g.append('<path d="M-5 -34 Q-8 -28 -7 -24" stroke="url(#s3-dskin2)" stroke-width="3.4" stroke-linecap="round" fill="none"/>')
    g.append(f'<ellipse cx="0.4" cy="-41" rx="5.6" ry="5.4" fill="url(#s3-dskin2)" {OUTL} stroke-width="0.6"/>')
    g.append('<path d="M-3 -45.6 Q-4.6 -50 -2.4 -52 Q-2.2 -48.6 -1.2 -46 Z M3 -45.8 Q4.6 -50 2.8 -52.4 Q2.2 -48.6 1.6 -46 Z" fill="url(#s3-horn)" stroke="#3A2A10" stroke-width="0.4"/>')
    g.append('<path d="M-4.6 -44.6 q2 -2.6 5 -2.4 q3 0 5 2.6 q-2 -1 -5 -1 q-3 0 -5 0.8z" fill="#14162C"/>')
    g.append('<g filter="url(#s3-glow)"><circle cx="-1.4" cy="-41.4" r="0.9" fill="#FFE27A"/><circle cx="2.6" cy="-41.4" r="0.9" fill="#FFE27A"/></g>')
    g.append('<path d="M-2.6 -38.2 Q0.6 -36 3.6 -38.2" stroke="#F4EEDC" stroke-width="0.9" fill="none"/>')
    if ball:
        g.append('<g filter="url(#s3-glow2)"><circle cx="8.4" cy="-51" r="5.2" fill="url(#s3-ball)"/><circle cx="7.6" cy="-52" r="1.8" fill="#fff"/></g>')
    g.append('</g>')
    if hp is not None:
        g.append(hp_bar(x, y - 64 * s, hp, w=22, segs=5, enemy=True))
    return "".join(g)


def phantom(x, y, s=1.0):
    return (f'<g opacity="0.72" transform="translate({f(x)},{f(y)}) scale({f(s)})">'
            '<ellipse cx="14" cy="34" rx="12" ry="4" fill="#000" opacity="0.35" filter="url(#s3-sh)"/>' +
            f'<path d="M-9 -4 Q-10 -18 0 -20 Q10 -18 9 -4 Q8 6 2 12 Q6 16 3 22 Q-2 16 -4 10 Q-10 6 -9 -4Z" fill="url(#s3-ball)" filter="url(#s3-glow)"/>'
            '<circle cx="-3" cy="-9" r="1.4" fill="#0E1E3A"/><circle cx="3" cy="-9" r="1.4" fill="#0E1E3A"/>'
            '<path d="M-3 -4 Q0 -2 3 -4" stroke="#0E1E3A" stroke-width="0.9" fill="none"/>'
            '<path d="M-2 -19 L-4 -25 L0 -21 Z M2 -19 L4 -25 L0 -21 Z" fill="url(#s3-horn)"/></g>')


def rocket(sx, sy, ex, ey, t, bend=-30):
    """Rocket trail on a quadratic arc, head at fraction t."""
    cx, cy = (sx + ex) / 2, (sy + ey) / 2 + bend

    def q(tt):
        return ((1 - tt) ** 2 * sx + 2 * (1 - tt) * tt * cx + tt * tt * ex, (1 - tt) ** 2 * sy + 2 * (1 - tt) * tt * cy + tt * tt * ey)
    pts = [q(t * i / 24) for i in range(25)]
    hx_, hy_ = pts[-1]
    px, py = pts[-3]
    ang = math.degrees(math.atan2(hy_ - py, hx_ - px))
    g = [f'<polyline points="{P(pts)}" fill="none" stroke="#B9B2A4" stroke-width="5" stroke-opacity="0.3" filter="url(#s3-blur2)"/>',
         f'<polyline points="{P(pts[-14:])}" fill="none" stroke="#FFC266" stroke-width="1.6" stroke-opacity="0.85" stroke-linecap="round"/>']
    for k in range(8):
        a, b = pts[-1 - k * 2] if k * 2 < len(pts) else pts[0], None
        g.append(f'<circle cx="{f(a[0] + rnd.uniform(-2, 2))}" cy="{f(a[1] + rnd.uniform(-2, 2))}" r="{f(rnd.uniform(.4, 1))}" fill="#FFE7A8"/>')
    g.append(f'<g transform="translate({f(hx_)},{f(hy_)}) rotate({f(ang)})"><rect x="-9" y="-0.6" width="9" height="1.2" fill="#5A3A20"/>'
             f'<path d="M0 -1.4 L4 0 L0 1.4 Z" fill="url(#s3-iron)"/><circle cx="-9" cy="0" r="2.6" fill="#FFE9B0" filter="url(#s3-glow2)"/></g>')
    return "".join(g)


def fireball_arc(sx, sy, ex, ey, t, bend=-46):
    cx, cy = (sx + ex) / 2, (sy + ey) / 2 + bend

    def q(tt):
        return ((1 - tt) ** 2 * sx + 2 * (1 - tt) * tt * cx + tt * tt * ex, (1 - tt) ** 2 * sy + 2 * (1 - tt) * tt * cy + tt * tt * ey)
    pts = [q(t * i / 20) for i in range(21)]
    hx_, hy_ = pts[-1]
    return (f'<polyline points="{P(pts[-12:])}" fill="none" stroke="{R2}" stroke-width="5" stroke-opacity="0.35" stroke-linecap="round" filter="url(#s3-blur2)"/>'
            f'<polyline points="{P(pts[-7:])}" fill="none" stroke="{R2L}" stroke-width="2" stroke-linecap="round"/>'
            f'<g filter="url(#s3-glow2)"><circle cx="{f(hx_)}" cy="{f(hy_)}" r="5" fill="url(#s3-ball)"/><circle cx="{f(hx_ - 1)}" cy="{f(hy_ - 1)}" r="1.8" fill="#fff"/></g>')


def explosion(x, y, r=16):
    g = [f'<ellipse cx="{f(x)}" cy="{f(y + r * .3)}" rx="{f(r * 1.3)}" ry="{f(r * .5)}" fill="#1a0e06" opacity="0.6" filter="url(#s3-sh)"/>',
         f'<g filter="url(#s3-smoke)"><circle cx="{f(x - r * .5)}" cy="{f(y - r * 1.1)}" r="{f(r * .8)}" fill="url(#s3-smkD)"/><circle cx="{f(x + r * .5)}" cy="{f(y - r * 1.3)}" r="{f(r * .7)}" fill="url(#s3-smkD)"/></g>',
         f'<circle cx="{f(x)}" cy="{f(y - r * .4)}" r="{f(r)}" fill="url(#s3-fire)" filter="url(#s3-glow2)"/>']
    for k in range(10):
        a = k * math.pi * 2 / 10 + rnd.uniform(-.2, .2)
        d = r * rnd.uniform(1.0, 1.6)
        g.append(f'<line x1="{f(x + math.cos(a) * r * .6)}" y1="{f(y - r * .4 + math.sin(a) * r * .4)}" x2="{f(x + math.cos(a) * d)}" y2="{f(y - r * .4 + math.sin(a) * d * .7)}" stroke="#FFD98A" stroke-width="0.9" stroke-linecap="round"/>')
    for k in range(6):
        g.append(f'<rect x="{f(x + rnd.uniform(-r, r) * 1.4)}" y="{f(y - rnd.uniform(0, r * 2))}" width="2" height="1.4" fill="#2a1a0e" transform="rotate({rnd.randint(0, 90)})"/>' if False else
                 f'<circle cx="{f(x + rnd.uniform(-r, r) * 1.4)}" cy="{f(y - rnd.uniform(0, r * 1.8))}" r="{f(rnd.uniform(.6, 1.4))}" fill="#2a1a0e"/>')
    return "".join(g)


def smoke(x, y, r=8, n=4, dark=False):
    fill = "url(#s3-smkD)" if dark else "url(#s3-smk)"
    return (f'<g filter="url(#s3-smoke)" opacity="0.8">' + "".join(
        f'<circle cx="{f(x + rnd.uniform(-r, r))}" cy="{f(y - k * r * .8 - rnd.uniform(0, r * .5))}" r="{f(r * rnd.uniform(.7, 1.1))}" fill="{fill}"/>' for k in range(n)) + '</g>')


# ---------------------------------------------------------------- scene pieces
def stone_wall(x0, y0, x1, y1, h=13, cap=False):
    """Dry stone wall (돌담) along a straight line, built from individual stones."""
    L = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    g = [f'<polygon points="{P([(x0 + 4, y0 + 4), (x1 + 4, y1 + 4), (x1 + 16, y1 + 9), (x0 + 16, y0 + 9)])}" fill="#000" opacity="0.4" filter="url(#s3-sh)"/>']
    # wall core
    g.append(f'<polygon points="{P([(x0, y0), (x1, y1), (x1, y1 - h), (x0, y0 - h)])}" fill="#5A5446"/>')
    rows = 3
    for r in range(rows):
        d = rnd.uniform(0, 4)
        while d < L:
            w = rnd.uniform(5, 9)
            cx, cy = x0 + ux * (d + w / 2), y0 + uy * (d + w / 2) - (r + 0.5) * h / rows
            col = rnd.choice(["#9A927E", "#8A8270", "#B0A890", "#7A7262", "#A49A84"])
            g.append(f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(w / 2)}" ry="{f(h / rows * .62)}" fill="{col}" stroke="#2a2418" stroke-width="0.6"/>')
            g.append(f'<ellipse cx="{f(cx - w * .12)}" cy="{f(cy - h / rows * .22)}" rx="{f(w * .26)}" ry="{f(h / rows * .2)}" fill="#E8E0CA" opacity="0.4"/>')
            d += w * rnd.uniform(0.85, 1.0)
    # top stones (seen from above)
    d = 0
    while d < L:
        w = rnd.uniform(6, 10)
        cx, cy = x0 + ux * (d + w / 2), y0 + uy * (d + w / 2) - h
        g.append(f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(w / 2)}" ry="{f(2.6)}" fill="{rnd.choice(["#B9B19A", "#A8A08A", "#C6BEA6"])}" stroke="#2a2418" stroke-width="0.5"/>')
        d += w * .9
    return "".join(g)


def paddy(ax, ay, a, b, z, kind="ripe", edge_h=7):
    g = []
    # retaining earth/stone edge (front faces)
    g.append(box(ax, ay, a, b, edge_h, "#6B5A3A", "#7E6B48", "#5A4A30", z0=z - edge_h))
    g.append(stones_face(ax, ay, a, b, "L", z - edge_h, z, "#857456", rows=1))
    g.append(stones_face(ax, ay, a, b, "R", z - edge_h, z, "#5E4E36", rows=1))
    # bund (raised grass rim)
    n, e, s_, w = iso(ax, ay, 0, 0, z), iso(ax, ay, a, 0, z), iso(ax, ay, a, b, z), iso(ax, ay, 0, b, z)
    g.append(poly([n, e, s_, w], "#7C7A3E"))
    i_n, i_e, i_s, i_w = iso(ax, ay, .18, .18, z), iso(ax, ay, a - .18, .18, z), iso(ax, ay, a - .18, b - .18, z), iso(ax, ay, .18, b - .18, z)
    if kind == "water":
        g.append(f'<polygon points="{P([i_n, i_e, i_s, i_w])}" fill="url(#s3-paddyW)" filter="url(#s3-water)"/>')
        g.append(f'<polygon points="{P([i_n, i_e, lerp(i_e, i_s, .3), lerp(i_n, i_w, .3)])}" fill="#E8F2F0" opacity="0.18"/>')
        for r in range(1, int(b * 3)):
            for c in range(1, int(a * 3)):
                p = iso(ax, ay, .18 + c / 3 * (a - .36) / a * a / a * 1, .18 + r / 3, z) if False else iso(ax, ay, c / 3, r / 3, z)
                if c / 3 < a - .2 and r / 3 < b - .2:
                    g.append(f'<path d="M{f(p[0] - 1)} {f(p[1])} l0.6 -2.2 M{f(p[0] + 1)} {f(p[1])} l-0.4 -2" stroke="#C9A85A" stroke-width="0.6"/>')
    else:
        g.append(f'<polygon points="{P([i_n, i_e, i_s, i_w])}" fill="url(#s3-rice)" filter="url(#s3-tex)"/>')
        rows = int(b * 5)
        for r in range(rows):
            v = .25 + (b - .5) * r / max(1, rows - 1)
            cols = int(a * 7)
            for c in range(cols):
                u = .22 + (a - .44) * c / max(1, cols - 1)
                p = iso(ax, ay, u, v, z)
                col = rnd.choice(["#F2D474", "#D9B44E", "#C29A38", "#E8C860"])
                g.append(f'<path d="M{f(p[0] - 1.6)} {f(p[1] + .6)} q1.6 -3.6 {f(rnd.uniform(1.6, 3.2))} -5.4 M{f(p[0] + 1.4)} {f(p[1] + .6)} q-.6 -3 {f(-rnd.uniform(.6, 2))} -5" stroke="{col}" stroke-width="0.8" fill="none" stroke-linecap="round"/>')
                if rnd.random() < .5:
                    g.append(f'<circle cx="{f(p[0] + rnd.uniform(-1, 2))}" cy="{f(p[1] - 5)}" r="0.8" fill="#FFE9A0"/>')
    # inner rim shading
    g.append(f'<polyline points="{P([i_w, i_n, i_e])}" fill="none" stroke="#2a2010" stroke-opacity="0.5" stroke-width="1.2"/>')
    g.append(f'<polyline points="{P([i_w, i_s, i_e])}" fill="none" stroke="#E8E0B0" stroke-opacity="0.35" stroke-width="0.9"/>')
    for k in range(int((a + b) * 3)):
        t = rnd.random()
        p = lerp(w, s_, t) if k % 2 else lerp(s_, e, t)
        g.append(grass_tuft(p[0], p[1] + 1, 0.8))
    return "".join(g)


def crystal(x, y, h, w, lean=0.0):
    tip = (x + lean, y - h)
    l, r = (x - w / 2, y), (x + w / 2, y)
    ml, mr = (x - w * .55 + lean * .5, y - h * .55), (x + w * .5 + lean * .5, y - h * .6)
    mid = (x + lean * .4 + w * .05, y - h * .5)
    return (f'<polygon points="{P([l, ml, tip, mid, (x, y + 1)])}" fill="url(#s3-crysL)" stroke="#1E4A40" stroke-width="0.6"/>'
            f'<polygon points="{P([(x, y + 1), mid, tip, mr, r])}" fill="url(#s3-crysD)" stroke="#1E4A40" stroke-width="0.6"/>'
            f'<path d="M{f(ml[0] + 1)} {f(ml[1])} L{f(tip[0] - .4)} {f(tip[1] + 2)}" stroke="#FFFFFF" stroke-width="0.8" opacity="0.8"/>')


def ore_vein(cx, cy):
    g = [f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="120" ry="60" fill="url(#s3-oreglow)"/>']
    # rocky outcrop slabs
    for (dx, dy, r) in [(-70, -10, 22), (-30, -30, 28), (20, -24, 26), (62, -6, 20), (-48, 18, 18), (10, 14, 22), (52, 26, 16), (-10, -2, 16)]:
        g.append(boulder(cx + dx, cy + dy, r, col=rnd.choice(["#7E7868", "#6E6A5C", "#8A8472"])))
    crystals = []
    for k in range(26):
        x = cx + rnd.uniform(-90, 80)
        y = cy + rnd.uniform(-34, 34)
        crystals.append((y, x, rnd.uniform(10, 26), rnd.uniform(5, 9), rnd.uniform(-5, 5)))
    crystals.sort()
    for y, x, h, w, lean in crystals:
        g.append(crystal(x, y, h, w, lean))
    g.append(f'<g filter="url(#s3-glow2)" opacity="0.7">' + "".join(
        f'<circle cx="{f(cx + rnd.uniform(-90, 80))}" cy="{f(cy + rnd.uniform(-40, 30))}" r="{f(rnd.uniform(.8, 1.8))}" fill="{ORE_G}"/>' for _ in range(16)) + '</g>')
    return "".join(g)


def mist_cleft(x, y):
    g = [f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="70" ry="34" fill="url(#s3-gasglow)"/>',
         f'<path d="M{f(x - 46)} {f(y + 4)} L{f(x - 30)} {f(y - 4)} L{f(x - 18)} {f(y + 2)} L{f(x - 2)} {f(y - 8)} L{f(x + 14)} {f(y - 2)} L{f(x + 30)} {f(y - 10)} L{f(x + 46)} {f(y - 4)} '
         f'L{f(x + 34)} {f(y + 4)} L{f(x + 16)} {f(y + 6)} L{f(x)} {f(y + 2)} L{f(x - 16)} {f(y + 10)} L{f(x - 32)} {f(y + 8)} Z" fill="#120c1a" stroke="#3a2a4a" stroke-width="1.2"/>',
         f'<path d="M{f(x - 30)} {f(y + 2)} L{f(x - 2)} {f(y - 4)} L{f(x + 30)} {f(y - 6)}" stroke="#E4D4FA" stroke-width="1.6" fill="none" filter="url(#s3-glow2)" opacity="0.9"/>']
    for (dx, dy, r) in [(-50, 8, 9), (44, 4, 8), (-30, 16, 6), (28, 14, 7)]:
        g.append(boulder(x + dx, y + dy, r, col="#7A7268"))
    g.append(f'<g filter="url(#s3-mist)" opacity="0.75">' + "".join(
        f'<ellipse cx="{f(x + rnd.uniform(-20, 20) + k * 3)}" cy="{f(y - 14 - k * 16)}" rx="{f(22 + k * 5)}" ry="{f(10 + k * 2)}" fill="{GAS}" opacity="{f(0.55 - k * .08)}"/>' for k in range(6)) + '</g>')
    return "".join(g)


def bridge(ax, ay, length=4.0, width=1.15, deck=16):
    """Stone arch bridge (홍예교) running along +v."""
    g = [cast_shadow(ax, ay, width, length, deck * .6, grow=0.05, op=.35)]
    n0, e0, s0, w0 = iso(ax, ay, 0, 0, deck), iso(ax, ay, width, 0, deck), iso(ax, ay, width, length, deck), iso(ax, ay, 0, length, deck)
    # near side face (u=width) with arch opening
    side = [iso(ax, ay, width, 0, 0), iso(ax, ay, width, length, 0), s0, e0]
    g.append(poly(side, "#7E7666"))
    for r in range(4):
        z0, z1 = r * deck / 4, (r + 1) * deck / 4
        t = rnd.uniform(-.1, 0)
        while t < 1:
            t1 = min(1, t + rnd.uniform(.08, .16))
            q = [iso(ax, ay, width, max(0, t) * length, z0 + .5), iso(ax, ay, width, t1 * length, z0 + .5), iso(ax, ay, width, t1 * length, z1 - .5), iso(ax, ay, width, max(0, t) * length, z1 - .5)]
            g.append(f'<polygon points="{P(q)}" fill="{rnd.choice(["#8E8674", "#7A7262", "#9A927E", "#857D6B"])}" stroke="#2a2418" stroke-width="0.5"/>')
            t = t1
    arch = [iso(ax, ay, width + .01, length * (.25 + .5 * i / 20), deck * .78 * math.sin(math.pi * i / 20)) for i in range(21)]
    g.append(f'<polygon points="{P(arch)}" fill="#1A2C30" stroke="#C9C0A8" stroke-width="1.4"/>')
    g.append(f'<polygon points="{P(arch[2:19])}" fill="#2F5A64" opacity="0.6"/>')
    # voussoir stones around the arch
    for i in range(0, 21, 2):
        a0 = arch[i]
        g.append(f'<circle cx="{f(a0[0])}" cy="{f(a0[1] - 1)}" r="1.1" fill="#D2C9B0"/>')
    # deck
    g.append(poly([n0, e0, s0, w0], "#A49C86"))
    for k in range(1, int(length * 4)):
        p0, p1 = iso(ax, ay, 0, k / 4, deck), iso(ax, ay, width, k / 4, deck)
        g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="#6E6656" stroke-width="0.6"/>')
    # railings (low parapets with posts)
    for u in (0.04, width - 0.04):
        for k in range(9):
            p = iso(ax, ay, u, k * length / 8, deck)
            g.append(f'<rect x="{f(p[0] - 1.6)}" y="{f(p[1] - 8)}" width="3.2" height="8" fill="#B9B19A" {OUTL} stroke-width="0.5"/>'
                     f'<rect x="{f(p[0] - 2.2)}" y="{f(p[1] - 9.6)}" width="4.4" height="2" fill="#D2CAB2"/>')
        a, b = iso(ax, ay, u, 0, deck + 5), iso(ax, ay, u, length, deck + 5)
        g.append(f'<line x1="{f(a[0])}" y1="{f(a[1])}" x2="{f(b[0])}" y2="{f(b[1])}" stroke="#C6BEA6" stroke-width="2"/>')
    return "".join(g)


def armillary(x, y):
    g = [box(x - 0, y - 0, 0.9, 0.9, 10, "#B3AB94", "#958C76", "#6E6654")]
    c = (x, y + 14 - 34)
    g.append(f'<g filter="url(#s3-glow)"><circle cx="{f(c[0])}" cy="{f(c[1])}" r="3.6" fill="{ORE_G}"/></g>')
    for rx, ry, rot in [(13, 13, 0), (13, 5, -20), (13, 4, 50), (5, 13, 15)]:
        g.append(f'<ellipse cx="{f(c[0])}" cy="{f(c[1])}" rx="{rx}" ry="{ry}" fill="none" stroke="url(#s3-brass)" stroke-width="1.8" transform="rotate({rot} {f(c[0])} {f(c[1])})"/>')
    g.append(f'<path d="M{f(c[0] - 9)} {f(c[1] + 16)} L{f(c[0])} {f(c[1] + 8)} L{f(c[0] + 9)} {f(c[1] + 16)}" stroke="url(#s3-brassV)" stroke-width="2" fill="none"/>')
    return "".join(g)


def hongsal_gate(ax, ay, w=2.2, h=46):
    a, b = iso(ax, ay, 0, 0), iso(ax, ay, w, 0)
    g = []
    for p in (a, b):
        g.append(f'<rect x="{f(p[0] - 2.4)}" y="{f(p[1] - h)}" width="4.8" height="{f(h)}" fill="url(#s3-pillar)" {OUTL} stroke-width="0.6"/>')
    g.append(f'<line x1="{f(a[0])}" y1="{f(a[1] - h + 6)}" x2="{f(b[0])}" y2="{f(b[1] - h + 6)}" stroke="#8A3422" stroke-width="3.4"/>')
    g.append(f'<line x1="{f(a[0])}" y1="{f(a[1] - h + 14)}" x2="{f(b[0])}" y2="{f(b[1] - h + 14)}" stroke="#8A3422" stroke-width="2.4"/>')
    for k in range(1, 10):
        p = lerp(a, b, k / 10)
        g.append(f'<line x1="{f(p[0])}" y1="{f(p[1] - h + 14)}" x2="{f(p[0])}" y2="{f(p[1] - h - 6)}" stroke="#A8402A" stroke-width="1.4"/>')
    m = lerp(a, b, .5)
    g.append(f'<circle cx="{f(m[0])}" cy="{f(m[1] - h - 8)}" r="3.4" fill="{RED}" stroke="{YEL}" stroke-width="0.8"/><path d="M{f(m[0] - 2.4)} {f(m[1] - h - 8)} q2.4 -2 4.8 0 q-2.4 2 -4.8 0" fill="{BLUE_O}"/>')
    return "".join(g)


def target(x, y):
    return (f'<ellipse cx="{f(x + 6)}" cy="{f(y + 1)}" rx="8" ry="3" fill="#000" opacity="0.4" filter="url(#s3-sh)"/>' +
            f'<line x1="{f(x - 3)}" y1="{f(y)}" x2="{f(x)}" y2="{f(y - 14)}" stroke="#5A3A20" stroke-width="1.4"/><line x1="{f(x + 3)}" y1="{f(y)}" x2="{f(x)}" y2="{f(y - 14)}" stroke="#5A3A20" stroke-width="1.4"/>'
            f'<ellipse cx="{f(x)}" cy="{f(y - 20)}" rx="7" ry="8.4" fill="#D9B86A" stroke="#6A4A1A" stroke-width="0.8"/>'
            f'<ellipse cx="{f(x)}" cy="{f(y - 20)}" rx="4.6" ry="5.6" fill="#EFE6D2"/><ellipse cx="{f(x)}" cy="{f(y - 20)}" rx="2.2" ry="2.8" fill="{RED}"/>'
            f'<line x1="{f(x - 6)}" y1="{f(y - 24)}" x2="{f(x - 1)}" y2="{f(y - 20.4)}" stroke="#3A2414" stroke-width="0.8"/>')


def granary_wip(ax, ay, a=2.4, b=1.7, prog=0.62):
    g = [cast_shadow(ax, ay, a, b, 40, grow=.5, op=.35)]
    bx, by = iso(ax, ay, -.3, -.3)
    g.append(box(bx, by, a + .6, b + .6, 7, "#A79F88", "#8C836E", "#625A4A"))
    g.append(stones_face(bx, by, a + .6, b + .6, "L", 0, 7, "#9A917A", rows=1))
    g.append(stones_face(bx, by, a + .6, b + .6, "R", 0, 7, "#6A6252", rows=1))
    # floor beams + posts (stilts)
    posts = [(u, v) for u in (0, a / 2, a) for v in (0, b)]
    for (u, v) in posts:
        p = iso(ax, ay, u, v, 7)
        g.append(f'<rect x="{f(p[0] - 2)}" y="{f(p[1] - 40)}" width="4" height="40" fill="url(#s3-woodL)" {OUTL} stroke-width="0.5"/>')
    # partial plank walls (lower 60%)
    for side in ("L", "R"):
        L = a if side == "L" else b
        for k in range(int(L * 5)):
            t0, t1 = k / (L * 5), (k + .9) / (L * 5)
            if t1 > 1:
                break
            zt = 7 + 24 * (0.6 if k < L * 5 * prog else 0.25)
            q = face_quad(ax, ay, a, b, side, t0, t1, 7, zt)
            g.append(f'<polygon points="{P(q)}" fill="{"#B08254" if side == "L" else "#7A5634"}" stroke="#3a2414" stroke-width="0.5"/>')
    # top beams
    for (u0, v0, u1, v1) in [(0, b, a, b), (a, 0, a, b), (0, 0, a, 0), (0, 0, 0, b)]:
        p0, p1 = iso(ax, ay, u0, v0, 47), iso(ax, ay, u1, v1, 47)
        g.append(f'<line x1="{f(p0[0])}" y1="{f(p0[1])}" x2="{f(p1[0])}" y2="{f(p1[1])}" stroke="#6A4628" stroke-width="3" stroke-linecap="round"/>')
    # rafters + partial roof tiles (front half done)
    R0, R1 = iso(ax, ay, b / 2, b / 2, 70), iso(ax, ay, a - b / 2, b / 2, 70)
    g.append(f'<line x1="{f(R0[0])}" y1="{f(R0[1])}" x2="{f(R1[0])}" y2="{f(R1[1])}" stroke="#5A3A20" stroke-width="3"/>')
    for k in range(9):
        u = -.3 + k * (a + .6) / 8
        e = iso(ax, ay, u, b + .4, 48)
        uu = min(max(u, b / 2), a - b / 2)
        r = iso(ax, ay, uu, b / 2, 70)
        g.append(f'<line x1="{f(e[0])}" y1="{f(e[1])}" x2="{f(r[0])}" y2="{f(r[1])}" stroke="#7A5634" stroke-width="1.6"/>')
    rp, rq = iso(ax, ay, -.3, b + .4, 48), iso(ax, ay, (a + .6) * .55 - .3, b + .4, 48)
    rr = iso(ax, ay, (a + .6) * .55 - .3, b / 2 + .2, 66)
    rs = iso(ax, ay, b / 2, b / 2, 70)
    g.append(poly([rp, rq, rr, rs], "url(#s3-roofF)"))
    for k in range(10):
        t = (k + .5) / 10
        e = lerp(rp, rq, t)
        u = lerp(rs, rr, t)
        g.append(f'<line x1="{f(e[0])}" y1="{f(e[1])}" x2="{f(u[0])}" y2="{f(u[1])}" stroke="#8E97A2" stroke-width="1.6" stroke-opacity="0.8"/>')
    # scaffold poles + rope lashing
    for (u, v) in [(-0.7, b + .6), (a + .5, b + .6), (a + .6, -0.4)]:
        p = iso(ax, ay, u, v)
        g.append(f'<line x1="{f(p[0])}" y1="{f(p[1])}" x2="{f(p[0] + 2)}" y2="{f(p[1] - 78)}" stroke="#C9B27A" stroke-width="1.8"/>')
    a0, a1 = iso(ax, ay, -.7, b + .6, 34), iso(ax, ay, a + .5, b + .6, 34)
    g.append(f'<line x1="{f(a0[0])}" y1="{f(a0[1])}" x2="{f(a1[0])}" y2="{f(a1[1])}" stroke="#C9B27A" stroke-width="1.6"/>')
    g.append(f'<line x1="{f(a0[0])}" y1="{f(a0[1] - 2)}" x2="{f(a1[0])}" y2="{f(a1[1] - 2)}" stroke="#8A6A40" stroke-width="2.6" stroke-dasharray="3 1.2"/>')
    return "".join(g)


def lumber(x, y):
    g = [f'<ellipse cx="{f(x + 8)}" cy="{f(y + 3)}" rx="18" ry="5" fill="#000" opacity="0.4" filter="url(#s3-sh)"/>']
    for k in range(3):
        for j in range(3 - k):
            cx, cy = x - 8 + j * 6 + k * 3, y - k * 4.6
            g.append(f'<line x1="{f(cx - 10)}" y1="{f(cy + 5)}" x2="{f(cx + 10)}" y2="{f(cy - 5)}" stroke="#7A5634" stroke-width="4.4" stroke-linecap="round"/>')
            g.append(f'<ellipse cx="{f(cx - 10)}" cy="{f(cy + 5)}" rx="2.2" ry="2.2" fill="#D9B886" stroke="#5A3A20" stroke-width="0.5"/>')
    return "".join(g)


def watchtower(ax, ay):
    g = [cast_shadow(ax, ay, 1.2, 1.2, 110, grow=.3, op=.35)]
    bx, by = iso(ax, ay, -.25, -.25)
    g.append(box(bx, by, 1.7, 1.7, 22, "#A79F88", "#8C836E", "#625A4A"))
    g.append(stones_face(bx, by, 1.7, 1.7, "L", 0, 22, "#9A917A", rows=3))
    g.append(stones_face(bx, by, 1.7, 1.7, "R", 0, 22, "#6A6252", rows=3))
    for (u, v) in [(0, 0), (1.2, 0), (0, 1.2), (1.2, 1.2)]:
        p = iso(ax, ay, u, v, 22)
        g.append(f'<rect x="{f(p[0] - 2)}" y="{f(p[1] - 52)}" width="4" height="52" fill="url(#s3-pillar)" {OUTL} stroke-width="0.5"/>')
    for z in (40, 60):
        g.append(f'<polyline points="{P([iso(ax, ay, 0, 1.2, z), iso(ax, ay, 1.2, 1.2, z), iso(ax, ay, 1.2, 0, z)])}" fill="none" stroke="#5A3A20" stroke-width="1.8"/>')
    # x-bracing
    g.append(f'<line x1="{f(iso(ax, ay, 0, 1.2, 22)[0])}" y1="{f(iso(ax, ay, 0, 1.2, 22)[1])}" x2="{f(iso(ax, ay, 1.2, 1.2, 60)[0])}" y2="{f(iso(ax, ay, 1.2, 1.2, 60)[1])}" stroke="#6A4628" stroke-width="1.4"/>')
    g.append(f'<line x1="{f(iso(ax, ay, 1.2, 1.2, 22)[0])}" y1="{f(iso(ax, ay, 1.2, 1.2, 22)[1])}" x2="{f(iso(ax, ay, 1.2, 0, 60)[0])}" y2="{f(iso(ax, ay, 1.2, 0, 60)[1])}" stroke="#6A4628" stroke-width="1.4"/>')
    # platform deck + railing
    px, py = iso(ax, ay, -.3, -.3)
    g.append(box(px, py, 1.8, 1.8, 4, "#9A6E42", "#7A5230", "#5A3A20", z0=74))
    for k in range(7):
        p = iso(px, py, k * 1.8 / 6, 1.8, 78)
        q = iso(px, py, 1.8, k * 1.8 / 6, 78)
        g.append(f'<line x1="{f(p[0])}" y1="{f(p[1])}" x2="{f(p[0])}" y2="{f(p[1] - 8)}" stroke="#8A3422" stroke-width="1.4"/>')
        g.append(f'<line x1="{f(q[0])}" y1="{f(q[1])}" x2="{f(q[0])}" y2="{f(q[1] - 8)}" stroke="#5A2214" stroke-width="1.4"/>')
    g.append(f'<polyline points="{P([iso(px, py, 0, 1.8, 86), iso(px, py, 1.8, 1.8, 86), iso(px, py, 1.8, 0, 86)])}" fill="none" stroke="#A8402A" stroke-width="2"/>')
    # rain gauge (brass) on platform + sentry
    rg = iso(px, py, .5, .6, 78)
    g.append(f'<rect x="{f(rg[0] - 3)}" y="{f(rg[1] - 10)}" width="6" height="10" fill="url(#s3-brass)" stroke="#4E3610" stroke-width="0.5"/><ellipse cx="{f(rg[0])}" cy="{f(rg[1] - 10)}" rx="3" ry="1.2" fill="#2a2010"/>')
    g.append(gunner(*iso(px, py, 1.1, 1.1, 78), s=0.8, face=1, aim=-8))
    # small hip roof
    r, _, _ = roof(ax - 0, ay, 1.2, 1.2, 96, 20, o=.5, up=4)
    g.append(r)
    return "".join(g)


# ---------------------------------------------------------------- battlefield composition
STREAM = [(1000, -10), (1028, 100), (998, 178), (1012, 244), (1056, 330), (1030, 432), (1072, 548), (1050, 720)]
ROAD = [(470, 520), (956, 276)]
ROAD2 = [(1078, 214), (1330, 88)]


def smooth(pts, n=10):
    """Catmull-Rom sample."""
    out = []
    P_ = [pts[0]] + pts + [pts[-1]]
    for i in range(1, len(P_) - 2):
        p0, p1, p2, p3 = P_[i - 1], P_[i], P_[i + 1], P_[i + 2]
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    out.append(pts[-1])
    return out


def offset_band(line, w):
    left, right = [], []
    for i, (x, y) in enumerate(line):
        a = line[max(0, i - 1)]
        b = line[min(len(line) - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        ww = w * (1 + 0.12 * math.sin(i * .7))
        left.append((x + nx * ww, y + ny * ww * .8))
        right.append((x - nx * ww, y - ny * ww * .8))
    return left, right


def road_poly(a, b, w=22):
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy)
    nx, ny = -dy / L, dx / L
    pts = []
    n = 24
    for i in range(n + 1):
        t = i / n
        x, y = a[0] + dx * t, a[1] + dy * t
        j = w * (1 + rnd.uniform(-.12, .12))
        pts.append((x + nx * j, y + ny * j))
    for i in range(n, -1, -1):
        t = i / n
        x, y = a[0] + dx * t, a[1] + dy * t
        j = w * (1 + rnd.uniform(-.12, .12))
        pts.append((x - nx * j, y - ny * j))
    return pts


# ---------------------------------------------------------------- kit-rendered characters (override the flat v1 figures above)
import screen_figs as SF  # noqa: E402

KM = SF.SK("s3k")
KM.res = 8
_FIG = {}


def fig(key, fn, *args):
    """Render each distinct figure pose once into <defs>, then instance it with <use>."""
    if key not in _FIG:
        _FIG[key] = (f"s3-fig{len(_FIG) + 1}", fn(KM, *args))
    return _FIG[key][0]


def _var(x, y, n=3):
    """Deterministic per-instance face/build variant (n variants per unit type keeps the defs small)."""
    return int(abs(x) * 7 + abs(y) * 3) % n


def use(key, fn, args, x, y, sc):
    return f'<use href="#{fig(key, fn, *args)}" transform="translate({f(x)},{f(y)}) scale({f(sc)})"/>'


def gunner(x, y, s=1.0, face=1, aim=-14, fire=False, sel=False, hp=None, haunted=False, var=None):
    g = []
    if sel:
        g.append(ring(x, y))
    v = _var(x, y) if var is None else var
    g.append(use(("g", face, aim, fire, v), SF.gunner, (face, aim, fire, v), x, y, 0.45 * s))
    if haunted:
        g.append(soul_wisp(x - 3, y - 4, 0.8) + soul_wisp(x + 5, y - 14, 0.6))
    if hp is not None:
        g.append(hp_bar(x + 1, y - 50 * s, hp))
    return "".join(g)


def worker(x, y, face=1, mining=False, carry=False, hammer=False, s=1.0):
    mode = "mining" if mining else "hammer" if hammer else "carry" if carry else "idle"
    v = _var(x, y)
    return use(("w", face, mode, v), SF.worker, (face, mode, v), x, y, 0.42 * s)


def brute(x, y, face=-1, s=1.0, hp=None, swing=False):
    v = _var(x, y, 4)
    g = [use(("b", face, swing, v), SF.brute, (face, swing, v), x, y, 0.5 * s), wisps(x, y - 30 * s, 3, 20 * s)]
    if hp is not None:
        g.append(hp_bar(x, y - 78 * s, hp, w=28, segs=6, enemy=True))
    return "".join(g)


def changgwi(x, y, face=-1, s=1.0, hp=None, bolt=True):
    v = _var(x, y, 3)
    g = [use(("cg", face, bolt, v), SF.changgwi, (face, bolt, v), x, y, 0.5 * s)]
    if hp is not None:
        g.append(hp_bar(x, y - 74 * s, hp, w=22, segs=5, enemy=True))
    return "".join(g)


def eodukshini(x, y, s=1.0):
    return use(("ed",), SF.eodukshini, (0,), x, y, 0.72 * s)


def bulgasari(x, y, face=-1, s=1.0, hp=None):
    g = [f'<ellipse cx="{f(x)}" cy="{f(y + 2)}" rx="{f(62 * s)}" ry="{f(11 * s)}" fill="none" stroke="#DCF0F4" stroke-width="1.6" stroke-opacity="0.55"/>',
         f'<ellipse cx="{f(x)}" cy="{f(y + 3)}" rx="{f(74 * s)}" ry="{f(15 * s)}" fill="none" stroke="#DCF0F4" stroke-width="1" stroke-opacity="0.3"/>',
         use(("bg", face), SF.bulgasari, (face,), x, y, 0.72 * s)]
    if hp is not None:
        g.append(hp_bar(x + face * 8 * s, y - 86 * s, hp, w=46, segs=10, enemy=True))
    return "".join(g)


def soul_wisp(x, y, s=1.0):
    """Pale ghost-fire clinging to a unit marked by a 창귀."""
    return (f'<g filter="url(#s3-glow)"><path d="M{f(x - 3 * s)} {f(y)} Q{f(x - 5 * s)} {f(y - 7 * s)} {f(x)} {f(y - 14 * s)} Q{f(x + 5 * s)} {f(y - 6 * s)} {f(x + 3 * s)} {f(y)} Z" fill="#CFE4EA" opacity="0.8"/>'
            f'<circle cx="{f(x)}" cy="{f(y - 4 * s)}" r="{f(1.5 * s)}" fill="#FFF"/></g>')


def soul_bolt(sx, sy, ex, ey, t, bend=-30):
    cx, cy = (sx + ex) / 2, (sy + ey) / 2 + bend

    def q(tt):
        return ((1 - tt) ** 2 * sx + 2 * (1 - tt) * tt * cx + tt * tt * ex, (1 - tt) ** 2 * sy + 2 * (1 - tt) * tt * cy + tt * tt * ey)
    pts = [q(t * i / 20) for i in range(21)]
    hx_, hy_ = pts[-1]
    return (f'<polyline points="{P(pts[-13:])}" fill="none" stroke="#7FD4FF" stroke-width="5" stroke-opacity="0.3" stroke-linecap="round" filter="url(#s3-blur2)"/>'
            f'<polyline points="{P(pts[-8:])}" fill="none" stroke="#DCEFF4" stroke-width="1.6" stroke-opacity="0.85" stroke-linecap="round"/>'
            f'<g filter="url(#s3-glow2)"><circle cx="{f(hx_)}" cy="{f(hy_)}" r="4.4" fill="#BFE6F2"/><circle cx="{f(hx_ - 0.8)}" cy="{f(hy_ - 0.8)}" r="1.8" fill="#FFF"/></g>')


def battlefield():
    g = [f'<svg width="{FW}" height="{FH}" viewBox="0 0 {FW} {FH}" style="position:absolute;left:0;top:0" role="img" '
         'aria-label="전장: 좌측에 조선 공방 본진과 귀화석 광맥, 중앙 위쪽에 계단식 논, 오른쪽 개울과 돌다리 위에서 화승총수와 화차가 요물 무리와 교전 중">', DEFS]
    # --- base ground
    g.append(f'<rect width="{FW}" height="{FH}" fill="#6E6A3C"/>')
    g.append(f'<rect width="{FW}" height="{FH}" filter="url(#s3-mot1)"/>')
    g.append(f'<rect width="{FW}" height="{FH}" filter="url(#s3-mot2)" opacity="0.55"/>')
    # --- dirt: base yard, road, ore apron, stream banks
    yard = [iso(250, 330, -1.5, -1.0), iso(250, 330, 7.4, -1.0), iso(250, 330, 7.6, 5.4), iso(250, 330, -1.2, 5.6)]
    g.append(f'<polygon points="{P(yard)}" fill="#8A7652" filter="url(#s3-dirt)"/>')
    road1 = road_poly(*ROAD, w=24)
    road2 = road_poly(*ROAD2, w=22)
    for rp in (road1, road2):
        g.append(f'<polygon points="{P(rp)}" fill="#8E7754" filter="url(#s3-dirt)"/>')
    for (a, b) in (ROAD, ROAD2):
        for off in (-7, 7):
            dx, dy = b[0] - a[0], b[1] - a[1]
            L = math.hypot(dx, dy)
            nx, ny = -dy / L * off, dx / L * off
            g.append(f'<line x1="{f(a[0] + nx)}" y1="{f(a[1] + ny)}" x2="{f(b[0] + nx)}" y2="{f(b[1] + ny)}" stroke="#5E4A30" stroke-width="2.2" stroke-opacity="0.55" stroke-dasharray="40 6 22 4"/>')
    g.append(f'<ellipse cx="180" cy="168" rx="150" ry="72" fill="#7A6C50" filter="url(#s3-dirt)"/>')
    g.append(f'<ellipse cx="700" cy="604" rx="96" ry="44" fill="#7A6A52" filter="url(#s3-dirt)"/>')
    # explored-but-fogged far bank darker grass
    # --- stream
    line = smooth(STREAM, 8)
    bl, br = offset_band(line, 54)
    g.append(f'<polygon points="{P(bl + br[::-1])}" fill="#5A4A30" filter="url(#s3-dirt)"/>')
    wl, wr = offset_band(line, 36)
    g.append(f'<polygon points="{P(wl + wr[::-1])}" fill="url(#s3-waterG)" filter="url(#s3-water)" stroke="#20363a" stroke-width="1.5"/>')
    sl, sr = offset_band(line, 28)
    g.append(f'<polyline points="{P(sl)}" fill="none" stroke="#7FB0B4" stroke-opacity="0.35" stroke-width="6" filter="url(#s3-blur2)"/>')
    g.append(f'<polyline points="{P(wl)}" fill="none" stroke="#E8F2EC" stroke-opacity="0.55" stroke-width="1.2" stroke-dasharray="6 5 14 4"/>')
    g.append(f'<polyline points="{P(wr)}" fill="none" stroke="#E8F2EC" stroke-opacity="0.4" stroke-width="1" stroke-dasharray="10 6 4 8"/>')
    for i in range(0, len(line) - 1, 2):
        x, y = line[i]
        for _ in range(2):
            rx_, ry_ = x + rnd.uniform(-24, 24), y + rnd.uniform(-6, 6)
            g.append(f'<path d="M{f(rx_ - 6)} {f(ry_)} q6 -2.4 12 0" stroke="#DDEFEA" stroke-opacity="{f(rnd.uniform(.25, .55))}" stroke-width="0.8" fill="none"/>')
    # stream rocks + foam
    for (x, y, r) in [(986, 120, 7), (1040, 176, 6), (1022, 300, 8), (1068, 372, 7), (1044, 506, 9), (1060, 600, 7), (1002, 60, 6)]:
        g.append(f'<ellipse cx="{f(x - 4)}" cy="{f(y + 2)}" rx="{f(r + 5)}" ry="{f(r * .5 + 2)}" fill="#F4FAF6" opacity="0.5" filter="url(#s3-blur2)"/>')
        g.append(boulder(x, y, r, col="#7E786C"))
    # stepping stones (ford)
    for k in range(6):
        x, y = 996 + k * 13, 446 - k * 4 + (k % 2) * 3
        g.append(f'<ellipse cx="{f(x + 1)}" cy="{f(y + 1.6)}" rx="6.4" ry="3.2" fill="#14262a" opacity="0.6"/><ellipse cx="{f(x)}" cy="{f(y)}" rx="6" ry="3" fill="#A8A08A" stroke="#2a2418" stroke-width="0.6"/><ellipse cx="{f(x - 1.6)}" cy="{f(y - 1)}" rx="2.6" ry="1" fill="#E2DCC8" opacity="0.6"/>')
    # --- ground scatter (grass, flowers, pebbles, leaves), skipping water/roads/yard
    water_poly = bl + br[::-1]
    for _ in range(1500):
        x, y = rnd.uniform(0, FW), rnd.uniform(40, FH)
        if in_poly(x, y, water_poly) or in_poly(x, y, road1) or in_poly(x, y, road2) or in_poly(x, y, yard):
            continue
        r = rnd.random()
        if r < .62:
            g.append(grass_tuft(x, y, rnd.uniform(.8, 1.3)))
        elif r < .74:
            g.append(f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(rnd.uniform(.9, 1.5))}" fill="{rnd.choice(["#F2EEE0", "#D8C8F0", "#F6E28A"])}"/>')
        elif r < .87:
            g.append(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rnd.uniform(1, 2.4))}" ry="{f(rnd.uniform(.6, 1.2))}" fill="{rnd.choice(["#A49C88", "#8A8270", "#C2BAA4"])}" stroke="#2a2418" stroke-width="0.3"/>')
        else:
            g.append(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="1.6" ry="0.8" fill="{rnd.choice(["#C2412A", "#E0722E", "#F0A548", "#8E2A18"])}" transform="rotate({rnd.randint(0, 180)} {f(x)} {f(y)})"/>')
    for _ in range(260):  # pebbles on roads/yard
        x, y = rnd.uniform(0, FW), rnd.uniform(40, FH)
        if in_poly(x, y, road1) or in_poly(x, y, road2) or in_poly(x, y, yard):
            g.append(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rnd.uniform(.8, 2))}" ry="{f(rnd.uniform(.5, 1))}" fill="{rnd.choice(["#B3A688", "#6E5E44", "#C9BC9C"])}"/>')
    # --- terraced paddies (static, drawn before sorted objects)
    g.append(paddy(640, 58, 4.2, 2.5, 26, "ripe"))
    g.append(paddy(781, 128, 3.4, 2.5, 26, "water"))
    g.append(paddy(552, 104, 4.2, 2.5, 13, "water"))
    g.append(paddy(693, 174, 3.4, 2.5, 13, "ripe"))
    g.append(paddy(470, 152, 2.6, 2.4, 2, "ripe"))
    # --- sorted objects
    add(168, ore_vein(180, 170))
    add(264, stone_wall(0, 262, 140, 332))
    add(380, stone_wall(206, 364, 330, 426))
    add(620, stone_wall(560, 672, 690, 607))
    add(566, mist_cleft(880, 566))
    add(255, watchtower(392, 158))
    add(500, hanok(232, 334, 5.2, 3.0, wall_h=34, roof_h=36, base_h=10, bays=(5, 3), doors=((1, 2, 3), (1,)), glow=True))
    add(520, armillary(108, 470))
    for (x, y, wv) in [(112, 424, 1), (428, 452, 1)]:
        add(600, flag(x, y, 50, wave=wv))
    add(640, hanok(70, 556, 2.6, 1.8, wall_h=28, roof_h=24, base_h=7, open_walls=True, stairs=False))
    add(690, hongsal_gate(14, 586, w=1.9))
    add(675, target(40, 672))
    add(668, target(206, 660))
    add(636, granary_wip(452, 548))
    add(652, lumber(570, 640))
    add(225, bridge(1078, 196, length=4.1, width=1.2))
    add(150, straw_stack(625, 154))
    add(196, straw_stack(910, 202))
    add(160, straw_stack(930, 192))
    # trees
    for (x, y, h) in [(26, 120, 78), (70, 96, 66), (12, 230, 70), (1138, 506, 84), (1196, 560, 76), (1226, 486, 70), (850, 674, 72), (1270, 620, 80)]:
        add(y, pine(x, y, h))
    for (x, y, r) in [(940, 120, 24), (960, 64, 20), (1176, 430, 26), (780, 650, 22), (446, 440, 18), (1250, 560, 22), (900, 652, 20)]:
        add(y, maple(x, y, r))
    for (x, y, r) in [(420, 302, 9), (600, 300, 7), (1110, 610, 12), (520, 470, 6)]:
        add(y, boulder(x, y, r))
    # workers
    add(206, worker(128, 206, 1, mining=True))
    add(204, worker(236, 204, -1, mining=True))
    add(150, worker(268, 150, -1, mining=True))
    add(300, worker(302, 300, 1, carry=True))
    add(258, worker(338, 258, 1, carry=True, s=.95))
    add(608, worker(540, 608, -1, hammer=True))
    add(560, worker(456, 560, 1, hammer=True, s=.95))
    # --- battle: player
    gun_pos = [(792, 386, .95, False), (826, 368, .72, True), (812, 414, 1.0, True), (848, 398, .4, True), (858, 352, 1.0, False), (780, 432, .22, False)]
    for i, (x, y, hp, fire) in enumerate(gun_pos):
        add(y, gunner(x, y, s=1.12, face=1, aim=-24 if i % 2 else -16, fire=fire, sel=True, hp=hp, haunted=(hp < .3)))
    add(468, hwacha(648, 468, s=1.08, firing=True, sel=True, hp=0.86))
    add(512, hwacha(712, 512, s=1.08, firing=False, sel=True, hp=1.0))
    # --- battle: enemies
    add(258, brute(1000, 258 - 16, face=-1, s=1.0, hp=.55, swing=True))   # on the bridge deck
    add(232, brute(1040, 236 - 16, face=-1, s=.95, hp=.9))
    add(446, bulgasari(1040, 446, face=-1, s=1.0, hp=.7))                   # wading the ford, barrel in its jaws
    add(372, brute(904, 372, face=-1, s=1.0, hp=.3, swing=True))            # engaged
    add(304, changgwi(1126, 304, face=-1, s=1.0, hp=.8))
    add(368, changgwi(1150, 368, face=-1, s=1.0, hp=1.0, bolt=False))
    add(170, changgwi(1118, 170, face=-1, s=.95, hp=.6))
    # ground fire + scorch
    add(430, f'<ellipse cx="826" cy="430" rx="22" ry="8" fill="#10141C" opacity="0.55" filter="url(#s3-sh)"/>' + soul_wisp(816, 432, 1.0) + soul_wisp(832, 428, .75) + soul_wisp(842, 436, .6))
    add(470, f'<ellipse cx="1092" cy="262" rx="20" ry="7" fill="#1a0e06" opacity="0.65" filter="url(#s3-sh)"/>')
    # sort and emit
    for _, _, svg in sorted(DRAW):
        g.append(svg)
    # --- overlays: projectiles + explosions (above everything)
    g.append(rocket(676, 418, 1092, 262, .98, bend=-90))
    g.append(rocket(670, 420, 1126, 312, .72, bend=-110))
    g.append(rocket(682, 414, 1048, 222, .55, bend=-80))
    g.append(rocket(674, 416, 1160, 220, .35, bend=-120))
    g.append(explosion(1094, 262, 15))
    g.append(explosion(1018, 228, 11))
    g.append(soul_bolt(1100, 266, 842, 372, .7))
    g.append(soul_bolt(1094, 134, 812, 410, .45, bend=-70))
    g.append(smoke(1094, 240, 9, 4, dark=True))
    # selection-target marker on the engaged brute
    g.append(ring(904, 372, rx=16, enemy=True))
    # --- fog of war (ink wash): explored band + unexplored
    fog_ex = [(1210, 0), (1440, 0), (1440, 700), (1290, 700), (1240, 560), (1270, 420), (1200, 300), (1230, 160), (1196, 60)]
    fog_un = [(1320, 0), (1440, 0), (1440, 700), (1380, 700), (1350, 520), (1386, 380), (1330, 250), (1360, 120)]
    g.append(f'<polygon points="{P(fog_ex)}" fill="#0E0D0A" opacity="0.55" filter="url(#s3-ink)"/>')
    g.append(f'<g opacity="0.5"><rect width="{FW}" height="{FH}" filter="url(#s3-inkwash)" clip-path="url(#s3-fogclip)"/></g>')
    g.append(f'<clipPath id="s3-fogclip"><polygon points="{P(fog_ex)}"/></clipPath>')
    # remembered lair (ghosted)
    g.append('<g opacity="0.55"><ellipse cx="1340" cy="170" rx="60" ry="28" fill="url(#s3-lairglow)"/>'
             '<path d="M1312 190 C1306 150 1318 112 1340 98 C1362 112 1372 150 1366 190 Z" fill="#2a2a3a" stroke="#4FB3E8" stroke-width="1.2" stroke-dasharray="4 3"/>'
             '<path d="M1330 190 Q1338 168 1348 190" fill="#4FB3E8" opacity="0.6"/></g>')
    g.append(f'<polygon points="{P(fog_un)}" fill="#08070A" opacity="0.85" filter="url(#s3-ink)"/>')
    g.append(eodukshini(1262, 330, 1.0))
    # left/top edge darkening (vignette)
    g.append(f'<rect width="{FW}" height="{FH}" fill="none" stroke="#000" stroke-opacity="0.45" stroke-width="60" filter="url(#s3-blur9)"/>')
    g.append('</svg>')
    return "".join(g)


# ---------------------------------------------------------------- HUD pieces
def icon_ore(sz=20):
    return (f'<svg width="{sz}" height="{sz}" viewBox="0 0 20 20" aria-hidden="true">'
            '<polygon points="4,17 6,7 10,2 12,9 9,17" fill="url(#s3-crysL)" stroke="#1E4A40" stroke-width="0.7"/>'
            '<polygon points="9,17 12,9 15,5 17,12 15,17" fill="url(#s3-crysD)" stroke="#1E4A40" stroke-width="0.7"/>'
            '<path d="M7 8 L10 3" stroke="#fff" stroke-width="0.8"/></svg>')


def icon_gas(sz=20):
    return (f'<svg width="{sz}" height="{sz}" viewBox="0 0 20 20" aria-hidden="true">'
            f'<path d="M3 14 q3 -4 7 -2 q4 2 7 -2" stroke="{GAS}" stroke-width="2.2" fill="none" stroke-linecap="round"/>'
            '<path d="M5 9 q3 -4 6 -2 q3 2 5 -2" stroke="#E4D6FA" stroke-width="1.6" fill="none" stroke-linecap="round"/></svg>')


def icon_food(sz=20):
    return (f'<svg width="{sz}" height="{sz}" viewBox="0 0 20 20" aria-hidden="true">'
            '<path d="M3 10 h14 q0 7 -7 7 q-7 0 -7 -7z" fill="#8A5A2E" stroke="#2a1a0e" stroke-width="0.7"/>'
            f'<path d="M3.6 10 q6.4 -7 12.8 0z" fill="{FOOD}" stroke="#6A5A3A" stroke-width="0.6"/>'
            '<circle cx="8" cy="8" r="0.8" fill="#fff"/><circle cx="11" cy="7.4" r="0.8" fill="#fff"/></svg>')


def seal(sz=30, ch="參"):
    return (f'<svg width="{sz}" height="{sz}" viewBox="0 0 30 30" aria-hidden="true">'
            f'<rect x="1" y="1" width="28" height="28" rx="3" fill="{RED}" stroke="#7A1E10" stroke-width="1"/>'
            '<rect x="3.5" y="3.5" width="23" height="23" rx="1.5" fill="none" stroke="#F6E3C6" stroke-width="1.1"/>'
            f'<text x="15" y="21" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="16" fill="#F6E3C6">{ch}</text></svg>')


PARCH = ("background:linear-gradient(180deg,#EADCB8 0%,#D9C79C 55%,#C7B184 100%);"
         "border:1px solid #5A3A1E;box-shadow:inset 0 1px 0 rgba(255,250,230,.7), inset 0 -2px 4px rgba(90,58,30,.35), 0 1px 0 rgba(0,0,0,.6);")
LACQ = ("background:linear-gradient(180deg,#2A1B12,#140D08);border:1px solid #6A4A22;"
        "box-shadow:inset 0 1px 0 rgba(255,220,160,.12), inset 0 0 8px rgba(0,0,0,.7);")
INKC = "#2A1C10"


def cloud_scroll(x, y, s=1.0, flip=1):
    return (f'<g transform="translate({f(x)},{f(y)}) scale({f(s * flip)},{f(s)})">'
            '<path d="M0 0 C6 -10 20 -10 22 -2 C24 4 16 8 12 4 C9 1 13 -3 16 -1" fill="none" stroke="url(#s3-brassV)" stroke-width="2.4" stroke-linecap="round"/>'
            '<path d="M-2 4 C10 6 26 4 34 -4" fill="none" stroke="url(#s3-brassV)" stroke-width="1.6" stroke-linecap="round"/></g>')


def rivet(x, y, r=2.2):
    return f'<circle cx="{f(x)}" cy="{f(y)}" r="{r}" fill="url(#s3-brass)" stroke="#2a1a08" stroke-width="0.6"/><circle cx="{f(x - r * .3)}" cy="{f(y - r * .3)}" r="{f(r * .35)}" fill="#FFF6D0"/>'


def corner_fitting(x, y, sx=1, sy=1):
    return (f'<g transform="translate({x},{y}) scale({sx},{sy})">'
            '<path d="M0 0 H26 L22 5 H5 V22 L0 26 Z" fill="url(#s3-brass)" stroke="#3a2608" stroke-width="0.8"/>'
            '<path d="M3 3 H19 M3 3 V19" stroke="#FFF0C0" stroke-width="0.7" opacity="0.7"/>'
            f'{rivet(9, 2.6, 1.6)}{rivet(2.6, 9, 1.6)}</g>')


def top_bar_svg():
    w, h = 1440, 60
    g = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" style="position:absolute;left:0;top:0" aria-hidden="true">', '<defs>',
         '<linearGradient id="s3-plankSh" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE0A8" stop-opacity="0.14"/><stop offset="0.5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.45"/></linearGradient>',
         '</defs>',
         f'<rect x="0" y="0" width="{w}" height="50" fill="#3A2416"/>',
         f'<rect x="0" y="0" width="{w}" height="50" fill="#fff" filter="url(#s3-woodf)"/>',
         f'<rect x="0" y="0" width="{w}" height="50" fill="url(#s3-plankSh)"/>']
    for x in (360, 720, 1080):
        g.append(f'<line x1="{x}" y1="0" x2="{x}" y2="50" stroke="#120a05" stroke-width="1.2"/><line x1="{x + 1.2}" y1="0" x2="{x + 1.2}" y2="50" stroke="#8A5A34" stroke-opacity="0.4" stroke-width="0.8"/>')
    # brass rails
    g.append(f'<rect x="0" y="48" width="{w}" height="4" fill="url(#s3-brassV)"/><rect x="0" y="52" width="{w}" height="2" fill="#120a05"/>')
    g.append(f'<rect x="0" y="0" width="{w}" height="2" fill="url(#s3-brassV)" opacity="0.8"/>')
    # dancheong strip under the rail
    for i in range(0, w, 36):
        for j, (c, ww) in enumerate([(RED, 12), (GRN, 10), (YEL, 4), (BLUE_O, 10)]):
            off = sum(v for _, v in [(RED, 12), (GRN, 10), (YEL, 4), (BLUE_O, 10)][:j])
            g.append(f'<rect x="{i + off}" y="54" width="{ww}" height="4" fill="{c}" opacity="0.92"/>')
    g.append(f'<rect x="0" y="58" width="{w}" height="2" fill="#000" opacity="0.6"/>')
    for x in range(14, w, 120):
        g.append(rivet(x, 50, 1.8))
    # clock cartouche (center)
    g.append('<path d="M712 6 H928 L944 25 L928 44 H712 L696 25 Z" fill="#140D08" stroke="url(#s3-brassV)" stroke-width="2"/>')
    g.append('<path d="M718 10 H922 L934 25 L922 40 H718 L706 25 Z" fill="none" stroke="#C9A35A" stroke-opacity="0.4" stroke-width="0.8"/>')
    g.append(cloud_scroll(660, 30, .9, 1) + cloud_scroll(980, 30, .9, -1))
    g.append('</svg>')
    return "".join(g)


def top_bar():
    num = "font-family:'IBM Plex Mono',monospace;font-size:15px;font-weight:600;color:" + INKC
    sub = f"font-size:11px;color:#5A3E22;font-weight:600"
    def chip(icon, val, inc, label):
        return (f'<span style="display:flex;align-items:center;gap:7px;padding:4px 12px 4px 8px;border-radius:2px;{PARCH}">{icon}'
                f'<span style="{num}">{val}</span>'
                + (f'<span style="font-family:\'IBM Plex Mono\',monospace;font-size:11px;color:#2E6A3A;font-weight:600">{inc}</span>' if inc else "")
                + f'<span style="{sub}">{label}</span></span>')
    return f"""
  {top_bar_svg()}
  <div style="position:absolute;left:0;top:0;width:1440px;height:48px;display:flex;align-items:center;padding:0 14px;box-sizing:border-box;gap:14px">
    <div style="display:flex;align-items:center;gap:9px">
      {seal(30)}
      <span style="font-family:'Nanum Myeongjo',serif;font-weight:800;font-size:21px;letter-spacing:.04em;color:#F6E3B4;text-shadow:0 1px 0 #000, 0 0 6px rgba(0,0,0,.6)">삼파전</span>
      <span style="font-family:'IBM Plex Mono',monospace;font-size:11px;color:#D9C49A;letter-spacing:.06em">1P · 조선 공방</span>
    </div>
    <div style="display:flex;gap:7px;margin-left:6px">
      {chip(icon_ore(), "540", "+12", "귀화석")}
      {chip(icon_gas(), "80", "+4", "산기")}
      {chip(icon_food(), "14<span style='color:#6A4E2E;font-weight:400'> / 30</span>", "", "식량")}
    </div>
    <div style="position:absolute;left:700px;top:0;width:240px;height:50px;display:flex;justify-content:center;align-items:center;gap:10px">
      <span style="font-family:'IBM Plex Mono',monospace;font-size:21px;font-weight:600;letter-spacing:.05em;color:#F2D58A;text-shadow:0 1px 0 #000">06:42.1</span>
      <span style="font-family:'IBM Plex Mono',monospace;font-size:11px;color:#C9B48A">T 020105</span>
    </div>
    <div style="margin-left:auto;display:flex;align-items:center;gap:9px;font-family:'IBM Plex Mono',monospace;font-size:11px">
      <span style="color:{OK};display:flex;align-items:center;gap:6px;text-shadow:0 1px 0 #000"><span style="width:7px;height:7px;border-radius:50%;background:{OK};box-shadow:0 0 6px {OK}"></span>동기화 0x7F3A·91C2</span>
      <span style="color:#D9C49A">핑 32ms</span>
      <span style="padding:5px 11px;border-radius:2px;color:#F6E3B4;font-family:'IBM Plex Sans KR',sans-serif;font-size:12px;{LACQ}">단축키 <b style="font-family:'IBM Plex Mono',monospace;color:{YEL};font-weight:600">F1</b></span>
      <span style="padding:5px 11px;border-radius:2px;color:#F6E3B4;font-family:'IBM Plex Sans KR',sans-serif;font-size:12px;{LACQ}">메뉴 <b style="font-family:'IBM Plex Mono',monospace;color:{YEL};font-weight:600">F10</b></span>
    </div>
  </div>"""


def wood_tag(inner, accent, w=284):
    return (f'<div style="position:relative;width:{w}px;box-sizing:border-box;padding:11px 14px 11px 16px;display:flex;gap:12px;align-items:center;'
            f'background:linear-gradient(180deg,#4A2E1C,#2A1A10);border:1px solid #120a05;border-radius:3px;'
            f'box-shadow:inset 0 0 0 2px rgba(201,163,90,.55), inset 0 1px 0 rgba(255,220,160,.25), 0 6px 16px rgba(0,0,0,.55)">'
            f'<span style="position:absolute;left:0;top:6px;bottom:6px;width:4px;background:{accent};box-shadow:0 0 8px {accent}"></span>{inner}</div>')


def toasts():
    a = wood_tag(f"""{seal(32, "急")}
      <div style="display:flex;flex-direction:column;gap:3px">
        <span style="font-family:'Nanum Myeongjo',serif;font-size:16px;font-weight:800;color:#F6E3B4">본진이 공격받고 있습니다</span>
        <span style="font-family:'IBM Plex Mono',monospace;font-size:11px;color:#D9C49A"><b style="color:{YEL};font-weight:600">Space</b> 로 이동 · 3초 전</span>
      </div>""", ALERT)
    b = wood_tag(f"""<svg width="26" height="26" viewBox="-13 -13 26 26" aria-hidden="true"><circle r="11" fill="#1a110a" stroke="url(#s3-brass)" stroke-width="1.6"/><path d="M-5 0 L-1.4 4 L5.6 -4" stroke="{OK}" stroke-width="2.2" fill="none" stroke-linecap="round"/></svg>
      <div style="display:flex;flex-direction:column;gap:2px">
        <span style="font-size:13px;font-weight:600;color:#F2E6CC">공방 일꾼 생산 완료</span>
        <span style="font-family:'IBM Plex Mono',monospace;font-size:11px;color:#C9B48A">관상감 본청 · 대기열 2</span>
      </div>""", OK)
    return f'<div style="position:absolute;right:14px;top:70px;display:flex;flex-direction:column;gap:8px;align-items:flex-end">{a}{b}</div>'


def armed_hint():
    return f"""
  <div style="position:absolute;left:770px;top:598px;transform:translateX(-50%);display:flex;align-items:center;gap:12px;padding:7px 16px 7px 8px;border-radius:2px;{PARCH}">
    {seal(26, "擊")}
    <span style="font-family:'Nanum Myeongjo',serif;font-size:15px;font-weight:800;color:#8A1E10">공격 이동</span>
    <span style="font-size:12px;color:{INKC};font-weight:500">지점이나 적을 좌클릭 · <b style="font-family:'IBM Plex Mono',monospace;color:#7A1E10;font-weight:600">Shift</b> 예약 · <b style="font-family:'IBM Plex Mono',monospace;color:#7A1E10;font-weight:600">Esc</b> 취소</span>
  </div>"""


def group_tabs():
    tabs = [("1", "본청", False), ("2", "화승총수 6", True), ("3", "화차 2", False), ("4", "일꾼 8", False)]
    out = []
    for k, label, active in tabs:
        bg = "linear-gradient(180deg,#6A4224,#3A2414)" if active else "linear-gradient(180deg,#3A2416,#22160E)"
        bd = YEL if active else "#6A4A22"
        out.append(f'<span style="display:flex;align-items:center;gap:6px;padding:4px 11px 5px;background:{bg};border:1px solid {bd};border-bottom:none;border-radius:4px 4px 0 0;font-size:12px;color:#F2E6CC;'
                   f'box-shadow:inset 0 1px 0 rgba(255,220,160,.2){";0 0 10px rgba(226,177,60,.35)" if active else ""}">'
                   f'<b style="font-family:\'IBM Plex Mono\',monospace;color:{YEL};font-weight:600">{k}</b>{label}</span>')
    idle = (f'<span style="display:flex;align-items:center;gap:6px;padding:4px 11px 5px;background:linear-gradient(180deg,#3A2416,#22160E);border:1px solid {YEL};border-bottom:none;border-radius:4px 4px 0 0;font-size:12px;color:#F2E6CC">'
            f'<svg width="16" height="18" viewBox="-8 -40 18 42" aria-hidden="true">{worker(0, 0, 1)}</svg>'
            f'유휴 일꾼 <b style="font-family:\'IBM Plex Mono\',monospace;color:{YEL}">1</b></span>')
    return (f'<div style="position:absolute;left:14px;top:642px;display:flex;gap:4px">{idle}</div>'
            f'<div style="position:absolute;left:232px;top:642px;display:flex;gap:4px">{"".join(out)}</div>')


def minimap():
    m = ['<svg width="172" height="172" viewBox="0 0 172 172" role="img" aria-label="미니맵: 좌하단 아군 본진, 우상단 적 소굴, 개울과 교전 지점">',
         '<rect width="172" height="172" fill="#fff" filter="url(#s3-parch)"/>',
         # painted terrain on parchment (ink + watercolour)
         '<path d="M0 0 H172 V172 H0Z" fill="#7A8A4A" opacity="0.18"/>',
         '<path d="M118 0 C106 30 122 52 108 80 S90 120 102 172" stroke="#3E6E7A" stroke-width="8" fill="none" opacity="0.75"/>',
         '<path d="M118 0 C106 30 122 52 108 80 S90 120 102 172" stroke="#BFD8DA" stroke-width="2" fill="none" opacity="0.6"/>',
         '<path d="M0 118 C30 110 60 98 108 82 S148 68 172 62" stroke="#8A6A3E" stroke-width="2.4" fill="none" stroke-dasharray="5 2"/>',
         '<g fill="#C9A44E" stroke="#6A4A1A" stroke-width="0.6"><rect x="46" y="66" width="13" height="8"/><rect x="61" y="66" width="12" height="8"/><rect x="48" y="76" width="22" height="8"/></g>',
         '<g fill="#3E5A34" opacity="0.85"><circle cx="130" cy="68" r="6"/><circle cx="138" cy="76" r="5"/><circle cx="126" cy="94" r="5"/><circle cx="18" cy="38" r="7"/><circle cx="28" cy="28" r="5"/><circle cx="84" cy="150" r="6"/></g>',
         '<g fill="#A8402A" opacity="0.8"><circle cx="100" cy="30" r="3"/><circle cx="96" cy="140" r="3"/></g>',
         '<path d="M6 4 C30 12 22 30 8 40" stroke="#5A4A3A" stroke-width="1.2" fill="none"/><path d="M146 150 l6 -8 l6 8 M140 154 l5 -6 l5 6" stroke="#5A4A3A" stroke-width="1" fill="none"/>',
         f'<g fill="{ORE}" stroke="#1E4A40" stroke-width="0.6"><path d="M20 76 l3 -5 l3 5z"/><path d="M26 74 l3 -5 l3 5z"/><path d="M150 150 l3 -5 l3 5z"/><path d="M88 26 l3 -5 l3 5z"/></g>',
         f'<g fill="{GAS}"><circle cx="58" cy="98" r="2.6"/><circle cx="140" cy="20" r="2.6"/><circle cx="20" cy="150" r="2.6"/></g>',
         f'<g fill="{R1}" stroke="#3a0e06" stroke-width="0.6"><rect x="26" y="86" width="11" height="9"/><rect x="16" y="98" width="6" height="6"/><rect x="40" y="100" width="5" height="5"/><rect x="62" y="84" width="3" height="3"/></g>',
         f'<g fill="#8A1E10"><circle cx="72" cy="88" r="1.6"/><circle cx="76" cy="90" r="1.6"/><circle cx="74" cy="93" r="1.6"/><circle cx="68" cy="94" r="1.6"/><circle cx="29" cy="78" r="1.3"/><circle cx="33" cy="77" r="1.3"/></g>',
         f'<g fill="#1F5F8E"><circle cx="84" cy="86" r="1.8"/><circle cx="88" cy="84" r="1.8"/><circle cx="86" cy="90" r="1.8"/><circle cx="92" cy="88" r="1.8"/></g>',
         '<rect x="146" y="14" width="14" height="13" fill="none" stroke="#1F5F8E" stroke-width="1.4" stroke-dasharray="2 1.5"/>',
         '<path d="M100 0 H172 V172 H118 C124 140 108 110 122 84 C132 60 108 30 100 0Z" fill="#14100A" opacity="0.4"/>',
         '<path d="M138 0 H172 V172 H148 C154 130 138 100 150 70 C158 40 138 20 138 0Z" fill="#14100A" opacity="0.55"/>',
         '<path d="M0 0 H58 C40 10 20 14 0 30Z" fill="#14100A" opacity="0.55"/>',
         '<path d="M0 148 C20 138 50 148 68 172 H0Z" fill="#14100A" opacity="0.55"/>',
         f'<circle cx="32" cy="90" r="8" fill="none" stroke="{RED}" stroke-width="1.6"/><circle cx="32" cy="90" r="13" fill="none" stroke="{RED}" stroke-width="1" opacity="0.6"/>',
         '<rect x="18" y="66" width="80" height="36" fill="none" stroke="#1a0f08" stroke-width="1.4"/>',
         '<rect x="18" y="66" width="80" height="36" fill="none" stroke="#FFF6D8" stroke-width="0.6" stroke-dasharray="3 2"/>',
         '<g transform="translate(158,158)"><circle r="9" fill="#EADCB8" stroke="#5A3A1E" stroke-width="0.8"/><path d="M0 -7 L2 0 L0 7 L-2 0Z" fill="#8A1E10"/><text y="-9.5" text-anchor="middle" font-size="6" font-family="Nanum Myeongjo, serif" font-weight="800" fill="#2A1C10">北</text></g>',
         '</svg>']
    return "".join(m)


def portrait():
    return f"""<svg width="132" height="132" viewBox="0 0 132 132" role="img" aria-label="화승총수 초상">
  <defs>
    <clipPath id="s3-pclip"><circle cx="66" cy="66" r="58"/></clipPath>
    <radialGradient id="s3-pbg" cx="0.42" cy="0.32" r="0.8"><stop offset="0" stop-color="#7A3A20"/><stop offset="0.55" stop-color="#3A1C10"/><stop offset="1" stop-color="#140A06"/></radialGradient>
    <radialGradient id="s3-pskin" cx="0.38" cy="0.35" r="0.75"><stop offset="0" stop-color="#F6D8B0"/><stop offset="0.6" stop-color="#D4A676"/><stop offset="1" stop-color="#7E5434"/></radialGradient>
  </defs>
  <g clip-path="url(#s3-pclip)">
  <rect width="132" height="132" fill="url(#s3-pbg)"/>
  <circle cx="30" cy="34" r="30" fill="#FFB060" opacity="0.18" filter="url(#s3-blur9)"/>
  <path d="M0 96 Q30 84 66 90 T132 86 V132 H0Z" fill="#1a0e08" opacity="0.6"/>
  <g transform="rotate(-62 104 110)"><rect x="40" y="106" width="96" height="7" rx="2" fill="url(#s3-gun)" stroke="#1E140C"/><rect x="96" y="107" width="40" height="5" fill="url(#s3-iron)"/>
    <rect x="70" y="105" width="3" height="9" fill="url(#s3-brass)"/><rect x="88" y="105" width="3" height="9" fill="url(#s3-brass)"/></g>
  <circle cx="114" cy="62" r="2.4" fill="#FFB060" filter="url(#s3-glow)"/>
  <g transform="translate(-6 -12) scale(1.2)">{SF.bust_r1("s3")}</g>
  </g>
  <circle cx="66" cy="66" r="58" fill="none" stroke="#0a0604" stroke-width="3"/>
  <circle cx="66" cy="66" r="61" fill="none" stroke="url(#s3-brass)" stroke-width="5"/>
  <circle cx="66" cy="66" r="64" fill="none" stroke="#2a1a08" stroke-width="1"/>
  {"".join(rivet(66 + math.cos(a) * 61, 66 + math.sin(a) * 61, 1.6) for a in [i * math.pi / 4 + math.pi / 8 for i in range(8)])}
</svg>"""


def mini_unit_card(hp, primary=False, var=0):
    col = OK if hp > 0.6 else ("#E8C547" if hp > 0.3 else ALERT)
    bd = YEL if primary else "#6A4A22"
    return (f'<div style="width:54px;height:64px;display:flex;flex-direction:column;align-items:center;justify-content:space-between;padding:3px 0 0;box-sizing:border-box;'
            f'background:radial-gradient(circle at 40% 30%,#4A2E1C,#1A100A);border:1px solid {bd};border-radius:3px;'
            f'box-shadow:inset 0 1px 0 rgba(255,220,160,.2){";0 0 10px rgba(226,177,60,.45)" if primary else ""}">'
            f'<svg width="44" height="50" viewBox="-14 -48 32 52" aria-hidden="true">{gunner(0, 0, s=1.0, face=1, aim=-14, var=var)}</svg>'
            f'<div style="width:100%;height:5px;background:#0B0A08"><div style="width:{int(hp * 100)}%;height:100%;background:{col}"></div></div></div>')


def selection_panel():
    cards = "".join(mini_unit_card(hp, i == 0, i % 3) for i, hp in enumerate([0.95, 0.72, 1.0, 0.4, 1.0, 0.22]))
    return f"""
    <div style="position:absolute;left:232px;top:46px;width:756px;height:164px;display:flex;gap:16px;align-items:flex-start">
      <div style="position:relative;width:136px;height:136px;flex:none;margin-top:2px">{portrait()}
        <span style="position:absolute;left:18px;bottom:-20px;width:100px;text-align:center;font-family:'IBM Plex Mono',monospace;font-size:11px;color:#D9C49A">U-A2 · 1×1</span></div>
      <div style="display:flex;flex-direction:column;gap:5px;width:236px;flex:none;padding:10px 12px;box-sizing:border-box;border-radius:2px;{PARCH}">
        <div style="display:flex;align-items:baseline;gap:8px"><span style="font-family:'Nanum Myeongjo',serif;font-weight:800;font-size:22px;line-height:1.1;color:{INKC}">화승총수</span><span style="font-family:'IBM Plex Mono',monospace;font-size:13px;color:#8A1E10;font-weight:600">×6</span></div>
        <span style="font-size:12px;color:#5A3E22;font-weight:500">조선 공방 · 보병 · 원거리</span>
        <div style="display:flex;flex-direction:column;gap:3px;margin-top:2px">
          <div style="display:flex;justify-content:space-between;font-family:'IBM Plex Mono',monospace;font-size:12px;color:{INKC}"><span style="color:#5A3E22">체력</span><span style="font-weight:600">212 / 270</span></div>
          <div style="height:8px;background:#2a1a0e;border:1px solid #5A3A1E;position:relative"><div style="width:78%;height:100%;background:linear-gradient(#9AD6A6,#4E8A5A)"></div></div>
        </div>
        <div style="display:grid;grid-template-columns:auto 1fr;column-gap:12px;row-gap:2px;font-size:12px;color:{INKC}">
          <span style="color:#5A3E22">상태</span><span style="color:#8A1E10;font-weight:600">공격 이동 중</span>
          <span style="color:#5A3E22">무기</span><span style="font-family:'IBM Plex Mono',monospace">피해 6 · 사거리 5</span>
          <span style="color:#5A3E22">화약</span><span style="font-family:'IBM Plex Mono',monospace">일제 사격 4초 후</span>
        </div>
      </div>
      <div style="display:flex;flex-direction:column;gap:7px;padding-top:2px">
        <span style="font-size:12px;color:#D9C49A">부대 <b style="font-family:'IBM Plex Mono',monospace;color:{YEL};font-weight:600">Ctrl+2</b> · 클릭해 단일 선택</span>
        <div style="display:grid;grid-template-columns:repeat(6,54px);gap:6px">{cards}</div>
        <div style="display:flex;gap:6px;margin-top:2px;align-items:center;font-size:12px;color:#F2C4B8">
          <span style="display:inline-block;width:10px;height:10px;background:{R2};border-radius:50%;box-shadow:0 0 6px {R2}"></span>R6 창귀의 원혼 · 쓰러지면 적에게 끌려감
        </div>
      </div>
    </div>"""


GLYPH = {
    "move": '<path d="M4 12 H17 M12 6 L18 12 L12 18" stroke="url(#s3-gly)" stroke-width="2.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
    "stop": '<rect x="6" y="6" width="12" height="12" rx="1.5" fill="url(#s3-gly)" stroke="#3a2608" stroke-width="0.8"/>',
    "attack": '<circle cx="12" cy="12" r="6.5" stroke="url(#s3-glyR)" stroke-width="2.4" fill="none"/><path d="M12 2 V7 M12 17 V22 M2 12 H7 M17 12 H22" stroke="url(#s3-glyR)" stroke-width="2.4" stroke-linecap="round"/><circle cx="12" cy="12" r="1.8" fill="#FFF0E0"/>',
    "hold": '<path d="M12 3 L19 6 V12 C19 16 16 19 12 21 C8 19 5 16 5 12 V6 Z" fill="url(#s3-gly)" stroke="#3a2608" stroke-width="0.8"/><path d="M12 7 V16" stroke="#3a2608" stroke-width="2"/>',
    "patrol": '<path d="M5 10 A7 7 0 0 1 18 8" stroke="url(#s3-gly)" stroke-width="2.4" fill="none" stroke-linecap="round"/><path d="M18 3.5 V8.2 H13.5" stroke="url(#s3-gly)" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M19 14 A7 7 0 0 1 6 16" stroke="url(#s3-gly)" stroke-width="2.4" fill="none" stroke-linecap="round"/><path d="M6 20.5 V15.8 H10.5" stroke="url(#s3-gly)" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
    "volley": '<path d="M3 8 H13 M3 12 H16 M3 16 H13" stroke="url(#s3-gly)" stroke-width="2.2" stroke-linecap="round"/><path d="M17 12 L21 9 L20 12 L21 15Z" fill="#FFD27A"/><circle cx="18" cy="12" r="2.4" fill="#FFD27A"/>',
    "powder": '<path d="M10 3 h4 v3 c4 1 6 4 6 8 a8 8 0 0 1 -16 0 c0 -4 2 -7 6 -8z" fill="url(#s3-gly)" stroke="#3a2608" stroke-width="0.8"/><path d="M8 13 h8" stroke="#3a2608" stroke-width="1.6"/>',
    "cancel": '<path d="M6 6 Q12 11 18 18 M18 6 Q11 12 6 18" stroke="url(#s3-gly)" stroke-width="3" fill="none" stroke-linecap="round"/>',
}


def cmd_button(glyph, key, label, state="normal", cooldown=None):
    if glyph is None:
        return ('<div style="height:48px;border-radius:3px;background:linear-gradient(180deg,#1A100A,#24170E);border:1px solid #0a0604;'
                'box-shadow:inset 0 2px 5px rgba(0,0,0,.8), inset 0 -1px 0 rgba(255,220,160,.08)"></div>')
    bg = "linear-gradient(180deg,#7A5030 0%,#56361F 45%,#3A2414 100%)"
    ring_ = "inset 0 0 0 1px rgba(201,163,90,.55)"
    glow, op = "", "1"
    if state == "armed":
        bg = "linear-gradient(180deg,#8A3A26 0%,#5E2216 50%,#3A140C 100%)"
        ring_ = f"inset 0 0 0 1px {ALERT}"
        glow = ",0 0 0 1px rgba(229,96,77,.5),0 0 14px rgba(229,96,77,.5)"
    if state == "disabled":
        op = "0.42"
    cd = ""
    gop = "1"
    if cooldown is not None:
        gop = "0"
        cd = (f'<div style="position:absolute;inset:0;background:conic-gradient(rgba(10,6,4,.78) 0 {cooldown}%, transparent {cooldown}% 100%);border-radius:2px"></div>'
              f'<span style="position:absolute;left:0;right:0;top:4px;height:22px;line-height:22px;text-align:center;font-family:\'IBM Plex Mono\',monospace;font-size:14px;font-weight:600;color:#FFF0C8;text-shadow:0 1px 2px #000;z-index:1">4s</span>')
    return (f'<div style="position:relative;height:48px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;padding:0 4px;box-sizing:border-box;background:{bg};border:1px solid #120a05;border-radius:3px;'
            f'box-shadow:{ring_}, inset 0 1px 0 rgba(255,230,180,.35), inset 0 -3px 0 rgba(0,0,0,.4), 0 2px 0 #0a0604{glow};opacity:{op}">'
            f'<svg width="22" height="22" viewBox="0 0 24 24" aria-hidden="true" style="opacity:{gop};filter:drop-shadow(0 1px 0 #000)">{GLYPH[glyph]}</svg>'
            f'<span style="font-size:11px;line-height:1.2;white-space:nowrap;color:#F6E3B4;text-shadow:0 1px 0 #000;position:relative;z-index:2">{label}</span>'
            f'<span style="position:absolute;right:3px;top:2px;padding:0 3px;font-family:\'IBM Plex Mono\',monospace;font-size:9.5px;font-weight:600;color:{INKC};background:#E2D2A8;border-radius:1px;line-height:13px">{key}</span>{cd}</div>')


def command_grid():
    btns = [
        ("move", "M", "이동"), ("stop", "S", "정지"), ("attack", "A", "공격", "armed"), ("hold", "H", "위치 고수"),
        ("patrol", "P", "순찰"), ("volley", "V", "일제 사격", "normal", 62), ("powder", "R", "화약 보충", "disabled"), (None,),
        (None,), (None,), (None,), ("cancel", "Esc", "취소"),
    ]
    cells = []
    for b in btns:
        if b[0] is None:
            cells.append(cmd_button(None, "", ""))
        else:
            state = b[3] if len(b) > 3 else "normal"
            cd = b[4] if len(b) > 4 else None
            cells.append(cmd_button(b[0], b[1], b[2], state, cd))
    return (f'<div style="position:absolute;left:1018px;top:46px;width:404px;height:164px;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));grid-auto-rows:48px;gap:7px">{"".join(cells)}</div>')


def console_svg():
    w, h = 1440, 240
    g = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" style="position:absolute;left:0;top:0" aria-hidden="true">', '<defs>',
         '<linearGradient id="s3-conSh" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE0A8" stop-opacity="0.12"/><stop offset="0.25" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.5"/></linearGradient>',
         '<linearGradient id="s3-well" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0E0805"/><stop offset="1" stop-color="#22150C"/></linearGradient>',
         '</defs>']
    # carved silhouette: flat top with raised caps over each well and scroll ends
    top = ("M0 240 V34 H6 Q10 26 18 26 H206 Q214 26 216 18 Q220 8 232 8 H990 Q1002 8 1006 18 Q1008 26 1016 26 H1424 Q1432 26 1434 34 H1440 V240 Z")
    g.append(f'<path d="{top}" fill="#000" opacity="0.55" transform="translate(0,-4)" filter="url(#s3-sh2)"/>')
    g.append(f'<path d="{top}" fill="#3A2416"/>')
    g.append(f'<path d="{top}" fill="#fff" filter="url(#s3-woodf)"/>')
    g.append(f'<path d="{top}" fill="url(#s3-conSh)"/>')
    g.append('<path d="M0 34 H6 Q10 26 18 26 H206 Q214 26 216 18 Q220 8 232 8 H990 Q1002 8 1006 18 Q1008 26 1016 26 H1424 Q1432 26 1434 34 H1440" fill="none" stroke="url(#s3-brassV)" stroke-width="3"/>')
    g.append('<path d="M0 38 H8 Q12 31 20 31 H206 Q217 31 219 22 Q223 13 233 13 H989 Q999 13 1003 22 Q1006 31 1016 31 H1422 Q1428 31 1432 38 H1440" fill="none" stroke="#120a05" stroke-width="1.2"/>')
    # dancheong band along the raised center cap
    for i in range(240, 984, 36):
        off = 0
        for c, ww in [(RED, 12), (GRN, 10), (YEL, 4), (BLUE_O, 10)]:
            if i + off + ww <= 984:
                g.append(f'<rect x="{i + off}" y="16" width="{ww}" height="5" fill="{c}" opacity="0.9"/>')
            off += ww
    # center crest medallion
    g.append('<g transform="translate(611,13)"><circle r="15" fill="#140D08" stroke="url(#s3-brass)" stroke-width="3"/>'
             f'<circle r="10.4" fill="{RED}" stroke="#F6E3C6" stroke-width="1"/><text y="5" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="13" fill="#F6E3C6">參</text></g>')
    g.append(cloud_scroll(520, 14, 1.0, -1) + cloud_scroll(702, 14, 1.0, 1))
    # wells
    for (x, y, ww, hh) in [(14, 42, 192, 192), (224, 40, 774, 194), (1010, 40, 420, 182)]:
        g.append(f'<rect x="{x - 3}" y="{y - 3}" width="{ww + 6}" height="{hh + 6}" rx="4" fill="#120a05"/>')
        g.append(f'<rect x="{x}" y="{y}" width="{ww}" height="{hh}" rx="3" fill="url(#s3-well)"/>')
        g.append(f'<rect x="{x}" y="{y}" width="{ww}" height="{hh}" rx="3" fill="none" stroke="#000" stroke-opacity="0.8" stroke-width="3" filter="url(#s3-blur2)"/>')
        g.append(f'<path d="M{x - 3} {y + hh + 3} H{x + ww + 3} V{y - 3}" fill="none" stroke="#C9A35A" stroke-opacity="0.35" stroke-width="1"/>')
        g.append(corner_fitting(x - 4, y - 4, 1, 1) + corner_fitting(x + ww + 4, y - 4, -1, 1) + corner_fitting(x - 4, y + hh + 4, 1, -1) + corner_fitting(x + ww + 4, y + hh + 4, -1, -1))
    # lattice inlay in the selection well background
    for k in range(234, 990, 22):
        g.append(f'<line x1="{k}" y1="44" x2="{k}" y2="230" stroke="#C9A35A" stroke-opacity="0.05"/>')
    for k in range(48, 232, 22):
        g.append(f'<line x1="228" y1="{k}" x2="994" y2="{k}" stroke="#C9A35A" stroke-opacity="0.05"/>')
    # rivets along bottom
    for x in range(30, 1440, 90):
        g.append(rivet(x, 236, 1.6))
    g.append('</svg>')
    return "".join(g)


def bottom_hud():
    return f"""
  <div style="position:absolute;left:0;top:660px;width:1440px;height:240px">
    {console_svg()}
    <div style="position:absolute;left:24px;top:52px;width:172px;height:172px;box-shadow:0 0 0 2px #2a1a08, 0 0 0 3px #C9A35A">{minimap()}</div>
    {selection_panel()}
    {command_grid()}
  </div>"""


def kit_defs():
    figs = "".join(f'<g id="{i}">{body}</g>' for i, body in _FIG.values())
    return f'<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>{KM.defs_svg()}{figs}</defs></svg>'


def build():
    css = ("body{margin:0;background:#16140F}\n"
           f"a{{color:{YEL}}}a:hover{{color:#F2D58A}}\n"
           "button{font-family:inherit}")
    html = f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>인게임 HUD</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
{css}
</style>
</helmet>
<div style="position: relative; width: 1440px; height: 900px; overflow: hidden; background: {GROUND}; color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif">
{battlefield()}
{top_bar()}
{toasts()}
{armed_hint()}
{group_tabs()}
{bottom_hud()}
{kit_defs()}
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":1440,"height":900}}}}'>
class Component extends DCLogic {{
  renderVals() {{
    return {{}};
  }}
}}
</script>
</body>
</html>
"""
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(html)
    print(OUT, len(html))


if __name__ == "__main__":
    build()
