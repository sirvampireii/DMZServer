#!/usr/bin/env bash
# Build dmzultra-0.5.7-noscale-placefix.jar from the stock DMZ Ultra 0.5.7 jar (see PlaceFixPatch.java).
#   bash tools/dmzultra-placefix/build.sh <stock dmzultra-0.5.7.jar> [out dir]
# Step 1 is the noscale build (tools/dmzultra-noscale/build.sh, which pins the stock sha256 and refuses any
# other jar); step 2 adds the place fix on top. Output: <out dir>/dmzultra-0.5.7-noscale-placefix.jar
# (default out dir: mods/). The stock jar is only read. The mod version inside stays 0.5.7.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STOCK="${1:?usage: build.sh <stock dmzultra-0.5.7.jar> [out dir]}"
OUT_DIR="${2:-$ROOT/mods}"
STOCK_SHA_057="ca3cbbf1f1e97ac36fe5fbaa77374b3d41868cb93f304a6317bd67259960992a"
got="$(sha256sum "$STOCK" | cut -d' ' -f1)"
got="${got#\\}"  # Git Bash escapes the hash when the path has a backslash
if [[ "$got" != "$STOCK_SHA_057" ]]; then
  echo "ERROR: $STOCK is not the stock DMZ Ultra 0.5.7 jar (sha256 $got; want $STOCK_SHA_057)" >&2
  exit 1
fi
ASM="$ROOT/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar"
ASM_TREE="$ROOT/libraries/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"
SRG="$ROOT/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
FORGE_U="$ROOT/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar"
DFU="$ROOT/libraries/com/mojang/datafixerupper/6.0.8/datafixerupper-6.0.8.jar"
for f in "$ASM" "$ASM_TREE" "$SRG" "$FORGE_U" "$DFU"; do
  [[ -f "$f" ]] || { echo "ERROR: missing $f" >&2; exit 1; }
done
OUT="$OUT_DIR/dmzultra-0.5.7-noscale-placefix.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/noscale" "$WORK/patch" "$WORK/fix"

bash "$ROOT/tools/dmzultra-noscale/build.sh" "$STOCK" "$WORK/noscale" >/dev/null
NOSCALE="$WORK/noscale/dmzultra-0.5.7-noscale.jar"

SEP=":"
w() { echo "$1"; }
if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
  SEP=";"
  w() { cygpath -w "$1"; }
fi
javac --release 17 -encoding UTF-8 -nowarn -proc:none \
  -cp "$(w "$SRG")$SEP$(w "$FORGE_U")$SEP$(w "$DFU")" -d "$(w "$WORK/fix")" "$(w "$HERE/PlaceGroundFix.java")"
javac --release 17 -encoding UTF-8 -cp "$(w "$ASM")$SEP$(w "$ASM_TREE")" -d "$(w "$WORK/patch")" "$(w "$HERE/PlaceFixPatch.java")"
mkdir -p "$OUT_DIR"
java -cp "$(w "$WORK/patch")$SEP$(w "$ASM")$SEP$(w "$ASM_TREE")" PlaceFixPatch \
  "$(w "$NOSCALE")" "$(w "$WORK/fix/com/dmzultra/r3/bleachworld/places/PlaceGroundFix.class")" "$(w "$OUT")"
python "$HERE/verify.py" "$NOSCALE" "$OUT"
out_sha="$(sha256sum "$OUT" | cut -d' ' -f1)"
out_sha="${out_sha#\\}"
echo "$out_sha  $OUT"
