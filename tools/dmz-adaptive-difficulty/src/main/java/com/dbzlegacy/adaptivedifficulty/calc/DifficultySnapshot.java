package com.dbzlegacy.adaptivedifficulty.calc;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;

/** Cached V3 view of a player's difficulty / combat-rating numbers. */
public final class DifficultySnapshot {
    public final int dmzLevel;
    public final int prestige;
    public final double transformationPower;
    public final int highestUnlockedTier;
    public final int activeTier;
    public final long activeDifficulty;
    /** Compatibility alias for older GUI/PAPI bindings (`snap.active`). */
    public final long active;
    public final long tierMaxDifficulty;
    public final long availableMax;
    public final long personalMax;
    public final long teamThresholdBonus;
    public final long teamContribution;
    public final long combatRating;
    public final long ancientCopper;
    public final String activeTierName;
    /** Player personal difficulty toggle — when false, scaling/rewards are paused. */
    public final boolean personalEnabled;

    // Legacy-compatible aliases used by older GUI/PAPI bindings.
    public final long calculated;
    public final long purchased;

    public DifficultySnapshot(
            int dmzLevel,
            int prestige,
            double transformationPower,
            int highestUnlockedTier,
            int activeTier,
            long activeDifficulty,
            long tierMaxDifficulty,
            long availableMax,
            long personalMax,
            long teamThresholdBonus,
            long teamContribution,
            long combatRating,
            long ancientCopper,
            boolean personalEnabled
    ) {
        this.dmzLevel = dmzLevel;
        this.prestige = prestige;
        this.transformationPower = transformationPower;
        this.highestUnlockedTier = highestUnlockedTier;
        this.activeTier = activeTier;
        this.activeDifficulty = activeDifficulty;
        this.active = activeDifficulty;
        this.tierMaxDifficulty = tierMaxDifficulty;
        this.availableMax = availableMax;
        this.personalMax = personalMax;
        this.teamThresholdBonus = teamThresholdBonus;
        this.teamContribution = teamContribution;
        this.combatRating = combatRating;
        this.ancientCopper = ancientCopper;
        this.personalEnabled = personalEnabled;
        UnlockTier tier = UnlockTier.byId(activeTier);
        this.activeTierName = tier == null ? "None" : ("T" + tier.id + " " + tier.display);
        this.calculated = combatRating;
        this.purchased = ancientCopper;
    }

    public String stateColorCode() {
        return switch (state()) {
            case "Off" -> "c";
            case "Inactive" -> "7";
            case "Below" -> "a";
            case "Balanced" -> "e";
            case "Tier Max" -> "6";
            case "Team Boosted" -> "d";
            case "Extreme" -> "c";
            default -> "f";
        };
    }

    public String state() {
        if (!personalEnabled) {
            return "Off";
        }
        if (activeTier <= 0 || activeDifficulty <= 0) {
            return "Inactive";
        }
        long hardCap = Math.max(0L, DifficultyConfig.get().hardCapDifficulty);
        boolean atHardCap = hardCap > 0 && activeDifficulty >= hardCap;
        boolean fullTeamCeiling = availableMax > personalMax && activeDifficulty >= availableMax;
        if (atHardCap || fullTeamCeiling) {
            return "Extreme";
        }
        if (activeDifficulty > personalMax) {
            return "Team Boosted";
        }
        if (activeDifficulty >= personalMax && personalMax > 0) {
            return "Tier Max";
        }
        return "Balanced";
    }
}
