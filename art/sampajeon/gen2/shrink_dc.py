"""Shrink a .dc.html artboard under the Design canvas limit without changing how it looks.

The canvas silently drops any file over 2 MiB when it loads (parseContent in the type's app.js),
so run this after a generator: `python3 shrink_dc.py <file> [--prec 1] [--out path]`.

Only SVG geometry is touched: unused defs are removed, ids get shorter, coordinates lose decimals
that are invisible at 2x, and repeated presentation attributes move to shared classes. Text, inline
style attributes, <helmet> and the data-dc-script block are left alone.
"""
import argparse
import re
import sys

LIMIT = 2 * 1024 * 1024
GEOM_ATTRS = ("x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "fx", "fy", "width", "height", "stroke-width")
NUM = re.compile(r"-?\d*\.?\d+(?:e-?\d+)?")
SVG_BLOCK = re.compile(r"<svg\b.*?</svg>", re.S)
TAG = re.compile(r"<([a-zA-Z][a-zA-Z0-9]*)((?:\s+[a-zA-Z_:][-a-zA-Z0-9_:.]*(?:=\"[^\"]*\")?)*)\s*(/?)>")
# objectBoundingBox fractions and filter regions must keep their precision
EXACT_TAGS = {"linearGradient", "radialGradient", "pattern", "filter", "stop", "feGaussianBlur", "feOffset",
              "feTurbulence", "feColorMatrix", "feDisplacementMap", "feComposite", "feMerge", "feMergeNode",
              "feFlood", "feBlend", "feDropShadow", "feSpecularLighting", "fePointLight", "feDistantLight",
              "feMorphology", "feImage", "feTile", "feComponentTransfer", "feFuncA", "feFuncR", "feFuncG", "feFuncB"}


def fmt(v, prec):
    s = f"{v:.{prec}f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    if s.startswith("0."):
        s = s[1:]
    elif s.startswith("-0."):
        s = "-" + s[2:]
    return s


def round_value(val, prec):
    if not NUM.fullmatch(val.strip()):
        return val
    v = float(val)
    return fmt(v, 2 if abs(v) < 1 else prec)


def shrink_path(d, prec):
    """Round path numbers and drop separators that the path grammar does not need."""
    def rep(m):
        v = float(m.group(0))
        return fmt(v, prec)
    # arc flags are not plain numbers; leave any path with arcs at full structure, numbers only
    d = NUM.sub(rep, d)
    d = re.sub(r"\s*([MmLlHhVvCcSsQqTtAaZz])\s*", r"\1", d)
    d = re.sub(r"\s+-", "-", d)
    d = re.sub(r"\s+", " ", d).strip()
    return d


def shrink_points(p, prec):
    return re.sub(r"\s+", " ", NUM.sub(lambda m: fmt(float(m.group(0)), prec), p)).strip()


def shrink_transform(t, prec):
    # translate/rotate only; scale and matrix keep their precision
    def rep(m):
        name, args = m.group(1), m.group(2)
        if name not in ("translate", "rotate"):
            return m.group(0)
        return f"{name}({NUM.sub(lambda n: fmt(float(n.group(0)), prec), args)})"
    return re.sub(r"(\w+)\(([^)]*)\)", rep, t)


def rewrite_tags(svg, prec):
    def rep(m):
        tag, attrs, slash = m.group(1), m.group(2), m.group(3)
        exact = tag in EXACT_TAGS and 'gradientUnits="userSpaceOnUse"' not in attrs

        def attr(a):
            name, val = a.group(1), a.group(2)
            if name == "d":
                return f'd="{shrink_path(val, prec)}"'
            if name == "points":
                return f'points="{shrink_points(val, prec)}"'
            if name == "transform":
                return f'transform="{shrink_transform(val, prec)}"'
            if name in GEOM_ATTRS and not exact and "%" not in val:
                return f'{name}="{round_value(val, prec)}"'
            return a.group(0)

        attrs = re.sub(r'([a-zA-Z_:][-a-zA-Z0-9_:.]*)="([^"]*)"', attr, attrs)
        return f"<{tag}{attrs}{slash}>"
    return TAG.sub(rep, svg)


def prune_unused_defs(html):
    """Remove gradients, filters, clip paths and masks that nothing refers to, until stable."""
    pat = re.compile(r'<(linearGradient|radialGradient|filter|clipPath|mask|pattern)\b[^>]*\bid="([^"]+)"[^>]*>.*?</\1>', re.S)
    for _ in range(4):
        refs = set(re.findall(r'url\(#([^)"\']+)\)', html)) | set(re.findall(r'href="#([^"]+)"', html))
        before = len(html)
        html = pat.sub(lambda m: m.group(0) if m.group(2) in refs else "", html)
        if len(html) == before:
            break
    return html


def dedupe_defs(html):
    """Point references at one copy of byte-identical defs. Inline SVG ids resolve across the whole document."""
    pat = re.compile(r'<(linearGradient|radialGradient|filter|clipPath|mask|pattern)\b([^>]*?)\s*\bid="([^"]+)"([^>]*)>(.*?)</\1>', re.S)
    first, alias = {}, {}
    for m in pat.finditer(html):
        key = (m.group(1), m.group(2), m.group(4), m.group(5))
        if key in first:
            alias[m.group(3)] = first[key]
        else:
            first[key] = m.group(3)
    if not alias:
        return html
    html = re.sub(r'url\(#([^)"\']+)\)', lambda m: f"url(#{alias.get(m.group(1), m.group(1))})", html)
    return re.sub(r'href="#([^"]+)"', lambda m: f'href="#{alias.get(m.group(1), m.group(1))}"', html)


def b36(n):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    out = ""
    while True:
        out = digits[n % 36] + out
        n //= 36
        if n == 0:
            return out


def shorten_ids(html):
    """Rename SVG ids to short tokens. Ids used outside SVG geometry stay valid because every reference moves too."""
    ids = list(dict.fromkeys(re.findall(r'\bid="([^"]+)"', html)))
    refs = set(re.findall(r'url\(#([^)"\']+)\)', html)) | set(re.findall(r'href="#([^"]+)"', html))
    table = {old: f"k{b36(i)}" for i, old in enumerate(i for i in ids if i in refs)}

    html = re.sub(r'url\(#([^)"\']+)\)', lambda m: f"url(#{table.get(m.group(1), m.group(1))})", html)
    html = re.sub(r'href="#([^"]+)"', lambda m: f'href="#{table.get(m.group(1), m.group(1))}"', html)
    html = re.sub(r'\bid="([^"]+)"', lambda m: f'id="{table[m.group(1)]}"' if m.group(1) in table else "", html)
    return html


CLASS_ATTRS = ("fill", "stroke", "stroke-width", "stroke-opacity", "fill-opacity", "opacity", "stroke-linecap", "stroke-linejoin")


def to_classes(html):
    """Move repeated presentation-attribute sets to one shared class per set, declared in <helmet>.

    A class on the same element paints exactly like the attributes it replaces. url() values stay as attributes."""
    attr_re = re.compile(r'\s(' + "|".join(CLASS_ATTRS) + r')="([^"]*)"')
    el_re = re.compile(r"<(path|circle|ellipse|rect|line|polygon|polyline)\b([^>]*)>")

    def sig(attrs):
        return tuple(sorted((n, v) for n, v in attr_re.findall(attrs) if "url(" not in v))

    counts = {}
    for block in SVG_BLOCK.findall(html):
        for m in el_re.finditer(block):
            if ' class="' in m.group(2):
                continue
            k = sig(m.group(2))
            if len(k) >= 2:
                counts[k] = counts.get(k, 0) + 1
    # a class pays for itself only when its uses save more than its CSS rule costs
    cost = lambda k: sum(len(n) + len(v) + 4 for n, v in k)
    chosen = sorted((k for k, c in counts.items() if c * (cost(k) - 10) > cost(k) + 8), key=lambda k: -counts[k] * cost(k))
    names = {k: "z" + b36(i) for i, k in enumerate(chosen)}

    def el(m):
        tag, attrs = m.group(1), m.group(2)
        if ' class="' in attrs:
            return m.group(0)
        k = sig(attrs)
        if k not in names:
            return m.group(0)
        keep = attr_re.sub(lambda a: a.group(0) if "url(" in a.group(2) else "", attrs)
        return f'<{tag} class="{names[k]}"{keep}>'

    html = SVG_BLOCK.sub(lambda m: el_re.sub(el, m.group(0)), html)
    css = "".join(f".{names[k]}{{" + ";".join(f"{n}:{v}" for n, v in k) + "}" for k in chosen)
    return html.replace("</helmet>", f"<style>{css}</style></helmet>", 1)


def merge_strokes(html):
    """Join neighbouring unfilled paths that have identical attributes into one path (dense scatter such as grass)."""
    pat = re.compile(r'<path\s+((?:(?!\bd=)[^>])*?)\s*d="([^"]*)"([^>]*)></path>(?=<path)')
    # classes hide fill:none, so only attributes declared in the CSS rule or on the element count
    css = dict(re.findall(r"\.(z[0-9a-z]+)\{([^}]*)\}", html))

    def unfilled(attrs):
        if 'fill="none"' in attrs:
            return True
        m = re.search(r'class="(z[0-9a-z]+)"', attrs)
        return bool(m) and "fill:none" in css.get(m.group(1), "")

    def join(block):
        out, cur_key, cur_d = [], None, []

        def flush():
            nonlocal cur_key, cur_d
            if cur_key is not None:
                out.append(f'<path {cur_key[0]} d="{"".join(cur_d)}"{cur_key[1]}></path>' if cur_key[0] else f'<path d="{"".join(cur_d)}"{cur_key[1]}></path>')
            cur_key, cur_d = None, []

        pos = 0
        for m in re.finditer(r'<path\s+((?:(?!\bd=)[^>])*?)\s*d="([^"]*)"([^>]*)></path>', block):
            if m.start() != pos:
                flush()
                out.append(block[pos:m.start()])
            key = (m.group(1), m.group(3))
            if key != cur_key or not unfilled(m.group(1) + m.group(3)):
                flush()
                cur_key = key
            cur_d.append(m.group(2))
            pos = m.end()
        flush()
        out.append(block[pos:])
        return "".join(out)

    return SVG_BLOCK.sub(lambda m: join(m.group(0)), html)


def tighten_scatter(html):
    """Extra savings for very dense scenes: rotated ellipses stop repeating their centre, and in joined
    stroke paths (grass, pebble marks) each blade's start point snaps to a whole unit. Blade shapes stay as drawn."""
    def ell(m):
        a, x, y = m.group(1), m.group(2), m.group(3)
        attrs = m.group(0)
        if f'cx="{x}"' not in attrs or f'cy="{y}"' not in attrs:
            return attrs
        attrs = attrs.replace(f' cx="{x}"', "").replace(f' cy="{y}"', "")
        return attrs.replace(f'transform="rotate({a} {x} {y})"', f'transform="translate({x} {y})rotate({a})"')

    html = re.sub(r'<ellipse\b[^>]*transform="rotate\((-?[\d.]+) (-?[\d.]+) (-?[\d.]+)\)"[^>]*>', ell, html)

    def snap(m):
        d = m.group(1)
        if d.count("M") < 2:
            return m.group(0)
        return 'd="' + re.sub(r"M(-?[\d.]+)(?:\s|(?=-))(-?[\d.]+)", lambda p: f"M{round(float(p.group(1)))} {round(float(p.group(2)))}", d) + '"'

    return re.sub(r'\bd="([^"]*)"', snap, html)


def compact_whitespace(svg):
    svg = re.sub(r">\s+<", "><", svg)
    return re.sub(r"\s+(/?>)", r"\1", svg)


def shrink(html, prec=1, merge=False):
    html = dedupe_defs(html)
    html = prune_unused_defs(html)
    html = shorten_ids(html)
    html = SVG_BLOCK.sub(lambda m: compact_whitespace(rewrite_tags(m.group(0), prec)), html)
    html = to_classes(html)
    return tighten_scatter(merge_strokes(html)) if merge else html


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--prec", type=int, default=1)
    ap.add_argument("--out")
    ap.add_argument("--merge", action="store_true", help="also join neighbouring strokes and tighten scatter; use only when still over the limit")
    a = ap.parse_args()
    src = open(a.file, encoding="utf-8").read()
    out = shrink(src, a.prec, a.merge)
    path = a.out or a.file
    open(path, "w", encoding="utf-8").write(out)
    n0, n1 = len(src.encode()), len(out.encode())
    print(f"{a.file}: {n0 / 1048576:.2f} -> {n1 / 1048576:.2f} MiB ({'OK' if n1 <= LIMIT else 'OVER LIMIT'})")
    return 0 if n1 <= LIMIT else 1


if __name__ == "__main__":
    sys.exit(main())
