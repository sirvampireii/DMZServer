#!/usr/bin/env python3
"""Copy the DMZ Face Anim 0.2.0 frames into Legacy Textures (0.2.3).

usage: import_faceanim.py --fa <dmzfaceanim-0.2.0.jar> --zone <DMZ HD Plus.zip> [--check]

The face animation code was decompiled once from the same jar into
src/main/java/com/dbzlegacy/legacytextures/faceanim/ and mixin/DMZSkinLayerFaceMixin (its owner relicensed
it into Legacy Textures on 2026-11-05). This script owns the frames. They keep the dragonminez: namespace,
because DMZSkinLayer draws face layers by dragonminez: path and FaceAnimator matches those paths:

    assets/dragonminez/textures/entity/races/<race>/faces/anim/*.png   blink and scream frames, verbatim
    anjo/  -> angel/  (DMZ's race folder), anjo_eye_0_* -> angel_eye_0_*, anjo_form1..5 frames dropped
    angel/faces/angel_eye_0_0..3.png   base Angel eyes, renamed from the ZoneMC pack's anjo/ art

None of these paths exists in DragonMineZ (the audit checks), so no DMZ file is overridden.
--check fails if the committed copy differs. FACEANIM_MANIFEST.json records the sources' sha256.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
RES = HERE.parent / "src" / "main" / "resources"
MANIFEST = HERE / "FACEANIM_MANIFEST.json"
RACES = "assets/dragonminez/textures/entity/races/"
ZONE_ANJO = RACES + "anjo/faces/anjo_eye_0_{}.png"


def target(name: str) -> str | None:
    """Where a frame of the face animation jar goes, or None when it is not shipped."""
    if not (name.startswith(RACES) and name.endswith(".png") and "/faces/anim/" in name):
        return None
    if "/anjo/" not in name:
        return name
    stem = name.rsplit("/", 1)[1]
    if stem.startswith("anjo_form"):
        return None  # anjo_form1..5: no such faces in DragonMineZ
    stem = re.sub(r"^anjo_", "angel_", stem)
    return RACES + "angel/faces/anim/" + stem


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fa", required=True)
    ap.add_argument("--zone", required=True)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    files: dict[str, bytes] = {}
    with zipfile.ZipFile(a.fa) as fa:
        for name in fa.namelist():
            dest = target(name)
            if dest:
                files[dest] = fa.read(name)
    with zipfile.ZipFile(a.zone) as zone:
        for n in range(4):
            files[RACES + f"angel/faces/angel_eye_0_{n}.png"] = zone.read(ZONE_ANJO.format(n))
    manifest = {
        "sources": {
            "faceanim": {"file": Path(a.fa).name, "sha256": sha(Path(a.fa).read_bytes())},
            "zone": {"file": Path(a.zone).name, "sha256": sha(Path(a.zone).read_bytes())},
        },
        "frames_total": len(files),
        "files": sorted(files),
    }
    bad = []
    for path, data in sorted(files.items()):
        out = RES / path
        if a.check:
            if not out.is_file() or out.read_bytes() != data:
                bad.append(path)
        else:
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(data)
    have = {p.relative_to(RES).as_posix() for p in (RES / RACES).rglob("*.png")} if (RES / RACES).is_dir() else set()
    extra = sorted(have - set(files))
    if a.check:
        if bad or extra:
            print(f"face anim frames differ: {len(bad)} changed or missing, {len(extra)} extra: {(bad + extra)[:5]}")
            return 1
        print(f"face anim frames match ({len(files)} files)")
        return 0
    for path in extra:
        (RES / path).unlink()
    MANIFEST.write_text(json.dumps(manifest, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {len(files)} face anim frames, removed {len(extra)} stale, manifest {MANIFEST.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
