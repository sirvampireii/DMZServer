#!/usr/bin/env python3
"""Prestige Need follows Overhaul's native cap ladder (4.7.2), mirroring the server rules.

* The requirement is the player's level cap as Overhaul has it: PrestigeSystem.requiredLevel reads
  Overhaul's native PrestigeSystem.levelCap through DmzRevampPrestigeBridge.levelCap. LM does not
  override or recompute it.
* Overhaul's cap rises with its prestige count (LM sets it from held):
  floor(initialLevelCap + count * maxLevel / maxPrestigeCount), capped at maxLevel.
  LM pins initialLevelCap 50k and maxLevel 150k, so with max count 10: 50k, 65k, 80k, 95k,
  110k, 125k, 140k, then 150k from 7 held.
* The gate is level >= cap. Without dmzrevamp the cap is 100k.
* The most prestiges a player can hold is revamp's Prestige.maxPrestigeCount
  (config/dmzrevamp/LevelingRevamp.json), 10 when dmzrevamp is absent.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
SRC = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty"
PIN_INITIAL = 50_000
PIN_MAX = 150_000
NO_REVAMP_CAP = 100_000
FALLBACK_MAX_HELD = 10


def overhaul_cap(count: int, initial: int = PIN_INITIAL, max_level: int = PIN_MAX, max_count: int = 10) -> int:
    """Overhaul's native PrestigeSystem.levelCap."""
    c = min(max(0, count), max_count)
    if c >= max_count:
        return max_level
    step = initial / max_count + (max_level - initial) / max_count
    return int(min(max_level, math.floor(initial + c * step)))


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
    want = [50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000, 150_000, 150_000, 150_000, 150_000]
    for count, w in enumerate(want):
        if overhaul_cap(count) != w:
            errors.append(f"{count} held: need should be {w}, got {overhaul_cap(count)}")
    # A different maxPrestigeCount changes the ladder (e.g. 5: 50k, 80k, 110k, 140k, then 150k).
    if [overhaul_cap(c, max_count=5) for c in range(6)] != [50_000, 80_000, 110_000, 140_000, 150_000, 150_000]:
        errors.append("ladder must follow maxPrestigeCount (5 -> 50k, 80k, 110k, 140k, 150k)")
    if not meets(65_000, 65_000) or meets(64_999, 65_000):
        errors.append("gate must be level at or above the cap")
    rmax = revamp_max_prestige()
    if clamp_held(rmax + 5, rmax) != rmax or clamp_held(-1, rmax) != 0:
        errors.append(f"held must clamp to revamp's maxPrestigeCount {rmax}")

    # The Java has to match the model.
    ps = (SRC / "progression/shop/PrestigeSystem.java").read_text(encoding="utf-8")
    req = ps.split("public static int requiredLevel(ServerPlayer player) {", 1)[-1].split("\n    }\n", 1)[0]
    if "DmzRevampPrestigeBridge.levelCap(data)" not in req or re.search(r"getCompleted|heldCount|NeedFloor", req):
        errors.append("PrestigeSystem.requiredLevel must read the Overhaul cap and nothing else")
    if "LmOverhaulCapMath.configuredMaxPrestigeCount()" not in ps or "MAX_HELD" in ps:
        errors.append("PrestigeSystem.maxHeld must read revamp's maxPrestigeCount")
    cap_math = (SRC / "progression/LmOverhaulCapMath.java").read_text(encoding="utf-8")
    over = cap_math.split("public static int overhaulLevelCap(StatsData data) {", 1)[-1].split("\n    }\n", 1)[0]
    if over.strip() != "return levelCap(data);":
        errors.append("overhaulLevelCap(data) must be Overhaul's native cap (read through the bridge)")
    if f"OVERHAUL_INITIAL_LEVEL_CAP = {PIN_INITIAL:_}" not in cap_math \
            or "initial.setInt(prestige, OVERHAUL_INITIAL_LEVEL_CAP);" not in cap_math:
        errors.append("initialLevelCap must be pinned to 50,000")
    if "return OVERHAUL_LEVEL_CAP;" not in cap_math.split("public static int overhaulLevelCap() {", 1)[-1].split("\n    }\n", 1)[0]:
        errors.append("without dmzrevamp the cap must stay 100k")
    mixin = (SRC / "mixin/DmzRevampPrestigeCapMixin.java").read_text(encoding="utf-8")
    if '@Inject(method = "levelCap"' in mixin:
        errors.append("LM must not override Overhaul's levelCap (the native ladder is the cap)")
    bridge = (SRC / "progression/bridge/DmzRevampPrestigeBridge.java").read_text(encoding="utf-8")
    if "PrestigeSystem.levelCap(data)" not in bridge:
        errors.append("the bridge must read Overhaul's levelCap")
    cfg_bridge = (SRC / "progression/bridge/DmzRevampConfigBridge.java").read_text(encoding="utf-8")
    if "maxPrestigeCount" in cfg_bridge.split("public static void onServerStarting()", 1)[-1]:
        errors.append("LM must not overwrite revamp's maxPrestigeCount on boot")

    if errors:
        print("FAIL prestige need follows Overhaul:")
        for e in errors:
            print(" ", e)
        raise SystemExit(1)
    print("OK prestige need is Overhaul's native cap: 50k, 65k, 80k ... 140k, then 150k; "
          f"most held = revamp maxPrestigeCount ({rmax}); 100k without dmzrevamp")


if __name__ == "__main__":
    main()
