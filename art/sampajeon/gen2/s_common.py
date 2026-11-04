"""Shared helpers for the SAMJAE screen artboards (Main, Tokens)."""

FONT_LINK = ("https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800"
             "&amp;family=IBM+Plex+Mono:wght@400;500;600"
             "&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap")

# Tokens
GROUND, PANEL, RAISED, LINE, INK, MUTED = "#16140F", "#201D17", "#2A2620", "#4A4236", "#EFE6D2", "#A89E8A"
JEOK, HWANG, CHEONG, BAEK, HEUK = "#C8432B", "#E2B13C", "#2F6E8E", "#EFE8D8", "#1A1814"
ALERT, OK = "#E5604D", "#8CC79A"
ORE, ORE_GLOW, GAS, FOOD = "#9FE3D0", "#CFF5EA", "#B79AE0", "#E8DDBF"
R1, R1L, R1G, BRASS, WOOD, WOODD = "#D9532E", "#F09A6E", "#3E8F7A", "#C9A35A", "#4A2E1E", "#2E1E14"
R2, R2L, R2D, R2D2, HIDE = "#4FB3E8", "#A8DDF5", "#12222E", "#1B3242", "#2B2F55"
R3, R3L, R3D, INDIGO, CLOTH, ROPE = "#E2B13C", "#F2D58A", "#3A2E14", "#3B4E8C", "#E8E1C8", "#8A7A5A"

DISPLAY = "'Nanum Myeongjo', serif"
BODY = "'IBM Plex Sans KR', sans-serif"
MONO = "'IBM Plex Mono', monospace"


def page(title, body, w, h):
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
<link rel="stylesheet" href="{FONT_LINK}">
<style>
body{{margin:0;background:{GROUND}}}
a{{color:{HWANG}}}a:hover{{color:{R3L}}}
button{{font-family:inherit}}
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


def seal(p, size=56, char="參"):
    """Red 낙관 seal with a rough stamped edge."""
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 64 64" role="img" aria-label="낙관">
  <defs>
    <filter id="{p}-rough" x="-10%" y="-10%" width="120%" height="120%">
      <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="7" result="n"></feTurbulence>
      <feDisplacementMap in="SourceGraphic" in2="n" scale="2.6" xChannelSelector="R" yChannelSelector="G"></feDisplacementMap>
    </filter>
    <linearGradient id="{p}-ink" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#D9502F"></stop><stop offset="1" stop-color="#A8321E"></stop>
    </linearGradient>
  </defs>
  <g filter="url(#{p}-rough)">
    <rect x="4" y="4" width="56" height="56" rx="5" fill="url(#{p}-ink)"></rect>
    <rect x="9" y="9" width="46" height="46" rx="2" fill="none" stroke="#F6E6D0" stroke-width="2.2"></rect>
    <text x="32" y="44" text-anchor="middle" font-family="{DISPLAY}" font-weight="800" font-size="31" fill="#F6E6D0">{char}</text>
  </g>
</svg>"""


def emblem_r1(p, size=48):
    """조선 공방: round heaven inside square earth, compass needle."""
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 64 64" role="img" aria-label="조선 공방 문장">
  <defs>
    <linearGradient id="{p}-plate" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E9C37A"></stop><stop offset=".5" stop-color="{BRASS}"></stop><stop offset="1" stop-color="#7A5A28"></stop></linearGradient>
    <radialGradient id="{p}-disc" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="{R1L}"></stop><stop offset=".55" stop-color="{R1}"></stop><stop offset="1" stop-color="#7E2A14"></stop></radialGradient>
  </defs>
  <rect x="6" y="8" width="52" height="52" rx="3" fill="#000" opacity=".35"></rect>
  <rect x="5" y="5" width="52" height="52" rx="3" fill="url(#{p}-plate)"></rect>
  <rect x="9" y="9" width="44" height="44" rx="1.5" fill="{WOODD}"></rect>
  <rect x="9.5" y="9.5" width="43" height="43" rx="1.5" fill="none" stroke="{R1G}" stroke-width="1.6"></rect>
  <circle cx="31" cy="31" r="17" fill="url(#{p}-disc)"></circle>
  <circle cx="31" cy="31" r="17" fill="none" stroke="#F6D7B0" stroke-width="1" opacity=".6"></circle>
  <path d="M31 12 L34 31 L31 50 L28 31 Z" fill="#F7E9C9"></path>
  <path d="M31 12 L34 31 L31 31 Z" fill="#FFFFFF" opacity=".55"></path>
  <path d="M12 31 L31 28.5 L50 31 L31 33.5 Z" fill="{WOODD}" opacity=".75"></path>
  <circle cx="31" cy="31" r="3" fill="{BRASS}" stroke="{WOODD}" stroke-width="1"></circle>
  <circle cx="12" cy="12" r="1.4" fill="#F7E3B0"></circle><circle cx="50" cy="12" r="1.4" fill="#F7E3B0"></circle>
  <circle cx="12" cy="50" r="1.4" fill="#F7E3B0"></circle><circle cx="50" cy="50" r="1.4" fill="#F7E3B0"></circle>
</svg>"""


def emblem_r2(p, size=48):
    """요물 무리: horned goblin-fire flame."""
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 64 64" role="img" aria-label="요물 무리 문장">
  <defs>
    <radialGradient id="{p}-fire" cx=".5" cy=".7" r=".7"><stop offset="0" stop-color="#F2FBFF"></stop><stop offset=".35" stop-color="{R2L}"></stop><stop offset=".75" stop-color="{R2}"></stop><stop offset="1" stop-color="#1D5E86"></stop></radialGradient>
    <linearGradient id="{p}-horn" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F3DDA4"></stop><stop offset="1" stop-color="#8A6524"></stop></linearGradient>
    <filter id="{p}-glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3.2"></feGaussianBlur></filter>
  </defs>
  <path d="M32 6 C40 18 52 26 50 42 C48 54 40 60 32 60 C24 60 16 54 14 42 C12 26 24 18 32 6 Z" fill="{R2}" opacity=".55" filter="url(#{p}-glow)"></path>
  <path d="M19 22 C14 16 13 9 16 4 C18 11 22 15 26 17 Z" fill="url(#{p}-horn)"></path>
  <path d="M45 22 C50 16 51 9 48 4 C46 11 42 15 38 17 Z" fill="url(#{p}-horn)"></path>
  <path d="M32 9 C39 19 49 27 47 42 C45.5 52 39 57 32 57 C25 57 18.5 52 17 42 C15 27 25 19 32 9 Z" fill="url(#{p}-fire)"></path>
  <path d="M32 24 C36 31 41 35 40 43 C39 49 35.5 51 32 51 C28.5 51 25 49 24 43 C23 35 28 31 32 24 Z" fill="#E8F8FF" opacity=".75"></path>
  <ellipse cx="27.5" cy="40" rx="2.2" ry="3" fill="{R2D}"></ellipse><ellipse cx="36.5" cy="40" rx="2.2" ry="3" fill="{R2D}"></ellipse>
  <path d="M27 47 Q32 50 37 47" stroke="{R2D}" stroke-width="1.6" fill="none" stroke-linecap="round"></path>
</svg>"""


def emblem_r3(p, size=48):
    """오방 신당: five 오방색 flags on a stone platform, with a thin 금줄."""
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 64 64" role="img" aria-label="오방 신당 문장">
  <path d="M6 52 H58 L61 60 H3 Z" fill="#6E665A" stroke="#2A241C" stroke-width="1.2"></path><path d="M6 52 H58" stroke="#B4AA98" stroke-width="1.2"></path><path d="M14 52 V16" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M15 17 L22 20 L15 28 Z" fill="#2F6E8E" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="14" cy="15" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M23 52 V10" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M24 11 L31 14 L24 22 Z" fill="#C8432B" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="23" cy="9" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M32 52 V4" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M33 5 L40 8 L33 16 Z" fill="#E2B13C" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="32" cy="3" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M41 52 V10" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M42 11 L49 14 L42 22 Z" fill="#EFE8D8" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="41" cy="9" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M50 52 V16" stroke="#4A2E1E" stroke-width="2.2" stroke-linecap="round"></path><path d="M51 17 L58 20 L51 28 Z" fill="#1A1814" stroke="#120D08" stroke-width="0.9" stroke-linejoin="round"></path><circle cx="50" cy="15" r="1.8" fill="#E2B13C" stroke="#3E2A0C" stroke-width="0.5"></circle><path d="M9 40 Q32 47 55 40" fill="none" stroke="#C8B88E" stroke-width="1.5"></path><path d="M16.2 42.6 h3.6 l-1 6 h-1.6 Z" fill="#F6F0DE" stroke="#6A6050" stroke-width="0.4"></path><path d="M30.2 44.6 h3.6 l-1 6 h-1.6 Z" fill="#F6F0DE" stroke="#6A6050" stroke-width="0.4"></path><path d="M44.2 42.6 h3.6 l-1 6 h-1.6 Z" fill="#F6F0DE" stroke="#6A6050" stroke-width="0.4"></path>
</svg>"""


def icon_ore(p, size=28):
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 32 32" role="img" aria-label="귀화석">
  <defs><filter id="{p}-g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2"></feGaussianBlur></filter></defs>
  <ellipse cx="16" cy="18" rx="12" ry="10" fill="{ORE}" opacity=".35" filter="url(#{p}-g)"></ellipse>
  <ellipse cx="17" cy="28" rx="11" ry="2.6" fill="#000" opacity=".4"></ellipse>
  <path d="M8 27 L6 16 L10 9 L13 27 Z" fill="#5FAF9E"></path><path d="M10 9 L13 27 L11 16 Z" fill="#B6EEDF"></path>
  <path d="M12 27 L13 9 L17 3 L21 9 L21 27 Z" fill="#7CCBB8"></path>
  <path d="M17 3 L21 9 L21 27 L17 27 Z" fill="#4E9E8C"></path>
  <path d="M13 9 L17 3 L17 27 L13 27 Z" fill="{ORE_GLOW}"></path>
  <path d="M20 27 L22 13 L26 17 L26 27 Z" fill="#5FAF9E"></path><path d="M22 13 L24 15 L23 27 L20 27 Z" fill="{ORE}"></path>
  <path d="M15 6 L16 5" stroke="#fff" stroke-width="1.2" stroke-linecap="round"></path>
</svg>"""


def icon_gas(p, size=28):
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 32 32" role="img" aria-label="산기">
  <defs>
    <filter id="{p}-g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.6"></feGaussianBlur></filter>
    <linearGradient id="{p}-m" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E4D6FA"></stop><stop offset="1" stop-color="#7C5FB8"></stop></linearGradient>
  </defs>
  <path d="M4 28 L12 16 L16 21 L20 13 L28 28 Z" fill="#3A3346"></path>
  <path d="M12 16 L16 21 L14 28 L4 28 Z" fill="#4C4460"></path>
  <path d="M9 14 C6 10 10 6 14 8 C15 3 22 3 23 8 C27 7 29 12 25 14 Z" fill="{GAS}" opacity=".6" filter="url(#{p}-g)"></path>
  <path d="M9 14 C6 10 10 6 14 8 C15 3 22 3 23 8 C27 7 29 12 25 14 Z" fill="url(#{p}-m)" opacity=".9"></path>
  <path d="M12 11 C14 9 17 10 18 12 M18 8 C20 6 22 7 23 9" stroke="#F5EEFF" stroke-width="1.2" fill="none" stroke-linecap="round"></path>
  <path d="M15 22 C17 18 15 16 17 14" stroke="{GAS}" stroke-width="1.4" fill="none" stroke-linecap="round" opacity=".9"></path>
</svg>"""


def icon_food(p, size=28):
    return f"""<svg width="{size}" height="{size}" viewBox="0 0 32 32" role="img" aria-label="식량">
  <defs><linearGradient id="{p}-b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8A5A34"></stop><stop offset="1" stop-color="#3E2614"></stop></linearGradient></defs>
  <ellipse cx="16" cy="28" rx="11" ry="2.4" fill="#000" opacity=".4"></ellipse>
  <path d="M5 15 C9 7 23 7 27 15 Z" fill="{FOOD}"></path>
  <path d="M8 13 C11 8 18 8 21 11 C17 10 12 11 8 13 Z" fill="#FFF8E6"></path>
  <circle cx="12" cy="12" r=".9" fill="#C9BB92"></circle><circle cx="17" cy="10.5" r=".9" fill="#C9BB92"></circle><circle cx="21" cy="12.5" r=".9" fill="#C9BB92"></circle>
  <path d="M4 15 H28 C27 22 22 26 16 26 C10 26 5 22 4 15 Z" fill="url(#{p}-b)"></path>
  <path d="M4 15 H28" stroke="{BRASS}" stroke-width="1.4"></path>
  <path d="M7 18 C9 21 12 23 16 23" stroke="#B07A4A" stroke-width="1" fill="none" opacity=".7"></path>
  <rect x="12" y="25.5" width="8" height="2" rx=".8" fill="#2E1E14"></rect>
</svg>"""


def keycap(k, bg=RAISED, fg=INK, border=LINE):
    return (f'<span style="display: inline-flex; align-items: center; justify-content: center; min-width: 26px; height: 26px; '
            f'padding: 0 6px; box-sizing: border-box; background: {bg}; color: {fg}; border: 1px solid {border}; '
            f'border-bottom-width: 3px; border-radius: 4px; font-family: {MONO}; font-size: 13px; font-weight: 600">{k}</span>')


def dancheong_band(h=6):
    """Thin red/green/yellow stripes used as a panel header band."""
    return (f'<div style="height: {h}px; background: linear-gradient(90deg, {JEOK} 0 22%, {HWANG} 22% 26%, {R1G} 26% 70%, '
            f'{HWANG} 70% 74%, {CHEONG} 74% 100%)"></div>')
