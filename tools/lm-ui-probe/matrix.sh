#!/usr/bin/env bash
# Full LM menu test matrix in one command (test only; never on the live server).
#   bash tools/lm-ui-probe/matrix.sh <LM jar> [baseline results dir]
#
# 1. builds the probe against the jar,
# 2. full:   LM client + KaiPath, staff, every menu at GUI scale 2/3/4, scripted checks, server class-load log,
# 3. kai:    KaiPath only (bridge regression), first 45 menus,
# 4. reject: requireLmClient on, no LM client: the login must be turned away,
# 5. report.py: REPORT.md (pass/fail per check) and review.html (GUI 3/4 contact sheet) in $RIG_OUT.
# A baseline dir (an older run's full/ results) adds the bridge wire comparison.
# Needs the same environment as run.sh (RIG_SERVER, RIG_CLIENT, RIG_KAIPATH_JAR, RIG_LIVECFG, ...).
# SKIP="kai reject" skips stages; ONLY_SCRIPTED=1 screenshots only the scripted pages in the full stage.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
J="$1"; BASE="${2:-}"
OUT="${RIG_OUT:-$HERE/out}"; mkdir -p "$OUT"
skip() { case " ${SKIP:-} " in *" $1 "*) return 0;; esac; return 1; }
LM_JAR="$J" bash "$HERE/build.sh" || exit 1
echo "== start $(date +%T) $(basename "$J") $(sha256sum "$J" | cut -c1-16)" > "$OUT/matrix.out"
rm -rf "$OUT/full" "$OUT/kai" "$OUT/reject"
EXTRA=""; [ "${ONLY_SCRIPTED:-0}" = 1 ] && EXTRA="-Duiprobe.scriptshots=true"
echo "== full $(date +%T)" >> "$OUT/matrix.out"
SRVJVM="-Xlog:class+load=info:file=classload.log $EXTRA" bash "$HERE/run.sh" full "$J" 1 "$J" 1 > "$OUT/full.out" 2>&1
if ! skip kai; then
  echo "== kai $(date +%T)" >> "$OUT/matrix.out"
  MAXSHOTS=45 bash "$HERE/run.sh" kai "$J" 1 - 1 > "$OUT/kai.out" 2>&1
fi
if ! skip reject; then
  echo "== reject $(date +%T)" >> "$OUT/matrix.out"
  MAXSHOTS=0 REQUIRE=1 REJECT=1 bash "$HERE/run.sh" reject "$J" 1 - 1 > "$OUT/reject.out" 2>&1
fi
echo "== done $(date +%T)" >> "$OUT/matrix.out"
PYTHONUTF8=1 python "$HERE/report.py" "$OUT" ${BASE:+"$BASE"}
