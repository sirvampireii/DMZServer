#!/usr/bin/env bash
# Build dmzultra-<version>-noscale.jar from a stock DMZ Ultra jar (see NoScalePatch.java).
#   bash tools/dmzultra-noscale/build.sh <stock dmzultra-0.4.0.jar | dmzultra-0.5.3.jar | dmzultra-0.5.5.jar> [out dir]
# The version comes from the stock jar's sha256; any other jar is refused.
# Output: <out dir>/dmzultra-<version>-noscale.jar (default out dir: mods/). The stock jar is only read.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
STOCK="${1:?usage: build.sh <stock dmzultra-<version>.jar> [out dir]}"
OUT_DIR="${2:-$ROOT/mods}"
# 0.4.0: FormsChecks.overhaulScale is rewritten to log that the scale is off ("log").
STOCK_SHA="ea63963c59e55ad6cab8ff28967380432c8ee94d7b95a6ee2d58c9ae1b9feacf"
# 0.5.3 (CurseForge file 9537895): FormsChecks stays as shipped ("keep"); it never runs on a production server.
STOCK_SHA_053="0a84d1729e13cec17a9b4bc30bd50ca65b923253a67ec37c2862569b884f8afa"
# 0.5.5 (CurseForge file 9549358, Main File, uploaded 2026-11-10): same as 0.5.3 ("keep") —
# FormsChecks/UltraChecks gating verified identical to 0.5.3 (dev self-test only, FMLEnvironment.production).
STOCK_SHA_055="3cd0ec76cf3627ca0316dec12113b00e6f2c5611768520457c115ebc1ed16007"
ASM="$ROOT/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar"
ASM_TREE="$ROOT/libraries/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar"

got="$(sha256sum "$STOCK" | cut -d' ' -f1)"
case "$got" in
  "$STOCK_SHA") VERSION="0.4.0"; CHECKS="log" ;;
  "$STOCK_SHA_053") VERSION="0.5.3"; CHECKS="keep" ;;
  "$STOCK_SHA_055") VERSION="0.5.5"; CHECKS="keep" ;;
  *)
    echo "ERROR: $STOCK is not a known stock DMZ Ultra jar (sha256 $got; want 0.4.0 $STOCK_SHA, 0.5.3 $STOCK_SHA_053 or 0.5.5 $STOCK_SHA_055)" >&2
    exit 1 ;;
esac
OUT="$OUT_DIR/dmzultra-$VERSION-noscale.jar"
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
java -cp "$CLASSES$SEP$CP" NoScalePatch "$STOCK" "$OUT" "$VERSION" "$CHECKS"
python "$HERE/verify.py" "$STOCK" "$OUT" "$CHECKS"
sha256sum "$OUT"
