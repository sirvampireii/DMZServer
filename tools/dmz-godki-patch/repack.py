"""repack.py <orig.jar> <out.jar> <classesDir> <patchVersion> <entry>...

Copies every entry of orig.jar into out.jar in order, swapping the listed entries for the files
under classesDir and stamping <patchVersion> (e.g. 2.2.1.1) on the dragonminez version line of
META-INF/mods.toml. Then checks that every other entry's bytes are identical, the swapped ones
changed, and the mods.toml change is that one line.
"""
import re
import sys
import zipfile
from pathlib import Path

TOML = "META-INF/mods.toml"
STOCK_LINE = 'version="2.2.1"'

orig, out, classes, version = sys.argv[1], sys.argv[2], Path(sys.argv[3]), sys.argv[4]
if not re.fullmatch(r"2\.2\.1\.[1-9]\d*", version):
    sys.exit(f"patch version must be 2.2.1.N (N >= 1), got {version!r}")
swap = set(sys.argv[5:])
seen = set()


def stamp(data):
    text = data.decode("utf-8")
    lines = text.split("\n")
    hits = [i for i, l in enumerate(lines) if l.strip() == STOCK_LINE]
    if len(hits) != 1:
        sys.exit(f"{TOML}: expected one {STOCK_LINE} line, found {len(hits)}")
    lines[hits[0]] = lines[hits[0]].replace(STOCK_LINE, f'version="{version}"')
    return "\n".join(lines).encode("utf-8")


with zipfile.ZipFile(orig) as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename in swap:
            data = (classes / info.filename).read_bytes()
            seen.add(info.filename)
        elif info.filename == TOML:
            data = stamp(data)
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zout.writestr(zi, data)
missing = swap - seen
if missing:
    sys.exit(f"entries not in jar: {sorted(missing)}")
same = changed = 0
with zipfile.ZipFile(orig) as a, zipfile.ZipFile(out) as b:
    names_a, names_b = a.namelist(), b.namelist()
    assert names_a == names_b, "entry list changed"
    for n in names_a:
        if a.read(n) == b.read(n):
            same += 1
            assert n not in swap, f"{n} did not change"
        elif n == TOML:
            la, lb = a.read(n).decode("utf-8").split("\n"), b.read(n).decode("utf-8").split("\n")
            diff = [(x, y) for x, y in zip(la, lb) if x != y]
            assert len(la) == len(lb) and diff == [(STOCK_LINE, f'version="{version}"')], f"{TOML} diff: {diff}"
            changed += 1
        else:
            changed += 1
            assert n in swap, f"unexpected change: {n}"
print(f"repack OK: {len(names_a)} entries, {same} identical, {changed - 1} classes swapped, "
      f"{TOML} version 2.2.1 -> {version} (one line)")
