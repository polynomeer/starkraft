#!/usr/bin/env python3
"""Export game sprites from the design generators and write data/visuals.json.

Each sprite is drawn without its card ground, rendered to a transparent PNG by headless Chrome,
and listed in the manifest with the point that sits on the map (anchor) and its size in tiles.

Usage: python3 art/sampajeon/export_sprites.py [--only Name ...]
"""
import argparse
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
GEN = HERE / "gen2"
RES = ROOT / "sim" / "src" / "main" / "resources"
OUT_REL = "assets/sprites/sampajeon"
OUT = RES / OUT_REL
VISUALS = RES / "data" / "visuals.json"
sys.path.insert(0, str(GEN))
os.chdir(GEN)

CHROME_CANDIDATES = [
    os.environ.get("CHROME", ""),
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    shutil.which("google-chrome") or "",
    shutil.which("chromium") or "",
]


def r1_art():
    """조선 공방 art. Ground patches are removed so sprites sit on the game's terrain."""
    import r1_figs as F
    import r1_lib as L
    import r1_scenes as S

    # Yard props only make sense on the card's ground patch.
    S.ground_patch = S.straw_sacks = S.jars = S.lantern = lambda *a, **k: []
    defs = lambda: L.defs() + F.KK.defs_svg()
    join = "".join

    def gas_vent():
        return join([
            L.ell(60, 94, 40, 13, "#000", opacity=0.45, filter=L.u("shadow")),
            L.ell(60, 88, 34, 10, "#3A3430"),
            L.ell(60, 87, 24, 6.5, "#0E0A10"),
            L.ell(60, 62, 30, 34, L.u("gas"), filter=L.u("glow-l"), opacity=0.8),
            L.ell(60, 86, 19, 4, "#E8DCFF", opacity=0.7, filter=L.u("blur1")),
            L.ell(56, 66, 11, 8, "#C9B4EE", opacity=0.42, filter=L.u("blur1")),
            L.ell(66, 44, 14, 9, "#A898C8", opacity=0.3, filter=L.u("blur1")),
            L.ell(58, 24, 16, 9, "#A898C8", opacity=0.18, filter=L.u("blur1")),
        ])

    return defs, {
        "artisan": lambda: F.artisan(),
        "gunner": lambda: F.gunner(),
        "hall": lambda: join(S.b_hall()),
        "granary": lambda: join(S.b_granary()),
        "still": lambda: join(S.b_still()),
        "ore": lambda: join(L.ore_crystal(60, 70, 2.1)),
        "gas": gas_vent,
    }


def r2_art():
    """요물 무리 art. Importing race_r2 also writes its artboard into design2/project (git-ignored)."""
    import race_r2 as R

    R.diorama = lambda *a, **k: ""
    defs = lambda: R.DEFS + R.K.defs_svg()
    return defs, {"brute": R.unit_brute}


# name, art source, viewBox, pixel width, anchor in viewBox units, width on the map in tiles
SPRITES = {
    "artisan": ("r1", "artisan", (-70, -205, 140, 223), 128, (0, 0), 1.7),
    "gunner": ("r1", "gunner", (-80, -178, 160, 196), 160, (0, 0), 2.1),
    "brute": ("r2", "brute", (30, -45, 200, 231), 192, (125, 170), 2.5),
    "hall": ("r1", "hall", (0, 0, 190, 150), 512, (95, 84), 3.4),
    "granary": ("r1", "granary", (0, 0, 190, 150), 384, (98, 70), 3.2),
    "still": ("r1", "still", (0, 0, 190, 150), 384, (85, 88), 3.2),
    "ore": ("r1", "ore", (0, 0, 120, 100), 128, (60, 72), 2.0),
    "gas": ("r1", "gas", (0, 0, 120, 110), 128, (60, 88), 2.2),
}

# What each current game type uses until the roster changes (plan stage 4).
MANIFEST = {
    "units": {"Worker": "artisan", "Marine": "gunner", "Zergling": "brute"},
    "buildings": {"Depot": "hall", "ResourceDepot": "granary", "GasDepot": "still"},
    # The client snapshot reports a node's entity type as its kind, so both spellings are listed.
    "resources": {"minerals": "ore", "gas": "gas", "MineralField": "ore", "GasGeyser": "gas"},
}


def png_size(path):
    with open(path, "rb") as fh:
        head = fh.read(24)
    return struct.unpack(">II", head[16:24])


def find_chrome():
    for c in CHROME_CANDIDATES:
        if c and os.path.exists(c):
            return c
    raise SystemExit("Chrome not found. Set CHROME to its path.")


def render(chrome, defs, body, vb, width, dest):
    x, y, w, h = vb
    height = round(width * h / w)
    # Chrome keeps a minimum window width, so the sprite is drawn at the page's top-left and checked below.
    html = (f'<!doctype html><meta charset="utf-8"><style>html,body{{margin:0;background:transparent;overflow:hidden}}</style>'
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="{x} {y} {w} {h}" style="display:block">'
            f"<defs>{defs}</defs>{body}</svg>")
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "s.html"
        page.write_text(html, encoding="utf-8")
        subprocess.run(
            [chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
             f"--window-size={width},{height}", f"--screenshot={dest}", page.as_uri()],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
    got = png_size(dest)
    if got != (width, height):
        raise SystemExit(f"{dest.name}: expected {width}x{height}, got {got[0]}x{got[1]}")
    return width, height


def manifest_entry(name):
    _, _, vb, _, anchor, tiles = SPRITES[name]
    return {
        "asset": f"{OUT_REL}/{name}.png",
        "anchorX": round((anchor[0] - vb[0]) / vb[2], 4),
        "anchorY": round((anchor[1] - vb[1]) / vb[3], 4),
        "widthTiles": tiles,
    }


def write_manifest():
    old = json.loads(VISUALS.read_text(encoding="utf-8"))
    data = {
        "units": {k: manifest_entry(v) for k, v in MANIFEST["units"].items()},
        "unitArchetypes": {},
        "buildings": {k: manifest_entry(v) for k, v in MANIFEST["buildings"].items()},
        "buildingArchetypes": {},
        "resources": {k: manifest_entry(v) for k, v in MANIFEST["resources"].items()},
        "terrain": old.get("terrain", {}),
    }
    VISUALS.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", help="sprite names to export")
    args = ap.parse_args()
    names = args.only or list(SPRITES)
    chrome = find_chrome()
    OUT.mkdir(parents=True, exist_ok=True)
    sources = {}
    total = 0
    for name in names:
        src, key, vb, width, _, _ = SPRITES[name]
        if src not in sources:
            sources[src] = {"r1": r1_art, "r2": r2_art}[src]()
        defs, art = sources[src]
        dest = OUT / f"{name}.png"
        body = art[key]()  # the kit registers gradients while drawing, so read defs afterwards
        w, h = render(chrome, defs(), body, vb, width, dest)
        size = dest.stat().st_size
        total += size
        print(f"{name:10} {w}x{h} {size // 1024} KB")
    write_manifest()
    print(f"total {total // 1024} KB, manifest {VISUALS.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
