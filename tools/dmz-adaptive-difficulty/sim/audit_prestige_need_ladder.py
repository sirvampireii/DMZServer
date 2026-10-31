#!/usr/bin/env python3
"""Prestige Need is Overhaul's flat level cap (dmzrevamp 2.2.4, LM 4.7.14), mirroring the server rules.

* The requirement is the level cap as Overhaul has it: PrestigeSystem.requiredLevel reads
  PrestigeSystem.configuredLevelMaximum() through DmzRevampPrestigeBridge.levelCap. LM does not
  override or recompute it.
* dmzrevamp 2.2.4 has one cap for everyone (levelsAndAttributes.maxLevel, LM pins 150k). There is no
  per-prestige ladder: 2.2.4 removed levelCap, initialLevelCap and maxPrestigeCount, and LM must not
  rebuild one.
* The gate is level >= cap. Without dmzrevamp the cap is 100k.
* The most prestiges a player can hold is LM's own 20 (2.2.4 has no count cap).
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
SRC = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty"
PIN_MAX = 150_000
NO_REVAMP_CAP = 100_000
MAX_HELD = 20


def meets(level: int, cap: int) -> bool:
    return level >= cap


def clamp_held(held: int, max_held: int) -> int:
    return max(0, min(max(0, max_held), held))


def main() -> None:
    errors = []
    if not meets(PIN_MAX, PIN_MAX) or meets(PIN_MAX - 1, PIN_MAX):
        errors.append("gate must be level at or above the cap")
    if clamp_held(MAX_HELD + 5, MAX_HELD) != MAX_HELD or clamp_held(-1, MAX_HELD) != 0:
        errors.append(f"held must clamp to {MAX_HELD}")

    cfg = json.loads((REPO / "config/dmzrevamp/LevelingRevamp.json").read_text(encoding="utf-8"))
    if cfg.get("levelsAndAttributes", {}).get("maxLevel") != PIN_MAX:
        errors.append(f"LevelingRevamp.json maxLevel should be {PIN_MAX}")
    if "initialLevelCap" in cfg.get("Prestige", {}) or "maxPrestigeCount" in cfg.get("Prestige", {}):
        errors.append("LevelingRevamp.json mirror still has 2.2.3 ladder keys")

    # The Java has to match the model.
    ps = (SRC / "progression/shop/PrestigeSystem.java").read_text(encoding="utf-8")
    req = ps.split("public static int requiredLevel(ServerPlayer player) {", 1)[-1].split("\n    }\n", 1)[0]
    if "DmzRevampPrestigeBridge.levelCap(data)" not in req or re.search(r"getCompleted|heldCount|NeedFloor", req):
        errors.append("PrestigeSystem.requiredLevel must read the Overhaul cap and nothing else")
    if "LmOverhaulCapMath.configuredMaxPrestigeCount()" not in ps or "MAX_HELD" in ps:
        errors.append("PrestigeSystem.maxHeld must read configuredMaxPrestigeCount")
    cap_math = (SRC / "progression/LmOverhaulCapMath.java").read_text(encoding="utf-8")
    over = cap_math.split("public static int overhaulLevelCap(StatsData data) {", 1)[-1].split("\n    }\n", 1)[0]
    if over.strip() != "return levelCap(data);":
        errors.append("overhaulLevelCap(data) must be Overhaul's cap (read through the bridge)")
    flat = cap_math.split("public static int overhaulLevelCap() {", 1)[-1].split("\n    }\n", 1)[0]
    if '.getMethod("configuredLevelMaximum")' not in flat or "return OVERHAUL_LEVEL_CAP;" not in flat:
        errors.append("overhaulLevelCap() must read configuredLevelMaximum, and stay 100k without dmzrevamp")
    for gone in ("ladderCap", "LADDER_STEPS", "OVERHAUL_INITIAL_LEVEL_CAP", "pinPrestigeCap",
                 '"initialLevelCap"', '"maxPrestigeCount"'):
        if gone in cap_math:
            errors.append(f"LmOverhaulCapMath must not keep the ladder ({gone})")
    if f"OVERHAUL_ABSOLUTE_LEVEL_CAP = PrestigePointsSystem.ABSOLUTE_LEVEL_CAP" not in cap_math:
        errors.append("maxLevel pin must be the absolute cap")
    if "return LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE;" not in cap_math:
        errors.append(f"most held must be LM's {MAX_HELD}")
    mixin = (SRC / "mixin/DmzRevampPrestigeCapMixin.java").read_text(encoding="utf-8")
    if '@Inject(method = "levelCap"' in mixin or '@Inject(method = "hexStatReference"' in mixin:
        errors.append("LM must not hook levelCap / hexStatReference (gone in dmzrevamp 2.2.4)")
    if (SRC / "mixin/DmzRevampPrestigeLadderMixin.java").exists():
        errors.append("DmzRevampPrestigeLadderMixin must be gone")
    bridge = (SRC / "progression/bridge/DmzRevampPrestigeBridge.java").read_text(encoding="utf-8")
    if "return LmOverhaulCapMath.overhaulLevelCap();" not in bridge or "PrestigeSystem.levelCap(" in bridge:
        errors.append("the bridge must read the flat cap, not levelCap")

    if errors:
        print("FAIL prestige need follows Overhaul:")
        for e in errors:
            print(" ", e)
        raise SystemExit(1)
    print(f"OK prestige need is Overhaul's flat level cap ({PIN_MAX:,}, configuredLevelMaximum); no ladder; "
          f"most held = {MAX_HELD}; 100k without dmzrevamp")


if __name__ == "__main__":
    main()
