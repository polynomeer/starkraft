"""Shared drawing library for the R1 (관상감 공방) high-fidelity race codex.

2.5D isometric (2:1) painterly SVG. Light from the top-left: SW faces lit, SE faces shaded.
World axes: u -> screen right-down, v -> screen left-down, z -> up.
All SVG ids are prefixed r1-.
"""
import math
import random

RNG = random.Random(1717)


# ------------------------------------------------------------------ primitives
def f(v):
    if isinstance(v, float):
        s = f"{v:.2f}".rstrip("0").rstrip(".")
        return s if s not in ("-0", "") else "0"
    return str(v)


def _attrs(a):
    return " ".join(f'{k.rstrip("_").replace("_", "-")}="{f(v)}"' for k, v in a.items() if v is not None)


def el(tag, **a):
    return f"<{tag} {_attrs(a)}></{tag}>"


def rect(x, y, w, h, fill, **a):
    return el("rect", x=x, y=y, width=w, height=h, fill=fill, **a)


def circ(cx, cy, r, fill, **a):
    return el("circle", cx=cx, cy=cy, r=r, fill=fill, **a)


def ell(cx, cy, rx, ry, fill, **a):
    return el("ellipse", cx=cx, cy=cy, rx=rx, ry=ry, fill=fill, **a)


def path(d, fill="none", **a):
    return el("path", d=d, fill=fill, **a)


def pts(ps):
    return " ".join(f"{f(float(x))},{f(float(y))}" for x, y in ps)


def poly(ps, fill, **a):
    return el("polygon", points=pts(ps), fill=fill, **a)


def pline(ps, stroke, w=1, **a):
    return el("polyline", points=pts(ps), fill="none", stroke=stroke, stroke_width=w, **a)


def line(x1, y1, x2, y2, stroke, w=1, **a):
    return el("line", x1=x1, y1=y1, x2=x2, y2=y2, stroke=stroke, stroke_width=w, **a)


def g(children, **a):
    return f"<g {_attrs(a)}>" + "".join(children) + "</g>"


def svg(w, h, label, body, vb=None):
    vb = vb or f"0 0 {w} {h}"
    return (f'<svg width="{w}" height="{h}" viewBox="{vb}" role="img" aria-label="{label}" '
            f'style="display: block; max-width: 100%">{"".join(body)}</svg>')


def lin(id_, stops, x1=0, y1=0, x2=0, y2=1):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"' + (f' stop-opacity="{op}"' if op is not None else "") + "></stop>"
                for o, c, op in [(t + (None,))[:3] for t in stops])
    return f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def rad(id_, stops, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"' + (f' stop-opacity="{op}"' if op is not None else "") + "></stop>"
                for o, c, op in [(t + (None,))[:3] for t in stops])
    extra = (f' fx="{fx}"' if fx is not None else "") + (f' fy="{fy}"' if fy is not None else "")
    return f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{r}"{extra}>{s}</radialGradient>'


def u(name):
    return f"url(#r1-{name})"


# ------------------------------------------------------------------ palette
OUT = "#120D08"          # sprite outline
RED, RED_L, RED_D = "#D9532E", "#F09A6E", "#7E2814"
GREEN, GREEN_L, GREEN_D = "#3E8F7A", "#7CC4AE", "#1F4E42"
BRASS, BRASS_L, BRASS_D = "#C9A35A", "#FFE7A8", "#6E5222"
WOOD, WOOD_D = "#4A2E1E", "#2E1E14"
ORE, ORE_L, ORE_D = "#9FE3D0", "#CFF5EA", "#2F6F62"
GAS = "#B79AE0"
INK, MUTED = "#EFE6D2", "#A89E8A"
SKIN, SKIN_D = "#E2B48C", "#9A6A48"
HEMP, HEMP_D = "#D8CCB0", "#8C7E62"
TILE, TILE_L, TILE_D = "#59646B", "#8C9AA2", "#2C3338"


# ------------------------------------------------------------------ shared defs
def _tex_filter(id_, freq, octaves, seed, dark, light, strength=0.85):
    """Paint grain: dark and light mottling clipped to the shape."""
    dr, dg, db = (int(dark[i:i + 2], 16) / 255 for i in (1, 3, 5))
    lr, lg, lb = (int(light[i:i + 2], 16) / 255 for i in (1, 3, 5))
    return (f'<filter id="{id_}" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB">'
            f'<feTurbulence type="fractalNoise" baseFrequency="{freq}" numOctaves="{octaves}" seed="{seed}" result="n"></feTurbulence>'
            f'<feColorMatrix in="n" type="matrix" values="0 0 0 0 {dr:.3f}  0 0 0 0 {dg:.3f}  0 0 0 0 {db:.3f}  {strength} 0 0 0 -0.36" result="d"></feColorMatrix>'
            f'<feColorMatrix in="n" type="matrix" values="0 0 0 0 {lr:.3f}  0 0 0 0 {lg:.3f}  0 0 0 0 {lb:.3f}  0 -{strength} 0 0 0.30" result="l"></feColorMatrix>'
            f'<feComposite in="d" in2="SourceGraphic" operator="in" result="dc"></feComposite>'
            f'<feComposite in="l" in2="SourceGraphic" operator="in" result="lc"></feComposite>'
            f'<feMerge><feMergeNode in="SourceGraphic"></feMergeNode><feMergeNode in="dc"></feMergeNode><feMergeNode in="lc"></feMergeNode></feMerge>'
            f'</filter>')


def defs():
    d = []
    # blur / glow / smoke
    d.append('<filter id="r1-shadow" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="2.6"></feGaussianBlur></filter>')
    d.append('<filter id="r1-shadow-l" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="5"></feGaussianBlur></filter>')
    d.append('<filter id="r1-blur1" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1"></feGaussianBlur></filter>')
    d.append('<filter id="r1-glow" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="2.4" result="b"></feGaussianBlur>'
             '<feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>')
    d.append('<filter id="r1-glow-l" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="6"></feGaussianBlur></filter>')
    d.append('<filter id="r1-smoke" x="-40%" y="-40%" width="180%" height="180%">'
             '<feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="3" seed="9" result="t"></feTurbulence>'
             '<feDisplacementMap in="SourceGraphic" in2="t" scale="14" xChannelSelector="R" yChannelSelector="G" result="dm"></feDisplacementMap>'
             '<feGaussianBlur in="dm" stdDeviation="1.6"></feGaussianBlur></filter>')
    d.append('<filter id="r1-fire" x="-40%" y="-40%" width="180%" height="180%">'
             '<feTurbulence type="fractalNoise" baseFrequency="0.11" numOctaves="2" seed="21" result="t"></feTurbulence>'
             '<feDisplacementMap in="SourceGraphic" in2="t" scale="9" xChannelSelector="R" yChannelSelector="G" result="dm"></feDisplacementMap>'
             '<feGaussianBlur in="dm" stdDeviation="0.6" result="s"></feGaussianBlur>'
             '<feGaussianBlur in="dm" stdDeviation="3" result="b"></feGaussianBlur>'
             '<feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="s"></feMergeNode></feMerge></filter>')
    # painterly material textures
    d.append(_tex_filter("r1-tex", "0.55", 2, 4, "#1A1008", "#FFF4DC"))
    d.append(_tex_filter("r1-tex-soft", "0.22", 3, 11, "#1A1008", "#FFF4DC", 0.6))
    d.append(_tex_filter("r1-wood", "0.03 0.55", 3, 7, "#1A0C04", "#F8D8A8"))
    d.append(_tex_filter("r1-stone", "0.18", 4, 5, "#14120E", "#F4EEDC"))
    d.append(_tex_filter("r1-gtex", "0.045", 5, 13, "#2A2410", "#F2E2A8", 0.95))
    d.append(_tex_filter("r1-cloth", "0.9 0.25", 2, 17, "#100A06", "#FFFFFF", 0.7))
    # gradients (objectBoundingBox, reusable)
    d.append(lin("r1-red", [(0, "#F07A50"), (0.45, RED), (1, RED_D)], 0, 0, 1, 1))
    d.append(lin("r1-red-s", [(0, "#A63A1E"), (1, "#5A1A0C")], 0, 0, 1, 1))
    d.append(lin("r1-skin", [(0, "#F3CFA6"), (0.6, SKIN), (1, SKIN_D)], 0, 0, 1, 1))
    d.append(lin("r1-hemp", [(0, "#F1E8D2"), (0.5, HEMP), (1, HEMP_D)], 0, 0, 1, 1))
    d.append(lin("r1-hemp-s", [(0, "#A99B7C"), (1, "#5E533F")], 0, 0, 1, 1))
    d.append(lin("r1-felt", [(0, "#4A4440"), (0.5, "#1E1A18"), (1, "#0A0808")], 0, 0, 1, 1))
    d.append(lin("r1-woodg", [(0, "#8A5A38"), (0.5, "#5E3A22"), (1, "#2E1C10")], 0, 0, 1, 1))
    d.append(lin("r1-woodv", [(0, "#6E4628"), (1, "#3A2414")], 0, 0, 1, 0))
    d.append(lin("r1-brass", [(0, BRASS_L), (0.4, BRASS), (1, BRASS_D)], 0, 0, 1, 1))
    d.append(lin("r1-iron", [(0, "#9AA4A8"), (0.45, "#4E585C"), (1, "#1E2426")], 0, 0, 1, 1))
    d.append(lin("r1-indigo", [(0, "#4C5E9A"), (1, "#1E2850")], 0, 0, 1, 1))
    d.append(lin("r1-roof-f", [(0, "#7C8A92"), (0.55, TILE), (1, "#3A4348")], 0, 0, 0, 1))
    d.append(lin("r1-roof-r", [(0, "#4A545A"), (1, "#262C30")], 0, 0, 1, 1))
    d.append(lin("r1-roof-b", [(0, "#6A767C"), (1, "#4A545A")], 0, 1, 0, 0))
    d.append(lin("r1-roof-l", [(0, "#8E9CA4"), (1, "#5E6A70")], 0, 0, 1, 1))
    d.append(lin("r1-plaster", [(0, "#F2EAD8"), (1, "#C8BCA2")], 0, 0, 0, 1))
    d.append(lin("r1-hanji", [(0, "#FFF2D0"), (1, "#E8C890")], 0, 0, 0, 1))
    d.append(lin("r1-hanji-dim", [(0, "#D8CBAE"), (1, "#A89878")], 0, 0, 0, 1))
    d.append(lin("r1-stoneg", [(0, "#A8A08E"), (0.5, "#7C7464"), (1, "#4C463C")], 0, 0, 1, 1))
    d.append(lin("r1-green", [(0, GREEN_L), (0.5, GREEN), (1, GREEN_D)], 0, 0, 0, 1))
    d.append(lin("r1-ore", [(0, "#F2FFFB"), (0.35, ORE_L), (0.7, ORE), (1, ORE_D)], 0, 0, 1, 1))
    d.append(lin("r1-ore-s", [(0, "#6FC0AE"), (1, "#1E4E46")], 0, 0, 1, 1))
    d.append(rad("r1-ore-glow", [(0, ORE_L, 0.9), (0.5, ORE, 0.35), (1, ORE, 0)]))
    d.append(rad("r1-ground", [(0, "#8A7A4A"), (0.6, "#6A5C34"), (1, "#4A4024")], 0.4, 0.35, 0.7))
    d.append(rad("r1-yard", [(0, "#B8A47A"), (0.7, "#9A865C"), (1, "#7A6844")], 0.4, 0.35, 0.7))
    d.append(rad("r1-fireball", [(0, "#FFFBE0"), (0.25, "#FFE08A"), (0.55, "#F49A3A"), (0.8, "#C8432B"), (1, "#5A1A0C", 0)], 0.45, 0.45, 0.55))
    d.append(rad("r1-flame", [(0, "#FFF6C8"), (0.4, "#FFC24A"), (0.8, "#E8602A"), (1, "#8A2010", 0)], 0.5, 0.7, 0.6))
    d.append(rad("r1-smokeg", [(0, "#B4AEA0", 0.95), (0.6, "#7A746A", 0.7), (1, "#4A463E", 0)], 0.4, 0.35, 0.6))
    d.append(rad("r1-dark-smoke", [(0, "#5A524A", 0.95), (0.7, "#2E2924", 0.7), (1, "#14100C", 0)], 0.4, 0.35, 0.6))
    d.append(rad("r1-ember", [(0, "#FFF2B0"), (0.4, "#FFB040"), (1, "#E04A1A", 0)]))
    d.append(rad("r1-lamp", [(0, "#FFE7A8", 0.95), (0.5, "#F09A6E", 0.45), (1, "#F09A6E", 0)]))
    d.append(rad("r1-scorch", [(0, "#0A0806", 0.9), (0.6, "#1A120C", 0.6), (1, "#1A120C", 0)]))
    d.append(rad("r1-gas", [(0, "#E8DAFF", 0.8), (0.5, GAS, 0.45), (1, GAS, 0)], 0.5, 0.6, 0.5))
    d.append(rad("r1-medal", [(0, "#3A2618"), (0.8, "#1E120A"), (1, "#0E0804")], 0.4, 0.35, 0.7))
    return ('<svg width="0" height="0" style="position: absolute; width: 0; height: 0; overflow: hidden" aria-hidden="true" focusable="false"><defs>'
            + "".join(d) + "</defs></svg>")


# ------------------------------------------------------------------ iso math
def P(ox, oy, uu, vv, z=0.0):
    return (ox + uu - vv, oy + (uu + vv) / 2 - z)


def m_left(ox, oy, W, D, z0, H):
    """Local frame on the SW face (v=D): a along u (0..W), b down from wall top (0..H)."""
    return f"matrix(1,0.5,0,1,{f(float(ox - D))},{f(float(oy + D / 2 - z0 - H))})"


def m_right(ox, oy, W, D, z0, H):
    """Local frame on the SE face (u=W): a along v (0..D), b down from wall top."""
    return f"matrix(1,-0.5,0,1,{f(float(ox + W - D))},{f(float(oy + (W + D) / 2 - z0 - H))})"


def m_top(ox, oy, z):
    return f"matrix(1,0.5,-1,0.5,{f(float(ox))},{f(float(oy - z))})"


def iso_box(ox, oy, W, D, H, top, left, right, z0=0.0, tex=None, outline=True, extra_l=None, extra_r=None, extra_t=None):
    """Box from (0,0,z0) to (W,D,z0+H). Returns svg list. extra_* are local-frame drawings."""
    z1 = z0 + H
    a, b, c, d_ = P(ox, oy, 0, D, z0), P(ox, oy, W, D, z0), P(ox, oy, W, 0, z0), P(ox, oy, 0, 0, z1)
    at, bt, ct = P(ox, oy, 0, D, z1), P(ox, oy, W, D, z1), P(ox, oy, W, 0, z1)
    st = dict(stroke=OUT, stroke_width=0.7, stroke_opacity=0.7, stroke_linejoin="round") if outline else {}
    flt = f"url(#r1-{tex})" if tex else None
    out = [poly([a, b, bt, at], left, filter=flt, **st)]
    if extra_l:
        out.append(g(extra_l, transform=m_left(ox, oy, W, D, z0, H)))
    out.append(poly([b, c, ct, bt], right, filter=flt, **st))
    if extra_r:
        out.append(g(extra_r, transform=m_right(ox, oy, W, D, z0, H)))
    out.append(poly([d_, at, bt, ct], top, filter=flt, **st))
    if extra_t:
        out.append(g(extra_t, transform=m_top(ox, oy, z1)))
    return out


def iso_shadow(ox, oy, W, D, H, k=0.55, op=0.55):
    """Soft cast shadow toward the lower right (light from the top-left)."""
    s = H * k
    ps = [P(ox, oy, 0, D), P(ox, oy, W, D), P(ox, oy, W + s, D), P(ox, oy, W + s, -s * 0.2), P(ox, oy, W, 0)]
    ps = [(x + s * 0.5, y + s * 0.15) for x, y in ps] + [P(ox, oy, 0, D)]
    return poly(ps, "#000", opacity=op, filter=u("shadow"))


# ------------------------------------------------------------------ ground
def tufts(cx, cy, rx, ry, n, cols=("#6E7A3A", "#8E8A44", "#A8944E", "#56662E"), h=5.0):
    out = []
    for _ in range(n):
        a = RNG.random() * math.tau
        r = math.sqrt(RNG.random())
        x, y = cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r
        c = RNG.choice(cols)
        hh = h * (0.6 + RNG.random() * 0.8)
        d = "".join(f"M{f(x + dx)} {f(y)} q{f(dx * 0.4 + RNG.uniform(-1, 1))} {f(-hh * 0.6)} {f(dx * 0.9 + RNG.uniform(-1.5, 1.5))} {f(-hh)}"
                    for dx in (-1.6, -0.6, 0.4, 1.4))
        out.append(path(d, stroke=c, stroke_width=0.9, stroke_linecap="round"))
    return out


def pebbles(cx, cy, rx, ry, n):
    out = []
    for _ in range(n):
        a = RNG.random() * math.tau
        r = math.sqrt(RNG.random())
        x, y = cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r
        s = 0.8 + RNG.random() * 1.6
        out.append(ell(x + 0.4, y + 0.5, s, s * 0.55, "#000", opacity=0.35))
        out.append(ell(x, y, s, s * 0.6, RNG.choice(["#9A9282", "#7E7666", "#B0A890", "#6A6252"])))
        out.append(ell(x - s * 0.3, y - s * 0.2, s * 0.4, s * 0.22, "#E8E0CC", opacity=0.5))
    return out


def leaves(cx, cy, rx, ry, n):
    out = []
    for _ in range(n):
        a = RNG.random() * math.tau
        r = math.sqrt(RNG.random())
        x, y = cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r
        rot = RNG.uniform(0, 180)
        out.append(ell(x, y, 1.6, 0.8, RNG.choice(["#C8432B", "#E2903A", "#B0681E", "#D9B23C"]), transform=f"rotate({f(rot)} {f(x)} {f(y)})", opacity=0.9))
    return out


def ground_patch(cx, cy, rx, ry, kind="field", detail=1.0):
    """Painted iso ground diamond-ish patch with texture, grass, stones, leaves."""
    out = []
    # soft ragged edge: an iso diamond with rounded corners plus an ellipse blend
    dia = [(cx - rx, cy), (cx, cy - ry), (cx + rx, cy), (cx, cy + ry)]
    base = u("yard") if kind == "yard" else u("ground")
    out.append(ell(cx, cy + 2, rx * 1.02, ry * 1.08, "#000", opacity=0.45, filter=u("shadow-l")))
    out.append(poly(dia, base, filter=u("gtex"), stroke="#2A2214", stroke_width=0.8, stroke_opacity=0.5, stroke_linejoin="round"))
    if kind == "field":
        # dirt and grass mottling
        for _ in range(int(6 * detail)):
            x = cx + RNG.uniform(-0.5, 0.5) * rx
            y = cy + RNG.uniform(-0.45, 0.45) * ry
            out.append(ell(x, y, RNG.uniform(6, 16), RNG.uniform(3, 7), RNG.choice(["#7C8240", "#8E7A44", "#6A7034", "#9A8650"]), opacity=0.55, filter=u("blur1")))
        out += tufts(cx, cy, rx * 0.7, ry * 0.7, int(16 * detail))
        out += pebbles(cx, cy, rx * 0.7, ry * 0.7, int(6 * detail))
        out += leaves(cx, cy, rx * 0.7, ry * 0.7, int(6 * detail))
    else:
        # packed yard with flagstone hints and a grass rim
        for _ in range(int(10 * detail)):
            x = cx + RNG.uniform(-0.55, 0.55) * rx
            y = cy + RNG.uniform(-0.5, 0.5) * ry
            out.append(ell(x, y, RNG.uniform(1.5, 4), RNG.uniform(0.8, 2), "#6E5E40", opacity=0.45))
        out += tufts(cx, cy, rx * 0.95, ry * 0.95, int(10 * detail))
        out += pebbles(cx, cy, rx * 0.8, ry * 0.8, int(5 * detail))
    return out


# ------------------------------------------------------------------ hanok (iso, hipped giwa roof)
def _lift(t, L):
    """Upturned eave curve: t in [0,1] along the eave; lift at the corners."""
    return L * ((2 * t - 1) ** 4)


def roof(ox, oy, W, D, z, rh, o=8.0, L=4.0, ridge_u=None, key="r", tone=1.0, burnt=False):
    """Hipped giwa roof over a W×D footprint whose eaves sit at height z. Returns svg list."""
    a = min(D / 2 + o * 0.3, W / 2 - 1) if ridge_u is None else ridge_u
    zt = z + rh
    U0, U1, V0, V1 = -o, W + o, -o, D + o

    def eave_u(t, vv):  # along u at fixed v
        uu = U0 + (U1 - U0) * t
        return P(ox, oy, uu, vv, z + _lift(t, L))

    def eave_v(t, uu):
        vv = V0 + (V1 - V0) * t
        return P(ox, oy, uu, vv, z + _lift(t, L))

    N = 18
    front = [eave_u(i / N, V1) for i in range(N + 1)]          # SW eave, left->bottom corner
    right = [eave_v(1 - i / N, U1) for i in range(N + 1)]      # SE eave, bottom->right corner
    back = [eave_u(1 - i / N, V0) for i in range(N + 1)]       # NE eave
    left = [eave_v(i / N, U0) for i in range(N + 1)]           # NW eave
    R1, R2 = P(ox, oy, a, D / 2, zt), P(ox, oy, W - a, D / 2, zt)
    cf = {"f": u("roof-f"), "r": u("roof-r"), "b": u("roof-b"), "l": u("roof-l")}
    if burnt:
        cf = {k: "#2A2420" for k in cf}
    st = dict(stroke=OUT, stroke_width=0.8, stroke_opacity=0.75, stroke_linejoin="round")
    out = []
    out.append(poly(back + [R1, R2], cf["b"], filter=u("tex"), **st))
    out.append(poly(left + [R1], cf["l"], filter=u("tex"), **st))
    out.append(poly(right + [R2], cf["r"], filter=u("tex"), **st))
    out.append(poly(front + [R2, R1], cf["f"], filter=u("tex"), **st))

    # tile rows (수키와) on the front plane: run from eave (v=V1) up toward ridge/hips, along -v.
    rows = []
    n_f = max(6, int((U1 - U0) / 4.2))
    for i in range(1, n_f):
        t = i / n_f
        uu = U0 + (U1 - U0) * t
        e = eave_u(t, V1)
        if uu < a:      # meets the left hip
            k = (uu - U0) / (a - U0)
            top = P(ox, oy, uu, V1 - k * (V1 - D / 2), z + _lift(t, L) * (1 - k) + k * rh)
        elif uu > W - a:
            k = (U1 - uu) / (U1 - (W - a))
            top = P(ox, oy, uu, V1 - k * (V1 - D / 2), z + _lift(t, L) * (1 - k) + k * rh)
        else:
            top = P(ox, oy, uu, D / 2, zt)
        rows.append((e, top))
    # tile rows on the right hip: from eave (u=U1) toward ridge end R2 along -u
    rrows = []
    n_r = max(4, int((V1 - V0) / 4.2))
    for i in range(1, n_r):
        t = i / n_r
        vv = V0 + (V1 - V0) * t
        e = eave_v(t, U1)
        span = abs(vv - D / 2) / (V1 - D / 2)
        k = 1 - span
        top = P(ox, oy, U1 - k * (U1 - (W - a)), vv, z + _lift(t, L) * (1 - k) + k * rh)
        rrows.append((e, top))
    dark = "#1C2226" if not burnt else "#0E0C0A"
    hi = TILE_L if not burnt else "#4A3C34"
    for e, top in rows:
        out.append(line(e[0], e[1], top[0], top[1], dark, 2.6 * tone, stroke_linecap="round", opacity=0.85))
        out.append(line(e[0] - 0.7, e[1] - 0.4, top[0] - 0.7, top[1] - 0.4, hi, 0.9 * tone, stroke_dasharray="3.2 0.9", opacity=0.9))
    for e, top in rrows:
        out.append(line(e[0], e[1], top[0], top[1], dark, 2.4 * tone, stroke_linecap="round", opacity=0.85))
        out.append(line(e[0] - 0.5, e[1] - 0.6, top[0] - 0.5, top[1] - 0.6, "#6E7A82" if not burnt else "#3A302A", 0.8 * tone, stroke_dasharray="3 1", opacity=0.8))
    # eave end tiles (막새)
    for e, _ in rows:
        out.append(circ(e[0], e[1] + 0.6, 1.25 * tone, "#2A3034", stroke="#8C9AA2", stroke_width=0.4))
    for e, _ in rrows:
        out.append(circ(e[0], e[1] + 0.6, 1.15 * tone, "#1E2428", stroke="#5E6A70", stroke_width=0.4))
    # eave edge lines
    out.append(pline(front, "#151A1D", 1.6))
    out.append(pline(right, "#0E1214", 1.6))
    # hip ridges (내림마루) + main ridge (용마루)
    for p_, q in ((R1, front[0]), (R2, front[-1]), (R2, right[-1]), (R1, back[-1])):
        out.append(line(p_[0], p_[1], q[0], q[1], "#22292D", 3.2 * tone, stroke_linecap="round"))
        out.append(line(p_[0], p_[1] - 0.8, q[0], q[1] - 0.8, "#B8C4CA" if not burnt else "#5A4C40", 0.9, stroke_linecap="round", opacity=0.8))
    out.append(line(R1[0], R1[1], R2[0], R2[1], "#1A1F22", 5 * tone, stroke_linecap="round"))
    out.append(line(R1[0], R1[1] - 1.4, R2[0], R2[1] - 1.4, "#E8EEF0" if not burnt else "#5A4C40", 1.2 * tone, stroke_linecap="round", opacity=0.85))
    # ridge end ornaments (망와) curling up
    for p_, s in ((R1, -1), (R2, 1)):
        out.append(path(f"M{f(p_[0])} {f(p_[1])} q{f(s * 3)} {f(-1.5)} {f(s * 4)} {f(-5.5)} q{f(-s * 1.8)} {f(1.6)} {f(-s * 3.4)} {f(3.2)} z",
                        "#22292D", stroke="#C8D2D6", stroke_width=0.5))
    return out


def stone_course(W, H, seed_rows=3, tone="#8E8676"):
    """Local-frame foundation stones in W×H."""
    out = [rect(0, 0, W, H, u("stoneg"), filter=u("stone"))]
    rows = max(1, seed_rows)
    rh = H / rows
    for r in range(rows):
        x = -RNG.uniform(0, 6)
        while x < W:
            w = RNG.uniform(5, 11)
            c = RNG.choice(["#A49C88", "#8E8676", "#7A7262", "#B4AC96", "#9A927E"])
            out.append(rect(max(x, 0) + 0.4, r * rh + 0.4, max(0.5, min(w, W - x) - 0.8 - (0 if x >= 0 else -x)), rh - 0.8, c, rx=1.2,
                            stroke="#2A2620", stroke_width=0.5))
            out.append(rect(max(x, 0) + 0.8, r * rh + 0.7, max(0.5, min(w, W - x) * 0.6), 0.7, "#F0E8D4", opacity=0.35))
            x += w
    return out


def wall_face(W, H, cols, kind="lattice", lit=True, glow=False, door_at=None):
    """Local-frame wall: posts, beams, lattice windows / plaster, dancheong band at top."""
    out = []
    band = min(6.0, H * 0.22)
    out.append(rect(0, 0, W, H, u("plaster"), filter=u("tex-soft")))
    # dancheong band under the eave: green base, red/yellow/white motifs
    out.append(rect(0, 0, W, band, u("green")))
    step = 5.0
    x = 1.0
    while x < W - 2:
        out.append(rect(x, band * 0.18, 2.2, band * 0.64, RED, rx=0.6))
        out.append(circ(x + 1.1, band * 0.5, 0.55, "#F2D58A"))
        out.append(rect(x + 2.8, band * 0.3, 1.2, band * 0.4, "#EFE8D8", opacity=0.85))
        x += step
    out.append(rect(0, band, W, 1.1, "#1E120A"))
    # posts (기둥) + middle beam (인방)
    pw = 2.4
    xs = [i * (W - pw) / cols for i in range(cols + 1)]
    for i in range(cols):
        x0, x1 = xs[i] + pw, xs[i + 1]
        cw = x1 - x0
        top, bot = band + 1.6, H - 1.2
        if door_at is not None and i == door_at:
            out.append(rect(x0, top, cw, bot - top, "#2A1A10"))
            out.append(rect(x0 + 0.8, top + 0.8, cw / 2 - 1.1, bot - top - 1.6, u("woodv"), filter=u("wood")))
            out.append(rect(x0 + cw / 2 + 0.3, top + 0.8, cw / 2 - 1.1, bot - top - 1.6, u("woodv"), filter=u("wood")))
            for k in range(3):
                yy = top + 2 + k * (bot - top - 4) / 2
                out.append(circ(x0 + cw / 2 - 1.4, yy, 0.45, BRASS_L))
                out.append(circ(x0 + cw / 2 + 1.4, yy, 0.45, BRASS_L))
            continue
        if kind == "lattice":
            wy0, wy1 = top + 1, bot - (bot - top) * 0.28
            out.append(rect(x0 + 0.6, wy0, cw - 1.2, wy1 - wy0, u("hanji") if glow else u("hanji-dim")))
            if glow:
                out.append(rect(x0 + 0.6, wy0, cw - 1.2, wy1 - wy0, "#FFC870", opacity=0.35, filter=u("blur1")))
            # 문살: vertical and horizontal muntins
            nx = max(2, int(cw / 2.6))
            ny = max(3, int((wy1 - wy0) / 2.6))
            for k in range(1, nx):
                xx = x0 + 0.6 + k * (cw - 1.2) / nx
                out.append(line(xx, wy0, xx, wy1, "#4A2E1E", 0.55))
            for k in range(1, ny):
                yy = wy0 + k * (wy1 - wy0) / ny
                out.append(line(x0 + 0.6, yy, x0 + cw - 0.6, yy, "#4A2E1E", 0.55))
            out.append(rect(x0 + 0.6, wy0, cw - 1.2, wy1 - wy0, "none", stroke="#2E1E14", stroke_width=0.9))
            out.append(rect(x0, wy1 + 0.6, cw, bot - wy1 - 0.6, u("plaster")))
            out.append(rect(x0, wy1, cw, 1.0, "#5E3A22"))
        else:  # plaster panel with beams
            out.append(rect(x0, top, cw, bot - top, u("plaster"), filter=u("tex-soft")))
            out.append(rect(x0, top + (bot - top) * 0.45, cw, 1.0, "#5E3A22"))
    for x in xs:
        out.append(rect(x, band + 1.1, pw, H - band - 1.1, "#7A2A18"))
        out.append(rect(x, band + 1.1, pw * 0.4, H - band - 1.1, "#C8563A", opacity=0.75))
    out.append(rect(0, H - 1.2, W, 1.2, "#3A2414"))
    if not lit:
        out.append(rect(0, 0, W, H, "#0A0C14", opacity=0.42))
    else:
        out.append(rect(0, band + 1, W, 3, "#000", opacity=0.25))  # eave shadow
    return out


def hanok(ox, oy, W, D, Hw=16.0, rh=14.0, o=7.0, plinth=4.0, cols=(4, 3), kind="lattice", glow=True,
          door=True, L=4.0, burnt=False, roof_tone=1.0, extras_top=None, z_base=0.0):
    """Complete iso hanok: shadow, stone plinth, walls (two visible faces), giwa roof."""
    out = [iso_shadow(ox, oy, W + 4, D + 4, Hw + rh + plinth + z_base, k=0.5)]
    pb = 1.8
    out += iso_box(ox, oy, W + 2 * pb, D + 2 * pb, plinth, u("stoneg"), u("stoneg"), "#4C463C", z0=z_base,
                   extra_l=stone_course(W + 2 * pb, plinth, 1 if plinth < 5 else 2),
                   extra_r=stone_course(D + 2 * pb, plinth, 1 if plinth < 5 else 2) + [rect(0, 0, D + 2 * pb, plinth, "#000", opacity=0.38)],
                   tex="stone")
    wx, wy = P(ox, oy, pb, pb)
    zb = z_base + plinth
    doorL = cols[0] // 2 if door else None
    out += iso_box(wx, wy, W, D, Hw, "#3A2414", u("plaster"), "#8C826C", z0=zb,
                   extra_l=wall_face(W, Hw, cols[0], kind, True, glow, doorL),
                   extra_r=wall_face(D, Hw, cols[1], kind, False, glow))
    out += roof(wx, wy, W, D, zb + Hw, rh, o=o, L=L, burnt=burnt, tone=roof_tone)
    if extras_top:
        out += extras_top
    return out


# ------------------------------------------------------------------ small props
def lantern(x, y, s=1.0):
    return [ell(x, y, 7 * s, 7 * s, u("lamp"), filter=u("blur1")),
            line(x, y - 6 * s, x, y - 3 * s, "#2A1A10", 0.6),
            ell(x, y, 2.2 * s, 3 * s, "#E8603A", stroke=OUT, stroke_width=0.4),
            ell(x - 0.6 * s, y - 0.8 * s, 0.8 * s, 1.4 * s, "#FFD8A0", opacity=0.8),
            rect(x - 1.4 * s, y - 3.4 * s, 2.8 * s, 0.8 * s, "#2A1A10"),
            rect(x - 1.4 * s, y + 2.7 * s, 2.8 * s, 0.8 * s, "#2A1A10")]


def flag(x, y, color, h=24.0, w=10.0):
    return [line(x + 1.5, y + 1, x + 1.5, y - h + 1, "#000", 1.4, opacity=0.35, filter=u("blur1")),
            line(x, y, x, y - h, "#3A2414", 1.2), circ(x, y - h, 0.9, BRASS_L),
            path(f"M{f(x)} {f(y - h + 1)} q{f(w * 0.5)} 2 {f(w)} 0 q-1 {f(h * 0.2)} 0 {f(h * 0.35)} q{f(-w * 0.5)} -2 {f(-w)} 0 z", color,
                 stroke=OUT, stroke_width=0.5, filter=u("cloth")),
            path(f"M{f(x + 1)} {f(y - h + 3)} q{f(w * 0.4)} 1.5 {f(w * 0.8)} 0", stroke="#FFE7A8", stroke_width=0.5, opacity=0.6)]


def ore_crystal(cx, cy, s=1.0, glow=True, n=5, depleted=0.0):
    """Goblin-fire stone cluster on a rock base (iso)."""
    out = []
    if glow:
        out.append(ell(cx, cy - 4 * s, 18 * s, 12 * s, u("ore-glow"), filter=u("glow-l")))
    out.append(ell(cx + 2 * s, cy + 2 * s, 14 * s, 5 * s, "#000", opacity=0.45, filter=u("shadow")))
    out.append(path(f"M{f(cx - 13 * s)} {f(cy)} q{f(2 * s)} {f(-6 * s)} {f(9 * s)} {f(-7 * s)} q{f(8 * s)} {f(-2 * s)} {f(15 * s)} {f(3 * s)} q{f(3 * s)} {f(4 * s)} {f(-3 * s)} {f(6 * s)} q{f(-10 * s)} {f(3 * s)} {f(-21 * s)} {f(-2 * s)} z",
                    u("stoneg"), stroke=OUT, stroke_width=0.6, filter=u("stone")))
    shards = [(-6, -2, 3.2, 15, -14), (-1, -3, 4.2, 22, -4), (5, -2, 3.4, 17, 10), (9, 0, 2.6, 11, 22), (-10, 0, 2.4, 9, -28),
              (2, 1, 2.2, 9, 30), (-4, 1, 2.0, 8, -40)][:n]
    for dx, dy, w, h, ang in shards:
        h = h * (1 - depleted * 0.75)
        x0, y0 = cx + dx * s, cy + dy * s
        tip = (x0 + math.sin(math.radians(ang)) * h * s, y0 - math.cos(math.radians(ang)) * h * s)
        lft = (x0 - w * s, y0)
        rgt = (x0 + w * s, y0)
        mid = (x0 + (tip[0] - x0) * 0.25 - w * 0.2 * s, y0 + (tip[1] - y0) * 0.25)
        out.append(poly([lft, tip, mid], u("ore"), stroke=ORE_D, stroke_width=0.5))
        out.append(poly([mid, tip, rgt], u("ore-s"), stroke=ORE_D, stroke_width=0.5))
        out.append(line(lft[0] + w * 0.4 * s, lft[1] - 1, tip[0] - 0.3, tip[1] + 1.5, "#FFFFFF", 0.6, opacity=0.8))
    if glow:
        for _ in range(4):
            x = cx + RNG.uniform(-10, 10) * s
            y = cy - RNG.uniform(4, 22) * s
            out.append(circ(x, y, RNG.uniform(0.5, 1.1), ORE_L, filter=u("glow")))
    return out


def smoke_puff(cx, cy, r, dark=False, op=0.9):
    gid = "dark-smoke" if dark else "smokeg"
    out = []
    for dx, dy, k in ((0, 0, 1), (-r * 0.55, r * 0.25, 0.7), (r * 0.55, r * 0.2, 0.75), (r * 0.15, -r * 0.5, 0.65)):
        out.append(circ(cx + dx, cy + dy, r * k, u(gid), opacity=op, filter=u("smoke")))
    return out


def flames(cx, cy, s=1.0, n=3):
    out = [ell(cx, cy - 4 * s, 12 * s, 9 * s, "#FF8A3A", opacity=0.35, filter=u("glow-l"))]
    for i in range(n):
        dx = (i - (n - 1) / 2) * 4.2 * s
        h = (12 + RNG.uniform(-3, 5)) * s
        out.append(path(f"M{f(cx + dx - 3.4 * s)} {f(cy)} q{f(-1 * s)} {f(-h * 0.55)} {f(3.4 * s)} {f(-h)} q{f(4.4 * s)} {f(h * 0.45)} {f(3.4 * s)} {f(h)} z",
                        u("flame"), filter=u("fire")))
    return out
