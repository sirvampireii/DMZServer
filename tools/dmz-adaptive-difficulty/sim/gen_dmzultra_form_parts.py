#!/usr/bin/env python3
"""Regenerate src/main/resources/dmzultra-form-parts.json from a DMZ Ultra jar.

Usage:
    PYTHONUTF8=1 python sim/gen_dmzultra_form_parts.py <dmzultra-X.Y.Z.jar> [--check]

Reads the jar directly (nothing is extracted):
  - assets/dmzultra/form_parts/forms.json: the client's piece list ('pieces') and its forms.
  - assets/dmzultra/form_parts/sets.json: the piece sets the client reads besides forms.json, in order.
  - every other table in assets/dmzultra/form_parts/: Ultra reads a table that sets.json does not list
    after the listed ones.
  - each table's 'formKeyAliases' (an old key that draws as a current one).

Rules, matching Ultra's FormParts reader:
  - A set wins over forms.json, and the set named first wins; a key defined twice is reported.
  - Only plain race/group/form keys are kept. Variant keys ('#slim', '#female', '#bt<N>', '#model:...')
    depend on the client's body model, which the server cannot see, and LM never builds them.
  - 'pieces' (the Body Parts Shop list) is forms.json's 'pieces': the ids every client can draw.
  - 'designs' colors come from each form's a/b/c/g hex keys (own/trim/horn/light); a missing key is
    left out, and BodyPartCatalog reads it as white.

--check exits 1 when the committed catalog differs from what the jar produces.
"""
import json
import posixpath
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "dmzultra-form-parts.json"
BASE = "assets/dmzultra/form_parts/"
KEY = re.compile(r"^[a-z0-9_]+/[a-z0-9_]+/[a-z0-9_]+$")
COLORS = (("a", "own"), ("b", "trim"), ("c", "horn"), ("g", "light"))


def load(jar, name):
    return json.loads(jar.read(name).decode("utf-8"))


def ultra_version(jar):
    try:
        toml = jar.read("META-INF/mods.toml").decode("utf-8")
    except KeyError:
        return "unknown"
    block = toml.split('modId="dmzultra"', 1)[-1]
    m = re.search(r'version\s*=\s*"([^"]+)"', block)
    return m.group(1) if m else "unknown"


def tables(jar):
    """forms.json last; listed sets first in sets.json order; unlisted tables after them, by name."""
    names = sorted(n for n in jar.namelist()
                   if n.startswith(BASE) and n.endswith(".json") and "/" not in n[len(BASE):])
    listed = []
    for path in load(jar, BASE + "sets.json").get("sets", {}).values():
        full = "assets/dmzultra/" + path
        if full in names and full not in listed:
            listed.append(full)
    skip = {BASE + "forms.json", BASE + "sets.json"}
    unlisted = [n for n in names if n not in listed and n not in skip]
    return listed + unlisted + [BASE + "forms.json"]


def design(entry):
    out = {}
    for src, dst in COLORS:
        value = entry.get(src)
        if isinstance(value, str) and re.fullmatch(r"#?[0-9A-Fa-f]{6}", value):
            out[dst] = int(value.lstrip("#"), 16)
    return out


def build(jar_path):
    with zipfile.ZipFile(jar_path) as jar:
        main = load(jar, BASE + "forms.json")
        pieces = [str(p).strip().lower() for p in main.get("pieces", []) if str(p).strip()]
        forms, designs, owner, aliases, dupes = {}, {}, {}, {}, []
        for name in tables(jar):
            table = load(jar, name)
            short = posixpath.basename(name)
            for alias, target in (table.get("formKeyAliases") or {}).items():
                aliases.setdefault(alias.strip().lower(), target.strip().lower())
            entries = table.get("forms") or {}
            if not isinstance(entries, dict):
                continue
            for raw, entry in entries.items():
                key = raw.strip().lower()
                if not KEY.match(key) or not isinstance(entry, dict) or not isinstance(entry.get("parts"), list):
                    continue
                if key in owner:
                    dupes.append((key, owner[key], short))
                    continue
                owner[key] = short
                forms[key] = [str(p).strip().lower() for p in entry["parts"] if str(p).strip()]
                colors = design(entry)
                if colors:
                    designs[key] = colors
        for alias, target in aliases.items():
            if alias not in forms and target in forms and KEY.match(alias):
                forms[alias] = forms[target]
                if target in designs:
                    designs[alias] = designs[target]
                owner[alias] = "alias of " + target
        version = ultra_version(jar)
    catalog = {
        "source": "dmzultra",
        "ultraVersion": version,
        "catalog": "assets/dmzultra/form_parts/forms.json + every set table (sets.json order, then unlisted tables)",
        "generator": "tools/dmz-adaptive-difficulty/sim/gen_dmzultra_form_parts.py",
        "note": "Generated; do not hand-edit. Form key is race/group/form, the same key DMZUltra FormParts.of "
                "builds (variant keys such as #slim are left out). Piece ids are FormParts ids; 'pieces' is "
                "forms.json's piece list, the ids every client can draw, and the Body Parts Shop list. "
                "'designs' carries the per-form FormParts Design colors (own/trim/horn/light from the upstream "
                "a/b/c/g keys) so a dedicated server can send explicit colors in GuiModelSyncPacket without "
                "the client mod.",
        "pieces": pieces,
        "forms": forms,
        "designs": designs,
    }
    return catalog, owner, dupes


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    catalog, owner, dupes = build(argv[1])
    text = json.dumps(catalog, indent=2, ensure_ascii=False) + "\n"
    for key, first, second in dupes:
        print(f"WARN {key} is in {first} and {second}; kept {first}")
    if "--check" in argv:
        same = OUT.exists() and OUT.read_text(encoding="utf-8").replace("\r\n", "\n") == text
        print(("OK" if same else "STALE") + f" {OUT.name} vs dmzultra {catalog['ultraVersion']}")
        return 0 if same else 1
    OUT.write_text(text, encoding="utf-8", newline="\n")
    tables_used = sorted(set(v for v in owner.values() if not v.startswith("alias")))
    print(f"dmzultra {catalog['ultraVersion']}: {len(catalog['pieces'])} shop pieces, {len(catalog['forms'])} forms "
          f"from {len(tables_used)} tables, {len(catalog['designs'])} designs -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
