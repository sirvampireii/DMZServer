#!/usr/bin/env python3
"""Ensure player menus route Ultra-only; no CNPC/Bukkit fallback."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/gui"
BACKEND = GUI / "GuiBackend.java"


def main() -> int:
    errors: list[str] = []

    menu_files = sorted(p for p in GUI.glob("*Menu.java") if not p.name.endswith("ChatMenu.java"))
    for path in menu_files:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT)
        if "CmiGuiBridge" in text or "BukkitGuiBridge" in text:
            errors.append(f"{rel}: player menu must not call Bukkit/CMI bridge")
        if "gui.cnpc" in text or "CnpcLmGui" in text:
            errors.append(f"{rel}: must not call CNPC menus")
        if "UltraGui" not in text:
            # Chat menus are text-only; Skills/Progression/etc. must open Ultra.
            if path.name.endswith("Menu.java") and "Chat" not in path.name:
                if "UltraGui" not in text:
                    errors.append(f"{rel}: expected UltraGui open path")

    backend = BACKEND.read_text(encoding="utf-8")
    if "ULTRA" not in backend:
        errors.append("GuiBackend.java: missing ULTRA backend")
    if "fromConfig()" not in backend:
        errors.append("GuiBackend.java: missing fromConfig()")
    # fromConfig must always resolve to ULTRA (retired values coerced).
    if "yield ULTRA" not in backend and "-> ULTRA" not in backend:
        errors.append("GuiBackend.java: fromConfig must yield ULTRA")
    if re.search(r'case\s+"cnpc".*->\s*CNPC', backend, re.S):
        errors.append("GuiBackend.java: cnpc must not map to CNPC (coerce to ultra)")

    config = (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.java").read_text(
        encoding="utf-8"
    )
    if 'guiBackend = "ultra"' not in config and 'guiBackend = "ultra"' not in config:
        errors.append("DifficultyConfig: default guiBackend must be ultra")
    if 'case "cnpc"' in config or "case \"cnpc\"" in config:
        # Must coerce cnpc → ultra in migration switch.
        if '-> "ultra"' not in config and "-> \"ultra\"" not in config:
            errors.append("DifficultyConfig: cnpc must migrate to ultra")

    net = (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/LmGuiNetwork.java").read_text(
        encoding="utf-8"
    )
    if "isVersionAtLeast" not in net or "1.2.5" not in net:
        errors.append("LmGuiNetwork: must compare KaiPath version >= 1.2.5")
    if 'equals("1.2.5")' in net or "equals(\"1.2.5\")" in net:
        errors.append("LmGuiNetwork: must not use exact equals(\"1.2.5\") for KaiPath")
    if "CNPC/chat fallbacks" in net:
        errors.append("LmGuiNetwork: must not advertise CNPC fallbacks")

    mods_toml = (ROOT / "src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8")
    if 'modId = "kaipath"' not in mods_toml:
        errors.append("mods.toml: missing soft dependency on kaipath")
    elif "[1.2.5,)" not in mods_toml:
        errors.append("mods.toml: kaipath versionRange must be [1.2.5,)")

    ultra = GUI / "ultra" / "UltraGui.java"
    if not ultra.is_file():
        errors.append("Missing gui/ultra/UltraGui.java")
    else:
        utext = ultra.read_text(encoding="utf-8")
        if "preferUltra" not in utext or "clientHasKaiPath" not in utext:
            errors.append("UltraGui.java: must check KaiPath presence")
        if "CnpcLmGui" in utext or "gui.cnpc" in utext:
            errors.append("UltraGui.java: must not fall back to CNPC")
        if "Install KaiPath" not in utext:
            errors.append("UltraGui.java: must tell players to install KaiPath when missing")

    inspect = GUI / "ForgeInspectGui.java"
    if inspect.is_file():
        itext = inspect.read_text(encoding="utf-8")
        if "CnpcLmGui" in itext or "gui.cnpc" in itext:
            errors.append("ForgeInspectGui.java: must open Ultra, not CNPC")
        if "UltraGui" not in itext:
            errors.append("ForgeInspectGui.java: missing UltraGui open path")

    char = GUI / "ultra" / "UltraCharacterScreens.java"
    if char.is_file():
        ctext = char.read_text(encoding="utf-8")
        for needle in ("Change race", "Change class", "Customize model"):
            if needle not in ctext:
                errors.append(f"UltraCharacterScreens.java: missing '{needle}' button")
    else:
        errors.append("Missing UltraCharacterScreens.java")

    open_pkt = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/net/gui/GuiOpenPacket.java"
    if open_pkt.is_file():
        otext = open_pkt.read_text(encoding="utf-8")
        if "StandardCharsets.UTF_8" not in otext:
            errors.append("GuiOpenPacket.java: must encode/decode with StandardCharsets.UTF_8")
        if "writeUtf8" not in otext or "readUtf8" not in otext:
            errors.append("GuiOpenPacket.java: missing writeUtf8/readUtf8 helpers")
    else:
        errors.append("Missing GuiOpenPacket.java")

    mechanics = (ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.java").read_text(
        encoding="utf-8"
    )
    if "CmiGuiBridge" in mechanics or "BukkitGuiBridge" in mechanics:
        errors.append("MechanicsCommands.java: /lm must not require Bukkit GUI plugin")
    if "gui.cnpc" in mechanics or "CnpcLmGui" in mechanics:
        errors.append("MechanicsCommands.java: must not open CNPC menus")
    if "§" in mechanics:
        errors.append("MechanicsCommands.java: remaining section-sign literals (use UltraStyle)")

    # Bukkit ForgeBridge must not loadClass deleted gui.cnpc (CNFE for players).
    bukkit_bridge = (
        ROOT.parent.parent
        / "tools/dmz-adaptive-difficulty-gui/src/main/java/com/dbzlegacy/adaptivedifficulty/bukkit/ForgeBridge.java"
    )
    if not bukkit_bridge.is_file():
        bukkit_bridge = (
            ROOT.parent
            / "dmz-adaptive-difficulty-gui/src/main/java/com/dbzlegacy/adaptivedifficulty/bukkit/ForgeBridge.java"
        )
    if bukkit_bridge.is_file():
        btext = bukkit_bridge.read_text(encoding="utf-8")
        if "gui.cnpc.CnpcLmGui" in btext:
            errors.append("ForgeBridge.java: must not loadClass gui.cnpc.CnpcLmGui")
        if "gui.ultra.UltraGui" not in btext:
            errors.append("ForgeBridge.java: must open UltraGui for menus")
        if 'return "cnpc"' in btext:
            errors.append('ForgeBridge.guiBackend must not return "cnpc"')

    for name in ("DifficultyMenu", "RivalMenu", "SparMenu", "PrestigeMenu", "CharacterServicesMenu"):
        path = GUI / f"{name}.java"
        if not path.is_file():
            errors.append(f"Missing {name}.java")
            continue
        body = path.read_text(encoding="utf-8")
        if re.search(r"CmiGuiBridge\s*\.\s*open", body):
            errors.append(f"{name}.java: still opens CmiGuiBridge")

    ultra_actions = ROOT / "sim" / "audit_ultra_actions.py"
    if ultra_actions.is_file():
        r = subprocess.run([sys.executable, str(ultra_actions)], check=False)
        if r.returncode != 0:
            errors.append("audit_ultra_actions.py failed")
    else:
        errors.append("missing sim/audit_ultra_actions.py")

    print("=== Forge GUI backend (Ultra-only, no CNPC) ===")
    for e in errors:
        print(f"  FAIL  {e}")
    if errors:
        print(f"\n{len(errors)} error(s)")
        return 1
    print("  OK  *Menu.java → Ultra; KaiPath required; UTF-8 packets; Character Home buttons")
    return 0


if __name__ == "__main__":
    sys.exit(main())
