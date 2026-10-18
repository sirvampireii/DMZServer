#!/usr/bin/env bash
# Dev-only smoke: overlay tools/ultra-gui-kit/kaipath-client onto a released KaiPath jar.
# Adds headerBottom/accent/palette/sound/buttonSkin/frame/pagedBook/portraitFrame/fixedFooter
# honor+advertise (and field 11 / textInput if base is older).
# Official KaiPath releases are owned by the KaiPath mod developer — do NOT deploy this
# overlay to live; merge kaipath-client/ into the next official jar instead.
#
# Usage:
#   bash tools/ultra-gui-kit/build-kaipath-overlay.sh
# Optional:
#   KAIPATH_BASE_JAR=mods/kaipath-1.3.13.jar
#   KAIPATH_OVERLAY_VERSION=1.3.13-lm-ultra
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# javac is a Windows binary under Git Bash: it cannot read /c/... MSYS paths.
abs() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}
# Windows javac splits -cp on ';', Linux javac on ':'.
if [[ -n "${WINDIR:-}" ]]; then CP_SEP=';'; else CP_SEP=':'; fi
BASE_JAR="${KAIPATH_BASE_JAR:-$ROOT/mods/kaipath-1.3.13.jar}"
GSON="$ROOT/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
LOG4J="$ROOT/libraries/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar"
MIXIN="$ROOT/libraries/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar"
ASM="$ROOT/libraries/org/ow2/asm/asm/9.8/asm-9.8.jar"
ASM_TREE="$ROOT/libraries/org/ow2/asm/asm-tree/9.8/asm-tree-9.8.jar"
OUT_DIR="$HERE/build"
CLASSES="$OUT_DIR/classes"
STUB_CLASSES="$OUT_DIR/stub-classes"
SRC="$HERE/kaipath-client"
TEST_SRC="$HERE/test"
VERSION="${KAIPATH_OVERLAY_VERSION:-1.3.13-lm-ultra}"
OUT_JAR="$ROOT/mods/kaipath-${VERSION}.jar"

if [[ ! -f "$BASE_JAR" ]]; then
  echo "Missing base KaiPath jar: $BASE_JAR" >&2
  exit 1
fi
if [[ ! -f "$GSON" ]]; then
  echo "Missing gson: $GSON" >&2
  exit 1
fi
if [[ ! -f "$LOG4J" ]]; then
  echo "Missing log4j-api: $LOG4J" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$CLASSES" "$STUB_CLASSES"

python3 "$HERE/stubs/gen_stubs.py"
# Prefer real log4j; drop stub log4j sources if present
find "$HERE/stubs" -path '*/org/apache/logging/*' -delete 2>/dev/null || true
find "$(abs "$HERE/stubs")" -name '*.java' > "$OUT_DIR/stubs.list"
javac --release 17 -encoding UTF-8 -d "$STUB_CLASSES" @"$OUT_DIR/stubs.list"

# Server kit must compile against the same SRG stubs (one mapping for open/action/features).
SERVER_SRC="$HERE/server"
SERVER_CLASSES="$OUT_DIR/server-classes"
mkdir -p "$SERVER_CLASSES"
find "$(abs "$SERVER_SRC")" -name '*.java' > "$OUT_DIR/server.list"
javac --release 17 -encoding UTF-8 -cp "$(abs "$STUB_CLASSES")$CP_SEP$(abs "$GSON")" -d "$SERVER_CLASSES" @"$OUT_DIR/server.list"

find "$(abs "$SRC")" -name '*.java' > "$OUT_DIR/sources.list"
# Windows javac splits -cp on ';' and cannot read /c/... MSYS paths; Linux javac
# splits on ':'. Normalize both path style and separator per platform.
CP="$(abs "$STUB_CLASSES")$CP_SEP$(abs "$BASE_JAR")$CP_SEP$(abs "$GSON")$CP_SEP$(abs "$LOG4J")$CP_SEP$(abs "$MIXIN")$CP_SEP$(abs "$ASM")$CP_SEP$(abs "$ASM_TREE")"
javac --release 17 -encoding UTF-8 -proc:none -cp "$CP" -d "$CLASSES" @"$OUT_DIR/sources.list"

if [[ -d "$TEST_SRC" ]]; then
  find "$(abs "$TEST_SRC")" -name '*.java' > "$OUT_DIR/tests.list"
  TEST_CLASSES="$OUT_DIR/test-classes"
  mkdir -p "$TEST_CLASSES"
  javac --release 17 -encoding UTF-8 -proc:none -cp "$(abs "$CLASSES")$CP_SEP$(abs "$SERVER_CLASSES")$CP_SEP$CP" -d "$TEST_CLASSES" @"$OUT_DIR/tests.list"
  java -cp "$(abs "$TEST_CLASSES")$CP_SEP$(abs "$CLASSES")$CP_SEP$CP" \
    com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiOpenPacketRoundTripTest
  java -Dultragui.fixture="$HERE/server/BridgeFutureFieldsFixture.json" \
    -Dultragui.sketch="$HERE/server/HelloWorldServerSketch.java" \
    -cp "$(abs "$TEST_CLASSES")$CP_SEP$(abs "$SERVER_CLASSES")$CP_SEP$(abs "$STUB_CLASSES")$CP_SEP$(abs "$GSON")" \
    com.example.ultragui.ServerKitSelfTest
fi

cp -f "$BASE_JAR" "$OUT_JAR"

# Preserve + stamp MANIFEST before jar/zip rewrites (jar uf can drop it).
MANIFEST_DIR="$OUT_DIR/manifest"
mkdir -p "$MANIFEST_DIR/META-INF"
unzip -p "$BASE_JAR" META-INF/MANIFEST.MF > "$MANIFEST_DIR/META-INF/MANIFEST.MF"
python3 - "$MANIFEST_DIR/META-INF/MANIFEST.MF" "$VERSION" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
version = sys.argv[2]
text = path.read_text(encoding="utf-8", errors="replace")
lines = []
seen = False
for line in text.splitlines():
    if line.startswith("Implementation-Version:"):
        lines.append(f"Implementation-Version: {version}")
        seen = True
    else:
        lines.append(line)
if not seen:
    lines.append(f"Implementation-Version: {version}")
rewritten = []
for line in lines:
    if line.startswith("MixinConfigs:"):
        configs = [part.strip() for part in line.split(":", 1)[1].split(",") if part.strip()]
        if "kaipath-bodyparts.mixins.json" not in configs:
            configs.append("kaipath-bodyparts.mixins.json")
        rewritten.append("MixinConfigs: " + ",".join(configs))
    else:
        rewritten.append(line)
if not any(line.startswith("MixinConfigs:") for line in rewritten):
    rewritten.append("MixinConfigs: kaipath.mixins.json,kaipath-bodyparts.mixins.json")
lines = rewritten
if not lines or lines[-1] != "":
    lines.append("")
# newline="" — join with CRLF literally; text-mode translation on Windows would
# turn "\r\n" into "\r\r\n", which java.util.jar.Manifest splits at the lone \r
# (severing MixinConfigs behind a phantom blank line — javap reports "invalid
# manifest format (line 3)").
with path.open("w", encoding="utf-8", newline="") as fh:
    fh.write("\r\n".join(lines))
PY

# Drop prior consumer classes (incl. obsolete inner classes) then add kit overlay.
if command -v zip >/dev/null 2>&1; then
  zip -d "$OUT_JAR" \
    'com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/*' \
    'com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry.class' \
    'com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry$*.class' \
    'com/dmzlegacy/kaipath/ultra/consumers/UltraGuiPartners.class' \
    2>/dev/null || true
fi

jar uf "$OUT_JAR" -C "$CLASSES" com/dmzlegacy/kaipath/ultra/consumers/
jar uf "$OUT_JAR" -C "$CLASSES" com/dmzlegacy/kaipath/mixin/FormPartsPreviewMixin.class
jar uf "$OUT_JAR" -C "$CLASSES" com/dmzlegacy/kaipath/mixin/RaceTailSuppressMixin.class
jar uf "$OUT_JAR" -C "$CLASSES" com/dmzlegacy/kaipath/mixin/BodyPartMixinPlugin.class
jar uf "$OUT_JAR" -C "$HERE" kaipath-bodyparts.mixins.json

# Fix stock fitLabel mojibake ellipsis (â€¦ → ...) that shows as weird A/E glyphs.
FIT_CLASS_DIR="$OUT_DIR/fitlabel-patch"
mkdir -p "$FIT_CLASS_DIR/com/dmzlegacy/kaipath/ultra/internal"
unzip -p "$OUT_JAR" com/dmzlegacy/kaipath/ultra/internal/UltraWidgetReflect.class \
  > "$FIT_CLASS_DIR/com/dmzlegacy/kaipath/ultra/internal/UltraWidgetReflect.class"
python3 "$HERE/patch_fitlabel_ascii.py" \
  "$FIT_CLASS_DIR/com/dmzlegacy/kaipath/ultra/internal/UltraWidgetReflect.class"
jar uf "$OUT_JAR" -C "$FIT_CLASS_DIR" com/dmzlegacy/kaipath/ultra/internal/UltraWidgetReflect.class
# Verify mojibake gone and ASCII ellipsis present
if unzip -p "$OUT_JAR" com/dmzlegacy/kaipath/ultra/internal/UltraWidgetReflect.class \
  | python3 -c "import sys; d=sys.stdin.buffer.read(); moj=bytes([0xC3,0xA2,0xE2,0x82,0xAC,0xC2,0xA6]);
sys.exit(1 if moj in d or b'...' not in d else 0)"; then
  echo "fitLabel ASCII ellipsis OK"
else
  echo "ERROR: UltraWidgetReflect still has mojibake ellipsis or missing ASCII ..." >&2
  exit 1
fi

# Restore stamped MANIFEST (jar uf often drops META-INF/MANIFEST.MF; zip is not
# guaranteed in Git Bash, so rewrite with python3 zipfile).
python3 - "$MANIFEST_DIR/META-INF/MANIFEST.MF" "$OUT_JAR" <<'PY'
import os, sys, zipfile
src, jar_path = sys.argv[1], sys.argv[2]
data = open(src, "rb").read()
tmp = jar_path + ".tmp"
with zipfile.ZipFile(jar_path, "r") as zin, zipfile.ZipFile(tmp, "w") as zout:
    for info in zin.infolist():
        if info.filename == "META-INF/MANIFEST.MF":
            continue
        zout.writestr(info, zin.read(info.filename))
    zout.writestr("META-INF/MANIFEST.MF", data)
os.replace(tmp, jar_path)
PY
if ! jar tf "$OUT_JAR" | grep -q '^META-INF/MANIFEST.MF$'; then
  echo "ERROR: META-INF/MANIFEST.MF missing from $OUT_JAR" >&2
  jar tf "$OUT_JAR" | grep META-INF || true
  exit 1
fi
if ! unzip -p "$OUT_JAR" META-INF/MANIFEST.MF | grep -q "Implementation-Version: ${VERSION}"; then
  echo "ERROR: Implementation-Version not stamped to $VERSION" >&2
  unzip -p "$OUT_JAR" META-INF/MANIFEST.MF >&2 || true
  exit 1
fi
if ! jar tf "$OUT_JAR" | grep -q '^META-INF/mods.toml$'; then
  echo "ERROR: META-INF/mods.toml missing from $OUT_JAR" >&2
  exit 1
fi

for c in \
  GuiOpenPacket GuiActionPacket GuiFeaturesPacket GuiModelSyncPacket \
  LmGuiNetwork LmScreens LmDynamicScreen LmHubScreen LmUltraConsumer \
  UltraBridgeChrome UltraBridgeFooter; do
  if ! jar tf "$OUT_JAR" | grep -q "consumers/legacymechanics/${c}.class"; then
    echo "ERROR: missing $c in $OUT_JAR" >&2
    exit 1
  fi
done
if ! jar tf "$OUT_JAR" | grep -q 'consumers/UltraGuiConsumerRegistry.class'; then
  echo "ERROR: missing UltraGuiConsumerRegistry in $OUT_JAR" >&2
  exit 1
fi
if ! jar tf "$OUT_JAR" | grep -q 'consumers/UltraGuiPartners.class'; then
  echo "ERROR: missing UltraGuiPartners in $OUT_JAR" >&2
  exit 1
fi
for m in FormPartsPreviewMixin RaceTailSuppressMixin BodyPartMixinPlugin; do
  if ! jar tf "$OUT_JAR" | grep -q "mixin/${m}.class"; then
    echo "ERROR: missing mixin/$m in $OUT_JAR" >&2
    exit 1
  fi
done
if ! unzip -p "$OUT_JAR" kaipath-bodyparts.mixins.json | grep -q 'RaceTailSuppressMixin'; then
  echo "ERROR: kaipath-bodyparts.mixins.json does not register RaceTailSuppressMixin" >&2
  exit 1
fi

javap -classpath "$OUT_JAR" -public \
  com.dmzlegacy.kaipath.ultra.consumers.legacymechanics.GuiOpenPacket \
  | tee "$OUT_DIR/GuiOpenPacket.javap.txt"
if ! grep -q 'inputsJson' "$OUT_DIR/GuiOpenPacket.javap.txt"; then
  echo "ERROR: GuiOpenPacket missing inputsJson accessor" >&2
  exit 1
fi

# Confirm advertise tokens are in the registry class bytes (python3 — strings is
# not guaranteed in Git Bash).
python3 - "$OUT_JAR" <<'PY'
import sys, zipfile
data = zipfile.ZipFile(sys.argv[1]).read(
    "com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry.class")
missing = [t for t in ("textInput", "fixedFooter", "portraitFrame", "bodyParts", "bodyPartsSync")
           if t.encode() not in data]
if missing:
    print("ERROR: UltraGuiConsumerRegistry missing advertise tokens:", missing)
    sys.exit(1)
print("advertise tokens OK")
PY

echo "Built $OUT_JAR"
jar tf "$OUT_JAR" | grep -E 'consumers/(UltraGuiConsumerRegistry|UltraGuiPartners|legacymechanics/)' | sort
echo "Advertise: $(python3 - "$(abs "$OUT_JAR")" <<'PY'
import sys, zipfile, re
z = zipfile.ZipFile(sys.argv[1])
data = z.read("com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry.class")
# rough extract of UTF-8 string constants containing textInput
for m in re.findall(rb'\[[^\]]{0,200}textInput[^\]]{0,80}\]', data):
    print(m.decode("utf-8", "replace"))
    break
else:
    print("textInput present in class bytes")
PY
)"
