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

    # 4.7.61: with the LM client installed nothing falls back to KaiPath, Preview forms included.
    drawn = re.findall(r"^SCREEN \d+ (\S+) (\S+) cfg=\S+ .* class=(\S+)", client, re.M)
    not_native = sorted({(s_, p_.split(":")[0], c_) for s_, p_, c_ in drawn if c_ != "NativeMenuScreen"})
    previews = sorted({p_ for s_, p_, c_ in drawn if s_ == "character" and p_.startswith("form_preview")})
    row("with the LM client every menu is native (Preview forms too)", bool(drawn) and not not_native,
        (f"{len(previews)} Preview forms pages native" if not not_native else f"fell back: {not_native[:5]}"))

    script = read(os.path.join(full, "uiprobe-script.txt")) or ""
    passes = len(re.findall(r"\bPASS\b", script))
    fails = [l for l in script.splitlines() if re.search(r"\bFAIL\b", l)]
    row("scripted checks", passes > 0 and not fails, f"{passes} PASS" + (f"; FAIL: {fails[:5]}" if fails else ""))
    # Per-section totals (first word of each verdict line: HUD, NAV, DOJO, FORM, ...), so a section that stops
    # running shows up as a missing or smaller count instead of hiding inside one total.
    sect = {}
    for l in script.splitlines():
        v = re.search(r"\b(PASS|FAIL)\b", l)
        if v and not l.startswith("PACKET"):
            k = l.split(" ", 1)[0]
            p_, f_ = sect.get(k, (0, 0))
            sect[k] = (p_ + (v.group(1) == "PASS"), f_ + (v.group(1) == "FAIL"))
    if sect:
        row("scripted checks by section", not any(f_ for _, f_ in sect.values()),
            ", ".join(f"{k} {p_}/{p_ + f_}" for k, (p_, f_) in sorted(sect.items())))
    # 4.7.72 native HUD: every HUD check passes and the real client reported drawing all five elements.
    hud = [l for l in script.splitlines() if l.startswith("HUD ") and re.search(r"\b(PASS|FAIL)\b", l)]
    drew = re.search(r"^HUD CLIENT \S+ drew (\S+): PASS", script, re.M)
    if hud:
        row("native HUD on the LM client: same text as the bars, all five elements drawn",
            # 4.7.75+: more elements exist; the five 4.7.72 ones must draw, the rest may wait on a small window
            # (the client's layout verdict, a separate HUD line, checks nothing overlaps).
            all("PASS" in l for l in hud) and bool(drew)
            and {"underdog", "tp", "penalty", "instinct", "momentum"} <= set(drew.group(1).split(",")),
            f"{sum('PASS' in l for l in hud)}/{len(hud)} HUD checks; client drew {drew.group(1) if drew else 'nothing'}")
    else:
        row("native HUD on the LM client: same text as the bars, all five elements drawn", None, "no HUD checks ran")

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
    row("skin, background and look on every native menu, with its system accent", "SKIN OK" in txt,
        next((l for l in txt.splitlines() if l.startswith("SKIN OK") or l.startswith("SKIN MISMATCH")), txt[:200])
        + " | " + next((l for l in txt.splitlines() if l.startswith("SKINS SEEN")), ""))
    fps_row(row, full, base, pk_of(full))
    wire = re.search(r"wire compare vs .*?: common pages (\d+) differ (\d+) only-old (\d+) only-new (\d+)", txt)
    if base and wire:
        common, differ = int(wire.group(1)), int(wire.group(2))
        diffs = [l.strip()[:160] for l in txt.splitlines() if l.strip().startswith("DIFF")]
        # A label with a Java array reference ("[I@1a2b") changes on every run: noise, listed but not failed.
        real = [d for d in diffs if not re.search(r"\[I@[0-9a-f]+", d)]
        # RIG_EXPECTED_DIFF="hub|main ..." names pages a release changes on purpose (4.7.62: the main menu's
        # Menu skins tile): listed as expected, never failed.
        expected = [e for e in os.environ.get("RIG_EXPECTED_DIFF", "").split() if "|" in e]
        allowed = [d for d in real if any(f"('{e.split('|')[0]}', '{e.split('|')[1]}'," in d for e in expected)]
        real = [d for d in real if d not in allowed]
        row("bridge sends the same specs as the baseline", not real,
            f"{common - differ} of {common} identical" + (f"; {len(diffs) - len(real)} volatile" if len(diffs) > len(real) else "")
            + (f"; {len(allowed)} expected ({' '.join(expected)})" if allowed else "")
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
    kscript = read(os.path.join(kai, "uiprobe-script.txt")) or ""
    khud = [l for l in kscript.splitlines() if l.startswith("HUD ") and re.search(r"\b(PASS|FAIL)\b", l)]
    if khud:
        row("KaiPath keeps the boss bars and chat lines (no HUD over the bridge)",
            "client takes hud=false" in kscript and all("PASS" in l for l in khud),
            f"{sum('PASS' in l for l in khud)}/{len(khud)} bridge HUD checks")
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
        same = differ = wheel = 0
        # 4.7.67: a page with a color wheel differs on purpose. The wheel (and its row and hint) goes only to LM
        # clients that draw it; KaiPath gets the hex field alone. Such a page counts as "by design" when the native
        # packet has a color_wheel input and the KaiPath packet has none; any other difference still fails.
        has_wheel = lambda r: any(i.get("type") == "color_wheel" for i in (r.get("inputs") or []))
        # 4.7.71: navigation differs on purpose. LM clients get Back from their page history (nav:back, named after
        # the page they came from), a breadcrumb and the hub search row; KaiPath gets Back to the page's registry
        # parent and no breadcrumb or search field. A page counts as "by design" when it matches once the footer
        # Back / Main menu buttons and the breadcrumb are set aside, or (hub Home and Search) when only the LM packet
        # has the search field. Any other difference still fails.
        def plain(t):
            return re.sub("§.", "", t or "")

        def footer(b):
            lab = plain(b.get("label", ""))
            return lab.startswith("Back to") or lab == "Main menu"

        def nav_norm(r):
            lay = dict(r["layout"]) if isinstance(r["layout"], dict) else r["layout"]
            if isinstance(lay, dict):
                lay.pop("breadcrumb", None)
            btns = [b for b in (r["buttons"] or []) if not footer(b)]
            # Preview forms names what the figure wears, which differs by client (the LM client draws the player's
            # own look, KaiPath race colors) - the line, wherever it wraps, is set aside too.
            lines = [x for x in (r["lines"] or [])
                     if not re.search(r"The model (uses|wears)|colors, not your own|hair and skin, in this form|^skin, in this form.|^not your own.",
                                      plain(x))
                     # 4.7.84: More tells only players without the LM client that the HUD needs it.
                     and plain(x) != "The HUD needs the Legacy Mechanics client."
                     # 4.7.80 Goals: the reset clock and the week's progress move between the two runs.
                     and not (r["screen"] == "hub" and r["page"] == "goals"
                              and re.search(r"Resets in|\[[#-]+\]|^Done", plain(x)))]
            return json.dumps([lay, btns, lines], sort_keys=True)

        has_search = lambda r: any(i.get("id") == "hub_search" or "hub_search" in json.dumps(i)
                                   for i in (r.get("inputs") or []))
        nav = 0
        differ_pages = []
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
            elif has_wheel(o) and not has_wheel(r):
                wheel += 1
            elif nav_norm(r) == nav_norm(o) or (r["screen"] == "hub" and has_search(o) and not has_search(r)):
                nav += 1
            elif not re.search(r"\[I@[0-9a-f]+", json.dumps(r["buttons"])):
                differ += 1
                differ_pages.append(f"{r['screen']}|{r['page']}")
        row("one spec for both render paths (KaiPath run == native run)", differ == 0,
            f"{same} identical, {differ} differ" + (f" ({', '.join(differ_pages[:5])})" if differ_pages else "") + (f", {wheel} differ only by the LM-client color wheel" if wheel else "")
            + (f", {nav} differ only by LM-client navigation (history Back, breadcrumb, search row, preview look line)" if nav else ""))

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


def pk_of(d):
    return packets(d)


def fps_of(d, pk):
    """{(look, screen, page, cfg): fps} for the skins-pass shots (look 'default' on a jar without skins)."""
    out = {}
    text = read(os.path.join(d, "uiprobe-client.txt")) or ""
    for m in re.finditer(r"^SCREEN (\d+) (\S+) (\S+) cfg=(\S+) .* class=(\S+) fps=(\d+)", text, re.M):
        i, screen, page, cfg, cls, fps = m.groups()
        r = pk.get(int(i))
        if not r or not r.get("from", "").startswith("script skins") or cfg.endswith("-end"):
            continue
        look = re.search(r"look=(\S+)", r["from"])
        out[(look.group(1) if look else "saiyan_royal,on,full", screen, page, cfg)] = int(fps)
    return out


def fps_row(row, full, base, pk):
    """4.7.62 budget: menus hold 60 FPS at GUI 3/4 in every skin, Full and Lite; Saiyan Royal no slower than before."""
    now = fps_of(full, pk)
    if not now:
        row("frame rate: 60+ FPS at GUI 3/4 in every skin, Full and Lite", None, "no skins-pass shots")
        return
    sel = {k: v for k, v in now.items() if k[3] in ("1080p-gs3", "1080p-gs4")}
    low = sorted((v, k) for k, v in sel.items() if v < 60)
    by = {}
    for k, v in sel.items():
        by.setdefault(k[0], []).append(v)
    summary = "; ".join(f"{look}: min {min(v)} median {sorted(v)[len(v) // 2]}" for look, v in sorted(by.items()))
    ok = not low
    # RIG_FPS_BASE: an older run's full/ folder with skins-pass shots (a scripted-only run is enough).
    base = norm(os.environ["RIG_FPS_BASE"]) if os.environ.get("RIG_FPS_BASE") else base
    if base:
        old = fps_of(base, packets(base))
        pairs = [(v, old[("saiyan_royal,on,full",) + k[1:]]) for k, v in sel.items()
                 if k[0] == "saiyan_royal,on,full" and ("saiyan_royal,on,full",) + k[1:] in old]
        if pairs:
            mn = sorted(a for a, _ in pairs)[len(pairs) // 2]
            mo = sorted(b for _, b in pairs)[len(pairs) // 2]
            summary += f" | Saiyan Royal median {mn} vs {mo} before"
            ok = ok and mn >= mo * 0.9
    row("frame rate: 60+ FPS at GUI 3/4 in every skin, Full and Lite", ok,
        summary + (f" | under 60: {low[:4]}" if low else ""))


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
    looks = {}
    for i in sorted(pk):
        m = re.search(r"look=(\S+)", pk[i].get("from", ""))
        if m:
            looks.setdefault(m.group(1), []).append(pk[i])
    for look, rs in looks.items():
        imgs = ""
        for r in rs:
            f = f"full/ui-{r['i']:03d}-1080p-gs3.png"
            if os.path.exists(os.path.join(out, f)):
                imgs += (f'<figure><a href="{f}"><img src="{f}" loading="lazy"></a>'
                         f'<figcaption>{html.escape(r["screen"] + " / " + r["page"])}</figcaption></figure>')
        if imgs:
            cards.append(f"<section><h2>Skin {html.escape(look)}</h2><div class=row>{imgs}</div></section>")
    forms = ""
    for i in sorted(pk):
        r = pk[i]
        if r["screen"] == "character" and r["page"].startswith("form_preview"):
            f = f"full/ui-{r['i']:03d}-1080p-gs3.png"
            if os.path.exists(os.path.join(out, f)):
                forms += (f'<figure><a href="{f}"><img src="{f}" loading="lazy"></a>'
                          f'<figcaption>{html.escape(r["page"])}</figcaption></figure>')
    if forms:
        cards.append(f"<section><h2>Preview forms (the figure must wear the named form)</h2><div class=row>{forms}</div></section>")
    page = ("<!doctype html><meta charset=utf-8><title>LM menu review</title><style>"
            "body{background:#111;color:#eee;font:14px system-ui;margin:16px}h1{font-size:20px}"
            "h2{font-size:15px;margin:18px 0 6px;color:#f0b429}.row{display:flex;gap:12px;flex-wrap:wrap}"
            "figure{margin:0}img{width:min(46vw,640px);border:1px solid #333}figcaption{color:#aaa}</style>"
            "<h1>LM menu review: GUI scale 3 and 4</h1>" + "".join(cards))
    open(os.path.join(out, "review.html"), "w", encoding="utf-8").write(page)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None))
