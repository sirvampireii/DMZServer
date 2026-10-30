#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRG="$ROOT/libraries/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
FORGE_U="$ROOT/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar"
FORGE_S="$ROOT/libraries/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-server.jar"
DMZ="${DMZ_JAR:-$ROOT/mods/dragonminez-2.2.jar}"
GSON="$ROOT/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
LIGHTMANS="$ROOT/libraries/lightmanscurrency-1.20.1-2.3.0.5.jar"
if [[ ! -f "$LIGHTMANS" ]]; then
  LIGHTMANS="$ROOT/mods/lightmanscurrency-1.20.1-2.3.0.5.jar"
fi
FTB="$ROOT/libraries/ftb-teams-forge-2001.3.1.jar"
FTB_CHUNKS="$ROOT/libraries/ftb-chunks-forge-2001.3.8.jar"
FTB_LIBRARY="$ROOT/libraries/ftb-library-forge-2001.2.13.jar"
ARCHITECTURY="$ROOT/libraries/architectury-9.2.14-forge.jar"
REVAMP="$ROOT/libraries/dmzrevamp-2.1.1.jar"
CNPC="$ROOT/mods/CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260227.jar"
if [[ ! -f "$FTB" ]]; then
  echo "Downloading FTB Teams for compile..."
  curl -fsSL -o "$FTB" \
    "https://maven.ftb.dev/releases/dev/ftb/mods/ftb-teams-forge/2001.3.1/ftb-teams-forge-2001.3.1.jar"
fi
for dep in "$FTB_CHUNKS" "$FTB_LIBRARY" "$ARCHITECTURY" "$REVAMP" "$CNPC"; do
  if [[ ! -f "$dep" ]]; then
    echo "Missing $dep — copy from live mods/ or libraries/" >&2
    exit 1
  fi
done
NAME="LegacyMechanics"
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/src/main/java"
MOD_JAVA="$SRC/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java"
VERSION="$(grep -oP 'public static final String VERSION = "\K[0-9.]+(?=")' "$MOD_JAVA")"
if [[ -z "$VERSION" ]]; then
  echo "Could not read VERSION from $MOD_JAVA" >&2
  exit 1
fi
RES="$HERE/src/main/resources"
OUT="$HERE/build/classes"
CONSOLIDATED_BASE="$HERE/base/LegacyMechanics-4.5.49-consolidated.jar"
LEGACY_BASE="$HERE/base/LegacyMechanics-4.5.23-direct-dmz-resource-max.jar"
BASE_JAR="${LM_BASE_JAR:-$CONSOLIDATED_BASE}"
if [[ ! -f "$BASE_JAR" && -f "$LEGACY_BASE" ]]; then
  BASE_JAR="$LEGACY_BASE"
fi
# Overlay CNPC/GUI patches onto consolidated base; ship as LegacyMechanics-${VERSION}.jar
if [[ -f "$BASE_JAR" ]]; then
  JAR="$ROOT/mods/${NAME}-${VERSION}.jar"
else
  JAR="$ROOT/mods/${NAME}-${VERSION}.jar"
fi

# Replace this version's jar. Older LegacyMechanics jars are removed after a successful build.
rm -f "$JAR" \
      "$ROOT"/mods/AdaptiveDifficulty-"${VERSION}".jar \
      "$ROOT"/mods/dmz_adaptive_difficulty-"${VERSION}".jar

NETTY_V="4.1.82.Final"
NETTY="$ROOT/libraries/io/netty/netty-common/$NETTY_V/netty-common-$NETTY_V.jar:\
$ROOT/libraries/io/netty/netty-buffer/$NETTY_V/netty-buffer-$NETTY_V.jar:\
$ROOT/libraries/io/netty/netty-transport/$NETTY_V/netty-transport-$NETTY_V.jar:\
$ROOT/libraries/io/netty/netty-handler/$NETTY_V/netty-handler-$NETTY_V.jar:\
$ROOT/libraries/io/netty/netty-codec/$NETTY_V/netty-codec-$NETTY_V.jar:\
$ROOT/libraries/io/netty/netty-resolver/$NETTY_V/netty-resolver-$NETTY_V.jar"
CP="$SRG:$FORGE_S:$FORGE_U:$GSON:$LIGHTMANS:$FTB:$FTB_CHUNKS:$FTB_LIBRARY:$ARCHITECTURY:$REVAMP:\
$ROOT/libraries/net/minecraftforge/fmlcore/1.20.1-47.4.10/fmlcore-1.20.1-47.4.10.jar:\
$ROOT/libraries/net/minecraftforge/fmlloader/1.20.1-47.4.10/fmlloader-1.20.1-47.4.10.jar:\
$ROOT/libraries/net/minecraftforge/forgespi/7.0.1/forgespi-7.0.1.jar:\
$ROOT/libraries/net/minecraftforge/javafmllanguage/1.20.1-47.4.10/javafmllanguage-1.20.1-47.4.10.jar:\
$ROOT/libraries/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar:\
$ROOT/libraries/org/ow2/asm/asm-tree/9.6/asm-tree-9.6.jar:\
$ROOT/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar:\
$ROOT/libraries/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar:\
$ROOT/libraries/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar:\
$ROOT/libraries/com/mojang/authlib/4.0.43/authlib-4.0.43.jar:\
$ROOT/libraries/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar:\
$ROOT/libraries/com/google/guava/guava/31.1-jre/guava-31.1-jre.jar:\
$ROOT/libraries/org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar:\
$NETTY:\
$CNPC:\
$DMZ"

NOEA="${NOEA_JAR:-}"
if [[ -z "$NOEA" ]]; then
  for candidate in \
    /tmp/noea-1.2.0.jar \
    /tmp/noea.jar \
    "$ROOT"/mods/Noea-1.2*.jar \
    "$ROOT"/mods/Noea*.jar
  do
    if [[ -f "$candidate" ]]; then
      NOEA="$candidate"
      break
    fi
  done
fi
if [[ ! -f "$NOEA" ]]; then
  echo "Missing Noea jar for absorption mixin compile (set NOEA_JAR)." >&2
  exit 1
fi
echo "Using Noea jar for compile: $NOEA"
MELEE="$(ls -1 "$ROOT"/mods/dmz_mohist_melee_fix-*.jar 2>/dev/null | sort -V | tail -1 || true)"
if [[ -z "${MELEE:-}" || ! -f "$MELEE" ]]; then
  echo "Missing dmz_mohist_melee_fix jar for the reset wipe mixin." >&2
  exit 1
fi
echo "Using melee jar for compile: $MELEE"
# dmzultra jar - the nemesis gate mixin targets com.dmzultra.journal.Rival by name.
DMZULTRA="${DMZULTRA_JAR:-}"
if [[ -z "$DMZULTRA" ]]; then
  for candidate in /tmp/dmzultra-*.jar "$ROOT"/mods/dmzultra-*.jar; do
    if [[ -f "$candidate" ]]; then
      DMZULTRA="$candidate"
      break
    fi
  done
fi
if [[ ! -f "$DMZULTRA" ]]; then
  echo "Missing dmzultra jar for the nemesis gate mixin compile (set DMZULTRA_JAR)." >&2
  exit 1
fi
echo "Using dmzultra jar for compile: $DMZULTRA"
CP="$CP:$NOEA:$MELEE:$DMZULTRA"

echo "Cleaning compile output before packaging"
rm -rf "$OUT"
mkdir -p "$OUT"

mapfile -t SOURCES < <(find "$SRC" -name '*.java' | sort)
# Argfile keeps Windows argv under the 32K limit; javac supports @file everywhere.
# MSYS does not path-convert @file contents, so map /c/... -> c:/... on Windows.
SOURCES_LIST="$HERE/build/lm-sources.list"
mkdir -p "$HERE/build"
if [[ -n "${WINDIR:-}" ]]; then
  printf '%s\n' "${SOURCES[@]}" | sed -E 's|^/([A-Za-z])/|\1:/|' > "$SOURCES_LIST"
else
  printf '%s\n' "${SOURCES[@]}" > "$SOURCES_LIST"
fi
javac --release 17 -encoding UTF-8 -proc:none -cp "$CP" -d "$OUT" "@$SOURCES_LIST"
# Every mixin target, overload, callback type, handler signature, and @At must match the
# DragonMineZ jar on CP (javac never reads mixin target strings). The two optional-mod mixins
# are plugin-gated on their mod being present, so their target class may be absent here.
python3 "$ROOT/scripts/audit_mixin_targets.py" --src "$SRC" --cp "$CP" --gate-plugin "$SRC/com/dbzlegacy/adaptivedifficulty/mixin/LegacyMechanicsMixinPlugin.java"   --optional-mixins VipersCallUnendingVenomMixin,KaiPathLmConsumerBootstrapMixin   "$RES/legacymechanics.mixins.json"

# Break ldc #0xC2 / operand-C2 + goto(A7) false positives in ultra callers so a
# raw C2 A7 (§) scan only hits UltraStyle (which owns the mark in its CP).
ASM_JAR="$ROOT/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar"
PATCH_SRC="$HERE/sim/PatchUltraGotoFalsePositives.java"
PATCH_OUT="$HERE/build/patch-ultra"
if [[ -f "$ASM_JAR" && -f "$PATCH_SRC" ]]; then
  rm -rf "$PATCH_OUT"
  mkdir -p "$PATCH_OUT"
  javac --release 17 -encoding UTF-8 -proc:none -cp "$ASM_JAR" -d "$PATCH_OUT" "$PATCH_SRC"
  java -cp "$PATCH_OUT:$ASM_JAR" PatchUltraGotoFalsePositives "$OUT"
fi

# For every class this compile actually overlaid, drop base-jar inner classes the
# new bytecode does not emit, and copy the inner classes it does emit.
sync_overlaid_inners() {
  local out_dir="$1" merge_dir="$2"
  local src rel dest base dest_dir src_dir stale name inner
  while IFS= read -r -d '' src; do
    rel="${src#"$out_dir"/}"
    dest="$merge_dir/$rel"
    [[ -f "$dest" ]] || continue
    cmp -s "$src" "$dest" || continue
    base="$(basename "$rel" .class)"
    [[ "$base" == *\$* ]] && continue
    dest_dir="$(dirname "$dest")"
    src_dir="$(dirname "$src")"
    shopt -s nullglob
    for stale in "$dest_dir/${base}\$"*.class; do
      name="$(basename "$stale")"
      if [[ ! -f "$src_dir/$name" ]]; then
        rm -f "$stale"
      fi
    done
    for inner in "$src_dir/${base}\$"*.class; do
      cp "$inner" "$dest_dir/"
    done
    shopt -u nullglob
  done < <(find "$out_dir" -name '*.class' -print0)
}

merge_onto_base_jar() {
  local base="$1" dest="$2"
  local tmp merge
  tmp="$(mktemp -d)"
  merge="$(mktemp -d)"
  unzip -q "$base" -d "$merge"
  # CNPC GUI package retired — strip any stale classes from the base jar.
  local rel
  rm -rf "$merge/com/dbzlegacy/adaptivedifficulty/gui/cnpc"
  # Ultra / KaiPath bridge — server packet builders + legacymechanics:gui channel.
  if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/gui/ultra" ]]; then
    rm -rf "$merge/com/dbzlegacy/adaptivedifficulty/gui/ultra"
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/gui/ultra"
    cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/gui/ultra/." "$merge/com/dbzlegacy/adaptivedifficulty/gui/ultra/"
  fi
  if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/net/gui" ]]; then
    rm -rf "$merge/com/dbzlegacy/adaptivedifficulty/net/gui"
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/net/gui"
    cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/net/gui/." "$merge/com/dbzlegacy/adaptivedifficulty/net/gui/"
  fi
  for class in GuiBackend DifficultyMenu RivalMenu SparMenu PrestigeMenu CharacterServicesMenu \
      ProgressionMenu MechanicsMenu SkillsMenu ForgeInspectGui CnpcStaffTestGui CnpcGuiOpener \
      GuiNotice CharacterServicesGuiApi; do
    if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/${class}.class" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/gui"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/${class}.class" \
        "$merge/com/dbzlegacy/adaptivedifficulty/gui/${class}.class"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/${class}\$"*.class \
        "$merge/com/dbzlegacy/adaptivedifficulty/gui/" 2>/dev/null || true
    fi
  done
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalSystem.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalSystem.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/rival/RivalSystem.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalFusion.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/rival"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalFusion.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/rival/RivalFusion.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalChallengeManager.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/rival"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalChallengeManager.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/rival/RivalChallengeManager.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalStore.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/rival"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalStore.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/rival/RivalStore.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/rival/RivalStore\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/rival/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/rival/NemesisSpawnMode.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/rival"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/rival/NemesisSpawnMode.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/rival/NemesisSpawnMode.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/SparGuiApi.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/SparGuiApi.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/gui/SparGuiApi.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/sparring/DojoRankings.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/sparring"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/sparring/DojoRankings.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/sparring/DojoRankings.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/" 2>/dev/null || true
  fi
  # Role-warn helper used by PrestigeSystem.replyConfirm + UltraPrestigeScreens — must ship
  # from src or the first prestige confirm throws NoClassDefFoundError.
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeUniverseRoleWarn.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeUniverseRoleWarn.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeUniverseRoleWarn.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeUniverseRoleWarn\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/" 2>/dev/null || true
  fi
  # God Ki Enhancement prestige store — buy flow, login top-up, form gates.
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/GodKiEnhancementShop.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/GodKiEnhancementShop.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/GodKiEnhancementShop.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/GodKiEnhancementShop\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/shop/" 2>/dev/null || true
  fi
  # Hub/chat fallback + /lm open|page — must ship from src (base jar may still reference /lmdo).
  for class in DifficultyChatMenu ProgressionChatMenu GuiClickConfirm DifficultyTeamGuiApi CnpcStaffTestGui; do
    if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/${class}.class" ]]; then
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/${class}.class" \
        "$merge/com/dbzlegacy/adaptivedifficulty/gui/${class}.class"
      # Inner classes (GuiClickConfirm$Pending) are separate files. Copying only the
      # outer class throws NoClassDefFoundError the first time a confirm click runs.
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/${class}\$"*.class \
        "$merge/com/dbzlegacy/adaptivedifficulty/gui/" 2>/dev/null || true
    fi
  done
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/command"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/command/" 2>/dev/null || true
  fi
  for class in LmCommandSuggestions LmCommandFeedback LmStaffHelp LmCommandHelp LmCommandMessages CommandAccess LmAdminArgCoalesce MohistCommandBridge PrestigeAdminCommandTree DifficultyCommands RivalCommands SparCommands ProgressionCommands CharacterCommands MechanicsCommands FusionCooldownReset CommandTreeArgumentGuard; do
    if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/command/${class}.class" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/command"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/command/${class}.class" \
        "$merge/com/dbzlegacy/adaptivedifficulty/command/${class}.class"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/command/${class}\$"*.class \
        "$merge/com/dbzlegacy/adaptivedifficulty/command/" 2>/dev/null || true
    fi
  done
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/data/PlayerDataClear.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/data"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/data/PlayerDataClear.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/data/PlayerDataClear.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/data/PlayerDataClear\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/data/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesStore.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/character"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesStore.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesStore.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesStore\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/character/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesSystem.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesSystem.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesSystem.class"
  fi
  for class in SagaResetConfig SagaResetService; do
    if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/quest/${class}.class" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/quest"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/quest/${class}.class" \
        "$merge/com/dbzlegacy/adaptivedifficulty/quest/${class}.class"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/quest/${class}\$"*.class \
        "$merge/com/dbzlegacy/adaptivedifficulty/quest/" 2>/dev/null || true
    fi
  done
  for class in CharacterServicesAccess CharacterServicesPermissionBootstrap DmzFightingClassStatsSync \
      DmzClassCommandApply RaceChangeCreationFlow       CosmeticHeadBoneService CosmeticHeadBoneCatalog \
      RaceHeadBoneSync HeadPartPieces CharacterServicesConfig RacialBonusWipeHelper \
      RaceChangeSkillPreserve RacialStrikeWipe RacialStrikeRules; do
    if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/character/${class}.class" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/character"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/character/${class}.class" \
        "$merge/com/dbzlegacy/adaptivedifficulty/character/${class}.class"
      cp "$OUT/com/dbzlegacy/adaptivedifficulty/character/${class}\$"*.class \
        "$merge/com/dbzlegacy/adaptivedifficulty/character/" 2>/dev/null || true
    fi
  done
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/gui/CharacterServicesGuiApi.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/gui/CharacterServicesGuiApi.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/gui/CharacterServicesGuiApi.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/event"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/event/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/event/DragonBallRadarPickup.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/event"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/event/DragonBallRadarPickup.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/event/DragonBallRadarPickup.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/event/DragonBallRadarPickup\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/event/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/event/FallDamageDiag.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/event"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/event/FallDamageDiag.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/event/FallDamageDiag.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/event/FallDamageDiag\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/event/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/util/StaffAccess.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/util/StaffAccess.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/util/StaffAccess.class"
  fi
  # Death, sparring hits, and TP awards call TpBossNotify. A base jar without it
  # throws NoClassDefFoundError the first time those paths run.
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/util/TpBossNotify.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/util"
    rm -f "$merge/com/dbzlegacy/adaptivedifficulty/util/TpBossNotify\$"*.class
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/util/TpBossNotify.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/util/TpBossNotify.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/util/TpBossNotify\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/util/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.class"
  fi
  if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/kube" ]]; then
    rm -rf "$merge/com/dbzlegacy/adaptivedifficulty/kube"
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/kube"
    cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/kube/." \
      "$merge/com/dbzlegacy/adaptivedifficulty/kube/"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.class"
  fi
  if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/progression/end" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/end"
    cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/progression/end/." \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/end/"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/AndroidConversion.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/AndroidConversion.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/race/AndroidConversion.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLockConfig.class" ]]; then
    rm -f "$merge/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLockConfig\$"*.class
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLockConfig.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLockConfig.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLockConfig\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/race/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.class"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/ProgressionConfig.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/ProgressionConfig.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/ProgressionConfig.class"
  fi
  if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/progression/form" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/form"
    cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/progression/form/." \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/form/"
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/ProgressionModuleCatalog.class" ]]; then
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/ProgressionModuleCatalog.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/ProgressionModuleCatalog.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/ProgressionModuleCatalog\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/progression/" 2>/dev/null || true
  fi
  # Ki/stamina: one compiled slice + src mixins so HUD max matches DmzResourcePoolClamp.
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/DmzResourcePoolClamp.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/progression/bridge" \
      "$merge/com/dbzlegacy/adaptivedifficulty/mixin"
    for pool_cls in DmzResourcePoolClamp LmOverhaulPrestigeIntegration ProgressionSystem \
        StaminaRegenGuard StatsDataLoadContext PersonalLevelCapMirror LmOverhaulCapMath \
        LmStatsDataAccess PrestigeResourceRecovery DmzInflatedAttributeReset FusionBonusOwner; do
      if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/progression/${pool_cls}.class" ]]; then
        cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/${pool_cls}.class" \
          "$merge/com/dbzlegacy/adaptivedifficulty/progression/${pool_cls}.class"
        cp "$OUT/com/dbzlegacy/adaptivedifficulty/progression/${pool_cls}\$"*.class \
          "$merge/com/dbzlegacy/adaptivedifficulty/progression/" 2>/dev/null || true
      fi
    done
    if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/progression/bridge" ]]; then
      cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/progression/bridge/." \
        "$merge/com/dbzlegacy/adaptivedifficulty/progression/bridge/"
    fi
    for mixin_cls in ResourcesEnergyDrainGuardMixin StatsDataGuardsMixin StatsDataMixin \
        StatsSyncC2SGenderMixin \
        DmzStatsResetPlayerBlockMixin \
        MeleeStatsResetAbsorptionWipeMixin \
        MeleeStatsResetPlayerBlockMixin \
        DendeResetAbsorptionWipeMixin \
        CreateCharacterAbsorptionWipeMixin \
        FusionBonusReadGateMixin \
        StrikeTechniqueReadGateMixin \
        FusionUnfuseBonusClearMixin \
        FormModeHandlerGodKiGateMixin \
        StackFormModeHandlerGodKiGateMixin \
        LegacyMechanicsMixinPlugin; do
      if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/${mixin_cls}.class" ]]; then
        cp "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/${mixin_cls}.class" \
          "$merge/com/dbzlegacy/adaptivedifficulty/mixin/${mixin_cls}.class"
      fi
    done
    if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/noea" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/mixin/noea"
      cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/noea/." \
        "$merge/com/dbzlegacy/adaptivedifficulty/mixin/noea/"
    fi
    if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/dmzultra" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/mixin/dmzultra"
      cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/dmzultra/." \
        "$merge/com/dbzlegacy/adaptivedifficulty/mixin/dmzultra/"
    fi
    if [[ -d "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/simplymore" ]]; then
      mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/mixin/simplymore"
      cp -a "$OUT/com/dbzlegacy/adaptivedifficulty/mixin/simplymore/." \
        "$merge/com/dbzlegacy/adaptivedifficulty/mixin/simplymore/"
    fi
    # Ordinary classes cannot live in the mixin package. The server classloader
    # does not resolve them, so the Majin wipe never runs.
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/noea"
    for noea_cls in MajinAbsorptionStore AbsorptionClearLog AbsorptionWipeHelper GodKiWipeHelper; do
      if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/noea/${noea_cls}.class" ]]; then
        cp "$OUT/com/dbzlegacy/adaptivedifficulty/noea/${noea_cls}.class" \
          "$merge/com/dbzlegacy/adaptivedifficulty/noea/${noea_cls}.class"
      fi
    done
    rm -f "$merge/com/dbzlegacy/adaptivedifficulty/mixin/AbsorptionClearLog.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/mixin/noea/MajinAbsorptionStore.class"
    cp "$RES/legacymechanics.mixins.json" "$merge/legacymechanics.mixins.json"
    if [[ -f "$RES/legacymechanics.refmap.json" ]]; then
      cp "$RES/legacymechanics.refmap.json" "$merge/legacymechanics.refmap.json"
    fi
  fi
  # Retired facades. A base jar can still contain these classes after the sources are gone.
  rm -f \
    "$merge/com/dbzlegacy/adaptivedifficulty/gui/BukkitGuiBridge.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/gui/CmiGuiBridge.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/progression/skills/SkillProgression.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/progression/race/RaceProgression.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/progression/tp/TpProgression.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/progression/bridge/CnpcBridge.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataLoadClampMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataGetPlayerMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataSecondaryPlayerMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataRestoreMultiplierClampMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataResetPrestigeSyncMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/ResourcesPoolClampMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/ResourcesLoadClampMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/DmzStatsResetAbsorptionWipeMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataHudPoolMaxMixin.class" \
    "$merge/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataOverhaulCombatScaleMixin.class"
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/config"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/config"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/" 2>/dev/null || true
  fi
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/config/editor"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/editor/" 2>/dev/null || true
  fi
  # ConfigEditor.friendlyDescription / staffLabel call ConfigFieldHelp. Leaving it
  # off the overlay throws NoClassDefFoundError when staff open the config editor.
  if [[ -f "$OUT/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp.class" ]]; then
    mkdir -p "$merge/com/dbzlegacy/adaptivedifficulty/config/editor"
    rm -f "$merge/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp\$"*.class
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp.class" \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp.class"
    cp "$OUT/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp\$"*.class \
      "$merge/com/dbzlegacy/adaptivedifficulty/config/editor/" 2>/dev/null || true
  fi
  # Keep net/gui (Ultra channel). Strip client-only packages if a base jar ever had them.
  rm -rf "$merge/com/dbzlegacy/adaptivedifficulty/client"
  # Classes copied from this compile must ship their inner classes, and must
  # not keep a stale base-jar inner the new bytecode no longer emits.
  sync_overlaid_inners "$OUT" "$merge"
  cp "$RES/META-INF/mods.toml" "$merge/META-INF/mods.toml"
  if [[ -f "$RES/META-INF/kaipath-ultra.txt" ]]; then
    cp "$RES/META-INF/kaipath-ultra.txt" "$merge/META-INF/kaipath-ultra.txt"
  fi
  if [[ -d "$RES/data" ]]; then
    mkdir -p "$merge/data"
    cp -a "$RES/data/." "$merge/data/"
  fi
  if [[ -f "$RES/character-services.default.json" ]]; then
    cp "$RES/character-services.default.json" "$merge/character-services.default.json"
  fi
  if [[ -f "$RES/dmzultra-form-parts.json" ]]; then
    cp "$RES/dmzultra-form-parts.json" "$merge/dmzultra-form-parts.json"
  fi
  if [[ ! -f "$merge/legacymechanics.mixins.json" ]]; then
    cp "$RES/legacymechanics.mixins.json" "$merge/legacymechanics.mixins.json"
  fi
  (cd "$merge" && jar cfm "$dest" META-INF/MANIFEST.MF .)
  if unzip -p "$dest" legacymechanics.mixins.json | grep -Eq '"StatsDataHudPoolMaxMixin"|"StatsDataOverhaulCombatScaleMixin"'; then
    echo "ERROR: shipped mixins.json must not register LM prestige scale mixins" >&2
    exit 1
  fi
  rm -rf "$tmp" "$merge"
}

if [[ -f "$BASE_JAR" ]]; then
  echo "Merging compiled Ultra overlay onto base: $(basename "$BASE_JAR")"
  merge_onto_base_jar "$BASE_JAR" "$JAR"
else
  echo "WARN: Base jar missing ($BASE_JAR) — full compile jar (no live ki/stamina base)" >&2
  (
    cd "$OUT"
    # Pass the com/ dir itself: recursive, and avoids the Windows 32K argv limit
    # that $(find com -name '*.class') hits (~463 args). Adds dir entries like the base merge path.
    jar cvmf "$RES/META-INF/MANIFEST.MF" "$JAR" com \
      -C "$RES" META-INF/mods.toml \
      -C "$RES" META-INF/kaipath-ultra.txt \
      -C "$RES" pack.mcmeta \
      -C "$RES" legacymechanics.mixins.json \
      -C "$RES" legacymechanics.refmap.json \
      -C "$RES" data \
      -C "$RES" character-services.default.json \
      -C "$RES" dmzultra-form-parts.json
  )
fi

# Ki pool: do NOT overlay reference/ki-pool-2.4.115 .class
# files — mixing 2.4.115 bytecode with a fresh compile causes Java 17 VerifyError during
# StatsData#load (mixins → DmzResourcePoolClamp) and clients see "Invalid player data".

echo "Built $JAR (base: $(basename "$BASE_JAR"))"
if jar tf "$JAR" | grep -Eq 'StatsDataHudPoolMaxMixin|StatsDataOverhaulCombatScaleMixin'; then
  echo "ERROR: shipped jar still contains unregistered prestige scale mixin classes" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'kube/LmHotPath.class'; then
  echo "ERROR: kube/LmHotPath.class missing from $JAR" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'gui/GuiClickConfirm\$Pending.class'; then
  echo "ERROR: GuiClickConfirm\$Pending.class missing from $JAR (confirm clicks crash the server)" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'net/gui/LmGuiNetwork.class'; then
  echo "ERROR: net/gui/LmGuiNetwork.class missing from $JAR (Ultra GUI channel)" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'gui/ultra/UltraGui.class'; then
  echo "ERROR: gui/ultra/UltraGui.class missing from $JAR" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'util/TpBossNotify.class'; then
  echo "ERROR: util/TpBossNotify.class missing from $JAR (death/spar/TP notices crash)" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'util/TpBossNotify$Held.class'; then
  echo "ERROR: util/TpBossNotify\$Held.class missing from $JAR" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'config/editor/ConfigFieldHelp.class'; then
  echo "ERROR: config/editor/ConfigFieldHelp.class missing from $JAR (staff config editor crashes)" >&2
  exit 1
fi
if ! jar tf "$JAR" | grep -q 'META-INF/kaipath-ultra.txt'; then
  echo "ERROR: META-INF/kaipath-ultra.txt missing from $JAR (KaiPath partner discovery)" >&2
  exit 1
fi
# ── Weapon hotpath classes (kube/hotpath): source-compiled, byte-verify baseline ─
# These 9 classes now have COMMITTED source (reconstructed from the audited live bytecode; the
# overlay workflow is RETIRED). build.sh no longer copies bytecode — the classes compile from src/.
# A byte-verify guard ensures every build ships bytecode identical to the committed baseline in
# reference/weapon-hotpath-4.6.229/, so a future refactor can never silently change or drop them.
# Re-baseline after a good live deploy: run once with LM_WEAPON_SEED=1, then commit the baseline.
WEAPON_PKG="com/dbzlegacy/adaptivedifficulty/kube/hotpath"
WEAPON_REF="${LM_WEAPON_REF_DIR:-$HERE/reference/weapon-hotpath-4.6.229}"
WEAPON_CLASSES=(
  "WeaponBonusScale.class"
  "WeaponBonusScale\$Info.class"
  "WeaponBonusScale\$Result.class"
  "WeaponBonusTables.class"
  "WeaponBonusTables\$WeaponDef.class"
  "WeaponRatioConfig.class"
  "WeaponRatioConfig\$Json.class"
  "WeaponRatioConfig\$Snapshot.class"
  "VipersCallBlacklist.class"
)
# Every weapon class must be present in the built jar (a dropped class is silent behavior loss).
for c in "${WEAPON_CLASSES[@]}"; do
  if ! jar tf "$JAR" | grep -qF "$WEAPON_PKG/$c"; then
    echo "ERROR: $WEAPON_PKG/$c missing from $JAR (weapon hotpath source did not compile/package)" >&2
    exit 1
  fi
done
if [[ "${LM_WEAPON_SEED:-0}" == "1" ]]; then
  echo "SEEDING weapon hotpath baseline from $(basename "$JAR") into $(basename "$WEAPON_REF")"
  rm -rf "$WEAPON_REF"
  mkdir -p "$WEAPON_REF"
  for c in "${WEAPON_CLASSES[@]}"; do
    unzip -o -q -d "$WEAPON_REF" "$JAR" "$WEAPON_PKG/$c"
  done
  echo "  seeded $(find "$WEAPON_REF" -name '*.class' | wc -l) classes — commit this baseline"
else
  if [[ ! -d "$WEAPON_REF" ]]; then
    echo "ERROR: weapon hotpath baseline not seeded: $WEAPON_REF" >&2
    echo "  Run one known-good build with LM_WEAPON_SEED=1 to extract the baseline, then commit it." >&2
    exit 1
  fi
  echo "Verifying 9 weapon hotpath classes against baseline $(basename "$WEAPON_REF")"
  for c in "${WEAPON_CLASSES[@]}"; do
    ref="$WEAPON_REF/$WEAPON_PKG/$c"
    if [[ ! -f "$ref" ]]; then
      echo "ERROR: baseline missing $WEAPON_PKG/$c (re-seed with LM_WEAPON_SEED=1)" >&2
      exit 1
    fi
    ref_sum="$(sha256sum "$ref" | awk '{print $1}')"
    jar_sum="$(unzip -p "$JAR" "$WEAPON_PKG/$c" | sha256sum | awk '{print $1}')"
    if [[ "$ref_sum" != "$jar_sum" ]]; then
      echo "ERROR: $WEAPON_PKG/$c in $JAR does not match the committed baseline" >&2
      echo "  baseline=$ref_sum  shipped=$jar_sum" >&2
      echo "  If intended, re-baseline with LM_WEAPON_SEED=1 and commit the new baseline." >&2
      exit 1
    fi
  done
  echo "  all 9 weapon hotpath classes byte-identical to baseline"
fi
# ── Prestige-shop (4.6.228.1) delta classes: byte-verify against reference set ─
# reference/prestige-shop-228.1/ holds the 25 audited .class files (15 new shop classes
# PrestigeCustomShop/PrestigeShopConfig/PrestigeShopHooks + inners, and 10 modified classes) extracted
# from the live 4.6.228.1 jar. Unlike the weapon guard this is a pure ASSERTION (no overlay): once the
# authored 228.1 source is compiled into the jar, all 25 must be byte-identical to the reference.
# Gated on PrestigeShopConfig.java existing in src/ — that class is new in 228.1, so the marker is
# absent until the authored source is integrated; the guard then stays dormant and the current 4.6.228
# build remains green. See reference/README.md for the refresh procedure.
if [[ -f "$SRC/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig.java" ]]; then
  echo ">> Prestige-shop 228.1 source integrated — verifying 25 delta classes against reference set"
  python3 "$HERE/sim/audit_prestige_shop_classes.py" "$JAR"
else
  echo ">> Prestige-shop 228.1 source not yet integrated (no PrestigeShopConfig.java in src/) — 25-class byte-verify dormant (harness armed)"
fi
python3 "$HERE/sim/audit_jar_inner_classes.py" "$JAR"
python3 "$ROOT/scripts/audit_jar_manifest.py" "$JAR" "$VERSION"
python3 - "$JAR" << 'PY'
import sys, zipfile
jar = sys.argv[1]
bad = []
banned = (
    "com/dbzlegacy/adaptivedifficulty/client/",
)
required = (
    "com/dbzlegacy/adaptivedifficulty/net/gui/LmGuiNetwork.class",
    "com/dbzlegacy/adaptivedifficulty/net/gui/GuiOpenPacket.class",
    "com/dbzlegacy/adaptivedifficulty/net/gui/GuiActionPacket.class",
    "com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraGui.class",
    "META-INF/kaipath-ultra.txt",
)
with zipfile.ZipFile(jar) as zf:
    names = set(zf.namelist())
    for req in required:
        if req not in names:
            bad.append("MISSING " + req)
    for name in names:
        norm = name.replace("\\", "/")
        if any(norm.startswith(prefix) for prefix in banned):
            bad.append(name)
            continue
        if not name.endswith(".class") or not norm.startswith("com/dbzlegacy/"):
            continue
        # net/gui + gui/ultra may reference LmGuiNetwork; that is server-side Ultra.
        if norm.startswith("com/dbzlegacy/adaptivedifficulty/net/gui/") or \
                norm.startswith("com/dbzlegacy/adaptivedifficulty/gui/ultra/"):
            data = zf.read(name)
            if (b"com/dmzultra/client" in data or b"com.dmzultra.client" in data
                    or b"net/minecraft/client/" in data or b"LmClientBootstrap" in data):
                bad.append(name)
            continue
        data = zf.read(name)
        if (b"com/dmzultra/client" in data or b"com.dmzultra.client" in data
                or b"net/minecraft/client/" in data or b"LmClientBootstrap" in data):
            bad.append(name)
if bad:
    print("ERROR: jar client GUI / Ultra packaging check failed:", file=sys.stderr)
    for name in bad:
        print("  " + name, file=sys.stderr)
    sys.exit(1)
print("Server jar ships Ultra net/gui; no client GUI dependency")
PY
# A second LegacyMechanics jar in mods/ crashes a fresh server on startup.
find "$ROOT/mods" -maxdepth 1 -type f -name 'LegacyMechanics-*.jar' ! -name "LegacyMechanics-${VERSION}.jar" -delete
jar tf "$JAR"

# Fail-closed audits: product features, combat scaling sim, GUI ABI.
HERE_SIM="$(cd "$(dirname "$0")" && pwd)/sim"
python3 "$HERE_SIM/audit_mixin_visibility.py"
python3 "$HERE_SIM/audit_overhaul_scale_delegation.py"
python3 "$HERE_SIM/audit_cnpc_gui_style.py"
python3 "$HERE_SIM/audit_forge_gui_backend.py"
python3 "$HERE_SIM/audit_ultra_spar_card_split.py"
python3 "$HERE_SIM/audit_ultra_actions.py"
python3 "$HERE_SIM/audit_ultra_style_codes.py" "$JAR"
python3 "$HERE_SIM/audit_no_section_sign.py"
python3 "$HERE_SIM/audit_scaling_sync.py"
python3 "$HERE_SIM/audit_features.py"
python3 "$HERE_SIM/audit_command_humanization.py"
python3 "$HERE_SIM/audit_prestige_need_ladder.py"
python3 "$HERE_SIM/audit_prestige_gui_flow.py"
python3 "$HERE_SIM/audit_gui_menus_comprehensive.py"
python3 "$HERE_SIM/audit_gui_tp_copy.py"
python3 "$HERE_SIM/audit_ship_manifest.py"
python3 "$HERE_SIM/audit_branch_manifest_matrix.py"
python3 "$HERE_SIM/audit_gui_tooltips.py"
python3 "$HERE_SIM/audit_form_bands.py"
python3 "$HERE_SIM/validate_tier_costs.py"
python3 "$HERE_SIM/audit_tier_level_matrix.py"
python3 "$HERE_SIM/validate_scaling.py"
python3 "$HERE_SIM/simulate_build_matrix.py" --check
python3 "$HERE_SIM/audit_concept.py"
if [[ -f "$ROOT/mods/LegacyMechanics-${VERSION}.jar" ]]; then
  python3 "$HERE_SIM/audit_gui_abi.py"
else
  echo "WARN: LegacyMechanics-${VERSION}.jar missing — skip GUI ABI audit" >&2
fi
