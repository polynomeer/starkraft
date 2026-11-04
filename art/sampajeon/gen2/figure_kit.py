"""figure_kit — pre-rendered-sprite style character parts for 삼파전 (shared by all artboards).

Goal: the look of late-90s/2000s Korean 2.5D games' pre-rendered 3D sprites — every limb is a
shaded cylinder (light edge -> specular -> midtone -> core shadow -> reflected bounce light),
joints are spheres, hands have fingers, cloth has folds that wrap the limb, and there is ambient
occlusion wherever two forms meet. Light comes from the top-left (screen).

Usage:
    K = Kit("r2c")                  # id prefix; unique per artboard + call site
    out = [K.limb((0,-60),(8,-35),7,5, K.mat("skin_blue"), elbow=True), ...]
    defs = K.defs_svg()             # put inside <defs> of the same <svg>

Every gradient is userSpaceOnUse and generated per shape, so shading follows each limb's axis.
Coordinates are in the caller's local space; wrap in <g transform> to place/scale.
"""
import math

LIGHT = (-0.62, -0.78)  # unit vector pointing TOWARD the light (top-left)
OUTLINE = "#120D08"


def f(v):
    if isinstance(v, (int,)):
        return str(v)
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def P(p):
    return f"{f(p[0])} {f(p[1])}"


def _hex(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def _rgb(t):
    return "#" + "".join(f"{max(0, min(255, int(round(v)))):02X}" for v in t)


def mix(a, b, t):
    A, B = _hex(a), _hex(b)
    return _rgb(tuple(A[i] + (B[i] - A[i]) * t for i in range(3)))


def shade(c, k):
    """k<0 darken toward ink-brown, k>0 lighten toward warm white."""
    return mix(c, "#0B0806", -k) if k < 0 else mix(c, "#FFF6E4", k)


# ------------------------------------------------------------------ materials
class Mat:
    """A painted material: five-tone ramp + specular + bounce light."""

    def __init__(self, base, light=None, core=None, bounce=None, spec="#FFF8EA", spec_op=0.35,
                 gloss=0.5, outline=OUTLINE):
        self.base = base
        self.light = light or shade(base, 0.32)
        self.core = core or shade(base, -0.62)
        self.bounce = bounce or mix(shade(base, -0.25), "#6A7FA0", 0.28)
        self.spec = spec
        self.spec_op = spec_op
        self.gloss = gloss  # 0 matte cloth .. 1 polished metal
        self.outline = outline

    def back(self, k=0.22):
        """Same material pushed into depth (far-side limbs)."""
        return Mat(shade(self.base, -k), shade(self.light, -k), shade(self.core, -k * 0.5),
                   shade(self.bounce, -k), self.spec, self.spec_op * 0.5, self.gloss, self.outline)


MATS = {
    "skin": Mat("#C98E62", "#F2C49A", "#4A2614", "#8A5A48"),
    "skin_fair": Mat("#D8A47C", "#F8D8B8", "#5A3020", "#9A6A5A"),
    "skin_tan": Mat("#B47A50", "#E2AE82", "#3E1E0E", "#7E5040"),
    "skin_weathered": Mat("#A87052", "#D49C78", "#3A1A10", "#7A5248"),
    "skin_ruddy": Mat("#C4805C", "#ECB490", "#4E2014", "#8E5048"),
    "skin_dark": Mat("#A86A44", "#DCA27A", "#3A1C0E", "#7A4A3A"),
    "skin_blue": Mat("#3E4FA8", "#8E9EEC", "#0C0F38", "#3BA3D8", spec="#E4ECFF", spec_op=0.45),
    "skin_ghost": Mat("#6E86D8", "#C4D4FF", "#1A2460", "#7FE0FF", spec="#FFFFFF", spec_op=0.5),
    "red_cloth": Mat("#B23A22", "#E8774E", "#3A0C06", "#7A3A40", spec_op=0.18, gloss=0.2),
    "white_hemp": Mat("#D8CFB8", "#FBF5E6", "#5A503E", "#9AA0A8", spec_op=0.2, gloss=0.15),
    "indigo": Mat("#33427A", "#6E80C0", "#0C1028", "#4A5A80", spec_op=0.2, gloss=0.2),
    "black_cloth": Mat("#2A2622", "#5E564C", "#070605", "#3A4250", spec_op=0.22, gloss=0.25),
    "ochre_cloth": Mat("#C2922E", "#F0C868", "#3A2608", "#8A7048", spec_op=0.2, gloss=0.2),
    "green_cloth": Mat("#2E6E5E", "#5EAE96", "#081A16", "#3A5A68", spec_op=0.2, gloss=0.2),
    "leather": Mat("#6A4228", "#A8784E", "#1A0C06", "#5A4440", spec_op=0.4, gloss=0.55),
    "leather_dark": Mat("#3A2618", "#6E5038", "#0A0503", "#3A3438", spec_op=0.45, gloss=0.6),
    "brass": Mat("#B88A3A", "#F4D488", "#3A2408", "#7A6A50", spec="#FFFBE8", spec_op=0.85, gloss=1.0),
    "iron": Mat("#5A5E66", "#A8B0BC", "#121418", "#4E6278", spec="#FFFFFF", spec_op=0.8, gloss=1.0),
    "wood": Mat("#7A4E2A", "#B88050", "#24120A", "#5A4A48", spec_op=0.25, gloss=0.3),
    "lacquer": Mat("#4A1E14", "#8A4430", "#0E0503", "#4A3036", spec="#FFE8D8", spec_op=0.7, gloss=0.9),
    "straw": Mat("#C8A860", "#F0D898", "#4A3818", "#8A8068", spec_op=0.25, gloss=0.2),
    "horn": Mat("#D8B060", "#FFF0B8", "#4A3010", "#A08868", spec_op=0.6, gloss=0.7),
    "hair": Mat("#1E1612", "#4E3E32", "#050302", "#2E3440", spec="#C8D8FF", spec_op=0.45, gloss=0.7),
    "tiger": Mat("#D8962E", "#FFC860", "#4A2408", "#8A6A48", spec_op=0.25, gloss=0.25),
}


# ------------------------------------------------------------------ kit
class Kit:
    def __init__(self, prefix):
        self.p = prefix
        self.n = 0
        self._defs = []
        self._filters_done = False

    def mat(self, name):
        return MATS[name]

    def id(self, tag="g"):
        self.n += 1
        return f"{self.p}-{tag}{self.n}"

    # --- defs ---------------------------------------------------------
    def _filters(self):
        if self._filters_done:
            return
        self._filters_done = True
        p = self.p
        self._defs.append(
            f'<filter id="{p}-ao" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2.2"></feGaussianBlur></filter>'
            f'<filter id="{p}-soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="0.9"></feGaussianBlur></filter>'
            f'<filter id="{p}-cast" x="-50%" y="-80%" width="200%" height="260%"><feGaussianBlur stdDeviation="3.2"></feGaussianBlur></filter>'
            f'<filter id="{p}-weave" x="0" y="0" width="100%" height="100%">'
            f'<feTurbulence type="fractalNoise" baseFrequency="0.9 0.35" numOctaves="2" seed="7" result="n"></feTurbulence>'
            f'<feColorMatrix in="n" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.22 0" result="a"></feColorMatrix>'
            f'<feComposite in="a" in2="SourceGraphic" operator="in" result="t"></feComposite>'
            f'<feMerge><feMergeNode in="SourceGraphic"></feMergeNode><feMergeNode in="t"></feMergeNode></feMerge></filter>'
        )

    def defs_svg(self):
        self._filters()
        return "".join(self._defs)

    def _cyl_grad(self, c, n, w, m):
        """Linear gradient across a form: lit edge -> spec -> base -> core shadow -> bounce."""
        gid = self.id("c")
        x1, y1 = c[0] + n[0] * w, c[1] + n[1] * w
        x2, y2 = c[0] - n[0] * w, c[1] - n[1] * w
        sp = mix(m.light, m.spec, 0.25 + 0.55 * m.gloss)
        stops = [(0.0, shade(m.light, -0.12)), (0.14, m.light), (0.26 + 0.04 * (1 - m.gloss), sp),
                 (0.4, m.light), (0.58, m.base), (0.78, m.core), (0.9, mix(m.core, m.bounce, 0.55)), (1.0, m.bounce)]
        st = "".join(f'<stop offset="{f(o)}" stop-color="{col}"></stop>' for o, col in stops)
        self._defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" '
                          f'x2="{f(x2)}" y2="{f(y2)}">{st}</linearGradient>')
        return gid

    def _axis_ao(self, a, b, at_start=True, at_end=False, strength=0.38):
        gid = self.id("a")
        s = []
        if at_start:
            s += [(0, strength), (0.22, 0)]
        else:
            s += [(0, 0)]
        if at_end:
            s += [(0.8, 0), (1, strength * 0.8)]
        else:
            s += [(1, 0)]
        st = "".join(f'<stop offset="{f(o)}" stop-color="#0A0604" stop-opacity="{f(op)}"></stop>' for o, op in s)
        self._defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(a[0])}" y1="{f(a[1])}" '
                          f'x2="{f(b[0])}" y2="{f(b[1])}">{st}</linearGradient>')
        return gid

    def _sphere_grad(self, cx, cy, r, m):
        gid = self.id("s")
        fx, fy = cx + LIGHT[0] * r * 0.42, cy + LIGHT[1] * r * 0.42
        sp = mix(m.light, m.spec, 0.3 + 0.6 * m.gloss)
        st = "".join(f'<stop offset="{f(o)}" stop-color="{col}"></stop>' for o, col in
                     ((0, sp), (0.18, m.light), (0.5, m.base), (0.8, m.core), (0.92, mix(m.core, m.bounce, 0.5)), (1, m.bounce)))
        self._defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(cx + r * 0.08)}" cy="{f(cy + r * 0.1)}" '
                          f'r="{f(r * 1.12)}" fx="{f(fx)}" fy="{f(fy)}">{st}</radialGradient>')
        return gid

    # --- geometry helpers -----------------------------------------------
    @staticmethod
    def _lit_normal(a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / L, dx / L
        if nx * LIGHT[0] + ny * LIGHT[1] < 0:
            nx, ny = -nx, -ny
        return (nx, ny), (dx / L, dy / L), L

    def _profile(self, a, b, w1, w2, bumps, n=14):
        """Outline of a tapered form; bumps = [(t, amount, side)] side +1 lit, -1 shadow, 0 both."""
        nrm, ax, L = self._lit_normal(a, b)
        left, right = [], []
        for i in range(n + 1):
            t = i / n
            w = w1 + (w2 - w1) * t
            wl = wr = w
            for (bt, amt, side) in bumps:
                k = math.exp(-((t - bt) / 0.2) ** 2) * amt
                if side >= 0:
                    wl += k
                if side <= 0:
                    wr += k
            px, py = a[0] + ax[0] * L * t, a[1] + ax[1] * L * t
            left.append((px + nrm[0] * wl, py + nrm[1] * wl))
            right.append((px - nrm[0] * wr, py - nrm[1] * wr))
        return left, right, nrm, ax, L

    @staticmethod
    def _smooth(pts, closed=True):
        """Catmull-Rom -> cubic bezier path."""
        if closed:
            q = [pts[-1]] + pts + [pts[0], pts[1]]
        else:
            q = [pts[0]] + pts + [pts[-1]]
        d = f"M{P(q[1])}"
        for i in range(1, len(q) - 2):
            p0, p1, p2, p3 = q[i - 1], q[i], q[i + 1], q[i + 2]
            c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
            c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
            d += f" C{P(c1)} {P(c2)} {P(p2)}"
        return d + (" Z" if closed else "")

    def _capsule_d(self, left, right, nrm, ax, w1, w2, a, b):
        # round caps: a few points around each end
        cap_b = [(b[0] + ax[0] * w2 * 0.55 + nrm[0] * w2 * 0.55, b[1] + ax[1] * w2 * 0.55 + nrm[1] * w2 * 0.55),
                 (b[0] + ax[0] * w2 * 0.8, b[1] + ax[1] * w2 * 0.8),
                 (b[0] + ax[0] * w2 * 0.55 - nrm[0] * w2 * 0.55, b[1] + ax[1] * w2 * 0.55 - nrm[1] * w2 * 0.55)]
        cap_a = [(a[0] - ax[0] * w1 * 0.55 - nrm[0] * w1 * 0.55, a[1] - ax[1] * w1 * 0.55 - nrm[1] * w1 * 0.55),
                 (a[0] - ax[0] * w1 * 0.8, a[1] - ax[1] * w1 * 0.8),
                 (a[0] - ax[0] * w1 * 0.55 + nrm[0] * w1 * 0.55, a[1] - ax[1] * w1 * 0.55 + nrm[1] * w1 * 0.55)]
        pts = left + cap_b + list(reversed(right)) + cap_a
        return self._smooth(pts)

    # --- primitives --------------------------------------------------------
    def form(self, a, b, w1, w2, m, bumps=(), ao_start=True, ao_end=False, outline=True, spec=True, extra=""):
        """A shaded tapered cylinder (limb segment, neck, finger, shaft...)."""
        self._filters()
        left, right, nrm, ax, L = self._profile(a, b, w1, w2, list(bumps))
        d = self._capsule_d(left, right, nrm, ax, w1, w2, a, b)
        wmax = max(w1, w2) + max([bb[1] for bb in bumps] + [0])
        c = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        gid = self._cyl_grad(c, nrm, wmax, m)
        out = [f'<path d="{d}" fill="url(#{gid})"'
               + (f' stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.7" stroke-linejoin="round"' if outline else "")
               + f"{extra}></path>"]
        if ao_start or ao_end:
            aid = self._axis_ao(a, b, ao_start, ao_end)
            out.append(f'<path d="{d}" fill="url(#{aid})"></path>')
        if spec and m.spec_op > 0:
            # specular streak along lit side
            sl = [(p[0] * 0.62 + q[0] * 0.38, p[1] * 0.62 + q[1] * 0.38) for p, q in zip(left, right)]
            k0, k1 = int(len(sl) * 0.18), int(len(sl) * 0.78)
            seg = sl[k0:k1 + 1]
            if len(seg) >= 2:
                out.append(f'<path d="{self._smooth(seg, closed=False)}" fill="none" stroke="{m.spec}" '
                           f'stroke-width="{f(max(0.6, wmax * (0.18 + 0.12 * m.gloss)))}" stroke-opacity="{f(m.spec_op)}" '
                           f'stroke-linecap="round" filter="url(#{self.p}-soft)"></path>')
            # rim of bounce light on the shadow edge
            rl = [(p[0] * 0.1 + q[0] * 0.9, p[1] * 0.1 + q[1] * 0.9) for p, q in zip(left, right)][k0:k1 + 1]
            out.append(f'<path d="{self._smooth(rl, closed=False)}" fill="none" stroke="{m.bounce}" stroke-width="{f(max(0.5, wmax * 0.12))}" '
                       f'stroke-opacity="0.38" stroke-linecap="round"></path>')
        return "".join(out)

    def ball(self, cx, cy, r, m, outline=True, ry=None):
        self._filters()
        gid = self._sphere_grad(cx, cy, r, m)
        o = f' stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.7"' if outline else ""
        if ry is None:
            return f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="url(#{gid})"{o}></circle>'
        return f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(r)}" ry="{f(ry)}" fill="url(#{gid})"{o}></ellipse>'

    def ao(self, cx, cy, rx, ry, op=0.55):
        self._filters()
        return f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx)}" ry="{f(ry)}" fill="#0A0604" opacity="{f(op)}" filter="url(#{self.p}-ao)"></ellipse>'

    def cast_shadow(self, cx, cy, rx, ry, op=0.55, lean=0.0):
        """Ground shadow: soft wide cast (offset to bottom-right, away from light) + tight contact AO."""
        self._filters()
        return (f'<ellipse cx="{f(cx + rx * 0.35 + lean)}" cy="{f(cy + ry * 0.15)}" rx="{f(rx * 1.25)}" ry="{f(ry)}" fill="#000" '
                f'opacity="{f(op * 0.7)}" filter="url(#{self.p}-cast)"></ellipse>'
                f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(rx * 0.7)}" ry="{f(ry * 0.5)}" fill="#000" opacity="{f(op)}" '
                f'filter="url(#{self.p}-soft)"></ellipse>')

    # --- anatomy -------------------------------------------------------------
    def arm_v1(self, shoulder, elbow, wrist, m_upper, m_fore=None, w=6.0, muscular=0.0, sleeve=None, cuff=None,
            hand="fist", hand_dir=None, hand_m=None, back=False):
        """Upper arm + elbow + forearm (+ hand). sleeve=Mat draws a cloth sleeve with folds over the upper/fore arm.
        muscular adds deltoid/biceps/forearm bulges (for brutes)."""
        mu = m_upper.back() if back else m_upper
        mf = (m_fore or m_upper)
        mf = mf.back() if back else mf
        hm = (hand_m or MATS["skin"])
        hm = hm.back() if back else hm
        out = []
        ub = [(0.12, w * (0.25 + 0.35 * muscular), 1), (0.45, w * (0.18 + 0.4 * muscular), 1), (0.55, w * 0.25 * muscular, -1)]
        fb = [(0.22, w * (0.2 + 0.35 * muscular), 0), (0.3, w * 0.15 * muscular, 1)]
        out.append(self.form(shoulder, elbow, w, w * 0.78, mu, ub, ao_start=True))
        out.append(self.ball(elbow[0], elbow[1], w * 0.6, mu, outline=False))
        out.append(self.form(elbow, wrist, w * 0.8, w * 0.55, mf, fb, ao_start=True))
        if sleeve is not None:
            out.append(self.folds(shoulder, elbow, w, w * 0.8, sleeve if not back else sleeve.back(), n=3))
            out.append(self.folds(elbow, wrist, w * 0.8, w * 0.62, sleeve if not back else sleeve.back(), n=3, start=0.05))
        if cuff is not None:
            cm = cuff.back() if back else cuff
            ax = (wrist[0] - elbow[0], wrist[1] - elbow[1])
            L = math.hypot(*ax) or 1
            c0 = (wrist[0] - ax[0] / L * w * 1.1, wrist[1] - ax[1] / L * w * 1.1)
            out.append(self.form(c0, (wrist[0] - ax[0] / L * w * 0.2, wrist[1] - ax[1] / L * w * 0.2), w * 0.72, w * 0.7, cm, ao_start=False))
        if hand:
            hd = hand_dir or (wrist[0] - elbow[0], wrist[1] - elbow[1])
            out.append(self.hand(wrist, hd, w * 0.72, hm, kind=hand))
        return "".join(out)

    def leg_v1(self, hip, knee, ankle, m_thigh, m_shin=None, w=7.0, muscular=0.0, trouser=None, wrap=None,
            boot=None, foot_dir=1, back=False):
        """Thigh + knee + shin + foot. trouser=Mat makes baggy 바지 with folds; wrap=Mat draws 행전 leg wraps."""
        mt = m_thigh.back() if back else m_thigh
        ms = (m_shin or m_thigh)
        ms = ms.back() if back else ms
        out = []
        tb = [(0.25, w * (0.2 + 0.4 * muscular), 0), (0.65, w * 0.2 * muscular, 1)]
        sb = [(0.25, w * (0.18 + 0.45 * muscular), -1), (0.2, w * 0.1, 1)]
        if trouser is not None:
            tm = trouser.back() if back else trouser
            out.append(self.form(hip, knee, w * 1.25, w * 1.05, tm, [(0.4, w * 0.25, 0)], ao_start=True))
            out.append(self.ball(knee[0], knee[1], w * 0.88, tm, outline=False))
            out.append(self.form(knee, ankle, w * 1.05, w * 0.8, tm, [(0.35, w * 0.2, 0)], ao_start=True))
            out.append(self.folds(hip, knee, w * 1.25, w * 1.05, tm, n=3))
            out.append(self.folds(knee, ankle, w * 1.05, w * 0.8, tm, n=4, start=0.0))
        else:
            out.append(self.form(hip, knee, w, w * 0.78, mt, tb, ao_start=True))
            out.append(self.ball(knee[0], knee[1], w * 0.58, mt, outline=False))
            # kneecap highlight
            out.append(f'<ellipse cx="{f(knee[0] - w * 0.15)}" cy="{f(knee[1] - w * 0.1)}" rx="{f(w * 0.32)}" ry="{f(w * 0.26)}" '
                       f'fill="{mt.light}" opacity="0.35" filter="url(#{self.p}-soft)"></ellipse>')
            out.append(self.form(knee, ankle, w * 0.78, w * 0.5, ms, sb, ao_start=True))
        if wrap is not None:
            wm = wrap.back() if back else wrap
            t0 = 0.4
            a0 = (knee[0] + (ankle[0] - knee[0]) * t0, knee[1] + (ankle[1] - knee[1]) * t0)
            out.append(self.form(a0, ankle, w * 0.78, w * 0.62, wm, ao_start=True))
            out.append(self.bands(a0, ankle, w * 0.78, w * 0.62, wm, n=5))
        out.append(self.foot(ankle, w * 0.8, boot or MATS["leather_dark"], foot_dir, back))
        return "".join(out)

    # --- continuous limbs (v2) ------------------------------------------------
    @staticmethod
    def _catmull(pts, n_per=8):
        """Sample a Catmull-Rom spline through pts; returns [(x, y, t_global)]."""
        q = [pts[0]] + list(pts) + [pts[-1]]
        out = []
        segs = len(pts) - 1
        for i in range(segs):
            p0, p1, p2, p3 = q[i], q[i + 1], q[i + 2], q[i + 3]
            for k in range(n_per + (1 if i == segs - 1 else 0)):
                t = k / n_per
                t2, t3 = t * t, t * t * t
                x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                           + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
                y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                           + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
                out.append((x, y, (i + t) / segs))
        return out

    def tube(self, pts, widths, m, bumps=(), outline=True, n_per=8, chunks_per_seg=4, crease_at=()):
        """One continuous shaded tube through joint points (shoulder->elbow->wrist etc.).

        widths: half-width at each joint point; bumps: [(t, amount, side)] with t in 0..1 over the whole tube,
        side +1 lit / -1 shadow / 0 both. Shading is stacked round-capped strokes offset toward the light, so
        bends at elbows and knees stay smooth instead of breaking into separate capsules.
        crease_at: joint indices that get a soft crease on the inside of the bend."""
        self._filters()
        S = self._catmull(pts, n_per)
        nseg = len(pts) - 1

        def width(t):
            u = t * nseg
            i = min(int(u), nseg - 1)
            fr = u - i
            w = widths[i] + (widths[i + 1] - widths[i]) * fr
            lit = shd = 0.0
            for (bt, amt, side) in bumps:
                k = math.exp(-((t - bt) / 0.12) ** 2) * amt
                if side >= 0:
                    lit += k
                if side <= 0:
                    shd += k
            return w, lit, shd

        samp = []
        for j, (x, y, t) in enumerate(S):
            a = S[max(0, j - 1)]
            b = S[min(len(S) - 1, j + 1)]
            dx, dy = b[0] - a[0], b[1] - a[1]
            L = math.hypot(dx, dy) or 1.0
            nx, ny = -dy / L, dx / L
            if nx * LIGHT[0] + ny * LIGHT[1] < 0:
                nx, ny = -nx, -ny
            w, lit, shd = width(t)
            # asymmetric bulge: shift the centre toward the bulging side and widen
            cx, cy = x + nx * (lit - shd) * 0.5, y + ny * (lit - shd) * 0.5
            samp.append((cx, cy, nx, ny, w + (lit + shd) * 0.5))

        n_chunks = max(2, nseg * chunks_per_seg)
        per = max(1, (len(samp) - 1) // n_chunks)
        groups = []
        i = 0
        while i < len(samp) - 1:
            groups.append(samp[i:min(len(samp), i + per + 1)])
            i += per

        sp = mix(m.light, m.spec, 0.25 + 0.55 * m.gloss)
        # (width fraction, offset toward light as fraction of w, colour, opacity, blurred)
        layers = [(1.0, 0.0, mix(m.core, m.bounce, 0.3), 1, False),
                  (0.16, -0.8, m.bounce, 0.75, False),
                  (0.9, 0.1, m.core, 1, True),
                  (0.74, 0.2, m.core, 1, True),
                  (0.6, 0.3, m.base, 1, True),
                  (0.42, 0.42, m.light, 1, True),
                  (0.2, 0.55, sp, m.spec_op + 0.25, True)]
        out = []
        if outline:
            for gp in groups:
                wv = sum(s[4] for s in gp) / len(gp)
                d = "M" + " L".join(P((s[0], s[1])) for s in gp)
                out.append(f'<path d="{d}" fill="none" stroke="{m.outline}" stroke-opacity="0.8" stroke-width="{f(wv * 2 + 1.5)}" '
                           f'stroke-linecap="round" stroke-linejoin="round"></path>')
        inner = []
        for (wf, off, col, op, blur) in layers:
            part = []
            for gp in groups:
                wv = sum(s[4] for s in gp) / len(gp)
                d = "M" + " L".join(P((s[0] + s[2] * s[4] * off, s[1] + s[3] * s[4] * off)) for s in gp)
                part.append(f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{f(max(0.4, wv * 2 * wf))}" '
                            f'stroke-linecap="round" stroke-linejoin="round"></path>')
            g = "".join(part)
            o = f' opacity="{f(min(1, op))}"' if op < 1 else ""
            if blur:
                inner.append(f'<g{o} filter="url(#{self.p}-soft)">{g}</g>')
            else:
                out.append(f'<g{o}>{g}</g>')
        out.extend(inner)
        for ji in crease_at:
            if 0 < ji < len(pts) - 1:
                a, b, c = pts[ji - 1], pts[ji], pts[ji + 1]
                v1 = (a[0] - b[0], a[1] - b[1])
                v2 = (c[0] - b[0], c[1] - b[1])
                l1, l2 = math.hypot(*v1) or 1, math.hypot(*v2) or 1
                bis = (v1[0] / l1 + v2[0] / l2, v1[1] / l1 + v2[1] / l2)
                bl = math.hypot(*bis)
                if bl < 0.15:
                    continue
                bis = (bis[0] / bl, bis[1] / bl)
                w = widths[ji]
                p0 = (b[0] + bis[0] * w * 0.85, b[1] + bis[1] * w * 0.85)
                tx, ty = -bis[1], bis[0]
                out.append(f'<path d="M{P((p0[0] - tx * w * 0.5, p0[1] - ty * w * 0.5))} Q{P((p0[0] - bis[0] * w * 0.25, p0[1] - bis[1] * w * 0.25))} '
                           f'{P((p0[0] + tx * w * 0.5, p0[1] + ty * w * 0.5))}" fill="none" stroke="{m.core}" stroke-width="{f(max(0.6, w * 0.16))}" '
                           f'stroke-opacity="0.7" stroke-linecap="round" filter="url(#{self.p}-soft)"></path>')
        return "".join(out)

    def arm(self, shoulder, elbow, wrist, m_upper, m_fore=None, w=6.0, muscular=0.0, sleeve=None, cuff=None,
            hand="fist", hand_dir=None, hand_m=None, back=False):
        """Continuous arm: one tube shoulder->elbow->wrist. If m_fore differs from m_upper (rolled sleeve,
        bare forearm), the forearm material runs the whole length and the upper material is a sleeve over it."""
        mu = m_upper.back() if back else m_upper
        mf = (m_fore or m_upper)
        mf = mf.back() if back else mf
        hm = (hand_m or MATS["skin"])
        hm = hm.back() if back else hm
        mus = muscular
        bumps = [(0.08, w * (0.18 + 0.3 * mus), 1),   # deltoid cap
                 (0.27, w * (0.12 + 0.38 * mus), 1),  # biceps
                 (0.3, w * (0.05 + 0.22 * mus), -1),  # triceps
                 (0.62, w * (0.12 + 0.3 * mus), 0)]   # forearm belly
        widths = [w, w * 0.74, w * 0.56]
        out = []
        if sleeve is not None:
            sm = sleeve.back() if back else sleeve
            sw = [w * 1.12, w * 0.95, w * 0.82]
            out.append(self.tube([shoulder, elbow, wrist], sw, sm, [(0.3, w * 0.15, 0), (0.62, w * 0.12, 0)], crease_at=(1,)))
            out.append(self.folds(shoulder, elbow, sw[0], sw[1], sm, n=2, start=0.2))
            out.append(self.folds(elbow, wrist, sw[1], sw[2], sm, n=3, start=0.0))
        elif m_fore is not None and m_fore is not m_upper:
            out.append(self.tube([shoulder, elbow, wrist], widths, mf, bumps, crease_at=(1,)))
            mid = (elbow[0] + (wrist[0] - elbow[0]) * 0.12, elbow[1] + (wrist[1] - elbow[1]) * 0.12)
            out.append(self.tube([shoulder, elbow, mid], [w * 1.12, w * 0.92, w * 0.9], mu, bumps[:2], crease_at=(1,)))
            out.append(self.folds(shoulder, elbow, w * 1.1, w * 0.9, mu, n=2, start=0.25))
        else:
            out.append(self.tube([shoulder, elbow, wrist], widths, mu, bumps, crease_at=(1,)))
        if cuff is not None:
            cm = cuff.back() if back else cuff
            ax = (wrist[0] - elbow[0], wrist[1] - elbow[1])
            L = math.hypot(*ax) or 1
            c0 = (wrist[0] - ax[0] / L * w * 1.1, wrist[1] - ax[1] / L * w * 1.1)
            c1 = (wrist[0] - ax[0] / L * w * 0.15, wrist[1] - ax[1] / L * w * 0.15)
            cw = w * (0.86 if sleeve is not None else 0.62)
            out.append(self.tube([c0, c1], [cw, cw * 0.97], cm))
        if hand:
            hd = hand_dir or (wrist[0] - elbow[0], wrist[1] - elbow[1])
            out.append(self.hand(wrist, hd, w * 0.8, hm, kind=hand))
        return "".join(out)

    def leg(self, hip, knee, ankle, m_thigh, m_shin=None, w=7.0, muscular=0.0, trouser=None, wrap=None,
            boot=None, foot_dir=1, back=False):
        """Continuous leg: one tube hip->knee->ankle (+ foot). trouser = baggy 바지 with folds; wrap = 행전."""
        mt = m_thigh.back() if back else m_thigh
        ms = (m_shin or m_thigh)
        ms = ms.back() if back else ms
        mus = muscular
        out = []
        if trouser is not None:
            tm = trouser.back() if back else trouser
            tw = [w * 1.3, w * 1.08, w * 0.82]
            out.append(self.tube([hip, knee, ankle], tw, tm, [(0.25, w * 0.22, 0), (0.62, w * 0.2, 0)], crease_at=(1,)))
            out.append(self.folds(hip, knee, tw[0], tw[1], tm, n=2, start=0.25))
            out.append(self.folds(knee, ankle, tw[1], tw[2], tm, n=3, start=0.05))
        else:
            bumps = [(0.18, w * (0.16 + 0.32 * mus), 0),   # quads / hamstring
                     (0.47, w * 0.08, 1),                  # kneecap
                     (0.66, w * (0.14 + 0.36 * mus), -1)]  # calf
            src = ms if (m_shin is not None and m_shin is not m_thigh) else mt
            out.append(self.tube([hip, knee, ankle], [w, w * 0.68, w * 0.46], src, bumps, crease_at=(1,)))
            if src is not mt:
                mid = (knee[0] + (ankle[0] - knee[0]) * 0.1, knee[1] + (ankle[1] - knee[1]) * 0.1)
                out.append(self.tube([hip, knee, mid], [w * 1.06, w * 0.8, w * 0.76], mt, bumps[:1], crease_at=(1,)))
        if wrap is not None:
            wm = wrap.back() if back else wrap
            # follow the same spline as the leg so the wrap sits on the shin instead of beside it
            S = self._catmull([hip, knee, ankle], 8)
            sub = [(x, y) for (x, y, t) in S if t >= 0.69]
            sub = sub[::2] + ([sub[-1]] if (len(sub) - 1) % 2 else [])
            ww = w * (0.9 if trouser is not None else 0.62)
            n = len(sub)
            out.append(self.tube(sub, [ww * (1 - 0.18 * i / max(1, n - 1)) for i in range(n)], wm, n_per=3, chunks_per_seg=1))
            out.append(self.bands(sub[0], ankle, ww, ww * 0.82, wm, n=5))
        out.append(self.foot(ankle, w * 0.78, boot or MATS["leather_dark"], foot_dir, back))
        return "".join(out)

    def foot(self, ankle, s, m, dirx=1, back=False):
        m = m.back() if back else m
        x, y = ankle
        d = (f"M{f(x - s * 0.7)} {f(y - s * 0.4)} C{f(x - s * 0.8)} {f(y + s * 0.5)} {f(x - s * 0.5)} {f(y + s * 1.0)} "
             f"{f(x + dirx * s * 0.2)} {f(y + s * 1.05)} C{f(x + dirx * s * 1.4)} {f(y + s * 1.1)} {f(x + dirx * s * 1.9)} {f(y + s * 0.9)} "
             f"{f(x + dirx * s * 1.7)} {f(y + s * 0.45)} C{f(x + dirx * s * 1.3)} {f(y + s * 0.15)} {f(x + s * 0.7)} {f(y - s * 0.1)} "
             f"{f(x + s * 0.6)} {f(y - s * 0.5)} Z")
        gid = self._sphere_grad(x + dirx * s * 0.4, y + s * 0.3, s * 1.4, m)
        sole = (f"M{f(x - s * 0.6)} {f(y + s * 0.9)} Q{f(x + dirx * s * 0.6)} {f(y + s * 1.25)} {f(x + dirx * s * 1.85)} {f(y + s * 0.92)}")
        return (f'<path d="{d}" fill="url(#{gid})" stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.75"></path>'
                f'<path d="{sole}" fill="none" stroke="#0A0604" stroke-width="{f(s * 0.22)}" stroke-opacity="0.8" stroke-linecap="round"></path>'
                f'<ellipse cx="{f(x + dirx * s * 1.1)} " cy="{f(y + s * 0.55)}" rx="{f(s * 0.35)}" ry="{f(s * 0.16)}" fill="{m.spec}" '
                f'opacity="{f(m.spec_op * 0.8)}" filter="url(#{self.p}-soft)"></ellipse>')

    def hand(self, wrist, direction, s, m, kind="fist"):
        """kind: fist (closed, knuckles visible), grip (wrapped round a shaft along `direction`), open (spread fingers), point."""
        dx, dy = direction
        L = math.hypot(dx, dy) or 1
        ux, uy = dx / L, dy / L
        nx, ny = -uy, ux
        if nx * LIGHT[0] + ny * LIGHT[1] < 0:
            nx, ny = -nx, -ny
        cx, cy = wrist[0] + ux * s * 0.95, wrist[1] + uy * s * 0.95
        out = []
        if kind == "open":
            out.append(self.ball(cx, cy, s * 0.95, m, ry=s * 0.85))
            for i, ang in enumerate((-38, -14, 8, 30)):
                a = math.radians(ang)
                vx, vy = ux * math.cos(a) - uy * math.sin(a), ux * math.sin(a) + uy * math.cos(a)
                ln = s * (1.15 + (0.25 if i in (1, 2) else 0))
                b0 = (cx + vx * s * 0.7, cy + vy * s * 0.7)
                out.append(self.form(b0, (b0[0] + vx * ln, b0[1] + vy * ln), s * 0.24, s * 0.18, m, ao_start=True, spec=False))
            tb = (cx + nx * s * 0.7, cy + ny * s * 0.7)
            out.append(self.form(tb, (tb[0] + nx * s * 0.6 + ux * s * 0.5, tb[1] + ny * s * 0.6 + uy * s * 0.5), s * 0.28, s * 0.2, m, spec=False))
            return "".join(out)
        # palm / back of hand
        out.append(self.ball(cx, cy, s * 1.0, m, ry=s * 0.9))
        # knuckle row: four small balls across the hand front
        kx, ky = cx + ux * s * 0.7, cy + uy * s * 0.7
        for i in range(4):
            t = (i - 1.5) * 0.42
            out.append(self.ball(kx + nx * s * t, ky + ny * s * t, s * 0.3, m, outline=True))
        # finger creases
        for i in range(3):
            t = (i - 1.0) * 0.42
            px, py = kx + nx * s * t, ky + ny * s * t
            out.append(f'<path d="M{P((px - ux * s * 0.3, py - uy * s * 0.3))} L{P((px + ux * s * 0.25, py + uy * s * 0.25))}" '
                       f'stroke="{m.core}" stroke-width="0.6" stroke-opacity="0.8"></path>')
        # thumb wrapping over
        tb = (cx + nx * s * 0.75 - ux * s * 0.1, cy + ny * s * 0.75 - uy * s * 0.1)
        te = (cx + nx * s * 0.25 + ux * s * 0.85, cy + ny * s * 0.25 + uy * s * 0.85)
        out.append(self.form(tb, te, s * 0.34, s * 0.24, m, ao_start=False, spec=False))
        out.append(f'<path d="M{P((cx - ux * s * 0.6, cy - uy * s * 0.6))} Q{P((cx, cy))} {P((cx + nx * s * 0.6, cy + ny * s * 0.6))}" '
                   f'fill="none" stroke="{m.light}" stroke-width="0.7" stroke-opacity="0.6"></path>')
        return "".join(out)

    def folds(self, a, b, w1, w2, m, n=3, start=0.12, op=0.85):
        """Cloth folds wrapping a tube: dark crease crescent + light ridge per fold."""
        nrm, ax, L = self._lit_normal(a, b)
        out = []
        for i in range(n):
            t = start + (i + 0.5) * (0.82 - start) / n
            w = w1 + (w2 - w1) * t
            c = (a[0] + ax[0] * L * t, a[1] + ax[1] * L * t)
            sag = w * 0.45
            p0 = (c[0] + nrm[0] * w * 0.92, c[1] + nrm[1] * w * 0.92)
            p1 = (c[0] - nrm[0] * w * 0.92, c[1] - nrm[1] * w * 0.92)
            mid = (c[0] + ax[0] * sag, c[1] + ax[1] * sag)
            ctrl = (2 * mid[0] - (p0[0] + p1[0]) / 2, 2 * mid[1] - (p0[1] + p1[1]) / 2)
            out.append(f'<path d="M{P(p0)} Q{P(ctrl)} {P(p1)}" fill="none" stroke="{m.core}" stroke-width="{f(max(0.8, w * 0.2))}" '
                       f'stroke-opacity="{f(op * 0.7)}" stroke-linecap="round" filter="url(#{self.p}-soft)"></path>')
            sh = w * 0.18
            p0b = (p0[0] - ax[0] * sh, p0[1] - ax[1] * sh)
            ctrlb = (ctrl[0] - ax[0] * sh, ctrl[1] - ax[1] * sh)
            mid_l = ((p0b[0] + ctrlb[0]) / 2, (p0b[1] + ctrlb[1]) / 2)
            out.append(f'<path d="M{P(p0b)} Q{P(mid_l)} {P(((p0b[0] + p1[0]) / 2 - ax[0] * sh * 0.3 + ax[0] * sag * 0.6, (p0b[1] + p1[1]) / 2 - ax[1] * sh * 0.3 + ax[1] * sag * 0.6))}" '
                       f'fill="none" stroke="{m.light}" stroke-width="{f(max(0.6, w * 0.13))}" stroke-opacity="{f(op * 0.6)}" stroke-linecap="round"></path>')
        return "".join(out)

    def bands(self, a, b, w1, w2, m, n=5):
        """Spiral wrap bands (leg wraps, rope grips): alternating crease lines tilted across the tube."""
        nrm, ax, L = self._lit_normal(a, b)
        out = []
        for i in range(n):
            t = (i + 0.6) / (n + 0.4)
            w = w1 + (w2 - w1) * t
            c = (a[0] + ax[0] * L * t, a[1] + ax[1] * L * t)
            p0 = (c[0] + nrm[0] * w - ax[0] * w * 0.25, c[1] + nrm[1] * w - ax[1] * w * 0.25)
            p1 = (c[0] - nrm[0] * w + ax[0] * w * 0.25, c[1] - nrm[1] * w + ax[1] * w * 0.25)
            ctl = (c[0] + ax[0] * w * 0.35, c[1] + ax[1] * w * 0.35)
            out.append(f'<path d="M{P(p0)} Q{P(ctl)} {P(p1)}" fill="none" stroke="{m.core}" stroke-width="0.9" stroke-opacity="0.7"></path>')
            out.append(f'<path d="M{P((p0[0] - ax[0], p0[1] - ax[1]))} Q{P((ctl[0] - ax[0], ctl[1] - ax[1]))} {P((c[0] - ax[0], c[1] - ax[1]))}" '
                       f'fill="none" stroke="{m.light}" stroke-width="0.7" stroke-opacity="0.65"></path>')
        return "".join(out)

    def torso(self, neck, waist, sw, ww, m, chest=0.0, belly=0.0, abs_=False, outline=True):
        """Barrel-shaped torso from neck base to waist; sw/ww = half widths. Returns (svg, path_d)."""
        self._filters()
        nx, ny = neck
        wx, wy = waist
        h = wy - ny
        pts = [(nx - sw * 0.35, ny - h * 0.02), (nx - sw * 0.85, ny + h * 0.04), (nx - sw, ny + h * 0.16),
               (nx - sw * (0.98 + chest * 0.05), ny + h * 0.38), ((nx + wx) / 2 - ww - belly * 0.5, ny + h * 0.68),
               (wx - ww - belly * 0.2, wy), (wx + ww + belly * 0.2, wy), ((nx + wx) / 2 + ww + belly * 0.5, ny + h * 0.68),
               (nx + sw * (0.98 + chest * 0.05), ny + h * 0.38), (nx + sw, ny + h * 0.16), (nx + sw * 0.85, ny + h * 0.04),
               (nx + sw * 0.35, ny - h * 0.02)]
        d = self._smooth(pts)
        c = ((nx + wx) / 2, (ny + wy) / 2)
        gid = self._cyl_grad(c, (-1.0, -0.12), max(sw, ww) * 1.1, m)
        o = f' stroke="{m.outline}" stroke-width="0.9" stroke-opacity="0.75" stroke-linejoin="round"' if outline else ""
        out = [f'<path d="{d}" fill="url(#{gid})"{o}></path>']
        # vertical falloff: darker toward waist, AO under the chest
        vid = self.id("v")
        self._defs.append(f'<linearGradient id="{vid}" gradientUnits="userSpaceOnUse" x1="{f(nx)}" y1="{f(ny)}" x2="{f(wx)}" y2="{f(wy)}">'
                          f'<stop offset="0" stop-color="#FFF4DC" stop-opacity="0.16"></stop><stop offset="0.35" stop-color="#FFF4DC" stop-opacity="0"></stop>'
                          f'<stop offset="0.75" stop-color="#0A0604" stop-opacity="0.12"></stop><stop offset="1" stop-color="#0A0604" stop-opacity="0.45"></stop></linearGradient>')
        out.append(f'<path d="{d}" fill="url(#{vid})"></path>')
        if chest > 0:
            for s in (-1, 1):
                px = nx + s * sw * 0.45
                py = ny + h * 0.3
                out.append(f'<path d="M{f(px - sw * 0.42)} {f(py - h * 0.12)} Q{f(px)} {f(py + h * 0.14)} {f(px + s * sw * 0.0 + sw * 0.42)} {f(py - h * 0.1)}" '
                           f'fill="none" stroke="{m.core}" stroke-width="{f(sw * 0.12)}" stroke-opacity="{f(0.55 * chest)}" stroke-linecap="round" filter="url(#{self.p}-soft)"></path>')
                out.append(f'<ellipse cx="{f(px - sw * 0.08)}" cy="{f(py - h * 0.06)}" rx="{f(sw * 0.3)}" ry="{f(h * 0.08)}" fill="{m.light}" '
                           f'opacity="{f((0.45 if s < 0 else 0.2) * chest)}" filter="url(#{self.p}-soft)"></ellipse>')
        if abs_:
            out.append(f'<path d="M{f(nx)} {f(ny + h * 0.42)} L{f(wx)} {f(wy - h * 0.05)}" stroke="{m.core}" stroke-width="1.1" stroke-opacity="0.5"></path>')
            for k in range(3):
                yy = ny + h * (0.52 + k * 0.13)
                for s in (-1, 1):
                    out.append(f'<path d="M{f(nx + s * 1.5)} {f(yy)} q{f(s * ww * 0.35)} {f(h * 0.03)} {f(s * ww * 0.7)} {f(-h * 0.015)}" '
                               f'fill="none" stroke="{m.core}" stroke-width="1" stroke-opacity="0.45"></path>')
                    out.append(f'<ellipse cx="{f(nx + s * ww * 0.35)}" cy="{f(yy - h * 0.04)}" rx="{f(ww * 0.28)}" ry="{f(h * 0.035)}" '
                               f'fill="{m.light}" opacity="{0.28 if s < 0 else 0.12}" filter="url(#{self.p}-soft)"></ellipse>')
        if belly > 0:
            out.append(f'<ellipse cx="{f(wx - ww * 0.2)}" cy="{f(wy - h * 0.22)}" rx="{f(ww * 0.6)}" ry="{f(h * 0.18)}" fill="{m.light}" '
                       f'opacity="0.25" filter="url(#{self.p}-soft)"></ellipse>')
        return "".join(out), d

    def neck(self, base, top, w, m):
        return self.form(base, top, w, w * 0.9, m, ao_start=False, ao_end=True)

    def head(self, cx, cy, r, m, face=True, jaw=1.0, ear=True, eye_col="#1A100A", brow="#24160C", mouth="#6A2A1C", turn=0.0):
        """Shaded head: skull sphere + jaw, ear, eye sockets, nose bridge, cheek planes, chin shadow.
        turn in [-1,1] shifts features toward screen-left (-) or right (+)."""
        out = []
        if ear:
            ex = cx - r * (0.92 - turn * 0.3)
            out.append(self.ball(ex, cy + r * 0.12, r * 0.24, m, ry=r * 0.34))
            out.append(f'<ellipse cx="{f(ex + r * 0.04)}" cy="{f(cy + r * 0.12)}" rx="{f(r * 0.1)}" ry="{f(r * 0.18)}" fill="{m.core}" opacity="0.6"></ellipse>')
        # skull + jaw as one smooth shape
        pts = [(cx, cy - r), (cx + r * 0.78, cy - r * 0.72), (cx + r * 0.96, cy - r * 0.05), (cx + r * 0.72 * jaw, cy + r * 0.62),
               (cx + r * 0.3 * jaw + turn * r * 0.15, cy + r * 1.02), (cx - r * 0.22 * jaw + turn * r * 0.15, cy + r * 1.0),
               (cx - r * 0.8 * jaw, cy + r * 0.55), (cx - r * 0.98, cy - r * 0.08), (cx - r * 0.8, cy - r * 0.72)]
        d = self._smooth(pts)
        gid = self._sphere_grad(cx, cy, r * 1.05, m)
        out.append(f'<path d="{d}" fill="url(#{gid})" stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.75"></path>')
        if not face:
            return "".join(out), d
        fx = cx + turn * r * 0.28
        ey = cy - r * 0.02
        # eye sockets (AO), cheek light planes
        for s in (-1, 1):
            out.append(f'<ellipse cx="{f(fx + s * r * 0.36)}" cy="{f(ey)}" rx="{f(r * 0.26)}" ry="{f(r * 0.16)}" fill="{m.core}" opacity="0.4" filter="url(#{self.p}-soft)"></ellipse>')
        out.append(f'<ellipse cx="{f(fx - r * 0.42)}" cy="{f(cy + r * 0.38)}" rx="{f(r * 0.24)}" ry="{f(r * 0.16)}" fill="{m.light}" opacity="0.45" filter="url(#{self.p}-soft)"></ellipse>')
        out.append(f'<ellipse cx="{f(fx + r * 0.5)}" cy="{f(cy + r * 0.42)}" rx="{f(r * 0.22)}" ry="{f(r * 0.3)}" fill="{m.core}" opacity="0.3" filter="url(#{self.p}-soft)"></ellipse>')
        # brows
        for s in (-1, 1):
            out.append(f'<path d="M{f(fx + s * r * 0.14)} {f(ey - r * 0.18)} Q{f(fx + s * r * 0.36)} {f(ey - r * 0.3)} {f(fx + s * r * 0.58)} {f(ey - r * 0.16)}" '
                       f'fill="none" stroke="{brow}" stroke-width="{f(r * 0.1)}" stroke-linecap="round"></path>')
        # eyes: lid line + iris + catchlight
        for s in (-1, 1):
            ex = fx + s * r * 0.36
            out.append(f'<path d="M{f(ex - r * 0.15)} {f(ey + r * 0.02)} Q{f(ex)} {f(ey - r * 0.08)} {f(ex + r * 0.15)} {f(ey + r * 0.02)}" '
                       f'fill="#F2E8D8" stroke="{eye_col}" stroke-width="{f(r * 0.05)}"></path>')
            out.append(f'<circle cx="{f(ex + turn * r * 0.04)}" cy="{f(ey - r * 0.01)}" r="{f(r * 0.06)}" fill="{eye_col}"></circle>')
            out.append(f'<circle cx="{f(ex - r * 0.02)}" cy="{f(ey - r * 0.03)}" r="{f(r * 0.022)}" fill="#FFFFFF" opacity="0.9"></circle>')
        # nose: bridge highlight, side shadow, nostril
        out.append(f'<path d="M{f(fx - r * 0.02)} {f(ey + r * 0.02)} L{f(fx - r * 0.06)} {f(cy + r * 0.38)}" stroke="{m.light}" stroke-width="{f(r * 0.07)}" stroke-opacity="0.75" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(fx + r * 0.06)} {f(ey + r * 0.06)} Q{f(fx + r * 0.16)} {f(cy + r * 0.32)} {f(fx + r * 0.04)} {f(cy + r * 0.42)} L{f(fx - r * 0.1)} {f(cy + r * 0.42)}" '
                   f'fill="none" stroke="{m.core}" stroke-width="{f(r * 0.06)}" stroke-opacity="0.7" stroke-linecap="round"></path>')
        # mouth + lower lip light + chin shadow
        out.append(f'<path d="M{f(fx - r * 0.2)} {f(cy + r * 0.62)} Q{f(fx)} {f(cy + r * 0.66)} {f(fx + r * 0.2)} {f(cy + r * 0.6)}" fill="none" stroke="{mouth}" stroke-width="{f(r * 0.06)}" stroke-linecap="round"></path>')
        out.append(f'<ellipse cx="{f(fx - r * 0.02)}" cy="{f(cy + r * 0.72)}" rx="{f(r * 0.12)}" ry="{f(r * 0.04)}" fill="{m.light}" opacity="0.5"></ellipse>')
        out.append(f'<ellipse cx="{f(fx + r * 0.05)}" cy="{f(cy + r * 0.86)}" rx="{f(r * 0.18)}" ry="{f(r * 0.06)}" fill="{m.core}" opacity="0.35" filter="url(#{self.p}-soft)"></ellipse>')
        return "".join(out), d

    # --- individual faces (v2) -----------------------------------------------
    FACE_SHAPES = {  # cheek width, jaw width, chin width, chin depth, forehead width (fractions of r)
        "oval": (0.95, 0.74, 0.28, 1.02, 0.86),
        "long": (0.86, 0.68, 0.26, 1.16, 0.8),
        "square": (0.97, 0.9, 0.44, 1.0, 0.9),
        "round": (1.0, 0.86, 0.36, 0.95, 0.9),
        "heart": (0.95, 0.6, 0.2, 1.04, 0.92),
        "diamond": (1.0, 0.66, 0.24, 1.06, 0.74),
    }

    def face(self, cx, cy, r, m, look=None, ear=True):
        """A distinct, individually designed face. look keys (all optional):
        shape: oval|long|square|round|heart|diamond   jaw: width mult   cheek: cheekbone prominence 0..1
        turn: -1..1 (3/4 view toward screen right +)   tilt: head tilt degrees (applied by caller's transform)
        eye: almond|narrow|round|droopy|sharp   eye_size: mult   lid: mono|double   gaze: (dx, dy) -1..1
        brow: straight|arched|angry|worried|thick|thin   brow_col
        nose: straight|broad|hooked|small|flat   nose_len: mult
        mouth: neutral|smile|stern|shout|smirk|grin|calm   lip: fullness 0..1
        age: 0..1 (wrinkles, eye bags, nasolabial folds)   fem: bool (softer jaw, lashes, lip colour, blush)
        beard: None|stubble|goatee|full|long|mustache|thin_long   beard_col
        scar: None|'cheek'|'brow'   eye_col, mouth_col
        Returns (svg, outline_path_d)."""
        L = dict(look or {})
        shape = L.get("shape", "oval")
        cw, jw, chw, chd, fw = self.FACE_SHAPES.get(shape, self.FACE_SHAPES["oval"])
        jw *= L.get("jaw", 1.0)
        fem = L.get("fem", False)
        if fem:
            jw *= 0.9
            chw *= 0.85
            chd *= 0.97
        turn = L.get("turn", 0.0)
        age = L.get("age", 0.25)
        cheek = L.get("cheek", 0.4)
        sh = turn * r * 0.16  # silhouette shift of the lower face in 3/4 view
        out = []
        if ear:
            ex = cx - r * (0.94 - max(0, turn) * 0.25) if turn >= -0.2 else cx + r * 0.94
            out.append(self.ball(ex, cy + r * 0.1, r * 0.2, m, ry=r * 0.32))
            out.append(f'<path d="M{f(ex - r * 0.04)} {f(cy - r * 0.08)} q{f(r * 0.12)} {f(r * 0.16)} 0 {f(r * 0.34)}" fill="none" '
                       f'stroke="{m.core}" stroke-width="{f(r * 0.06)}" stroke-opacity="0.7"></path>')
        pts = [(cx, cy - r * 1.02),
               (cx + r * fw * 0.86, cy - r * 0.78), (cx + r * fw, cy - r * 0.3),
               (cx + r * cw + sh * 0.3, cy + r * 0.12),
               (cx + r * jw + sh * 0.6, cy + r * 0.62),
               (cx + r * chw + sh, cy + r * chd * 0.94),
               (cx + sh, cy + r * chd),
               (cx - r * chw + sh, cy + r * chd * 0.94),
               (cx - r * jw + sh * 0.6, cy + r * 0.62),
               (cx - r * cw + sh * 0.3, cy + r * 0.12),
               (cx - r * fw, cy - r * 0.3), (cx - r * fw * 0.86, cy - r * 0.78)]
        d = self._smooth(pts)
        gid = self._sphere_grad(cx, cy, r * 1.08, m)
        out.append(f'<path d="{d}" fill="url(#{gid})" stroke="{m.outline}" stroke-width="0.8" stroke-opacity="0.75"></path>')
        # facial planes: shadow side of the face + under-cheekbone hollow + jaw shadow (clipped to the face)
        fx = cx + turn * r * 0.3
        ey = cy - r * 0.04
        planes = [
            f'<ellipse cx="{f(cx + r * 0.78)}" cy="{f(cy + r * 0.2)}" rx="{f(r * 0.42)}" ry="{f(r * 1.0)}" fill="{m.core}" opacity="0.38" filter="url(#{self.p}-ao)"></ellipse>',
            f'<ellipse cx="{f(fx + r * 0.52)}" cy="{f(cy + r * 0.44)}" rx="{f(r * 0.2)}" ry="{f(r * 0.16)}" fill="{m.core}" opacity="{f(0.2 + 0.35 * cheek)}" filter="url(#{self.p}-soft)"></ellipse>',
            f'<ellipse cx="{f(fx - r * 0.5)}" cy="{f(cy + r * 0.44)}" rx="{f(r * 0.16)}" ry="{f(r * 0.12)}" fill="{m.core}" opacity="{f(0.1 + 0.2 * cheek)}" filter="url(#{self.p}-soft)"></ellipse>',
            f'<ellipse cx="{f(fx - r * 0.44)}" cy="{f(cy + r * 0.24)}" rx="{f(r * 0.24)}" ry="{f(r * 0.13)}" fill="{m.light}" opacity="{f(0.3 + 0.35 * cheek)}" filter="url(#{self.p}-soft)"></ellipse>',
            f'<ellipse cx="{f(cx + sh)}" cy="{f(cy + r * chd * 0.98)}" rx="{f(r * chw * 1.6)}" ry="{f(r * 0.1)}" fill="{m.core}" opacity="0.3" filter="url(#{self.p}-soft)"></ellipse>',
            f'<ellipse cx="{f(fx - r * 0.1)}" cy="{f(cy - r * 0.62)}" rx="{f(r * 0.4)}" ry="{f(r * 0.18)}" fill="{m.light}" opacity="0.35" filter="url(#{self.p}-soft)"></ellipse>',
        ]
        if fem or L.get("blush"):
            planes.append(f'<ellipse cx="{f(fx - r * 0.42)}" cy="{f(cy + r * 0.34)}" rx="{f(r * 0.2)}" ry="{f(r * 0.1)}" fill="#E0605A" opacity="0.28" filter="url(#{self.p}-soft)"></ellipse>')
            planes.append(f'<ellipse cx="{f(fx + r * 0.42)}" cy="{f(cy + r * 0.34)}" rx="{f(r * 0.16)}" ry="{f(r * 0.09)}" fill="#C04848" opacity="0.2" filter="url(#{self.p}-soft)"></ellipse>')
        out.append(self.clip_overlay(d, "".join(planes)))
        out.append(self._eyes(fx, ey, r, m, L, turn, age))
        out.append(self._nose(fx, cy, r, m, L))
        out.append(self._mouth(fx, cy, r, m, L, fem))
        if age > 0.2:
            out.append(self._age_lines(fx, cy, ey, r, m, age, d))
        sc = L.get("scar")
        if sc:
            sx, sy = (fx + r * 0.42, cy + r * 0.1) if sc == "cheek" else (fx - r * 0.38, ey - r * 0.32)
            out.append(f'<path d="M{f(sx - r * 0.1)} {f(sy - r * 0.22)} L{f(sx + r * 0.08)} {f(sy + r * 0.24)}" stroke="#E8B0A0" stroke-width="{f(r * 0.06)}" '
                       f'stroke-linecap="round" opacity="0.85"></path>'
                       f'<path d="M{f(sx - r * 0.08)} {f(sy - r * 0.2)} L{f(sx + r * 0.1)} {f(sy + r * 0.26)}" stroke="#6A2A20" stroke-width="{f(r * 0.025)}" opacity="0.7"></path>')
        bd = L.get("beard")
        if bd:
            out.append(self._beard(fx, cx, cy, r, L, bd, jw, chw, chd, sh))
        return "".join(out), d

    def _eyes(self, fx, ey, r, m, L, turn, age):
        kind = L.get("eye", "almond")
        es = L.get("eye_size", 1.0)
        col = L.get("eye_col", "#20140C")
        gx, gy = L.get("gaze", (0.0, 0.0))
        # (half width, upper lid height, lower lid depth, outer-corner tilt)
        spec = {"almond": (0.15, 0.085, 0.045, 0.02), "narrow": (0.15, 0.045, 0.025, 0.03),
                "round": (0.14, 0.11, 0.07, 0.0), "droopy": (0.15, 0.075, 0.04, -0.04),
                "sharp": (0.16, 0.06, 0.03, 0.06)}[kind]
        hw, uh, ld, tilt = [v * es if i < 3 else v for i, v in enumerate(spec)]
        out = []
        for s in (-1, 1):
            far = (s > 0 and turn > 0) or (s < 0 and turn < 0)
            k = 1 - 0.3 * abs(turn) if far else 1.0
            ex = fx + s * r * 0.36 * (1 - 0.15 * abs(turn) * (1 if far else -0.5))
            w = hw * r * k
            ix, ox = ex - s * w, ex + s * w  # inner / outer corners
            oy = ey - tilt * r
            up = (f"M{f(ix)} {f(ey)} C{f(ix + s * w * 0.4)} {f(ey - uh * r * 1.3)} {f(ox - s * w * 0.35)} {f(oy - uh * r * 1.2)} {f(ox)} {f(oy)}")
            lo = (f" C{f(ox - s * w * 0.4)} {f(oy + ld * r * 1.3)} {f(ix + s * w * 0.4)} {f(ey + ld * r * 1.3)} {f(ix)} {f(ey)} Z")
            cid = self.id("e")
            iris_r = r * 0.075 * es * (0.9 if kind == "narrow" else 1)
            icx = ex + gx * w * 0.35
            icy = ey - uh * r * 0.25 + gy * uh * r * 0.3
            out.append(f'<clipPath id="{cid}"><path d="{up}{lo}"></path></clipPath>')
            out.append(f'<path d="{up}{lo}" fill="#EDE2D0"></path>')
            out.append(f'<g clip-path="url(#{cid})"><circle cx="{f(icx)}" cy="{f(icy)}" r="{f(iris_r)}" fill="#4A2E1C"></circle>'
                       f'<circle cx="{f(icx)}" cy="{f(icy)}" r="{f(iris_r * 0.55)}" fill="{col}"></circle>'
                       f'<circle cx="{f(icx - iris_r * 0.35)}" cy="{f(icy - iris_r * 0.4)}" r="{f(iris_r * 0.28)}" fill="#FFFFFF" opacity="0.9"></circle>'
                       f'<rect x="{f(min(ix, ox) - 2)}" y="{f(ey - uh * r * 2)}" width="{f(w * 2 + 4)}" height="{f(uh * r * 1.25)}" fill="{m.core}" opacity="0.35"></rect></g>')
            # upper lid line (thicker toward the outer corner) + lashes
            lw = r * (0.05 if not L.get("fem") else 0.065)
            out.append(f'<path d="{up}" fill="none" stroke="#1A0F08" stroke-width="{f(lw)}" stroke-linecap="round"></path>')
            if L.get("fem"):
                out.append(f'<path d="M{f(ox)} {f(oy)} l{f(s * r * 0.06)} {f(-r * 0.05)}" stroke="#1A0F08" stroke-width="{f(lw * 0.7)}" stroke-linecap="round"></path>')
            if L.get("lid", "mono") == "double" or kind == "round":
                out.append(f'<path d="M{f(ix + s * w * 0.3)} {f(ey - uh * r * 1.3)} Q{f(ex)} {f(ey - uh * r * 2.1)} {f(ox - s * w * 0.1)} {f(oy - uh * r * 1.25)}" '
                           f'fill="none" stroke="{m.core}" stroke-width="{f(r * 0.025)}" stroke-opacity="0.8"></path>')
            # lower lid
            out.append(f'<path d="M{f(ox)} {f(oy)}{lo[:lo.index("Z")]}" fill="none" stroke="{m.core}" stroke-width="{f(r * 0.022)}" stroke-opacity="0.6"></path>')
            if age > 0.35:
                out.append(f'<path d="M{f(ix + s * w * 0.2)} {f(ey + ld * r * 2.2)} Q{f(ex)} {f(ey + ld * r * 3.6)} {f(ox)} {f(oy + ld * r * 2.0)}" fill="none" '
                           f'stroke="{m.core}" stroke-width="{f(r * 0.02)}" stroke-opacity="{f(0.3 + 0.4 * age)}"></path>')
        out.append(self._brows(fx, ey, r, L, turn))
        return "".join(out)

    def _brows(self, fx, ey, r, L, turn):
        kind = L.get("brow", "straight")
        col = L.get("brow_col", "#1E140C")
        th = {"thick": 0.11, "thin": 0.045}.get(kind, 0.075) * r
        out = []
        for s in (-1, 1):
            far = (s > 0 and turn > 0) or (s < 0 and turn < 0)
            k = 1 - 0.3 * abs(turn) if far else 1.0
            bx = fx + s * r * 0.36
            iy = ey - r * 0.2
            inner = (bx - s * r * 0.2 * k, iy + {"angry": 0.06, "worried": -0.07}.get(kind, 0.0) * r)
            outer = (bx + s * r * 0.22 * k, iy + {"arched": 0.02, "angry": -0.06, "worried": 0.04, "thick": 0.0}.get(kind, 0.01) * r)
            peak = (bx + s * r * 0.06, iy - {"arched": 0.09, "angry": 0.02, "worried": 0.02}.get(kind, 0.045) * r)
            d = (f"M{P(inner)} Q{P(peak)} {P(outer)} Q{P((peak[0], peak[1] + th * 0.9))} {P((inner[0], inner[1] + th))} Z")
            out.append(f'<path d="{d}" fill="{col}" stroke="{col}" stroke-width="{f(r * 0.015)}" stroke-linejoin="round"></path>')
            out.append(f'<path d="M{P(inner)} Q{P(peak)} {P(outer)}" fill="none" stroke="#000" stroke-width="{f(r * 0.02)}" stroke-opacity="0.4"></path>')
        return "".join(out)

    def _nose(self, fx, cy, r, m, L):
        kind = L.get("nose", "straight")
        nl = L.get("nose_len", 1.0)
        nw = {"broad": 1.35, "small": 0.8, "flat": 1.25, "hooked": 1.0}.get(kind, 1.0)
        ty = cy + r * 0.36 * nl
        out = []
        # bridge: lit left plane + shadow right plane
        hook = r * 0.05 if kind == "hooked" else 0
        out.append(f'<path d="M{f(fx - r * 0.02)} {f(cy - r * 0.08)} Q{f(fx - r * 0.06 - hook)} {f(cy + r * 0.14)} {f(fx - r * 0.06)} {f(ty - r * 0.04)}" '
                   f'fill="none" stroke="{m.light}" stroke-width="{f(r * 0.07)}" stroke-opacity="0.8" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(fx + r * 0.07)} {f(cy - r * 0.02)} Q{f(fx + r * 0.12 + hook)} {f(cy + r * 0.18)} {f(fx + r * 0.1 * nw)} {f(ty)}" '
                   f'fill="none" stroke="{m.core}" stroke-width="{f(r * 0.08)}" stroke-opacity="0.45" stroke-linecap="round" filter="url(#{self.p}-soft)"></path>')
        # tip ball, alae (nostril wings), nostrils, cast shadow under the nose
        out.append(f'<ellipse cx="{f(fx - r * 0.02)}" cy="{f(ty)}" rx="{f(r * 0.08 * nw)}" ry="{f(r * 0.065)}" fill="{m.light}" opacity="0.55"></ellipse>')
        for s in (-1, 1):
            out.append(f'<path d="M{f(fx + s * r * 0.1 * nw)} {f(ty - r * 0.06)} q{f(s * r * 0.07)} {f(r * 0.05)} {f(s * r * 0.01)} {f(r * 0.1)}" fill="none" '
                       f'stroke="{m.core}" stroke-width="{f(r * 0.035)}" stroke-opacity="0.75" stroke-linecap="round"></path>')
            out.append(f'<ellipse cx="{f(fx + s * r * 0.055 * nw)}" cy="{f(ty + r * 0.055)}" rx="{f(r * 0.03 * nw)}" ry="{f(r * 0.017)}" fill="#2A120A" opacity="0.8"></ellipse>')
        out.append(f'<ellipse cx="{f(fx + r * 0.04)}" cy="{f(ty + r * 0.12)}" rx="{f(r * 0.12 * nw)}" ry="{f(r * 0.035)}" fill="{m.core}" opacity="0.35" filter="url(#{self.p}-soft)"></ellipse>')
        return "".join(out)

    def _mouth(self, fx, cy, r, m, L, fem):
        kind = L.get("mouth", "neutral")
        lip = L.get("lip", 0.4 + (0.3 if fem else 0))
        col = L.get("mouth_col", "#B4544A" if fem else "#7A3A2C")
        my = cy + r * 0.64
        w = r * {"grin": 0.3, "shout": 0.24, "smile": 0.26}.get(kind, 0.22)
        lift = {"smile": -0.06, "grin": -0.08, "stern": 0.05, "smirk": 0.0, "calm": -0.015}.get(kind, 0.0) * r
        out = []
        # philtrum
        out.append(f'<path d="M{f(fx - r * 0.03)} {f(cy + r * 0.48)} L{f(fx - r * 0.02)} {f(my - r * 0.06)}" stroke="{m.core}" stroke-width="{f(r * 0.04)}" stroke-opacity="0.3"></path>')
        lx, rx = fx - w, fx + w
        ly = ry = my + lift
        if kind == "smirk":
            ry = my - r * 0.06
        if kind in ("shout", "grin"):
            open_h = r * (0.22 if kind == "shout" else 0.09)
            d = (f"M{f(lx)} {f(ly)} Q{f(fx)} {f(my - r * 0.05)} {f(rx)} {f(ry)} Q{f(fx)} {f(my + open_h * 1.6)} {f(lx)} {f(ly)} Z")
            out.append(f'<path d="{d}" fill="#3A0E08" stroke="#1A0604" stroke-width="{f(r * 0.03)}"></path>')
            out.append(f'<path d="M{f(lx + w * 0.25)} {f(my - r * 0.01)} Q{f(fx)} {f(my + r * 0.03)} {f(rx - w * 0.25)} {f(my - r * 0.01)} L{f(rx - w * 0.3)} {f(my + r * 0.05)} '
                       f'Q{f(fx)} {f(my + r * 0.08)} {f(lx + w * 0.3)} {f(my + r * 0.05)} Z" fill="#EDE4D0"></path>')
            if kind == "shout":
                out.append(f'<ellipse cx="{f(fx)}" cy="{f(my + open_h * 0.9)}" rx="{f(w * 0.45)}" ry="{f(open_h * 0.35)}" fill="#8A2A22" opacity="0.8"></ellipse>')
        else:
            # upper lip (shadowed), parting line, lower lip with highlight
            out.append(f'<path d="M{f(lx)} {f(ly)} Q{f(fx - w * 0.5)} {f(my - r * 0.05 * (0.5 + lip))} {f(fx)} {f(my - r * 0.025)} '
                       f'Q{f(fx + w * 0.5)} {f(my - r * 0.05 * (0.5 + lip))} {f(rx)} {f(ry)} Q{f(fx)} {f(my + r * 0.01)} {f(lx)} {f(ly)} Z" '
                       f'fill="{col}" opacity="{f(0.55 + 0.35 * lip)}"></path>')
            out.append(f'<path d="M{f(lx)} {f(ly)} Q{f(fx)} {f(my + r * 0.015 + lift * 0.4)} {f(rx)} {f(ry)}" fill="none" stroke="#2A0E08" stroke-width="{f(r * 0.035)}" stroke-linecap="round"></path>')
            out.append(f'<path d="M{f(lx + w * 0.25)} {f(my + r * 0.03)} Q{f(fx)} {f(my + r * (0.07 + 0.07 * lip))} {f(rx - w * 0.25)} {f(my + r * 0.03)}" '
                       f'fill="{col}" opacity="{f(0.35 + 0.4 * lip)}"></path>')
            out.append(f'<ellipse cx="{f(fx - w * 0.15)}" cy="{f(my + r * 0.06)}" rx="{f(w * 0.3)}" ry="{f(r * 0.022)}" fill="#FFF0E0" opacity="0.4"></ellipse>')
        # mouth-corner shadows, chin dimple
        for s, yy in ((-1, ly), (1, ry)):
            out.append(f'<circle cx="{f(fx + s * w * 1.05)}" cy="{f(yy)}" r="{f(r * 0.03)}" fill="{m.core}" opacity="0.5"></circle>')
        out.append(f'<ellipse cx="{f(fx)}" cy="{f(my + r * 0.18)}" rx="{f(r * 0.12)}" ry="{f(r * 0.05)}" fill="{m.core}" opacity="0.25" filter="url(#{self.p}-soft)"></ellipse>')
        return "".join(out)

    def _age_lines(self, fx, cy, ey, r, m, age, d):
        a = min(1.0, age)
        o = []
        for s in (-1, 1):  # nasolabial folds
            o.append(f'<path d="M{f(fx + s * r * 0.16)} {f(cy + r * 0.34)} Q{f(fx + s * r * 0.3)} {f(cy + r * 0.52)} {f(fx + s * r * 0.3)} {f(cy + r * 0.72)}" '
                     f'fill="none" stroke="{m.core}" stroke-width="{f(r * 0.035)}" stroke-opacity="{f(0.25 + 0.45 * a)}" stroke-linecap="round"></path>')
        if a > 0.45:
            for k in range(2 + int(a * 2)):  # forehead
                yy = cy - r * (0.42 + k * 0.1)
                o.append(f'<path d="M{f(fx - r * 0.34)} {f(yy)} Q{f(fx)} {f(yy - r * 0.04)} {f(fx + r * 0.34)} {f(yy)}" fill="none" stroke="{m.core}" '
                         f'stroke-width="{f(r * 0.02)}" stroke-opacity="{f(0.2 + 0.3 * a)}"></path>')
            for s in (-1, 1):  # crow's feet
                ox = fx + s * r * 0.56
                for k in (-1, 0, 1):
                    o.append(f'<path d="M{f(ox)} {f(ey + k * r * 0.04)} l{f(s * r * 0.1)} {f(k * r * 0.05)}" stroke="{m.core}" stroke-width="{f(r * 0.018)}" stroke-opacity="{f(0.3 * a)}"></path>')
        return self.clip_overlay(d, "".join(o))

    def _beard(self, fx, cx, cy, r, L, kind, jw, chw, chd, sh):
        col = L.get("beard_col", "#1A120C")
        hi = shade(col, 0.35)
        my = cy + r * 0.64
        out = []

        def strands(x0, y0, x1, y1, n, ln, seed):
            s = []
            for i in range(n):
                t = i / max(1, n - 1)
                x = x0 + (x1 - x0) * t
                y = y0 + (y1 - y0) * t
                l = ln * (0.7 + 0.3 * math.sin(i * 2.3 + seed))
                s.append(f'<path d="M{f(x)} {f(y)} q{f(math.sin(i * 1.7 + seed) * r * 0.05)} {f(l * 0.6)} {f(math.sin(i + seed) * r * 0.06)} {f(l)}" '
                         f'fill="none" stroke="{hi if i % 3 == 0 else col}" stroke-width="{f(r * 0.03)}" stroke-linecap="round"></path>')
            return "".join(s)

        if kind in ("mustache", "goatee", "full", "long", "thin_long"):
            for s in (-1, 1):  # mustache
                out.append(f'<path d="M{f(fx + s * r * 0.02)} {f(my - r * 0.1)} Q{f(fx + s * r * 0.2)} {f(my - r * 0.14)} {f(fx + s * r * 0.34)} {f(my + r * 0.05)} '
                           f'Q{f(fx + s * r * 0.18)} {f(my - r * 0.04)} {f(fx + s * r * 0.02)} {f(my - r * 0.04)} Z" fill="{col}"></path>')
            out.append(f'<path d="M{f(fx - r * 0.2)} {f(my - r * 0.1)} q{f(r * 0.08)} {f(-r * 0.03)} {f(r * 0.16)} 0" stroke="{hi}" stroke-width="{f(r * 0.025)}" fill="none" opacity="0.7"></path>')
        if kind == "goatee":
            out.append(f'<path d="M{f(fx - r * 0.12)} {f(my + r * 0.12)} Q{f(fx)} {f(my + r * 0.1)} {f(fx + r * 0.12)} {f(my + r * 0.12)} '
                       f'L{f(fx + r * 0.06)} {f(my + r * 0.52)} Q{f(fx)} {f(my + r * 0.6)} {f(fx - r * 0.06)} {f(my + r * 0.52)} Z" fill="{col}"></path>')
            out.append(strands(fx - r * 0.08, my + r * 0.2, fx + r * 0.08, my + r * 0.2, 5, r * 0.3, 1))
        if kind == "thin_long":
            out.append(f'<path d="M{f(fx - r * 0.08)} {f(my + r * 0.14)} Q{f(fx)} {f(my + r * 0.1)} {f(fx + r * 0.08)} {f(my + r * 0.14)} '
                       f'L{f(fx + r * 0.03)} {f(my + r * 0.95)} L{f(fx - r * 0.03)} {f(my + r * 0.95)} Z" fill="{col}"></path>')
            out.append(strands(fx - r * 0.05, my + r * 0.2, fx + r * 0.05, my + r * 0.2, 4, r * 0.7, 2))
        if kind in ("full", "long"):
            depth = r * (0.55 if kind == "full" else 1.1)
            jy = cy + r * 0.3
            d = (f"M{f(cx - r * jw * 1.02 + sh * 0.6)} {f(jy)} Q{f(cx - r * jw + sh * 0.6)} {f(cy + r * chd + depth * 0.5)} {f(cx + sh)} {f(cy + r * chd + depth)} "
                 f"Q{f(cx + r * jw + sh * 0.6)} {f(cy + r * chd + depth * 0.5)} {f(cx + r * jw * 1.02 + sh * 0.6)} {f(jy)} "
                 f"Q{f(cx + r * jw * 0.8)} {f(my + r * 0.05)} {f(fx + r * 0.2)} {f(my + r * 0.1)} Q{f(fx)} {f(my + r * 0.22)} {f(fx - r * 0.2)} {f(my + r * 0.1)} "
                 f"Q{f(cx - r * jw * 0.8)} {f(my + r * 0.05)} {f(cx - r * jw * 1.02 + sh * 0.6)} {f(jy)} Z")
            gid = self.id("bd")
            self._defs.append(f'<linearGradient id="{gid}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{hi}"></stop>'
                              f'<stop offset="0.45" stop-color="{col}"></stop><stop offset="1" stop-color="{shade(col, -0.5)}"></stop></linearGradient>')
            out.append(f'<path d="{d}" fill="url(#{gid})" stroke="#0A0604" stroke-width="0.6" stroke-opacity="0.6"></path>')
            out.append(strands(cx - r * jw * 0.8 + sh, cy + r * 0.6, cx + r * jw * 0.8 + sh, cy + r * 0.6, 9, depth * 0.8, 3))
        if kind == "stubble":
            out.append(f'<path d="M{f(cx - r * jw + sh * 0.6)} {f(cy + r * 0.45)} Q{f(cx + sh)} {f(cy + r * chd * 1.25)} {f(cx + r * jw + sh * 0.6)} {f(cy + r * 0.45)} '
                       f'Q{f(fx)} {f(my + r * 0.12)} {f(cx - r * jw + sh * 0.6)} {f(cy + r * 0.45)} Z" fill="{col}" opacity="0.28" filter="url(#{self.p}-soft)"></path>')
        return "".join(out)

    def clip_overlay(self, d, inner):
        """Clip arbitrary overlay markup (pattern lines, studs, stripes) to a shape path."""
        cid = self.id("k")
        return f'<clipPath id="{cid}"><path d="{d}"></path></clipPath><g clip-path="url(#{cid})">{inner}</g>'

    def stud(self, x, y, r, m=None):
        m = m or MATS["brass"]
        return self.ball(x, y, r, m, outline=True)
