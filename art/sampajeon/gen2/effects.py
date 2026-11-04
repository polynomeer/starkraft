"""Generate Effects.dc.html for 삼파전 SAMPAJEON (sheet E) — high-fidelity pass.

Usage: python3 effects.py <out.html>

Every SVG id starts with "e-". Shared gradients, filters and the 화승총수 symbol live in one
hidden <defs> SVG (ids "e-sh-*"); each visible SVG adds its own ids under its own prefix.
Randomness is seeded so the output is deterministic.
"""
import math
import random
import re
import sys

OUT = sys.argv[1]

MONO = "'IBM Plex Mono', monospace"
SERIF = "'Nanum Myeongjo', serif"
SANS = "'IBM Plex Sans KR', sans-serif"

GROUND, PANEL, RAISED, LINE, INK, MUTED = "#16140F", "#201D17", "#2A2620", "#4A4236", "#EFE6D2", "#A89E8A"
RED, YEL, BLU, WHT, BLK = "#C8432B", "#E2B13C", "#2F6E8E", "#EFE8D8", "#1A1814"
ALERT, OK = "#E5604D", "#8CC79A"
ORE, ORE_GLOW, GAS, FOOD = "#9FE3D0", "#CFF5EA", "#B79AE0", "#E8DDBF"
BRASS, GOLD_T = "#C9A35A", "#E8C673"
OUTLINE = "#120d08"


def close_tags(s):
    return re.sub(r"<([a-zA-Z]+)((?:\s[^<>]*?)?)\s*/>", r"<\1\2></\1>", s)


def f2(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def stops(st):
    out = []
    for s in st:
        a = f' stop-opacity="{s[2]}"' if len(s) > 2 else ""
        out.append(f'<stop offset="{s[0]}" stop-color="{s[1]}"{a}/>')
    return "".join(out)


def lin(i, st, x1=0, y1=0, x2=0, y2=1):
    return f'<linearGradient id="{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops(st)}</linearGradient>'


def rad(i, st, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
    f = f' fx="{fx}" fy="{fy}"' if fx is not None else ""
    return f'<radialGradient id="{i}" cx="{cx}" cy="{cy}" r="{r}"{f}>{stops(st)}</radialGradient>'


US = 'filterUnits="userSpaceOnUse" x="-300" y="-300" width="900" height="900"'


def svg(w, h, vb, label, body, defs=""):
    d = f"<defs>{defs}</defs>" if defs else ""
    return (f'<svg width="{w}" height="{h}" viewBox="{vb}" role="img" aria-label="{label}" '
            f'style="display: block">{d}{body}</svg>')


# ------------------------------------------------------------------ shared defs

def shared_defs():
    d = []
    # metals and lacquer
    d.append(lin("e-sh-brass", [(0, "#FBE7A6"), (0.35, "#D9B460"), (0.7, "#9C7632"), (1, "#5E4318")], 0, 0, 1, 1))
    d.append(rad("e-sh-rivet", [(0, "#FFF6CF"), (0.35, "#E1BE6A"), (0.8, "#7A5824"), (1, "#3E2A0E")], 0.5, 0.5, 0.5, 0.32, 0.3))
    d.append(lin("e-sh-gold", [(0, "#FFF3C0"), (0.4, "#EBC45C"), (0.75, "#B98A2E"), (1, "#7E5718")], 0, 0, 0.4, 1))
    d.append(lin("e-sh-goldDim", [(0, "#8E877A"), (1, "#4E4840")], 0, 0, 0.4, 1))
    d.append(lin("e-sh-goldRed", [(0, "#FFD9B8"), (0.45, "#F08A5A"), (1, "#9C2E1A")], 0, 0, 0.4, 1))
    for k, (a, b, c) in {"normal": ("#7A4428", "#4E2A1A", "#24130B"), "hover": ("#9A5832", "#683820", "#2E180C"),
                         "pressed": ("#4A2818", "#341C11", "#1A0D07"), "disabled": ("#4C4640", "#36322D", "#1E1C1A"),
                         "armed": ("#8A3A24", "#5A2316", "#28100A"), "cooldown": ("#5E3420", "#3E2214", "#1E100A")}.items():
        d.append(lin(f"e-sh-lac-{k}", [(0, a), (0.55, b), (1, c)], 0, 0, 1, 1))
        d.append(lin(f"e-sh-pan-{k}", [(0, c), (0.5, b), (1, a)], 0, 0, 1, 1))
    d.append(lin("e-sh-hanji", [(0, "#F4ECD8"), (1, "#D6C9A8")], 0, 0, 0, 1))
    # paper / wood noise overlay (alpha noise)
    d.append('<filter id="e-sh-noise" x="0" y="0" width="100%" height="100%">'
             '<feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="3" seed="11"/>'
             '<feColorMatrix type="matrix" values="0 0 0 0 0.1  0 0 0 0 0.07  0 0 0 0 0.04  0 0 0 -0.9 0.62"/></filter>')
    d.append('<filter id="e-sh-noiseL" x="0" y="0" width="100%" height="100%">'
             '<feTurbulence type="fractalNoise" baseFrequency="0.7" numOctaves="2" seed="4"/>'
             '<feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 0.92  0 0 0 0 0.75  0 0 0 0.9 -0.48"/></filter>')
    # ground texture (two layers: mottled earth + fine grit)
    d.append(f'<filter id="e-sh-earth" {US}>'
             '<feTurbulence type="fractalNoise" baseFrequency="0.035 0.07" numOctaves="4" seed="21"/>'
             '<feColorMatrix type="matrix" values="0 0 0 0 0.24  0 0 0 0 0.2  0 0 0 0 0.12  -1.4 0 0 0 1.05"/></filter>')
    d.append(f'<filter id="e-sh-grit" {US}>'
             '<feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="8"/>'
             '<feColorMatrix type="matrix" values="0 0 0 0 0.85  0 0 0 0 0.74  0 0 0 0 0.52  0 0 0 -1.6 0.7"/></filter>')
    for k, s in (("b1", 1), ("b2", 2), ("b3", 3), ("b5", 5), ("b8", 8), ("b05", 0.6)):
        d.append(f'<filter id="e-sh-{k}" {US}><feGaussianBlur stdDeviation="{s}"/></filter>')
    # glow = blur + original on top
    d.append(f'<filter id="e-sh-glow" {US}><feGaussianBlur in="SourceGraphic" stdDeviation="2.4" result="b"/>'
             '<feMerge><feMergeNode in="b"/><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>')
    d.append(f'<filter id="e-sh-glowS" {US}><feGaussianBlur in="SourceGraphic" stdDeviation="1.1" result="b"/>'
             '<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>')
    # turbulent fire / smoke edges
    for k, (fr, sc, seed) in {"fire": (0.09, 7, 3), "fire2": (0.14, 5, 9), "smoke": (0.05, 11, 5),
                              "smoke2": (0.08, 8, 13), "brush": (0.6, 2.6, 7)}.items():
        d.append(f'<filter id="e-sh-f{k}" {US}>'
                 f'<feTurbulence type="fractalNoise" baseFrequency="{fr}" numOctaves="3" seed="{seed}" result="n"/>'
                 f'<feDisplacementMap in="SourceGraphic" in2="n" scale="{sc}" xChannelSelector="R" yChannelSelector="G"/></filter>')
    d.append(f'<filter id="e-sh-desat" {US}><feColorMatrix type="saturate" values="0.12"/>'
             '<feComponentTransfer><feFuncR type="linear" slope="0.7"/><feFuncG type="linear" slope="0.7"/>'
             '<feFuncB type="linear" slope="0.7"/></feComponentTransfer></filter>')
    d.append(f'<filter id="e-sh-hit" {US}><feColorMatrix type="matrix" values="0.6 0.4 0.2 0 0.35  0.1 0.3 0.1 0 0.02  '
             '0.1 0.1 0.25 0 0.02  0 0 0 1 0"/></filter>')
    d.append(f'<filter id="e-sh-dead" {US}><feColorMatrix type="saturate" values="0.35"/>'
             '<feComponentTransfer><feFuncR type="linear" slope="0.7"/><feFuncG type="linear" slope="0.62"/>'
             '<feFuncB type="linear" slope="0.58"/></feComponentTransfer></filter>')
    # gunner materials
    d.append(lin("e-sh-gRed", [(0, "#EE7A58"), (0.45, "#C2442C"), (1, "#6A1E12")], 0, 0, 1, 0.3))
    d.append(lin("e-sh-gRedD", [(0, "#B23C26"), (1, "#4E140C")], 0, 0, 1, 0.4))
    d.append(rad("e-sh-gSkin", [(0, "#F6D2AE"), (0.6, "#D9A07A"), (1, "#9C6646")], 0.4, 0.4, 0.6, 0.35, 0.3))
    d.append(lin("e-sh-gFelt", [(0, "#5A544C"), (0.4, "#2A2622"), (1, "#0C0B0A")], 0, 0, 1, 1))
    d.append(lin("e-sh-gBrim", [(0, "#4C4741"), (0.5, "#24211E"), (1, "#0B0A09")], 0, 0, 1, 0.6))
    d.append(lin("e-sh-gWrap", [(0, "#F2EBDC"), (0.6, "#C9BFA8"), (1, "#7E7462")], 0, 0, 1, 0))
    d.append(lin("e-sh-gTrou", [(0, "#5C6A78"), (1, "#222A33")], 0, 0, 1, 0))
    d.append(lin("e-sh-gWood", [(0, "#B07A48"), (0.5, "#7A4A28"), (1, "#3A2010")], 0, 0, 0, 1))
    d.append(lin("e-sh-gIron", [(0, "#C8CDD2"), (0.45, "#6C7278"), (1, "#24272B")], 0, 0, 0, 1))
    d.append(lin("e-sh-gCord", [(0, "#6E86C8"), (1, "#26336A")], 0, 0, 1, 1))
    d.append(rad("e-sh-gGourd", [(0, "#E2B878"), (0.6, "#A8763E"), (1, "#5A3A18")], 0.5, 0.5, 0.55, 0.35, 0.3))
    # fire / smoke paints
    d.append(rad("e-sh-flash", [(0, "#FFFFFF"), (0.25, "#FFF6CC"), (0.55, "#FFC85A", 0.85), (1, "#F07A2A", 0)]))
    d.append(rad("e-sh-fireball", [(0, "#FFF8D8"), (0.25, "#FFD670"), (0.55, "#F58A2C"), (0.82, "#C23A16"), (1, "#5A140A", 0)],
                 0.5, 0.55, 0.5, 0.45, 0.6))
    d.append(rad("e-sh-smoke", [(0, "#A8A196"), (0.55, "#6A645B"), (1, "#2C2925")], 0.5, 0.5, 0.55, 0.35, 0.3))
    d.append(rad("e-sh-smokeL", [(0, "#E8E2D6"), (0.6, "#B6AEA0"), (1, "#6E675C")], 0.5, 0.5, 0.55, 0.35, 0.3))
    d.append(rad("e-sh-scorch", [(0, "#050403"), (0.55, "#14100C", 0.95), (0.85, "#2A1E14", 0.6), (1, "#2A1E14", 0)]))
    d.append(rad("e-sh-ember", [(0, "#FFF0B0"), (0.4, "#FF9A3A"), (1, "#C2300E", 0)]))
    d.append(rad("e-sh-gob", [(0, "#FFFFFF"), (0.3, "#C8EEFF"), (0.65, "#4FB3E8"), (1, "#1A5C8C", 0)]))
    d.append(rad("e-sh-gobGround", [(0, "#A8DDF5", 0.8), (0.5, "#4FB3E8", 0.35), (1, "#12222E", 0)]))
    d.append(rad("e-sh-oreGlow", [(0, "#CFF5EA", 0.9), (0.5, "#9FE3D0", 0.35), (1, "#9FE3D0", 0)]))
    d.append(rad("e-sh-gasGlow", [(0, "#E4D6FA", 0.9), (0.5, "#B79AE0", 0.4), (1, "#B79AE0", 0)]))
    d.append(rad("e-sh-redGlow", [(0, "#FF8A6A", 0.85), (0.5, "#E5604D", 0.35), (1, "#E5604D", 0)]))
    d.append(rad("e-sh-goldGlow", [(0, "#FFE9A8", 0.9), (0.5, "#E2B13C", 0.35), (1, "#E2B13C", 0)]))
    d.append(rad("e-sh-jadeRing", [(0, "#CFF5EA", 0), (0.7, "#9FE3D0", 0), (0.85, "#CFF5EA", 0.9), (1, "#9FE3D0", 0)]))
    d.append(rad("e-sh-shadow", [(0, "#000", 0.55), (0.7, "#000", 0.25), (1, "#000", 0)]))
    return "".join(d)


# ------------------------------------------------------------------ 화승총수 symbol (iso, ~5.5 heads)

def gunner_symbol():
    """Symbol in coords x -40..40, y 0..120; feet at y≈112, muzzle at (28, 22)."""
    rng = random.Random(42)
    p = []
    o = f'stroke="{OUTLINE}" stroke-opacity="0.65" stroke-width="0.8" stroke-linejoin="round"'
    # clip paths for stud patterns
    torso = "M-14 42 C-17 46 -17 56 -13 70 L13 70 C16 58 16 46 13 42 C6 39 -6 39 -14 42 Z"
    skirt = "M-13 69 L13 69 L18 95 C8 98 -8 98 -18 95 Z"
    cps = (f'<clipPath id="e-sh-cpTorso"><path d="{torso}"/></clipPath>'
           f'<clipPath id="e-sh-cpSkirt"><path d="{skirt}"/></clipPath>')
    # --- legs
    for lx, sh in ((-6, 0), (6, 1)):
        p.append(f'<path d="M{lx - 4} 92 L{lx + 4} 92 L{lx + 3.4} 108 L{lx - 3.4} 108 Z" fill="url(#e-sh-gWrap)" {o}/>')
        for y in range(95, 107, 3):
            p.append(f'<path d="M{lx - 3.8} {y} L{lx + 3.8} {y + 1.4}" stroke="#7E7462" stroke-width="0.6" opacity="0.8"/>')
        p.append(f'<ellipse cx="{lx + sh * 0.6}" cy="110" rx="4.6" ry="2.6" fill="#141210" {o}/>')
        p.append(f'<ellipse cx="{lx - 1 + sh * 0.6}" cy="109.2" rx="2" ry="0.8" fill="#5A544C" opacity="0.7"/>')
    # --- skirt (studded red)
    p.append(f'<path d="{skirt}" fill="url(#e-sh-gRed)" {o}/>')
    p.append('<path d="M0 72 L0 97" stroke="#4E140C" stroke-width="1.6"/>')
    p.append('<path d="M-0.8 72 L-0.8 97" stroke="#F09A7A" stroke-width="0.4" opacity="0.6"/>')
    studs = []
    for row, y in enumerate(range(73, 95, 4)):
        for x in range(-16, 18, 4):
            sx = x + (2 if row % 2 else 0)
            studs.append(f'<circle cx="{sx}" cy="{y}" r="0.85" fill="url(#e-sh-rivet)"/>')
    p.append(f'<g clip-path="url(#e-sh-cpSkirt)">{"".join(studs)}'
             '<rect x="-20" y="69" width="12" height="30" fill="#FFD8C0" opacity="0.08"/>'
             '<rect x="6" y="69" width="14" height="30" fill="#000" opacity="0.18"/></g>')
    p.append('<path d="M-18 95 C-8 98 8 98 18 95 L18 93.2 C8 96.2 -8 96.2 -18 93.2 Z" fill="#2E1E14"/>')
    # --- back arm (holds stock, right side of body)
    p.append(f'<path d="M10 44 C17 48 17 56 12 62 L8 60 C11 55 11 50 7 47 Z" fill="url(#e-sh-gRedD)" {o}/>')
    # --- torso (studded brigandine)
    p.append(f'<path d="{torso}" fill="url(#e-sh-gRed)" {o}/>')
    studs = []
    for row, y in enumerate(range(44, 70, 3)):
        for x in range(-16, 17, 3):
            sx = x + (1.5 if row % 2 else 0)
            studs.append(f'<circle cx="{sx}" cy="{y}" r="0.7" fill="url(#e-sh-rivet)"/>')
    p.append(f'<g clip-path="url(#e-sh-cpTorso)">{"".join(studs)}'
             '<path d="M-16 40 L-6 40 L-10 72 L-18 72 Z" fill="#FFE0C8" opacity="0.1"/>'
             '<path d="M6 40 L16 40 L16 72 L8 72 Z" fill="#000" opacity="0.22"/></g>')
    # collar (동정) and front opening
    p.append(f'<path d="M-6 41 L0 50 L6 41 L4 40 L0 46 L-4 40 Z" fill="#F2EBDC" {o}/>')
    p.append('<path d="M0 50 L0 69" stroke="#4E140C" stroke-width="1.1"/>')
    # match-cord coil across chest
    p.append('<path d="M-13 45 C-4 54 6 60 13 66" fill="none" stroke="#6A4A2A" stroke-width="2.2"/>')
    p.append('<path d="M-13 45 C-4 54 6 60 13 66" fill="none" stroke="#A88458" stroke-width="0.8" stroke-dasharray="1.2 1.4"/>')
    # pauldrons
    for sx, g in ((-14, "e-sh-gRed"), (13, "e-sh-gRedD")):
        p.append(f'<ellipse cx="{sx}" cy="45" rx="6" ry="4.6" fill="url(#{g})" {o}/>')
        for k in range(3):
            p.append(f'<path d="M{sx - 5} {44.5 + k * 1.6} C{sx - 2} {46.2 + k * 1.6} {sx + 2} {46.2 + k * 1.6} {sx + 5} {44.5 + k * 1.6}" '
                     f'fill="none" stroke="#E2B13C" stroke-width="0.5" opacity="0.75"/>')
    # belt (광다회 blue cord) with knot + tassels
    p.append(f'<path d="M-14 67 C-6 70 6 70 14 67 L14 71 C6 74 -6 74 -14 71 Z" fill="url(#e-sh-gCord)" {o}/>')
    p.append('<path d="M-14 68.6 C-6 71.6 6 71.6 14 68.6" fill="none" stroke="#A9B8E8" stroke-width="0.5" opacity="0.7"/>')
    p.append(f'<circle cx="-5" cy="72" r="2.2" fill="#3B4E8C" {o}/>')
    p.append('<path d="M-6 73.5 L-8 86 M-5 73.6 L-5.4 87 M-4 73.5 L-2.6 85.6" stroke="#3B4E8C" stroke-width="1.1"/>')
    p.append('<path d="M-8.6 84 L-7 88 M-5.8 85 L-5.2 89 M-3.4 84 L-2 87.4" stroke="#C8D2F0" stroke-width="0.6"/>')
    # powder gourd at hip
    p.append(f'<path d="M11 73 C17 73 18 80 15 83 C13 86 9 85 9 81 C8 78 10 76 11.5 75 Z" fill="url(#e-sh-gGourd)" {o}/>')
    p.append(f'<circle cx="12" cy="71.6" r="2" fill="url(#e-sh-gGourd)" {o}/>')
    p.append('<path d="M11.4 70 L12.4 68 L13 70" fill="#5A3A18"/>')
    p.append('<path d="M10.4 79 C11.6 77.8 13.6 77.8 14.8 79" fill="none" stroke="#F6D8A8" stroke-width="0.5" opacity="0.7"/>')
    # --- matchlock (stock low-left -> muzzle up-right)
    p.append(f'<path d="M-17 70 L-11 63 L-6 60 L-1 57 L1.6 59.4 L-3 63 L-8 68 L-13 73 Z" fill="url(#e-sh-gWood)" {o}/>')
    p.append(f'<path d="M-2 58.4 L22 30 L24 31.6 L0.4 60 Z" fill="url(#e-sh-gWood)" {o}/>')
    p.append(f'<path d="M3 52.6 L28 22 L29.4 23.2 L4.6 54 Z" fill="url(#e-sh-gIron)" {o}/>')
    p.append('<path d="M3.4 52.4 L28.2 22.2" stroke="#F2F4F6" stroke-width="0.4" opacity="0.8"/>')
    for t in (0.25, 0.55, 0.8):
        x, y = -2 + 24 * t, 58.4 - 28.4 * t
        p.append(f'<path d="M{f2(x - 0.4)} {f2(y - 1.6)} L{f2(x + 2.2)} {f2(y + 0.6)}" stroke="#C9A35A" stroke-width="1.1"/>')
    p.append(f'<circle cx="28.6" cy="22.6" r="1.1" fill="#24272B" {o}/>')
    # serpentine + lit match
    p.append('<path d="M-1 59 C-4 57 -5 54 -3 52" fill="none" stroke="#C9A35A" stroke-width="1.2"/>')
    p.append('<path d="M-3 52 C-6 49 -9 50 -11 47" fill="none" stroke="#7A5A3A" stroke-width="0.9"/>')
    p.append('<circle cx="-3" cy="51.6" r="1.6" fill="url(#e-sh-ember)" filter="url(#e-sh-glowS)"/>')
    p.append('<path d="M-3 50 C-5 46 -1 44 -3 40 C-5 37 -2 34 -4 31" fill="none" stroke="#CFC8BC" stroke-width="0.9" '
             'opacity="0.5" filter="url(#e-sh-b05)"/>')
    # --- front arm + hands
    p.append(f'<path d="M-17 45 C-21 52 -18 58 -9 59 L-7 55.6 C-12 54.8 -14 51 -12 47 Z" fill="url(#e-sh-gRed)" {o}/>')
    p.append(f'<path d="M7 47 C12 48 15 44 16 41 L13.4 39.6 C12 42 10 44 6.4 44 Z" fill="url(#e-sh-gRedD)" {o}/>')
    p.append(f'<circle cx="-7.4" cy="57.6" r="2.7" fill="url(#e-sh-gSkin)" {o}/>')
    p.append(f'<circle cx="15.4" cy="40.6" r="2.5" fill="url(#e-sh-gSkin)" {o}/>')
    # --- head
    p.append(f'<path d="M-3.4 36 L3.4 36 L3 41 L-3 41 Z" fill="#B88060"/>')
    p.append(f'<ellipse cx="0" cy="30.5" rx="7.2" ry="8.2" fill="url(#e-sh-gSkin)" {o}/>')
    p.append(f'<ellipse cx="-7" cy="30.5" rx="1.6" ry="2.4" fill="#C98E68" {o}/>')
    p.append('<path d="M-3.8 29.4 L-1.4 29.8 M1.6 29.8 L4 29.4" stroke="#1A1210" stroke-width="1" stroke-linecap="round"/>')
    p.append('<path d="M-4.2 27.6 L-1.2 27.2 M1.4 27.2 L4.4 27.6" stroke="#2A1A12" stroke-width="0.7" opacity="0.8"/>')
    p.append('<path d="M-0.4 30.4 L-1 33 L0.6 33.4" fill="none" stroke="#9C6646" stroke-width="0.6"/>')
    p.append('<path d="M-3.6 35 C-2 34 -0.6 34.6 0 35.2 C0.6 34.6 2 34 3.6 35 C2.4 36.6 -2.4 36.6 -3.6 35 Z" fill="#1A1210"/>')
    p.append('<path d="M-1.2 37.4 C-0.4 38.6 0.6 38.6 1.2 37.4" fill="none" stroke="#1A1210" stroke-width="0.6"/>')
    p.append('<ellipse cx="2.8" cy="31.8" rx="1.6" ry="1" fill="#E89C82" opacity="0.4"/>')
    # 전립 hat: brim, crown, band, beads, tassel
    p.append(f'<ellipse cx="0" cy="25" rx="17.5" ry="6.6" fill="url(#e-sh-gBrim)" {o}/>')
    p.append('<path d="M-17 24.6 C-10 20.4 10 20.4 17 24.6" fill="none" stroke="#8A8278" stroke-width="0.6" opacity="0.8"/>')
    p.append('<ellipse cx="0" cy="25.8" rx="15" ry="5" fill="none" stroke="#000" stroke-width="0.6" opacity="0.5"/>')
    p.append(f'<path d="M-8.6 24 C-9 15 -5 11.6 0 11.6 C5 11.6 9 15 8.6 24 C4 26 -4 26 -8.6 24 Z" fill="url(#e-sh-gFelt)" {o}/>')
    p.append('<path d="M-8.4 21.6 C-4 23.6 4 23.6 8.4 21.6 L8.6 24 C4 26 -4 26 -8.6 24 Z" fill="#8A2A1A"/>')
    p.append('<path d="M-6.4 16 C-5 13.6 -2.6 12.6 -1 12.6" fill="none" stroke="#9A928A" stroke-width="0.8" opacity="0.7"/>')
    # 상모 red tassel + peacock feather
    p.append(f'<circle cx="0" cy="11.2" r="1.6" fill="url(#e-sh-brass)" {o}/>')
    for k in range(9):
        a = -math.pi / 2 + (k - 4) * 0.16
        x2, y2 = 0.4 + 9 * math.cos(a) * 0.55, 10 + 9 * math.sin(a)
        p.append(f'<path d="M0 10.6 Q{f2(x2 * 0.6 + 1)} {f2(y2 + 3)} {f2(x2 + 1.6)} {f2(y2)}" fill="none" '
                 f'stroke="{"#E2442A" if k % 2 else "#B82A18"}" stroke-width="1.2" stroke-linecap="round"/>')
    p.append('<path d="M1 10 C5 4 10 2 13 0" fill="none" stroke="#2E5E4E" stroke-width="0.9"/>')
    p.append('<ellipse cx="12.6" cy="0.6" rx="2.2" ry="1.5" fill="#2F6E8E" stroke="#E2B13C" stroke-width="0.5"/>')
    # amber chin-strap beads (갓끈)
    for side in (-1, 1):
        for k in range(7):
            t = k / 6
            x = side * (9 - 3.6 * t)
            y = 27 + 15 * t
            p.append(f'<circle cx="{f2(x)}" cy="{f2(y)}" r="{f2(1.05 - 0.25 * t)}" fill="url(#e-sh-gGourd)"/>')
    sym = f'<symbol id="e-sh-gunner" viewBox="-40 -4 80 124" overflow="visible">{"".join(p)}</symbol>'
    return cps + sym


# ------------------------------------------------------------------ ground (iso, painterly)

def ground_patch(pid, x, y, w, h, seed, tone="autumn", grid=True, density=1.0):
    """Painted earth patch: base gradient, mottling, grit, iso grid, tufts, pebbles, leaves."""
    rng = random.Random(seed)
    base = {"autumn": ("#4E4430", "#3A3224", "#262016"), "dark": ("#2E2A22", "#221F19", "#16140F")}[tone]
    g = [f'<linearGradient id="{pid}-gb" x1="0" y1="0" x2="1" y2="1">{stops([(0, base[0]), (0.6, base[1]), (1, base[2])])}</linearGradient>',
         f'<clipPath id="{pid}-gc"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="3"/></clipPath>']
    b = [f'<g clip-path="url(#{pid}-gc)">',
         f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#{pid}-gb)"/>',
         f'<rect x="{x}" y="{y}" width="{w}" height="{h}" filter="url(#e-sh-earth)" opacity="0.9"/>',
         f'<rect x="{x}" y="{y}" width="{w}" height="{h}" filter="url(#e-sh-grit)" opacity="0.35"/>']
    if grid:
        lines = []
        step = 32
        for k in range(-int(h / 8) - 4, int(w / 16) + 6):
            x0 = x + k * step
            lines.append(f'M{x0} {y} L{x0 + h * 2} {y + h}')
            lines.append(f'M{x0} {y + h} L{x0 + h * 2} {y}')
        b.append(f'<path d="{" ".join(lines)}" stroke="#D8C9A0" stroke-width="0.5" opacity="0.07"/>')
    # dry grass tufts
    n = int(w * h / 420 * density)
    tufts = []
    for _ in range(n):
        tx, ty = x + rng.uniform(0, w), y + rng.uniform(0, h)
        col = rng.choice(["#9C8A4A", "#B49C58", "#7A7038", "#C8AE62", "#6A6A34"])
        blades = []
        for _k in range(rng.randint(3, 6)):
            dx, hh = rng.uniform(-2.4, 2.4), rng.uniform(2.6, 5.4)
            blades.append(f'M{f2(tx)} {f2(ty)} q{f2(dx * 0.3)} {f2(-hh * 0.6)} {f2(dx)} {f2(-hh)}')
        tufts.append(f'<path d="{" ".join(blades)}" stroke="{col}" stroke-width="0.7" fill="none" stroke-linecap="round"/>')
    b.append(f'<g opacity="0.85">{"".join(tufts)}</g>')
    # pebbles with highlight + shadow
    peb = []
    for _ in range(int(w * h / 900 * density)):
        px, py, r = x + rng.uniform(0, w), y + rng.uniform(0, h), rng.uniform(0.8, 2.2)
        peb.append(f'<ellipse cx="{f2(px + 0.6)}" cy="{f2(py + 0.6)}" rx="{f2(r)}" ry="{f2(r * 0.6)}" fill="#000" opacity="0.4"/>'
                   f'<ellipse cx="{f2(px)}" cy="{f2(py)}" rx="{f2(r)}" ry="{f2(r * 0.62)}" fill="{rng.choice(["#6E6658", "#857B68", "#5A5448"])}"/>'
                   f'<ellipse cx="{f2(px - r * 0.3)}" cy="{f2(py - r * 0.25)}" rx="{f2(r * 0.4)}" ry="{f2(r * 0.22)}" fill="#C8BEA8" opacity="0.6"/>')
    b.append("".join(peb))
    # fallen maple leaves
    lv = []
    for _ in range(int(w * h / 1600 * density)):
        lx, ly = x + rng.uniform(0, w), y + rng.uniform(0, h)
        lv.append(f'<path d="M{f2(lx)} {f2(ly)} l1.4 -1.2 l0.4 1.4 l1.4 0.2 l-1.2 1 l0.4 1.4 l-1.4 -0.6 l-1.2 0.8 z" '
                  f'fill="{rng.choice(["#C8432B", "#D9602E", "#B8562A", "#E2B13C"])}" opacity="0.85" '
                  f'transform="rotate({rng.randint(0, 359)} {f2(lx)} {f2(ly)})"/>')
    b.append("".join(lv))
    # vignette
    b.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="url(#{pid}-gv)"/>')
    g.append(f'<radialGradient id="{pid}-gv" cx="0.45" cy="0.4" r="0.75">{stops([(0, "#000", 0), (0.7, "#000", 0.12), (1, "#000", 0.55)])}</radialGradient>')
    b.append("</g>")
    return "".join(g), "".join(b)


def iso_ell(cx, cy, r):
    return f'cx="{f2(cx)}" cy="{f2(cy)}" rx="{f2(r)}" ry="{f2(r / 2)}"'


ISO = "scale(1 0.5) rotate(45)"


# ------------------------------------------------------------------ command plaques

ICONS = [
    ("move", "이동", "M", [("f", "M4 19.5 L13.2 10.3 L10 7 H18.5 V15.5 L15.3 12.3 L6.1 21.5 Z")]),
    ("attack", "공격", "A", [("s", "M12 5.4 A6.6 6.6 0 1 1 11.99 5.4 Z"),
                            ("s", "M12 1.6 V6.6 M12 17.4 V22.4 M1.6 12 H6.6 M17.4 12 H22.4"),
                            ("f", "M12 9.4 L14.6 12 L12 14.6 L9.4 12 Z")]),
    ("stop", "정지", "S", [("f", "M8.5 3.5 H15.5 L20.5 8.5 V15.5 L15.5 20.5 H8.5 L3.5 15.5 V8.5 Z"),
                          ("d", "M7.5 10.8 H16.5 V13.2 H7.5 Z")]),
    ("hold", "위치 사수", "H", [("f", "M12 2.5 L20 5.5 V11.5 C20 16.5 16.5 20 12 21.5 C7.5 20 4 16.5 4 11.5 V5.5 Z"),
                             ("d", "M12 6 L16.6 7.8 V11.8 C16.6 14.6 14.7 16.8 12 17.9 Z")]),
    ("patrol", "순찰", "P", [("s", "M4 8.5 H15.5 M20 15.5 H8.5"),
                            ("f", "M15 4.3 L20.6 8.5 L15 12.7 Z M9 11.3 L3.4 15.5 L9 19.7 Z")]),
    ("gather", "채집", "G", [("s", "M4.5 20.5 L13 12"),
                            ("f", "M8.6 4.2 C13.4 2.2 19.2 4.4 21.8 10.2 C18.6 9.1 15.6 9.4 13.6 10.6 L11.6 8.6 C11 7.2 10 5.6 8.6 4.2 Z")]),
    ("build", "건설", "B", [("f", "M12 2.6 L22 9.4 L19.6 9.4 Q12 7.4 4.4 9.4 L2 9.4 Z"),
                           ("f", "M5.6 10.6 H18.4 V20.6 H5.6 Z"),
                           ("d", "M10 20.6 V15 H14 V20.6 Z")]),
    ("rally", "집결", "Y", [("s", "M8 22 V6"), ("f", "M8 5.6 C10 2.6 14.4 2.4 17.6 4.2 L20.6 3.6 L18.6 5.8 C17 8.4 12.4 8.6 9.4 7.2 Z"),
                           ("f", "M4.4 20.4 H11.6 V22.6 H4.4 Z")]),
    ("produce", "생산", "U", [("f", "M3.6 3.6 H20.4 V20.4 H3.6 Z"), ("d", "M6.2 6.2 H17.8 V17.8 H6.2 Z"),
                             ("f", "M10.8 8.2 H13.2 V10.8 H15.8 V13.2 H13.2 V15.8 H10.8 V13.2 H8.2 V10.8 H10.8 Z")]),
    ("research", "연구", "I", [("f", "M5 3 H19 V5 H17.6 V19 H19 V21 H5 V19 H6.4 V5 H5 Z"),
                              ("d", "M8.4 6.6 H15.6 V8 H8.4 Z M8.4 10 H15.6 V11.4 H8.4 Z M8.4 13.4 H13.2 V14.8 H8.4 Z")]),
    ("amove", "공격 이동", "⇧A", [("f", "M2.8 20.8 L10.4 13.2 L7.8 10.6 H14.8 V17.6 L12.2 15 L4.6 22.6 Z"),
                               ("s", "M18 2.6 A3.6 3.6 0 1 1 17.99 2.6 Z"),
                               ("f", "M18 4.9 L19.3 6.2 L18 7.5 L16.7 6.2 Z")]),
    ("cancel", "취소", "Esc", [("s", "M6 6 L18 18 M18 6 L6 18")]),
]


def wood_grain(rng, x0, y0, w, h, n=16):
    out = []
    for i in range(n):
        y = y0 + (i + 0.5) * h / n + rng.uniform(-1, 1)
        segs, x = [f"M{f2(x0 - 2)} {f2(y)}"], x0 - 2
        while x < x0 + w + 2:
            step = rng.uniform(10, 20)
            out_y = y + rng.uniform(-1.6, 1.6)
            segs.append(f"Q{f2(x + step / 2)} {f2(out_y + rng.uniform(-1.4, 1.4))} {f2(x + step)} {f2(out_y)}")
            x += step
        col, op = ("#1A0C06", 0.45) if i % 2 else ("#C08250", 0.16)
        out.append(f'<path d="{" ".join(segs)}" fill="none" stroke="{col}" stroke-width="{f2(rng.uniform(0.4, 0.9))}" opacity="{op}"/>')
    for _ in range(2):  # knots
        kx, ky = x0 + rng.uniform(10, w - 10), y0 + rng.uniform(8, h - 8)
        for r in (3.2, 2.0, 1.0):
            out.append(f'<ellipse cx="{f2(kx)}" cy="{f2(ky)}" rx="{f2(r * 1.8)}" ry="{f2(r)}" fill="none" stroke="#1A0C06" stroke-width="0.5" opacity="0.4"/>')
    return "".join(out)


def cloud_curl(cx, cy, s, fill):
    """구름문 — small double-curl cloud ornament."""
    return (f'<path d="M{f2(cx - 7 * s)} {f2(cy)} C{f2(cx - 7 * s)} {f2(cy - 4 * s)} {f2(cx - 2 * s)} {f2(cy - 4 * s)} {f2(cx - 2 * s)} {f2(cy - 1 * s)} '
            f'C{f2(cx - 2 * s)} {f2(cy + 1 * s)} {f2(cx - 4.4 * s)} {f2(cy + 1 * s)} {f2(cx - 4.4 * s)} {f2(cy - 0.6 * s)} '
            f'M{f2(cx - 2 * s)} {f2(cy - 1 * s)} C{f2(cx - 1 * s)} {f2(cy - 5 * s)} {f2(cx + 5 * s)} {f2(cy - 5 * s)} {f2(cx + 5 * s)} {f2(cy - 1 * s)} '
            f'C{f2(cx + 5 * s)} {f2(cy + 1.6 * s)} {f2(cx + 1.6 * s)} {f2(cy + 1.6 * s)} {f2(cx + 1.6 * s)} {f2(cy - 0.6 * s)} '
            f'M{f2(cx + 5 * s)} {f2(cy - 1 * s)} C{f2(cx + 7 * s)} {f2(cy - 1 * s)} {f2(cx + 8 * s)} {f2(cy)} {f2(cx + 8 * s)} {f2(cy + 0.4 * s)}" '
            f'fill="none" stroke="{fill}" stroke-width="{f2(0.9 * s)}" stroke-linecap="round"/>')


def corner_cap(x, y, sx, sy):
    """Brass L-cap at a plaque corner with two rivets; sx/sy = ±1 orientation."""
    pts = [(0, 0), (16, 0), (16, 3.2), (5.6, 3.2), (3.2, 5.6), (3.2, 16), (0, 16)]
    d = "M" + " L".join(f"{f2(x + px * sx)} {f2(y + py * sy)}" for px, py in pts) + " Z"
    return (f'<path d="{d}" fill="url(#e-sh-brass)" stroke="#2E1E0A" stroke-width="0.6"/>'
            f'<path d="M{f2(x + 15 * sx)} {f2(y + 0.8 * sy)} L{f2(x + 0.8 * sx)} {f2(y + 0.8 * sy)} L{f2(x + 0.8 * sx)} {f2(y + 15 * sy)}" '
            f'fill="none" stroke="#FFF2C2" stroke-width="0.5" opacity="0.7"/>'
            f'<circle cx="{f2(x + 10.4 * sx)}" cy="{f2(y + 1.7 * sy)}" r="1.25" fill="url(#e-sh-rivet)" stroke="#2E1E0A" stroke-width="0.3"/>'
            f'<circle cx="{f2(x + 1.7 * sx)}" cy="{f2(y + 10.4 * sy)}" r="1.25" fill="url(#e-sh-rivet)" stroke="#2E1E0A" stroke-width="0.3"/>')


def glyph(shapes, state):
    fill = {"disabled": "url(#e-sh-goldDim)", "armed": "url(#e-sh-goldRed)"}.get(state, "url(#e-sh-gold)")
    dark = "#2A1608"
    dy = 1.2 if state == "pressed" else 0
    sh, mid, hi = [], [], []
    for kind, d in shapes:
        if kind == "f":
            sh.append(f'<path d="{d}" fill="{dark}" transform="translate(0.7 0.9)"/>')
            mid.append(f'<path d="{d}" fill="{fill}" stroke="{dark}" stroke-width="0.55" stroke-linejoin="round"/>')
            hi.append(f'<path d="{d}" fill="none" stroke="#FFF7D6" stroke-width="0.35" opacity="0.65" transform="translate(-0.3 -0.3)"/>')
        elif kind == "s":
            sh.append(f'<path d="{d}" fill="none" stroke="{dark}" stroke-width="3.4" stroke-linecap="round" transform="translate(0.7 0.9)"/>')
            mid.append(f'<path d="{d}" fill="none" stroke="{dark}" stroke-width="3.3" stroke-linecap="round"/>'
                       f'<path d="{d}" fill="none" stroke="{fill}" stroke-width="2.3" stroke-linecap="round"/>')
            hi.append(f'<path d="{d}" fill="none" stroke="#FFF7D6" stroke-width="0.5" stroke-linecap="round" opacity="0.6" transform="translate(-0.35 -0.35)"/>')
        else:  # recessed detail
            mid.append(f'<path d="{d}" fill="{dark}" opacity="0.85"/>')
    glow = ""
    if state in ("hover", "armed"):
        gc = "#FFD98A" if state == "hover" else "#FF7A5A"
        glow = f'<g filter="url(#e-sh-b2)" opacity="0.7">{"".join(m.replace(fill, gc) for m in mid)}</g>'
    return (f'<g transform="translate(23.6 {f2(18.6 + dy)}) scale(2.2)">{glow}<g opacity="0.75">{"".join(sh)}</g>'
            f'{"".join(mid)}{"".join(hi)}</g>')


def plaque(pid, icon, state="normal", px=78, seed=1):
    key, _name, hot, shapes = icon
    rng = random.Random(seed)
    defs = f'<clipPath id="{pid}-c"><rect x="8" y="8" width="84" height="84" rx="3"/></clipPath>'
    b = [f'<rect x="5" y="8" width="92" height="92" rx="6" fill="#000" opacity="0.55" filter="url(#e-sh-b3)"/>',
         f'<rect x="2" y="2" width="96" height="96" rx="6" fill="url(#e-sh-lac-{state})" stroke="{OUTLINE}" stroke-width="1"/>']
    # carved outer frame: grain + bevel
    b.append(f'<g opacity="0.9">{wood_grain(rng, 2, 2, 96, 96, 22)}</g>')
    b.append('<path d="M3 97 L3 3 L97 3" fill="none" stroke="#E9B07A" stroke-width="1.2" opacity="0.4"/>')
    b.append('<path d="M3 97 L97 97 L97 3" fill="none" stroke="#000" stroke-width="1.4" opacity="0.6"/>')
    # cloud ornaments carved into frame
    b.append(f'<g opacity="0.55">{cloud_curl(50, 6.4, 0.55, "#1A0C06")}{cloud_curl(50, 96.4, 0.55, "#1A0C06")}</g>')
    b.append(f'<g opacity="0.35" transform="translate(-0.5 -0.5)">{cloud_curl(50, 6.4, 0.55, "#F2C48A")}</g>')
    # recessed panel
    pan = f"url(#e-sh-pan-{state})"
    b.append(f'<rect x="8" y="8" width="84" height="84" rx="3" fill="{pan}"/>')
    b.append(f'<g clip-path="url(#{pid}-c)"><g opacity="0.55">{wood_grain(rng, 8, 8, 84, 84, 14)}</g>'
             f'<rect x="8" y="8" width="84" height="84" filter="url(#e-sh-noise)" opacity="0.5"/>'
             '<path d="M8 8 H92 V14 C60 11 30 11 8 16 Z" fill="#000" opacity="0.35"/>'
             '<path d="M8 8 V92 H14 C11 60 11 30 16 8 Z" fill="#000" opacity="0.3"/>'
             '<path d="M92 92 H8 V89 C40 91 70 91 92 88 Z" fill="#F2C48A" opacity="0.1"/></g>')
    b.append('<path d="M8.5 91.5 L91.5 91.5 L91.5 8.5" fill="none" stroke="#E9B07A" stroke-width="0.9" opacity="0.35"/>')
    b.append('<path d="M8.5 91.5 L8.5 8.5 L91.5 8.5" fill="none" stroke="#000" stroke-width="1.2" opacity="0.7"/>')
    # glyph
    b.append(glyph(shapes, state))
    # brass corner caps
    b.append(corner_cap(2, 2, 1, 1) + corner_cap(98, 2, -1, 1) + corner_cap(2, 98, 1, -1) + corner_cap(98, 98, -1, -1))
    # hanji hotkey tag (tied with a cord)
    tw = 10 + 5.6 * len(hot)
    tx, ty = 92 - tw, 74
    b.append(f'<g transform="rotate(-4 {f2(tx + tw / 2)} {ty + 8})">'
             f'<path d="M{f2(tx + tw / 2)} {ty - 4} C{f2(tx + tw / 2 - 3)} {ty - 1} {f2(tx + tw / 2 + 3)} {ty} {f2(tx + tw / 2)} {ty + 2}" '
             f'fill="none" stroke="#C8432B" stroke-width="1"/>'
             f'<rect x="{f2(tx + 0.8)}" y="{ty + 1.6}" width="{f2(tw)}" height="16" rx="1.5" fill="#000" opacity="0.5"/>'
             f'<rect x="{f2(tx)}" y="{ty + 0.6}" width="{f2(tw)}" height="16" rx="1.5" fill="url(#e-sh-hanji)" stroke="#6E5A3A" stroke-width="0.5"/>'
             f'<rect x="{f2(tx)}" y="{ty + 0.6}" width="{f2(tw)}" height="16" filter="url(#e-sh-noise)" opacity="0.35"/>'
             f'<circle cx="{f2(tx + tw / 2)}" cy="{ty + 3}" r="0.9" fill="#6E5A3A"/>'
             f'<text x="{f2(tx + tw / 2)}" y="{ty + 13.4}" text-anchor="middle" font-family="IBM Plex Mono, monospace" '
             f'font-size="9.5" font-weight="600" fill="#2A1A10">{hot}</text></g>')
    # state overlays
    if state == "hover":
        b.insert(0, '<rect x="-2" y="-2" width="104" height="104" rx="9" fill="url(#e-sh-goldGlow)" opacity="0.7"/>')
        b.append('<rect x="2.5" y="2.5" width="95" height="95" rx="6" fill="none" stroke="#FFE3A0" stroke-width="1.2" opacity="0.8"/>')
    elif state == "pressed":
        b.append('<rect x="8" y="8" width="84" height="84" rx="3" fill="#000" opacity="0.32"/>'
                 '<path d="M8 8 H92 V18 C60 13 30 13 8 20 Z M8 8 V92 H18 C13 60 13 30 20 8 Z" fill="#000" opacity="0.45"/>'
                 '<rect x="2.5" y="2.5" width="95" height="95" rx="6" fill="none" stroke="#000" stroke-width="2" opacity="0.6"/>')
    elif state == "disabled":
        b = [f'<g filter="url(#e-sh-desat)">{"".join(b)}</g>',
             '<path d="M14 86 L86 14" stroke="#000" stroke-width="2.4" opacity="0.35"/>']
    elif state == "armed":
        b.insert(0, '<rect x="-4" y="-4" width="108" height="108" rx="10" fill="url(#e-sh-redGlow)"/>')
        b.append('<rect x="0" y="0" width="100" height="100" rx="7" fill="none" stroke="#E5604D" stroke-width="2.2" '
                 'stroke-dasharray="9 4" filter="url(#e-sh-fbrush)"/>')
        b.append('<rect x="70" y="4" width="24" height="24" rx="2" fill="#C8432B" stroke="#7A1E10" stroke-width="0.8" '
                 'transform="rotate(8 82 16)" filter="url(#e-sh-fbrush)"/>'
                 '<text x="82" y="22" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" '
                 'font-size="15" fill="#FBE4D6" transform="rotate(8 82 16)">擊</text>')
    elif state == "cooldown":
        a = 2 * math.pi * 0.62
        ex, ey = 50 + 46 * math.sin(a), 50 - 46 * math.cos(a)
        defs += f'<clipPath id="{pid}-cd"><rect x="8" y="8" width="84" height="84" rx="3"/></clipPath>'
        b.append(f'<g clip-path="url(#{pid}-cd)"><path d="M50 50 L50 -10 A60 60 0 1 1 {f2(ex)} {f2(ey)} Z" '
                 f'fill="#0A0806" opacity="0.72" filter="url(#e-sh-fbrush)"/>'
                 f'<path d="M50 50 L{f2(ex)} {f2(ey)}" stroke="#E8C673" stroke-width="1.4" opacity="0.9"/></g>'
                 '<text x="50" y="58" text-anchor="middle" font-family="IBM Plex Mono, monospace" font-weight="600" '
                 'font-size="20" fill="#FFF3C0" stroke="#000" stroke-width="3" paint-order="stroke">4.2</text>')
    return svg(px, px, "-4 -4 108 108", f"{_name} 명령 버튼 ({state})", "".join(b), defs)


# ------------------------------------------------------------------ ground markers (iso)

def marker_svg(pid, label, seed, body_fn, tone="autumn"):
    gd, gb = ground_patch(pid, 0, 0, 220, 120, seed, tone)
    return svg(206, 112, "0 0 220 120", label, gb + body_fn(), gd)


def m_move():
    out = []
    for i, (cx, s, op) in enumerate(((44, 0.55, 0.45), (110, 0.8, 0.8), (176, 1.0, 1.0))):
        cy = 70
        out.append(f'<ellipse {iso_ell(cx, cy + 2, 26 * s + 6)} fill="url(#e-sh-oreGlow)" opacity="{op * 0.6}"/>')
        for k, r in enumerate((26, 18, 10)):
            rr = r * s + (3 - i) * 2
            out.append(f'<ellipse {iso_ell(cx, cy, rr)} fill="none" stroke="#CFF5EA" stroke-width="{2.6 - k * 0.6}" '
                       f'opacity="{op * (1 - k * 0.22):.2f}" filter="url(#e-sh-fbrush)"/>')
        out.append(f'<ellipse {iso_ell(cx, cy, 3)} fill="#FFFFFF" filter="url(#e-sh-glowS)" opacity="{op}"/>')
        # dust specks kicked up
        rng = random.Random(i + 3)
        for _ in range(6):
            a = rng.uniform(0, 2 * math.pi)
            r = 18 * s + rng.uniform(4, 10)
            out.append(f'<circle cx="{f2(cx + r * math.cos(a))}" cy="{f2(cy + r * math.sin(a) / 2 - 2)}" r="0.9" '
                       f'fill="#E8DCC0" opacity="{op * 0.6:.2f}"/>')
        out.append(f'<text x="{cx}" y="112" text-anchor="middle" font-family="IBM Plex Mono, monospace" font-size="9" fill="#E8DCC0">{i + 1}F</text>')
    return "".join(out)


def seal_iso(cx, cy, s, rot=0, op=1.0):
    return (f'<g transform="translate({cx} {cy}) {ISO} rotate({rot})" opacity="{op}">'
            f'<rect x="{-14 * s}" y="{-14 * s}" width="{28 * s}" height="{28 * s}" fill="#C8432B" stroke="#7A1E10" '
            f'stroke-width="1.2" filter="url(#e-sh-fbrush)"/>'
            f'<rect x="{-11 * s}" y="{-11 * s}" width="{22 * s}" height="{22 * s}" fill="none" stroke="#FBE4D6" stroke-width="1"/>'
            f'<text x="0" y="{7 * s}" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" '
            f'font-size="{19 * s}" fill="#FBE4D6">擊</text></g>')


def m_attack():
    out = ['<ellipse cx="72" cy="66" rx="40" ry="20" fill="url(#e-sh-redGlow)"/>', seal_iso(72, 64, 1.15, 0),
           '<ellipse cx="160" cy="66" rx="34" ry="17" fill="url(#e-sh-redGlow)" opacity="0.7"/>', seal_iso(160, 64, 0.95, 12, 0.85)]
    for cx, r in ((72, 30), (160, 25)):
        for a in (0, 90, 180, 270):
            x1, y1 = cx + r * math.cos(math.radians(a)), 64 + r * math.sin(math.radians(a)) / 2
            x2, y2 = cx + (r + 9) * math.cos(math.radians(a)), 64 + (r + 9) * math.sin(math.radians(a)) / 2
            out.append(f'<path d="M{f2(x1)} {f2(y1)} L{f2(x2)} {f2(y2)}" stroke="#FF8A6A" stroke-width="2.2" '
                       f'stroke-linecap="round" filter="url(#e-sh-glowS)"/>')
    out.append('<text x="72" y="112" text-anchor="middle" font-family="IBM Plex Mono, monospace" font-size="9" fill="#E8DCC0">1F</text>'
               '<text x="160" y="112" text-anchor="middle" font-family="IBM Plex Mono, monospace" font-size="9" fill="#E8DCC0">2F</text>')
    return "".join(out)


def sotdae(x, y, s=1.0):
    """솟대: tall pole with a carved wooden bird on top and a cloth flag."""
    return (f'<ellipse {iso_ell(x, y, 12 * s)} fill="url(#e-sh-shadow)"/>'
            f'<ellipse {iso_ell(x, y, 8 * s)} fill="none" stroke="#E2B13C" stroke-width="1.6" filter="url(#e-sh-glowS)"/>'
            f'<path d="M{x - 1.6 * s} {y} L{x - 1 * s} {y - 64 * s} L{x + 1 * s} {y - 64 * s} L{x + 1.6 * s} {y} Z" fill="url(#e-sh-gWood)" stroke="{OUTLINE}" stroke-width="0.6"/>'
            f'<path d="M{x - 0.6 * s} {y - 2} L{x - 0.2 * s} {y - 62 * s}" stroke="#E2B07A" stroke-width="0.5" opacity="0.6"/>'
            # carved bird
            f'<path d="M{x - 9 * s} {y - 66 * s} C{x - 4 * s} {y - 70 * s} {x + 3 * s} {y - 70 * s} {x + 7 * s} {y - 68 * s} '
            f'L{x + 12 * s} {y - 69 * s} L{x + 8 * s} {y - 66 * s} C{x + 4 * s} {y - 63 * s} {x - 4 * s} {y - 63 * s} {x - 9 * s} {y - 66 * s} Z" '
            f'fill="url(#e-sh-gWood)" stroke="{OUTLINE}" stroke-width="0.6"/>'
            f'<path d="M{x - 9 * s} {y - 66 * s} L{x - 13 * s} {y - 68 * s} L{x - 11 * s} {y - 64 * s} Z" fill="#7A4A28"/>'
            f'<circle cx="{x + 7.4 * s}" cy="{y - 68 * s}" r="0.7" fill="#120d08"/>'
            # flag
            f'<path d="M{x + 1 * s} {y - 56 * s} C{x + 10 * s} {y - 59 * s} {x + 16 * s} {y - 52 * s} {x + 24 * s} {y - 55 * s} '
            f'L{x + 22 * s} {y - 44 * s} C{x + 15 * s} {y - 41 * s} {x + 9 * s} {y - 48 * s} {x + 1 * s} {y - 45 * s} Z" '
            f'fill="#C8432B" stroke="#6A1E10" stroke-width="0.6"/>'
            f'<path d="M{x + 1 * s} {y - 52 * s} C{x + 9 * s} {y - 54 * s} {x + 15 * s} {y - 48 * s} {x + 23 * s} {y - 50 * s}" '
            f'stroke="#E2B13C" stroke-width="1.4" fill="none"/>')


def m_rally():
    out = ['<path d="M8 104 L40 88 L72 104 L40 120 Z" fill="#000" opacity="0.3"/>']
    # little building corner (giwa eave) at left as origin
    out.append('<path d="M6 84 L36 70 L58 81 L28 95 Z" fill="#2A2C30" stroke="#120d08" stroke-width="0.8"/>'
               '<path d="M6 84 L28 95 L28 112 L6 101 Z" fill="#6E5A44" stroke="#120d08" stroke-width="0.6"/>'
               '<path d="M28 95 L58 81 L58 98 L28 112 Z" fill="#4A3A2C" stroke="#120d08" stroke-width="0.6"/>')
    for k in range(6):
        out.append(f'<path d="M{8 + k * 5} {83 - k * 2.4} L{30 + k * 5} {94 - k * 2.4}" stroke="#4E5258" stroke-width="1.2"/>')
    pts = [(50, 96), (80, 92), (110, 84), (136, 78), (160, 76)]
    d = "M" + " Q".join(f"{x} {y}" for x, y in pts[:1]) + "".join(
        f" Q{f2((pts[i][0] + pts[i + 1][0]) / 2)} {f2(min(pts[i][1], pts[i + 1][1]) - 6)} {pts[i + 1][0]} {pts[i + 1][1]}" for i in range(len(pts) - 1))
    out.append(f'<path d="{d}" fill="none" stroke="#000" stroke-width="3" opacity="0.4" stroke-dasharray="2 5" stroke-linecap="round" transform="translate(1 1.5)"/>'
               f'<path d="{d}" fill="none" stroke="#F2D58A" stroke-width="2.4" stroke-dasharray="2 5" stroke-linecap="round" filter="url(#e-sh-glowS)"/>')
    out.append(sotdae(168, 80, 1.0))
    return "".join(out)


def mini_gunner(x, y, s=0.32, extra=""):
    return (f'<ellipse {iso_ell(x, y, 9 * s / 0.32)} fill="url(#e-sh-shadow)"/>'
            f'<use href="#e-sh-gunner" x="{f2(x - 40 * s)}" y="{f2(y - 112 * s)}" width="{f2(80 * s)}" height="{f2(124 * s)}"{extra}/>')


def m_queue():
    pts = [(30, 92), (86, 70), (140, 86), (192, 50)]
    out = []
    for i in range(len(pts) - 1):
        (x1, y1), (x2, y2) = pts[i], pts[i + 1]
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2 - 10
        d = f"M{x1} {y1} Q{f2(mx)} {f2(my)} {x2} {y2}"
        out.append(f'<path d="{d}" fill="none" stroke="#000" stroke-width="3.4" stroke-dasharray="0.1 7" stroke-linecap="round" opacity="0.45" transform="translate(1 1.4)"/>'
                   f'<path d="{d}" fill="none" stroke="#FBE4D6" stroke-width="3" stroke-dasharray="0.1 7" stroke-linecap="round"/>')
    out.append(mini_gunner(30, 94))
    for i, (x, y) in enumerate(pts[1:]):
        out.append(f'<ellipse {iso_ell(x, y, 13)} fill="url(#e-sh-redGlow)"/>'
                   f'<g transform="translate({x} {y}) {ISO}"><rect x="-6.5" y="-6.5" width="13" height="13" fill="#C8432B" stroke="#7A1E10" '
                   f'stroke-width="0.8" filter="url(#e-sh-fbrush)"/></g>'
                   f'<text x="{x}" y="{y + 3.2}" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="9" '
                   f'fill="#FBE4D6">{"一二三"[i]}</text>')
    out.append('<text x="208" y="112" text-anchor="end" font-family="IBM Plex Mono, monospace" font-size="9" fill="#E8DCC0">SHIFT</text>')
    return "".join(out)


def m_drag():
    out = ['<rect x="28" y="18" width="164" height="86" fill="#E2B13C" opacity="0.08"/>']
    for x, y, sx, sy in ((28, 18, 1, 1), (192, 18, -1, 1), (28, 104, 1, -1), (192, 104, -1, -1)):
        out.append(f'<path d="M{x} {y + 18 * sy} L{x} {y} L{x + 26 * sx} {y}" fill="none" stroke="#000" stroke-width="5" '
                   f'opacity="0.4" stroke-linecap="round" transform="translate(1 1.4)"/>'
                   f'<path d="M{x} {y + 18 * sy} L{x} {y} L{x + 26 * sx} {y}" fill="none" stroke="#F2D58A" stroke-width="3.6" '
                   f'stroke-linecap="round" filter="url(#e-sh-fbrush)"/>')
    out.append('<rect x="28" y="18" width="164" height="86" fill="none" stroke="#F2D58A" stroke-width="0.8" stroke-dasharray="3 4" opacity="0.6"/>')
    for x, y in ((66, 62), (100, 52), (126, 80), (156, 60)):
        out.append(f'<ellipse {iso_ell(x, y + 1, 12)} fill="none" stroke="#E2B13C" stroke-width="1.6" filter="url(#e-sh-glowS)"/>'
                   + mini_gunner(x, y + 1))
    out.append('<path d="M190 102 L190 116 L194 112 L197 119 L199.6 118 L196.6 111 L202 111 Z" fill="#FFF3C0" stroke="#120d08" stroke-width="0.8"/>')
    return "".join(out)


def m_nobuild():
    out = []
    # ghost hall footprint 3x2 on iso diamond grid
    for i in range(3):
        for j in range(2):
            cx = 110 + (i - j) * 24 - 12
            cy = 52 + (i + j) * 12 - 6
            out.append(f'<path d="M{cx} {cy - 12} L{cx + 24} {cy} L{cx} {cy + 12} L{cx - 24} {cy} Z" fill="#E5604D" '
                       f'fill-opacity="{0.42 if (i + j) % 2 else 0.3}" stroke="#FF8A6A" stroke-width="1"/>')
            # lattice 문살 inside each tile
            out.append(f'<path d="M{cx - 12} {cy - 6} L{cx + 12} {cy + 6} M{cx + 12} {cy - 6} L{cx - 12} {cy + 6}" '
                       f'stroke="#FFD0C0" stroke-width="0.5" opacity="0.6"/>')
    out.append('<g filter="url(#e-sh-glowS)"><path d="M84 40 L136 84 M136 40 L84 84" stroke="#2A0A06" stroke-width="10" '
               'stroke-linecap="round" opacity="0.6" transform="translate(1.5 2)"/>'
               '<path d="M84 40 L136 84 M136 40 L84 84" stroke="#E5604D" stroke-width="8" stroke-linecap="round" filter="url(#e-sh-fbrush)"/>'
               '<path d="M86 41 L134 82" stroke="#FFB6A0" stroke-width="1.6" stroke-linecap="round" opacity="0.7"/></g>')
    # rock blocking
    out.append('<ellipse cx="160" cy="86" rx="16" ry="7" fill="#000" opacity="0.4"/>'
               '<path d="M146 84 L150 72 L162 68 L172 74 L174 84 L162 90 Z" fill="#6E6658" stroke="#120d08" stroke-width="0.7"/>'
               '<path d="M150 72 L162 68 L166 78 L152 80 Z" fill="#9A9080"/>')
    out.append('<text x="214" y="112" text-anchor="end" font-family="IBM Plex Mono, monospace" font-size="9" fill="#FFB6A0">BLOCKED · 3×2</text>')
    return "".join(out)


# ------------------------------------------------------------------ fx helpers

def fx_frame(pid, label, body, seed, tone="dark", w=100, h=100):
    gd, gb = ground_patch(pid, 0, 0, w, h, seed, tone, density=0.6)
    return svg(92, 92 * h // w, f"0 0 {w} {h}", label, gb + body, gd)


def flame(cx, base, hgt, wid, lean=0.0):
    tip = cx + lean
    return (f"M{f2(cx - wid)} {f2(base)} C{f2(cx - wid)} {f2(base - hgt * 0.45)} {f2(tip - wid * 0.35)} {f2(base - hgt * 0.65)} "
            f"{f2(tip)} {f2(base - hgt)} C{f2(tip + wid * 0.25)} {f2(base - hgt * 0.62)} {f2(cx + wid)} {f2(base - hgt * 0.45)} "
            f"{f2(cx + wid)} {f2(base)} C{f2(cx + wid * 0.5)} {f2(base + wid * 0.35)} {f2(cx - wid * 0.5)} {f2(base + wid * 0.35)} {f2(cx - wid)} {f2(base)} Z")


def fire_stack(cx, base, s, rng, filt="e-sh-ffire"):
    layers = [("#7A1A0A", 1.0), ("#D8481E", 0.82), ("#F5902E", 0.62), ("#FFD670", 0.42), ("#FFF6D0", 0.22)]
    out = [f'<g filter="url(#{filt})">']
    for col, k in layers:
        for j in range(3):
            dx = (j - 1) * 6 * s * k
            out.append(f'<path d="{flame(cx + dx, base, (26 + rng.uniform(-4, 6)) * s * k * (1.15 if j == 1 else 0.8), 7 * s * k, rng.uniform(-3, 3) * s)}" fill="{col}"/>')
    out.append("</g>")
    return "".join(out)


def smoke_cloud(cx, cy, s, rng, n=9, paint="e-sh-smoke", op=0.95, filt="e-sh-fsmoke"):
    out = [f'<g filter="url(#{filt})" opacity="{op}">']
    for _ in range(n):
        x, y = cx + rng.uniform(-14, 14) * s, cy + rng.uniform(-10, 8) * s
        r = rng.uniform(7, 12) * s
        out.append(f'<circle cx="{f2(x + 1.5 * s)}" cy="{f2(y + 2 * s)}" r="{f2(r)}" fill="#14110E" opacity="0.6"/>')
    for _ in range(n):
        x, y = cx + rng.uniform(-14, 14) * s, cy + rng.uniform(-12, 6) * s
        r = rng.uniform(6, 11) * s
        out.append(f'<circle cx="{f2(x)}" cy="{f2(y)}" r="{f2(r)}" fill="url(#{paint})"/>')
    out.append("</g>")
    return "".join(out)


def sparks(cx, cy, n, r0, r1, rng, col="#FFE29A", w=0.9):
    out = []
    for _ in range(n):
        a = rng.uniform(0, 2 * math.pi)
        a0, a1 = rng.uniform(r0, r0 + 4), rng.uniform(r1 * 0.7, r1)
        out.append(f'<path d="M{f2(cx + a0 * math.cos(a))} {f2(cy + a0 * math.sin(a) * 0.7)} L{f2(cx + a1 * math.cos(a))} {f2(cy + a1 * math.sin(a) * 0.7)}" '
                   f'stroke="{col}" stroke-width="{w}" stroke-linecap="round"/>')
    return f'<g filter="url(#e-sh-glowS)">{"".join(out)}</g>'


def embers(cx, cy, n, spread, rng, rise=20):
    out = []
    for _ in range(n):
        x, y = cx + rng.uniform(-spread, spread), cy - rng.uniform(0, rise)
        out.append(f'<circle cx="{f2(x)}" cy="{f2(y)}" r="{f2(rng.uniform(0.5, 1.4))}" fill="url(#e-sh-ember)"/>')
    return f'<g filter="url(#e-sh-glowS)">{"".join(out)}</g>'


def debris(cx, cy, n, rng, reach=34):
    out = []
    for _ in range(n):
        a = rng.uniform(math.pi * 1.05, math.pi * 1.95) if rng.random() < 0.8 else rng.uniform(0, math.pi)
        r = rng.uniform(reach * 0.5, reach)
        x, y = cx + r * math.cos(a), cy + r * math.sin(a) * 0.8
        s = rng.uniform(1.2, 2.6)
        out.append(f'<path d="M{f2(cx + (r - 8) * math.cos(a))} {f2(cy + (r - 8) * math.sin(a) * 0.8)} L{f2(x)} {f2(y)}" '
                   f'stroke="#3A2A1C" stroke-width="0.6" opacity="0.5"/>'
                   f'<path d="M{f2(x - s)} {f2(y)} L{f2(x)} {f2(y - s)} L{f2(x + s)} {f2(y + s * 0.4)} L{f2(x)} {f2(y + s)} Z" '
                   f'fill="{rng.choice(["#4A3A2A", "#2A221A", "#6A5A44"])}" stroke="#0a0806" stroke-width="0.3" '
                   f'transform="rotate({rng.randint(0, 90)} {f2(x)} {f2(y)})"/>')
    return "".join(out)


def scorch(cx, cy, rx, rng, glow=True):
    out = [f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{rx / 2}" fill="url(#e-sh-scorch)" filter="url(#e-sh-fbrush)"/>']
    cr = []
    for _ in range(7):
        a = rng.uniform(0, 2 * math.pi)
        r1, r2 = rx * 0.3, rx * rng.uniform(0.75, 1.05)
        mx = cx + (r1 + r2) / 2 * math.cos(a + 0.2)
        cr.append(f"M{f2(cx + r1 * math.cos(a))} {f2(cy + r1 * math.sin(a) / 2)} Q{f2(mx)} {f2(cy + (r1 + r2) / 2 * math.sin(a + 0.2) / 2)} "
                  f"{f2(cx + r2 * math.cos(a))} {f2(cy + r2 * math.sin(a) / 2)}")
    out.append(f'<path d="{" ".join(cr)}" fill="none" stroke="#000" stroke-width="0.9" opacity="0.8"/>')
    if glow:
        out.append(embers(cx, cy + 2, 10, rx * 0.5, rng, rise=4))
    return "".join(out)


# ------------------------------------------------------------------ fx strips

def fx_explosion():
    frames = []
    rng = random.Random(101)
    # 1 flash
    b = (f'<ellipse cx="50" cy="66" rx="40" ry="20" fill="url(#e-sh-goldGlow)"/>'
         f'<ellipse cx="50" cy="66" rx="30" ry="15" fill="none" stroke="#FFF2C8" stroke-width="1.6" opacity="0.8" filter="url(#e-sh-b1)"/>'
         + "".join(f'<path d="M50 56 L{f2(50 + 40 * math.cos(a) * 0.12)} {f2(56 + 40 * math.sin(a) * 0.12)} L{f2(50 + 44 * math.cos(a))} {f2(56 + 44 * math.sin(a) * 0.75)} Z" '
                   f'fill="#FFF6CC" opacity="0.9" filter="url(#e-sh-b05)"/>'
                   for a in [k * math.pi / 4 + 0.2 for k in range(8)])
         + '<circle cx="50" cy="56" r="22" fill="url(#e-sh-flash)" filter="url(#e-sh-b1)"/>'
         + '<circle cx="50" cy="56" r="7" fill="#FFFFFF" filter="url(#e-sh-glow)"/>'
         + sparks(50, 56, 18, 10, 40, rng))
    frames.append(b)
    # 2 fireball + debris
    b = (f'<ellipse cx="50" cy="74" rx="34" ry="14" fill="url(#e-sh-redGlow)"/>'
         + debris(50, 60, 14, rng)
         + '<g filter="url(#e-sh-ffire2)"><circle cx="50" cy="56" r="24" fill="url(#e-sh-fireball)"/>'
           '<circle cx="38" cy="50" r="12" fill="url(#e-sh-fireball)"/><circle cx="62" cy="52" r="13" fill="url(#e-sh-fireball)"/>'
           '<circle cx="50" cy="40" r="12" fill="url(#e-sh-fireball)"/></g>'
         + fire_stack(50, 72, 1.0, rng)
         + sparks(50, 54, 12, 22, 42, rng, "#FFC870")
         + embers(50, 50, 12, 30, rng, 26))
    frames.append(b)
    # 3 smoke billow with fire base
    b = (scorch(50, 76, 30, rng, glow=False)
         + smoke_cloud(50, 36, 1.35, rng, 10)
         + '<g opacity="0.85">' + fire_stack(50, 76, 0.7, rng) + '</g>'
         + '<ellipse cx="50" cy="56" rx="18" ry="12" fill="#F5902E" opacity="0.35" filter="url(#e-sh-b5)"/>'
         + embers(50, 46, 14, 26, rng, 30))
    frames.append(b)
    # 4 embers + thinning smoke column
    b = (scorch(50, 76, 30, rng)
         + smoke_cloud(54, 26, 1.1, rng, 7, op=0.7)
         + smoke_cloud(50, 52, 0.7, rng, 5, op=0.6)
         + '<g opacity="0.7">' + fire_stack(44, 78, 0.35, rng) + fire_stack(58, 76, 0.28, rng) + '</g>'
         + embers(50, 60, 22, 30, rng, 50))
    frames.append(b)
    # 5 scorch decal (iso)
    b = (scorch(50, 70, 36, rng) + debris(50, 70, 10, rng, reach=40)
         + '<path d="M48 64 C44 54 54 48 50 38 C47 30 54 24 52 16" fill="none" stroke="#8A847A" stroke-width="3" '
           'opacity="0.35" filter="url(#e-sh-b2)"/>')
    frames.append(b)
    labels = ["섬광", "화구 · 파편", "연기 기둥", "불티", "그을음"]
    return [fx_frame(f"e-fxx{i}", f"흑색화약 폭발 {i + 1}프레임", f, 300 + i) for i, f in enumerate(frames)], labels


def gunner_at(x, y, s, extra=""):
    return (f'<ellipse cx="{x}" cy="{y}" rx="{f2(16 * s)}" ry="{f2(7 * s)}" fill="url(#e-sh-shadow)"/>'
            f'<use href="#e-sh-gunner" x="{f2(x - 40 * s)}" y="{f2(y - 112 * s)}" width="{f2(80 * s)}" height="{f2(124 * s)}"{extra}/>')


def muzzle_xy(x, y, s):
    # symbol (28,22) in -40..40 / -4..120 -> local
    return x + 28 * s, y - 112 * s + (22 + 4) * s


def fx_matchlock():
    rng = random.Random(202)
    gx, gy, s = 30, 90, 0.62
    mx, my = muzzle_xy(gx, gy, s)
    f1 = (gunner_at(gx, gy, s)
          + f'<g transform="translate({f2(mx)} {f2(my)}) rotate(-50)">'
            '<path d="M0 0 L22 -6 L30 0 L22 6 Z" fill="#FFD68A" filter="url(#e-sh-glow)"/>'
            '<path d="M0 0 L16 -3 L22 0 L16 3 Z" fill="#FFFFFF"/></g>'
          + f'<circle cx="{f2(mx + 6)}" cy="{f2(my - 6)}" r="10" fill="url(#e-sh-flash)"/>'
          + sparks(mx + 8, my - 8, 10, 4, 18, rng)
          + smoke_cloud(mx + 4, my - 2, 0.45, rng, 5, "e-sh-smokeL", 0.9, "e-sh-fsmoke2"))
    f2_ = (gunner_at(gx - 1, gy + 1, s)
           + smoke_cloud(mx + 6, my - 4, 0.7, rng, 7, "e-sh-smokeL", 0.85, "e-sh-fsmoke2")
           + f'<path d="M{f2(mx + 14)} {f2(my - 14)} L94 6" stroke="#FFE7A8" stroke-width="1.4" stroke-linecap="round" filter="url(#e-sh-glowS)"/>'
           + '<circle cx="94" cy="6" r="1.6" fill="#FFFFFF" filter="url(#e-sh-glowS)"/>')
    f3 = (gunner_at(gx, gy, s)
          + smoke_cloud(mx + 12, my - 14, 1.0, rng, 9, "e-sh-smokeL", 0.7, "e-sh-fsmoke")
          + '<g>' + "".join(f'<path d="M{44 + k * 3} {88 - k} l2 -1" stroke="#C9A35A" stroke-width="1.2"/>' for k in range(2)) + '</g>')
    labels = ["섬광", "탄도", "백연"]
    return [fx_frame(f"e-fxm{i}", f"화승총 {i + 1}프레임", f, 400 + i) for i, f in enumerate([f1, f2_, f3])], labels


def rocket(x, y, ang, s=1.0, lit=True):
    t = f'translate({f2(x)} {f2(y)}) rotate({ang}) scale({s})'
    flame_ = ('<path d="M-30 0 L-40 -3 L-46 0 L-40 3 Z" fill="#FFD68A" filter="url(#e-sh-glowS)"/>'
              '<path d="M-30 0 L-36 -1.4 L-39 0 L-36 1.4 Z" fill="#FFFFFF"/>') if lit else ""
    return (f'<g transform="{t}">{flame_}'
            '<path d="M-30 -0.6 L10 -0.6 L10 0.6 L-30 0.6 Z" fill="#8A6A44"/>'  # bamboo shaft
            '<path d="M-6 -2.8 L8 -2.8 C11 -2.8 12 -1 12 0 C12 1 11 2.8 8 2.8 L-6 2.8 Z" fill="url(#e-sh-gRed)" stroke="#120d08" stroke-width="0.4"/>'
            '<path d="M-4 -2.8 L-4 2.8 M2 -2.8 L2 2.8" stroke="#E2B13C" stroke-width="0.7"/>'
            '<path d="M12 0 L17 -1.4 L12 -2.4 Z M12 0 L17 1.4 L12 2.4 Z" fill="#C8CDD2"/>'
            '<path d="M-28 -0.6 L-32 -4 L-26 -0.6 Z M-28 0.6 L-32 4 L-26 0.6 Z" fill="#E8E1C8"/></g>')


def fx_hwacha():
    rng = random.Random(303)
    # cart corner + ignition
    cart = ('<path d="M6 92 L36 78 L52 86 L22 100 Z" fill="#4A2E1E" stroke="#120d08" stroke-width="0.6"/>'
            '<path d="M14 82 L40 70 L48 76 L22 88 Z" fill="#7A4A28" stroke="#120d08" stroke-width="0.6"/>'
            + "".join(f'<circle cx="{18 + i * 4}" cy="{82 - i * 2}" r="1.2" fill="#120d08"/>' for i in range(7))
            + '<ellipse cx="14" cy="94" rx="6" ry="6" fill="none" stroke="#2E1E14" stroke-width="2.2"/>')
    f1 = (cart + rocket(42, 66, -38, 0.85)
          + sparks(32, 74, 16, 2, 16, rng)
          + smoke_cloud(28, 76, 0.55, rng, 6, "e-sh-smokeL", 0.8, "e-sh-fsmoke2"))
    trail = []
    for k in range(26):
        t = k / 25
        x, y = 10 + 70 * t, 86 - 64 * t + 10 * math.sin(t * 3)
        trail.append(f'<circle cx="{f2(x)}" cy="{f2(y)}" r="{f2(0.5 + rng.random() * 1.1)}" fill="{rng.choice(["#FFE29A", "#FFB04A", "#FFFFFF"])}"/>')
    f2_ = ('<path d="M10 86 C30 70 50 50 80 22" fill="none" stroke="#B6AEA0" stroke-width="7" opacity="0.35" filter="url(#e-sh-fsmoke2)"/>'
           f'<g filter="url(#e-sh-glowS)">{"".join(trail)}</g>' + rocket(82, 20, -42, 0.9))
    f3 = ('<ellipse cx="54" cy="74" rx="30" ry="12" fill="url(#e-sh-redGlow)"/>'
          + debris(54, 66, 10, rng, 28)
          + '<circle cx="54" cy="60" r="16" fill="url(#e-sh-fireball)" filter="url(#e-sh-ffire2)"/>'
          + fire_stack(54, 76, 0.6, rng)
          + sparks(54, 60, 20, 8, 38, rng)
          + embers(54, 56, 10, 22, rng, 22))
    labels = ["점화", "비행 · 불꼬리", "착탄"]
    return [fx_frame(f"e-fxh{i}", f"화차 로켓 {i + 1}프레임", f, 500 + i) for i, f in enumerate([f1, f2_, f3])], labels


def gob_flame(cx, base, s, rng):
    out = ['<g filter="url(#e-sh-ffire)">']
    for col, k in (("#1A5C8C", 1.0), ("#4FB3E8", 0.78), ("#A8DDF5", 0.55), ("#FFFFFF", 0.3)):
        out.append(f'<path d="{flame(cx, base, 24 * s * k, 6 * s * k, rng.uniform(-2, 2) * s)}" fill="{col}"/>')
    out.append("</g>")
    return "".join(out)


def fx_goblin():
    rng = random.Random(404)
    f1 = ('<path d="M14 84 Q40 18 82 54" fill="none" stroke="#A8DDF5" stroke-width="1" stroke-dasharray="2 3" opacity="0.6"/>'
          '<path d="M36 40 C46 30 56 30 64 36" fill="none" stroke="#4FB3E8" stroke-width="6" opacity="0.5" filter="url(#e-sh-b3)"/>'
          '<circle cx="66" cy="38" r="12" fill="url(#e-sh-gob)" filter="url(#e-sh-glow)"/>'
          + gob_flame(64, 44, 0.9, rng)
          + '<circle cx="66" cy="40" r="4" fill="#FFFFFF"/>'
          + sparks(66, 40, 8, 6, 16, rng, "#C8EEFF"))
    f2_ = ('<ellipse cx="54" cy="70" rx="36" ry="16" fill="url(#e-sh-gobGround)"/>'
           + "".join(gob_flame(54 + dx, 74 + dy, s, rng) for dx, dy, s in ((-22, 2, 0.6), (18, 4, 0.7), (0, 0, 1.2), (-8, 8, 0.5), (24, -4, 0.45)))
           + "".join(f'<circle cx="{f2(54 + 30 * math.cos(a))}" cy="{f2(64 + 14 * math.sin(a) - 8)}" r="{f2(1 + rng.random() * 1.6)}" '
                     f'fill="#A8DDF5" filter="url(#e-sh-glowS)"/>' for a in [rng.uniform(math.pi, 2 * math.pi) for _ in range(10)])
           + sparks(54, 66, 12, 10, 36, rng, "#C8EEFF"))
    f3 = ('<ellipse cx="54" cy="72" rx="30" ry="13" fill="url(#e-sh-gobGround)" opacity="0.6"/>'
          + scorch(54, 74, 24, rng, glow=False)
          + "".join(gob_flame(54 + dx, 74 + dy, s, rng) for dx, dy, s in ((-14, 2, 0.35), (10, 4, 0.45), (0, -2, 0.55)))
          + smoke_cloud(56, 40, 0.7, rng, 6, "e-sh-smoke", 0.55))
    labels = ["투척", "튀김", "잔불"]
    return [fx_frame(f"e-fxg{i}", f"도깨비불 {i + 1}프레임", f, 600 + i) for i, f in enumerate([f1, f2_, f3])], labels


def shield_kite(x, y, s):
    return (f'<g transform="translate({x} {y}) rotate(-8) scale({s})">'
            '<rect x="-12" y="-15" width="24" height="30" fill="url(#e-sh-hanji)" stroke="#5A4A30" stroke-width="0.8"/>'
            '<path d="M-12 -15 L12 -15 L12 -9 L-12 -9 Z" fill="#C8432B"/>'
            '<path d="M-12 -15 L-4 -15 L-12 -7 Z M12 -15 L4 -15 L12 -7 Z" fill="#3B4E8C"/>'
            '<path d="M-12 15 L-4 15 L-12 7 Z M12 15 L4 15 L12 7 Z" fill="#E2B13C"/>'
            '<circle cx="0" cy="2" r="5.4" fill="#16140F" stroke="#5A4A30" stroke-width="0.6"/>'
            '<path d="M-12 -15 L12 15 M12 -15 L-12 15 M0 -15 L0 15 M-12 0 L12 0" stroke="#8A7A5A" stroke-width="0.4" opacity="0.7"/></g>')


def fire_arrow(x, y, ang, s=1.0):
    return (f'<g transform="translate({f2(x)} {f2(y)}) rotate({ang}) scale({s})">'
            '<path d="M-26 0 L8 0" stroke="#8A6A44" stroke-width="1.2"/>'
            '<path d="M-26 0 L-31 -3 L-28 0 L-31 3 Z" fill="#E8E1C8"/>'
            '<path d="M8 -2 L14 0 L8 2 Z" fill="#C8CDD2"/>'
            '<ellipse cx="4" cy="0" rx="5" ry="3.2" fill="#F5902E" filter="url(#e-sh-glow)"/>'
            '<ellipse cx="4" cy="0" rx="2.4" ry="1.6" fill="#FFF6CC"/></g>')


def fx_kite():
    rng = random.Random(505)
    f1 = ('<path d="M24 92 L30 30" stroke="#E8E1C8" stroke-width="0.5" opacity="0.7"/>'
          + shield_kite(32, 24, 0.9)
          + '<ellipse cx="24" cy="94" rx="8" ry="3.6" fill="url(#e-sh-shadow)"/>'
            '<path d="M18 92 L22 74 L28 74 L30 92 Z" fill="#3B4E8C" stroke="#120d08" stroke-width="0.5"/>'
            '<circle cx="25" cy="70" r="4" fill="url(#e-sh-gSkin)"/>'
            '<ellipse cx="25" cy="67.6" rx="8" ry="2.6" fill="#C8AE62" stroke="#5A4A30" stroke-width="0.5"/>'
            '<path d="M30 80 C38 70 38 62 32 54" fill="none" stroke="#8A6A44" stroke-width="1.6"/>'
          + fire_arrow(48, 66, -34, 0.9) + sparks(56, 60, 6, 3, 10, rng))
    f2_ = ('<path d="M6 92 C30 50 60 30 92 34" fill="none" stroke="#F5902E" stroke-width="1" stroke-dasharray="1 3" opacity="0.6"/>'
           '<path d="M30 60 C44 44 56 38 66 36" fill="none" stroke="#B6AEA0" stroke-width="3" opacity="0.35" filter="url(#e-sh-fsmoke2)"/>'
           + fire_arrow(76, 34, -6, 1.0) + embers(52, 46, 6, 14, rng, 6))
    f3 = ('<ellipse cx="56" cy="78" rx="20" ry="8" fill="url(#e-sh-redGlow)" opacity="0.7"/>'
          + scorch(56, 80, 12, rng)
          + fire_arrow(48, 66, 52, 1.0)
          + fire_stack(58, 80, 0.45, rng)
          + smoke_cloud(60, 52, 0.45, rng, 5, "e-sh-smoke", 0.6))
    labels = ["발사", "비행", "꽂힘 · 불붙음"]
    return [fx_frame(f"e-fxk{i}", f"방패연 불화살 {i + 1}프레임", f, 700 + i) for i, f in enumerate([f1, f2_, f3])], labels


# ------------------------------------------------------------------ unit state frames

def unit_state(pid, state):
    rng = random.Random(800 + len(state))
    gx, gy, s = 50, 112, 0.78
    w, h = 100, 124
    gd, gb = ground_patch(pid, 0, 0, w, h, 900 + len(state) * 7, "autumn", density=0.7)
    body = []
    if state == "idle":
        body.append(gunner_at(gx, gy, s))
    elif state == "selected":
        body.append(f'<ellipse {iso_ell(gx, gy, 26)} fill="url(#e-sh-goldGlow)" opacity="0.6"/>'
                    f'<ellipse {iso_ell(gx, gy, 22)} fill="none" stroke="#E2B13C" stroke-width="2" filter="url(#e-sh-glowS)"/>'
                    f'<ellipse {iso_ell(gx, gy, 22)} fill="none" stroke="#FFF3C0" stroke-width="0.6"/>')
        body.append(gunner_at(gx, gy, s))
        # segmented HP bar in a carved frame
        body.append('<rect x="24" y="6" width="52" height="7" rx="1.5" fill="#1A0C06" stroke="#C9A35A" stroke-width="0.7"/>'
                    + "".join(f'<rect x="{26 + i * 6.2}" y="7.6" width="5.4" height="3.8" fill="{"#8CC79A" if i < 6 else "#3A3A30"}"/>' for i in range(8))
                    + '<rect x="26" y="7.6" width="37" height="1.2" fill="#FFFFFF" opacity="0.35"/>')
    elif state == "moving":
        body.append(f'<use href="#e-sh-gunner" x="{f2(gx - 14 - 40 * s)}" y="{f2(gy + 4 - 112 * s)}" width="{f2(80 * s)}" height="{f2(124 * s)}" opacity="0.18"/>'
                    f'<use href="#e-sh-gunner" x="{f2(gx - 7 - 40 * s)}" y="{f2(gy + 2 - 112 * s)}" width="{f2(80 * s)}" height="{f2(124 * s)}" opacity="0.32"/>')
        body.append(smoke_cloud(gx - 18, gy - 4, 0.35, rng, 5, "e-sh-smokeL", 0.5, "e-sh-fsmoke2"))
        body.append(f'<g transform="rotate(4 {gx} {gy})">{gunner_at(gx + 4, gy, s)}</g>')
    elif state == "attack":
        body.append(gunner_at(gx - 2, gy + 1, s))
        mx, my = muzzle_xy(gx - 2, gy + 1, s)
        body.append(f'<g transform="translate({f2(mx)} {f2(my)}) rotate(-50)"><path d="M0 0 L20 -6 L28 0 L20 6 Z" fill="#FFD68A" filter="url(#e-sh-glow)"/>'
                    '<path d="M0 0 L14 -3 L19 0 L14 3 Z" fill="#FFFFFF"/></g>'
                    f'<circle cx="{f2(mx + 5)}" cy="{f2(my - 5)}" r="9" fill="url(#e-sh-flash)"/>'
                    + smoke_cloud(mx + 2, my, 0.4, rng, 5, "e-sh-smokeL", 0.85, "e-sh-fsmoke2")
                    + sparks(mx + 6, my - 6, 8, 3, 14, rng))
    elif state == "hit":
        body.append(gunner_at(gx + 2, gy, s, ' filter="url(#e-sh-hit)"'))
        body.append(f'<circle cx="{gx + 16}" cy="{gy - 52}" r="14" fill="url(#e-sh-redGlow)"/>'
                    + sparks(gx + 16, gy - 52, 14, 2, 18, rng, "#FFD0B0", 1.1)
                    + debris(gx + 16, gy - 40, 5, rng, 16))
    elif state == "dead":
        body.append(scorch(gx, gy - 6, 30, rng, glow=False))
        body.append(f'<g filter="url(#e-sh-dead)"><g transform="translate({gx + 24} {gy - 20}) scale(1 0.72) rotate(-96) '
                    f'translate({f2(-40 * s)} {f2(-70 * s)})"><use href="#e-sh-gunner" x="0" y="0" width="{f2(80 * s)}" height="{f2(124 * s)}"/></g></g>')
        body.append('<g transform="translate(76 104) rotate(-14)"><ellipse cx="0" cy="0" rx="11" ry="4.2" fill="#24211E" stroke="#120d08" stroke-width="0.6"/>'
                    '<path d="M-5 -1 C-5 -6 5 -6 5 -1 Z" fill="#141210"/><circle cx="0" cy="-5.6" r="1.2" fill="#C8432B"/></g>')
        body.append(smoke_cloud(gx - 4, gy - 46, 0.5, rng, 6, "e-sh-smoke", 0.5))
    return svg(92, 114, f"0 0 {w} {h}", f"화승총수 {state} 상태", gb + "".join(body), gd)


# ------------------------------------------------------------------ alerts and resources

def knot_cord(x, y0, y1):
    return (f'<path d="M{x} {y0} L{x} {y1}" stroke="#C8432B" stroke-width="1.6"/>'
            f'<path d="M{x - 4} {y0 + 3} C{x - 8} {y0 + 7} {x - 2} {y0 + 10} {x} {y0 + 6} C{x + 2} {y0 + 10} {x + 8} {y0 + 7} {x + 4} {y0 + 3} Z" '
            f'fill="#C8432B" stroke="#6A1E10" stroke-width="0.5"/>')


def tassel(x, y, col="#C8432B"):
    return (f'<circle cx="{x}" cy="{y}" r="2.2" fill="url(#e-sh-brass)"/>'
            + "".join(f'<path d="M{x + dx} {y + 1.4} L{x + dx * 1.6} {y + 13}" stroke="{col}" stroke-width="0.9"/>' for dx in (-1.6, -0.6, 0.4, 1.4))
            + f'<path d="M{x - 2.6} {y + 4} L{x + 2.6} {y + 4}" stroke="#E2B13C" stroke-width="1.2"/>')


def wooden_tag(pid, ch, ink, accent, seed):
    rng = random.Random(seed)
    defs = f'<clipPath id="{pid}-c"><path d="M14 18 L50 18 L56 26 L56 78 L50 84 L14 84 L8 78 L8 26 Z"/></clipPath>'
    b = [knot_cord(32, 0, 18),
         '<path d="M17 21 L53 21 L59 29 L59 81 L53 87 L17 87 L11 81 L11 29 Z" fill="#000" opacity="0.5" filter="url(#e-sh-b2)"/>',
         f'<path d="M14 18 L50 18 L56 26 L56 78 L50 84 L14 84 L8 78 L8 26 Z" fill="url(#e-sh-lac-normal)" stroke="{OUTLINE}" stroke-width="0.9"/>',
         f'<g clip-path="url(#{pid}-c)"><g opacity="0.9">{wood_grain(rng, 8, 18, 48, 66, 12)}</g></g>',
         '<path d="M14 23 L50 23 L52 26 L52 78 L50 80 L14 80 L12 78 L12 26 Z" fill="none" stroke="#F2C48A" stroke-width="0.6" opacity="0.4"/>',
         '<path d="M15 25 L49 25 L50 27 L50 77 L49 78 L15 78 L14 77 L14 27 Z" fill="none" stroke="#000" stroke-width="0.8" opacity="0.6"/>',
         f'<g opacity="0.8">{cloud_curl(32, 30, 0.5, accent)}{cloud_curl(32, 74, 0.5, accent)}</g>',
         f'<text x="32.8" y="61.6" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="24" fill="#000" opacity="0.6">{ch}</text>',
         f'<text x="32" y="60.6" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="24" fill="{ink}">{ch}</text>',
         '<circle cx="32" cy="22" r="1.6" fill="url(#e-sh-rivet)"/>',
         tassel(32, 86, accent)]
    return svg(48, 78, "0 0 64 104", f"{ch} 걸이 나무패", "".join(b), defs)


def seal_badge(pid, ch, col, edge, glow, seed, cracked=False):
    rng = random.Random(seed)
    b = []
    if glow:
        b.append(f'<circle cx="40" cy="40" r="38" fill="url(#{glow})"/>')
    b.append(f'<rect x="15" y="17" width="52" height="52" rx="4" fill="#000" opacity="0.5" filter="url(#e-sh-b2)" transform="rotate(-5 41 43)"/>')
    b.append(f'<g transform="rotate(-5 40 40)"><rect x="13" y="13" width="54" height="54" rx="4" fill="{col}" stroke="{edge}" stroke-width="1.2" '
             'filter="url(#e-sh-fbrush)"/>'
             f'<rect x="13" y="13" width="54" height="54" rx="4" filter="url(#e-sh-noiseL)" opacity="0.35"/>'
             f'<rect x="18" y="18" width="44" height="44" rx="2" fill="none" stroke="#FBE4D6" stroke-width="1.6" opacity="0.85" filter="url(#e-sh-fbrush)"/>'
             f'<text x="40" y="52" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="30" fill="#FBE4D6">{ch}</text>')
    if cracked:
        b.append('<path d="M20 22 L34 36 L30 46 L44 58 L40 66 M34 36 L46 32 L60 40" fill="none" stroke="#120d08" stroke-width="1.6" opacity="0.8"/>')
    for _ in range(14):  # ink speckle
        b.append(f'<circle cx="{f2(rng.uniform(16, 64))}" cy="{f2(rng.uniform(16, 64))}" r="{f2(rng.uniform(0.3, 1.0))}" fill="#FBE4D6" opacity="0.5"/>')
    b.append("</g>")
    return svg(54, 54, "0 0 80 80", f"{ch} 인장 배지", "".join(b))


def res_ore():
    b = ['<ellipse cx="40" cy="62" rx="30" ry="12" fill="url(#e-sh-oreGlow)"/>',
         '<path d="M10 62 L18 50 L34 46 L54 48 L68 56 L66 66 L44 72 L20 70 Z" fill="#4A4438" stroke="#120d08" stroke-width="0.8"/>',
         '<path d="M18 50 L34 46 L54 48 L46 56 L24 58 Z" fill="#7A7060"/>',
         '<path d="M24 58 L46 56 L44 72 L20 70 Z" fill="#2E2A22"/>']
    crystals = [(28, 54, 6, 34, -14), (40, 52, 8, 46, 2), (52, 56, 6, 30, 16), (34, 60, 4.5, 20, -26), (48, 62, 4, 18, 28)]
    for x, y, w, h, rot in crystals:
        b.append(f'<g transform="rotate({rot} {x} {y})">'
                 f'<path d="M{x - w} {y} L{x - w} {y - h * 0.72} L{x} {y - h} L{x} {y} Z" fill="#7FCFBC" stroke="#0E3A30" stroke-width="0.6"/>'
                 f'<path d="M{x} {y} L{x} {y - h} L{x + w} {y - h * 0.72} L{x + w} {y} Z" fill="#3E9C88" stroke="#0E3A30" stroke-width="0.6"/>'
                 f'<path d="M{x - w} {y - h * 0.72} L{x} {y - h} L{x + w} {y - h * 0.72} L{x} {y - h * 0.62} Z" fill="#E6FFF8"/>'
                 f'<path d="M{x - w * 0.6} {y - 2} L{x - w * 0.6} {y - h * 0.66}" stroke="#FFFFFF" stroke-width="0.8" opacity="0.7"/></g>')
    b.append('<circle cx="40" cy="30" r="10" fill="#CFF5EA" opacity="0.35" filter="url(#e-sh-b5)"/>')
    b.append('<g filter="url(#e-sh-glowS)"><circle cx="30" cy="20" r="0.9" fill="#FFFFFF"/><circle cx="54" cy="28" r="0.8" fill="#FFFFFF"/>'
             '<path d="M44 10 L44 16 M41 13 L47 13" stroke="#FFFFFF" stroke-width="0.8"/></g>')
    return svg(58, 58, "0 0 80 80", "귀화석 아이콘", "".join(b))


def res_gas():
    rng = random.Random(77)
    b = ['<ellipse cx="40" cy="66" rx="30" ry="10" fill="url(#e-sh-gasGlow)"/>',
         '<path d="M8 66 L22 58 L34 62 L40 56 L48 62 L60 56 L72 64 L62 72 L20 74 Z" fill="#3A3428" stroke="#120d08" stroke-width="0.8"/>',
         '<path d="M34 62 L40 56 L48 62 L42 68 Z" fill="#120d08"/>',
         '<path d="M36 64 L40 59 L46 63 L41 66 Z" fill="#B79AE0" filter="url(#e-sh-glowS)"/>',
         smoke_cloud(40, 34, 0.9, rng, 7, "e-sh-gasGlow", 0.95, "e-sh-fsmoke2"),
         f'<g opacity="0.9">{cloud_curl(40, 30, 1.1, "#E4D6FA")}{cloud_curl(30, 44, 0.7, "#CDBAF0")}</g>',
         '<path d="M40 58 C34 50 46 44 40 36" fill="none" stroke="#E4D6FA" stroke-width="2" opacity="0.6" filter="url(#e-sh-b1)"/>']
    return svg(58, 58, "0 0 80 80", "산기 아이콘", "".join(b))


def res_food():
    b = ['<ellipse cx="42" cy="68" rx="28" ry="7" fill="url(#e-sh-shadow)"/>',
         '<path d="M16 26 C16 18 64 18 64 26 L66 62 C66 72 14 72 14 62 Z" fill="#C8AE62" stroke="#3A2E14" stroke-width="1"/>',
         '<path d="M16 26 C16 18 64 18 64 26 L66 62 C66 72 14 72 14 62 Z" fill="url(#e-sh-hanji)" opacity="0.18"/>']
    # straw weave
    for k in range(12):
        x = 18 + k * 4
        b.append(f'<path d="M{x} 24 C{x - 1} 40 {x + 1} 52 {x - 0.6} 68" stroke="#8A7038" stroke-width="0.6" opacity="0.8"/>')
    for y in range(28, 68, 3):
        b.append(f'<path d="M15 {y} C30 {y + 2} 50 {y + 2} 65 {y}" stroke="#E8D49A" stroke-width="0.4" opacity="0.6"/>')
    b.append('<path d="M16 26 C16 32 64 32 64 26 C64 18 16 18 16 26 Z" fill="#E8DDBF" stroke="#3A2E14" stroke-width="0.8"/>')
    for x, y in ((28, 24), (34, 26), (40, 23), (46, 25), (52, 24), (38, 27), (44, 27), (32, 22)):
        b.append(f'<ellipse cx="{x}" cy="{y}" rx="1.4" ry="0.8" fill="#FFFFFF" stroke="#C8BEA8" stroke-width="0.2"/>')
    for y in (36, 56):
        b.append(f'<path d="M14 {y} C30 {y + 4} 50 {y + 4} 66 {y}" stroke="#4A2E1E" stroke-width="2.6" fill="none"/>'
                 f'<path d="M14 {y - 0.8} C30 {y + 3.2} 50 {y + 3.2} 66 {y - 0.8}" stroke="#8A6A44" stroke-width="0.8" fill="none" stroke-dasharray="1.4 1.2"/>')
    b.append('<path d="M18 30 L18 64" stroke="#FFF3C0" stroke-width="1.6" opacity="0.25"/>')
    return svg(58, 58, "0 0 80 80", "식량 아이콘", "".join(b))


# ------------------------------------------------------------------ page chrome

def corner_svg(pos):
    t = {"tl": "", "tr": "scale(-1 1) translate(-18 0)", "bl": "scale(1 -1) translate(0 -18)", "br": "scale(-1 -1) translate(-18 -18)"}[pos]
    css = {"tl": "top: -3px; left: -3px", "tr": "top: -3px; right: -3px", "bl": "bottom: -3px; left: -3px", "br": "bottom: -3px; right: -3px"}[pos]
    return (f'<svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true" style="position: absolute; {css}">'
            f'<g transform="{t}"><path d="M0 0 H18 V4 H6 Q4 4 4 6 V18 H0 Z" fill="#B8924A" stroke="#2E1E0A" stroke-width="0.6"/>'
            '<path d="M1 17 V1 H17" fill="none" stroke="#FBE7A6" stroke-width="0.7" opacity="0.7"/>'
            '<circle cx="11" cy="2" r="1.1" fill="#FBE7A6" stroke="#5E4318" stroke-width="0.4"/>'
            '<circle cx="2" cy="11" r="1.1" fill="#FBE7A6" stroke="#5E4318" stroke-width="0.4"/></g></svg>')


def card(title, sub, inner, extra=""):
    corners = "".join(corner_svg(p) for p in ("tl", "tr", "bl", "br"))
    band = ('<div style="display: flex; height: 3px">'
            '<span style="flex: 1; background: #C8432B"></span><span style="flex: 1; background: #3E8F7A"></span>'
            '<span style="flex: 1; background: #E2B13C"></span><span style="flex: 1; background: #2F6E8E"></span>'
            '<span style="flex: 1; background: #EFE8D8"></span></div>')
    return (f'<section style="position: relative; display: flex; flex-direction: column; gap: 8px; padding: 10px 14px 12px; '
            f'background: linear-gradient(160deg, #2A2219 0%, #1E1A14 55%, #171410 100%); border: 1px solid #5E4A30; '
            f'box-shadow: inset 0 0 0 2px #120E0A, inset 0 0 0 3px #3A2E20, 0 6px 14px rgba(0,0,0,0.45); {extra}">'
            f'{corners}'
            f'<div style="display: flex; align-items: baseline; gap: 10px">'
            f'<h2 style="margin: 0; font-family: {SERIF}; font-weight: 800; font-size: 17px; color: {GOLD_T}; '
            f'text-shadow: 0 1px 0 #000, 0 0 8px rgba(226,177,60,0.25)">{title}</h2>'
            f'<span style="font-size: 12px; color: {MUTED}">{sub}</span></div>{band}{inner}</section>')


def fig(inner, t, s=None):
    sub = f'<br><span style="font-size: 11px; color: {MUTED}">{s}</span>' if s else ""
    return (f'<figure style="margin: 0; display: flex; flex-direction: column; align-items: center; gap: 3px">{inner}'
            f'<figcaption style="font-size: 12px; text-align: center; line-height: 1.3">{t}{sub}</figcaption></figure>')


def commands():
    grid = "".join(fig(plaque(f"e-cmd-{ic[0]}", ic, "normal", 66, seed=i + 1), f'{ic[1]}') for i, ic in enumerate(ICONS))
    states = [("normal", "기본"), ("hover", "호버"), ("pressed", "누름"), ("disabled", "비활성"), ("armed", "무장"), ("cooldown", "재사용")]
    srow = "".join(fig(plaque(f"e-st-{k}", ICONS[1], k, 66, seed=50 + i), n) for i, (k, n) in enumerate(states))
    inner = (f'<div style="display: grid; grid-template-columns: repeat(6, 66px); gap: 4px 12px">{grid}</div>'
             f'<div style="height: 1px; background: linear-gradient(90deg, transparent, #6E5A3A, transparent)"></div>'
             f'<div style="display: grid; grid-template-columns: repeat(6, 66px); gap: 12px">{srow}</div>')
    return card("명령 패", "조각 나무판 · 놋쇠 모서리 · 금박 문양 · 6상태", inner)


def markers():
    items = [(marker_svg("e-mk-move", "이동 지점 표식", 11, m_move), "이동 지점", "옥빛 물결 3F"),
             (marker_svg("e-mk-atk", "공격 대상 표식", 12, m_attack), "공격 대상", "붉은 낙관 조준"),
             (marker_svg("e-mk-rally", "집결 지점 표식", 13, m_rally), "집결 지점", "솟대 깃발"),
             (marker_svg("e-mk-queue", "예약 경로 표식", 14, m_queue), "예약 경로", "Shift · 숫자 인장"),
             (marker_svg("e-mk-drag", "드래그 선택 표식", 15, m_drag), "드래그 선택", "붓 모서리 상자"),
             (marker_svg("e-mk-nob", "건설 불가 표식", 16, m_nobuild), "건설 불가", "붉은 X · 문살 칸")]
    inner = '<div style="display: grid; grid-template-columns: repeat(3, 206px); gap: 4px 10px">' + "".join(
        fig(s, f'{t} <span style="color: {MUTED}; font-size: 11px">· {sub}</span>') for s, t, sub in items) + "</div>"
    return card("지면 표식", "2.5D 아이소 지면 · 2:1 타원", inner)


def resources():
    items = [(res_ore(), "귀화석", "주 자원"), (res_food(), "식량", "인구 한도"), (res_gas(), "산기", "보조 자원")]
    inner = '<div style="display: flex; flex-direction: column; gap: 12px; padding-top: 2px">' + "".join(
        f'<div style="display: flex; align-items: center; gap: 10px">{s}<div style="display: flex; flex-direction: column; gap: 2px">'
        f'<span style="font-family: {SERIF}; font-weight: 800; font-size: 16px">{t}</span>'
        f'<span style="font-size: 12px; color: {MUTED}">{sub}</span></div></div>'
        for s, t, sub in items) + "</div>"
    return card("자원", "", inner, "flex: 1; min-width: 0")


def fx_group(title, frames, labels):
    figs = "".join(fig(f, l) for f, l in zip(frames, labels))
    return (f'<div style="display: flex; flex-direction: column; gap: 5px">'
            f'<span style="font-family: {SERIF}; font-weight: 700; font-size: 13px; color: {YEL}">{title}</span>'
            f'<div style="display: flex; gap: 6px">{figs}</div></div>')


def fx_rows():
    ex, exl = fx_explosion()
    ml, mll = fx_matchlock()
    hw, hwl = fx_hwacha()
    gb, gbl = fx_goblin()
    kt, ktl = fx_kite()
    st = [("idle", "대기"), ("selected", "선택"), ("moving", "이동"), ("attack", "공격"), ("hit", "피격"), ("dead", "사망")]
    us = [unit_state(f"e-us-{k}", k) for k, _ in st]
    row1 = (fx_group("흑색화약 폭발 · 5F", ex, exl) + fx_group("화승총 · 3F", ml, mll) + fx_group("화차 로켓 · 3F", hw, hwl))
    row2 = (fx_group("도깨비불 · 3F", gb, gbl) + fx_group("방패연 불화살 · 3F", kt, ktl)
            + f'<span style="width: 1px; align-self: stretch; background: {LINE}"></span>'
            + fx_group("유닛 상태 · 화승총수 (모든 유닛 공통 규칙)", us, [n for _, n in st]))
    return card("전투 효과 · 유닛 상태", "아이소 지면 위 프레임 · 왼쪽 위 광원",
                f'<div style="display: flex; gap: 20px">{row1}</div><div style="display: flex; gap: 20px; align-items: flex-end">{row2}</div>')


def alerts():
    items = [(seal_badge("e-al-atk", "急", "#C8432B", "#7A1E10", "e-sh-redGlow", 1), "본진 공격받음", "붉은 낙관 · 점멸"),
             (wooden_tag("e-al-done", "成", "#F2D58A", "#C8432B", 2), "생산 완료", "걸이 나무패"),
             (wooden_tag("e-al-res", "究", "#CFF5EA", "#3E8F7A", 3), "연구 완료", "걸이 나무패"),
             (seal_badge("e-al-dep", "竭", "#5A5448", "#2A2620", None, 4, cracked=True), "귀화석 고갈", "갈라진 회색 인장"),
             (seal_badge("e-al-rec", "連", "#3B4E8C", "#1A2448", "e-sh-oreGlow", 5), "재연결", "쪽빛 인장")]
    inner = '<div style="display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; align-items: center">' + "".join(
        f'<div style="display: flex; align-items: center; gap: 12px">{s}'
        f'<div style="display: flex; flex-direction: column; gap: 2px"><span style="font-family: {SERIF}; font-weight: 800; font-size: 15px">{t}</span>'
        f'<span style="font-size: 12px; color: {MUTED}">{sub}</span></div></div>' for s, t, sub in items) + "</div>"
    return card("알림 배지", "낙관 인장 · 매듭 달린 걸이 나무패", inner)


def page():
    hidden = (f'<svg width="0" height="0" style="position: absolute" aria-hidden="true">'
              f'<defs>{shared_defs()}{gunner_symbol()}</defs></svg>')
    header = (
        '<div style="display: flex; align-items: flex-end; justify-content: space-between">'
        '<div style="display: flex; flex-direction: column; gap: 4px">'
        f'<span style="font-family: {MONO}; font-size: 12px; letter-spacing: 0.14em; color: {MUTED}">ASSET SHEET E · 삼파전 SAMPAJEON · 2.5D ISO · LIGHT ↖</span>'
        f'<h1 style="margin: 0; font-family: {SERIF}; font-weight: 800; font-size: 34px; line-height: 1; color: #F2E2B8; '
        f'text-shadow: 0 2px 0 #000, 0 0 14px rgba(226,177,60,0.25)">효과 · 아이콘 · 유닛 상태</h1></div>'
        '<div style="display: flex; align-items: center; gap: 14px">'
        f'<span style="font-size: 13px; color: {MUTED}">옻칠 나무 · 놋쇠 · 금박 · 오방색 · 낙관</span>'
        f'<span style="display: flex; align-items: center; gap: 8px; font-family: {SERIF}; font-weight: 800; font-size: 22px; color: #F2E2B8">삼파전'
        f'<span style="width: 30px; height: 30px; background: {RED}; color: #FBE4D6; display: flex; align-items: center; justify-content: center; '
        f'font-size: 18px; border-radius: 3px; transform: rotate(-4deg); box-shadow: 0 2px 4px rgba(0,0,0,0.5)">參</span></span></div></div>')
    body = (f'{hidden}{header}'
            f'<div style="display: flex; gap: 12px">{commands()}{markers()}{resources()}</div>'
            f'{fx_rows()}{alerts()}')
    bg = ("background-color: #16140F; background-image: radial-gradient(ellipse at 20% 0%, rgba(226,177,60,0.06), transparent 60%), "
          "radial-gradient(ellipse at 100% 100%, rgba(200,67,43,0.05), transparent 55%)")
    return f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>효과 · 아이콘 시트</title>
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
<div style="width: 1440px; height: 960px; box-sizing: border-box; {bg}; color: {INK}; font-family: {SANS}; padding: 14px 30px; display: flex; flex-direction: column; gap: 9px; overflow: hidden; position: relative">
{close_tags(body)}
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


with open(OUT, "w", encoding="utf-8") as f:
    f.write(page())
