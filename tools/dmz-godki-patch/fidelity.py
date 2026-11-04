"""fidelity.py <origDir> <newDir> <class rel path>...: compare javap member lists and per-method
normalized instruction streams (offsets, cp indices and branch targets stripped)."""
import re
import subprocess
import sys


def javap(path, *flags):
    return subprocess.run(["javap", *flags, path], capture_output=True, text=True).stdout


def methods(path):
    out = javap(path, "-c", "-p")
    res, cur, body = {}, None, []
    for line in out.splitlines():
        m = re.match(r"^  (\S.*\));?$", line) or re.match(r"^  (static \{\});?$", line)
        if m and not line.startswith("    "):
            if cur:
                res[cur] = body
            cur, body = m.group(1), []
            continue
        ins = re.match(r"^\s+\d+: (\w+)\s*(.*)$", line)
        if cur and ins:
            op, rest = ins.groups()
            comment = rest.split("//", 1)[1].strip() if "//" in rest else ""
            if op.startswith(("if", "goto")) or op in ("tableswitch", "lookupswitch"):
                comment = ""
            res_line = op + " " + comment
            # lambda bodies get numbered by order; normalize
            res_line = re.sub(r"lambda\$(\w+)\$\d+", r"lambda$\1$N", res_line)
            body.append(res_line)
    if cur:
        res[cur] = body
    return res


orig, new = sys.argv[1], sys.argv[2]
bad = 0
for rel in sys.argv[3:]:
    a, b = f"{orig}/{rel}", f"{new}/{rel}"
    ma = sorted(re.sub(r"lambda\$(\w+)\$\d+", r"lambda$\1$N", l) for l in javap(a, "-p").splitlines())
    mb = sorted(re.sub(r"lambda\$(\w+)\$\d+", r"lambda$\1$N", l) for l in javap(b, "-p").splitlines())
    if ma != mb:
        bad += 1
        print(f"{rel}: MEMBER LIST DIFFERS")
        for x in sorted(set(ma) ^ set(mb)):
            print("   ", "orig" if x in ma else "new ", x)
    xa, xb = methods(a), methods(b)
    diff = [k for k in sorted(set(xa) | set(xb)) if not k.startswith("private static java.lang.Object lambda") and xa.get(k) != xb.get(k)]
    lam_a = sorted(v for k, v in xa.items() if "lambda$" in k)
    print(f"{rel}: {len(xa)} methods, {len(diff)} differ")
    for k in diff:
        bad += 1
        la, lb = xa.get(k, []), xb.get(k, [])
        print(f"   DIFF {k}: {len(la)} vs {len(lb)} instructions")
print("FIDELITY", "OK" if bad == 0 else f"{bad} difference(s)")
