"""gen_lm_corpus.py <LM UI probe results dir> <out .jsonl>: real LM menu specs for the kit's corpus test.

Takes the crawl an LM UI probe run recorded (tools/lm-ui-probe, uiprobe-packets.jsonl) and keeps one packet per
menu family (screen + page kind), crawled pages only. Refresh it with every LM release that changes menus:
  python tools/ultra-gui-kit/test/gen_lm_corpus.py tools/lm-ui-probe/out/full tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl
"""
import json
import os
import re
import sys


def norm(path):
    m = re.match(r"^/([a-zA-Z])/(.*)$", path)
    return m.group(1) + ":/" + m.group(2) if m and os.name == "nt" else path


src, dst = norm(sys.argv[1]), norm(sys.argv[2])
seen, out = set(), []
for line in open(os.path.join(src, "uiprobe-packets.jsonl"), encoding="utf-8"):
    r = json.loads(line)
    if r.get("from", "").startswith("script"):
        continue
    fam = (r["screen"], re.split(r"[:_]\d|:", r["page"])[0])
    if fam in seen:
        continue
    seen.add(fam)
    # Volatile values (object references in a label) are dropped so the fixture is stable across runs.
    buttons = json.loads(re.sub(r"\[I@[0-9a-f]+", "[I@0", json.dumps(r["buttons"])))
    out.append({"screen": r["screen"], "title": r["title"], "page": r["page"], "tab": r["tab"],
                "panelW": r["panelW"], "panelH": r["panelH"],
                "layout": json.dumps(r["layout"], separators=(",", ":")),
                "buttons": json.dumps(buttons, separators=(",", ":")),
                "lines": json.dumps(r["lines"], separators=(",", ":"), ensure_ascii=False),
                "inputs": json.dumps(r["inputs"], separators=(",", ":"))})
with open(dst, "w", encoding="utf-8", newline="\n") as f:
    for o in out:
        f.write(json.dumps(o, ensure_ascii=False) + "\n")
print(f"{len(out)} menu families -> {dst}")
