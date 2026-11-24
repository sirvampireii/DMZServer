#!/usr/bin/env python3
"""LM 4.7.71: every menu page the screens declare or link to has a PageRegistry entry (build-time check).

Reads gui/ultra/PageRegistry.java (screen, pattern) and every Ultra*Screens.java: page ids from .page("...") and
from "page:..." / "go:screen|..." button targets. A literal that ends in ':' is the fixed start of a page id built at
run time ("dojo_up:" + id) and must match the start of some pattern. Exit 1 on a page with no entry.

Redirect ids (opened only to forward elsewhere, never shown as a page) are listed in REDIRECTS with the reason.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ULTRA = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ultra"

# screen|page ids that only forward to another page (UltraHubScreens.open and the like): never shown, no entry.
REDIRECTS = {
    "hub|board": "opens spar|sparboard",
    "hub|mydojo": "opens spar|dojo",
    "hub|wars": "opens spar|wars",
    "hub|goals": "old link: opens the hub Search tab",
    "hub|admin": "opens the Staff Admin system",
    "hub|prestige_shop_edit": "opens config|edit:prestige-shop:0:",
    "spar|board": "alias read as sparboard",
}


def registry() -> list[tuple[str, str]]:
    src = (ULTRA / "PageRegistry.java").read_text(encoding="utf-8")
    src = src.replace("HUB_MAIN", '"main"').replace("(HUB,", '("hub",')
    out = []
    for m in re.finditer(r'(?:\bp|\bitem|\bstaff)\(\s*"([a-z_]+)",\s*"([^"]*)"', src):
        out.append((m.group(1), m.group(2)))
    for m in re.finditer(r'new Page\(\s*"([a-z_]+)",\s*"([^"]*)"', src):
        out.append((m.group(1), m.group(2)))
    return out


def match(pattern: str, page: str) -> bool:
    p, g = pattern.split(":"), page.split(":")
    for i, part in enumerate(p):
        if part == "**":
            return True
        if i >= len(g):
            return False
        if part == "*":
            continue
        if part.endswith("*") and len(part) > 1:
            if not g[i].startswith(part[:-1]):
                return False
            continue
        if part != g[i]:
            return False
    return len(p) == len(g)


def prefix_match(pattern: str, start: str) -> bool:
    """A literal 'a:b:' (built at run time) fits a pattern whose first parts agree and that has a part after."""
    g = start.rstrip(":").split(":")
    p = pattern.split(":")
    if len(p) <= len(g) and "**" not in p:
        return False
    for i, part in enumerate(g):
        if i >= len(p):
            return False
        if p[i] == "**":
            return True
        if p[i] == "*":
            continue
        if p[i].endswith("*") and len(p[i]) > 1:
            if not part.startswith(p[i][:-1]):
                return False
            continue
        if p[i] != part:
            return False
    return True


def main() -> int:
    reg = registry()
    problems = []
    checked = 0
    for path in sorted(ULTRA.glob("Ultra*Screens.java")):
        src = path.read_text(encoding="utf-8")
        screens = sorted(set(re.findall(r'UltraScreenSpec\.of\("([a-z_]+)"\)', src)))
        if not screens:
            continue
        found = set()
        for m in re.finditer(r'\.page\("([^"]+)"\)', src):
            found.add(m.group(1))
        for m in re.finditer(r'"page:([^"]+)"', src):
            found.add(m.group(1))
        for page in sorted(found):
            checked += 1
            ok = False
            for s in screens:
                if f"{s}|{page}" in REDIRECTS:
                    ok = True
                    break
                for rs, pat in reg:
                    if rs != s:
                        continue
                    if (page.endswith(":") and prefix_match(pat, page)) or match(pat, page):
                        ok = True
                        break
                if ok:
                    break
            if not ok:
                problems.append(f"{path.name}: page '{page}' ({'/'.join(screens)}) has no PageRegistry entry")
    for p in problems:
        print("  FAIL ", p)
    print(f"page registry: {len(reg)} entries, {checked} page ids checked, {len(problems)} without an entry")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
