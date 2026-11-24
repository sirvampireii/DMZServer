package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;

/**
 * Typed accessors for natural-progression feature toggles stored on {@link DifficultyConfig}.
 */
public final class ProgressionConfig {
    private ProgressionConfig() {}

    public static boolean masterEnabled() {
        return DifficultyConfig.get().enableProgression;
    }

    public static boolean potential() {
        return masterEnabled() && DifficultyConfig.get().enablePotential;
    }

    /** Time meditating near another player, or near two living circle NPCs. */
    public static boolean livingWorldMeditation() {
        return masterEnabled() && DifficultyConfig.get().enableLivingWorldMeditation;
    }

    public static boolean globalTpBoost() {
        return masterEnabled() && DifficultyConfig.get().enableGlobalTpBoost;
    }

    public static boolean globalMasteryBoost() {
        return masterEnabled() && DifficultyConfig.get().enableGlobalMasteryBoost;
    }

    public static boolean bioAndroid() {
        return masterEnabled() && DifficultyConfig.get().enableBioAndroid;
    }

    /** Race lock is retired. Selection is never blocked. */
    public static boolean raceLock() {
        return false;
    }

    public static boolean yardrat() {
        return masterEnabled() && DifficultyConfig.get().enableYardrat;
    }

    public static boolean spiritualistKi() {
        return masterEnabled() && DifficultyConfig.get().enableSpiritualistKi;
    }

    public static boolean androidConversion() {
        return masterEnabled() && DifficultyConfig.get().enableAndroidConversion;
    }

    public static boolean kiWeapons() {
        return masterEnabled() && DifficultyConfig.get().enableKiWeapons;
    }

    public static boolean piercingBonus() {
        return masterEnabled() && DifficultyConfig.get().enablePiercingBonus;
    }

    public static boolean dotExtraDamage() {
        return masterEnabled() && DifficultyConfig.get().enableDotExtraDamage;
    }

    public static boolean apothicElemental() {
        return masterEnabled() && DifficultyConfig.get().enableApothicElemental;
    }

    public static boolean endDimensionStrength() {
        return masterEnabled() && DifficultyConfig.get().enableEndDimensionStrength;
    }

    public static boolean endPortalGuard() {
        return masterEnabled() && DifficultyConfig.get().enableEndPortalGuard;
    }

    /** End mob HP/DEF scaling — off by default (script v2.11.0). */
    public static boolean endMobScaling() {
        return endDimensionStrength() && DifficultyConfig.get().enableEndMobScaling;
    }

    public static boolean shadowDummyLimiter() {
        return masterEnabled() && DifficultyConfig.get().enableShadowDummyLimiter;
    }

    public static boolean skillUnlockService() {
        return masterEnabled() && DifficultyConfig.get().enableSkillUnlockService;
    }

    public static boolean prestigeSystem() {
        return masterEnabled() && DifficultyConfig.get().enablePrestigeSystem;
    }

    public static boolean playerStatChecker() {
        return masterEnabled() && DifficultyConfig.get().enablePlayerStatChecker;
    }

    public static String statusSummary() {
        DifficultyConfig c = DifficultyConfig.get();
        return "§eSkills§7: master=" + onOff(c.enableProgression)
                + " pot=" + onOff(c.enablePotential)
                + " med=" + onOff(c.enableLivingWorldMeditation)
                + "\n§6TP§7: boost=" + onOff(c.enableGlobalTpBoost)
                + " mastery=" + onOff(c.enableGlobalMasteryBoost)
                + " bio=" + onOff(c.enableBioAndroid)
                + "\n§bRace§7: yard=" + onOff(c.enableYardrat)
                + " spirit=" + onOff(c.enableSpiritualistKi)
                + " android=" + onOff(c.enableAndroidConversion)
                + "\n§cCombat§7: ki=" + onOff(c.enableKiWeapons)
                + " pierce=" + onOff(c.enablePiercingBonus)
                + " dot=" + onOff(c.enableDotExtraDamage)
                + " apothic=" + onOff(c.enableApothicElemental)
                + "\n§5End§7: end=" + onOff(c.enableEndDimensionStrength)
                + " endmobs=" + onOff(c.enableEndMobScaling)
                + " endportal=" + onOff(c.enableEndPortalGuard)
                + "\n§aShop§7: prestige=" + onOff(c.enablePrestigeSystem)
                + " skills=" + onOff(c.enableSkillUnlockService)
                + "\n§7Utility: shadow=" + onOff(c.enableShadowDummyLimiter)
                + " statcheck=" + onOff(c.enablePlayerStatChecker);
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    /**
     * 4.7.81: {@link #statusSummary} for a menu page - each feature by name, no config keys
     * ({@code Skills  Mastery, Potential Unlock and Meditation on}). The chat status keeps the keys staff type.
     */
    public static java.util.List<String> statusLinesInWords() {
        DifficultyConfig c = DifficultyConfig.get();
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(wordsLine(UltraStyle.YELLOW + "Skills", new String[] {"Mastery", "Potential Unlock", "Meditation"},
                c.enableProgression, c.enablePotential, c.enableLivingWorldMeditation));
        out.add(wordsLine(UltraStyle.GOLD + "TP", new String[] {"TP boost", "Mastery boost", "Bio-Android"},
                c.enableGlobalTpBoost, c.enableGlobalMasteryBoost, c.enableBioAndroid));
        out.add(wordsLine(UltraStyle.AQUA + "Race", new String[] {"Yardrat", "Spiritualist Ki", "Android conversion"},
                c.enableYardrat, c.enableSpiritualistKi, c.enableAndroidConversion));
        out.add(wordsLine(UltraStyle.RED + "Combat",
                new String[] {"Ki weapons", "Piercing bonus", "Damage over time", "Apothic elements"},
                c.enableKiWeapons, c.enablePiercingBonus, c.enableDotExtraDamage, c.enableApothicElemental));
        out.add(wordsLine(UltraStyle.DARK_PURPLE + "End", new String[] {"End strength", "End mob scaling", "End portal guard"},
                c.enableEndDimensionStrength, c.enableEndMobScaling, c.enableEndPortalGuard));
        out.add(wordsLine(UltraStyle.GREEN + "Shop", new String[] {"Prestige", "Skill unlock"},
                c.enablePrestigeSystem, c.enableSkillUnlockService));
        out.add(wordsLine(UltraStyle.GRAY + "Utility", new String[] {"Shadow dummy limit", "Stat checker"},
                c.enableShadowDummyLimiter, c.enablePlayerStatChecker));
        return out;
    }

    /** {@code Group  A, B and C on, D off}. */
    static String wordsLine(String group, String[] names, boolean... on) {
        java.util.List<String> yes = new java.util.ArrayList<>();
        java.util.List<String> no = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            (i < on.length && on[i] ? yes : no).add(names[i]);
        }
        StringBuilder sb = new StringBuilder(group).append(' ').append(UltraStyle.VALUE);
        if (!yes.isEmpty()) {
            sb.append(joinWords(yes)).append(" on");
        }
        if (!no.isEmpty()) {
            sb.append(yes.isEmpty() ? "" : ", ").append(joinWords(no)).append(" off");
        }
        return sb.toString();
    }

    /** {@code A}, {@code A and B}, {@code A, B and C}. */
    private static String joinWords(java.util.List<String> words) {
        if (words.size() == 1) {
            return words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }
}
