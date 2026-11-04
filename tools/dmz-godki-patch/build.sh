#!/usr/bin/env bash
# Build dragonminez-<PATCH_VERSION>.jar from the stock DMZ 2.2.1 jar (see README.md).
#   DMZ_JAR=/path/to/dragonminez-2.2.1.jar [PATCH_VERSION=2.2.1.N] bash tools/dmz-godki-patch/build.sh
# PATCH_VERSION is stamped into META-INF/mods.toml; bump the default for each new patch build.
# Output: tools/dmz-godki-patch/out/dragonminez-<PATCH_VERSION>.jar (gitignored).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
L="$ROOT/libraries"
DMZ="${DMZ_JAR:?set DMZ_JAR to the stock dragonminez-2.2.1.jar}"
STOCK_SHA="039ce26942145cfa3c9d5619b7967b08d5b7ed3316157f88dc383f0b89568181"
WORK="$HERE/work"
OUT="$HERE/out"
PATCH_VERSION="${PATCH_VERSION:-2.2.1.1}"
JAR_OUT="$OUT/dragonminez-$PATCH_VERSION.jar"
SD=com/dragonminez/common/stats/StatsData.class
SH=com/dragonminez/server/events/players/actionmode/StackFormModeHandler.class
FH=com/dragonminez/server/events/players/actionmode/FormModeHandler.class
EA=com/dragonminez/common/network/C2S/ExecuteActionC2S.class
export PYTHONUTF8=1

win() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }
SEP=":"; command -v cygpath > /dev/null && SEP=";"

have_sha="$(sha256sum "$DMZ" | cut -c1-64)"
if [[ "$have_sha" != "$STOCK_SHA" ]]; then
  echo "DMZ_JAR is not the stock 2.2.1 jar (sha256 $have_sha)." >&2
  exit 1
fi

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK/tools" "$WORK/cls" "$OUT"
fetch() {  # fetch <maven path> <file>
  curl -sfL "https://repo1.maven.org/maven2/$1/$2" -o "$WORK/tools/$2"
  want="$(curl -sfL "https://repo1.maven.org/maven2/$1/$2.sha1" | cut -c1-40)"
  [[ "$(sha1sum "$WORK/tools/$2" | cut -c1-40)" == "$want" ]] || { echo "sha1 mismatch: $2" >&2; exit 1; }
}
fetch org/vineflower/vineflower/1.10.1 vineflower-1.10.1.jar
fetch org/projectlombok/lombok/1.18.30 lombok-1.18.30.jar

FORGE_S="$L/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-server.jar"
SRG="$L/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
FORGE_U="$L/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar"
# Forge's patched classes first so Forge-added methods (getPersistentData) resolve.
CP_LIST=(
  "$FORGE_S" "$SRG" "$FORGE_U"
  "$L/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
  "$L/net/minecraftforge/fmlcore/1.20.1-47.4.10/fmlcore-1.20.1-47.4.10.jar"
  "$L/net/minecraftforge/fmlloader/1.20.1-47.4.10/fmlloader-1.20.1-47.4.10.jar"
  "$L/net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar"
  "$L/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar"
  "$L/net/minecraftforge/mergetool/1.1.5/mergetool-1.1.5-api.jar"
  "$L/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar"
  "$L/com/mojang/authlib/4.0.43/authlib-4.0.43.jar"
  "$L/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar"
  "$L/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar"
  "$L/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar"
  "$(ls "$L"/com/mojang/datafixerupper/*/*.jar | head -1)"
  "$(ls "$L"/it/unimi/dsi/fastutil/*/*.jar | head -1)"
  "$(ls "$L"/io/netty/netty-buffer/*/*.jar | head -1)"
  "$(ls "$L"/io/netty/netty-common/*/*.jar | head -1)"
  "$WORK/tools/lombok-1.18.30.jar"
  "$DMZ"
)
CP=""
for p in "${CP_LIST[@]}"; do CP="${CP:+$CP$SEP}$(win "$p")"; done
ASM_CP="$(win "$L/org/ow2/asm/asm/9.6/asm-9.6.jar")$SEP$(win "$L/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar")"

echo ">> decompile StatsData + StackFormModeHandler + FormModeHandler + ExecuteActionC2S"
(cd "$WORK/cls" && unzip -q -o "$DMZ" "$SD" 'com/dragonminez/common/stats/StatsData$1.class' "$SH" "$FH" "$EA" \
  'com/dragonminez/common/network/C2S/ExecuteActionC2S$1.class' 'com/dragonminez/common/network/C2S/ExecuteActionC2S$ActionType.class')
java -jar "$(win "$WORK/tools/vineflower-1.10.1.jar")" -e="$(win "$DMZ")" -e="$(win "$SRG")" -e="$(win "$FORGE_U")" \
  "$(win "$WORK/cls")" "$(win "$WORK/src")" > "$WORK/vineflower.log" 2>&1
python3 "$HERE/apply_patch.py" "$WORK/src" --fixup-only
cp -r "$WORK/src" "$WORK/src0"

echo ">> round-trip check (unpatched recompile; javac re-encoding only, not shipped)"
mkdir -p "$WORK/out0"
javac -nowarn -proc:none --release 17 -g -cp "$CP" -d "$(win "$WORK/out0")" $(find "$WORK/src0" -name '*.java')

echo ">> patch + compile"
python3 "$HERE/apply_patch.py" "$WORK/src"
(cd "$WORK" && diff -ru src0 src > "$OUT/godki-patch.diff") || true
mkdir -p "$WORK/out1"
javac -nowarn -proc:none --release 17 -g -cp "$CP" -d "$(win "$WORK/out1")" $(find "$WORK/src" -name '*.java')

echo ">> transplant patched methods into the stock class bytes"
mkdir -p "$WORK/out2/$(dirname "$SD")" "$WORK/out2/$(dirname "$SH")" "$WORK/out2/$(dirname "$EA")"
java -cp "$ASM_CP" "$(win "$HERE/Transplant.java")" "$(win "$WORK/cls/$FH")" "$(win "$WORK/out1/$FH")" "$(win "$WORK/out2/$FH")"   canCharge,attemptTransform
java -cp "$ASM_CP" "$(win "$HERE/Transplant.java")" "$(win "$WORK/cls/$SD")" "$(win "$WORK/out1/$SD")" "$(win "$WORK/out2/$SD")" \
  getTotalMultiplier,getFormMultiplier,getStackFormMultiplier
java -cp "$ASM_CP" "$(win "$HERE/Transplant.java")" "$(win "$WORK/cls/$SH")" "$(win "$WORK/out1/$SH")" "$(win "$WORK/out2/$SH")" \
  canCharge,attemptTransform
java -cp "$ASM_CP" "$(win "$HERE/Transplant.java")" "$(win "$WORK/cls/$EA")" "$(win "$WORK/out1/$EA")" "$(win "$WORK/out2/$EA")" \
  instantTransformForm,instantTransformStackForm

echo ">> fidelity: only the patched / added methods may differ from stock"
python3 "$HERE/fidelity.py" "$WORK/cls" "$WORK/out2" "$SD" "$SH" "$FH" "$EA" | tee "$OUT/fidelity.txt"
python3 - "$OUT/fidelity.txt" <<'EOF'
import re, sys
allowed = {"getTotalMultiplier", "getFormMultiplier", "getStackFormMultiplier", "canCharge", "attemptTransform",
           "isGodKiStackForm", "isGodKiCopyActive", "isDivineKiActive", "isDivineKiForm", "isGodFormGroup",
           "isInGodForm", "canActivateDivineKi", "getDivineKiFactor", "getGodKiCopyMultiplier",
           "isGodKiForm", "isInBaseForm", "canActivateGodKi", "hasGodKiCopy", "findGodKiCopySource", "isKaiPathDivineGroup",
           "instantTransformForm", "instantTransformStackForm"}
bad = [l for l in open(sys.argv[1], encoding="utf-8") if l.strip().startswith("DIFF")
       and re.search(r"(\w+)\(", l.split("DIFF", 1)[1]).group(1) not in allowed]
if bad:
    sys.exit("unexpected method changes:\n" + "".join(bad))
print("fidelity gate OK: only the patched / added methods differ")
EOF

echo ">> repack"
python3 "$HERE/repack.py" "$DMZ" "$JAR_OUT" "$WORK/out2" "$PATCH_VERSION" "$SD" "$SH" "$FH" "$EA"
sha256sum "$JAR_OUT" | tee "$OUT/sha256.txt"
echo "Built $JAR_OUT"
