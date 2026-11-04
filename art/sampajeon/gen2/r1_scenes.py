"""R1 관상감 공방 buildings (iso hanok) and FX/economy scenes (ids r1-)."""
import math

from r1_lib import (RNG, OUT, RED, RED_L, GREEN, BRASS, BRASS_L, ORE, ORE_L, GAS, P, iso_box, iso_shadow, hanok, roof,
                    stone_course, rect, circ, ell, path, poly, pline, line, g, u, f, ground_patch, lantern, flag,
                    ore_crystal, smoke_puff, flames, tufts, pebbles)
import r1_figs as F
from r1_units import artisan, gunner, hwacha, jeonrip, matchlock, O


def armillary(cx, cy, s=1.0):
    """혼천의 on a stone pedestal."""
    out = [ell(cx + 4 * s, cy + 2 * s, 10 * s, 3.6 * s, "#000", opacity=0.45, filter=u("shadow"))]
    out += [path(f"M{f(cx - 6 * s)} {f(cy)} l{f(2 * s)} {f(-8 * s)} l{f(8 * s)} 0 l{f(2 * s)} {f(8 * s)} Z", u("stoneg"), filter=u("stone"), **O),
            line(cx, cy - 8 * s, cx, cy - 14 * s, "#3A2414", 1.6 * s)]
    c = (cx, cy - 22 * s)
    out.append(circ(c[0], c[1], 9 * s, u("ore-glow"), filter=u("glow-l"), opacity=0.6))
    for rx, ry, rot, w in ((9, 9, 0, 1.4), (9, 3.4, -20, 1.2), (9, 3.4, 60, 1.2), (3.6, 9, 0, 1.1)):
        out.append(ell(c[0], c[1], rx * s, ry * s, "none", stroke=u("brass"), stroke_width=w * s,
                       transform=f"rotate({rot} {f(c[0])} {f(c[1])})"))
    out += [circ(c[0], c[1], 2.4 * s, u("ore"), filter=u("glow")), line(c[0] - 10 * s, c[1] + 6 * s, c[0] + 10 * s, c[1] - 6 * s, BRASS_L, 0.7 * s)]
    return out


def straw_sacks(x, y, n=5, s=1.0):
    out = []
    for k in range(n):
        dx = (k % 3) * 7 * s - (k // 3) * 3 * s
        dy = -(k // 3) * 5 * s + (k % 3) * 2 * s
        out += [ell(x + dx + 1, y + dy + 1.5, 4.4 * s, 2.2 * s, "#000", opacity=0.35, filter=u("blur1")),
                ell(x + dx, y + dy, 4.4 * s, 3.2 * s, "#C8A860", filter=u("cloth"), **O),
                path(f"M{f(x + dx - 3 * s)} {f(y + dy - 1 * s)} q{f(3 * s)} {f(2 * s)} {f(6 * s)} 0", stroke="#7A5E2A", stroke_width=0.6),
                line(x + dx, y + dy - 3 * s, x + dx, y + dy - 5 * s, "#8A6A2A", 0.9)]
    return out


def jars(x, y, n=3, s=1.0):
    out = []
    for k in range(n):
        dx = k * 8 * s
        out += [ell(x + dx + 1.5, y + 1, 4 * s, 1.6 * s, "#000", opacity=0.4, filter=u("blur1")),
                path(f"M{f(x + dx - 2.4 * s)} {f(y)} q{f(-2.8 * s)} {f(-5 * s)} {f(-0.4 * s)} {f(-9 * s)} l{f(5.6 * s)} 0 q{f(2.4 * s)} {f(4 * s)} {f(-0.4 * s)} {f(9 * s)} Z",
                     "#5A3A24", filter=u("tex"), **O),
                ell(x + dx + 0.4 * s, y - 9 * s, 2.8 * s, 1.0 * s, "#2A1A10"),
                path(f"M{f(x + dx - 1.6 * s)} {f(y - 7 * s)} q{f(-1 * s)} {f(3 * s)} 0 {f(6 * s)}", stroke="#B08060", stroke_width=0.7, opacity=0.7)]
    return out


# ------------------------------------------------------------------ buildings (viewBox 190×150)
def b_hall():
    out = ground_patch(95, 112, 92, 40, "yard", 1.0)
    out += iso_box(85, 44, 90, 70, 4, u("stoneg"), u("stoneg"), "#4C463C", z0=0, tex="stone",
                   extra_l=stone_course(90, 4, 1), extra_r=stone_course(70, 4, 1) + [rect(0, 0, 70, 4, "#000", opacity=0.35)])
    out += hanok(85, 50, 74, 54, Hw=20, rh=22, o=9, plinth=5, cols=(5, 4), L=5, z_base=4)
    out += armillary(36, 118, 0.9)
    out += lantern(130, 120, 1.0) + lantern(60, 126, 1.0)
    out += flag(150, 108, RED, 30, 12) + flag(28, 96, GREEN, 26, 10)
    out += straw_sacks(150, 128, 4, 0.8)
    return out


def b_still():
    out = ground_patch(95, 112, 88, 38, "yard", 0.8)
    out.append(ell(118, 120, 22, 9, "#2A1A3A", opacity=0.9, filter=u("blur1")))
    out.append(ell(118, 106, 26, 30, u("gas"), filter=u("glow-l")))
    out += hanok(70, 56, 46, 40, Hw=16, rh=16, o=7, plinth=4, cols=(3, 3), L=4)
    # brass pot still over the vent
    cx, cy = 128, 118
    out += [ell(cx + 4, cy + 2, 16, 5, "#000", opacity=0.45, filter=u("shadow")),
            path(f"M{cx - 13} {cy} l0 -18 q13 -6 26 0 l0 18 q-13 5 -26 0 Z", u("brass"), filter=u("tex"), **O),
            ell(cx, cy - 18, 13, 3.6, "#8A6A30", **O),
            path(f"M{cx - 8} {cy - 20} q8 -16 16 0", u("brass"), **O),
            path(f"M{cx} {cy - 30} l0 -10 q0 -6 -10 -8 l-24 -6", stroke=u("iron"), stroke_width=3, stroke_linecap="round"),
            path(f"M{cx} {cy - 30} l0 -10 q0 -6 -10 -8 l-24 -6", stroke="#C8D2D6", stroke_width=0.6, opacity=0.6)]
    for k in range(3):
        out.append(rect(cx - 13, cy - 15 + k * 5, 26, 1.2, "#6E5222", opacity=0.8))
    out += [ell(cx - 6, cy - 9, 2.6, 4, "#FFF0C0", opacity=0.6)]
    out += smoke_puff(cx + 4, cy - 46, 7, op=0.55)
    for k in range(5):
        out.append(circ(cx - 10 + k * 5, cy - 34 - k * 4, 2 + k * 0.6, GAS, opacity=0.35, filter=u("blur1")))
    out += jars(40, 120, 3, 0.9)
    return out


def b_drill():
    out = ground_patch(95, 112, 90, 38, "yard", 1.0)
    out += hanok(92, 46, 52, 36, Hw=16, rh=16, o=7, plinth=4, cols=(4, 3), kind="plaster", glow=False, L=4)
    # 홍살문 red gate (two posts, beam, spikes)
    gx, gy = 40, 128
    out += [line(gx + 2, gy + 2, gx + 4, gy - 40, "#000", 2.4, opacity=0.35, filter=u("blur1")),
            line(gx, gy, gx, gy - 40, "#9A2A18", 2.6), line(gx + 30, gy - 15, gx + 30, gy - 55, "#9A2A18", 2.6),
            line(gx - 2, gy - 38, gx + 32, gy - 53, "#7A2010", 2.4), line(gx - 2, gy - 34, gx + 32, gy - 49, "#7A2010", 1.6)]
    for k in range(11):
        x = gx + k * 3
        y = gy - 38 - k * 1.5
        out.append(line(x, y, x, y - 7, RED, 1.0))
    out.append(circ(gx + 15, gy - 51, 2.6, "#F2D58A", stroke=RED, stroke_width=0.8))
    # straw targets
    for tx, ty in ((140, 124), (160, 112)):
        out += [ell(tx + 3, ty + 1, 7, 2.4, "#000", opacity=0.4, filter=u("blur1")),
                line(tx - 4, ty, tx - 3, ty - 14, "#5E3A22", 1.2), line(tx + 4, ty, tx + 3, ty - 14, "#5E3A22", 1.2),
                ell(tx, ty - 14, 6.4, 7, "#C8A860", filter=u("cloth"), **O), ell(tx, ty - 14, 4.2, 4.6, "#EFE8D8"),
                ell(tx, ty - 14, 2.2, 2.4, RED), ell(tx, ty - 14, 0.9, 1, "#1A1008")]
    out += flag(70, 132, RED, 24, 10)
    out.append(g([gunner(aim=True, seed=3)], transform="translate(112 136) scale(0.245)"))
    out.append(g([gunner(seed=5)], transform="translate(96 140) scale(0.245)"))
    return out


def b_clock():
    out = ground_patch(95, 112, 88, 38, "yard", 0.8)
    out += hanok(96, 44, 54, 40, Hw=17, rh=17, o=7, plinth=4, cols=(4, 3), L=4)
    # stepped water vessels (three tiers) + streams
    for k, (x, y, s) in enumerate(((30, 108, 1.1), (46, 118, 0.95), (60, 128, 0.85))):
        out += iso_box(x - 8, y - 12, 12 * s, 10 * s, 14 - k * 4, u("stoneg"), u("stoneg"), "#4C463C", tex="stone")
        top = P(x - 8, y - 12, 6 * s, 5 * s, 14 - k * 4)
        out += [path(f"M{f(top[0] - 6 * s)} {f(top[1])} q{f(-1 * s)} {f(-8 * s)} {f(6 * s)} {f(-9 * s)} q{f(7 * s)} {f(1 * s)} {f(6 * s)} {f(9 * s)} Z", u("brass"), filter=u("tex"), **O),
                ell(top[0], top[1] - 8.6 * s, 5 * s, 1.6 * s, "#2E5E70")]
    out += [path("M38 92 q4 6 6 14", stroke="#7CC4E8", stroke_width=1.2, opacity=0.8), path("M52 104 q4 6 6 14", stroke="#7CC4E8", stroke_width=1.2, opacity=0.8)]
    # brass gear wheel (dial)
    gx, gy = 150, 112
    teeth = []
    for k in range(16):
        a = math.tau * k / 16
        teeth.append((gx + math.cos(a) * 14, gy + math.sin(a) * 7))
        a2 = a + math.tau / 32
        teeth.append((gx + math.cos(a2) * 11.5, gy + math.sin(a2) * 5.75))
    out += [ell(gx + 4, gy + 6, 14, 4, "#000", opacity=0.4, filter=u("shadow")),
            poly(teeth, u("brass"), **O), ell(gx, gy, 8, 4, "#5A4220", **O), ell(gx, gy, 2.4, 1.2, BRASS_L)]
    for k in range(6):
        a = math.tau * k / 6
        out.append(line(gx, gy, gx + math.cos(a) * 8, gy + math.sin(a) * 4, BRASS, 1.0))
    out += lantern(124, 132, 0.9)
    return out


def b_tower():
    out = ground_patch(95, 118, 70, 30, "yard", 0.7)
    out += iso_box(78, 84, 32, 32, 34, u("stoneg"), u("stoneg"), "#4C463C", tex="stone",
                   extra_l=stone_course(32, 34, 6), extra_r=stone_course(32, 34, 6) + [rect(0, 0, 32, 34, "#000", opacity=0.38)])
    out += hanok(78, 86, 24, 24, Hw=11, rh=13, o=6, plinth=2, cols=(2, 2), glow=True, door=False, L=3.5, z_base=34)
    # rain gauge (측우기) on the parapet + small 총통 cannon
    rx, ry = P(78, 84, 30, 3, 34)
    out += [rect(rx - 2.2, ry - 8, 4.4, 8, u("brass"), **O), ell(rx, ry - 8, 2.2, 0.8, "#3A2A1A")]
    cx, cy = P(78, 84, 3, 30, 36)
    out.append(g([rect(-1, -1.6, 14, 3.2, u("iron"), **O), circ(0, 0, 2.2, "#2A1A10")], transform=f"translate({f(cx)} {f(cy)}) rotate(160)"))
    out += flag(122, 70, RED, 22, 9) + lantern(56, 124, 0.9)
    # vision ring
    out.append(ell(95, 118, 86, 30, "none", stroke=RED_L, stroke_width=0.8, stroke_dasharray="3 3", opacity=0.6))
    return out


def b_granary():
    out = ground_patch(95, 114, 86, 36, "yard", 0.9)
    # stilts
    ox, oy = 92, 46
    for a, b in ((2, 2), (46, 2), (2, 34), (46, 34), (24, 34)):
        x, y = P(ox, oy, a, b)
        out += [line(x + 2, y + 1, x + 4, y - 1, "#000", 2, opacity=0.3, filter=u("blur1")),
                line(x, y, x, y - 12, "#4A2E1E", 2.6), circ(x, y, 1.8, u("stoneg"))]
    out += hanok(ox, oy, 48, 36, Hw=16, rh=17, o=7, plinth=2, cols=(4, 3), kind="plaster", glow=False, L=4, z_base=12)
    # ladder
    lx, ly = P(ox, oy, 26, 40)
    out += [line(lx - 4, ly + 8, lx - 2, ly - 14, "#6E4628", 1.2), line(lx + 2, ly + 8, lx + 4, ly - 14, "#6E4628", 1.2)]
    for k in range(5):
        out.append(line(lx - 3.6 + k * 0.4, ly + 6 - k * 4.4, lx + 2.4 + k * 0.4, ly + 6 - k * 4.4, "#8A5A38", 0.9))
    out += straw_sacks(140, 128, 5, 0.9) + jars(30, 120, 3, 0.85)
    return out


def b_works():
    out = ground_patch(95, 112, 92, 38, "yard", 1.0)
    out += hanok(96, 42, 70, 36, Hw=16, rh=16, o=7, plinth=4, cols=(5, 3), kind="plaster", glow=False, L=4)
    # forge glow from the door + chimney smoke
    fx, fy = P(96, 42, 30, 38, 6)
    out += [ell(fx, fy + 6, 20, 8, "#FF8A3A", opacity=0.4, filter=u("glow-l"))]
    out += smoke_puff(130, 26, 7, dark=True, op=0.7) + smoke_puff(138, 14, 5, dark=True, op=0.5)
    # treadwheel crane
    cx, cy = 34, 112
    out += [line(cx, cy, cx + 8, cy - 46, "#5E3A22", 2.4), line(cx + 16, cy + 4, cx + 8, cy - 46, "#4A2E1E", 2.4),
            line(cx + 8, cy - 46, cx + 36, cy - 52, "#6E4628", 2.0), line(cx + 34, cy - 52, cx + 34, cy - 30, "#C8B48A", 0.7),
            rect(cx + 31, cy - 30, 6, 4, u("iron"), **O),
            ell(cx + 6, cy - 14, 12, 13, "none", stroke="#6E4628", stroke_width=2.4),
            ell(cx + 6, cy - 14, 9, 10, "none", stroke="#8A5A38", stroke_width=0.8, stroke_dasharray="1.5 2.5")]
    # hwacha under construction
    out.append(g(hwacha(0, 0, crew=False), transform="translate(116 102) scale(0.5)"))
    out += lantern(78, 132, 0.9)
    return out


# ------------------------------------------------------------------ FX + economy scenes (viewBox 164×136)
def scene_ground(kind="field", d=0.8):
    return ground_patch(82, 96, 78, 34, kind, d)


def rocket(x, y, ang, trail=28, s=1.0):
    dx, dy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    tx, ty = x - dx * trail, y - dy * trail
    out = [line(tx, ty, x, y, "#FFB040", 3.2 * s, stroke_linecap="round", opacity=0.45, filter=u("glow")),
           line(tx + dx * trail * 0.4, ty + dy * trail * 0.4, x, y, "#FFF0C0", 1.2 * s, stroke_linecap="round")]
    for k in range(5):
        t = RNG.uniform(0.1, 0.9)
        out.append(circ(tx + dx * trail * t + RNG.uniform(-2, 2), ty + dy * trail * t + RNG.uniform(-2, 2), RNG.uniform(0.4, 0.9), "#FFE08A"))
    out += [line(x, y, x + dx * 9 * s, y + dy * 9 * s, "#C8B48A", 0.9), circ(x + dx * 9 * s, y + dy * 9 * s, 1.0, u("iron"))]
    return out


def fx_ignite():
    out = scene_ground()
    out.append(g(hwacha(0, 0, fire=True, crew=False), transform="translate(40 34) scale(0.85)"))
    for k in range(8):
        a = math.radians(200 + k * 9)
        out.append(line(64, 74, 64 + math.cos(a) * 26, 74 + math.sin(a) * 18, "#FFE08A", 0.7, opacity=0.8))
    return out


def fx_flight():
    out = scene_ground()
    for k, (x, y) in enumerate(((40, 40), (58, 30), (76, 44), (94, 26), (110, 40))):
        out += rocket(x, y, -150 + k * 3, 30)
    out += smoke_puff(140, 96, 8, op=0.5)
    return out


def fx_burst():
    out = scene_ground()
    for (x, y, r) in ((58, 92, 18), (96, 84, 14), (78, 104, 11)):
        out += [ell(x, y + 4, r * 1.3, r * 0.5, u("scorch"))]
        out += smoke_puff(x + 3, y - r * 0.9, r * 0.6, dark=True, op=0.6)
        out += [circ(x, y - r * 0.4, r, u("fireball"), filter=u("fire")), circ(x, y - r * 0.4, r * 0.4, "#FFFBE0", filter=u("glow"))]
        for k in range(7):
            a = RNG.uniform(0, math.tau)
            out.append(line(x, y - r * 0.4, x + math.cos(a) * r * 1.6, y - r * 0.4 + math.sin(a) * r * 1.1, "#FFD68A", 0.8))
    for _ in range(10):
        out.append(rect(RNG.uniform(40, 120), RNG.uniform(60, 100), 2, 1.2, "#3A2414", transform=f"rotate({RNG.randint(0, 90)} 80 80)"))
    return out


def fx_smoke():
    out = scene_ground()
    for (x, y) in ((58, 96), (96, 88), (80, 108)):
        out.append(ell(x, y, 16, 6, u("scorch")))
        out += smoke_puff(x, y - 12, 12, dark=True, op=0.85) + smoke_puff(x + 6, y - 32, 10, op=0.75) + smoke_puff(x + 12, y - 50, 8, op=0.5)
        for _ in range(4):
            out.append(circ(x + RNG.uniform(-10, 10), y + RNG.uniform(-3, 3), 1.1, u("ember"), filter=u("glow")))
    return out


def fx_death():
    out = scene_ground()
    out.append(ell(82, 98, 22, 8, u("scorch")))
    out.append(g([F.fallen_gear()], transform="translate(54 96) scale(0.82)"))
    out.append(g([F.wreckage()], transform="translate(112 106) scale(0.92)"))
    out += smoke_puff(86, 74, 9, op=0.65) + smoke_puff(92, 54, 6, op=0.4)
    for _ in range(5):
        out.append(circ(82 + RNG.uniform(-14, 14), 98 + RNG.uniform(-4, 4), 0.9, u("ember"), filter=u("glow")))
    return out


def fx_destroyed():
    out = scene_ground("yard")
    out += hanok(80, 40, 44, 32, Hw=12, rh=10, o=5, plinth=3, cols=(3, 2), kind="plaster", glow=False, door=False, L=2, burnt=True)
    out.append(rect(0, 0, 164, 136, "#000", opacity=0.0))
    out += flames(68, 70, 1.1, 3) + flames(98, 66, 0.9, 2)
    out += smoke_puff(76, 34, 11, dark=True, op=0.8) + smoke_puff(94, 16, 8, dark=True, op=0.6)
    for _ in range(14):
        x, y = RNG.uniform(40, 126), RNG.uniform(94, 116)
        out.append(rect(x, y, 3.2, 1.6, "#3A4246", transform=f"rotate({RNG.randint(-30, 30)} {f(x)} {f(y)})", **O))
    return out


def fx_harvest():
    out = scene_ground()
    out += ore_crystal(104, 98, 1.1)
    out.append(g([artisan(swing=-38, seed=2)], transform="translate(58 114) scale(0.43)"))
    for k in range(6):
        a = math.radians(-160 + k * 22)
        out.append(poly([(96, 82), (96 + math.cos(a) * 10, 82 + math.sin(a) * 8), (98 + math.cos(a) * 8, 84 + math.sin(a) * 6)], u("ore"), opacity=0.9))
    out.append(circ(94, 82, 5, "#FFFFFF", opacity=0.7, filter=u("glow")))
    return out


def fx_dropoff():
    out = scene_ground("yard")
    out += hanok(92, 36, 46, 32, Hw=13, rh=13, o=6, plinth=3, cols=(4, 2), L=3)
    out.append(g([artisan()], transform="translate(50 122) scale(0.36)"))
    out += [path("M70 104 q14 -6 22 -18", stroke=ORE_L, stroke_width=1.2, stroke_dasharray="2 2"),
            path("M92 86 l-6 0 l3 -5 Z", ORE_L)]
    return out
