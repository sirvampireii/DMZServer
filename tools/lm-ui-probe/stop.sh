#!/usr/bin/env bash
# Stop a running rig matrix or run completely (test only):
#   bash tools/lm-ui-probe/stop.sh
# Stopping only the outer shell of a background matrix leaves matrix.sh running, and its next stage then
# starts while you start another run. This stops the matrix script, the stage script, and every server,
# client and command feed a run started (-Dlmrig.run=...), then clears the locks. Needs RIG_SERVER.
set -u
: "${RIG_SERVER:?set RIG_SERVER}"
for f in "$RIG_SERVER/.rig-matrix.lock" "$RIG_SERVER/.rig.lock"; do
  pid="$(cat "$f" 2>/dev/null || true)"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null && echo "stopped $(basename "$f" .lock) pid $pid"
  fi
  rm -f "$f"
done
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.CommandLine -match 'lmrig\\.run=|uiprobe\\.server=|tail(\\.exe)?\"? -f .*cmd-|lm-ui-probe[/\\\\](matrix|run)\\.sh' -and \$_.ProcessId -ne \$PID } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue; 'stopped ' + \$_.ProcessId + ' ' + \$_.Name }"
echo "rig stopped"
