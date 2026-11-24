#!/usr/bin/env bash
# Re-extract AdaptiveDifficultyMod.class into the prestige-shop reference set after a version bump.
# That class carries the version string, so every release drifts it by design (reference/README.md); nothing
# else may drift. Refuses when any other reference class differs from the jar.
#   bash tools/dmz-adaptive-difficulty/rebaseline-version-class.sh [path/to/LegacyMechanics-x.jar]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../.." && pwd)"
MOD="$HERE/src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java"
V="$(grep -oP 'public static final String VERSION = "\K[0-9.]+(?=")' "$MOD")"
JAR="${1:-$R/mods/LegacyMechanics-$V.jar}"
[ -f "$JAR" ] || { echo "no jar at $JAR (build first)" >&2; exit 1; }
REF="$HERE/reference/prestige-shop-228.1"
C=com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.class
other=0
while IFS= read -r f; do
  rel="${f#$REF/}"
  [ "$rel" = "$C" ] && continue
  if ! cmp -s <(unzip -p "$JAR" "$rel") "$f"; then
    echo "DRIFT (not the version class): $rel" >&2
    other=1
  fi
done < <(find "$REF" -name '*.class')
[ $other = 0 ] || { echo "refused: other reference classes drifted; that is a real change, not a version bump" >&2; exit 1; }
unzip -p "$JAR" "$C" > "$REF/$C"
grep -aq "$V" "$REF/$C" || { echo "extracted class does not carry $V" >&2; exit 1; }
echo "reference $C re-baselined from $(basename "$JAR") ($V)"
