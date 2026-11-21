"""analyze_look.py <results dir>: what a reviewer would catch by eye, from the LM client's own layout dump.

- Contrast: every button label against its face and every body line against its background, WCAG ratio.
  Under MIN_CONTRAST is a problem (red text on a green button was 1.6).
- Cut labels: a button label the renderer had to shorten with "...".
- Panel edge: a server control whose bottom or right side sits within EDGE_GAP design px of the panel edge
  (the rounded border cuts it).
- Looks disabled: a live button (normal / confirm / danger) whose label is drawn in a dark gray, the locked look.
- Field hint overflow: a text field's grey hint wider than the field (it runs under the next control).
- Raw value: a Java object printed as text ("[I@2feb1f5f") in a label or body line.
- Orphan word (NOTE, not a failure): a wrapped body line that leaves one short word alone on the next line.
Prints one PROBLEM line per kind (with examples) or "no look problems". Exit code 1 when a PROBLEM is found.
"""
import os
import re
import sys
from collections import defaultdict

MIN_CONTRAST = 3.0
EDGE_GAP = 3.0


def norm(path):
    """Accept Git Bash paths (/c/Users/...) as well as Windows ones."""
    m = re.match(r"^/([a-zA-Z])/(.*)$", path)
    if m and os.name == "nt":
        return m.group(1) + ":/" + m.group(2)
    return path


def lum(hexrgb):
    v = int(hexrgb, 16)
    out = []
    for shift in (16, 8, 0):
        c = ((v >> shift) & 0xFF) / 255.0
        out.append(c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * out[0] + 0.7152 * out[1] + 0.0722 * out[2]


def contrast(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


RAW = re.compile(r"\[[IJDZBCSF]@[0-9a-f]{4,}|\b[a-z]+(\.[a-z]+)+\.[A-Z]\w*@[0-9a-f]{4,}")


def gray(hexrgb):
    """Dark neutral gray, the locked/disabled label look."""
    v = int(hexrgb, 16)
    r, g, b = (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF
    return max(r, g, b) - min(r, g, b) <= 8 and max(r, g, b) <= 0x90


def blocks_of(path):
    blocks, cur = [], None
    for line in open(path, encoding="utf-8", errors="replace"):
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
    return blocks


def main(d):
    d = norm(d)
    problems = defaultdict(list)
    notes = defaultdict(list)
    seen = set()
    checked = 0
    for b in blocks_of(d + "/uiprobe-client.txt"):
        if b["cls"] != "NativeMenuScreen" or b["cfg"].endswith("-end"):
            continue
        panel = next((l for l in b["N"] if l.startswith("PANEL")), None)
        if panel is None:
            continue
        px, py, pw, ph = map(float, panel.split()[1:5])
        scale = float(re.search(r"scale=([\d.]+)", panel).group(1))
        lines = {}
        for l in b["N"]:
            if l.startswith("L "):
                lines[len(lines)] = l.split(" ", 3)[3] if l.count(" ") >= 3 else ""
        if b["cfg"] == "1080p-gs3":
            for i in range(1, len(lines)):
                prev, cur = lines[i - 1].rstrip(), lines[i].strip()
                if (cur and " " not in cur and len(cur) <= 12 and len(prev) >= 36
                        and prev[-1:] not in ".:!?)" and not cur[:1].isupper()):
                    notes["orphan word"].append((b["i"], b["screen"], b["page"], cur))
        for idx, text in lines.items():
            if RAW.search(text):
                problems["raw value"].append((b["i"], b["screen"], b["page"], text[:60]))
        for l in b["N"]:
            key = None
            if l.startswith("B "):
                m = re.search(r'"(.*)" face=([0-9A-F]{6}) ink=([0-9A-F]{6}) cut=(\w+)$', l)
                if not m:
                    continue
                checked += 1
                label, face, ink, cut = m.groups()
                f = l.split(" ", 7)
                x, y, w, h = map(float, f[1:5])
                aid = f[6]
                if label.strip():
                    c = contrast(face, ink)
                    if c < MIN_CONTRAST:
                        key = ("low contrast label", b["screen"], label, face, ink)
                        problems["low contrast label"].append((b["i"], b["screen"], b["page"], label, face, ink,
                                                               round(c, 2)))
                if cut == "true":
                    problems["label cut"].append((b["i"], b["screen"], b["page"], b["cfg"], label))
                if aid not in ("CLOSE", "BOOK_PREV", "BOOK_NEXT") and ((py + ph) - (y + h) < EDGE_GAP * scale
                                                                     or (px + pw) - (x + w) < EDGE_GAP * scale):
                    problems["control on the panel edge"].append((b["i"], b["screen"], b["page"], b["cfg"], label))
                style = f[5]
                if style in ("normal", "confirm", "danger") and label.strip() and gray(ink):
                    problems["live button looks disabled"].append((b["i"], b["screen"], b["page"], label, ink))
                if RAW.search(label):
                    problems["raw value"].append((b["i"], b["screen"], b["page"], label))
            elif l.startswith("T "):
                m = re.search(r'hint="(.*)" hintw=(\d+) room=(\d+)$', l)
                if m and int(m.group(2)) > int(m.group(3)):
                    problems["field hint overflow"].append((b["i"], b["screen"], b["page"], m.group(1),
                                                            f"{m.group(2)}px in {m.group(3)}px"))
            elif l.startswith("I "):
                m = re.match(r"I (\d+) ink=([0-9A-F]{6}) bg=([0-9A-F]{6})", l)
                if not m:
                    continue
                idx, ink, bg = int(m.group(1)), m.group(2), m.group(3)
                text = lines.get(idx, "")
                if not text.strip():
                    continue
                checked += 1
                c = contrast(ink, bg)
                if c < MIN_CONTRAST:
                    problems["low contrast text"].append((b["i"], b["screen"], b["page"], text[:40], ink, bg,
                                                          round(c, 2)))
            if key:
                seen.add(key)
    print(f"look checks: {checked} labels and lines")
    for k, v in notes.items():
        uniq = sorted(set(v))
        print(f"NOTE {k}: {len(uniq)} distinct", uniq[:8])
    if not problems:
        print("no look problems")
        return 0
    for k, v in problems.items():
        # One example per distinct page/label so a repeated chrome bug reads as one line, not hundreds.
        uniq = []
        keys = set()
        for item in v:
            sig = (item[1], item[2], item[3]) if len(item) > 3 else item
            if sig not in keys:
                keys.add(sig)
                uniq.append(item)
        print(f"PROBLEM {k}: {len(v)} ({len(uniq)} distinct)", uniq[:10])
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
