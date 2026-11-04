"""obang_figs — the 오방 신당 figures: six units and three heroes (see design2/SPEC_TJI.md).

Every function takes a figure_kit.Kit and returns SVG markup in local coordinates with the figure's ground
point at (0, 0) and y going up as negative, so callers place them with <g transform>. Gradients and filters
are registered on the Kit, so the caller only has to emit K.defs_svg() after all drawing calls.
Output is deterministic. Imports nothing from the artboard generators, so any of them can use it.

These are figures of a living religion: they stay dignified, calm and upright. No comic, villainous,
grotesque or sexualised treatment, and nothing that reads as a Japanese shrine.
"""
import math
import random

from figure_kit import LIGHT, Mat, MATS, P, f, mix, shade

OUTL = "#120D08"
GOLD, GOLD_L = "#E2B13C", "#F6DE9A"
OBANG = {"청": "#2F6E8E", "적": "#C8432B", "황": "#E2B13C", "백": "#EFE8D8", "흑": "#1F1D1A"}

WHITE = Mat("#D6CFBA", "#FFFCF0", "#544E3C", "#8C96A8", spec_op=0.18, gloss=0.12)
HANJI = Mat("#E2DAC2", "#FFFEF6", "#6A6250", "#98A0AE", spec_op=0.15, gloss=0.1)
BLACK = Mat("#2C2A28", "#6C6862", "#050404", "#3C5674", spec_op=0.3, gloss=0.35)
NAVY = Mat("#27386E", "#5E76BC", "#080C22", "#42587E", spec_op=0.2, gloss=0.2)
RED = Mat("#B23A26", "#EA7C54", "#340A05", "#7A3A46", spec_op=0.2, gloss=0.22)
GREEN = Mat("#2E7A62", "#6CC0A4", "#07201A", "#3C6470", spec_op=0.2, gloss=0.2)
YELLOW = Mat("#C8962E", "#F6D277", "#3A2608", "#8A7048", spec_op=0.22, gloss=0.25)
STRAW = MATS["straw"]
WOOD = Mat("#6E4A2A", "#A87C52", "#1E0F07", "#54484A", spec_op=0.14, gloss=0.15)
WOOD_D = Mat("#4A3120", "#80603E", "#120905", "#42383C", spec_op=0.12, gloss=0.15)
STONE = Mat("#7E7C74", "#C4C2B8", "#1C1C1A", "#56646E", spec_op=0.15, gloss=0.15)
BRASS = MATS["brass"]
IRON = MATS["iron"]
DOPO = Mat("#1C1B1C", "#4C4A4A", "#040303", "#2E4664", spec_op=0.16, gloss=0.2)
FELT = Mat("#242220", "#55504A", "#070605", "#2E3A4A", spec_op=0.08, gloss=0.08)
PALE = Mat("#C6B8A2", "#F0E6D4", "#40362A", "#6E8094", spec_op=0.2, gloss=0.25)
SKIN = MATS["skin_fair"]
SKIN_T = MATS["skin_tan"]
OLD = MATS["skin_weathered"]
TIGER = Mat("#CC8A2C", "#F6C872", "#2E1606", "#8A6A4E", spec_op=0.18, gloss=0.2)
FUR_W = Mat("#D8CFB8", "#FFFBEE", "#5A5040", "#8A92A0", spec_op=0.12, gloss=0.1)


def stroke(d, col, w, op=1.0, extra=""):
    return f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{f(float(w))}" stroke-opacity="{f(float(op))}" stroke-linecap="round" stroke-linejoin="round"{extra}></path>'


def fill(d, col, op=1.0, extra=""):
    return f'<path d="{d}" fill="{col}" fill-opacity="{f(float(op))}"{extra}></path>'


def ell(cx, cy, rx, ry, col, op=1.0, extra=""):
    return f'<ellipse cx="{f(float(cx))}" cy="{f(float(cy))}" rx="{f(float(rx))}" ry="{f(float(ry))}" fill="{col}" opacity="{f(float(op))}"{extra}></ellipse>'


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def soft(K):
    return f' filter="url(#{K.p}-soft)"'


def ao_f(K):
    return f' filter="url(#{K.p}-ao)"'


def cloth(K, pts, m, n=(-0.98, -0.2), w=None, smooth=True, outline=True):
    """A shaded closed cloth shape; returns (svg, path d)."""
    d = K._smooth(pts) if smooth else "M" + " L".join(P(p) for p in pts) + " Z"
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    c = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    gid = K._cyl_grad(c, n, w or (max(xs) - min(xs)) * 0.55, m)
    o = f' stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.75" stroke-linejoin="round"' if outline else ""
    return f'<path d="{d}" fill="url(#{gid})"{o}></path>', d


def skirt(K, tl, tr, br, bl, m, folds=6, seed=1, hem=None, hem_h=5.0, wave=2.0):
    """A hanging cloth panel: pleats run from the top edge to a gently waved hem, each pleat a crease + ridge."""
    rng = random.Random(seed)
    hem_pts = []
    n = 8
    for i in range(n + 1):
        p = lerp(br, bl, i / n)
        hem_pts.append((p[0], p[1] + (wave if i % 2 else -wave * 0.4)))
    pts = [tl, tr, lerp(tr, br, 0.5)] + hem_pts + [lerp(bl, tl, 0.5)]
    body, d = cloth(K, pts, m)
    inner = []
    for i in range(1, folds + 1):
        t = i / (folds + 1) + rng.uniform(-0.03, 0.03)
        a = lerp(tl, tr, t)
        b = lerp(bl, br, t)
        mid = ((a[0] + b[0]) / 2 + rng.uniform(-1.6, 1.6), (a[1] + b[1]) / 2)
        pd = f"M{P(a)} Q{P(mid)} {P(b)}"
        inner.append(stroke(pd, m.core, 1.5, 0.62, soft(K)))
        pd2 = f"M{P((a[0] - 1.3, a[1]))} Q{P((mid[0] - 1.6, mid[1]))} {P((b[0] - 1.6, b[1]))}"
        inner.append(stroke(pd2, m.light, 0.8, 0.5))
    if hem:
        h0 = (bl[0], bl[1] - hem_h)
        h1 = (br[0], br[1] - hem_h)
        inner.append(f'<path d="M{P(h0)} L{P(h1)} L{P((br[0] + 4, br[1] + 6))} L{P((bl[0] - 4, bl[1] + 6))} Z" fill="{hem}" opacity="0.92"></path>')
    inner.append(ell((tl[0] + tr[0]) / 2, tl[1] + 2, abs(tr[0] - tl[0]) * 0.6, 5, "#0A0604", 0.42, soft(K)))
    return body + K.clip_overlay(d, "".join(inner)), d


def wide_sleeve(K, sh, el, wr, m, droop=10.0, w=6.0, hand="fist", hand_dir=None, hand_m=None, back=False):
    """Long hanging 도포 sleeve: arm tube inside, a deep sleeve pocket hanging below the forearm."""
    mm = m.back() if back else m
    hm = hand_m or SKIN
    hm = hm.back() if back else hm
    mid = lerp(el, wr, 0.55)
    tip = lerp(el, wr, 0.92)
    pts = [(sh[0], sh[1] - w * 0.9), (el[0], el[1] - w), (tip[0], tip[1] - w * 0.9), (tip[0] + (wr[0] - el[0]) * 0.1, tip[1] + w),
           (tip[0], tip[1] + droop + w), (mid[0], mid[1] + droop * 1.3 + w), (el[0], el[1] + w * 1.1), (sh[0], sh[1] + w)]
    body, d = cloth(K, pts, mm)
    inner = [stroke(f"M{P(el)} Q{P((mid[0], mid[1] + droop * 0.7))} {P((tip[0], tip[1] + droop * 0.9))}", mm.core, 1.5, 0.65, soft(K)),
             stroke(f"M{P((el[0], el[1] - w * 0.5))} Q{P((mid[0], mid[1] - w * 0.3))} {P((tip[0], tip[1] - w * 0.5))}", mm.light, 1.2, 0.5)]
    out = []
    if hand:
        out.append(K.hand(wr, hand_dir or (wr[0] - el[0], wr[1] - el[1]), w * 0.72, hm, kind=hand))
    out.append(body + K.clip_overlay(d, "".join(inner)))
    return "".join(out)


def gat(K, cx, y, r, m=BLACK, tilt=0.0):
    """Black 갓: translucent wide brim, tall crown, chin cord. y = brim height."""
    rx, ry = r * 2.05, r * 0.42
    gid = K._sphere_grad(cx - rx * 0.1, y, rx, m)
    out = [ell(cx, y + r * 0.5, r * 1.1, r * 0.3, "#0A0604", 0.5, ao_f(K)),
           f'<ellipse cx="{f(float(cx))}" cy="{f(float(y))}" rx="{f(rx)}" ry="{f(ry)}" fill="url(#{gid})" fill-opacity="0.93" stroke="{OUTL}" stroke-width="0.7" stroke-opacity="0.8"></ellipse>',
           stroke(f"M{f(cx - rx * 0.86)} {f(y - ry * 0.3)} Q{f(float(cx))} {f(y - ry * 1.05)} {f(cx + rx * 0.5)} {f(y - ry * 0.5)}", "#C8D4E4", r * 0.08, 0.5),
           K.form((cx, y - r * 0.02), (cx, y - r * 1.2), r * 0.74, r * 0.62, m, ao_start=False, ao_end=False),
           ell(cx, y - r * 1.32, r * 0.62, r * 0.14, shade(m.base, 0.15)),
           stroke(f"M{f(cx - r * 0.72)} {f(y - r * 0.18)} Q{f(float(cx))} {f(y + r * 0.04)} {f(cx + r * 0.72)} {f(y - r * 0.18)}", "#6E5A38", r * 0.12, 0.8)]
    return "".join(out)


def bell_cluster(K, cx, cy, s=1.0, ring=False):
    """무당의 방울: a short handle with brass bells clustered at the top."""
    out = [K.form((cx, cy + 15 * s), (cx, cy + 3 * s), 1.3 * s, 1.1 * s, WOOD, ao_start=False, spec=False)]
    for dx, dy, r in ((-5, 0, 2.6), (5, 0, 2.6), (-2.6, -4.4, 2.6), (2.6, -4.4, 2.6), (0, -8.2, 2.6), (-7, -5, 2.3), (7, -5, 2.3)):
        out.append(K.ball(cx + dx * s, cy + dy * s, r * s, BRASS))
        out.append(ell(cx + dx * s, cy + dy * s + r * s * 0.55, r * s * 0.55, r * s * 0.13, "#2A1A06", 0.85))
    out.append(stroke(f"M{f(cx)} {f(cy + 15 * s)} q{f(-3 * s)} {f(6 * s)} {f(-1.5 * s)} {f(11 * s)}", OBANG["적"], 1.4 * s))
    out.append(stroke(f"M{f(cx)} {f(cy + 15 * s)} q{f(3 * s)} {f(6 * s)} {f(1.6 * s)} {f(10 * s)}", OBANG["청"], 1.4 * s))
    return "".join(out)


def fan(K, cx, cy, ang, s=1.0):
    """접부채 opened from a pivot at (cx, cy), centred on ang degrees, with ribs and painted red/blue edge bands."""
    out = []
    a0, a1 = math.radians(ang - 55), math.radians(ang + 55)
    R0, R1 = 7 * s, 27 * s
    def pt(a, r):
        return (cx + math.cos(a) * r, cy + math.sin(a) * r)
    d = (f"M{P(pt(a0, R0))} L{P(pt(a0, R1))} A{f(R1)} {f(R1)} 0 0 1 {P(pt(a1, R1))} L{P(pt(a1, R0))} A{f(R0)} {f(R0)} 0 0 0 {P(pt(a0, R0))} Z")
    gid = K._cyl_grad((cx, cy), (-0.7, -0.7), R1, HANJI)
    out.append(f'<path d="{d}" fill="url(#{gid})" stroke="{OUTL}" stroke-width="0.8" stroke-opacity="0.8"></path>')
    band = (f"M{P(pt(a0, R1 * 0.8))} L{P(pt(a0, R1))} A{f(R1)} {f(R1)} 0 0 1 {P(pt(a1, R1))} L{P(pt(a1, R1 * 0.8))} A{f(R1 * 0.8)} {f(R1 * 0.8)} 0 0 0 {P(pt(a0, R1 * 0.8))} Z")
    out.append(fill(band, OBANG["적"], 0.92))
    k = 12
    for i in range(k + 1):
        a = a0 + (a1 - a0) * i / k
        out.append(stroke(f"M{P(pt(a, R0))} L{P(pt(a, R1))}", "#6E5A38" if i % 2 else "#FFF8E0", 0.55, 0.75))
    out.append(K.ball(cx, cy, 1.8 * s, BRASS))
    return "".join(out)


def geumjul(K, pts, papers=True, sag=4.0, seed=3):
    """금줄: a THIN left-twisted straw rope with hanji strips, pine sprigs and charcoal knotted in."""
    rng = random.Random(seed)
    out = []
    for a, b in zip(pts, pts[1:]):
        c = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + sag)
        d = f"M{P(a)} Q{P(c)} {P(b)}"
        out.append(stroke(d, "#3A2A10", 2.2, 0.9))
        out.append(stroke(d, "#C8A860", 1.4))
        out.append(f'<path d="{d}" fill="none" stroke="#6E5628" stroke-width="1.4" stroke-dasharray="1 2"></path>')
        if papers:
            m = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1]) / 13))
            for i in range(1, m):
                t = i / m
                x = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t ** 2 * b[0]
                y = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t ** 2 * b[1]
                kind = rng.choice(("p", "p", "s", "c"))
                if kind == "p":
                    out.append(f'<path d="M{f(x - 1.2)} {f(y)} l2.4 0 l{f(rng.uniform(-0.6, 0.6))} 6.4 l-2.4 0 Z" fill="#FBF6E6" stroke="#6A6250" stroke-width="0.3"></path>')
                elif kind == "s":
                    out.append(stroke(f"M{f(x)} {f(y)} l-2 4 M{f(x)} {f(y)} l0 5 M{f(x)} {f(y)} l2 4", "#3E6E48", 0.8))
                else:
                    out.append(f'<rect x="{f(x - 1.4)}" y="{f(y + 0.4)}" width="2.8" height="3" rx="0.7" fill="#14110E"></rect>')
    return "".join(out)


def flag(K, base, top, col, side=1, w=30.0, h=20.0, wave=0.0, finial=True, glyph=None, pole_w=1.3):
    """A tall pole with a wind-blown cloth flag and triangular 화염 tails; side +1 flies right, -1 left."""
    out = [K.form(base, top, pole_w * 1.15, pole_w * 0.8, WOOD_D, ao_start=False, spec=False)]
    x, y = top[0], top[1] + 3
    s = side
    m = Mat(col, shade(col, 0.35), shade(col, -0.6), mix(shade(col, -0.25), "#6A7FA0", 0.3), spec_op=0.15, gloss=0.2)
    tips = []
    n = 4
    for i in range(n):
        a = i / n
        b = (i + 0.5) / n
        tips += [(x + s * w * 1.18, y + h * b + wave), (x + s * w * 0.94, y + h * (i + 1) / n + wave * 0.6)]
    pts = [(x, y), (x + s * w * 0.5, y - 2 + wave * 0.5), (x + s * w, y + wave)] + tips + [(x + s * w * 0.5, y + h + 1 + wave * 0.5), (x, y + h)]
    d = "M" + " L".join(P(p) for p in pts) + " Z"
    gid = K._cyl_grad((x + s * w * 0.5, y + h * 0.5), (-0.2, -0.98), h * 0.7, m)
    out.append(f'<path d="{d}" fill="url(#{gid})" stroke="{OUTL}" stroke-width="0.7" stroke-opacity="0.8" stroke-linejoin="round"></path>')
    out.append(K.clip_overlay(d, stroke(f"M{f(x + s * w * 0.32)} {f(y)} q{f(s * 2)} {f(h * 0.5)} 0 {f(h)}", m.core, 2.2, 0.5, soft(K))
                              + stroke(f"M{f(x + s * w * 0.66)} {f(y + wave)} q{f(s * 2)} {f(h * 0.5)} 0 {f(h)}", m.core, 2.2, 0.45, soft(K))
                              + stroke(f"M{f(x + s * w * 0.42)} {f(y)} q{f(s * 2)} {f(h * 0.5)} 0 {f(h)}", m.light, 1.1, 0.5)))
    if finial:
        out.append(K.form((top[0], top[1] + 1), (top[0], top[1] - 7), 1.5, 0.3, BRASS, ao_start=False, spec=False))
        out.append(K.ball(top[0], top[1] + 1, 1.8, BRASS))
    return "".join(out)


def rope_coil(K, cx, cy, rx, ry, turns=4):
    out = []
    for i in range(turns):
        k = i / max(1, turns - 1)
        o = stroke(f"M{f(cx - rx)} {f(cy + k * 3 - 1.5)} a{f(rx)} {f(ry)} 0 1 0 {f(rx * 2)} 0 a{f(rx)} {f(ry)} 0 1 0 {f(-rx * 2)} 0", "#3A2A10", 2.2, 0.8)
        o += stroke(f"M{f(cx - rx)} {f(cy + k * 3 - 1.5)} a{f(rx)} {f(ry)} 0 1 0 {f(rx * 2)} 0 a{f(rx)} {f(ry)} 0 1 0 {f(-rx * 2)} 0", "#C8A860", 1.3)
        out.append(o)
    return "".join(out)


def lamellar(K, d, x0, y0, x1, y1, m, pw=4.2, ph=5.6, seed=1):
    """찰갑: overlapping plates clipped to the shape d, each with its own lit edge and a lace hole."""
    plates = []
    rows = int((y1 - y0) / ph) + 2
    cols = int((x1 - x0) / pw) + 2
    for r in range(rows):
        y = y0 + r * ph
        for c in range(cols):
            x = x0 + c * pw + (pw / 2 if r % 2 else 0)
            lit = 1 - (x - x0) / max(1, x1 - x0)
            col = mix(m.core, m.light, 0.2 + 0.75 * lit)
            plates.append(f'<path d="M{f(x)} {f(y)} h{f(pw - 0.5)} v{f(ph - 1.6)} q{f(-(pw - 0.5) / 2)} 2.2 {f(-(pw - 0.5))} 0 Z" fill="{col}" stroke="{m.core}" stroke-width="0.35"></path>')
            if lit > 0.45 and (r + c) % 2 == 0:
                plates.append(f'<path d="M{f(x + 0.5)} {f(y + 0.5)} v{f(ph - 2.6)}" stroke="#FFF8E8" stroke-width="0.45" stroke-opacity="0.7"></path>')
    return K.clip_overlay(d, "".join(plates))


# ------------------------------------------------------------------ U-T1 금줄지기
def rope_keeper(K, strike=False):
    """Small white-clad keeper with stakes on the back, stringing 금줄 from a stake."""
    w = WHITE
    o = [K.cast_shadow(0, 0, 22, 6.5)]
    # stakes bundle + back coil
    for a, b in (((6, -40), (24, -116)), ((10, -40), (30, -112)), ((3, -44), (17, -120))):
        o.append(K.form(a, b, 1.6, 1.2, WOOD, ao_start=False, spec=False))
    o.append(stroke("M14 -100 l14 -3 M12 -88 l13 -3", "#C8A860", 1.6))
    o.append(K.leg((4, -48), (8, -27), (9, -6), w, w=5.6, trouser=w, boot=STRAW, back=True))
    o.append(K.leg((-5, -48), (-7, -27), (-8, -6), w, w=5.6, trouser=w, boot=STRAW, foot_dir=-1))
    o.append(K.arm((10, -84), (17, -66), (11, -52), w, sleeve=w, w=5.2, hand="fist", hand_m=SKIN, back=True))
    s, d = skirt(K, (-10, -64), (10, -64), (15, -30), (-15, -30), HANJI, folds=5, seed=2)
    o.append(s)
    t, _ = K.torso((0, -90), (0, -60), 12, 10.5, HANJI)
    o.append(t)
    o.append(stroke("M-5 -90 L3 -71 M5 -90 L-3 -72", "#8A8270", 0.9, 0.9))
    o.append(K.form((-11, -61), (11, -61), 2.6, 2.6, STRAW, ao_start=False, spec=False))
    o.append(stroke("M2 -59 q1 7 -1 12 M3 -59 q3 6 3 11", "#C8A860", 1.3))
    o.append(rope_coil(K, 11, -83, 8, 5.4))
    o.append(K.neck((0, -90), (0, -98), 3.6, SKIN))
    look = dict(shape="round", eye="almond", brow="straight", nose="small", mouth="calm", age=0.1, turn=-0.25, cheek=0.3, lid="double")
    h, _ = K.face(0, -106, 9.6, SKIN, look)
    o.append(h)
    # hanji 두건 (head cloth), knotted at the back
    hd, _ = cloth(K, [(-9.6, -107), (-8.6, -116), (0, -119), (8.6, -116), (9.8, -107), (5, -110.5), (-5, -110.5)], HANJI)
    o.append(hd)
    o.append(stroke("M-8 -110 Q0 -113.5 8.4 -110", "#8A8270", 0.9))
    o.append(stroke("M9 -110 q5 4 7 11 M9 -109 q6 1 10 6", "#EFE8D8", 1.6))
    if strike:  # driving a stake with a mallet
        o.append(K.arm((-10, -84), (-20, -92), (-26, -106), w, sleeve=w, w=5.2, hand="grip", hand_m=SKIN))
        o.append(K.form((-27, -108), (-38, -118), 1.3, 1.3, WOOD, ao_start=False, spec=False))
        o.append(K.form((-44, -113), (-33, -124), 3.2, 3.2, WOOD_D, ao_start=False))
    else:  # paying rope out toward a stake
        o.append(K.arm((-10, -84), (-20, -68), (-32, -60), w, sleeve=w, w=5.2, hand="grip", hand_m=SKIN))
        o.append(K.form((-47, -2), (-47, -44), 2.3, 1.7, WOOD, ao_start=False))
        o.append(geumjul(K, [(-35, -59), (-47, -40)], sag=2.5, seed=5))
    return "".join(o)


# ------------------------------------------------------------------ U-T2 저승사자
def death_envoy(K):
    """Tall pale envoy: black 도포 to the ground, 갓, 명부 scroll and rope. Composed, not monstrous."""
    b = DOPO
    o = [K.cast_shadow(0, 0, 25, 7)]
    o.append(ell(0, -3, 34, 6, "#9FB0C8", 0.16, ao_f(K)))
    o.append(wide_sleeve(K, (12, -128), (22, -102), (17, -80), b, droop=9, w=6.4, hand="grip", hand_m=PALE, back=True))
    # rope 오랏줄 hanging from the far hand
    o.append(stroke("M18 -74 q10 22 3 42 q-4 13 7 22", "#2A1F12", 2.2))
    o.append(stroke("M18 -74 q10 22 3 42 q-4 13 7 22", "#B8483A", 1.3))
    s, d = skirt(K, (-13, -88), (13, -88), (23, -5), (-23, -5), b, folds=7, seed=4, wave=1.6)
    o.append(s)
    t, td = K.torso((0, -133), (0, -85), 15, 12.5, b)
    o.append(t)
    # white 동정 + collar, 세조대 cord
    o.append(stroke("M-5 -133 L6 -108 M5 -133 L-4 -112", "#E8E2D0", 1.5))
    o.append(K.form((-12.5, -88), (12.5, -88), 1.3, 1.3, Mat("#8A2A20", "#D0604A", "#2A0805", "#6A3A3A"), ao_start=False, spec=False))
    o.append(stroke("M4 -87 q2 14 0 28 M6 -87 q5 14 5 26", "#A83A2C", 1.1))
    o.append(K.neck((0, -133), (0, -141), 3.7, PALE))
    look = dict(shape="long", eye="narrow", brow="thin", brow_col="#1A1A1E", nose="straight", nose_len=1.08, mouth="stern", age=0.15, turn=-0.15,
                cheek=0.85, eye_col="#141418", mouth_col="#5A4A4E", lip=0.25)
    h, _ = K.face(0, -151, 10, PALE, look)
    o.append(h)
    o.append(gat(K, 0, -158, 10.4))
    # near arm holds the 명부 scroll against the chest
    o.append(wide_sleeve(K, (-13, -128), (-22, -103), (-6, -96), b, droop=13, w=6.6, hand=None))
    sc, _ = cloth(K, [(-13, -116), (-3, -119), (-1, -84), (-11, -81)], HANJI, smooth=False)
    o.append(sc)
    for k in range(5):
        y = -111 + k * 5
        o.append(stroke(f"M-9.5 {y} l6 -1.3", "#2A2622", 0.8, 0.8))
    o.append(K.form((-14.5, -118), (-1, -121), 1.6, 1.6, WOOD_D, ao_start=False, spec=False))
    o.append(K.form((-12.5, -82), (0.6, -85), 1.6, 1.6, WOOD_D, ao_start=False, spec=False))
    o.append(K.hand((-8, -97), (5, 1), 4.6, PALE, kind="grip"))
    return "".join(o)


# ------------------------------------------------------------------ U-T3 무당
def _saekdong(K, a, b, c, w0, w1):
    """색동 sleeve: one sleeve silhouette, 오방색 stripes painted across it, one shading pass over the whole arm."""
    K._filters()
    cols = [RED, YELLOW, GREEN, HANJI, NAVY, RED, YELLOW, GREEN]
    S = K._catmull([a, b, c], 16)
    rows = []  # x, y, half-width, side normal, normal facing the light
    for j, (x, y, t) in enumerate(S):
        p, q = S[max(0, j - 1)], S[min(len(S) - 1, j + 1)]
        dx, dy = q[0] - p[0], q[1] - p[1]
        L = math.hypot(dx, dy) or 1.0
        n = (-dy / L, dx / L)
        lit = n if n[0] * LIGHT[0] + n[1] * LIGHT[1] >= 0 else (-n[0], -n[1])
        rows.append((x, y, w0 + (w1 - w0) * t, n, lit))

    def line(off):
        return " L".join(f"{f(x + nl[0] * w * off)} {f(y + nl[1] * w * off)}" for x, y, w, _, nl in rows)

    left = [(x + n[0] * w, y + n[1] * w) for x, y, w, n, _ in rows]
    right = [(x - n[0] * w, y - n[1] * w) for x, y, w, n, _ in rows]
    sil = ("M" + " L".join(f"{f(x)} {f(y)}" for x, y in left)
           + f" A{f(w1)} {f(w1)} 0 0 0 {f(right[-1][0])} {f(right[-1][1])} L"
           + " L".join(f"{f(x)} {f(y)}" for x, y in reversed(right))
           + f" A{f(w0)} {f(w0)} 0 0 0 {f(left[0][0])} {f(left[0][1])} Z")
    k = (len(rows) - 1) // len(cols)
    inner = [stroke(f"M{f(a[0])} {f(a[1])} L{f(rows[1][0])} {f(rows[1][1])}", cols[0].base, w0 * 2.4)]  # shoulder cap
    for i, m in enumerate(cols):
        sub = rows[i * k:(i + 1) * k + 1] if i < len(cols) - 1 else rows[i * k:]
        d = "M" + " L".join(f"{f(x)} {f(y)}" for x, y, *_ in sub)
        inner.append(f'<path d="{d}" fill="none" stroke="{m.base}" stroke-width="{f(max(w0, w1) * 2.6)}" stroke-linecap="butt"></path>')
    # shading sits over every stripe, so the arm reads as one form
    inner.append(f'<g filter="url(#{K.p}-soft)">'
                 f'<path d="M{line(-0.72)}" fill="none" stroke="#0A0612" stroke-opacity="0.62" stroke-width="{f(w0 * 0.95)}"></path>'
                 f'<path d="M{line(0.42)}" fill="none" stroke="#FFF8E6" stroke-opacity="0.34" stroke-width="{f(w0 * 0.42)}"></path></g>')
    return f'<path d="{sil}" fill="{cols[0].base}" stroke="#160C08" stroke-opacity="0.85" stroke-width="1.1" stroke-linejoin="round"></path>' + K.clip_overlay(sil, "".join(inner))


def braid_crown(K, fx, fy, r):
    """땋은 얹은머리: hair wound over the crown, tied with 오방색 ribbons. Same design as the Lobby/Results bust."""
    u = r / 23.5  # the bust's face radius sets the scale
    X = lambda x: fx + (x - 60) * u
    Y = lambda y: fy + (y - 64.5) * u
    hair = Mat("#120D0A", "#3A3028", "#040302", "#1E222C", spec="#8A90A2", spec_op=0.2, gloss=0.25)
    cap = (f"M{f(X(36))} {f(Y(64))} C{f(X(34))} {f(Y(48))} {f(X(44))} {f(Y(40))} {f(X(60))} {f(Y(40))} "
           f"C{f(X(76))} {f(Y(40))} {f(X(86))} {f(Y(48))} {f(X(84))} {f(Y(64))} "
           f"C{f(X(80))} {f(Y(54))} {f(X(70))} {f(Y(48.5))} {f(X(60.5))} {f(Y(47.5))} "
           f"C{f(X(50))} {f(Y(48.5))} {f(X(40))} {f(Y(54))} {f(X(36))} {f(Y(64))} Z")
    o = [f'<path d="{cap}" fill="#14100C" stroke="#050302" stroke-width="0.6"></path>']
    for i in range(9):  # braid wound over the crown
        x = 36.5 + i * 5.9
        y = 50 - 10 * (1 - ((x - 60) / 26) ** 2)
        o.append(K.ball(X(x), Y(y), r * 0.215, hair, ry=r * 0.18))
    for i, c in enumerate((OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#2A2622")):
        d = (f"M{f(X(81))} {f(Y(44))} C{f(X(87))} {f(Y(52 + i))} {f(X(85 + i * 1.6))} {f(Y(66 + i * 3))} "
             f"{f(X(88 + i * 2.2))} {f(Y(80 + i * 3))}")
        o.append(stroke(d, "#120D08", 1.3, 0.75) + stroke(d, c, 0.85))
    o.append(K.ball(X(81), Y(44), r * 0.14, MATS["brass"]))
    return "".join(o)


def _ribbon(K, a, b, c, col, w=1.7):
    m = Mat(col, shade(col, 0.3), shade(col, -0.55), mix(shade(col, -0.2), "#6A7FA0", 0.3), spec_op=0.12, gloss=0.2)
    return K.tube([a, b, c], [w, w * 0.92, w * 0.7], m, n_per=5, chunks_per_seg=2)


def mudang(K, raised=True, cheek=None):
    """Mid-dance: 방울 raised high, 부채 extended, skirts flaring as she turns. Calm and dignified."""
    o = [K.cast_shadow(0, 0, 32, 7.5)]
    # sashes fly behind the turn
    cols = [OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#3A3632"]
    for i, c in enumerate(cols):
        o.append(_ribbon(K, (9, -70), (34 + i * 3.2, -68 + i * 7), (52 + i * 3, -48 + i * 11), c))
    s0, _ = skirt(K, (-10, -72), (11, -72), (42, -5), (-33, -3), RED, folds=9, seed=8, hem=OBANG["황"], hem_h=4.0, wave=2.6)
    o.append(s0)
    # sleeveless 쾌자 (navy), split front, gold hem
    s1, _ = skirt(K, (-11, -80), (11, -80), (24, -34), (-21, -30), NAVY, folds=6, seed=9, hem=GOLD, hem_h=2.8, wave=1.5)
    o.append(s1)
    o.append(_saekdong(K, (10, -97), (28, -86), (44, -92), 5.2, 4.2))
    t, _ = K.torso((0, -102), (0, -71), 12.5, 10, NAVY)
    o.append(t)
    o.append(stroke("M-5 -102 L4 -82 M5 -102 L-3 -85", "#F6EFD8", 1.5))
    o.append(stroke("M2 -84 q-3 10 -6 19 M2 -84 q0 11 1 20", OBANG["적"], 1.7))
    o.append(K.form((-10.5, -74), (10.5, -74), 2.5, 2.5, Mat("#B23A26", "#EA7C54", "#340A05", "#7A3A46"), ao_start=False, spec=False))
    o.append(K.neck((0, -102), (0, -110), 3.2, SKIN))
    look = cheek or dict(shape="oval", fem=True, eye="almond", brow="thin", nose="small", mouth="calm", age=0.3, turn=-0.3, cheek=0.5, lid="double",
                         gaze=(-0.4, -0.5), lip=0.5)
    # hair at the nape
    o.append(ell(4, -115, 10.2, 11, "#14100C"))
    h, _ = K.face(0, -117, 9.6, SKIN, look)
    o.append(h)
    o.append(braid_crown(K, 0, -117, 9.6))
    if raised:
        o.append(_saekdong(K, (-10, -97), (-22, -106), (-27, -128), 5.2, 4.2))
        o.append(K.hand((-27, -129), (-1, -7), 3.8, SKIN, kind="grip"))
        o.append(bell_cluster(K, -28, -151, 1.3))
    else:
        o.append(_saekdong(K, (-10, -97), (-22, -88), (-31, -96), 5.2, 4.2))
        o.append(K.hand((-32, -97), (-5, -1), 3.8, SKIN, kind="grip"))
        o.append(bell_cluster(K, -38, -114, 1.3))
    o.append(fan(K, 50, -96, -28, 1.0))
    o.append(K.hand((45, -93), (5, -2), 3.8, SKIN, kind="grip"))
    return "".join(o)


# ------------------------------------------------------------------ U-T4 장승
def _bark(K, x0, x1, y0, y1, seed, m):
    rng = random.Random(seed)
    out = []
    for i in range(16):
        x = rng.uniform(x0, x1)
        y = rng.uniform(y0, y1 - 18)
        lit = 1 - (x - x0) / max(1, x1 - x0)
        out.append(stroke(f"M{f(x)} {f(y)} q{f(rng.uniform(-2, 2))} {f(rng.uniform(8, 14))} {f(rng.uniform(-1.5, 1.5))} {f(rng.uniform(16, 28))}",
                          m.core if rng.random() < 0.7 else m.light, rng.uniform(0.6, 1.2), 0.25 + 0.3 * (1 - lit) if rng.random() < 0.7 else 0.3 * lit))
    return "".join(out)


def _jangseung_one(K, label, female=False, lean=4.0, step=1, seed=1):
    """One carved post with a large face, a vertical inscription and root feet. Local base (0, 0)."""
    m = WOOD_D if female else WOOD
    h = 138 if female else 158
    tw = 20 if female else 23
    o = []
    # root feet: walking, one root planted and one lifted
    for (a, b, c, wr) in (((-6, -18), (-22, -10), (-34, -2), 5.2), ((6, -18), (20, -11), (32, -3 - step * 5), 5.2), ((-2, -16), (-8, -6), (-14, 2), 4.4), ((3, -16), (10, -7), (15, 0), 4.2)):
        o.append(K.tube([a, b, c], [wr, wr * 0.7, wr * 0.32], m, n_per=5, chunks_per_seg=2))
    o.append(K.tube([(0, -12), (lean * 0.4, -h * 0.5), (lean, -h)], [tw, tw * 0.93, tw * 0.9], m, n_per=6))
    top = (lean, -h)
    o.append(_bark(K, -tw + 2, tw - 2, -h * 0.5, -10, seed, m))
    # 관모: a flat-topped carved crown over a wide brim; the general's has side wings
    cy = -h + 2
    cap = m.back(0.1)
    crown, cd = cloth(K, [(lean - tw * 0.86, cy), (lean - tw * 0.8, cy - 19), (lean + tw * 0.8, cy - 19), (lean + tw * 0.86, cy)], cap, smooth=False, w=tw)
    o.append(crown + K.clip_overlay(cd, stroke(f"M{f(lean - tw)} {f(cy - 7)} H{f(lean + tw)}", m.core, 1.4, 0.8) + stroke(f"M{f(lean - tw)} {f(cy - 8.4)} H{f(lean + tw)}", m.light, 0.8, 0.5)))
    if not female:
        for s_ in (-1, 1):
            w_, _ = cloth(K, [(lean + s_ * tw * 0.8, cy - 16), (lean + s_ * (tw + 11), cy - 21), (lean + s_ * (tw + 12), cy - 13), (lean + s_ * tw * 0.84, cy - 8)], cap, smooth=False, w=8)
            o.append(w_)
    o.append(K.tube([(lean - tw - 2, cy), (lean + tw + 2, cy)], [3.4, 3.4], m, n_per=3, chunks_per_seg=1))
    fy = -h + 25
    fx = lean - 1
    # carved brow ridge
    o.append(K.tube([(fx - 14, fy - 8), (fx - 5, fy - 11), (fx, fy - 7)], [2.8, 3.2, 2.2], m, n_per=4, chunks_per_seg=2))
    o.append(K.tube([(fx + 14, fy - 8), (fx + 5, fy - 11), (fx, fy - 7)], [2.8, 3.2, 2.2], m, n_per=4, chunks_per_seg=2))
    er = 4.8 if female else 5.8
    for s in (-1, 1):
        ex = fx + s * 9.6
        o.append(ell(ex, fy - 1, er + 1.5, er + 1.3, "#120A05", 0.85))
        o.append(K.ball(ex, fy - 1, er, Mat("#B89E78", "#E6D4AE", "#3A2A18", "#6A6058", spec_op=0.12, gloss=0.15), outline=False))
        o.append(f'<circle cx="{f(ex)}" cy="{f(fy - 0.6)}" r="{f(er * 0.5)}" fill="#120A05"></circle>')
        o.append(stroke(f"M{f(ex - er)} {f(fy - er * 0.5)} Q{f(ex)} {f(fy - er * 1.25)} {f(ex + er)} {f(fy - er * 0.5)}", "#120A05", 1.3, 0.85))
    # broad carved nose
    o.append(K.tube([(fx, fy - 4), (fx - 0.5, fy + 4), (fx - 0.5, fy + 9)], [2.6, 3.4, 5.2], m, n_per=4, chunks_per_seg=2))
    o.append(ell(fx - 3, fy + 11, 1.1, 0.8, "#120A05", 0.9))
    o.append(ell(fx + 2.4, fy + 11, 1.1, 0.8, "#120A05", 0.9))
    # mouth: carved opening with a row of teeth; the general has tusks, the 여장군 a closed line of teeth
    my = fy + 18
    o.append(f'<path d="M{f(fx - 11)} {f(my - 2)} Q{f(fx)} {f(my + 1)} {f(fx + 11)} {f(my - 2)} Q{f(fx)} {f(my + 8)} {f(fx - 11)} {f(my - 2)} Z" fill="#120A05"></path>')
    for k in range(6):
        tx = fx - 8 + k * 3.2
        o.append(f'<rect x="{f(tx)}" y="{f(my - 0.2 + abs(k - 2.5) * -0.3)}" width="2.5" height="2.6" fill="#E2DAC2"></rect>')
    if not female:
        for s in (-1, 1):
            o.append(f'<path d="M{f(fx + s * 8.2)} {f(my + 1)} L{f(fx + s * 9.8)} {f(my - 8)} L{f(fx + s * 5.8)} {f(my + 1)} Z" fill="#F0E8D0" stroke="#3A2A18" stroke-width="0.5"></path>')
        # carved beard lines
        for k in range(7):
            x = fx - 10 + k * 3.3
            o.append(stroke(f"M{f(x)} {f(my + 6)} q{f((k - 3) * 0.5)} 6 {f((k - 3) * 0.8)} 12", m.core, 1.2, 0.8))
    # inscription: carved panel with black ink glyphs, read top to bottom
    py = fy + (34 if not female else 28)
    pw = 12
    gh = 11.2
    ph = gh * len(label) + 3
    o.append(f'<rect x="{f(fx - pw / 2)}" y="{f(py)}" width="{pw}" height="{f(ph)}" rx="1" fill="#D2B98A" stroke="#2A1608" stroke-width="0.8"></rect>')
    o.append(f'<rect x="{f(fx - pw / 2 + 0.8)}" y="{f(py + 0.8)}" width="2" height="{f(ph - 1.6)}" fill="#F0DCB0" opacity="0.7"></rect>')
    for i, ch in enumerate(label):
        o.append(f'<text x="{f(fx)}" y="{f(py + 10.4 + i * gh)}" text-anchor="middle" font-family="Nanum Myeongjo, serif" font-weight="800" font-size="10.4" fill="#1A0E06">{ch}</text>')
    return "".join(o)


def jangseung_pair(K):
    o = [K.cast_shadow(-34, 2, 42, 8), K.cast_shadow(36, 2, 40, 8)]
    o.append(f'<g transform="translate(40 -10)">{_jangseung_one(K, "地下女將軍", female=True, lean=-3, step=-1, seed=7)}</g>')
    o.append(f'<g transform="translate(-34 0)">{_jangseung_one(K, "天下大將軍", female=False, lean=4, step=1, seed=4)}</g>')
    # a thin 금줄 tied between the pair
    o.append(geumjul(K, [(-16, -82), (24, -80)], sag=7, seed=9))
    return "".join(o)


# ------------------------------------------------------------------ U-T5 솟대 오리
def sotdae_duck(K, alt=112):
    """A carved wooden duck in flight, its pole trailing behind with 오방색 ribbons. Ground shadow far below."""
    o = [K.cast_shadow(6, 0, 24, 6, 0.42)]
    m = WOOD
    y = -alt
    # trailing pole
    o.append(K.form((-2, y + 5), (34, y + 72), 2.1, 1.6, WOOD_D, ao_start=False))
    for i, c in enumerate((OBANG["청"], OBANG["적"], OBANG["황"], OBANG["백"], "#3A3632")):
        bx, by = 17 + i * 1.1, y + 39 + i * 2.2
        o.append(_ribbon(K, (bx, by), (bx + 14 + i * 2, by + 2 + i * 3), (bx + 26 + i * 3, by + 16 + i * 2), c, w=1.4))
    # far wing
    far = [(4, y - 3), (20, y - 26), (46, y - 34), (40, y - 18), (28, y - 5), (12, y + 3)]
    b, d = cloth(K, far, m.back(0.28), n=(-0.4, -0.9), w=24)
    o.append(b + K.clip_overlay(d, "".join(stroke(f"M{f(10 + k * 6)} {f(y - 1)} L{f(28 + k * 5)} {f(y - 28 + k)}", "#120A05", 0.8, 0.6) for k in range(4))))
    # body + tail
    o.append(K.tube([(26, y + 6), (8, y + 2), (-14, y - 1), (-26, y - 9)], [2.5, 11, 12, 7], m, n_per=5, chunks_per_seg=2))
    o.append(f'<path d="M24 {y + 4} L44 {y - 2} L37 {y + 6} L45 {y + 11} L24 {y + 10} Z" fill="{m.base}" stroke="{OUTL}" stroke-width="0.7"></path>')
    # carved feathers on the body
    for k in range(5):
        x = -8 + k * 6
        o.append(stroke(f"M{x} {y - 8} q5 7 1 15", m.core, 1.0, 0.65))
        o.append(stroke(f"M{x - 1.2} {y - 8} q5 7 1 15", m.light, 0.6, 0.4))
    # neck, head, bill
    o.append(K.tube([(-19, y - 6), (-30, y - 15), (-37, y - 24)], [7, 5, 5.2], m, n_per=4, chunks_per_seg=2))
    o.append(K.ball(-39, y - 27, 7.2, m))
    o.append(f'<path d="M-44 {y - 30} L-60 {y - 24} Q-59 {y - 21} -44 {y - 22.5} Z" fill="#B8863A" stroke="{OUTL}" stroke-width="0.8"></path>')
    o.append(stroke(f"M-45 {y - 25.5} L-58 {y - 24}", "#3A2408", 0.8))
    o.append(f'<circle cx="-41" cy="{y - 29}" r="1.6" fill="#120A05"></circle><circle cx="-41.6" cy="{y - 29.6}" r="0.5" fill="#FFF"></circle>')
    # near wing, raised, with carved rows
    near = [(-4, y - 3), (-2, y - 26), (14, y - 50), (38, y - 62), (34, y - 42), (26, y - 22), (10, y + 4)]
    b, d = cloth(K, near, m, n=(-0.6, -0.8), w=30)
    inner = "".join(stroke(f"M{f(2 + k * 5)} {f(y)} Q{f(8 + k * 5)} {f(y - 26)} {f(20 + k * 4)} {f(y - 52 + k * 2)}", m.core, 1.0, 0.6) for k in range(5))
    inner += "".join(stroke(f"M{f(0 + k * 5)} {f(y)} Q{f(6 + k * 5)} {f(y - 26)} {f(18 + k * 4)} {f(y - 52 + k * 2)}", m.light, 0.6, 0.5) for k in range(5))
    o.append(b + K.clip_overlay(d, inner))
    return "".join(o)


# ------------------------------------------------------------------ U-T6 오방신장
GENERAL_MATS = {"청": Mat("#256A8A", "#66B4D2", "#071E2A", "#3E6A82", spec_op=0.25, gloss=0.3),
                "적": Mat("#B23420", "#F08058", "#340804", "#7A3A44", spec_op=0.25, gloss=0.3),
                "황": Mat("#C89428", "#FFDC82", "#3E2806", "#8A7450", spec_op=0.35, gloss=0.4),
                "백": Mat("#CEC8B6", "#FFFDF2", "#544E3E", "#8A96AA", spec_op=0.3, gloss=0.3),
                "흑": Mat("#26242A", "#6A6874", "#040406", "#4A6E96", spec_op=0.35, gloss=0.4)}


def general(K, key, look, flag_side=1, sword=False, cape=True):
    """One armoured 신장: lamellar coat in its colour, iron helmet with plume, tall flag in one hand."""
    m = GENERAL_MATS[key]
    col = OBANG[key]
    o = [K.cast_shadow(0, 0, 26, 7)]
    if cape:
        c, _ = skirt(K, (-14, -112), (15, -112), (29 * flag_side + 2, -14), (-23 * flag_side - 2, -10), m.back(0.3), folds=5, seed=ord(key[0]) % 97, wave=2.2)
        o.append(c)
    o.append(K.leg((6, -64), (10, -34), (11, -6), BLACK, w=7, boot=MATS["leather_dark"], trouser=BLACK, back=True))
    o.append(K.leg((-6, -64), (-10, -34), (-12, -6), BLACK, w=7, boot=MATS["leather_dark"], trouser=BLACK, foot_dir=-1))
    # lamellar skirt
    s, d = skirt(K, (-14, -76), (14, -76), (20, -36), (-20, -36), m, folds=0, seed=3, hem=None, wave=1.4)
    o.append(s + lamellar(K, d, -22, -78, 22, -30, m, pw=4.6, ph=5.8))
    # torso
    t, td = K.torso((0, -112), (0, -72), 15, 13, m)
    o.append(t + lamellar(K, td, -18, -114, 18, -70, m, pw=4.2, ph=5.2))
    o.append(K.form((-13.5, -74), (13.5, -74), 3, 3, BRASS, ao_start=False, spec=True))
    o.append(K.ball(0, -74, 3.6, BRASS))
    o.append(K.ball(0, -98, 5.4, BRASS))
    o.append(f'<circle cx="0" cy="-98" r="2.6" fill="{col}" stroke="#2A1A06" stroke-width="0.5"></circle>')
    # shoulders
    for s_ in (-1, 1):
        o.append(K.ball(s_ * 15.5, -106, 7.6, IRON if s_ < 0 else IRON.back(0.15)))
        o.append(stroke(f"M{s_ * 9} {-104} q{s_ * 7} 7 {s_ * 14} 3", "#141618", 0.9, 0.7))
    o.append(K.neck((0, -112), (0, -119), 4, SKIN_T))
    h, _ = K.face(0, -128, 9.8, look.pop("skin", SKIN_T), look)
    o.append(h)
    # helmet: iron bowl with coloured brim band and plume
    o.append(ell(0, -133, 12, 4, "#0A0604", 0.5, ao_f(K)))
    o.append(K.ball(0, -139, 11.2, IRON, ry=9.8))
    o.append(K.form((-11, -134.5), (11, -134.5), 2, 2, BRASS, ao_start=False, spec=True))
    for k in (-6, 0, 6):
        o.append(stroke(f"M{k} -135 Q{k * 0.8} -143 {k * 0.35} -148", "#141618", 0.9, 0.7))
    o.append(K.form((0, -148), (0, -156), 1.6, 0.8, BRASS, ao_start=False, spec=False))
    for k in range(5):
        o.append(stroke(f"M0 -155 q{f(-flag_side * (4 + k * 3))} {f(-8 + k)} {f(-flag_side * (10 + k * 4))} {f(-1 + k * 3)}", col if key != "흑" else "#8A8690", 1.5))
    # arms: one grips the flag pole, the other a sword point-down or a fist on the belt
    px = -30 * flag_side
    o.append(K.arm((-15.5 * flag_side, -106), (-27 * flag_side, -90), (px, -112), m, m_fore=IRON, w=5.8, hand="grip", hand_dir=(0, -1), hand_m=SKIN_T))
    o.append(flag(K, (px, -3), (px, -178), col, side=-flag_side, w=34, h=23, wave=3))
    if sword:
        o.append(K.arm((15.5 * flag_side, -106), (24 * flag_side, -86), (13 * flag_side, -70), m, m_fore=IRON, w=5.8, hand="grip", hand_m=SKIN_T))
        o.append(K.form((15 * flag_side, -66), (20 * flag_side, -10), 1.9, 0.7, IRON, ao_start=False))
        o.append(K.form((10 * flag_side, -70), (20 * flag_side, -66), 1.5, 1.5, BRASS, ao_start=False, spec=False))
    else:
        o.append(K.arm((15.5 * flag_side, -106), (25 * flag_side, -88), (15 * flag_side, -76), m, m_fore=IRON, w=5.8, hand="fist", hand_m=SKIN_T))
    return "".join(o)


def obang_generals(K, compact=False):
    """Five 신장 in a shallow V, 황 at the centre and larger; 흑 and 백 behind, 청 and 적 in the wings."""
    looks = {"흑": dict(shape="square", eye="narrow", brow="thick", nose="broad", mouth="stern", age=0.6, beard="full", beard_col="#141210", turn=0.15, cheek=0.7, skin=OLD),
             "백": dict(shape="long", eye="sharp", brow="straight", nose="straight", mouth="stern", age=0.8, beard="long", beard_col="#D8D4C8", turn=-0.15, cheek=0.8, skin=SKIN),
             "청": dict(shape="oval", eye="almond", brow="arched", nose="straight", mouth="calm", age=0.2, beard="mustache", turn=0.2, cheek=0.5, skin=SKIN),
             "적": dict(shape="diamond", eye="sharp", brow="straight", nose="hooked", mouth="stern", age=0.4, beard="goatee", turn=-0.2, cheek=0.7, skin=MATS["skin_ruddy"]),
             "황": dict(shape="square", jaw=1.05, eye="sharp", brow="thick", nose="straight", mouth="stern", age=0.45, beard="full", beard_col="#20160E", turn=-0.05, cheek=0.7, skin=SKIN_T)}
    place = [("흑", -46, -16, 0.72, 1, False), ("백", 50, -16, 0.72, -1, False), ("청", -92, -2, 0.82, 1, False), ("적", 96, -2, 0.82, -1, False), ("황", 0, 8, 1.0, 1, True)]
    o = []
    for key, x, y, s, side, sw in place:
        o.append(f'<g transform="translate({x} {y}) scale({s})">{general(K, key, dict(looks[key]), flag_side=side, sword=sw)}</g>')
    return "".join(o)


# ------------------------------------------------------------------ H-T1 바리공주
def medicine_flask(K, cx, cy, s=1.0):
    """호리병: a two-bulb gourd flask of medicine water, stopper and red cord."""
    g = Mat("#C8B478", "#F6E8B4", "#3A2E10", "#7E8A84", spec_op=0.5, gloss=0.6)
    return (K.ball(cx, cy + 9 * s, 8.4 * s, g) + K.ball(cx, cy - 3 * s, 5.4 * s, g)
            + K.form((cx, cy - 8 * s), (cx, cy - 12 * s), 1.8 * s, 1.5 * s, WOOD, ao_start=False, spec=False)
            + stroke(f"M{f(cx - 3.8 * s)} {f(cy + 1.8 * s)} Q{f(float(cx))} {f(cy + 3.6 * s)} {f(cx + 3.8 * s)} {f(cy + 1.8 * s)}", OBANG["적"], 1.1 * s))


def flower(K, cx, cy, col, s=1.0):
    out = []
    for k in range(5):
        a = k * math.tau / 5 - math.pi / 2
        out.append(ell(cx + math.cos(a) * 2.8 * s, cy + math.sin(a) * 2.8 * s, 2.3 * s, 2.3 * s, col))
    out.append(ell(cx - 0.6 * s, cy - 0.8 * s, 1.5 * s, 1.1 * s, "#FFF8E0", 0.5))
    out.append(ell(cx, cy, 1.4 * s, 1.4 * s, GOLD))
    return "".join(out)


def bari(K):
    """바리공주: young, calm, in layered 무복, holding the flask low and a sprig of flowers. Souls follow elsewhere."""
    robe = Mat("#D6CFBA", "#FFFCF0", "#4E4A3C", "#7E90B0", spec_op=0.2, gloss=0.15)
    jade = Mat("#2E6E62", "#74C4AC", "#071E1A", "#44687A", spec_op=0.22, gloss=0.25)
    o = []
    # long hair behind
    o.append(K.tube([(3, -126), (8, -96), (12, -64)], [8, 7, 3], MATS["hair"], n_per=6))
    # back sash
    o.append(_ribbon(K, (10, -80), (26, -76), (38, -62), OBANG["적"], w=0.8))
    o.append(_ribbon(K, (10, -78), (24, -70), (32, -52), OBANG["황"], w=0.7))
    o.append(wide_sleeve(K, (12, -103), (24, -84), (28, -66), jade, droop=14, w=6.2, hand=None, back=True))
    s0, _ = skirt(K, (-13, -74), (13, -74), (30, -3), (-29, -2), robe, folds=10, seed=12, hem=OBANG["청"], hem_h=4, wave=2.2)
    o.append(s0)
    # jade 원삼-like outer robe, open at the front
    for side in (-1, 1):
        a = (side * 2.5, -106)
        b = (side * 15, -106)
        c = (side * 28, -30)
        e = (side * 8, -24)
        pts = [a, b, c, e] if side > 0 else [b, a, e, c]
        s1, _ = skirt(K, pts[0], pts[1], pts[2], pts[3], jade, folds=4, seed=13 + side, hem=GOLD, hem_h=3, wave=1.5)
        o.append(s1)
    t, _ = K.torso((0, -112), (0, -76), 11, 9.5, robe, outline=False)
    o.append(t)
    o.append(stroke("M-3.5 -112 L3 -92 M3.5 -112 L-2.5 -94", "#8A8270", 0.9))
    o.append(K.form((-11, -82), (11, -82), 2.6, 2.6, RED, ao_start=False, spec=False))
    o.append(stroke("M1 -80 q-2 16 -4 34 M2 -80 q1 16 2 32", "#B8362A", 1.6))
    o.append(ell(-1, -52, 2.8, 2.8, GOLD))
    o.append(K.neck((0, -112), (0, -119), 3.1, SKIN))
    look = dict(shape="oval", fem=True, eye="narrow", brow="thin", nose="small", mouth="calm", age=0.05, turn=-0.1, cheek=0.35, lid="mono", gaze=(0, 0.3), lip=0.42, blush=False)
    o.append(ell(0, -127, 10.6, 11.6, "#14100C"))
    h, _ = K.face(0, -126, 9.6, SKIN, look)
    o.append(h)
    # hair parting
    o.append(fill("M0 -136.5 Q-9 -135 -9.6 -124 Q-6 -131 0 -133 Q6 -131 9.6 -124 Q9 -135 0 -136.5 Z", "#14100C"))
    # 화관: a small flower crown of the princess
    o.append(K.form((-8, -136), (8, -136), 1.9, 1.9, BRASS, ao_start=False, spec=True))
    for dx, c_ in ((-7, OBANG["적"]), (-3.5, OBANG["황"]), (0, "#FFF8E0"), (3.5, OBANG["청"]), (7, OBANG["적"])):
        o.append(flower(K, dx, -140 - (3 - abs(dx) * 0.3), c_, 0.62))
    for dx in (-9, 9):
        o.append(stroke(f"M{dx} -136 l{0.6 if dx > 0 else -0.6} 13", GOLD, 0.7))
        o.append(ell(dx * 1.06, -122, 1.1, 1.5, "#8AD0B8"))
    # near arm low with the medicine flask
    o.append(wide_sleeve(K, (-11, -103), (-24, -84), (-23, -60), jade, droop=15, w=6.4, hand=None))
    o.append(medicine_flask(K, -23, -40, 0.95))
    o.append(K.hand((-22.5, -57), (0, 5), 4.3, SKIN, kind="grip"))
    # far hand raises a sprig of flowers
    o.append(K.hand((28, -64), (2, -5), 4.1, SKIN.back(0.1), kind="grip"))
    o.append(K.form((29, -60), (35, -96), 0.9, 0.5, Mat("#4A7A44", "#8AC070", "#102408", "#4A6460"), ao_start=False, spec=False))
    for x, y, c_ in ((36, -99, "#F4F0E0"), (31, -92, "#E8A0B0"), (39, -89, "#F4F0E0"), (33, -83, "#E8A0B0")):
        o.append(flower(K, x, y, c_, 0.82))
    return "".join(o)


def soul(K, cx, cy, s=1.0, op=0.8):
    """A faint guided soul: a pale teardrop with the suggestion of a robed head, never a face of horror."""
    return (f'<g opacity="{f(float(op))}">'
            + f'<path d="M{f(float(cx))} {f(cy - 14 * s)} C{f(cx + 8 * s)} {f(cy - 8 * s)} {f(cx + 8 * s)} {f(cy + 6 * s)} {f(cx + 2 * s)} {f(cy + 16 * s)} C{f(cx)} {f(cy + 8 * s)} {f(cx - 6 * s)} {f(cy + 12 * s)} {f(cx - 5 * s)} {f(cy + 18 * s)} C{f(cx - 10 * s)} {f(cy + 4 * s)} {f(cx - 9 * s)} {f(cy - 8 * s)} {f(float(cx))} {f(cy - 14 * s)} Z" fill="#CFE8F0" opacity="0.5"{ao_f(K)}></path>'
            + f'<path d="M{f(float(cx))} {f(cy - 12 * s)} C{f(cx + 5.6 * s)} {f(cy - 7 * s)} {f(cx + 5.4 * s)} {f(cy + 4 * s)} {f(cx + 1 * s)} {f(cy + 12 * s)} C{f(cx - 3 * s)} {f(cy + 8 * s)} {f(cx - 6 * s)} {f(cy + 4 * s)} {f(cx - 6 * s)} {f(cy - 4 * s)} C{f(cx - 6 * s)} {f(cy - 8 * s)} {f(cx - 3 * s)} {f(cy - 11 * s)} {f(float(cx))} {f(cy - 12 * s)} Z" fill="#F4FBFF" opacity="0.72"></path>'
            + "</g>")


# ------------------------------------------------------------------ H-T2 강림도령
def gangnim(K):
    """Mid-stride in a red 철릭, rope swung out, the red summons scroll held high. Young and bold."""
    r = Mat("#B83422", "#F2835A", "#360804", "#7E3A46", spec_op=0.22, gloss=0.25)
    o = []
    # wind-blown skirts of the 철릭 behind the stride
    c, _ = skirt(K, (-12, -76), (13, -76), (52, -32), (6, -16), r.back(0.2), folds=6, seed=21, hem=GOLD, hem_h=2.5, wave=2)
    o.append(c)
    # legs in a long stride: rear leg pushes off, front knee drives forward
    o.append(K.leg((6, -68), (24, -42), (40, -14), BLACK, w=7.2, trouser=WHITE, wrap=None, boot=MATS["leather_dark"], foot_dir=1, back=True))
    o.append(K.arm((13, -108), (30, -100), (46, -116), r, w=4.6, sleeve=r, hand="grip", hand_dir=(2, -3), hand_m=SKIN_T, back=True))
    o.append(K.leg((-6, -68), (-24, -46), (-22, -8), BLACK, w=7.4, trouser=WHITE, wrap=None, boot=MATS["leather_dark"], foot_dir=-1))
    s, _ = skirt(K, (-13, -78), (13, -78), (14, -42), (-30, -38), r, folds=6, seed=22, hem=GOLD, hem_h=2.5, wave=1.6)
    o.append(s)
    t, _ = K.torso((-1, -113), (0, -74), 14.5, 12, r)
    o.append(t)
    o.append(stroke("M-6 -113 L5 -90 M4 -113 L-5 -93", "#F6EFD8", 1.6))
    o.append(K.form((-12.5, -78), (12.5, -78), 3, 3, BLACK, ao_start=False))
    o.append(K.ball(0, -78, 3, BRASS))
    # the 패도 at his belt
    o.append(K.form((8, -76), (30, -52), 1.8, 1.5, MATS["lacquer"], ao_start=False))
    o.append(K.neck((-1, -113), (-2, -120), 3.8, SKIN_T))
    look = dict(shape="heart", jaw=1.05, eye="sharp", brow="thick", nose="straight", mouth="smirk", age=0.08, turn=-0.3, cheek=0.55, lid="double", gaze=(-0.5, -0.1))
    h, _ = K.face(-2, -126, 9.9, SKIN_T, look)
    o.append(h)
    # 벙거지 (felt envoy hat) with a red tassel
    o.append(ell(-2, -131, 10, 2.4, "#0A0604", 0.28, ao_f(K)))
    gid = K._sphere_grad(-2, -133, 18, FELT)
    o.append(f'<ellipse cx="-2" cy="-132.5" rx="17" ry="4.2" fill="url(#{gid})" stroke="{OUTL}" stroke-width="0.7"></ellipse>')
    o.append(K.ball(-2, -139, 9.4, FELT, ry=9.2))
    o.append(K.ball(-2, -148.5, 2, BRASS))
    for k in range(5):
        o.append(stroke(f"M-2 -148 q{f(5 + k * 1.6)} {f(-3 + k)} {f(12 + k * 2.4)} {f(1 + k * 2.4)}", OBANG["적"], 1.4))
    # near arm thrusts the summons scroll forward and high
    o.append(K.arm((-13, -108), (-30, -112), (-46, -134), r, w=4.8, sleeve=r, hand="grip", hand_dir=(-1.2, -2), hand_m=SKIN_T))
    sc = [(-58, -176), (-41, -180), (-37, -134), (-54, -130)]
    b, d = cloth(K, sc, Mat("#C82E20", "#F2694A", "#44100A", "#8A4A50", spec_op=0.12, gloss=0.1), smooth=False)
    o.append(b)
    for k in range(5):
        y = -171 + k * 7.4
        o.append(stroke(f"M-53 {f(y + 1)} L-42 {f(y - 1)}", "#FFF2DC", 1.2, 0.85))
    o.append(K.form((-60, -177), (-39, -181), 1.8, 1.8, WOOD_D, ao_start=False, spec=False))
    o.append(K.form((-56, -129), (-35, -133), 1.8, 1.8, WOOD_D, ao_start=False, spec=False))
    return "".join(o)


def lasso(K, wr=(46, -116)):
    """오랏줄 swung in a wide loop from the rear hand."""
    d = f"M{P(wr)} q18 -30 44 -24 q26 8 12 34 q-18 22 -38 2 q-10 -14 8 -26 q14 -7 22 4"
    return stroke(d, "#2A1A0C", 3.2) + stroke(d, "#C84A3A", 2.0) + f'<path d="{d}" fill="none" stroke="#7A1E14" stroke-width="2" stroke-dasharray="1.2 2.6"></path>'


# ------------------------------------------------------------------ H-T3 산군
def _tiger_head(K, cx, cy, r):
    o = []
    # ears
    for s in (-1, 1):
        o.append(K.ball(cx + s * r * 0.76, cy - r * 0.76, r * 0.3, TIGER))
        o.append(ell(cx + s * r * 0.76, cy - r * 0.72, r * 0.15, r * 0.17, "#2A1408"))
    # ruff of white cheek fur
    for s in (-1, 1):
        for k in range(5):
            a = (35 + k * 22) * math.pi / 180
            x0, y0 = cx + s * math.cos(a) * r * 0.74, cy + math.sin(a) * r * 0.72
            x1, y1 = cx + s * math.cos(a) * r * 1.1, cy + math.sin(a) * r * 1.06
            o.append(K.form((x0, y0), (x1, y1), r * 0.22, r * 0.08, FUR_W, ao_start=False, spec=False, outline=False))
    o.append(K.ball(cx, cy, r, TIGER, ry=r * 0.94))
    # forehead + cheek stripes
    for s in (-1, 1):
        for k in range(3):
            y = cy - r * 0.2 + k * r * 0.28
            o.append(fill(f"M{f(cx + s * r * 0.96)} {f(y)} Q{f(cx + s * r * 0.68)} {f(y - r * 0.06)} {f(cx + s * r * 0.48)} {f(y + r * 0.1)} Q{f(cx + s * r * 0.7)} {f(y + r * 0.08)} {f(cx + s * r * 0.94)} {f(y + r * 0.13)} Z", "#1A0C04", 0.92))
        o.append(fill(f"M{f(cx + s * r * 0.08)} {f(cy - r * 0.9)} Q{f(cx + s * r * 0.3)} {f(cy - r * 0.62)} {f(cx + s * r * 0.14)} {f(cy - r * 0.36)} Q{f(cx + s * r * 0.15)} {f(cy - r * 0.62)} {f(cx + s * r * 0.02)} {f(cy - r * 0.86)} Z", "#1A0C04", 0.9))
        o.append(fill(f"M{f(cx + s * r * 0.5)} {f(cy - r * 0.76)} Q{f(cx + s * r * 0.42)} {f(cy - r * 0.54)} {f(cx + s * r * 0.26)} {f(cy - r * 0.44)} Q{f(cx + s * r * 0.5)} {f(cy - r * 0.48)} {f(cx + s * r * 0.62)} {f(cy - r * 0.66)} Z", "#1A0C04", 0.88))
    o.append(fill(f"M{f(cx - r * 0.2)} {f(cy - r * 0.5)} L{f(cx + r * 0.2)} {f(cy - r * 0.5)} L{f(cx + r * 0.2)} {f(cy - r * 0.44)} L{f(cx - r * 0.2)} {f(cy - r * 0.44)} Z", "#1A0C04", 0.9))
    # white around eyes, eyes
    for s in (-1, 1):
        ex = cx + s * r * 0.36
        ey = cy - r * 0.16
        o.append(ell(ex, ey + r * 0.04, r * 0.26, r * 0.17, "#FBF4DC", 0.9))
        o.append(fill(f"M{f(ex - s * r * 0.17)} {f(ey + r * 0.05)} Q{f(float(ex))} {f(ey - r * 0.13)} {f(ex + s * r * 0.2)} {f(ey - r * 0.05)} Q{f(float(ex))} {f(ey + r * 0.11)} {f(ex - s * r * 0.17)} {f(ey + r * 0.05)} Z", "#D89A28"))
        o.append(ell(ex, ey - r * 0.01, r * 0.052, r * 0.08, "#0A0604"))
        o.append(ell(ex - r * 0.03, ey - r * 0.05, r * 0.022, r * 0.022, "#FFF"))
        o.append(stroke(f"M{f(ex - s * r * 0.2)} {f(ey + r * 0.06)} Q{f(float(ex))} {f(ey - r * 0.16)} {f(ex + s * r * 0.24)} {f(ey - r * 0.06)}", "#0A0604", r * 0.045))
    # muzzle
    o.append(K.ball(cx - r * 0.2, cy + r * 0.42, r * 0.34, FUR_W, outline=False))
    o.append(K.ball(cx + r * 0.2, cy + r * 0.42, r * 0.34, FUR_W, outline=False))
    o.append(K.ball(cx, cy + r * 0.7, r * 0.22, FUR_W, outline=False))
    o.append(K.form((cx, cy - r * 0.32), (cx, cy + r * 0.1), r * 0.1, r * 0.17, TIGER, ao_start=False, outline=False))
    o.append(fill(f"M{f(cx - r * 0.16)} {f(cy + r * 0.13)} L{f(cx + r * 0.16)} {f(cy + r * 0.13)} L{f(float(cx))} {f(cy + r * 0.32)} Z", "#2A1410"))
    o.append(stroke(f"M{f(float(cx))} {f(cy + r * 0.32)} L{f(float(cx))} {f(cy + r * 0.48)} M{f(cx - r * 0.3)} {f(cy + r * 0.54)} Q{f(cx - r * 0.1)} {f(cy + r * 0.62)} {f(float(cx))} {f(cy + r * 0.48)} Q{f(cx + r * 0.1)} {f(cy + r * 0.62)} {f(cx + r * 0.3)} {f(cy + r * 0.54)}", "#1A0C04", r * 0.04))
    for s in (-1, 1):
        for k in range(3):
            o.append(stroke(f"M{f(cx + s * r * 0.3)} {f(cy + r * (0.36 + k * 0.08))} q{f(s * r * 0.4)} {f(-r * 0.04 + k * r * 0.05)} {f(s * r * 0.82)} {f(-r * 0.04 + k * r * 0.1)}", "#FFFDF0", r * 0.018, 0.75))
    return "".join(o)


def _tiger_stripes(K, spans):
    out = []
    for (x, y0, ln, sk) in spans:
        out.append(fill(f"M{f(float(x))} {f(float(y0))} Q{f(x + sk)} {f(y0 + ln * 0.5)} {f(x + sk * 0.3)} {f(y0 + ln)} Q{f(x + sk * 0.45 + 5)} {f(y0 + ln * 0.5)} {f(x + 6)} {f(float(y0))} Z", "#160A04", 0.92))
    return "".join(out)


def sangun_body(K):
    """The tiger alone: walking left, shoulders high. Elder is drawn separately so he can ride on top."""
    o = []
    # tail curling up behind
    o.append(K.tube([(70, -66), (100, -58), (116, -86), (98, -116)], [6.2, 5.4, 5, 4.2], TIGER, n_per=5, chunks_per_seg=2))
    for x, y in ((90, -58), (106, -66), (114, -84), (108, -100)):
        o.append(ell(x, y, 5, 2.4, "#160A04", 0.9))
    o.append(ell(97, -117, 5, 5, "#160A04"))
    # far legs
    o.append(K.tube([(46, -56), (58, -32), (50, -5)], [15, 8, 5.4], TIGER.back(0.28), bumps=[(0.2, 3, 0)], crease_at=(1,)))
    o.append(K.tube([(-50, -56), (-58, -30), (-62, -5)], [12, 7, 5.2], TIGER.back(0.28), crease_at=(1,)))
    o.append(K.ball(-65, -3, 7, TIGER.back(0.28), ry=4.4))
    o.append(K.ball(47, -3, 7, TIGER.back(0.28), ry=4.4))
    # body
    o.append(K.tube([(70, -66), (34, -76), (-16, -76), (-54, -78)], [20, 27, 27, 25], TIGER, n_per=7, chunks_per_seg=3))
    # white belly
    o.append(fill("M64 -50 Q20 -38 -44 -50 Q-20 -56 20 -56 Q48 -54 64 -50 Z", "#FBF4DC", 0.92, soft(K)))
    # side stripes
    o.append(_tiger_stripes(K, [(58, -84, 28, -5), (42, -102, 40, -7), (26, -103, 42, -6), (10, -103, 40, -8), (-6, -103, 42, -5), (-22, -103, 40, -7), (-38, -102, 38, -6), (-54, -100, 32, -3)]))
    # near hind leg, near foreleg, big paws
    o.append(K.tube([(52, -66), (66, -38), (58, -6)], [19, 9, 6.2], TIGER, bumps=[(0.2, 4, 0)], crease_at=(1,)))
    o.append(_tiger_stripes(K, [(50, -72, 16, -3), (58, -50, 14, -2), (56, -28, 10, -1)]))
    o.append(K.tube([(-40, -62), (-34, -32), (-40, -6)], [15, 8.5, 6.6], TIGER, bumps=[(0.2, 3, 0)], crease_at=(1,)))
    o.append(_tiger_stripes(K, [(-44, -58, 14, -2), (-40, -38, 12, -1), (-42, -22, 8, -1)]))
    for px, py in ((55, -1), (-44, -1)):
        o.append(K.ball(px, py, 8.8, FUR_W, ry=5.2))
        for k in range(3):
            o.append(stroke(f"M{f(px - 6 + k * 4)} {f(py - 3)} l-0.4 6", "#2A1A0C", 0.9))
    # head, turned to the viewer
    o.append(K.tube([(-54, -82), (-70, -92)], [22, 20], TIGER, n_per=3, chunks_per_seg=1))
    o.append(_tiger_head(K, -80, -96, 28))
    return "".join(o)


def sangun_elder(K):
    """The mountain elder seated side-on on the tiger's back; local origin at the seat."""
    r = Mat("#D2CCBA", "#FFFCF0", "#4C4A3C", "#8092A8", spec_op=0.18, gloss=0.12)
    o = []
    # lap and hanging robe
    s, _ = skirt(K, (-15, -22), (14, -22), (30, 34), (-22, 36), r, folds=7, seed=31, hem=OBANG["청"], hem_h=3, wave=2)
    o.append(s)
    o.append(wide_sleeve(K, (13, -60), (26, -40), (18, -22), r, droop=12, w=6.2, hand=None, back=True))
    t, _ = K.torso((0, -66), (0, -18), 15, 13.5, r)
    o.append(t)
    o.append(stroke("M-5 -66 L5 -42 M5 -66 L-4 -45", "#8A8270", 1))
    o.append(K.form((-13, -24), (13, -24), 2.3, 2.3, RED, ao_start=False, spec=False))
    o.append(K.neck((0, -66), (0, -72), 3.6, OLD))
    look = dict(shape="long", eye="droopy", brow="thick", brow_col="#E8E4D8", nose="straight", nose_len=1.1, mouth="calm", age=0.95, beard="long", beard_col="#EDE9DC", turn=-0.35, cheek=0.75, gaze=(-0.5, 0))
    h, _ = K.face(0, -82, 10, OLD, look)
    o.append(h)
    # white hair + 복건 (dark soft cap with tails)
    o.append(fill("M-10 -84 Q-11 -96 0 -97 Q11 -96 10.2 -84 Q6 -91 0 -92 Q-6 -91 -10 -84 Z", "#E8E4D8"))
    hd, _ = cloth(K, [(-10.4, -89), (-8.4, -100), (0, -106), (9, -100), (10.6, -88), (0, -92.4)], NAVY)
    o.append(hd)
    o.append(stroke("M10 -93 q6 6 7 18 M9 -94 q9 3 12 13", "#27386E", 1.8))
    # near arm holds a long staff upright
    o.append(K.form((-40, 40), (-34, -118), 1.9, 1.5, WOOD_D, ao_start=False))
    o.append(K.tube([(-34, -118), (-30, -125), (-34, -131)], [1.7, 1.6, 1.4], WOOD_D, n_per=3, chunks_per_seg=1))
    gourd = Mat("#BC8A3A", "#F2CC7A", "#3A2408", "#7E7050", spec_op=0.5, gloss=0.6)
    o.append(K.ball(-31, -100, 3.4, gourd) + K.ball(-31, -93, 4.6, gourd))
    o.append(wide_sleeve(K, (-13, -60), (-26, -48), (-37, -60), r, droop=12, w=6.4, hand=None))
    o.append(K.hand((-35.5, -62), (-2, 0.5), 4.3, OLD, kind="grip"))
    return "".join(o)


def sangun(K):
    return K.cast_shadow(0, 2, 92, 15, 0.62) + sangun_body(K) + f'<g transform="translate(14 -94)">{sangun_elder(K)}</g>'
