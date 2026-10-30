#!/usr/bin/env python3
"""Fail the build when a shipped class names an LM class that is not in the jar.

Constant-pool UTF8 entries for com/dbzlegacy/adaptivedifficulty/.../Outer and
Outer$Inner must have a matching .class file. This is the check that would have
stopped the 4.6.17 GuiClickConfirm$Pending crash and the 4.6.44 FallDamageDiag
crash before deploy.
"""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

PREFIX = b"com/dbzlegacy/adaptivedifficulty/"
INNER = re.compile(
    rb"com/dbzlegacy/adaptivedifficulty/(?:[A-Za-z0-9_]+/)*[A-Za-z0-9_]+(?:\$[A-Za-z0-9_]+)+"
)
# Top-level class names. The trailing check rejects a prefix of a longer token.
TOP = re.compile(
    rb"com/dbzlegacy/adaptivedifficulty/(?:[A-Za-z0-9_]+/)*[A-Za-z0-9_]+"
)


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("usage: audit_jar_inner_classes.py <jar> [package/prefix/]", file=sys.stderr)
        return 2
    jar = Path(sys.argv[1])
    # Optional package prefix so other mods (the melee fix) can run the same check.
    prefix, inner, top = PREFIX, INNER, TOP
    if len(sys.argv) == 3:
        prefix = sys.argv[2].encode("ascii")
        body = re.escape(prefix) + rb"(?:[A-Za-z0-9_]+/)*[A-Za-z0-9_]+"
        inner = re.compile(body + rb"(?:\$[A-Za-z0-9_]+)+")
        top = re.compile(body)
    if not jar.is_file():
        print(f"ERROR: missing jar {jar}", file=sys.stderr)
        return 1
    missing: dict[str, set[str]] = {}
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for info in zf.infolist():
            if not info.filename.endswith(".class"):
                continue
            data = zf.read(info.filename)
            if prefix not in data:
                continue
            for match in inner.finditer(data):
                ref = match.group(0).decode("ascii")
                path = ref + ".class"
                if path not in names:
                    missing.setdefault(path, set()).add(info.filename)
            for match in top.finditer(data):
                end = match.end()
                if end < len(data) and data[end:end + 1] not in (b";", b"<", b"\x00", b"\x01"):
                    # Descriptors end at ';'. A following identifier or '$' is a longer name.
                    nxt = data[end:end + 1]
                    if nxt in (b"$", b"/") or nxt.isalnum() or nxt == b"_":
                        continue
                ref = match.group(0).decode("ascii")
                path = ref + ".class"
                if path not in names:
                    missing.setdefault(path, set()).add(info.filename)
    if missing:
        print("ERROR: jar references classes that were not packaged:", file=sys.stderr)
        for path in sorted(missing):
            users = ", ".join(sorted(missing[path])[:8])
            print(f"  missing {path} (referenced by {users})", file=sys.stderr)
        return 1
    print(f"PASS — {prefix.decode()} classes referenced in {jar.name} are packaged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
