#!/usr/bin/env python3
"""Verify dmzultra-0.4.0-noscale.jar against the stock jar it was built from.

    python tools/dmzultra-noscale/verify.py <stock dmzultra-0.4.0.jar> <dmzultra-0.4.0-noscale.jar>

Exits 1 on any failure. Checks:
  - every entry except the five patched ones is byte-identical, and the only missing entry is
    FormDataScaleMixin.class;
  - dmzultra.mixins.json still parses, lost exactly FormDataScaleMixin from "mixins", and its other
    lists and keys are unchanged; no other mixin config or class names the mixin;
  - OverhaulScale is still there and active() is `iconst_0; ireturn`; its other methods are unchanged;
  - FormsChecks.overhaulScale() only logs, with no expectScaled call left in the jar's call graph;
  - the manifest Build-Time ends in " noscale", the mod version stays 0.4.0.
"""
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

MIXIN = "FormDataScaleMixin"
MIXIN_CLASS = "com/dmzultra/mixin/FormDataScaleMixin.class"
PATCHED = {"dmzultra.mixins.json", "com/dmzultra/form/OverhaulScale.class",
           "com/dmzultra/dev/FormsChecks.class", "META-INF/MANIFEST.MF"}
failures = []


def check(name, ok, detail=""):
    print(("  OK  " if ok else "  FAIL ") + name + ("" if ok else f" -- {detail}"))
    if not ok:
        failures.append(name)


def javap(data, tmp, name):
    p = Path(tmp) / name
    p.write_bytes(data)
    out = subprocess.run(["javap", "-J-Dstdout.encoding=UTF-8", "-p", "-c", "-constants", str(p)],
                         capture_output=True, check=True).stdout
    return out.decode("utf-8", errors="replace").replace("\r\n", "\n")


def method(dump, sig):
    """Body of one method in a javap -c dump."""
    start = dump.find(sig)
    if start < 0:
        return None
    rest = dump[start:]
    end = re.search(r"\n\n|\n  [a-z].*\(.*\);\n", rest[len(sig):])
    return rest[: len(sig) + (end.start() if end else len(rest))]


def strip_methods(dump, skip):
    """javap dump with the named methods' bodies removed and constant-pool indexes blanked."""
    out = dump
    for sig in skip:
        body = method(out, sig)
        if body:
            out = out.replace(body, sig)
    return re.sub(r"#\d+", "#", out)


def main(stock_path, patched_path):
    stock = zipfile.ZipFile(stock_path)
    patched = zipfile.ZipFile(patched_path)
    s_names, p_names = set(stock.namelist()), set(patched.namelist())
    check("only FormDataScaleMixin.class is missing", s_names - p_names == {MIXIN_CLASS} and p_names <= s_names,
          f"missing={sorted(s_names - p_names)[:5]} extra={sorted(p_names - s_names)[:5]}")
    changed = [n for n in p_names if n not in PATCHED and stock.read(n) != patched.read(n)]
    check("every other entry is byte-identical", not changed, str(changed[:5]))

    s_mix = json.loads(stock.read("dmzultra.mixins.json"))
    p_mix = json.loads(patched.read("dmzultra.mixins.json"))
    check("mixins.json lost exactly FormDataScaleMixin from 'mixins'",
          [m for m in s_mix["mixins"] if m != MIXIN] == p_mix["mixins"] and MIXIN in s_mix["mixins"])
    check("mixins.json other keys unchanged",
          {k: v for k, v in s_mix.items() if k != "mixins"} == {k: v for k, v in p_mix.items() if k != "mixins"})
    others = [n for n in p_names if n.endswith(".mixins.json") and MIXIN.encode() in patched.read(n)]
    classes = [n for n in p_names if n.endswith(".class") and MIXIN_CLASS[:-6].encode() in patched.read(n)]
    check("no mixin config or class names the mixin", not others and not classes, str(others + classes))

    with tempfile.TemporaryDirectory() as tmp:
        s_os = javap(stock.read("com/dmzultra/form/OverhaulScale.class"), tmp, "s_OverhaulScale.class")
        p_os = javap(patched.read("com/dmzultra/form/OverhaulScale.class"), tmp, "p_OverhaulScale.class")
        body = method(p_os, "public static boolean active();") or ""
        ops = re.findall(r"^\s+\d+: (\w+)", body, re.M)
        check("OverhaulScale.active() is iconst_0; ireturn", ops == ["iconst_0", "ireturn"], str(ops))
        check("OverhaulScale's other methods unchanged",
              strip_methods(s_os, ["public static boolean active();"])
              == strip_methods(p_os, ["public static boolean active();"]))
        s_fc = javap(stock.read("com/dmzultra/dev/FormsChecks.class"), tmp, "s_FormsChecks.class")
        p_fc = javap(patched.read("com/dmzultra/dev/FormsChecks.class"), tmp, "p_FormsChecks.class")
        body = method(p_fc, "public static void overhaulScale();") or ""
        check("FormsChecks.overhaulScale() only logs that the scale is off",
              "Logger.info" in body and "off in this build" in body and "expectScaled" not in body
              and "simulate" not in body, body[:300])
        callers = [ln for ln in p_fc.splitlines() if "Method expectScaled" in ln]
        check("no expectScaled call left", not callers, str(callers[:3]))
        check("FormsChecks' other methods unchanged",
              strip_methods(s_fc, ["public static void overhaulScale();"])
              == strip_methods(p_fc, ["public static void overhaulScale();"]))

    mf = patched.read("META-INF/MANIFEST.MF").decode("utf-8")
    smf = stock.read("META-INF/MANIFEST.MF").decode("utf-8")
    check("manifest Build-Time ends in ' noscale', nothing else changed",
          re.search(r"^Build-Time: .+ noscale\r?$", mf, re.M) is not None
          and re.sub(r" noscale(?=\r?\n)", "", mf) == smf)
    check("mod version stays 0.4.0", 'version="0.4.0"' in patched.read("META-INF/mods.toml").decode("utf-8"))
    print("PASS" if not failures else f"FAIL ({len(failures)})")
    return 0 if not failures else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
