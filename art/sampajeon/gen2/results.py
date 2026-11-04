"""Generates design2/project/Results.dc.html (S4 결과·리플레이 검증, id prefix s4-).

High-fidelity pass: a painted dusk battlefield aftermath (the goblin lair
smouldering, the Joseon Works banner planted on the ridge) behind ornate
wood-and-hanji panels.
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

P = "s4"
W, H = 1440, 900
OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "Results.dc.html")
C1, C2 = "#B83A22", "#2A6E9E"


# ==========================================================================
# Painted dusk battlefield
# ==========================================================================

def arrow(x, y, ang, L, rng):
    dx, dy = math.cos(math.radians(ang)) * L, math.sin(math.radians(ang)) * L
    tx, ty = x - dx, y - dy
    return (f'<path d="M{x:.1f} {y:.1f} L{tx:.1f} {ty:.1f}" stroke="#2A1A0E" stroke-width="1.3"></path>'
            f'<path d="M{tx:.1f} {ty:.1f} l{-dx * 0.18 - dy * 0.12:.1f} {-dy * 0.18 + dx * 0.12:.1f} M{tx:.1f} {ty:.1f} l{-dx * 0.18 + dy * 0.12:.1f} {-dy * 0.18 - dx * 0.12:.1f}" '
            f'stroke="{rng.choice(("#E8E1C8", "#C8432B", "#D8CDB0"))}" stroke-width="1.6"></path>'
            f'<ellipse cx="{x + 1:.1f}" cy="{y + 0.5:.1f}" rx="3" ry="1" fill="#000" opacity="0.4"></ellipse>')


def crow(x, y, s):
    return (f'<path d="M{x:.1f} {y:.1f} q{-6 * s:.1f} {-5 * s:.1f} {-13 * s:.1f} {-2 * s:.1f} q{6 * s:.1f} {0:.1f} {13 * s:.1f} {4 * s:.1f} '
            f'q{6 * s:.1f} {-4 * s:.1f} {13 * s:.1f} {-4 * s:.1f} q{-7 * s:.1f} {-3 * s:.1f} {-13 * s:.1f} {2 * s:.1f} Z" fill="#120C10"></path>')


def smoke_column(x, y, h, w, rng, col="#4A3A44", op=0.55):
    puffs = []
    for i in range(16):
        t = i / 15
        cx = x + math.sin(t * 3.2) * w * 0.6 + t * w * 1.4
        cy = y - t * h
        r = w * (0.35 + t * 0.9)
        puffs.append(f'<ellipse cx="{cx:.1f}" cy="{cy:.1f}" rx="{r:.1f}" ry="{r * 0.72:.1f}" fill="{col}" opacity="{op * (1 - t * 0.6):.2f}"></ellipse>')
    return f'<g filter="url(#{P}-smoke)">{"".join(puffs)}</g>'


def hwacha_wreck(x, y):
    """Broken rocket cart: tipped launcher box, one wheel off, spent rockets."""
    o = [f'<ellipse cx="{x + 10}" cy="{y + 8}" rx="62" ry="13" fill="#0A0604" opacity="0.55" filter="url(#{P}-blur6)"></ellipse>']
    # tipped launcher frame (iso box)
    o.append(f'<path d="M{x - 40} {y - 22} L{x + 6} {y - 2} L{x + 6} {y - 30} L{x - 40} {y - 50} Z" fill="#6A3A22" stroke="#1A0A04" stroke-width="1"></path>')
    o.append(f'<path d="M{x + 6} {y - 2} L{x + 30} {y - 14} L{x + 30} {y - 42} L{x + 6} {y - 30} Z" fill="#3A1E10" stroke="#1A0A04" stroke-width="1"></path>')
    o.append(f'<path d="M{x - 40} {y - 50} L{x + 6} {y - 30} L{x + 30} {y - 42} L{x - 16} {y - 62} Z" fill="#8A5232" stroke="#1A0A04" stroke-width="1"></path>')
    holes = []
    for r in range(4):
        for c in range(7):
            hx = x - 34 + c * 6.4 + r * 6
            hy = y - 50 + c * 2.8 - r * 3
            holes.append(f'<ellipse cx="{hx:.1f}" cy="{hy:.1f}" rx="1.8" ry="1" fill="#120804"></ellipse>')
    o.append("".join(holes))
    o.append(f'<path d="M{x - 40} {y - 36} L{x + 6} {y - 16}" stroke="#C9A35A" stroke-width="1.6"></path>')
    # charring and dancheong scraps
    o.append(f'<path d="M{x - 30} {y - 20} q10 -10 26 -4 l2 10 q-14 2 -28 -6 Z" fill="#120804" opacity="0.7"></path>')
    # wheels
    o.append(f'<ellipse cx="{x - 30}" cy="{y - 6}" rx="7" ry="13" fill="#3A2416" stroke="#120804" stroke-width="1.6"></ellipse>'
             f'<path d="M{x - 30} {y - 19} V{y + 7} M{x - 37} {y - 6} H{x - 23}" stroke="#7A5236" stroke-width="1.3"></path>')
    o.append(f'<ellipse cx="{x + 54}" cy="{y + 8}" rx="14" ry="5.4" fill="#3A2416" stroke="#120804" stroke-width="1.4"></ellipse>'
             f'<path d="M{x + 40} {y + 8} H{x + 68} M{x + 54} {y + 3} V{y + 13}" stroke="#7A5236" stroke-width="1.2"></path>')
    # shafts
    o.append(f'<path d="M{x + 30} {y - 14} L{x + 70} {y - 2}" stroke="#4A2E1C" stroke-width="3" stroke-linecap="round"></path>')
    # spent rockets in the dirt
    for i, (dx, dy, a) in enumerate(((-64, 6, -20), (-52, 14, 12), (38, 20, -34), (70, 18, 8), (-80, 18, 30))):
        rx, ry = x + dx, y + dy
        ex, ey = rx + math.cos(math.radians(a)) * 22, ry + math.sin(math.radians(a)) * 22
        o.append(f'<path d="M{rx} {ry} L{ex:.1f} {ey:.1f}" stroke="#5A3A22" stroke-width="1.6"></path>'
                 f'<path d="M{rx} {ry} l{math.cos(math.radians(a)) * 5:.1f} {math.sin(math.radians(a)) * 5:.1f}" stroke="#2A1408" stroke-width="3.4"></path>')
    # embers
    o.append(f'<circle cx="{x - 20}" cy="{y - 34}" r="16" fill="url(#{P}-glow)" opacity="0.7"></circle>')
    o.append(f'<path d="M{x - 24} {y - 32} q3 -9 5 -14 q3 6 5 14 q-5 3 -10 0 Z" fill="#F2A04A"></path>'
             f'<path d="M{x - 22} {y - 33} q2 -5 3 -8 q2 4 2.6 8 q-2.6 1.4 -5.6 0 Z" fill="#FFE6A0"></path>')
    return "".join(o)


def lair_ruin(x, y, rng):
    """Burnt, split hollow zelkova trunk with dying goblin-fire."""
    o = [f'<ellipse cx="{x + 16}" cy="{y + 6}" rx="80" ry="16" fill="#0A0604" opacity="0.55" filter="url(#{P}-blur6)"></ellipse>']
    o.append(f'<path d="M{x - 52} {y} C{x - 50} {y - 40} {x - 46} {y - 64} {x - 40} {y - 92} L{x - 30} {y - 74} L{x - 22} {y - 100} '
             f'L{x - 12} {y - 70} C{x - 10} {y - 40} {x - 16} {y - 18} {x - 8} {y} Z" fill="url(#{P}-bark)"></path>')
    o.append(f'<path d="M{x + 6} {y} C{x + 8} {y - 30} {x + 14} {y - 54} {x + 12} {y - 80} L{x + 22} {y - 66} L{x + 30} {y - 88} '
             f'L{x + 38} {y - 60} C{x + 44} {y - 36} {x + 48} {y - 16} {x + 56} {y} Z" fill="url(#{P}-barkD)"></path>')
    for d in (f"M{x - 44} {y - 20} q4 -20 2 -40", f"M{x - 30} {y - 10} q2 -26 6 -52", f"M{x + 22} {y - 12} q4 -22 0 -46", f"M{x + 40} {y - 8} q0 -20 -4 -38"):
        o.append(f'<path d="{d}" fill="none" stroke="#120804" stroke-width="1.6" opacity="0.8"></path>')
    # roots
    for d in (f"M{x - 52} {y} q-20 2 -34 12", f"M{x + 56} {y} q22 0 38 10", f"M{x - 20} {y} q-6 8 -18 16", f"M{x + 30} {y} q10 8 8 18"):
        o.append(f'<path d="{d}" fill="none" stroke="#2A1A10" stroke-width="5" stroke-linecap="round"></path>')
    # dying goblin-fire inside the split
    o.append(f'<ellipse cx="{x - 1}" cy="{y - 18}" rx="34" ry="26" fill="url(#{P}-glowb)" opacity="0.85"></ellipse>')
    for (dx, s) in ((-6, 1.0), (6, 0.8), (-1, 1.3)):
        fx, fy = x + dx, y - 6
        o.append(f'<path d="M{fx} {fy - 22 * s:.1f} C{fx + 6 * s:.1f} {fy - 12 * s:.1f} {fx + 7 * s:.1f} {fy - 4 * s:.1f} {fx} {fy} C{fx - 7 * s:.1f} {fy - 4 * s:.1f} {fx - 6 * s:.1f} {fy - 12 * s:.1f} {fx} {fy - 22 * s:.1f} Z" '
                 f'fill="#A8DDF5" opacity="0.85"></path>')
    # scattered blue embers
    for _ in range(14):
        ex, ey = x + rng.uniform(-70, 80), y + rng.uniform(-6, 14)
        o.append(f'<circle cx="{ex:.1f}" cy="{ey:.1f}" r="{1 + rng.random() * 1.6:.1f}" fill="#A8DDF5" opacity="{0.4 + rng.random() * 0.5:.2f}"></circle>')
    return "".join(o)


def banner_pole(x, y):
    """Victorious Joseon Works war banner on a mound (wind from the left)."""
    return (f'<path d="M{x - 40} {y + 4} Q{x} {y - 16} {x + 46} {y + 4} Z" fill="#3A2C1C"></path>'
            f'<path d="M{x - 30} {y} Q{x} {y - 12} {x + 32} {y}" fill="none" stroke="#7A6446" stroke-width="1.2"></path>'
            f'<path d="M{x} {y - 4} V{y - 150}" stroke="#2A1A0E" stroke-width="3.4"></path>'
            f'<path d="M{x - 1} {y - 4} V{y - 150}" stroke="#7A5236" stroke-width="1"></path>'
            f'<path d="M{x} {y - 150} l-3 -8 l3 -6 l3 6 Z" fill="url(#{P}-brass)"></path>'
            f'<path d="M{x + 2} {y - 144} C{x + 30} {y - 150} {x + 52} {y - 132} {x + 82} {y - 140} L{x + 74} {y - 116} L{x + 86} {y - 92} '
            f'C{x + 56} {y - 84} {x + 32} {y - 102} {x + 2} {y - 94} Z" fill="url(#{P}-flag)" stroke="#4A1006" stroke-width="1"></path>'
            f'<path d="M{x + 2} {y - 144} C{x + 30} {y - 150} {x + 52} {y - 132} {x + 82} {y - 140}" fill="none" stroke="#2E7A66" stroke-width="4"></path>'
            f'<path d="M{x + 2} {y - 94} C{x + 32} {y - 102} {x + 56} {y - 84} {x + 86} {y - 92}" fill="none" stroke="#2E7A66" stroke-width="4"></path>'
            f'<circle cx="{x + 38}" cy="{y - 118}" r="13" fill="none" stroke="#F2D58A" stroke-width="2"></circle>'
            f'<rect x="{x + 29}" y="{y - 127}" width="18" height="18" fill="none" stroke="#F2D58A" stroke-width="1.6" transform="rotate(8 {x + 38} {y - 118})"></rect>'
            f'<circle cx="{x + 38}" cy="{y - 118}" r="3" fill="#F2D58A"></circle>'
            f'<path d="M{x + 14} {y - 130} C{x + 30} {y - 134} {x + 50} {y - 122} {x + 66} {y - 126}" fill="none" stroke="#FF9A70" stroke-opacity="0.5" stroke-width="1.4"></path>')


def battlefield():
    rng = random.Random(77)
    o = []
    defs = (f'<linearGradient id="{P}-sky" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0" stop-color="#140F1E"></stop><stop offset="0.12" stop-color="#2A1B2E"></stop><stop offset="0.17" stop-color="#5A2A30"></stop>'
            f'<stop offset="0.24" stop-color="#B04A2C"></stop><stop offset="0.285" stop-color="#E8873E"></stop><stop offset="0.31" stop-color="#5A3428"></stop>'
            f'<stop offset="1" stop-color="#1A120C"></stop></linearGradient>'
            f'<radialGradient id="{P}-sun" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFF2C0"></stop><stop offset="0.5" stop-color="#FFC060"></stop><stop offset="1" stop-color="#F07A30"></stop></radialGradient>'
            f'<radialGradient id="{P}-halo" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FFB050" stop-opacity="0.7"></stop><stop offset="1" stop-color="#E0603F" stop-opacity="0"></stop></radialGradient>'
            f'<linearGradient id="{P}-field" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6A4A30"></stop><stop offset="0.18" stop-color="#4A3424"></stop><stop offset="1" stop-color="#1E1610"></stop></linearGradient>'
            f'<linearGradient id="{P}-bark" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#5A4030"></stop><stop offset="0.6" stop-color="#2E2018"></stop><stop offset="1" stop-color="#140C08"></stop></linearGradient>'
            f'<linearGradient id="{P}-barkD" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3A2A1E"></stop><stop offset="1" stop-color="#0E0806"></stop></linearGradient>'
            f'<linearGradient id="{P}-flag" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F06A44"></stop><stop offset="0.5" stop-color="#C8432B"></stop><stop offset="1" stop-color="#7A1E10"></stop></linearGradient>'
            f'<linearGradient id="{P}-shade" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#0E0A08" stop-opacity="0.82"></stop><stop offset="0.38" stop-color="#0E0A08" stop-opacity="0.35"></stop><stop offset="0.56" stop-color="#0E0A08" stop-opacity="0"></stop></linearGradient>'
            f'<linearGradient id="{P}-puddle" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E8873E" stop-opacity="0.75"></stop><stop offset="1" stop-color="#5A2A30" stop-opacity="0.6"></stop></linearGradient>')
    o.append(f'<defs>{defs}</defs>')
    o.append(f'<rect width="{W}" height="{H}" fill="url(#{P}-sky)"></rect><g transform="translate(0 -44)">')
    for _ in range(40):
        o.append(f'<circle cx="{rng.random() * W:.0f}" cy="{80 + rng.random() * 70:.0f}" r="{0.5 + rng.random() * 0.7:.1f}" fill="#FFF0E0" opacity="{0.25 + rng.random() * 0.4:.2f}"></circle>')
    # cloud streaks lit from below
    for i in range(9):
        cx, cy = rng.uniform(80, 1400), rng.uniform(150, 255)
        w = rng.uniform(140, 380)
        col = rng.choice(("#7A3A3A", "#9A4A36", "#5A2E3A", "#C8643A"))
        o.append(f'<ellipse cx="{cx:.0f}" cy="{cy:.0f}" rx="{w:.0f}" ry="{rng.uniform(4, 9):.1f}" fill="{col}" opacity="0.55" filter="url(#{P}-blur6)"></ellipse>')
    o.append(f'<circle cx="770" cy="282" r="150" fill="url(#{P}-halo)"></circle><circle cx="770" cy="286" r="40" fill="url(#{P}-sun)"></circle>')
    for d, c, op in ((ridge(W, 262, 34, 1.4), "#5A3040", 0.85), (ridge(W, 286, 26, 3.1), "#3E2430", 0.95), (ridge(W, 306, 18, 5.2), "#2A1A20", 1)):
        o.append(f'<path d="{d}" fill="{c}" opacity="{op}"></path>')
    o.append(f'<ellipse cx="760" cy="300" rx="560" ry="10" fill="#F2B070" opacity="0.18" filter="url(#{P}-blur6)"></ellipse>')
    # the field
    o.append(f'<path d="M0 316 C240 306 480 312 720 308 C960 304 1200 312 1440 306 L1440 900 L0 900 Z" fill="url(#{P}-field)"></path>')
    o.append(f'<rect y="300" width="{W}" height="{H - 300}" fill="#000" filter="url(#{P}-ground)" opacity="0.85"></rect>')
    # paddy dikes receding (iso-ish diagonals) and furrows
    for i in range(12):
        y0 = 322 + i * i * 2.6
        o.append(f'<path d="M0 {y0 + 10:.0f} C400 {y0:.0f} 1000 {y0 + 6:.0f} 1440 {y0 - 4:.0f}" fill="none" stroke="#7A5A3A" stroke-opacity="{0.35 - i * 0.02:.2f}" stroke-width="{1 + i * 0.25:.1f}"></path>')
    # puddles reflecting the sky
    for (cx, cy, rx) in ((560, 352, 60), (930, 340, 44), (300, 372, 70)):
        o.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{rx * 0.16:.1f}" fill="url(#{P}-puddle)"></ellipse>'
                 f'<ellipse cx="{cx - rx * 0.2:.0f}" cy="{cy - 1}" rx="{rx * 0.5:.0f}" ry="1" fill="#FFE0A0" opacity="0.6"></ellipse>')
    # scorch craters with embers
    for (cx, cy, r) in ((700, 336, 40), (1060, 352, 54), (420, 346, 34), (860, 372, 44)):
        o.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{r}" ry="{r * 0.3:.1f}" fill="#0E0806" opacity="0.8" filter="url(#{P}-blur1)"></ellipse>'
                 f'<ellipse cx="{cx}" cy="{cy - 1}" rx="{r * 0.7:.0f}" ry="{r * 0.18:.1f}" fill="#2A1A10"></ellipse>')
        for _ in range(6):
            o.append(f'<circle cx="{cx + rng.uniform(-r * 0.6, r * 0.6):.1f}" cy="{cy + rng.uniform(-3, 3):.1f}" r="{1 + rng.random():.1f}" fill="#FF9A40" opacity="{0.5 + rng.random() * 0.5:.2f}"></circle>')
    # smoke columns
    o.append(smoke_column(990, 300, 230, 40, rng, "#6A5866", 0.55))
    o.append(smoke_column(700, 330, 150, 26, rng, "#7A6670", 0.4))
    o.append(smoke_column(470, 320, 120, 20, rng, "#4A3A40", 0.4))
    # set pieces
    o.append(lair_ruin(1000, 330, rng))
    o.append(hwacha_wreck(820, 352))
    o.append(banner_pole(640, 326))
    # broken goblin club and arrows
    o.append(f'<g transform="translate(960 368) rotate(-18)"><path d="M0 0 L58 -6 L60 4 L2 8 Z" fill="#5A3A22" stroke="#1A0A04" stroke-width="0.8"></path>'
             + "".join(f'<circle cx="{14 + k * 9}" cy="{1 - k * 0.4:.1f}" r="1.8" fill="#C9A35A"></circle>' for k in range(5)) + '</g>')
    for _ in range(46):
        x = rng.uniform(320, 1300)
        y = rng.uniform(318, 400)
        o.append(arrow(x, y, rng.uniform(-150, -30), 10 + (y - 300) * 0.12, rng))
    for _ in range(70):
        x = rng.uniform(0, W)
        y = rng.uniform(320, 420)
        o.append(grass_tuft(x, y, rng, 0.8 + (y - 320) / 120, ("#4A3A1E", "#6A5228", "#3A2E18")))
    # crows
    for (x, y, s) in ((560, 150, 0.9), (600, 136, 0.7), (1260, 120, 1.0), (1300, 140, 0.8), (1220, 150, 0.6), (980, 110, 0.7)):
        o.append(crow(x, y, s))
    o.append('</g>')
    # legibility wash on the left and a bottom fade into the panels
    o.append(f'<rect width="{W}" height="{H}" fill="url(#{P}-shade)"></rect>')
    o.append(f'<rect y="330" width="{W}" height="{H - 330}" fill="#0E0A08" opacity="0.35"></rect>')
    return (f'<svg width="{W}" height="{H}" viewBox="0 0 {W} {H}" aria-hidden="true" style="position: absolute; inset: 0; z-index: 0">'
            f'{"".join(o)}</svg>')


# ==========================================================================
# Panels
# ==========================================================================

def player_card(slot, name, race, won, stats):
    info = RACES[race]
    mark = "勝" if won else "敗"
    mark_col = P_RED if won else P_MUTED
    inner = f"""
        <div style="display: flex; align-items: center; gap: 12px">
          {medallion(P, slot, race, 74, "selected" if won else "dim")}
          <div style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px">
            <span style="font-family: {MONO}; font-size: 11px; font-weight: 700; color: {info['ink'] if won else P_MUTED}">{slot.upper()} · {info['han']} {info['ko']}</span>
            <span style="font-family: {DISPLAY}; font-weight: 800; font-size: 21px; color: {P_INK}">{name}</span>
            <span style="display: flex; gap: 10px; font-family: {MONO}; font-size: 11px; color: {P_MUTED}; white-space: nowrap">{stats}</span>
          </div>
          <span style="font-family: {DISPLAY}; font-weight: 800; font-size: 38px; color: {mark_col}; {"text-shadow: 0 1px 0 #F6E8C8" if won else ""}">{mark}</span>
        </div>"""
    return ornate(P, inner, pad="8px 14px 8px 10px", gap=0, extra=f"width: 320px; {'' if won else 'filter: saturate(0.6) brightness(0.86)'}")


def banner():
    p1 = player_card("1p", "commander", "r1", True, "<span>병력 58</span><span>건물 14</span><span>APM 96</span>")
    p2 = player_card("2p", "rival", "r2", False, "<span>병력 0</span><span>건물 0</span><span>APM 71</span>")
    return f"""
    <section style="position: relative; height: 250px; flex: none; display: flex; align-items: center; gap: 26px; padding: 0 16px">
      <div style="position: relative; filter: drop-shadow(0 12px 22px rgba(200,67,43,0.35)) drop-shadow(0 6px 10px rgba(0,0,0,0.6))">{seal(P + "-win", "승", 168, rotate=-7, font_scale=0.6)}</div>
      <div style="flex: 1; display: flex; flex-direction: column; gap: 10px; min-width: 0">
        <span style="font-family: {MONO}; font-size: 12px; letter-spacing: 0.14em; color: #E6D8B8; text-shadow: 0 1px 2px #000">MATCH END · T 012630 · 04:12.6 · DUEL-01 · 한강 나루</span>
        {gold_text("승리", 96, ls="0.1em")}
        <div style="display: flex; align-items: center; gap: 10px; margin-top: 2px">
          <span style="font-size: 16px; font-weight: 500; color: #F3E6C8; text-shadow: 0 1px 3px #000">도깨비 소굴과 남은 병력을 모두 제거했습니다</span>
          {chip("elimination", GOLD, "rgba(20,10,4,0.55)", "#B48A3E", mono=True)}
        </div>
      </div>
      <div style="display: flex; flex-direction: column; gap: 12px; align-self: flex-end; margin-bottom: 4px">
        {p1}
        {p2}
      </div>
    </section>"""


STATS = [
    ("채집한 귀화석", 4860, 3920, "#2A8A78"),
    ("채집한 산기", 720, 480, "#7A5AB8"),
    ("생산한 유닛", 31, 24, None),
    ("잃은 유닛", 9, 24, None),
    ("지은 건물", 14, 11, None),
    ("파괴한 건물", 11, 3, None),
    ("분당 명령 (APM)", 96, 71, None),
]


def stats_panel():
    rows = []
    for label, a, b, d in STATS:
        m = max(a, b)
        wa, wb = a / m * 100, b / m * 100
        lead_a, lead_b = (a > b), (b > a)
        if label == "잃은 유닛":
            lead_a, lead_b = lead_b, lead_a
        dot_html = dot(d, 8) if d else ""
        rows.append(f"""
          <div style="display: grid; grid-template-columns: 1fr 66px 160px 66px 1fr; align-items: center; height: 26px; border-bottom: 1px solid rgba(110,78,38,0.35)">
            <div style="display: flex; justify-content: flex-end"><div style="height: 11px; width: {wa:.0f}%; border-radius: 1px; background: linear-gradient(180deg, #F0845E, {C1} 60%, #7A1E10); box-shadow: inset 0 1px 0 rgba(255,220,200,0.5), 0 1px 0 rgba(0,0,0,0.25)"></div></div>
            <span style="text-align: right; font-family: {MONO}; font-size: 14px; font-weight: {700 if lead_a else 500}; color: {P_INK if lead_a else P_MUTED}">{a:,}</span>
            <span style="display: flex; align-items: center; justify-content: center; gap: 7px; font-size: 12.5px; font-weight: 600; color: {P_INK}">{dot_html}{label}</span>
            <span style="font-family: {MONO}; font-size: 14px; font-weight: {700 if lead_b else 500}; color: {P_INK if lead_b else P_MUTED}">{b:,}</span>
            <div style="display: flex"><div style="height: 11px; width: {wb:.0f}%; border-radius: 1px; background: linear-gradient(180deg, #7AB8E0, {C2} 60%, #12406A); box-shadow: inset 0 1px 0 rgba(220,240,255,0.5), 0 1px 0 rgba(0,0,0,0.25)"></div></div>
          </div>""")
    legend = (f'<span style="display: flex; gap: 16px; font-size: 12px; font-weight: 600; color: {P_INK}">'
              f'<span style="display: flex; align-items: center; gap: 6px"><span style="width: 10px; height: 10px; background: {C1}"></span>1P commander</span>'
              f'<span style="display: flex; align-items: center; gap: 6px"><span style="width: 10px; height: 10px; background: {C2}"></span>2P rival</span></span>')
    inner = panel_title("전투 요약", legend) + f'<div style="display: flex; flex-direction: column">{"".join(rows)}</div>'
    return ornate(P, inner, pad="12px 18px 10px", gap=8)


S1 = [4, 6, 8, 10, 12, 14, 17, 20, 22, 25, 27, 24, 28, 32, 35, 37, 40, 38, 42, 45, 48, 51, 54, 56, 58, 58]
S2 = [4, 6, 8, 11, 13, 16, 19, 22, 25, 27, 30, 26, 21, 22, 24, 23, 20, 17, 14, 12, 9, 6, 4, 2, 1, 0]
EVENTS = [(110, "첫 교전"), (150, "화차 일제 사격"), (220, "도깨비 소굴 함락")]


def timeline_panel():
    w, h = 884, 150
    l, r, t, b = 34, 104, 14, 24
    pw, ph = w - l - r, h - t - b
    tmax, ymax = 252.6, 60

    def X(s):
        return l + s / tmax * pw

    def Y(v):
        return t + ph - v / ymax * ph

    def series(vals):
        return [(X(min(i * 10, tmax)), Y(v)) for i, v in enumerate(vals)]

    def line(pts):
        return "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in pts)

    def area(pts):
        return line(pts) + f" L{pts[-1][0]:.1f} {Y(0):.1f} L{pts[0][0]:.1f} {Y(0):.1f} Z"

    p1, p2 = series(S1), series(S2)
    grid = "".join(
        f'<line x1="{l}" x2="{l + pw}" y1="{Y(v):.1f}" y2="{Y(v):.1f}" stroke="{P_RULE}" stroke-opacity="{0.9 if v == 0 else 0.55}" stroke-width="1" stroke-dasharray="{"" if v == 0 else "2 4"}"></line>'
        f'<text x="{l - 8}" y="{Y(v) + 3.5:.1f}" text-anchor="end" font-family="IBM Plex Mono" font-size="10.5" fill="{P_MUTED}">{v}</text>'
        for v in (0, 20, 40, 60))
    xt = "".join(
        f'<text x="{X(s):.1f}" y="{h - 6}" text-anchor="middle" font-family="IBM Plex Mono" font-size="10.5" fill="{P_MUTED}">{s // 60}:{s % 60:02d}</text>'
        for s in (0, 60, 120, 180, 240))
    ev = "".join(
        f'<line x1="{X(s):.1f}" x2="{X(s):.1f}" y1="{t}" y2="{Y(0):.1f}" stroke="{P_OCHRE}" stroke-opacity="0.75" stroke-dasharray="3 3"></line>'
        f'<path d="M{X(s):.1f} {t - 2} l4.5 4.5 l-4.5 4.5 l-4.5 -4.5 Z" fill="{P_OCHRE}"></path>'
        f'<text x="{X(s) + (8 if i < len(EVENTS) - 1 else -8):.1f}" y="{t + 10}" text-anchor="{"start" if i < len(EVENTS) - 1 else "end"}" font-family="IBM Plex Sans KR" font-size="11.5" font-weight="600" fill="{P_INK}">{name}</text>'
        for i, (s, name) in enumerate(EVENTS))
    e1, e2 = p1[-1], p2[-1]
    svg = f"""
        <svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="두 플레이어의 병력 보급 사용량 추이">
          <defs>
            <linearGradient id="{P}-a1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{C1}" stop-opacity="0.32"></stop><stop offset="1" stop-color="{C1}" stop-opacity="0"></stop></linearGradient>
            <linearGradient id="{P}-a2" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{C2}" stop-opacity="0.26"></stop><stop offset="1" stop-color="{C2}" stop-opacity="0"></stop></linearGradient>
          </defs>
          {grid}{xt}
          <path d="{area(p2)}" fill="url(#{P}-a2)"></path>
          <path d="{area(p1)}" fill="url(#{P}-a1)"></path>
          {ev}
          <path d="{line(p2)}" fill="none" stroke="{C2}" stroke-width="2.2" stroke-linejoin="round"></path>
          <path d="{line(p1)}" fill="none" stroke="#FFF6E0" stroke-width="5" stroke-opacity="0.6" stroke-linejoin="round"></path>
          <path d="{line(p1)}" fill="none" stroke="{C1}" stroke-width="2.6" stroke-linejoin="round"></path>
          <circle cx="{e1[0]:.1f}" cy="{e1[1]:.1f}" r="4.4" fill="{C1}" stroke="#FFF6E0" stroke-width="2"></circle>
          <circle cx="{e2[0]:.1f}" cy="{e2[1]:.1f}" r="4.4" fill="{C2}" stroke="#FFF6E0" stroke-width="2"></circle>
          <text x="{e1[0] + 10:.1f}" y="{e1[1] + 4:.1f}" font-family="IBM Plex Sans KR" font-size="12" font-weight="700" fill="{RACES['r1']['ink']}">commander 58</text>
          <text x="{e2[0] + 10:.1f}" y="{e2[1] - 2:.1f}" font-family="IBM Plex Sans KR" font-size="12" font-weight="700" fill="{RACES['r2']['ink']}">rival 0</text>
        </svg>"""
    inner = panel_title("병력 추이", f'<span style="font-family: {MONO}; font-size: 11px; color: {P_MUTED}">보급 사용량 · 10초 간격</span>') + svg
    return ornate(P, inner, pad="12px 18px 8px", gap=6, extra="flex: 1; min-height: 0")


def keyframe_grid():
    cells = []
    for i in range(84):
        hov = i == 57
        op = 0.6 + 0.4 * ((i * 7) % 5) / 4
        ring = "outline: 2px solid #FFF2C8; outline-offset: 1px;" if hov else ""
        cells.append(f'<span style="height: 9px; border-radius: 1px; background: linear-gradient(180deg, #CFF0D8, #5AAA78); opacity: {1 if hov else op:.2f}; '
                     f'box-shadow: 0 0 4px rgba(140,199,154,0.5); {ring}"></span>')
    tip = (f'<div style="position: absolute; left: 66px; top: 22px; z-index: 2; padding: 6px 9px; border-radius: 2px; background: #F3E9D0; '
           f'box-shadow: 0 0 0 1px #6E4E26, 0 8px 18px rgba(0,0,0,0.55); font-family: {MONO}; font-size: 11px; color: {P_INK}; white-space: nowrap">'
           f'T 008,550 · 0x41C9…E07A <span style="color: {P_GREEN}; font-weight: 700">일치</span></div>')
    return (f'<div style="position: relative">{tip}<div style="display: grid; grid-template-columns: repeat(14, minmax(0, 1fr)); gap: 3px">'
            f'{"".join(cells)}</div></div>')


def replay_panel():
    meta = "".join(
        f'<span style="color: {P_MUTED}">{k}</span><span style="font-family: {MONO}; font-weight: 600; color: {c}; text-align: right">{v}</span>'
        for k, v, c in (("파일", "duel-01.replay.jsonl", P_INK), ("시드", "1234", P_INK), ("명령", "1,482건", P_INK),
                        ("기록 해시", "0x7F3A91C2E0B4", P_INK), ("재실행 해시", "0x7F3A91C2E0B4", P_GREEN)))
    inner = f"""
        {panel_title("리플레이 검증", stamp(dot(P_GREEN) + "결정론 일치", P_GREEN))}
        <div style="display: flex; gap: 14px; align-items: center">
          <div style="flex: 1; display: grid; grid-template-columns: auto 1fr; gap: 4px 14px; font-size: 12.5px">{meta}</div>
          {seal(P + "-ok", "일치", 74, rotate=8, font_scale=0.34, sub="VERIFIED")}
        </div>
        <div style="display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border-radius: 2px; background: linear-gradient(180deg, #160C06, #2A1A10);
             box-shadow: inset 0 2px 5px rgba(0,0,0,0.7), 0 1px 0 rgba(255,248,226,0.7), 0 0 0 1px #4A2E14">
          <div style="display: flex; justify-content: space-between; font-size: 12.5px"><span style="color: #D9C69A">키프레임 해시</span><span style="font-family: {MONO}; color: #A8E0B8; font-weight: 600">252 / 252</span></div>
          {keyframe_grid()}
          <span style="font-size: 11.5px; color: #C9B48A">50틱마다 저장한 해시와 재실행 결과 비교 · 칸 하나 = 키프레임 3개</span>
        </div>
        <div style="display: flex; flex-direction: column; gap: 6px">
          <span style="font-size: 12px; font-weight: 600; color: {P_MUTED}">CLI로 다시 확인</span>
          <div style="display: flex; align-items: center; gap: 8px">
            {lacquer_slot('<span style="color: #F2D58A">$</span>&nbsp;tools replay verify-ndjson duel-01.replay.jsonl', h=34).replace('font-size: 14px', 'font-size: 11.5px; flex: 1; min-width: 0; white-space: nowrap; overflow: hidden')}
            {btn("wood", "복사", None, h=30, size=12, extra="padding: 0 12px")}
          </div>
        </div>
        {btn("wood", "리플레이 파일 내보내기", None, h=34, size=13, extra="margin-top: auto; flex: none")}"""
    return ornate(P, inner, pad="12px 16px 12px", gap=9, extra="flex: 1; min-height: 0")


def actions():
    return (f'<div style="display: flex; gap: 12px; padding: 2px 4px 6px">'
            f'{btn("red", "리플레이 보기", "R", h=48, size=17, extra="flex: 1.3; padding: 0 10px", disp=True)}'
            f'{btn("wood", "같은 시드로", None, h=48, size=13, extra="flex: 1; padding: 0 10px")}'
            f'{btn("wood", "메인으로", "Esc", h=48, size=13, extra="flex: 1.1; padding: 0 10px")}</div>')


def topbar():
    crumbs = (f'<span style="display: flex; align-items: center; gap: 10px; font-size: 13px; color: #C9BC9E">'
              f'<span>메인</span><span style="color: #8A7A5A">›</span><span>duel-01</span><span style="color: #8A7A5A">›</span>'
              f'<span style="color: {INK}; font-weight: 600">매치 결과</span></span>')
    left = f'{wordmark(P, compact=True)}<span style="width: 1px; height: 30px; background: #6A4A2E"></span>{crumbs}'
    right = (chip("sim dev · protocol v1", "#D9C69A", "rgba(0,0,0,0.25)", "#6A4A2E", mono=True)
             + chip("seed 1234 · 12,630 ticks", "#D9C69A", "rgba(0,0,0,0.25)", "#6A4A2E", mono=True))
    return title_bar(P, left, right)


def build():
    body = f"""<div style="position: relative; width: {W}px; height: {H}px; box-sizing: border-box; background: {GROUND}; color: {INK}; font-family: {BODY}; display: flex; flex-direction: column; overflow: hidden">
  {shared_defs(P)}
  {battlefield()}
  {topbar()}
  <main style="position: relative; z-index: 1; flex: 1; min-height: 0; box-sizing: border-box; padding: 8px 22px 16px; display: flex; flex-direction: column; gap: 12px">
    {banner()}
    <div style="flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(0, 1fr) 440px; gap: 16px">
      <div style="display: flex; flex-direction: column; gap: 12px; min-height: 0">
        {stats_panel()}
        {timeline_panel()}
      </div>
      <div style="display: flex; flex-direction: column; gap: 12px; min-height: 0">
        {replay_panel()}
        {actions()}
      </div>
    </div>
  </main>
</div>"""
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(page("매치 결과와 리플레이 검증", W, H, body))


if __name__ == "__main__":
    build()
    print(os.path.abspath(OUT))
