"""Verify shrunk artboards: unique ids, resolved refs, well-formed markup, same visible text as the full file."""
import collections, re, sys
from html.parser import HTMLParser

VOID = {"meta", "link", "br", "img", "input", "hr", "area", "base", "col", "embed", "source", "track", "wbr"}

class P(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True); self.st = []; self.bad = 0; self.text = []; self.skip = 0
    def handle_starttag(self, t, a):
        if t not in VOID: self.st.append(t)
        if t in ("script", "style"): self.skip += 1
    def handle_startendtag(self, t, a): pass
    def handle_endtag(self, t):
        if t in VOID: return
        if not self.st or self.st[-1] != t: self.bad += 1
        else: self.st.pop()
        if t in ("script", "style"): self.skip -= 1
    def handle_data(self, d):
        if not self.skip and d.strip(): self.text.append(d.strip())

def text(s):
    p = P(); p.feed(s); return p
for n in sys.argv[1:]:
    a = open(f"full/{n}.dc.html", encoding="utf-8").read(); b = open(f"shrunk/{n}.dc.html", encoding="utf-8").read()
    ids = collections.Counter(re.findall(r'\bid="([^"]+)"', b))
    refs = {x or y for x, y in re.findall(r'url\(#([^)"\']+)\)|href="#([^"]+)"', b)}
    pa, pb = text(a), text(b)
    print(n, f"{len(b.encode()):,} B", "ok" if len(b.encode()) <= 2097152 else "OVER",
          "dup", sum(v > 1 for v in ids.values()), "missing", len([r for r in refs if r not in ids]),
          "tagerr", pb.bad, "unclosed", len(pb.st), "text-same", pa.text == pb.text, len(pb.text),
          "script-same", a[a.find('<script type="text/x-dc"'):] == b[b.find('<script type="text/x-dc"'):])
