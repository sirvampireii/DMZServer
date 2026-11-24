#!/usr/bin/env bash
# LM play-test (test only; never the live server): a local test server and a game client you play yourself, with the
# LM jar you built, to look for bugs by hand. Separate from the UI probe rig (its own folders and port), so a rig run
# can go on at the same time.
#
#   bash tools/lm-ui-probe/playtest.sh                  start the server, then the client (joins it on start)
#   FRESH=1 bash tools/lm-ui-probe/playtest.sh          throw the test world away first
#   LM_JAR=path/to/LegacyMechanics-x.jar ...            another LM jar (default: the newest in mods/)
#   PLAYER=Name ...                                     the offline player name (default Tester; made op)
#
# Uses the rig's environment (RIG_SERVER as the server template, RIG_KAIPATH_JAR, RIG_EXTRA_MODS, RIG_LIVECFG; see
# run.sh). Folders: PLAY_DIR (default Desktop/Repository/lm-playtest): server/ and client/. The world is kept between
# runs. Server console: the window titled "LM test server" (type commands there; "stop" to quit).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../.." && pwd)"
: "${RIG_SERVER:?set RIG_SERVER}" "${RIG_KAIPATH_JAR:?set RIG_KAIPATH_JAR}" "${RIG_LIVECFG:?set RIG_LIVECFG}"
PLAY="${PLAY_DIR:-$(cd "$R/.." && pwd)/lm-playtest}"
SRV="$PLAY/server"; CLI="$PLAY/client"; PORT="${PLAY_PORT:-25610}"; PLAYER="${PLAYER:-Tester}"
LMJAR="${LM_JAR:-$(ls -t "$R"/mods/LegacyMechanics-*.jar | head -1)}"
# shellcheck disable=SC2206
EXTRA=(${RIG_EXTRA_MODS:-})
COMMON=("$R/tools/dmz-godki-patch/out/dragonminez-2.2.1.2.jar" "${EXTRA[@]}"
      "$R/mods/geckolib-forge-1.20.1-4.8.4.jar" "$R/mods/curios-forge-5.14.1+1.20.1.jar"
      "$(ls "$R"/mods/TerraBlender-forge-1.20.1-*.jar | head -1)"
      "$R/mods/CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260227.jar" "$R/mods/lightmanscurrency-1.20.1-2.3.0.5.jar")

echo "LM jar: $(basename "$LMJAR")"
mkdir -p "$SRV" "$CLI"
if [ ! -d "$SRV/libraries" ]; then
  echo "setting up the test server from the rig template..."
  cp -r "$RIG_SERVER/libraries" "$SRV/"
  cp "$RIG_SERVER/server.properties" "$RIG_SERVER/eula.txt" "$SRV/"
fi
cd "$SRV" || exit 1
[ "${FRESH:-0}" = 1 ] && rm -rf world
sed -i "s/^server-port=.*/server-port=$PORT/; s/^online-mode=.*/online-mode=false/; s/^level-type=.*/level-type=minecraft\\:normal/; s/^motd=.*/motd=LM play-test/" server.properties
rm -rf mods; mkdir -p mods config
cp "${COMMON[@]}" "$LMJAR" "$RIG_KAIPATH_JAR" mods/
cp -r "$RIG_LIVECFG/dragonminez" config/
cp "$RIG_LIVECFG/legacymechanics.json" config/legacymechanics.json
printf '[{"uuid":"%s","name":"%s","level":4,"bypassesPlayerLimit":false}]\n' "$(python3 -c "import uuid,sys;print(uuid.UUID(bytes=__import__('hashlib').md5(('OfflinePlayer:'+sys.argv[1]).encode()).digest(), version=3))" "$PLAYER")" "$PLAYER" > ops.json
: > logs/latest.log 2>/dev/null || mkdir -p logs
cmd //c start "LM test server" cmd //k "java -Xmx5G @libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt nogui"
echo "server starting (port $PORT)..."
for i in $(seq 1 600); do
  grep -q "Done (" logs/latest.log 2>/dev/null && break
  grep -q "Crash report saved\|Failed to start the minecraft server" logs/latest.log 2>/dev/null && { echo "server failed; see $SRV/logs/latest.log"; exit 1; }
  sleep 1
done
grep -E "Done \(|LegacyMechanics.*self-test FAILED" logs/latest.log | head -3

rm -rf "$CLI/mods"; mkdir -p "$CLI/mods"
cp "${COMMON[@]}" "$LMJAR" "$RIG_KAIPATH_JAR" "$CLI/mods/"
[ -f "$CLI/options.txt" ] || printf "guiScale:3\nonboardAccessibility:false\ntutorialStep:none\n" > "$CLI/options.txt"
CF="${CF_INSTALL:-$HOME/curseforge/minecraft/Install}"
JAVA="$(ls "$CF"/runtime/*/windows-x64/*/bin/java.exe | head -1)"
echo "client starting as $PLAYER (joins localhost:$PORT)..."
LAUNCH_USERNAME="$PLAYER" LAUNCH_UUID="$(python3 -c "import uuid,hashlib,sys;print(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+sys.argv[1]).encode()).digest(), version=3).hex)" "$PLAYER")" \
  LAUNCH_SIZE=1600x900 LAUNCH_JOIN="localhost:$PORT" PYTHONUTF8=1 \
  python3 "$R/tools/legacytextures/test/clientprobe/launch_client.py" "$(cygpath -w "$CF")" "$(cygpath -w "$JAVA")" "$(cygpath -w "$CLI")" -Xmx4G \
  > "$CLI/launch.out" 2>&1 &
echo "started. Client log: $CLI/logs/latest.log  Server log: $SRV/logs/latest.log"
