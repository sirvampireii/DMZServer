package com.dbzlegacy.adaptivedifficulty.tier;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;

/**
 * Difficulty tiers that unlock AI / abilities.
 * Ladder runs from early Awakened up to {@link #ZENITH} at the theoretical max
 * for a maxed player (DMZ level 100000 × 10 prestiges × prestigeMultiplier 10 = 10M).
 */
public enum DifficultyTier {
    NONE(0, "None"),
    AWAKENED(10, "Awakened"),
    ENHANCED(50, "Enhanced"),
    ELITE(100, "Elite"),
    ADVANCED(500, "Advanced"),
    MASTER(1_000, "Master"),
    LEGENDARY(5_000, "Legendary"),
    GOD(10_000, "God"),
    DIVINE(50_000, "Divine"),
    IMPOSSIBLE(100_000, "Impossible"),
    /** Mid prestige — past base Impossible. */
    TRANSCENDENT(250_000, "Transcendent"),
    ETERNAL(500_000, "Eternal"),
    MYTHIC(1_000_000, "Mythic"),
    OMEGA(2_500_000, "Omega"),
    ABSOLUTE(5_000_000, "Absolute"),
    APEX(7_500_000, "Apex"),
    /** Theoretical max: level 100000 @ 10 prestiges (default multipliers). */
    ZENITH(10_000_000, "Zenith");

    /** Concept / design default threshold (used when config is unavailable). */
    public final long threshold;
    public final String display;

    private static volatile long[] CACHED_THRESHOLDS;

    DifficultyTier(long threshold, String display) {
        this.threshold = threshold;
        this.display = display;
    }

    public static void invalidateThresholdCache() {
        CACHED_THRESHOLDS = null;
    }

    /** Live threshold from config (falls back to design default). */
    public long threshold() {
        long[] cached = CACHED_THRESHOLDS;
        if (cached == null) {
            cached = buildCache();
            CACHED_THRESHOLDS = cached;
        }
        return cached[ordinal()];
    }

    private static long[] buildCache() {
        DifficultyTier[] values = values();
        long[] out = new long[values.length];
        DifficultyConfig cfg = DifficultyConfig.get();
        for (DifficultyTier tier : values) {
            out[tier.ordinal()] = cfg.tierThreshold(tier);
        }
        return out;
    }

    public static DifficultyTier of(long difficulty) {
        DifficultyTier best = NONE;
        for (DifficultyTier tier : values()) {
            if (difficulty >= tier.threshold()) {
                best = tier;
            }
        }
        return best;
    }

    /** Tier from a config name ("MYTHIC", "mythic"); {@code fallback} when blank or unknown. */
    public static DifficultyTier parse(String raw, DifficultyTier fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public int ordinalPower() {
        return ordinal();
    }
}
