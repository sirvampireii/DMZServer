"""God Ki v3 patch (DMZ 2.2.1-godki v3): raise God Ki / Divine Ki mastery to 10,000 in godki.json.

godki maxMastery 5000 -> 10000, divineki maxMastery 5000 -> 10000, divineki unlockOnMastery
5000 -> 10000 (Divine Ki unlocks when God Ki is fully mastered). Every other field is kept as is.
The v3 jar's DIVINEKI_FULL_MASTERY (10000) must match divineki maxMastery.

Usage: python scripts/patch-godki-json-v3.py <live-sftp.env> [--apply]
Without --apply it only prints the planned change. With --apply it saves a backup next to the file
(godki.json.pre_godki_v3_backup, once) and writes the patched JSON. Then run /dmzreload config.
"""
import json
import sys

import paramiko

PATH = "config/dragonminez/forms/godki.json"
BACKUP = PATH + ".pre_godki_v3_backup"
CHANGES = [("godki", "maxMastery", 10000.0), ("divineki", "maxMastery", 10000.0),
           ("divineki", "unlockOnMastery", 10000.0)]

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
for form, key, value in CHANGES:
    print(f"{form}.{key}: {cfg['forms'][form].get(key)} -> {value}")
    cfg["forms"][form][key] = value
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
    print("verified:", all(check["forms"][form][key] == value for form, key, value in CHANGES))
else:
    print("dry run - nothing written")
t.close()
