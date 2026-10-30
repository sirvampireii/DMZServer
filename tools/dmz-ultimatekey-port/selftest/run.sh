#!/usr/bin/env bash
# Run DUK's empires self-tests (the suite the server runs on start when the empires layer is on)
# on a plain JVM against the built jar. Needs:
#   DMZULTRA_JAR=<dmzultra-0.4.x.jar>  KAIPATH_JAR=<kaipath jar> (both read only)
# FTB Library touches two client texture classes during its static init; selftest/stubs holds
# empty stand-ins for those, for this runner only.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
LIB="$ROOT/libraries"
JAR="$(cd "$(dirname "${1:-$(ls "$ROOT"/mods/dmzultimatekey-*.jar | head -1)}")" && pwd)/$(basename "${1:-$(ls "$ROOT"/mods/dmzultimatekey-*.jar | head -1)}")"
: "${DMZULTRA_JAR:?set DMZULTRA_JAR}" "${KAIPATH_JAR:?set KAIPATH_JAR}"
SEP=":"; wp() { printf '%s' "$1"; }
if command -v cygpath > /dev/null 2>&1; then SEP=";"; wp() { cygpath -m "$1"; }; fi
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$(wp "$OUT")" "$(wp "$HERE/RunCheck.java")" $(find "$HERE/stubs" -name '*.java' | while read -r f; do wp "$f"; printf ' '; done)
CP="$(wp "$OUT")"
for j in "$JAR" "$ROOT/mods/dragonminez-2.2.jar" "$DMZULTRA_JAR" "$KAIPATH_JAR" \
    "$ROOT/mods/CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260227.jar" \
    "$LIB/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar" \
    $(find "$LIB" -name '*.jar' ! -path '*/minecraft/server/*' ! -name 'dmzrevamp*' ! -name 'forge-*-server.jar'); do
    CP="$CP$SEP$(wp "$j")"
done
# The empires config store writes config/noea_empires/ under the working directory: run in $OUT.
cd "$OUT"
if [ $# -gt 1 ]; then java -cp "$CP" RunCheck "${@:2}"; else java -cp "$CP" RunCheck LegacyChecks; fi
