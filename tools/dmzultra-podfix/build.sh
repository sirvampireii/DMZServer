#!/usr/bin/env bash
# Build dmzultra-0.5.7-noscale-placefix-podfix.jar from the stock DMZ Ultra 0.5.7 jar (see PodFixPatch.java).
#   bash tools/dmzultra-podfix/build.sh <stock dmzultra-0.5.7.jar> [out dir]
# Steps: noscale (tools/dmzultra-noscale), then placefix (tools/dmzultra-placefix), then podfix on top, so the
# one jar live runs carries all three. The stock sha256 is pinned by the earlier steps. Output:
# <out dir>/dmzultra-0.5.7-noscale-placefix-podfix.jar (default out dir: mods/). The mod version stays 0.5.7.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STOCK="${1:?usage: build.sh <stock dmzultra-0.5.7.jar> [out dir]}"
OUT_DIR="${2:-$ROOT/mods}"
ASM="$ROOT/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar"
ASM_TREE="$ROOT/libraries/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"
SRG="$ROOT/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
DFU="$ROOT/libraries/com/mojang/datafixerupper/6.0.8/datafixerupper-6.0.8.jar"
AUTHLIB="$ROOT/libraries/com/mojang/authlib/4.0.43/authlib-4.0.43.jar"
SLF4J="$ROOT/libraries/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar"
for f in "$ASM" "$ASM_TREE" "$SRG" "$DFU" "$AUTHLIB" "$SLF4J"; do
  [[ -f "$f" ]] || { echo "ERROR: missing $f" >&2; exit 1; }
done
OUT="$OUT_DIR/dmzultra-0.5.7-noscale-placefix-podfix.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/placefix" "$WORK/patch" "$WORK/stub"

bash "$ROOT/tools/dmzultra-placefix/build.sh" "$STOCK" "$WORK/placefix" >/dev/null
BASE="$WORK/placefix/dmzultra-0.5.7-noscale-placefix.jar"

SEP=":"
w() { echo "$1"; }
if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
  SEP=";"
  w() { cygpath -w "$1"; }
fi
# The stub compiles against the real jar (DMZUltra.LOGGER) and Minecraft; its own PodVoyage source wins.
javac --release 17 -encoding UTF-8 -nowarn -proc:none -implicit:none \
  -cp "$(w "$BASE")$SEP$(w "$SRG")$SEP$(w "$DFU")$SEP$(w "$AUTHLIB")$SEP$(w "$SLF4J")" \
  -d "$(w "$WORK/stub")" "$(w "$HERE/stub/com/dmzultra/multiverse/PodVoyage.java")"
javac --release 17 -encoding UTF-8 -cp "$(w "$ASM")$SEP$(w "$ASM_TREE")" -d "$(w "$WORK/patch")" "$(w "$HERE/PodFixPatch.java")"
mkdir -p "$OUT_DIR"
java -cp "$(w "$WORK/patch")$SEP$(w "$ASM")$SEP$(w "$ASM_TREE")" PodFixPatch \
  "$(w "$BASE")" "$(w "$WORK/stub/com/dmzultra/multiverse/PodVoyage.class")" "$(w "$OUT")"
python "$HERE/verify.py" "$BASE" "$OUT"
out_sha="$(sha256sum "$OUT" | cut -d' ' -f1)"
out_sha="${out_sha#\\}"
echo "$out_sha  $OUT"
