package com.dbzlegacy.adaptivedifficulty.calc;

import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import net.minecraft.server.level.ServerPlayer;

/** Unlock tiers, activation ceilings, combat rating (rewards/display). Rival mutual teams. */
public final class DifficultyCalculator {
    private DifficultyCalculator() {}

    public static DifficultySnapshot snapshot(ServerPlayer player, PlayerDifficultyData data) {
        UnlockSystem.syncUnlocks(player, data);

        // Buy GUI reads snap.dmzLevel — tierScalingDmzLevel heals placeholder/stale reads.
        int level = DmzProgression.tierScalingDmzLevel(player, data.getHighestDmzLevel());
        long gate = UnlockSystem.gateLevelForEligibility(player);
        if (level <= 1 && gate > 1L) {
            level = (int) Math.min(Integer.MAX_VALUE, gate);
        }
        int prestige = DmzProgression.prestige(player);
        double transform = DmzProgression.transformationPower(player);
        // Never ratchet highest DMZ level from a form-inflated or login-race reading.
        if (!DmzProgression.isTransformed(player)
                && DmzProgression.hasReliableUnlockGateSample(player)
                && level > 1) {
            data.noteDmzLevel(level);
        }

        int highest = UnlockSystem.highestUnlocked(data);
        int activeTier = data.getActiveTier();
        UnlockTier tier = UnlockTier.byId(activeTier);
        long tierMax = tier == null ? 0L : tier.maxDifficulty();

        // 4.7.78: rival teams are gone; every player scales personally only (no threshold bonus, no contribution).
        long personalMax = tierMax;
        long thresholdBonus = 0L;
        long contribution = 0L;
        long availableMax = clampNonNegative(personalMax + thresholdBonus + contribution);

        long active = Math.min(data.getActiveDifficultyLevel(), availableMax);
        if (tier == null) {
            active = 0L;
        }
        if (active != data.getActiveDifficultyLevel()) {
            data.setActiveDifficultyLevel(active);
        }
        // Repair unlock-list desync without granting free tiers.
        // Only restore / revoke when the unlock gate sample is reliable.
        boolean gateReliable = DmzProgression.hasReliableUnlockGateSample(player);
        if (activeTier > 0 && !data.hasUnlockedTier(activeTier)) {
            if (tier != null && UnlockSystem.isEligible(player, tier)) {
                data.unlockTier(activeTier);
            } else if (gateReliable || tier == null) {
                data.resetTemporary();
                activeTier = 0;
                active = 0L;
                availableMax = 0L;
                personalMax = 0L;
                thresholdBonus = 0L;
                contribution = 0L;
                tier = null;
                tierMax = 0L;
            }
        } else if (gateReliable && activeTier > 0 && tier != null && !UnlockSystem.isEligible(player, tier)) {
            // Prestige/level reset while still carrying an active high tier.
            data.revokeTier(activeTier);
            data.resetTemporary();
            activeTier = 0;
            active = 0L;
            availableMax = 0L;
            personalMax = 0L;
            thresholdBonus = 0L;
            contribution = 0L;
            tier = null;
            tierMax = 0L;
        }
        highest = UnlockSystem.highestUnlocked(data);

        long combatRating = CombatRating.compute(level, prestige, active, transform, DifficultyConfig.get());
        long ancientCopper = AncientCoinEconomy.balance(player);

        return new DifficultySnapshot(
                level,
                prestige,
                transform,
                highest,
                activeTier,
                active,
                tierMax,
                availableMax,
                personalMax,
                thresholdBonus,
                contribution,
                combatRating,
                ancientCopper,
                data.isPersonalEnabled()
        );
    }

    private static long clampNonNegative(long value) {
        if (value < 0L) {
            return 0L;
        }
        long cap = DifficultyConfig.get().hardCapDifficulty;
        if (cap > 0L) {
            return Math.min(value, cap);
        }
        return value;
    }
}
