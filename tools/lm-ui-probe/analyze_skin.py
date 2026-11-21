"""analyze_skin.py <results dir> [<older results dir>]: Saiyan Royal skin + system accent per native screen, plus a wire
comparison of every spec against an older run (the bridge must send the same bytes)."""
import json
import re
import sys
from collections import Counter

import os as _os
import re as _re


def norm(path):
    """Accept Git Bash paths (/c/Users/...) as well as Windows ones."""
    m = _re.match(r"^/([a-zA-Z])/(.*)$", path)
    if m and _os.name == "nt":
        return m.group(1) + ":/" + m.group(2)
    return path


D = norm(sys.argv[1])
blocks = []
cur = None
import os
for line in (open(D + "/uiprobe-client.txt", encoding="utf-8", errors="replace") if os.path.exists(D + "/uiprobe-client.txt") else []):
    line = line.rstrip("\n")
    if line.startswith("SCREEN "):
        m = re.match(r"SCREEN (\d+) (\S+) (\S+) cfg=(\S+) .* class=(\S+)", line)
        cur = {"i": int(m.group(1)), "screen": m.group(2), "page": m.group(3), "cfg": m.group(4),
               "cls": m.group(5), "N": []}
        blocks.append(cur)
    elif line.startswith("  N ") and cur is not None:
        cur["N"].append(line[4:])
    elif not line.startswith("  "):
        cur = None

ACC = {"difficulty": "E23B3B", "rival": "FFFFFF", "spar": "00AAAA", "prestige": "55FFFF", "character": "55FF55",
       "skillcheck": "FFFF55", "skills": "FFFF55", "admin": "AA0000", "config": "AA0000", "progression": "AA0000"}
# LM 4.7.62: a menu without a system accent (the main menu, Menu skins) takes the skin's trim.
TRIM = {"saiyan_royal": "F0B429", "namek_serenity": "FFD166", "obsidian_gold": "E8B33C",
        "beerus_destruction": "E86A9A", "zeno_omni": "F59E0B"}
SKINS = list(TRIM)

# Which look each page was sent in: the skins pass tags its packets "script skins look=<skin>,<bg>,<lite>".
looks = {}
if os.path.exists(D + "/uiprobe-packets.jsonl"):
    for line in open(D + "/uiprobe-packets.jsonl", encoding="utf-8"):
        r = json.loads(line)
        m = re.search(r"look=([a-z_]+),(on|off),(full|lite)", r.get("from", ""))
        if m:
            looks[r["i"]] = m.groups()


def parse_skin(n):
    """SKIN <id> accent=RRGGBB [bg=on|off look=full|lite dark=.. baked=N] (4.7.60 lines have only the first two)."""
    m = re.match(r"(\S+) accent=([0-9A-F]{6})(?: bg=(on|off) look=(full|lite))?", n)
    return m.groups() if m else (n, "", None, None)


skin = Counter()
seen_skins = Counter()
bad = []
for b in blocks:
    if b["cls"] != "NativeMenuScreen":
        continue
    raw = next((n[5:] for n in b["N"] if n.startswith("SKIN ")), "missing")
    sid, acc, bg, look = parse_skin(raw)
    skin[(b["screen"], sid)] += 1
    seen_skins[sid] += 1
    want_skin, want_bg, want_look = looks.get(b["i"], ("saiyan_royal", "on", "full"))
    if b["screen"] == "config":
        want_bg = "off"  # the config editor fills the window: nothing shows behind it
    want_acc = ACC.get(b["screen"], TRIM.get(want_skin, "?"))
    problems = []
    if sid != want_skin:
        problems.append(f"skin {sid} want {want_skin}")
    if acc != want_acc:
        problems.append(f"accent {acc} want {want_acc}")
    if bg is not None and (bg != want_bg or look != want_look):
        problems.append(f"bg={bg} look={look} want bg={want_bg} look={want_look}")
    if problems:
        bad.append((b["i"], b["screen"], b["page"], b["cfg"], "; ".join(problems)))
print("native blocks", sum(1 for b in blocks if b["cls"] == "NativeMenuScreen"), "of", len(blocks))
print("skin by screen:", dict(skin))
print("SKINS SEEN", {k: seen_skins.get(k, 0) for k in SKINS})
missing = [k for k in SKINS if looks and not seen_skins.get(k)]
if bad or missing:
    print("SKIN MISMATCH", bad[:10], ("missing skins: " + ", ".join(missing)) if missing else "")
else:
    print("SKIN OK: every native menu in its expected skin, background and look, with its system accent"
          + (f"; all {len(SKINS)} skins drawn" if looks else ""))

if len(sys.argv) > 2:
    TIME = re.compile(r"\d+:\d\d|\d+(\.\d+)?s\b|\[[#-]+\]")

    def load(d):
        out = {}
        for line in open(d + "/uiprobe-packets.jsonl", encoding="utf-8"):
            r = json.loads(line)
            key = (r["screen"], r["page"], r["title"], r["tab"])
            body = json.dumps([r["layout"], r["buttons"], r["lines"], r["inputs"], r["panelW"], r["panelH"]],
                              sort_keys=True)
            out.setdefault(key, TIME.sub("T", body))
        return out
    a, b = load(norm(sys.argv[2])), load(D)
    common = sorted(set(a) & set(b))
    diff = [k for k in common if a[k] != b[k]]
    print("wire compare vs 4.7.59: common pages", len(common), "differ", len(diff),
          "only-old", len(set(a) - set(b)), "only-new", len(set(b) - set(a)))
    for k in diff[:8]:
        x, y = a[k], b[k]
        i = next(j for j in range(min(len(x), len(y))) if x[j] != y[j]) if x != y else 0
        print("  DIFF", k, "|", x[max(0, i - 60):i + 80], "||", y[max(0, i - 60):i + 80])
