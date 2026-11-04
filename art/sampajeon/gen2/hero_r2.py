"""Generate HeroR2.dc.html — hero codex for R2 요물 무리 (삼재 SAMJAE), per SPEC_HERO.md and SPEC_YOMUL.md.

Heroes: 비형랑 (summoner), 두두리 (smith-god brute), 구미호 (fox trickster) — from 『삼국유사』 and folk belief.
Figures are built with figure_kit through race_r2's R2Kit and painted helpers (imported, not edited).
New ids are prefixed h2-; kit/helper ids are renamed r2k- -> h2k- and r2- -> h2r- on output.
Usage: python3 hero_r2.py [<project dir>]
"""
import math
import random
import re
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJ = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "design2" / "project"
OUT = PROJ / "HeroR2.dc.html"

# race_r2 generates its sheet at import time; point that output at a throwaway dir.
_argv = sys.argv
sys.argv = [_argv[0], tempfile.mkdtemp(prefix="r2imp")]
sys.path.insert(0, str(HERE))
import race_r2 as R  # noqa: E402
from figure_kit import MATS, Mat, mix  # noqa: E402
sys.argv = _argv

K = R.R2Kit("r2k")
R.K = K  # helpers in race_r2 resolve the module-global kit at call time
f, P, path, circ, ell, poly, el = R.f, R.P, R.path, R.circ, R.ell, R.poly, R.el
BLUE, BLUE_L, OUTL = R.BLUE, R.BLUE_L, R.OUTL

# ---------------------------------------------------------------- materials
SPIRIT = Mat("#C9A894", "#F6E0CC", "#3E2A3E", "#6FB4E8", spec="#FFF4EC", spec_op=0.4)          # 비형랑: pale half-spirit skin
ROBE_W = Mat("#C4CED6", "#F6FAFF", "#34405A", "#5FA8E0", spec_op=0.25, gloss=0.2)             # inner white silk
ROBE_B = Mat("#2C3C7E", "#6680CC", "#090D2A", "#3FB0E8", spec="#DDE8FF", spec_op=0.35, gloss=0.45)  # outer indigo silk
GOLD = Mat("#C8962E", "#FFE08A", "#3E2406", "#8A6A40", spec="#FFFDF0", spec_op=0.9, gloss=1.0)
JADE = Mat("#2E9474", "#A8F0D4", "#06261C", "#4FB3E8", spec="#FFFFFF", spec_op=0.85, gloss=0.9)
HIDE = Mat("#30409A", "#7C8EE0", "#080B2C", "#FF9A4A", spec="#E4ECFF", spec_op=0.45)        # 두두리: forge-lit bounce
APRON = Mat("#6A3E22", "#B07A4A", "#1A0A04", "#FF8A3A", spec_op=0.45, gloss=0.55)
FUR_W = Mat("#E6D8BC", "#FFF8E8", "#5A4A3A", "#8AC8F0", spec_op=0.25, gloss=0.2)
TAILFUR = Mat("#C8662A", "#F6AE62", "#3A1204", "#4FB3E8", spec="#FFE8C8", spec_op=0.3, gloss=0.25)  # 구미호: russet fur
TAILFIRE = Mat("#3E9EE0", "#BFEBFF", "#123C78", "#7FD8FF", spec="#FFFFFF", spec_op=0.6, gloss=0.3)   # the same tails burning
GUMIHO_SKIN = Mat("#D2AE98", "#FAE8D8", "#4A2C3A", "#7FB8E8", spec="#FFF4EC", spec_op=0.35)        # pale, moonlit
IVORY = Mat("#D8D2C4", "#FBF6EA", "#6E6A70", "#8FB4DC", spec_op=0.15, gloss=0.12)
CHIMA = Mat("#5A1830", "#B04468", "#12040C", "#4FB3E8", spec="#FFD8E4", spec_op=0.3, gloss=0.5)     # deep plum silk
CRIMSON = Mat("#9A2436", "#E0586A", "#2A060E", "#6A7AA0", spec_op=0.3, gloss=0.45)
JANGOT = Mat("#1E3A40", "#4C7E86", "#060E12", "#4FB3E8", spec_op=0.25, gloss=0.35)                  # dark outer 장옷
HAIR = MATS["hair"]
STONE_N = Mat("#4E5568", "#8E98B0", "#0E111C", "#3A5A88", spec_op=0.2, gloss=0.2)
RED = MATS["red_cloth"]
IRON = MATS["iron"]
LEATHER = MATS["leather_dark"]

HDEFS = (
    '<filter id="h2-ghost" x="-30%" y="-30%" width="160%" height="160%">'
    '<feColorMatrix type="matrix" values="0 0 0 0 0.3  0 0 0 0 0.62  0 0 0 0 1  0 0 0 0.85 0"></feColorMatrix>'
    '<feGaussianBlur stdDeviation="1.4"></feGaussianBlur></filter>'
    '<filter id="h2-glow" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur in="SourceGraphic" stdDeviation="2.4" result="b"></feGaussianBlur>'
    '<feMerge><feMergeNode in="b"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>'
    '<filter id="h2-blur3" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="3"></feGaussianBlur></filter>'
    '<filter id="h2-blur8" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="8"></feGaussianBlur></filter>'
    + R.rg("h2-coin", [(0, "#FFF6C8"), (0.35, "#F2C850"), (0.75, "#A8701A"), (1, "#5A3608")], cx=0.35, cy=0.3, r=0.8)
    + R.rg("h2-silver", [(0, "#FFFFFF"), (0.4, "#D8DEE8"), (0.8, "#7A8494"), (1, "#3A4250")], cx=0.35, cy=0.3, r=0.8)
    + R.rg("h2-forge", [(0, "#FFF2C0", 0.95), (0.35, "#FF9A3A", 0.55), (1, "#C83A10", 0)])
    + R.rg("h2-bgB", [(0, "#2C4A6E"), (0.55, "#121C2C"), (1, "#07090E")], cx=0.5, cy=0.35, r=0.75)
    + R.rg("h2-bgD", [(0, "#5A3420"), (0.5, "#1E1612"), (1, "#08070A")], cx=0.35, cy=0.65, r=0.8)
    + R.rg("h2-bgG", [(0, "#24305A"), (0.6, "#10142A"), (1, "#06070E")], cx=0.5, cy=0.45, r=0.75)
    + R.rg("h2-moonface", [(0, "#F2F6FF"), (0.7, "#C4D4EE"), (1, "#8EA6CC")], cx=0.42, cy=0.42, r=0.62)
    + R.rg("h2-foxorb", [(0, "#FFFFFF"), (0.35, "#CFF2FF"), (0.75, BLUE), (1, "#123C78")], cx=0.38, cy=0.34, r=0.72)
    + R.lg("h2-tail", [(0, "#C8662A"), (0.35, "#F2A050"), (0.6, BLUE), (0.85, BLUE_L), (1, "#FFFFFF")], 0, 0, 1, 0)
    + R.lg("h2-smear", [(0, "#FFFFFF", 0), (0.6, "#D8F0FF", 0.35), (1, "#FFFFFF", 0.75)], 0, 0, 1, 0)
    + R.lg("h2-moon", [(0, "#BFE6FF", 0.5), (1, "#BFE6FF", 0)], 0, 0, 0, 1)
    + R.lg("h2-icon", [(0, "#24324A"), (1, "#0A0E16")], 0, 0, 0, 1)
)


# ---------------------------------------------------------------- shared painted helpers
def drape(pts, m, normal=(-1.0, -0.2), creases=(), seed=1):
    """A cloth panel: smooth outline, cylinder shading across `normal`, clipped crease + ridge pairs."""
    d = K._smooth(pts)
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    c = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    w = max(max(xs) - min(xs), max(ys) - min(ys)) * 0.5
    gid = K._cyl_grad(c, normal, w, m)
    out = [path(d, fill=f"url(#{gid})", stroke=OUTL, stroke_width="0.9", stroke_opacity="0.75", stroke_linejoin="round")]
    inner = []
    rr = random.Random(seed)
    for (a, b) in creases:
        mid = ((a[0] + b[0]) / 2 + rr.uniform(-2, 2), (a[1] + b[1]) / 2 + rr.uniform(-2, 2))
        inner.append(path(f"M{P(a)} Q{P(mid)} {P(b)}", fill="none", stroke=m.core, stroke_width="2.2", stroke_opacity="0.55",
                          stroke_linecap="round", filter="url(#r2k-soft)"))
        inner.append(path(f"M{f(a[0] - 1.6)} {f(a[1] + 1)} Q{f(mid[0] - 1.8)} {f(mid[1])} {f(b[0] - 1.6)} {f(b[1])}", fill="none",
                          stroke=m.light, stroke_width="0.9", stroke_opacity="0.6", stroke_linecap="round"))
    # bottom falloff
    vid = K.id("dv")
    K._defs.append(f'<linearGradient id="{vid}" gradientUnits="userSpaceOnUse" x1="0" y1="{f(min(ys))}" x2="0" y2="{f(max(ys))}">'
                   f'<stop offset="0" stop-color="#FFF4DC" stop-opacity="0.12"></stop><stop offset="0.4" stop-color="#000" stop-opacity="0"></stop>'
                   f'<stop offset="1" stop-color="#05040A" stop-opacity="0.45"></stop></linearGradient>')
    inner.append(f'<rect x="{f(min(xs) - 4)}" y="{f(min(ys) - 4)}" width="{f(max(xs) - min(xs) + 8)}" height="{f(max(ys) - min(ys) + 8)}" fill="url(#{vid})"></rect>')
    out.append(K.clip_overlay(d, "".join(inner)))
    return "".join(out), d


def fan_creases(top_l, top_r, bot_l, bot_r, n, seed=1, jitter=3.0):
    rr = random.Random(seed)
    res = []
    for i in range(n):
        t = (i + 0.5) / n
        a = (top_l[0] + (top_r[0] - top_l[0]) * t + rr.uniform(-1, 1), top_l[1] + (top_r[1] - top_l[1]) * t + 2)
        b = (bot_l[0] + (bot_r[0] - bot_l[0]) * t + rr.uniform(-jitter, jitter), bot_l[1] + (bot_r[1] - bot_l[1]) * t - rr.uniform(2, 8))
        res.append((a, b))
    return res


def trim(pts, col="url(#r2-brass)", w=1.6, hi="#FFF2C8"):
    d = "M" + " L".join(P(p) for p in pts)
    return (path(d, fill="none", stroke=OUTL, stroke_width=f(w + 1.2), stroke_linecap="round", stroke_linejoin="round", opacity="0.8")
            + path(d, fill="none", stroke=col, stroke_width=f(w), stroke_linecap="round", stroke_linejoin="round")
            + path(d, fill="none", stroke=hi, stroke_width="0.5", opacity="0.6", stroke_linecap="round"))


def gold_ring_text(x, y, text, size, fill="url(#r2-brass)", anchor="middle", rot=0):
    st = f"font-family: 'Nanum Myeongjo', serif; font-weight: 800; font-size: {size}px"
    tr = f' transform="rotate({rot} {f(x)} {f(y)})"' if rot else ""
    return (f'<text x="{f(x)}" y="{f(y)}" text-anchor="{anchor}" style="{st}" fill="none" stroke="#120804" stroke-width="{f(size * 0.22)}" '
            f'stroke-linejoin="round"{tr}>{text}</text>'
            f'<text x="{f(x)}" y="{f(y)}" text-anchor="{anchor}" style="{st}" fill="{fill}"{tr}>{text}</text>')


def wisp(x, y, s, seed):
    """Tiny horned dokkaebi-fire."""
    o = [R.flame(x, y, s, halo=True, seed=seed)]
    for sx in (-1, 1):
        o.append(path(f"M{f(x + sx * 5 * s)} {f(y - 10 * s)} q{f(sx * 2 * s)} {f(-6 * s)} {f(sx * 5 * s)} {f(-9 * s)} q{f(-sx * 1 * s)} {f(5 * s)} {f(-sx * 2.4 * s)} {f(9 * s)} Z",
                      fill="url(#r2-horn)", stroke="#3A2610", stroke_width="0.5"))
    for sx in (-1, 1):
        o.append(ell(x + sx * 3.2 * s, y - 2 * s, 1.5 * s, 2 * s, fill="#0A1A30"))
        o.append(circ(x + sx * 3.2 * s - 0.4 * s, y - 2.6 * s, 0.6 * s, fill="#FFFFFF"))
    return "".join(o)


def fur_tufts(a, b, w, n, col, side=1, seed=1, ln=3.0):
    """Spiky fur fringe along one side of a segment."""
    rr = random.Random(seed)
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy) or 1
    ux, uy = dx / L, dy / L
    nx, ny = -uy * side, ux * side
    o = []
    for i in range(n):
        t = (i + 0.5) / n
        px, py = a[0] + dx * t + nx * w, a[1] + dy * t + ny * w
        l = ln * rr.uniform(0.6, 1.3)
        tip = (px + nx * l + ux * l * 0.8, py + ny * l + uy * l * 0.8)
        o.append(path(f"M{f(px - ux * 1.6)} {f(py - uy * 1.6)} L{P(tip)} L{f(px + ux * 1.6)} {f(py + uy * 1.6)} Z", fill=col,
                      stroke=OUTL, stroke_width="0.4", stroke_opacity="0.7"))
    return "".join(o)


# ================================================================= 비형랑
def bihyeong():
    o = []
    # --- backdrop: moonlit sky, the ghost bridge 귀교 materialising, mist
    o.append(el("rect", x="-12", y="-12", width="224", height="240", fill="url(#h2-bgB)"))
    o.append(circ(150, 34, 18, fill="#DDF2FF", opacity="0.16", filter="url(#h2-blur8)"))
    o.append(circ(150, 34, 9, fill="#EAF6FF", opacity="0.5"))
    bridge = []
    for k in range(17):  # arch voussoirs
        a0 = math.pi + k * math.pi / 17
        a1 = math.pi + (k + 1) * math.pi / 17
        rI, rO = 44, 54
        cx, cy = 100, 150
        q = [(cx + math.cos(a0) * rO, cy + math.sin(a0) * rO * 0.62), (cx + math.cos(a1) * rO, cy + math.sin(a1) * rO * 0.62),
             (cx + math.cos(a1) * rI, cy + math.sin(a1) * rI * 0.62), (cx + math.cos(a0) * rI, cy + math.sin(a0) * rI * 0.62)]
        q = [(x, y + 36) for x, y in q]
        bridge.append(poly(q, fill="#2A4A6A" if k % 2 else "#33587E", stroke="#9AD8FF", stroke_width="0.6", stroke_opacity="0.7"))
    bridge.append(el("rect", x="18", y="146", width="164", height="7", fill="#2E5072", stroke="#9AD8FF", stroke_width="0.6"))
    for k in range(12):
        bridge.append(el("rect", x=f(20 + k * 13.5), y="140", width="3", height="7", fill="#3A6488", stroke="#9AD8FF", stroke_width="0.4"))
    o.append(f'<g opacity="0.55">{"".join(bridge)}</g>')
    o.append(path("M18 153 L182 153", stroke=BLUE_L, stroke_width="1.4", opacity="0.6", filter="url(#r2-blur2)"))
    rr = random.Random(5)
    for _ in range(22):  # stones still flying into place
        x, y = rr.uniform(14, 186), rr.uniform(70, 104)
        s = rr.uniform(2, 4.5)
        o.append(poly([(x, y), (x + s, y - s * 0.3), (x + s * 1.2, y + s * 0.6), (x + s * 0.1, y + s * 0.8)], fill="#3A6488", stroke=BLUE_L,
                      stroke_width="0.4", opacity=f"{rr.uniform(0.35, 0.8):.2f}"))
    for k in range(5):
        o.append(ell(30 + k * 36, 166 + (k % 2) * 6, 40, 9, fill="#8AB8E0", opacity="0.12", filter="url(#h2-blur8)"))
    o.append(R.diorama(100, 194, 86, 21, 10, seed=71, density=0.9, dark=True))
    o.append(ell(100, 192, 60, 13, fill="url(#r2-pool)", opacity="0.8"))
    o.append(K.cast_shadow(102, 192, 30, 7, 0.6))
    # ghost echoes of the body (rendered from the same group, tinted)
    o.append('<use href="#h2-bihfig" transform="translate(-17 -5)" filter="url(#h2-ghost)" opacity="0.32"></use>')
    o.append('<use href="#h2-bihfig" transform="translate(16 -9)" filter="url(#h2-ghost)" opacity="0.2"></use>')
    o.append(f'<g id="h2-bihfig"><g transform="translate(100 192) scale(1.04) translate(-100 -192)">{bihyeong_body()}</g></g>')
    # summoning fire above the raised hand + orbiting ring
    o.append(ell(58, 18, 22, 7, fill="none", stroke=BLUE_L, stroke_width="1.2", opacity="0.75", filter="url(#r2-glow)",
                 transform="rotate(-14 58 18)"))
    o.append(ell(58, 20, 30, 10, fill="none", stroke=BLUE, stroke_width="0.8", stroke_dasharray="3 4", opacity="0.6", transform="rotate(-14 58 20)"))
    o.append(R.flame(58, 12, 0.85, seed=81))
    o.append(R.sparks(58, 14, 30, 16, 26, 82))
    for (x, y, s, sd) in ((84, 6, 0.32, 83), (30, 30, 0.3, 84), (34, 4, 0.26, 85)):
        o.append(wisp(x, y, s, sd))
    # commanded wisps streaming from the open right hand
    for (x, y, s, sd) in ((176, 70, 0.42, 86), (186, 98, 0.36, 87), (170, 46, 0.32, 88), (190, 128, 0.3, 89)):
        o.append(path(f"M162 80 Q{f((162 + x) / 2)} {f(y + 8)} {f(x)} {f(y)}", fill="none", stroke=BLUE_L, stroke_width="0.8",
                      stroke_dasharray="1.5 3", opacity="0.6"))
        o.append(wisp(x, y, s, sd))
    return "".join(o)


def bihyeong_body():
    o = []
    m = SPIRIT
    # long hair flowing behind to the right
    rr = random.Random(9)
    for k in range(16):
        y0 = 40 + k * 1.5
        ln = rr.uniform(26, 46)
        o.append(path(f"M{f(104 + k * 0.6)} {f(y0 + 3)} q{f(4 + rr.uniform(0, 6))} {f(ln * 0.5)} {f(12 + rr.uniform(0, 12))} {f(ln)}",
                      fill="none", stroke="#141018" if k % 3 else "#3A3448", stroke_width=f(rr.uniform(0.7, 1.4)), stroke_linecap="round"))
    # boots under the hem
    o.append(K.foot((88, 186), 6.2, LEATHER, -1))
    o.append(K.foot((112, 187), 6.2, LEATHER, 1, back=True))
    # inner white skirt (blown right)
    sk, skd = drape([(80, 108), (120, 108), (130, 140), (140, 172), (146, 184), (128, 189), (106, 185), (86, 188), (66, 186), (70, 160), (76, 132)],
                    ROBE_W, creases=fan_creases((82, 112), (118, 112), (70, 186), (144, 184), 9, seed=3), seed=3)
    o.append(sk)
    o.append(K.clip_overlay(skd, path("M60 184 L150 182 L150 194 L60 194 Z", fill="#9AB8D8", opacity="0.35")))
    # far (screen-right) extended arm: shoulder -> elbow -> wrist, hidden by the wide sleeve; only hand shows
    sh2, el2, wr2 = (120, 76), (140, 85), (158, 80)
    o.append(K.arm(sh2, el2, wr2, m, w=5.0, hand=None))
    o.append(K.hand(wr2, (1.0, -0.25), 4.0, m, kind="open"))
    o.append(circ(166, 78, 10, fill="url(#r2-halo)", opacity="0.9"))
    # outer indigo robe: bodice + two skirt panels
    lp, _ = drape([(80, 104), (98, 108), (96, 150), (84, 160), (68, 156), (72, 128)], ROBE_B,
                  creases=fan_creases((80, 108), (96, 110), (70, 154), (94, 152), 4, seed=4), seed=4)
    rp, _ = drape([(102, 108), (120, 104), (130, 124), (146, 148), (150, 158), (132, 160), (110, 154)], ROBE_B, normal=(-0.8, -0.6),
                  creases=fan_creases((104, 110), (120, 108), (112, 152), (148, 156), 5, seed=5), seed=5)
    o.append(lp)
    o.append(rp)
    o.append(trim([(68, 156), (84, 160), (96, 150)], w=1.8))
    o.append(trim([(110, 154), (132, 160), (150, 158)], w=1.8))
    bod, bodd = drape([(82, 70), (100, 66), (118, 70), (127, 84), (123, 108), (100, 111), (77, 108), (73, 84)], ROBE_B, normal=(-1, -0.15),
                      creases=[((88, 80), (84, 104)), ((112, 82), (118, 104)), ((100, 92), (100, 108))], seed=6)
    o.append(bod)
    o.append(K.ao(100, 104, 20, 4, 0.45))
    # wide right sleeve hanging from the outstretched arm; only the hand emerges
    rs, _ = drape([(114, 70), (128, 76), (142, 80), (156, 74), (160, 82), (156, 96), (148, 114), (134, 120), (122, 112), (118, 92)], ROBE_B,
                  normal=(-0.5, -1), creases=[((130, 80), (130, 116)), ((142, 84), (144, 112)), ((152, 80), (152, 100)), ((122, 80), (124, 104))], seed=13)
    o.append(rs)
    o.append(trim([(156, 74), (160, 82), (156, 96), (148, 114), (134, 120)], w=1.6))
    o.append(K.form((155, 75), (159, 82), 3.4, 3.4, ROBE_W, ao_start=False))
    o.append(K.hand(wr2, (1.0, -0.25), 4.0, m, kind="open"))
    # crossed collar (교임): white lining + gold edge
    o.append(path("M92 67 L108 96 L104 99 L89 70 Z", fill="#E8EEF6", stroke=OUTL, stroke_width="0.6"))
    o.append(path("M108 67 L98 84 L95 82 L105 66 Z", fill="#D4DCE8", stroke=OUTL, stroke_width="0.6"))
    o.append(trim([(92, 67), (108, 96)], w=1.2))
    o.append(trim([(108, 67), (97, 84)], w=1.2))
    # gold belt with plaques and dangling pendants (요패)
    o.append(K.form((78, 107), (122, 107), 3.2, 3.2, GOLD, ao_start=False))
    for k in range(8):
        x = 81 + k * 5.6
        o.append(el("rect", x=f(x - 2), y="104.4", width="4", height="5.2", fill="url(#r2-brass)", stroke=OUTL, stroke_width="0.4"))
        o.append(circ(x, 107, 0.8, fill="#2E9474"))
    for k, (x, ln) in enumerate(((84, 24), (90, 32), (96, 20), (104, 30), (110, 22), (116, 34))):
        o.append(path(f"M{x} 110 l{(k % 2) * 1.5 - 0.7} {ln}", stroke="url(#r2-brass)", stroke_width="1.2"))
        for j in range(3):
            o.append(ell(x + (k % 2) * 0.6, 114 + j * ln / 3.4, 1.4, 0.8, fill="url(#r2-brass)", stroke=OUTL, stroke_width="0.3"))
        o.append(path(f"M{f(x - 1.8)} {f(110 + ln)} a2.4 2.4 0 1 1 2.6 3 q-1 2 -3 2.4 q1.4 -1.6 0.4 -5.4 Z", fill="url(#r2-ore-l)",
                      stroke="#0A3024", stroke_width="0.4"))
    # neck + head
    o.append(K.neck((100, 69), (100, 59), 4.8, m))
    o.append(K.ao(100, 66, 7, 2.4, 0.5))
    hg = []
    # youthful half-spirit: refined heart-shaped face, sharp slanted eyes, faint mysterious smile
    h, hd = K.face(100, 45, 11.5, m, dict(shape="heart", eye="sharp", eye_size=1.05, lid="double", brow="straight", brow_col="#141018",
                                          nose="straight", nose_len=1.05, mouth="smirk", lip=0.35, mouth_col="#8A4048", age=0.0,
                                          turn=-0.12, cheek=0.75, eye_col="#0A2A48", gaze=(0.6, -0.2)))
    hg.append(h)
    # glowing spirit eyes + hair cap
    for sx in (-1, 1):
        hg.append(circ(100 - 1.4 + sx * 4.1, 44.8, 2.2, fill=BLUE, opacity="0.45", filter="url(#r2-blur2)"))
    hg.append(path("M88.6 42 C88 31 96 28.6 100 28.8 C106 28.6 113 31 112 42 C108 36 104 35 100 35.6 C95 35 91 37 88.6 42 Z",
                  fill="#141018", stroke=OUTL, stroke_width="0.7"))
    hg.append(path("M92 34 Q99 30.6 107 33", fill="none", stroke="#5A5470", stroke_width="0.8", opacity="0.8"))
    # Silla-style gold diadem: band + three tree uprights + dangling jade commas and spangles
    hg.append(K.form((88.4, 35.6), (111.6, 35.6), 2.2, 2.2, GOLD, ao_start=False))
    for x0, hgt in ((92, 15), (100, 19), (108, 15)):
        stem = [(x0, 34), (x0, 34 - hgt)]
        hg.append(K.form(stem[0], stem[1], 1.1, 0.9, GOLD, ao_start=False))
        for j in range(3):
            yy = 34 - hgt * (0.32 + j * 0.28)
            hg.append(path(f"M{x0} {f(yy)} l-3.6 -3.4 M{x0} {f(yy)} l3.6 -3.4", stroke="url(#r2-brass)", stroke_width="1.1", stroke_linecap="round"))
        hg.append(circ(x0, 34 - hgt - 1, 1.6, fill="url(#r2-brass)", stroke=OUTL, stroke_width="0.3"))
    for x in (89.5, 94, 98, 102, 106, 110.5):
        hg.append(path(f"M{x} 37 l0 3", stroke="url(#r2-brass)", stroke_width="0.5"))
        hg.append(circ(x, 40.6, 0.9, fill="url(#r2-brass)"))
    for x in (90, 110):
        hg.append(path(f"M{x} 37.5 l0 9", stroke="url(#r2-brass)", stroke_width="0.7"))
        hg.append(path(f"M{f(x - 1.3)} 47 a1.6 1.6 0 1 1 1.8 2 q-0.6 1.4 -2 1.6 q1 -1 0.2 -3.6 Z", fill="url(#r2-ore-l)", stroke="#0A3024", stroke_width="0.3"))
    o.append(f'<g transform="translate(0 3.2)">{"".join(hg)}</g>')
    # near (screen-left) arm raised: bare forearm, wide sleeve fallen to the shoulder
    sh, el_, wr = (80, 76), (69, 55), (63, 32)
    sl, sld = drape([(66, 52), (75, 49), (86, 70), (84, 88), (74, 102), (60, 104), (56, 90), (60, 66)], ROBE_B, normal=(-0.9, -0.4),
                    creases=[((68, 56), (60, 98)), ((72, 56), (70, 100)), ((78, 62), (80, 94))], seed=7)
    # one continuous arm (the fallen sleeve hides the upper arm, the bare forearm rises above it)
    o.append(K.tube([sh, el_, wr], [5.6, 4.4, 3.0], m, bumps=[(0.2, 1.2, 1), (0.7, 1.0, 0)], crease_at=(1,)))
    o.append(sl)
    o.append(trim([(56, 90), (60, 104), (74, 102), (84, 88)], w=1.6))
    o.append(K.ao(70, 54, 6, 3, 0.55))
    o.append(K.form((67.6, 49), (66, 44), 4.6, 4.2, ROBE_W, ao_start=False))  # rolled inner-sleeve cuff
    o.append(K.bands((68, 50), (66, 43), 4.6, 4.2, ROBE_W, n=3))
    o.append(K.hand(wr, (-0.2, -1.0), 3.8, m, kind="open"))
    # cold rim light on the shadow side from the summoned fire
    return "".join(o)


# ================================================================= 두두리
def coin(x, y, r, ang, silver=False):
    sq = abs(math.cos(math.radians(ang)))
    rx = r * max(0.18, sq)
    g = "url(#h2-silver)" if silver else "url(#h2-coin)"
    hole = r * 0.32 * max(0.2, sq)
    return (f'<g transform="rotate({f(ang * 0.4)} {f(x)} {f(y)})">'
            + ell(x, y, rx, r, fill=g, stroke="#3A2206", stroke_width="0.5")
            + ell(x, y, rx * 0.72, r * 0.72, fill="none", stroke="#FFF2B0", stroke_width="0.35", opacity="0.6")
            + el("rect", x=f(x - hole), y=f(y - r * 0.3), width=f(hole * 2), height=f(r * 0.6), fill="#1A0E04")
            + ell(x - rx * 0.35, y - r * 0.4, rx * 0.25, r * 0.16, fill="#FFFFFF", opacity="0.8")
            + "</g>")


def chain(a, b, n, r=2.2):
    o = []
    for i in range(n + 1):
        t = i / n
        x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t + math.sin(t * math.pi) * 6
        ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
        if i % 2:
            o.append(ell(x, y, r * 1.5, r * 0.55, fill="none", stroke=OUTL, stroke_width="2.4", transform=f"rotate({f(ang)} {f(x)} {f(y)})"))
            o.append(ell(x, y, r * 1.5, r * 0.55, fill="none", stroke="url(#r2-iron)", stroke_width="1.4", transform=f"rotate({f(ang)} {f(x)} {f(y)})"))
        else:
            o.append(ell(x, y, r * 1.6, r, fill="none", stroke=OUTL, stroke_width="2.2", transform=f"rotate({f(ang)} {f(x)} {f(y)})"))
            o.append(ell(x, y, r * 1.6, r, fill="none", stroke="url(#r2-iron)", stroke_width="1.3", transform=f"rotate({f(ang)} {f(x)} {f(y)})"))
            o.append(path(f"M{f(x - r)} {f(y - r * 0.8)} q{f(r)} -1 {f(r * 1.8)} 0.2", fill="none", stroke="#FFFFFF", stroke_width="0.5", opacity="0.75"))
    return "".join(o)


def scar(pts, w=1.1):
    d = "M" + " L".join(P(p) for p in pts)
    return (path(d, fill="none", stroke="#FF7A2A", stroke_width=f(w * 2.6), opacity="0.45", filter="url(#r2-blur2)", stroke_linecap="round")
            + path(d, fill="none", stroke="#FFD27A", stroke_width=f(w), stroke_linecap="round", stroke_linejoin="round"))


# ancient smith goblin: huge square jaw, long tusks, wide grin, bushy grey brows, deep age lines, grey mane and beard
DUDURI_LOOK = dict(shape="square", jaw=1.32, cheek=0.9, eye="droopy", eye_size=0.9, brow="thick", brow_col="#A6AAC0", nose="broad",
                   age=0.95, turn=0.05, ears="torn", hair="wild", hair_col="#8A90AC", mouth_g="grin", tusks=2, ridge=1.0,
                   nose_bulb=1.0, glow=0.55, eye_col="#FFB040", beard_g=True)


def duduri():
    m = HIDE
    o = [el("rect", x="-12", y="-12", width="224", height="240", fill="url(#h2-bgD)")]
    # forge glow behind, sparks
    o.append(circ(46, 170, 70, fill="url(#h2-forge)", opacity="0.55"))
    o.append(R.sparks(70, 120, 70, 60, 40, 91, color="#FFC870", rmax=1.1))
    # swing smear arc (club came over from the upper right)
    o.append(path("M182 36 C150 -6 70 -8 22 24 C64 4 140 6 170 44 Z", fill="url(#h2-smear)", opacity="0.8"))
    o.append(path("M178 40 C146 2 74 0 26 26", fill="none", stroke="#FFFFFF", stroke_width="0.8", opacity="0.5"))
    o.append(R.diorama(100, 196, 90, 20, 10, seed=72, density=1.0))
    # anvil with hot ingot
    o.append(K.cast_shadow(40, 192, 20, 5, 0.6))
    o.append(poly([(28, 190), (52, 190), (48, 182), (32, 182)], fill="url(#r2-iron)", stroke=OUTL, stroke_width="0.8"))
    o.append(K.form((36, 182), (44, 182), 4, 4, IRON, ao_start=False))
    o.append(path("M18 168 L60 168 L62 174 L52 176 L46 180 L34 180 L28 176 L22 174 Q14 172 10 168 Z", fill="url(#r2-iron)", stroke=OUTL, stroke_width="0.9"))
    o.append(path("M18 168 L60 168", stroke="#E8EEF6", stroke_width="1.2", opacity="0.8"))
    o.append(el("rect", x="30", y="163", width="16", height="5", rx="1.2", fill="#FFB050", stroke="#8A2A08", stroke_width="0.5", filter="url(#h2-glow)"))
    o.append(el("rect", x="31", y="163.6", width="14", height="1.6", fill="#FFF4C8"))
    o.append(K.cast_shadow(108, 194, 44, 9, 0.6))
    # legs: wide lunge
    o.append(R._dleg((114, 132), (138, 156), (154, 184), m, 12.5, 1, back=True))
    o.append(R._dleg((92, 132), (74, 158), (68, 184), m, 13.5, -1))
    for (a, b) in (((70, 172), (68, 182)), ((150, 172), (153, 182))):  # ankle wraps
        o.append(K.bands(a, b, 7.2, 6.8, MATS["straw"], n=3))
    o.append(K.ao(104, 138, 16, 7, 0.6))
    # torso, twisted into the swing
    t, td = K.torso((100, 74), (106, 132), 36, 26, m, chest=1, abs_=True, belly=6)
    o.append(R.trap((100, 76), 36, m))
    o.append(t)
    o.append(R.rim_stroke(td, 118, 60, 40, 80, color="#FFB070", width=2.2, op=0.45))
    o.append(scar([(84, 92), (90, 98), (88, 106), (95, 112)]))
    o.append(scar([(112, 86), (118, 90), (116, 96)], 0.9))
    # leather apron with rivets, stitching, scorch
    ap, apd = drape([(82, 128), (130, 128), (132, 146), (128, 162), (106, 166), (84, 162), (80, 146)], APRON, normal=(-1, -0.2),
                    creases=fan_creases((84, 132), (128, 132), (86, 160), (126, 160), 6, seed=8), seed=8)
    o.append(ap)
    rr = random.Random(11)
    o.append(K.clip_overlay(apd, "".join(ell(rr.uniform(86, 128), rr.uniform(138, 160), rr.uniform(3, 7), rr.uniform(2, 4), fill="#120804", opacity="0.4",
                                             filter="url(#r2k-soft)") for _ in range(6))))
    o.append(path("M84 162 L106 166 L128 162", fill="none", stroke="#E8C890", stroke_width="0.7", stroke_dasharray="2 1.6", opacity="0.75"))
    for x in (82, 94, 106, 118, 130):
        o.append(K.stud(x, 133, 1.5, GOLD))
    o.append(K.form((76, 128), (134, 128), 3.6, 3.6, LEATHER, ao_start=False))
    o.append(el("rect", x="99", y="124.6", width="10", height="7.4", rx="1", fill="none", stroke="url(#r2-brass)", stroke_width="1.6"))
    # heavy chain across the chest
    o.append(chain((72, 80), (130, 126), 14))
    # head: wild mane, two great horns, beard, red headband
    o.append(R.dhead(98, 52, 18, m, horns=[(-0.5, -0.72, -1.15, -1.75, 0.44, -0.3), (0.5, -0.72, 1.05, -1.85, 0.44, 0.3)],
                     grin=1.15, beard=True, seed=51, brow_angry=0.9, look=DUDURI_LOOK))
    # forge soot smudged across the cheek and brow
    for (sx, sy, rx, ry) in ((88, 56, 4.5, 2.2), (109, 50, 3.6, 1.8), (95, 40, 3, 1.4)):
        o.append(ell(sx, sy, rx, ry, fill="#0A0806", opacity="0.35", filter="url(#r2k-soft)"))
    o.append(path("M80.6 45 Q98 37 115.6 45 L115 49.6 Q98 42 81 49.6 Z", fill="#B83020", stroke=OUTL, stroke_width="0.7"))
    o.append(path("M82 46 Q98 39 114 46", fill="none", stroke="#F08A6A", stroke_width="0.7", opacity="0.8"))
    o.append(path("M116 47 q8 2 12 10 M116 48 q10 -2 14 4", fill="none", stroke="#B83020", stroke_width="2.2", stroke_linecap="round"))
    # back arm (far, reaching across to the lower grip)
    sh2, el2, wr2 = (128, 82), (124, 110), (97, 104)
    o.append(R.deltoid(128, 80, 11, m.back(), 1))
    o.append(K.arm(sh2, el2, wr2, m, w=10.5, muscular=0.55, hand=None, back=True))
    # the club: two-handed, smashing down toward the anvil
    grip = (84, 92)
    o.append(R.kclub(grip, -128, 112, 5.2, 17.5, seed=12))
    # gold studs on the club head (it is the magic 방망이)
    a = math.radians(-128)
    for k, t in enumerate((0.62, 0.7, 0.78, 0.86)):
        for s in (-1, 0, 1):
            x = grip[0] + math.cos(a) * 112 * t - math.sin(a) * s * 8
            y = grip[1] + math.sin(a) * 112 * t + math.cos(a) * s * 8
            o.append(K.stud(x, y, 1.9, GOLD))
    # front arm
    sh, el_, wr = (70, 80), (52, 98), (72, 88)
    o.append(R.deltoid(70, 78, 11.5, m, -1))
    o.append(K.arm(sh, el_, wr, m, w=11, muscular=0.6, hand=None))
    o.append(scar([(56, 92), (60, 98), (58, 104)], 0.8))
    o.append(R._armband(sh, el_, 0.42, 11.2))
    for (w_, e_) in ((wr2, el2), (wr, el_)):
        fd = (w_[0] - e_[0], w_[1] - e_[1])
        o.append(K.hand(w_, fd, 7.6, m, kind="fist"))
        o.append(R.kclaws(w_, fd, 7.6))
    # gold & silver coins bursting from the club head — "금 나와라 뚝딱"
    hx, hy = grip[0] + math.cos(a) * 92, grip[1] + math.sin(a) * 92
    o.append(circ(hx, hy, 26, fill="url(#h2-forge)", opacity="0.6"))
    rr = random.Random(21)
    for i in range(34):
        ang = rr.uniform(-200, 40)
        dist = rr.uniform(10, 54)
        x = hx + math.cos(math.radians(ang)) * dist
        y = hy + math.sin(math.radians(ang)) * dist * 0.85 + dist * 0.25
        if 0 < x < 198 and 0 < y < 200:
            o.append(coin(x, y, rr.uniform(2.6, 4.4), rr.uniform(0, 180), silver=(i % 7 == 0)))
    for i in range(10):  # coins already landed
        x, y = rr.uniform(14, 70), rr.uniform(184, 198)
        o.append(ell(x, y, 3.2, 1.3, fill="url(#h2-coin)", stroke="#3A2206", stroke_width="0.4"))
    o.append(R.sparks(hx, hy, 24, 22, 30, 22, color="#FFF0B0", rmax=1.2))
    o.append(gold_ring_text(158, 46, "뚝딱!", 17, rot=-8))
    return f'<g id="h2-dudfig">{"".join(o)}</g>'


# ================================================================= 구미호
def fox_tail(base, ang, ln, w, bend, seed, back=False):
    """One continuous fur tube in an S-curve, the last third burning into goblin-fire blue, a flame at the tip."""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    nx, ny = -uy, ux

    def pt(t, b):
        return (base[0] + ux * ln * t + nx * b, base[1] + uy * ln * t + ny * b)

    pts = [base, pt(0.3, bend * 0.55), pt(0.62, bend), pt(0.86, bend * 0.45), pt(1.0, -bend * 0.25)]
    fur = TAILFUR.back(0.18) if back else TAILFUR
    fire = TAILFIRE.back(0.1) if back else TAILFIRE
    o = [K.tube(pts, [w * 0.42, w * 0.95, w, w * 0.62, w * 0.14], fur, n_per=5, chunks_per_seg=2)]
    # creamy underside toward the tip, then the fire: same spline, no outline, so there is no seam
    S = K._catmull(pts, 5)
    sub = [(x, y) for (x, y, t) in S if t >= 0.62][::2]
    n = len(sub)
    if n >= 2:
        o.append(K.tube(sub, [w * (0.72 - 0.62 * i / (n - 1)) for i in range(n)], fire, outline=False, n_per=3, chunks_per_seg=1))
    # fur strokes following the curve
    rr = random.Random(seed)
    strokes = []
    for (x, y, t) in S[2:-3]:
        for _ in range(3):
            ox, oy = rr.uniform(-w, w) * 0.72, rr.uniform(-w, w) * 0.72
            strokes.append(path(f"M{f(x + ox)} {f(y + oy)} l{f(ux * 5 + rr.uniform(-1.4, 1.4))} {f(uy * 5 + rr.uniform(-1.4, 1.4))}", fill="none",
                                stroke=rr.choice(("#FFE2B0", "#7A2E0C", "#FFF4DC" if t > 0.6 else "#A84E1E")), stroke_width="0.6",
                                stroke_opacity="0.55", stroke_linecap="round"))
    o.append("".join(strokes))
    # spiky fringe breaking the silhouette
    for i in range(1, len(S) - 2, 2):
        x, y, t = S[i]
        for s in (-1, 1):
            l = rr.uniform(2, 4.4)
            hw = w * (0.5 + 0.5 * math.sin(min(t * 1.25, 1) * math.pi))
            px, py = x + nx * s * hw, y + ny * s * hw
            o.append(path(f"M{f(px - ux * 1.5)} {f(py - uy * 1.5)} L{f(px + ux * l + nx * s * l * 0.5)} {f(py + uy * l + ny * s * l * 0.5)} L{f(px + ux * 1.5)} {f(py + uy * 1.5)} Z",
                          fill=fur.base if t < 0.6 else fire.base, opacity="0.9"))
    tip = pts[-1]
    o.append(R.flame(tip[0], tip[1] + 3, 0.34, rot=ang + 90, halo=True, seed=seed))
    return "".join(o)


# an unsettling, elegant adult: long refined face, sharp amber eyes, thin brows, the faintest closed-lip smirk
GUMIHO_LOOK = dict(shape="long", jaw=0.92, cheek=0.7, fem=True, eye="sharp", eye_size=1.12, lid="double", brow="thin", brow_col="#1A1014",
                   nose="small", nose_len=1.0, mouth="smirk", lip=0.65, mouth_col="#8E2230", age=0.12, turn=-0.18, eye_col="#2A1404", gaze=(0.5, 0.0))


def gumiho_head(hx, hy, hr):
    """Hair mass, 쪽머리 bun and 비녀, faint fox ears, then the amber-eyed face and parted hair cap."""
    o = []
    # loose strands and back hair blown to the right
    rr = random.Random(21)
    for k in range(14):
        x0 = hx + 4 + k * 0.6
        ln = rr.uniform(14, 34)
        o.append(path(f"M{f(x0)} {f(hy + 2 + k * 0.5)} q{f(rr.uniform(3, 8))} {f(ln * 0.5)} {f(rr.uniform(9, 18))} {f(ln)}", fill="none",
                      stroke="#120C12" if k % 3 else "#3C3048", stroke_width=f(rr.uniform(0.6, 1.2)), stroke_linecap="round"))
    # fox ears: behind the hair, translucent, as if still coming through
    for sx, tipx in ((-1, -0.92), (1, 0.98)):
        ear = [(hx + sx * hr * 0.28, hy - hr * 0.86), (hx + tipx * hr, hy - hr * 2.02), (hx + sx * hr * 1.02, hy - hr * 0.52)]
        inner = [(hx + sx * hr * 0.46, hy - hr * 0.82), (hx + tipx * hr * 0.96, hy - hr * 1.72), (hx + sx * hr * 0.9, hy - hr * 0.6)]
        o.append(f'<g opacity="0.86">{poly(ear, fill="#A84E1E" if sx < 0 else "#6A2A10", stroke=OUTL, stroke_width="0.7")}{poly(inner, fill="#22101C")}'
                 f'{path(f"M{P(ear[0])} L{P(ear[1])}", stroke=BLUE_L, stroke_width="0.8", opacity="0.9")}</g>')
    o.append(ell(hx + 1.5, hy - 1, hr * 1.1, hr * 1.14, fill="#0E0A10", stroke=OUTL, stroke_width="0.7"))
    # low bun with a long gold 비녀
    o.append(K.ball(hx + hr * 1.16, hy + hr * 0.42, hr * 0.56, HAIR, ry=hr * 0.48))
    o.append(path(f"M{f(hx + hr * 0.92)} {f(hy + hr * 0.3)} q{f(hr * 0.3)} {f(-hr * 0.14)} {f(hr * 0.6)} 0 M{f(hx + hr * 0.9)} {f(hy + hr * 0.5)} q{f(hr * 0.34)} {f(-hr * 0.1)} {f(hr * 0.66)} {f(hr * 0.06)}",
                  fill="none", stroke="#5A4A68", stroke_width="0.6", opacity="0.8"))
    o.append(K.form((hx + hr * 0.46, hy + hr * 0.52), (hx + hr * 1.96, hy + hr * 0.3), 0.8, 0.7, GOLD, ao_start=False))
    o.append(K.ball(hx + hr * 2.0, hy + hr * 0.3, 1.5, JADE))
    fc, _ = K.face(hx, hy, hr, GUMIHO_SKIN, GUMIHO_LOOK, ear=False)
    # amber fox irises: the kit paints a brown ring; this face gets amber, with a glow over each eye
    o.append(fc.replace('fill="#4A2E1C"', 'fill="#F0A626"'))
    fx = hx + GUMIHO_LOOK["turn"] * hr * 0.3
    for sx in (-1, 1):
        o.append(circ(fx + sx * hr * 0.36, hy - hr * 0.04, hr * 0.2, fill="#FFB428", opacity="0.28", filter="url(#r2-blur2)"))
    # parted hair cap framing the forehead
    x, y, r = hx, hy, hr
    o.append(path(f"M{f(x - r * 1.0)} {f(y + r * 0.1)} C{f(x - r * 1.12)} {f(y - r * 0.9)} {f(x - r * 0.5)} {f(y - r * 1.2)} {f(x - r * 0.1)} {f(y - r * 1.16)} "
                  f"C{f(x + r * 0.5)} {f(y - r * 1.2)} {f(x + r * 1.12)} {f(y - r * 0.9)} {f(x + r * 1.0)} {f(y + r * 0.1)} "
                  f"C{f(x + r * 0.8)} {f(y - r * 0.5)} {f(x + r * 0.34)} {f(y - r * 0.72)} {f(x - r * 0.08)} {f(y - r * 0.92)} "
                  f"C{f(x - r * 0.42)} {f(y - r * 0.72)} {f(x - r * 0.82)} {f(y - r * 0.5)} {f(x - r * 1.0)} {f(y + r * 0.1)} Z",
                  fill="#120C12", stroke=OUTL, stroke_width="0.6"))
    for k, off in enumerate((-0.7, -0.45, 0.3, 0.6)):
        o.append(path(f"M{f(x - r * 0.08)} {f(y - r * 1.08)} Q{f(x + r * off)} {f(y - r * 1.06)} {f(x + r * off * 1.3)} {f(y - r * 0.45)}", fill="none",
                      stroke="#6A5A80", stroke_width="0.55", opacity="0.75" if off < 0 else "0.4"))
    # one loose lock over the cheek
    o.append(path(f"M{f(x + r * 0.86)} {f(y - r * 0.2)} q{f(r * 0.2)} {f(r * 0.8)} {f(-r * 0.04)} {f(r * 1.5)}", fill="none", stroke="#120C12", stroke_width="0.9", stroke_linecap="round"))
    return "".join(o)


def seonangdang(cx, cy):
    """Roadside 서낭당 cairn: heaped stones."""
    rr = random.Random(31)
    o = []
    for row, (n, w) in enumerate(((6, 21), (5, 16), (3, 10), (1, 3))):
        for i in range(n):
            x = cx - w + (2 * w) * (i + 0.5) / n + rr.uniform(-1, 1)
            y = cy - row * 6.2 + rr.uniform(-0.8, 0.8)
            o.append(K.ball(x, y, rr.uniform(3.6, 5.0), STONE_N, ry=rr.uniform(2.8, 3.8)))
    return "".join(o)


def moonlit_pass():
    """Our own night scene: a mountain pass under the moon, a bare sacred tree with cloth strips, a cairn, ground mist."""
    o = [el("rect", x="-12", y="-12", width="224", height="240", fill="url(#h2-bgG)")]
    o.append(circ(152, 54, 54, fill="#A8C8F0", opacity="0.2", filter="url(#h2-blur8)"))
    o.append(circ(152, 54, 25, fill="url(#h2-moonface)"))
    rr = random.Random(5)
    for _ in range(9):
        a, d = rr.uniform(0, math.tau), rr.uniform(4, 20)
        o.append(ell(152 + math.cos(a) * d, 54 + math.sin(a) * d, rr.uniform(2, 6), rr.uniform(1.5, 4), fill="#8EA6C8", opacity="0.35", filter="url(#r2-blur2)"))
    for k in range(3):
        o.append(ell(120 + k * 30, 66 + k * 8, 34, 3.4, fill="#16203C", opacity="0.75", filter="url(#r2-blur2)"))
    for _ in range(46):
        x, y = rr.uniform(-6, 206), rr.uniform(6, 104)
        if (x - 152) ** 2 + (y - 54) ** 2 > 1100:
            o.append(circ(x, y, rr.uniform(0.25, 0.75), fill="#DCE8FF", opacity=f(rr.uniform(0.3, 0.9))))
    # layered ridges
    o.append(path("M-12 142 L12 118 L30 128 L56 100 L82 122 L110 106 L140 126 L170 104 L212 132 L212 196 L-12 196 Z", fill="#141B34"))
    o.append(path("M56 100 L66 112 L60 112 L72 124 M170 104 L178 116 L172 116 L186 128", fill="none", stroke="#3C4C78", stroke_width="0.8", opacity="0.7"))
    o.append(path("M-12 162 L20 140 L52 154 L90 136 L132 156 L168 138 L212 158 L212 200 L-12 200 Z", fill="#0C1024"))
    # bare 서낭나무 with 오색 strips, on the left
    o.append(path("M14 192 C16 160 12 140 18 118 L24 118 C22 142 26 162 28 192 Z", fill="#070810", stroke="#2A3A5C", stroke_width="0.6"))
    for (x0, y0, x1, y1, w) in ((19, 124, -2, 92, 3), (21, 120, 34, 84, 3), (17, 118, 12, 80, 2.4), (26, 102, 44, 96, 1.8), (10, 104, -6, 112, 1.6), (13, 92, 24, 70, 1.6), (0, 96, -8, 76, 1.2)):
        o.append(path(f"M{x0} {y0} Q{(x0 + x1) / 2 + 4} {(y0 + y1) / 2} {x1} {y1}", fill="none", stroke="#070810", stroke_width=f(w), stroke_linecap="round"))
    for (x, y, col, ln) in ((6, 106, "#C8432B", 15), (31, 94, "#EFE8D8", 18), (20, 128, "#E2B13C", 14), (14, 96, "#2F6E8E", 13), (38, 98, "#C8432B", 12), (25, 112, "#3E8F7A", 12)):
        o.append(path(f"M{x} {y} q3 {ln * 0.5} 7 {ln} l2.4 -1 q-3 {-ln * 0.5} -6 {-ln} Z", fill=col, stroke=OUTL, stroke_width="0.35", opacity="0.8"))
    o.append(R.diorama(100, 196, 86, 20, 10, seed=73, density=0.9, dark=True))
    o.append(seonangdang(172, 183))
    for k in range(4):
        o.append(ell(30 + k * 48, 178 + (k % 2) * 4, 42, 7, fill="#7E9CCC", opacity="0.14", filter="url(#h2-blur8)"))
    return "".join(o)


def gumiho():
    m = GUMIHO_SKIN
    o = [moonlit_pass()]
    o.append(K.cast_shadow(100, 190, 44, 8, 0.6))
    # nine tails, fanned from the small of the back: outer ones first, darker
    base = (104, 120)
    tails = [(-172, 76, 8.4, 11), (-8, 78, 8.4, -12), (-152, 88, 8.8, -13), (-28, 88, 8.8, 13), (-132, 94, 9, 12), (-48, 94, 9, -12),
             (-112, 96, 9.2, -10), (-68, 96, 9.2, 10), (-90, 100, 9.4, 9)]
    for i, (ang, ln, w, bend) in enumerate(tails):
        o.append(fox_tail(base, ang, ln, w, bend, seed=100 + i, back=i < 4))
    # the fox she really is: a huge ghost over the tails, behind her body, with amber eyes either side of her head
    fox = ("M46 112 C42 84 36 50 24 12 C46 26 62 42 72 52 C86 46 110 46 124 52 C134 42 150 26 172 12 C160 50 154 84 150 112 "
           "C142 136 120 152 98 172 C76 152 54 136 46 112 Z")
    o.append(path(fox, fill="#0A1432", opacity="0.3", filter="url(#r2-blur2)"))
    o.append(path(fox, fill="none", stroke=BLUE_L, stroke_width="1.2", opacity="0.6", filter="url(#r2-blur2)"))
    for sx in (-1, 1):
        o.append(path(f"M{f(98 + sx * 22)} 80 L{f(98 + sx * 42)} 66 L{f(98 + sx * 34)} 83 Z", fill="#FFB428", opacity="0.75", filter="url(#r2-blur2)"))
    body = []
    # 장옷: dark outer robe off the shoulders, streaming behind
    cloak, _ = drape([(84, 72), (112, 72), (130, 96), (146, 128), (156, 158), (138, 166), (116, 168), (84, 160), (72, 118)], JANGOT, normal=(-0.9, -0.4),
                     creases=fan_creases((90, 78), (120, 82), (92, 160), (150, 158), 7, seed=31), seed=31)
    body.append(cloak)
    body.append(trim([(130, 96), (146, 128), (156, 158), (138, 166)], w=1.3))
    # 배래: the far sleeve's hanging curve
    body.append(drape([(124, 86), (136, 96), (138, 110), (130, 118), (120, 106)], IVORY.back(0.15), normal=(-0.7, -0.7),
                      creases=[((127, 92), (128, 112))], seed=35)[0])
    # far arm (screen right): sleeve falls away, hooked fingers with a fox's nails
    sh2, el2, wr2 = (113, 78), (130, 92), (143, 112)
    body.append(K.arm(sh2, el2, wr2, m, sleeve=IVORY, cuff=CRIMSON, w=5.0, hand=None, back=True))
    fd2 = (wr2[0] - el2[0], wr2[1] - el2[1])
    body.append(K.hand(wr2, fd2, 4.0, m.back(0.12), kind="fist"))
    body.append(R.kclaws(wr2, fd2, 4.0))
    # under the hem: a 버선 toe on one side; on the other a fox paw has already come through
    body.append(K.ball(76, 192, 5.4, FUR_W, ry=2.8))
    paw = (124, 190)
    body.append(K.tube([(122, 180), paw], [4.2, 3.8], TAILFUR))
    body.append(K.ball(paw[0] + 1.6, paw[1] + 2.4, 5.2, FUR_W, ry=3.2))
    for k in range(3):
        body.append(path(f"M{f(paw[0] + 1.4 + k * 2)} {f(paw[1] + 4.4)} l1.3 1.6", stroke="#2A1A10", stroke_width="0.8", stroke_linecap="round"))
    # the full 치마 wrapped high under the bust
    sk, skd = drape([(82, 92), (116, 92), (124, 118), (132, 150), (140, 178), (146, 188), (124, 191), (100, 192), (78, 191), (58, 186), (62, 160), (70, 124)],
                    CHIMA, normal=(-1, -0.15), creases=fan_creases((84, 96), (116, 96), (60, 186), (142, 186), 14, seed=33, jitter=4.0), seed=33)
    body.append(sk)
    # 금박 hem and a wash of blue fire rising from the hem: the dress is coming apart into fox-fire
    hem = "".join(path(f"M{f(58 + k * 7.4)} 180 q2 -3 4 0 q-2 3 -4 0 Z", fill="url(#r2-brass)", opacity="0.85") for k in range(13))
    hem += el("rect", x="50", y="174", width="104", height="20", fill=BLUE, opacity="0.14", filter="url(#r2-blur2)")
    body.append(K.clip_overlay(skd, hem))
    # wrap seam: the overlapping front edge of the skirt, with its pale lining
    body.append(K.clip_overlay(skd, path("M94 96 Q86 140 88 196", fill="none", stroke="#120410", stroke_width="3", opacity="0.6", filter="url(#r2k-soft)")
                               + path("M92.6 96 Q84.6 140 86.6 196", fill="none", stroke="#F2E8D8", stroke_width="1.1", opacity="0.9")))
    body.append(trim([(58, 186), (78, 191), (100, 192), (124, 191), (146, 188)], w=0.9))
    body.append(R.rim_stroke(skd, 118, 96, 34, 94, width=1.8, op=0.55))
    body.append(R.sparks(102, 182, 44, 8, 18, 44))
    # neck under the head; 저고리 sits over it
    body.append(K.tube([(95, 72), (94, 62)], [4.2, 3.5], m))
    body.append(K.ao(95, 65, 5, 2, 0.45))
    jg, jgd = drape([(83, 75), (92, 69), (100, 69), (111, 74), (118, 83), (118, 96), (100, 100), (80, 96), (78, 84)], IVORY, normal=(-1, -0.25),
                    creases=[((86, 82), (84, 96)), ((112, 82), (114, 96)), ((98, 82), (98, 98))], seed=34)
    body.append(jg)
    # 깃 and 고름 in crimson, a long ribbon falling down the skirt
    body.append(path("M92 68 L104 88 L100 91 L88 71 Z", fill="#8E2230", stroke=OUTL, stroke_width="0.5"))
    body.append(path("M101 68 L95 80 L92 78 L97 67 Z", fill="#6E1A26", stroke=OUTL, stroke_width="0.5"))
    body.append(path("M92 68 L103.4 88", stroke="#F6EEE0", stroke_width="0.9"))
    body.append(K.tube([(102, 89), (106, 110), (103, 134)], [1.8, 2.1, 2.0], CRIMSON, n_per=4, chunks_per_seg=2))
    body.append(K.tube([(101, 89), (97, 106), (98, 120)], [1.6, 1.8, 1.7], CRIMSON.back(0.1), n_per=4, chunks_per_seg=2))
    body.append(K.ball(102, 89, 2.6, CRIMSON))
    # 노리개 with a blue-fire jade
    body.append(path("M110 96 L111 116", stroke="url(#r2-brass)", stroke_width="0.8"))
    body.append(K.ball(111, 118, 2.6, JADE))
    for dx in (-1.6, 0, 1.6):
        body.append(path(f"M111 121 l{dx} 12", stroke="#8E2230", stroke_width="0.7"))
    body.append(K.ao(100, 98, 20, 2.6, 0.4))
    body.append(gumiho_head(94, 52, 10.8))
    # near arm (screen left) lifted, offering the glowing 여우구슬 on the open palm
    sh, el_, wr = (82, 78), (62, 92), (52, 70)
    # 배래: the wide sleeve's hanging belly behind the raised forearm
    body.append(drape([(58, 76), (47, 78), (44, 92), (52, 104), (66, 102), (72, 90)], IVORY, normal=(-0.8, -0.5),
                      creases=[((52, 82), (52, 100)), ((60, 84), (62, 100))], seed=36)[0])
    body.append(K.arm(sh, el_, wr, m, sleeve=IVORY, cuff=CRIMSON, w=5.2, hand=None))
    body.append(K.ao(80, 84, 5, 5, 0.3))
    orb = (47, 50)
    body.append(circ(orb[0], orb[1], 19, fill="url(#r2-halo)", opacity="0.95"))
    body.append(K.hand(wr, (-0.25, -1), 4.2, m, kind="open"))
    body.append(circ(orb[0], orb[1], 7.4, fill="url(#h2-foxorb)", stroke="#BFEBFF", stroke_width="0.6"))
    body.append(path(f"M{orb[0] - 4} {orb[1] + 2} q4 -7 8 -2 q-2 5 -6 3", fill="none", stroke="#FFFFFF", stroke_width="0.8", opacity="0.85"))
    body.append(circ(orb[0] - 2.4, orb[1] - 2.8, 1.6, fill="#FFFFFF", opacity="0.95"))
    body.append(R.sparks(orb[0], orb[1], 17, 15, 18, 112))
    # the orb lights her lit side: cool glow on the raised sleeve and cheek
    body.append(ell(62, 78, 11, 16, fill=BLUE_L, opacity="0.12", filter="url(#r2-blur2)"))
    o.append("".join(body))
    return f'<g id="h2-gumfig">{"".join(o)}</g>'


# ================================================================= UI pieces
def hero_svg(fn, label):
    return (f'<svg width="100%" height="100%" viewBox="-6 4 212 206" preserveAspectRatio="xMidYMid slice" role="img" aria-label="{label}">'
            f'{fn()}</svg>')


def medallion(ref_id, cx, cy, zoom, label):
    """Portrait bust: zoom into the hero's own head/shoulders inside a brass medallion."""
    cid = f"{ref_id}-med"
    tx, ty = 50 - cx * zoom, 52 - cy * zoom
    return (f'<svg width="76" height="76" viewBox="0 0 100 100" role="img" aria-label="{label} 초상">'
            f'<clipPath id="{cid}"><circle cx="50" cy="50" r="40"></circle></clipPath>'
            f'<circle cx="50" cy="50" r="48" fill="url(#r2-brass)" stroke="#2A1608" stroke-width="1.2"></circle>'
            + "".join(f'<circle cx="{f(50 + math.cos(k * math.tau / 20) * 44.4)}" cy="{f(50 + math.sin(k * math.tau / 20) * 44.4)}" r="1.3" fill="{"#FFF0C0" if k % 2 else "#5A3A10"}"></circle>' for k in range(20))
            + f'<circle cx="50" cy="50" r="41" fill="#0C1220" stroke="#1A0E06" stroke-width="2"></circle>'
            f'<g clip-path="url(#{cid})"><rect width="100" height="100" fill="url(#h2-bgB)"></rect>'
            f'<use href="#{ref_id}" transform="translate({f(tx)} {f(ty)}) scale({f(zoom)})"></use>'
            f'<circle cx="50" cy="50" r="40" fill="url(#r2-vign)"></circle></g>'
            f'<circle cx="50" cy="50" r="40" fill="none" stroke="#FFF0C0" stroke-width="0.8" opacity="0.6"></circle></svg>')


def icon(kind, key):
    g = []
    if kind == "bridge":
        g.append(circ(30, 13, 5, fill="#EAF6FF", opacity="0.85"))
        g.append(path("M6 34 Q22 14 38 34 L38 38 L6 38 Z", fill="#33587E", stroke=BLUE_L, stroke_width="1"))
        g.append(path("M11 38 Q22 24 33 38 Z", fill="#0A1426"))
        g.append(path("M6 30 L38 30", stroke=BLUE_L, stroke_width="0.8", opacity="0.6"))
    elif kind == "wisps":
        for (x, y, s) in ((14, 30, 0.34), (30, 28, 0.38), (22, 20, 0.3)):
            g.append(wisp(x, y, s, int(x * 7 + y)))
    elif kind == "procession":
        for k, (x, y, s) in enumerate(((10, 34, 0.18), (17, 30, 0.22), (25, 27, 0.26), (34, 25, 0.32))):
            g.append(R.flame(x, y, s, halo=False, seed=40 + k))
        g.append(path("M4 38 Q22 30 40 28", fill="none", stroke=BLUE_L, stroke_width="0.8", stroke_dasharray="2 2", opacity="0.7"))
    elif kind == "gold":
        g.append(circ(22, 22, 16, fill="url(#h2-forge)", opacity="0.8"))
        for (x, y, r, a) in ((14, 16, 4.4, 10), (28, 14, 4, 60), (22, 26, 5, 0), (32, 28, 3.6, 120), (12, 30, 3.4, 70)):
            g.append(coin(x, y, r, a))
    elif kind == "silver":
        g.append(path("M22 6 L36 12 Q36 28 22 38 Q8 28 8 12 Z", fill="url(#h2-silver)", stroke="#2A3240", stroke_width="1"))
        g.append(path("M22 10 L32 14 Q32 26 22 33", fill="none", stroke="#FFFFFF", stroke_width="0.9", opacity="0.7"))
        g.append(coin(22, 21, 6, 0, silver=True))
    elif kind == "forge":
        g.append(circ(22, 26, 14, fill="url(#h2-forge)", opacity="0.85"))
        g.append(path("M6 26 L36 26 L37 29 L30 30 L28 34 L16 34 L14 30 L8 29 Q4 28 6 26 Z", fill="url(#r2-iron)", stroke=OUTL, stroke_width="0.8"))
        g.append(path("M6 26 L36 26", stroke="#FFFFFF", stroke_width="0.8", opacity="0.7"))
        g.append(el("rect", x="15", y="22", width="12", height="4", fill="#FFB050", filter="url(#h2-glow)"))
    elif kind == "fox":
        g.append(path("M10 34 L8 10 L18 18 Q22 16 26 18 L36 10 L34 34 Q28 40 22 40 Q16 40 10 34 Z", fill="url(#h2-tail)", stroke=OUTL,
                      stroke_width="0.8", opacity="0.9"))
        g.append(path("M14 26 l5 2 M30 26 l-5 2", stroke="#0A0610", stroke_width="1.6", stroke_linecap="round"))
        g.append(path("M18 35 Q22 37 26 35", fill="none", stroke="#0A0610", stroke_width="1"))
    elif kind == "orb":
        g.append(circ(22, 22, 17, fill="url(#r2-halo)", opacity="0.9"))
        g.append(circ(22, 22, 9, fill="url(#h2-foxorb)", stroke="#BFEBFF", stroke_width="0.7"))
        g.append(path("M17 24 q5 -9 10 -3 q-2 6 -7 4", fill="none", stroke="#FFFFFF", stroke_width="1", opacity="0.85"))
        g.append(path("M6 38 Q14 30 15 24 M38 38 Q30 30 29 24", fill="none", stroke="#E8A020", stroke_width="1.4", stroke_dasharray="3 2", stroke_linecap="round"))
    elif kind == "charm":
        d = "M22 22"
        for k in range(1, 40):
            a = k * 0.45
            r = k * 0.42
            d += f" L{f(22 + math.cos(a) * r)} {f(22 + math.sin(a) * r)}"
        g.append(path(d, fill="none", stroke=BLUE_L, stroke_width="1.4", filter="url(#r2-glow)"))
        g.append(circ(22, 22, 3, fill="#FFFFFF"))
    body = "".join(g)
    return (f'<svg width="44" height="44" viewBox="0 0 44 44" role="img" aria-label="스킬 {key}" style="flex: none">'
            f'<rect x="1" y="1" width="42" height="42" rx="4" fill="url(#r2-brass)" stroke="#2A1608" stroke-width="1"></rect>'
            f'<rect x="4" y="4" width="36" height="36" rx="2" fill="url(#h2-icon)" stroke="#1A0E06" stroke-width="1"></rect>'
            f'<g transform="translate(4 4) scale(0.818)">{body}</g>'
            f'<rect x="4" y="4" width="36" height="14" rx="2" fill="#FFFFFF" opacity="0.06"></rect>'
            f'<rect x="28" y="29" width="13" height="12" rx="2" fill="#1A0E06" stroke="#C9A35A" stroke-width="0.8"></rect>'
            f'<text x="34.5" y="38.4" text-anchor="middle" style="font-family: \'IBM Plex Mono\', monospace; font-weight: 600; font-size: 9px" fill="#F2D58A">{key}</text>'
            f'</svg>')


MONO, MYEONG, GOLD_TEXT, FRAME, PARCH = R.MONO, R.MYEONG, R.GOLD_TEXT, R.FRAME, R.PARCH


def stat(label, v, col):
    cells = "".join(f'<span style="flex: 1; height: 7px; background: {col if i < v else "#CDBB92"}; '
                    f'box-shadow: inset 0 -1px 0 rgba(0,0,0,{0.3 if i < v else 0.12})"></span>' for i in range(10))
    return (f'<div style="display: flex; align-items: center; gap: 6px"><span style="width: 42px; font-size: 11px; color: #4A3A26; font-weight: 600">{label}</span>'
            f'<span style="flex: 1; display: flex; gap: 1.5px; padding: 1.5px; background: #2A1E12; border-radius: 1px">{cells}</span>'
            f'<span style="{MONO}; width: 14px; text-align: right; font-size: 11px; color: #2A1E12">{v}</span></div>')


def skill(kind, key, name, kind_txt, desc):
    return (f'<div style="display: flex; gap: 9px; align-items: center">{icon(kind, key)}'
            f'<div style="display: flex; flex-direction: column; gap: 1px; min-width: 0">'
            f'<span style="display: flex; gap: 6px; align-items: baseline"><b style="{MYEONG}; font-weight: 800; font-size: 14px; color: #1E140A">{name}</b>'
            f'<span style="{MONO}; font-size: 10px; color: #155A8A; font-weight: 600">{kind_txt}</span></span>'
            f'<span style="font-size: 12px; line-height: 1.35; color: #3A2C1C">{desc}</span></div></div>')


def hero_panel(code, fig_svg, med, name, hanja, role, source, stats, skills, lore, tint):
    stats_html = "".join(stat(l, v, c) for l, v, c in stats)
    skills_html = "".join(skill(*s) for s in skills)
    return f"""
    <article style="{FRAME}; min-height: 0">{R.corners()}
      <div style="position: relative; height: 418px; overflow: hidden; border: 1px solid #0A0603; box-shadow: inset 0 0 26px rgba(0,0,0,0.8); background: #0A0C10">
        {fig_svg}
        <div style="position: absolute; left: 0; right: 0; top: 0; padding: 8px 12px; display: flex; justify-content: space-between; background: linear-gradient(180deg, rgba(8,6,4,0.78), rgba(8,6,4,0))">
          <span style="{MONO}; font-size: 11px; font-weight: 600; letter-spacing: 0.1em; color: #F2D58A">{code}</span>
          <span style="{MONO}; font-size: 11px; letter-spacing: 0.08em; color: {tint}">{role}</span>
        </div>
      </div>
      <div style="{PARCH}; flex: 1; display: flex; flex-direction: column; gap: 7px; padding: 8px 12px 9px">{R.paper_grain()}
        <div style="position: relative; display: flex; gap: 10px; align-items: center">
          {med}
          <div style="display: flex; flex-direction: column; gap: 2px; min-width: 0">
            <span style="display: flex; align-items: baseline; gap: 8px"><b style="{MYEONG}; font-weight: 800; font-size: 27px; color: #1A1008; line-height: 1.05">{name}</b>
              <span style="{MYEONG}; font-weight: 700; font-size: 15px; color: #6A2A18">{hanja}</span></span>
            <span style="font-size: 11px; color: #5A4632; line-height: 1.35">{source}</span>
          </div>
        </div>
        <div style="position: relative; display: grid; grid-template-columns: 1fr 1fr; gap: 3px 14px">{stats_html}</div>
        <div style="position: relative; height: 1px; background: linear-gradient(90deg, rgba(138,98,52,0), #8A6234, rgba(138,98,52,0))"></div>
        <div style="position: relative; display: flex; flex-direction: column; gap: 5px">{skills_html}</div>
        <p style="position: relative; margin: auto 0 0; font-size: 12px; line-height: 1.45; color: #3A2C1C; border-left: 2px solid #B8432A; padding-left: 8px">{lore}</p>
      </div>
    </article>"""


SKY = "#7FCBF2"
panels = "".join([
    hero_panel("HERO H-D1", hero_svg(bihyeong, "비형랑 — 도깨비불을 불러내는 반인반귀"),
               medallion("h2-bihfig", 100, 52, 2.2, "비형랑"),
               "비형랑", "鼻荊郞", "소환 영웅 · SUMMONER", "『삼국유사』 기이편 · 도화녀와 비형랑 설화",
               [("체력", 6, "#4F8FC8"), ("공격", 4, "#C8432B"), ("사거리", 7, "#3E8F7A"), ("이동", 7, "#C9A35A")],
               [("bridge", "Q", "귀교 하룻밤", "지형 생성", "강이나 골짜기에 돌다리 · 돌담을 즉시 세웁니다."),
                ("wisps", "W", "귀신 부리기", "소환", "도깨비불 셋을 불러내 채집과 정찰을 맡깁니다."),
                ("procession", "E", "요물 행렬", "지속 오라", "주변 요물 아군의 이동 속도 +25%.")],
               "죽은 왕의 혼과 도화녀 사이에서 난 반인반귀. 밤이면 귀신 무리를 이끌고 노닐다, 동이 트기 전에 돌다리를 놓고 사라졌다.", SKY),
    hero_panel("HERO H-D2", hero_svg(duduri, "두두리 — 방망이로 금을 쏟아내는 대장장이 도깨비"),
               medallion("h2-dudfig", 98, 56, 1.75, "두두리"),
               "두두리", "豆豆里", "대장장이 신 · FORGE BRUTE", "신라 목랑(木郞) · 두두리 신앙과 방망이 민담",
               [("체력", 10, "#4F8FC8"), ("공격", 9, "#C8432B"), ("사거리", 2, "#3E8F7A"), ("이동", 4, "#C9A35A")],
               [("gold", "Q", "금 나와라 뚝딱", "자원", "방망이로 땅을 내리쳐 귀화석 +60을 쏟아냅니다."),
                ("silver", "W", "은 나와라 뚝딱", "보호막", "아군 하나에 피해 200을 막는 은빛 막을 씌웁니다."),
                ("forge", "E", "대장간 신령", "지속 오라", "주변 방망이 대장간 생산 +30%, 아군 공격 +1.")],
               "쇠를 두드려 복을 내리는 도깨비 대장장이. 방망이를 한 번 치면 금이, 두 번 치면 은이 쏟아진다.", "#FFB070"),
    hero_panel("HERO H-D3", hero_svg(gumiho, "구미호 — 꼬리 아홉에 푸른 불을 단 둔갑 여우"),
               medallion("h2-gumfig", 94, 56, 2.3, "구미호"),
               "구미호", "九尾狐", "둔갑 영웅 · TRICKSTER", "민담 · 고소설 · 여우구슬 전승",
               [("체력", 5, "#4F8FC8"), ("공격", 7, "#C8432B"), ("사거리", 4, "#3E8F7A"), ("이동", 9, "#C9A35A")],
               [("fox", "Q", "여우 둔갑", "은신", "8초간 모습을 감추고, 첫 공격 피해가 두 배가 됩니다."),
                ("orb", "W", "여우구슬", "흡수", "적 하나의 기운을 빨아들여 체력을 회복합니다."),
                ("charm", "E", "홀림", "제어", "적 하나를 4초간 홀려 잠시 조종합니다.")],
                "오래 묵은 여우가 구슬을 품고 사람의 모습을 얻었다. 달 밝은 고갯길에서 말을 걸어 오거든, 먼저 그 그림자의 꼬리를 세어 보라.", SKY),
])

HEAD = re.sub(r"(도깨비|요물) 무리 종족 코덱스", "요물 무리 영웅 도감", R.HEAD)

body = f"""<div style="position: relative; width: 1440px; height: 960px; box-sizing: border-box; background: {R.GROUND}; color: {R.INK}; font-family: 'IBM Plex Sans KR', sans-serif; overflow: hidden">
  <svg width="0" height="0" style="position: absolute; width: 0; height: 0; overflow: hidden" aria-hidden="true" focusable="false"><defs>{R.DEFS}{HDEFS}<!--R2K--></defs></svg>
  {R.LATTICE_BG}
  <div style="position: relative; height: 100%; box-sizing: border-box; padding: 20px 40px 18px; display: flex; flex-direction: column; gap: 12px">
  <header style="{FRAME}; flex-direction: row; align-items: center; gap: 18px; padding: 10px 18px">{R.corners()}
    {R.crest()}
    <div style="display: flex; flex-direction: column; gap: 5px; flex: 1; min-width: 0">
      <span style="{MONO}; font-size: 12px; letter-spacing: 0.12em; color: #CFB98A">SAMJAE · HERO CODEX R2 · 옛 기록 · 민담 · 민간 신앙 · 3 HEROES</span>
      <h1 style="margin: 0; display: flex; align-items: baseline; gap: 14px; line-height: 1.05"><span style="{MYEONG}; font-weight: 800; font-size: 44px; {GOLD_TEXT}">영웅 · 요물 무리</span><span style="{MONO}; font-size: 19px; font-weight: 600; color: {BLUE}; letter-spacing: 0.14em; text-shadow: 0 0 8px rgba(79,179,232,0.5)">YOMUL HEROES</span></h1>
      <span style="font-size: 13px; color: #E2D6BC">옛 기록과 민담에서 걸어 나온 세 영웅. 한 판에 하나만 부를 수 있고, 쓰러지면 도깨비 소굴에서 다시 깨어납니다.</span>
    </div>
    <div style="{PARCH}; width: 300px; display: flex; flex-direction: column; gap: 3px; padding: 8px 12px; border: 1px solid #8A6234; font-size: 12px; line-height: 1.45">{R.paper_grain()}
      <b style="position: relative; {MYEONG}; font-weight: 800; font-size: 13px">영웅 규칙</b>
      <span style="position: relative">◆ 소굴에서 1기 소환 · 레벨 1–5 · 스킬 Q/W/E</span>
      <span style="position: relative">◆ 푸른 귀화 림라이트 + 바닥 오라로 일반 유닛과 구분</span>
      <span style="position: relative">◆ 사망 시 60초 뒤 소굴에서 부활</span>
    </div>
  </header>
  <div style="flex: 1; min-height: 0; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px">{panels}
  </div>
  </div>
</div>"""

body = body.replace("<!--R2K-->", K.defs_svg())
PROJ.mkdir(parents=True, exist_ok=True)
html = HEAD + body + R.TAIL
html = html.replace("도깨비 무리 문장", "요물 무리 문장")
# Own id namespace for this artboard: kit ids r2k- -> h2k-, race_r2 helper ids r2- -> h2r- (h2- stays for hero-local ids),
# so nothing can collide with RaceR2 or with this file's own h2- ids.
html = re.sub(r'(id="|#)r2k-', r'\1h2k-', html)
html = re.sub(r'(id="|#)r2-', r'\1h2r-', html)
_ids = re.findall(r'\bid="([^"]+)"', html)
assert len(_ids) == len(set(_ids)), "duplicate ids after rename"
OUT.write_text(html)
print(OUT, len(html))
