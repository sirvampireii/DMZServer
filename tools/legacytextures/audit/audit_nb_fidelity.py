#!/usr/bin/env python3
"""usage: audit_nb_fidelity.py <nbarmors-0.1.0.jar> <legacytextures jar>

The absorbed NB Armors classes were decompiled and recompiled. Fail unless every class keeps exactly the
original members (fields, methods, signatures, modifiers), with only the package renamed.
"""
import re, subprocess, sys, zipfile

orig_jar, new_jar = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(orig_jar) as z:
    classes = sorted(n[:-6].replace("/", ".") for n in z.namelist() if n.startswith("com/nbarmors/") and n.endswith(".class"))


def members(jar, cls):
    out = subprocess.run(["javap", "-p", "-cp", jar, cls], capture_output=True, text=True).stdout
    lines = [l.strip() for l in out.splitlines()[1:] if l.strip() and l.strip() != "}"]
    return sorted(re.sub(r"com\.dbzlegacy\.legacytextures\.nb", "com.nbarmors", l) for l in lines)


bad = 0
for c in classes:
    n = c.replace("com.nbarmors", "com.dbzlegacy.legacytextures.nb")
    a, b = members(orig_jar, c), members(new_jar, n)
    if a != b:
        bad += 1
        print("DIFF", c)
        for l in sorted(set(a) ^ set(b)):
            print("   ", "orig" if l in a else "new ", l)
print(f"NB fidelity: {len(classes)} classes compared, {bad} with member differences")
sys.exit(1 if bad else 0)
