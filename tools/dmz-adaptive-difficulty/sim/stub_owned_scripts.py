#!/usr/bin/env python3
"""Stub CNPC scripts that LegacyMechanics Java already owns (prevent double-apply).

Full copies stay under uploads/scripts/ (or uploads/script-backups-full/).
customnpcs/scripts/ gets thin stubs that either no-op or forward triggers to Java.
"""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path("/workspace")
CNPC = ROOT / "customnpcs" / "scripts"
UPLOADS = ROOT / "uploads" / "scripts"
BACKUP = ROOT / "uploads" / "script-backups-full"
BACKUP.mkdir(parents=True, exist_ok=True)

# Scripts that still have full logic in customnpcs but Java owns them.
# Value: (java_owner, forge_command_hint, events_to_noop)
OWNED = {
    "Rival System.js": (
        "rival.RivalSystem",
        "/lm rival · /lm → Rival",
        ["init", "login", "tick", "damaged", "damagedEntity", "kill", "died", "logout", "trigger"],
    ),
    "Rival Command Handler.js": (
        "command.RivalCommands",
        "/lm rival",
        ["init", "trigger", "command"],
    ),
    "Sparring Tp System.js": (
        "sparring.SparringSystem",
        "/lm spar · /lm → Spar",
        ["init", "login", "tick", "damaged", "damagedEntity", "kill", "died", "logout", "trigger"],
    ),
    "Sparring Command Handler.js": (
        "command.SparCommands",
        "/lm spar",
        ["init", "trigger", "command"],
    ),
    "Prestige NPC.js": (
        "progression.shop.PrestigeSystem",
        "/lm prestige · /lm → Prestige",
        ["interact", "init"],
    ),
    "SkillCheckCommand.js": (
        "progression.shop.SkillCheckService",
        "/lm skillcheck · LM-SkillCheck-NPC.js",
        ["trigger", "init", "command"],
    ),
    "SkillUnlockNPC.js": (
        "progression.shop.SkillUnlockService",
        "/lm admin skills (staff) · /lm skillcheck",
        ["interact", "init", "trigger"],
    ),
    "Farming TP Skill.js": (
        "(removed — farming TP is not in LegacyMechanics)",
        "(do not enable; another mod owns crop TP)",
        ["broken", "init"],
    ),
    "PlayerStatChecker.js": (
        "progression.PlayerStatChecker",
        "sneak + right-click player",
        ["interact", "init"],
    ),
    "Flight.js": (
        "(removed — flight is not in LegacyMechanics)",
        "(do not enable; another mod owns flight)",
        ["tick", "login", "init", "damaged", "killed"],
    ),
    "flight suppression.js": (
        "(removed — flight is not in LegacyMechanics)",
        "(do not enable; another mod owns flight)",
        ["tick", "init"],
    ),
    "Fly.js": (
        "(removed — flight is not in LegacyMechanics)",
        "(do not enable; another mod owns flight)",
        ["tick", "login", "init"],
    ),
    "ViltrumiteFly.js": (
        "(removed — flight is not in LegacyMechanics)",
        "(do not enable; another mod owns flight)",
        ["tick", "login", "init"],
    ),
    "SprintJump.js": (
        "(removed — sprint and jump are not in LegacyMechanics)",
        "(do not enable; another mod owns sprint and jump)",
        ["tick", "login", "init"],
    ),
    "JumpSprint.js": (
        "(removed — sprint and jump are not in LegacyMechanics)",
        "(do not enable; another mod owns sprint and jump)",
        ["tick", "login", "init"],
    ),
    "Jump.js": (
        "(removed — sprint and jump are not in LegacyMechanics)",
        "(do not enable; another mod owns sprint and jump)",
        ["tick", "login", "init"],
    ),
    "Sprint.js": (
        "(removed — sprint and jump are not in LegacyMechanics)",
        "(do not enable; another mod owns sprint and jump)",
        ["tick", "login", "init"],
    ),
    "KiWeapons.js": (
        "progression.combat.KiWeapons",
        "(automatic)",
        ["damagedEntity", "init", "tick"],
    ),
    "Piercing.js": (
        "progression.combat.PiercingBonus",
        "(automatic)",
        ["damagedEntity", "init"],
    ),
    "damageovertime.js": (
        "progression.combat.DotExtraDamage",
        "(automatic)",
        ["damaged", "tick", "init"],
    ),
    "Apothicfireandcolddamage.js": (
        "progression.combat.ApothicElemental",
        "(automatic)",
        ["damagedEntity", "init"],
    ),
    "BioAndroid.js": (
        "progression.tp.BioAndroidAbsorb",
        "(automatic DrainActive)",
        ["tick", "kill", "init"],
    ),
    "DMZ RACE LOCK.js": (
        "progression.race.RaceLock",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "Yardrat.js": (
        "progression.race.YardratProgression",
        "(automatic)",
        ["tick", "login", "init", "kill"],
    ),
    "YardratRace.js": (
        "progression.race.YardratProgression",
        "(automatic)",
        ["tick", "login", "init", "kill"],
    ),
    "YardratSkills.js": (
        "progression.race.YardratProgression",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "Spirtualist Ki Control.js": (
        "progression.race.SpiritualistKiControl",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "ShadowDummyLimiter.js": (
        "progression.dummy.ShadowDummyLimiter",
        "(automatic)",
        ["tick", "init", "spawn", "timer"],
    ),
    "ShadowDummyForgeProtect.js": (
        "progression.dummy.ShadowDummyLimiter",
        "(automatic)",
        ["init", "tick"],
    ),
    "DMZ Fabled Bridge.js": (
        "progression.bridge.FabledBridge",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "DMZ Energy.js": (
        "progression.bridge.EnergyManaSync",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "DMZ Stat Screen.js": (
        "progression.bridge.StatScreenSync",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "DMZ Class Permission.js": (
        "progression.bridge.ClassPermissionSync",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "Fabled Sync.js": (
        "progression.bridge (PrestigeSkill/Faction/ValueCleaner)",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "Attr Fabled Multi bonus.js": (
        "progression.bridge.AttrMultiBonus",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "Attr Fabled bonus stats.js": (
        "(disabled duplicate — use Multi bonus / AttrMultiBonus)",
        "(do not enable)",
        ["tick", "init"],
    ),
    "Races.js": (
        "progression.bridge.RaceClassSync",
        "(automatic)",
        ["tick", "login", "init"],
    ),
    "TP IS SP Fabled.js": (
        "progression.bridge.TpSpMirror",
        "(automatic)",
        ["tick", "login", "init"],
    ),
}


def is_full_script(path: Path) -> bool:
    if not path.is_file():
        return False
    text = path.read_text(encoding="utf-8", errors="replace")
    if "DISABLED STUB" in text or "owned by LegacyMechanics" in text[:800]:
        # Heuristic: stubs are small
        return path.stat().st_size > 4000 and "function trigger" in text and "Java.type" in text and "STUB" not in text[:400]
    return path.stat().st_size > 2500


def stub_text(name: str, owner: str, cmd: str, events: list[str]) -> str:
    handlers = "\n".join(f"function {ev}(e) {{ /* owned by LegacyMechanics */ }}" for ev in events)
    return f"""/*
============================================================
 {name} — DISABLED STUB
============================================================
 LegacyMechanics (Forge) owns this system:
   Java: com.dbzlegacy.adaptivedifficulty.{owner}
   Use: {cmd}

 Full script backup: uploads/scripts/{name}
   and/or uploads/script-backups-full/ / live-scripts-2026-08-27/

 Do NOT re-enable while the matching enable* flag is ON in the
 Forge mod — double TP / double handlers / double sync will occur.
============================================================
*/

{handlers}
"""


def ensure_backup(name: str, src: Path) -> None:
    dest_uploads = UPLOADS / name
    dest_backup = BACKUP / name
    if is_full_script(src):
        # Prefer keeping a full copy in uploads if uploads is stub/missing
        if not dest_uploads.exists() or not is_full_script(dest_uploads):
            if dest_uploads.exists() and dest_uploads.stat().st_size < 4000:
                # Move stub aside only if we have fuller content
                pass
            # Always keep a dedicated full backup
            shutil.copy2(src, dest_backup)
            # If uploads is stub-sized, also write full beside it as .full.js? Keep backup folder.
            print(f"  backup → script-backups-full/{name} ({src.stat().st_size} bytes)")
        else:
            shutil.copy2(dest_uploads, dest_backup)
            print(f"  backup uploads → script-backups-full/{name}")
    elif is_full_script(dest_uploads):
        shutil.copy2(dest_uploads, dest_backup)
        print(f"  backup uploads → script-backups-full/{name}")


def main() -> None:
    stubbed = 0
    skipped = 0
    for name, (owner, cmd, events) in OWNED.items():
        src = CNPC / name
        if not src.exists():
            print(f"SKIP missing {name}")
            skipped += 1
            continue
        text = src.read_text(encoding="utf-8", errors="replace")
        already = "DISABLED STUB" in text[:600] or (
            "owned by LegacyMechanics" in text[:800] and src.stat().st_size < 4000
        )
        if already:
            print(f"OK already stub {name}")
            skipped += 1
            continue
        ensure_backup(name, src)
        src.write_text(stub_text(name, owner, cmd, events), encoding="utf-8")
        print(f"STUBBED {name}")
        stubbed += 1
    print(f"\nDone: stubbed={stubbed} skipped={skipped}")


if __name__ == "__main__":
    main()
