"""yomul_figs — the 요물 무리 creatures: 창귀, 불가사리, 어둑시니, 이무기 (see design2/SPEC_YOMUL.md).

Every function takes a figure_kit.Kit and returns SVG markup in local coordinates with the creature's
ground point at (0, 0) and y going up as negative, so callers place them with <g transform>. Gradients and
filters are registered on the Kit, so the caller only has to emit K.defs_svg() after all drawing calls.
Output is deterministic. Imports nothing from the artboard generators, so any of them can use it.
"""
import math
import random

from figure_kit import Mat, MATS, P, f, mix, shade

BLUE, BLUE_L = "#4FB3E8", "#A8DDF5"
AMBER, AMBER_L = "#D98A2B", "#F6C46A"
VIOLET = "#9A84D8"
OUTL = "#120D08"

ASH = Mat("#7E7C72", "#CFCBBC", "#1C1C1A", "#5E86A4", spec_op=0.18, gloss=0.15)
ASH_SKIN = Mat("#9C9A8C", "#E0DCCB", "#23211C", "#5E94B8", spec_op=0.3, gloss=0.3)
TIGER_G = Mat("#A2682A", "#DFA65E", "#1C0C04", "#5E6A70", spec_op=0.15, gloss=0.15)
IRON_HIDE = Mat("#4A4038", "#84766A", "#0B0706", "#3E4648", spec="#D8D4CC", spec_op=0.16, gloss=0.2)
RUST = Mat("#8A4E2A", "#D08A52", "#1E0C04", "#5A5A60", spec_op=0.35, gloss=0.45)
SCALE = Mat("#1F6E6A", "#62C4B2", "#04201F", "#2E7EA8", spec="#E8FFF8", spec_op=0.55, gloss=0.7)
BELLY = Mat("#C8C49A", "#F2EFD0", "#3E4A3A", "#6A9A9A", spec_op=0.35, gloss=0.5)


def _el(tag, **a):
    s = " ".join(f'{k.rstrip("_").replace("_", "-")}="{f(v) if isinstance(v, (int, float)) else v}"' for k, v in a.items() if v is not None)
    return f"<{tag} {s}></{tag}>"


def path(d, **a):
    return _el("path", d=d, **a)


def ell(cx, cy, rx, ry, **a):
    return _el("ellipse", cx=float(cx), cy=float(cy), rx=float(rx), ry=float(ry), **a)


def circ(cx, cy, r, **a):
    return _el("circle", cx=float(cx), cy=float(cy), r=float(r), **a)


def group(inner, **a):
    s = " ".join(f'{k.rstrip("_").replace("_", "-")}="{v}"' for k, v in a.items() if v is not None)
    return f"<g {s}>{inner}</g>"


def _defs(K):
    """Register the shared creature filters once per Kit; returns their ids."""
    if not getattr(K, "_yomul", None):
        p = K.p
        ids = {n: f"{p}-y{n}" for n in ("ink", "ink2", "glow", "blur3", "blur6", "blur12", "orb", "aura", "water", "pearl")}
        K._defs.append(
            f'<filter id="{ids["ink"]}" x="-30%" y="-30%" width="160%" height="160%"><feTurbulence type="fractalNoise" baseFrequency="0.035 0.05" numOctaves="4" seed="11" result="n"></feTurbulence>'
            f'<feDisplacementMap in="SourceGraphic" in2="n" scale="20" xChannelSelector="R" yChannelSelector="G"></feDisplacementMap></filter>'
            f'<filter id="{ids["ink2"]}" x="-30%" y="-30%" width="160%" height="160%"><feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="3" seed="5" result="n"></feTurbulence>'
            f'<feDisplacementMap in="SourceGraphic" in2="n" scale="9" xChannelSelector="R" yChannelSelector="G" result="d"></feDisplacementMap><feGaussianBlur in="d" stdDeviation="0.5"></feGaussianBlur></filter>'
            f'<filter id="{ids["glow"]}" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="1.6" result="b"></feGaussianBlur>'
            f'<feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>'
            f'<filter id="{ids["blur3"]}" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="3"></feGaussianBlur></filter>'
            f'<filter id="{ids["blur6"]}" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="6"></feGaussianBlur></filter>'
            f'<filter id="{ids["blur12"]}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="12"></feGaussianBlur></filter>'
            f'<radialGradient id="{ids["orb"]}"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="0.3" stop-color="{BLUE_L}"></stop>'
            f'<stop offset="0.65" stop-color="{BLUE}" stop-opacity="0.75"></stop><stop offset="1" stop-color="{BLUE}" stop-opacity="0"></stop></radialGradient>'
            f'<radialGradient id="{ids["aura"]}"><stop offset="0" stop-color="{AMBER_L}" stop-opacity="0.55"></stop><stop offset="0.55" stop-color="{AMBER}" stop-opacity="0.22"></stop>'
            f'<stop offset="1" stop-color="{AMBER}" stop-opacity="0"></stop></radialGradient>'
            f'<linearGradient id="{ids["water"]}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0E3A40"></stop><stop offset="0.55" stop-color="#082428"></stop><stop offset="1" stop-color="#030E12"></stop></linearGradient>'
            f'<radialGradient id="{ids["pearl"]}" cx="0.36" cy="0.32" r="0.72"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="0.45" stop-color="#E4F4FF"></stop>'
            f'<stop offset="0.8" stop-color="#9CC8E8"></stop><stop offset="1" stop-color="#4A7EB0"></stop></radialGradient>'
        )
        K._yomul = ids
    return K._yomul


def _lin(K, stops, x1, y1, x2, y2):
    gid = K.id("yl")
    st = "".join(f'<stop offset="{f(float(o))}" stop-color="{c}"' + (f' stop-opacity="{f(float(a[0]))}"' if a else "") + "></stop>" for o, c, *a in stops)
    K._defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(float(x1))}" y1="{f(float(y1))}" x2="{f(float(x2))}" y2="{f(float(y2))}">{st}</linearGradient>')
    return f"url(#{gid})"


# ------------------------------------------------------------------ 창귀
def soul_bolt(K, x, y, s=1.0, trail=None):
    """Goblin-blue soul with an amber heart. trail=(dx, dy) adds a comet tail pointing that way."""
    d = _defs(K)
    o = [circ(x, y, 15 * s, fill=f"url(#{d['orb']})")]
    if trail:
        tx, ty = trail
        L = math.hypot(tx, ty) or 1
        nx, ny = -ty / L, tx / L
        w = 4.5 * s
        o.insert(0, path(f"M{f(x + nx * w)} {f(y + ny * w)} Q{f(x + tx * 0.5 + nx * w * 0.8)} {f(y + ty * 0.5 + ny * w * 0.8)} {f(x + tx)} {f(y + ty)} "
                         f"Q{f(x + tx * 0.5 - nx * w * 0.8)} {f(y + ty * 0.5 - ny * w * 0.8)} {f(x - nx * w)} {f(y - ny * w)} Z",
                         fill=BLUE_L, opacity="0.65", filter=f"url(#{d['glow']})"))
    o.append(circ(x, y, 4.2 * s, fill="#FFFFFF", filter=f"url(#{d['glow']})"))
    o.append(circ(x + 0.6 * s, y + 0.8 * s, 1.9 * s, fill=AMBER_L))
    return "".join(o)


def ghost_tiger(K, cx, cy, r):
    """The tiger that owns the thrall: a spectral head, snarling, dissolving toward the jaw."""
    d = _defs(K)
    mid = K.id("ym")
    fade = _lin(K, [(0, "#FFFFFF"), (0.55, "#FFFFFF"), (0.86, "#808080"), (1, "#000000")], 0, cy - r * 1.2, 0, cy + r * 1.3)
    K._defs.append(f'<mask id="{mid}" maskUnits="userSpaceOnUse" x="{f(cx - r * 2)}" y="{f(cy - r * 2)}" width="{f(r * 4)}" height="{f(r * 4)}">'
                   f'<rect x="{f(cx - r * 2)}" y="{f(cy - r * 2)}" width="{f(r * 4)}" height="{f(r * 4)}" fill="{fade}"></rect></mask>')
    o = [circ(cx, cy, r * 1.6, fill=f"url(#{d['aura']})", opacity="0.8")]
    t = []
    pts = [(cx - r * 0.3, cy - r * 0.86), (cx + r * 0.34, cy - r * 0.86), (cx + r * 0.86, cy - r * 0.42), (cx + r * 1.2, cy + r * 0.2),
           (cx + r * 0.98, cy + r * 0.8), (cx + r * 0.3, cy + r * 1.12), (cx - r * 0.5, cy + r * 1.12), (cx - r * 1.2, cy + r * 0.82),
           (cx - r * 1.32, cy + r * 0.18), (cx - r * 0.88, cy - r * 0.44)]
    hd = K._smooth(pts)
    for ex, ey, er in ((cx - r * 0.96, cy - r * 0.66, r * 0.22), (cx + r * 0.84, cy - r * 0.7, r * 0.22)):
        t.append(K.ball(ex, ey, er, TIGER_G))
        t.append(circ(ex + er * 0.05, ey + er * 0.1, er * 0.58, fill="#120804", opacity="0.85"))
    # cheek ruffs: torn fur past the outline
    ruff = []
    for sx, x0 in ((-1, cx - r * 1.2), (1, cx + r * 1.08)):
        for k in range(5):
            y = cy + r * (0.02 + k * 0.2)
            ruff.append(f"M{f(x0)} {f(y - r * 0.14)} L{f(x0 + sx * r * (0.34 - abs(k - 2) * 0.05))} {f(y + r * 0.08)} L{f(x0 - sx * r * 0.08)} {f(y + r * 0.12)} Z ")
    t.append(path("".join(ruff), fill=TIGER_G.base, stroke=OUTL, stroke_width="0.5"))
    grad = K._sphere_grad(cx - r * 0.12, cy - r * 0.02, r * 1.24, TIGER_G)
    t.append(path(hd, fill=f"url(#{grad})", stroke=OUTL, stroke_width="1", stroke_opacity="0.7"))
    inner = []
    inner.append(ell(cx - r * 0.1, cy + r * 0.56, r * 0.62, r * 0.44, fill="#E6DCC4", opacity="0.82", filter=f"url(#{K.p}-soft)"))
    inner.append(ell(cx + r * 0.86, cy + r * 0.2, r * 0.46, r * 0.95, fill="#0E0603", opacity="0.75", filter=f"url(#{K.p}-ao)"))
    inner.append(ell(cx, cy - r * 0.28, r * 0.9, r * 0.2, fill="#120804", opacity="0.5", filter=f"url(#{K.p}-soft)"))
    st = []
    for x0, y0, x1, y1, w in ((-0.1, -0.9, -0.1, -0.42, 0.1), (0.16, -0.9, 0.06, -0.44, 0.09), (-0.4, -0.82, -0.3, -0.42, 0.09), (0.44, -0.8, 0.28, -0.42, 0.08),
                              (-1.2, -0.12, -0.72, -0.1, 0.1), (-1.28, 0.2, -0.74, 0.12, 0.1), (-1.14, 0.54, -0.72, 0.38, 0.09),
                              (1.08, -0.22, 0.66, -0.12, 0.1), (1.16, 0.1, 0.7, 0.1, 0.1), (1.04, 0.5, 0.68, 0.36, 0.09)):
        ax, ay, bx, by = cx + x0 * r, cy + y0 * r, cx + x1 * r, cy + y1 * r
        mx, my = (ax + bx) / 2, (ay + by) / 2
        nx, ny = -(by - ay), bx - ax
        L = math.hypot(nx, ny) or 1
        ww = w * r
        st.append(f"M{f(ax - nx / L * ww)} {f(ay - ny / L * ww)} Q{f(mx - nx / L * ww * 1.2)} {f(my - ny / L * ww * 1.2)} {f(bx)} {f(by)} "
                  f"Q{f(mx + nx / L * ww * 1.2)} {f(my + ny / L * ww * 1.2)} {f(ax + nx / L * ww)} {f(ay + ny / L * ww)} Z ")
    inner.append(path("".join(st), fill="#0C0603", opacity="0.95"))
    t.append(K.clip_overlay(hd, "".join(inner)))
    # eyes: slanted hard toward the nose, burning, with a heavy brow
    for s_, ex in ((-1, cx - r * 0.5), (1, cx + r * 0.3)):
        ey = cy - r * 0.1
        tilt = s_ * r * 0.15
        t.append(path(f"M{f(ex - r * 0.3)} {f(ey + tilt)} Q{f(ex)} {f(ey - r * 0.2)} {f(ex + r * 0.3)} {f(ey - tilt)} Q{f(ex)} {f(ey + r * 0.16)} {f(ex - r * 0.3)} {f(ey + tilt)} Z", fill="#0A0402", stroke="#0A0402", stroke_width="1.2"))
        t.append(ell(ex, ey - r * 0.01, r * 0.14, r * 0.06, fill="#FFD27A", filter=f"url(#{d['glow']})"))
        t.append(ell(ex, ey - r * 0.01, r * 0.024, r * 0.058, fill="#0A0402"))
    # wrinkled snarl: nose bridge folds, bared open mouth, long fangs
    nx0 = cx - r * 0.1
    ny0 = cy + r * 0.28
    t.append(path(f"M{f(nx0 - r * 0.2)} {f(ny0 - r * 0.2)} Q{f(nx0)} {f(ny0 - r * 0.28)} {f(nx0 + r * 0.2)} {f(ny0 - r * 0.2)} M{f(nx0 - r * 0.17)} {f(ny0 - r * 0.32)} Q{f(nx0)} {f(ny0 - r * 0.4)} {f(nx0 + r * 0.17)} {f(ny0 - r * 0.32)}",
                  fill="none", stroke="#120804", stroke_width="1.1", stroke_opacity="0.8"))
    t.append(path(f"M{f(nx0 - r * 0.18)} {f(ny0 - r * 0.08)} Q{f(nx0)} {f(ny0 - r * 0.15)} {f(nx0 + r * 0.18)} {f(ny0 - r * 0.08)} L{f(nx0)} {f(ny0 + r * 0.1)} Z", fill="#2A100A", stroke="#0A0402", stroke_width="0.8"))
    mt = f"M{f(nx0 - r * 0.56)} {f(ny0 + r * 0.14)} Q{f(nx0)} {f(ny0 + r * 0.26)} {f(nx0 + r * 0.56)} {f(ny0 + r * 0.14)} Q{f(nx0 + r * 0.34)} {f(ny0 + r * 0.76)} {f(nx0)} {f(ny0 + r * 0.76)} Q{f(nx0 - r * 0.34)} {f(ny0 + r * 0.76)} {f(nx0 - r * 0.56)} {f(ny0 + r * 0.14)} Z"
    t.append(path(mt, fill="#180604", stroke="#0A0402", stroke_width="1"))
    for s_, top, ln, wd in ((-1, 0.2, 0.42, 0.08), (1, 0.2, 0.42, 0.08), (-1, 0.72, -0.28, 0.06), (1, 0.72, -0.28, 0.06)):
        x0_ = nx0 + s_ * r * (0.34 if ln > 0 else 0.2)
        y0_ = ny0 + r * top
        t.append(path(f"M{f(x0_ - r * wd)} {f(y0_)} L{f(x0_)} {f(y0_ + r * ln)} L{f(x0_ + r * wd)} {f(y0_)} Z", fill="#F2ECD8", stroke="#0A0402", stroke_width="0.6"))
    o.append(group("".join(t), mask=f"url(#{mid})"))
    return "".join(o)


def changgwi(K, pose="charge", tiger=True, ground=True):
    """창귀 ~150 tall. pose: charge (soul gathers in the reaching hand) | fire (arm thrust, bolt gone) | idle."""
    d = _defs(K)
    o = []
    if ground:
        o.append(K.cast_shadow(4, 0, 30, 8, 0.6))
        # the striped shadow it casts: the tiger's, not its own
        sh = []
        for k in range(5):
            x0 = 14 + k * 13
            sh.append(f"M{x0} {f(-5 + k * 1.2)} q9 5 4 {f(14 - k)} q-8 -5 -11 {f(-13 + k)} Z ")
        o.append(path("".join(sh), fill="#000", opacity="0.62", filter=f"url(#{K.p}-soft)"))
    if tiger:
        o.append(group(ghost_tiger(K, 38, -132, 35), opacity="0.92"))
    # far leg + near leg: bare, wasted, mist-wrapped
    o.append(K.leg((6, -66), (10, -36), (8, -9), ASH_SKIN, w=5.2, boot=ASH_SKIN, foot_dir=-1, back=True))
    o.append(K.leg((-6, -66), (-9, -36), (-12, -9), ASH_SKIN, w=5.4, boot=ASH_SKIN, foot_dir=-1))
    # far arm: limp at the side, long fingers
    o.append(K.arm((13, -111), (21, -86), (17, -60), ASH, m_fore=ASH_SKIN, w=5.2, hand="open", hand_m=ASH_SKIN, back=True))
    # tattered 베옷: narrow chest, hem torn into rags
    hem = [(-19, -38), (-14, -28), (-10, -40), (-5, -24), (0, -38), (6, -26), (10, -40), (16, -30), (20, -42)]
    pts = [(-5, -118), (-16, -113), (-17, -96), (-15, -74), (-19, -52)] + hem + [(18, -60), (15, -82), (17, -100), (16, -113), (5, -118)]
    rd = "M" + P(pts[0]) + "".join(" L" + P(p) for p in pts[1:]) + " Z"
    o.append(path(rd, fill=_lin(K, [(0, ASH.light), (0.3, ASH.base), (0.72, ASH.core), (1, ASH.bounce)], -19, -80, 20, -72),
                  stroke=OUTL, stroke_width="0.9", stroke_opacity="0.8", stroke_linejoin="round"))
    inner = []
    # vertical cloth folds, rags, falloff
    for x, y0, y1, w in ((-11, -104, -44, 1.2), (-4, -96, -36, 1.0), (5, -100, -34, 1.2), (11, -96, -44, 1.1)):
        inner.append(path(f"M{x} {y0} Q{x + 2} {(y0 + y1) / 2} {x - 1} {y1}", fill="none", stroke="#1C1C1A", stroke_width=str(w), stroke_opacity="0.55"))
        inner.append(path(f"M{x - 1.6} {y0} Q{x + 0.4} {(y0 + y1) / 2} {x - 2.6} {y1}", fill="none", stroke=ASH.light, stroke_width="0.7", stroke_opacity="0.55"))
    # coarse hemp weave
    wv = "".join(f"M-22 {y} H24 " for y in range(-116, -24, 3))
    inner.append(path(wv, fill="none", stroke="#1C1C1A", stroke_width="0.35", stroke_opacity="0.3"))
    # claw wounds across the chest
    for k in range(3):
        x0, y0 = -13 + k * 7, -106 + k * 1.5
        inner.append(path(f"M{x0} {y0} Q{x0 + 6} {y0 + 11} {x0 + 12} {y0 + 24}", fill="none", stroke="#1A0604", stroke_width="3.4", stroke_linecap="round"))
        inner.append(path(f"M{x0 + 0.4} {y0 + 1} Q{x0 + 6} {y0 + 11} {x0 + 11.4} {y0 + 22}", fill="none", stroke="#A02A1C", stroke_width="1.5", stroke_linecap="round"))
    inner.append(ell(0, -116, 18, 6, fill="#0A0604", opacity="0.5", filter=f"url(#{K.p}-soft)"))
    inner.append(ell(14, -70, 9, 40, fill="#0A0604", opacity="0.3", filter=f"url(#{K.p}-ao)"))
    o.append(K.clip_overlay(rd, "".join(inner)))
    o.append(path("M-18 -70 Q0 -65 18 -71", fill="none", stroke="#3E3A2E", stroke_width="2.4"))
    # neck pitched forward, head hanging from it
    o.append(K.neck((-1, -116), (-3, -125), 3.4, ASH_SKIN))
    hx, hy = -4, -134
    fs, fd = K.face(hx, hy, 11.5, ASH_SKIN, dict(shape="diamond", cheek=1.0, eye="droopy", brow="worried", brow_col="#35332C", nose="straight", nose_len=1.05,
                                                  mouth="neutral", mouth_col="#3A3028", age=0.72, turn=-0.25, lip=0.1, ear=True))
    o.append(fs)
    # hollow sockets burn through the drawn eyes
    for ex in (hx - 7.0, hx + 1.9):
        o.append(ell(ex, hy - 0.5, 3.1, 2.3, fill="#060406"))
        o.append(circ(ex, hy - 0.2, 0.95, fill=AMBER_L, filter=f"url(#{d['glow']})"))
    # lank wet hair over the skull and hanging past the jaw
    hair = [path(f"M{f(hx - 11)} {f(hy - 2)} Q{f(hx - 9)} {f(hy - 14)} {f(hx + 1)} {f(hy - 13)} Q{f(hx + 11)} {f(hy - 12)} {f(hx + 12)} {f(hy + 1)} Q{f(hx + 6)} {f(hy - 8)} {f(hx)} {f(hy - 8.5)} Q{f(hx - 7)} {f(hy - 8)} {f(hx - 11)} {f(hy - 2)} Z",
                 fill="#17130F", stroke=OUTL, stroke_width="0.6")]
    for k, (x0, ln, bend) in enumerate(((-11, 22, -2), (-9.5, 15, 1), (-6, 10, -1), (6.5, 12, 1), (10, 19, 2), (12, 26, 1), (8, 22, -1))):
        hair.append(path(f"M{f(hx + x0)} {f(hy - 5)} q{f(bend)} {f(ln * 0.5)} {f(bend * 0.3)} {f(ln)}", fill="none", stroke="#17130F" if k % 3 else "#3A3430", stroke_width="1.15", stroke_linecap="round"))
    o.append("".join(hair))
    # the long reaching arm, past natural length
    if pose == "fire":
        sh_, el_, wr = (-13, -112), (-38, -117), (-70, -118)
    else:
        sh_, el_, wr = (-13, -112), (-34, -119), (-62, -110)
    o.append(K.arm(sh_, el_, wr, ASH, m_fore=ASH_SKIN, w=5.6, hand="open", hand_dir=(wr[0] - el_[0], wr[1] - el_[1] - 6), hand_m=ASH_SKIN))
    if tiger:
        # the tiger's paw on the thrall's shoulder: who owns whom
        o.append(group(K.ball(18, -117, 8.5, TIGER_G) + "".join(
            path(f"M{f(13 + k * 3.6)} {f(-111.5 + abs(k - 1) * 0.5)} q{f(-0.4)} 3 {f(-1.8 + k * 0.3)} 6 q2.5 -2.4 3 -6 Z", fill="#F6EFDA", stroke=OUTL, stroke_width="0.5") for k in range(3)), opacity="0.92"))
    if pose == "charge":
        o.append(soul_bolt(K, -77, -116, 1.1))
    elif pose == "fire":
        o.append(circ(-84, -120, 9, fill=f"url(#{d['orb']})"))
    # blue thread + feet dissolving in mist
    o.append(ell(-2, -4, 26, 7, fill=BLUE_L, opacity="0.28", filter=f"url(#{d['blur3']})"))
    o.append(path("M-17 -46 Q-23 -84 -14 -113", fill="none", stroke=BLUE_L, stroke_width="1", stroke_opacity="0.5"))
    return "".join(o)


def risen_soul(K, m=None):
    """A fallen enemy's soul pulled up as a brief ally (~86 tall): small, translucent, kneeling up."""
    m = m or Mat("#C89048", "#FFE0A0", "#4A2A10", "#7ABCE0", spec_op=0.3)
    o = [K.leg((5, -30), (15, -18), (7, -6), m, w=4.2, boot=m, foot_dir=-1, back=True),
         K.leg((-5, -30), (-3, -15), (-12, -6), m, w=4.4, boot=m, foot_dir=-1)]
    t, _ = K.torso((0, -62), (0, -30), 11, 8, m)
    o.append(t)
    fs, _ = K.face(0, -71, 7, m, dict(shape="oval", eye="narrow", mouth="neutral", age=0.1, turn=-0.3))
    o.append(fs)
    o.append(K.arm((10, -59), (19, -46), (22, -32), m, w=3.5, hand="fist", hand_m=m, back=True))
    o.append(K.arm((-10, -59), (-23, -60), (-32, -72), m, w=3.5, hand="open", hand_m=m))
    return "".join(o)


# ------------------------------------------------------------------ 불가사리
def _needles(rr, pts, ln, w, spread=0.5, lean=(0.45, -0.1)):
    """Needle path data: pts = [(x, y, nx, ny)], each a sliver along its normal, swept toward the rump."""
    d = []
    for (x, y, nx, ny) in pts:
        a = rr.uniform(-spread, spread)
        vx = nx * math.cos(a) - ny * math.sin(a) + lean[0]
        vy = nx * math.sin(a) + ny * math.cos(a) + lean[1]
        L = math.hypot(vx, vy) or 1
        vx, vy = vx / L, vy / L
        l = ln * rr.uniform(0.7, 1.25)
        px, py = -vy * w, vx * w
        d.append(f"M{f(x + px)} {f(y + py)} L{f(x + vx * l)} {f(y + vy * l)} L{f(x - px)} {f(y - py)} Z ")
    return "".join(d)


def _sample_closed(pts, n_per=8):
    q = pts + pts[:3]
    out = []
    for i in range(len(pts)):
        p0, p1, p2, p3 = q[i - 1] if i else pts[-1], q[i], q[i + 1], q[i + 2]
        for k in range(n_per):
            t = k / n_per
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p1[c] + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2 + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3) for c in (0, 1)))
    return out


def _wheel(K, cx, cy, r, tilt=0.45):
    """A bitten cart wheel lying in the scrap."""
    o = [ell(cx, cy, r, r * tilt, fill="none", stroke="#14100C", stroke_width=f(r * 0.36)),
         ell(cx, cy - 0.5, r, r * tilt, fill="none", stroke=MATS["iron"].base, stroke_width=f(r * 0.22)),
         ell(cx - r * 0.2, cy - r * tilt * 0.75, r * 0.5, r * tilt * 0.2, fill="none", stroke="#E4ECF4", stroke_width="0.7", opacity="0.7")]
    for k in range(5):
        a = k * math.pi * 2 / 5 + 0.3
        o.append(path(f"M{f(cx)} {f(cy)} L{f(cx + math.cos(a) * r * 0.9)} {f(cy + math.sin(a) * r * 0.9 * tilt)}", stroke=MATS["wood"].base, stroke_width=f(r * 0.13)))
    o.append(K.ball(cx, cy, r * 0.2, MATS["brass"]))
    return "".join(o)


def bulgasari(K, seed=9, chew=True, ground=True):
    """불가사리: squat iron-eating beast, facing screen left, about 205 wide, 128 tall."""
    d = _defs(K)
    rr = random.Random(seed)
    o = []
    if ground:
        o.append(K.cast_shadow(6, -2, 92, 14, 0.7))
    H = IRON_HIDE
    # far legs
    o.append(K.tube([(52, -62), (66, -34), (60, -8)], [17, 11, 9], H.back(), [(0.3, 3, 0)], crease_at=(1,)))
    o.append(K.tube([(-34, -56), (-46, -30), (-42, -8)], [15, 10, 8.5], H.back(), [(0.3, 3, 0)], crease_at=(1,)))
    # silhouette needles behind the body outline
    body = [(-66, -50), (-52, -88), (-14, -112), (32, -108), (72, -90), (92, -58), (82, -32), (34, -24), (-28, -28), (-60, -34)]
    edge = _sample_closed(body, 6)
    cx0, cy0 = 12.0, -66.0
    top = []
    for (x, y) in edge:
        L = math.hypot(x - cx0, y - cy0) or 1
        nx, ny = (x - cx0) / L, (y - cy0) / L
        if ny < 0.25:
            top.append((x, y, nx, ny))
    o.append(path(_needles(rr, top, 22, 2.5, 0.35), fill=_lin(K, [(0, "#E2DCCF"), (0.5, "#807468"), (1, "#14100C")], -40, -130, 60, -40), stroke=OUTL, stroke_width="0.45"))
    bd = K._smooth(body)
    grad = K._sphere_grad(-2, -82, 84, H)
    o.append(path(bd, fill=f"url(#{grad})", stroke=OUTL, stroke_width="1.1", stroke_opacity="0.85"))
    inner = []
    # overlapping rust plates on the back, row by row, each with its lit lip and shadow
    plates = []
    rows = ((-98, 7, 26), (-84, 8, 24), (-70, 8, 23), (-56, 7, 22))
    for ri, (yy, cnt, pw) in enumerate(rows):
        for k in range(cnt):
            x = -44 + k * 21 + (ri % 2) * 10 + rr.uniform(-2, 2)
            y = yy + rr.uniform(-2, 2) + (abs(x - 8) ** 2) * 0.0035
            m = RUST if rr.random() < 0.72 else H
            lit = max(0.0, 1 - (x + 40) / 150)
            pd = f"M{f(x - pw / 2)} {f(y)} Q{f(x)} {f(y - 11)} {f(x + pw / 2)} {f(y)} Q{f(x + 1)} {f(y + 13)} {f(x - pw / 2)} {f(y)} Z"
            plates.append(path(pd, fill=mix(m.core, m.base, 0.3 + 0.7 * lit), stroke="#0C0705", stroke_width="1"))
            plates.append(path(f"M{f(x - pw / 2 + 2)} {f(y - 1)} Q{f(x)} {f(y - 10)} {f(x + pw / 2 - 2)} {f(y - 1)}", fill="none", stroke=m.light, stroke_width="1.2", stroke_opacity=f(0.3 + 0.55 * lit)))
            if rr.random() < 0.5:
                plates.append(circ(x - 3, y + 2, 0.9, fill=MATS["brass"].light, opacity="0.8"))
            if rr.random() < 0.55:
                plates.append(path(f"M{f(x + rr.uniform(-6, 6))} {f(y + 1)} q1 {f(rr.uniform(4, 8))} -1 {f(rr.uniform(8, 13))}", fill="none", stroke="#C86A2A", stroke_width="1.3", stroke_opacity="0.6"))
    inner.append("".join(plates))
    # needle coat over the plates
    coat = []
    for _ in range(190):
        x = rr.uniform(-66, 92)
        y = rr.uniform(-114, -30)
        coat.append((x, y, -0.2, -1.0))
    inner.append(path(_needles(rr, coat, 10, 1.05, 0.5, (0.8, 0.1)), fill="#C8BEB0", opacity="0.7"))
    coat2 = [(rr.uniform(-66, 92), rr.uniform(-114, -30), -0.2, -1.0) for _ in range(110)]
    inner.append(path(_needles(rr, coat2, 9, 1.0, 0.5, (0.8, 0.1)), fill="#080604", opacity="0.75"))
    # glowing goblin-fire veins where it has swallowed 귀화석
    inner.append(path("M-20 -44 L-6 -56 L-12 -66 L6 -72 M-6 -56 L12 -50 L26 -62 M26 -62 L44 -58 L52 -70 M26 -62 L30 -46", fill="none", stroke=BLUE, stroke_width="2.4", stroke_linejoin="round", opacity="0.95", filter=f"url(#{d['glow']})"))
    inner.append(path("M-20 -44 L-6 -56 L-12 -66 L6 -72 M-6 -56 L12 -50 L26 -62 M26 -62 L44 -58 L52 -70 M26 -62 L30 -46", fill="none", stroke="#E8F8FF", stroke_width="0.8"))
    # form: right side and belly fall into deep shadow, top-left catches the key light
    inner.append(ell(70, -48, 40, 50, fill="#050302", opacity="0.72", filter=f"url(#{d['blur12']})"))
    inner.append(ell(14, -22, 88, 16, fill="#050302", opacity="0.85", filter=f"url(#{d['blur6']})"))
    inner.append(ell(-22, -100, 42, 12, fill="#FFF2DC", opacity="0.3", filter=f"url(#{d['blur6']})"))
    o.append(K.clip_overlay(bd, "".join(inner)))
    # near legs: short columns ending in iron claws
    claw_m = MATS["iron"]
    for (hip, knee, ank, w0) in (((58, -54), (70, -26), (62, -7), 21), ((-36, -48), (-44, -24), (-38, -7), 19)):
        o.append(K.tube([hip, knee, ank], [w0, w0 * 0.68, w0 * 0.58], H, [(0.22, 4, 0), (0.7, 2, -1)], crease_at=(1,)))
        needles = [(hip[0] + rr.uniform(-w0 * 0.7, w0 * 0.7), hip[1] + rr.uniform(0, 34), -0.3, -1.0) for _ in range(26)]
        o.append(path(_needles(rr, needles, 8, 0.95, 0.4, (0.6, 0.6)), fill="#17110C", opacity="0.8"))
        for k in range(3):
            cxk = ank[0] - w0 * 0.42 + k * w0 * 0.42
            o.append(K.form((cxk, ank[1] + 1), (cxk - 2.6, ank[1] + 8), 3.2, 0.6, claw_m, ao_start=True, outline=True))
    # neck + head low and forward, bearing down on what it is eating
    o.append(K.tube([(-40, -66), (-66, -64), (-82, -58)], [30, 26, 21], H, [(0.4, 3, 1)], outline=False))
    o.append(ell(-36, -64, 16, 30, fill=H.core, opacity="0.55", filter=f"url(#{d['blur6']})"))
    neckn = [(rr.uniform(-86, -18), rr.uniform(-94, -40), -0.3, -1.0) for _ in range(84)]
    o.append(path(_needles(rr, neckn, 11, 1.1, 0.4, (0.9, -0.1)), fill="#201812", opacity="0.85"))
    neckt = [(rr.uniform(-84, -44), rr.uniform(-92, -82), -0.3, -1.0) for _ in range(18)]
    o.append(path(_needles(rr, neckt, 15, 1.8, 0.3, (0.9, -0.2)), fill="#CFC6B6", stroke=OUTL, stroke_width="0.4"))
    # ears
    for ex, ey, er in ((-68, -88, 8.5), (-96, -86, 7)):
        o.append(K.ball(ex, ey, er, H))
        o.append(circ(ex, ey + 0.6, er * 0.55, fill="#2A120C", opacity="0.85"))
    jaw = K.tube([(-76, -42), (-98, -27), (-116, -24)], [12, 10, 7], H.back(0.1), [(0.5, 1, 0)])
    o.append(jaw)
    if chew:
        # a cannon barrel crosswise in its jaws, bent and tooth-scarred
        br, ir = MATS["brass"], MATS["iron"]
        o.append(K.form((-150, -24), (-62, -46), 9.5, 12.5, ir, ao_start=False))
        for t, w0 in ((0.04, 11), (0.3, 11.6), (0.74, 13.2)):
            xb = -150 + 88 * t
            yb = -24 - 22 * t
            o.append(K.form((xb - 2.4, yb + 0.6), (xb + 2.4, yb - 0.6), w0, w0, br, ao_start=False))
        o.append(ell(-151, -23.6, 3.2, 9.5, fill="#060403", stroke=br.light, stroke_width="1.6", transform="rotate(-14 -151 -23.6)"))
        # bent where it has been bitten: dent and bright scratches
        o.append(path("M-110 -41 l5 12 M-102 -43 l5 12 M-94 -45 l5 13", stroke="#FFFFFF", stroke_width="1", stroke_opacity="0.65"))
    # skull and tapir snout, with heavy brow
    o.append(K.tube([(-80, -68), (-98, -62), (-118, -50)], [24, 17, 11.5], H, [(0.2, 2, 1)]))
    o.append(K.ball(-80, -70, 24, H))
    o.append(K.ball(-120, -48, 10.5, Mat("#5E4E44", "#B8A08C", "#150D09", "#4A6A84", spec_op=0.6, gloss=0.8)))
    o.append(ell(-124, -46, 2.2, 1.5, fill="#060302"))
    # bristles and a rust cap carry over the skull, so the head stops reading as a polished ball
    headn = [(rr.uniform(-100, -58), rr.uniform(-92, -56), -0.3, -1.0) for _ in range(46)]
    o.append(path(_needles(rr, headn, 8, 0.9, 0.45, (0.9, 0.1)), fill="#120C08", opacity="0.85"))
    headt = [(-92 + k * 4.2 + rr.uniform(-1, 1), -92 - (k % 3) * 1.2 + abs(k - 4) * 0.8, -0.3, -1.0) for k in range(10)]
    o.append(path(_needles(rr, headt, 13, 1.7, 0.3, (0.8, -0.2)), fill="#CFC6B6", stroke=OUTL, stroke_width="0.4"))
    for (px_, py_, pw_) in ((-90, -84, 15), (-74, -86, 16), (-66, -72, 13)):
        o.append(path(f"M{f(px_ - pw_ / 2)} {f(py_)} Q{f(px_)} {f(py_ - 9)} {f(px_ + pw_ / 2)} {f(py_)} Q{f(px_ + 1)} {f(py_ + 9)} {f(px_ - pw_ / 2)} {f(py_)} Z", fill=RUST.base, stroke="#0C0705", stroke_width="0.9"))
        o.append(path(f"M{f(px_ - pw_ / 2 + 2)} {f(py_ - 1)} Q{f(px_)} {f(py_ - 8)} {f(px_ + pw_ / 2 - 2)} {f(py_ - 1)}", fill="none", stroke=RUST.light, stroke_width="1"))
    o.append(ell(-66, -56, 20, 14, fill="#050302", opacity="0.6", filter=f"url(#{d['blur6']})"))
    o.append(path("M-114 -42 Q-96 -34 -72 -46", fill="none", stroke="#060403", stroke_width="2.8", stroke_linecap="round"))
    # lower teeth come up over the barrel
    lt = "".join(f"M{f(-112 + k * 7)} {f(-25 - k * 1.6)} l3 {f(-8)} l3 8 Z " for k in range(4))
    o.append(path(lt, fill="#E2D8BE", stroke=OUTL, stroke_width="0.5"))
    # teeth biting down into the barrel
    t_ = "".join(f"M{f(-112 + k * 6.2)} {f(-41.5 + k * 0.5)} l3 {f(9.5 - abs(k - 2) * 0.7)} l3 {f(-9.5 + abs(k - 2) * 0.7)} Z " for k in range(6))
    o.append(path(t_, fill="#EDE4CC", stroke=OUTL, stroke_width="0.5"))
    o.append(path("M-112 -41 Q-96 -35 -76 -40", fill="none", stroke="#2A1810", stroke_width="1"))
    # small, deep-set eye: the blue thread again
    o.append(ell(-92, -72, 7, 5, fill="#050302"))
    o.append(path("M-102 -76 Q-92 -84 -80 -75", fill="none", stroke="#080504", stroke_width="4.5", stroke_linecap="round"))
    o.append(path("M-102 -78 Q-92 -86 -80 -77", fill="none", stroke=H.light, stroke_width="1.3", stroke_opacity="0.75", stroke_linecap="round"))
    o.append(circ(-93, -71.5, 2.3, fill=BLUE_L, filter=f"url(#{d['glow']})"))
    o.append(circ(-93.5, -72, 0.8, fill="#FFFFFF"))
    # scraps
    if ground:
        o.append(_wheel(K, -112, -5, 15))
        for (x, y, a, w0, m) in ((-72, 4, 20, 13, "iron"), (-20, 8, -30, 11, "iron"), (24, 8, 12, 9, "brass"), (84, 4, -18, 12, "iron"), (-48, 10, 62, 8, "brass")):
            o.append(group(K.form((-w0 / 2, 0), (w0 / 2, 0), 1.7, 1.1, MATS[m], ao_start=False, spec=False), transform=f"translate({x} {y}) rotate({a})"))
    return "".join(o)


# ------------------------------------------------------------------ 어둑시니
def eodukshini_bg(K, x0, y0, w, h):
    """Moonlit mist backdrop: the one card that needs a light field so a dark mass reads as a silhouette."""
    d = _defs(K)
    sky = _lin(K, [(0, "#4C5A82"), (0.5, "#7C86A8"), (0.8, "#A9A6B4"), (1, "#5C5C70")], 0, y0, 0, y0 + h)
    o = [f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" fill="{sky}"></rect>']
    mx, my = x0 + w * 0.76, y0 + h * 0.2
    o.append(circ(mx, my, 34, fill="#F4EFD8", opacity="0.28", filter=f"url(#{d['blur6']})"))
    o.append(circ(mx, my, 17, fill="#F6F2DE", opacity="0.85"))
    hills = (f"M{x0} {y0 + h * 0.74} Q{x0 + w * 0.2} {y0 + h * 0.58} {x0 + w * 0.42} {y0 + h * 0.72} T{x0 + w * 0.8} {y0 + h * 0.66} T{x0 + w} {y0 + h * 0.72} V{y0 + h} H{x0} Z")
    o.append(path(hills, fill="#39405A", opacity="0.85"))
    return "".join(o)


def eodukshini(K, seed=4, ground=True):
    """어둑시니: limbless column of ink, pinched at the base, swelling upward; ~172 tall, ~150 wide at the top."""
    d = _defs(K)
    rr = random.Random(seed)
    o = []
    if ground:
        o.append(ell(4, 1, 46, 11, fill="#05040A", opacity="0.75", filter=f"url(#{K.p}-cast)"))
        o.append(ell(0, 0, 18, 5, fill="#05040A", opacity="0.9", filter=f"url(#{K.p}-soft)"))
    mass = [(-6, -6), (-14, -28), (-30, -56), (-54, -92), (-70, -124), (-62, -152), (-30, -168), (6, -178), (44, -170), (68, -146), (72, -116), (52, -84), (28, -52), (14, -26), (6, -6)]
    md = K._smooth(mass)
    # outer halo of diffused ink
    o.append(path(md, fill="#120E22", opacity="0.5", filter=f"url(#{d['blur6']})", transform="translate(0 -6) scale(1.06)"))
    body = _lin(K, [(0, "#2C2748"), (0.35, "#17132A"), (0.7, "#0B0916"), (1, "#05040A")], -70, -170, 64, -20)
    ink = []
    ink.append(path(md, fill=body, stroke="#05040A", stroke_width="2"))
    # dark form inside: swirling plumes read as volume, with slate lights on the upper left
    for (x, y, rx, ry, c, op) in ((-30, -130, 28, 30, "#3E3866", 0.55), (22, -142, 26, 18, "#2C2850", 0.5), (34, -106, 26, 26, "#020108", 0.85),
                                  (-8, -88, 22, 26, "#020108", 0.8), (-42, -102, 14, 16, "#4A4478", 0.35), (-2, -58, 12, 22, "#1A1630", 0.6)):
        ink.append(ell(x, y, rx, ry, fill=c, opacity=str(op), filter=f"url(#{d['blur6']})"))
    o.append(group("".join(ink), filter=f"url(#{d['ink']})"))
    # swirl strokes like ink dropped in water
    sw = []
    for (dd, c, op, w) in (("M-44 -128 C-20 -150 22 -146 46 -118", "#5A5490", 0.55, 2.2),
                           ("M-52 -104 C-36 -84 -30 -72 -24 -52", "#4A447E", 0.5, 1.8),
                           ("M46 -130 C60 -110 44 -84 26 -70", "#3A3468", 0.5, 1.8),
                           ("M-30 -156 C-6 -166 26 -162 50 -148", "#6A64A4", 0.55, 1.6)):
        sw.append(path(dd, fill="none", stroke=c, stroke_opacity=str(op), stroke_width=str(w), stroke_linecap="round"))
    o.append(group("".join(sw), filter=f"url(#{d['ink2']})"))
    # rim: the goblin-blue thread on the light side, faint violet on the other
    o.append(group(path("M-14 -28 C-30 -56 -54 -92 -70 -124 C-72 -142 -52 -164 -30 -168 C-14 -174 -4 -178 6 -178", fill="none", stroke=BLUE, stroke_width="2.3", stroke_opacity="0.95", stroke_linecap="round"), filter=f"url(#{d['ink']})"))
    o.append(group(path("M44 -170 C60 -160 74 -134 72 -116 C66 -96 42 -66 14 -26", fill="none", stroke=VIOLET, stroke_width="1.6", stroke_opacity="0.75", stroke_linecap="round"), filter=f"url(#{d['ink']})"))
    # plumes dissolving upward
    for k in range(9):
        x = -56 + k * 15 + rr.uniform(-4, 4)
        y0 = -158 - abs(math.sin(k)) * 8
        ln = rr.uniform(10, 24)
        o.append(path(f"M{f(x)} {f(y0)} q{f(rr.uniform(-8, 8))} {f(-ln * 0.5)} {f(rr.uniform(-5, 5))} {f(-ln)}", fill="none", stroke="#100C1E", stroke_width=f(rr.uniform(3, 7)),
                      stroke_opacity="0.75", stroke_linecap="round", filter=f"url(#{K.p}-soft)"))
    # eyes: one pair that watches back, and more opening deep in the mass
    for (x, y, s_, op) in ((-40, -94, 0.55, 0.55), (46, -128, 0.5, 0.5), (-20, -62, 0.42, 0.5), (34, -84, 0.42, 0.45), (-50, -140, 0.45, 0.5)):
        for sx in (-1, 1):
            o.append(ell(x + sx * 8 * s_, y, 3.6 * s_, 1.5 * s_, fill="#D8D2F4", opacity=str(op)))
    for sx in (-1, 1):
        x, y = 2 + sx * 17, -124 - sx * 2
        o.append(path(f"M{f(x - 11)} {f(y + sx * 2.5)} Q{f(x)} {f(y - 8)} {f(x + 11)} {f(y - sx * 2.5)} Q{f(x)} {f(y + 7)} {f(x - 11)} {f(y + sx * 2.5)} Z",
                      fill="#EDE8FF", filter=f"url(#{d['glow']})"))
        o.append(ell(x, y, 2.1, 5.2, fill="#0A0616"))
    # someone small, looking up at it with a lantern: this is how it grows
    if ground:
        o.append(_villager(K, -66, -2))
    return "".join(o)


def _villager(K, x, y):
    d = _defs(K)
    sil = "#0E0D14"
    o = [circ(x + 12, y - 22, 13, fill=AMBER_L, opacity="0.5", filter=f"url(#{d['blur3']})"),
         path(f"M{x - 5} {y} L{x - 4} {y - 13} Q{x - 6} {y - 26} {x - 2} {y - 28} L{x + 3} {y - 28} Q{x + 6} {y - 24} {x + 4} {y - 13} L{x + 5} {y} Z", fill=sil),
         circ(x + 0.6, y - 32, 3.6, fill=sil),
         ell(x + 0.6, y - 33.5, 7.5, 1.7, fill=sil, transform=f"rotate(-14 {x + 0.6} {y - 33.5})"),
         path(f"M{x + 3} {y - 25} L{x + 11} {y - 26}", stroke=sil, stroke_width="1.6", stroke_linecap="round"),
         path(f"M{x + 11} {y - 26} V{y - 22}", stroke=sil, stroke_width="0.6"),
         ell(x + 11, y - 19.5, 2.4, 3, fill=AMBER_L, stroke=sil, stroke_width="0.6")]
    return "".join(o)


# ------------------------------------------------------------------ 이무기
def _spline_samples(K, pts, widths, n_per=10):
    S = K._catmull(pts, n_per)
    nseg = len(pts) - 1
    out = []
    for j, (x, y, t) in enumerate(S):
        a, b = S[max(0, j - 1)], S[min(len(S) - 1, j + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1
        u = t * nseg
        i = min(int(u), nseg - 1)
        w = widths[i] + (widths[i + 1] - widths[i]) * (u - i)
        out.append((x, y, dx / L, dy / L, w))
    return out


def scaled_body(K, pts, widths, m=SCALE, spines=True, belly=True, seed=3, step=6.5):
    """A serpent section: continuous kit tube + belly plates + row-by-row scales + dorsal spines on the upper edge."""
    o = []
    S = _spline_samples(K, pts, widths)
    # orient: "up" is the normal facing the top of the screen, spines ride there and the belly runs below
    if spines:
        sp = []
        acc = 0.0
        for j in range(1, len(S)):
            x, y, tx, ty, w = S[j]
            acc += math.hypot(x - S[j - 1][0], y - S[j - 1][1])
            if acc < 9:
                continue
            acc = 0
            nx, ny = -ty, tx
            if ny > 0:
                nx, ny = -nx, -ny
            h = w * 0.55 + 2
            sp.append(f"M{f(x + nx * w * 0.86 - tx * 5)} {f(y + ny * w * 0.86 - ty * 5)} L{f(x + nx * (w + h) + tx * 2)} {f(y + ny * (w + h) + ty * 2)} L{f(x + nx * w * 0.86 + tx * 5)} {f(y + ny * w * 0.86 + ty * 5)} Z ")
        o.append(path("".join(sp), fill=mix(m.core, m.base, 0.55), stroke=OUTL, stroke_width="0.7", stroke_linejoin="round"))
    o.append(K.tube(pts, widths, m, n_per=10, chunks_per_seg=6))
    dark, lit, hi = [], [], []
    acc = step
    for j in range(1, len(S)):
        x, y, tx, ty, w = S[j]
        acc += math.hypot(x - S[j - 1][0], y - S[j - 1][1])
        if acc < step:
            continue
        acc = 0
        nx, ny = -ty, tx
        if ny > 0:
            nx, ny = -nx, -ny
        cols = max(3, int(w * 2 / step))
        odd = (j % 2) * 0.5
        for c in range(cols):
            u = -0.86 + (c + 0.5 + odd * 0.5) * 1.72 / cols
            if belly and u < -0.42:
                continue
            # perspective: scales foreshorten toward the tube edge
            sq = math.sqrt(max(0.1, 1 - u * u))
            px, py = x + nx * u * w, y + ny * u * w
            sw = step * 0.5 * sq
            sl = step * 0.62
            a0 = (px + nx * sw, py + ny * sw)
            a1 = (px - nx * sw, py - ny * sw)
            tip = (px + tx * sl, py + ty * sl)
            seg = f"M{P(a0)} Q{P((tip[0] + nx * sw, tip[1] + ny * sw))} {P(tip)} Q{P((tip[0] - nx * sw, tip[1] - ny * sw))} {P(a1)} "
            dark.append(seg)
            if u > 0.05:
                hi.append(f"M{P((px + nx * sw * 0.5 + tx, py + ny * sw * 0.5 + ty))} L{P((px + tx * sl * 0.62, py + ty * sl * 0.62))} ")
            elif u > -0.42:
                lit.append(f"M{P((px + nx * sw * 0.4 + tx, py + ny * sw * 0.4 + ty))} L{P((px + tx * sl * 0.5, py + ty * sl * 0.5))} ")
    if belly:
        bl = []
        for j in range(1, len(S), 2):
            x, y, tx, ty, w = S[j]
            nx, ny = -ty, tx
            if ny > 0:
                nx, ny = -nx, -ny
            a = (x - nx * w * 0.97, y - ny * w * 0.97)
            b = (x - nx * w * 0.42, y - ny * w * 0.42)
            bl.append(f"M{P(a)} Q{P(((a[0] + b[0]) / 2 + tx * 3, (a[1] + b[1]) / 2 + ty * 3))} {P(b)} ")
        # belly band sits under the scale rows on the side facing the water
        lo = [(x - (-ty if tx > 0 else ty) * w * 0.7, y - (tx if tx > 0 else -tx) * w * 0.7) for (x, y, tx, ty, w) in S]
        o.append(path("M" + " L".join(P(p) for p in lo), fill="none", stroke=mix(BELLY.base, m.core, 0.42), stroke_width=f(max(widths) * 0.52), stroke_linecap="butt", stroke_linejoin="round", stroke_opacity="0.9",
                      filter=f"url(#{K.p}-soft)"))
        o.append(path("".join(bl), fill="none", stroke="#0E2420", stroke_width="1", stroke_opacity="0.8"))
    o.append(path("".join(dark), fill="none", stroke="#031312", stroke_width="0.95", stroke_opacity="0.8"))
    o.append(path("".join(hi), fill="none", stroke=m.light, stroke_width="1.05", stroke_opacity="0.7", stroke_linecap="round"))
    o.append(path("".join(lit), fill="none", stroke=m.base, stroke_width="0.85", stroke_opacity="0.75", stroke_linecap="round"))
    return "".join(o)


def _imugi_head(K, hx, hy, s=1.0):
    """Faces screen left. Budding horns only: it has not become a dragon yet."""
    d = _defs(K)
    m = SCALE
    o = []
    # horn buds + back fins
    o.append(K.form((hx + 4 * s, hy - 12 * s), (hx + 17 * s, hy - 27 * s), 3.8 * s, 1.0 * s, MATS["horn"]))
    o.append(K.form((hx - 5 * s, hy - 13 * s), (hx + 4 * s, hy - 29 * s), 3.8 * s, 1.0 * s, MATS["horn"]))
    for k in range(3):
        o.append(path(f"M{f(hx + (14 + k * 4) * s)} {f(hy - (6 - k * 3) * s)} L{f(hx + (30 + k * 3) * s)} {f(hy - (14 - k * 8) * s)} L{f(hx + (16 + k * 4) * s)} {f(hy + (1 + k * 3) * s)} Z",
                      fill=mix(m.core, m.base, 0.5), stroke=OUTL, stroke_width="0.7"))
    # lower jaw, then mouth interior, then skull
    jaw = f"M{f(hx + 16 * s)} {f(hy + 7 * s)} C{f(hx + 2 * s)} {f(hy + 16 * s)} {f(hx - 20 * s)} {f(hy + 18 * s)} {f(hx - 36 * s)} {f(hy + 14 * s)} L{f(hx - 34 * s)} {f(hy + 9 * s)} C{f(hx - 16 * s)} {f(hy + 8 * s)} {f(hx + 2 * s)} {f(hy + 5 * s)} {f(hx + 16 * s)} {f(hy + 3 * s)} Z"
    o.append(path(jaw, fill=_lin(K, [(0, BELLY.light), (0.6, BELLY.base), (1, BELLY.core)], hx, hy + 6 * s, hx, hy + 18 * s), stroke=OUTL, stroke_width="0.9"))
    o.append(path(f"M{f(hx - 38 * s)} {f(hy + 2 * s)} L{f(hx + 12 * s)} {f(hy + 2 * s)} L{f(hx + 14 * s)} {f(hy + 6 * s)} C{f(hx)} {f(hy + 9 * s)} {f(hx - 20 * s)} {f(hy + 11 * s)} {f(hx - 34 * s)} {f(hy + 10 * s)} Z", fill="#2A0C10"))
    o.append(path(f"M{f(hx - 30 * s)} {f(hy + 8 * s)} C{f(hx - 16 * s)} {f(hy + 7 * s)} {f(hx)} {f(hy + 6 * s)} {f(hx + 8 * s)} {f(hy + 5 * s)}", fill="none", stroke="#A0404A", stroke_width=f(2.2 * s), stroke_linecap="round"))
    skull = (f"M{f(hx + 20 * s)} {f(hy - 7 * s)} C{f(hx + 10 * s)} {f(hy - 17 * s)} {f(hx - 8 * s)} {f(hy - 18 * s)} {f(hx - 18 * s)} {f(hy - 11 * s)} "
             f"C{f(hx - 28 * s)} {f(hy - 9 * s)} {f(hx - 40 * s)} {f(hy - 7 * s)} {f(hx - 44 * s)} {f(hy - 1 * s)} C{f(hx - 46 * s)} {f(hy + 3 * s)} {f(hx - 42 * s)} {f(hy + 5 * s)} {f(hx - 36 * s)} {f(hy + 4 * s)} "
             f"L{f(hx - 4 * s)} {f(hy + 4 * s)} C{f(hx + 6 * s)} {f(hy + 6 * s)} {f(hx + 16 * s)} {f(hy + 6 * s)} {f(hx + 22 * s)} {f(hy + 3 * s)} Z")
    grad = K._sphere_grad(hx - 10 * s, hy - 6 * s, 32 * s, m)
    o.append(path(skull, fill=f"url(#{grad})", stroke=OUTL, stroke_width="1", stroke_linejoin="round"))
    sc = []
    for r_ in range(3):
        for k in range(7 - r_):
            x = hx + (16 - k * 7 - r_ * 3) * s
            y = hy + (-12 + r_ * 5 + abs(k - 2) * 0.2) * s
            sc.append(f"M{f(x + 3 * s)} {f(y - 2 * s)} Q{f(x - 3 * s)} {f(y - 1 * s)} {f(x + 3 * s)} {f(y + 3 * s)} ")
    o.append(K.clip_overlay(skull, path("".join(sc), fill="none", stroke="#041615", stroke_width="0.8", stroke_opacity="0.8")
                            + ell(hx + 14 * s, hy, 14 * s, 11 * s, fill="#031110", opacity="0.65", filter=f"url(#{K.p}-ao)")
                            + ell(hx - 18 * s, hy - 10 * s, 14 * s, 3.4 * s, fill="#EFFFF8", opacity="0.5", filter=f"url(#{K.p}-soft)")))
    # teeth
    tt = "".join(f"M{f(hx - 36 * s + k * 7 * s)} {f(hy + 3.6 * s)} l{f(2 * s)} {f((5.6 if k in (0, 3) else 3) * s)} l{f(2 * s)} {f(-(5.6 if k in (0, 3) else 3) * s)} Z " for k in range(6))
    o.append(path(tt, fill="#F4EFD8", stroke=OUTL, stroke_width="0.4"))
    # nostril + eye under a ridge, blue thread in the iris
    o.append(ell(hx - 39 * s, hy - 2.5 * s, 1.8 * s, 1.1 * s, fill="#041010"))
    o.append(ell(hx - 7 * s, hy - 7 * s, 6 * s, 4.2 * s, fill="#041010"))
    o.append(ell(hx - 7 * s, hy - 7 * s, 4.2 * s, 3.2 * s, fill=BLUE_L, filter=f"url(#{d['glow']})"))
    o.append(ell(hx - 7 * s, hy - 7 * s, 1 * s, 3 * s, fill="#041010"))
    o.append(circ(hx - 8.4 * s, hy - 8.4 * s, 0.8 * s, fill="#FFFFFF"))
    o.append(path(f"M{f(hx - 15 * s)} {f(hy - 11 * s)} Q{f(hx - 6 * s)} {f(hy - 15 * s)} {f(hx + 3 * s)} {f(hy - 9 * s)}", fill="none", stroke="#041010", stroke_width=f(2.8 * s), stroke_linecap="round"))
    o.append(path(f"M{f(hx - 15 * s)} {f(hy - 12.4 * s)} Q{f(hx - 6 * s)} {f(hy - 16.2 * s)} {f(hx + 3 * s)} {f(hy - 10.6 * s)}", fill="none", stroke=m.light, stroke_width=f(1.1 * s), stroke_opacity="0.85", stroke_linecap="round"))
    # whiskers feeling toward the pearl
    o.append(K.tube([(hx - 40 * s, hy - 3 * s), (hx - 54 * s, hy - 9 * s), (hx - 62 * s, hy + 1 * s)], [1.5 * s, 1.0 * s, 0.5 * s], BELLY, outline=False, n_per=4, chunks_per_seg=2))
    o.append(K.tube([(hx - 38 * s, hy), (hx - 48 * s, hy + 10 * s), (hx - 60 * s, hy + 14 * s)], [1.4 * s, 0.9 * s, 0.5 * s], BELLY, outline=False, n_per=4, chunks_per_seg=2))
    return "".join(o)


def imugi(K, seed=3, ground=True):
    """이무기: serpent half risen from a black pond, about 290 wide and 215 tall; water line at y=0."""
    d = _defs(K)
    rr = random.Random(seed)
    o = []
    if ground:
        # stone-rimmed pond at 2:1
        rim = [(-140, 6), (-98, -22), (-30, -32), (60, -32), (122, -18), (142, 6), (102, 30), (20, 40), (-72, 36), (-122, 22)]
        rd = K._smooth(rim)
        o.append(path(rd, fill="#0C0A08", transform="translate(0 7)"))
        o.append(path(rd, fill=f"url(#{d['water']})", stroke="#0A0706", stroke_width="1.2"))
        o.append(K.clip_overlay(rd, ell(-30, -24, 90, 14, fill="#3A8A8E", opacity="0.3", filter=f"url(#{d['blur6']})")))
    # far coil, then the main coil, each entering and leaving the water
    back = [(-112, 4), (-104, -28), (-74, -46), (-44, -30), (-36, 6)]
    o.append(group(scaled_body(K, back, [15, 18, 20, 19, 17], SCALE.back(0.28), seed=seed), opacity="1"))
    front = [(44, 10), (54, -34), (88, -52), (120, -28), (124, 8)]
    o.append(scaled_body(K, front, [17, 20, 21, 19, 16], SCALE.back(0.14), seed=seed + 1))
    # the rising neck: thick at the water, S-curving up to the head
    neck = [(-6, 22), (-2, -20), (30, -56), (38, -92), (8, -120), (-30, -128)]
    o.append(scaled_body(K, neck, [25, 25, 22, 18, 14, 11.5], SCALE, seed=seed + 2, step=7.2))
    # form shadow where the coils pass under each other, then the water closes over the entry points
    o.append(ell(14, -2, 26, 7, fill="#020808", opacity="0.75", filter=f"url(#{K.p}-soft)"))
    if ground:
        for (cx, cy, rx, ry) in ((-112, 8, 21, 6), (-36, 10, 22, 6.5), (44, 13, 23, 7), (124, 11, 21, 6), (-6, 22, 33, 9)):
            o.append(ell(cx, cy + 3.5, rx, ry, fill=f"url(#{d['water']})"))
            for k in range(3):
                g_ = 1 + k * 0.32
                o.append(ell(cx, cy, rx * g_, ry * g_, fill="none", stroke=BLUE_L if k == 0 else "#7FC4C8", stroke_width=str(1.4 - k * 0.35), stroke_opacity=f"{0.8 - k * 0.24:.2f}"))
            # foam and drips on the lit side
            for _ in range(4):
                o.append(circ(cx - rx * rr.uniform(0.2, 1.0), cy - ry * rr.uniform(0.2, 1.1), rr.uniform(0.6, 1.3), fill="#EFFFFA", opacity="0.75"))
    o.append(_imugi_head(K, -30, -130, 1.08))
    # 여의주: just out of reach. A cold pearl, in its own bright halo.
    px, py = -124, -146
    o.append(circ(px, py, 34, fill=f"url(#{d['orb']})", opacity="0.65"))
    o.append(K.ball(px, py, 12, Mat("#D8ECFA", "#FFFFFF", "#507CB0", "#9AC8E8", spec="#FFFFFF", spec_op=1.0, gloss=1.0)))
    o.append(path(f"M{px - 6} {py + 4} Q{px + 2} {py + 9} {px + 8} {py + 2}", fill="none", stroke="#2E5E92", stroke_width="1.1", stroke_opacity="0.7"))
    for k in range(7):
        a = rr.uniform(0, math.tau)
        rr_ = rr.uniform(15, 28)
        o.append(circ(px + math.cos(a) * rr_, py + math.sin(a) * rr_ * 0.8, rr.uniform(0.6, 1.3), fill="#FFFFFF", opacity="0.85"))
    if ground:
        # rim stones in front of the water
        for (x, y, r) in ((-126, 22, 8), (-96, 32, 9), (-58, 38, 8), (-20, 42, 10), (22, 42, 9), (62, 38, 8), (98, 32, 10), (128, 20, 8), (140, 6, 7)):
            o.append(K.ball(x + rr.uniform(-2, 2), y, r, Mat("#5A5650", "#A8A49A", "#141210", "#44627A", spec_op=0.2, gloss=0.2), ry=r * 0.66))
    return "".join(o)
