package com.dbzlegacy.adaptivedifficulty.progression.bridge;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulCapMath;
import com.dbzlegacy.adaptivedifficulty.progression.LmOverhaulPrestigeIntegration;
import net.minecraftforge.fml.ModList;

/**
 * Reloads Overhaul leveling config on boot so live {@code LevelingRevamp.json} edits apply
 * without hunting down cached defaults, and pins {@code levelsAndAttributes.maxLevel} to 150k, the
 * level cap. dmzrevamp 2.2.4 has no {@code initialLevelCap} or {@code maxPrestigeCount}; the cap of
 * 20 prestiges is LM's own ({@code DmzRevampPrestigeCapMixin}, 4.7.14).
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
            com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraScreenSelfTest.checkOverhaulFlatCapAndCount();
            LmOverhaulPrestigeIntegration.logOverhaulPrestigeState();
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] dmzrevamp config reload skipped: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
        }
    }
}
