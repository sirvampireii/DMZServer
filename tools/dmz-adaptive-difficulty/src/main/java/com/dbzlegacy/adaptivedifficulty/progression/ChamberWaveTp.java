package com.dbzlegacy.adaptivedifficulty.progression;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;

/**
 * Chamber-wave TP multiplier (config {@code chamberWaveTpMultiplier}, default 0.1). Applied to
 * Ultra's per-foe wave TP before the player's own TP multipliers, on every chamber run: Popo's Time
 * Chamber training waves (4.7.24) and Whis's God Trial (4.7.26). Every other TP path is untouched.
 * A scaled amount never drops below 1 TP per foe. Called from the dmzultra chamber mixins.
 */
public final class ChamberWaveTp {
    private ChamberWaveTp() {}

    /** Per-foe wave TP for a chamber run (training or trial), scaled by the multiplier. */
    public static long scaleWaveTp(long perFoe) {
        return scale(perFoe);
    }

    /** Popo's and Whis's menu preview (TP per foe at each power step), scaled like the payout. */
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
