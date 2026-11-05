#!/usr/bin/env python3
"""Legacy Textures ship checks. usage: audit_legacytextures.py <jar> <version>

Fails the build when:
  * mods.toml loses an identity/dependency rule (displayTest, the four dependency ranges);
  * the jar carries an asset outside the legacytextures namespace (a same-path override would make
    the vanilla variant unreachable), a lang file (lang merges globally, it cannot be per-viewer),
    or any dmzanatomy / jinryuudragonbc file;
  * the HD payload in the jar does not match import/IMPORT_MANIFEST.json;
  * a shipped texture set file is missing or wrong (vanilla, hd -> fallback vanilla);
  * a mixin class has a non-private method that is not a handler (Mixin would merge it);
  * a Java source has a raw section sign;
  * a DragonMineZ cache CacheFlush clears is missing from the DMZ jar (DMZ_JAR, default the newest
    mods/dragonminez-*.jar), so a DMZ update that renames one is caught here, not in game.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LT = HERE.parent
ROOT = LT.parent.parent
SRC = LT / "src" / "main" / "java" / "com" / "dbzlegacy" / "legacytextures"
errors: list[str] = []


def check(name: str, ok: bool) -> None:
    print(("  ok   " if ok else "  FAIL ") + name)
    if not ok:
        errors.append(name)


def main() -> int:
    jar, version = sys.argv[1], sys.argv[2]
    with zipfile.ZipFile(jar) as zf:
        names = [n for n in zf.namelist() if not n.endswith("/")]
        toml = zf.read("META-INF/mods.toml").decode("utf-8")
        sets = {n.rsplit("/", 1)[1]: json.loads(zf.read(n)) for n in names
                if n.startswith("legacytextures/texture_sets/") and n.endswith(".json")}

    print("--- identity")
    check("modId legacytextures, version " + version, 'modId = "legacytextures"' in toml
          and f'version = "{version}"' in toml and 'displayName = "Legacy Textures"' in toml)
    check("displayTest MATCH_VERSION", 'displayTest = "MATCH_VERSION"' in toml)
    deps = re.findall(r'\[\[dependencies\.legacytextures\]\](.*?)(?=\n\[\[|\Z)', toml, re.S)
    want = {"forge": ("true", "[47,)", None), "minecraft": ("true", "[1.20.1,1.21)", None),
            "dragonminez": ("false", "[2.2,)", "AFTER"), "nbarmors": ("false", "[0.1.0,)", "AFTER")}
    got = {}
    for d in deps:
        mid = re.search(r'modId = "([^"]+)"', d).group(1)
        got[mid] = (re.search(r"mandatory = (\w+)", d).group(1), re.search(r'versionRange = "([^"]+)"', d).group(1),
                    re.search(r'ordering = "(\w+)"', d).group(1), re.search(r'side = "(\w+)"', d).group(1))
    for mid, (mand, rng, order) in want.items():
        g = got.get(mid)
        check(f"dependency {mid} mandatory={mand} {rng}" + (f" {order}" if order else "") + " side BOTH",
              g is not None and g[0] == mand and g[1] == rng and (order is None or g[2] == order) and g[3] == "BOTH")

    print("--- assets")
    assets = [n for n in names if n.startswith("assets/")]
    namespaces = {n.split("/")[1] for n in assets}
    check("assets only under the legacytextures namespace (no same-path overrides)", namespaces == {"legacytextures"})
    check("no lang file (lang merges globally)", not any("/lang/" in n for n in assets))
    check("no dmzanatomy / jinryuudragonbc files", not any(("dmzanatomy" in n or "jinryuudragonbc" in n) for n in names))
    check("blank.png present", "assets/legacytextures/textures/blank.png" in names)
    manifest = json.loads((LT / "import" / "IMPORT_MANIFEST.json").read_text(encoding="utf-8"))
    payload = [n for n in assets if n.split("/")[3:4] == ["hd"]]
    check(f"HD payload in jar = IMPORT_MANIFEST shipped_total ({manifest['shipped_total']})",
          len(payload) == manifest["shipped_total"])
    check("payload is HD variants only: assets/legacytextures/<textures|geo|animations>/hd/<ns>/...",
          all(re.fullmatch(r"assets/legacytextures/(textures|geo|animations)/hd/(dragonminez|minecraft|nbarmors)/.+",
                           n) for n in payload))

    print("--- texture sets")
    v, hd = sets.get("vanilla.json"), sets.get("hd.json")
    check("vanilla.json: id vanilla, blank namespace", v is not None and v.get("id") == "vanilla"
          and v.get("namespace", "") == "")
    check("hd.json: id hd, legacytextures/hd, fallback vanilla", hd is not None and hd.get("id") == "hd"
          and hd.get("namespace") == "legacytextures" and hd.get("basePath") == "hd" and hd.get("fallback") == "vanilla")
    check("hd.json covers players, armor, gui, world",
          hd is not None and sorted(hd.get("categories", [])) == ["armor", "gui", "players", "world"])

    print("--- sources")
    java = list(SRC.rglob("*.java"))
    check("no raw section sign in Java sources", not any("§" in p.read_text(encoding="utf-8") for p in java))
    bad = []
    for p in (SRC / "mixin").glob("*Mixin.java"):
        code = re.sub(r"/\*.*?\*/|//[^\n]*", "", p.read_text(encoding="utf-8"), flags=re.S)
        for m in re.finditer(r"((?:@\w+(?:\((?:[^()]|\([^()]*\))*\))?\s*)*)((?:public|protected|private|static|final|"
                             r"abstract|\s)*)[\w<>\[\],.?]+\s+(\w+)\s*\([^)]*\)\s*\{", code):
            annos, mods, name = m.group(1), m.group(2), m.group(3)
            if "private" not in mods and not re.search(r"@(Inject|ModifyVariable|Redirect|Shadow|Overwrite)", annos):
                bad.append(f"{p.stem}.{name}")
    check("mixin classes: every non-handler method is private" + (f" ({bad})" if bad else ""), not bad)
    screen = (SRC / "client" / "TextureScreen.java").read_text(encoding="utf-8")
    check("menu text: no 'Hub', no 'Invalid input'", "Hub" not in screen and "Invalid input" not in screen)

    print("--- DragonMineZ caches CacheFlush clears")
    dmz = os.environ.get("DMZ_JAR") or str(sorted((ROOT / "mods").glob("dragonminez-*.jar"))[-1])
    flush = (SRC / "client" / "CacheFlush.java").read_text(encoding="utf-8")
    calls = re.findall(r'\{DMZ \+ "([\w.]+)", "(\w+)"\}', flush)
    for cls, member in calls:
        out = subprocess.run(["javap", "-p", "-cp", dmz, "com.dragonminez.client." + cls],
                             capture_output=True, text=True).stdout
        check(f"{Path(dmz).name}: {cls}.{member}", re.search(r"\b" + member + r"\b", out) is not None)
    if errors:
        print(f"ERROR: {len(errors)} Legacy Textures check(s) failed", file=sys.stderr)
        return 1
    print("Legacy Textures audit OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
