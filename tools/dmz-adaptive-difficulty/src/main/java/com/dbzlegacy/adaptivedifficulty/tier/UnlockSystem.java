package com.dbzlegacy.adaptivedifficulty.tier;

import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.calc.DmzProgression;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * V3 unlock logic: current DMZ level requirement OR prestige ≥ tier id.
 * Prestige bypasses level gates but never activates difficulty.
 * Unlock bits are live-reconciled when a reliable base-form level is known —
 * never revoke from a "level 1" placeholder while transformed with no sample.
 * No free coin bootstrap — coins come from kill drops.
 * <p>
 * Combat Rating / Battle Power does <b>not</b> unlock tiers.
 */
public final class UnlockSystem {
    private UnlockSystem() {}

    public static boolean isEligible(ServerPlayer player, UnlockTier tier) {
        if (player == null || tier == null) {
            return false;
        }
        // 4.8.3: a staff tier grant counts as eligible until staff revoke it.
        try {
            PlayerDifficultyData own = DifficultyCache.data(player);
            if (own != null && own.hasStaffGrantedTier(tier.id)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // Prestige-point permanent unlocks bypass the DMZ level gate.
        try {
            if (com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .hasPurchasedTier(player, tier.id)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        int prestige = DmzProgression.prestige(player);
        if (prestige >= tier.id) {
            return true;
        }
        long level = gateLevelForEligibility(player);
        return level >= tier.requiredDmzLevel();
    }

    /**
     * DMZ level used for unlock eligibility (and Buy GUI "You: DMZ …").
     * Prefer a reliable base-form sample. When unavailable, fall back to the
     * prestige-scoped high-water mark. Returns {@code 0} when no safe level is known.
     */
    public static long gateLevelForEligibility(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        if (DmzProgression.hasReliableUnlockGateSample(player)) {
            return DmzProgression.dmzLevelForUnlockGate(player);
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        return persistedGateLevel(player, data);
    }

    /**
     * Prestige-scoped high-water level for eligibility when the live base-form
     * sample is unavailable. Never uses form-inflated live {@code getLevel()}.
     */
    static long persistedGateLevel(ServerPlayer player, PlayerDifficultyData data) {
        if (player == null || data == null) {
            return 0L;
        }
        int prestige = DmzProgression.prestige(player);
        int seen = data.getLastSeenPrestige();
        // Untrusted pre-tracking high-water while already prestiged — wait for a
        // real base-form sample (first reliable sync clamps it).
        if (seen < 0 && prestige > 0) {
            return 0L;
        }
        if (seen >= 0 && seen != prestige) {
            return 0L;
        }
        long hw = data.getHighestDmzLevel();
        // Placeholder / early-login pollution (highest=1) is not a real gate sample —
        // treat as unknown so the Buy GUI can fall through to live DMZ level.
        if (hw <= 1L) {
            return 0L;
        }
        return hw;
    }

    /**
     * Sync permanent unlocks from current DMZ level + prestige.
     * Grants newly eligible tiers. Revokes only when the unlock gate sample is reliable.
     *
     * @return newly unlocked tier ids
     */
    public static List<Integer> syncUnlocks(ServerPlayer player, PlayerDifficultyData data) {
        List<Integer> newly = new ArrayList<>();
        if (player == null || data == null) {
            return newly;
        }

        int prestige = DmzProgression.prestige(player);
        boolean reliable = DmzProgression.hasReliableUnlockGateSample(player);
        int gateLevel = reliable ? DmzProgression.dmzLevelForUnlockGate(player) : 0;
        int prevPrestige = data.getLastSeenPrestige();

        if (prevPrestige >= 0 && prestige > prevPrestige) {
            // Prestige-up restarts the DMZ level ladder.
            if (reliable) {
                data.resetHighestDmzLevel(gateLevel);
            } else {
                data.resetHighestDmzLevel(0);
            }
            data.setLastSeenPrestige(prestige);
        } else if (reliable && prevPrestige < 0) {
            // First reliable sync — clamp any leftover high-water to this prestige cycle.
            if (prestige > 0) {
                data.resetHighestDmzLevel(gateLevel);
            }
            data.setLastSeenPrestige(prestige);
        } else if (reliable) {
            data.setLastSeenPrestige(prestige);
        } else if (prevPrestige < 0 && prestige <= 0) {
            data.setLastSeenPrestige(0);
        }
        // If prestige > 0, lastSeen < 0, and !reliable: leave lastSeen unset and do NOT
        // wipe highestDmzLevel. Prestige still unlocks T1..TP; level catches up in base form.

        if (reliable && !DmzProgression.isTransformed(player)) {
            data.noteDmzLevel(gateLevel);
        }

        for (UnlockTier tier : UnlockTier.values()) {
            if ((data.hasStaffGrantedTier(tier.id) || isEligible(player, tier)) && data.unlockTier(tier.id)) {
                newly.add(tier.id);
            }
        }

        if (reliable) {
            for (Integer id : new ArrayList<>(data.getUnlockedTiers())) {
                UnlockTier tier = UnlockTier.byId(id);
                if (tier == null || (!data.hasStaffGrantedTier(id) && !isEligible(player, tier))) {
                    data.revokeTier(id);
                }
            }
            int activeId = data.getActiveTier();
            if (activeId > 0 && !data.hasUnlockedTier(activeId)) {
                data.resetTemporary();
            }
        } else {
            for (Integer id : new ArrayList<>(data.getUnlockedTiers())) {
                UnlockTier tier = UnlockTier.byId(id);
                if (tier == null) {
                    data.revokeTier(id);
                }
            }
        }
        // Prestige-point permanent tiers must survive revoke/reconcile.
        try {
            com.dbzlegacy.adaptivedifficulty.progression.shop.PrestigePointsSystem
                    .reapplyTierUnlocks(player);
        } catch (Throwable ignored) {
        }
        return newly;
    }

    /** 4.8.3: staff grant of a tier (kept through every resync); true when the grant is new. */
    public static boolean grantStaffTier(ServerPlayer player, int tierId) {
        PlayerDifficultyData data = player == null ? null : DifficultyCache.data(player);
        if (data == null || UnlockTier.byId(tierId) == null) {
            return false;
        }
        boolean added = data.grantStaffTier(tierId);
        data.unlockTier(tierId);
        syncUnlocks(player, data);
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        return added;
    }

    /**
     * 4.8.3: undo a staff tier grant. Only the grant goes; the tier stays unlocked when the player still qualifies
     * (level, prestige or a Prestige Shop purchase). True when there was a grant.
     */
    public static boolean revokeStaffTier(ServerPlayer player, int tierId) {
        PlayerDifficultyData data = player == null ? null : DifficultyCache.data(player);
        if (data == null || !data.revokeStaffTier(tierId)) {
            return false;
        }
        UnlockTier tier = UnlockTier.byId(tierId);
        if (tier != null && !isEligible(player, tier)) {
            data.revokeTier(tierId);
            if (data.getActiveTier() == tierId) {
                data.resetTemporary();
            }
        }
        syncUnlocks(player, data);
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        return true;
    }

    public static int highestUnlocked(PlayerDifficultyData data) {
        if (data == null) {
            return 0;
        }
        int best = 0;
        for (int id : data.getUnlockedTiers()) {
            best = Math.max(best, id);
        }
        return best;
    }

    public static long maxDifficultyFor(PlayerDifficultyData data) {
        if (data == null) {
            return 0L;
        }
        UnlockTier tier = UnlockTier.byId(data.getActiveTier());
        return tier == null ? 0L : tier.maxDifficulty();
    }
}
