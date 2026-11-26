#!/usr/bin/env python3
"""Forge command trees: one canonical path per feature, all under /lm (4.7.88: no shortcuts, no other roots)."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MECH = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.java"
PROG = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java"
BRIDGE = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/command/MohistCommandBridge.java"

REQUIRED_LM_ADMIN = [
    "help",
    "reload",
    "clear",
    "character",
    "cooldown",
    "syslog",
    "inspect",
]

FORBIDDEN_LITERALS = [
    'm_82127_("legacymechanics")',
    'm_82127_("prog")',
    'm_82127_("prestigeadmin")',
    'm_82127_("cleardragons")',
    'm_82127_("spawndragon")',
    'm_82127_("killdragons")',
    'm_82127_("spawndragon")',
    'register(build("legacymechanics"))',
    'List.of("legacymechanics")',
    'List.of("prog")',
    'List.of("prestigeadmin")',
    'List.of("sparring")',
]

ACCESS = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty/command/CommandAccess.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> int:
    errors: list[str] = []
    mech = read(MECH)
    prog = read(PROG)
    bridge = read(BRIDGE)

    for token in REQUIRED_LM_ADMIN:
        if token not in mech:
            errors.append(f"MechanicsCommands missing: {token!r}")

    if "clearCharacterCooldowns" not in mech:
        errors.append("MechanicsCommands must call PlayerDataClear.clearCharacterCooldowns")
    if "stafffree" in mech and "adminStaffFree" in mech:
        errors.append("stafffree belongs under /difficulty admin only (remove from /lm admin)")

    if "LmAdminArgCoalesce" not in bridge:
        errors.append("MohistCommandBridge must use LmAdminArgCoalesce")

    if "migrate-cnpc" in mech:
        errors.append("/lm admin migrate-cnpc was retired in 4.7.88")
    if 'm_82127_("padmin")' in prog:
        errors.append("4.7.88: the padmin root is gone; prestige admin lives at /lm admin prestige")
    if 'PrestigeAdminCommandTree.attach(Commands.m_82127_("prestige"))' not in prog:
        errors.append("ProgressionCommands must attach PrestigeAdminCommandTree as /lm admin prestige")
    if "PrestigeAdminCommandTree" not in prog:
        errors.append("ProgressionCommands must use PrestigeAdminCommandTree")
    if 'm_82127_("admin")' in prog and "PrestigeAdminCommandTree.attach(Commands.m_82127_(\"admin\"))" in prog:
        errors.append("Do not attach PrestigeAdminCommandTree under /prestige admin (use /padmin)")

    for forbidden in FORBIDDEN_LITERALS:
        hay = mech + prog + bridge
        if forbidden in hay:
            errors.append(f"Removed shortcut/duplicate still present: {forbidden}")

    if 'm_82127_("lm")' not in read(ACCESS) or "CommandAccess.lmRoot()" not in mech:
        errors.append("MechanicsCommands must register /lm through CommandAccess.lmRoot()")
    if bridge.count("registerOne(server, commandMap") != 1:
        errors.append("4.7.88: the Mohist bridge forwards /lm only")

    out = ROOT / "sim/out/lm-admin-command-audit.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    lines = ["# LM command tree audit", ""]
    if errors:
        lines.append("FAIL")
        for e in errors:
            lines.append(f"- {e}")
        out.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print("\n".join(errors), file=sys.stderr)
        return 1
    lines.append("PASS — canonical command trees, all under /lm (no shortcuts)")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("PASS — lm admin command audit")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
