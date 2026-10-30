package com.dbzlegacy.adaptivedifficulty.currency;

import com.dbzlegacy.adaptivedifficulty.AdaptiveDifficultyMod;
import com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache;
import com.dbzlegacy.adaptivedifficulty.config.DifficultyConfig;
import com.dbzlegacy.adaptivedifficulty.data.PlayerDifficultyData;
import com.dbzlegacy.adaptivedifficulty.tier.UnlockTier;
import com.dbzlegacy.adaptivedifficulty.title.TitleEffects;
import com.dbzlegacy.adaptivedifficulty.util.LmLog;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.common.core.ModItems;
import io.github.lightman314.lightmanscurrency.common.items.AncientCoinItem;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import io.github.lightman314.lightmanscurrency.common.items.ancient_coins.AncientCoinType;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * Ancient Coin economy — real Lightman's {@code coin_ancient} items only.
 * <p>
 * Ladder (cheap → expensive): Copper → Iron → Gold → Emerald → Diamond →
 * Netherite (HEROBRINE-H letter only on drops/grants; other letters still count in balance).
 * ancient coins are ignored (never spent, counted, granted, or dropped).
 * <p>
 * Spendable balances come from <b>player inventory + equipped wallet</b>.
 * Lightman's bank accounts are never read or charged.
 * <p>
 * Charges prefer an exact copper total, but players may <b>pay up</b> with any
 * mix of denominations whose total value is ≥ the cost (lower or higher coins).
 * Overpay is returned as Ancient Coin change (largest denominations first).
 */
public final class AncientCoinEconomy {
    private static final boolean LIGHTMANS = ModList.get().isLoaded("lightmanscurrency");
    private static volatile boolean warnedMissingLightmans;
    /** One-shot NBT wallet → inventory migrate per login session. */
    private static final Set<UUID> MIGRATED = ConcurrentHashMap.newKeySet();

    /**
     * Max coins of one denomination for a tier price. Beyond this the cost
     * promotes to the next Ancient Coin type (Copper→…→Netherite), rounding up.
     */
    public static final int MAX_COINS_PER_TYPE = 128;

    /**
     * Netherite ancient coin drop/grant type.
     * <p>
     * Item: {@code lightmanscurrency:coin_ancient}<br>
     * NBT: {@code {CoinType:"NETHERITE_H"}} via {@link AncientCoinType#asItem()}<br>
     * Matches Lightman's {@code misc/ancient_netherite_coins} loot table first entry (HEROBRINE-H).
     */
    private static final AncientCoinType NETHERITE_DROP_TYPE = AncientCoinType.NETHERITE_H;

    public enum CoinKind {
        COPPER(1L, "Copper", AncientCoinType.COPPER),
        IRON(10L, "Iron", AncientCoinType.IRON),
        GOLD(100L, "Gold", AncientCoinType.GOLD),
        EMERALD(1_000L, "Emerald", AncientCoinType.EMERALD),
        DIAMOND(10_000L, "Diamond", AncientCoinType.DIAMOND),
        NETHERITE(100_000L, "Netherite", AncientCoinType.NETHERITE_H);

        public final long copperValue;
        public final String display;
        public final AncientCoinType ancientType;

        CoinKind(long copperValue, String display, AncientCoinType ancientType) {
            this.copperValue = copperValue;
            this.display = display;
            this.ancientType = ancientType;
        }

        /** Payment / balance order, highest value first. */
        static CoinKind[] highToLow() {
            return new CoinKind[] {NETHERITE, DIAMOND, EMERALD, GOLD, IRON, COPPER};
        }

        /** Ladder order, cheapest first. */
        static CoinKind[] lowToHigh() {
            return new CoinKind[] {COPPER, IRON, GOLD, EMERALD, DIAMOND, NETHERITE};
        }

        /** Next higher denomination, or null at Netherite. */
        public CoinKind nextHigher() {
            return switch (this) {
                case COPPER -> IRON;
                case IRON -> GOLD;
                case GOLD -> EMERALD;
                case EMERALD -> DIAMOND;
                case DIAMOND -> NETHERITE;
                case NETHERITE -> null;
            };
        }

        static CoinKind of(AncientCoinType type) {
            if (type == null) {
                return null;
            }
            // Lapis / Ender Pearl ancients are intentionally unused by this mod.
            String name = type.name();
            if ("LAPIS".equals(name) || "ENDER_PEARL".equals(name)) {
                return null;
            }
            if (name != null && name.startsWith("NETHERITE")) {
                return NETHERITE;
            }
            for (CoinKind kind : values()) {
                if (kind.ancientType == type) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** Exact drop: one coin kind + count (no copper-value conversion). */
    public record Drop(CoinKind kind, int count) {
        public Drop {
            kind = kind == null ? CoinKind.COPPER : kind;
            count = Math.max(0, count);
        }

        public long copperValue() {
            return Math.max(0L, kind.copperValue * (long) count);
        }

        public String display() {
            if (count <= 0) {
                return "nothing";
            }
            return count + "× " + kind.display;
        }
    }

    /** Primary kill drop plus optional rare higher-denomination bonus. */
    public record KillLoot(Drop primary, Drop bonus) {
        public KillLoot {
            if (primary == null) {
                primary = new Drop(CoinKind.COPPER, 0);
            }
        }

        public boolean hasPrimary() {
            return primary != null && primary.count() > 0;
        }

        public boolean hasBonus() {
            return bonus != null && bonus.count() > 0;
        }

        public String display() {
            if (!hasPrimary() && !hasBonus()) {
                return "nothing";
            }
            if (!hasBonus()) {
                return primary.display();
            }
            if (!hasPrimary()) {
                return bonus.display();
            }
            return primary.display() + " §8+ §6" + bonus.display();
        }
    }

    private AncientCoinEconomy() {}

    public static boolean realCoinsAvailable() {
        return LIGHTMANS;
    }

    public static long balance(ServerPlayer player) {
        if (player == null) {
            return 0L;
        }
        if (LIGHTMANS) {
            return inventoryCopper(player);
        }
        return DifficultyCache.data(player).getAncientCopper();
    }

    public static String balanceText(ServerPlayer player) {
        return inventoryBreakdown(player);
    }

    /**
     * Per-type Ancient Coin counts in inventory + equipped wallet (not bank).
     * Always lists every Lightman's Ancient type the mod recognizes (incl. 0s).
     */
    public static String inventoryBreakdown(ServerPlayer player) {
        if (player == null || !LIGHTMANS) {
            return format(balance(player));
        }
        long[] counts = countByKind(player);
        StringBuilder sb = new StringBuilder("§f");
        boolean first = true;
        for (CoinKind kind : CoinKind.values()) {
            if (!first) {
                sb.append(" §8· §f");
            }
            first = false;
            sb.append(kind.display).append(' ').append(counts[kind.ordinal()]);
        }
        return sb.toString();
    }

    /** Alias used by some GUI/chat bindings. */
    public static String formatCoins(long copper) {
        return formatExactCost(copper);
    }

    public static long countOf(ServerPlayer player, CoinKind kind) {
        if (player == null || kind == null) {
            return 0L;
        }
        return countByKind(player)[kind.ordinal()];
    }

    public static String format(long copper) {
        if (copper <= 0L) {
            return "0 Copper";
        }
        CoinKind kind = preferredKind(copper);
        long n = copper / kind.copperValue;
        if (copper % kind.copperValue == 0L) {
            return n + "× " + kind.display;
        }
        return copper + "× Copper";
    }

    /**
     * Short price for a menu button, e.g. {@code 128 Netherite} or {@code free}.
     * Stays within 13 glyphs at the 128-coin cap so it can share a button with a short prefix.
     */
    public static String compactCost(long copperCost) {
        if (copperCost <= 0L) {
            return "free";
        }
        long normalized = normalizeCost(copperCost);
        CoinKind kind = preferredKind(normalized);
        long n = normalized / kind.copperValue;
        if (n <= 0L) {
            return "free";
        }
        return n + " " + kind.display;
    }

    /** Format a tier activation cost as an exact coin shopping list. */
    public static String formatExactCost(long copperCost) {
        if (copperCost <= 0L) {
            return "free";
        }
        long normalized = normalizeCost(copperCost);
        CoinKind kind = preferredKind(normalized);
        long n = normalized / kind.copperValue;
        return n + "× " + kind.display + " Ancient";
    }

    /**
     * Snap a copper-value cost so it is payable as ≤{@link #MAX_COINS_PER_TYPE}
     * of a single Ancient Coin type. When more would be required, promote to the
     * next ladder step (rounding up).
     */
    public static long normalizeCost(long copperCost) {
        if (copperCost <= 0L) {
            return 0L;
        }
        long value = copperCost;
        CoinKind[] ladder = CoinKind.lowToHigh();
        for (int i = 0; i < ladder.length; i++) {
            long unit = ladder[i].copperValue;
            long count = (value + unit - 1L) / unit; // ceil
            boolean topRung = i == ladder.length - 1;
            if (count <= MAX_COINS_PER_TYPE || topRung) {
                // No denomination above Netherite — hard-cap at 128× top rung.
                if (topRung) {
                    count = Math.min(count, MAX_COINS_PER_TYPE);
                }
                return safeMul(count, unit);
            }
            // Keep the ceiled copper value while promoting.
            value = safeMul(count, unit);
        }
        return value;
    }

    /** Hard ceiling used by {@link #normalizeCost} (128× Netherite). */
    public static long maxNormalizedCost() {
        return safeMul(MAX_COINS_PER_TYPE, CoinKind.NETHERITE.copperValue);
    }

    /**
     * Smallest normalized cost strictly above {@code copperCost}, or the hard
     * cap when already at the top rung.
     */
    public static long costStrictlyAbove(long copperCost) {
        long current = normalizeCost(Math.max(0L, copperCost));
        long cap = maxNormalizedCost();
        if (current >= cap) {
            return cap;
        }
        CoinKind kind = preferredKind(current);
        long step = Math.max(1L, kind.copperValue);
        long next = normalizeCost(current + step);
        if (next <= current) {
            next = normalizeCost(current + 1L);
        }
        return Math.min(cap, Math.max(current + 1L, next));
    }

    /** Best single denomination for a (preferably normalized) copper total. */
    private static CoinKind preferredKind(long copperCost) {
        if (copperCost <= 0L) {
            return CoinKind.COPPER;
        }
        CoinKind fallback = CoinKind.COPPER;
        for (CoinKind kind : CoinKind.highToLow()) {
            if (copperCost % kind.copperValue != 0L) {
                continue;
            }
            long n = copperCost / kind.copperValue;
            if (n <= 0L) {
                continue;
            }
            fallback = kind;
            if (n <= MAX_COINS_PER_TYPE) {
                return kind;
            }
        }
        return fallback;
    }

    public static boolean canAfford(ServerPlayer player, long copperCost) {
        if (copperCost <= 0L) {
            return true;
        }
        if (player == null) {
            return false;
        }
        // Pay-up allowed: any mix whose copper-value is ≥ cost (change returned).
        return balance(player) >= copperCost;
    }

    /** Human-readable shortfall when {@link #canAfford} is false. */
    public static String missingText(ServerPlayer player, long copperCost) {
        if (copperCost <= 0L) {
            return "free";
        }
        long have = balance(player);
        long missing = Math.max(0L, copperCost - have);
        return "Need " + formatExactCost(copperCost)
                + " — missing " + formatExactCost(missing)
                + " (have " + inventoryBreakdown(player) + ")"
                + " §8· pay-up OK · change returned";
    }

    public static boolean charge(ServerPlayer player, long copperCost) {
        if (copperCost <= 0L) {
            return true;
        }
        if (player == null) {
            return false;
        }
        if (LIGHTMANS) {
            return chargePayment(player, copperCost);
        }
        warnMissingLightmans();
        PlayerDifficultyData data = DifficultyCache.data(player);
        if (!data.spendAncientCopper(copperCost)) {
            return false;
        }
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
        return true;
    }

    /** Grant exact copper-value as Copper ancient coins only (bootstrap / migration). */
    public static void grantCopperExact(ServerPlayer player, long copperCount) {
        if (player == null || copperCount <= 0L) {
            return;
        }
        grantExact(player, CoinKind.COPPER, copperCount);
    }

    /** Put exactly {@code count} coins of {@code kind} into inventory. No splitting / change. */
    public static void grantExact(ServerPlayer player, CoinKind kind, long count) {
        if (player == null || kind == null || count <= 0L) {
            return;
        }
        if (LIGHTMANS) {
            giveStacks(player, kind, count);
            DifficultyCache.refresh(player);
            return;
        }
        warnMissingLightmans();
        PlayerDifficultyData data = DifficultyCache.data(player);
        data.addAncientCopper(safeMul(kind.copperValue, count));
        DifficultyCache.save(player);
        DifficultyCache.refresh(player);
    }

    /** Hard safety cap — kill rolls never exceed this many coins per death. */
    private static final int MAX_KILL_DROP_COUNT = 8;

    /**
     * Spawn exact Ancient Coin item stacks in the world at the killed mob.
     * Does <b>not</b> put coins into the player's inventory.
     * Prefer {@link #dropInWorld(LivingEntity, Drop, UUID)} so only the killer can pick up.
     */
    public static void dropInWorld(LivingEntity at, Drop drop) {
        dropInWorld(at, drop, null);
    }

    /** World drop owned by {@code ownerId} (killer) — others cannot pick up. */
    public static void dropInWorld(LivingEntity at, Drop drop, UUID ownerId) {
        if (at == null || drop == null || drop.count() <= 0 || drop.kind() == null) {
            return;
        }
        if (!LIGHTMANS) {
            warnMissingLightmans();
            return;
        }
        Level level = at.m_9236_();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double x = at.m_20185_();
        double y = at.m_20186_() + 0.35;
        double z = at.m_20189_();
        // Clamp runaway counts — kill rewards are tiny (1–5); never spawn thousands.
        long left = Math.min(MAX_KILL_DROP_COUNT, drop.count());
        while (left > 0L) {
            int chunk = (int) Math.min(64L, left);
            AncientCoinType type = drop.kind() == CoinKind.NETHERITE
                    ? NETHERITE_DROP_TYPE
                    : drop.kind().ancientType;
            ItemStack stack = type.asItem(chunk);
            if (stack == null || stack.m_41619_()) {
                break;
            }
            spawnDropEntity(server, x, y, z, stack, ownerId);
            left -= chunk;
        }
    }

    private static void spawnDropEntity(
            ServerLevel server, double x, double y, double z, ItemStack stack, UUID ownerId
    ) {
        ItemEntity entity = new ItemEntity(server, x, y, z, stack);
        // Short pickup delay so nearby killer can grab it, but still a world drop.
        entity.m_32061_(); // setDefaultPickUpDelay (10 ticks)
        if (ownerId != null) {
            // Only the killer can pick up (Mohist/Forge method name varies).
            try {
                entity.getClass().getMethod("setTarget", UUID.class).invoke(entity, ownerId);
            } catch (Throwable ignored) {
                try {
                    entity.getClass().getMethod("m_261348_", UUID.class).invoke(entity, ownerId);
                } catch (Throwable ignored2) {
                }
            }
        }
        double spread = 0.12;
        entity.m_20334_(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                0.12 + ThreadLocalRandom.current().nextDouble() * 0.08,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * spread
        );
        server.m_7967_(entity); // addFreshEntity
    }

    /**
     * Roll kill coins: stock 5% chance for the primary denomination, of which
     * stock 0.5% is a dual drop (primary + next-higher, e.g. Copper + Iron).
     * Pre-T1 (no active tier) still uses Copper as the primary kind.
     */
    public static KillLoot rollKillLoot(ServerPlayer killer, long combatRating, boolean elite, boolean boss) {
        DifficultyConfig cfg = DifficultyConfig.get();
        if (!cfg.enableAncientCoinDrops || killer == null) {
            return new KillLoot(new Drop(CoinKind.COPPER, 0), null);
        }
        PlayerDifficultyData data = DifficultyCache.data(killer);
        int tier = data.getActiveTier();
        double dropChance = Math.max(0.0, Math.min(1.0, cfg.ancientCoinDropChance));
        dropChance = Math.min(1.0, dropChance + TitleEffects.coinDropChanceBonus(killer));
        if (boss) {
            dropChance = Math.min(1.0, dropChance + TitleEffects.bossCoinChanceBonus(killer));
        }
        if (elite) {
            dropChance = Math.min(1.0, dropChance + TitleEffects.eliteRewardBonus(killer) * 0.5);
        }
        double upgradeChance = Math.max(0.0, Math.min(dropChance, cfg.ancientCoinUpgradeChance));
        double roll = ThreadLocalRandom.current().nextDouble();
        if (dropChance <= 0.0 || roll >= dropChance) {
            return new KillLoot(new Drop(CoinKind.COPPER, 0), null);
        }
        CoinKind kind;
        int count = 1;
        if (tier <= 0) {
            // Starter economy — Copper until a tier is purchased.
            kind = CoinKind.COPPER;
        } else {
            kind = rollKind(tier);
            // Title quality bump: small chance to promote denomination one step.
            double quality = TitleEffects.coinQualityBumpChance(killer, elite, boss, tier);
            if (quality > 0.0 && ThreadLocalRandom.current().nextDouble() < quality) {
                CoinKind up = kind.nextHigher();
                if (up != null) {
                    kind = up;
                }
            }
            if (elite) {
                count += 1;
            }
            if (boss) {
                count += 2;
            }
            // Optional mult: chance for +1 extra of the same kind (never converts denomination).
            double mult = Math.max(0.0, cfg.ancientCoinDropMult);
            if (mult > 1.0 && ThreadLocalRandom.current().nextDouble() < Math.min(0.75, (mult - 1.0) * 0.35)) {
                count += 1;
            }
            if (combatRating > cfg.ancientCoinRatingDivisor
                    && ThreadLocalRandom.current().nextDouble() < 0.15) {
                count += 1;
            }
            double qtyBonus = TitleEffects.coinQuantityBonus(killer);
            if (qtyBonus > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(0.75, qtyBonus * 4.0)) {
                count += 1;
            }
        }
        Drop primary = new Drop(kind, Math.min(MAX_KILL_DROP_COUNT, Math.max(1, count)));
        Drop bonus = null;
        CoinKind upgrade = kind.nextHigher();
        // Dual band: roll in [0, upgradeChance) → primary + next-higher.
        if (upgrade != null && upgradeChance > 0.0 && roll < upgradeChance) {
            bonus = new Drop(upgrade, 1);
        }
        return new KillLoot(primary, bonus);
    }

    public static CoinKind rollKind(int activeTier) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // Ladder: Copper < Iron < Gold < Emerald < Diamond < Netherite
        return switch (Math.max(1, activeTier)) {
            case 1 -> CoinKind.COPPER;
            case 2 -> rng.nextDouble() < 0.25 ? CoinKind.IRON : CoinKind.COPPER;
            case 3 -> rng.nextDouble() < 0.30 ? CoinKind.GOLD : (rng.nextDouble() < 0.55 ? CoinKind.IRON : CoinKind.COPPER);
            case 4 -> rng.nextDouble() < 0.25 ? CoinKind.EMERALD : (rng.nextDouble() < 0.55 ? CoinKind.GOLD : CoinKind.IRON);
            case 5 -> rng.nextDouble() < 0.22 ? CoinKind.DIAMOND : (rng.nextDouble() < 0.55 ? CoinKind.EMERALD : CoinKind.GOLD);
            case 6 -> rng.nextDouble() < 0.18 ? CoinKind.NETHERITE : (rng.nextDouble() < 0.50 ? CoinKind.DIAMOND : CoinKind.EMERALD);
            default -> rng.nextDouble() < 0.35 ? CoinKind.NETHERITE : (rng.nextDouble() < 0.55 ? CoinKind.DIAMOND : CoinKind.EMERALD);
        };
    }

    public static void notifyGrant(ServerPlayer player, Drop drop) {
        if (player == null || drop == null || drop.count() <= 0) {
            return;
        }
        player.m_213846_(Component.m_237113_("§6Dropped " + drop.display() + " §7Ancient Coin"
                + (drop.count() == 1 ? "" : "s")));
    }

    public static void notifyGrant(ServerPlayer player, KillLoot loot) {
        if (player == null || loot == null || (!loot.hasPrimary() && !loot.hasBonus())) {
            return;
        }
        boolean plural = loot.hasBonus() || (loot.hasPrimary() && loot.primary().count() != 1);
        player.m_213846_(Component.m_237113_("§6Dropped " + loot.display() + " §7Ancient Coin"
                + (plural ? "s" : "")));
    }

    public static void notifyGrant(ServerPlayer player, long copper) {
        if (player == null || copper <= 0L) {
            return;
        }
        player.m_213846_(Component.m_237113_("§6+ " + formatExactCost(copper)));
    }

    /**
     * Highest recorded Dragon Mine Z level. Form changes do not rewrite this.
     * Used for level-scaled prices such as saga reset.
     */
    public static int getHighestDmzLevel(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        try {
            long level = com.dbzlegacy.adaptivedifficulty.cache.DifficultyCache
                    .data(player).getHighestDmzLevel();
            if (level <= 0L) {
                return 0;
            }
            if (level > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) level;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static long activationCost(UnlockTier tier) {
        return tier == null ? 0L : normalizeCost(tier.activationCost());
    }

    /**
     * Level-scaled activation cost for {@code tier}. Prefer
     * {@link #activationCost(UnlockTier, ServerPlayer, int)} when the player's current
     * active tier is known — downgrades are always free.
     */
    public static long activationCost(UnlockTier tier, ServerPlayer player) {
        int current = 0;
        if (player != null) {
            try {
                current = DifficultyCache.data(player).getActiveTier();
            } catch (Throwable ignored) {
            }
        }
        return activationCost(tier, player, current);
    }

    /**
     * Direction-aware tier change cost.
     * <ul>
     *   <li>Downgrade ({@code tier.id < currentActiveTier}) → {@code 0} (free)
     *   <li>Same tier / no active tier / upgrade → normal level-scaled cost
     * </ul>
     */
    public static long activationCost(UnlockTier tier, ServerPlayer player, int currentActiveTier) {
        if (tier == null) {
            return 0L;
        }
        // Choosing a weaker tier never costs Ancient Coins.
        if (currentActiveTier > 0 && tier.id < currentActiveTier) {
            return 0L;
        }
        // Form-stable level only — transforming must not change buy prices.
        if (player == null) {
            return normalizeCost(tier.activationCostForLevel(0));
        }
        long fallback = 0L;
        try {
            fallback = DifficultyCache.data(player).getHighestDmzLevel();
        } catch (Throwable ignored) {
        }
        int level = com.dbzlegacy.adaptivedifficulty.calc.DmzProgression
                .tierScalingDmzLevel(player, fallback);
        return normalizeCost(tier.activationCostForLevel(level));
    }

    /**
     * Max copper-value converted from legacy NBT wallet per login.
     * Old {@code purchased} difficulty points were never coin counts — uncapped
     * migration previously dumped tens of thousands of Copper items into the world.
     */
    private static final long MAX_MIGRATE_COPPER = 500_000L; // 5× Netherite value

    /**
     * Convert leftover NBT wallet into mixed Ancient Coin denominations in inventory.
     * Runs at most once per login session. Never world-dumps overflow.
     */
    public static void migrateWalletToItems(ServerPlayer player) {
        if (player == null || !LIGHTMANS) {
            return;
        }
        UUID id = player.m_20148_();
        if (!MIGRATED.add(id)) {
            return;
        }
        PlayerDifficultyData data = DifficultyCache.data(player);
        long wallet = data.getAncientCopper();
        if (wallet <= 0L) {
            return;
        }
        long toGrant = Math.min(wallet, MAX_MIGRATE_COPPER);
        long leftover = wallet - toGrant;
        data.setAncientCopper(leftover);
        DifficultyCache.save(player);
        long placed = grantMixedCopperValueInventoryOnly(player, toGrant);
        long unplaced = Math.max(0L, toGrant - placed);
        if (unplaced > 0L) {
            // Inventory full — keep remainder in NBT instead of littering the world.
            data.setAncientCopper(data.getAncientCopper() + unplaced);
            DifficultyCache.save(player);
        }
        LmLog.event(
                "[{}] migrated wallet {} → {} copper-value Ancient coins for {} (leftover NBT={})",
                AdaptiveDifficultyMod.MOD_ID,
                wallet,
                placed,
                id,
                data.getAncientCopper()
        );
    }

    /**
     * Greedy high→low denomination grant into inventory only.
     * @return copper-value successfully placed
     */
    private static long grantMixedCopperValueInventoryOnly(ServerPlayer player, long copperValue) {
        if (player == null || copperValue <= 0L) {
            return 0L;
        }
        long remaining = copperValue;
        long placed = 0L;
        for (CoinKind kind : CoinKind.highToLow()) {
            if (remaining < kind.copperValue) {
                continue;
            }
            long count = remaining / kind.copperValue;
            long given = giveStacksInventoryOnly(player, kind, count);
            long value = safeMul(kind.copperValue, given);
            remaining -= value;
            placed += value;
            if (remaining <= 0L) {
                break;
            }
        }
        return placed;
    }

    /** @return number of coins actually placed in inventory */
    private static long giveStacksInventoryOnly(ServerPlayer player, CoinKind kind, long count) {
        if (player == null || kind == null || count <= 0L) {
            return 0L;
        }
        long left = count;
        long given = 0L;
        while (left > 0L) {
            int chunk = (int) Math.min(64L, left);
            AncientCoinType type = kind == CoinKind.NETHERITE ? NETHERITE_DROP_TYPE : kind.ancientType;
            ItemStack stack = type.asItem(chunk);
            if (stack == null || stack.m_41619_()) {
                break;
            }
            ItemStack copy = stack.m_41777_();
            int before = copy.m_41613_();
            boolean fully = player.m_150109_().m_36054_(copy);
            // Inventory.add may insert a partial stack and return false — credit that amount
            // or migrateWalletToItems will restore too much NBT and dupe coins on relog.
            int placed = Math.max(0, before - copy.m_41613_());
            if (placed > 0) {
                given += placed;
                left -= placed;
            }
            if (!fully) {
                break;
            }
        }
        if (given > 0L) {
            DifficultyCache.refresh(player);
        }
        return given;
    }

    public static void clearMigrateFlag(UUID playerId) {
        if (playerId != null) {
            MIGRATED.remove(playerId);
        }
    }

    // ── Inventory + equipped-wallet helpers (never bank) ───────────────────

    private static long inventoryCopper(ServerPlayer player) {
        return totalCopperValue(countByKind(player));
    }

    /** Combined counts: player inventory first, then equipped Lightman's wallet. */
    private static long[] countByKind(ServerPlayer player) {
        long[] counts = new long[CoinKind.values().length];
        if (player == null) {
            return counts;
        }
        addCountsFromInventory(counts, player);
        addCountsFromWallet(counts, player);
        return counts;
    }

    private static void addCountsFromInventory(long[] counts, ServerPlayer player) {
        Inventory inv = player.m_150109_();
        for (int i = 0; i < inv.m_6643_(); i++) {
            ItemStack stack = inv.m_8020_(i);
            CoinKind kind = kindOf(stack);
            if (kind == null) {
                continue;
            }
            counts[kind.ordinal()] = safeAdd(counts[kind.ordinal()], stack.m_41613_());
        }
    }

    private static void addCountsFromWallet(long[] counts, ServerPlayer player) {
        Container walletInv = equippedWalletContents(player);
        if (walletInv == null) {
            return;
        }
        for (int i = 0; i < walletInv.m_6643_(); i++) {
            ItemStack stack = walletInv.m_8020_(i);
            CoinKind kind = kindOf(stack);
            if (kind == null) {
                continue;
            }
            counts[kind.ordinal()] = safeAdd(counts[kind.ordinal()], stack.m_41613_());
        }
    }

    /**
     * Equipped Lightman's wallet contents, or null.
     * Never opens bank storage — only the wallet item's internal coin slots.
     */
    private static Container equippedWalletContents(ServerPlayer player) {
        ItemStack wallet = equippedWalletStack(player);
        if (wallet == null || wallet.m_41619_() || !WalletItem.isWallet(wallet)) {
            return null;
        }
        try {
            return WalletItem.getWalletInventory(wallet);
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] wallet inventory read failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return null;
        }
    }

    private static ItemStack equippedWalletStack(ServerPlayer player) {
        if (player == null || !LIGHTMANS) {
            return ItemStack.f_41583_;
        }
        try {
            CoinAPI api = CoinAPI.getApi();
            if (api == null) {
                return ItemStack.f_41583_;
            }
            ItemStack wallet = api.getEquippedWallet(player);
            return wallet == null ? ItemStack.f_41583_ : wallet;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.debug(
                    "[{}] equipped wallet resolve failed: {}", AdaptiveDifficultyMod.MOD_ID, t.toString());
            return ItemStack.f_41583_;
        }
    }

    /**
     * Prefer exact payment; otherwise pay-up (value ≥ cost, change returned later).
     * Returns take-counts per CoinKind ordinal, or null if unaffordable.
     */
    private static long[] planPayment(long[] available, long copperCost) {
        if (copperCost <= 0L) {
            return new long[CoinKind.values().length];
        }
        long[] exact = planExactPayment(available, copperCost);
        if (exact != null) {
            return exact;
        }
        return planPayUpPayment(available, copperCost);
    }

    /**
     * Plan an exact payment (no overpay). Returns take-counts per CoinKind ordinal, or null.
     */
    private static long[] planExactPayment(long[] available, long copperCost) {
        if (copperCost <= 0L) {
            return new long[CoinKind.values().length];
        }
        long[] plan = new long[CoinKind.values().length];
        long remaining = copperCost;
        // Largest first that still fits exactly into the remainder.
        for (CoinKind kind : CoinKind.highToLow()) {
            long unit = kind.copperValue;
            if (unit <= 0L || remaining < unit) {
                continue;
            }
            long canTake = Math.min(available[kind.ordinal()], remaining / unit);
            plan[kind.ordinal()] = canTake;
            remaining -= canTake * unit;
        }
        return remaining == 0L ? plan : null;
    }

    /**
     * Pay-up plan: spend largest coins first until copper-value ≥ cost.
     * Overpay is returned as change by {@link #grantChange}.
     * Example: cost of 1× Iron (10) paid with 1× Gold → take Gold, refund 90 copper-value.
     */
    private static long[] planPayUpPayment(long[] available, long copperCost) {
        if (copperCost <= 0L) {
            return new long[CoinKind.values().length];
        }
        if (available == null || totalCopperValue(available) < copperCost) {
            return null;
        }
        long[] plan = new long[CoinKind.values().length];
        long[] left = java.util.Arrays.copyOf(available, available.length);
        long paid = 0L;
        for (CoinKind kind : CoinKind.highToLow()) {
            int idx = kind.ordinal();
            long unit = kind.copperValue;
            if (unit <= 0L) {
                continue;
            }
            while (paid < copperCost && left[idx] > 0L) {
                plan[idx]++;
                left[idx]--;
                paid = safeAdd(paid, unit);
            }
            if (paid >= copperCost) {
                return plan;
            }
        }
        return paid >= copperCost ? plan : null;
    }

    /** Return {@code copperValue} as Ancient Coins (largest denominations first). */
    private static void grantChange(ServerPlayer player, long copperValue) {
        if (player == null || copperValue <= 0L) {
            return;
        }
        long left = copperValue;
        for (CoinKind kind : CoinKind.highToLow()) {
            long unit = kind.copperValue;
            if (unit <= 0L || left < unit) {
                continue;
            }
            long count = left / unit;
            if (count <= 0L) {
                continue;
            }
            grantExact(player, kind, count);
            left -= count * unit;
        }
        if (left > 0L) {
            grantExact(player, CoinKind.COPPER, left);
        }
    }

    private static long totalCopperValue(long[] counts) {
        if (counts == null) {
            return 0L;
        }
        long total = 0L;
        for (CoinKind kind : CoinKind.values()) {
            int idx = kind.ordinal();
            if (idx < 0 || idx >= counts.length || counts[idx] <= 0L) {
                continue;
            }
            total = safeAdd(total, safeMul(kind.copperValue, counts[idx]));
        }
        return total;
    }

    /**
     * Remove coins per {@link #planPayment} — inventory first, then equipped wallet.
     * Never touches Lightman's bank accounts.
     */
    private static boolean chargePayment(ServerPlayer player, long copperCost) {
        long[] invCounts = new long[CoinKind.values().length];
        long[] walletCounts = new long[CoinKind.values().length];
        addCountsFromInventory(invCounts, player);
        addCountsFromWallet(walletCounts, player);
        long[] available = new long[CoinKind.values().length];
        for (int i = 0; i < available.length; i++) {
            available[i] = safeAdd(invCounts[i], walletCounts[i]);
        }
        long[] plan = planPayment(available, copperCost);
        if (plan == null) {
            return false;
        }
        long[] fromInv = new long[CoinKind.values().length];
        long[] fromWallet = new long[CoinKind.values().length];
        for (CoinKind kind : CoinKind.values()) {
            int idx = kind.ordinal();
            long need = plan[idx];
            if (need <= 0L) {
                continue;
            }
            long invTake = Math.min(need, invCounts[idx]);
            long walletTake = need - invTake;
            if (walletTake > walletCounts[idx]) {
                return false;
            }
            fromInv[idx] = invTake;
            fromWallet[idx] = walletTake;
        }
        // Wallet first (in-memory + put), then inventory — on any failure restore both.
        long[] takenInv = new long[CoinKind.values().length];
        long[] takenWallet = new long[CoinKind.values().length];
        if (!takeExactFromWalletTracked(player, fromWallet, takenWallet)) {
            refundCoinsNoWorldDrop(player, takenWallet);
            return false;
        }
        if (!takeExactFromInventoryTracked(player, fromInv, takenInv)) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] inventory shortfall after wallet charge for {} — refunding",
                    AdaptiveDifficultyMod.MOD_ID,
                    player.m_6302_());
            refundCoinsNoWorldDrop(player, takenInv);
            refundCoinsNoWorldDrop(player, takenWallet);
            return false;
        }
        long paid = totalCopperValue(plan);
        long overpay = paid - copperCost;
        if (overpay > 0L) {
            grantChange(player, overpay);
        }
        DifficultyCache.refresh(player);
        return true;
    }

    /**
     * Restore coins without world-dropping (inventory, then NBT wallet fallback).
     * Avoids lootable refund entities when inventory is full.
     */
    private static void refundCoinsNoWorldDrop(ServerPlayer player, long[] countsByKind) {
        if (player == null || countsByKind == null) {
            return;
        }
        for (CoinKind kind : CoinKind.values()) {
            long count = countsByKind[kind.ordinal()];
            if (count <= 0L) {
                continue;
            }
            long placed = giveStacksInventoryOnly(player, kind, count);
            long missing = count - placed;
            if (missing > 0L) {
                PlayerDifficultyData data = DifficultyCache.data(player);
                data.setAncientCopper(safeAdd(data.getAncientCopper(), safeMul(kind.copperValue, missing)));
                DifficultyCache.save(player);
            }
        }
    }

    private static boolean takeExactFromInventoryTracked(
            ServerPlayer player, long[] needByKind, long[] takenOut
    ) {
        if (player == null || needByKind == null) {
            return true;
        }
        Inventory inv = player.m_150109_();
        for (CoinKind kind : CoinKind.values()) {
            int idx = kind.ordinal();
            long need = needByKind[idx];
            if (need <= 0L) {
                continue;
            }
            long remaining = need;
            for (int i = 0; i < inv.m_6643_() && remaining > 0L; i++) {
                ItemStack stack = inv.m_8020_(i);
                if (kindOf(stack) != kind) {
                    continue;
                }
                int count = stack.m_41613_();
                int take = (int) Math.min(count, Math.min(Integer.MAX_VALUE, remaining));
                if (take <= 0) {
                    continue;
                }
                stack.m_41774_(take);
                if (stack.m_41619_()) {
                    inv.m_6836_(i, ItemStack.f_41583_);
                }
                remaining -= take;
                if (takenOut != null) {
                    takenOut[idx] += take;
                }
            }
            if (remaining > 0L) {
                return false;
            }
        }
        return true;
    }

    /** Take planned coins from the equipped wallet only (never bank). Tracks taken for rollback. */
    private static boolean takeExactFromWalletTracked(
            ServerPlayer player, long[] needByKind, long[] takenOut
    ) {
        if (needByKind == null) {
            return true;
        }
        boolean any = false;
        for (long n : needByKind) {
            if (n > 0L) {
                any = true;
                break;
            }
        }
        if (!any) {
            return true;
        }
        ItemStack wallet = equippedWalletStack(player);
        if (wallet.m_41619_() || !WalletItem.isWallet(wallet)) {
            return false;
        }
        Container walletInv;
        try {
            walletInv = WalletItem.getWalletInventory(wallet);
        } catch (Throwable t) {
            return false;
        }
        if (walletInv == null) {
            return false;
        }
        // Snapshot slots so putWalletInventory failure / shortfall can restore.
        ItemStack[] snapshot = new ItemStack[walletInv.m_6643_()];
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack s = walletInv.m_8020_(i);
            snapshot[i] = s == null || s.m_41619_() ? ItemStack.f_41583_ : s.m_41777_();
        }
        for (CoinKind kind : CoinKind.values()) {
            int idx = kind.ordinal();
            long need = needByKind[idx];
            if (need <= 0L) {
                continue;
            }
            long remaining = need;
            for (int i = 0; i < walletInv.m_6643_() && remaining > 0L; i++) {
                ItemStack stack = walletInv.m_8020_(i);
                if (kindOf(stack) != kind) {
                    continue;
                }
                int count = stack.m_41613_();
                int take = (int) Math.min(count, Math.min(Integer.MAX_VALUE, remaining));
                if (take <= 0) {
                    continue;
                }
                stack.m_41774_(take);
                if (stack.m_41619_()) {
                    walletInv.m_6836_(i, ItemStack.f_41583_);
                }
                remaining -= take;
                if (takenOut != null) {
                    takenOut[idx] += take;
                }
            }
            if (remaining > 0L) {
                restoreWalletSnapshot(walletInv, snapshot);
                try {
                    WalletItem.putWalletInventory(wallet, walletInv);
                } catch (Throwable ignored) {
                }
                if (takenOut != null) {
                    java.util.Arrays.fill(takenOut, 0L);
                }
                return false;
            }
        }
        try {
            WalletItem.putWalletInventory(wallet, walletInv);
            return true;
        } catch (Throwable t) {
            AdaptiveDifficultyMod.LOGGER.warn(
                    "[{}] failed to persist wallet after charge: {}",
                    AdaptiveDifficultyMod.MOD_ID,
                    t.toString());
            restoreWalletSnapshot(walletInv, snapshot);
            try {
                WalletItem.putWalletInventory(wallet, walletInv);
            } catch (Throwable ignored) {
            }
            if (takenOut != null) {
                java.util.Arrays.fill(takenOut, 0L);
            }
            return false;
        }
    }

    private static void restoreWalletSnapshot(Container walletInv, ItemStack[] snapshot) {
        if (walletInv == null || snapshot == null) {
            return;
        }
        for (int i = 0; i < snapshot.length && i < walletInv.m_6643_(); i++) {
            ItemStack s = snapshot[i];
            walletInv.m_6836_(i, s == null || s.m_41619_() ? ItemStack.f_41583_ : s.m_41777_());
        }
    }

    private static void giveStacks(ServerPlayer player, CoinKind kind, long count) {
        long left = count;
        while (left > 0L) {
            int chunk = (int) Math.min(64L, left);
            AncientCoinType type = kind == CoinKind.NETHERITE ? NETHERITE_DROP_TYPE : kind.ancientType;
            dropOrAdd(player, type.asItem(chunk));
            left -= chunk;
        }
    }

    private static void dropOrAdd(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return;
        }
        ItemStack copy = stack.m_41777_();
        if (player.m_150109_().m_36054_(copy)) {
            return;
        }
        ItemEntity dropped = player.m_36176_(stack, false);
        if (dropped != null) {
            dropped.m_32060_();
        }
    }

    private static CoinKind kindOf(ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return null;
        }
        try {
            Item ancient = ModItems.COIN_ANCIENT.get();
            if (!stack.m_150930_(ancient)) {
                return null;
            }
            return CoinKind.of(AncientCoinItem.getAncientCoinType(stack));
        } catch (Throwable t) {
            return null;
        }
    }

    private static void warnMissingLightmans() {
        if (warnedMissingLightmans) {
            return;
        }
        warnedMissingLightmans = true;
        AdaptiveDifficultyMod.LOGGER.warn(
                "[{}] Lightman's Currency not loaded — Ancient Coins falling back to NBT wallet. "
                        + "Install lightmanscurrency for real coin_ancient items.",
                AdaptiveDifficultyMod.MOD_ID
        );
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0L) {
            return Long.MAX_VALUE / 4L;
        }
        return Math.max(0L, r);
    }

    private static long safeMul(long a, long b) {
        if (a == 0L || b == 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE / 4L;
        }
    }
}
