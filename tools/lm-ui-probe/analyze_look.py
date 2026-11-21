"""analyze_look.py <results dir>: what a reviewer would catch by eye, from the LM client's own layout dump.

- Contrast: every button label against its face and every body line against its background, WCAG ratio.
  Under MIN_CONTRAST is a problem (red text on a green button was 1.6).
- Cut labels: a button label the renderer had to shorten with "...".
- Panel edge: a server control whose bottom sits within EDGE_GAP design px of the panel's bottom edge.
Prints one PROBLEM line per kind (with examples) or "no look problems". Exit code 1 when anything is found.
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
                if aid not in ("CLOSE", "BOOK_PREV", "BOOK_NEXT") and (py + ph) - (y + h) < EDGE_GAP * scale:
                    problems["control on the panel edge"].append((b["i"], b["screen"], b["page"], b["cfg"], label))
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
