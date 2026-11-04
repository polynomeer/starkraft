"""R1 관상감 공방 people — pre-rendered-sprite style figures built on figure_kit (Kit prefix r1k).

Every figure is drawn with feet at (0, 0), ~150 units tall to the crown (about 6.8 heads).
Light from the top-left. Far-side limbs use Mat.back(). Call KK.defs_svg() once, after all drawing.
"""
import math

from figure_kit import Kit, Mat, MATS, mix, shade, f, P

KK = Kit("r1k")
M = MATS

RED_COAT = Mat("#B8361E", "#EE7A4E", "#3A0A04", "#7A3A44", spec_op=0.22, gloss=0.3)
HEMP = Mat("#D2C8AE", "#FBF4E2", "#4E4634", "#8E96A0", spec_op=0.18, gloss=0.12)
HEMP_DK = Mat("#B8AA88", "#E8DCBE", "#423A28", "#7E8690", spec_op=0.15, gloss=0.12)
FELT = Mat("#26221F", "#5A524A", "#050404", "#3E4656", spec="#E8E0D0", spec_op=0.3, gloss=0.35)
SASH = Mat("#2E3E78", "#6A7EC4", "#0A0E26", "#4A5878", spec_op=0.25, gloss=0.3)
JADE = Mat("#2E7A66", "#6CC0A6", "#08201A", "#3A5A68", spec_op=0.3, gloss=0.35)
SKIN = M["skin"]


def _defs_once():
    if getattr(KK, "_r1_done", False):
        return
    KK._r1_done = True
    # reusable objectBoundingBox sphere gradients for studs / rivets (cheap, used in bulk)
    for gid, m in (("stud", M["brass"]), ("studi", M["iron"])):
        KK._defs.append(
            f'<radialGradient id="r1k-{gid}" cx="0.55" cy="0.58" r="0.62" fx="0.3" fy="0.26">'
            f'<stop offset="0" stop-color="#FFFBEA"></stop><stop offset="0.22" stop-color="{m.light}"></stop>'
            f'<stop offset="0.55" stop-color="{m.base}"></stop><stop offset="0.85" stop-color="{m.core}"></stop>'
            f'<stop offset="1" stop-color="{m.bounce}"></stop></radialGradient>')
    KK._defs.append('<radialGradient id="r1k-ember" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFF6C8"></stop>'
                    '<stop offset="0.35" stop-color="#FFB040"></stop><stop offset="1" stop-color="#E04A1A" stop-opacity="0"></stop></radialGradient>')
    KK._defs.append('<radialGradient id="r1k-tyre" cx="0.42" cy="0.38" r="0.62" fx="0.25" fy="0.2"><stop offset="0.8" stop-color="#1A1E22"></stop>'
                    '<stop offset="0.88" stop-color="#9AA6AE"></stop><stop offset="0.94" stop-color="#4A5258"></stop><stop offset="1" stop-color="#0E1012"></stop></radialGradient>')
    KK._defs.append('<linearGradient id="r1k-felloe" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#C08A58"></stop>'
                    '<stop offset="0.35" stop-color="#8A5A34"></stop><stop offset="0.7" stop-color="#3E2414"></stop><stop offset="1" stop-color="#1A0E06"></stop></linearGradient>')
    KK._defs.append('<radialGradient id="r1k-hubao" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#000" stop-opacity="0.6"></stop>'
                    '<stop offset="0.7" stop-color="#3A2414" stop-opacity="0.2"></stop><stop offset="1" stop-color="#000" stop-opacity="0.7"></stop></radialGradient>')
    KK._defs.append('<radialGradient id="r1k-lens" cx="0.4" cy="0.35" r="0.7"><stop offset="0" stop-color="#E8FFF8"></stop>'
                    '<stop offset="0.45" stop-color="#7CC4AE"></stop><stop offset="1" stop-color="#14403A"></stop></radialGradient>')
    KK._defs.append('<filter id="r1k-glow" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="2.2" result="b"></feGaussianBlur>'
                    '<feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>')


def stud(x, y, r, iron=False):
    _defs_once()
    return (f'<circle cx="{f(x + r * 0.25)}" cy="{f(y + r * 0.35)}" r="{f(r)}" fill="#0A0604" opacity="0.45"></circle>'
            f'<circle cx="{f(x)}" cy="{f(y)}" r="{f(r)}" fill="url(#r1k-{"studi" if iron else "stud"})" stroke="#2A1A08" stroke-width="0.35"></circle>')


def _soft(x, y, rx, ry, col, op):
    return f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rx)}" ry="{f(ry)}" fill="{col}" opacity="{f(op)}" filter="url(#r1k-soft)"></ellipse>'


def _path(d, fill="none", **a):
    at = " ".join(f'{k.replace("_", "-")}="{f(v) if isinstance(v, float) else v}"' for k, v in a.items())
    return f'<path d="{d}" fill="{fill}" {at}></path>'


def _lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


# ------------------------------------------------------------------ props
def matchlock(x0, y0, x1, y1, lit=True, s=1.0):
    """화승총 from butt (x0,y0) to muzzle (x1,y1): shaded wood stock, iron barrel, brass bands, serpentine, glowing match."""
    _defs_once()
    a, b = (x0, y0), (x1, y1)
    L = math.hypot(x1 - x0, y1 - y0) or 1
    ux, uy = (x1 - x0) / L, (y1 - y0) / L
    nx, ny = -uy, ux
    out = []
    # stock: club-shaped butt, then a fore-stock under the barrel
    butt_end = _lerp(a, b, 0.3)
    out.append(KK.form((a[0] - ux * 2, a[1] - uy * 2), butt_end, 3.6 * s, 2.0 * s, M["wood"], [(0.05, 0.8 * s, -1)], ao_start=False))
    out.append(KK.form(butt_end, _lerp(a, b, 0.86), 1.9 * s, 1.5 * s, M["wood"], ao_start=False))
    # barrel
    out.append(KK.form(_lerp(a, b, 0.26), b, 1.2 * s, 1.05 * s, M["iron"], ao_start=False))
    out.append(KK.ball(x1, y1, 1.05 * s, M["iron"]))
    out.append(f'<circle cx="{f(x1)}" cy="{f(y1)}" r="{f(0.55 * s)}" fill="#050403"></circle>')
    # brass bands
    for t in (0.42, 0.62, 0.82):
        c = _lerp(a, b, t)
        out.append(KK.form((c[0] - ux * 0.9 * s, c[1] - uy * 0.9 * s), (c[0] + ux * 0.9 * s, c[1] + uy * 0.9 * s), 1.9 * s, 1.9 * s,
                           M["brass"], ao_start=False, spec=False))
    # lock plate + serpentine
    lp = _lerp(a, b, 0.3)
    out.append(KK.form((lp[0] - nx * 0.6 * s, lp[1] - ny * 0.6 * s), (lp[0] + ux * 5 * s - nx * 0.6 * s, lp[1] + uy * 5 * s - ny * 0.6 * s),
                       1.6 * s, 1.6 * s, M["brass"], ao_start=False))
    sp = (lp[0] + nx * 1.6 * s, lp[1] + ny * 1.6 * s)
    out.append(_path(f"M{P(sp)} q{f(nx * 2.4 * s - ux * 1.6 * s)} {f(ny * 2.4 * s - uy * 1.6 * s)} {f(nx * 3.6 * s + ux * 1.4 * s)} {f(ny * 3.6 * s + uy * 1.4 * s)}",
                     stroke=M["brass"].core, stroke_width=1.1 * s, stroke_linecap="round"))
    # trigger
    tg = _lerp(a, b, 0.24)
    out.append(_path(f"M{P((tg[0] - nx * 1.4 * s, tg[1] - ny * 1.4 * s))} q{f(-nx * 2.4 * s)} {f(-ny * 2.4 * s)} {f(-nx * 2.2 * s + ux * 2 * s)} {f(-ny * 2.2 * s + uy * 2 * s)}",
                     stroke="#1A1008", stroke_width=0.8 * s))
    if lit:
        mx, my = sp[0] + nx * 3.4 * s + ux * 1.4 * s, sp[1] + ny * 3.4 * s + uy * 1.4 * s
        out.append(_path(f"M{f(mx)} {f(my)} q{f(-4 * s)} {f(4 * s)} {f(-2 * s)} {f(9 * s)} q{f(2 * s)} {f(5 * s)} {f(-1 * s)} {f(10 * s)}",
                         stroke="#7A5A34", stroke_width=0.9 * s, stroke_linecap="round"))
        out.append(f'<circle cx="{f(mx)}" cy="{f(my)}" r="{f(4.2 * s)}" fill="url(#r1k-ember)" filter="url(#r1k-glow)"></circle>')
        out.append(f'<circle cx="{f(mx)}" cy="{f(my)}" r="{f(0.9 * s)}" fill="#FFF8D8"></circle>')
        out.append(_path(f"M{f(mx)} {f(my - 2 * s)} q{f(-3 * s)} {f(-5 * s)} 0 {f(-10 * s)} q{f(3 * s)} {f(-4 * s)} {f(-1 * s)} {f(-10 * s)}",
                         stroke="#C8C0B0", stroke_width=1.8 * s, opacity="0.3", filter="url(#r1k-soft)"))
    return "".join(out)


def jeonrip(y=0.0, x=0.0, s=1.0, tilt=0.0):
    """전립 (felt military hat): wide shaded brim, domed crown, brass finial, red horsehair tassel, chin cord.
    (x, y) is the brim centre."""
    _defs_once()
    out = []
    # brim: dark felt disc seen from slightly above, with underside lip at the front and a lit rim on the top-left
    out.append(_soft(x + 3 * s, y + 3.2 * s, 17 * s, 4.4 * s, "#000", 0.35))
    out.append(KK.ball(x, y, 17.5 * s, FELT, ry=5.6 * s))
    out.append(_path(f"M{f(x - 17 * s)} {f(y + 0.8 * s)} Q{f(x)} {f(y + 8.6 * s)} {f(x + 17 * s)} {f(y + 0.8 * s)} Q{f(x)} {f(y + 5.2 * s)} {f(x - 17 * s)} {f(y + 0.8 * s)} Z",
                     "#050404", opacity="0.85"))
    out.append(_path(f"M{f(x - 16 * s)} {f(y - 1.2 * s)} Q{f(x - 10 * s)} {f(y - 5.2 * s)} {f(x + 2 * s)} {f(y - 5.4 * s)}",
                     stroke="#A89C8C", stroke_width=0.9 * s, opacity="0.75", stroke_linecap="round"))
    # brim band (red cloth) and crown
    out.append(KK.ball(x + 0.4 * s, y - 6.4 * s, 9.2 * s, FELT, ry=8.6 * s))
    out.append(KK.form((x - 8.4 * s, y - 2.6 * s), (x + 8.6 * s, y - 2.2 * s), 1.6 * s, 1.6 * s, RED_COAT, ao_start=False, spec=False))
    out.append(_soft(x - 3.6 * s, y - 10.6 * s, 3 * s, 2 * s, "#E8E0D0", 0.35))
    # brass finial + red tassel spilling over the crown
    out.append(KK.ball(x + 0.6 * s, y - 15.4 * s, 2.0 * s, M["brass"]))
    for k in range(9):
        ang = math.radians(-150 + k * 16)
        ln = (9 + (k % 3) * 2.4) * s
        ex, ey = x + 0.6 * s + math.cos(ang) * ln, y - 15 * s + math.sin(ang) * ln * 0.45 + ln * 0.75
        col = mix("#E8432B", "#7A1408", (k % 3) / 3)
        out.append(_path(f"M{f(x + 0.6 * s)} {f(y - 15 * s)} Q{f((x + ex) / 2)} {f(y - 17 * s)} {f(ex)} {f(ey)}", stroke=col, stroke_width=1.5 * s,
                         stroke_linecap="round"))
    out.append(_path(f"M{f(x - 2 * s)} {f(y - 14.8 * s)} Q{f(x - 5 * s)} {f(y - 15.6 * s)} {f(x - 8 * s)} {f(y - 8 * s)}", stroke="#FFA078",
                     stroke_width=0.7 * s, opacity="0.8"))
    out.append(_path(f"M{f(x + 0.6 * s)} {f(y - 15 * s)} m{f(-1.8 * s)} 0 a{f(1.8 * s)} {f(1.8 * s)} 0 0 1 {f(3.6 * s)} 0", stroke="#FFF2C0",
                     stroke_width=0.5 * s, opacity="0.8"))
    g = "".join(out)
    return f'<g transform="rotate({f(tilt)} {f(x)} {f(y)})">{g}</g>' if tilt else g


def jige(load=True):
    """지게 back frame with a lashed load of goblin-fire stone (behind the body; feet at origin)."""
    _defs_once()
    out = []
    W = M["wood"]
    for x0, x1, back in ((-15, -17, False), (14, 15, True)):
        out.append(KK.form((x0 + 1, -158), (x1, -14), 2.4, 2.8, W.back() if back else W, ao_start=False))
    for y in (-136, -112, -92):
        out.append(KK.form((-16, y), (15, y + 1), 1.8, 1.8, W.back(0.12), ao_start=False, spec=False))
    # 지게 prop arms (the forks at the top)
    out.append(KK.form((-16, -150), (-26, -168), 1.8, 1.4, W))
    out.append(KK.form((15, -150), (24, -166), 1.8, 1.4, W.back()))
    if load:
        out.append(f'<ellipse cx="0" cy="-160" rx="26" ry="18" fill="url(#r1-ore-glow)" filter="url(#r1-glow-l)"></ellipse>')
        sack = Mat("#7A6440", "#B89E6E", "#22180C", "#5A5A60", spec_op=0.15, gloss=0.1)
        out.append(KK.ball(-1, -148, 19, sack, ry=11))
        out.append(KK.folds((-18, -148), (17, -149), 9, 9, sack, n=4))
        for dx, h, a in ((-11, 18, -24), (-4, 26, -6), (4, 22, 10), (11, 15, 28), (-8, 13, -40), (1, 16, 2)):
            bx, by = dx, -156
            tx, ty = bx + math.sin(math.radians(a)) * h, by - math.cos(math.radians(a)) * h
            out.append(f'<polygon points="{f(bx - 3.2)},{f(by + 2)} {f(tx)},{f(ty)} {f(bx + 0.4)},{f(by + 3)}" fill="url(#r1-ore)" stroke="#2F6F62" stroke-width="0.5"></polygon>')
            out.append(f'<polygon points="{f(bx + 0.4)},{f(by + 3)} {f(tx)},{f(ty)} {f(bx + 3.4)},{f(by + 2)}" fill="url(#r1-ore-s)" stroke="#2F6F62" stroke-width="0.5"></polygon>')
            out.append(_path(f"M{f(bx - 1)} {f(by)} L{f(tx)} {f(ty)}", stroke="#F2FFFB", stroke_width=0.5, opacity="0.7"))
        rope = Mat("#A08A5A", "#E0CC98", "#2A2010", "#6A6460", spec_op=0.2, gloss=0.2)
        for y0 in (-150, -142):
            out.append(KK.form((-19, y0), (18, y0 - 1), 1.1, 1.1, rope, ao_start=False, spec=False))
            out.append(KK.bands((-19, y0), (18, y0 - 1), 1.1, 1.1, rope, n=7))
    return "".join(out)


def pick(hand_a, head_end, s=1.0):
    """곡괭이: shaded wooden helve from the hands to the head end, forged iron head across it."""
    out = [KK.form(hand_a, head_end, 1.8 * s, 2.1 * s, M["wood"], ao_start=False)]
    ux, uy = head_end[0] - hand_a[0], head_end[1] - hand_a[1]
    L = math.hypot(ux, uy) or 1
    ux, uy = ux / L, uy / L
    nx, ny = -uy, ux
    c = head_end
    out.append(KK.form((c[0] - nx * 13 * s + ux * 2 * s, c[1] - ny * 13 * s + uy * 2 * s), (c[0] + ux * 1 * s, c[1] + uy * 1 * s), 0.8 * s, 2.6 * s, M["iron"], ao_start=False))
    out.append(KK.form((c[0] + ux * 1 * s, c[1] + uy * 1 * s), (c[0] + nx * 9 * s + ux * 3 * s, c[1] + ny * 9 * s + uy * 3 * s), 2.6 * s, 1.0 * s, M["iron"], ao_start=False))
    out.append(KK.ball(c[0] + ux * 1 * s, c[1] + uy * 1 * s, 3.0 * s, M["iron"]))
    return "".join(out)


# ------------------------------------------------------------------ body pieces
def coat_skirt(m, waist_y=-80, hem_y=-40, ww=14, hw=21, studs=True, split=True):
    """Long armoured coat skirt flaring from waist to hem, front split, quilting rows, studs."""
    _defs_once()
    d = (f"M{f(-ww)} {f(waist_y)} C{f(-ww - 3)} {f(waist_y + 14)} {f(-hw - 1)} {f(hem_y - 12)} {f(-hw)} {f(hem_y)} "
         f"Q{f(-hw * 0.5)} {f(hem_y + 4)} {f(-1.5)} {f(hem_y + 1)} L{f(-1)} {f(waist_y + 18)} L{f(1)} {f(waist_y + 18)} L{f(1.5)} {f(hem_y + 1)} "
         f"Q{f(hw * 0.5)} {f(hem_y + 4)} {f(hw)} {f(hem_y - 1)} C{f(hw + 1)} {f(hem_y - 12)} {f(ww + 3)} {f(waist_y + 14)} {f(ww)} {f(waist_y)} Z")
    gid = KK._cyl_grad((0, (waist_y + hem_y) / 2), (-1, -0.1), hw * 1.1, m)
    out = [f'<path d="{d}" fill="url(#{gid})" stroke="#120D08" stroke-width="0.9" stroke-opacity="0.75"></path>']
    inner = []
    # vertical fold shadows
    for x, op in ((-12, 0.3), (-5, 0.25), (7, 0.35), (14, 0.4)):
        inner.append(_path(f"M{f(x * 0.7)} {f(waist_y + 6)} Q{f(x * 1.1)} {f((waist_y + hem_y) / 2)} {f(x * 1.35)} {f(hem_y + 2)}", stroke=m.core,
                           stroke_width=2.2, opacity=f(op), filter="url(#r1k-soft)"))
        inner.append(_path(f"M{f(x * 0.7 - 1.6)} {f(waist_y + 6)} Q{f(x * 1.1 - 1.6)} {f((waist_y + hem_y) / 2)} {f(x * 1.35 - 1.8)} {f(hem_y + 2)}",
                           stroke=m.light, stroke_width=0.8, opacity=f(op * 0.9)))
    # quilting rows (두정갑 panels) + studs
    rows = 4
    for r in range(rows):
        yy = waist_y + 8 + r * (hem_y - waist_y - 8) / rows
        inner.append(_path(f"M{f(-hw - 2)} {f(yy + 2)} Q0 {f(yy + 5)} {f(hw + 2)} {f(yy + 2)}", stroke=m.core, stroke_width=0.7, opacity="0.6"))
        if studs:
            for k in range(-4, 5):
                if k == 0:
                    continue
                xx = k * (ww + (hw - ww) * (r + 0.5) / rows) / 4.4
                inner.append(stud(xx, yy + 5 + 3 * (xx / hw) ** 2, 0.95))
    # hem fur-ish trim (dark) and darker AO under the belt
    inner.append(_path(f"M{f(-hw - 2)} {f(hem_y - 1)} Q{f(-hw * 0.5)} {f(hem_y + 3)} 0 {f(hem_y + 1)} Q{f(hw * 0.5)} {f(hem_y + 3)} {f(hw + 2)} {f(hem_y - 2)}",
                       stroke="#1A0804", stroke_width=2.6, opacity="0.7"))
    inner.append(_soft(0, waist_y + 3, ww + 4, 4, "#0A0604", 0.55))
    out.append(KK.clip_overlay(d, "".join(inner)))
    return "".join(out)


def brigandine_torso(neck=(0, -122), waist=(0, -80), sw=16.5, ww=13.5, m=RED_COAT):
    """두정갑 upper body: shaded torso + plate rows with per-stud specular + green collar + front closure."""
    t, d = KK.torso(neck, waist, sw, ww, m, chest=0.6)
    inner = []
    h = waist[1] - neck[1]
    for r in range(6):
        yy = neck[1] + 8 + r * (h - 10) / 6
        inner.append(_path(f"M{f(-sw - 2)} {f(yy + 3)} Q0 {f(yy + 6)} {f(sw + 2)} {f(yy + 3)}", stroke=m.core, stroke_width=0.7, opacity="0.55"))
        for k in range(-5, 6):
            if k == 0:
                continue
            xx = k * sw / 5.6
            inner.append(stud(xx, yy + 5 + 2.4 * (xx / sw) ** 2, 1.0))
    # front closure strip and side shading under arms
    inner.append(_path(f"M0 {f(neck[1] + 2)} L0 {f(waist[1])}", stroke=m.core, stroke_width=2.4, opacity="0.7"))
    inner.append(_path(f"M-1 {f(neck[1] + 2)} L-1 {f(waist[1])}", stroke=m.light, stroke_width=0.6, opacity="0.6"))
    inner.append(_soft(sw - 1, neck[1] + h * 0.3, 4, h * 0.25, "#0A0604", 0.5))
    inner.append(_soft(-sw + 1, neck[1] + h * 0.3, 3, h * 0.22, "#0A0604", 0.3))
    t += KK.clip_overlay(d, "".join(inner))
    # collar (깃) in dancheong green
    nx, ny = neck
    t += KK.form((nx - 8, ny - 1), (nx + 1, ny + 12), 2.6, 2.2, JADE, ao_start=False)
    t += KK.form((nx + 8, ny - 1), (nx + 1, ny + 12), 2.6, 2.2, JADE.back(0.15), ao_start=False)
    # shoulder guards (어깨 철갑) — brass-edged pauldron caps
    for sx, back in ((-sw + 0.5, False), (sw - 0.5, True)):
        mm = m.back() if back else m
        t += KK.ball(nx + sx, ny + 6, 7.4, mm, ry=6.2)
        t += _path(f"M{f(nx + sx - 7)} {f(ny + 8)} Q{f(nx + sx)} {f(ny + 14)} {f(nx + sx + 7)} {f(ny + 8)}", stroke=M["brass"].base, stroke_width=1.3)
        for k in range(3):
            t += stud(nx + sx - 4 + k * 4, ny + 9.6 + (1 if k == 1 else 0), 0.8)
    return t


def belt(y=-80, w=14.5, knot=True):
    """Indigo 띠 sash with a knot and hanging ends."""
    out = [KK.form((-w - 1, y + 0.5), (w + 1, y - 0.5), 3.0, 3.0, SASH, ao_start=False)]
    if knot:
        out.append(KK.ball(-4, y + 0.5, 3.4, SASH))
        out.append(KK.form((-4, y + 2), (-7, y + 22), 2.0, 2.4, SASH, ao_start=True))
        out.append(KK.form((-3, y + 2), (-1, y + 18), 1.8, 2.2, SASH.back(0.12), ao_start=True))
        out.append(KK.folds((-4, y + 2), (-7, y + 22), 2.0, 2.4, SASH, n=2))
    out.append(stud(8, y, 1.4))
    return "".join(out)


def face_hair(cx, cy, r, beard=True, mustache=True, topknot=False, headband=False, goggles=False):
    """Hair, beard and gear layered over a kit head."""
    out = []
    hair = M["hair"]
    if topknot:
        cap = (f"M{f(cx - r * 0.98)} {f(cy - r * 0.05)} C{f(cx - r * 1.02)} {f(cy - r * 1.05)} {f(cx + r * 1.0)} {f(cy - r * 1.1)} "
               f"{f(cx + r * 0.98)} {f(cy - r * 0.05)} C{f(cx + r * 0.6)} {f(cy - r * 0.45)} {f(cx - r * 0.6)} {f(cy - r * 0.48)} {f(cx - r * 0.98)} {f(cy - r * 0.05)} Z")
        gid = KK._sphere_grad(cx, cy - r * 0.4, r, hair)
        out.append(f'<path d="{cap}" fill="url(#{gid})" stroke="#050302" stroke-width="0.7"></path>')
        for k in range(6):
            x0 = cx - r * 0.8 + k * r * 0.32
            out.append(_path(f"M{f(x0)} {f(cy - r * 0.3)} Q{f(x0 + r * 0.1)} {f(cy - r * 0.9)} {f(cx + r * 0.05)} {f(cy - r * 1.02)}",
                             stroke="#6E6460", stroke_width=0.45, opacity="0.55"))
        out.append(KK.ball(cx + r * 0.1, cy - r * 1.22, r * 0.36, hair))
        out.append(KK.form((cx - r * 0.05, cy - r * 1.42), (cx + r * 0.28, cy - r * 1.62), r * 0.08, r * 0.05, M["brass"], ao_start=False, spec=False))
    if mustache:
        out.append(_path(f"M{f(cx - r * 0.02)} {f(cy + r * 0.48)} q{f(-r * 0.3)} {f(-r * 0.04)} {f(-r * 0.52)} {f(r * 0.24)} "
                         f"q{f(r * 0.26)} {f(-r * 0.06)} {f(r * 0.52)} {f(-r * 0.1)} q{f(r * 0.26)} {f(-r * 0.0)} {f(r * 0.5)} {f(r * 0.12)} "
                         f"q{f(-r * 0.2)} {f(-r * 0.28)} {f(-r * 0.5)} {f(-r * 0.26)} Z", "#1A120C"))
    if beard:
        out.append(_path(f"M{f(cx - r * 0.18)} {f(cy + r * 0.78)} q{f(r * 0.2)} {f(r * 0.7)} {f(r * 0.06)} {f(r * 0.86)} "
                         f"q{f(r * 0.18)} {f(-r * 0.3)} {f(r * 0.22)} {f(-r * 0.88)} Z", "#1A120C"))
        out.append(_path(f"M{f(cx - r * 0.06)} {f(cy + r * 0.86)} q{f(r * 0.06)} {f(r * 0.4)} {f(r * 0.04)} {f(r * 0.6)}", stroke="#5A4A40",
                         stroke_width=0.4, opacity="0.7"))
    if headband:
        out.append(KK.form((cx - r * 1.0, cy - r * 0.42), (cx + r * 1.0, cy - r * 0.48), r * 0.15, r * 0.15, HEMP, ao_start=False))
        out.append(KK.form((cx + r * 0.95, cy - r * 0.45), (cx + r * 1.6, cy - r * 0.05), r * 0.12, r * 0.08, HEMP.back(0.1), ao_start=False, spec=False))
        out.append(KK.form((cx + r * 0.95, cy - r * 0.42), (cx + r * 1.45, cy + r * 0.25), r * 0.11, r * 0.07, HEMP.back(0.15), ao_start=False, spec=False))
    if goggles:
        _defs_once()
        out.append(_path(f"M{f(cx - r * 0.98)} {f(cy - r * 0.62)} Q{f(cx)} {f(cy - r * 0.9)} {f(cx + r * 0.98)} {f(cy - r * 0.66)}",
                         stroke="#3A2414", stroke_width=r * 0.14))
        for s in (-1, 1):
            gx, gy = cx + s * r * 0.36, cy - r * 0.68
            out.append(KK.ball(gx, gy, r * 0.27, M["brass"]))
            out.append(f'<circle cx="{f(gx)}" cy="{f(gy)}" r="{f(r * 0.17)}" fill="url(#r1k-lens)"></circle>')
            out.append(f'<circle cx="{f(gx - r * 0.06)}" cy="{f(gy - r * 0.07)}" r="{f(r * 0.05)}" fill="#FFFFFF" opacity="0.9"></circle>')
    return "".join(out)


# ------------------------------------------------------------------ individual faces
# Base look per unit type; vary() derives a deterministic per-instance variant so squads never look cloned.
UNIT_LOOKS = {
    "gunner": dict(skin="skin_tan", shape="square", eye="sharp", brow="thick", nose="straight", mouth="stern",
                   age=0.35, beard="goatee", cheek=0.6),
    "artisan": dict(skin="skin_weathered", shape="round", eye="narrow", brow="straight", nose="flat", mouth="smile",
                    age=0.4, beard="mustache", cheek=0.4),
    "crew": dict(skin="skin_ruddy", shape="long", eye="droopy", brow="worried", nose="broad", mouth="grin",
                 age=0.3, beard="stubble", cheek=0.5),
    "pilot": dict(skin="skin_fair", shape="heart", eye="round", brow="arched", nose="small", mouth="grin",
                  age=0.05, beard=None, cheek=0.3),
}
_SHAPES = ("oval", "long", "square", "round", "heart", "diamond")
_EYES = ("almond", "narrow", "round", "droopy", "sharp")
_BROWS = ("straight", "arched", "thick", "thin", "angry")
_NOSES = ("straight", "broad", "small", "flat", "hooked")
_BEARDS = (None, "stubble", "goatee", "mustache", "full")
_SKINS = ("skin", "skin_tan", "skin_weathered", "skin_ruddy", "skin_fair")


def vary(kind, seed=0):
    """Deterministic variant of a unit type's look; seed 0 returns the base look."""
    lk = dict(UNIT_LOOKS[kind])
    if seed:
        import random
        r = random.Random(seed * 7919 + len(kind))
        lk["shape"] = r.choice(_SHAPES)
        lk["eye"] = r.choice(_EYES)
        lk["brow"] = r.choice(_BROWS)
        lk["nose"] = r.choice(_NOSES)
        lk["skin"] = r.choice(_SKINS)
        lk["age"] = max(0.0, min(0.9, lk["age"] + r.uniform(-0.25, 0.35)))
        lk["jaw"] = r.uniform(0.9, 1.12)
        if kind != "pilot":
            lk["beard"] = r.choice(_BEARDS)
    return lk


def human_head(cx=0.0, cy=-136.0, r=10.5, turn=-0.15, look=None, **hair):
    """Individual kit face + hair/gear. look: K.face look dict (may carry 'skin' material name)."""
    lk = dict(look or UNIT_LOOKS["gunner"])
    skin = M[lk.pop("skin", "skin")] if isinstance(lk.get("skin", "skin"), str) else lk.pop("skin")
    lk.setdefault("turn", turn)
    out = [KK.ao(cx + 2, cy + r * 1.05, r * 0.9, r * 0.35, 0.6)]  # under-jaw occlusion on the neck
    h, _ = KK.face(cx, cy, r, skin, lk)
    out.append(h)
    hair = dict(hair)
    hair["beard"] = False  # facial hair now comes from the look
    hair["mustache"] = False
    out.append(face_hair(cx + lk["turn"] * r * 0.28, cy, r, **hair))
    return "".join(out)


def shin_cover(hip, knee, ankle, t0, w0, w1, m, n=5):
    """Wrap/boot shaft that follows the same spline as a kit leg (so it sits ON the shin, not beside it).
    t0: start along the whole leg (0 hip .. 0.5 knee .. 1 ankle); w0/w1: half widths at start/end."""
    S = KK._catmull([hip, knee, ankle], 8)
    sub = [(x, y) for (x, y, t) in S if t >= t0]
    step = max(1, (len(sub) - 1) // (n - 1))
    pts = sub[::step]
    if pts[-1] != sub[-1]:
        pts.append(sub[-1])
    k = len(pts)
    return KK.tube(pts, [w0 + (w1 - w0) * i / max(1, k - 1) for i in range(k)], m, n_per=3, chunks_per_seg=1)


def legs_hemp(stance=1.0, trouser=HEMP, wrap=HEMP, boot=None, walk=0.0):
    """Both legs in baggy 바지 with 행전 wraps. stance scales feet spread; walk shifts the stride."""
    boot = boot or M["leather_dark"]
    out = [KK.cast_shadow(4, 0, 26, 7)]
    out.append(KK.leg((6, -78), (10 + walk * 4, -42), (11 + walk * 8 * stance, -7), trouser, w=7.2, trouser=trouser, wrap=wrap,
                      boot=boot, foot_dir=1, back=True))
    out.append(KK.leg((-6, -78), (-10 - walk * 4, -42), (-12 * stance - walk * 6, -7), trouser, w=7.2, trouser=trouser, wrap=wrap,
                      boot=boot, foot_dir=-1))
    out.append(KK.ao(0, -76, 8, 5, 0.5))
    return "".join(out)


# ------------------------------------------------------------------ figures (feet at origin, ~150 tall)
def gunner(dy=0.0, lit=True, aim=False, seed=0):
    """화승총수: 전립, 붉은 두정갑 with brass studs, indigo sash, 행전 wraps, boots, matchlock in both hands."""
    out = []
    out.append(legs_hemp())
    out.append(coat_skirt(RED_COAT))
    out.append(brigandine_torso())
    out.append(belt())
    out.append(KK.neck((0, -121), (0.5, -128), 4.4, SKIN))
    out.append(human_head(0.5, -138, 10.5, turn=-0.2 if not aim else -0.45, look=vary("gunner", seed)))
    out.append(KK.ao(1, -142, 11, 3, 0.55))
    out.append(jeonrip(-146.8, 0.5))
    if aim:
        # shouldered aim toward screen-left: butt at right shoulder, muzzle far left
        gun_a, gun_b = (14, -116), (-58, -128)
        out.append(KK.arm((14, -113), (18, -96), (6, -111), RED_COAT, w=6.0, sleeve=RED_COAT, cuff=M["black_cloth"], hand="grip",
                          hand_dir=(-1, 0.3), back=True))
        out.append(matchlock(*gun_a, *gun_b, lit=lit))
        out.append(KK.arm((-14, -113), (-26, -104), (-30, -120), RED_COAT, w=6.2, sleeve=RED_COAT, cuff=M["black_cloth"], hand="grip",
                          hand_dir=(-1, -0.6)))
        out.append(KK.ao(10, -116, 6, 4, 0.35))
    else:
        # port arms: butt at the right hip, muzzle up past the left shoulder
        gun_a, gun_b = (19, -62), (-26, -168)
        out.append(KK.arm((14, -113), (21, -90), (14, -76), RED_COAT, w=6.0, sleeve=RED_COAT, cuff=M["black_cloth"], hand="grip",
                          hand_dir=(-0.4, -1), back=True))
        out.append(matchlock(*gun_a, *gun_b, lit=lit))
        out.append(KK.ao(-2, -104, 7, 5, 0.4))
        out.append(KK.arm((-14, -113), (-24, -94), (-6, -104), RED_COAT, w=6.2, sleeve=RED_COAT, cuff=M["black_cloth"], hand="grip",
                          hand_dir=(-0.4, -1)))
    body = "".join(out)
    return f'<g transform="translate(0 {f(dy)})">{body}</g>' if dy else body


def crewman(push=True, seed=0):
    """화차 crew: hemp coat over red, 전립, both arms forward on the cart shafts (facing screen-left)."""
    out = [legs_hemp(stance=1.1, walk=0.8)]
    out.append(KK.arm((14, -113), (4, -98), (-14, -100), HEMP_DK, w=6.0, sleeve=HEMP_DK, cuff=RED_COAT, hand="grip", hand_dir=(-1, 0.2), back=True))
    out.append(coat_skirt(HEMP_DK, studs=False))
    t, _ = KK.torso((0, -122), (0, -80), 16, 13.5, HEMP_DK)
    out.append(t)
    out.append(KK.form((-8, -122), (2, -100), 2.4, 2.0, RED_COAT, ao_start=False))
    out.append(belt(knot=False))
    out.append(KK.neck((0, -121), (-0.5, -128), 4.4, SKIN))
    out.append(human_head(-1, -138, 10.5, turn=-0.55, look=vary("crew", seed)))
    out.append(KK.ao(0, -142, 11, 3, 0.55))
    out.append(jeonrip(-146.8, -1))
    out.append(KK.arm((-14, -113), (-24, -96), (-34, -104), HEMP_DK, w=6.2, sleeve=HEMP_DK, cuff=RED_COAT, hand="grip", hand_dir=(-1, 0.2)))
    return "".join(out)


def artisan(swing=0.0, seed=0):
    """공방 일꾼: 지게 with a glowing ore load, white hemp jeogori, rolled sleeves (bare forearms), headband + goggles,
    상투 topknot, pick. swing<0 raises the pick overhead to strike."""
    out = [jige()]
    out.append(legs_hemp(stance=1.05, trouser=HEMP, wrap=HEMP))
    strike = swing < 0
    if strike:
        ha, hb = (26, -90), (14, -97)
        out.append(KK.arm((14, -113), (25, -106), ha, HEMP, SKIN, w=5.8, cuff=None, hand="grip", hand_dir=(1, 0.6), back=True))
    # jeogori + short skirt panel
    t, d = KK.torso((0, -122), (0, -82), 15.5, 13.5, HEMP)
    out.append(t)
    out.append(KK.clip_overlay(d, _path("M-12 -121 L10 -90", stroke=HEMP.core, stroke_width=1.6, opacity="0.55")
                               + _path("M-12.6 -121 L9.4 -90", stroke=HEMP.light, stroke_width=0.8, opacity="0.8")
                               + _soft(13, -108, 4, 10, "#0A0604", 0.45)))
    out.append(KK.form((-3, -104), (-7, -88), 1.6, 1.8, RED_COAT, ao_start=False))  # 고름 ribbon
    out.append(KK.form((-2, -104), (2, -90), 1.4, 1.6, RED_COAT.back(0.1), ao_start=False))
    # 지게 shoulder straps (멜빵)
    strap = Mat("#8A6A40", "#C8A470", "#22160A", "#5A5250", spec_op=0.2, gloss=0.2)
    out.append(KK.form((-10, -122), (-15, -92), 1.8, 2.0, strap, ao_start=False))
    out.append(KK.form((10, -122), (14, -92), 1.8, 2.0, strap.back(), ao_start=False))
    out.append(belt(y=-82, w=14, knot=True))
    out.append(KK.neck((0, -121), (0.5, -128), 4.2, SKIN))
    out.append(human_head(0.5, -138, 10.2, turn=0.15 if strike else -0.2, look=vary("artisan", seed), topknot=True, headband=True, goggles=True))
    if strike:
        out.append(pick((8, -101), (50, -70)))
        out.append(KK.arm((-14, -113), (-8, -96), hb, HEMP, SKIN, w=6.0, hand="grip", hand_dir=(1, 0.6)))
        out.append(KK.folds((-14, -113), (-8, -96), 6, 4.8, HEMP, n=2))
    else:
        # pick hanging from the far hand, near hand gripping the strap
        out.append(pick((22, -76), (34, -30)))
        out.append(KK.arm((14, -113), (22, -96), (22, -78), HEMP, SKIN, w=5.8, hand="grip", hand_dir=(0.3, 1), back=True))
        out.append(KK.arm((-14, -113), (-22, -96), (-12, -104), HEMP, SKIN, w=6.0, hand="fist", hand_dir=(0.6, -0.5)))
        out.append(KK.folds((-14, -113), (-22, -96), 6, 4.8, HEMP, n=2))
    return "".join(out)


def pilot(seed=0):
    """비거 pilot (seated, upper body only, origin at the seat): goggles over 전립, red coat, hands on levers."""
    out = []
    lever = M["wood"]
    out.append(KK.arm((13, -42), (22, -26), (14, -16), RED_COAT, w=5.6, sleeve=RED_COAT, cuff=M["black_cloth"], hand="grip",
                      hand_dir=(0, 1), back=True))
    out.append(KK.form((14, -10), (20, -30), 1.4, 1.2, lever))
    out.append(brigandine_torso((0, -50), (0, -8), 15, 13))
    out.append(KK.neck((0, -49), (0.5, -56), 4.2, SKIN))
    out.append(human_head(0.5, -66, 10, turn=-0.3, look=vary("pilot", seed), goggles=True))
    out.append(KK.ao(1, -70, 10, 3, 0.55))
    out.append(jeonrip(-74.6, 0.5))
    out.append(KK.form((-16, -8), (-22, -30), 1.4, 1.2, lever))
    out.append(KK.arm((-13, -42), (-24, -28), (-20, -18), RED_COAT, w=5.8, sleeve=RED_COAT, cuff=M["black_cloth"], hand="grip", hand_dir=(0, -1)))
    return "".join(out)


def fallen_gear():
    """For the death frame: dropped 전립 and matchlock lying on the ground."""
    return (KK.cast_shadow(10, 4, 26, 5, op=0.4) + matchlock(-10, 4, 34, -2, lit=False) + jeonrip(-2, -14, s=0.75, tilt=-16))
