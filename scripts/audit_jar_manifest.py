#!/usr/bin/env python3
"""Fail when a mod jar's META-INF/MANIFEST.MF or mods.toml disagrees with the jar.

usage: audit_jar_manifest.py <jar> <version>

* Implementation-Version equals the build's version pin.
* Every MixinConfigs entry, and every [[mixins]] config in mods.toml, is a file in the jar.
  A config named but missing breaks mixin bootstrap (melee 3.0.0 named the deleted raid/SDU configs).
"""
import re
import sys
import zipfile

jar, version = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar) as zf:
    names = set(zf.namelist())
    # Manifest lines wrap at 72 bytes; a continuation line starts with one space.
    manifest = zf.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n", "\n").replace("\n ", "")
    toml = zf.read("META-INF/mods.toml").decode("utf-8") if "META-INF/mods.toml" in names else ""
problems = []
m = re.search(r"^Implementation-Version: *(.+)$", manifest, re.M)
if not m or m.group(1).strip() != version:
    problems.append(f"Implementation-Version {m.group(1).strip() if m else '(none)'} != {version}")
m = re.search(r"^MixinConfigs: *(.+)$", manifest, re.M)
configs = [c.strip() for c in m.group(1).split(",") if c.strip()] if m else []
configs += re.findall(r'^\s*config\s*=\s*"([^"]+)"', toml, re.M)
for c in configs:
    if c not in names:
        problems.append(f"mixin config {c} is named but not in the jar")
if problems:
    print("ERROR: jar manifest check failed:", file=sys.stderr)
    for p in problems:
        print("  " + p, file=sys.stderr)
    sys.exit(1)
print(f"PASS — manifest version {version}; {len(set(configs))} mixin config(s) present")
