#!/usr/bin/env bash
# Remove duplicate/wrong LM files on live (active mods/plugins only).
# Keeps recycle_bin history unless LM_PURGE_RECYCLE=1.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -f "$ROOT/live-sftp.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/live-sftp.env"
fi

HOST="${LIVE_SFTP_HOST:-}"
PORT="${LIVE_SFTP_PORT:-2022}"
USER="${LIVE_SFTP_USER:-}"
PASS="${LIVE_SFTP_PASS:-${SSHPASS:-}}"
REMOTE_MODS="${LIVE_SFTP_MODS:-mods}"
REMOTE_PLUGINS="${LIVE_SFTP_PLUGINS:-plugins}"
RECYCLE="${LIVE_SFTP_RECYCLE:-recycle_bin}"

if [[ -z "$HOST" || -z "$USER" || -z "$PASS" ]]; then
  echo "Live SFTP credentials missing (live-sftp.env)." >&2
  exit 1
fi

VERSION_JAVA="$ROOT/tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java"
DEFAULT_VER="$(grep -oP 'public static final String VERSION = "\K[0-9.]+(?=")' "$VERSION_JAVA" || true)"
if [[ -z "$DEFAULT_VER" ]]; then
  echo "Could not read LM version for the keep jar." >&2
  exit 1
fi
KEEP_FORGE="${LM_KEEP_FORGE:-LegacyMechanics-${DEFAULT_VER}.jar}"
KEEP_GUI="${LM_KEEP_GUI:-}"
RECYCLE_ALL_GUI="${LM_RECYCLE_ALL_GUI:-0}"
KEEP_MELEE="${LM_KEEP_MELEE:-dmz_mohist_melee_fix-3.0.0.jar}"

export SSHPASS="$PASS"
SFTP_CMD=(sshpass -e sftp -o StrictHostKeyChecking=accept-new -P "$PORT")

list_names() {
  local dir="$1" pattern="$2"
  printf 'ls -1 %s/%s\n' "$dir" "$pattern" \
    | "${SFTP_CMD[@]}" "$USER@$HOST" 2>/dev/null \
    | rg -o '[^[:space:]]+' | rg "$(basename "$pattern" | sed 's/\*/.*/')" || true
}

echo "LM live cleanup on $USER@$HOST (keep forge=$KEEP_FORGE gui=$KEEP_GUI melee=$KEEP_MELEE)"

DEL_CMDS="mkdir $RECYCLE"$'\n'
while IFS= read -r name; do
  [[ -z "$name" ]] && continue
  base="${name##*/}"
  [[ "$base" == "$KEEP_FORGE" ]] && continue
  case "$base" in
    LegacyMechanics-*.jar|LegacyMechanics-*.jar.pending)
      DEL_CMDS+="rename $REMOTE_MODS/$base $RECYCLE/$base"$'\n'
      ;;
  esac
done < <(list_names "$REMOTE_MODS" 'LegacyMechanics-*')

while IFS= read -r name; do
  [[ -z "$name" ]] && continue
  base="${name##*/}"
  if [[ "$RECYCLE_ALL_GUI" == "1" ]]; then
    case "$base" in
      LegacyMechanicsGUI-*.jar|LegacyMechanicsGUI-*.jar.pending)
        DEL_CMDS+="rename $REMOTE_PLUGINS/$base $RECYCLE/$base"$'\n'
        ;;
    esac
  elif [[ -n "$KEEP_GUI" ]]; then
    [[ "$base" == "$KEEP_GUI" ]] && continue
    case "$base" in
      LegacyMechanicsGUI-*.jar|LegacyMechanicsGUI-*.jar.pending)
        DEL_CMDS+="rename $REMOTE_PLUGINS/$base $RECYCLE/$base"$'\n'
        ;;
    esac
  fi
done < <(list_names "$REMOTE_PLUGINS" 'LegacyMechanicsGUI-*')

while IFS= read -r name; do
  [[ -z "$name" ]] && continue
  base="${name##*/}"
  [[ "$base" == "$KEEP_MELEE" ]] && continue
  case "$base" in
    dmz_mohist_melee_fix-*.jar)
      DEL_CMDS+="rename $REMOTE_MODS/$base $RECYCLE/$base"$'\n'
      ;;
  esac
done < <(list_names "$REMOTE_MODS" 'dmz_mohist_melee_fix-*')

# Stray extracted GUI data (not a valid plugin layout)
DEL_CMDS+="rm $REMOTE_PLUGINS/LegacyMechanicsGUI/gui-tooltips.json"$'\n'
DEL_CMDS+="rmdir $REMOTE_PLUGINS/LegacyMechanicsGUI"$'\n'

if [[ "${LM_PURGE_RECYCLE:-0}" == "1" ]]; then
  echo "LM_PURGE_RECYCLE=1 — listing recycle_bin LM/melee (manual review recommended)." >&2
fi

if [[ "${DEPLOY_LIVE_CONFIRM:-}" != "LIVE" ]]; then
  echo "Set DEPLOY_LIVE_CONFIRM=LIVE to run cleanup." >&2
  exit 1
fi

printf '%s\nbye\n' "$DEL_CMDS" | "${SFTP_CMD[@]}" "$USER@$HOST"
echo "Cleanup done. Active mods/plugins should only have the keep versions above."
