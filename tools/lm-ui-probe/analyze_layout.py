"""analyze_layout.py <results dir>: native layout check (widths, gaps, button heights, scale-to-fit at GUI 3/4) from uiprobe-client.txt N lines + packets."""
import json
import re
import sys
from collections import Counter, defaultdict

D = sys.argv[1]
packets = {}
for line in open(D + "/uiprobe-packets.jsonl", encoding="utf-8"):
    r = json.loads(line)
    packets[r["i"]] = r

blocks = []
cur = None
for line in open(D + "/uiprobe-client.txt", encoding="utf-8", errors="replace"):
    line = line.rstrip("\n")
    if line.startswith("SCREEN "):
        m = re.match(r"SCREEN (\d+) (\S+) (\S+) cfg=(\S+) .* gui=(\d+)x(\d+) scale=\S+ class=(\S+)", line)
        cur = {"i": int(m.group(1)), "screen": m.group(2), "page": m.group(3), "cfg": m.group(4),
               "gw": int(m.group(5)), "gh": int(m.group(6)), "cls": m.group(7), "N": []}
        blocks.append(cur)
    elif line.startswith("  N ") and cur is not None:
        cur["N"].append(line[4:])
    elif not line.startswith("  "):
        cur = None

classes = Counter(b["cls"] for b in blocks)
print("blocks", len(blocks), "pages", len({b["i"] for b in blocks}), "classes", dict(classes))
problems = defaultdict(list)
scroll = defaultdict(set)
scales = defaultdict(list)
for b in blocks:
    if b["cls"] != "NativeMenuScreen":
        problems["not native"].append((b["i"], b["screen"], b["page"], b["cfg"], b["cls"]))
        continue
    if b["cfg"].endswith("-end"):
        continue
    panel = next(l for l in b["N"] if l.startswith("PANEL"))
    px, py, pw, ph = map(float, panel.split()[1:5])
    s = float(re.search(r"scale=([\d.]+)", panel).group(1))
    ms = float(re.search(r"maxScroll=([\d.]+)", panel).group(1))
    scales[b["cfg"]].append(s)
    if ms > 0.5:
        scroll[b["cfg"]].add(b["i"])
    if s < 0.4999:
        problems["scale under floor"].append((b["i"], b["cfg"], s))
    btns = []
    for l in b["N"]:
        if l.startswith("B "):
            f = l.split(" ", 7)
            x, y, w, h = map(float, f[1:5])
            btns.append((x, y, w, h, f[5], f[6], l[l.index('"'):]))
    eps = 0.6
    for (x, y, w, h, st, aid, lab) in btns:
        if x < px - eps or x + w > px + pw + eps or y < py - eps or y + h > py + ph + eps:
            problems["button outside panel"].append((b["i"], b["cfg"], lab))
        if w + eps < 80 * s and aid not in ("BOOK_PREV", "BOOK_NEXT"):
            problems["button under 80px"].append((b["i"], b["cfg"], lab, round(w / s)))
    for i in range(len(btns)):
        for j in range(i + 1, len(btns)):
            a, c = btns[i], btns[j]
            if a[0] < c[0] + c[2] - eps and c[0] < a[0] + a[2] - eps and a[1] < c[1] + c[3] - eps and c[1] < a[1] + a[3] - eps:
                problems["overlap"].append((b["i"], b["cfg"], a[6], c[6]))
    p = packets.get(b["i"])
    if p is not None:
        server = len(p["buttons"])
        native = sum(1 for t in btns if t[5] not in ("CLOSE", "BOOK_PREV", "BOOK_NEXT"))
        if server != native:
            problems["button count differs from packet"].append((b["i"], server, native))
        if not p["layout"].get("fillScreen") and p["layout"].get("buttonHeight") != 16:
            problems["menu buttonHeight not 16"].append((b["i"], p["layout"].get("buttonHeight")))
        if not p["layout"].get("fillScreen") and p["layout"].get("buttonGap") != 6:
            problems["menu gap not 6"].append((b["i"], p["layout"].get("buttonGap")))
        if not p["layout"].get("fillScreen") and p["panelW"] < 426:
            problems["menu under 426 wide"].append((b["i"], p["panelW"]))

for cfg in sorted(scales):
    v = scales[cfg]
    print(f"{cfg}: pages={len(v)} native-size={sum(1 for s in v if s >= 0.999)} scaled={sum(1 for s in v if s < 0.999)}"
          f" min-scale={min(v):.3f} scrolling={len(scroll[cfg])}")
for k, v in problems.items():
    print(f"PROBLEM {k}: {len(v)}", v[:8])
if not problems:
    print("no layout problems")
for cfg in sorted(scroll):
    if "gs3" in cfg or "gs4" in cfg:
        ids = sorted(scroll[cfg])
        print(cfg, "scrolling pages:", [(i, packets[i]["screen"], packets[i]["page"], packets[i]["panelH"]) for i in ids][:20])
