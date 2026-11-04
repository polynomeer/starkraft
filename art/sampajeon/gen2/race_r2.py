"""Generate RaceR2.dc.html — race codex R2: 요물 무리 Yomul Horde (삼파전 SAMPAJEON).

High-fidelity pass (SPEC_HQ.md): 2.5D isometric dioramas, painterly layered shading,
ornate lacquered-wood + brass chrome with hanji label panels.
All art is drawn here. Every SVG id is prefixed r2-; shared gradients/filters live in one
hidden defs block; per-instance ids come from uid(). Output is deterministic (seeded RNG).
Usage: python3 race_r2.py [<project dir>]   (default: ../design2/project)
"""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import yomul_figs as Y  # noqa: E402

HERE = Path(__file__).resolve().parent
PROJ = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "design2" / "project"
OUT = PROJ / "RaceR2.dc.html"

# ---------------------------------------------------------------- palette
BLUE, BLUE_L, BLUE_D, BLUE_D2 = "#4FB3E8", "#A8DDF5", "#12222E", "#1B3242"
OCHRE, INDIGO = "#C9A35A", "#2B2F55"
ORE, ORE_GLOW, GAS = "#9FE3D0", "#CFF5EA", "#B79AE0"
INK, MUTED, GROUND, PANEL, LINE = "#EFE6D2", "#A89E8A", "#16140F", "#201D17", "#4A4236"
RED, YEL, GRN, WHITE5 = "#C8432B", "#E2B13C", "#3E8F7A", "#EFE8D8"
OUTL = "#120D08"

rng = random.Random(7)
_uid = [0]


def uid(p):
    _uid[0] += 1
    return f"r2-{p}{_uid[0]}"


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".") if abs(v) >= 0.05 else "0"


def P(pt):
    return f"{f(pt[0])} {f(pt[1])}"


def attrs(a):
    out = []
    for k, v in a.items():
        if v is None:
            continue
        k = k.rstrip("_").replace("_", "-")
        out.append(f'{k}="{v}"')
    return " ".join(out)


def el(tag, inner="", **a):
    return f"<{tag} {attrs(a)}>{inner}</{tag}>"


def path(d, **a):
    return el("path", d=d, **a)


def poly(pts, **a):
    return el("polygon", points=" ".join(f"{f(x)},{f(y)}" for x, y in pts), **a)


def ell(cx, cy, rx, ry, **a):
    return el("ellipse", cx=f(cx), cy=f(cy), rx=f(rx), ry=f(ry), **a)


def circ(cx, cy, r, **a):
    return el("circle", cx=f(cx), cy=f(cy), r=f(r), **a)


OL = dict(stroke=OUTL, stroke_opacity="0.6", stroke_width="1", stroke_linejoin="round")


# ---------------------------------------------------------------- shared defs
def lg(id_, stops, x1=0, y1=0, x2=0, y2=1):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"{"" if op is None else f" stop-opacity=\"{op}\""}></stop>'
                for o, c, op in [(t + (None,))[:3] if len(t) == 2 else t for t in stops])
    return f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def rg(id_, stops, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"{"" if op is None else f" stop-opacity=\"{op}\""}></stop>'
                for o, c, op in [(t + (None,))[:3] if len(t) == 2 else t for t in stops])
    fxy = "" if fx is None else f' fx="{fx}" fy="{fy}"'
    return f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{r}"{fxy}>{s}</radialGradient>'


DEFS = "".join([
    '<filter id="r2-shadow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"></feGaussianBlur></filter>',
    '<filter id="r2-soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.2"></feGaussianBlur></filter>',
    '<filter id="r2-blur2" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="2"></feGaussianBlur></filter>',
    '<filter id="r2-blur5" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="5"></feGaussianBlur></filter>',
    '<filter id="r2-blur9" x="-120%" y="-120%" width="340%" height="340%"><feGaussianBlur stdDeviation="9"></feGaussianBlur></filter>',
    '<filter id="r2-glow" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur in="SourceGraphic" stdDeviation="1.6" result="b"></feGaussianBlur><feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>',
    '<filter id="r2-smoke" x="-40%" y="-40%" width="180%" height="180%"><feTurbulence type="fractalNoise" baseFrequency="0.07" numOctaves="3" seed="4" result="n"></feTurbulence><feDisplacementMap in="SourceGraphic" in2="n" scale="10" result="d"></feDisplacementMap><feGaussianBlur in="d" stdDeviation="1.4"></feGaussianBlur></filter>',
    '<filter id="r2-grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed="11"></feTurbulence><feColorMatrix values="0 0 0 0 0.08  0 0 0 0 0.06  0 0 0 0 0.03  0 0 0 0.7 0"></feColorMatrix><feComposite in2="SourceGraphic" operator="in"></feComposite></filter>',
    '<filter id="r2-mottle" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.045" numOctaves="3" seed="23"></feTurbulence><feColorMatrix values="0 0 0 0 0.35  0 0 0 0 0.3  0 0 0 0 0.12  0 0 0 0.9 -0.35"></feColorMatrix><feComposite in2="SourceGraphic" operator="in"></feComposite></filter>',
    '<filter id="r2-paper" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.65" numOctaves="3" seed="5"></feTurbulence><feColorMatrix values="0 0 0 0 0.35  0 0 0 0 0.25  0 0 0 0 0.12  0 0 0 0.22 0"></feColorMatrix><feComposite in2="SourceGraphic" operator="in"></feComposite></filter>',
    '<filter id="r2-woodgrain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency="0.01 0.35" numOctaves="2" seed="9"></feTurbulence><feColorMatrix values="0 0 0 0 0.05  0 0 0 0 0.03  0 0 0 0 0.01  0 0 0 0.55 0"></feColorMatrix><feComposite in2="SourceGraphic" operator="in"></feComposite></filter>',
    # skin / body
    rg("r2-skin", [(0, "#8E94C8"), (0.38, "#4E5494"), (0.78, "#2A2E62"), (1, "#141632")], cx=0.34, cy=0.28, r=0.85),
    lg("r2-skinL", [(0, "#7E84BE"), (0.45, "#3E4482"), (1, "#16183A")], 0, 0, 1, 1),
    lg("r2-shadeR", [(0, "#000", 0), (0.5, "#000", 0), (1, "#070820", 0.62)], 0, 0, 1, 0),
    lg("r2-shadeB", [(0, "#000", 0), (0.62, "#000", 0), (1, "#070820", 0.55)], 0, 0, 0, 1),
    lg("r2-hiT", [(0, "#DDE2FF", 0.32), (0.4, "#DDE2FF", 0), (1, "#DDE2FF", 0)], 0, 0, 0.6, 1),
    lg("r2-hair", [(0, "#4A5290"), (0.5, "#1C1F44"), (1, "#07081A")], 0, 0, 1, 1),
    lg("r2-horn", [(0, "#F6E2A8"), (0.45, OCHRE), (1, "#5E4420")], 0, 0, 1, 0),
    lg("r2-ivory", [(0, "#FFF8E4"), (1, "#B8A478")], 0, 0, 1, 0),
    rg("r2-iris", [(0, "#FFFFFF"), (0.35, BLUE_L), (1, "#1E78B8")], cx=0.4, cy=0.4, r=0.6),
    lg("r2-club", [(0, "#C48E58"), (0.35, "#8A5A32"), (0.8, "#4A2E16"), (1, "#26160A")]),
    rg("r2-stud", [(0, "#FFFFFF"), (0.3, "#D0CABE"), (1, "#2E2A24")], cx=0.35, cy=0.3, r=0.75),
    lg("r2-iron", [(0, "#8A8780"), (0.5, "#4A4842"), (1, "#1E1D1A")]),
    lg("r2-cloth-shade", [(0, "#FFF4D8", 0.22), (0.45, "#000", 0), (1, "#000", 0.6)], 0, 0, 0.7, 1),
    lg("r2-rope", [(0, "#E6CF92"), (0.5, "#A88A4C"), (1, "#5E4A24")]),
    rg("r2-bead-w", [(0, "#E8B87A"), (0.5, "#8A5426"), (1, "#3A200C")], cx=0.35, cy=0.3, r=0.75),
    rg("r2-bead-b", [(0, "#FFFFFF"), (0.5, "#D8CDB0"), (1, "#6A5E44")], cx=0.35, cy=0.3, r=0.75),
    '<pattern id="r2-tiger" width="12" height="12" patternUnits="userSpaceOnUse" patternTransform="rotate(14)"><rect width="12" height="12" fill="#D98A2B"></rect><rect width="12" height="5" fill="#E8A040" opacity="0.6"></rect><path d="M0 2 Q3 3.6 6 1.6 Q8.6 0.4 12 2.2 L12 4 Q8.6 2.6 6 3.8 Q3 5.2 0 3.8 Z" fill="#1A1208"></path><path d="M0 8 Q2.6 9.4 5 7.8 L4.6 9.4 Q2.2 10.4 0 9.6 Z" fill="#1A1208"></path><path d="M7 8.2 Q9.4 7.2 12 8.6 L12 9.8 Q9.4 8.8 7.4 9.8 Z" fill="#1A1208"></path></pattern>',
    # fire
    rg("r2-flame", [(0, "#FFFFFF"), (0.25, "#D8F2FF"), (0.55, BLUE), (1, "#16588E")], cx=0.5, cy=0.75, r=0.7),
    rg("r2-halo", [(0, BLUE_L, 0.8), (0.4, BLUE, 0.32), (1, BLUE, 0)]),
    rg("r2-pool", [(0, "#BFE8FF", 0.55), (0.5, BLUE, 0.22), (1, BLUE, 0)]),
    # ground
    rg("r2-earth", [(0, "#8A7448"), (0.55, "#6A5634"), (1, "#3E3220")], cx=0.38, cy=0.3, r=0.85),
    rg("r2-earth-dark", [(0, "#4A3E2A"), (1, "#221C12")], cx=0.38, cy=0.3, r=0.85),
    lg("r2-soilL", [(0, "#5E4A30"), (0.35, "#4A3A24"), (1, "#2A2014")]),
    lg("r2-soilR", [(0, "#3A2E1E"), (1, "#16120C")]),
    rg("r2-pebble", [(0, "#C8C2B4"), (0.55, "#7E786C"), (1, "#3A3630")], cx=0.35, cy=0.3, r=0.8),
    # stone / wood / roof
    lg("r2-stoneL", [(0, "#B0AA9C"), (0.5, "#7C776C"), (1, "#4A4640")], 0, 0, 1, 1),
    lg("r2-stoneR", [(0, "#6A665C"), (1, "#2C2A26")], 0, 0, 1, 1),
    lg("r2-stoneT", [(0, "#C8C2B2"), (1, "#8A8478")], 0, 0, 1, 1),
    lg("r2-plasterL", [(0, "#EDE2C8"), (1, "#BFAE8A")], 0, 0, 1, 1),
    lg("r2-plasterR", [(0, "#8E8068"), (1, "#5A4E3C")], 0, 0, 1, 1),
    lg("r2-timberL", [(0, "#7A4E2E"), (1, "#4A2C18")], 0, 0, 1, 0),
    lg("r2-timberR", [(0, "#3E2414"), (1, "#22140A")], 0, 0, 1, 0),
    lg("r2-redpost", [(0, "#D8603A"), (0.5, "#A83A22"), (1, "#5A1A10")], 0, 0, 1, 0),
    lg("r2-roofF", [(0, "#2E3038"), (0.55, "#4E525C"), (1, "#6E737E")], 0, 1, 0, 0),
    lg("r2-roofB", [(0, "#1E1F25"), (1, "#3A3D45")], 0, 1, 0, 0),
    lg("r2-thatchF", [(0, "#5E4A28"), (0.4, "#A88C56"), (1, "#D8BE86")], 0, 1, 0, 0),
    lg("r2-thatchB", [(0, "#3E3018"), (1, "#7A6438")], 0, 1, 0, 0),
    lg("r2-bark", [(0, "#9A7A56"), (0.3, "#6A4E36"), (0.7, "#3A2A1C"), (1, "#1A120C")], 0, 0, 1, 0),
    rg("r2-hollow", [(0, "#F2FBFF"), (0.25, BLUE_L), (0.6, BLUE), (1, "#06101A")], cx=0.5, cy=0.65, r=0.62),
    rg("r2-jar", [(0, "#B47A4A"), (0.3, "#6A3A20"), (0.75, "#2E160A"), (1, "#140804")], cx=0.32, cy=0.32, r=0.85),
    rg("r2-sand", [(0, "#EAD8A6"), (0.6, "#C4AE7A"), (1, "#8A7650")], cx=0.4, cy=0.35, r=0.75),
    lg("r2-brass", [(0, "#F6DE9A"), (0.4, "#C9A35A"), (1, "#6A4A1E")], 0, 0, 1, 1),
    lg("r2-leafA", [(0, "#F0A048"), (1, "#9A3A1A")], 0, 0, 1, 1),
    lg("r2-leafB", [(0, "#E8C060"), (1, "#8A5A1E")], 0, 0, 1, 1),
    lg("r2-ore-l", [(0, "#F4FFFC"), (1, ORE)], 0, 0, 1, 1),
    lg("r2-ore-d", [(0, "#5FB8A6"), (1, "#1A4E46")], 0, 0, 1, 1),
    rg("r2-ore-glow", [(0, ORE_GLOW, 0.75), (1, ORE, 0)]),
    rg("r2-mist", [(0, GAS, 0.7), (1, GAS, 0)]),
    rg("r2-smokeg", [(0, "#B4B4C2"), (0.6, "#626476"), (1, "#2A2B36", 0)], cx=0.42, cy=0.38, r=0.6),
    lg("r2-ghost", [(0, "#9AA4F0", 0.9), (0.55, "#4E58A8", 0.6), (1, INDIGO, 0)]),
    lg("r2-ghostL", [(0, "#A8B0F4", 0.85), (1, "#3E4690", 0.55)], 0, 0, 1, 1),
    rg("r2-scorch", [(0, "#050608", 0.92), (0.65, "#14181C", 0.55), (1, "#14181C", 0)]),
    rg("r2-vign", [(0, "#000", 0), (0.7, "#000", 0.15), (1, "#000", 0.6)]),
    rg("r2-ember", [(0, "#FFE7B0"), (0.5, "#F08A3A"), (1, "#8A2A10", 0)]),
])

# ---------------------------------------------------------------- generic painted primitives
LIGHT = (-0.6, -0.8)


def shadow(cx, cy, rx, ry, op=0.5):
    return ell(cx, cy, rx, ry, fill="#000", opacity=op, filter="url(#r2-shadow)")


def _ctrl(a, c, b):
    return (2 * c[0] - (a[0] + b[0]) / 2, 2 * c[1] - (a[1] + b[1]) / 2)


def limb(p1, p2, w1, w2, bulge=1.15, grad="r2-skinL", rim=BLUE_L, shade="#080A24", hi="#C8CEFA", joint=True, op=None):
    """Tapered, muscled limb segment with lit side, shadow band, highlight and fire rim light."""
    x1, y1 = p1
    x2, y2 = p2
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    if nx * LIGHT[0] + ny * LIGHT[1] < 0:
        nx, ny = -nx, -ny
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    wm = max(w1, w2) * bulge

    def side(k, s):
        a = (x1 + nx * w1 * k * s, y1 + ny * w1 * k * s)
        b = (x2 + nx * w2 * k * s, y2 + ny * w2 * k * s)
        c = (mx + nx * wm * k * s, my + ny * wm * k * s)
        return a, c, b

    la, lc, lb = side(1, 1)
    sa, sc, sb = side(1, -1)
    out = []
    if joint:
        out.append(circ(x1, y1, w1 * 0.96, fill=f"url(#{grad})"))
    out.append(path(f"M{P(la)} Q{P(_ctrl(la, lc, lb))} {P(lb)} L{P(sb)} Q{P(_ctrl(sa, sc, sb))} {P(sa)} Z",
                    fill=f"url(#{grad})", **OL))
    ia, ic, ib = side(0.1, -1)
    out.append(path(f"M{P(sa)} Q{P(_ctrl(sa, sc, sb))} {P(sb)} L{P(ib)} Q{P(_ctrl(ia, ic, ib))} {P(ia)} Z", fill=shade, opacity="0.42"))
    ha, hc, hb = side(0.55, 1)
    out.append(path(f"M{P(ha)} Q{P(_ctrl(ha, hc, hb))} {P(hb)}", fill="none", stroke=hi, stroke_width=f(max(0.8, w1 * 0.3)),
                    opacity="0.38", stroke_linecap="round"))
    ra, rc, rb = side(0.88, -1)
    out.append(path(f"M{P(ra)} Q{P(_ctrl(ra, rc, rb))} {P(rb)}", fill="none", stroke=rim, stroke_width="1.2", opacity="0.6",
                    stroke_linecap="round"))
    # muscle split line
    ma, mc, mb = side(0.25, 1)
    out.append(path(f"M{P(((ma[0] * 3 + mb[0]) / 4, (ma[1] * 3 + mb[1]) / 4))} Q{P(mc)} {P(((ma[0] + mb[0] * 3) / 4, (ma[1] + mb[1] * 3) / 4))}",
                    fill="none", stroke=shade, stroke_width="0.8", opacity="0.35"))
    g = "".join(out)
    return f'<g opacity="{op}">{g}</g>' if op else g


def clip_shade(d, bbox, grads=("r2-shadeR", "r2-shadeB", "r2-hiT")):
    """Paint gradient overlays clipped to a shape path (directional key light + falloff)."""
    cid = uid("cl")
    x, y, w, h = bbox
    rects = "".join(el("rect", x=f(x), y=f(y), width=f(w), height=f(h), fill=f"url(#{g})") for g in grads)
    return f'<clipPath id="{cid}"><path d="{d}"></path></clipPath><g clip-path="url(#{cid})">{rects}</g>'


def rim_stroke(d, x0, y0, w, h, color=BLUE_L, width=2.4, op=0.55):
    cid = uid("rim")
    return (f'<clipPath id="{cid}"><rect x="{f(x0)}" y="{f(y0)}" width="{f(w)}" height="{f(h)}"></rect></clipPath>'
            + path(d, fill="none", stroke=color, stroke_width=f(width), opacity=f"{op}", clip_path=f"url(#{cid})"))


def torso(cx, sy, sw, wy, ww, belly=0.0, grad="r2-skin", rim=True):
    nw = sw * 0.34
    h = wy - sy
    d = (f"M{f(cx - nw)} {f(sy - 8)} C{f(cx - nw - sw * 0.35)} {f(sy - 3)} {f(cx - sw)} {f(sy - 5)} {f(cx - sw)} {f(sy + h * 0.14)} "
         f"C{f(cx - sw - 4)} {f(sy + h * 0.45)} {f(cx - ww - belly - 3)} {f(sy + h * 0.72)} {f(cx - ww)} {f(wy)} "
         f"L{f(cx + ww)} {f(wy)} C{f(cx + ww + belly + 3)} {f(sy + h * 0.72)} {f(cx + sw + 4)} {f(sy + h * 0.45)} {f(cx + sw)} {f(sy + h * 0.14)} "
         f"C{f(cx + sw)} {f(sy - 5)} {f(cx + nw + sw * 0.35)} {f(sy - 3)} {f(cx + nw)} {f(sy - 8)} Z")
    out = [path(d, fill=f"url(#{grad})", **OL)]
    bb = (cx - sw - 8, sy - 10, 2 * sw + 16, h + 14)
    out.append(clip_shade(d, bb))
    # pecs
    py = sy + h * 0.3
    for s in (-1, 1):
        out.append(path(f"M{f(cx + s * sw * 0.86)} {f(sy + h * 0.12)} Q{f(cx + s * sw * 0.7)} {f(py + h * 0.14)} {f(cx + s * 2)} {f(py + h * 0.05)}",
                        fill="none", stroke="#0A0C2A", stroke_width="1.8", opacity="0.5", stroke_linecap="round"))
        out.append(path(f"M{f(cx + s * sw * 0.7)} {f(sy + h * 0.06)} Q{f(cx + s * sw * 0.4)} {f(py - h * 0.04)} {f(cx + s * 4)} {f(py - h * 0.02)}",
                        fill="none", stroke="#D8DEFF", stroke_width="1.4", opacity=("0.32" if s < 0 else "0.14"), stroke_linecap="round"))
        # nipples
        out.append(circ(cx + s * sw * 0.45, py + h * 0.04, 1.1, fill="#1A1C44", opacity="0.7"))
    # sternum + abs
    out.append(path(f"M{f(cx)} {f(sy + h * 0.06)} L{f(cx)} {f(wy - h * 0.12)}", stroke="#0A0C2A", stroke_width="1.2", opacity="0.4"))
    for k in range(3):
        yy = py + h * (0.18 + k * 0.15)
        for s in (-1, 1):
            out.append(path(f"M{f(cx + s * 2)} {f(yy)} Q{f(cx + s * ww * 0.45)} {f(yy + 2.5)} {f(cx + s * ww * 0.8)} {f(yy - 1)}",
                            fill="none", stroke="#0A0C2A", stroke_width="1", opacity="0.32"))
    if belly > 0:
        out.append(ell(cx - ww * 0.25, wy - h * 0.28, ww * 0.55, h * 0.18, fill="#C8D0FF", opacity="0.12"))
    out.append(ell(cx + 0.5, wy - h * 0.16, 1.4, 1.8, fill="#0A0C2A", opacity="0.6"))
    if rim:
        out.append(rim_stroke(d, cx + sw * 0.35, sy - 10, sw + 10, h + 14))
    return "".join(out)


def horn(bx, by, tx, ty, w, bend):
    dx, dy = tx - bx, ty - by
    ln = math.hypot(dx, dy) or 1
    px, py = -dy / ln, dx / ln
    mx, my = bx + dx * 0.5 + px * bend, by + dy * 0.5 + py * bend
    l1 = (bx - px * w / 2, by - py * w / 2)
    l2 = (bx + px * w / 2, by + py * w / 2)
    d = (f"M{P(l1)} Q{f(mx - px * w * 0.3)} {f(my - py * w * 0.3)} {f(tx)} {f(ty)} "
         f"Q{f(mx + px * w * 0.45)} {f(my + py * w * 0.45)} {P(l2)} Z")
    out = [path(d, fill="url(#r2-horn)", stroke="#2A1E0C", stroke_width="0.8")]
    for i in range(6):
        k = 0.1 + i * 0.12
        cx = bx + dx * k + px * bend * (4 * k * (1 - k))
        cy = by + dy * k + py * bend * (4 * k * (1 - k))
        hw = w / 2 * (1 - k * 0.95)
        out.append(path(f"M{f(cx - px * hw)} {f(cy - py * hw)} Q{f(cx + dx / ln * 1.4)} {f(cy + dy / ln * 1.4)} {f(cx + px * hw)} {f(cy + py * hw)}",
                        fill="none", stroke="#4A3416", stroke_width="0.8", opacity="0.75"))
        out.append(path(f"M{f(cx - px * hw * 0.8 - dx / ln)} {f(cy - py * hw * 0.8 - dy / ln)} L{f(cx - px * hw * 0.2 - dx / ln)} {f(cy - py * hw * 0.2 - dy / ln)}",
                        stroke="#FFF2CC", stroke_width="0.6", opacity="0.6"))
    out.append(path(f"M{f(l1[0] + dx * 0.06)} {f(l1[1] + dy * 0.06)} Q{f(mx - px * w * 0.18)} {f(my - py * w * 0.18)} {f(tx - dx * 0.06)} {f(ty - dy * 0.06)}",
                    stroke="#FFF6DA", stroke_width="0.9", fill="none", opacity="0.7"))
    out.append(circ(tx - dx * 0.04, ty - dy * 0.04, 0.8, fill="#FFFFFF", opacity="0.8"))
    return "".join(out)


def hair_strands(cx, cy, r, n, a0, a1, lmin, lmax, wmul, seed, up=0.55, fill="url(#r2-hair)"):
    rr = random.Random(seed)
    out = []
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * (i + rr.random() * 0.8) / n)
        bx, by = cx + math.cos(a) * r * 0.9, cy + math.sin(a) * r * 0.82
        dxn, dyn = math.cos(a) * (1 - up), math.sin(a) * (1 - up) - up
        dl = math.hypot(dxn, dyn)
        dxn, dyn = dxn / dl, dyn / dl
        ln = r * (lmin + rr.random() * (lmax - lmin))
        px, py = -dyn, dxn
        w = r * wmul * (0.7 + rr.random() * 0.6)
        wave = (rr.random() - 0.5) * ln * 0.6
        tip = (bx + dxn * ln + px * wave * 0.4, by + dyn * ln + py * wave * 0.4)
        c = (bx + dxn * ln * 0.55 + px * wave, by + dyn * ln * 0.55 + py * wave)
        d = f"M{f(bx - px * w)} {f(by - py * w)} Q{P(c)} {P(tip)} Q{f(c[0] + px * w * 0.5)} {f(c[1] + py * w * 0.5)} {f(bx + px * w)} {f(by + py * w)} Z"
        out.append(path(d, fill=fill, stroke="#05050F", stroke_width="0.5", stroke_opacity="0.7"))
        if rr.random() < 0.6:
            out.append(path(f"M{f(bx)} {f(by)} Q{P(c)} {f((tip[0] + c[0]) / 2)} {f((tip[1] + c[1]) / 2)}", fill="none",
                            stroke="#7C86C8", stroke_width="0.6", opacity="0.55"))
    return "".join(out)


def head(cx, cy, r, horns=(), grin=1.0, tusks=True, beard=False, seed=1, brow_angry=0.0, ghost=False, skin_fill="url(#r2-skin)"):
    out = []
    # back hair mane
    out.append(hair_strands(cx, cy - r * 0.1, r * 1.05, 26, 160, 380, 0.45, 0.95, 0.13, seed))
    if beard:
        out.append(hair_strands(cx, cy + r * 0.55, r * 0.75, 18, 20, 160, 0.4, 0.9, 0.12, seed + 7, up=-0.5))
    # ears
    for s in (-1, 1):
        ed = (f"M{f(cx + s * r * 0.86)} {f(cy - r * 0.18)} Q{f(cx + s * r * 1.32)} {f(cy - r * 0.62)} {f(cx + s * r * 1.48)} {f(cy - r * 0.5)} "
              f"Q{f(cx + s * r * 1.3)} {f(cy + r * 0.05)} {f(cx + s * r * 0.92)} {f(cy + r * 0.25)} Z")
        out.append(path(ed, fill=(skin_fill if s < 0 else "#2C3270"), **OL))
        out.append(path(f"M{f(cx + s * r * 0.98)} {f(cy - r * 0.08)} Q{f(cx + s * r * 1.25)} {f(cy - r * 0.38)} {f(cx + s * r * 1.34)} {f(cy - r * 0.42)}",
                        fill="none", stroke="#0A0C2A", stroke_width="1", opacity="0.6"))
        # earring
        out.append(circ(cx + s * r * 1.02, cy + r * 0.3, r * 0.1, fill="none", stroke="url(#r2-brass)", stroke_width=f(r * 0.06)))
    skull = (f"M{f(cx - r * 0.95)} {f(cy - r * 0.1)} C{f(cx - r * 1.02)} {f(cy - r * 1.18)} {f(cx + r * 1.02)} {f(cy - r * 1.18)} {f(cx + r * 0.95)} {f(cy - r * 0.1)} "
             f"C{f(cx + r * 1.08)} {f(cy + r * 0.55)} {f(cx + r * 0.62)} {f(cy + r * 1.02)} {f(cx)} {f(cy + r * 1.04)} "
             f"C{f(cx - r * 0.62)} {f(cy + r * 1.02)} {f(cx - r * 1.08)} {f(cy + r * 0.55)} {f(cx - r * 0.95)} {f(cy - r * 0.1)} Z")
    out.append(path(skull, fill=skin_fill, **OL))
    if skin_fill == "url(#r2-skin)":
        out.append(clip_shade(skull, (cx - r * 1.2, cy - r * 1.2, r * 2.4, r * 2.3)))
    # cheekbones / jaw planes
    for s in (-1, 1):
        out.append(path(f"M{f(cx + s * r * 0.85)} {f(cy + r * 0.15)} Q{f(cx + s * r * 0.7)} {f(cy + r * 0.62)} {f(cx + s * r * 0.3)} {f(cy + r * 0.9)}",
                        fill="none", stroke="#0A0C2A", stroke_width="1.2", opacity="0.35"))
    out.append(ell(cx - r * 0.55, cy + r * 0.18, r * 0.2, r * 0.12, fill="#B8C4FF", opacity="0.3"))
    # brow ridge
    ba = brow_angry
    brow = (f"M{f(cx - r * 0.82)} {f(cy - r * (0.2 + ba * 0.1))} Q{f(cx - r * 0.42)} {f(cy - r * 0.5)} {f(cx)} {f(cy - r * (0.24 - ba * 0.12))} "
            f"Q{f(cx + r * 0.42)} {f(cy - r * 0.5)} {f(cx + r * 0.82)} {f(cy - r * (0.2 + ba * 0.1))} "
            f"L{f(cx + r * 0.74)} {f(cy - r * 0.04)} Q{f(cx + r * 0.38)} {f(cy - r * 0.26)} {f(cx)} {f(cy - r * (0.06 - ba * 0.1))} "
            f"Q{f(cx - r * 0.38)} {f(cy - r * 0.26)} {f(cx - r * 0.74)} {f(cy - r * 0.04)} Z")
    out.append(path(brow, fill="#2A3068", **OL))
    out.append(path(f"M{f(cx - r * 0.8)} {f(cy - r * 0.22)} Q{f(cx - r * 0.42)} {f(cy - r * 0.5)} {f(cx - r * 0.05)} {f(cy - r * 0.26)}",
                    fill="none", stroke="#B8C0FF", stroke_width=f(r * 0.06), opacity="0.45"))
    # eyes
    for s in (-1, 1):
        ex, ey = cx + s * r * 0.38, cy - r * 0.02
        out.append(circ(ex, ey, r * 0.28, fill=BLUE, opacity="0.35", filter="url(#r2-blur2)"))
        out.append(ell(ex, ey, r * 0.2, r * 0.15, fill="#F4EAC8", stroke="#0A0610", stroke_width="0.8"))
        out.append(circ(ex + s * r * 0.02, ey + r * 0.01, r * 0.115, fill="url(#r2-iris)"))
        out.append(circ(ex + s * r * 0.02, ey + r * 0.01, r * 0.05, fill="#040612"))
        out.append(circ(ex - r * 0.05, ey - r * 0.05, r * 0.035, fill="#FFFFFF"))
        out.append(path(f"M{f(ex - r * 0.22)} {f(ey - r * 0.04)} Q{f(ex)} {f(ey - r * 0.22)} {f(ex + r * 0.22)} {f(ey - r * 0.04)}",
                        fill="none", stroke="#05060F", stroke_width=f(r * 0.07), stroke_linecap="round"))
        out.append(path(f"M{f(ex - r * 0.16)} {f(ey + r * 0.16)} Q{f(ex)} {f(ey + r * 0.22)} {f(ex + r * 0.16)} {f(ey + r * 0.16)}",
                        fill="none", stroke="#0A0C2A", stroke_width="0.8", opacity="0.6"))
    # nose
    nd = (f"M{f(cx - r * 0.22)} {f(cy + r * 0.32)} Q{f(cx - r * 0.26)} {f(cy + r * 0.12)} {f(cx - r * 0.08)} {f(cy + r * 0.02)} "
          f"L{f(cx + r * 0.08)} {f(cy + r * 0.02)} Q{f(cx + r * 0.26)} {f(cy + r * 0.12)} {f(cx + r * 0.22)} {f(cy + r * 0.32)} "
          f"Q{f(cx)} {f(cy + r * 0.44)} {f(cx - r * 0.22)} {f(cy + r * 0.32)} Z")
    out.append(path(nd, fill="#5A64B4", **OL))
    for s in (-1, 1):
        out.append(ell(cx + s * r * 0.1, cy + r * 0.3, r * 0.055, r * 0.035, fill="#0A0C24"))
    out.append(ell(cx - r * 0.06, cy + r * 0.15, r * 0.07, r * 0.045, fill="#D8DEFF", opacity="0.6"))
    # mouth / grin
    w = r * 0.62 * grin
    my = cy + r * 0.5
    md = (f"M{f(cx - w)} {f(my - r * 0.06)} Q{f(cx)} {f(my + r * 0.55)} {f(cx + w)} {f(my - r * 0.06)} "
          f"Q{f(cx)} {f(my + r * 0.12)} {f(cx - w)} {f(my - r * 0.06)} Z")
    out.append(path(md, fill="#2A0810", stroke="#08020A", stroke_width="0.8"))
    out.append(ell(cx, my + r * 0.3, w * 0.45, r * 0.08, fill="#8A2430", opacity="0.85"))
    n = 8
    for i in range(n):
        t = (i + 0.5) / n
        x = cx - w * 0.9 + 2 * w * 0.9 * t
        yb = my - r * 0.06 + r * 0.17 * (1 - (2 * t - 1) ** 2)
        out.append(path(f"M{f(x - w / n * 0.85)} {f(yb - r * 0.03)} L{f(x + w / n * 0.85)} {f(yb - r * 0.03)} L{f(x + w / n * 0.5)} {f(yb + r * 0.1)} L{f(x - w / n * 0.5)} {f(yb + r * 0.1)} Z",
                        fill="#F2EAD2", stroke="#3A2A1A", stroke_width="0.4"))
    out.append(path(f"M{f(cx - w * 1.05)} {f(my - r * 0.12)} Q{f(cx)} {f(my + r * 0.05)} {f(cx + w * 1.05)} {f(my - r * 0.12)}",
                    fill="none", stroke="#0A0C2A", stroke_width="1", opacity="0.5"))
    if tusks:
        for s in (-1, 1):
            bx, by = cx + s * w * 0.7, my + r * 0.2
            out.append(path(f"M{f(bx - r * 0.08)} {f(by)} Q{f(bx + s * r * 0.0)} {f(by - r * 0.36)} {f(bx + s * r * 0.15)} {f(by - r * 0.5)} "
                            f"Q{f(bx + s * r * 0.14)} {f(by - r * 0.16)} {f(bx + r * 0.08)} {f(by)} Z", fill="url(#r2-ivory)", stroke="#4A3A22", stroke_width="0.6"))
    # front fringe tufts
    out.append(hair_strands(cx, cy - r * 0.55, r * 0.7, 9, 200, 340, 0.35, 0.6, 0.16, seed + 3, up=-0.15))
    # horns on top
    for (bx, by, tx, ty, w_, bend) in horns:
        out.append(horn(cx + bx * r, cy + by * r, cx + tx * r, cy + ty * r, w_ * r, bend * r))
    out.append(rim_stroke(skull, cx + r * 0.3, cy - r * 1.2, r * 1.2, r * 2.4, width=2.0))
    g = "".join(out)
    return f'<g opacity="0.72">{g}</g>' if ghost else g


def fist(x, y, r, grad="r2-skinL"):
    out = [circ(x, y, r, fill=f"url(#{grad})", **OL)]
    for k in range(3):
        a = math.radians(-60 + k * 40)
        out.append(path(f"M{f(x + math.cos(a) * r * 0.25)} {f(y + math.sin(a) * r * 0.25)} L{f(x + math.cos(a) * r * 0.9)} {f(y + math.sin(a) * r * 0.9)}",
                        stroke="#0A0C2A", stroke_width="0.8", opacity="0.55"))
    out.append(circ(x - r * 0.35, y - r * 0.35, r * 0.3, fill="#DDE2FF", opacity="0.3"))
    return "".join(out)


def foot(x, y, s, flip=1):
    out = [ell(x, y, 9 * s, 4.4 * s, fill="url(#r2-skinL)", **OL)]
    for k in (-1, 0, 1):
        tx, ty = x + flip * (5 + abs(k)) * s + k * 1.8 * s, y + 2 * s + k * 1.4 * s
        out.append(circ(tx, ty, 2 * s, fill="url(#r2-skinL)", **OL))
        out.append(path(f"M{f(tx + flip * 1.5 * s)} {f(ty)} l{f(flip * 2.2 * s)} {f(0.6 * s)} l{f(-flip * 2 * s)} {f(0.9 * s)} Z", fill="url(#r2-ivory)"))
    out.append(ell(x - 2 * s, y - 1.4 * s, 4 * s, 1.4 * s, fill="#DDE2FF", opacity="0.25"))
    return "".join(out)


def club(x, y, ang, ln, w1, w2, seed=1, studs=True):
    rr = random.Random(seed)
    d = (f"M0 {f(-w1)} L{f(ln * 0.3)} {f(-w1)} C{f(ln * 0.5)} {f(-w1)} {f(ln * 0.6)} {f(-w2)} {f(ln * 0.82)} {f(-w2)} "
         f"Q{f(ln * 1.03)} {f(-w2)} {f(ln * 1.03)} 0 Q{f(ln * 1.03)} {f(w2)} {f(ln * 0.82)} {f(w2)} "
         f"C{f(ln * 0.6)} {f(w2)} {f(ln * 0.5)} {f(w1)} {f(ln * 0.3)} {f(w1)} L0 {f(w1)} Q{f(-w1)} {f(w1)} {f(-w1)} 0 Q{f(-w1)} {f(-w1)} 0 {f(-w1)} Z")
    o = [f'<g transform="translate({f(x)} {f(y)}) rotate({f(ang)})">', path(d, fill="url(#r2-club)", **OL)]
    for i in range(7):
        yy = (rr.random() - 0.5) * w2 * 1.3
        o.append(path(f"M{f(ln * 0.32)} {f(yy * 0.45)} C{f(ln * 0.55)} {f(yy * 0.8 + 1)} {f(ln * 0.75)} {f(yy - 1)} {f(ln * 0.98)} {f(yy * 0.9)}",
                      fill="none", stroke=("#2A180A" if i % 2 else "#D8A470"), stroke_width="0.6", opacity="0.55"))
    o.append(ell(ln * 0.7, -w2 * 0.55, ln * 0.22, w2 * 0.18, fill="#F8D8A8", opacity="0.25"))
    # leather wrap on grip
    for k in range(8):
        xx = ln * 0.02 + k * ln * 0.028
        o.append(path(f"M{f(xx)} {f(-w1 - 0.3)} L{f(xx + ln * 0.022)} {f(w1 + 0.3)}", stroke="#2A1608", stroke_width=f(ln * 0.02), opacity="0.85"))
        o.append(path(f"M{f(xx + 0.4)} {f(-w1)} L{f(xx + ln * 0.012)} {f(0)}", stroke="#8A6040", stroke_width="0.5"))
    # iron bands
    for bx in (0.31, 0.36):
        o.append(el("rect", x=f(ln * bx), y=f(-w1 - 1.2), width=f(ln * 0.03), height=f(2 * w1 + 2.4), fill="url(#r2-iron)", stroke="#0A0806", stroke_width="0.5"))
    if studs:
        for i in range(8):
            t = 0.46 + 0.5 * i / 7
            xx = ln * t
            hw = w1 + (w2 - w1) * min(1, (t - 0.3) / 0.5)
            for yy in (-hw * 0.62, 0, hw * 0.62)[(i % 2):: 1 if i % 2 == 0 else 2]:
                o.append(circ(xx + 0.6, yy + 0.8, 1.9, fill="#000", opacity="0.4"))
                o.append(circ(xx, yy, 1.8, fill="url(#r2-stud)", stroke="#1A1612", stroke_width="0.4"))
    o.append("</g>")
    return "".join(o)


def tiger_cloth(cx, wy, ww, drop, seed=2):
    rr = random.Random(seed)
    top_l, top_r = cx - ww - 4, cx + ww + 4
    pts = []
    n = 11
    for i in range(n + 1):
        t = i / n
        x = top_r + 3 - (top_r - top_l + 6) * t
        y = wy + drop + (rr.random() * 5 if i % 2 else -2) + 4 * math.sin(t * math.pi)
        pts.append((x, y))
    d = f"M{f(top_l)} {f(wy - 4)} Q{f(cx)} {f(wy + 2)} {f(top_r)} {f(wy - 4)} L{f(top_r + 3)} {f(wy + drop * 0.6)} " + " ".join(f"L{P(p)}" for p in pts) + f" L{f(top_l - 3)} {f(wy + drop * 0.6)} Z"
    out = [path(d, fill="url(#r2-tiger)", **OL), path(d, fill="url(#r2-cloth-shade)")]
    # folds
    for k in range(4):
        x = top_l + (top_r - top_l) * (0.2 + k * 0.2)
        out.append(path(f"M{f(x)} {f(wy)} Q{f(x + 2)} {f(wy + drop * 0.5)} {f(x - 1)} {f(wy + drop * 0.9)}", fill="none", stroke="#2A1608", stroke_width="1", opacity="0.4"))
    # fur fringe
    for (x, y) in pts:
        for j in range(3):
            out.append(path(f"M{f(x + j - 1)} {f(y - 2)} l{f(rr.random() * 2 - 1)} {f(3 + rr.random() * 2)}", stroke="#F2E2BC", stroke_width="0.7", opacity="0.85"))
    # straw rope belt
    out.append(path(f"M{f(top_l - 1)} {f(wy - 4)} Q{f(cx)} {f(wy + 3)} {f(top_r + 1)} {f(wy - 4)}", fill="none", stroke="url(#r2-rope)", stroke_width="4.5", stroke_linecap="round"))
    out.append(path(f"M{f(top_l - 1)} {f(wy - 4)} Q{f(cx)} {f(wy + 3)} {f(top_r + 1)} {f(wy - 4)}", fill="none", stroke="#5A441E", stroke_width="4.5", stroke_dasharray="1.6 2.2", opacity="0.6"))
    out.append(path(f"M{f(cx + ww * 0.4)} {f(wy - 1)} l3 9 m-3 -9 l-2 10", stroke="#A88A4C", stroke_width="2", stroke_linecap="round"))
    return "".join(out)


def beads(cx, cy, rx, ry, n, r=2.6, a0=20, a1=160):
    out = []
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * i / (n - 1))
        x, y = cx + math.cos(a) * rx, cy + math.sin(a) * ry
        out.append(circ(x + 0.5, y + 0.8, r, fill="#000", opacity="0.35"))
        out.append(circ(x, y, r * (1.15 if i == n // 2 else 1), fill=("url(#r2-bead-b)" if i % 3 == 0 else "url(#r2-bead-w)"), stroke="#1A0E06", stroke_width="0.4"))
    return "".join(out)


FLAME_D = "M0 -22 C 5 -14 13 -7 11 3 C 10 10 5 14 0 14 C -5 14 -10 10 -11 3 C -12 -5 -4 -10 -3 -15 C -1 -12 0 -16 0 -22 Z"


def flame(cx, cy, s=1.0, rot=0.0, halo=True, op=1.0, seed=1):
    rr = random.Random(seed)
    t = f"translate({f(cx)} {f(cy)}) rotate({f(rot)})"
    o = [f'<g transform="{t}" opacity="{op}">']
    if halo:
        o.append(circ(0, -3 * s, 28 * s, fill="url(#r2-halo)"))
    o.append(path(FLAME_D, transform=f"scale({f(s * 1.2)})", fill="#1E7EC0", opacity="0.6", filter="url(#r2-blur2)"))
    for k, a in ((-1, -28), (1, 24)):
        o.append(path(FLAME_D, transform=f"translate({f(k * 6 * s)} {f(2 * s)}) rotate({a}) scale({f(s * 0.5)})", fill="url(#r2-flame)", opacity="0.9"))
    o.append(path(FLAME_D, transform=f"scale({f(s)})", fill="url(#r2-flame)"))
    o.append(path(FLAME_D, transform=f"translate(0 {f(4 * s)}) scale({f(s * 0.6)})", fill=BLUE_L, opacity="0.9"))
    o.append(path(FLAME_D, transform=f"translate(0 {f(7 * s)}) scale({f(s * 0.32)})", fill="#FFFFFF"))
    for _ in range(int(4 + 3 * s)):
        o.append(circ((rr.random() - 0.5) * 22 * s, -14 * s - rr.random() * 18 * s, 0.5 + rr.random() * 0.9 * s, fill=BLUE_L, filter="url(#r2-glow)"))
    o.append("</g>")
    return "".join(o)


def ore(cx, cy, s=1.0, seed=3, glow=True, n=6):
    rr = random.Random(seed)
    o = []
    if glow:
        o.append(ell(cx, cy - 6 * s, 26 * s, 16 * s, fill="url(#r2-ore-glow)"))
    # rock base
    for k in range(5):
        x = cx + (rr.random() - 0.5) * 26 * s
        y = cy + (rr.random() - 0.3) * 6 * s
        o.append(ell(x, y, (5 + rr.random() * 4) * s, (3 + rr.random() * 2) * s, fill="url(#r2-pebble)", **OL))
    crystals = sorted([((rr.random() - 0.5) * 70, (14 + rr.random() * 16) * s, (3.4 + rr.random() * 2.4) * s, (rr.random() - 0.5) * 18 * s) for _ in range(n)], key=lambda c: -c[1])
    for ang, ln, w, off in crystals:
        a = math.radians(ang - 90)
        bx, by = cx + off, cy
        tx, ty = bx + math.cos(a) * ln, by + math.sin(a) * ln
        px, py = -math.sin(a), math.cos(a)
        sh = (bx + math.cos(a) * ln * 0.78, by + math.sin(a) * ln * 0.78)
        left = [(bx - px * w, by - py * w), (sh[0] - px * w, sh[1] - py * w), (tx, ty), (sh[0], sh[1]), (bx, by + w * 0.3)]
        right = [(bx, by + w * 0.3), (sh[0], sh[1]), (tx, ty), (sh[0] + px * w, sh[1] + py * w), (bx + px * w, by + py * w)]
        lit_first = px < 0
        o.append(poly(left, fill=("url(#r2-ore-l)" if lit_first else "url(#r2-ore-d)"), **OL))
        o.append(poly(right, fill=("url(#r2-ore-d)" if lit_first else "url(#r2-ore-l)"), **OL))
        o.append(path(f"M{P((bx, by))} L{P(sh)} L{P((tx, ty))}", stroke="#FFFFFF", stroke_width="0.7", opacity="0.75"))
        o.append(circ(tx, ty, 1.2 * s, fill="#FFFFFF", filter="url(#r2-glow)"))
    return "".join(o)


# ---------------------------------------------------------------- isometric world
class Iso:
    """2:1 dimetric projection: u runs to the lower-right, v to the lower-left, z up."""

    def __init__(self, ox, oy, k=1.0):
        self.ox, self.oy, self.k = ox, oy, k

    def p(self, u, v, z=0.0):
        return (self.ox + (u - v) * self.k, self.oy + (u + v) * 0.5 * self.k - z * self.k)

    def poly(self, pts3, **a):
        return poly([self.p(*q) for q in pts3], **a)


def tuft(x, y, s, rr, cols=("#C2A458", "#9A8A42", "#7A8A3A", "#D8BC6A")):
    o = []
    for j in range(5):
        a = math.radians(-90 + (j - 2) * 18 + (rr.random() - 0.5) * 14)
        ln = (4 + rr.random() * 4) * s
        o.append(path(f"M{f(x + (j - 2) * 0.6)} {f(y)} q{f(math.cos(a) * ln * 0.3)} {f(math.sin(a) * ln * 0.6)} {f(math.cos(a) * ln)} {f(math.sin(a) * ln)}",
                      fill="none", stroke=rr.choice(cols), stroke_width=f(0.8 * s), stroke_linecap="round"))
    return "".join(o)


def leaf(x, y, s, rr):
    rot = rr.random() * 360
    c = rr.choice(("url(#r2-leafA)", "url(#r2-leafB)", "#B8432A", "#C86A2A"))
    d = "M0 -3 L0.9 -1 L3 -1.4 L1.6 0.4 L2.4 2.6 L0 1.4 L-2.4 2.6 L-1.6 0.4 L-3 -1.4 L-0.9 -1 Z"
    return path(d, transform=f"translate({f(x)} {f(y)}) rotate({f(rot)}) scale({f(s)} {f(s * 0.6)})", fill=c, stroke="#3A1A0A", stroke_width="0.25")


def pebble(x, y, s):
    return ell(x + 0.6, y + 0.9, 2.2 * s, 1.2 * s, fill="#000", opacity="0.35") + ell(x, y, 2.2 * s, 1.4 * s, fill="url(#r2-pebble)", stroke="#1A1612", stroke_width="0.3")


def ground_top(pts, bbox, seed, density=1.0, dark=False, cx=None, cy=None, hw=None, hh=None):
    """Textured diamond top: earth, mottled grass patches, grain, tufts, pebbles, maple leaves."""
    rr = random.Random(seed)
    cid = uid("gc")
    x, y, w, h = bbox
    d = "M" + " L".join(P(p) for p in pts) + " Z"
    o = [f'<clipPath id="{cid}"><path d="{d}"></path></clipPath>', path(d, fill=("url(#r2-earth-dark)" if dark else "url(#r2-earth)"))]
    g = [el("rect", x=f(x), y=f(y), width=f(w), height=f(h), fill="#6E7A34", filter="url(#r2-mottle)", opacity="0.85"),
         el("rect", x=f(x), y=f(y), width=f(w), height=f(h), fill="#000", filter="url(#r2-grain)")]

    def rnd():
        while True:
            a, b = rr.uniform(-1, 1), rr.uniform(-1, 1)
            if abs(a) + abs(b) < 0.9:
                return cx + a * hw, cy + b * hh

    n = int(hw * hh / 60 * density)
    for _ in range(int(n * 0.25)):
        px, py = rnd()
        g.append(ell(px, py, rr.uniform(3, 9), rr.uniform(1.5, 4), fill=rr.choice(("#4A3A22", "#8A7A48", "#5E6A2C")), opacity="0.35"))
    for _ in range(int(n * 0.3)):
        px, py = rnd()
        g.append(path(f"M{f(px)} {f(py)} q{f(rr.uniform(4, 9))} {f(rr.uniform(-1, 1))} {f(rr.uniform(8, 16))} {f(rr.uniform(-2, 2))}", fill="none",
                      stroke="#2A2012", stroke_width="0.6", opacity="0.35"))
    for _ in range(int(n * 0.5)):
        g.append(tuft(*rnd(), rr.uniform(0.7, 1.15), rr))
    for _ in range(int(n * 0.22)):
        g.append(pebble(*rnd(), rr.uniform(0.6, 1.2)))
    for _ in range(int(n * 0.3)):
        g.append(leaf(*rnd(), rr.uniform(0.8, 1.3), rr))
    o.append(f'<g clip-path="url(#{cid})">{"".join(g)}</g>')
    # lit edges / shaded edges
    o.append(path(f"M{P(pts[3])} L{P(pts[0])} L{P(pts[1])}", fill="none", stroke="#E8D6A0", stroke_width="1", opacity="0.4"))
    return "".join(o)


def diorama(cx, cy, hw, hh, depth=12, seed=1, density=1.0, dark=False):
    top = [(cx, cy - hh), (cx + hw, cy), (cx, cy + hh), (cx - hw, cy)]
    o = [ell(cx + 6, cy + hh * 0.7 + depth, hw * 0.95, hh * 0.55, fill="#000", opacity="0.55", filter="url(#r2-blur5)")]
    lf = [(cx - hw, cy), (cx, cy + hh), (cx, cy + hh + depth), (cx - hw, cy + depth)]
    rf = [(cx, cy + hh), (cx + hw, cy), (cx + hw, cy + depth), (cx, cy + hh + depth)]
    o.append(poly(lf, fill="url(#r2-soilL)", **OL))
    o.append(poly(rf, fill="url(#r2-soilR)", **OL))
    rr = random.Random(seed + 99)
    for k in range(3):
        yy = depth * (0.3 + k * 0.25)
        o.append(path(f"M{f(cx - hw)} {f(cy + yy)} L{f(cx)} {f(cy + hh + yy)} L{f(cx + hw)} {f(cy + yy)}", fill="none",
                      stroke=("#7A6040" if k % 2 else "#1A140C"), stroke_width="0.7", opacity="0.6"))
    for _ in range(int(hw / 6)):
        t = rr.random()
        side = rr.random() < 0.5
        if side:
            x, y = cx - hw + hw * t, cy + hh * t + rr.uniform(2, depth - 2)
        else:
            x, y = cx + hw * t, cy + hh * (1 - t) + rr.uniform(2, depth - 2)
        o.append(ell(x, y, rr.uniform(1, 2.2), rr.uniform(0.7, 1.4), fill=("#8A7A62" if side else "#4A4236")))
    # roots poking out
    for _ in range(3):
        t = rr.random()
        x, y = cx - hw + hw * t, cy + hh * t + 2
        o.append(path(f"M{f(x)} {f(y)} q{f(rr.uniform(-3, 3))} {f(depth * 0.4)} {f(rr.uniform(-4, 4))} {f(depth * 0.8)}", fill="none", stroke="#2A1A0C", stroke_width="0.8"))
    o.append(ground_top(top, (cx - hw, cy - hh, hw * 2, hh * 2), seed, density, dark, cx, cy, hw, hh))
    return "".join(o)


def sparks(cx, cy, rx, ry, n, seed, color=BLUE_L, rmax=1.4):
    rr = random.Random(seed)
    return "".join(circ(cx + rr.uniform(-rx, rx), cy + rr.uniform(-ry, ry), rr.uniform(0.4, rmax), fill=color, filter="url(#r2-glow)") for _ in range(n))


# ---------------------------------------------------------------- units (viewBox 0 0 250 200) — figure_kit render pass
# Pre-rendered-sprite shading (SPEC_CHAR.md): every limb is a shaded cylinder with core shadow + goblin-fire bounce,
# articulated elbows/knees, fists with knuckles, clawed toes, AO at every junction.
from figure_kit import Kit, MATS, Mat, mix as kmix  # noqa: E402

class R2Kit(Kit):
    """Organic dokkaebi flesh: softer joint AO. Limbs come from the kit's continuous tubes (arm/leg/tube)."""

    def _axis_ao(self, a, b, at_start=True, at_end=False, strength=0.2):
        return super()._axis_ao(a, b, at_start, at_end, min(strength, 0.2))


K = R2Kit("r2k")
SKB = MATS["skin_blue"]
SKB_D = Mat("#34449A", "#7E90E2", "#090C30", "#3FB0E8", spec="#E4ECFF", spec_op=0.45)  # brute/giant: deeper hide
GHO = MATS["skin_ghost"]
WISP = Mat("#2E8EE0", "#A8E2FF", "#0A2A64", "#7FD8FF", spec="#FFFFFF", spec_op=0.6)
HORN = MATS["horn"]
WOOD = MATS["wood"]
IRON = MATS["iron"]
TIGER = MATS["tiger"]
ROPE = MATS["straw"]


def kskin(cx, cy, r, m):
    return f"url(#{K._sphere_grad(cx, cy, r, m)})"


def khorn(base, tip, w, bend=0.0, m=HORN, rings=5):
    """Curved conical horn: one continuous tapered tube with growth rings."""
    bx, by = base
    tx, ty = tip
    dx, dy = tx - bx, ty - by
    ln = math.hypot(dx, dy) or 1
    px, py = -dy / ln, dx / ln
    pts = []
    for i in range(5):
        t = i / 4
        b = bend * 4 * t * (1 - t)
        pts.append((bx + dx * t + px * b, by + dy * t + py * b))
    out = [K.ao(bx, by + w * 0.3, w * 0.9, w * 0.45, 0.6)]
    out.append(K.tube(pts, [w, w * 0.78, w * 0.55, w * 0.32, max(0.4, w * 0.08)], m, n_per=4, chunks_per_seg=2))
    for i in range(rings):
        t = 0.1 + i * 0.13
        k = int(t * 4)
        u = t * 4 - k
        c = (pts[k][0] + (pts[k + 1][0] - pts[k][0]) * u, pts[k][1] + (pts[k + 1][1] - pts[k][1]) * u)
        hw = w * (1 - t * 0.96)
        out.append(f'<path d="M{f(c[0] - px * hw)} {f(c[1] - py * hw)} Q{f(c[0] + dx / ln * hw * 0.5)} {f(c[1] + dy / ln * hw * 0.5)} '
                   f'{f(c[0] + px * hw)} {f(c[1] + py * hw)}" fill="none" stroke="#3A2610" stroke-width="0.8" stroke-opacity="0.6"></path>')
    out.append(circ(tx, ty, 0.8, fill="#FFFFFF", opacity="0.85"))
    return "".join(out)


def dfoot(ankle, s, m, dirx=1, back=False):
    """Bare dokkaebi foot: kit foot volume + three toes with ivory claws."""
    mm = m.back() if back else m
    out = [K.foot(ankle, s, mm, dirx, back=False)]
    x, y = ankle
    for k in range(3):
        tx = x + dirx * s * (1.25 + k * 0.32)
        ty = y + s * (0.62 + k * 0.16)
        out.append(K.ball(tx, ty, s * 0.32, mm))
        out.append(path(f"M{f(tx + dirx * s * 0.22)} {f(ty - s * 0.12)} l{f(dirx * s * 0.36)} {f(s * 0.14)} l{f(-dirx * s * 0.3)} {f(s * 0.16)} Z",
                        fill="url(#r2-ivory)", stroke="#3A2A12", stroke_width="0.4"))
    return "".join(out)


def kclaws(wrist, direction, s):
    """Ivory nails at the knuckle row of a kit fist."""
    dx, dy = direction
    L = math.hypot(dx, dy) or 1
    ux, uy = dx / L, dy / L
    nx, ny = -uy, ux
    cx, cy = wrist[0] + ux * s * 0.95, wrist[1] + uy * s * 0.95
    kx, ky = cx + ux * s * 0.95, cy + uy * s * 0.95
    out = []
    for i in range(4):
        t = (i - 1.5) * 0.42
        x, y = kx + nx * s * t, ky + ny * s * t
        out.append(path(f"M{f(x - nx * s * 0.12)} {f(y - ny * s * 0.12)} L{f(x + ux * s * 0.3)} {f(y + uy * s * 0.3)} L{f(x + nx * s * 0.12)} {f(y + ny * s * 0.12)} Z",
                        fill="url(#r2-ivory)", stroke="#3A2A12", stroke_width="0.35"))
    return "".join(out)


def kclub(grip, ang, ln, w1, w2, seed=1):
    """Studded bangmang-i club: lacquered wood shaft + swollen head + iron studs, all volume-shaded."""
    rr = random.Random(seed)
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    b = (grip[0] - ux * ln * 0.18, grip[1] - uy * ln * 0.18)
    m1 = (grip[0] + ux * ln * 0.3, grip[1] + uy * ln * 0.3)
    e = (grip[0] + ux * ln * 0.82, grip[1] + uy * ln * 0.82)
    out = [K.form(b, m1, w1, w1 * 1.15, WOOD, ao_start=False),
           K.form(m1, e, w1 * 1.15, w2, WOOD, bumps=[(0.8, w2 * 0.18, 0)], ao_start=True, ao_end=False)]
    # wood grain + rope grip
    out.append(K.bands(b, (grip[0] + ux * ln * 0.08, grip[1] + uy * ln * 0.08), w1 * 1.05, w1 * 1.05, ROPE, n=4))
    nx, ny = -uy, ux
    for k in range(9):
        t = rr.uniform(0.38, 0.95)
        off = rr.uniform(-0.8, 0.8)
        px = grip[0] + ux * ln * t
        py = grip[1] + uy * ln * t
        w = w1 * 1.15 + (w2 - w1 * 1.15) * (t - 0.3) / 0.52
        x, y = px + nx * w * off, py + ny * w * off
        out.append(K.stud(x, y, max(1.2, w2 * 0.17), IRON))
    out.append(K.ball(e[0], e[1], w2 * 0.9, WOOD, outline=False))
    return "".join(out)


def kcloth(cx, wy, ww, drop, seed=2, flap=True):
    """Tiger-skin loincloth: volume gradient + clipped stripes + wrapping folds + ragged fur hem + rope belt."""
    rr = random.Random(seed)
    L, R = cx - ww - 3, cx + ww + 3
    pts = [(L, wy - 3)]
    n = 9
    for i in range(n + 1):
        t = i / n
        x = L - 2 + (R - L + 4) * t
        y = wy + drop * (0.85 + 0.15 * math.sin(t * math.pi)) + rr.uniform(-2.2, 2.2) + (3 if i % 2 else -1)
        pts.append((x, y))
    pts.append((R, wy - 3))
    d = "M" + " L".join(P(p) for p in pts) + " Z"
    gid = K._cyl_grad((cx, wy + drop * 0.5), (-1.0, -0.2), ww * 1.15, TIGER)
    out = [path(d, fill=f"url(#{gid})", stroke=OUTL, stroke_width="0.9", stroke_opacity="0.75", stroke_linejoin="round")]
    stripes = []
    for k in range(10):
        x0 = L + (R - L) * (k + 0.3) / 10 + rr.uniform(-2, 2)
        y0 = wy + rr.uniform(-1, drop * 0.4)
        ln = rr.uniform(drop * 0.35, drop * 0.75)
        bend = rr.uniform(-5, 5)
        wd = rr.uniform(1.6, 2.8)
        stripes.append(f'<path d="M{f(x0)} {f(y0)} q{f(bend)} {f(ln * 0.5)} {f(bend * 0.4)} {f(ln)} q{f(-wd * 0.5)} {f(-ln * 0.4)} {f(-wd - bend * 0.3)} {f(-ln * 0.8)} Z" '
                       f'fill="#1A0E06" opacity="0.88"></path>')
    # folds wrap the hips: vertical creases with lit ridges
    for k in range(6):
        x = L + (R - L) * (k + 0.5) / 6
        stripes.append(f'<path d="M{f(x)} {f(wy + 1)} q{f(rr.uniform(-2, 2))} {f(drop * 0.5)} {f(rr.uniform(-1, 3))} {f(drop)}" fill="none" stroke="#2A1004" '
                       f'stroke-width="2.4" stroke-opacity="0.5" filter="url(#r2k-soft)"></path>')
        stripes.append(f'<path d="M{f(x - 2.4)} {f(wy + 2)} q{f(rr.uniform(-1, 1))} {f(drop * 0.45)} {f(rr.uniform(-1, 1))} {f(drop * 0.85)}" fill="none" stroke="#FFE2A0" '
                       f'stroke-width="1" stroke-opacity="0.45"></path>')
    # AO from the belly overhang
    stripes.append(f'<rect x="{f(L - 4)}" y="{f(wy - 4)}" width="{f(R - L + 8)}" height="{f(drop * 0.3)}" fill="#0A0604" opacity="0.35" filter="url(#r2k-ao)"></rect>')
    out.append(K.clip_overlay(d, "".join(stripes)))
    # fur tufts on hem
    for p in pts[1:-1]:
        out.append(path(f"M{f(p[0] - 1.4)} {f(p[1] - 2)} l{f(rr.uniform(-1, 1))} {f(rr.uniform(2.5, 4))} l2.4 -3.6", fill="#E8B060", stroke="#3A1E08", stroke_width="0.4"))
    # rope belt with knot and hanging ends
    out.append(K.form((L - 1, wy - 2), (cx - 2, wy), 2.8, 2.8, ROPE, ao_start=False))
    out.append(K.form((cx - 2, wy), (R + 1, wy - 2), 2.8, 2.8, ROPE, ao_start=False))
    out.append(K.bands((L - 1, wy - 2), (R + 1, wy - 2), 2.8, 2.8, ROPE, n=9))
    if flap:
        kx = cx - ww * 0.35
        out.append(K.ball(kx, wy, 4, ROPE))
        out.append(K.form((kx, wy + 2), (kx - 4, wy + drop * 0.7), 2.2, 1.6, ROPE, ao_start=True))
        out.append(K.form((kx + 1, wy + 2), (kx + 4, wy + drop * 0.6), 2.2, 1.6, ROPE, ao_start=True))
    return "".join(out)


GOBLIN_LOOKS = {
    # per-character dokkaebi faces: kit face look + goblin extras
    # ears: pointed|long|round|torn|none   hair: mane|wild|tuft|bald|topknot   mouth_g: grin|fang|roar|smug|none
    # tusks: 0 none, 1 short, 2 long   ridge: brow-ridge weight 0..1   nose_bulb: 0..1   glow: eye glow 0..1
    "brute": dict(shape="square", jaw=1.2, eye="narrow", brow="angry", nose="broad", age=0.35, turn=-0.05, cheek=0.8,
                  ears="torn", hair="wild", mouth_g="grin", tusks=1, ridge=1.0, nose_bulb=0.7, glow=0.5, eye_col="#F0C040"),
    "thrower": dict(shape="heart", eye="round", eye_size=1.2, brow="arched", nose="small", age=0.1, turn=0.15, cheek=0.4,
                    ears="long", hair="tuft", mouth_g="fang", tusks=0, ridge=0.4, nose_bulb=0.3, glow=0.8, eye_col="#2AA8F0"),
    "giant": dict(shape="round", jaw=1.25, eye="droopy", eye_size=0.85, brow="thick", nose="flat", age=0.55, turn=0.0, cheek=0.6,
                  ears="round", hair="topknot", mouth_g="roar", tusks=2, ridge=0.8, nose_bulb=1.0, glow=0.4, eye_col="#E86A30", beard_g=True),
    "phantom": dict(shape="long", eye="sharp", brow="thin", nose="small", age=0.0, turn=-0.1, cheek=0.9,
                    ears="pointed", hair="bald", mouth_g="smug", tusks=0, ridge=0.2, nose_bulb=0.0, glow=1.0, eye_col="#BFF4FF"),
    "codex": dict(shape="oval", jaw=1.05, eye="almond", brow="angry", nose="broad", age=0.25, turn=0.0, cheek=0.6,
                  ears="pointed", hair="mane", mouth_g="grin", tusks=1, ridge=0.7, nose_bulb=0.5, glow=0.7, eye_col="#2AA8F0"),
}


def _goblin_ear(cx, cy, r, m, s, kind):
    """Ear on side s (-1 screen-left, +1 right) as a continuous tapered tube."""
    if kind == "none":
        return ""
    root = (cx + s * r * 0.88, cy + r * 0.02)
    if kind == "long":
        pts, ws = [root, (cx + s * r * 1.35, cy - r * 0.28), (cx + s * r * 1.85, cy - r * 0.62)], [r * 0.24, r * 0.16, r * 0.03]
    elif kind == "round":
        pts, ws = [root, (cx + s * r * 1.12, cy + r * 0.02), (cx + s * r * 1.22, cy - r * 0.08)], [r * 0.26, r * 0.28, r * 0.12]
    elif kind == "torn":
        pts, ws = [root, (cx + s * r * 1.25, cy - r * 0.2), (cx + s * r * 1.45, cy - r * 0.42)], [r * 0.25, r * 0.17, r * 0.07]
    else:  # pointed
        pts, ws = [root, (cx + s * r * 1.22, cy - r * 0.22), (cx + s * r * 1.55, cy - r * 0.55)], [r * 0.24, r * 0.15, r * 0.03]
    mm = m if s < 0 else m.back(0.18)
    out = [K.tube(pts, ws, mm, n_per=5, chunks_per_seg=2)]
    # inner-ear hollow
    mid = pts[1]
    out.append(ell((root[0] + mid[0]) / 2, (root[1] + mid[1]) / 2, r * 0.13, r * 0.07, fill=mm.core, opacity="0.65",
                   transform=f"rotate({f(math.degrees(math.atan2(mid[1] - root[1], mid[0] - root[0])))} {f((root[0] + mid[0]) / 2)} {f((root[1] + mid[1]) / 2)})"))
    if kind == "torn":
        tx, ty = pts[2]
        out.append(path(f"M{f(tx - s * r * 0.05)} {f(ty - r * 0.02)} l{f(-s * r * 0.08)} {f(r * 0.1)} l{f(-s * r * 0.02)} {f(-r * 0.12)} Z", fill=GROUND))
    out.append(circ(root[0] + s * r * 0.08, root[1] + r * 0.2, r * 0.09, fill="none", stroke="url(#r2-brass)", stroke_width=f(r * 0.05)))
    return "".join(out)


def _goblin_mouth(fx, cy, r, kind, grin=1.0):
    my = cy + r * 0.6
    out = []
    if kind in ("grin", "roar"):
        w = r * 0.5 * grin
        drop = r * (0.5 if kind == "roar" else 0.32)
        md = (f"M{f(fx - w)} {f(my - r * 0.08)} Q{f(fx)} {f(my + drop)} {f(fx + w)} {f(my - r * 0.08)} "
              f"Q{f(fx)} {f(my + r * 0.06)} {f(fx - w)} {f(my - r * 0.08)} Z")
        out.append(path(md, fill="#2A0810", stroke="#08020A", stroke_width="0.8"))
        out.append(ell(fx, my + drop * 0.55, w * 0.42, r * 0.07, fill="#8A2430", opacity="0.85"))
        n = 7 if kind == "grin" else 5
        for i in range(n):
            t = (i + 0.5) / n
            x = fx - w * 0.85 + 2 * w * 0.85 * t
            yb = my - r * 0.07 + r * 0.12 * (1 - (2 * t - 1) ** 2)
            tw = w / n * 0.85
            out.append(path(f"M{f(x - tw)} {f(yb - r * 0.02)} L{f(x + tw)} {f(yb - r * 0.02)} L{f(x + tw * 0.6)} {f(yb + r * 0.1)} L{f(x - tw * 0.6)} {f(yb + r * 0.1)} Z",
                            fill="#F2EAD2", stroke="#3A2A1A", stroke_width="0.35"))
        if kind == "roar":
            for i in range(4):
                t = (i + 0.5) / 4
                x = fx - w * 0.6 + 1.2 * w * t
                out.append(path(f"M{f(x - r * 0.04)} {f(my + drop * 0.78)} L{f(x)} {f(my + drop * 0.55)} L{f(x + r * 0.04)} {f(my + drop * 0.78)} Z", fill="#E8DEC2"))
        # cheek bunching above the grin corners
        for s in (-1, 1):
            out.append(path(f"M{f(fx + s * w * 0.75)} {f(my - r * 0.22)} Q{f(fx + s * w * 1.12)} {f(my - r * 0.1)} {f(fx + s * w * 1.05)} {f(my + r * 0.06)}",
                            fill="none", stroke="#0A0C2A", stroke_width=f(r * 0.04), opacity="0.5"))
    elif kind == "fang":
        w = r * 0.32
        out.append(path(f"M{f(fx - w)} {f(my)} Q{f(fx)} {f(my + r * 0.14)} {f(fx + w)} {f(my - r * 0.12)}", fill="none", stroke="#14040A",
                        stroke_width=f(r * 0.06), stroke_linecap="round"))
        out.append(path(f"M{f(fx + w * 0.55)} {f(my - r * 0.04)} l{f(r * 0.05)} {f(r * 0.16)} l{f(r * 0.05)} {f(-r * 0.18)} Z", fill="#F4ECD6", stroke="#3A2A1A", stroke_width="0.3"))
    elif kind == "smug":
        w = r * 0.28
        out.append(path(f"M{f(fx - w)} {f(my - r * 0.02)} Q{f(fx)} {f(my + r * 0.1)} {f(fx + w)} {f(my - r * 0.1)}", fill="none", stroke="#0A1430",
                        stroke_width=f(r * 0.05), stroke_linecap="round"))
    return "".join(out), my


def gface(cx, cy, r, m, look, horns=(), ghost=False, grin=1.0, seed=1):
    """Individual dokkaebi face: the kit face rig (shape/eyes/nose/age) plus goblin anatomy — ears, mane,
    brow ridge, bulb nose, tusks, toothy mouth, eye glow, horns."""
    L = dict(look)
    mouth_g = L.pop("mouth_g", "grin")
    ears = L.pop("ears", "pointed")
    hair = L.pop("hair", "mane")
    tusks = L.pop("tusks", 1)
    ridge = L.pop("ridge", 0.6)
    bulb = L.pop("nose_bulb", 0.5)
    glow = L.pop("glow", 0.6)
    beard_g = L.pop("beard_g", False)
    hair_col = L.pop("hair_col", "url(#r2-hair)")
    if mouth_g != "none":
        L["mouth"] = "calm"
        L.setdefault("lip", 0.0)
        L["mouth_col"] = m.core
    L.setdefault("brow_col", "#0E1030")
    out = []
    if hair in ("mane", "wild"):
        out.append(hair_strands(cx, cy - r * 0.1, r * 1.05, 30 if hair == "wild" else 24, 160, 380, 0.45, 1.05 if hair == "wild" else 0.85,
                                0.13, seed, fill=hair_col))
    if beard_g:
        out.append(hair_strands(cx, cy + r * 0.6, r * 0.8, 20, 20, 160, 0.5, 1.0, 0.12, seed + 7, up=-0.5, fill=hair_col))
    for s in (-1, 1):
        out.append(_goblin_ear(cx, cy, r, m, s, ears))
    face, d = K.face(cx, cy, r, m, L, ear=False)
    out.append(face)
    fx = cx + L.get("turn", 0.0) * r * 0.3
    ey = cy - r * 0.04
    # heavy brow ridge (overhang casts shadow into the sockets)
    if ridge > 0:
        rid = (f"M{f(fx - r * 0.78)} {f(ey - r * 0.2)} Q{f(fx - r * 0.4)} {f(ey - r * (0.38 + 0.12 * ridge))} {f(fx)} {f(ey - r * 0.22)} "
               f"Q{f(fx + r * 0.4)} {f(ey - r * (0.38 + 0.12 * ridge))} {f(fx + r * 0.78)} {f(ey - r * 0.2)}")
        out.append(K.clip_overlay(d, path(rid, fill="none", stroke=m.core, stroke_width=f(r * 0.16 * ridge), opacity="0.45", filter=f"url(#{K.p}-soft)")
                                  + path(rid, fill="none", stroke=m.light, stroke_width=f(r * 0.05 * ridge), opacity="0.5",
                                         transform=f"translate(0 {f(-r * 0.07)})")))
    if glow > 0:
        col = L.get("eye_col", BLUE)
        for s in (-1, 1):
            out.append(circ(fx + s * r * 0.36, ey, r * 0.2, fill=col, opacity=f(0.35 * glow), filter="url(#r2-blur2)"))
    if bulb > 0:
        out.append(K.ball(fx - r * 0.02, cy + r * 0.36, r * (0.1 + 0.08 * bulb), m, outline=False))
    mo, my = _goblin_mouth(fx, cy, r, mouth_g, grin)
    out.append(mo)
    if tusks:
        tl = r * (0.32 if tusks == 1 else 0.6)
        w = r * (0.35 if mouth_g in ("grin", "roar") else 0.22) * grin
        for s in (-1, 1):
            bx, by = fx + s * w, my + r * 0.12
            out.append(path(f"M{f(bx - r * 0.07)} {f(by)} Q{f(bx)} {f(by - tl * 0.7)} {f(bx + s * r * 0.12)} {f(by - tl)} "
                            f"Q{f(bx + s * r * 0.12)} {f(by - tl * 0.35)} {f(bx + r * 0.07)} {f(by)} Z", fill="url(#r2-ivory)", stroke="#4A3A22", stroke_width="0.6"))
    if hair == "tuft":
        out.append(hair_strands(cx, cy - r * 0.7, r * 0.5, 9, 220, 320, 0.5, 0.9, 0.16, seed + 3, up=0.4, fill=hair_col))
    elif hair == "topknot":
        out.append(K.ball(cx + r * 0.05, cy - r * 1.12, r * 0.28, MATS["hair"]))
        out.append(K.tube([(cx - r * 0.2, cy - r * 0.98), (cx + r * 0.3, cy - r * 0.98)], [r * 0.07, r * 0.07], MATS["red_cloth"], n_per=2))
    elif hair in ("mane", "wild"):
        out.append(hair_strands(cx, cy - r * 0.62, r * 0.7, 9, 200, 340, 0.3, 0.55, 0.15, seed + 3, up=-0.15, fill=hair_col))
    for (bx, by, tx, ty, w_, bend) in horns:
        out.append(khorn((cx + bx * r, cy + by * r), (cx + tx * r, cy + ty * r), w_ * r * 0.55, bend * r))
    out.append(rim_stroke(d, cx + r * 0.45, cy - r * 0.35, r * 1.2, r * 1.6, width=1.6, op=0.4))
    g = "".join(out)
    return f'<g opacity="0.78">{g}</g>' if ghost else g


def dhead(cx, cy, r, m, horns=(), grin=1.0, beard=False, seed=1, brow_angry=0.0, ghost=False, look="codex"):
    """Dokkaebi head with an individual face (see GOBLIN_LOOKS / gface)."""
    lk = dict(GOBLIN_LOOKS[look]) if isinstance(look, str) else dict(look)
    if beard:
        lk["beard_g"] = True
    return gface(cx, cy, r, m, lk, horns=horns, ghost=ghost, grin=grin, seed=seed)


def trap(neck, sw, m):
    """Trapezius slope + neck column with AO under the jaw."""
    nx, ny = neck
    d = (f"M{f(nx - sw * 0.9)} {f(ny + 6)} Q{f(nx - sw * 0.45)} {f(ny - 4)} {f(nx - 6)} {f(ny - 12)} L{f(nx + 6)} {f(ny - 12)} "
         f"Q{f(nx + sw * 0.45)} {f(ny - 4)} {f(nx + sw * 0.9)} {f(ny + 6)} Z")
    gid = K._cyl_grad((nx, ny), (-1.0, -0.3), sw * 0.9, m)
    return (path(d, fill=f"url(#{gid})", stroke=OUTL, stroke_width="0.8", stroke_opacity="0.7")
            + K.form((nx, ny + 2), (nx, ny - 14), sw * 0.32, sw * 0.28, m, ao_start=False, ao_end=True))


def deltoid(x, y, r, m, side=-1):
    """Deltoid cap: a teardrop muscle sliding from the shoulder down the outside of the upper arm."""
    top = (x - side * r * 0.2, y - r * 0.55)
    bot = (x + side * r * 0.35, y + r * 1.1)
    return K.form(top, bot, r * 0.95, r * 0.45, m, bumps=[(0.3, r * 0.15, 0)], ao_start=False, ao_end=False, outline=False)


def unit_brute():
    m = SKB_D
    o = [diorama(125, 168, 100, 31, 11, seed=12)]
    o.append(K.cast_shadow(126, 172, 46, 11, 0.6))
    cx = 122
    # legs: wide wrestler stance (back leg first)
    o.append(_dleg((cx + 13, 122), (cx + 26, 145), (cx + 28, 164), m, 13, 1, back=True))
    o.append(_dleg((cx - 13, 122), (cx - 25, 146), (cx - 27, 165), m, 13, -1))
    o.append(K.ao(cx, 128, 16, 7, 0.6))
    # back (lit) arm hangs — drawn under torso edge
    t, d = K.torso((cx, 64), (cx, 122), 35, 23, m, chest=1, abs_=True, belly=4)
    o.append(trap((cx, 66), 35, m))
    o.append(t)
    o.append(rim_stroke(d, cx + 16, 50, 40, 80, width=2.2, op=0.5))
    o.append(kcloth(cx, 121, 23, 22, seed=4))
    o.append(beads(cx, 70, 24, 18, 11, r=3))
    o.append(K.ao(cx, 66, 14, 5, 0.55))
    o.append(dhead(cx, 42, 19, m, horns=[(0.05, -0.8, 0.2, -1.95, 0.42, 0.16)], grin=1.05, seed=21, brow_angry=0.6, look="brute"))
    # left (screen) arm: hangs forward, fist clenched
    o.append(deltoid(cx - 35, 72, 10.5, m, -1))
    sh, el_, wr = (cx - 35, 74), (cx - 47, 100), (cx - 40, 122)
    o.append(K.arm(sh, el_, wr, m, w=10.5, muscular=0.45, hand="fist", hand_m=m))
    o.append(kclaws(wr, (wr[0] - el_[0], wr[1] - el_[1]), 7.2))
    # right arm: club resting on the shoulder
    o.append(deltoid(cx + 35, 72, 10.5, m, 1))
    sh, el_, wr = (cx + 35, 74), (cx + 58, 94), (cx + 40, 70)
    fd = (wr[0] - el_[0], wr[1] - el_[1])
    o.append(K.arm(sh, el_, wr, m, w=10.5, muscular=0.45, hand=None))
    hc = _hand_center(wr, fd, 7.2)
    o.append(kclub(hc, -64, 104, 4.2, 12.5, seed=8))
    o.append(K.hand(wr, fd, 7.2, m, kind="fist"))
    o.append(kclaws(wr, fd, 7.2))
    # brass arm-band
    o.append(_armband((cx + 35, 74), (cx + 58, 94), 0.42, 10.6))
    return "".join(o)


def unit_changgwi():
    o = [diorama(125, 168, 100, 31, 11, seed=31, dark=True)]
    o.append(f'<g transform="translate(130 174)">{Y.changgwi(K)}</g>')
    return "".join(o)


def unit_bulgasari():
    o = [diorama(125, 165, 120, 30, 11, seed=41, dark=True)]
    o.append(f'<g transform="translate(146 172) scale(0.92)">{Y.bulgasari(K)}</g>')
    return "".join(o)


def unit_eodukshini():
    o = [Y.eodukshini_bg(K, -30, -30, 310, 270)]
    o.append(diorama(125, 172, 96, 25, 10, seed=51, density=0.6))
    o.append(f'<g transform="translate(132 176) scale(0.9)">{Y.eodukshini(K)}</g>')
    return "".join(o)


def _hand_center(wrist, d, s):
    L = math.hypot(*d) or 1
    return (wrist[0] + d[0] / L * s * 0.95, wrist[1] + d[1] / L * s * 0.95)


def _armband(a, b, t, w):
    """Brass ring wrapping the arm: curved band with specular and studs."""
    c = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    L = math.hypot(b[0] - a[0], b[1] - a[1]) or 1
    ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
    nx, ny = -uy, ux
    p0, p1 = (c[0] + nx * w, c[1] + ny * w), (c[0] - nx * w, c[1] - ny * w)
    ctl = (c[0] + ux * w * 0.5, c[1] + uy * w * 0.5)
    d = f"M{P(p0)} Q{P(ctl)} {P(p1)}"
    out = [path(d, fill="none", stroke=OUTL, stroke_width="5", stroke_linecap="round", opacity="0.8"),
           path(d, fill="none", stroke="url(#r2-brass)", stroke_width="3.6", stroke_linecap="round"),
           path(f"M{P(p0)} Q{P((ctl[0] - ux, ctl[1] - uy))} {P(((p0[0] + p1[0]) / 2 + ux * w * 0.2, (p0[1] + p1[1]) / 2 + uy * w * 0.2))}",
                fill="none", stroke="#FFF6D0", stroke_width="1", opacity="0.8")]
    for k in (0.3, 0.6):
        q = ((1 - k) ** 2 * p0[0] + 2 * k * (1 - k) * ctl[0] + k * k * p1[0], (1 - k) ** 2 * p0[1] + 2 * k * (1 - k) * ctl[1] + k * k * p1[1])
        out.append(K.stud(q[0], q[1], 1.2))
    return "".join(out)


def _dleg(hip, knee, ankle, m, w, dirx, back=False, muscular=1.0):
    """Kit leg (thigh/knee/calf) ending in a clawed dokkaebi foot."""
    leg = K.leg(hip, knee, ankle, m, w=w, muscular=muscular, back=back, boot=m, foot_dir=dirx)
    return leg + dfoot(ankle, w * 0.8, m, dirx, back)


def unit_thrower(pose="hold", s=1.0, x=0, y=0, ground=True):
    m = SKB
    o = []
    if ground:
        o.append(diorama(125, 168, 100, 31, 11, seed=13))
    o.append(f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">')
    o.append(K.cast_shadow(128, 172, 36, 9, 0.55))
    cx = 120
    # stride: left foot forward
    o.append(_dleg((cx + 9, 120), (cx + 20, 143), (cx + 26, 165), m, 8.5, 1, back=True))
    o.append(_dleg((cx - 9, 120), (cx - 19, 142), (cx - 17, 165), m, 8.5, -1))
    o.append(K.ao(cx, 126, 11, 5, 0.6))
    # gourd arm (screen right) at the hip, behind the torso edge
    sh2, el2, wr2 = (cx + 22, 82), (cx + 32, 104), (cx + 26, 118)
    o.append(K.arm(sh2, el2, wr2, m, w=7.2, muscular=0.35, hand=None, back=True))
    t, d = K.torso((cx, 76), (cx, 120), 23, 15, m, chest=0.7, abs_=True)
    o.append(trap((cx, 78), 23, m))
    o.append(t)
    o.append(rim_stroke(d, cx + 10, 66, 30, 60, width=2, op=0.5))
    # red sash across the chest
    o.append(K.form((cx - 22, 80), (cx + 15, 118), 3.4, 3.4, MATS["red_cloth"], ao_start=False))
    o.append(K.folds((cx - 22, 80), (cx + 15, 118), 3.4, 3.4, MATS["red_cloth"], n=4))
    o.append(kcloth(cx, 119, 15, 16, seed=6, flap=False))
    # gourd (호리병) hanging from the hand
    gx, gy = cx + 28, 134
    o.append(K.ball(gx, gy + 6, 9, MATS["wood"], ry=10))
    o.append(K.ball(gx, gy - 8, 6, MATS["wood"], ry=6.5))
    o.append(K.form((gx - 6.5, gy - 2), (gx + 6.5, gy - 2), 1.8, 1.8, ROPE, ao_start=False))
    o.append(K.form((gx, gy - 14), (gx, gy - 19), 2.2, 2.2, MATS["leather_dark"], ao_start=False))
    o.append(flame(gx, gy - 21, 0.28, halo=False, seed=9))
    o.append(K.hand(wr2, (wr2[0] - el2[0], wr2[1] - el2[1]), 5.2, m.back(), kind="fist"))
    o.append(K.ao(cx, 78, 10, 4, 0.5))
    o.append(dhead(cx, 56, 16, m, horns=[(-0.45, -0.75, -0.8, -1.75, 0.34, -0.12), (0.45, -0.75, 0.82, -1.7, 0.34, 0.12)],
                   grin=0.95, seed=31, brow_angry=0.2, look="thrower"))
    # throwing arm (screen left)
    if pose == "hold":
        el_, wr, fl = (cx - 40, 72), (cx - 44, 50), (cx - 46, 34)
    elif pose == "windup":
        el_, wr, fl = (cx - 44, 90), (cx - 62, 74), (cx - 68, 62)
    else:  # release
        el_, wr, fl = (cx - 6, 66), (cx + 14, 52), (cx + 30, 40)
    sh = (cx - 23, 82)
    o.append(deltoid(cx - 23, 80, 7.5, m, -1))
    fd = (wr[0] - el_[0], wr[1] - el_[1])
    o.append(K.arm(sh, el_, wr, m, w=7.2, muscular=0.35, hand="open" if pose == "release" else "fist", hand_m=m))
    if pose != "release":
        o.append(kclaws(wr, fd, 5.0))
        o.append(flame(fl[0], fl[1], 0.75, seed=12))
    o.append("</g>")
    return "".join(o)


def unit_giant():
    m = SKB_D
    o = [diorama(125, 172, 110, 26, 10, seed=14)]
    o.append(K.cast_shadow(126, 176, 58, 12, 0.65))
    cx = 118
    for k, (dx, r) in enumerate(((-38, 10), (40, 11), (-24, 6), (28, 6))):
        o.append(circ(cx + dx, 172 - k, r, fill="url(#r2-smokeg)", opacity="0.4", filter="url(#r2-smoke)"))
    # the single, tree-trunk leg (centred under the pelvis)
    o.append(_dleg((cx + 1, 128), (cx + 9, 150), (cx + 4, 168), m, 17, 1, muscular=1.2))
    o.append(K.ao(cx + 2, 132, 20, 6, 0.6))
    # club dragged along the ground (behind right arm)
    # left (screen) arm
    o.append(deltoid(cx - 46, 74, 14, m.back(), -1))
    sh, el_, wr = (cx - 48, 76), (cx - 70, 106), (cx - 66, 134)
    o.append(K.arm(sh, el_, wr, m, w=13.5, muscular=0.5, hand=None, back=True))
    t, d = K.torso((cx, 66), (cx, 130), 46, 35, m, chest=1, abs_=False, belly=12)
    o.append(trap((cx, 68), 46, m))
    o.append(t)
    o.append(rim_stroke(d, cx + 20, 50, 56, 90, width=2.6, op=0.5))
    # navel + belly fold
    o.append(path(f"M{cx - 26} 118 Q{cx - 2} 126 {cx + 26} 116", fill="none", stroke="#090C30", stroke_width="2.2", opacity="0.5", filter="url(#r2k-soft)"))
    o.append(ell(cx + 2, 110, 2, 2.6, fill="#090C30", opacity="0.7"))
    o.append(kcloth(cx, 129, 35, 13, seed=8))
    o.append(beads(cx, 62, 32, 22, 13, r=3.6))
    o.append(K.hand(wr, (wr[0] - el_[0], wr[1] - el_[1]), 9.4, m.back(), kind="fist"))
    o.append(kclaws(wr, (wr[0] - el_[0], wr[1] - el_[1]), 9.4))
    o.append(K.ao(cx, 66, 18, 6, 0.55))
    o.append(dhead(cx, 40, 22, m, horns=[(-0.5, -0.78, -0.95, -1.65, 0.34, -0.2), (0, -0.92, 0.05, -1.95, 0.4, 0.1), (0.5, -0.78, 0.95, -1.65, 0.34, 0.2)],
                   grin=1.1, beard=True, seed=41, brow_angry=0.4, look="giant"))
    # right arm gripping the dragged club
    o.append(deltoid(cx + 46, 74, 14, m, 1))
    sh, el_, wr = (cx + 48, 76), (cx + 70, 104), (cx + 66, 128)
    fd = (wr[0] - el_[0], wr[1] - el_[1])
    o.append(K.arm(sh, el_, wr, m, w=13.5, muscular=0.5, hand=None))
    o.append(kclub(_hand_center(wr, fd, 9.4), 40, 74, 5.5, 15.5, seed=10))
    o.append(K.hand(wr, fd, 9.4, m, kind="fist"))
    o.append(kclaws(wr, fd, 9.4))
    o.append(_armband(sh, el_, 0.45, 13.6))
    o.append(path(f"M{cx - 8} 178 l-12 4 l-8 -2 M{cx + 16} 178 l12 3 l6 4", fill="none", stroke="#1A120A", stroke_width="1.2"))
    return "".join(o)


def unit_phantom():
    m = GHO
    o = [diorama(125, 172, 96, 22, 9, seed=15, density=0.8)]
    o.append(ell(160, 178, 22, 5, fill="#000", opacity="0.4", filter="url(#r2-shadow)"))
    o.append(path("M160 178 L128 120", stroke=BLUE_L, stroke_width="0.6", stroke_dasharray="2 3", opacity="0.4"))
    cx = 120
    o.append(circ(cx, 70, 74, fill="url(#r2-halo)", opacity="0.55"))
    tail = (f"M{cx - 20} 104 C{cx - 26} 130 {cx - 60} 130 {cx - 70} 150 C{cx - 78} 166 {cx - 56} 174 {cx - 48} 160 "
            f"C{cx - 42} 150 {cx - 56} 148 {cx - 52} 154 C{cx - 30} 146 {cx + 4} 134 {cx + 18} 104 Z")
    o.append(path(tail, fill="url(#r2-ghost)", filter="url(#r2-smoke)", opacity="0.85"))
    o.append(path(tail, fill="none", stroke=BLUE_L, stroke_width="0.8", opacity="0.4"))
    body = []
    # reaching arms with spread clawed fingers
    for s, (sh, el_, wr) in ((1, ((cx + 22, 72), (cx + 48, 82), (cx + 70, 64))), (-1, ((cx - 22, 72), (cx - 48, 84), (cx - 68, 66)))):
        body.append(K.arm(sh, el_, wr, m, w=6.5, muscular=0.35, hand="open", hand_m=m, back=(s > 0)))
    t, d = K.torso((cx, 66), (cx, 110), 25, 17, m, chest=0.9, abs_=True)
    body.append(trap((cx, 68), 25, m))
    body.append(t)
    body.append(deltoid(cx - 23, 70, 7, m, -1) + deltoid(cx + 23, 70, 7, m.back(), 1))
    # body dissolves into the smoke tail
    body.append(ell(cx, 108, 26, 9, fill="#4E58A8", opacity="0.6", filter="url(#r2-blur5)"))
    o.append(f'<g opacity="0.82">{"".join(body)}</g>')
    o.append(dhead(cx, 46, 18, m, horns=[(-0.4, -0.8, -0.7, -1.7, 0.3, -0.1), (0.4, -0.8, 0.7, -1.7, 0.3, 0.1)], grin=0.8, seed=51, ghost=True, look="phantom"))
    for s in (-1, 1):
        o.append(circ(cx + s * 7, 46, 4, fill=BLUE_L, filter="url(#r2-blur2)"))
    o.append(sparks(cx, 70, 70, 50, 26, 8))
    return "".join(o)


def wisp_arms(cx, cy, s, ore_at):
    """Little flame-flesh arms hugging a lump of ore."""
    m = WISP
    out = []
    for sx in (-1, 1):
        sh = (cx + sx * 16 * s, cy + 16 * s)
        el_ = (cx + sx * 27 * s, cy + 36 * s)
        wr = (ore_at[0] + sx * 10 * s, ore_at[1] - 4 * s)
        out.append(K.arm(sh, el_, wr, m, w=3.4 * s, hand="open", hand_m=m, back=(sx > 0),
                         hand_dir=(-sx * 1.0, 0.6)))
    return "".join(out)


def unit_wisp():
    o = [diorama(125, 168, 96, 30, 11, seed=11, density=0.9)]
    o.append(ell(125, 170, 40, 12, fill="url(#r2-pool)"))
    o.append(K.cast_shadow(130, 172, 22, 6, 0.45))
    for k in range(4):
        o.append(flame(90 - k * 9, 122 + k * 6, 0.5 - k * 0.07, rot=-50, halo=False, op=0.7 - k * 0.12, seed=40 + k))
    cx, cy = 125, 96
    o.append(circ(cx, cy - 6, 66, fill="url(#r2-halo)"))
    o.append(path(FLAME_D, transform=f"translate({cx} {cy}) scale(3.9)", fill="#1E7EC0", opacity="0.55", filter="url(#r2-blur5)"))
    for k, a, sx in ((-1, -24, 28), (1, 22, 26), (-1, -40, 36), (1, 38, 32)):
        o.append(path(FLAME_D, transform=f"translate({cx + k * sx} {cy + 12}) rotate({a}) scale({1.35 if abs(a) < 30 else 1.0})", fill="url(#r2-flame)", opacity="0.85"))
    # flame body with a volumetric core: dark outer mantle -> bright inner core
    o.append(path(FLAME_D, transform=f"translate({cx} {cy}) scale(3.4)", fill="url(#r2-flame)", stroke="#0E3A60", stroke_width="0.3"))
    o.append(path(FLAME_D, transform=f"translate({cx + 6} {cy + 4}) scale(2.6)", fill="#0E3A70", opacity="0.35", filter="url(#r2k-ao)"))
    o.append(path(FLAME_D, transform=f"translate({cx - 3} {cy + 14}) scale(2.2)", fill=BLUE_L, opacity="0.85"))
    o.append(path(FLAME_D, transform=f"translate({cx - 4} {cy + 24}) scale(1.1)", fill="#FFFFFF", opacity="0.95"))
    o.append(khorn((cx - 17, cy - 38), (cx - 28, cy - 62), 4.2, -3))
    o.append(khorn((cx + 15, cy - 40), (cx + 24, cy - 62), 3.8, 3))
    for s in (-1, 1):
        ex, ey = cx + s * 12, cy - 6
        o.append(ell(ex, ey, 6.5, 8, fill="#0B2A48"))
        o.append(ell(ex, ey + 1, 3.9, 5, fill="#E6F8FF"))
        o.append(circ(ex - 1.2, ey - 1.6, 1.5, fill="#FFFFFF"))
    o.append(path(f"M{cx - 14} {cy + 8} Q{cx} {cy + 23} {cx + 14} {cy + 8} Q{cx} {cy + 13} {cx - 14} {cy + 8} Z", fill="#0B2A48"))
    for i in range(4):
        x = cx - 9 + i * 6
        o.append(path(f"M{x - 2} {cy + 10} L{x + 2} {cy + 10} L{x} {cy + 14} Z", fill="#E6F8FF"))
    oa = (cx, cy + 50)
    o.append(ore(oa[0], oa[1], 0.8, seed=5, n=5))
    o.append(wisp_arms(cx, cy, 1.0, oa))
    o.append(sparks(cx, cy - 40, 54, 36, 22, 3))
    return "".join(o)



# ---------------------------------------------------------------- buildings (viewBox 0 0 200 170)
def wall_face(iso, a, b, h, kind="L", posts=3, window=None, door=None, seed=1):
    """Timber-framed plaster wall on the face from ground point a(u,v) to b(u,v), height h."""
    (u0, v0), (u1, v1) = a, b
    pl = "url(#r2-plasterL)" if kind == "L" else "url(#r2-plasterR)"
    tm = "url(#r2-timberL)" if kind == "L" else "url(#r2-timberR)"

    def fp(s, t):
        return iso.p(u0 + (u1 - u0) * s, v0 + (v1 - v0) * s, t)

    o = [poly([fp(0, 0), fp(1, 0), fp(1, h), fp(0, h)], fill=pl, **OL)]
    # plaster stains
    rr = random.Random(seed)
    for _ in range(4):
        s, t = rr.uniform(0.1, 0.9), rr.uniform(0.15, 0.8) * h
        c = fp(s, t)
        o.append(ell(c[0], c[1], rr.uniform(2, 5), rr.uniform(1, 3), fill="#5A4A30", opacity="0.15"))
    # stone base course
    o.append(poly([fp(0, 0), fp(1, 0), fp(1, h * 0.14), fp(0, h * 0.14)], fill=("url(#r2-stoneL)" if kind == "L" else "url(#r2-stoneR)"), **OL))
    for k in range(int(posts * 3)):
        s0 = k / (posts * 3)
        o.append(path(f"M{P(fp(s0, 0))} L{P(fp(s0, h * 0.14))}", stroke="#1A1612", stroke_width="0.5", opacity="0.6"))
    # beams
    for t0, t1 in ((h * 0.14, h * 0.2), (h * 0.55, h * 0.6), (h * 0.92, h)):
        o.append(poly([fp(0, t0), fp(1, t0), fp(1, t1), fp(0, t1)], fill=tm, **OL))
    # posts
    for k in range(posts + 1):
        s = k / posts
        sw = 0.035
        o.append(poly([fp(max(0, s - sw), h * 0.14), fp(min(1, s + sw), h * 0.14), fp(min(1, s + sw), h), fp(max(0, s - sw), h)], fill=tm, **OL))
    if window:
        s0, s1, t0, t1 = window
        o.append(poly([fp(s0, t0 * h), fp(s1, t0 * h), fp(s1, t1 * h), fp(s0, t1 * h)], fill="#F2E8CE", stroke="#3A2414", stroke_width="1"))
        for k in range(1, 5):
            s = s0 + (s1 - s0) * k / 5
            o.append(path(f"M{P(fp(s, t0 * h))} L{P(fp(s, t1 * h))}", stroke="#5A3A20", stroke_width="0.7"))
        for k in range(1, 4):
            t = (t0 + (t1 - t0) * k / 4) * h
            o.append(path(f"M{P(fp(s0, t))} L{P(fp(s1, t))}", stroke="#5A3A20", stroke_width="0.7"))
    if door:
        s0, s1, t1, glow = door
        o.append(poly([fp(s0, h * 0.14), fp(s1, h * 0.14), fp(s1, t1 * h), fp(s0, t1 * h)], fill=("#0A1A28" if glow else "#2A1A0E"), **OL))
        if glow:
            c = fp((s0 + s1) / 2, h * 0.35)
            o.append(ell(c[0], c[1], 9, 12, fill=BLUE, opacity="0.55", filter="url(#r2-blur5)"))
            o.append(ell(c[0], c[1] + 4, 4, 6, fill=BLUE_L, opacity="0.8", filter="url(#r2-blur2)"))
    if kind == "R":
        o.append(poly([fp(0, 0), fp(1, 0), fp(1, h), fp(0, h)], fill="#000", opacity="0.12"))
    return "".join(o)


def dancheong_band(iso, a, b, z0, z1, kind="L"):
    (u0, v0), (u1, v1) = a, b
    cols = (RED, GRN, YEL, GRN, "#2F6E8E")
    o = []
    n = 14
    for k in range(n):
        s0, s1 = k / n, (k + 1) / n
        pts = [iso.p(u0 + (u1 - u0) * s0, v0 + (v1 - v0) * s0, z0), iso.p(u0 + (u1 - u0) * s1, v0 + (v1 - v0) * s1, z0),
               iso.p(u0 + (u1 - u0) * s1, v0 + (v1 - v0) * s1, z1), iso.p(u0 + (u1 - u0) * s0, v0 + (v1 - v0) * s0, z1)]
        o.append(poly(pts, fill=cols[k % len(cols)], opacity=("1" if kind == "L" else "0.65")))
    return "".join(o)


def giwa_roof(iso, W, D, H, R, o_=8, curl=5, seed=1):
    """Gable roof with ridge along u: back slope, front slope with individual tile rows, ridge, end tiles."""
    o = []
    # back slope (faces -v)
    back = [iso.p(-o_, -o_, H + curl), iso.p(W + o_, -o_, H + curl), iso.p(W + o_, D / 2, H + R), iso.p(-o_, D / 2, H + R)]
    o.append(poly(back, fill="url(#r2-roofB)", **OL))
    for k in range(int((W + 2 * o_) / 4)):
        u = -o_ + k * 4 + 2
        a, b = iso.p(u, -o_, H + curl * (1 if k < 2 or u > W + o_ - 8 else 0.4)), iso.p(u, D / 2, H + R)
        o.append(path(f"M{P(a)} L{P(b)}", stroke=("#4A4E58" if k % 2 else "#141418"), stroke_width="1.4"))
    # gable end (u = W) pediment
    ped = [iso.p(W, 0, H), iso.p(W, D, H), iso.p(W, D / 2, H + R * 0.92)]
    o.append(poly(ped, fill="url(#r2-plasterR)", **OL))
    o.append(path(f"M{P(iso.p(W, D / 2, H))} L{P(iso.p(W, D / 2, H + R * 0.9))}", stroke="#2A1608", stroke_width="1.6"))
    # gable bargeboards
    o.append(path(f"M{P(iso.p(W + o_, -o_, H + curl))} L{P(iso.p(W + o_, D / 2, H + R + 1))} L{P(iso.p(W + o_, D + o_, H + curl))}",
                  fill="none", stroke="#3A2414", stroke_width="2.6", stroke_linejoin="round"))
    # front slope (faces +v) with curved eave
    e0, e1, em = iso.p(-o_, D + o_, H + curl), iso.p(W + o_, D + o_, H + curl), iso.p(W / 2, D + o_, H - 2)
    r0, r1 = iso.p(-o_, D / 2, H + R), iso.p(W + o_, D / 2, H + R)
    ctrl = (2 * em[0] - (e0[0] + e1[0]) / 2, 2 * em[1] - (e0[1] + e1[1]) / 2)
    front_d = f"M{P(e0)} Q{P(ctrl)} {P(e1)} L{P(r1)} L{P(r0)} Z"
    o.append(path(front_d, fill="url(#r2-roofF)", **OL))
    cid = uid("rf")
    rows = []
    n = int((W + 2 * o_) / 3.6)
    for k in range(n + 1):
        t = k / n
        u = -o_ + (W + 2 * o_) * t
        # eave point on the quadratic
        ex = (1 - t) ** 2 * e0[0] + 2 * (1 - t) * t * ctrl[0] + t * t * e1[0]
        ey = (1 - t) ** 2 * e0[1] + 2 * (1 - t) * t * ctrl[1] + t * t * e1[1]
        rx, ry = iso.p(u, D / 2, H + R)
        if k % 2 == 0:
            rows.append(path(f"M{f(ex)} {f(ey)} L{f(rx)} {f(ry)}", stroke="#1A1B20", stroke_width="2.6"))
            rows.append(path(f"M{f(ex - 0.5)} {f(ey - 0.4)} L{f(rx - 0.5)} {f(ry - 0.4)}", stroke="#8A909C", stroke_width="0.8", opacity="0.8"))
            # tile courses (segment marks)
            for j in range(1, 7):
                q = j / 7
                mx, my = ex + (rx - ex) * q, ey + (ry - ey) * q
                rows.append(path(f"M{f(mx - 1.3)} {f(my + 0.2)} L{f(mx + 1.3)} {f(my - 0.2)}", stroke="#0E0F12", stroke_width="0.7"))
            rows.append(circ(ex, ey + 1, 1.7, fill="#3A3E48", stroke="#0A0A0E", stroke_width="0.4"))
            rows.append(circ(ex - 0.4, ey + 0.6, 0.6, fill="#A0A6B2"))
        else:
            rows.append(path(f"M{f(ex)} {f(ey)} L{f(rx)} {f(ry)}", stroke="#2A2C34", stroke_width="1.2", opacity="0.8"))
    o.append(f'<clipPath id="{cid}"><path d="{front_d}"></path></clipPath><g clip-path="url(#{cid})">{"".join(rows)}'
             f'{el("rect", x=f(min(e0[0], r0[0]) - 5), y=f(min(r0[1], r1[1]) - 5), width=f(abs(e1[0] - e0[0]) + 40), height="120", fill="url(#r2-shadeR)", opacity="0.6")}</g>')
    # eave underside shadow
    o.append(path(f"M{P(e0)} Q{P(ctrl)} {P(e1)}", fill="none", stroke="#0A0A0C", stroke_width="1.6"))
    # ridge beam
    o.append(path(f"M{P(r0)} L{P(r1)}", stroke="#0E0F12", stroke_width="5.5", stroke_linecap="round"))
    o.append(path(f"M{P(r0)} L{P(r1)}", stroke="#5A5E68", stroke_width="3.4", stroke_linecap="round"))
    o.append(path(f"M{f(r0[0])} {f(r0[1] - 1.2)} L{f(r1[0])} {f(r1[1] - 1.2)}", stroke="#B0B6C2", stroke_width="0.8"))
    for (x, y), s in ((r0, -1), (r1, 1)):
        o.append(path(f"M{f(x)} {f(y)} q{f(s * 3)} -3 {f(s * 5)} -8 q{f(-s * 1)} 4 {f(-s * 2)} 8 Z", fill="#2A2C34", stroke="#0A0A0E", stroke_width="0.5"))
    # upturned eave corners
    for (x, y), s in ((e0, -1), (e1, 1)):
        o.append(path(f"M{f(x)} {f(y)} q{f(s * 4)} -1 {f(s * 6)} -6", fill="none", stroke="#1A1B20", stroke_width="2.4", stroke_linecap="round"))
    return "".join(o)


def bsvg(body, label, vb="0 0 200 170"):
    return f'<svg width="100%" height="100%" viewBox="{vb}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="{label}">{body}</svg>'


def lantern(x, y, s=1.0):
    o = [path(f"M{f(x)} {f(y - 10 * s)} L{f(x)} {f(y - 5 * s)}", stroke="#1A120A", stroke_width="0.8"),
         circ(x, y, 9 * s, fill="url(#r2-halo)"),
         ell(x, y, 3.4 * s, 4.4 * s, fill="#E6F2E8", stroke="#3A2414", stroke_width="0.7"),
         ell(x, y, 2 * s, 3 * s, fill=BLUE_L, opacity="0.8", filter="url(#r2-blur2)"),
         path(f"M{f(x - 3.4 * s)} {f(y - 4.4 * s)} L{f(x + 3.4 * s)} {f(y - 4.4 * s)} M{f(x - 3.4 * s)} {f(y + 4.4 * s)} L{f(x + 3.4 * s)} {f(y + 4.4 * s)}", stroke=RED, stroke_width="1.4")]
    return "".join(o)


def geumjul(points, seed=1, paper=True):
    """금줄: twisted straw rope through points with hanging white paper strips."""
    rr = random.Random(seed)
    d = "M" + " ".join(P(p) for p in points[:1]) + "".join(f" Q{P(points[i])} {P(((points[i][0] + points[i + 1][0]) / 2, (points[i][1] + points[i + 1][1]) / 2))}" for i in range(1, len(points) - 1)) + f" L{P(points[-1])}"
    o = [path(d, fill="none", stroke="#3A2A10", stroke_width="3.6", stroke_linecap="round"),
         path(d, fill="none", stroke="url(#r2-rope)", stroke_width="2.6", stroke_linecap="round"),
         path(d, fill="none", stroke="#5A441C", stroke_width="2.6", stroke_dasharray="1.2 1.6")]
    if paper:
        for i in range(1, len(points) - 1):
            x, y = points[i]
            for j in range(2):
                xx = x + (j - 0.5) * 5 + rr.uniform(-1, 1)
                o.append(path(f"M{f(xx)} {f(y)} l1.8 4 l-2.2 2.6 l2 3.6 l-1.6 2.4", fill="none", stroke="#F6F2E6", stroke_width="1.7", stroke_linejoin="bevel"))
    return "".join(o)


def bld_lair():
    o = [diorama(100, 132, 92, 34, 12, seed=21)]
    # sprawling roots
    rr = random.Random(5)
    for k in range(9):
        a = math.radians(rr.uniform(-170, -10) + 180)
        ln = rr.uniform(26, 50)
        x0, y0 = 100 + math.cos(a) * 14, 120 + math.sin(a) * 5
        x1, y1 = 100 + math.cos(a) * ln, 122 + math.sin(a) * ln * 0.42
        o.append(path(f"M{f(x0)} {f(y0)} Q{f((x0 + x1) / 2)} {f((y0 + y1) / 2 - 4)} {f(x1)} {f(y1)}", fill="none", stroke="#1A120C", stroke_width="6.5", stroke_linecap="round"))
        o.append(path(f"M{f(x0)} {f(y0)} Q{f((x0 + x1) / 2)} {f((y0 + y1) / 2 - 4)} {f(x1)} {f(y1)}", fill="none", stroke="url(#r2-bark)", stroke_width="4.6", stroke_linecap="round"))
    o.append(shadow(118, 128, 46, 12, 0.6))
    # trunk
    trunk = ("M70 126 C74 108 78 92 76 74 C74 60 66 50 58 42 L66 40 C74 46 82 54 88 62 C90 50 94 40 104 34 L110 38 C104 46 104 56 108 66 "
             "C114 56 124 48 134 44 L138 50 C128 56 122 66 120 80 C118 96 124 112 132 126 Z")
    o.append(path(trunk, fill="url(#r2-bark)", **OL))
    for k in range(18):
        x = 74 + k * 3.2 + rr.uniform(-1, 1)
        o.append(path(f"M{f(x)} 124 C{f(x + rr.uniform(-3, 3))} 110 {f(x + rr.uniform(-4, 4))} 92 {f(x + rr.uniform(-3, 3) + (k - 9) * 0.6)} {f(70 + rr.uniform(0, 8))}",
                      fill="none", stroke=("#140C06" if k % 2 else "#A88660"), stroke_width=("1.1" if k % 2 else "0.6"), opacity=("0.6" if k % 2 else "0.5")))
    for _ in range(10):
        x, y = rr.uniform(78, 118), rr.uniform(70, 118)
        o.append(ell(x, y, rr.uniform(1.5, 3), rr.uniform(0.8, 1.5), fill="#0E0804", opacity="0.6"))
    o.append(clip_shade(trunk, (56, 30, 86, 100), grads=("r2-shadeR",)))
    # hollow
    hol = "M90 126 C88 112 90 98 100 92 C110 98 112 112 110 126 Z"
    o.append(path("M86 127 C84 110 88 94 100 87 C112 94 116 110 114 127 Z", fill="#2A1C10", **OL))
    o.append(path(hol, fill="url(#r2-hollow)"))
    o.append(ell(100, 122, 12, 6, fill=BLUE_L, opacity="0.6", filter="url(#r2-blur5)"))
    o.append(flame(100, 118, 0.38, halo=False, seed=4))
    # 금줄
    o.append(geumjul([(70, 100), (84, 106), (100, 108), (116, 106), (128, 100)], seed=3))
    # canopy (autumn zelkova)
    clusters = [(56, 38, 22), (82, 22, 24), (112, 18, 26), (142, 34, 22), (100, 40, 22), (128, 52, 16), (68, 56, 14)]
    for (x, y, r) in clusters:
        o.append(circ(x + 4, y + 6, r, fill="#2A1408", opacity="0.6", filter="url(#r2-blur2)"))
    for (x, y, r) in clusters:
        o.append(circ(x, y, r, fill="#7A2E14", **OL))
        for _ in range(int(r * 2.2)):
            a = rr.uniform(0, math.tau)
            d = rr.uniform(0, r * 0.9)
            lx, ly = x + math.cos(a) * d, y + math.sin(a) * d
            light = (-(lx - x) - (ly - y)) / (r * 1.4)
            col = "#F2A848" if light > 0.35 else ("#D2702A" if light > -0.1 else "#8A3418")
            o.append(circ(lx, ly, rr.uniform(1.6, 3.4), fill=col, opacity="0.9"))
        o.append(circ(x - r * 0.35, y - r * 0.35, r * 0.4, fill="#FFD890", opacity="0.25", filter="url(#r2-blur2)"))
    # hanging lanterns + wisps
    o.append(path("M60 56 L60 70 M140 52 L140 66", stroke="#1A120A", stroke_width="0.8"))
    o.append(lantern(60, 74, 1.0))
    o.append(lantern(140, 70, 1.0))
    o.append(flame(40, 112, 0.32, seed=5))
    o.append(flame(162, 118, 0.3, seed=6))
    o.append(sparks(100, 70, 60, 40, 14, 9))
    return bsvg("".join(o), "도깨비 소굴")


def stone_cylinder(cx, cy, R, H, courses=5, seed=1):
    rr = random.Random(seed)
    o = [path(f"M{f(cx - R)} {f(cy)} A{f(R)} {f(R / 2)} 0 0 0 {f(cx + R)} {f(cy)} L{f(cx + R)} {f(cy - H)} A{f(R)} {f(R / 2)} 0 0 1 {f(cx - R)} {f(cy - H)} Z",
              fill="#3A3630", **OL)]
    ch = H / courses
    for c in range(courses):
        z0, z1 = c * ch, (c + 1) * ch
        n = 7
        off = (c % 2) * 0.5
        for k in range(n):
            t0 = (k + off) / n * math.pi
            t1 = min(math.pi, (k + off + 0.94) / n * math.pi)
            if t1 <= t0:
                continue
            pts = []
            for t in (t0, (t0 + t1) / 2, t1):
                pts.append((cx + R * math.cos(math.pi - t) * 0.98, cy + R / 2 * math.sin(t) * 0.98 - z0 - 0.6))
            for t in (t1, (t0 + t1) / 2, t0):
                pts.append((cx + R * math.cos(math.pi - t) * 0.98, cy + R / 2 * math.sin(t) * 0.98 - z1 + 0.6))
            mid = (t0 + t1) / 2
            light = -math.cos(math.pi - mid)
            v = int(110 + light * 50 + rr.uniform(-14, 14))
            col = f"#{v:02X}{int(v * 0.96):02X}{int(v * 0.88):02X}"
            o.append(poly(pts, fill=col, stroke="#1A1612", stroke_width="0.6"))
            o.append(path(f"M{P(pts[3])} L{P(pts[4])}", stroke="#FFFFFF", stroke_width="0.5", opacity=f"{max(0.05, 0.3 * light):.2f}"))
    return "".join(o)


def bld_well():
    o = [diorama(100, 128, 86, 32, 12, seed=22)]
    o.append(shadow(112, 126, 40, 12, 0.6))
    cx, cy, R, H = 100, 122, 28, 22
    o.append(stone_cylinder(cx, cy, R, H, 4, seed=3))
    # rim top
    o.append(ell(cx, cy - H, R, R / 2, fill="url(#r2-stoneT)", **OL))
    o.append(ell(cx, cy - H, R * 0.78, R * 0.38, fill="#0A121A", **OL))
    o.append(ell(cx, cy - H + 2, R * 0.66, R * 0.3, fill="url(#r2-hollow)", opacity="0.85"))
    o.append(flame(cx, cy - H - 2, 0.42, halo=True, seed=7))
    # violet 산기 plume
    for k, (dx, dy, r) in enumerate(((0, -40, 14), (-6, -58, 12), (5, -74, 10), (-3, -88, 8))):
        o.append(circ(cx + dx, cy - H + dy + 18, r, fill="url(#r2-mist)", filter="url(#r2-smoke)"))
    # timber frame with small giwa cap
    for x in (cx - 30, cx + 30):
        o.append(el("rect", x=f(x - 2.4), y=f(cy - H - 44), width="4.8", height="48", fill=("url(#r2-timberL)" if x < cx else "url(#r2-timberR)"), **OL))
    o.append(path(f"M{cx - 38} {cy - H - 44} L{cx + 38} {cy - H - 44}", stroke="#2A1608", stroke_width="5", stroke_linecap="round"))
    o.append(path(f"M{cx - 38} {cy - H - 45} L{cx + 38} {cy - H - 45}", stroke="#8A5A32", stroke_width="1.4"))
    roof = f"M{cx - 44} {cy - H - 44} Q{cx} {cy - H - 50} {cx + 44} {cy - H - 44} L{cx + 30} {cy - H - 60} L{cx - 30} {cy - H - 60} Z"
    o.append(path(roof, fill="url(#r2-roofF)", **OL))
    for k in range(15):
        x = cx - 40 + k * 5.7
        o.append(path(f"M{f(x)} {f(cy - H - 45)} L{f(cx - 28 + k * 4)} {f(cy - H - 59)}", stroke=("#1A1B20" if k % 2 == 0 else "#7A808C"), stroke_width=("2" if k % 2 == 0 else "0.7")))
    o.append(path(f"M{cx - 32} {cy - H - 60} L{cx + 32} {cy - H - 60}", stroke="#5A5E68", stroke_width="3.4", stroke_linecap="round"))
    # pulley + rope + bucket
    o.append(circ(cx, cy - H - 38, 4, fill="url(#r2-club)", **OL))
    o.append(path(f"M{cx + 2} {cy - H - 38} L{cx + 2} {cy - H - 14}", stroke="url(#r2-rope)", stroke_width="1.4"))
    o.append(path(f"M{cx - 5} {cy - H - 14} L{cx + 9} {cy - H - 14} L{cx + 7} {cy - H - 4} L{cx - 3} {cy - H - 4} Z", fill="url(#r2-timberL)", **OL))
    # wooden trough + jar
    o.append(poly([(54, 132), (72, 141), (72, 146), (54, 137)], fill="url(#r2-timberL)", **OL))
    o.append(ell(146, 134, 7, 8, fill="url(#r2-jar)", **OL))
    o.append(ell(146, 127, 4, 1.6, fill="#1A0C06"))
    return bsvg("".join(o), "귀화 우물")


def thatch_roof(iso, W, D, H, R, o_=7, seed=1):
    rr = random.Random(seed)
    o = []
    back = [iso.p(-o_, -o_, H), iso.p(W + o_, -o_, H), iso.p(W + o_, D / 2, H + R), iso.p(-o_, D / 2, H + R)]
    o.append(poly(back, fill="url(#r2-thatchB)", **OL))
    ped = [iso.p(W, 0, H), iso.p(W, D, H), iso.p(W, D / 2, H + R * 0.9)]
    o.append(poly(ped, fill="url(#r2-plasterR)", **OL))
    # rounded gable end of thatch
    o.append(path(f"M{P(iso.p(W + o_, -o_, H))} Q{P(iso.p(W + o_ + 3, D / 2, H + R + 6))} {P(iso.p(W + o_, D + o_, H))}", fill="none", stroke="#7A6438", stroke_width="4.5"))
    e0, e1 = iso.p(-o_, D + o_, H - 2), iso.p(W + o_, D + o_, H - 2)
    r0, r1 = iso.p(-o_, D / 2, H + R + 3), iso.p(W + o_, D / 2, H + R + 3)
    front_d = f"M{P(e0)} L{P(e1)} Q{f((e1[0] + r1[0]) / 2 + 4)} {f((e1[1] + r1[1]) / 2 - 4)} {P(r1)} L{P(r0)} Q{f((e0[0] + r0[0]) / 2 - 3)} {f((e0[1] + r0[1]) / 2 - 3)} {P(e0)} Z"
    o.append(path(front_d, fill="url(#r2-thatchF)", **OL))
    cid = uid("th")
    straw = []
    n = 70
    for k in range(n):
        t = rr.random()
        u = -o_ + (W + 2 * o_) * t
        a, b = iso.p(u, D + o_, H - 2), iso.p(u, D / 2, H + R + 3)
        q0, q1 = rr.uniform(0, 0.8), 0
        q1 = min(1, q0 + rr.uniform(0.1, 0.3))
        sa = (a[0] + (b[0] - a[0]) * q0, a[1] + (b[1] - a[1]) * q0)
        sb = (a[0] + (b[0] - a[0]) * q1, a[1] + (b[1] - a[1]) * q1)
        straw.append(path(f"M{P(sa)} L{P(sb)}", stroke=rr.choice(("#E8D098", "#8A6E3C", "#C4A464", "#5E4A26")), stroke_width="0.7", opacity="0.85"))
    # rope net
    for k in range(7):
        u = -o_ + (W + 2 * o_) * k / 6
        a, b = iso.p(u, D + o_, H - 2), iso.p(u, D / 2, H + R + 3)
        straw.append(path(f"M{P(a)} L{P(b)}", stroke="#4A3818", stroke_width="1.2", opacity="0.8"))
    for k in range(1, 4):
        q = k / 4
        a = (e0[0] + (r0[0] - e0[0]) * q, e0[1] + (r0[1] - e0[1]) * q)
        b = (e1[0] + (r1[0] - e1[0]) * q, e1[1] + (r1[1] - e1[1]) * q)
        straw.append(path(f"M{P(a)} L{P(b)}", stroke="#4A3818", stroke_width="1.2", opacity="0.8"))
    straw.append(el("rect", x="0", y="0", width="200", height="170", fill="url(#r2-shadeR)", opacity="0.5"))
    o.append(f'<clipPath id="{cid}"><path d="{front_d}"></path></clipPath><g clip-path="url(#{cid})">{"".join(straw)}</g>')
    # ragged eave fringe
    for k in range(40):
        t = k / 39
        x, y = e0[0] + (e1[0] - e0[0]) * t, e0[1] + (e1[1] - e0[1]) * t
        o.append(path(f"M{f(x)} {f(y - 1)} l{f(rr.uniform(-1, 1))} {f(rr.uniform(2, 4.5))}", stroke=rr.choice(("#C4A464", "#6A5228")), stroke_width="0.9"))
    o.append(path(f"M{P(r0)} L{P(r1)}", stroke="#5E4A26", stroke_width="4", stroke_linecap="round"))
    return "".join(o)


def bld_forge():
    o = [diorama(100, 130, 88, 32, 12, seed=23)]
    iso = Iso(92, 78, 1.0)
    o.append(shadow(118, 128, 46, 12, 0.6))
    W, D, H = 56, 40, 30
    o.append(wall_face(iso, (0, D), (W, D), H, "L", posts=3, door=(0.38, 0.72, 0.8, True), seed=2))
    o.append(wall_face(iso, (W, D), (W, 0), H, "R", posts=2, window=(0.25, 0.75, 0.32, 0.72), seed=3))
    o.append(thatch_roof(iso, W, D, H, 22, seed=4))
    # chimney + smoke
    c = iso.p(W - 10, 6, H + 24)
    o.append(el("rect", x=f(c[0] - 4), y=f(c[1] - 12), width="8", height="16", fill="url(#r2-stoneL)", **OL))
    for k, (dx, dy, r) in enumerate(((2, -20, 7), (6, -32, 9), (12, -46, 10))):
        o.append(circ(c[0] + dx, c[1] + dy, r, fill="url(#r2-smokeg)", opacity="0.75", filter="url(#r2-smoke)"))
    # anvil on stump
    a = iso.p(20, D + 22, 0)
    o.append(ell(a[0], a[1], 8, 4, fill="url(#r2-club)", **OL))
    o.append(path(f"M{f(a[0] - 8)} {f(a[1])} L{f(a[0] - 8)} {f(a[1] - 6)} A8 4 0 0 1 {f(a[0] + 8)} {f(a[1] - 6)} L{f(a[0] + 8)} {f(a[1])}", fill="url(#r2-timberL)", **OL))
    o.append(path(f"M{f(a[0] - 9)} {f(a[1] - 10)} L{f(a[0] + 10)} {f(a[1] - 10)} L{f(a[0] + 6)} {f(a[1] - 6)} L{f(a[0] - 5)} {f(a[1] - 6)} Z M{f(a[0] + 10)} {f(a[1] - 10)} l5 1 l-5 2 Z", fill="url(#r2-iron)", **OL))
    o.append(sparks(a[0], a[1] - 16, 10, 6, 8, 3, color="#BFE8FF"))
    # club rack
    for k in range(4):
        x = 150 + k * 6
        o.append(club(x, 128 - k * 2, -100 + k * 4, 30, 1.8, 4.6, seed=20 + k, studs=k % 2 == 0))
    o.append(path("M144 104 L176 98", stroke="#2A1608", stroke_width="2.4"))
    # water barrel + coal pile
    o.append(ell(46, 130, 7, 3.4, fill="#1A2A34", **OL))
    o.append(path("M39 130 L39 120 A7 3.4 0 0 1 53 120 L53 130", fill="url(#r2-timberL)", **OL))
    o.append(ell(46, 120, 7, 3.4, fill="#2A4A5A", **OL))
    rr = random.Random(9)
    for _ in range(12):
        o.append(circ(64 + rr.uniform(-8, 8), 142 + rr.uniform(-3, 3), rr.uniform(1.4, 2.6), fill="#141210", stroke="#3A3630", stroke_width="0.4"))
    return bsvg("".join(o), "방망이 대장간")


def stone_plinth(iso, W, D, H, seed=1):
    rr = random.Random(seed)
    o = [iso.poly([(0, D, 0), (W, D, 0), (W, D, H), (0, D, H)], fill="url(#r2-stoneL)", **OL),
         iso.poly([(W, D, 0), (W, 0, 0), (W, 0, H), (W, D, H)], fill="url(#r2-stoneR)", **OL),
         iso.poly([(0, 0, H), (W, 0, H), (W, D, H), (0, D, H)], fill="url(#r2-stoneT)", **OL)]
    for face in ("L", "R"):
        n = int((W if face == "L" else D) / 9)
        for row in range(2):
            z = H * row / 2
            for k in range(n + 1):
                s = (k + 0.5 * row) / n
                if s > 1:
                    continue
                a = iso.p(W * s, D, z) if face == "L" else iso.p(W, D * (1 - s), z)
                b = iso.p(W * s, D, z + H / 2) if face == "L" else iso.p(W, D * (1 - s), z + H / 2)
                o.append(path(f"M{P(a)} L{P(b)}", stroke="#1A1612", stroke_width="0.7", opacity="0.7"))
            a = iso.p(0, D, z) if face == "L" else iso.p(W, D, z)
            b = iso.p(W, D, z) if face == "L" else iso.p(W, 0, z)
            o.append(path(f"M{P(a)} L{P(b)}", stroke="#1A1612", stroke_width="0.7", opacity="0.7"))
    for _ in range(6):
        u, v = rr.uniform(4, W - 4), rr.uniform(4, D - 4)
        c = iso.p(u, v, H)
        o.append(ell(c[0], c[1], rr.uniform(1, 3), 0.8, fill="#5A564E", opacity="0.4"))
    return "".join(o)


def bld_vault():
    o = [diorama(100, 132, 88, 32, 12, seed=24)]
    iso = Iso(96, 84, 1.0)
    o.append(shadow(118, 130, 48, 12, 0.6))
    W, D = 52, 40
    o.append(f'<g transform="translate(0 0)">{stone_plinth(iso, W + 8, D + 8, 7, seed=2)}</g>')
    iso2 = Iso(96 + 4 - 4, 84 + 4 - 7, 1.0)
    # red posts walls
    H = 26
    o.append(wall_face(iso2, (0, D), (W, D), H, "L", posts=3, door=(0.36, 0.64, 0.82, False), window=None, seed=4))
    o.append(wall_face(iso2, (W, D), (W, 0), H, "R", posts=2, window=(0.25, 0.75, 0.3, 0.75), seed=5))
    for k in range(4):
        s = k / 3
        a, b = iso2.p(W * s, D, 0), iso2.p(W * s, D, H)
        o.append(path(f"M{P(a)} L{P(b)}", stroke="url(#r2-redpost)", stroke_width="3.4"))
    # door lattice
    for k in range(1, 5):
        s = 0.36 + 0.28 * k / 5
        a, b = iso2.p(W * s, D, H * 0.16), iso2.p(W * s, D, H * 0.8)
        o.append(path(f"M{P(a)} L{P(b)}", stroke="#C49A5A", stroke_width="0.6"))
    o.append(dancheong_band(iso2, (0, D), (W, D), H - 3, H, "L"))
    o.append(dancheong_band(iso2, (W, D), (W, 0), H - 3, H, "R"))
    o.append(giwa_roof(iso2, W, D, H, 20, o_=9, curl=5))
    # plaque
    pq = iso2.p(W * 0.5, D + 9, H + 1)
    o.append(el("rect", x=f(pq[0] - 9), y=f(pq[1] - 5), width="18", height="8", fill="#1A120A", stroke="url(#r2-brass)", stroke_width="1"))
    o.append(el("text", "감투", x=f(pq[0]), y=f(pq[1] + 1.4), font_size="5.2", fill=YEL, text_anchor="middle", font_family="Nanum Myeongjo, serif", font_weight="800"))
    # stone lanterns
    for u in (-12, W + 14):
        b = iso.p(u, D + 18, 0)
        o.append(el("rect", x=f(b[0] - 3), y=f(b[1] - 14), width="6", height="14", fill="url(#r2-stoneL)", **OL))
        o.append(path(f"M{f(b[0] - 6)} {f(b[1] - 14)} L{f(b[0] + 6)} {f(b[1] - 14)} L{f(b[0] + 4)} {f(b[1] - 22)} L{f(b[0] - 4)} {f(b[1] - 22)} Z", fill="url(#r2-stoneL)", **OL))
        o.append(flame(b[0], b[1] - 17, 0.18, seed=int(u) + 50))
        o.append(path(f"M{f(b[0] - 7)} {f(b[1] - 22)} L{f(b[0])} {f(b[1] - 28)} L{f(b[0] + 7)} {f(b[1] - 22)} Z", fill="url(#r2-stoneT)", **OL))
    # floating hat (감투) relic above door
    h = iso2.p(W * 0.5, D + 20, 12)
    o.append(circ(h[0], h[1], 12, fill="url(#r2-halo)"))
    o.append(ell(h[0], h[1] + 2, 9, 2.6, fill="#0E0E14", stroke="#3A3A48", stroke_width="0.6"))
    o.append(path(f"M{f(h[0] - 5)} {f(h[1] + 1)} Q{f(h[0] - 5)} {f(h[1] - 8)} {f(h[0])} {f(h[1] - 8)} Q{f(h[0] + 5)} {f(h[1] - 8)} {f(h[0] + 5)} {f(h[1] + 1)} Z", fill="#14141C", stroke="#4A4A5A", stroke_width="0.6"))
    o.append(path(f"M{f(h[0] - 3)} {f(h[1] - 5)} Q{f(h[0] - 2)} {f(h[1] - 7)} {f(h[0])} {f(h[1] - 7)}", stroke="#8A8AA8", stroke_width="0.7", fill="none"))
    return bsvg("".join(o), "감투 창고")


def bld_ember_tower():
    o = [diorama(100, 134, 70, 26, 11, seed=25)]
    o.append(shadow(112, 132, 30, 9, 0.6))
    rr = random.Random(3)
    y = 132
    stones = []
    for k in range(9):
        rx = 26 - k * 2.2 + rr.uniform(-1.5, 1.5)
        ry = rx * 0.42
        th = 7 - k * 0.3
        x = 100 + rr.uniform(-2.5, 2.5)
        stones.append((x, y, rx, ry, th))
        y -= th + 1
    for (x, yy, rx, ry, th) in stones:
        o.append(path(f"M{f(x - rx)} {f(yy - th)} L{f(x - rx)} {f(yy)} A{f(rx)} {f(ry)} 0 0 0 {f(x + rx)} {f(yy)} L{f(x + rx)} {f(yy - th)} Z", fill="url(#r2-stoneL)", **OL))
        o.append(path(f"M{f(x + rx * 0.2)} {f(yy - th + ry)} L{f(x + rx)} {f(yy - th)} L{f(x + rx)} {f(yy)} A{f(rx)} {f(ry)} 0 0 1 {f(x + rx * 0.2)} {f(yy + ry * 0.98)} Z", fill="#000", opacity="0.3"))
        o.append(ell(x, yy - th, rx, ry, fill="url(#r2-stoneT)", **OL))
        for _ in range(3):
            o.append(ell(x + rr.uniform(-rx * 0.6, rx * 0.6), yy - th + rr.uniform(-ry * 0.4, ry * 0.4), rr.uniform(1, 2.6), 0.7, fill="#5A564E", opacity="0.5"))
    top = stones[-1]
    bx, by = top[0], top[1] - top[4]
    # stone bowl
    o.append(path(f"M{f(bx - 12)} {f(by - 6)} Q{f(bx)} {f(by + 6)} {f(bx + 12)} {f(by - 6)} Z", fill="url(#r2-stoneL)", **OL))
    o.append(ell(bx, by - 6, 12, 4, fill="#0A121A", **OL))
    o.append(flame(bx, by - 18, 0.85, seed=21))
    o.append(geumjul([(76, 108), (88, 112), (100, 113), (112, 112), (124, 108)], seed=4))
    # small cairns around
    for x, yy in ((56, 138), (144, 138), (70, 150)):
        for k in range(3):
            o.append(ell(x, yy - k * 4, 6 - k * 1.5, 2.6 - k * 0.5, fill="url(#r2-stoneT)", **OL))
    # range ring
    o.append(ell(100, 134, 66, 24, fill="none", stroke=BLUE, stroke_width="1", stroke_dasharray="3 4", opacity="0.6"))
    o.append(sparks(bx, by - 40, 20, 18, 14, 5))
    return bsvg("".join(o), "불씨 탑")


def jar(cx, cy, s, glow=False, seed=1):
    rr = random.Random(seed)
    d = (f"M{f(cx - 5 * s)} {f(cy)} C{f(cx - 12 * s)} {f(cy - 4 * s)} {f(cx - 13 * s)} {f(cy - 16 * s)} {f(cx - 8 * s)} {f(cy - 20 * s)} "
         f"L{f(cx - 6 * s)} {f(cy - 22 * s)} L{f(cx + 6 * s)} {f(cy - 22 * s)} L{f(cx + 8 * s)} {f(cy - 20 * s)} "
         f"C{f(cx + 13 * s)} {f(cy - 16 * s)} {f(cx + 12 * s)} {f(cy - 4 * s)} {f(cx + 5 * s)} {f(cy)} Z")
    o = [ell(cx + 3 * s, cy + 1, 11 * s, 3.4 * s, fill="#000", opacity="0.45", filter="url(#r2-soft)"),
         path(d, fill="url(#r2-jar)", **OL)]
    # glaze drips and highlight
    o.append(path(f"M{f(cx - 9 * s)} {f(cy - 13 * s)} Q{f(cx)} {f(cy - 10 * s)} {f(cx + 10 * s)} {f(cy - 13 * s)}", fill="none", stroke="#1A0A04", stroke_width=f(0.8 * s), opacity="0.6"))
    o.append(ell(cx - 5 * s, cy - 14 * s, 1.8 * s, 4.5 * s, fill="#FFF2DA", opacity="0.5", transform=f"rotate(-14 {f(cx - 5 * s)} {f(cy - 14 * s)})"))
    o.append(circ(cx - 5.4 * s, cy - 16 * s, 0.9 * s, fill="#FFFFFF", opacity="0.85"))
    o.append(path(f"M{f(cx + 8 * s)} {f(cy - 17 * s)} Q{f(cx + 11 * s)} {f(cy - 9 * s)} {f(cx + 6 * s)} {f(cy - 2 * s)}", fill="none", stroke=BLUE_L, stroke_width=f(0.9 * s), opacity="0.45"))
    # lid
    o.append(ell(cx, cy - 22.5 * s, 8 * s, 2.6 * s, fill="#4A2814", **OL))
    o.append(ell(cx, cy - 23.5 * s, 6.5 * s, 2 * s, fill="#6A3A20"))
    o.append(circ(cx, cy - 24.5 * s, 1.4 * s, fill="#3A2010"))
    if glow:
        o.append(flame(cx, cy - 27 * s, 0.3 * s, seed=seed))
    return "".join(o)


def bld_jars():
    o = [diorama(100, 132, 88, 32, 12, seed=26)]
    iso = Iso(100, 92, 1.0)
    o.append(shadow(116, 128, 48, 12, 0.55))
    o.append(stone_plinth(iso, 60, 46, 9, seed=5))
    rows = []
    rr = random.Random(4)
    for v in (8, 22, 36):
        for u in (8, 22, 36, 50):
            if rr.random() < 0.12:
                continue
            p = iso.p(u, v, 9)
            rows.append((p[1], p[0], 0.7 + rr.random() * 0.45))
    for i, (yy, xx, s) in enumerate(sorted(rows)):
        o.append(jar(xx, yy + 3, s, glow=(i == 5), seed=10 + i))
    # straw mat + gourd dipper
    o.append(poly([(40, 140), (62, 150), (54, 154), (32, 144)], fill="#B89A5A", **OL))
    for k in range(6):
        o.append(path(f"M{36 + k * 3.6} {142 + k * 1.6} L{58 + k * 0} {152}", stroke="#7A6030", stroke_width="0.5", opacity="0"))
    for k in range(6):
        o.append(path(f"M{f(34 + k * 4)} {f(143 + k * 1.8)} l-3 1.6", stroke="#6A5228", stroke_width="0.6"))
    o.append(ell(150, 146, 6, 3, fill="url(#r2-club)", **OL))
    o.append(path("M154 145 L166 138", stroke="#5A3A1E", stroke_width="1.6", stroke_linecap="round"))
    return bsvg("".join(o), "장독대")


def bld_ring():
    o = [diorama(100, 128, 96, 34, 12, seed=27)]
    cx, cy, R = 100, 124, 62
    o.append(shadow(108, 128, R, R * 0.4, 0.5))
    # raised sand mound
    o.append(path(f"M{cx - R} {cy} L{cx - R} {cy - 6} A{R} {R * 0.42} 0 0 1 {cx + R} {cy - 6} L{cx + R} {cy} A{R} {R * 0.42} 0 0 1 {cx - R} {cy} Z", fill="#7A6644", **OL))
    o.append(path(f"M{cx - R} {cy - 6} L{cx - R} {cy} A{R} {R * 0.42} 0 0 0 {cx + R} {cy} L{cx + R} {cy - 6} A{R} {R * 0.42} 0 0 1 {cx - R} {cy - 6} Z", fill="url(#r2-soilL)", **OL))
    o.append(ell(cx, cy - 6, R, R * 0.42, fill="url(#r2-sand)", **OL))
    cid = uid("sd")
    rr = random.Random(6)
    g = [el("rect", x=f(cx - R), y=f(cy - 40), width=f(2 * R), height="70", fill="#000", filter="url(#r2-grain)")]
    for k in range(1, 6):
        g.append(ell(cx, cy - 6, R * k / 6, R * 0.42 * k / 6, fill="none", stroke="#8A7650", stroke_width="0.6", opacity="0.5"))
    for _ in range(30):
        g.append(circ(cx + rr.uniform(-R, R), cy - 6 + rr.uniform(-20, 20), 0.6, fill="#5A4A2E", opacity="0.6"))
    # footprints scuffs
    for k in range(6):
        x, y = cx + rr.uniform(-30, 30), cy - 6 + rr.uniform(-10, 10)
        g.append(ell(x, y, 3, 1.4, fill="#8A7650", opacity="0.6", transform=f"rotate({rr.randint(-30, 30)} {f(x)} {f(y)})"))
    o.append(f'<clipPath id="{cid}"><ellipse cx="{cx}" cy="{cy - 6}" rx="{R}" ry="{f(R * 0.42)}"></ellipse></clipPath><g clip-path="url(#{cid})">{"".join(g)}</g>')
    # twisted straw rope boundary
    o.append(ell(cx, cy - 7, R * 0.9, R * 0.38, fill="none", stroke="#3A2A10", stroke_width="4.6"))
    o.append(ell(cx, cy - 7, R * 0.9, R * 0.38, fill="none", stroke="url(#r2-rope)", stroke_width="3.4"))
    o.append(ell(cx, cy - 7, R * 0.9, R * 0.38, fill="none", stroke="#5A441C", stroke_width="3.4", stroke_dasharray="1.4 1.8"))
    # flag poles at four corners (오방색 banners)
    for (x, y, col) in ((cx - R + 4, cy - 14, RED), (cx - 18, cy - 34, YEL), (cx + 30, cy - 32, "#2F6E8E"), (cx + R - 6, cy - 10, WHITE5)):
        o.append(path(f"M{x} {y} L{x} {y - 46}", stroke="#2A1608", stroke_width="2.2"))
        o.append(path(f"M{x} {y} L{x} {y - 46}", stroke="#8A5A32", stroke_width="0.8"))
        o.append(path(f"M{x} {y - 45} Q{x + 10} {y - 48} {x + 18} {y - 44} L{x + 16} {y - 32} Q{x + 8} {y - 36} {x} {y - 32} Z", fill=col, **OL))
        o.append(path(f"M{x} {y - 45} Q{x + 10} {y - 48} {x + 18} {y - 44} L{x + 16} {y - 32} Q{x + 8} {y - 36} {x} {y - 32} Z", fill="url(#r2-cloth-shade)"))
        for k in range(5):
            o.append(path(f"M{x + 3 + k * 3} {f(y - 33 - k * 0.4)} l0.4 3", stroke=col, stroke_width="0.9"))
        o.append(circ(x, y - 47, 1.6, fill="url(#r2-brass)"))
    # drum on stand + braziers
    o.append(ell(170, 138, 9, 4, fill="#8A2A1A", **OL))
    o.append(path("M161 138 L161 128 A9 4 0 0 1 179 128 L179 138", fill="url(#r2-redpost)", **OL))
    o.append(ell(170, 128, 9, 4, fill="#E8D8B0", **OL))
    o.append(path("M164 134 L168 128 L172 134 L176 128", fill="none", stroke=YEL, stroke_width="0.8"))
    for x in (34, 100):
        y = 150 if x == 34 else 160
        o.append(path(f"M{x - 5} {y} L{x - 3} {y - 8} L{x + 3} {y - 8} L{x + 5} {y}", fill="url(#r2-iron)", **OL))
        o.append(flame(x, y - 12, 0.3, seed=x))
    return bsvg("".join(o), "씨름판")


# ---------------------------------------------------------------- crest
def crest():
    o = ['<svg width="96" height="96" viewBox="0 0 100 100" role="img" aria-label="요물 무리 문장">']
    o.append(circ(50, 50, 48, fill="url(#r2-brass)", stroke="#2A1A08", stroke_width="1.4"))
    for k in range(24):
        a = math.tau * k / 24
        o.append(circ(50 + math.cos(a) * 44, 50 + math.sin(a) * 44, 1.4, fill="#FFF0C0" if k % 2 else "#5A3A10"))
    o.append(circ(50, 50, 40, fill="#1A0E08", stroke="#6A4628", stroke_width="1.4"))
    o.append(circ(50, 50, 38, fill="#0E1620"))
    # 구름문 ring
    for k in range(8):
        a = math.tau * k / 8
        x, y = 50 + math.cos(a) * 32, 50 + math.sin(a) * 32
        o.append(path(f"M{f(x - 3)} {f(y)} a3 3 0 1 1 3 3 a1.6 1.6 0 1 0 -1.6 -1.6", fill="none", stroke=YEL, stroke_width="0.9", opacity="0.75"))
    o.append(circ(50, 52, 28, fill="url(#r2-halo)"))
    o.append(flame(50, 66, 1.0, halo=False, seed=33))
    o.append(dhead(50, 52, 13, SKB, horns=[(-0.45, -0.75, -0.8, -1.7, 0.3, -0.12), (0.45, -0.75, 0.8, -1.7, 0.3, 0.12)], grin=1.0, seed=61, look="codex"))
    o.append("</svg>")
    return "".join(o)


# ---------------------------------------------------------------- FX strip (viewBox 0 0 160 118)
def fsvg(body, label):
    return f'<svg width="100%" height="108" viewBox="0 0 160 118" preserveAspectRatio="xMidYMid meet" role="img" aria-label="{label}">{body}</svg>'


def fx_ground(seed, dark=False):
    return diorama(80, 92, 76, 22, 7, seed=seed, density=0.7, dark=dark)


def burn_patch(cx, cy, s=1.0, seed=1):
    o = [ell(cx, cy, 18 * s, 6 * s, fill="url(#r2-scorch)")]
    for k in range(3):
        o.append(flame(cx + (k - 1) * 8 * s, cy - 2 * s, 0.32 * s + (k % 2) * 0.08, halo=(k == 1), seed=seed + k))
    return "".join(o)


def _fallen(x, y, s=0.62, m=None):
    """A struck-down enemy lying on the ground."""
    m = m or Mat("#5E4A3C", "#A08A72", "#120C08", "#44586A", spec_op=0.15, gloss=0.15)
    return f'<g transform="translate({x} {y}) rotate(-80) scale({s})">{Y.risen_soul(K, m)}</g>'


def fx_frames():
    frames = []
    # 1 the soul gathers in the long hand
    b = fx_ground(71) + f'<g transform="translate(96 96) scale(0.54)">{Y.changgwi(K, "charge", ground=False)}</g>'
    frames.append(("혼 모으기", b))
    # 2 the soul-bolt leaves
    b = fx_ground(72) + f'<g transform="translate(116 96) scale(0.52)">{Y.changgwi(K, "fire", ground=False)}</g>'
    b += path("M72 34 Q50 40 28 72", fill="none", stroke=BLUE_L, stroke_width="1", stroke_dasharray="3 3", opacity="0.65")
    b += Y.soul_bolt(K, 36, 58, 0.75, trail=(24, -18))
    frames.append(("혼령탄", b))
    # 3 it strikes: claw marks open in the air over the target
    b = fx_ground(73) + _fallen(52, 84)
    b += ell(80, 88, 44, 12, fill="url(#r2-pool)")
    b += circ(80, 66, 26, fill="url(#r2-halo)")
    for k in range(3):
        x0 = 66 + k * 12
        b += path(f"M{x0} 34 Q{x0 + 3} 58 {x0 - 8} 88", fill="none", stroke="#F6C46A", stroke_width="4", stroke_linecap="round", filter="url(#r2-glow)", opacity="0.95")
        b += path(f"M{x0} 34 Q{x0 + 3} 58 {x0 - 8} 88", fill="none", stroke="#FFFFFF", stroke_width="1.2", stroke_linecap="round")
    b += sparks(80, 62, 36, 22, 16, 9, color="#F6C46A")
    frames.append(("명중", b))
    # 4 the fallen soul is dragged up to fight for the tiger, for a while
    b = fx_ground(74) + _fallen(70, 92)
    b += ell(80, 92, 34, 9, fill="#F6C46A", opacity="0.3", filter="url(#r2-blur5)")
    b += f'<g transform="translate(88 86) scale(0.82)" opacity="0.82">{Y.risen_soul(K)}</g>'
    b += path("M64 92 Q70 74 84 66", fill="none", stroke="#F6C46A", stroke_width="1", stroke_dasharray="2 2", opacity="0.8")
    b += sparks(88, 44, 28, 22, 12, 12, color="#F6C46A")
    frames.append(("끌어들임", b))
    return frames


def death_frame():
    b = fx_ground(75)
    for k, (x, y, r) in enumerate(((70, 66, 18), (92, 60, 20), (82, 46, 16), (104, 74, 14), (60, 80, 12))):
        b += circ(x, y, r, fill="url(#r2-smokeg)", filter="url(#r2-smoke)", opacity="0.9")
    b += '<g opacity="0.7">' + K.arm((96, 70), (106, 56), (102, 38), SKB_D, w=5.5, muscular=1, hand="open", hand_m=SKB_D) + '</g>'
    b += circ(96, 72, 12, fill="url(#r2-smokeg)", filter="url(#r2-smoke)", opacity="0.9")
    b += kclub((62, 94), -8, 52, 2.4, 6.8, seed=31)
    b += sparks(84, 52, 40, 24, 16, 13)
    b += el("text", "펑!", x="112", y="34", font_size="15", fill=BLUE_L, font_family="Nanum Myeongjo, serif", font_weight="800", transform="rotate(-10 112 34)")
    return fsvg(b, "사망")


def destroyed_frame():
    b = fx_ground(76, dark=True)
    b += ell(80, 92, 48, 14, fill="url(#r2-scorch)")
    for x0, lean in ((66, -14), (92, 12)):
        d = f"M{x0 - 10} 96 C{x0 - 8} 80 {x0 + lean - 6} 64 {x0 + lean - 4} 50 L{x0 + lean + 2} 58 L{x0 + lean + 6} 46 L{x0 + 8} 72 L{x0 + 10} 96 Z"
        b += path(d, fill="#1E140C", **OL)
        b += path(d, fill="url(#r2-shadeR)")
        for k in range(5):
            b += path(f"M{x0 - 8 + k * 4} 94 Q{x0 - 6 + k * 4 + lean * 0.4} 78 {x0 - 4 + k * 3 + lean * 0.7} 62", fill="none", stroke="#F08A3A" if k == 2 else "#0A0604", stroke_width="0.7", opacity="0.7")
    b += ell(80, 90, 10, 4, fill=BLUE, opacity="0.5", filter="url(#r2-blur5)")
    for k, (x, y, r) in enumerate(((76, 34, 12), (90, 22, 10), (70, 16, 8))):
        b += circ(x, y, r, fill="url(#r2-smokeg)", filter="url(#r2-smoke)", opacity="0.85")
    b += geumjul([(40, 100), (52, 104), (62, 102)], seed=8)
    b += sparks(80, 60, 26, 20, 12, 15, color="#F6B86A")
    return fsvg(b, "파괴")


def mini_wisp(x, y, s=0.6, seed=1, arms_to=None):
    o = [flame(x, y, 1.1 * s * 1.6, seed=seed)]
    for sx in (-1, 1):
        o.append(ell(x + sx * 4 * s, y - 2 * s, 1.6 * s, 2 * s, fill="#0B2A48"))
    o.append(horn(x - 5 * s, y - 14 * s, x - 9 * s, y - 22 * s, 3 * s, -1))
    o.append(horn(x + 5 * s, y - 14 * s, x + 9 * s, y - 22 * s, 3 * s, 1))
    if arms_to is not None:
        o.append(wisp_arms(x, y - 12 * s, s * 0.55, arms_to))
    return "".join(o)


def harvest_frame():
    b = fx_ground(77) + ore(62, 90, 1.15, seed=9, n=7)
    b += path("M80 70 Q96 58 108 64", fill="none", stroke=BLUE_L, stroke_width="1.2", stroke_dasharray="2 2", opacity="0.8")
    b += ore(108, 70, 0.4, seed=12, glow=True, n=3)
    b += mini_wisp(110, 58, 0.9, seed=3)
    b += sparks(80, 60, 30, 16, 10, 21, color=ORE_GLOW)
    return fsvg(b, "채취")


def dropoff_frame():
    b = fx_ground(78)
    trunk = "M96 98 C98 80 100 62 96 40 L140 40 C134 62 136 80 140 98 Z"
    b += path(trunk, fill="url(#r2-bark)", **OL)
    b += path("M108 98 C106 84 108 72 118 66 C128 72 130 84 128 98 Z", fill="url(#r2-hollow)")
    b += geumjul([(96, 62), (108, 66), (122, 67), (138, 62)], seed=9)
    b += ore(84, 86, 0.35, seed=4, glow=True, n=3) + mini_wisp(84, 72, 0.8, seed=5)
    b += path("M92 76 Q102 78 110 84", fill="none", stroke=BLUE_L, stroke_width="1", stroke_dasharray="2 2")
    b += el("text", "+5", x="40", y="40", font_size="15", fill=ORE_GLOW, font_family="IBM Plex Mono, monospace", font_weight="600")
    b += el("text", "귀화석", x="40", y="54", font_size="9", fill="#CFC5AE", font_family="IBM Plex Sans KR, sans-serif")
    return fsvg(b, "반납")


# ---------------------------------------------------------------- page chrome
MONO = "font-family: 'IBM Plex Mono', monospace"
MYEONG = "font-family: 'Nanum Myeongjo', serif"
GOLD_TEXT = ("background: linear-gradient(180deg, #FFF0C0 0%, #E8C46A 45%, #A8741E 100%); -webkit-background-clip: text; background-clip: text; "
             "color: transparent; filter: drop-shadow(0 2px 0 #1A0F06) drop-shadow(0 0 6px rgba(0,0,0,0.6))")
FRAME = ("position: relative; display: flex; flex-direction: column; min-width: 0; padding: 6px; "
         "background: linear-gradient(180deg, #3E2716 0%, #2A190D 55%, #1E1209 100%); border: 1px solid #0A0603; "
         "box-shadow: inset 0 0 0 1px #6E4A2A, inset 0 0 0 4px #24160C, inset 0 0 0 5px #8A6234, 0 6px 14px rgba(0,0,0,0.55)")
ART = ("position: relative; display: flex; align-items: center; justify-content: center; overflow: hidden; "
       "background: radial-gradient(ellipse at 50% 42%, #2A3442 0%, #161A20 58%, #0C0D10 100%); border: 1px solid #0A0603; "
       "box-shadow: inset 0 0 22px rgba(0,0,0,0.75)")
PARCH = ("position: relative; overflow: hidden; background: linear-gradient(180deg, #F1E6C8 0%, #E2D2A8 100%); color: #2A1E12; "
         "border-top: 2px solid #8A6234; box-shadow: inset 0 1px 0 #FFF6DA")


def corners():
    one = ('<svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true" style="position: absolute; {pos}; transform: {rot}">'
           '<path d="M1 1 H16 L12 5 H5 V12 L1 16 Z" fill="url(#r2-brass)" stroke="#2A1608" stroke-width="0.8"></path>'
           '<path d="M2.2 2.2 H13.6" stroke="#FFF2C8" stroke-width="0.7" opacity="0.8"></path>'
           '<circle cx="4.2" cy="4.2" r="1.7" fill="url(#r2-stud)" stroke="#2A1608" stroke-width="0.4"></circle></svg>')
    return "".join(one.format(pos=p, rot=r) for p, r in (("left: -2px; top: -2px", "none"), ("right: -2px; top: -2px", "rotate(90deg)"),
                                                          ("right: -2px; bottom: -2px", "rotate(180deg)"), ("left: -2px; bottom: -2px", "rotate(270deg)")))


def paper_grain():
    return ('<svg width="100%" height="100%" aria-hidden="true" style="position: absolute; inset: 0; pointer-events: none">'
            '<rect width="100%" height="100%" fill="#000" filter="url(#r2-paper)"></rect></svg>')


def cloud(w=34, flip=False):
    t = ' style="transform: scaleX(-1)"' if flip else ""
    return (f'<svg width="{w}" height="16" viewBox="0 0 34 16" aria-hidden="true"{t}>'
            '<path d="M2 12 C2 6 8 4 11 8 C11 3 18 2 20 7 C22 4 28 5 28 9 C32 9 33 12 31 13 L4 13 Z" fill="none" stroke="#E2B13C" stroke-width="1.2"></path>'
            '<path d="M8 10 a2.2 2.2 0 1 1 2.2 2.2 M17 9 a2.4 2.4 0 1 1 2.4 2.4" fill="none" stroke="#E2B13C" stroke-width="0.9"></path></svg>')


UNIT_ART_H = 172


def h2(text, note=""):
    n = f'<span style="font-size: 12px; font-weight: 400; color: #A89E8A; letter-spacing: 0">{note}</span>' if note else ""
    return (f'<h2 style="margin: 0; display: flex; gap: 10px; align-items: center; font-size: 15px">{cloud()}'
            f'<span style="{MYEONG}; font-weight: 800; font-size: 17px; letter-spacing: 0.06em; {GOLD_TEXT}">{text}</span>{n}'
            f'<span style="flex: 1; height: 1px; background: linear-gradient(90deg, #8A6234, rgba(138,98,52,0))"></span></h2>')


def unit_card(code, fp, name, en, role, desc, inner, vb="25 0 200 200", source=""):
    src = f'<span style="position: relative; margin-top: auto; padding-top: 2px; font-size: 10.5px; line-height: 1.3; color: #5E4A30">{source}</span>' if source else ""
    return f"""
    <article style="{FRAME}">{corners()}
      <div style="{ART}; height: {UNIT_ART_H}px">
        <svg width="100%" height="{UNIT_ART_H}" viewBox="{vb}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="{name}">{inner}</svg>
      </div>
      <div style="{PARCH}; flex: 1; display: flex; flex-direction: column; gap: 1px; padding: 6px 10px 7px">{paper_grain()}
        <span style="position: relative; display: flex; justify-content: space-between; {MONO}; font-size: 11px; color: #155A8A; font-weight: 600"><span>{code}</span><span>{fp}</span></span>
        <b style="position: relative; {MYEONG}; font-weight: 800; font-size: 18px; line-height: 1.2">{name}</b>
        <span style="position: relative; font-size: 11.5px; color: #5A4A36">{en} · {role}</span>
        <span style="position: relative; font-size: 12px; line-height: 1.4; color: #3A2C1C">{desc}</span>
        {src}
      </div>
    </article>"""


def bld_tile(code, fp, name, en, role, art):
    return f"""
    <article style="{FRAME}">{corners()}
      <div style="{ART}; height: 128px">{art}</div>
      <div style="{PARCH}; flex: 1; display: flex; flex-direction: column; gap: 1px; padding: 6px 9px 7px">{paper_grain()}
        <span style="position: relative; display: flex; justify-content: space-between; {MONO}; font-size: 11px; color: #155A8A; font-weight: 600"><span>{code}</span><span>{fp}</span></span>
        <b style="position: relative; {MYEONG}; font-weight: 800; font-size: 15px">{name}</b>
        <span style="position: relative; font-size: 11px; color: #4A3C2A">{en} · {role}</span>
      </div>
    </article>"""


def fig(svg, title, sub):
    return f"""
      <figure style="{FRAME}; margin: 0; padding: 5px">{corners()}
        <div style="{ART}; height: 108px">{svg}</div>
        <figcaption style="{PARCH}; flex: 1; padding: 4px 7px; font-size: 12px">{paper_grain()}<span style="position: relative"><b>{title}</b> <span style="color: #5A4A36">· {sub}</span></span></figcaption>
      </figure>"""


SWATCHES = [(BLUE, "귀화 빛"), (INDIGO, "도깨비"), ("#D98A2B", "범 호박"), ("#8A4E2A", "쇠 녹"), ("#17132A", "먹빛"), ("#1F6E6A", "비늘")]
swatch_html = "".join(
    f'<span style="display: flex; flex-direction: column; gap: 3px; align-items: center; width: 50px">'
    f'<svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true"><circle cx="15" cy="15" r="13" fill="url(#r2-brass)" stroke="#2A1608" stroke-width="0.8"></circle>'
    f'<circle cx="15" cy="15" r="10" fill="{c}" stroke="#1A0E06" stroke-width="0.8"></circle><ellipse cx="11.5" cy="11" rx="3.5" ry="2" fill="#FFFFFF" opacity="0.35"></ellipse></svg>'
    f'<span style="{MONO}; font-size: 10px; color: #CFC5AE">{c}</span><span style="font-size: 10px; color: {MUTED}">{n}</span></span>'
    for c, n in SWATCHES)

units_html = "".join([
    unit_card("U-D1", "1×1 · 부유", "도깨비불", "Wisp", "일꾼", "귀화석을 끌어안고 소굴로 나릅니다.", unit_wisp()),
    unit_card("U-D2", "1×1", "방망이 장사", "Club Brute", "근접", "외뿔에 징 박은 방망이. 씨름하듯 밀어붙입니다.", unit_brute()),
    unit_card("U-D3", "1×1", "창귀", "Tiger Thrall", "원거리", "쓰러진 적을 잠시 아군으로 끌어들입니다.", unit_changgwi(),
              source="호랑이 전승 · 범에게 죽은 이의 혼령"),
    unit_card("U-D4", "2×2", "불가사리", "Bulgasari", "중장", "쇠를 먹어 커집니다. 불에 약합니다.", unit_bulgasari(), vb="0 28 250 168",
              source="민간 설화 · 쇠를 먹는 불가살이(不可殺伊)"),
    unit_card("U-D5", "1×1 · 공중", "어둑시니", "Eodukshini", "공중 정찰", "쳐다볼수록 커집니다. 주변 시야를 가립니다.", unit_eodukshini(),
              source="민담 요괴 · 표준어 어둑서니"),
    unit_card("U-D6", "3×2", "이무기", "Imugi", "최종 유닛", "승천을 노리는 최종 유닛. 감투 창고 연구 후 씨름판에서 깨어납니다.", Y.imugi(K), vb="-160 -178 310 232",
              source="민간 전승 · 용이 되지 못한 큰 뱀"),
])

blds_html = "".join([
    bld_tile("B-D1", "3×3", "도깨비 소굴", "Hollow Lair", "본진 · 반납", bld_lair()),
    bld_tile("B-D2", "2×2", "귀화 우물", "Fire Well", "산기 채취", bld_well()),
    bld_tile("B-D3", "2×2", "방망이 대장간", "Club Forge", "기본 생산", bld_forge()),
    bld_tile("B-D4", "2×2", "감투 창고", "Hat Vault", "연구 · 이무기 해금", bld_vault()),
    bld_tile("B-D5", "1×1", "불씨 탑", "Ember Tower", "방어", bld_ember_tower()),
    bld_tile("B-D6", "2×2", "장독대", "Jar Terrace", "식량 +8", bld_jars()),
    bld_tile("B-D7", "3×2", "씨름판", "Wrestling Ring", "중장 생산", bld_ring()),
])

fx_html = "".join(fig(fsvg(svg, n), f"{i + 1}. {n}", "창귀") for i, (n, svg) in enumerate(fx_frames()))
strip_html = (fx_html
              + fig(death_frame(), "사망", "펑! 방망이만")
              + fig(destroyed_frame(), "파괴", "갈라진 고목")
              + fig(harvest_frame(), "채취", "귀화석 뜯기")
              + fig(dropoff_frame(), "반납", "소굴로 쏙"))

LATTICE_BG = ('<svg width="1440" height="960" aria-hidden="true" style="position: absolute; left: 0; top: 0; pointer-events: none">'
              '<defs><pattern id="r2-lat" width="28" height="28" patternUnits="userSpaceOnUse"><path d="M0 14 H28 M14 0 V28 M7 7 H21 V21 H7 Z" fill="none" stroke="#2A2418" stroke-width="1"></path></pattern></defs>'
              '<rect width="1440" height="960" fill="url(#r2-lat)" opacity="0.55"></rect><rect width="1440" height="960" fill="url(#r2-vign)"></rect></svg>')

HEAD = """<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>요물 무리 종족 코덱스</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
body{margin:0;background:#16140F}
a{color:#E2B13C}a:hover{color:#F2D58A}
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

body = f"""<div style="position: relative; width: 1440px; height: 960px; box-sizing: border-box; background: {GROUND}; color: {INK}; font-family: 'IBM Plex Sans KR', sans-serif; overflow: hidden">
  <svg width="0" height="0" style="position: absolute; width: 0; height: 0; overflow: hidden" aria-hidden="true" focusable="false"><defs>{DEFS}<!--R2K--></defs></svg>
  {LATTICE_BG}
  <div style="position: relative; height: 100%; box-sizing: border-box; padding: 20px 40px 18px; display: flex; flex-direction: column; gap: 9px">

  <header style="{FRAME}; flex-direction: row; align-items: center; gap: 18px; padding: 10px 18px">{corners()}
    {crest()}
    <div style="display: flex; flex-direction: column; gap: 5px; flex: 1; min-width: 0">
      <span style="{MONO}; font-size: 12px; letter-spacing: 0.12em; color: #CFB98A">SAMPAJEON · RACE CODEX R2 · 민담 요물 · 6 UNITS · 7 STRUCTURES</span>
      <h1 style="margin: 0; display: flex; align-items: baseline; gap: 14px; line-height: 1.05"><span style="{MYEONG}; font-weight: 800; font-size: 44px; {GOLD_TEXT}">요물 무리</span><span style="{MONO}; font-size: 19px; font-weight: 600; color: {BLUE}; letter-spacing: 0.14em; text-shadow: 0 0 8px rgba(79,179,232,0.5)">YOMUL HORDE</span></h1>
      <span style="font-size: 13px; color: #E2D6BC">귀화석과 함께 깨어난 산과 물의 요물들. 도깨비들이 내기와 씨름판을 벌여 끌어모읍니다.</span>
    </div>
    <div style="display: flex; gap: 2px">{swatch_html}</div>
    <div style="{PARCH}; width: 250px; display: flex; flex-direction: column; gap: 3px; padding: 8px 12px; border: 1px solid #8A6234; font-size: 12px; line-height: 1.45">{paper_grain()}
      <b style="position: relative; {MYEONG}; font-weight: 800; font-size: 13px">형태 언어</b>
      <span style="position: relative">▲ 뿔과 방망이 = 판을 벌이는 도깨비</span>
      <span style="position: relative">● 푸른 귀화 빛 = 모든 요물의 공통 표식</span>
      <span style="position: relative">◆ 범 줄무늬 · 쇠 바늘털 · 먹빛 · 비늘</span>
    </div>
  </header>

  {h2("유닛", "2.5D 쿼터뷰 · 빛은 좌상단 · 요물마다 다른 몸, 공통의 푸른 귀화 빛")}
  <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1.3fr 1fr 1.55fr; gap: 10px">{units_html}
  </div>

  {h2("건물", "아이소메트릭 2:1 · 지붕 + 두 벽면 · 금줄과 도깨비불이 영역 표시")}
  <div style="display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 10px">{blds_html}
  </div>

  {h2("전투 · 경제 동작", "시그니처 공격 4프레임 · 사망 · 파괴 · 채취 · 반납")}
  <div style="display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 8px">{strip_html}
  </div>
  </div>
</div>"""

PROJ.mkdir(parents=True, exist_ok=True)
body = body.replace("<!--R2K-->", K.defs_svg())
OUT.write_text(HEAD + body + TAIL)
print(OUT, len(HEAD + body + TAIL))
