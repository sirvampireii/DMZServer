package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Popo's Time Chamber training-wave TP multiplier (4.7.24, config {@code chamberWaveTpMultiplier},
 * default 0.1). Applied to Ultra's per-foe wave TP before the player's own TP multipliers; Whis's
 * God Trial and every other TP path are untouched. A scaled amount never drops below 1 TP per foe.
 * Called from the dmzultra chamber mixins.
 */
public final class ChamberWaveTp {
    /** Player UUID -> whether their current (latest started) chamber run is Whis's God Trial. */
    private static final Map<UUID, Boolean> TRIAL_RUN = new ConcurrentHashMap<>();

    private ChamberWaveTp() {}

    public static void recordRun(UUID player, boolean trial) {
        if (player != null) {
            TRIAL_RUN.put(player, trial);
        }
    }

    /** Per-foe wave TP for this player's run: scaled on training runs, unchanged on the trial. */
    public static long scaleWaveTp(UUID player, long perFoe) {
        Boolean trial = player == null ? null : TRIAL_RUN.get(player);
        if (trial == null || trial) {
            return perFoe;
        }
        return scale(perFoe);
    }

    /** Popo's menu preview (TP per foe at each power step), scaled like the payout. */
    public static long[] scalePreview(long[] perFoe) {
        if (perFoe == null) {
            return null;
        }
        long[] out = new long[perFoe.length];
        for (int i = 0; i < perFoe.length; i++) {
            out[i] = scale(perFoe[i]);
        }
        return out;
    }

    static long scale(long perFoe) {
        double mult = multiplier();
        if (mult >= 1.0 || perFoe <= 0L) {
            return perFoe;
        }
        return Math.max(1L, Math.round(perFoe * mult));
    }

    public static double multiplier() {
        double m = DifficultyConfig.get().chamberWaveTpMultiplier;
        return Double.isFinite(m) ? Math.max(0.0, Math.min(1.0, m)) : 0.1;
    }
}
