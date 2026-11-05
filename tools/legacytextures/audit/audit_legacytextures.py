#!/usr/bin/env python3
"""Legacy Textures ship checks. usage: audit_legacytextures.py <jar> <version>

Fails the build when:
  * mods.toml loses an identity/dependency rule (displayTest, the four dependency ranges);
  * the jar carries an asset outside the legacytextures and nbarmors namespaces (a same-path override
    would make the vanilla variant unreachable), any lang file other than its own two
    (assets/legacytextures/lang/en_us.json and assets/nbarmors/lang/en_us.json; lang merges globally,
    so a ZoneMC lang file would change text for everyone), or any dmzanatomy / jinryuudragonbc file;
  * the HD payload in the jar does not match import/IMPORT_MANIFEST.json;
  * a shipped texture set file is missing or wrong (vanilla, hd -> fallback vanilla);
  * a mixin class has a non-private method that is not a handler (Mixin would merge it);
  * a Java source has a raw section sign, or menu/command/status text is not a lang lookup;
  * the absorbed NB Armors classes/resources do not match the nbarmors 0.1.0 jar (NB_MANIFEST.json);
  * a DragonMineZ touchpoint of the NB compat layer moved (DbzArmorItem constructors, DMZPlayerRenderer);
  * GeckoLib's GeoModel no longer has exactly one getBakedModel overload (GeoModelMixin targets it by
    name with require = 0, so a second overload would be hooked, or a renamed one silently skipped);
  * a DragonMineZ cache CacheFlush clears is missing from the DMZ jar (DMZ_JAR, default the newest
    mods/dragonminez-*.jar), so a DMZ update that renames one is caught here, not in game.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
LT = HERE.parent
ROOT = LT.parent.parent
SRC = LT / "src" / "main" / "java" / "com" / "dbzlegacy" / "legacytextures"
errors: list[str] = []


def check(name: str, ok: bool) -> None:
    print(("  ok   " if ok else "  FAIL ") + name)
    if not ok:
        errors.append(name)


def main() -> int:
    jar, version = sys.argv[1], sys.argv[2]
    with zipfile.ZipFile(jar) as zf:
        names = [n for n in zf.namelist() if not n.endswith("/")]
        toml = zf.read("META-INF/mods.toml").decode("utf-8")
        sets = {n.rsplit("/", 1)[1]: json.loads(zf.read(n)) for n in names
                if n.startswith("legacytextures/texture_sets/") and n.endswith(".json")}

    print("--- identity")
    check("modId legacytextures, version " + version, 'modId = "legacytextures"' in toml
          and 'displayName = "Legacy Textures"' in toml)
    versions = re.findall(r'^version = "([^"]+)"', toml, re.M)
    check(f"both [[mods]] entries (legacytextures, nbarmors) at version {version}",
          versions == [version, version] and 'modId = "nbarmors"' in toml)
    check("displayTest MATCH_VERSION on both mods", toml.count('displayTest = "MATCH_VERSION"') == 2)
    mod_java = (SRC / "LegacyTextures.java").read_text(encoding="utf-8")
    check(f"VERSION constant {version} and the boot banner 'Legacy Textures {{}} loaded' prints it",
          f'VERSION = "{version}"' in mod_java and 'LOG.info("Legacy Textures {} loaded", VERSION)' in mod_java)
    want = {
        "legacytextures": {"forge": ("true", "[47,)", None), "minecraft": ("true", "[1.20.1]", None),
                           "dragonminez": ("false", "[2.2,)", "AFTER")},
        "nbarmors": {"forge": ("true", "[47,)", None), "minecraft": ("true", "[1.20.1]", None),
                     "curios": ("true", "[5.14.1,)", "AFTER"), "dragonminez": ("false", "[2.2,)", "AFTER")},
    }
    for owner, deps_want in want.items():
        got = {}
        for d in re.findall(r'\[\[dependencies\.' + owner + r'\]\](.*?)(?=\n\[\[|\Z)', toml, re.S):
            mid = re.search(r'modId = "([^"]+)"', d).group(1)
            got[mid] = (re.search(r"mandatory = (\w+)", d).group(1), re.search(r'versionRange = "([^"]+)"', d).group(1),
                        re.search(r'ordering = "(\w+)"', d).group(1), re.search(r'side = "(\w+)"', d).group(1))
        check(f"{owner}: dependencies are exactly {sorted(deps_want)} (no separate nbarmors jar)",
              sorted(got) == sorted(deps_want))
        for mid, (mand, rng, order) in deps_want.items():
            g = got.get(mid)
            check(f"{owner} -> {mid} mandatory={mand} {rng}" + (f" {order}" if order else "") + " side BOTH",
                  g is not None and g[0] == mand and g[1] == rng and (order is None or g[2] == order) and g[3] == "BOTH")

    print("--- assets")
    assets = [n for n in names if n.startswith("assets/")]
    namespaces = {n.split("/")[1] for n in assets}
    check("assets only under legacytextures and the absorbed nbarmors (no dragonminez/minecraft overrides)",
          namespaces == {"legacytextures", "nbarmors"})
    check("lang: only Legacy Textures' own en_us and NB Armors' en_us (no ZoneMC lang)",
          sorted(n for n in assets if "/lang/" in n) == ["assets/legacytextures/lang/en_us.json",
                                                         "assets/nbarmors/lang/en_us.json"])
    check("no dmzanatomy / jinryuudragonbc files", not any(("dmzanatomy" in n or "jinryuudragonbc" in n) for n in names))
    check("blank.png present", "assets/legacytextures/textures/blank.png" in names)
    manifest = json.loads((LT / "import" / "IMPORT_MANIFEST.json").read_text(encoding="utf-8"))
    payload = [n for n in assets if n.split("/")[3:4] == ["hd"]]
    check(f"HD payload in jar = IMPORT_MANIFEST shipped_total ({manifest['shipped_total']})",
          len(payload) == manifest["shipped_total"])
    check("payload is HD variants only: assets/legacytextures/<textures|geo|animations>/hd/<ns>/...",
          all(re.fullmatch(r"assets/legacytextures/(textures|geo|animations)/hd/(dragonminez|minecraft|nbarmors)/.+",
                           n) for n in payload))

    print("--- NB Armors (absorbed)")
    nbm = json.loads((LT / "import" / "NB_MANIFEST.json").read_text(encoding="utf-8"))
    nb_res = [n for n in names if n.startswith(("assets/nbarmors/", "data/nbarmors/", "data/curios/", "legacy_models/"))
              or n in ("nbarmors_catalog.json", "nbarmors_tabs.json")]
    check(f"NB resources in jar = NB_MANIFEST ({nbm['resources_total']})", len(nb_res) == nbm["resources_total"])
    nb_classes = [n for n in names if n.startswith("com/dbzlegacy/legacytextures/nb/") and n.endswith(".class")
                  and not n.endswith("package-info.class")]
    check(f"all {nbm['classes_in_jar']} NB classes moved to com/dbzlegacy/legacytextures/nb/ ({len(nb_classes)})",
          sorted(c.replace("com/dbzlegacy/legacytextures/nb/", "") for c in nb_classes)
          == sorted(c.replace("com/nbarmors/", "") for c in nbm["classes"]))
    check("no com/nbarmors classes left", not any(n.startswith("com/nbarmors/") for n in names))
    with zipfile.ZipFile(jar) as zf:
        catalog = json.loads(zf.read("nbarmors_catalog.json").decode("utf-8-sig"))
    models = {n for n in names if n.startswith("assets/nbarmors/models/item/")}
    check(f"every catalog item ({len(catalog)}) has its nbarmors item model",
          all(f"assets/nbarmors/models/item/{e['id']}.json" in models for e in catalog))
    check("Curios vanity slot + entity binding shipped", "data/nbarmors/curios/slots/vanity.json" in names
          and "data/nbarmors/curios/entities/vanity.json" in names)

    print("--- texture sets")
    v, hd = sets.get("vanilla.json"), sets.get("hd.json")
    check("vanilla.json: id vanilla, blank namespace", v is not None and v.get("id") == "vanilla"
          and v.get("namespace", "") == "")
    check("hd.json: id hd, legacytextures/hd, fallback vanilla", hd is not None and hd.get("id") == "hd"
          and hd.get("namespace") == "legacytextures" and hd.get("basePath") == "hd" and hd.get("fallback") == "vanilla")
    check("hd.json covers players, armor, gui, world",
          hd is not None and sorted(hd.get("categories", [])) == ["armor", "gui", "players", "world"])

    print("--- sources")
    java = list(SRC.rglob("*.java"))
    check("no raw section sign in Java sources", not any("§" in p.read_text(encoding="utf-8") for p in java))
    bad = []
    for p in (SRC / "mixin").glob("*Mixin.java"):
        code = re.sub(r"/\*.*?\*/|//[^\n]*", "", p.read_text(encoding="utf-8"), flags=re.S)
        for m in re.finditer(r"((?:@\w+(?:\((?:[^()]|\([^()]*\))*\))?\s*)*)((?:public|protected|private|static|final|"
                             r"abstract|\s)*)[\w<>\[\],.?]+\s+(\w+)\s*\([^)]*\)\s*\{", code):
            annos, mods, name = m.group(1), m.group(2), m.group(3)
            if "private" not in mods and not re.search(r"@(Inject|ModifyVariable|Redirect|Shadow|Overwrite)", annos):
                bad.append(f"{p.stem}.{name}")
    check("mixin classes: every non-handler method is private" + (f" ({bad})" if bad else ""), not bad)
    screen = (SRC / "client" / "TextureScreen.java").read_text(encoding="utf-8")
    check("menu text: no 'Hub', no 'Invalid input'", "Hub" not in screen and "Invalid input" not in screen)
    lang = json.loads((LT / "src/main/resources/assets/legacytextures/lang/en_us.json").read_text(encoding="utf-8"))
    player_text = [SRC / "client" / "TextureScreen.java", SRC / "client" / "ClientStatus.java",
                   SRC / "server" / "LtCommand.java"]
    literal = [p.name for p in player_text if "m_237113_" in p.read_text(encoding="utf-8")]
    check("menu, command and status text are lang lookups (no Component.literal)" + (f" {literal}" if literal else ""),
          not literal)
    used = set()
    for p in java:
        used |= set(re.findall(r'"((?:legacytextures|key)\.[a-z0-9_.]+[a-z0-9_])"', p.read_text(encoding="utf-8")))
    used |= {f"legacytextures.preset.{p}{suf}" for p in ("all_hd", "players_hd_only", "all_vanilla") for suf in ("", ".help")}
    used |= {f"legacytextures.switch.{w}{suf}" for w in ("players", "nbarmor", "world") for suf in ("", ".help")}
    used -= {"legacytextures.mixins.json", "legacytextures.preset", "legacytextures.switch"}
    missing_keys = sorted(k for k in used if k not in lang)
    check("every lang key the code uses is in assets/legacytextures/lang/en_us.json"
          + (f" {missing_keys}" if missing_keys else ""), not missing_keys)

    dmz = os.environ.get("DMZ_JAR") or str(ROOT / "tools/dmz-godki-patch/out/dragonminez-2.2.1.1.jar")
    gecko = str(sorted((ROOT / "mods").glob("geckolib-forge-1.20.1-*.jar"))[-1])
    print("--- DragonMineZ touchpoints of the NB Armors compat layer (" + Path(dmz).name + ")")
    armor = subprocess.run(["javap", "-cp", dmz + os.pathsep + gecko, "com.dragonminez.common.init.armor.DbzArmorItem"],
                           capture_output=True, text=True).stdout
    base = ("com.dragonminez.common.init.armor.DbzArmorItem(net.minecraft.world.item.ArmorMaterial, "
            "net.minecraft.world.item.ArmorItem$Type, net.minecraft.world.item.Item$Properties, java.lang.String")
    check("DbzArmorItem(material, type, properties, itemId) constructor", base + ");" in armor)
    check("DbzArmorItem(material, type, properties, modId, itemId) constructor (used by NbDmzArmorItem)",
          base + ", java.lang.String);" in armor)
    renderer = subprocess.run(["javap", "-cp", dmz + os.pathsep + gecko, "com.dragonminez.client.render.DMZPlayerRenderer"],
                              capture_output=True, text=True).stdout
    check("DMZPlayerRenderer extends GeoEntityRenderer (DmzClientCompat adds its layers there)",
          "class com.dragonminez.client.render.DMZPlayerRenderer" in renderer
          and "extends software.bernie.geckolib.renderer.GeoEntityRenderer" in renderer)

    print("--- GeckoLib touchpoint of GeoModelMixin (" + Path(gecko).name + ")")
    geo = subprocess.run(["javap", "-cp", gecko, "software.bernie.geckolib.model.GeoModel"],
                         capture_output=True, text=True).stdout
    overloads = [line.strip() for line in geo.splitlines() if re.search(r"\bgetBakedModel\(", line)]
    check("GeoModel has exactly one getBakedModel overload, getBakedModel(ResourceLocation)" + (f" {overloads}" if len(overloads) != 1 else ""),
          len(overloads) == 1 and "getBakedModel(net.minecraft.resources.ResourceLocation)" in overloads[0])

    print("--- DragonMineZ caches CacheFlush clears")
    flush = (SRC / "client" / "CacheFlush.java").read_text(encoding="utf-8")
    calls = re.findall(r'\{DMZ \+ "([\w.]+)", "(\w+)"\}', flush)
    for cls, member in calls:
        out = subprocess.run(["javap", "-p", "-cp", dmz, "com.dragonminez.client." + cls],
                             capture_output=True, text=True).stdout
        check(f"{Path(dmz).name}: {cls}.{member}", re.search(r"\b" + member + r"\b", out) is not None)
    if errors:
        print(f"ERROR: {len(errors)} Legacy Textures check(s) failed", file=sys.stderr)
        return 1
    print("Legacy Textures audit OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
