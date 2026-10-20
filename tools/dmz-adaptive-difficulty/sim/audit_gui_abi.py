#!/usr/bin/env python3
"""Audit LegacyMechanics Forge jar ABI required by the Bukkit GUI sources.

The LegacyMechanicsGUI jar is not shipped. Version and plugin entrypoints
are checked against source. Fail-closed: any missing required class, method,
or field exits non-zero. Optional symbols (gracefully null'd by ForgeBridge)
are reported as WARN.
"""
from __future__ import annotations

import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GUI_YML = ROOT / "tools" / "dmz-adaptive-difficulty-gui" / "src" / "main" / "resources" / "plugin.yml"
GUI_PLUGIN_JAVA = (
    ROOT
    / "tools"
    / "dmz-adaptive-difficulty-gui"
    / "src"
    / "main"
    / "java"
    / "com"
    / "dbzlegacy"
    / "adaptivedifficulty"
    / "bukkit"
    / "AdaptiveDifficultyGuiPlugin.java"
)


def latest_jar(directory: Path, prefix: str) -> Path | None:
    matches = sorted(directory.glob(f"{prefix}-*.jar"), key=lambda p: p.stat().st_mtime)
    return matches[-1] if matches else None


FORGE_JAR = latest_jar(ROOT / "mods", "LegacyMechanics")
if FORGE_JAR is None:
    FORGE_JAR = latest_jar(ROOT / "mods", "AdaptiveDifficulty")

# Required: ensureResolved() hard-fails without these.
REQUIRED_CLASSES = [
    "com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod",
    "com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache",
    "com.dbzlegacy.adaptivedifficulty.service.DifficultyActions",
    "com.dbzlegacy.adaptivedifficulty.service.DifficultyActions$Result",
    "com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot",
    "com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig",
    "com.dbzlegacy.adaptivedifficulty.util.SystemGate",
]

# Optional but important for full GUI (ForgeBridge catches missing).
OPTIONAL_CLASSES = [
    "com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy",
    "com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy$CoinKind",
    "com.dbzlegacy.adaptivedifficulty.tier.UnlockTier",
    "com.dbzlegacy.adaptivedifficulty.title.TitleSystem",
    "com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle",
    "com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile",
    "com.dbzlegacy.adaptivedifficulty.gui.DifficultyChatMenu",
    "com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi",
    "com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi",
    "com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData",
    "com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem",
    "com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty",
    "com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler",
    "com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker",
    "com.dbzlegacy.adaptivedifficulty.world.VanillaDifficultyGuard",
]

# method name -> list of descriptor fragments that must appear in javap -public -s
REQUIRED_METHODS = {
    "com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache": [
        ("get", "ServerPlayer"),
        ("refresh", "ServerPlayer"),
        ("data", "ServerPlayer"),
        ("invalidateAll", ""),
        ("save", "ServerPlayer"),
    ],
    "com.dbzlegacy.adaptivedifficulty.service.DifficultyActions": [
        ("handle", "ServerPlayer, java.lang.String, long, java.lang.String"),
        ("handleArg", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
        ("handleArgNoReopen", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
    ],
    "com.dbzlegacy.adaptivedifficulty.service.DifficultyActions$Result": [
        ("ok", ""),
        ("message", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig": [
        ("get", ""),
        ("isEnabled", ""),
        ("setEnabled", "boolean"),
        ("isWhitelistEnabled", ""),
        ("setWhitelistEnabled", "boolean"),
        ("whitelistEntries", ""),
        ("addWhitelistEntry", "java.lang.String"),
        ("removeWhitelistEntry", "java.lang.String"),
        ("reload", ""),
        ("save", ""),
        ("sanitizeLive", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.util.SystemGate": [
        ("allows", "ServerPlayer"),
    ],
    "com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot": [
        ("state", ""),
        ("stateColorCode", ""),
    ],
}

OPTIONAL_METHODS = {
    "com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy": [
        ("balanceText", "ServerPlayer"),
        ("format", "long"),
        ("formatExactCost", "long"),
        ("balance", "ServerPlayer"),
        ("countOf", "ServerPlayer"),
        ("activationCost", "UnlockTier, net.minecraft.server.level.ServerPlayer"),
        ("activationCost", "UnlockTier, net.minecraft.server.level.ServerPlayer, int"),
    ],
    "com.dbzlegacy.adaptivedifficulty.tier.UnlockTier": [
        ("values", ""),
        ("byId", "int"),
        ("activationCost", ""),
        ("maxDifficulty", ""),
        ("activationCostForLevel", "int"),
        ("requiredDmzLevel", ""),
        ("requiredPrestige", ""),
        ("requirementTip", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.title.TitleSystem": [
        ("activeDisplay", "ServerPlayer"),
        ("activeId", "ServerPlayer"),
        ("has", "ServerPlayer"),
        ("syncTierTitles", "ServerPlayer"),
    ],
    "com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle": [
        ("values", ""),
        ("requirementTip", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile": [
        ("of", "ServerPlayer"),
        ("topStatsLabel", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.gui.DifficultyChatMenu": [
        ("open", "ServerPlayer, java.lang.String"),
    ],
    "com.dbzlegacy.adaptivedifficulty.gui.RivalGuiApi": [
        ("placeholders", "ServerPlayer"),
        ("linesForPage", "ServerPlayer, java.lang.String"),
        ("handleDo", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
        ("onlinePlayerNames", "ServerPlayer"),
        ("resolveOnline", "ServerPlayer, java.lang.String"),
        ("currentRivalCards", "ServerPlayer"),
        ("pastRivalCards", "ServerPlayer"),
        ("currentRivalArgs", "ServerPlayer"),
    ],
    "com.dbzlegacy.adaptivedifficulty.gui.SparGuiApi": [
        ("placeholders", "ServerPlayer"),
        ("linesForPage", "ServerPlayer, java.lang.String"),
        ("handleDo", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
        ("resolveOnline", "ServerPlayer, java.lang.String"),
    ],
    "com.dbzlegacy.adaptivedifficulty.gui.MechanicsGuiApi": [
        ("placeholders", "ServerPlayer"),
        ("linesForPage", "ServerPlayer, java.lang.String"),
        ("handleDo", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
    ],
    "com.dbzlegacy.adaptivedifficulty.gui.ProgressionGuiApi": [
        ("placeholders", "ServerPlayer"),
        ("linesForPage", "ServerPlayer, java.lang.String"),
        ("handleDo", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
        ("prestigePlaceholders", "ServerPlayer"),
        ("prestigeLines", "ServerPlayer, java.lang.String"),
        ("handlePrestigeDo", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
        ("skillsPlaceholders", "ServerPlayer"),
        ("skillsLines", "ServerPlayer, java.lang.String"),
        ("handleSkillsDo", "ServerPlayer, java.lang.String, java.lang.String, java.lang.String"),
    ],
    "com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData": [
        ("isPersonalEnabled", ""),
        ("isCoinDropChat", ""),
        ("hasUnlockedTier", "int"),
        ("resetTemporary", ""),
        ("setActiveDifficultyLevel", "long"),
        ("setActiveTier", "int"),
    ],
    "com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem": [
        ("syncUnlocks", "ServerPlayer"),
    ],
    "com.dbzlegacy.adaptivedifficulty.scaling.AreaDifficulty": [
        ("clearCache", ""),
        ("at", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.tick.NearbyMobScaler": [
        ("shutdownAllScaling", ""),
        ("processEvictions", ""),
    ],
    "com.dbzlegacy.adaptivedifficulty.tick.ScaledMobTracker": [
        ("releaseAndRevertPlayer", "ServerPlayer"),
    ],
    "com.dbzlegacy.adaptivedifficulty.world.VanillaDifficultyGuard": [
        ("parse", "java.lang.String"),
        ("set", "MinecraftServer"),
    ],
}

REQUIRED_FIELDS = {
    "com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod": [
        "VERSION",
        "MOD_ID",
        "DISPLAY_NAME",
    ],
    "com.dbzlegacy.adaptivedifficulty.calc.DifficultySnapshot": [
        "active",
        "availableMax",
        "calculated",
        "purchased",
        "personalMax",
        "teamThresholdBonus",
        "teamContribution",
        "combatRating",
        "ancientCopper",
        "dmzLevel",
        "prestige",
        "activeTier",
        "highestUnlockedTier",
        "teamMode",
        "activeTierName",
    ],
    "com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig": [
        "enabled",
        "whitelistEnabled",
        "whitelist",
        "guiBackend",
        "adminPermission",
        "areaDifficultyMode",
        "mobScaleRadius",
        "areaGroupBonusPercent",
        "mobHealthScale",
        "maxFormBoost",
        "maxLiveCombatChannel",
    ],
    "com.dbzlegacy.adaptivedifficulty.service.DifficultyActions$Result": [
        "message",
        "ok",
    ],
}

OPTIONAL_FIELDS = {
    "com.dbzlegacy.adaptivedifficulty.tier.UnlockTier": [
        "id",
        "display",
        "defaultMaxDifficulty",
    ],
    "com.dbzlegacy.adaptivedifficulty.title.DifficultyTitle": [
        "id",
        "display",
    ],
    "com.dbzlegacy.adaptivedifficulty.calc.PlayerCombatProfile": [
        "fightingClass",
        "race",
        "style",
        "weakest",
    ],
}

# GUI plugin methods Forge reflects into.
GUI_REQUIRED_METHODS = [
    ("openMenu", "Player"),
    ("openMenuForUuid", "UUID"),
    ("openChestMenu", "Player"),
    ("openChestMenuForUuid", "UUID"),
    ("openRivalMenu", "Player"),
    ("openRivalMenuForUuid", "UUID"),
    ("openRivalChestMenuForUuid", "UUID"),
    ("openSparMenu", "Player"),
    ("openSparMenuForUuid", "UUID"),
    ("openSparChestMenuForUuid", "UUID"),
    ("openHubMenu", "Player"),
    ("openHubMenuForUuid", "UUID"),
    ("openHubChestMenuForUuid", "UUID"),
    ("openProgressionMenu", "Player"),
    ("openProgressionMenuForUuid", "UUID"),
    ("openProgressionChestMenuForUuid", "UUID"),
    ("openPrestigeMenu", "Player"),
    ("openPrestigeMenuForUuid", "UUID"),
    ("openPrestigeChestMenuForUuid", "UUID"),
    ("openSkillsMenu", "Player"),
    ("openSkillsMenuForUuid", "UUID"),
    ("openSkillsChestMenuForUuid", "UUID"),
    ("openInspectForUuid", "UUID"),
    ("openLmInspectForUuid", "UUID"),
    ("clearInspectForUuid", "UUID"),
]


def jar_has_class(jar: Path, fqcn: str) -> bool:
    path = fqcn.replace(".", "/") + ".class"
    with zipfile.ZipFile(jar) as zf:
        return path in zf.namelist()


def javap_public(jar: Path, fqcn: str) -> str:
    r = subprocess.run(
        ["javap", "-classpath", str(jar), "-public", fqcn],
        capture_output=True,
        text=True,
        check=False,
    )
    if r.returncode != 0:
        return ""
    return r.stdout


def method_present(javap_out: str, name: str, sig_hint: str) -> bool:
    # Match "public ... name(...)" lines; ignore constructors named like class.
    for line in javap_out.splitlines():
        line = line.strip()
        if not line.startswith("public "):
            continue
        # method form
        m = re.search(rf"\b{re.escape(name)}\s*\(", line)
        if not m:
            continue
        if name == line.split("(")[0].split()[-1] and " class " in line:
            continue
        args = line[m.end() : line.rfind(")")]
        if not sig_hint:
            return True
        # All tokens in hint should appear in args (order-insensitive enough).
        ok = True
        for token in [t.strip() for t in sig_hint.split(",") if t.strip()]:
            short = token.split(".")[-1]
            if short not in args and token not in args:
                ok = False
                break
        if ok:
            return True
    return False


def field_present(javap_out: str, name: str) -> bool:
    for line in javap_out.splitlines():
        line = line.strip()
        if not line.startswith("public "):
            continue
        # field: ends without '('
        if "(" in line:
            continue
        # strip trailing ;
        bare = line.rstrip(";").strip()
        if bare.endswith(" " + name) or bare.endswith("." + name):
            return True
        if bare.split()[-1] == name:
            return True
    return False


def read_constant_string(jar: Path, fqcn: str, field: str) -> str | None:
    """Best-effort: read UTF-8 constant near field via strings in class file."""
    # Prefer source of truth from mods.toml / AdaptiveDifficultyMod via javap -c if needed.
    # For VERSION we parse ConstantValue from javap -verbose.
    r = subprocess.run(
        ["javap", "-classpath", str(jar), "-verbose", fqcn],
        capture_output=True,
        text=True,
        check=False,
    )
    if r.returncode != 0:
        return None
    # Find field then ConstantValue
    lines = r.stdout.splitlines()
    for i, line in enumerate(lines):
        if f" {field};" in line or line.strip().endswith(f"{field};"):
            # look ahead for ConstantValue: String ...
            for j in range(i, min(i + 12, len(lines))):
                m = re.search(r"ConstantValue:\s+String\s+(\S+)", lines[j])
                if m:
                    return m.group(1)
    return None


def plugin_yml_version() -> str | None:
    text = GUI_YML.read_text(encoding="utf-8")
    m = re.search(r"^version:\s*['\"]?([^'\"\n]+)", text, re.M)
    return m.group(1).strip() if m else None


def jar_mods_toml_version(jar: Path) -> str | None:
    with zipfile.ZipFile(jar) as zf:
        text = zf.read("META-INF/mods.toml").decode("utf-8")
    m = re.search(r'^version\s*=\s*"([^"]+)"', text, re.M)
    return m.group(1).strip() if m else None


def shipped_gui_jars() -> list[Path]:
    plugins = ROOT / "plugins"
    found: list[Path] = []
    for prefix in ("LegacyMechanicsGUI", "AdaptiveDifficultyGUI"):
        found.extend(plugins.glob(f"{prefix}-*.jar"))
    return found


def source_has_method(text: str, name: str, hint: str) -> bool:
    for line in text.splitlines():
        if f"void {name}(" in line and hint in line:
            return True
    return False


def main() -> int:
    errors: list[str] = []
    warns: list[str] = []

    if FORGE_JAR is None or not FORGE_JAR.is_file():
        print("FAIL: missing mods/LegacyMechanics-*.jar", file=sys.stderr)
        return 2
    print(f"Forge jar: {FORGE_JAR.name}")
    stray = shipped_gui_jars()
    if stray:
        names = ", ".join(p.name for p in stray)
        errors.append(f"plugins/ must not ship a GUI plugin jar ({names})")
        print(f"  FAIL plugins/ GUI jar present: {names}")
    else:
        print("  OK no LegacyMechanicsGUI jar in plugins/")

    forge_ver = read_constant_string(FORGE_JAR, "com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod", "VERSION")
    toml_ver = jar_mods_toml_version(FORGE_JAR)
    src_ver = plugin_yml_version()

    print("=== Version handshake ===")
    print(f"  AdaptiveDifficultyMod.VERSION = {forge_ver}")
    print(f"  mods.toml version             = {toml_ver}")
    print(f"  GUI plugin.yml (source)       = {src_ver}")
    for label, a, b in [
        ("Forge VERSION vs mods.toml", forge_ver, toml_ver),
        ("Forge VERSION vs source plugin.yml", forge_ver, src_ver),
    ]:
        if a != b:
            errors.append(f"version skew ({label}): {a!r} != {b!r}")
        else:
            print(f"  OK {label}: {a}")

    print("\n=== Required Forge classes ===")
    for fqcn in REQUIRED_CLASSES:
        if jar_has_class(FORGE_JAR, fqcn):
            print(f"  OK {fqcn}")
        else:
            errors.append(f"missing required class {fqcn}")
            print(f"  FAIL {fqcn}")

    print("\n=== Optional Forge classes ===")
    for fqcn in OPTIONAL_CLASSES:
        if jar_has_class(FORGE_JAR, fqcn):
            print(f"  OK {fqcn}")
        else:
            warns.append(f"missing optional class {fqcn}")
            print(f"  WARN {fqcn}")

    print("\n=== Required methods/fields ===")
    for fqcn, methods in REQUIRED_METHODS.items():
        out = javap_public(FORGE_JAR, fqcn)
        if not out:
            errors.append(f"javap failed for {fqcn}")
            continue
        for name, hint in methods:
            if method_present(out, name, hint):
                print(f"  OK {fqcn}.{name}({hint})")
            else:
                errors.append(f"missing required method {fqcn}.{name}({hint})")
                print(f"  FAIL {fqcn}.{name}({hint})")

    for fqcn, fields in REQUIRED_FIELDS.items():
        out = javap_public(FORGE_JAR, fqcn)
        if not out:
            errors.append(f"javap failed for {fqcn}")
            continue
        for name in fields:
            if field_present(out, name):
                print(f"  OK {fqcn}.{name}")
            else:
                errors.append(f"missing required field {fqcn}.{name}")
                print(f"  FAIL {fqcn}.{name}")

    print("\n=== Optional methods/fields ===")
    for fqcn, methods in OPTIONAL_METHODS.items():
        if not jar_has_class(FORGE_JAR, fqcn):
            continue
        out = javap_public(FORGE_JAR, fqcn)
        for name, hint in methods:
            if method_present(out, name, hint):
                print(f"  OK {fqcn}.{name}({hint})")
            else:
                warns.append(f"missing optional method {fqcn}.{name}({hint})")
                print(f"  WARN {fqcn}.{name}({hint})")

    for fqcn, fields in OPTIONAL_FIELDS.items():
        if not jar_has_class(FORGE_JAR, fqcn):
            continue
        out = javap_public(FORGE_JAR, fqcn)
        for name in fields:
            if field_present(out, name):
                print(f"  OK {fqcn}.{name}")
            else:
                warns.append(f"missing optional field {fqcn}.{name}")
                print(f"  WARN {fqcn}.{name}")

    print("\n=== GUI plugin Forge entrypoints (source) ===")
    gui_cls = "com.dbzlegacy.adaptivedifficulty.bukkit.AdaptiveDifficultyGuiPlugin"
    if not GUI_PLUGIN_JAVA.is_file():
        errors.append(f"missing GUI source {GUI_PLUGIN_JAVA.name}")
    else:
        gui_src = GUI_PLUGIN_JAVA.read_text(encoding="utf-8")
        for name, hint in GUI_REQUIRED_METHODS:
            if source_has_method(gui_src, name, hint):
                print(f"  OK {gui_cls}.{name}({hint})")
            else:
                errors.append(f"missing GUI method {gui_cls}.{name}({hint})")
                print(f"  FAIL {gui_cls}.{name}({hint})")

    # Package stability: no accidental rename away from com.dbzlegacy.adaptivedifficulty
    with zipfile.ZipFile(FORGE_JAR) as zf:
        pkgs = {n for n in zf.namelist() if n.startswith("com/dbzlegacy/adaptivedifficulty/") and n.endswith(".class")}
    if not pkgs:
        errors.append("Forge jar has no classes under com.dbzlegacy.adaptivedifficulty")
    else:
        print(f"\n=== Package root OK ({len(pkgs)} classes under com.dbzlegacy.adaptivedifficulty) ===")

    print("\n=== Summary ===")
    for w in warns:
        print(f"WARN: {w}")
    if errors:
        for e in errors:
            print(f"FAIL: {e}")
        print(f"{len(errors)} error(s), {len(warns)} warning(s)")
        return 1
    print(f"PASS — ABI intact ({len(warns)} warning(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
