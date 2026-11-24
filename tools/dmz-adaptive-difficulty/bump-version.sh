#!/usr/bin/env bash
# Set the LM version everywhere it lives, in one step (CLAUDE.md: identical in AdaptiveDifficultyMod.VERSION,
# mods.toml, MANIFEST.MF and plugin.yml). Refuses a version that is not higher than the current one, so a
# number is never reused, and refuses retired numbers listed in RETIRED below or in docs/LM_*_BUILD_NOTES.md.
#   bash tools/dmz-adaptive-difficulty/bump-version.sh 4.7.73
# After the next build, run rebaseline-version-class.sh (the reference copy of AdaptiveDifficultyMod.class
# carries the version string).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$(cd "$HERE/../.." && pwd)"
NEW="${1:?usage: bump-version.sh <new version, e.g. 4.7.73>}"
[[ "$NEW" =~ ^[0-9]+(\.[0-9]+)+$ ]] || { echo "not a version: $NEW" >&2; exit 1; }
# Numbers skipped on purpose and never to be used.
RETIRED="4.7.69 4.7.70"

MOD="$HERE/src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java"
TOML="$HERE/src/main/resources/META-INF/mods.toml"
MF="$HERE/src/main/resources/META-INF/MANIFEST.MF"
YML="$R/tools/dmz-adaptive-difficulty-gui/src/main/resources/plugin.yml"

CUR="$(grep -oP 'public static final String VERSION = "\K[0-9.]+(?=")' "$MOD")"
newer() { [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" = "$1" ] && [ "$1" != "$2" ]; }
newer "$NEW" "$CUR" || { echo "refused: $NEW is not higher than the current $CUR (never reuse a version)" >&2; exit 1; }
for r in $RETIRED; do
  [ "$r" = "$NEW" ] && { echo "refused: $NEW is retired" >&2; exit 1; }
done
if grep -lq "retired.*\b${NEW//./\\.}\b\|\b${NEW//./\\.}\b.*retired" "$R"/docs/LM_*_BUILD_NOTES.md 2>/dev/null; then
  echo "refused: build notes list $NEW as retired" >&2; exit 1
fi

sed -i "s/public static final String VERSION = \"$CUR\"/public static final String VERSION = \"$NEW\"/" "$MOD"
sed -i "s/^version = \"$CUR\"/version = \"$NEW\"/" "$TOML"
sed -i "s/^Implementation-Version: $CUR/Implementation-Version: $NEW/" "$MF"
sed -i "s/^version: $CUR/version: $NEW/" "$YML"

bad=0
grep -q "VERSION = \"$NEW\"" "$MOD" || { echo "AdaptiveDifficultyMod.java not updated" >&2; bad=1; }
grep -q "^version = \"$NEW\"" "$TOML" || { echo "mods.toml not updated" >&2; bad=1; }
grep -q "^Implementation-Version: $NEW" "$MF" || { echo "MANIFEST.MF not updated" >&2; bad=1; }
grep -q "^version: $NEW" "$YML" || { echo "plugin.yml not updated" >&2; bad=1; }
[ $bad = 0 ] || exit 1
echo "version $CUR -> $NEW in AdaptiveDifficultyMod.java, mods.toml, MANIFEST.MF, plugin.yml"
echo "next: build, then bash tools/dmz-adaptive-difficulty/rebaseline-version-class.sh"
