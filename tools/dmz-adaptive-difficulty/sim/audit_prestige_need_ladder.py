#!/usr/bin/env python3
"""Prestige Need follows the held count; the level cap stays flat (LM 4.7.15), mirroring the server rules.

* The level cap is Overhaul's flat PrestigeSystem.configuredLevelMaximum() (dmzrevamp 2.2.4,
  levelsAndAttributes.maxLevel, LM pins 150k). It does not change with prestige.
* The requirement to prestige (PrestigeSystem.requiredLevel) is LmOverhaulCapMath.prestigeRequirement
  of the held count: 50k, 65k, 80k, 95k, 110k, 125k, 140k for 0-6 held, then 150k, never above the cap.
  Overhaul's own prestige button uses the same value (blocksOverhaulPrestige in the cap mixin).
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


def need(held: int, cap: int = PIN_MAX) -> int:
    ladder = [50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000]
    h = max(0, held)
    return min(ladder[h] if h < len(ladder) else 150_000, cap)


def main() -> None:
    errors = []
    want = [50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000] + [150_000] * 13
    if [need(h) for h in range(20)] != want:
        errors.append("need ladder must be 50k..140k for 0-6 held, then 150k")
    if need(5, NO_REVAMP_CAP) != NO_REVAMP_CAP:
        errors.append("need must never be above the cap")
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
    if "LmOverhaulCapMath.prestigeRequirement(" not in req or "getHeldWallet(player)" not in req \
            or re.search(r"getCompleted|NeedFloor", req):
        errors.append("PrestigeSystem.requiredLevel must be prestigeRequirement of the held wallet")
    if "{50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000}" not in (SRC / "progression/LmOverhaulCapMath.java").read_text(encoding="utf-8"):
        errors.append("LmOverhaulCapMath must carry the need ladder 50k..140k")
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
            errors.append(f"LmOverhaulCapMath must not keep the old cap ladder ({gone})")
    if "LmOverhaulCapMath.blocksOverhaulPrestige(count, data.getLevel())" not in (SRC / "mixin/DmzRevampPrestigeCapMixin.java").read_text(encoding="utf-8"):
        errors.append("Overhaul's prestige gate must use the same need ladder")
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
    print(f"OK prestige need 50k, 65k ... 140k, then 150k by held count (both routes); level cap flat "
          f"{PIN_MAX:,}; most held = {MAX_HELD}; 100k without dmzrevamp")


if __name__ == "__main__":
    main()
