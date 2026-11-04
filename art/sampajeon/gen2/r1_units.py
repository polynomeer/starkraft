"""R1 관상감 공방 units — painterly 2.5D figures and vehicles (ids r1-)."""
import math

import r1_figs as F

from r1_lib import (RNG, OUT, RED, RED_L, RED_D, GREEN, BRASS, BRASS_L, BRASS_D, ORE, ORE_L, ORE_D, HEMP, HEMP_D,
                    SKIN, SKIN_D, P, m_left, iso_box, iso_shadow, rect, circ, ell, path, poly, pline, line, g, u,
                    ground_patch, ore_crystal, smoke_puff, f)

O = dict(stroke=OUT, stroke_width=0.7, stroke_opacity=0.75, stroke_linejoin="round")


# ------------------------------------------------------------------ figure parts (feet at 0,0; ~100 tall)
def fig_shadow(k=1.0):
    return [ell(7 * k, 1, 20 * k, 6 * k, "#000", opacity=0.5, filter=u("shadow"))]


def legs(trouser=u("hemp"), gaiter=True, shoe="#2A1E16"):
    out = [
        path("M-11 -47 C-14 -34 -15 -20 -13 -9 L-4 -9 C-4 -22 -3 -34 -1 -45 Z", trouser, filter=u("cloth"), **O),
        path("M1 -46 C3 -34 4 -22 4 -9 L13 -9 C13 -22 12 -36 10 -47 Z", trouser, filter=u("cloth"), **O),
        path("M-9 -40 q-1 12 -1 26", stroke="#000", stroke_width=0.8, opacity=0.25),
        path("M7 -40 q1 12 2 26", stroke="#000", stroke_width=0.8, opacity=0.25),
    ]
    if gaiter:  # 행전: white leg wraps
        for x0, x1 in ((-13.4, -3.6), (3.8, 13.2)):
            out.append(path(f"M{x0} -24 L{x1} -24 L{x1 - 0.4} -9 L{x0 + 0.3} -9 Z", "#EDE6D4", **O))
            for k in range(4):
                yy = -22 + k * 3.4
                out.append(line(x0 + 0.3, yy, x1 - 0.4, yy + 1.2, "#9C9280", 0.5))
    out += [ell(-8.5, -7, 6, 3, shoe, **O), ell(9, -7, 6, 3, shoe, **O),
            ell(-10, -8, 2.4, 1, "#FFFFFF", opacity=0.25), ell(7.5, -8, 2.4, 1, "#FFFFFF", opacity=0.25)]
    return out


def head(beard=False, topknot=False, headband=False, goggles=False, y=-90.0):
    out = [rect(-3.6, y + 6, 7.2, 6, u("skin"), **O),
           ell(-7.4, y + 1, 1.8, 2.6, SKIN_D, **O),
           ell(0, y, 7.4, 8.6, u("skin"), **O),
           path(f"M3 {y - 6} q5 4 3 12 q-2 3 -6 3", "#000", opacity=0.14),
           # brows, eyes, nose, mouth
           path(f"M-5 {y - 2.6} q2 -1.2 3.6 0 M1.6 {y - 2.6} q2 -1.2 3.6 0", stroke="#2A1A10", stroke_width=0.9, stroke_linecap="round"),
           ell(-3.2, y - 0.6, 1.0, 0.7, "#1A100A"), ell(3.2, y - 0.6, 1.0, 0.7, "#1A100A"),
           circ(-2.9, y - 0.9, 0.3, "#FFFFFF", opacity=0.8), circ(3.5, y - 0.9, 0.3, "#FFFFFF", opacity=0.8),
           path(f"M0 {y - 0.4} q1.4 2.6 -0.2 3.6", stroke=SKIN_D, stroke_width=0.8),
           path(f"M-2.4 {y + 5} q2.4 1.2 4.8 0", stroke="#7A3A28", stroke_width=0.8, stroke_linecap="round")]
    if beard:
        out += [path(f"M-4.8 {y + 3.8} q2.4 -1.6 4.8 -0.2 q2.4 -1.4 4.8 0.2 q-2.4 1.2 -4.8 0.4 q-2.4 0.8 -4.8 -0.4 Z", "#1E140E"),
                path(f"M-1.6 {y + 6.6} q1.6 5 0 7.4 q1.8 -1 3.2 -7 Z", "#1E140E")]
    if topknot:
        out += [path(f"M-7.4 {y - 1} q0 -10 7.4 -10 q7.4 0 7.4 10 q-3 -5 -7.4 -5.4 q-4.4 0.4 -7.4 5.4 Z", "#1A120C", **O),
                ell(0.6, y - 12.4, 3.2, 3.6, "#1A120C", **O), ell(-0.4, y - 13.4, 1, 1.4, "#5A4A3A")]
    if headband:
        out += [path(f"M-7.6 {y - 4} q7.6 -3 15.2 0 l0 2.6 q-7.6 -3 -15.2 0 Z", "#EFE8D8", **O),
                path(f"M7.4 {y - 3} q4 1 6 5 M7.4 {y - 2} q5 -1 7 2", stroke="#EFE8D8", stroke_width=1.2)]
    if goggles:
        out += [path(f"M-7.6 {y - 3.4} q7.6 -2.4 15.2 0", stroke="#3A2414", stroke_width=1.3),
                circ(-3.2, y - 3.6, 2.4, u("brass"), **O), circ(3.4, y - 3.6, 2.4, u("brass"), **O),
                circ(-3.2, y - 3.6, 1.5, "#7CC4AE", opacity=0.9), circ(3.4, y - 3.6, 1.5, "#7CC4AE", opacity=0.9),
                circ(-3.8, y - 4.2, 0.5, "#FFFFFF")]
    return out


def jeonrip(y=-97.0):
    """전립 felt military hat: wide brim, domed crown, red tassel, brass badge, bead chin strap."""
    out = [ell(1.5, y + 2.6, 17, 5.2, "#000", opacity=0.35, filter=u("blur1")),
           ell(0, y, 17.5, 5.6, u("felt"), **O),
           path(f"M-15 {y - 0.6} q15 4.2 30 0", stroke="#6A625C", stroke_width=0.7, opacity=0.8),
           path(f"M-8 {y - 1} q-0.6 -10 8 -11 q8.6 1 8 11 q-8 2.4 -16 0 Z", u("felt"), **O),
           path(f"M-5.4 {y - 7} q2 -3.4 5.4 -3.6", stroke="#8A827A", stroke_width=0.9, opacity=0.7),
           circ(0, y - 6, 2.2, u("brass"), **O), circ(-0.5, y - 6.5, 0.7, BRASS_L)]
    # 상모: red tassel fan
    for k in range(9):
        a = math.radians(-70 + k * 17.5)
        x2, y2 = math.sin(a) * 9, y - 12 - math.cos(a) * 6
        out.append(path(f"M0 {y - 11} Q{f(x2 * 0.5)} {f(y - 15)} {f(x2)} {f(y2)}", stroke=RED if k % 2 else "#F07A50", stroke_width=1.5, stroke_linecap="round"))
    out.append(circ(0, y - 11.5, 1.6, BRASS, **O))
    # bead chin strap
    for k in range(7):
        out.append(circ(7.6 - k * 0.2, y + 2 + k * 1.9, 0.75, "#E8D8B0" if k % 2 else "#A0522D"))
    return out


def brigandine():
    """두정갑: red studded coat with brass studs, indigo collar and sash."""
    body = "M-15 -82 C-18 -70 -19 -50 -19 -34 L19 -34 C19 -50 18 -70 15 -82 C9 -85 -9 -85 -15 -82 Z"
    out = [path(body, u("red"), filter=u("cloth"), **O),
           path("M5 -82 C10 -66 12 -48 13 -34 L19 -34 C19 -50 18 -70 15 -82 Z", "#000", opacity=0.22),
           line(0.5, -80, 1.5, -34, "#5A1A0C", 1.0),
           path("M-19 -38 L19 -38 L19 -34 L-19 -34 Z", "#5A1A0C", opacity=0.7)]
    # stud grid clipped by an approximate coat width per row
    for r in range(9):
        y = -78 + r * 4.8
        half = 14 + (r / 8) * 4.2
        for c in range(7):
            x = -half + 1.6 + c * (2 * half - 3.2) / 6
            if abs(x - 1) < 1.2:
                continue
            out.append(circ(x, y, 0.85, u("brass")))
            out.append(circ(x - 0.25, y - 0.3, 0.3, BRASS_L, opacity=0.9))
    out += [path("M-9 -84 q9 5 18 0 l1.6 4 q-10.6 5 -21.2 0 Z", u("indigo"), **O),
            rect(-18, -60, 36, 4.4, u("indigo"), rx=1.2, **O),
            path("M2 -58 q4 6 2 16 l2.6 -0.4 q1 -9 -2 -15.6 Z", "#2A3466", **O),
            circ(2.6, -58, 2.2, "#3B4E8C", **O)]
    # shoulder guards
    out += [path("M-15 -82 q-6 1 -8 9 q4 2 9 -1 Z", u("red"), **O), path("M15 -82 q6 1 8 9 q-4 2 -9 -1 Z", u("red-s"), **O),
            path("M-22.6 -73.4 q4 2 9 -1", stroke=BRASS, stroke_width=0.9), path("M22.6 -73.4 q-4 2 -9 -1", stroke=BRASS, stroke_width=0.9)]
    return out


def jeogori():
    """Hemp work jacket + apron for the artisan."""
    out = [path("M-14 -82 C-17 -72 -17 -60 -16 -50 L16 -50 C17 -60 17 -72 14 -82 C8 -85 -8 -85 -14 -82 Z", u("hemp"), filter=u("cloth"), **O),
           path("M4 -82 C9 -70 11 -58 11 -50 L16 -50 C17 -60 17 -72 14 -82 Z", "#000", opacity=0.18),
           path("M-6 -84 L2 -64 L8 -84", stroke="#8C7E62", stroke_width=1.2),
           path("M2 -66 q6 0 7 4 q-4 -1 -7 3 Z", "#E8E0CC", **O),
           path("M-13 -56 L13 -56 L15 -36 L-15 -36 Z", "#6E4A2E", filter=u("cloth"), **O),
           rect(-16, -58, 32, 3.4, "#3A2414", rx=1),
           circ(9, -48, 1.6, u("brass")), rect(-12, -50, 6, 7, "#4A2E1E", **O), rect(-11.4, -49.4, 1.4, 6, "#C9A35A")]
    return out


def arm(x0, y0, x1, y1, x2, y2, sleeve, w=6.0, hand=True, cuff=None):
    out = [path(f"M{x0} {y0} Q{x1} {y1} {x2} {y2}", stroke=OUT, stroke_width=w + 1.4, stroke_linecap="round", stroke_opacity=0.75),
           path(f"M{x0} {y0} Q{x1} {y1} {x2} {y2}", stroke=sleeve, stroke_width=w, stroke_linecap="round"),
           path(f"M{x0 - 1} {y0} Q{x1 - 1} {y1} {x2 - 1} {y2}", stroke="#FFFFFF", stroke_width=w * 0.25, stroke_linecap="round", opacity=0.25)]
    if cuff:
        out.append(circ(x2, y2, w * 0.55, cuff))
    if hand:
        out += [circ(x2, y2, 2.8, u("skin"), **O), circ(x2 - 0.8, y2 - 0.8, 0.9, "#F8DCC0", opacity=0.7)]
    return out


def matchlock(x0, y0, x1, y1, lit=True):
    """화승총: wooden stock, iron barrel, brass bands, glowing match cord."""
    ang = math.atan2(y1 - y0, x1 - x0)
    L = math.hypot(x1 - x0, y1 - y0)
    deg = math.degrees(ang)
    stock = g([path(f"M0 -2.4 L{L * 0.42} -1.6 L{L * 0.42} 1.6 L-2 3.6 L-6 4.2 L-6 -2 Z", u("woodg"), filter=u("wood"), **O),
               rect(L * 0.3, -1.1, L * 0.72, 2.2, u("iron"), **O),
               line(L * 0.3, -0.6, L * 1.0, -0.6, "#C8D2D6", 0.5, opacity=0.8),
               rect(L * 0.46, -1.7, 1.4, 3.4, u("brass")), rect(L * 0.7, -1.6, 1.4, 3.2, u("brass")), rect(L * 0.98, -1.5, 1.2, 3.0, u("brass")),
               path(f"M{L * 0.28} 1.6 q1 3 3.4 2.8", stroke="#2A1A10", stroke_width=0.9),
               rect(L * 0.24, -3.6, 3.6, 2.2, u("brass"), **O)],
              transform=f"translate({f(x0)} {f(y0)}) rotate({f(deg)})")
    out = [stock]
    if lit:
        lx = x0 + math.cos(ang) * L * 0.26
        ly = y0 + math.sin(ang) * L * 0.26 - 3
        out += [path(f"M{f(lx)} {f(ly)} q-4 -3 -2 -7 q2 -4 -1 -8 q-3 -3 1 -6", stroke="#6A4A2A", stroke_width=0.9),
                circ(lx, ly, 3.6, u("ember"), filter=u("glow")), circ(lx, ly, 1.0, "#FFF6C8"),
                path(f"M{f(lx)} {f(ly - 2)} q-3 -5 0 -9 q3 -4 -1 -9", stroke="#B8B0A0", stroke_width=1.6, opacity=0.35, filter=u("blur1"))]
    return out


def gunner(dy=0.0, lit=True, aim=False):
    """화승총수 figure (feet at origin)."""
    out = fig_shadow() + legs(u("hemp"))
    out += arm(-13, -78, -20, -64, -16, -54, RED_D)             # back arm (drawn under coat)
    out += brigandine()
    out += head(beard=True)
    out += jeonrip()
    if aim:
        out += matchlock(10, -66, -34, -78)
        out += arm(13, -78, 16, -70, 8, -66, RED, cuff=BRASS)
        out += arm(-13, -78, -22, -76, -20, -72, RED, cuff=BRASS)
    else:
        out += matchlock(14, -50, -22, -104, lit)
        out += arm(13, -78, 20, -62, 13, -54, RED, cuff=BRASS)
        out += arm(-13, -78, -14, -74, -10, -82, RED, cuff=BRASS)
    return g(out, transform=f"translate(0 {f(dy)})")


def artisan(swing=0.0):
    """공방 일꾼: 지게 back frame with goblin-fire stone load, pick, headband, goggles."""
    out = fig_shadow(1.1)
    # 지게 behind the body
    jg = [line(-13, -112, -15, -4, "#5E3A22", 3.2, stroke_linecap="round"), line(11, -112, 13, -4, "#4A2E1E", 3.2, stroke_linecap="round"),
          line(-13.6, -112, -15.4, -6, "#A87A50", 0.9), line(-14, -88, 12, -88, "#5E3A22", 2.4), line(-14.6, -60, 12.6, -60, "#5E3A22", 2.4),
          line(-14.4, -74, 12.4, -74, "#5E3A22", 2.0)]
    out += jg
    # load: bundle of glowing stone tied on top
    out.append(ell(-1, -114, 19, 13, u("ore-glow"), filter=u("glow-l")))
    out.append(path("M-16 -102 q-2 -12 8 -16 q10 -4 20 2 q6 6 2 14 q-14 6 -30 0 Z", "#6E5A3A", filter=u("cloth"), **O))
    for dx, h, a in ((-9, 14, -20), (-3, 20, -5), (4, 16, 12), (9, 11, 26), (-6, 10, -34)):
        tipx, tipy = -1 + dx + math.sin(math.radians(a)) * h, -112 - math.cos(math.radians(a)) * h
        out.append(poly([(-1 + dx - 2.6, -110), (tipx, tipy), (-1 + dx + 0.4, -108)], u("ore"), stroke=ORE_D, stroke_width=0.5))
        out.append(poly([(-1 + dx + 0.4, -108), (tipx, tipy), (-1 + dx + 2.8, -110)], u("ore-s"), stroke=ORE_D, stroke_width=0.5))
    out.append(path("M-17 -104 q16 6 32 0", stroke="#C8B48A", stroke_width=1.4))
    out.append(path("M-15 -110 q15 5 30 -1", stroke="#C8B48A", stroke_width=1.2))
    out += legs(u("hemp"))
    out += arm(-13, -78, -19, -64, -15, -54, "#C4B696")
    out += jeogori()
    # shoulder straps
    out += [path("M-10 -84 q-2 14 -6 26", stroke="#8C6A40", stroke_width=2.4), path("M10 -84 q2 14 5 26", stroke="#6E5230", stroke_width=2.4)]
    out += head(topknot=True, headband=True, goggles=True)
    # pick over the right shoulder
    rot = -swing
    pick = g([line(0, 0, 10, -46, "#5E3A22", 2.6, stroke_linecap="round"), line(-0.6, -0.6, 9.4, -46, "#A87A50", 0.8),
              path("M2 -48 q8 -4 18 4 q-8 -2 -9 1 q-1 -3 -9 -5 Z", u("iron"), **O),
              path("M2 -48 q-4 -2 -7 2", stroke="#4E585C", stroke_width=2.2, stroke_linecap="round")],
             transform=f"translate(14 -50) rotate({f(rot)})")
    out.append(pick)
    out += arm(13, -78, 19, -64, 14, -52, "#C4B696")
    out += arm(13, -78, 4, -70, 12, -60, "#D8CCB0")
    return g(out)


# ------------------------------------------------------------------ vehicles
def wheel_left(ox, oy, W, D, z0, H, a, r, spokes=8, dark=False):
    """Spoked wooden wheel in a SW-facing plane: shaded felloe, iron tyre with specular, turned spokes, brass hub, AO."""
    F._defs_once()
    cx, cy = a, H - r
    c = [circ(cx + 0.8, cy + 0.9, r + 0.3, "#000", opacity=0.45, filter=u("blur1")),
         circ(cx, cy, r, "url(#r1k-tyre)", stroke=OUT, stroke_width=0.8),
         circ(cx, cy, r - 1.4, "url(#r1k-felloe)"),
         circ(cx, cy, r - 3.0, "#120A06"),
         circ(cx, cy, r - 3.0, "url(#r1k-hubao)")]
    for k in range(spokes):
        ang = math.tau * k / spokes + 0.2
        ex, ey = cx + math.cos(ang) * (r - 2.6), cy + math.sin(ang) * (r - 2.6)
        lit = 0.5 + 0.5 * math.cos(ang + 2.4)
        c.append(line(cx, cy, ex, ey, "#2A1A0E", 1.7))
        c.append(line(cx - 0.3, cy - 0.3, ex - 0.3, ey - 0.3, "#C89868", 0.6, opacity=0.35 + 0.5 * lit))
    for k in range(spokes * 2):
        ang = math.tau * k / (spokes * 2)
        lit = 0.5 + 0.5 * math.cos(ang + 2.3)
        c.append(circ(cx + math.cos(ang) * (r - 0.6), cy + math.sin(ang) * (r - 0.6), 0.5, "#E8F0F4" if lit > 0.6 else "#5A646A"))
    c.append(path(f"M{f(cx - r * 0.86)} {f(cy - r * 0.3)} A{f(r)} {f(r)} 0 0 1 {f(cx + r * 0.2)} {f(cy - r * 0.96)}", stroke="#F2F6F8",
                  stroke_width=0.9, opacity=0.75, stroke_linecap="round"))
    c += [circ(cx, cy, 2.6, "url(#r1k-stud)", stroke=OUT, stroke_width=0.5), circ(cx - 0.7, cy - 0.7, 0.7, "#FFFBEA")]
    if dark:
        c.append(circ(cx, cy, r, "#000", opacity=0.4))
    return g(c, transform=m_left(ox, oy, W, D, z0, H))


def hwacha(ox=118.0, oy=96.0, fire=False, crew=True):
    """화차: cart, inclined rack of 신기전 tubes facing SW, wheels, push crew."""
    W, D = 56.0, 30.0
    out = [iso_shadow(ox, oy, W, D, 26, k=0.7, op=0.5)]
    # far wheel (SE side, darker) — drawn as ellipse at the u=W end is hidden; draw back wheel on v=0 plane
    out.append(wheel_left(ox, oy, W, 0.0, 0, 17, W * 0.42, 9.0, dark=True))
    # chassis
    out += iso_box(ox, oy, W, D, 5, u("woodg"), u("woodv"), "#3A2414", z0=8, tex="wood")
    # shafts/handles behind (toward +u, NE) for the crew
    h1, h2 = P(ox, oy, W, 6, 11), P(ox, oy, W + 26, 4, 15)
    h3, h4 = P(ox, oy, W, D - 6, 11), P(ox, oy, W + 26, D - 8, 15)
    out += [line(*h1, *h2, "#4A2E1E", 2.4, stroke_linecap="round"), line(*h3, *h4, "#5E3A22", 2.4, stroke_linecap="round")]
    # inclined rack: base box + tube face
    rW, rD, rH = 40.0, 22.0, 20.0
    rx, ry = P(ox, oy, 8, 4)
    tubes = []
    cols, rows = 10, 6
    for r in range(rows):
        for c in range(cols):
            a = 2.4 + c * (rW - 4.8) / (cols - 1)
            b = 2.6 + r * (rH - 5) / (rows - 1)
            tubes += [circ(a, b, 1.55, "#140C08"), circ(a - 0.35, b - 0.35, 0.6, "#3A2A1E")]
            if not fire or RNG.random() > 0.5:
                tubes.append(circ(a + 0.4, b + 0.4, 0.55, "#C8432B"))
    out += iso_box(rx, ry, rW, rD, rH, u("woodg"), u("woodv"), "#3A2414", z0=13, tex="wood",
                   extra_l=[rect(0, 0, rW, rH, "#2E1C10", opacity=0.55)] + tubes + [rect(0, 0, rW, 1.6, u("brass")), rect(0, rH - 1.6, rW, 1.6, u("brass"))],
                   extra_r=[rect(0, 0, rD, rH, "#000", opacity=0.3)] + [line(3 + k * 4, 0, 3 + k * 4, rH, "#2A1A10", 0.6) for k in range(5)])
    # arrow shafts sticking up out of the rack top with fletching
    for k in range(14):
        a = 3 + (k % 7) * 5.4 + RNG.uniform(-0.6, 0.6)
        b = 4 + (k // 7) * 9 + RNG.uniform(-1, 1)
        x, y = P(rx, ry, a, b, 13 + rH)
        out.append(line(x, y, x + 3, y - 9, "#C8B48A", 0.8))
        out.append(path(f"M{f(x + 2.6)} {f(y - 8)} l1.8 -2.8 l-0.2 3.4 Z", "#EFE8D8"))
        out.append(path(f"M{f(x + 2.4)} {f(y - 7.6)} l-1.4 -2.4 l2.4 1.2 Z", RED))
    # near wheel
    out.append(wheel_left(ox, oy, W, D, 0, 17, W * 0.42, 9.6))
    # brass corner fittings on chassis
    for a in (2, W - 2):
        x, y = P(ox, oy, a, D, 10.5)
        out.append(rect(x - 1.2, y - 2, 2.4, 4, u("brass")))
    if crew:
        for ci, (cu, cv, s, flip) in enumerate(((W + 24, 2, 0.26, 1), (W + 25, D - 9, 0.26, 1))):
            x, y = P(ox, oy, cu, cv)
            out.append(g([F.crewman(seed=ci)], transform=f"translate({f(x)} {f(y)}) scale({s})"))
    if fire:
        fx, fy = P(rx, ry, rW * 0.5, rD, 13 + rH * 0.5)
        out += [ell(fx - 6, fy + 2, 26, 14, "#FFB040", opacity=0.45, filter=u("glow-l"))]
        out += smoke_puff(fx - 4, fy - 4, 9, op=0.8)
    return out


def gunner_small():
    """Simplified crewman (hat + hemp coat), drawn at feet origin."""
    out = fig_shadow() + legs(u("hemp"))
    out += [path("M-14 -82 C-17 -70 -17 -55 -16 -44 L16 -44 C17 -55 17 -70 14 -82 C8 -85 -8 -85 -14 -82 Z", "#8A3A22", filter=u("cloth"), **O),
            rect(-16, -60, 32, 4, u("indigo"))]
    out += head(beard=True) + jeonrip()
    out += arm(13, -78, 4, -72, -10, -70, "#8A3A22") + arm(-13, -78, -20, -72, -26, -74, "#8A3A22")
    return g(out)


def _dome_h(nu, nv, Hm):
    r2 = nu * nu + nv * nv
    return Hm * math.sqrt(max(0.0, 1 - r2)) if r2 < 1 else 0.0


def turtle(ox=126.0, oy=78.0):
    """거북 전차: hex-plated iron dome with spikes, dragon head, wooden wheels, gun ports."""
    W, D, Hm = 86.0, 56.0, 30.0
    cu, cv = W / 2, D / 2
    out = [iso_shadow(ox, oy, W, D, 34, k=0.7, op=0.55)]
    # skirt box (lacquered planks) + wheels along the SW side
    sk = []
    for k in range(9):
        sk.append(rect(k * W / 9, 0, W / 9 - 0.6, 12, "#4A2E1E" if k % 2 else "#5E3A22"))
    sk += [rect(0, 0, W, 2, "#2E1E14"), rect(0, 10, W, 2, "#2E1E14")]
    for k in range(5):  # gun ports
        x = 8 + k * (W - 16) / 4
        sk += [rect(x - 2.6, 3, 5.2, 4.4, "#0E0806", stroke=BRASS, stroke_width=0.8), circ(x, 5.2, 1.0, "#3A2A1E")]
    out += iso_box(ox, oy, W, D, 12, "#2E1E14", "#5E3A22", "#2E1E14", z0=6, tex="wood", extra_l=sk,
                   extra_r=[rect(0, 0, D, 12, "#000", opacity=0.3)])
    for k in range(4):
        out.append(wheel_left(ox, oy, W, D, 0, 12, 12 + k * (W - 24) / 3, 6.4, spokes=6))
    # dome plates: hex grid over the footprint, projected onto the dome
    L = (-0.62, 0.32, 0.72)
    hexes = []
    R = 6.2
    for j in range(-6, 7):
        for i in range(-8, 9):
            hu = cu + i * R * 1.5
            hv = cv + j * R * math.sqrt(3) + (R * math.sqrt(3) / 2 if i % 2 else 0)
            nu, nv = (hu - cu) / (W / 2 + 2), (hv - cv) / (D / 2 + 2)
            if nu * nu + nv * nv > 0.86:
                continue
            hexes.append((hu, hv))
    hexes.sort(key=lambda t: t[0] + t[1])
    for hu, hv in hexes:
        vs = []
        for k in range(6):
            a = math.tau * k / 6
            pu, pv = hu + math.cos(a) * R * 0.92, hv + math.sin(a) * R * 0.92
            nu, nv = (pu - cu) / (W / 2 + 2), (pv - cv) / (D / 2 + 2)
            vs.append(P(ox, oy, pu, pv, 18 + _dome_h(nu, nv, Hm)))
        nu, nv = (hu - cu) / (W / 2 + 2), (hv - cv) / (D / 2 + 2)
        e = 0.02
        dhu = (_dome_h(nu + e, nv, Hm) - _dome_h(nu - e, nv, Hm)) / (2 * e * (W / 2 + 2))
        dhv = (_dome_h(nu, nv + e, Hm) - _dome_h(nu, nv - e, Hm)) / (2 * e * (D / 2 + 2))
        n = (-dhu, -dhv, 1.0)
        nl = math.sqrt(sum(c * c for c in n))
        lit = max(0.0, sum(a * b for a, b in zip(n, L)) / nl)
        base = (10, 20, 20)
        hi = (150, 196, 184)
        lt = lit ** 1.6
        col = "#%02X%02X%02X" % tuple(int(b + (h - b) * lt) for b, h in zip(base, hi))
        out.append(poly(vs, col, stroke="#5A1A0C", stroke_width=1.0, stroke_linejoin="round"))
        cx, cy = P(ox, oy, hu, hv, 18 + _dome_h(nu, nv, Hm))
        # bevel: lit upper-left edge of each plate, shadowed lower-right edge
        out.append(pline(vs[3:6], "#E8FFF6", 0.6, opacity=0.15 + 0.5 * lt))
        out.append(pline(vs[0:3], "#000000", 0.8, opacity=0.35))
        if lt > 0.55:
            out.append(ell(cx - 1.2, cy - 0.6, 2.2, 1.0, "#FFFFFF", opacity=0.55 * (lt - 0.5), filter=u("blur1")))
        # spike: lit and shaded half-cones + specular tip
        out.append(path(f"M{f(cx - 1.4)} {f(cy)} L{f(cx)} {f(cy - 5)} L{f(cx)} {f(cy + 0.5)} Z", "#B8C2C8", stroke=OUT, stroke_width=0.25))
        out.append(path(f"M{f(cx)} {f(cy + 0.5)} L{f(cx)} {f(cy - 5)} L{f(cx + 1.4)} {f(cy)} Z", "#20262A", stroke=OUT, stroke_width=0.25))
        out.append(circ(cx - 0.2, cy - 4.4, 0.4, "#FFFFFF", opacity=0.9))
    # dragon head on the SW end (front)
    hx, hy = P(ox, oy, -6, D * 0.62, 20)
    dh = [path("M0 0 q-6 -10 -16 -8 q-6 2 -10 8 q4 6 12 7 q10 0 14 -7 Z", u("red"), filter=u("tex"), **O),
          path("M-26 0 q6 2 12 0 l2 4 q-8 2 -14 -1 Z", "#2A0A06", **O),
          path("M-22 1 l2 2 l2 -2 l2 2 l2 -2 l2 2", stroke="#F2EAD8", stroke_width=0.8),
          path("M-8 -8 q-2 -8 4 -12 M-12 -7 q-4 -8 0 -13", stroke=u("brass"), stroke_width=1.8, stroke_linecap="round"),
          circ(-14, -4, 1.8, "#FFE08A", filter=u("glow")), circ(-14, -4, 0.7, "#1A0A06"),
          path("M-2 -6 q6 -4 10 2", stroke=GREEN, stroke_width=2)]
    out.append(g(dh, transform=f"translate({f(hx)} {f(hy)})"))
    out += smoke_puff(hx - 34, hy - 8, 7, op=0.85) + smoke_puff(hx - 44, hy - 18, 5, op=0.6)
    # brass rim along the dome edge
    rim = [P(ox, oy, cu + math.cos(a) * (W / 2 - 1), cv + math.sin(a) * (D / 2 - 1), 18) for a in [math.tau * k / 40 for k in range(41)]]
    out.append(pline(rim, BRASS, 1.4, opacity=0.9))
    return out


def bigeo(ox=124.0, oy=60.0, alt=46.0):
    """비거: hanji wings on bamboo ribs, small cart body, pilot; shadow far below."""
    out = []
    # ground shadow (far below)
    sx, sy = P(ox, oy, 20, 14)
    out.append(path(f"M{f(sx - 70)} {f(sy + alt + 10)} l70 -18 l70 18 l-70 18 Z", "#000", opacity=0.32, filter=u("shadow-l")))
    z = 0.0
    # wings: two large membranes in the top plane, spanning along v
    for side in (-1, 1):
        root_a, root_b = (14, 14 + side * 4), (34, 14 + side * 4)
        tip_a = (6, 14 + side * 62)
        tip_b = (30, 14 + side * 58)
        mid = (40, 14 + side * 34)
        ps = [P(ox, oy, *root_a, z), P(ox, oy, *tip_a, z + 6), P(ox, oy, *tip_b, z + 4), P(ox, oy, *mid, z + 1), P(ox, oy, *root_b, z)]
        fill = u("hanji") if side < 0 else u("hanji-dim")
        out.append(poly(ps, fill, filter=u("cloth"), **O))
        # bamboo ribs
        for k in range(6):
            t = k / 5
            ta = (tip_a[0] + (tip_b[0] - tip_a[0]) * t, tip_a[1] + (tip_b[1] - tip_a[1]) * t)
            p1, p2 = P(ox, oy, 24, 14 + side * 4, z), P(ox, oy, ta[0], ta[1], z + 5)
            out.append(line(*p1, *p2, "#6E5A2A", 1.0))
            out.append(line(p1[0], p1[1] - 0.4, p2[0], p2[1] - 0.4, "#E8D8A0", 0.35, opacity=0.8))
        # red sun patch + painted stripe
        cx, cy = P(ox, oy, 20, 14 + side * 36, z + 3)
        out.append(ell(cx, cy, 6, 3.2, RED, opacity=0.85))
        out.append(ell(cx, cy, 3, 1.6, "#3B4E8C", opacity=0.9))
    # cart body
    out += iso_box(ox + 2, oy - 8, 30, 16, 9, u("woodg"), u("woodv"), "#3A2414", z0=z - 8, tex="wood",
                   extra_l=[rect(0, 0, 30, 1.6, u("brass")), rect(0, 7.4, 30, 1.6, u("brass"))] + [line(4 + k * 6, 1.6, 4 + k * 6, 7.4, "#2E1E14", 0.6) for k in range(5)])
    # bellows/propeller at the back
    px, py = P(ox + 2, oy - 8, 34, 8, z + 2)
    for k in range(4):
        a = math.radians(20 + k * 90)
        out.append(ell(px + math.cos(a) * 5, py + math.sin(a) * 2.6, 4.6, 1.4, "#C8B48A", transform=f"rotate({f(math.degrees(a))} {f(px + math.cos(a) * 5)} {f(py + math.sin(a) * 2.6)})", **O))
    out.append(circ(px, py, 1.8, u("brass"), **O))
    # pilot
    qx, qy = P(ox + 2, oy - 8, 14, 8, z + 1)
    out.append(g([F.pilot()], transform=f"translate({f(qx)} {f(qy + 4)}) scale(0.4)"))
    # pennant streamers
    tx, ty = P(ox + 2, oy - 8, 30, 0, z + 9)
    out.append(path(f"M{f(tx)} {f(ty)} q10 -6 22 -2 q-10 2 -22 6", RED, opacity=0.9))
    return out


# ------------------------------------------------------------------ unit cards (viewBox 260×200)
def card_unit(kind):
    body = []
    if kind == "artisan":
        body += ground_patch(130, 168, 120, 30, "field", 0.9)
        body += ore_crystal(212, 160, 0.8)
        body.append(g([F.artisan()], transform="translate(116 191) scale(0.98)"))
    elif kind == "gunner":
        body += ground_patch(130, 168, 120, 30, "field", 0.9)
        body.append(g([F.gunner()], transform="translate(134 190) scale(1.1)"))
    elif kind == "puppet":
        body += ground_patch(130, 172, 118, 26, "yard", 0.9)
        body.append(g([F.puppet()], transform="translate(146 192) scale(0.88)"))
    elif kind == "hwacha":
        body += ground_patch(130, 150, 125, 46, "field", 1.0)
        body.append(g(hwacha(118, 108), transform="translate(-48 -46) scale(1.38)"))
    elif kind == "turtle":
        body += ground_patch(130, 150, 126, 48, "field", 1.0)
        body += turtle(130, 92)
    elif kind == "bigeo":
        body += ground_patch(130, 168, 120, 30, "field", 0.7)
        body.append(g(bigeo(118, 64), transform="translate(-30 -8) scale(1.22)"))
    return body


# pre-rendered-sprite replacements (figure_kit) used by scenes
gunner = F.gunner
artisan = F.artisan
jeonrip = F.jeonrip
matchlock = F.matchlock
