"""God Ki copy patch (DMZ 2.2.1-godki): clear godki's incompatibleWith list in godki.json.

The list (20 god forms) stopped God Ki stacking on god forms; with the patched DMZ jar God Ki copies
the strongest god form instead, so the gate goes. Every other field is kept as is, including God Ki's
and Divine Ki's fixed multipliers (inert under the patched jar).

Usage: python scripts/patch-godki-json-godki-copy.py <live-sftp.env> [--apply]
Without --apply it only prints the planned change. With --apply it saves a backup next to the file
(godki.json.pre_godki_copy_backup, once) and writes the patched JSON. Ship it together with
dragonminez-2.2.1-godki.jar: on the stock jar an empty list lets the fixed 1.3x God Ki stack on
god forms.
"""
import json
import sys

import paramiko

PATH = "config/dragonminez/forms/godki.json"
BACKUP = PATH + ".pre_godki_copy_backup"

env = {}
for line in open(sys.argv[1], encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
apply = "--apply" in sys.argv[2:]
t = paramiko.Transport((env["LIVE_SFTP_HOST"], int(env.get("LIVE_SFTP_PORT", "2022"))))
t.connect(username=env["LIVE_SFTP_USER"], password=env["LIVE_SFTP_PASS"])
s = paramiko.SFTPClient.from_transport(t)
with s.open(PATH, "r") as f:
    raw = f.read()
cfg = json.loads(raw.decode("utf-8"))
before = cfg["forms"]["godki"].get("incompatibleWith", [])
print(f"godki.incompatibleWith: {len(before)} entries -> 0")
for entry in before:
    print("   -", entry)
cfg["forms"]["godki"]["incompatibleWith"] = []
if apply:
    try:
        s.stat(BACKUP)
        print("backup already present:", BACKUP)
    except IOError:
        with s.open(BACKUP, "w") as f:
            f.write(raw)
        print("backup written:", BACKUP)
    with s.open(PATH, "w") as f:
        f.write(json.dumps(cfg, indent=2).encode("utf-8"))
    with s.open(PATH, "r") as f:
        check = json.loads(f.read().decode("utf-8"))
    print("verified:", check["forms"]["godki"]["incompatibleWith"] == [])
else:
    print("dry run - nothing written")
t.close()
