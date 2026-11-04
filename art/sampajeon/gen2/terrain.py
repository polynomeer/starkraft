# 삼파전 SAMPAJEON — T 지형·배경 sheet generator (high-fidelity 2.5D isometric pass).
# Usage: python3 terrain.py <out.html>
# Previous top-down version: terrain_v1_topdown.py
import math, random, sys


def f(v):
    s = ('%.1f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('', '-0') else s


def hx(c):
    return tuple(int(c[k:k + 2], 16) for k in (1, 3, 5))


def mix(a, b, t):
    ra, rb = hx(a), hx(b)
    return '#%02X%02X%02X' % tuple(round(x + (y - x) * t) for x, y in zip(ra, rb))


def dk(c, t):
    return mix(c, '#000000', t)


def lt(c, t):
    return mix(c, '#FFFFFF', t)


def P(pts, close=True):
    d = 'M' + ' L'.join(f'{f(x)} {f(y)}' for x, y in pts)
    return d + (' Z' if close else '')


OL = ' stroke="#120D08" stroke-opacity="0.6" stroke-width="0.8" stroke-linejoin="round"'


def poly(pts, fill, extra=''):
    return f'<path d="{P(pts)}" fill="{fill}"{extra}></path>'


def line(pts, col, w, extra=''):
    return f'<path d="{P(pts, False)}" fill="none" stroke="{col}" stroke-width="{f(w) if w >= 0.1 else "0.1"}" stroke-linecap="round" stroke-linejoin="round"{extra}></path>'


def ell(x, y, rx, ry, fill, extra=''):
    return f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rx)}" ry="{f(ry)}" fill="{fill}"{extra}></ellipse>'


def stop_markup(stops):
    out = ''
    for s in stops:
        o, col, op = (tuple(s) + (1,))[:3]
        extra = '' if op == 1 else f' stop-opacity="{op}"'
        out += f'<stop offset="{o}" stop-color="{col}"{extra}></stop>'
    return out


# ---------- per-SVG definition registry (unique, prefixed ids) ----------
class Ctx:
    def __init__(self, p):
        self.p, self.defs, self.keys, self.n = p, [], {}, 0

    def _add(self, params, markup):
        if params not in self.keys:
            self.n += 1
            fid = f'{self.p}-{self.n}'
            self.keys[params] = fid
            self.defs.append(markup.replace('@ID', fid))
        return self.keys[params]

    def noise(self, seed, freq, rgb, amp, off, octv=4):
        r, g, b = rgb
        return self._add(('n', seed, freq, rgb, amp, off, octv),
            f'<filter id="@ID" x="0" y="0" width="1" height="1" color-interpolation-filters="sRGB">'
            f'<feTurbulence type="fractalNoise" baseFrequency="{freq}" numOctaves="{octv}" seed="{seed}"></feTurbulence>'
            f'<feColorMatrix type="matrix" values="0 0 0 0 {f(r)} 0 0 0 0 {f(g)} 0 0 0 0 {f(b)} {amp} 0 0 0 {off}"></feColorMatrix></filter>')

    def blur(self, sd):
        return self._add(('b', sd),
            f'<filter id="@ID" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="{sd}"></feGaussianBlur></filter>')

    def ink(self, seed, scale, freq=0.035):
        return self._add(('i', seed, scale, freq),
            f'<filter id="@ID" x="-25%" y="-25%" width="150%" height="150%">'
            f'<feTurbulence type="fractalNoise" baseFrequency="{freq}" numOctaves="4" seed="{seed}" result="t"></feTurbulence>'
            f'<feDisplacementMap in="SourceGraphic" in2="t" scale="{scale}" xChannelSelector="R" yChannelSelector="G" result="d"></feDisplacementMap>'
            f'<feGaussianBlur in="d" stdDeviation="0.9"></feGaussianBlur></filter>')

    def gray(self, sat=0.12):
        return self._add(('g', sat), f'<filter id="@ID"><feColorMatrix type="saturate" values="{sat}"></feColorMatrix></filter>')

    def lin(self, stops, x1=0, y1=0, x2=0, y2=1):
        st = stop_markup(stops)
        return self._add(('l', tuple(stops), x1, y1, x2, y2),
            f'<linearGradient id="@ID" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{st}</linearGradient>')

    def rad(self, stops, cx=0.5, cy=0.5, r=0.5):
        st = stop_markup(stops)
        return self._add(('r', tuple(stops), cx, cy, r),
            f'<radialGradient id="@ID" cx="{cx}" cy="{cy}" r="{r}">{st}</radialGradient>')

    def clip(self, d):
        self.n += 1
        fid = f'{self.p}-{self.n}'
        self.defs.append(f'<clipPath id="{fid}"><path d="{d}"></path></clipPath>')
        return fid

    def svg(self, label, inner, w, h, vb):
        return (f'<svg width="{w}" height="{h}" viewBox="{vb}" role="img" aria-label="{label}">'
                f'<defs>{"".join(self.defs)}</defs>{inner}</svg>')


class Iso:
    def __init__(self, ox, oy, W=64, H=32):
        self.ox, self.oy, self.W, self.H = ox, oy, W, H

    def p(self, i, j, z=0):
        return (self.ox + (i - j) * self.W / 2, self.oy + (i + j) * self.H / 2 - z)


# ---------- ground painting ----------
GK = {  # base light, base dark, mottle rgb, speckle rgb
    'grass': ('#7C7644', '#585632', (0.17, 0.19, 0.08), (0.82, 0.76, 0.44)),
    'grass2': ('#6F7A42', '#4A5530', (0.12, 0.15, 0.07), (0.70, 0.78, 0.44)),
    'dirt': ('#8A6E4D', '#5E4A33', (0.22, 0.15, 0.08), (0.86, 0.72, 0.50)),
    'wet': ('#5E4E3A', '#3A3024', (0.12, 0.09, 0.06), (0.58, 0.50, 0.38)),
    'moss': ('#34423F', '#1C2624', (0.06, 0.10, 0.10), (0.42, 0.66, 0.70)),
    'mortar': ('#4A4538', '#35312A', (0.10, 0.09, 0.07), (0.55, 0.52, 0.45)),
    'ash': ('#4A4238', '#2E2822', (0.08, 0.07, 0.06), (0.50, 0.46, 0.40)),
}


def ground(c, pts, kind, seed, scale=1.0, streak=True):
    a, b, dkc, ltc = GK[kind]
    g = c.lin([(0, a), (1, b)], 0, 0, 1, 1)
    cid = c.clip(P(pts))
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    x0, y0, w, h = min(xs) - 2, min(ys) - 2, max(xs) - min(xs) + 4, max(ys) - min(ys) + 4
    r = f'x="{f(x0)}" y="{f(y0)}" width="{f(w)}" height="{f(h)}"'
    n1 = c.noise(seed, round(0.045 / scale, 4), dkc, 2.6, -1.1)
    n2 = c.noise(seed + 5, round(0.75 / scale, 3), ltc, 3.0, -1.95, 2)
    n3 = c.noise(seed + 9, round(1.2 / scale, 3), tuple(v * 0.5 for v in dkc), 3.0, -1.7, 2)
    out = (f'<g clip-path="url(#{cid})"><path d="{P(pts)}" fill="url(#{g})"></path>'
           f'<rect {r} filter="url(#{n1})" opacity="{0.45 if scale > 1 else 0.6}"></rect>'
           f'<rect {r} filter="url(#{n2})" opacity="0.5"></rect>'
           f'<rect {r} filter="url(#{n3})" opacity="0.55"></rect>')
    if streak:  # anisotropic noise reads as brush strokes
        n4 = c.noise(seed + 13, f'{round(0.012 / scale, 4)} {round(0.11 / scale, 3)}', ltc, 2.2, -1.25, 3)
        out += f'<rect {r} filter="url(#{n4})" opacity="0.35"></rect>'
    return out + '</g>'


def tuft(x, y, s, col, rng, blades=5):
    d = ''
    for k in range(blades):
        dx = (k - (blades - 1) / 2) * 0.75 * s + rng.uniform(-0.3, 0.3) * s
        hh = rng.uniform(2.6, 4.4) * s
        d += f'M{f(x + dx * 0.2)} {f(y)} q{f(dx * 0.3)} {f(-hh * 0.6)} {f(dx)} {f(-hh)} '
    return (f'<path d="{d}" fill="none" stroke="#1E1A0E" stroke-opacity="0.45" stroke-width="{f(0.75 * s)}" stroke-linecap="round" transform="translate(0.5 0.4)"></path>'
            f'<path d="{d}" fill="none" stroke="{col}" stroke-width="{f(0.6 * s)}" stroke-linecap="round"></path>')


TUFT_AUTUMN = ('#9A9050', '#8A8A48', '#B0A060', '#6E7A3E', '#C4AC64')


def pebble(x, y, r, rng, tone=None):
    tone = tone or rng.choice(['#8E8676', '#7C7566', '#9A917E', '#6E685A', '#A39A86'])
    return (ell(x + r * 0.5, y + r * 0.35, r * 1.15, r * 0.6, '#1A140D', ' opacity="0.5"')
            + ell(x, y, r * 1.15, r * 0.65, tone)
            + ell(x - r * 0.3, y - r * 0.22, r * 0.55, r * 0.28, '#E2D9C2', ' opacity="0.55"'))


def leaf(x, y, rng, s=1.0):
    col = rng.choice(['#B8452A', '#D46A2E', '#E09A3A', '#8E3020', '#C8822E'])
    return ell(x, y, 1.2 * s, 0.6 * s, col, f' transform="rotate({rng.randint(0, 180)} {f(x)} {f(y)})"')


def flower(x, y, rng, s=1.0):
    col = rng.choice(['#EDE6D0', '#E8C850', '#B89AD8', '#E8E0F0'])
    return ell(x, y, 0.9 * s, 0.6 * s, col) + ell(x, y - 0.1, 0.35 * s, 0.25 * s, '#6A4A1A')


def scatter(iso, rng, i0, j0, a, b, n_t=0, n_f=0, n_l=0, n_p=0, s=1.0, z=0, tcols=TUFT_AUTUMN):
    out = ''
    pt = lambda: iso.p(i0 + rng.random() * a, j0 + rng.random() * b, z)
    for _ in range(n_p):
        x, y = pt(); out += pebble(x, y, rng.uniform(0.6, 1.4) * s, rng)
    for _ in range(n_l):
        x, y = pt(); out += leaf(x, y, rng, s)
    for _ in range(n_t):
        x, y = pt(); out += tuft(x, y, s * rng.uniform(0.8, 1.2), rng.choice(tcols), rng)
    for _ in range(n_f):
        x, y = pt(); out += flower(x, y, rng, s)
    return out


def flagstones(c, iso, i0, j0, a, b, ni, nj, rng, z=0, tones=('#9A9384', '#8C8576', '#A59C8A', '#7E786A', '#938B7A')):
    out = ''
    du, dv = a / ni, b / nj
    for ii in range(ni):
        for jj in range(nj):
            g = 0.035 * min(du, dv) / 0.25
            jit = lambda: rng.uniform(-0.03, 0.03) * min(du, dv) / 0.25
            u0, v0 = i0 + ii * du + g, j0 + jj * dv + g
            u1, v1 = i0 + (ii + 1) * du - g, j0 + (jj + 1) * dv - g
            N = iso.p(u0 + jit(), v0 + jit(), z); E = iso.p(u1 + jit(), v0 + jit(), z)
            S = iso.p(u1 + jit(), v1 + jit(), z); W = iso.p(u0 + jit(), v1 + jit(), z)
            tone = rng.choice(tones)
            out += poly([N, E, S, W], tone)
            out += line([W, N, E], lt(tone, 0.35), 0.6, ' opacity="0.8"')
            out += line([E, S, W], dk(tone, 0.45), 0.7, ' opacity="0.8"')
            if rng.random() < 0.25:
                cx, cy = iso.p((u0 + u1) / 2, (v0 + v1) / 2, z)
                out += line([(cx - 1.5, cy), (cx, cy + 0.5), (cx + 1.2, cy - 0.3)], dk(tone, 0.5), 0.35)
    return out


def plate(c, iso, i0, j0, a, b, depth=4, col='#4A3B2A'):
    """Thin earth slab under a catalog tile so each tile reads as a 3D block."""
    W, S, E = iso.p(i0, j0 + b), iso.p(i0 + a, j0 + b), iso.p(i0 + a, j0)
    lf = [W, S, (S[0], S[1] + depth), (W[0], W[1] + depth)]
    rf = [S, E, (E[0], E[1] + depth), (S[0], S[1] + depth)]
    return poly(lf, dk(col, 0.1), OL) + poly(rf, dk(col, 0.4), OL)


def box(iso, i, j, a, b, z0, h, top, left, right, ol=OL):
    N, E, S, W = iso.p(i, j, z0 + h), iso.p(i + a, j, z0 + h), iso.p(i + a, j + b, z0 + h), iso.p(i, j + b, z0 + h)
    Sb, Wb, Eb = iso.p(i + a, j + b, z0), iso.p(i, j + b, z0), iso.p(i + a, j, z0)
    out = ''
    if left: out += poly([W, S, Sb, Wb], left, ol)
    if right: out += poly([S, E, Eb, Sb], right, ol)
    if top: out += poly([N, E, S, W], top, ol)
    return out


def cast_shadow(c, pts, dx, dy, op=0.45, sd=2.5):
    return poly([(x + dx, y + dy) for x, y in pts], '#0E0A06', f' opacity="{op}" filter="url(#{c.blur(sd)})"')


# ---------- vegetation & rocks ----------
def pine(c, x, y, s, rng):
    out = ell(x + 8 * s, y + 2.5 * s, 10 * s, 3.6 * s, '#0E0A06', f' opacity="0.42" filter="url(#{c.blur(round(1.6 * s, 1))})"')
    tg = c.lin([(0, '#7A5232'), (1, '#3A2416')], 0, 0, 1, 0)
    out += poly([(x - 1.3 * s, y), (x - 0.9 * s, y - 9 * s), (x + 0.9 * s, y - 9 * s), (x + 1.4 * s, y)], f'url(#{tg})', OL)
    tiers = 5
    for k in range(tiers):
        ty = y - 7 * s - k * 4.3 * s
        r = (9.5 - k * 1.7) * s
        out += ell(x + 0.6 * s, ty + 0.9 * s, r, r * 0.5, '#0F1A12', ' opacity="0.85"')
        out += ell(x, ty, r, r * 0.52, '#1F3325')
        for m in range(7):
            ang = math.pi * (0.95 + m * 0.16) + rng.uniform(-0.1, 0.1)
            bx, by = x + math.cos(ang) * r * 0.62, ty + math.sin(ang) * r * 0.3 - 0.8 * s
            out += ell(bx, by, r * 0.36, r * 0.2, rng.choice(['#2E4A32', '#33503A', '#2A4430']),
                       f' transform="rotate({rng.randint(-25, 25)} {f(bx)} {f(by)})"')
        for m in range(3):
            bx, by = x - r * (0.15 + 0.2 * m), ty - r * 0.18 - m * 0.4 * s
            out += ell(bx, by, r * 0.22, r * 0.1, rng.choice(['#56764A', '#668554', '#7C9660']))
    out += ell(x - 0.4 * s, y - 7 * s - tiers * 4.3 * s + 2.5 * s, 1.6 * s, 2.4 * s, '#2E4A32')
    return out


def maple(c, x, y, s, rng):
    out = ell(x + 9 * s, y + 2.5 * s, 11 * s, 4 * s, '#0E0A06', f' opacity="0.42" filter="url(#{c.blur(round(1.7 * s, 1))})"')
    tg = c.lin([(0, '#6A4630'), (1, '#2E1E14')], 0, 0, 1, 0)
    out += poly([(x - 1.4 * s, y), (x - 0.8 * s, y - 10 * s), (x - 4 * s, y - 15 * s), (x - 3 * s, y - 15.5 * s), (x, y - 12 * s),
                 (x + 3.2 * s, y - 16 * s), (x + 4 * s, y - 15.4 * s), (x + 0.9 * s, y - 10 * s), (x + 1.5 * s, y)], f'url(#{tg})', OL)
    cy = y - 19 * s
    out += ell(x + 1.2 * s, cy + 1.5 * s, 11.5 * s, 8.2 * s, '#4A160E')
    cols = ['#7A2418', '#9A2E1C', '#B83E22', '#D0582E', '#E07A36', '#E8A048']
    for m in range(34):
        ang, rr = rng.uniform(0, 2 * math.pi), rng.uniform(0, 1) ** 0.6
        bx, by = x + math.cos(ang) * 10 * s * rr, cy + math.sin(ang) * 6.8 * s * rr
        lit = max(0, min(5, int(2.6 - (bx - x) / (5 * s) - (by - cy) / (4 * s) + rng.uniform(-0.8, 0.8))))
        out += ell(bx, by, rng.uniform(2.2, 3.4) * s, rng.uniform(1.6, 2.4) * s, cols[lit])
    for m in range(5):
        bx, by = x - rng.uniform(2, 7) * s, cy - rng.uniform(2.5, 5.5) * s
        out += ell(bx, by, 1.3 * s, 0.9 * s, '#F2B868', ' opacity="0.8"')
    return out


def boulder(c, x, y, s, rng, tones=('#8C8474', '#6E685A', '#4E493F', '#B4AB96')):
    lit, mid, dark, hi = tones
    n = 7
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n + rng.uniform(-0.2, 0.2)
        pts.append((x + math.cos(a) * s * rng.uniform(0.85, 1.1), y + math.sin(a) * s * 0.55 * rng.uniform(0.85, 1.1) - s * 0.35))
    out = ell(x + s * 0.6, y + s * 0.15, s * 1.15, s * 0.45, '#0E0A06', f' opacity="0.45" filter="url(#{c.blur(round(0.5 + s * 0.15, 1))})"')
    out += poly(pts, dark, OL)
    top = [(px - (px - x) * 0.22 - s * 0.12, py - (py - y) * 0.3 - s * 0.38) for px, py in pts]
    out += poly(top[:5], mid)
    out += poly([top[3], top[4], top[5], (x - s * 0.1, y - s * 0.55)], lit)
    out += line([top[4], top[5], top[6]], hi, max(0.4, s * 0.09), ' opacity="0.75"')
    if rng.random() < 0.6:
        out += ell(x - s * 0.2, y - s * 0.6, s * 0.35, s * 0.12, '#6E7A3E', ' opacity="0.7"')
    return out


def crystal(c, x, y, h, w, lean, glow=True):
    L, R, F, T = (x - w / 2, y), (x + w / 2, y - w * 0.08), (x + w * 0.08, y + w * 0.28), (x + lean, y - h)
    gl = c.lin([(0, '#E6FFF8'), (0.55, '#9FE3D0'), (1, '#5FB8A6')], 0, 0, 1, 1)
    gr = c.lin([(0, '#6CC4B2'), (1, '#24564F')], 0, 0, 1, 1)
    out = ''
    if glow:
        out += ell(x, y + w * 0.1, w * 1.4, w * 0.55, '#9FE3D0', f' opacity="0.35" filter="url(#{c.blur(round(w * 0.35, 1))})"')
    out += poly([L, (x - w * 0.15, y - w * 0.3), T], '#2E6E66', OL)
    out += poly([L, F, T], f'url(#{gl})', OL)
    out += poly([F, R, T], f'url(#{gr})', OL)
    out += line([F, T], '#F2FFFB', max(0.3, w * 0.07), ' opacity="0.85"')
    out += line([(L[0] + w * 0.12, L[1] - h * 0.15), (T[0] - w * 0.08, T[1] + h * 0.25)], '#FFFFFF', max(0.2, w * 0.05), ' opacity="0.55"')
    return out


def sparkle(x, y, s=1.6):
    return (f'<path d="M{f(x)} {f(y - s * 2)} L{f(x + s * 0.4)} {f(y - s * 0.4)} L{f(x + s * 2)} {f(y)} L{f(x + s * 0.4)} {f(y + s * 0.4)} '
            f'L{f(x)} {f(y + s * 2)} L{f(x - s * 0.4)} {f(y + s * 0.4)} L{f(x - s * 2)} {f(y)} L{f(x - s * 0.4)} {f(y - s * 0.4)} Z" fill="#FFFFFF" opacity="0.9"></path>')


def reeds(x, y, s, rng, n=9):
    out = ''
    for _ in range(n):
        dx, hh = rng.uniform(-3, 3) * s, rng.uniform(8, 14) * s
        bend = rng.uniform(1, 3) * s
        out += f'<path d="M{f(x + dx)} {f(y)} q{f(bend * 0.3)} {f(-hh * 0.6)} {f(bend)} {f(-hh)}" fill="none" stroke="#7A6A42" stroke-width="{f(0.7 * s)}" stroke-linecap="round"></path>'
        out += ell(x + dx + bend, y - hh - 1.2 * s, 0.9 * s, 2.2 * s, rng.choice(['#D8C8A0', '#C8B48A', '#E4D8B8']),
                   f' transform="rotate({f(18 + bend * 4)} {f(x + dx + bend)} {f(y - hh - 1.2 * s)})"')
    return out


def strawstack(c, x, y, s):
    g = c.lin([(0, '#E2C47A'), (0.6, '#B8954E'), (1, '#7A5E30')], 0, 0, 1, 1)
    out = ell(x + 5 * s, y + 1.5 * s, 8 * s, 3 * s, '#0E0A06', f' opacity="0.45" filter="url(#{c.blur(round(1.2 * s, 1))})"')
    out += f'<path d="M{f(x - 6 * s)} {f(y)} Q{f(x - 6.5 * s)} {f(y - 9 * s)} {f(x)} {f(y - 14 * s)} Q{f(x + 6.5 * s)} {f(y - 9 * s)} {f(x + 6 * s)} {f(y)} Q{f(x)} {f(y + 3 * s)} {f(x - 6 * s)} {f(y)} Z" fill="url(#{g})"{OL}></path>'
    for k in range(3):
        yy = y - (3 + k * 3.5) * s
        ww = 6 * s * (1 - k * 0.22)
        out += f'<path d="M{f(x - ww)} {f(yy)} Q{f(x)} {f(yy + 2 * s)} {f(x + ww)} {f(yy)}" fill="none" stroke="#6A4E26" stroke-width="{f(0.6 * s)}"></path>'
    out += f'<path d="M{f(x - 1.4 * s)} {f(y - 13.2 * s)} L{f(x)} {f(y - 16 * s)} L{f(x + 1.4 * s)} {f(y - 13.2 * s)} Z" fill="#8A6A38"></path>'
    return out


# ---------- built structures ----------
def stone_wall(c, iso, i0, j0, L, axis, rng, h=11, t=0.2):
    if axis == 'i':
        out = box(iso, i0, j0, L, t, 0, h, '#5A5446', '#4A443A', '#2E2A24')
        F = lambda u, z: iso.p(i0 + u, j0 + t, z)
        shade = 0.0
    else:
        out = box(iso, i0, j0, t, L, 0, h, '#5A5446', '#4A443A', '#2E2A24')
        F = lambda u, z: iso.p(i0 + t, j0 + u, z)
        shade = 0.35
    bands = [(0.4, 4.2), (4.4, 7.8), (8.0, h - 0.3)]
    for z0, z1 in bands:
        u = rng.uniform(0, 0.08)
        while u < L - 0.02:
            u1 = min(L, u + rng.uniform(0.09, 0.2))
            g = 0.012
            pts = [F(u + g, z0), F(u1 - g, z0 + rng.uniform(-0.4, 0.4)), F(u1 - g, z1), F(u + g, z1 + rng.uniform(-0.5, 0.3))]
            tone = dk(rng.choice(['#A39A86', '#8E8676', '#9C927C', '#7E7666', '#B0A68E']), shade)
            out += poly(pts, tone)
            out += line([pts[3], pts[2]], lt(tone, 0.3), 0.5, ' opacity="0.8"')
            out += line([pts[0], pts[1]], dk(tone, 0.5), 0.5, ' opacity="0.8"')
            u = u1
    # giwa coping
    o = 0.06
    if axis == 'i':
        E = lambda u: iso.p(i0 + u, j0 + t + o, h + 1.5)
        R = lambda u: iso.p(i0 + u, j0 + t / 2, h + 5)
    else:
        E = lambda u: iso.p(i0 + t + o, j0 + u, h + 1.5)
        R = lambda u: iso.p(i0 + t / 2, j0 + u, h + 5)
    us = [-0.04 + k * (L + 0.08) / 10 for k in range(11)]
    cg = c.lin([(0, '#6A7078'), (1, '#3A3E44')], 0, 0, 0, 1)
    out += poly([R(us[0])] + [E(u) for u in us] + [R(us[-1])], dk('#3A3E44', shade) if shade else f'url(#{cg})', OL)
    k = 0.0
    while k < L + 0.04:
        out += line([E(k - 0.02), R(k - 0.02)], '#23262A', 0.9) + line([E(k), R(k)], '#8A9098', 0.4, ' opacity="0.7"')
        k += 0.07
    out += line([R(us[0]), R(us[-1])], '#1E2125', 1.6) + line([R(us[0]), R(us[-1])], '#D8D2C4', 0.5, ' transform="translate(0 -0.8)"')
    return out


def house(c, iso, i, j, a, b, wh, rh, rng, kind='giwa', dan=True, o=0.26):
    out = ''
    fp = [iso.p(i - o, j - o), iso.p(i + a + o, j - o), iso.p(i + a + o, j + b + o), iso.p(i - o, j + b + o)]
    out += cast_shadow(c, fp, (wh + rh) * 0.55, (wh + rh) * 0.2, 0.45, 2.5)
    # stone foundation
    out += box(iso, i - 0.06, j - 0.06, a + 0.12, b + 0.12, 0, 3, '#7E786A', '#8C8576', '#5A554A')
    fl = lambda u, z: iso.p(i + u, j + b, z)
    fr = lambda u, z: iso.p(i + a, j + u, z)
    plaster_l, plaster_r = ('#DCD2BA', '#A89E88') if kind == 'giwa' else ('#B09470', '#7E6A50')
    timber = '#5A3624'
    out += box(iso, i, j, a, b, 3, wh - 3, None, plaster_l, plaster_r)
    for face, span, sh in ((fl, a, 0), (fr, b, 0.35)):
        n = max(1, round(span / 0.42))
        for k in range(n):
            u0, u1 = span * k / n, span * (k + 1) / n
            if (k % 2 == 1 or n == 1) and wh > 10:
                w0, w1, z0, z1 = u0 + span / n * 0.18, u1 - span / n * 0.18, 5.5, wh - 4.5
                win = [face(w0, z0), face(w1, z0), face(w1, z1), face(w0, z1)]
                out += poly(win, dk('#E8DFC6', sh), OL)
                for m in range(1, 4):
                    uu = w0 + (w1 - w0) * m / 4
                    out += line([face(uu, z0), face(uu, z1)], dk('#6A4630', sh), 0.5)
                zz = z0
                while zz < z1 - 0.5:
                    out += line([face(w0, zz), face(w1, zz)], dk('#6A4630', sh), 0.4)
                    zz += 1.6
            out += line([face(u0, 3), face(u0, wh)], dk(timber, sh), 1.3)
        out += line([face(span, 3), face(span, wh)], dk(timber, sh), 1.3)
        out += poly([face(0, wh - 3.2), face(span, wh - 3.2), face(span, wh), face(0, wh)], dk(timber, sh))
        out += line([face(0, 6.6 if wh > 14 else 4.2), face(span, 6.6 if wh > 14 else 4.2)], dk(timber, sh), 0.8)
        if dan and kind == 'giwa':
            band = [face(0, wh - 2.8), face(span, wh - 2.8), face(span, wh - 0.4), face(0, wh - 0.4)]
            out += poly(band, dk('#3E8F7A', sh))
            uu = 0.05
            while uu < span - 0.05:
                x, y = face(uu, wh - 1.6)
                out += ell(x, y, 0.75, 0.6, dk('#C8432B', sh)) + ell(x, y, 0.3, 0.25, dk('#E2B13C', sh))
                uu += 0.11
        ao = [face(0, wh - 5), face(span, wh - 5), face(span, wh), face(0, wh)]
        out += poly(ao, '#000000', ' opacity="0.28"')
    out += roof(c, iso, i, j, a, b, wh, rh, kind, o)
    return out


def roof(c, iso, i, j, a, b, wh, rh, kind, o):
    up = 3.2 if kind == 'giwa' else 1.0
    eL = lambda t: iso.p(i - o + t * (a + 2 * o), j + b + o, wh + up * (2 * t - 1) ** 2)
    eR = lambda t: iso.p(i + a + o, j - o + t * (b + 2 * o), wh + up * (2 * t - 1) ** 2)
    N = iso.p(i - o, j - o, wh + up)
    zr = wh + rh
    half = min(a, b) / 2
    R1 = iso.p(i + half, j + b / 2, zr)
    R2 = iso.p(i + a - half, j + b / 2, zr)
    ts = [k / 14 for k in range(15)]
    if kind == 'giwa':
        cf = (c.lin([(0, '#737A84'), (1, '#40454C')], 0, 0, 0, 1), '#2E3237', '#2A2D33', '#363A40')
    else:
        cf = (c.lin([(0, '#D4B676'), (0.6, '#A8864C'), (1, '#7A5E34')], 0, 0, 0, 1), '#7A6034', '#5E4A2A', '#8A6C3C')
    front, east, back, west = cf
    out = poly([N, eR(0), R2, R1], back, OL)
    out += poly([eL(0), N, R1], west, OL)
    out += poly([eR(t) for t in reversed(ts)] + [R2], east, OL)
    out += poly([eL(t) for t in ts] + [R2, R1], f'url(#{front})', OL)

    def top_front(t):
        u = i - o + t * (a + 2 * o)
        jt = max(j + b / 2, (j + b + o) - min(u - (i - o), (i + a + o) - u))
        return iso.p(u, jt, wh + rh * ((j + b + o) - jt) / (b / 2 + o))

    def top_east(t):
        v = j - o + t * (b + 2 * o)
        it = max(i + a - half, (i + a + o) - min(v - (j - o), (j + b + o) - v))
        return iso.p(it, v, wh + rh * ((i + a + o) - it) / (b / 2 + o))

    if kind == 'giwa':
        n = max(6, int((a + 2 * o) / 0.075))
        for k in range(1, n):
            t = k / n
            E, T = eL(t), top_front(t)
            out += line([E, T], '#24282D', 0.95) + line([(E[0] - 0.7, E[1]), (T[0] - 0.7, T[1])], '#9AA1AA', 0.45, ' opacity="0.6"')
            out += ell(E[0], E[1] + 0.3, 0.85, 0.7, '#8A9098', OL)
        n = max(4, int((b + 2 * o) / 0.075))
        for k in range(1, n):
            t = k / n
            E, T = eR(t), top_east(t)
            out += line([E, T], '#1C1F23', 0.85) + line([(E[0] - 0.6, E[1]), (T[0] - 0.6, T[1])], '#5E646C', 0.4, ' opacity="0.6"')
            out += ell(E[0], E[1] + 0.3, 0.75, 0.6, '#5E646C')
        for q in (0.33, 0.66):
            out += line([(eL(t)[0] + (top_front(t)[0] - eL(t)[0]) * q, eL(t)[1] + (top_front(t)[1] - eL(t)[1]) * q) for t in ts], '#1E2125', 0.4, ' opacity="0.5"')
    else:
        for pts, sd, op in (([eL(t) for t in ts] + [R2, R1], 51, 0.55), ([eR(t) for t in reversed(ts)] + [R2], 52, 0.45)):
            xs, ys = [p[0] for p in pts], [p[1] for p in pts]
            nf = c.noise(sd, '0.35 0.035', (0.30, 0.22, 0.10), 2.6, -1.25, 2)
            out += (f'<g clip-path="url(#{c.clip(P(pts))})"><rect x="{f(min(xs))}" y="{f(min(ys))}" width="{f(max(xs) - min(xs))}" '
                    f'height="{f(max(ys) - min(ys))}" filter="url(#{nf})" opacity="{op}"></rect></g>')
        out += line([(eL(t)[0] + (top_front(t)[0] - eL(t)[0]) * 0.55, eL(t)[1] + (top_front(t)[1] - eL(t)[1]) * 0.55) for t in ts[2:-2]], '#5A4424', 0.8, ' opacity="0.55"')
        hx0, hy0 = top_front(0.5)
        out += ell((hx0 + eL(0.35)[0]) / 2, (hy0 + eL(0.35)[1]) / 2, (a + 2 * o) * 9, rh * 0.35, '#F2DCA0', f' opacity="0.25" filter="url(#{c.blur(2)})"')
        for k in range(1, 22):
            t = k / 22
            E = eL(t)
            out += line([E, (E[0] - 0.3, E[1] + 2.0)], '#6A5028', 0.5)
        out += line([eL(t) for t in ts], '#E8D298', 0.6, ' opacity="0.6" transform="translate(0 -1.2)"')
    rc = '#1E2125' if kind == 'giwa' else '#5A4424'
    out += line([eL(0), R1], rc, 1.8) + line([eL(1), R2], rc, 1.8) + line([eR(0), R2], rc, 1.6)
    out += line([R1, R2], rc, 3.2)
    if kind == 'giwa':
        out += line([R1, R2], '#D8D2C4', 0.9, ' transform="translate(0 -1.4)"')
        for R, dx in ((R1, -1), (R2, 1)):
            out += f'<path d="M{f(R[0])} {f(R[1])} q{f(dx * 1.5)} -1 {f(dx * 2.6)} -3.6 q{f(-dx * 0.2)} 1.8 {f(-dx * 1.3)} 3.6 Z" fill="#1E2125"></path>'
    return out


def plateau(c, iso, i0, j0, a, b, h, rng, top_kind='grass', seed=1, scale=1.0):
    out = ''
    W, S, E = iso.p(i0, j0 + b), iso.p(i0 + a, j0 + b), iso.p(i0 + a, j0)
    Wt, St, Et, Nt = iso.p(i0, j0 + b, h), iso.p(i0 + a, j0 + b, h), iso.p(i0 + a, j0, h), iso.p(i0, j0, h)
    out += cast_shadow(c, [W, S, E, (E[0] + h * 0.6, E[1] + h * 0.3), (S[0] + h * 0.6, S[1] + h * 0.3)], 0, 0, 0.4, 3)

    def jag(p0, p1, n, amp):
        pts = []
        for k in range(n + 1):
            t = k / n
            pts.append((p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t + (rng.uniform(-amp, amp) if 0 < k < n else 0)))
        return pts
    nseg = max(6, int(a * 7))
    lf = jag(Wt, St, nseg, 1.6) + list(reversed(jag(W, S, nseg, 2.2)))
    rf = jag(St, Et, max(6, int(b * 7)), 1.6) + list(reversed(jag(S, E, max(6, int(b * 7)), 2.2)))
    gl = c.lin([(0, '#A69E8C'), (1, '#6A6456')], 0, 0, 0, 1)
    gr = c.lin([(0, '#5E584C'), (1, '#36322B')], 0, 0, 0, 1)
    for pts, g, sh in ((lf, gl, 0), (rf, gr, 1)):
        out += poly(pts, f'url(#{g})', OL)
        cid = c.clip(P(pts))
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        nf = c.noise(seed + 21 + sh, round(0.08 / scale, 3), (0.12, 0.11, 0.09), 2.4, -1.15, 3)
        inner = f'<rect x="{f(min(xs))}" y="{f(min(ys))}" width="{f(max(xs) - min(xs))}" height="{f(max(ys) - min(ys))}" filter="url(#{nf})" opacity="0.8"></rect>'
        p0, p1 = (W, S) if sh == 0 else (S, E)
        for k in range(1, int(h / 4.5)):
            z = k * 4.5 + rng.uniform(-1, 1)
            seg = [(p0[0] + (p1[0] - p0[0]) * t / 10, p0[1] + (p1[1] - p0[1]) * t / 10 - z + rng.uniform(-0.8, 0.8)) for t in range(11)]
            inner += line(seg, '#2A2620', 0.8, ' opacity="0.65"') + line([(x, y - 0.8) for x, y in seg], '#C4BCA6' if sh == 0 else '#7A7364', 0.5, ' opacity="0.5"')
        for k in range(int((a if sh == 0 else b) * 3)):
            t = rng.random()
            bx, by = p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t
            zz = rng.uniform(0, h * 0.6)
            crack = [(bx, by - zz)]
            for _ in range(4):
                crack.append((crack[-1][0] + rng.uniform(-1.5, 1.5), crack[-1][1] - rng.uniform(2, 4)))
            inner += line(crack, '#1E1A14', 0.7, ' opacity="0.7"')
        out += f'<g clip-path="url(#{cid})">{inner}</g>'
    top = [Nt, Et] + jag(Et, St, max(6, int(b * 7)), 0.6)[1:] + jag(St, Wt, nseg, 0.6)[1:]
    out += ground(c, top, top_kind, seed, scale)
    out += line(jag(Wt, St, nseg, 0.8) + jag(St, Et, max(6, int(b * 7)), 0.8)[1:], '#D2C9AE', 0.9, ' opacity="0.7"')
    tiso = Iso(iso.ox, iso.oy - h, iso.W, iso.H)
    edge = ''
    for k in range(int((a + b) * 8)):
        if rng.random() < 0.5:
            u = rng.random() * a; x, y = tiso.p(i0 + u, j0 + b - 0.04)
        else:
            v = rng.random() * b; x, y = tiso.p(i0 + a - 0.04, j0 + v)
        edge += tuft(x, y + 1.0, 0.9 * scale ** 0.3, rng.choice(TUFT_AUTUMN), rng)
    for _ in range(int((a + b) * 2)):
        t = rng.random()
        x, y = (W[0] + (S[0] - W[0]) * t, W[1] + (S[1] - W[1]) * t) if rng.random() < 0.6 else (S[0] + (E[0] - S[0]) * t, S[1] + (E[1] - S[1]) * t)
        edge += boulder(c, x + rng.uniform(-2, 2), y + rng.uniform(1, 4), rng.uniform(1.8, 3.6), rng)
    return out + edge, tiso


# ---------- water ----------
def water_fill(c, pts, seed, deep=False):
    g = c.lin([(0, '#4E8A8C'), (0.5, '#2E5E64'), (1, '#1C3E46')] if not deep else [(0, '#3A6E76'), (1, '#152E36')], 0, 0, 0.4, 1)
    cid = c.clip(P(pts))
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    r = f'x="{f(min(xs))}" y="{f(min(ys))}" width="{f(max(xs) - min(xs))}" height="{f(max(ys) - min(ys))}"'
    n = c.noise(seed, '0.02 0.18', (0.75, 0.88, 0.86), 2.6, -1.6, 3)
    return (f'<g clip-path="url(#{cid})"><path d="{P(pts)}" fill="url(#{g})"></path>'
            f'<rect {r} filter="url(#{n})" opacity="0.55"></rect></g>')


def ripples(rng, pts_fn, n, s=1.0):
    out = ''
    for _ in range(n):
        x, y = pts_fn()
        w = rng.uniform(2.5, 6) * s
        out += f'<path d="M{f(x - w)} {f(y)} q{f(w)} {f(-1.2 * s)} {f(2 * w)} 0" fill="none" stroke="#D8ECE6" stroke-width="{f(0.5 * s)}" opacity="{f(rng.uniform(0.35, 0.7))}"></path>'
    return out


def smooth(points, steps=10):
    pts = [points[0]] + points + [points[-1]]
    out = []
    for k in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[k - 1], pts[k], pts[k + 1], pts[k + 2]
        for s in range(steps):
            t = s / steps
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[q]) + (-p0[q] + p2[q]) * t + (2 * p0[q] - 5 * p1[q] + 4 * p2[q] - p3[q]) * t2 + (-p0[q] + 3 * p1[q] - 3 * p2[q] + p3[q]) * t3) for q in (0, 1)))
    out.append(points[-1])
    return out


def ribbon(pl, w_fn):
    left, right = [], []
    for k in range(len(pl)):
        a, b = pl[max(0, k - 1)], pl[min(len(pl) - 1, k + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        w = w_fn(k / (len(pl) - 1))
        left.append((pl[k][0] + nx * w, pl[k][1] + ny * w * 0.75))
        right.append((pl[k][0] - nx * w, pl[k][1] - ny * w * 0.75))
    return left, right


# ---------- terraces ----------
def paddy(c, iso, i, j, a, b, z, rng, ripe=True, h=4, rs=0.75, du=0.17, dv=0.13):
    out = box(iso, i, j, a, b, 0, z + h, None, '#7A6248', '#4E3E2C')
    out += box(iso, i, j, a, b, z + h - 0.01, 0.01, '#8A7254', None, None, '')
    ins = 0.08
    wpts = [iso.p(i + ins, j + ins, z + h), iso.p(i + a - ins, j + ins, z + h), iso.p(i + a - ins, j + b - ins, z + h), iso.p(i + ins, j + b - ins, z + h)]
    out += water_fill(c, wpts, 31)
    out += poly(wpts, 'none', ' stroke="#4A3826" stroke-width="1.2" stroke-opacity="0.8"')
    mx, my = iso.p(i + a * 0.3, j + b * 0.4, z + h)
    out += ell(mx, my, 9, 2.2, '#E8F2EE', f' opacity="0.25" filter="url(#{c.blur(1.5)})"')
    u = i + ins + 0.06
    cols = ['#D8B652', '#C8A040', '#E2C468', '#B88E36'] if ripe else ['#8A7A4A', '#7A6A3E']
    while u < i + a - ins - 0.04:
        v = j + ins + 0.06
        while v < j + b - ins - 0.04:
            x, y = iso.p(u + rng.uniform(-0.01, 0.01), v, z + h)
            if ripe:
                out += tuft(x, y, rs, rng.choice(cols), rng, 5)
                if rng.random() < 0.45:
                    out += ell(x + 0.8 * rs, y - 3.6 * rs, 1.1 * rs, 0.6 * rs, '#F0D888', ' opacity="0.85"')
            else:
                out += line([(x - 0.5, y), (x - 0.3, y - 1.4)], rng.choice(cols), 0.5) + line([(x + 0.4, y), (x + 0.5, y - 1.2)], rng.choice(cols), 0.5)
            v += dv
        u += du
    return out


# ---------- resources ----------
def ore_node(c, iso, rng, stage, cx, cy, s=1.0):
    out = ''
    for k in range(4):
        out += boulder(c, cx + rng.uniform(-11, 11) * s, cy + rng.uniform(-2, 5) * s, rng.uniform(3.5, 6) * s, rng)
    specs = [(-5, -1, 15, 5.5, -2), (2, -2, 20, 6.5, 1.5), (8, 1, 12, 5, 3), (-9, 3, 9, 4, -2.5), (4, 4, 11, 4.5, 1), (-1, 5, 8, 4, -1), (11, 5, 7, 3.5, 2)]
    if stage == 0:
        for dx, dy, h, w, ln in sorted(specs, key=lambda t: t[1]):
            out += crystal(c, cx + dx * s, cy + dy * s, h * s, w * s, ln * s)
        out += sparkle(cx + 2 * s, cy - 20 * s, 1.4 * s) + sparkle(cx - 6 * s, cy - 13 * s, 1.0 * s)
    elif stage == 1:
        for idx, (dx, dy, h, w, ln) in enumerate(sorted(specs, key=lambda t: t[1])):
            if idx in (1, 3, 5):
                out += crystal(c, cx + dx * s, cy + dy * s, h * 0.65 * s, w * 0.85 * s, ln * 0.5 * s)
            else:
                out += crystal(c, cx + dx * s, cy + dy * s, 3.2 * s, w * 0.8 * s, 0, glow=False)
        for _ in range(6):
            x, y = cx + rng.uniform(-12, 12) * s, cy + rng.uniform(2, 8) * s
            out += poly([(x, y), (x + 1.4 * s, y - 0.6 * s), (x + 0.6 * s, y + 0.8 * s)], '#9FE3D0', ' opacity="0.8"')
    else:
        for dx, dy, h, w, ln in specs:
            x, y = cx + dx * s, cy + dy * s
            out += poly([(x - w * 0.4 * s, y), (x - w * 0.2 * s, y - 2.2 * s), (x + w * 0.3 * s, y - 1.6 * s), (x + w * 0.4 * s, y)], '#4A5652', OL)
            out += line([(x - w * 0.2 * s, y - 2.2 * s), (x + w * 0.3 * s, y - 1.6 * s)], '#7E928C', 0.5)
        for _ in range(8):
            x, y = cx + rng.uniform(-12, 12) * s, cy + rng.uniform(1, 8) * s
            out += ell(x, y, 0.8 * s, 0.4 * s, '#6E7A76')
    return out


def gas_cleft(c, x, y, s, active, rng):
    out = ''
    pts = [(x - 16 * s, y), (x - 8 * s, y - 2.6 * s), (x - 2 * s, y - 2 * s), (x + 6 * s, y - 3.2 * s), (x + 15 * s, y - 0.6 * s),
           (x + 7 * s, y + 2.4 * s), (x, y + 2 * s), (x - 8 * s, y + 2.8 * s)]
    rim = [(px + (px - x) * 0.25, py + (py - y) * 0.6 + 0.8 * s) for px, py in pts]
    out += poly(rim, '#3A3430', OL)
    out += poly(pts, '#0E0A10', OL)
    if active:
        out += ell(x, y, 15 * s, 3.4 * s, '#B79AE0', f' opacity="0.65" filter="url(#{c.blur(round(2.2 * s, 1))})"')
        out += line([(x - 12 * s, y), (x - 4 * s, y - 0.6 * s), (x + 4 * s, y - 0.8 * s), (x + 12 * s, y)], '#E8DCFF', 0.7 * s, f' opacity="0.8" filter="url(#{c.blur(round(0.4 * s, 1))})"')
        for k in range(7):
            yy = y - (5 + k * 6.5) * s
            xx = x + (k * 1.6 + math.sin(k) * 2.5) * s
            rr = (5 + k * 2.2) * s
            out += ell(xx, yy, rr, rr * 0.62, '#C9B4EE' if k < 3 else '#A898C8', f' opacity="{f(0.5 - k * 0.06)}" filter="url(#{c.blur(round((1.8 + k * 0.9) * s, 1))})"')
        for _ in range(5):
            out += ell(x + rng.uniform(-10, 10) * s, y - rng.uniform(4, 30) * s, 0.6 * s, 0.6 * s, '#F2EAFF', ' opacity="0.8"')
    else:
        out += poly([(px * 0.6 + x * 0.4, py * 0.6 + y * 0.4) for px, py in pts], '#4A4440')
        out += line([(x - 10 * s, y), (x - 2 * s, y + 0.6 * s), (x + 8 * s, y - 0.4 * s)], '#6E6660', 0.6)
        out += ell(x - 3 * s, y - 4 * s, 4 * s, 2 * s, '#8A8090', f' opacity="0.25" filter="url(#{c.blur(round(1.5 * s, 1))})"')
    for _ in range(5):
        out += boulder(c, x + rng.uniform(-18, 18) * s, y + rng.uniform(2, 7) * s, rng.uniform(1.8, 3.2) * s, rng)
    return out


def blue_fire(c, x, y, s):
    out = ell(x, y - 4 * s, 6 * s, 7 * s, '#4FB3E8', f' opacity="0.5" filter="url(#{c.blur(round(2.6 * s, 1))})"')
    g = c.lin([(0, '#E8F8FF'), (0.4, '#8CD4F6'), (1, '#2F7EB8')], 0, 0, 0, 1)
    out += f'<path d="M{f(x)} {f(y - 11 * s)} C{f(x + 3 * s)} {f(y - 6 * s)} {f(x + 4 * s)} {f(y - 2 * s)} {f(x)} {f(y)} C{f(x - 4 * s)} {f(y - 2 * s)} {f(x - 2.6 * s)} {f(y - 6 * s)} {f(x)} {f(y - 11 * s)} Z" fill="url(#{g})"></path>'
    out += ell(x, y - 2.6 * s, 1.3 * s, 2 * s, '#FFFFFF', ' opacity="0.85"')
    return out


# ---------- catalog tiles (76×62 viewBox, one 64×32 diamond centred on 0,0) ----------
T = Iso(0, -16)
DIAMOND = [T.p(0, 0), T.p(1, 0), T.p(1, 1), T.p(0, 1)]
VB = '-38 -44 76 62.6'


def wavy(rng, n=10, base=0.5, amp=0.1):
    ph = rng.uniform(0, 6)
    return [(base + amp * math.sin(ph + k * 0.9) + rng.uniform(-0.03, 0.03), k / n) for k in range(n + 1)]


def t_grass(c, rng):
    return plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'grass', 3) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 16, 4, 6, 3)


def t_dirt(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7)
    out += line([T.p(0.15, 0.3), T.p(0.85, 0.3)], '#4A3A26', 1.6, ' opacity="0.55"') + line([T.p(0.15, 0.48), T.p(0.85, 0.48)], '#4A3A26', 1.6, ' opacity="0.55"')
    out += line([(x - 0.6, y - 0.6) for x, y in (T.p(0.15, 0.3), T.p(0.85, 0.3))], '#B49A72', 0.5, ' opacity="0.6"')
    out += scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 0, 0, 2, 9)
    out += scatter(T, rng, 0.0, 0.75, 1.0, 0.22, 6, 0, 0, 0)
    return out


def t_paved(c, rng):
    return plate(c, T, 0, 0, 1, 1, col='#5A5446') + ground(c, DIAMOND, 'mortar', 11) + flagstones(c, T, 0, 0, 1, 1, 4, 4, rng) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 3, 0, 2, 0, 0.8)


def t_grass_dirt(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7)
    bd = wavy(rng, 12, 0.48, 0.1)
    reg = [T.p(0, 0)] + [T.p(u, v) for u, v in bd] + [T.p(0, 1)]
    out += line([T.p(u + 0.03, v) for u, v in bd], '#2A2014', 2.4, f' opacity="0.45" filter="url(#{c.blur(1.2)})"')
    out += ground(c, reg, 'grass', 3)
    for u, v in bd:
        x, y = T.p(u, v)
        out += tuft(x, y + 0.5, 0.9, rng.choice(TUFT_AUTUMN), rng)
    out += scatter(T, rng, 0.02, 0.05, 0.35, 0.9, 6, 2, 3, 0) + scatter(T, rng, 0.62, 0.05, 0.35, 0.9, 0, 0, 1, 6)
    return out


def t_dirt_stone(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7)
    sub = ''
    for ii in range(4):
        for jj in range(4):
            if ii / 4 + rng.uniform(-0.05, 0.1) > 0.42:
                sub += flagstones(c, T, ii / 4, jj / 4, 0.25, 0.25, 1, 1, rng)
            elif ii / 4 > 0.2 and rng.random() < 0.5:
                x, y = T.p(ii / 4 + 0.12, jj / 4 + 0.12)
                sub += pebble(x, y, 1.6, rng)
    out += sub + scatter(T, rng, 0.02, 0.05, 0.3, 0.9, 3, 0, 2, 5)
    return out


def t_shore(c, rng):
    out = plate(c, T, 0, 0, 1, 1, col='#3A3226') + ground(c, DIAMOND, 'grass', 3)
    bd = [(v, u) for u, v in wavy(rng, 12, 0.5, 0.08)]
    sand = [T.p(0, 0), T.p(1, 0)] + [T.p(u, min(1, v - 0.12)) for u, v in reversed(bd)]
    wet = [T.p(u, v - 0.12) for u, v in bd] + [T.p(1, 1), T.p(0, 1)]
    wat = [T.p(u, v) for u, v in bd] + [T.p(1, 1), T.p(0, 1)]
    out += ground(c, wet, 'wet', 15)
    out += water_fill(c, wat, 17)
    out += line([T.p(u, v) for u, v in bd], '#E8F2EC', 1.4, f' opacity="0.8" filter="url(#{c.blur(0.5)})"')
    out += line([T.p(u, v + 0.05) for u, v in bd], '#D8ECE6', 0.6, ' opacity="0.5" stroke-dasharray="2 2"')
    out += ripples(rng, lambda: T.p(rng.uniform(0.2, 0.9), rng.uniform(0.7, 0.95)), 5, 0.8)
    out += scatter(T, rng, 0.05, 0.05, 0.9, 0.3, 8, 2, 3, 0)
    x, y = T.p(0.2, 0.5)
    out += reeds(x, y, 0.55, rng, 6)
    return out


def t_deep(c, rng):
    out = plate(c, T, 0, 0, 1, 1, col='#2A2620') + water_fill(c, DIAMOND, 19, deep=True)
    out += ripples(rng, lambda: T.p(rng.uniform(0.1, 0.9), rng.uniform(0.1, 0.9)), 9, 0.8)
    out += line([T.p(0.1, 0.25), T.p(0.6, 0.25)], '#E8F4F0', 1.0, f' opacity="0.35" filter="url(#{c.blur(0.8)})"')
    for _ in range(4):
        x, y = T.p(rng.uniform(0.2, 0.8), rng.uniform(0.2, 0.8))
        out += leaf(x, y, rng, 1.2)
    return out


def t_paddy(c, rng):
    return plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'wet', 15) + paddy(c, T, 0.04, 0.04, 0.92, 0.92, 0, rng)


def t_ford(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'grass', 3)
    band = [T.p(u, 0.3 + 0.04 * math.sin(u * 6)) for u in [k / 10 for k in range(11)]]
    band2 = [T.p(u, 0.72 + 0.04 * math.sin(u * 5 + 1)) for u in [k / 10 for k in range(11)]]
    out += ground(c, [(x, y - 1.5) for x, y in band] + [(x, y + 1.5) for x, y in reversed(band2)], 'wet', 15)
    out += water_fill(c, band + list(reversed(band2)), 21)
    out += ripples(rng, lambda: T.p(rng.uniform(0.1, 0.9), rng.uniform(0.4, 0.62)), 6, 0.7)
    for u, v in ((0.18, 0.5), (0.42, 0.46), (0.64, 0.54), (0.86, 0.5)):
        x, y = T.p(u, v)
        out += ell(x + 0.4, y + 0.8, 6.2, 2.6, '#E8F2EC', ' opacity="0.45"')
        out += boulder(c, x, y + 1.4, 4.4, rng, ('#9A927E', '#7A7362', '#4A463C', '#D2C8B0'))
    out += scatter(T, rng, 0.05, 0.05, 0.9, 0.2, 4, 1, 1, 0) + scatter(T, rng, 0.05, 0.8, 0.9, 0.15, 4, 0, 1, 0)
    return out


def t_reeds(c, rng):
    out = plate(c, T, 0, 0, 1, 1, col='#3A3226') + ground(c, DIAMOND, 'wet', 15)
    for _ in range(5):
        x, y = T.p(rng.uniform(0.15, 0.85), rng.uniform(0.15, 0.85))
        out += ell(x, y, 5, 1.6, '#6E9696', ' opacity="0.5"')
    pts = sorted([T.p(rng.uniform(0.1, 0.9), rng.uniform(0.1, 0.9)) for _ in range(9)], key=lambda p: p[1])
    for x, y in pts:
        out += reeds(x, y, 0.7, rng, 5)
    return out


def t_wall(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'grass', 3) + scatter(T, rng, 0.05, 0.05, 0.9, 0.35, 6, 1, 3, 0)
    out += cast_shadow(c, [T.p(0, 0.42), T.p(1, 0.42), T.p(1, 0.66), T.p(0, 0.66)], 6, 3, 0.4, 2)
    out += stone_wall(c, T, 0, 0.4, 1, 'i', rng, 11, 0.2)
    out += scatter(T, rng, 0.05, 0.64, 0.9, 0.3, 9, 1, 3, 2)
    return out


def t_cliff(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7)
    p, tiso = plateau(c, T, 0.1, 0.1, 0.8, 0.8, 18, rng, 'grass', 5, 0.6)
    out += p + scatter(tiso, rng, 0.15, 0.15, 0.6, 0.6, 5, 1, 2, 0, 0.8)
    x, y = tiso.p(0.35, 0.4)
    out += pine(c, x, y, 0.55, rng)
    x, y = tiso.p(0.62, 0.58)
    out += pine(c, x, y, 0.45, rng)
    return out


def t_pines(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'grass2', 23) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 8, 0, 3, 2)
    for u, v, s in sorted([(0.25, 0.2, 0.55), (0.65, 0.3, 0.6), (0.3, 0.62, 0.62), (0.75, 0.75, 0.5)], key=lambda t: t[0] + t[1]):
        x, y = T.p(u, v)
        out += pine(c, x, y, s, rng)
    return out


def t_maples(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'grass', 3) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 5, 0, 22, 1)
    for u, v, s in sorted([(0.25, 0.3, 0.5), (0.7, 0.35, 0.45), (0.5, 0.75, 0.55)], key=lambda t: t[0] + t[1]):
        x, y = T.p(u, v)
        out += maple(c, x, y, s, rng)
    return out


def ruin_walls(c, iso, i, j, a, b, wh, rng):
    out = ''
    for face, span, sh in ((lambda u, z: iso.p(i + u, j + b, z), a, 0), (lambda u, z: iso.p(i + a, j + u, z), b, 0.4)):
        n = 10
        top = [face(span * k / n, wh * rng.uniform(0.25, 1.0) if 0 < k < n else wh * rng.uniform(0.3, 0.6)) for k in range(n + 1)]
        pts = top + [face(span, 0), face(0, 0)]
        out += poly(pts, dk('#CFC4AA', sh), OL)
        cid = c.clip(P(pts))
        inner = ''
        for k in range(0, n + 1, 3):
            inner += line([face(span * k / n, 0), face(span * k / n, wh)], dk('#3A2416', sh), 1.2)
        inner += poly([face(0, 0), face(span, 0), face(span, 4), face(0, 4)], dk('#7E786A', sh))
        inner += ell(*face(span * 0.6, wh * 0.5), 5, 4, '#1A140D', f' opacity="0.45" filter="url(#{c.blur(2)})"')
        out += f'<g clip-path="url(#{cid})">{inner}</g>'
    return out


def broken_tiles(rng, x0, y0, w, h, n, s=1.0):
    out = ''
    for _ in range(n):
        x, y = x0 + rng.uniform(0, w), y0 + rng.uniform(0, h)
        r = rng.randint(0, 180)
        out += (f'<path d="M{f(x - 1.6 * s)} {f(y)} q{f(1.6 * s)} {f(-1.4 * s)} {f(3.2 * s)} 0 l-0.3 0.8 q{f(-1.3 * s)} {f(-1 * s)} {f(-2.6 * s)} 0 Z" '
                f'fill="{rng.choice(["#4A4E54", "#5E636A", "#3A3E44"])}" transform="rotate({r} {f(x)} {f(y)})"{OL}></path>')
    return out


def t_ruin(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7)
    out += flagstones(c, T, 0.15, 0.15, 0.5, 0.5, 2, 2, rng)
    out += ruin_walls(c, T, 0.15, 0.15, 0.55, 0.45, 13, rng)
    x0, y0 = T.p(0.6, 0.85)
    out += broken_tiles(rng, x0 - 22, y0 - 10, 36, 12, 16, 0.9)
    out += line([T.p(0.3, 0.8), T.p(0.9, 0.55)], '#1E140C', 2.2) + line([T.p(0.3, 0.8), T.p(0.9, 0.55)], '#5A3624', 0.8, ' transform="translate(-0.4 -0.6)"')
    out += scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 10, 1, 4, 3)
    return out


def t_ore(stage):
    def fn(c, rng):
        out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 4, 0, 1, 4)
        return out + ore_node(c, T, rng, stage, 0, 4, 1.0)
    return fn


def t_gas(active):
    def fn(c, rng):
        out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'dirt', 7) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 5, 0, 2, 3)
        return out + gas_cleft(c, 0, 4, 0.95, active, rng)
    return fn


def mini_scene(c, rng):
    out = plate(c, T, 0, 0, 1, 1) + ground(c, DIAMOND, 'grass', 3) + scatter(T, rng, 0.05, 0.05, 0.9, 0.9, 10, 2, 5, 2)
    out += house(c, T, 0.45, 0.2, 0.42, 0.32, 7, 6, rng, 'thatch', False, 0.1)
    x, y = T.p(0.25, 0.3)
    out += pine(c, x, y, 0.5, rng)
    x, y = T.p(0.35, 0.8)
    out += maple(c, x, y, 0.38, rng)
    return out


def t_vis(mode):
    def fn(c, rng):
        scene = mini_scene(c, rng)
        if mode == 0:
            return scene
        if mode == 1:
            ink = c.noise(41, 0.06, (0.05, 0.04, 0.03), 2.0, -0.7, 3)
            return (f'<g filter="url(#{c.gray(0.1)})">{scene}</g>' + poly(DIAMOND, '#1A1712', ' opacity="0.42"')
                    + f'<g clip-path="url(#{c.clip(P(DIAMOND))})"><rect x="-34" y="-20" width="68" height="40" filter="url(#{ink})" opacity="0.6"></rect></g>')
        fog = unexplored_fog(c, rng, DIAMOND, 43)
        if mode == 2:
            return plate(c, T, 0, 0, 1, 1, col='#14110D') + fog
        half = [T.p(0.5, 0), T.p(1, 0), T.p(1, 1), T.p(0.5, 1)]
        return scene + f'<g filter="url(#{c.ink(47, 14, 0.06)})">' + unexplored_fog(c, rng, half, 45, False) + '</g>'
    return fn


def unexplored_fog(c, rng, pts, seed, brush=True):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    r = f'x="{f(min(xs))}" y="{f(min(ys))}" width="{f(max(xs) - min(xs))}" height="{f(max(ys) - min(ys))}"'
    n = c.noise(seed, '0.03 0.09', (0.2, 0.18, 0.15), 2.0, -1.0, 4)
    cid = c.clip(P(pts))
    out = f'<g clip-path="url(#{cid})"><path d="{P(pts)}" fill="#0D0B08"></path><rect {r} filter="url(#{n})" opacity="0.9"></rect>'
    if brush:
        for k in range(4):
            y = min(ys) + (k + 0.6) * (max(ys) - min(ys)) / 4.5
            out += f'<path d="M{f(min(xs))} {f(y)} q20 -4 40 1 t40 -2" fill="none" stroke="#2A2620" stroke-width="2.4" stroke-linecap="round" opacity="0.6"></path>'
    return out + '</g>'


def tile_svg(name, fn, p, seed):
    c = Ctx(p)
    rng = random.Random(seed)
    inner = fn(c, rng)
    return c.svg(name, inner, 114, 94, VB)


# ---------- composite isometric battlefield ----------
MW, MH = 920, 990


def map_svg():
    c = Ctx('t-map')
    rng = random.Random(1234)
    iso = Iso(460, -40)
    ij = lambda x, y: (((y + 40) / 16 + (x - 460) / 32) / 2, ((y + 40) / 16 - (x - 460) / 32) / 2)
    out = []
    full = [(0, 0), (MW, 0), (MW, MH), (0, MH)]
    out.append(ground(c, full, 'grass', 3, 1.6))
    # large tonal variation
    tv = c.noise(77, 0.006, (0.05, 0.05, 0.02), 2.2, -1.0, 3)
    out.append(f'<rect x="0" y="0" width="{MW}" height="{MH}" filter="url(#{tv})" opacity="0.6"></rect>')
    for _ in range(26):
        x, y, col = rng.uniform(0, MW), rng.uniform(0, MH), rng.choice(['#5E7034', '#8E8040', '#A08A48', '#4E6232'])
        out.append(ell(x, y, rng.uniform(40, 110), rng.uniform(20, 50), col, f' opacity="0.35" filter="url(#{c.blur(14)})"'))
    # goblin corner tint
    out.append(ell(820, 140, 230, 130, '#1E3438', f' opacity="0.75" filter="url(#{c.blur(30)})"'))
    # meadow scatter
    sc = ''
    for _ in range(900):
        x, y = rng.uniform(0, MW), rng.uniform(0, MH)
        sc += tuft(x, y, rng.uniform(1.0, 1.5), rng.choice(TUFT_AUTUMN), rng)
    for _ in range(160):
        sc += flower(rng.uniform(0, MW), rng.uniform(0, MH), rng, 1.3)
    for _ in range(260):
        sc += leaf(rng.uniform(0, 520), rng.uniform(150, MH), rng, 1.4)
    for _ in range(120):
        sc += pebble(rng.uniform(0, MW), rng.uniform(0, MH), rng.uniform(0.8, 1.8), rng)
    out.append(sc)

    # dirt yards (village, around base foot, around ore)
    def blob(cx, cy, rx, ry, n=16, j=0.18):
        return [(cx + math.cos(2 * math.pi * k / n) * rx * rng.uniform(1 - j, 1 + j), cy + math.sin(2 * math.pi * k / n) * ry * rng.uniform(1 - j, 1 + j)) for k in range(n)]
    for cx, cy, rx, ry in ((265, 330, 150, 80), (500, 790, 110, 55), (470, 640, 70, 34)):
        b = blob(cx, cy, rx, ry)
        out.append(poly(b, '#2A2014', f' opacity="0.4" filter="url(#{c.blur(5)})"'))
        out.append(f'<g filter="url(#{c.ink(5, 10, 0.05)})">' + ground(c, b, 'dirt', 7, 1.6) + '</g>')

    # field path from the ramp foot down to the ore field
    fpth = smooth([(470, 655), (500, 720), (520, 780), (600, 860), (700, 930)], 8)
    Lf, Rf = ribbon(fpth, lambda t: 7)
    out.append(poly(Lf + list(reversed(Rf)), '#2A2014', f' opacity="0.4" filter="url(#{c.blur(2)})"'))
    out.append(ground(c, Lf + list(reversed(Rf)), 'dirt', 7, 1.6, False))
    # road
    road = smooth([(440, 640), (420, 560), (380, 470), (330, 400), (420, 380), (500, 352), (560, 330), (640, 290), (720, 230), (800, 180)], 8)
    L, R = ribbon(road, lambda t: 15)
    out.append(poly(L + list(reversed(R)), '#2A2014', f' opacity="0.45" filter="url(#{c.blur(3)})"'))
    out.append(ground(c, L + list(reversed(R)), 'dirt', 7, 1.6))
    L2, R2 = ribbon(road, lambda t: 6)
    out.append(line(L2, '#4A3622', 2.2, ' opacity="0.6"') + line(R2, '#4A3622', 2.2, ' opacity="0.6"'))
    out.append(line([(x - 1, y - 1) for x, y in L2], '#B49A72', 0.7, ' opacity="0.6"') + line([(x - 1, y - 1) for x, y in R2], '#B49A72', 0.7, ' opacity="0.6"'))
    for k in range(0, len(L), 2):
        out.append(tuft(L[k][0], L[k][1], 1.2, rng.choice(TUFT_AUTUMN), rng) + tuft(R[k][0], R[k][1], 1.2, rng.choice(TUFT_AUTUMN), rng))
    for _ in range(30):
        k = rng.randrange(len(road))
        out.append(pebble(road[k][0] + rng.uniform(-10, 10), road[k][1] + rng.uniform(-6, 6), rng.uniform(0.8, 1.5), rng))

    # stream
    st = smooth([(360, -20), (420, 120), (500, 240), (550, 330), (590, 440), (660, 530), (770, 600), (940, 640)], 10)
    Lb, Rb = ribbon(st, lambda t: 30 + 8 * t)
    out.append(poly(Lb + list(reversed(Rb)), '#1E1810', f' opacity="0.6" filter="url(#{c.blur(5)})"'))
    out.append(ground(c, Lb + list(reversed(Rb)), 'wet', 15, 1.6))
    Lw, Rw = ribbon(st, lambda t: 22 + 7 * t)
    wpts = Lw + list(reversed(Rw))
    out.append(water_fill(c, wpts, 17))
    Ld, Rd = ribbon(st, lambda t: 9 + 3 * t)
    out.append(poly(Ld + list(reversed(Rd)), '#14323A', f' opacity="0.55" filter="url(#{c.blur(4)})"'))
    out.append(line(Lw, '#E8F2EC', 1.6, f' opacity="0.7" filter="url(#{c.blur(0.8)})"') + line(Rw, '#E8F2EC', 1.4, f' opacity="0.55" filter="url(#{c.blur(0.8)})"'))
    out.append(ripples(rng, lambda: (lambda k: (st[k][0] + rng.uniform(-12, 12), st[k][1] + rng.uniform(-6, 6)))(rng.randrange(len(st))), 70, 1.2))
    for k in range(4, len(Lb) - 2, 3):
        for side in (Lb, Rb):
            if rng.random() < 0.5:
                out.append(boulder(c, side[k][0] + rng.uniform(-4, 4), side[k][1] + rng.uniform(-2, 4), rng.uniform(2.5, 4.5), rng))
            else:
                out.append(reeds(side[k][0], side[k][1] + 2, 1.0, rng, 6))
    # stepping-stone ford near paddies
    for k in range(5):
        t = k / 4
        x, y = 690 + (720 - 690) * t, 525 + (590 - 525) * t
        out.append(ell(x + 1, y + 1.5, 9, 3.6, '#E8F2EC', ' opacity="0.45"') + boulder(c, x, y + 2, 6, rng, ('#9A927E', '#7A7362', '#4A463C', '#D2C8B0')))

    objs = []  # (depth y, svg)
    # pine ridge plateau (top-left)
    i0, j0 = ij(150, 175)
    pr, tiso = plateau(c, iso, i0 - 7, j0 - 5.5, 7, 5.5, 34, rng, 'grass2', 23, 1.6)
    objs.append((40, pr))
    ridge_trees = []
    for _ in range(26):
        u, v = rng.uniform(0.2, 6.8), rng.uniform(0.2, 5.3)
        x, y = tiso.p(i0 - 7 + u, j0 - 5.5 + v)
        if -10 < x < MW + 10 and y > -20:
            ridge_trees.append((y, pine(c, x, y, rng.uniform(1.6, 2.1), rng)))
    for y, s in sorted(ridge_trees):
        objs.append((41 + y * 0.001, s))

    # hilltop base plateau
    pi, pj, pa, pb, ph = 14.5, 22.5, 7, 6, 40
    pl, btop = plateau(c, iso, pi, pj, pa, pb, ph, rng, 'grass', 3, 1.6)
    base = pl
    yard = [btop.p(15.0, 23.0), btop.p(21.0, 23.0), btop.p(21.0, 28.0), btop.p(15.0, 28.0)]
    base += ground(c, yard, 'mortar', 11, 1.6) + flagstones(c, btop, 15.0, 23.0, 6.0, 5.0, 12, 10, rng)
    base += stone_wall(c, btop, 14.7, 28.2, 4.2, 'i', rng, 12, 0.22)
    base += stone_wall(c, btop, 21.2, 22.7, 1.7, 'j', rng, 12, 0.22)
    base += stone_wall(c, btop, 21.2, 26.2, 2.1, 'j', rng, 12, 0.22)
    bl = []
    bl.append((btop.p(16, 25.2)[1], house(c, btop, 16, 23.2, 3.0, 2.0, 22, 18, rng, 'giwa')))
    bl.append((btop.p(20, 27.6)[1], house(c, btop, 19.3, 26.2, 1.6, 1.3, 15, 12, rng, 'giwa')))
    bl.append((btop.p(16.2, 27.6)[1], house(c, btop, 15.4, 26.6, 1.0, 1.0, 26, 10, rng, 'giwa', False)))
    for fx, fy in ((btop.p(19.6, 23.4)), (btop.p(15.3, 25.6))):
        bl.append((fy, line([(fx, fy), (fx, fy - 34)], '#3A2416', 1.4)
                   + f'<path d="M{f(fx)} {f(fy - 34)} q8 2 16 -1 l-2 6 2 6 q-8 3 -16 1 Z" fill="#C8432B"{OL}></path>'
                   + f'<path d="M{f(fx)} {f(fy - 34)} q8 2 16 -1" fill="none" stroke="#E8836A" stroke-width="0.8"></path>'))
    for yv, s in sorted(bl):
        base += s
    # ramp down the east face
    A, B = iso.p(21.5, 24.6, ph), iso.p(21.5, 26.0, ph)
    C, D = iso.p(24.2, 26.0, 0), iso.p(24.2, 24.6, 0)
    rg = c.lin([(0, '#8C7252'), (1, '#6A543A')], 0, 0, 1, 1)
    base += poly([B, C, iso.p(21.5, 26.0, 0)], '#4A3C2A', OL)
    base += poly([A, B, C, D], f'url(#{rg})', OL)
    for k in range(1, 12):
        t = k / 12
        base += line([(A[0] + (D[0] - A[0]) * t, A[1] + (D[1] - A[1]) * t), (B[0] + (C[0] - B[0]) * t, B[1] + (C[1] - B[1]) * t)], '#3E3022', 0.9, ' opacity="0.7"')
    objs.append((iso.p(pi, pj)[1], base))

    # village (left of stream, middle)
    vill = [(9.0, 15.8, 1.8, 1.2, 'giwa'), (11.6, 17.4, 1.6, 1.1, 'thatch'), (8.0, 18.6, 1.4, 1.0, 'thatch'), (12.2, 14.6, 1.5, 1.0, 'thatch'), (10.2, 20.2, 1.7, 1.15, 'giwa')]
    for i, j, a, b, kd in vill:
        objs.append((iso.p(i + a, j + b)[1], house(c, iso, i, j, a, b, 16 if kd == 'giwa' else 12, 13 if kd == 'giwa' else 11, rng, kd, kd == 'giwa')))
    objs.append((iso.p(13.6, 16.9)[1], stone_wall(c, iso, 13.4, 13.9, 3.0, 'j', rng, 10, 0.2)))
    objs.append((iso.p(10.8, 21.8)[1], stone_wall(c, iso, 7.6, 21.6, 2.4, 'i', rng, 10, 0.2)))
    for x, y in ((240, 288), (300, 420), (190, 400)):
        objs.append((y, strawstack(c, x, y, 1.3)))
    for k in range(6):
        x, y = 345 + k * 7 - (k % 2) * 3, 262 + (k % 2) * 4
        g = c.lin([(0, '#8A5A3A'), (1, '#3E2414')], 0, 0, 1, 0)
        objs.append((y, ell(x + 2, y + 1, 4, 1.5, '#0E0A06', ' opacity="0.4"') + f'<path d="M{f(x - 3)} {f(y)} q-1.5 -5 0.6 -7 h4.8 q2.1 2 0.6 7 Z" fill="url(#{g})"{OL}></path>' + ell(x, y - 7, 2.4, 0.8, '#2A1810')))

    # ore field
    for cx, cy, sd in ((470, 770, 3), (540, 800, 4), (430, 820, 5), (585, 770, 6)):
        objs.append((cy, ore_node(c, iso, random.Random(sd), 0 if sd != 6 else 1, cx, cy, 1.5)))
    # gas cleft
    objs.append((875, gas_cleft(c, 120, 875, 1.6, True, rng)))
    objs.append((940, gas_cleft(c, 300, 945, 1.1, False, rng)))

    # terraced paddies
    for r in range(4):
        for q in range(-1, 3):
            i, j = 25.6 + r * 1.95, 17.6 + q * 1.95
            x, y = iso.p(i, j)
            if x > MW + 40:
                continue
            objs.append((iso.p(i + 1.9, j + 1.9)[1], paddy(c, iso, i, j, 1.85, 1.85, max(0, 9 - (r + q) * 3), rng, ripe=(r + q) % 3 != 2, rs=1.5, du=0.15, dv=0.11)))
    for x, y in ((610, 900), (880, 960), (560, 690)):
        objs.append((y, strawstack(c, x, y, 1.4)))

    # trees
    trees = [(30, 470, 'p'), (70, 430, 'p'), (20, 560, 'm'), (110, 500, 'm'), (60, 940, 'p'), (210, 960, 'p'), (380, 940, 'm'), (420, 280, 'm'),
             (175, 240, 'm'), (150, 330, 'p'), (360, 200, 'p'), (470, 180, 'p'), (620, 400, 'm'), (680, 440, 'p'), (900, 520, 'p'), (860, 480, 'p'),
             (700, 340, 'p'), (760, 390, 'm'), (440, 470, 'm'), (520, 560, 'p'), (890, 900, 'p'), (250, 860, 'm')]
    for x, y, k in trees:
        objs.append((y, (pine if k == 'p' else maple)(c, x, y, rng.uniform(1.9, 2.4), rng)))
    for _ in range(18):
        x, y = rng.uniform(0, MW), rng.uniform(150, MH)
        objs.append((y, boulder(c, x, y, rng.uniform(3, 6), rng)))
    # southern groves and a farmstead below the terraces
    for _ in range(10):
        x, y = rng.uniform(20, 300), rng.uniform(915, 985)
        objs.append((y, pine(c, x, y, rng.uniform(1.7, 2.3), rng)))
    for _ in range(6):
        x, y = rng.uniform(480, 640), rng.uniform(905, 980)
        objs.append((y, maple(c, x, y, rng.uniform(1.7, 2.1), rng)))
    fi, fj = ij(790, 935)
    objs.append((iso.p(fi + 1.4, fj + 1.0)[1], house(c, iso, fi, fj, 1.4, 1.0, 12, 11, rng, 'thatch', False)))
    for x, y in ((700, 950), (735, 975), (860, 985)):
        objs.append((y, strawstack(c, x, y, 1.4)))
    for _ in range(40):
        x, y = rng.uniform(0, MW), rng.uniform(860, MH)
        objs.append((y, leaf(x, y, rng, 1.5)))

    # stone arch bridge across the stream
    bi, bj = ij(520, 318)
    br = ''
    br += cast_shadow(c, [iso.p(bi, bj), iso.p(bi + 2.6, bj), iso.p(bi + 2.6, bj + 0.75), iso.p(bi, bj + 0.75)], 8, 4, 0.45, 2)
    br += box(iso, bi, bj, 2.6, 0.75, 0, 12, None, '#8C8474', '#5A5448')
    face = lambda u, z: iso.p(bi + u, bj + 0.75, z)
    arch = [face(0.75 + 1.1 * k / 12, 8.5 * math.sin(math.pi * k / 12)) for k in range(13)]
    br += poly(arch, '#14100C', OL)
    for k in range(13):
        u = 0.75 + 1.1 * k / 12
        a0, a1 = face(u, 8.5 * math.sin(math.pi * k / 12)), face(u, 8.5 * math.sin(math.pi * k / 12) + 2.6)
        br += line([a0, a1], '#4A453A', 0.6)
    for k in range(9):
        u = k * 2.6 / 9
        br += line([face(u, 12), face(u + 2.6 / 9, 12)], '#3A352C', 0.5)
    deck = [iso.p(bi, bj, 12), iso.p(bi + 2.6, bj, 12), iso.p(bi + 2.6, bj + 0.75, 12), iso.p(bi, bj + 0.75, 12)]
    br += ground(c, deck, 'mortar', 11, 1.6) + flagstones(c, Iso(iso.ox, iso.oy - 12), bi, bj, 2.6, 0.75, 8, 2, rng)
    br += box(iso, bi, bj + 0.66, 2.6, 0.09, 12, 3, '#A39A86', '#8C8474', '#5A5448') + box(iso, bi, bj, 2.6, 0.09, 12, 3, '#A39A86', '#8C8474', '#5A5448')
    objs.append((iso.p(bi, bj + 0.75)[1], br))

    # goblin lair (top-right, under explored fog)
    gl = ''
    lx, ly = 815, 150
    gl += ell(lx, ly + 6, 70, 26, '#0E1A1E', f' opacity="0.7" filter="url(#{c.blur(6)})"')
    for k in range(9):
        a = k * 0.7
        rx, ry = lx + math.cos(a) * 46, ly + 8 + math.sin(a) * 18
        gl += f'<path d="M{f(lx)} {f(ly + 2)} Q{f((lx + rx) / 2)} {f((ly + ry) / 2 - 6)} {f(rx)} {f(ry)}" fill="none" stroke="#2A1E14" stroke-width="3.2" stroke-linecap="round"></path>'
        gl += f'<path d="M{f(lx)} {f(ly + 2)} Q{f((lx + rx) / 2)} {f((ly + ry) / 2 - 6)} {f(rx)} {f(ry)}" fill="none" stroke="#4FB3E8" stroke-width="0.8" opacity="0.6"></path>'
    tg = c.lin([(0, '#6A5440'), (0.5, '#3A2C20'), (1, '#1E160E')], 0, 0, 1, 0)
    gl += f'<path d="M{lx - 26} {ly + 4} C{lx - 22} {ly - 30} {lx - 34} {ly - 60} {lx - 14} {ly - 82} L{lx + 16} {ly - 84} C{lx + 30} {ly - 60} {lx + 22} {ly - 30} {lx + 28} {ly + 4} Z" fill="url(#{tg})"{OL}></path>'
    for k in range(6):
        gl += f'<path d="M{lx - 20 + k * 8} {ly + 2} q{-3 + k} -40 {2 - k * 0.5} -80" fill="none" stroke="#1A120A" stroke-width="1.2" opacity="0.7"></path>'
    gl += f'<path d="M{lx - 9} {ly + 2} C{lx - 12} {ly - 16} {lx - 6} {ly - 30} {lx + 1} {ly - 32} C{lx + 9} {ly - 30} {lx + 12} {ly - 16} {lx + 9} {ly + 2} Z" fill="#0A1418"></path>'
    gl += ell(lx, ly - 12, 12, 18, '#4FB3E8', f' opacity="0.7" filter="url(#{c.blur(6)})"')
    for k in range(14):
        a = k / 14 * math.pi * 2
        bx, by = lx + math.cos(a) * 30, ly - 92 + math.sin(a) * 16
        gl += ell(bx, by, 14, 9, rng.choice(['#3A3018', '#4A3A1A', '#2A2414']))
    gl += f'<path d="M{lx - 30} {ly - 40} q30 -10 60 0" fill="none" stroke="#C8B48A" stroke-width="1.2"></path>'
    for k in range(5):
        gl += f'<path d="M{lx - 24 + k * 12} {ly - 41 + abs(k - 2) * 1.2} l-2 9 l4 0 Z" fill="#EFE8D8"></path>'
    for x, y, s in ((lx - 46, ly + 18, 1.4), (lx + 44, ly + 14, 1.3), (lx - 10, ly + 30, 1.1), (lx + 60, ly - 20, 1.0), (lx - 60, ly - 10, 1.0)):
        gl += blue_fire(c, x, y, s)
    objs.append((ly, f'<g filter="url(#{c.gray(0.35)})">{gl}</g>'))

    for _, s in sorted(objs, key=lambda t: t[0]):
        out.append(s)

    # fog of war (top-right)
    exp = [(560, -10), (930, -10), (930, 430)] + [(930 - k * 37 + rng.uniform(-14, 14), 430 - k * 44 + rng.uniform(-14, 14)) for k in range(1, 10)]
    une = [(760, -10), (930, -10), (930, 190)] + [(930 - k * 34 + rng.uniform(-10, 10), 190 - k * 40 + rng.uniform(-8, 8)) for k in range(1, 5)]
    ink = c.ink(61, 36, 0.03)
    xs, ys = [p[0] for p in exp], [p[1] for p in exp]
    inkn = c.noise(67, '0.008 0.03', (0.16, 0.14, 0.11), 2.0, -0.9, 4)
    out.append(f'<g filter="url(#{ink})">' + poly(exp, '#100E0A', ' opacity="0.5"')
               + f'<g clip-path="url(#{c.clip(P(exp))})"><rect x="{f(min(xs))}" y="{f(min(ys))}" width="{f(max(xs) - min(xs))}" height="{f(max(ys) - min(ys))}" filter="url(#{inkn})" opacity="0.7"></rect></g></g>')
    out.append(f'<g filter="url(#{c.ink(63, 44, 0.025)})">' + unexplored_fog(c, rng, une, 65, False) + '</g>')
    for k in range(5):
        y = 20 + k * 30
        out.append(f'<path d="M{790 + k * 18} {y} q30 -6 60 2 t60 -3" fill="none" stroke="#2E2A22" stroke-width="5" stroke-linecap="round" opacity="0.5" filter="url(#{c.blur(1.2)})"></path>')

    # vignette + labels
    vg = c.rad([(0.6, '#000000', 0), (1, '#000000', 0.55)], 0.5, 0.5, 0.72)
    out.append(f'<rect x="0" y="0" width="{MW}" height="{MH}" fill="url(#{vg})"></rect>')

    def label(x, y, t, col='#EFE6D2', w=None):
        w = w or len(t) * 15 + 20
        return (f'<g><rect x="{f(x)}" y="{f(y)}" width="{f(w)}" height="26" rx="2" fill="#16140F" fill-opacity="0.82" stroke="{col}" stroke-opacity="0.55"></rect>'
                f'<text x="{f(x + 10)}" y="{f(y + 18)}" font-family="IBM Plex Sans KR, sans-serif" font-size="15" font-weight="600" fill="{col}">{t}</text></g>')
    out.append(label(110, 640, '언덕 본진') + label(400, 700, '귀화석 광맥', '#CFF5EA') + label(70, 800, '산기 틈', '#D4C4F4')
               + label(190, 200, '마을 · 기와 · 초가') + label(470, 270, '홍예교') + label(700, 820, '계단식 논', '#F2D58A')
               + label(60, 30, '솔 능선') + label(650, 510, '징검다리') + label(690, 230, '도깨비 소굴 · 탐험됨', '#A8DDF5') + label(800, 40, '미탐험', '#A89E8A'))
    return c.svg('아이소메트릭 전장 예시: 언덕 본진, 계단식 논, 마을, 개울과 홍예교, 솔 능선, 귀화석 광맥, 도깨비 소굴과 수묵 안개', ''.join(out), 720, 775, f'0 0 {MW} {MH}')


# ---------- page ----------
def fig(name, cap, sub, fn, p, seed):
    return (f'        <figure style="margin: 0; display: flex; flex-direction: column; gap: 2px; width: 114px">\n'
            f'          {tile_svg(name, fn, p, seed)}\n'
            f'          <figcaption style="font-size: 12px; line-height: 1.35">{cap} <span style="color: #A89E8A">{sub}</span></figcaption>\n        </figure>\n')


def row(title, items):
    out = '      <div style="display: flex; flex-direction: column; gap: 4px">\n'
    out += f'      <h2 style="margin: 0; font-size: 13px; font-weight: 600; letter-spacing: 0.08em; color: #A89E8A">{title}</h2>\n'
    out += '      <div style="display: flex; gap: 12px">\n'
    out += ''.join(fig(*it, seed=k * 7 + 3) for k, it in enumerate(items))
    return out + '      </div>\n      </div>\n'


rows = [
    row('바닥 · 전이 타일', [
        ('풀밭', '풀밭', '들꽃 · 낙엽', t_grass, 't-a1'),
        ('마당 흙', '마당 흙', '수레 자국', t_dirt, 't-a2'),
        ('박석 마당', '박석', '본진 바닥', t_paved, 't-a3'),
        ('풀에서 흙 전이', '풀→흙', '전이', t_grass_dirt, 't-a4'),
        ('흙에서 박석 전이', '흙→박석', '전이', t_dirt_stone, 't-a5')]),
    row('물 · 가중 지형', [
        ('물가 전이', '물가', '전이 · 거품', t_shore, 't-b1'),
        ('깊은 물', '깊은 물', '통행 불가', t_deep, 't-b2'),
        ('무논', '무논', '이동 ×0.5', t_paddy, 't-b3'),
        ('징검다리', '징검다리', '×0.7', t_ford, 't-b4'),
        ('갈대밭', '갈대밭', '×0.8', t_reeds, 't-b5')]),
    row('장애물 · 높이 있음', [
        ('돌담', '돌담', '기와 얹음', t_wall, 't-c1'),
        ('화강암 절벽', '절벽', '고지 경계', t_cliff, 't-c2'),
        ('솔숲', '솔숲', '시야 차단', t_pines, 't-c3'),
        ('단풍숲', '단풍숲', '시야 차단', t_maples, 't-c4'),
        ('무너진 기와집', '기와집 터', '폐허', t_ruin, 't-c5')]),
    row('자원 노드 · 남은 양', [
        ('귀화석 100%', '귀화석', '100%', t_ore(0), 't-d1'),
        ('귀화석 50%', '귀화석', '50%', t_ore(1), 't-d2'),
        ('귀화석 고갈', '귀화석', '고갈', t_ore(2), 't-d3'),
        ('산기 틈 활성', '산기 틈', '활성', t_gas(True), 't-d4'),
        ('산기 틈 고갈', '산기 틈', '닫힘', t_gas(False), 't-d5')]),
    row('시야 단계 · 수묵 안개', [
        ('보임', '보임', '전체 색', t_vis(0), 't-e1'),
        ('탐험됨', '탐험됨', '먹빛 담채', t_vis(1), 't-e2'),
        ('미탐험', '미탐험', '먹 붓질', t_vis(2), 't-e3'),
        ('시야 경계', '경계', '번지는 붓 끝', t_vis(3), 't-e4')]),
]

HEAD = '''<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>지형 · 배경</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Nanum+Myeongjo:wght@700;800&amp;family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans+KR:wght@400;500;600;700&amp;display=swap">
<style>
body{margin:0;background:#16140F}
</style>
</helmet>
'''
TAIL = '''</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":1440,"height":960}}'>
class Component extends DCLogic {
  renderVals() {
    return {};
  }
}
</script>
</body>
</html>
'''

swatches = ''.join(f'<span style="width: 14px; height: 14px; background: {c}"></span>' for c in ['#7C7644', '#8A6E4D', '#9A9384', '#2E5E64', '#D0582E', '#9FE3D0', '#B79AE0'])

page = HEAD + '''<div style="width: 1440px; height: 960px; box-sizing: border-box; background: #16140F; color: #EFE6D2; font-family: 'IBM Plex Sans KR', sans-serif; padding: 30px 40px; display: flex; flex-direction: column; gap: 16px; overflow: hidden">

  <div style="display: flex; align-items: flex-end; justify-content: space-between; height: 60px">
    <div style="display: flex; flex-direction: column; gap: 8px">
      <span style="font-family: 'IBM Plex Mono', monospace; font-size: 12px; letter-spacing: 0.12em; color: #A89E8A">SAMPAJEON · ASSET SHEET T · 2.5D ISOMETRIC 2:1 · 64×32 TILE · KEY LIGHT TOP-LEFT</span>
      <div style="display: flex; align-items: center; gap: 14px">
        <span style="width: 34px; height: 34px; background: #C8432B; color: #EFE6D2; display: flex; align-items: center; justify-content: center; font-family: 'Nanum Myeongjo', serif; font-weight: 800; font-size: 20px; border-radius: 3px; box-shadow: inset 0 0 0 2px #E06A50">參</span>
        <h1 style="margin: 0; font-family: 'Nanum Myeongjo', serif; font-weight: 800; font-size: 40px; line-height: 1">지형 · 배경</h1>
        <span style="font-size: 13px; color: #A89E8A; margin-left: 6px">늦가을 조선 산하 — 황토, 억새, 단풍, 소나무</span>
      </div>
    </div>
    <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 8px">
      <div style="display: flex; height: 4px; width: 220px"><span style="flex: 1; background: #C8432B"></span><span style="flex: 1; background: #3E8F7A"></span><span style="flex: 1; background: #E2B13C"></span><span style="flex: 1; background: #2F6E8E"></span><span style="flex: 1; background: #EFE8D8"></span></div>
      <div style="display: flex; gap: 8px; align-items: center; font-family: 'IBM Plex Mono', monospace; font-size: 11px; color: #A89E8A">''' + swatches + '''<span style="margin-left: 6px">풀 · 흙 · 박석 · 물 · 단풍 · 귀화석 · 산기</span></div>
    </div>
  </div>

  <div style="display: flex; gap: 22px; min-height: 0">
    <div style="display: flex; flex-direction: column; gap: 20px; width: 618px; flex: none">
''' + ''.join(rows) + '''    </div>

    <section style="display: flex; flex-direction: column; gap: 8px; flex: none">
      <h2 style="margin: 0; font-size: 13px; font-weight: 600; letter-spacing: 0.08em; color: #A89E8A">조합 예시 · 아이소메트릭 전장 (2.5D)</h2>
      <div style="border: 1px solid #4A4236; box-shadow: 0 0 0 4px #201D17, 0 0 0 5px #4A4236; line-height: 0">
      ''' + map_svg() + '''
      </div>
    </section>
  </div>
</div>
''' + TAIL

open(sys.argv[1], 'w').write(page)
