package com.dbzlegacy.adaptivedifficulty.progression.bridge;

import com.dragonminez.common.stats.StatsData;

/**
 * The caps that actually win on this server, read from dmzrevamp's {@code PrestigeSystem}: its
 * HEAD cancellers (priority 1000) answer every stat-cap read before LM's (priority 5000), so
 * menus that show a cap must quote these, not {@code LmOverhaulCapMath}. Reading through
 * revamp's public methods returns whatever the running game answers. Empty when dmzrevamp is
 * absent or the read fails.
 */
public final class RevampEffectiveCaps {
    private static final String PRESTIGE_SYSTEM = "com.dmzrevamp.revamp.prestige.PrestigeSystem";

    private RevampEffectiveCaps() {}

    /** {@code PrestigeSystem.configuredLevelMaximum()} (150,000 as live is configured), or -1. */
    public static int levelMaximum() {
        try {
            Object v = Class.forName(PRESTIGE_SYSTEM).getMethod("configuredLevelMaximum").invoke(null);
            return ((Number) v).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** {@code PrestigeSystem.maxAssignableTotal(data)}, or -1. */
    public static int maxAssignableTotal(StatsData data) {
        if (data == null) {
            return -1;
        }
        try {
            Object v = Class.forName(PRESTIGE_SYSTEM).getMethod("maxAssignableTotal", StatsData.class).invoke(null, data);
            return ((Number) v).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }
}
