"""Generates design2/project/Main.dc.html — SAMPAJEON main menu, hi-fi 2.5D pass (id prefix s1-)."""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from s_common import *  # noqa: E402,F403
from ornate import (Iso, OUTLINE, f, flame, framed, gold_title, grass_tuft, hall, kite, lantern,  # noqa: E402
                    maple, mix, person, pine, stall, stone_box, wall_run, wood_defs, corner, cloud_path)

OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "Main.dc.html")
W, H = 1440, 900


import screen_figs as SF  # noqa: E402

KS = SF.SK("s1k")
KS.res = 8
_FIG = {}


def person(rnd, x, y, kind="soldier", s=1.0, face=1):
    """Kit-rendered townsfolk (overrides ornate.person); each pose is drawn once and instanced."""
    var = int(abs(x) * 7 + abs(y) * 3) % 3  # per-instance face variant, independent of the layout RNG
    key = (kind, face, var)
    if key not in _FIG:
        _FIG[key] = (f"s1-fig{len(_FIG) + 1}", SF.crowd(KS, kind, face, var))
    sc = (0.3 if kind == "goblin" else 0.4) * s
    return f'<use href="#{_FIG[key][0]}" transform="translate({f(x)},{f(y)}) scale({f(sc)})"></use>'


def kit_defs():
    figs = "".join(f'<g id="{i}">{body}</g>' for i, body in _FIG.values())
    return f'<svg width="0" height="0" style="position: absolute" aria-hidden="true"><defs>{KS.defs_svg()}{figs}</defs></svg>'


def sky_and_mountains(rnd):
    o = []
    o.append(f'<rect width="{W}" height="{H}" fill="url(#s1-sky)"></rect>')
    o.append('<circle cx="1090" cy="210" r="210" fill="url(#s1-sun)"></circle>')
    o.append('<circle cx="1090" cy="214" r="46" fill="#FFE6B0" opacity=".95"></circle>')
    o.append('<circle cx="1090" cy="214" r="46" fill="url(#s1-sundisc)"></circle>')
    # cloud banks, lit from below by the setting sun
    for (cx, cy, w, h, c) in [(760, 120, 260, 26, "#5A3A48"), (1240, 96, 300, 24, "#5E3C46"), (980, 150, 200, 16, "#8A5048"),
                              (1330, 170, 180, 14, "#9A5A44"), (620, 190, 220, 14, "#6E4446")]:
        o.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{w}" ry="{h}" fill="{c}" opacity=".85" filter="url(#s1-blur6)"></ellipse>')
        o.append(f'<ellipse cx="{cx + 10}" cy="{cy + h * .6}" rx="{w * .8}" ry="{h * .35}" fill="#F2A060" opacity=".45" filter="url(#s1-blur4)"></ellipse>')
    # three ridge layers with mist between
    layers = [(250, "#5A4058", "#7A5060", 26, 1.0), (300, "#3E2E44", "#5A3A4A", 34, .95), (345, "#271E2E", "#3A2836", 40, 1.0)]
    for li, (base, c0, c1, amp, op) in enumerate(layers):
        pts = []
        x = 380
        while x <= W + 40:
            y = base - amp * (0.6 * abs(((x * (0.004 + li * .001) + li) % 2) - 1) + 0.4 * rnd.random())
            pts.append((x, y))
            x += rnd.uniform(26, 60)
        d = f"M380 {H} L" + " L".join(f"{f(x)} {f(y)}" for x, y in pts) + f" L{W + 40} {H} Z"
        o.append(f'<path d="{d}" fill="url(#s1-mt{li})" opacity="{op}"></path>')
        # sunlit rim on the ridge (light from upper left/behind)
        rim = "M" + " L".join(f"{f(x)} {f(y + 1)}" for x, y in pts)
        o.append(f'<path d="{rim}" fill="none" stroke="#F2A060" stroke-width="1.2" opacity="{.35 - li * .08}"></path>')
        # distant pine silhouettes on ridges
        for (x, y) in pts[::3]:
            if rnd.random() < .6:
                s = .18 + li * .06
                o.append(f'<path d="M{f(x)} {f(y + 2)} l{f(-6 * s * 4)} {f(4 * s * 4)} h{f(12 * s * 4)} Z M{f(x)} {f(y - 4 * s * 4)} l{f(-5 * s * 4)} {f(5 * s * 4)} h{f(10 * s * 4)} Z" fill="{c1}" opacity=".9"></path>')
        o.append(f'<rect x="380" y="{base - 6}" width="{W}" height="40" fill="url(#s1-mist)" opacity=".6"></rect>')
    return "".join(o)


def ground(iso, rnd):
    o = []
    # base ground plane (whole lower area) + broad lighting
    o.append(f'<path d="M0 {H} L0 330 L{W} 300 L{W} {H} Z" fill="url(#s1-earth)"></path>')
    o.append(f'<rect x="0" y="280" width="{W}" height="{H - 280}" fill="#000" filter="url(#s1-dirt)" opacity=".55"></rect>')
    P = iso.P
    # flagstone court in front of the gate (박석): jittered diamond stones
    for i in range(-3, 10):
        for j in range(3, 6):
            for (a, b) in [(0, 0), (.5, 0), (0, .5), (.5, .5)]:
                ii, jj = i + a, j + b
                c = mix("#9A8E74", rnd.choice(["#C8B894", "#5E5444", "#8A7C62", "#B0A07E"]), rnd.uniform(.1, .5))
                q = [P(ii + .03, jj + .03), P(ii + .47, jj + .03), P(ii + .47, jj + .47), P(ii + .03, jj + .47)]
                o.append(iso.path(q, c))
                o.append(f'<path d="M{f(q[3][0])} {f(q[3][1])} L{f(q[0][0])} {f(q[0][1])} L{f(q[1][0])} {f(q[1][1])}" stroke="#E8DCC0" stroke-width=".5" opacity=".35" fill="none"></path>')
    # dirt market street with cart ruts
    road = [P(-9, 6.2), P(16, 6.2), P(16, 9.4), P(-9, 9.4)]
    o.append(iso.path(road, "#7A5E3E", 'opacity=".85"'))
    o.append(iso.path(road, "#000", 'filter="url(#s1-dirt)" opacity=".5"'))
    for jj in (7.3, 7.7, 8.4, 8.8):
        a, b = P(-9, jj), P(16, jj)
        o.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="#4A3420" stroke-width="2.2" opacity=".55"></path>')
        o.append(f'<path d="M{f(a[0])} {f(a[1] + 1.5)} L{f(b[0])} {f(b[1] + 1.5)}" stroke="#B89A6E" stroke-width=".8" opacity=".4"></path>')
    # puddles reflecting the sky
    for (i, j, r) in [(3.2, 8.1, 1.0), (9.5, 7.6, .7), (-2, 8.6, .8)]:
        x, y = P(i, j)
        o.append(f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(22 * r)}" ry="{f(8 * r)}" fill="url(#s1-puddle)" stroke="#3A2A1A" stroke-width=".8"></ellipse>')
    # grass tufts, fallen maple leaves, pebbles
    for _ in range(170):
        x, y = rnd.uniform(560, W), rnd.uniform(330, H)
        sx, sy = x, y
        if rnd.random() < .55:
            o.append(grass_tuft(rnd, sx, sy, rnd.uniform(.7, 1.3), rnd.choice(["#B89A4A", "#8A7A3A", "#C8A85A", "#6E6A34"])))
        elif rnd.random() < .6:
            o.append(f'<path d="M{f(sx)} {f(sy)} l3 -2 l1 3 l-3 1 Z" fill="{rnd.choice(["#C8432B", "#E58A3A", "#A8281A", "#F2B04A"])}" opacity=".9"></path>')
        else:
            o.append(f'<ellipse cx="{f(sx)}" cy="{f(sy)}" rx="{f(rnd.uniform(1.5, 3.5))}" ry="{f(rnd.uniform(1, 2))}" fill="#6A6050"></ellipse>')
    return "".join(o)


def scene(rnd):
    iso = Iso(982, 308)
    P = iso.P
    items = []  # (depth, svg)

    def add(depth, svg):
        items.append((depth, svg))

    # palace interior behind the wall (far)
    add(-20, stone_box(iso, rnd, 1, -6.4, 5.6, -4.2, 14, base="#8A8070", courses=2))
    add(-19.9, hall(iso, rnd, 1.4, -6.1, 5.2, -4.5, 14, 26, 30, o=.45, bays=5, side_bays=2, roof="#353B42"))
    add(-12, stone_box(iso, rnd, 8.4, -3.6, 12.6, -1.4, 12, base="#8A8070", courses=2))
    add(-11.9, hall(iso, rnd, 8.7, -3.3, 12.3, -1.7, 12, 24, 28, o=.45, bays=4, side_bays=2, roof="#353B42"))
    for (i, j, kind, s) in [(-1.5, -3.5, "pine", .8), (7.2, -5.2, "maple", .85), (14.4, -3.6, "maple", .9), (6.6, -2.2, "pine", .7)]:
        x, y = P(i, j)
        add(i + j, (pine if kind == "pine" else maple)(rnd, x, y, s))
    # palace walls + gate
    add(-6.4, wall_run(iso, rnd, -7, 0, .9, 1.7, 44))
    add(-6.6, stone_box(iso, rnd, -8.6, .3, -6.8, 2.3, 58, base="#A09480", courses=4))
    add(-6.5, hall(iso, rnd, -8.3, .6, -7.1, 2.0, 58, 22, 26, o=.45, bays=2, side_bays=2, roof="#3D434A"))
    add(4.5, stone_box(iso, rnd, 0, 0, 6, 2.6, 66, base="#A09480", courses=5, arches=((.2, .12, 1), (.5, .16, 1), (.8, .12, 1))))
    add(4.6, hall(iso, rnd, .45, .45, 5.55, 2.15, 66, 34, 40, o=.6, bays=5, side_bays=2, roof="#3D434A"))
    add(10.6, wall_run(iso, rnd, 6, 22, .9, 1.7, 44))
    # banners on the gate wall
    for u in (.06, .94):
        a = P(u * 6, 2.6, 62)
        add(4.7, f'<path d="M{f(a[0])} {f(a[1])} v28" stroke="#2A1810" stroke-width="1.6"></path>'
                 f'<path d="M{f(a[0])} {f(a[1] + 2)} h-14 l3 10 l-3 10 h14 Z" fill="#C8432B" stroke="{OUTLINE}" stroke-width=".6"></path>'
                 f'<circle cx="{f(a[0] - 7)}" cy="{f(a[1] + 12)}" r="3.2" fill="#E2B13C"></circle>')
    # lanterns along the wall front
    for i in (-6, -3, 7.5, 10.5, 13.5):
        x, y = P(i, 1.9, 36)
        add(i + 2.2, lantern(x, y, .9))
    # guards before the gate
    for k, i in enumerate((1.0, 1.9, 4.1, 5.0)):
        x, y = P(i, 3.3)
        add(i + 3.3, person(rnd, x, y, "soldier", 1.05, 1 if k % 2 else -1))
    # court trees
    for (i, j, kind, s) in [(-5.5, 3.8, "maple", 1.05), (-8.5, 2.6, "pine", 1.0), (8.6, 4.4, "maple", 1.0), (12.4, 3.2, "pine", .95)]:
        x, y = P(i, j)
        add(i + j, (pine if kind == "pine" else maple)(rnd, x, y, s))
    # goblins lurking at the wall corner with goblin-fire
    for (i, j, face) in [(8.2, 2.8, -1), (9.0, 3.2, -1), (9.6, 2.6, -1)]:
        x, y = P(i, j)
        add(i + j, person(rnd, x, y, "goblin", 1.1, face))
    for (i, j, s) in [(8.9, 2.2, .55), (10.2, 3.0, .42), (7.6, 3.5, .36)]:
        x, y = P(i, j, 14)
        add(i + j + .01, flame(x, y, s))
    # market stalls on both sides of the street
    for (i, j, c, g) in [(-3, 5.2, "#C8432B", "jars"), (1, 5.2, "#2F6E8E", "cloth"), (5, 5.2, "#E2B13C", "kites"),
                         (-1, 9.6, "#3E8F7A", "cloth"), (3.6, 9.6, "#C8432B", "jars"), (7.4, 9.6, "#E2B13C", "kites")]:
        add(i + j + 1, stall(iso, rnd, i, j, c, g))
    # street crowd
    crowd = [(-4.5, 7.4, "merchant"), (-2.6, 7.9, "woman"), (-.8, 7.2, "noble"), (.6, 8.4, "merchant"), (2.2, 7.6, "woman"),
             (3.8, 8.0, "soldier"), (5.1, 7.3, "merchant"), (6.4, 8.6, "noble"), (8.0, 7.8, "woman"), (9.6, 8.2, "merchant"),
             (-6.2, 8.3, "soldier"), (11.2, 7.4, "noble"), (-3.4, 8.9, "merchant")]
    for (i, j, kind) in crowd:
        x, y = P(i, j)
        add(i + j, person(rnd, x, y, kind, 1.15, rnd.choice((-1, 1))))
    items.sort(key=lambda t: t[0])
    return "".join(s for _, s in items)


def sky_life(rnd):
    o = []
    o.append(kite(760, 128, 30, -12, .95, (720, 470)))
    o.append(kite(905, 92, 22, 8, .9, (842, 452)))
    o.append(kite(1330, 120, 26, 14, .9, (1180, 470)))
    o.append(kite(640, 210, 18, -20, .8, (690, 470)))
    # hwacha rocket streaks rising from behind the palace
    for (x1, y1, x2, y2) in [(1250, 330, 1188, 150), (1290, 340, 1260, 130), (1330, 336, 1338, 160)]:
        o.append(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="url(#s1-trail)" stroke-width="7" stroke-linecap="round" filter="url(#s1-blur4)" opacity=".8"></line>'
                 f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="url(#s1-trail)" stroke-width="2" stroke-linecap="round"></line>'
                 f'<circle cx="{x2}" cy="{y2}" r="9" fill="#F09A6E" filter="url(#s1-blur4)"></circle><circle cx="{x2}" cy="{y2}" r="2.6" fill="#FFF4D8"></circle>')
    # birds
    for _ in range(9):
        x, y = rnd.uniform(560, 1000), rnd.uniform(160, 260)
        o.append(f'<path d="M{f(x)} {f(y)} q4 -4 8 0 q4 -4 8 0" stroke="#1E1620" stroke-width="1.2" fill="none"></path>')
    return "".join(o)


def foreground(rnd):
    o = []
    # big foreground maple branch framing the lower middle
    o.append('<path d="M520 900 C560 830 600 790 690 760 C740 744 790 748 830 736" stroke="#1A0E08" stroke-width="14" fill="none" stroke-linecap="round"></path>')
    o.append('<path d="M640 776 C660 740 700 716 740 706" stroke="#1A0E08" stroke-width="7" fill="none" stroke-linecap="round"></path>')
    for _ in range(70):
        cx, cy = rnd.uniform(600, 900), rnd.uniform(690, 800)
        t = (cx - 600) / 300
        col = rnd.choice(["#4A0C08", "#6E1610", "#8A1E12", "#A8281A"]) if rnd.random() < .7 else "#C8432B"
        o.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(rnd.uniform(6, 13))}" fill="{col}" opacity="{f(.95 - t * .2)}"></circle>')
    for _ in range(16):
        cx, cy = rnd.uniform(620, 880), rnd.uniform(680, 740)
        o.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(rnd.uniform(2, 4))}" fill="#F2A060" opacity=".8"></circle>')
    # stone lantern at bottom-left of the art
    o.append('<ellipse cx="612" cy="868" rx="40" ry="10" fill="#000" opacity=".5"></ellipse>')
    o.append('<path d="M594 868 h36 v-8 h-6 v-46 h-24 v46 h-6 Z" fill="#5A544A" stroke="#120d08" stroke-width="1"></path>')
    o.append('<path d="M586 812 h52 l-8 -14 h-36 Z" fill="#6E685C" stroke="#120d08"></path>')
    o.append('<rect x="596" y="770" width="32" height="28" fill="#3A3630" stroke="#120d08"></rect>')
    o.append('<rect x="603" y="776" width="18" height="16" fill="#FFB45A"></rect>')
    o.append('<circle cx="612" cy="784" r="26" fill="#FFB45A" opacity=".35" filter="url(#s1-blur6)"></circle>')
    o.append('<path d="M582 772 h60 l-10 -16 h-40 Z" fill="#6E685C" stroke="#120d08"></path><circle cx="612" cy="752" r="5" fill="#6E685C"></circle>')
    o.append('<path d="M596 812 v46 M600 812 v46" stroke="#8A8478" stroke-width="1" opacity=".6"></path>')
    return "".join(o)


def defs():
    return f"""<defs>
    <linearGradient id="s1-sky" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#16121E"></stop><stop offset=".18" stop-color="#2A2034"></stop><stop offset=".3" stop-color="#5A3440"></stop>
      <stop offset=".38" stop-color="#B0583A"></stop><stop offset=".44" stop-color="#E8955A"></stop><stop offset=".6" stop-color="#3A2622"></stop></linearGradient>
    <radialGradient id="s1-sun" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFD08A" stop-opacity=".85"></stop><stop offset=".35" stop-color="#F28A4A" stop-opacity=".35"></stop><stop offset="1" stop-color="#F28A4A" stop-opacity="0"></stop></radialGradient>
    <radialGradient id="s1-sundisc" cx=".4" cy=".4" r=".7"><stop offset="0" stop-color="#FFFBEA"></stop><stop offset="1" stop-color="#F2B060" stop-opacity=".3"></stop></radialGradient>
    <linearGradient id="s1-mt0" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8A5A62"></stop><stop offset=".35" stop-color="#5A4058"></stop><stop offset="1" stop-color="#3A2A3A"></stop></linearGradient>
    <linearGradient id="s1-mt1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5A3A4A"></stop><stop offset=".4" stop-color="#3E2E44"></stop><stop offset="1" stop-color="#2A2030"></stop></linearGradient>
    <linearGradient id="s1-mt2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3A2836"></stop><stop offset=".4" stop-color="#271E2E"></stop><stop offset="1" stop-color="#1A1420"></stop></linearGradient>
    <linearGradient id="s1-mist" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E8A27A" stop-opacity="0"></stop><stop offset=".5" stop-color="#C8806A" stop-opacity=".45"></stop><stop offset="1" stop-color="#C8806A" stop-opacity="0"></stop></linearGradient>
    <linearGradient id="s1-earth" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6A5236"></stop><stop offset=".45" stop-color="#5A4428"></stop><stop offset="1" stop-color="#2E2216"></stop></linearGradient>
    <radialGradient id="s1-puddle" cx=".4" cy=".4" r=".7"><stop offset="0" stop-color="#E8955A"></stop><stop offset=".6" stop-color="#7A4A44"></stop><stop offset="1" stop-color="#2A2030"></stop></radialGradient>
    <linearGradient id="s1-arch" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0A0706"></stop><stop offset="1" stop-color="#3A2214"></stop></linearGradient>
    <linearGradient id="s1-cloth" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFF8E8"></stop><stop offset="1" stop-color="#D8C9A6"></stop></linearGradient>
    <radialGradient id="s1-flame" cx=".5" cy=".7" r=".7"><stop offset="0" stop-color="#F2FBFF"></stop><stop offset=".4" stop-color="#A8DDF5"></stop><stop offset=".8" stop-color="#4FB3E8"></stop><stop offset="1" stop-color="#1D5E86"></stop></radialGradient>
    <linearGradient id="s1-trail" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#D9532E" stop-opacity="0"></stop><stop offset=".7" stop-color="#F09A6E"></stop><stop offset="1" stop-color="#FFF4D8"></stop></linearGradient>
    <linearGradient id="s1-leftfade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#0E0B08" stop-opacity=".92"></stop><stop offset=".36" stop-color="#0E0B08" stop-opacity=".72"></stop><stop offset=".52" stop-color="#0E0B08" stop-opacity="0"></stop></linearGradient>
    <radialGradient id="s1-vig" cx=".62" cy=".42" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"></stop><stop offset="1" stop-color="#000" stop-opacity=".7"></stop></radialGradient>
    <filter id="s1-soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3"></feGaussianBlur></filter>
    <filter id="s1-blur4" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="4"></feGaussianBlur></filter>
    <filter id="s1-blur6" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="8"></feGaussianBlur></filter>
    <filter id="s1-fireglow" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="7"></feGaussianBlur></filter>
    <filter id="s1-dirt" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency=".035 .07" numOctaves="4" seed="11" result="n"></feTurbulence>
      <feColorMatrix in="n" type="matrix" values="0 0 0 0 .1  0 0 0 0 .07  0 0 0 0 .04  1.4 0 0 0 -.55" result="g"></feColorMatrix>
      <feComposite in="g" in2="SourceAlpha" operator="in"></feComposite></filter>
  </defs>"""


def key_art():
    rnd = random.Random(1234)
    return (f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}" aria-hidden="true" style="position: absolute; left: 0; top: 0">'
            + defs() + sky_and_mountains(rnd) + ground(Iso(982, 308), rnd) + scene(rnd) + sky_life(rnd) + foreground(rnd)
            + f'<rect width="{W}" height="{H}" fill="url(#s1-leftfade)"></rect>'
            + f'<rect width="{W}" height="{H}" fill="url(#s1-vig)"></rect>'
            + '<rect width="1440" height="900" fill="#FF9A50" opacity=".05"></rect>'
            + "</svg>")


MENU = [
    ("싱글 플레이", "로컬 시뮬레이션과 클라이언트를 함께 실행", "P", "primary"),
    ("온라인 대전", "권위 서버의 방에 참가 · 끊기면 재접속", "N", "hover"),
    ("리플레이", "기록 재생 · 월드 해시 검증", "R", "idle"),
    ("지도 · 데이터 도구", "맵 생성, 유닛·건물 데이터 검증", "T", "idle"),
    ("설정", "조작 · 화면 · 소리", "S", "idle"),
    ("나가기", "", "Q", "idle"),
]


def menu_row(label, desc, key, state):
    if state == "primary":
        bg = "linear-gradient(180deg,#C24A2C 0%,#962F18 55%,#6E2010 100%)"
        bd, fg, sub, kb = "#E9C37A", "#FFF1D6", "#F6D3C2", "#5A180C"
        sh = "inset 0 1px 0 rgba(255,214,170,.6), inset 0 -3px 0 rgba(0,0,0,.35), 0 3px 0 #1C0D06, 0 0 18px rgba(226,140,60,.35)"
    elif state == "hover":
        bg = "linear-gradient(180deg,#5E3A22 0%,#43281A 55%,#2E1A0E 100%)"
        bd, fg, sub, kb = "#F3D58C", "#FFE7A8", "#D8C6A2", "#1A0D05"
        sh = "inset 0 1px 0 rgba(255,224,170,.45), inset 0 -3px 0 rgba(0,0,0,.35), 0 3px 0 #120904, 0 0 14px rgba(226,177,60,.35)"
    else:
        bg = "linear-gradient(180deg,#3E2616 0%,#2C1A0E 100%)"
        bd, fg, sub, kb = "#7A5A2E", "#EFE6D2", "#B4A88E", "#140A04"
        sh = "inset 0 1px 0 rgba(255,214,160,.18), inset 0 -2px 0 rgba(0,0,0,.4), 0 2px 0 #120904"
    d = f'<span style="font-family: {BODY}; font-size: 12px; font-weight: 400; color: {sub}; text-shadow: none">{desc}</span>' if desc else ""
    return f"""        <button type="button" style="display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; min-height: {58 if desc else 46}px; padding: 8px 14px 8px 18px; box-sizing: border-box; border-radius: 3px; border: 1.5px solid {bd}; background: {bg}; box-shadow: {sh}; text-align: left; cursor: pointer">
          <span style="display: flex; align-items: center; gap: 12px">
            <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true"><path d="M6 0 L12 6 L6 12 L0 6 Z" fill="{bd}"></path><path d="M6 3 L9 6 L6 9 L3 6 Z" fill="{kb}"></path></svg>
            <span style="display: flex; flex-direction: column; gap: 2px">
              <span style="font-family: {DISPLAY}; font-weight: 800; font-size: 19px; color: {fg}; text-shadow: 0 1px 0 #1A0804">{label}</span>
              {d}
            </span>
          </span>
          <span style="display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 3px; background: {kb}; border: 1px solid {bd}; box-shadow: inset 0 1px 0 rgba(255,230,180,.25); font-family: {MONO}; font-size: 12px; font-weight: 600; color: {fg}">{key}</span>
        </button>"""


RACES = [
    ("r1", emblem_r1, "조선 공방", "JOSEON WORKS", "사람과 나무·쇠 기계", "#B8381E", "#7E2414", "1", "人"),
    ("r2", emblem_r2, "요물 무리", "YOMUL HORDE", "도깨비와 깨어난 요물들", "#2B4E8A", "#14264A", "2", "地"),
    ("r3", emblem_r3, "오방 신당", "OBANG SHRINE", "금줄을 치는 신령들", "#C8962A", "#7A5414", "3", "天"),
]


def cloth_banner(tag, fn, name, en, line, c0, c1, key, han):
    p = f"s1-bn{tag}"
    w, h = 196, 252
    return f"""      <div style="position: relative; width: {w}px; height: {h}px">
        <svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" aria-hidden="true" style="position: absolute; left: 0; top: 0; overflow: visible">
          <defs>
            {wood_defs(p, 5)}
            <linearGradient id="{p}-cl" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stop-color="{c1}"></stop><stop offset=".14" stop-color="{c0}"></stop><stop offset=".3" stop-color="{mix(c0, '#000000', .25)}"></stop>
              <stop offset=".5" stop-color="{mix(c0, '#FFFFFF', .12)}"></stop><stop offset=".72" stop-color="{mix(c0, '#000000', .3)}"></stop><stop offset=".88" stop-color="{c0}"></stop><stop offset="1" stop-color="{c1}"></stop></linearGradient>
            <linearGradient id="{p}-dk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".35"></stop><stop offset=".25" stop-color="#000" stop-opacity="0"></stop><stop offset=".75" stop-color="#000" stop-opacity="0"></stop><stop offset="1" stop-color="#000" stop-opacity=".45"></stop></linearGradient>
            <filter id="{p}-wv" x="0" y="0" width="100%" height="100%">
              <feTurbulence type="fractalNoise" baseFrequency="1.2 .9" numOctaves="2" seed="4" result="n"></feTurbulence>
              <feColorMatrix in="n" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1 .55" result="g"></feColorMatrix>
              <feComposite in="g" in2="SourceAlpha" operator="in"></feComposite></filter>
          </defs>
          <path d="M18 22 H{w - 18} V{h - 40} L{w / 2} {h - 14} L18 {h - 40} Z" fill="#000" opacity=".55" transform="translate(5 8)"></path>
          <path d="M18 22 H{w - 18} V{h - 40} L{w / 2} {h - 14} L18 {h - 40} Z" fill="url(#{p}-cl)" stroke="{OUTLINE}" stroke-width="1.2"></path>
          <path d="M18 22 H{w - 18} V{h - 40} L{w / 2} {h - 14} L18 {h - 40} Z" fill="#000" filter="url(#{p}-wv)" opacity=".5"></path>
          <path d="M18 22 H{w - 18} V{h - 40} L{w / 2} {h - 14} L18 {h - 40} Z" fill="url(#{p}-dk)"></path>
          <path d="M26 30 H{w - 26} V{h - 46} L{w / 2} {h - 24} L26 {h - 46} Z" fill="none" stroke="#E9C37A" stroke-width="1.6" opacity=".9"></path>
          <path d="M30 34 H{w - 30} V{h - 48} L{w / 2} {h - 28} L30 {h - 48} Z" fill="none" stroke="#E9C37A" stroke-width=".7" opacity=".55"></path>
          <g fill="#E9C37A" opacity=".85">{cloud_path(w / 2, 44, .55)}</g>
          <path d="M{w / 2 - 10} {h - 14} q-3 10 -1 20 M{w / 2 + 10} {h - 14} q3 10 1 20" stroke="#C8432B" stroke-width="2.4" fill="none"></path>
          <circle cx="{w / 2 - 11}" cy="{h + 6}" r="3" fill="#E2B13C"></circle><circle cx="{w / 2 + 11}" cy="{h + 6}" r="3" fill="#E2B13C"></circle>
          <rect x="4" y="12" width="{w - 8}" height="12" rx="6" fill="url(#{p}-wd)" stroke="{OUTLINE}" stroke-width="1"></rect>
          <rect x="4" y="12" width="{w - 8}" height="12" rx="6" fill="#000" filter="url(#{p}-gr)" opacity=".7"></rect>
          <path d="M10 14.5 H{w - 10}" stroke="#C99A6A" stroke-width="1" opacity=".6"></path>
          <circle cx="6" cy="18" r="7" fill="url(#{p}-br)" stroke="#3E2A0C"></circle><circle cx="{w - 6}" cy="18" r="7" fill="url(#{p}-br)" stroke="#3E2A0C"></circle>
          <path d="M{w / 2 - 30} 12 L{w / 2} -4 L{w / 2 + 30} 12" stroke="#8A7A5A" stroke-width="1.4" fill="none"></path>
          <circle cx="{w / 2}" cy="-5" r="3.5" fill="url(#{p}-br)"></circle>
        </svg>
        <div style="position: absolute; left: 30px; right: 30px; top: 58px; display: flex; flex-direction: column; align-items: center; gap: 5px; text-align: center">
          <div style="position: relative; width: 60px; height: 60px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: radial-gradient(circle at 40% 35%, #F6E8C8, #CDB88A); border: 2px solid #E9C37A; box-shadow: 0 3px 6px rgba(0,0,0,.5), inset 0 -3px 6px rgba(0,0,0,.25)">{fn("s1-" + tag, 46)}<span style="position: absolute; right: -22px; top: -8px; width: 26px; height: 26px; display: flex; align-items: center; justify-content: center; font-family: {DISPLAY}; font-weight: 800; font-size: 17px; line-height: 1; color: #FFF6E2; background: #A82E1C; border: 1px solid #E9C37A; border-radius: 3px; box-shadow: 0 2px 3px rgba(0,0,0,.6)">{han}</span></div>
          <span style="font-family: {DISPLAY}; font-weight: 800; font-size: 20px; color: #FFF6E2; text-shadow: 0 2px 2px rgba(0,0,0,.7)">{name}</span>
          <span style="font-family: {MONO}; font-size: 10px; letter-spacing: .14em; color: #FBE3A0; text-shadow: 0 1px 1px #000">{en}</span>
          <span style="font-size: 12px; line-height: 1.35; color: #FFF1D6; text-shadow: 0 1px 2px #000">{line}</span>
          <span style="display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; margin-top: 2px; border-radius: 3px; background: rgba(0,0,0,.45); border: 1px solid #E9C37A; font-family: {MONO}; font-size: 12px; font-weight: 600; color: #FBE3A0">{key}</span>
        </div>
      </div>"""


def build():
    menu = "\n".join(menu_row(*m) for m in MENU)
    banners = "\n".join(cloth_banner(*r) for r in RACES)
    menu_inner = f"""<div style="display: flex; flex-direction: column; gap: 9px">
{menu}
      </div>"""
    status_inner = f"""<div style="display: flex; align-items: center; gap: 12px; font-size: 13px; color: {INK}; white-space: nowrap">
        <span style="font-family: {MONO}; font-size: 11px; letter-spacing: .1em; color: #D8C6A2">이번 판</span>
        <span style="font-weight: 600">S-01 기본 전장</span>
        <span style="color: #C9BB9A">96 × 96 · seed 1234</span>
        <span style="display: flex; align-items: center; gap: 5px; color: {ORE}">{icon_ore("s1-hud-ore", 18)} 500</span>
        <span style="display: flex; align-items: center; gap: 5px; color: {GAS}">{icon_gas("s1-hud-gas", 18)} 0</span>
      </div>"""
    body = f"""<div style="position: relative; width: {W}px; height: {H}px; box-sizing: border-box; background: {GROUND}; color: {INK}; font-family: {BODY}; overflow: hidden">
  {key_art()}

  <div style="position: absolute; left: 60px; top: 44px; display: flex; flex-direction: column; gap: 4px">
    <span style="font-family: {MONO}; font-size: 12px; letter-spacing: .16em; color: #F2CF7E; text-shadow: 0 1px 2px #000">조선펑크 민담 RTS · 1대1 결정론 전술</span>
    <div style="display: flex; align-items: center; gap: 14px">
      {gold_title("s1-wm", "삼파전", 350, 128, 108)}
      {seal("s1-seal", 74)}
    </div>
    <span style="font-family: {MONO}; font-size: 13px; letter-spacing: .62em; color: #D8C6A2; margin-top: -6px">SAMPAJEON</span>
  </div>

  <div style="position: absolute; left: 52px; top: 262px">
    {framed("s1-menu", 470, 470, menu_inner, b=18, pad=16, seed=8)}
  </div>
  <p style="position: absolute; left: 70px; top: 750px; width: 440px; margin: 0; font-size: 14px; line-height: 1.6; color: #E4D8BE; text-shadow: 0 1px 3px #000">귀화석 광맥을 두고 세 세력이 겨루는 조선의 산하.<br>같은 입력이면 같은 결과 — 20ms 고정 틱 위의 한 판.</p>

  <div style="position: absolute; right: 44px; top: 30px">
    {framed("s1-stat", 470, 74, status_inner, b=12, pad=10, crest=False, seed=9)}
  </div>

  <div style="position: absolute; right: 52px; bottom: 54px; display: flex; flex-direction: column; align-items: flex-end; gap: 6px">
    <span style="font-family: {MONO}; font-size: 11px; letter-spacing: .14em; color: #E4D8BE; text-shadow: 0 1px 2px #000">세 세력 · 종족 선택은 로비에서</span>
    <div style="display: flex; gap: 18px">
{banners}
    </div>
  </div>

  <div style="position: absolute; left: 0; right: 0; bottom: 0; height: 34px; padding: 0 56px 0 70px; box-sizing: border-box; display: flex; align-items: center; justify-content: space-between; background: linear-gradient(180deg, rgba(14,10,6,.0), rgba(14,10,6,.92) 40%); font-family: {MONO}; font-size: 12px; color: #C9BB9A">
    <div style="display: flex; gap: 18px">
      <span>sim dev-2c6c817</span><span>protocol v1</span><span>tick 20ms</span>
    </div>
    <div style="display: flex; align-items: center; gap: 8px">
      <span style="width: 8px; height: 8px; border-radius: 50%; background: {OK}; box-shadow: 0 0 8px {OK}"></span>
      <span style="color: {INK}">ws://127.0.0.1:8080</span><span>접속 가능 · 23ms · 방 2개 대기</span>
    </div>
  </div>
  {kit_defs()}
</div>"""
    return page("삼파전 메인 메뉴", body, W, H)


if __name__ == "__main__":
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(build())
    print("wrote", os.path.abspath(OUT))
