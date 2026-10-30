package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem;
import com.dragonminez.common.stats.StatsData;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Level cap and stat-total math shared with dmzrevamp's Overhaul.
 * <p>
 * The cap is Overhaul's native {@code PrestigeSystem.levelCap}: it rises with the Overhaul prestige
 * count (LM sets that from held prestige), {@code floor(initialLevelCap + count × maxLevel /
 * maxPrestigeCount)} capped at {@code maxLevel}. LM pins 50k / 150k, so with 10 max that is 50k, 65k,
 * 80k … 140k, then 150k from 7 held. {@link #levelCap(StatsData)} reads it; LM does not override or
 * recompute it. Without dmzrevamp the cap is {@link #OVERHAUL_LEVEL_CAP} (100k). Breakthroughs were
 * removed in 4.7.2; stored counts are left in place and read by nothing.
 */
public final class LmOverhaulCapMath {
    /** Stock Overhaul {@code levelUpPerPoints} when {@code LevelingRevamp.json} is default. */
    public static final int LEVEL_UP_PER_POINTS = 6;
    /** The level cap without dmzrevamp installed (no Overhaul to ask). */
    public static final int OVERHAUL_LEVEL_CAP = PrestigePointsSystem.BASE_LEVEL_CAP;
    /** Overhaul {@code initialLevelCap} LM pins: the cap at 0 held prestige. */
    public static final int OVERHAUL_INITIAL_LEVEL_CAP = 50_000;
    /** Overhaul {@code maxLevel} LM pins (the DMZ stat ceiling). */
    public static final int OVERHAUL_ABSOLUTE_LEVEL_CAP = PrestigePointsSystem.ABSOLUTE_LEVEL_CAP;

    private static volatile Method revampInitialStatTotal;
    private static volatile Method revampPointsPerLevel;

    private LmOverhaulCapMath() {}

    /** The player's level cap: Overhaul's native {@code levelCap} (100k without dmzrevamp). */
    public static int levelCap(StatsData data) {
        return com.dbzlegacy.adaptivedifficulty.progression.bridge.DmzRevampPrestigeBridge.levelCap(data);
    }

    public static int levelCap(ServerPlayer player) {
        if (player == null) {
            return overhaulLevelCap();
        }
        return levelCap(com.dbzlegacy.adaptivedifficulty.calc.DmzProgression.stats(player));
    }

    public static int maxAssignableTotal(StatsData data, int levelCap) {
        if (data == null || levelCap < 1) {
            return 0;
        }
        int initial = initialStatTotal(data);
        int perLevel = statPointsPerLevel();
        long max = (long) initial + (long) Math.max(0, levelCap - 1) * perLevel;
        return (int) Math.min(Integer.MAX_VALUE, max);
    }

    public static int maxAssignableTotal(StatsData data, ServerPlayer player) {
        return maxAssignableTotal(data, levelCap(player));
    }

    /** Called from Overhaul's {@code maxAssignableTotal}; uses Overhaul's native cap for the player. */
    public static int maxAssignableTotal(StatsData data) {
        return maxAssignableTotal(data, overhaulLevelCap(data));
    }

    /**
     * The cap for a player with no data: Overhaul's configured {@code initialLevelCap} (the cap at 0
     * held, pinned to {@link #OVERHAUL_INITIAL_LEVEL_CAP}), or {@link #OVERHAUL_LEVEL_CAP} without
     * dmzrevamp.
     */
    public static int overhaulLevelCap() {
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Class<?> cfg = Class.forName("com.dmzrevamp.config.LevelingRevampConfig");
                Object revampCfg = cfg.getMethod("get").invoke(null);
                Object prestige = revampCfg.getClass().getField("Prestige").get(revampCfg);
                return Math.max(1, prestige.getClass().getField("initialLevelCap").getInt(prestige));
            } catch (Throwable ignored) {
            }
            return OVERHAUL_INITIAL_LEVEL_CAP;
        }
        return OVERHAUL_LEVEL_CAP;
    }

    /**
     * Overhaul's native {@code levelCap} for this player (rises with the prestige count). Used by
     * {@code DmzRevampPrestigeCapMixin}'s {@code maxAssignableTotal}, {@code hexStatReference} and
     * {@code canPrestige}. Nothing may call this from a hook on {@code levelCap} itself (it reads
     * {@code levelCap} and would recurse).
     */
    public static int overhaulLevelCap(StatsData data) {
        return levelCap(data);
    }

    /**
     * Most prestiges a player can hold: Overhaul {@code LevelingRevampConfig.Prestige.maxPrestigeCount},
     * or {@link LmOverhaulPrestigeIntegration#OVERHAUL_MAX_PRESTIGE} when dmzrevamp is absent.
     */
    public static int configuredMaxPrestigeCount() {
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Class<?> cfg = Class.forName("com.dmzrevamp.config.LevelingRevampConfig");
                Object revampCfg = cfg.getMethod("get").invoke(null);
                Object prestige = revampCfg.getClass().getField("Prestige").get(revampCfg);
                return Math.max(1, prestige.getClass().getField("maxPrestigeCount").getInt(prestige));
            } catch (Throwable ignored) {
            }
        }
        return LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE;
    }

    /**
     * Force live Overhaul {@code initialLevelCap} to 50k and {@code maxLevel} to 150k, the ends of
     * the native cap ladder.
     */
    public static void pinOverhaulLevelCaps() {
        if (!ModList.get().isLoaded("dmzrevamp")) {
            return;
        }
        try {
            Class<?> cfgCls = Class.forName("com.dmzrevamp.config.LevelingRevampConfig");
            Object cfg = cfgCls.getMethod("get").invoke(null);
            if (cfg == null) {
                return;
            }
            Object levels = cfg.getClass().getField("levelsAndAttributes").get(cfg);
            Object prestige = cfg.getClass().getField("Prestige").get(cfg);
            java.lang.reflect.Field maxLevel = levels.getClass().getField("maxLevel");
            java.lang.reflect.Field initial = prestige.getClass().getField("initialLevelCap");
            if (maxLevel.getInt(levels) != OVERHAUL_ABSOLUTE_LEVEL_CAP) {
                maxLevel.setInt(levels, OVERHAUL_ABSOLUTE_LEVEL_CAP);
            }
            if (initial.getInt(prestige) != OVERHAUL_INITIAL_LEVEL_CAP) {
                initial.setInt(prestige, OVERHAUL_INITIAL_LEVEL_CAP);
            }
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Overhaul level caps pinned: initialLevelCap={} maxLevel={}",
                    AdaptiveDifficultyMod.MOD_ID,
                    initial.getInt(prestige),
                    maxLevel.getInt(levels));
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] Overhaul level cap pin failed: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }

    private static int initialStatTotal(StatsData data) {
        if (data != null && ModList.get().isLoaded("dmzrevamp")) {
            try {
                if (revampInitialStatTotal == null) {
                    Class<?> helper = Class.forName("com.dmzrevamp.revamp.DmzRevampHelper");
                    revampInitialStatTotal = helper.getMethod("getInitialStatTotal", StatsData.class);
                }
                Object v = revampInitialStatTotal.invoke(null, data);
                if (v instanceof Number n) {
                    return Math.max(0, n.intValue());
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static int statPointsPerLevel() {
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                if (revampPointsPerLevel == null) {
                    Class<?> helper = Class.forName("com.dmzrevamp.revamp.DmzRevampHelper");
                    revampPointsPerLevel = helper.getMethod("getConfiguredStatPointsPerLevel");
                }
                Object v = revampPointsPerLevel.invoke(null);
                if (v instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (Throwable ignored) {
            }
        }
        return LEVEL_UP_PER_POINTS;
    }
}
