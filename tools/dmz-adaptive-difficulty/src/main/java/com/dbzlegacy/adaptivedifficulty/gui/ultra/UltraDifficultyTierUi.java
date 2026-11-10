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

    /**
     * Tier {@code t} as a {@link PurchaseState} (4.7.46): ACTIVE, OWNED (free switch down),
     * BUYABLE, SHORT (cannot pay yet) or LOCKED, each with the reason the Tiers page shows. Only
     * tappable states get a button. A LOCKED reason quotes dmzrevamp's effective level cap
     * ({@link RevampEffectiveCaps}) when the tier's level gate is above it.
     */
    public static PurchaseState.View tierView(
            ServerPlayer player, int t, int activeTier, boolean unlocked, boolean eligible) {
        UnlockTier tier = UnlockTier.byId(t);
        if (tier == null) {
            return new PurchaseState.View(PurchaseState.State.UNSOLD, "this tier does not exist");
        }
        boolean usable = unlocked || eligible;
        boolean owned = t == activeTier || (activeTier > 0 && t < activeTier && usable);
        long price = PaidFeatureAccess.bypassAncientCoinCost(player)
                ? 0L
                : AncientCoinEconomy.activationCost(tier, player, activeTier);
        long funds = AncientCoinEconomy.balance(player);
        PurchaseState.State state = PurchaseState.state(
                new PurchaseState.Inputs(owned, t == activeTier, true, false, false, !usable, price, funds));
        String reason = switch (state) {
            case ACTIVE -> "your current tier";
            case OWNED -> "switch down for free";
            case BUYABLE -> price <= 0L ? "activate for free" : "activate for " + AncientCoinEconomy.formatExactCost(price);
            case SHORT -> "need " + AncientCoinEconomy.formatExactCost(price - funds) + " more (costs "
                    + AncientCoinEconomy.formatExactCost(price) + ")";
            case LOCKED -> lockedReason(player, tier);
            default -> "not available";
        };
        return new PurchaseState.View(state, reason);
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
