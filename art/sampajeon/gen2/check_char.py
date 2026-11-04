import re, sys
from html.parser import HTMLParser
for fn in sys.argv[1:]:
    s = open(fn, encoding="utf-8").read()
    ids = re.findall(r'\sid="([^"]+)"', s)
    dup = {i for i in ids if ids.count(i) > 1} if len(ids) < 20000 else None
    if dup is None:
        from collections import Counter
        dup = {k for k, v in Counter(ids).items() if v > 1}
    refs = set(re.findall(r'url\(#([^)]+)\)', s)) | set(re.findall(r'href="#([^"]+)"', s))
    missing = refs - set(ids)
    class P(HTMLParser):
        def __init__(s2):
            super().__init__(); s2.st = []; s2.bad = 0
        def handle_starttag(s2, t, a):
            if t not in ("meta", "link", "br", "img", "input", "stop", "feMergeNode"): s2.st.append(t)
        def handle_startendtag(s2, t, a): pass
        def handle_endtag(s2, t):
            if t in ("meta", "link", "br", "img", "input"): return
            if s2.st and s2.st[-1] == t: s2.st.pop()
            else: s2.bad += 1
    p = P(); p.feed(s)
    print(fn.split("/")[-1], "bytes", len(s), "ids", len(ids), "dup", len(dup), list(dup)[:5], "missing", list(missing)[:5], "unbalanced", p.bad, len(p.st))
