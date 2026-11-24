#!/usr/bin/env bash
# LM 4.7.68 dojo halls: recipes -> structure files -> checks -> pictures, all with the mod-toolkit
# (Desktop/Repository/mod-toolkit, see tools/toolkit.json). Run from anywhere:
#   bash tools/dmz-adaptive-difficulty/tools/dojo/make.sh            everything
#   bash tools/dmz-adaptive-difficulty/tools/dojo/make.sh ki_t2      one style / tier of the pictures (no export)
# 1. BuildKit builds each tier's hall shell from tools/dojo/hall_t<n>.json (japanese style).
# 2. DojoKit (tools/fx) renders the pictures for docs/dojo-visuals: a new dojo, every style at every tier, upgrades.
# 3. DojoKit export writes what LM ships: per hall tier and style the empty hall (base) and a piece per ring, room
#    level, balcony level and garden level, the plot index (anchors and part boxes), and the menu textures (plans and
#    close-ups, quantised to 256 colours). They go into the mod: data/legacymechanics/structures/dojo,
#    data/legacymechanics/dojo/index.json, assets/legacymechanics/textures/gui/dojo.
# 4. BuildLint checks every whole plot the pictures show; its findings go to docs/dojo-visuals/buildlint.txt.
# The menu mockups come from the real LM client (tools/lm-ui-probe, "Menu mockups" in its README).
set -euo pipefail
LM="$(cd "$(dirname "$0")/../.." && pwd)"
REPO="$(cd "$LM/../.." && pwd)"
cd "$LM"
run() { powershell -NoProfile -ExecutionPolicy Bypass -File tools/fx/run.ps1 "$@"; }
OUT=build/dojo
DOCS="$REPO/docs/dojo-visuals"
RES=src/main/resources
mkdir -p "$OUT/nbt" "$OUT/pics" "$DOCS"
HALLS="$OUT/hall_t1.nbt,$OUT/hall_t2.nbt,$OUT/hall_t3.nbt"

for t in 1 2 3; do
  run BuildKit build "tools/dojo/hall_t$t.json" "$OUT/hall_t$t.nbt" -preview "$OUT/hall_t$t.png"
done
ONLY=()
[ $# -gt 0 ] && ONLY=(-only "$1")
run DojoKit make "$HALLS" tools/dojo/hall_t2.json "$OUT/nbt" "$OUT/pics" "${ONLY[@]}"

if [ $# -eq 0 ]; then
  rm -rf "$OUT/export"
  run DojoKit export "$HALLS" tools/dojo/hall_t2.json "$OUT/export/structures" "$OUT/export/textures" "$OUT/export/index.json"
  rm -rf "$RES/data/legacymechanics/structures/dojo" "$RES/assets/legacymechanics/textures/gui/dojo"
  mkdir -p "$RES/data/legacymechanics/structures/dojo" "$RES/data/legacymechanics/dojo" "$RES/assets/legacymechanics/textures/gui/dojo"
  cp -r "$OUT"/export/structures/* "$RES/data/legacymechanics/structures/dojo/"
  cp "$OUT/export/index.json" "$RES/data/legacymechanics/dojo/index.json"
  # menu textures: close-ups at most 260 px, 256 colours with transparency (the jar stays small)
  python -I -c "import sys,glob,os
from PIL import Image
for f in glob.glob(os.path.join(sys.argv[1],'*.png')):
    im=Image.open(f).convert('RGBA')
    if os.path.basename(f).startswith('cu_') and max(im.size)>260:
        k=260/max(im.size); im=im.resize((int(im.width*k),int(im.height*k)),Image.LANCZOS)
    im.quantize(colors=255,method=Image.Quantize.FASTOCTREE).save(os.path.join(sys.argv[2],os.path.basename(f)),optimize=True)" \
    "$OUT/export/textures" "$RES/assets/legacymechanics/textures/gui/dojo"
fi

: > "$OUT/lint.txt"
for f in "$OUT"/nbt/dojo_*.nbt; do
  n=$(basename "$f" .nbt)
  echo "== $n" >> "$OUT/lint.txt"
  run BuildLint "$f" -out "$OUT/lint_$n.png" >> "$OUT/lint.txt" 2>&1 || echo "   (exit $?)" >> "$OUT/lint.txt"
done

rm -f "$DOCS"/*.jpg
# the renders are photo-like: JPEG keeps the repository small
python -I -c "import sys,glob,os
from PIL import Image
for f in glob.glob(os.path.join(sys.argv[1],'*.png')):
    if os.path.basename(f).startswith('menu_plan'): continue
    Image.open(f).convert('RGB').save(os.path.join(sys.argv[2], os.path.basename(f)[:-4]+'.jpg'), quality=88)" "$OUT/pics" "$DOCS"
cp "$OUT/lint.txt" "$DOCS/buildlint.txt"
echo "done: structures, index and textures in $RES; pictures in $DOCS; lint in $OUT/lint.txt"
