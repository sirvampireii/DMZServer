package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.progression.bridge.RevampEffectiveCaps;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockSystem;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import net.minecraft.server.level.ServerPlayer;

/** Tier button labels and cost copy for Ultra difficulty menus. */
public final class UltraDifficultyTierUi {
    private UltraDifficultyTierUi() {}

    /** Cost label for unlocking/upgrading to {@code tier} (no active-tier context). */
    public static String formatActivationCost(ServerPlayer player, UnlockTier tier) {
        int current = 0;
        try {
            if (player != null) {
                current = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache
                        .data(player)
                        .getActiveTier();
            }
        } catch (Throwable ignored) {
        }
        return formatActivationCost(player, tier, current);
    }

    /**
     * Direction-aware cost label. Downgrades ({@code tier.id < activeTier}) always show free.
     */
    public static String formatActivationCost(ServerPlayer player, UnlockTier tier, int activeTier) {
        if (tier == null) {
            return UltraStyle.LABEL + "Free";
        }
        if (PaidFeatureAccess.bypassAncientCoinCost(player)) {
            return UltraStyle.LABEL + "Free";
        }
        if (activeTier > 0 && tier.id < activeTier) {
            return UltraStyle.LABEL + "Free";
        }
        long cost = AncientCoinEconomy.activationCost(tier, player, activeTier);
        if (cost <= 0L) {
            return UltraStyle.LABEL + "Free";
        }
        return UltraStyle.VALUE + AncientCoinEconomy.formatExactCost(cost);
    }

    public static String humanRequirement(UnlockTier tier) {
        if (tier == null) {
            return "-";
        }
        return tier.requirementTip()
                .replace("DMZ ", "Level ")
                .replace(" or Prestige ", " or prestige ");
    }

    /** Coin price that fits beside {@code T#} on a standard button. */
    private static String buttonPrice(ServerPlayer player, int t, int activeTier) {
        if (PaidFeatureAccess.bypassAncientCoinCost(player)) {
            return "Free";
        }
        if (activeTier > 0 && t < activeTier) {
            return "Free";
        }
        return AncientCoinEconomy.compactCost(
                AncientCoinEconomy.activationCost(UnlockTier.byId(t), player, activeTier));
    }

    /** {@code reach level 5,000 (you: 3,200) or prestige 4}; prestige only when the level is past the cap. */
    static String lockedReason(ServerPlayer player, UnlockTier tier) {
        long need = tier.requiredDmzLevel();
        int cap = RevampEffectiveCaps.levelMaximum();
        if (cap > 0 && need > cap) {
            return "reach prestige " + tier.requiredPrestige() + " - level " + num(need)
                    + " is above this server's level cap of " + num(cap);
        }
        long you = player == null ? 0L : UnlockSystem.gateLevelForEligibility(player);
        return "reach level " + num(need) + " (you: " + num(you) + ") or prestige " + tier.requiredPrestige();
    }

    private static String num(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    public static boolean isEligible(ServerPlayer player, UnlockTier tier) {
        if (player == null || tier == null) {
            return false;
        }
        return UnlockSystem.isEligible(player, tier);
    }
}
