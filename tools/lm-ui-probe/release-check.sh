#!/usr/bin/env bash
# One command for a release's proof (test only; never touches the live server):
#   1. build the LM jar (every build.sh audit),
#   2. optional mock set on the real client (--mock nav|dojo|hud),
#   3. the full matrix (full and reject stages, scripted checks, report.py; 4.8.2: no KaiPath bridge stage, the
#      reject stage proves a KaiPath-only client is turned away),
#   4. SUMMARY.md in $RIG_OUT: version, jar sha256, scripted PASS/FAIL totals with every failing line, the matrix
#      rows, the navigation audit and the HUD checks.
# Exit 0 only when the build passes, no scripted check fails and no matrix row fails.
#
#   bash tools/lm-ui-probe/release-check.sh                 # build + matrix
#   bash tools/lm-ui-probe/release-check.sh --mock hud      # build + hud mock + matrix
#   bash tools/lm-ui-probe/release-check.sh --scripted-only # build + scripted pages only (faster)
#   bash tools/lm-ui-probe/release-check.sh --no-build --mock nav --no-matrix
#
# Paths come from $RIG_ENV (default tools/lm-ui-probe/rig.env, gitignored; copy rig.env.example) and the usual
# build variables (DMZ_JAR, DMZULTRA_JAR, DMZULTRA_SLOTS_JAR; defaults below).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../.." && pwd)"
RIG_ENV="${RIG_ENV:-$HERE/rig.env}"
[ -f "$RIG_ENV" ] && . "$RIG_ENV"
: "${RIG_SERVER:?set RIG_SERVER (see tools/lm-ui-probe/rig.env.example)}"

MOCK=""; MATRIX=1; BUILD=1; SCRIPTED=0
while [ $# -gt 0 ]; do
  case "$1" in
    --mock) MOCK="$2"; shift ;;
    --no-matrix) MATRIX=0 ;;
    --no-build) BUILD=0 ;;
    --scripted-only) SCRIPTED=1 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done

V="$(grep -oP 'public static final String VERSION = "\K[0-9.]+(?=")' \
  "$R/tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java")"
JAR="$R/mods/LegacyMechanics-$V.jar"
OUT="${RIG_OUT:-$HERE/out}/release-$V"
mkdir -p "$OUT"
SUM="$OUT/SUMMARY.md"
log() { echo "$*"; echo "$*" >> "$OUT/release.log"; }
: > "$OUT/release.log"

cd "$R"
export PYTHONUTF8=1
export DMZ_JAR="${DMZ_JAR:-$R/tools/dmz-godki-patch/out/dragonminez-2.2.1.2.jar}"
export DMZULTRA_SLOTS_JAR="${DMZULTRA_SLOTS_JAR:-$R/mods/dmzultra-0.4.0-noscale.jar}"
if [ $BUILD = 1 ]; then
  : "${DMZULTRA_JAR:?set DMZULTRA_JAR (the DMZ Ultra 0.2.9 jar the build compiles against)}"
  log "== build $V $(date +%T)"
  if ! bash tools/dmz-adaptive-difficulty/build.sh </dev/null > "$OUT/build.log" 2>&1; then
    log "BUILD FAIL (see $OUT/build.log)"
    grep -a -E "error:|FAIL|ERROR" "$OUT/build.log" | head -20 | tee -a "$OUT/release.log"
    exit 1
  fi
  log "build OK"
fi
[ -f "$JAR" ] || { log "no jar $JAR"; exit 1; }
SHA="$(sha256sum "$JAR" | cut -d' ' -f1)"
# The rig reads the jar from a copy so a rebuild during a run cannot change it underneath.
SNAP="$OUT/LegacyMechanics-$V.jar"
cp "$JAR" "$SNAP"
bash "$HERE/stop.sh" >/dev/null 2>&1 || true

if [ -n "$MOCK" ]; then
  log "== mock $MOCK $(date +%T)"
  LM_JAR="$SNAP" bash "$HERE/build.sh" >> "$OUT/release.log" 2>&1 || { log "probe build failed"; exit 1; }
  RIG_OUT="$OUT/mock-$MOCK" SRVJVM="-Duiprobe.mock=true -Duiprobe.mockset=$MOCK" \
    bash "$HERE/run.sh" mock "$SNAP" 1 "$SNAP" 1 > "$OUT/mock.out" 2>&1
  log "mock shots: $(ls "$OUT/mock-$MOCK/mock" 2>/dev/null | grep -c png) in $OUT/mock-$MOCK/mock"
fi

if [ $MATRIX = 1 ]; then
  log "== matrix $(date +%T)"
  if [ $SCRIPTED = 1 ]; then
    RIG_OUT="$OUT/matrix" ONLY_SCRIPTED=1 SKIP="reject" bash "$HERE/matrix.sh" "$SNAP" > "$OUT/matrix.log" 2>&1
  else
    RIG_OUT="$OUT/matrix" bash "$HERE/matrix.sh" "$SNAP" > "$OUT/matrix.log" 2>&1
  fi
  log "matrix done $(date +%T)"
  # The kit corpus JLDK copies from comes from LM's real wire. 4.8.2: the KaiPath stage is gone, so it comes from the
  # full stage's crawl (a full run only; a scripted-only run does not crawl every menu).
  if [ $SCRIPTED = 0 ] && [ -d "$OUT/matrix/full" ]; then
    python tools/ultra-gui-kit/test/gen_lm_corpus.py "$OUT/matrix/full" tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl       >> "$OUT/release.log" 2>&1 && log "kit corpus refreshed: $(git diff --stat -- tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl | tail -1)"
  fi
fi

# ---- summary
S="$OUT/matrix/full/uiprobe-script.txt"
P=0; F=0
[ -f "$S" ] && P=$(grep -a -c PASS "$S") && F=$(grep -a -c FAIL "$S")
ROWFAIL=$(grep -c -E '^FAIL ' "$OUT/matrix.log" 2>/dev/null || true)
{
  echo "# LM $V release check"
  echo
  echo "- Jar: \`LegacyMechanics-$V.jar\` sha256 \`$SHA\`"
  echo "- Build: $([ $BUILD = 1 ] && echo "OK (every build.sh audit)" || echo "skipped (--no-build)")"
  [ -n "$MOCK" ] && echo "- Mock \`$MOCK\`: $(ls "$OUT/mock-$MOCK/mock" 2>/dev/null | grep -c png) shots in \`mock-$MOCK/mock/\`"
  if [ $MATRIX = 1 ]; then
    echo "- Scripted checks (full stage): **$P PASS, $F FAIL**"
    echo "- Matrix rows failing: **${ROWFAIL:-0}**"
    echo "- Kit corpus: $(git diff --stat -- tools/ultra-gui-kit/test/fixtures/lm-specs.jsonl | tail -1 | sed 's/^ *//') (commit it with the release)"
    echo
    echo "## Matrix rows"
    echo
    grep -E '^(PASS|FAIL|SKIP) ' "$OUT/matrix.log" | cut -c1-260 | sed 's/^/- /'
    echo
    echo "## Failing scripted checks"
    echo
    grep -a FAIL "$S" 2>/dev/null | grep -v '^PACKET' | cut -c1-300 | sed 's/^/- /' || true
    echo
    echo "## Navigation audit and HUD"
    echo
    grep -a -E 'NAV AUDIT|^HUD' "$S" 2>/dev/null | cut -c1-300 | sed 's/^/- /' || true
  fi
} > "$SUM"
cat "$SUM"
[ "$F" = 0 ] && [ "${ROWFAIL:-0}" = 0 ]
