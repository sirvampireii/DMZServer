#!/usr/bin/env python3
"""Prestige GUI + Need wiring audit (Forge ProgressionGuiApi + Ultra screens)."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/dbzlegacy/adaptivedifficulty"

errors: list[str] = []
warnings: list[str] = []


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def ok(name: str, cond: bool, detail: str = "") -> None:
    mark = "OK" if cond else "FAIL"
    line = f"  {mark}  {name}" + (f" — {detail}" if detail and not cond else "")
    print(line)
    if not cond:
        errors.append(name + (f": {detail}" if detail else ""))


def main() -> None:
    print("=== Prestige Need ladder (logic) ===")
    import audit_prestige_need_ladder  # noqa: E402

    try:
        audit_prestige_need_ladder.main()
    except SystemExit as e:
        if e.code:
            errors.append("prestige need ladder sim")

    print("\n=== Forge API (ProgressionGuiApi + PrestigeSystem) ===")
    gui_api = read(SRC / "gui/ProgressionGuiApi.java")
    prestige_sys = read(SRC / "progression/shop/PrestigeSystem.java")
    ok("prestigePlaceholders uses heldCountForNeed", "heldCountForNeed(player)" in gui_api)
    ok("prestigePlaceholders uses requiredLevel(player)", "PrestigeSystem.requiredLevel(player)" in gui_api)
    ok("required_fmt exposed", 'out.put("required_fmt"' in gui_api)

    lines_block = gui_api.split("prestigeLines(", 1)[1].split("handlePrestigeDo", 1)[0]
    pre_switch = lines_block.split("switch (p)", 1)[0]
    ok("main wallet lore has no Cap line before switch", "§7Cap:" not in pre_switch)
    ok("main default shows Need", "required_fmt" in lines_block and "default ->" in lines_block)

    ok("PrestigeSystem need is the Overhaul cap (no veteran branch)",
       "DmzRevampPrestigeBridge.levelCap(data)" in prestige_sys
       and "HELD_GATE_MIN_COMPLETED" not in prestige_sys)
    ok("next requirement shows the cap", 'out.put("next_required", String.valueOf(required))' in gui_api)
    ok("PrestigeSystem heldCountForNeed", "heldCountForNeed" in prestige_sys)

    print("\n=== Ultra Prestige menu ===")
    ultra = read(SRC / "gui/ultra/UltraPrestigeScreens.java")
    ok("Ultra prestige screens exist", "class UltraPrestigeScreens" in ultra)
    ok("CNPC prestige package removed", not (SRC / "gui/cnpc").is_dir())
    ok("shop hub with Skills / Tiers / Effects",
       "openShopHub" in ultra
       and "page:skills" in ultra
       and "page:tiers" in ultra
       and "page:effects" in ultra)
    ok("favor buys for tiers and effects",
       "do:tier:" in ultra and "do:majin" in ultra and "do:mutant" in ultra)
    actions = read(SRC / "gui/ultra/UltraGuiActions.java")
    # reopenPage prestige arm (not dispatch / tab mapping) keeps buyers on tiers/effects.
    ok("reopen stays on tiers/effects after buy",
       'case "tier", "buy_tier", "unlock_tier" -> "tiers"' in actions
       and ('"unmutant", "unbuy_mutant", "remove_mutant" -> "effects"' in actions
            or '-> "effects"' in actions)
       and "buy_majin" in actions)

    print("\n=== Summary ===")
    if errors:
        for e in errors:
            print(f" FAIL {e}")
        sys.exit(1)
    print("  OK  prestige Ultra path")
    sys.exit(0)


if __name__ == "__main__":
    main()
