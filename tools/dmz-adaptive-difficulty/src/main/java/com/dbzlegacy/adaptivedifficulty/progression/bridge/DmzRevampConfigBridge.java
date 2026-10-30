package com.dbzlegacy.adaptivedifficulty.progression.bridge;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import net.minecraftforge.fml.ModList;

/**
 * Reloads Overhaul leveling config on boot so live {@code LevelingRevamp.json} edits apply
 * without hunting down cached defaults. Also pins {@code initialLevelCap} / {@code maxLevel}
 * so prestige 0 is 100k (not stock 50k). {@code maxPrestigeCount} is left as configured: it is
 * the most prestiges a player can hold.
 */
public final class DmzRevampConfigBridge {
    private DmzRevampConfigBridge() {}

    public static void onServerStarting() {
        if (!ModList.get().isLoaded("dmzrevamp") || !DifficultyConfig.get().enablePrestigeSystem) {
            return;
        }
        try {
            Class<?> cfg = Class.forName("com.dmzrevamp.config.LevelingRevampConfig");
            cfg.getMethod("reload").invoke(null);
            LmOverhaulCapMath.pinOverhaulLevelCaps();
            LmOverhaulPrestigeIntegration.logOverhaulPrestigeState();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] dmzrevamp config reload skipped: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }
}
