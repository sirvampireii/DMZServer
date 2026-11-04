"""repack.py <orig.jar> <out.jar> <classesDir> <entry>...

Copies every entry of orig.jar into out.jar in order, swapping the listed entries for the files
under classesDir. Then checks that every other entry's bytes are identical and the swapped ones
changed.
"""
import sys
import zipfile
from pathlib import Path

orig, out, classes = sys.argv[1], sys.argv[2], Path(sys.argv[3])
swap = set(sys.argv[4:])
seen = set()
with zipfile.ZipFile(orig) as zin, zipfile.ZipFile(out, "w") as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename in swap:
            data = (classes / info.filename).read_bytes()
            seen.add(info.filename)
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
        else:
            changed += 1
            assert n in swap, f"unexpected change: {n}"
print(f"repack OK: {len(names_a)} entries, {same} identical, {changed} swapped")
