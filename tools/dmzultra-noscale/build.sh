#!/usr/bin/env bash
# Build dmzultra-0.4.0-noscale.jar from the stock DMZ Ultra 0.4.0 jar (see NoScalePatch.java).
#   bash tools/dmzultra-noscale/build.sh <stock dmzultra-0.4.0.jar> [out dir]
# Output: <out dir>/dmzultra-0.4.0-noscale.jar (default out dir: mods/). The stock jar is only read.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STOCK="${1:?usage: build.sh <stock dmzultra-0.4.0.jar> [out dir]}"
OUT_DIR="${2:-$ROOT/mods}"
STOCK_SHA="ea63963c59e55ad6cab8ff28967380432c8ee94d7b95a6ee2d58c9ae1b9feacf"
ASM="$ROOT/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar"
ASM_TREE="$ROOT/libraries/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"
OUT="$OUT_DIR/dmzultra-0.4.0-noscale.jar"

got="$(sha256sum "$STOCK" | cut -d' ' -f1)"
if [[ "$got" != "$STOCK_SHA" ]]; then
  echo "ERROR: $STOCK is not the stock DMZ Ultra 0.4.0 jar (sha256 $got, want $STOCK_SHA)" >&2
  exit 1
fi
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
SEP=":"
[[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]] && SEP=";"
CP="$ASM$SEP$ASM_TREE"
CLASSES="$WORK"
if [[ "$SEP" == ";" ]]; then
  CP="$(cygpath -w "$ASM");$(cygpath -w "$ASM_TREE")"
  CLASSES="$(cygpath -w "$WORK")"
fi
javac --release 17 -encoding UTF-8 -cp "$CP" -d "$CLASSES" "$HERE/NoScalePatch.java"
mkdir -p "$OUT_DIR"
java -cp "$CLASSES$SEP$CP" NoScalePatch "$STOCK" "$OUT"
python "$HERE/verify.py" "$STOCK" "$OUT"
sha256sum "$OUT"
