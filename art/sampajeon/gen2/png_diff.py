"""Pixel comparison of two same-size PNGs (no PIL here): share of pixels that differ visibly, and the worst block."""
import struct, sys, zlib

def load(path):
    b = open(path, "rb").read()
    pos, idat = 8, []
    while pos < len(b):
        n, t = struct.unpack(">I4s", b[pos:pos + 8])
        if t == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", b[pos + 8:pos + 18])
        elif t == b"IDAT":
            idat.append(b[pos + 8:pos + 8 + n])
        pos += 12 + n
    ch = {2: 3, 6: 4}[ctype]
    raw = zlib.decompress(b"".join(idat))
    stride = w * ch
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[p]; line = bytearray(raw[p + 1:p + 1 + stride]); p += 1 + stride
        if f == 1:
            for i in range(ch, stride): line[i] = (line[i] + line[i - ch]) & 255
        elif f == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0; c = prev[i - ch] if i >= ch else 0; q = prev[i]
                pa, pb, pc = abs(q - c), abs(a - c), abs(a + q - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else q if pb <= pc else c)) & 255
        rows.append(line); prev = line
    return w, h, ch, rows

for n in sys.argv[1:]:
    w, h, ch, A = load(f"{n}_full.png"); w2, h2, ch2, B = load(f"{n}_shrunk.png")
    assert (w, h, ch) == (w2, h2, ch2), (w, h, w2, h2)
    vis = big = 0; blocks = {}
    for y in range(0, h, 2):
        a, b = A[y], B[y]
        if a == b: continue
        for x in range(0, w * ch, ch * 2):
            d = max(abs(a[x] - b[x]), abs(a[x + 1] - b[x + 1]), abs(a[x + 2] - b[x + 2]))
            if d > 24:
                vis += 1; blocks[(x // ch // 100, y // 100)] = blocks.get((x // ch // 100, y // 100), 0) + 1
            if d > 80: big += 1
    tot = (h // 2) * (w // 2)
    top = sorted(blocks.items(), key=lambda kv: -kv[1])[:3]
    print(n, f"{w}x{h}", f"visible diff {vis / tot * 100:.3f}%  strong {big / tot * 100:.3f}%  worst blocks(100px@2x) {top}")
