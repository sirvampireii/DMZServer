#!/usr/bin/env python3
"""Write the ported DUK jar: the base jar with the rebuilt classes swapped in and the metadata bumped.

usage: pack.py <base.jar> <classes dir> <out.jar> <version> <dmz range>

* Every class under <classes dir> replaces its namesake in the base jar. A base class whose outer
  class was rebuilt but that javac did not emit (a lambda/anonymous class that no longer exists) is
  dropped, so no stale inner class ships.
* META-INF/mods.toml gets the new version and DragonMineZ range; MANIFEST.MF gets the new
  Implementation-Version. Everything else is copied byte for byte, in the original order.
"""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path


def main() -> None:
    base, classes, out, version, dmz_range = sys.argv[1:6]
    root = Path(classes)
    rebuilt = {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob("*.class")}
    outers = {n[:-6].split("$")[0] for n in rebuilt}
    with zipfile.ZipFile(base) as zin:
        names = zin.namelist()
        missing = sorted(n for n in rebuilt if n not in names)
        if missing:
            sys.exit("pack: rebuilt classes that are not in the base jar: " + ", ".join(missing))
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                n = info.filename
                data = zin.read(n)
                if n.endswith(".class") and n[:-6].split("$")[0] in outers:
                    if n not in rebuilt:
                        print("pack: dropped stale " + n)
                        continue
                    data = rebuilt[n]
                elif n == "META-INF/mods.toml":
                    text = data.decode("utf-8")
                    text, a = re.subn(r'(?m)^version="[^"]*"', f'version="{version}"', text, count=1)
                    text, b = re.subn(r'(modId="dragonminez"\s*\n\s*mandatory=true\s*\n\s*versionRange=)"[^"]*"',
                                      rf'\1"{dmz_range}"', text, count=1)
                    if a != 1 or b != 1:
                        sys.exit("pack: could not set the version/DragonMineZ range in mods.toml")
                    data = text.encode("utf-8")
                elif n == "META-INF/MANIFEST.MF":
                    text = data.decode("utf-8")
                    text, a = re.subn(r"(?m)^Implementation-Version: .*$", f"Implementation-Version: {version}", text)
                    if a != 1:
                        sys.exit("pack: no Implementation-Version in MANIFEST.MF")
                    data = text.encode("utf-8")
                zout.writestr(info, data)
    print(f"pack: {len(rebuilt)} classes swapped in -> {out}")


if __name__ == "__main__":
    main()
