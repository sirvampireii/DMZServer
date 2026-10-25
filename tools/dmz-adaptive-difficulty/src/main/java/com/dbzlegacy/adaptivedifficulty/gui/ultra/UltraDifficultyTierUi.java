package com.dbzlegacy.adaptivedifficulty.gui.ultra;

import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
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

    public static String tierActionLabel(
            ServerPlayer player, int t, int activeTier, boolean unlocked, boolean eligible) {
        if (t <= 0) {
            return UltraStyle.dim("-");
        }
        if (activeTier == t) {
            return UltraStyle.confirm("T" + t + " on");
        }
        // Downgrade to Tier is free (- Free). Button reads "T# free".
        if (activeTier > 0 && t < activeTier) {
            if (unlocked || eligible) {
                return UltraStyle.confirm("T" + t + " free");
            }
            return UltraStyle.dim("T" + t + " lock");
        }
        if (!unlocked) {
            if (eligible) {
                return UltraStyle.confirm("T" + t + " " + buttonPrice(player, t, activeTier));
            }
            return UltraStyle.dim("T" + t + " lock");
        }
        return UltraStyle.LABEL + "T" + t + " " + buttonPrice(player, t, activeTier);
    }

    /** Coin price that fits beside {@code T#} on a standard button. */
    private static String buttonPrice(ServerPlayer player, int t, int activeTier) {
        if (PaidFeatureAccess.bypassAncientCoinCost(player)) {
            return "free";
        }
        if (activeTier > 0 && t < activeTier) {
            return "free";
        }
        return AncientCoinEconomy.compactCost(
                AncientCoinEconomy.activationCost(UnlockTier.byId(t), player, activeTier));
    }

    public static boolean tierButtonEnabled(int t, int activeTier, boolean unlocked, boolean eligible) {
        if (t <= 0 || t > 7) {
            return false;
        }
        if (activeTier == t) {
            return false;
        }
        // Downgrade to a weaker tier when already eligible/unlocked.
        if (activeTier > 0 && t < activeTier) {
            return unlocked || eligible;
        }
        if (!unlocked) {
            return eligible;
        }
        return true;
    }

    public static boolean isEligible(ServerPlayer player, UnlockTier tier) {
        if (player == null || tier == null) {
            return false;
        }
        return UnlockSystem.isEligible(player, tier);
    }
}
