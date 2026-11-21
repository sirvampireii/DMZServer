#!/usr/bin/env bash
# Build the LM UI probe (test only, never shipped) against an LM jar.
#   LM_JAR=mods/LegacyMechanics-4.7.60.jar bash tools/lm-ui-probe/build.sh   -> tools/lm-ui-probe/out/uiprobe.jar
# Needs the CurseForge Forge 1.20.1-47.4.10 client install (~/curseforge/minecraft/Install) and the repo libraries/.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../.." && pwd)"
CF="${CF_INSTALL:-$HOME/curseforge/minecraft/Install}"
LM_JAR="${LM_JAR:-$(ls -t "$R"/mods/LegacyMechanics-*.jar | head -1)}"
w() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
SEP=";"; command -v cygpath >/dev/null 2>&1 || SEP=":"
CP=""
for p in "$CF/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar" \
  "$CF/libraries/net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar" \
  "$R/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar" \
  "$R/libraries/net/minecraftforge/fmlcore/1.20.1-47.4.10/fmlcore-1.20.1-47.4.10.jar" \
  "$R/libraries/net/minecraftforge/fmlloader/1.20.1-47.4.10/fmlloader-1.20.1-47.4.10.jar" \
  "$R/libraries/net/minecraftforge/javafmllanguage/1.20.1-47.4.10/javafmllanguage-1.20.1-47.4.10.jar" \
  "$R/libraries/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar" \
  "$R/libraries/net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar" \
  "$R/libraries/net/minecraftforge/mergetool/1.1.5/mergetool-1.1.5-api.jar" \
  "$R/libraries/com/mojang/authlib/4.0.43/authlib-4.0.43.jar" "$R/libraries/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar" \
  "$R/libraries/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar" "$(ls "$R"/libraries/com/mojang/datafixerupper/*/*.jar | head -1)" \
  "$R/libraries/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar" "$R/libraries/org/apache/maven/maven-artifact/3.8.5/maven-artifact-3.8.5.jar" \
  "$R/tools/dmz-godki-patch/out/dragonminez-2.2.1.2.jar" "$LM_JAR" \
  "$CF/libraries/org/lwjgl/lwjgl-glfw/3.3.1/lwjgl-glfw-3.3.1.jar" "$CF/libraries/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar" \
  "$R/mods/geckolib-forge-1.20.1-4.8.4.jar" "$R/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"; do
  CP="$CP$SEP$(w "$p")"
done
OUT="$HERE/out"; rm -rf "$OUT/classes"; mkdir -p "$OUT/classes/META-INF"
javac --release 17 -encoding UTF-8 -proc:none -nowarn -cp "${CP#$SEP}" -d "$(w "$OUT/classes")" "$HERE"/src/com/dbzlegacy/uiprobe/*.java
cp "$HERE/mods.toml" "$OUT/classes/META-INF/"
(cd "$OUT/classes" && jar --create --file ../uiprobe.jar .)
echo "built $OUT/uiprobe.jar against $(basename "$LM_JAR")"
