#!/usr/bin/env python3
"""Audit a built DUK jar: metadata agreement and mixin targets.

usage: audit_duk_jar.py <duk.jar> <version> <dmz range> <dragonminez.jar>

* mods.toml version, MANIFEST Implementation-Version and <version> agree; the DragonMineZ range is
  <dmz range>; mod id dmzultimatekey and displayTest IGNORE_ALL_VERSION are kept.
* MixinConfigs names both configs, both are in the jar, and the empires config keeps its plugin.
* Every mixin class listed in a config is in the jar. Mixin targets are annotation strings, not
  class constants, so the API audit does not see them: each DragonMineZ class a mixin names must
  exist in <dragonminez.jar>. A mixin that targets another mod (CNPC, Noea, ...) is reported, not
  failed; those configs use defaultRequire 0 and the empires plugin gate.
* No class still names a DragonMineZ class that 2.2 deleted (TaiyokenCastC2S, TaiyokenHandler).
"""
from __future__ import annotations

import json
import re
import sys
import zipfile

MANIFEST_WRAP = re.compile(r"\r?\n ")


def main() -> None:
    jar, version, dmz_range, dmz_jar = sys.argv[1:5]
    errors: list[str] = []
    with zipfile.ZipFile(dmz_jar) as dz:
        dmz_classes = {n[:-6] for n in dz.namelist() if n.endswith(".class")}
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        toml = zf.read("META-INF/mods.toml").decode("utf-8")
        manifest = MANIFEST_WRAP.sub("", zf.read("META-INF/MANIFEST.MF").decode("utf-8"))
        if f'version="{version}"' not in toml:
            errors.append(f"mods.toml version is not {version}")
        if f"Implementation-Version: {version}" not in manifest:
            errors.append(f"MANIFEST Implementation-Version is not {version}")
        dep = re.search(r'modId="dragonminez"\s*\n\s*mandatory=true\s*\n\s*versionRange="([^"]*)"', toml)
        if not dep or dep.group(1) != dmz_range:
            errors.append(f"DragonMineZ range should be {dmz_range}, got {dep.group(1) if dep else None}")
        for need in ('modId="dmzultimatekey"', 'displayTest="IGNORE_ALL_VERSION"'):
            if need not in toml:
                errors.append("mods.toml lost " + need)
        configs = re.search(r"^MixinConfigs: (.*)$", manifest, re.M)
        configs = [c.strip() for c in configs.group(1).split(",")] if configs else []
        if sorted(configs) != ["dmzultimatekey.empires.mixins.json", "dmzultimatekey.mixins.json"]:
            errors.append(f"MixinConfigs should name both DUK configs, got {configs}")
        for cfg_name in configs:
            if cfg_name not in names:
                errors.append(f"mixin config {cfg_name} is not in the jar")
                continue
            cfg = json.loads(zf.read(cfg_name))
            if "empires" in cfg_name and cfg.get("plugin") != "com.dmzlegacy.ultimatekey.empires.EmpiresMixinPlugin":
                errors.append("empires mixin config lost its plugin")
            pkg = cfg["package"].replace(".", "/")
            for side in ("mixins", "client", "server"):
                for mixin in cfg.get(side, []):
                    path = f"{pkg}/{mixin}.class"
                    if path not in names:
                        errors.append(f"{cfg_name}: mixin {mixin} is not in the jar")
                        continue
                    blob = zf.read(path).decode("latin-1")
                    for target in sorted(set(re.findall(r"L(com/dragonminez/[\w/$]+);", blob))):
                        if target not in dmz_classes:
                            errors.append(f"{mixin}: names {target}, which DragonMineZ does not have")
                    for dotted in sorted(set(re.findall(r"(com\.dragonminez\.[\w.$]+)", blob))):
                        if dotted.replace(".", "/") not in dmz_classes:
                            errors.append(f"{mixin}: targets {dotted}, which DragonMineZ does not have")
                    others = sorted(set(re.findall(r"L((?:com/butterjaffa|noppes)/[\w/$]+);", blob)))
                    if others:
                        print(f"  note: {mixin} ({side}) targets another mod: {', '.join(others[:3])}")
        for n in names:
            if n.endswith(".class"):
                blob = zf.read(n)
                for gone in (b"TaiyokenCastC2S", b"combat/TaiyokenHandler"):
                    if gone in blob:
                        errors.append(f"{n} still names {gone.decode()}")
    if errors:
        print("FAIL DUK jar audit:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print(f"OK DUK jar {version}: metadata agree, DragonMineZ {dmz_range}, mixin targets present")


if __name__ == "__main__":
    main()
