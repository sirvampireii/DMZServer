package com.dbzlegacy.adaptivedifficulty.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Staff-facing map from progression module flags to config fields and Java sources.
 * Full behavior lives in code (ports of old KubeJS/CNPC scripts), not in-game script files.
 */
public final class ProgressionModuleCatalog {
    private ProgressionModuleCatalog() {}

    public record ModuleInfo(String title, String configKey, String javaClass, String legacyScript) {}

    private static final Map<String, ModuleInfo> BY_FLAG = Map.ofEntries(
            Map.entry("potential", info("Potential", "enablePotential",
                    "progression.skills.PotentialProgression", "kubejs potential / spar")),
            Map.entry("boost", info("Global TP Boost", "enableGlobalTpBoost",
                    "progression.tp.GlobalTpBoost", "kubejs global_tp_boost")),
            Map.entry("bio", info("Bio-Android", "enableBioAndroid",
                    "progression.tp.BioAndroidAbsorb", "kubejs bio android absorb")),
            Map.entry("yardrat", info("Yardrat", "enableYardrat",
                    "progression.race.YardratProgression", "kubejs yardrat")),
            Map.entry("spiritualist", info("Spiritualist Ki", "enableSpiritualistKi",
                    "progression.race.SpiritualistKiControl", "kubejs spiritualist")),
            Map.entry("android", info("Android convert", "enableAndroidConversion",
                    "progression.race.AndroidConversion", "CNPC Dr. Gero script")),
            Map.entry("kiweapons", info("Ki Weapons", "enableKiWeapons",
                    "progression.combat.KiWeapons", "kubejs ki weapons")),
            Map.entry("piercing", info("Piercing", "enablePiercingBonus",
                    "progression.combat.PiercingBonus", "kubejs piercing")),
            Map.entry("dot", info("DoT Extra", "enableDotExtraDamage",
                    "progression.combat.DotExtraDamage", "kubejs dot extra")),
            Map.entry("apothic", info("Apothic Elemental", "enableApothicElemental",
                    "progression.combat.ApothicElemental", "kubejs apothic elemental")),
            Map.entry("end", info("End Strength", "enableEndDimensionStrength",
                    "progression.end.EndDimensionStrength", "kubejs end dimension")),
            Map.entry("endportal", info("End Portal Guard", "enableEndPortalGuard",
                    "progression.end.EndPortalGuard", "kubejs end portal")),
            Map.entry("prestige", info("Prestige System", "enablePrestigeSystem",
                    "progression.shop.PrestigeSystem", "kubejs prestige shop")),
            Map.entry("skills", info("Skill Unlock Service", "enableSkillUnlockService",
                    "progression.shop.SkillUnlockService", "kubejs skill unlock")),
            Map.entry("shadow", info("Shadow Dummy", "enableShadowDummyLimiter",
                    "progression.dummy.ShadowDummyLimiter", "CNPC shadow dummy")),
            Map.entry("statchecker", info("Stat Checker", "enablePlayerStatChecker",
                    "progression.PlayerStatChecker", "kubejs stat checker"))
    );

    private static ModuleInfo info(String title, String configKey, String javaClass, String legacyScript) {
        return new ModuleInfo(title, configKey, javaClass, legacyScript);
    }

    /** Flag keys shown as toggles on a progression GUI section (lowercase). */
    public static boolean isSectionPage(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "skills", "tp", "race", "combat", "end", "shop", "utility", "flags" -> true;
            default -> false;
        };
    }

    public static String[] flagKeysForPage(String page) {
        if (page == null || page.isBlank()) {
            return new String[0];
        }
        return switch (page.toLowerCase(Locale.ROOT)) {
            case "skills" -> new String[]{"potential"};
            case "tp" -> new String[]{"boost", "bio"};
            case "race" -> new String[]{"yardrat", "spiritualist", "android"};
            case "combat" -> new String[]{"kiweapons", "piercing", "dot", "apothic"};
            case "end" -> new String[]{"end", "endportal"};
            case "shop" -> new String[]{"prestige", "skills"};
            case "utility" -> new String[]{"shadow", "statchecker"};
            default -> new String[0];
        };
    }

    public static String[] allFlagKeys() {
        return BY_FLAG.keySet().stream().sorted().toArray(String[]::new);
    }

    public static ModuleInfo module(String flagKey) {
        if (flagKey == null) {
            return null;
        }
        return BY_FLAG.get(flagKey.toLowerCase(Locale.ROOT));
    }

    public static String displayTitle(String flagKey) {
        ModuleInfo m = module(flagKey);
        if (m != null) {
            return m.title();
        }
        return flagKey == null ? "" : flagKey;
    }

    /** Chat lines for one module (staff). */
    public static List<String> formatModuleDoc(String flagKey) {
        List<String> out = new ArrayList<>();
        ModuleInfo m = module(flagKey);
        if (m == null) {
            out.add("§cUnknown module flag: §f" + flagKey);
            return out;
        }
        out.add("§6§l" + m.title() + " §8(§7" + flagKey + "§8)");
        out.add("§7Config §fconfig/adaptivedifficulty.json §7→ §f" + m.configKey());
        out.add("§7Code §fcom.dbzlegacy.adaptivedifficulty." + m.javaClass());
        if (m.legacyScript() != null && !m.legacyScript().isBlank()) {
            out.add("§7Legacy §f" + m.legacyScript());
        }
        out.add("§8Edit the Java module or JSON, then §f/lm admin reload§8.");
        out.add("§8Toggle in-game: progression section GUIs or §f/lm admin progression do flag " + flagKey);
        return out;
    }

    /** Chat lines summarizing every module on a section page. */
    public static List<String> formatSectionDoc(String page) {
        List<String> out = new ArrayList<>();
        out.add("§6§lProgression sources · §f" + page);
        out.add("§8Behavior is implemented in LegacyMechanics Java (not CNPC scripts).");
        out.add("");
        for (String key : flagKeysForPage(page)) {
            ModuleInfo m = module(key);
            if (m == null) {
                continue;
            }
            out.add("§e" + m.title() + " §8— §7" + m.configKey() + " §8· §f" + m.javaClass());
        }
        out.add("");
        out.add("§8Reload after file edits: §f/lm admin reload");
        return out;
    }
}
