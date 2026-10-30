#!/usr/bin/env python3
"""Audit a built DUK jar: metadata agreement, mixin targets, and no Noea left.

usage: audit_duk_jar.py <duk.jar> <version> <dmz range> <dragonminez.jar> <dmzultra range> <dmzultra.jar>

* mods.toml version, MANIFEST Implementation-Version and <version> agree; the DragonMineZ range is
  <dmz range>; DMZ Ultra is an optional dependency, <dmzultra range>, ordered AFTER; mod id
  dmzultimatekey and displayTest IGNORE_ALL_VERSION are kept.
* MixinConfigs names both configs, both are in the jar, and the empires config keeps its plugin.
* Every mixin class listed in a config is in the jar. Mixin targets are annotation strings, not
  class constants, so the API audits do not see them: each DragonMineZ or DMZ Ultra class a mixin
  names must exist in that mod's jar. CustomNPC targets are reported, not failed (defaultRequire 0).
* No entry in the jar names com/butterjaffa (Noea), and no class still names a DragonMineZ class
  that 2.2 deleted (TaiyokenCastC2S, TaiyokenHandler).
"""
from __future__ import annotations

import json
import re
import sys
import zipfile

MANIFEST_WRAP = re.compile(r"\r?\n ")


def classes(jar: str) -> set[str]:
    with zipfile.ZipFile(jar) as z:
        return {n[:-6] for n in z.namelist() if n.endswith(".class")}


def main() -> None:
    jar, version, dmz_range, dmz_jar, ultra_range, ultra_jar = sys.argv[1:7]
    errors: list[str] = []
    dmz_classes = classes(dmz_jar)
    ultra_classes = classes(ultra_jar)
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        own = {n[:-6] for n in names if n.endswith(".class")}
        toml = zf.read("META-INF/mods.toml").decode("utf-8")
        manifest = MANIFEST_WRAP.sub("", zf.read("META-INF/MANIFEST.MF").decode("utf-8"))
        if f'version="{version}"' not in toml:
            errors.append(f"mods.toml version is not {version}")
        if f"Implementation-Version: {version}" not in manifest:
            errors.append(f"MANIFEST Implementation-Version is not {version}")
        dep = re.search(r'modId="dragonminez"\s*\n\s*mandatory=true\s*\n\s*versionRange="([^"]*)"', toml)
        if not dep or dep.group(1) != dmz_range:
            errors.append(f"DragonMineZ range should be {dmz_range}, got {dep.group(1) if dep else None}")
        ultra = re.search(r'modId="dmzultra"\s*\n\s*mandatory=(\w+)\s*\n\s*versionRange="([^"]*)"\s*\n\s*ordering="(\w+)"', toml)
        if not ultra or ultra.groups() != ("false", ultra_range, "AFTER"):
            errors.append(f"DMZ Ultra should be optional, {ultra_range}, AFTER; got {ultra.groups() if ultra else None}")
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
                    targets = set(re.findall(r"L((?:com/dragonminez|com/dmzultra)/[\w/$]+);", blob))
                    targets |= {t.replace(".", "/") for t in re.findall(r"((?:com\.dragonminez|com\.dmzultra)\.[\w.$]+)", blob)}
                    for target in sorted(targets):
                        have = dmz_classes if target.startswith("com/dragonminez/") else ultra_classes
                        if target not in have and target not in own:
                            errors.append(f"{mixin}: names {target}, which its mod does not have")
                    others = sorted(set(re.findall(r"L(noppes/[\w/$]+);", blob)))
                    if others:
                        print(f"  note: {mixin} ({side}) targets CustomNPCs: {', '.join(others[:2])}")
        for n in sorted(names):
            blob = zf.read(n)
            if b"com/butterjaffa" in blob or b"com.butterjaffa" in blob:
                errors.append(f"{n} still names Noea (com/butterjaffa)")
            if n.endswith(".class"):
                for gone in (b"TaiyokenCastC2S", b"combat/TaiyokenHandler"):
                    if gone in blob:
                        errors.append(f"{n} still names {gone.decode()}")
    if errors:
        print("FAIL DUK jar audit:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print(f"OK DUK jar {version}: metadata agree, DragonMineZ {dmz_range}, DMZ Ultra {ultra_range} optional, "
          "mixin targets present, no Noea")


if __name__ == "__main__":
    main()
