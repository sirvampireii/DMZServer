#!/usr/bin/env python3
"""Verify dmzultra-0.5.7-noscale-placefix-podfix.jar against the placefix jar it was built from.

    python tools/dmzultra-podfix/verify.py <dmzultra-0.5.7-noscale-placefix.jar> <...-podfix.jar>

Exits 1 on any failure. Checks:
  - same entries; every entry except PodVoyage.class and the manifest is byte-identical;
  - PodVoyage gains exactly podfixLeftPod(ServerPlayer, Voyage, long) and the static field PODFIX_HELD;
  - PodVoyage.request(player, port): only the landing ticket distance 2 became 4;
  - PodVoyage.advance differs only where `ldc "they left the pod"; invokestatic cancel` became
    `aload 4; lload_1; invokestatic podfixLeftPod`; every other method is unchanged;
  - podfixLeftPod still cancels with "they left the pod", checks voyage.port, and every PodVoyage /
    PodVoyage$Voyage member it uses exists in the real classes with the same descriptor;
  - the manifest Build-Time ends in " noscale placefix podfix"; mods.toml still says version 0.5.7.
"""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

PV = "com/dmzultra/multiverse/PodVoyage"
VOYAGE_CLASS = PV + ".class"
INNER_CLASS = PV + "$Voyage.class"
MANIFEST = "META-INF/MANIFEST.MF"
PLAYER = "net.minecraft.server.level.ServerPlayer"
failures = []


def check(name, ok, detail=""):
    print(("  OK  " if ok else "  FAIL ") + name + ("" if ok else f" -- {detail}"))
    if not ok:
        failures.append(name)


def javap(data, tmp, name, verbose=True):
    p = Path(tmp) / name
    p.write_bytes(data)
    flags = ["-p", "-c", "-constants"] + (["-v"] if verbose else [])
    out = subprocess.run(["javap", "-J-Dstdout.encoding=UTF-8", *flags, str(p)], capture_output=True, check=True).stdout
    return out.decode("utf-8", errors="replace").replace("\r\n", "\n")


def methods(dump):
    """{signature line: [normalized instructions]} from a javap -c dump."""
    out = {}
    cur = None
    in_code = False
    for line in dump.split("\n"):
        if re.match(r"^  \S.*\);$", line):
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
            if op.startswith(("if", "goto", "jsr")) or op in ("tableswitch", "lookupswitch"):
                rest = ""
            out[cur].append((op + " " + rest).strip())
        elif in_code and re.match(r"^\s+(LineNumberTable|LocalVariableTable|StackMapTable|Exception table)", line):
            in_code = False
    return out


def members(dump):
    """Field and method declarations of a javap -p (non-verbose) dump, as 'name:descriptor'-ish lines."""
    return set(l.strip() for l in dump.split("\n") if l.startswith("  ") and not l.startswith("   "))


def main(base_path, patched_path):
    base = zipfile.ZipFile(base_path)
    patched = zipfile.ZipFile(patched_path)
    b_names, p_names = set(base.namelist()), set(patched.namelist())
    check("same entries", b_names == p_names, f"{sorted(b_names ^ p_names)[:5]}")
    changed = [n for n in b_names & p_names if n not in (VOYAGE_CLASS, MANIFEST) and base.read(n) != patched.read(n)]
    check("every other entry is byte-identical", not changed, str(changed[:5]))

    with tempfile.TemporaryDirectory() as tmp:
        d0 = javap(base.read(VOYAGE_CLASS), tmp, "v0.class")
        d1 = javap(patched.read(VOYAGE_CLASS), tmp, "v1.class")
        m0, m1 = methods(d0), methods(d1)
        hook = [s for s in m1 if " podfixLeftPod(" in s]
        check("PodVoyage gains exactly podfixLeftPod", set(m1) - set(m0) == set(hook) and len(hook) == 1
              and set(m0) <= set(m1), str(set(m1) ^ set(m0)))
        check("podfixLeftPod is private static (ServerPlayer, Voyage, long)",
              hook == [f"private static void podfixLeftPod({PLAYER}, com.dmzultra.multiverse.PodVoyage$Voyage, long);"], str(hook))
        check("PODFIX_HELD is a private static Map",
              re.search(r"^  private static java\.util\.Map<java\.util\.UUID, java\.lang\.Long> PODFIX_HELD;$", d1, re.M) is not None)
        adv = f"public static void advance({PLAYER}, long);"
        a0, a1 = m0[adv], m1[adv]
        i = a0.index("ldc_w # // String they left the pod")
        want = a0[:i] + ["aload 4", "lload_1",
                         f"invokestatic # // Method podfixLeftPod:(L{PLAYER.replace('.', '/')};L{PV}$Voyage;J)V"] + a0[i + 2:]
        check("advance: only the left-the-pod cancel became podfixLeftPod(player, voyage, now)", a1 == want,
              str([(x, y) for x, y in zip(a0, a1) if x != y][:4]))
        req = f"public static boolean request({PLAYER}, com.dmzultra.multiverse.PodVoyage$Port);"
        r0, r1 = m0[req], m1[req]
        rdiff = [(x, y) for x, y in zip(r0, r1) if x != y]
        check("request(player, port): only the landing ticket distance changed, 2 -> 4",
              len(r0) == len(r1) and rdiff == [("iconst_2", "iconst_4")], str(rdiff[:3]))
        others = [s for s in m0 if s not in (adv, req)]
        check("every other PodVoyage method is unchanged", all(m0[s] == m1[s] for s in others),
              str([s for s in others if m0[s] != m1[s]][:3]))

        body = m1[hook[0]] if hook else []
        text = "\n".join(body)
        check('podfixLeftPod still cancels with "they left the pod"',
              "String they left the pod" in text and "Method cancel:(" in text)
        check("podfixLeftPod reads voyage.port", f"Field {PV}$Voyage.port:" in text or "Field port:" in text)
        real_v = javap(base.read(VOYAGE_CLASS), tmp, "rv.class", verbose=False)
        real_i = javap(base.read(INNER_CLASS), tmp, "ri.class", verbose=False)
        refs = re.findall(r"// (Field|Method) ((?:com/dmzultra/multiverse/PodVoyage\$Voyage\.)?)(\w+):(\S+)", text)
        own = [(k, owner, n, d) for k, owner, n, d in refs if (owner or "com/dmzultra/multiverse/PodVoyage.") and
               (owner.startswith("com/dmzultra/multiverse/PodVoyage") or owner == "")]
        missing = []
        for kind, owner, name, desc in own:
            dump = real_i if owner else real_v
            if name == "PODFIX_HELD":
                continue
            found = any(re.search(r"\b" + re.escape(name) + r"\b", l) for l in members(dump))
            if not found:
                missing.append(f"{owner}{name}:{desc}")
        check("every PodVoyage/Voyage member podfixLeftPod uses exists in the real classes", not missing and own,
              str(missing))
        # descriptor-exact check for Voyage fields
        iv = javap(base.read(INNER_CLASS), tmp, "iv.class")
        bad = []
        for kind, owner, name, desc in own:
            if owner and kind == "Field":
                m = re.search(r"^  .* " + re.escape(name) + r";\n\s+descriptor: (\S+)", iv, re.M)
                if not m or m.group(1) != desc:
                    bad.append(f"{name}:{desc} vs {m.group(1) if m else None}")
            if not owner and kind == "Method" and name != "podfixLeftPod":
                m = re.search(r"^  .* " + re.escape(name) + r"\(.*\);\n\s+descriptor: " + re.escape(desc) + "$", d0, re.M)
                if not m:
                    bad.append(f"{name}{desc}")
        check("their descriptors match the real classes", not bad, str(bad))

    mf = patched.read(MANIFEST).decode("utf-8")
    check('manifest Build-Time ends in " noscale placefix podfix"',
          re.search(r"^Build-Time: .* noscale placefix podfix\r?$", mf, re.M) is not None)
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
