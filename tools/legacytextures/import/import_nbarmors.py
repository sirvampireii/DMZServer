#!/usr/bin/env python3
"""Copy the NB Armors 0.1.0 resources into Legacy Textures, verbatim.

usage: import_nbarmors.py --nb <nbarmors-0.1.0.jar> [--check]

Legacy Textures 0.2.0 absorbs NB Armors (owner-approved 2026-11-05). The Java sources were
decompiled once from the same jar into src/main/java/com/dbzlegacy/legacytextures/nb/ (package
renamed only); this script owns the resources, which keep the nbarmors: namespace untouched:

    assets/nbarmors/**            textures, item models, lang
    data/nbarmors/**, data/curios/**   Curios vanity slot, entity binding and tags
    legacy_models/*.json          armor/vanity model geometry read by the catalog code
    nbarmors_catalog.json, nbarmors_tabs.json

--check fails if the committed copy differs from the jar. NB_MANIFEST.json records the jar's sha256.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
RES = HERE.parent / "src" / "main" / "resources"
MANIFEST = HERE / "NB_MANIFEST.json"
KEEP = ("assets/nbarmors/", "data/nbarmors/", "data/curios/", "legacy_models/")
KEEP_FILES = ("nbarmors_catalog.json", "nbarmors_tabs.json")


def wanted(name: str) -> bool:
    return not name.endswith("/") and (name.startswith(KEEP) or name in KEEP_FILES)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--nb", required=True)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    with zipfile.ZipFile(a.nb) as zf:
        files = {n: zf.read(n) for n in zf.namelist() if wanted(n)}
        classes = sorted(n for n in zf.namelist() if n.startswith("com/nbarmors/") and n.endswith(".class"))
    if a.check:
        bad = [n for n, data in files.items() if not (RES / n).is_file() or (RES / n).read_bytes() != data]
        extra = [p.relative_to(RES).as_posix() for root in ("assets/nbarmors", "data/nbarmors", "data/curios", "legacy_models")
                 for p in (RES / root).rglob("*") if p.is_file() and p.relative_to(RES).as_posix() not in files]
        if bad or extra:
            sys.exit(f"NB resources differ from the jar: {len(bad)} changed/missing {bad[:5]}, {len(extra)} extra {extra[:5]}")
        print(f"NB resources match the jar: {len(files)} files")
        return 0
    for n, data in files.items():
        dest = RES / n
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    counts: dict[str, int] = {}
    for n in files:
        key = "/".join(n.split("/")[:3]) if n.startswith("assets/") else n.split("/")[0]
        counts[key] = counts.get(key, 0) + 1
    MANIFEST.write_text(json.dumps({
        "source": {"file": Path(a.nb).name, "sha256": hashlib.sha256(Path(a.nb).read_bytes()).hexdigest()},
        "resources_total": len(files),
        "resources_counts": dict(sorted(counts.items())),
        "classes_in_jar": len(classes),
        "classes": classes,
    }, indent=1) + "\n", encoding="utf-8")
    print(f"copied {len(files)} NB resources: {counts}; {len(classes)} classes in the jar")
    return 0


if __name__ == "__main__":
    sys.exit(main())
