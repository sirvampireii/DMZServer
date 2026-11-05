#!/usr/bin/env python3
"""Check every class/field/method a DUK jar links against the mod jars it runs with.

usage: audit_api_refs.py <duk.jar> --api <jar> [--api <jar> ...] [--lib <jar> ...]
                         [--prefix com/dragonminez/] [--allow <file>]

* --api jars are the ones under test (DMZ 2.2, dmzrevamp, ...). Every constant-pool reference from a
  DUK class to a class whose package starts with one of --prefix must resolve in them: the class must
  exist, and a field/method must be declared on it or on a superclass/interface (walking into --lib
  jars such as Minecraft/Forge for inherited members). Members inherited from java/* are trusted.
* --allow lists refs that are known missing but never linked (one per line, "owner.name desc" or a
  bare class name; '#' comments). Use it for classes only touched by gated mixins.
Prints each missing ref with the DUK classes using it and exits 1 if any are not allowed.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import struct
import sys
import zipfile
from collections import defaultdict


def parse(b: bytes):
    """Return (name, super, interfaces, members{name+desc}, refs[(kind, owner, name, desc)])."""
    count = struct.unpack(">H", b[8:10])[0]
    pool: list = [None] * count
    i, k = 10, 1
    while k < count:
        tag = b[i]
        if tag == 1:
            ln = struct.unpack(">H", b[i + 1:i + 3])[0]
            pool[k] = ("utf8", b[i + 3:i + 3 + ln].decode("utf-8", "replace"))
            i += 3 + ln
        elif tag in (3, 4):
            i += 5
        elif tag in (5, 6):
            i += 9
            k += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[k] = (tag, struct.unpack(">H", b[i + 1:i + 3])[0])
            i += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[k] = (tag, struct.unpack(">H", b[i + 1:i + 3])[0], struct.unpack(">H", b[i + 3:i + 5])[0])
            i += 5
        elif tag == 15:
            i += 4
        else:
            raise ValueError(f"bad constant tag {tag}")
        k += 1

    def utf(idx):
        return pool[idx][1]

    def cls(idx):
        return utf(pool[idx][1])

    access, this_i, super_i = struct.unpack(">HHH", b[i:i + 6])
    i += 6
    name = cls(this_i)
    sup = cls(super_i) if super_i else None
    n_if = struct.unpack(">H", b[i:i + 2])[0]
    i += 2
    ifaces = [cls(struct.unpack(">H", b[i + 2 * j:i + 2 * j + 2])[0]) for j in range(n_if)]
    i += 2 * n_if
    members = set()
    for _ in range(2):  # fields, then methods
        n = struct.unpack(">H", b[i:i + 2])[0]
        i += 2
        for _ in range(n):
            _, ni, di, ac = struct.unpack(">HHHH", b[i:i + 8])
            i += 8
            members.add(utf(ni) + utf(di))
            for _ in range(ac):
                ln = struct.unpack(">I", b[i + 2:i + 6])[0]
                i += 6 + ln
    refs = []
    for e in pool:
        if not e:
            continue
        if e[0] == 7:
            refs.append(("class", utf(e[1]).lstrip("["), "", ""))
        elif e[0] in (9, 10, 11):
            owner = cls(e[1])
            nt = pool[e[2]]
            refs.append(("field" if e[0] == 9 else "method", owner, utf(nt[1]), utf(nt[2])))
    return name, sup, ifaces, members, refs


def load(jars):
    out = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as zf:
            for n in zf.namelist():
                if n.endswith(".class") and not n.startswith("META-INF/"):
                    try:
                        c = parse(zf.read(n))
                    except Exception:
                        continue
                    out.setdefault(c[0], c)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--api", action="append", default=[])
    ap.add_argument("--lib", action="append", default=[])
    ap.add_argument("--prefix", action="append", default=[])
    ap.add_argument("--allow")
    a = ap.parse_args()
    prefixes = tuple(a.prefix or ["com/dragonminez/"])
    allow = set()
    if a.allow:
        for line in open(a.allow, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if line:
                allow.add(line)
    duk = load([a.jar])
    world = load(a.api + a.lib)

    jdk_cache = {}

    def jdk(owner):
        """JDK classes come from javap (members inherited from java/lang/Enum, Object, ...)."""
        if not owner.startswith("java/"):
            return None
        if owner not in jdk_cache:
            try:
                txt = subprocess.run(["javap", "-p", "-s", owner.replace("/", ".")],
                                     capture_output=True, text=True).stdout
            except OSError:
                txt = ""
            dotted = owner.replace("/", ".")
            members, sup, ifaces, last = set(), None, [], None
            for line in txt.splitlines():
                line = line.strip()
                if line.endswith("{") and (" class " in f" {line}" or " interface " in f" {line}"):
                    plain = line
                    while "<" in plain:  # drop generics, innermost first
                        plain = re.sub(r"<[^<>]*>", "", plain)
                    ext = re.search(r" extends ([^{]+?)(?: implements |\s*\{)", plain)
                    imp = re.search(r" implements ([^{]+?)\s*\{", plain)
                    names = lambda m: [t.strip().replace(".", "/") for t in m.group(1).split(",")] if m else []
                    if " interface " in f" {plain}":
                        ifaces = names(ext)
                    else:
                        sup = (names(ext) or ["java/lang/Object"])[0] if owner != "java/lang/Object" else None
                        ifaces = names(imp)
                elif line.startswith("descriptor:") and last:
                    members.add(last + line.split(":", 1)[1].strip())
                elif line.endswith(";"):
                    head = line.split("(", 1)[0].split("=", 1)[0].split()
                    last = head[-1] if head else None
                    if last == dotted:
                        last = "<init>"
            jdk_cache[owner] = (owner, sup, ifaces, members, []) if txt else None
        return jdk_cache[owner]

    def has(owner, member, seen=None):
        seen = seen or set()
        if owner in seen:
            return False
        seen.add(owner)
        c = world.get(owner) or jdk(owner)
        if c is None:
            return False
        if member in c[3]:
            return True
        for parent in ([c[1]] if c[1] else []) + c[2]:
            if has(parent, member, seen):
                return True
        return False

    def is_array_or_prim(owner):
        return owner.startswith("[") or ";" in owner

    missing = defaultdict(set)
    for cname, c in duk.items():
        for kind, owner, nm, desc in c[4]:
            if is_array_or_prim(owner) or not owner.startswith(prefixes):
                continue
            if owner not in world:
                missing[owner].add(cname)
            elif kind != "class" and not has(owner, nm + desc):
                missing[f"{owner}.{nm} {desc}"].add(cname)
    bad = 0
    for key in sorted(missing):
        cls_only = key.split(".", 1)[0].split(" ", 1)[0]
        ok = key in allow or cls_only in allow
        bad += 0 if ok else 1
        users = sorted(missing[key])
        print(f"{'allowed' if ok else 'MISSING'}  {key}\n          used by {', '.join(users[:6])}"
              + (f" (+{len(users) - 6})" if len(users) > 6 else ""))
    print(f"{len(duk)} classes scanned against {', '.join(prefixes)}: {bad} missing, "
          f"{len(missing) - bad} allowed")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
