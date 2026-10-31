package com.dbzlegacy.adaptivedifficulty.config;

import com.dbzlegacy.adaptivedifficulty.character.CharacterServicesConfig;
import com.dbzlegacy.adaptivedifficulty.kube.hotpath.HotPathConfig;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigeShopHooks;
import com.dbzlegacy.adaptivedifficulty.quest.SagaResetConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Settings modules the staff editor can open. Each row is a live config object,
 * its file, and how to save and reload it.
 * Player record files stay registered so they are obvious, and they stay closed.
 */
public final class ConfigRegistry {
    /** Shown as a tab in {@code /lm admin config}. Later slices stay registered and closed. */
    public static final int PHASE_MENU = 1;

    private ConfigRegistry() {}

    /**
     * @param rootFields field names shown at the top of this module. Null shows every field.
     * @param editable   false for player-record files such as rivalry and sparring.
     */
    public record Module(
            String id,
            String title,
            Class<?> type,
            Path file,
            Supplier<Object> instance,
            Runnable save,
            Runnable reload,
            boolean editable,
            int phase,
            Set<String> rootFields) {}

    public static List<Module> all() {
        List<Module> modules = new ArrayList<>();
        modules.add(settings(
                "difficulty",
                "Difficulty",
                DifficultyConfig.class,
                DifficultyConfig.path(),
                DifficultyConfig::get,
                null,
                PHASE_MENU));
        modules.add(settings(
                "progression",
                "Progression",
                DifficultyConfig.class,
                DifficultyConfig.path(),
                DifficultyConfig::get,
                ConfigRegistry::progressionField,
                PHASE_MENU));
        modules.add(new Module(
                "character-services",
                "Character services",
                CharacterServicesConfig.class,
                CharacterServicesConfig.path(),
                CharacterServicesConfig::get,
                () -> {
                    if (!CharacterServicesConfig.save()) {
                        throw new IllegalStateException("character-services save failed");
                    }
                },
                CharacterServicesConfig::load,
                true,
                PHASE_MENU,
                null));
        modules.add(new Module(
                "saga",
                "Saga reset",
                SagaResetConfig.class,
                SagaResetConfig.path(),
                SagaResetConfig::get,
                () -> {
                    if (!SagaResetConfig.save()) {
                        throw new IllegalStateException("saga-reset save failed");
                    }
                },
                SagaResetConfig::load,
                true,
                PHASE_MENU,
                null));
        modules.add(settings("currency", "Currency", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::currencyField, PHASE_MENU));
        modules.add(new Module(
                "hot-path",
                "Combat",
                HotPathConfig.class,
                HotPathConfig.path(),
                HotPathConfig::get,
                () -> {
                    if (!HotPathConfig.save()) {
                        throw new IllegalStateException("hot-path save failed");
                    }
                },
                HotPathConfig::load,
                true,
                PHASE_MENU,
                null));
        modules.add(settings("ai", "AI", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::aiField, 2));
        modules.add(settings("boss", "Boss", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::bossField, 2));
        modules.add(settings("elite", "Elite", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::eliteField, 2));
        modules.add(settings("evolution", "Evolution", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::evolutionField, 2));
        modules.add(settings("telemetry", "Telemetry", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::telemetryField, 2));
        modules.add(settings("tier", "Tier", DifficultyConfig.class, DifficultyConfig.path(),
                DifficultyConfig::get, ConfigRegistry::tierField, 2));
        // rivalry-v4.json and sparring.json are player records, not settings.
        modules.add(data("rival", "Rival records", ConfigPaths.rivalryPath()));
        modules.add(data("sparring", "Sparring records", ConfigPaths.sparringPath()));
        PrestigeShopHooks.addModules(modules);
        return modules;
    }

    /** Modules the menu is allowed to open. */
    public static List<Module> menuModules() {
        List<Module> open = new ArrayList<>();
        for (Module module : all()) {
            if (module.editable() && module.phase() == PHASE_MENU) {
                open.add(module);
            }
        }
        return open;
    }

    public static Module find(String id) {
        if (id == null) {
            return null;
        }
        for (Module module : all()) {
            if (module.id().equals(id)) {
                return module;
            }
        }
        return null;
    }

    private static Module settings(
            String id,
            String title,
            Class<?> type,
            Path file,
            Supplier<Object> instance,
            Predicate<String> fields,
            int phase) {
        Set<String> names = null;
        if (fields != null) {
            names = new java.util.LinkedHashSet<>();
            for (java.lang.reflect.Field field : DifficultyConfig.class.getFields()) {
                if (fields.test(field.getName())) {
                    names.add(field.getName());
                }
            }
        }
        return new Module(id, title, type, file, instance, () -> {
            DifficultyConfig.sanitizeLive();
            DifficultyConfig.save();
        }, () -> {
            DifficultyConfig.reload();
        }, true, phase, names);
    }

    /** Player records. Registered so the editor can refuse them by name. */
    private static Module data(String id, String title, Path file) {
        return new Module(id, title, null, file, () -> null, () -> {
            throw new IllegalStateException(id + " is player data");
        }, () -> {
        }, false, 2, null);
    }

    private static boolean progressionField(String name) {
        return switch (name) {
            case "enableProgression", "enablePotential", "enableLivingWorldMeditation",
                    "meditationDetectionRadius", "meditationLevelSeconds", "enableGlobalTpBoost",
                    "enableGlobalMasteryBoost",
                    "enableBioAndroid", "enableRaceLock", "enableYardrat", "enableSpiritualistKi",
                    "enableAndroidConversion", "enableKiWeapons", "enablePiercingBonus",
                    "enableDotExtraDamage", "enableApothicElemental", "enableEndDimensionStrength",
                    "enableEndPortalGuard", "enableEndNaturalDragonSpawn", "enableEndPlayerDragonSummon",
                    "endDragonSummonNetheriteCost", "enableWitherSummon", "witherSummonNetheriteCost",
                    "enableWardenSummon", "wardenSummonNetheriteCost", "summonLootBonusPerTierPercent",
                    "enableEndMobScaling", "endEnforceSingleDragon",
                    "endKiCleanupEnabled", "endKiPurgeWhenNoDragon", "endKiMaxAliveWhileDragon",
                    "enableShadowDummyLimiter", "enableSkillUnlockService", "enablePrestigeSystem",
                    "enableOverhaulPrestigeIntegration", "enableSkillCheck", "donatorPermission",
                    "selfRevivePermission", "terminalPermission", "reviveOthersPermission",
                    "skillCheckNpcNameContains", "enablePlayerStatChecker", "enableLmTips",
                    "lmTipIntervalSeconds", "lmTipNewPlayerIntervalSeconds", "lmTipLoginDelaySeconds",
                    "lmTipFrequentGroups", "lmTipNewPlayerMaxLevel", "debugLogging" -> true;
            default -> false;
        };
    }

    private static boolean currencyField(String name) {
        return "staffFreeAncientCoinCosts".equals(name) || name.startsWith("ancientCoin")
                || "enableAncientCoinDrops".equals(name);
    }

    private static boolean aiField(String name) {
        return "enableAdaptiveAi".equals(name) || name.startsWith("adaptiveAi")
                || "enableDodgeCounter".equals(name) || "dodgeCounterTier".equals(name);
    }

    private static boolean bossField(String name) {
        return name.startsWith("boss") || "enableBossScaling".equals(name)
                || "bossMechanicsMinUnlockTier".equals(name);
    }

    private static boolean eliteField(String name) {
        return name.startsWith("elite") || name.startsWith("mutation") || "enableElites".equals(name)
                || "enableMutations".equals(name);
    }

    private static boolean evolutionField(String name) {
        return "enableEnemyEvolution".equals(name) || "enemyEvolutionMinUnlockTier".equals(name);
    }

    private static boolean telemetryField(String name) {
        return name.contains("elemetry") || name.contains("Telemetry");
    }

    private static boolean tierField(String name) {
        return name.startsWith("tier") || name.startsWith("unlockTier");
    }
}
