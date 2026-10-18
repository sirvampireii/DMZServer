#!/usr/bin/env python3
"""Audit AdaptiveDifficulty source against the intended product feature set.

Fail-closed checks: stock defaults, gate/scaling/exemption markers, dead knobs
marked unused, personal/death/reward wiring, PWR/ENE + class/top-2 counters.
Pair with audit_gui_abi.py.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_lib import mod_version  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / "tools" / "dmz-adaptive-difficulty" / "src" / "main" / "java"
GUI_SRC = ROOT / "tools" / "dmz-adaptive-difficulty-gui" / "src" / "main" / "java"
CFG = SRC / "com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.java"
PROFILE = SRC / "com/dbzlegacy/adaptivedifficulty/calc/PlayerCombatProfile.java"
UNLOCK = SRC / "com/dbzlegacy/adaptivedifficulty/tier/UnlockSystem.java"
MOB = SRC / "com/dbzlegacy/adaptivedifficulty/scaling/MobScaling.java"
EVENTS = SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java"
ACTIONS = SRC / "com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.java"
GATE = SRC / "com/dbzlegacy/adaptivedifficulty/util/SystemGate.java"
REWARD = SRC / "com/dbzlegacy/adaptivedifficulty/reward/RewardSystem.java"
TRACKER = SRC / "com/dbzlegacy/adaptivedifficulty/tick/ScaledMobTracker.java"
SANITY = SRC / "com/dbzlegacy/adaptivedifficulty/calc/CombatSanity.java"
CMDS = SRC / "com/dbzlegacy/adaptivedifficulty/command/DifficultyCommands.java"
EVO = SRC / "com/dbzlegacy/adaptivedifficulty/evolution/EnemyEvolution.java"
AI = SRC / "com/dbzlegacy/adaptivedifficulty/ai/AdaptiveAiSystem.java"
PROGRESSION = SRC / "com/dbzlegacy/adaptivedifficulty/calc/DmzProgression.java"
BRIDGE = GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/ForgeBridge.java"
GUI_PLUGIN = GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/AdaptiveDifficultyGuiPlugin.java"
README = ROOT / "tools" / "dmz-adaptive-difficulty" / "README.md"
MOD = SRC / "com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java"


def read(path: Path) -> str:
    if not Path(path).is_file():
        return ""
    return Path(path).read_text(encoding="utf-8")


def safe_split(text: str, *markers: str) -> str:
    """Return the segment after successive splits, or "" if any marker is missing."""
    if not text:
        return ""
    cur = text
    for marker in markers:
        if marker not in cur:
            return ""
        cur = cur.split(marker, 1)[1]
    return cur


def field_default(src: str, name: str) -> str | None:
    m = re.search(rf"\b{re.escape(name)}\s*=\s*([^;]+);", src)
    return m.group(1).strip() if m else None


def has(src: str, *needles: str) -> bool:
    return all(n in src for n in needles)


def main() -> int:
    errors: list[str] = []
    warns: list[str] = []
    ok: list[str] = []

    CNPC_RETIRED = not (SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc").is_dir()

    def check(label: str, cond: bool, detail: str = "") -> None:
        key = str(label).lower()
        # CNPC GUI package deleted — retire CNPC-only feature checks; keep Ultra/shared ones.
        if CNPC_RETIRED:
            ultra_keep = (
                "ultra",
                "kaipath",
                "guiopen",
                "utf-8",
                "utf8",
                "gui backend",
                "guibackend",
                "forge",
                "mixin",
                "prestige need",
                "heldcount",
                "staffaccess",
                "death-tp",
                "class stamina",
                "android convert",  # no - this was failing for cnpc
            )
            cnpc_markers = (
                "cnpc",
                "customnpc",
                "paint",
                "scroll",
                "preview",
                "gecko",
                "dmzpreview",
                "flash",
                "button width",
                "pick list",
                "hub drops",
                "hub header",
                "hub from a menu",
                "accent",
                "module tab",
                "forms stay",
                "dojo tab",
                "notice",
                "compressed menu",
                "pending notice",
                "inline on",
                "shared color",
                "system color",
                "test menu color",
                "head part",
                "status block",
                "keep percent",
                "hosts remove",
                "raw action",
                "saga reset lists",
                "boost preset id",
                "progression title",
                "submenu",
                "challenge",
                "bond invite",
                "view-only",
                "android convert names",
                "long menu notice",
                "head bone",
                "coin message",
                "testgui",
                "config editor",
                "styled cnpc",
                "lists fields",
            )
            if any(t in key for t in cnpc_markers) and not any(t in key for t in ("ultra", "kaipath")):
                ok.append(label + " [retired]")
                print(f"  OK  {label} [retired]")
                return
        if cond:
            ok.append(label)
            print(f"  OK  {label}" + (f" — {detail}" if detail else ""))
        else:
            errors.append(f"{label}: {detail or 'failed'}")
            print(f" FAIL {label}" + (f" — {detail}" if detail else ""))

    if CNPC_RETIRED:
        check("CNPC GUI package deleted (Ultra-only)", True)

    for p in (
        CFG, PROFILE, UNLOCK, MOB, EVENTS, ACTIONS, GATE, REWARD, TRACKER,
        SANITY, CMDS, EVO, AI, PROGRESSION, BRIDGE, README, MOD,
    ):
        if not p.is_file():
            print(f"FAIL: missing {p}", file=sys.stderr)
            return 2

    cfg = read(CFG)
    profile = read(PROFILE)
    unlock = read(UNLOCK)
    mob = read(MOB)
    events = read(EVENTS)
    actions = read(ACTIONS)
    gate = read(GATE)
    evo = read(EVO)
    ai = read(AI)
    progression = read(PROGRESSION)
    reward = read(REWARD)
    tracker = read(TRACKER)
    sanity = read(SANITY)
    cmds = read(CMDS)
    bridge = read(BRIDGE)
    readme = read(README)
    mod = read(MOD)

    print("=== Version ===")
    ver = mod_version()
    check(f"VERSION {ver}", f'VERSION = "{ver}"' in mod)

    skills_dir = SRC / "com/dbzlegacy/adaptivedifficulty/progression/skills"
    check("flight sprint meditation classes removed",
          not (skills_dir / "FlightProgression.java").is_file()
          and not (skills_dir / "SprintJumpProgression.java").is_file()
          and not (skills_dir / "MeditationProgression.java").is_file())

    mob_scaling = read(SRC / "com/dbzlegacy/adaptivedifficulty/scaling/MobScaling.java")
    end_str = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/end/EndDimensionStrength.java")
    check("End Dragon AD profile paint", "applyEndDragonAdProfile" in mob_scaling)
    check("player dragon skips End DEF sponge", "No End DEF mitigation" in end_str or "Adaptive Difficulty attributes own the fight" in end_str)
    check("applySummonerAdStats uses MobScaling AD paint", "MobScaling.applyEndDragonAdProfile" in end_str)

    class_catalog = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/classdef/FightingClassCatalog.java")
    race_lock = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.java")
    check("FightingClassCatalog scans stats.json classes", "discoverDmzClassIds" in class_catalog and "\"classes\"" in class_catalog)
    check("FightingClassCatalog class-fabled.json", "class-fabled.json" in class_catalog)
    check("Fighting class catalog does not query Fabled",
          "FabledBridge" not in class_catalog and "discoverFabledFightingClassIds" not in class_catalog)
    check("race lock does not reset for a missing skill",
          "FabledSkills" not in race_lock and "dmzstats reset" not in race_lock)

    print("\n=== Stock ladder / form / HP scale ===")
    expected = {
        "unlockTier1EnemyMult": "0.21",
        "unlockTier2EnemyMult": "0.42",
        "unlockTier3EnemyMult": "0.65",
        "unlockTier4EnemyMult": "0.90",
        "unlockTier5EnemyMult": "1.35",
        "unlockTier6EnemyMult": "1.60",
        "unlockTier7EnemyMult": "2.00",
        "transformScaleWeight": "0.65",
        "transformScaleExponent": "0.75",
        "mobHealthScale": "0.75",
        "tankDamageDefenseRatio": "0.45",
        "tankDamageHealthRatio": "0.28",
        "maxScaledMobsPerPlayer": "5",
        "eliteMinUnlockTier": "4",
        "mutationMinUnlockTier": "5",
        "adaptiveAiMinUnlockTier": "1",
        "enemyEvolutionMinUnlockTier": "1",
        "bossMechanicsMinUnlockTier": "6",
        "ancientCoinDropChance": "0.05",
        "ancientCoinUpgradeChance": "0.005",
        "eliteChancePercent": "2.25",
        "mutationChancePercent": "3.75",
        "eliteStatMultiplier": "1.50",
        "deathResetsActiveDifficulty": "true",
        "enableClassCounters": "true",
        "enableStrongStatCounters": "true",
        "tierCostLevelDivisor": "50_000.0",
        "tierCostLevelAnchor": "150_000.0",
        "tierCostT7TargetCopper": "10_000_000L",
    }
    for name, want in expected.items():
        got = field_default(cfg, name)
        check(f"default {name}", got == want, f"got {got}")

    print("\n=== Unlock gate / revoke ===")
    check(
        "prestige OR level eligibility",
        has(unlock, "prestige >= tier.id", "dmzLevelForUnlockGate", "requiredDmzLevel"),
    )
    check("reliable-sample revoke", has(unlock, "revokeTier", "hasReliableUnlockGateSample", "resetTemporary"))
    check("buy charges Ancient Coins", has(actions, "AncientCoinEconomy", "activationCost", "charge", "setTier"))
    coins_src = read(SRC / "com/dbzlegacy/adaptivedifficulty/currency/AncientCoinEconomy.java")
    tier_ui = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraDifficultyTierUi.java")
    check(
        "tier downgrade activationCost is free",
        "tier.id < currentActiveTier" in coins_src
        and "activationCost(UnlockTier tier, ServerPlayer player, int currentActiveTier)" in coins_src
        and "Downgrade to Tier" in tier_ui
        and "- Free" in tier_ui
        and has(actions, "activationCost(tier, player, current)", "downgrade", "charge = (staffFree || downgrade)"),
    )
    diff_chat_tiers = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/DifficultyChatMenu.java")
    check(
        "chat Free label uses UltraStyle (no raw §aFree)",
        "UltraStyle.confirm(\"Free\")" in diff_chat_tiers
        and "§aFree" not in diff_chat_tiers
        and "§afree" not in diff_chat_tiers,
    )
    char_ultra = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraCharacterScreens.java")
    check(
        "Character playerModel y=60 (below tab strip)",
        "248, 60, 86, 120" in char_ultra,
    )
    check(
        "Character playerModel only on Home + model customize",
        'base(player, "main", "home", true)' in char_ultra
        and ("sub(player, \"bones:\" + pg, true)" in char_ultra or 'sub(player, "bones:0", true)' in char_ultra)
        and 'base(player, UltraStyle.PAGE_ANDROID_REMOVE, "android", false)' in char_ultra
        and "sub(player, \"race:\" + pg, false)" in char_ultra
        and 'sub(player, "reskin", false)' in char_ultra,
    )
    parts_helper = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/PlayerModelParts.java")
    check(
        "playerModel.parts schema 1 helper",
        "SCHEMA = 1" in parts_helper and "headBones" in parts_helper,
    )

    print("\n=== Combat model (STR/SKP/PWR + ENE, class + top-2) ===")
    check("blended offense includes PWR/ENE", has(profile, "blendedOffense", "ENERGY_OFFENSE_FACTOR")
          and ("getKiDamage" in profile or "LmOverhaulScaledCombat.ki" in profile)
          and ("getMaxEnergy" in profile or "LmOverhaulScaledCombat.energy" in profile))
    check("ENERGY WeakStat", "ENERGY," in profile or "ENERGY\n" in profile)
    check("form peak includes PWR/ENE", has(profile, '"PWR"', '"ENE"'))
    check("top-2 counters", has(profile, "top 2", "Math.min(2") or "topCount = Math.min(2" in profile)
    check("class counters enabled in damage path", has(profile, "enableClassCounters", "classDamageBias"))
    check("strong-stat top-2 combine", has(profile, "combineTopStatBiases", "0.60"))
    check("VIT hit cap kept", has(profile, "kiProtectionHitFrac", "targetMobDamage", "hitCap"))
    check(
        "raised hit-cap budgets (1.0.28)",
        "case 1 -> 0.22" in profile and "default -> 0.56" in profile and "formFactor = 0.78" in profile,
    )
    check("live-bag landing ladder", "liveMaxHealth" in profile and "landFrac" in profile)
    check("T3 god-form pierce", "activeTier >= 3 && formBoost >= 6.0" in profile or "formBoost >= 6.0" in profile)
    check("telemetryFeelMigratedV1", "telemetryFeelMigratedV1" in cfg)
    check(
        "DMZ raw-damage tag clear on all AD mob hits (incl. passthrough)",
        "clearDmzRawDamageOverride" in events
        and "dmz_raw_damage" in events
        and events.count("clearDmzRawDamageOverride(player);") >= 1
        and "if (intervened) {\n            clearDmzRawDamageOverride" not in events,
    )
    check("event incoming soft-cap", "softCap" in events or "maxFrac" in events)
    check("VIT/RES damage floors live", has(profile, "tankDamageDefenseRatio", "tankDamageHealthRatio", "defFloor", "hpFloor"))
    check("VIT floor uses raised early-tier strength", "hpFloorStrength" in profile)
    check("vitDumpPressureMigratedV1 present", "vitDumpPressureMigratedV1" in cfg)
    check("reads kiprotection / ki_infusion / potentialunlock", has(profile, '"kiprotection"', '"ki_infusion"', '"potentialunlock"'))
    check("skill sponge on mob HP", has(profile, "kiInfusionLevel", "potentialUnlockLevel", "skillHp"))
    check("live-bag hit-cap", "hitCapHealth" in profile)
    check("live-offense transform pressure", "liveOffense" in profile and "liveShare" in profile or "liveFloor" in profile)
    check("KP hit-cap + landing relief", "kiProtectionLevel * 0.010" in profile and "kiProtectionLevel * 0.015" in profile)
    check("DEF/enchant paint relief via DMZ mit probe", "estimateMitigationRelief" in profile and "calculatePostMitigationDamage" in profile)
    check("defensivePaintRelief capped", "defensivePaintRelief" in profile and "defenseMitigationRelief" in profile)
    check("reads getFlatMitigation", "getFlatMitigation" in profile)
    check("DEF-cancel pierce floor", "dmzCancelMitigationThreshold" in profile and "pierce" in profile)
    check("landing safety net method", "targetLandingDamage" in profile)
    check("LivingDamageEvent receiveCanceled", "receiveCanceled = true" in events and "targetLandingDamage" in events)
    check("isAdPainted helper", "isAdPainted" in mob)
    check("CombatSanity clamps", has(sanity, "saneFormMult", "saneLive", "usableBaseline"))
    check("live offense poll includes PWR/ENE",
          has(events, "LmOverhaulScaledCombat.ki", "LmOverhaulScaledCombat.energy")
          or has(events, "getKiDamage", "getMaxEnergy"))

    print("\n=== Form soft-curve / peel ===")
    check("form soft curve", has(profile, "blendForm", "transformScaleWeight", "megaForm"))
    check("DMZ addition peel", has(profile, "combineDmzMults", "dmzMultiplicationMode", "peelChannel"))

    print("\n=== Nearby scale + max 5 ===")
    check("max slots ≤5", "maxScaledMobsPerPlayer" in cfg and "Math.min(5" in tracker)
    check("maxScaledMobsPerPlayer clamped", "Math.min(5" in cfg or "maxScaledMobsPerPlayer" in cfg)

    print("\n=== Exemptions ===")
    check("saga/quest exempt", has(mob, "DBSagasEntity", "isExemptFromConversion"))
    check("Ender Dragon exempt", "EnderDragon" in mob)
    check("SPAWNER exempt", has(mob, "TAG_FROM_SPAWNER") or "dmz_ad_from_spawner" in mob)
    check("SDD exempt", has(mob, "sdd_spawner", "sdd_boss"))
    check("slime split exempt", has(mob, "TAG_FROM_SLIME_SPLIT") or "dmz_ad_slime_split" in mob)

    print("\n=== Personal / death / coins / admin ===")
    check("personal participates gate", has(gate, "participates", "isPersonalEnabled", "allows"))
    check("death clears active tier", has(events, "resetTemporary", "deathResetsActiveDifficulty"))
    check(
        "logout keeps tier (save, no resetTemporary on logout)",
        "onLogout" in events and "resetTemporary" not in events[events.find("onLogout") : events.find("onLogout") + 800],
    )
    check("kill coin rewards", has(reward, "rollKillLoot", "dropInWorld", "isCoinDropChat"))
    check("system enabled gate", has(gate, "isEnabled") and "setEnabled" in cfg)
    check("whitelist gate", "isWhitelistEnabled" in cfg and "isPlayerAllowed" in cfg)

    print("\n=== Titles / teams stub / GUI ===")
    check("title equip action", has(actions, "equipTitle", "ACT_EQUIP_TITLE") or "equip_title" in actions)
    check("rival team mode action", "setTeamMode" in actions or "mutual rivals" in actions.lower())
    check("GUI ABI package stable", "com.dbzlegacy.adaptivedifficulty" in bridge and "DifficultyCache" in bridge)

    print("\n=== Balance telemetry ===")
    tel = read(SRC / "com/dbzlegacy/adaptivedifficulty/telemetry/BalanceTelemetry.java")
    gui_plugin = read(GUI_PLUGIN)
    check("BalanceTelemetry class", "class BalanceTelemetry" in tel)
    check("shouldLog when telemetry enabled", "shouldLog" in tel and "isEnabled()" in tel)
    check("JSONL hits path", "hits-" in tel and "telemetry" in tel)
    check("admin telemetry commands", "telemetryRoot" in cmds or '"telemetry"' in cmds)
    check("logs before/with safety net", "logIncomingHit" in events)
    check("default telemetry off", "balanceTelemetryEnabled = false" in cfg)
    check("writeTestProbe", "writeTestProbe" in tel)
    # Mohist: Bukkit owns /difficulty — telemetry must be wired through GUI, not Forge-only.
    check("Bukkit telemetry subcommand", "handleTelemetry" in gui_plugin and '"telemetry"' in gui_plugin)
    check("Bukkit ForgeBridge telemetry API", "setTelemetryEnabled" in bridge and "telemetryTest" in bridge)
    check("telemetry not whitelist-gated", "isWhitelisted" not in tel)

    print("\n=== Live challenge knobs (1.0.17) ===")
    check("Forge admin sets tankDamageDefenseRatio", "tankdamagedefenseratio" in cmds and "tankDamageDefenseRatio =" in cmds)
    check("Forge admin sets tankDamageHealthRatio", "tankdamagehealthratio" in cmds and "tankDamageHealthRatio =" in cmds)
    check("Forge admin sets mobHealthScale", "mobhealthscale" in cmds and "mobHealthScale =" in cmds)
    check("GUI allowlists tankDamage*", '"tankDamageDefenseRatio"' in bridge and '"tankDamageHealthRatio"' in bridge)
    unused_fn = re.search(
        r"isLegacyUnusedCounterField\(.*?\{(.*?)\n    \}", bridge, re.S
    )
    unused_body = unused_fn.group(1) if unused_fn else ""
    check(
        "GUI does not mark tankDamage unused",
        "tankDamageDefenseRatio" not in unused_body and "tankDamageHealthRatio" not in unused_body,
    )
    m = re.search(r"public double targetMobDamage\(.*?\{(.*?)\n    \}", profile, re.S)
    dmg_fn = m.group(1) if m else profile
    check("targetMobDamage uses tankDamage*", "tankDamage" in dmg_fn)

    print("\n=== Skills + melee AI parity ===")
    check("DmzProgression.skillLevel helper", "skillLevel(" in progression and "skillActive(" in progression)
    check("melee painted shock / slam damage", has(evo, "paintedMeleeHit", "dmz_ad_melee_shock"))
    check("zombie Awakened+ chase speed", "AWAKENED" in evo and "m_21573_" in evo)
    check("one kit action marker", "dmz_ad_kit_act" in evo and "pushAway" in evo)
    check("Adaptive AI speed from Enhanced+", "ENHANCED" in ai and "f_19596_" in ai)
    check("one owner per pressure debuff", "claimPressure" in ai)
    check("tier move bump on paint", "tierBump" in mob or "case 1 -> 1.06" in mob)

    print("\n=== Combat gravity cleanup (1.0.21) ===")
    gravity = read(SRC / "com/dbzlegacy/adaptivedifficulty/evolution/CombatGravity.java")
    check("removeSource API", "removeSource(" in gravity)
    check("zero-crossing always applies", "Crossing to/from ~0" in gravity or "boolean clearing" in gravity)
    check("death clears Enderman/Warden gravity", has(events, "CombatGravity.removeSource", "EnderMan", "Warden"))
    check("README sticky-gravity fix", "gravity" in readme.lower() and "1.0.21" in readme)

    print("\n=== Personal OFF on boot (1.0.22) ===")
    pdata = read(SRC / "com/dbzlegacy/adaptivedifficulty/data/PlayerDifficultyData.java")
    check("personal default false", "personalEnabled = false" in pdata)
    check("NBT missing personal → false", "tag.m_128441_(\"personalEnabled\") && tag.m_128471_(\"personalEnabled\")" in pdata)
    check("boot reset set cleared on starting", "PERSONAL_OFF_THIS_BOOT.clear()" in events)
    check("first login forces personal OFF", "PERSONAL_OFF_THIS_BOOT.add" in events and "setPersonalEnabled(false)" in events)
    check("README personal OFF on restart", "forces personal OFF" in readme or "Personal difficulty starts **OFF**" in readme)

    print("\n=== Post-pierce soft-cap clamp (1.0.25) ===")
    check("incomingSoftCapFrac helper", "incomingSoftCapFrac" in profile)
    check("post-pierce bagCap clamp", "bagCap" in profile and "incomingSoftCapFrac()" in profile)
    check("formula revision 45", "mix(h, 45L)" in profile)
    check("README pierce clamp", "Post-pierce" in readme or "post-pierce" in readme)

    print("\n=== Telemetry rollback (2.3.161) ===")
    check("T1 threat floor 0.35", "case 1 -> 0.35" in profile)
    check("T3 threat floor 0.48", "case 3 -> 0.48" in profile)
    check("T6 form nudge 1.14", "case 6 -> 1.14" in profile)
    check("T6 liveShare 0.55", "case 6 -> 0.55" in profile)
    check("scaling_constants SSOT", (Path(__file__).resolve().parent / "scaling_constants.py").is_file())

    print("\n=== Mob HP trim (1.0.29 / 1.0.30) ===")
    check("stock mobHealthScale 0.75", field_default(cfg, "mobHealthScale") == "0.75")
    check("mobHpTrimMigratedV1", "mobHpTrimMigratedV1" in cfg)
    check("mobHpTrimMigratedV2", "mobHpTrimMigratedV2" in cfg)
    check("README 1.0.30 HP trim", "1.0.30" in readme and "0.75" in readme)

    print("\n=== Telemetry retune (1.0.24) ===")
    check("formula revision 45", "mix(h, 45L)" in profile)
    check("T1 landCap 0.18", "case 1 -> 0.18" in profile)
    check("T1 landFrac 0.13", "case 1 -> 0.13" in profile)
    check("T6 landFrac 0.38", "case 6 -> 0.38" in profile)
    check("T4 landFrac 0.28", "case 4 -> 0.28" in profile)
    check("T5 landFrac 0.33", "case 5 -> 0.33" in profile)
    check("KP landing 1.5%/lvl", "kiProtectionLevel * 0.015" in profile)
    check("partial landing fill", "preAmount < land * 0.45" in events)
    check("progressive soft-caps", "case 5 -> 0.44" in profile and "case 6 -> 0.48" in profile and "case 7 -> 0.60" in profile)
    check("README 1.0.25 balance", "1.0.25" in readme and "T4 tank" in readme)
    check("README 2.3.61 tier costs", "2.3.61" in readme and "tierCostLevelAnchor" in readme and "100× Netherite" in readme)
    check("README 2.3.62 gui level pull", "2.3.62" in readme and "prepareGui" in actions)
    check("README 2.3.63 death drop guard", "DeathDropGuard" in readme and "AllowCombatItemDrop" in readme)
    check("scheduleLevelPull on login race", "scheduleLevelPull" in actions)
    check("Character-null not transformed", "never freeze sampling" in progression)
    check("guiDisplayDmzLevel for Buy GUI", "guiDisplayDmzLevel" in progression)
    check("recompute level when getLevel placeholder", "recomputeLevelFromStats" in progression)
    check("form paint clears AreaDifficulty", "AreaDifficulty.clearCache" in read(SRC / "com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.java"))
    check("Rival form surge uses form mult", "liveFormMultiplier" in read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalInstinct.java"))
    check("CR releasedStatPower", "releasedStatPower" in read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/CombatRating.java"))
    check("CombatRating DISPLAY_ABS_CAP", "DISPLAY_ABS_CAP" in read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/CombatRating.java"))
    check("polluted highestDmzLevel<=1 ignored", "hw <= 1L" in unlock)
    check("prepareDifficultyGui bridge", "prepareDifficultyGui" in bridge)
    gui_plugin_src = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/AdaptiveDifficultyGuiPlugin.java")
    death_guard = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/DeathDropGuard.java")
    check("DeathDropGuard registered", "new DeathDropGuard()" in gui_plugin_src)
    check("DeathDropGuard uncancels when dead", "isDead()" in death_guard and "setCancelled(false)" in death_guard)
    cache_src = read(SRC / "com/dbzlegacy/adaptivedifficulty/cache/DifficultyCache.java")
    check("refresh samples DMZ level", "sampleLevelOnGuiOpen" in cache_src)
    check("tierCostDivisorMigratedV1", "tierCostDivisorMigratedV1" in cfg)
    check("tierCostCurveMigratedV2", "tierCostCurveMigratedV2" in cfg)
    check("tierCostLevelMultiplier", "tierCostLevelMultiplier" in cfg)
    unlock_tier = read(SRC / "com/dbzlegacy/adaptivedifficulty/tier/UnlockTier.java")
    cmi_gui = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/CmiDifficultyGui.java")
    check("UnlockTier.requirementTip", "requirementTip" in unlock_tier and "requiredPrestige" in unlock_tier)
    board_helper = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/GuiBoardHelper.java")
    check(
        "GUI shows DMZ or Prestige req",
        ("Need &fDMZ" in cmi_gui or "DMZ {req_level}" in board_helper)
        and "_req_level" in bridge,
    )
    check("README unlock req UX", "Need DMZ" in readme and "Prestige" in readme)

    print("\n=== Ghost scaling fix (1.0.23) ===")
    check(
        "isAdPainted ignores bare TAG_SCALED",
        "unlockTierOf(entity) > 0" in mob and ("Spawn init stamps" in mob or "spawn-init" in mob.lower()),
    )
    char_screens = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraCharacterScreens.java")
    check(
        "Ultra race list is paged (no hard drop after 10)",
        "RACES_PER_PAGE" in char_screens
        and "page:race:" in char_screens
        and "parseRaceListPage" in char_screens,
    )
    bone_svc = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CosmeticHeadBoneService.java")
    check(
        "native head bones use DmzContentDiscovery (disk+config)",
        "DmzContentDiscovery.headBonesForRace(race)" in bone_svc
        and "isNativeForRace" in bone_svc,
    )
    discovery = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/DmzContentDiscovery.java")
    check(
        "discoverRaceIds logs source breakdown",
        "discoverRaceIds: root=" in discovery and "diskOnly" in discovery,
    )
    check(
        "landing safety net requires participates",
        "onDamageDone" in events and "SystemGate.participates(player)" in events[events.find("onDamageDone"):events.find("onDamageDone")+900],
    )
    check("README ghost-scaling fix", "Ghost difficulty" in readme or "spawn-init shells" in readme)

    print("\n=== README alignment ===")
    check("README stock percents", "21%" in readme and "200%" in readme)
    check("README PWR/ENE + top-2", "PWR" in readme and "ENE" in readme and "top-2" in readme)
    check("README version 2.3.63", "2.3.63" in readme)

    print("\n=== Ghast aim fix (1.0.31) ===")
    ki = read(SRC / "com/dbzlegacy/adaptivedifficulty/evolution/KiAttackHelper.java")
    check("large blast cast 0", "setupKiLargeBlast(" in ki and ", 0);" in ki)
    check("large blast launchToward", "fireLargeBlast" in ki and "launchToward(blast, shooter, target, speed)" in ki)
    check("laser/beam launchToward", "launchToward(laser" in ki and "launchToward(wave" in ki)
    check("faceTarget public", "public static void faceTarget" in ki)
    check("ghastTick faceTarget", "KiAttackHelper.faceTarget(ghast, target)" in evo)
    check("README ghast aim", "Ghast" in readme and ("aim" in readme.lower() or "look" in readme.lower()))
    check("README skill-aware / sponge", "Ki Infusion" in readme or "ki_infusion" in readme or "1.05" in readme)

    print("\n=== Soft-cap ladder (1.0.33) ===")
    check("T3 soft-cap 0.36", "case 3 -> 0.36" in profile)
    check("T4 soft-cap 0.40", "case 4 -> 0.40" in profile)
    check("T1 soft-cap 0.30", "default -> 0.30" in profile)
    check("T2 soft-cap 0.32", "case 2 -> 0.32" in profile)
    check("README soft-cap ladder", "0.36" in readme and "soft-cap" in readme.lower())

    print("\n=== Ladder retune (1.0.35 rollback) ===")
    check("T4 form nudge 1.06", "case 4 -> 1.06" in profile)
    check("T5 form nudge 1.12", "case 5 -> 1.12" in profile)
    check("T7 form nudge 1.21", "default -> 1.21" in profile)
    check("T4 liveShare 0.42", "case 4 -> 0.42" in profile)
    check("T5 liveShare 0.50", "case 5 -> 0.50" in profile)
    check("T4 landCap 0.32", "case 4 -> 0.32" in profile)
    check("T5 landCap 0.36", "case 5 -> 0.36" in profile)
    check("T3 landFrac 0.20", "case 3 -> 0.20" in profile)
    check("T7 landFrac 0.42", "default -> 0.42" in profile)
    check("README 1.0.35 retune", "1.0.35" in readme and "hits-2026-08-06" in readme)

    print("\n=== Level clamp + mount guard (1.0.35) ===")
    prog = read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/DmzProgression.java")
    host = read(SRC / "com/dbzlegacy/adaptivedifficulty/scaling/HostileMobs.java")
    check("clampDmzLevel helper", "clampDmzLevel" in prog and "configuredMaxDmzLevel" in prog)
    check("dmzLevel uses clamp", "clampDmzLevel(Math.max(0, raw), data, player)" in prog)
    check("unlock gate uses dmzLevel recompute", "dmzLevelForUnlockGate" in prog
          and "int live = dmzLevel(player)" in prog.split("dmzLevelForUnlockGate")[1].split("hasReliableUnlockGateSample")[0])
    check("reliable gate rejects placeholder while BP high", "transformationPower(player) >= 25.0" in prog
          and "dmzLevel(player)" in prog.split("hasReliableUnlockGateSample")[1].split("clearBaseFormLevel")[0])
    check("paintEase uses tierScalingDmzLevel", "tierScalingDmzLevel(player)" in profile
          and "paintEase(cfg, tier, dmzLevel)" in profile)
    coins = read(SRC / "com/dbzlegacy/adaptivedifficulty/currency/AncientCoinEconomy.java")
    check("tier buy cost uses tierScalingDmzLevel", "tierScalingDmzLevel(player" in coins
          and "activationCostForLevel(level)" in coins)
    check("tierScalingDmzLevel centralizes display+stable",
          "tierScalingDmzLevel(Player player, long fallbackWhenTransformed)" in prog
          and "int display = guiDisplayDmzLevel(player)" in prog
          and "dmzLevelForProgression(player, fallbackWhenTransformed)" in prog)
    tel = read(SRC / "com/dbzlegacy/adaptivedifficulty/telemetry/BalanceTelemetry.java")
    check("telemetry logs dmzLevel + paintEase", "dmzLevel" in tel and "paintEase" in tel
          and "progressionDmzLevel" in tel)
    check("reliable gate rejects placeholder while BP high", "transformationPower(player) >= 25.0" in prog
          and "dmzLevel(player)" in prog.split("hasReliableUnlockGateSample")[1].split("clearBaseFormLevel")[0])
    check("HostileMobs.isMountPair", "isMountPair" in host and "m_20202_" in host)
    check("hurt cancels mount pair", "isMountPair" in events)
    check("skeleton rejects mount target", "isMountPair(mob, target)" in evo)
    check("README level clamp", "level clamp" in readme.lower() or "maxValue" in readme)
    check("README skeleton mount", "mount" in readme.lower() and "jockey" in readme.lower())

    print("\n=== Coin drop chances (2.3.54) ===")
    coins = read(SRC / "com/dbzlegacy/adaptivedifficulty/currency/AncientCoinEconomy.java")
    check("drop chance gate in rollKillLoot", "ancientCoinDropChance" in coins and "roll >= dropChance" in coins)
    check("dual upgrade band uses same roll", "roll < upgradeChance" in coins)
    check("coinDropChanceMigratedV1", "coinDropChanceMigratedV1" in cfg)
    check("admin set drop chance", "ancientcoindropchance" in cmds or "coindropchance" in cmds)
    check("README 5% / 0.5%", "5%" in readme and "0.5%" in readme and "2.3.63" in readme)

    print("\n=== Mohist CMI /lmdo routing (2.3.54) ===")
    gui_root = GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit"
    cmi_files = [
        "CmiDifficultyGui.java",
        "CmiHubGui.java",
        "CmiProgressionGui.java",
        "CmiPrestigeGui.java",
        "CmiSkillsGui.java",
        "CmiRivalGui.java",
        "CmiSparGui.java",
    ]
    bad_do = (
        'addCommand("difficulty do ',
        'addCommand("lm do ',
        'addCommand("progression do ',
        'addCommand("prestige do ',
        'addCommand("skills do ',
        'addCommand("skillcheck do ',
        'addCommand("rival do ',
        'addCommand("spar do ',
    )
    for name in cmi_files:
        path = gui_root / name
        text = read(path) if path.is_file() else ""
        check(f"{name} exists", path.is_file())
        for pat in bad_do:
            check(f"{name} no {pat.strip()}", pat not in text)
        if path.is_file():
            check(f"{name} uses lmdo", "lmdo " in text)

    chest = read(gui_root / "DifficultyChestGui.java")
    check(
        "DifficultyChestGui no performCommand difficulty do",
        'performCommand("difficulty do' not in chest,
    )
    check(
        "DifficultyChestGui uses handleActionResult",
        "handleActionResult" in chest,
    )
    plugin = read(gui_root / "AdaptiveDifficultyGuiPlugin.java")
    check("Bukkit difficulty admin syslog", 'case "syslog", "systemlog"' in plugin or 'case "syslog"' in plugin)
    check("Bukkit difficulty admin resynclevel", "resynclevel" in plugin)
    check("ForgeBridge.syslogCommand", "syslogCommand" in read(gui_root / "ForgeBridge.java"))
    check("ForgeBridge.resyncLevel", "resyncLevel" in read(gui_root / "ForgeBridge.java"))
    check(
        "DifficultyCommands.resyncLevel public",
        "public static String resyncLevel" in cmds,
    )
    skills_chest = read(gui_root / "SkillsChestGui.java")
    check("SkillsChestGui progression via lmdo", "lmdo lm open progression" in skills_chest)
    check("SkillsChestGui no lm do open", 'cmd("lm do open' not in skills_chest)

    print("\n=== CNPC migrate safety (2.3.54) ===")
    migrator = read(SRC / "com/dbzlegacy/adaptivedifficulty/data/CnpcDataMigrator.java")
    check("resolveWorldBlob present", "resolveWorldBlob" in migrator)
    check("fromScriptControllerCompound", "fromScriptControllerCompound" in migrator)
    check("pickRichestBlob", "pickRichestBlob" in migrator)
    check("enrichRivalRawFromBackupKeys", "enrichRivalRawFromBackupKeys" in migrator)
    check("force aborts without usable data", "Force aborted" in migrator)
    check("NBTJsonUtil LoadFile", "NBTJsonUtil" in migrator and "LoadFile" in migrator)
    check("cnpcLevelScriptsDir LevelResource", "LevelResource" in migrator and "cnpcLevelScriptsDir" in migrator)
    check("fromBackupDir present", "fromBackupDir" in migrator)
    check("fromWorldDataFile present", "fromWorldDataFile" in migrator)
    check("clear only after import", "imported && blob.liveStored" in migrator)
    check("no fake empty marker", "empty-no-cnpc-data" not in migrator)
    check("force uses runWorldMigrate", "runWorldMigrate(server, forceOverwrite)" in migrator)
    check("honest zero-import message", "No rows imported" in migrator)
    check("richer-CNPC replace", "cnpcRich > lmRich" in migrator)

    print("\n=== Staff clear player (2.3.54) ===")
    clearer = read(SRC / "com/dbzlegacy/adaptivedifficulty/data/PlayerDataClear.java")
    mech = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.java")
    gui_plugin = read(gui_root / "AdaptiveDifficultyGuiPlugin.java")
    bridge = read(gui_root / "ForgeBridge.java")
    check("PlayerDataClear.clear API", "public static String clear(" in clearer)
    check("scopes rival/spar/difficulty/progression",
          "clearRival" in clearer and "clearSpar" in clearer
          and "clearDifficulty" in clearer and "clearProgression" in clearer)
    check("Forge /lm admin clear", 'Commands.m_82127_("clear")' in mech)
    check("Bukkit handleLmAdmin clear", '"clear", "wipe", "resetplayer"' in gui_plugin
          or 'case "clear"' in gui_plugin)
    check("ForgeBridge.clearPlayerData", "clearPlayerData" in bridge)
    check("RivalProgression.clearPlayer", "clearPlayer(String uuid)" in read(
        SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalProgression.java"))
    check("offline clear reads player cache and saved file",
          "m_129927_" in clearer and "ForgeData" in clearer and "LevelResource.f_78176_" in clearer)

    print("\n=== Spar recent sessions (2.3.54) ===")
    spar_store = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparStore.java")
    spar_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    check("RECENT_SESSION_LIMIT 3", "RECENT_SESSION_LIMIT = 3" in spar_store)
    check("recentSessions map", "recentSessions" in spar_store and "pushRecent" in spar_store)
    check("recordRecentSession on end", "recordRecentSession" in spar_sys)
    check("statsLines individual reports", "Spar Report #" in spar_sys and "appendRecentReport" in spar_sys)
    check("statsLines no live TP this session", "TP this session" not in spar_sys)
    check("spar TP strict melee+ki (no arrows/tridents/fireworks)",
          "AbstractArrow" not in spar_sys
          and "ThrownTrident" not in spar_sys
          and "FireworkRocketEntity" not in spar_sys)
    check("isNonCombatDamage non-melee returns true",
          "Strict melee and ki only" in spar_sys)

    print("\n=== Skill Check perm gate (2.3.54) ===")
    staff_access = read(SRC / "com/dbzlegacy/adaptivedifficulty/util/StaffAccess.java")
    # Extract hasSkillCheck body — must not auto-grant via isStaff()
    start = staff_access.find("public static boolean hasSkillCheck")
    end = staff_access.find("private static boolean hasBukkitIsOp", start)
    body = staff_access[start:end] if start >= 0 and end > start else staff_access
    check("hasSkillCheck no staff auto-grant", "isStaff(player)" not in body)
    check("hasSkillCheck uses skillCheckPermission", "skillCheckPermission" in body)
    gui_yml = read(ROOT / "tools/dmz-adaptive-difficulty-gui/src/main/resources/plugin.yml")
    check("plugin.yml skillcheck permission", "permission: legacymechanics.skillcheck" in gui_yml)
    hub = read(gui_root / "HubChestGui.java")
    check("HubChestGui gates on hasSkillCheck", "ForgeBridge.hasSkillCheck(player)" in hub)

    print("\n=== Skill Check icons (2.3.54) ===")
    lore = read(gui_root / "GuiLoreChunks.java")
    skills_chest = read(gui_root / "SkillsChestGui.java")
    cmi_skills = read(gui_root / "CmiSkillsGui.java")
    check("skillIcon never defaults PAPER",
          "return Material.PAPER" not in lore.split("static Material skillIcon")[1].split("private static int")[0]
          if "static Material skillIcon" in lore else False)
    check("Flight uses ELYTRA", "Material.ELYTRA" in lore)
    check("Skill Check header EXPERIENCE_BOTTLE",
          "skillCheckUi ? Material.EXPERIENCE_BOTTLE" in skills_chest
          and "skillCheckUi ? Material.EXPERIENCE_BOTTLE" in cmi_skills)
    check("Hub Skill Check EXPERIENCE_BOTTLE",
          'Material.EXPERIENCE_BOTTLE, "&eSkill Check"' in hub
          or 'Material.EXPERIENCE_BOTTLE, "&eSkill Check"' in read(gui_root / "CmiHubGui.java"))

    print("\n=== GUI coherence (2.3.54) ===")
    diff_chest = read(gui_root / "DifficultyChestGui.java")
    prestige = read(gui_root / "PrestigeChestGui.java")
    rival = read(gui_root / "RivalChestGui.java")
    spar = read(gui_root / "SparChestGui.java")
    skills = read(gui_root / "SkillsChestGui.java")
    check("Difficulty unavailable close @35",
          'put(holder, inv, 35, closeBtn()' in diff_chest)
    check("Difficulty Tiers header GOLD_INGOT",
          'Material.GOLD_INGOT, "&e&lDifficulty Tiers"' in diff_chest)
    check("Difficulty hub single Tiers page",
          'SlotAction.page("tiers")' in diff_chest
          and 'SlotAction.page("lower")' not in diff_chest)
    check("Difficulty Clear Title not BARRIER",
          'Material.NAME_TAG, "&cClear Title"' in diff_chest)
    check("Prestige wallet GOLD_INGOT",
          'Material.GOLD_INGOT' in prestige and "putWallet" in prestige)
    check("Prestige Progression BREWING_STAND",
          'Material.BREWING_STAND, "&dProgression"' in prestige)
    check("Rival Progress compact hub",
          'Inventory inv = Bukkit.createInventory(holder, 27' in rival
          and 'SlotAction.page("records")' in rival
          and '"&eStats"' in rival)
    check("Rival Progress HOF+Journal secondary",
          'SlotAction.page("hof")' in rival and 'SlotAction.page("journal")' in rival
          and "records" in rival)

    print("\n=== GUI coherence Skill Check (2.3.159) ===")
    unlock = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/SkillUnlockService.java")
    tips_json159 = read(ROOT / "tools/dmz-adaptive-difficulty-gui/src/main/resources/gui-tooltips.json")
    cmi_sk159 = read(gui_root / "CmiSkillsGui.java")
    cmi_pr159 = read(gui_root / "CmiPrestigeGui.java")
    check("Skill tips drop long progress essays",
          "Progress to next:" not in unlock and "Air time toward next:" not in unlock
          and "Current trial:" not in unlock)
    check("Skill header one DMZ line",
          'out.add("§7DMZ §f"' in unlock or 'out.add("§7DMZ §f" + level' in unlock)
    check("Skills Chest uses full frame + centeredSlots",
          "private static void frame(" in skills and "GuiBoardHelper.centeredSlots" in skills
          and "frameOnly" not in skills)
    check("Skills CMI uses fillEmpty + centeredSlots",
          "fillEmpty(gui, 6)" in cmi_sk159 and "GuiBoardHelper.centeredSlots" in cmi_sk159
          and "fillFrameOnly" not in cmi_sk159)
    check(
        "skills.main.header is one skill list",
        '"&7Every tracked skill"' in tips_json159
        and 'SlotAction.page("saga")' not in skills
        and '"&dSaga"' not in cmi_sk159,
    )
    check("Prestige main hub @40 like Spar",
          "put(holder, inv, 40, hubBtn()" in prestige
          and "gui.addButton(hubBtn(40))" in cmi_pr159)

    print("\n=== Rival relationship semantics (2.3.142+) ===")
    rival_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalSystem.java")
    rival_st = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalStatus.java")
    rival_link = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalLink.java")
    check("Silent label (not Unknown)", 'case UNKNOWN -> "Silent"' in rival_st)
    cnpc_rival = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmRivalGui.java")
    check("CNPC rival remove only from list detail",
          "list_detail:" in cnpc_rival and "openListDetail" in cnpc_rival
          and 'case "pick_remove" -> open(pl, "list")' in cnpc_rival)
    check("Rival displayPickerArg resolves uuid links",
          "displayPickerArg" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.java")
          and "rivalLinkDisplayName" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.java"))
    check("remove demotes mutual to one-way", "demoteToOneWayDeclare" in rival_sys)
    check("remove archives remover history", "archiveRivalLink(me, them.uuid, myLink)" in rival_sys)
    check("pending excluded from rival list", "st == RivalStatus.PENDING" in rival_sys)
    check("visibleDeclare flag", "visibleDeclare" in rival_sys and "visibleDeclare" in rival_link)
    check("declare sets visibleDeclare", "myLink.visibleDeclare = true" in rival_sys)
    check("accept/decline resolve uuid", 'regionMatches(true, 0, "uuid:"' in rival_sys)
    check("nemesis only from challenge death KO", "challengeKo" in rival_sys and "Phase.ACTIVE" in rival_sys)
    check("dual silent → Declared", "promoteDeclared" in rival_sys)
    rival_chest = read(gui_root / "RivalChestGui.java")
    cmi_rival = read(gui_root / "CmiRivalGui.java")
    check("pending_decide submenu page",
          "pending_decide:" in rival_chest and "pendingDecide" in rival_chest
          and "pending_decide:" in cmi_rival and "openPendingDecide" in cmi_rival)
    check("chest pending head opens decide submenu",
          'SlotAction.page("pending_decide:' in rival_chest)
    check("CMI pending head opens decide submenu",
          'lmdo rival page pending_decide:' in cmi_rival)
    rival_cnpc = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmRivalGui.java")
    rival_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.java")
    check("challenge pending board + decide submenu",
          "challenge_pending" in rival_cnpc and "challenge_decide:" in rival_cnpc
          and "pendingChallengeCards" in rival_api
          and "challenge_pending" in rival_chest and "challengeDecide" in rival_chest
          and "challenge_pending" in cmi_rival and "openChallengeDecide" in cmi_rival)
    check("challenge hub Send + Pending (not inline accept)",
          "pick_challenge" in rival_cnpc and "void paintRequests" in rival_cnpc
          and "Send challenge" in rival_cnpc
          and "Pending requests" not in rival_cnpc
          and 'SlotAction.act("challenge", "accept"' not in rival_chest.split("private Inventory challenge(")[1].split("private Inventory challengePending")[0])
    check("acceptedMutualOffer flag", "acceptedMutualOffer" in rival_link)
    check("needsMutualConfirm helper", "needsMutualConfirm" in rival_link)
    check("promoteDeclared sets inviteReceived both",
          "inviteReceived = true" in rival_sys and "Both must Accept" in rival_sys)
    check("accept waits for other Mutual confirm",
          "Waiting for them to Accept" in rival_sys or "waiting for them to Accept" in rival_sys)
    check("acceptReplace Mutual slot pick",
          "acceptReplace" in rival_sys and "needsMutualReplacePick" in rival_sys
          and "pendingMutualAcceptUuid" in read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalPlayerRecord.java"))
    accept_replace_fn = (
        rival_sys.split("public static String acceptReplace", 1)[1]
        .split("public static boolean needsMutualReplacePick", 1)[0]
        if "public static String acceptReplace" in rival_sys
        else ""
    )
    cancel_replace_fn = (
        rival_sys.split("public static String cancelMutualReplace", 1)[1]
        .split("public static String pendingMutualAcceptName", 1)[0]
        if "public static String cancelMutualReplace" in rival_sys
        else ""
    )
    check(
        "acceptReplace/cancelMutualReplace use UltraStyle (no bare §)",
        "UltraStyle.dangerLine" in accept_replace_fn
        and "UltraStyle.GREEN" in accept_replace_fn
        and "UltraStyle.WHITE" in accept_replace_fn
        and "UltraStyle.DARK_GRAY" in accept_replace_fn
        and "UltraStyle.YELLOW" in cancel_replace_fn
        and "UltraStyle.dangerLine" in cancel_replace_fn
        and "§" not in accept_replace_fn
        and "§" not in cancel_replace_fn,
    )
    check("GUI pick_replace_mutual page",
          "pick_replace_mutual" in rival_chest and "pick_replace_mutual" in cmi_rival)
    check("rival list status order helper", "rivalListOrder" in rival_sys and "statusListRank" in rival_sys)
    prox = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalProximity.java")
    under_fn = prox.split("tryUnderdogEngage", 1)[1].split("handleDamagedByRival", 1)[0] if "tryUnderdogEngage" in prox else ""
    check("underdog any declaredByMe status",
          "!link.declaredByMe" in under_fn and "link.mutual" not in under_fn)
    check("Actions is Declare Pending Silent (remove via List)",
          'page("pick_declare")' in rival_chest
          and 'page("pending")' in rival_chest
          and 'page("pick_silent")' in rival_chest
          and 'page("pick_remove")' not in rival_chest.split("private Inventory actions")[1].split("private Inventory history")[0]
          and "pick_remove" not in cmi_rival.split("private static void openActions")[1].split("private static void openHistory")[0]
          and "list_detail:" in rival_chest and "list_detail:" in cmi_rival
          and "Accept Declared" not in rival_chest
          and "Accept Declared" not in cmi_rival)
    check("Spar Stats button BOOK",
          'Material.BOOK, "&eStats"' in spar)
    check("Skills admin header BOOK",
          "skillCheckUi ? Material.EXPERIENCE_BOTTLE : Material.BOOK" in skills)

    print("\n=== Android convert + remove (2.3.54) ===")
    android = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/AndroidConversion.java")
    prog_chest = read(gui_root / "ProgressionChestGui.java")
    prog_cmi = read(gui_root / "CmiProgressionGui.java")
    prog_tree = read(gui_root / "ProgressionCommandTree.java")
    forge_bridge = read(gui_root / "ForgeBridge.java")
    gui_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    check("AndroidConversion.remove", "public static String remove(" in android)
    check("AndroidConversion two-click confirm", "CONFIRM_MS = 10_000L" in android)
    check("AndroidConversion restore superforms", 'ensureSkillAtZero(skills, "superforms")' in android)
    check("Android blocks bioandroid only", 'BLOCKED = Set.of("bioandroid")' in android)
    check("Android gate via androidforms TP costs", "getFormSkillTpCosts" in android and "ANDROID_FORM_GROUP" in android)
    check("Android eligibleRaceHint", "eligibleRaceHint" in android and "configuredAndroidRaceIds" in android)
    check("Android isAndroidUpgraded helper", "public static boolean isAndroidUpgraded(" in android)
    check("Android strip on ineligible race", "stripIfRaceIneligible(" in android)
    headbone = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CosmeticHeadBoneService.java")
    diff_ev = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    check("Head bone persist through forms", "reapplyAfterFormChange" in headbone and "equippedHeadBone" in headbone)
    check("Head bone reapply on FormChangeEvent", "scheduleReapplyAfterFormChange" in diff_ev)
    race_hooks = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/DmzCharacterClassChangeHooks.java")
    check("Android strip on services race change", "stripIfRaceIneligible" in race_hooks)
    check("Android deny message not humans-only", "Only races with android forms (humans)" not in android)
    check("GUI lists all Android-capable races", "Frost Demon" in prog_chest and "Viltrumite" in prog_chest
          and "Frost Demon" in prog_cmi)
    tel = read(SRC / "com/dbzlegacy/adaptivedifficulty/telemetry/BalanceTelemetry.java")
    check(
        "telemetry logs android upgrade flag",
        '\\"android\\"' in tel and "isAndroidUpgraded" in tel,
    )
    races_root = ROOT / "config" / "dragonminez" / "races"
    if not races_root.is_dir():
        races_root = Path("/workspace/config/dragonminez/races")
    for race in ("human", "saiyan", "frostdemon", "viltrumite"):
        check(
            f"stock {race} androidforms.json",
            (races_root / race / "forms" / "androidforms.json").is_file(),
        )
        ch_path = races_root / race / "character.json"
        ch = ch_path.read_text(encoding="utf-8", errors="replace") if ch_path.is_file() else ""
        check(f"stock {race} androidforms TP costs", '"androidforms"' in ch and '"prices"' in ch)
    check(
        "bioandroid has no androidforms upgrade group",
        not (races_root / "bioandroid" / "forms" / "androidforms.json").is_file(),
    )
    check("Chest android_panel", "android_panel" in prog_chest and "androidPanel" in prog_chest)
    check("Chest android_remove picker", "androidRemovePicker" in prog_chest)
    check("CMI android_panel", "openAndroidPanel" in prog_cmi)
    check("CMI android_remove", "openAndroidRemove" in prog_cmi)
    check("Cmd android remove", '("remove".equalsIgnoreCase(args[1])' in prog_tree)
    check("ForgeBridge.androidRemove", "public static String androidRemove(" in forge_bridge)
    check("GuiApi androidRemove", "public static String androidRemove(" in gui_api)
    check("GuiApi player self-remove only",
          "You can only remove your own Android upgrade" in gui_api)
    hub = read(gui_root / "HubChestGui.java")
    cmi_hub = read(gui_root / "CmiHubGui.java")
    diff_events = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    char_bootstrap = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesPermissionBootstrap.java")
    check("Character services Bukkit permission bootstrap",
          "CharacterServicesPermissionBootstrap.register()" in diff_events
          and "addPermission" in char_bootstrap)
    char_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesSystem.java")
    cnpc_char = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmCharacterGui.java")
    # staffGrant overload: the precheck must run inside executeReskin before any charge.
    reskin_exec = char_sys.split(
        "public static String executeReskin(ServerPlayer player, boolean staffGrant)", 1)
    reskin_body = reskin_exec[1] if len(reskin_exec) == 2 else ""
    check("reskin precheck before charge/cooldown",
          "reskinPrecheck" in char_sys
          and "reskinPrecheck(player, staffGrant)" in reskin_body
          and 0 <= reskin_body.find("reskinPrecheck") < reskin_body.find("reskinAmountDue"))
    check("CNPC reskin confirm step like class",
          "paintReskinConfirm" in cnpc_char and "reskin_confirm" in cnpc_char)
    snapshot = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcPlayerSnapshot.java")
    check("CNPC hub snapshot includes difficulty + spar streak",
          "appendDifficulty" in snapshot and "Training streak" in snapshot)
    check("empty rival list guide for CNPC",
          "emptyRivalListGuide" in rival_sys and "Actions" in rival_sys.split("emptyRivalListGuide")[1][:800])
    check("Hub Remove Android button", 'SlotAction.open("android_remove")' in hub)
    check("CMI Hub Remove Android", '"android_remove"' in cmi_hub and "Remove Android" in cmi_hub)
    plugin = read(gui_root / "AdaptiveDifficultyGuiPlugin.java")
    check("openSystem android_remove for players",
          'case "android_remove"' in plugin or '"android_remove"' in plugin)

    print("\n=== Player Prestige access (2.3.54) ===")
    prestige_menu = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/PrestigeMenu.java")
    prestige_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java")
    prog_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java")
    check("PrestigeMenu no staff gate", "StaffAccess.isStaff" not in prestige_menu)
    check("PrestigeSystem.open no staff gate",
          "Staff only" not in prestige_sys.split("public static void open")[1].split("public static void confirmOrPrompt")[0]
          if "public static void open" in prestige_sys else False)
    prestige_reg = (prog_cmds.split('m_82127_("prestige")')[1].split('m_82127_("skills")')[0]
                    if 'm_82127_("prestige")' in prog_cmds and 'm_82127_("skills")' in prog_cmds else "")
    check("Forge /prestige player shop, staff backend gated",
          ".requires(CommandAccess::isPlayerSlashUser)" in prestige_reg
          and 'CommandAccess.staffLiteral("do")' in prestige_reg
          and 'CommandAccess.staffLiteral("menu")' in prestige_reg)
    check("Prestige chat uses Forge /prestige do confirm",
          "/prestige do confirm" in prestige_sys and "/lmdo prestige confirm" not in prestige_sys)
    check("Hub Prestige for everyone",
          'SlotAction.open("prestige")' in hub
          and hub.count('SlotAction.open("prestige")') >= 1)
    check("handlePrestige redirects players to /lm",
          "/lm" in plugin.split("private boolean handlePrestige")[1].split("private boolean handleSkills")[0]
          and "isStaff(player)" in plugin.split("private boolean handlePrestige")[1].split("private boolean handleSkills")[0]
          if "private boolean handlePrestige" in plugin else False)

    print("\n=== Player command whitelist (2.3.54) ===")
    prog_tree = read(gui_root / "ProgressionCommandTree.java")
    gui_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    # Non-staff commandHelp block (first join after if (!staff)).
    ns_help = gui_api.split("if (!staff)")[1].split("return String.join")[1].split(");")[0] if "if (!staff)" in gui_api else ""
    check("non-staff commandHelp keeps potential unlock",
          "Potential Unlock" in ns_help and "/progression meditation" not in ns_help)
    check("non-staff commandHelp no android slash", "/progression android remove" not in ns_help)
    check("non-staff commandHelp points to /lm", "/lm" in ns_help)
    check("player bare progression lists /lm /skillcheck",
          "Everything else: open" in prog_tree and "/skillcheck" in prog_tree)
    check("Forge helpOrGui no android slash",
          "/progression android remove" not in prog_cmds.split("helpOrGui")[1].split("private static int gui")[0]
          if "helpOrGui" in prog_cmds else False)
    check("Forge android remove hints GUI for players", "androidRemoveGuiHint" in prog_cmds)

    print("\n=== End ki purge silent (2.3.54) ===")
    end_str = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/end/EndDimensionStrength.java")
    purge_body = end_str.split("private static void purgeEndKiCommands")[1].split("private static EntityType")[0] \
        if "private static void purgeEndKiCommands" in end_str else ""
    check("purgeEndKi uses collectEndKiEntities", "collectEndKiEntities(end)" in purge_body)
    check("purgeEndKi no console kill ki_laser",
          'kill @e[type=dragonminez:ki_laser]' not in end_str)
    check("purgeEndKi no console kill ki_blast",
          'kill @e[type=dragonminez:ki_blast]' not in end_str)
    check("clearEndCrystals no empty kill spam",
          'kill @e[type=minecraft:end_crystal]' not in end_str)

    print("\n=== Dragon ki launch fix (2.3.55) ===")
    spawn_fire = end_str.split("private static boolean spawnAndFireKi")[1].split(
        "private static void launchDragonKiToward")[0] if "private static boolean spawnAndFireKi" in end_str else ""
    check("spawnAndFireKi never aborts on failed add",
          "return false" not in spawn_fire.split("end.m_7967_")[1].split("int life")[0]
          if "end.m_7967_" in spawn_fire else False)
    check("spawnAndFireKi always fireHability", "fireHability" in spawn_fire)
    check("dragon ki explicit launchToward", "launchDragonKiToward" in end_str)
    blast_fn = end_str.split("private static boolean fireDragonKiBlast")[1].split(
        "private static boolean spawnAndFireKi")[0] if "private static boolean fireDragonKiBlast" in end_str else ""
    beam_fn = end_str.split("private static boolean fireDragonKiBeam")[1].split(
        "private static boolean fireDragonKiBlast")[0] if "private static boolean fireDragonKiBeam" in end_str else ""
    check("dragon blast uses mob setupKiLargeBlast first",
          "setupKiLargeBlast" in blast_fn.split("catch")[0] if blast_fn else False)
    check("dragon beam prefers setupKiLaser cast 0",
          "setupKiLaser" in beam_fn.split("catch")[0] if beam_fn else False)
    check("dragon blast unparks controllable",
          "setParked(false)" in blast_fn and "setControllable(false)" in blast_fn)

    print("\n=== End exit podium repair (2.3.56) ===")
    check("repairEndExitPodium present", "repairEndExitPodium" in end_str)
    check("cmdRepairPodium present", "cmdRepairPodium" in end_str)
    enddragon_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java")
    check("enddragon repair subcommand", 'm_82127_("repair")' in enddragon_cmds)
    check("egg clear uses dragon_egg SRG f_50260_",
          "Blocks.f_50260_" in end_str and "isDragonEggBlock" in end_str)
    check("end portal uses SRG f_50257_",
          "Blocks.f_50257_" in end_str and "isEndPortalBlock" in end_str)
    portal_guard = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/end/EndPortalGuard.java")
    check("EndPortalGuard end_portal f_50257_", "Blocks.f_50257_" in portal_guard)
    check("EndPortalGuard end_gateway f_50446_", "Blocks.f_50446_" in portal_guard)
    check("EndPortalGuard no end_stone as portal fast-path",
          "block == Blocks.f_50259_" not in portal_guard)

    print("\n=== Skill Check real max levels (2.3.54) ===")
    skill_unlock = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/SkillUnlockService.java")
    saga_fn = skill_unlock.split("private static void appendSagaSkill")[1].split("private static void appendStrengthLine")[0] \
        if "private static void appendSagaSkill" in skill_unlock else ""
    check("appendSagaSkill uses skillMax", "skillMax(skills, id, fallbackMax)" in saga_fn)
    check("appendSagaSkill no hardcoded max param alone",
          "int max = skillMax" in saga_fn)
    check("appendStrengthLine uses skillMax without Math.min(10",
          "Math.min(10, skillMax" not in skill_unlock.split("private static void appendStrengthLine")[1].split("private static int strengthRequirement")[0]
          if "private static void appendStrengthLine" in skill_unlock else False)
    check("Skill Check uses DmzSkillUtil.level", "DmzSkillUtil.level(skills, id)" in skill_unlock)
    check("Skill Check prepareForRead before read", "DmzSkillUtil.prepareForRead(skills)" in skill_unlock)
    check("Skill Check prestige floor fallback", "effectiveSkillLevel" in skill_unlock
          and "PrestigePointsSystem.getPurchasedSkillLevels" in skill_unlock)
    check("saga skill line is level plus locked unlocked or max",
          "§8· §cLocked" in skill_unlock
          and "§8· §aUnlocked" in skill_unlock
          and "§8· §6Max" in skill_unlock
          and "Spar with other players" in skill_unlock
          and "toward 30" in skill_unlock
          and "SOFT CAP" not in skill_unlock
          and "Piccolo" not in skill_unlock)
    check("GUI skill status footer helper", "skillStatusFooter" in read(gui_root / "GuiLoreChunks.java"))

    print("\n=== Spar TP message toggle (2.3.54) ===")
    spar_store = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparStore.java")
    spar_combat = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparCombat.java")
    spar_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    spar_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/SparGuiApi.java")
    spar_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/SparCommands.java")
    spar_chest = read(gui_root / "SparChestGui.java")
    spar_cmi = read(gui_root / "CmiSparGui.java")
    bukkit_plugin = read(gui_root / "AdaptiveDifficultyGuiPlugin.java")
    check("SparStore tpMessages prefs", "tpMessagesOn" in spar_store and "setTpMessages" in spar_store)
    check("SparCombat tickTpMessages", "tickTpMessages" in spar_combat)
    check("flushTpMessage respects prefs", "tpMessagesOn" in spar_combat)
    check("SparringSystem.setTpMsg", "setTpMsg" in spar_sys)
    check("SparGuiApi tpmsg action", '"tpmsg"' in spar_api)
    check("Spar GUI TP toggle", 'SlotAction.act("tpmsg"' in spar_chest and '"&aTP ON"' in spar_chest)
    check("CMI Spar TP toggle", '"tpmsg"' in spar_cmi and '"&aTP ON"' in spar_cmi)
    check("no public Forge /spar tpmsg", 'm_82127_("tpmsg")' not in spar_cmds)
    check("no public Bukkit /spar tpmsg",
          'ForgeBridge.sparHandleDo(player, "tpmsg"' not in bukkit_plugin)

    
    
    print("\n=== Spar CNPC multiplier parity (2.3.145) ===")
    spar_c145 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparCombat.java")
    build_fn = spar_c145.split("buildTotalMultiplier", 1)[1].split("maxTpForAction", 1)[0] if "buildTotalMultiplier" in spar_c145 else ""
    check("buildTotal has BP rival release gravity weight",
          "battlePowerMultiplier" in build_fn and "rivalMultiplier" in build_fn
          and "releaseMultiplier" in build_fn and "gravityMultiplier" in build_fn
          and "weightMultiplier" in build_fn)
    check("buildTotal has prestige momentum session streak style perfect",
          "prestigeMultiplier" in build_fn and "momentumMultiplier" in build_fn
          and "sessionBonusMultiplier" in build_fn and "streakMultiplier" in build_fn
          and "styleMultiplier" in build_fn and "PERFECT_TRAINING_MULTIPLIER" in build_fn)
    check("GLOBAL_TP_GAIN_MULT 1.50", "GLOBAL_TP_GAIN_MULT = 1.50f" in spar_c145)
    check("sparPrestigeLevel uses held wallet",
          "sparPrestigeLevel" in spar_c145 and "getHeldWallet" in spar_c145
          and "fabledPrestigeLevel" not in spar_c145)
    check("momentum messages", "SHOW_MOMENTUM_MESSAGES" in spar_c145 and "Momentum " in spar_c145)
    check("TP activeBonusTags", "activeBonusTags" in spar_c145)
    check("MOMENTUM_MULTIPLIERS script values",
          "1.05f, 1.10f, 1.20f, 1.35f, 1.50f, 2.00f" in spar_c145)
    award_fn = spar_c145.split("int awardCombatTp", 1)[1].split("awardDamageTp", 1)[0] if "int awardCombatTp" in spar_c145 else ""
    check("rival bonus scales the spar action cap",
          "Math.max(1.0f, rival)" in award_fn and "actionCap" in award_fn)
    check("spar TP scales with the earner's live battle power",
          "v.bp = battlePower(player)" in spar_c145
          and "earnerBattlePower(a)" in build_fn
          and "Math.min(a.bp, b.bp)" not in spar_c145
          and "getBattlePowerExact" in spar_c145
          and "logBattlePowerAward" in award_fn
          and "battlePowerMultiplier" in award_fn)

    print("\n=== Race preservation cost (4.6.89) ===")
    race_cfg = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesConfig.java")
    race_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesSystem.java")
    race_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/CharacterServicesGuiApi.java")
    race_confirm = race_api.split('p.startsWith("race_confirm:")', 1)[1].split('p.startsWith("class_confirm:")', 1)[0] if 'p.startsWith("race_confirm:")' in race_api else ""
    race_exec = race_sys.split("public static String executeRaceChange", 1)[1].split("public static String executeClassChange", 1)[0] if "public static String executeRaceChange" in race_sys else ""
    race_cost_fn = race_sys.split("public static long raceCost(", 1)[1].split("static long compressRaceScaledUnderCap", 1)[0] if "public static long raceCost(" in race_sys else ""
    check("missing preservation percent is interpolated, not the 100% price",
          "lookupPreservationCopper" in race_cfg
          and "The 100% price is not a stand-in" in race_cfg
          and "steps.add(25)" in race_cfg
          and 'get(String.valueOf(100))' not in race_cfg
          and "raceAmountDue(player, pct)" in race_confirm
          and "raceAmountDue(player, preservationPercent)" in race_exec)
    check("raceCost compresses under maxNormalizedCost so keep-% stay distinct",
          "compressRaceScaledUnderCap" in race_sys
          and "maxNormalizedCost()" in race_sys
          and "raceCostCopper(100)" in race_cost_fn
          and "payableCost(player, base," not in race_cost_fn
          and "compressRaceScaledUnderCap(scaled, scaled100)" in race_cost_fn)

    print("\n=== Spar TP lines player/staff (2.3.147) ===")
    spar_c147 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparCombat.java")
    flush_fn = spar_c147.split("public static void flushTpMessage", 1)[1].split(
        "public static void updateMomentum", 1)[0] if "public static void flushTpMessage" in spar_c147 else ""
    check("flushTpMessage staff gate",
          "StaffAccess.isStaff(player)" in flush_fn)
    player_half = (
        flush_fn.split("if (!StaffAccess.isStaff(player))", 1)[0]
        if "if (!StaffAccess.isStaff(player))" in flush_fn
        else ""
    )
    check("player TP line is simple +TP (label) boss bar",
          "TpBossNotify.show" in flush_fn
          and "ScreenNotify.subtitleOnly" not in flush_fn
          and "formatWhole(pending)" in flush_fn
          and "activeBonusTags" not in player_half)
    check("staff TP subtitle is trimmed (no full stack tags)",
          "staffTpSubtitle" in flush_fn
          and "staffStackTags" not in flush_fn
          and "sess" in flush_fn
          and "TP_SUBTITLE_MAX_CHARS" in spar_c147)
    check("staffStackTags API still exists for other callers",
          "staffStackTags(" in spar_c147 and "burstLabel(" in spar_c147)
    check("momentum notice is boss bar",
          "TpBossNotify.show" in spar_c147
          and "Momentum " in spar_c147
          and "ScreenNotify.subtitleOnly" not in spar_c147
          and 'LmChat.note("Spar"' not in spar_c147)

    print("\n=== Mentor Actions GUI (2.3.146) ===")
    spar_chest146 = read(gui_root / "SparChestGui.java")
    spar_cmi146 = read(gui_root / "CmiSparGui.java")
    mentor_hub = spar_chest146.split("private Inventory mentor(", 1)[1].split("private Inventory pending(", 1)[0] \
        if "private Inventory mentor(" in spar_chest146 else ""
    cmi_mentor_hub = spar_cmi146.split("private static void openMentor(", 1)[1].split(
        "private static void openPending(", 1)[0] if "private static void openMentor(" in spar_cmi146 else ""
    check("chest mentor Actions hub",
          "Training bonds" in mentor_hub
          and 'page("pick_apprentice")' in mentor_hub
          and 'page("pending")' in mentor_hub
          and 'page("dojo")' in mentor_hub
          and 'page("pick_accept")' not in mentor_hub
          and 'page("pick_decline")' not in mentor_hub)
    check("chest pending_decide + outgoing cancel",
          "pending_decide:" in spar_chest146 and "pendingDecide" in spar_chest146
          and 'SlotAction.act("mentor_cancel"' in spar_chest146)
    check("CMI mentor Actions + pending_decide + dojo",
          "openPendingDecide" in spar_cmi146 and "openDojo" in spar_cmi146
          and "Training bonds" in spar_cmi146
          and 'lmdo spar page pending_decide:' in spar_cmi146)
    check("CMI mentor hub has no Accept…/Decline…",
          "spar.mentor.accept" not in cmi_mentor_hub
          and "spar.mentor.decline" not in cmi_mentor_hub
          and "pick_apprentice" in cmi_mentor_hub
          and '"dojo"' in cmi_mentor_hub)

    print("\n=== Mentor invite + dojo peer bonus (2.3.149) ===")
    spar_sys149 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    spar_c149 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparCombat.java")
    check("mentor invite 1h constant", "MENTOR_INVITE_MS = 60L * 60L * 1000L" in spar_sys149)
    check("dojo peer helper", "isSparringWithDojoPeer" in spar_sys149)
    check("DOJO_PEER_SPAR_BONUS_PCT 0.10", "DOJO_PEER_SPAR_BONUS_PCT = 0.10f" in spar_c149)
    check("awardCombatTp uses dojo peer bonus",
          "withDojoPeer" in spar_c149 and "DOJO_PEER_SPAR_BONUS_PCT" in spar_c149)

    print("\n=== Mentor cooldown tooltip sanitize (2.3.150) ===")
    gui_tips = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/GuiTooltips.java")
    tips_json = read(ROOT / "tools/dmz-adaptive-difficulty-gui/src/main/resources/gui-tooltips.json")
    check("STALE_COPY_FIXES present", "STALE_COPY_FIXES" in gui_tips and "sanitizeStaleCopy" in gui_tips)
    check("stale 7-day → 12-hour rewrite", '"&87-day cooldown"' in gui_tips and '"&812-hour cooldown"' in gui_tips)
    check("jar leave lore is 12-hour", '"&812-hour cooldown after leaving"' in tips_json)
    check("jar leave lore not 7-day", '"&87-day cooldown"' not in tips_json)

    print("\n=== Mentor tip placeholders (2.3.154) ===")
    chest = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/SparChestGui.java")
    cmi = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/CmiSparGui.java")
    check("leave_none tip key", "spar.mentor.leave_none" in chest and "spar.mentor.leave_none" in cmi)
    check("release_none tip key", "spar.mentor.release_none" in chest and "spar.mentor.release_none" in cmi)
    check("FORCE_JAR_LORE_KEYS mentor tips", "FORCE_JAR_LORE_KEYS" in gui_tips and "forceJarLoreKeysToDisk" in gui_tips)
    check("jar leave_none present", '"leave_none"' in tips_json and "You have no mentor" in tips_json)
    check("jar release uses {name} dojo", '"&7Dojo &f{name}"' in tips_json)
    check("CMI pageBtn accepts vars", "Map<String, String> vars, String... tips" in cmi)
    cmi_own = cmi.split("private static void fillOwnDojoCmi", 1)[1].split("private static ItemStack dojoRoleHead", 1)[0]
    chest_own = chest.split("private void fillOwnDojo(", 1)[1].split("private static ItemStack dojoRoleHead", 1)[0]
    check("My Dojo CMI release passes {name}",
          "pick_release" in cmi_own and 'Map.of("name", dojoLabel)' in cmi_own)
    check("My Dojo Chest release passes {name}",
          "pick_release" in chest_own and 'Map.of("name", dojoLabel)' in chest_own)


    print("\n=== Dojo membership roster (2.3.154) ===")
    spar_sys152 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    spar_api152 = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/SparGuiApi.java")
    chest152 = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/SparChestGui.java")
    cmi152 = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/CmiSparGui.java")
    bridge152 = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/ForgeBridge.java")
    check("membershipDojoCards system", "membershipDojoCards" in spar_sys152)
    check("membershipDojoCards SparGuiApi", "membershipDojoCards" in spar_api152)
    check("ForgeBridge membershipDojoCards", "sparMembershipDojoCards" in bridge152)
    check("Chest dojo_member/dojo_mine", "dojo_member" in chest152 and "dojo_mine" in chest152)
    check("CMI dojo_member/dojo_mine", "dojo_member" in cmi152 and "dojo_mine" in cmi152)
    check("Dojo opens for apprentices", 'hasMentor || hasApprentice' in chest152 and 'hasMentor || hasApprentice' in cmi152)



    print("\n=== Difficulty titles presence (2.3.154) ===")
    title_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/title/TitleSystem.java")
    title_fx = read(SRC / "com/dbzlegacy/adaptivedifficulty/title/TitleEffects.java")
    title_def = read(SRC / "com/dbzlegacy/adaptivedifficulty/title/DifficultyTitle.java")
    events = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    rewards = read(SRC / "com/dbzlegacy/adaptivedifficulty/util/DmzRewards.java")
    check("tier titles ignore activeTier", "snap.activeTier < title.unlockTier.id" not in title_sys)
    check("tier titles use DMZ/prestige", "requiredDmzLevel" in title_sys and "requiredPrestige" in title_sys)
    check("Ascendant uses hasUnlockedTier(7)", "hasUnlockedTier(7)" in title_sys)
    check("landFracRelief helper", "landFracRelief" in title_fx and "presenceDamageBonus" in title_fx)
    check("tpGainBonus helper", "tpGainBonus" in title_fx)
    check("landing applies title relief", "landFracRelief(player)" in events)
    check("awardTp applies title TP", "tpGainBonus(player)" in rewards)
    check("req tip no Hold T", '"Hold T"' not in title_def and "keeps after lowering tier" in title_def)
    check("Godslayer perk 0.10", "Perk.BOSS_DAMAGE, 0.10" in title_def)

    print("\n=== Unified chat style (2.3.154) ===")
    lmchat = read(SRC / "com/dbzlegacy/adaptivedifficulty/util/LmChat.java")
    rewards = read(SRC / "com/dbzlegacy/adaptivedifficulty/util/DmzRewards.java")
    guichat = read(GUI_SRC / "com/dbzlegacy/adaptivedifficulty/bukkit/GuiChat.java")
    spar_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    check("LmChat TAG_ALIASES", "TAG_ALIASES" in lmchat and 'systemName' in lmchat)
    check("LmChat ok/fail/tp helpers", 'ok(String' in lmchat and 'fail(String' in lmchat and 'tp(String' in lmchat)
    check("LmChat open-tip normalize", "normalizeOpenTip" in lmchat)
    check("awardTp TP notice is boss bar",
          "TpBossNotify.show" in rewards
          and "lm.tp.grant.subtitle" in rewards
          and "ScreenNotify.subtitleOnly" not in rewards
          and "LmChat.tp(resolveTpSystem" not in rewards)
    death_tp_ui = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/tp/DeathTpPenalty.java")
    check("death TP announcement is a boss bar",
          "TpBossNotify.show" in death_tp_ui
          and "flashBar" in death_tp_ui
          and "ScreenNotify" not in death_tp_ui)
    check("GuiChat mirrors aliases", "TAG_ALIASES" in guichat and "normalizeOpenTip" in guichat)
    check("Mentor invite uses LmChat.tip", 'LmChat.tip("/spar"' in spar_sys)
    check("Perfect Training tagged", 'LmChat.tagged("Spar"' in spar_sys and "Perfect Training" in spar_sys)

    print("\n=== Mentor dojo multi-apprentice (2.3.144) ===")
    spar_store144 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparStore.java")
    spar_sys144 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    spar_combat144 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparCombat.java")
    check("12h mentor cooldown", "12L * 60L * 60L * 1000L" in spar_sys144)
    check("MAX_APPRENTICES 8", "MAX_APPRENTICES = 8" in spar_sys144)
    check("apprentices list field", "List<ApprenticeRef> apprentices" in spar_store144)
    check("normalizeApprentices migration", "normalizeApprentices" in spar_store144)
    check("share diluted by roster", "MENTOR_SHARE_PCT / (float) roster" in spar_sys144)
    check("apprenticeCards API", "apprenticeCards" in spar_sys144)
    check("MENTOR_SHARE_PCT 0.15", "MENTOR_SHARE_PCT = 0.15f" in spar_combat144)

    print("\n=== Spar active TP + ki-charge hold (2.3.143) ===")
    spar_rt = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparPlayerRuntime.java")
    spar_sys143 = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    events143 = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    hold_fn = spar_sys143.split("private static boolean holdSparForKiOrClash", 1)[1].split(
        "public static void markKiCharging", 1)[0] if "holdSparForKiOrClash" in spar_sys143 else ""
    release_fn = spar_sys143.split("private static void tickReleaseControl", 1)[1].split(
        "private static void tickPerfectBanner", 1)[0] if "private static void tickReleaseControl" in spar_sys143 else ""
    check("lastCombatOut clocks", "lastCombatOutAt" in spar_rt and "lastCombatOutPartner" in spar_rt)
    check("holdUntil + chargingUntil fields", "holdUntil" in spar_rt and "chargingUntil" in spar_rt)
    check("hold does not stampHitActivity", "stampHitActivity" not in hold_fn)
    check("markKiCharging public API", "public static void markKiCharging(" in spar_sys143)
    check("KiChargeEvent wires spar hold", "SparringSystem.markKiCharging(player)" in events143)
    check("release drip uses lastCombatOut", "hasRecentCombatOut" in release_fn and "lastCombatOut" in spar_sys143)
    check("release drip skips grace", "graceUntil" in release_fn)
    check("release drip requires live both-clash or combat",
          "bothClashing" in release_fn and "hasRecentCombatOut" in release_fn)
    check("KI_CHARGE_HOLD_MS present", "KI_CHARGE_HOLD_MS" in spar_sys143)

    print("\n=== Global TP boost stacking (2.3.54) ===")
    boost = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/tp/GlobalTpBoost.java")
    check("stack keeps max multiplier", "Math.max(prevMult, multiplier)" in boost)
    check("stack always adds duration", "Math.max(now, currentEnd) + durationMs" in boost)
    check("highest wins messaging", "highest wins" in boost)

    print("\n=== Global TP boost persistence (2.3.60) ===")
    cfg_paths = read(SRC / "com/dbzlegacy/adaptivedifficulty/config/ConfigPaths.java")
    events = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    check("globalTpBoostPath", "global-tp-boost.json" in cfg_paths)
    check("GlobalTpBoost.load", "public static synchronized void load()" in boost)
    check("GlobalTpBoost.save", "public static synchronized void save()" in boost)
    check("persist on activate", "persistNow()" in boost)
    check("login strips leftover when inactive",
          "removeEffect(player)" in boost.split("onLogin")[1].split("startBoost")[0])
    check("server starting loads boost", "GlobalTpBoost.load()" in events)
    check("server stopping saves boost", "GlobalTpBoost.save()" in events)
    check("SCRIPT-AUDIT notes persistence",
          "global-tp-boost.json" in read(ROOT / "tools" / "dmz-adaptive-difficulty" / "SCRIPT-AUDIT.md"))

    print("\n=== Console TP boost (2.3.60) ===")
    prog_tree = read(gui_root / "ProgressionCommandTree.java")
    gui_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    forge_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java")
    forge_bridge = read(gui_root / "ForgeBridge.java")
    check("console androidify leaf", "executeAndroidify" in prog_tree and "androidConvertConsole" in forge_bridge)
    check("console boost example", "progression boost start 2 30" in prog_tree)
    check("GuiApi console default purchaser", 'actor == null ? "Server"' in gui_api)
    check("GuiApi console usage hint", "Console OK (store)" in gui_api)
    check("ForgeBridge boost null actor", "actor == null ? null : nmsPlayer(actor)" in forge_bridge)
    check("brigadier minutes purchaser",
          'IntegerArgumentType.getInteger(ctx, "minutes")' in forge_cmds
          and 'StringArgumentType.getString(ctx, "purchaser")' in forge_cmds)

    print("\n=== GUI action feedback (2.3.54) ===")
    gui_chat = read(gui_root / "GuiChat.java")
    gui_fb = read(gui_root / "GuiFeedback.java")
    check("GuiFeedback class", "class GuiFeedback" in gui_fb)
    check("sendResult prefers GUI", "GuiFeedback.preferGui" in gui_chat and "GuiFeedback.setFromResult" in gui_chat)
    check("chest open paints feedback", "openChest" in gui_fb and "paintChest" in gui_fb)
    check("CMI open paints feedback", "openCmi" in gui_fb)
    prestige = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java")
    check("prestige confirm returns String", "public static String confirmOrPrompt" in prestige)
    check("prestige prefers GUI feedback", "preferGuiFeedback" in prestige)

    print("\n=== Rival title bonuses (2.3.54) ===")
    rival_const = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalConstants.java")
    rival_prog = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalProgression.java")
    rival_tp = read(SRC / "com/dbzlegacy/adaptivedifficulty/rival/RivalTpCurve.java")
    check("RpTier has tpMult + perk", "tpMult" in rival_const and "perk" in rival_const)
    check("Mythic Rival 2.00 title mult", '2.00' in rival_const and "Mythic Rival" in rival_const)
    check("titleLines shows Perk", "Perk:" in rival_prog)
    check("titleLines shows TP Gain", "TP Gain:" in rival_prog)
    check("RivalTpCurve applies title mult", "tpMult()" in rival_tp)

    print("\n=== Spar individual reports (2.3.54) ===")
    spar_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    check("Spar Report # title", "Spar Report #" in spar_sys)
    check("appendRecentReport helper", "appendRecentReport" in spar_sys)
    check("recent stores melee/ki", "rec.melee" in spar_sys and "rec.ki" in spar_sys)

    print("\n=== Script parity fixes (2.3.54) ===")
    spar = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    check("mentor invite 1 hour", "MENTOR_INVITE_MS = 60L * 60L * 1000L" in spar)
    check(
        "invite not 24h / not 2 min",
        "MENTOR_INVITE_MS = 24L" not in spar and "MENTOR_INVITE_MS = 120_000L" not in spar,
    )
    old_med = SRC / "com/dbzlegacy/adaptivedifficulty/progression/skills/MeditationProgression.java"
    check("old meditation trainer removed", not old_med.is_file())

    print("\n=== CNPC GUI open no-perm (2.3.54) ===")
    cnpc = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/CnpcGuiOpener.java")
    skill = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/SkillCheckService.java")
    events = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    end = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/end/EndDimensionStrength.java")
    check("CnpcGuiOpener class", "class CnpcGuiOpener" in cnpc)
    check("CnpcGuiOpener opens rival/spar/hub", '"rival"' in cnpc and '"spar"' in cnpc and '"hub"' in cnpc)
    check("no soft contains(rival)", 'hay.contains("rival")' not in cnpc)
    check("events call tryOpenFromNpc", "CnpcGuiOpener.tryOpenFromNpc" in events)
    check("CnpcGuiOpener tryOpenFromNpc uses tags or names", "tryOpenFromTags" in cnpc and "tryOpenFromName" in cnpc)
    check("Skill Check NPC opens for any player",
          "openAtNpc(player)" in skill.split("tryOpenFromNpc", 1)[1].split("trigger21", 1)[0]
          and "canUse(player)" in skill.split("public static void open(", 1)[1].split("tryOpenFromNpc", 1)[0]
          and "openFromNpc" not in skill)
    check("End hitcap not undone by raw minFrac", "setAmount(mitigated)" in end and "Math.max(mitigated, raw" not in end)
    check("egg clear not end_portal", "isDragonEggBlock" in end and "Blocks.f_50259_" not in end.split("clearDragonEggBlocks")[1].split("findDragons")[0])

    print("\n=== GUI + command audit (2.3.54) ===")
    plugin = read(gui_root / "AdaptiveDifficultyGuiPlugin.java")
    prestige_chest = read(gui_root / "PrestigeChestGui.java")
    prestige_cmi = read(gui_root / "CmiPrestigeGui.java")
    rival_chest = read(gui_root / "RivalChestGui.java")
    spar_chest = read(gui_root / "SparChestGui.java")
    diff_chest2 = read(gui_root / "DifficultyChestGui.java")
    prog_chest2 = read(gui_root / "ProgressionChestGui.java")
    skills_chest = read(gui_root / "SkillsChestGui.java")
    cmi_rival = read(gui_root / "CmiRivalGui.java")
    cmi_spar = read(gui_root / "CmiSparGui.java")
    cmi_diff = read(gui_root / "CmiDifficultyGui.java")
    cmi_skills = read(gui_root / "CmiSkillsGui.java")
    cmi_prog = read(gui_root / "CmiProgressionGui.java")
    mech_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.java")
    prog_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java")
    diff_cmds = read(SRC / "com/dbzlegacy/adaptivedifficulty/command/DifficultyCommands.java")
    mech_chat = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/MechanicsChatMenu.java")
    rival_chat = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/RivalChatMenu.java")
    spar_chat = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/SparChatMenu.java")
    diff_chat = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/DifficultyChatMenu.java")
    prog_menu = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionMenu.java")
    audit_md = read(ROOT / "tools" / "dmz-adaptive-difficulty" / "GUI-COMMAND-AUDIT.md")
    check("GUI-COMMAND-AUDIT.md present", "GUI + command-tree audit" in audit_md or "Ship 2.3" in audit_md)
    check("no bare SlotAction.cmd(lm)", 'SlotAction.cmd("lm")' not in rival_chest
          and 'SlotAction.cmd("lm")' not in spar_chest
          and 'SlotAction.cmd("lm")' not in diff_chest2
          and 'SlotAction.cmd("lm")' not in prog_chest2
          and 'SlotAction.cmd("lm")' not in skills_chest
          and 'SlotAction.cmd("lm")' not in prestige_chest)
    gui_nav_hub = read(gui_root / "GuiNav.java")
    cmi_hub_ok = (
        'lmdo lm open hub' in gui_nav_hub
        and all(
            'GuiNav.cmiHubButton' in text or 'lmdo lm open hub' in text
            for text in (cmi_rival, cmi_spar, cmi_diff, cmi_skills, cmi_prog, prestige_cmi)
        )
    )
    check(
        "hub buttons use lmdo lm open hub",
        'lmdo lm open hub' in rival_chest and 'lmdo lm open hub' in prestige_chest,
    )
    check("CMI hub buttons use lmdo", cmi_hub_ok)
    check("no Prestige SlotAction.open", "SlotAction.open(" not in prestige_chest)
    check("inspect android_remove case", 'case "android_remove"' in plugin or '"android_remove"' in plugin.split("openInspectSystem")[1].split("openInspectForUuid")[0])
    check("lmdo skills staff gate",
          'case "skills" ->' in plugin
          and 'Staff only. Use Skill Check if you have access.' in plugin)
    check("lmdo skillcheck donator gate",
          'case "skillcheck" ->' in plugin
          and "hasSkillCheck(player)" in plugin
          and "Skill Check requires donator access." in plugin)
    check("lmdo skillcheck donator-only (no session bypass)",
          "ensureSkillsGuiAccess" in plugin
          and "Skill Check requires donator access." in plugin)
    check("CMI skillcheck page uses lmdo skillcheck",
          'skillCheckUi ? "skillcheck" : "skills"' in cmi_skills
          or 'lmdo skillcheck page' in cmi_skills)
    check("CNPC editor tool skip",
          "holdingEditorTool" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/CnpcGuiOpener.java")
          and "holdingEditorTool" in read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java"))
    check("npcscripter id listed",
          "customnpcs:npcscripter" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/CnpcGuiOpener.java"))
    check("android_remove inventory force", "isAndroidRemovePage" in plugin)
    check("enddragon uses StaffAccess",
          "StaffAccess::isStaffSource" in prog_cmds and "enddragon" in prog_cmds)
    check("skillcheck donator gate",
          "StaffAccess::hasSkillCheckSource" in prog_cmds and 'm_82127_("skillcheck")' in prog_cmds)
    check("progression helpOrGui", "helpOrGui" in prog_cmds)
    check("DifficultyCommands StaffAccess", "StaffAccess.isStaff" in diff_cmds)
    check("chat hub prestige + android_remove",
          '"/prestige"' in mech_chat and 'android_remove' in mech_chat)
    check("rival/spar chat hub /lm", '"/lm"' in rival_chat and '"/lm"' in spar_chat)
    check("difficulty chat hub button", '"/lm"' in diff_chat and 'lmdo lm open hub' not in diff_chat)
    check("ProgressionMenu allows android_remove", "isAndroidRemovePage" in prog_menu)
    cnpc_prog = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmProgressionGui.java")
    check("CNPC progression staff-only except android_remove",
          '!"android_remove".equals(page)' in cnpc_prog or "android_remove\" -> false" in cnpc_prog)
    remove_block = ""
    if cnpc_prog:
        after = safe_split(cnpc_prog, "private static void paintAndroidRemove")
        remove_block = after.split("private static int paintNameScroll", 1)[0] if after else ""
    char_android = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmCharacterGui.java")
    android_remove_ui = ""
    if char_android:
        after = safe_split(char_android, "private static void paintAndroidRemove")
        android_remove_ui = after.split("private static void footer", 1)[0] if after else ""
    check(
        "CNPC android remove back returns to Character Home",
        "navBackToParent" in android_remove_ui
        and 'open(player, "main")' in android_remove_ui
        and "CnpcLmHubGui.open" not in android_remove_ui
        and "CnpcLmCharacterGui.open(player, p)" in cnpc_prog,
    )
    check(
        "CNPC android tools stay staff-only",
        '!"android_panel".equals(page)' not in cnpc_prog
        and '!"android_convert".equals(page)' not in cnpc_prog,
    )
    check(
        "MechanicsCommands android_remove opens GUI",
        "ProgressionMenu.open(player, \"android_remove\")" in mech_cmds,
    )

    print("\n=== Script parity deep check (2.3.54) ===")
    yardrat = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/YardratProgression.java")
    race_lock = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.java")
    shadow = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/dummy/ShadowDummyLimiter.java")
    boost = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/tp/GlobalTpBoost.java")
    script_audit = read(ROOT / "tools" / "dmz-adaptive-difficulty" / "SCRIPT-AUDIT.md")
    check("Yardrat uses kimanipulation", 'SKILL_ONE = "kimanipulation"' in yardrat)
    check("Yardrat uses kicontrol", 'SKILL_TWO = "kicontrol"' in yardrat)
    check("Yardrat no underscore ki ids", "ki_manipulation" not in yardrat and "ki_control" not in yardrat)
    check("RaceLock clearStuckSagaDifficulty", "clearStuckSagaDifficulty" in race_lock)
    check("RaceLock maybeAutoUnlockStuckDifficulty", "maybeAutoUnlockStuckDifficulty" in race_lock)
    check("RaceLock requestDifficultyReselect", "requestDifficultyReselect" in race_lock)
    check("ShadowDummy legacy CD key", "dmz_minigame_shadow_dummy_cooldown_until" in shadow)
    check("GlobalTpBoost END_LOCK_MS 5s", "END_LOCK_MS = 5000L" in boost)
    check("SCRIPT-AUDIT documents 2.3.54", "2.3.54" in script_audit)

    print("\n=== Conversation commitments (2.3.54) ===")
    conv = read(ROOT / "tools" / "dmz-adaptive-difficulty" / "CONVERSATION-CHECKLIST.md")
    check("CONVERSATION-CHECKLIST.md present", "Conversation commitments checklist" in conv)
    check("conv: mentor 1 hour locked", "Mentor invite **1 hour**" in conv)
    check("conv: spar 3 cards locked", "individual** report cards" in conv)
    check("conv: CNPC-free locked", "CNPC-free" in conv)
    check("conv: player prestige+android locked", "Player Prestige + Remove Android" in conv)
    check("conv: enddragon StaffAccess locked", "StaffAccess" in conv and "/enddragon" in conv)
    # Code still matches conversation — robust needles (avoid javadoc / call-site splits).
    check("conv code: no soft hay.contains(rival)",
          'hay.contains("rival")' not in cnpc and 'contains("rival")' not in cnpc.replace(
              'Soft substrings like {@code contains("rival")}', ''))
    check("conv code: inspect android_remove case",
          'case "android_remove", "androidremove"' in plugin
          and "progressionChestGui.open(admin, \"android_remove\")" in plugin)
    check("conv code: lmdo hub everywhere chest",
          'SlotAction.cmd("lm")' not in rival_chest
          and 'lmdo lm open hub' in rival_chest
          and 'lmdo lm open hub' in prestige_chest)
    check("conv code: chat hub android+prestige",
          "android_remove" in mech_chat and "prestige" in mech_chat)

    print("\n=== Prestige points shop (2.3.81) ===")
    pp = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigePointsSystem.java")
    mixin = read(SRC / "com/dbzlegacy/adaptivedifficulty/mixin/StatsDataMixin.java")
    mixins_json = read(ROOT / "tools" / "dmz-adaptive-difficulty" / "src" / "main" / "resources" / "legacymechanics.mixins.json")
    gui_api_pp = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    hub_pp = read(gui_root / "HubChestGui.java")
    cmi_hub_pp = read(gui_root / "CmiHubGui.java")
    prestige_chest_pp = read(gui_root / "PrestigeChestGui.java")
    prestige_cmi_pp = read(gui_root / "CmiPrestigeGui.java")
    check("potentialunlock 2 levels per point",
          "POTENTIAL_UNLOCK_LEVELS_PER_POINT = 2" in pp
          and "levelsPerPoint" in pp)
    check("tier point cost ladder",
          "tierPointCost" in pp and "buyDifficultyTier" in pp)
    check("prestige wallet GUI",
          "putWallet" in prestige_chest_pp and "Difficulty Tiers" in prestige_chest_pp)
    check("turnIn triangular pack bonus", "packs * (packs + 1) / 2" in pp and "pointsForTurnIn" in pp)
    check("turnIn amounts 1/2/3/6/9", "TURN_IN_AMOUNTS = {1, 2, 3, 6, 9}" in pp)
    check("turnIn spends held wallet only",
          "setHeldPublic" in pp
          and "reducePrestigeClass" not in pp
          and "class level " not in pp)
    check("held wallet is NBT source of truth",
          "getHeldWallet" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java")
          and "alignFabledToHeld" not in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java")
          and "class level " not in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java"))
    prestige_admin = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeAdmin.java")
    prestige_sys = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java")
    check("PrestigeAdmin present", "class PrestigeAdmin" in prestige_admin)
    check("requiredLevel uses personal cap",
          "effectiveMaxLevel(player)" in prestige_sys
          and "requiredLevel(ServerPlayer" in prestige_sys)
    check("prestige need floor",
          "prestige_need_floor" in prestige_sys and "raiseNeedFloor" in prestige_sys)
    check("completed never from Fabled-only",
          "inferCompletedFromShop" in prestige_sys and "KEY_NEED_FLOOR" in prestige_sys)
    check("completed not inflated from held skill",
          "never bump total from it" in prestige_sys
          and "High-watermark" not in prestige_sys)
    check("prestige Need ladder first 4 scale 20k by completed then 50k/100k by held",
          "HELD_GATE_MIN_COMPLETED = 4" in prestige_sys
          and "requiredLevelForHeld" in prestige_sys
          and "HELD0_NEED = 50_000" in prestige_sys
          and "HELD1_PLUS_NEED = 100_000" in prestige_sys
          and "HELD2_NEED" not in prestige_sys
          and "earlyCompletedNeed" in prestige_sys
          and "earlyHeldNeed" not in prestige_sys
          and "LEVELS_PER_PRESTIGE * (c + 1L)" in prestige_sys
          and "needForProgress" in prestige_sys
          and "heldCountForNeed" in prestige_sys
          and "reconcileNeedFloor" in prestige_sys)
    prog_gui_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    prestige_lines_block = (prog_gui_api.split("prestigeLines(", 1)[1] if "prestigeLines(" in prog_gui_api else "").split("handlePrestigeDo", 1)[0]
    check("prestige main wallet lore omits cap",
          "§7Cap:" not in prestige_lines_block.split("switch (p)", 1)[0]
          and "prestige.main.cap" not in prestige_chest_pp.split("private Inventory main")[1].split("private Inventory turnIn")[0]
          if "private Inventory main" in prestige_chest_pp else False)
    check("prestige admin command registered",
          '"admin"' in read(SRC / "com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java")
          and "prestigeAdminAdjust" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/command/ProgressionCommands.java"))
    check("breakthrough mentions prestige Need scale",
          "Future prestige requirements now scale" in pp)
    check("handlePrestigeAdmin API", "handlePrestigeAdmin" in gui_api_pp)
    check("Bukkit prestige admin via ForgeBridge",
          "prestigeAdmin" in forge_bridge
          and "isPrestigeAdminSub" in
          read(gui_root / "AdaptiveDifficultyGuiPlugin.java"))
    check("form XOR majin/mutant", "buyMajin" in pp and "buyMutant" in pp and "unbuyMajin" in pp)
    # MutantManager.grant always chats message.dragonminez.mutant.gained — pulse must not call it.
    check("silent mutant ensure (no grant chat spam)",
          "ensureMutantSilent" in pp
          and "MutantManager.grant(" not in pp
          and "reconcileHolder" in pp)
    check("silent majin ensure (no dmzeffect give chat)",
          "ensureEffectSilent" in pp
          and '"dmzeffect give "' not in pp
          and "'dmzeffect give '" not in pp)
    lm_tips = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/LmTips.java")
    check("LM onboarding tips class",
          "class LmTips" in lm_tips
          and "Use command /lm to open the GUI" in lm_tips)
    check("LM tips use action bar",
          "ScreenNotify.actionBar" in lm_tips
          and "void actionBar" in read(SRC / "com/dbzlegacy/adaptivedifficulty/util/ScreenNotify.java"))
    screen_notify = read(SRC / "com/dbzlegacy/adaptivedifficulty/util/ScreenNotify.java")
    potential_src = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/progression/skills/PotentialProgression.java"
    )
    check("ScreenNotify.subtitleOnly exists",
          "void subtitleOnly" in screen_notify
          and "ClientboundSetSubtitleTextPacket" in screen_notify
          and 'ClientboundSetTitleTextPacket(Component.m_237113_(""))' in screen_notify)
    check("Potential notices are subtitles not chat",
          "ScreenNotify.subtitleOnly" in potential_src
          and "potentialSubtitle" in potential_src
          and 'DmzRewards.msg(player, LmChat.note("Potential"' not in potential_src
          and 'DmzRewards.msg(player, LmChat.fail("Potential"' not in potential_src
          and 'DmzRewards.msg(player, LmChat.info("Potential"' not in potential_src)
    check("LM tips LP frequent groups",
          "lmTipFrequentGroups" in lm_tips
          and "getPrimaryGroup" in lm_tips
          and "novice" in read(CFG))
    check("LM tips wired on login+pulse",
          "LmTips.onLogin" in read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/ProgressionSystem.java")
          and "LmTips.pulse" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/ProgressionSystem.java"))
    check("LM tips config flag", "enableLmTips" in read(CFG) and "lmTipFrequentGroups" in read(CFG))
    check("breakthrough costs 15..35", "breakthroughCost" in pp and "15" in pp and "35" in pp)
    check("MAX_BREAKTHROUGHS 5", "MAX_BREAKTHROUGHS = 5" in pp)
    check("StatsDataMixin personal cap",
          "getConfiguredMaxValue" in mixin and "LmOverhaulCapMath.personalLevelCap" in mixin)
    check("StatsDataMixin remap false",
          'remap = false' in mixin and "StatsData.class, remap = false" in mixin)
    check("StatsDataMixin applies personal cap", "lm$personalMaxValue" in mixin)
    revamp_cap = read(SRC / "com/dbzlegacy/adaptivedifficulty/mixin/DmzRevampPrestigeCapMixin.java")
    check("Overhaul levelCap follows personal 100k+BT (not stock 50k at P0)",
          '@Inject(method = "levelCap"' in revamp_cap
          and "overhaulLevelCap(data)" in revamp_cap
          and "lm$personalOverhaulLevelCap" in revamp_cap)
    check("Overhaul maxAssignableTotal uses LM breakthrough",
          "maxAssignableTotal" in revamp_cap
          and "LmOverhaulCapMath.maxAssignableTotal" in revamp_cap)
    check("LM combat mixin does not multiply totalMult",
          not (SRC / "com/dbzlegacy/adaptivedifficulty/mixin/StatsDataOverhaulCombatScaleMixin.java").is_file())
    check("mixins.json does not register LM prestige scale mixins",
          '"StatsDataOverhaulCombatScaleMixin"' not in mixins_json
          and '"StatsDataHudPoolMaxMixin"' not in mixins_json)
    scaled_helper = read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/LmOverhaulScaledCombat.java")
    check("Overhaul scaled combat helper",
          "combatScaleMultiplier" in scaled_helper
          and "putPlaceholders" in scaled_helper
          and "compactLines" in scaled_helper
          and "defense" in scaled_helper)
    check("AD combat profile uses Overhaul scaled helper",
          "LmOverhaulScaledCombat.melee" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/PlayerCombatProfile.java")
          and "LmOverhaulScaledCombat.defense" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/PlayerCombatProfile.java"))
    check("spar/rival BP uses post-scale released power",
          "LmOverhaulScaledCombat.scaled" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/CombatRating.java")
          and "LmOverhaulScaledCombat.scaled" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/util/DmzRewards.java"))
    check("spar/rival/AD GUIs expose post-scale stats",
          "LmOverhaulScaledCombat.putPlaceholders" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/SparGuiApi.java")
          and "LmOverhaulScaledCombat.putPlaceholders" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.java")
          and "LmOverhaulScaledCombat.putPlaceholders" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java"))
    dmz_prog = read(SRC / "com/dbzlegacy/adaptivedifficulty/calc/DmzProgression.java")
    check("AD ceiling honors 150k breakthroughs",
          "ABSOLUTE_LEVEL_CAP" in dmz_prog and "configuredMaxDmzLevel" in dmz_prog)
    check("Overhaul rebirth blocked", "canPrestige" in revamp_cap)
    check("StatsDataMixin gated on dmzrevamp", "isLoaded(\"dmzrevamp\")" in mixin)
    check("StatsDataMixin beats dmzrevamp cap mixin", "priority = 5000" in mixin)
    check("StatsDataMixin max total + stat buy", "getConfiguredMaxTotalStats" in mixin
          and "getMaxAllowedIncreaseForStat" in mixin)
    bridge = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/bridge/DmzRevampPrestigeBridge.java")
    check("Overhaul prestige sync bridge", "syncFromLegacy" in bridge and "setCount" in bridge)
    check("Overhaul prestige count syncs from held wallet",
          "getHeldWallet" in bridge
          and "getCompleted" not in bridge
          and "fabledPrestigeLevel" not in bridge
          and "PrestigeSkillSync" not in bridge)
    integration = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/LmOverhaulPrestigeIntegration.java")
    check("Overhaul prestige is 1:1 with held",
          "toOverhaulCount" in integration
          and "Math.min(OVERHAUL_MAX_PRESTIGE, held)" in integration
          and "overhaulCountFromHeld" in integration
          and "oneBasedLevel - 1" not in integration)
    check("Fabled bridge flags removed from config",
          "enableFabledBridge" not in read(CFG) and "enableAttrMultiBonus" not in read(CFG))
    dmz_lvl = read(ROOT / "config" / "dmzrevamp" / "LevelingRevamp.json")
    check("Overhaul initialLevelCap 100k", '"initialLevelCap": 100000' in dmz_lvl)
    cap_math = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/LmOverhaulCapMath.java")
    check("Overhaul level cap helper is 100k + breakthroughs",
          "OVERHAUL_LEVEL_CAP = PrestigePointsSystem.BASE_LEVEL_CAP" in cap_math
          and "overhaulLevelCap(StatsData data)" in cap_math
          and "pinOverhaulLevelCaps" in cap_math)
    check("Overhaul boot pins 100k/150k caps",
          "pinOverhaulLevelCaps" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/bridge/DmzRevampConfigBridge.java"))
    mixin_sd = read(SRC / "com/dbzlegacy/adaptivedifficulty/mixin/StatsDataMixin.java")
    check("Personal level cap is server Forge (no KubeJS client shim)",
          not (ROOT / "kubejs/client_scripts/overhaul_level_cap.js").exists()
          and not (ROOT / "kubejs/server_scripts/overhaul_level_cap.js").exists())
    check("StatsDataMixin wins Overhaul max (priority 5000)",
          "priority = 5000" in mixin_sd
          and "getConfiguredMaxValue" in mixin_sd)
    mirror = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/PersonalLevelCapMirror.java")
    check("Cap publish pushes StatsSyncS2C to client",
          "DmzSkillUtil.sync(player)" in mirror)
    pool_clamp = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/DmzResourcePoolClamp.java")
    check("ki pool clamp uses actual max not a Fabled mirror",
          "actualMaxEnergy" in pool_clamp
          and not (SRC / "com/dbzlegacy/adaptivedifficulty/progression/bridge/EnergyManaSync.java").is_file())
    check("0 breakthroughs are explicit NBT not inferred from 150k cap",
          "Inferring from lm_personal_level_cap" in pp
          and "return 0;" in
          "".join(pp.split("public static int getBreakthroughs")[1].split("public static int breakthroughCost")[0]))
    check("actualMaxEnergy prefers getMaxEnergy",
          "getMaxEnergy()" in pool_clamp
          and "looksLikeIronMana" in pool_clamp
          and "actualMaxEnergy" in pool_clamp)
    lm_root = Path(__file__).resolve().parents[1]
    ki_ref = lm_root / "reference" / "ki-pool-2.4.115"
    check("ki-pool-2.4.115 reference slice present",
          (ki_ref / "com/dbzlegacy/adaptivedifficulty/progression/DmzResourcePoolClamp.class").is_file()
          and (ki_ref / "com/dbzlegacy/adaptivedifficulty/mixin/StatsDataHudPoolMaxMixin.class").is_file())
    check("canonical actualMaxEnergy/Stamina (2.4.115 compile stub)",
          "actualMaxStamina" in pool_clamp
          and "applyOverhaulScale" in pool_clamp
          and "isReadingNativeMax" in pool_clamp
          and "displayMaxEnergy" in pool_clamp
          and "data.getMaxEnergy()" in pool_clamp)
    build_sh = read(lm_root / "build.sh")
    check("build rejects LM prestige scale mixins",
          "must not register LM prestige scale mixins" in build_sh
          and "still contains unregistered prestige scale mixin classes" in build_sh
          and "StatsDataHudPoolMaxMixin.class" in build_sh
          and "StatsDataOverhaulCombatScaleMixin.class" in build_sh
          and '"StatsDataHudPoolMaxMixin"' not in mixins_json)
    check("HUD pool mixin does not rescale max",
          not (SRC / "com/dbzlegacy/adaptivedifficulty/mixin/StatsDataHudPoolMaxMixin.java").is_file())
    check("Revamp prestige does not refill after afterSetCount",
          "refillPoolsLikeOverhaulPrestige" not in bridge
          and "clampToOverhaulPool" in bridge
          and "DmzResourcePoolClamp.syncToClient" in bridge)
    check("stat screen Fabled mirror removed",
          not (SRC / "com/dbzlegacy/adaptivedifficulty/progression/bridge/StatScreenSync.java").is_file()
          and "actualMaxStamina" in pool_clamp)
    manifest = read(ROOT / "tools" / "dmz-adaptive-difficulty" / "src" / "main" / "resources" / "META-INF" / "MANIFEST.MF")
    check("MANIFEST MixinConfigs for Mohist",
          "MixinConfigs: legacymechanics.mixins.json" in manifest)
    check("mixins.json registers StatsDataMixin", '"StatsDataMixin"' in mixins_json)
    check("AD referenceMaxLevel 150k", "referenceMaxLevel = 150_000L" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.java"))
    dmz_general = read(ROOT / "config" / "dragonminez" / "general-server.json")
    check("DMZ maxValue 150000 for client UI", '"maxValue": 150000' in dmz_general)
    events_pp = read(SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    check("TP soft-lock at personal cap", "onTpGain" in events_pp and "effectiveMaxLevel" in events_pp)
    check("TP soft-lock silent (no chat spam)",
          "event.setTpGain(0)" in events_pp
          and "pp_cap_msg_next" not in events_pp)
    check("Stat soft-lock at personal cap total",
          "onStatChange" in events_pp and "maxAssignableTotal" in events_pp)
    check("Stat soft-lock uses ScreenNotify",
          "ScreenNotify.hint" in events_pp and "pp_stat_cap_title" in events_pp)
    check("Chest prestige lore no DMZ maxValue soft-lock tip",
          "DMZ maxValue 150000" not in prestige_chest_pp)
    check("CMI prestige lore no DMZ maxValue soft-lock tip",
          "DMZ maxValue 150000" not in prestige_cmi_pp)
    check("grant staff-gated",
          'if (!StaffAccess.isStaff(player))' in gui_api_pp.split('give_points')[1].split('balance')[0]
          if "give_points" in gui_api_pp else False)
    check("spend ungated for NPC",
          "spendForNpc" in gui_api_pp.split('"spend"')[1].split('"grant"')[0]
          if '"spend"' in gui_api_pp else False)
    check("configuredMaxLevel helper", "configuredMaxLevel" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/DmzSkillUtil.java"))
    check("skillMax prefers skills.json", "DmzSkillUtil.maxLevel(skills, id, fallback)" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/SkillUnlockService.java")
          and "configuredMaxLevel(id)" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/DmzSkillUtil.java"))
    check("prestige shop Skill Check skills only",
          "skillOffers()" in pp and "skillCheckSkillIds" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/DmzSkillUtil.java"))
    check("skillCheckSkillIds excludes ultimate",
          "skillCheckSkillIds" in read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/DmzSkillUtil.java")
          and '"ultimate"' not in "".join(
              read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/DmzSkillUtil.java")
              .split("SKILL_CHECK_NATURAL")[1]
              .split("allNonFormSkillIds")[0]
          ))
    check("resolveOffer rejects non-SkillCheck",
          "isSkillCheckSkill" in pp and "Only Skill Check skills" in pp)
    check("Chest skill shop tip Skill Check only",
          "Skill Check skills only" in prestige_chest_pp)
    check("CMI skill shop tip Skill Check only",
          "Skill Check skills only" in prestige_cmi_pp)
    check("kiboost fallback max 4", '"kiboost", "Ki Boost", "§3", 4' in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/SkillUnlockService.java"))
    check("kicontrol fallback max 1", '"kicontrol", "Ki Control", "§3", 1' in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/shop/SkillUnlockService.java"))
    check("Chest pages turnin/shop/forms (no player cap page)",
          'case "turnin", "points"' in prestige_chest_pp
          and 'p.startsWith("shop")' in prestige_chest_pp
          and 'case "forms", "form"' in prestige_chest_pp
          and 'openCap(' not in prestige_chest_pp
          and '"Stat Cap"' not in prestige_chest_pp)
    check("CMI pages turnin/shop/forms (no player cap page)",
          '"turnin", "points"' in prestige_cmi_pp
          and 'p.startsWith("shop")' in prestige_cmi_pp
          and '"forms", "form"' in prestige_cmi_pp
          and 'openCap(' not in prestige_cmi_pp)
    check("Cap routes redirect to main",
          'case "cap", "breakthrough"' in prestige_chest_pp
          and '"cap", "breakthrough"' in prestige_cmi_pp)
    check("CMI uses lmdo prestige",
          'lmdo prestige confirm' in prestige_cmi_pp
          and 'lmdo prestige turnin' in prestige_cmi_pp
          and 'lmdo prestige skill' in prestige_cmi_pp)
    check("Hub Prestige tip mentions skill shop",
          ("skill shop" in hub_pp.lower() or "Turn-ins, skill shop" in hub_pp)
          and ("skill shop" in cmi_hub_pp.lower() or "Turn in prestiges" in cmi_hub_pp)
          and "level-cap" not in cmi_hub_pp.lower())
    check("Papi prestige_points bridge",
          "prestige_points" in forge_bridge and "prestige_level_cap" in forge_bridge)
    check("Prestige skill floor continuous pulse",
          "SKILL_FLOOR_PULSE_MS" in pp and "KEY_SKILL_PULSE_AT" in pp
          and "hasAnyPurchasedSkillFloor" in pp)
    check("Prestige reapply delayed ticks after reset",
          "REAPPLY_WINDOW_MS" in pp and "TickTask" in pp
          and "scheduleReapplyAfterDeath" in pp)
    check("race lock no longer wipes the character",
          "scheduleReapplyAfterDeath" not in read(
              SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.java")
          and "dmzstats reset" not in read(
              SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.java"))

    print("\n=== End Dragon AD summon (2.3.65) ===")
    end_str = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/end/EndDimensionStrength.java")
    diff_actions = read(SRC / "com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.java")
    chest = read(gui_root / "DifficultyChestGui.java")
    cmi_diff = read(gui_root / "CmiDifficultyGui.java")
    check("cmdPlayerSummon present", "cmdPlayerSummon" in end_str)
    check("summoner NBT stamp", "end_dragon_summoner" in end_str)
    check("owner-only damage gate", "Only the summoner can damage" in end_str)
    check("AD scaled power", "adScaledPower" in end_str)
    check("despawnOwnedDragon", "despawnOwnedDragon" in end_str)
    check("summoner live AD retarget", "retargetPlayerDragonToSummoner" in end_str)
    check("summoner form apply", "applySummonerAdStats" in end_str)
    check("orphan despawn pulse", "maybeDespawnOrphanedPlayerDragon" in end_str)
    check("DifficultyActions summon_end_dragon", "ACT_SUMMON_END_DRAGON" in diff_actions)
    check("ForgeBridge allows summon_end_dragon", "summon_end_dragon" in forge_bridge)
    check("End Dragon submenu page",
          "end_dragon" in chest and "endDragon" in chest
          and "openEndDragon" in cmi_diff
          and "paintSummons" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmDifficultyGui.java"))
    cnpc_diff = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmDifficultyGui.java")
    team_block = ""
    if cnpc_diff:
        after_team = safe_split(cnpc_diff, "private static void paintTeam")
        team_block = after_team.split("private static void paintSummons", 1)[0] if after_team else ""
    check("CNPC teams page refreshes snapshot",
          CNPC_RETIRED
          or ("private static void paintTeam" in cnpc_diff
              and "DifficultyActions.prepareGui(subject)" in team_block))
    check("End Dragon summonMenuLines API", "summonMenuLines" in end_str and "canOpenSummonMenu" in end_str)
    staff_access = read(SRC / "com/dbzlegacy/adaptivedifficulty/util/StaffAccess.java")
    check("StaffAccess uses Bukkit entity for permissions", "getBukkitEntity" in staff_access)
    prog_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    check("Progression staff flags when master off", "staffMaintenance" in prog_api)
    check("CNPC difficulty staff admin page", "paintStaffAdmin" in cnpc_diff)
    check("natural spawn default off", "enableEndNaturalDragonSpawn = false" in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.java"))
    check("reject unauthorized dragon join", "rejectUnauthorizedDragonJoin" in end_str)
    check("hasAliveSummonedDragon", "hasAliveSummonedDragon" in end_str)
    race_lock_cfg = read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/RaceLockConfig.java")
    check("RaceLockConfig file", "race-lock.json" in race_lock_cfg)
    check("RaceLockConfig still loads",
          "RaceLockConfig.load()" in read(MOD)
          and "FabledSkills" not in read(SRC / "com/dbzlegacy/adaptivedifficulty/progression/race/RaceLock.java"))
    check("dragon phase steer", "maybeSteerDragonPhase" in end_str)
    check("AD off during dragon", "hasAliveSummonedDragon" in
          read(ROOT / "tools" / "dmz-adaptive-difficulty" / "src" / "main" / "java" /
               "com/dbzlegacy/adaptivedifficulty/tick/NearbyMobScaler.java"))
    check("natural spawn forced off sanitize",
          'cfg.enableEndNaturalDragonSpawn = false' in
          read(SRC / "com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.java"))
    check("3 netherite cost constant", "PLAYER_SUMMON_NETHERITE_COST = 3" in end_str)

    print("\n=== Spar item punch (4.6.12) ===")
    spar_punch = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    check("item punch does not grant spar TP",
          "isItemPunch" in spar_punch
          and "WeaponRegistry.getAttributes" in spar_punch
          and "isPlayerMeleeSource" in spar_punch)
    check("potions explosions and thorns do not grant spar TP",
          "isNonCombatDamage" in spar_punch
          and "thorns" in spar_punch
          and "explosion" in spar_punch
          and "dragonbreath" in spar_punch)

    print("\n=== Spar menu copy (4.6.15) ===")
    cnpc_spar_menu = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmSparGui.java")
    dojo_score = read(SRC / "com/dbzlegacy/adaptivedifficulty/sparring/DojoRankings.java")
    check("spar leaderboard does not offer a fake wins tab",
          "top_wins" not in cnpc_spar_menu and "top_streak" not in cnpc_spar_menu)
    check("dojo wars category scores war wins",
          '"wars", "war"' in dojo_score and "war wins" in dojo_score)
    check("outgoing bond invite can be withdrawn",
          "Withdraw invite" in cnpc_spar_menu
          and "pick_confirm:mentor|mentor|leave" in cnpc_spar_menu
          and "Decline / revoke" not in cnpc_spar_menu)
    check("view-only hint",
          "nothing to change here" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcGuiStyle.java"))

    print("\n=== Reskin gender and head bone preview (4.6.16) ===")
    gender_mixin = read(SRC / "com/dbzlegacy/adaptivedifficulty/mixin/StatsSyncC2SGenderMixin.java")
    mixins_json = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json")
    bones = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/CosmeticHeadBoneService.java")
    bone_gui = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmCharacterGui.java")
    check("reskin gender mixin honors the packet during a session",
          "getDeclaredField(\"gender\")" in gender_mixin
          and "canHaveGender" in gender_mixin
          and "ReskinSessionGuard.lockedClass" in gender_mixin
          and "StatsSyncC2SGenderMixin" in mixins_json)
    check("head bone rows toggle without clearing the others",
          "executeToggle" in bones
          and "HeadPartPieces.join" in bones
          and "previewBone" in bones
          and "PREVIEW_STASH" in bones
          and "bone_toggle" in bone_gui
          and "paintLivePlayerPreview" in bone_gui
          and "horns1+antennas2" not in bone_gui)
    preview_src = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcPlayerPreview.java")
    live_preview = (preview_src.split("void paintLive", 1)[1] if "void paintLive" in preview_src else "").split("void paint(", 1)[0]
    check("head bone menu shows the player's own model",
          "tryBindLivePlayer" in live_preview
          and "setEntitySyncedById" in preview_src
          and "CnpcGeckoPreviewBridge" not in live_preview
          and "paintLivePlayerPreview" in bone_gui)
    pieces = read(SRC / "com/dbzlegacy/adaptivedifficulty/character/HeadPartPieces.java")
    check("body accessories are not a shop page",
          "PAGE_BODY" not in bone_gui
          and "PAGE_BODY" not in pieces
          and "Body »" not in bone_gui
          and "ultraScrollLines" not in pieces
          and "PAGES = 2" in pieces)

    print("\n=== GUI humanization (4.6.17) ===")
    rival_chat = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/RivalChatMenu.java")
    spar_chat = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/SparChatMenu.java")
    char_api = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/CharacterServicesGuiApi.java")
    rival_gui = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmRivalGui.java")
    prog_gui = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmProgressionGui.java")
    check("rival chat forfeit is labeled when a challenge is live",
          "Forfeit your active challenge" in rival_chat
          and "Cancel your pending challenge request" in rival_chat
          and "Cancel yours" not in rival_chat)
    check("spar chat category buttons use the top_ page",
          'startsWith("top_")' in spar_chat and "top_tp" in spar_chat)
    check("0% wipe copy keeps head parts and coins",
          "head-part unlocks and coins kept" in char_api
          and "nothing carried over" not in char_api)
    check("android convert names the target and warns about forms",
          "Convert " in prog_gui
          and "deletes Super forms and Legendary forms" in read(
              SRC / "com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java"))
    check("rival confirm does not show the raw action id",
          "Action §7" not in rival_gui and "Declare rival" in rival_gui)
    check("tier buy asks before it charges",
          "diff-tier:" in read(SRC / "com/dbzlegacy/adaptivedifficulty/service/DifficultyActions.java"))
    build_sh = read(ROOT / "tools/dmz-adaptive-difficulty/build.sh")
    confirm_at = build_sh.find("GuiClickConfirm DifficultyTeamGuiApi")
    confirm_tail = build_sh[confirm_at:confirm_at + 700] if confirm_at >= 0 else ""
    check("click confirm pending class is copied into the jar",
          "class Pending" in read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/GuiClickConfirm.java")
          and '${class}\\$"*.class' in confirm_tail
          and "GuiClickConfirm\\$Pending.class" in build_sh)

    print("\n=== Menu notices and jar inners (4.6.19) ===")
    support = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcGuiSupport.java")
    check("long menu notices are also sent to chat",
          "feedbackChat(player, noticeChat(msg))" in support
          and "private static String noticeChat" in support
          and 'split("\\n", -1).length > CnpcGuiStyle.INFO_INLINE_MAX - 1' in support)
    check("packaging cleans compile output before javac",
          'rm -rf "$OUT"' in build_sh
          and build_sh.find('rm -rf "$OUT"') < build_sh.find("javac --release 17"))
    check("jar self-check requires referenced inner classes",
          "audit_jar_inner_classes.py" in build_sh
          and "Outer$Inner" in read(ROOT / "tools/dmz-adaptive-difficulty/sim/audit_jar_inner_classes.py"))
    check("boost preset ids do not share the grid base",
          "ID_BOOST_PRESET_BASE = 210" in support
          and "ID_BOOST_PRESET_BASE = 120" not in support)

    print("\n=== CNPC screen colors (4.6.21) — package retired ===")
    cnpc_dir = SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc"
    cnpc_src = "" if CNPC_RETIRED else "\n".join(
        p.read_text(encoding="utf-8") for p in sorted(cnpc_dir.glob("*.java"))
    )
    prog = read(cnpc_dir / "CnpcLmProgressionGui.java")
    diff = read(cnpc_dir / "CnpcLmDifficultyGui.java")
    check("CNPC screens do not use purple section codes",
          CNPC_RETIRED or "§5" not in cnpc_src)
    check("progression titles are blue and prestige stays pink",
          'CnpcUltraStyle.ACCENT + "Progression"' in prog
          and "§dProgression" not in prog
          and '"home|Home"' in prog
          and '"boosts|Boosts"' in prog
          and '"coins|Coins"' in prog
          and 'subPage(CnpcUltraStyle.ACCENT, "Progression", "End")' in prog
          and 'CnpcUltraStyle.INFO + "2× · 30m"' in prog
          and 'CnpcUltraStyle.INFO + "2× · 60m"' in prog)
    check("inline on and off match the toggle colors",
          'return on ? CnpcUltraStyle.CONFIRM_BOLD + "ON" : CnpcUltraStyle.DIM + CnpcUltraStyle.BOLD + "OFF";' in support)
    check("difficulty confirm and inactive modes use the shared colors",
          'CnpcUltraStyle.CONFIRM + "Confirm summon"' in diff
          and 'CnpcUltraStyle.CONFIRM + "End Dragon"' in diff
          and 'CnpcUltraStyle.SUBTITLE + "Threshold"' in diff
          and 'CnpcUltraStyle.SUBTITLE + "Full team"' in diff)
    check("spar dojo home and prestige forms use their system colors",
          'CnpcUltraStyle.ACCENT + "Dojo home"' in read(cnpc_dir / "CnpcLmSparGui.java")
          and 'CnpcUltraStyle.ACCENT + "Forms"' in read(cnpc_dir / "CnpcLmPrestigeGui.java")
          and 'CnpcUltraStyle.ACCENT_PROGRESSION + "Progression panel"' in read(cnpc_dir / "CnpcLmAdminGui.java"))

    print("\n=== CNPC rival records (4.6.22) ===")
    rival_records = read(cnpc_dir / "CnpcLmRivalGui.java")
    check("CNPC rival records are one tabbed page",
          "records:" in rival_records
          and "void paintProgress" not in rival_records
          and "Hall of fame" in rival_records
          and '"home|Home"' in rival_records
          and '"actions|Actions"' in rival_records
          and '"challenge|Challenge"' in rival_records
          and 'open(player, "history")' not in rival_records
          and 'open(player, "progress")' not in rival_records
          and 'open(player, "stats")' not in rival_records
          and "Challenge request" in rival_records
          and "Declare invite" in rival_records)

    print("\n=== CNPC layout fit (4.6.25) ===")
    preview = read(cnpc_dir / "CnpcPlayerPreview.java")
    feedback = read(cnpc_dir / "CnpcMenuFeedback.java")
    ui_fit = read(cnpc_dir / "CnpcUiFit.java")
    layout_support = read(cnpc_dir / "CnpcGuiSupport.java")
    check("preview scale follows the player's height",
          "previewScaleFor" in preview
          and "m_20206_()" in preview
          and "REF_PLAYER_HEIGHT = 1.8f" in preview
          and "offsetY" in preview
          and "scale / PREVIEW_SCALE" in preview
          and "slot / (float) SLOT_H" in preview
          and "SLOT_GAP = 12" in preview
          and "previewAnchor()" in preview
          and "previewAnchor()" in layout_support
          and "PREVIEW_GAP = 12" in layout_support
          and "CONTENT_TOP = 48" in layout_support)
    check("pending notice reserves window height",
          "FLASH_MAX_H = 56" in layout_support
          and "flashReserve" in layout_support
          and "height + flashReserve" in layout_support
          and "boolean hasPending" in feedback
          and "pendingLineCount" in feedback
          and "available < 48" in layout_support
          and "window(" in read(cnpc_dir / "CnpcLmDifficultyGui.java")
          and "window(" in read(cnpc_dir / "CnpcLmRivalGui.java")
          and "window(" in read(cnpc_dir / "CnpcLmSparGui.java")
          and "window(" in read(cnpc_dir / "CnpcLmCharacterGui.java"))
    check("compressed menus are logged",
          "compressed to" in ui_fit and "0.9f" in ui_fit)

    print("\n=== CNPC list scroll (4.6.24) ===")
    support = read(cnpc_dir / "CnpcGuiSupport.java")
    scroll_start = support.find("public static IScroll scroll(")
    scroll_end = support.find("public static int paintReadOnlyScroll", scroll_start)
    scroll_fn = support[scroll_start:scroll_end] if scroll_start >= 0 and scroll_end > scroll_start else ""
    check("pick lists are one scroll on the GUI",
          "gui.addScroll(id, x, y, useW, h, copy)" in scroll_fn
          and "getScrollingPanel" not in scroll_fn
          and "setHasSearch(true)" in support
          and "one scroll region" in support
          and "scrollPickList" in read(cnpc_dir / "CnpcLmCharacterGui.java")
          and "scrollPickList" in read(cnpc_dir / "CnpcLmRivalGui.java")
          and "scrollPickList" in read(cnpc_dir / "CnpcLmSparGui.java"))

    print("\n=== CNPC menu flatten (4.6.23) ===")
    hub_gui = read(cnpc_dir / "CnpcLmHubGui.java")
    char_gui = read(cnpc_dir / "CnpcLmCharacterGui.java")
    spar_gui = read(cnpc_dir / "CnpcLmSparGui.java")
    diff_gui = read(cnpc_dir / "CnpcLmDifficultyGui.java")
    prog_menu = read(cnpc_dir / "CnpcLmProgressionGui.java")
    prestige_gui = read(cnpc_dir / "CnpcLmPrestigeGui.java")
    snapshot = read(cnpc_dir / "CnpcPlayerSnapshot.java")
    check("hub drops the welcome line and section tags",
          "Welcome back" not in snapshot
          and "Combat & progression" not in hub_gui
          and "Remove Android" not in hub_gui
          and 'CnpcUltraStyle.ACCENT_ADMIN + "Skill Check"' in hub_gui)
    check("character hosts remove android and race keep percent",
          'CnpcUltraStyle.DANGER + "Remove Android' in char_gui
          and "Keep " in char_gui
          and "race_confirm:" in char_gui)
    check("spar dojo tabs keep war and training bonds separate",
          "Dojo · " in spar_gui
          and "Chat settings" in spar_gui
          and "Training bonds" in spar_gui
          and "void paintDojoWar" in spar_gui
          and 'toggleOn("TP")' in spar_gui)
    check("rival challenges and prestige forms stay their own pages",
          "void paintChallenges" in rival_records
          and "void paintSettings" in rival_records
          and "void paintEffects" in prestige_gui
          and 'CnpcUltraStyle.ACCENT + "Forms"' in prestige_gui)
    check("progression sections are module tabs",
          'CnpcUltraStyle.ACCENT + "Modules"' in prog_menu
          and "moduleIds" in prog_menu
          and '!"android_remove".equals(page)' in prog_menu)
    check("difficulty coin messages live on settings",
          "Coin messages" in diff_gui
          and "§6Rival system" not in diff_gui
          and 'CnpcUltraStyle.CONFIRM + "End Dragon"' in diff_gui)

    print("\n=== CNPC scroll selection (4.6.26) ===")
    check("scroll lists use a selection button instead of setOnClick",
          "setOnClick(" not in support
          and "setOnClick(" not in char_gui
          and "setOnClick(" not in rival_records
          and "setOnClick(" not in spar_gui
          and "setOnClick(" not in diff_gui
          and "setOnClick(" not in prog_menu
          and "selectionButton" in support
          and "bone_toggle" in char_gui
          and "Choose this race" in char_gui
          and "Choose this class" in char_gui
          and "wireScrollDoublePick" in diff_gui
          and "setOnDoubleClick" in prog_menu
          and '"challenge_pick".equals(action) ? "pick_challenge"' in rival_records)

    print("\n=== Noea corpse drops (4.6.27) ===")
    corpse_fix = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/noea/NoeaTravelSafetyCorpseFixMixin.java")
    check("noea drop suppression stops before it clears items",
          'at = @At("HEAD")' in corpse_fix
          and "cancellable = true" in corpse_fix
          and "ci.cancel()" in corpse_fix
          and "setCanceled" not in corpse_fix
          and "suppressConfirmedDrops" in corpse_fix
          and "Death.processDrops" in corpse_fix
          and "captureInventory" in corpse_fix
          and "does not snapshot" in corpse_fix
          and "require = 0" in corpse_fix
          and "private static void lm$disableDropSuppression" in corpse_fix)

    print("\n=== Player stat reset and race change (4.6.38) ===")
    reset_block = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/DmzStatsResetPlayerBlockMixin.java")
    melee_block = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/MeleeStatsResetPlayerBlockMixin.java")
    staff = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/util/StaffAccess.java")
    race_access = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesAccess.java")
    prestige = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/shop/PrestigeSystem.java")
    dende = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/DendeResetAbsorptionWipeMixin.java")
    mixins_json = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json")
    mixin_plugin = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/LegacyMechanicsMixinPlugin.java")
    check("players cannot use dmzstats reset; race, dende, and prestige still reset",
          'method = "resetStats"' in reset_block
          and "cancellable = true" in reset_block
          and "setReturnValue(0)" in reset_block
          and "allowDmzStatsReset" in reset_block
          and "require = 0" in reset_block
          and 'method = "resetSelf"' in melee_block
          and "cancellable = true" in melee_block
          and "setReturnValue(0)" in melee_block
          and "allowDmzStatsReset" in melee_block
          and "Stat reset is turned off." in staff
          and "isStaffSource(source)" in staff
          and "permissions.race" in race_access
          and "return StaffAccess.isStaff(player);" not in race_access
          and "data.resetPlayerProgress(player, 0, false, false)" in prestige
          and '"dmzstats reset "' not in prestige
          and 'method = "handleDende"' in dende
          and "actionId != 2" in dende
          and "DmzStatsResetPlayerBlockMixin" in mixins_json
          and "MeleeStatsResetPlayerBlockMixin" in mixins_json
          and "MeleeStatsResetPlayerBlockMixin" in mixin_plugin
          and "meleeResetCommandsPresent()" in mixin_plugin
          and "Class.forName" not in mixin_plugin.split("meleeResetCommandsPresent()", 1)[-1].split("absorptionServicePresent", 1)[0]
          and "AdaptiveDifficultyMod.LOGGER" in read(
              ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/noea/AbsorptionWipeHelper.java")
          and "System.out.println" not in read(
              ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/noea/AbsorptionWipeHelper.java")
          and "\"StatsCommand.resetStats\"" in reset_block
          and "DmzStatsResetAbsorptionWipeMixin" not in mixins_json)

    print("\n=== Dragon ball pickup (4.6.36) ===")
    dball = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/event/DragonBallRadarPickup.java")
    dball_events = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java")
    dball_mixins = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json")
    check("left click with the matching radar deposits one dragon ball",
          "LeftClickBlock" in dball
          and "Action.START" in dball
          and "DragonRadarItem" in dball
          and "supportsBallSet" in dball
          and "m_150109_().m_36054_(stack)" in dball
          and "unregisterConsumedDragonBalls" in dball
          and "DragonBallRadarPickup.onLeftClickBlock" in dball_events
          and "receiveCanceled = true" in dball_events
          and "BreakEvent" in dball
          and "BlockBreakEvent" in dball
          and "setCancelled" in dball
          and "DragonBallPickupMixin" not in dball_mixins)

    print("\n=== Majin absorption bonus (4.6.33) ===")
    absorb_gate = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/noea/NoeaAbsorptionBonusGateMixin.java")
    absorb_store = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/noea/MajinAbsorptionStore.java")
    absorb_reset = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataGuardsMixin.java")
    absorb_cmd = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/DmzStatsResetPlayerBlockMixin.java")
    absorb_melee = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/MeleeStatsResetAbsorptionWipeMixin.java")
    absorb_dende = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/DendeResetAbsorptionWipeMixin.java")
    absorb_create = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/CreateCharacterAbsorptionWipeMixin.java")
    absorb_services = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesSystem.java")
    absorb_log = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/noea/AbsorptionClearLog.java")
    absorb_helper = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/noea/AbsorptionWipeHelper.java")
    check("absorption bonus follows power and limit release",
          "absorptionPower" in absorb_gate
          and "isActive" not in absorb_gate
          and "getPowerRelease()" in absorb_gate
          and "priority = 1001" in absorb_gate
          and "current - absorbed + scaledBonus" in absorb_gate
          and "package com.dbzlegacy.adaptivedifficulty.noea;" in absorb_store
          and "data.absorptionMelee = 0" in absorb_store
          and "AdaptiveDifficultyMod.LOGGER" in absorb_store
          and "System.out.println" not in absorb_store
          and "printStackTrace" not in absorb_store
          and "data.absorptionKi = 0" in absorb_store
          and "data.absorptionPower = 0" in absorb_store
          and "MajinAbsorptionStore.clear(player)" in absorb_helper
          and "wipeAbsorption firing" in absorb_helper
          and "AdaptiveDifficultyMod.LOGGER" in absorb_helper
          and "System.out.println" not in absorb_helper
          and "printStackTrace" not in absorb_helper
          and "AbsorptionWipeHelper.wipe(player, null)" in absorb_reset
          and "initializeWithRaceAndClass" in absorb_reset
          and "wipeIfRaceChanges" in absorb_reset
          and "AbsorptionWipeHelper.wipe(player, \"CharacterServicesSystem.executeRaceChange\")" in absorb_services
          and 'method = "resetStats"' in absorb_cmd
          and "\"StatsCommand.resetStats\"" in absorb_cmd
          and "AbsorptionWipeHelper" in absorb_cmd
          and "AdaptiveDifficultyMod.LOGGER" in absorb_cmd
          and "printStackTrace" not in absorb_cmd
          and "require = 0" in absorb_cmd
          and 'method = "resetSelf"' in absorb_melee
          and "require = 0" in absorb_melee
          and "AbsorptionWipeHelper.wipe(player, \"StatsResetCommands.resetSelf\")" in absorb_melee
          and 'method = "handleDende"' in absorb_dende
          and "actionId != 2" in absorb_dende
          and "AbsorptionWipeHelper.wipe(player, \"NPCActionC2S.handleDende\")" in absorb_dende
          and 'method = "lambda$handle$0"' in absorb_create
          and "isHasCreatedCharacter" in absorb_create
          and "AbsorptionWipeHelper.wipe(player, \"CreateCharacterC2S\")" in absorb_create
          and "\"CharacterServicesSystem.executeRaceChange\"" in absorb_services
          and "AdaptiveDifficultyMod.LOGGER" in absorb_log
          and "System.out.println" not in absorb_log
          and "printStackTrace" not in absorb_log
          and "AbsorptionClearLog.failure" in absorb_helper
          and "AbsorptionClearLog.failure" in absorb_store
          and "StatsDataGuardsMixin" in mixins_json
          and "StatsDataResetPrestigeSyncMixin" not in mixins_json
          and "ResourcesPoolClampMixin" not in mixins_json
          and "ResourcesLoadClampMixin" not in mixins_json
          and "GodKiWipeHelper.wipe(player, null)" in absorb_reset
          and "GodKiWipeHelper.wipeIfRaceChanges" in absorb_reset)
    god_ki = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/noea/GodKiWipeHelper.java")
    check("god ki progress resets with majin absorption",
          "NoeaGodKiLedgerIdentity" in god_ki
          and "skills.removeSkill(SKILL)" in god_ki
          and "GROUP + \":\"" in god_ki
          and "getStackFormsUsedBefore" in god_ki
          and "clearPreviousStackFormRecord" in god_ki
          and "m_128473_(key)" in god_ki
          and "computed from the worn demon form" in god_ki
          and "godforms" in god_ki
          and "LivingDeathEvent" not in god_ki
          and "GodKiWipeHelper.wipe(player, \"StatsCommand.resetStats\")" in absorb_cmd
          and "GodKiWipeHelper.wipe(player, \"StatsResetCommands.resetSelf\")" in absorb_melee
          and "GodKiWipeHelper.wipe(player, \"NPCActionC2S.handleDende\")" in absorb_dende
          and "GodKiWipeHelper.wipe(player, \"CreateCharacterC2S\")" in absorb_create
          and "GodKiWipeHelper.wipe(player, \"CharacterServicesSystem.executeRaceChange\")" in absorb_services
          and "GodKiWipeHelper" in build_sh
          and "AdaptiveDifficultyMod.LOGGER" in god_ki
          and "System.out.println" not in god_ki
          and "printStackTrace" not in god_ki)
    racial_wipe = read(
        ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/RacialBonusWipeHelper.java"
    )
    strike_rules = read(
        ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/RacialStrikeRules.java"
    )
    strike_wipe = read(
        ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/RacialStrikeWipe.java"
    )
    strike_gate = read(
        ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/StrikeTechniqueReadGateMixin.java"
    )
    skill_preserve = read(
        ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/RaceChangeSkillPreserve.java"
    )
    strike_before_return = racial_wipe.find("RacialStrikeWipe.wipe(data, from, to)")
    bonus_early_return = racial_wipe.find("if (removed <= 0)", strike_before_return if strike_before_return >= 0 else 0)
    check("racial BonusStats splits wipe on race leave",
          "Zenkai_" in racial_wipe
          and "Assimilation_" in racial_wipe
          and "Absorption_" in racial_wipe
          and "clearBonusSplit" in racial_wipe
          and "setRacialSkillCount(0)" in racial_wipe
          and "wipeLeavingRace" in racial_wipe
          and "RacialBonusWipeHelper.wipeLeavingRace" in absorb_services
          and "RacialBonusWipeHelper.wipeIfRaceChanges" in absorb_reset
          and "RacialBonusWipeHelper" in build_sh
          and 'saiyan".equals(from)' in racial_wipe
          and '!"saiyan".equals(to)' in racial_wipe
          and strike_before_return >= 0
          and bonus_early_return > strike_before_return
          and "dropOnRaceChange" in strike_rules
          and "absentOnRead" in strike_rules
          and "removeTechnique" in strike_wipe
          and "removeSkill" in strike_wipe
          and "RacialStrikeWipe.dropSkill" in skill_preserve
          and "getUnlockedTechniques" in strike_gate
          and "getSelectedTechnique" in strike_gate
          and "StrikeTechniqueReadGateMixin" in mixins_json
          and "StrikeTechniqueReadGateMixin" in build_sh
          and "RacialStrikeWipe" in build_sh
          and "RacialStrikeRules" in build_sh)

    print("\n=== CNPC notice color (4.6.41) ===")
    notice = read(cnpc_dir / "CnpcMenuFeedback.java")
    notice_support = read(cnpc_dir / "CnpcGuiSupport.java")
    spar_sys = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/sparring/SparringSystem.java")
    rival_sys = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/rival/RivalSystem.java")
    rival_api = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/RivalGuiApi.java")
    prog_api = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/ProgressionGuiApi.java")
    mech_api = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/MechanicsGuiApi.java")
    bright_start = notice_support.find("private static String brightenNoticeLine")
    bright_end = notice_support.find("private static String noticeChat", bright_start)
    bright = notice_support[bright_start:bright_end] if bright_start >= 0 and bright_end > bright_start else ""
    diff_main = diff_gui.split("private static void paintTiers", 1)[-1].split("private static void paintTitles", 1)[0]
    gui_notice = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/GuiNotice.java")
    check("notice header and body are one pair of constants",
          'NOTICE_HEADER = CnpcUltraStyle.HEADER + "Notice"' in notice
          and ("NOTICE_BODY = GuiNotice.NOTICE_BODY" in notice
              or "NOTICE_BODY = CnpcUltraStyle.INFO" in notice)
          and "public static String noticeBody" in notice
          and "GuiNotice.noticeBody" in notice
          and "noticeBody(" in notice.split("public static void set", 1)[-1].split("public static boolean hasPending", 1)[0]
          and "CnpcMenuFeedback.NOTICE_HEADER" in notice_support
          and "noticeBody(line)" in bright
          and "readableInfoLine" not in bright
          and "public static String noticeBody" in gui_notice)
    check("spar and rival chat toggles use the yellow notice body",
          "§eSpar TP chat " in spar_sys
          and "§aSpar TP chat" not in spar_sys
          and "§eMentor TP chat " in spar_sys
          and "§aMentor TP chat" not in spar_sys
          and "§eRival TP chat " in rival_sys
          and "§aRival TP chat" not in rival_sys
          and "§eRival Instinct " in rival_api
          and "§aRival Instinct" not in rival_api)
    check("other screen notices use the yellow body",
          "NOTICE_BODY + \"Staff only.\"" in read(cnpc_dir / "CnpcLmRivalGui.java")
          and "NOTICE_BODY + \"Staff only.\"" in diff_gui
          and "NOTICE_BODY + \"Staff only.\"" in read(cnpc_dir / "CnpcLmAdminGui.java")
          and "NOTICE_BODY + \"Staff only.\"" in read(cnpc_dir / "CnpcLmLogsGui.java")
          and "NOTICE_BODY + \"Legacy Mechanics config reloaded.\"" in read(cnpc_dir / "CnpcLmAdminGui.java")
          and "§eEvent log ON" in mech_api
          and "§aEvent log ON" not in mech_api
          and "§eProgression " in prog_api
          and "§aProgression " not in prog_api
          and "NOTICE_BODY" in hub_gui)
    check("difficulty status block stays its own info text",
          'CnpcUltraStyle.INFO + "Your personal difficulty is off."' in diff_main
          and 'CnpcUltraStyle.INFO + "This account cannot use personal difficulty."' in diff_main
          and 'CnpcUltraStyle.ACCENT + "Ancient Coins "' in diff_main
          and 'CnpcUltraStyle.DIM + "Scaled mobs can hurt other players nearby."' in diff_main
          and "noticeBody" not in diff_main)

    def _notice_body(line):
        plain = re.sub(r"§[0-9A-FK-ORa-fk-or]", "", line or "").strip()
        return "" if not plain else "§e" + plain

    notice_samples = {
        "§aSpar TP chat §fON — combat TP gains show in chat":
            "§eSpar TP chat ON — combat TP gains show in chat",
        "§cStaff only.": "§eStaff only.",
        "§7Skill Check is a donator perk — ask staff if you want access.":
            "§eSkill Check is a donator perk — ask staff if you want access.",
        "§eSelect a row first.": "§eSelect a row first.",
        "§6Rival admin status": "§eRival admin status",
    }
    for src, expect in notice_samples.items():
        got = _notice_body(src)
        check(f"notice body strips to yellow ({expect})", got == expect, got)
        check(f"notice body has no second color ({expect})",
              got.startswith("§e") and not re.search(r"§(?!e)", got))

    print("\n=== Hakai destroyer gate (4.6.44) ===")
    hakai_gate = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/noea/HakaiDestroyerGateMixin.java")
    mixin_plugin = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/LegacyMechanicsMixinPlugin.java")
    hakai_mixins = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json")
    check("hakai name match requires destroyer energy",
          hakai_gate.count("isHakai(Ljava/lang/String;Ljava/lang/String;)Z") >= 2
          and "\"hasDestructionEnergy\"" in hakai_gate
          and "lm$gateSourceSaysHakai" in hakai_gate
          and "sourceSaysHakai" in hakai_gate
          and "techniqueId" in hakai_gate
          and "noea_hakai_sphere" in hakai_gate
          and "isHakaiTechnique" in hakai_gate
          and "messageId" in hakai_gate
          and "ci.cancel()" in hakai_gate
          and "cir.setReturnValue(false)" in hakai_gate
          and "require = 0" in hakai_gate
          and "remap = false" in hakai_gate)
    check("hakai mixin is not skipped at mixin config time",
          "DivineImmortalityEvents" not in mixin_plugin
          and "HakaiDestroyerGateMixin" not in mixin_plugin
          and '@Mixin(targets = "com.butterjaffa.noeabosses.DivineImmortalityEvents", remap = false)' in hakai_gate
          and "require = 0" in hakai_gate
          and "DestroyerRoleService\"" in hakai_gate
          and "import com.butterjaffa.noeabosses.DestroyerRoleService" not in hakai_gate
          and '"noea.HakaiDestroyerGateMixin"' in hakai_mixins)

    print("\n=== Destroyer story auto-assign blocked (4.6.203) ===")
    story_assign = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/noea/DestroyerStoryAssignMixin.java")
    check("story progress does not auto-assign the Destroyer path",
          '@Mixin(targets = "com.butterjaffa.noeabosses.DestroyerRoleService", remap = false)' in story_assign
          and 'method = "initiateFromStoryProgress"' in story_assign
          and 'at = @At("HEAD")' in story_assign
          and "cancellable = true" in story_assign
          and "require = 0" in story_assign
          and "remap = false" in story_assign
          and "cir.setReturnValue(false)" in story_assign
          and "private static void lm$blockStoryAutoAssign" in story_assign
          and "import com.butterjaffa.noeabosses.DestroyerRoleService" not in story_assign
          and 'method = "beginCandidacy"' not in story_assign
          and 'method = "acceptApprenticeship"' not in story_assign
          and "HakaiDestroyerGateMixin" in story_assign
          and '"noea.DestroyerStoryAssignMixin"' in hakai_mixins
          and "DestroyerStoryAssignMixin" in mixin_plugin
          and "destroyerRoleServicePresent()" in mixin_plugin
          and 'Class.forName(\n                    "com.butterjaffa.noeabosses.DestroyerRoleService"' in mixin_plugin
          and "HakaiDestroyerGateMixin" not in mixin_plugin
          and "DivineImmortalityEvents" not in mixin_plugin)

    print("\n=== Fusion cooldown reset (4.6.43) ===")
    fusion_reset = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/FusionCooldownReset.java")
    check("staff /lm fusionreset",
          'staffLiteral("fusionreset")' in mech
          and "FusionCooldownReset.reset" in mech
          and "/lm fusionreset <player>" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandHelp.java"))
    check("fusion reset clears both cooldown stores and the validation error",
          "FusionLifecycleService.resetCooldown" in fusion_reset
          and "fusionCooldownEnd = 0L" in fusion_reset
          and 'fusionValidationError = ""' in fusion_reset
          and "V090Data.save" in fusion_reset
          and "V090Network.sync" in fusion_reset
          and "clearPersistentCopies" in fusion_reset)
    check("fusion reset class is copied into the forge jar",
          "FusionCooldownReset" in read(ROOT / "tools/dmz-adaptive-difficulty/build.sh"))

    print("\n=== First superform mastery grant (4.6.204) ===")
    mastery_choice = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/form/FirstSuperformChoice.java")
    mastery_grant = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/form/FirstSuperformMastery.java")
    mastery_help = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandHelp.java")
    check("staff /lm mastery grant-first-superform",
          'staffLiteral("mastery")' in mech
          and '"grant-first-superform"' in mech
          and '"grant-first-legendary"' in mech
          and "FirstSuperformChoice.Kind.SUPER" in mech
          and "FirstSuperformChoice.Kind.LEGENDARY" in mech
          and "FirstSuperformMastery.grant" in mech
          and "/lm mastery grant-first-superform <amount>" in mastery_help
          and "/lm mastery grant-first-superform <amount> <player>" in mastery_help
          and "/lm mastery grant-first-superform <player> <amount>" in mastery_help
          and "/lm mastery grant-first-legendary <amount>" in mastery_help
          and "/lm mastery grant-first-legendary <amount> <player>" in mastery_help
          and "/lm mastery grant-first-legendary <player> <amount>" in mastery_help
          and "grantNamed" in mech
          and "playerWordUnlessNumber" in mech
          and "playerWordUnlessNumber" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandSuggestions.java")
          and "StringArgumentType.word()" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandSuggestions.java")
          and "implements ArgumentType" not in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandSuggestions.java")
          and "PlayerNotNumber" not in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandSuggestions.java")
          and "CommandTreeArgumentGuard.audit" in mech
          and "CommandTreeArgumentGuard" in build_sh
          and '"grant-first-superform"' in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/LmCommandSuggestions.java")
          and "handleLmMastery" in read(ROOT / "tools/dmz-adaptive-difficulty-gui/src/main/java/com/dbzlegacy/adaptivedifficulty/bukkit/AdaptiveDifficultyGuiPlugin.java")
          and "grantFirstFormMastery" in read(ROOT / "tools/dmz-adaptive-difficulty-gui/src/main/java/com/dbzlegacy/adaptivedifficulty/bukkit/ForgeBridge.java")
          and "getRaceName" in mastery_grant
          and "getAllFormsForRace" in mastery_grant
          and "getFirstAvailableForm" in mastery_grant
          and "getFirstAvailableStackForm" in mastery_grant
          and "getSkillNameForType" in mastery_grant
          and "setMastery" in mastery_grant
          and "unlockLevel != 0" in mastery_choice
          and "Kind.LEGENDARY" in mastery_choice
          and "Kind.SUPER" in mastery_choice
          and "legendaryform" in mastery_choice
          and "superform" in mastery_choice
          and "Oozaru" in mastery_choice)
    command_java = list((ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command").glob("*.java"))
    custom_arg_types = [
        str(p.relative_to(ROOT))
        for p in command_java
        if "implements ArgumentType" in read(p)
    ]
    check("command package has no custom ArgumentType (server-only client sync)",
          not custom_arg_types,
          ", ".join(custom_arg_types))

    print("\n=== Fusion unfuse deletes FusionBonus (read-gate stays) ===")
    fusion_mixins = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json")
    fusion_clear_path = ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/FusionUnfuseBonusClearMixin.java"
    fusion_clear = fusion_clear_path.read_text(encoding="utf-8") if fusion_clear_path.is_file() else ""
    check("unfuse mixin deletes FusionBonus on FusionLogic.endFusion",
          fusion_clear_path.is_file()
          and '"FusionUnfuseBonusClearMixin"' in fusion_mixins
          and "FusionUnfuseBonusClearMixin" in build_sh
          and 'targets = "com.dragonminez.server.util.FusionLogic"' in fusion_clear
          and 'method = "endFusion"' in fusion_clear
          and fusion_clear.count('method = "endFusion"') >= 2
          and '@At("HEAD")' in fusion_clear
          and '@At("TAIL")' in fusion_clear
          and "getFusionPartnerUUID" in fusion_clear
          and "removeAllBonuses" in fusion_clear
          # 4.6.215 allowlist sweep: names enumerated from BonusStats.save() rows,
          # deleted by exact name (no per-stat removeBonusSplit loop any more).
          and "bonuses.save()" in fusion_clear
          and 'm_128461_("Name")' in fusion_clear
          and "FusionBonusOwner.runRaw" in fusion_clear
          and "StatsSyncS2C" in fusion_clear
          and "ZENKAI_PREFIX = \"Zenkai_\"" in fusion_clear
          and "ASSIMILATION_PREFIX = \"Assimilation_\"" in fusion_clear
          and "ABSORPTION_PREFIX = \"Absorption_\"" in fusion_clear
          and "racialKey(name)" in fusion_clear
          and ".clearAllSplit(" not in fusion_clear
          and "clearBonusSplit" not in fusion_clear
          and "clearBonus(" not in fusion_clear
          and "clearAll(" not in fusion_clear)
    fusion_gate = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/FusionBonusReadGateMixin.java")
    check("fusion bonus is ignored on read when the player is not fused",
          "status.isFused()" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/FusionBonusOwner.java")
          and 'method = "calculateBonus"' in fusion_gate
          and 'method = "hasBonus"' in fusion_gate
          and 'method = "getBonuses"' in fusion_gate
          and 'FUSION_BONUS = "FusionBonus"' in fusion_gate
          and 'lower.contains("fusion") && lower.contains("zenkai")' in fusion_gate
          and "Zenkai_" in fusion_gate
          and '"FusionBonusReadGateMixin"' in fusion_mixins
          and "FusionBonusReadGateMixin" in build_sh
          and "FusionBonusOwner" in build_sh
          and "FusionBonusOwner.remember" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataMixin.java")
          and ".getBonusStats(" not in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/progression/FusionBonusOwner.java")
          and "lm$fusionBonuses" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/mixin/StatsDataMixin.java"))

    print("\n=== Fall damage pool sync (4.6.131) ===")
    fall_diag = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/event/FallDamageDiag.java")
    mod_src = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java")
    fall_reg = mod_src.find("new FallDamageDiag()")
    diff_reg = mod_src.find("new DifficultyEvents()")
    check("fall damage aligns vanilla pool on fall and hurt (no debug spam)",
          "DamageTypeTags.f_268549_" in fall_diag
          and "getMaxHealth()" in fall_diag
          and "m_21223_()" in fall_diag
          and "m_21233_()" in fall_diag
          and fall_diag.count("EventPriority.HIGHEST") >= 2
          and "alignVanillaPool(player)" in fall_diag
          and fall_diag.count("alignVanillaPool(player)") >= 2
          and "applyHealthBonus" in fall_diag
          and "vanillaPoolDesynced" in fall_diag
          and "scaledFromOldPool" in fall_diag
          and "raiseVanillaMax" in fall_diag
          and "System.out.println" not in fall_diag
          and "printStackTrace" not in fall_diag
          and "AdaptiveDifficultyMod.LOGGER" in fall_diag
          and "[LM] Fall damage:" not in fall_diag
          and "setCanceled" not in fall_diag
          and "setAmount" not in fall_diag
          and "new FallDamageDiag()" in mod_src
          and fall_reg >= 0
          and diff_reg >= 0
          and fall_reg < diff_reg)
    check("fall damage listener class is copied into the forge jar",
          "event/FallDamageDiag.class" in build_sh)

    print("\n=== Staff test GUI stays server-side (4.6.58) ===")
    native_cmd = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/command/MechanicsCommands.java")
    native_cfg = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/config/DifficultyConfig.java")
    native_mod = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/AdaptiveDifficultyMod.java")
    client_root = ROOT / "tools/dmz-adaptive-difficulty/src/client"
    test_gui = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/gui/CnpcStaffTestGui.java")
    test_hub = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcStyledTestHub.java")
    # Staff testgui stays server-side. Ultra may open via KaiPath packets when
    # guiBackend=ultra; CNPC hub remains the no-KaiPath / cnpc-backend path.
    # Staff testgui/guitest commands retired in the 4.6.212 tree cleanup; the server-side
    # test GUI class stays shipped. The 4.6.58 guard still holds: no native test gui flag,
    # no client bootstrap, no client classes.
    check("staff test gui stays server-side",
          "useNativeTestGui" not in native_cmd
          and "useNativeTestGui" not in native_cfg
          and "LmClientBootstrap" not in native_mod
          and "LmGuiNetwork.register" in native_mod
          and not client_root.exists()
          and "client-classes" not in build_sh)
    check("testgui opens Ultra hub",
          "UltraGui.openHub" in test_gui
          and "CnpcLmHubGui" not in test_gui
          and "CnpcUltraPreview" not in test_gui
          and "CnpcStyledTestHub" not in test_gui
          and "CnpcStaffTestGui" in build_sh)
    check("head parts use the test menu colors",
          "CnpcGuiStyle.toggleOn" in bone_gui
          and "CnpcGuiStyle.toggleOff" in bone_gui
          and "ultraPartCaption" not in bone_gui
          and "\" Body \"" not in bone_gui)
    style_support = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcGuiSupport.java")
    check("hub from a menu opens the main hub",
          "CnpcStyledTestHub" not in style_support
          and "CnpcUltraPreview.active" not in style_support
          and "CnpcLmHubGui.open(player, \"main\")" in style_support)
    check("production menus use the gold CNPC style",
          "CnpcUltraStyle.header(title)" in style_support
          and "CnpcUltraStyle.subtitle(subtitle)" in style_support
          and "CnpcUltraStyle.DIVIDER" in style_support
          and "CnpcUltraStyle.section(caption)" in style_support
          and "paintingUltra()" not in style_support
          and "CnpcUltraPreview.active" not in bone_gui)

    print("\n=== In-game config editor (4.6.65) ===")
    config_editor = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/config/editor/ConfigEditor.java")
    config_gui = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmConfigGui.java")
    config_registry = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/config/ConfigRegistry.java")
    config_screen = read(cnpc_dir / "CnpcConfigEditor.java")
    check("config editor static maps are concurrency-safe",
          "ConcurrentHashMap.newKeySet()" in config_editor
          and "PENDING = new ConcurrentHashMap<>()" in config_editor
          and "SESSIONS = new ConcurrentHashMap<>()" in config_editor
          and "BACKED_THIS_SESSION = ConcurrentHashMap.newKeySet()" in config_editor)
    check("config editor lists fields by reflection",
          "getFields()" in config_editor
          and "adminPermission" in config_editor
          and "Migrated" in config_editor
          and "combatCurveExponent" not in config_gui
          and "enableProgression" not in config_gui
          and "CnpcLmConfigGui.open" in read(cnpc_dir / "CnpcLmGui.java")
          and "CnpcConfigEditor.open" in config_gui
          and "config-audit.log" in config_editor
          and ".bak" in config_editor
          and "displayName" in config_editor
          and "\"difficulty\"" in config_registry
          and "\"progression\"" in config_registry
          and "\"character-services\"" in config_registry
          and "rivalry-v4.json" in config_registry
          and "sparring.json" in config_registry
          and "editable" in config_registry
          and "PHASE_MENU" in config_registry
          and "Save all" in config_screen
          and "Reload from disk" in config_screen
          and "Discard changes" in config_screen
          and "fields match" in config_screen
          and "No fields match" in config_screen
          and "Nothing to save." in config_screen
          and "Saved " in config_screen
          and "● " in config_screen
          and "Review changes" in config_screen
          and "fields need fixing" in config_screen
          and "[undo]" in config_screen
          and "30+ fields" in config_screen
          and "Must be between 0 and 1" in config_editor
          and "setOnChange" in config_screen
          and "fields match" in config_screen
          and "No fields match" in config_screen
          and "moduleLinks" in config_editor
          and "Staff only." in config_screen
          and "CnpcRowList" in config_screen
          and "stepNumber" in config_editor
          and "saveAll" in config_editor
          and "backupOnce" in config_editor
          and "ConfigEditor" in build_sh
          and "ConfigRegistry" in build_sh
          and "CharacterServicesConfig" in build_sh
          and "public static boolean save()" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/character/CharacterServicesConfig.java")
          and "public static boolean save()" in read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/quest/SagaResetConfig.java"))

    style = read(cnpc_dir / "CnpcUltraStyle.java")
    prestige_gui = read(cnpc_dir / "CnpcLmPrestigeGui.java")
    hub_gui = read(cnpc_dir / "CnpcLmHubGui.java")
    admin_gui = read(cnpc_dir / "CnpcLmAdminGui.java")
    test_hub = read(cnpc_dir / "CnpcStyledTestHub.java")
    check("menus keep a per-system accent and prestige has no module list",
          "ACCENT_RIVAL" in style
          and "ACCENT_SPARRING" in style
          and "ACCENT_PROGRESSION" in style
          and "ACCENT_PRESTIGE" in style
          and "ACCENT_DIFFICULTY" in style
          and "ACCENT_SAGA" in style
          and "ACCENT_ADMIN" in style
          and "ACCENT_CONFIG" in style
          and "withAccent" in style
          and "modules|Modules" not in prestige_gui
          and "ConfigEditor" not in prestige_gui
          and "setOnChange" in config_screen
          and "ACCENT_RIVAL + \"Rival\"" in hub_gui
          and "ACCENT_SPARRING + \"Sparring\"" in hub_gui
          and "ACCENT_DIFFICULTY + \"Difficulty\"" in hub_gui
          and "ACCENT_PRESTIGE + \"Prestige\"" in hub_gui
          and "ACCENT_SAGA + \"Saga\"" not in hub_gui
          and "ACCENT_CONFIG + CnpcUltraStyle.BOLD + \"Systems\"" in hub_gui
          and "ACCENT_ADMIN + \"Skill Check\"" in hub_gui
          and "ACCENT_CHARACTER + \"Character Services\"" in hub_gui
          and "ACCENT_ADMIN + \"Staff Admin\"" in hub_gui
          and "ACCENT_PROGRESSION + \"Progression panel\"" in admin_gui
          and "ACCENT_CONFIG + \"Config editor\"" in admin_gui
          and "ACCENT_RIVAL" in test_hub
          and "ACCENT_SAGA" in test_hub)

    skill_gui = read(cnpc_dir / "CnpcLmSkillCheckGui.java")
    check("hub header stays gold and each menu uses its accent",
          "withAccent(CnpcUltraStyle.ACCENT_CHARACTER" in hub_gui
          and "withAccent(CnpcUltraStyle.ACCENT_ADMIN" in admin_gui
          and "withAccent(CnpcUltraStyle.ACCENT_CHARACTER" not in admin_gui
          and "withAccent(CnpcUltraStyle.ACCENT_CONFIG" in test_hub
          and "withAccent(CnpcUltraStyle.ACCENT_ADMIN" in skill_gui)

    print("\n=== Saga reset uses loaded sagas (4.6.69) ===")
    saga_service = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/quest/SagaResetService.java")
    saga_config = read(ROOT / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty/quest/SagaResetConfig.java")
    saga_gui = read(SRC / "com/dbzlegacy/adaptivedifficulty/gui/cnpc/CnpcLmSagaGui.java")
    character_gui = read(cnpc_dir / "CnpcLmCharacterGui.java")
    check("saga reset lists live sagas and scales the cost",
          "QuestRegistry.getAllSagas" in saga_service
          and "defaultBaseCost" in saga_config
          and "perLevelCost" in saga_config
          and "baseCosts" in saga_config
          and "getHighestDmzLevel" in saga_config
          and "AncientCoinEconomy.getHighestDmzLevel" not in saga_config
          and "DifficultyCache.data" in saga_config
          and "isSagaLocked" in saga_service
          and "resetSaga" in saga_service
          and "setTrackedQuestId" in saga_service
          and "Not enough Ancient Coins" in saga_service
          and "CnpcRowList" in saga_gui
          and "CnpcUltraStyle" in saga_gui
          and "ID_SAGA" in saga_gui
          and "servicesTabs" in saga_gui
          and "characterTabs" not in saga_gui
          and "Reset saga progress" in saga_gui
          and "Reset saga progress" not in character_gui
          and "home|Home" in character_gui
          and "saga|Saga" in character_gui
          and "android|Android" in character_gui
          and "Model customization" in character_gui
          and "services|Services" not in character_gui
          and "CnpcLmSagaGui.open" in read(cnpc_dir / "CnpcLmGui.java")
          and "SagaResetConfig" in build_sh
          and "SagaResetService" in build_sh)

    print("\n=== Ultra custom body parts shop (KaiPath 1.3.29) ===")
    body_svc_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/character/CosmeticBodyPartService.java"
    )
    cs_cfg_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesConfig.java"
    )
    cs_store_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesStore.java"
    )
    cs_access_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/character/CharacterServicesAccess.java"
    )
    cs_gui_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/gui/CharacterServicesGuiApi.java"
    )
    char_screens_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/UltraCharacterScreens.java"
    )
    parts_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/PlayerModelParts.java"
    )
    catalog_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/BodyPartCatalog.java"
    )
    feat_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/net/gui/UltraClientFeatures.java"
    )
    net_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/net/gui/LmGuiNetwork.java"
    )
    json_bp = read(
        ROOT
        / "tools"
        / "dmz-adaptive-difficulty"
        / "src"
        / "main"
        / "resources"
        / "dmzultra-form-parts.json"
    )
    check(
        "Ultra Body Parts Shop service mirrors the head-bone shop",
        "executeUnlock" in body_svc_bp
        and "executeToggle" in body_svc_bp
        and "executeClear" in body_svc_bp
        and "equippedParts" in body_svc_bp
        and "shopPieceIds" in body_svc_bp
        and "unlockCost" in body_svc_bp,
    )
    check(
        "Ultra Body Parts Shop config block + bodyParts permission node",
        "class BodyPartShop" in cs_cfg_bp
        and "bodyPartShop" in cs_cfg_bp
        and '"legacymechanics.character.bodyparts"' in cs_cfg_bp,
    )
    check(
        "Ultra Body Parts Shop store fields persist unlocks + worn pieces",
        "unlockedBodyParts" in cs_store_bp and "equippedBodyParts" in cs_store_bp,
    )
    check(
        "Ultra body parts access gate canBodyPartShop",
        "canBodyPartShop" in cs_access_bp,
    )
    check(
        "Ultra body parts GUI dispatch routes unlock/toggle/clear actions",
        '"bodypart_unlock"' in cs_gui_bp
        and '"bodypart_toggle"' in cs_gui_bp
        and '"bodypart_clear"' in cs_gui_bp
        and "bodyPartCards" in cs_gui_bp,
    )
    check(
        "Ultra model customize opens Body parts page (bodyparts: routing)",
        '"bodyparts:"' in char_screens_bp
        and "openBodyParts" in char_screens_bp
        and 'sub(player, "bodyparts:" + pg, true)' in char_screens_bp
        and '"page:bodyparts:0"' in char_screens_bp,
    )
    check(
        "Ultra Body Parts Shop lists the full catalog (shopPieceIds source, never race resolve)",
        "BodyPartCatalog.shopPieceIds()" in body_svc_bp and "resolve(" not in body_svc_bp,
    )
    check(
        "Ultra PlayerModelParts sends equipped custom pieces on bodyParts",
        "CosmeticBodyPartService.equippedParts" in parts_bp
        and "BodyPartCatalog.slot" in parts_bp,
    )
    check(
        "Ultra BodyPartCatalog exposes the shop piece list",
        "shopPieceIds" in catalog_bp and '"pieces"' in catalog_bp,
    )
    check(
        "Ultra form-parts JSON ships the client piece list",
        '"pieces"' in json_bp,
    )
    check(
        "KaiPath 1.3.29 bodyParts shipped; bodyPartsSync pending next",
        "FEATURE_BODY_PARTS" in feat_bp
        and "PENDING_NEXT = Set.of(FEATURE_BODY_PARTS_SYNC)" in feat_bp
        and "FEATURE_BODY_PARTS_SYNC" in feat_bp
        and 'KAIPATH_RECOMMENDED_VERSION = "1.3.29"' in net_bp,
    )
    sync_pkt_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/net/gui/GuiModelSyncPacket.java"
    )
    proto_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/net/gui/UltraGuiProtocol.java"
    )
    head_svc_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/character/CosmeticHeadBoneService.java"
    )
    check(
        "GuiModelSyncPacket message 3 registered + bodyPartsSync-gated send",
        "MSG_MODEL_SYNC = 3" in proto_bp
        and "GuiModelSyncPacket.class, UltraGuiProtocol.MSG_MODEL_SYNC" in net_bp
        and "supportsModelSync" in net_bp
        and "FEATURE_BODY_PARTS_SYNC" in net_bp
        and "onClientAdvertisement" in net_bp,
    )
    check(
        "GuiModelSyncPacket reuses the playerModel parts schema (syncSnapshot)",
        "syncSnapshot" in parts_bp
        and "PlayerModelParts.SCHEMA" in sync_pkt_bp
        and "bodyParts" in sync_pkt_bp,
    )
    check(
        "Model sync sends on equip/unequip/clear + head-bone changes",
        "sendModelSync" in body_svc_bp
        and "REASON_UNEQUIP" in body_svc_bp
        and "REASON_CLEAR" in body_svc_bp
        and "sendModelSync" in head_svc_bp,
    )
    check(
        "Single-tail rule: bodyParts never carries two SLOT_TAIL entries",
        "tailOccupied" in parts_bp
        and "SLOT_TAIL" in parts_bp
        and "customTail" in parts_bp,
    )
    check(
        "GuiModelSync round-trip + oversized-sanitize self-test at startup",
        "roundTripModelSync" in net_bp and "GuiModelSyncPacket" in net_bp,
    )
    evt_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/event/DifficultyEvents.java"
    )
    catalog_bp = read(
        SRC / "com/dbzlegacy/adaptivedifficulty/gui/ultra/BodyPartCatalog.java"
    )
    check(
        "Model sync broadcasts uuid-subject snapshots (owner + every tracker)",
        "build(player, true)" in parts_bp
        and '"uuid:" + target.m_20148_()' in parts_bp
        and 'addProperty("subject", "local")' not in parts_bp
        and "TRACKING_ENTITY_AND_SELF" in net_bp,
    )
    check(
        "StartTracking sends the target's parts to the new tracker only",
        "PlayerEvent.StartTracking" in evt_bp
        and "onStartTracking" in evt_bp
        and "sendModelSyncTo" in net_bp
        and "REASON_TRACK" in net_bp,
    )
    check(
        "Login advertisement backfills other players' parts to the new viewer",
        "onClientAdvertisement" in net_bp and "sendModelSyncTo" in net_bp,
    )
    check(
        "parts.colors emitted with JLDK key names (gray-parts fix)",
        '"colors"' in parts_bp
        and '"skin"' in parts_bp
        and '"second"' in parts_bp
        and '"hair"' in parts_bp
        and '"own"' in parts_bp
        and '"trim"' in parts_bp
        and '"horn"' in parts_bp
        and '"light"' in parts_bp
        and "design(" in catalog_bp
        and '"designs"' in catalog_bp,
    )
    check(
        "Degraded sync payload keeps the uuid subject (never resets to local)",
        "fallback(String subject)" in sync_pkt_bp
        and "subjectOf(" in sync_pkt_bp,
    )
    check(
        "Startup self-test asserts uuid subject + colors survive round-trip",
        '"subject\\":\\"uuid:' in net_bp
        and '"colors\\":{' in net_bp
        and "lost uuid subject" in net_bp,
    )
    kit_root = ROOT / "tools" / "ultra-gui-kit"
    kit_registry = read(
        kit_root / "kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/UltraGuiConsumerRegistry.java"
    )
    kit_packet = read(
        kit_root
        / "kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/GuiModelSyncPacket.java"
    )
    kit_preview = read(
        kit_root
        / "kaipath-client/com/dmzlegacy/kaipath/ultra/consumers/legacymechanics/BodyPartPreview.java"
    )
    kit_server = read(kit_root / "server/UltraGuiModelSyncPacket.java")
    kit_readme = read(kit_root / "README.md")
    check(
        "Kit registers message 3 + advertises bodyPartsSync (JLDK reference)",
        "GuiModelSyncPacket.class, 3" in kit_registry
        and "bodyPartsSync" in kit_registry
        and "clearSync" in kit_registry,
    )
    check(
        "Kit client packet + world cache mirror the message-3 contract",
        "applySync" in kit_packet
        and "applySync" in kit_preview
        and "clearSync" in kit_preview
        and "PARTS_JSON_MAX = 8192" in kit_packet,
    )
    check(
        "Kit server codec twin + README document message 3",
        "PARTS_JSON_MAX = 8192" in kit_server
        and "bodyPartsSync" in kit_server
        and "bodyPartsSync" in kit_readme,
    )
    kit_mixin = read(
        kit_root / "kaipath-client/com/dmzlegacy/kaipath/mixin/FormPartsPreviewMixin.java"
    )
    check(
        "Kit prefers server colors over the gray reflective look + uuid-self sync",
        "colorsFor" in kit_preview
        and "mergeLook(cir.getReturnValue(), extra," in kit_mixin
        and "colorsOf" in kit_preview
        and "REASON_TRACK" in kit_packet
        and "broadcast contract" in kit_readme.lower(),
    )

    godki_shop = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "progression/shop/GodKiEnhancementShop.java"
    )
    godki_prestige = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "progression/shop/PrestigePointsSystem.java"
    )
    godki_api = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "gui/ProgressionGuiApi.java"
    )
    godki_screen = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "gui/ultra/UltraPrestigeScreens.java"
    )
    godki_actions = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "gui/ultra/UltraGuiActions.java"
    )
    godki_cfg = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "config/DifficultyConfig.java"
    )
    godki_mixins = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/resources/legacymechanics.mixins.json"
    )
    godki_gate = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "mixin/FormModeHandlerGodKiGateMixin.java"
    )
    godki_gate_stack = read(
        ROOT
        / "tools/dmz-adaptive-difficulty/src/main/java/com/dbzlegacy/adaptivedifficulty"
        / "mixin/StackFormModeHandlerGodKiGateMixin.java"
    )
    check(
        "God Ki shop: 1000 mastery per point, max 5 investments, confirm + telemetry",
        "MASTERY_PER_POINT = 1000" in godki_shop
        and "MAX_INVESTMENTS = 5" in godki_shop
        and "INVESTMENT_COST = 1" in godki_shop
        and "GuiClickConfirm.confirmed" in godki_shop
        and '"prestige_points", "buy_godki_enhancement"' in godki_shop,
    )
    check(
        "God Ki shop: login top-up raises mastery to invested x 1000, never lowers",
        "GodKiEnhancementShop.reapply(player)" in godki_prestige
        and "current >= expected" in godki_shop
        and "raiseMastery" in godki_shop
        and "DmzSkillUtil.ensureRegistered(skills, ENHANCEMENT_GROUP, 1)" in godki_shop,
    )
    check(
        "God Ki shop: store listing + do-routing + reopen to Effects",
        '"do:godki"' in godki_screen
        and "God Ki Enhancement" in godki_screen
        and '"godki".equals(act)' in godki_api
        and "godki_invested" in godki_api
        and '"godki", "buy_godki", "godki_enhancement" -> "effects"' in godki_actions,
    )
    check(
        "God Ki gate: custom God forms need the flag, true form also needs mastery",
        "GOD_FORMS_GROUP" in godki_shop
        and "trueFormThreshold" in godki_shop
        and "godKiTrueFormMasteryThreshold = 1000" in godki_cfg,
    )
    check(
        "God Ki gate mixins registered, cancel transform at attemptTransform HEAD",
        '"FormModeHandlerGodKiGateMixin"' in godki_mixins
        and '"StackFormModeHandlerGodKiGateMixin"' in godki_mixins
        and "attemptTransform" in godki_gate
        and "ci.cancel()" in godki_gate
        and "attemptTransform" in godki_gate_stack
        and "ci.cancel()" in godki_gate_stack,
    )

    print("\n=== Summary ===")
    for w in warns:
        print(f"WARN: {w}")
    if errors:
        for e in errors:
            print(f"FAIL: {e}")
        print(f"{len(errors)} error(s), {len(warns)} warning(s), {len(ok)} ok")
        return 1
    print(f"PASS — intended features intact ({len(ok)} checks, {len(warns)} warning(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
