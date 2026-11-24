#!/usr/bin/env bash
# LM 4.7.68 dojo halls: recipes -> structure files -> checks -> pictures, all with the mod-toolkit
# (Desktop/Repository/mod-toolkit, see tools/toolkit.json). Run from anywhere:
#   bash tools/dmz-adaptive-difficulty/tools/dojo/make.sh            everything
#   bash tools/dmz-adaptive-difficulty/tools/dojo/make.sh ki_t2      one style / tier (no contact sheets)
# 1. BuildKit builds each tier's hall shell from tools/dojo/hall_t<n>.json (japanese style, BuildLint on the shell).
# 2. DojoKit (tools/fx) puts the interior, banners and grounds in, per style and tier, from the recipe's "dojo" section,
#    writes the plot structures and renders the pictures with BlockRender.
# 3. BuildLint checks every structure that ships (the 12 plots; the bare shells are build inputs only).
# 4. The structures are copied into the mod (data/legacymechanics/structures/dojo), the pictures into docs/dojo-visuals.
# 5. The menu mockups come from the real LM client, not from here: a mock rig run (tools/lm-ui-probe, see its README,
#    "Menu mockups") shoots the proposed menus in the current look and mock_compose.py adds the map from step 2.
set -euo pipefail
LM="$(cd "$(dirname "$0")/../.." && pwd)"
REPO="$(cd "$LM/../.." && pwd)"
cd "$LM"
run() { powershell -NoProfile -ExecutionPolicy Bypass -File tools/fx/run.ps1 "$@"; }
OUT=build/dojo
RES=src/main/resources/data/legacymechanics/structures/dojo
DOCS="$REPO/docs/dojo-visuals"
mkdir -p "$OUT/nbt" "$OUT/pics" "$RES" "$DOCS"

for t in 1 2 3; do
  run BuildKit build "tools/dojo/hall_t$t.json" "$OUT/hall_t$t.nbt" -preview "$OUT/hall_t$t.png"
done
ONLY=()
[ $# -gt 0 ] && ONLY=(-only "$1")
run DojoKit make "$OUT/hall_t1.nbt,$OUT/hall_t2.nbt,$OUT/hall_t3.nbt" tools/dojo/hall_t2.json "$OUT/nbt" "$OUT/pics" "${ONLY[@]}"

: > "$OUT/lint.txt"
for f in "$OUT"/nbt/dojo_*.nbt; do
  n=$(basename "$f" .nbt)
  echo "== $n" >> "$OUT/lint.txt"
  run BuildLint "$f" -out "$OUT/lint_$n.png" >> "$OUT/lint.txt" 2>&1 || echo "   (exit $?)" >> "$OUT/lint.txt"
done

cp "$OUT"/nbt/dojo_*.nbt "$RES/"
rm -f "$DOCS"/*.jpg
cp "$OUT"/pics/menu_plan_*.png "$DOCS/"
# the renders are photo-like: JPEG keeps the repository small (the menu map stays PNG: GuiKit draws it as a texture)
python -I -c "import sys,glob,os
from PIL import Image
for f in glob.glob(os.path.join(sys.argv[1],'*.png')):
    if os.path.basename(f).startswith('menu_plan_'): continue
    Image.open(f).convert('RGB').save(os.path.join(sys.argv[2], os.path.basename(f)[:-4]+'.jpg'), quality=88)" "$OUT/pics" "$DOCS"
cp "$OUT/lint.txt" "$DOCS/buildlint.txt"
echo "done: structures in $RES, pictures in $DOCS, lint in $OUT/lint.txt"
