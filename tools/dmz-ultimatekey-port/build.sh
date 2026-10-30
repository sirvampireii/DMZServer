#!/usr/bin/env bash
# Build DMZ Ultimate Key for DragonMineZ 2.2 from the 0.2.20 jar plus the ported sources in src/.
# The empires layer reads DMZ Ultra instead of Noea. Every audit must pass.
# Output: mods/dmzultimatekey-$VERSION.jar
#   DMZULTRA_JAR=<path to dmzultra-0.4.x.jar>  (compile and audit input only; never bundled)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
VERSION="0.3.1"
DMZ_RANGE="[2.2,2.3)"
ULTRA_RANGE="[0.4.0,)"
BASE="$HERE/base/dmzultimatekey-0.2.20.jar"
BASE_SHA="32466987ba25b35ef3c26154e3b3a34660308f38f3b012a9e947835f44c1beae"
DMZ_JAR="${DMZ_JAR:-$ROOT/mods/dragonminez-2.2.jar}"
ULTRA_JAR="${DMZULTRA_JAR:-}"
OUT="$ROOT/mods/dmzultimatekey-$VERSION.jar"
BUILD="$HERE/build"
LIB="$ROOT/libraries"
PY="${PYTHON:-python}"
export PYTHONUTF8=1

# javac/java on Windows want ';' and drive paths; elsewhere ':'.
SEP=":"
winpath() { printf '%s' "$1"; }
if command -v cygpath > /dev/null 2>&1; then
    SEP=";"
    winpath() { cygpath -m "$1"; }
fi
cp_join() { local out="" p; for p in "$@"; do out="${out:+$out$SEP}$(winpath "$p")"; done; printf '%s' "$out"; }

if [ -z "$ULTRA_JAR" ] || [ ! -f "$ULTRA_JAR" ]; then
    echo "ERROR: set DMZULTRA_JAR to the dmzultra 0.4.x jar" >&2
    exit 1
fi
ultra_version="$(unzip -p "$ULTRA_JAR" META-INF/mods.toml | grep -m1 '^version=' | cut -d'"' -f2)"
case "$ultra_version" in
    0.4.*) ;;
    *) echo "ERROR: DMZULTRA_JAR is dmzultra $ultra_version, want 0.4.x" >&2; exit 1 ;;
esac

echo "== base jar"
got="$(sha256sum "$BASE" | cut -d' ' -f1)"
[ "$got" = "$BASE_SHA" ] || { echo "ERROR: base jar hash $got, want $BASE_SHA" >&2; exit 1; }

rm -rf "$BUILD"
rm -f "$ROOT"/mods/dmzultimatekey-*.jar
mkdir -p "$BUILD/patcher" "$BUILD/classes"

MC_SRG="$LIB/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
FORGE="$LIB/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar"
EVENTBUS="$LIB/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar"
ASM_CP="$(cp_join "$LIB/org/ow2/asm/asm/9.6/asm-9.6.jar" "$LIB/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar" \
    "$LIB/org/ow2/asm/asm-commons/9.6/asm-commons-9.6.jar")"

echo "== patcher"
javac -nowarn -d "$(winpath "$BUILD/patcher")" -cp "$ASM_CP" \
    "$(winpath "$HERE/patcher/AtWiden.java")" \
    "$(winpath "$HERE/patcher/BytecodePatches.java")" \
    "$(winpath "$HERE/patcher/NoeaToUltra.java")"
PATCH_CP="$(winpath "$BUILD/patcher")$SEP$ASM_CP"
# Forge makes some vanilla members public at runtime (e.g. ServerGamePacketListenerImpl.f_9742_);
# javac needs the same view. The widened jar is a compile input only.
java -cp "$PATCH_CP" AtWiden "$(winpath "$MC_SRG")" "$(winpath "$FORGE")" "$(winpath "$BUILD/mc-at.jar")"

COMPILE_CP="$(cp_join \
    "$BUILD/mc-at.jar" "$FORGE" \
    "$LIB/net/minecraftforge/fmlcore/1.20.1-47.4.10/fmlcore-1.20.1-47.4.10.jar" \
    "$LIB/net/minecraftforge/fmlloader/1.20.1-47.4.10/fmlloader-1.20.1-47.4.10.jar" \
    "$LIB/net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar" \
    "$LIB/net/minecraftforge/javafmllanguage/1.20.1-47.4.10/javafmllanguage-1.20.1-47.4.10.jar" \
    "$EVENTBUS" \
    "$LIB/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar" \
    "$LIB/org/ow2/asm/asm/9.6/asm-9.6.jar" "$LIB/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar" \
    "$LIB/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar" \
    "$LIB/com/mojang/authlib/4.0.43/authlib-4.0.43.jar" \
    "$LIB/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar" \
    "$LIB/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar" \
    "$LIB/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" \
    "$LIB/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar" \
    "$LIB/io/netty/netty-common/4.1.82.Final/netty-common-4.1.82.Final.jar" \
    "$LIB/io/netty/netty-buffer/4.1.82.Final/netty-buffer-4.1.82.Final.jar" \
    "$LIB/io/netty/netty-transport/4.1.82.Final/netty-transport-4.1.82.Final.jar" \
    "$DMZ_JAR" "$ROOT/mods/geckolib-forge-1.20.1-4.8.4.jar" "$ULTRA_JAR" \
    "$BASE")"

echo "== compile src/ against $(basename "$DMZ_JAR") and $(basename "$ULTRA_JAR")"
mapfile -t SOURCES < <(find "$HERE/src" -name '*.java' | sort)
WIN_SOURCES=()
for s in "${SOURCES[@]}"; do WIN_SOURCES+=("$(winpath "$s")"); done
javac -nowarn -proc:none --release 17 -encoding UTF-8 -cp "$COMPILE_CP" -d "$(winpath "$BUILD/classes")" "${WIN_SOURCES[@]}"

echo "== bytecode patches (client classes)"
(cd "$BUILD/classes" && unzip -q -o "$(winpath "$BASE")" 'com/dmzlegacy/ultimatekey/client/editor/PreviewModel*.class')
java -cp "$PATCH_CP" BytecodePatches "$(winpath "$BUILD/classes")"

echo "== decompile fidelity (only the port's own methods may differ from 0.2.20)"
"$PY" "$HERE/compare_bytecode.py" "$BASE" "$BUILD/classes" --expect "$HERE/port-changes.txt" \
    --new-prefix com/dmzlegacy/ultimatekey/empires/ultra/

echo "== pack"
"$PY" "$HERE/pack.py" "$BASE" "$BUILD/classes" "$BUILD/packed.jar" "$VERSION" "$DMZ_RANGE" "$ULTRA_RANGE"

echo "== empires layer: Noea -> DMZ Ultra"
java -cp "$PATCH_CP" NoeaToUltra "$(winpath "$BUILD/packed.jar")" "$(winpath "$OUT")" "$(winpath "$HERE/noea-to-ultra.txt")"

echo "== API audit: every DragonMineZ reference resolves in $(basename "$DMZ_JAR")"
"$PY" "$HERE/audit_api_refs.py" "$OUT" --api "$DMZ_JAR" \
    --lib "$MC_SRG" --lib "$FORGE" --lib "$EVENTBUS" \
    --prefix com/dragonminez/ --allow "$HERE/api-allow.txt"

echo "== API audit: every DMZ Ultra reference resolves in $(basename "$ULTRA_JAR")"
"$PY" "$HERE/audit_api_refs.py" "$OUT" --api "$ULTRA_JAR" --api "$DMZ_JAR" \
    --lib "$MC_SRG" --lib "$FORGE" --lib "$EVENTBUS" \
    --prefix com/dmzultra/

echo "== API audit: every call into DUK's Ultra facades resolves (the Noea shapes the empires code uses)"
"$PY" "$HERE/audit_api_refs.py" "$OUT" --api "$OUT" --api "$ULTRA_JAR" \
    --lib "$MC_SRG" --lib "$FORGE" --lib "$EVENTBUS" \
    --prefix com/dmzlegacy/ultimatekey/empires/ultra/

echo "== jar metadata, mixin targets, no Noea"
"$PY" "$HERE/audit_duk_jar.py" "$OUT" "$VERSION" "$DMZ_RANGE" "$DMZ_JAR" "$ULTRA_RANGE" "$ULTRA_JAR"

echo "Built $OUT"
