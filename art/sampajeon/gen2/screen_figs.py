"""screen_figs — kit-rendered characters for the screen artboards (Main / Lobby / Match / Results).

Pre-rendered-sprite shading via figure_kit: every limb a lit cylinder with core shadow and bounce
light, real elbows/knees/hands, cloth folds, AO at every junction. Light from the top-left.

Full figures stand with feet at (0, 0); humans are ~100 units tall, dokkaebi brutes ~165.
`face` = +1 looks screen-right, -1 screen-left. Geometry is mirrored, lighting is NOT
(the key light stays top-left), so never flip a figure with a negative scale transform.

Busts live in the 120x140 box the ui_s24 medallion/portrait frames expect.
"""
import math
import random

from figure_kit import Kit, MATS, Mat, mix, shade, f, P, LIGHT

M = MATS
BRIG = Mat("#A62E1C", "#E26446", "#2A0704", "#6A2C3A", spec_op=0.22, gloss=0.3)
FELT = Mat("#2A2522", "#6A615A", "#060504", "#3A4458", spec="#E8E4F0", spec_op=0.3, gloss=0.4)
HEMP = M["white_hemp"]
SKIN = Mat("#C68A5E", "#F4C8A0", "#4A2414", "#8C5E4E")
BLUE = M["skin_blue"]
TIGER = M["tiger"]
OUT_S = "#120D08"
NAVY = Mat("#28305E", "#5A66A8", "#080A1E", "#3A4870", spec_op=0.2, gloss=0.2)


class SK(Kit):
    """Kit + Joseon costume parts. `res` lowers profile resolution for small (battlefield) sprites."""

    res = 14

    def _profile(self, a, b, w1, w2, bumps, n=None):
        return super()._profile(a, b, w1, w2, bumps, n=n or self.res)

    def grad_lin(self, a, b, stops):
        gid = self.id("l")
        st = "".join(f'<stop offset="{f(o)}" stop-color="{c}"' + (f' stop-opacity="{f(op)}"' if op is not None else "") + "></stop>"
                     for o, c, op in [(s + (None,))[:3] for s in stops])
        self._defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(a[0])}" y1="{f(a[1])}" '
                          f'x2="{f(b[0])}" y2="{f(b[1])}">{st}</linearGradient>')
        return gid

    def shaded_path(self, d, m, c, w, normal=(-0.8, -0.6), outline=True, sw=0.8):
        gid = self._cyl_grad(c, normal, w, m)
        o = f' stroke="{m.outline}" stroke-width="{f(sw)}" stroke-opacity="0.72" stroke-linejoin="round"' if outline else ""
        return f'<path d="{d}" fill="url(#{gid})"{o}></path>'

    def dome(self, d, cx, cy, r, m, outline=True, sw=0.8):
        gid = self._sphere_grad(cx, cy, r, m)
        o = f' stroke="{m.outline}" stroke-width="{f(sw)}" stroke-opacity="0.72"' if outline else ""
        return f'<path d="{d}" fill="url(#{gid})"{o}></path>'

    def crease(self, d, m, w=1.0, op=0.7, light_off=None):
        """Cloth fold: soft dark crease + thin light ridge offset toward the light."""
        self._filters()
        s = (f'<path d="{d}" fill="none" stroke="{m.core}" stroke-width="{f(w * 1.6)}" stroke-opacity="{f(op)}" '
             f'stroke-linecap="round" filter="url(#{self.p}-soft)"></path>')
        if light_off is not None:
            s += (f'<path d="{d}" transform="translate({f(light_off[0])} {f(light_off[1])})" fill="none" stroke="{m.light}" '
                  f'stroke-width="{f(w * 0.7)}" stroke-opacity="{f(op * 0.8)}" stroke-linecap="round"></path>')
        return s

    def ear(self, x, y, r, m):
        return (self.ball(x, y, r * 0.55, m, ry=r * 0.8)
                + f'<ellipse cx="{f(x + r * 0.08)}" cy="{f(y + r * 0.05)}" rx="{f(r * 0.24)}" ry="{f(r * 0.45)}" fill="{m.core}" opacity="0.55"></ellipse>')

    def small_head(self, cx, cy, r, m, face=1, look=None):
        """Individual face (figure_kit.face) turned toward the facing direction."""
        lk = dict(look or {})
        lk["turn"] = max(-0.6, min(0.6, 0.35 * face + lk.get("turn", 0.0)))
        return self.face(cx, cy, r, m, lk)

    def stud(self, x, y, r, m=None):
        """Small sprites: every brass stud shares one bounding-box gradient instead of its own."""
        if self.res > 8 or (m is not None and m is not M["brass"]):
            return super().stud(x, y, r, m)
        gid = f"{self.p}-stud"
        if not getattr(self, "_stud_def", False):
            self._stud_def = True
            b = M["brass"]
            self._defs.append(f'<radialGradient id="{gid}" cx="0.55" cy="0.6" r="0.6" fx="0.32" fy="0.28">'
                              f'<stop offset="0" stop-color="{mix(b.light, b.spec, 0.8)}"></stop><stop offset="0.35" stop-color="{b.light}"></stop>'
                              f'<stop offset="0.75" stop-color="{b.base}"></stop><stop offset="1" stop-color="{b.core}"></stop></radialGradient>')
        return f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(r)}" fill="url(#{gid})" stroke="{OUT_S}" stroke-width="0.5" stroke-opacity="0.6"></circle>'

    def tube(self, pts, widths, m, bumps=(), outline=True, n_per=8, chunks_per_seg=4, crease_at=()):
        if self.res <= 8:  # battlefield / crowd sprites: fewer samples keep the instanced defs small
            n_per, chunks_per_seg = min(n_per, 4), min(chunks_per_seg, 2)
        return super().tube(pts, widths, m, bumps, outline, n_per, chunks_per_seg, crease_at)

    def jeonrip(self, cx, cy, r, tassel=True):
        """전립 felt hat: wide brim + domed crown + brass knob + red 상모 tassel. (cx,cy)=brim centre."""
        out = [self.ao(cx + r * 0.1, cy + r * 0.35, r * 0.9, r * 0.22, 0.5)]
        brim = (f"M{f(cx - r * 1.35)} {f(cy)} C{f(cx - r * 1.3)} {f(cy - r * 0.42)} {f(cx + r * 1.3)} {f(cy - r * 0.42)} {f(cx + r * 1.35)} {f(cy)} "
                f"C{f(cx + r * 1.3)} {f(cy + r * 0.4)} {f(cx - r * 1.3)} {f(cy + r * 0.4)} {f(cx - r * 1.35)} {f(cy)} Z")
        gid = self.grad_lin((cx - r, cy - r * 0.4), (cx + r * 0.6, cy + r * 0.4),
                            [(0, "#7A716A"), (0.3, "#3E3834"), (0.7, "#1A1614"), (1, "#3A4256")])
        out.append(f'<path d="{brim}" fill="url(#{gid})" stroke="#060403" stroke-width="0.7" stroke-opacity="0.8"></path>')
        out.append(f'<path d="M{f(cx - r * 1.2)} {f(cy - r * 0.06)} C{f(cx - r * 0.9)} {f(cy - r * 0.3)} {f(cx)} {f(cy - r * 0.34)} {f(cx + r * 0.5)} {f(cy - r * 0.3)}" '
                   f'fill="none" stroke="#B8B0A6" stroke-width="{f(r * 0.06)}" stroke-opacity="0.6"></path>')
        crown = (f"M{f(cx - r * 0.62)} {f(cy - r * 0.1)} C{f(cx - r * 0.66)} {f(cy - r * 0.95)} {f(cx + r * 0.66)} {f(cy - r * 0.95)} "
                 f"{f(cx + r * 0.62)} {f(cy - r * 0.1)} C{f(cx + r * 0.3)} {f(cy + r * 0.06)} {f(cx - r * 0.3)} {f(cy + r * 0.06)} {f(cx - r * 0.62)} {f(cy - r * 0.1)} Z")
        out.append(self.dome(crown, cx, cy - r * 0.45, r * 0.7, FELT))
        out.append(self.form((cx - r * 0.6, cy - r * 0.2), (cx + r * 0.6, cy - r * 0.2), r * 0.1, r * 0.1, M["brass"], ao_start=False, outline=False))
        if tassel:
            rng = random.Random(int(cx * 7 + cy))
            tx, ty = cx, cy - r * 0.78
            for i in range(9):
                a = -2.5 + i * 0.32
                L = r * (0.75 + rng.random() * 0.35)
                ex, ey = tx + math.cos(a) * L, ty + math.sin(a) * L * 0.7 - r * 0.15
                c1 = (tx + math.cos(a) * L * 0.4, ty - r * 0.35)
                col = "#7A1A0C" if i % 3 == 0 else "#D23C22"
                out.append(f'<path d="M{P((tx, ty))} Q{P(c1)} {P((ex, ey))}" fill="none" stroke="{col}" stroke-width="{f(r * 0.13)}" stroke-linecap="round"></path>')
                if i % 2:
                    out.append(f'<path d="M{P((tx, ty))} Q{P((c1[0] - r * 0.04, c1[1] - r * 0.04))} {P((ex - r * 0.05, ey - r * 0.05))}" fill="none" '
                               f'stroke="#FF9070" stroke-width="{f(r * 0.04)}" stroke-opacity="0.8" stroke-linecap="round"></path>')
            out.append(self.ball(tx, ty, r * 0.13, M["brass"]))
        return "".join(out)

    def paeraengi(self, cx, cy, r):
        """패랭이 bamboo hat: woven brim + conical crown (straw)."""
        st = M["straw"]
        out = [self.ao(cx + r * 0.1, cy + r * 0.3, r * 0.9, r * 0.2, 0.5)]
        brim = (f"M{f(cx - r * 1.2)} {f(cy)} C{f(cx - r * 1.1)} {f(cy - r * 0.36)} {f(cx + r * 1.1)} {f(cy - r * 0.36)} {f(cx + r * 1.2)} {f(cy)} "
                f"C{f(cx + r * 1.1)} {f(cy + r * 0.34)} {f(cx - r * 1.1)} {f(cy + r * 0.34)} {f(cx - r * 1.2)} {f(cy)} Z")
        out.append(self.shaded_path(brim, st, (cx, cy), r * 1.2, normal=(-0.5, -0.86)))
        for a in range(0, 360, 15):
            ra = math.radians(a)
            out.append(f'<path d="M{P((cx + math.cos(ra) * r * 0.55, cy + math.sin(ra) * r * 0.16))} L{P((cx + math.cos(ra) * r * 1.15, cy + math.sin(ra) * r * 0.32))}" '
                       f'stroke="{st.core}" stroke-width="{f(r * 0.03)}" stroke-opacity="0.45"></path>')
        crown = (f"M{f(cx - r * 0.66)} {f(cy - r * 0.05)} C{f(cx - r * 0.6)} {f(cy - r * 0.7)} {f(cx - r * 0.3)} {f(cy - r * 0.95)} {f(cx)} {f(cy - r * 0.97)} "
                 f"C{f(cx + r * 0.3)} {f(cy - r * 0.95)} {f(cx + r * 0.6)} {f(cy - r * 0.7)} {f(cx + r * 0.66)} {f(cy - r * 0.05)} "
                 f"C{f(cx + r * 0.3)} {f(cy + r * 0.1)} {f(cx - r * 0.3)} {f(cy + r * 0.1)} {f(cx - r * 0.66)} {f(cy - r * 0.05)} Z")
        out.append(self.dome(crown, cx, cy - r * 0.45, r * 0.75, st))
        for i in range(1, 4):
            yy = cy - r * 0.22 * i
            ww = r * (0.66 - 0.12 * i)
            out.append(f'<path d="M{f(cx - ww)} {f(yy)} Q{f(cx)} {f(yy + r * 0.1)} {f(cx + ww)} {f(yy)}" fill="none" stroke="{st.core}" '
                       f'stroke-width="{f(r * 0.04)}" stroke-opacity="0.55"></path>')
        return "".join(out)

    def gat(self, cx, cy, r):
        """갓: translucent black horsehair brim + tall flat-topped crown."""
        out = [self.ao(cx + r * 0.1, cy + r * 0.3, r, r * 0.22, 0.5)]
        out.append(f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(r * 1.45)}" ry="{f(r * 0.38)}" fill="#14100E" opacity="0.78" stroke="#000" stroke-width="0.5"></ellipse>')
        out.append(f'<ellipse cx="{f(cx - r * 0.3)}" cy="{f(cy - r * 0.08)}" rx="{f(r * 0.8)}" ry="{f(r * 0.14)}" fill="#8A8A9A" opacity="0.35"></ellipse>')
        d = (f"M{f(cx - r * 0.45)} {f(cy)} L{f(cx - r * 0.42)} {f(cy - r * 1.05)} Q{f(cx)} {f(cy - r * 1.2)} {f(cx + r * 0.42)} {f(cy - r * 1.05)} "
             f"L{f(cx + r * 0.45)} {f(cy)} Q{f(cx)} {f(cy + r * 0.12)} {f(cx - r * 0.45)} {f(cy)} Z")
        out.append(self.shaded_path(d, FELT, (cx, cy - r * 0.5), r * 0.5, normal=(-1, 0)))
        return "".join(out)

    def horn(self, base, tip, w, bend=0.0):
        """Ringed horn: tapered form + growth rings."""
        hm = M["horn"]
        mid = ((base[0] + tip[0]) / 2 + bend, (base[1] + tip[1]) / 2)
        out = [self.form(base, mid, w, w * 0.62, hm, ao_start=True, spec=True),
               self.form(mid, tip, w * 0.62, w * 0.12, hm, ao_start=False)]
        for t in (0.18, 0.36, 0.54):
            p = (base[0] + (mid[0] - base[0]) * t * 2, base[1] + (mid[1] - base[1]) * t * 2) if t < 0.5 else \
                (mid[0] + (tip[0] - mid[0]) * (t - 0.5) * 2, mid[1] + (tip[1] - mid[1]) * (t - 0.5) * 2)
            ww = w * (1 - t * 0.7)
            out.append(f'<path d="M{f(p[0] - ww * 0.9)} {f(p[1] + ww * 0.15)} Q{f(p[0])} {f(p[1] + ww * 0.45)} {f(p[0] + ww * 0.9)} {f(p[1] + ww * 0.1)}" '
                       f'fill="none" stroke="{hm.core}" stroke-width="{f(max(0.4, w * 0.12))}" stroke-opacity="0.65"></path>')
        return "".join(out)

    def studs_in(self, d, x0, y0, x1, y1, step, r):
        st = "".join(self.stud(x0 + i * step + (j % 2) * step * 0.5, y0 + j * step * 0.9, r)
                     for j in range(int((y1 - y0) / (step * 0.9)) + 1) for i in range(int((x1 - x0) / step) + 1))
        return self.clip_overlay(d, st)

    def fire_glow(self, cx, cy, r, op=0.9):
        self._filters()
        gid = self.id("fg")
        self._defs.append(f'<radialGradient id="{gid}"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="0.25" stop-color="#CFF2FF"></stop>'
                          f'<stop offset="0.55" stop-color="#4FB3E8" stop-opacity="0.75"></stop><stop offset="1" stop-color="#1B6AA8" stop-opacity="0"></stop></radialGradient>')
        return f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="url(#{gid})" opacity="{f(op)}"></circle>'

    def ember(self, cx, cy, r):
        return (self.fire_glow(cx, cy, r * 2.2, 0.7)
                + f'<path d="M{f(cx)} {f(cy - r * 1.6)} C{f(cx + r * 0.9)} {f(cy - r * 0.6)} {f(cx + r)} {f(cy + r * 0.2)} {f(cx)} {f(cy + r * 0.9)} '
                  f'C{f(cx - r)} {f(cy + r * 0.2)} {f(cx - r * 0.9)} {f(cy - r * 0.6)} {f(cx)} {f(cy - r * 1.6)} Z" fill="#A8E6FF"></path>'
                + f'<circle cx="{f(cx - r * 0.15)}" cy="{f(cy + r * 0.1)}" r="{f(r * 0.45)}" fill="#FFFFFF"></circle>')


_SKINS = ("skin", "skin_tan", "skin_weathered", "skin_ruddy", "skin_fair")
_BEARDS = {"soldier": (None, "mustache", "goatee", "stubble"), "noble": ("thin_long", "goatee", "long"),
           "merchant": (None, "stubble", "mustache", "goatee"), "worker": (None, "stubble", "mustache", None)}


def human_look(seed, kind="soldier"):
    """Deterministic individual face for repeated units/townsfolk: returns (look dict, skin Mat)."""
    rng = random.Random(1000 + seed * 37 + sum(map(ord, kind)))
    lk = dict(shape=rng.choice(("oval", "long", "square", "round", "heart", "diamond")),
              eye=rng.choice(("almond", "narrow", "sharp", "droopy", "round")),
              brow=rng.choice(("straight", "arched", "angry", "thick", "thin", "worried")),
              nose=rng.choice(("straight", "broad", "hooked", "small", "flat")),
              mouth=rng.choice(("neutral", "stern", "calm", "smirk", "smile")),
              age=rng.choice((0.1, 0.3, 0.45, 0.65)), cheek=round(rng.uniform(0.3, 0.85), 2),
              jaw=round(rng.uniform(0.9, 1.12), 2))
    if kind == "woman":
        lk.update(fem=True, brow=rng.choice(("thin", "arched")), age=min(lk["age"], 0.45),
                  shape=rng.choice(("oval", "heart", "round")), mouth=rng.choice(("calm", "smile", "neutral")))
        skin = M[rng.choice(("skin_fair", "skin"))]
    else:
        lk["beard"] = rng.choice(_BEARDS.get(kind, (None,)))
        if lk["beard"] and lk["age"] > 0.6:
            lk["beard_col"] = "#6E665E"
        skin = M[rng.choice(_SKINS[:4])]
    return lk, skin


def _X(face):
    return lambda x, y: (face * x, y)


# =============================================================== battlefield units (feet at 0,0)
def gunner(K, face=1, aim=-14, fire=False, var=0):
    T = _X(face)
    look, skn = human_look(var, "soldier")
    if fire:
        look["mouth"] = "shout"
    o = [K.cast_shadow(face * 3, 0, 15, 4.2, 0.6)]
    far, near = 1, -1  # leg/arm on the +x local side is far (screen-right when facing right)
    # gun geometry
    a = math.radians(aim)
    ux, uy = math.cos(a), math.sin(a)
    butt = (-3.0, -63.0)
    gp = lambda t: T(butt[0] + ux * t, butt[1] + uy * t)
    # legs
    o.append(K.leg(T(3.2, -44), T(6.5, -25), T(8, -5), HEMP, w=4.0, trouser=HEMP, wrap=HEMP, foot_dir=face, back=True))
    o.append(K.leg(T(-3.2, -44), T(-5.5, -24), T(-6.5, -5), HEMP, w=4.0, trouser=HEMP, wrap=HEMP, foot_dir=face))
    # brigandine skirt + torso
    sk = (f"M{P(T(-8.4, -47))} L{P(T(8.4, -47))} L{P(T(10.6, -33))} Q{P(T(0, -30.5))} {P(T(-10.6, -33))} Z")
    o.append(K.shaded_path(sk, BRIG, T(0, -40), 10.6, normal=(-0.95, -0.3)))
    o.append(K.studs_in(sk, -11, -46, 11, -32, 3.4, 0.62))
    o.append(K.crease(f"M{P(T(-3, -46))} Q{P(T(-3.6, -39))} {P(T(-4.6, -32))}", BRIG, 0.6, 0.6))
    o.append(K.crease(f"M{P(T(4, -46))} Q{P(T(4.8, -39))} {P(T(6, -32))}", BRIG, 0.6, 0.6))
    t, td = K.torso(T(0, -72), T(0, -46), 9.4, 8.0, BRIG, chest=0.4)
    o.append(t)
    o.append(K.studs_in(td, -10, -70, 10, -48, 3.2, 0.6))
    o.append(K.form(T(-8.4, -48.5), T(8.4, -48.5), 1.7, 1.7, M["indigo"], ao_start=False))
    o.append(K.ball(*T(0.5, -48.5), 1.6, M["brass"]))
    o.append(K.ao(*T(0, -45.5), 8, 1.6, 0.5))
    # powder flask
    o.append(K.ball(*T(-9.5, -42), 2.2, M["lacquer"], ry=3.0))
    # collar + neck + head
    o.append(K.neck(T(0.4, -71), T(0.8, -76), 2.8, skn))
    o.append(f'<path d="M{P(T(-3.4, -72.6))} L{P(T(0.6, -67.5))} L{P(T(4.4, -72.6))}" fill="none" stroke="#EFE8D8" stroke-width="1.5" stroke-linejoin="round"></path>')
    h, hd = K.small_head(*T(1.2, -82), 6.2, skn, face=face, look=look)
    o.append(h)
    o.append(K.jeonrip(*T(1.2, -87.2), 6.6))
    # far arm (supports the forestock)
    o.append(K.arm(T(7.2, -69), T(13.5, -60), gp(22), BRIG, w=2.9, sleeve=BRIG, cuff=M["black_cloth"], hand="grip",
                   hand_dir=(face * ux, uy), hand_m=skn, back=True))
    # musket
    o.append(K.form(gp(0), gp(15), 2.2, 1.5, M["wood"], ao_start=False))
    o.append(K.form(gp(11), gp(47), 1.05, 0.9, M["iron"], ao_start=False))
    for tt in (24, 36):
        o.append(K.ball(*gp(tt), 1.2, M["brass"], outline=False))
    cord = gp(12)
    o.append(f'<circle cx="{f(cord[0])}" cy="{f(cord[1] - 1.6)}" r="1.4" fill="#FFB040" opacity="0.9" filter="url(#{K.p}-soft)"></circle>')
    if fire:
        m0 = gp(48)
        o.append(f'<g filter="url(#{K.p}-soft)"><circle cx="{f(m0[0] + face * ux * 3)}" cy="{f(m0[1] + uy * 3)}" r="4.4" fill="#FFF4C8"></circle>'
                 f'<path d="M{P(m0)} l{f(face * ux * 13 - uy * 3)} {f(uy * 13 - 3)} l{f(-face * ux * 3)} {f(3)} l{f(face * ux * 8 + uy * 3)} {f(uy * 8 + 2)} Z" fill="#FFD27A"></path></g>')
        for k in range(3):
            o.append(f'<circle cx="{f(m0[0] + face * (8 + k * 6))}" cy="{f(m0[1] - 6 - k * 5)}" r="{f(5 + k * 1.5)}" fill="#D8D0C4" opacity="{f(0.55 - k * 0.12)}" filter="url(#{K.p}-ao)"></circle>')
    # near arm (trigger hand)
    o.append(K.arm(T(-7.6, -69.5), T(-6.5, -57), gp(5), BRIG, w=3.0, sleeve=BRIG, cuff=M["black_cloth"], hand="grip",
                   hand_dir=(face * ux, uy), hand_m=skn))
    return "".join(o)


def worker(K, face=1, mode="idle", var=0):
    """mode: idle | mining | hammer | carry."""
    T = _X(face)
    look, skn = human_look(var, "worker")
    o = [K.cast_shadow(face * 2, 0, 13, 3.8, 0.6)]
    wd = M["wood"]
    # 지게 back frame behind the body
    for x0 in (-9.5, -4.5):
        o.append(K.form(T(x0, -6), T(x0 + 1.2, -76), 1.1, 1.0, wd, ao_start=False))
    for y in (-30, -46, -62):
        o.append(K.form(T(-10.4, y), T(-3.2, y - 0.6), 0.8, 0.8, wd.back(), ao_start=False, spec=False))
    if mode == "carry":
        for i, (dx, dy, hh) in enumerate(((-10, -64, 14), (-6, -66, 18), (-2.5, -63, 12))):
            o.append(K.fire_glow(*T(dx, dy - hh * 0.4), hh * 0.9, 0.35))
            pts = [T(dx - 2.6, dy), T(dx - 1.4, dy - hh), T(dx + 1.2, dy - hh * 0.8), T(dx + 2.4, dy)]
            o.append(f'<path d="M{" L".join(P(p) for p in pts)} Z" fill="#7FD8C0" stroke="#1C4A40" stroke-width="0.6"></path>'
                     f'<path d="M{P(pts[1])} L{P(pts[2])} L{P(T(dx, dy))} Z" fill="#CFF5EA" opacity="0.8"></path>')
    o.append(K.leg(T(3.0, -42), T(5.6, -23), T(6.6, -5), HEMP, w=3.8, trouser=HEMP, wrap=HEMP, foot_dir=face, back=True))
    o.append(K.leg(T(-3.0, -42), T(-4.6, -23), T(-5.4, -5), HEMP, w=3.8, trouser=HEMP, wrap=HEMP, foot_dir=face))
    t, td = K.torso(T(0, -69), T(0, -43), 8.6, 7.4, HEMP)
    o.append(t)
    o.append(K.crease(f"M{P(T(-4, -66))} Q{P(T(-2, -56))} {P(T(-3, -46))}", HEMP, 0.7, 0.6, (face * -0.8, -0.4)))
    o.append(K.crease(f"M{P(T(4.5, -64))} Q{P(T(3, -55))} {P(T(5, -46))}", HEMP, 0.7, 0.6))
    o.append(K.form(T(-8, -50), T(8, -50), 1.5, 1.5, M["red_cloth"], ao_start=False))
    o.append(K.form(T(-9.8, -66), T(-6.2, -45), 0.9, 0.9, M["leather_dark"], ao_start=False, spec=False))  # strap
    o.append(K.neck(T(0.4, -68), T(0.8, -73), 2.6, skn))
    h, hd = K.small_head(*T(1, -79), 6.0, skn, face=face, look=look)
    o.append(h)
    # headband + goggles pushed up
    o.append(K.form(T(-5.6, -83), T(6.4, -83.4), 1.1, 1.1, M["red_cloth"], ao_start=False))
    o.append(K.ball(*T(2.4, -84.6), 1.9, M["brass"]) + f'<circle cx="{f(T(2.4, 0)[0])}" cy="-84.6" r="1.1" fill="#7CC4AE"></circle>')
    o.append(f'<path d="M{P(T(-5.6, -84))} Q{P(T(0, -91))} {P(T(6.2, -84))}" fill="#1A120C" stroke="#000" stroke-width="0.4"></path>')
    if mode in ("mining", "hammer"):
        # two-hand overhead swing
        hx, hy = T(10, -86)
        o.append(K.arm(T(6.4, -66), T(11, -76), (hx, hy), HEMP, w=2.7, sleeve=HEMP, hand="grip", hand_dir=(face * 0.3, -1), hand_m=skn, back=True))
        head = T(16, -104) if mode == "mining" else T(13, -100)
        o.append(K.form(T(6, -66), head, 1.1, 1.0, wd, ao_start=False))
        if mode == "mining":
            o.append(K.form(T(10, -108), T(24, -96), 1.6, 0.4, M["iron"], ao_start=False))
        else:
            o.append(K.form(T(10.4, -103.5), T(16, -97), 2.6, 2.6, M["iron"], ao_start=False))
        o.append(K.arm(T(-6.8, -66), T(-2, -73), T(8, -82), HEMP, w=2.8, sleeve=HEMP, hand="grip", hand_dir=(face * 0.3, -1), hand_m=skn))
    else:
        o.append(K.arm(T(6.4, -66), T(8.6, -56), T(7.6, -46), HEMP, w=2.6, sleeve=HEMP, hand="fist", hand_m=skn, back=True))
        o.append(K.arm(T(-6.8, -66), T(-9, -56), T(-7.4, -47), HEMP, w=2.8, sleeve=HEMP, hand="grip", hand_dir=(0, 1), hand_m=skn))
    return "".join(o)


def _dokkaebi_head(K, cx, cy, r, face, m=BLUE, grin=True, horns=2, var=0):
    # per-individual variation: jaw, brow, eye colour, mouth, tusks, horn count/spread
    vr = random.Random(4242 + var * 131)
    jw = (1.0, 1.14, 0.9, 1.06)[var % 4]
    eye_col = ("#FFE27A", "#FF9A4A", "#E8F4FF", "#C8FF7A")[var % 4]
    mouth = ("grin", "snarl", "smirk", "grin")[var % 4] if grin else None
    brow_k = (1.0, 1.4, 0.7, 1.2)[var % 4]
    tusk = (1.0, 1.5, 0.7, 1.2)[var % 4]
    if horns == 2 and var % 4 == 2:
        horns = 1
    elif horns == 2 and var % 4 == 3:
        horns = 3
    out = []
    # wild hair mane behind
    rng = random.Random(int(abs(cx) * 13 + abs(cy)))
    hair = []
    for i in range(13):
        a = math.radians(180 + i * 15 + rng.uniform(-4, 4))
        r2 = r * (1.35 + rng.random() * 0.35)
        a0, a1 = a - 0.16, a + 0.16
        pts = [(cx + math.cos(a0) * r * 0.85, cy + math.sin(a0) * r * 0.85 - r * 0.1), (cx + math.cos(a) * r2, cy + math.sin(a) * r2 - r * 0.1),
               (cx + math.cos(a1) * r * 0.85, cy + math.sin(a1) * r * 0.85 - r * 0.1)]
        hair.append("M" + " L".join(P(p) for p in pts) + " Z")
    hg = K._sphere_grad(cx, cy - r * 0.3, r * 1.6, M["hair"])
    out.append(f'<path d="{" ".join(hair)}" fill="url(#{hg})" stroke="#000" stroke-width="0.5" stroke-opacity="0.7"></path>')
    # pointed ear on the back side
    ex = cx - face * r * 0.95
    ed = f"M{P((ex + face * r * 0.2, cy - r * 0.15))} L{P((ex - face * r * 0.75, cy - r * 0.55))} L{P((ex + face * r * 0.15, cy + r * 0.45))} Z"
    out.append(K.shaded_path(ed, m, (ex, cy), r * 0.5))
    # skull + heavy jaw
    pts = [(cx, cy - r * 0.95), (cx + r * 0.85, cy - r * 0.6), (cx + r * 1.0, cy + r * 0.05), (cx + r * 0.92 * jw, cy + r * 0.62),
           (cx + r * 0.45 * jw, cy + r * (0.96 + 0.06 * jw)), (cx - r * 0.45 * jw, cy + r * (0.96 + 0.06 * jw)), (cx - r * 0.92 * jw, cy + r * 0.62), (cx - r * 1.0, cy + r * 0.05),
           (cx - r * 0.85, cy - r * 0.6)]
    d = K._smooth(pts)
    out.append(K.dome(d, cx, cy, r * 1.1, m))
    fx = cx + face * r * 0.18
    ey = cy - r * 0.05
    # brow ridge: lit top plane, dark under
    out.append(f'<path d="M{f(fx - r * 0.82)} {f(ey - r * 0.28)} Q{f(fx)} {f(ey - r * 0.02)} {f(fx + r * 0.82)} {f(ey - r * 0.28)}" fill="none" '
               f'stroke="{m.core}" stroke-width="{f(r * 0.3)}" stroke-opacity="0.65" stroke-linecap="round" filter="url(#{K.p}-soft)"></path>')
    out.append(f'<path d="M{f(fx - r * 0.78)} {f(ey - r * 0.42)} Q{f(fx - r * 0.3)} {f(ey - r * 0.56)} {f(fx - r * 0.05)} {f(ey - r * 0.3)}" fill="none" '
               f'stroke="{m.light}" stroke-width="{f(r * 0.12)}" stroke-opacity="0.7" stroke-linecap="round"></path>')
    # glowing eyes
    for s in (-1, 1):
        exx = fx + s * r * 0.38
        out.append(f'<ellipse cx="{f(exx)}" cy="{f(ey)}" rx="{f(r * 0.28)}" ry="{f(r * 0.2)}" fill="#4FB3E8" opacity="0.5" filter="url(#{K.p}-soft)"></ellipse>'
                   f'<ellipse cx="{f(exx)}" cy="{f(ey)}" rx="{f(r * 0.2)}" ry="{f(r * 0.14)}" fill="{eye_col}"></ellipse>'
                   f'<ellipse cx="{f(exx + face * r * 0.03)}" cy="{f(ey)}" rx="{f(r * 0.05)}" ry="{f(r * 0.12)}" fill="#120A04"></ellipse>'
                   f'<circle cx="{f(exx - r * 0.07)}" cy="{f(ey - r * 0.05)}" r="{f(r * 0.04)}" fill="#FFF"></circle>')
        out.append(f'<path d="M{f(exx - s * r * 0.3)} {f(ey - r * 0.24 + (brow_k - 1) * r * 0.1)} L{f(exx + s * r * 0.24)} {f(ey - r * 0.12 - (brow_k - 1) * r * 0.08)}" stroke="#0A0C24" stroke-width="{f(r * 0.12 * brow_k)}" stroke-linecap="round"></path>')
    # broad nose
    out.append(K.ball(fx, cy + r * 0.32, r * 0.24, m, ry=r * 0.18))
    out.append(f'<circle cx="{f(fx - r * 0.12)}" cy="{f(cy + r * 0.4)}" r="{f(r * 0.06)}" fill="{m.core}"></circle><circle cx="{f(fx + r * 0.12)}" cy="{f(cy + r * 0.4)}" r="{f(r * 0.06)}" fill="{m.core}"></circle>')
    # cheek planes
    out.append(f'<ellipse cx="{f(fx - r * 0.55)}" cy="{f(cy + r * 0.3)}" rx="{f(r * 0.24)}" ry="{f(r * 0.15)}" fill="{m.light}" opacity="0.4" filter="url(#{K.p}-soft)"></ellipse>')
    out.append(f'<ellipse cx="{f(fx + r * 0.62)}" cy="{f(cy + r * 0.45)}" rx="{f(r * 0.25)}" ry="{f(r * 0.32)}" fill="{m.core}" opacity="0.35" filter="url(#{K.p}-soft)"></ellipse>')
    if mouth == "snarl":
        my = cy + r * 0.66
        out.append(f'<path d="M{f(fx - r * 0.42)} {f(my)} Q{f(fx)} {f(my - r * 0.16)} {f(fx + r * 0.42)} {f(my)} Q{f(fx)} {f(my + r * 0.2)} {f(fx - r * 0.42)} {f(my)} Z" fill="#2A0A12"></path>')
        for s in (-1, 1):
            tx = fx + s * r * 0.3
            out.append(f'<path d="M{f(tx - r * 0.08)} {f(my + r * 0.08)} L{f(tx)} {f(my - r * 0.42 * tusk)} L{f(tx + r * 0.08)} {f(my + r * 0.08)} Z" fill="#FFF8E6" stroke="#6A4A1A" stroke-width="0.3"></path>')
    elif mouth == "smirk":
        my = cy + r * 0.64
        out.append(f'<path d="M{f(fx - r * 0.4)} {f(my + r * 0.04)} Q{f(fx + r * 0.1)} {f(my + r * 0.12)} {f(fx + r * 0.5)} {f(my - r * 0.14)}" fill="none" stroke="#2A0A12" stroke-width="{f(r * 0.08)}" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(fx + r * 0.32)} {f(my - r * 0.04)} L{f(fx + r * 0.38)} {f(my - r * 0.3 * tusk)} L{f(fx + r * 0.44)} {f(my - r * 0.08)} Z" fill="#FFF8E6" stroke="#6A4A1A" stroke-width="0.3"></path>')
    elif mouth:
        my = cy + r * 0.62
        out.append(f'<path d="M{f(fx - r * 0.62)} {f(my - r * 0.06)} Q{f(fx)} {f(my + r * 0.42)} {f(fx + r * 0.62)} {f(my - r * 0.06)} Q{f(fx)} {f(my + r * 0.12)} {f(fx - r * 0.62)} {f(my - r * 0.06)} Z" fill="#2A0A12"></path>')
        for i in range(7):
            tx = fx - r * 0.45 + i * r * 0.15
            ty = my + r * 0.02 + math.sin(math.pi * i / 6) * r * 0.1
            out.append(f'<rect x="{f(tx - r * 0.06)}" y="{f(ty)}" width="{f(r * 0.12)}" height="{f(r * 0.1)}" rx="{f(r * 0.02)}" fill="#F4ECD6"></rect>')
        for s in (-1, 1):
            tx = fx + s * r * 0.5
            out.append(f'<path d="M{f(tx - r * 0.07)} {f(my + r * 0.04)} L{f(tx)} {f(my - r * 0.3 * tusk)} L{f(tx + r * 0.07)} {f(my + r * 0.04)} Z" fill="#FFF8E6" stroke="#6A4A1A" stroke-width="0.3"></path>')
    # chin AO onto neck
    out.append(K.ao(cx + r * 0.1, cy + r * 1.05, r * 0.7, r * 0.18, 0.5))
    if horns == 1:
        out.append(K.horn((cx, cy - r * 0.86), (cx + r * 0.1, cy - r * 2.0), r * 0.26, bend=r * 0.12))
    elif horns:
        out.append(K.horn((cx - r * 0.42, cy - r * 0.72), (cx - r * 0.75, cy - r * 1.75), r * 0.2, bend=-r * 0.25))
        out.append(K.horn((cx + r * 0.42, cy - r * 0.72), (cx + r * 0.75, cy - r * 1.75), r * 0.2, bend=r * 0.25))
        if horns == 3:
            out.append(K.horn((cx, cy - r * 0.9), (cx, cy - r * 1.6), r * 0.15))
    # fire rim on the shadow edge
    out.append(f'<path d="M{P((cx + r * 0.75, cy - r * 0.62))} Q{P((cx + r * 1.08, cy + r * 0.1))} {P((cx + r * 0.6, cy + r * 0.9))}" fill="none" '
               f'stroke="#7FD4FF" stroke-width="{f(r * 0.07)}" stroke-opacity="0.7" stroke-linecap="round"></path>')
    return "".join(out)


def _tiger_skirt(K, T, y0, y1, w0, w1, seed=2):
    d = (f"M{P(T(-w0, y0))} L{P(T(w0, y0))} L{P(T(w1, y1 - 3))} L{P(T(w1 * 0.4, y1))} L{P(T(0, y1 - 4))} "
         f"L{P(T(-w1 * 0.5, y1 + 1))} L{P(T(-w1, y1 - 2))} Z")
    o = [K.shaded_path(d, TIGER, T(0, (y0 + y1) / 2), w1, normal=(-0.95, -0.3))]
    rng = random.Random(seed)
    stripes = []
    for k in range(int(w1 * 2 / 4.5)):
        x = -w1 + 2.5 + k * 4.5 + rng.uniform(-0.8, 0.8)
        stripes.append(f'<path d="M{P(T(x, y0))} q{f(rng.uniform(1, 2.6))} {f((y1 - y0) * 0.35)} {f(rng.uniform(-0.8, 0.8))} {f((y1 - y0) * 0.7)}" '
                       f'fill="none" stroke="#1A0E06" stroke-width="{f(rng.uniform(1.1, 1.8))}" stroke-linecap="round"></path>')
    o.append(K.clip_overlay(d, "".join(stripes)))
    o.append(K.form(T(-w0 - 0.5, y0), T(w0 + 0.5, y0), 1.9, 1.9, M["leather"], ao_start=False))
    o.append(K.ao(*T(0, y1 + 1), w1 * 0.7, 2, 0.45))
    return "".join(o)


def brute(K, face=-1, swing=False, var=0):
    T = _X(face)
    o = [K.cast_shadow(face * 3, 0, 26, 6.5, 0.62)]
    # far leg + far arm
    o.append(K.leg(T(7, -64), T(12, -36), T(11, -7), BLUE, w=7.2, muscular=1, foot_dir=face, back=True, boot=BLUE))
    o.append(K.arm(T(20, -112), T(29, -88), T(25, -66), BLUE, w=7.0, muscular=1, hand="fist", hand_m=BLUE, back=True))
    o.append(K.leg(T(-7, -64), T(-13, -36), T(-12, -7), BLUE, w=7.4, muscular=1, foot_dir=face, boot=BLUE))
    t, td = K.torso(T(0, -116), T(0, -62), 21, 15, BLUE, chest=1, abs_=True, belly=3)
    o.append(t)
    o.append(_tiger_skirt(K, T, -66, -44, 15.5, 17.5))
    # traps + bead necklace
    o.append(K.form(T(-5, -120), T(-17, -113), 4.4, 5.4, BLUE, ao_start=False))
    o.append(K.form(T(5, -120), T(17, -113), 4.4, 5.4, BLUE, ao_start=False))
    for i in range(11):
        tt = i / 10
        bx, by = -11 + 22 * tt, -114 + 9 * math.sin(math.pi * tt)
        o.append(K.ball(*T(bx, by), 1.6, M["lacquer"] if i % 2 else M["horn"]))
    o.append(_dokkaebi_head(K, *T(1, -129), 12.5, face, var=var))
    # club
    ang = math.radians(-118 if swing else -62)
    hand = T(-27, -96) if swing else T(-24, -72)
    cu = (face * math.cos(ang), math.sin(ang))
    c0 = (hand[0] - cu[0] * 6, hand[1] - cu[1] * 6)
    c1 = (hand[0] + cu[0] * 44, hand[1] + cu[1] * 44)
    o.append(K.form(c0, c1, 2.6, 6.5, M["wood"], bumps=[(0.75, 1.5, 0)], ao_start=False))
    for k in range(5):
        tt = 18 + k * 5.5
        p = (hand[0] + cu[0] * tt, hand[1] + cu[1] * tt)
        nrm = (-cu[1], cu[0])
        side = 1 if k % 2 else -1
        o.append(K.ball(p[0] + nrm[0] * side * 3.6, p[1] + nrm[1] * side * 3.6, 1.6, M["iron"]))
    o.append(K.form((hand[0] - cu[0] * 4, hand[1] - cu[1] * 4), (hand[0] + cu[0] * 2, hand[1] + cu[1] * 2), 2.9, 2.9, M["brass"], ao_start=False, outline=False))
    # near arm gripping the club
    elbow = T(-36, -100) if swing else T(-33, -88)
    o.append(K.arm(T(-20, -112), elbow, (hand[0] - cu[0] * 2.5, hand[1] - cu[1] * 2.5), BLUE, w=7.4, muscular=1, hand="grip",
                   hand_dir=cu, hand_m=BLUE))
    o.append(K.ball(*T(-21.5, -110), 2.6, M["brass"]))  # arm ring
    return "".join(o)


def thrower(K, face=-1, ball=True, var=0):
    T = _X(face)
    o = [K.cast_shadow(face * 2, 0, 18, 4.8, 0.6)]
    o.append(K.leg(T(4.6, -52), T(8, -29), T(8, -6), BLUE, w=5.0, muscular=0.6, foot_dir=face, back=True, boot=BLUE))
    o.append(K.arm(T(13, -91), T(18, -76), T(14, -62), BLUE, w=4.6, muscular=0.5, hand="fist", hand_m=BLUE, back=True))
    o.append(K.leg(T(-4.6, -52), T(-8, -29), T(-8, -6), BLUE, w=5.2, muscular=0.6, foot_dir=face, boot=BLUE))
    t, td = K.torso(T(0, -94), T(0, -51), 13.5, 10.5, BLUE, chest=0.7, abs_=True)
    o.append(t)
    o.append(_tiger_skirt(K, T, -55, -38, 11, 12.5, seed=5))
    o.append(K.form(T(-11, -90), T(9, -60), 1.3, 1.3, M["red_cloth"], ao_start=False))  # cross strap
    o.append(K.ball(*T(-12.5, -45), 3.0, M["straw"]) + K.ball(*T(-12.5, -50), 2.0, M["straw"]))  # gourd
    o.append(_dokkaebi_head(K, *T(1, -105), 9.5, face, var=var))
    # raised throwing arm
    o.append(K.arm(T(-12, -91), T(-19, -104), T(-15, -118), BLUE, w=4.8, muscular=0.6, hand="open", hand_dir=(face * 0.3, -1), hand_m=BLUE))
    if ball:
        o.append(K.ember(*T(-14, -128), 5.2))
    return "".join(o)


def phantom(K, face=-1, var=0):
    T = _X(face)
    gm = M["skin_ghost"]
    o = ['<g opacity="0.8">']
    tail = f"M{P(T(-8, -40))} C{P(T(-12, -20))} {P(T(4, -14))} {P(T(-2, 0))} C{P(T(10, -10))} {P(T(12, -26))} {P(T(9, -40))} Z"
    o.append(K.shaded_path(tail, gm, T(0, -20), 10, outline=False))
    t, td = K.torso(T(0, -64), T(0, -38), 11, 8, gm, chest=0.6, outline=False)
    o.append(t)
    o.append(K.arm(T(-10, -60), T(-20, -52), T(-26, -60), gm, w=3.4, hand="open", hand_m=gm))
    o.append(K.arm(T(10, -60), T(19, -66), T(24, -76), gm, w=3.2, hand="open", hand_m=gm, back=True))
    o.append(_dokkaebi_head(K, *T(0, -74), 8.5, face, m=gm, grin=True, var=var))
    o.append('</g>')
    return "".join(o)


# =============================================================== 요물 무리 battlefield creatures (feet at 0,0; +x = forward)
GWI_SKIN = Mat("#8E8F86", "#CFCFC0", "#20231F", "#5E7480", spec_op=0.2, gloss=0.15)
GWI_CLOTH = Mat("#77736A", "#B4AE9E", "#1A1916", "#4E5E68", spec_op=0.12, gloss=0.1)
GWI_TIGER = Mat("#C98A2C", "#F4C468", "#3A1E08", "#7A6248", spec_op=0.3, gloss=0.3)
BUL_HIDE = Mat("#55463C", "#A48C72", "#0E0A07", "#3E5C72", spec="#FFE8C8", spec_op=0.5, gloss=0.6)
BUL_RUST = Mat("#8A4A24", "#D08A50", "#200E06", "#5A5048", spec_op=0.35, gloss=0.45)


def _glow_orb(K, x, y, r, inner="#EAF7FF", outer="#4FB3E8"):
    gid = K.id("o")
    K._defs.append(f'<radialGradient id="{gid}"><stop offset="0" stop-color="{inner}"></stop><stop offset="0.35" stop-color="{outer}" stop-opacity="0.85"></stop>'
                   f'<stop offset="1" stop-color="{outer}" stop-opacity="0"></stop></radialGradient>')
    return f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(r)}" fill="url(#{gid})"></circle>'


def changgwi(K, face=-1, bolt=True, var=0):
    """창귀: gaunt ghost in torn 베옷 with a spectral tiger over its shoulder; no feet, the hem dissolves."""
    T = _X(face)
    rng = random.Random(31 + var)
    o = [K.cast_shadow(face * -6, -1, 20, 5, 0.6)]
    # tiger-stripe shadow on the ground, cast backwards
    for i in range(4):
        x0 = -10 - i * 7
        o.append(f'<path d="M{P(T(x0, -5))} L{P(T(x0 - 3, 3))}" stroke="#0A0705" stroke-width="2.4" stroke-opacity="0.55" stroke-linecap="round"></path>')
    # spectral tiger behind the shoulder
    tx, ty = T(-11, -104)
    tig = [K.ball(tx, ty, 14.5, GWI_TIGER, ry=12.5)]
    for s in (-1, 1):
        tig.append(K.ball(tx + s * 11, ty - 11, 4.2, GWI_TIGER))
        tig.append(f'<circle cx="{f(tx + s * 11)}" cy="{f(ty - 10.5)}" r="1.9" fill="#2A1408"></circle>')
    for i, dx in enumerate((-7, -2.5, 2.5, 7)):
        tig.append(f'<path d="M{f(tx + dx)} {f(ty - 12)} q{f(dx * 0.2)} 4 {f(dx * 0.06)} {f(7 - abs(dx) * 0.3)}" fill="none" stroke="#1A0E06" stroke-width="1.7" stroke-linecap="round"></path>')
    for s in (-1, 1):
        for k in range(2):
            tig.append(f'<path d="M{f(tx + s * 13.5)} {f(ty - 2 + k * 5)} q{f(-s * 3.5)} 0 {f(-s * 6.5)} 1.4" fill="none" stroke="#1A0E06" stroke-width="1.5" stroke-linecap="round"></path>')
        tig.append(_glow_orb(K, tx + s * 5.2, ty - 2.5, 4, "#FFF3C0", "#F2A52C"))
        tig.append(f'<ellipse cx="{f(tx + s * 5.2)}" cy="{f(ty - 2.5)}" rx="0.7" ry="1.8" fill="#1A0A04"></ellipse>')
    tig.append(K.ball(tx, ty + 6, 6, Mat("#E8D8B8", "#FFF6E0", "#6A5438", "#9A8A78", spec_op=0.2), ry=4.2))
    tig.append(f'<path d="M{f(tx - 2.6)} {f(ty + 2.4)} L{f(tx + 2.6)} {f(ty + 2.4)} L{f(tx)} {f(ty + 5)} Z" fill="#2A1008"></path>')
    tig.append(f'<path d="M{f(tx - 4)} {f(ty + 8.6)} Q{f(tx)} {f(ty + 11)} {f(tx + 4)} {f(ty + 8.6)}" fill="none" stroke="#2A1008" stroke-width="1"></path>')
    o.append(f'<g opacity="0.82" transform="translate({f(tx)} {f(ty - 5)}) scale(1.28) translate({f(-tx)} {f(-ty)})">{"".join(tig)}</g>')
    # far arm hanging limp
    o.append(K.arm(T(-8, -76), T(-12, -56), T(-9, -38), GWI_CLOTH, m_fore=GWI_SKIN, w=3.3, hand="open", hand_m=GWI_SKIN, hand_dir=(0, 1), back=True))
    # torn robe: narrow shoulders, ragged hem that dissolves near the ground
    hem = []
    n = 7
    for i in range(n + 1):
        x = 15 - 30 * i / n
        hem.append(T(x, -8 - (rng.uniform(0, 9) if i % 2 else rng.uniform(9, 17))))
    d = (f"M{P(T(-9, -84))} Q{P(T(0, -88))} {P(T(9, -84))} C{P(T(12, -66))} {P(T(13, -40))} {P(T(15, -18))} L"
         + " L".join(P(p) for p in hem) + f" C{P(T(-14, -40))} {P(T(-12, -64))} {P(T(-9, -84))} Z")
    fade = K.id("f")
    K._defs.append(f'<linearGradient id="{fade}" gradientUnits="userSpaceOnUse" x1="0" y1="-30" x2="0" y2="-4"><stop offset="0" stop-color="#FFF"></stop>'
                   f'<stop offset="1" stop-color="#FFF" stop-opacity="0.15"></stop></linearGradient>'
                   f'<mask id="{fade}m" maskUnits="userSpaceOnUse" x="-70" y="-140" width="140" height="150"><rect x="-70" y="-140" width="140" height="150" fill="url(#{fade})"></rect></mask>')
    o.append(f'<g mask="url(#{fade}m)">' + K.shaded_path(d, GWI_CLOTH, (0, -48), 15, normal=(-0.95, -0.3)))
    folds = "".join(K.crease(f"M{P(T(x, -74))} Q{P(T(x + 2, -46))} {P(T(x * 1.2, -18))}", GWI_CLOTH, w=0.9, op=0.75, light_off=(-1, 0)) for x in (-5, 1, 6))
    o.append(K.clip_overlay(d, folds) + "</g>")
    # 저고리 overlap + tiger-claw wounds across the chest
    o.append(f'<path d="M{P(T(-5, -85))} L{P(T(5, -62))}" stroke="#3A372F" stroke-width="1.5" stroke-linecap="round"></path>')
    for i in range(3):
        y0 = -80 + i * 4.4
        o.append(f'<path d="M{P(T(-8 + i, y0))} L{P(T(6 + i, y0 + 11))}" stroke="#5A0E0A" stroke-width="1.7" stroke-linecap="round"></path>'
                 f'<path d="M{P(T(-7.5 + i, y0 - 0.8))} L{P(T(6.5 + i, y0 + 10.2))}" stroke="#D8C8B0" stroke-width="0.5" stroke-opacity="0.8"></path>')
    # head
    hx, hy = T(2, -95)
    o.append(K.form(T(1, -84), T(2, -90), 3, 2.8, GWI_SKIN, ao_start=False, ao_end=True, spec=False))
    look = dict(shape=("long", "diamond", "long")[var % 3], cheek=1.0, eye="droopy", brow="worried", nose=("small", "straight", "flat")[var % 3],
                mouth=("neutral", "stern", "neutral")[var % 3], age=0.65, turn=face * 0.28, ear=False, brow_col="#3A3830")
    fs, fd = K.face(hx, hy, 8.2, GWI_SKIN, look, ear=False)
    o.append(fs)
    # hollow eye sockets drawn over the kit's eyes
    fx = hx + face * 0.28 * 8.2 * 0.3
    for s in (-1, 1):
        ex = fx + s * 8.2 * 0.36
        o.append(f'<ellipse cx="{f(ex)}" cy="{f(hy - 0.3)}" rx="2.1" ry="1.7" fill="#050608"></ellipse><circle cx="{f(ex)}" cy="{f(hy - 0.2)}" r="0.55" fill="#9FE3FF"></circle>')
    # lank hair
    hair = f"M{P(T(-8.6, -92))} C{P(T(-9, -106))} {P(T(8, -107))} {P(T(8.6, -96))} C{P(T(5, -102))} {P(T(-2, -103))} {P(T(-6, -96))} C{P(T(-7, -88))} {P(T(-8, -82))} {P(T(-11, -78))} C{P(T(-10.5, -84))} {P(T(-9.6, -88))} {P(T(-8.6, -92))} Z"
    o.append(K.shaded_path(hair, M["hair"], (hx, hy - 6), 9, normal=(-0.6, -0.8)))
    # long reaching arm with a soul-bolt gathering in the hand
    wrist = T(40, -74)
    o.append(K.arm(T(7, -77), T(23, -68), wrist, GWI_CLOTH, m_fore=GWI_SKIN, w=3.5, hand="open", hand_dir=(face, -0.1), hand_m=GWI_SKIN))
    if bolt:
        o.append(_glow_orb(K, *T(50, -75), 7.5, "#FFFFFF", "#7FD4FF"))
    return "".join(o)


def eodukshini(K, var=0):
    """어둑시니: limbless swelling mass of darkness; narrow at the base, ink-wash edges dissolving upward."""
    p = K.p
    K._filters()
    fid = K.id("i")
    gid = K.id("d")
    hal = K.id("h")
    K._defs.append(
        f'<filter id="{fid}" x="-40%" y="-30%" width="180%" height="160%"><feTurbulence type="fractalNoise" baseFrequency="0.035 0.05" numOctaves="3" seed="11"></feTurbulence>'
        f'<feDisplacementMap in="SourceGraphic" scale="15" xChannelSelector="R" yChannelSelector="G"></feDisplacementMap></filter>'
        f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="-30" y1="-126" x2="26" y2="-8"><stop offset="0" stop-color="#2E2748"></stop><stop offset="0.4" stop-color="#100D1C"></stop>'
        f'<stop offset="0.85" stop-color="#05040A"></stop><stop offset="1" stop-color="#05040A" stop-opacity="0.55"></stop></linearGradient>'
        f'<radialGradient id="{hal}"><stop offset="0" stop-color="#7A62C8" stop-opacity="0.6"></stop><stop offset="0.65" stop-color="#4FB3E8" stop-opacity="0.2"></stop><stop offset="1" stop-color="#4FB3E8" stop-opacity="0"></stop></radialGradient>')
    d = ("M-5 -6 C-11 -22 -15 -38 -27 -56 C-40 -76 -39 -104 -20 -118 C-8 -128 12 -129 25 -118 C42 -103 41 -76 28 -56 C17 -38 12 -22 6 -6 Q0 -2 -5 -6 Z")
    o = [K.cast_shadow(6, 8, 15, 4.5, 0.55)]
    o.append(f'<ellipse cx="0" cy="-74" rx="58" ry="66" fill="url(#{hal})"></ellipse>')
    o.append(f'<g filter="url(#{fid})">')
    o.append(f'<path d="{d}" fill="none" stroke="#7FD4FF" stroke-width="5" stroke-opacity="0.55" filter="url(#{p}-soft)"></path>')
    o.append(f'<path d="{d}" fill="url(#{gid})"></path>')
    o.append('<ellipse cx="-14" cy="-96" rx="16" ry="20" fill="#5A4A8A" opacity="0.42"></ellipse>')
    o.append(f'<path d="M-20 -116 C-8 -128 12 -129 25 -118" fill="none" stroke="#A8DDF5" stroke-width="1.6" stroke-opacity="0.75"></path>')
    o.append('</g>')
    rng = random.Random(77 + var)
    for i in range(7):  # ink wisps peeling off the top
        x = -30 + i * 10 + rng.uniform(-3, 3)
        y = -116 + abs(x) * 0.34
        o.append(f'<path d="M{f(x)} {f(y)} q{f(rng.uniform(-8, 8))} {f(-rng.uniform(6, 12))} {f(rng.uniform(-5, 5))} {f(-rng.uniform(13, 24))}" fill="none" stroke="#120E20" '
                 f'stroke-width="{f(rng.uniform(1.8, 3.6))}" stroke-opacity="0.75" stroke-linecap="round" filter="url(#{p}-soft)"></path>')
    # faint eyes, slightly mismatched, far too high on the body
    for x, y, r in ((-10, -88, 5.4), (10, -91, 4.4)):
        o.append(_glow_orb(K, x, y, r * 2.2, "#E8F7FF", "#6FC8F0"))
        o.append(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(r * 0.55)}" ry="{f(r * 0.3)}" fill="#F4FBFF"></ellipse><circle cx="{f(x)}" cy="{f(y)}" r="{f(r * 0.2)}" fill="#05040A"></circle>')
    return "".join(o)


def bulgasari(K, face=-1):
    """불가사리: stocky four-legged iron-eater with needle bristles and rust plates, chewing a cannon barrel."""
    T = _X(face)
    rng = random.Random(53)
    o = [K.cast_shadow(-4 * face, 1, 62, 10, 0.65)]
    # scrap metal it has been eating
    for x, y, ln, a in ((54, -3, 12, 0.3), (-58, -2, 10, -0.2), (70, 3, 8, 0.9)):
        o.append(K.form(T(x, y), T(x + ln * math.cos(a), y + ln * math.sin(a)), 1.6, 1.3, M["iron"], ao_start=False))
    # far legs
    far = BUL_HIDE.back(0.3)
    o.append(K.tube([T(-30, -40), T(-36, -22), T(-33, -6)], [10, 7.5, 7], far))
    o.append(K.tube([T(26, -42), T(31, -24), T(28, -6)], [9.5, 7, 6.6], far))
    # stub tail
    o.append(K.tube([T(-50, -58), T(-62, -62), T(-68, -52)], [5, 3.6, 1.5], BUL_HIDE))
    # barrel body
    pts = [T(-52, -50), T(-46, -72), T(-22, -86), T(10, -86), T(36, -76), T(46, -56), T(40, -32), T(10, -22), T(-24, -22), T(-46, -30)]
    d = K._smooth(pts)
    o.append(K.dome(d, face * -4, -56, 58, BUL_HIDE, sw=1.0))
    o.append(K.clip_overlay(d, f'<ellipse cx="0" cy="-20" rx="56" ry="15" fill="#050302" opacity="0.65" filter="url(#{K.p}-ao)"></ellipse>'))
    # rust plates with goblin-fire seams
    plates = []
    for i, (x, y, w, h) in enumerate(((-30, -60, 15, 11), (-6, -66, 16, 12), (18, -62, 14, 11), (-18, -42, 14, 9), (8, -42, 15, 9), (31, -44, 9, 8))):
        pd = (f"M{P(T(x - w, y + h * 0.3))} Q{P(T(x - w * 0.6, y - h))} {P(T(x, y - h))} Q{P(T(x + w * 0.7, y - h))} {P(T(x + w, y + h * 0.1))} "
              f"Q{P(T(x + w * 0.5, y + h))} {P(T(x, y + h))} Q{P(T(x - w * 0.6, y + h))} {P(T(x - w, y + h * 0.3))} Z")
        plates.append(f'<path d="{pd}" fill="none" stroke="#4FB3E8" stroke-width="2.6" stroke-opacity="0.7" filter="url(#{K.p}-soft)"></path>')
        plates.append(K.dome(pd, T(x, y)[0], y, max(w, h) * 1.25, BUL_RUST, sw=0.8))
        for k in range(3):
            rx, ry = T(x + rng.uniform(-w * 0.6, w * 0.6), y + rng.uniform(-h * 0.5, h * 0.5))
            plates.append(f'<circle cx="{f(rx)}" cy="{f(ry)}" r="{f(rng.uniform(0.9, 1.5))}" fill="#1E0E06" opacity="0.6"></circle>')
    o.append(K.clip_overlay(d, "".join(plates)))
    # needle bristles along the back: each spike has a lit and a shaded face
    spikes = []
    N = 34
    for i in range(N):
        t = i / (N - 1)
        x = -50 + 92 * t
        top = -86 + 0.0105 * (x + 6) ** 2
        ln = rng.uniform(10, 17) * (0.75 + 0.4 * math.sin(math.pi * t))
        lean = -0.5 + t + rng.uniform(-0.15, 0.15)
        b0, b1 = T(x - 2.1, top + 3), T(x + 2.1, top + 3)
        tip = T(x + lean * ln * 0.6, top - ln)
        L, R = (b0, b1) if b0[0] < b1[0] else (b1, b0)
        mx = ((L[0] + R[0]) / 2, (L[1] + R[1]) / 2)
        spikes.append(f'<path d="M{P(L)} L{P(tip)} L{P(mx)} Z" fill="#C8D0D8" stroke="#120D08" stroke-width="0.4"></path>'
                      f'<path d="M{P(mx)} L{P(tip)} L{P(R)} Z" fill="#2C3036" stroke="#120D08" stroke-width="0.4"></path>')
    # shorter flank bristles
    for i in range(26):
        x, y = rng.uniform(-44, 36), rng.uniform(-78, -36)
        ln = rng.uniform(5, 8)
        tip = T(x - 2, y - ln)
        L, R = T(x - 1.2, y), T(x + 1.2, y)
        spikes.append(f'<path d="M{P(L)} L{P(tip)} L{P(R)} Z" fill="#8E969E" stroke="#120D08" stroke-width="0.3"></path>')
    o.append("".join(spikes))
    # near legs: thick, continuous, with iron claws
    for hip, knee, ankle in ((T(-34, -36), T(-42, -19), T(-38, -6)), (T(22, -36), T(28, -19), T(24, -6))):
        o.append(K.tube([hip, knee, ankle], [12, 8.6, 8], BUL_HIDE, [(0.2, 2.5, 0)], crease_at=(1,)))
        o.append(K.ball(ankle[0], ankle[1] + 1, 9, BUL_HIDE, ry=6))
        for c in (-5, 0, 5):
            cx = ankle[0] + c
            cy = ankle[1] + 5
            o.append(f'<path d="M{f(cx - 1.8)} {f(cy - 1)} L{f(cx + face * 2.4)} {f(cy + 3.4)} L{f(cx + 1.8)} {f(cy - 1)} Z" fill="#D0D6DC" stroke="#120D08" stroke-width="0.4"></path>')
    # neck + head + tapir snout
    o.append(K.tube([T(32, -66), T(46, -66), T(56, -62)], [15, 13.5, 12], BUL_HIDE))
    hx, hy = T(60, -62)
    o.append(K.ball(*T(50, -80), 4.6, BUL_HIDE) + K.ball(*T(70, -78), 4.2, BUL_HIDE))
    o.append(K.ball(hx, hy, 15.5, BUL_HIDE, ry=14))
    o.append(K.tube([T(68, -58), T(80, -54), T(88, -56)], [10, 7.8, 5.6], BUL_HIDE))
    o.append(K.ball(*T(89, -57), 5, BUL_HIDE, ry=4.2))
    o.append(f'<ellipse cx="{f(T(91, -58)[0])}" cy="-58" rx="1.2" ry="1.6" fill="#050302"></ellipse>')
    # brow ridge + glowing eye
    ex, ey = T(66, -68)
    o.append(f'<path d="M{P(T(58, -74))} Q{P(T(66, -76))} {P(T(74, -68))}" fill="none" stroke="#0A0604" stroke-width="3.4" stroke-linecap="round"></path>')
    o.append(_glow_orb(K, ex, ey, 6, "#FFFFFF", "#4FB3E8"))
    o.append(f'<circle cx="{f(ex)}" cy="{f(ey)}" r="1.1" fill="#04121A"></circle>')
    # lower jaw + the cannon barrel it chews, wheel still attached
    o.append(K.tube([T(58, -48), T(70, -44), T(80, -46)], [7, 5.4, 3.6], BUL_HIDE.back(0.12)))
    bar0, bar1 = T(58, -36), T(100, -52)
    o.append(K.form(bar0, bar1, 3.4, 4.2, M["brass"], ao_start=False, ao_end=False))
    o.append(K.ball(bar1[0], bar1[1], 4.6, M["brass"], ry=4.6) + f'<circle cx="{f(bar1[0])}" cy="{f(bar1[1])}" r="2.4" fill="#0A0604"></circle>')
    wx, wy = T(60, -20)
    o.append(f'<circle cx="{f(wx)}" cy="{f(wy)}" r="11" fill="none" stroke="#3E2616" stroke-width="3"></circle><circle cx="{f(wx)}" cy="{f(wy)}" r="11" fill="none" stroke="#8A9096" stroke-width="0.9"></circle>')
    for a in range(0, 180, 45):
        ca, sa = math.cos(math.radians(a)) * 10, math.sin(math.radians(a)) * 10
        o.append(f'<path d="M{f(wx - ca)} {f(wy - sa)} L{f(wx + ca)} {f(wy + sa)}" stroke="#5A3A22" stroke-width="1.6"></path>')
    o.append(K.ball(wx, wy, 2.6, M["brass"]))
    # upper-lip teeth biting down on the barrel
    for tx0 in (68, 72.5, 77, 81.5):
        t0, t1, t2 = T(tx0 - 1.8, -50), T(tx0 + 0.2, -43.5), T(tx0 + 1.8, -50)
        o.append(f'<path d="M{P(t0)} L{P(t1)} L{P(t2)} Z" fill="#E8E0CC" stroke="#3A2A18" stroke-width="0.4"></path>')
    return "".join(o)


# =============================================================== crowd figures (Main menu)
def crowd(K, kind="soldier", face=1, var=0):
    """Townsfolk ~100 units tall. kinds: soldier, merchant, noble, woman, goblin."""
    T = _X(face)
    look, skn = human_look(var, kind)
    o = [K.cast_shadow(face * 2, 0, 13, 3.6, 0.55)]
    if kind == "goblin":
        return brute(K, face, swing=False, var=var)
    robe = {"soldier": BRIG, "merchant": HEMP, "noble": Mat("#E6DFCC", "#FFFBF0", "#5E5848", "#8A98AA", spec_op=0.25, gloss=0.2),
            "woman": M["ochre_cloth"]}[kind]
    if kind == "merchant":
        for x0 in (-9.5, -4.5):
            o.append(K.form(T(x0, -8), T(x0 + 1, -74), 1.0, 0.9, M["wood"], ao_start=False))
        box = f"M{P(T(-12, -78))} L{P(T(-2, -78))} L{P(T(-2, -58))} L{P(T(-12, -58))} Z"
        o.append(K.shaded_path(box, M["wood"], T(-7, -68), 6, normal=(-1, -0.2)))
        o.append(K.form(T(-12, -68), T(-2, -68), 0.7, 0.7, M["leather_dark"], ao_start=False, spec=False))
        o.append(K.ball(*T(-7, -82), 5.5, M["indigo"], ry=3.6))
    if kind in ("soldier", "merchant"):
        o.append(K.leg(T(3, -42), T(5, -23), T(6, -5), HEMP, w=3.8, trouser=HEMP, wrap=HEMP, foot_dir=face, back=True))
        o.append(K.leg(T(-3, -42), T(-4.5, -23), T(-5.2, -5), HEMP, w=3.8, trouser=HEMP, wrap=HEMP, foot_dir=face))
    else:
        # long robe / chima to the ankles
        hem = 13 if kind == "woman" else 10.5
        skirt_m = NAVY if kind == "woman" else robe
        d = (f"M{P(T(-7.5, -48 if kind != 'woman' else -60))} L{P(T(7.5, -48 if kind != 'woman' else -60))} "
             f"C{P(T(10, -30))} {P(T(hem, -12))} {P(T(hem, -3))} Q{P(T(0, 0.5))} {P(T(-hem, -3))} C{P(T(-hem, -12))} {P(T(-10, -30))} Z")
        o.append(K.shaded_path(d, skirt_m, T(0, -28), hem, normal=(-0.97, -0.2)))
        for x in (-5, -1, 3.5, 7):
            o.append(K.crease(f"M{P(T(x * 0.6, -44))} Q{P(T(x * 0.9, -25))} {P(T(x * 1.2, -4))}", skirt_m, 0.6, 0.55, (face * -0.7, 0)))
        o.append(K.ball(*T(-3, -2), 2.4, M["white_hemp"], ry=1.4) + K.ball(*T(4, -2), 2.4, M["white_hemp"].back(), ry=1.4))
    top_y = -70
    waist = -45 if kind != "woman" else -60
    t, td = K.torso(T(0, top_y), T(0, waist), 8.6 if kind != "woman" else 7.6, 7.4 if kind != "woman" else 6.6, robe)
    o.append(t)
    if kind == "soldier":
        o.append(K.studs_in(td, -10, -68, 10, -47, 3.2, 0.6))
        sk = f"M{P(T(-8.2, -46))} L{P(T(8.2, -46))} L{P(T(10, -33))} Q{P(T(0, -31))} {P(T(-10, -33))} Z"
        o.append(K.shaded_path(sk, BRIG, T(0, -40), 10, normal=(-0.95, -0.3)))
        o.append(K.studs_in(sk, -11, -45, 11, -33, 3.4, 0.6))
    if kind == "noble":
        o.append(K.form(T(-7, -52), T(7, -52), 0.9, 0.9, Mat("#7A1E2A", "#C4485A", "#20060A"), ao_start=False))
        o.append(K.form(T(5, -52), T(6.5, -38), 0.6, 0.5, Mat("#7A1E2A", "#C4485A", "#20060A"), ao_start=False, spec=False))
    if kind == "woman":
        o.append(K.form(T(-1, -64), T(3, -54), 1.1, 0.7, M["red_cloth"], ao_start=False))  # 고름
    belt = {"soldier": M["indigo"], "merchant": M["leather"], "noble": None, "woman": None}[kind]
    if belt:
        o.append(K.form(T(-8, -47.5), T(8, -47.5), 1.5, 1.5, belt, ao_start=False))
    o.append(K.neck(T(0.4, -69), T(0.8, -74), 2.5, skn))
    o.append(f'<path d="M{P(T(-3, -70.4))} L{P(T(0.6, -65.5))} L{P(T(4, -70.4))}" fill="none" stroke="#F4EEDD" stroke-width="1.3" stroke-linejoin="round"></path>')
    h, hd = K.small_head(*T(1, -80), 6.0, skn, face=face, look=look)
    o.append(h)
    if kind == "soldier":
        o.append(K.jeonrip(*T(1, -85.2), 6.4))
        # spear held upright
        o.append(K.form(T(10, -2), T(12.5, -112), 0.9, 0.8, M["wood"], ao_start=False))
        o.append(K.form(T(12.4, -112), T(12.8, -124), 1.6, 0.2, M["iron"], ao_start=False))
        o.append(f'<path d="M{P(T(12.4, -110))} q{f(face * 3)} 3 {f(face * 1)} 7" fill="none" stroke="#D23C22" stroke-width="1.6" stroke-linecap="round"></path>')
        o.append(K.arm(T(6.4, -67), T(9.6, -57), T(11, -62), robe, w=2.5, sleeve=robe, hand="grip", hand_dir=(face * 0.02, -1), hand_m=skn, back=True))
        o.append(K.arm(T(-6.6, -67), T(-8.6, -56), T(-7.2, -46.5), robe, w=2.7, sleeve=robe, hand="fist", hand_m=skn))
    elif kind == "merchant":
        o.append(K.paeraengi(*T(1, -84.6), 6.6))
        o.append(K.arm(T(6.2, -66), T(8.4, -56), T(7, -46), robe, w=2.5, sleeve=robe, hand="fist", hand_m=skn, back=True))
        o.append(K.arm(T(-6.6, -66), T(-6.4, -55), T(-8.5, -66), robe, w=2.7, sleeve=robe, hand="grip", hand_dir=(0, -1), hand_m=skn))
    elif kind == "noble":
        o.append(K.gat(*T(1, -85), 6.4))
        # wide sleeves clasped in front
        o.append(K.tube([T(7.4, -67), T(8.6, -59), T(3, -52)], [3.4, 4.2, 5.0], robe.back(), crease_at=(1,)))
        o.append(K.tube([T(-7.4, -67), T(-8.8, -59), T(-3, -52)], [3.6, 4.4, 5.2], robe, crease_at=(1,)))
        o.append(K.folds(T(-7.4, -67), T(-3, -52), 3.6, 5.0, robe, n=2))
        o.append(K.ao(*T(0, -50), 5, 1.6, 0.5))
    elif kind == "woman":
        o.append(K.ball(*T(-5.4, -79), 3.0, M["hair"]))  # bun
        o.append(f'<path d="M{P(T(-5.6, -80))} Q{P(T(1, -91))} {P(T(7, -81))} Q{P(T(2, -86))} {P(T(-5.6, -80))} Z" fill="#14100E"></path>')
        o.append(K.form(T(-8, -80), T(-3, -79.4), 0.5, 0.5, M["brass"], ao_start=False, outline=False))  # binyeo
        o.append(K.arm(T(5.6, -68), T(7.6, -60), T(3, -55), robe, w=2.2, sleeve=robe, cuff=M["red_cloth"], hand="fist", hand_m=skn, back=True))
        o.append(K.arm(T(-5.8, -68), T(-6.6, -59), T(-1.5, -55), robe, w=2.3, sleeve=robe, cuff=M["red_cloth"], hand="fist", hand_m=skn))
    return "".join(o)


# =============================================================== busts (120 x 140 box)
_BN = [0]


def _bkit(p):
    _BN[0] += 1
    return SK(f"{p}k{_BN[0]}")


def _bust_body(K, m, sw=56, top=97, neck_w=10, cx=60):
    """Shoulders + chest mass with cylinder shading, deltoid swells and collar AO."""
    d = (f"M{f(cx - sw)} 142 C{f(cx - sw + 2)} 122 {f(cx - sw + 10)} {f(top + 9)} {f(cx - sw * 0.5)} {f(top + 2)} "
         f"C{f(cx - neck_w - 6)} {f(top - 1)} {f(cx - neck_w)} {f(top - 1.5)} {f(cx)} {f(top - 1.5)} "
         f"C{f(cx + neck_w)} {f(top - 1.5)} {f(cx + neck_w + 6)} {f(top - 1)} {f(cx + sw * 0.5)} {f(top + 2)} "
         f"C{f(cx + sw - 10)} {f(top + 9)} {f(cx + sw - 2)} 122 {f(cx + sw)} 142 Z")
    o = [K.shaded_path(d, m, (cx, 120), sw * 0.95, normal=(-0.97, -0.25), sw=1)]
    # deltoid caps
    o.append(K.clip_overlay(d, f'<ellipse cx="{f(cx - sw * 0.7)}" cy="{f(top + 14)}" rx="{f(sw * 0.26)}" ry="{f(sw * 0.16)}" fill="{m.light}" opacity="0.55" filter="url(#{K.p}-ao)"></ellipse>'
                            f'<ellipse cx="{f(cx + sw * 0.78)}" cy="{f(top + 26)}" rx="{f(sw * 0.24)}" ry="{f(sw * 0.3)}" fill="{m.core}" opacity="0.45" filter="url(#{K.p}-ao)"></ellipse>'
                            f'<ellipse cx="{f(cx)}" cy="{f(top + 40)}" rx="{f(sw * 0.5)}" ry="{f(sw * 0.2)}" fill="{m.core}" opacity="0.25" filter="url(#{K.p}-ao)"></ellipse>'))
    o.append(f'<path d="M{f(cx - sw * 0.92)} {f(top + 22)} C{f(cx - sw * 0.85)} {f(top + 10)} {f(cx - sw * 0.6)} {f(top + 4)} {f(cx - sw * 0.35)} {f(top + 2)}" '
             f'fill="none" stroke="{m.spec}" stroke-width="1.6" stroke-opacity="0.45" stroke-linecap="round" filter="url(#{K.p}-soft)"></path>')
    return "".join(o), d


def _bust_neck(K, skin):
    """Continuous neck flaring into the collar, with sternocleidomastoid planes and jaw AO."""
    # matte, low-contrast ramp: a neck is a soft form in the jaw's shadow, not a glossy cylinder
    nm = Mat(shade(skin.base, -0.08), skin.base, mix(skin.core, skin.base, 0.45), skin.bounce, spec_op=0.0, gloss=0.1)
    o = [K.tube([(60, 101), (60, 92), (61, 80)], [14.5, 11.6, 10.8], nm)]
    for s_ in (-1, 1):
        o.append(K.crease(f"M{f(60 + s_ * 9.5)} 84 Q{f(60 + s_ * 7)} 92 {f(60 + s_ * 2.4)} 99", nm, 0.8, 0.4,
                          (-0.7, -0.3) if s_ < 0 else None))
    o.append(K.ao(61.5, 87.5, 12, 4.2, 0.75))
    o.append(K.ao(60, 99.5, 4, 2, 0.45))
    return "".join(o)


BUST_LOOKS = {
    "r1": dict(shape="square", jaw=1.08, eye="sharp", brow="thick", nose="broad", mouth="stern", age=0.45,
               beard="goatee", cheek=0.75, turn=-0.08, gaze=(0.3, 0.0)),
    "r3": dict(shape="oval", fem=True, eye="almond", eye_size=1.05, lid="double", brow="thin", nose="straight", mouth="calm", lip=0.45,
               age=0.18, cheek=0.55, turn=-0.1, gaze=(0.1, -0.1)),
}


def bust_r1(p):
    K = _bkit(p)
    o = []
    body, bd = _bust_body(K, BRIG)
    o.append(body)
    # armhole/arm folds
    for dd, lo in (("M20 112 C26 124 25 132 27 142", (-1, -0.5)), ("M100 112 C94 124 95 132 93 142", None),
                   ("M12 128 C14 122 17 117 22 113", (-0.8, -0.8)), ("M108 128 C106 122 103 117 98 113", None)):
        o.append(K.crease(dd, BRIG, 1.1, 0.7, lo))
    # vest with green trim + studs
    vest = "M40 100 C46 112 50 126 50 142 H70 C70 126 74 112 80 100 C74 98 66 97 60 97 C54 97 46 98 40 100 Z"
    o.append(K.shaded_path(vest, BRIG.back(0.12), (60, 120), 20, normal=(-0.97, -0.2)))
    o.append(K.studs_in(vest, 38, 100, 82, 142, 5.2, 1.05))
    o.append(K.studs_in(bd, 4, 104, 118, 142, 6.4, 1.15))
    for dd in ("M40 100 C46 112 50 126 50 142", "M80 100 C74 112 70 126 70 142"):
        o.append(f'<path d="{dd}" fill="none" stroke="#18382E" stroke-width="4.2"></path><path d="{dd}" fill="none" stroke="#3E8F7A" stroke-width="2.6"></path>'
                 f'<path d="{dd}" transform="translate(-0.6 -0.6)" fill="none" stroke="#8ED0B8" stroke-width="0.7" stroke-opacity="0.8"></path>')
    # brass pauldron discs
    for cx, rot, mm in ((22, -24, M["brass"]), (98, 24, M["brass"].back(0.15))):
        o.append(f'<g transform="rotate({rot} {cx} 110)">' + K.ball(cx, 110, 11.5, mm, ry=6.6)
                 + f'<ellipse cx="{cx}" cy="110" rx="6.4" ry="3.4" fill="none" stroke="#5A3A10" stroke-width="0.9"></ellipse>'
                 + K.ball(cx, 110, 1.6, M["iron"]) + '</g>')
    # neck + collar
    o.append(_bust_neck(K, M["skin_tan"]))
    o.append(K.ao(61, 99, 13, 4, 0.6))
    o.append('<path d="M47 97.6 L60 114 L73 97.6" fill="none" stroke="#3A0E06" stroke-width="6" stroke-linejoin="round"></path>'
             '<path d="M47 97.6 L60 114 L73 97.6" fill="none" stroke="#EFE8D8" stroke-width="3.2" stroke-linejoin="round"></path>'
             '<path d="M47.6 96.8 L60 112.4" fill="none" stroke="#FFFFFF" stroke-width="1" stroke-opacity="0.7"></path>')
    # gear badge
    o.append("".join(f'<rect x="58.6" y="114.4" width="2.8" height="3.6" fill="#B88A3A" stroke="#3A2408" stroke-width="0.3" transform="rotate({a} 60 124)"></rect>' for a in range(0, 360, 30)))
    o.append(K.ball(60, 124, 7, M["brass"]) + K.ball(60, 124, 3.4, M["lacquer"], outline=False) + '<circle cx="60" cy="124" r="1.4" fill="#9FE3D0"></circle>')
    # head: a square-jawed veteran gunnery sergeant (individual look, not the shared face)
    sk1 = M["skin_tan"]
    o.append(K.ear(81.4, 64, 7.4, sk1.back(0.15)))
    fs, fd = K.face(60, 62.5, 24, sk1, BUST_LOOKS["r1"])
    o.append(fs)
    # hat-brim shadow, hat, goggles
    o.append(f'<path d="M41 47 C50 53 70 53 79 47 L79 53 C70 58 50 58 41 53 Z" fill="#1A0C06" opacity="0.5" filter="url(#{K.p}-soft)"></path>')
    o.append(K.jeonrip(60, 45.5, 26))
    for gx in (51.5, 68.5):
        o.append(K.ball(gx, 38.6, 5.8, M["brass"]))
        gid = K._sphere_grad(gx, 38.6, 3.9, Mat("#3E9C86", "#BFF2E2", "#0A2A24", "#2E6A80", spec="#FFFFFF", spec_op=0.9, gloss=1))
        o.append(f'<circle cx="{gx}" cy="38.6" r="3.9" fill="url(#{gid})" stroke="#1A1006" stroke-width="0.5"></circle>')
    o.append(K.form((57.4, 38.6), (62.6, 38.6), 1.2, 1.2, M["brass"], ao_start=False, outline=False))
    return f"<defs>{K.defs_svg()}</defs>" + "".join(o)


def bust_r2(p):
    K = _bkit(p)
    o = [K.fire_glow(101, 72, 16, 0.9),
         '<path d="M101 60 C106 66 109 70 108.4 75 C107.8 80 104.6 83 101 83 C97.4 83 94.2 80 93.6 75 C93 70 96 66 101 60 Z" fill="#A8E6FF"></path>'
         '<circle cx="98.6" cy="75" r="1" fill="#12222E"></circle><circle cx="103.4" cy="75" r="1" fill="#12222E"></circle>']
    body, bd = _bust_body(K, BLUE, sw=58, top=95, neck_w=15)
    o.append(body)
    # trapezius + pecs + sternum
    o.append(K.form((48, 92), (24, 102), 6, 9, BLUE, ao_start=False))
    o.append(K.form((72, 92), (96, 102), 6, 9, BLUE.back(0.12), ao_start=False))
    for s in (-1, 1):
        px = 60 + s * 17
        o.append(K.crease(f"M{f(px - s * 15)} 112 Q{f(px)} 126 {f(60 + s * 1.5)} 122", BLUE, 1.6, 0.75))
        o.append(f'<ellipse cx="{f(px - 3)}" cy="113" rx="11" ry="5" fill="{BLUE.light if s < 0 else BLUE.base}" opacity="{0.5 if s < 0 else 0.2}" filter="url(#{K.p}-soft)"></ellipse>')
    o.append(K.crease("M60 112 L60 142", BLUE, 1.1, 0.6))
    o.append(K.crease("M24 120 C24 128 26 134 28 142", BLUE, 1.2, 0.6, (-0.8, 0)))
    # tiger sash
    sash = "M70 96 C84 98 98 104 106 116 L120 142 H96 C92 128 86 116 66 104 Z"
    o.append(K.shaded_path(sash, TIGER, (92, 120), 22, normal=(-0.6, -0.8)))
    stripes = "".join(f'<path d="M{x - 5} {y + 4} Q{x} {y - 2} {x + 6} {y + 1} Q{x + 1} {y + 3} {x - 5} {y + 4} Z" fill="#1A0E06"></path>'
                      for x, y in ((76, 100), (84, 105), (92, 112), (98, 120), (103, 129), (108, 137)))
    o.append(K.clip_overlay(sash, stripes))
    o.append(K.crease("M70 96 C84 98 98 104 106 116", TIGER, 0.8, 0.5, (-0.6, -0.8)))
    # neck
    nm = BLUE.back(0.12)
    o.append(K.tube([(60, 108), (60, 96), (61, 83)], [25, 17, 14.5], nm))
    for s_ in (-1, 1):
        o.append(K.crease(f"M{f(60 + s_ * 12)} 82 Q{f(60 + s_ * 8)} 94 {f(60 + s_ * 2.5)} 105", nm, 1.5, 0.7,
                          (-0.8, -0.3) if s_ < 0 else None))
    o.append(K.ao(61, 86, 15, 4.5, 0.6))
    o.append(K.ao(60, 105, 6, 3, 0.55))
    # bead necklace + fang
    for i in range(15):
        t = i / 14
        x, y = 40 + 40 * t, 98 + 13 * math.sin(math.pi * t)
        o.append(K.ball(x, y, 2.6, M["lacquer"] if i % 2 else M["horn"]))
    o.append(K.dome("M56.6 111 C57 118 59 122 60 124.6 C61 122 63 118 63.4 111 Z", 59, 115, 6, Mat("#E8DCC0", "#FFFBF0", "#5A4A30")))
    o.append(_dokkaebi_head(K, 60, 60, 25, 1, var=1))
    o.append('<circle cx="24.6" cy="62" r="3.8" fill="none" stroke="#E2B13C" stroke-width="1.7"></circle>')
    return f"<defs>{K.defs_svg()}</defs>" + "".join(o)


def bust_r3(p):
    """오방 신당 무당: composed woman in layered 무복, braided 얹은머리 with an 오방색 ribbon, sashes and a 방울."""
    K = _bkit(p)
    o = []
    hair = Mat("#120D0A", "#2A221C", "#040302", "#16181E", spec="#6E7486", spec_op=0.12, gloss=0.2)
    robe = Mat("#2E4F7E", "#6E94C4", "#0A1426", "#4A5E80", spec_op=0.22, gloss=0.25)  # 쾌자
    # hair falling behind the neck
    o.append(K.shaded_path("M38 62 C37 80 43 92 52 96 H70 C78 92 84 80 83 62 Z", hair, (60, 80), 22, normal=(-0.9, -0.3)))
    body, bd = _bust_body(K, robe, sw=50)
    o.append(body)
    for dd, lo in (("M20 118 C24 126 23 134 25 142", (-0.8, -0.5)), ("M100 118 C96 126 97 134 95 142", None),
                   ("M28 106 C32 112 31 118 33 124", (-0.8, -0.4)), ("M92 106 C88 112 89 118 87 124", None)):
        o.append(K.crease(dd, robe, 1.1, 0.7, lo))
    # white 저고리 panel under the open 쾌자
    panel = "M44 99 C48 112 50 126 50 142 H70 C70 126 72 112 76 99 C70 97 66 96 60 96 C54 96 50 97 44 99 Z"
    o.append(K.shaded_path(panel, M["white_hemp"], (60, 120), 18, normal=(-0.97, -0.2)))
    o.append(K.crease("M56 108 C57 118 57 130 57 142", M["white_hemp"], 0.8, 0.55, (-0.6, 0)))
    # slim neck, shaded under the jaw
    sk3 = M["skin_fair"]
    nm = Mat(shade(sk3.base, -0.1), sk3.base, mix(sk3.core, sk3.base, 0.45), sk3.bounce, spec_op=0.0, gloss=0.1)
    o.append(K.tube([(60, 97), (60, 91), (60.5, 82)], [10.5, 8.6, 8.2], nm))
    o.append(K.ao(61.5, 90.5, 10, 3.6, 0.7))
    # red-and-white crossed collar
    o.append('<path d="M49 97 L60 111 L71 97" fill="none" stroke="#3A0C06" stroke-width="5.4" stroke-linejoin="round"></path>'
             '<path d="M49 97 L60 111 L71 97" fill="none" stroke="#C8432B" stroke-width="3.6" stroke-linejoin="round"></path>'
             '<path d="M51 95.6 L60 107.4 L69 95.6" fill="none" stroke="#F6F0DE" stroke-width="1.4" stroke-linejoin="round"></path>')
    # 오방색 sashes falling from the shoulder
    for i, c in enumerate(("#2F6E8E", "#C8432B", "#E2B13C", "#EFE8D8", "#2A2622")):
        x0, x1 = 28 + i * 3.6, 52 + i * 2.6
        o.append(f'<path d="M{f(x0)} 104 C{f(x0 + 5)} 118 {f(x1 - 8)} 130 {f(x1)} 144" fill="none" stroke="#120D08" stroke-width="3.5" stroke-opacity="0.8"></path>'
                 f'<path d="M{f(x0)} 104 C{f(x0 + 5)} 118 {f(x1 - 8)} 130 {f(x1)} 144" fill="none" stroke="{c}" stroke-width="2.7"></path>')
    # brass 방울 cluster hung at the chest
    o.append('<path d="M73 112 L76 121" stroke="#8A2E1E" stroke-width="1.2"></path>')
    for bx, by, r in ((72.5, 125, 3.2), (79.5, 124.5, 3.2), (76, 130, 3.4), (69.5, 131, 2.8), (83, 130.5, 2.8)):
        o.append(K.ball(bx, by, r, M["brass"]))
        o.append(f'<path d="M{f(bx - r * 0.5)} {f(by + r * 0.45)} h{f(r)}" stroke="#2A1808" stroke-width="0.7"></path>')
    # face, centre-parted hair, braided crown with an 오방색 ribbon
    fs, fd = K.face(60, 64.5, 23.5, sk3, BUST_LOOKS["r3"])
    o.append(fs)
    o.append(K.shaded_path("M36 64 C34 48 44 40 60 40 C76 40 86 48 84 64 C80 54 70 48.5 60.5 47.5 C50 48.5 40 54 36 64 Z", hair, (60, 48), 24, normal=(-0.8, -0.6)))
    o.append(f'<path d="M42 50 C50 56 70 56 78 50 L78 54 C70 60 50 60 42 54 Z" fill="#1A0C06" opacity="0.3" filter="url(#{K.p}-soft)"></path>')
    for i in range(9):  # braid wound over the crown
        x = 36.5 + i * 5.9
        y = 50 - 10 * (1 - ((x - 60) / 26) ** 2)
        o.append(K.ball(x, y, 4.6, hair, ry=3.8))
    for i, c in enumerate(("#2F6E8E", "#C8432B", "#E2B13C", "#EFE8D8", "#2A2622")):
        o.append(f'<path d="M81 44 C87 {f(52 + i)} {f(85 + i * 1.6)} {f(66 + i * 3)} {f(88 + i * 2.2)} {f(80 + i * 3)}" fill="none" stroke="#120D08" stroke-width="2.3" stroke-opacity="0.75"></path>'
                 f'<path d="M81 44 C87 {f(52 + i)} {f(85 + i * 1.6)} {f(66 + i * 3)} {f(88 + i * 2.2)} {f(80 + i * 3)}" fill="none" stroke="{c}" stroke-width="1.5"></path>')
    o.append(K.ball(81, 44, 3, M["brass"]))
    return f"<defs>{K.defs_svg()}</defs>" + "".join(o)


BUSTS = {"r1": bust_r1, "r2": bust_r2, "r3": bust_r3}
