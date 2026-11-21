"""report.py <results dir> [<baseline full dir>]: one pass/fail summary for a matrix run, plus a contact sheet.

Reads the stages matrix.sh leaves in <results dir> (full/, kai/, reject/ and the srv-*.log files), runs the
analyzers, and writes:
  REPORT.md    one line per check, PASS / FAIL / SKIP, with the evidence;
  review.html  GUI scale 3 and 4 screenshots of every Difficulty page and the main page of every other menu,
               plus the spin-stop shots, for the owner to eyeball (open it from disk; nothing is uploaded).
Exit code 1 when any check fails.
"""
import html
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def norm(path):
    m = re.match(r"^/([a-zA-Z])/(.*)$", path)
    if m and os.name == "nt":
        return m.group(1) + ":/" + m.group(2)
    return path


def read(path):
    try:
        return open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return None


def analyzer(name, *args):
    r = subprocess.run([sys.executable, os.path.join(HERE, name), *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return r.returncode, r.stdout + r.stderr


def packets(d):
    out = {}
    text = read(os.path.join(d, "uiprobe-packets.jsonl"))
    for line in (text or "").splitlines():
        r = json.loads(line)
        out[r["i"]] = r
    return out


def main(out, base=None):
    out = norm(out)
    base = norm(base) if base else None
    full = os.path.join(out, "full")
    rows = []

    def row(name, ok, evidence):
        rows.append((name, "SKIP" if ok is None else ("PASS" if ok else "FAIL"), evidence))

    srv = read(os.path.join(out, "srv-full.log"))
    if srv is None:
        row("server boot", False, "no srv-full.log")
    else:
        failed = [l for l in srv.splitlines() if re.search(r"self-(test|check).*FAIL", l)]
        oks = len(re.findall(r"self-(?:test|check) OK", srv))
        row("server boot, strict self-tests", "Done (" in srv and not failed,
            f"{oks} self-tests OK" + (f"; FAILED: {failed[:3]}" if failed else ""))
    cl = read(os.path.join(full, "classload.log"))
    if cl is None:
        row("dedicated server loads no client class", None, "no classload.log")
    else:
        n = cl.count("adaptivedifficulty.client")
        row("dedicated server loads no client class", n == 0, f"{n} client classes of {cl.count(chr(10))} loads")

    client = read(os.path.join(full, "uiprobe-client.txt")) or ""
    disc = [l for l in client.splitlines() if l.startswith(("DISCONNECTED", "NOSCREEN", "CLOSED"))]
    shots = client.count("\nSCREEN ") + client.startswith("SCREEN ")
    row("full crawl completed", shots > 0 and not disc, f"{shots} layouts" + (f"; {disc[:3]}" if disc else ""))

    script = read(os.path.join(full, "uiprobe-script.txt")) or ""
    passes = len(re.findall(r"\bPASS\b", script))
    fails = [l for l in script.splitlines() if re.search(r"\bFAIL\b", l)]
    row("scripted checks", passes > 0 and not fails, f"{passes} PASS" + (f"; FAIL: {fails[:5]}" if fails else ""))

    rec = {}
    for m in re.finditer(r"^REC (\S+) .*?\n(?:  .*\n)*?  N M .* rotate=(\w+) yaw=([\d.-]+)", client, re.M):
        rec[m.group(1)] = (m.group(2), float(m.group(3)))
    interrupted = re.findall(r"^REC (rot\S+) class=(?!\S*NativeMenuScreen)(\S+)", client, re.M)
    stop = rec.get("rot4-stopped-after-back")
    back = rec.get("rot3-spun-to-back")
    if interrupted:
        row("spin stop is front-facing", False, f"check interrupted: {interrupted[:2]} was on screen instead of the menu")
    elif not stop:
        row("spin stop is front-facing", None, "no spin records")
    else:
        row("spin stop is front-facing", stop == ("false", 0.0) and back is not None and back[0] == "true",
            f"spun to yaw {back[1] if back else '?'}, stopped at yaw {stop[1]} (0 = front toward the viewer)")

    rc, txt = analyzer("analyze_skin.py", full, *([base] if base else []))
    row("skin on every native menu with its system accent", "SKIN OK" in txt,
        next((l for l in txt.splitlines() if l.startswith("SKIN")), txt[:200]))
    wire = re.search(r"wire compare vs .*?: common pages (\d+) differ (\d+) only-old (\d+) only-new (\d+)", txt)
    if base and wire:
        common, differ = int(wire.group(1)), int(wire.group(2))
        diffs = [l.strip()[:160] for l in txt.splitlines() if l.strip().startswith("DIFF")]
        # A label with a Java array reference ("[I@1a2b") changes on every run: noise, listed but not failed.
        real = [d for d in diffs if not re.search(r"\[I@[0-9a-f]+", d)]
        row("bridge sends the same specs as the baseline", not real,
            f"{common - differ} of {common} identical" + (f"; {len(diffs) - len(real)} volatile" if len(diffs) > len(real) else "")
            + (f"; differ: {real[:3]}" if real else ""))
    else:
        row("bridge sends the same specs as the baseline", None, "no baseline given")

    rc, txt = analyzer("analyze_layout.py", full)
    probs = [l for l in txt.splitlines() if l.startswith("PROBLEM")]
    fit = [l for l in txt.splitlines() if re.match(r"1080p-gs[34]:", l)]
    gs3 = re.search(r"1080p-gs3: .* scrolling=(\d+)", txt)
    pk = packets(full)
    diff_scroll = []
    for l in txt.splitlines():
        if l.startswith("1080p-gs4 scrolling pages:") or l.startswith("1080p-gs3 scrolling pages:"):
            diff_scroll += re.findall(r"\(\d+, 'difficulty', '([^']*)'", l)
    row("layout: inside the panel, 80px buttons, 16px rows, 6px gaps, 426 wide, no overlaps", not probs,
        "; ".join(probs)[:300] or "no layout problems")
    row("scale-to-fit: nothing scrolls at GUI 3, no Difficulty page scrolls at GUI 3/4",
        bool(gs3) and gs3.group(1) == "0" and not diff_scroll, " | ".join(fit))

    rc, txt = analyzer("analyze_look.py", full)
    row("look: label/text contrast >= 3:1, no cut labels, nothing on the panel edge", rc == 0,
        " ".join(txt.split())[:400])

    kai = os.path.join(out, "kai")
    kc = read(os.path.join(kai, "uiprobe-client.txt"))
    if kc is None:
        row("KaiPath only: every menu through the bridge", None, "kai stage not run")
    else:
        classes = set(re.findall(r"^SCREEN .* class=(\S+)", kc, re.M))
        row("KaiPath only: every menu through the bridge", classes == {"class=LmDynamicScreen"} or classes == {"LmDynamicScreen"},
            f"classes {sorted(classes)}")
        a, b = packets(kai), packets(full)
        # Crawled menus only: scripted pages carry live timers and test state that differ between runs.
        crawled = lambda d: [r for r in d.values() if not r.get("from", "").startswith("script")]
        idx = {}
        for r in crawled(b):
            idx.setdefault((r["screen"], r["page"], r["title"], r["tab"]), r)
        same = differ = 0
        seen_keys = set()
        for r in crawled(a):
            k = (r["screen"], r["page"], r["title"], r["tab"])
            if k in seen_keys:
                continue
            seen_keys.add(k)
            o = idx.get(k)
            if o is None:
                continue
            if json.dumps([r["layout"], r["buttons"], r["lines"]], sort_keys=True) == json.dumps(
                    [o["layout"], o["buttons"], o["lines"]], sort_keys=True):
                same += 1
            elif not re.search(r"\[I@[0-9a-f]+", json.dumps(r["buttons"])):
                differ += 1
        row("one spec for both render paths (KaiPath run == native run)", differ == 0, f"{same} identical, {differ} differ")

    rj = read(os.path.join(out, "srv-reject.log"))
    if rj is None:
        row("requireLmClient turns a no-client login away", None, "reject stage not run")
    else:
        msg = re.search(r"lost connection: (This server needs the Legacy Mechanics client mod\.[^\n]*)", rj)
        row("requireLmClient turns a no-client login away", "turning away" in rj and bool(msg),
            msg.group(1) if msg else "no install message seen")

    gallery(out, pk, client)
    bad = [r for r in rows if r[1] == "FAIL"]
    md = ["# LM UI matrix report", "", f"Results: `{out}`" + (f", baseline `{base}`" if base else ""), "",
          "| Check | Result | Evidence |", "|---|---|---|"]
    md += [f"| {n} | {s} | {e.replace('|', '/')} |" for n, s, e in rows]
    md += ["", f"**{len(rows) - len(bad)} of {len(rows)} checks pass or skip; {len(bad)} fail.**",
           "", "Screenshots for review: `review.html` (GUI scale 3 and 4)."]
    open(os.path.join(out, "REPORT.md"), "w", encoding="utf-8").write("\n".join(md) + "\n")
    for n, s, e in rows:
        print(f"{s:4}  {n}: {e[:160]}")
    print("report:", os.path.join(out, "REPORT.md"), "| review:", os.path.join(out, "review.html"))
    return 1 if bad else 0


def gallery(out, pk, client):
    full = os.path.join(out, "full")
    picks = []
    seen = set()
    for i in sorted(pk):
        r = pk[i]
        fam = (r["screen"], r["page"].split(":")[0], r.get("from", "")[:40] if r.get("from", "").startswith("script") else "")
        if r["screen"] == "difficulty" or r["page"] in ("main", "home", "admin") or r["screen"] in ("hub", "config"):
            if fam in seen:
                continue
            seen.add(fam)
            picks.append(r)
    cards = []
    for r in picks:
        imgs = ""
        for g in ("gs3", "gs4"):
            f = f"full/ui-{r['i']:03d}-1080p-{g}.png"
            if os.path.exists(os.path.join(out, f)):
                imgs += f'<figure><a href="{f}"><img src="{f}" loading="lazy"></a><figcaption>GUI {g[-1]}</figcaption></figure>'
        if imgs:
            title = html.escape(f"{r['screen']} / {r['page']}" + (f" ({r['from'][7:]})" if r.get("from", "").startswith("script") else ""))
            cards.append(f"<section><h2>{title}</h2><div class=row>{imgs}</div></section>")
    spin = ""
    for name in ("rot3-spun-to-back", "rot4-stopped-after-back"):
        f = f"full/ui-rec-{name}.png"
        if os.path.exists(os.path.join(out, f)):
            spin += f'<figure><a href="{f}"><img src="{f}" loading="lazy"></a><figcaption>{name}</figcaption></figure>'
    if spin:
        cards.append(f"<section><h2>Model spin stop (must show the front)</h2><div class=row>{spin}</div></section>")
    page = ("<!doctype html><meta charset=utf-8><title>LM menu review</title><style>"
            "body{background:#111;color:#eee;font:14px system-ui;margin:16px}h1{font-size:20px}"
            "h2{font-size:15px;margin:18px 0 6px;color:#f0b429}.row{display:flex;gap:12px;flex-wrap:wrap}"
            "figure{margin:0}img{width:min(46vw,640px);border:1px solid #333}figcaption{color:#aaa}</style>"
            "<h1>LM menu review: GUI scale 3 and 4</h1>" + "".join(cards))
    open(os.path.join(out, "review.html"), "w", encoding="utf-8").write(page)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None))
