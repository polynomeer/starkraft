"""Generates design2/project/Lobby.dc.html (S2 접속·로비, id prefix s2-).

High-fidelity pass: an isometric painted inn courtyard (객주 마당) at dusk
behind ornate wood-and-hanji panels; painted race busts in the slots.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from ui_s24 import *  # noqa: E402,F401,F403
import ui_s24  # noqa: E402
import screen_figs  # noqa: E402

ui_s24.BUSTS.update(screen_figs.BUSTS)  # kit-rendered busts (shaded faces, folds, AO)

P = "s2"
W, H = 1440, 900
OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "Lobby.dc.html")


# ==========================================================================
# Painted courtyard scene
# ==========================================================================

def _shade(rgb, k):
    return "#%02X%02X%02X" % tuple(max(0, min(255, int(c + k))) for c in rgb)


def stone_rows(iso, face, a0, a1, fixed, z0, z1, rows, rng, base=(132, 124, 108), dark=False):
    """Irregular masonry on a vertical face. face='y' -> plane y=fixed spanning x; face='x' -> plane x=fixed spanning y."""
    out = []
    hz = (z1 - z0) / rows
    for r in range(rows):
        za, zb = z0 + r * hz, z0 + (r + 1) * hz
        a = a0 + (rng.random() * 0.8 if r % 2 else 0)
        while a < a1 - 0.2:
            wdt = 1.4 + rng.random() * 1.8
            b = min(a1, a + wdt)
            k = rng.randint(-22, 18) - (34 if dark else 0)
            col = _shade(base, k)
            g = 0.09
            if face == "y":
                pts = [(a + g, fixed, za + g), (b - g, fixed, za + g), (b - g, fixed, zb - g), (a + g, fixed, zb - g)]
                top = [(a + g, fixed, zb - g), (b - g, fixed, zb - g)]
            else:
                pts = [(fixed, a + g, za + g), (fixed, b - g, za + g), (fixed, b - g, zb - g), (fixed, a + g, zb - g)]
                top = [(fixed, a + g, zb - g), (fixed, b - g, zb - g)]
            out.append(f'<path d="{iso.poly(pts)}" fill="{col}"></path>')
            out.append(f'<path d="{iso.line(top)}" fill="none" stroke="#FFF" stroke-opacity="{0.14 if dark else 0.28}" stroke-width="0.9"></path>')
            a = b
    return "".join(out)


def roof(iso, x0, y0, L, D, z_eave, rise, over, rng, tile_step=1.05, ridge_inset=None):
    """Hipped giwa roof with upturned eave corners, tile columns, white-mortar ridges."""
    out = []
    ex0, ex1 = x0 - over, x0 + L + over
    ey0, ey1 = y0 - over, y0 + D + over
    zr = z_eave + rise
    ri = ridge_inset if ridge_inset is not None else (D / 2 + over) * 0.72
    rx0, rx1 = x0 + ri, x0 + L - ri
    ry = y0 + D / 2
    up = 1.5

    def lift(u):
        return up * (2 * u - 1) ** 6

    n = 28
    front = [(ex0 + (ex1 - ex0) * i / n, ey1, z_eave + lift(i / n)) for i in range(n + 1)]
    side = [(ex1, ey1 - (ey1 - ey0) * i / n, z_eave + lift(i / n)) for i in range(n + 1)]
    # soffit shadow under the eave (gives thickness)
    out.append(f'<path d="{iso.poly([(p[0], p[1], p[2] - 0.55) for p in front] + front[::-1])}" fill="#1A1410"></path>')
    out.append(f'<path d="{iso.poly([(p[0], p[1], p[2] - 0.55) for p in side] + side[::-1])}" fill="#120E0B"></path>')
    # roof planes
    out.append(f'<path d="{iso.poly(front + [(rx1, ry, zr), (rx0, ry, zr)])}" fill="url(#{P}-roofF)"></path>')
    out.append(f'<path d="{iso.poly(side + [(rx1, ry, zr)])}" fill="url(#{P}-roofS)"></path>')

    def hip_point(xa, corner, rend):
        t = (xa - corner[0]) / (rend[0] - corner[0]) if rend[0] != corner[0] else 0
        t = max(0.0, min(1.0, t))
        return tuple(corner[i] + (rend[i] - corner[i]) * t for i in range(3))

    # tile columns on the front plane
    cols = []
    caps = []
    x = ex0 + 0.6
    cL, cR = front[0], front[-1]
    while x < ex1 - 0.4:
        u = (x - ex0) / (ex1 - ex0)
        e = (x, ey1, z_eave + lift(u))
        if x < rx0:
            top = hip_point(x, cL, (rx0, ry, zr))
        elif x > rx1:
            top = hip_point(x, cR, (rx1, ry, zr))
        else:
            top = (x, ry, zr)
        cols.append(f'<path d="{iso.line([e, top])}" stroke="#20232A" stroke-width="2.6" stroke-linecap="round"></path>')
        a, b = iso(*e), iso(*top)
        cols.append(f'<path d="M{a[0] - 0.9:.1f} {a[1] - 0.4:.1f} L{b[0] - 0.7:.1f} {b[1] - 0.2:.1f}" stroke="#9AA2B2" stroke-opacity="0.55" stroke-width="0.8"></path>')
        caps.append(f'<circle cx="{a[0]:.1f}" cy="{a[1] + 0.6:.1f}" r="2.1" fill="#2E323A" stroke="#7A8292" stroke-width="0.6"></circle>')
        x += tile_step
    out.append("".join(cols))
    # tile columns on the side (hip) plane, converging on the ridge end
    sc = []
    for i in range(1, n, 1):
        p = side[i]
        if i % 1 == 0:
            sc.append(f'<path d="{iso.line([p, (rx1, ry, zr)])}" stroke="#16181E" stroke-width="1.8" stroke-opacity="0.9"></path>')
            a = iso(*p)
            caps.append(f'<circle cx="{a[0]:.1f}" cy="{a[1] + 0.6:.1f}" r="1.8" fill="#24272E" stroke="#5A6272" stroke-width="0.5"></circle>')
    out.append("".join(sc))
    # faint course lines parallel to the eave
    for k in range(1, 6):
        t = k / 6
        pts = []
        for i in range(n + 1):
            e = front[i]
            xx = e[0]
            top = hip_point(xx, cL, (rx0, ry, zr)) if xx < rx0 else (hip_point(xx, cR, (rx1, ry, zr)) if xx > rx1 else (xx, ry, zr))
            pts.append(tuple(e[j] + (top[j] - e[j]) * t for j in range(3)))
        out.append(f'<path d="{iso.line(pts)}" fill="none" stroke="#000" stroke-opacity="0.18" stroke-width="0.8"></path>')
    out.append("".join(caps))
    # hip ridges (white mortar + dark cap) and main ridge with end ornaments
    for a, b in ((cL, (rx0, ry, zr)), (cR, (rx1, ry, zr)), (side[-1], (rx1, ry, zr))):
        out.append(f'<path d="{iso.line([a, b])}" stroke="#E6E0D2" stroke-width="4.6" stroke-linecap="round"></path>'
                   f'<path d="{iso.line([a, b])}" stroke="#23262C" stroke-width="2.4" stroke-linecap="round"></path>')
    rz = [(rx0, ry, zr), (rx1, ry, zr)]
    out.append(f'<path d="{iso.line([(p[0], p[1], p[2] - 0.35) for p in rz])}" stroke="#ECE6D8" stroke-width="6.4" stroke-linecap="round"></path>'
               f'<path d="{iso.line([(p[0], p[1], p[2] + 0.25) for p in rz])}" stroke="#1E2126" stroke-width="3.6" stroke-linecap="round"></path>'
               f'<path d="{iso.line([(p[0], p[1], p[2] + 0.42) for p in rz])}" stroke="#7A8292" stroke-opacity="0.6" stroke-width="0.9"></path>')
    for rx, s in ((rx0, -1), (rx1, 1)):
        a = iso(rx, ry, zr + 0.3)
        out.append(f'<path d="M{a[0]:.1f} {a[1]:.1f} c{2 * s} -4 {6 * s} -6 {8 * s} -5 c-1 3 {-3 * s} 5 {-6 * s} 7 Z" fill="#23262C" stroke="#7A8292" stroke-width="0.6"></path>')
    # eave corner tips
    for c in (cL, cR, side[-1]):
        a = iso(*c)
        out.append(f'<circle cx="{a[0]:.1f}" cy="{a[1]:.1f}" r="2.2" fill="#E6E0D2"></circle>')
    return "".join(out)


def hall(iso, x0, y0, L, D, rng, base=2.0, wall=8.0, rise=7.4, over=2.6, bays=6, store=False):
    out = []
    top = base + wall
    bx0, bx1, by0, by1 = x0 - 1.4, x0 + L + 1.4, y0 - 1.4, y0 + D + 1.4
    # cast shadow toward bottom-right
    out.append(f'<path d="{iso.poly([(bx0, by1, 0), (bx1, by1, 0), (bx1, by0, 0), (bx1 + 9, by0 + 3, 0), (bx1 + 9, by1 + 4, 0), (bx0 + 7, by1 + 4, 0)])}" '
               f'fill="#0A0604" opacity="0.5" filter="url(#{P}-blur6)"></path>')
    # stone terrace (기단)
    out.append(f'<path d="{iso.poly([(bx0, by0, base), (bx1, by0, base), (bx1, by1, base), (bx0, by1, base)])}" fill="#8E8676"></path>')
    out.append(stone_rows(iso, "y", bx0, bx1, by1, 0, base, 2, rng))
    out.append(stone_rows(iso, "x", by0, by1, bx1, 0, base, 2, rng, dark=True))
    out.append(f'<path d="{iso.line([(bx0, by1, base), (bx1, by1, base), (bx1, by0, base)])}" fill="none" stroke="#D8D0BC" stroke-width="1"></path>')
    # step in the middle bay
    mid = x0 + L / 2
    for k, (dz, dy) in enumerate(((base * 0.5, 1.2), (base, 0))):
        sy = by1 + dy
        out.append(f'<path d="{iso.poly([(mid - 2.2, sy, dz), (mid + 2.2, sy, dz), (mid + 2.2, sy + 1.2, dz), (mid - 2.2, sy + 1.2, dz)])}" fill="#9A9282"></path>'
                   f'<path d="{iso.poly([(mid - 2.2, sy + 1.2, 0), (mid + 2.2, sy + 1.2, 0), (mid + 2.2, sy + 1.2, dz), (mid - 2.2, sy + 1.2, dz)])}" fill="#6E6858"></path>')
    # walls
    fy, fx = y0 + D, x0 + L
    wall_f = "#D9C9A6" if not store else "#6E4E34"
    wall_s = "#9A8A6C" if not store else "#4A3222"
    out.append(f'<path d="{iso.poly([(x0, fy, base), (fx, fy, base), (fx, fy, top), (x0, fy, top)])}" fill="{wall_f}"></path>')
    out.append(f'<path d="{iso.poly([(fx, fy, base), (fx, y0, base), (fx, y0, top), (fx, fy, top)])}" fill="{wall_s}"></path>')
    if store:
        # vertical planks
        x = x0 + 0.5
        while x < fx:
            out.append(f'<path d="{iso.line([(x, fy, base), (x, fy, top)])}" stroke="#3A2416" stroke-width="0.8" stroke-opacity="0.8"></path>')
            x += 0.62
        y = y0 + 0.5
        while y < fy:
            out.append(f'<path d="{iso.line([(fx, y, base), (fx, y, top)])}" stroke="#24160C" stroke-width="0.8" stroke-opacity="0.8"></path>')
            y += 0.62
        # big door with iron straps and brass lock
        dx0, dx1 = x0 + L / 2 - 2.0, x0 + L / 2 + 2.0
        out.append(f'<path d="{iso.poly([(dx0, fy, base), (dx1, fy, base), (dx1, fy, base + 5.4), (dx0, fy, base + 5.4)])}" fill="#4A3020" stroke="#1A0E06" stroke-width="0.8"></path>')
        for zz in (base + 1.2, base + 4.2):
            out.append(f'<path d="{iso.line([(dx0, fy, zz), (dx1, fy, zz)])}" stroke="#2A2420" stroke-width="2.2"></path>')
        lk = iso(x0 + L / 2, fy, base + 2.8)
        out.append(f'<rect x="{lk[0] - 2.4:.1f}" y="{lk[1] - 3:.1f}" width="4.8" height="6" rx="1" fill="url(#{P}-brass)" stroke="#3A2408" stroke-width="0.5"></rect>')
    else:
        # lattice doors glowing from inside, plaster upper band
        bw = L / bays
        for i in range(bays):
            a, b = x0 + i * bw + 0.55, x0 + (i + 1) * bw - 0.55
            za, zb = base + 0.9, base + 5.6
            out.append(f'<path d="{iso.poly([(a, fy, za), (b, fy, za), (b, fy, zb), (a, fy, zb)])}" fill="url(#{P}-lamp)"></path>')
            lat = []
            x = a + 0.62
            while x < b - 0.1:
                lat.append(iso.line([(x, fy, za), (x, fy, zb)]))
                x += 0.62
            z = za + 0.66
            while z < zb - 0.1:
                lat.append(iso.line([(a, fy, z), (b, fy, z)]))
                z += 0.66
            out.append(f'<path d="{" ".join(lat)}" fill="none" stroke="#3A2416" stroke-width="0.9"></path>')
            out.append(f'<path d="{iso.poly([(a, fy, za), (b, fy, za), (b, fy, zb), (a, fy, zb)])}" fill="none" stroke="#2A170C" stroke-width="1.6"></path>')
            mm = (a + b) / 2
            out.append(f'<path d="{iso.line([(mm, fy, za), (mm, fy, zb)])}" stroke="#2A170C" stroke-width="1.3"></path>')
        # shaded side: one dim door
        a, b = y0 + 1.2, y0 + D - 1.2
        out.append(f'<path d="{iso.poly([(fx, b, base + 0.9), (fx, a, base + 0.9), (fx, a, base + 5.6), (fx, b, base + 5.6)])}" fill="#B88A50" opacity="0.75"></path>')
        lat = []
        y = a + 0.62
        while y < b:
            lat.append(iso.line([(fx, y, base + 0.9), (fx, y, base + 5.6)]))
            y += 0.62
        z = base + 1.56
        while z < base + 5.5:
            lat.append(iso.line([(fx, a, z), (fx, b, z)]))
            z += 0.66
        out.append(f'<path d="{" ".join(lat)}" fill="none" stroke="#24140A" stroke-width="0.9"></path>')
    # timber posts and beams
    beams = []
    for i in range(bays + 1):
        x = x0 + i * L / bays
        beams.append(f'<path d="{iso.poly([(x - 0.42, fy + 0.05, base), (x + 0.42, fy + 0.05, base), (x + 0.42, fy + 0.05, top), (x - 0.42, fy + 0.05, top)])}" fill="url(#{P}-post)"></path>')
    for j in range(3):
        y = y0 + j * D / 2
        beams.append(f'<path d="{iso.poly([(fx + 0.05, y - 0.42, base), (fx + 0.05, y + 0.42, base), (fx + 0.05, y + 0.42, top), (fx + 0.05, y - 0.42, top)])}" fill="#3A2416"></path>')
    for z0, z1 in ((base, base + 0.75), (base + 5.6, base + 6.3)):
        beams.append(f'<path d="{iso.poly([(x0, fy + 0.06, z0), (fx, fy + 0.06, z0), (fx, fy + 0.06, z1), (x0, fy + 0.06, z1)])}" fill="#4A2E1C"></path>')
        beams.append(f'<path d="{iso.poly([(fx + 0.06, fy, z0), (fx + 0.06, y0, z0), (fx + 0.06, y0, z1), (fx + 0.06, fy, z1)])}" fill="#2E1C10"></path>')
    out.append("".join(beams))
    # dancheong band (bracket zone) on top of the walls
    zb0, zb1 = top - 1.3, top
    out.append(f'<path d="{iso.poly([(x0 - 0.3, fy + 0.1, zb0), (fx + 0.3, fy + 0.1, zb0), (fx + 0.3, fy + 0.1, zb1), (x0 - 0.3, fy + 0.1, zb1)])}" fill="#2E7A66"></path>')
    out.append(f'<path d="{iso.poly([(fx + 0.1, fy + 0.3, zb0), (fx + 0.1, y0 - 0.3, zb0), (fx + 0.1, y0 - 0.3, zb1), (fx + 0.1, fy + 0.3, zb1)])}" fill="#1E5446"></path>')
    motifs = []
    x = x0 + 0.5
    while x < fx - 0.3:
        c = iso(x, fy + 0.1, (zb0 + zb1) / 2)
        motifs.append(f'<path d="M{c[0]:.1f} {c[1] - 3.6:.1f} l3 3.6 l-3 3.6 l-3 -3.6 Z" fill="#E2B13C"></path>'
                      f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="1.5" fill="#C8432B"></circle>')
        e = iso(x + 0.55, fy + 0.1, zb1 - 0.15)
        motifs.append(f'<path d="M{e[0] - 1.6:.1f} {e[1]:.1f} h3.2" stroke="#EFE8D8" stroke-width="1.2"></path>')
        x += 1.1
    out.append("".join(motifs))
    out.append(f'<path d="{iso.line([(x0 - 0.3, fy + 0.1, zb0), (fx + 0.3, fy + 0.1, zb0)])}" stroke="#C8432B" stroke-width="1.4"></path>')
    # rafter ends row under the eave
    rafters = []
    x = x0 - over + 0.7
    while x < fx + over - 0.5:
        c = iso(x, fy + over * 0.62, top + 0.1)
        rafters.append(f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="1.7" fill="#2E7A66" stroke="#1A0E06" stroke-width="0.4"></circle>'
                       f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="0.7" fill="#E2B13C"></circle>')
        x += 0.95
    out.append("".join(rafters))
    out.append(roof(iso, x0, y0, L, D, top + 0.55, rise, over, rng))
    return "".join(out)


def lantern(x, y, s=1.0):
    """청사초롱 — red silk body with blue bands, glowing."""
    w, h = 8 * s, 12 * s
    return (f'<circle cx="{x:.1f}" cy="{y + h / 2:.1f}" r="{26 * s:.1f}" fill="url(#{P}-glow)" opacity="0.85"></circle>'
            f'<path d="M{x:.1f} {y - 10 * s:.1f} V{y:.1f}" stroke="#1A0E06" stroke-width="0.9"></path>'
            f'<rect x="{x - w / 2:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h * 0.22:.1f}" fill="#2C4C8C"></rect>'
            f'<rect x="{x - w / 2 - 0.6 * s:.1f}" y="{y + h * 0.22:.1f}" width="{w + 1.2 * s:.1f}" height="{h * 0.56:.1f}" rx="{2 * s:.1f}" fill="url(#{P}-silk)"></rect>'
            f'<rect x="{x - w / 2:.1f}" y="{y + h * 0.78:.1f}" width="{w:.1f}" height="{h * 0.22:.1f}" fill="#2C4C8C"></rect>'
            f'<path d="M{x - w * 0.2:.1f} {y + h * 0.26:.1f} V{y + h * 0.74:.1f}" stroke="#FFF2C8" stroke-opacity="0.75" stroke-width="{1.1 * s:.1f}"></path>'
            f'<path d="M{x:.1f} {y + h:.1f} v{4 * s:.1f}" stroke="#C8432B" stroke-width="{1.4 * s:.1f}"></path>'
            f'<path d="M{x - 1.4 * s:.1f} {y + h + 4 * s:.1f} h{2.8 * s:.1f} l-0.6 {5 * s:.1f} h-1.6 Z" fill="#C8432B"></path>')


def onggi(x, y, s, rng):
    """Glazed earthenware jar (옹기) with lid and specular."""
    w = 7 * s
    h = 8 * s
    return (f'<ellipse cx="{x + 2 * s:.1f}" cy="{y + 1 * s:.1f}" rx="{w * 0.95:.1f}" ry="{w * 0.38:.1f}" fill="#000" opacity="0.4"></ellipse>'
            f'<path d="M{x - w * 0.55:.1f} {y:.1f} C{x - w * 1.1:.1f} {y - h * 0.4:.1f} {x - w:.1f} {y - h * 0.95:.1f} {x - w * 0.5:.1f} {y - h:.1f} '
            f'H{x + w * 0.5:.1f} C{x + w:.1f} {y - h * 0.95:.1f} {x + w * 1.1:.1f} {y - h * 0.4:.1f} {x + w * 0.55:.1f} {y:.1f} Z" fill="url(#{P}-onggi)"></path>'
            f'<ellipse cx="{x:.1f}" cy="{y - h:.1f}" rx="{w * 0.5:.1f}" ry="{w * 0.18:.1f}" fill="#2A160C"></ellipse>'
            f'<path d="M{x - w * 0.56:.1f} {y - h - 0.6 * s:.1f} Q{x:.1f} {y - h - 3.6 * s:.1f} {x + w * 0.56:.1f} {y - h - 0.6 * s:.1f} Z" fill="#4A2A18"></path>'
            f'<path d="M{x - w * 0.62:.1f} {y - h * 0.62:.1f} Q{x - w * 0.6:.1f} {y - h * 0.86:.1f} {x - w * 0.3:.1f} {y - h * 0.9:.1f}" fill="none" stroke="#FFE6C8" stroke-opacity="0.75" stroke-width="{1.1 * s:.1f}" stroke-linecap="round"></path>'
            f'<path d="M{x - w * 0.8:.1f} {y - h * 0.5:.1f} Q{x:.1f} {y - h * 0.4:.1f} {x + w * 0.8:.1f} {y - h * 0.5:.1f}" fill="none" stroke="#1A0A04" stroke-opacity="0.45" stroke-width="0.8"></path>')


def persimmon_tree(x, y, rng):
    out = []
    out.append(f'<ellipse cx="{x + 30}" cy="{y + 6}" rx="70" ry="20" fill="#0A0604" opacity="0.4" filter="url(#{P}-blur6)"></ellipse>')
    out.append(f'<path d="M{x - 5} {y} C{x - 4} {y - 40} {x - 10} {y - 70} {x - 4} {y - 110} L{x + 4} {y - 110} C{x + 2} {y - 70} {x + 8} {y - 40} {x + 7} {y} Z" fill="url(#{P}-trunk)"></path>')
    for d in (f"M{x} {y - 70} C{x - 20} {y - 90} {x - 40} {y - 96} {x - 58} {y - 110}",
              f"M{x} {y - 88} C{x + 18} {y - 100} {x + 36} {y - 112} {x + 52} {y - 128}",
              f"M{x - 2} {y - 105} C{x - 6} {y - 125} {x - 2} {y - 140} {x + 6} {y - 156}"):
        out.append(f'<path d="{d}" fill="none" stroke="#3A2414" stroke-width="4" stroke-linecap="round"></path>')
    blobs = []
    for i in range(70):
        a = rng.random() * math.tau
        r = rng.random() ** 0.6
        bx = x + math.cos(a) * 68 * r
        by = y - 120 + math.sin(a) * 46 * r
        rr = 9 + rng.random() * 12
        c = rng.choice(("#3E4A22", "#4E5626", "#5E5A24", "#6A4E1E", "#7A3A16", "#4A3A1A"))
        blobs.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="{rr:.1f}" fill="{c}" opacity="0.95"></circle>')
    out.append("".join(blobs))
    hi = []
    for i in range(36):
        a = rng.random() * math.tau
        r = rng.random() ** 0.7
        bx = x - 14 + math.cos(a) * 52 * r
        by = y - 132 + math.sin(a) * 30 * r
        hi.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="{4 + rng.random() * 6:.1f}" fill="{rng.choice(("#8A7A34", "#A8843A", "#9A5A22"))}" opacity="0.8"></circle>')
    out.append("".join(hi))
    fruit = []
    for i in range(30):
        a = rng.random() * math.tau
        r = rng.random() ** 0.8
        fx = x + math.cos(a) * 62 * r
        fy = y - 118 + math.sin(a) * 42 * r
        fruit.append(f'<circle cx="{fx:.1f}" cy="{fy:.1f}" r="3.2" fill="#E8742A" stroke="#7A2E0E" stroke-width="0.6"></circle>'
                     f'<circle cx="{fx - 1:.1f}" cy="{fy - 1.1:.1f}" r="1" fill="#FFD8A0"></circle>')
    out.append("".join(fruit))
    return "".join(out)


def stone_wall(iso, a0, a1, fixed, axis, rng, h=3.0):
    out = []
    if axis == "y":  # wall along x at y=fixed, visible face +y
        out.append(stone_rows(iso, "y", a0, a1, fixed, 0, h, 3, rng, base=(118, 108, 92)))
        cap = [(a0, fixed - 0.8, h), (a1, fixed - 0.8, h), (a1, fixed + 0.5, h - 0.2), (a0, fixed + 0.5, h - 0.2)]
        out.append(f'<path d="{iso.poly([(a0, fixed - 0.8, h + 0.9), (a1, fixed - 0.8, h + 0.9)] + cap[2:][::-1][::-1])}" fill="#3A3E46"></path>')
        ridge = [(a0, fixed - 0.6, h + 1.0), (a1, fixed - 0.6, h + 1.0)]
        out.append(f'<path d="{iso.poly([(a0, fixed + 0.6, h - 0.2), (a1, fixed + 0.6, h - 0.2), ridge[1], ridge[0]])}" fill="#4A4F58"></path>')
        x = a0 + 0.4
        t = []
        while x < a1:
            t.append(iso.line([(x, fixed + 0.6, h - 0.2), (x, fixed - 0.6, h + 1.0)]))
            x += 0.8
        out.append(f'<path d="{" ".join(t)}" stroke="#1E2126" stroke-width="1.6"></path>')
        out.append(f'<path d="{iso.line(ridge)}" stroke="#E6E0D2" stroke-width="2.6"></path><path d="{iso.line(ridge)}" stroke="#23262C" stroke-width="1.4"></path>')
    else:  # wall along y at x=fixed, visible face +x
        out.append(stone_rows(iso, "x", a0, a1, fixed, 0, h, 3, rng, base=(118, 108, 92), dark=True))
        ridge = [(fixed - 0.6, a0, h + 1.0), (fixed - 0.6, a1, h + 1.0)]
        out.append(f'<path d="{iso.poly([(fixed + 0.6, a0, h - 0.2), (fixed + 0.6, a1, h - 0.2), ridge[1], ridge[0]])}" fill="#30343B"></path>')
        y = a0 + 0.4
        t = []
        while y < a1:
            t.append(iso.line([(fixed + 0.6, y, h - 0.2), (fixed - 0.6, y, h + 1.0)]))
            y += 0.8
        out.append(f'<path d="{" ".join(t)}" stroke="#16181C" stroke-width="1.6"></path>')
        out.append(f'<path d="{iso.line(ridge)}" stroke="#D6D0C2" stroke-width="2.6"></path><path d="{iso.line(ridge)}" stroke="#23262C" stroke-width="1.4"></path>')
    return "".join(out)


def well(iso, x, y, rng):
    out = []
    c = iso(x, y, 0)
    out.append(f'<ellipse cx="{c[0] + 10:.1f}" cy="{c[1] + 6:.1f}" rx="30" ry="12" fill="#0A0604" opacity="0.45" filter="url(#{P}-blur6)"></ellipse>')
    # stone ring: an iso cylinder of stacked stones
    r = 2.6
    top_z = 2.0
    ring = []
    for k in range(18):
        a0 = k / 18 * math.tau
        a1 = (k + 1) / 18 * math.tau
        if math.sin((a0 + a1) / 2 + math.pi / 4) < -0.2:
            continue
        for zr in range(2):
            za, zb = zr * 1.0, (zr + 1) * 1.0
            pts = [(x + r * math.cos(a0), y + r * math.sin(a0), za + 0.06), (x + r * math.cos(a1), y + r * math.sin(a1), za + 0.06),
                   (x + r * math.cos(a1), y + r * math.sin(a1), zb - 0.06), (x + r * math.cos(a0), y + r * math.sin(a0), zb - 0.06)]
            k2 = rng.randint(-20, 16)
            shade = 0.75 + 0.25 * math.cos((a0 + a1) / 2 - 2.4)
            col = _shade((int(130 * shade), int(122 * shade), int(106 * shade)), k2)
            ring.append(f'<path d="{iso.poly(pts)}" fill="{col}"></path>')
    out.append("".join(ring))
    rim = [(x + r * math.cos(a / 24 * math.tau), y + r * math.sin(a / 24 * math.tau), top_z) for a in range(24)]
    inner = [(x + (r - 0.7) * math.cos(a / 24 * math.tau), y + (r - 0.7) * math.sin(a / 24 * math.tau), top_z) for a in range(24)]
    out.append(f'<path d="{iso.poly(rim)}" fill="#A69E8C"></path><path d="{iso.poly(inner)}" fill="#0E1418"></path>')
    w = iso(x + 0.4, y + 0.4, top_z - 0.3)
    out.append(f'<ellipse cx="{w[0]:.1f}" cy="{w[1]:.1f}" rx="7" ry="3" fill="#2F6E8E" opacity="0.5"></ellipse>')
    # wooden frame + pulley + bucket
    for py in (y - r - 0.2, y + r + 0.2):
        out.append(f'<path d="{iso.line([(x, py, 0.5), (x, py, 6.4)])}" stroke="#4A2E1C" stroke-width="3.2"></path>'
                   f'<path d="{iso.line([(x, py, 0.5), (x, py, 6.4)])}" stroke="#8A6040" stroke-width="1" transform="translate(-0.8 0)"></path>')
    out.append(f'<path d="{iso.line([(x, y - r - 0.6, 6.4), (x, y + r + 0.6, 6.4)])}" stroke="#3A2416" stroke-width="3"></path>')
    pc = iso(x, y, 6.0)
    out.append(f'<circle cx="{pc[0]:.1f}" cy="{pc[1]:.1f}" r="3.4" fill="#5A3A22" stroke="#1A0E06" stroke-width="0.8"></circle>')
    out.append(f'<path d="M{pc[0]:.1f} {pc[1]:.1f} V{pc[1] + 26:.1f}" stroke="#8A7A5A" stroke-width="0.9"></path>')
    bk = (pc[0] + 0.5, pc[1] + 26)
    out.append(f'<path d="M{bk[0] - 4:.1f} {bk[1]:.1f} h8 l-1 7 h-6 Z" fill="#6A4A2E" stroke="#1A0E06" stroke-width="0.7"></path>'
               f'<path d="M{bk[0] - 4:.1f} {bk[1] + 2.4:.1f} h8" stroke="#2A2420" stroke-width="1"></path>')
    return "".join(out)


def kite_line(p1, p2, rng):
    out = [f'<path d="M{p1[0]:.1f} {p1[1]:.1f} Q{(p1[0] + p2[0]) / 2:.1f} {max(p1[1], p2[1]) + 18:.1f} {p2[0]:.1f} {p2[1]:.1f}" fill="none" stroke="#8A7A5A" stroke-width="1"></path>']
    for i, t in enumerate((0.22, 0.5, 0.78)):
        x = p1[0] + (p2[0] - p1[0]) * t
        y = p1[1] + (p2[1] - p1[1]) * t + 18 * 4 * t * (1 - t) * 0.5
        rot = rng.uniform(-10, 10)
        top = ("#C8432B", "#2C4C8C", "#E2B13C")[i]
        out.append(f'<g transform="translate({x:.1f} {y + 2:.1f}) rotate({rot:.1f})">'
                   f'<rect x="-7" y="0" width="14" height="17" fill="#F2EBD6" stroke="#6A5A3A" stroke-width="0.7"></rect>'
                   f'<rect x="-7" y="0" width="14" height="4" fill="{top}"></rect>'
                   f'<path d="M-7 0 L7 17 M7 0 L-7 17 M0 0 V17" stroke="#8A7A5A" stroke-width="0.5"></path>'
                   f'<circle cx="0" cy="9" r="3" fill="#2A2018"></circle>'
                   f'<path d="M-3 17 q-2 6 1 10 M3 17 q2 6 -1 10" fill="none" stroke="{top}" stroke-width="1.2"></path></g>')
    return "".join(out)


def sotdae(x, y, h=118):
    return (f'<ellipse cx="{x + 6}" cy="{y + 2}" rx="10" ry="3" fill="#000" opacity="0.4"></ellipse>'
            f'<path d="M{x - 1.6} {y} L{x - 1} {y - h} L{x + 1} {y - h} L{x + 1.6} {y} Z" fill="url(#{P}-trunk)"></path>'
            f'<g transform="translate({x} {y - h})">'
            f'<path d="M-12 -2 C-8 -8 2 -9 8 -6 L14 -9 L11 -4 C10 0 2 2 -6 1 Z" fill="#8A6A44" stroke="#2A1A0E" stroke-width="0.7"></path>'
            f'<path d="M-6 -4 C-2 -6 3 -6 6 -5" fill="none" stroke="#C9A878" stroke-width="0.8"></path>'
            f'<circle cx="8.4" cy="-6.4" r="0.8" fill="#1A0E06"></circle></g>')


def cart(x, y):
    return (f'<ellipse cx="{x + 8}" cy="{y + 4}" rx="34" ry="9" fill="#000" opacity="0.4" filter="url(#{P}-blur1)"></ellipse>'
            f'<path d="M{x - 26} {y - 10} L{x + 14} {y + 10} L{x + 30} {y + 2} L{x - 10} {y - 18} Z" fill="#6A4A2E" stroke="#1A0E06" stroke-width="0.8"></path>'
            f'<path d="M{x - 26} {y - 10} L{x + 14} {y + 10} L{x + 14} {y + 14} L{x - 26} {y - 6} Z" fill="#3A2416"></path>'
            f'<path d="M{x - 20} {y - 18} q6 -14 22 -8 q10 4 14 12 q-16 8 -36 -4 Z" fill="#B49A6A" stroke="#4A3A20" stroke-width="0.8"></path>'
            f'<path d="M{x - 14} {y - 20} q8 -4 18 2 M{x - 8} {y - 26} l6 14" fill="none" stroke="#6A5A3A" stroke-width="0.9"></path>'
            f'<path d="M{x + 4} {y - 22} q8 -10 18 -2 q4 6 2 12 q-12 2 -20 -10 Z" fill="#3B4E8C" stroke="#1A2240" stroke-width="0.8"></path>'
            f'<ellipse cx="{x - 4}" cy="{y + 4}" rx="5" ry="10" fill="#4A2E1C" stroke="#1A0E06" stroke-width="1.2"></ellipse>'
            f'<ellipse cx="{x - 4}" cy="{y + 4}" rx="2" ry="4" fill="none" stroke="#8A6040" stroke-width="1"></ellipse>'
            f'<path d="M{x + 30} {y + 2} L{x + 58} {y + 16} M{x + 22} {y - 2} L{x + 50} {y + 12}" stroke="#4A2E1C" stroke-width="2.4" stroke-linecap="round"></path>')


def courtyard():
    rng = random.Random(42)
    iso = Iso(470, 200, 7)
    o = []
    defs = (f'<linearGradient id="{P}-sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#110F1E"></stop><stop offset="0.28" stop-color="#241D33"></stop>'
            f'<stop offset="0.46" stop-color="#4A3040"></stop><stop offset="0.6" stop-color="#7A4A3C"></stop><stop offset="1" stop-color="#2A1E18"></stop></linearGradient>'
            f'<radialGradient id="{P}-moon" cx="0.4" cy="0.38" r="0.62"><stop offset="0" stop-color="#FFF8E4"></stop><stop offset="0.7" stop-color="#F2E2B8"></stop><stop offset="1" stop-color="#C9B488"></stop></radialGradient>'
            f'<linearGradient id="{P}-earth" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6E5C40"></stop><stop offset="0.5" stop-color="#5A4A32"></stop><stop offset="1" stop-color="#3A2E1E"></stop></linearGradient>'
            f'<linearGradient id="{P}-roofF" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#3A3F48"></stop><stop offset="1" stop-color="#626A78"></stop></linearGradient>'
            f'<linearGradient id="{P}-roofS" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#22252C"></stop><stop offset="1" stop-color="#3A3F4A"></stop></linearGradient>'
            f'<linearGradient id="{P}-lamp" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE6B0"></stop><stop offset="0.6" stop-color="#F2B860"></stop><stop offset="1" stop-color="#C87A34"></stop></linearGradient>'
            f'<linearGradient id="{P}-post" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#7A5236"></stop><stop offset="1" stop-color="#3A2214"></stop></linearGradient>'
            f'<linearGradient id="{P}-silk" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FF9A6A"></stop><stop offset="0.5" stop-color="#E0482A"></stop><stop offset="1" stop-color="#8A1E10"></stop></linearGradient>'
            f'<linearGradient id="{P}-trunk" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#6A4A30"></stop><stop offset="1" stop-color="#2A1A0E"></stop></linearGradient>'
            f'<radialGradient id="{P}-onggi" cx="0.32" cy="0.3" r="0.8"><stop offset="0" stop-color="#9A5A34"></stop><stop offset="0.5" stop-color="#5A2E18"></stop><stop offset="1" stop-color="#24120A"></stop></radialGradient>'
            f'<clipPath id="{P}-gclip"><path d="{iso.poly([(-20, -20, 0), (150, -20, 0), (150, 150, 0), (-20, 150, 0)])}"></path></clipPath>')
    o.append(f'<defs>{defs}</defs>')
    # sky, moon, mountains
    o.append(f'<rect width="{W}" height="{H}" fill="url(#{P}-sky)"></rect>')
    for _ in range(90):
        x, y = rng.random() * W, 70 + rng.random() * 170
        o.append(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{0.5 + rng.random() * 0.8:.1f}" fill="#FFF6E0" opacity="{0.3 + rng.random() * 0.5:.2f}"></circle>')
    o.append(f'<circle cx="1010" cy="150" r="70" fill="#F2E2B8" opacity="0.18" filter="url(#{P}-blur14)"></circle>'
             f'<circle cx="1010" cy="150" r="30" fill="url(#{P}-moon)"></circle>'
             f'<path d="M996 140 q6 -4 10 1 M1014 160 q5 -2 7 2" fill="none" stroke="#C9B488" stroke-width="1.4" opacity="0.6"></path>')
    for d, c, op in ((ridge(W, 250, 60, 0.6), "#2E2838", 0.9), (ridge(W, 290, 48, 2.2), "#231E2A", 1), (ridge(W, 330, 36, 4.4), "#1A1620", 1)):
        o.append(f'<path d="{d}" fill="{c}" opacity="{op}"></path>')
    o.append(f'<ellipse cx="900" cy="300" rx="420" ry="16" fill="#E8D8C8" opacity="0.08" filter="url(#{P}-blur14)"></ellipse>')
    # distant pine silhouettes on the ridge
    pines = []
    for i in range(16):
        x = 820 + i * 38 + rng.random() * 18
        y = 300 + rng.random() * 30
        s = 0.8 + rng.random() * 0.6
        pines.append(f'<path d="M{x:.0f} {y - 46 * s:.0f} l{9 * s:.1f} {16 * s:.1f} h{-4 * s:.1f} l{11 * s:.1f} {14 * s:.1f} h{-5 * s:.1f} l{12 * s:.1f} {16 * s:.1f} h{-46 * s:.1f} l{12 * s:.1f} {-16 * s:.1f} h{-5 * s:.1f} l{11 * s:.1f} {-14 * s:.1f} h{-4 * s:.1f} Z" fill="#141018"></path>')
    o.append("".join(pines))

    # ground
    o.append(f'<path d="{iso.poly([(-20, -20, 0), (150, -20, 0), (150, 150, 0), (-20, 150, 0)])}" fill="url(#{P}-earth)"></path>')
    g = [f'<g clip-path="url(#{P}-gclip)">']
    g.append(f'<rect width="{W}" height="{H}" fill="#000" filter="url(#{P}-ground)" opacity="0.9"></rect>')
    for _ in range(60):
        x, y = rng.uniform(-18, 90), rng.uniform(-18, 60)
        c = iso(x, y)
        g.append(f'<ellipse cx="{c[0]:.0f}" cy="{c[1]:.0f}" rx="{20 + rng.random() * 40:.0f}" ry="{8 + rng.random() * 14:.0f}" '
                 f'fill="{rng.choice(("#7A6648", "#4A3C28", "#6A5A3C"))}" opacity="0.35"></ellipse>')
    # lamplight pools
    for x, y in ((14, 18), (34, 18), (54, 18), (60, 6)):
        c = iso(x, y)
        g.append(f'<ellipse cx="{c[0]:.0f}" cy="{c[1]:.0f}" rx="90" ry="36" fill="#F2A04A" opacity="0.22" filter="url(#{P}-blur14)"></ellipse>')
    # stepping-stone path from the gate to the hall
    for k in range(10):
        x, y = 23 + k * 0.4 + rng.uniform(-0.3, 0.3), 18 + k * 3.1
        c = iso(x, y)
        g.append(f'<ellipse cx="{c[0] + 1:.1f}" cy="{c[1] + 2:.1f}" rx="16" ry="7" fill="#000" opacity="0.35"></ellipse>'
                 f'<ellipse cx="{c[0]:.1f}" cy="{c[1]:.1f}" rx="16" ry="7.4" fill="#8E8878"></ellipse>'
                 f'<ellipse cx="{c[0] - 4:.1f}" cy="{c[1] - 2:.1f}" rx="8" ry="2.8" fill="#C8C0AE" opacity="0.45"></ellipse>')
    for _ in range(140):
        x, y = rng.uniform(-18, 100), rng.uniform(-18, 70)
        c = iso(x, y)
        g.append(pebble(c[0], c[1], rng, 0.8))
    for _ in range(36):
        x, y = rng.uniform(40, 70), rng.uniform(-18, 6)
        c = iso(x, y)
        g.append(f'<ellipse cx="{c[0]:.1f}" cy="{c[1]:.1f}" rx="2.6" ry="1.3" fill="{rng.choice(("#C8582A", "#E2882E", "#9A3A16", "#D8A040"))}" '
                 f'transform="rotate({rng.uniform(-60, 60):.0f} {c[0]:.1f} {c[1]:.1f})"></ellipse>')
    g.append("</g>")
    o.append("".join(g))
    # walls along the back edges
    o.append(stone_wall(iso, -20, 150, -20, "y", rng))
    o.append(stone_wall(iso, -20, 150, -20, "x", rng))
    for _ in range(50):
        x = rng.uniform(-18, 120)
        c = iso(x, -19.2)
        o.append(grass_tuft(c[0], c[1], rng, 1.1))
    # persimmon tree behind the storehouse
    t = iso(48, -16)
    o.append(persimmon_tree(t[0], t[1], rng))
    # buildings (back to front)
    o.append(hall(iso, 56, -18, 12, 12, rng, base=1.4, wall=6.4, rise=5.6, over=2.0, bays=2, store=True))
    o.append(hall(iso, 0, 0, 46, 14, rng))
    # lanterns under the hall eaves
    for x in (8, 23, 38):
        c = iso(x, 14 + 2.0, 10.6)
        o.append(lantern(c[0], c[1] + 4, 1.1))
    c = iso(46 + 2.0, 7, 10.6)
    o.append(lantern(c[0], c[1] + 4, 1.0))
    # props in the open courtyard
    o.append(well(iso, 54, 14, rng))
    a, b = iso(50, -4, 9.0), iso(66, 6, 9.0)
    for q, base_pt in ((a, iso(50, -4)), (b, iso(66, 6))):
        o.append(f'<path d="M{base_pt[0]:.1f} {base_pt[1]:.1f} V{q[1]:.1f}" stroke="#4A2E1C" stroke-width="3"></path>')
    o.append(kite_line(a, b, rng))
    s = iso(62, -6)
    o.append(sotdae(s[0], s[1], 110))
    for (x, y, sc) in ((60, 22, 1.4), (62, 23.6, 1.15), (58.6, 24.4, 1.25), (61, 25.6, 1.0), (63.4, 21.4, 1.0)):
        c = iso(x, y)
        o.append(onggi(c[0], c[1], sc, rng))
    c = iso(70, 14)
    o.append(cart(c[0], c[1]))
    # foreground lamp post on the right
    lp = iso(74, 4)
    o.append(f'<path d="M{lp[0]:.1f} {lp[1]:.1f} V{lp[1] - 74:.1f} h-14" fill="none" stroke="#3A2416" stroke-width="3"></path>')
    o.append(lantern(lp[0] - 14, lp[1] - 74 + 8, 1.15))
    # vignette and a soft dark wash behind the side columns
    o.append(f'<rect width="{W}" height="{H}" fill="url(#{P}-vig)"></rect>')
    vig = (f'<radialGradient id="{P}-vig" cx="0.52" cy="0.42" r="0.72"><stop offset="0.5" stop-color="#000" stop-opacity="0"></stop>'
           f'<stop offset="1" stop-color="#000" stop-opacity="0.7"></stop></radialGradient>')
    return (f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}" aria-hidden="true" style="position: absolute; inset: 0; z-index: 0">'
            f'<defs>{vig}</defs>{"".join(o)}</svg>')


# ==========================================================================
# Panels
# ==========================================================================

def field(label, value, hint, focus=False, mono=True, full=False):
    return f"""
        <label style="display: flex; flex-direction: column; gap: 5px;{' grid-column: 1 / -1;' if full else ''}">
          <span style="font-size: 12px; font-weight: 700; color: {P_MUTED}">{label}</span>
          {lacquer_slot(value, focus=focus, mono=mono, h=36)}
          {f'<span style="font-size: 11.5px; color: {P_MUTED}">{hint}</span>' if hint else ''}
        </label>"""


def connect_panel():
    fields = "".join([
        field("서버 주소", "ws://127.0.0.1:8080/ws", "ws:// 또는 wss:// 주소", full=True),
        field("플레이어 이름", "commander", "", focus=True),
        field("방 ID", "duel-01", ""),
        field("sim 버전", "dev", ""),
    ])
    inner = (panel_title("서버 접속", stamp("protocol v1", P_MUTED, mono=True))
             + f'<div style="display: grid; grid-template-columns: 1.3fr 1fr 0.7fr; gap: 10px 10px">{fields}</div>')
    return ornate(P, inner, pad="12px 16px 14px", gap=10)


def resume_panel():
    inner = f"""
        {panel_title("이전 세션 이어하기", stamp(dot(P_GREEN) + "보관 중", P_GREEN))}
        <div style="display: flex; align-items: center; gap: 12px">
          <span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px">
            <span style="font-family: {MONO}; font-size: 13px; font-weight: 600; color: {P_INK}">resume-3f9a•••c21e</span>
            <span style="font-size: 12px; color: {P_MUTED}">duel-01 · 15초간 자리 유지</span>
          </span>
          {btn("gold", "이어하기", "R", h=36, size=13)}
        </div>
        <div style="display: flex; flex-direction: column; gap: 5px">
          <div style="position: relative; height: 8px; border-radius: 2px; background: #2A1A10; box-shadow: inset 0 1px 2px rgba(0,0,0,0.7), 0 1px 0 rgba(255,248,226,0.7)">
            <div style="position: absolute; left: 1px; top: 1px; bottom: 1px; width: 74%; border-radius: 1px; background: linear-gradient(180deg, #A8E0B8, #3E8F6A); box-shadow: 0 0 6px rgba(140,199,154,0.6)"></div>
          </div>
          <div style="display: flex; justify-content: space-between; font-family: {MONO}; font-size: 11px; color: {P_MUTED}">
            <span>남은 시간 11.2s / 15.0s</span><span>토큰 일부만 표시</span>
          </div>
        </div>"""
    return ornate(P, inner, pad="12px 16px 12px", gap=10)


def connect_buttons():
    return (f'<div style="display: flex; gap: 12px; padding: 4px 4px 6px">'
            f'{btn("red", "접속", "Enter", h=48, size=20, extra="flex: 1", disp=True)}'
            f'{btn("wood", "관전 접속", None, h=48, size=14, extra="width: 112px")}</div>')


ROOMS = [
    ("duel-01", "한강 나루", "1v1", "2/2", "준비 중", P_OCHRE, "12ms", True),
    ("duel-02", "백두 능선", "1v1", "2/2", "진행 중", P_RED, "18ms", False),
    ("practice-ai", "솔숲 고개", "AI 연습", "1/2", "대기", P_GREEN, "9ms", False),
    ("river-ferry", "삼남 장터", "1v1", "0/2", "빈 방", P_MUTED, "24ms", False),
]


def room_list():
    cols = "grid-template-columns: 90px minmax(0, 1fr) 28px 64px 34px"
    head = (f'<div style="display: grid; {cols}; gap: 6px; padding: 0 6px; font-size: 11.5px; font-weight: 700; color: {P_MUTED}">'
            f'<span>방 ID</span><span>지도</span><span>인원</span><span>상태</span><span style="text-align: right">지연</span></div>')
    rows = []
    for rid, mapn, mode, ppl, state, scol, ping, sel in ROOMS:
        bg = ("background: linear-gradient(90deg, rgba(158,46,26,0.20), rgba(158,46,26,0.04)); box-shadow: inset 3px 0 0 #9E2E1A, inset 0 0 0 1px rgba(158,46,26,0.35);"
              if sel else "")
        rows.append(
            f'<div style="display: grid; {cols}; gap: 6px; align-items: center; height: 32px; padding: 0 6px; border-bottom: 1px solid rgba(110,78,38,0.35); font-size: 12.5px; {bg}">'
            f'<span style="font-family: {MONO}; font-size: 12px; font-weight: {700 if sel else 500}; color: {P_INK}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{rid}</span>'
            f'<span style="color: {P_INK}; white-space: nowrap">{mapn}</span>'
            f'<span style="font-family: {MONO}; color: {P_MUTED}">{ppl}</span>'
            f'<span style="display: flex; align-items: center; gap: 5px; font-weight: 600; font-size: 12px; color: {scol}; white-space: nowrap">{dot(scol, 6)}{state}</span>'
            f'<span style="text-align: right; font-family: {MONO}; color: {P_MUTED}">{ping}</span></div>')
    search = (f'<span style="display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 9px; width: 112px; box-sizing: border-box; border-radius: 2px; '
              f'background: linear-gradient(180deg, #160C06, #2A1A10); box-shadow: inset 0 2px 3px rgba(0,0,0,0.6), 0 1px 0 rgba(255,248,226,0.7); font-size: 12px; color: #C9B48A">'
              f'<svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true"><circle cx="5" cy="5" r="3.6" fill="none" stroke="#C9B48A" stroke-width="1.4"></circle><path d="M7.8 7.8 L11 11" stroke="#C9B48A" stroke-width="1.4"></path></svg>방 찾기</span>')
    inner = (panel_title("방 목록", search, "4개")
             + head + f'<div style="display: flex; flex-direction: column; margin-top: -4px">{"".join(rows)}</div>')
    return ornate(P, inner, pad="12px 14px 10px", gap=8)


def race_picker(slot, selected, locked=False, hover=None):
    items = []
    for r in ("r1", "r2", "r3"):
        info = RACES[r]
        sel = r == selected
        hov = r == hover
        state = "selected" if sel else ("normal" if hov else "dim")
        tag = (f'<span style="position: absolute; top: -6px; right: 4px; z-index: 2">{seal(f"{P}-{slot}-{r}-pick", "選", 24, rotate=8)}</span>'
               if sel and locked else "")
        hov_ring = (f'<span style="position: absolute; inset: -4px 6px auto 6px; height: 4px; background: {info["color"]}; opacity: 0.0"></span>' if hov else "")
        col = P_INK if (sel or hov) else P_MUTED
        sub = info["ink"] if (sel or hov) else P_MUTED
        items.append(
            f'<div style="position: relative; flex: 1; display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 6px 2px 4px; border-radius: 3px; '
            f'{"background: rgba(255,246,220,0.55); box-shadow: inset 0 0 0 1.5px " + info["ink"] + ";" if sel else ("background: rgba(255,246,220,0.3); box-shadow: inset 0 0 0 1px rgba(110,78,38,0.5);" if hov else "")}">'
            f'{tag}{hov_ring}{medallion(P, f"{slot}-{r}", r, 60, state)}'
            f'<span style="font-size: 12.5px; font-weight: 700; color: {col}; white-space: nowrap"><span style="font-family: {DISPLAY}; font-weight: 800; color: {info["ink"]}">{info["han"]}</span> {info["ko"]}</span>'
            f'</div>')
    return f'<div style="display: flex; gap: 6px">{"".join(items)}</div>'


def slot_panel(slot, name, meta, race, ready, me=False, hover=None, extra=""):
    rinfo = RACES[race]
    if ready:
        state = stamp(dot(P_GREEN) + "준비 완료", P_GREEN, filled=False)
    else:
        state = stamp("종족 선택 중…", P_OCHRE)
    me_tag = stamp("나", P_RED, filled=True) if me else ""
    inner = f"""
        <div style="display: flex; align-items: center; gap: 10px">
          <span style="font-family: {MONO}; font-size: 12px; font-weight: 700; color: {rinfo['ink']}">{slot.upper()}</span>
          <span style="font-family: {DISPLAY}; font-weight: 800; font-size: 21px; color: {P_INK}">{name}</span>{me_tag}
          <span style="margin-left: auto; font-family: {MONO}; font-size: 11px; color: {P_MUTED}">{"지연 12ms" if me else "지연 18ms"}</span>
        </div>
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: -4px">
          <span style="font-size: 12px; color: {P_MUTED}">{meta}</span>{state}
        </div>
        {race_picker(slot, race, locked=ready, hover=hover)}
        {extra}"""
    return ornate(P, inner, pad="10px 12px 10px", gap=10, extra="flex: 1; min-width: 0")


def stage():
    plaque = f"""
      <div style="position: relative; align-self: center; margin-top: 18px">
        <svg width="300" height="26" viewBox="0 0 300 26" aria-hidden="true" style="position: absolute; left: 50%; top: -24px; transform: translateX(-50%)">
          <path d="M70 26 L130 2 M230 26 L170 2" stroke="#2A1A0E" stroke-width="1.6"></path>
          <circle cx="130" cy="2" r="2" fill="#B48A3E"></circle><circle cx="170" cy="2" r="2" fill="#B48A3E"></circle>
        </svg>
        <div style="position: relative; padding: 11px 34px 10px; background: {LACQUER_BG}; border-radius: 3px; display: flex; flex-direction: column; align-items: center; gap: 5px;
             box-shadow: 0 0 0 1px #0E0703, 0 0 0 4px #6A4430, 0 0 0 5px #B48A3E, 0 0 0 6px #0E0703, inset 0 1px 0 rgba(255,214,160,0.2), 0 14px 28px rgba(0,0,0,0.65)">
          {gold_text("한강 나루", 30, ls="0.18em")}
          <span style="font-family: {MONO}; font-size: 11px; letter-spacing: 0.16em; color: #D9C69A">ROOM duel-01 · HOST commander</span>
          <span style="position: absolute; left: 10px; top: 50%; transform: translateY(-50%)">{cloud_mark("#B48A3E", 18)}</span>
          <span style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%) scaleX(-1)">{cloud_mark("#B48A3E", 18)}</span>
        </div>
      </div>"""
    portraits = f"""
      <div style="position: relative; margin-top: auto; display: flex; align-items: flex-end; justify-content: space-between; padding: 0 64px; height: 214px">
        <div style="display: flex; flex-direction: column; align-items: center; filter: drop-shadow(0 14px 18px rgba(0,0,0,0.6))">{portrait(P, "p1", "r1", 160, 196)}</div>
        <div style="position: absolute; left: 50%; bottom: 40px; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; gap: 2px; filter: drop-shadow(0 10px 18px rgba(0,0,0,0.7))">
          {seal(P + "-vs", "對", 92, rotate=-6, font_scale=0.62, sub="VERSUS")}
        </div>
        <div style="display: flex; flex-direction: column; align-items: center; filter: drop-shadow(0 14px 18px rgba(0,0,0,0.6))">{portrait(P, "p2", "r2", 160, 196)}</div>
      </div>"""
    strengths = (f'<div style="display: grid; grid-template-columns: 34px 1fr; gap: 3px 8px; font-size: 12px; line-height: 1.45; padding-top: 8px; border-top: 1px solid rgba(110,78,38,0.45)">'
                 f'<span style="font-weight: 700; color: {P_GREEN}">강점</span><span style="color: {P_INK}">화차 일제 사격 · 단단한 방어 건물</span>'
                 f'<span style="font-weight: 700; color: {P_RED}">약점</span><span style="color: {P_INK}">느린 기동 · 산기 소모가 큼</span></div>')
    hint = (f'<div style="display: flex; align-items: center; gap: 8px; font-size: 12px; line-height: 1.45; padding-top: 8px; border-top: 1px solid rgba(110,78,38,0.45); color: {P_INK}">'
            f'{dot(P_OCHRE)}<span>오방 신당 미리보기 중 · 시작 자원은 모두 같음</span></div>')
    slots = f"""
      <div style="display: flex; gap: 16px; margin-top: -6px">
        {slot_panel("1p", "commander", "player-1 · 확정", "r1", True, me=True, extra=strengths)}
        {slot_panel("2p", "rival", "player-2 · 방금 입장", "r2", False, hover="r3", extra=hint)}
      </div>"""
    ready = f"""
      <div style="display: flex; align-items: center; gap: 14px; padding: 12px 14px 10px; border-radius: 3px; background: {LACQUER_BG};
           box-shadow: 0 0 0 1px #0E0703, 0 0 0 3px #4A2E1E, 0 0 0 4px #B48A3E, 0 0 0 5px #0E0703, 0 12px 26px rgba(0,0,0,0.6)">
        <div style="display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0">
          <span style="font-size: 14px; font-weight: 600; color: {INK}">상대가 준비를 마치면 <span style="color: {GOLD}; font-weight: 700">3초 뒤</span> 판이 열립니다</span>
          <span style="font-size: 12px; color: #C9BC9E">시작 전까지 종족 변경 가능 · 바꾸면 준비가 풀립니다</span>
        </div>
        {btn("wood", "방 나가기", "Esc", h=44, size=14)}
        {btn("jade", "준비 완료", "Space", h=44, size=15)}
      </div>"""
    return f"""
    <div style="position: relative; display: flex; flex-direction: column; gap: 14px; min-height: 0">
      {plaque}
      {portraits}
      {slots}
      {ready}
    </div>"""


def mini_map():
    iso = Iso(78, 6, 2.3)
    rng = random.Random(5)
    o = [f'<path d="{iso.poly([(0, 0, 0), (32, 0, 0), (32, 32, 0), (0, 32, 0)])}" fill="#6E6A40" stroke="#2A1A0E" stroke-width="1"></path>']
    o.append(f'<path d="{iso.poly([(0, 18, 0), (32, 12, 0), (32, 16, 0), (0, 22, 0)])}" fill="#2F6E8E"></path>')
    o.append(f'<path d="{iso.line([(0, 18, 0), (32, 12, 0)])}" stroke="#A8DDF5" stroke-opacity="0.6" stroke-width="0.8"></path>')
    for x in range(1, 31, 3):
        for y in range(1, 31, 3):
            if rng.random() < 0.18 and not (12 < y < 23):
                c = iso(x + rng.random(), y + rng.random())
                o.append(f'<circle cx="{c[0]:.1f}" cy="{c[1] - 2:.1f}" r="2.4" fill="#2E4A22"></circle>')
    for (x, y) in ((8, 4), (24, 27)):
        c = iso(x, y)
        o.append(f'<path d="M{c[0]:.1f} {c[1] - 6:.1f} l3 -2 l3 2 v4 l-3 2 l-3 -2 Z" fill="#9FE3D0" stroke="#2A5A50" stroke-width="0.5"></path>')
    a = iso(4, 4)
    o.append(f'<path d="M{a[0] - 7:.1f} {a[1]:.1f} l7 -3.5 l7 3.5 l-7 3.5 Z" fill="#D9532E" stroke="#4A1006" stroke-width="0.8"></path>'
             f'<path d="M{a[0] - 7:.1f} {a[1]:.1f} v-5 l7 -3.5 l7 3.5 v5" fill="none" stroke="#F09A6E" stroke-width="0.8"></path>')
    b = iso(28, 28)
    o.append(f'<path d="M{b[0] - 7:.1f} {b[1]:.1f} l7 -3.5 l7 3.5 l-7 3.5 Z" fill="#4FB3E8" stroke="#12222E" stroke-width="0.8"></path>')
    for t in (0.3, 0.7):
        c = iso(32 * t, 18 - 6 * t + 2)
        o.append(f'<path d="M{c[0] - 3:.1f} {c[1]:.1f} h6" stroke="#C9A35A" stroke-width="2"></path>')
    return (f'<svg width="156" height="88" viewBox="0 0 156 88" role="img" aria-label="한강 나루 지도 미리보기" style="flex: none; border-radius: 2px; '
            f'background: linear-gradient(180deg, #1A120C, #2A1A10); box-shadow: inset 0 2px 4px rgba(0,0,0,0.6), 0 1px 0 rgba(255,248,226,0.7)">{"".join(o)}</svg>')


def room_info():
    info = "".join(
        f'<span style="color: {P_MUTED}">{k}</span><span style="font-family: {MONO}; font-weight: 600; color: {c}; text-align: right">{v}</span>'
        for k, v, c in (("크기", "64×64 · 2인", P_INK), ("시드", "1234", P_INK), ("틱", "20ms", P_INK), ("시작", "귀화석 500", P_GREEN)))
    inner = f"""
        {panel_title("방 정보", stamp("호스트 commander", P_MUTED))}
        <div style="display: flex; gap: 12px; align-items: center">
          {mini_map()}
          <div style="flex: 1; display: grid; grid-template-columns: auto 1fr; gap: 4px 8px; font-size: 12px">{info}</div>
        </div>
        <span style="font-size: 12px; color: {P_MUTED}">귀화석 광맥 8 · 산기 분출구 4 · 징검다리 2</span>"""
    return ornate(P, inner, pad="12px 14px 12px", gap=10)


LOG = [
    ("00:00.000", P_BLUE, "connect", "ws://127.0.0.1:8080/ws"),
    ("00:00.031", P_GREEN, "handshakeAck", "clientId=player-1 · resume 발급"),
    ("00:04.870", P_OCHRE, "race", "player-1 → 조선 공방"),
    ("00:05.002", P_GREEN, "ready", "player-1 준비"),
    ("00:09.416", P_BLUE, "join", "player-2 rival 입장"),
    ("00:09.430", P_MUTED, "race", "player-2 고르는 중…"),
]


def log_panel():
    items = []
    for i, (t, col, title, detail) in enumerate(LOG):
        last = i == len(LOG) - 1
        line = "" if last else f'<span style="position: absolute; left: 4px; top: 14px; bottom: -10px; width: 1px; background: {P_RULE}"></span>'
        items.append(
            f'<div style="position: relative; display: grid; grid-template-columns: 9px 1fr; gap: 10px">'
            f'{line}<span style="width: 8px; height: 8px; margin-top: 4px; background: {col}; transform: rotate(45deg); box-shadow: 0 0 0 1px rgba(0,0,0,0.25)"></span>'
            f'<div style="display: flex; flex-direction: column; gap: 1px; min-width: 0">'
            f'<span style="display: flex; justify-content: space-between; gap: 8px"><span style="font-family: {MONO}; font-size: 12.5px; font-weight: 700; color: {P_INK}">{title}</span>'
            f'<span style="font-family: {MONO}; font-size: 11px; color: {P_MUTED}">{t}</span></span>'
            f'<span style="font-size: 12px; color: {P_MUTED}; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{detail}</span></div></div>')
    stats = "".join(
        f'<div style="display: flex; flex-direction: column; gap: 1px; padding: 6px 9px; border-radius: 2px; background: linear-gradient(180deg, #160C06, #2A1A10); '
        f'box-shadow: inset 0 2px 3px rgba(0,0,0,0.6), 0 1px 0 rgba(255,248,226,0.7)">'
        f'<span style="font-size: 10.5px; color: #C9B48A">{k}</span><span style="font-family: {MONO}; font-size: 15px; font-weight: 600; color: {c}">{v}</span></div>'
        for k, v, c in (("왕복 지연", "12ms", "#A8E0B8"), ("틱 간격", "20ms", "#F3E6C8"), ("손실", "0.0%", "#A8E0B8")))
    inner = (panel_title("연결 기록", stamp(dot(P_GREEN) + "동기화됨", P_GREEN))
             + f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px">{stats}</div>'
             + f'<div style="flex: 1; display: flex; flex-direction: column; gap: 9px">{"".join(items)}</div>')
    return ornate(P, inner, pad="12px 14px 12px", gap=10, extra="flex: 1; min-height: 0")


def chat_panel():
    inner = f"""
        {panel_title("방 채팅", f'<span style="font-family: {MONO}; font-size: 11px; color: {P_MUTED}">2명</span>')}
        <div style="display: flex; flex-direction: column; gap: 4px; font-size: 12.5px">
          <div style="display: flex; gap: 8px"><span style="font-family: {MONO}; font-weight: 700; color: {RACES['r1']['ink']}">commander</span><span style="color: {P_INK}">어서 오세요, 한 판 합시다</span></div>
          <div style="display: flex; gap: 8px"><span style="font-family: {MONO}; font-weight: 700; color: {RACES['r2']['ink']}">rival</span><span style="color: {P_INK}">요물이냐 신령이냐… 잠깐만요</span></div>
        </div>
        {lacquer_slot('<span style="color: #A8977A; font-family: ' + BODY + '; font-size: 12.5px">방 채팅 입력</span><span style="margin-left: auto; font-family: ' + MONO + '; font-size: 11px; color: #A8977A">Enter</span>', h=32)}"""
    return ornate(P, inner, pad="11px 14px 12px", gap=8)


def topbar():
    crumbs = (f'<span style="display: flex; align-items: center; gap: 10px; font-size: 13px; color: #C9BC9E">'
              f'<span>메인</span><span style="color: #8A7A5A">›</span><span style="color: {INK}; font-weight: 600">접속 · 로비</span></span>')
    left = f'{wordmark(P, compact=True)}<span style="width: 1px; height: 30px; background: #6A4A2E"></span>{crumbs}'
    right = (chip("sim dev · protocol v1", "#D9C69A", "rgba(0,0,0,0.25)", "#6A4A2E", mono=True)
             + chip(dot(OK, 6) + "서버 연결됨", "#A8E0B8", "rgba(80,160,110,0.12)", "rgba(140,199,154,0.45)")
             + btn("wood", "메인으로", "Esc", h=32, size=13))
    return title_bar(P, left, right)


def build():
    body = f"""<div style="position: relative; width: {W}px; height: {H}px; box-sizing: border-box; background: {GROUND}; color: {INK}; font-family: {BODY}; display: flex; flex-direction: column; overflow: hidden">
  {shared_defs(P)}
  {courtyard()}
  {topbar()}
  <main style="position: relative; z-index: 1; flex: 1; min-height: 0; box-sizing: border-box; padding: 14px 22px 16px; display: grid; grid-template-columns: 364px minmax(0, 1fr) 330px; gap: 18px">
    <div style="display: flex; flex-direction: column; gap: 12px; min-height: 0">
      {connect_panel()}
      {resume_panel()}
      {connect_buttons()}
      {room_list()}
    </div>
    {stage()}
    <div style="display: flex; flex-direction: column; gap: 12px; min-height: 0">
      {room_info()}
      {log_panel()}
      {chat_panel()}
    </div>
  </main>
</div>"""
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(page("접속과 로비", W, H, body))


if __name__ == "__main__":
    build()
    print(os.path.abspath(OUT))
