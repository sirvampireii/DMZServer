#!/usr/bin/env python3
"""Start a Forge 1.20.1-47.4.10 client offline from a CurseForge install (test use only).

usage: launch_client.py <install dir> <java.exe> <game dir> [extra jvm args...]

Reads versions/forge-47.4.10/forge-47.4.10.json (+ the inherited 1.20.1.json), builds the
classpath and module path the launcher would, and runs the game with an offline user name.
Returns the game's exit code.
"""
import json
import os
import subprocess
import sys

inst, java, game = sys.argv[1], sys.argv[2], sys.argv[3]
extra = sys.argv[4:]
lib = os.path.join(inst, "libraries")
forge = json.load(open(os.path.join(inst, "versions", "forge-47.4.10", "forge-47.4.10.json"), encoding="utf-8"))
van = json.load(open(os.path.join(inst, "versions", "1.20.1", "1.20.1.json"), encoding="utf-8"))


def allowed(entry):
    rules = entry.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        osname = r.get("os", {}).get("name")
        if osname is None or osname == "windows":
            ok = r["action"] == "allow"
    return ok


cp, seen = [], set()
for entry in forge["libraries"] + van["libraries"]:
    if not allowed(entry):
        continue
    art = entry.get("downloads", {}).get("artifact")
    if not art:
        continue
    parts = entry["name"].split(":")
    key = ":".join(parts[:2] + parts[3:])  # group:artifact[:classifier]
    if key in seen:
        continue
    seen.add(key)
    path = os.path.join(lib, art["path"].replace("/", os.sep))
    if os.path.isfile(path):
        cp.append(path)
cp.append(os.path.join(inst, "versions", "forge-47.4.10", "forge-47.4.10.jar"))
sep = ";"
subs = {"${library_directory}": lib, "${classpath_separator}": sep, "${version_name}": "forge-47.4.10"}


def sub(a):
    for k, v in subs.items():
        a = a.replace(k, v)
    return a


natives = os.path.join(game, "natives")
os.makedirs(natives, exist_ok=True)
jvm = ["-Xmx4G", "-Djava.library.path=" + natives, "-Dminecraft.launcher.brand=ltprobe"] + extra
jvm += [sub(a) for a in forge["arguments"]["jvm"]]
# Play-testing (tools/lm-ui-probe/playtest.sh): LAUNCH_USERNAME / LAUNCH_UUID pick the offline player, LAUNCH_SIZE the
# window ("1280x720"), LAUNCH_JOIN a server to join on start ("localhost:25610"). The probe keeps its defaults.
name = os.environ.get("LAUNCH_USERNAME", "LTProbe")
uuid = os.environ.get("LAUNCH_UUID", "00000000000000000000000000000001")
w, h = os.environ.get("LAUNCH_SIZE", "854x480").split("x")
join = ["--quickPlayMultiplayer", os.environ["LAUNCH_JOIN"]] if os.environ.get("LAUNCH_JOIN") else []
cmd = [java] + jvm + ["-cp", sep.join(cp), forge["mainClass"],
                      "--username", name, "--version", "forge-47.4.10", "--gameDir", game,
                      "--assetsDir", os.path.join(inst, "assets"), "--assetIndex", van["assetIndex"]["id"],
                      "--uuid", uuid, "--accessToken", "0", "--userType", "legacy",
                      "--versionType", "release", "--width", w, "--height", h] + forge["arguments"]["game"] + join
with open(os.path.join(game, "launch.log"), "w", encoding="utf-8") as out:
    sys.exit(subprocess.call(cmd, cwd=game, stdout=out, stderr=subprocess.STDOUT))
