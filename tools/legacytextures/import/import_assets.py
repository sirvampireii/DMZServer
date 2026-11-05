#!/usr/bin/env python3
"""Build the Legacy Textures HD payload from the source packs.

usage: import_assets.py --zone <DMZ HD Plus.zip> --nbhd <Nb Armors HD.zip>
                        --dmz <dragonminez-2.2.1.1.jar> --nb <nbarmors-0.1.0.jar> [--check]

Every shipped file is an HD *variant* under the legacytextures namespace:

    <source ns>:<type>/<rest>  ->  legacytextures:<type>/hd/<source ns>/<rest>

(type = textures | geo | animations). Nothing is written at a dragonminez: or nbarmors: path, so
the vanilla variant is always the mod jar's own file and stays reachable at render time.

Left out, with the reason recorded in import/IMPORT_MANIFEST.json:
  * files byte-identical to the mod jar's own file (the fallback already draws the same bytes)
  * dmzanatomy (no code references it)
  * ZoneMC lang/en_us.json (400 keys for content 2.2.1.1 does not have, and it renames the
    quest difficulty "Easy" to "Infernal"; lang files merge globally, so it cannot be per-viewer)
  * NB Armors pack-only textures (nbarmors 0.1.0 never requests them: CatalogEntry.wornTexture
    picks "<layer>jbra" whenever the entry lists it, so the plain "<prefix>_<layer>.png" layout
    is unreachable; see docs/LEGACYTEXTURES.md)
  * jinryuudragonbc NPC textures (no mod on the server uses that namespace)

--check rebuilds into a temp folder and fails if the committed payload differs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "legacytextures"
MANIFEST = HERE / "IMPORT_MANIFEST.json"
SET_BASE = "hd"
TYPES = ("textures", "geo", "animations")
VALID = re.compile(r"[a-z0-9_./-]+")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_sha(path: str) -> str:
    return sha(Path(path).read_bytes())


def entries(zf: zipfile.ZipFile, prefix: str) -> dict[str, bytes]:
    out = {}
    for info in zf.infolist():
        if info.is_dir() or not info.filename.startswith(prefix):
            continue
        out[info.filename[len(prefix):]] = zf.read(info)
    return out


def variant(ns: str, rel: str) -> str:
    """'textures/entity/x.png' in ns -> path under assets/legacytextures/."""
    kind, rest = rel.split("/", 1)
    return f"{kind}/{SET_BASE}/{ns}/{rest}"


def build(args, out_root: Path) -> dict:
    shipped: dict[str, bytes] = {}
    excluded: dict[str, list[str]] = {}

    def skip(reason: str, name: str) -> None:
        excluded.setdefault(reason, []).append(name)

    with zipfile.ZipFile(args.dmz) as dz:
        dmz = entries(dz, "assets/dragonminez/")
    with zipfile.ZipFile(args.nb) as nz:
        nbj = entries(nz, "assets/nbarmors/")
    with zipfile.ZipFile(args.zone) as zz:
        for name in sorted(n for n in zz.namelist() if not n.endswith("/")):
            if not name.startswith("assets/"):
                continue
            ns, rel = name[len("assets/"):].split("/", 1)
            data = zz.read(name)
            if ns == "dmzanatomy":
                skip("dmzanatomy: no code references it", name)
            elif ns == "dragonminez" and rel == "lang/en_us.json":
                skip("lang: global merge, renames Easy to Infernal, 400 keys for absent content", name)
            elif ns in ("dragonminez", "minecraft") and rel.split("/", 1)[0] in TYPES:
                if ns == "dragonminez" and dmz.get(rel) == data:
                    skip("identical to the dragonminez 2.2.1.1 jar file", name)
                else:
                    shipped[variant(ns, rel)] = data
            else:
                skip("unexpected path", name)
    with zipfile.ZipFile(args.nbhd) as hz:
        for name in sorted(n for n in hz.namelist() if not n.endswith("/")):
            if not name.startswith("assets/"):
                continue
            ns, rel = name[len("assets/"):].split("/", 1)
            data = hz.read(name)
            if ns == "jinryuudragonbc":
                skip("jinryuudragonbc: namespace not used by any server mod", name)
            elif ns != "nbarmors":
                skip("unexpected path", name)
            elif rel not in nbj:
                skip("nbarmors pack-only: never requested by nbarmors 0.1.0 (jbra layout wins)", name)
            elif nbj[rel] == data:
                skip("identical to the nbarmors 0.1.0 jar file", name)
            else:
                shipped[variant("nbarmors", rel)] = data

    bad = [p for p in shipped if not VALID.fullmatch(p)]
    if bad:
        sys.exit(f"invalid resource paths (must be [a-z0-9_./-]): {bad[:10]}")
    for rel, data in shipped.items():
        dest = out_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    counts: dict[str, int] = {}
    for rel in shipped:
        kind, _, ns = rel.split("/", 3)[:3]
        counts[f"{ns} {kind}"] = counts.get(f"{ns} {kind}", 0) + 1
    return {
        "set": SET_BASE,
        "sources": {
            "zone": {"file": Path(args.zone).name, "sha256": file_sha(args.zone)},
            "nbhd": {"file": Path(args.nbhd).name, "sha256": file_sha(args.nbhd)},
            "dragonminez": {"file": Path(args.dmz).name, "sha256": file_sha(args.dmz)},
            "nbarmors": {"file": Path(args.nb).name, "sha256": file_sha(args.nb)},
        },
        "shipped_total": len(shipped),
        "shipped_counts": dict(sorted(counts.items())),
        "payload_sha256": sha(b"".join(k.encode() + b"\0" + sha(v).encode() for k, v in sorted(shipped.items()))),
        "excluded_counts": {k: len(v) for k, v in sorted(excluded.items())},
        "excluded": {k: sorted(v) for k, v in sorted(excluded.items())},
    }


def tree(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): file_sha(str(p)) for p in root.rglob("*") if p.is_file()
            and p.relative_to(root).parts[1:2] == (SET_BASE,)}


def main() -> int:
    ap = argparse.ArgumentParser()
    for a in ("zone", "nbhd", "dmz", "nb"):
        ap.add_argument(f"--{a}", required=True)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as tmp:
            manifest = build(args, Path(tmp))
            want, have = tree(Path(tmp)), tree(ASSETS)
        if want != have:
            diff = sorted(set(want.items()) ^ set(have.items()))
            sys.exit(f"payload differs from the sources ({len(diff)} entries), first: {diff[:5]}")
        print(f"payload matches the sources: {manifest['shipped_total']} files")
        return 0
    for kind in TYPES:
        shutil.rmtree(ASSETS / kind / SET_BASE, ignore_errors=True)
    manifest = build(args, ASSETS)
    MANIFEST.write_text(json.dumps(manifest, indent=1) + "\n", encoding="utf-8")
    print(f"shipped {manifest['shipped_total']} files: {manifest['shipped_counts']}")
    print(f"excluded: {manifest['excluded_counts']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
