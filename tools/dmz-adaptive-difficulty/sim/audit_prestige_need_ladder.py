#!/usr/bin/env python3
"""Prestige Need follows Overhaul (4.7.2), mirroring the server rules.

* The requirement is the player's level cap as Overhaul has it: LM's
  DmzRevampPrestigeCapMixin answers Overhaul's levelCap with the base cap LM pins (100k),
  and PrestigeSystem.requiredLevel reads it back through DmzRevampPrestigeBridge.levelCap.
  Completed and held counts do not change it; there are no breakthroughs.
* The gate is level >= cap.
* The most prestiges a player can hold is revamp's Prestige.maxPrestigeCount
  (config/dmzrevamp/LevelingRevamp.json), 10 when dmzrevamp is absent.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
SRC = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty"
BASE_CAP = 100_000
FALLBACK_MAX_HELD = 10


def need(completed: int, held: int, cap: int = BASE_CAP) -> int:
    """Requirement for the next prestige: the cap, whatever was completed or is held."""
    return cap


def meets(level: int, cap: int) -> bool:
    return level >= cap


def clamp_held(held: int, max_held: int) -> int:
    return max(0, min(max(0, max_held), held))


def revamp_max_prestige() -> int:
    path = REPO / "config" / "dmzrevamp" / "LevelingRevamp.json"
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return max(1, int(data["Prestige"]["maxPrestigeCount"]))
    except Exception:
        return FALLBACK_MAX_HELD


def main() -> None:
    errors = []
    for c in (0, 1, 3, 4, 8, 20):
        for h in (0, 1, 3, 9):
            if need(c, h) != BASE_CAP:
                errors.append(f"completed {c} held {h}: need should be the cap {BASE_CAP}")
    if not meets(BASE_CAP, BASE_CAP) or meets(BASE_CAP - 1, BASE_CAP):
        errors.append("gate must be level at or above the cap")
    rmax = revamp_max_prestige()
    if clamp_held(rmax + 5, rmax) != rmax or clamp_held(-1, rmax) != 0:
        errors.append(f"held must clamp to revamp's maxPrestigeCount {rmax}")
    if clamp_held(15, 20) != 15 or clamp_held(15, 10) != 10:
        errors.append("held clamp must follow a changed maxPrestigeCount")

    # The Java has to match the model.
    ps = (SRC / "progression/shop/PrestigeSystem.java").read_text(encoding="utf-8")
    req = ps.split("public static int requiredLevel(ServerPlayer player) {", 1)[-1].split("\n    }\n", 1)[0]
    if "DmzRevampPrestigeBridge.levelCap(data)" not in req or re.search(r"getCompleted|heldCount|NeedFloor", req):
        errors.append("PrestigeSystem.requiredLevel must read the Overhaul cap and nothing else")
    if "LmOverhaulCapMath.configuredMaxPrestigeCount()" not in ps or "MAX_HELD" in ps:
        errors.append("PrestigeSystem.maxHeld must read revamp's maxPrestigeCount")
    cap_math = (SRC / "progression/LmOverhaulCapMath.java").read_text(encoding="utf-8")
    over = cap_math.split("public static int overhaulLevelCap(StatsData data) {", 1)[-1].split("\n    }\n", 1)[0]
    if over.strip() != "return OVERHAUL_LEVEL_CAP;":
        errors.append("the cap LM hands Overhaul's levelCap must be the base cap (no per-player math)")
    if "return LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE;" not in cap_math:
        errors.append("configuredMaxPrestigeCount must fall back to OVERHAUL_MAX_PRESTIGE (10)")
    cfg_bridge = (SRC / "progression/bridge/DmzRevampConfigBridge.java").read_text(encoding="utf-8")
    if "maxPrestigeCount" in cfg_bridge.split("public static void onServerStarting()", 1)[-1]:
        errors.append("LM must not overwrite revamp's maxPrestigeCount on boot")

    if errors:
        print("FAIL prestige need follows Overhaul:")
        for e in errors:
            print(" ", e)
        raise SystemExit(1)
    print(f"OK prestige need is the Overhaul cap ({BASE_CAP}); most held = revamp maxPrestigeCount ({rmax})")


if __name__ == "__main__":
    main()
