"""Generates design2/project/Tokens.dc.html — SAMPAJEON design tokens & components (id prefix s5-)."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from s_common import *  # noqa: E402,F403
from ornate import (OUTLINE, crest_ornament, cloud_curls, cloud_path, f, framed, gold_title,  # noqa: E402
                    medallion, ornate_button, wood_defs)

OUT = os.path.join(os.path.dirname(__file__), "..", "design2", "project", "Tokens.dc.html")

H2 = f"margin: 0; font-size: 12px; font-weight: 600; letter-spacing: .14em; color: {MUTED}"


def section(title, inner, gap=10):
    return (f'<section style="display: flex; flex-direction: column; gap: {gap}px">'
            f'<h2 style="{H2}">{title}</h2>{inner}</section>')


def swatch(color, name, hexv, w=62, h=40, ring=False):
    border = f"1px solid {LINE}" if ring else "1px solid rgba(0,0,0,.25)"
    return (f'<div style="display: flex; flex-direction: column; gap: 4px; width: {w}px">'
            f'<div style="height: {h}px; background: {color}; border: {border}; box-shadow: inset 0 1px 0 rgba(255,255,255,.12)"></div>'
            f'<span style="font-size: 12px; font-weight: 600">{name}</span>'
            f'<span style="font-family: {MONO}; font-size: 10px; color: {MUTED}">{hexv}</span></div>')


def colors():
    surface = "".join(swatch(c, n, c, ring=True) for n, c in [
        ("바탕 먹", GROUND), ("패널", PANEL), ("돋움", RAISED), ("선", LINE), ("한지 글자", INK), ("보조 글자", MUTED)])
    obang = "".join(
        f'<div style="display: flex; flex-direction: column; gap: 4px; width: 76px">'
        f'<div style="position: relative; height: 44px; background: {c}; border: 1px solid {LINE}">'
        f'<span style="position: absolute; right: 6px; bottom: 2px; font-family: {DISPLAY}; font-weight: 800; font-size: 20px; color: {t}; opacity: .85">{h}</span></div>'
        f'<span style="font-size: 12px; font-weight: 600">{n}</span>'
        f'<span style="font-family: {MONO}; font-size: 10px; color: {MUTED}">{c}</span></div>'
        for n, c, h, t in [("청 · 동", CHEONG, "靑", BAEK), ("적 · 남", JEOK, "赤", BAEK), ("황 · 중앙", HWANG, "黃", HEUK),
                           ("백 · 서", BAEK, "白", HEUK), ("흑 · 북", HEUK, "黑", BAEK)])
    states = "".join(swatch(c, n, c, w=88, h=26) for n, c in [("경고 · 피격", ALERT), ("완료 · 아군 체력", OK), ("초점 링", HWANG)])
    factions = ""
    for tag, fn, name, cols in [
        ("r1", emblem_r1, "人 조선 공방", [(R1, "주홍"), (R1L, "밝음"), (R1G, "단청 녹"), (BRASS, "놋쇠"), (WOOD, "옻칠 목")]),
        ("r2", emblem_r2, "地 요물 무리", [(R2, "도깨비불"), (R2L, "밝음"), (R2D2, "그늘"), (HIDE, "가죽 쪽"), (BRASS, "뿔")]),
        ("r3", emblem_r3, "天 오방 신당", [(R3, "황금"), (R3L, "밝음"), (INDIGO, "쪽빛"), (CLOTH, "한지"), (ROPE, "금줄")]),
    ]:
        chips = "".join(f'<div title="{l}" style="display: flex; flex-direction: column; gap: 3px; width: 50px">'
                        f'<div style="height: 18px; background: {c}; border: 1px solid rgba(0,0,0,.3)"></div>'
                        f'<span style="font-family: {MONO}; font-size: 9px; color: {MUTED}">{c}</span></div>' for c, l in cols)
        factions += (f'<div style="display: flex; align-items: center; gap: 10px">{fn("s5-" + tag, 36)}'
                     f'<span style="width: 84px; font-size: 13px; font-weight: 600">{name}</span>'
                     f'<div style="display: flex; gap: 4px">{chips}</div></div>')
    res = "".join(
        f'<div style="display: flex; align-items: center; gap: 10px; padding: 8px 10px; background: {PANEL}; border: 1px solid {LINE}; flex: 1">'
        f'{fn("s5-res-" + k, 30)}<div style="display: flex; flex-direction: column; gap: 2px">'
        f'<span style="font-size: 13px; font-weight: 600; color: {c}">{n}</span>'
        f'<span style="font-family: {MONO}; font-size: 10px; color: {MUTED}">{c}</span></div></div>'
        for k, fn, n, c in [("ore", icon_ore, "귀화석", ORE), ("gas", icon_gas, "산기", GAS), ("food", icon_food, "식량", FOOD)])
    return "".join([
        section("표면 · 먹과 한지", f'<div style="display: flex; gap: 8px">{surface}</div>'),
        section("오방색 · 강조", f'<div style="display: flex; gap: 8px">{obang}</div>'),
        section("상태", f'<div style="display: flex; gap: 8px">{states}</div>'),
        section("세력 색", f'<div style="display: flex; flex-direction: column; gap: 8px">{factions}</div>'),
        section("자원", f'<div style="display: flex; gap: 8px">{res}</div>'),
    ])


def type_scale():
    rows = [
        (f"font-family: {DISPLAY}; font-weight: 800; font-size: 46px; line-height: 1", "삼파전", "Display · 명조 800 / 46"),
        (f"font-family: {DISPLAY}; font-weight: 800; font-size: 28px; line-height: 1.1", "한 판이 끝났습니다", "Title · 명조 800 / 28"),
        ("font-size: 20px; font-weight: 600", "화승총수 4기 선택", "Heading · Plex 600 / 20"),
        ("font-size: 15px", "귀화석 광맥이 바닥나고 있습니다.", "Body · Plex 400 / 15"),
        (f"font-size: 13px; color: {MUTED}", "Shift를 누른 채 명령하면 대기열에 추가", "Caption · Plex 400 / 13"),
        (f"font-family: {MONO}; font-size: 12px; color: {HWANG}", "tick 004820 · hash 9f3a1c07", "Data · Plex Mono / 12"),
    ]
    inner = "".join(
        f'<div style="display: flex; align-items: baseline; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px solid {PANEL}">'
        f'<span style="{s}">{t}</span><span style="flex: none; font-family: {MONO}; font-size: 10px; color: {MUTED}">{l}</span></div>'
        for s, t, l in rows)
    return section("글자 체계", f'<div style="display: flex; flex-direction: column">{inner}</div>', 6)


def btn(label, style):
    return (f'<span style="display: inline-flex; align-items: center; justify-content: center; height: 36px; width: 86px; '
            f'box-sizing: border-box; font-size: 14px; font-weight: 600; {style}">{label}</span>')


def buttons():
    P = {
        "기본": f"background: linear-gradient(180deg, #D4502F, {JEOK}); color: #FFF6E8; border: 1px solid #7E2414; box-shadow: inset 0 1px 0 rgba(255,255,255,.25), 0 2px 0 #5A180C",
        "호버": f"background: linear-gradient(180deg, #E2643E, #D04A2C); color: #FFF; border: 1px solid {HWANG}; box-shadow: 0 0 0 2px rgba(226,177,60,.35), 0 2px 0 #5A180C",
        "누름": "background: #8E2A18; color: #F3D6C6; border: 1px solid #5A180C; box-shadow: inset 0 2px 4px rgba(0,0,0,.5); transform: translateY(2px)",
        "비활성": f"background: #3A332B; color: #7A705E; border: 1px solid {LINE}",
    }
    S = {
        "기본": f"background: transparent; color: {INK}; border: 1px solid {LINE}",
        "호버": f"background: {RAISED}; color: {HWANG}; border: 1px solid {HWANG}",
        "누름": f"background: #12100C; color: {HWANG}; border: 1px solid {HWANG}; box-shadow: inset 0 2px 4px rgba(0,0,0,.6); transform: translateY(2px)",
        "비활성": f"background: transparent; color: #5E5646; border: 1px dashed {LINE}",
    }
    D = {
        "기본": f"background: transparent; color: {ALERT}; border: 1px solid {ALERT}",
        "호버": f"background: rgba(229,96,77,.16); color: #FF8A78; border: 1px solid #FF8A78",
        "누름": f"background: {ALERT}; color: {HEUK}; border: 1px solid {ALERT}; transform: translateY(2px)",
        "비활성": f"background: transparent; color: #6A4A42; border: 1px dashed #5A3A32",
    }
    head = "".join(f'<span style="width: 86px; text-align: center; font-family: {MONO}; font-size: 10px; color: {MUTED}">{k}</span>' for k in P)
    rows = ""
    for name, label, d in [("주요", "출전", P), ("보조", "취소", S), ("위험", "항복", D)]:
        rows += (f'<div style="display: flex; align-items: center; gap: 8px"><span style="width: 34px; font-size: 12px; color: {MUTED}">{name}</span>'
                 + "".join(btn(label, s) for s in d.values()) + '</div>')
    icon_btns = "".join(
        f'<span style="position: relative; width: 52px; height: 52px; display: inline-flex; align-items: center; justify-content: center; '
        f'background: linear-gradient(160deg, #3A3328, #1E1A14); border: 1px solid {b}; box-shadow: inset 0 1px 0 rgba(255,255,255,.12), inset 0 -3px 0 rgba(0,0,0,.4){g}; '
        f'font-family: {DISPLAY}; font-weight: 800; font-size: 20px; color: {c}">{t}'
        f'<span style="position: absolute; right: 3px; top: 2px; font-family: {MONO}; font-size: 9px; color: {MUTED}">{k}</span></span>'
        for t, k, b, c, g in [("移", "M", LINE, INK, ""), ("攻", "A", HWANG, HWANG, ", 0 0 10px rgba(226,177,60,.45)"),
                              ("止", "S", LINE, INK, ""), ("建", "B", LINE, "#6A604E", "")])
    return section("버튼 · 기본 / 호버 / 누름 / 비활성", f"""<div style="display: flex; flex-direction: column; gap: 8px">
  <div style="display: flex; gap: 8px; padding-left: 42px">{head}</div>
  {rows}
  <div style="display: flex; align-items: center; gap: 8px; margin-top: 4px"><span style="width: 34px; font-size: 12px; color: {MUTED}">명령</span>{icon_btns}
    <span style="font-size: 12px; line-height: 1.45; color: {MUTED}; margin-left: 6px">명령판 버튼 · 한자 낱자 + 단축키<br>노랑 테두리 = 지정 중</span></div>
</div>""")


def field(label, value, state):
    if state == "focus":
        b, extra, note = HWANG, "box-shadow: 0 0 0 3px rgba(226,177,60,.25)", f'<span style="font-size: 11px; color: {MUTED}">Enter 접속 · Esc 취소</span>'
    elif state == "error":
        b, extra, note = ALERT, "", f'<span style="font-size: 11px; color: {ALERT}">주소는 ws:// 또는 wss:// 로 시작해야 합니다</span>'
    else:
        b, extra, note = LINE, "", ""
    caret = f'<span style="width: 1px; height: 16px; background: {HWANG}"></span>' if state == "focus" else ""
    return (f'<label style="display: flex; flex-direction: column; gap: 4px; flex: 1"><span style="font-size: 12px; color: {MUTED}">{label}</span>'
            f'<span style="display: flex; align-items: center; height: 36px; padding: 0 10px; background: #12100C; border: 1px solid {b}; {extra}; '
            f'font-family: {MONO}; font-size: 13px; color: {INK}">{value}{caret}</span>{note}</label>')


def lattice_tile():
    return """<svg width="132" height="78" viewBox="0 0 132 78" role="img" aria-label="문살 무늬">
  <defs>
    <pattern id="s5-lat" width="22" height="22" patternUnits="userSpaceOnUse">
      <rect width="22" height="22" fill="#2A2620"></rect>
      <path d="M0 11 H22 M11 0 V22 M5 5 H17 V17 H5 Z" stroke="#7A5A3A" stroke-width="2.2" fill="none"></path>
      <path d="M0 11 H22 M11 0 V22" stroke="#B08A5A" stroke-width=".8" fill="none" opacity=".6"></path>
    </pattern>
    <linearGradient id="s5-latlight" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#EFE6D2" stop-opacity=".18"></stop><stop offset="1" stop-color="#000" stop-opacity=".3"></stop></linearGradient>
  </defs>
  <rect width="132" height="78" fill="url(#s5-lat)"></rect>
  <rect width="132" height="78" fill="url(#s5-latlight)"></rect>
  <rect x=".5" y=".5" width="131" height="77" fill="none" stroke="#4A4236"></rect>
</svg>"""


def hanji_tile():
    return """<svg width="132" height="78" viewBox="0 0 132 78" role="img" aria-label="한지 결">
  <defs>
    <filter id="s5-fiber" x="0" y="0" width="100%" height="100%">
      <feTurbulence type="fractalNoise" baseFrequency=".04 .6" numOctaves="3" seed="4" result="n"></feTurbulence>
      <feColorMatrix in="n" type="matrix" values="0 0 0 0 .55  0 0 0 0 .5  0 0 0 0 .42  0 0 0 1.1 -.35"></feColorMatrix>
    </filter>
  </defs>
  <rect width="132" height="78" fill="#E9DFC6"></rect>
  <rect width="132" height="78" filter="url(#s5-fiber)" opacity=".6"></rect>
  <path d="M10 60 C30 52 50 66 72 56 C92 48 110 60 124 54" stroke="#A89878" stroke-width=".6" fill="none" opacity=".6"></path>
  <rect x=".5" y=".5" width="131" height="77" fill="none" stroke="#4A4236"></rect>
</svg>"""


def dancheong_tile():
    motifs = "".join(
        f'<g transform="translate({x} 39)"><circle r="11" fill="#2F6E8E"></circle><circle r="7.5" fill="#E8DDBF"></circle>'
        f'<circle r="4.5" fill="#C8432B"></circle><circle r="1.6" fill="#E2B13C"></circle>'
        + "".join(f'<ellipse cx="0" cy="-9.2" rx="2.2" ry="3" fill="#3E8F7A" transform="rotate({a})"></ellipse>' for a in range(0, 360, 45))
        + '</g>' for x in (22, 66, 110))
    return f"""<svg width="132" height="78" viewBox="0 0 132 78" role="img" aria-label="단청 띠">
  <rect width="132" height="78" fill="#3E8F7A"></rect>
  <rect y="0" width="132" height="10" fill="#C8432B"></rect><rect y="10" width="132" height="3" fill="#E2B13C"></rect>
  <rect y="65" width="132" height="3" fill="#E2B13C"></rect><rect y="68" width="132" height="10" fill="#2F6E8E"></rect>
  {motifs}
  <path d="M44 20 V58 M88 20 V58" stroke="#1A1814" stroke-width="2" opacity=".5"></path>
  <rect x=".5" y=".5" width="131" height="77" fill="none" stroke="#4A4236"></rect>
</svg>"""


def shape_markers():
    m1 = f"""<svg width="44" height="44" viewBox="0 0 44 44" role="img" aria-label="조선 공방 표식"><rect x="6" y="6" width="32" height="32" rx="3" fill="#3A2318" stroke="{R1}" stroke-width="2.5"></rect><circle cx="22" cy="22" r="9" fill="{R1}"></circle><circle cx="19" cy="19" r="3" fill="{R1L}" opacity=".8"></circle></svg>"""
    m2 = f"""<svg width="44" height="44" viewBox="0 0 44 44" role="img" aria-label="요물 표식"><defs><filter id="s5-m2g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="2.5"></feGaussianBlur></filter></defs><path d="M22 5 C29 14 36 19 35 28 C34 35 29 39 22 39 C15 39 10 35 9 28 C8 19 15 14 22 5 Z" fill="{R2}" opacity=".6" filter="url(#s5-m2g)"></path><path d="M14 13 L10 4 L18 10 Z M30 13 L34 4 L26 10 Z" fill="{BRASS}"></path><path d="M22 7 C28 15 34 20 33 28 C32 34 27 37 22 37 C17 37 12 34 11 28 C10 20 16 15 22 7 Z" fill="{R2D2}" stroke="{R2}" stroke-width="2.5"></path><path d="M22 19 C25 23 28 26 27 30 C26 33 24 34 22 34 C20 34 18 33 17 30 C16 26 19 23 22 19 Z" fill="{R2L}"></path></svg>"""
    m3 = f"""<svg width="44" height="44" viewBox="0 0 44 44" role="img" aria-label="오방 신당 표식"><path d="M5 39 H39" stroke="{R3}" stroke-width="2.5" stroke-linecap="round"></path><path d="M11 39 V11" stroke="{R3L}" stroke-width="1.8" stroke-linecap="round"></path><path d="M11 11 l6 2.5 l-6 7 Z" fill="#2F6E8E" stroke="#120D08" stroke-width="0.6"></path><path d="M17 39 V8" stroke="{R3L}" stroke-width="1.8" stroke-linecap="round"></path><path d="M17 8 l6 2.5 l-6 7 Z" fill="#C8432B" stroke="#120D08" stroke-width="0.6"></path><path d="M22 39 V4" stroke="{R3L}" stroke-width="1.8" stroke-linecap="round"></path><path d="M22 4 l6 2.5 l-6 7 Z" fill="#E2B13C" stroke="#120D08" stroke-width="0.6"></path><path d="M27 39 V8" stroke="{R3L}" stroke-width="1.8" stroke-linecap="round"></path><path d="M27 8 l6 2.5 l-6 7 Z" fill="#EFE8D8" stroke="#120D08" stroke-width="0.6"></path><path d="M33 39 V11" stroke="{R3L}" stroke-width="1.8" stroke-linecap="round"></path><path d="M33 11 l6 2.5 l-6 7 Z" fill="#1A1814" stroke="#120D08" stroke-width="0.6"></path></svg>"""
    items = "".join(
        f'<div style="display: flex; align-items: center; gap: 8px; flex: 1; padding: 6px 8px; background: {PANEL}; border: 1px solid {LINE}">{svg}'
        f'<div style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 12px; font-weight: 600">{n}</span>'
        f'<span style="font-size: 11px; color: {MUTED}">{d}</span></div></div>'
        for svg, n, d in [(m1, "人 조선 공방", "천원지방 ■●"), (m2, "地 요물 무리", "뿔 · 불꽃"), (m3, "天 오방 신당", "오방 깃발")])
    return section("세력 형태 언어 · 지도 표식", f'<div style="display: flex; gap: 8px">{items}</div>')


def right_col():
    inputs = f"""<div style="display: flex; flex-direction: column; gap: 10px">
  <div style="display: flex; gap: 10px">{field("서버 주소", "ws://127.0.0.1:8080", "idle")}{field("이름", "김정호", "focus")}</div>
  {field("서버 주소", "127.0.0.1:8080", "error")}
</div>"""
    tabs = (f'<div style="display: flex; border-bottom: 1px solid {LINE}">'
            + "".join(f'<span style="padding: 8px 14px; font-size: 14px; font-weight: {w}; color: {c}; border-bottom: 3px solid {b}; margin-bottom: -1px">{t}</span>'
                      for t, w, c, b in [("방 목록", 600, INK, HWANG), ("직접 입력", 400, MUTED, "transparent"), ("재접속", 400, MUTED, "transparent")])
            + f'<span style="margin-left: auto; align-self: center; font-family: {MONO}; font-size: 11px; color: {MUTED}">Tab 전환</span></div>')
    hp = "".join(f'<span style="flex: 1; height: 6px; background: {c}"></span>' for c in [OK] * 7 + ["#3A4A3E"] * 3)
    panel = f"""<div style="background: {PANEL}; border: 1px solid {LINE}; box-shadow: 0 6px 16px rgba(0,0,0,.4)">
  {dancheong_band(5)}
  <div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 12px 6px">
    <span style="font-family: {DISPLAY}; font-weight: 800; font-size: 17px">선택 · 화승총수 ×4</span>
    <span style="font-family: {MONO}; font-size: 11px; color: {R1}">조선 공방</span>
  </div>
  <div style="display: flex; flex-direction: column; gap: 6px; padding: 0 12px 12px">
    <div style="display: flex; gap: 2px">{hp}</div>
    <div style="display: flex; justify-content: space-between; font-size: 12px; color: {MUTED}"><span>체력 70 / 100</span><span>사거리 6 · 공격 8</span></div>
  </div>
</div>"""
    patterns = "".join(f'<figure style="margin: 0; display: flex; flex-direction: column; gap: 4px">{t}<figcaption style="font-size: 11px; color: {MUTED}">{c}</figcaption></figure>'
                       for t, c in [(lattice_tile(), "문살 · 구분선/배경"), (hanji_tile(), "한지 결 · 종이 면"), (dancheong_tile(), "단청 · 머리띠")])
    return "".join([
        section("입력 · 기본 / 초점 / 오류", inputs),
        section("탭", tabs),
        section("패널 · 단청 머리띠", panel),
        section("무늬", f'<div style="display: flex; gap: 10px">{patterns}</div>'),
        shape_markers(),
    ])


def toasts():
    items = [(ALERT, "!", "본진이 공격받고 있습니다", "F1 · 위치로 이동", "rgba(229,96,77,.12)"),
             (OK, "✓", "화승총수 생산 완료", "훈련원 · 대기열 2", "rgba(140,199,154,.10)"),
             (HWANG, "↻", "서버에 다시 연결했습니다", "tick 004820부터 이어서", "rgba(226,177,60,.10)"),
             (ORE, "◇", "귀화석 광맥 고갈", "북동 광맥 · 남은 0", "rgba(159,227,208,.08)")]
    inner = "".join(
        f'<div style="flex: 1; display: flex; align-items: center; gap: 12px; padding: 10px 14px; background: linear-gradient(90deg, {bg}, {PANEL} 60%); '
        f'border: 1px solid {LINE}; border-left: 4px solid {c}; box-shadow: 0 6px 14px rgba(0,0,0,.35)">'
        f'<span style="width: 28px; height: 28px; flex: none; display: flex; align-items: center; justify-content: center; border-radius: 50%; '
        f'background: {c}; color: {HEUK}; font-weight: 700; font-size: 15px">{g}</span>'
        f'<div style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 14px; font-weight: 600">{t}</span>'
        f'<span style="font-size: 12px; color: {MUTED}">{d}</span></div></div>' for c, g, t, d, bg in items)
    return section("알림 토스트 · 경고 / 완료 / 재접속 / 자원", f'<div style="display: flex; gap: 12px">{inner}</div>')


def btn_matrix():
    states = [("normal", "기본"), ("hover", "올림"), ("pressed", "누름"), ("disabled", "잠김")]
    rows = [("primary", "출전", "주 버튼"), ("secondary", "대기열", "보조"), ("danger", "항복", "위험")]
    grid = "display: grid; grid-template-columns: 52px repeat(4, minmax(0, 1fr)); gap: 10px; align-items: center"
    head = (f'<div style="{grid}"><span></span>'
            + "".join(f'<span style="font-family: {MONO}; font-size: 10px; letter-spacing: .08em; color: #D8C6A2; text-align: center">{l}</span>'
                      for _, l in states) + '</div>')
    body = ""
    for v, label, name in rows:
        cells = "".join(ornate_button(label, v, st, h=40, size=15).replace("padding: 0 22px;", "width: 100%;") for st, _ in states)
        body += f'<div style="{grid}"><span style="font-size: 12px; color: #D8C6A2">{name}</span>{cells}</div>'
    inner = f'<div style="display: flex; flex-direction: column; gap: 14px">{head}{body}</div>'
    return section("옻칠 버튼 · 3종 × 4상태", framed("s5-bt", 432, 232, inner, b=14, pad=14, crest=False, seed=12))


def frame_parts():
    lac = (f'<div style="display: flex; flex-direction: column; gap: 6px; height: 100%; justify-content: center">'
           f'<span style="font-family: {DISPLAY}; font-weight: 800; font-size: 16px; color: #F2CF7E">옻칠 목재 틀</span>'
           f'<span style="font-size: 12px; line-height: 1.5; color: #D8C6A2">메뉴 · 선택 · 명령 패널</span></div>')
    par = (f'<div style="display: flex; flex-direction: column; gap: 6px; height: 100%; justify-content: center">'
           f'<span style="font-family: {DISPLAY}; font-weight: 800; font-size: 16px; color: #2A1A0C">한지 두루마리</span>'
           f'<span style="font-size: 12px; line-height: 1.5; color: #3E2A16">결과 · 규칙 · 도움말 본문</span></div>')
    notes = "".join(
        f'<div style="display: flex; align-items: center; gap: 8px; font-size: 12px; color: {INK}">'
        f'<span style="width: 18px; height: 18px; flex: none; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; '
        f'background: #A8321E; border: 1px solid #E9C37A; font-family: {MONO}; font-size: 10px; color: #FFF1D6">{n}</span>{t}</div>'
        for n, t in [(1, "놋쇠 모서리 · 리벳 3개"), (2, "구름문 머리 장식"), (3, "이중 홈 · 위 밝음 / 아래 그늘"), (4, "오목 패널 · 안쪽 그림자 12px")])
    frames = (f'<div style="display: flex; gap: 14px; align-items: center">'
              f'{framed("s5-fl", 200, 112, lac, b=14, pad=10, seed=21)}'
              f'{framed("s5-fp", 200, 112, par, b=14, pad=10, panel="parchment", seed=22)}</div>')
    return section("틀 부품", frames + f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 14px; margin-top: 4px">{notes}</div>')


def ornaments():
    clouds = (f'<svg width="200" height="104" viewBox="0 0 200 104" role="img" aria-label="구름문 장식">'
              f'<defs>{wood_defs("s5-cl", 6)}</defs>'
              '<rect width="200" height="104" rx="3" fill="#1E1610"></rect>'
              f'{crest_ornament("s5-cl", 52, 42, 1.15)}'
              f'<g fill="#3E8F7A" stroke="#E2B13C" stroke-width=".8">{cloud_path(146, 42, .95)}</g>'
              f'{cloud_curls(146, 42, .95, "#E8DDBF", 1.1)}'
              '<path d="M14 74 H186" stroke="#E9C37A" stroke-width="1" opacity=".6"></path>'
              f'<text x="52" y="92" text-anchor="middle" font-family="{MONO}" font-size="10" fill="#D8C6A2">놋쇠 · 틀 머리</text>'
              f'<text x="146" y="92" text-anchor="middle" font-family="{MONO}" font-size="10" fill="#D8C6A2">단청 · 띠 장식</text></svg>')
    meds = "".join(
        f'<figure style="margin: 0; display: flex; flex-direction: column; align-items: center; gap: 4px">{medallion(p, 96, w)}'
        f'<figcaption style="font-size: 11px; color: {MUTED}">{c}</figcaption></figure>'
        for p, w, c in [("s5-md1", "gunner", "아군 초상"), ("s5-md2", "goblin", "적 초상")])
    return section("초상 메달리온 · 구름문", f'<div style="display: flex; gap: 12px; align-items: flex-start">{meds}{clouds}</div>')


def _poly(pts, fill, extra=""):
    return '<path d="M' + " L".join(f"{f(x)} {f(y)}" for x, y in pts) + f' Z" fill="{fill}" {extra}></path>'


def iso_spec():
    ox, oy, hw, hh = 150, 34, 28, 14
    P = lambda i, j, z=0: (ox + (i - j) * hw, oy + (i + j) * hh - z)
    g = []
    for i in range(5):
        for j in range(5):
            g.append(_poly([P(i, j), P(i + 1, j), P(i + 1, j + 1), P(i, j + 1)], "#2E2820" if (i + j) % 2 else "#272219",
                           'stroke="#4A4236" stroke-width=".8"'))
    g.append(_poly([P(0, 0), P(1, 0), P(1, 1), P(0, 1)], "none", 'stroke="#E2B13C" stroke-width="1.8"'))
    bi, bj, z = 2, 2, 42
    g.append(_poly([P(bi, bj + 2), P(bi + 2, bj + 2), P(bi + 2, bj + 2, z), P(bi, bj + 2, z)], "#B8956A", f'stroke="{OUTLINE}"'))
    g.append(_poly([P(bi + 2, bj), P(bi + 2, bj + 2), P(bi + 2, bj + 2, z), P(bi + 2, bj, z)], "#5A4028", f'stroke="{OUTLINE}"'))
    g.append(_poly([P(bi, bj, z), P(bi + 2, bj, z), P(bi + 2, bj + 2, z), P(bi, bj + 2, z)], "#4A5056", f'stroke="{OUTLINE}"'))
    lx, ly = P(bi + 1, bj + 2, z / 2)
    rx, ry = P(bi + 2, bj + 1, z / 2)
    t1 = P(1, 0)
    notes = "".join(f'<text x="302" y="{y}" fill="{c}">{t}</text>' for y, t, c in [
        (40, "화면 x = (i − j) · 32", "#D8C6A2"), (58, "화면 y = (i + j) · 16 − z", "#D8C6A2"),
        (84, "외곽선 #120d08 · 60%", "#A89E8A"), (102, "그림자 우하단 · 흐림 3", "#A89E8A"),
        (120, "지붕 = 기와 줄 + 막새", "#A89E8A"), (138, "유닛 5–6등신 · 45° 내려봄", "#A89E8A")])
    svg = (f'<svg width="432" height="196" viewBox="0 0 432 196" role="img" aria-label="아이소메트릭 격자 규격">'
           '<defs><marker id="s5-arw" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">'
           '<path d="M0 0 L10 5 L0 10 Z" fill="#F2CF7E"></path></marker></defs>'
           '<rect width="432" height="196" rx="3" fill="#1A1611" stroke="#4A4236"></rect>'
           + "".join(g) +
           f'<text x="{f(lx)}" y="{f(ly + 4)}" text-anchor="middle" font-family="{BODY}" font-size="9.5" font-weight="700" fill="#2A1A0C">남서 · 밝음</text>'
           f'<text x="{f(rx)}" y="{f(ry + 4)}" text-anchor="middle" font-family="{BODY}" font-size="9.5" font-weight="700" fill="#F2E2C2">남동 · 그늘</text>'
           '<path d="M26 24 L56 44" stroke="#F2CF7E" stroke-width="2" marker-end="url(#s5-arw)"></path>'
           f'<text x="14" y="16" font-family="{MONO}" font-size="10" fill="#F2CF7E">빛 · 좌상단</text>'
           f'<text x="{f(t1[0] + 10)}" y="{f(t1[1] + 4)}" font-family="{MONO}" font-size="10" fill="#E2B13C">64 × 32 타일</text>'
           f'<g font-family="{BODY}" font-size="10.5">{notes}</g></svg>')
    return section("2.5D 아이소 격자 · 2:1", svg)


def patterns_row():
    tiles = "".join(f'<figure style="margin: 0; display: flex; flex-direction: column; gap: 4px">{t}<figcaption style="font-size: 11px; color: {MUTED}">{c}</figcaption></figure>'
                    for t, c in [(lattice_tile(), "문살 · 배경"), (hanji_tile(), "한지 결 · 종이 면"), (dancheong_tile(), "단청 · 머리띠")])
    return section("무늬", f'<div style="display: flex; gap: 18px">{tiles}</div>')


def build():
    body = f"""<div style="width: 1440px; height: 900px; box-sizing: border-box; padding: 26px 48px; background: radial-gradient(120% 90% at 50% 0%, #241C14 0%, {GROUND} 60%); color: {INK}; font-family: {BODY}; display: flex; flex-direction: column; gap: 16px; overflow: hidden">
  <header style="display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 12px; border-bottom: 1px solid #5A4630; box-shadow: 0 1px 0 rgba(0,0,0,.6)">
    <div style="display: flex; align-items: center; gap: 12px">
      {gold_title("s5-wm", "삼파전", 190, 64, 56)}
      {seal("s5-seal", 48)}
      <div style="display: flex; flex-direction: column; gap: 4px; margin-left: 6px">
        <span style="font-family: {MONO}; font-size: 11px; letter-spacing: .5em; color: #D8C6A2">SAMPAJEON</span>
        <span style="font-size: 13px; color: {MUTED}">2.5D 조선펑크 · 옻칠 목재 · 놋쇠 · 한지 · 오방색</span>
      </div>
    </div>
    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px">
      <span style="font-family: {MONO}; font-size: 11px; letter-spacing: .14em; color: {HWANG}">S5 · DESIGN TOKENS · HI-FI</span>
      {gold_title("s5-tt", "디자인 토큰 · 장식 UI 키트", 520, 52, 34, anchor="end", x=514)}
    </div>
  </header>
  <div style="display: grid; grid-template-columns: 404px 432px minmax(0, 1fr); gap: 30px; min-height: 0">
    <div style="display: flex; flex-direction: column; gap: 16px">{colors()}{shape_markers()}</div>
    <div style="display: flex; flex-direction: column; gap: 18px">{type_scale()}{btn_matrix()}{patterns_row()}</div>
    <div style="display: flex; flex-direction: column; gap: 14px">{frame_parts()}{ornaments()}{iso_spec()}</div>
  </div>
</div>"""
    return page("삼파전 디자인 토큰", body, 1440, 900)


if __name__ == "__main__":
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(build())
    print("wrote", os.path.abspath(OUT))
