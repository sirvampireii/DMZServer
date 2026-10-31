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
 * dmzrevamp 2.2.4 has one level cap for everyone, {@code PrestigeSystem.configuredLevelMaximum()}
 * ({@code levelsAndAttributes.maxLevel}, which LM pins to 150k; DMZ's own max when Overhaul levels are
 * off), and no per-prestige ladder ({@code levelCap}, {@code initialLevelCap},
 * {@code maxPrestigeCount} are gone). LM keeps its own ladder by held count
 * ({@link #prestigeRequirement}): 50k at 0 held, +15k per held, 150k from 7 held, never above
 * {@link #overhaulLevelCap()}. It is both the level needed to prestige (4.7.15; LM's menu and, through
 * {@link #blocksOverhaulPrestige}, Overhaul's button) and the player's level cap (4.7.16,
 * {@link #levelCap(StatsData)}), so a player cannot level past what their next prestige needs. Without dmzrevamp the cap is
 * {@link #OVERHAUL_LEVEL_CAP} (100k). The most prestiges a player can hold is LM's own
 * {@link LmOverhaulPrestigeIntegration#OVERHAUL_MAX_PRESTIGE} (20); 2.2.4 has no count cap, so
 * {@code DmzRevampPrestigeCapMixin} enforces it on Overhaul's side. Breakthroughs were removed in
 * 4.7.2; stored counts are left in place and read by nothing.
 */
public final class LmOverhaulCapMath {
    /** Stock Overhaul {@code levelUpPerPoints} when {@code LevelingRevamp.json} is default. */
    public static final int LEVEL_UP_PER_POINTS = 6;
    /** The level cap without dmzrevamp installed (no Overhaul to ask). */
    public static final int OVERHAUL_LEVEL_CAP = PrestigePointsSystem.BASE_LEVEL_CAP;
    /** Overhaul {@code maxLevel} LM pins (the DMZ stat ceiling). */
    public static final int OVERHAUL_ABSOLUTE_LEVEL_CAP = PrestigePointsSystem.ABSOLUTE_LEVEL_CAP;

    /** Level needed to prestige at 0-6 held (4.7.15); 7 held and up need {@link #PRESTIGE_NEED_TOP}. */
    private static final int[] PRESTIGE_NEED = {50_000, 65_000, 80_000, 95_000, 110_000, 125_000, 140_000};
    /** Level needed to prestige from 7 held up. */
    public static final int PRESTIGE_NEED_TOP = 150_000;

    private static volatile Method revampLevelMaximum;
    private static volatile Method revampInitialStatTotal;
    private static volatile Method revampPointsPerLevel;

    private LmOverhaulCapMath() {}

    /** The player's level cap: {@link #prestigeRequirement} of their held count (4.7.16). */
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

    /** Called from Overhaul's {@code maxAssignableTotal}; uses Overhaul's level cap. */
    public static int maxAssignableTotal(StatsData data) {
        return maxAssignableTotal(data, overhaulLevelCap(data));
    }

    /**
     * The level cap: dmzrevamp 2.2.4's {@code PrestigeSystem.configuredLevelMaximum()} (one value for
     * every player), {@link #OVERHAUL_ABSOLUTE_LEVEL_CAP} if that cannot be read, or
     * {@link #OVERHAUL_LEVEL_CAP} without dmzrevamp.
     */
    public static int overhaulLevelCap() {
        if (ModList.get().isLoaded("dmzrevamp")) {
            try {
                Method m = revampLevelMaximum;
                if (m == null) {
                    m = Class.forName("com.dmzrevamp.revamp.prestige.PrestigeSystem")
                            .getMethod("configuredLevelMaximum");
                    revampLevelMaximum = m;
                }
                Object v = m.invoke(null);
                if (v instanceof Number n && n.intValue() > 0) {
                    return n.intValue();
                }
            } catch (Throwable ignored) {
            }
            return OVERHAUL_ABSOLUTE_LEVEL_CAP;
        }
        return OVERHAUL_LEVEL_CAP;
    }

    /**
     * This player's level cap ({@link #levelCap(StatsData)}, by held count), for
     * {@code DmzRevampPrestigeCapMixin}'s {@code maxAssignableTotal}.
     */
    public static int overhaulLevelCap(StatsData data) {
        return levelCap(data);
    }

    /**
     * Level needed for the next prestige with {@code held} prestiges held: 50k, 65k, 80k, 95k, 110k,
     * 125k, 140k for 0-6 held, then 150k. Never above Overhaul's cap ({@link #overhaulLevelCap()}), so
     * it stays reachable. Also the player's level cap (4.7.16): reaching it stops level gains until the
     * next prestige raises it.
     */
    public static int prestigeRequirement(int held) {
        int h = Math.max(0, held);
        int need = h < PRESTIGE_NEED.length ? PRESTIGE_NEED[h] : PRESTIGE_NEED_TOP;
        return Math.min(need, overhaulLevelCap());
    }

    /**
     * LM's veto on Overhaul's own prestige (its Statistics button, {@code canPrestigeAuthoritative}):
     * at the cap of 20, or below {@link #prestigeRequirement} for the Overhaul count (= held).
     */
    public static boolean blocksOverhaulPrestige(int count, int level) {
        return count >= configuredMaxPrestigeCount() || level < prestigeRequirement(count);
    }

    /**
     * Most prestiges a player can hold: LM's {@link LmOverhaulPrestigeIntegration#OVERHAUL_MAX_PRESTIGE}
     * (20). dmzrevamp 2.2.4 has no {@code maxPrestigeCount}, so LM owns this limit.
     */
    public static int configuredMaxPrestigeCount() {
        return LmOverhaulPrestigeIntegration.OVERHAUL_MAX_PRESTIGE;
    }

    /** Force live Overhaul {@code levelsAndAttributes.maxLevel} to 150k, the level cap. */
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
            java.lang.reflect.Field maxLevel = levels.getClass().getField("maxLevel");
            if (maxLevel.getInt(levels) != OVERHAUL_ABSOLUTE_LEVEL_CAP) {
                maxLevel.setInt(levels, OVERHAUL_ABSOLUTE_LEVEL_CAP);
            }
            AdaptiveDifficultyMod.LOGGER.info(
                    "[{}] Overhaul level cap pinned: maxLevel={} (level cap now {}); prestige cap {} (LM)",
                    AdaptiveDifficultyMod.MOD_ID,
                    maxLevel.getInt(levels),
                    overhaulLevelCap(),
                    configuredMaxPrestigeCount());
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
