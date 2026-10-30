#!/usr/bin/env python3
"""Decompile fidelity check: recompiled classes vs the original jar, method by method.

usage: compare_bytecode.py <original.jar> <classes dir> [--expect <file>] [--new-prefix <pkg/>]

Each class in <classes dir> is disassembled with javap next to its counterpart in the original jar.
Each method becomes a multiset of what it does: calls (by name and descriptor; the static receiver
type is dropped, since `List` vs `ArrayList` locals compile to different owners), field accesses,
constants, allocations, type checks, arithmetic, returns, throws, and the number of conditional
branches. Loads/stores, dup/pop, gotos, casts and branch polarity are dropped, so a decompiler's loop
restructuring does not count but a lost call, constant, assignment or return does. An ArrayList
local captured by a lambda is read as List (decompilers widen or narrow that type freely).

A method that differs is reported unless it is listed in --expect ("Class.method" per line, '#'
comments): the methods the port changes on purpose. Exit 1 on an unexpected difference, a
missing/extra method, or a class that was not rebuilt alongside its outer class.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import zipfile
from collections import Counter
from pathlib import Path

LAMBDA = re.compile(r"lambda\$(\w+?)\$\d+")
OWNER = re.compile(r"^(?:Interface)?Method (?:[\w/$]+\.)?")
SKIP = {"goto", "goto_w", "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1", "dup2_x2", "pop", "pop2", "swap",
        "nop", "iinc", "checkcast"}
SKIP_PREFIX = ("iload", "lload", "fload", "dload", "aload", "istore", "lstore", "fstore", "dstore", "astore")


def disasm(path: Path) -> dict[str, Counter]:
    out = subprocess.run(["javap", "-c", "-p", str(path)], capture_output=True, text=True, check=True).stdout
    methods: dict[str, Counter] = {}
    current = None
    for line in out.splitlines():
        if line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(";"):
            current = LAMBDA.sub(r"lambda$\1$N", line.strip().rstrip(";")).replace("java.util.ArrayList", "java.util.List")
            methods.setdefault(current, Counter())
            continue
        m = re.match(r"\s+\d+: (\w+)(.*)", line)
        if not m or current is None:
            continue
        op, rest = m.group(1), m.group(2)
        comment = rest.split("//", 1)[1].strip() if "//" in rest else ""
        if op in SKIP or op.startswith(SKIP_PREFIX) or op.isdigit() or op == "default":
            continue  # (switch case rows print as "key: offset")
        if op.startswith("if"):
            op, rest = "branch", ""
        elif op in ("tableswitch", "lookupswitch"):
            rest = ""
        elif op.startswith("invoke") and op != "invokedynamic":
            rest = OWNER.sub("", comment)
            op = op if op in ("invokestatic", "invokespecial") else "invoke"
        elif op in ("ldc_w", "ldc2_w"):
            op, rest = "ldc", comment
        else:
            rest = comment or rest.strip()
        ins = LAMBDA.sub(r"lambda$\1$N", f"{op} {rest}".strip())
        methods[current][ins.replace("Ljava/util/ArrayList;", "Ljava/util/List;")] += 1
    return methods


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("classes")
    ap.add_argument("--expect")
    ap.add_argument("--new-prefix", default=None, help="classes under this path are new in the port")
    a = ap.parse_args()
    expect = set()
    if a.expect:
        for line in open(a.expect, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if line:
                expect.add(line)
    root = Path(a.classes)
    bad = 0
    touched = set()
    with zipfile.ZipFile(a.jar) as zf, tempfile.TemporaryDirectory() as tmp:
        names = set(zf.namelist())
        for cls in sorted(root.rglob("*.class")):
            rel = cls.relative_to(root).as_posix()
            short = rel[:-6].rsplit("/", 1)[-1]
            if rel not in names and a.new_prefix and rel.startswith(a.new_prefix):
                continue
            if rel not in names:
                print(f"EXTRA class {rel}")
                bad += 1
                continue
            orig = Path(tmp) / rel.replace("/", "_")
            orig.write_bytes(zf.read(rel))
            want, got = disasm(orig), disasm(cls)
            for sig in sorted(set(want) | set(got)):
                name = re.sub(r"\(.*", "", sig).split()[-1]
                key = f"{short}.{name}"
                if sig in want and sig in got and want[sig] == got[sig]:
                    continue
                touched.add(key)
                if key in expect:
                    continue
                bad += 1
                if sig not in got:
                    print(f"MISSING method {rel}: {sig}")
                elif sig not in want:
                    print(f"EXTRA method {rel}: {sig}")
                else:
                    print(f"DIFF {rel}: {sig}")
                    for ins, n in sorted((want[sig] - got[sig]).items()):
                        print(f"    only in original x{n}: {ins}")
                    for ins, n in sorted((got[sig] - want[sig]).items()):
                        print(f"    only in rebuilt  x{n}: {ins}")
        # every sibling of a recompiled outer class must have been rebuilt too
        outers = {p.relative_to(root).as_posix()[:-6].split("$")[0] for p in root.rglob("*.class")
                  if p.relative_to(root).as_posix() in names}
        for n in sorted(names):
            if n.endswith(".class") and n[:-6].split("$")[0] in outers and not (root / n).is_file():
                print(f"NOT REBUILT {n} (its outer class was recompiled)")
                bad += 1
    for key in sorted(expect - touched):
        print(f"note: expected change {key} matches the original")
    print(f"{'FAIL' if bad else 'OK'} bytecode fidelity: {bad} unexpected difference(s)")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
