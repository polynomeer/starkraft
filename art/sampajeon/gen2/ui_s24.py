"""Shared helpers for the SAMJAE lobby (s2-) and results (s4-) screens.

High-fidelity pass: carved lacquered-wood frames with brass corner fittings,
aged hanji panels, gold display type, beveled lacquer buttons, painted race
busts in brass medallions, and an isometric projector for the painted
2.5D background scenes.
"""
import math
import random

FONTS = ("https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800"
         "&amp;family=IBM+Plex+Mono:wght@400;500;600"
         "&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap")

# Base tokens (SPEC.md)
GROUND, PANEL, RAISED, LINE = "#16140F", "#201D17", "#2A2620", "#4A4236"
INK, MUTED = "#EFE6D2", "#A89E8A"
RED, YELLOW, BLUE, WHITE, BLACK = "#C8432B", "#E2B13C", "#2F6E8E", "#EFE8D8", "#1A1814"
GREEN = "#3E8F7A"
ALERT, OK = "#E5604D", "#8CC79A"
ORE, GAS, FOOD = "#9FE3D0", "#B79AE0", "#E8DDBF"

# Ink on hanji (all >= 4.5:1 on the paper mid-tone #E4D4B0)
P_INK = "#2A1D12"
P_MUTED = "#5A4A36"
P_RED = "#9E2E1A"
P_GREEN = "#2C6A48"
P_BLUE = "#1E5574"
P_OCHRE = "#7A4E0E"
P_RULE = "#9C7E52"
GOLD = "#F2D58A"

DISPLAY = "'Nanum Myeongjo', serif"
BODY = "'IBM Plex Sans KR', sans-serif"
MONO = "'IBM Plex Mono', monospace"

RACES = {
    "r1": {"han": "人", "ko": "조선 공방", "en": "Joseon Works", "color": "#D9532E", "light": "#F09A6E", "ink": "#9E2E1A"},
    "r2": {"han": "地", "ko": "요물 무리", "en": "Yomul Horde", "color": "#4FB3E8", "light": "#A8DDF5", "ink": "#1E5574"},
    "r3": {"han": "天", "ko": "오방 신당", "en": "Obang Shrine", "color": "#E2B13C", "light": "#F2D58A", "ink": "#7A4E0E"},
}

WOOD_BG = ("repeating-linear-gradient(0deg, rgba(0,0,0,0.16) 0 1px, transparent 1px 4px), "
           "repeating-linear-gradient(0deg, rgba(255,210,150,0.06) 0 2px, transparent 2px 11px), "
           "linear-gradient(180deg, #5E3A24 0%, #3F2617 46%, #28170D 100%)")
LACQUER_BG = ("repeating-linear-gradient(0deg, rgba(0,0,0,0.14) 0 1px, transparent 1px 5px), "
              "linear-gradient(180deg, #3A1E14 0%, #24120B 60%, #170B06 100%)")
PAPER_BG = ("radial-gradient(circle at 86% 78%, rgba(140,96,44,0.20), transparent 34%), "
            "radial-gradient(circle at 8% 92%, rgba(140,96,44,0.16), transparent 30%), "
            "radial-gradient(120% 95% at 28% 16%, #F3E9D0 0%, #E6D6B3 52%, #D3BE93 100%)")


def page(title, w, h, body):
    return f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="{FONTS}">
<style>
body{{margin:0;background:{GROUND}}}
a{{color:{YELLOW}}}a:hover{{color:#F2D58A}}
button,input{{font-family:inherit}}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
  renderVals() {{
    return {{}};
  }}
}}
</script>
</body>
</html>
"""


# --------------------------------------------------------------------------
# Shared <defs>: one zero-size (but rendered) SVG per page.
# --------------------------------------------------------------------------

def _lg(i, stops, x2="0", y2="1", x1="0", y1="0"):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"{"" if a is None else f" stop-opacity={chr(34)}{a}{chr(34)}"}></stop>'
                for o, c, a in [(t + (None,))[:3] for t in stops])
    return f'<linearGradient id="{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def _rg(i, stops, cx="0.5", cy="0.5", r="0.5", fx=None, fy=None):
    f = f' fx="{fx}" fy="{fy}"' if fx is not None else ""
    s = "".join(f'<stop offset="{o}" stop-color="{c}"{"" if a is None else f" stop-opacity={chr(34)}{a}{chr(34)}"}></stop>'
                for o, c, a in [(t + (None,))[:3] for t in stops])
    return f'<radialGradient id="{i}" cx="{cx}" cy="{cy}" r="{r}"{f}>{s}</radialGradient>'


def shared_defs(p):
    g = []
    # metal / wood / paper
    g.append(_lg(f"{p}-brass", [(0, "#FFF0C0"), (0.28, "#E2C078"), (0.55, "#B48A3E"), (0.8, "#7A5A24"), (1, "#C9A35A")], x2="1", y2="1"))
    g.append(_rg(f"{p}-rivet", [(0, "#FFF6D8"), (0.35, "#E2C078"), (0.8, "#6A4A1A"), (1, "#2A1A08")], cx="0.38", cy="0.34", r="0.66"))
    g.append(_lg(f"{p}-woodv", [(0, "#6A4430"), (0.5, "#4A2E1E"), (1, "#2A1A10")], x2="1", y2="0"))
    g.append(_lg(f"{p}-woodh", [(0, "#7A5236"), (0.45, "#523320"), (1, "#2E1C12")]))
    g.append(_lg(f"{p}-gold", [(0, "#FFF4CC"), (0.5, "#E8C46A"), (1, "#8C6220")]))
    g.append(_rg(f"{p}-glow", [(0, "#FFE2A0", 0.95), (0.4, "#F2A04A", 0.5), (1, "#C8432B", 0)]))
    g.append(_rg(f"{p}-glowb", [(0, "#E6F7FF", 0.95), (0.4, "#4FB3E8", 0.55), (1, "#1B3242", 0)]))
    # bust materials
    g.append(_rg(f"{p}-bu-skinA", [(0, "#F8D8B2"), (0.55, "#E2AE80"), (1, "#A86A42")], cx="0.4", cy="0.36", r="0.7"))
    g.append(_rg(f"{p}-bu-skinC", [(0, "#F2CB9C"), (0.55, "#D49A68"), (1, "#8E5632")], cx="0.4", cy="0.36", r="0.7"))
    g.append(_rg(f"{p}-bu-skinB", [(0, "#8CA2E6"), (0.45, "#4C5CA4"), (1, "#1C2248")], cx="0.38", cy="0.34", r="0.74"))
    g.append(_lg(f"{p}-bu-bodyB", [(0, "#6274BC"), (0.5, "#2E3462"), (1, "#141732")], x2="1", y2="0.4"))
    g.append(_lg(f"{p}-bu-neck", [(0, "#C98E62"), (1, "#8A5232")]))
    g.append(_lg(f"{p}-bu-neckB", [(0, "#3E4A8A"), (1, "#1A1E40")]))
    g.append(_lg(f"{p}-bu-r1robe", [(0, "#F0845E"), (0.35, "#C8432B"), (0.75, "#8A2614"), (1, "#5A160A")], x2="1", y2="0.3"))
    g.append(_lg(f"{p}-bu-r1vest", [(0, "#4A4238"), (0.6, "#26201A"), (1, "#141110")], x2="1", y2="0.4"))
    g.append(_lg(f"{p}-bu-felt", [(0, "#4A4440"), (0.4, "#221E1B"), (1, "#0C0A09")], x2="0.6", y2="1"))
    g.append(_rg(f"{p}-bu-lens", [(0, "#F2FFFB"), (0.3, "#9FE3D0"), (0.75, "#2E7A72"), (1, "#0E2A2A")], cx="0.36", cy="0.32", r="0.7"))
    g.append(_lg(f"{p}-bu-tiger", [(0, "#F4B45A"), (0.6, "#D0782A"), (1, "#8A4612")], x2="1", y2="1"))
    g.append(_lg(f"{p}-bu-horn", [(0, "#FFF0C2"), (0.45, "#D8B266"), (1, "#6A4A1A")], x2="1", y2="1"))
    g.append(_rg(f"{p}-bu-fire", [(0, "#FFFFFF"), (0.35, "#BDE8FA"), (0.75, "#4FB3E8"), (1, "#1B5A8A")], cx="0.5", cy="0.66", r="0.6"))
    g.append(_lg(f"{p}-bu-hemp", [(0, "#F6EFDA"), (0.5, "#D6C9A6"), (1, "#958662")], x2="1", y2="0.4"))
    g.append(_lg(f"{p}-bu-indigo", [(0, "#4E64A8"), (0.55, "#2C3C70"), (1, "#18213F")], x2="1", y2="0.4"))
    g.append(_lg(f"{p}-bu-bamboo", [(0, "#F4E2B0"), (0.5, "#CDA866"), (1, "#7E5A2A")], x2="0.7", y2="1"))
    g.append(_lg(f"{p}-bu-pole", [(0, "#8A6040"), (0.5, "#5A3A22"), (1, "#2E1C10")], x2="1", y2="0"))
    for r, c0, c1 in (("r1", "#7A3424", "#1E100A"), ("r2", "#1E5A86", "#0A1420"), ("r3", "#7A5A1E", "#1A1408")):
        g.append(_rg(f"{p}-mg-{r}", [(0, c0), (1, c1)], cx="0.5", cy="0.32", r="0.75"))
    # filters
    g.append(f'<filter id="{p}-soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="3"></feGaussianBlur></filter>')
    g.append(f'<filter id="{p}-blur1" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.4"></feGaussianBlur></filter>')
    g.append(f'<filter id="{p}-blur6" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="6"></feGaussianBlur></filter>')
    g.append(f'<filter id="{p}-blur14" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="14"></feGaussianBlur></filter>')
    g.append(f'<filter id="{p}-woodgrain" x="0" y="0" width="100%" height="100%" filterUnits="userSpaceOnUse">'
             f'<feTurbulence type="fractalNoise" baseFrequency="0.006 0.28" numOctaves="3" seed="4" result="t"></feTurbulence>'
             f'<feColorMatrix in="t" type="matrix" values="0 0 0 0 0.10  0 0 0 0 0.05  0 0 0 0 0.02  0 0 0 -1.9 1.15"></feColorMatrix></filter>')
    g.append(f'<filter id="{p}-paper" x="0" y="0" width="100%" height="100%" filterUnits="userSpaceOnUse">'
             f'<feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed="9" result="n"></feTurbulence>'
             f'<feColorMatrix in="n" type="matrix" values="0 0 0 0 0.36  0 0 0 0 0.25  0 0 0 0 0.12  0 0 0 -1.4 0.86" result="speck"></feColorMatrix>'
             f'<feTurbulence type="fractalNoise" baseFrequency="0.012 0.09" numOctaves="2" seed="21" result="f"></feTurbulence>'
             f'<feColorMatrix in="f" type="matrix" values="0 0 0 0 0.98  0 0 0 0 0.94  0 0 0 0 0.84  0 0 0 -2.2 1.12" result="fiber"></feColorMatrix>'
             f'<feMerge><feMergeNode in="fiber"></feMergeNode><feMergeNode in="speck"></feMergeNode></feMerge></filter>')
    g.append(f'<filter id="{p}-ground" x="0" y="0" width="100%" height="100%" filterUnits="userSpaceOnUse">'
             f'<feTurbulence type="fractalNoise" baseFrequency="0.035 0.07" numOctaves="4" seed="13" result="t"></feTurbulence>'
             f'<feColorMatrix in="t" type="matrix" values="0 0 0 0 0.10  0 0 0 0 0.07  0 0 0 0 0.04  0 0 0 -1.7 1.05"></feColorMatrix></filter>')
    g.append(f'<filter id="{p}-smoke" x="-40%" y="-40%" width="180%" height="180%">'
             f'<feTurbulence type="fractalNoise" baseFrequency="0.035" numOctaves="3" seed="5" result="t"></feTurbulence>'
             f'<feDisplacementMap in="SourceGraphic" in2="t" scale="26" result="d"></feDisplacementMap>'
             f'<feGaussianBlur in="d" stdDeviation="3.2"></feGaussianBlur></filter>')
    g.append(f'<filter id="{p}-ink" x="-20%" y="-20%" width="140%" height="140%">'
             f'<feTurbulence type="fractalNoise" baseFrequency="0.02" numOctaves="3" seed="2" result="t"></feTurbulence>'
             f'<feDisplacementMap in="SourceGraphic" in2="t" scale="18" result="d"></feDisplacementMap>'
             f'<feGaussianBlur in="d" stdDeviation="1.2"></feGaussianBlur></filter>')
    return (f'<svg width="0" height="0" style="position: absolute; width: 0; height: 0; overflow: hidden" aria-hidden="true">'
            f'<defs>{"".join(g)}</defs></svg>')


# --------------------------------------------------------------------------
# Chrome: frames, corner fittings, plaques, buttons, chips
# --------------------------------------------------------------------------

def _overlay(p, filt, opacity=1.0, z=0):
    return (f'<svg width="100%" height="100%" style="position: absolute; inset: 0; z-index: {z}; pointer-events: none" aria-hidden="true">'
            f'<rect width="100%" height="100%" fill="#000" filter="url(#{p}-{filt})" opacity="{opacity}"></rect></svg>')


def corner_fitting(p, size=30):
    """Brass L-plate with cloud-scroll ends and three rivets (drawn top-left)."""
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 30 30" aria-hidden="true" style="display: block">'
            f'<path d="M3 4 H27 C28.6 4 29 6.4 27.4 7.6 C25.6 9 22.6 8.2 21.4 9.6 C20.4 10.8 20.6 12 19 12 H12 V19 C12 20.6 10.8 20.4 9.6 21.4 '
            f'C8.2 22.6 9 25.6 7.6 27.4 C6.4 29 4 28.6 4 27 V4 Z" fill="#120A04" opacity="0.55" transform="translate(1.2 1.6)"></path>'
            f'<path d="M2 3 H26 C27.6 3 28 5.4 26.4 6.6 C24.6 8 21.6 7.2 20.4 8.6 C19.4 9.8 19.6 11 18 11 H11 V18 C11 19.6 9.8 19.4 8.6 20.4 '
            f'C7.2 21.6 8 24.6 6.6 26.4 C5.4 28 3 27.6 3 26 V3 Z" fill="url(#{p}-brass)" stroke="#3A2408" stroke-width="0.8"></path>'
            f'<path d="M4.4 24 V4.4 H24" fill="none" stroke="#FFF2C8" stroke-opacity="0.55" stroke-width="0.8"></path>'
            f'<path d="M7.4 14 C7.4 10 10 7.4 14 7.4 M10 12.4 C10.6 11 11 10.6 12.4 10" fill="none" stroke="#5A3A10" stroke-width="0.9" stroke-linecap="round"></path>'
            f'<circle cx="6.6" cy="6.6" r="2.1" fill="url(#{p}-rivet)" stroke="#2A1806" stroke-width="0.5"></circle>'
            f'<circle cx="22.6" cy="5.2" r="1.5" fill="url(#{p}-rivet)" stroke="#2A1806" stroke-width="0.5"></circle>'
            f'<circle cx="5.2" cy="22.6" r="1.5" fill="url(#{p}-rivet)" stroke="#2A1806" stroke-width="0.5"></circle></svg>')


def corners4(p, size=30, off=-5):
    out = []
    for v, hh, tf in (("top", "left", ""), ("top", "right", "scaleX(-1)"),
                      ("bottom", "left", "scaleY(-1)"), ("bottom", "right", "scale(-1,-1)")):
        out.append(f'<span style="position: absolute; z-index: 3; {v}: {off}px; {hh}: {off}px; width: {size}px; height: {size}px; '
                   f'transform: {tf or "none"}">{corner_fitting(p, size)}</span>')
    return "".join(out)


def cloud_mark(color=P_RED, size=18):
    """Small cloud-scroll ornament (구름문) used before panel titles."""
    return (f'<svg width="{size}" height="{size * 0.66:.0f}" viewBox="0 0 30 20" aria-hidden="true" style="flex: none">'
            f'<path d="M3 15 C1 11 4 7 8 8 C8 4 13 2 16 5 C18 2 24 2 25 7 C29 7 30 12 27 14 C26 17 22 17 20 15 '
            f'C18 18 13 18 12 15 C9 17 5 17 3 15 Z" fill="{color}"></path>'
            f'<path d="M9 12 C9 10 11 9.6 12 11 M17 9.6 C17 8 19 7.6 20 9" fill="none" stroke="#F3E9D0" stroke-width="1.3" stroke-linecap="round"></path></svg>')


def gold_text(text, size, font=DISPLAY, weight=800, ls="0.04em", extra=""):
    return (f'<span style="font-family: {font}; font-weight: {weight}; font-size: {size}px; line-height: 1; letter-spacing: {ls}; '
            f'background: linear-gradient(180deg, #FFF6D2 0%, #F2D58A 38%, #C99A42 70%, #8A5E1E 100%); '
            f'-webkit-background-clip: text; background-clip: text; color: transparent; -webkit-text-stroke: 0.5px #3A2208; '
            f'filter: drop-shadow(0 2px 0 #1A0E06) drop-shadow(0 3px 6px rgba(0,0,0,0.6)); {extra}">{text}</span>')


def panel_title(title, right="", sub="", color=P_RED):
    sub_html = f'<span style="font-size: 12px; font-weight: 500; color: {P_MUTED}">{sub}</span>' if sub else ""
    return (f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 8px; '
            f'border-bottom: 3px double {P_RULE}">'
            f'<div style="display: flex; align-items: center; gap: 9px; min-width: 0">{cloud_mark(color)}'
            f'<h2 style="margin: 0; font-family: {DISPLAY}; font-weight: 800; font-size: 19px; color: {P_INK}; letter-spacing: 0.02em; white-space: nowrap">{title}</h2>{sub_html}</div>'
            f'{right}</div>')


def ornate(p, inner, pad="14px 18px 16px", gap=12, extra="", surface="paper", frame=9):
    """Carved wood frame + brass corners around a hanji (or lacquer) panel."""
    if surface == "paper":
        surf = (f"background: {PAPER_BG}; color: {P_INK}; box-shadow: inset 0 0 0 1px #6E4E26, inset 0 0 0 2px rgba(255,244,214,0.55), "
                f"inset 0 3px 10px rgba(70,40,10,0.35), inset 0 -2px 6px rgba(70,40,10,0.2), 0 0 0 1px #120A04")
        tex = _overlay(p, "paper", 0.9)
    else:
        surf = (f"background: {LACQUER_BG}; color: {INK}; box-shadow: inset 0 0 0 1px #6E4E26, inset 0 1px 0 2px rgba(255,214,160,0.12), "
                f"inset 0 4px 14px rgba(0,0,0,0.55), 0 0 0 1px #120A04")
        tex = _overlay(p, "woodgrain", 0.5)
    return (f'<section style="position: relative; box-sizing: border-box; padding: {frame}px; background: {WOOD_BG}; border: 1px solid #0E0703; border-radius: 3px; '
            f'box-shadow: 0 12px 26px rgba(0,0,0,0.6), 0 2px 0 rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,214,160,0.32), inset 0 -2px 0 rgba(0,0,0,0.55), '
            f'inset 1px 0 0 rgba(255,214,160,0.14), inset -1px 0 0 rgba(0,0,0,0.5); display: flex; flex-direction: column; {extra}">'
            f'{_overlay(p, "woodgrain", 0.85)}'
            f'<div style="position: relative; flex: 1; min-height: 0; box-sizing: border-box; padding: {pad}; {surf}; overflow: hidden; display: flex; flex-direction: column">'
            f'{tex}<div style="position: relative; z-index: 1; flex: 1; min-height: 0; display: flex; flex-direction: column; gap: {gap}px">{inner}</div></div>'
            f'{corners4(p)}</section>')


def btn(kind, label, k=None, h=50, size=15, extra="", disp=False):
    fam = DISPLAY if disp else BODY
    wt = 800 if disp else 700
    ring = ("0 0 0 1px #1A0C04, 0 0 0 3px #B48A3E, 0 0 0 4px #1A0C04")
    if kind == "red":
        bg = "linear-gradient(180deg, #E8714E 0%, #C8432B 34%, #9A2C18 70%, #6E1C0E 100%)"
        fg, shadow = "#FFF1DC", "0 1px 0 #4A1006, 0 0 8px rgba(0,0,0,0.35)"
        glow = ", 0 8px 20px rgba(200,67,43,0.35)"
    elif kind == "jade":
        bg = "linear-gradient(180deg, #CFEAD6 0%, #8FC7A2 36%, #4E8F68 78%, #2E6248 100%)"
        fg, shadow = "#0E2416", "0 1px 0 rgba(255,255,255,0.45)"
        glow = ", 0 8px 20px rgba(80,160,110,0.3)"
    elif kind == "gold":
        bg = "linear-gradient(180deg, #FFF0B8 0%, #E8C46A 40%, #B48A3E 78%, #7A5A24 100%)"
        fg, shadow = "#2A1606", "0 1px 0 rgba(255,250,220,0.6)"
        glow = ""
    else:  # wood
        bg = "linear-gradient(180deg, #6E4A30 0%, #4A2E1E 45%, #2E1C12 100%)"
        fg, shadow = "#F3E6C8", "0 1px 0 #120A04"
        glow = ""
    kb = ""
    if k:
        kb = (f'<span style="font-family: {MONO}; font-size: 11px; font-weight: 600; padding: 1px 6px; margin-left: 10px; '
              f'border: 1px solid currentColor; border-radius: 2px; opacity: 0.8">{k}</span>')
    return (f'<span style="height: {h}px; box-sizing: border-box; padding: 0 18px; display: inline-flex; align-items: center; justify-content: center; '
            f'gap: 8px; border-radius: 3px; background: {bg}; color: {fg}; font-family: {fam}; font-weight: {wt}; font-size: {size}px; '
            f'letter-spacing: 0.04em; text-shadow: {shadow}; white-space: nowrap; '
            f'box-shadow: {ring}, inset 0 1px 0 rgba(255,240,210,0.55), inset 0 -3px 0 rgba(0,0,0,0.28), 0 4px 0 #120A04{glow}; {extra}">'
            f'{label}{kb}</span>')


def stamp(text, color, mono=False, filled=False):
    fam = MONO if mono else BODY
    if filled:
        return (f'<span style="display: inline-flex; align-items: center; gap: 6px; height: 24px; padding: 0 9px; border-radius: 2px; '
                f'font-family: {fam}; font-size: 12px; font-weight: 700; color: #FFF6E4; background: {color}; '
                f'box-shadow: inset 0 0 0 1px rgba(0,0,0,0.25), inset 0 1px 0 rgba(255,255,255,0.25); white-space: nowrap">{text}</span>')
    return (f'<span style="display: inline-flex; align-items: center; gap: 6px; height: 24px; padding: 0 9px; border-radius: 2px; '
            f'font-family: {fam}; font-size: 12px; font-weight: 700; color: {color}; background: rgba(255,250,236,0.45); '
            f'border: 1.5px solid {color}; white-space: nowrap">{text}</span>')


def dot(color, size=7):
    return (f'<span style="width: {size}px; height: {size}px; flex: none; background: {color}; transform: rotate(45deg); '
            f'box-shadow: 0 0 0 1px rgba(0,0,0,0.25)"></span>')


def kbd(k, dark=False):
    c = "#F3E6C8" if dark else P_INK
    return (f'<span style="font-family: {MONO}; font-size: 11px; padding: 1px 6px; border: 1px solid {c}; color: {c}; '
            f'border-radius: 2px; opacity: 0.8; margin-left: 8px">{k}</span>')


def chip(text, fg, bg, border=None, mono=False):
    """Chip for dark (lacquer) surfaces."""
    fam = MONO if mono else BODY
    b = border or bg
    return (f'<span style="display: inline-flex; align-items: center; gap: 6px; height: 26px; padding: 0 10px; border-radius: 2px; '
            f'font-family: {fam}; font-size: 12px; font-weight: 600; color: {fg}; background: {bg}; border: 1px solid {b}; white-space: nowrap">{text}</span>')


def seal(p, text, size, rotate=-4, color=RED, font_scale=0.56, sub=None):
    """Red seal stamp (낙관) with a roughened edge and worn ink."""
    fs = size * font_scale
    sub_html = ""
    if sub:
        sub_html = (f'<text x="50" y="86" text-anchor="middle" font-family="{MONO}" font-size="8" '
                    f'letter-spacing="1.5" fill="#F7E7D2" opacity="0.85">{sub}</text>')
    ty = 62 if not sub else 58
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 100 100" role="img" aria-label="{text} 낙관" style="flex: none; transform: rotate({rotate}deg); overflow: visible">
      <defs>
        <filter id="{p}-rough" x="-10%" y="-10%" width="120%" height="120%">
          <feTurbulence type="fractalNoise" baseFrequency="0.08" numOctaves="2" seed="3" result="t"></feTurbulence>
          <feDisplacementMap in="SourceGraphic" in2="t" scale="3.2"></feDisplacementMap>
        </filter>
        <filter id="{p}-wear" x="0" y="0" width="100%" height="100%">
          <feTurbulence type="fractalNoise" baseFrequency="0.55" numOctaves="2" seed="11" result="n"></feTurbulence>
          <feColorMatrix in="n" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.3 1.5" result="m"></feColorMatrix>
          <feComposite in="SourceGraphic" in2="m" operator="in"></feComposite>
        </filter>
        <linearGradient id="{p}-ink" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#E05A3E"></stop>
          <stop offset="1" stop-color="{color}"></stop>
        </linearGradient>
      </defs>
      <g filter="url(#{p}-wear)">
        <g filter="url(#{p}-rough)">
          <rect x="6" y="6" width="88" height="88" rx="7" fill="url(#{p}-ink)"></rect>
          <rect x="13" y="13" width="74" height="74" rx="3" fill="none" stroke="#F7E7D2" stroke-width="2.4" opacity="0.9"></rect>
        </g>
        <text x="50" y="{ty}" text-anchor="middle" dominant-baseline="middle" font-family="{DISPLAY}" font-weight="800" font-size="{fs * 100 / size:.1f}" fill="#F7E7D2">{text}</text>
        {sub_html}
      </g>
    </svg>"""


def wordmark(p, compact=False):
    s = 40 if compact else 52
    return (f'<div style="display: flex; align-items: center; gap: 12px">{seal(p + "-wm", "參", s, rotate=-3)}'
            f'<div style="display: flex; flex-direction: column; gap: 5px">'
            f'{gold_text("삼재", 27 if compact else 34)}'
            f'<span style="font-family: {MONO}; font-size: 10px; letter-spacing: 0.32em; color: #C9B48A">SAMJAE</span></div></div>')


def title_bar(p, left, right, h=64):
    """Top bar: a carved lintel beam with a dancheong band underneath."""
    return (f'<header style="position: relative; z-index: 5; height: {h}px; flex: none; box-sizing: border-box; padding: 0 30px; display: flex; '
            f'align-items: center; justify-content: space-between; background: {LACQUER_BG}; '
            f'box-shadow: inset 0 1px 0 rgba(255,214,160,0.2), inset 0 -1px 0 #B48A3E, 0 1px 0 #120A04, 0 8px 20px rgba(0,0,0,0.5)">'
            f'{_overlay(p, "woodgrain", 0.6)}'
            f'<div style="position: relative; z-index: 1; display: flex; align-items: center; gap: 26px">{left}</div>'
            f'<div style="position: relative; z-index: 1; display: flex; align-items: center; gap: 10px">{right}</div></header>'
            f'{dancheong_band(p)}')


def dancheong_band(p, h=12):
    """Repeating dancheong motif strip (green ground, red/yellow/white flowers)."""
    unit = (f'<rect width="32" height="{h}" fill="#2E7A66"></rect>'
            f'<rect y="0" width="32" height="2" fill="#C8432B"></rect><rect y="{h - 2}" width="32" height="2" fill="#1A3A5A"></rect>'
            f'<path d="M16 2.6 L20.4 {h / 2} L16 {h - 2.6} L11.6 {h / 2} Z" fill="#E2B13C"></path>'
            f'<circle cx="16" cy="{h / 2}" r="1.8" fill="#C8432B"></circle>'
            f'<path d="M0 {h / 2} L4 3 L4 {h - 3} Z M32 {h / 2} L28 3 L28 {h - 3} Z" fill="#EFE8D8"></path>'
            f'<path d="M6.5 {h / 2} h3 M22.5 {h / 2} h3" stroke="#EFE8D8" stroke-width="1.4"></path>')
    return (f'<svg width="100%" height="{h}" style="display: block; flex: none; position: relative; z-index: 5; box-shadow: 0 3px 8px rgba(0,0,0,0.5)" aria-hidden="true">'
            f'<defs><pattern id="{p}-dc" width="32" height="{h}" patternUnits="userSpaceOnUse">{unit}</pattern></defs>'
            f'<rect width="100%" height="{h}" fill="url(#{p}-dc)"></rect>'
            f'<rect width="100%" height="1" fill="#120A04"></rect><rect y="{h - 1}" width="100%" height="1" fill="#120A04"></rect></svg>')


def lacquer_slot(value, focus=False, mono=True, h=38):
    """Recessed lacquer input on hanji."""
    fam = MONO if mono else BODY
    ring = ("0 0 0 2px #E2B13C, 0 0 10px rgba(226,177,60,0.55)," if focus else "")
    caret = '<span style="width: 1.5px; height: 16px; background: #F2D58A; margin-left: 1px"></span>' if focus else ""
    return (f'<span style="height: {h}px; box-sizing: border-box; display: flex; align-items: center; padding: 0 12px; border-radius: 2px; '
            f'background: linear-gradient(180deg, #160C06, #2A1A10); box-shadow: {ring} inset 0 2px 4px rgba(0,0,0,0.7), 0 1px 0 rgba(255,248,226,0.7), 0 0 0 1px #4A2E14; '
            f'font-family: {fam}; font-size: 14px; color: #F3E6C8">{value}{caret}</span>')


# --------------------------------------------------------------------------
# Crest (unchanged motif, used in small spots)
# --------------------------------------------------------------------------

def crest(p, race, size, dim=False):
    gray = ' style="filter: grayscale(0.85) brightness(0.7)"' if dim else ""
    if race == "r1":
        art = f"""
      <defs>
        <linearGradient id="{p}-wood" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6A4330"></stop><stop offset="0.5" stop-color="#4A2E1E"></stop><stop offset="1" stop-color="#24170F"></stop></linearGradient>
        <radialGradient id="{p}-sun" cx="0.38" cy="0.34" r="0.75"><stop offset="0" stop-color="#F7B48E"></stop><stop offset="0.45" stop-color="#D9532E"></stop><stop offset="1" stop-color="#7A2614"></stop></radialGradient>
      </defs>
      <rect x="7" y="7" width="48" height="48" rx="5" fill="url(#{p}-wood)" stroke="#C9A35A" stroke-width="2.4"></rect>
      <circle cx="31" cy="31" r="16.5" fill="url(#{p}-sun)" stroke="#C9A35A" stroke-width="2"></circle>
      <ellipse cx="31" cy="31" rx="22" ry="7.5" fill="none" stroke="#E2C078" stroke-width="1.8" transform="rotate(-28 31 31)"></ellipse>
      <path d="M31 17 L33.4 31 L31 45 L28.6 31 Z" fill="#EFE8D8" opacity="0.92"></path>
      <circle cx="31" cy="31" r="3.4" fill="#3E8F7A" stroke="#EFE8D8" stroke-width="1.2"></circle>"""
    elif race == "r2":
        art = f"""
      <defs>
        <radialGradient id="{p}-fire" cx="0.5" cy="0.68" r="0.62"><stop offset="0" stop-color="#F2FBFF"></stop><stop offset="0.3" stop-color="#A8DDF5"></stop><stop offset="0.7" stop-color="#4FB3E8"></stop><stop offset="1" stop-color="#1B3242"></stop></radialGradient>
      </defs>
      <path d="M31 6 L54 18 L54 42 L31 56 L8 42 L8 18 Z" fill="#12222E" stroke="#4FB3E8" stroke-width="1.8" stroke-linejoin="round"></path>
      <path d="M21 27 C14 24 11 17 13 10 C16 16 20 19 25 22 Z" fill="#D8B266"></path>
      <path d="M41 27 C48 24 51 17 49 10 C46 16 42 19 37 22 Z" fill="#D8B266"></path>
      <path d="M31 16 C37 23 42 29 41 38 C40 45 36 49 31 49 C26 49 22 45 21 38 C20 29 25 23 31 16 Z" fill="url(#{p}-fire)"></path>
      <circle cx="27.5" cy="35" r="1.4" fill="#12222E"></circle><circle cx="34.5" cy="35" r="1.4" fill="#12222E"></circle>"""
    else:
        art = """<path d="M6 52 H58 L61 60 H3 Z" fill="#6E665A" stroke="#2A241C" stroke-width="1.2"></path><path d="M6 52 H58" stroke="#B4AA98" stroke-width="1.2"></path><path d="M14 52 V16" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M15 17 L22 20 L15 28 Z" fill="#2F6E8E" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="14" cy="15" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M23 52 V10" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M24 11 L31 14 L24 22 Z" fill="#C8432B" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="23" cy="9" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M32 52 V4" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M33 5 L40 8 L33 16 Z" fill="#E2B13C" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="32" cy="3" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M41 52 V10" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M42 11 L49 14 L42 22 Z" fill="#EFE8D8" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="41" cy="9" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M50 52 V16" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M51 17 L58 20 L51 28 Z" fill="#1A1814" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="50" cy="15" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M9 40 Q32 47 55 40" fill="none" stroke="#C8B88E" stroke-width="1.5"></path><path d="M16.2 42.6 h3.6 l-1 6 h-1.6 Z" fill="#F6F0DE" stroke="#6A6050" stroke-width="0.4"></path><path d="M30.2 44.6 h3.6 l-1 6 h-1.6 Z" fill="#F6F0DE" stroke="#6A6050" stroke-width="0.4"></path><path d="M44.2 42.6 h3.6 l-1 6 h-1.6 Z" fill="#F6F0DE" stroke="#6A6050" stroke-width="0.4"></path>"""
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 64 64" role="img" aria-label="{RACES[race]["ko"]} 문장"{gray}>'
            f'{art}</svg>')


# --------------------------------------------------------------------------
# Painted busts (viewBox 0 0 120 140, light from top-left)
# --------------------------------------------------------------------------

def _bust_r1(p):
    o = []
    # robe + folds
    o.append(f'<path d="M4 140 C6 120 14 108 30 102 C40 98 48 96 60 96 C72 96 80 98 90 102 C106 108 114 120 116 140 Z" fill="url(#{p}-bu-r1robe)"></path>')
    for d in ("M26 112 C31 124 29 132 31 140", "M94 112 C89 124 91 132 89 140", "M16 126 C18 120 20 116 24 112", "M104 126 C102 120 100 116 96 112"):
        o.append(f'<path d="{d}" fill="none" stroke="#4A1006" stroke-opacity="0.55" stroke-width="1.6" stroke-linecap="round"></path>')
    o.append('<path d="M14 120 C18 112 24 106 32 103" fill="none" stroke="#FFC0A0" stroke-opacity="0.55" stroke-width="1.6" stroke-linecap="round"></path>')
    # vest with green trim and gold studs
    o.append(f'<path d="M40 100 C46 112 50 126 50 140 H70 C70 126 74 112 80 100 C74 98 66 97 60 97 C54 97 46 98 40 100 Z" fill="url(#{p}-bu-r1vest)"></path>')
    o.append('<path d="M40 100 C46 112 50 126 50 140 M80 100 C74 112 70 126 70 140" fill="none" stroke="#3E8F7A" stroke-width="3"></path>')
    for t in range(9):
        u = t / 8
        x1 = 40 + 10 * u + 1.4 * math.sin(u * 3)
        y1 = 100 + 40 * u
        o.append(f'<circle cx="{x1 + 2:.1f}" cy="{y1:.1f}" r="0.9" fill="#E2C078"></circle><circle cx="{118 - x1 + 2 - 4:.1f}" cy="{y1:.1f}" r="0.9" fill="#E2C078"></circle>')
    # collar
    o.append('<path d="M48 97.5 L60 113 L72 97.5" fill="none" stroke="#5A160A" stroke-width="5.4" stroke-linejoin="round"></path>')
    o.append('<path d="M48 97.5 L60 113 L72 97.5" fill="none" stroke="#EFE8D8" stroke-width="3" stroke-linejoin="round"></path>')
    # gear badge
    teeth = "".join(f'<rect x="58.6" y="114.6" width="2.8" height="3.4" fill="url(#{p}-brass)" transform="rotate({a} 60 124)"></rect>' for a in range(0, 360, 30))
    o.append(teeth)
    o.append(f'<circle cx="60" cy="124" r="7" fill="url(#{p}-brass)" stroke="#3A2408" stroke-width="0.8"></circle>'
             f'<circle cx="60" cy="124" r="3.4" fill="#2A1A10"></circle><circle cx="60" cy="124" r="1.3" fill="#9FE3D0"></circle>'
             f'<path d="M55.4 120.6 A6 6 0 0 1 61 118.2" fill="none" stroke="#FFF2C8" stroke-width="0.8" opacity="0.8"></path>')
    # pauldron discs
    for cx, rot in ((24, -24), (96, 24)):
        o.append(f'<ellipse cx="{cx}" cy="110" rx="11" ry="6.4" fill="url(#{p}-brass)" stroke="#3A2408" stroke-width="0.8" transform="rotate({rot} {cx} 110)"></ellipse>'
                 f'<ellipse cx="{cx}" cy="110" rx="6" ry="3.2" fill="none" stroke="#6A4A1A" stroke-width="0.9" transform="rotate({rot} {cx} 110)"></ellipse>'
                 f'<circle cx="{cx}" cy="110" r="1.4" fill="url(#{p}-rivet)"></circle>')
    # neck & head
    o.append(f'<path d="M50 82 C50 92 52 98 60 99 C68 98 70 92 70 82 Z" fill="url(#{p}-bu-neck)"></path>')
    o.append('<path d="M51 92 C55 96 65 96 69 92" fill="none" stroke="#6A3A20" stroke-opacity="0.45" stroke-width="2"></path>')
    o.append(f'<ellipse cx="40.4" cy="63" rx="3.6" ry="6.4" fill="#D29A6C"></ellipse><ellipse cx="79.6" cy="63" rx="3.6" ry="6.4" fill="#B47A50"></ellipse>')
    o.append('<path d="M40 60 C39 63 40 66 41.6 67" fill="none" stroke="#8A5232" stroke-width="0.9"></path>')
    o.append(f'<path d="M41 58 C41 44 49 37 60 37 C71 37 79 44 79 58 C79 72 73 84 66 88 C63 90 57 90 54 88 C47 84 41 72 41 58 Z" fill="url(#{p}-bu-skinA)"></path>')
    o.append('<path d="M70 58 C76 68 74 82 66 88 C70 80 72 70 70 58 Z" fill="#7A4226" opacity="0.35"></path>')
    o.append('<ellipse cx="49" cy="71" rx="5" ry="2.8" fill="#D9705A" opacity="0.22"></ellipse><ellipse cx="71" cy="71" rx="5" ry="2.8" fill="#D9705A" opacity="0.2"></ellipse>')
    o.append('<path d="M42.4 52 C43 46 47 41.4 52 39.6" fill="none" stroke="#FFF0D8" stroke-opacity="0.6" stroke-width="1.4" stroke-linecap="round"></path>')
    # eyes, brows
    for ex, s in ((51, 1), (69, -1)):
        o.append(f'<ellipse cx="{ex}" cy="62.6" rx="3.8" ry="1.9" fill="#F4EDE0"></ellipse>'
                 f'<circle cx="{ex + 0.6 * s:.1f}" cy="62.6" r="1.7" fill="#2A1A10"></circle>'
                 f'<circle cx="{ex + 0.1 * s:.1f}" cy="62" r="0.55" fill="#FFF"></circle>'
                 f'<path d="M{ex - 4.2} 62 Q{ex} 59.4 {ex + 4.2} 62" fill="none" stroke="#2A1A10" stroke-width="1.3" stroke-linecap="round"></path>'
                 f'<path d="M{ex - 6 * s:.1f} 57 Q{ex - 0.5 * s:.1f} 53.4 {ex + 5.6 * s:.1f} 56" fill="none" stroke="#1F150E" stroke-width="2.6" stroke-linecap="round"></path>')
    o.append('<path d="M60 62.5 C59 68 57.4 71 58.2 73 C59.2 74.6 62 74.6 63.2 73" fill="none" stroke="#A06640" stroke-width="1.2" stroke-linecap="round"></path>'
             '<path d="M58.6 63.4 L57.8 69.6" stroke="#FFE6C8" stroke-opacity="0.65" stroke-width="1"></path>')
    o.append('<path d="M49.6 78.4 C54 75.2 58 75.6 60 77.2 C62 75.6 66 75.2 70.4 78.4 C66 77.8 63 78.8 60 79.6 C57 78.8 54 77.8 49.6 78.4 Z" fill="#1F150E"></path>'
             '<path d="M57 84 Q60 90.4 63 84 Z" fill="#1F150E"></path>'
             '<path d="M55 81.4 Q60 83 65 81.4" fill="none" stroke="#6A2E1E" stroke-width="1.1"></path>')
    # hat brim shadow on forehead
    o.append('<path d="M42 48 C50 52 70 52 78 48 L78 51 C70 55 50 55 42 51 Z" fill="#3A1E10" opacity="0.35"></path>')
    # hat: brim + crown + brass band + goggles + red tassel
    o.append(f'<ellipse cx="60" cy="45" rx="35" ry="9.4" fill="url(#{p}-bu-felt)" stroke="#000" stroke-opacity="0.5" stroke-width="0.6"></ellipse>')
    o.append('<path d="M27 43.4 C34 38.6 46 36.4 60 36.4 C74 36.4 86 38.6 93 43.4" fill="none" stroke="#8A8078" stroke-opacity="0.6" stroke-width="0.9"></path>')
    o.append(f'<path d="M44 45 C44 31 50 23 60 23 C70 23 76 31 76 45 Z" fill="url(#{p}-bu-felt)"></path>')
    o.append('<path d="M47 40 C47.4 32 51 26.4 57 24.6" fill="none" stroke="#9A928A" stroke-opacity="0.5" stroke-width="1.2" stroke-linecap="round"></path>')
    o.append(f'<path d="M44.4 39.6 C52 42.4 68 42.4 75.6 39.6 L75.9 43.8 C68 46.4 52 46.4 44.1 43.8 Z" fill="url(#{p}-brass)" stroke="#3A2408" stroke-width="0.5"></path>')
    for gx in (52, 68):
        o.append(f'<circle cx="{gx}" cy="38.4" r="5.6" fill="url(#{p}-brass)" stroke="#2A1806" stroke-width="0.7"></circle>'
                 f'<circle cx="{gx}" cy="38.4" r="3.8" fill="url(#{p}-bu-lens)"></circle>'
                 f'<path d="M{gx - 2.4} 36.8 A2.8 2.8 0 0 1 {gx} 35.4" fill="none" stroke="#FFF" stroke-width="0.9" opacity="0.9"></path>')
    o.append(f'<rect x="57" y="37.2" width="6" height="2.4" fill="url(#{p}-brass)"></rect>')
    rng = random.Random(7)
    strands = []
    for i in range(16):
        a = -1.25 + i * 0.165
        L = 11 + rng.random() * 5
        ex, ey = 60 + math.sin(a) * L, 20 - math.cos(a) * L * 0.75
        c = "#E0563A" if i % 3 else "#9E2614"
        strands.append(f'<path d="M60 21 Q{60 + math.sin(a) * L * 0.4:.1f} {14 - rng.random() * 2:.1f} {ex:.1f} {ey:.1f}" fill="none" stroke="{c}" stroke-width="1.5" stroke-linecap="round"></path>')
    o.append("".join(strands))
    o.append(f'<circle cx="60" cy="22" r="2.6" fill="url(#{p}-brass)" stroke="#3A2408" stroke-width="0.5"></circle>')
    return "".join(o)


def _bust_r2(p):
    o = []
    # floating goblin-fire wisp (behind shoulder)
    o.append(f'<circle cx="101" cy="72" r="15" fill="url(#{p}-glowb)" opacity="0.9"></circle>')
    o.append(f'<path d="M101 60 C106 66 109 70 108.4 75 C107.8 80 104.6 83 101 83 C97.4 83 94.2 80 93.6 75 C93 70 96 66 101 60 Z" fill="url(#{p}-bu-fire)"></path>'
             '<circle cx="98.6" cy="75" r="1" fill="#12222E"></circle><circle cx="103.4" cy="75" r="1" fill="#12222E"></circle>')
    # shoulders
    o.append(f'<path d="M2 140 C4 118 16 104 34 98 C44 95 52 94 60 94 C68 94 76 95 86 98 C104 104 116 118 118 140 Z" fill="url(#{p}-bu-bodyB)"></path>')
    for d in ("M34 112 C44 119 53 119 58 114", "M86 112 C76 119 67 119 62 114", "M58 124 V140", "M22 128 C24 122 26 118 30 114"):
        o.append(f'<path d="{d}" fill="none" stroke="#0C0E22" stroke-opacity="0.75" stroke-width="1.6" stroke-linecap="round"></path>')
    o.append('<path d="M12 122 C18 110 26 103 38 99" fill="none" stroke="#A8B8F0" stroke-opacity="0.5" stroke-width="1.8" stroke-linecap="round"></path>')
    # tiger-skin sash
    o.append(f'<path d="M72 96 C84 98 98 104 106 116 L118 140 H96 C92 128 86 116 68 104 Z" fill="url(#{p}-bu-tiger)"></path>')
    for i, (x, y) in enumerate(((78, 101), (86, 106), (93, 113), (99, 121), (104, 130), (108, 137))):
        o.append(f'<path d="M{x - 4} {y + 4} Q{x} {y - 1} {x + 5} {y + 1} Q{x + 1} {y + 2.6} {x - 4} {y + 4} Z" fill="#1A0E06"></path>')
    o.append('<path d="M72 96 C84 98 98 104 106 116" fill="none" stroke="#FFD490" stroke-opacity="0.6" stroke-width="1"></path>')
    # neck
    o.append(f'<path d="M44 80 C44 90 48 96 60 97 C72 96 76 90 76 80 Z" fill="url(#{p}-bu-neckB)"></path>')
    # bead necklace + fang pendant
    beads = []
    for i in range(15):
        t = i / 14
        x = 40 + 40 * t
        y = 98 + 13 * math.sin(math.pi * t)
        c = "#8A2E1E" if i % 2 else "#D8B266"
        beads.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.5" fill="{c}" stroke="#120A04" stroke-width="0.5"></circle>'
                     f'<circle cx="{x - 0.8:.1f}" cy="{y - 0.8:.1f}" r="0.7" fill="#FFF" opacity="0.6"></circle>')
    o.append("".join(beads))
    o.append('<path d="M56.6 111 C57 118 59 122 60 124 C61 122 63 118 63.4 111 Z" fill="#F4E6C4" stroke="#6A4A1A" stroke-width="0.6"></path>')
    # hair behind head
    hair = []
    rng = random.Random(3)
    for i in range(15):
        a = math.radians(196 + i * 10.6)
        r1 = 26
        r2 = 36 + rng.random() * 7
        cx, cy = 60, 56
        a0, a1 = a - 0.11, a + 0.11
        pts = [(cx + math.cos(a0) * r1, cy + math.sin(a0) * r1), (cx + math.cos(a) * r2, cy + math.sin(a) * r2),
               (cx + math.cos(a1) * r1, cy + math.sin(a1) * r1)]
        hair.append("M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z")
    o.append(f'<path d="{" ".join(hair)}" fill="#0E0E18"></path>')
    # ears with gold hoops
    # head
    o.append(f'<path d="M34 58 C34 40 46 31 60 31 C74 31 86 40 86 58 C86 74 80 86 68 90 C64 92 56 92 52 90 C40 86 34 74 34 58 Z" fill="url(#{p}-bu-skinB)"></path>')
    o.append('<path d="M84 50 C86 60 84 72 78 82" fill="none" stroke="#A8DDF5" stroke-opacity="0.8" stroke-width="1.8" stroke-linecap="round"></path>')
    o.append('<path d="M37 53 L19 44 L35 70 Z" fill="#5A6CB4" stroke="#141732" stroke-width="0.8"></path><path d="M34 56 L25 50 L34 64" fill="none" stroke="#2B2F55" stroke-width="1.2"></path>'
             '<path d="M83 53 L101 44 L85 70 Z" fill="#2E3870" stroke="#141732" stroke-width="0.8"></path><path d="M86 56 L95 50 L86 64" fill="none" stroke="#141732" stroke-width="1.2"></path>')
    o.append('<circle cx="25.6" cy="60" r="3.8" fill="none" stroke="#E2B13C" stroke-width="1.7"></circle><path d="M22.6 58 A3.8 3.8 0 0 1 25 56.3" fill="none" stroke="#FFF2C8" stroke-width="0.8"></path>')
    # hair fringe on top
    fr = []
    for i in range(9):
        x = 40 + i * 5
        fr.append(f"M{x} 40 L{x + 3.5} {30 - (i % 3) * 3} L{x + 6} 41 Z")
    o.append(f'<path d="{" ".join(fr)}" fill="#0E0E18"></path>')
    # horns
    o.append(f'<path d="M45 38 C38 30 34 19 37 6 C41 17 47 25 53 32 Z" fill="url(#{p}-bu-horn)" stroke="#3A2408" stroke-width="0.7"></path>'
             f'<path d="M75 38 C82 30 86 19 83 6 C79 17 73 25 67 32 Z" fill="url(#{p}-bu-horn)" stroke="#3A2408" stroke-width="0.7"></path>')
    for y in (28, 22, 16):
        o.append(f'<path d="M{37 + (32 - y) * 0.1:.1f} {y + 2} q4 -1.5 7 1" fill="none" stroke="#6A4A1A" stroke-width="0.9"></path>'
                 f'<path d="M{83 - (32 - y) * 0.1:.1f} {y + 2} q-4 -1.5 -7 1" fill="none" stroke="#6A4A1A" stroke-width="0.9"></path>')
    # brows, glowing eyes
    o.append('<path d="M40 54 Q48 46 58 52" fill="none" stroke="#0E0E18" stroke-width="4.4" stroke-linecap="round"></path>'
             '<path d="M80 54 Q72 46 62 52" fill="none" stroke="#0E0E18" stroke-width="4.4" stroke-linecap="round"></path>')
    for ex in (49.5, 70.5):
        o.append(f'<ellipse cx="{ex}" cy="60" rx="6.6" ry="5.4" fill="#4FB3E8" opacity="0.55" filter="url(#{p}-blur1)"></ellipse>'
                 f'<ellipse cx="{ex}" cy="60" rx="5.8" ry="4.8" fill="#F6FBF0"></ellipse>'
                 f'<circle cx="{ex}" cy="60.2" r="3" fill="#F2C94C"></circle>'
                 f'<ellipse cx="{ex}" cy="60.2" rx="0.9" ry="2.5" fill="#120A04"></ellipse>'
                 f'<circle cx="{ex - 1.3}" cy="58.8" r="0.8" fill="#FFF"></circle>')
    # nose
    o.append('<path d="M55 63 C54 69 51.6 72 54 74.4 C56 76.4 58 74.4 60 75.4 C62 74.4 64 76.4 66 74.4 C68.4 72 66 69 65 63" fill="#3A4892" stroke="#141732" stroke-width="0.9"></path>'
             '<circle cx="56.4" cy="73" r="1.1" fill="#0C0E22"></circle><circle cx="63.6" cy="73" r="1.1" fill="#0C0E22"></circle>'
             '<path d="M57 64 L56 70" stroke="#A8B8F0" stroke-opacity="0.6" stroke-width="1"></path>')
    # grin with teeth and fangs
    o.append('<path d="M40 77 C46 89 74 89 80 77 C72 82 48 82 40 77 Z" fill="#2A0E14"></path>')
    teeth = []
    for i in range(9):
        t = i / 8
        x = 44 + 32 * t
        y = 79.6 + 2.6 * math.sin(math.pi * t)
        teeth.append(f'<rect x="{x - 1.6:.1f}" y="{y - 0.6:.1f}" width="3.2" height="3" rx="0.6" fill="#F6F0DC"></rect>')
    o.append("".join(teeth))
    o.append('<path d="M47 82 L49 76.4 L51 83 Z M73 82 L71 76.4 L69 83 Z" fill="#FFF8E6" stroke="#6A4A1A" stroke-width="0.4"></path>')
    o.append('<path d="M38 75 C39 78 41 79 42 79 M82 75 C81 78 79 79 78 79" fill="none" stroke="#141732" stroke-width="1.2"></path>')
    return "".join(o)


def _bust_r3(p):
    o = []
    # back-frame (jige) poles behind the shoulders
    for x0, x1 in ((30, 25), (90, 95)):
        o.append(f'<path d="M{x0 - 2.6} 140 L{x1 - 2.6} 52 L{x1 + 2.6} 50 L{x0 + 2.6} 140 Z" fill="url(#{p}-bu-pole)" stroke="#1A0E06" stroke-width="0.6"></path>')
    o.append(f'<path d="M23 66 L97 64 L97 69 L23 71 Z" fill="url(#{p}-bu-pole)" stroke="#1A0E06" stroke-width="0.6"></path>')
    o.append('<path d="M22.4 50 L18 40 M97.6 48 L102 38" stroke="#5A3A22" stroke-width="4" stroke-linecap="round"></path>')
    # hemp jacket
    o.append(f'<path d="M6 140 C8 120 18 106 32 100 C42 96 50 95 60 95 C70 95 78 96 88 100 C102 106 112 120 114 140 Z" fill="url(#{p}-bu-hemp)"></path>')
    for d in ("M22 116 C26 126 24 134 26 140", "M98 116 C94 126 96 134 94 140", "M14 128 C16 122 18 118 22 114"):
        o.append(f'<path d="{d}" fill="none" stroke="#7A6A48" stroke-opacity="0.7" stroke-width="1.4" stroke-linecap="round"></path>')
    o.append('<path d="M12 122 C18 112 24 106 34 101" fill="none" stroke="#FFFBEE" stroke-opacity="0.7" stroke-width="1.4"></path>')
    # indigo vest
    o.append(f'<path d="M38 100 C44 112 48 126 48 140 H72 C72 126 76 112 82 100 C76 98 68 97 60 97 C52 97 44 98 38 100 Z" fill="url(#{p}-bu-indigo)"></path>')
    o.append('<path d="M48 97.5 L60 112 L72 97.5" fill="none" stroke="#F6F0DE" stroke-width="3" stroke-linejoin="round"></path>')
    # rope straps with twist marks
    for d in ("M33 99 C36 112 40 126 42 140", "M87 99 C84 112 80 126 78 140"):
        o.append(f'<path d="{d}" fill="none" stroke="#5E5038" stroke-width="6" stroke-linecap="round"></path>'
                 f'<path d="{d}" fill="none" stroke="#A8966A" stroke-width="4.2" stroke-linecap="round" stroke-dasharray="2.4 2"></path>')
    # brass bell on cord
    o.append('<path d="M60 112 L60 120" stroke="#8A2E1E" stroke-width="1.2"></path>')
    o.append(f'<path d="M54 129 C54 122 56.4 119.6 60 119.6 C63.6 119.6 66 122 66 129 L67.4 130.6 H52.6 Z" fill="url(#{p}-brass)" stroke="#3A2408" stroke-width="0.7"></path>'
             '<circle cx="60" cy="132" r="1.6" fill="#3A2408"></circle><path d="M56 124 C56.4 122 57.6 121 59 120.8" fill="none" stroke="#FFF6D8" stroke-width="0.9"></path>')
    # neck, ears, head
    o.append(f'<path d="M50 82 C50 92 52 98 60 99 C68 98 70 92 70 82 Z" fill="url(#{p}-bu-neck)"></path>')
    o.append('<ellipse cx="40.6" cy="63" rx="3.6" ry="6.2" fill="#C88E5E"></ellipse><ellipse cx="79.4" cy="63" rx="3.6" ry="6.2" fill="#A06A40"></ellipse>')
    o.append(f'<path d="M41 58 C41 44 49 37 60 37 C71 37 79 44 79 58 C79 73 73 84 66 88 C63 90 57 90 54 88 C47 84 41 73 41 58 Z" fill="url(#{p}-bu-skinC)"></path>')
    o.append('<path d="M70 58 C76 68 74 82 66 88 C70 80 72 70 70 58 Z" fill="#6A3A1E" opacity="0.35"></path>')
    rng = random.Random(11)
    o.append("".join(f'<circle cx="{52 + rng.random() * 16:.1f}" cy="{80 + rng.random() * 8:.1f}" r="0.45" fill="#4A2A16" opacity="0.5"></circle>' for _ in range(26)))
    o.append('<path d="M42.4 52 C43 46 47 41.4 52 39.6" fill="none" stroke="#FFF0D8" stroke-opacity="0.6" stroke-width="1.4" stroke-linecap="round"></path>')
    # smiling closed eyes + crow's feet
    for ex, s in ((51, 1), (69, -1)):
        o.append(f'<path d="M{ex - 4.2} 63 Q{ex} 59.4 {ex + 4.2} 63" fill="none" stroke="#2A1A10" stroke-width="1.7" stroke-linecap="round"></path>'
                 f'<path d="M{ex + 6 * s * -1:.1f} 61 l{-2.4 * s:.1f} -1.2 M{ex + 6 * s * -1:.1f} 63.6 l{-2.6 * s:.1f} 0.6" fill="none" stroke="#8A5232" stroke-width="0.8"></path>'
                 f'<path d="M{ex - 5.6 * s:.1f} 56.4 Q{ex:.1f} 53.6 {ex + 5.2 * s:.1f} 55.8" fill="none" stroke="#3A2414" stroke-width="2" stroke-linecap="round"></path>')
    o.append('<ellipse cx="48.6" cy="70" rx="5" ry="2.8" fill="#D9705A" opacity="0.28"></ellipse><ellipse cx="71.4" cy="70" rx="5" ry="2.8" fill="#D9705A" opacity="0.24"></ellipse>')
    o.append('<path d="M60 62.5 C59 68 57.4 71 58.2 73 C59.2 74.6 62 74.6 63.2 73" fill="none" stroke="#8E5632" stroke-width="1.2" stroke-linecap="round"></path>')
    o.append('<path d="M51.6 77.6 C55.6 76 58 76.4 60 77.4 C62 76.4 64.4 76 68.4 77.6" fill="none" stroke="#2A1A10" stroke-width="1.7" stroke-linecap="round"></path>'
             '<path d="M53.6 80.6 Q60 86 66.4 80.6" fill="#7A2E1E" stroke="#4A1A0E" stroke-width="0.8"></path>'
             '<path d="M55.4 81.2 Q60 83.2 64.6 81.2" fill="none" stroke="#F6EEDC" stroke-width="1.1"></path>'
             '<path d="M50 76 C49 79 50 82 52 83 M70 76 C71 79 70 82 68 83" fill="none" stroke="#8A5232" stroke-width="0.9"></path>')
    # chin strap
    o.append('<path d="M33 47 C37 70 48 86 60 90 C72 86 83 70 87 47" fill="none" stroke="#2A1A10" stroke-opacity="0.7" stroke-width="0.9"></path>')
    # bamboo hat (paeraengi): brim + crown with weave + cotton bolls
    o.append('<path d="M42 49 C50 53 70 53 78 49 L78 52 C70 56 50 56 42 52 Z" fill="#3A1E10" opacity="0.32"></path>')
    o.append(f'<ellipse cx="60" cy="46" rx="31" ry="8.4" fill="url(#{p}-bu-bamboo)" stroke="#5A3A16" stroke-width="0.7"></ellipse>')
    weave = "".join(f'<path d="M60 46 L{60 + 31 * math.cos(math.radians(a)):.1f} {46 + 8.4 * math.sin(math.radians(a)):.1f}" stroke="#7E5A2A" stroke-opacity="0.45" stroke-width="0.6"></path>'
                    for a in range(0, 360, 12))
    o.append(weave)
    o.append('<ellipse cx="60" cy="46" rx="25" ry="6.4" fill="none" stroke="#7E5A2A" stroke-opacity="0.5" stroke-width="0.6"></ellipse>')
    o.append(f'<path d="M43 46 C43.6 35 48.6 28.6 60 28.6 C71.4 28.6 76.4 35 77 46 Z" fill="url(#{p}-bu-bamboo)" stroke="#5A3A16" stroke-width="0.7"></path>')
    o.append("".join(f'<path d="M{44 + i * 2.6:.1f} 46 Q{46 + i * 2.2:.1f} 36 {50 + i * 1.2:.1f} 29.6" fill="none" stroke="#7E5A2A" stroke-opacity="0.5" stroke-width="0.6"></path>'
                     for i in range(13)))
    o.append('<path d="M43.6 40 C52 42 68 42 76.4 40 M44.6 35 C52 37 68 37 75.4 35" fill="none" stroke="#5A3A16" stroke-opacity="0.6" stroke-width="0.8"></path>')
    o.append('<path d="M46 43 C46.4 36 50 31 56 29.4" fill="none" stroke="#FFF6D8" stroke-opacity="0.6" stroke-width="1.2" stroke-linecap="round"></path>')
    for bx, by in ((78, 38), (42, 38)):
        o.append("".join(f'<circle cx="{bx + dx}" cy="{by + dy}" r="{r}" fill="{c}"></circle>'
                         for dx, dy, r, c in ((0, 0, 3.6, "#D8D2C4"), (-2.4, -2, 3, "#F6F2E8"), (2.4, -1.6, 3, "#FBF8F0"), (0, -3.6, 2.8, "#FFFFFF"), (-0.6, -1.4, 1, "#C9C0AC"))))
    return "".join(o)


BUSTS = {"r1": _bust_r1, "r2": _bust_r2, "r3": _bust_r3}


def medallion(p, inst, race, size, state="normal"):
    """Painted bust in a brass ring medallion. state: normal | selected | dim."""
    cid = f"{p}-{inst}-clip"
    rivets = "".join(f'<circle cx="{60 + 55 * math.cos(math.radians(a)):.1f}" cy="{60 + 55 * math.sin(math.radians(a)):.1f}" r="1.9" '
                     f'fill="url(#{p}-rivet)" stroke="#2A1806" stroke-width="0.4"></circle>' for a in range(0, 360, 45))
    filt = ' style="filter: grayscale(0.75) brightness(0.62); flex: none"' if state == "dim" else ' style="flex: none; overflow: visible"'
    sel = ""
    if state == "selected":
        sel = (f'<circle cx="60" cy="60" r="62" fill="none" stroke="#FFE6A0" stroke-width="3" opacity="0.9" filter="url(#{p}-blur1)"></circle>'
               f'<circle cx="60" cy="60" r="60.5" fill="none" stroke="#FFF2C8" stroke-width="1.4"></circle>')
    col = RACES[race]["color"]
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 120 120" role="img" aria-label="{RACES[race]["ko"]} 초상"{filt}>'
            f'<defs><clipPath id="{cid}"><circle cx="60" cy="60" r="50"></circle></clipPath></defs>'
            f'<circle cx="62" cy="64" r="58" fill="#000" opacity="0.5" filter="url(#{p}-soft)"></circle>'
            f'{sel}'
            f'<circle cx="60" cy="60" r="58" fill="url(#{p}-brass)" stroke="#2A1806" stroke-width="1"></circle>'
            f'<circle cx="60" cy="60" r="52.6" fill="#1A0E06"></circle>'
            f'<circle cx="60" cy="60" r="50" fill="url(#{p}-mg-{race})"></circle>'
            f'<g clip-path="url(#{cid})"><g transform="translate(0 -4)">{BUSTS[race](p)}</g>'
            f'<circle cx="60" cy="60" r="50" fill="none" stroke="#000" stroke-opacity="0.5" stroke-width="5"></circle></g>'
            f'<path d="M14 46 A48 48 0 0 1 46 13" fill="none" stroke="#FFF6D8" stroke-opacity="0.7" stroke-width="1.6" stroke-linecap="round"></path>'
            f'{rivets}'
            f'<path d="M60 108 L66 114 L60 120 L54 114 Z" fill="{col}" stroke="#2A1806" stroke-width="1"></path>'
            f'<path d="M57 113 L60 110 L62 112" fill="none" stroke="#FFF" stroke-opacity="0.7" stroke-width="0.9"></path></svg>')


def portrait(p, inst, race, w=168, h=196):
    """Large painted bust in an arched lacquer-and-brass frame."""
    cid = f"{p}-{inst}-arch"
    col = RACES[race]["color"]
    arch = "M10 196 V60 C10 30 34 10 84 10 C134 10 158 30 158 60 V196 Z"
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 168 206" role="img" aria-label="{RACES[race]["ko"]} 초상화" style="flex: none; display: block">'
            f'<defs><clipPath id="{cid}"><path d="{arch}"></path></clipPath></defs>'
            f'<path d="M4 206 V58 C4 24 32 2 84 2 C136 2 164 24 164 58 V206 Z" fill="url(#{p}-woodv)" stroke="#0E0703" stroke-width="1.4"></path>'
            f'<path d="M7 204 V59 C7 27 33 6 84 6" fill="none" stroke="#FFD8A0" stroke-opacity="0.28" stroke-width="1"></path>'
            f'<path d="{arch}" fill="url(#{p}-mg-{race})"></path>'
            f'<g clip-path="url(#{cid})">'
            f'<circle cx="84" cy="60" r="70" fill="{col}" opacity="0.16" filter="url(#{p}-blur14)"></circle>'
            f'<g transform="translate(12 36) scale(1.2)">{BUSTS[race](p)}</g>'
            f'<path d="M10 150 H158 V196 H10 Z" fill="#000" opacity="0.18"></path></g>'
            f'<path d="{arch}" fill="none" stroke="url(#{p}-brass)" stroke-width="3"></path>'
            f'<path d="{arch}" fill="none" stroke="#000" stroke-opacity="0.45" stroke-width="7" clip-path="url(#{cid})"></path>'
            f'<path d="M84 2 L92 10 L84 18 L76 10 Z" fill="{col}" stroke="#2A1806" stroke-width="1"></path>'
            f'<circle cx="17" cy="198" r="2.2" fill="url(#{p}-rivet)"></circle><circle cx="151" cy="198" r="2.2" fill="url(#{p}-rivet)"></circle></svg>')


# --------------------------------------------------------------------------
# Isometric projector + small painted-scene helpers
# --------------------------------------------------------------------------

class Iso:
    """2:1 dimetric projection. x runs down-right, y runs down-left, z is up."""

    def __init__(self, ox, oy, s):
        self.ox, self.oy, self.s = ox, oy, s

    def __call__(self, x, y, z=0.0):
        return (self.ox + (x - y) * self.s, self.oy + (x + y) * self.s / 2 - z * self.s)

    def poly(self, pts):
        return "M" + " L".join(f"{a:.1f} {b:.1f}" for a, b in (self(*q) for q in pts)) + " Z"

    def line(self, pts):
        return "M" + " L".join(f"{a:.1f} {b:.1f}" for a, b in (self(*q) for q in pts))


def path_d(pts, close=True):
    return "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in pts) + (" Z" if close else "")


def grass_tuft(x, y, rng, s=1.0, cols=("#6E6A34", "#8A7E3E", "#A89448")):
    blades = []
    for i in range(5 + rng.randrange(4)):
        a = (rng.random() - 0.5) * 1.3
        L = (5 + rng.random() * 6) * s
        bx = x + (rng.random() - 0.5) * 4 * s
        blades.append(f'<path d="M{bx:.1f} {y:.1f} Q{bx + a * L * 0.4:.1f} {y - L * 0.6:.1f} {bx + a * L:.1f} {y - L:.1f}" '
                      f'fill="none" stroke="{rng.choice(cols)}" stroke-width="{1.1 * s:.1f}" stroke-linecap="round"></path>')
    return "".join(blades)


def pebble(x, y, rng, s=1.0, base=(120, 112, 98)):
    r = (1.6 + rng.random() * 2.6) * s
    k = rng.randint(-18, 14)
    c = f"rgb({base[0] + k},{base[1] + k},{base[2] + k})"
    return (f'<ellipse cx="{x + 0.8 * s:.1f}" cy="{y + 0.8 * s:.1f}" rx="{r:.1f}" ry="{r * 0.6:.1f}" fill="#000" opacity="0.35"></ellipse>'
            f'<ellipse cx="{x:.1f}" cy="{y:.1f}" rx="{r:.1f}" ry="{r * 0.62:.1f}" fill="{c}"></ellipse>'
            f'<ellipse cx="{x - r * 0.3:.1f}" cy="{y - r * 0.25:.1f}" rx="{r * 0.45:.1f}" ry="{r * 0.22:.1f}" fill="#FFF" opacity="0.22"></ellipse>')


def ridge(width, base, amp, seed, step=8, bottom=900):
    pts = []
    for x in range(-step, width + step * 2, step):
        y = (base - amp * (0.55 * math.sin(x / 97 + seed) + 0.3 * math.sin(x / 41 + seed * 2.1)
                           + 0.15 * math.sin(x / 17 + seed * 3.7)))
        pts.append(f"{x} {y:.1f}")
    return f"M{-step} {bottom} L" + " L".join(pts) + f" L{width + step * 2} {bottom} Z"
