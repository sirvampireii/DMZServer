#!/usr/bin/env bash
# Build Legacy Textures (Forge 1.20.1 / 47.4.10, Java 17). Same javac-on-SRG setup as LegacyMechanics.
#   bash tools/legacytextures/build.sh
# Client classes come from a Forge 1.20.1-47.4.10 client install (the CurseForge launcher's
# libraries folder by default; set CLIENT_LIBS to use another). Output: build/legacytextures-<VERSION>.jar
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
L="$ROOT/libraries"
CL="${CLIENT_LIBS:-$HOME/curseforge/minecraft/Install/libraries}"
SRC="$HERE/src/main/java"
RES="$HERE/src/main/resources"
OUT="$HERE/build"
export PYTHONUTF8=1

win() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
SEP=":"; command -v cygpath > /dev/null && SEP=";"

VERSION="$(sed -n 's/.*String VERSION = "\([0-9.]*\)".*/\1/p' "$SRC/com/dbzlegacy/legacytextures/LegacyTextures.java")"
[[ -n "$VERSION" ]] || { echo "Could not read VERSION" >&2; exit 1; }
TOML_V="$(sed -n 's/^version = "\(.*\)"/\1/p' "$RES/META-INF/mods.toml")"
MF_V="$(sed -n 's/^Implementation-Version: *\(.*\)\r\{0,1\}$/\1/p' "$RES/META-INF/MANIFEST.MF")"
if [[ "$TOML_V" != "$VERSION" || "$MF_V" != "$VERSION" ]]; then
  echo "Version pins disagree: VERSION=$VERSION mods.toml=$TOML_V MANIFEST=$MF_V" >&2
  exit 1
fi
echo ">> Legacy Textures $VERSION"

CLIENT_SRG="$CL/net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar"
FORGE_C="$CL/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar"
for f in "$CLIENT_SRG" "$FORGE_C"; do
  [[ -f "$f" ]] || { echo "Missing $f - install Forge 1.20.1-47.4.10 client or set CLIENT_LIBS" >&2; exit 1; }
done
GECKO="$(ls "$ROOT"/mods/geckolib-forge-1.20.1-*.jar | head -1)"
CP_LIST=(
  "$FORGE_C" "$CLIENT_SRG"
  "$L/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar"
  "$L/net/minecraftforge/fmlcore/1.20.1-47.4.10/fmlcore-1.20.1-47.4.10.jar"
  "$L/net/minecraftforge/fmlloader/1.20.1-47.4.10/fmlloader-1.20.1-47.4.10.jar"
  "$L/net/minecraftforge/javafmllanguage/1.20.1-47.4.10/javafmllanguage-1.20.1-47.4.10.jar"
  "$L/net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar"
  "$L/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar"
  "$L/net/minecraftforge/mergetool/1.1.5/mergetool-1.1.5-api.jar"
  "$L/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar"
  "$L/org/ow2/asm/asm/9.6/asm-9.6.jar"
  "$L/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"
  "$L/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
  "$L/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar"
  "$L/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar"
  "$L/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar"
  "$L/com/mojang/authlib/4.0.43/authlib-4.0.43.jar"
  "$L/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar"
  "$(ls "$L"/com/mojang/datafixerupper/*/*.jar | head -1)"
  "$(ls "$L"/it/unimi/dsi/fastutil/*/*.jar | head -1)"
  "$(ls "$L"/io/netty/netty-buffer/*/*.jar | head -1)"
  "$(ls "$L"/io/netty/netty-common/*/*.jar | head -1)"
  "$(ls "$CL"/org/joml/joml/*/*.jar | tail -1)"
  "$GECKO"
)
CP=""
for p in "${CP_LIST[@]}"; do
  [[ -f "$p" ]] || { echo "Missing compile library $p" >&2; exit 1; }
  CP="${CP:+$CP$SEP}$(win "$p")"
done

echo ">> clean + compile"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/jar"
find "$SRC" -name '*.java' | sort | sed -E 's|^/([A-Za-z])/|\1:/|' > "$OUT/sources.list"
javac --release 17 -encoding UTF-8 -proc:none -Xlint:-options -cp "$CP" -d "$(win "$OUT/classes")" "@$(win "$OUT/sources.list")"

echo ">> mixin targets (javac never reads mixin target strings)"
python3 "$ROOT/scripts/audit_mixin_targets.py" --src "$(win "$SRC")" --cp "$CP" \
  --gate-plugin "$(win "$SRC/com/dbzlegacy/legacytextures/mixin/LtMixinPlugin.java")" \
  "$(win "$RES/legacytextures.mixins.json")"

echo ">> resolver tests"
mkdir -p "$OUT/test"
javac --release 17 -encoding UTF-8 -proc:none -cp "$(win "$OUT/classes")$SEP$CP" -d "$(win "$OUT/test")" \
  "$(win "$HERE/test/ResolverTest.java")"
java -cp "$(win "$OUT/test")$SEP$(win "$OUT/classes")$SEP$(win "$RES")$SEP$CP" ResolverTest

echo ">> package"
JAR="$OUT/legacytextures-$VERSION.jar"
cp -r "$OUT/classes/." "$OUT/jar/"
cp -r "$RES/." "$OUT/jar/"
rm -f "$OUT/jar/META-INF/MANIFEST.MF"
(cd "$OUT/jar" && jar --create --file "$(win "$JAR")" --manifest "$(win "$RES/META-INF/MANIFEST.MF")" .)

echo ">> jar audits"
python3 "$ROOT/tools/dmz-adaptive-difficulty/sim/audit_jar_inner_classes.py" "$(win "$JAR")" com/dbzlegacy/legacytextures/
python3 "$ROOT/scripts/audit_jar_manifest.py" "$(win "$JAR")" "$VERSION"
python3 "$HERE/audit/audit_legacytextures.py" "$(win "$JAR")" "$VERSION"

sha256sum "$JAR" | tee "$OUT/sha256.txt"
echo "Built $JAR"
