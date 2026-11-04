"""Ornate traditional UI kit + 2.5D isometric scene helpers for SAMJAE screens (Main, Tokens).

Everything is our own design: carved lacquered-wood frames with brass fittings, hanji/parchment
panels, beveled lacquer buttons, gold titles, cloud ornaments (구름문), and a 2:1 isometric
painter for giwa-roofed halls, stone bases, trees and small figures.
"""
import math
import random

from s_common import (BODY, BRASS, DISPLAY, HWANG, INK, MONO, MUTED, R1, R1G, R2, R2L, R3,
                      INDIGO, CLOTH, ROPE, WOODD)

OUTLINE = "#120d08"


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


# ---------------------------------------------------------------- shared defs
def wood_defs(p, seed=3):
    return f"""<linearGradient id="{p}-wd" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#6E4126"></stop><stop offset=".45" stop-color="#4C2B17"></stop><stop offset="1" stop-color="#2C170B"></stop></linearGradient>
    <linearGradient id="{p}-br" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#FBE3A0"></stop><stop offset=".35" stop-color="#D9B060"></stop><stop offset=".7" stop-color="#9C7330"></stop><stop offset="1" stop-color="#5E4116"></stop></linearGradient>
    <radialGradient id="{p}-rv" cx=".35" cy=".3" r=".75">
      <stop offset="0" stop-color="#FFF3C8"></stop><stop offset=".45" stop-color="#C99B48"></stop><stop offset="1" stop-color="#4E3510"></stop></radialGradient>
    <filter id="{p}-gr" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency="0.006 0.22" numOctaves="3" seed="{seed}" result="n"></feTurbulence>
      <feColorMatrix in="n" type="matrix" values="0 0 0 0 0.06  0 0 0 0 0.03  0 0 0 0 0.01  1.6 0 0 0 -0.62" result="g"></feColorMatrix>
      <feComposite in="g" in2="SourceAlpha" operator="in"></feComposite>
    </filter>"""


CORNER_PLATE = ("M0 0 H40 C37 4 33 6 28 5 C27 10 22 13 17 12 C17 17 13 22 9 22 "
                "C10 27 6 33 5 36 C4 37 2 39 0 40 Z")


def corner(p, x, y, sx, sy, s=1.0):
    return (f'<g transform="translate({f(x)} {f(y)}) scale({f(sx * s)} {f(sy * s)})">'
            f'<path d="{CORNER_PLATE}" fill="#000" opacity=".45" transform="translate(1.5 2)"></path>'
            f'<path d="{CORNER_PLATE}" fill="url(#{p}-br)" stroke="#3E2A0C" stroke-width=".9"></path>'
            '<path d="M3 3 H31 C29 6 26 7 23 7 C22 11 18 13 14 13 C14 18 11 21 7 21 C7 24 5 28 3 30 Z" '
            'fill="none" stroke="#FFF0C0" stroke-width=".7" opacity=".55"></path>'
            '<path d="M14 6 C18 6 20 9 18 11 C16 13 13 11 14 9" fill="none" stroke="#5E4116" stroke-width=".9"></path>'
            f'<circle cx="7" cy="7" r="2.6" fill="url(#{p}-rv)" stroke="#3E2A0C" stroke-width=".5"></circle>'
            f'<circle cx="25" cy="5" r="1.8" fill="url(#{p}-rv)"></circle>'
            f'<circle cx="5" cy="25" r="1.8" fill="url(#{p}-rv)"></circle>'
            '</g>')


def cloud_path(x, y, s=1.0):
    """Korean cloud motif (구름문): a lobed cloud with curled tails."""
    return (f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">'
            '<path d="M-30 6 C-36 -2 -28 -12 -19 -8 C-18 -17 -5 -20 0 -11 C5 -20 18 -17 19 -8 '
            'C28 -12 36 -2 30 6 C26 11 19 10 17 7 C12 12 4 11 0 7 C-4 11 -12 12 -17 7 C-19 10 -26 11 -30 6 Z"></path>'
            '</g>')


def cloud_curls(x, y, s, stroke, w=1.2):
    return (f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})" fill="none" stroke="{stroke}" stroke-width="{w}" stroke-linecap="round">'
            '<path d="M-19 -2 C-22 -6 -16 -9 -14 -5 C-13 -3 -16 -2 -16 -4"></path>'
            '<path d="M0 -4 C-3 -10 5 -12 6 -7 C7 -4 3 -3 3 -5"></path>'
            '<path d="M19 -2 C22 -6 16 -9 14 -5 C13 -3 16 -2 16 -4"></path>'
            '</g>')


def crest_ornament(p, x, y, s=1.0):
    """Brass cloud medallion that sits across a frame's top rail."""
    return (f'<g>'
            f'<g fill="#000" opacity=".45" transform="translate(1.5 2.5)">{cloud_path(x, y, s)}</g>'
            f'<g fill="url(#{p}-br)" stroke="#3E2A0C" stroke-width="{f(0.9 / s)}">{cloud_path(x, y, s)}</g>'
            f'{cloud_curls(x, y, s, "#5E4116", 1.3)}'
            f'<circle cx="{f(x)}" cy="{f(y - 2 * s)}" r="{f(4.2 * s)}" fill="#A8321E" stroke="#3E2A0C" stroke-width=".8"></circle>'
            f'<circle cx="{f(x - 1.2 * s)}" cy="{f(y - 3.4 * s)}" r="{f(1.4 * s)}" fill="#F6B49A" opacity=".8"></circle>'
            '</g>')


def wood_frame_svg(p, w, h, b=16, panel="lacquer", crest=True, seed=3, corners=True):
    """Carved lacquered-wood frame with a recessed panel and brass corner fittings."""
    if panel == "parchment":
        pfill = f"url(#{p}-pp)"
        pdef = f"""<linearGradient id="{p}-pp" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#EBDDB8"></stop><stop offset="1" stop-color="#D3BF8E"></stop></linearGradient>
    <filter id="{p}-pt" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="{seed + 4}" result="n"></feTurbulence>
      <feColorMatrix in="n" type="matrix" values="0 0 0 0 0.35  0 0 0 0 0.25  0 0 0 0 0.12  0 0 0 -1.1 0.62" result="g"></feColorMatrix>
      <feComposite in="g" in2="SourceAlpha" operator="in"></feComposite></filter>"""
        ptex = f'<rect x="{b}" y="{b}" width="{w - 2 * b}" height="{h - 2 * b}" fill="#000" filter="url(#{p}-pt)" opacity=".5"></rect>'
    else:
        pfill = f"url(#{p}-pl)"
        pdef = f"""<linearGradient id="{p}-pl" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#2A1E15"></stop><stop offset="1" stop-color="#16100B"></stop></linearGradient>"""
        ptex = ""
    iw, ih = w - 2 * b, h - 2 * b
    parts = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" aria-hidden="true" '
             f'style="position: absolute; left: 0; top: 0; overflow: visible">',
             f'<defs>{wood_defs(p, seed)}{pdef}'
             f'<linearGradient id="{p}-is" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".65"></stop>'
             f'<stop offset="1" stop-color="#000" stop-opacity="0"></stop></linearGradient></defs>',
             f'<rect x="2" y="5" width="{w - 2}" height="{h - 2}" rx="5" fill="#000" opacity=".55"></rect>',
             f'<rect x="0" y="0" width="{w}" height="{h}" rx="5" fill="url(#{p}-wd)" stroke="{OUTLINE}" stroke-width="1.2"></rect>',
             f'<rect x="0" y="0" width="{w}" height="{h}" rx="5" fill="#000" filter="url(#{p}-gr)" opacity=".8"></rect>',
             f'<path d="M5 {h - 3} V5 Q5 2 8 2 H{w - 4}" fill="none" stroke="#B9875A" stroke-width="1.2" opacity=".65"></path>',
             f'<path d="M{w - 2} 6 V{h - 5} Q{w - 2} {h - 2} {w - 5} {h - 2} H6" fill="none" stroke="#000" stroke-width="1.4" opacity=".6"></path>',
             f'<rect x="6.5" y="6.5" width="{w - 13}" height="{h - 13}" rx="3" fill="none" stroke="#1B0E06" stroke-width="1.6"></rect>',
             f'<rect x="7.8" y="7.8" width="{w - 15.6}" height="{h - 15.6}" rx="3" fill="none" stroke="#9A6A40" stroke-width=".7" opacity=".55"></rect>',
             f'<rect x="{b - 3}" y="{b - 3}" width="{iw + 6}" height="{ih + 6}" fill="none" stroke="url(#{p}-br)" stroke-width="1.6"></rect>',
             f'<rect x="{b}" y="{b}" width="{iw}" height="{ih}" fill="{pfill}"></rect>',
             ptex,
             f'<rect x="{b}" y="{b}" width="{iw}" height="12" fill="url(#{p}-is)" opacity=".8"></rect>',
             f'<rect x="{b}" y="{b}" width="{iw}" height="{ih}" fill="none" stroke="#000" stroke-width="2" opacity=".55"></rect>']
    if corners:
        parts += [corner(p, 2, 2, 1, 1), corner(p, w - 2, 2, -1, 1), corner(p, 2, h - 2, 1, -1), corner(p, w - 2, h - 2, -1, -1)]
    if crest:
        parts.append(crest_ornament(p, w / 2, 3, 1.0))
    parts.append("</svg>")
    return "".join(parts)


def framed(p, w, h, inner, b=16, panel="lacquer", crest=True, pad=14, seed=3, extra_style=""):
    """HTML block: an ornate frame with content laid over the recessed panel."""
    return (f'<div style="position: relative; width: {w}px; height: {h}px; flex: none; {extra_style}">'
            f'{wood_frame_svg(p, w, h, b, panel, crest, seed)}'
            f'<div style="position: absolute; left: {b + pad}px; top: {b + pad}px; right: {b + pad}px; bottom: {b + pad}px; '
            f'display: flex; flex-direction: column">{inner}</div></div>')


# ---------------------------------------------------------------- titles
def gold_title(p, text, w, h, size, anchor="start", x=None, y=None, spacing=0):
    x = (6 if anchor == "start" else w / 2) if x is None else x
    y = h * 0.78 if y is None else y
    return f"""<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="{text}" style="overflow: visible">
  <defs>
    <linearGradient id="{p}-g" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFF6D6"></stop><stop offset=".38" stop-color="#F2CF7E"></stop>
      <stop offset=".55" stop-color="#B9862E"></stop><stop offset=".72" stop-color="#E7BE66"></stop><stop offset="1" stop-color="#8A5E1C"></stop></linearGradient>
    <filter id="{p}-ds" x="-10%" y="-20%" width="120%" height="150%">
      <feGaussianBlur in="SourceAlpha" stdDeviation="{f(size / 18)}"></feGaussianBlur><feOffset dx="0" dy="{f(size / 16)}" result="b"></feOffset>
      <feFlood flood-color="#000" flood-opacity=".85"></feFlood><feComposite in2="b" operator="in" result="s"></feComposite>
      <feMerge><feMergeNode in="s"></feMergeNode><feMergeNode in="SourceGraphic"></feMergeNode></feMerge></filter>
  </defs>
  <g filter="url(#{p}-ds)" font-family="{DISPLAY}" font-weight="800" font-size="{size}" text-anchor="{anchor}" letter-spacing="{spacing}">
    <text x="{f(x)}" y="{f(y)}" fill="none" stroke="#2A1406" stroke-width="{f(size / 9)}" stroke-linejoin="round">{text}</text>
    <text x="{f(x)}" y="{f(y)}" fill="url(#{p}-g)" stroke="#FFF2C8" stroke-width="{f(size / 90)}" stroke-opacity=".5">{text}</text>
  </g>
</svg>"""


# ---------------------------------------------------------------- buttons
BTN = {
    "primary": dict(
        normal=("linear-gradient(180deg,#C24A2C 0%,#962F18 52%,#6E2010 100%)", "#D7B26A", "#FFF1D6",
                "inset 0 1px 0 rgba(255,214,170,.6), inset 0 -3px 0 rgba(0,0,0,.35), 0 0 0 1px #2A1408, 0 4px 0 #1C0D06, 0 8px 14px rgba(0,0,0,.55)", ""),
        hover=("linear-gradient(180deg,#DA5A36 0%,#B03A1E 52%,#842714 100%)", "#FBE3A0", "#FFFFFF",
               "inset 0 1px 0 rgba(255,226,190,.75), inset 0 -3px 0 rgba(0,0,0,.3), 0 0 0 1px #2A1408, 0 4px 0 #1C0D06, 0 0 18px rgba(242,190,90,.55)", ""),
        pressed=("linear-gradient(180deg,#6E2010 0%,#8E2D18 60%,#A8381E 100%)", "#B08A48", "#F4D9BC",
                 "inset 0 3px 7px rgba(0,0,0,.6), 0 0 0 1px #2A1408, 0 1px 0 #1C0D06", "transform: translateY(3px);"),
        disabled=("linear-gradient(180deg,#4E4439 0%,#3A332B 100%)", "#6A6050", "#968C7A",
                  "inset 0 1px 0 rgba(255,255,255,.08), 0 0 0 1px #1A1410, 0 4px 0 #120E0A", ""),
    ),
    "secondary": dict(
        normal=("linear-gradient(180deg,#5C3820 0%,#3E2414 55%,#2A170B 100%)", "#B8914E", "#F2D58A",
                "inset 0 1px 0 rgba(255,214,160,.35), inset 0 -3px 0 rgba(0,0,0,.4), 0 0 0 1px #1A0D05, 0 4px 0 #120904, 0 8px 14px rgba(0,0,0,.5)", ""),
        hover=("linear-gradient(180deg,#704628 0%,#4E2E18 55%,#33200F 100%)", "#F3D58C", "#FFE7A8",
               "inset 0 1px 0 rgba(255,224,170,.5), inset 0 -3px 0 rgba(0,0,0,.35), 0 0 0 1px #1A0D05, 0 4px 0 #120904, 0 0 16px rgba(226,177,60,.4)", ""),
        pressed=("linear-gradient(180deg,#2A170B 0%,#3E2414 60%,#4E2E18 100%)", "#8A6A34", "#D8BC7A",
                 "inset 0 3px 7px rgba(0,0,0,.65), 0 0 0 1px #1A0D05, 0 1px 0 #120904", "transform: translateY(3px);"),
        disabled=("linear-gradient(180deg,#3A3530 0%,#2C2824 100%)", "#5A5248", "#8E8574",
                  "inset 0 1px 0 rgba(255,255,255,.06), 0 0 0 1px #141010, 0 4px 0 #0E0B08", ""),
    ),
    "danger": dict(
        normal=("linear-gradient(180deg,#2C2622 0%,#1E1916 100%)", "#E5604D", "#F7A898",
                "inset 0 1px 0 rgba(255,180,160,.25), inset 0 -3px 0 rgba(0,0,0,.4), 0 0 0 1px #140806, 0 4px 0 #0C0504, 0 8px 14px rgba(0,0,0,.5)", ""),
        hover=("linear-gradient(180deg,#3A2420 0%,#2A1A16 100%)", "#FF8A76", "#FFD2C8",
               "inset 0 1px 0 rgba(255,190,170,.35), inset 0 -3px 0 rgba(0,0,0,.35), 0 0 0 1px #140806, 0 4px 0 #0C0504, 0 0 16px rgba(229,96,77,.45)", ""),
        pressed=("linear-gradient(180deg,#140C0A 0%,#24140F 100%)", "#B04A3A", "#E89484",
                 "inset 0 3px 7px rgba(0,0,0,.7), 0 0 0 1px #140806, 0 1px 0 #0C0504", "transform: translateY(3px);"),
        disabled=("linear-gradient(180deg,#2E2A26 0%,#24201C 100%)", "#544A40", "#867C6C",
                  "0 0 0 1px #141010, 0 4px 0 #0E0B08", ""),
    ),
}


def ornate_button(label, variant="primary", state="normal", w=None, h=46, size=17, key=None):
    bg, border, fg, shadow, extra = BTN[variant][state]
    width = f"width: {w}px;" if w else "padding: 0 22px;"
    dis = ' disabled aria-disabled="true"' if state == "disabled" else ""
    ts = "0 1px 0 rgba(0,0,0,.7)" if state == "disabled" else "0 1px 0 #2A1006, 0 0 10px rgba(255,200,120,.25)"
    k = ""
    if key:
        k = (f'<span style="display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 20px; '
             f'padding: 0 4px; margin-left: 10px; border-radius: 3px; background: rgba(0,0,0,.35); border: 1px solid {border}; '
             f'font-family: {MONO}; font-size: 11px; font-weight: 600; color: {fg}">{key}</span>')
    return (f'<button type="button"{dis} style="{width} height: {h}px; box-sizing: border-box; display: inline-flex; '
            f'align-items: center; justify-content: center; border-radius: 4px; border: 2px solid {border}; '
            f'background: {bg}; box-shadow: {shadow}; color: {fg}; font-family: {DISPLAY}; font-weight: 800; '
            f'font-size: {size}px; letter-spacing: .04em; text-shadow: {ts}; cursor: pointer; {extra}">{label}{k}</button>')


# ---------------------------------------------------------------- medallion portrait
def medallion(p, size=120, who="gunner"):
    s = size / 120
    face = gunner_portrait(p) if who == "gunner" else goblin_portrait(p)
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 120 120" role="img" aria-label="초상 메달리온">
  <defs>
    {wood_defs(p)}
    <radialGradient id="{p}-bg" cx=".5" cy=".38" r=".7"><stop offset="0" stop-color="#7A4A2A"></stop><stop offset="1" stop-color="#1E120A"></stop></radialGradient>
    <clipPath id="{p}-c"><circle cx="60" cy="60" r="41"></circle></clipPath>
    <linearGradient id="{p}-ring" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FBE3A0"></stop><stop offset=".5" stop-color="#B08436"></stop><stop offset="1" stop-color="#4E3510"></stop></linearGradient>
  </defs>
  <circle cx="61" cy="63" r="56" fill="#000" opacity=".5"></circle>
  <circle cx="60" cy="60" r="56" fill="url(#{p}-wd)" stroke="{OUTLINE}" stroke-width="1.4"></circle>
  <circle cx="60" cy="60" r="56" fill="#000" filter="url(#{p}-gr)" opacity=".7"></circle>
  {''.join(f'<path d="M{f(60 + 52 * math.cos(a))} {f(60 + 52 * math.sin(a))} L{f(60 + 47 * math.cos(a - .08))} {f(60 + 47 * math.sin(a - .08))} L{f(60 + 47 * math.cos(a + .08))} {f(60 + 47 * math.sin(a + .08))} Z" fill="url(#{p}-ring)"></path>' for a in [i * math.pi / 8 for i in range(16)])}
  <circle cx="60" cy="60" r="46" fill="none" stroke="url(#{p}-ring)" stroke-width="5"></circle>
  <circle cx="60" cy="60" r="48.5" fill="none" stroke="#2A1406" stroke-width="1"></circle>
  <circle cx="60" cy="60" r="43.5" fill="none" stroke="#2A1406" stroke-width="1.2"></circle>
  <path d="M24 38 A44 44 0 0 1 60 15" fill="none" stroke="#FFF3C8" stroke-width="1.4" opacity=".7"></path>
  <circle cx="60" cy="60" r="41" fill="url(#{p}-bg)"></circle>
  <g clip-path="url(#{p}-c)">{face}</g>
  <circle cx="60" cy="60" r="41" fill="none" stroke="#000" stroke-width="3" opacity=".5"></circle>
  {crest_ornament(p, 60, 106, .62)}
</svg>"""


def gunner_portrait(p):
    return f"""
    <path d="M20 120 C22 92 38 84 60 82 C82 84 98 92 100 120 Z" fill="#A8321E"></path>
    <path d="M20 120 C22 92 38 84 60 82 C52 92 46 104 44 120 Z" fill="#C84A2C"></path>
    <path d="M44 86 L60 104 L76 86" fill="none" stroke="#2F6E8E" stroke-width="5"></path>
    {''.join(f'<circle cx="{x}" cy="{y}" r="1.5" fill="#E9C37A"></circle>' for x, y in [(34, 100), (40, 96), (80, 96), (86, 100), (30, 108), (90, 108)])}
    <path d="M50 70 L50 84 C54 88 66 88 70 84 L70 70 Z" fill="#B98258"></path>
    <ellipse cx="60" cy="62" rx="15.5" ry="18" fill="#E2B48A"></ellipse>
    <path d="M45 62 C45 74 52 80 60 80 C54 76 50 70 50 60 Z" fill="#C08A60" opacity=".7"></path>
    <path d="M51 58 L57 57 M63 57 L69 58" stroke="#2A1A10" stroke-width="2" stroke-linecap="round"></path>
    <ellipse cx="54.5" cy="62" rx="1.8" ry="1.3" fill="#1A120A"></ellipse><ellipse cx="65.5" cy="62" rx="1.8" ry="1.3" fill="#1A120A"></ellipse>
    <path d="M60 63 L58 69 L61 69.5" fill="none" stroke="#9A6440" stroke-width="1.2"></path>
    <path d="M52 73 C56 71 64 71 68 73 C64 74.5 56 74.5 52 73 Z" fill="#2A1A10"></path>
    <path d="M57 76 L63 76 L60 82 Z" fill="#2A1A10"></path>
    <ellipse cx="60" cy="47" rx="38" ry="8" fill="#000" opacity=".35"></ellipse>
    <ellipse cx="60" cy="45" rx="37" ry="7.5" fill="#1E1A18"></ellipse>
    <ellipse cx="60" cy="44" rx="37" ry="7" fill="none" stroke="#5A5048" stroke-width="1"></ellipse>
    <path d="M44 44 C44 30 50 24 60 24 C70 24 76 30 76 44 Z" fill="#2A2420"></path>
    <path d="M47 42 C47 32 52 27 58 26" fill="none" stroke="#6A5E54" stroke-width="1.6"></path>
    <path d="M44 41 H76" stroke="#C9A35A" stroke-width="2.4"></path>
    <path d="M60 24 C58 16 52 12 46 12 C52 16 56 20 58 24 Z" fill="#D9332A"></path>
    <path d="M60 24 C62 14 68 10 74 10 C68 14 64 19 62 24 Z" fill="#E8483A"></path>
    <path d="M60 24 C60 14 62 8 60 4 C57 9 58 16 59 24 Z" fill="#C42A20"></path>
    <circle cx="60" cy="24" r="2.6" fill="#E9C37A"></circle>"""


def goblin_portrait(p):
    return f"""
    <path d="M18 120 C20 90 40 82 60 82 C80 82 100 90 102 120 Z" fill="#2B2F55"></path>
    <ellipse cx="60" cy="62" rx="22" ry="21" fill="#3E6FB0"></ellipse>
    <ellipse cx="54" cy="56" rx="12" ry="10" fill="#6A9FD8" opacity=".6"></ellipse>
    <path d="M42 48 C36 36 38 26 44 20 C44 30 48 38 50 44 Z" fill="#E9D3A0"></path>
    <path d="M78 48 C84 36 82 26 76 20 C76 30 72 38 70 44 Z" fill="#C9A35A"></path>
    <ellipse cx="52" cy="60" rx="4.5" ry="5" fill="#F4FBFF"></ellipse><ellipse cx="68" cy="60" rx="4.5" ry="5" fill="#F4FBFF"></ellipse>
    <circle cx="53" cy="61" r="2" fill="#12222E"></circle><circle cx="67" cy="61" r="2" fill="#12222E"></circle>
    <path d="M46 70 C52 78 68 78 74 70 C68 73 52 73 46 70 Z" fill="#12222E"></path>
    <path d="M50 72 L52 76 L54 72.5 M66 72.5 L68 76 L70 72" fill="#F4FBFF"></path>
    <path d="M38 50 C34 40 44 34 50 40 C54 30 66 30 70 40 C76 34 86 40 82 50" fill="#1B1F3A"></path>"""


# ---------------------------------------------------------------- isometric painter
class Iso:
    def __init__(self, ox, oy, hw=32, hh=16):
        self.ox, self.oy, self.hw, self.hh = ox, oy, hw, hh

    def P(self, i, j, z=0.0):
        return (self.ox + (i - j) * self.hw, self.oy + (i + j) * self.hh - z)

    def path(self, pts, fill, extra=""):
        d = "M" + " L".join(f"{f(x)} {f(y)}" for x, y in pts) + " Z"
        return f'<path d="{d}" fill="{fill}" {extra}></path>'

    def pts(self, coords):
        return [self.P(*c) for c in coords]


def mix(c1, c2, t):
    a = [int(c1[k:k + 2], 16) for k in (1, 3, 5)]
    b = [int(c2[k:k + 2], 16) for k in (1, 3, 5)]
    return "#" + "".join(f"{round(a[k] + (b[k] - a[k]) * t):02X}" for k in range(3))


def stone_box(iso, rnd, i0, j0, i1, j1, h, z0=0, base="#8A8478", courses=4, arches=()):
    """A cut-stone base with individually shaded blocks on both visible faces."""
    out = []
    P = iso.P
    lit, dark, top = mix(base, "#F2E2C2", .12), mix(base, "#1A1410", .45), mix(base, "#FFF4DC", .2)
    out.append(iso.path([P(i0, j1, z0), P(i1, j1, z0), P(i1, j1, z0 + h), P(i0, j1, z0 + h)], lit, f'stroke="{OUTLINE}" stroke-width="1" stroke-opacity=".6"'))
    out.append(iso.path([P(i1, j0, z0), P(i1, j1, z0), P(i1, j1, z0 + h), P(i1, j0, z0 + h)], dark, f'stroke="{OUTLINE}" stroke-width="1" stroke-opacity=".6"'))
    ch = h / courses
    for face in ("L", "R"):
        L = (i1 - i0) if face == "L" else (j1 - j0)
        for c in range(courses):
            u = -rnd.uniform(0, .5)
            while u < L:
                sw = rnd.uniform(.45, .8)
                ua, ub = max(u, 0), min(u + sw, L)
                za, zb = z0 + c * ch + .8, z0 + (c + 1) * ch - .8
                if ub - ua > .06:
                    if face == "L":
                        q = [P(i0 + ua + .03, j1, za), P(i0 + ub - .03, j1, za), P(i0 + ub - .03, j1, zb), P(i0 + ua + .03, j1, zb)]
                        col = mix(lit, rnd.choice(["#FFF4DC", "#2A2016", "#B8A27A"]), rnd.uniform(.0, .22))
                    else:
                        q = [P(i1, j0 + ua + .03, za), P(i1, j0 + ub - .03, za), P(i1, j0 + ub - .03, zb), P(i1, j0 + ua + .03, zb)]
                        col = mix(dark, rnd.choice(["#6A6050", "#0E0A08", "#4A4034"]), rnd.uniform(.0, .25))
                    out.append(iso.path(q, col))
                    out.append(f'<path d="M{f(q[3][0])} {f(q[3][1])} L{f(q[2][0])} {f(q[2][1])}" stroke="#FFF2D6" stroke-width=".6" opacity="{".35" if face == "L" else ".12"}"></path>')
                u += sw
    for (uc, uw, vh) in arches:
        Lx = i1 - i0
        pts = []
        for k in range(15):
            a = math.pi * k / 14
            uu = (uc - uw / 2 * math.cos(a)) * Lx
            vv = vh * .5 + vh * .25 * math.sin(a)
            pts.append(P(i0 + uu, j1, z0 + vv * h))
        poly = [P(i0 + (uc - uw / 2) * Lx, j1, z0)] + pts + [P(i0 + (uc + uw / 2) * Lx, j1, z0)]
        ring = [(x, y - 4) for x, y in pts]
        out.append('<path d="M' + " L".join(f"{f(x)} {f(y)}" for x, y in ring) + '" stroke="#C8BC9C" stroke-width="7" fill="none" opacity=".9"></path>')
        out.append('<path d="M' + " L".join(f"{f(x)} {f(y)}" for x, y in ring) + '" stroke="#5A5040" stroke-width="1" fill="none" stroke-dasharray="2 6"></path>')
        out.append(iso.path(poly, "#0E0A08"))
        inner = [(x + 4, y + 3) for x, y in poly]
        out.append(iso.path(inner, "url(#s1-arch)"))
        # half-open red lacquered doors
        da, db = P(i0 + (uc - uw / 2) * Lx, j1, z0), P(i0 + (uc - uw / 2 + uw * .22) * Lx, j1, z0)
        top = vh * .55 * h
        out.append(iso.path([da, db, (db[0], db[1] - top), (da[0], da[1] - top)], "#8E2A16", f'stroke="{OUTLINE}" stroke-width=".7"'))
        for kk in range(3):
            out.append(f'<circle cx="{f((da[0] + db[0]) / 2)}" cy="{f(da[1] - top * (kk + 1) / 4)}" r="1.3" fill="#E9C37A"></circle>')
    out.append(iso.path([P(i0, j0, z0 + h), P(i1, j0, z0 + h), P(i1, j1, z0 + h), P(i0, j1, z0 + h)], top, f'stroke="{OUTLINE}" stroke-width="1" stroke-opacity=".6"'))
    return "".join(out)


def hall(iso, rnd, i0, j0, i1, j1, z0, wh, rh, o=.5, bays=5, side_bays=2, roof="#3D434A", lit_win=True, band=True):
    """Timber hall: red posts, hanji lattice windows, dancheong band, hipped giwa roof with tile rows."""
    P = iso.P
    out = []
    # walls -----------------------------------------------------------
    for face in ("L", "R"):
        if face == "L":
            A = lambda u, v: P(i0 + u * (i1 - i0), j1, z0 + v * wh)
            nb, shade = bays, 0.0
        else:
            A = lambda u, v: P(i1, j0 + u * (j1 - j0), z0 + v * wh)
            nb, shade = side_bays, .42
        wall = mix("#6A3A20", "#120A06", shade)
        out.append(iso.path([A(0, 0), A(1, 0), A(1, 1), A(0, 1)], wall, f'stroke="{OUTLINE}" stroke-width="1"'))
        for b in range(nb):
            ua, ub = b / nb + .022, (b + 1) / nb - .022
            # lower plaster/wood panel
            out.append(iso.path([A(ua, .04), A(ub, .04), A(ub, .3), A(ua, .3)], mix("#D8C8A4", "#2A2016", shade + .1)))
            out.append(iso.path([A(ua + .01, .08), A(ub - .01, .08), A(ub - .01, .26), A(ua + .01, .26)], mix("#8A5A34", "#120A06", shade)))
            # lattice window (lit hanji)
            win = mix("#F6D592" if lit_win else "#E8DCBC", "#2A1C10", shade * .8)
            out.append(iso.path([A(ua, .34), A(ub, .34), A(ub, .78), A(ua, .78)], win))
            if lit_win and face == "L":
                out.append(iso.path([A(ua, .34), A(ub, .34), A(ub, .78), A(ua, .78)], "#FFB45A", 'opacity=".25" filter="url(#s1-soft)"'))
            for k in range(1, 4):
                uu = ua + (ub - ua) * k / 4
                a, c = A(uu, .34), A(uu, .78)
                out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(c[0])} {f(c[1])}" stroke="#5A3018" stroke-width=".9" opacity=".85"></path>')
            for k in range(1, 5):
                vv = .34 + .44 * k / 5
                a, c = A(ua, vv), A(ub, vv)
                out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(c[0])} {f(c[1])}" stroke="#5A3018" stroke-width=".8" opacity=".75"></path>')
        # posts
        for b in range(nb + 1):
            u = b / nb
            pc = mix("#B8381E", "#2A0C06", shade)
            out.append(iso.path([A(max(u - .022, 0), 0), A(min(u + .022, 1), 0), A(min(u + .022, 1), 1), A(max(u - .022, 0), 1)], pc))
            a, c = A(max(u - .018, 0), 0), A(max(u - .018, 0), 1)
            out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(c[0])} {f(c[1])}" stroke="#F09A6E" stroke-width=".7" opacity="{.6 - shade}"></path>')
        # dancheong band under eaves
        if band:
            out.append(iso.path([A(0, .82), A(1, .82), A(1, 1), A(0, 1)], mix(R1G, "#08120E", shade)))
            out.append(iso.path([A(0, .82), A(1, .82), A(1, .86), A(0, .86)], mix("#C8432B", "#200806", shade)))
            n = max(4, int(nb * 5))
            for k in range(n):
                cu = (k + .5) / n
                a = A(cu, .91)
                col = ["#E2B13C", "#EFE8D8", "#C8432B", "#2F6E8E"][k % 4]
                out.append(f'<circle cx="{f(a[0])}" cy="{f(a[1])}" r="1.5" fill="{mix(col, "#000000", shade * .8)}"></circle>')
        # eave shadow
        out.append(iso.path([A(0, .7), A(1, .7), A(1, 1), A(0, 1)], "#000", 'opacity=".38"'))
    # roof ----------------------------------------------------------------
    jc = (j0 + j1) / 2
    r = (j1 - j0) / 2 + o
    zt = z0 + wh
    E = lambda i, j: P(i, j, zt)
    eA, eB, eC, eD = (i0 - o, j1 + o), (i1 + o, j1 + o), (i1 + o, j0 - o), (i0 - o, j0 - o)
    R0, R1_ = P(i0 - o + r, jc, zt + rh), P(i1 + o - r, jc, zt + rh)
    lit, mid, dk = mix(roof, "#F4F0E6", .18), roof, mix(roof, "#050505", .5)
    out.append(iso.path([E(*eD), E(*eC), R1_, R0], mix(roof, "#F4F0E6", .05)))  # back slope sliver
    out.append(iso.path([E(*eD), E(*eA), R0], mix(roof, "#050505", .2)))        # left hip
    out.append(iso.path([E(*eB), E(*eC), R1_], dk, f'stroke="{OUTLINE}" stroke-width="1"'))
    out.append(iso.path([E(*eA), E(*eB), R1_, R0], lit, f'stroke="{OUTLINE}" stroke-width="1"'))
    # tile rows on the front slope (parallel up-slope lines)
    L = (i1 + o) - (i0 - o)
    n = int(L / .16)
    for k in range(1, n):
        i = i0 - o + L * k / n
        jb = j1 + o
        if i < i0 - o + r:
            jt = jb - (i - (i0 - o))
        elif i > i1 + o - r:
            jt = jb - ((i1 + o) - i)
        else:
            jt = jc
        zt2 = zt + (jb - jt) / r * rh
        a, b = P(i, jb, zt), P(i, jt, zt2)
        out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="#14181C" stroke-width="1.5" opacity=".55"></path>')
        out.append(f'<path d="M{f(a[0] - 1.4)} {f(a[1] - .4)} L{f(b[0] - 1.4)} {f(b[1] - .4)}" stroke="#AEB6BC" stroke-width=".8" opacity=".5"></path>')
        out.append(f'<circle cx="{f(a[0])}" cy="{f(a[1] + .6)}" r="1.7" fill="#C9CFD2" stroke="#1A1E22" stroke-width=".5"></circle>')
    # tile rows on the right hip (fan toward the ridge end)
    m = int(((j1 + o) - (j0 - o)) / .2)
    for k in range(1, m):
        j = j0 - o + ((j1 + o) - (j0 - o)) * k / m
        a = E(i1 + o, j)
        out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(R1_[0] + (a[0] - R1_[0]) * .1)} {f(R1_[1] + (a[1] - R1_[1]) * .1)}" stroke="#05080A" stroke-width="1.1" opacity=".55"></path>')
        out.append(f'<circle cx="{f(a[0])}" cy="{f(a[1] + .6)}" r="1.5" fill="#7E868C" stroke="#0E1012" stroke-width=".5"></circle>')
    # ridges
    for a, b in [(R0, R1_), (E(*eA), R0), (E(*eB), R1_), (E(*eC), R1_)]:
        out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="#0E1012" stroke-width="5" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(a[0])} {f(a[1] - 1)} L{f(b[0])} {f(b[1] - 1)}" stroke="#DADFE0" stroke-width="2.2" stroke-linecap="round" opacity=".85"></path>')
    for R in (R0, R1_):
        out.append(f'<path d="M{f(R[0] - 4)} {f(R[1] + 1)} C{f(R[0] - 6)} {f(R[1] - 9)} {f(R[0] + 4)} {f(R[1] - 12)} {f(R[0] + 5)} {f(R[1] - 5)} '
                   f'C{f(R[0] + 5)} {f(R[1] - 2)} {f(R[0] + 1)} {f(R[1] - 2)} {f(R[0] + 1)} {f(R[1] - 5)}" fill="#1A1E22" stroke="#9AA2A6" stroke-width=".7"></path>')
    # upturned eave corners + eave edge highlight
    for (ci, cj), (dx, dy) in [(eA, (-7, -7)), (eB, (2, -9)), (eC, (7, -7))]:
        c = E(ci, cj)
        out.append(f'<path d="M{f(c[0])} {f(c[1])} Q{f(c[0] + dx * .3)} {f(c[1] + 1)} {f(c[0] + dx)} {f(c[1] + dy)}" '
                   f'stroke="#0E1012" stroke-width="3.6" fill="none" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(c[0])} {f(c[1] - 1)} Q{f(c[0] + dx * .3)} {f(c[1])} {f(c[0] + dx)} {f(c[1] + dy - 1)}" '
                   f'stroke="#C9CFD2" stroke-width="1.2" fill="none" stroke-linecap="round"></path>')
    a, b = E(*eA), E(*eB)
    out.append(f'<path d="M{f(a[0])} {f(a[1] + 2)} L{f(b[0])} {f(b[1] + 2)}" stroke="#2A0E06" stroke-width="2.4" opacity=".8"></path>')
    out.append(f'<path d="M{f(a[0])} {f(a[1] + 4)} L{f(b[0])} {f(b[1] + 4)}" stroke="{R1G}" stroke-width="1.6" opacity=".9"></path>')
    return "".join(out)


def wall_run(iso, rnd, i0, i1, j0, j1, h):
    """Palace wall: stone body with a small tiled cap (gable along i)."""
    out = [stone_box(iso, rnd, i0, j0, i1, j1, h, base="#9A8E78", courses=3)]
    P = iso.P
    o, rh = .25, 11
    jc = (j0 + j1) / 2
    a, b = P(i0, j1 + o, h), P(i1, j1 + o, h)
    c, d = P(i1, jc, h + rh), P(i0, jc, h + rh)
    out.append(iso.path([P(i0, j0 - o, h), P(i1, j0 - o, h), c, d], "#4A5056"))
    out.append(iso.path([a, b, c, d], "#565E64", f'stroke="{OUTLINE}" stroke-width="1"'))
    out.append(iso.path([P(i1, j1 + o, h), P(i1, j0 - o, h), c], "#2A2E32"))
    n = int((i1 - i0) / .16)
    for k in range(1, n):
        i = i0 + (i1 - i0) * k / n
        e, t = P(i, j1 + o, h), P(i, jc, h + rh)
        out.append(f'<path d="M{f(e[0])} {f(e[1])} L{f(t[0])} {f(t[1])}" stroke="#14181C" stroke-width="1.1" opacity=".55"></path>')
        out.append(f'<circle cx="{f(e[0])}" cy="{f(e[1] + .4)}" r="1.2" fill="#BFC6CA"></circle>')
    out.append(f'<path d="M{f(d[0])} {f(d[1])} L{f(c[0])} {f(c[1])}" stroke="#0E1012" stroke-width="4" stroke-linecap="round"></path>')
    out.append(f'<path d="M{f(d[0])} {f(d[1] - 1)} L{f(c[0])} {f(c[1] - 1)}" stroke="#C9CFD2" stroke-width="1.5" opacity=".7"></path>')
    return "".join(out)


# ---------------------------------------------------------------- vegetation
def maple(rnd, x, y, s=1.0, shadow=True):
    out = []
    if shadow:
        out.append(f'<ellipse cx="{f(x + 14 * s)}" cy="{f(y + 3 * s)}" rx="{f(30 * s)}" ry="{f(10 * s)}" fill="#000" opacity=".35" filter="url(#s1-soft)"></ellipse>')
    out.append(f'<path d="M{f(x - 3 * s)} {f(y)} C{f(x - 2 * s)} {f(y - 18 * s)} {f(x - 6 * s)} {f(y - 30 * s)} {f(x - 10 * s)} {f(y - 42 * s)} '
               f'M{f(x - 2 * s)} {f(y - 22 * s)} C{f(x + 6 * s)} {f(y - 30 * s)} {f(x + 10 * s)} {f(y - 38 * s)} {f(x + 14 * s)} {f(y - 46 * s)}" '
               f'stroke="#2A1810" stroke-width="{f(4 * s)}" fill="none" stroke-linecap="round"></path>')
    cols = ["#7A1A10", "#A8281A", "#C8432B", "#D9602E", "#E58A3A", "#F2B04A"]
    for k in range(34):
        a = rnd.uniform(0, math.tau)
        rr = rnd.uniform(0, 1) ** .6
        cx = x + 2 * s + math.cos(a) * 30 * s * rr
        cy = y - 52 * s + math.sin(a) * 22 * s * rr
        # light from top-left: upper-left clusters brighter
        t = max(0, min(1, .5 - (cx - x) / (70 * s) - (cy - (y - 52 * s)) / (50 * s)))
        ci = min(5, max(0, int(t * 5 + rnd.uniform(-.8, .8))))
        r = rnd.uniform(6, 11) * s
        out.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="{cols[ci]}"></circle>')
    for k in range(10):
        cx = x + rnd.uniform(-22, 10) * s
        cy = y - 58 * s + rnd.uniform(-16, 4) * s
        out.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(rnd.uniform(2, 4) * s)}" fill="#FFD27A" opacity=".75"></circle>')
    return "".join(out)


def pine(rnd, x, y, s=1.0, shadow=True):
    out = []
    if shadow:
        out.append(f'<ellipse cx="{f(x + 16 * s)}" cy="{f(y + 3 * s)}" rx="{f(28 * s)}" ry="{f(8 * s)}" fill="#000" opacity=".35" filter="url(#s1-soft)"></ellipse>')
    out.append(f'<path d="M{f(x)} {f(y)} C{f(x + 2 * s)} {f(y - 20 * s)} {f(x - 8 * s)} {f(y - 38 * s)} {f(x - 2 * s)} {f(y - 60 * s)} '
               f'C{f(x + 2 * s)} {f(y - 70 * s)} {f(x + 8 * s)} {f(y - 78 * s)} {f(x + 6 * s)} {f(y - 90 * s)}" '
               f'stroke="#5A2E1A" stroke-width="{f(5 * s)}" fill="none" stroke-linecap="round"></path>')
    out.append(f'<path d="M{f(x - 1 * s)} {f(y - 4 * s)} C{f(x)} {f(y - 22 * s)} {f(x - 8 * s)} {f(y - 38 * s)} {f(x - 3 * s)} {f(y - 58 * s)}" '
               f'stroke="#B0603A" stroke-width="{f(1.4 * s)}" fill="none" opacity=".7"></path>')
    for (dx, dy, w) in [(-20, -36, 24), (14, -50, 26), (-6, -66, 22), (10, -84, 20), (-14, -92, 14)]:
        cx, cy = x + dx * s, y + dy * s
        for layer, (col, oy) in enumerate([("#0E2216", 3), ("#1E3E26", 0), ("#3A6A3A", -3)]):
            ww = w * s * (1 - layer * .18)
            out.append(f'<ellipse cx="{f(cx - layer * 2 * s)}" cy="{f(cy + oy * s)}" rx="{f(ww)}" ry="{f(6.5 * s * (1 - layer * .2))}" fill="{col}"></ellipse>')
        for k in range(5):
            px = cx + rnd.uniform(-w * .8, w * .6) * s
            out.append(f'<path d="M{f(px)} {f(cy - 2 * s)} l{f(-3 * s)} {f(-4 * s)} M{f(px)} {f(cy - 2 * s)} l{f(2 * s)} {f(-4 * s)}" stroke="#6E9A58" stroke-width=".8" opacity=".7"></path>')
    return "".join(out)


def grass_tuft(rnd, x, y, s=1.0, col="#B89A4A"):
    d = " ".join(f"M{f(x + k * 1.6 * s)} {f(y)} q{f(rnd.uniform(-3, 3) * s)} {f(-4 * s)} {f(rnd.uniform(-4, 4) * s)} {f(-rnd.uniform(6, 11) * s)}"
                 for k in range(-2, 3))
    return f'<path d="{d}" stroke="{col}" stroke-width="{f(1.1 * s)}" fill="none" stroke-linecap="round"></path>'


# ---------------------------------------------------------------- figures (feet at x,y)
def _shadow(x, y, s):
    return f'<ellipse cx="{f(x + 3 * s)}" cy="{f(y)}" rx="{f(9 * s)}" ry="{f(3.4 * s)}" fill="#000" opacity=".45"></ellipse>'


def person(rnd, x, y, kind="soldier", s=1.0, face=1):
    S = lambda v: f(v * s)
    out = [_shadow(x, y, s)]
    robe = {"soldier": ("#C8432B", "#7E2414"), "merchant": ("#D8CDB0", "#8A7E62"), "noble": ("#EFE8D8", "#9E9684"),
            "woman": ("#3B4E8C", "#1E2850"), "goblin": ("#2B2F55", "#14162E")}[kind]
    skin = "#4F86C6" if kind == "goblin" else "#E2B48A"
    skin_d = "#2A4E86" if kind == "goblin" else "#B07E58"
    # legs
    out.append(f'<path d="M{f(x - 2.5 * s)} {f(y)} L{f(x - 2 * s)} {f(y - 9 * s)} M{f(x + 2.5 * s)} {f(y)} L{f(x + 2 * s)} {f(y - 9 * s)}" '
               f'stroke="{"#E8E0CC" if kind != "goblin" else skin_d}" stroke-width="{S(3)}" stroke-linecap="round"></path>')
    out.append(f'<path d="M{f(x - 4.5 * s)} {f(y)} h{S(4)} M{f(x + 1 * s)} {f(y)} h{S(4)}" stroke="#1A120A" stroke-width="{S(2)}" stroke-linecap="round"></path>')
    # carried back-frame (지게) for merchants
    if kind == "merchant":
        out.append(f'<path d="M{f(x - 6 * s)} {f(y - 6 * s)} L{f(x - 3 * s)} {f(y - 34 * s)} M{f(x + 2 * s)} {f(y - 6 * s)} L{f(x + 4 * s)} {f(y - 34 * s)} '
                   f'M{f(x - 5 * s)} {f(y - 14 * s)} H{f(x + 3 * s)} M{f(x - 4 * s)} {f(y - 24 * s)} H{f(x + 4 * s)}" stroke="#6A4424" stroke-width="{S(1.6)}" fill="none"></path>')
        out.append(f'<rect x="{f(x - 9 * s)}" y="{f(y - 40 * s)}" width="{S(16)}" height="{S(12)}" rx="{S(2)}" fill="#8A6A3A" stroke="{OUTLINE}" stroke-width=".6"></rect>')
        out.append(f'<path d="M{f(x - 9 * s)} {f(y - 34 * s)} h{S(16)} M{f(x - 2 * s)} {f(y - 40 * s)} v{S(12)}" stroke="#4A3018" stroke-width=".8"></path>')
        out.append(f'<rect x="{f(x - 7 * s)}" y="{f(y - 46 * s)}" width="{S(12)}" height="{S(7)}" rx="{S(2)}" fill="#3B4E8C"></rect>')
    # robe
    if kind == "woman":
        out.append(f'<path d="M{f(x - 8 * s)} {f(y - 1 * s)} C{f(x - 7 * s)} {f(y - 10 * s)} {f(x - 4 * s)} {f(y - 18 * s)} {f(x)} {f(y - 19 * s)} '
                   f'C{f(x + 4 * s)} {f(y - 18 * s)} {f(x + 7 * s)} {f(y - 10 * s)} {f(x + 8 * s)} {f(y - 1 * s)} Z" fill="{robe[0]}" stroke="{OUTLINE}" stroke-width=".6"></path>')
        out.append(f'<path d="M{f(x + 1 * s)} {f(y - 18 * s)} C{f(x + 5 * s)} {f(y - 12 * s)} {f(x + 6 * s)} {f(y - 6 * s)} {f(x + 7 * s)} {f(y - 1 * s)} L{f(x + 8 * s)} {f(y - 1 * s)} C{f(x + 7 * s)} {f(y - 10 * s)} {f(x + 4 * s)} {f(y - 18 * s)} {f(x)} {f(y - 19 * s)} Z" fill="{robe[1]}"></path>')
        out.append(f'<path d="M{f(x - 4.5 * s)} {f(y - 19 * s)} L{f(x + 4.5 * s)} {f(y - 19 * s)} L{f(x + 3.5 * s)} {f(y - 26 * s)} L{f(x - 3.5 * s)} {f(y - 26 * s)} Z" fill="#E2B13C" stroke="{OUTLINE}" stroke-width=".5"></path>')
        out.append(f'<path d="M{f(x)} {f(y - 23 * s)} l{S(3)} {S(6)}" stroke="#C8432B" stroke-width="{S(1.2)}"></path>')
    else:
        out.append(f'<path d="M{f(x - 6 * s)} {f(y - 6 * s)} L{f(x - 5.5 * s)} {f(y - 22 * s)} C{f(x - 3 * s)} {f(y - 26 * s)} {f(x + 3 * s)} {f(y - 26 * s)} '
                   f'{f(x + 5.5 * s)} {f(y - 22 * s)} L{f(x + 6 * s)} {f(y - 6 * s)} Z" fill="{robe[0]}" stroke="{OUTLINE}" stroke-width=".6"></path>')
        out.append(f'<path d="M{f(x + 1 * s)} {f(y - 25 * s)} C{f(x + 4 * s)} {f(y - 24 * s)} {f(x + 5.5 * s)} {f(y - 22 * s)} {f(x + 6 * s)} {f(y - 6 * s)} L{f(x + 2 * s)} {f(y - 6 * s)} Z" fill="{robe[1]}"></path>')
        out.append(f'<path d="M{f(x - 2 * s)} {f(y - 24 * s)} L{f(x - 3 * s)} {f(y - 8 * s)}" stroke="#FFF" stroke-width=".6" opacity=".35"></path>')
        belt = {"soldier": "#2F6E8E", "goblin": "#E2B13C", "merchant": "#6A4424", "noble": "#3B4E8C"}[kind]
        out.append(f'<path d="M{f(x - 5.8 * s)} {f(y - 15 * s)} H{f(x + 5.8 * s)}" stroke="{belt}" stroke-width="{S(1.8)}"></path>')
        if kind == "goblin":
            for k in range(3):
                out.append(f'<path d="M{f(x - 5 * s + k * 4 * s)} {f(y - 12 * s)} l{S(1.5)} {S(4)}" stroke="#14100A" stroke-width="{S(1.2)}"></path>')
            out.append(f'<path d="M{f(x - 6 * s)} {f(y - 14 * s)} L{f(x + 6 * s)} {f(y - 14 * s)} L{f(x + 5 * s)} {f(y - 8 * s)} L{f(x - 5 * s)} {f(y - 8 * s)} Z" fill="#E2A13C" opacity=".85"></path>')
        if kind == "soldier":
            for k in range(4):
                out.append(f'<circle cx="{f(x - 3.5 * s + k * 2.4 * s)}" cy="{f(y - 19 * s)}" r="{S(.7)}" fill="#E9C37A"></circle>')
    # arms + weapon
    if kind == "soldier":
        out.append(f'<path d="M{f(x + 4 * s * face)} {f(y - 22 * s)} L{f(x + 7 * s * face)} {f(y - 13 * s)}" stroke="{robe[1]}" stroke-width="{S(2.6)}" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(x - 2 * s * face)} {f(y - 4 * s)} L{f(x + 9 * s * face)} {f(y - 36 * s)}" stroke="#4A2E1E" stroke-width="{S(1.8)}" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(x + 5 * s * face)} {f(y - 24 * s)} L{f(x + 9 * s * face)} {f(y - 36 * s)}" stroke="#8C9298" stroke-width="{S(1.1)}"></path>')
        out.append(f'<circle cx="{f(x + 9 * s * face)}" cy="{f(y - 37 * s)}" r="{S(1.4)}" fill="#FFB45A" filter="url(#s1-soft)"></circle>')
    elif kind == "goblin":
        out.append(f'<path d="M{f(x + 5 * s * face)} {f(y - 21 * s)} L{f(x + 10 * s * face)} {f(y - 30 * s)}" stroke="{skin}" stroke-width="{S(3)}" stroke-linecap="round"></path>')
        out.append(f'<path d="M{f(x + 9 * s * face)} {f(y - 28 * s)} L{f(x + 16 * s * face)} {f(y - 44 * s)}" stroke="#6A4424" stroke-width="{S(4.6)}" stroke-linecap="round"></path>')
        for k in range(3):
            tx, ty = x + (12 + k * 1.6) * s * face, y - (36 + k * 3) * s
            out.append(f'<circle cx="{f(tx)}" cy="{f(ty)}" r="{S(.9)}" fill="#E9C37A"></circle>')
    else:
        out.append(f'<path d="M{f(x - 5 * s)} {f(y - 22 * s)} L{f(x - 6.5 * s)} {f(y - 13 * s)} M{f(x + 5 * s)} {f(y - 22 * s)} L{f(x + 6.5 * s)} {f(y - 13 * s)}" '
                   f'stroke="{robe[1]}" stroke-width="{S(2.4)}" stroke-linecap="round"></path>')
    # head
    hy = y - (29 if kind != "woman" else 30) * s
    out.append(f'<ellipse cx="{f(x)}" cy="{f(hy)}" rx="{S(4.2)}" ry="{S(4.6)}" fill="{skin}" stroke="{OUTLINE}" stroke-width=".5"></ellipse>')
    out.append(f'<path d="M{f(x + 1 * s)} {f(hy - 4 * s)} C{f(x + 5 * s)} {f(hy - 2 * s)} {f(x + 5 * s)} {f(hy + 3 * s)} {f(x + 1 * s)} {f(hy + 4.5 * s)}" fill="{skin_d}" opacity=".55"></path>')
    out.append(f'<circle cx="{f(x + 1.6 * s * face)}" cy="{f(hy)}" r="{S(.6)}" fill="#1A120A"></circle>')
    # headwear
    if kind == "soldier":
        out.append(f'<ellipse cx="{f(x)}" cy="{f(hy - 3 * s)}" rx="{S(8)}" ry="{S(2.4)}" fill="#1E1A18" stroke="#5A5048" stroke-width=".5"></ellipse>')
        out.append(f'<path d="M{f(x - 3.5 * s)} {f(hy - 3 * s)} C{f(x - 3.5 * s)} {f(hy - 9 * s)} {f(x + 3.5 * s)} {f(hy - 9 * s)} {f(x + 3.5 * s)} {f(hy - 3 * s)} Z" fill="#2A2420"></path>')
        out.append(f'<path d="M{f(x)} {f(hy - 8 * s)} c{S(-2)} {S(-3)} {S(-1)} {S(-5)} {S(1)} {S(-6)} c{S(0)} {S(2)} {S(1)} {S(4)} {S(-1)} {S(6)} Z" fill="#E8483A"></path>')
    elif kind == "merchant":
        out.append(f'<path d="M{f(x - 8 * s)} {f(hy - 2 * s)} L{f(x)} {f(hy - 9 * s)} L{f(x + 8 * s)} {f(hy - 2 * s)} Z" fill="#C9A35A" stroke="#6A4C1A" stroke-width=".6"></path>')
        out.append(f'<path d="M{f(x - 4 * s)} {f(hy - 5.4 * s)} L{f(x + 4 * s)} {f(hy - 5.4 * s)}" stroke="#E9D3A0" stroke-width=".8"></path>')
    elif kind == "noble":
        out.append(f'<ellipse cx="{f(x)}" cy="{f(hy - 3 * s)}" rx="{S(9)}" ry="{S(2.6)}" fill="#14100E" opacity=".9"></ellipse>')
        out.append(f'<rect x="{f(x - 3 * s)}" y="{f(hy - 10 * s)}" width="{S(6)}" height="{S(7)}" rx="{S(1)}" fill="#14100E"></rect>')
    elif kind == "woman":
        out.append(f'<ellipse cx="{f(x - .6 * s)}" cy="{f(hy - 2 * s)}" rx="{S(4.6)}" ry="{S(3.4)}" fill="#14100E"></ellipse>')
        out.append(f'<circle cx="{f(x - 4 * s)}" cy="{f(hy + 1 * s)}" r="{S(2)}" fill="#14100E"></circle>')
    elif kind == "goblin":
        out.append(f'<path d="M{f(x - 3 * s)} {f(hy - 3 * s)} l{S(-2)} {S(-6)} l{S(3)} {S(4)} Z M{f(x + 3 * s)} {f(hy - 3 * s)} l{S(2)} {S(-6)} l{S(-3)} {S(4)} Z" fill="#E9D3A0"></path>')
        out.append(f'<path d="M{f(x - 4 * s)} {f(hy - 2 * s)} C{f(x - 5 * s)} {f(hy - 7 * s)} {f(x + 5 * s)} {f(hy - 7 * s)} {f(x + 4 * s)} {f(hy - 2 * s)}" fill="#1B1F3A"></path>')
        out.append(f'<circle cx="{f(x)}" cy="{f(hy - 12 * s)}" r="{S(5)}" fill="{R2}" opacity=".35" filter="url(#s1-soft)"></circle>')
    return "".join(out)


def flame(x, y, s, o=1.0, glow=True):
    g = ('<path d="M0 -30 C8 -18 16 -10 14 2 C12 12 6 16 0 16 C-6 16 -12 12 -14 2 C-16 -10 -8 -18 0 -30 Z" '
         'fill="#4FB3E8" filter="url(#s1-fireglow)" opacity=".9"></path>') if glow else ""
    return (f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})" opacity="{o}">{g}'
            '<path d="M0 -30 C8 -18 16 -10 14 2 C12 12 6 16 0 16 C-6 16 -12 12 -14 2 C-16 -10 -8 -18 0 -30 Z" fill="url(#s1-flame)"></path>'
            '<path d="M3 -22 C7 -15 9 -10 8 -4" stroke="#E8F8FF" stroke-width="1.4" fill="none" opacity=".8"></path>'
            '<path d="M0 -12 C4 -5 8 -1 7 5 C6 10 3 12 0 12 C-3 12 -6 10 -7 5 C-8 -1 -4 -5 0 -12 Z" fill="#F2FBFF" opacity=".9"></path>'
            '</g>')


def lantern(x, y, s=1.0, col="#C8432B"):
    return (f'<g transform="translate({f(x)} {f(y)}) scale({f(s)})">'
            '<circle cx="0" cy="6" r="18" fill="#FFB45A" opacity=".45" filter="url(#s1-soft)"></circle>'
            '<path d="M0 -8 V-2" stroke="#2A1810" stroke-width="1"></path>'
            '<rect x="-5" y="-3" width="10" height="2.4" fill="#2A1810"></rect>'
            f'<path d="M-6 0 C-9 4 -9 10 -6 14 H6 C9 10 9 4 6 0 Z" fill="{col}"></path>'
            '<path d="M-6 0 C-9 4 -9 10 -6 14 H-1 C-4 10 -4 4 -1 0 Z" fill="#FFE2A8" opacity=".55"></path>'
            '<path d="M-7.5 5 H7.5 M-7.8 9 H7.8" stroke="#5A1A0E" stroke-width=".7"></path>'
            '<rect x="-5" y="14" width="10" height="2.4" fill="#2A1810"></rect>'
            '<path d="M0 16.5 V24" stroke="#E2B13C" stroke-width="1.2"></path>'
            '</g>')


def kite(x, y, w, rot, o=1.0, string_to=None, pid="s1"):
    h = w * 1.28
    hole = w * 0.2
    s = ""
    if string_to:
        sx, sy = string_to
        s = (f'<path d="M{f(x)} {f(y + h / 2)} C{f(x + 30)} {f(y + h + 80)} {f(sx - 120)} {f(sy - 160)} {f(sx)} {f(sy)}" '
             f'stroke="#E8DCC0" stroke-width=".9" fill="none" opacity="{f(0.5 * o)}"></path>')
    hw, hh = w / 2, h / 2
    return s + (f'<g transform="translate({f(x)} {f(y)}) rotate({rot})" opacity="{o}">'
                f'<rect x="{f(-hw + 5)}" y="{f(-hh + 8)}" width="{f(w)}" height="{f(h)}" fill="#000" opacity=".3" filter="url(#{pid}-soft)"></rect>'
                f'<rect x="{f(-hw)}" y="{f(-hh)}" width="{f(w)}" height="{f(h)}" fill="url(#{pid}-cloth)" stroke="{OUTLINE}" stroke-width=".8"></rect>'
                f'<path d="M{f(-hw)} {f(-hh)} H{f(hw)} V{f(-hh + h * .26)} H{f(-hw)} Z" fill="#C8432B"></path>'
                f'<path d="M{f(-hw)} {f(-hh)} L0 {f(-hh + h * .26)} L{f(hw)} {f(-hh)}" fill="#3B4E8C"></path>'
                f'<path d="M{f(-hw)} {f(hh)} L{f(-hw + w * .28)} {f(hh)} L{f(-hw)} {f(hh - h * .25)} Z" fill="#3B4E8C"></path>'
                f'<path d="M{f(hw)} {f(hh)} L{f(hw - w * .28)} {f(hh)} L{f(hw)} {f(hh - h * .25)} Z" fill="#2F6E8E"></path>'
                f'<circle cx="0" cy="{f(h * .04)}" r="{f(hole)}" fill="#2A2230"></circle>'
                f'<circle cx="0" cy="{f(h * .04)}" r="{f(hole)}" fill="none" stroke="#8A7A5A" stroke-width=".9"></circle>'
                f'<path d="M{f(-hw)} {f(-hh)} L{f(hw)} {f(hh)} M{f(hw)} {f(-hh)} L{f(-hw)} {f(hh)} M0 {f(-hh)} V{f(hh)} M{f(-hw)} {f(-hh + h * .26)} H{f(hw)}" '
                f'stroke="#6A5A3A" stroke-width=".7" opacity=".6"></path>'
                f'<path d="M{f(-hw)} {f(-hh)} V{f(hh)}" stroke="#FFF8E8" stroke-width="1" opacity=".55"></path>'
                f'<path d="M{f(-hw * .6)} {f(hh)} q{f(-4)} {f(10)} {f(2)} {f(18)} M{f(hw * .6)} {f(hh)} q{f(4)} {f(10)} {f(-2)} {f(18)}" stroke="#C8432B" stroke-width="1.6" fill="none"></path>'
                '</g>')


def stall(iso, rnd, i, j, awn="#C8432B", goods="jars"):
    """Market stall: wooden table, slanted striped awning on poles, goods."""
    P = iso.P
    out = []
    i0, i1, j0, j1 = i, i + 1.4, j, j + .8
    gx, gy = P(i + .7, j + .4)
    out.append(f'<ellipse cx="{f(gx + 10)}" cy="{f(gy + 4)}" rx="46" ry="16" fill="#000" opacity=".35" filter="url(#s1-soft)"></ellipse>')
    for (pi, pj) in [(i0, j1), (i1, j1), (i1, j0)]:
        a, b = P(pi, pj, 0), P(pi, pj, 34)
        out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="#3A2414" stroke-width="2.4"></path>')
    # table
    out.append(iso.path([P(i0, j1, 0), P(i1, j1, 0), P(i1, j1, 12), P(i0, j1, 12)], "#6A4424", f'stroke="{OUTLINE}" stroke-width=".8"'))
    out.append(iso.path([P(i1, j0, 0), P(i1, j1, 0), P(i1, j1, 12), P(i1, j0, 12)], "#3A2414", f'stroke="{OUTLINE}" stroke-width=".8"'))
    out.append(iso.path([P(i0, j0, 12), P(i1, j0, 12), P(i1, j1, 12), P(i0, j1, 12)], "#9A6A3E", f'stroke="{OUTLINE}" stroke-width=".8"'))
    for k in range(1, 4):
        a, b = P(i0 + k * .35, j1, 1), P(i0 + k * .35, j1, 11)
        out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="#3A2414" stroke-width=".7"></path>')
    # goods
    if goods == "jars":
        for k in range(4):
            gx, gy = P(i0 + .2 + k * .3, j0 + .45, 12)
            out.append(f'<ellipse cx="{f(gx)}" cy="{f(gy - 5)}" rx="5" ry="6.5" fill="#5A3420" stroke="{OUTLINE}" stroke-width=".6"></ellipse>')
            out.append(f'<ellipse cx="{f(gx - 1.6)}" cy="{f(gy - 7)}" rx="1.6" ry="3" fill="#C99A6A" opacity=".6"></ellipse>')
            out.append(f'<ellipse cx="{f(gx)}" cy="{f(gy - 11)}" rx="3" ry="1.2" fill="#2A1810"></ellipse>')
    elif goods == "cloth":
        for k, c in enumerate(["#C8432B", "#2F6E8E", "#E2B13C", "#3E8F7A", "#EFE8D8"]):
            gx, gy = P(i0 + .18 + k * .25, j0 + .45, 12)
            out.append(f'<rect x="{f(gx - 5)}" y="{f(gy - 7)}" width="9" height="7" rx="1.5" fill="{c}" stroke="{OUTLINE}" stroke-width=".5"></rect>')
            out.append(f'<path d="M{f(gx - 5)} {f(gy - 5.5)} h9" stroke="#FFF" stroke-width=".6" opacity=".4"></path>')
    else:  # kites
        for k, c in enumerate(["#EFE8D8", "#E8DCC0", "#F2E6C8"]):
            gx, gy = P(i0 + .3 + k * .4, j0 + .4, 12)
            out.append(f'<rect x="{f(gx - 6)}" y="{f(gy - 15)}" width="12" height="15" fill="{c}" stroke="{OUTLINE}" stroke-width=".5" transform="rotate({-8 + k * 8} {f(gx)} {f(gy)})"></rect>')
            out.append(f'<circle cx="{f(gx)}" cy="{f(gy - 7)}" r="2.4" fill="#2A2230"></circle>')
            out.append(f'<rect x="{f(gx - 6)}" y="{f(gy - 15)}" width="12" height="4" fill="#C8432B" transform="rotate({-8 + k * 8} {f(gx)} {f(gy)})"></rect>')
    # awning
    A = [P(i0 - .15, j1 + .25, 30), P(i1 + .15, j1 + .25, 30), P(i1 + .15, j0 - .1, 40), P(i0 - .15, j0 - .1, 40)]
    out.append(iso.path(A, awn, f'stroke="{OUTLINE}" stroke-width=".9"'))
    for k in range(0, 8, 2):
        u0, u1 = k / 8, (k + 1) / 8
        q = [P(i0 - .15 + 1.7 * u0, j1 + .25, 30), P(i0 - .15 + 1.7 * u1, j1 + .25, 30),
             P(i0 - .15 + 1.7 * u1, j0 - .1, 40), P(i0 - .15 + 1.7 * u0, j0 - .1, 40)]
        out.append(iso.path(q, "#F2E6C8", 'opacity=".8"'))
    a, b = A[0], A[1]
    out.append(f'<path d="M{f(a[0])} {f(a[1])} L{f(b[0])} {f(b[1])}" stroke="#2A1006" stroke-width="2"></path>')
    for k in range(9):
        t = k / 8
        x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        out.append(f'<path d="M{f(x)} {f(y)} q2 4 0 7" stroke="{awn}" stroke-width="2" fill="none"></path>')
    out.append(iso.path([A[1], P(i1 + .15, j1 + .25, 24), P(i1 + .15, j0 - .1, 34), A[2]], "#000", 'opacity=".25"'))
    return "".join(out)
