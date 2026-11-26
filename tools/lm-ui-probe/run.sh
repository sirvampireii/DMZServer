#!/usr/bin/env bash
# LM UI probe run (test only; never on the live server).
#   bash tools/lm-ui-probe/run.sh <label> <server LM jar> <staff 1|0> <client LM jar or -> <client KaiPath 1|0>
#
# Boots a local test server, joins one real client, crawls every LM menu, screenshots each at GUI scale 2, 3
# and 4 (1920x1080), runs the scripted checks in UiProbe (Difficulty guide, Momentum, free trial, spin stop),
# and copies the results to $RIG_OUT/<label>/ (uiprobe-packets.jsonl, uiprobe-script.txt, uiprobe-client.txt,
# ui-*.png). Then: python analyze_layout.py <dir>; python analyze_skin.py <dir> [<older dir>].
#
# Machine setup (environment; nothing private is committed):
#   RIG_SERVER       prepared Forge 1.20.1-47.4.10 server dir (libraries/, win_args.txt, server.properties on
#                    port 25600). Its world, config and mods are wiped each run.
#   RIG_CLIENT       client game dir for the probe client (wiped each run).
#   RIG_KAIPATH_JAR  KaiPath jar for "KaiPath 1" (server and/or client); never modified.
#   RIG_LIVECFG      dir with legacymechanics.json and dragonminez/ copied from the live server.
#   RIG_EXTRA_MODS   space-separated extra jars for both sides (DMZ Ultra build, DMZ Revamp, ...).
#   RIG_SERVER_ONLY  space-separated extra jars for the server only (optional).
#   RIG_OUT          results root (default tools/lm-ui-probe/out, gitignored).
# Options: MAXSHOTS=n (cap screenshot pages), REQUIRE=1 (requireLmClient true), SRVJVM="extra server JVM
#   flags" (e.g. -Xlog:class+load=info:file=classload.log), REJECT=1 (expect the login to be turned away),
#   SRVKAI=0 (no KaiPath on the server).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../.." && pwd)"
: "${RIG_SERVER:?set RIG_SERVER}" "${RIG_CLIENT:?set RIG_CLIENT}" "${RIG_KAIPATH_JAR:?set RIG_KAIPATH_JAR}"
: "${RIG_LIVECFG:?set RIG_LIVECFG}"
OUTROOT="${RIG_OUT:-$HERE/out}"
L="$1"; LMJAR="$2"; STAFF="$3"; CLM="$4"; CKAI="$5"
T="$RIG_SERVER"; G="$RIG_CLIENT"; KAI="$RIG_KAIPATH_JAR"
PROBE="$HERE/out/uiprobe.jar"
[ -f "$PROBE" ] || { echo "build the probe first: LM_JAR=$LMJAR bash $HERE/build.sh"; exit 1; }
# shellcheck disable=SC2206
EXTRA=(${RIG_EXTRA_MODS:-}); SONLY=(${RIG_SERVER_ONLY:-})
COMMON=("$R/tools/dmz-godki-patch/out/dragonminez-2.2.1.2.jar" "${EXTRA[@]}"
      "$R/mods/geckolib-forge-1.20.1-4.8.4.jar" "$R/mods/curios-forge-5.14.1+1.20.1.jar"
      "$(ls "$R"/mods/TerraBlender-forge-1.20.1-*.jar | head -1)" "$PROBE"
      "$R/mods/CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260227.jar" "$R/mods/lightmanscurrency-1.20.1-2.3.0.5.jar")
mkdir -p "$OUTROOT"

# One rig run at a time. Every process a run starts carries -Dlmrig.run=<id>; a stale run's server, client or
# command feed (left behind by an interrupted run) is stopped before this one starts, and this run's own are
# stopped when it exits, so two runs can never share the port, the probe player name or the server folder.
# The lock lives in the shared server folder, not in $RIG_OUT: two runs with different output folders still
# share the server, client, port and probe name. A matrix holds its own lock across all stages (matrix.sh
# exports RIG_MATRIX_PID), so another matrix cannot start a stage between two of this one's.
LOCK="$T/.rig.lock"
MLOCK="$T/.rig-matrix.lock"
if [ -f "$MLOCK" ] && kill -0 "$(cat "$MLOCK" 2>/dev/null)" 2>/dev/null \
    && [ "$(cat "$MLOCK" 2>/dev/null)" != "${RIG_MATRIX_PID:-none}" ]; then
  echo "another rig matrix is active (pid $(cat "$MLOCK")); stop it with tools/lm-ui-probe/stop.sh first"; exit 2
fi
if [ -f "$LOCK" ] && kill -0 "$(cat "$LOCK" 2>/dev/null)" 2>/dev/null; then
  echo "another rig run is active (pid $(cat "$LOCK")); wait for it or stop it with tools/lm-ui-probe/stop.sh"; exit 2
fi
echo $$ > "$LOCK"
RUNID="$L-$$"
rig_stop() {  # $1 = regex over process command lines
  powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -match '$1' -and \$_.Name -notmatch '^(bash|sh|powershell|pwsh|cmd)\.exe$' -and \$_.ProcessId -ne \$PID } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }" > /dev/null 2>&1
}
rig_stop 'lmrig\.run=|uiprobe\.server=|tail(\.exe)?"? -f .*cmd-'
cleanup() { rig_stop "lmrig\.run=$RUNID|tail(\.exe)?\"? -f .*cmd-$L"; rm -f "$LOCK"; }
trap cleanup EXIT INT TERM

cd "$T" || exit 1
rm -rf mods/*.jar world logs crash-reports config defaultconfigs legacymechanics* uiprobe-packets.jsonl uiprobe-script.txt classload.log
sed -i "s/^level-type=.*/level-type=minecraft\:flat/" server.properties
mkdir -p config mods && cp -r "$RIG_LIVECFG/dragonminez" config/
PYTHONUTF8=1 python3 - "$RIG_LIVECFG/legacymechanics.json" config/legacymechanics.json "${REQUIRE:-0}" <<'PY'
import json, sys
c = json.load(open(sys.argv[1], encoding="utf-8"))
c["debugLogging"] = True
c["requireLmClient"] = sys.argv[3] == "1"
json.dump(c, open(sys.argv[2], "w", encoding="utf-8"), indent=2)
PY
cp "${COMMON[@]}" "$LMJAR" mods/; [ ${#SONLY[@]} -gt 0 ] && cp "${SONLY[@]}" mods/; [ "${SRVKAI:-1}" = 1 ] && cp "$KAI" mods/
LOG="$OUTROOT/srv-$L.log"; CMD="$OUTROOT/cmd-$L"; : > "$CMD"
before="$(ps | awk '$NF ~ /tail$/ {print $1}' | sort)"
( tail -f "$CMD" | java -Xmx5G -Dlmrig.run=$RUNID -Duiprobe=1 -Dlm.selftest.strict=true ${MAXSHOTS:+-Duiprobe.maxshots=$MAXSHOTS} ${SRVJVM:-} @libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt nogui > "$LOG" 2>&1 ) &
sleep 2
mine="$(comm -13 <(echo "$before") <(ps | awk '$NF ~ /tail$/ {print $1}' | sort))"
for i in $(seq 1 900); do
  grep -q "Done (" "$LOG" && break
  grep -q "Crash report saved\|Failed to start the minecraft server" "$LOG" && break
  sleep 1
done
grep -E "Done \(|LegacyMechanics.*[0-9]+\.[0-9]+\.|Crash report|self-test" "$LOG" | head -6
if [ "$STAFF" = 1 ]; then echo "op LTProbe" >> "$CMD"; else echo "deop LTProbe" >> "$CMD"; fi
for c in "time set midnight" "gamerule doDaylightCycle false" "gamerule doMobSpawning false" "difficulty normal"; do echo "$c" >> "$CMD"; done
rm -rf "$G/mods" "$G/config" "$G/defaultconfigs" "$G/screenshots" "$G/uiprobe-client.txt"; mkdir -p "$G/mods"
# 4.7.62: vsync off and no frame cap, so the fps= on each SCREEN line measures real headroom (vsync pinned it at
# the monitor rate, 60, and the FPS budget could not tell builds apart).
printf "guiScale:2\nonboardAccessibility:false\nenableVsync:false\nmaxFps:260\ntutorialStep:none\n" > "$G/options.txt"
cp "${COMMON[@]}" "$G/mods/"
[ "$CKAI" = 1 ] && cp "$KAI" "$G/mods/"
[ "$CLM" != "-" ] && cp "$CLM" "$G/mods/"
echo "client mods: $(ls "$G/mods" | tr '\n' ' ')"
CF="${CF_INSTALL:-$HOME/curseforge/minecraft/Install}"
JAVA="$(ls "$CF"/runtime/*/windows-x64/*/bin/java.exe | head -1)"
PYTHONUTF8=1 python3 "$R/tools/legacytextures/test/clientprobe/launch_client.py" "$(cygpath -w "$CF")" "$(cygpath -w "$JAVA")" \
  "$(cygpath -w "$G")" -Xmx4G -Dlmrig.run=$RUNID -Duiprobe.server=localhost:25600 > "$OUTROOT/uic-$L.out" 2>&1 &
CLI=$!
if [ "${REJECT:-0}" = 1 ]; then
  for i in $(seq 1 900); do grep -q "requireLmClient: turning away" "$LOG" && break; sleep 1; done
  sleep 20
  cp "$G/logs/latest.log" "$OUTROOT/uic-$L.log" 2>/dev/null
  kill $CLI 2>/dev/null
  # Stop the probe client JVM by its game dir (the window title is not reliable).
  for pid in $(wmic process where "name='javaw.exe' or name='java.exe'" get processid,commandline 2>/dev/null | grep -F "$(basename "$G")" | awk '{print $NF}'); do taskkill //F //PID "$pid" > /dev/null 2>&1; done
else
  for i in $(seq 1 900); do grep -q "LTProbe joined the game" "$LOG" && break; sleep 1; done
  sleep 3; echo "kitest make saiyan warrior" >> "$CMD"
  wait $CLI
  cp "$G/logs/latest.log" "$OUTROOT/uic-$L.log" 2>/dev/null
fi
echo stop >> "$CMD"
for i in $(seq 1 120); do grep -q "All dimensions are saved" "$LOG" && break; sleep 1; done
sleep 5
for pid in $mine; do kill "$pid" 2>/dev/null; done
D="$OUTROOT/$L"; mkdir -p "$D"
cp "$T/uiprobe-packets.jsonl" "$T/uiprobe-script.txt" "$D/" 2>/dev/null; cp "$G/uiprobe-client.txt" "$D/" 2>/dev/null; cp "$G"/screenshots/ui-*.png "$D/" 2>/dev/null
[ -f "$T/classload.log" ] && cp "$T/classload.log" "$D/"
rm -f "$T"/mods/*.jar "$T"/uiprobe-packets.jsonl "$T"/uiprobe-script.txt
echo "== results in $D"; wc -l "$D/"*.jsonl "$D/"*.txt 2>/dev/null; ls "$D" | grep -c png
grep -a -iE "uiprobe|self-check|self-test|FAILED|Exception|native" "$LOG" | cut -c1-250 | head -16
