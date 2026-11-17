#!/usr/bin/env python3
"""Verify dmzultra-0.5.7-noscale-placefix.jar against the noscale jar it was built from.

    python tools/dmzultra-placefix/verify.py <dmzultra-0.5.7-noscale.jar> <dmzultra-0.5.7-noscale-placefix.jar>

Exits 1 on any failure. Checks:
  - the only new entry is PlaceGroundFix.class; nothing is missing; every entry except PlaceGround,
    PlaceBuilder and the manifest is byte-identical;
  - PlaceGround.vegetation(BlockState) and natural(BlockState) are the noscale bodies with exactly the
    prefix `aload_0; invokestatic PlaceGroundFix.plant|ground; ifeq; iconst_1; ireturn` in front; every
    other PlaceGround method is unchanged;
  - PlaceBuilder: BUILT_MAX is 8 and scan's built limit is `bipush 8` where `iconst_3` was; nothing else in
    the class changed;
  - PlaceGroundFix is Java 17 bytecode with public static plant/ground(BlockState)Z;
  - the manifest Build-Time ends in " noscale placefix" and mods.toml still says version 0.5.7.
"""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

PKG = "com/dmzultra/r3/bleachworld/places/"
GROUND = PKG + "PlaceGround.class"
BUILDER = PKG + "PlaceBuilder.class"
FIX = PKG + "PlaceGroundFix.class"
MANIFEST = "META-INF/MANIFEST.MF"
STATE = "net.minecraft.world.level.block.state.BlockState"
PATCHED = {GROUND, BUILDER, MANIFEST}
failures = []


def check(name, ok, detail=""):
    print(("  OK  " if ok else "  FAIL ") + name + ("" if ok else f" -- {detail}"))
    if not ok:
        failures.append(name)


def javap(data, tmp, name):
    p = Path(tmp) / name
    p.write_bytes(data)
    out = subprocess.run(["javap", "-J-Dstdout.encoding=UTF-8", "-p", "-c", "-v", "-constants", str(p)],
                         capture_output=True, check=True).stdout
    return out.decode("utf-8", errors="replace").replace("\r\n", "\n")


def methods(dump):
    """{signature line: [normalized instructions]} from a javap -c -v dump."""
    out = {}
    cur = None
    in_code = False
    for line in dump.split("\n"):
        m = re.match(r"^  (\S.*\));$|^  (\S.*);$", line)
        if m and "(" in line:
            cur = line.strip()
            out[cur] = []
            in_code = False
            continue
        if cur is None:
            continue
        if line.strip() == "Code:":
            in_code = True
            continue
        ins = re.match(r"^\s+\d+: (\w+)\s*(.*)$", line)
        if in_code and ins:
            op, rest = ins.group(1), ins.group(2)
            rest = re.sub(r"\s+", " ", re.sub(r"#\d+(,\s*\d+)?", "#", rest))
            if op.startswith(("if", "goto", "jsr")):
                rest = ""  # targets move when bytes are inserted
            if op in ("tableswitch", "lookupswitch"):
                rest = ""
            out[cur].append((op + " " + rest).strip())
        elif in_code and re.match(r"^\s+(LineNumberTable|LocalVariableTable|StackMapTable|Exception table)", line):
            in_code = False
    return out


def built_max(dump):
    m = re.search(r"static final int BUILT_MAX = (\d+);", dump)
    return m.group(1) if m else None


def main(noscale_path, patched_path):
    base = zipfile.ZipFile(noscale_path)
    patched = zipfile.ZipFile(patched_path)
    b_names, p_names = set(base.namelist()), set(patched.namelist())
    check("only PlaceGroundFix.class is new and nothing is missing",
          p_names - b_names == {FIX} and b_names <= p_names,
          f"new={sorted(p_names - b_names)[:5]} missing={sorted(b_names - p_names)[:5]}")
    changed = [n for n in b_names & p_names if n not in PATCHED and base.read(n) != patched.read(n)]
    check("every other entry is byte-identical", not changed, str(changed[:5]))

    with tempfile.TemporaryDirectory() as tmp:
        g0 = methods(javap(base.read(GROUND), tmp, "g0.class"))
        g1 = methods(javap(patched.read(GROUND), tmp, "g1.class"))
        check("PlaceGround keeps the same methods", g0.keys() == g1.keys(), str(set(g0) ^ set(g1)))
        for name, helper in (("vegetation", "plant"), ("natural", "ground")):
            sig = f"static boolean {name}({STATE});"
            pre = ["aload_0", f"invokestatic # // Method {PKG}PlaceGroundFix.{helper}:(L{STATE.replace('.', '/')};)Z",
                   "ifeq", "iconst_1", "ireturn"]
            got = g1.get(sig, [])
            check(f"PlaceGround.{name} starts with the PlaceGroundFix.{helper} prefix", got[:5] == pre, str(got[:5]))
            check(f"PlaceGround.{name} is otherwise unchanged", got[5:] == g0.get(sig), f"{len(got)} vs {len(g0.get(sig) or [])}")
        others = [s for s in g0 if not re.search(r" (vegetation|natural)\(" + re.escape(STATE) + r"\);$", s)]
        check("other PlaceGround methods are unchanged", all(g0[s] == g1.get(s) for s in others),
              str([s for s in others if g0[s] != g1.get(s)][:3]))

        d0 = javap(base.read(BUILDER), tmp, "b0.class")
        d1 = javap(patched.read(BUILDER), tmp, "b1.class")
        check("PlaceBuilder.BUILT_MAX was 3", built_max(d0) == "3", str(built_max(d0)))
        check("PlaceBuilder.BUILT_MAX is 8", built_max(d1) == "8", str(built_max(d1)))
        b0, b1 = methods(d0), methods(d1)
        scan = [s for s in b0 if " scan(" in s and s.endswith("boolean, boolean);")]
        check("one 7-argument scan", len(scan) == 1, str(scan))
        s0, s1 = b0[scan[0]], b1.get(scan[0], [])
        diff = [(a, b) for a, b in zip(s0, s1) if a != b]
        check("scan: only iconst_3 -> bipush 8 changed", len(s0) == len(s1) and diff == [("iconst_3", "bipush 8")], str(diff[:3]))
        check("other PlaceBuilder methods are unchanged",
              b0.keys() == b1.keys() and all(b0[s] == b1[s] for s in b0 if s != scan[0]))

        f = javap(patched.read(FIX), tmp, "f.class")
        check("PlaceGroundFix is Java 17 bytecode", "major version: 61" in f)
        for helper in ("plant", "ground"):
            check(f"PlaceGroundFix.{helper}(BlockState) is public static boolean",
                  f"public static boolean {helper}({STATE});" in f)

    mf = patched.read(MANIFEST).decode("utf-8")
    check('manifest Build-Time ends in " noscale placefix"',
          re.search(r"^Build-Time: .* noscale placefix\r?$", mf, re.M) is not None)
    check("mods.toml still says version 0.5.7", 'version="0.5.7"' in patched.read("META-INF/mods.toml").decode("utf-8"))

    if failures:
        print(f"{len(failures)} check(s) failed")
        sys.exit(1)
    print("all checks passed")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2])
