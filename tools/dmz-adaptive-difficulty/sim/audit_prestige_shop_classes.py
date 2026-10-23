#!/usr/bin/env python3
"""Fail when any prestige-shop (4.6.228.1) delta class in the jar is not
byte-identical to the committed reference set.

reference/prestige-shop-228.1/ holds the 25 audited .class files that the
live 4.6.228.1 jar ships: 15 new shop classes (PrestigeCustomShop / PrestigeShopConfig /
PrestigeShopHooks + inners) and 10 modified classes (AdaptiveDifficultyMod, PrestigeAdminCommandTree,
ConfigRegistry, ConfigEditor, ConfigFieldHelp, ProgressionGuiApi, UltraPrestigeScreens,
GodKiEnhancementShop, PrestigePointsSystem + $SkillOffer).

Unlike the weapon hotpath guard (which OVERLAYS audited bytecode because those classes have no usable
repo source), this is a pure ASSERTION: the authored 228.1 source is compiled from src/, and this
script verifies the compiled output is byte-identical to the live 228.1 reference. Any drift — a
half-integrated edit, a dropped class, a toolchain mismatch — fails loudly.

Keep the CLASS list in sync with sim/extract_pristige_shop_reference.py.
"""
from __future__ import annotations

import hashlib
import os
import sys
import zipfile
from pathlib import Path

# 15 NEW shop classes + 10 MODIFIED classes = the complete class-level delta
# between repo 4.6.228 and live 4.6.228.1 (resources MANIFEST.MF / mods.toml excluded:
# they legitimately change per build / version bump).
CLASS_PATHS = [
    # new
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeCustomShop.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeCustomShop$LineAction.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeCustomShop$Lines.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeCustomShop$PotionWant.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeCustomShop$StatWant.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$CustomEntry.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$CustomShop.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$Exchange.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$FormOffer.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$Forms.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$GodKi.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$SkillShop.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopConfig$TierShop.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeShopHooks.class",
    # modified
    "com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.class",
    "com/dbzlegacy/adaptivedifficulty/command/PrestigeAdminCommandTree.class",
    "com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry.class",
    "com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.class",
    "com/dbzlegacy/adaptivedifficulty/config/editor/ConfigFieldHelp.class",
    "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.class",
    "com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraPrestigeScreens.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/GodKiEnhancementShop.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem$SkillOffer.class",
    "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem.class",
]


def _ref_dir() -> Path:
    override = os.environ.get("LM_SHOP_REF_DIR")
    if override:
        return Path(override)
    return Path(__file__).resolve().parent.parent / "reference" / "prestige-shop-228.1"


def _sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: audit_prestige_shop_classes.py <jar>", file=sys.stderr)
        return 2
    jar = Path(sys.argv[1])
    if not jar.is_file():
        print(f"ERROR: missing jar {jar}", file=sys.stderr)
        return 1
    if len(CLASS_PATHS) != 25 or len(set(CLASS_PATHS)) != 25:
        print("ERROR: internal — CLASS_PATHS must be 25 unique entries", file=sys.stderr)
        return 1

    ref_dir = _ref_dir()
    if not ref_dir.is_dir():
        print(f"ERROR: missing reference dir {ref_dir}", file=sys.stderr)
        return 1

    # Reference-set completeness: every class must have a committed reference file.
    ref_missing = [c for c in CLASS_PATHS if not (ref_dir / c).is_file()]
    if ref_missing:
        print("ERROR: reference set is incomplete (re-extract from the audited live 228.1 jar):",
              file=sys.stderr)
        for c in ref_missing:
            print(f"  missing reference {c}", file=sys.stderr)
        return 1

    built_missing: list[str] = []
    mismatched: list[str] = []
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        print(f"{'built sha256':16} {'ref sha256':16}  verdict  class")
        print("-" * 104)
        for cls in CLASS_PATHS:
            ref_sha = _sha((ref_dir / cls).read_bytes())
            if cls not in names:
                built_missing.append(cls)
                print(f"{'-':16} {ref_sha[:16]:16}  MISSING  {cls}")
                continue
            built_sha = _sha(zf.read(cls))
            if built_sha == ref_sha:
                print(f"{built_sha[:16]:16} {ref_sha[:16]:16}  OK       {cls}")
            else:
                mismatched.append(cls)
                print(f"{built_sha[:16]:16} {ref_sha[:16]:16}  DRIFT    {cls}")

    print("-" * 104)
    total = len(CLASS_PATHS)
    ok = total - len(built_missing) - len(mismatched)
    print(f"{ok}/{total} byte-identical to reference/{ref_dir.name}")
    if built_missing or mismatched:
        print(
            f"ERROR: prestige-shop verify FAILED — {len(built_missing)} missing, "
            f"{len(mismatched)} drifted vs reference set",
            file=sys.stderr,
        )
        return 1
    print(f"PASS — all {total} prestige-shop (228.1) classes byte-identical to reference")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
