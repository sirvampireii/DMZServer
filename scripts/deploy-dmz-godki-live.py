"""Swap live DMZ to dragonminez-2.2.1-godki.jar (tools/dmz-godki-patch).

Usage: python scripts/deploy-dmz-godki-live.py <live-sftp.env> <patched jar> LIVE

Moves every live mods/dragonminez-*.jar to recycle_bin/ (rollback: move it back, delete the godki
jar), uploads the patched jar through a .part file, and checks the remote sha256 against the local
one. Live mods/ ends with exactly one dragonminez jar. Run the godki.json patch
(scripts/patch-godki-json-godki-copy.py --apply) with it. The server needs a full restart afterwards.
"""
import hashlib
import sys
import time

import paramiko

if len(sys.argv) != 4 or sys.argv[3] != "LIVE":
    sys.exit(__doc__)
env = {}
for line in open(sys.argv[1], encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
local = sys.argv[2]
name = "dragonminez-2.2.1-godki.jar"
want = hashlib.sha256(open(local, "rb").read()).hexdigest()
print("local", name, want)

t = paramiko.Transport((env["LIVE_SFTP_HOST"], int(env.get("LIVE_SFTP_PORT", "2022"))))
t.connect(username=env["LIVE_SFTP_USER"], password=env["LIVE_SFTP_PASS"])
s = paramiko.SFTPClient.from_transport(t)
try:
    s.stat("recycle_bin")
except IOError:
    s.mkdir("recycle_bin")
stamp = time.strftime("%Y%m%d-%H%M%S")
for f in s.listdir("mods"):
    if f.startswith("dragonminez-") and f.endswith(".jar"):
        dest = f"recycle_bin/{f}.pre-godki-{stamp}"
        s.rename(f"mods/{f}", dest)
        print("archived", f, "->", dest)
s.put(local, f"mods/{name}.part")
s.rename(f"mods/{name}.part", f"mods/{name}")
h = hashlib.sha256()
with s.open(f"mods/{name}", "rb") as f:
    f.prefetch()
    for chunk in iter(lambda: f.read(1 << 20), b""):
        h.update(chunk)
print("remote", name, h.hexdigest(), "MATCH" if h.hexdigest() == want else "MISMATCH")
print("mods dragonminez jars:", sorted(x for x in s.listdir("mods") if x.startswith("dragonminez-")))
t.close()
if h.hexdigest() != want:
    sys.exit(1)
