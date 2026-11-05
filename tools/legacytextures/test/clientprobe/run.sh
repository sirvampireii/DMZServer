#!/usr/bin/env bash
# Client smoke test for Legacy Textures (test only; opens a game window for a few minutes).
#   [LT_SERVER=host:port] bash tools/legacytextures/test/clientprobe/run.sh <game dir> <dragonminez jar>
# Builds the probe mod, puts DMZ + its required mods + Legacy Textures + the probe in <game dir>/mods
# (no nbarmors jar: Legacy Textures carries NB Armors; no HD resource pack), starts the client offline,
# waits for the probe to quit the game and prints ltprobe-results.txt. Run build.sh first.
# With LT_SERVER the probe also joins that server; when it writes <game dir>/ltprobe-need-gear, give the
# player (name in the file) nbarmors:angel1_chest and put nbarmors:aeoshead in Curios slot vanity 0.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LT="$(cd "$HERE/../.." && pwd)"
ROOT="$(cd "$LT/../.." && pwd)"
GAME="$1"; DMZ="$2"
CF="${CF_INSTALL:-$HOME/curseforge/minecraft/Install}"
JAVA="${CF_JAVA:-$(ls "$CF"/runtime/*/windows-x64/*/bin/java.exe | head -1)}"
export PYTHONUTF8=1
win() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
SEP=";"
LTJAR="$(ls "$LT"/build/legacytextures-*.jar | head -1)"
CP="$(win "$LT/build/classes")"
for p in "$CF/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar" \
         "$CF/libraries/net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar" \
         "$ROOT/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar" \
         "$ROOT/libraries/net/minecraftforge/fmlcore/1.20.1-47.4.10/fmlcore-1.20.1-47.4.10.jar" \
         "$ROOT/libraries/net/minecraftforge/fmlloader/1.20.1-47.4.10/fmlloader-1.20.1-47.4.10.jar" \
         "$ROOT/libraries/net/minecraftforge/javafmllanguage/1.20.1-47.4.10/javafmllanguage-1.20.1-47.4.10.jar" \
         "$ROOT/libraries/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar" \
         "$ROOT/libraries/net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar" \
         "$ROOT/libraries/net/minecraftforge/mergetool/1.1.5/mergetool-1.1.5-api.jar" \
         "$ROOT/libraries/com/mojang/authlib/4.0.43/authlib-4.0.43.jar" \
         "$ROOT/libraries/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar" \
         "$ROOT/libraries/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar" \
         "$(ls "$ROOT"/libraries/com/mojang/datafixerupper/*/*.jar | head -1)" \
         "$ROOT/libraries/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar" \
         "$ROOT/libraries/org/apache/maven/maven-artifact/3.8.5/maven-artifact-3.8.5.jar" \
         "$ROOT/mods/geckolib-forge-1.20.1-4.8.4.jar" \
         "$ROOT/mods/curios-forge-5.14.1+1.20.1.jar" \
         "$DMZ"; do
  CP="$CP$SEP$(win "$p")"
done
PB="$GAME/.probe-build"
rm -rf "$PB"; mkdir -p "$PB/classes/META-INF"
javac --release 17 -encoding UTF-8 -proc:none -nowarn -cp "$CP" -d "$(win "$PB/classes")" \
  "$(win "$HERE/src/com/dbzlegacy/ltprobe/LtClientProbe.java")"
cp "$HERE/mods.toml" "$PB/classes/META-INF/mods.toml"
cp "$LT/src/main/resources/pack.mcmeta" "$PB/classes/pack.mcmeta"
(cd "$PB/classes" && jar --create --file "$(win "$PB/ltprobe.jar")" .)

mkdir -p "$GAME/mods"
rm -f "$GAME"/mods/*.jar "$GAME/ltprobe-results.txt" "$GAME/ltprobe-need-gear" "$GAME"/screenshots/ltprobe-*.png
rm -rf "$GAME/config/legacytextures"
cp "$DMZ" "$LTJAR" "$PB/ltprobe.jar" "$GAME/mods/"
cp "$ROOT"/mods/geckolib-forge-1.20.1-4.8.4.jar "$ROOT"/mods/curios-forge-5.14.1+1.20.1.jar "$GAME/mods/"
cp "$(ls "$ROOT"/mods/TerraBlender-forge-1.20.1-*.jar | head -1)" "$GAME/mods/"
echo ">> starting the client (offline user LTProbe); the probe quits the game when done"
EXTRA="${LT_JVM_EXTRA:-}"
[[ -n "${LT_SERVER:-}" ]] && EXTRA="$EXTRA -Dltprobe.server=$LT_SERVER"
set +e
python3 "$HERE/launch_client.py" "$(win "$CF")" "$(win "$JAVA")" "$(win "$GAME")" $EXTRA
code=$?
set -e
echo ">> client exited with $code"
if [[ -f "$GAME/ltprobe-results.txt" ]]; then cat "$GAME/ltprobe-results.txt"; else echo "no results (see $GAME/launch.log, $GAME/logs/latest.log)"; exit 1; fi
grep -q "^PROBE OK" "$GAME/ltprobe-results.txt"
