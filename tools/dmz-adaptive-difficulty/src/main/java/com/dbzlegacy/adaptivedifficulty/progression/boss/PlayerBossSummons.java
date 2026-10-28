package com.dbzlegacy.adaptivedifficulty.progression.boss;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.currency.AncientCoinEconomy;
import com.dbzlegacy.adaptivedifficulty.gui.ultra.UltraStyle;
import com.dbzlegacy.adaptivedifficulty.scaling.MobScaling;
import com.dbzlegacy.adaptivedifficulty.telemetry.SystemTelemetry;
import com.dbzlegacy.adaptivedifficulty.util.DmzRewards;
import com.dbzlegacy.adaptivedifficulty.util.PaidFeatureAccess;
import com.dbzlegacy.adaptivedifficulty.util.PersistentDataAccess;
import com.dbzlegacy.adaptivedifficulty.util.SystemGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Difficulty GUI paid Wither and Warden summons, built the same way as the End Dragon summon
 * ({@code EndDimensionStrength.cmdPlayerSummon}): gates in order (enabled, personal AD ON,
 * active tier in range, dimension, one living boss of that kind per summoner), Ancient
 * Netherite charge, spawn near the summoner, ownership stamp, the summoner's AD boss profile,
 * and a damage lock so only the summoner can hurt it. Vanilla Wither building and Warden
 * shrieker spawns are untouched - these are extra, paid summons.
 */
public final class PlayerBossSummons {
    private PlayerBossSummons() {}

    /** The summonable bosses. Each keeps its own ownership stamp. */
    public enum Boss {
        WITHER("Wither", 3, 7, Level.f_46429_, "the Nether", "lm_wither_summoner"),
        WARDEN("Warden", 4, 7, Level.f_46428_, "the Overworld", "lm_warden_summoner");

        public final String display;
        public final int minTier;
        public final int maxTier;
        final ResourceKey<Level> dimension;
        public final String dimensionName;
        final String nbtSummoner;

        Boss(String display, int minTier, int maxTier, ResourceKey<Level> dimension,
                String dimensionName, String nbtSummoner) {
            this.display = display;
            this.minTier = minTier;
            this.maxTier = maxTier;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.nbtSummoner = nbtSummoner;
        }

        public boolean enabled() {
            DifficultyConfig cfg = DifficultyConfig.get();
            return this == WITHER ? cfg.enableWitherSummon : cfg.enableWardenSummon;
        }

        /** Ancient Netherite coins this summon costs (config, at least 1). */
        public int netheriteCoins() {
            DifficultyConfig cfg = DifficultyConfig.get();
            return Math.max(1, this == WITHER ? cfg.witherSummonNetheriteCost : cfg.wardenSummonNetheriteCost);
        }

        /** Copper value of {@link #netheriteCoins} Ancient Netherite coins. */
        public long copperCost() {
            return AncientCoinEconomy.CoinKind.NETHERITE.copperValue * (long) netheriteCoins();
        }

        EntityType<? extends Mob> entityType() {
            return this == WITHER ? EntityType.f_20496_ : EntityType.f_217015_;
        }
    }

    /** True when the player stands in this boss's dimension (same key check as the End gate). */
    public static boolean inDimension(ServerPlayer player, Boss boss) {
        return player != null && boss != null && player.m_9236_().m_46472_() == boss.dimension;
    }

    /** True when enable/personal/tier gates pass - location is checked on confirm, as for the dragon. */
    public static boolean canOpenSummonMenu(ServerPlayer player, Boss boss) {
        if (player == null || boss == null || !DifficultyConfig.get().enabled || !boss.enabled()) {
            return false;
        }
        if (!SystemGate.participates(player)) {
            return false;
        }
        int tier = DifficultyCache.data(player).getActiveTier();
        return tier >= boss.minTier && tier <= boss.maxTier;
    }

    /** Cost text for the GUI: the exact Ancient Coin amount, or free for staff with the bypass. */
    public static String costText(ServerPlayer player, Boss boss) {
        if (player != null && PaidFeatureAccess.bypassAncientCoinCost(player)) {
            return "Free for staff";
        }
        return AncientCoinEconomy.formatExactCost(boss.copperCost());
    }

    /**
     * Run the paid summon. Gates are checked in order and nothing is charged until all pass.
     *
     * @return a message for the menu; blank when the summon already messaged the player
     */
    public static SummonOutcome summonOutcome(ServerPlayer player, Boss boss) {
        if (player == null || boss == null) {
            return SummonOutcome.fail(UltraStyle.dangerLine("This only works in-game."));
        }
        if (!DifficultyConfig.get().enabled) {
            return SummonOutcome.fail(UltraStyle.dangerLine("Adaptive Difficulty is turned off. Ask staff to turn it on."));
        }
        if (!boss.enabled()) {
            return SummonOutcome.fail(UltraStyle.dangerLine(boss.display + " summoning is turned off. Ask staff to turn it on."));
        }
        if (!SystemGate.participates(player)) {
            return SummonOutcome.fail(UltraStyle.dangerLine("Turn personal Adaptive Difficulty ON to summon the " + boss.display + "."));
        }
        int tier = DifficultyCache.data(player).getActiveTier();
        if (tier < boss.minTier || tier > boss.maxTier) {
            return SummonOutcome.fail(UltraStyle.dangerLine("You need an active difficulty tier from T" + boss.minTier + " to T"
                    + boss.maxTier + " (yours: T" + tier + "). A higher tier unlocks this boss - unlock and"
                    + " activate one on the Tiers tab first."));
        }
        if (!inDimension(player, boss)) {
            return SummonOutcome.fail(UltraStyle.dangerLine("You must be in " + boss.dimensionName + " to summon the "
                    + boss.display + ". Travel there, then confirm again."));
        }
        LivingEntity owned = findOwned(player, boss);
        if (owned != null) {
            return SummonOutcome.fail(UltraStyle.dangerLine("Your " + boss.display + " is already alive (HP "
                    + DmzRewards.formatWhole(owned.m_21223_()) + "). Defeat it before summoning another."));
        }
        long cost = boss.copperCost();
        boolean free = PaidFeatureAccess.bypassAncientCoinCost(player);
        long charge = free ? 0L : cost;
        String costText = free ? "free (staff)" : AncientCoinEconomy.formatExactCost(cost);
        if (charge > 0L && !AncientCoinEconomy.canAfford(player, charge)) {
            return SummonOutcome.fail(AncientCoinEconomy.missingText(player, charge));
        }
        if (charge > 0L && !AncientCoinEconomy.charge(player, charge)) {
            return SummonOutcome.fail(AncientCoinEconomy.missingText(player, charge));
        }
        LivingEntity spawned = spawnNear(player, boss);
        if (spawned == null) {
            // Refund exactly what was charged - nothing when the summon was free.
            if (charge > 0L) {
                AncientCoinEconomy.grantExact(player, AncientCoinEconomy.CoinKind.NETHERITE, boss.netheriteCoins());
            }
            return SummonOutcome.fail(UltraStyle.dangerLine("Could not find room for the " + boss.display
                    + " - try again in open space nearby. Your coins were returned."));
        }
        double hp = MobScaling.applySummonedBossAdProfile(spawned, player);
        if (spawned instanceof Warden warden) {
            // Point the Warden at its summoner so it fights instead of sniffing around and digging away.
            try {
                warden.m_219387_(player, 150, false); // increaseAggressionTowards
            } catch (Throwable ignored) {
            }
        }
        // Menu-first: the confirmation paints on the summon page, not in chat.
        String done = UltraStyle.YELLOW + "Summoned Adaptive " + boss.display + " with "
                + UltraStyle.DANGER + DmzRewards.formatWhole(hp > 0 ? hp : spawned.m_21233_())
                + UltraStyle.YELLOW + " HP (T" + tier + " AD)."
                + "\n" + UltraStyle.LABEL + "Cost " + UltraStyle.VALUE + costText
                + UltraStyle.LABEL + " - only you can damage this " + boss.display + "."
                + "\n" + UltraStyle.YELLOW + SummonLootBonus.describe(tier, boss.minTier);
        SystemTelemetry.log("difficulty", "boss_summon", player, null, Map.of(
                "boss", boss.name().toLowerCase(java.util.Locale.ROOT),
                "tier", tier,
                "cost", charge));
        return SummonOutcome.ok(done);
    }

    /** Legacy string form: blank on success (the outcome text is for the menu). */
    public static String summon(ServerPlayer player, Boss boss) {
        SummonOutcome out = summonOutcome(player, boss);
        return out.ok() ? "" : out.text();
    }

    /** Result of a paid summon: success with its confirmation, or the reason it did not happen. */
    public record SummonOutcome(boolean ok, String text) {
        public static SummonOutcome ok(String text) {
            return new SummonOutcome(true, text == null ? "" : text);
        }

        public static SummonOutcome fail(String text) {
            return new SummonOutcome(false, text == null ? "" : text);
        }
    }

    /** Status lines for the confirm page, in the dragon confirm page's pattern. */
    public static List<String> statusLines(ServerPlayer player, Boss boss, boolean ready) {
        List<String> lines = new ArrayList<>();
        if (!DifficultyConfig.get().enabled) {
            lines.add(UltraStyle.LABEL + "Adaptive Difficulty is turned off. Ask staff to turn it on.");
            return lines;
        }
        if (!boss.enabled()) {
            lines.add(UltraStyle.LABEL + boss.display + " summoning is turned off. Ask staff to turn it on.");
            return lines;
        }
        lines.add(UltraStyle.LABEL + "The " + boss.display + " matches your difficulty. Only you can damage it.");
        lines.add(UltraStyle.LABEL + "You need a tier from " + UltraStyle.VALUE + "T" + boss.minTier
                + UltraStyle.LABEL + " through " + UltraStyle.VALUE + "T" + boss.maxTier
                + UltraStyle.LABEL + ", personal difficulty ON, and you must be in " + boss.dimensionName + ".");
        lines.add(UltraStyle.LABEL + "One living " + boss.display + " at a time per player.");
        int myTier = DifficultyCache.data(player).getActiveTier();
        if (myTier >= boss.minTier && myTier <= boss.maxTier) {
            lines.add(UltraStyle.VALUE + SummonLootBonus.describe(myTier, boss.minTier));
        } else {
            lines.add(UltraStyle.LABEL + "Higher tiers drop more: +"
                    + Math.max(0, DifficultyConfig.get().summonLootBonusPerTierPercent)
                    + "% chance per kill to double every drop, per tier above T" + boss.minTier + ".");
        }
        if (!ready) {
            if (!SystemGate.participates(player)) {
                lines.add(UltraStyle.LABEL + "Turn on personal difficulty on the Tiers tab first.");
            } else {
                lines.add(UltraStyle.LABEL + "Activate a tier in that range on the Tiers tab first.");
            }
        } else if (!inDimension(player, boss)) {
            lines.add(UltraStyle.LABEL + "Travel to " + boss.dimensionName + ", then confirm the summon.");
        } else {
            LivingEntity owned = findOwned(player, boss);
            if (owned != null) {
                lines.add(UltraStyle.LABEL + "You already have a living " + boss.display + " (HP "
                        + DmzRewards.formatWhole(owned.m_21223_()) + ").");
            }
        }
        return lines;
    }

    /**
     * Only the summoner may damage their summoned boss; their hits re-apply their current AD
     * profile (as the dragon does). Damage from anything that is not a player is left alone.
     */
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event == null ? null : event.getEntity();
        if (target == null || target.m_9236_().f_46443_) {
            return;
        }
        Boss boss = stampedBoss(target);
        if (boss == null) {
            return;
        }
        ServerPlayer attacker = playerAttacker(event);
        if (attacker == null) {
            return;
        }
        if (!isSummoner(target, boss, attacker)) {
            event.setCanceled(true);
            event.setAmount(0.0f);
            try {
                attacker.m_213846_(Component.m_237113_(
                        UltraStyle.DANGER + "[Difficulty] " + UltraStyle.LABEL
                                + "Only the summoner can damage this " + boss.display + "."));
            } catch (Throwable ignored) {
            }
            return;
        }
        MobScaling.applySummonedBossAdProfile(target, attacker);
    }

    /**
     * Death drops of a paid Wither / Warden summon (LOWEST priority, after every mod added its
     * drops): tier bonus copies. Natural Withers and shrieker Wardens carry no stamp and are untouched.
     */
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event == null || event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead == null || dead.m_9236_().f_46443_) {
            return;
        }
        Boss boss = stampedBoss(dead);
        if (boss == null) {
            return;
        }
        int added = SummonLootBonus.applyToDrops(dead, event.getDrops(), boss.minTier);
        if (added > 0) {
            AdaptiveDifficultyMod.LOGGER.info("[{}] {} summon loot bonus: +{} stack(s) (T{})",
                    AdaptiveDifficultyMod.MOD_ID, boss.display, added, SummonLootBonus.stampedTier(dead));
        }
    }

    /** The boss kind this entity was summoned as, or null for any other entity. */
    private static Boss stampedBoss(LivingEntity entity) {
        CompoundTag tag = PersistentDataAccess.get(entity);
        for (Boss boss : Boss.values()) {
            String id = tag.m_128461_(boss.nbtSummoner);
            if (id != null && !id.isBlank()) {
                return boss;
            }
        }
        return null;
    }

    private static boolean isSummoner(LivingEntity entity, Boss boss, ServerPlayer player) {
        String id = PersistentDataAccess.getString(entity, boss.nbtSummoner);
        return id != null && !id.isBlank() && id.equalsIgnoreCase(player.m_20148_().toString());
    }

    /** The player's living summon of this kind, in any loaded dimension, or null. */
    private static LivingEntity findOwned(ServerPlayer player, Boss boss) {
        MinecraftServer server = player.m_20194_();
        if (server == null) {
            return null;
        }
        try {
            for (ServerLevel level : server.m_129785_()) { // getAllLevels
                for (Entity e : level.m_8583_()) { // getAllEntities
                    if (e instanceof LivingEntity living && living.m_6084_()
                            && living.m_6095_() == boss.entityType()
                            && isSummoner(living, boss, player)) {
                        return living;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Spawn the boss in open space near the summoner. The ownership stamp is written before the
     * entity joins the world, so the nearby scaler treats it as an owned boss from the first tick.
     */
    private static LivingEntity spawnNear(ServerPlayer player, Boss boss) {
        if (!(player.m_9236_() instanceof ServerLevel level)) {
            return null;
        }
        Mob mob;
        try {
            mob = boss.entityType().m_20615_(level); // create
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] {} summon create failed ({})",
                    AdaptiveDifficultyMod.MOD_ID, boss.display, t.getClass().getSimpleName());
            return null;
        }
        if (mob == null) {
            return null;
        }
        double px = player.m_20185_();
        double py = player.m_20186_();
        double pz = player.m_20189_();
        float yaw = player.m_146908_();
        int[][] offsets = {
            {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
            {6, 0}, {-6, 0}, {0, 6}, {0, -6}, {2, 0}, {0, 2}, {-2, 0}, {0, -2}
        };
        boolean placed = false;
        for (int[] off : offsets) {
            for (int dy = 0; dy <= 2 && !placed; dy++) {
                mob.m_7678_(px + off[0], py + dy, pz + off[1], yaw, 0.0f); // moveTo
                if (level.m_45786_(mob)) { // noCollision
                    placed = true;
                }
            }
            if (placed) {
                break;
            }
        }
        if (!placed) {
            return null;
        }
        CompoundTag tag = PersistentDataAccess.get(mob);
        if (!PersistentDataAccess.isWritable(tag)) {
            return null;
        }
        tag.m_128359_(boss.nbtSummoner, player.m_20148_().toString());
        tag.m_128379_(MobScaling.TAG_SUMMONED_BOSS, true);
        SummonLootBonus.stampTier(mob, DifficultyCache.data(player).getActiveTier());
        mob.m_21530_(); // setPersistenceRequired
        try {
            if (!level.m_7967_(mob)) { // addFreshEntity
                return null;
            }
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] {} summon spawn failed ({})",
                    AdaptiveDifficultyMod.MOD_ID, boss.display, t.getClass().getSimpleName());
            return null;
        }
        return mob;
    }

    private static ServerPlayer playerAttacker(LivingHurtEvent event) {
        if (event.getSource() == null) {
            return null;
        }
        Entity src = event.getSource().m_7639_(); // getEntity
        if (src instanceof ServerPlayer sp) {
            return sp;
        }
        if (src instanceof Projectile proj) {
            try {
                Entity owner = proj.m_19749_(); // getOwner
                if (owner instanceof ServerPlayer sp) {
                    return sp;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
